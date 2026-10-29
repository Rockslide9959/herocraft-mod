const fs=require('fs'),zlib=require('zlib'),path=require('path');
const L=require(path.join(__dirname,'../viperion/lib_concept_models.js'));
function dec(f){const b=fs.readFileSync(f);let i=8,bd,w,h,ct,pal,trns,idat=[];while(i<b.length){const len=b.readUInt32BE(i),t=b.toString('ascii',i+4,i+8),d=b.slice(i+8,i+8+len);if(t=='IHDR'){w=d.readUInt32BE(0);h=d.readUInt32BE(4);bd=d[8];ct=d[9];}if(t=='PLTE')pal=d;if(t=='tRNS')trns=d;if(t=='IDAT')idat.push(d);i+=12+len;}
const raw=zlib.inflateSync(Buffer.concat(idat));const bpp=ct==3?1:ct==6?4:ct==4?2:ct==0?1:3;const rowB=ct==3?Math.ceil(w*bd/8):w*bpp;const out=Buffer.alloc(w*h*4);let prev=Buffer.alloc(rowB);let p=0;
for(let y=0;y<h;y++){const ft=raw[p++];const line=Buffer.from(raw.slice(p,p+rowB));p+=rowB;for(let x=0;x<line.length;x++){const a=x>=bpp?line[x-bpp]:0,u=prev[x],c=x>=bpp?prev[x-bpp]:0;let v=line[x];if(ft==1)v+=a;else if(ft==2)v+=u;else if(ft==3)v+=(a+u)>>1;else if(ft==4){const pp=a+u-c,pa=Math.abs(pp-a),pb=Math.abs(pp-u),pc=Math.abs(pp-c);v+=(pa<=pb&&pa<=pc)?a:(pb<=pc?u:c);}line[x]=v&255;}prev=line;
for(let x=0;x<w;x++){const o=(y*w+x)*4;if(ct==3){const k=bd==8?line[x]:(line[(x*bd)>>3]>>(8-bd-((x*bd)&7)))&((1<<bd)-1);out[o]=pal[k*3];out[o+1]=pal[k*3+1];out[o+2]=pal[k*3+2];out[o+3]=trns&&k<trns.length?trns[k]:255;}else if(ct==6){for(let c=0;c<4;c++)out[o+c]=line[x*4+c];}else if(ct==2){for(let c=0;c<3;c++)out[o+c]=line[x*3+c];out[o+3]=255;}}}
return {w,h,px:out};}
module.exports={dec};
if(require.main===module){const files=process.argv.slice(3);const S=8;const imgs=files.map(dec);const W=imgs.reduce((s,i)=>s+i.w*S+16,0),H=Math.max(...imgs.map(i=>i.h*S));
const px=Buffer.alloc(W*H*4);for(let i=0;i<px.length;i+=4){const c=((i/4%W>>3)+((i/4/W|0)>>3))&1?60:90;px[i]=px[i+1]=px[i+2]=c;px[i+3]=255;}
let ox=0;for(const im of imgs){for(let y=0;y<im.h*S;y++)for(let x=0;x<im.w*S;x++){const s=((y/S|0)*im.w+(x/S|0))*4;if(im.px[s+3]<10)continue;const d=(y*W+ox+x)*4;px[d]=im.px[s];px[d+1]=im.px[s+1];px[d+2]=im.px[s+2];}ox+=im.w*S+16;}
fs.writeFileSync(process.argv[2],L.encodePng(W,H,px));}
