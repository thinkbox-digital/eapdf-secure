export type SecurityState='PASS'|'FAIL'|'UNKNOWN'|'NOT_APPLICABLE';
export type SecurityCategory='C2PA'|'PAdES'|'C2PA + PAdES'|'CMS'|'Other checks';
export type SecurityCheck={name:string;status:SecurityState;detail:string;category?:SecurityCategory};
export type PdfSignatureFacts={contents:Uint8Array;byteRange:number[];type:string;subFilter:string};
export type SecurityEvidence={certificates:Uint8Array[];crls:Uint8Array[];ocsps:Uint8Array[];error?:string};
export type SecurityFacts={signatures:PdfSignatureFacts[];encrypted:boolean;evidence?:SecurityEvidence};
export type SecurityInput={blob:Blob;facts:SecurityFacts;credentials?:Uint8Array;policy?:Uint8Array;discoveryError?:string;trustRoots?:Uint8Array[]};
export type SelectedTrustCertificate={sha256:string;subject:string;issuer:string;notBefore:string;notAfter:string};
export type SecurityReport={schemaVersion:1;status:SecurityState;checks:SecurityCheck[];checkedAt:string;fileSize:number;elapsedMilliseconds?:number;filename?:string;fileSha256?:string;scope:string;certificates:{sha256:string;subject:string;notBefore:string;notAfter:string}[];selectedCertificates?:SelectedTrustCertificate[]};

/** Bounded facts extracted independently from each saved creator revision. */
export type CreatorArchiveRevision={end:number;startXRef:number;previousXRef:number;protectedSha256:string;signatures:PdfSignatureFacts[];signatureRefs:string[];fieldNames:string[];manifest:Uint8Array;policy:Uint8Array;evidence:SecurityEvidence};
export type CreatorArchiveFacts={schemaVersion:1;status:'COMPLETE'|'INVALID'|'UNAVAILABLE';detail?:string;revisions?:CreatorArchiveRevision[]};
export type CreatorArchiveReader=(ends:number[])=>Promise<CreatorArchiveFacts>;
