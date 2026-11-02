# Omnitrix revamp: Big Chill

Status: **BUILT (v0.17.2)**. User asks: "can you work on revamps for the rest of the alien force aliens please, look at their powers
and abilities in the show and do research and make them good please"; "delete all the alien models and only use the skins i
provided please no extra things untill i say"; and from the earlier revamps, "dont add ultimate aliens yet". So there is no
Ultimate Big Chill, no blue cold-fire and no plasma beams. Code: `omnitrix/alien/bigchill/BigChillKit.java` and
`client/omnitrix/alien/BigChillClient.java`. Tests: `OmnitrixBigChillGameTests` (7). Screenshots: `docs/screenshots/v0172/big_chill/`.

## Research

ben10.fandom wouldn't fetch, so the sources are fan-wiki search snippets plus memory of the show. They agree that Big Chill is a
**Necrofriggian** from Kylmyys, a moth-like "ghost of the ice" who debuted in Alien Force. He has:
- **freezing breath** that freezes foes solid and puts out fires *(recurring)*
- **intangibility**: he phases through walls, and phases *into* foes or machines to freeze them from the inside, a signature move *(recurring)*
- **flight** on moth wings
- wings that **wrap round him like a cloak**, so he blends into the dark and goes unseen *(recurring)*
- **immunity to cold**

He is light-bodied, and heat and fire are his weakness, which is a common fan-wiki note and a gameplay counter. Episode citations
are from memory and marked *(recurring)*. They are deliberately not tied to a specific episode.

## The model

Every extra body part is **gone**: the two wing plates, the inner cloak plates, the antennae with tips, and the glowing eye cubes,
along with the `big_chill_extras.png` sheet. He is only the player model, on the wide-arm model like every alien (this works for
slim players too), in one of the user's two 64x64 skins:
- `big_chill_cloaked.png`: wings wrapped round him. This is his normal look.
- `big_chill.png`: wings open.

The server-synced `F_OPEN` flag picks the skin. It is on while he flies, dives, breathes, storms or fires the ultimate, and for
1.5 s after any attacking move, so a burst of quick moves never flickers. A soft frost ring and a wingbeat mark each swap. The Cloak
always shows the wrapped skin. Phased or cloaked he is drawn see-through. The Omnitrix chest badge stays, and so do the move VFX.

## Kit (built)

The engine is **chill → frozen solid → shatter**. Chilled means slowed and frosted over. A chilled creature hit by more cold
freezes solid: it is rooted in an ice shell (bosses for a quarter of the time). Shatter cashes that in. Every cold move does 25%
less in the heat (see weaknesses).

| Key | Move | What it does | Numbers |
|---|---|---|---|
| R | Ice Shard | a spinning shard of ice (rendered spike projectile), chills | 7, cd 0.6 s |
| Shift+R (hold) | Freezing Breath | hold up to 2.5 s: a cone whose reach grows from 4 to 8 blocks over 1.5 s. Every pulse chills. From the third pulse they freeze solid. Water under the cone ices over (frosted ice that thaws by itself) and fire blocks go out | 3 per 0.5 s (up to 15), cd 6 s from the release |
| G | Frost Grip | seizes the creature in reach and freezes it solid | 13, frozen 3 s, reach 4.5, cd 7 s |
| Shift+G | Shatter | every frozen-solid creature within 8 that he can see bursts out of its ice (block shards). The flying ice cuts anything within 2. Refused with no cooldown if nothing is frozen | 16, splash 5, cd 8 s |
| X | Moth Flight | take off / land (double-tap jump works too); wings open through the lift-off | cd 0.5 s |
| Shift+X | Phase | intangible 5 s: walls and physical harm pass through him. **Phantom Chill**: passing through a creature freezes it from the inside, once per creature per Phase. Can't start while burning | 8, frozen 2 s; cd 10 s after it ends |
| Z | Ice Dive | climb, then plunge: frost shock ring, crack decal, ice shards, camera shake, water ices over | 17 + chilled in r 4, cd 10 s |
| Shift+Z | **Absolute Zero (ULT)** | 1 s drawing the cold in (rooted, blue vignette), then an ice dome: everything near is frozen solid, and water freezes and fire dies in r 8 | 55 at the centre, 40 at the 8-block edge; frozen 4 s; cd 60 s |
| C | Blizzard | a 4 s snowstorm wheeling round him (rendered bands) | 2 per 0.5 s (16) + chilled in r 6, cd 15 s |
| Shift+C | Frost Field | frost spreads over the ground he looks at, within 12 blocks (ice decal, water ices over) | 6 + chilled as it forms, then 1 + chilled per 0.5 s, r 4, 8 s, cd 16 s |
| V | Cloak | wings wrap round: invisible 10 s and hunters lose track. Striking out of it is an **Ambush**. Attacking ends it | Ambush +6, frozen 2 s; cd 20 s |
| Shift+V | Frost Armour | an ice shell: less damage taken, and attackers within 4 are chilled | -40% for 6 s, cd 15 s |
| Attack | Frost Touch | blows chill; an already-chilled creature freezes solid | frozen 2 s |

Damage check: quick 7 (Shard), 3 a pulse; normal 13 to 17 (Grip, Shatter 16, Dive 17); the Breath is 15 at most; the ultimate
55/40.

### Passives
- **Moth wings:** flight, no fall damage, and the skin swap described above.
- **Cold-blooded:** freezing and powder snow never hurt or freeze him.
- **Frost Mend (new):** on snow or ice, in powder snow, or in a snowy biome he heals 1 every 2 s.
- He has no armour of his own; he only gets the hidden Resistance I every alien has. Health is 28.

### Weaknesses
- **Fire:** fire and lava do x1.5. Burning breaks Phase and Cloak, and he can't start either while alight ("the heat holds you solid").
- **Heat (new):** in the Nether or a desert / badlands biome (base temperature 1.5 or more) his cold moves do 25% less.
- **Slight body:** 28 health with no armour. Phasing only stops physical harm, so fire and magic still reach him.

## Conventions kept
Static per-player maps (`PHASED_THROUGH`, `LAST_TICK`) are cleared on revert and in `clearSessionState`. Timed pulses (the breath,
blizzard, field) are guarded against double ticks. Lang covers `projecthero.omnitrix.big_chill.<move>` + `.desc`, the guide entry,
the role and passive/weakness lines, and three action-bar messages.
