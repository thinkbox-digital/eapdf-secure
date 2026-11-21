"""Regeneration planning/publication checks, isolated from the real out directory."""
import importlib.util
import io
from contextlib import redirect_stdout
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'scripts'))
import regenerate_out as runner

VERIFIED_SECURITY={'status':'UNKNOWN','requestedProtectionMet':True,'padesBLta':True,
                   'checks':{'c2pa.signature':{'status':'PASS'},'c2pa.binding':{'status':'PASS'}}}

class RegenerationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='eapdf-regeneration-test-', dir='/tmp')
        self.root = Path(self.temp.name)
        (self.root/'in').mkdir()
        self.patch = patch.object(runner, 'ROOT', self.root)
        self.patch.start()
    def tearDown(self):
        self.patch.stop()
        self.temp.cleanup()
    def source(self, name):
        path=self.root/'in'/name
        path.write_text('source '+name)
        return path

    def local_security_config(self):
        folder=self.root/'config';folder.mkdir(exist_ok=True)
        for name in ('signer.p12','signer.passphrase','root.pem','root.crl'):
            (folder/name).write_text('test-only '+name)
        (folder/'policy.json').write_text(json.dumps({'trustCertificates':['root.pem'],'crls':['root.crl']}))
        config={'signing':{'enabled':False,'c2pa':False,'protection':'CMS_AND_PDF',
                          'pkcs12Path':'signer.p12','passphraseFile':'signer.passphrase',
                          'policyPath':'policy.json','tsaUrl':'http://timestamp.example.invalid'}}
        path=folder/'signing-digicert.local.json';path.write_text(json.dumps(config))
        return path

    def test_local_security_paths_and_full_protection_are_required(self):
        self.local_security_config()
        with patch.dict(runner.os.environ,{},clear=True): config,policy=runner.regeneration_config()
        signing=config['signing']
        self.assertTrue(signing['enabled']);self.assertTrue(signing['c2pa'])
        self.assertEqual('PADES_B_LTA',signing['protection'])
        self.assertEqual(str(self.root/'config/signer.p12'),signing['pkcs12Path'])
        self.assertEqual(str(self.root/'config/signer.passphrase'),signing['passphraseFile'])
        self.assertEqual([str(self.root/'config/root.pem')],policy['trustCertificates'])
        self.assertEqual([str(self.root/'config/root.crl')],policy['crls'])
        self.assertEqual('http://timestamp.example.invalid',signing['tsaUrl'])

    def test_custom_config_inherits_locations_and_cannot_disable_security(self):
        self.local_security_config()
        folder=self.root/'custom';folder.mkdir();(folder/'custom.p12').write_text('custom credential')
        path=folder/'settings.json';path.write_text(json.dumps({'creator':'Custom creator','signing':{
            'enabled':False,'c2pa':False,'protection':'CMS_AND_PDF','pkcs12Path':'custom.p12','tsaUrl':''}}))
        with patch.dict(runner.os.environ,{'EAPDF_CONFIG':str(path)},clear=True): config,policy=runner.regeneration_config()
        self.assertEqual('Custom creator',config['creator'])
        self.assertEqual(str(folder/'custom.p12'),config['signing']['pkcs12Path'])
        self.assertEqual(str(self.root/'config/signer.passphrase'),config['signing']['passphraseFile'])
        self.assertTrue(config['signing']['enabled']);self.assertTrue(config['signing']['c2pa'])
        self.assertEqual('PADES_B_LTA',config['signing']['protection'])
        self.assertEqual('http://timestamp.digicert.com',config['signing']['tsaUrl'])

    def test_missing_security_files_stop_generation_before_clearing_output(self):
        self.local_security_config();(self.root/'config/signer.p12').unlink()
        self.source('one.eml');(self.root/'VERSION').write_text('2.0.0-beta.1')
        (self.root/'out').mkdir();existing=self.root/'out/keep.pdf';existing.write_bytes(b'preserve')
        with patch.dict(runner.os.environ,{},clear=True),patch.object(sys,'argv',['regenerate','sample','--workers','1']),patch.object(runner,'available_memory',return_value=8*1024**3),patch.object(runner,'clear_output') as clear,redirect_stdout(io.StringIO()),patch.object(sys,'stderr',io.StringIO()):
            with self.assertRaises(SystemExit):runner.main()
        clear.assert_not_called();self.assertEqual(b'preserve',existing.read_bytes())

    def test_environment_password_overrides_missing_passphrase_file(self):
        self.local_security_config();(self.root/'config/signer.passphrase').unlink()
        with patch.dict(runner.os.environ,{'EAPDF_SIGNING_PASSWORD':''},clear=True):
            config,_=runner.regeneration_config()
        self.assertNotIn('password',json.dumps(config).lower())
        with patch.dict(runner.os.environ,{},clear=True):
            with self.assertRaisesRegex(ValueError,'passphraseFile'):runner.regeneration_config()

    def test_missing_revocation_evidence_is_rejected(self):
        self.local_security_config();(self.root/'config/root.crl').unlink()
        with patch.dict(runner.os.environ,{},clear=True):
            with self.assertRaisesRegex(ValueError,'crls'):runner.regeneration_config()

    def test_failed_signing_preflight_preserves_existing_output(self):
        self.local_security_config();self.source('one.eml');(self.root/'VERSION').write_text('2.0.0-beta.1')
        dist=self.root/'dist';dist.mkdir()
        (dist/'emailtopdfa-2.0.0-beta.1.jar').write_bytes(b'creator')
        (dist/'eapdf-validator-2.0.0-beta.1.jar').write_bytes(b'validator')
        (self.root/'out').mkdir();existing=self.root/'out/keep.pdf';existing.write_bytes(b'preserve')
        def execute(command,log,timeout,env):
            log.write_text('rejected' if command[-1]=='preflight' else 'compiled')
            return {'exit_code':1 if command[-1]=='preflight' else 0,'timeout':False,'seconds':0.1}
        stderr = io.StringIO()
        with patch.dict(runner.os.environ,{},clear=True),patch.object(sys,'argv',['regenerate','sample','--workers','1']),patch.object(runner,'available_memory',return_value=8*1024**3),patch.object(runner.shutil,'which',return_value='/usr/bin/java'),patch.object(runner,'execute',execute),patch.object(runner,'clear_output') as clear,redirect_stdout(io.StringIO()),patch.object(sys,'stderr',stderr):
            with self.assertRaises(SystemExit):runner.main()
        clear.assert_not_called();self.assertEqual(b'preserve',existing.read_bytes())
        self.assertIn('rejected', stderr.getvalue())
        self.assertFalse((self.root/'output').exists())
        self.assertEqual([], list((self.root/'.generator_web_tmp').iterdir()))

    def test_completed_regeneration_removes_temporary_evidence(self):
        self.local_security_config()
        self.source('one.eml')
        (self.root/'VERSION').write_text('test')
        dist = self.root/'dist'
        dist.mkdir()
        (dist/'emailtopdfa-test.jar').write_bytes(b'creator')
        (dist/'eapdf-validator-test.jar').write_bytes(b'validator')
        (self.root/'output').mkdir()

        def run_task(task, args, evidence, workspace, config, hashes, heap):
            self.assertEqual(workspace.parent, evidence.parent)
            self.assertEqual(self.root/'.generator_web_tmp', evidence.parent.parent)
            self.assertTrue((evidence/'runtime-jars.json').is_file())
            return {'id': task['id'], 'status': 'PASS'}

        with (
            patch.dict(runner.os.environ, {}, clear=True),
            patch.object(sys, 'argv', ['regenerate', 'sample', '--workers', '1']),
            patch.object(runner, 'available_memory', return_value=8*1024**3),
            patch.object(runner.shutil, 'which', return_value='/usr/bin/java'),
            patch.object(runner, 'execute', return_value={'exit_code': 0}),
            patch.object(runner, 'run_task', side_effect=run_task) as tasks,
            redirect_stdout(io.StringIO()),
        ):
            self.assertEqual(0, runner.main())
        self.assertGreater(tasks.call_count, 0)
        self.assertEqual([], list((self.root/'output').iterdir()))
        self.assertEqual([], list((self.root/'.generator_web_tmp').iterdir()))

    def test_failed_security_check_publishes_no_pdfs_from_job(self):
        source=self.source('source.eml');runner.clear_output(self.root)
        evidence=self.root/'evidence';evidence.mkdir();workspace=self.root/'workspace';workspace.mkdir()
        config=self.root/'config.json';config.write_text(json.dumps({'signing':{'policyPath':'policy.json','enabled':True,'c2pa':True,'protection':'PADES_B_LTA'}}))
        args=SimpleNamespace(security_classes=Path('/tmp/classes'),jobs=1,creator_jar=Path('creator.jar'),validator_jar=Path('validator.jar'),timeout=60)
        calls=[]
        def execute(command,log,timeout,env):
            calls.append(command);log.write_text('test')
            if '-jar' in command:
                out=Path(command[command.index('--out')+1])
                for name in ('first.pdf','second.pdf'): (out/name).write_bytes(b'%PDF-test')
                code=0
            else:
                # The second document fails after the first has verified.
                code=1 if Path(command[-2]).name=='second.pdf' else 0
                Path(command[-1]).write_text('{}')
            return {'exit_code':code,'timeout':False,'seconds':0.1}
        _,tasks=runner.tasks_for([source],'everything')
        with patch.object(runner,'execute',execute),redirect_stdout(io.StringIO()):
            result=runner.run_task(tasks[0],args,evidence,workspace,config,{str(source):runner.digest(source)},2*1024**3)
        self.assertEqual('FAIL',result['status']);self.assertEqual([],result['documents'])
        self.assertIn('C2PA/PAdES B-LTA',result['reason']);self.assertEqual(3,len(calls))
        self.assertFalse(list((self.root/'out').rglob('*.pdf')))

    def test_java_security_failure_cannot_be_hidden_by_conformance_pass(self):
        source=self.source('source.eml');runner.clear_output(self.root)
        evidence=self.root/'evidence';evidence.mkdir();workspace=self.root/'workspace';workspace.mkdir()
        config=self.root/'config.json';config.write_text(json.dumps({'signing':{'policyPath':'policy.json'}}))
        args=SimpleNamespace(security_classes=Path('/tmp/classes'),jobs=1,creator_jar=Path('creator.jar'),validator_jar=Path('validator.jar'),timeout=60)
        def execute(command,log,timeout,env):
            log.write_text('test')
            if '-jar' in command:
                (Path(command[command.index('--out')+1])/'one.pdf').write_bytes(b'%PDF-test')
                code=0
            elif 'eapdf.RegenerationSecurity' in command:
                Path(command[-1]).write_text(json.dumps(VERIFIED_SECURITY));code=0
            else:
                Path(command[-1]).write_text(json.dumps({'summary':{'status':'PASS'},
                    'security':{'status':'FAIL','requestedProtectionMet':False}}));code=2
            return {'exit_code':code,'timeout':False,'seconds':0.1}
        _,tasks=runner.tasks_for([source],'everything')
        with patch.object(runner,'execute',execute),redirect_stdout(io.StringIO()):
            result=runner.run_task(tasks[0],args,evidence,workspace,config,{str(source):runner.digest(source)},2*1024**3)
        self.assertEqual('FAIL',result['status'])
        self.assertEqual('PASS',result['documents'][0]['validation'])
        self.assertEqual('FAIL',result['documents'][0]['security']['status'])
        self.assertIn('did not confirm',result['reason'])
    def test_every_source_covered_by_every_applicable_profile(self):
        paths=[self.source(f'{i}.{kind}') for kind in runner.KINDS for i in range(3)]
        selected,tasks=runner.tasks_for(paths,'everything')
        self.assertEqual(set(paths),set(selected))
        self.assertEqual(len(tasks),len({t['id'] for t in tasks}))
        for path in paths:
            expected={'1m','1mi','1c','1ci'} if path.suffix in ('.pst','.mbox') else {'1s','1si','1m','1mi','1c','1ci'}
            self.assertEqual(expected,
                             {t['profile'] for t in tasks if path in t['sources']})
        sample,_=runner.tasks_for(paths,'sample')
        self.assertEqual(8,len(sample))
    def test_archive_groups_are_bounded_balanced_and_cover_every_source(self):
        for size in (1,2,20,21,41):
            paths=[self.source(f'group-{size}-{i}.{kind}') for kind in ('eml','msg') for i in range(size)]
            # Force a known shuffle so grouping must use the shuffled order.
            with patch.object(runner.random.SystemRandom,'shuffle',side_effect=lambda values:values.reverse()):
                _,tasks=runner.tasks_for(paths,'everything')
            for kind in ('eml','msg'):
                selected=[p for p in paths if p.suffix=='.'+kind]
                for profile in runner.ARCHIVE_PROFILES:
                    groups=[t['sources'] for t in tasks if t['type']==kind and t['profile']==profile]
                    flattened=[p for group in groups for p in group]
                    self.assertCountEqual(selected,flattened)
                    self.assertTrue(all(1<=len(g)<=20 for g in groups))
                    if size>1:self.assertTrue(all(len(g)>=2 for g in groups))
                    self.assertEqual(selected[-1],groups[0][0])
                reference=[t['sources'] for t in tasks if t['type']==kind and t['profile']=='1m']
                for profile in ('1mi','1c','1ci'):
                    self.assertEqual(reference,[t['sources'] for t in tasks if t['type']==kind and t['profile']==profile])
        for mode in ('sample','everything'):
            _,tasks=runner.tasks_for([self.source('archive.PST'),self.source('archive.MBOX')],mode)
            self.assertEqual(8,len(tasks))
            self.assertTrue(all(t['profile'] in runner.ARCHIVE_PROFILES and len(t['sources'])==1 for t in tasks))

    def test_grouped_archives_run_with_their_type_before_mailboxes(self):
        paths=[self.source(f'{i}.{kind}') for kind in runner.KINDS for i in range(2)]
        for mode in ('sample','everything'):
            _,tasks=runner.tasks_for(paths,mode)
            grouped=[t for t in tasks if t['type'] in ('eml','msg') and t['profile'] in runner.ARCHIVE_PROFILES]
            self.assertEqual(8,len(grouped))
            self.assertEqual(sorted((t['type'] for t in tasks),key=runner.KINDS.index),[t['type'] for t in tasks])
            for kind in ('eml','msg'):
                same_type=[t for t in tasks if t['type']==kind]
                self.assertEqual([t for t in grouped if t['type']==kind],same_type[:4])
            self.assertTrue(all(len(t['sources'])==2 for t in grouped))
            containers=[i for i,t in enumerate(tasks) if t['type'] in ('pst','mbox')]
            singles=[i for i,t in enumerate(tasks) if t['profile'] in ('1s','1si')]
            self.assertLess(max(singles),min(containers))

    def test_pst_size_limit_is_inclusive_and_does_not_apply_to_mbox(self):
        below=self.source('below.pst');exact=self.source('exact.PST');large=self.source('large.PsT');mbox=self.source('large.mbox')
        for path,size in ((below,runner.MAX_PST_BYTES-1),(exact,runner.MAX_PST_BYTES),(large,runner.MAX_PST_BYTES+1),(mbox,runner.MAX_PST_BYTES+1)):
            with path.open('r+b') as stream: stream.truncate(size)
        for mode in ('sample','everything'):
            selected,tasks=runner.tasks_for([large,below,exact,mbox],mode)
            self.assertCountEqual([below,exact,mbox],selected)
            self.assertTrue(all(large not in t['sources'] for t in tasks))
            self.assertEqual(12,len(tasks))

    def test_dry_run_reports_oversized_pst_without_touching_output(self):
        self.source('one.eml');large=self.source('large.PST')
        with large.open('r+b') as stream: stream.truncate(runner.MAX_PST_BYTES+1)
        (self.root/'VERSION').write_text('2.0.0-beta.1')
        (self.root/'out').mkdir();existing=self.root/'out/keep.pdf';existing.write_bytes(b'preserve')
        with patch.object(sys,'argv',['eapdf-regenerate-experimental','everything','--dry-run','--workers','1']),patch.object(runner,'available_memory',return_value=8*1024**3),redirect_stdout(io.StringIO()) as output:
            self.assertEqual(0,runner.main())
        self.assertIn('SKIP in/large.PST: PST exceeds 250 MB',output.getvalue())
        self.assertIn('1 sources, 6 profile jobs',output.getvalue())
        self.assertEqual(b'preserve',existing.read_bytes())

    def test_failed_document_is_not_downgraded_by_later_incomplete_document(self):
        source=self.source('mixed.eml');runner.clear_output(self.root)
        evidence=self.root/'evidence';evidence.mkdir()
        workspace=self.root/'workspace';workspace.mkdir()
        config=self.root/'config.json';config.write_text(json.dumps({'signing':{'policyPath':'policy.json','enabled':True,'c2pa':True,'protection':'PADES_B_LTA'}}))
        args=SimpleNamespace(security_classes=Path('/tmp/classes'),jobs=1,creator_jar=Path('creator.jar'),validator_jar=Path('validator.jar'),timeout=60)
        statuses=iter(['FAIL','INCOMPLETE'])
        def execute(command,log,timeout,env):
            if 'eapdf.RegenerationSecurity' in command:
                log.write_text('security verified')
                Path(command[-1]).write_text(json.dumps({'status':'UNKNOWN','requestedProtectionMet':True,'padesBLta':True,
                    'checks':{'c2pa.signature':{'status':'PASS'},'c2pa.binding':{'status':'PASS'}}}))
                return {'exit_code':0,'timeout':False,'seconds':0.1}
            log.write_text('ok')
            if '-jar' in command:
                out=Path(command[command.index('--out')+1])
                for name in ('a.pdf','b.pdf'): (out/name).write_bytes(b'%PDF-test')
                code=0
            else:
                Path(command[-1]).write_text(json.dumps({'summary':{'status':next(statuses)},'security':VERIFIED_SECURITY}));code=2
            return {'exit_code':code,'timeout':False,'seconds':0.1}
        _,tasks=runner.tasks_for([source],'everything')
        with patch.object(runner,'execute',execute),redirect_stdout(io.StringIO()):
            result=runner._run_task(tasks[0],args,evidence,workspace,config,{str(source):runner.digest(source)},2*1024**3)
        self.assertEqual('FAIL',result['status'])
        self.assertEqual(['FAIL','INCOMPLETE'],[d['validation'] for d in result['documents']])

    def test_incomplete_validation_regenerates_warning_once_and_preserves_reports(self):
        source=self.source('nested.msg');runner.clear_output(self.root)
        evidence=self.root/'evidence';evidence.mkdir()
        workspace=self.root/'workspace';workspace.mkdir()
        config=self.root/'config.json';config.write_text(json.dumps({'signing':{'policyPath':'policy.json','enabled':True,'c2pa':True,'protection':'PADES_B_LTA'}}))
        args=SimpleNamespace(security_classes=Path('/tmp/classes'),jobs=1,creator_jar=Path('creator.jar'),validator_jar=Path('validator.jar'),timeout=60)
        attempts=[];validated=[]
        def execute(command,log,timeout,env):
            if 'eapdf.RegenerationSecurity' in command:
                log.write_text('security verified')
                Path(command[-1]).write_text(json.dumps({'status':'UNKNOWN','requestedProtectionMet':True,'padesBLta':True,
                    'checks':{'c2pa.signature':{'status':'PASS'},'c2pa.binding':{'status':'PASS'}}}))
                return {'exit_code':0,'timeout':False,'seconds':0.1}
            if '-jar' in command:
                cfg=json.loads(Path(env['EAPDF_CONFIG']).read_text());attempts.append(cfg)
                payload=b'%PDF-warning' if cfg.get('validationWarning') else b'%PDF-original'
                out=Path(command[command.index('--out')+1]);(out/'one.pdf').write_bytes(payload)
                log.write_text('warning conversion; VALIDATION WARNING BANNER: displayed' if cfg.get('validationWarning') else 'initial conversion')
                return {'exit_code':0,'timeout':False,'seconds':0.1}
            payload=Path(command[-2]).read_bytes();validated.append(payload)
            report={'summary':{'status':'INCOMPLETE'},'security':VERIFIED_SECURITY,'marker':payload.decode()}
            Path(command[-1]).write_text(json.dumps(report));log.write_text(payload.decode())
            return {'exit_code':2,'timeout':False,'seconds':0.1}
        _,tasks=runner.tasks_for([source],'everything');task=tasks[0]
        with patch.object(runner,'execute',execute),redirect_stdout(io.StringIO()):
            result=runner.run_task(task,args,evidence,workspace,config,{str(source):runner.digest(source)},2*1024**3)
        self.assertEqual(2,len(attempts));self.assertNotIn('validationWarning',attempts[0])
        self.assertEqual(attempts[0]['signing'],attempts[1]['signing'])
        self.assertEqual('PADES_B_LTA',attempts[1]['signing']['protection'])
        self.assertIn('could not be completed',attempts[1]['validationWarning'])
        self.assertEqual([b'%PDF-original',b'%PDF-warning'],validated)
        self.assertEqual('INCOMPLETE',result['status']);self.assertTrue(result['validation_warning'])
        self.assertEqual(b'%PDF-warning',(self.root/result['documents'][0]['path']).read_bytes())
        folder=evidence/task['id'];initial=folder/'initial-validation'
        self.assertEqual('initial conversion',(initial/'conversion.log').read_text())
        self.assertIn('warning conversion',(folder/'conversion.log').read_text())
        self.assertEqual('%PDF-original',json.loads(next(initial.glob('*.validation.json')).read_text())['marker'])
        self.assertEqual('%PDF-warning',json.loads(next(folder.glob('*.validation.json')).read_text())['marker'])
        self.assertEqual('source nested.msg',source.read_text())

    def test_failed_validation_regenerates_warning_once_and_preserves_failure(self):
        source=self.source('nested.msg');runner.clear_output(self.root)
        evidence=self.root/'evidence';evidence.mkdir()
        workspace=self.root/'workspace';workspace.mkdir()
        config=self.root/'config.json';config.write_text(json.dumps({'signing':{'policyPath':'policy.json','enabled':True,'c2pa':True,'protection':'PADES_B_LTA'}}))
        args=SimpleNamespace(security_classes=Path('/tmp/classes'),jobs=1,creator_jar=Path('creator.jar'),validator_jar=Path('validator.jar'),timeout=60)
        attempts=[];validated=[]
        def execute(command,log,timeout,env):
            if 'eapdf.RegenerationSecurity' in command:
                log.write_text('security verified')
                Path(command[-1]).write_text(json.dumps({'status':'UNKNOWN','requestedProtectionMet':True,'padesBLta':True,
                    'checks':{'c2pa.signature':{'status':'PASS'},'c2pa.binding':{'status':'PASS'}}}))
                return {'exit_code':0,'timeout':False,'seconds':0.1}
            if '-jar' in command:
                cfg=json.loads(Path(env['EAPDF_CONFIG']).read_text());attempts.append(cfg)
                payload=b'%PDF-warning' if cfg.get('validationWarning') else b'%PDF-original'
                out=Path(command[command.index('--out')+1]);(out/'one.pdf').write_bytes(payload)
                log.write_text('warning conversion; VALIDATION WARNING BANNER: displayed' if cfg.get('validationWarning') else 'initial conversion')
                return {'exit_code':0,'timeout':False,'seconds':0.1}
            payload=Path(command[-2]).read_bytes();validated.append(payload)
            report={'summary':{'status':'FAIL'},'security':VERIFIED_SECURITY,'marker':payload.decode()}
            Path(command[-1]).write_text(json.dumps(report));log.write_text(payload.decode())
            return {'exit_code':2,'timeout':False,'seconds':0.1}
        _,tasks=runner.tasks_for([source],'everything');task=tasks[0]
        with patch.object(runner,'execute',execute),redirect_stdout(io.StringIO()):
            result=runner.run_task(task,args,evidence,workspace,config,{str(source):runner.digest(source)},2*1024**3)
        self.assertEqual(2,len(attempts));self.assertNotIn('validationWarning',attempts[0])
        self.assertEqual(attempts[0]['signing'],attempts[1]['signing'])
        self.assertEqual('PADES_B_LTA',attempts[1]['signing']['protection'])
        self.assertIn('checks failed',attempts[1]['validationWarning'])
        self.assertEqual([b'%PDF-original',b'%PDF-warning'],validated)
        self.assertEqual('FAIL',result['status']);self.assertTrue(result['validation_warning'])
        self.assertEqual(b'%PDF-warning',(self.root/result['documents'][0]['path']).read_bytes())
        folder=evidence/task['id'];initial=folder/'initial-validation'
        self.assertEqual('initial conversion',(initial/'conversion.log').read_text())
        self.assertIn('warning conversion',(folder/'conversion.log').read_text())
        self.assertEqual('%PDF-original',json.loads(next(initial.glob('*.validation.json')).read_text())['marker'])
        self.assertEqual('%PDF-warning',json.loads(next(folder.glob('*.validation.json')).read_text())['marker'])
        self.assertEqual('source nested.msg',source.read_text())

    def test_crashed_warning_recheck_cannot_reuse_the_previous_report(self):
        source=self.source('nested.msg');runner.clear_output(self.root)
        evidence=self.root/'evidence';evidence.mkdir()
        workspace=self.root/'workspace';workspace.mkdir()
        config=self.root/'config.json';config.write_text(json.dumps({'signing':{'policyPath':'policy.json','enabled':True,'c2pa':True,'protection':'PADES_B_LTA'}}))
        args=SimpleNamespace(security_classes=Path('/tmp/classes'),jobs=1,creator_jar=Path('creator.jar'),validator_jar=Path('validator.jar'),timeout=60)
        warning=False
        def execute(command,log,timeout,env):
            if 'eapdf.RegenerationSecurity' in command:
                log.write_text('security verified')
                Path(command[-1]).write_text(json.dumps({'status':'UNKNOWN','requestedProtectionMet':True,'padesBLta':True,
                    'checks':{'c2pa.signature':{'status':'PASS'},'c2pa.binding':{'status':'PASS'}}}))
                return {'exit_code':0,'timeout':False,'seconds':0.1}
            nonlocal warning
            log.write_text('diagnostic')
            if '-jar' in command:
                warning=bool(json.loads(Path(env['EAPDF_CONFIG']).read_text()).get('validationWarning'))
                (Path(command[command.index('--out')+1])/'one.pdf').write_bytes(b'%PDF')
                if warning: log.write_text('VALIDATION WARNING BANNER: displayed')
            elif warning:
                return {'exit_code':3,'timeout':False,'seconds':0.1}
            else:
                report=Path(command[-1]);report.write_text(json.dumps({'summary':{'status':'INCOMPLETE'},'security':VERIFIED_SECURITY}))
                Path(str(report)+'.summary.json').write_text(report.read_text())
            return {'exit_code':0,'timeout':False,'seconds':0.1}
        _,tasks=runner.tasks_for([source],'everything')
        with patch.object(runner,'execute',execute),redirect_stdout(io.StringIO()):
            result=runner.run_task(tasks[0],args,evidence,workspace,config,{str(source):runner.digest(source)},2*1024**3)
        self.assertEqual('UNAVAILABLE',result['documents'][0]['validation'])
        self.assertEqual('INCOMPLETE',result['status'])
        self.assertTrue(list((evidence/tasks[0]['id']/'initial-validation').glob('*.summary.json')))

    def test_older_creator_cannot_publish_an_unconfirmed_warning(self):
        source=self.source('nested.msg');runner.clear_output(self.root)
        evidence=self.root/'evidence';evidence.mkdir()
        workspace=self.root/'workspace';workspace.mkdir()
        config=self.root/'config.json';config.write_text(json.dumps({'signing':{'policyPath':'policy.json','enabled':True,'c2pa':True,'protection':'PADES_B_LTA'}}))
        args=SimpleNamespace(security_classes=Path('/tmp/classes'),jobs=1,creator_jar=Path('creator.jar'),validator_jar=Path('validator.jar'),timeout=60)
        def execute(command,log,timeout,env):
            if 'eapdf.RegenerationSecurity' in command:
                log.write_text('security verified')
                Path(command[-1]).write_text(json.dumps({'status':'UNKNOWN','requestedProtectionMet':True,'padesBLta':True,
                    'checks':{'c2pa.signature':{'status':'PASS'},'c2pa.binding':{'status':'PASS'}}}))
                return {'exit_code':0,'timeout':False,'seconds':0.1}
            log.write_text('Creator succeeded but ignored the unknown warning configuration.')
            (Path(command[command.index('--out')+1])/'one.pdf').write_bytes(b'%PDF-no-banner')
            return {'exit_code':0,'timeout':False,'seconds':0.1}
        _,tasks=runner.tasks_for([source],'everything')
        with patch.object(runner,'execute',execute),redirect_stdout(io.StringIO()):
            result=runner._run_task(tasks[0],args,evidence,workspace,config,{str(source):runner.digest(source)},2*1024**3,warning=True)
        self.assertEqual('FAIL',result['status']);self.assertEqual([],result['documents'])
        self.assertIn('did not confirm',result['reason'])
        self.assertFalse(list((self.root/'out').rglob('*.pdf')))

    def test_warning_conversion_failure_keeps_original_and_reports_failure(self):
        source=self.source('nested.msg');runner.clear_output(self.root)
        evidence=self.root/'evidence';evidence.mkdir()
        workspace=self.root/'workspace';workspace.mkdir()
        config=self.root/'config.json';config.write_text(json.dumps({'signing':{'policyPath':'policy.json','enabled':True,'c2pa':True,'protection':'PADES_B_LTA'}}))
        args=SimpleNamespace(security_classes=Path('/tmp/classes'),jobs=1,creator_jar=Path('creator.jar'),validator_jar=Path('validator.jar'),timeout=60)
        def execute(command,log,timeout,env):
            if 'eapdf.RegenerationSecurity' in command:
                log.write_text('security verified')
                Path(command[-1]).write_text(json.dumps({'status':'UNKNOWN','requestedProtectionMet':True,'padesBLta':True,
                    'checks':{'c2pa.signature':{'status':'PASS'},'c2pa.binding':{'status':'PASS'}}}))
                return {'exit_code':0,'timeout':False,'seconds':0.1}
            log.write_text('diagnostic')
            if '-jar' in command:
                if json.loads(Path(env['EAPDF_CONFIG']).read_text()).get('validationWarning'):
                    return {'exit_code':1,'timeout':False,'seconds':0.1}
                (Path(command[command.index('--out')+1])/'one.pdf').write_bytes(b'%PDF-original')
            else:Path(command[-1]).write_text(json.dumps({'summary':{'status':'INCOMPLETE'},'security':VERIFIED_SECURITY}))
            return {'exit_code':0,'timeout':False,'seconds':0.1}
        _,tasks=runner.tasks_for([source],'everything')
        with patch.object(runner,'execute',execute),redirect_stdout(io.StringIO()):
            result=runner.run_task(tasks[0],args,evidence,workspace,config,{str(source):runner.digest(source)},2*1024**3)
        self.assertEqual('FAIL',result['status']);self.assertFalse(result['validation_warning'])
        self.assertIn('original PDF retained',result['reason'])
        self.assertEqual(b'%PDF-original',(self.root/result['documents'][0]['path']).read_bytes())

    def test_final_summary_lists_failures_incomplete_and_missing_outputs(self):
        evidence=self.root/'evidence';evidence.mkdir()
        cases=[
            {'id':'passed','status':'PASS','sources':['in/pass.eml'],'documents':[]},
            {'id':'not-applicable','status':'NOT_APPLICABLE','sources':['in/one.mbox'],'documents':[]},
            {'id':'rejected','status':'FAIL','reason':'Conversion rejected.','sources':['in/bad.msg'],'documents':[]},
            {'id':'incomplete','status':'INCOMPLETE','sources':['in/archive.mbox'],
             'documents':[{'path':'out/mbox/archive_1m.pdf','validation':'INCOMPLETE'}]},
            {'id':'partial','status':'INCOMPLETE','reason':'Incomplete source data.','sources':['in/partial.eml'],
             'documents':[{'path':'out/eml/partial_1s.pdf','validation':'PASS','validation_execution':{'exit_code':0}}]},
            {'id':'mixed','status':'FAIL','sources':['in/mixed.eml'],
             'documents':[{'path':'out/eml/bad_1s.pdf','validation':'FAIL'},
                          {'path':'out/eml/good_1s.pdf','validation':'PASS','validation_execution':{'exit_code':0}}]},
        ]
        report=evidence/'incomplete/archive_1m.pdf.validation.json'
        report.parent.mkdir();report.write_text('{}')
        log=report.with_suffix('.log');log.write_text('unavailable')
        output=io.StringIO()
        with redirect_stdout(output): self.assertTrue(runner.final_summary(cases,evidence))
        text=output.getvalue()
        self.assertEqual(text,(evidence/'summary.txt').read_text())
        self.assertIn('1 PASS; 2 FAIL; 2 INCOMPLETE; 1 NOT_APPLICABLE',text)
        self.assertIn('No PDF produced.',text);self.assertIn('in/bad.msg',text)
        self.assertIn('Validation log:', text)
        self.assertIn('unavailable', text)
        self.assertNotIn(str(report), text)
        self.assertNotIn(str(log), text)
        self.assertIn('out/eml/partial_1s.pdf (validation: PASS)',text)
        self.assertIn('out/eml/bad_1s.pdf',text);self.assertNotIn('out/eml/good_1s.pdf',text)
        self.assertNotIn('in/pass.eml',text);self.assertNotIn('in/one.mbox',text)

    def test_final_summary_success_and_source_integrity_failure(self):
        evidence=self.root/'evidence';evidence.mkdir()
        with redirect_stdout(io.StringIO()) as output:
            self.assertFalse(runner.final_summary([],evidence))
        self.assertIn('No failed or incomplete files.',output.getvalue())
        with redirect_stdout(io.StringIO()) as output:
            self.assertTrue(runner.final_summary([],evidence,['in/changed.eml']))
        self.assertIn('SOURCE INTEGRITY FAIL: in/changed.eml',output.getvalue())

    def test_clear_does_not_follow_links(self):
        outside=self.root/'preserved';outside.mkdir();(outside/'keep').write_text('keep')
        (self.root/'out').symlink_to(outside,target_is_directory=True)
        with self.assertRaises(RuntimeError):runner.clear_output(self.root)
        (self.root/'out').unlink();(self.root/'out').mkdir()
        (self.root/'out'/'link').symlink_to(outside,target_is_directory=True)
        (self.root/'out'/'old.pdf').write_text('old')
        runner.clear_output(self.root)
        self.assertEqual('keep',(outside/'keep').read_text())
        self.assertEqual(set(runner.KINDS),{p.name for p in (self.root/'out').iterdir()})
    def test_failed_publication_preserves_previous_file_and_cleans_temporary_copy(self):
        original=self.source('source.eml')
        output=self.root/'out';output.mkdir()
        destination=output/'ready.pdf';destination.write_bytes(b'previous')
        with patch.object(runner.shutil,'copystat',side_effect=OSError('copy failed')):
            with self.assertRaises(OSError):runner.publish_pdf(original,destination)
        self.assertEqual(b'previous',destination.read_bytes())
        self.assertEqual([destination],list(output.iterdir()))
    def test_validation_status_uses_small_sidecar_without_loading_full_report(self):
        report=self.root/'large.validation.json'
        report.write_bytes(b'Full report deliberately not parsed')
        Path(str(report)+'.summary.json').write_text(json.dumps({'summary':{'status':'INCOMPLETE'},'security':VERIFIED_SECURITY}))
        self.assertEqual('INCOMPLETE',runner.validation_status(report))

    def test_report_allowances_share_available_disk_and_preserve_explicit_limits(self):
        pdf=self.root/'large.pdf';pdf.write_bytes(b'PDF')
        with patch.object(runner.shutil,'disk_usage',return_value=SimpleNamespace(free=80*1024**3)):
            env=runner.validation_environment({'UNCHANGED':'value'},pdf,workers=4)
            self.assertEqual(str(5*1024**3),env['VALIDATOR_MAX_WORK_FILE_BYTES'])
            self.assertEqual(str(10*1024**3),env['VALIDATOR_MAX_SCRATCH_BYTES'])
            self.assertEqual('value',env['UNCHANGED'])
            original={'VALIDATOR_MAX_WORK_FILE_BYTES':'1024','VALIDATOR_MAX_SCRATCH_BYTES':'2048'}
            self.assertEqual(original,runner.validation_environment(original,pdf,workers=4))
            self.assertEqual({'VALIDATOR_MAX_WORK_FILE_BYTES':'1024','VALIDATOR_MAX_SCRATCH_BYTES':'2048'},original)

    def test_working_storage_is_on_project_disk_and_cleans_only_its_own_directory(self):
        root=self.root/'.generator_web_tmp';root.mkdir()
        unrelated=root/'another-job';unrelated.mkdir();(unrelated/'keep').write_text('preserve')
        with runner.working_directory() as folder:
            path=Path(folder);self.assertEqual(root,path.parent)
            (path/'scratch').mkdir();(path/'scratch/file').write_text('temporary')
        self.assertFalse(path.exists());self.assertEqual('preserve',(unrelated/'keep').read_text())
        try:
            with runner.working_directory() as folder:
                failed=Path(folder);(failed/'partial').write_text('temporary')
                raise RuntimeError('failed conversion')
        except RuntimeError:pass
        self.assertFalse(failed.exists());self.assertTrue(unrelated.exists())

    def test_running_jobs_use_immutable_jar_snapshots(self):
        evidence=self.root/'evidence';evidence.mkdir()
        creator=self.root/'creator-1.jar';creator.write_bytes(b'creator version one')
        validator=self.root/'validator-1.jar';validator.write_bytes(b'validator version one')
        args=SimpleNamespace(creator_jar=creator,validator_jar=validator)
        runner.snapshot_jars(args,evidence)
        creator.write_bytes(b'new creator');validator.unlink()
        self.assertEqual(b'creator version one',args.creator_jar.read_bytes())
        self.assertEqual(b'validator version one',args.validator_jar.read_bytes())
        self.assertEqual('creator-1.jar',args.creator_jar.name)
        self.assertEqual({'creator','validator'},set(json.loads((evidence/'runtime-jars.json').read_text())))
    def test_isolated_publication_reports_and_original_hashes(self):
        source=self.source('space name.eml')
        runner.clear_output(self.root)
        evidence=self.root/'evidence';evidence.mkdir()
        workspace=self.root/'workspace';workspace.mkdir()
        args=SimpleNamespace(security_classes=Path('/tmp/classes'),jobs=2,creator_jar=Path('creator.jar'),validator_jar=Path('validator.jar'),timeout=60)
        config=self.root/'config.json';config.write_text(json.dumps({'signing':{'policyPath':'policy.json','enabled':True,'c2pa':True,'protection':'PADES_B_LTA'}}))
        calls=[]
        def execute(command,log,timeout,env):
            if 'eapdf.RegenerationSecurity' in command:
                log.write_text('security verified')
                Path(command[-1]).write_text(json.dumps({'status':'UNKNOWN','requestedProtectionMet':True,'padesBLta':True,
                    'checks':{'c2pa.signature':{'status':'PASS'},'c2pa.binding':{'status':'PASS'}}}))
                return {'exit_code':0,'timeout':False,'seconds':0.1}
            calls.append((command,env))
            log.write_text('ok')
            if '-jar' in command:
                out=Path(command[command.index('--out')+1])
                (out/'one.pdf').write_bytes(b'%PDF-test')
                (out/'two.pdf').write_bytes(b'%PDF-test-two')
                (out/'progress.json').write_text('{}')
            else:
                published=list((self.root/'out/eml').glob('*.pdf'))
                self.assertEqual(2,len(published),'all converted PDFs must be visible before validation begins')
                progress=json.loads(next(evidence.rglob('result.json')).read_text())
                self.assertEqual('VALIDATING',progress['status'])
                self.assertEqual(2,len(progress['documents']))
                self.assertEqual(2,len(list((self.root/'out/eml').iterdir())), 'no incomplete publication files remain')
                Path(command[-1]).write_text(json.dumps({'summary':{'status':'PASS'},'security':VERIFIED_SECURITY}))
            return {'exit_code':0,'timeout':False,'seconds':0.1}
        _,tasks=runner.tasks_for([source],'everything')
        with patch.object(runner,'execute',execute):
            result=runner.run_task(next(t for t in tasks if t['profile']=='1s'),args,evidence,workspace,config,{str(source):runner.digest(source)},2*1024**3)
        self.assertEqual('PASS',result['status'])
        pdf=next((self.root/'out/eml').iterdir())
        self.assertTrue(pdf.name.endswith('_1s.pdf'))
        self.assertEqual(2,len(list((self.root/'out').rglob('*.*'))))
        self.assertTrue(list(evidence.rglob('progress.json')))
        self.assertTrue(list(evidence.rglob('*.validation.json')))
        self.assertEqual('1',calls[0][1]['VALIDATOR_CHILD_PROCESSES'])
        self.assertEqual('policy.json',calls[-1][1]['EAPDF_SECURITY_POLICY'])
        self.assertEqual('PADES_B_LTA',calls[-1][1]['EAPDF_SECURITY_REQUIRED_PROTECTION'])
        self.assertEqual(str(2*1024**3),calls[0][1]['EAPDF_WORKER_HEAP_BYTES'])
        self.assertEqual('source space name.eml',source.read_text())

if __name__=='__main__':unittest.main()
