import {validateSecurity} from './security-validation.js';
import type {CreatorArchiveFacts,SecurityInput} from './security-types.js';
const scope=globalThis as unknown as {onmessage:(event:MessageEvent<SecurityInput|{archivalResponse:CreatorArchiveFacts}>)=>void;postMessage:(value:unknown)=>void};
let started=false,resolveArchive:((facts:CreatorArchiveFacts)=>void)|undefined;
scope.onmessage=event=>{
  if('archivalResponse'in event.data){const resolve=resolveArchive;resolveArchive=undefined;resolve?.(event.data.archivalResponse);return;}
  if(started)return;started=true;
  void validateSecurity(event.data,()=>{},message=>scope.postMessage({progress:message}),ends=>new Promise(resolve=>{
    resolveArchive=resolve;scope.postMessage({archivalRequest:{ends}});
  })).then(report=>scope.postMessage({report})).catch(error=>scope.postMessage({error:error instanceof Error?error.message:String(error)}));
};
