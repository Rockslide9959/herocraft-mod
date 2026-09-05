// Generates the Normal Symbiote Host's armour textures: two 64x32 vanilla armor-layer sheets
// (layer_1 = helmet/chest/boots, layer_2 = leggings) plus 4 item-icon PNGs. "Living black
// diamond/iron armour" -- mostly black with dark-grey shading noise and sparse obsidian-purple
// highlight flecks, deliberately NOT the Spider-Man black suit (no eyes, no web, no logo). Filled
// uniformly across the whole canvas (not per-body-part UV regions) -- same pragmatic approach this
// project's early flat-color armor passes used, since a near-uniform dark material doesn't need
// per-cube precision to read correctly in game.
const fs = require('fs');
const zlib = require('zlib');
const OUT_ARMOR = 'src/main/resources/assets/herocraft/textures/models/armor';
const OUT_ITEM = 'src/main/resources/assets/herocraft/textures/item';

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

// Simple deterministic PRNG so re-running produces the same art.
let seed = 1337;
function rand() { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return seed / 0x7fffffff; }

function paintArmorLayer(w, h) {
	const data = Buffer.alloc(w * h * 4);
	for (let y = 0; y < h; y++) {
		for (let x = 0; x < w; x++) {
			const i = (y * w + x) * 4;
			const n = rand();
			// Base near-black with subtle dark-grey shading noise.
			let r = 8 + Math.floor(n * 14);
			let g = 8 + Math.floor(n * 14);
			let b = 10 + Math.floor(n * 16);
			// Sparse obsidian-purple highlight flecks.
			if (rand() < 0.035) {
				r += 18; g += 10; b += 30;
			}
			data[i] = Math.min(255, r);
			data[i + 1] = Math.min(255, g);
			data[i + 2] = Math.min(255, b);
			data[i + 3] = 255;
		}
	}
	return data;
}

fs.writeFileSync(`${OUT_ARMOR}/symbiote_host_layer_1.png`, encode(64, 32, paintArmorLayer(64, 32)));
fs.writeFileSync(`${OUT_ARMOR}/symbiote_host_layer_2.png`, encode(64, 32, paintArmorLayer(64, 32)));
console.log('wrote symbiote_host_layer_1/2.png to', OUT_ARMOR);

// Item icons: simple 16x16 flat-colour silhouette-free swatches (same near-black/obsidian palette),
// distinct enough from the fully-transparent Black Suit icon convention -- these ARE meant to show
// (Normal-host armour is craftable-adjacent gear conceptually, not a hidden second skin).
function paintIcon() {
	const data = Buffer.alloc(16 * 16 * 4);
	for (let y = 0; y < 16; y++) {
		for (let x = 0; x < 16; x++) {
			const i = (y * 16 + x) * 4;
			const edge = x === 0 || y === 0 || x === 15 || y === 15;
			const n = rand();
			if (edge) {
				data[i] = 0; data[i + 1] = 0; data[i + 2] = 0; data[i + 3] = 0;
				continue;
			}
			let r = 10 + Math.floor(n * 16);
			let g = 10 + Math.floor(n * 16);
			let b = 14 + Math.floor(n * 20);
			if (rand() < 0.06) {
				r += 20; g += 12; b += 34;
			}
			data[i] = Math.min(255, r);
			data[i + 1] = Math.min(255, g);
			data[i + 2] = Math.min(255, b);
			data[i + 3] = 255;
		}
	}
	return data;
}

for (const name of ['symbiote_host_helmet', 'symbiote_host_chestplate', 'symbiote_host_leggings', 'symbiote_host_boots']) {
	fs.writeFileSync(`${OUT_ITEM}/${name}.png`, encode(16, 16, paintIcon()));
}
console.log('wrote 4 symbiote_host_*.png icons to', OUT_ITEM);
