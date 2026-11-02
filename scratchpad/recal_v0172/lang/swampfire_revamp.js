// v0.17.2 Swampfire revamp: rewrites Swampfire's lang block in en_us.json in place (only Swampfire keys; the order of every other
// line is untouched). Run from the repo root: node scratchpad/recal_v0172/lang/swampfire_revamp.js
'use strict';
const fs = require('fs');
const FILE = 'src/main/resources/assets/projecthero/lang/en_us.json';
const L = {
	'projecthero.guide.omnitrix.alien.swampfire': 'A Methanosian: a walking swamp plant who sweats methane and sets it alight. Fire and lava cannot hurt him and he keeps growing back -- even "killed", he bursts into a heap of mulch and pulls himself together. He vents methane clouds and lights them with his fire, throws seeds that burst or plant themselves, lashes and snares with vines, swings on them and grows ladders up walls; his ultimate is a methane explosion that leaves his fire running hot. Cold and water are his weaknesses.',
	'projecthero.omnitrix.info.swampfire.role': 'Plant pyrokinetic: methane clouds he sets off with his own fire, seeds and vines -- and he keeps growing back.',
	'projecthero.omnitrix.info.swampfire.passive.1': 'Methanosian Body: fire and lava never hurt him (his own fire blocks included).',
	'projecthero.omnitrix.info.swampfire.passive.2': 'Regrowth: heals 1 health a second, 2 while burning or standing in fire -- his own fire rings are a healing zone.',
	'projecthero.omnitrix.info.swampfire.passive.3': 'Reassembly: lethal damage bursts him into a heap of swamp mulch for 3 s (nothing can hit or chase it), then he reforms on 10 health with 2 s of Resistance II. Once every 150 s (shown on the HUD); /kill and the void still kill him.',
	'projecthero.omnitrix.info.swampfire.passive.4': 'Swamp Stench: anything standing within 2 blocks of him for 2 s gets 3 s of nausea (a green haze for players).',
	'projecthero.omnitrix.info.swampfire.passive.5': 'Combo: any of his fire passing through his methane cloud -- a Fire Blast, the Torch, Root Eruption bursting under it -- sets it off.',
	'projecthero.omnitrix.info.swampfire.weakness.1': 'Cold: freezing damage hurts him twice as much, and while frozen (powder snow, ice attacks) he doesn\'t regrow and Reassembly can\'t save him.',
	'projecthero.omnitrix.info.swampfire.weakness.2': 'Doused: under water his fire fizzles (Fire Blast, Torch, Ignite and Methane Inferno are refused; the cloud just bubbles away). Rain halves how long his fire burns.',
	'projecthero.omnitrix.info.swampfire.weakness.3': 'Flammable cloud: Ignite hits everything in the cloud but him (squad allies are spared).',
	'projecthero.omnitrix.swampfire.fire_blast': 'Fire Blast',
	'projecthero.omnitrix.swampfire.fire_blast.desc': 'A methane fireball from your palm: 7 damage and alight for 4 s. Flying through your methane cloud, it sets the cloud off.',
	'projecthero.omnitrix.swampfire.methane_torch': 'Methane Torch',
	'projecthero.omnitrix.swampfire.methane_torch.desc': 'Hold (up to 2 s): a cone of burning gas from both palms that grows from 4 to 8 blocks while held -- 3.5 damage every half second (up to 14), alight for 5 s, and real fire where it plays on the ground. The 6 s cooldown starts when you let go.',
	'projecthero.omnitrix.swampfire.seed_bomb': 'Seed Bomb',
	'projecthero.omnitrix.swampfire.seed_bomb.desc': 'Lob a seed pod that bursts on impact: up to 15 damage in 3.5 blocks, rooted for 2 s (bosses 0.5 s), and a vine tangle left there for 2 s that roots whatever walks in.',
	'projecthero.omnitrix.swampfire.vine_snare': 'Vine Snare',
	'projecthero.omnitrix.swampfire.vine_snare.desc': 'Vines spiral up round the creature you look at (20 blocks): 6 damage, rooted for 3 s. Press Shift+G again while it holds to yank it 4 blocks toward you.',
	'projecthero.omnitrix.swampfire.methane_jet': 'Methane Jet',
	'projecthero.omnitrix.swampfire.methane_jet.desc': 'A burst of burning gas launches you up and forward; no fall damage for 1.5 s.',
	'projecthero.omnitrix.swampfire.vine_swing': 'Vine Swing',
	'projecthero.omnitrix.swampfire.vine_swing.desc': 'A vine to the block you look at (22 blocks). Tap: it hauls you there. Hold: stay tethered and swing on it (look where you want to go), let go to fly off with the momentum. No fall damage meanwhile.',
	'projecthero.omnitrix.swampfire.methane_cloud': 'Methane Cloud / Ignite',
	'projecthero.omnitrix.swampfire.methane_cloud.desc': 'Vent a 4-block cloud of methane for 6 s: 2 damage a second and nausea. Press Z again while it hangs to Ignite it -- 19 damage in 5 blocks, alight for 5 s, and a ring of real fire. Your fire passing through it ignites it too. The 10 s cooldown starts after the blast.',
	'projecthero.omnitrix.swampfire.methane_inferno': 'Methane Inferno',
	'projecthero.omnitrix.swampfire.methane_inferno.desc': 'ULTIMATE: a 1 s build-up, then a huge methane explosion round you -- 60 damage at the centre, 40 at the 8-block edge, alight for 8 s, real fire scattered and ringed round you. Then 12 s of Methane Surge: your fire moves deal 25% more and Fire Blasts leave fire where they land.',
	'projecthero.omnitrix.swampfire.regrowth': 'Regrowth',
	'projecthero.omnitrix.swampfire.regrowth.desc': 'Root yourself for 3 s and regrow: +2 health every half second (12); poison, wither, slowness and weakness cleared, and Reassembly recharges twice as fast meanwhile. Not at full health.',
	'projecthero.omnitrix.swampfire.overgrowth': 'Overgrowth',
	'projecthero.omnitrix.swampfire.overgrowth.desc': 'Make the plants and crops within 4 blocks grow (bone meal), and grow a climbable vine ladder up the wall you look at (5 blocks away, up to 8 tall, gone after 30 s).',
	'projecthero.omnitrix.swampfire.root_eruption': 'Root Eruption',
	'projecthero.omnitrix.swampfire.root_eruption.desc': 'A line of root spikes bursts up ahead of you, 10 blocks: 16 damage and launched. A spike bursting under your methane cloud lights it.',
	'projecthero.omnitrix.swampfire.seed_scatter': 'Seed Scatter',
	'projecthero.omnitrix.swampfire.seed_scatter.desc': 'Fling 5 seeds in a fan; they plant where they land for 20 s (5 at most). An enemy coming within 2 blocks bursts one into snapping vines: 8 damage, rooted for 1.5 s.',
	'projecthero.omnitrix.swampfire.burning_fist': 'Burning Fist',
	'projecthero.omnitrix.swampfire.burning_fist.desc': 'Your punch sets the target alight for 3 s, and hits 2 harder against a creature already burning.',
	'projecthero.omnitrix.swampfire.vine_lash': 'Vine Lash',
	'projecthero.omnitrix.swampfire.vine_lash.desc': 'Right-click with an empty hand: a vine whips from your arm, 6 blocks -- 6 damage, and small creatures are pulled toward you.',
	'projecthero.omnitrix.swampfire.reassembly': 'Reassembly',
	'projecthero.omnitrix.swampfire.reassembly.desc': 'Lethal damage bursts you into a heap of mulch for 3 s instead; you reform on 10 health with 2 s of Resistance II. Not while frozen, never against /kill or the void.',
	'hud.projecthero.omnitrix.swampfire.reassembly': 'Reassembly',
	'hud.projecthero.omnitrix.swampfire.surge': 'Surge',
	'message.projecthero.omnitrix.swampfire.no_target': 'Nothing to snare there.',
	'message.projecthero.omnitrix.swampfire.no_anchor': 'No block for the vine to catch.',
	'message.projecthero.omnitrix.swampfire.no_cloud': 'No methane to ignite -- let out a cloud first (Z).',
	'message.projecthero.omnitrix.swampfire.full_health': 'You are already whole.',
	'message.projecthero.omnitrix.swampfire.doused': 'Your methane fizzles under water.',
	'message.projecthero.omnitrix.swampfire.bubbles': 'The methane just bubbles away under water.',
	'message.projecthero.omnitrix.swampfire.mulched': 'Swampfire bursts into mulch... and starts pulling himself back together.',
};
const REMOVED = ['projecthero.omnitrix.swampfire.ignite', 'projecthero.omnitrix.swampfire.ignite.desc', 'projecthero.omnitrix.swampfire.inferno_bloom',
	'projecthero.omnitrix.swampfire.inferno_bloom.desc', 'projecthero.omnitrix.swampfire.bloom', 'projecthero.omnitrix.swampfire.bloom.desc'];
const lines = fs.readFileSync(FILE, 'utf8').split('\n');
const keyOf = l => { const m = /^\s*"([^"]+)"\s*:/.exec(l); return m ? m[1] : null; };
const isSwamp = k => k && (k.includes('omnitrix.swampfire') || k.includes('omnitrix.info.swampfire') || k === 'projecthero.guide.omnitrix.alien.swampfire');
let first = -1;
const out = [];
for (const l of lines) {
	const k = keyOf(l);
	if (isSwamp(k) && (k in L || REMOVED.includes(k) || k.startsWith('projecthero.omnitrix.info.swampfire.'))) {
		if (first < 0) first = out.length;
		continue;
	}
	out.push(l);
}
if (first < 0) throw new Error('no Swampfire block found');
const block = Object.entries(L).map(([k, v]) => '  ' + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',');
out.splice(first, 0, ...block);
const text = out.join('\n');
JSON.parse(text); // still valid
fs.writeFileSync(FILE, text);
console.log('swampfire lang: ' + block.length + ' keys at line ' + (first + 1));
