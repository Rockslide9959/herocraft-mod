// v0.17.1 Erasure + Zero Gravity revamp: the lang changes. Existing keys are rewritten IN PLACE (same line); new keys go in ONE
// contiguous block per Quirk, right after that Quirk's last existing key (so parallel kit builds merge cleanly). Idempotent.
//   node scratchpad/lang_erasure_zerogravity.js
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');

const ER = 'projecthero.quirk.ability.quirk_erasure_';
const ZG = 'projecthero.quirk.ability.quirk_zero_gravity_';

const update = {
	// ---- Erasure
	'projecthero.quirk.ultimate.erasure.desc': 'Goggles snap down: everything in a 20-block cone is erased for 6 s (bosses 4 s), then you chain through up to six of them -- zip, kick (10), bind -- and leave them all strung up for 2 s. After it your eyes are spent (Dry Eyes 100).',
	'projecthero.guide.quirk.erasure.body': 'Stare at someone and their power stops -- and anything you erase is Exposed: +20% damage from every hit, no sprinting, no jump boost, so even plain mobs fall fast. Fight with the capture scarf -- bind, string up, pull, swing, spin a foe into others, tie up a crowd, rip weapons away -- and with close-quarters martial arts.',
	'projecthero.guide.quirk.erasure.meter.body': 'Dry Eyes fills slowly while you stare (V) and while you hold Reel Lock, and drains fast when you blink (let go). The goggles slow it.',
	'projecthero.guide.quirk.erasure.overload.body': 'At 100 your eyes shut: the stare ends and can\'t start again until they recover (about 3 s). Your other moves still work. Right-click Eye Drops to open them at once.',
	'projecthero.guide.quirk.erasure.passive.body': 'The stare cancels EVERY power while it holds, players included: keys, flight (hero, Thor, Kryptonian, Nova, Lantern, Max Steel), wall-crawling and passives. On mobs: creepers can\'t swell, skeletons and pillagers can\'t shoot, endermen can\'t teleport, blazes and ghasts can\'t fire. Bosses lose their special moves but still fight. Tech (Iron Man, the Punisher) keeps working. Exposed: whatever you erase takes +20% damage, can\'t sprint and loses jump boosts, and every scarf hit on it ties it up for 1 s. Underground: sneaking in the dark, mobs notice you at half range. Catnap: Aizawa\'s Sleeping Bag (yellow wool and string) lets anyone sleep anywhere at night without moving their spawn, and you wake with Dry Eyes at 0. The full Eraser Head suit makes the Goggles perk passive.',
	'projecthero.quirk.erasure.intro': 'Hold V and stare to cancel a power, any power -- and leave the target Exposed (+20% damage taken). Fight with the capture scarf. Dry Eyes fills slowly while you stare.',
	[ER + 'bind.desc']: 'Whip the capture scarf round the target and tie it up: rooted 4 s. Keep R held to string it up (Reel Lock). Bosses break free.',
	[ER + 'lash.desc']: 'Yank the bound target (or the one you look at) in to you: 6 damage, and it lands in reach. On an ally it is a rescue: dragged out of lava, water or a fall.',
	[ER + 'combo.desc']: 'A fast four-strike combo: jab, elbow, knee, then a spinning kick that sends them flying (5, 5, 6, 10).',
	[ER + 'counter.desc']: 'A parry stance for 0.6 s: the next hit is caught, and a close attacker takes 10 and is tied up for 2 s.',
	[ER + 'swing.desc']: 'Hold X: the scarf latches a block and you swing like a grapple. Let go to fly on.',
	[ER + 'zip.desc']: 'Tap X: yank yourself to the block or creature you look at (6 on arrival), or up your swing line.',
	[ER + 'spin.desc']: 'Swing the bound (or looked-at) target round you twice on the scarf, smashing it into others (8 each), then fling it (10). Not bosses.',
	[ER + 'whip.desc']: 'A cracking lash of the scarf, 10 blocks: 7 damage and a stagger.',
	[ER + 'goggles.desc']: 'Shift+V toggle: night vision, powered players and special mobs glow, your eyes can\'t be read, the stare locks on (it can\'t be dodged) and your eyes dry 25% slower.',
	[ER + 'erase.desc']: 'Hold and stare: the one you look at (24 blocks; 32 once Strained) loses its powers and is Exposed. Burning, it holds two at once but burns you. Dries your eyes; let go to blink.',
	[ER + 'eye_drops.desc']: 'Right-click the item: Dry Eyes back to 0, shut eyes open.',
	// ---- Zero Gravity
	'projecthero.quirk.ultimate.zero_gravity.desc': 'Fingertips spread: every mob, loose block and item within 16 floats up for 4 s (bosses are rooted and lifted 3 blocks). Then "Release!": it all crashes down -- a slam by height plus a meteor shower of the rubble. You are sick after (Nausea 100).',
	'projecthero.guide.quirk.zero_gravity.body': 'Touch something with all five fingertips and it becomes weightless: mobs, players, dropped items, even blocks -- each one wears your glowing pink fingertip prints and slowly turns. Bring your fingertips together to Release, and everything you floated comes crashing down. V is your utility key (Touch, Release, hold for Catch), X your movement key (the Zero-G Leap, the Weightless Push, hold for Float Self).',
	'projecthero.guide.quirk.zero_gravity.overload.body': 'At 100 everything you float drops, you get Nausea for 5 s and can\'t use moves, except Settle (Shift + hold V).',
	'projecthero.guide.quirk.zero_gravity.passive.body': 'Up to 5 creatures and 6 blocks float by touch at once (a quarter more once Queasy). Bosses can\'t be floated. Light on her feet: no fall damage below Queasy. Float pickup: dropped items near you drift to you. Crane: touch a placed block and Release sets it down where you point -- moved, never broken. Herding: animals you touch follow you for 20 s, then settle down softly. A lifted block\'s hole is filled back in exactly as it was the moment the block lands, and blocks are only taken with terrain damage and mobGriefing on.',
	'projecthero.quirk.zero_gravity.intro': 'Touch to make things weightless, Release to bring them down. Floating weight builds Nausea slowly; at full you get sick and everything drops.',
	[ZG + 'float_touch.desc']: 'Tap V: touch a mob, a dropped item or a block and it floats. Mobs take 2 damage and hang helpless; squadmates are just lifted; animals follow you. Bosses are immune.',
	[ZG + 'release.desc']: 'Tap Shift+V, fingertips together: everything you floated drops (fall damage from the height), a touched block is set down where you point (or smashes on a mob), a Meteor Shower cloud rains. Shift + hold V instead: Settle.',
	[ZG + 'meteor_shower.desc']: 'C: rip up the ground round you into a rubble cloud overhead that follows your aim. C again (or Release) rains it: 6 a piece, x1.3 when Pushing it.',
	[ZG + 'debris_shield.desc']: 'Shift+Z: rubble orbits you for 8 s and stops every arrow and projectile that comes near.',
	[ZG + 'zero_g_self.desc']: 'Hold X: you float and glide on low gravity as long as you hold it. Jump to rise, Sneak to sink, move keys to drift. Adds your own weight to Nausea.',
	[ZG + 'float_leap.desc']: 'Tap X: a huge, nearly weightless leap the way you look. No fall damage when you land.',
	[ZG + 'comet_toss.desc']: 'R: throw the floated thing nearest your aim hard at what you look at: 10 where it smashes, 4 round it. Nothing floated: a chunk of the ground tears up and is thrown.',
	[ZG + 'zero_gravity_slam.desc']: 'Shift+G: touch a mob and it shoots 8 blocks up, then you let go: it slams down for 8 plus its fall, 4 to anything next to it.',
	[ZG + 'gunhead_martial_arts.desc']: 'G: the Gunhead set -- palm strike, fingertip touch, arm-lock throw over your shoulder: 4, 4 and 8. The throw leaves it floating.',
	[ZG + 'zero_gravity_field.desc']: 'Z: for 4 s everything within 5 blocks of where you aim floats helplessly, mobs and items alike, then drops.',
	[ZG + 'catch.desc']: 'Hold V: everyone falling within 12 blocks, you too, is caught while you hold it: their fall stops and they float down safely. Saving a falling squadmate fills Plus Ultra.',
	[ZG + 'settle.desc']: 'Shift + hold V: let everything down safely, no fall damage, then kneel and breathe: Nausea drains fast while you stay still. Works while sick.',
	'hud.projecthero.zero_gravity.sick': 'SICK... Shift + hold V to Settle',
	'hud.projecthero.zero_gravity.cloud': 'Cloud overhead (%s): C again to rain',
};

const add = {
	// after the last Erasure key
	'item.projecthero.eye_drops.desc': {
		[ER + 'reel']: 'Reel Lock',
		[ER + 'reel.desc']: 'Hold R on a bound target: it is strung up on the scarf a block off the ground for as long as you hold, 2 damage a second. Dries your eyes while you hold it.',
		[ER + 'flip']: 'Evade Flip',
		[ER + 'flip.desc']: 'Shift+X: a backflip about five blocks away, with a moment of i-frames -- every hit in it is dodged.',
		[ER + 'multi_bind']: 'Multi-Bind',
		[ER + 'multi_bind.desc']: 'Shift+C: the scarf fans out across a cone in front of you and ties up to three targets for 2.5 s. Bosses break free.',
		'projecthero.quirk.combo.erasure.crack_yank': 'Crack and Yank',
		'projecthero.quirk.combo.erasure.crack_yank.desc': 'Scarf Whip, then Capture Pull on the same target within 1.2 s: a 1.4x harder yank.',
		'projecthero.guide.quirks.hold_key': ' Hold %s',
		'item.projecthero.sleeping_bag': 'Aizawa\'s Sleeping Bag',
		'item.projecthero.sleeping_bag.desc': 'Right-click at night to sleep where you stand. Skips the night like a bed but never moves your spawn. Eraser Head wakes with fresh eyes.',
		'message.projecthero.sleeping_bag.cant': 'You can\'t sleep right now.',
		'message.projecthero.sleeping_bag.not_here': 'You can\'t sleep in this dimension.',
		'message.projecthero.sleeping_bag.ground': 'Lie down on solid ground first.',
		'subtitles.projecthero.quirk_erasure_scarf_whip': 'Capture scarf whips',
		'subtitles.projecthero.quirk_erasure_scarf_snap': 'Capture scarf snaps taut',
		'subtitles.projecthero.quirk_erasure_glare': 'Erasure stare hums',
		'subtitles.projecthero.quirk_erasure_bind': 'Scarf winds tight',
		'subtitles.projecthero.quirk_erasure_goggles': 'Goggles click',
	},
	// after the last Zero Gravity key
	'hud.projecthero.zero_gravity.self': {
		[ZG + 'debris_barrage']: 'Debris Barrage',
		[ZG + 'debris_barrage.desc']: 'Shift+R: every piece of debris you have up -- the shield, the cloud, touched blocks -- fires along your aim like a shotgun, 4 each.',
		[ZG + 'weightless_push']: 'Weightless Push',
		[ZG + 'weightless_push.desc']: 'Shift+X: an air dash off nothing, about 8 blocks the way you look. Twice before you land.',
		[ZG + 'gravity_bomb']: 'Gravity Bomb',
		[ZG + 'gravity_bomb.desc']: 'Shift+C: a 5x3x5 slab of ground tears up and floats overhead, following your aim. Shift+C again (or after 5 s) drops it on your aim: 20 in radius 4. The ground is put back after.',
		'message.projecthero.zero_gravity.no_debris': 'No debris floating to fire.',
		'message.projecthero.zero_gravity.no_push': 'Land first to push off again.',
		'subtitles.projecthero.quirk_zerogravity_touch': 'Fingertips touch',
		'subtitles.projecthero.quirk_zerogravity_float': 'Something floats',
		'subtitles.projecthero.quirk_zerogravity_release': '"Release!"',
		'subtitles.projecthero.quirk_zerogravity_meteor': 'Rubble rumbles',
		'subtitles.projecthero.quirk_zerogravity_push': 'Weightless push',
	},
};

let text = fs.readFileSync(file, 'utf8');
const crlf = text.includes('\r\n');
let lines = text.replace(/\r\n/g, '\n').split('\n');
const esc = s => JSON.stringify(s);
const keyOf = l => { const m = /^\s*"((?:[^"\\]|\\.)*)"\s*:/.exec(l); return m ? JSON.parse('"' + m[1] + '"') : null; };
let changed = 0;
for (let i = 0; i < lines.length; i++) {
	const k = keyOf(lines[i]);
	if (k != null && k in update) {
		const comma = lines[i].trimEnd().endsWith(',');
		lines[i] = '  ' + esc(k) + ': ' + esc(update[k]) + (comma ? ',' : '');
		changed++;
		delete update[k];
	}
}
const missing = Object.keys(update);
if (missing.length) console.log('NOT FOUND (left out):', missing);
for (const [anchor, kv] of Object.entries(add)) {
	const have = new Set(lines.map(keyOf).filter(Boolean));
	const idx = lines.findIndex(l => keyOf(l) === anchor);
	if (idx < 0) { console.log('no anchor', anchor); process.exit(1); }
	const block = Object.entries(kv).filter(([k]) => !have.has(k)).map(([k, v]) => '  ' + esc(k) + ': ' + esc(v) + ',');
	if (!lines[idx].trimEnd().endsWith(',')) lines[idx] += ',';
	lines.splice(idx + 1, 0, ...block);
	changed += block.length;
}
let out = lines.join('\n');
JSON.parse(out);
if (crlf) out = out.replace(/\n/g, '\r\n');
fs.writeFileSync(file, out);
console.log('lang lines changed / added:', changed);
