import type {BrowserSession,BrowserDocument} from './browser-session.js';
/** Security validation belongs to Full; Lite keeps the shared opening lifecycle. */
export class SecurityController {
  reset():void {}
  async start(_session:BrowserSession,_doc:BrowserDocument):Promise<void> {}
}
