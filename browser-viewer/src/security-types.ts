export type SecurityState='PASS'|'FAIL'|'UNKNOWN'|'NOT_APPLICABLE';
export type SecurityCheck={name:string;status:SecurityState;detail:string};
export type PdfSignatureFacts={contents:Uint8Array;byteRange:number[];type:string;subFilter:string};
export type SecurityEvidence={certificates:Uint8Array[];crls:Uint8Array[];ocsps:Uint8Array[];error?:string};
export type SecurityFacts={signatures:PdfSignatureFacts[];encrypted:boolean;evidence?:SecurityEvidence};
export type SecurityInput={blob:Blob;facts:SecurityFacts;credentials?:Uint8Array;policy?:Uint8Array;discoveryError?:string;trustRoots?:Uint8Array[]};
export type SecurityReport={schemaVersion:1;status:SecurityState;checks:SecurityCheck[];checkedAt:string;fileSize:number;elapsedMilliseconds?:number;filename?:string;fileSha256?:string;scope:string;certificates:{sha256:string;subject:string;notBefore:string;notAfter:string}[]};
