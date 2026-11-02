# Omnitrix revamp: Brainstorm

Status: **BUILT (v0.17.2).** User: "can you work on revamps for the rest of the alien force aliens please, look at their powers and
abilities in the show and do research and make them good please" and "delete all the alien models and only use the skins i provided
please no extra things untill i say". No Ultimate alien form ("dont add ultimate aliens yet").

Code: `src/main/java/com/projecthero/mod/omnitrix/alien/brainstorm/BrainstormKit.java`, client
`src/client/java/com/projecthero/mod/client/omnitrix/alien/BrainstormClient.java` + `brainstorm/BrainstormFx.java`, tests
`src/gametest/java/com/projecthero/mod/gametest/OmnitrixBrainstormGameTests.java` (8) and the updated `OmnitrixRecalCGameTests`.

## Research
ben10.fandom returned 402 to the fetcher, so the sources are fan wikis seen through search snippets (cnben10.fandom, 5yl.fandom,
aliens.fandom, ben10fanfiction) and memory. They agree on: a Cerebrocrustacean (crab-like, from Encephalonus IV) whose skull plates
open on a huge brain; **electrokinesis** from the exposed brain (bolts strong enough to one-shot a foe in "Lights Out");
**force fields** round himself and others; **electric levitation** of objects (moving things with an electric field); towering
**intellect**; pincers; slow on land. A fan wiki notes Echo Echo's sonic blasts giving him a headache. Episode citations beyond
"Lights Out" are not verified, so the rest is marked *(recurring)*.

Distinct from Grey Matter (tiny tinkerer genius): Brainstorm is a battle-mage -- a Static charge / Overload combo engine, a
force-field disc to ride, and a storm. The Spider-Man and Grey Matter kits are not reused.

## Kit (built)

| Key | Move | What it does | Numbers |
|---|---|---|---|
| R | Lightning Bolt | bolt from the top of his head at the crosshair | 7, range 20, cd 1 s |
| Shift+R | Chain Lightning | jumps foe to foe | 14 then 4 x 12 within 7, cd 8 s |
| G | Arc Pincer | shocking claw snap, cone 3.5 | 13 + 1 s stun, cd 5 s |
| Shift+G | Ball Lightning | slow orb; arcs at every foe within 3 each 0.5 s, then bursts | 4 per arc, burst 16 r 3.5, cd 8 s |
| X (tap) | Static Hop | charged leap, no fall damage | cd 2 s |
| Hold X | Field Lift | rides a force-field disc: hover, rise / sink with the look, glide | up to 3 s, cd 6 s |
| Shift+X | Bolt Dash | 8 blocks as a bolt | 6 to everything passed, cd 6 s |
| Z | Discharge | burst round him, thrown back; in water r 8 and shorts him for 3 | 17 r 5, cd 10 s |
| Shift+Z | **Electric Storm** (ULT) | storm cloud over the crosshair, strikes every foe under it each 0.5 s, then a finale strike | 4 x 12 + 22 = up to 70, r 8, 6 s, cd 45 s |
| C | Static Cage | lightning bars round a creature | 12 + 3 s stun, range 16, cd 12 s |
| Shift+C | Mega Bolt | 1 s charge, piercing beam | 19, range 24, cd 15 s |
| V | Genius Analysis | reads a creature: chat stats, glows 10 s | +25% from his moves, Overload at 2 charges, cd 15 s |
| Shift+V | Force Field | 6 s bubble: shots bounce, -60%, creatures shoved; squad allies / his pets inside get Resistance II | cd 25 s |
| Attack | Shocking Pincers | punch zaps | +2 and a Static charge |

Damage check: quick 4-7, normal 12-17, big 19, ultimate 70.

### Passives
- **Electrokinesis:** lightning never hurts him.
- **Static (new):** every electric hit (not the storm) adds a charge to the creature (6 s); the 3rd Overloads it: +6 on that hit and
  a 1 s stun (bosses a quarter), with a rendered shock ring.
- **Genius:** Analysis (+25%, Overload at 2).
- **Crab shell:** armour 6 (tough, so he keeps it), 22 HP, 0.9 scale.

### Weaknesses
- **Water:** half damage standing in water; Discharge spreads to 8 in water but shocks him for 3.
- **Slow shell:** 0.9x speed.
- **Sonic headache (new):** sonic damage (warden boom, an Echo Echo) x1.5 and it breaks the Mega Bolt charge and the Field Lift.

## Model
Removed every extra (head-shell halves, brain dome and glow, back shell, claw boxes, crab legs, first-person claws) and
`brainstorm_extras.png` + `scratchpad/recal_v0172/brainstorm/gen_brainstorm.js`. He is the player model only, in the user's 128x128
HD skin (normalised UVs draw it at full resolution), on the wide-arm model (slim players are redirected), Omnitrix badge kept.
Bolts now leave from the top of his head.

## Screenshots
`docs/screenshots/v0172/brainstorm/`: skin_front, chain_lightning, field_lift, electric_storm_ult.
