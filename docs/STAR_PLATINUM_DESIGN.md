# Stands: Star Platinum (design proposal, v0.17.2)

Status: APPROVED by the user 2026-11-01. Change from the proposal: the Stand is NOT visible, audible or perceivable to non-Stand users (only Stand users see it, hear its sounds and see its particles). Voice lines and SFX: the user will supply them after the build -- leave clean sound hooks.

Sources: jojowiki.com (Star Platinum, Star Platinum: The World, Stand), ASBR / Eyes of Heaven / Heritage for the
Future move lists, Roblox JoJo games (Project JoJo, Morioh High, A Universal Time), Roundabout mod wiki.

## Stand framework (reused by every future Stand)
- `com.projecthero.mod.stand`: a `StandType` registry, one `StandState` attachment (which Stand, summoned or not,
  Stamina, cooldowns, time-stop mastery, chosen pose), a `StandMove` record (same shape as the Omnitrix `AlienMove`),
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
| N | Pose | Strike your chosen pose (set in the menu). Holding the pose briefly gives Resistance I, a nod to "menacing". |
| Shift+N | Stand Menu | Stat card, pose picker, move list with descriptions, time-stop mastery progress, settings (ORA text, rush auto-face). |
| R | ORA Rush (hold) | A barrage in front: many fast hits on everything within reach, deflects arrows and projectiles. Releasing it (or Stamina running out) finishes with one ORAAA knockback punch. |
| Shift+R | Star Finger | The fingers shoot out about 8 blocks in a straight line, pierce through every mob in the line and cause bleeding. |
| G | Power Grab | Grab the mob, item or loose block in front of you, then press G again to hurl it. Damage scales with the block's hardness or the mob's size. |
| Shift+G | Ball Bearing Flick | Flick 3 bearings like bullets (the Diamond is Unbreakable feat): a precise, long-range shot. Uses iron nuggets, or is free on a longer cooldown if you have none. |
| X | Stand Leap | Star Platinum throws you into a huge leap, with up to 3 wall rebounds while airborne. No fall damage from it. |
| Shift+X | Stand Dash | A quick burst along the ground in the direction you're moving. The Stand catches the first hit that lands during it. |
| Z | Star Breaker (hold) | Charge up to 1.5 s: a huge single punch that breaks shields, launches the target and can smash weak blocks. |
| Shift+Z | ULTIMATE: Star Platinum: The World | Time stops. Everything else freezes (mobs, projectiles, other players, falling blocks). Damage you deal stacks up and lands all at once when time resumes. Duration grows with mastery (see below). |
| V | Stand Guard | Arms crossed in front of you: blocks 70% of frontal damage and catches arrows out of the air. Held while you hold V. |
| Shift+V | Precision Sight | Telescopic zoom with night vision and a glow on mobs. While zoomed, Star Platinum snatches incoming projectiles. |
| C | Super Inhale | For 2 s it inhales and pulls mobs, items and XP toward you. Clears lingering potion clouds. Mobs pulled in are left winded (Weakness). |
| Shift+C | ORAAA Uppercut | A launcher: one rising punch sends the target high into the air. It combos into Star Finger or a Leap chase. |

Key convention kept: X and Shift+X are movement, V and Shift+V are utility, everything else attacks.

## Time Stop details
- **Mastery (canon progression)**: it starts at 1 s and grows with each use: 2 s after 10 stops, 3 s after
  25, and the 5 s maximum after 50. Shown on the menu.
- **Cost and cooldown**: it needs at least 50 Stamina, empties the bar and has a 90 s cooldown. Afterwards you
  are winded for 3 s (Slowness), echoing his "can't stop it twice in a row".
- **Implementation**: built on the existing Time Slow engine, using the server's own tick freeze. The world is
  fully frozen and only you keep moving. Inside the stop you can't open chests or break blocks, so it is
  purely for fighting and moving. The screen desaturates, plays a chime and a clock tick, and color snaps
  back on resume.
- **Fairness**: damage queued against another player during the stop lands at most 30% of their max health.
  Bosses take reduced queued damage. Only one time stop can run at once.

## Sounds and text
The anime's voice clips are copyrighted, so I won't ship them. Instead: punch sounds layered fast, a heavy impact
for ORAAA, a bell and clock chime for the time stop, and floating "ORA ORA ORA" text over the rush. That text can
be turned off in the menu. A sound slot can be pointed at your own clip if you want.

## Guide, HUD and the rest
- HUD: a Stamina bar plus key boxes like the other heroes. A clock icon shows the time-stop cooldown and mastery.
- Guidebook: a new "Stands" chapter with a Star Platinum page, the Stand Arrow, and its I-key page.
- Wired into everything else: the Squad identity colour (purple), `/hero` grant and revoke, unique powers, and
  the state reset.
