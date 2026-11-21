import test from 'node:test';
import assert from 'node:assert/strict';
import {fileURLToPath,pathToFileURL} from 'node:url';
import {join} from 'node:path';
import {Sequence,OctetString} from 'asn1js';
import {createHash} from 'node:crypto';
const modules=process.env.EAPDF_SECURITY_MODULES??fileURLToPath(new URL('../dist/chromium/',import.meta.url));
const {validateCreatorArchive,checkSignerBinding}=await import(pathToFileURL(join(modules,'security-archival.js')));
const hash=bytes=>createHash('sha256').update(bytes).digest();
const bytes=Uint8Array.of(1,2,3),policy=Uint8Array.of(4,5),empty=()=>({certificates:[],crls:[],ocsps:[]});
function fixture(){
 const verified=[10,20,30].map((end,index)=>({end,gap:[end-2,end-1],facts:{type:index?'DocTimeStamp':'Sig',subFilter:index?'ETSI.RFC3161':'ETSI.CAdES.detached',byteRange:[0,end-2,end-1,1],contents:bytes},cms:{},stamp:index?undefined:{time:new Date()}}));
 const revision=(index,end,previousXRef)=>({end,startXRef:end-1,previousXRef,protectedSha256:'a'.repeat(64),signatures:verified.slice(0,index+1).map(s=>s.facts),signatureRefs:verified.slice(0,index+1).map((_,i)=>String(i+1)),fieldNames:verified.slice(0,index+1).map((_,i)=>i?'':'Archive export signature'),manifest:bytes,policy,evidence:empty()});
 return {input:{blob:new Blob([new Uint8Array(30)]),credentials:bytes,policy},facts:{schemaVersion:1,status:'COMPLETE',revisions:[revision(0,10,0),revision(0,15,9),revision(1,20,14),revision(2,30,19)]},verified,report:{checks:[]}};
}
const run=value=>validateCreatorArchive(value.input,value.facts,value.verified,value.report,()=>{},()=>{});
test('changed protected objects, history gaps, lost signatures and modified policy cannot pass',async()=>{
 for(const mutate of [
  f=>f.facts.revisions[1].protectedSha256='b'.repeat(64),
  f=>f.facts.revisions[2].previousXRef=9,
  f=>f.facts.revisions.splice(2,1),
  f=>f.facts.revisions[2].signatures[0]={...f.verified[0].facts,contents:Uint8Array.of(9)},
  f=>f.facts.revisions[2].signatureRefs[0]='changed',
  f=>f.facts.revisions[2].policy=Uint8Array.of(0),
  f=>f.facts.revisions[1].manifest=Uint8Array.of(9),
 ]){const f=fixture();mutate(f);await assert.rejects(run(f));assert.ok(!f.report.checks.some(c=>c.name==='Creator archival revision history'&&c.status==='PASS'));}
});
test('lost or unreadable stored revocation evidence cannot establish archival protection',async()=>{
 const removed=fixture();removed.facts.revisions[1].evidence.crls=[bytes];await assert.rejects(run(removed),/removed or replaced/);
 const unreadable=fixture();unreadable.facts.revisions[1].evidence.error='limit';await assert.rejects(run(unreadable),/could not be read/);
});
test('unavailable extraction is unknown, and invalid extraction fails',async()=>{
 for(const [status,type]of [['UNAVAILABLE','UnsupportedSecurity'],['INVALID','Error']]){
  const f=fixture();f.facts={schemaVersion:1,status,detail:'test failure'};await assert.rejects(run(f),error=>error.constructor.name===type);
 }
});
test('signed ESS SHA-256 binding must identify the exact signing certificate',async()=>{
 const cms=digest=>({signed:{signerInfos:[{signedAttrs:{attributes:[{type:'1.2.840.113549.1.9.16.2.47',values:[new Sequence({value:[new Sequence({value:[new Sequence({value:[new OctetString({valueHex:digest.buffer.slice(digest.byteOffset,digest.byteOffset+digest.byteLength)})]})]})]})]}]}}]},signer:{toSchema:()=>({toBER:()=>bytes.buffer})}});
 await checkSignerBinding(cms(hash(bytes)));await assert.rejects(checkSignerBinding(cms(hash(Uint8Array.of(9)))),/does not identify/);
 const missing=cms(hash(bytes));missing.signed.signerInfos[0].signedAttrs.attributes=[];await assert.rejects(checkSignerBinding(missing),/Missing/);
});
