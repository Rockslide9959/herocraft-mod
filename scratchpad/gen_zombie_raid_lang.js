// Merges the Zombie Raid's translation keys into the existing en_us.json, preserving everything
// already there and its ordering.
const fs = require('fs');

const FILE = 'src/main/resources/assets/herocraft/lang/en_us.json';
const lang = JSON.parse(fs.readFileSync(FILE, 'utf8'));

const add = {
  // ---------------- blocks & items ----------------
  'block.herocraft.cursed_grave': 'Cursed Grave',
  'item.herocraft.grave_essence': 'Grave Essence',
  'item.herocraft.corrupted_power_core': 'Corrupted Power Core',
  'item.herocraft.corrupted_power_core.named': 'Corrupted %s Core',
  'item.herocraft.corrupted_power_core.power': 'Bound power: %s',
  'item.herocraft.corrupted_power_core.hint': 'A research and crafting reagent — it cannot be consumed for its power.',
  'item.herocraft.gravewalker_charm': 'Gravewalker Charm',
  'item.herocraft.undying_totem': 'Undying Totem',
  'item.herocraft.undying_totem.charges': 'Charges: %s / %s',
  'item.herocraft.undying_totem.hint': 'Held in a hand, it spends one charge to prevent a death.',
  'item.herocraft.necrotic_blade': 'Necrotic Blade',
  'item.herocraft.necrotic_blade.wither': '%s%% chance to inflict Wither',
  'item.herocraft.necrotic_blade.stacks': 'Killing undead sharpens it, up to %s stacks',
  'item.herocraft.gravekeeper_shield': 'Gravekeeper Shield',
  'item.herocraft.gravekeeper_shield.undead': '%s%% less damage from undead',
  'item.herocraft.gravekeeper_shield.acid': '%s%% less damage from acid',
  'item.herocraft.gravekeeper_shield.charge': 'Holds firm against a Juggernaut charge',
  'item.herocraft.heart_of_the_grave': 'Heart of the Grave',
  'item.herocraft.grave_ritual_totem': 'Grave Ritual Totem',
  'item.herocraft.grave_ritual_totem.hint': 'Use it in a Graveyard to call the Gravebound Curse on yourself.',
  'item.herocraft.boss_trophy': 'Empowered Zombie Head',
  'item.herocraft.boss_trophy.named': '%s Zombie Head',
  'item.herocraft.boss_trophy.hint': 'A trophy taken from a Powered Zombie Boss.',
  'item.herocraft.final_boss_trophy': 'Grave Champion Head',
  'item.herocraft.final_boss_trophy.named': 'Grave Champion Head — %s',

  // ---------------- entities ----------------
  'entity.herocraft.cursed_zombie': 'Cursed Zombie',
  'entity.herocraft.raid_zombie': 'Risen Zombie',
  'entity.herocraft.acid_zombie': 'Acid Zombie',
  'entity.herocraft.sword_skeleton': 'Sword Skeleton',
  'entity.herocraft.juggernaut_zombie': 'Juggernaut Zombie',
  'entity.herocraft.empowered_zombie': 'Empowered Zombie',
  'entity.herocraft.acid_glob': 'Acid Glob',

  // ---------------- the curse ----------------
  'message.herocraft.curse.applied.title': 'YOU HAVE BEEN CURSED',
  'message.herocraft.curse.applied.sub': 'The Gravebound Curse has taken hold...',
  'message.herocraft.curse.applied.hint': 'Find an Enchanted Golden Apple before the dead awaken.',
  'message.herocraft.curse.broken': 'The Gravebound Curse has been broken.',
  'message.herocraft.curse.already': 'The curse already has you. Nothing changes.',
  'message.herocraft.curse.ambient.following': 'Something is following you...',
  'message.herocraft.curse.ambient.restless': 'The dead grow restless.',
  'message.herocraft.curse.ambient.shifting': 'You feel the earth shifting beneath you.',
  'curse.herocraft.source.graveyard': 'Graveyard',
  'curse.herocraft.source.cursed_zombie': 'Cursed Zombie',
  'curse.herocraft.source.ritual': 'Grave Ritual',
  'curse.herocraft.source.command': 'Command',

  // ---------------- the raid ----------------
  'event.herocraft.zombie_raid': 'ZOMBIE RAID',
  'event.herocraft.zombie_raid.rise': 'THE DEAD HAVE RISEN',
  'event.herocraft.zombie_raid.wave': 'Wave %s of %s',
  'event.herocraft.zombie_raid.wave_cleared': 'Wave %s cleared.',
  'event.herocraft.zombie_raid.boss': 'An Empowered Zombie rises — %s',
  'event.herocraft.zombie_raid.cleared': 'THE RAID IS BROKEN',
  'event.herocraft.zombie_raid.cleared.sub': 'The dead return to their graves.',
  'event.herocraft.zombie_raid.progress': 'Wave %s / %s',
  'event.herocraft.zombie_raid.joined': 'You have joined the Zombie Raid already underway here.',
  'event.herocraft.zombie_raid.too_close': 'The dead stir, but another raid already rages nearby.',
  'event.herocraft.abandoned': '%s was abandoned.',
  'event.herocraft.paused': '%s pauses while no one stands against it.',
  'event.herocraft.resumed': '%s resumes.',
  'event.herocraft.leaving': 'You are leaving the %s area!',

  // ---------------- rewards / research ----------------
  'message.herocraft.raid.first_clear': 'A Heart of the Grave is yours. Grave Ritual Totems can now be forged.',
  'message.herocraft.raid.chest': 'A Cursed Grave Chest has risen at %s, %s, %s.',
  'message.herocraft.research.progress': 'POWER ANALYSIS — %s: %s%%',
  'message.herocraft.research.complete': 'Power analysis complete: %s. See your HeroPack Guide.',
  'message.herocraft.charm.triggered': 'The Gravewalker Charm flares.',
  'message.herocraft.undying_totem.used': 'The Undying Totem holds. %s charges remain.',
  'message.herocraft.ritual.not_near_graveyard': 'The ritual needs the ground of a Graveyard.',
  'message.herocraft.ritual.already_cursed': 'You are already Gravebound.',
  'message.herocraft.ritual.raid_nearby': 'A Zombie Raid already rages nearby.',

  // ---------------- HUD ----------------
  'hud.herocraft.curse': 'GRAVEBOUND',
  'hud.herocraft.raid': 'ZOMBIE RAID',
  'hud.herocraft.raid.wave': 'Wave %s / %s',
  'hud.herocraft.raid.enemies': 'Enemies Remaining: %s',
  'hud.herocraft.raid.next_wave': 'Next wave in %ss',
  'hud.herocraft.raid.incoming': 'The dead are rising...',

  // ---------------- advancements ----------------
  'advancement.herocraft.gravebound.cursed.title': 'Gravebound',
  'advancement.herocraft.gravebound.cursed.description': 'Take the Gravebound Curse from a Graveyard or a Cursed Zombie',
  'advancement.herocraft.gravebound.curse_broken.title': 'Not Today',
  'advancement.herocraft.gravebound.curse_broken.description': 'Break the Gravebound Curse with an Enchanted Golden Apple',
  'advancement.herocraft.gravebound.zombie_slayer.title': 'Zombie Slayer',
  'advancement.herocraft.gravebound.zombie_slayer.description': 'Complete a Zombie Raid',
  'advancement.herocraft.gravebound.deathless.title': 'Deathless',
  'advancement.herocraft.gravebound.deathless.description': 'Complete a Zombie Raid without dying',
  'advancement.herocraft.gravebound.one_man_army.title': 'One-Man Army',
  'advancement.herocraft.gravebound.one_man_army.description': 'Complete a Zombie Raid alone',
  'advancement.herocraft.gravebound.last_stand.title': 'Last Stand',
  'advancement.herocraft.gravebound.last_stand.description': 'Complete a Zombie Raid with at least four participants',
  'advancement.herocraft.gravebound.power_breaker.title': 'Power Breaker',
  'advancement.herocraft.gravebound.power_breaker.description': 'Defeat all three Powered Zombie Bosses in one raid',
  'advancement.herocraft.gravebound.gravewalker.title': 'Gravewalker',
  'advancement.herocraft.gravebound.gravewalker.description': 'Complete ten Zombie Raids',
  'advancement.herocraft.gravebound.power_analysis.title': 'Power Analysis',
  'advancement.herocraft.gravebound.power_analysis.description': 'Fully analyse an Experimental Power from Empowered Zombie kills',

  // ---------------- guide ----------------
  'herocraft.guide.section.events': 'World Events',
  'herocraft.guide.zombie_raid': 'The Zombie Raid',
  'herocraft.guide.zombie_raid.body':
    'Somewhere out there is a Graveyard, and somewhere in the dark is a Cursed Zombie. Either one can '
    + 'make you Gravebound — and twenty minutes later, the dead come for you.',
  'herocraft.guide.zombie_raid.curse': 'The Gravebound Curse',
  'herocraft.guide.zombie_raid.curse.body':
    'Twenty minutes of play. It survives logging out, dying, changing dimension, restarting the game, '
    + 'and milk. Only an Enchanted Golden Apple breaks it — everything else just delays the reckoning. '
    + 'The timer shows in the top-left while it runs.',
  'herocraft.guide.zombie_raid.sources': 'Catching it',
  'herocraft.guide.zombie_raid.sources.body':
    'Activate the Cursed Grave in a Graveyard\'s crypt, or take a hit from a rare Cursed Zombie. Both '
    + 'lead to exactly the same curse: a second source never resets, extends or stacks your timer.',
  'herocraft.guide.zombie_raid.waves': 'Twelve waves',
  'herocraft.guide.zombie_raid.waves.body':
    'Risen and baby zombies, Armoured Zombies that shrug off arrows, Acid Zombies that deny ground, '
    + 'Sword Skeletons that hunt archers, and Juggernauts that charge. Powered Zombie Bosses arrive on '
    + 'waves 4, 8 and 12, each carrying one of the Experimental Powers.',
  'herocraft.guide.zombie_raid.bosses': 'Powered Zombie Bosses',
  'herocraft.guide.zombie_raid.bosses.body':
    'An Empowered Zombie fights with a real power, not a bigger health bar. It picks its targets, '
    + 'follows fliers, closes on archers and saves its area attacks for crowds. Kill one and you take a '
    + 'Corrupted Power Core that remembers which power it had, plus research progress toward it.',
  'herocraft.guide.zombie_raid.rewards': 'Rewards',
  'herocraft.guide.zombie_raid.rewards.body':
    'Grave Essence from everything you kill; a Cursed Grave Chest after wave 12, holding the final '
    + 'boss\'s core and — if you are lucky — a Gravewalker Charm, a Gravekeeper Shield, a Necrotic '
    + 'Blade or an Undying Totem. Your first clear also yields a Heart of the Grave.',
  'herocraft.guide.zombie_raid.repeat': 'Doing it again',
  'herocraft.guide.zombie_raid.repeat.body':
    'Craft the Heart of the Grave into a Grave Ritual Totem with Grave Essence, Soul Sand, Rotten Flesh '
    + 'and a Skeleton Skull. Use it in a Graveyard to curse yourself deliberately.',
};

let added = 0;
for (const [key, value] of Object.entries(add)) {
  if (!(key in lang)) added++;
  lang[key] = value;
}
fs.writeFileSync(FILE, JSON.stringify(lang, null, 2) + '\n');
console.log(`merged ${Object.keys(add).length} keys (${added} new) into ${FILE}`);
