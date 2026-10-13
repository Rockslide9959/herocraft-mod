// v0.16.1 Galactus: The Devourer lang keys. Idempotent: adds missing keys before the closing brace of en_us.json and
// rewrites existing ones in place. Run from the repo (or worktree) root: node scratchpad/lang_v0161_galactus.js [root]
const fs = require('fs');
const path = require('path');
const root = process.argv[2] || process.cwd();
const file = path.join(root, 'src/main/resources/assets/projecthero/lang/en_us.json');

const L = {
  // ---------------------------------------------------------------- entities, items, blocks
  'entity.projecthero.galactus': 'Galactus',
  'entity.projecthero.galactus_weak_point': 'Weak Point of Galactus',
  'entity.projecthero.galactus_weak_point.0': 'the Chest Emblem',
  'entity.projecthero.galactus_weak_point.1': 'the Helmet Crest',
  'entity.projecthero.galactus_weak_point.2': 'the Left Gauntlet',
  'entity.projecthero.galactus_weak_point.3': 'the Right Gauntlet',
  'entity.projecthero.galactus_hand': 'Hand of Galactus',
  'entity.projecthero.silver_surfer': 'Silver Surfer',
  'entity.projecthero.element_converter': 'Element Converter',
  'entity.projecthero.punisher': 'Punisher',
  'entity.projecthero.power_pylon': 'Power Pylon',
  'entity.projecthero.worldship': 'Taa II, the Worldship',
  'entity.projecthero.silver_surfer_board': "Silver Surfer's Board",
  'block.projecthero.heralds_beacon': "Herald's Beacon",
  'block.projecthero.heralds_beacon.hint': 'Right-click at night to call the Devourer.',
  'block.projecthero.heralds_beacon.hint2': 'Survival only. Not on Peaceful. Bring friends.',
  'block.projecthero.consumed_ground': 'Consumed Ground',
  'block.projecthero.cosmic_hazard': 'Cosmic Hazard',
  'block.projecthero.galactus_helmet_crest': "Galactus' Helmet Crest",
  'block.projecthero.galactus_helmet_crest.hint': 'Taken from the Devourer of Worlds.',
  'item.projecthero.heralds_beacon': "Herald's Beacon",
  'item.projecthero.galactus_helmet_crest': "Galactus' Helmet Crest",
  'item.projecthero.ultimate_nullifier_part': 'Ultimate Nullifier Part',
  'item.projecthero.ultimate_nullifier_part.hint': 'Keep it. The Silver Surfer assembles the Nullifier once every part is held.',
  'item.projecthero.ultimate_nullifier': 'Ultimate Nullifier',
  'item.projecthero.ultimate_nullifier.hint': 'Hold right-click on Galactus for 3 seconds.',
  'item.projecthero.ultimate_nullifier.hint2': 'The one weapon the Devourer fears.',
  'item.projecthero.silver_surfer_board': "Silver Surfer's Board",
  'item.projecthero.silver_surfer_board.hint': 'Right-click to ride. Fly where you look; sprint to boost; jump climbs.',
  'item.projecthero.silver_surfer_board.hint2': 'Sneak + right-click the board to put it away.',
  'item.projecthero.power_cosmic': 'The Power Cosmic',
  'item.projecthero.power_cosmic.hint': 'Right-click: a cosmic blast.',
  'item.projecthero.power_cosmic.hint2': 'Sneak + right-click: a short shield. Best in the off hand.',
  'item.projecthero.silver_surfer_spawn_egg': 'Silver Surfer Spawn Egg',
  'item.projecthero.punisher_spawn_egg': 'Punisher Spawn Egg',

  // ---------------------------------------------------------------- the event
  'event.projecthero.galactus': 'Galactus: The Devourer',
  'event.projecthero.galactus.bar.arrival': 'A cosmic signal answers - %s s',
  'event.projecthero.galactus.bar.herald': 'The Herald of Galactus',
  'event.projecthero.galactus.bar.turn': 'The Herald turns',
  'event.projecthero.galactus.bar.worldship': 'World Consumption %s%% - converters destroyed %s/%s',
  'event.projecthero.galactus.bar.galactus': 'World Consumption %s%% - pylons %s - weak points broken %s/%s',
  'event.projecthero.galactus.bar.nullifier': 'World Consumption %s%% - pylons %s - the Nullifier! (%s/%s broken)',
  'event.projecthero.galactus.bar.retreat': 'Galactus retreats',
  'event.projecthero.galactus.bar.consumed': 'The world is consumed',
  'boss.projecthero.galactus.chat': '%s: %s',

  'title.projecthero.galactus.start': 'A COSMIC SIGNAL',
  'title.projecthero.galactus.start.sub': 'Something vast has heard it. The stars come out at noon.',
  'title.projecthero.galactus.herald': 'THE SILVER SURFER',
  'title.projecthero.galactus.herald.sub': 'The Herald of Galactus comes first',
  'title.projecthero.galactus.turn': 'THE HERALD TURNS',
  'title.projecthero.galactus.turn.sub': 'He will fight beside you now',
  'title.projecthero.galactus.worldship': 'THE WORLDSHIP',
  'title.projecthero.galactus.worldship.sub': '%s Element Converters fall. Destroy them!',
  'title.projecthero.galactus.consumption': 'World Consumption %s%%',
  'title.projecthero.galactus.consumption.sub': 'The land is being eaten',
  'title.projecthero.galactus.galactus': 'GALACTUS',
  'title.projecthero.galactus.galactus.sub': 'Devourer of Worlds. He cannot be killed.',
  'title.projecthero.galactus.unsealed': 'THE PYLONS ARE DOWN',
  'title.projecthero.galactus.unsealed.sub': 'His weak points are open: emblem, crest, gauntlets',
  'title.projecthero.galactus.nullifier': 'THE ULTIMATE NULLIFIER',
  'title.projecthero.galactus.nullifier.sub': 'Gather all %s parts - the Surfer will assemble it',
  'title.projecthero.galactus.assembled': 'THE NULLIFIER IS READY',
  'title.projecthero.galactus.assembled.sub': '%s holds it. Aim at Galactus and hold right-click',
  'title.projecthero.galactus.nullified': 'GALACTUS FALTERS',
  'title.projecthero.galactus.nullified.sub': '%s turned the Ultimate Nullifier on him',
  'title.projecthero.galactus.consumed': 'THE WORLD IS CONSUMED',
  'title.projecthero.galactus.consumed.sub': 'Galactus feeds',

  'message.projecthero.galactus.joined': 'You have been drawn into the fight against Galactus!',
  'message.projecthero.galactus.no_escape': 'The Power Cosmic walls you in -- there is no leaving',
  'message.projecthero.galactus.converter_shielded': 'Shielded -- destroy its Punisher guards first!',
  'message.projecthero.galactus.weak_point_sealed': 'Sealed by the Power Pylons -- break them first!',
  'message.projecthero.galactus.converter_down': 'An Element Converter falls (%s of %s) and drops a Nullifier part!',
  'message.projecthero.galactus.pylon_down': 'A Power Pylon shatters (%s left)',
  'message.projecthero.galactus.weak_point_broken': '%s is broken! (%s of %s)',
  'message.projecthero.galactus.grabbed': 'Caught in his hand! Your allies must hit the hand to free you.',
  'message.projecthero.galactus.grabbed_all': '%s is in the hand of Galactus -- hit the hand!',
  'message.projecthero.galactus.grip_broken': 'The grip breaks -- %s falls free!',
  'message.projecthero.galactus.fighter_down': '%s has fallen. Walk back into the arena to fight on.',
  'message.projecthero.galactus.all_down': 'Every defender has fallen. Galactus feeds unopposed, then moves on.',
  'message.projecthero.galactus.defeat': 'The world was consumed. The land returns as Galactus moves on -- but he will be back.',
  'message.projecthero.galactus.first_clear': "You drove off the Devourer of Worlds! The Silver Surfer leaves you his Board and a shard of the Power Cosmic.",
  'message.projecthero.galactus.signal': 'A cosmic signal tears across the sky. Something answers.',
  'message.projecthero.galactus.already': 'The signal has already been sent',
  'message.projecthero.galactus.disabled': 'Galactus is switched off on this server',
  'message.projecthero.galactus.survival_only': 'Only a survivor can send the signal (Survival mode)',
  'message.projecthero.galactus.peaceful': 'The Devourer does not come on Peaceful',
  'message.projecthero.galactus.night_only': 'The beacon only reaches the stars at night',
  'message.projecthero.galactus.refused': 'The signal is lost -- Galactus is already coming somewhere near',
  'message.projecthero.galactus.nullifier_no_target': 'There is no Galactus here to use it on',
  'message.projecthero.galactus.nullifier_missed': 'The Nullifier must be aimed at Galactus',
  'message.projecthero.galactus.nullifier_charging': 'The Ultimate Nullifier charges... %s%%',

  // ---------------------------------------------------------------- dialogue
  'boss.projecthero.galactus.surfer.say.arrival': 'I am the Herald of Galactus. This world has been chosen. Stand aside.',
  'boss.projecthero.galactus.surfer.say.turn1': 'Enough... you fight like a world that wishes to live.',
  'boss.projecthero.galactus.surfer.say.turn2': 'I led him to so many worlds. I will not lead him to yours.',
  'boss.projecthero.galactus.surfer.say.turn3': 'Then let the Herald defy his master. I fight with you now.',
  'boss.projecthero.galactus.surfer.say.part': 'A piece of the Ultimate Nullifier! Keep it safe -- we will need every one.',
  'boss.projecthero.galactus.surfer.say.unsealed': 'His shields are down! Strike the emblem, the crest, the gauntlets!',
  'boss.projecthero.galactus.surfer.say.nullifier': 'Now! Bring me every part of the Nullifier -- I will make it whole.',
  'boss.projecthero.galactus.surfer.say.assembled': 'It is done. Turn it on him. Even Galactus fears this.',
  'boss.projecthero.galactus.surfer.say.recovered': 'A part was lost. I have found it again -- take it.',
  'boss.projecthero.galactus.surfer.say.shield': 'Hold on -- the Power Cosmic will shield you.',
  'boss.projecthero.galactus.surfer.say.farewell': 'He is gone. Your world endures. Remember the stars that shone at noon.',
  'boss.projecthero.galactus.galactus.say.hunger': 'HERALD. YOU FORGET YOURSELF. NO MATTER. THIS WORLD WILL STILL FEED ME.',
  'boss.projecthero.galactus.galactus.say.arrival': 'I AM GALACTUS. YOU ARE NOTHING TO ME.',
  'boss.projecthero.galactus.galactus.say.retreat1': 'THE ULTIMATE NULLIFIER... IN THE HANDS OF CHILDREN.',
  'boss.projecthero.galactus.galactus.say.retreat2': 'VERY WELL. KEEP YOUR SMALL WORLD. THE UNIVERSE IS WIDE, AND I AM PATIENT.',
  'boss.projecthero.galactus.galactus.say.consumed': 'IT IS DONE. THIS WORLD IS PART OF GALACTUS NOW.',

  // ---------------------------------------------------------------- the guide chapter
  'projecthero.guide.galactus': 'Galactus: The Devourer',
  'projecthero.guide.galactus.body': 'The endgame event. A cosmic signal reaches the Devourer of Worlds: his Herald comes first, then his Worldship, then Galactus himself -- sixty blocks tall and impossible to kill. The only way to win is the Ultimate Nullifier.',
  'projecthero.guide.galactus.ladder': 'Where It Sits',
  'projecthero.guide.galactus.ladder.body': 'The top of the threat ladder: after the Frost Giant Assault and the Ultron Contagion. Bring a full party, flight, and ranged damage. One Galactus at a time per area.',
  'projecthero.guide.galactus.start': 'Starting It',
  'projecthero.guide.galactus.start.body': "Craft a Herald's Beacon (end crystals, a netherite ingot, a nether star, amethyst blocks, crying obsidian and a beacon), place it and right-click it at night in Survival. Once your world has beaten the Ultron Contagion finale or a Frost Giant Assault, a cosmic signal may also bring him on its own (a small chance each in-game day, never on Peaceful).",
  'projecthero.guide.galactus.arena': 'The Arena',
  'projecthero.guide.galactus.arena.body': 'An 80-block ring round the beacon, edged in violet and silver. The sky turns deep purple and the stars shine at noon. Fighters are kept in; anyone who walks in joins; a fallen fighter is back in the fight by walking back inside. Everyone down at once is a loss.',
  'projecthero.guide.galactus.herald': 'Act 1: The Herald',
  'projecthero.guide.galactus.herald.body': 'The Silver Surfer duels you from the air: Power Cosmic bolts (dodge them), a light-speed dash along a silver line (step off it), a reflective shield (projectiles bounce back -- use melee) and a cosmic lift that throws you into the sky. At 10% health he stops and turns: from then on he fights beside you and cannot be hurt.',
  'projecthero.guide.galactus.worldship': 'Act 2: The Worldship',
  'projecthero.guide.galactus.worldship.body': 'The Worldship hangs overhead and three Element Converters (one more per two extra fighters, at most five) crash down. Each eats the land round it -- grass, dirt, stone and sand turn to grey Consumed Ground out to 16 blocks -- and fills the World Consumption bar. Each is shielded while its two Punisher guards live. Destroy it and it drops an Ultimate Nullifier part and the land comes back. Nothing near a chest or furnace is ever touched, and every block is put back.',
  'projecthero.guide.galactus.consumption': 'World Consumption',
  'projecthero.guide.galactus.consumption.body': 'Each landed converter adds 5% a minute; in Act 3 each standing Power Pylon adds 1.5%. At 100% the planet is consumed: a loss. Everything he changed is put back either way.',
  'projecthero.guide.galactus.galactus': 'Act 3: Galactus',
  'projecthero.guide.galactus.galactus.body': 'He stands at the arena\'s edge and turns to face you. His body takes only a fifth of any hit. Four Power Pylons feed him: while any stands he regenerates and his weak points are sealed -- ground fighters, break them. Then the weak points: the Chest Emblem, the Helmet Crest and both Gauntlets take full damage, and breaking one takes a chunk more. He cannot drop below 1 health.',
  'projecthero.guide.galactus.moves': 'His Moves',
  'projecthero.guide.galactus.moves.body': 'Eye Beams: a violet line glows on the ground for 2 s, then the beam sweeps it -- get off the line. Hand Grab: a shadow grows for 1.5 s; caught, you are lifted to his face and flung -- allies, hit the hand to break the grip. Matter Transmutation: the ground under you is marked, then turns to burning cosmic hazard -- move. Disintegration Wave: a ring races out from his feet -- jump or fly over it. Punisher Summons: two more robots drop.',
  'projecthero.guide.galactus.punishers': 'Punishers',
  'projecthero.guide.galactus.punishers.body': '8-block war robots: a heavy punch up close, a blaster-gauntlet bolt at range, and a ground slam telegraphed by a violet ring. Guards never stray far from their converter.',
  'projecthero.guide.galactus.nullifier': 'The Ultimate Nullifier',
  'projecthero.guide.galactus.nullifier.body': 'When every weak point is broken -- or his health is worn down to a fifth -- the Surfer calls for the parts. Once fighters hold every part, he assembles the Nullifier and hands it over. Aim at Galactus and hold right-click for 3 seconds: he raises a hand, speaks, and is gone in a burst of Power Cosmic. The Worldship leaves and the land is restored.',
  'projecthero.guide.galactus.rewards': 'Rewards',
  'projecthero.guide.galactus.rewards.body': "The beacon becomes a reward chest: Galactus' Helmet Crest, diamonds, netherite scrap, emeralds, amethyst and enchanted books, scaled for the party. Your first clear also gives you the Silver Surfer's Board (a fast flying mount: fly where you look, sprint to boost) and the Power Cosmic (a cosmic blast; sneak for a short shield).",
  'projecthero.guide.galactus.commands': 'Commands',
  'projecthero.guide.galactus.commands.body': 'Operators: /projecthero raid start galactus (here, any time), /projecthero raid end galactus (everything is put back), /projecthero raid advance galactus (on to the next stage). Settings live in config/projecthero_galactus.json.',
};

let text = fs.readFileSync(file, 'utf8');
const crlf = text.includes('\r\n');
text = text.replace(/\r\n/g, '\n');
const esc = (s) => JSON.stringify(s);
let added = 0;
let changed = 0;
for (const [k, v] of Object.entries(L)) {
  const re = new RegExp('^(\\s*)' + esc(k).replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ':\\s*".*?"(,?)$', 'm');
  if (re.test(text)) {
    text = text.replace(re, (m, ind, comma) => ind + esc(k) + ': ' + esc(v) + comma);
    changed++;
  } else {
    const end = text.lastIndexOf('}');
    const before = text.slice(0, end).replace(/\s*$/, '');
    text = before + ',\n  ' + esc(k) + ': ' + esc(v) + '\n}\n';
    added++;
  }
}
JSON.parse(text); // must still be valid
if (crlf) text = text.replace(/\n/g, '\r\n');
fs.writeFileSync(file, text, 'utf8');
console.log('galactus v0.16.1 lang: added ' + added + ', updated ' + changed);
