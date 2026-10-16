// v0.16.5: converts the user's pistolbutshit.obj (Maya export, muzzle +X, cm) into the Punisher pistol mesh.
//   node scratchpad/convert_pistol_obj.js "<path to pistolbutshit.obj>"
// Writes:
//   assets/projecthero/meshes/punisher_pistol.json  -- quads in item-model space (0..16), barrel -Z, grip -Y
//   assets/projecthero/textures/item/punisher_pistol.png -- 16x16 palette, one 4x4 swatch per part colour
// The OBJ has no texture (a black Maya default material), so every part is flat-coloured from PALETTE by group name.
const fs = require("fs");
const zlib = require("zlib");
const path = require("path");

const src = process.argv[2];
const ROOT = path.join(__dirname, "..", "src", "main", "resources", "assets", "projecthero");

// swatch index -> RGB. Order matters: index i sits at texel (4*(i%4), 4*floor(i/4)).
const PALETTE = [
	0x23272d, // 0 blued steel: slide, frame, trigger guard
	0x8c9198, // 1 bright steel: trigger, slide stop, takedown lever, barrel crown
	0x151617, // 2 rubber: grip panels, magazine, rear sight
	0x30353c, // 3 slide serrations / chamber hood (a shade lighter so they read)
];
const MAT = { pistol_blued_SG: 0, pistol_bright_SG: 1, pistol_rubber_SG: 2 };
function swatchFor(group, mat) {
	const g = group.toLowerCase();
	if (g.includes("serrations") || g.includes("chamber_hood")) return 3;
	if (g.includes("barrel_tip")) return 1;
	return MAT[mat] ?? 0;
}

// ---- parse ----
const lines = fs.readFileSync(src, "utf8").split(/\r?\n/);
const V = [];
const faces = []; // {group, idx[]}
let group = "default";
const mats = {};
for (const l of lines) {
	const p = l.trim().split(/\s+/);
	if (p[0] === "v") V.push(p.slice(1, 4).map(Number));
	else if (p[0] === "g") group = p.slice(1).join(" ");
	else if (p[0] === "usemtl") mats[group] = p[1];
	else if (p[0] === "f") faces.push({ group, idx: p.slice(1).map(s => { const i = parseInt(s.split("/")[0], 10); return i < 0 ? V.length + i : i - 1; }) });
}

// ---- transform: OBJ +X muzzle / -X stock, Y up, Z width  ->  model -Z muzzle, +Z stock, Y up ----
// A proper rotation about Y (det +1), so face winding is preserved: (x, y, z) -> (z, y, -x).
let lo = [1e9, 1e9, 1e9], hi = [-1e9, -1e9, -1e9];
for (const v of V) for (let k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], v[k]); hi[k] = Math.max(hi[k], v[k]); }
// About the old voxel pistol's footprint: 11 px from the muzzle (z=3) back, centred on x=8, magazine base at y=2.5.
const s = 11.0 / (hi[0] - lo[0]);
const cz = (lo[2] + hi[2]) / 2;
const T = v => [8 + (v[2] - cz) * s, 2.5 + (v[1] - lo[1]) * s, 3 + (hi[0] - v[0]) * s];
const P = V.map(T);

// ---- faces -> quads (n-gons fan-triangulated; a triangle is a quad with its last vertex repeated) ----
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
function normalOf(ids) { // Newell's method
	const n = [0, 0, 0];
	for (let i = 0; i < ids.length; i++) {
		const a = P[ids[i]], b = P[ids[(i + 1) % ids.length]];
		n[0] += (a[1] - b[1]) * (a[2] + b[2]); n[1] += (a[2] - b[2]) * (a[0] + b[0]); n[2] += (a[0] - b[0]) * (a[1] + b[1]);
	}
	return n;
}
// Winding check: outward faces should point away from their part's centroid. Maya writes CCW-front faces, which is
// also what Minecraft wants; if a part comes out mostly inward we flip it.
const byGroup = {};
for (const f of faces) (byGroup[f.group] ||= []).push(f);
const quads = [];
let flipped = [];
for (const [g, fs_] of Object.entries(byGroup)) {
	const c = [0, 0, 0]; let n = 0;
	for (const f of fs_) for (const i of f.idx) { c[0] += P[i][0]; c[1] += P[i][1]; c[2] += P[i][2]; n++; }
	c[0] /= n; c[1] /= n; c[2] /= n;
	let out = 0, inn = 0;
	for (const f of fs_) {
		const fc = [0, 0, 0]; for (const i of f.idx) { fc[0] += P[i][0] / f.idx.length; fc[1] += P[i][1] / f.idx.length; fc[2] += P[i][2] / f.idx.length; }
		if (dot(normalOf(f.idx), sub(fc, c)) >= 0) out++; else inn++;
	}
	const flip = inn > 4 * out; // only when clearly inside-out: grooved parts (the barrel) legitimately have inward-looking faces
	if (flip) flipped.push(g);
	const sw = swatchFor(g, mats[g]);
	for (const f of fs_) {
		const ids = flip ? [...f.idx].reverse() : f.idx;
		const polys = ids.length === 4 ? [ids] : [];
		if (ids.length === 3) polys.push([ids[0], ids[1], ids[2], ids[2]]);
		if (ids.length > 4) for (let i = 1; i + 1 < ids.length; i++) polys.push([ids[0], ids[i], ids[i + 1], ids[i + 1]]);
		for (const q of polys) quads.push({ s: sw, v: q.map(i => P[i].map(x => Math.round(x * 10000) / 10000)) });
	}
}

fs.mkdirSync(path.join(ROOT, "meshes"), { recursive: true });
fs.writeFileSync(path.join(ROOT, "meshes", "punisher_pistol.json"), JSON.stringify({
	comment: "Punisher pistol mesh, converted from pistolbutshit.obj by scratchpad/convert_pistol_obj.js. Item-model units (0..16), barrel -Z, grip -Y. s = palette swatch (4x4 texels at (4*(s%4), 4*floor(s/4)) of textures/item/punisher_pistol.png).",
	quads: quads.map(q => ({ s: q.s, v: q.v.flat() })),
}));

// ---- 16x16 palette texture ----
function png(w, h, rgba) {
	const crcT = new Int32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c; });
	const crc = b => { let c = -1; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ -1) >>> 0; };
	const chunk = (t, d) => { const len = Buffer.alloc(4); len.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([len, td, c]); };
	const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	const raw = Buffer.alloc((w * 4 + 1) * h);
	for (let y = 0; y < h; y++) { raw[y * (w * 4 + 1)] = 0; rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4); }
	return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
const img = Buffer.alloc(16 * 16 * 4);
for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
	const i = Math.floor(y / 4) * 4 + Math.floor(x / 4);
	const c = PALETTE[i] ?? PALETTE[0];
	const o = (y * 16 + x) * 4;
	img[o] = (c >> 16) & 255; img[o + 1] = (c >> 8) & 255; img[o + 2] = c & 255; img[o + 3] = 255;
}
fs.writeFileSync(path.join(ROOT, "textures", "item", "punisher_pistol.png"), png(16, 16, img));

let blo = [1e9, 1e9, 1e9], bhi = [-1e9, -1e9, -1e9];
for (const p of P) for (let k = 0; k < 3; k++) { blo[k] = Math.min(blo[k], p[k]); bhi[k] = Math.max(bhi[k], p[k]); }
console.log(`${faces.length} faces -> ${quads.length} quads; bounds ${blo.map(x => x.toFixed(2))} .. ${bhi.map(x => x.toFixed(2))}; flipped: ${flipped.join(", ") || "none"}`);

// rig points (for GunFirstPerson / MuzzleFlash), from the OBJ parts
const box = g => { const b = [[1e9,1e9,1e9],[-1e9,-1e9,-1e9]]; for (const f of byGroup[g]) for (const i of f.idx) for (let k = 0; k < 3; k++) { b[0][k] = Math.min(b[0][k], P[i][k]); b[1][k] = Math.max(b[1][k], P[i][k]); } return b; };
const ctr = b => b[0].map((x, k) => ((x + b[1][k]) / 2).toFixed(2));
for (const g of Object.keys(byGroup)) { const b = box(g); console.log(g.padEnd(34), "centre", ctr(b).join(", "), " top", b[1][1].toFixed(2), " z", b[0][2].toFixed(2), "..", b[1][2].toFixed(2)); }
