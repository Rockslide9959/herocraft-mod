// v0.17.7 Bifrost Travel cinematic: lang keys, key by key (en_us.json is CRLF and shared -- never rewrite the whole file).
// Run from the repo root: node scratchpad/v0177/lang_bifrost_travel.js
const fs = require('fs');
const p = 'src/main/resources/assets/projecthero/lang/en_us.json';
let s = fs.readFileSync(p, 'utf8');
const EOL = s.includes('\r\n') ? '\r\n' : '\n';
const SET = {
	'item.projecthero.stormbreaker.ability.bifrost3': '  waypoints, with everyone near you (60 s)',
	'message.projecthero.bifrost.fail.busy': 'The Bifrost is already carrying you',
	'message.projecthero.bifrost.travelled_with': 'The Bifrost carried you and %s companion(s) -- ready again in %s s',
	'message.projecthero.bifrost.travelled': 'The Bifrost carried you across -- ready again in %s s',
	'projecthero.guide.thor.stormbreaker.bifrost': 'Shift+X (Bifrost Travel, with Stormbreaker alone in hand): open the Bifrost menu. Type X, Y and Z (it starts on where you stand) and press Open Bifrost, or use one of three waypoints: name a slot and press Save here to store where you are standing, Go to travel there, and the cross to clear it. Waypoints are yours for good -- they survive relogging, dying and losing the axe. Pick a destination and you raise Stormbreaker to the sky: the rainbow bridge beams down on you, everyone in it rises 10 blocks over 3 seconds, and the Bifrost sets you all down gently at the destination, burning its rune circle into the ground at both ends. It takes every player within 4 blocks of you, every squadmate within 8 and their pets (not ones told to sit); sneak to stay behind. Hostile mobs never come along. Each lands safely -- on solid ground with room to stand, never in a wall, lava or the void. No distance limit inside the world border, but same dimension only. 1-minute cooldown of its own. With Mjolnir in your other hand the Bifrost will not open.',
};
const esc = (v) => JSON.stringify(v);
for (const [k, v] of Object.entries(SET)) {
	// [ \t]* not \s*: JS treats \r as a line start for ^, so \s* would swallow the \n and add a blank line
	const re = new RegExp('^([ \\t]*)"' + k.replace(/\./g, '\\.') + '": ".*?",?(\\r?)$', 'm');
	const m = s.match(re);
	if (m) {
		const line = m[0];
		const comma = /",\r?$/.test(line) ? ',' : '';
		s = s.replace(line, m[1] + '"' + k + '": ' + esc(v) + comma + (m[2] || ''));
		console.log('updated', k);
	} else {
		// insert after the empty_waypoint line
		const anchor = s.match(/^([ \t]*)"message\.projecthero\.bifrost\.fail\.empty_waypoint": ".*?",(\r?)$/m);
		if (!anchor) throw new Error('anchor missing');
		s = s.replace(anchor[0], anchor[0].replace(/\r$/, '') + EOL + anchor[1] + '"' + k + '": ' + esc(v) + ',' + (anchor[2] || ''));
		console.log('added', k);
	}
}
JSON.parse(s);
fs.writeFileSync(p, s);
