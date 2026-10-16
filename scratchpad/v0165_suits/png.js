const fs=require('fs'),zlib=require('zlib');
exports.read=function(inp){const b=fs.readFileSync(inp);let p=8,W,H,ct,bd,plte,idat=[],trns;
while(p<b.length){const len=b.readUInt32BE(p),t=b.toString('ascii',p+4,p+8),d=b.slice(p+8,p+8+len);
if(t==='IHDR'){W=d.readUInt32BE(0);H=d.readUInt32BE(4);bd=d[8];ct=d[9];}
if(t==='PLTE')plte=d;if(t==='tRNS')trns=d;if(t==='IDAT')idat.push(d);p+=12+len;}
const raw=zlib.inflateSync(Buffer.concat(idat));
const ch={0:1,2:3,3:1,4:2,6:4}[ct];const bpp=Math.max(1,ch*bd/8);const stride=Math.ceil(W*ch*bd/8);
const px=Buffer.alloc(W*H*4);let prev=Buffer.alloc(stride);
for(let y=0;y<H;y++){const f=raw[y*(stride+1)];const line=Buffer.from(raw.slice(y*(stride+1)+1,(y+1)*(stride+1)));
for(let i=0;i<stride;i++){const a=i>=bpp?line[i-bpp]:0,up=prev[i],c=i>=bpp?prev[i-bpp]:0;let v=line[i];
if(f===1)v+=a;else if(f===2)v+=up;else if(f===3)v+=(a+up)>>1;else if(f===4){const pp=a+up-c,pa=Math.abs(pp-a),pb=Math.abs(pp-up),pc=Math.abs(pp-c);v+=(pa<=pb&&pa<=pc)?a:(pb<=pc?up:c);}line[i]=v&255;}
for(let x=0;x<W;x++){let r,g,bl,al=255;
if(ct===3){let idx;if(bd===8)idx=line[x];else{const per=8/bd;idx=(line[Math.floor(x/per)]>>(8-bd*(x%per+1)))&((1<<bd)-1);}r=plte[idx*3];g=plte[idx*3+1];bl=plte[idx*3+2];if(trns&&idx<trns.length)al=trns[idx];}
else if(ct===6){r=line[x*4];g=line[x*4+1];bl=line[x*4+2];al=line[x*4+3];}else if(ct===2){r=line[x*3];g=line[x*3+1];bl=line[x*3+2];}
else if(ct===4){r=g=bl=line[x*2];al=line[x*2+1];}else{r=g=bl=line[x];}
px.set([r,g,bl,al],(y*W+x)*4);}prev=line;}
return {W,H,px};};
const crcT=[];for(let n=0;n<256;n++){let c=n;for(let k=0;k<8;k++)c=c&1?0xedb88320^(c>>>1):c>>>1;crcT[n]=c>>>0;}
const crc=bf=>{let c=0xffffffff;for(const x of bf)c=crcT[(c^x)&255]^(c>>>8);return (c^0xffffffff)>>>0;};
const chunk=(t,d)=>{const l=Buffer.alloc(4);l.writeUInt32BE(d.length);const td=Buffer.concat([Buffer.from(t),d]);const c=Buffer.alloc(4);c.writeUInt32BE(crc(td));return Buffer.concat([l,td,c]);};
exports.write=function(out,W,H,px){const o=Buffer.alloc(H*(W*4+1));for(let y=0;y<H;y++){o[y*(W*4+1)]=0;px.copy(o,y*(W*4+1)+1,y*W*4,(y+1)*W*4);}
const ih=Buffer.alloc(13);ih.writeUInt32BE(W,0);ih.writeUInt32BE(H,4);ih[8]=8;ih[9]=6;
fs.writeFileSync(out,Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',ih),chunk('IDAT',zlib.deflateSync(o)),chunk('IEND',Buffer.alloc(0))]));};
