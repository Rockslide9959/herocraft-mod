const fs = require('fs');
const f = 'README.md';
let s = fs.readFileSync(f, 'utf8');

const oldStatus = '## Status\n\n**v0.4.3 — GeckoLib leg-render fix, Mark I/II suits, Iron Man mechanics rework ("changes 12").**';
const newStatus = [
  '## Status',
  '',
  '**v0.5.0 — the Zombie Raid, and a long-session memory-leak fix.**',
  '',
  '- **New world event: the Zombie Raid.** Catch the twenty-minute **Gravebound Curse** from a naturally',
  '  generated **Graveyard** or a rare **Cursed Zombie**, then either burn an Enchanted Golden Apple to',
  '  escape it or survive twelve waves and three **Powered Zombie Bosses** — each carrying a real',
  '  Experimental Power and fighting with it. Full writeup:',
  '  [docs/ZOMBIE_RAID_REFERENCE.md](docs/ZOMBIE_RAID_REFERENCE.md).',
  '- Built on a small reusable **world-event framework** (`com.herocraft.mod.event`) so future events',
  '  (End invasion, Nether corruption, robot uprising, world bosses) reuse waves, participants,',
  '  boundaries and persistence rather than reimplementing them.',
  '- **Fixed: the game got progressively choppier over a long session.** Every static server-side cache',
  '  in the mod was outliving the server that filled it, pinning the previous world — levels, chunk map',
  '  and all its entities — in memory each time you quit to the title screen and opened another world.',
  '  `ServerStateReset` now drops all of it on server stop, and sweeps two marker maps that were only',
  '  ever pruned while somebody happened to have the owning power selected.',
  '- **Fixed: calling armour from an unloaded Suit Platform stopped working.** The registry entry that',
  "  exists specifically to make that possible was being deleted whenever the platform's chunk unloaded,",
  '  because the block entity dropped it from `setRemoved()` — which vanilla also calls on chunk unload,',
  "  not just on the block being broken. Removal now happens from the block's `onRemove`, which fires",
  '  only when the block genuinely changes.',
  '',
  '<details>',
  '<summary>v0.4.3 — GeckoLib leg-render fix, Mark I/II suits, Iron Man mechanics rework ("changes 12")</summary>',
  '',
].join('\n');

if (!s.includes(oldStatus)) throw new Error('status anchor missing');
s = s.replace(oldStatus, newStatus);

s = s.replace(
  'See [docs/IRONMAN_REFERENCE.md §17f](docs/IRONMAN_REFERENCE.md) for the full writeup.\n\n---',
  'See [docs/IRONMAN_REFERENCE.md §17f](docs/IRONMAN_REFERENCE.md) for the full writeup.\n\n</details>\n\n---');

s = s.replace('- Package root: `com.herocraft.mod`', [
  '- `src/gametest/java` — automated in-server tests (`./gradlew runGameTest`)',
  '- Package root: `com.herocraft.mod`',
  '',
  'Reference docs: [Thor](THOR_DESIGN.md) · [HeroPack powers](docs/HEROPACK_CONTENT_REFERENCE.md) ·',
  '[Iron Man](docs/IRONMAN_REFERENCE.md) · [armour models](docs/ARMOR_MODELS.md) ·',
  '[Zombie Raid](docs/ZOMBIE_RAID_REFERENCE.md)',
].join('\n'));

fs.writeFileSync(f, s);
console.log('README updated');
