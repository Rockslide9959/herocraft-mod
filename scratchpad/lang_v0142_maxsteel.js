// v0.14.2 Max Steel lang: new HUD/message keys + rewritten guide text. Idempotent: set() replaces or inserts.
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
function del(key) { const i = idx(key); if (i >= 0) lines.splice(i, 1); }

// ---- messages
set('message.projecthero.max_steel.press_h', 'Press H to Go Turbo.', 'message.projecthero.max_steel.not_transformed');
set('message.projecthero.max_steel.not_transformed', 'Go Turbo first (H).', 'message.projecthero.max_steel.not_transformed');
set('message.projecthero.max_steel.cannon_full', 'TURBO CANNON -- FULL CHARGE', 'message.projecthero.max_steel.press_h');
set('message.projecthero.max_steel.cannon_too_short', 'The Turbo Cannon folds away -- hold C longer to fire.', 'message.projecthero.max_steel.cannon_full');

// ---- HUD
del('hud.projecthero.max_steel.cannon_charge');
set('hud.projecthero.max_steel.title', 'MAX STEEL', 'hud.projecthero.max_steel.turbo');
set('hud.projecthero.max_steel.press_h', 'H  Go Turbo', 'hud.projecthero.max_steel.title');
set('hud.projecthero.max_steel.key_h', 'H  Go Turbo / Power Down', 'hud.projecthero.max_steel.press_h');
set('hud.projecthero.max_steel.turbo_pct', 'T.U.R.B.O. %s%%', 'hud.projecthero.max_steel.key_h');
set('hud.projecthero.max_steel.mode_row.base', 'Base', 'hud.projecthero.max_steel.turbo_pct');
set('hud.projecthero.max_steel.mode_row.flight', 'Flight', 'hud.projecthero.max_steel.mode_row.base');
set('hud.projecthero.max_steel.mode_row.strength', 'Strength', 'hud.projecthero.max_steel.mode_row.flight');
set('hud.projecthero.max_steel.mode_row.speed', 'Speed', 'hud.projecthero.max_steel.mode_row.strength');
set('hud.projecthero.max_steel.mode_row.stealth', 'Stealth', 'hud.projecthero.max_steel.mode_row.speed');
set('hud.projecthero.max_steel.cannon_charging', 'Cannon %s%%', 'hud.projecthero.max_steel.mode_row.stealth');
set('hud.projecthero.max_steel.cannon_full', 'Cannon FULL', 'hud.projecthero.max_steel.cannon_charging');
set('hud.projecthero.max_steel.cannon_locked', 'LOCKED', 'hud.projecthero.max_steel.cannon_full');
set('hud.projecthero.max_steel.cannon_free', 'NO LOCK', 'hud.projecthero.max_steel.cannon_locked');
set('hud.projecthero.max_steel.cannon_recharging', 'Cannon recharging %ss', 'hud.projecthero.max_steel.cannon_free');
set('hud.projecthero.max_steel.cannon_ready', 'Turbo Cannon READY', 'hud.projecthero.max_steel.cannon_recharging');
set('hud.projecthero.max_steel.blast_charging', 'Blast %s%%  stage %s', 'hud.projecthero.max_steel.cannon_ready');
set('hud.projecthero.max_steel.blast_full', 'Blast OVERCHARGED', 'hud.projecthero.max_steel.blast_charging');
set('hud.projecthero.max_steel.going_turbo', 'Going Turbo %s%%', 'hud.projecthero.max_steel.blast_full');
set('hud.projecthero.max_steel.powering_down', 'Powering Down %s%%', 'hud.projecthero.max_steel.going_turbo');

// ---- entity
set('entity.projecthero.turbo_cannon_beam', 'Turbo Cannon Beam', 'hud.projecthero.max_steel.powering_down');

// ---- guide
set('projecthero.guide.max_steel.controls', 'Press H to Go Turbo, and H again to power down (not while IN COMBAT). H is the only transform key -- the ability keys do nothing until you are suited. Shift+H while suited opens or seals the helmet; hold Left Alt to see every move name beside the HUD. The six abilities:', 'projecthero.guide.max_steel.controls');
set('projecthero.guide.max_steel.ability.turbo_blast', 'Tap for a fast T.U.R.B.O. bolt (15 damage). Hold to charge: a ball of energy forms in your fist and grows through three stages over 2 seconds (a chime marks each), your arm raises to aim, and the bolt grows with it -- up to 34 damage, and a full charge detonates for 17 more on everything within 3.5 blocks.', 'projecthero.guide.max_steel.ability.turbo_blast');
set('projecthero.guide.max_steel.ability.turbo_strength', 'Reconfigure into the heavy Strength form -- pauldrons, gauntlets, a chest plate and heavy boots materialise over the suit, 20% larger. +8 unarmed damage but slower to move and swing. Crouch to brace and block 50% of incoming damage. Sprint into a melee hit for a Heavy Punch shockwave. Press G again for a Turbo Slam -- 15 damage to everything within 5 blocks. Shift+G returns to Base.', 'projecthero.guide.max_steel.ability.turbo_strength');
set('projecthero.guide.max_steel.ability.turbo_speed', 'Reconfigure into the sleek Speed form -- a helmet crest, forearm blades and calf fins. Very high, controllable ground speed with automatic step-up. Tap X again for a short Turbo Dash that hits the first thing in front of you; Shift+X returns to Base.', 'projecthero.guide.max_steel.ability.turbo_speed');
set('projecthero.guide.max_steel.ability.turbo_flight', 'Reconfigure into the Flight form -- swept-back wings on a thruster pack, helmet fins and ankle jets -- and take off, an energy trail streaming behind you. Movement keys steer, jump climbs, sneak drops, sprint boosts. Costs more the faster you go.', 'projecthero.guide.max_steel.ability.turbo_flight');
set('projecthero.guide.max_steel.ability.turbo_stealth', 'Near-total invisibility -- the suit vanishes and nearby hostiles lose track of you. Breaks the moment you attack or take a real hit, then goes on a 12-second cooldown; the suit forms back over you pixel by pixel.', 'projecthero.guide.max_steel.ability.turbo_stealth');
set('projecthero.guide.max_steel.ability.turbo_cannon', 'Hold C: an arm cannon materialises over your right forearm and you brace side-on, aiming down the crosshair. While it charges (up to 3 seconds) it locks onto the enemy nearest your crosshair -- blue brackets close in around it. Release to fire a piercing beam at the lock (or exactly where you aim): 16 to 45 damage to everything along it, then a blast at the far end for almost half that again, over 2.5-5 blocks. Wider, hotter and costlier the longer you charge; it kicks you back unless you are in Strength. Let go too early and it just folds away, free. Works in every mode; 8-second recharge, shown on the HUD.', 'projecthero.guide.max_steel.ability.turbo_cannon');
set('projecthero.guide.max_steel.suit', 'The suit', 'projecthero.guide.max_steel.energy.body');
set('projecthero.guide.max_steel.suit.body', "Steel's nanotech builds the suit one pixel at a time: it spreads out from the T.U.R.B.O. core in your chest -- a bright band of nanites sweeping over you, undersuit first and armour plates last -- while you clench a fist to the core and then throw your arms wide. Powering down runs it backwards, the suit drawing back into your chest from your hands, feet and head. Every Turbo Mode rebuilds it the same way: the new form (Strength, Speed or Flight) rematerialises over the old one from the chest out, extra armour and all.", 'projecthero.guide.max_steel.suit');
set('projecthero.guide.max_steel.hud', 'The HUD', 'projecthero.guide.max_steel.suit.body');
set('projecthero.guide.max_steel.hud.body', "Bottom-right while suited: the six ability keys (cooldowns drain up each box, the active mode's box lights up), MAX STEEL with IN COMBAT / READY, the mode row -- Base, Flight, Strength, Speed, Stealth -- with the active one lit, then thin hairline bars: T.U.R.B.O. Energy with its percentage, the Turbo Cannon (charge %, lock status, or its recharge), and the Turbo Blast charge while you hold R.", 'projecthero.guide.max_steel.hud');

// ---- overview (v0.14.1 made every mutation eight abilities)
set('projecthero.guide.overview.body', 'Project Hero turns Minecraft into a superhero sandbox. Alongside iconic heroes like Thor, Iron Man, Spider-Man, Max Steel, the Punisher, Green Lantern, Wolverine, the Titan Shifter, All Might, Moon Knight and the Hulk, you can undergo experimental mutations to permanently gain one of 27 minor powers, each with eight abilities on a shared control scheme.', 'projecthero.guide.overview.body');

fs.writeFileSync(F, lines.join('\n'));
JSON.parse(fs.readFileSync(F, 'utf8'));
console.log('ok');
