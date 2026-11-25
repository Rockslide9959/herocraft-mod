// Packs the TEMP Bifrost harness (src copy) into scratchpad/BifrostHarness.v0177.java.txt before it is removed from src.
//   node scratchpad/v0177/pack_bifrost_harness.js
const fs = require('fs');
const head = [
	'// TEMP-HARNESS copy (v0.17.7 Stormbreaker clipping fix + Bifrost Travel cinematic). To use: put BifrostHarness.java back in',
	'// src/client/java/com/projecthero/mod/client/, add "com.projecthero.mod.client.BifrostHarness" to the client entrypoints in',
	'// src/main/resources/fabric.mod.json, put the mixin below in src/client/java/com/projecthero/mod/client/mixin/ and list it in',
	'// projecthero.client.mixins.json, copy the OLD stormbreaker.json / stormbreaker_thrown.json into run/bifrost_before_models/ (for',
	'// the before stills; leave the second flag part out to skip them), write run/bifrost.flag = "bifrost_out|bifrost_before_models",',
	'// node scratchpad/low_graphics_options.js run/options.txt, ./gradlew runClient; then node scratchpad/v0177/bifrost_films.js.',
	'// Remove it all again afterwards.',
	'',
	'// ===== BifrostHarness.java =====',
].join('\n');
const a = fs.readFileSync('src/client/java/com/projecthero/mod/client/BifrostHarness.java', 'utf8');
const b = fs.readFileSync('src/client/java/com/projecthero/mod/client/mixin/BifrostHarnessCameraMixin.java', 'utf8');
fs.writeFileSync('scratchpad/BifrostHarness.v0177.java.txt', head + '\n' + a + '\n// ===== mixin/BifrostHarnessCameraMixin.java =====\n' + b);
console.log('packed');
