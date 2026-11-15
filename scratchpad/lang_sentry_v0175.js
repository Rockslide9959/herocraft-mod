// v0.17.5 Sentry & the Void: adds the en_us keys key by key (never rewrites the file: other branches touch it too).
// Keeps the file's line endings and 2-space indent. Existing keys are left alone unless --force.
//   node scratchpad/lang_sentry_v0175.js [root] [--force]
const fs = require('fs');
const path = require('path');
const args = process.argv.slice(2);
const force = args.includes('--force');
const root = args.find(a => !a.startsWith('--')) || process.cwd();
const file = path.join(root, 'src/main/resources/assets/projecthero/lang/en_us.json');

const K = {
  // items / blocks
  'item.projecthero.sentry_serum': 'Sentry Serum',
  'item.projecthero.sentry_serum.desc1': 'Drink to gain the power of a million exploding suns.',
  'item.projecthero.sentry_serum.desc2': 'Every sun casts a shadow.',
  'item.projecthero.sentry_serum.hint': 'The Sentry Serum only comes from the sealed O.X.E. serum vat in The Vault (a Tier 4 dungeon), in the vault room behind Warden Kross\'s arena. The vat opens when Kross falls, and gives every player one serum. You can\'t craft it and it isn\'t in any other loot. Drinking it makes you the Sentry and replaces your Primary power.',
  'block.projecthero.oxe_serum_vat': 'O.X.E. Serum Vat',
  'block.projecthero.oxe_serum_vat.sealed': 'Sealed by O.X.E. Defeat Warden Kross to open it.',
  'block.projecthero.oxe_serum_vat.taken': 'You already took your serum from this vat.',
  'block.projecthero.oxe_serum_vat.got': 'You take a Sentry Serum from the vat.',
  'block.projecthero.oxe_serum_vat.hint': 'A sealed O.X.E. serum vat. It stands in The Vault\'s vault room and opens when Warden Kross is defeated. Use it to take your Sentry Serum (one per player).',
  'block.projecthero.void_matter': 'Void Matter',
  'block.projecthero.burnt_shadow': 'Burnt Shadow',
  'gamerule.projectheroVoidGriefing': 'The Void eats the world for good',
  'gamerule.projectheroVoidGriefing.description': 'When on, blocks the Void swallows during a Sentry takeover never come back.',
  // deaths
  'death.attack.projecthero.sentry_light': '%1$s was burned away by golden light',
  'death.attack.projecthero.sentry_light.player': '%1$s was burned away by %2$s\'s golden light',
  'death.attack.projecthero.sentry_void': '%1$s was swallowed by the Void',
  'death.attack.projecthero.sentry_void.player': '%1$s was erased by %2$s\'s Void',
  // messages
  'message.projecthero.sentry.already': 'You already have the power of the Sentry.',
  'message.projecthero.sentry.title': 'THE SENTRY',
  'message.projecthero.sentry.subtitle': 'and the Void',
  'message.projecthero.sentry.acquired': 'You have the power of a million exploding suns.',
  'message.projecthero.sentry.acquired_hint': 'The Sun meter fuels you; keep the Void meter low. Hold V to calm it. Open the Guidebook for the full kit.',
  'message.projecthero.sentry.no_sun': 'Not enough sunlight.',
  'message.projecthero.sentry.misfire': 'The Void moves your hand.',
  'message.projecthero.sentry.calming': 'You can\'t attack while you meditate.',
  'message.projecthero.sentry.no_target': 'Nothing to reach.',
  'message.projecthero.sentry.no_terrain': 'Terrain damage is off on this server.',
  'message.projecthero.sentry.dive_ground': 'You need to be in the air to dive.',
  'message.projecthero.sentry.let_out_locked': 'You can\'t let it out again yet (%s s).',
  'message.projecthero.sentry.let_out_low': 'The Void isn\'t strong enough yet (50 Void).',
  'message.projecthero.sentry.let_out': 'You let the Void out.',
  'message.projecthero.sentry.too_bright': 'Too bright there.',
  'message.projecthero.sentry.return_failed': 'The Void won\'t let go (below 90 Void).',
  'message.projecthero.sentry.spawn_held': 'The Void waits for you outside the spawn.',
  'message.projecthero.sentry.takeover.title': 'THE VOID',
  'message.projecthero.sentry.takeover.sub': 'Your body is not yours.',
  'message.projecthero.sentry.takeover.warn': 'The Void has taken %s. Hold right click on them with an empty hand to reach them.',
  'message.projecthero.sentry.won.title': 'BOB',
  'message.projecthero.sentry.won.sub': 'You threw the Void out.',
  'message.projecthero.sentry.room_broken': 'Someone reached you: a room breaks open.',
  'message.projecthero.sentry.room_failed': 'The dark pulls you back a room.',
  'message.projecthero.sentry.room_cleared': 'Room cleared (%s / %s).',
  // HUD
  'hud.projecthero.sentry.sun': 'Sun',
  'hud.projecthero.sentry.void': 'Void',
  'hud.projecthero.sentry.title': 'THE SENTRY',
  'hud.projecthero.sentry.title_void': 'THE VOID',
  'hud.projecthero.sentry.flying': 'Flying',
  'hud.projecthero.sentry.calm': 'Calm',
  'hud.projecthero.sentry.shield': 'Shield',
  'hud.projecthero.sentry.nightfall': 'Nightfall',
  'hud.projecthero.sentry.suns': 'A Million Suns',
  'hud.projecthero.sentry.locked': 'Let out',
  'hud.projecthero.sentry.takeover': 'THE VOID HAS YOU',
  'hud.projecthero.sentry.mind_locked': 'You can only watch... (%s s)',
  'hud.projecthero.sentry.mind_open': 'Fight your way back through the Shame Rooms.',
  'hud.projecthero.sentry.rooms': 'Rooms %s / %s   Grip %s',
  // the Shame Rooms
  'screen.projecthero.shame_rooms': 'The Shame Rooms',
  'screen.projecthero.shame_rooms.room': 'THE SHAME ROOMS: room %s of %s',
  'screen.projecthero.shame_rooms.hint': 'Click the points of light to fill the Light bar. Keep your cursor away from the dark.',
  'screen.projecthero.shame_rooms.light': 'Light %s   %s s',
  'screen.projecthero.shame_rooms.cleared': 'The room fills with light.',
  'screen.projecthero.shame_rooms.failed': 'The dark closes in...',
  // abilities
  'projecthero.sentry.ability.golden_strike': 'Golden Strike',
  'projecthero.sentry.ability.golden_strike.desc': 'A heavy punch (24). Every third punch hits for 30 and sends out a golden shockwave (14 around the target). Costs 2 Sun.',
  'projecthero.sentry.ability.sunbeam': 'Sunbeam',
  'projecthero.sentry.ability.sunbeam.desc': 'Hold: a solid beam of light from both hands, 32 blocks, 6 every 4 ticks and sets targets alight. Up to 6 s, 5 Sun a second. It can clash with other beams.',
  'projecthero.sentry.ability.molecular_grip': 'Molecular Grip',
  'projecthero.sentry.ability.molecular_grip.desc': 'Lift the creature you look at (20 blocks) in a golden field. Let go of G to throw it (14). Keep holding G for 1.5 s to tear it apart (55, +5 Void). Costs 10 Sun.',
  'projecthero.sentry.ability.unmake': 'Unmake',
  'projecthero.sentry.ability.unmake.desc': 'Turns a 3x3 patch of the blocks you look at into drifting light. No drops. Never bedrock, chests or portals. Costs 8 Sun, +3 Void.',
  'projecthero.sentry.ability.sun_dive': 'Sun Dive',
  'projecthero.sentry.ability.sun_dive.desc': 'From the air: dive down and slam a golden crater, 32 to everything within 5 blocks. Costs 12 Sun.',
  'projecthero.sentry.ability.million_suns': 'A Million Suns',
  'projecthero.sentry.ability.million_suns.desc': 'A 3 s charge, hanging in the air, then an explosion of light: 140 at the centre to 70 at 12 blocks. Costs 60 Sun, +25 Void. 60 s cooldown.',
  'projecthero.sentry.ability.healing_light': 'Healing Light',
  'projecthero.sentry.ability.healing_light.desc': 'Heals you, your squad, your pets and villagers within 10 blocks for 8 and clears bad effects. Lowers the Void by 8, and 1 more for each one healed. Costs 20 Sun.',
  'projecthero.sentry.ability.sentinel_shield': 'Sentinel Shield',
  'projecthero.sentry.ability.sentinel_shield.desc': 'A golden dome (5 blocks) that stops enemy projectiles for 6 s. Costs 15 Sun.',
  'projecthero.sentry.ability.flight': 'Flight',
  'projecthero.sentry.ability.flight.desc': 'Press X to take off or land: 22 blocks a second, 44 sprinting.',
  'projecthero.sentry.ability.sonic_dash': 'Sonic Boom',
  'projecthero.sentry.ability.sonic_dash.desc': 'Double-tap X: a sonic-boom dash 18 blocks along your look, 14 to everything you pass. Costs 5 Sun.',
  'projecthero.sentry.ability.calm': 'Calm',
  'projecthero.sentry.ability.calm.desc': 'Hold V to meditate: the Void drains 6 a second, but you can\'t attack.',
  'projecthero.sentry.ability.let_it_out': 'Let It Out',
  'projecthero.sentry.ability.let_it_out.desc': 'At 50 Void or more: become the Void yourself (Void Form). Stronger, but the Void climbs 1 a second.',
  'projecthero.sentry.ability.shadow_lash': 'Shadow Lash',
  'projecthero.sentry.ability.shadow_lash.desc': 'Tendrils whip everything in front of you (6 blocks): 20 each. +1 Void.',
  'projecthero.sentry.ability.erase': 'Erase',
  'projecthero.sentry.ability.erase.desc': 'Wipes the creature you look at (24 blocks) into a burnt shadow: 100. A kill leaves its shadow on the ground. +6 Void, 20 s cooldown.',
  'projecthero.sentry.ability.grasp': 'Grasp',
  'projecthero.sentry.ability.grasp.desc': 'Tendrils drag up to five creatures within 14 blocks to you (6 each). +2 Void.',
  'projecthero.sentry.ability.void_rift': 'Void Rift',
  'projecthero.sentry.ability.void_rift.desc': 'A black hole where you look. For 4 s it pulls everything within 7 blocks in and crushes it (6 every half second). +5 Void.',
  'projecthero.sentry.ability.nightfall': 'Nightfall',
  'projecthero.sentry.ability.nightfall.desc': 'A dome of darkness (10 blocks, 10 s): enemies inside get Darkness and Weakness; you regenerate. +6 Void.',
  'projecthero.sentry.ability.shadow_step': 'Shadow Step',
  'projecthero.sentry.ability.shadow_step.desc': 'Teleport to any dark spot you look at (light 7 or less, 40 blocks). +1 Void.',
  'projecthero.sentry.ability.return': 'Return',
  'projecthero.sentry.ability.return.desc': 'Go back to being the Sentry. Only works below 90 Void.',
  // squad
  'projecthero.squad.identity.sentry': 'Sentry',
  // the Guidebook chapter
  'projecthero.guide.sentry': 'Sentry & the Void',
  'projecthero.guide.sentry.tier': 'Hero-Tier Primary power',
  'projecthero.guide.sentry.body': 'Bob Reynolds has the power of a million exploding suns, and the Void is his other half. You fight with golden light, flight and molecular control while keeping a darkness inside you in check. Let it grow too far and it takes your body.',
  'projecthero.guide.sentry.getting': 'Getting it',
  'projecthero.guide.sentry.getting.body': 'Drink the Sentry Serum, a golden vial. It only comes from the sealed O.X.E. serum vat in The Vault (Tier 4 dungeon), in the vault room behind Warden Kross\'s arena. The vat opens when Kross falls and gives every player one serum. It can\'t be crafted and isn\'t in any other loot. It replaces your Primary power.',
  'projecthero.guide.sentry.meters': 'Sun and Void',
  'projecthero.guide.sentry.meters.body': 'Sun (the gold Gauge) pays for the Sentry moves. It fills 6 a second in direct sunlight, 2 in daytime shade, 0.5 under the night sky and not at all in the dark. Sun also heals you slowly. Void (the black Hairline under it) is the danger. It rises with the big moves, heavy hits (8 damage or more), every kill, standing at night (0.25 a second) and standing in the dark (0.5 a second). It falls 1 a second in daylight, when you heal others, and 6 a second while you hold V.',
  'projecthero.guide.sentry.thresholds': 'The Void\'s thresholds',
  'projecthero.guide.sentry.thresholds.body': '50: whispers, black veins creep in from the edges of your screen, and your golden glow flickers dark. 75: your keys sometimes misfire into Void moves, and your aim drifts toward the nearest living thing. 90: a heartbeat, the sky dims around you, and everything you hit leaves a burnt shadow on the ground. 100: the Void takes over.',
  'projecthero.guide.sentry.body_stats': 'The body',
  'projecthero.guide.sentry.body_stats.body': '+30 health (+40 in Void Form), 70% less damage taken (75% in Void Form), +12 melee (+16), 30% faster, no hunger. No fall damage while you fly or dive.',
  'projecthero.guide.sentry.controls': 'Sentry controls (H selects the power)',
  'projecthero.guide.sentry.void_controls': 'Void Form controls',
  'projecthero.guide.sentry.takeover': 'The takeover',
  'projecthero.guide.sentry.takeover.body': 'At 100 Void your body is not yours. The camera pulls out behind you and the Void flies you on its own. It attacks everything living around it: monsters, animals, other players, your squad and your pets. It uses the Void Form moves at full strength, hunts the nearest living thing, and drifts toward players and villages when nothing is near. Creatures it kills are absorbed and heal it.',
  'projecthero.guide.sentry.world': 'It eats the world',
  'projecthero.guide.sentry.world.body': 'A growing ring of black void matter spreads out from the Void and swallows blocks into nothing (up to 16 blocks out). The sky goes dark around it and the clock shows midnight near it (only on screen). About a minute after you get control back, everything it ate grows back. It never eats bedrock, chests and other containers, portals, or anything inside spawn protection. Server owners can make it permanent with /gamerule projectheroVoidGriefing true.',
  'projecthero.guide.sentry.rooms': 'The Shame Rooms',
  'projecthero.guide.sentry.rooms.body': 'For the first 30 s you can only watch. Then the Shame Rooms open: a black room with memory doors. Click the drifting points of light to fill the Light bar while shapes of darkness chase your cursor and drain it. Fill it to clear the room. If the bar empties or 45 s run out, you fall back a room and have to wait 20 s more. There are three rooms, each faster and darker, and the longer the takeover runs the harder they get.',
  'projecthero.guide.sentry.help': 'Help from outside',
  'projecthero.guide.sentry.help.body': 'Other players can reach you. They walk up to the Void and hold right click on it with an empty hand. Each one drains the Void\'s grip (shown under the takeover view) and takes its attention, and its hits. When the grip breaks, a room breaks open on its own, and a weaker grip makes the rooms easier. Daylight weakens the Void a little; night makes it stronger.',
  'projecthero.guide.sentry.ending': 'Coming back',
  'projecthero.guide.sentry.ending.body': 'Clear all three rooms to throw the Void out: a golden flash, you drop to your knees, the Void resets to 40, and you can\'t use Let It Out (Shift+V) for 5 minutes. Dying also ends it (you respawn with 60 Void). Logging out ends it too, and the world still grows back.',
  'projecthero.guide.sentry.commands': 'Commands (operators)',
  'projecthero.guide.sentry.commands.body': '/projecthero power tune sentry sun <0-100> | void <0-100> | cooldowns | takeover | release. Grant the power with /projecthero power grant <player> sentry.',
};

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
