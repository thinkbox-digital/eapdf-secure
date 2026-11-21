// Run against independently created signed exports, preserving their original bytes.
import {chromium} from 'playwright';
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir,mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
process.chdir(fileURLToPath(new URL('../',import.meta.url)));
const fixtures=process.env.EAPDF_SECURITY_FIXTURES;
if(!fixtures)throw Error('Set EAPDF_SECURITY_FIXTURES to a temporary directory containing independently generated CMS_AND_PDF_1s.pdf, CMS_AND_PDF_TIMESTAMPED_1s.pdf, PADES_B_LTA_1s.pdf and renewed_1c.pdf.');
const temporary=await mkdtemp(join(tmpdir(),'eapdf-security-browser-'));
const extension=resolve('dist/chromium'),evidence=process.env.EAPDF_SECURITY_EVIDENCE??temporary;
await mkdir(evidence,{recursive:true});
const context=await chromium.launchPersistentContext('',{headless:true,acceptDownloads:true,executablePath:process.env.CHROMIUM_PATH??'/snap/chromium/current/usr/lib/chromium-browser/chrome',args:[`--disable-extensions-except=${extension}`,`--load-extension=${extension}`]});
try{
 await context.setOffline(true);const worker=context.serviceWorkers()[0]??await context.waitForEvent('serviceworker'),page=await context.newPage(),errors=[];
 page.on('pageerror',error=>errors.push(error.message));
 await page.goto(`chrome-extension://${new URL(worker.url()).host}/viewer.html`);await page.waitForFunction(()=>!document.querySelector('#file').disabled);
 const ready=()=>page.waitForFunction(()=>document.querySelector('#header-filename').dataset.security&&document.querySelector('#header-filename').dataset.security!=='checking',null,{timeout:150000});
 const open=async path=>{await page.locator('#file').setInputFiles(path);await ready();};
 const results=[];
 for(const name of ['CMS_AND_PDF_1s.pdf','CMS_AND_PDF_TIMESTAMPED_1s.pdf','PADES_B_LTA_1s.pdf','renewed_1c.pdf']){
  const path=join(fixtures,name);await open(path);
  const state=await page.locator('#header-filename').getAttribute('data-security'),summary=await page.locator('#security-checks').textContent();
  const archival=name.startsWith('PADES_')||name.startsWith('renewed_');
  assert.equal(state,archival?'unknown':'pass',name+' '+summary);assert.equal(await page.locator('#security-dialog').isVisible(),false,'Validation must not open the report');assert.match(summary,/C2PA provenance: signature: Verified/);assert.match(summary,/certificate trust: Not verified/);
  if(name==='CMS_AND_PDF_1s.pdf'){
   for(const width of [1280,1024,360]){
    await page.setViewportSize({width,height:760});
    const layout=await page.locator('#header-filename').evaluate(node=>{const range=document.createRange();range.selectNodeContents(node);const slot=node.parentElement;return {badge:node.getBoundingClientRect().width,text:range.getBoundingClientRect().width,slot:slot.getBoundingClientRect().width,background:getComputedStyle(slot).backgroundColor,header:getComputedStyle(document.querySelector('.app-header')).backgroundColor};});
    assert.ok(layout.badge<=layout.text+12,'Only the filename receives the highlight: '+JSON.stringify(layout));
    assert.ok(layout.badge<=layout.slot+1,'Long filenames stay inside their layout slot');
    assert.equal(layout.background,'rgba(0, 0, 0, 0)');assert.equal(layout.header,'rgb(255, 255, 255)');
    await page.screenshot({path:join(evidence,`filename-${width}.png`)});
   }
   await page.setViewportSize({width:1280,height:760});
  }
  await page.locator('#header-filename').click();assert.equal(await page.locator('#security-dialog').isVisible(),true);assert.equal(await page.locator('#security-dialog').evaluate(el=>el.matches(':modal')),true);
  assert.equal(await page.locator('#header-filename').evaluate(el=>getComputedStyle(el).color),archival?'rgb(133, 77, 14)':'rgb(22, 101, 52)');assert.equal(await page.locator('#security-dialog').evaluate(el=>el.scrollTop),0);
  assert.equal(await page.locator('#security-dialog').evaluate(el=>{const r=el.getBoundingClientRect();return Math.abs(r.x+r.width/2-innerWidth/2)<1&&Math.abs(r.y+r.height/2-innerHeight/2)<1;}),true,'Security report is centred in the viewport');
  assert.equal(await page.locator('#security-dialog').evaluate(el=>{const r=el.getBoundingClientRect();return r.width>=900&&r.height>=innerHeight*.8;}),true,'Security report is wider and taller');
  const reportText=await page.locator('#security-dialog').innerText();
  assert.ok(!reportText.includes('This PDF and the files it contains.'));assert.ok(!reportText.includes('Choose public CA certificates you trust.'));assert.ok(!reportText.includes('Use the standalone validator for general PAdES profiles or unsupported evidence.'));
  assert.equal(await page.locator('#security-scope').count(),0);
  for(const [check,category]of [['C2PA claim signature','C2PA'],['PDF signature 1','PAdES'],['Selected trust policy','Other checks']])assert.equal(await page.locator(`.security-check[title="${check}"]`).evaluate(node=>node.closest('.security-check-group').dataset.category),category);
  if(name==='CMS_AND_PDF_1s.pdf'){
   await page.evaluate(()=>{const dialog=document.querySelector('#security-dialog'),row=document.querySelector('#security-checks .security-check');row.open=true;window.securityKeptRow=row;window.securityClosed=0;dialog.addEventListener('close',()=>window.securityClosed++);dialog.scrollTop=100;document.querySelector('#security-trust-choose').focus({preventScroll:true});window.securityKeptScroll=dialog.scrollTop;window.securityKeptTop=row.getBoundingClientRect().top;});
   await page.locator('#security-trust-files').setInputFiles({name:'invalid-root.cer',mimeType:'application/pkix-cert',buffer:Buffer.from([1,2,3])});
   await page.waitForFunction(()=>document.querySelector('#security-dialog').dataset.updating==='false'&&document.querySelector('.security-check[title="Selected trust policy"] .security-check-detail')?.textContent.includes('invalid'));
   assert.equal(await page.locator('#security-dialog').isVisible(),true,'Certificate selection keeps the report open');
   assert.equal(await page.evaluate(()=>window.securityClosed),0,'Certificate selection never closes and reopens the dialog');
   assert.equal(await page.evaluate(()=>document.querySelector('#security-checks .security-check')===window.securityKeptRow&&window.securityKeptRow.open),true,'Existing check rows and expanded details survive rechecking');
   assert.equal(await page.evaluate(()=>document.activeElement.id),'security-trust-choose','Certificate selection preserves focus');
   assert.ok(await page.evaluate(()=>Math.abs(window.securityKeptRow.getBoundingClientRect().top-window.securityKeptTop)<1),'Certificate selection preserves the visible check position');
   await page.locator('#security-trust-clear').click();
   await page.waitForFunction(()=>document.querySelector('#security-dialog').dataset.updating==='false'&&document.querySelector('.security-check[title="Selected trust policy"] .security-check-detail')?.textContent.includes('No independently trusted'));
   assert.equal(await page.evaluate(()=>window.securityClosed),0,'Clearing certificates also keeps the report open');
  }
  if(archival){
   const aggregate=page.locator('.security-check[title="PAdES archival level and allowed revisions"] .security-result');
   assert.equal(await aggregate.getAttribute('data-status'),'UNKNOWN','Archival protection requires explicitly selected roots');
   await page.locator('#security-trust-files').setInputFiles(join(fixtures,'test-root.pem'));
   await page.waitForFunction(()=>document.querySelector('#security-dialog').dataset.updating==='false'&&document.querySelector('#security-selected-certificates .security-certificate'),null,{timeout:150000});await ready();
   assert.equal(await aggregate.getAttribute('data-status'),'PASS',await page.locator('#security-checks').textContent());
   assert.equal(await page.locator('#header-filename').getAttribute('data-security'),'pass');
   assert.equal(await page.locator('.security-check[title="Creator archival credentials"] .security-result').getAttribute('data-status'),'PASS');
  }
  const download=page.waitForEvent('download');await page.locator('#security-download').click();const report=JSON.parse(await readFile(await(await download).path(),'utf8'));
  const source=await readFile(path);assert.equal(report.fileSha256,createHash('sha256').update(source).digest('hex'));assert.equal(report.filename,name);assert.equal(report.status,'PASS');assert.equal(report.scope,'Export security');assert.ok(report.checks.every(check=>check.category));
  if(archival)assert.equal(report.checks.find(check=>check.name==='Creator archival credentials').category,'C2PA + PAdES');
  results.push({name,sha256:report.fileSha256,checks:report.checks});
  if(archival){await page.locator('#security-trust-clear').click();await ready();}
  await page.locator('#security-close').click();await page.locator('#header-filename').focus();await page.keyboard.press('Enter');assert.equal(await page.locator('#security-dialog').isVisible(),true);await page.keyboard.press('Escape');
 }
 const signed=await readFile(join(fixtures,'CMS_AND_PDF_1s.pdf')),changed=Buffer.from(signed);changed[7]=changed[7]===48?49:48;
 const bad=join(temporary,'tampered_1s.pdf'),appended=join(temporary,'appended_1s.pdf');await writeFile(bad,changed);await writeFile(appended,Buffer.concat([signed,Buffer.from('\n% later bytes\n')]));
 for(const path of [bad,appended]){await open(path);assert.equal(await page.locator('#header-filename').getAttribute('data-security'),'fail');assert.equal(await page.locator('#security-dialog').isVisible(),false,'Failed checks must not open the report');await page.locator('#header-filename').click();await page.locator('#security-close').click();}
 await open(resolve('tests/fixtures/ordinary.pdf'));assert.equal(await page.locator('#header-filename').getAttribute('data-security'),'not_applicable');assert.equal(await page.locator('.security-check[title="PAdES archival level and allowed revisions"] .security-result').getAttribute('data-status'),'NOT_APPLICABLE');assert.equal(await page.locator('.security-check[title="Creator archival revision history"]').count(),0);assert.equal(await page.locator('#security-dialog').isVisible(),false,'Unsigned state must not open the report');
 await page.locator('#file').setInputFiles(join(fixtures,'PADES_B_LTA_1s.pdf'));await page.waitForFunction(()=>document.querySelector('#header-filename').dataset.security==='checking');await page.locator('#close').click();
 await page.waitForTimeout(500);assert.equal(await page.locator('#header-filename').getAttribute('data-security'),null);assert.equal(await page.locator('#security-dialog').isVisible(),false);
 await open(join(fixtures,'CMS_AND_PDF_1s.pdf'));assert.equal(await page.locator('#security-dialog').isVisible(),false);await page.locator('#header-filename').click();await page.screenshot({path:join(evidence,'full-security-report.png'),fullPage:true});
 await page.setViewportSize({width:360,height:760});
 assert.equal(await page.locator('#security-dialog').evaluate(el=>{const r=el.getBoundingClientRect();return r.width<=innerWidth&&r.height<=innerHeight&&Math.abs(r.x+r.width/2-innerWidth/2)<1&&Math.abs(r.y+r.height/2-innerHeight/2)<1;}),true,'Security report remains centred and bounded on a narrow screen');
 assert.deepEqual(errors,[]);await writeFile(join(evidence,'browser-security.json'),JSON.stringify({status:'PASS',results,negativeControls:['changed signed byte','appended bytes','unsigned','close cancels stale worker']},null,2)+'\n');
 console.log('PASS: Full offline CMS, timestamps, C2PA, renewal, filename states, filename-activated modal report, enlarged centred layout, download, keyboard access and cancellation.');
}finally{await context.close();await rm(temporary,{recursive:true,force:true});}
