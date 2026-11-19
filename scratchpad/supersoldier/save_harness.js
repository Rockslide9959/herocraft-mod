// Copies the temporary CapSuitHarness (+ its camera mixin) into scratchpad/CapSuitHarness.v0175.java.txt before they are removed from source.
//   node scratchpad/supersoldier/save_harness.js
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..', '..');
const c = path.join(root, 'src', 'client', 'java', 'com', 'projecthero', 'mod', 'client');
const head = [
	'// TEMP-HARNESS copy (v0.17.5 Captain America Battle Suit). To use: put CapSuitHarness.java back in src/client/java/com/projecthero/mod/client/,',
	'// add "com.projecthero.mod.client.CapSuitHarness" to the client entrypoints in src/main/resources/fabric.mod.json, put the camera mixin below in',
	'// src/client/java/com/projecthero/mod/client/mixin/CapSuitHarnessCameraMixin.java and list it in projecthero.client.mixins.json, write',
	'// run/capsuit.flag = "capsuit_out|after", run node scratchpad/low_graphics_options.js run/options.txt, ./gradlew runClient;',
	'// then node scratchpad/supersoldier/cap_suit_films.js. Remove all of it again afterwards.',
	'',
	'// ===== CapSuitHarness.java =====',
].join('\r\n');
const a = fs.readFileSync(path.join(c, 'CapSuitHarness.java'), 'utf8');
const b = fs.readFileSync(path.join(c, 'mixin', 'CapSuitHarnessCameraMixin.java'), 'utf8');
const out = (head + '\r\n' + a + '\r\n\r\n// ===== mixin/CapSuitHarnessCameraMixin.java =====\r\n' + b).replace(/\r?\n/g, '\r\n');
fs.writeFileSync(path.join(root, 'scratchpad', 'CapSuitHarness.v0175.java.txt'), out);
console.log('saved', out.split('\r\n').length, 'lines');
