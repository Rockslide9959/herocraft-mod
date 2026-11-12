// TEMP: hooks the Ball Breaker candidates filming harness in, or out with "off".
// Usage: node scratchpad/spin/ball_breaker_harness_hook.js <worktree root> [off]
// Then: node scratchpad/low_graphics_options.js run/options.txt, write run/bb.flag, ./gradlew runClient,
// sh scratchpad/spin/ball_breaker_films.sh
const fs = require('fs');
const W = process.argv[2];
const off = process.argv[3] === 'off';
const dst = W + '/src/client/java/com/projecthero/mod/client/BallBreakerHarness.java';
const cl = W + '/src/client/java/com/projecthero/mod/client/ProjectHeroModClient.java';
let c = fs.readFileSync(cl, 'utf8');
const nl = c.includes('\r\n') ? '\r\n' : '\n';
const anchor = '\t\tModKeyBindings.initialize();';
const hook = anchor + nl + '\t\tBallBreakerHarness.init(); // TEMP-HARNESS';
if (off) {
	c = c.replace(hook, anchor);
	if (fs.existsSync(dst)) fs.unlinkSync(dst);
} else {
	fs.copyFileSync(W + '/scratchpad/spin/BallBreakerHarness.v0173.java.txt', dst);
	if (!c.includes('BallBreakerHarness')) {
		if (!c.includes(anchor)) throw new Error('hook anchor not found');
		c = c.replace(anchor, hook);
	}
}
fs.writeFileSync(cl, c);
console.log(off ? 'harness off' : 'harness on');
