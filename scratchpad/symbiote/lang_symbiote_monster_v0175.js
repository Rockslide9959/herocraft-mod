// v0.17.5 Symbiote Monster form (Shift+Z ultimate): en_us keys, key by key (re-runnable; keeps the file's line endings).
//   node scratchpad/symbiote/lang_symbiote_monster_v0175.js
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
let text = fs.readFileSync(FILE, 'utf8');
const EOL = text.includes('\r\n') ? '\r\n' : '\n';
const esc = (s) => JSON.stringify(s);

/** Sets {@code key} to {@code value}: in place when it exists, else on a new line after {@code after}. */
function set(key, value, after) {
	const re = new RegExp('^(\\s*)' + esc(key).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ':\\s*".*?(?<!\\\\)"(,?)\\s*$', 'm');
	const m = text.match(re);
	if (m) {
		text = text.replace(re, `${m[1]}${esc(key)}: ${esc(value)}${m[2]}`);
		return;
	}
	const anchorRe = new RegExp('^(\\s*)' + esc(after).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ':.*$', 'm');
	const a = text.match(anchorRe);
	if (!a) throw new Error('no anchor ' + after);
	let line = a[0].replace(/\r$/, '');
	const hasComma = /,\s*$/.test(line);
	const newLine = `${a[1]}${esc(key)}: ${esc(value)}` + (hasComma ? ',' : '');
	const fixed = hasComma ? line : line + ',';
	text = text.replace(a[0], fixed + (a[0].endsWith('\r') ? '\r' : '') + EOL + newLine + (a[0].endsWith('\r') ? '\r' : ''));
}
function edit(key, from, to) {
	const re = new RegExp('^(\\s*' + esc(key).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ':\\s*)(".*?(?<!\\\\)")', 'm');
	const m = text.match(re);
	if (!m) throw new Error('no key ' + key);
	const v = JSON.parse(m[2]);
	if (!v.includes(from)) {
		if (v.includes(to)) return;
		throw new Error(key + ' lacks ' + from);
	}
	text = text.replace(re, m[1] + esc(v.split(from).join(to)));
}

const M = 'message.projecthero.symbiote.monster.';
let prev = 'message.projecthero.symbiote.onslaught_interrupted';
for (const [k, v] of [
	['charging', 'The Symbiote is rising -- keep holding...'],
	['cancelled', 'Transformation abandoned'],
	['cooldown', 'The Monster Form is recovering -- %s'],
	['no_biomass', 'The Symbiote needs %s Biomass to become the monster'],
	['no_room', 'No room to transform -- the monster needs 7 blocks of space above you'],
	['transforming', 'The cocoon closes around you...'],
	['unleashed', 'WE ARE THE MONSTER'],
	['fading', 'The monster is weakening -- 5 seconds left'],
	['ended', 'The Symbiote settles back into the suit'],
	['lost_suit', 'The monster collapses back into goo'],
	['nothing_to_eat', 'Nothing in reach to devour'],
]) { set(M + k, v, prev); prev = M + k; }

const H = 'hud.projecthero.symbiote.monster.';
prev = 'hud.projecthero.symbiote.hp';
for (const [k, v] of [['title', 'Symbiote Monster'], ['timer', 'Monster'], ['transforming', 'Cocoon'], ['melting', 'Melting']]) {
	set(H + k, v, prev);
	prev = H + k;
}

const A = 'projecthero.symbiote.ability.';
prev = 'projecthero.symbiote.ability.onslaught.desc';
for (const [k, name, desc] of [
	['monster_form', 'Monster Form', 'The ultimate. Sneak and hold Z for 3 seconds (rooted, the Symbiote roaring): tendrils erupt from the ground round your feet, spiral up round you faster and faster and weave a cocoon that swells to 7 blocks, beats like a heart and bursts -- you are a hulking, hunchbacked symbiote monster for 40 seconds, in your suit colours. +100% max health, 50% less damage taken, no knockback, a bigger jump, longer reach, and six monster moves on R G X Z C V; Sneak+Z ends it early. Costs 50 Biomass; let go before 3 seconds to cancel for free. 4-minute cooldown, from when the form ends.'],
	['monster_swipe', 'Claw Swipe', 'Monster R: a huge raking swipe of the claws across a 120-degree arc 4.5 blocks out -- 24 damage and a hard knockback. 1-second cooldown.'],
	['monster_pound', 'Ground Pound', 'Monster G: both fists raised overhead and brought down -- everything within 6 blocks takes 20 damage and is thrown into the air. 6-second cooldown.'],
	['monster_leap', 'Leap Smash', 'Monster X: a crouch and a colossal pounce up to about 14 blocks the way you look; the landing smashes everything within 4 blocks for 22 damage. No fall damage. 5-second cooldown.'],
	['monster_roar', 'Monster Roar', 'Monster Z: a deafening roar down a 10-block cone -- 8 damage, Slowness III and Weakness II for 4 seconds, and they are blown back. 10-second cooldown.'],
	['monster_devour', 'Devour Whole', 'Monster C: tendrils seize the nearest mob within 5 blocks in front of you and drag it into the jaws -- eaten whole at any health: +30 Biomass, +3 hearts. Players are never eaten; a boss takes a 35-damage bite instead. 8-second cooldown.'],
	['monster_pull', 'Tendril Pull', 'Monster V: tendrils lash out across an 8-block cone and yank everything in it to your feet (4 damage). Bosses are not moved. 8-second cooldown.'],
	['monster_end', 'End Form', 'Monster Sneak+Z: end the Monster Form early -- the monster melts back down into the suit (the 4-minute cooldown starts).'],
]) {
	set(A + k, name, prev);
	set(A + k + '.desc', desc, A + k);
	prev = A + k + '.desc';
}

const G = 'projecthero.guide.symbiote.s.';
set(G + 'move.monster_form', 'Hold 3 seconds (rooted): a cocoon of tendrils grows to 7 blocks and bursts -- you are the monster for 40 s (+100% health, half damage, no knockback). 50 Biomass; let go early to cancel for free.', G + 'move.onslaught');
set(G + 'monster', 'Monster form (Sneak + hold Z)', G + 'move.monster_form');
set(G + 'monster.body', 'For 40 seconds you are a 7-block, hunchbacked symbiote monster in your suit colours -- the face, the jaws and the tongue, the emblem on its chest. Its health is doubled, it takes half damage, nothing knocks it back, it jumps higher and reaches further, and the normal moves are replaced by these. The HUD shows the time left. It melts back into the suit at the end, on death, in another dimension or if the suit comes off.', G + 'monster');
prev = G + 'monster.body';
for (const [k, v] of [
	['monster_swipe', '24 damage across a 120-degree arc 4.5 blocks out, with knockback.'],
	['monster_pound', '20 damage to everything within 6 blocks, thrown into the air.'],
	['monster_leap', 'A pounce up to ~14 blocks the way you look; 22 damage within 4 blocks of the landing.'],
	['monster_roar', '10-block cone: 8 damage, Slowness III and Weakness II for 4 s, blown back.'],
	['monster_devour', 'Seize a mob within 5 blocks and eat it whole (any health): +30 Biomass, +3 hearts. Bosses take a 35-damage bite; players are never eaten.'],
	['monster_pull', 'Yank everything in an 8-block cone to your feet (4 damage).'],
	['monster_end', 'End the form early (it melts back into the suit).'],
]) { set(G + 'move.' + k, v, prev); prev = G + 'move.' + k; }

// the Onslaught is gone from the keys: everything that pointed players at it now points at the Monster Form
edit('projecthero.jei.symbiote_host_moves', 'Onslaught (hold)', 'Monster Form (hold 3 s)');
edit('projecthero.guide.symbiote.passive.squad', 'the Onslaught', 'the Monster Form');
set('message.projecthero.symbiote.talk.surrounded_onslaught', 'Too many. Let me OUT. Shift. Hold Z.', 'message.projecthero.symbiote.talk.surrounded_onslaught');
set('message.projecthero.symbiote.talk.onslaught_now', 'Let us become the monster. Now.', 'message.projecthero.symbiote.talk.onslaught_now');
set('message.projecthero.symbiote.talk.clustered_onslaught', 'They are clustered. Let the monster out.', 'message.projecthero.symbiote.talk.clustered_onslaught');

fs.writeFileSync(FILE, text);
JSON.parse(text);
console.log('en_us: Monster form keys written');
