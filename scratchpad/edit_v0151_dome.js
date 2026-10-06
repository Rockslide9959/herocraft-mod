// v0.15.1: wire the GL dome renderer, drop the server particle outline. Run from the repo root.
const fs = require('fs');
const crlf = (f) => fs.writeFileSync(f, fs.readFileSync(f, 'utf8').replace(/\r?\n/g, '\r\n'));
crlf('src/client/java/com/projecthero/mod/client/greenlantern/GreenLanternDomeRenderer.java');

const c = 'src/client/java/com/projecthero/mod/client/greenlantern/GreenLanternClient.java';
let s = fs.readFileSync(c, 'utf8');
const m = s.match(/([ \t]*)(?:com\.projecthero\.mod\.client\.greenlantern\.)?GreenLanternShieldRenderer\.initialize\(\);[^\n]*\n/);
if (!m) throw new Error('shield renderer init not found');
s = s.replace(m[0], m[0] + m[1] + 'GreenLanternDomeRenderer.initialize(); // v0.15.1: hard-light dome model\r\n');
fs.writeFileSync(c, s);

const g = 'src/main/java/com/projecthero/mod/greenlantern/GreenLanternShield.java';
let t = fs.readFileSync(g, 'utf8').replace(/\r\n/g, '\n');
function rep(a, b) {
	if (t.split(a).length !== 2) throw new Error('no single match: ' + a);
	t = t.replace(a, b);
}
rep('\t\temitDomeOutline(player, level, 0.0);\n', '\t\t// v0.15.1: no particle outline -- the client draws the dome as a hard-light globe (GreenLanternDomeRenderer)\n');
rep('\t\tif (player.tickCount % DOME_OUTLINE_INTERVAL_TICKS == 0) {\n\t\t\temitDomeOutline(player, player.serverLevel(), radius);\n\t\t}\n', '');
fs.writeFileSync(g, t.replace(/\n/g, '\r\n'));
console.log('ok');
