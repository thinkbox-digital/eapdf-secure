// Refresh a permanent unpacked extension directory; keep its path and directory intact.
import {readFile,writeFile,readdir,lstat,mkdir,mkdtemp,link,rename,rm,unlink} from 'node:fs/promises';
import {join,resolve,dirname} from 'node:path';
import {createHash} from 'node:crypto';
import {extensionVersion} from './extension-version.mjs';
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
function safeName(name){
 if(typeof name!=='string'||!name||name.includes('\\')||name.split('/').some(part=>!part||part==='.'||part==='..')||/[\x00-\x1f]/.test(name))throw Error('Unsafe installed file name: '+name);
 return name;
}
async function regular(path){if(!(await lstat(path)).isFile())throw Error('Expected a regular build file: '+path);return readFile(path);}
async function directory(path){
 try{if(!(await lstat(path)).isDirectory())throw Error('Expected an install directory: '+path);}
 catch(error){if(error.code!=='ENOENT')throw error;await mkdir(path);}
}
export async function installLocalBuild(source,destination,expectedVersion){
 source=resolve(source);destination=resolve(destination);
 if(source===destination||source.startsWith(destination+'/')||destination.startsWith(source+'/'))throw Error('Build and install directories must be separate.');
 const infoBytes=await regular(join(source,'build-info.json')),info=JSON.parse(infoBytes);
 if(info.project!==expectedVersion)throw Error('Build version differs from the requested local installation.');
 const payloads=new Map();
 for(const [name,hash]of Object.entries(info.files)){
  safeName(name);if(name==='build-info.json')throw Error('Build inventory must not include itself.');
  const bytes=await regular(join(source,name));if(digest(bytes)!==hash)throw Error('Build hash mismatch: '+name);payloads.set(name,bytes);
 }
 const enumerate=async(directory,prefix='')=>{
  const names=[];for(const entry of await readdir(directory,{withFileTypes:true})){
   const name=prefix+entry.name;if(entry.isDirectory())names.push(...await enumerate(join(directory,entry.name),name+'/'));
   else{if(!entry.isFile())throw Error('Unexpected non-file build entry: '+name);names.push(name);}
  }return names;
 };
 const actual=await enumerate(source),approved=new Set([...payloads.keys(),'build-info.json']);
 if(actual.length!==approved.size||actual.some(name=>!approved.has(name)))throw Error('Build contains unexpected or missing files.');
 if(payloads.has('manifest.json')){
  const manifest=JSON.parse(payloads.get('manifest.json'));
  if(manifest.version!==extensionVersion(info.project))throw Error('Extension manifest version differs from the build.');
 }
 await mkdir(dirname(destination),{recursive:true});
 await directory(destination);
 const currentHash = async path => {
  try { return digest(await regular(path)); }
  catch(error) { if(error.code!=='ENOENT')throw error; }
 };
 const metadata = join(destination, 'build-info.json');
 let previousBytes;
 try { previousBytes = await regular(metadata); }
 catch(error) { if(error.code!=='ENOENT')throw error; }
 const previous = previousBytes ? JSON.parse(previousBytes) : {files:{}};
 const stale = Object.keys(previous.files).filter(name => !payloads.has(safeName(name)));
 const targetHashes = new Map();
 for (const [name, bytes] of payloads) {
  let parent = destination;
  for (const part of name.split('/').slice(0, -1)) {
   parent = join(parent, part);
   await directory(parent);
  }
  const existing = await currentHash(join(destination, name));
  if (existing !== undefined && existing !== previous.files[name] && existing !== digest(bytes)) {
   throw new Error('Refusing to replace a changed or unmanaged install file: ' + name);
  }
  targetHashes.set(name, existing);
 }

 const staging = await mkdtemp(join(destination, '.eapdf-install-'));
 try {
  for (const [name, bytes] of [...payloads, ['build-info.json', infoBytes]]) {
   const file = join(staging, name);
   await mkdir(dirname(file), {recursive:true});
   await writeFile(file, bytes, { flag: 'wx' });
  }
  for (const [name, hash] of targetHashes) {
   const file = join(destination, name);
   if (await currentHash(file) !== hash) throw new Error('Install file changed during update: ' + name);
   if (hash === undefined) await link(join(staging, name), file);
   else if (hash !== info.files[name]) await rename(join(staging, name), file);
  }
  for (const [name, hash] of Object.entries(info.files)) {
   if (await currentHash(join(destination, name)) !== hash) throw new Error('Installed hash mismatch: ' + name);
  }
  if (await currentHash(metadata) !== (previousBytes ? digest(previousBytes) : undefined)) {
   throw new Error('Install metadata changed during update');
  }
  const stagedMetadata = join(staging, 'build-info.json');
  if (previousBytes) await rename(stagedMetadata, metadata);
  else await link(stagedMetadata, metadata);

  for (const name of stale) {
   let parent = destination;
   let present = true;
   for (const part of name.split('/').slice(0, -1)) {
    parent = join(parent, part);
    try {
     if (!(await lstat(parent)).isDirectory()) { present = false; break; }
    } catch (error) {
     if (error.code !== 'ENOENT') throw error;
     present = false;
     break;
    }
   }
   if (!present) continue;
   const file = join(destination, name);
   try {
    if ((await lstat(file)).isFile() && await currentHash(file) === previous.files[name]) await unlink(file);
   } catch (error) {
    if (error.code !== 'ENOENT') throw error;
   }
  }
 } finally {
  await rm(staging, { recursive: true, force: true });
 }
 console.log(`Updated local viewer ${info.project}: ${destination}`);
 return info;
}
