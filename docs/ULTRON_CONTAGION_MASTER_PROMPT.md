# Ultron: Harder Uprising + The Contagion: master prompt

Paste everything under **PROMPT** into a new session to build it. The notes above that line are for you.

## Design notes (the short version)

### Part A: a harder Ultron Uprising
The v0.15.12 raid is too easy. Make it tougher with straight numbers and smarter fights, **not** with resistances:
more waves, a 4th pylon, pylons guarded by shields you have to clear, two new units that go after players' strengths
(shielded Lancers and cloaked Hijackers), and new moves for Prime and the Sentry. There is **no damage adaptation**:
your attacks always do what they say.

### Part B: The Ultron Contagion (he comes back)
- **"You can't kill an idea."** When Ultron dies for the first time in a world, his last line is a promise. A few
  in-game days later he's back, not as a raid but as an **infection that spreads through the world**.
- **Patient zero:** the Ultron Core trophy and any Stark machine (Fabricator, Sorter, Stark Furnaces, Gantry). One
  of them flickers red, and JARVIS glitches: "Sir, something is... *I am still here.*"
- **The infection:**
  - **Ultron Nexus** spires grow out of the ground near players and spread red-veined nanite corruption across the
    terrain.
  - Mobs inside the corruption become **Assimilated** (red eyes, metal plating, ranged shots), iron golems turn into
    **Ultron Golems**, and Stark machines get **Compromised**.
  - Iron Man suits face **hijack attempts**.
  - A world-wide **Saturation** bar fills while the Nexus spires stand.
- **Fighting back:**
  - **Purge Charges** clean corruption.
  - **Firewall Beacons** protect your base.
  - The **Override Spike** lets you capture a Nexus: hold the zone while Ultron throws everything at you, then it
    flips blue. The Mind Stone makes that faster.
  - Each captured Nexus lets JARVIS **trace** Ultron. Three traces reveal where he is hiding.
- **The finale: the Sokovia Protocol.** Ultron raises a floating sky-citadel around a **Vibranium Core**, and if he
  charges it to 100% the citadel drops. You fight **Ultron Omega** on it:
  - **Omega:** heavy vibranium melee, blasts that knock you toward the edge, and rotating beams.
  - **Legion:** he splits into decoys, and only the Mind Stone or an Iron Man scanner shows the real one.
  - **Iron Legion:** he hijacks empty Iron Man suits (your own Mark models) and turns them on you.
  - **Upload:** he tries to escape into the network, and you must cut three antennae in time.
  - **The finishing blow** is the **Mind Stone beam**: the Vision moment.
- **Win:** the infection is wiped and the corruption reverts. You get **Vibranium**, the **Omega Core** and an
  empowered Mind Stone.
- **Lose:** the citadel crashes (cosmetic crater, no real terrain destroyed). Ultron survives and the Saturation resets
  to 50%, so you try again.
- **He keeps coming back:** after the Omega fight, Ultron can **resurface** rarely as a stronger version (v2, v3...),
  unless you keep Firewall Beacons running. It's all config, and it can be switched off.

### Skins for the new Ultron units
Search The Skindex for "ultron age of ultron", "ultron mark", "ultron omega" and "iron legion". Show the user a
contact sheet and **ask before downloading**. The existing Ultron units stay on their current skins (see
`docs/ULTRON_REFERENCE.md` section 6). The **Iron Legion** reuses the existing Iron Man Mark models and textures with a
red Ultron-eye glowmask, with no new skin. Ultron Omega should get a **custom GeckoLib model** (generator pipeline, like
the Titan) rather than a player model. He's the final form, so make him tall (4 blocks), sleek, silver and red, with a
vibranium sheen.

---

## PROMPT

Two changes for Project Hero (Fabric 1.21.1, mod id `projecthero`, package `com.projecthero.mod`, repo at
`C:\Users\ethan\OneDrive\Desktop\Coding Projects\Superhero Mod`): **(A)** make the existing Ultron Uprising
significantly harder, and **(B)** add **The Ultron Contagion**, a world infection that starts after Ultron's first
defeat and ends in a sky-citadel boss fight against **Ultron Omega**. These can ship as two releases (A first). Check
`gradle.properties` **and the existing git tags** before picking each version, and bump `version=`.

Read these before writing code:
- `docs/ULTRON_REFERENCE.md`: the current raid, every number, and every class name.
- `com.projecthero.mod.ultron`: everything (`UltronUprising`, `UltronConfig`, `UltronCombat`, `UltronPrimeEntity`,
  `UltronSentryEntity`, `UltronPylonEntity`, `UltronRewards`, `MindStoneItem`, `MindStoneCharm`, `UltronDialogue`).
- `com.projecthero.mod.ironman`: JARVIS (`JarvisDialogue`), `IronManHighlight` (**private highlight rule: never set
  a server glowing flag**), `IronManTargetScanner`, `IronManSentryEntity`, drones, the HUD.
- `BossThreat` and `docs/TARGETING.md` (every boss uses the shared threat table), `com.projecthero.mod.event`,
  `docs/TITAN_BOSS_REFERENCE.md` + `scratchpad/gen_v01420_titan_model.js` (custom-model pipeline for Ultron Omega).
- Your memory files, **especially**: the stale-config trap (bump `UltronConfig` to VersionedConfig **v2** with a
  migration that rewrites the changed balance keys, or old worlds keep the easy numbers), `ServerStateReset` for every
  static cache, frozen-chunk spawn gotchas, and the lag lessons from the Titan block-throttle work.

---

### PART A: Harder Ultron Uprising

#### A1. No adaptation (user decision)
Ultron's robots and bosses do **not** build resistances. The existing `UltronCombat` multipliers stay exactly as they
are (lightning / Electrokinesis x1.5, Hulk or heavy direct hits x1.25, immune to poison and wither). Every difficulty
increase below comes from health, damage, unit mix and boss moves. Do not add any per-damage-type scaling.

#### A2. Tuning (all in `UltronConfig` v2, migrated)
| | Old | New |
|---|---|---|
| Waves | 5 | **7** |
| Pylons | 3 | **4** (on a 24-block ring) |
| Pylon HP | 300 + 100/extra | **450 + 150/extra** |
| Pylon repair aura | 2 HP/s, 16 blocks | **3 HP/s**, 16 blocks |
| Pylon shield | none | **invulnerable while 3+ Ultron units stand within 8 blocks of it** (red shell; clear the guards first) |
| Drone HP / bolt | 30 / 4 | **40 / 5** |
| Sentinel Drone HP | 45 | **60** |
| Heavy HP / missiles | 120 / 3 | **180 / 4** |
| Sniper shot | 14 | **16**, and it **relocates** to another pylon after each shot |
| Alive cap | 30 | **36** |
| Prime HP | 900 + 300, cap 3000 | **1600 + 450, cap 5000** |
| Prime reserve pylons | 2 at 150 HP | **2 at 300 HP**, and they're shielded while Prime is above 50% |
| Sentry HP | 1500 + 400, cap 4000 | **2600 + 600, cap 7000** |
| Sentry shield drones | 3 | **4**, and they **re-shield** once at 25% |
| All Ultron damage | x1.0 | **x1.3** (config `damage.globalMultiplier`) |

Solo waves (drones / sentinel drones / heavies / snipers / lancers / hijackers): w1 6/0/0/0/0/0, w2 8/2/0/0/0/0,
w3 6/4/1/0/0/0, w4 6/4/1/2/1/0, w5 6/4/2/2/1/1, w6 8/5/2/2/2/1, w7 8/6/3/3/2/2.

#### A3. Two new units (player-model robots like the existing ones, Skindex skins, ask first)
| Unit | HP | Behaviour |
|---|---|---|
| **Ultron Lancer** | 90, armour 8 | Carries a **front energy shield** (blocks projectiles and beams from the front; flank it or hit from above). Advances in a line and lunges with an energy lance (10 dmg, reach 4). Shield drops for 2 s after each lunge. |
| **Ultron Hijacker** | 50, armour 2 | Fast and cloaked (a shimmer only) until within 6 blocks, then latches onto a player for up to 4 s: **disables one random ability or power key** of theirs (hero abilities, Iron Man slots, mutation slots) for 6 s, and on Iron Man suits drains 15% energy. Hitting it breaks the latch. The Mind Stone and Iron Man highlight show it while cloaked. |

#### A4. New boss moves
- **Prime:**
  - **Overclock** (below 66%): for 6 s he moves 50% faster and his barrage fires 8 beams.
  - **Encephalo-beam** sweeps **twice** (back and forth) in phase 3.
  - **Network Override**: targets the highest-threat fighter and disables their abilities for 4 s (a red static
    overlay on their screen; telegraphed with a 1 s targeting reticle above their head).
- **Sentry:**
  - **Chest cannon** now tracks slowly (it turns 20°/s toward the target during the beam).
  - **Fist Rocket**: launches a forearm as a homing projectile (18 dmg, 2 s flight, can be shot down for 60 HP).
  - **Reconstruct**: at 50% and 25%, a 6 s channel where every drone in the arena flies into him (each one absorbed
    heals 2%). Kill the drones first.
- **Body jump** keeps working as it does now.

#### A5. Death line (leads into Part B)
Change the Sentry's final death line on a world's **first** clear to: "You can't kill an idea. I'll see you soon."
Store a world-level flag `ultron_first_defeat_time` (`SavedData`). That starts the Contagion timer (Part B).

---

### PART B: The Ultron Contagion

#### B1. How it starts
- `contagion.delayDays` (default **3-6 in-game days**, random) after the first Uprising clear in that world, the
  Contagion begins. Config switch `contagion.enabled` (default true). It never runs on Peaceful.
- **Patient zero:** pick, in order of preference:
  1. a placed **Ultron Core** trophy block;
  2. a Stark machine (Fabricator, Sorting Station, Stark Furnace / Smelter / Smoker, Gantry);
  3. a random spot 80-140 blocks from a random online player who fought in an Uprising.
  It must be in a loaded, ticking chunk. The block flickers red for 20 s, then the first **Ultron Nexus** grows there.
- Announce:
  - JARVIS to every Tony Stark player (the voice glitches with red text): "Sir, I'm detecting... *I am still here.*"
  - Everyone else gets a title: "THE CONTAGION". Ultron in chat: "Did you really think deleting a body would delete
    me?"
  - `/projecthero contagion start|stop|status|saturation <0-100>|trace` for ops.

#### B2. Saturation (the world-wide pressure)
- One **Saturation** value per world, 0-100%, in `SavedData`. Shown on a **Hairline** HUD bar (the user's name for
  the thin bar style) in red at the top of the screen, for every player while the Contagion is active, with the number
  of active Nexus spires.
- Rises **+1% per active Nexus per 2 minutes** (config). Falls 5% when a Nexus is purged, and 1% per 2 minutes per
  lit Firewall Beacon (as long as the Saturation is under 90%).
- Thresholds:
  - **25%:** a new Nexus can spawn every 15 min (instead of every 25).
  - **50%:** Assimilated mobs get +25% health; Ultron Golems appear.
  - **75%:** **Ultron Hunter-Killer squads** (3 drones + a Heavy) are sent at random players every 10 min, wherever
    they are (respecting spawn rules and loaded chunks).
  - **100%: The Sokovia Protocol starts on its own** (B6), whether or not you're ready. That's the loss pressure.

#### B3. Ultron Nexus (the infection hubs)
- A spire entity or multi-block structure, 9 blocks tall, black metal with red veins and a pulsing red eye. It **grows
  out of the ground over 2 minutes** (visible from far away: a red beam up to the clouds while growing).
- New ones spawn 64-160 blocks from a random online player every 25 min (or 15 at 25%+), max `contagion.maxNexus`
  (default 4) at once. Never inside a Firewall Beacon's radius. Never in claimed/protected spawn. Loaded chunks only.
- **Corruption:** each Nexus spreads **Nanite Corruption** in a radius that grows to 32 blocks. It converts **only
  natural surface blocks** (grass, dirt, stone, sand, gravel, logs and leaves) into **Corrupted Circuitry** variants
  (dark metal with animated red veins, a light red emissive layer). The spread is capped at N blocks per tick and every
  converted block's original state is recorded in the Nexus's saved data, so **purging reverts it exactly**. It never
  touches player-placed blocks: skip any block with a block entity and anything outside a natural-block tag. A
  `contagion.terrainCorruption` config switch turns it off (particles-only mode).
- **Inside the corruption:**
  - **Assimilated mobs:** zombies, skeletons, spiders, creepers and pillagers that spawn or walk in become
    Assimilated. They get a red-eye overlay layer, half-metal plating, +4 armour, immunity to poison, and a 4-dmg red
    bolt at 16 blocks. Skeletons fire red bolts instead of arrows. Creepers become **Proximity Mines**: faster fuse,
    an EMP blast that drains Iron Man energy.
  - **Ultron Golems:** iron golems inside the corruption are converted after 10 s (red eyes, they turn hostile, 150
    HP, a magnetic slam). Purging the Nexus converts them back (they keep their health).
  - **Compromised Stark machines:** any Stark machine inside stops working, glows red and spawns 1 Ultron Drone every
    60 s (max 3 alive per machine) until the Nexus is purged or a Purge Charge hits it.
  - **Villagers** shut themselves indoors (they go home as if it were a raid) and refuse to trade.
- **Iron Man suits:**
  - inside corruption, the HUD glitches (a red static flash for 1 s every 30-60 s);
  - JARVIS speaks in Ultron's voice now and then;
  - once per Nexus visit, Ultron attempts a **Hijack**: a red "OVERRIDE ATTEMPT" Hairline fills over 4 s. Getting out
    of the corruption, or hitting R/attack 6 times, or having a **Firewall Chip** installed (B5) breaks it. If it
    completes, the suit locks for 5 s (no flight, no abilities, the visor goes red), then JARVIS fights it off.
    Annoying, not deadly.

#### B4. Taking a Nexus down: the Override
- A Nexus has **800 HP** and a shield that blocks all damage **until it's Overridden**. You can't just shoot it.
- **Override Spike** (an item; B5): use it on the Nexus to start a **capture**. A 10-block capture circle appears.
  Stand in it for **60 s** (the timer only runs while a non-downed player is inside, and goes twice as fast with
  2+ players). Ultron throws waves from the Nexus the whole time (drones, Lancers, Hijackers, a Heavy at 50%, and an
  **Ultron Prime echo** at 80%: a weaker Prime with 600 HP, no body-jump).
- **The Mind Stone** held in the off hand while inside the circle makes the capture **40% faster** and reveals cloaked
  Hijackers (it was the source of Ultron's mind, so it speaks his language).
- When the capture completes the Nexus shield **flips blue**. Now break it (800 HP). When it falls:
  - the corruption reverts (the recorded blocks, a few hundred per tick, with a blue wave visual);
  - the golems revert, the machines un-compromise, and the Assimilated mobs inside die (they drop scrap);
  - Saturation -5%, and JARVIS gets a **Trace** (one of three pieces). JARVIS: "Trace fragment acquired. He's routing
    through somewhere... I need more."
  - Drop: Ultron Scrap x(3-6), and 1 **Nanite Sample** (used for crafting).

#### B5. New items (all craftable; recipes use Ultron Scrap, Nanite Samples and existing Stark materials)
- **Ultron Scrap:** drops from all Contagion robots and Assimilated mobs (20%).
- **Purge Charge** (throwable, stacks to 16): scrap + redstone + copper + an EMP core (sculk sensor or similar).
  Bursts into a blue EMP in 6 blocks that reverts corruption there (only blocks recorded by a Nexus), stuns robots for
  3 s, un-compromises machines and strips Assimilated plating (they become normal mobs).
- **Firewall Beacon** (block): Nanite Sample x2 + Arc Reactor + Vibranium Plating + iron blocks. Needs power: refuel
  with redstone blocks (1 block = 1 in-game day). While running, a **48-block radius** is immune to corruption, to Nexus
  spawns and to hijacks. A blue hex dome flickers at its edge when Ultron "probes" it. It lowers Saturation (B2) and,
  after the Omega fight, keeps Ultron from resurfacing near your base (B8).
- **Override Spike** (consumable): Nanite Sample + Ultron Scrap x4 + a lightning rod + an Arc Reactor fragment (or
  redstone block). One per capture.
- **Firewall Chip** (Iron Man upgrade, smithing table, base = a helmet, addition = the chip; no template): that suit
  is immune to hijacks. Shown on the I-key spec sheet.

#### B6. The Sokovia Protocol (the finale)
- **Trigger:** 3 Traces collected (or Saturation hits 100%). JARVIS: "Got him. He's not hiding, he's *building*."
  A red beam marks a location 300-600 blocks from the players' average position (a dry, loaded-able area). The
  location is shown on the compass/HUD as a red diamond, and the event waits **at most 1 in-game day** (or until a
  player arrives within 64 blocks) before starting on its own.
- **The citadel:**
  - **Do not rip up real terrain.** Generate a **floating sky-citadel** 90 blocks above the ground: a 50-block-wide
    disc of corrupted rock and Ultron architecture, built from a structure template or by code, with broken edges and
    hanging rock.
  - The **Vibranium Core** stands at the centre: a tall machine, the event's anchor and its bar.
  - 4 **Lift Engine** spires stand at the edge, and 3 **Uplink Antennae** rise in phase 4.
  - Players get there by flying, by an **Ultron rising-rock path** (temporary floating stepping-stone blocks that spiral
    up from the ground, so non-fliers can climb), or with a one-time **Stark Lift** (right-click the marker beam's base
    to be carried up on a blue beam).
  - Falling off: Iron Man/Kryptonian/Thor/Green Lantern/Nova fly back. Anyone else gets slow-fall and is put back on
    the edge after 2 s of falling (the citadel's "safety net"), taking 6 damage. The fall is never instant death.
  - Everything is removed at the end. Track every placed block.
- **The Core charge:** the Vibranium Core charges 0 → 100% over **12 minutes** (faster when Ultron touches it; see
  the phases). At 100% the citadel **drops** = defeat. Destroying a **Lift Engine** (shielded until its guard dies; 500
  HP) **removes 15% charge**.

**Ultron Omega:** a custom GeckoLib model (generator pipeline, like the Titan). 4 blocks tall, sleek and inhuman:
silver-white vibranium plating over a black frame, a narrow face with a sharp jaw, glowing red eyes and mouth, red
energy lines down the chest and arms, and long fingers. Flies. Health **4000 + 900 per extra fighter, cap 12000**; 16
armour / 10 toughness (no adaptation; see A1); own boss bar; `BossThreat` aggro; dialogue lines
`boss.projecthero.ultron_omega.say.*` (original lines).

1. **Phase 1: Omega** (100-75%)
   - Moves:
     - **Vibranium Strike** (melee combo, 3 hits, 8/8/14).
     - **Concussive Blast** (a red cone, 12 dmg, knockback toward the edge; mind the drop).
     - **Encephalo Lattice** (three beams that sweep in rotation around him for 4 s; find the gap).
   - **Core Siphon**: every 60 s he flies to the Core and channels for 5 s (+5% charge). Interrupt by dealing 8% of
     his max health in that window.
2. **Phase 2: Legion** (75-50%)
   - He splits into **4 identical Omegas**. Only one is real. The decoys have 1 HP-equivalent bars that never drop
     visibly, deal 50% damage, and every hit on a decoy **heals the real one 0.5%**.
   - Tells:
     - the **Mind Stone** holder sees the real one outlined gold (client-side only);
     - **Iron Man's scanner** shows its real readout;
     - everyone else: the real one's eye flickers in sync with the Core's pulse.
   - Hitting the real one for 10% of his health collapses the decoys.
3. **Phase 3: Iron Legion** (50-25%)
   - "Stark built me an army. I'm only borrowing it."
   - Ultron hijacks **empty Iron Man suits**: spawn 3 + fighters **Hijacked Mark** units using the existing Iron Man
     Mark models/textures (Mark 2/III/4/5/6, randomised) with a red visor glow, flying, using repulsors (5 dmg) and
     unibeam (2 s telegraph, 14 dmg). 150 HP each.
   - If a fighter is wearing an Iron Man suit, at least one hijacked suit copies **their current Mark**.
   - While 3+ Legion suits are alive, Ultron is shielded.
   - A **Purge Charge** frees a hijacked suit: it powers down and drops 1 Ultron Scrap (never the actual armour).
4. **Phase 4: Upload** (25-0%)
   - At 25% Ultron tries to **escape into the network**. Three **Uplink Antennae** rise at the citadel's edge (300 HP
     each + 100 per extra fighter) and a 35 s "UPLOAD" bar starts.
   - He is shielded while uploading, but spams drones and Concussive Blasts.
   - All three destroyed in time: he's **stranded**, takes x1.5 damage and goes desperate (faster, enraged).
   - If the upload finishes: **he escapes**. That's a partial loss: the citadel lowers safely, Saturation = 50%, one
     Trace is lost, and he rebuilds (he can be re-traced).
5. **The Vision Moment** (finishing blow)
   - At 0 HP he doesn't die. He staggers, kneels, and the screen tints gold: "You think you've won?"
   - Any fighter holding the **Mind Stone** gets a prompt: hold right-click for 3 s to fire the **Mind Stone Beam**
     (a thick golden beam into him). He disintegrates ("Ultron, you're unbearably naive.", or an original line).
   - If no one has a Mind Stone, he self-destructs after 10 s (6-block blast, a 3 s telegraph) and still dies.
     That's a weaker ending, but still a win.

- **Defeat** (the Core hits 100% or everyone is downed):
  - the citadel drops with a huge cinematic fall and an impact flash at the bottom, then disappears;
  - a **cosmetic crater** (a decal of scorched and red blocks recorded and reverted after 5 minutes, or particles-only
    in safe mode), never a real crater;
  - Saturation = 50%, the Traces reset, and Ultron taunts.

#### B7. Victory rewards
The Vibranium Core becomes the reward chest:
- **Vibranium Ingots** x (2 + fighters), a new material:
  - the **Vibranium Shield** (the TODO from v0.15.12), a better Super Soldier shield (or a separate shield);
  - **Vibranium Plating II** for Iron Man (+3 armour and +2 toughness per piece; replaces Plating I).
- **Omega Core** (trophy block: Ultron Omega's head on a pedestal, the eye slowly dimming).
- Ultron Scrap, netherite scrap, diamonds, emeralds, enchanted books (party-scaled).
- **First ever Omega win** (per-player attachment, like `ultron_clears`):
  - an **Empowered Mind Stone**: the existing Mind Stone powers plus the **Mind Stone Beam** (right-click hold, 2 s
    golden beam, 6 dmg every 5 ticks, 60 s cooldown);
  - Iron Man players: JARVIS gets the "Firewall" protocol permanently (hijack-immune on every suit).
- World: the infection is wiped. All Nexus spires collapse, every recorded block reverts, the golems revert and the
  machines are restored. JARVIS: "He's gone, sir. Truly, this time... I think."

#### B8. He keeps coming back: Resurgence
- After an Omega win, Ultron can **resurface** (`contagion.resurgence`):
  - default: a **15% chance every 20 in-game days**;
  - **0% while any Firewall Beacon in the world is fuelled**, so maintaining defences pays off.
- Each resurgence is a **new version**: "Ultron v2", "v3"... (stored in the world `SavedData`). Each version adds +20%
  to Contagion unit HP, +1 max Nexus, +10% Omega HP and +10% Omega damage, capped at
  v5. The chat intro names the version ("Version 3.0. Faster. Stronger. Still me.").
- The raid itself (Part A) stays repeatable as before.
- Config: `contagion.resurgence.enabled`, chance, interval, scaling, max version.

#### B9. Engineering checklist
- Package `com.projecthero.mod.ultron.contagion` (+ client counterpart). `ContagionConfig` (VersionedConfig v1),
  `ContagionState` (`SavedData`: Saturation, Nexus list, Traces, version, first-defeat time, recorded blocks per Nexus,
  Firewall Beacons).
- **Lag:** throttle corruption spread, reverts and particles. Never force-load chunks (process Nexus spires in
  loaded chunks only, and pause the Saturation rise for Nexus spires in unloaded chunks). Assimilation conversion is
  checked on spawn plus a slow sweep, not every tick.
- **Safety:** never convert player-placed blocks or block entities, and never modify blocks outside the recorded set
  on revert. The citadel and all temporary blocks are tracked and removed on win, loss, abort, `/projecthero
  contagion stop` and on load if orphaned.
- Static caches in `ServerStateReset`. **Private highlight rule** for the Mind Stone gold outline and Hijacker reveals
  (client-side decision only).
- Multiplayer: Saturation and Traces are world-wide. Captures, the citadel and rewards follow the Uprising's
  fighters/down rules.
- Gametests: robots take the same damage from repeated hits of one type (no adaptation); the config v2 migration rewrites the old keys; the
  Contagion starts after the delay; a Nexus records and reverts its blocks exactly and never touches a block entity; the
  capture timer needs a player inside; Firewall stops spread and spawns; Purge Charge reverts and un-compromises; Legion
  decoys heal the real Omega; the Upload escape path; the Vision moment with and without a Mind Stone; the first-win
  rewards are granted once; resurgence is blocked by a fuelled Firewall. **Policy:** no local gametest runs. Compile,
  push master, and let CI run the suite.
- Docs: update `docs/ULTRON_REFERENCE.md` (Part A) and add `docs/ULTRON_CONTAGION_REFERENCE.md`. Guide chapter
  entries, lang, tooltips (keep the TooltipWrap guard). Update the Threat Ladder.
- **Screenshots:** use the harness to capture a Nexus growing, the corruption, an Assimilated mob, an Ultron Golem,
  the hijack HUD, the citadel, Omega in every phase, the Iron Legion and the Mind Stone beam, and show them to the user
  in Claude Code.
- **Release:** tag with the full changelog annotation, verify CurseForge, GitHub and Discord all arrived, then
  `./gradlew copyToModpack`.
