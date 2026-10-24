# Green Lantern revamp — v0.17.1 (draft for approval)

Nothing is removed. Every current key, construct, suit and the Oath mode stay as they are.

## 1. Eight new constructs (C wheel)

They're added to the end of the construct wheel, so saved selections keep working. Each one gets its own hard-light model and wheel icon.

| # | Construct | Type | What it does | Cost |
|---|-----------|------|--------------|------|
| 1 | **Boxing Glove** | attack | A giant glove on a spring punches straight out 12 blocks: 20 dmg, big knockback, hits everything in the line | 18 |
| 2 | **Fly Swatter** | attack | A huge swatter slams down on the targeted spot: 28 dmg in a 4-block area, stuns for 1 s | 24 |
| 3 | **Catapult** | attack | A catapult builds next to you and lobs a hard-light boulder where you're aiming: 30 dmg in a 3-block blast, breaks no blocks | 26 |
| 4 | **Bear Trap** | trap | Placed on the ground (3 at most). The first mob to step in is rooted for 4 s and takes 16 dmg | 12 each |
| 5 | **Emerald Tentacles** | attack | Six tentacles come up out of the ground in a 6-block radius, grab mobs, hold them for 2 s, then slam them for 18 | 30 |
| 6 | **Fighter Jet** | summon | A jet circles the targeted area for 10 s, firing bolts (6 dmg each) at hostiles | 35 + upkeep |
| 7 | **Emerald Mace** | melee buff | Replaces your fist like the Energy Blade, but slower and heavier: 18 dmg with a small shockwave on every hit | 15 + upkeep |
| 8 | **Med Bay** | support | A hard-light tent (5-block radius, 12 s) gives you and your squadmates Regeneration II and slowly mends splinted wounds | 30 + upkeep |

## 2. Power scaling: Lantern Ranks

GL now grows the more you use the ring.

- **Willpower XP**: 1 XP for every point of damage your constructs or ring attacks deal, +50 for every kill, +200 for every boss kill.
- **Five ranks**, saved on the player:

| Rank | XP | Construct damage | Max Ring Charge | Construct cost | Extra |
|------|----|------------------|-----------------|----------------|-------|
| 1 Recruit | 0 | ×1.00 | ×1.00 | ×1.00 | — |
| 2 Lantern | 1,500 | ×1.10 | ×1.10 | ×0.95 | +1 active construct |
| 3 Veteran | 5,000 | ×1.20 | ×1.20 | ×0.90 | Shield takes 15% less drain |
| 4 Honor Guard | 12,000 | ×1.35 | ×1.30 | ×0.85 | +1 active construct, faster charge regen |
| 5 Corps Legend | 25,000 | ×1.50 | ×1.40 | ×0.80 | Oath mode lasts 50% longer and turns every construct golden-green |

- **Willpower Surge**: above 80% Ring Charge, constructs deal another +15%. Below 20% they deal −15%, so it pays to keep the battery topped up.
- Your rank and an XP bar show on the GL HUD (a thin Hairline under the charge bar), in the GL guide page and in the ring's tooltip.
- `/projecthero gl rank <player> <1-5>` for testing (ops only).

## 3. Weak-spot buffs

| Move | Now | New |
|------|-----|-----|
| Sentry Turret | 7 dmg | 9 dmg |
| Emerald Gatling | 5 dmg/shot | 6 dmg/shot |
| Chain Snare tick | 2 dmg | 3 dmg |
| Emerald Warrior | 12 dmg | 15 dmg |

## Open calls (my defaults, change any of them)

- **Rank on ring removal**: kept. Taking the ring off and putting it back on doesn't reset you.
- **Rank on death**: kept.
- **XP from player kills** (PvP): counts the same as mob kills.
