// v0.17.2: the kryptonite tool icons (16x16), drawn here as pixel maps. node scratchpad/gen_kryptonite_tools_v0172.js
const fs = require("fs");
const zlib = require("zlib");
const PAL = {
	o: [0x0d, 0x3b, 0x12, 255], // crystal outline
	g: [0x2f, 0xb0, 0x34, 255], // crystal
	l: [0x7c, 0xf5, 0x5c, 255], // crystal light
	h: [0xd9, 0xff, 0xc4, 255], // glint
	d: [0x2e, 0x1c, 0x0e, 255], // handle outline
	w: [0x7a, 0x4e, 0x27, 255], // handle
};
const ART = {
	kryptonite_sword: [
		"..............oo",
		".............oho",
		"............olgo",
		"...........olgo.",
		"..........olgo..",
		".........olgo...",
		"........olgo....",
		".......olgo.....",
		"..oo..olgo......",
		"..ogoolgo.......",
		"...oglgo........",
		"....ogo.........",
		"...dwoogo.......",
		"..dwd..oo.......",
		"ddwd............",
		"dd..............",
	],
	kryptonite_pickaxe: [
		"................",
		".....oooooo.....",
		"....ohllllgo....",
		"....oooooggoo...",
		"........dwoggo..",
		".......dwd.ogo..",
		"......dwd...ogo.",
		".....dwd....ogo.",
		"....dwd......oo.",
		"...dwd..........",
		"..dwd...........",
		".dwd............",
		"dwd.............",
		"dd..............",
		"................",
		"................",
	],
	kryptonite_axe: [
		"................",
		"......ooo.......",
		".....ohlgo......",
		"....ohlggoo.....",
		"....olggodo.....",
		".....oggdwgo....",
		"......oodwggo...",
		".......dwd.oo...",
		"......dwd.......",
		".....dwd........",
		"....dwd.........",
		"...dwd..........",
		"..dwd...........",
		".dwd............",
		"dwd.............",
		"dd..............",
	],
	kryptonite_shovel: [
		"...........ooo..",
		"..........ohlgo.",
		".........ohlggo.",
		".........olgggo.",
		"..........oggo..",
		".........dwoo...",
		"........dwd.....",
		".......dwd......",
		"......dwd.......",
		".....dwd........",
		"....dwd.........",
		"...dwd..........",
		"..dwd...........",
		".dwd............",
		"dwd.............",
		"dd..............",
	],
	kryptonite_hoe: [
		"................",
		".....oooooo.....",
		"....ohllggo.....",
		".....ooodggo....",
		".......dwd.o....",
		"......dwd.......",
		".....dwd........",
		"....dwd.........",
		"...dwd..........",
		"..dwd...........",
		".dwd............",
		"dwd.............",
		"dd..............",
		"................",
		"................",
		"................",
	],
};
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
function png(rows) {
	const w = 16, h = 16, raw = Buffer.alloc((w * 4 + 1) * h);
	for (let y = 0; y < h; y++) {
		if (rows[y].length !== 16) throw new Error("row " + y + " is " + rows[y].length);
		raw[y * (w * 4 + 1)] = 0;
		for (let x = 0; x < w; x++) {
			const p = PAL[rows[y][x]] || [0, 0, 0, 0];
			p.forEach((v, i) => (raw[y * (w * 4 + 1) + 1 + x * 4 + i] = v));
		}
	}
	const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = 6;
	return Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), chunk("IHDR", ihdr),
		chunk("IDAT", zlib.deflateSync(raw)), chunk("IEND", Buffer.alloc(0))]);
}
for (const [name, rows] of Object.entries(ART)) {
	fs.writeFileSync(`src/main/resources/assets/projecthero/textures/item/${name}.png`, png(rows));
	fs.writeFileSync(`src/main/resources/assets/projecthero/models/item/${name}.json`,
		JSON.stringify({ parent: "minecraft:item/handheld", textures: { layer0: `projecthero:item/${name}` } }, null, 2) + "\n");
}
console.log("ok");
