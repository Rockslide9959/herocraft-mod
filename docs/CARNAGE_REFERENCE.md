# Carnage (v0.14.25)

A world boss and the Symbiote power's rival. Package `com.projecthero.mod.carnage`. Config: `config/projecthero_carnage.json` (`CarnageConfig`).

## Arrival

- **Natural spawn** (`CarnageSpawner`):
  - Rolled once a minute per Overworld player, at night, never on Peaceful.
  - Chance per roll: `spawnChancePerMinute` 0.2%, or `symbioteHostChancePerMinute` 0.8% for a Symbiote host.
  - At most one Carnage in the world.
  - Cooldown: 40 min after a meteor and 60 min after a kill.
- **The meteor** (`CrimsonMeteorEntity`):
  - Lands 24-40 blocks from the player.
  - Slants down over about 3 s as a tumbling magma block with forced fire and red smoke particles.
  - On impact: a boom that breaks no blocks, 6 damage and knockback within 5 blocks.
  - Carnage then climbs out (`emerge()`: 2 s, untouchable).
- If no player has been within 96 blocks for 10 minutes, he leaves (is discarded).

## Stats

- **Health:** 700, +250 per extra player within 48 blocks at spawn, capped at 3000.
- **Defence:** 6 armour, 0.6 knockback resistance.
- **Size:** x1.2 scale, slim player model.
- **Regeneration:** 1.5 HP/s, or 3 HP/s while frenzied. It stops for 6 s after fire, writhing or sonic damage.
- **Immune** to poison and wither.

## Moves (`CarnageEntity.Brain`)

- **Claws:** 3 blade-arm hits about 0.3 s apart (10 each).
- **Pounce:** 5-16 blocks. A ballistic leap (`AbilityHelpers.ballisticLaunch`) that lands for 12 damage plus knockback within 2.5 blocks.
- **Tendril Lash:** up to 4 players within 10 blocks and in sight. 7 damage each, and they are dragged in.
  - Visual: `SymbioteTendrilEntity.fromBody`, a new `ANCHOR_BODY` that follows him, drawn in crimson.
- **Spike Volley:** 6-24 blocks. A fan of 7 crimson spikes, 5 damage plus Wither II for 3 s each.
  - Uses `SymbioteSpikeEntity.shootCrimson`; the spikes never hit his brood.

## The split

- At 75%, 50% and 25% health:
  - He becomes a cocoon: untouchable and pulsing.
  - He releases `3 + 2 * (players - 1)` Crimson Spawn, capped at 9. Each spawn has 30 HP, 0.55x scale, leaps, and takes double fire damage.
- **Brood all dead:** he bursts out **staggered** (x1.5 damage for 5 s).
- **Brood alive after `reabsorbAfterTicks` (400):** each surviving spawn is reabsorbed and heals him 4% of max health.
- **After the third split he frenzies:** +30% speed and cooldowns x0.6.

## Weaknesses

- **Fire:** x2 damage. The first hit makes him writhe and flinch (80-tick cooldown) and locks his regeneration.
- **Sound:** whenever `SonicVulnerability.isDisrupted` is true, he writhes for the disruption time (20-60 ticks) and takes x1.5 damage.
  - The triggers come from `SonicTriggers`: bells, goat horns, a Warden's sonic boom.
- **Explosions:** x1.25 damage.

## Rewards

- **Crimson Biomass:** 1-3, +1 per extra player within 32 blocks.
  - A Symbiote host can absorb it (`SymbioteVitalsManager.restoreBiomass`): Biomass to full, plus 60 s of Strength II, Speed and Regeneration. 30 s cooldown.
  - Non-hosts can't use it.
- 300 XP.

## Commands

`/projecthero raid start carnage` drops a meteor 12-20 blocks away. `end` removes every Carnage and Crimson Spawn. Spawn eggs exist for both.

## Skin credit

`textures/entity/carnage/carnage.png` is [carnage](https://www.minecraftskins.com/skin/24048046/carnage/) by TheTickanatorDSSX (The Skindex).

The crimson tendril and spike textures are luminance-mapped recolours of the Symbiote ones (`scratchpad/gen_syndicate_assets.js` makes the item textures).
