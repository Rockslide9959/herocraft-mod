# Stands: Star Platinum (design proposal, v0.17.2)

Status: APPROVED by the user 2026-11-01; BUILT on branch claude/star-platinum-2cd2ef (v0.17.2). Change from the proposal: the Stand is NOT visible, audible or perceivable to non-Stand users (only Stand users see it, hear its sounds and see its particles). Voice lines and SFX: the user will supply them after the build -- leave clean sound hooks.

Sources: jojowiki.com (Star Platinum, Star Platinum: The World, Stand), ASBR / Eyes of Heaven / Heritage for the
Future move lists, Roblox JoJo games (Project JoJo, Morioh High, A Universal Time), Roundabout mod wiki.

## Stand framework (reused by every future Stand)
- `com.projecthero.mod.stand`: a `StandType` registry, one `StandState` attachment (which Stand, summoned or not,
  Stamina, cooldowns, chosen pose), a `StandMove` record (same shape as the Omnitrix `AlienMove`),
  one Stand ability manager, HUD, menu and renderer. Star Platinum is the first `StandType`.
- One Hero-Tier key `stand` (a Primary power, so it follows the one-Primary rule).
- **Stand Arrow** item: found in desert pyramids, trail ruins and ancient cities. Right-click to stab yourself
  (4 hearts damage) and awaken a Stand from the pool (only Star Platinum for now). Never from random serums.
- Body: a player-shaped model wearing the user's NovaSkin Star Platinum skin, about 1.15x size, a faint purple
  outline glow, floating behind the user's right shoulder. Rush draws extra blurred arm copies.

## Stats (canon) and the Stamina bar
Power A, Speed A, Range C, Stamina A, Precision A, Potential A, shown on the menu's stat card.
- **Range C**: Star Platinum only reaches about 3 blocks from you. Star Finger and thrown objects cover longer range.
- **Stamina bar (100)**: the rush drains it while held, other moves cost a chunk, and it refills quickly while
  the Stand is out and not fighting.

## Keys
| Key | Move | What it does |
|---|---|---|
| H | Summon / Dismiss | Star Platinum appears behind you with a menacing ゴゴゴ burst. While out, empty-hand punches become Stand punches (3-hit chain, +reach, the 3rd hit knocks back). |
| N | Pose | The one pose for now (user, v0.17.2, `docs/reference/jotaro_pose/`): Jotaro's point, side-on, right arm thrust out, left hand on the hip, coat flaring, the Stand looming over the shoulder with a fist raised. Holding it briefly gives Resistance I. |
| Shift+N | Stand Menu | Stat card, pose picker, move list with descriptions, settings (ORA text, rush auto-face). |
| R | ORA Rush (hold) | A barrage in front: many fast hits on everything within reach, deflects arrows and projectiles. Releasing it (or Stamina running out) finishes with one ORAAA knockback punch. |
| Shift+R | Star Finger | The fingers shoot out about 8 blocks in a straight line, pierce through every mob in the line and cause bleeding. |
| G | Power Grab | Grab the mob, item or loose block in front of you, then press G again to hurl it. Damage scales with the block's hardness or the mob's size. |
| Shift+G | Ball Bearing Flick | Flick 3 bearings like bullets (the Diamond is Unbreakable feat): a precise, long-range shot. Uses iron nuggets, or is free on a longer cooldown if you have none. |
| X | Stand Leap | Star Platinum throws you into a huge leap, with up to 3 wall rebounds while airborne. No fall damage from it. |
| Shift+X | Stand Dash | A quick burst along the ground in the direction you're moving. The Stand catches the first hit that lands during it. |
| Z | Star Breaker (hold) | Charge up to 1.5 s: a huge single punch that breaks shields, launches the target and can smash weak blocks. |
| Shift+Z | ULTIMATE: Star Platinum: The World | Time stops. Everything else freezes (mobs, projectiles, other players, falling blocks). Damage you deal stacks up and lands all at once when time resumes. Lasts 10 s. |
| V | Stand Guard | Arms crossed in front of you: blocks 70% of frontal damage and catches arrows out of the air. Held while you hold V. |
| Shift+V | Precision Sight | Telescopic zoom with night vision and a glow on mobs. While zoomed, Star Platinum snatches incoming projectiles. |
| C | Super Inhale | For 2 s it inhales and pulls mobs, items and XP toward you. Clears lingering potion clouds. Mobs pulled in are left winded (Weakness). |
| Shift+C | ORAAA Uppercut | A launcher: one rising punch sends the target high into the air. It combos into Star Finger or a Leap chase. |

Key convention kept: X and Shift+X are movement, V and Shift+V are utility, everything else attacks.

## Time Stop details
- **Length (user, v0.17.2)**: no mastery, everything is maxed out: every stop lasts 10 s.
- **Cost and cooldown**: it needs 20 Stamina, paid as time resumes (moves are free inside it), and has a 90 s cooldown. Afterwards you
  are winded for 3 s (Slowness), echoing his "can't stop it twice in a row".
- **Implementation (as built)**: NOT vanilla's `/tick freeze` (that stops the game clock, which every cooldown and
  animation in the mod runs on, and leaves players free). A custom freeze (`stand.TimeStop`): every entity in the
  caster's dimension except the caster and its mount skips its server tick, and block entities hold still; the
  game clock keeps running. Frozen players' keys, attacks, item use and block breaking are refused. Inside the
  stop you can't open chests or break blocks, so it is purely for fighting and moving. Something you throw or
  shoot flies 3 ticks, then hangs in the air and flies on when time resumes.
- **Who sees what**: a Stand user's client freezes everything but the caster, freezes particles and drains the
  colour (`shaders/post/stand_time_stop.json`, saturation 0.15 with a cool tint); a non-Stand user's client
  freezes EVERYTHING, the caster too, with no cue at all -- they just see the world jump when it resumes.
- **Fairness**: damage queued against another player during the stop lands at most 30% of their max health.
  Bosses take reduced queued damage. Only one time stop can run at once.

## Sounds and text
The anime's voice clips are copyrighted, so I won't ship them. Instead: punch sounds layered fast, a heavy impact
for ORAAA, a bell and clock chime for the time stop, and floating "ORA ORA ORA" text over the rush. That text can
be turned off in the menu. A sound slot can be pointed at your own clip if you want.

### Sound slots (as built) -- drop your clips in here
Every Stand sound is its own named event, `projecthero:stand.star_platinum.<name>`, heard only by Stand users. For
now `sounds.json` points each one at a vanilla placeholder (an `"type": "event"` entry). To use your own clip:
1. put it at `src/main/resources/assets/projecthero/sounds/stand/star_platinum/<name>.ogg` (mono .ogg for a
   positional sound);
2. in `assets/projecthero/sounds.json`, change that event's sound from
   `{ "name": "minecraft:<placeholder>", "type": "event" }` to `{ "name": "projecthero:stand/star_platinum/<name>" }`.

| `<name>` | When it plays | Placeholder now |
|---|---|---|
| `summon` | H: Star Platinum appears | **the user's clip** (`stand/star_platinum/summon.ogg`) |
| `menacing` | with the summon (the ゴゴゴ) | warden heartbeat |
| `dismiss` | H: it fades away | **the summon clip reversed**, lower and quicker (`stand/star_platinum/dismiss.ogg`) |
| `awaken` | the Stand Arrow awakens your Stand | beacon power select |
| `punch` | each Stand punch / rush hits (every few ticks) | strong attack |
| `ora_rush` | the ORA ORA ORA rush voice (start, then every 0.6 s) | sweep attack |
| `ora_finisher` | the ORAAA finisher / the 3rd chain punch | knockback attack |
| `star_finger` | Star Finger | trident throw |
| `grab` / `throw` | Power Grab / the hurl | leather equip / pearl throw |
| `bearing_flick` | Ball Bearing Flick | crossbow shot |
| `leap` / `dash` | Stand Leap (and rebounds) / Stand Dash | goat long jump / sweep |
| `breaker_charge` / `star_breaker` | Star Breaker wind-up / the punch | beacon ambient / mace heavy smash |
| `time_stop` | "Star Platinum: The World!" -- time stops | the user's "Za Warudo" clip (v0.17.2) |
| `clock_tick` | each second of stopped time | synthesised ringing clock tick, tick/tock pitches (v0.17.2) |
| `time_resume` | "...and time resumes" | the user's time-resume clip (v0.17.2) |
| `guard` / `catch` | Stand Guard up (and blocking) / a caught projectile or hit | shield block / item pickup |
| `precision_sight` | Precision Sight on | spyglass |
| `inhale` | Super Inhale | breeze inhale |
| `uppercut` | ORAAA Uppercut | knockback attack |
| `yare_yare` | the "Yare yare daze" pose (N) | amethyst chime |

## Guide, HUD and the rest
- HUD (user, v0.17.2): the Minecraft-style HUD the Omnitrix has (`VanillaHud`): a purple boss-bar Stamina row over a short hotbar of the six keys. No clock.
- Stamina (user, v0.17.2): every move costs about 40% of the first tuning.
- Animation (user, v0.17.2): `client/stand/StandPoses` -- real elbows and knees (`JointedLimbs`), a keyframed clip per move, cross-faded every frame. Star Finger is a stretching two-finger mesh (`StandFinger`) that pokes the point the server worked out; bearings are flying steel balls (`StandFx`).
- Guidebook: a new "Stands" chapter with a Star Platinum page, the Stand Arrow, and its I-key page.
- Wired into everything else: the Squad identity colour (purple), `/hero` grant and revoke, unique powers, and
  the state reset.
