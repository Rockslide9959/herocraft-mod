// 16x16 bullet-hole decal: transparent bg, dark irregular impact crater + a few short radial cracks.
// Hand-rolled PNG (no image lib), same encoder as the other gen_*.js scripts.
const fs = require('fs'), zlib = require('zlib');
const OUT = 'src/main/resources/assets/projecthero/textures/misc/bullet_hole.png';
const CRC = (() => { const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = b => { let c = 0xffffffff; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (ty, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length, 0);
  const td = Buffer.concat([Buffer.from(ty, 'ascii'), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc32(td), 0); return Buffer.concat([l, td, c]); };
function encode(w, h, data) {
  const st = w * 4; const raw = Buffer.alloc((st + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (st + 1)] = 0; data.copy(raw, y * (st + 1) + 1, y * st, y * st + st); }
  const sig = Buffer.from([0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a]);
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w,0); ihdr.writeUInt32BE(h,4); ihdr[8]=8; ihdr[9]=6;
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw,{level:9})), chunk('IEND', Buffer.alloc(0))]);
}
const W = 16, H = 16, d = Buffer.alloc(W*H*4);
const set = (x,y,c) => { if (x<0||y<0||x>=W||y>=H) return; const i=(y*W+x)*4; d[i]=c[0];d[i+1]=c[1];d[i+2]=c[2];d[i+3]=c[3]; };
const cx = 7.5, cy = 7.5;
for (let y=0;y<H;y++) for (let x=0;x<W;x++) {
  const dx = x-cx, dy = y-cy;
  const r = Math.sqrt(dx*dx+dy*dy);
  // wobble the crater edge a little so it isn't a perfect circle
  const ang = Math.atan2(dy,dx);
  const wob = Math.sin(ang*5)*0.7 + Math.sin(ang*3+1)*0.5;
  if (r < 2.4 + wob*0.3) set(x,y,[8,8,10,255]);            // black core
  else if (r < 4.1 + wob) set(x,y,[26,24,26, 200]);        // dark ring
  else if (r < 5.4 + wob) set(x,y,[40,38,40, 90]);         // soft scorch falloff
}
// four short radial cracks
const cracks = [[1,0.2],[-1,-0.15],[0.25,1],[-0.2,-1]];
for (const [ux,uy] of cracks) {
  const n = Math.sqrt(ux*ux+uy*uy);
  for (let t=2; t<7; t++) {
    const x = Math.round(cx + ux/n*t), y = Math.round(cy + uy/n*t);
    set(x,y,[18,16,18, Math.max(40, 200 - t*28)]);
  }
}
fs.writeFileSync(OUT, encode(W,H,d));
console.log('wrote', OUT);
