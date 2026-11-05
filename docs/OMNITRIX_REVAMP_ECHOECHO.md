# Omnitrix revamp: Echo Echo

Status: **BUILT (v0.17.2).** Code: `omnitrix/alien/echoecho/EchoEchoKit.java`, clone entity `EchoCloneEntity`, client
`client/omnitrix/alien/EchoEchoClient.java` + `echoecho/EchoCloneRenderer.java`. Tests: `OmnitrixEchoEchoGameTests` (8), plus the
older `OmnitrixRecalAGameTests` Echo Echo tests (unchanged, still pass).

The user asked: "can you work on revamps for the rest of the alien force aliens please, look at their powers and abilities in the
show and do research and make them good please". He also said "dont add ultimate aliens yet", so there is no Ultimate Echo Echo,
and "delete all the alien models and only use the skins i provided please no extra things untill i say".

Research note: ben10.fandom returned 402 and the respect threads were gone, so the sources were fan wikis and summaries. They agree
on the core powers: a Sonorosian whose **sonic screams** knock things back, overload machinery and stop projectiles; who **duplicates**
into many identical copies that share his mind; who makes a **sound force field**; who is small and agile; and whose body is
**fragile**. **Episode citations are from memory** and marked *(recurring)*. The water weakness and "hollow shell" are game logic,
not episodes.

## In the show
A small white Sonorosian in a speaker-like shell. His signature trick is **splitting into a crowd of himself**, then having every
copy scream at once *(recurring)*. The copies often surround a target and hit it from all sides *(recurring)*. His screams shatter
glass and crystal and fry Highbreed / DNAlien machinery *(recurring)*. A scream can also hold up a barrier that turns attacks
aside. He is light and easy to knock around.

## Kit (built)

| Key | Move | What it does | Numbers |
|---|---|---|---|
| R | Sonic Scream | sound wave along the look; each clone screams at the same spot | 7 + knockback, 14 blocks; clones +3; cd 1 s |
| Shift+R (hold) | Wall of Sound | held cone of sound, up to 2 s; pushes back; shots flying into it are stopped | 3 / 0.5 s (clones +1), 9 blocks / 55°; cd 6 s from release |
| G | Echo Ring | a ring of sound round him and round every clone | 12 in 5; clones 4 in 3; cd 6 s |
| Shift+G | Surround Sound | NEW. Every clone runs round the creature you look at, then screams inward 0.75 s later | 5 a clone, stun 1 s (+0.5 s a clone past the first), Resonating 6 s; range 16; cd 10 s |
| X | Sonic Leap | blast at the ground throws him up and forward; no fall damage 1.5 s | 4 in 2 at take-off; cd 1.5 s |
| Shift+X | Echo Swap | change places with the clone you look at (else the farthest) | cd 3 s |
| Z | Sonic Cannon | focused beam through everything in line; glass and ice in its path shatter (real blocks) | 16, 24 blocks; clones +3; cd 8 s |
| Shift+Z | Ultimate Chorus | ULT. Fills the chorus to 10, 1 s build-up, then a dome of sound plus a ring round every clone | 55 centre / 40 at 10 blocks, stun 2 s; clone rings 8 in 4; cd 60 s |
| C | Shatter Note | NEW. One pitched note at the creature you look at; golems and shulkers crack; glass and ice round it shatter | 15 (x1.5 iron / snow golem, shulker), Resonating 6 s, range 20; cd 7 s |
| Shift+C | Echolocation | NEW. A ping: every creature within 32 glows | glow 8 s; cd 20 s |
| V | Duplicate | split off a clone (up to 10, 6 HP each); it slides out of him to its own spot | cd 0.5 s |
| Shift+V | Merge | the clones rush back into him | +2 HP each; cd 2 s |
| Use (hold, empty hand) | Sound Barrier | moved from Z. Held shell, up to 3 s: half damage, creatures pushed off, shots turned back | cd 5 s after it drops |
| Attack | Chorus Jab | NEW. Clones near the target echo the punch | +1.5 per clone within 6 |

Damage check: quick 3-7 (scream, wall pulses, clone echoes), normal 12-16 (ring, cannon, note), ult 55/40.

### Passives
- **Chorus:** clones follow in formation, each on its own spot (user: "make the limit on his clones 10 and make them each have their own
  position, they shouldnt stand inside each other like how they currently do please"): ten places in a two-row fan behind him
  (`CloneSpacing`, shared with Ditto), an even ring round a Surround Sound target, and a push that keeps any two apart on the way.
  They copy his sound poses. Scream, Wall of Sound, Echo Ring, Cannon, the ultimate and his punches
  all come from every clone too.
- **Which One's Real? (NEW):** lethal damage while a clone is up breaks the nearest clone instead. He stands where it stood on 6 HP,
  and every mob hunting him loses track. Cooldown 90 s. `/kill` and the void still kill him. This runs as an early `ALLOW_DEATH`
  phase, the same pattern as Swampfire's Reassembly.
- **Resonating (NEW):** Surround Sound and Shatter Note mark a creature for 6 s. All his sound hits it 25% harder.
- Small and quick: x0.6 size, x1.15 speed, safe fall 3. Hidden Resistance I only, with **no armour of his own** (was 2).

### Weaknesses
- **Water:** +25% damage taken, and his sound does half.
- **Fragile:** 16 HP, no armour; a clone breaks after 6 damage.
- **Hollow shell (NEW):** explosions do +50%.

## Model
Removed: the ear discs, head crest and back speaker disc (`renderExtras`), the same ear discs on every clone, and
`echo_echo_extras.png`. Echo Echo and each clone are now the wide player model in his existing skin (the user gave no new skin) at
0.6 scale, with the Omnitrix badge. Move VFX: the client world pass keeps the sound-wave rings, cannon beam, barrier shell and
chorus dome. New moves draw QuirkFx shapes: the Wall of Sound beam cone, ribbons from the clones, shock rings, the echolocation ring,
and shards when a decoy breaks.

## Animations (v0.17.2 round 3)

Every move has its own third-person clip and first-person clip, and the sound moves shake and shove his whole body (`AlienBodyTurn`).
His scream is a breath in, chin out and arms thrown back as the sound drives him a step back. The Wall of Sound holds that pose for as
long as the key is down and eases out on release. The cannon is two hands cupped at the mouth with a recoil. Shatter Note is one hand at
the ear and one pointing it home. Echolocation is one hand at the mouth and one at the ear. Echo Ring balls up and flings wide. Sonic
Leap crouches over the blast. The chorus folds in shaking harder, then bursts. Clones slide out of him on a ring of sound with a small pop,
rush back in dwindling on a Merge, look in at a Surround Sound target and scream with his sound moves, but not his leap or swap.

Screenshots: `docs/screenshots/v0172/echo_echo/`, `docs/screenshots/v0172/round3_echo_echo/`.
