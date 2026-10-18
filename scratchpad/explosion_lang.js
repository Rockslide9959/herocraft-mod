// v0.16.7: inserts the Explosion Quirk's lang block after its anchor (CRLF kept, JSON checked, no duplicates).
const fs = require('fs');
const file = process.argv[2];
const anchor = '"message.projecthero.quirk.overloaded.explosion"';
const P = 'projecthero.quirk.ability.quirk_explosion_';
const add = [
	['message.projecthero.explosion.need_sweat', 'Not enough sweat! Need %s.'],
	[P + 'ap_shot', 'AP Shot'],
	[P + 'ap_shot.desc', 'A focused blast from the palm. Explodes on the first thing it hits for 10 (32 blocks).'],
	[P + 'ap_auto_cannon', 'AP Shot: Auto-Cannon'],
	[P + 'ap_auto_cannon.desc', 'A rapid volley of 8 small AP shots, 4 each, following your aim.'],
	[P + 'blast_punch', 'Blast Punch'],
	[P + 'blast_punch.desc', 'A point-blank explosion from your fist: 14 and a heavy knockback.'],
	[P + 'uppercut', 'Explosion Uppercut'],
	[P + 'uppercut.desc', 'A blast from below that launches the target into the air for 12.'],
	[P + 'stun_grenade', 'Stun Grenade'],
	[P + 'stun_grenade.desc', 'A blinding flash: everything within 8 blocks is blinded 5 s, slowed and loses its target.'],
	[P + 'smoke_blast', 'Smoke Blast'],
	[P + 'smoke_blast.desc', 'A thick smoke screen for 8 s. Creatures inside are blinded and lose track; you are hidden in it.'],
	[P + 'blast_rush', 'Blast Rush'],
	[P + 'blast_rush.desc', 'Hold: explosions from your palms fly you. W/S along your look, A/D sideways, Space/Sneak up/down, sprint for more.'],
	[P + 'blast_dash', 'Blast Dash'],
	[P + 'blast_dash.desc', 'One burst throws you about 12 blocks the way you look.'],
	[P + 'howitzer_impact', 'Howitzer Impact'],
	[P + 'howitzer_impact.desc', 'Spin into a tornado of blasts, fly at the target and detonate: 30 in a huge radius. Needs 30 Sweat.'],
	[P + 'max_shot', 'Max Shot'],
	[P + 'max_shot.desc', 'Charge both palms, then fire a giant cannon blast down your aim: 26 to all in its path. All your Sweat (40+).'],
	[P + 'cluster', 'Cluster'],
	[P + 'cluster.desc', 'Two rings of small explosions burst around you: 9 to everything close.'],
	[P + 'explosive_counter', 'Explosive Counter'],
	[P + 'explosive_counter.desc', 'For 2 s, the next melee hit on you is blocked and blasts the attacker for 14.'],
];
let text = fs.readFileSync(file, 'utf8');
const nl = text.includes('\r\n') ? '\r\n' : '\n';
const lines = text.split(nl);
const i = lines.findIndex(l => l.trim().startsWith(anchor));
if (i < 0) throw new Error('anchor not found');
const have = new Set(Object.keys(JSON.parse(text)));
const block = add.filter(([k]) => !have.has(k)).map(([k, v]) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',');
if (!lines[i].trim().endsWith(',')) throw new Error('anchor is the last line');
lines.splice(i + 1, 0, ...block);
const out = lines.join(nl);
JSON.parse(out);
fs.writeFileSync(file, out);
console.log('added', block.length);
