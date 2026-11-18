// Refresh a permanent unpacked extension directory; keep its path and directory intact.
import {readFile,writeFile,readdir,lstat,mkdir,rm} from 'node:fs/promises';
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
 await mkdir(dirname(destination),{recursive:true});await directory(destination);
 let previous={files:{}};
 try{previous=JSON.parse(await regular(join(destination,'build-info.json')));}catch(error){if(error.code!=='ENOENT')throw error;}
 const stale=Object.keys(previous.files).filter(name=>!payloads.has(safeName(name)));
 // Preflight every destination before writing. Unrelated files are preserved.
 for(const name of [...payloads.keys(),'build-info.json',...stale]){
  const parts=name.split('/');let parent=destination;
  for(const part of parts.slice(0,-1)){parent=join(parent,part);await directory(parent);}
  try{if(!(await lstat(join(destination,name))).isFile())throw Error('Refusing to replace a non-file install entry: '+name);}
  catch(error){if(error.code!=='ENOENT')throw error;}
 }
 for(const [name,bytes]of payloads)await writeFile(join(destination,name),bytes);
 for(const name of stale)await rm(join(destination,name),{force:true});
 for(const [name,hash]of Object.entries(info.files))if(digest(await regular(join(destination,name)))!==hash)throw Error('Installed hash mismatch: '+name);
 await writeFile(join(destination,'build-info.json'),infoBytes);
 console.log(`Updated local viewer ${info.project}: ${destination}`);
 return info;
}
