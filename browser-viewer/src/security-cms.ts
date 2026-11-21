import {fromBER,OctetString,ObjectIdentifier} from 'asn1js';
import {Certificate,ContentInfo,CryptoEngine,IssuerAndSerialNumber,SignedData,TSTInfo} from 'pkijs';
import {sha256} from './domain/hashes.js';
import type {SecurityReport} from './security-types.js';
export const SHA256='2.16.840.1.101.3.4.2.1',DATA='1.2.840.113549.1.7.1',TST='1.2.840.113549.1.9.16.1.4';
export class UnsupportedSecurity extends Error {}
export const hex=(bytes:Uint8Array)=>Array.from(bytes,b=>b.toString(16).padStart(2,'0')).join('');
export function requireSecurity(value:unknown,detail:string):asserts value{if(!value)throw Error(detail);}
export function der(bytes:Uint8Array,padded=false){
  // Bound nesting before ASN1js allocates its recursive object graph. DER only.
  let nodes=0;
  function walk(start:number,bound:number,depth:number):number{
    requireSecurity(depth<=32&&++nodes<=10000&&start+2<=bound,'ASN.1 structure limit');
    let offset=start,tag=bytes[offset++]!;requireSecurity((tag&31)!==31,'Unsupported ASN.1 tag');
    let length=bytes[offset++]!;
    if(length&128){const count=length&127;requireSecurity(count>0&&count<=4&&offset+count<=bound,'Invalid DER length');length=0;for(let n=0;n<count;n++)length=length*256+bytes[offset++]!;}
    const end=offset+length;requireSecurity(end<=bound,'Truncated DER');
    if(tag&32)while(offset<end)offset=walk(offset,end,depth+1);
    return end;
  }
  requireSecurity(bytes.length>0&&bytes.length<=1024*1024,'ASN.1 byte limit');
  const end=walk(0,bytes.length,0);requireSecurity(padded?bytes.subarray(end).every(v=>v===0):end===bytes.length,'Trailing ASN.1 data');
  const parsed=fromBER(bytes.slice(0,end));requireSecurity(parsed.offset===end,'Invalid ASN.1');return parsed.result;
}
export function certificate(bytes:Uint8Array){return new Certificate({schema:der(bytes)});}
export async function recordCertificate(cert:Certificate,report:SecurityReport){
  const fingerprint=await sha256(new Uint8Array(cert.toSchema().toBER(false)));
  if(!report.certificates.some(c=>c.sha256===fingerprint))report.certificates.push({sha256:fingerprint,subject:cert.subject.typesAndValues.map(v=>v.value.valueBlock.value).join(', '),notBefore:cert.notBefore.value.toISOString(),notAfter:cert.notAfter.value.toISOString()});
}
/** Content digest is supplied by the streaming file hash; only signed attributes reach WebCrypto. */
export async function verifyCms(bytes:Uint8Array,digest:string,report:SecurityReport,timestamp=false):Promise<{signed:SignedData;signature:Uint8Array;signer:Certificate;certificates:Certificate[];time?:Date;timestampInfo?:TSTInfo}> {
  const info=new ContentInfo({schema:der(bytes,true)});requireSecurity(info.contentType===ContentInfo.SIGNED_DATA,'Expected CMS SignedData');
  const signed=new SignedData({schema:info.content});
  requireSecurity(signed.signerInfos.length===1,'Ambiguous CMS signers');
  const signer=signed.signerInfos[0]!;
  if(signer.digestAlgorithm.algorithmId!==SHA256||!['1.2.840.113549.1.1.1','1.2.840.113549.1.1.11','1.2.840.113549.1.1.10','1.2.840.10045.4.3.2'].includes(signer.signatureAlgorithm.algorithmId)||!(signer.sid instanceof IssuerAndSerialNumber))throw new UnsupportedSecurity('Unsupported CMS algorithm or signer identifier');
  const matches=(signed.certificates??[]).filter((c):c is Certificate=>c instanceof Certificate&&c.issuer.isEqual((signer.sid as IssuerAndSerialNumber).issuer)&&c.serialNumber.isEqual((signer.sid as IssuerAndSerialNumber).serialNumber));
  requireSecurity(matches.length===1&&!!signer.signedAttrs,'Missing/ambiguous CMS signer or signed attributes');
  const attribute=(type:string)=>{const values=signer.signedAttrs!.attributes.filter(a=>a.type===type);requireSecurity(values.length===1&&values[0]!.values.length===1,'Missing/ambiguous CMS signed attribute');return values[0]!.values[0];};
  const contentType=attribute('1.2.840.113549.1.9.3'),messageDigest=attribute('1.2.840.113549.1.9.4');
  requireSecurity(contentType instanceof ObjectIdentifier&&contentType.valueBlock.toString()===(timestamp?TST:DATA),'CMS content type mismatch');
  let expected=digest;
  let time:Date|undefined,timestampInfo:TSTInfo|undefined;
  if(timestamp){
    requireSecurity(signed.encapContentInfo.eContentType===TST&&signed.encapContentInfo.eContent instanceof OctetString,'Missing timestamp content');
    const content=new Uint8Array(signed.encapContentInfo.eContent.getValue());
    const tst=new TSTInfo({schema:der(content)});
    timestampInfo=tst;time=tst.genTime;
    if(tst.messageImprint.hashAlgorithm.algorithmId!==SHA256)throw new UnsupportedSecurity('Unsupported timestamp hash algorithm');
    requireSecurity(hex(tst.messageImprint.hashedMessage.valueBlock.valueHexView)===digest,'Timestamp imprint differs from covered bytes');
    expected=await sha256(content);
  }else requireSecurity(!signed.encapContentInfo.eContent&&signed.encapContentInfo.eContentType===DATA,'PDF signature must be detached');
  requireSecurity(messageDigest instanceof OctetString&&hex(messageDigest.valueBlock.valueHexView)===expected,'CMS content digest mismatch');
  const engine=new CryptoEngine({name:'eapdf-offline',crypto:globalThis.crypto,subtle:globalThis.crypto.subtle});
  requireSecurity(await engine.verifyWithPublicKey(signer.signedAttrs.encodedValue,signer.signature,matches[0]!.subjectPublicKeyInfo,signer.signatureAlgorithm,'SHA-256'),'CMS cryptographic signature mismatch');
  await recordCertificate(matches[0]!,report);
  return {signed,signature:new Uint8Array(signer.signature.getValue()),signer:matches[0]!,certificates:(signed.certificates??[]).filter((c):c is Certificate=>c instanceof Certificate),time,timestampInfo};
}
