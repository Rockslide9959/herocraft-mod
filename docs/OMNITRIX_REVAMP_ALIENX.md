# Omnitrix revamp: Alien X -- the Celestial Council (v0.17.2)

Status: **BUILT** (v0.17.2, second rework). Replaces the 1.5 s random-vote debate.

User: "we need a new system for alien X by the way and he has to be a lot stronger and impervious to all forms of damage, it
should be difficult to use him aswell, also if you transform into alien X you watch battery stops depleting and you can basically
stay as him for as long as you like until you detransform"; "Alien X can one shot anything but the new system wont allow him to
just go and hit things"; "when the player transforms into alien x i want his body to remain in place, make alien x 2.5 blocks
tall by the way, but the player gets transported to that dimension where he talks to the two heads, make a good debating system
where the player can do many many many things, make it work well and be intuitive please".

Code: `omnitrix/alien/alienx/` -- `AlienXKit` (the kit), `AlienXCouncil` (sessions, the trip, the debate, validation, networking),
`AlienXActions` (the actions), `AlienXBodyEntity` (the body left in the world), `AlienXCouncilPayload` /
`AlienXCouncilStatePayload`; client `AlienXClient`, `AlienXBodyRenderer`, `alienx/AlienXCouncilScreen`, `alienx/AlienXFx` (the
heads), `alienx/AlienXHall` + `AlienXHallMesh` + `AlienXHallAmbience` (the mind palace); mixins `AlienXHallCameraMixin`, `AlienXHallHideMixin`,
`AlienXHallOutlineMixin`, `AlienXHallHiddenMixin`, `AlienXHallNoPickupMixin`, and one line each in the Hulk's `GuiHulkRampageMixin`,
`MouseHandlerHulkMindMixin` and `MinecraftHulkMindMusicMixin`.
Battery hook: `Omnitrix.HOLD_BATTERY`. Tests: `OmnitrixRecalAlienXGameTests`, `OmnitrixAlienXGameTests`.
Screenshots + video: `docs/screenshots/v0172/alien_x_system/`.

## Transforming: the hall (v0.17.2 round 4)

User: "can you change the alien x council thing to be the mind palace thing that the hulk has, i also want a screen where the player
can see a third person view of themselves and even turn and look around if they want to please".

- His **body** (2.5 blocks tall, the user's Alien X skin and horns) stays standing where he transformed: frozen like the show,
  impervious, no knockback, never saved, gone the moment the session ends. When a motion is carried it lifts a hand.
- **He stays in the world**, held inside that body, because a client can only draw the level its player stands in and the hall's
  screen shows that level. **The hall is an illusion drawn over his screen** (`AlienXHall`, the method of `HulkMindScreen`): the world
  frame is rendered from a third-person camera on an orbit round the body, copied to a texture, and the hall is drawn over the window
  with that texture on its giant screen.
- **The hall**: a star-filled void (stars, a band of the galaxy, nebulae) with a causeway and a round dais of dark glass, a low curtain
  of light along every edge, standing slabs round the rim. **Serena** and **Bellicus** hang either side of the screen; they drift,
  turn to watch him, and the one speaking nods, its eyes flare and rings ripple out. He arrives at the far end of the causeway, 57
  blocks from the screen, and walks with WASD, the mouse and the sprint key at vanilla speeds, with vanilla's bobbing and footsteps.
- **Talking to the heads**: inside the ring of light under either head (pale blue for Serena, red for Bellicus), a right-click or **R**
  opens the council screen. R elsewhere says to walk closer. **V, N and the alien wheel work anywhere** in the hall: they move Let Me
  Go and the screen opens where he stands, so nobody has to walk anywhere to ask to leave. The screen itself is unchanged.
- **Looking round his body**: at the point of light in front of the screen a right-click takes the view. The hall camera rises to face
  the screen, which nearly fills the window; the mouse then swings the camera round the body (any direction, kept out of walls by a
  block ray), the scroll wheel zooms (3 to 22 blocks), a left-click resets it behind him, and a right-click, S or Sneak steps back. The
  view stays where he left it.
- **Everything else** is swallowed, as in the Hulk's hall (chat, the player list, screenshots, the I screen and the power wheel still
  work), the HUD is put away, and the music is the End's over a low hum and far chimes.
- **Coming back**: v0.17.2 (user: "when detransforming as alien x make it so that the player has to convince the heads to let them
  detransform aswell please"): N, V, the alien wheel and "Leave the Council" move the **Let Me Go** motion (category Leave, target his
  body, leans 30 / 35, no cooldown); only when both heads agree does he turn back, 1.5 s after the banner
  (AlienXCouncil.requestRelease / RELEASE_DELAY). The camera flies into the screen, as the Hulk's does, and he is standing where
  the body stood (the inventory never left him). With no body or no session he turns back at once. Death, logout, the watch coming
  off and /kill still end it outright. A switch to another alien brings him back first.

### What the old dimension gave for free, and where it is now

| Used to be free | Now |
|---|---|
| Others could not see or touch him | `AlienXKit.inHall` (synced Omnitrix state): no client draws him (`AlienXHallHideMixin`, himself included: only the body is on screen), no hitbox, no pushing, mobs lose him (`AlienXHallHiddenMixin`), hero targeting skips him (`HeroTargets.canHarm`), the motions' target lists and areas skip him |
| He could not touch the world | Keys let go on his client; on the server every use, attack and break is refused while in session; no item or XP pickup (`AlienXHallNoPickupMixin`) |
| Safe from anything | The kit's existing imperviousness, unchanged; /kill and the void still get through |
| Could not wander off | `AlienXCouncil.hold`, every tick: he is put back at the body (it may settle onto the ground, or Move My Body may carry it), and portals are held off |
| Body's chunk held by a ticket | He stands there, so his own chunks hold it. The ticket is gone |
| A lost body | `hold` puts a new one where he stands. The body hangs rather than falling out of the world |
| Logout | Session and body end, the Omnitrix turns him back, and he is saved where he stands |
| Crash or stopped server | He is saved as Alien X with no session: the kit opens a new one at once, with a new body |
| Death | The normal revert ends the session; the client drops the hall with no flight |
| GPU | The captured frame is freed as soon as the scene ends and on leaving the world |

**The dimension's data stays**: `dimension/`, `dimension_type/` and `worldgen/biome/celestial_council.json`, `AlienXCouncil.DIMENSION`,
the saved return (`alien_x_council_return`) and the half-second sweep (`AlienXCouncil.check`). Nothing sends anyone there any more, but
a player saved inside it by an older version must be sent back to his saved spot (or spawn, if nothing was saved), and vanilla only
drops an unknown dimension's player into the overworld at the same coordinates. These can go in a later release. Slots, the
barrier floor and the world-render pass are gone.

## Passives

- **Impervious**: no damage of any kind (mobs, players, fire, lava, falls, drowning, explosions, magic, wither, freezing...); /kill
  and the void still work (OmnitrixDamage lets them past every kit). No knockback (explosions too), harmful effects fall off at
  once, no fire, never out of breath.
- **Battery held**: on the Recalibrated Omnitrix the battery stops where it was (`HOLD_BATTERY`), no timeout; turning back stores
  exactly that.
- **No key powers, no punches**: R at the heads opens the council screen, V asks to leave. A punch does nothing ("the council must agree").

## The debate (the council screen, at the heads)

1. **Propose**: a category (left), an action (middle; greyed + seconds while it rests), a target (right) from what is round his
   body within 48 blocks: "All round my body", "Where my body looks", "My body", and every creature / player with health and
   distance (only the kinds the action takes). The description says what it does and which argument tones backfire; the two
   meters preview where each head starts (how the action leans them).
2. **Debate**: both meters must reach **85** (v0.17.2, user: "maybe make it like 15% easier please"; was 100) within **7 turns** (the patience pips). Each turn five of the eight tones are
   offered (keys 1-5); each row shows how each head feels about the tone (▲▲ / ▲ / – / ▼ / ▼▼), and hovering shows Ben's actual
   words.

   | Tone | Bellicus | Serena |
   |---|---|---|
   | Force | +35 | -20 |
   | Glory | +30 | -15 |
   | Resolve | +25 | -5 |
   | Protection | +15 | +25 |
   | Mercy | -20 | +35 |
   | Balance | -5 | +30 |
   | Wisdom | +5 | +20 |
   | Humility | -15 | +20 |

   - **Contradicting the motion** (a tone the action lists, e.g. mercy for Unmake): -15 to both, marked ✖.
   - **Flip-flopping** (force after mercy, glory after humility, either way): -10 to both on top.
   - **Repeating** a tone: half its gain each time (penalties stay whole).
   - Each head has a hidden **concern** (one of the tones it likes, never one the motion contradicts) worth **double**.
     **Listen** (L) spends a turn and both say what it is (★ on the rows after).
   - Meters run -50..85. **Withdraw** (W) drops the motion at no cost.
3. **Carried**: the action happens at his body, the result is read out ("Husk is unmade."); that action rests its cooldown.
   **Refused** (out of patience): that whole category rests 20 s.

The server checks everything: the action exists, the target is the right kind, alive, in the same world and within 48 of the
body, nothing is resting, the argument is on offer.

## The 24 actions

| Category | Action | Targets | Leans (B / S) | Backfires | What it does | Rests |
|---|---|---|---|---|---|---|
| Strike | **Unmake** | creature, player | 50 / 0 | mercy, humility | kills outright, any health, armour or boss (hurt for max(1000, 10x max health), then `kill()`) | 30 s |
| Strike | Starfall | point, creature, player | 45 / 10 | mercy | 60 to everything within 6 | 15 s |
| Strike | Disarm | creature, player | 30 / 40 | | held weapons drop, Weakness III 30 s | 10 s |
| Time | Stop Time | area | 40 / 40 | | everything within 16 stunned 10 s (bosses 2.5 s), projectiles hang | 30 s |
| Time | Slow Time | area | 40 / 45 | | hostiles within 24: Slowness III + Mining Fatigue III 20 s | 20 s |
| Protect | Shield | player, body | 25 / 55 | force | Resistance IV + 16 absorption 30 s | 20 s |
| Protect | Barrier | area | 30 / 55 | | glass dome r 5 round the body, 20 s | 20 s |
| Protect | Calm | area | 10 / 60 | force, glory | creatures within 24 (not bosses) drop their target, slowed + weakened 10 s | 20 s |
| Heal | Restore | player | 20 / 65 | force | full health and hunger, effects and fire gone | 15 s |
| Heal | Mend All | area | 15 / 60 | force | players within 16: +20, effects and fire gone | 30 s |
| Reshape | Raise the Earth | point, creature, player | 40 / 35 | | stone wall 7 x 4 for 20 s | 15 s |
| Reshape | Open the Ground | point, creature, player | 50 / 15 | mercy | 3 x 3 pit, 5 deep, for 10 s, then put back | 15 s |
| Create | Cage | creature, player | 40 / 40 | | black-glass cage 15 s, held still | 15 s |
| Banish | Banish | creature, player | 40 / 20 | mercy | sent 600-1000 blocks away onto solid ground | 30 s |
| Banish | Exile | area | 35 / 30 | | hostiles within 16 (not bosses) thrown 48-80 blocks | 30 s |
| Sky | Clear Skies / Summon Storm | area | 35/45, 55/20 | | weather (a world with a sky) | 30 s |
| Sky | Bring the Day / Night | area | 35/45, 45/30 | | the time of day | 60 s |
| Gravity | Lift | creature, player, area | 45 / 25 | | Levitation IV 3 s (area: hostiles within 12) | 15 s |
| Gravity | Crush | area | 55 / 5 | mercy, humility | 50 to everything within 10, slammed down, Slowness IV 5 s | 20 s |
| Travel | Move My Body | point, creature, player | 40 / 40 | | the body steps to where it looks / beside the target | 10 s |
| Travel | Summon | player | 30 / 50 | | brings the player to the body | 20 s |
| Summon | Guardian | area | 45 / 40 | | an iron golem beside the body for 60 s | 60 s |
| Leave | **Let Me Go** | body | 30 / 35 | | he turns back and returns to his body (N, V and the wheel move it) | none |

Terrain changes (Barrier, Raise the Earth, Cage blocks, Open the Ground) are temporary and need the abilityTerrainDamage rule;
the opened ground is put back block for block (never over something placed since, never blocks with contents, bedrock or
fluids), and all at once if the server stops. World effects are QuirkFx (rings, blasts, ribbons, shards) and the vanilla-sample
sound cues; no particle spam.

## Notes

- The game-test server loads no datapack dimensions, so the trip tests use the Nether as a stand-in council
  (`AlienXCouncil.useTestCouncil`); in a real world the council dimension loads (checked in the client harness).
- Ultimate Alien forms stay out (user: "dont add ultimate aliens yet").
