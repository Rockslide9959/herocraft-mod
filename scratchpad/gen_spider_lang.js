// HeroPack 0.6.3 -- add every Spider-Man translation key to en_us.json, and rewrite the Spider
// Adhesion entries that the climbing overhaul made wrong.
//
// Done as a script rather than by hand because en_us.json is ~1200 sorted-by-section lines and a
// hand edit in the middle of it is easy to get subtly wrong. Existing keys are overwritten in place
// (so re-running is safe); new ones are appended in a labelled block.
const fs = require('fs');

const FILE = 'src/main/resources/assets/projecthero/lang/en_us.json';
const lang = JSON.parse(fs.readFileSync(FILE, 'utf8'));

// ---------------------------------------------------------------- Spider Adhesion, revised

Object.assign(lang, {
  'projecthero.power.power_16_spider_climbing_adhesion.name': 'Spider Climbing / Adhesion',
  'projecthero.power.power_16_spider_climbing_adhesion.desc':
    'Cling to any surface and move across it. Walls, ceilings and the corners between them -- climb up, '
    + 'crawl sideways, hang still, and leap away when you want to. Slower and less sure-footed than '
    + 'the arachnid who evolves out of it.',
  'projecthero.power.power_16_spider_climbing_adhesion.ability.wall_grip': 'Wall Grip',
  'projecthero.power.power_16_spider_climbing_adhesion.ability.wall_grip.desc':
    'Hold on. While active you stick to whatever surface you touch and move relative to it: look where '
    + 'you want to go and walk. Stop and you stay put; sneak and you hold tighter.',
  'projecthero.power.power_16_spider_climbing_adhesion.ability.adhesion_mode': 'Adhesion Mode',
  'projecthero.power.power_16_spider_climbing_adhesion.ability.adhesion_mode.desc':
    'The same grip, left on permanently -- walls, overhangs and ceilings all become floor. Jump to push '
    + 'off whatever you are holding.',
  'projecthero.power.power_16_spider_climbing_adhesion.ability.wall_leap.desc':
    'Powerfully launch away from the surface you are gripping.',
  'projecthero.power.power_16_spider_climbing_adhesion.passive.evolve':
    'Can be evolved into the Spider-Man Hero Class with an Arachnid Mutagen',
});

// ---------------------------------------------------------------- Spider-Man

const spider = {
  // --- item ---
  'item.projecthero.arachnid_mutagen': 'Arachnid Mutagen',
  'item.projecthero.arachnid_mutagen.desc1': 'Refines an existing arachnid adaptation into something far greater.',
  'item.projecthero.arachnid_mutagen.desc2': 'Useless to anyone who has not already been changed by a spider.',

  // --- the power ---
  'projecthero.spider_man.name': 'Spider-Man',
  'projecthero.spider_man.desc':
    'A Hero Class built entirely around movement. Swing across the sky, crawl over anything, and see '
    + 'trouble coming before it arrives.',

  // --- abilities ---
  'projecthero.spider_man.ability.web_swing': 'Web Swing',
  'projecthero.spider_man.ability.web_swing.desc':
    'Hold to fire a web and swing from it. Finds real anchors -- rooftops, cliffs, canopy, cave ceilings '
    + '-- and fires one high ahead of you when there is nothing to catch. W builds momentum, S brakes, '
    + 'A and D steer, jump reels in, sneak lets out. Let go at the right moment and you keep every bit '
    + 'of the speed.',
  'projecthero.spider_man.ability.web_zip': 'Web Zip',
  'projecthero.spider_man.ability.web_zip.desc':
    'Fire at the surface you are looking at and pull yourself to it, fast. Precision traversal: rooftops, '
    + 'ledges, the far side of a ravine, or a save on the way down.',
  'projecthero.spider_man.ability.web_shot': 'Web Shot',
  'projecthero.spider_man.ability.web_shot.desc':
    'A burst of webbing at what you are aiming at. Slows a target badly and worse with each hit, until an '
    + 'ordinary mob simply stops. Puts out fires, including your own.',
  'projecthero.spider_man.ability.web_yank': 'Web Yank',
  'projecthero.spider_man.ability.web_yank.desc':
    'Line something and haul. Light things come to you, heavy things pull you to them, and dropped items '
    + 'come flying -- from a ravine, a lava edge or anywhere you would rather not walk.',
  'projecthero.spider_man.ability.web_cocoon': 'Web Cocoon',
  'projecthero.spider_man.ability.web_cocoon.desc':
    'Wrap a target completely. Five seconds out of the fight for an ordinary mob; a boss is badly hampered '
    + 'but never switched off.',
  'projecthero.spider_man.ability.web_net': 'Web Net',
  'projecthero.spider_man.ability.web_net.desc':
    'Spin a temporary platform of webbing where you are aiming. A bridge, a floor, a landing pad or a '
    + 'trap. Dissolves on its own and puts back whatever it covered.',
  'projecthero.spider_man.ability.double_jump': 'Double Jump',

  // --- HUD ---
  'hud.projecthero.spider_man.web_reserve': 'WEB %s/%s',
  'hud.projecthero.spider_man.double_jump': 'Double jump %ss',

  // --- messages ---
  'message.projecthero.spider_man.evolved': 'Your arachnid abilities have evolved.',
  'message.projecthero.spider_man.acquired': 'Hero Class Acquired: Spider-Man',
  'message.projecthero.spider_man.no_webbing': 'Out of webbing.',
  'message.projecthero.spider_man.no_surface': 'Nothing to web onto.',
  'message.projecthero.spider_man.no_target': 'No target.',
  'message.projecthero.spider_man.net_failed': 'No room to spin a net there.',
  'message.projecthero.spider_man.cocooned': 'Cocooned %s for %ss.',
  'message.projecthero.arachnid_mutagen.already': 'You are already Spider-Man.',
  'message.projecthero.arachnid_mutagen.no_adhesion':
    'Your body lacks the arachnid adaptation required for this mutation.',

  // --- commands ---
  'commands.projecthero.spiderman.already': 'That player is already Spider-Man.',
  'commands.projecthero.spiderman.no_adhesion':
    'That player has no Spider Climbing / Adhesion to evolve, and none could be granted.',
  'commands.projecthero.spiderman.granted': 'Granted the Spider-Man Hero Class to %s.',
  'commands.projecthero.spiderman.revoked': 'Revoked the Spider-Man Hero Class from %s.',
  'commands.projecthero.spiderman.web_set': 'Web Reserve set to %s.',

  // --- guide chapter ---
  'projecthero.guide.spider_man': 'Spider-Man',
  'projecthero.guide.spider_man.tier': 'Hero Class',
  'projecthero.guide.spider_man.body':
    'You cannot become Spider-Man directly. It is what Spider Climbing / Adhesion grows into: the same '
    + 'adaptation, refined until it is something else. Everything Adhesion could do, this does better, '
    + 'and it replaces it outright -- you are never carrying both.',
  'projecthero.guide.spider_man.progression': 'How to get it',
  'projecthero.guide.spider_man.step.adhesion':
    'Mutate into Spider Climbing / Adhesion the ordinary way (Adhesive Mutation Serum, then survive a '
    + 'Cave Spider bite).',
  'projecthero.guide.spider_man.step.mutagen':
    'Craft an Arachnid Mutagen: phantom membrane, an echo shard, spider eyes, fermented spider eyes, '
    + 'amethyst and a golden apple.',
  'projecthero.guide.spider_man.step.evolve':
    'Use it. With the adaptation already in you it evolves; without it, nothing happens and the mutagen '
    + 'is not consumed.',
  'projecthero.guide.spider_man.controls': 'Abilities',
  'projecthero.guide.spider_man.passives': 'Always on',
  'projecthero.guide.spider_man.passive.sense':
    'Spider Sense -- you feel an attack coming before it lands.',
  'projecthero.guide.spider_man.passive.dodge':
    'Half of all incoming attacks are simply dodged.',
  'projecthero.guide.spider_man.passive.arrows':
    'Arrows you dodge are caught out of the air and kept.',
  'projecthero.guide.spider_man.passive.crawl':
    'Wall crawling -- climb, descend, hang and crawl sideways across any surface.',
  'projecthero.guide.spider_man.passive.ceiling':
    'Ceiling crawling, and clean transitions between wall and ceiling in both directions.',
  'projecthero.guide.spider_man.passive.double_jump':
    'A second jump in mid-air, once every two seconds.',
  'projecthero.guide.spider_man.passive.strength':
    'Enhanced strength, speed and jump; some resistance to being knocked around.',
  'projecthero.guide.spider_man.passive.agility':
    'Improved footing and air control.',
  'projecthero.guide.spider_man.passive.fall':
    'Falls hurt far less, and a fall you web out of does not hurt at all.',
  'projecthero.guide.spider_man.passive.reserve':
    'Organic webbing -- no shooters, no ammunition, just a reserve that refills.',
  'projecthero.guide.spider_man.swinging':
    'Swinging prefers real terrain and always will: a city, a mountainside or a dense forest gives longer, '
    + 'faster, more dramatic arcs than open ground. Where there is genuinely nothing to catch, the web '
    + 'goes up and ahead anyway so traversal never stops -- but those swings will only carry you so far '
    + 'above where the run began, so webbing across a desert stays swinging rather than becoming flying. '
    + 'Climb real terrain and that limit rises with you.',
  'projecthero.guide.spider_man.reserve':
    'Every web ability spends from your Web Reserve, shown under the ability row. Swinging costs very '
    + 'little; combat webbing costs real amounts. It refills by itself a moment after you stop using it.',

  // --- power wheel ---
  'screen.projecthero.power_select.spider_man': 'Spider-Man',
};

Object.assign(lang, spider);

fs.writeFileSync(FILE, JSON.stringify(lang, null, 2) + '\n');
console.log(`en_us.json now has ${Object.keys(lang).length} keys`);
