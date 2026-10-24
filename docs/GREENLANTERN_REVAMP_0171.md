# Green Lantern revamp — v0.17.1 (draft 2, for approval)

The idea: **every construct lives in one menu, and you choose which key each one goes on.** Nothing is removed. Every
current ability moves into the menu, and your current layout is the default.

## 1. The Construct Menu (hold C for 0.5 s)

- **Opening it:** hold **C** for 0.5 s. This replaces the old hold-C construct wheel. Esc or C again closes it.
- **Layout:** a clear grid in five labelled sections. Every entry has an icon. Any key it's bound to shows as a badge in the corner (e.g. `G`, `⇧Z`).
  - **Attacks**: Ring Bolt, Beam, Fist, War Hammer, Gatling, Missile Barrage, Giant Hand, Buzzsaw, Anvil Drop, Chain Snare, Battering Ram, Sniper Rifle, plus the new ones below.
  - **Defence**: Directional Shield, Protective Dome, Hard-Light Wall, Containment Cage, Atmosphere Bubble, plus the new ones.
  - **Building & Utility**: Platform, Bridge, Stair Ramp, Mining Drill, Lantern Light, Hard-Light Tools, Rescue Tether, Carry Platform, Launch Pad.
  - **Summons**: Sentry Turret, Emerald Warrior, plus the new ones.
  - **Ring Powers** (not constructs, but bindable just the same): Oath ("Green Lantern's Light!"), Suit Up / Down, Ring Scan.
- **Hover** over an entry to see its description, Ring Charge cost, upkeep, damage, cooldown, and whether it's a tap or a hold.
- **Binding:** hover over an entry and press the key. That's **G, H, Z, X, C or V**, or **Shift +** any of them, for 12 slots in all. Pressing a key that's already used moves it to the new entry. **Right-click** an entry to clear its keys. A **Reset to default** button puts back today's layout.
- **Spawning from the menu:** **hold left click on an entry for 0.5 s** (a ring fills around the icon). It spawns once where you're aiming, at its normal cost and cooldown, and the menu stays open.
- **What stays where it is:** **R** (tap: Ring Bolt / hold: Continuous Beam), **N** (dismiss all constructs; Shift + hold N for 5 s takes the ring off) and double-jump **flight**.
- **Plain C** only takes **tap** constructs, because holding C opens the menu. Hold constructs (Shield, Gatling, Beam...) can go on Shift+C or any other key.
- Bindings are saved on the player (they survive death and relogging) and synced, so the HUD key boxes show the right construct names and icons.

**Default bindings** (today's keys, so nothing changes until you rebind):

| Key | Default | Shift+ | Default |
|-----|---------|--------|---------|
| G | Construct Fist | Shift+G | War Hammer Slam |
| H | Giant Hand | Shift+H | *(empty)* |
| Z | Directional Shield (hold) | Shift+Z | Protective Dome |
| X | Oath (hold) | Shift+X | Emerald Gatling (hold) |
| C | Deploy last-spawned construct | Shift+C | Missile Barrage |
| V | Suit Up / Down | Shift+V | Ring Scan |

## 2. New constructs (15)

| Construct | Section | What it does | Cost |
|-----------|---------|--------------|------|
| **Boxing Glove** | Attack | A giant spring-loaded glove punches 12 blocks straight out: 20 dmg, big knockback, hits everything in the line | 18 |
| **Fly Swatter** | Attack | A huge swatter slams down on the targeted spot: 28 dmg in a 4-block area, 1 s stun | 24 |
| **Catapult** | Attack | Lobs a hard-light boulder at where you're aiming: 30 dmg in a 3-block blast, breaks no blocks | 26 |
| **Emerald Tentacles** | Attack | Six tentacles come out of the ground (6-block radius), grab mobs for 2 s, then slam them for 18 | 30 |
| **Wrecking Ball** *(new)* | Attack | A ball on a chain swings around you in a wide arc for 2 s: 16 dmg to each enemy, flings them out | 22 |
| **Emerald Cannon** *(new)* | Attack | Hold to charge (up to 2 s), release: a big shot that does 15 to 40 dmg by charge and pierces up to 3 enemies | 20 to 35 |
| **Emerald Bow** *(new)* | Attack | Hold to draw, release: a fan of 5 hard-light arrows, 8 dmg each | 14 |
| **Bulldozer** *(new)* | Attack | A hard-light dozer drives 15 blocks forward, shoving every enemy ahead of it: 12 dmg, then dumps them at the end | 24 |
| **Bear Trap** | Defence | Placed on the ground (3 at most). The first mob to step in is rooted 4 s and takes 16 | 12 each |
| **Mirror Shield** *(new)* | Defence | Hold: a curved shield in front of you that sends arrows, fireballs and projectiles straight back at the shooter | 10 + upkeep |
| **Grappling Claw** *(new)* | Utility | A claw grabs the block you're aiming at and pulls you to it, or grabs a mob and pulls it to you (Shift while firing) | 8 |
| **Fighter Jet** | Summon | A jet circles the targeted area for 10 s, firing 6-dmg bolts at enemies | 35 + upkeep |
| **Emerald Dragon** *(new)* | Summon | A hard-light dragon flies beside you for 15 s and breathes light at whatever you're fighting (4 dmg / 0.5 s cone) | 40 + upkeep |
| **Emerald Mace** | Melee buff | Replaces your fist like the Energy Blade: 18 dmg with a small shockwave on every hit | 15 + upkeep |
| **Med Bay** | Summon | A hard-light tent (5-block radius, 12 s): Regeneration II for you and your squadmates | 30 + upkeep |

New constructs are added to the end of the saved list, so existing worlds keep their selections.

## 3. Power scaling: Lantern Ranks

- **Willpower XP**: 1 XP for every point of damage from your constructs and ring attacks, +50 per kill, +200 per boss kill.
- **Five ranks**, saved on the player and **kept on death and ring removal**:

| Rank | XP | Construct damage | Max Ring Charge | Construct cost | Extra |
|------|----|------------------|-----------------|----------------|-------|
| 1 Recruit | 0 | ×1.00 | ×1.00 | ×1.00 | — |
| 2 Lantern | 1,500 | ×1.10 | ×1.10 | ×0.95 | +1 active construct |
| 3 Veteran | 5,000 | ×1.20 | ×1.20 | ×0.90 | Shield takes 15% less drain |
| 4 Honor Guard | 12,000 | ×1.35 | ×1.30 | ×0.85 | +1 active construct, faster charge regen |
| 5 Corps Legend | 25,000 | ×1.50 | ×1.40 | ×0.80 | Oath lasts 50% longer and every construct turns golden-green |

- **Willpower Surge**: above 80% Ring Charge constructs deal +15%. Below 20% they deal −15%.
- **Where it shows**: in the **I menu (Power Info)** while you have the power: your rank name and emblem, an XP bar with "X / Y to next rank", what the next rank unlocks, and your current multipliers. The rank also shows at the top of the Construct Menu.
- `/projecthero gl rank <player> <1-5>` for testing (ops only).

## 4. Weak-spot buffs

| Move | Now | New |
|------|-----|-----|
| Sentry Turret | 7 dmg | 9 dmg |
| Emerald Gatling | 5 dmg/shot | 6 dmg/shot |
| Chain Snare tick | 2 dmg | 3 dmg |
| Emerald Warrior | 12 dmg | 15 dmg |

## 5. Menus & guide

- The guidebook GL pages are rewritten around the Construct Menu, with one entry per construct and a page for Lantern Ranks.
- The HUD key boxes show whatever construct is bound to each key, with its icon and cooldown.
- The ring tooltip shows your rank.

## 6. Balance pass (user, after testing)

- **Every Green Lantern damage number is cut by 30%** (old and new constructs alike). For example: Ring Bolt 18→12, Construct Fist 24→16, War Hammer 26→18, Missiles 12→8 each, Gatling 6→4 a bolt, Anvil 34→24, Sniper 22→16, Catapult 30→21, Fly Swatter 28→20. Ring Charge costs are unchanged.
- **The bonuses add up instead of multiplying:** the Oath adds +50% damage and +50% melee (was double), Lantern Rank adds up to +25% (was ×1.5), and Willpower Surge is ±15%. The best case is now ×1.9 instead of ×3.45. The Oath still doubles every Ring Charge cost.
