// v0.17.2 (user: "make ripjaws tail textures look like the green and black tail on his skin model please"): ripjaws_tail.png in the
// colours of the tail his skin paints on its legs -- the skin's near-black with its lime bands, sampled from the skin. Same layout
// as before (u 0-8 dorsal, 8-16 flank, 16-24 belly, 24-32 x 0-8 end cap, 32-64 the fluke's two faces); the old sheet's alpha stays.
'use strict';
const path=require('path');const PNG=require('./pnglib.js');
const D=path.join(__dirname,'..','src','main','resources','assets','projecthero','textures','entity','omnitrix');
const skin=PNG.read(path.join(D,'ripjaws.png'));const sheet=PNG.read(path.join(D,'ripjaws_tail.png'));
const get=(x,y)=>{const i=(y*64+x)*4;return [skin.px[i],skin.px[i+1],skin.px[i+2],skin.px[i+3]];};
let lime=null,best=-1;const darks=[];
for(let y=20;y<32;y++)for(let x=0;x<16;x++){const c=get(x,y);if(c[3]<200)continue;const s=c[1]-c[2];if(s>best){best=s;lime=c;}if(c[0]+c[1]+c[2]<120)darks.push(c);}
const avg=darks.reduce((a,c)=>[a[0]+c[0],a[1]+c[1],a[2]+c[2]],[0,0,0]).map(v=>Math.round(v/darks.length));
const black=avg,blue=[Math.min(255,avg[0]+14),Math.min(255,avg[1]+18),Math.min(255,avg[2]+30)];
console.log('lime',lime.slice(0,3),'black',black,'blue',blue);
const mix=(a,b,t)=>a.map((v,i)=>Math.round(v+(b[i]-v)*t));
const PERIOD=6,BAND=2;
const band=y=>((y+4)%PERIOD)<BAND;
const put=(x,y,c)=>{const i=(y*64+x)*4;sheet.px[i]=c[0];sheet.px[i+1]=c[1];sheet.px[i+2]=c[2];};
const hash=(x,y)=>((x*73+y*151)^(x*y*31))%7;
for(let y=0;y<32;y++)for(let x=0;x<24;x++){
  const f=x>>3,col=x&7;
  // the faces meet at the edges: a little darker there, so the box reads round
  const edge=(col===0||col===7)?0.82:1;
  const shade=(f===2?0.8:1)*edge;
  const base=hash(x,y)===0?blue:black;
  put(x,y,(band(y)?lime:base).slice(0,3).map(v=>Math.round(v*shade)));
}
for(let y=0;y<8;y++)for(let x=24;x<32;x++)put(x,y,(hash(x,y)===0?blue:black).slice(0,3));
// the fluke: the same black, its edge and spine in lime like the bands; a dim copy underneath
const alpha=(x,y)=>x>=0&&y>=0&&x<64&&y<32?sheet.px[(y*64+x)*4+3]:0;
const src=Buffer.from(sheet.px);const a0=(x,y)=>x>=32&&x<64&&y>=0&&y<32?src[(y*64+x)*4+3]:0;
for(let y=0;y<32;y++)for(let x=32;x<64;x++){
  if(a0(x,y)<128)continue;
  const top=y<16,y0=top?0:16,y1=top?16:32;
  const inR=(xx,yy)=>xx>=32&&xx<64&&yy>=y0&&yy<y1&&a0(xx,yy)>=128;
  const rim=!inR(x-1,y)||!inR(x+1,y)||!inR(x,y-1)||!inR(x,y+1);
  const spine=Math.abs(x-47.5)<1;
  let c=(hash(x,y)===0?blue:black).slice(0,3);
  if(rim||spine)c=lime.slice(0,3);
  put(x,y,top?c:mix(c,black.slice(0,3),0.45));
}
PNG.write(path.join(D,'ripjaws_tail.png'),64,32,sheet.px);
