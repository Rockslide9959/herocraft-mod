// Merges the Zombie Raid's translation keys into the existing en_us.json, preserving everything
// already there and its ordering.
const fs = require('fs');

const FILE = 'src/main/resources/assets/projecthero/lang/en_us.json';
const lang = JSON.parse(fs.readFileSync(FILE, 'utf8'));

const add = {
  // ---------------- blocks & items ----------------
  'block.projecthero.cursed_grave': 'Cursed Grave',
  'item.projecthero.grave_essence': 'Grave Essence',
  'item.projecthero.corrupted_power_core': 'Corrupted Power Core',
  'item.projecthero.corrupted_power_core.named': 'Corrupted %s Core',
  'item.projecthero.corrupted_power_core.power': 'Bound power: %s',
  'item.projecthero.corrupted_power_core.hint': 'A research and crafting reagent — it cannot be consumed for its power.',
  'item.projecthero.gravewalker_charm': 'Gravewalker Charm',
  'item.projecthero.undying_totem': 'Undying Totem',
  'item.projecthero.undying_totem.charges': 'Charges: %s / %s',
  'item.projecthero.undying_totem.hint': 'Held in a hand, it spends one charge to prevent a death.',
  'item.projecthero.necrotic_blade': 'Necrotic Blade',
  'item.projecthero.necrotic_blade.wither': '%s%% chance to inflict Wither',
  'item.projecthero.necrotic_blade.stacks': 'Killing undead sharpens it, up to %s stacks',
  'item.projecthero.gravekeeper_shield': 'Gravekeeper Shield',
  'item.projecthero.gravekeeper_shield.undead': '%s%% less damage from undead',
  'item.projecthero.gravekeeper_shield.acid': '%s%% less damage from acid',
  'item.projecthero.gravekeeper_shield.charge': 'Holds firm against a Juggernaut charge',
  'item.projecthero.heart_of_the_grave': 'Heart of the Grave',
  'item.projecthero.grave_ritual_totem': 'Grave Ritual Totem',
  'item.projecthero.grave_ritual_totem.hint': 'Use it in a Graveyard to call the Gravebound Curse on yourself.',
  'item.projecthero.boss_trophy': 'Empowered Zombie Head',
  'item.projecthero.boss_trophy.named': '%s Zombie Head',
  'item.projecthero.boss_trophy.hint': 'A trophy taken from a Powered Zombie Boss.',
  'item.projecthero.final_boss_trophy': 'Grave Champion Head',
  'item.projecthero.final_boss_trophy.named': 'Grave Champion Head — %s',

  // ---------------- entities ----------------
  'entity.projecthero.cursed_zombie': 'Cursed Zombie',
  'entity.projecthero.raid_zombie': 'Risen Zombie',
  'entity.projecthero.acid_zombie': 'Acid Zombie',
  'entity.projecthero.sword_skeleton': 'Sword Skeleton',
  'entity.projecthero.juggernaut_zombie': 'Juggernaut Zombie',
  'entity.projecthero.empowered_zombie': 'Empowered Zombie',
  'entity.projecthero.acid_glob': 'Acid Glob',

  // ---------------- the curse ----------------
  'message.projecthero.curse.applied.title': 'YOU HAVE BEEN CURSED',
  'message.projecthero.curse.applied.sub': 'The Gravebound Curse has taken hold...',
  'message.projecthero.curse.applied.hint': 'Find an Enchanted Golden Apple before the dead awaken.',
  'message.projecthero.curse.broken': 'The Gravebound Curse has been broken.',
  'message.projecthero.curse.already': 'The curse already has you. Nothing changes.',
  'message.projecthero.curse.ambient.following': 'Something is following you...',
  'message.projecthero.curse.ambient.restless': 'The dead grow restless.',
  'message.projecthero.curse.ambient.shifting': 'You feel the earth shifting beneath you.',
  'curse.projecthero.source.graveyard': 'Graveyard',
  'curse.projecthero.source.cursed_zombie': 'Cursed Zombie',
  'curse.projecthero.source.ritual': 'Grave Ritual',
  'curse.projecthero.source.command': 'Command',

  // ---------------- the raid ----------------
  'event.projecthero.zombie_raid': 'ZOMBIE RAID',
  'event.projecthero.zombie_raid.rise': 'THE DEAD HAVE RISEN',
  'event.projecthero.zombie_raid.wave': 'Wave %s of %s',
  'event.projecthero.zombie_raid.wave_cleared': 'Wave %s cleared.',
  'event.projecthero.zombie_raid.boss': 'An Empowered Zombie rises — %s',
  'event.projecthero.zombie_raid.cleared': 'THE RAID IS BROKEN',
  'event.projecthero.zombie_raid.cleared.sub': 'The dead return to their graves.',
  'event.projecthero.zombie_raid.progress': 'Wave %s / %s',
  'event.projecthero.zombie_raid.joined': 'You have joined the Zombie Raid already underway here.',
  'event.projecthero.zombie_raid.too_close': 'The dead stir, but another raid already rages nearby.',
  'event.projecthero.abandoned': '%s was abandoned.',
  'event.projecthero.paused': '%s pauses while no one stands against it.',
  'event.projecthero.resumed': '%s resumes.',
  'event.projecthero.leaving': 'You are leaving the %s area!',

  // ---------------- rewards / research ----------------
  'message.projecthero.raid.first_clear': 'A Heart of the Grave is yours. Grave Ritual Totems can now be forged.',
  'message.projecthero.raid.chest': 'A Cursed Grave Chest has risen at %s, %s, %s.',
  'message.projecthero.research.progress': 'POWER ANALYSIS — %s: %s%%',
  'message.projecthero.research.complete': 'Power analysis complete: %s. See your HeroPack Guide.',
  'message.projecthero.charm.triggered': 'The Gravewalker Charm flares.',
  'message.projecthero.undying_totem.used': 'The Undying Totem holds. %s charges remain.',
  'message.projecthero.ritual.not_near_graveyard': 'The ritual needs the ground of a Graveyard.',
  'message.projecthero.ritual.already_cursed': 'You are already Gravebound.',
  'message.projecthero.ritual.raid_nearby': 'A Zombie Raid already rages nearby.',

  // ---------------- HUD ----------------
  'hud.projecthero.curse': 'GRAVEBOUND',
  'hud.projecthero.raid': 'ZOMBIE RAID',
  'hud.projecthero.raid.wave': 'Wave %s / %s',
  'hud.projecthero.raid.enemies': 'Enemies Remaining: %s',
  'hud.projecthero.raid.next_wave': 'Next wave in %ss',
  'hud.projecthero.raid.incoming': 'The dead are rising...',

  // ---------------- advancements ----------------
  'advancement.projecthero.gravebound.cursed.title': 'Gravebound',
  'advancement.projecthero.gravebound.cursed.description': 'Take the Gravebound Curse from a Graveyard or a Cursed Zombie',
  'advancement.projecthero.gravebound.curse_broken.title': 'Not Today',
  'advancement.projecthero.gravebound.curse_broken.description': 'Break the Gravebound Curse with an Enchanted Golden Apple',
  'advancement.projecthero.gravebound.zombie_slayer.title': 'Zombie Slayer',
  'advancement.projecthero.gravebound.zombie_slayer.description': 'Complete a Zombie Raid',
  'advancement.projecthero.gravebound.deathless.title': 'Deathless',
  'advancement.projecthero.gravebound.deathless.description': 'Complete a Zombie Raid without dying',
  'advancement.projecthero.gravebound.one_man_army.title': 'One-Man Army',
  'advancement.projecthero.gravebound.one_man_army.description': 'Complete a Zombie Raid alone',
  'advancement.projecthero.gravebound.last_stand.title': 'Last Stand',
  'advancement.projecthero.gravebound.last_stand.description': 'Complete a Zombie Raid with at least four participants',
  'advancement.projecthero.gravebound.power_breaker.title': 'Power Breaker',
  'advancement.projecthero.gravebound.power_breaker.description': 'Defeat all three Powered Zombie Bosses in one raid',
  'advancement.projecthero.gravebound.gravewalker.title': 'Gravewalker',
  'advancement.projecthero.gravebound.gravewalker.description': 'Complete ten Zombie Raids',
  'advancement.projecthero.gravebound.power_analysis.title': 'Power Analysis',
  'advancement.projecthero.gravebound.power_analysis.description': 'Fully analyse an Experimental Power from Empowered Zombie kills',

  // ---------------- guide ----------------
  'projecthero.guide.section.events': 'World Events',
  'projecthero.guide.zombie_raid': 'The Zombie Raid',
  'projecthero.guide.zombie_raid.body':
    'Somewhere out there is a Graveyard, and somewhere in the dark is a Cursed Zombie. Either one can '
    + 'make you Gravebound — and twenty minutes later, the dead come for you.',
  'projecthero.guide.zombie_raid.curse': 'The Gravebound Curse',
  'projecthero.guide.zombie_raid.curse.body':
    'Twenty minutes of play. It survives logging out, dying, changing dimension, restarting the game, '
    + 'and milk. Only an Enchanted Golden Apple breaks it — everything else just delays the reckoning. '
    + 'The timer shows in the top-left while it runs.',
  'projecthero.guide.zombie_raid.sources': 'Catching it',
  'projecthero.guide.zombie_raid.sources.body':
    'Activate the Cursed Grave in a Graveyard\'s crypt, or take a hit from a rare Cursed Zombie. Both '
    + 'lead to exactly the same curse: a second source never resets, extends or stacks your timer.',
  'projecthero.guide.zombie_raid.waves': 'Twelve waves',
  'projecthero.guide.zombie_raid.waves.body':
    'Risen and baby zombies, Armoured Zombies that shrug off arrows, Acid Zombies that deny ground, '
    + 'Sword Skeletons that hunt archers, and Juggernauts that charge. Powered Zombie Bosses arrive on '
    + 'waves 4, 8 and 12, each carrying one of the Experimental Powers.',
  'projecthero.guide.zombie_raid.bosses': 'Powered Zombie Bosses',
  'projecthero.guide.zombie_raid.bosses.body':
    'An Empowered Zombie fights with a real power, not a bigger health bar. It picks its targets, '
    + 'follows fliers, closes on archers and saves its area attacks for crowds. Kill one and you take a '
    + 'Corrupted Power Core that remembers which power it had, plus research progress toward it.',
  'projecthero.guide.zombie_raid.rewards': 'Rewards',
  'projecthero.guide.zombie_raid.rewards.body':
    'Grave Essence from everything you kill; a Cursed Grave Chest after wave 12, holding the final '
    + 'boss\'s core and — if you are lucky — a Gravewalker Charm, a Gravekeeper Shield, a Necrotic '
    + 'Blade or an Undying Totem. Your first clear also yields a Heart of the Grave.',
  'projecthero.guide.zombie_raid.repeat': 'Doing it again',
  'projecthero.guide.zombie_raid.repeat.body':
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
