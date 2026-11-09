// v0.17.3: Andy's Suit -- textures from the user's skin (scratchpad/andy/andy_suit_skin.png, a wide 64x64 skin):
//  - textures/entity/andy/andy_suit_wide.png (as is) and andy_suit_slim.png (the arm boxes narrowed to 3 px),
//  - item icons textures/item/andy_suit_{helmet,chestplate,leggings,boots}.png drawn from the skin's front faces,
//  - an empty vanilla armour layer (textures/models/armor/andy_suit_layer_1/2.png: never drawn, AndySuitArmourMixin).
// Run from the repo root: node scratchpad/andy/andy_suit_textures.js
const P = require('./png.js');
const orig = P.read('scratchpad/andy/andy_suit_skin.png');
// v0.17.3 (user: 'the andy armour should just be the suit without the skin so that the players skin can show'): only the
// clothes -- the head (base + hat) is cleared and every skin-tone texel elsewhere (hands, the collar's neck) is transparent,
// so the wearer's own face, hair and hands show through. The forehead card is Andy's own render (AndyRender.card), not the suit.
const src = P.blank(64, 64);
orig.data.copy(src.data);
const skinTone = c => c[3] > 0 && c[0] >= 0xe0 && c[1] >= 0xb4 && c[1] <= 0xd8 && c[2] >= 0x88 && c[2] <= 0xb0;
for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
  const head = y < 16 && x < 64; // the head and hat boxes
  if (head || skinTone(P.get(src, x, y))) P.set(src, x, y, [0, 0, 0, 0]);
}
const R = 'src/main/resources/assets/projecthero/textures/';
P.write(R + 'entity/andy/andy_suit_wide.png', src);

// ---- slim: each arm box (wide 4x12x4 at ox,oy) re-unwrapped as 3x12x4
const slim = P.blank(64, 64);
src.data.copy(slim.data);
function clearBox(img, ox, oy) { for (let y = oy; y < oy + 16; y++) for (let x = ox; x < ox + 16; x++) P.set(img, x, y, [0, 0, 0, 0]); }
function narrow(ox, oy) {
  const g = (x, y) => P.get(src, x, y);
  clearBox(slim, ox, oy);
  // top (ox+4, oy) 4x4 -> (ox+4, oy) 3x4; bottom (ox+8, oy) -> (ox+7, oy) 3x4 (drop the outer column)
  for (let y = 0; y < 4; y++) for (let x = 0; x < 3; x++) { P.set(slim, ox + 4 + x, oy + y, g(ox + 4 + x, oy + y)); P.set(slim, ox + 7 + x, oy + y, g(ox + 8 + x, oy + y)); }
  for (let y = 4; y < 16; y++) {
    for (let x = 0; x < 4; x++) P.set(slim, ox + x, oy + y, g(ox + x, oy + y));               // right side 4 wide, same place
    for (let x = 0; x < 3; x++) P.set(slim, ox + 4 + x, oy + y, g(ox + 4 + (x < 2 ? x : 3), oy + y)); // front 4 -> 3 (drop col 2)
    for (let x = 0; x < 4; x++) P.set(slim, ox + 7 + x, oy + y, g(ox + 8 + x, oy + y));       // left side, moved 1 left
    for (let x = 0; x < 3; x++) P.set(slim, ox + 11 + x, oy + y, g(ox + 12 + (x < 1 ? x : x + 1), oy + y)); // back 4 -> 3
  }
}
for (const [ox, oy] of [[40, 16], [40, 32], [32, 48], [48, 48]]) narrow(ox, oy);
P.write(R + 'entity/andy/andy_suit_slim.png', slim);

// ---- item icons
function over(a, b) { return b[3] > 0 ? b : a; }
function px(x, y, ox, oy) { return over(P.get(orig, x, y), P.get(orig, x + ox, y + oy)); }
function icon(name, w, h, sample, scale) {
  const img = P.blank(16, 16);
  const x0 = Math.floor((16 - w * scale) / 2), y0 = Math.floor((16 - h * scale) / 2);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const c = sample(x, y);
    if (!c || c[3] === 0) continue;
    for (let j = 0; j < scale; j++) for (let i = 0; i < scale; i++) P.set(img, x0 + x * scale + i, y0 + y * scale + j, c);
  }
  // a dark outline so it reads on any slot
  const out = P.blank(16, 16); img.data.copy(out.data);
  for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
    if (P.get(img, x, y)[3] > 0) continue;
    const n = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => x + dx >= 0 && x + dx < 16 && y + dy >= 0 && y + dy < 16 && P.get(img, x + dx, y + dy)[3] > 0);
    if (n) P.set(out, x, y, [0x16, 0x10, 0x14, 255]);
  }
  P.write(R + 'item/' + name + '.png', out);
}
// helmet: the face (head front 8,8 with the hat front 40,8 over it), 2x
if (false) icon('andy_suit_helmet', 7, 7, (x, y) => px(8 + Math.min(7, Math.round(x * 8 / 7)), 8 + Math.min(7, Math.round(y * 8 / 7)), 32, 0), 2);
// chestplate: right arm front | body front | left arm front, 16x12 at 1x -- squeezed to 14 wide
icon('andy_suit_chestplate', 14, 12, (x, y) => {
  if (x < 3) return px(44 + Math.min(3, Math.round(x * 4 / 3)), 20 + y, 0, 16);
  if (x >= 11) return px(36 + Math.min(3, Math.round((x - 11) * 4 / 3)), 52 + y, 16, 0);
  return px(20 + Math.min(7, Math.round((x - 3) * 8 / 8)), 20 + y, 0, 16);
}, 1);
// leggings: both legs' fronts, the top 7 rows, plus the belt row of the body, 2x (8 x 7 -> 16 x 14) -- with a gap between legs
icon('andy_suit_leggings', 8, 7, (x, y) => (x < 4 ? px(4 + x, 20 + y, 0, 16) : px(20 + x - 4, 52 + y, -16, 0)), 2);
// boots: both legs' fronts from row 7 down (5 rows), 2x
icon('andy_suit_boots', 8, 5, (x, y) => (x < 4 ? px(4 + x, 27 + y, 0, 16) : px(20 + x - 4, 59 + y, -16, 0)), 2);

// ---- the never-drawn vanilla armour layers
P.write(R + 'models/armor/andy_suit_layer_1.png', P.blank(64, 32));
P.write(R + 'models/armor/andy_suit_layer_2.png', P.blank(64, 32));
console.log('ok');
