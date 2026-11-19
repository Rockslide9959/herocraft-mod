// v0.17.5 Symbiote moveset v2: rewrites the Symbiote move keys whose meaning changed and inserts the new ones after an anchor,
// key by key (never the whole file). Keeps the file's line endings; JSON.parse checked before writing. Re-runnable.
const fs = require('fs');
const path = require('path');
const file = path.join(__dirname, '..', '..', 'src/main/resources/assets/projecthero/lang/en_us.json');
let text = fs.readFileSync(file, 'utf8');
const EOL = text.includes('\r\n') ? '\r\n' : '\n';

const replace = {
  'projecthero.symbiote.ability.tendril_strike.desc': "R (tap): a living tendril whips out of your hand along your aim, up to 30 blocks, and hits whatever it meets first for 15 damage and a solid knockback. It can miss, and blocks stop it. Every third strike in a row that finds something is a Tendril Slam. Hold R: Impaling Spear. Shift+R: Tendril Sweep.",
  'projecthero.symbiote.ability.spike_shot.desc': "G (tap): fire a living spike from your hand -- a real projectile that flies fast and almost flat, so it can miss. 14 damage and Wither V for 5 seconds. 4-second cooldown. Hold G: Spike Rain. Shift+G: Spike Fan.",
  'projecthero.symbiote.ability.leap.desc': "X: the Symbiote launches you the way you are looking, far enough to carry you 20 blocks, and you ram the first enemy in the way for 15 damage. 2-second cooldown. No fall damage until you land. In the air (2.5 blocks or more up), X is Dive Bomb instead.",
  'projecthero.symbiote.ability.barrage.desc': "Z (tap): a flurry of tendrils from alternating hands for over a second, each flying up to 30 blocks down your aim for 4 damage. It can miss. With the Blade form, Z is a wide slashing arc instead. Hold Z: Ground Eruption. Shift + hold Z: Symbiote Onslaught.",
  'projecthero.symbiote.ability.blade.desc': "The Blade form (V cycles to it): a curved living blade grows out of your hand -- +5 melee, swings 50% faster, bites past armour. It feeds on 0.3 Biomass a second and dissolves when the Biomass runs out. Nothing can be held in that hand while it is out.",
  'projecthero.symbiote.ability.spikes.desc': "The Thorns form (V cycles to it): living spikes bristle out of you -- attackers in melee take reflected damage, like Thorns IV.",
  'projecthero.guide.symbiote.s.move.tendril_strike': "Tap: a tendril whips 30 blocks down your aim for 15 damage and a knockback. It can miss, and blocks stop it.",
  'projecthero.guide.symbiote.s.move.spike_shot': "Tap: a living spike, fast and almost flat: 14 damage and Wither V for 5 seconds. Spikes burst out of your back and arms as it fires.",
  'projecthero.guide.symbiote.s.move.leap': "Launches you 20 blocks the way you look; the first enemy in your path takes 15. No fall damage on the landing.",
  'projecthero.guide.symbiote.s.move.barrage': "Tap: a second-long flurry of tendrils from both hands, 4 damage a hit, 30 blocks. In the Blade form it is a wide 8-damage slash instead.",
  'projecthero.guide.symbiote.s.move.blade': "Blade form: a curved blade grows from your hand -- +5 melee, 50% faster swings. Feeds on 0.3 Biomass a second; nothing else fits in that hand.",
  'projecthero.guide.symbiote.s.move.spikes': "Thorns form: spikes bristle out of your back and arms; anything that hits you in melee takes Thorns IV damage.",
  'projecthero.guide.symbiote.s.biomass.costs': "Costs: the Blade form 0.3 a second, Shield 0.5 a second, healing you about 2 a second, catching an arrow 3. Maw Bite wins back 12, a heavy bite 20, Devour 40.",
  'projecthero.guide.symbiote.s.tip.biomass': "Put the Blade and Shield away when you are not using them, so the Biomass can grow back -- or feed: bite things.",
};

const insertAfter = 'projecthero.symbiote.ability.spike_fan.desc';
const add = [
  ['projecthero.symbiote.ability.form', 'Form'],
  ['projecthero.symbiote.ability.form.desc', "V: change Form -- Normal, then Blade, then Thorns, then back to Normal. Blade: a living blade on your hand (+5 melee, faster swings, feeds on Biomass). Thorns: spikes that hurt anything hitting you in melee. With an item in your hand, Normal goes straight to Thorns. Shift+V: Symbiote Shield."],
  ['projecthero.symbiote.form.normal', 'Normal'],
  ['projecthero.symbiote.form.blade', 'Blade'],
  ['projecthero.symbiote.form.thorns', 'Thorns'],
  ['message.projecthero.symbiote.form', 'Form: %s'],
  ['message.projecthero.symbiote.form.blade_skipped', 'Your hand is full: Thorns form'],
  ['projecthero.symbiote.ability.tendril_slam', 'Tendril Slam'],
  ['projecthero.symbiote.ability.tendril_slam.desc', "Every third Tendril Strike in a row (each within 4 seconds) wraps the target, yanks it in and smashes it into the ground in front of you: 22 damage, Slowness II for 2 seconds, and 6 to anything else near the impact. Bosses are smashed where they stand."],
  ['projecthero.symbiote.ability.impaling_spear', 'Impaling Spear'],
  ['projecthero.symbiote.ability.impaling_spear.desc', "Hold R: a thick tendril spear grows in your hand. Let go to throw it 10 blocks down your aim: it pierces everything on the line for 14 damage, up to 26 if you hold it another second, and pins the first thing it hits in place for 1 second. 8-second cooldown."],
  ['projecthero.symbiote.ability.spike_rain', 'Spike Rain'],
  ['projecthero.symbiote.ability.spike_rain.desc', "Hold G: spikes burst up out of you, then rain down on the spot you are aiming at (up to 24 blocks away) for 3 seconds -- a 4-block circle, 3 damage and Wither I a spike; one spike in every volley falls on something in the circle. 14-second cooldown."],
  ['projecthero.symbiote.ability.dive_bomb', 'Dive Bomb'],
  ['projecthero.symbiote.ability.dive_bomb.desc', "X in the air (2.5 blocks or more up): the Symbiote drives you straight down into a slam. Everything within 4.5 blocks takes 12 damage, plus more the further you fell (up to 24), and is thrown up and out. No fall damage. 6-second cooldown."],
  ['projecthero.symbiote.ability.ground_eruption', 'Ground Eruption'],
  ['projecthero.symbiote.ability.ground_eruption.desc', "Hold Z: tendrils burst out of the ground in a line racing 12 blocks ahead of you. Everything they come up under takes 12 damage, is launched into the air and slowed for 3 seconds. 12-second cooldown."],
  ['projecthero.symbiote.ability.maw_bite', 'Maw Bite'],
  ['projecthero.symbiote.ability.maw_bite.desc', "C (tap): you snap forward at the nearest enemy in front of you (up to 5 blocks) and the Symbiote's jaws close on it: 18 damage, and it wins back 12 Biomass. 5-second cooldown. Hold C: Devour. Shift+C: Tendril Grab."],
  ['projecthero.symbiote.ability.devour', 'Devour'],
  ['projecthero.symbiote.ability.devour.desc', "Hold C on an enemy under 25% health: the tongue drags it into the maw and the Symbiote eats it -- it dies, you win back 40 Biomass and heal 3 hearts. Bosses are too big: they take a heavy 30-damage bite (20 Biomass). On anything else, or a player, it is a Maw Bite. 20-second cooldown."],
  ['message.projecthero.symbiote.devour_too_big', 'Too big to devour: a heavy bite instead'],
  ['message.projecthero.symbiote.devoured', 'Devoured: %s'],
  ['hud.projecthero.symbiote.move_list.air', 'In the air: %s'],
  ['projecthero.guide.symbiote.s.holds', 'Hold moves'],
  ['projecthero.guide.symbiote.s.holds.body', "R, G, Z and C each have a second move: tap the key and the normal move fires when you let go; hold it for under half a second and the hold move starts instead (the key's box fills while you hold). Shift moves still fire the moment you press."],
  ['projecthero.guide.symbiote.s.move.tendril_slam', "Every third Tendril Strike in a row yanks the target in and smashes it down in front of you: 22 damage, Slowness II, and 6 to anything near the impact."],
  ['projecthero.guide.symbiote.s.move.dive_bomb', "X in the air (2.5+ blocks up): straight down into a 4.5-block slam -- 12 damage, more the further you fell (up to 24) -- that throws everything up."],
  ['projecthero.guide.symbiote.s.move.maw_bite', "Snap forward at the nearest enemy (5 blocks) and bite: 18 damage, and 12 Biomass back."],
  ['projecthero.guide.symbiote.s.move.form', "Change Form: Normal, Blade, Thorns, and round again. The key's box lights while Blade or Thorns is up."],
  ['projecthero.guide.symbiote.s.move.impaling_spear', "Charge a tendril spear, let go to throw it: 10 blocks, pierces everything for 14-26 (longer hold, more damage), pins the first target for 1 second."],
  ['projecthero.guide.symbiote.s.move.spike_rain', "Spikes rain on the spot you aim at for 3 seconds: a 4-block circle, 3 damage and Wither I a spike; one spike in every volley falls on something in the circle."],
  ['projecthero.guide.symbiote.s.move.ground_eruption', "Tendrils burst out of the ground in a 12-block line: 12 damage, launched up, slowed for 3 seconds."],
  ['projecthero.guide.symbiote.s.move.devour', "On an enemy under 25% health: the tongue drags it in and the Symbiote eats it (40 Biomass, 3 hearts). Bosses take a heavy 30-damage bite instead; anything else gets a Maw Bite."],
  ['projecthero.guide.symbiote.s.passive.suit_melee', "Suit fists: with the suit on your punches reach 1 block further, and every third punch in a row knocks the target back."],
  ['projecthero.jei.symbiote_host_moves', "Moves with the suit on: R Tendril Strike (every 3rd in a row a Tendril Slam; hold: Impaling Spear), G Spike Shot (hold: Spike Rain), X Lunge (in the air: Dive Bomb), Z Tendril Barrage (hold: Ground Eruption), C Maw Bite (hold: Devour), V Form (Normal, Blade, Thorns). Shift: Sweep, Spike Fan, Grapple, Onslaught (hold), Shield, Tendril Grab."],
];

const esc = s => JSON.stringify(s);
for (const [k, v] of Object.entries(replace)) {
  const re = new RegExp('("' + k.replace(/\./g, '\\.') + '"\\s*:\\s*)"(?:[^"\\\\]|\\\\.)*"');
  if (!re.test(text)) throw new Error('missing key ' + k);
  text = text.replace(re, (m, p1) => p1 + esc(v));
}
const anchorRe = new RegExp('^.*"' + insertAfter.replace(/\./g, '\\.') + '"\\s*:.*$', 'm');
const m = text.match(anchorRe);
if (!m) throw new Error('missing anchor');
const lines = add.filter(([k]) => !text.includes('"' + k + '"')).map(([k, v]) => '  ' + esc(k) + ': ' + esc(v) + ',');
if (lines.length) {
  const anchorLine = m[0].replace(/\r$/, '');
  const fixed = anchorLine.endsWith(',') ? anchorLine : anchorLine + ',';
  text = text.replace(m[0], fixed + (m[0].endsWith('\r') ? '\r' : '') + EOL + lines.join(EOL).replace(/,$/, anchorLine.endsWith(',') ? ',' : ''));
}
JSON.parse(text);
fs.writeFileSync(file, text);
console.log('ok', Object.keys(replace).length, 'replaced,', lines.length, 'added');
