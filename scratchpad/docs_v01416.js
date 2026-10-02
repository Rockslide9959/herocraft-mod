const fs=require("fs");
function edit(f,pairs){let s=fs.readFileSync(f,"utf8");const crlf=s.includes("\r\n");s=s.replace(/\r\n/g,"\n");
for(const [a,b] of pairs){if(!s.includes(a))throw new Error(f+" missing "+a.slice(0,80));s=s.replace(a,b);}
if(crlf)s=s.replace(/\n/g,"\r\n");fs.writeFileSync(f,s);}
edit("docs/CURSEFORGE_DESCRIPTION.md",[
["- Throw Mjolnir and **recall it from anywhere**, even out of another player's hand or an unloaded chunk. Fly by\n  double-tapping jump.",
 "- Throw Mjolnir and **recall it from anywhere** -- out of a chest, an ender chest, another player's hand or an unloaded\n  chunk -- and it is always the ONE hammer (never a duplicate). Fly by double-tapping jump."],
["  cape, each arriving with its own bolt from the sky. Diamond-level: the three pieces equal a full diamond set.",
 "  a flowing **crimson cape**, each arriving with its own bolt from the sky. Diamond-level: the three pieces equal a full\n  diamond set."],
["  flight with no suit at all.\n",
 "  flight with no suit at all.\n- **Stark Sorting Station:** fill its 54-slot store, press **Sort**, and a little **Sorter Bot** flies out and files\n  everything into the chests and barrels within 10 blocks -- the more chests, the finer it sorts, and it keeps each\n  chest's existing theme. Watch it work in real time.\n"],
["- **Speed Mode** walks at ~20 blocks a second (easy to fight in) and **sprints at ~40**; **Overdrive** hits ~100.",
 "- **Speed Mode** walks at ~20 blocks a second (easy to fight in) and **sprints at ~40**; **Overdrive** walks at ~32 and\n  sprints at ~100 (press V again to end it early)."],
["  Mach Punch, Speed Vortex, a 10-second **Speed Sweep**, Phase through walls,",
 "  Mach Punch, Speed Vortex, a 50-block **Speed Sweep** that doesn't stop until every enemy is hit, Phase through walls,"],
["  lose the suit on death -- and slowly mends itself while it is in there.",
 "  lose the suit on death -- and slowly mends itself while it is in there (its durability % shows above your HUD)."],
["---\n\n## 🤝 Squads",
 "### 🧱 Horde Blocks\nCraft a **Zombie, Skeleton or Spider Horde** block (a 3x3 of rotten flesh / bone blocks / spider eyes) and break it open\nfor **8 waves** and a boss -- harder each tier, with a reward chest that gets richer to match.\n- **Zombie Horde** ends with **the Titan**.\n- **Skeleton Horde:** Bone Runners, Blight Archers, shield-walled Bone Knights, TNT-headed Bone Bombers, Necromancers and\n  Bone Brutes -- then **the Bone Tyrant**, a 7-block lich-king with ten telegraphed attacks over three phases.\n- **Spider Horde:** Hunters, Venom Spitters, Trapdoor Leapers, Acid Bursters, Ironback Brutes, invisible Shadow Stalkers and\n  Broodmothers -- then **the Brood Queen**, a 4,000-health spider queen with nine attacks, web cocoons and acid rain.\n\n---\n\n## 🤝 Squads"],
["The Locator Bar shows their faces across\nthe top of your screen.",
 "The Locator Bar shows their faces across\nthe top of your screen. The leader can switch **friendly fire** on with `/squad friendlyfire on` or the button on the P screen."],
]);
console.log("ok");
