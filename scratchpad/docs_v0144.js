// v0.14.4: CurseForge description updates (non-Thor). Run once; throws if an anchor is missing.
const fs = require('fs');
const P = 'docs/CURSEFORGE_DESCRIPTION.md';
let s = fs.readFileSync(P, 'utf8');
const nl = s.includes('\r\n') ? '\r\n' : '\n';
s = s.replace(/\r\n/g, '\n');
const rep = (a, b) => { if (!s.includes(a)) throw new Error('missing anchor: ' + a.slice(0, 70)); s = s.replace(a, b); };

rep(`You can hold **two Primary
  powers at once**; your mutations (up to three, stacking) share a single slot. Gaining a new hero replaces your oldest.`,
`You hold **one Primary
  power at a time**; your mutations (up to three, stacking) count as that one power. Gaining a new hero -- lifting
  Mjolnir included -- replaces what you had.`);

rep(`- **Black Suit Spider-Man** and **Agent Venom** (with the Punisher) get their own Symbiote extras.`,
`- **Symbiote Pets:** a free Symbiote also takes over animals, wolves and cats. Sneak + right-click your own wolf or cat
  while suited and it becomes a hulking black **Symbiote Pet** with white eyes, regeneration, a Tendril Lash, a Pounce and
  a Spike Burst -- loyal to you and your squad.
- **Black Suit Spider-Man** and **Agent Venom** (with the Punisher) get their own Symbiote extras.`);

rep(`- **Go Turbo** (**N**) into the nanotech suit, powered by T.U.R.B.O. energy.`,
`- **Go Turbo** (**N**) into the nanotech suit, powered by T.U.R.B.O. energy -- or press a mode key and go straight from
  Normal form into that Turbo mode. T.U.R.B.O. energy recharges in Normal form too.`);

rep(`- Every move has its own animation, the HUD is a framed ring-charge panel, and the ring on your hand glows and flares
  as you use it.`,
`- Every move has its own animation, a clean ring-charge HUD shows every key, and the ring on your hand glows and flares
  as you use it.`);

rep(`- **Banner can't be killed:** a fatal hit just unleashes the Hulk. Lose control and he goes on a rampage -- hitting
  friend and foe alike, squadmates included -- and you'll need the breathing minigame to calm him down.`,
`- **Banner can't be killed:** a fatal hit just unleashes the Hulk. Lose control and he goes on a rampage -- hitting
  friend and foe alike, squadmates included, who can fight back -- and you'll need the breathing minigame to calm him down.`);

rep(`hit **+7** harder, run **30% faster**, jump over
  **two blocks** and take **20% less** damage.`,
`hit **+7** harder, run **30% faster**, jump over
  **two blocks**, step straight up full blocks, take **20% less** damage and **half** fall damage.`);

rep(`- **Lunar power:** everything scales with the moon -- x1.5 under a full moon, weaker as it wanes, x0.7 by day, and less
  underground. **Vengeance** builds by protecting villagers and travellers from monsters at night, and powers his
  strongest moves.`,
`- **Lunar power:** three states -- **Day** x0.7, **Night** x1.0, **Full Moon** x1.5 (the Nether and the End count as
  day). **Vengeance** builds by protecting villagers and travellers and killing monsters, regenerates when you're out of
  combat, and powers his strongest moves.`);

rep(`**Steven** the scholar in the Mr. Knight suit
  (Scholar's Sight finds chests and ores through walls, better trades)`,
`**Steven** the scholar in the capeless Mr. Knight suit
  (Scholar's Sight finds chests and ores through walls, better trades, **Fortune III** on everything he mines)`);

rep(`- **R** Crescent Darts (homing at night, boomerang back; a charged fan; **Moon Mark**) · **G** Grapple Kick · **X** Dash,
  that goes wherever you aim, and **Sneak+X** a 100-block Grappling Line that reels mobs in · **Z** Moonbeam, **Khonshu's Judgement** and the ultimate
  **Eye of Khonshu** under a full moon · **C** Truncheon (three-hit combo slams, a staff spin, ground / dive slams) ·
  **Sneak+G** Shadow Step.`,
`- **R** Crescent Darts (homing at night, boomerang back; a **Crescent Fan** of five darts that each lock on to an enemy;
  **Moon Mark**) · **G** Grapple Kick (20 damage, with aim assist and a lock-on marker) · **X** Dash, that goes wherever
  you aim, and **Sneak+X** a 100-block Grappling Line that reels in mobs and squadmates · **Z** an area-blast **Moonbeam**,
  **Khonshu's Judgement** (a 15 s burn that heals you) and the ultimate one-minute **Eye of Khonshu** that calls moonbeams
  down on everything around you · **C** summons the **Truncheon** into your hand (a three-hit combo, a 15-damage staff
  spin, ground / dive slams) · **Sneak+G** Shadow Step.`);

rep(`Catch the **Gravebound Curse** from a Graveyard or a Cursed Zombie, then cure it with an Enchanted Golden Apple or survive
**twelve waves** and three **Powered Zombie Bosses**, each wielding a real superpower.`,
`Catch the **Gravebound Curse** from a Graveyard or a Cursed Zombie, then cure it with an Enchanted Golden Apple or survive
**twelve waves** and three **Powered Zombie Bosses**, each wielding a real superpower. Their heads are **wearable,
placeable trophies** that remember who slew them. The curse can strike again and again.`);

rep(`A **Pillager Spy** attacking you in a village sets off five raider waves and an **Empowered** supervillain with a random
power and look.`,
`A **Pillager Spy** attacking you in a village sets off five raider waves and an **Empowered** supervillain with a random
power and look. Raids are repeatable: spies keep coming, even for a village that has been raided before.`);

rep(`A zombie that isn't what it seems. At low health it becomes an **18-block giant** that hunts you with punches, sweeps,
stomps, shockwaves, grabs, boulder throws and charges, all telegraphed and all dodgeable.`,
`A zombie that isn't what it seems. At low health it becomes an **18-block giant** that hunts you with punches, sweeps,
stomps, shockwaves, grabs, boulder throws, charges, a **Leaping Slam** and a **Grave Roar** that raises the dead -- every
move animated, telegraphed and dodgeable. It turns on whoever hurts it most, so the whole group has to stay sharp.`);

fs.writeFileSync(P, s.replace(/\n/g, nl));
console.log('CURSEFORGE_DESCRIPTION v0.14.4 (non-Thor): ok');
