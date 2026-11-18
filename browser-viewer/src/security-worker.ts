import {validateSecurity} from './security-validation.js';
import type {SecurityInput} from './security-types.js';
const scope=globalThis as unknown as {onmessage:(event:MessageEvent<SecurityInput>)=>void;postMessage:(value:unknown)=>void};
scope.onmessage=event=>{void validateSecurity(event.data,()=>{},message=>scope.postMessage({progress:message})).then(report=>scope.postMessage({report})).catch(error=>scope.postMessage({error:error instanceof Error?error.message:String(error)}));};
