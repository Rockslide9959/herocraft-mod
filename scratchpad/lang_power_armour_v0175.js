// v0.17.5 Power Armour: adds the en_us keys key by key (never rewrites the file: other branches touch it too).
// Keeps the file's line endings and 2-space indent. Existing keys are left alone unless --force.
//   node scratchpad/lang_power_armour_v0175.js [root] [--force]
const fs = require('fs');
const path = require('path');
const args = process.argv.slice(2);
const force = args.includes('--force');
const root = args.find(a => !a.startsWith('--')) || process.cwd();
const file = path.join(root, 'src/main/resources/assets/projecthero/lang/en_us.json');

const TIERS = {
  iron: ['Iron', 'Sturdy and slow: 15 armour, 300 integrity, 3 upgrade slots.'],
  gold: ['Gold', 'Weaker plating (11 armour, 220 integrity), but the core drains 25% slower. 3 slots.'],
  hero_alloy: ['Hero Alloy', 'Better plating: 17 armour, 2 toughness, 450 integrity, 4 slots.'],
  diamond: ['Diamond', 'Strong: 20 armour, 5 toughness, 650 integrity, 5 slots.'],
  netherite: ['Netherite', 'Very strong: 22 armour, 8 toughness, 900 integrity, 6 slots. Fire and lava proof, no knockback.'],
  vibranium: ['Vibranium', 'Absorbs impacts: 24 armour, 10 toughness, 1200 integrity, 7 slots. Falls and blasts charge the core.'],
  celestial: ['Celestial', 'The best: 26 armour, 12 toughness, 1600 integrity, 8 slots. A faint glow and the fastest servos.'],
};
const MODULES = {
  night_visor: ['Night Visor', 'Helmet. See in the dark while sealed.'],
  ore_scanner: ['Ore Scanner', 'Helmet. With the Visor on (V), ores within 10 blocks are outlined through walls.'],
  target_lock: ['Target Lock', 'Helmet. With the Visor on (V), the arm weapon and missiles aim at the nearest threat in front of you.'],
  rebreather: ['Rebreather', 'Helmet. Breathe underwater and shrug off poison gas.'],
  reactive_plating: ['Reactive Plating', 'Chest. When something hits you up close, the plates burst and knock everything back.'],
  capacitor_bank: ['Capacitor Bank', 'Chest. A 600 E reserve that keeps the suit running after the core is dry. Tops itself up while the core is above half.'],
  overclock: ['Overclock', 'Chest. +3 core output and +15% damage, but the suit drains 25% faster.'],
  solar_plate: ['Solar Plate', 'Chest. Trickles 0.5 E a second into the core in daylight, worn or parked.'],
  breacher_plating: ['Breacher Plating', 'Chest. Shoulder Charge (Z) smashes through blocks up to stone hardness.'],
  gatling: ['Gatling', 'Arm weapon. Hold Shift+R: a spinning gatling, 2.5 damage a round, about 7 rounds a second.'],
  flamer: ['Flamer', 'Arm weapon. Hold Shift+R: a cone of fire that sets everything in front of you alight.'],
  tesla_coil: ['Tesla Coil', 'Arm weapon. Shift+R: lightning that chains between up to 4 targets.'],
  rail_cannon: ['Rail Cannon', 'Arm weapon. Hold Shift+R to charge, let go to fire a piercing beam (28 damage, 48 blocks).'],
  power_fist: ['Power Fist', 'Arm weapon. Bigger punches with a shockwave; Shift+R slams a piledriver into the ground in front of you.'],
  magnetic_clamp: ['Magnetic Clamp', 'Arms. Servo Grip (Shift+G) pulls a mob to you from 14 blocks away.'],
  missile_pod: ['Missile Pod', 'Back. C fires 4 homing missiles at the nearest threats.'],
  shield_projector: ['Shield Projector', 'Back. C raises an energy bubble for 8 s: it turns arrows and cuts other damage by 60%.'],
  repair_drone: ['Repair Drone', 'Back. C launches a drone that repairs the suit and heals you for 10 s.'],
  sentry_turret: ['Sentry Turret', 'Back. C drops a turret that shoots threats near you for 30 s.'],
  thruster_pack: ['Thruster Pack', 'Back. Hold X to fly (WASD steers, Sneak sinks). C fires an afterburner dash.'],
  servo_boosters: ['Servo Boosters', 'Legs. Walk 20% faster.'],
  seismic_plates: ['Seismic Plates', 'Legs. Ground Stomp (G) reaches 6 blocks, hits harder and slows.'],
  stabilisers: ['Stabilisers', 'Legs. No knockback, whatever the frame.'],
};

const K = {
  // ---- items
  'item.projecthero.redstone_circuit_board': 'Redstone Circuit Board',
  'item.projecthero.redstone_circuit_board.hint': 'The brain of a Power Armour frame. Also the template for upgrading a frame to the next tier at a smithing table.',
  'item.projecthero.power_armour_frame.desc': 'Place it, put a core in, right-click to step in. Sneak + right-click with an empty hand to edit it.',
  'item.projecthero.fusion_cell': 'Fusion Cell',
  'item.projecthero.fusion_cell.desc': 'A basic power core: 1000 E, output 6.',
  'item.projecthero.arc_core': 'Arc Core',
  'item.projecthero.arc_core.desc': 'A strong power core: 2500 E, output 10.',
  'item.projecthero.star_core': 'Star Core',
  'item.projecthero.star_core.desc': 'A Celestial power core: 6000 E, output 16.',
  'block.projecthero.core_charger': 'Core Charger',
  'item.projecthero.core_charger.hint': 'A furnace for power cores: a core on top, any fuel (or redstone) below. Charges 2 E a tick while it burns.',
  'container.projecthero.core_charger': 'Core Charger',
  'container.projecthero.power_armour_editor': 'Armour Editor',
  'entity.projecthero.power_armour_frame': 'Power Armour',
  'entity.projecthero.pa_missile': 'Missile',
  'entity.projecthero.pa_sentry_turret': 'Sentry Turret',
  // ---- tooltips
  'projecthero.power_armour.tooltip.stats': 'Armour %s, %s upgrade slots',
  'projecthero.power_armour.tooltip.integrity': 'Integrity %s / %s',
  'projecthero.power_armour.tooltip.core': 'Core: %s (%s / %s E)',
  'projecthero.power_armour.tooltip.no_core': 'No core',
  'projecthero.power_armour.core.charge': 'Charge %s / %s E',
  'projecthero.power_armour.core.output': 'Output %s',
  'projecthero.power_armour.module.mount': 'Mounts on: %s. Needs %s output.',
  'projecthero.power_armour.module.weapon': 'Arm weapon (one per suit)',
  'projecthero.power_armour.part.helmet': 'Helmet',
  'projecthero.power_armour.part.chest': 'Chest',
  'projecthero.power_armour.part.arms': 'Arms',
  'projecthero.power_armour.part.back': 'Back',
  'projecthero.power_armour.part.legs': 'Legs',
  'projecthero.power_armour.fit.no_slot': 'No such slot',
  'projecthero.power_armour.fit.wrong_part': 'That module mounts somewhere else',
  'projecthero.power_armour.fit.duplicate': 'Already fitted',
  'projecthero.power_armour.fit.one_weapon': 'Only one arm weapon',
  'projecthero.power_armour.fit.over_budget': 'Not enough core output',
  'projecthero.power_armour.fit.core_too_weak': 'This core cannot run the fitted modules',
  // ---- charger
  'projecthero.power_armour.charger.charge': 'Charge %s / %s E',
  'projecthero.power_armour.charger.empty': 'Put a core in the top slot',
  'projecthero.power_armour.charger.fuel': 'Fuel or redstone below',
  // ---- editor
  'projecthero.power_armour.editor.pick_up': 'Pick Up',
  'projecthero.power_armour.editor.core': 'Core',
  'projecthero.power_armour.editor.armour': 'Armour %s',
  'projecthero.power_armour.editor.integrity': 'Integrity %s%%',
  'projecthero.power_armour.editor.charge': 'Core %s/%s',
  'projecthero.power_armour.editor.no_core': 'No core',
  'projecthero.power_armour.editor.output': 'Output %s/%s',
  'projecthero.power_armour.editor.drain': 'Idle %s E/s',
  'projecthero.power_armour.editor.slots': 'Slots %s/%s',
  'projecthero.power_armour.editor.core_slot': 'Core bay: a Fusion Cell, Arc Core or Star Core',
  'projecthero.power_armour.editor.module_slot': '%s slot',
  'projecthero.power_armour.editor.budget_left': 'Core output left: %s',
  'projecthero.power_armour.editor.paint.0': 'Primary paint (plates)',
  'projecthero.power_armour.editor.paint.1': 'Secondary paint (trim)',
  'projecthero.power_armour.editor.paint.2': 'Glow colour (visor, core, lights)',
  'projecthero.power_armour.editor.paint_hint': 'Left-click: next colour. Right-click: back. Past the last dye it returns to the tier colour.',
  'projecthero.power_armour.editor.preview_hint': 'Drag to turn it. Click to open or close the plates.',
  // ---- messages
  'message.projecthero.power_armour.sealed': 'Suit sealed. Systems online.',
  'message.projecthero.power_armour.sealed_unpowered': 'Suit sealed, but it has no power. Hold Shift+V to step out.',
  'message.projecthero.power_armour.unpowered': 'Core empty: the suit is unpowered.',
  'message.projecthero.power_armour.no_power': 'No power. Hold Shift+V to step out.',
  'message.projecthero.power_armour.low_power': 'Not enough power for that.',
  'message.projecthero.power_armour.overload_low': 'The core is too low to overload.',
  'message.projecthero.power_armour.no_weapon': 'No arm weapon fitted.',
  'message.projecthero.power_armour.no_back': 'No back module fitted.',
  'message.projecthero.power_armour.too_heavy': 'Too heavy for the servos.',
  'message.projecthero.power_armour.wrecked': 'This suit is wrecked. Repair it with its material first.',
  'message.projecthero.power_armour.other_suit': 'You cannot step in while another suit or transformation is on.',
  'message.projecthero.power_armour.cant_now': 'You cannot step in right now.',
  'message.projecthero.power_armour.broken': 'The suit has broken down and ejected you.',
  'message.projecthero.power_armour.burst': 'Something burst the suit open!',
  'message.projecthero.power_armour.repaired': 'Repaired: integrity %s%%',
  'message.projecthero.power_armour.repaired_full': 'Already fully repaired.',
  'message.projecthero.power_armour.picked_up': 'Suit packed up. Everything stays on it.',
  // ---- HUD
  'hud.projecthero.power_armour.title': '%s Power Armour',
  'hud.projecthero.power_armour.core': 'Core',
  'hud.projecthero.power_armour.integrity': 'Integrity',
  'hud.projecthero.power_armour.bank': 'Bank',
  'hud.projecthero.power_armour.sealing': 'SEALING',
  'hud.projecthero.power_armour.online': 'SYSTEMS ONLINE',
  'hud.projecthero.power_armour.tag.unpowered': 'UNPOWERED',
  'hud.projecthero.power_armour.tag.damaged': 'Damaged',
  'hud.projecthero.power_armour.tag.visor': 'Visor',
  'hud.projecthero.power_armour.tag.lock': 'Target Lock',
  'hud.projecthero.power_armour.tag.shield': 'Shield %ss',
  'hud.projecthero.power_armour.tag.drone': 'Drone',
  'hud.projecthero.power_armour.tag.turret': 'Turret',
  'hud.projecthero.power_armour.tag.thrust': 'Thrust',
  'hud.projecthero.power_armour.move.punch': 'Servo Punch (hold to charge)',
  'hud.projecthero.power_armour.move.power_punch': 'Power Fist punch (hold to charge)',
  'hud.projecthero.power_armour.move.no_weapon': 'Arm weapon (none fitted)',
  'hud.projecthero.power_armour.move.stomp': 'Ground Stomp',
  'hud.projecthero.power_armour.move.seismic_stomp': 'Seismic Stomp',
  'hud.projecthero.power_armour.move.grip': 'Servo Grip / throw',
  'hud.projecthero.power_armour.move.clamp': 'Magnetic Clamp / throw',
  'hud.projecthero.power_armour.move.charge': 'Shoulder Charge',
  'hud.projecthero.power_armour.move.breach': 'Breaching Charge',
  'hud.projecthero.power_armour.move.overload': 'Core Overload',
  'hud.projecthero.power_armour.move.jets': 'Jump Jets',
  'hud.projecthero.power_armour.move.thrust': 'Thrusters (hold)',
  'hud.projecthero.power_armour.move.no_back': 'Back module (none fitted)',
  'hud.projecthero.power_armour.move.visor': 'Visor',
  'hud.projecthero.power_armour.move.exit': 'Hold: step out',
  // ---- guidebook chapter
  'projecthero.guide.power_armour': 'Power Armour',
  'projecthero.guide.power_armour.tier': 'Secondary power: only while sealed in a suit',
  'projecthero.guide.power_armour.body': 'A slim, layered exo-suit built from pixels: an under-frame, armour plates over it, raised trim and glowing strips on top. Any hero can own and wear one. It never replaces your Primary power: while you are sealed inside, the suit’s kit takes R, G, Z, X, C and V, your Primary’s passives keep working, and its keys come back when you step out. H stays the power selector.',
  'projecthero.guide.power_armour.getting_head': 'Building a frame',
  'projecthero.guide.power_armour.getting_text': 'Craft a Redstone Circuit Board (redstone, gold nuggets, copper, iron), then six Platings of one material (four ingots, gems or shards each; Netherite Plating is a netherite ingot and two diamonds, Celestial Plating a Celestial Core and two Vibranium Shards). Platings around the board make a frame. Place it on the ground: it stands facing you.',
  'projecthero.guide.power_armour.entering_head': 'Stepping in and out',
  'projecthero.guide.power_armour.entering_text': 'Right-click a frame: it splits open, you walk up, turn your back to it and step in, then the plates close round you (legs, arms, chest, helmet) and the visor lights. Hold Shift+V to open up and step out: the frame stays standing. Armour you were wearing is stored while you are inside and given back when you leave. You cannot step in while another suit or transformation is on (Iron Man, the Hulk, an Omnitrix alien and the like).',
  'projecthero.guide.power_armour.cores_head': 'Power cores',
  'projecthero.guide.power_armour.cores_text': 'The suit runs on a swappable core: Fusion Cell (1000 E, output 6), Arc Core (2500 E, output 10) or Star Core (6000 E, output 16). Being sealed draws 1 E a second; moves cost more. An empty core leaves the suit unpowered: very slow, no moves, but you can always step out. Recharge cores in a Core Charger (furnace fuel or redstone) or with a Solar Plate.',
  'projecthero.guide.power_armour.tiers_head': 'Tiers',
  'projecthero.guide.power_armour.tiers_text': 'A stronger material makes a stronger suit with more upgrade slots and harder-hitting moves. Upgrade a frame at a smithing table: the Circuit Board as the template, the frame, and one Plating of the next tier. It keeps its modules, core, paint and integrity.',
  'projecthero.guide.power_armour.tier_line': '%s: %s',
  'projecthero.guide.power_armour.integrity_head': 'Integrity and repairs',
  'projecthero.guide.power_armour.integrity_text': 'The suit has its own integrity: every point of damage you take inside costs a point. At zero it breaks down, ejects you and stands as a wreck that will not seal until it is repaired. Right-click a parked frame with its material (or a Suit Repair Kit) to repair it. Dying inside: the suit ejects you and stands where you fell, a little more damaged.',
  'projecthero.guide.power_armour.editor_head': 'The Armour Editor',
  'projecthero.guide.power_armour.editor_text': 'Sneak + right-click a parked frame with an empty hand, anywhere, no station needed. Fit modules into the slots for each body part, swap the core, paint the plates, trim and glow, and check the stats. Each module needs some of the core’s output; a stronger core runs more. Pick Up packs the frame back into an item with everything on it.',
  'projecthero.guide.power_armour.controls': 'Controls (while sealed)',
  'projecthero.guide.power_armour.k.r': 'Servo Punch: a hydraulic punch with big knockback. Hold to charge: twice the damage, and it breaks weak blocks.',
  'projecthero.guide.power_armour.k.sr': 'Arm weapon: whatever is fitted on the arms (Gatling, Flamer, Tesla Coil, Rail Cannon, Power Fist).',
  'projecthero.guide.power_armour.k.g': 'Ground Stomp: a shockwave around you that throws mobs up and away.',
  'projecthero.guide.power_armour.k.sg': 'Servo Grip: grab a mob in front of you, then press again to throw it (bosses are too heavy).',
  'projecthero.guide.power_armour.k.z': 'Shoulder Charge: dash forward, ramming everything in the way.',
  'projecthero.guide.power_armour.k.sz': 'Core Overload: wind up for a second, then vent the core in a huge blast. Costs 35% of the core.',
  'projecthero.guide.power_armour.k.x': 'Jump Jets: a boosted leap. Landings never hurt (falls drain the core instead).',
  'projecthero.guide.power_armour.k.c': 'Back module: whatever is fitted on the back (Missile Pod, Shield Projector, Repair Drone, Sentry Turret, Thruster Pack).',
  'projecthero.guide.power_armour.k.v': 'Visor: outlines living things through walls (Ore Scanner and Target Lock work while it is on).',
  'projecthero.guide.power_armour.k.sv': 'Hold Shift+V: open up and step out.',
  'projecthero.guide.power_armour.modules_head': 'Modules',
  'projecthero.guide.power_armour.modules_text': 'Crafted from a Circuit Board with Salvaged Tech or Hero Alloy and a few parts. Each one mounts on one body part and shows on the suit.',
  'projecthero.guide.power_armour.passives_head': 'Passives',
  'projecthero.guide.power_armour.passives_text': 'Armour and toughness from the frame (your own armour is stored), no fall damage while powered, heavy footsteps, a half-block step-up, and no sprint-swimming in a suit this heavy.',
  'projecthero.jei.power_armour': 'Power Armour: craft a frame from six Platings and a Circuit Board, place it, put a core in and right-click to step in. See the Guidebook chapter.',
  'projecthero.jei.power_armour_upgrade': 'Smithing table: the Redstone Circuit Board, a frame and one Plating of the next tier upgrade the frame, keeping its modules, core and paint.',
};
for (const [id, [name, hint]] of Object.entries(TIERS)) {
  K['projecthero.power_armour.tier.' + id] = name;
  K['item.projecthero.power_armour_frame_' + id] = name + ' Power Armour';
  K['item.projecthero.power_armour_plating_' + id] = name + ' Plating';
  K['item.projecthero.power_armour_frame.tier_hint.' + id] = hint;
}
for (const [id, [name, desc]] of Object.entries(MODULES)) {
  K['item.projecthero.pa_module_' + id] = name;
  K['item.projecthero.pa_module_' + id + '.desc'] = desc;
}

let text = fs.readFileSync(file, 'utf8');
const eol = text.includes('\r\n') ? '\r\n' : '\n';
const existing = JSON.parse(text);
const add = [];
let replaced = 0;
for (const [k, v] of Object.entries(K)) {
  if (k in existing) {
    if (force && existing[k] !== v) {
      const re = new RegExp('(\\n  ' + JSON.stringify(k).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ': )(".*?(?<!\\\\)")(,?)(\\r?)$', 'm');
      text = text.replace(re, (m, a, b, c, d) => a + JSON.stringify(v) + c + d);
      replaced++;
    }
    continue;
  }
  add.push(`  ${JSON.stringify(k)}: ${JSON.stringify(v)}`);
}
if (add.length) {
  const close = text.lastIndexOf('}');
  let body = text.slice(0, close).replace(/\s+$/, '');
  body += ',' + eol + add.join(',' + eol) + eol;
  text = body + '}' + text.slice(close + 1);
}
JSON.parse(text);
fs.writeFileSync(file, text);
console.log(`en_us: +${add.length} keys, ${replaced} replaced`);
