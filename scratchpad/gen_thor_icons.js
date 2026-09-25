// 16x16 inventory icons for Thor's Armour (v0.12.32): black plate, crimson cloth, silver studs -- the palette of the
// supplied thor.bbmodel skin. Hand-drawn like every other sprite in this mod.
const fs = require('fs');
const { encode } = require('./pnglib.js');
const OUT = 'src/main/resources/assets/projecthero/textures/item';
class C {
	constructor(w = 16, h = 16) { this.w = w; this.h = h; this.data = Buffer.alloc(w * h * 4); }
	set(x, y, c) { x = Math.round(x); y = Math.round(y); if (!c || x < 0 || y < 0 || x >= this.w || y >= this.h) return; const i = (y * this.w + x) * 4; this.data[i] = c[0]; this.data[i + 1] = c[1]; this.data[i + 2] = c[2]; this.data[i + 3] = 255; }
	rect(x, y, w, h, c) { for (let dy = 0; dy < h; dy++) for (let dx = 0; dx < w; dx++) this.set(x + dx, y + dy, c); }
}
const BLACK = [26, 24, 28], BLACK_L = [48, 46, 52], RED = [150, 22, 26], RED_D = [102, 12, 16], RED_L = [190, 44, 44], SILVER = [178, 182, 190], SILVER_D = [120, 124, 134];

// chestplate: black torso, crimson sleeves, studded sternum plate
{
	const c = new C();
	c.rect(4, 2, 8, 11, BLACK); c.rect(4, 2, 8, 1, BLACK_L);
	c.rect(1, 2, 3, 9, RED); c.rect(12, 2, 3, 9, RED);
	c.rect(1, 2, 3, 1, RED_L); c.rect(12, 2, 3, 1, RED_L);
	c.rect(1, 10, 3, 1, RED_D); c.rect(12, 10, 3, 1, RED_D);
	c.rect(6, 4, 4, 7, BLACK_L);
	for (const [x, y] of [[7, 5], [8, 5], [7, 7], [8, 7], [7, 9], [8, 9]]) c.set(x, y, SILVER);
	c.set(6, 4, SILVER_D); c.set(9, 4, SILVER_D);
	c.rect(4, 12, 8, 1, RED_D);
	fs.writeFileSync(`${OUT}/thor_armor_chestplate.png`, encode(16, 16, c.data));
}
// leggings: black greaves with a crimson panel, silver belt buckle
{
	const c = new C();
	c.rect(3, 1, 10, 4, BLACK); c.rect(3, 1, 10, 1, BLACK_L);
	c.rect(3, 5, 4, 9, BLACK); c.rect(9, 5, 4, 9, BLACK);
	c.rect(4, 6, 2, 6, RED); c.rect(10, 6, 2, 6, RED);
	c.rect(4, 6, 2, 1, RED_L); c.rect(10, 6, 2, 1, RED_L);
	c.rect(7, 1, 2, 2, SILVER); c.set(7, 1, SILVER_D);
	c.rect(3, 13, 4, 1, BLACK_L); c.rect(9, 13, 4, 1, BLACK_L);
	fs.writeFileSync(`${OUT}/thor_armor_leggings.png`, encode(16, 16, c.data));
}
// boots: black boots with crimson cuffs and silver toe caps
{
	const c = new C();
	c.rect(3, 3, 4, 10, BLACK); c.rect(9, 3, 4, 10, BLACK);
	c.rect(2, 11, 5, 3, BLACK); c.rect(9, 11, 5, 3, BLACK);
	c.rect(3, 3, 4, 2, RED); c.rect(9, 3, 4, 2, RED);
	c.rect(3, 3, 4, 1, RED_L); c.rect(9, 3, 4, 1, RED_L);
	c.rect(2, 12, 2, 2, SILVER_D); c.rect(9, 12, 2, 2, SILVER_D);
	c.rect(2, 14, 5, 1, BLACK_L); c.rect(9, 14, 5, 1, BLACK_L);
	fs.writeFileSync(`${OUT}/thor_armor_boots.png`, encode(16, 16, c.data));
}
console.log('wrote 3 Thor armour icons');
