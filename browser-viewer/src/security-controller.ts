import type {BrowserDocument,BrowserSession} from './browser-session.js';
import type {SecurityInput,SecurityReport,SecurityFacts,CreatorArchiveFacts,SecurityCategory} from './security-types.js';
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
function element<K extends keyof HTMLElementTagNameMap>(tag:K,className='',text=''):HTMLElementTagNameMap[K]{
  const node=document.createElement(tag);node.className=className;node.textContent=text;return node;
}
type DisplayCertificate=SecurityReport['certificates'][number]&{issuer?:string};
/** One verification per opened outer file. Child viewing stays within that file's protection. */
export class SecurityController {
  private worker?:Worker;private epoch=0;private report?:SecurityReport;private timer?:ReturnType<typeof setTimeout>;
  private archivalCache=new WeakMap<BrowserDocument,{key:string;facts:Promise<CreatorArchiveFacts>}>();
  private roots:Uint8Array[]=[];private active?:{session:BrowserSession;doc:BrowserDocument};
  private readonly filename=document.getElementById('header-filename')!;
  private readonly dialog=element('dialog');private readonly state=element('p');private readonly rows=element('div');private readonly download=element('button');
  private readonly file=element('p','security-file');private readonly badge=element('span','security-result');
  private readonly counts=element('p','security-counts');
  private readonly selected=element('div','security-certificates');private readonly selectedCount=element('span','security-section-count');
  private readonly included=element('div','security-certificates');private readonly includedTitle=element('summary');
  private readonly checkNodes=new Map<string,HTMLDetailsElement>();
  private readonly selectedNodes=new Map<string,HTMLDetailsElement>();private readonly includedNodes=new Map<string,HTMLDetailsElement>();
  private readonly groups=new Map<SecurityCategory,{section:HTMLElement;list:HTMLElement;count:HTMLElement}>();
  constructor(){
    this.dialog.id='security-dialog';this.dialog.setAttribute('aria-labelledby','security-title');
    const header=element('header','security-header'),title=element('div','security-heading');
    const heading=element('h2','','Security report');heading.id='security-title';title.append(heading,this.file);
    const close=element('button','','Close');close.id='security-close';close.type='button';close.autofocus=true;close.onclick=()=>this.dialog.close();
    this.download.id='security-download';this.download.type='button';this.download.textContent='Download report';this.download.disabled=true;
    this.download.onclick=()=>{const report=this.report;if(report)void saveBlob(new Blob([JSON.stringify(report,null,2)+'\n'],{type:'application/json'}),'eapdf-security-report.json',()=>{},this.dialog);};
    const actions=element('div','security-actions');actions.append(this.download,close);header.append(title,actions);
    const body=element('div','security-body'),overview=element('section','security-overview');
    this.state.id='security-summary';this.state.setAttribute('role','status');
    overview.append(this.badge,this.state,this.counts);
    const trust=element('section','security-trust-panel'),trustHeading=element('div','security-section-heading');
    trustHeading.append(element('h3','','Trusted certificates · shared trust'),this.selectedCount);
    const choose=element('button','security-primary','Add certificates…');choose.id='security-trust-choose';choose.type='button';
    const clear=element('button','','Clear selection');clear.id='security-trust-clear';clear.type='button';
    const picker=element('input');picker.id='security-trust-files';picker.type='file';picker.multiple=true;picker.accept='.pem,.crt,.cer,.der';picker.hidden=true;
    choose.onclick=()=>picker.click();clear.onclick=()=>{this.roots=[];if(this.active)void this.start(this.active.session,this.active.doc,true);};
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
        this.roots=combined;if(this.active)await this.start(this.active.session,this.active.doc,true);
      }catch(error){this.state.textContent=error instanceof Error?error.message:String(error);}
      finally{picker.value='';}
    })();};
    const trustActions=element('div','security-trust-actions');trustActions.append(choose,clear,picker);
    const trustHelp=element('p','security-help','You can select multiple PEM, CRT, CER or DER files. Selections stay in this session.');
    this.selected.id='security-selected-certificates';trust.append(trustHeading,trustHelp,trustActions,this.selected);
    this.rows.id='security-checks';
    for(const name of ['C2PA','PAdES','C2PA + PAdES','CMS','Other checks']as const){
      const section=element('section','security-check-group'),heading=element('div','security-section-heading'),count=element('span','security-section-count'),list=element('div','security-group-list');
      section.dataset.category=name;heading.append(element('h3','',name),count);section.append(heading,list);section.hidden=true;this.rows.append(section);this.groups.set(name,{section,list,count});
    }
    const certificates=element('details','security-included');this.includedTitle.textContent='Included signing certificates';certificates.append(this.includedTitle,this.included);
    const guidance=element('p','security-help');guidance.id='security-guidance';guidance.textContent='Additional archival checks cover the supported EA-PDF creator workflow. Other files skip those checks.';
    body.append(overview,trust,this.rows,certificates,guidance);this.dialog.append(header,body);document.body.append(this.dialog);
    this.filename.setAttribute('role','button');this.filename.setAttribute('tabindex','0');this.filename.setAttribute('aria-controls',this.dialog.id);
    this.filename.addEventListener('click',()=>this.open());this.filename.addEventListener('keydown',event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();this.open();}});
  }
  private certificates(container:HTMLElement,nodes:Map<string,HTMLDetailsElement>,values:DisplayCertificate[],empty:string){
    const retained=new Set(values.map(c=>c.sha256));
    for(const [key,node]of nodes)if(!retained.has(key)){node.remove();nodes.delete(key);}
    container.querySelector('.security-empty')?.remove();
    for(const certificate of values){
      if(nodes.has(certificate.sha256))continue;
      const card=element('details','security-certificate'),title=element('summary','',certificate.subject),fields=element('dl');
      for(const [label,value]of [['Subject',certificate.subject],['Issuer',certificate.issuer],['SHA-256',certificate.sha256],['Valid from',certificate.notBefore],['Valid until',certificate.notAfter]]){
        if(!value)continue;fields.append(element('dt','',label!),element('dd',label==='SHA-256'?'security-fingerprint':'',value));
      }
      card.append(title,fields);container.append(card);nodes.set(certificate.sha256,card);
    }
    if(!values.length)container.append(element('p','security-empty',empty));
  }
  private render(report:SecurityReport){
    const scroll=this.dialog.scrollTop,visibleTop=this.dialog.getBoundingClientRect().top+this.dialog.querySelector('.security-header')!.getBoundingClientRect().height;
    const anchor=scroll>0?[...this.rows.querySelectorAll<HTMLDetailsElement>('.security-check')].find(node=>node.getBoundingClientRect().bottom>visibleTop&&node.getBoundingClientRect().top<this.dialog.getBoundingClientRect().bottom):undefined,anchorTop=anchor?.getBoundingClientRect().top;
    const states={PASS:'Verified',FAIL:'Failed',UNKNOWN:'Not verified',NOT_APPLICABLE:'Information'};
    this.dialog.dataset.updating='false';this.dialog.setAttribute('aria-busy','false');
    this.badge.dataset.status=report.status;this.badge.textContent=report.status==='PASS'?'Integrity verified':report.status==='NOT_APPLICABLE'?'Unsigned':states[report.status];
    this.counts.textContent=(['PASS','UNKNOWN','FAIL']as const).map(status=>`${report.checks.filter(c=>c.status===status).length} ${states[status].toLowerCase()}`).join(' · ');
    const selected=report.selectedCertificates??[];this.selectedCount.textContent=`${selected.length} selected`;
    this.certificates(this.selected,this.selectedNodes,selected,this.roots.length?'Certificate details are unavailable for this check.':'No CA certificates selected. Add certificates to verify trust.');
    this.certificates(this.included,this.includedNodes,report.certificates,'No signing certificates included.');this.includedTitle.textContent=`Included signing certificates (${report.certificates.length})`;
    const retained=new Set(report.checks.map(c=>c.name));
    for(const [key,node]of this.checkNodes)if(!retained.has(key)){node.remove();this.checkNodes.delete(key);}
    for(const row of report.checks){
      let node=this.checkNodes.get(row.name);
      if(!node){node=element('details','security-check');node.title=row.name;const summary=element('summary'),name=element('span','security-check-name'),result=element('span','security-result'),detail=element('p','security-check-detail');summary.append(name,result);node.append(summary,detail);this.checkNodes.set(row.name,node);}
      const category=row.category??'Other checks',group=this.groups.get(category)!;
      node.dataset.category=category;if(node.parentElement!==group.list)group.list.append(node);
      node.querySelector('.security-check-name')!.textContent=`${checkLabel(row.name)}: `;
      const result=node.querySelector<HTMLElement>('.security-result')!;result.textContent=states[row.status];result.dataset.status=row.status;node.querySelector('.security-check-detail')!.textContent=row.detail;
    }
    for(const {section,list,count}of this.groups.values()){const size=list.children.length;section.hidden=!size;count.textContent=`${size} checks`;}
    this.dialog.scrollTop=scroll+(anchor?.isConnected&&anchorTop!==undefined?anchor.getBoundingClientRect().top-anchorTop:0);
  }
  private open(){if(this.filename.dataset.security&&!this.dialog.open){this.dialog.showModal();this.dialog.scrollTop=0;}}
  private cancel(){this.epoch++;clearTimeout(this.timer);this.worker?.terminate();this.worker=undefined;this.download.disabled=true;}
  reset(){this.cancel();this.report=undefined;this.active=undefined;this.dialog.close();this.checkNodes.clear();for(const {section,list}of this.groups.values()){list.replaceChildren();section.hidden=true;}this.included.replaceChildren();this.includedNodes.clear();this.includedTitle.textContent='Included signing certificates';this.state.textContent='';this.counts.textContent='';this.selected.replaceChildren();this.included.replaceChildren();this.selectedNodes.clear();this.includedNodes.clear();this.selectedCount.textContent='';this.includedTitle.textContent='Included signing certificates';delete this.filename.dataset.security;this.filename.removeAttribute('aria-label');}
  async start(session:BrowserSession,doc:BrowserDocument,refresh=false){
    if(refresh)this.cancel();else this.reset();this.active={session,doc};const epoch=this.epoch,check=()=>{if(epoch!==this.epoch)throw Error('Security validation cancelled');};
    this.file.textContent=doc.name;this.file.title=doc.name;this.dialog.dataset.updating='true';this.dialog.setAttribute('aria-busy','true');this.badge.dataset.status='CHECKING';this.badge.textContent=refresh?'Updating checks…':'Checking…';
    const label=(text:string)=>{this.state.textContent=text;this.filename.title=`${doc.name} · ${text} · Open security report`;this.filename.setAttribute('aria-label',`${doc.name}: ${text}. Open security report.`);};
    this.filename.dataset.security='checking';label(refresh?'Rechecking with your certificate selection…':'Checking export security…');
    const finish=(report:SecurityReport)=>{check();clearTimeout(this.timer);this.worker?.terminate();this.worker=undefined;report.filename=doc.name;report.fileSha256=doc.inspection.documentId.replace(/^sha256:/,'');this.report=report;this.download.disabled=false;this.filename.dataset.security=report.status.toLowerCase();
      const certificateChecks=report.checks.filter(c=>/certificate trust$|certificate path$| revocation$/.test(c.name));
      const trusted=certificateChecks.length>0&&certificateChecks.every(c=>c.status==='PASS');
      label(report.status==='PASS'?(trusted?'Covered content and offline certificate checks verified':'Covered content verified; certificate trust incomplete'):report.status==='FAIL'?'Security validation failed':report.status==='NOT_APPLICABLE'?'Unsigned / no verifiable export protection':'Security validation incomplete');
      this.render(report);
    };
    let fileSize=0;
    const unknown=(detail:string):SecurityReport=>({schemaVersion:1,status:'UNKNOWN',checkedAt:new Date().toISOString(),fileSize,scope:'Security validation did not complete. No integrity or trust pass is asserted.',certificates:[],checks:[{name:'Security validation',status:'UNKNOWN',detail,category:'Other checks'}]});
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
      worker.onmessage=event=>{if(epoch!==this.epoch)return;const data=event.data;
        if(data.archivalRequest){
          const ends=data.archivalRequest.ends;
          if(!Array.isArray(ends)||ends.length>64||!ends.every(Number.isSafeInteger)){worker.postMessage({archivalResponse:{schemaVersion:1,status:'UNAVAILABLE',detail:'Invalid archival inspection request.'}});return;}
          const key=ends.join(','),cached=this.archivalCache.get(doc);
          const facts=cached?.key===key?cached.facts:Promise.resolve().then(()=>doc.pdf.getDocumentAssets({securityOnly:true,creatorArchival:ends}) as Promise<CreatorArchiveFacts>).catch(error=>({schemaVersion:1 as const,status:'UNAVAILABLE' as const,detail:error instanceof Error?error.message:String(error)}));
          this.archivalCache.set(doc,{key,facts});
          void facts.then(result=>{if(epoch===this.epoch)worker.postMessage({archivalResponse:result});});return;
        }
        if(data.progress)label(data.progress);else if(data.report)finish(data.report);else if(data.error)finish(unknown(data.error));};
      worker.onerror=()=>{if(epoch===this.epoch)finish(unknown('The security worker could not complete validation.'));};
      worker.postMessage(input);
    }catch(error){if(epoch===this.epoch)finish(unknown(error instanceof Error?error.message:String(error)));}
  }
}
