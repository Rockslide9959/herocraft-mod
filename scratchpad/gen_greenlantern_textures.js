// Placeholder textures for Green Lantern: item icons, block textures, and a 64x64 armour skin
// (black base / green torso-boots / white gloves, roughly banded across the vanilla skin UV regions --
// a first-pass placeholder, not hand-painted per-region art; see docs/GREENLANTERN_REFERENCE.md).
const fs = require('fs');
const zlib = require('zlib');

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

function solidIcon(w, h, r, g, b, a, borderR, borderG, borderB) {
	const data = Buffer.alloc(w * h * 4);
	for (let y = 0; y < h; y++) {
		for (let x = 0; x < w; x++) {
			const i = (y * w + x) * 4;
			const edge = x === 0 || y === 0 || x === w - 1 || y === h - 1;
			data[i] = edge ? borderR : r;
			data[i + 1] = edge ? borderG : g;
			data[i + 2] = edge ? borderB : b;
			data[i + 3] = a;
		}
	}
	return data;
}

function write(path, w, h, data) {
	fs.writeFileSync(path, encode(w, h, data));
	console.log('wrote', path);
}

const ITEM_DIR = 'src/main/resources/assets/projecthero/textures/item';
const BLOCK_DIR = 'src/main/resources/assets/projecthero/textures/block';
const ARMOR_DIR = 'src/main/resources/assets/projecthero/textures/armor';

// Power Ring: bright green band on a darker green backing.
write(`${ITEM_DIR}/power_ring.png`, 16, 16, (() => {
	const data = Buffer.alloc(16 * 16 * 4);
	for (let y = 0; y < 16; y++) {
		for (let x = 0; x < 16; x++) {
			const i = (y * 16 + x) * 4;
			const cx = x - 7.5, cy = y - 7.5;
			const dist = Math.sqrt(cx * cx + cy * cy);
			const onRing = dist > 4.5 && dist < 7;
			if (onRing) {
				data[i] = 0x30; data[i + 1] = 0xF0; data[i + 2] = 0x60; data[i + 3] = 0xFF;
			} else if (dist <= 4.5) {
				data[i] = 0xFF; data[i + 1] = 0xFF; data[i + 2] = 0xFF; data[i + 3] = dist < 2.5 ? 0xFF : 0x00;
			} else {
				data[i] = 0; data[i + 1] = 0; data[i + 2] = 0; data[i + 3] = 0;
			}
		}
	}
	return data;
})());

// Lantern Core: a bright green gem on transparent background.
write(`${ITEM_DIR}/lantern_core.png`, 16, 16, (() => {
	const data = Buffer.alloc(16 * 16 * 4);
	for (let y = 0; y < 16; y++) {
		for (let x = 0; x < 16; x++) {
			const i = (y * 16 + x) * 4;
			const cx = x - 7.5, cy = y - 7.5;
			const dist = Math.abs(cx) + Math.abs(cy); // diamond shape
			if (dist < 6) {
				const bright = dist < 2.5;
				data[i] = bright ? 0xC0 : 0x18;
				data[i + 1] = 0xFF;
				data[i + 2] = bright ? 0xC0 : 0x50;
				data[i + 3] = 0xFF;
			}
		}
	}
	return data;
})());

const suitParts = ['green_lantern_suit_helmet', 'green_lantern_suit_chestplate',
	'green_lantern_suit_leggings', 'green_lantern_suit_boots'];
for (const part of suitParts) {
	write(`${ITEM_DIR}/${part}.png`, 16, 16, solidIcon(16, 16, 0x18, 0x30, 0x1C, 0xFF, 0x30, 0xF0, 0x60));
}

// Power Battery block texture (single texture used on all faces): dark green shell, bright core.
write(`${BLOCK_DIR}/power_battery.png`, 16, 16, (() => {
	const data = Buffer.alloc(16 * 16 * 4);
	for (let y = 0; y < 16; y++) {
		for (let x = 0; x < 16; x++) {
			const i = (y * 16 + x) * 4;
			const core = x >= 5 && x <= 10 && y >= 5 && y <= 10;
			if (core) {
				data[i] = 0x40; data[i + 1] = 0xFF; data[i + 2] = 0x80; data[i + 3] = 0xFF;
			} else {
				data[i] = 0x0C; data[i + 1] = 0x2A; data[i + 2] = 0x14; data[i + 3] = 0xFF;
			}
		}
	}
	return data;
})());

// Fallen Lantern Pedestal top-of-pillar accent texture.
write(`${BLOCK_DIR}/fallen_lantern_pedestal.png`, 16, 16, solidIcon(16, 16, 0x18, 0x30, 0x1C, 0xFF, 0x30, 0xF0, 0x60));

// 64x64 armour skin placeholder: black base overall, a green horizontal band through the torso rows
// and the boot rows, white in the hand/glove corners. Purely a first-pass colour placeholder -- not
// UV-accurate per body part; see docs/GREENLANTERN_REFERENCE.md for the bespoke-model follow-up note.
write(`${ARMOR_DIR}/green_lantern.png`, 64, 64, (() => {
	const data = Buffer.alloc(64 * 64 * 4);
	for (let y = 0; y < 64; y++) {
		for (let x = 0; x < 64; x++) {
			const i = (y * 64 + x) * 4;
			let r = 0x14, g = 0x14, b = 0x16, a = 0xFF;
			const torsoBand = y >= 20 && y <= 32;
			const bootBand = y >= 48 && y <= 58;
			const handCorner = (y >= 52 && y <= 60) && ((x >= 44 && x <= 50) || (x >= 4 && x <= 10));
			if (torsoBand || bootBand) {
				r = 0x1E; g = 0xB8; b = 0x4A;
			}
			if (handCorner) {
				r = 0xE8; g = 0xF4; b = 0xE8;
			}
			data[i] = r; data[i + 1] = g; data[i + 2] = b; data[i + 3] = a;
		}
	}
	return data;
})());

console.log('done');
