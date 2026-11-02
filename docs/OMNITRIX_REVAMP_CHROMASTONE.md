# Omnitrix revamp: Chromastone

Status: **BUILT (v0.17.2)**. Requested by the user: "can you work on revamps for the rest of the alien force aliens please, look at
their powers and abilities in the show and do research and make them good please"; "delete all the alien models and only use the
skins i provided please no extra things untill i say"; and from the earlier revamps, "dont add ultimate aliens yet". Code:
`omnitrix/alien/chromastone/ChromastoneKit.java` and `client/omnitrix/alien/ChromastoneClient.java`. Tests:
`OmnitrixChromastoneGameTests` (6). Screenshots: `docs/screenshots/v0172/chromastone/`.

## Research

Sources are fan wikis (ben-10-aliens fandom, omnipedia mirrors, myanimethoughts) and memory of the show, so they are unverified.
They agree that Chromastone is a **Crystalsapien** from Petropia: the DNA is Sugilite's, and *The Secret of Chromastone* (Ultimate
Alien) is about him. His powers:
- **energy absorption and redirection**: he takes in energy blasts, electricity and light and fires them back. This is his
  signature *(recurring)*.
- **ultraviolet / prism beams** from his hands and from his whole body at once *(recurring)*. His own blasts, made without absorbed
  energy, drain him.
- **flight and energy shields**, which the fan wikis list.
- a **dense crystal body**: durable, but blunt force can crack and shatter it.

Overloading him past what he can hold is a fan-wiki idea and a gameplay counter, marked as such.

**Kept distinct from Diamondhead.** Diamondhead owns crystal shards, spikes, prisons and pillars, so the basic kit's Shard Spray,
Crystal Slam and Spike Burst are **gone**. Chromastone is now light and energy only.

## The model

Every extra body part is **gone**: the crown, back, shoulder and arm crystal spikes, the eye glow cubes, the chest crystal that
glowed with charge, and the `chromastone_extras.png` sheet. He is only the player model in the user's 64x64 `chromastone.png`, on
the wide-arm model (works for slim players). The Omnitrix chest badge stays, and so do the move VFX.

## Kit (built)

The engine is the **Charge** meter, 0 to 100, shown on the HUD. The meter flashes "OVERLOAD" when he is overcharged.

| Key | Move | What it does | Numbers |
|---|---|---|---|
| R | Prism Bolt | a bolt of UV light (rendered streak projectile); free | 6, cd 0.6 s |
| Shift+R | Prism Beam | a rainbow beam that pierces everything in line | 20 charge; 15 each, 28 blocks, cd 3 s |
| G | Redirect | for 1.5 s, energy, fire, magic and projectile hits are **absorbed whole** (no damage, full charge; the projectile is destroyed) and **fired straight back** at whoever sent them, as a light ribbon | 1.5x the hit (8 to 17), up to 3 returns, cd 6 s |
| Shift+G | Refraction | his light splits into rays onto the nearest creatures he can see | 30 charge; up to 5 within 16, 12 each, cd 6 s |
| X | Light Flight | take off / land, hovering on his own light | cd 0.5 s |
| Shift+X | Light Dash | a streak of light along the look (rendered trail) that hits what it passes | 10 charge; 6 to everything in 8 blocks, cd 3 s |
| Z | Radiant Burst | light pours out of his whole body in 8 beams (spectrum ribbons) | 13 to the first creature in each, 14 blocks; with 25+ charge it spends 25 and they hit 17; cd 8 s |
| Shift+Z | **Full Spectrum (ULT)** | 0.75 s gathering the colours into his chest (rooted, violet vignette), then a rainbow beam with a shock ring, an end blast, a scorch decal and camera shake | needs 50+ charge and spends it all; 40 + 0.6 per charge over 50 (70 at full), 32 blocks, cd 45 s |
| C | Charge Release | everything stored goes out in a shockwave ring | 8 + a tenth of the charge (up to 18), r 6; spends it all, cd 10 s |
| Shift+C | UV Flare | a flash of ultraviolet: blinded and outlined; **the undead burn** in it | 15 charge; r 8, blind 3 s; undead 6 + alight 4 s; cd 10 s |
| V | Absorb Stance | every hit is absorbed at double charge, but he is slowed. V again ends it | 5 s, cd 15 s |
| Shift+V | Prism Shield | a hexagon of hard light in front, its edges cycling through the spectrum. Hits from within 75° of where he looks take less; frontal energy and projectiles are absorbed whole into charge | 20 charge; 6 s, frontal hits -70%; cd 15 s |
| Attack | Charged Fists | with 25+ charge a blow flares with light | +3, costs 2 charge |

Damage check: quick 6 (Bolt, Dash); normal 12 to 17 (Refraction 12, Radiant 13/17, Prism Beam 15, Redirect 8 to 17, Release up to
18); ultimate 40 to 70.

### Passives
- **Absorption:** energy, fire, lightning, magic and projectile hits are mostly absorbed. He takes a fifth, and each point becomes
  4 charge. A lightning strike fills him to 100.
- **Light-fed:** under the open daytime sky he gains 1 charge every 2 s.
- **Crystal does not burn:** fire never sets him alight.
- **Tough:** 34 health, armour 10 (+2 toughness) plus the hidden Resistance I, 40% knockback resistance.

### Weaknesses
- **Brittle:** blows, explosions and falling blocks do x1.25 and are never absorbed.
- **Overload (new):** when he is already at 100 and still absorbing, he has 10 s to spend charge below 100. If he doesn't, it
  bursts out of him: 10 to everything within 4, 4 to himself, and the charge drops to 40.
- His best moves cost charge, he is a little slow (x0.95), and he loses the charge when he turns back.

## Conventions kept
Static per-player maps (`RETURNS`, `LAST_TICK`) are cleared on revert and in `clearSessionState`. The sunlight and overload clocks
are guarded against double ticks. Lang covers `projecthero.omnitrix.chromastone.<move>` + `.desc`, the guide entry, the role and
passive/weakness lines, the action-bar messages and the HUD labels.
