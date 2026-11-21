import {sha256Blob} from './blob-hash.js';
import {sha256} from './domain/hashes.js';
import {decodeCbor,cborMap,cborBytes,encodeCbor} from './security-cbor.js';
import {certificate,recordCertificate,requireSecurity,UnsupportedSecurity,hex} from './security-cms.js';
import type {SecurityReport} from './security-types.js';
type Box={start:number;end:number;type:string;label:string;children:Box[];content:Uint8Array};
export function parseJumbf(bytes:Uint8Array):Box {
  const text=(start:number,end:number)=>new TextDecoder('utf-8',{fatal:true}).decode(bytes.subarray(start,end));
  const length=(offset:number)=>new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength).getUint32(offset);
  let nodes=0;
  function read(start:number,bound:number,depth:number):Box {
    requireSecurity(depth<=8&&++nodes<=64&&start+8<=bound,'Invalid JUMBF structure');
    const size=length(start),end=start+size,type=text(start+4,start+8);
    requireSecurity(size>=8&&end<=bound,'Invalid JUMBF length');
    if(type!=='jumb')return {start,end,type,label:'',children:[],content:bytes.slice(start+8,end)};
    let at=start+8;requireSecurity(at+26<=end,'Missing JUMBF description');
    const sizeDescription=length(at),descriptionEnd=at+sizeDescription;
    requireSecurity(sizeDescription>=26&&descriptionEnd<=end&&text(at+4,at+8)==='jumd'&&bytes[at+24]===3&&hex(bytes.subarray(at+12,at+24))==='00110010800000aa00389b71'&&bytes[descriptionEnd-1]===0,'Unsupported JUMBF description');
    const boxType=text(at+8,at+12),label=text(at+25,descriptionEnd-1);requireSecurity(!label.includes('\0'),'Invalid JUMBF label');
    at=descriptionEnd;const children:Box[]=[];while(at<end){const child=read(at,end,depth+1);children.push(child);at=child.end;}
    return {start,end,type:boxType,label,children,content:children.length===1&&children[0]!.type==='cbor'?children[0]!.content:new Uint8Array()};
  }
  const root=read(0,bytes.length,0);requireSecurity(root.end===bytes.length,'Trailing JUMBF data');return root;
}
async function locate(blob:Blob,pattern:Uint8Array,check:()=>void):Promise<number>{
  const prefix=new Uint32Array(pattern.length);for(let i=1,j=0;i<pattern.length;i++){while(j&&pattern[i]!==pattern[j])j=prefix[j-1]!;if(pattern[i]===pattern[j])j++;prefix[i]=j;}
  let found=-1,matched=0;
  for(let offset=0;offset<blob.size;offset+=1024*1024){check();const bytes=new Uint8Array(await blob.slice(offset,offset+1024*1024).arrayBuffer());
    for(let i=0;i<bytes.length;i++){while(matched&&bytes[i]!==pattern[matched])matched=prefix[matched-1]!;if(bytes[i]===pattern[matched])matched++;if(matched===pattern.length){requireSecurity(found<0,'Ambiguous C2PA stream location');found=offset+i-pattern.length+1;matched=prefix[matched-1]!;}}
  }
  requireSecurity(found>=0,'C2PA stream not stored as an unfiltered payload');return found;
}
export async function verifyC2pa(blob:Blob,encoded:Uint8Array,policy:Uint8Array|undefined,gap:[number,number]|undefined,report:SecurityReport,check:()=>void,progress:(value:string)=>void){
  if(encoded.length!==32768)throw new UnsupportedSecurity('Unsupported C2PA manifest capacity/profile');
  requireSecurity(new TextDecoder().decode(policy)==='{"schemaVersion":1,"scope":"EMAIL_EXPORT","specification":"2.2","profile":"eapdf-1"}\n','Missing or incompatible EA-PDF C2PA policy');
  const root=parseJumbf(encoded);requireSecurity(root.label==='c2pa'&&root.type==='c2pa'&&root.children.length===1,'Unsupported C2PA manifest store');
  const active=root.children[0]!;if(active.type!=='c2ma')throw new UnsupportedSecurity('Unsupported C2PA manifest type');
  const child=(box:Box,label:string)=>{const matches=box.children.filter(b=>b.label===label);requireSecurity(matches.length===1,'Missing/ambiguous C2PA '+label);return matches[0]!;};
  const claimBox=child(active,'c2pa.claim.v2'),assertions=child(active,'c2pa.assertions'),signatureBox=child(active,'c2pa.signature');
  requireSecurity(claimBox.type==='c2cl'&&assertions.type==='c2as'&&signatureBox.type==='c2cs','Invalid C2PA box type');
  const claim=cborMap(decodeCbor(claimBox.content));requireSecurity(claim.get('signature')===`self#jumbf=/c2pa/${active.label}/c2pa.signature`&&claim.get('alg')==='sha256','C2PA claim/signature reference mismatch');
  const refs=claim.get('created_assertions');requireSecurity(Array.isArray(refs)&&refs.length===3&&assertions.children.length===3,'Unsupported C2PA assertions');
  const expected=new Set(['c2pa.hash.data','c2pa.actions.v2','digital.thinkbox.eapdf']);
  for(const ref of refs){const value=cborMap(ref),url=value.get('url');requireSecurity(typeof url==='string'&&url.startsWith('self#jumbf=c2pa.assertions/'),'Invalid C2PA assertion URL');const label=url.slice('self#jumbf=c2pa.assertions/'.length);requireSecurity(expected.delete(label),'Unsupported/duplicate C2PA assertion');const box=child(assertions,label);requireSecurity(await sha256(encoded.subarray(box.start+8,box.end))===hex(cborBytes(value.get('hash'))),'C2PA assertion digest mismatch');}
  const assertion=cborMap(decodeCbor(child(assertions,'digital.thinkbox.eapdf').content));
  requireSecurity(assertion.get('schemaVersion')===1&&assertion.get('scope')==='EMAIL_EXPORT'&&assertion.get('sourceAuthentication')==='NOT_ASSERTED','Unsupported EA-PDF provenance assertion');
  const cose=decodeCbor(signatureBox.content);requireSecurity(Array.isArray(cose)&&cose.length===4&&cose[2]===null,'Expected detached COSE Sign1');
  const protectedBytes=cborBytes(cose[0]),protectedHeaders=cborMap(decodeCbor(protectedBytes)),unprotected=cborMap(cose[1]),algorithm=protectedHeaders.get(1);
  if((algorithm!==-7&&algorithm!==-37)||protectedHeaders.has(2)||unprotected.has(1))throw new UnsupportedSecurity('Unsupported COSE algorithm/critical headers');
  requireSecurity(!(protectedHeaders.has(33)&&unprotected.has(33)),'Ambiguous COSE certificate chain');
  const chain=protectedHeaders.get(33)??unprotected.get(33);requireSecurity(Array.isArray(chain)&&chain.length>=1&&chain.length<=16,'Missing COSE signing certificate');
  const cert=certificate(cborBytes(chain[0])),spki=cert.subjectPublicKeyInfo.toSchema().toBER(false);
  const key=await crypto.subtle.importKey('spki',spki,algorithm===-7?{name:'ECDSA',namedCurve:'P-256'}:{name:'RSA-PSS',hash:'SHA-256'},false,['verify']);
  if(algorithm===-37)requireSecurity((key.algorithm as RsaKeyAlgorithm).modulusLength>=2048,'Weak C2PA RSA key');
  const signature=cborBytes(cose[3]),data=encodeCbor(['Signature1',protectedBytes,new Uint8Array(),claimBox.content]);
  requireSecurity(await crypto.subtle.verify(algorithm===-7?{name:'ECDSA',hash:'SHA-256'}:{name:'RSA-PSS',saltLength:32},key,signature.slice().buffer,data.slice().buffer),'C2PA COSE claim signature mismatch');
  await recordCertificate(cert,report);check();
  report.checks.push({name:'C2PA claim signature',status:'PASS',detail:'COSE signature and all three assertion hashes verified against the included certificate.'});
  progress('Checking C2PA file binding…');
  const offset=await locate(blob,encoded,check),ranges:[number,number][]=[[offset,encoded.length]];if(gap)ranges.push([gap[0],gap[1]-gap[0]]);ranges.sort((a,b)=>a[0]-b[0]);
  const binding=cborMap(decodeCbor(child(assertions,'c2pa.hash.data').content));requireSecurity(binding.get('alg')==='sha256','Unsupported C2PA binding hash');
  const exclusions=binding.get('exclusions');requireSecurity(Array.isArray(exclusions)&&exclusions.length===ranges.length,'Unsafe C2PA exclusions');
  const parts:Blob[]=[];let position=0;
  for(const [i,[start,size]]of ranges.entries()){
    const declared=cborMap(exclusions[i]);requireSecurity(declared.get('start')===start&&declared.get('length')===size&&start>=position&&start+size<=blob.size,'Unsafe C2PA exclusion range');
    parts.push(blob.slice(position,start));position=start+size;
  }
  parts.push(blob.slice(position));requireSecurity(await sha256Blob(new Blob(parts),check)===hex(cborBytes(binding.get('hash'))),'C2PA current PDF content hash mismatch');
  report.checks.push({name:'C2PA file binding',status:'PASS',detail:'Current PDF bytes, including embedded sources, match the signed hash. Only this manifest and the final signature value are excluded. Application inventory assertions are signed but are not separately reconciled here.'});
  // This identity is inside the authenticated claim. A PDF Producer string alone
  // must never enable the creator-specific revision allowances.
  const generators=claim.get('claim_generator_info');
  const generator=generators instanceof Map?generators:undefined;
  const creatorProfile=generator?.get('name')==='EA-PDF'&&typeof generator.get('version')==='string'&&/^[0-9]+(?:\.[0-9]+)*(?:-[A-Za-z0-9.]+)?$/.test(generator.get('version') as string)
    &&['CONFIGURED_CERTIFICATE','LOCAL_EPHEMERAL_UNTRUSTED'].includes(String(assertion.get('identity')))
    &&assertion.get('metadataSha256') instanceof Uint8Array&&(assertion.get('metadataSha256') as Uint8Array).length===32
    &&assertion.get('inventorySha256') instanceof Uint8Array&&(assertion.get('inventorySha256') as Uint8Array).length===32;
  return {signer:cert,certificates:chain.map(value=>certificate(cborBytes(value))),creatorProfile};
}
