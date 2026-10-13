// v0.16.1 Frost Giant Assault lang keys. Idempotent: adds missing keys before the closing brace of en_us.json and
// rewrites existing ones in place. Run from the repo (or worktree) root: node scratchpad/lang_v0161_frost.js [root]
const fs = require('fs');
const path = require('path');
const root = process.argv[2] || process.cwd();
const file = path.join(root, 'src/main/resources/assets/projecthero/lang/en_us.json');

const L = {
  // ---------------------------------------------------------------- the event
  'event.projecthero.frost_giants': 'Frost Giant Assault',
  'event.projecthero.frost_giants.bar.countdown': 'Frost Giant Assault - the rift opens in %s s',
  'event.projecthero.frost_giants.bar.wave': 'Frost Giant Assault - wave %s/%s - %s giants - braziers lit %s/%s',
  'event.projecthero.frost_giants.bar.breather': 'Frost Giant Assault - wave %s/%s repelled - braziers lit %s/%s',
  'event.projecthero.frost_giants.bar.boss': 'King Laufey - braziers lit %s/%s',
  'event.projecthero.frost_giants.bar.boss_ymir': 'Ymir reaches through - braziers lit %s/%s',
  'event.projecthero.frost_giants.bar.boss_drained': 'The Casket is broken - finish Laufey (braziers %s/%s)',
  'event.projecthero.frost_giants.bar.victory': 'The rift is closing',
  'event.projecthero.frost_giants.bar.laufey_casket': 'King Laufey - The Casket Opens',
  'event.projecthero.frost_giants.bar.laufey_ymir': "King Laufey - Ymir's Wrath",
  'event.projecthero.frost_giants.bar.laufey_drained': 'King Laufey - Drained',

  'title.projecthero.frost_giants.start': 'THE RIFT OPENS',
  'title.projecthero.frost_giants.start.sub': 'The Frost Giants of Jotunheim march. Keep the braziers lit.',
  'title.projecthero.frost_giants.wave': 'Wave %s of %s',
  'title.projecthero.frost_giants.wave.sub': '%s giants come down the frost beam',
  'title.projecthero.frost_giants.all_out': 'THE FIRES ARE OUT',
  'title.projecthero.frost_giants.all_out.sub': 'The cold bites twice as hard. Relight a brazier!',
  'title.projecthero.frost_giants.laufey': 'KING LAUFEY',
  'title.projecthero.frost_giants.laufey.sub': 'The King of Jotunheim descends',
  'title.projecthero.frost_giants.casket': 'THE CASKET OPENS',
  'title.projecthero.frost_giants.casket.sub': 'The blizzard doubles and the fires falter',
  'title.projecthero.frost_giants.ymir': "YMIR'S WRATH",
  'title.projecthero.frost_giants.ymir.sub': 'Stagger Laufey, then break the Casket',
  'title.projecthero.frost_giants.stagger': 'LAUFEY STAGGERS',
  'title.projecthero.frost_giants.stagger.sub.lightning': 'Lightning! Strike the Casket now',
  'title.projecthero.frost_giants.stagger.sub.burst': 'He reels! Strike the Casket now',
  'title.projecthero.frost_giants.stagger.sub.grasp': 'His grasp is broken! Strike the Casket now',
  'title.projecthero.frost_giants.casket_broken': 'THE CASKET SHATTERS',
  'title.projecthero.frost_giants.casket_broken.sub': 'Ymir is torn back through the rift. Finish the king!',
  'title.projecthero.frost_giants.victory': 'JOTUNHEIM FALLS BACK',
  'title.projecthero.frost_giants.victory.sub': 'The rift collapses and the snow melts away',

  'message.projecthero.frost_giants.joined': "You've been drawn into the Frost Giant Assault!",
  'message.projecthero.frost_giants.no_escape': 'The blizzard walls you in -- there is no leaving',
  'message.projecthero.frost_giants.wave_cleared': 'Wave %s repelled. Horns sound from the rift...',
  'message.projecthero.frost_giants.waves_done': 'The host is broken. Something vast stirs above the rift...',
  'message.projecthero.frost_giants.fighter_down': '%s has frozen. Walk back into the arena to fight on.',
  'message.projecthero.frost_giants.defeat': 'Every defender has fallen. The Rift Anchor shatters -- Jotunheim claims this land.',
  'message.projecthero.frost_giants.brazier_out': 'A Rune Brazier has gone out! (%s of %s still lit)',
  'message.projecthero.frost_giants.brazier_relit': '%s relit a Rune Brazier (%s of %s lit)',
  'message.projecthero.frost_giants.relighting': 'Relighting the brazier... %s%%',
  'message.projecthero.frost_giants.imprisoned': 'Frozen in! Attack to crack the ice -- or let your allies break it.',
  'message.projecthero.frost_giants.casket_shielded': "The Casket is sealed -- stagger Laufey first!",
  'message.projecthero.frost_giants.bestiary': 'New Guidebook entry (Frost Giant Assault): %s',
  'message.projecthero.frost_giants.casket': 'You claimed the Casket of Ancient Winters! Hold it in your off hand.',
  'message.projecthero.frost_giants.natural': 'A Jotunheim Rune Stone has appeared in your pack. Place it at night if you dare.',
  'message.projecthero.frost_giants.already': 'The rift is already open',
  'message.projecthero.frost_giants.survival_only': 'Only a survivor can open the rift (Survival mode)',
  'message.projecthero.frost_giants.peaceful': 'Jotunheim does not answer on Peaceful',
  'message.projecthero.frost_giants.night_only': 'The Rune Stone only wakes at night',
  'message.projecthero.frost_giants.refused': 'Another Frost Giant Assault is already running nearby',

  'boss.projecthero.frost_giants.chat': '<%s> %s',
  'boss.projecthero.frost_giants.heimdall': 'Heimdall',
  'boss.projecthero.frost_giants.heimdall.say.march': 'The Jotnar march on Midgard.',
  'boss.projecthero.frost_giants.laufey.say.intro': 'Your little fires will not save you. Winter has a king, and he has come.',
  'boss.projecthero.frost_giants.laufey.say.spear': 'Let the cold find your heart.',
  'boss.projecthero.frost_giants.laufey.say.guard': 'Guard! To your king!',
  'boss.projecthero.frost_giants.laufey.say.grasp': 'Be still. Be still forever.',
  'boss.projecthero.frost_giants.laufey.say.casket_open': 'Behold the Casket of Ancient Winters -- and despair.',
  'boss.projecthero.frost_giants.laufey.say.breath': 'Breathe the winter of the first world.',
  'boss.projecthero.frost_giants.laufey.say.pillars': 'Jotunheim rises beneath your feet.',
  'boss.projecthero.frost_giants.laufey.say.slam': 'Kneel!',
  'boss.projecthero.frost_giants.laufey.say.ymir': 'Father of frost, first of us all -- reach through!',
  'boss.projecthero.frost_giants.laufey.say.stagger': 'Impossible...',
  'boss.projecthero.frost_giants.laufey.say.refuse': 'While the Casket endures, so do I.',
  'boss.projecthero.frost_giants.laufey.say.casket_broken': 'No! The winter... it slips from me!',
  'boss.projecthero.frost_giants.laufey.say.death': 'Jotunheim... will remember...',

  'hud.projecthero.frost_giants.warm': 'Warm -- Rune Brazier',
  'hud.projecthero.frost_giants.cold': 'Cold %s%%',
  'hud.projecthero.frost_giants.relight_hint': 'Hold use to relight',

  // ---------------------------------------------------------------- entities
  'entity.projecthero.rime_hound': 'Rime Hound',
  'entity.projecthero.jotun_raider': 'Jotun Raider',
  'entity.projecthero.frost_shaman': 'Frost Shaman',
  'entity.projecthero.ice_brute': 'Ice Brute',
  'entity.projecthero.frost_giant_warlord': 'Frost Giant Warlord',
  'entity.projecthero.king_laufey': 'King Laufey',
  'entity.projecthero.rune_brazier': 'Rune Brazier',
  'entity.projecthero.ice_casket': 'Casket of Ancient Winters',
  'entity.projecthero.ice_prison': 'Ice Prison',
  'entity.projecthero.ymir_arm': 'Ymir',
  'entity.projecthero.ice_javelin': 'Ice Javelin',

  // ---------------------------------------------------------------- blocks and items
  'block.projecthero.jotunheim_rune_stone': 'Jotunheim Rune Stone',
  'block.projecthero.jotunheim_rune_stone.hint': 'Place it and right-click it at night to open a rift to Jotunheim.',
  'block.projecthero.jotunheim_rune_stone.hint2': 'Starts the Frost Giant Assault. Never on Peaceful.',
  'block.projecthero.jotun_skull': 'Jotun Skull',
  'block.projecthero.jotun_skull.hint': 'A trophy from the Frost Giant Assault.',
  'item.projecthero.rime_shard': 'Rime Shard',
  'item.projecthero.rime_shard.hint': 'Smithing table: netherite armour or Mjolnir / Stormbreaker + a shard, no template.',
  'item.projecthero.rime_shard.hint2': 'Also crafts a Jotunheim Rune Stone (in place of the Heart of the Sea).',
  'item.projecthero.casket_of_ancient_winters': 'Casket of Ancient Winters',
  'item.projecthero.casket_of_ancient_winters.passive': 'Off hand: immune to freezing, walk on powder snow.',
  'item.projecthero.casket_of_ancient_winters.breath': "Right-click: Winter's Breath, a 2 s frost cone (20 s cooldown).",
  'item.projecthero.casket_of_ancient_winters.prison': 'Sneak + right-click: Ice Prison on a mob for 5 s (40 s cooldown).',
  'item.projecthero.casket_of_ancient_winters.storm': "Thor: your lightning near a Casket holder freezes what it hits.",
  'item.projecthero.casket_of_ancient_winters.lore': 'The winter of the first world, sealed in a box.',
  'item.projecthero.ice_javelin': 'Ice Javelin',
  'item.projecthero.rimed': 'Rimed: +1 armour toughness (full set: immune to freezing)',
  'item.projecthero.rime_touched': 'Rime-touched: thrown hits freeze',
  'item.projecthero.rime_hound_spawn_egg': 'Rime Hound Spawn Egg',
  'item.projecthero.jotun_raider_spawn_egg': 'Jotun Raider Spawn Egg',
  'item.projecthero.frost_shaman_spawn_egg': 'Frost Shaman Spawn Egg',
  'item.projecthero.ice_brute_spawn_egg': 'Ice Brute Spawn Egg',
  'item.projecthero.frost_giant_warlord_spawn_egg': 'Frost Giant Warlord Spawn Egg',
  'item.projecthero.king_laufey_spawn_egg': 'King Laufey Spawn Egg',
  'message.projecthero.casket.cooldown': '%s recharging (%s s)',
  'message.projecthero.casket.breath': "Winter's Breath",
  'message.projecthero.casket.prison': 'Ice Prison',
  'message.projecthero.casket.no_target': 'No creature in sight to imprison',
  'message.projecthero.casket.resists': '%s resists the Ice Prison',

  // ---------------------------------------------------------------- the Guidebook chapter
  'projecthero.guide.frost_giants': 'Frost Giant Assault',
  'projecthero.guide.frost_giants.body': "A rift to Jotunheim tears open above your base during a blizzard, and King Laufey's Frost Giants march through it. It's a fight against the cold as much as the giants: keep the Rune Braziers lit, or freeze.",
  'projecthero.guide.frost_giants.ladder': 'Where It Sits',
  'projecthero.guide.frost_giants.ladder.body': 'A Hero-tier siege raid: after the Ultron Uprising, alongside the Apokolips Invasion. Few enemies, each one huge, slow and dangerous -- the opposite of Ultron\'s swarms. Thor players get the most out of it, but anyone can run it.',
  'projecthero.guide.frost_giants.trigger': 'Starting It',
  'projecthero.guide.frost_giants.trigger.body': 'Craft a Jotunheim Rune Stone: blue ice in the top corners, packed ice at the top centre and both sides, a Heart of the Sea (or a Rime Shard) in the middle, chiseled stone bricks in the bottom corners and a lodestone at the bottom centre. Place it and right-click it at night, in Survival (never on Peaceful). It becomes the Rift Anchor: it glows blue-white and can\'t be mined until the fight ends. Servers can turn on a natural trigger: a worthy Thor in a snowy thunderstorm at night may hear Heimdall\'s warning and find a Rune Stone in their pack.',
  'projecthero.guide.frost_giants.arena': 'The Frost Rift',
  'projecthero.guide.frost_giants.arena.body': 'Everything happens within 56 blocks of the anchor, its edge a ring of pale blue snow. Everyone inside is a fighter and can\'t leave; anyone who walks in joins. A fighter who dies is down until they walk back in; if every fighter is down at once, the anchor shatters and you lose. The rift swirls 40 blocks overhead, the sky pales, fog closes in and snow builds up on the ground -- it all melts away when the fight ends.',
  'projecthero.guide.frost_giants.cold': 'The Cold',
  'projecthero.guide.frost_giants.cold.body': 'Away from a lit brazier you slowly freeze: slowness, the frosted screen, then 1 damage a second at full frost. Fire Resistance, being on fire, lava, Pyrokinesis, the Casket of Ancient Winters in your off hand or a full Rimed netherite set keep the cold off. With every brazier out -- or once Laufey opens the Casket -- the cold bites twice as fast.',
  'projecthero.guide.frost_giants.braziers': 'Rune Braziers',
  'projecthero.guide.frost_giants.braziers.body': 'Four braziers burn 20 blocks out from the anchor. Within 7 blocks of one you thaw fast and regenerate. The giants go for them whenever no fighter is within 8 blocks (Ice Brutes love them). A brazier at 0 health goes out; hold right-click on it for 3 s to relight it (flint and steel, a fire charge or Pyrokinesis: instantly). It comes back at half health.',
  'projecthero.guide.frost_giants.waves': 'The Waves',
  'projecthero.guide.frost_giants.waves.body': 'Eight waves come down a beam of frost light from the rift, half again bigger per extra fighter, never more than 16 giants at once: Rime Hounds, then Jotun Raiders, Frost Shamans, an Ice Brute, the Frost Giant Warlord in wave 5, two Ice Brutes from wave 6, and TWO Warlords in the final wave 8. 10 s between waves; horns sound from the rift.',
  'projecthero.guide.frost_giants.laufey': 'King Laufey',
  'projecthero.guide.frost_giants.laufey.body': 'A 14-block king with an ice crown and the Casket of Ancient Winters (2,500 health, +600 per extra fighter). Phase 1: three spears in a fan, two Raiders as a Royal Guard, and Frozen Grasp -- a ring under the fighter hurting him most, then an ice prison (allies break it fast; the prisoner mashes attack). Phase 2 (below two-thirds): he opens the Casket -- the blizzard doubles and every brazier loses 30%. Winter\'s Breath sprays a 3 s cone that freezes water and puts out braziers (hide behind ice), six Ice Pillars burst up and block sight for 20 s, and Avalanche Slam shakes 10 blocks round him.',
  'projecthero.guide.frost_giants.ymir': "Ymir's Wrath",
  'projecthero.guide.frost_giants.ymir.body': "Below a third, Ymir -- the first Frost Giant -- reaches an arm through the rift. He can't be hurt. Palm Slam: his hand's shadow grows on the ground for 2 s, then 30 damage in 7 blocks -- move. Glacial Sweep: a dark line across the arena for 3 s, then his hand drags along it (20 damage and a launch). Laufey's moves come faster and the Casket floats above him.",
  'projecthero.guide.frost_giants.casket': 'Breaking the Casket',
  'projecthero.guide.frost_giants.casket.body': 'You win by breaking the floating Casket, and it can only be hurt while Laufey staggers (5 s): hit him with lightning (Thor, Electrokinesis), break a Frozen Grasp prison within 1.5 s, or take 15% of his health inside 6 s. Broken, Ymir is torn back through the rift, the blizzard ends and Laufey takes double damage. Until then he refuses to fall -- at 1 health he kneels and the Casket stays open to attack.',
  'projecthero.guide.frost_giants.weakness': 'Weaknesses',
  'projecthero.guide.frost_giants.weakness.body': 'Every giant takes 50% more from fire, lava, burning attackers and Pyrokinesis, and 25% more from lightning (Thor is Jotunheim\'s old enemy). They shrug off freezing, slowness and falls, and never hurt each other.',
  'projecthero.guide.frost_giants.rewards': 'Rewards',
  'projecthero.guide.frost_giants.rewards.body': 'The anchor becomes a chest: Rime Shards (4 + 2 per fighter), the Jotun Skull trophy, diamonds, blue and packed ice, emeralds, bottles o\' enchanting and a level-30 book. Each fighter\'s first ever clear also hands them the Casket of Ancient Winters.',
  'projecthero.guide.frost_giants.gear': 'Rime Shards and the Casket',
  'projecthero.guide.frost_giants.gear.body': "At a smithing table (no template), a netherite armour piece + a Rime Shard becomes Rimed: +1 toughness and a frost trim; the full set makes you immune to freezing and lets you walk on powder snow. The Casket in your off hand does the same; right-click for Winter's Breath (a 2 s frost cone, 20 s cooldown), sneak + right-click for an Ice Prison on a mob (5 s, bosses resist, 40 s cooldown).",
  'projecthero.guide.frost_giants.thor': 'For Thor',
  'projecthero.guide.frost_giants.thor.body': 'Lightning staggers Laufey and hits every giant 25% harder. A Rime Shard on Mjolnir or Stormbreaker at a smithing table makes it Rime-touched: its thrown hits freeze. And a worthy Thor\'s lightning near a Casket holder becomes frost-lightning (Jotunheim Storm).',
  'projecthero.guide.frost_giants.commands': 'Commands',
  'projecthero.guide.frost_giants.commands.body': 'Operators: /projecthero raid start frost_giants (where you stand, any time of day), /projecthero raid end frost_giants, /projecthero raid advancetimer frost_giants (next stage: wave, Laufey\'s next phase, the Casket, the fall). Tuning: config/projecthero_frost_giants.json.',
  'projecthero.guide.frost_giants.bestiary': 'Bestiary',
  'projecthero.guide.frost_giants.bestiary.body': 'Defeat one of each to learn more about it (any fighter\'s kill teaches the whole team).',
  'projecthero.guide.frost_giants.locked': '??? -- defeat one to fill in this entry.',
  'projecthero.guide.frost_giants.unit.rime_hound': 'A frost wolf, 1.6 blocks tall, in packs of 3-5 (40 health). Fast: it circles, then darts in to bite (5 damage + frost) or pounces. The first to spot you howls and the whole pack rushes in.',
  'projecthero.guide.frost_giants.unit.jotun_raider': '4.5 blocks tall with an ice spear (140 health, 6 armour). They arrive in pairs and war-cry before charging. Spear thrust up close (10, reach 4); from 8-24 blocks a one-second wind-up, then an ice javelin (9 + frost). With nobody near, they go for the braziers.',
  'projecthero.guide.frost_giants.unit.frost_shaman': 'A robed caster, 5 blocks tall (160 health) -- kill her first. She keeps 14+ blocks away, raises 5 x 3 Ice Walls in front of you (8 s, breakable), encases wounded giants in a Rime Ward (4 s, then heals 25% -- break the ice!) and blasts a Frost Nova if you get within 4 blocks.',
  'projecthero.guide.frost_giants.unit.ice_brute': 'A 7-block brute with ice gauntlets (450 health, 12 armour) that smashes braziers first. It paws the ground for 1.2 s, then charges (16 + launch); or slams its fists and a line of ice spikes erupts toward you after a second of cracking frost (12 each). Fire hurts it badly.',
  'projecthero.guide.frost_giants.unit.frost_giant_warlord': 'Wave 5\'s mini-boss, 11 blocks tall with a horned helm and a greatsword (1,200 health, +300 per extra fighter). Cleave: a wide telegraphed arc (18). Sword Plant: a ring of ice spikes runs outward -- jump it. Rally: every giant within 24 blocks hits 20% harder for 8 s. At 30% he drops to one knee for 3 s and takes half again as much damage.',
  'projecthero.guide.frost_giants.unit.king_laufey': 'The King of Jotunheim. Three phases: the King\'s Court, the Casket Opens and Ymir\'s Wrath. Stagger him (lightning, a quick prison break, or a burst of damage) to expose the floating Casket, break it, then finish him. Dying, he kneels, his crown cracks and he turns to ice before he shatters.',
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
console.log('frost giants v0.16.1 lang: added ' + added + ', updated ' + changed);
