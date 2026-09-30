// v0.14.4 Max Steel / Green Lantern HUD lang. Idempotent: set() replaces or inserts, so it can be re-run after a merge.
// Run from the repo root: node scratchpad/lang_v0144_maxsteel_gl.js
const fs = require('fs');
const F = 'src/main/resources/assets/projecthero/lang/en_us.json';
let lines = fs.readFileSync(F, 'utf8').split('\n');
const q = (s) => JSON.stringify(s);
function idx(key) { return lines.findIndex((l) => l.startsWith('  ' + q(key) + ':')); }
function set(key, value, after) {
	const line = '  ' + q(key) + ': ' + q(value) + ',';
	const i = idx(key);
	if (i >= 0) { lines[i] = line; return; }
	const a = idx(after);
	if (a < 0) throw new Error('anchor missing: ' + after);
	lines.splice(a + 1, 0, line);
}

// ---- Max Steel HUD: unsuited shows "MODE Normal" instead of the "H  Go Turbo" hint
set('hud.projecthero.max_steel.mode_label_normal', 'MODE', 'hud.projecthero.max_steel.mode_label');
set('hud.projecthero.max_steel.mode_row.normal', 'Normal', 'hud.projecthero.max_steel.mode_label_normal');

// ---- Max Steel guide / power info
set('projecthero.guide.max_steel.energy.body', 'A pool of 250 that regenerates on its own, suited or not -- 10/second out of combat, 5/second in combat (a full refill takes 25 seconds), and not at all while a Turbo Mode is draining it or the Turbo Cannon is charging. If a Turbo Mode drains it to 0 you overload: you drop to Base Mode and cannot use any ability until natural regen brings T.U.R.B.O. Energy back up to 150. Turbo Modes have no time limit -- this pool is the only clock on them.', 'projecthero.guide.max_steel.energy');
set('projecthero.guide.max_steel.controls', 'Press H to Go Turbo, and H again to power down (not while IN COMBAT). You can also go straight into a Turbo Mode from Normal form: G, X, Z or V while unsuited forms the suit directly as that mode (Strength, Speed, Flight or Stealth) and switches it on the moment it settles -- no stop in Base first. R and C need the suit on. Shift+H while suited opens or seals the helmet; hold Left Alt to see every move name beside the HUD. The six abilities:', 'projecthero.guide.max_steel.controls');
set('projecthero.guide.max_steel.hud.body', "Bottom-right while suited: the six ability keys (cooldowns drain up each box, the active mode's box lights up), MAX STEEL with IN COMBAT / READY, the Turbo Mode you are in, then thin hairline bars: T.U.R.B.O. Energy with its percentage, the Turbo Cannon (charge %, lock status, or its recharge), and the Turbo Blast charge while you hold R. In Normal form a smaller version shows MAX STEEL, MODE Normal and your T.U.R.B.O. Energy, which keeps refilling out of the suit too.", 'projecthero.guide.max_steel.hud');
set('projecthero.guide.max_steel.ability.turbo_strength', 'Reconfigure into the heavy Strength form -- pauldrons, gauntlets, a chest plate and heavy boots materialise over the suit, 20% larger. +8 unarmed damage but slower to move and swing. Crouch to brace and block 50% of incoming damage. Sprint into a melee hit for a Heavy Punch shockwave. Press G again for a Turbo Slam -- 15 damage to everything within 5 blocks. Shift+G returns to Base. Pressed in Normal form, the suit builds straight into the Strength form.', 'projecthero.guide.max_steel.ability.turbo_strength');
set('projecthero.guide.max_steel.ability.turbo_speed', 'Reconfigure into the sleek Speed form -- a helmet crest, forearm blades and calf fins. Very high, controllable ground speed with automatic step-up. Tap X again for a short Turbo Dash that hits the first thing in front of you; Shift+X returns to Base. Pressed in Normal form, the suit builds straight into the Speed form.', 'projecthero.guide.max_steel.ability.turbo_speed');
set('projecthero.guide.max_steel.ability.turbo_flight', 'Reconfigure into the Flight form -- swept-back wings on a thruster pack, helmet fins and ankle jets -- and take off, an energy trail streaming behind you. Movement keys steer, jump climbs, sneak drops, sprint boosts. Costs more the faster you go. Pressed in Normal form, the suit builds straight into the Flight form and you lift off as it settles.', 'projecthero.guide.max_steel.ability.turbo_flight');
set('projecthero.guide.max_steel.ability.turbo_stealth', 'Near-total invisibility -- the suit vanishes and nearby hostiles lose track of you. Breaks the moment you attack or take a real hit, then goes on a 12-second cooldown; the suit forms back over you pixel by pixel. Pressed in Normal form, the suit forms and then fades straight into Stealth.', 'projecthero.guide.max_steel.ability.turbo_stealth');
set('projecthero.guide.max_steel.suit.body', "Steel's nanotech builds the suit one pixel at a time: it spreads out from the T.U.R.B.O. core in your chest -- a bright band of nanites sweeping over you, undersuit first and armour plates last -- while you clench a fist to the core and then throw your arms wide. Powering down runs it backwards, the suit drawing back into your chest from your hands, feet and head. Every Turbo Mode rebuilds it the same way: the new form (Strength, Speed or Flight) rematerialises over the old one from the chest out, extra armour and all. Going straight into a mode from Normal form builds that form in one pass.", 'projecthero.guide.max_steel.suit');

fs.writeFileSync(F, lines.join('\n'));
JSON.parse(fs.readFileSync(F, 'utf8'));
console.log('ok');
