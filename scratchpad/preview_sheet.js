// Contact sheet: every regenerated component sprite, 6x, on a slate background, for eyeballing.
const fs=require('fs'),zlib=require('zlib'),path=require('path');
const T=(()=>{const t=new Uint32Array(256);for(let n=0;n<256;n++){let c=n;for(let k=0;k<8;k++)c=c&1?0xedb88320^(c>>>1):c>>>1;t[n]=c>>>0;}return t;})();
const crc=b=>{let c=0xffffffff;for(let i=0;i<b.length;i++)c=T[(c^b[i])&0xff]^(c>>>8);return (c^0xffffffff)>>>0;};
const ch=(t,d)=>{const l=Buffer.alloc(4);l.writeUInt32BE(d.length,0);const td=Buffer.concat([Buffer.from(t,'ascii'),d]);const cc=Buffer.alloc(4);cc.writeUInt32BE(crc(td),0);return Buffer.concat([l,td,cc]);};
function enc(w,h,data){const s=w*4,raw=Buffer.alloc((s+1)*h);for(let y=0;y<h;y++){raw[y*(s+1)]=0;data.copy(raw,y*(s+1)+1,y*s,y*s+s);}
const ih=Buffer.alloc(13);ih.writeUInt32BE(w,0);ih.writeUInt32BE(h,4);ih[8]=8;ih[9]=6;
return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),ch('IHDR',ih),ch('IDAT',zlib.deflateSync(raw,{level:9})),ch('IEND',Buffer.alloc(0))]);}
// minimal PNG decode for our own 16x16 RGBA non-interlaced files
function dec(file){const b=fs.readFileSync(file);let o=8,w=0,h=0,idat=[];
while(o<b.length){const len=b.readUInt32BE(o);const type=b.toString('ascii',o+4,o+8);const d=b.subarray(o+8,o+8+len);
if(type==='IHDR'){w=d.readUInt32BE(0);h=d.readUInt32BE(4);}else if(type==='IDAT')idat.push(d);o+=12+len;}
const raw=zlib.inflateSync(Buffer.concat(idat));const s=w*4;const out=Buffer.alloc(s*h);
for(let y=0;y<h;y++){const f=raw[y*(s+1)];const row=raw.subarray(y*(s+1)+1,y*(s+1)+1+s);
for(let x=0;x<s;x++){const a=x>=4?out[y*s+x-4]:0;const bb=y>0?out[(y-1)*s+x]:0;const cc=(x>=4&&y>0)?out[(y-1)*s+x-4]:0;let v=row[x];
if(f===1)v+=a;else if(f===2)v+=bb;else if(f===3)v+=(a+bb)>>1;else if(f===4){const p=a+bb-cc,pa=Math.abs(p-a),pb=Math.abs(p-bb),pc=Math.abs(p-cc);v+=(pa<=pb&&pa<=pc)?a:(pb<=pc?bb:cc);}
out[y*s+x]=v&255;}}
return {w,h,data:out};}
const names=['copper_wiring','metal_plating','basic_circuit','mechanical_parts','titanium_gold_alloy','titanium_gold_plate','servo_motor','micro_thruster','repulsor','flight_stabilizer','targeting_module','stark_circuit','suit_computer','advanced_arc_reactor','reactor_core','missile_module','modular_armor_controller','nanotech_matrix'];
const S=6,CELL=16*S+8,COLS=6,ROWS=Math.ceil(names.length/COLS);
const W=COLS*CELL,H=ROWS*CELL,sheet=Buffer.alloc(W*H*4);
for(let i=0;i<W*H;i++){sheet[i*4]=58;sheet[i*4+1]=60;sheet[i*4+2]=68;sheet[i*4+3]=255;}
names.forEach((n,i)=>{const img=dec(path.join('src/main/resources/assets/projecthero/textures/item',n+'.png'));
const ox=(i%COLS)*CELL+4,oy=Math.floor(i/COLS)*CELL+4;
for(let y=0;y<16;y++)for(let x=0;x<16;x++){const si=(y*16+x)*4;const a=img.data[si+3];if(!a)continue;
for(let dy=0;dy<S;dy++)for(let dx=0;dx<S;dx++){const di=(((oy+y*S+dy)*W)+(ox+x*S+dx))*4;
sheet[di]=img.data[si];sheet[di+1]=img.data[si+1];sheet[di+2]=img.data[si+2];sheet[di+3]=255;}}});
const out=process.env.TMPDIR||'scratchpad';
fs.writeFileSync(path.join('scratchpad','component_sheet.png'),enc(W,H,sheet));
console.log('scratchpad/component_sheet.png  '+W+'x'+H+'  order: '+names.join(', '));
