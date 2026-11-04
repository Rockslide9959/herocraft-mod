// v0.17.2 round 2: the Adaptive Nova's energy shell texture -- white diagonal bands (tinted per element in code; the vanilla
// creeper swirl is blue and turned every colour teal). Usage: node scratchpad/gen_doomsday_nova_swirl.js
const fs = require('fs'), path = require('path'), k = require('./pngkit.js');
const W = 64, px = Buffer.alloc(W * W * 4);
for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) {
	const v = Math.sin((x + y) * Math.PI * 2 / 16) * 0.5 + 0.5 + Math.sin((x - y * 0.5) * Math.PI * 2 / 32) * 0.25;
	const a = Math.max(0, Math.min(1, (v - 0.35) * 1.6));
	const i = (y * W + x) * 4;
	px[i] = px[i + 1] = px[i + 2] = Math.round(140 + 115 * a);
	px[i + 3] = Math.round(255 * a);
}
fs.writeFileSync(path.join(__dirname, '..', 'src/main/resources/assets/projecthero/textures/entity/doomsday/nova_swirl.png'), k.encode(W, W, px));
console.log('ok');
