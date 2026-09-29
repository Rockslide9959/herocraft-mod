// v0.14.3 Green Lantern GUI art: construct icon atlas (128x64, 16x16 cells, index = ConstructType ordinal; cell 31 =
// the Lantern emblem) + the 3D Power Ring item texture. Run from the project root: node scratchpad/gen_greenlantern_v0143.js
const fs = require('fs');
const path = require('path');
const { encode } = require('./pngkit');

const ROOT = 'src/main/resources/assets/projecthero/textures/';
const PAL = {
	'.': null,
	o: [8, 44, 20, 255],     // outline
	d: [26, 128, 58, 255],   // deep green
	g: [53, 240, 117, 255],  // lantern green
	l: [168, 255, 192, 255], // pale
	w: [240, 255, 244, 255], // white-hot
	h: [53, 240, 117, 110],  // translucent glow
};

function img(w, h) {
	const px = Buffer.alloc(w * h * 4);
	return { w, h, px, set(x, y, c) { if (x < 0 || y < 0 || x >= w || y >= h || !c) return; const i = (y * w + x) * 4; px[i] = c[0]; px[i + 1] = c[1]; px[i + 2] = c[2]; px[i + 3] = c[3]; } };
}

const ICONS = {
	0: [ // Energy Blade
		'..............ow',
		'.............owl',
		'............owlo',
		'...........owlo.',
		'..........owlo..',
		'.........owlo...',
		'........owlo....',
		'.......owlo.....',
		'..o...owlo......',
		'..ogo.wlo.......',
		'...ogglo........',
		'....oggo........',
		'...oddgo........',
		'..odo.ogo.......',
		'.odo...o........',
		'.oo.............'],
	1: [ // Containment Cage
		'................',
		'.oooooooooooooo.',
		'.ogggggggggggggo',
		'.ogodgdgdgdgdog.',
		'.ogodgdgdgdgdog.',
		'.ogod.d.d.d.dog.',
		'.ogod.d.d.d.dog.',
		'.ogod.d.d.d.dog.',
		'.ogod.d.d.d.dog.',
		'.ogod.d.d.d.dog.',
		'.ogod.d.d.d.dog.',
		'.ogodgdgdgdgdog.',
		'.ogggggggggggggo',
		'.oooooooooooooo.',
		'................',
		'................'],
	2: [ // Sentry Turret
		'.......oo.......',
		'......olwo......',
		'.....olwwgo.....',
		'....olwggggo....',
		'...olggggggdo...',
		'....oggggddo....',
		'.....oggddo.....',
		'......oddo......',
		'.......oo.......',
		'.......gd.......',
		'.......gd.......',
		'.......gd.......',
		'......oggo......',
		'.....oggggo.....',
		'....oddddddo....',
		'....oooooooo....'],
	3: [ // Battering Ram
		'................',
		'.........oo.....',
		'.........olo....',
		'.........oglo...',
		'..ooooooooggl o.',
		'..olllllllgggglo',
		'..oggggggggggggo',
		'..oggggggggggggo',
		'..oddddddddgggdo',
		'..ooooooooggdo..',
		'.........ogdo...',
		'.........odo....',
		'.........oo.....',
		'.ll.............',
		'l..l............',
		'................'],
	4: [ // Hard-Light Wall
		'oooooooooooooooo',
		'ollllgoolllllgoo',
		'ogggggoogggggdoo',
		'oooooooooooooooo',
		'ooolllllgoollllo',
		'ooogggggdooggggo',
		'oooooooooooooooo',
		'ollllgoolllllgoo',
		'ogggggoogggggdoo',
		'oooooooooooooooo',
		'ooolllllgoollllo',
		'ooogggggdooggggo',
		'oooooooooooooooo',
		'ollllgoolllllgoo',
		'ogggggoogggggdoo',
		'oooooooooooooooo'],
	5: [ // Platform
		'................',
		'................',
		'................',
		'................',
		'................',
		'.oooooooooooooo.',
		'olllllllllllllgo',
		'oggggggggggggggo',
		'oddddddddddddddo',
		'.oooooooooooooo.',
		'...h........h...',
		'...h........h...',
		'....h......h....',
		'.....h....h.....',
		'................',
		'................'],
	6: [ // Bridge
		'................',
		'................',
		'................',
		'oooooooooooooooo',
		'olllllllllllllll',
		'oggggggggggggggo',
		'oggoooooooooggdo',
		'ogo.........oggo',
		'oo...........odo',
		'og...........ogo',
		'og...........ogo',
		'og...........ogo',
		'od...........odo',
		'oo...........ooo',
		'................',
		'................'],
	7: [ // Stair/Ramp
		'................',
		'...........ooooo',
		'...........ollgo',
		'...........oggdo',
		'.......oooooggdo',
		'.......ollggggdo',
		'.......ogggggddo',
		'...oooooggggggdo',
		'...ollggggggggdo',
		'...ogggggggggddo',
		'oooogggggggggddo',
		'ollgggggggggggdo',
		'oggggggggggggddo',
		'oddddddddddddddo',
		'oooooooooooooooo',
		'................'],
	8: [ // Mining Drill
		'................',
		'..oooooooooooo..',
		'..ollllllllllo..',
		'..oggggggggggo..',
		'...oggddggddo...',
		'...ogggggggdo...',
		'....oddggddo....',
		'....oggggggo....',
		'.....oddggo.....',
		'.....ogggdo.....',
		'......oddo......',
		'......oggo......',
		'.......oo.......',
		'.......lo.......',
		'.......o........',
		'................'],
	9: [ // Lantern Light
		'.......hh.......',
		'..h....hh....h..',
		'...h..........h.',
		'......oooo......',
		'....ollllgoo....',
		'...olwwwlggdo...',
		'hh.olwwllggdo.hh',
		'hh.olwlllggdo.hh',
		'...ollgggggdo...',
		'...oggggggddo...',
		'....oggggddo....',
		'......oooo......',
		'...h..........h.',
		'..h....hh....h..',
		'.......hh.......',
		'................'],
	10: [ // Atmosphere Bubble
		'.....oooooo.....',
		'...oohhhhhhoo...',
		'..ohhllhhhhhho..',
		'.ohhlhhhhhhhhho.',
		'.ohlhhhhhhhhhho.',
		'ohhhhhhhhhhhhhho',
		'ohhhhhhooohhhhho',
		'ohhhhhoggdohhhho',
		'ohhhhhogggohhhho',
		'ohhhhhhodohhhhho',
		'ohhhhhoggdohhhho',
		'.ohhhhoggdohhho.',
		'.ohhhhhhhhhhhho.',
		'..ohhhhhhhhhho..',
		'...oohhhhhhoo...',
		'.....oooooo.....'],
	11: [ // Rescue Tether
		'..........oooo..',
		'.........ollgdo.',
		'........olo..odo',
		'........ogo..ogo',
		'........odo.odo.',
		'.........ooodo..',
		'.......l..ogo...',
		'......l...odo...',
		'.....l....ogo...',
		'....l.....odo...',
		'...l....oooooo..',
		'..l....oggggggo.',
		'.l.....ogoooogo.',
		'l......ogo..ogo.',
		'.......odo..odo.',
		'........o....o..'],
	12: [ // Carry Platform
		'.......oo.......',
		'......olgo......',
		'.....olggdo.....',
		'....ooogdooo....',
		'......ogdo......',
		'......ogdo......',
		'......oooo......',
		'................',
		'.oooooooooooooo.',
		'olllllllllllllgo',
		'olggggggggggggdo',
		'oggggggggggggggo',
		'oddddddddddddddo',
		'.oooooooooooooo.',
		'..h.h.h..h.h.h..',
		'................'],
	13: [ // Hard-Light Tool Kit
		'................',
		'...oooooooooo...',
		'..ollllllllllo..',
		'.olggoooooggdo..',
		'.ogdo..ol..odgo.',
		'.ogo...og...ogo.',
		'.oo....og....oo.',
		'.......og.......',
		'.......og.......',
		'.......od.......',
		'.......og.......',
		'.......od.......',
		'.......og.......',
		'.......od.......',
		'.......oo.......',
		'................'],
	14: [ // Buzzsaw
		'.......o........',
		'....o.ogo.o.....',
		'...ogoogoogo....',
		'....oggggggo..o.',
		'..ooggddddggoogo',
		'.oggdddwwdddgo..',
		'..ogddwllwddgo..',
		'.ooggdwllwdggoo.',
		'..ogddwwwwddgo..',
		'..ogddddddddgo..',
		'.ooggddddddggoo.',
		'...oggggggggo...',
		'..ogo.oggo.ogo..',
		'...o..ogo...o...',
		'.......o........',
		'................'],
	15: [ // Anvil Drop
		'.......hh.......',
		'.......hh.......',
		'.....h.hh.h.....',
		'......hhhh......',
		'.......hh.......',
		'oooooooooooooo..',
		'ollllllllllllloo',
		'ogggggggggggggdo',
		'.ooddggggggddoo.',
		'....oggggggo....',
		'....oggggddo....',
		'...ogggggggdo...',
		'..olggggggggdo..',
		'..oddddddddddo..',
		'..oooooooooooo..',
		'................'],
	16: [ // Chain Snare
		'oooo............',
		'olgdo...........',
		'og.odo..........',
		'odo.ooooo.......',
		'.ooooglgdo......',
		'.....odo.odo....',
		'.....og...go....',
		'.....odo.odo....',
		'......odlgoooo..',
		'.......ooo.olgo.',
		'..........og.odo',
		'..........odo.go',
		'...........ooodo',
		'.............oo.',
		'................',
		'................'],
	17: [ // Launch Pad
		'.......oo.......',
		'......olgo......',
		'.....olgggo.....',
		'....ooogdooo....',
		'......ogdo......',
		'..oooooooooooo..',
		'..ollllllllllo..',
		'..oooooooooooo..',
		'....oggggggo....',
		'...oddddddddo...',
		'....oggggggo....',
		'...oddddddddo...',
		'.oooooooooooooo.',
		'.olllllllllllgo.',
		'.oddddddddddddo.',
		'.oooooooooooooo.'],
	18: [ // Emerald Warrior
		'.......oo.......',
		'......olgo......',
		'.....oooooo.....',
		'....ollllggo....',
		'...olggggggdo...',
		'...ogoooooogo...',
		'...ogowwwwogo...',
		'...ogoooooogo...',
		'...oggggggggo...',
		'...oggggggddo...',
		'....oggggddo....',
		'..ooodggggdooo..',
		'.olgggoooogggdo.',
		'.ogggggllggggdo.',
		'.odddddddddddddo',
		'.ooooooooooooooo'],
	31: [ // the Lantern emblem
		'..oooooooooooo..',
		'.ollllllllllllo.',
		'.oooooooooooooo.',
		'.....oooooo.....',
		'....oggggggo....',
		'...ogdooooggo...',
		'..ogdo....ogdo..',
		'..ogo......ogo..',
		'..ogo......ogo..',
		'..ogdo....ogdo..',
		'...ogdooooggo...',
		'....oggggggo....',
		'.....oooooo.....',
		'.oooooooooooooo.',
		'.ollllllllllllo.',
		'..oooooooooooo..'],
};

const atlas = img(128, 64);
for (const [idx, rows] of Object.entries(ICONS)) {
	const i = Number(idx);
	const ox = (i % 8) * 16;
	const oy = Math.floor(i / 8) * 16;
	rows.forEach((row, y) => {
		for (let x = 0; x < 16; x++) {
			atlas.set(ox + x, oy + y, PAL[row[x] || '.'] || null);
		}
	});
}
fs.mkdirSync(ROOT + 'gui/green_lantern', { recursive: true });
fs.writeFileSync(ROOT + 'gui/green_lantern/constructs.png', encode(atlas.w, atlas.h, atlas.px));

// the 3D Power Ring item texture: band metal (0-3), band highlight (4), bezel (5), gem (6-7) as horizontal stripes of
// 2px, sampled by models/item/power_ring.json
{
	const t = img(16, 16);
	for (let y = 0; y < 16; y++) {
		for (let x = 0; x < 16; x++) {
			let c;
			if (y < 4) c = [[36, 150, 70], [44, 170, 82], [30, 128, 60], [22, 104, 48]][y];
			else if (y < 6) c = [150, 255, 185];
			else if (y < 10) c = [[14, 60, 30], [10, 48, 24], [12, 54, 27], [8, 40, 20]][y - 6];
			else {
				const d = Math.hypot(x - 7.5, y - 12.5);
				const k = Math.max(0, 1 - d / 5);
				c = [Math.round(90 + 150 * k), 255, Math.round(140 + 110 * k)];
			}
			const n = ((x * 7 + y * 13) % 5) - 2;
			t.set(x, y, [Math.max(0, Math.min(255, c[0] + n)), Math.max(0, Math.min(255, c[1] + n)), Math.max(0, Math.min(255, c[2] + n)), 255]);
		}
	}
	fs.writeFileSync(ROOT + 'item/power_ring_model.png', encode(16, 16, t.px));
}
console.log('ok');
