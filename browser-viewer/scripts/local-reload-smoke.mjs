// Exercise a real extension update at one fixed path using consecutive local packages.
import {chromium} from 'playwright';
import {readFile,mkdtemp,mkdir,rm,writeFile,lstat} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import assert from 'node:assert/strict';
import {extensionVersion} from './extension-version.mjs';
import {installLocalBuild} from './local-install.mjs';
const root=fileURLToPath(new URL('../',import.meta.url));
const {version}=JSON.parse(await readFile(join(root,'package.json'),'utf8'));
const match=/^(\d+)\.(\d+)\.0(-beta\.\d+)?$/.exec(version);
assert.ok(match&&Number(match[2])>0,'Reload check needs a previous local minor version.');
const previous=`${match[1]}.${Number(match[2])-1}.0${match[3]??''}`;
const temporary=await mkdtemp(join(tmpdir(),'eapdf-extension-reload-')),install=join(temporary,'installed');let context;
try{
 for(const [name,value]of [['old',previous],['new',version]])execFileSync('python3',['-c','import sys,zipfile\nwith zipfile.ZipFile(sys.argv[1]) as archive: archive.extractall(sys.argv[2])',join(root,'releases',`eapdf-viewer-even-more-lite-${value}-chromium.zip`),join(temporary,name)]);
 await installLocalBuild(join(temporary,'old'),install,previous);const inode=(await lstat(install)).ino;
 context=await chromium.launchPersistentContext(join(temporary,'profile'),{headless:true,executablePath:process.env.CHROMIUM_PATH??chromium.executablePath(),args:[`--disable-extensions-except=${install}`,`--load-extension=${install}`]});
 await context.setOffline(true);
 const oldWorker=context.serviceWorkers()[0]??await context.waitForEvent('serviceworker');
 const id=new URL(oldWorker.url()).host;assert.equal(await oldWorker.evaluate(()=>chrome.runtime.getManifest().version),extensionVersion(previous));
 const oldPage=await context.newPage();await oldPage.goto(`chrome-extension://${id}/viewer.html`);
 await oldPage.waitForFunction(()=>!!document.querySelector('#file')&&!document.querySelector('#file').disabled);
 await installLocalBuild(join(temporary,'new'),install,version);assert.equal((await lstat(install)).ino,inode);
 const manager=await context.newPage();await manager.goto('chrome://extensions');
 await manager.locator('#devMode').click();
 await manager.locator(`extensions-item[id="${id}"] #dev-reload-button`).click();
 // Use the same management-page Reload button as a locally installed viewer.
 const page=await context.newPage();
 const deadline=Date.now()+10000;
 while(true){
  try{await page.goto(`chrome-extension://${id}/viewer.html`);break;}
  catch(error){if(Date.now()>=deadline)throw error;await new Promise(done=>setTimeout(done,100));}
 }
 assert.deepEqual(await page.evaluate(()=>({id:chrome.runtime.id,version:chrome.runtime.getManifest().version})),{id,version:extensionVersion(version)});
 await page.waitForFunction(()=>!!document.querySelector('#file')&&!document.querySelector('#file').disabled);
 assert.match(await page.locator('footer').textContent(),new RegExp(version.replaceAll('.','\\.')));
 await page.locator('#file').setInputFiles(join(root,'tests/fixtures/ordinary.pdf'));
 await page.waitForFunction(()=>!document.querySelector('#page-surface').hidden&&document.querySelector('#viewport').getAttribute('aria-busy')==='false');
 await page.locator('#next').click();await page.waitForFunction(()=>document.querySelector('#page').value==='2'&&document.querySelector('#viewport').getAttribute('aria-busy')==='false');
 const report={status:'PASS',from:previous,to:version,extensionId:id,directoryPreserved:true,extensionIdPreserved:true,reloadedRendering:true,browser:context.browser()?.version()};
 const evidence=join(root,'../output/even-more-lite-reload-2026-11-17');await mkdir(evidence,{recursive:true});await writeFile(join(evidence,'reload-check.json'),JSON.stringify(report,null,2)+'\n');
 console.log(JSON.stringify(report));
}finally{await context?.close();await rm(temporary,{recursive:true,force:true});}
