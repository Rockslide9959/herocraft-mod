// TEMP: hooks the land-mammal filming harness (LandHqHarness) in, or out with "off".
// Usage: node scratchpad/beastboy/land_harness_hook.js <worktree root> [off]
// Then: node scratchpad/low_graphics_options.js run/options.txt, write run/landhq.flag (optional shot filter),
// BEASTART_OUT=<frames dir> ./gradlew runClient, then sh scratchpad/beastboy/land_films.sh <frames dir>/screenshots
const fs = require('fs');
const W = process.argv[2];
const off = process.argv[3] === 'off';
const dst = W + '/src/client/java/com/projecthero/mod/client/LandHqHarness.java';
const cl = W + '/src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
let c = fs.readFileSync(cl, 'utf8');
const nl = c.includes('\r\n') ? '\r\n' : '\n';
const anchor = '\t\tModKeyBindings.initialize();';
const hook = anchor + nl + '\t\tLandHqHarness.init(); // TEMP-HARNESS';
if (off) {
  c = c.replace(hook, anchor);
  if (fs.existsSync(dst)) fs.unlinkSync(dst);
} else {
  fs.copyFileSync(W + '/scratchpad/beastboy/LandHqHarness.v0173.java.txt', dst);
  if (!c.includes('LandHqHarness')) {
    if (!c.includes(anchor)) throw new Error('hook anchor not found');
    c = c.replace(anchor, hook);
  }
}
fs.writeFileSync(cl, c);
console.log(off ? 'harness off' : 'harness on');
