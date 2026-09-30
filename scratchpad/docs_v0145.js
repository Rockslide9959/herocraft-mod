// v0.14.5 CurseForge description updates. Idempotent: node scratchpad/docs_v0145.js
const fs = require("fs");
const f = "docs/CURSEFORGE_DESCRIPTION.md";
let s = fs.readFileSync(f, "utf8");
const crlf = s.includes("\r\n");
s = s.replace(/\r\n/g, "\n");
function rep(a, b) {
	if (s.includes(b)) return;
	if (!s.includes(a)) throw new Error("missing: " + a.slice(0, 70));
	s = s.replace(a, b);
}
rep("bond with a living alien Symbiote, or mutate one of 27\nexperimental superpowers.",
	"bond with a living alien Symbiote, or mutate one of 26\nexperimental superpowers.");
rep("- Pistol, Rifle, Shotgun and a scoped Sniper, with recoil,",
	"- Pistol, Rifle, Shotgun and a fully modelled, scoped Sniper, with recoil,");
rep("## 27 Experimental Powers · 162 Abilities", "## 26 Experimental Powers");
rep(`Own up to three at once: all their passives run permanently, and the one you select drives your six ability keys. Each
power has six abilities, passives, and **combos** when the right two are paired.`,
	`Own up to three at once: all their passives run permanently, and the one you select drives your ability keys. Each
power has its own abilities (six keys, most with **H** / **N** extras), passives, and **combos** when the right two are
paired. **Super Regeneration** has no keys at all: it heals 10 HP every 5 ticks, burns off harmful effects in 2 seconds
and holds **three revive charges**, each recharging on its own minute.`);
rep("Cryokinesis · Telekinesis · Teleportation · Super Regeneration · Super Durability · Sonic Scream · Invisibility & Light",
	"Cryokinesis · Telekinesis · Teleportation · Super Regeneration · Sonic Scream · Invisibility & Light");
if (crlf) s = s.replace(/\n/g, "\r\n");
fs.writeFileSync(f, s);
console.log("docs_v0145: ok");
