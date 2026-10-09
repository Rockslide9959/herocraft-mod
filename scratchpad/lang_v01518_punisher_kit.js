// v0.15.18: the Punisher's new kit -- R Target Designation / Shift+R Threat Assessment, G Brutal Strike / Shift+G Breach
// Kick, Z Frag Grenade / Shift+Z Warzone, X Tactical Roll / Shift+X Tactical Advance, C Smoke Screen / Shift+C
// Flashbang, N Tactical Satchel. Suppressive Fire, Adrenaline and the Explosive Charge are gone.
// Usage: node lang_v01518_punisher_kit.js <path to en_us.json>   (idempotent -- safe to re-run)
const fs = require('fs');
const f = process.argv[2];
const j = JSON.parse(fs.readFileSync(f, 'utf8'));

const DELETE = [
	'projecthero.guide.punisher.ability.arsenal',
	'projecthero.guide.punisher.ability.suppressive',
	'projecthero.guide.punisher.ability.adrenaline',
	'projecthero.guide.punisher.ability.c4',
	'hud.projecthero.punisher.adrenaline',
	'hud.projecthero.punisher.suppressive',
	'hud.projecthero.punisher.name.satchel',
	'hud.projecthero.punisher.name.suppressive',
	'hud.projecthero.punisher.name.adrenaline',
	'hud.projecthero.punisher.name.c4',
	'message.projecthero.punisher.need_rifle',
	'message.projecthero.punisher.suppressive_on',
	'message.projecthero.punisher.suppressive_cooldown',
	'message.projecthero.punisher.adrenaline_on',
	'message.projecthero.punisher.adrenaline_crash',
	'message.projecthero.punisher.c4_max',
	'message.projecthero.punisher.c4_no_surface',
	'message.projecthero.punisher.c4_placed',
	'message.projecthero.punisher.c4_none',
];

const REPLACE = {
	'projecthero.guide.punisher.ability.grenade': 'Frag Grenade -- hold Z to cook, release to throw. It sticks where it lands and goes off on its fuse; hold too long and it goes off in your hand. 12s cooldown.',
	'projecthero.guide.punisher.passives.body': 'Weapon Proficiency (a regenerating personal reserve of 3 magazines per gun, less recoil, faster handling), Faster Reloading (15%), Ballistic Expertise (tighter aim), Headshot Feedback, and No Mercy: +25% firearm damage to a non-boss hostile below 15% health (+10% to a boss).',
	'projecthero.guide.agent_venom.body': 'The Symbiote no longer purges the Punisher. Bond with one as the Punisher (or become the Punisher while bonded) and you are Agent Venom: press H to let the suit take hold, H again to retract it. You keep the Punisher kit; the suit adds three Symbiote extras on Sneak + X, Z and V (while suited they replace Tactical Advance and Warzone), shown in their own three boxes above the Punisher HUD.',
};

// new keys, each block inserted right after the key named first
const INSERT_AFTER = [
	['projecthero.guide.punisher.controls', {
		'projecthero.guide.punisher.controls.note': 'Shift = sneak. Every Shift move has its own cooldown. With a gun in hand, tap R to reload and hold R for its moves.',
		'projecthero.guide.punisher.ability.mark': 'Target Designation -- mark the enemy you aim at (48 blocks) for 30s. It takes +30% damage from all your attacks and glows red for you alone. One mark at a time. 5s cooldown.',
		'projecthero.guide.punisher.ability.threat': 'Threat Assessment -- everything alive within 18 blocks glows for you alone for 10s. 12s cooldown.',
		'projecthero.guide.punisher.ability.strike': 'Brutal Strike -- 18 damage to the enemy in front of you (5 blocks) and a 2s stun: it can\'t move or attack. 2s cooldown.',
		'projecthero.guide.punisher.ability.kick': 'Breach Kick -- 25 damage, kicks the target about 10 blocks back and stuns it for 5s. 5s cooldown.',
	}],
	['projecthero.guide.punisher.ability.grenade', {
		'projecthero.guide.punisher.ability.warzone': 'Warzone -- hold for 5s to call artillery on the spot you aim at (up to 100 blocks). Missiles rain on a marked 15-block area for 10s: 30 damage each, no block damage, never you or your squad. 2 min cooldown.',
	}],
	['projecthero.guide.punisher.ability.roll', {
		'projecthero.guide.punisher.ability.advance': 'Tactical Advance -- Speed IV for 30s. 60s cooldown.',
		'projecthero.guide.punisher.ability.smoke': 'Smoke Screen -- a thick 5-block smoke cloud for 6s. Mobs inside lose their target and can\'t pick a new one; other players inside are blinded. 12s cooldown.',
		'projecthero.guide.punisher.ability.flashbang': 'Flashbang -- thrown, goes off after 1.5s. Everything within 6 blocks but you and your squad is blinded, slowed and dazed for 8s, and mobs lose their target. 12s cooldown.',
		'projecthero.guide.punisher.ability.satchel': 'Tactical Satchel -- a 9-slot bag that rides with you through death, power changes and dimensions. Opens only while you wear the full Punisher tactical armour.',
	}],
	['hud.projecthero.punisher.training', {
		'hud.projecthero.punisher.marked': 'MARKED %ss',
		'hud.projecthero.punisher.warzone_calling': 'CALLING WARZONE',
		'hud.projecthero.punisher.warzone_inbound': 'WARZONE INBOUND',
		'hud.projecthero.punisher.shift_key': 'Shift+%s  %s',
		'hud.projecthero.punisher.name.mark': 'Target Designation',
		'hud.projecthero.punisher.name.threat': 'Threat Assessment',
		'hud.projecthero.punisher.name.strike': 'Brutal Strike',
		'hud.projecthero.punisher.name.kick': 'Breach Kick',
	}],
	['hud.projecthero.punisher.name.grenade', {
		'hud.projecthero.punisher.name.warzone': 'Warzone',
	}],
	['hud.projecthero.punisher.name.roll', {
		'hud.projecthero.punisher.name.advance': 'Tactical Advance',
		'hud.projecthero.punisher.name.smoke': 'Smoke Screen',
		'hud.projecthero.punisher.name.flashbang': 'Flashbang',
		'hud.projecthero.punisher.name.weapon': 'Weapon Ability',
	}],
	['message.projecthero.punisher.grenade_overcooked', {
		'message.projecthero.punisher.mark_none': 'No target to mark',
		'message.projecthero.punisher.mark_set': 'Target marked',
		'message.projecthero.punisher.threats': '%s contacts',
		'message.projecthero.punisher.warzone_calling': 'Calling in Warzone -- hold...',
		'message.projecthero.punisher.warzone_cancelled': 'Warzone call cancelled',
		'message.projecthero.punisher.warzone_no_target': 'No target for the barrage',
		'message.projecthero.punisher.warzone_inbound': 'Warzone: barrage inbound!',
		'message.projecthero.punisher.warzone_cooldown': 'Warzone recharging (%ss)',
		'message.projecthero.punisher.advance_on': 'Tactical Advance',
	}],
	['entity.projecthero.c4_charge', {
		'entity.projecthero.flashbang': 'Flashbang',
	}],
];

for (const k of DELETE) {
	delete j[k];
}
for (const [k, v] of Object.entries(REPLACE)) {
	if (!(k in j)) throw new Error('missing key to replace: ' + k);
	j[k] = v;
}
const anchors = new Map(INSERT_AFTER);
for (const a of anchors.keys()) {
	if (!(a in j)) throw new Error('missing anchor: ' + a);
}
const added = new Set();
for (const block of anchors.values()) for (const k of Object.keys(block)) added.add(k);
const out = {};
for (const [k, v] of Object.entries(j)) {
	if (added.has(k)) continue; // re-running: re-placed after its anchor below
	out[k] = v;
	if (anchors.has(k)) Object.assign(out, anchors.get(k));
}
fs.writeFileSync(f, JSON.stringify(out, null, 2) + '\n');
console.log('deleted', DELETE.length, 'replaced', Object.keys(REPLACE).length, 'added', added.size);
