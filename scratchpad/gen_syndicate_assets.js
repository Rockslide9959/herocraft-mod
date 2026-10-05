// v0.14.25: Syndicate Bust + Carnage item/block textures (16x16 pixel art). node scratchpad/gen_syndicate_assets.js
const P=require('./png_v01425.js');const path=require('path');
const ROOT=path.join(__dirname,'..','src','main','resources','assets','projecthero','textures');
function img(){return{d:Buffer.alloc(16*16*4)}}
function px(im,x,y,c){if(x<0||y<0||x>15||y>15)return;const o=(y*16+x)*4;im.d[o]=c>>16&255;im.d[o+1]=c>>8&255;im.d[o+2]=c&255;im.d[o+3]=255}
function rect(im,x0,y0,x1,y1,c){for(let y=y0;y<=y1;y++)for(let x=x0;x<=x1;x++)px(im,x,y,c)}
function save(im,rel){P.write(path.join(ROOT,rel),16,16,im.d);console.log('wrote',rel)}
// police scanner: handheld radio
{const im=img();rect(im,4,4,11,15,0x2B2E33);rect(im,5,5,10,14,0x3A3E45);rect(im,10,0,10,4,0x1A1C20);px(im,10,0,0x55595F);
rect(im,5,6,10,8,0xE0A030);rect(im,6,7,9,7,0xFFD070);px(im,5,4,0xE03030);px(im,6,4,0xE03030);px(im,8,4,0x3060E0);px(im,9,4,0x3060E0);
for(let y=10;y<=13;y+=1)for(let x=6;x<=9;x++)if((x+y)%2==0)px(im,x,y,0x1E2025);rect(im,4,15,11,15,0x1A1C20);rect(im,11,9,11,11,0x55595F);save(im,'item/police_scanner.png')}
// villain dossier: manila folder with a red stripe and a clip
{const im=img();rect(im,2,4,13,14,0xC8A060);rect(im,2,3,6,4,0xB08850);rect(im,3,5,12,13,0xD8B474);rect(im,3,8,12,9,0xA02020);
for(let x=4;x<=11;x+=2)px(im,x,8,0xE0E0E0);rect(im,4,11,10,11,0x8A6A3A);rect(im,4,12,8,12,0x8A6A3A);rect(im,11,2,11,6,0xB0B4BA);rect(im,12,2,12,2,0xB0B4BA);
rect(im,2,14,13,14,0x8A6A3A);save(im,'item/villain_dossier.png')}
// kingpin cane: black shaft bottom-left to top-right, silver collar, diamond knob
{const im=img();for(let i=0;i<11;i++){px(im,2+i,15-i,0x15151A);px(im,3+i,15-i,0x2A2A33)}
px(im,1,15,0xA0A4AA);rect(im,12,3,13,4,0xC8CCD2);px(im,13,2,0xE8ECF0);
px(im,14,1,0x9FE8F0);px(im,13,1,0x6FD0E0);px(im,14,0,0xDFFAFF);px(im,15,1,0x6FD0E0);px(im,14,2,0x4FB0C8);px(im,15,0,0xFFFFFF);save(im,'item/kingpin_cane.png')}
// stash crate: side, front (padlock), top
function crate(lock,top){const im=img();rect(im,0,0,15,15,0x4A3220);for(let y=1;y<15;y++)for(let x=1;x<15;x++)px(im,x,y,(y%5==0)?0x3A2618:((x*7+y*3)%11==0?0x5A3E28:0x55392A));
rect(im,0,0,15,0,0x6A6E74);rect(im,0,15,15,15,0x6A6E74);rect(im,0,0,0,15,0x6A6E74);rect(im,15,0,15,15,0x6A6E74);
if(top){for(let i=1;i<15;i++){px(im,i,i,0x6A6E74);px(im,15-i,i,0x6A6E74)}}
else{rect(im,0,7,15,8,0x5A5E64)}
if(lock){rect(im,6,6,9,10,0xC8A030);rect(im,7,4,8,5,0x9A9EA4);px(im,6,5,0x9A9EA4);px(im,9,5,0x9A9EA4);px(im,7,8,0x2A2010);px(im,7,9,0x2A2010)}
return im}
save(crate(false,false),'block/syndicate_stash_side.png');save(crate(true,false),'block/syndicate_stash_front.png');save(crate(false,true),'block/syndicate_stash_top.png');
// crimson biomass (Carnage drop): a red goo blob
{const im=img();const c=[0x5A0008,0x8A0010,0xC01020,0xE83040];for(let y=3;y<15;y++)for(let x=2;x<14;x++){const dx=(x-7.5)/6,dy=(y-9)/5.5;const r=dx*dx+dy*dy;if(r<1){px(im,x,y,c[r>0.75?0:r>0.45?1:2])}}
px(im,5,6,0xFF8A90);px(im,6,6,0xE83040);px(im,5,7,0xE83040);rect(im,8,12,9,13,0x3A0005);px(im,11,8,0x2A0004);px(im,4,11,0x2A0004);px(im,7,15,0x8A0010);px(im,10,15,0x8A0010);save(im,'item/crimson_biomass.png')}
