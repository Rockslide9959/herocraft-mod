// v0.17.1 Explosion + Hellflame Quirks revamp: rewrites the two kits' lang lines in en_us.json in place. Existing keys keep
// their line; the kits' ability / message / hud blocks are rewritten as ONE contiguous block per Quirk (at the spot of the
// old block) so merges with the other Quirk builds stay easy; obsolete keys (Plus Ultra: Full Power, the old Howitzer
// Cluster move, the old Prominence beam) are removed.
//   node scratchpad/explosion_hellflame_lang_v0171.js
const fs = require('fs');
const path = require('path');
const FILE = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'projecthero', 'lang', 'en_us.json');
let lines = fs.readFileSync(FILE, 'utf8').split(/\r?\n/);
const keyOf = l => { const m = /^\s*"([^"]+)":/.exec(l); return m ? m[1] : null; };
const q = s => JSON.stringify(s);
const line = (k, v) => '  ' + q(k) + ': ' + q(v) + ',';
const find = k => lines.findIndex(l => keyOf(l) === k);

function set(k, v) {
	const i = find(k);
	if (i < 0) throw new Error('missing ' + k);
	lines[i] = line(k, v);
}
function remove(pred) {
	lines = lines.filter(l => { const k = keyOf(l); return !(k && pred(k)); });
}
/** Replaces every line whose key matches {@code pred} with {@code block} (at the first match). */
function block(pred, entries) {
	const first = lines.findIndex(l => { const k = keyOf(l); return k && pred(k); });
	if (first < 0) throw new Error('no block');
	remove(pred);
	lines.splice(first, 0, ...entries.map(([k, v]) => line(k, v)));
}

// ---------------------------------------------------------------- shared framework keys these two kits own
set('projecthero.quirk.ultimate.hellflame', 'Plus Ultra: Prominence Burn');
set('projecthero.quirk.ultimate.hellflame.desc', '1.5 s of ignition as a white-hot sun, then a comet charge up to 40 blocks along your aim: 20 to everything it burns through, and a 45 detonation (radius 8) at the end. A 10 s burnout follows.');
set('projecthero.quirk.ultimate.explosion', 'Howitzer Impact: Cluster');
set('projecthero.quirk.ultimate.explosion.desc', 'A spinning rise marks up to five foes within 20 blocks, then a corkscrew chain-dive blasts through each (15) and ends in a giant 40 explosion (radius 8). Your arms are shot afterwards (6 s, no blasts).');
// the combo routes (next to the framework's starter route)
remove(k => k === 'projecthero.quirk.combo.explosion.sky_shot' || k === 'projecthero.quirk.combo.explosion.sky_shot.desc'
	|| k.startsWith('projecthero.quirk.combo.hellflame.'));
{
	const i = find('projecthero.quirk.combo.explosion.stun_shot.desc');
	lines.splice(i + 1, 0,
		line('projecthero.quirk.combo.explosion.sky_shot', 'Sky Shot'),
		line('projecthero.quirk.combo.explosion.sky_shot.desc', 'Explosive Uppercut, then AP Shot within 1.5 s: the air shot hits 1.3x harder.'),
		line('projecthero.quirk.combo.hellflame.mesh_pillar', 'Caged Inferno'),
		line('projecthero.quirk.combo.hellflame.mesh_pillar.desc', 'Hell Spider: Mesh, then the Hellflame Pillar within 2 s: the pillar erupts under the trapped and hits 1.5x harder.'));
}
set('projecthero.guide.quirk.hellflame.bands', 'Body Heat climbs gently with every flame; resting vents it at 4 a second. The hotter you run, the harder you hit.');

// ---------------------------------------------------------------- the guide chapters
set('projecthero.guide.quirk.hellflame.body', 'The Number One Hero\'s fire, held back only by his own body. Cutting strings, flaming fists, jet flight and walls of flame all raise your Body Heat. G and X each have a hold move (keep the key down) on top of the tap and Shift moves. X is your movement key, V your utility key.');
set('projecthero.guide.quirk.hellflame.meter.body', 'Body Heat rises with every flame, and every second while you fly, wear the Flame Cloak or fire the Hellblast. Stop burning and it vents about 4 a second, faster in water or rain. Kindle (0-39): orange flames, 0.85x damage. Blaze (40-79): full power, your punches set things alight. Inferno (80+): white-hot, 1.3x, but it burns you half a heart a second.');
set('projecthero.guide.quirk.hellflame.overload', 'Overheat and burnout');
set('projecthero.guide.quirk.hellflame.overload.body', 'At 100 Heat you push past it: 5 s of Overheat at 1.5x damage, burning a heart a second. Then you burn out: 8 s with no fire moves and Slowness while the Heat drains to 40. V Heat Vent works through all of it and ends it. After Prominence Burn you are burnt out for 10 s and nothing works.');
set('projecthero.guide.quirk.hellflame.passive.body', 'Ordinary fire doesn\'t hurt you (lava still does), except while burnt out; the cold never freezes you, and in Inferno snow and ice melt round you. Your squad is never burned; bosses take at most 10% of their health per hit and are never trapped. Forge Hand: with an empty main hand, sneak and use to cook or smelt what is in your off hand (4 Heat each). The full Endeavor suit gives you 15% more heat headroom. Fire you light is always temporary and never placed next to anything that burns.');
set('projecthero.guide.quirk.explosion.body', 'Your sweat is like nitroglycerin: point-blank power, palm-blast flight and pure aggression. The more you sweat, the bigger the boom. R, G and X each have a hold move (keep the key down) on top of the tap and Shift moves. X is your movement key, V your utility key.');
set('projecthero.guide.quirk.explosion.meter.body', 'Sweat is your fuel. It builds on its own, 4 a second, faster while you fight and move and faster still where it is hot; water and rain wash it off. Dry (0-29): your blasts sputter at 0.6x. Primed (30-79): full power. Soaked (80+): 1.4x, and your palms crackle.');
set('projecthero.guide.quirk.explosion.overload', 'Arm Strain');
set('projecthero.guide.quirk.explosion.overload.body', 'Every blast strains your arms (the two arm icons on the HUD). It builds slowly and eases off two seconds after you stop. From 70 every blast is 15% stronger but costs half a heart. At 100 your arms are shot: 6 s of Weakness II and no blasts. Shift+V Cool Off eases it, even then.');
set('projecthero.guide.quirk.explosion.passive', 'Good to know');
set('projecthero.guide.quirk.explosion.passive.body', 'Your explosions never hurt you or your squad -- but they really blow up the terrain: an ordinary blast breaks a small pocket, the AP Shot bores a short tunnel, the big moves (Howitzer Impact, the Uppercut, the landing blast) blow a crater and the Plus Ultra ultimate a huge one, and a share of what breaks drops like TNT. Never containers, bedrock or obsidian, never where you could not break blocks yourself; the Stun Grenade is only a flash. A server can turn the block damage off (config/projecthero_quirk_explosion.json, terrain.blockDamage). Fall more than 8 blocks with 10+ Sweat and a blast under you breaks the fall. Blast mining: sneak and punch with an empty hand to blast a 3x3 of soft stone or earth into drops (5 Sweat; only where you may break blocks). The full Ground Zero suit\'s gauntlets bank Sweat while you are soaked (up to 50) for a Gauntlet Grenade.');
set('projecthero.quirk.hellflame.intro', 'Your body burns hotter than any flame. Every flame builds Body Heat: the hotter you run, the harder you hit, until you overheat and burn out.');
set('projecthero.quirk.explosion.intro', 'Your sweat is like nitroglycerin. Tap, Shift or hold your keys for blasts, flight and grabs; more Sweat, bigger blasts; overdo it and your arms are shot.');

// ---------------------------------------------------------------- Hellflame: one block
const HF = [
	['message.projecthero.quirk.overloaded.hellflame', 'Burnt out! Your flames sputter out.'],
	['projecthero.quirk.ability.quirk_hellflame_hell_spider', 'Hell Spider'],
	['projecthero.quirk.ability.quirk_hellflame_hell_spider.desc', 'Tap R: five thin cutting strings flick from your fingertips and sweep across your aim, 14 blocks: 9 to all they cross, set alight, a scorch line on the ground.'],
	['projecthero.quirk.ability.quirk_hellflame_hell_spider_net', 'Hell Spider: Mesh'],
	['projecthero.quirk.ability.quirk_hellflame_hell_spider_net.desc', 'Shift+R: a dome of burning strings over the aimed spot for 4 s: all inside are trapped and burn, 2.5 every half second. Bosses are only slowed.'],
	['projecthero.quirk.ability.quirk_hellflame_flashfire_fist', 'Flashfire Fist'],
	['projecthero.quirk.ability.quirk_hellflame_flashfire_fist.desc', 'Tap G, up to three times: a jab (6), a cross (6), then a jet-assisted straight (10) with a burst of flame and a shock ring.'],
	['projecthero.quirk.ability.quirk_hellflame_vanishing_fist', 'Vanishing Fist'],
	['projecthero.quirk.ability.quirk_hellflame_vanishing_fist.desc', 'Shift+G: leave a burning decoy behind (hunters lose you), reappear behind your target and strike: 16.'],
	['projecthero.quirk.ability.quirk_hellflame_hellblast', 'Flashfire Fist: Hellblast'],
	['projecthero.quirk.ability.quirk_hellflame_hellblast.desc', 'Hold G to draw your fist back and gather flame (0.5 to 2 s); let go to fire a 48-block beam for 1.5 to 3 s, 7 every quarter second. Heat climbs while it burns.'],
	['projecthero.quirk.ability.quirk_hellflame_jet_burst', 'Jet Burst'],
	['projecthero.quirk.ability.quirk_hellflame_jet_burst.desc', 'Tap X: a flame-thrust dash, about 10 blocks, on the ground or in the air.'],
	['projecthero.quirk.ability.quirk_hellflame_jet_burn', 'Flashfire Fist: Jet Burn'],
	['projecthero.quirk.ability.quirk_hellflame_jet_burn.desc', 'Shift+X: rocket 14 blocks along your aim behind a burning fist: 16 to everything in the line, a scorched trail behind you.'],
	['projecthero.quirk.ability.quirk_hellflame_fire_jet_flight', 'Fire Jet Flight'],
	['projecthero.quirk.ability.quirk_hellflame_fire_jet_flight.desc', 'Hold X: fly on jets from your feet and palms, 18 blocks a second (26 sprinting). Fly where you look; Space up, Sneak down. Heat builds while you fly.'],
	['projecthero.quirk.ability.quirk_hellflame_hellflame_barrage', 'Hellflame Barrage'],
	['projecthero.quirk.ability.quirk_hellflame_hellflame_barrage.desc', 'Tap Z: eight rapid fire jabs from alternating fists, 3.5 a jab; the last one launches.'],
	['projecthero.quirk.ability.quirk_hellflame_fire_pillar', 'Hellflame Pillar'],
	['projecthero.quirk.ability.quirk_hellflame_fire_pillar.desc', 'Shift+Z: the ground under your target cracks and glows, then a 9-block pillar of fire erupts: 14 and launched, and it burns on for 3 s (2 a second).'],
	['projecthero.quirk.ability.quirk_hellflame_flame_wave', 'Flame Wave'],
	['projecthero.quirk.ability.quirk_hellflame_flame_wave.desc', 'Tap C: a wide wave of fire sweeps 90 degrees in front of you, 10 blocks: 11, set burning for 4 s and thrown back.'],
	['projecthero.quirk.ability.quirk_hellflame_flame_cloak', 'Flame Cloak'],
	['projecthero.quirk.ability.quirk_hellflame_flame_cloak.desc', 'Shift+C: burn for 12 s: 3 a second to anything within 3 blocks, attackers catch fire, +2 armour. Heat rises slowly. Shift+C again puts it out.'],
	['projecthero.quirk.ability.quirk_hellflame_cool_down', 'Heat Vent'],
	['projecthero.quirk.ability.quirk_hellflame_cool_down.desc', 'Tap V: dump up to 60 Heat in a burst of steam and flame, 4 + 0.18 per Heat vented, 3 to 4 blocks. Works while overheated and ends it, puts out you and your allies, boils off the water round your feet.'],
	['projecthero.quirk.ability.quirk_hellflame_fire_wall', 'Fire Wall'],
	['projecthero.quirk.ability.quirk_hellflame_fire_wall.desc', 'Shift+V: a 7-block wall of flame rises in front of you for 8 s: it burns all who cross it (4) and stops arrows. It never sets blocks alight.'],
	['message.projecthero.hellflame.no_target', 'No target to strike.'],
	['message.projecthero.hellflame.burnt_out', 'Burnt out... your body can\'t cool yet.'],
	['message.projecthero.hellflame.burnout', 'Prominence Burn spent. You\'re burnt out.'],
	['hud.projecthero.hellflame.heat', 'Body Heat'],
	['hud.projecthero.hellflame.burnout', 'BURNT OUT %ss'],
	['hud.projecthero.hellflame.overheat', 'OVERHEAT %ss'],
	['hud.projecthero.hellflame.prominence', 'PROMINENCE BURN'],
	['hud.projecthero.hellflame.beam', 'Hellblast'],
	['hud.projecthero.hellflame.charge', 'Hellblast: charging'],
	['hud.projecthero.hellflame.flying', 'Fire Jet Flight'],
	['hud.projecthero.hellflame.hot', 'Burning up: half a heart a second'],
	['subtitles.projecthero.quirk_hellflame_roar', 'Flames roar'],
	['subtitles.projecthero.quirk_hellflame_jet', 'Fire jet bursts'],
	['subtitles.projecthero.quirk_hellflame_vent', 'Heat vents'],
	['subtitles.projecthero.quirk_hellflame_beam', 'Hellblast fires'],
	['subtitles.projecthero.quirk_hellflame_comet', 'Prominence Burn screams'],
	['subtitles.projecthero.quirk_hellflame_whip', 'Fire strings whip'],
];
block(k => k === 'message.projecthero.quirk.overloaded.hellflame' || k.startsWith('projecthero.quirk.ability.quirk_hellflame_')
	|| k.startsWith('message.projecthero.hellflame.') || k.startsWith('hud.projecthero.hellflame.') || k.startsWith('subtitles.projecthero.quirk_hellflame_'), HF);

// ---------------------------------------------------------------- Explosion: one block
const EX = [
	['message.projecthero.quirk.overloaded.explosion', 'Your arms are shot!'],
	['message.projecthero.explosion.need_sweat', 'Not enough sweat! Need %s.'],
	['message.projecthero.explosion.dry', 'Out of sweat: the blasts sputter out.'],
	['hud.projecthero.quirk.explosion.arms', 'Arms'],
	['hud.projecthero.quirk.explosion.bank', 'Gauntlets'],
	['projecthero.quirk.ability.quirk_explosion_ap_shot', 'AP Shot'],
	['projecthero.quirk.ability.quirk_explosion_ap_shot.desc', 'Tap R: a compressed, armour-piercing palm blast that explodes on the first thing it hits for 10 (32 blocks). After a Stun Grenade or the Uppercut it hits harder.'],
	['projecthero.quirk.ability.quirk_explosion_ap_auto_cannon', 'AP Shot: Auto-Cannon'],
	['projecthero.quirk.ability.quirk_explosion_ap_auto_cannon.desc', 'Shift+R: a rapid volley of 8 small AP shots, 4 each, alternating palms, following your aim.'],
	['projecthero.quirk.ability.quirk_explosion_ap_pierce', 'AP Shot: Pierce'],
	['projecthero.quirk.ability.quirk_explosion_ap_pierce.desc', 'Hold R to compress the blast (up to 1.5 s); let go to fire a thin beam that pierces everything in a line to 48 blocks: 8 to 22 with the charge.'],
	['projecthero.quirk.ability.quirk_explosion_blast_punch', 'Explosive Punch'],
	['projecthero.quirk.ability.quirk_explosion_blast_punch.desc', 'Tap G: lunge in with a point-blank blast from the fist: 14 and a heavy knockback.'],
	['projecthero.quirk.ability.quirk_explosion_blast_rush', 'Blast Rush'],
	['projecthero.quirk.ability.quirk_explosion_blast_rush.desc', 'Shift+G: slam both palms down: six blasts march away along the ground, 8 each.'],
	['projecthero.quirk.ability.quirk_explosion_crush_grip', 'Crush Grip'],
	['projecthero.quirk.ability.quirk_explosion_crush_grip.desc', 'Hold G: grab the face of the foe in front of you, three blasts in your palm (6, 6, 12), then throw it. Bosses take the blasts but are never grabbed.'],
	['projecthero.quirk.ability.quirk_explosion_blast_dash', 'Explosion Dash'],
	['projecthero.quirk.ability.quirk_explosion_blast_dash.desc', 'Tap X: one blast behind you throws you about 12 blocks the way you steer (W, A, S, D), or the way you look.'],
	['projecthero.quirk.ability.quirk_explosion_spin_dodge', 'Spin Dodge'],
	['projecthero.quirk.ability.quirk_explosion_spin_dodge.desc', 'Shift+X: a sideways blast spin (A or D picks the side), 5 blocks. Nothing can hurt you for a moment, and you end facing your attacker.'],
	['projecthero.quirk.ability.quirk_explosion_propulsion', 'Explosion Propulsion'],
	['projecthero.quirk.ability.quirk_explosion_propulsion.desc', 'Hold X: palm blasts fly you. W/S along your look, A/D sideways, Space/Sneak up/down, sprint for more. Burns Sweat.'],
	['projecthero.quirk.ability.quirk_explosion_cluster', 'Cluster'],
	['projecthero.quirk.ability.quirk_explosion_cluster.desc', 'Tap Z: two rings of small explosions burst around you: 9 to everything close.'],
	['projecthero.quirk.ability.quirk_explosion_uppercut', 'Explosive Uppercut'],
	['projecthero.quirk.ability.quirk_explosion_uppercut.desc', 'Shift+Z: a rising blast uppercut, 12: the target is launched and you rocket up after it for an air follow-up.'],
	['projecthero.quirk.ability.quirk_explosion_howitzer_impact', 'Howitzer Impact'],
	['projecthero.quirk.ability.quirk_explosion_howitzer_impact.desc', 'Tap C: blast up spinning, then corkscrew down at the target and detonate: 30 in a huge radius. Needs 30 Sweat.'],
	['projecthero.quirk.ability.quirk_explosion_gauntlet_grenade', 'Gauntlet Grenade'],
	['projecthero.quirk.ability.quirk_explosion_gauntlet_grenade.desc', 'Shift+C: pull the pin: all your Sweat in one cannon blast down your aim, 28 to all in its path. Needs 50 Sweat, or the Ground Zero gauntlets\' full bank. Big recoil.'],
	['projecthero.quirk.ability.quirk_explosion_stun_grenade', 'Stun Grenade'],
	['projecthero.quirk.ability.quirk_explosion_stun_grenade.desc', 'Tap V: a blinding flash: everything within 8 blocks is blinded and slowed for 5 s and loses its target (bosses are only slowed).'],
	['projecthero.quirk.ability.quirk_explosion_cool_off', 'Cool Off'],
	['projecthero.quirk.ability.quirk_explosion_cool_off.desc', 'Shift+V: shake the sweat off: -40 Sweat and -50 Arm Strain. Works while your arms are shot.'],
	['subtitles.projecthero.quirk_explosion_crackle', 'Palms crackle'],
	['subtitles.projecthero.quirk_explosion_blast_small', 'Palm blast pops'],
	['subtitles.projecthero.quirk_explosion_blast', 'Explosion booms'],
	['subtitles.projecthero.quirk_explosion_howitzer', 'Howitzer Impact roars'],
	['subtitles.projecthero.quirk_explosion_stun', 'Stun Grenade flashes'],
	['subtitles.projecthero.quirk_explosion_pierce', 'AP Shot pierces'],
];
block(k => k === 'message.projecthero.quirk.overloaded.explosion' || k.startsWith('message.projecthero.explosion.') || k.startsWith('hud.projecthero.quirk.explosion.')
	|| k.startsWith('projecthero.quirk.ability.quirk_explosion_') || k.startsWith('subtitles.projecthero.quirk_explosion_'), EX);

const out = lines.join('\n');
JSON.parse(out);
fs.writeFileSync(FILE, out);
console.log('ok', lines.length);
