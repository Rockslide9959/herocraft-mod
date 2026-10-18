// v0.16.7: inserts the Erasure kit's lang block right after its anchor key (CRLF kept, JSON checked, no duplicates).
const fs = require('fs');
const f = 'src/main/resources/assets/projecthero/lang/en_us.json';
let t = fs.readFileSync(f, 'utf8');
const anchor = '"message.projecthero.quirk.overloaded.erasure"';
const A = 'projecthero.quirk.ability.quirk_erasure_';
const keys = [
	[A + 'lash', 'Capture Lash'], [A + 'lash.desc', 'The scarf whips out: 3 damage and the target is yanked to you.'],
	[A + 'disarm', 'Capture Disarm'], [A + 'disarm.desc', "Yank the item out of a mob's hand and drop it at your feet."],
	[A + 'erase', 'Erase'], [A + 'erase.desc', 'Hold and stare: the one you look at (24 blocks) loses every power. Drains Dry Eyes.'],
	[A + 'glare', 'Erase Glare'], [A + 'glare.desc', 'Hold: erase everyone in front of you. Dry Eyes drain 3x faster.'],
	[A + 'swing', 'Capture Swing'], [A + 'swing.desc', 'Hold: the scarf hooks a block and you swing. Let go to fly on.'],
	[A + 'zip', 'Capture Zip'], [A + 'zip.desc', 'The scarf catches the creature you look at and reels you to it (4 damage).'],
	[A + 'bind', 'Capture Bind'], [A + 'bind.desc', 'Wrap the target in the scarf: rooted 4 s. Bosses break free.'],
	[A + 'hoist', 'Capture Hoist'], [A + 'hoist.desc', 'Bind the target and hang it up off the ground for 4 s.'],
	[A + 'slam', 'Bind Slam'], [A + 'slam.desc', 'Whip the bound (or looked-at) target up and slam it down: 8 damage.'],
	[A + 'double_bind', 'Double Bind'], [A + 'double_bind.desc', 'Bind two targets and smash them together: 7 damage each.'],
	[A + 'goggles', 'Goggles'], [A + 'goggles.desc', 'Toggle: night vision; powered players and special mobs glow.'],
	[A + 'eye_drops', 'Eye Drops'], [A + 'eye_drops.desc', 'Refill Dry Eyes at once, even mid-blink. 60 s cooldown.'],
	['message.projecthero.quirk.erasure.no_target', 'Nothing to catch.'],
	['message.projecthero.quirk.erasure.no_disarm', 'Nothing to take.'],
	['message.projecthero.quirk.erasure.boss_immune', 'Too strong to hold!'],
	['message.projecthero.quirk.erasure.no_hook', 'Nothing to hook.'],
	['message.projecthero.quirk.erasure.no_partner', 'No second target near it.'],
	['message.projecthero.quirk.erasure.drops', 'Eyes refreshed.'],
];
const json = JSON.parse(t);
for (const [k] of keys) if (k in json) throw new Error('duplicate ' + k);
const eol = t.includes('\r\n') ? '\r\n' : '\n';
const lines = t.split(eol);
const i = lines.findIndex(l => l.trimStart().startsWith(anchor));
if (i < 0) throw new Error('anchor');
const block = keys.map(([k, v]) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',');
// the anchor line may be the last entry (no trailing comma)
if (!lines[i].trimEnd().endsWith(',')) {
	lines[i] = lines[i] + ',';
	block[block.length - 1] = block[block.length - 1].slice(0, -1);
}
lines.splice(i + 1, 0, ...block);
t = lines.join(eol);
JSON.parse(t);
fs.writeFileSync(f, t);
console.log('inserted', keys.length, 'eol', JSON.stringify(eol));
