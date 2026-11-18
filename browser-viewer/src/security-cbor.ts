// Deliberately bounded CBOR subset for the Creator's COSE/JUMBF profile.
export function decodeCbor(bytes:Uint8Array):unknown {
  let offset=0,nodes=0;
  function read(depth=0):unknown {
    if(depth>16||++nodes>4096||offset>=bytes.length)throw Error('CBOR structure limit or truncation');
    const head=bytes[offset++]!,major=head>>>5,info=head&31;
    let size=info;
    if(info>=24){
      const count=2**(info-24);if(info>27||offset+count>bytes.length)throw Error('Unsupported CBOR length');
      size=0;for(let n=0;n<count;n++)size=size*256+bytes[offset++]!;
      if(!Number.isSafeInteger(size))throw Error('CBOR integer limit');
    }
    if(major===0)return size;if(major===1)return -1-size;
    if(major===2||major===3){if(size>bytes.length-offset)throw Error('Truncated CBOR string');const value=bytes.slice(offset,offset+=size);return major===2?value:new TextDecoder('utf-8',{fatal:true}).decode(value);}
    if(major===4){if(size>4096)throw Error('CBOR array limit');return Array.from({length:size},()=>read(depth+1));}
    if(major===5){
      if(size>128)throw Error('CBOR map limit');const result=new Map<unknown,unknown>();
      for(let n=0;n<size;n++){const key=read(depth+1);if((typeof key!=='string'&&typeof key!=='number')||result.has(key))throw Error('Invalid/duplicate CBOR key');result.set(key,read(depth+1));}return result;
    }
    if(major===6&&size===18&&depth===0)return read(depth+1);
    if(major===7&&info===22)return null;
    if(major===7&&info===20)return false;if(major===7&&info===21)return true;
    throw Error('Unsupported CBOR value');
  }
  const result=read();if(offset!==bytes.length)throw Error('Trailing CBOR data');return result;
}
export function cborMap(value:unknown):Map<unknown,unknown>{if(!(value instanceof Map))throw Error('Expected CBOR map');return value;}
export function cborBytes(value:unknown):Uint8Array{if(!(value instanceof Uint8Array))throw Error('Expected CBOR byte string');return value;}
export function encodeCbor(value:string|Uint8Array|(string|Uint8Array)[]):Uint8Array {
  const parts:Uint8Array[]=[];
  const header=(major:number,size:number)=>{if(size<24)parts.push(Uint8Array.of(major*32+size));else if(size<256)parts.push(Uint8Array.of(major*32+24,size));else if(size<65536)parts.push(Uint8Array.of(major*32+25,size>>>8,size&255));else throw Error('COSE input limit');};
  function add(item:typeof value){if(typeof item==='string'){const bytes=new TextEncoder().encode(item);header(3,bytes.length);parts.push(bytes);}else if(item instanceof Uint8Array){header(2,item.length);parts.push(item);}else{header(4,item.length);item.forEach(add);}}
  add(value);const output=new Uint8Array(parts.reduce((n,p)=>n+p.length,0));let offset=0;for(const part of parts){output.set(part,offset);offset+=part.length;}return output;
}
