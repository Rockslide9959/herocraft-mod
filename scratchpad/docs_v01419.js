// v0.14.19: Stormbreaker in the CurseForge description.
const fs=require("fs");const f="docs/CURSEFORGE_DESCRIPTION.md";let s=fs.readFileSync(f,"utf8");const crlf=s.includes("\r\n");s=s.replace(/\r\n/g,"\n");
const a="- **The Power of Thor:** +11 melee, +10 hearts, 80% less damage, permanent Regeneration, no fall or lightning damage.\n";
const b=a+"- **Stormbreaker:** craft the Unforged Stormbreaker (4 netherite ingots, a Nether Star, 2 blaze rods) and throw it into\n  **lava in the Nether** to forge it. 14 melee; right-click hurls it through up to 4 enemies with a lightning strike and\n  it flies back to you; **Shift + Right-click opens the Bifrost** and beams you (and anyone beside you) up to 256 blocks.\n  It works as Thor's weapon for his lightning and flight.\n";
if(!s.includes("**Stormbreaker:**")){if(!s.includes(a))throw new Error("anchor");s=s.replace(a,b);}
if(crlf)s=s.replace(/\n/g,"\r\n");fs.writeFileSync(f,s);console.log("ok");
