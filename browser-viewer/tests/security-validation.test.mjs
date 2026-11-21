import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,writeFile,readFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {pathToFileURL,fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {createHash,sign,constants} from 'node:crypto';
const modules=process.env.EAPDF_SECURITY_MODULES??fileURLToPath(new URL('../dist/chromium/',import.meta.url));
const {validateSecurity,signatureRange}=await import(pathToFileURL(join(modules,'security-validation.js')));
const {decodeCbor}=await import(pathToFileURL(join(modules,'security-cbor.js')));
const {parseJumbf}=await import(pathToFileURL(join(modules,'security-c2pa.js')));
const temporary=await mkdtemp(join(tmpdir(),'eapdf-security-test-'));
const run=(...args)=>execFileSync('openssl',args,{cwd:temporary,stdio:'pipe'});
run('req','-x509','-newkey','rsa:2048','-nodes','-subj','/CN=Interoperability fixture','-days','2','-keyout','key.pem','-out','certificate.pem');
const privateKey=await readFile(join(temporary,'key.pem'));
run('x509','-in','certificate.pem','-outform','DER','-out','certificate.der');
const cert=await readFile(join(temporary,'certificate.der'));
run('req','-x509','-newkey','ec','-pkeyopt','ec_paramgen_curve:P-256','-nodes','-subj','/CN=EC interoperability fixture','-days','2','-keyout','ec-key.pem','-out','ec-certificate.pem');
run('x509','-in','ec-certificate.pem','-outform','DER','-out','ec-certificate.der');
const ecKey=await readFile(join(temporary,'ec-key.pem')),ecCert=await readFile(join(temporary,'ec-certificate.der'));
const hash=bytes=>createHash('sha256').update(bytes).digest();
const facts=signatures=>({signatures,encrypted:false});
function cbor(value){
 const header=(major,n)=>n<24?Buffer.from([major*32+n]):n<256?Buffer.from([major*32+24,n]):n<65536?Buffer.from([major*32+25,n>>>8,n&255]):Buffer.from([major*32+26,n>>>24,(n>>>16)&255,(n>>>8)&255,n&255]);
 if(value===null)return Buffer.from([246]);if(typeof value==='number')return header(value<0?1:0,value<0?-1-value:value);
 if(typeof value==='string'){const b=Buffer.from(value);return Buffer.concat([header(3,b.length),b]);}
 if(value instanceof Uint8Array)return Buffer.concat([header(2,value.length),value]);
 if(Array.isArray(value))return Buffer.concat([header(4,value.length),...value.map(cbor)]);
 const entries=value instanceof Map?[...value]:Object.entries(value);return Buffer.concat([header(5,entries.length),...entries.flatMap(([k,v])=>[cbor(k),cbor(v)])]);
}
function box(type,bytes){const h=Buffer.alloc(8);h.writeUInt32BE(bytes.length+8);h.write(type,4);return Buffer.concat([h,bytes]);}
function superbox(type,label,...children){return box('jumb',Buffer.concat([box('jumd',Buffer.concat([Buffer.from(type),Buffer.from('00110010800000aa00389b71','hex'),Buffer.from([3]),Buffer.from(label),Buffer.from([0])])),...children]));}
function c2pa({unsafe=false,badSignature=false,ec=false}={}){
 const prefix=Buffer.from('%PDF-2.0 fixture start\n'),suffix=Buffer.from('\nfixture end %%EOF\n');
 const assertion=(label,value)=>superbox('cbor',label,box('cbor',cbor(value)));
 const assertions=[assertion('c2pa.hash.data',{alg:'sha256',hash:hash(Buffer.concat([prefix,suffix])),exclusions:[{start:unsafe?0:prefix.length,length:32768}]}),assertion('c2pa.actions.v2',{actions:[]}),assertion('digital.thinkbox.eapdf',{schemaVersion:1,scope:'EMAIL_EXPORT',sourceAuthentication:'NOT_ASSERTED'})];
 const labels=['c2pa.hash.data','c2pa.actions.v2','digital.thinkbox.eapdf'];
 const claim=cbor({signature:'self#jumbf=/c2pa/fixture/c2pa.signature',alg:'sha256',instanceID:'fixture',created_assertions:assertions.map((a,i)=>({url:'self#jumbf=c2pa.assertions/'+labels[i],hash:hash(a.subarray(8))}))});
 const protectedHeaders=cbor(new Map([[1,ec?-7:-37]])),signature=sign('sha256',cbor(['Signature1',protectedHeaders,Buffer.alloc(0),claim]),ec?{key:ecKey,dsaEncoding:'ieee-p1363'}:{key:privateKey,padding:constants.RSA_PKCS1_PSS_PADDING,saltLength:32});if(badSignature)signature[0]^=1;
 let padding=0,encoded;
 for(let n=0;n<5;n++){encoded=superbox('c2pa','c2pa',superbox('c2ma','fixture',superbox('c2as','c2pa.assertions',...assertions),superbox('c2cl','c2pa.claim.v2',box('cbor',claim)),superbox('c2cs','c2pa.signature',box('cbor',cbor([protectedHeaders,new Map([[33,[ec?ecCert:cert]],['pad',Buffer.alloc(padding)]]),null,signature])))));if(encoded.length===32768)break;padding+=32768-encoded.length;}
 assert.equal(encoded.length,32768);
 return {blob:new Blob([prefix,encoded,suffix]),facts:facts([]),credentials:new Uint8Array(encoded),policy:new TextEncoder().encode('{"schemaVersion":1,"scope":"EMAIL_EXPORT","specification":"2.2","profile":"eapdf-1"}\n')};
}
test.after(()=>rm(temporary,{recursive:true,force:true}));
test('foreign files skip creator archival inspection even when their basic credentials verify',async()=>{
 let calls=0;
 const readArchive=async()=>{calls++;throw Error('Must not inspect foreign history');};
 for(const input of [{blob:new Blob(['%PDF /Producer (EA-PDF)']),facts:facts([])},c2pa()]){
  const report=await validateSecurity(input,()=>{},()=>{},readArchive);
  assert.equal(report.checks.find(c=>c.name==='PAdES archival level and allowed revisions').status,'NOT_APPLICABLE');
 }
 assert.equal(calls,0);
});
test('unsigned and unsupported inputs cannot acquire a green integrity verdict',async()=>{
 let report=await validateSecurity({blob:new Blob(['unsigned']),facts:facts([])});assert.equal(report.status,'NOT_APPLICABLE');
 report=await validateSecurity({blob:new Blob(['unsigned']),facts:facts([]),discoveryError:'Unsupported profile'});assert.equal(report.status,'UNKNOWN');
});
test('detached PDF CMS verifies covered bytes while keeping trust unknown; tampering and appends fail',async()=>{
 const prefix=Buffer.from('%PDF-2.0 fixture /Contents '),suffix=Buffer.from('\n%%EOF\n'),gap=32770;
 const range=[0,prefix.length,prefix.length+gap,suffix.length];
 await writeFile(join(temporary,'content.bin'),Buffer.concat([prefix,suffix]));run('cms','-sign','-binary','-md','sha256','-in','content.bin','-signer','certificate.pem','-inkey','key.pem','-outform','DER','-out','signature.der');
 const cms=await readFile(join(temporary,'signature.der')),contents=new Uint8Array((gap-2)/2);contents.set(cms);
 const blob=new Blob([prefix,'<'+Buffer.from(contents).toString('hex')+'>',suffix]),input={blob,facts:facts([{contents,byteRange:range,type:'Sig',subFilter:'adbe.pkcs7.detached'}])};
 const report=await validateSecurity(input);assert.equal(report.status,'PASS',JSON.stringify(report));assert.equal(report.checks.find(c=>c.name.includes('certificate trust')).status,'UNKNOWN');assert.equal(report.certificates.length,1);assert.equal(report.checks.find(c=>c.name==='PDF signature 1').category,'CMS');assert.equal(report.checks.find(c=>c.name==='Selected trust policy').category,'Other checks');
 const changed=new Blob(['X',blob.slice(1)]);assert.equal((await validateSecurity({...input,blob:changed})).status,'FAIL');
 assert.equal((await validateSecurity({...input,blob:new Blob([blob,'appended'])})).status,'FAIL');
 const invented={...input.facts.signatures[0],byteRange:[0,1,3,1]};await assert.rejects(signatureRange(blob,invented));
});
test('C2PA COSE and current PDF binding verify; recomputed unsafe exclusions and changed signatures fail',async()=>{
 const input=c2pa(),report=await validateSecurity(input);assert.equal(report.status,'PASS',JSON.stringify(report));
 assert.equal(report.checks.filter(c=>c.name.startsWith('C2PA')&&c.status==='PASS').length,2);assert.equal(report.scope,'Export security');assert.ok(report.checks.filter(c=>c.name.startsWith('C2PA')).every(c=>c.category==='C2PA'));
 assert.equal((await validateSecurity({...input,blob:new Blob(['X',input.blob.slice(1)])})).status,'FAIL');
 assert.equal((await validateSecurity(c2pa({unsafe:true}))).status,'FAIL');assert.equal((await validateSecurity(c2pa({badSignature:true}))).status,'FAIL');
});
test('ES256 C2PA verifies with P-256 certificate and raw COSE ECDSA encoding',async()=>{
 const report=await validateSecurity(c2pa({ec:true}));assert.equal(report.status,'PASS',JSON.stringify(report));
});
test('parsers reject duplicate CBOR keys, truncation, deep structures and unsafe JUMBF lengths',()=>{
 assert.throws(()=>decodeCbor(Buffer.from('a201010102','hex')),/duplicate/);assert.throws(()=>decodeCbor(Buffer.from('5810','hex')),/Truncated/);
 assert.throws(()=>decodeCbor(Buffer.from('81'.repeat(20)+'00','hex')),/limit/);assert.throws(()=>parseJumbf(Buffer.alloc(32)),/length/);
});
test('cancellation propagates instead of producing a completed verification result',async()=>{
 await assert.rejects(validateSecurity(c2pa(),()=>{throw Error('cancelled');}),/cancelled/);
});
