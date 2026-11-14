// v0.17.4 Beast Boy: the green hearts drawn in place of the player's hearts while he is an animal (the form's health).
// 9x9 GUI sprites in the vanilla heart style, own palette: full, half, the empty container, and the blinking (hurt / heal flash) versions.
// Writes src/main/resources/assets/projecthero/textures/gui/sprites/hud/beast_heart/*.png
// Usage: node scratchpad/beastboy/gen_green_hearts.js
const fs = require('fs');
const path = require('path');
const png = require('../pnglib.js');
const OUT = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/textures/gui/sprites/hud/beast_heart');
fs.mkdirSync(OUT, { recursive: true });

// the outline (O) and the inside (#, H = highlight, S = shade on the lower edges, M = the lower right's mid tone)
const CONTAINER = [
  '..OO.OO..',
  '.O##O##O.',
  'O#######O',
  'O#######O',
  'O#######O',
  '.O#####O.',
  '..O###O..',
  '...O#O...',
  '....O....',
];
const HEART = [
  '.........',
  '..##.##..',
  '.#H#####.',
  '.######M.',
  '.S####MS.',
  '..S##MS..',
  '...SMS...',
  '....S....',
  '.........',
];

const hex = h => [(h >> 16) & 255, (h >> 8) & 255, h & 255, 255];
const PAL = {
  normal: { '#': hex(0x48C83A), H: hex(0xCDF7BE), S: hex(0x2C8A22), M: hex(0x38A82C) },
  blink: { '#': hex(0xAEEEA0), H: hex(0xEAFCE4), S: hex(0x88CC7C), M: hex(0x9ADE8C) },
};

function write(name, rows, colours, halfOnly) {
  const px = Buffer.alloc(9 * 9 * 4);
  for (let y = 0; y < 9; y++) {
    for (let x = 0; x < 9; x++) {
      if (halfOnly && x > 4) continue;
      const c = colours[rows[y][x]];
      if (!c) continue;
      px.set(c, (y * 9 + x) * 4);
    }
  }
  png.write(path.join(OUT, name + '.png'), 9, 9, px);
  console.log('wrote', name);
}

write('container', CONTAINER, { O: hex(0x0C140C), '#': hex(0x223022) });
write('container_blinking', CONTAINER, { O: hex(0xFFFFFF), '#': hex(0x223022) });
write('full', HEART, PAL.normal, false);
write('half', HEART, PAL.normal, true);
write('full_blinking', HEART, PAL.blink, false);
write('half_blinking', HEART, PAL.blink, true);
