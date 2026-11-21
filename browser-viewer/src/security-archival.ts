import {Sequence,OctetString,ObjectIdentifier,Null} from 'asn1js';
import {sha256} from './domain/hashes.js';
import {verifyCms,requireSecurity,UnsupportedSecurity,SHA256,hex} from './security-cms.js';
import {verifyC2pa} from './security-c2pa.js';
import {OfflineTrust} from './security-trust.js';
import type {CreatorArchiveFacts,CreatorArchiveRevision,PdfSignatureFacts,SecurityInput,SecurityReport,SecurityEvidence} from './security-types.js';

export type VerifiedPdfSignature={facts:PdfSignatureFacts;end:number;gap:[number,number];cms:Awaited<ReturnType<typeof verifyCms>>;stamp?:Awaited<ReturnType<typeof verifyCms>>};
const unsupported=(condition:unknown,message:string)=>{if(!condition)throw new UnsupportedSecurity(message);};
const sameBytes=(a:Uint8Array,b:Uint8Array)=>a.length===b.length&&a.every((value,index)=>value===b[index]);
const sameSignature=(a:PdfSignatureFacts,b:PdfSignatureFacts)=>a.type===b.type&&a.subFilter===b.subFilter&&a.byteRange.length===b.byteRange.length&&a.byteRange.every((v,i)=>v===b.byteRange[i])&&sameBytes(a.contents,b.contents);
const freshReport=(report:SecurityReport):SecurityReport=>({...report,checks:[],certificates:[]});

/** The creator and its current TSA use SHA-256 ESSCertIDv2. Other forms stay unknown. */
export async function checkSignerBinding(cms:Awaited<ReturnType<typeof verifyCms>>){
  unsupported(!cms.timestampInfo||cms.timestampInfo.version===1&&!cms.timestampInfo.extensions?.some(extension=>extension.critical),'Unsupported critical timestamp extension or timestamp version.');
  const attributes=cms.signed.signerInfos[0]!.signedAttrs!.attributes.filter(a=>a.type==='1.2.840.113549.1.9.16.2.47');
  requireSecurity(attributes.length===1&&attributes[0]!.values.length===1,'Missing or ambiguous SigningCertificateV2 signed attribute.');
  const value=attributes[0]!.values[0];
  unsupported(value instanceof Sequence&&value.valueBlock.value.length===1,'Unsupported SigningCertificateV2 structure.');
  const certs=(value as Sequence).valueBlock.value[0];
  unsupported(certs instanceof Sequence&&certs.valueBlock.value.length===1,'Unsupported ESS certificate list.');
  const identifier=(certs as Sequence).valueBlock.value[0];
  unsupported(identifier instanceof Sequence,'Unsupported ESS certificate identifier.');
  const parts=[...(identifier as Sequence).valueBlock.value];
  if(parts[0] instanceof Sequence){
    const algorithm=parts.shift() as Sequence,oid=algorithm.valueBlock.value[0];
    unsupported(oid instanceof ObjectIdentifier&&oid.valueBlock.toString()===SHA256&&algorithm.valueBlock.value.length<=2&&(algorithm.valueBlock.value.length===1||algorithm.valueBlock.value[1] instanceof Null),'Unsupported ESS certificate hash.');
  }
  unsupported(parts.length===1&&parts[0] instanceof OctetString,'Unsupported ESS issuer/serial or certificate hash.');
  requireSecurity(hex((parts[0] as OctetString).valueBlock.valueHexView)===await sha256(new Uint8Array(cms.signer.toSchema().toBER(false))),'Signed ESS certificate hash does not identify this signer.');
}

async function evidenceIds(evidence:SecurityEvidence,check:()=>void){
  unsupported(!evidence.error,'Archived validation evidence could not be read: '+evidence.error);
  const ids=new Set<string>();
  for(const kind of ['certificates','crls','ocsps']as const)for(const bytes of evidence[kind]){check();ids.add(kind+':'+await sha256(bytes));}
  return ids;
}

/** Strict support for our signed profile; this is not a general PDF change classifier. */
export async function validateCreatorArchive(input:SecurityInput,facts:CreatorArchiveFacts,verified:VerifiedPdfSignature[],report:SecurityReport,check:()=>void,progress:(message:string)=>void):Promise<Date>{
  requireSecurity(facts.status!=='INVALID',facts.detail??'Invalid saved creator revision.');
  unsupported(facts.schemaVersion===1&&facts.status==='COMPLETE'&&facts.revisions?.length,'Saved creator revisions are unavailable: '+(facts.detail??'unsupported extraction'));
  const revisions=facts.revisions!,first=revisions[0]!,last=revisions.at(-1)!;
  requireSecurity(revisions.length<=128&&first.signatures.length===1&&last.end===input.blob.size,'Incomplete creator archive history.');
  unsupported(verified.length>=3&&verified[0]?.facts.type==='Sig'&&verified.slice(1).every(s=>s.facts.type==='DocTimeStamp'),'Creator archive requires an export signature and archival document timestamps.');
  const finalSignatures=last.signatures;
  requireSecurity(finalSignatures.length===verified.length&&finalSignatures.every((s,i)=>sameSignature(s,verified[i]!.facts)),'Current and historical signature inventories disagree.');
  const signedRevisions=new Map<number,CreatorArchiveRevision>();
  let prior:CreatorArchiveRevision|undefined,retained=new Set<string>();
  for(const revision of revisions){
    check();
    requireSecurity(revision.protectedSha256===first.protectedSha256,'Protected pages, attachments, metadata, document information or ordinary annotations changed after export.');
    requireSecurity(revision.signatures.length>0&&revision.signatures.length<=verified.length&&revision.signatureRefs.length===revision.signatures.length&&revision.fieldNames.length===revision.signatures.length,'Invalid historical signature inventory.');
    requireSecurity(revision.signatures.every((s,i)=>sameSignature(s,verified[i]!.facts)),'An earlier signature was removed, changed or replaced.');
    requireSecurity(sameBytes(revision.policy,input.policy!),'The creator security policy changed after export.');
    const newest=verified[revision.signatures.length-1]!;
    requireSecurity(newest.end<=revision.end,'A saved revision refers to a signature from a later revision.');
    if(newest.end===revision.end){requireSecurity(!signedRevisions.has(newest.end),'Duplicate signed revision.');signedRevisions.set(newest.end,revision);}
    if(prior){
      requireSecurity(revision.end>prior.end&&revision.previousXRef===prior.startXRef&&revision.startXRef>prior.startXRef,'Creator saved revisions are not a continuous chain.');
      requireSecurity(revision.signatures.length===prior.signatures.length||revision.signatures.length===prior.signatures.length+1,'Unexpected archival signature additions.');
      requireSecurity(prior.fieldNames.every((name,i)=>name===revision.fieldNames[i]),'An earlier signature field label changed.');
      requireSecurity(prior.signatureRefs.every((ref,i)=>ref===revision.signatureRefs[i]),'Earlier signature objects were replaced.');
      if(revision.signatures.length===prior.signatures.length)requireSecurity(sameBytes(revision.manifest,prior.manifest),'Credentials changed in an unsigned evidence revision.');
      else requireSecurity(newest.end===revision.end,'New timestamp does not seal its complete saved revision.');
    }else requireSecurity(newest.end===revision.end,'Export signature does not seal the first saved revision.');
    const ids=await evidenceIds(revision.evidence,check);
    requireSecurity([...retained].every(id=>ids.has(id)),'Previously stored validation evidence was removed or replaced.');retained=ids;prior=revision;
  }
  requireSecurity(verified.every(s=>signedRevisions.has(s.end)),'An authenticated signature revision is missing from the saved history.');
  report.checks.push({name:'Creator archival revision history',status:'PASS',detail:`Inspected ${revisions.length} saved revisions, including unsigned evidence updates. Pages, embedded files, metadata, document information and ordinary annotations retain their export values. Only bounded evidence updates, invisible timestamps and authenticated creator credentials are permitted.`});

  const exportSignature=verified[0]!,signingStamp=exportSignature.stamp;
  unsupported(!!signingStamp?.time,'The creator export signature has no supported signing timestamp.');
  await checkSignerBinding(exportSignature.cms);await checkSignerBinding(signingStamp!);
  let time=signingStamp!.time!;
  for(const signature of verified.slice(1)){
    await checkSignerBinding(signature.cms);
    requireSecurity(!!signature.cms.time&&signature.cms.time>=time,'Archival timestamps run backwards in time.');time=signature.cms.time!;
  }
  report.checks.push({name:'Creator archival signer binding',status:'PASS',detail:'Export and timestamp signatures bind the exact signing certificates with signed SHA-256 ESSCertIDv2 attributes. Recorded signing and renewal times are ordered.'});

  const trusts=new Map<number,OfflineTrust>();
  const trustFor=(revision:CreatorArchiveRevision)=>{let trust=trusts.get(revision.end);if(!trust){trust=new OfflineTrust(revision.evidence,input.trustRoots??[],check);trusts.set(revision.end,trust);}return trust;};
  const assess=async(cms:Pick<VerifiedPdfSignature['cms'],'signer'|'certificates'>,at:Date,revision:CreatorArchiveRevision,label:string,timestamp=false)=>{
    const result=await trustFor(revision).assess(cms.signer,cms.certificates,at,timestamp);check();
    for(const [kind,value]of Object.entries(result)){
      requireSecurity(value.status!=='FAIL',label+' '+kind+': '+value.detail);
      unsupported(value.status==='PASS',label+' '+kind+': '+value.detail);
    }
  };
  const firstArchive=signedRevisions.get(verified[1]!.end)!;
  await assess(signingStamp!,signingStamp!.time!,firstArchive,'Export timestamp',true);
  await assess(exportSignature.cms,signingStamp!.time!,firstArchive,'Export signer');
  for(const [index,signature]of verified.slice(1).entries()){
    progress(`Checking archival timestamp ${index+1} and its stored evidence…`);
    await assess(signature.cms,signature.cms.time!,signedRevisions.get(signature.end)!,`Archival timestamp ${index+1}`,true);
  }
  report.checks.push({name:'Creator archival timestamps and evidence',status:'PASS',detail:`The export signer, signing timestamp and all ${verified.length-1} document timestamps connect to selected CAs with fresh signed revocation evidence at their recorded times. Each archival timestamp seals the evidence used for it; earlier evidence remains stored.`});

  let manifest:Uint8Array|undefined,credentialsSeen=false;
  const reservation=new TextEncoder().encode('THINKBOX_PDF_C2PA_RESERVED_STREAM!');
  for(const revision of revisions){
    if(manifest&&sameBytes(manifest,revision.manifest))continue;
    if(!credentialsSeen&&revision.manifest.length===32768&&reservation.every((byte,index)=>revision.manifest[index]===byte)&&revision.manifest.subarray(reservation.length).every(byte=>byte===0)){manifest=revision.manifest;continue;}
    const index=revision.signatures.length-1,signature=verified[index]!;
    requireSecurity(signature.end===revision.end&&(index===0||revision.fieldNames[index]==='C2PA archival document timestamp'),'Creator credential replacement is not sealed by its designated timestamp.');
    const credentials=await verifyC2pa(input.blob.slice(0,revision.end),revision.manifest,revision.policy,signature.gap,freshReport(report),check,progress);
    requireSecurity(credentials.creatorProfile,'An archived credential does not authenticate the supported creator profile.');
    if(!credentialsSeen)requireSecurity(sameBytes(new Uint8Array(credentials.signer.toSchema().toBER(false)),new Uint8Array(exportSignature.cms.signer.toSchema().toBER(false))),'The original creator credential and export signature have different signers.');
    await assess(credentials,index===0?signingStamp!.time!:signature.cms.time!,index===0?firstArchive:revision,'Archived C2PA signer');
    manifest=revision.manifest;credentialsSeen=true;
  }
  requireSecurity(last.fieldNames.at(-1)==='C2PA archival document timestamp'&&sameBytes(last.manifest,input.credentials!),'Final archival timestamp does not seal the current creator credentials.');
  report.checks.push({name:'Creator archival credentials',status:'PASS',detail:'Original and renewed creator credentials verify against their own saved PDF bytes, with certificate trust at their protected signing times. The final timestamp seals the current credentials and the complete archive.'});
  return time;
}
