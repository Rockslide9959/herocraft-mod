# Multimouse / Mullo: preview art (for approval, not wired into the game)

Everything here is under `scratchpad/multimouse/`. Nothing in `src/` was touched.

## Files
| File | What |
|---|---|
| `multimouse_suit_wide.png` / `multimouse_suit_slim.png` | 64x64 suit overlay skins (4-px / 3-px arms) |
| `multimouse_suit_wide_x10.png` / `..._slim_x10.png` | flat x10 previews (checkerboard = transparent, magenta grid every 8 texels) |
| `multimouse_suit_sheet_wide.png` / `..._slim.png` | FRONT / THREE-QUARTER / SIDE / BACK renders, worn over `placeholder_wearer.png`, with the 3D extras |
| `mullo_sheet.png`, `mullo.geo.json`, `mullo.png` | Mullo concept sheet + GeckoLib geo (`geometry.kwami_mullo`) + 512x320 texture |
| `mouse_pendant.png` / `mouse_pendant_x16.png` | 16x16 hand-painted item icon + x16 preview |

## Suit layout (same conventions as Viperion)
- Head base layer: clear except the mask (face rows y 11-14, eye holes on rows 12-13, wrapping one texel round each side at x 7 / x 16, straps at x 6 / x 17).
- Hat layer: the hood (top, sides, back). Its front is only a rim: a full brow row (y 8), two-texel corners on rows 9 and 14-15, one-texel sides. The hat bottom has only a dark lip; the neck opening is clear.
- Base layer: bodysuit. Belt on body row 8 (y 28), arm cuffs on arm rows 9-10, boot cuffs on leg row 7, pink soles on the leg bottom faces.
- Overlay layer: the raised cuffs (sleeve rows 9-10), boot cuffs (pants row 7) and the pendant (jacket front, rows 0-1, x 3-4).

## 3D extras (model pixels, standard biped: feet at y 0, body y 12-24, head y 24-32, front = -Z, entity's right = -X)
Bones are children of the vanilla head/body parts, as in `gen_multimouse_sheet.js`.

**Mouse ears** (on the head, one each side)
- Each ear is a disc of radius 3.4 px (6.8 px across) and 0.9 px thick, built from four overlapping boxes (6.8x3.0, 6.3x4.9, 4.9x6.3, 3.0x6.8) so it reads as a rounded octagon.
- Centre at (±3.0, 35.4, 0.4). It sits on the top corners of the hood and overlaps the hood top by about 0.7 px.
- Pivot (±3.0, 32.2, 0.4), rotation [-8, 0, ±20]: tipped back a little and splayed outward.
- Pink inner disc: radius 2.3 px and 0.2 px thick, 0.15 px lower than the outer disc's centre, on the front face (z -0.13 to 0.06 relative to the disc centre).
- Colours: grey satin outside with a light rolled rim; pink inside with a darker pink edge.

**Rope belt** (body part)
- A box from (-4.45, 14.85, -2.45) to (4.45, 16.15, 2.45), i.e. 8.9 x 1.3 x 4.9. It wraps body row 8, 0.45 px proud.
- Knot at the back: a 1.6 x 1.6 x 0.9 box centred at (0, 15.5, 2.65).
- Hanging handle at the front right hip:
  - a 0.42 px rope runs from (-2.9, 15.0, -2.6) down to (-3.15, 12.9, -2.7);
  - a 1.0 x 0.5 x 1.0 bead is centred at y 12.7;
  - the 0.8 x 2.6 x 0.8 grip is centred at y 11.2;
  - a second bead sits at y 9.75.

**Rope tail** (body part, its own `tail` bone, pivot (0, 15.5, 2.9) at the knot)
- A 0.62 px square rope through the points (0,15.5,2.9), (0.1,13.6,3.9), (0.4,10.8,4.7), (0.9,7.6,5.1), (1.4,4.9,4.9) and (1.7,3.3,4.4). It swings out behind the legs and comes back in.
- At the end, the jump-rope's other handle hangs down:
  - a 1.1 x 0.55 x 1.1 bead;
  - a 0.85 x 2.7 x 0.85 grip;
  - a 1.15 x 0.6 x 1.15 end bead.
- The tail's lowest point is about 0.5 px above the ground.
- In game, it would want a small sway in the walk/idle clips.

## Mullo
- Bones: root, body, head, arm_r, arm_l, leg_r, leg_l, tail. These are the shared Kwami names; Sass uses the same list, so the shared clips drive Mullo unchanged.
- The ears, whiskers, lashes, nose and teeth are cubes on the `head` bone.
- Height is set to 0.62 blocks, like Sass.
- No animation clips were written. Sass's clip code in `gen_sass.js` could be copied with the `sass.` prefix renamed to `mullo.`.

## Regenerate (from the repo root, Git Bash)
```
node scratchpad/multimouse/gen_multimouse_suit.js    # suit skins + x10 previews
node scratchpad/multimouse/gen_multimouse_sheet.js   # suit sheets (needs the skins), ~16 s
node scratchpad/multimouse/gen_mullo.js              # Mullo geo/texture/sheet + pendant icon
```
The scripts use `scratchpad/viperion/skin_tools.js` and `scratchpad/viperion/lib_concept_models.js`.
