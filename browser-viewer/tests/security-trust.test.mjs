import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,readFile,writeFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {pathToFileURL,fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import {CertificateRevocationList,RevokedCertificate,Time,BasicOCSPResponse,ResponseData,SingleResponse,CertID,CryptoEngine,setEngine,Extension} from 'pkijs';
import {Primitive,Constructed,GeneralizedTime,Null} from 'asn1js';
const modules=process.env.EAPDF_SECURITY_MODULES??fileURLToPath(new URL('../dist/chromium/',import.meta.url));
const {OfflineTrust}=await import(pathToFileURL(join(modules,'security-trust.js')));
const {certificate}=await import(pathToFileURL(join(modules,'security-cms.js')));
setEngine('eapdf-test',new CryptoEngine({name:'eapdf-test',crypto:globalThis.crypto,subtle:crypto.subtle}));
const directory=await mkdtemp(join(tmpdir(),'eapdf-offline-trust-'));
test.after(()=>rm(directory,{recursive:true,force:true}));
const run=(...args)=>execFileSync('openssl',args,{cwd:directory,stdio:'pipe'});
run('req','-x509','-newkey','rsa:2048','-nodes','-subj','/CN=Offline trust fixture CA','-days','2','-addext','basicConstraints=critical,CA:TRUE','-addext','keyUsage=critical,keyCertSign,cRLSign','-keyout','ca.key','-out','ca.pem');
const decode=bytes=>new Uint8Array(Buffer.from(bytes.toString().replace(/-----[^\n]+-----/g,'').replace(/\s/g,''),'base64'));
const root=certificate(decode(await readFile(join(directory,'ca.pem'))));
const key=await crypto.subtle.importKey('pkcs8',decode(await readFile(join(directory,'ca.key'))),{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);
async function issue(name,tsa=false){
 run('req','-newkey','rsa:2048','-nodes','-subj','/CN='+name,'-keyout',name+'.key','-out',name+'.csr');
 await writeFile(join(directory,name+'.ext'),'basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature\n'+(tsa?'extendedKeyUsage=critical,timeStamping\n':''));
 run('x509','-req','-in',name+'.csr','-CA','ca.pem','-CAkey','ca.key','-set_serial',tsa?'3':'2','-days','1','-extfile',name+'.ext','-out',name+'.pem');
 return certificate(decode(await readFile(join(directory,name+'.pem'))));
}
const leaf=await issue('document'),tsa=await issue('timestamp',true),now=new Date(),past=new Date(now.getTime()-10000),future=new Date(now.getTime()+3600000);
const encode=value=>new Uint8Array(value.toSchema().toBER(false));
test('reports identify selected CA certificates without promoting embedded certificates',async()=>{
 const {validateSecurity}=await import(pathToFileURL(join(modules,'security-validation.js')));
 const facts={signatures:[],encrypted:false,evidence:{certificates:[encode(root),encode(leaf)],crls:[],ocsps:[]}};
 const empty=await validateSecurity({blob:new Blob(['unsigned']),facts});
 assert.deepEqual(empty.selectedCertificates,[]);
 const report=await validateSecurity({blob:new Blob(['unsigned']),facts,trustRoots:[encode(root)]});
 assert.equal(report.selectedCertificates.length,1);
 const selected=JSON.parse(JSON.stringify(report)).selectedCertificates[0];
 assert.equal(selected.subject,'Offline trust fixture CA');assert.equal(selected.issuer,selected.subject);
 assert.match(selected.sha256,/^[a-f0-9]{64}$/);
 assert.equal(selected.notBefore,root.notBefore.value.toISOString());assert.equal(selected.notAfter,root.notAfter.value.toISOString());
});
async function crl({revoked=false,stale=false,forged=false}={}){
 const value=new CertificateRevocationList({version:1,issuer:root.subject,thisUpdate:new Time({type:0,value:past}),nextUpdate:new Time({type:0,value:stale?past:future}),revokedCertificates:revoked?[new RevokedCertificate({userCertificate:leaf.serialNumber,revocationDate:new Time({type:0,value:past})})]:[]});
 await value.sign(key,'SHA-256');if(forged)value.signatureValue.valueBlock.valueHexView[0]^=1;
 return encode(value);
}
async function ocsp({revoked=false,stale=false,forged=false,unauthorized=false,wrongCertificate=false}={}){
 const id=new CertID();await id.createForCertificate(wrongCertificate?tsa:leaf,{hashAlgorithm:'SHA-256',issuerCertificate:root});
 const status=revoked?new Constructed({idBlock:{tagClass:3,tagNumber:1},value:[new GeneralizedTime({valueDate:past})]}):new Primitive({idBlock:{tagClass:3,tagNumber:0}});
 const value=new BasicOCSPResponse({tbsResponseData:new ResponseData({responderID:unauthorized?leaf.subject:root.subject,producedAt:now,responses:[new SingleResponse({certID:id,certStatus:status,thisUpdate:past,nextUpdate:stale?past:future})]}),certs:[unauthorized?leaf:root]});
 const signer=unauthorized?await crypto.subtle.importKey('pkcs8',decode(await readFile(join(directory,'document.key'))),{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']):key;
 await value.sign(signer,'SHA-256');if(forged)value.signature.valueBlock.valueHexView[0]^=1;return encode(value);
}
const assess=(evidence,roots=[encode(root)],certificate=leaf,time=now,timestamp=false)=>new OfflineTrust({certificates:[],crls:[],ocsps:[],...evidence},roots,()=>{}).assess(certificate,[root],time,timestamp);
test('fresh signed CRL and an explicit CA establish path and revocation; missing trust/evidence stays unknown',async()=>{
 const evidence={crls:[await crl()]};let result=await assess(evidence);assert.equal(result.trust.status,'PASS',JSON.stringify(result));assert.equal(result.revocation.status,'PASS');
 assert.equal((await assess(evidence,[])).trust.status,'UNKNOWN');assert.equal((await assess({})).revocation.status,'UNKNOWN');
 assert.equal((await assess({...evidence,error:'Evidence limit'})).revocation.status,'UNKNOWN');
});
test('revoked dominates a good CRL; stale or forged CRLs cannot pass',async()=>{
 assert.equal((await assess({crls:[await crl(),await crl({revoked:true})]})).revocation.status,'FAIL');
 for(const options of [{stale:true},{forged:true}])assert.equal((await assess({crls:[await crl(options)]})).revocation.status,'UNKNOWN');
});
test('OCSP must be fresh, signed, authorized and for the exact certificate',async()=>{
 const good=await assess({ocsps:[await ocsp()]});assert.equal(good.trust.status,'PASS');assert.equal(good.revocation.status,'PASS',JSON.stringify(good));
 assert.equal((await assess({ocsps:[await ocsp({revoked:true})]})).revocation.status,'FAIL');
 for(const options of [{stale:true},{forged:true},{unauthorized:true},{wrongCertificate:true}])assert.equal((await assess({ocsps:[await ocsp(options)]})).revocation.status,'UNKNOWN',JSON.stringify(options));
});
test('TSA requires critical exclusive timestamp usage, path validity and fresh revocation',async()=>{
 const evidence={crls:[await crl()]};assert.equal((await assess(evidence,undefined,tsa,now,true)).trust.status,'PASS');
 assert.equal((await assess(evidence,undefined,leaf,now,true)).trust.status,'FAIL');
 assert.equal((await assess(evidence,undefined,tsa,new Date(now.getTime()+10*86400000),true)).trust.status,'UNKNOWN');
});
test('unsupported critical extensions, evidence limits and cancellation cannot acquire trust',async()=>{
 const modified=certificate(encode(leaf));modified.extensions.push(new Extension({extnID:'1.2.3.4.5',critical:true,extnValue:new Null().toBER(false)}));await modified.sign(key,'SHA-256');
 assert.equal((await assess({crls:[await crl()]},undefined,modified)).trust.status,'UNKNOWN');
 assert.equal((await assess({},Array.from({length:33},()=>encode(root)))).trust.status,'UNKNOWN');
 await assert.rejects(new OfflineTrust(undefined,[],()=>{throw Error('cancelled');}).assess(leaf,[root],now),/cancelled/);
});
