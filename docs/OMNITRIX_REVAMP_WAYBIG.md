# Way Big (prototype Omnitrix, v0.17.2)

User: "add waybig too please. Use this skin: https://www.planetminecraft.com/skin/way-big-ben-10-4631092/ Do research on his abilities
and powers in the show and make him a really good power. Make him 100 blocks tall please. Does not get slowed down by water or lava
and can walk safely through both. Remove the pixel omnitrix from the skin and place our omnitrix badge over it please. create an item
that lets the user unlock this alien please, make it something you need to get in the nether, maybe an abyssal behemoth drop"

## In the show

A To'kustar, an ultra-rare species of giants formed in cosmic storms.
- He towers over skyscrapers, throws buildings and wrestles other giants.
- He fires the **Cosmic Ray** from his arms crossed in a plus sign, a beam strong enough to knock back other To'kustars.
- Every stomp shakes the ground.
- He spins himself into a cyclone.
- He survives in space and shrugs off almost any attack.
- To'kustars can change their size.
- His one weak spot is the **head fin**: hits there cause paralysing pain.

## The size (how 100 blocks works)

Vanilla's scale attribute stops at 16x, about a 29-block player, and a 100-block hitbox could never move through the world. So:

| | Colossal (default) | Compact (V) |
|---|---|---|
| Drawn height | 100 blocks (player model x55.6) | 15 blocks (x8.3) |
| Eyes | 90 blocks up | 13.5 blocks up |
| Hitbox (his feet and shins) | 3 x 6 blocks | 1.2 x 4.5 blocks |
| Step height | 40 blocks (hills are knee-high) | 6 blocks |
| Walk speed | x3.5 | x1.6 |
| Move reach | full | about a third |

Engine hooks, each reusable by later giants:
- `AlienClientKit.renderScale`: the model drawn bigger than its hitbox.
- `cameraDistance`: the third-person camera pulled back to 0.8x his height.
- `AlienKit.drawnBody`: the culling box, so he isn't culled when his feet are off screen.
- `stepHeight`: the step-height attribute caps at 10, so this goes past it.
- `ignoresFluids`: water and lava don't affect his movement.

He's also exempt from the server's "moved too quickly" check, the same way speedsters are.

Every move aims from his eyes to where he looks, so he fights from the sky. He can't punch, mine or place blocks by hand: they're out of reach from 90 blocks up, and his hands are too big for tools (`pawsOnly`; he can still eat).

## Keys

| Key | Move | What it does |
|---|---|---|
| R | Titan Fist | A house-sized fist comes down where he looks (up to 220 blocks): 100 damage in 6 blocks, flung away. 2.5 s |
| Shift+R | Colossal Chop | Both hands brought down edge-first: 90 damage along a 48-block line ahead. 9 s |
| G (tap / hold) | Cosmic Ray | Arms raised in the cross. Tap for a 1.2 s burst, hold for up to 4 s: 20 burning damage every 4 ticks in the beam, then a 100-damage blast where it strikes. The beam is drawn on the client every frame. 20 s after it stops |
| X | Giant Stride | A leap that clears a valley; the landing quakes for 70 damage in 10 blocks. 8 s |
| Z | Quake Stomp | Shockwave ring out to 18 blocks: 85 damage, thrown up, slowed (ground only, not fliers high above). 10 s |
| Shift+Z | Thunder Clap | A thunderclap down a 40-block cone: 65 damage, blown away, dazed. 12 s |
| V | Size Shift | Colossal (100 blocks) and Compact (15 blocks), eased over 0.6 s. 3 s |
| C | To'kustar Cyclone | Spins for 5 s: everything within 28 blocks is dragged in and torn at (15 every half second), then flung out for 100. 45 s |

### Damage (round 3)

User: "way big is a giant a really powerful alien, make his moves stronger since hes huge and really powerful please". He sits well above the
aliens' normal scale (quick 5-8, normal 12-17, big 17-20, ultimates 21-36): every blow is about another alien's ultimate or more.

| Move | Before | After |
|---|---|---|
| Titan Fist | 42 | 100 |
| Colossal Chop | 36 | 90 |
| Cosmic Ray (tick / end blast) | 9 / 30 | 20 / 100 |
| Giant Stride landing | 30 | 70 |
| Quake Stomp | 34 | 85 |
| Thunder Clap | 26 | 65 |
| Cyclone (tick / fling) | 5 / 22 | 15 / 100 |

### Animations (round 3)

Every move has a wind-up, a hit and a recovery, third and first person. Each blow lands on the tick its pose does: fist 6, chop 9, foot 8,
clap 8 (the stomp and the clap used to hit on the press, before the pose got there). The Stride holds its pose until he lands, then a landing
pose takes it. The Cyclone turns his whole body (`AlienClientKit.bodySpin`) inside a funnel of six wind bands, and winds down onto a whole
turn. Size Shift eases along the log of his height, so the shrink reads evenly, with its own pose each way. A hit on the fin makes him clutch
his head.

## Passives

- **To'kustar Body:** water and lava never slow, push or float him, and he walks through both on the bottom. Fire, lava, drowning, suffocation, cramming and falls never hurt him, and fire never sticks.
- 300 health, maximum armour (30 armour, 20 toughness), never knocked back, plus the hidden Resistance I every alien has.
- Every stride shakes the ground and kicks up dust.

## Weaknesses

- **Head fin:** a blow from above his shoulders does double damage, and he clutches his head (`WayBigKit.finStruck`). It counts when the source, or whoever threw it, is above 82% of his height (82 blocks Colossal, 12.3 Compact), or when a shot or dropped thing falls on him from overhead at 60 degrees or steeper.
  - The old check read only `DamageSource.getSourcePosition()`. For a shot that is where the arrow is when it hits, and it can only hit his 6-block hitbox, so it never counted, and no melee reaches him from 82 blocks up.
  - His drawn head has no hitbox of its own: a shot aimed at his head in the air passes through, and a flyer up there can't punch him there. Anything shot from up there lands on his shins and still counts as the fin.
- He can't use tools or blocks, and is too high up to punch or mine by hand.
- He's a 100-block target that every mob can see.
- He isn't on the dial until a **To'kustar Shard** unlocks him. It's the Abyssal Behemoth's guaranteed drop in the Nether. `Aliens.ITEM_ONLY` keeps him out of the mistransform unlock pool.

## Skin

Every face of the downloaded skin was checked. It paints **no** Omnitrix symbol: the chest is his red stripe, the head stripe is the fin, and the dark bands are his wrists and collar. So nothing was painted out, and the mod's badge sits in the middle of his chest, where the show has it (`WayBigClient.badge()`). Textures come from `scratchpad/ben10_v0171/gen_way_big.js`. The dial icon is a placeholder silhouette until the user's icon art arrives.

Round 3 screenshots: `docs/screenshots/v0172/round3_waybig/`.
