// v0.17.2 (user: "make the omnitrix containment pod look better"): the Containment Pod's textures and block models.
// node scratchpad/gen_omnitrix_pod_v0172.js  (writes into src/main/resources/assets/projecthero)
const fs = require("fs"), zlib = require("zlib"), path = require("path");
const R = "src/main/resources/assets/projecthero";

function png(file, w, h, px) {
	const raw = Buffer.alloc((w * 4 + 1) * h);
	for (let y = 0; y < h; y++) {
		raw[y * (w * 4 + 1)] = 0;
		for (let x = 0; x < w; x++) {
			const c = px(x, y), o = y * (w * 4 + 1) + 1 + x * 4;
			raw[o] = c[0]; raw[o + 1] = c[1]; raw[o + 2] = c[2]; raw[o + 3] = c.length > 3 ? c[3] : 255;
		}
	}
	const crcT = new Int32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c; });
	const crc = b => { let c = -1; for (const v of b) c = crcT[(c ^ v) & 255] ^ (c >>> 8); return (c ^ -1) >>> 0; };
	const chunk = (t, d) => { const l = Buffer.alloc(4); l.writeUInt32BE(d.length); const td = Buffer.concat([Buffer.from(t), d]); const c = Buffer.alloc(4); c.writeUInt32BE(crc(td)); return Buffer.concat([l, td, c]); };
	const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	fs.writeFileSync(path.join(R, file), Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]));
}
const hash = (x, y, s) => { let n = (x * 374761393 + y * 668265263 + s * 2147483647) | 0; n = (n ^ (n >>> 13)) * 1274126177 | 0; return ((n ^ (n >>> 16)) >>> 0) / 4294967296; };
const mix = (c, k) => c.map(v => Math.max(0, Math.min(255, Math.round(v * k))));

// base: dark gunmetal plating -- a soft brushed grain, a panel seam round the edge, four rivets
png("textures/block/omnitrix_pod_base.png", 16, 16, (x, y) => {
	let c = [38, 42, 48];
	c = mix(c, 0.92 + hash(x, y >> 1, 1) * 0.14);
	if (x === 0 || y === 0) c = mix(c, 1.35);
	if (x === 15 || y === 15) c = mix(c, 0.6);
	if ((x === 2 || x === 13) && (y === 2 || y === 13)) c = [92, 98, 106];
	return c;
});
// frame: a lighter brushed steel with a chamfered edge and a thin green light line down the middle
png("textures/block/omnitrix_pod_frame.png", 16, 16, (x, y) => {
	let c = [70, 76, 84];
	c = mix(c, 0.9 + hash(x >> 1, y, 2) * 0.16);
	if (x <= 1) c = mix(c, 1.3);
	if (x >= 14) c = mix(c, 0.65);
	if (x === 7 || x === 8) c = y % 4 === 0 ? [40, 120, 50] : [86, 230, 96];
	return c;
});
// panel: the console's screen -- black glass, a green Omnitrix hourglass, a level bar and two readout lines
png("textures/block/omnitrix_pod_panel.png", 16, 16, (x, y) => {
	if (x === 0 || y === 0 || x === 15 || y === 15) return [70, 76, 84];
	let c = [10, 16, 14];
	const cx = x - 7.5, cy = y - 6.5;
	if (Math.abs(cy) <= 4 && Math.abs(cx) <= Math.abs(cy) * 0.9 + 0.4 && Math.abs(cy) >= 0.8) c = [96, 255, 110];
	if (y === 12 && x >= 2 && x <= 13) c = x <= 9 ? [70, 220, 90] : [30, 60, 34];
	if (y === 14 && x >= 2 && x <= 6) c = [50, 150, 60];
	return c;
});

// ---- the models: elements in model px (16 = a block), y from the bottom of THIS half
function el(from, to, tex, rot) {
	const faces = {};
	for (const f of ["north", "south", "east", "west", "up", "down"]) faces[f] = { texture: "#" + tex };
	const e = { from, to, faces };
	if (rot) e.rotation = rot;
	return e;
}
const lower = [
	el([0, 0, 0], [16, 1, 16], "base"),                       // the foot
	el([1, 1, 1], [15, 2, 15], "frame"),                      // the step
	el([3, 2, 3], [13, 2.25, 13], "base"),                    // the floor plate (the hourglass is lit on it)
	// four corner ribs, inset a pixel, chamfered by a thin rotated strip on their outer edge
	el([1, 2, 1], [3, 16, 3], "frame"), el([13, 2, 1], [15, 16, 3], "frame"),
	el([1, 2, 13], [3, 16, 15], "frame"), el([13, 2, 13], [15, 16, 15], "frame"),
	// a low back wall and side skirts give it mass behind the field
	el([3, 2, 14], [13, 6, 15], "base"),
	el([1, 2, 3], [1.5, 4, 13], "base"), el([14.5, 2, 3], [15, 4, 13], "base"),
	// the waist collar on the ribs
	el([0.5, 9, 0.5], [3.5, 10, 3.5], "base"), el([12.5, 9, 0.5], [15.5, 10, 3.5], "base"),
	el([0.5, 9, 12.5], [3.5, 10, 15.5], "base"), el([12.5, 9, 12.5], [15.5, 10, 15.5], "base"),
	// the console on the front-right rib: a post and a tilted screen facing out
	el([15, 6, 4.5], [16, 11, 7.5], "base"),
	el([15.4, 10, 3.5], [16.4, 14, 8.5], "panel", { origin: [15.5, 12, 6], axis: "z", angle: -22.5 }),
];
const upper = [
	el([1, 0, 1], [3, 11, 3], "frame"), el([13, 0, 1], [15, 11, 3], "frame"),
	el([1, 0, 13], [3, 11, 15], "frame"), el([13, 0, 13], [15, 11, 15], "frame"),
	el([1, 11, 1], [15, 12, 15], "frame"),                    // the emitter ring's housing (lit underneath)
	el([0, 12, 0], [16, 14, 16], "base"),                     // the cap
	el([2, 14, 2], [14, 15, 14], "frame"),                    // stepped crown (kept inside the block: no stretched texture)
	el([4, 15, 4], [12, 16, 12], "base"),
];
function model(elements) {
	return {
		parent: "block/block", ambientocclusion: false,
		textures: { particle: "projecthero:block/omnitrix_pod_base", base: "projecthero:block/omnitrix_pod_base", frame: "projecthero:block/omnitrix_pod_frame", panel: "projecthero:block/omnitrix_pod_panel" },
		elements,
	};
}
fs.writeFileSync(path.join(R, "models/block/omnitrix_containment_pod_lower.json"), JSON.stringify(model(lower), null, 2) + "\n");
fs.writeFileSync(path.join(R, "models/block/omnitrix_containment_pod_upper.json"), JSON.stringify(model(upper), null, 2) + "\n");
console.log("ok");
