// v0.11.7: reshape the Power Battery block into a multi-element "lantern" silhouette (base, glowing
// core, cap, carrying-handle loop) per an explicit user request ("make the power battery model just a
// green lantern but slightly bigger"). Regenerates the core texture as a clean solid glow (no border --
// the frame is now a separate element/texture instead of one flat cube_all) and adds a new dark-metal
// frame texture for the base/cap/handle. Same hand-rolled PNG-via-zlib approach as gen_greenlantern_textures.js.
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
function write(path, w, h, data) {
	fs.writeFileSync(path, encode(w, h, data));
	console.log('wrote', path);
}

const BLOCK_DIR = 'src/main/resources/assets/projecthero/textures/block';

// Core: a clean, bright, glowing green fill -- no border any more, since the "shell" look now comes
// from the separate frame element/texture wrapping around it in the model itself.
write(`${BLOCK_DIR}/power_battery.png`, 16, 16, (() => {
	const data = Buffer.alloc(16 * 16 * 4);
	for (let y = 0; y < 16; y++) {
		for (let x = 0; x < 16; x++) {
			const i = (y * 16 + x) * 4;
			const cx = x - 7.5, cy = y - 7.5;
			const dist = Math.sqrt(cx * cx + cy * cy);
			const bright = dist < 4;
			data[i] = bright ? 0x60 : 0x2C;
			data[i + 1] = bright ? 0xFF : 0xB8;
			data[i + 2] = bright ? 0x90 : 0x54;
			data[i + 3] = 0xFF;
		}
	}
	return data;
})());

// Frame: a dark, brushed-metal grey/black for the base/cap/handle-loop elements.
write(`${BLOCK_DIR}/power_battery_frame.png`, 16, 16, (() => {
	const data = Buffer.alloc(16 * 16 * 4);
	for (let y = 0; y < 16; y++) {
		for (let x = 0; x < 16; x++) {
			const i = (y * 16 + x) * 4;
			// A faint banding pattern so it doesn't read as a completely flat colour.
			const band = (Math.floor(x / 2) + Math.floor(y / 2)) % 2 === 0;
			data[i] = band ? 0x22 : 0x1A;
			data[i + 1] = band ? 0x26 : 0x1E;
			data[i + 2] = band ? 0x24 : 0x1C;
			data[i + 3] = 0xFF;
		}
	}
	return data;
})());

console.log('done');
