// Usage: node scratchpad/low_graphics_options.js <path to run/options.txt>
// Lowest-cost client settings for test clients (user, 2026-11-10: "open them with the lowest graphics settings possible").
// Particles stay on "All" (0) so mod VFX still show in films; everything else is the cheapest vanilla allows.
const fs = require('fs');
const f = process.argv[2] || 'run/options.txt';
const want = {
  graphicsMode: '0', renderDistance: '2', simulationDistance: '5', maxFps: '30', enableVsync: 'false',
  mipmapLevels: '0', biomeBlendRadius: '0', entityShadows: 'false', ao: 'false', renderClouds: '"false"',
  entityDistanceScaling: '0.5', menuBackgroundBlurriness: '0', soundCategory_master: '0.05', particles: '0',
};
let s = fs.existsSync(f) ? fs.readFileSync(f, 'utf8') : '';
const eol = s.includes('\r\n') ? '\r\n' : '\n';
for (const [k, v] of Object.entries(want)) {
  const re = new RegExp('^' + k + ':.*$', 'm');
  s = re.test(s) ? s.replace(re, k + ':' + v) : s + (s && !s.endsWith(eol) ? eol : '') + k + ':' + v + eol;
}
fs.writeFileSync(f, s);
console.log('low graphics set in', f);
