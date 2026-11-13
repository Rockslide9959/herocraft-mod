# Raven revamp -- approved and built (2026-11-13)

Approved by the user ("Yes, build it") with the open choices below as picked. Built in `raven/RavenMoves` (main keys),
`raven/RavenSpells` + `RavenSpell` (the wheel), `client/raven/RavenSpellWheelScreen`; numbers in `raven/RavenConfig`.
Build decisions not spelled out below: Demonic Ascension costs 40 Dark Energy; "spells +X%" applies to every Raven move and
spell (Trigon's Wrath keeps its flat 30); Shadow Binding's "4-block area" is a 4-block radius; Void Vortex hurts what is within
3.5 blocks of its eye each second (it pulls from 7); Astral Sanctuary is a fixed dome where it was cast (Rage halves its 40% to
20%); a misfire moves a spell cast at a spot (Vortex, Binding, Rift) up to 6 blocks, any other spell slips away as a small
4-damage burst near her (paid for, half its cooldown); V held 5 ticks opens the wheel.

Merges the current Raven kit (`docs/TEEN_TITANS_DESIGN.md`) with the user's ChatGPT moveset: the best moves from each, plus
ChatGPT's **Spell Wheel**. House rules kept: H = power selector only, no N, **X = Shadow Dash, Shift+X = mobility attack**,
flight = double-tap Space, V = utility/mode.

## Identity
A magical battlefield controller: crowd control, ranged damage, defence and support, held back by her emotions. Not a
melee brawler.

## Stats and resources
- 30 health (was 20), 10% damage resistance, slow fall when not flying.
- **Dark Energy** (Slab, 100), refills 5/s, 10/s in Demonic Ascension. Every move and spell costs it.
- **Composure** (Hairline under the Slab): the existing emotion meter, now ChatGPT's "Emotional Stability" too.
  - Casting, heavy hits and long fights push it down; Meditate, Soul Restoration and time out of combat bring it back.
  - **Below 40 (Unsettled):** spells +20% damage but wild: bolts drift, 1 in 8 wheel spells misfires into a random nearby spot.
  - **Below 10 (Rage):** four red eyes, spells +40%, but shields absorb half as much; Trigon's Wrath unlocks.

## Passives (ChatGPT)
- **Demonic Heritage:** 30% less magic and supernatural damage (potions, evokers, witches, Doctor Fate, Shazam's lightning).
- **Dark Empathy:** every kill gives back 1 heart and 5 Dark Energy.
- **Mystical Awareness:** every 8 s, hostile creatures within 24 blocks glow through walls for 2 s.

## Main keys

| Key | Move | From | Spec |
|---|---|---|---|
| R | Dark Bolt | both | fast black bolt, 8 dmg, bursts in a 2-block purple pop (4 to others). 8 energy |
| Shift+R (hold) | Telekinesis | current | wrap a creature, block or item in black energy, steer it with your look, release to throw (10 + impact). Not bosses. 10 + 4/s |
| G | Soul Shield | ChatGPT | personal purple barrier for 5 s: absorbs 30 damage, reflects arrows and projectiles, knocks back melee attackers; if it soaks 20+, it bursts at the end (8 dmg, 4 blocks). 25, 16 s cd |
| Shift+G | Soul-Self | both | your raven soul flies out and **you steer it for up to 5 s**, engulfing what it touches (14 dmg, Blindness, Slowness). Press Shift+G again to **teleport to it**; otherwise it flies back to you. 35, 18 s cd |
| Z | Telekinetic Crush | ChatGPT | grab up to 6 creatures in front of you, hold them in the air 2 s, slam them down: 14 dmg, 1 s stun. Bosses aren't lifted and take the damage only. 30, 9 s cd |
| Shift+Z | Trigon's Wrath | current | Rage only: giant 3x shadow with four red eyes for 15 s, tentacles slam everything within 12 blocks (30 dmg, fear on players). Afterwards drained (Composure calm, energy 0). 3 min cd |
| **X** | **Shadow Dash** | current | streak of dark energy 10 blocks along your look, through creatures, no damage taken. 12, 1 s cd |
| **Shift+X** | **Shadow Step Strike** | current | sink into your shadow and erupt under the creature you look at (20 blocks): claws launch it up, 12 dmg. 20, 4 s cd |
| C | Dark Energy Wave | ChatGPT | charge 0.6 s, then a wide wave of dark energy 12 blocks forward: 12 dmg, knockback, Slowness. Breaks glass and leaves only if mob griefing is on. 30, 11 s cd |
| Shift+C | Demonic Ascension | ChatGPT | 15 s: white glowing eyes, purple aura round the cloak, spells +35%, energy costs -25%, regen 10/s, +20% speed, Soul Shield absorbs 50, Soul-Self 50% bigger and stronger. 60 s cd. Pushes Composure down 20. |
| **V (tap)** | **Cast spell** | ChatGPT | casts the spell picked on the wheel (its icon shows in the HUD's V box) |
| **V (hold)** | **Spell Wheel** | ChatGPT | radial wheel of 10 spells (like Beast Boy's form wheel): point and release to pick. Each slice shows icon, name, one-line description, energy cost and cooldown. Doesn't pause the game. |
| Shift+V (hold) | Meditate | current | float cross-legged chanting: Composure and energy refill fast; you can't move or attack |
| (double Space) | Flight | current | calm levitation flight |

ChatGPT suggested a separate B key to cast; I kept everything on V (tap = cast, hold = wheel) so there's no new key to learn,
the same way Beast Boy's wheel works. B is easy to add if you'd rather.

## The Spell Wheel (10 spells)

**Combat**

| Spell | From | Spec |
|---|---|---|
| Void Vortex | ChatGPT | vortex where you look (24 blocks) for 4 s: pulls creatures in, 3 dmg/s, explodes at the end for 10. 30, 15 s cd |
| Shadow Binding | both (was Shadow Grasp) | claws rise from the ground in a 4-block area: 6 dmg and roots for 3 s (bosses slowed instead). 20, 12 s cd |
| Soul Detonation | ChatGPT | marks everything within 6 blocks, 1.5 s later the marks explode: up to 24 dmg, more near the centre. Taking 8+ damage while charging cancels it. 40, 18 s cd |
| Soul Devour | ChatGPT | drain beam on one target for 3 s: 4 dmg/0.5 s, half of it heals you; breaks past 16 blocks. 35, 20 s cd |
| Debris Storm | current | tear up 8 nearby blocks into an orbit and fire them one per click (energy copies if mob griefing is off; blocks restored after 10 s). 30, 20 s cd |

**Utility**

| Spell | From | Spec |
|---|---|---|
| Dimensional Rift | ChatGPT | purple portal where you look, up to 20 blocks, through walls if there's room: you step through it. 25, 12 s cd |
| Soul Restoration | both (was Empath Heal) | aimed at a squadmate: take their pain (up to 10 of their missing health as your damage) and heal them double. Aimed at no one: channel 3 s to heal 6 hearts and cure poison, weakness and slowness (moving or being hit stops it); also calms Composure +30. 45, 25 s cd |
| Telekinetic Pull | ChatGPT | pull every dropped item within 20 blocks to you, flip the lever or button you look at, or yank a light creature to you. 15, 8 s cd |
| Astral Sanctuary | both (was Dark Shield) | 5-block dome for 8 s: blocks projectiles, 40% less damage for you and squadmates inside, hostile creatures can't get in. 60, 35 s cd |
| Shadow Veil | new | melt into shadow for 6 s: invisible, mobs lose track of you, silent footsteps; any attack ends it. 20, 20 s cd |

ChatGPT's Levitation is left out because Raven already flies; Shadow Veil takes its slot. ChatGPT's Shadow Bolt is merged
into R.

## What moves where (from the current kit)
- Dark Shield (hold Z) becomes the Astral Sanctuary spell; Z is now Telekinetic Crush.
- Shadow Grasp (G) becomes the Shadow Binding spell; G is now Soul Shield.
- Telekinetic Wave (C) is folded into Telekinetic Crush (Z); C is now Dark Energy Wave.
- Debris Storm (Shift+C) becomes a spell; Shift+C is now Demonic Ascension.
- Empath Heal (V) becomes the Soul Restoration spell; V is now the wheel.
- Kept as they were: Dark Bolt, Telekinesis, Soul-Self (now steerable), Trigon's Wrath, Shadow Dash, Shadow Step Strike, Meditate, flight, Titans Together.

## Open choices (my picks unless the user says otherwise)
- V tap/hold for the wheel instead of a new B key.
- 30 health, energy stays on the 100 scale (ChatGPT's 1,000 scaled down, same ratios).
- Unsettled misfires are 1 in 8 wheel spells; Rage halves shields.
