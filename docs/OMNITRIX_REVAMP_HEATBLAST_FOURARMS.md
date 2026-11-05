# Heatblast and Four Arms, round 3 (v0.17.2)

The older tables in `OMNITRIX_FRAMEWORK_0171.md` and `OMNITRIX_DAMAGE_PASS_PROTOTYPE.md` are history; this page is what these two kits do now.

## Heatblast

| Key | Move | Now |
|---|---|---|
| R | Fireball Volley | 8 ticks gathering fire in both hands, then 3 throws 5 ticks apart: 10 each, 3 s alight, 2 s cooldown |
| Shift+R | Fire Breath (was Flamethrower) | Held. A 4-tick breath in, then fire from his mouth: 7 blocks, 8 every half second, lights the ground. Slowness II while held, no time limit, no cooldown while it runs; 6 s from the release |
| G | Magma Bomb | 1 s charge of a ball over his head, then thrown: 25 flat within 3.5 blocks (no separate direct hit), then the 4 s burning patch (7 a half second). 11 s cooldown from the throw |
| Shift+G | Magma Slam (was Blaze Cone) | 12 ticks gathering in raised fists, then both into the ground in front of him: 25 within 10 blocks all round him, alight 5 s, shoved; fire runs out in 5 rings to 10 blocks. 7 s cooldown |
| Z | Fire Tornado | Unchanged, pose included |
| Shift+Z | Supernova | 4 s charge, then 100 flat within 9 blocks and the 7.5-block fire ring. 30 s cooldown from the blast |

Everything else on his keys keeps its numbers. While he gathers, charges or breathes, his other keys, fists and fire bolt wait (`HeatblastKit.busy`). Water ends all of them; a doused Fire Breath still starts its cooldown.

### The Supernova's look (`HeatblastClient`)

- `novaFlames`: flames on the body, rooted on 30 points of the posed limbs. Few, short and soft at the start; more, longer and harder-kinked as the charge climbs (`jagged` is a zig-zag spine with barbed edges, in two crossed strips).
- `novaWorld`, seen by everyone and in first person too: the ground glow, a crown of jagged flames round his feet, and comets of fire spiralling in to his chest.
- The last 14% crushes it all down into a white-hot core (`crush`), with the inhale sound and a negative FOV punch.
- `novaBurst`: a shell of fire racing out to the blast's edge with jagged spears through it. It plays on the Supernova only: the Meteor Crash's landing has its own anim id now (`ANIM_CRASH`) and keeps just the star pose.

## Four Arms

R, the Four-Fist Combo: still 4 presses within 1.5 s of each other. Upper right 12, upper left 12, both lower fists together 12, then all four fists 18. The last blow lands 5 ticks after the press, as the fists come down. 3 s cooldown after it. If the combo lapses early there is no cooldown, as before.

Boulder Rip now keeps the boulder in his hands for 8 ticks (`boulderHeld`, drawn in `FourArmsClient`) and throws it as the arms come over. It used to fly at the press, before the arms had moved.

### Animations

Every move is two tables that run together: `upperClip` (the limbs) and `lowerClip` (the lower pair, absolute angles). The lower pair used to step between two poses. Arm-only moves leave the legs on the walk cycle while he moves. Punches go down the aim with the hips turned into them. The Haymaker has its own lunge and hit. The Giant Swing turns his whole body with the victim (the client sets the body yaw to the angle the server whirls it through).
