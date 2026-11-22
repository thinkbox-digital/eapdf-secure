import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,writeFile,readFile,rm,lstat,symlink} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createHash} from 'node:crypto';
import {installLocalBuild} from '../scripts/local-install.mjs';
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
async function fixture(directory,version,extra={}){
 await mkdir(directory,{recursive:true});
 const files={'manifest.json':JSON.stringify({version:version.replace('-beta.','.'),name:'Local reader'}),'viewer.html':version,...extra};
 for(const [name,bytes]of Object.entries(files)){await mkdir(join(directory,name,'..'),{recursive:true});await writeFile(join(directory,name),bytes);}
 const info={project:version,files:Object.fromEntries(Object.entries(files).map(([name,bytes])=>[name,hash(bytes)]))};
 await writeFile(join(directory,'build-info.json'),JSON.stringify(info));return info;
}
test('updating an unpacked install keeps its directory, removes obsolete managed files and preserves unrelated files',async()=>{
 const temporary=await mkdtemp(join(tmpdir(),'eapdf-local-install-test-'));
 try{
  const old=join(temporary,'old'),current=join(temporary,'current'),install=join(temporary,'install');
  await fixture(old,'1.4.0-beta.3',{'obsolete.js':'old implementation'});await fixture(current,'1.5.0-beta.3',{'new.js':'new implementation'});
  await installLocalBuild(old,install,'1.4.0-beta.3');const inode=(await lstat(install)).ino;
  await writeFile(join(install,'user-note.txt'),'keep');await installLocalBuild(current,install,'1.5.0-beta.3');
  assert.equal((await lstat(install)).ino,inode);assert.equal(await readFile(join(install,'viewer.html'),'utf8'),'1.5.0-beta.3');
  assert.equal(await readFile(join(install,'user-note.txt'),'utf8'),'keep');await assert.rejects(lstat(join(install,'obsolete.js')),{code:'ENOENT'});
 }finally{await rm(temporary,{recursive:true,force:true});}
});
test('invalid or unexpected source bytes cannot replace a working installation',async()=>{
 const temporary=await mkdtemp(join(tmpdir(),'eapdf-local-install-invalid-'));
 try{
  const old=join(temporary,'old'),current=join(temporary,'current'),install=join(temporary,'install');
  await fixture(old,'1.4.0-beta.3');await installLocalBuild(old,install,'1.4.0-beta.3');await fixture(current,'1.5.0-beta.3');
  await writeFile(join(current,'viewer.html'),'changed');await assert.rejects(installLocalBuild(current,install,'1.5.0-beta.3'),/hash mismatch/);
  await fixture(current,'1.5.0-beta.3');await writeFile(join(current,'unexpected.js'),'extra');await assert.rejects(installLocalBuild(current,install,'1.5.0-beta.3'),/unexpected or missing/);
  await assert.rejects(installLocalBuild(current,install,'1.6.0-beta.3'),/Build version differs/);
  assert.equal(await readFile(join(install,'viewer.html'),'utf8'),'1.4.0-beta.3');
 }finally{await rm(temporary,{recursive:true,force:true});}
});
test('updates reject symbolic links instead of writing outside the install directory',async()=>{
 const temporary=await mkdtemp(join(tmpdir(),'eapdf-local-install-links-'));
 try{
  const source=join(temporary,'source'),install=join(temporary,'install'),outside=join(temporary,'outside');
  await fixture(source,'1.5.0-beta.3',{'vendor/library.js':'new'});await mkdir(install);await mkdir(outside);await symlink(outside,join(install,'vendor'));
  await assert.rejects(installLocalBuild(source,install,'1.5.0-beta.3'),/Expected an install directory/);
  await assert.rejects(lstat(join(outside,'library.js')),{code:'ENOENT'});
 }finally{await rm(temporary,{recursive:true,force:true});}
});

test('modified obsolete files survive and collisions fail before replacing the installed reader',async()=>{
 const temporary=await mkdtemp(join(tmpdir(),'eapdf-local-install-ownership-'));
 try{
  const old=join(temporary,'old'),current=join(temporary,'current'),install=join(temporary,'install');
  await fixture(old,'1.4.0-beta.3',{'obsolete.js':'old implementation'});
  await fixture(current,'1.5.0-beta.3',{'new.js':'new implementation'});
  await installLocalBuild(old,install,'1.4.0-beta.3');
  await writeFile(join(install,'new.js'),'unmanaged');
  await assert.rejects(installLocalBuild(current,install,'1.5.0-beta.3'),/changed or unmanaged/);
  assert.equal(await readFile(join(install,'viewer.html'),'utf8'),'1.4.0-beta.3');
  assert.equal(await readFile(join(install,'new.js'),'utf8'),'unmanaged');
  await rm(join(install,'new.js'));
  await writeFile(join(install,'obsolete.js'),'operator edit');
  await installLocalBuild(current,install,'1.5.0-beta.3');
  assert.equal(await readFile(join(install,'obsolete.js'),'utf8'),'operator edit');
 }finally{await rm(temporary,{recursive:true,force:true});}
});
