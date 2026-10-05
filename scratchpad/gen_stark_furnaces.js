// v0.14.26: Stark Furnace / Smelter / Smoker block textures. node scratchpad/gen_stark_furnaces.js
const P=require('./png_v01425.js');const path=require('path');
const T=path.join(__dirname,'..','src','main','resources','assets','projecthero','textures','block');
const RED=0x9E1B1B,RED_D=0x6E1010,RED_L=0xC22A2A,GOLD=0xD4A537,GOLD_D=0x9C7420,STEEL=0x5A5F66,STEEL_D=0x3A3E44,STEEL_L=0x8A9098,BLACK=0x1A1C20;
function img(){return Buffer.alloc(16*16*4)}
function px(d,x,y,c){if(x<0||y<0||x>15||y>15)return;const o=(y*16+x)*4;d[o]=c>>16&255;d[o+1]=c>>8&255;d[o+2]=c&255;d[o+3]=255}
function rect(d,x0,y0,x1,y1,c){for(let y=y0;y<=y1;y++)for(let x=x0;x<=x1;x++)px(d,x,y,c)}
function frame(d){rect(d,0,0,15,15,RED);for(let y=1;y<15;y++)for(let x=1;x<15;x++)if((x*5+y*3)%13==0)px(d,x,y,RED_L);
rect(d,0,0,15,0,GOLD);rect(d,0,15,15,15,GOLD_D);rect(d,0,0,0,15,GOLD);rect(d,15,0,15,15,GOLD_D);for(const[x,y]of[[1,1],[14,1],[1,14],[14,14]])px(d,x,y,STEEL_L)}
function reactor(d,cx,cy,on){const ring=on?0xBFF6FF:STEEL_L,core=on?0x7FE8FF:0x2C4A55;for(let y=-2;y<=2;y++)for(let x=-2;x<=2;x++){const r=x*x+y*y;if(r<=1)px(d,cx+x,cy+y,on&&r==0?0xFFFFFF:core);else if(r<=5)px(d,cx+x,cy+y,ring)}}
function save(d,n){P.write(path.join(T,n+'.png'),16,16,d);console.log(n)}
for(const kind of['furnace','smelter','smoker']){
 // side: red armour panel, gold trim, a vent stripe
 let d=img();frame(d);rect(d,3,7,12,8,STEEL_D);for(let x=3;x<=12;x+=2)px(d,x,7,BLACK);save(d,'stark_'+kind+'_side');
 // top: steel plate, gold ring, kind-specific grille
 d=img();rect(d,0,0,15,15,STEEL);rect(d,0,0,15,0,GOLD);rect(d,0,15,15,15,GOLD_D);rect(d,0,0,0,15,GOLD);rect(d,15,0,15,15,GOLD_D);
 for(let y=3;y<=12;y++)for(let x=3;x<=12;x++){const r=(x-7.5)**2+(y-7.5)**2;if(r<=22&&r>=13)px(d,x,y,GOLD)}
 if(kind=='smoker'){for(let y=5;y<=10;y+=2)rect(d,5,y,10,y,BLACK)}else if(kind=='smelter'){for(let x=5;x<=10;x+=2)rect(d,x,5,x,10,BLACK)}else{rect(d,6,6,9,9,STEEL_D);rect(d,7,7,8,8,BLACK)}
 save(d,'stark_'+kind+'_top');
 // front off/on: steel hatch with the firebox window, arc reactor above
 for(const on of[false,true]){d=img();frame(d);rect(d,2,6,13,14,STEEL_D);rect(d,3,7,12,13,STEEL);
  const fire=on?[0xFFE070,0xFF9A20,0xE04010]:[BLACK,0x23262B,0x2E3238];
  rect(d,4,9,11,12,fire[2]);rect(d,5,10,10,12,fire[1]);rect(d,6,11,9,12,fire[0]);
  if(kind=='smelter'){for(let x=4;x<=11;x+=2)px(d,x,8,on?0xFFB040:BLACK)}
  if(kind=='smoker'){for(let x=4;x<=11;x++)if(x%2==0)rect(d,x,9,x,12,STEEL_D)}
  rect(d,3,13,12,13,GOLD_D);reactor(d,7.5|0,3,on);reactor(d,8,3,on);save(d,'stark_'+kind+'_front'+(on?'_on':''))}
}
