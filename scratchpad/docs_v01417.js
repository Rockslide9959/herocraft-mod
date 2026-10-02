// v0.14.17 (main session) description/reference updates.
const fs=require("fs");
function edit(f,pairs){let s=fs.readFileSync(f,"utf8");const crlf=s.includes("\r\n");s=s.replace(/\r\n/g,"\n");
for(const [a,b] of pairs){if(s.includes(b))continue;if(!s.includes(a))throw new Error(f+" missing "+a.slice(0,80));s=s.replace(a,b);}
if(crlf)s=s.replace(/\n/g,"\r\n");fs.writeFileSync(f,s);}
edit("docs/CURSEFORGE_DESCRIPTION.md",[
["  sprints at ~100 (press V again to end it early).",
 "  sprints at ~100 (press V again to end it early). Run across water and steer on it just like on land."],
["Phase through walls, carry anyone on **N**, and a charged,",
 "Phase through walls, carry anyone on **N** (no cooldown), and a charged,"],
["  lose the suit on death -- and slowly mends itself while it is in there (its durability % shows above your HUD).",
 "  lose the suit on death -- and slowly mends itself while it is in there (its durability % shows above your HUD). Worn,\n  it makes your Speed Mode and Overdrive sprint 50% faster (your walk stays easy to control)."],
["(fast in direct sun, slowly at night, barely underground) once\n  it hasn't been drained for 5 seconds, and fuels twelve cheap moves:",
 "(fast in direct sun, slowly at night, barely underground) all\n  the time, even right after a move, and fuels twelve cheap moves:"],
["files\n  everything into the chests and barrels within 10 blocks",
 "files\n  everything (carried in its hands, flying where it faces) into the chests and barrels within 10 blocks"],
]);
edit("docs/KRYPTONIAN_REFERENCE.md",[
["by every drain (`spendSolar` with a cost > 0, flight 0.1/s, Regeneration III 1/s, kryptonite 5/s, the Solar Flare); the\nbar only refills once `now - lastDrain >= 100` ticks (`SOLAR_REGEN_DELAY`).",
 "by every drain (`spendSolar` with a cost > 0, flight 0.1/s, Regeneration III 1/s, kryptonite 5/s, the Solar Flare).\n**v0.14.17:** the refill delay is gone (`SOLAR_REGEN_DELAY` / `solarRegenPaused` removed): every second `tickSolar` adds\nthe sun's rate minus the running drains (flight, Regeneration III); `lastDrain` is now informational only. Gametests pin\nthe rate per player with `Kryptonian.setSolarGainForTests`."],
["HUD: the Solar Hairline is\na duller gold while the refill waits (`Kryptonian.solarRegenPaused`).",
 "HUD: the Solar Hairline is\nalways the plain gold (green near kryptonite)."],
]);
console.log("docs ok");
