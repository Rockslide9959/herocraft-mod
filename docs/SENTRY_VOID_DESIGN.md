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
| X | **Flight** (toggle), double-tap X for a sonic-boom dash |
| V | **Calm:** hold to meditate, Void drains, you can't attack meanwhile |
| Shift+V | **Let it out:** at 50+ Void, choose to become the Void yourself (Void Form) — stronger, but Void climbs fast |

## Void Form (Shift+V, or forced by the takeover)
Black-and-gold-veined body, hair and eyes black, darkness smoking off you.
| Key | Move |
|---|---|
| R | **Shadow Lash:** tendrils whip everything in front of you |
| Shift+R | **Erase:** a creature is wiped into a burnt shadow on the ground (huge damage; kills leave the shadow) |
| G | **Grasp:** tendrils drag several creatures to you |
| Z | **Void Rift:** a black hole that pulls and crushes for 4 s |
| Shift+Z | **Nightfall:** a dome of darkness (Darkness on enemies, you regenerate inside) |
| X | **Shadow Step:** teleport to any dark spot you look at |
| V | **Return:** try to go back to Sentry (only works below 90 Void) |

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
4. **Shift+V Void Form exists** (at 50+ Void).
