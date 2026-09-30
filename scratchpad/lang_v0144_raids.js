// v0.14.4 "raids are repeatable" lang: set (or add) en_us values by key, keeping the file's layout.
// Re-runnable: existing keys are overwritten in place, new keys are inserted after an anchor key.
const fs = require('fs');
const file = 'src/main/resources/assets/projecthero/lang/en_us.json';
let s = fs.readFileSync(file, 'utf8');

const set = {
  // new
  'event.projecthero.zombie_raid.deferred': 'The dead are already risen close by -- your curse holds on. They come for you in %s seconds.',
  // guide text that implied one-time raids
  'projecthero.guide.supervillain_raid.body': 'A rare assault on a village by one of three Supervillains and their raider army. It is not a vanilla raid and it does not happen often -- but it is never a one-off: beating one does nothing to stop the next.',
  'projecthero.guide.supervillain_raid.spy.body': 'Every so often a lone Pillager appears in the wild, sometimes with an escort, and heads for the nearest village. Look closely: the robe is darker, a faint purple particle drifts from it now and then, and its name shows on your crosshair. It is scouting the village for a Supervillain -- it will not waste its bolts on you out in the fields (unless you attack it), it waits in the village for someone to walk in.',
  'projecthero.guide.supervillain_raid.mark.body': 'If the Spy manages to land a hit on a player who is standing inside a village (anywhere within about 64 blocks of its bell), that village is Marked for Attack. A miss does nothing; an ordinary Pillager does nothing; a hit outside the village does nothing. There is no limit on how often it can happen: after you beat a Supervillain Raid, the next Spy can mark the same village again -- even one that lost its villagers -- or any other.',
  'projecthero.guide.zombie_raid.sources.body': 'Activate the Cursed Grave in a Graveyard\'s crypt, or take a hit from a rare Cursed Zombie. Both lead to exactly the same curse: a second source never resets, extends or stacks your timer. Beating a Zombie Raid never makes you immune -- any time you are not already cursed, a Cursed Zombie can curse you again, and a used Cursed Grave rekindles after a minute or so.',
  'projecthero.guide.zombie_raid.repeat.body': 'Raids are repeatable: get cursed again the ordinary way and the dead come again. Or take matters into your own hands -- craft the Heart of the Grave into a Grave Ritual Totem with Grave Essence, Soul Sand, Rotten Flesh and a Skeleton Skull, and use it in a Graveyard to curse yourself deliberately.',
};

const anchors = {
  'event.projecthero.zombie_raid.deferred': 'event.projecthero.zombie_raid.too_close',
};

const nl = s.includes('\r\n') ? '\r\n' : '\n';
const esc = k => k.replace(/\./g, '\\.');
for (const [k, v] of Object.entries(set)) {
  // [ \t]* rather than \s*: with CRLF line endings '^' can match between \r and \n, and \s* would
  // then swallow the \n and the inserted line would gain a blank line above it.
  const re = new RegExp('^([ \\t]*)"' + esc(k) + '": ".*?",?$', 'm');
  const m = s.match(re);
  if (m) {
    const comma = m[0].trimEnd().endsWith(',') ? ',' : '';
    s = s.replace(re, () => m[1] + JSON.stringify(k) + ': ' + JSON.stringify(v) + comma);
    continue;
  }
  const anchorKey = anchors[k];
  if (!anchorKey) throw new Error('missing key with no anchor: ' + k);
  const anchor = new RegExp('^([ \\t]*)"' + esc(anchorKey) + '": ".*?",$', 'm');
  const a = s.match(anchor);
  if (!a) throw new Error('no anchor for ' + k);
  s = s.replace(anchor, () => a[0] + nl + a[1] + JSON.stringify(k) + ': ' + JSON.stringify(v) + ',');
}
const parsed = JSON.parse(s);
for (const [k, v] of Object.entries(set)) {
  if (parsed[k] !== v) throw new Error('verify failed for ' + k);
}
fs.writeFileSync(file, s);
console.log('ok', Object.keys(set).length);
