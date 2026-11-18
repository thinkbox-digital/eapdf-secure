import {sha256Blob} from './blob-hash.js';
import {sha256} from './domain/hashes.js';
import {verifyCms,hex,requireSecurity,UnsupportedSecurity} from './security-cms.js';
import {verifyC2pa} from './security-c2pa.js';
import {OfflineTrust} from './security-trust.js';
import type {SecurityInput,SecurityReport,PdfSignatureFacts} from './security-types.js';
export async function signatureRange(blob:Blob,signature:PdfSignatureFacts):Promise<{covered:Blob;end:number;gap:[number,number]}> {
  const [zero,start,end,size]=signature.byteRange;
  requireSecurity(signature.byteRange.length===4&&signature.byteRange.every(Number.isSafeInteger)&&zero===0&&start!>0&&end!>start!&&size!>=0&&end!<=blob.size&&size!<=blob.size-end!&&end!-start!<=2*1024*1024+2,'Invalid PDF signature byte ranges');
  const encoded=await blob.slice(start,end).text();
  requireSecurity(/^<[a-fA-F0-9]+>$/.test(encoded)&&encoded.length%2===0&&encoded.slice(1,-1).toLowerCase()===hex(signature.contents),'Signature gap does not match this signature Contents');
  return {covered:new Blob([blob.slice(0,start),blob.slice(end,end!+size!)]),end:end!+size!,gap:[start!,end!]};
}
export async function validateSecurity(input:SecurityInput,check:()=>void=()=>{},progress:(value:string)=>void=()=>{}):Promise<SecurityReport>{
  const started=performance.now();
  const {blob,facts}=input;
  const report:SecurityReport={schemaVersion:1,status:'UNKNOWN',checks:[],checkedAt:new Date().toISOString(),fileSize:blob.size,scope:'This PDF and the files it contains. Export signatures do not verify who sent the original emails or whether the PDF follows the EA-PDF specification.',certificates:[]};
  let verified=false,incomplete=false,failed=false;
  const trust=new OfflineTrust(facts.evidence,input.trustRoots??[],check);
  report.checks.push(await trust.policy());check();
  const assess=async(result:Awaited<ReturnType<typeof verifyCms>>,name:string,when:Date,timestamp=false)=>{
    const checked=await trust.assess(result.signer,result.certificates,when,timestamp);check();
    report.checks.push({name:name+' certificate trust',...checked.trust},{name:name+' revocation',...checked.revocation});
    if(checked.trust.status==='FAIL'||checked.revocation.status==='FAIL')failed=true;
    return checked.trust.status==='PASS'&&checked.revocation.status==='PASS';
  };
  const problem=(name:string,error:unknown)=>{const unknown=error instanceof UnsupportedSecurity;incomplete||=unknown;failed||=!unknown;report.checks.push({name,status:unknown?'UNKNOWN':'FAIL',detail:error instanceof Error?error.message:String(error)});};
  if(input.discoveryError){report.checks.push({name:'Security discovery',status:'UNKNOWN',detail:input.discoveryError});incomplete=true;}
  if(facts.encrypted){report.checks.push({name:'Encrypted PDF',status:'UNKNOWN',detail:'Security validation of encrypted documents is unsupported.'});incomplete=true;}
  const signatures=facts.signatures.slice().sort((a,b)=>(a.byteRange[2]!+a.byteRange[3]!)-(b.byteRange[2]!+b.byteRange[3]!));
  let latestGap:[number,number]|undefined;
  for(const [index,signature]of signatures.entries()){
    check();progress(`Checking PDF signature ${index+1} of ${signatures.length}…`);
    const name=signature.type==='DocTimeStamp'?'Document timestamp':'PDF signature';
    try{
      const range=await signatureRange(blob,signature);
      if(index===signatures.length-1){latestGap=range.gap;requireSecurity(range.end===blob.size,'The final signature does not cover the current file; later bytes are present');}
      if(!['adbe.pkcs7.detached','ETSI.CAdES.detached','ETSI.RFC3161'].includes(signature.subFilter))throw new UnsupportedSecurity('Unsupported PDF signature SubFilter: '+signature.subFilter);
      const timestamp=signature.subFilter==='ETSI.RFC3161';
      requireSecurity(timestamp===(signature.type==='DocTimeStamp'),'Inconsistent PDF timestamp type');
      const digest=await sha256Blob(range.covered,check),result=await verifyCms(signature.contents,digest,report,timestamp);check();
      report.checks.push({name:`${name} ${index+1}`,status:'PASS',detail:`${timestamp?'The timestamp signature and file fingerprint':'The signature and protected file content'} match through byte ${range.end.toLocaleString()}.${timestamp?' Recorded time: '+result.time!.toISOString()+'.':''} ${range.end===blob.size?'Covers the current file.':'This is an earlier saved version; the validator must check whether later changes are permitted.'}`});
      verified||=range.end===blob.size;
      let signingTime:Date|undefined;
      if(timestamp){
        const trusted=await assess(result,`${name} ${index+1}`,result.time!,true);
        report.checks.push({name:`Trusted timestamp ${index+1}`,status:trusted?'PASS':'UNKNOWN',detail:trusted?`Timestamp imprint, TSA path, usage and revocation verify at ${result.time!.toISOString()}.`:'Timestamp signature and imprint verify; selected trust and fresh revocation evidence must also verify to establish trusted time.'});
      }
      const timestamps=result.signed.signerInfos[0]!.unsignedAttrs?.attributes.filter(a=>a.type==='1.2.840.113549.1.9.16.2.14')??[];
      requireSecurity(timestamps.length<=1,'Ambiguous signature timestamps');
      if(timestamps.length){
        requireSecurity(timestamps[0]!.values.length===1,'Ambiguous timestamp token');
        const stamp=await verifyCms(new Uint8Array(timestamps[0]!.values[0]!.toBER(false)),await sha256(result.signature),report,true);
        report.checks.push({name:`Signature timestamp ${index+1} signature`,status:'PASS',detail:`The timestamp signature and fingerprint match this PDF signature. Recorded time: ${stamp.time!.toISOString()}. Trust in the timestamp provider is checked separately.`});
        const trusted=await assess(stamp,`Signature timestamp ${index+1}`,stamp.time!,true);
        if(trusted)signingTime=stamp.time;
        report.checks.push({name:`Signature timestamp ${index+1}`,status:trusted?'PASS':'UNKNOWN',detail:trusted?`Timestamp signature, imprint, TSA path, usage and revocation verify at ${stamp.time!.toISOString()}.`:'Timestamp signature and imprint verify; trusted time requires a verified TSA path and fresh revocation evidence.'});
      }
      if(!timestamp)await assess(result,`${name} ${index+1}`,signingTime??new Date());
    }catch(error){check();problem(`${name} ${index+1}`,error);}
  }
  if(input.credentials){
    try{
      const c2pa=await verifyC2pa(blob,input.credentials,input.policy,latestGap,report,check,progress);verified=true;
      const checked=await trust.assess(c2pa.signer,c2pa.certificates,new Date());check();
      report.checks.push({name:'C2PA signing certificate path',...checked.trust},{name:'C2PA signing certificate revocation',...checked.revocation});
      if(checked.trust.status==='FAIL'||checked.revocation.status==='FAIL')failed=true;
    }
    catch(error){check();problem('C2PA validation',error);}
  }else if(input.policy)problem('C2PA credentials',Error('C2PA policy is present without its manifest'));
  if(!signatures.length&&!input.credentials&&!input.policy&&!input.discoveryError){report.checks.push({name:'Export protection',status:'NOT_APPLICABLE',detail:'No PDF signature or C2PA export credentials were found. Unchanged-since-export status cannot be established.'});}
  report.checks.push(
    {name:'Offline evidence scope',status:'NOT_APPLICABLE',detail:'Uses only the CA certificates you selected and evidence stored inside the PDF. It does not download certificates or contact timestamp or certificate-status servers. This device’s clock is not proof of signing time. C2PA public trust-list checks are not included.'},
    {name:'PAdES archival level and allowed revisions',status:'UNKNOWN',detail:'Use the standalone EA-PDF Validator with your trust JSON to check long-term protection (PAdES B-LTA) and whether later changes to the file are permitted.'});
  if(report.certificates.length)report.checks.push({name:'Browser-clock date reference',status:'NOT_APPLICABLE',detail:report.certificates.every(c=>Date.parse(c.notBefore)<=Date.now()&&Date.now()<=Date.parse(c.notAfter))?'Included signing certificates are currently within their dates according to the browser clock. Applicable verification times and results appear in the individual certificate-path and timestamp checks.':'An included signing certificate is outside its dates at browser time. This alone does not invalidate an earlier trusted signature; inspect individual path/timestamp checks and use Java for full historical validation.'});
  report.status=failed?'FAIL':incomplete?'UNKNOWN':verified?'PASS':'NOT_APPLICABLE';report.elapsedMilliseconds=performance.now()-started;return report;
}
