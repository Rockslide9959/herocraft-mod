const PNG=require('../pnglib.js');const [,,inp,out,sc]=process.argv;const r=PNG.read(inp);const s=+(sc||10);
const W=r.W,H=r.H,px=r.px||r.data;const o=Buffer.alloc(W*s*H*s*4);
for(let y=0;y<H*s;y++)for(let x=0;x<W*s;x++){const i=((y/s|0)*W+(x/s|0))*4;const a=px[i+3]/255;const chk=(((x/s|0)+(y/s|0))%2)?200:150;const j=(y*W*s+x)*4;
for(let k=0;k<3;k++)o[j+k]=Math.round(px[i+k]*a+chk*(1-a));o[j+3]=255;
if(x%(8*s)==0||y%(8*s)==0){o[j]=255;o[j+1]=0;o[j+2]=255;}}
PNG.write(out,W*s,H*s,o);
