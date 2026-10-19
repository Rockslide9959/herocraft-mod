// v0.16.7: docs/MIRACULOUS_RENA_ROUGE.md -- the Mirage wheel. CRLF kept.
const fs = require("fs");
const f = process.argv[2];
let s = fs.readFileSync(f, "utf8");
const lines = s.split("\r\n");
const nl = "\r\n";
const zi = lines.findIndex((l) => l.startsWith("| Z | **Illusion Double**"));
if (zi < 0) throw new Error("no Z row");
lines[zi] = "| Z | **Illusion Double** | fights for 15 s at half her melee damage, mobs after her turn on it, takes 6 mob hits; 30 s | **MIRAGE** (special, v0.16.7: a wheel of 8 illusions, see below) | pick one: the Flute plays 1.2 s, the orange bubble floats 0.8 s and pops into it for **30 s**; **the countdown starts at the pop**; **2 min** cooldown from the pick (twice a transformation); Escape / no pick spends nothing |";
const ti = lines.findIndex((l) => l.startsWith("Bosses (300+ max health"));
if (ti < 0) throw new Error("no bosses line");
const mirage = `

## The Mirage wheel (v0.16.7, \`RenaMirage\`, \`MirageIllusion\`)

Shift+Z (when ready) makes the server send \`RenaMirageMenuPayload\`; the client opens \`RenaMirageWheelScreen\` (an orange
ring of 8 wedges, item icons, name in the middle, a wrapped hint under the ring). Mouse direction + click, or releasing Z
over a wedge, sends \`RenaMirageChoicePayload(ordinal)\`; Escape / closing sends -1 and spends nothing. The server only
accepts a pick within \`MIRAGE_MENU_TICKS\` of the offer (\`RenaRougeMoves.chooseMirage\`). The aim of the aimed ones is
taken at the pick (\`RenaMirage.aimPoint\`, 32 blocks). Every illusion lasts \`MIRAGE_TICKS\` (30 s); when it ends it
shatters in an orange flash and every mob it fooled is dazed 3 s (Slowness II + no target).

**Bosses are never immune** (user). A boss that can't really walk (movement speed <= 0.01, e.g. Galactus) stops attacking
instead (its target is refused for as long). Target refusals: \`PunisherControl.suppressTarget\` and
\`RenaMirage.refusesTarget\`, both asked from \`MobMindLockMixin\` at \`Mob#setTarget\` (a Mirage refusal keeps the mob's
current target if that one is allowed, so a mirrored mob stays on the mob it fights).

| # | Illusion | Server effect | Visual |
|---|---|---|---|
| 1 | **Army of Foxes** | the classic Mirage (5 copies that swing when she swings, a 9x4 fake wall, monsters within 20 confused) + 8 \`RenaFoxEntity\` that chase the nearest enemy within 16, bite for 6 (\`RenaRougeCombat.strike\`, never her / squad / pets), pull the bitten onto themselves, take 3 mob hits (a player's at once). A copy hit by anything bursts: Blindness 3 s + Nausea 5 s (a player) or 5 s no target + wander (a mob) | vanilla fox model, orange, see-through, shimmering |
| 2 | **Monster** | every mob within 24 of the beast: no target, runs 16 blocks away (every 0.5 s) | \`MirageFxEntity.MONSTER\`: the fox model x8, rearing, translucent orange |
| 3 | **Vanishing Act** | mobs within 32 that were after her / her squad drop them and can't pick them again unless one of them hit that mob within 5 s; 3 RUNNER copies sprint off (behind her and to the sides) and take the aggro | runners + a puff |
| 4 | **Mirage Zone** | radius 12 at the aim: mobs inside drop targets and walk out; mobs outside have a path / chase into it cut and are nudged back | \`ZONE\`: glowing disc, cracks, 34 flame sprites, client smoke / flame / lava particles |
| 5 | **Golden Lure** | every mob within 30 of the prize: no target, walks to it, stares | \`LURE\`: a rotating heap of gold items in a halo |
| 6 | **Pitfall** | a real 4x4 pit, 3 deep, at the aim, through \`GeoTerrain.pocket\` (natural ground only, \`GeoTerrain.canEdit\` build rules, never under her / a squadmate); mobs within 16 walk for it and are nudged in at the edge; she / squadmates who fall in are lifted to the rim; filled back bottom-up with \`restoreNow\` at the end (what fell in is lifted out); the ledger also restores on server stop | \`PIT\`: the ground block drawn over the open cells + a faint orange shimmer |
| 7 | **Eclipse** | a 10-radius night following her: enemies inside drop and can't pick anything farther than 4 blocks; their projectiles (\`ENTITY_LOAD\`) fly up to 25 degrees off | \`ECLIPSE\`: a dark dome with stars |
| 8 | **Hall of Mirrors** | enemies within 20 at the cast are mirrored: each one is kept on the nearest other mirrored mob (bosses through \`taunt\`), they can't pick her / her squad; a lone one just stays off her | \`MIRRORS\`: 8 turning glass panels with orange frames |

Performance: the work runs every 5 / 10 ticks over one box query (the mirrors reassert every tick, but only search for a
mob whose fight broke off); the big visuals are one client-drawn \`MirageFxEntity\`; server particles are count + spread.
`;
lines.splice(ti + 1, 0, ...mirage.split("\n"));
s = lines.join(nl);
s = s.replace("`rr_pounce`, `rr_double_until`, `rr_mirage_until`, `rr_wall_until`.", "`rr_pounce`, `rr_double_until`, `rr_mirage_until`, `rr_mirage_kind` (v0.16.7), `rr_wall_until`. `RenaMirage.RUNNING` (v0.16.7) is dropped the same way.");
s = s.replace("- `projecthero.mixins.json`: `RenaRougeFakeWallSightMixin`", "- v0.16.7: `MobMindLockMixin` asks `RenaMirage.refusesTarget`; `HeroPackGuide` adds the `mirage*` sections to her chapter.\r\n- `projecthero.mixins.json`: `RenaRougeFakeWallSightMixin`");
s = s.trimEnd() + nl + nl + "`RenaMirageGameTests` (12, v0.16.7, each in its own batch): Escape spends nothing, a pick costs 2 min and the pop starts the" + nl +
	"30 s and the countdown, one per illusion with a boss among the mobs (Army of Foxes: 8 foxes, 6 a bite, never the squad," + nl +
	"a copy bursts and blinds; Pitfall dug and filled back exactly, never under a squadmate), the end dazes, detransforming ends it." + nl;
fs.writeFileSync(f, s);
console.log("ok");
