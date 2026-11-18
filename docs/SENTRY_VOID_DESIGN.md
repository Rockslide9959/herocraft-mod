# Sentry & The Void (v0.17.5) — APPROVED 2026-11-15

Based on the comics (Bob Reynolds, "the power of a million exploding suns", molecular control, the Void as his
other half) and Thunderbolts* (O.X.E. vault, the Void erasing people into shadows, the shame rooms, the team
reaching him from inside).

## Two meters
- **Sun (gold, Gauge):** fuels Sentry moves. Fills fast in daylight, slowly at night, not at all in the dark.
- **Void (black, Hairline under it):** the danger. Rises when you use big moves, take heavy hits, kill things, fight
  at night or stand in darkness. Falls in daylight, while healing others, and while you hold **V (Calm)**.

| Void | What happens |
|---|---|
| 50 | whispers, black veins creep in from the screen edge, your golden glow flickers dark |
| 75 | keys sometimes misfire into Void moves, your aim drifts toward the nearest living thing |
| 90 | heartbeat, the sky dims around you, everything you hit leaves a burnt shadow on the ground |
| 100 | **The Void takes over** |

## Sentry kit (H selects the power; X movement, V utility, rest attacks) -- SUPERSEDED by moveset 2 and round 3 (below, built 2026-11-18)
| Key | Move |
|---|---|
| R | **Golden Strike:** heavy punch combo, every 3rd hit a golden shockwave |
| Shift+R | **Sunbeam:** a solid beam of light from both hands (beam clash) |
| G | **Molecular Grip:** lift a creature in a golden field; release throws it, hold G tears it apart (big damage) |
| Shift+G | **Unmake:** turns a 3x3 patch of blocks into drifting light (only blocks, no drops) |
| Z | **Sun Dive:** from the air, dive and slam a golden crater |
| Shift+Z | **A Million Suns:** 3 s charge, then an explosion of light (huge damage, big Void gain) |
| C | **Healing Light:** heals allies around you and clears bad effects (lowers Void) |
| Shift+C | **Sentinel Shield:** a golden dome that blocks projectiles for 6 s |
| X | **Sonic Boom:** a sonic-boom dash (rework 2026-11-16: X is only the dash) |
| 2x Space | **Flight** (toggle; rework: the same for the Void) |
| V | **Calm:** hold to meditate, Void drains, you can't attack meanwhile |
| Shift+V | **Let it out:** (rework) a toggle -- win a short trial to merge with the Void (Void Form): stronger, but Void climbs faster; lose and the Void jumps 15 |

## Void Form (Let It Out won, or forced by the takeover) -- the kit table is SUPERSEDED by moveset 2 (below, built 2026-11-18)
Rework: wearing the Sentry Suit, the suit fades into the Void suit (the user's skin) and the cape goes black; without it, the original
black-and-gold-veined body fades in. Darkness smoking off you.
| Key | Move |
|---|---|
| R | **Shadow Lash:** tendrils whip everything in front of you |
| Shift+R | **Erase:** a creature is wiped into a burnt shadow on the ground (huge damage; kills leave the shadow) |
| G | **Grasp:** tendrils drag several creatures to you |
| Z | **Void Rift:** a black hole that pulls and crushes for 4 s |
| Shift+Z | **Nightfall:** a dome of darkness (Darkness on enemies, you regenerate inside) |
| X | **Shadow Step:** teleport to any dark spot you look at |
| 2x Space | **Flight** |
| Shift+V / V | **Return:** go back to Sentry (only works below 90 Void) |

## The takeover (the cool part)
- **The body is not yours.** Third-person camera pulled out behind you, the screen bled to black and gold. The Void flies
  your body on its own and attacks **everything living** around it, using the Void Form kit at full strength.
- **It eats the world:** black void matter spreads out from it in a growing ring, swallowing blocks into nothing; mobs it
  kills are absorbed (it heals); the sky goes dark around it; the day/night clock freezes at midnight near it.
- **It moves:** it hunts the nearest living things, then wanders toward villages and players, so it doesn't just sit.
- **Regaining control is hard, three layers:**
  1. **Nothing for the first 30 s.** You just watch.
  2. **The Shame Rooms (mind screen):** you're in a black room with memory doors. Points of light drift in the dark —
     click them to fill the Light bar while darkness shapes chase your cursor and drain it. **Three rooms**, each faster
     and darker; failing a room sends you back a room and adds 20 s. The longer the takeover runs, the harder it gets.
  3. **Help from outside:** other players can **reach** you — walk up to the Void and hold right-click on it with an
     empty hand. Each one drains its darkness (and takes hits for it). Daylight weakens it a little; night makes it stronger.
- Winning all three rooms (plus any outside help) throws the Void out: a golden flash, you drop to your knees,
  Void resets to 40 and you can't use Shift+V for 5 minutes.
- **Death ends it** (respawn normally, Void at 60).

## Getting it
- **Sentry Serum** (golden vial) inside The Vault (Tier 4): a sealed O.X.E. serum vat behind Warden Kross's arena.
- Plus the usual admin/give paths, Guidebook chapter, JEI page.

## Decisions (user, 2026-11-15)
1. Eaten world **comes back** about a minute after control returns; gamerule `projectheroVoidGriefing` makes it permanent.
2. The Void attacks **everything**: mobs, players, squadmates and pets.
3. Sentry Serum comes **only from The Vault** (sealed O.X.E. vat behind Kross).
4. **Shift+V Void Form exists** (at 50+ Void). Superseded 2026-11-16: the trial replaces the 50 gate.

## Rework (user, 2026-11-16)
User: "dont put the suit on automatically when the player gets the power, make the suit craftable and make it repair itself over time.
Add this suit to the game as the sentry suit ... fading into this suit [the Sentry merged with the Void] ... Remove the skin and hair from
these suits ... blue cape ... black cape ... max hp 40 ... void bar fill up a lot slower ... let it out move a toggle ability ...
minigame ... sentry armour make void bar go up slower and sunlight recharged faster ... entire players body go black ... tiny glowing
white pixels for eyes" + "Make double tap space bar his flight ability and make it the same for the void power. Make X just a dash
ability for regular sentry."

- **The suit is an item now.** The power draws nothing. The **Sentry Suit** (top + trousers, no helmet: his own face and hair show) is
  crafted from gold, blue wool and hero alloy, diamond-level, and mends itself (1 durability / 3 s, worn or carried). Worn whole by the
  Sentry: Void gains x0.6, Sun x1.5. Look: the user's comics skin with face / hair / bare skin removed; a blue cape.
- **Let It Out = a toggle won through "Hold the Light"** (8 s: keep your falling light inside a drifting sun by holding Space / the
  mouse). Lose: +15 Void. Win: merged with the Void (Void kit + flight, +25% damage, Void cooldowns x0.75, the Void rises faster: x1.5
  and 0.5/s). Shift+V again returns (below 90). No 50 gate.
- **The fade:** wearing the suit, the comics suit dissolves outward from the heart into the user's Void suit over 2.5 s, cape blue ->
  black; reverse on return.
- **40 max health** in both forms. **The Void fills about a third as fast** (numbers in the reference).
- **Takeover:** the whole body black (suit and cape too), two tiny glowing white pixel eyes.
- **Flight = double-tap Space** in both forms; **X = Sonic Boom dash** only (merged X stays Shadow Step).
- **Licence:** the comics skin page says "ask me before using it in a heropack or addon"; both processed textures are held back from git
  and public builds (fallback: the original art) until permission is given. Credits in the reference.

## Takeover rework 2 (user, 2026-11-17)
User: "The void takeover needs to work better, he destroys some blocks and then flies down into his own hole and then when he targets
something above ground again he gets stuck in his own hole ... if hes moving towards something and gets stuck then he decays those blocks
aswell, increase the range ... make it so that the blocks slowly fade to black before dissolving quickly ... make it so that the void lifts
up into the air (50 blocks above the ground) and looks down on the destruction ... the range at which he destroys the world keeps
increasing and increasing and only he goes down slowly to the ground start fighting players and strong enemies"

- **It hangs 50 blocks up** over the original ground, looking down at the destruction (slow orbit and turn); the camera pulls out to 14.
- **The destruction is a disc** under it that never stops growing: 8 blocks at first, +0.4 a second, up to 56 (2 minutes). Trees, plants
  and builds go, the ground is eaten 3 layers deep at the edge to 10 under it.
- **Blocks fade to black** (2-2.5 s) and then dissolve in a quick dark puff.
- **It comes down only for players and strong enemies** (bosses, 40+ health or heavy armour) within 64, slowly, and goes back up after.
  Weak mobs are left to the destruction (void damage every second).
- **Never stuck:** it flies straight at what it wants; walls in the way and dead ends fade away in front of it (a faster fade), and it
  climbs out of its own crater before crossing to someone up on the ground.
- The black fog near it opens to 112 blocks (it was 40: the ground 50 below was lost in it).
- Everything comes back after control returns, as before (now 64 blocks a tick, in loaded chunks; unloaded ones wait).

## Moveset 2 for both forms + the takeover's pace (user, 2026-11-18) -- BUILT
User: "Sentry: Create a new moveset based on the sentries powers in the movies and comics and also create a new moveset for the void.
also for when the void takes control only make him attack once every 2 seconds with an ability or an M1"

**Sentry** (comics + Thunderbolts*: flying punches through mountains, light from his eyes, molecular disassembly, hurling foes into the
sky, a sonic-boom slam, a million exploding suns, healing light, the golden aura that turns attacks to light):
| Key | Move |
|---|---|
| R | **Golden Fury:** a three-punch flying combo; he lunges at anything up to 9 out; the 3rd, the Haymaker, launches it far (a wall hurts it more) |
| Hold Shift+R | **Solar Gaze:** twin beams of light from his eyes (beam clash), hotter after 2 s |
| Hold G | **Disassemble:** lift a creature in a golden field; it unravels and, held 2 s, comes apart into light; let go to throw it |
| Shift+G | **Skyward Hurl:** grab what's in reach and throw it straight up into the sky (bosses are slammed instead) |
| Z | **Sunfall:** a leap (from the ground), then a fist-first dive and a sonic-boom slam in two rings |
| Shift+Z | **A Million Suns:** a 4 s wind-up rising into the air (half damage taken), then the explosion of light |
| C | **Golden Guardian:** healing light, cleansing, Regeneration + Absorption for allies; the undead burn |
| Shift+C | **Molecular Shield:** a golden aura: projectiles turn to light (and Sun), less damage, melee deflected |
| X / 2x Space / V / Shift+V | Sonic Boom dash / flight / Calm / Let It Out (unchanged) |

**The Void** (comics + Thunderbolts*: tendrils, shadow imprints, the spreading shadow, people swallowed into darkness, mind horror):
| Key | Move |
|---|---|
| R | **Tendril Lash:** two whips, then the Impale (a spear of shadow that pins and drags) |
| Shift+R | **Erase:** seized, darkening, then wiped into a burnt shadow imprint |
| G | **Shadow Pool:** shadow spreads on the ground and holds down whoever stands in it |
| Shift+G | **Swallow:** a black void sphere around a creature (held, blind), then spat out; dying inside = absorbed |
| Z | **Nightmare:** a wave of horror -- figures of shadow, mobs flee, players see darkness |
| Shift+Z | **Shadow Eclipse:** he rises, a black sun with a gold corona drains everything under it while he regenerates; ends in a collapse |
| C | **Blot Out:** drains life and light (his own Sun too) to heal |
| Shift+C | **Shadow Clones:** three shadow doubles that hunt |
| X | **Shadow Step:** a blink through a smear of shadow (no light rule any more), hurting what it passes |

Merged buffs unchanged (+25% damage, Void moves x0.75 cooldowns, the Void rising faster).

**The takeover's pace:** the body attacks at most once every 2 s (40 ticks): one shared cooldown for every Void move and every fist
swing. The decay disc and its weak-mob damage are not attacks and are unchanged. It picks from the new Void moves by range and scene.
Numbers: `docs/SENTRY_VOID_REFERENCE.md`.

## Round 3 (user, 2026-11-18) -- BUILT; supersedes the key tables above
User: "the players arms and legs shake when starting to fly, beginning to move while flying or coming to a stop while flying. Make the bars
look like experience bars and not these square bars please. Make Solar Gaze his G move, remove the time limit on it, dont make it auto
track, nerf its damage to make it as powerful as supermans heat vision. Make let it out his H ability. Make shift+V his calm ability. Make V
his shield ability. Give sentry a landing slam animation thing like the other flight powers please. Make shift+G his disassemble ability.
Make shift+R an aoe ability please. if sonic boom is used while flying it does something different, makes player fly 50% faster for 30
seconds. move the other abilities around accordingly and make new moves if something is missing an ability please. If player enters the
controlled void mode then the red and black sentry suit should appear on the player even if the player isnt wearing the suit, the suit then
disappears after player returns to normal unless they were wearing the suit before ... Make the shadow clone models look better please,
make them look like really thin dark transparent player models with tiny white eyes. When the void takes over the player make the white
eyes a lot smaller, literal tiny glowing specs on the players face please. When the player is in controlled void state then they should be
doing 50% more damage. only make the players void bar increase when they use their powers, being out at night shouldnt do that. Remove the
players calm ability and replace it with a different ability, sleeping lowers void bar a lot and doing tasks like fishing and stuff should
also make it drain faster, void bar should drain slowly by default"

(The later "remove calm" wins over "shift+V = calm": Calm is gone, Shift+V is a new utility.)

| Key | Sentry | The Void (merged) |
|---|---|---|
| **H** | **Let It Out** (the trial; user-assigned H; Shift+H still opens the power wheel) | **Return** (below 90 Void) |
| R | Golden Fury | Tendril Lash |
| Shift+R | **Solar Flare** (new AoE: a burst of sunlight, undead x1.5) | Erase |
| G (hold) | **Solar Gaze** (Heat Vision's numbers, no time limit, an exact ray) | Shadow Pool |
| Shift+G | **Disassemble** (hold) | Swallow |
| Z / Shift+Z | Sunfall / A Million Suns | Nightmare / Shadow Eclipse |
| C | Golden Guardian | Blot Out |
| Shift+C | **Skyward Hurl** | Shadow Clones |
| X | Sonic Boom on the ground; **flying: Flight Boost** (+50% for 30 s) | Shadow Step |
| V | **Molecular Shield** | **Umbral Shroud** (new: the Void's shield -- hits -40%, shots swallowed, melee attackers withered) |
| Shift+V | **Golden Sense** (new utility replacing Calm: everything within 40 glows through walls, Night Vision) | **Shadow Meld** (new utility: invisible, fast, hunters lose you, until you attack) |
| 2x Space | Flight | Flight |

- **The Void meter** rises only from the powers (each move's gain, and being merged). It drains slowly by default, faster in sunlight, a lot in
  bed (a night slept through: -40), and calm everyday work drains it: fishing, harvesting ripe crops, trading, breeding, playing music.
- **Merged +50% damage** (was +25%); the faster Void cooldowns stay.
- **The look:** merged without the Sentry Suit, the red-and-black Void suit (his face and hair still show) and a black cape fade in over his skin
  and fade away on return; with the suit, the comics suit fades into the Void suit and back (as before). A visual only.
- **Shadow figures** (clones, nightmare figures): thin (60% width) translucent dark player models with tiny glowing eye specks.
- **The takeover's eyes:** two sub-pixel full-bright specks on the face.
- **Bars:** the vanilla XP bar sprites, tinted (Sun yellow, Void red darkening as it fills).
- **Landing:** fast flight touchdown = the shared superhero landing, a golden crater impact (dark merged) that hurts what is around him.
- **Flight shake:** the shared flight-feel limbs advanced their sway with `age x a pace-dependent frequency`; every change of pace jumped the
  phase (age x dPace). Phases are now integrated per tick (all fliers benefit).
