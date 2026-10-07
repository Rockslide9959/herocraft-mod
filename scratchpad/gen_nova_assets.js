// v0.15.13 Nova assets: the suit texture (the user's armour-only skin, byte-for-byte), its glow mask (cyan texels only),
// and the 16x16 Nova Corps Helmet item icon. Run: node gen_nova_assets.js <worktree root>
const fs = require('fs');
const path = require('path');
const { decode, encode } = require('C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad/nova_skins/png.js');
const root = process.argv[2];
const src = 'C:/Users/ethan/OneDrive/Desktop/Coding Projects/Superhero Mod/scratchpad/nova_skins/nova_armour.png';
const texDir = path.join(root, 'src/main/resources/assets/projecthero/textures/entity/nova');
fs.mkdirSync(texDir, { recursive: true });
fs.copyFileSync(src, path.join(texDir, 'nova_suit.png'));

// glow mask: the cyan star / eye-lens texels only
const s = decode(fs.readFileSync(src));
const glow = Buffer.alloc(64 * 64 * 4);
let n = 0;
for (let i = 0; i < 64 * 64; i++) {
  const o = i * 4;
  const [r, g, b, a] = [s.px[o], s.px[o + 1], s.px[o + 2], s.px[o + 3]];
  if (a > 0 && b > 200 && g > 200 && r < 200) {
    glow[o] = r; glow[o + 1] = g; glow[o + 2] = b; glow[o + 3] = 255;
    n++;
  }
}
fs.writeFileSync(path.join(texDir, 'nova_suit_glow.png'), encode(64, 64, glow));
console.log('glow texels', n);

// the helmet icon
const pal = {
  '.': [0, 0, 0, 0], K: [70, 44, 10, 255], G: [255, 196, 52, 255], g: [196, 132, 26, 255], H: [255, 238, 150, 255],
  R: [205, 32, 40, 255], r: [140, 16, 24, 255], C: [139, 248, 255, 255], B: [18, 49, 61, 255],
};
const rows = [
  '......KKKK......',
  '....KKGRRGKK....',
  '...KGHGRRGGgK...',
  '..KGHGGRRGGGgK..',
  '..KGHGGRRGGGgK..',
  '.KGGGGGRRGGGGgK.',
  '.KGRRRRRRRRRRgK.',
  '.KGGCCGRRGCCGgK.',
  '.KGGCCGrrGCCGgK.',
  '.KGGGGGRRGGGGgK.',
  '.KgGGGBBBBGGGgK.',
  '.KgGGB....BGGgK.',
  '.KggGB....BGggK.',
  '..KgGK....KGgK..',
  '...KK......KK...',
  '................',
];
const icon = Buffer.alloc(16 * 16 * 4);
rows.forEach((row, y) => {
  if (row.length !== 16) throw new Error('row ' + y + ' is ' + row.length);
  [...row].forEach((ch, x) => { const c = pal[ch]; const o = (y * 16 + x) * 4; icon[o] = c[0]; icon[o + 1] = c[1]; icon[o + 2] = c[2]; icon[o + 3] = c[3]; });
});
const itemDir = path.join(root, 'src/main/resources/assets/projecthero/textures/item');
fs.writeFileSync(path.join(itemDir, 'nova_corps_helmet.png'), encode(16, 16, icon));
const modelDir = path.join(root, 'src/main/resources/assets/projecthero/models/item');
fs.writeFileSync(path.join(modelDir, 'nova_corps_helmet.json'), JSON.stringify({
  parent: 'minecraft:item/generated', textures: { layer0: 'projecthero:item/nova_corps_helmet' } }, null, 2) + '\n');
fs.writeFileSync(path.join(modelDir, 'nova_centurion_spawn_egg.json'), JSON.stringify({ parent: 'minecraft:item/template_spawn_egg' }, null, 2) + '\n');
console.log('done');
