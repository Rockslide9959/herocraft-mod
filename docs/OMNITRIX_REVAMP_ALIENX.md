# Omnitrix revamp: Alien X (v0.17.2)

Status: **BUILT** (v0.17.2). Code: `omnitrix/alien/alienx/AlienXKit.java`, `client/omnitrix/alien/AlienXClient.java`,
`client/omnitrix/alien/alienx/AlienXFx.java`. Tests: `OmnitrixAlienXGameTests` (+ the basic ones in `OmnitrixRecalAlienXGameTests`).

User: "can you work on revamps for the rest of the alien force aliens please, look at their powers and abilities in the show and
do research and make them good please" / "delete all the alien models and only use the skins i provided please no extra things
untill i say". No Ultimate forms.

## In the show

A Celestialsapien: reality warping on a cosmic scale (stopping and rewinding time, flight through space, creating and unmaking
matter). Every Celestialsapien is a council of personalities. In Alien X, Ben is the third voice beside **Serena** (love,
compassion) and **Bellicus** (rage, aggression). In his debut (*X = Ben + 2*) he stands frozen while they argue. The rule settled
there: **every action is a motion, and a majority carries it**. Ben's yes plus one of theirs is enough, and Ben can plead his case
to win one over. Sources disagree on whether the vote must be unanimous or a majority. The kit uses the majority rule and makes
**unanimity** the bonus, and the gate for the ultimate. *(Fan wikis / VS Battles summaries; ben10.fandom was not reachable.)*

## The council (revamped)

- Every move is a motion: **1.5 s frozen debate**, then the vote. Ben always says yes.
- **Real votes:** each voice votes when the debate opens, and its line on the action bar says which way (yes / no line sets).
  The HUD debate row shows "aye" / "nay" next to each voice once it has spoken.
- **Motion kinds:** [S] Serena's (Time Stop, Restore, Rewind), [B] Bellicus's (Reality Bolt, Cosmic Ray, Gravity Crush, Cosmic
  Shockwave), [-] neither (Hurl, Blink, Flight, Rift). A voice says yes to its own kind **70%**, to neither **45%**, to the other's
  **20%**.
- **Council Favour** (0-100 per voice, HUD rows): a carried motion of a voice's kind gives it +12 and the other -8 (neither's kind:
  +3 both). Favour moves the yes chance by up to +/-20%. It drifts back to 50 by 1 every 2 s and resets when he turns back.
- **Argue** (right-click, empty hand, during a debate, once): every voice that said no votes again with +25%. A swayed voice says
  so.
- **Unanimous** (both yes): the move is empowered **+25%** (damage, Time Stop length, Restore healing, Rift damage).
- **Denied** (both no): 3 s on that move.
- The 5 s opening debate on transforming stays as before.

## Kit

| Key | Move | Kind | What it does | Numbers |
|---|---|---|---|---|
| R | Reality Bolt | B | bolt of starlight down the look | 17, 32 blocks; cd 2.5 s |
| Shift+R | Cosmic Ray | B | wide beam | 20 to all within 1.6 of a 40-block line; cd 10 s |
| G | Telekinetic Hurl | - | lift the look target and fling it | 16, range 24; cd 6 s |
| Shift+G | Gravity Crush | B | gravity multiplied round him | 18 in r 6, slammed, slowed 3 s; cd 12 s |
| X | Blink | - | teleport where he looks | 48 blocks; cd 3 s |
| Shift+X | Cosmic Flight | - | flight toggle (landing needs no vote) | cd 1 s |
| Z | Time Stop | S | freezes the moment | r 12, 5 s (bosses 1.25 s), projectiles stop; cd 25 s |
| Shift+Z | **Big Bang** (ULT) | unanimous only | a universe's birth in miniature | 70 centre to 50 rim, r 10, flung; cd 120 s; refused = 10 s |
| C | Cosmic Shockwave | B | ring of starlight | 19 in r 8, thrown; cd 14 s |
| Shift+C | Reality Rift | - | starfield tear 24 away that drags creatures in | r 6, 3 s, 3 / 0.5 s (18); cd 15 s |
| V | Restore | S | rewrites his body; squad allies within 8 too | +24 (allies +12), harmful effects and fire gone; cd 30 s |
| Shift+V | Rewind | S | back to where (and as healthy as) he was 5 s ago | cd 20 s |
| Use (during a debate) | Argue | - | see above | once per debate |

Big Bang's chances: Bellicus 80%, Serena 25% (both +/-20% favour, +25% after arguing). Please Serena first (Restore / Time Stop),
then argue.

### Passives
- He survives the heart of a star: no fire, freezing, drowning or fall damage.
- 60 health, armour 14, scale 1.15 (unchanged). Hidden Resistance I.

### Weaknesses
- Frozen during every debate (no walking, jumping or punching).
- Denied motions; Big Bang needs both voices.
- The 5 s opening debate.

## Look

Only the player model (1.15x) in the user's Alien X skin (`textures/entity/omnitrix/alien_x.png`, converted from the 8-bit
palette PNG to RGBA). **Removed:** the two white horn models, the additive star-shimmer pass over the body
(`alien_x_stars.png`, deleted), the glowing-eye pass (`alien_x_eyes.png`, deleted), the drifting END_ROD particles. Kept: every
move effect in `AlienXFx` (beams, rings, the time-stop clock face, rifts, Big Bang sphere). The extras sheet is now only the
rifts' starfield texture. QuirkCamera shakes on the Shockwave and Big Bang. New pose: Ben pleading (Argue).
