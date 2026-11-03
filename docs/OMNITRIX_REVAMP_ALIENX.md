# Omnitrix revamp: Alien X -- the Celestial Council (v0.17.2)

Status: **BUILT** (v0.17.2, second rework). Replaces the 1.5 s random-vote debate.

User: "we need a new system for alien X by the way and he has to be a lot stronger and impervious to all forms of damage, it
should be difficult to use him aswell, also if you transform into alien X you watch battery stops depleting and you can basically
stay as him for as long as you like until you detransform"; "Alien X can one shot anything but the new system wont allow him to
just go and hit things"; "when the player transforms into alien x i want his body to remain in place, make alien x 2.5 blocks
tall by the way, but the player gets transported to that dimension where he talks to the two heads, make a good debating system
where the player can do many many many things, make it work well and be intuitive please".

Code: `omnitrix/alien/alienx/` -- `AlienXKit` (the kit), `AlienXCouncil` (sessions, the trip, the debate, validation, networking),
`AlienXActions` (the 24 actions), `AlienXBodyEntity` (the body left in the world), `AlienXCouncilPayload` /
`AlienXCouncilStatePayload`; client `AlienXClient`, `AlienXBodyRenderer`, `alienx/AlienXCouncilScreen`, `alienx/AlienXFx` (the
heads). Data: `dimension/celestial_council.json`, `dimension_type/celestial_council.json`, `worldgen/biome/celestial_council.json`.
Battery hook: `Omnitrix.HOLD_BATTERY`. Tests: `OmnitrixRecalAlienXGameTests`, `OmnitrixAlienXGameTests`.
Screenshots + video: `docs/screenshots/v0172/alien_x_system/`.

## Transforming

- His **body** (2.5 blocks tall, the user's Alien X skin and horns) stays standing where he transformed: frozen like the show,
  impervious, no knockback, never saved, gone the moment the session ends. When a motion is carried it lifts a hand.
- **Ben** is sent to **the Celestial Council** (`projecthero:celestial_council`): always night, a black sky full of stars, far
  nebulae, an invisible barrier floor (walls too, so nobody walks off) under a faint ring of light. Two enormous floating heads in
  his skin face him: **Serena** (soft blue, pale eyes, a halo) and **Bellicus** (red, burning eyes, hard angry brows). They drift,
  turn to look at him, and the one speaking nods, its eyes flare and rings of its colour ripple out.
- Nothing can be built, broken, used or hurt there. Each visit has its own slot (2000 blocks apart).
- **Coming back**: N, V or "Leave the Council" turns him back at his body (wherever it is now; inventory never left him). A
  switch to another alien also brings him back first. Where he came from is saved on him (`alien_x_council_return`), so a
  **logout**, a crash or a **stopped server** leaves him in the council only until his next login: the half-second sweep sends
  anyone found in the council without a session back to that spot (or to spawn if nothing was saved).

## Passives

- **Impervious**: no damage of any kind (mobs, players, fire, lava, falls, drowning, explosions, magic, wither, freezing...); /kill
  and the void still work (OmnitrixDamage lets them past every kit). No knockback (explosions too), harmful effects fall off at
  once, no fire, never out of breath.
- **Battery held**: on the Recalibrated Omnitrix the battery stops where it was (`HOLD_BATTERY`), no timeout; turning back stores
  exactly that.
- **No key powers, no punches**: R opens the council screen, V leaves. A punch does nothing ("the council must agree").

## The debate (the council screen, R)

1. **Propose**: a category (left), an action (middle; greyed + seconds while it rests), a target (right) from what is round his
   body within 48 blocks: "All round my body", "Where my body looks", "My body", and every creature / player with health and
   distance (only the kinds the action takes). The description says what it does and which argument tones backfire; the two
   meters preview where each head starts (how the action leans them).
2. **Debate**: both meters must reach **100** within **7 turns** (the patience pips). Each turn five of the eight tones are
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
   - Meters run -50..100. **Withdraw** (W) drops the motion at no cost.
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

Terrain changes (Barrier, Raise the Earth, Cage blocks, Open the Ground) are temporary and need the abilityTerrainDamage rule;
the opened ground is put back block for block (never over something placed since, never blocks with contents, bedrock or
fluids), and all at once if the server stops. World effects are QuirkFx (rings, blasts, ribbons, shards) and the vanilla-sample
sound cues; no particle spam.

## Notes

- The game-test server loads no datapack dimensions, so the trip tests use the Nether as a stand-in council
  (`AlienXCouncil.useTestCouncil`); in a real world the council dimension loads (checked in the client harness).
- Ultimate Alien forms stay out (user: "dont add ultimate aliens yet").
