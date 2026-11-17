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

## Sentry kit (H selects the power; X movement, V utility, rest attacks)
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

## Void Form (Let It Out won, or forced by the takeover)
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
