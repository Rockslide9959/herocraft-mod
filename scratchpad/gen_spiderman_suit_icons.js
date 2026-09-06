// 16x16 inventory icons for the craftable Spider-Man Suit (v0.6.18).
// Hand-drawn, like every other sprite in this mod (no image library / no Python here).
// Red suit, black web lines, white eye lenses on the mask, black spider on the chest.
const fs = require('fs');
const zlib = require('zlib');
const OUT = 'src/main/resources/assets/projecthero/textures/item';

// ---- PNG encoder (same as gen_spider_textures.js) ----
const CRC_TABLE = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
const crc32 = (buf) => { let c = 0xffffffff; for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0; };
const chunk = (type, data) => { const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0); const td = Buffer.concat([Buffer.from(type, 'ascii'), data]); const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td), 0); return Buffer.concat([len, td, crc]); };
function encode(w, h, data) {
	const stride = w * 4;
	const raw = Buffer.alloc((stride + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (stride + 1)] = 0; data.copy(raw, y * (stride + 1) + 1, y * stride, y * stride + stride); }
	const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
	const ihdr = Buffer.alloc(13);
	ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
class C {
	constructor(w = 16, h = 16) { this.w = w; this.h = h; this.data = Buffer.alloc(w * h * 4); }
	set(x, y, c) { x = Math.round(x); y = Math.round(y); if (!c || x < 0 || y < 0 || x >= this.w || y >= this.h) return; const i = (y * this.w + x) * 4; this.data[i] = c[0]; this.data[i + 1] = c[1]; this.data[i + 2] = c[2]; this.data[i + 3] = c.length > 3 ? c[3] : 255; }
	rect(x, y, w, h, c) { for (let dy = 0; dy < h; dy++) for (let dx = 0; dx < w; dx++) this.set(x + dx, y + dy, c); }
	line(x0, y0, x1, y1, c) { const s = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)); for (let i = 0; i <= s; i++) this.set(x0 + (x1 - x0) * i / s, y0 + (y1 - y0) * i / s, c); }
}

const RED = [180, 26, 32];
const RED_D = [130, 16, 22];
const RED_L = [214, 54, 58];
const WEB = [30, 18, 22];
const EYE = [232, 240, 246];
const EYE_D = [150, 170, 186];

function webShade(c, x, y, w, h) {
	// faint diagonal web lattice over a filled region
	for (let yy = 0; yy < h; yy++) for (let xx = 0; xx < w; xx++) {
		if (((xx + yy) % 4) === 0) c.set(x + xx, y + yy, RED_D);
		if (((xx - yy + 40) % 5) === 0) c.set(x + xx, y + yy, RED_L);
	}
}

// ---- helmet: the mask + white eye lenses ----
{
	const c = new C();
	c.rect(3, 2, 10, 11, RED);
	c.rect(4, 1, 8, 1, RED);
	webShade(c, 3, 2, 10, 11);
	// outline
	c.rect(3, 2, 10, 1, RED_D); c.rect(3, 12, 10, 1, RED_D);
	c.rect(3, 2, 1, 11, RED_D); c.rect(12, 2, 1, 11, RED_D);
	// eyes: teardrop lenses angled up toward the centre
	c.rect(4, 6, 3, 3, EYE); c.set(6, 5, EYE); c.set(4, 9, EYE_D); c.set(6, 8, EYE_D);
	c.rect(9, 6, 3, 3, EYE); c.set(9, 5, EYE); c.set(11, 9, EYE_D); c.set(9, 8, EYE_D);
	c.line(7, 6, 8, 6, WEB); // brow between the eyes
	fs.writeFileSync(`${OUT}/spider_man_suit_helmet.png`, encode(16, 16, c.data));
}

// ---- chestplate: torso + black spider emblem ----
{
	const c = new C();
	c.rect(2, 2, 12, 10, RED);
	c.rect(3, 12, 10, 2, RED);
	webShade(c, 2, 2, 12, 12);
	c.rect(2, 2, 12, 1, RED_D); c.rect(2, 2, 1, 12, RED_D); c.rect(13, 2, 1, 12, RED_D);
	// shoulders
	c.rect(1, 3, 2, 4, RED_D); c.rect(13, 3, 2, 4, RED_D);
	// spider: body + 4 legs a side
	c.rect(7, 6, 2, 3, WEB); c.set(7, 5, WEB); c.set(8, 5, WEB);
	c.line(6, 6, 4, 4, WEB); c.line(6, 7, 3, 7, WEB); c.line(6, 8, 4, 10, WEB); c.line(6, 9, 5, 11, WEB);
	c.line(9, 6, 11, 4, WEB); c.line(9, 7, 12, 7, WEB); c.line(9, 8, 11, 10, WEB); c.line(9, 9, 10, 11, WEB);
	fs.writeFileSync(`${OUT}/spider_man_suit_chestplate.png`, encode(16, 16, c.data));
}

// ---- leggings ----
{
	const c = new C();
	c.rect(3, 1, 10, 5, RED);
	c.rect(3, 6, 4, 9, RED);
	c.rect(9, 6, 4, 9, RED);
	webShade(c, 3, 1, 10, 14);
	c.rect(3, 1, 10, 1, RED_D);
	c.rect(3, 1, 1, 14, RED_D); c.rect(12, 1, 1, 14, RED_D);
	c.rect(7, 6, 2, 9, RED_D); // gap between the legs
	fs.writeFileSync(`${OUT}/spider_man_suit_leggings.png`, encode(16, 16, c.data));
}

// ---- boots ----
{
	const c = new C();
	c.rect(3, 3, 4, 11, RED);
	c.rect(9, 3, 4, 11, RED);
	c.rect(2, 12, 6, 3, RED); // toe caps
	c.rect(8, 12, 6, 3, RED);
	webShade(c, 2, 3, 12, 12);
	c.rect(3, 3, 4, 1, RED_D); c.rect(9, 3, 4, 1, RED_D);
	c.rect(2, 14, 6, 1, WEB); c.rect(8, 14, 6, 1, WEB); // soles
	fs.writeFileSync(`${OUT}/spider_man_suit_boots.png`, encode(16, 16, c.data));
}

console.log('wrote 4 Spider-Man suit icons to', OUT);
