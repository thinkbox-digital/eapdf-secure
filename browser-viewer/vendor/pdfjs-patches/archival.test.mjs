import test from 'node:test';
import assert from 'node:assert/strict';
import {pathToFileURL} from 'node:url';
import {resolve} from 'node:path';
const load=path=>import(pathToFileURL(resolve(process.env.PDFJS_SOURCE,path)));
await load('node_modules/core-js/actual/map/get-or-insert-computed.js');
const {LocalPdfManager}=await load('src/core/pdf_manager.js');
const policy='{"schemaVersion":1,"scope":"EMAIL_EXPORT","specification":"2.2","profile":"eapdf-1"}\n';
const stream=text=>`<< /Length ${text.length} >>\nstream\n${text}\nendstream`;
const appearance='<< /Type /XObject /Subtype /Form /BBox [0 0 0 0] /Length 0 >>\nstream\n\nendstream';
const widget=(number,value,ap,extra='')=>`<< /Type /Annot /Subtype /Widget /FT /Sig /F 132 /Rect [0 0 0 0] /P 4 0 R /T (Signature${number}) /AP << /N ${ap} 0 R >> /V ${value} 0 R ${extra} >>`;
function fixture({changed=false,reverted=false,information=false,visible=false,duplicate=false,broken=false,streamChanged=false}={}){
 let bytes='%PDF-1.7\n',previous,ends=[];
 const form=fields=>`<< /SigFlags 3 /Fields [${fields.map(n=>n+' 0 R').join(' ')}] >>`;
 const page=fields=>`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 5 0 R /Annots [${fields.map(n=>n+' 0 R').join(' ')}] >>`;
 const catalog=dss=>`<< /Type /Catalog /Pages 2 0 R /AcroForm 3 0 R /AF [8 0 R 9 0 R] ${dss?'/DSS 18 0 R':''} >>`;
 const signature=timestamp=>`<< /Type /${timestamp?'DocTimeStamp':'Sig'} /Filter /Adobe.PPKLite /SubFilter /${timestamp?'ETSI.RFC3161':'ETSI.CAdES.detached'} /Contents <0102> /ByteRange [${Array(4).fill('0000000000').join(' ')}] >>`;
 function save(objects,signed=false){
  const start=bytes.length,entries=[];
  for(const [number,value]of Object.entries(objects)){entries.push([Number(number),bytes.length]);bytes+=`${number} 0 obj\n${value}\nendobj\n`;}
  const xref=bytes.length;bytes+='xref\n';
  for(const [number,offset]of entries)bytes+=`${number} 1\n${String(offset).padStart(10,'0')} 00000 n \n`;
  bytes+=`trailer\n<< /Size 64 /Root 1 0 R /Info 14 0 R ${previous!==undefined?'/Prev '+previous:''} >>\nstartxref\n${xref}\n%%EOF\n`;
  if(signed){const gap=bytes.indexOf('<0102>',start),range=[0,gap,gap+6,bytes.length-gap-6];bytes=bytes.slice(0,start)+bytes.slice(start).replace(Array(4).fill('0000000000').join(' '),range.map(n=>String(n).padStart(10,'0')).join(' '));ends.push(bytes.length);}
  previous=xref;
 }
 save({1:catalog(false),2:'<< /Type /Pages /Count 1 /Kids [4 0 R] >>',3:form([6]),4:page([6]),5:stream(''),6:widget(1,7,10,'/TU (Archive export signature)'),7:signature(false),8:'<< /Type /Filespec /F (eapdf-content-credentials.c2pa) /UF (eapdf-content-credentials.c2pa) /EF << /F 11 0 R /UF 11 0 R >> /AFRelationship /C2PA_Manifest >>',9:'<< /Type /Filespec /F (eapdf-c2pa-policy.json) /UF (eapdf-c2pa-policy.json) /EF << /F 13 0 R >> /AFRelationship /Supplement >>',10:appearance,11:stream('\0'.repeat(32768)),13:stream(policy),14:'<< /Title (Original) >>'},true);
 const unsigned={1:catalog(true),18:'<< /Certs [] /CRLs [] /OCSPs [] >>'};
 if(changed||reverted)unsigned[4]=page([6]).replace('612 792','612 700');
 if(information)unsigned[14]='<< /Title (Changed) >>';
 if(streamChanged)unsigned[5]=stream('q Q');
 save(unsigned);
 save({3:form([6,15]),4:page([6,15]).replace('612 792',changed?'612 700':'612 792'),15:widget(2,16,17).replace('0 0 0 0',visible?'0 0 10 10':'0 0 0 0'),16:signature(true),17:appearance},true);
 save({3:form([6,15,19]),4:page([6,15,19]).replace('612 792',changed?'612 700':'612 792'),19:widget(3,20,21,'/TU (C2PA archival document timestamp)'),20:signature(true),21:appearance,...(duplicate?{1:catalog(true).replace('/Pages 2 0 R','/Pages 2 0 R /Pages 2 0 R')}:{})},true);
 if(broken)bytes=bytes.replace(/\/Prev (\d+) (?=>>\nstartxref\n\d+\n%%EOF\n$)/,'/Prev 000000');
 return {bytes:new TextEncoder().encode(bytes),ends};
}
async function inspect(options){
 const {bytes,ends}=fixture(options),manager=new LocalPdfManager({source:bytes,docId:'archival-fixture',enableXfa:false,evaluatorOptions:{},handler:{send(){}}});
 const document=manager.pdfDocument;document.checkHeader();document.parseStartXRef();document.parse();
 return document.catalog.getDocumentAssets({securityOnly:true,creatorArchival:ends});
}
test('creator revision inspection includes unsigned DSS saves and retains protected objects',async()=>{
 const result=await inspect({});assert.equal(result.status,'COMPLETE',result.detail);assert.equal(result.revisions.length,4);
 assert.equal(new Set(result.revisions.map(r=>r.protectedSha256)).size,1);
 assert.deepEqual(result.revisions.map(r=>r.signatures.length),[1,1,2,3]);
});
test('content, Info and even subsequently reverted unsigned changes alter the protected graph',async()=>{
 for(const options of [{changed:true},{reverted:true},{information:true},{streamChanged:true}]){
  const result=await inspect(options);assert.equal(result.status,'COMPLETE',result.detail);
  assert.ok(new Set(result.revisions.map(r=>r.protectedSha256)).size>1,JSON.stringify(options));
 }
});
test('visible timestamp widgets, duplicate keys and a broken Prev chain cannot pass',async()=>{
 for(const options of [{visible:true},{duplicate:true}]){const result=await inspect(options);assert.notEqual(result.status,'COMPLETE',JSON.stringify(options));}
 await assert.rejects(inspect({broken:true}),error=>error.name==='XRefParseException');
});
