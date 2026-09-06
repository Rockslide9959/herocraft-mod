const fs = require('fs');
const f = 'src/main/java/com/projecthero/mod/event/entity/CursedZombie.java';
let s = fs.readFileSync(f, 'utf8');

// Drop the no-op override and move its reasoning into the class javadoc, where it belongs.
const start = s.indexOf('\t/**\n\t * Deliberately left on vanilla despawn rules');
const end = s.indexOf('\t@Override\n\tpublic void aiStep()');
if (start < 0 || end < 0) throw new Error('anchors missing');
s = s.slice(0, start) + s.slice(end);

const anchor = ' * <h2>The curse hit</h2>';
const note = [
  ' * <h2>Left on vanilla despawn rules, on purpose</h2>',
  ' * It is tempting to make a rare encounter persistent so it cannot evaporate while the player runs.',
  ' * That would be wrong here: a Cursed Zombie spawns from the ordinary natural-spawn path anywhere in',
  ' * the Overworld, and a persistent one is never cleaned up by anything -- so over a long-lived world,',
  ' * every one that ever spawned and was not killed would still be sitting in its chunk. At a fraction',
  ' * of a percent of all zombie spawns that accumulates slowly and invisibly, which is exactly how a',
  ' * world gets heavier the longer it is played.',
  ' *',
  " * <p>Vanilla's rules are also simply correct for this mob: it will not despawn while anyone is near",
  ' * enough to be threatened by it, and running far enough away that it does despawn <em>is</em>',
  ' * escaping it.',
  ' *',
  anchor,
].join('\n');
if (!s.includes(anchor)) throw new Error('javadoc anchor missing');
s = s.replace(anchor, note);

fs.writeFileSync(f, s);
console.log('CursedZombie updated');
