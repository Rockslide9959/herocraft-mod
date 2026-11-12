// v0.17.3 Beast Boy: draws the Sakutia Serum item icon (16x16) -- a stoppered vial of glowing green liquid with a paw mark.
// node scratchpad/beastboy/build_sakutia_serum.js  ->  src/main/resources/assets/projecthero/textures/item/sakutia_serum.png
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const W = 16, H = 16;
const px = new Uint8Array(W * H * 4);
function set(x, y, rgba) {
	if (x < 0 || y < 0 || x >= W || y >= H) return;
	const i = (y * W + x) * 4;
	px[i] = (rgba >> 24) & 255; px[i + 1] = (rgba >> 16) & 255; px[i + 2] = (rgba >> 8) & 255; px[i + 3] = rgba & 255;
}
// palette
const CORK = 0x8a5a32ff, CORK_D = 0x5e3a1eff, GLASS = 0xcfe8e0ff, GLASS_D = 0x7fa79aff, EDGE = 0x2b3f38ff;
const LIQ = 0x4fd03aff, LIQ_D = 0x2f8a22ff, LIQ_L = 0xa8ff7cff, PAW = 0x1f5a16ff;
// the cork
for (let x = 6; x <= 9; x++) { set(x, 1, CORK); set(x, 2, CORK); }
set(6, 2, CORK_D); set(9, 1, CORK_D); set(9, 2, CORK_D);
// the neck
for (let y = 3; y <= 5; y++) { set(5, y, EDGE); set(10, y, EDGE); for (let x = 6; x <= 9; x++) set(x, y, GLASS); }
set(6, 4, GLASS_D);
// the body: a rounded flask
const rows = [[4, 11], [3, 12], [2, 13], [2, 13], [2, 13], [2, 13], [2, 13], [3, 12], [4, 11]];
rows.forEach(([a, b], k) => {
	const y = 6 + k;
	set(a, y, EDGE); set(b, y, EDGE);
	for (let x = a + 1; x < b; x++) set(x, y, k < 2 ? GLASS : (x < a + 3 ? LIQ_D : LIQ));
});
for (let x = 5; x <= 10; x++) set(x, 15, EDGE);
// the liquid's surface shine and a highlight on the glass
for (let x = 4; x <= 11; x++) set(x, 8, LIQ_L);
set(4, 9, GLASS); set(4, 10, GLASS);
// a paw print in the liquid
set(8, 11, PAW); set(9, 11, PAW); set(8, 12, PAW); set(9, 12, PAW);
set(7, 10, PAW); set(10, 10, PAW); set(8, 9, PAW); set(9, 9, PAW);

// PNG writer
function crc32(buf) {
	let c, crc = 0xffffffff;
	for (let n = 0; n < buf.length; n++) {
		c = (crc ^ buf[n]) & 0xff;
		for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
		crc = (crc >>> 8) ^ c;
	}
	return (crc ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
	const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
	const td = Buffer.concat([Buffer.from(type), data]);
	const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
	return Buffer.concat([len, td, crc]);
}
const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
const raw = Buffer.alloc((W * 4 + 1) * H);
for (let y = 0; y < H; y++) {
	raw[y * (W * 4 + 1)] = 0;
	Buffer.from(px.buffer, y * W * 4, W * 4).copy(raw, y * (W * 4 + 1) + 1);
}
const png = Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
const out = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'textures', 'item', 'sakutia_serum.png');
fs.writeFileSync(out, png);
console.log('wrote ' + out);
