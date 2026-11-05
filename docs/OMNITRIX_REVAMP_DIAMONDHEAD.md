# Diamondhead (v0.17.2 round 3)

Code: `omnitrix/alien/diamondhead/DiamondheadKit.java`, client `client/omnitrix/alien/DiamondheadClient.java`.

## Keys

| Key | Move | Numbers |
| --- | --- | --- |
| Hold R | Shard Volley | clumps of 1-4 shards (6 each), 5-9 ticks apart, 1.5 s cooldown on release |
| Shift+R | Crystal Spear | 22, knockback, 6 s; thrown 5 ticks after the press |
| G | Spike Line | 30, launch, 8 s |
| Shift+G | Crystal Pillar | 28, launch, 7 s, 22 blocks |
| X | Crystal Slide | toggle |
| Shift+X | Crystal Vault | |
| Z | Crystal Eruption | 24 out to 6 blocks, 9 s |
| Shift+Z | Titan Shards | 70 + 16 splash, 30 s |
| V | Combat Mode | toggle |
| Shift+V | Crystal Construct | wall / dome / bridge |
| C | Crystal Maul | 20 in a frontal arc out to 4.5 blocks, staggered 2 s, 10 s |
| Shift+C | Prism Spire | stands 12 s, reach 7 blocks, 30 s cooldown |

Combat Mode: R Blade Stab (22, 4 s), Shift+R Crystal Cyclone (24), held right-click the Crystal Shield (40 HP).

## Decisions

- **Shard Volley** goes through `press` / `release`, not the framework's `holdable`, so the first clump leaves on the key press instead of 8 ticks later. `F_VOLLEY` is synced; the client pose is procedural (`volleyPose`) so the arms never drop between clumps, and both arms follow the look. Each shard is aimed at the same point; the bunch starts within 0.2 blocks of its middle and closes up in flight, so it never fans.
- **The hand at nothing** had two causes: the client's `onUseAir` swung the hand on every repeat, and `shieldUse` answered `SUCCESS`, which swings on a block or creature. `AlienClientKit.swingsOnUseAir` and `InteractionResult.CONSUME` end both.
- **Shield**: drawn for as long as Combat Mode is on, strapped along the outside of the left forearm (`shieldRest`), and swung round in front by a rigid blend (`blend`, lerped place, slerped turn) driven by `shieldRaise`. In first person the left arm comes up behind it through `AlienClientKit.poseFirstPersonOffHand`.
- **Crystal Maul** (C, combat): in the show his hands reshape into blades and hammers. The hammer is drawn on the right arm, hiding the blade while it is out.
- **Prism Spire** (Shift+C, utility): the show's refraction, planted. The crystal is a held `AlienProjectileEntity` (kind `diamondhead_spire`) like the Titan Shards, so it is real faceted geometry and every client sees it. Not a beam: the user removed the Prism Beam. No self-heal or armour either: the user removed those too. It ends when he turns back.
- **Timing**: the Spear, Stab, Maul, Pillar, Spike Line and Eruption now land when the arm gets there, not on the key press (`tickStrikes`, `SLAM_DELAY`).
- **Crystal Cyclone** is a real full-body turn (`DiamondheadSpinMixin`), not a torso twist.
