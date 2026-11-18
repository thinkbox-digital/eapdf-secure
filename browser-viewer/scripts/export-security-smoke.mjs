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
  assert.equal(state,'pass',name+' '+summary);assert.equal(await page.locator('#security-dialog').isVisible(),false,'Validation must not open the report');assert.match(summary,/C2PA provenance: signature: Verified/);assert.match(summary,/certificate trust: Not verified/);
  await page.locator('#header-filename').click();assert.equal(await page.locator('#security-dialog').isVisible(),true);assert.equal(await page.locator('#security-dialog').evaluate(el=>el.matches(':modal')),true);
  assert.equal(await page.locator('#header-filename').evaluate(el=>getComputedStyle(el).color),'rgb(22, 101, 52)');assert.equal(await page.locator('#security-dialog').evaluate(el=>el.scrollTop),0);
  assert.equal(await page.locator('#security-dialog').evaluate(el=>{const r=el.getBoundingClientRect();return Math.abs(r.x+r.width/2-innerWidth/2)<1&&Math.abs(r.y+r.height/2-innerHeight/2)<1;}),true,'Security report is centred in the viewport');
  assert.equal(await page.locator('#security-dialog').evaluate(el=>{const r=el.getBoundingClientRect();return r.width>=900&&r.height>=innerHeight*.8;}),true,'Security report is wider and taller');
  const download=page.waitForEvent('download');await page.locator('#security-download').click();const report=JSON.parse(await readFile(await(await download).path(),'utf8'));
  const source=await readFile(path);assert.equal(report.fileSha256,createHash('sha256').update(source).digest('hex'));assert.equal(report.filename,name);assert.equal(report.status,'PASS');
  results.push({name,sha256:report.fileSha256,checks:report.checks});
  await page.locator('#security-close').click();await page.locator('#header-filename').focus();await page.keyboard.press('Enter');assert.equal(await page.locator('#security-dialog').isVisible(),true);await page.keyboard.press('Escape');
 }
 const signed=await readFile(join(fixtures,'CMS_AND_PDF_1s.pdf')),changed=Buffer.from(signed);changed[7]=changed[7]===48?49:48;
 const bad=join(temporary,'tampered_1s.pdf'),appended=join(temporary,'appended_1s.pdf');await writeFile(bad,changed);await writeFile(appended,Buffer.concat([signed,Buffer.from('\n% later bytes\n')]));
 for(const path of [bad,appended]){await open(path);assert.equal(await page.locator('#header-filename').getAttribute('data-security'),'fail');assert.equal(await page.locator('#security-dialog').isVisible(),false,'Failed checks must not open the report');await page.locator('#header-filename').click();await page.locator('#security-close').click();}
 await open(resolve('tests/fixtures/ordinary.pdf'));assert.equal(await page.locator('#header-filename').getAttribute('data-security'),'not_applicable');assert.equal(await page.locator('#security-dialog').isVisible(),false,'Unsigned state must not open the report');
 await page.locator('#file').setInputFiles(join(fixtures,'PADES_B_LTA_1s.pdf'));await page.waitForFunction(()=>document.querySelector('#header-filename').dataset.security==='checking');await page.locator('#close').click();
 await page.waitForTimeout(500);assert.equal(await page.locator('#header-filename').getAttribute('data-security'),null);assert.equal(await page.locator('#security-dialog').isVisible(),false);
 await open(join(fixtures,'CMS_AND_PDF_1s.pdf'));assert.equal(await page.locator('#security-dialog').isVisible(),false);await page.locator('#header-filename').click();await page.screenshot({path:join(evidence,'full-security-report.png'),fullPage:true});
 await page.setViewportSize({width:360,height:760});
 assert.equal(await page.locator('#security-dialog').evaluate(el=>{const r=el.getBoundingClientRect();return r.width<=innerWidth&&r.height<=innerHeight&&Math.abs(r.x+r.width/2-innerWidth/2)<1&&Math.abs(r.y+r.height/2-innerHeight/2)<1;}),true,'Security report remains centred and bounded on a narrow screen');
 assert.deepEqual(errors,[]);await writeFile(join(evidence,'browser-security.json'),JSON.stringify({status:'PASS',results,negativeControls:['changed signed byte','appended bytes','unsigned','close cancels stale worker']},null,2)+'\n');
 console.log('PASS: Full offline CMS, timestamps, C2PA, renewal, filename states, filename-activated modal report, enlarged centred layout, download, keyboard access and cancellation.');
}finally{await context.close();await rm(temporary,{recursive:true,force:true});}
