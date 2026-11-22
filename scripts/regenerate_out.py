"""Bounded real-input profile matrix; invoked by eapdf-regenerate-experimental."""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
from contextlib import ExitStack
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import random
import shutil
import subprocess
import sys
import tempfile
from published_outputs import latest_batch_pdfs, walk_output_files
from run_real_world_corpus import available_memory, execute

ROOT = Path(__file__).resolve().parents[1]
SECURITY_HELPER = ROOT/'scripts/RegenerationSecurity.java'
KINDS = ('eml', 'msg', 'mbox', 'pst')
ARCHIVE_PROFILES = ('1m', '1mi', '1c', '1ci')
ARCHIVE_GROUP_SIZE = 20
MAX_PST_BYTES = 250_000_000  # 250 MB (decimal), inclusive.


def readable_file(value, parent, label):
    if not isinstance(value, str) or not value.strip():
        raise ValueError('Missing '+label)
    path = (parent/value).resolve()
    if not path.is_file() or not os.access(path, os.R_OK):
        raise ValueError('Missing or unreadable '+label+': '+str(path))
    return str(path)


def signing_settings(config, parent):
    signing = dict(config.get('signing', {}))
    for key in ('pkcs12Path', 'passphraseFile', 'policyPath'):
        if signing.get(key): signing[key] = str((parent/signing[key]).resolve())
    if signing.get('trustPolicy') is not None:
        signing['trustPolicy'] = resolve_policy(signing['trustPolicy'], parent)
    return signing


def resolve_policy(policy, parent):
    policy = dict(policy)
    for key in ('trustCertificates', 'supportingCertificates', 'crls', 'ocsps'):
        paths = policy.get(key, [])
        if not isinstance(paths, list): raise ValueError(key+' must be an array')
        policy[key] = [readable_file(value, parent, key) for value in paths]
    if not policy['trustCertificates'] or not (policy['crls'] or policy['ocsps']):
        raise ValueError('PAdES B-LTA requires trust certificates and current revocation evidence.')
    return policy


def regeneration_config():
    """Retain current credential locations, including when a custom config is used."""
    local = ROOT/'config/signing-digicert.local.json'
    selected = Path(os.environ.get('EAPDF_CONFIG') or local).resolve()
    config = json.loads(selected.read_text())
    signing = signing_settings(json.loads(local.read_text()), local.parent) if selected != local and local.is_file() else {}
    overrides = signing_settings(config, selected.parent)
    if 'policyPath' in overrides and 'trustPolicy' not in overrides:
        signing.pop('trustPolicy', None)
    signing.update(overrides)
    signing.update(enabled=True, c2pa=True, protection='PADES_B_LTA')
    signing['pkcs12Path'] = readable_file(signing.get('pkcs12Path'), ROOT, 'signing.pkcs12Path')
    if 'EAPDF_SIGNING_PASSWORD' not in os.environ:
        signing['passphraseFile'] = readable_file(signing.get('passphraseFile'), ROOT, 'signing.passphraseFile')
    if signing.get('trustPolicy') is not None:
        policy = signing.pop('trustPolicy')
    else:
        signing['policyPath'] = readable_file(signing.get('policyPath'), ROOT, 'signing.policyPath')
        policy_path = Path(signing['policyPath'])
        policy = resolve_policy(json.loads(policy_path.read_text()), policy_path.parent)
    signing['tsaUrl'] = signing.get('tsaUrl') or 'http://timestamp.digicert.com'
    config['signing'] = signing
    return config, policy


def security_command(args, heap, scratch):
    return ['java', '-Xmx'+str(max(256, heap//1024**2))+'m',
            '-XX:ActiveProcessorCount='+str(args.jobs), '-Djava.io.tmpdir='+str(scratch),
            '-cp', str(args.security_classes)+os.pathsep+str(args.creator_jar),
            'eapdf.RegenerationSecurity']


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def inventory(root):
    return sorted((p for p in (root/'in').rglob('*') if p.is_file()), key=lambda p: str(p))


def oversized_pst(path):
    return path.suffix.lower() == ".pst" and path.stat().st_size > MAX_PST_BYTES


def tasks_for(paths, mode):
    selected = []
    for kind in KINDS:
        candidates = [p for p in paths if p.suffix.lower() == '.'+kind and not oversized_pst(p)]
        if mode == 'sample':
            candidates = sorted(candidates, key=lambda p: (p.stat().st_size, str(p)))[:2]
        selected.extend(candidates)
    tasks = []
    for path in selected:
        kind = path.suffix.lower()[1:]
        slug=re.sub(r'[^\w.-]+', '_', path.stem)[:70]
        while len(slug.encode())>120: slug=slug[:-1]
        ident = kind+'_'+slug+'_'+hashlib.sha256(str(path.relative_to(ROOT)).encode()).hexdigest()[:10]
        profiles = ARCHIVE_PROFILES if kind in ('pst','mbox') else ('1s','1si')
        tasks.extend({'id':ident+'_'+profile,'type':kind,'profile':profile,'sources':[path]} for profile in profiles)
    for kind in ('eml','msg'):
        group = [p for p in selected if p.suffix.lower() == '.'+kind]
        if group:
            random.SystemRandom().shuffle(group)
            # Balance the final groups so a remainder of one does not create a
            # single-message archive when there are enough sources to share.
            count = (len(group)+ARCHIVE_GROUP_SIZE-1)//ARCHIVE_GROUP_SIZE
            for index in range(count):
                members = group[index::count]
                tasks.extend({'id':f'{kind}_group_{index+1:03d}_{profile}',
                              'type':kind,'profile':profile,'sources':members}
                             for profile in ARCHIVE_PROFILES)
    # Keep each input type together, with grouped archives before its standalone
    # PDFs. EML/MSG work precedes the longer whole-mailbox jobs.
    tasks.sort(key=lambda task: (KINDS.index(task['type']),
                                 task['profile'] not in ARCHIVE_PROFILES))
    return selected, tasks


def active_conversion(root):
    for path in Path('/proc').glob('[0-9]*/cmdline'):
        try:
            args = path.read_bytes().split(b'\0')
            if args and b'java' in Path(os.fsdecode(args[0])).name.encode() and any(os.fsencode(root/'out') == a or a.startswith(os.fsencode(root/'out')+b'/') for a in args):
                return path.parent.name
        except (OSError, ValueError):
            pass
    return None


def prepare_output(root):
    output = root/'out'
    if output.is_symlink() or output.resolve() != root.resolve()/'out':
        raise RuntimeError('Refusing a symlinked or out-of-tree output directory.')
    output.mkdir(exist_ok=True)
    for kind in KINDS:
        folder = output/kind
        if folder.is_symlink():
            raise RuntimeError('Refusing a symlinked output directory: '+str(folder))
        folder.mkdir(exist_ok=True)


def publish_run(results):
    """Publish verified PDFs using a hard link as an atomic no-replace operation."""
    prepare_output(ROOT)
    for result in sorted(results, key=lambda entry: entry['id']):
        for document in result['documents']:
            source = Path(document['staged'])
            destination = ROOT/document['path']
            stem, profile = destination.stem.rsplit('_', 1)
            if digest(source) != document['sha256']:
                raise RuntimeError('Staged PDF changed before publication: '+str(source))
            output = publish_pdf(source, destination, stem, profile, document['sha256'])
            document['report_basename'] = destination.name
            document['path'] = str(output.relative_to(ROOT))
            document['published'] = True


def publish_pdf(source, destination, stem, profile, expected_hash):
    # The final PDF name appears only after the complete copy is closed.
    checksum = hashlib.sha256()
    handle, pending = tempfile.mkstemp(prefix='.'+destination.stem+'-', suffix='.tmp', dir=destination.parent)
    try:
        with os.fdopen(handle, 'wb') as output, source.open('rb') as original:
            for block in iter(lambda: original.read(1024*1024), b''):
                output.write(block)
                checksum.update(block)
        shutil.copystat(source, pending)
        if checksum.hexdigest() != expected_hash:
            raise RuntimeError('Staged PDF changed while copying: '+str(source))
        candidate = destination
        copy = 0
        while True:
            try:
                os.link(pending, candidate)
                return candidate
            except FileExistsError:
                if not candidate.is_symlink() and candidate.is_file() and digest(candidate) == expected_hash:
                    return candidate
                copy += 1
                suffix = expected_hash[:12] + ('' if copy == 1 else f'-{copy}')
                candidate = destination.with_name(f'{stem}_{suffix}_{profile}.pdf')
    finally:
        Path(pending).unlink(missing_ok=True)



def validation_environment(environment, pdf, workers=1):
    """Share available disk between jobs; preserve explicit operator limits."""
    configured=dict(environment)
    share=max(1,shutil.disk_usage(pdf.parent).free//(2*max(1,workers)))
    configured.setdefault('VALIDATOR_MAX_WORK_FILE_BYTES',str(max(1,share//2)))
    configured.setdefault('VALIDATOR_MAX_SCRATCH_BYTES',str(share))
    return configured


def validation_assessment(report):
    # Current Java validators stream large reports and write a small summary sidecar.
    summary=Path(str(report)+'.summary.json')
    selected=summary if summary.is_file() else report
    if not selected.is_file(): return {}
    with selected.open() as stream:
        return json.load(stream)


def validation_status(report):
    return validation_assessment(report).get('summary',{}).get('status','UNAVAILABLE')


def required_security_met(security):
    return (security.get('status') in ('PASS','UNKNOWN')
            and security.get('requestedProtectionMet') is True
            and security.get('padesBLta') is True
            and all(security.get('checks',{}).get(key,{}).get('status') == 'PASS'
                    for key in ('c2pa.signature','c2pa.binding')))


def _run_task(task, args, evidence, workspace, config, hashes, heap, warning=False):
    name, profile = task['id'], task['profile']
    folder = evidence/name
    if warning:
        folder = folder/'warning-attempt'
    folder.mkdir(parents=warning)
    stage = workspace/(name+'-validation-warning' if warning else name); stage.mkdir()
    generated = stage/'generated'; generated.mkdir()
    scratch = stage/'scratch'; scratch.mkdir()
    sources = task['sources']
    result = {'id':name,'profile':profile,'sources':[str(p.relative_to(ROOT)) for p in sources], 'status':'FAIL','documents':[]}
    try:
        if len(sources)==1: source=sources[0]
        else:
            source=stage/'inputs';source.mkdir()
            for n,path in enumerate(sources):
                parent=source/str(n);parent.mkdir();(parent/path.name).symlink_to(path)
        environment=dict(os.environ, EAPDF_CONFIG=str(config), EAPDF_WORKER_HEAP_BYTES=str(heap), VALIDATOR_CHILD_PROCESSES='1')
        disk=validation_environment({},generated,getattr(args,'workers',1))
        environment.setdefault('EAPDF_MAX_TEMP_BYTES',disk['VALIDATOR_MAX_SCRATCH_BYTES'])
        environment.setdefault('EAPDF_MAX_OUTPUT_BYTES',disk['VALIDATOR_MAX_WORK_FILE_BYTES'])
        result['conversion_limits']={key:environment[key] for key in ('EAPDF_MAX_TEMP_BYTES','EAPDF_MAX_OUTPUT_BYTES')}
        command=['java','-XX:ActiveProcessorCount='+str(args.jobs),'-Djava.io.tmpdir='+str(scratch),'-jar',str(args.creator_jar),'--jobs',str(args.jobs),'--out',str(generated)]
        if profile=='1si': command+=['--omit-source']
        elif profile not in ('1s','1si'): command+=['--archive-profile',profile[1:],'--archive-output',str(generated/(name+'.pdf'))]
        command.append(str(source))
        result['command']=command
        result['conversion']=execute(command,folder/'conversion.log',args.timeout,environment)
        log=(folder/'conversion.log').read_text(errors='replace')
        if result['conversion']['exit_code']:
            if profile in ('1m','1mi') and 'require at least two emails' in log:
                result['status']='NOT_APPLICABLE';result['reason']='Input contains fewer than two emails; collection profiles cover it.'
            else: result['reason']='Conversion rejected or incomplete.'
            return result
        if warning and 'VALIDATION WARNING BANNER:' not in log:
            raise RuntimeError('Creator did not confirm the validation banner; use the updated creator JAR.')
        outputs=latest_batch_pdfs(generated)
        pdfs=list(outputs)
        if not pdfs: raise RuntimeError('Creator reported success but produced no PDFs.')
        pdf_hashes={pdf:digest(pdf) for pdf in pdfs}
        if any(expected_hash is not None and expected_hash!=pdf_hashes[pdf]
               for pdf,expected_hash in outputs.items()):
            raise RuntimeError('Creator batch member does not match its publication manifest.')
        # Do not publish even one PDF from a job unless every outer PDF is protected.
        for n,pdf in enumerate(pdfs,1):
            report=folder/f'{n:06d}.security.json'
            check=execute(security_command(args,heap,scratch)+[str(pdf),str(report)],
                          folder/f'{n:06d}.security.log',args.timeout,environment)
            if check['exit_code'] or not report.is_file():
                raise RuntimeError('C2PA/PAdES B-LTA verification failed; no replacement published.\n'
                                   + log_tail(report.with_suffix('.log')))
        incomplete=bool(re.search(r'(?:skipped input:|source omitted|INCOMPLETE SOURCE|failed: [1-9]|not processed: [1-9])',log,re.I))
        if incomplete: result['reason']='Creator reported incomplete or omitted source data.'
        result['status']='VALIDATING'
        for n,pdf in enumerate(pdfs,1):
            filename=(name if profile not in ('1s','1si') else name.rsplit('_',1)[0]+f'_{n:06d}_{profile}')+'.pdf'
            destination=ROOT/'out'/task['type']/filename
            result['documents'].append({'path':str(destination.relative_to(ROOT)),'staged':str(pdf),'sha256':pdf_hashes[pdf],'validation':'PENDING'})
            (folder/'result.json').write_text(json.dumps(result,indent=2)+'\n')
            print('Staged: '+filename+' (validation pending)',flush=True)
        # Validate the staged bytes before any output is published.
        for pdf,document in zip(pdfs,result['documents']):
            filename=Path(document['path']).name
            report=folder/(filename+'.validation.json')
            validation_env=validation_environment(environment,pdf,getattr(args,'workers',1))
            validation_env['EAPDF_SECURITY_POLICY']=json.loads(config.read_text())['signing']['policyPath']
            validation_env['EAPDF_SECURITY_REQUIRED_PROTECTION']='PADES_B_LTA'
            document['validation_limits']={key:validation_env[key] for key in ('VALIDATOR_MAX_WORK_FILE_BYTES','VALIDATOR_MAX_SCRATCH_BYTES')}
            validation=execute(['java','-Xmx'+str(max(256,heap//1024**2))+'m','-XX:ActiveProcessorCount='+str(args.jobs),'-Djava.io.tmpdir='+str(scratch),'-cp',str(args.validator_jar),'eapdf.JavaValidation',str(pdf),str(report)],folder/(filename+'.validation.log'),args.timeout,validation_env)
            assessment=validation_assessment(report)
            status=assessment.get('summary',{}).get('status','UNAVAILABLE')
            if status not in ('PASS','WARN') or validation['exit_code'] not in (0,2):
                outcome='INCOMPLETE' if status in ('INCOMPLETE','UNAVAILABLE','PENDING') else 'FAIL'
                if result['status']!='FAIL': result['status']=outcome
            security=assessment.get('security',{})
            document['security']=security
            if not required_security_met(security):
                outcome='FAIL' if security.get('status') in ('FAIL','ERROR') or security.get('requestedProtectionMet') is False else 'INCOMPLETE'
                if result['status']!='FAIL': result['status']=outcome
                result['reason']='Standalone Java validation did not confirm C2PA/PAdES B-LTA.'
            document.update(validation=status,validation_execution=validation)
            (folder/'result.json').write_text(json.dumps(result,indent=2)+'\n')
            print('Validated: '+document['path']+' — '+status,flush=True)
        if result['status']=='VALIDATING': result['status']='INCOMPLETE' if incomplete else 'PASS'
        if any(digest(p)!=hashes[str(p)] for p in sources): raise RuntimeError('Original source hash changed during conversion.')
    except Exception as error:
        result['status']='FAIL';result['reason']=str(error)
    finally:
        # Retain creator receipts/progress outside out, never mix them with published PDFs.
        for p in walk_output_files(generated):
            if p.is_file() and p.suffix.lower()!='.pdf':
                dest=folder/'creator-evidence'/p.relative_to(generated);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,dest)
        (folder/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    return result




def run_task(task, args, evidence, workspace, config, hashes, heap):
    result=_run_task(task,args,evidence,workspace,config,hashes,heap)
    if not any(d.get('validation') in ('FAIL','INCOMPLETE','UNAVAILABLE') for d in result['documents']):
        return result
    folder=evidence/task['id']
    initial=folder/'initial-validation';initial.mkdir()
    # Preserve the first verdict and all diagnostic evidence before regenerating.
    for path in list(folder.iterdir()):
        if path==initial: continue
        if path.is_dir(): shutil.copytree(path,initial/path.name)
        else: shutil.copy2(path,initial/path.name)
    warning_config=json.loads(config.read_text())
    failed=any(d.get('validation')=='FAIL' for d in result['documents'])
    warning_config['validationWarning']=('One or more validation checks failed.' if failed else 'One or more validation checks could not be completed.')+' Conformance was not established by the initial check. Run the standalone validator to review the published PDF.'
    config_path=folder/'validation-warning-config.json'
    config_path.write_text(json.dumps(warning_config,indent=2)+'\n')
    print('Adding validation warning: '+task['id'],flush=True)
    replacement=_run_task(task,args,evidence,workspace,config_path,hashes,heap,warning=True)
    replacement['validation_warning']=True
    replacement['initial_validation_status']=result['status']
    replacement['initial_validation_evidence']=str(initial)
    # Keep the first attempt in staging when the warning conversion fails.
    if not replacement['documents']:
        replacement['documents']=result['documents']
        replacement['validation_warning']=False
        replacement['reason']='Could not generate the validation-warning replacement; initial attempt kept in staging. '+replacement.get('reason','')
    for report in folder.glob('*.validation*'):
        report.unlink()
    for report in (folder/'warning-attempt').glob('*.validation*'):
        report.replace(folder/report.name)
    if (folder/'warning-attempt/conversion.log').is_file():
        shutil.copy2(folder/'warning-attempt/conversion.log', folder/'conversion.log')
    (folder/'result.json').write_text(json.dumps(replacement,indent=2)+'\n')
    return replacement


def log_tail(path):
    limit = 4096
    try:
        with path.open('rb') as log:
            log.seek(max(0, os.fstat(log.fileno()).st_size - limit))
            return log.read(limit).decode(errors='replace').strip()
    except FileNotFoundError:
        return ''


def final_summary(results, evidence, changed=()):
    """Print the outcome and bounded diagnostics before temporary files are removed."""
    counts={status:sum(r['status']==status for r in results)
            for status in ('PASS','FAIL','INCOMPLETE','NOT_APPLICABLE')}
    lines=[f"Finished: {len(results)} profile jobs; " + '; '.join(f"{n} {status}" for status,n in counts.items()) + '.']
    problems=sorted((r for r in results if r['status'] not in ('PASS','NOT_APPLICABLE')),key=lambda r:r['id'])
    if not problems: lines.append('No failed or incomplete files.')
    for result in problems:
        folder=evidence/result['id']
        lines.append(f"{result['status']}: {result['id']}")
        if result.get('reason'): lines.append('  Reason: '+result['reason'])
        lines.append('  Sources: '+', '.join(result['sources']))
        documents=result.get('documents',[])
        affected=[d for d in documents if d.get('validation') not in ('PASS','WARN')
                  or d.get('validation_execution',{}).get('exit_code',1) not in (0,2)]
        # Source omissions can leave a valid PDF but an incomplete conversion.
        for document in affected or documents:
            lines.append('  File: '+str(ROOT/document['path']) + (' (staged, unpublished)' if 'staged' in document and not document.get('published') else '')+' (validation: '+document.get('validation','UNAVAILABLE')+')')
            validation_log = log_tail(folder/(Path(document['path']).name+'.validation.log'))
            if validation_log:
                lines.append('  Validation log:')
                lines.extend('    '+line for line in validation_log.splitlines())
        if not documents: lines.append('  No PDF produced.')
        conversion_log = log_tail(folder/'conversion.log')
        if conversion_log:
            lines.append('  Conversion log:')
            lines.extend('    '+line for line in conversion_log.splitlines())
    if changed:
        lines.append('SOURCE INTEGRITY FAIL: '+', '.join(changed))
    text='\n'.join(lines)+'\n'
    (evidence/'summary.txt').write_text(text)
    print(text,end='',flush=True)
    return bool(problems or changed)



def working_directory():
    # Large mail archives cannot use a memory-backed /tmp for staging and scratch.
    root=ROOT/'.generator_web_tmp'
    root.mkdir(exist_ok=True)
    return tempfile.TemporaryDirectory(prefix='eapdf-regenerate-experimental-',dir=root)


def snapshot_jars(args, evidence):
    records = {}
    for role in ('creator', 'validator'):
        source = getattr(args, role+'_jar').resolve()
        destination = evidence/'runtime'/role/source.name
        destination.parent.mkdir(parents=True)
        checksum = digest(source)
        shutil.copy2(source, destination)
        if digest(destination) != checksum:
            raise RuntimeError('JAR changed while preparing the run: '+str(source))
        setattr(args, role+'_jar', destination)
        records[role] = {'filename':source.name,'sha256':checksum}
    (evidence/'runtime-jars.json').write_text(json.dumps(records,indent=2)+'\n')
    args.security_helper = evidence/'runtime/RegenerationSecurity.java'
    shutil.copy2(SECURITY_HELPER, args.security_helper)


def main():
    parser=argparse.ArgumentParser(prog='eapdf-regenerate-experimental',description='Regenerate a sample or every supported in/ file with required C2PA and PAdES B-LTA. Uses config/signing-digicert.local.json unless EAPDF_CONFIG selects a custom configuration. Working files are removed at the end of the run. Results are printed to the terminal.',epilog='Examples: eapdf-regenerate-experimental sample; eapdf-regenerate-experimental everything --workers 4 --jobs 2. Use --dry-run to preview without writing to out/ or opening credentials. EML/MSG sources are randomly grouped by type into archives of up to 20 messages, covering every selected source. Each PST/MBOX source produces only 1m, 1mi, 1c and 1ci archives; no individual-message extraction. PST files larger than 250 MB (250,000,000 bytes) are skipped. Single-email 1m/1mi cases are reported not applicable.')
    parser.add_argument('mode',choices=('sample','everything'))
    parser.add_argument('--creator',default=os.environ.get('EAPDF_CREATOR'))
    parser.add_argument('--workers',type=int,help='Concurrent Java jobs (default: CPU/memory bounded auto)')
    parser.add_argument('--jobs',type=int,default=2,help='Creator threads per process (default: 2)')
    parser.add_argument('--timeout',type=int,default=86400,help='Seconds per conversion/validation (default: 86400)')
    parser.add_argument('--dry-run',action='store_true')
    version=(ROOT/'VERSION').read_text().strip()
    parser.add_argument('--creator-jar',type=Path,default=ROOT/'dist'/f'emailtopdfa-{version}.jar')
    parser.add_argument('--validator-jar',type=Path,default=ROOT/'dist'/f'eapdf-validator-{version}.jar')
    args=parser.parse_args()
    if args.jobs<1 or args.timeout<1 or (args.workers is not None and args.workers<1): parser.error('workers, jobs and timeout must be positive')
    memory=int(available_memory()*.65)
    args.workers=args.workers or max(1,min(max(1,(os.cpu_count() or 2)//args.jobs),memory//(3*1024**3)))
    heap=max(256*1024**2,memory//args.workers-1024**3)
    if memory//args.workers<1280*1024**2: parser.error('Too many workers for currently available memory; reduce --workers.')
    paths=inventory(ROOT);selected,tasks=tasks_for(paths,args.mode)
    skipped=[{'path':str(p.relative_to(ROOT)),'size_bytes':p.stat().st_size,
              'reason':'PST exceeds 250 MB (250,000,000 bytes)'} for p in paths if oversized_pst(p)]
    for item in skipped: print(f"SKIP {item['path']}: {item['reason']} ({item['size_bytes']} bytes)",flush=True)
    if not selected: parser.error('No eligible supported inputs found under in/.')
    print(f'{args.mode}: {len(selected)} sources, {len(tasks)} profile jobs; {args.workers} processes × {args.jobs} threads; {heap//1024**2} MiB heap per job.',flush=True)
    if args.dry_run:
        for task in tasks: print(task['id']+': '+', '.join(str(p.relative_to(ROOT)) for p in task['sources']))
        print('Security: C2PA + PAdES B-LTA required; credentials and evidence are checked before generation.')
        print('Dry run: out/ was not changed.');return 0
    try: config,policy=regeneration_config()
    except (OSError,ValueError,TypeError) as error: parser.error('Security configuration: '+str(error))
    config['creator']=args.creator or config.get('creator') or 'Thinkbox.DIGITAL'
    if not config['creator'].strip(): config['creator']='Thinkbox.DIGITAL'
    for jar in (args.creator_jar,args.validator_jar):
        if not jar.is_file(): parser.error('Missing versioned JAR: '+str(jar))
    if not shutil.which('java') or not shutil.which('javac'): parser.error('A Java JDK with java and javac is required.')
    running=active_conversion(ROOT)
    if running: parser.error('Another Java conversion is using out/ (PID '+running+'). Wait for it to finish.')
    for key in ('branding','findingAid','premis'): config.pop(key,None)
    config.setdefault('accessibility',{})['addPdfUaFeatures']=True
    config['searchIndex']={'enabled':True,'fullTextEnabled':True}
    config['validationWarning']=''
    with ExitStack() as resources:
        # Lock the checkout directory inode so regeneration needs no persistent lock file.
        lock = os.open(ROOT, os.O_RDONLY | os.O_DIRECTORY)
        resources.callback(os.close, lock)
        try: fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError: parser.error('Another regeneration run is active.')
        workspace = Path(resources.enter_context(working_directory()))
        evidence = workspace/'evidence'
        evidence.mkdir()
        jobs = workspace/'jobs'
        jobs.mkdir()
        runtime = resources.enter_context(tempfile.TemporaryDirectory(prefix='eapdf-regenerate-runtime-', dir='/tmp'))
        snapshot_jars(args,evidence)
        policy_file=evidence/'security-policy.json';policy_file.write_text(json.dumps(policy,indent=2)+'\n')
        config['signing']['policyPath']=str(policy_file)
        config_file=evidence/'config.json';config_file.write_text(json.dumps(config,indent=2)+'\n')
        environment=dict(os.environ,EAPDF_CONFIG=str(config_file))
        args.security_classes=Path(runtime)
        compilation=execute(['javac','-cp',str(args.creator_jar),'-d',runtime,str(args.security_helper)],
                            evidence/'security-compile.log',args.timeout,environment)
        if compilation['exit_code']:
            parser.error('Cannot prepare C2PA/PAdES B-LTA checks; out/ was not changed.\n'
                         + log_tail(evidence/'security-compile.log'))
        with tempfile.TemporaryDirectory(prefix='eapdf-regenerate-preflight-',dir='/tmp') as scratch:
            preflight=execute(security_command(args,heap,scratch)+['preflight'],evidence/'security-preflight.log',args.timeout,environment)
        if preflight['exit_code']:
            parser.error('C2PA/PAdES B-LTA signing preflight failed; out/ was not changed.\n'
                         + log_tail(evidence/'security-preflight.log'))
        hashes={str(p):digest(p) for p in selected}
        (evidence/'sources.json').write_text(json.dumps({'selected':[{'path':str(p.relative_to(ROOT)),'sha256':hashes[str(p)]} for p in selected],'skipped':skipped,'not_selected':[str(p.relative_to(ROOT)) for p in paths if p not in selected],'unsupported':[str(p.relative_to(ROOT)) for p in paths if p.suffix.lower()[1:] not in KINDS]},indent=2)+'\n')
        (evidence/'plan.json').write_text(json.dumps([{**task,'sources':[str(p.relative_to(ROOT)) for p in task['sources']]} for task in tasks],indent=2)+'\n')
        print('Preparing verified outputs; temporary run evidence: '+str(evidence),flush=True)
        results=[]
        with ThreadPoolExecutor(max_workers=args.workers) as pool:
            futures=[pool.submit(run_task,t,args,evidence,jobs,config_file,hashes,heap) for t in tasks]
            for future in as_completed(futures):
                result=future.result();results.append(result);print(result['status']+' '+result['id'],flush=True)
                (evidence/'summary.json').write_text(json.dumps(results,indent=2)+'\n')
        changed=[str(p) for p in selected if digest(p)!=hashes[str(p)]]
        (evidence/'source-check.json').write_text(json.dumps({'unchanged':not changed,'changed':changed},indent=2)+'\n')
        if not changed and all(row['status'] in ('PASS','NOT_APPLICABLE') for row in results):
            publish_run(results)
            (evidence/'summary.json').write_text(json.dumps(results,indent=2)+'\n')
        return 1 if final_summary(results,evidence,changed) else 0

if __name__=='__main__':
    sys.exit(main())
