# Ladybug (Tikki's Miraculous) -- the Yo-yo kit (v0.16.6)

The core (box, jewel, Kwami, transformation, countdown, HUD, guide) is in `docs/MIRACULOUS_REFERENCE.md`. This file is
the Ladybug kit only. Code: `com.projecthero.mod.miraculous.ladybug` (server) and
`com.projecthero.mod.client.miraculous.ladybug` (client). Every number lives in `LadybugConfig`.

## Moves

| Key | Move | Numbers | Shift version | Numbers |
|---|---|---|---|---|
| R | **Yo-yo Strike** (`ladybug_yoyo_strike`) | 12-block whip along the look, first creature hit takes 10, snaps back | **Ricochet** (`ladybug_ricochet`) | up to 4 threats, each within 8 of the last, 7 each; 3 s cd |
| G | **Yo-yo Bind** (`ladybug_yoyo_bind`) | 16 blocks, string coils round it, rooted 3 s (bosses 1 s); G again inside the 3 s yanks it to 1.6 blocks in front of her (never a boss); 6 s cd | **Lasso Fling** (`ladybug_lasso_fling`) | the bound target, or one bound on the spot, thrown along the look (2.2 b/t + 0.55 up); 8 damage when it lands / hits a wall, plus vanilla fall damage; not bosses; 7 s cd |
| X | **Yo-yo Swing** (`ladybug_yoyo_swing`) | HOLD: hook a block within 30, Spider-Man's rope physics (Jump reels in, Sneak out); let go (or press again) to release with the arc's speed; 0.3 s cd | **Zip Line** (`ladybug_zip_line`) | hook within 30, reel straight to it at 1.6 b/t, ends 1.8 from the hook (3 s max) with a hop; 2 s cd |
| Z | **Spinning Shield** (`ladybug_spinning_shield`) | HOLD (6 s max): a spinning spotted disc in front; arrows shatter, other projectiles are reflected (vanilla `ProjectileDeflection.REVERSE`), front melee does 25% and knocks the attacker back 1.4; 2 s cd | **LUCKY CHARM** (`ladybug_lucky_charm`) | see below |
| V | **Pole Vault** (`ladybug_pole_vault`) | string planted 1.6 ahead, launched 1.3 up + 1.0 forward (~10 up, ~10 on); 3 s cd | **Trip Wire** (`ladybug_trip_wire`) | wire from her shins to the block she looks at (10 max) for 20 s; anything she may hit that crosses it is rooted 2 s (once each); 10 s cd |
| C | **Yo-yo Scan** (`ladybug_yoyo_scan`) | compact pose; every threat within 40 outlined red through walls for 10 s, for her alone; 15 s cd | **Yo-yo Phone** (`ladybug_yoyo_phone`) | chat ping + 30 s compass marker to every online squadmate; they glow for her 10 s; no squad = refused, no cd; 10 s cd |

Strike cooldown 0.8 s.

### Lucky Charm / Miraculous Ladybug (Shift+Z)

1. **Lucky Charm** (once per transformation): the Yo-yo goes up, a red light burst, and a charm drops from 6 blocks up
   (owner-only pickup). Choice (`LuckyCharm.pick`, in this order): health < 35% -> **spotted golden apple** (Instant
   Health II, Regeneration II 10 s, Absorption II 2 min, Resistance 30 s); 5+ threats within 8 -> **spotted TNT**
   (thrown, bursts on impact: 20 at the centre to 10 at 5 blocks, only things she may hit, no block damage); falling
   (vy < -0.5 or 3+ blocks fallen) -> **spotted parachute** (straight into her hand; held while falling: Slow Falling +
   a canopy model); otherwise a **spotted sword** (10.5 damage = diamond +50%, unbreakable) or, when the nearest threat
   is further than 10, a **spotted bow** (needs no arrows, arrows +50%). Then **Ladybug Vision** 10 s: her world goes
   grey (post chain `shaders/post/ladybug_vision.json`, vanilla `color_convolve` at saturation 0, run at the END of
   world rendering so the hand, GUI and outlines stay in colour) and the charm + threats within 32 glow red. The
   3-minute countdown starts (`Miraculous.startCountdown`).
2. **Miraculous Ladybug** (Shift+Z again while holding the charm): the charm is used up, a burst and a swarm of ~700
   tiny 3D ladybugs (client sim, `LadybugSwarm`) fly out; every block broken within 64 of her in the last 5 minutes that
   is still missing comes back (48 a tick, nearest first); she and every squadmate within 128 heal to full. Does NOT
   restart the countdown.
3. Charms carry `lb_owner` / `lb_form` custom data and vanish from any inventory that isn't hers in this transformation,
   when she detransforms, or after Miraculous Ladybug; dropped charm item entities are tracked and discarded.

### The restore memory (`LadybugRestore`)

Hooked into the existing City block-change hook (`CityLevelChunkMixin` on `LevelChunk.setBlockState`, one added line).
While a Ladybug is transformed (refreshed by her kit tick), any change within 64 that destroys or replaces a non-air
block records its old state (first record per spot wins). Bounded: 40 000 spots per dimension, 5-minute window, pruned
every 10 s. Restores use flags `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE`, skip spots that are no longer air / fluid / fire,
wait for anyone standing in a spot (giving up after 30 s). Block-entity contents are not restored.

## Suit and visuals

- Suit: the user's NovaSkin skin (post 5143214044) cut out by `scratchpad/make_suit_skins.js` into
  `textures/entity/miraculous/ladybug/suit_wide.png` / `suit_slim.png` (face, skin and hair transparent; the mask with eye
  holes kept). Drawn as an overlay a hair over the wearer's own skin, on the slim or wide model to match their skin;
  their jacket / sleeve / trouser layers are hidden, their hat layer (hair, hats, glasses) redrawn. Per-texel reveal
  from the top of the head down over `TRANSFORM_TICKS` (newest texels pale pink), reversed in 20 ticks on detransform.
- First person: the suit sleeve over the bare arm (`LadybugFirstPersonArmMixin`), the Yo-yo / string / shield drawn in
  world space from a point at the bottom right of the view.
- The Yo-yo (two spotted halves on a black axle, spins) hangs on the front of her right hip while idle. In moves: whipped
  out / back, bouncing through the ricochet, wound round the bound target (coils), hooked for swing / zip, planted for
  the vault, strung as the trip wire, opened like a compact (lit screen) for Scan / Phone, thrown up for the Lucky Charm.
  The string is a camera-facing ribbon, straight when taut, sagging (shallow catenary) and swaying when loose.
- Poses (anim ids 20..32, `LadybugKeys.ANIM_*`; `LadybugPose`): strike wind-up + whip, ricochet sweep, bind loop + throw,
  yank haul, fling sweep, swing / zip arm up the line, shield arm forward, vault hands high + knees tucked, wire flick,
  compact held at the face, charm throw-up, Miraculous Ladybug toss + arms wide, parachute hands on the cords.
- Outlines: `LadybugGlowPayload` (to her alone) + `LadybugGlowMixin` / `LadybugGlowColorMixin`. Squad ping:
  `LadybugPingPayload` + a HUD compass strip.

## Synced state

All in the core's `MiraculousState.vals` / `vecs`; keys and readers in `LadybugKeys` (client-safe). The core clears
them on detransformation; `LadybugKit.onDetransform` drops the line, the float tolerance, her roots and every charm.

## Hooks the core must provide

- `KwamiKit.modifyIncomingDamage` must be called by the core's damage hook (the shield's projectile / melee rules). The
  shield also deletes / reflects projectiles itself every tick, so it works without it.

## Tests

`LadybugGameTests` (20): numbers, roots, yank, fling landing, swing / zip, shield vs arrow and melee, the charm choice
table, charm drop + countdown + once per transformation, Miraculous Ladybug restoring a broken block + healing (self and
squad), charms vanishing, vault, trip wire, scan, phone, detransform clean-up.
