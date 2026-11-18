import type {BrowserDocument,BrowserSession} from './browser-session.js';
import type {SecurityInput,SecurityReport,SecurityFacts} from './security-types.js';
import {saveBlob} from './downloads.js';
function checkLabel(name:string):string {
  const labels:Record<string,string>={'Selected trust policy':'Trusted CA certificates you selected','C2PA claim signature':'C2PA provenance: signature','C2PA file binding':'C2PA provenance: matches this file','C2PA signing certificate path':'C2PA signer: certificate trust','C2PA signing certificate revocation':'C2PA signer: certificate not withdrawn','Offline evidence scope':'Where the evidence comes from','PAdES archival level and allowed revisions':'Long-term protection and later changes','Browser-clock date reference':'Certificate dates according to this device'};
  if(labels[name])return labels[name];
  for(const [pattern,wording]of [
    [/^Signature timestamp (\d+) certificate trust$/,'Signature $1: trust in timestamp provider'],
    [/^Signature timestamp (\d+) revocation$/,'Signature $1: timestamp provider’s certificate not withdrawn'],
    [/^Signature timestamp (\d+) signature$/,'Signature $1: timestamp matches signing event'],
    [/^Signature timestamp (\d+)$/,'Signature $1: trusted signing time'],
    [/^PDF signature (\d+) certificate trust$/,'PDF signature $1: signing certificate trust'],
    [/^PDF signature (\d+) revocation$/,'PDF signature $1: certificate not withdrawn'],
    [/^PDF signature (\d+)$/,'PDF signature $1: signed content'],
    [/^Document timestamp (\d+) certificate trust$/,'File timestamp $1: trust in timestamp provider'],
    [/^Document timestamp (\d+) revocation$/,'File timestamp $1: provider’s certificate not withdrawn'],
    [/^Document timestamp (\d+)$/,'File timestamp $1: signed content'],
    [/^Trusted timestamp (\d+)$/,'File timestamp $1: trusted date and time']
  ]as const)if(pattern.test(name))return name.replace(pattern,wording);
  return name;
}
/** One verification per opened outer file. Child viewing stays within that file's protection. */
export class SecurityController {
  private worker?:Worker;private epoch=0;private report?:SecurityReport;private timer?:ReturnType<typeof setTimeout>;
  private roots:Uint8Array[]=[];private active?:{session:BrowserSession;doc:BrowserDocument};
  private readonly filename=document.getElementById('header-filename')!;
  private readonly dialog=document.createElement('dialog');private readonly state=document.createElement('p');private readonly rows=document.createElement('dl');private readonly download=document.createElement('button');
  constructor(){
    this.dialog.id='security-dialog';this.dialog.setAttribute('aria-labelledby','security-title');
    const heading=document.createElement('h2');heading.id='security-title';heading.textContent='Security report';
    const guidance=document.createElement('p');guidance.id='security-guidance';
    guidance.textContent='For full verification, including certificate trust, revocation and trusted timestamps, use the standalone EA-PDF Validator with your trust JSON.';
    this.state.id='security-summary';this.state.setAttribute('role','status');this.rows.id='security-checks';
    const close=document.createElement('button');close.id='security-close';close.type='button';close.textContent='Close';close.autofocus=true;close.onclick=()=>this.dialog.close();
    this.download.id='security-download';this.download.type='button';this.download.textContent='Download report';this.download.disabled=true;
    this.download.onclick=()=>{const report=this.report;if(report)void saveBlob(new Blob([JSON.stringify(report,null,2)+'\n'],{type:'application/json'}),'eapdf-security-report.json',()=>{},this.dialog);};
    const actions=document.createElement('div');actions.className='security-actions';actions.append(this.download,close);
    const choose=document.createElement('button');choose.id='security-trust-choose';choose.type='button';choose.textContent='Choose trusted roots (.pem, .crt, .cer, .der)…';
    const clear=document.createElement('button');clear.id='security-trust-clear';clear.type='button';clear.textContent='Clear trusted roots';
    const picker=document.createElement('input');picker.id='security-trust-files';picker.type='file';picker.multiple=true;picker.accept='.pem,.crt,.cer,.der';picker.hidden=true;
    choose.onclick=()=>picker.click();
    clear.onclick=()=>{this.roots=[];if(this.active)void this.start(this.active.session,this.active.doc);};
    picker.onchange=()=>{void (async()=>{
      try {
        const files=Array.from(picker.files??[]);if(!files.length)return;
        if(files.length>32||files.reduce((n,f)=>n+f.size,0)>1048576)throw Error('Select at most 32 CA certificates, totalling at most 1 MiB.');
        const roots:Uint8Array[]=[];
        for(const file of files){
          const bytes=new Uint8Array(await file.arrayBuffer()),text=new TextDecoder().decode(bytes);
          if(/PRIVATE KEY|PKCS12/.test(text))throw Error('Select public CA certificates only. Private signing credentials are not needed.');
          if(text.includes('-----BEGIN')){
            const blocks=[...text.matchAll(/-----BEGIN CERTIFICATE-----\s*([A-Za-z0-9+/=\s]+?)\s*-----END CERTIFICATE-----/g)];
            if(!blocks.length)throw Error('No PEM certificate found. Choose PEM/DER CA certificates.');
            for(const block of blocks)roots.push(Uint8Array.from(atob(block[1]!.replace(/\s/g,'')),c=>c.charCodeAt(0)));
          }else roots.push(bytes);
        }
        if(roots.length>32)throw Error('Select at most 32 CA certificates.');
        const combined=[...new Map([...this.roots,...roots].map(bytes=>[Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join(''),bytes])).values()];
        if(combined.length>32||combined.reduce((n,b)=>n+b.length,0)>1048576)throw Error('At most 32 CA roots and 1 MiB can be selected in this session. Clear roots before replacing them.');
        this.roots=combined;if(this.active)await this.start(this.active.session,this.active.doc);
      }catch(error){this.state.textContent=error instanceof Error?error.message:String(error);}
      finally{picker.value='';}
    })();};
    const trustActions=document.createElement('div');trustActions.className='security-trust-actions';trustActions.append(choose,clear,picker);
    const trustHelp=document.createElement('p');trustHelp.textContent='Add independently trusted CA root certificates (PEM/DER), including from separate folders. The viewer uses embedded CRL/OCSP evidence offline. Selected roots stay in this session; no private key is needed.';
    this.dialog.append(heading,guidance,this.state,trustActions,trustHelp,actions,this.rows);document.body.append(this.dialog);
    this.filename.setAttribute('role','button');this.filename.setAttribute('tabindex','0');this.filename.setAttribute('aria-controls',this.dialog.id);
    this.filename.addEventListener('click',()=>this.open());this.filename.addEventListener('keydown',event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();this.open();}});
  }
  private open(){if(this.filename.dataset.security&&!this.dialog.open){this.dialog.showModal();this.dialog.scrollTop=0;}}
  reset(){this.epoch++;clearTimeout(this.timer);this.worker?.terminate();this.worker=undefined;this.report=undefined;this.active=undefined;this.download.disabled=true;this.dialog.close();this.rows.replaceChildren();this.state.textContent='';delete this.filename.dataset.security;this.filename.removeAttribute('aria-label');}
  async start(session:BrowserSession,doc:BrowserDocument){
    const reopen=this.dialog.open;this.reset();this.active={session,doc};const epoch=this.epoch,check=()=>{if(epoch!==this.epoch)throw Error('Security validation cancelled');};
    const label=(text:string)=>{this.state.textContent=`${doc.name}: ${text}`;this.filename.title=`${doc.name} · ${text} · Open security report`;this.filename.setAttribute('aria-label',`${doc.name}: ${text}. Open security report.`);};
    this.filename.dataset.security='checking';label('Checking export security…');
    const finish=(report:SecurityReport)=>{check();clearTimeout(this.timer);this.worker?.terminate();this.worker=undefined;report.filename=doc.name;report.fileSha256=doc.inspection.documentId.replace(/^sha256:/,'');this.report=report;this.download.disabled=false;this.filename.dataset.security=report.status.toLowerCase();
      const certificateChecks=report.checks.filter(c=>/certificate trust$|certificate path$| revocation$/.test(c.name));
      const trusted=certificateChecks.length>0&&certificateChecks.every(c=>c.status==='PASS');
      label(report.status==='PASS'?(trusted?'Covered content and offline certificate checks verified':'Covered content verified; certificate trust incomplete'):report.status==='FAIL'?'Security validation failed':report.status==='NOT_APPLICABLE'?'Unsigned / no verifiable export protection':'Security validation incomplete');
      this.rows.replaceChildren();const scopeLabel=document.createElement('dt'),scope=document.createElement('dd');scopeLabel.textContent='What this report covers';scope.textContent=report.scope;this.rows.append(scopeLabel,scope);
      const states={PASS:'Verified',FAIL:'Failed',UNKNOWN:'Not verified',NOT_APPLICABLE:'Information'};
      for(const row of report.checks){const term=document.createElement('dt'),detail=document.createElement('dd');term.textContent=`${checkLabel(row.name)}: ${states[row.status]}`;term.title=row.name;detail.textContent=row.detail;this.rows.append(term,detail);}
      for(const certificate of report.certificates){const term=document.createElement('dt'),detail=document.createElement('dd');term.textContent='Included signing certificate';detail.textContent=`${certificate.subject}\nSHA-256: ${certificate.sha256}\n${certificate.notBefore} to ${certificate.notAfter}`;this.rows.append(term,detail);}
      if(reopen&&!this.dialog.open){this.dialog.showModal();this.dialog.scrollTop=0;}
    };
    let fileSize=0;
    const unknown=(detail:string):SecurityReport=>({schemaVersion:1,status:'UNKNOWN',checkedAt:new Date().toISOString(),fileSize,scope:'Security validation did not complete. No integrity or trust pass is asserted.',certificates:[],checks:[{name:'Security validation',status:'UNKNOWN',detail}]});
    // The deadline covers extraction as well as crypto; reset/close terminates the worker.
    this.timer=setTimeout(()=>{if(epoch===this.epoch){finish(unknown('Security validation exceeded its 120-second deadline. Use the Java validator for this file.'));this.epoch++;}},120000);
    try{
      const blob=await session.sourceBlob(doc);check();fileSize=blob.size;
      let facts:SecurityFacts={signatures:[],encrypted:false},credentials:Uint8Array|undefined,policy:Uint8Array|undefined,discoveryError:string|undefined;
      try{
        // Security has its own deadline. Its expiry must never close the reading worker.
        facts=await doc.pdf.getDocumentAssets({securityOnly:true});check();
        const assets=session.cachedAssets(doc);
        for(const [name,limit]of [['eapdf-content-credentials.c2pa',32768],['eapdf-c2pa-policy.json',1024]] as const){
          const matches=assets.files.filter(f=>f.unicodeFilename.value===name||f.filename.value===name);
          if(matches.length>1)throw Error('Ambiguous '+name);const file=matches[0];if(!file)continue;
          if(!file.origins.some(o=>o.kind==='catalog-af')||file.streams.length!==1)throw Error('Invalid security file association');
          const stream=file.streams[0]!;const bytes=await doc.pdf.getAssociatedFileBytes({documentId:assets.documentId,fileId:file.locator.path,streamId:stream.locator.path,maxBytes:limit});check();
          if(name.endsWith('.c2pa'))credentials=bytes;else policy=bytes;
        }
        if(assets.files.some(f=>f.relationship.value==='C2PA_Manifest'&&f.unicodeFilename.value!=='eapdf-content-credentials.c2pa'&&f.filename.value!=='eapdf-content-credentials.c2pa'))throw Error('Unsupported C2PA application profile');
      }catch(error){check();discoveryError=error instanceof Error?error.message:String(error);}
      const input:SecurityInput={blob,facts,credentials,policy,discoveryError,trustRoots:this.roots};
      const worker=new Worker(new URL('./security-worker.js',import.meta.url),{type:'module'});this.worker=worker;
      worker.onmessage=event=>{if(epoch!==this.epoch)return;const data=event.data;if(data.progress)label(data.progress);else if(data.report)finish(data.report);else if(data.error)finish(unknown(data.error));};
      worker.onerror=()=>{if(epoch===this.epoch)finish(unknown('The security worker could not complete validation.'));};
      worker.postMessage(input);
    }catch(error){if(epoch===this.epoch)finish(unknown(error instanceof Error?error.message:String(error)));}
  }
}
