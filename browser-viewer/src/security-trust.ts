import {OctetString,ObjectIdentifier,Constructed,GeneralizedTime} from 'asn1js';
import {Certificate,CertificateChainValidationEngine,CertificateRevocationList,BasicOCSPResponse,OCSPResponse,RelativeDistinguishedNames,CryptoEngine} from 'pkijs';
import type {ICryptoEngine} from 'pkijs';
import {certificate,der,hex,UnsupportedSecurity} from './security-cms.js';
import {sha256} from './domain/hashes.js';
import type {SecurityCheck,SecurityEvidence,SecurityState,SelectedTrustCertificate} from './security-types.js';

type Verdict={status:SecurityState;detail:string};
export type TrustResult={trust:Verdict;revocation:Verdict};
const verdict=(status:SecurityState,detail:string):Verdict=>({status,detail});
const unknown=(detail:string)=>verdict('UNKNOWN',detail);
const id=(cert:Certificate)=>hex(cert.tbsView);
const extension=(cert:Certificate,oid:string)=>cert.extensions?.find(e=>e.extnID===oid);
const usage=(cert:Certificate,mask:number)=>{
  const value=extension(cert,'2.5.29.15');
  return !value||!!(value.parsedValue?.valueBlock.valueHexView[0]&mask);
};
const eku=(cert:Certificate,oid:string)=>extension(cert,'2.5.29.37')?.parsedValue?.keyPurposes?.includes(oid)===true;
const strong=(cert:Certificate)=>{
  const spki=cert.subjectPublicKeyInfo,algorithm=spki.algorithm.algorithmId;
  if(algorithm==='1.2.840.113549.1.1.1'||algorithm==='1.2.840.113549.1.1.10'){
    const key=spki.parsedKey as {modulus?:{valueBlock:{valueHexView:Uint8Array}}}|undefined;
    const bytes=key?.modulus?.valueBlock.valueHexView;if(!bytes)return false;
    let first=0;while(first<bytes.length&&bytes[first]===0)first++;
    return first<bytes.length&&(bytes.length-first-1)*8+32-Math.clz32(bytes[first]!)>=2048;
  }
  return algorithm==='1.2.840.10045.2.1'&&spki.algorithm.algorithmParams instanceof ObjectIdentifier&&['1.2.840.10045.3.1.7','1.3.132.0.34','1.3.132.0.35'].includes(spki.algorithm.algorithmParams.valueBlock.toString());
};
const fresh=(start:Date,end:Date|undefined,at:Date)=>Number.isFinite(start.getTime())&&!!end&&Number.isFinite(end.getTime())&&start<=at&&at<=end&&start<=end;
// SHA-1 remains usable for OCSP certificate identifiers, never evidence signatures.
const signatures=new Set(['1.2.840.113549.1.1.11','1.2.840.113549.1.1.12','1.2.840.113549.1.1.13','1.2.840.113549.1.1.10','1.2.840.10045.4.3.2','1.2.840.10045.4.3.3','1.2.840.10045.4.3.4']);
const criticalExtensions=new Set(['2.5.29.19','2.5.29.15','2.5.29.37','2.5.29.17','2.5.29.30','2.5.29.32','2.5.29.33','2.5.29.36','2.5.29.54']);
const unsupportedCritical=(cert:Certificate)=>cert.extensions?.some(e=>e.critical&&(!criticalExtensions.has(e.extnID)||!e.parsedValue));

/** Bounded offline X.509 checks. PDF revisions and C2PA ecosystem trust stay separate. */
export class OfflineTrust {
  private roots:Certificate[]=[];private certificates:Certificate[]=[];
  private crls:CertificateRevocationList[]=[];private ocsps:BasicOCSPResponse[]=[];
  private evidenceError='';private rootError='';
  // PKI.js 3.4's CryptoEngine/ICryptoEngine Ed25519 overload declarations differ.
  // These checks use only its shared RSA/ECDSA/digest methods, never generateKey.
  private readonly engine=new CryptoEngine({name:'eapdf-offline-trust',crypto:globalThis.crypto,subtle:globalThis.crypto.subtle}) as unknown as ICryptoEngine;
  constructor(evidence:SecurityEvidence|undefined,roots:Uint8Array[],private readonly check:()=>void){
    try {
      if(roots.length>32||roots.reduce((n,b)=>n+b.length,0)>1048576)throw Error('Trusted certificate limit');
      this.roots=roots.map(certificate);
      for(const cert of this.roots)if(extension(cert,'2.5.29.19')?.parsedValue?.cA!==true)throw Error('Select CA root certificates, not signing credentials');
    }catch{this.rootError='Selected trust certificates are invalid, unsupported or exceed limits.';this.roots=[];}
    try {
      if(!evidence)return;
      if([evidence.certificates,evidence.crls,evidence.ocsps].some(a=>a.length>32)||[...evidence.certificates,...evidence.crls,...evidence.ocsps].reduce((n,b)=>n+b.length,0)>4194304)throw Error('Evidence limit');
      this.evidenceError=evidence.error??'';
      this.certificates=evidence.certificates.map(certificate);
      this.crls=evidence.crls.map(bytes=>new CertificateRevocationList({schema:der(bytes)}));
      for(const bytes of evidence.ocsps){
        const schema=der(bytes);let response:BasicOCSPResponse;
        try {
          const wrapper=new OCSPResponse({schema});
          if(wrapper.responseStatus.valueBlock.valueDec!==0||wrapper.responseBytes?.responseType!=='1.3.6.1.5.5.7.48.1.1')continue;
          response=new BasicOCSPResponse({schema:der(new Uint8Array(wrapper.responseBytes.response.getValue()))});
        }catch{response=new BasicOCSPResponse({schema});}
        if(response.tbsResponseData.responses.length>64||(response.certs?.length??0)>16)throw Error('OCSP structure limit');
        this.ocsps.push(response);
      }
    }catch{this.evidenceError='Embedded revocation evidence is invalid, unsupported or exceeds limits.';this.crls=[];this.ocsps=[];}
  }
  async policy():Promise<SecurityCheck> {
    const fingerprints=await Promise.all(this.roots.map(async c=>sha256(new Uint8Array(c.toSchema().toBER(false)))));
    return {name:'Selected trust policy',status:this.roots.length?'PASS':'UNKNOWN',detail:this.rootError||(this.roots.length?`${this.roots.length} independently selected CA certificate(s); session only. SHA-256: ${fingerprints.join(', ')}. Embedded PDF certificates never become trust anchors.`:'No independently trusted CA certificates selected. Use Choose trusted roots in this report. No private key or passphrase is needed.')};
  }
  async selectedCertificates():Promise<SelectedTrustCertificate[]> {
    return Promise.all(this.roots.map(async cert=>{
      this.check();
      return {sha256:await sha256(new Uint8Array(cert.toSchema().toBER(false))),
        subject:cert.subject.typesAndValues.map(v=>v.value.valueBlock.value).join(', '),
        issuer:cert.issuer.typesAndValues.map(v=>v.value.valueBlock.value).join(', '),
        notBefore:cert.notBefore.value.toISOString(),notAfter:cert.notAfter.value.toISOString()};
    }));
  }
  async assess(leaf:Certificate,extra:Certificate[],at:Date,timestamp=false):Promise<TrustResult>{
    this.check();
    const missing={trust:unknown(this.rootError||'No independent trust anchors selected.'),revocation:unknown('Certificate trust must be established before revocation can pass.')};
    if(!this.roots.length)return missing;
    try {
      if(!Number.isFinite(at.getTime()))throw new UnsupportedSecurity('Invalid verification time');
      if(timestamp&&at.getTime()>Date.now()+300000)throw new UnsupportedSecurity('Timestamp is in the future relative to the verification clock.');
      if(!usage(leaf,0x80))return {trust:verdict('FAIL','Signing certificate lacks digitalSignature key usage.'),revocation:missing.revocation};
      if(timestamp&&(!eku(leaf,'1.3.6.1.5.5.7.3.8')||extension(leaf,'2.5.29.37')?.critical!==true||extension(leaf,'2.5.29.37')?.parsedValue?.keyPurposes?.length!==1))return {trust:verdict('FAIL','TSA certificate must have critical, exclusive timeStamping usage.'),revocation:missing.revocation};
      const pool=[...new Map([...extra,...this.certificates,...this.roots].map(c=>[id(c),c])).values()];
      if(pool.length>96)throw new UnsupportedSecurity('Certificate count limit');
      const path=[leaf],seen=new Set([id(leaf)]),anchors=new Set(this.roots.map(id));
      while(!anchors.has(id(path.at(-1)!))){
        this.check();if(path.length>=8)throw new UnsupportedSecurity('Certificate path exceeds eight certificates');
        const current=path.at(-1)!,candidates=pool.filter(c=>current.issuer.isEqual(c.subject)&&!seen.has(id(c)));
        if(candidates.length>4)throw new UnsupportedSecurity('Ambiguous certificate issuer limit');
        const issuers:Certificate[]=[];
        for(const issuer of candidates)if(signatures.has(current.signatureAlgorithm.algorithmId)&&await current.verify(issuer,this.engine))issuers.push(issuer);
        if(issuers.length!==1)throw new UnsupportedSecurity(issuers.length?'More than one certificate chain is possible; use the Java validator.':'This signer cannot be linked to a CA certificate you selected. Add the correct CA root to verify this signer.');
        path.push(issuers[0]!);seen.add(id(issuers[0]!));
      }
      if(path.length<2)throw new UnsupportedSecurity('Directly trusting a signing certificate is unsupported; choose its CA root.');
      if(path.some(c=>!strong(c)))throw new UnsupportedSecurity('Unsupported or weak certificate key; RSA 2048+ or P-256/P-384/P-521 is required.');
      if(path.some(unsupportedCritical))throw new UnsupportedSecurity('Unsupported critical certificate extension');
      // Supply one prebounded path so the library cannot explore a branching/cyclic graph.
      const next=new Map(path.slice(0,-1).map((c,i)=>[id(c),path[i+1]!]));
      const validator=new CertificateChainValidationEngine({trustedCerts:[path.at(-1)!],certs:[...path.slice(1,-1),leaf],checkDate:at,
        findIssuer:async cert=>next.has(id(cert))?[next.get(id(cert))!]:[]});
      const result=await validator.verify({},this.engine);this.check();
      if(!result.result)return {trust:unknown('Certificate path constraints or validity did not verify: '+result.resultMessage),revocation:missing.revocation};
      const states:Verdict[]=[];
      for(let i=0;i<path.length-1;i++){this.check();states.push(await this.revocation(path[i]!,path[i+1]!,at));}
      const revoked=states.find(s=>s.status==='FAIL'),unavailable=states.find(s=>s.status!=='PASS');
      return {trust:verdict('PASS',`The signer connects through ${path.length} certificates to a CA you selected. Certificate checks used ${at.toISOString()}.`),
        revocation:revoked??unavailable??verdict('PASS',`Fresh signed offline evidence covers all ${path.length-1} non-anchor certificate(s).`)};
    }catch(error){this.check();return {trust:unknown(error instanceof Error?error.message:'Unsupported certificate path'),revocation:missing.revocation};}
  }
  private async crl(cert:Certificate,issuer:Certificate,at:Date):Promise<Verdict>{
    let good=false;
    for(const crl of this.crls){
      this.check();
      if(!crl.issuer.isEqual(issuer.subject)||!fresh(crl.thisUpdate.value,crl.nextUpdate?.value,at)||!usage(issuer,0x02)||!signatures.has(crl.signatureAlgorithm.algorithmId))continue;
      if(crl.crlExtensions?.extensions.some(e=>['2.5.29.27','2.5.29.28'].includes(e.extnID)||e.critical&&!['2.5.29.20','2.5.29.35'].includes(e.extnID)))continue;
      if(!await crl.verify({issuerCertificate:issuer},this.engine))continue;
      const entry=crl.revokedCertificates?.find(e=>e.userCertificate.isEqual(cert.serialNumber));
      if(crl.revokedCertificates?.some(e=>e.crlEntryExtensions?.extensions.some(v=>v.critical||v.extnID==='2.5.29.29')))continue;
      if(entry&&entry.revocationDate.value<=at)return verdict('FAIL','A fresh signed CRL reports certificate revocation at or before the verification time.');
      good=true;
    }
    return good?verdict('PASS','Fresh issuer-signed complete CRL verified.'):unknown('No supported fresh signed CRL covers this certificate.');
  }
  private async revocation(cert:Certificate,issuer:Certificate,at:Date):Promise<Verdict>{
    const crl=await this.crl(cert,issuer,at);if(crl.status==='FAIL')return crl;
    let good=crl.status==='PASS';
    for(const ocsp of this.ocsps){
      this.check();
      const matches=[];
      for(const single of ocsp.tbsResponseData.responses){
        // getCertificateStatus validates the entire CertID (issuer name/key hashes and serial).
        const subset=new BasicOCSPResponse({tbsResponseData:ocsp.tbsResponseData});
        subset.tbsResponseData=Object.assign(Object.create(Object.getPrototypeOf(ocsp.tbsResponseData)),ocsp.tbsResponseData,{responses:[single]});
        if((await subset.getCertificateStatus(cert,issuer,this.engine)).isForCertificate)matches.push(single);
      }
      if(matches.length!==1)continue;
      const single=matches[0]!;
      if(!fresh(single.thisUpdate,single.nextUpdate,at)||ocsp.tbsResponseData.producedAt>at||ocsp.tbsResponseData.producedAt<single.thisUpdate||!signatures.has(ocsp.signatureAlgorithm.algorithmId)||ocsp.tbsResponseData.responseExtensions?.some(e=>e.critical)||single.singleExtensions?.some(e=>e.critical))continue;
      const possible=[issuer,...(ocsp.certs??[])];const responders:Certificate[]=[];
      for(const responder of new Map(possible.map(c=>[id(c),c])).values()){
        const responderId=ocsp.tbsResponseData.responderID;
        const matchesId=responderId instanceof RelativeDistinguishedNames?responder.subject.isEqual(responderId):responderId instanceof OctetString&&hex(new Uint8Array(await this.engine.digest({name:'SHA-1'},responder.subjectPublicKeyInfo.subjectPublicKey.valueBlock.valueHexView.slice().buffer)))===hex(responderId.valueBlock.valueHexView);
        if(matchesId)responders.push(responder);
      }
      if(responders.length!==1)continue;const responder=responders[0]!;
      if(id(responder)!==id(issuer)){
        if(!strong(responder)||unsupportedCritical(responder)||!responder.issuer.isEqual(issuer.subject)||!eku(responder,'1.3.6.1.5.5.7.3.9')||!usage(responder,0x80)||!signatures.has(responder.signatureAlgorithm.algorithmId)||!await responder.verify(issuer,this.engine)||responder.notBefore.value>ocsp.tbsResponseData.producedAt||responder.notAfter.value<ocsp.tbsResponseData.producedAt)continue;
        if(!extension(responder,'1.3.6.1.5.5.7.48.1.5')&&(await this.crl(responder,issuer,at)).status!=='PASS')continue;
      }
      if(!await this.engine.verifyWithPublicKey(ocsp.tbsResponseData.tbsView.slice().buffer,ocsp.signature,responder.subjectPublicKeyInfo,ocsp.signatureAlgorithm))continue;
      const status=await ocsp.getCertificateStatus(cert,issuer,this.engine);
      if(status.status===1){
        const value=single.certStatus instanceof Constructed?single.certStatus.valueBlock.value[0]:undefined;
        if(!(value instanceof GeneralizedTime))continue;
        if(value.toDate()>at)return unknown('OCSP reports a later revocation; historical archival validation requires the Java validator.');
        return verdict('FAIL','An authorized fresh signed OCSP response reports certificate revocation.');
      }
      if(status.status===0)good=true;
    }
    return good&&!this.evidenceError?verdict('PASS','Fresh signed CRL/OCSP evidence verified.'):unknown(this.evidenceError||'No supported fresh, authorized CRL/OCSP evidence covers this certificate. No network requests were made.');
  }
}
