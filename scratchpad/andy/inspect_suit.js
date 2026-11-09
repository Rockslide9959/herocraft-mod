const P = require('./png.js');
const img = P.read('scratchpad/andy/andy_suit_skin.png');
console.log(img.w, img.h);
// arm column 47 (wide: right arm side face column at x=47, y 20..31) and 54/55 (back face end)
for (const [name, x0, y0, w, h] of [['rarm', 40, 16, 16, 16], ['larm', 32, 48, 16, 16], ['hat', 32, 0, 32, 16], ['jacket', 16, 32, 24, 16], ['rsleeve', 40, 32, 16, 16], ['lsleeve', 48, 48, 16, 16], ['rpants', 0, 32, 16, 16], ['lpants', 0, 48, 16, 16]]) {
  let rows = [];
  for (let y = y0; y < y0 + h; y++) { let s = ''; for (let x = x0; x < x0 + w; x++) s += P.get(img, x, y)[3] > 0 ? '#' : '.'; rows.push(s); }
  console.log(name + '\n' + rows.join('\n'));
}
