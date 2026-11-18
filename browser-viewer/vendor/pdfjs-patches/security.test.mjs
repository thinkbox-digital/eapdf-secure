import test from 'node:test';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
import {resolve} from 'node:path';
const load=path=>import(pathToFileURL(resolve(process.env.PDFJS_SOURCE,path)));
const {DocumentAssets}=await load('src/core/document_assets.js');
const {Dict,Name}=await load('src/core/primitives.js');
const {Stream}=await load('src/core/stream.js');
const dict=values=>{const result=new Dict();for(const [key,value]of Object.entries(values))result.set(key,value);return result;};
const signature=()=>dict({Type:Name.get('Sig'),SubFilter:Name.get('adbe.pkcs7.detached'),Contents:'\x01\x02',ByteRange:[0,10,20,30]});
test('security facts follow inherited signature fields and deduplicate DocMDP',async()=>{
 const value=signature(),root=dict({AcroForm:dict({Fields:[dict({FT:Name.get('Sig'),Kids:[dict({V:value})]})]}),Perms:dict({DocMDP:value})});
 const facts=await new DocumentAssets({xref:{}},root).inspect({securityOnly:true});
 assert.equal(facts.signatures.length,1);assert.deepEqual([...facts.signatures[0].contents],[1,2]);assert.equal(facts.encrypted,false);
 assert.equal(facts.signatures[0].subFilter,'adbe.pkcs7.detached');assert.deepEqual(facts.files,[]);
});
test('cyclic and malformed signature fields cannot become an unsigned result',async()=>{
 const child=dict({});child.set('Kids',[child]);const root=dict({AcroForm:dict({Fields:[child]})});
 await assert.rejects(new DocumentAssets({xref:{}},root).inspect({securityOnly:true}),/cyclic/);
 const value=signature();value.set('ByteRange',[0,1,2]);root.set('AcroForm',dict({Fields:[dict({FT:Name.get('Sig'),V:value})]}));
 await assert.rejects(new DocumentAssets({xref:{}},root).inspect({securityOnly:true}),/Invalid/);
});
test('signature count and payload limits bound security inspection',async()=>{
 const root=dict({AcroForm:dict({Fields:Array.from({length:65},()=>dict({FT:Name.get('Sig'),V:signature()}))})});
 await assert.rejects(new DocumentAssets({xref:{}},root).inspect({securityOnly:true}),/oversized/);
});
test('DSS evidence is decoded within per-file, total-byte and entry-count limits',async()=>{
 const root=dict({DSS:dict({Certs:[new Stream(new Uint8Array([1,2]))],CRLs:[new Stream(new Uint8Array([3]))],OCSPs:[]})});
 let facts=await new DocumentAssets({xref:{}},root).inspect({securityOnly:true});
 assert.deepEqual([...facts.evidence.certificates[0]],[1,2]);assert.deepEqual([...facts.evidence.crls[0]],[3]);assert.equal(facts.evidence.error,undefined);
 for(const entries of [[dict({})],[new Stream(new Uint8Array(1048577))],Array.from({length:33},()=>new Stream(new Uint8Array([1]))),Array.from({length:5},()=>new Stream(new Uint8Array(900000)))]){
  root.set('DSS',dict({CRLs:entries}));facts=await new DocumentAssets({xref:{}},root).inspect({securityOnly:true});assert.match(facts.evidence.error,/limits/);
  assert.ok(facts.evidence.crls.reduce((n,b)=>n+b.length,0)<=4194304);
 }
});
