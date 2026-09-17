/*
 * The Symbiote (Normal Host) model was supplied as a Blockbench project
 * (C:/Users/ethan/OneDrive/Desktop/3d minecraft models/Symbiote/model.bbmodel) with no separate
 * texture file -- Blockbench embeds the art as a base64 data: URI inside the .bbmodel's own
 * "textures" array. This just decodes that one texture back out to a real PNG; no Node zlib/PNG
 * encoding needed since the bytes are already a valid PNG, just base64-wrapped.
 *
 * Run from the repo root:  node scratchpad/extract_symbiote_texture.js
 */
const fs = require('fs');
const path = require('path');

const SRC = process.argv[2]
	|| 'C:/Users/ethan/OneDrive/Desktop/3d minecraft models/Symbiote/model.bbmodel';
const OUT = process.argv[3]
	|| path.join(__dirname, '..', 'src/main/resources/assets/projecthero/textures/armor/symbiote_host.png');

const bb = JSON.parse(fs.readFileSync(SRC, 'utf8'));
const tex = bb.textures && bb.textures[0];
if (!tex || !tex.source || !tex.source.startsWith('data:image/png;base64,')) {
	throw new Error('no embedded base64 PNG texture found in ' + SRC);
}
const b64 = tex.source.slice('data:image/png;base64,'.length);
const buf = Buffer.from(b64, 'base64');

// Sanity check: PNG signature + IHDR dimensions match the .bbmodel's declared resolution.
const sig = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
if (!buf.subarray(0, 8).equals(sig)) {
	throw new Error('decoded bytes are not a PNG');
}
const w = buf.readUInt32BE(16);
const h = buf.readUInt32BE(20);
console.log('decoded PNG', w + 'x' + h, '(bbmodel declares', tex.width + 'x' + tex.height + ')');

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, buf);
console.log('wrote', OUT, '-', buf.length, 'bytes');
