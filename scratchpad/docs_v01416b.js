const fs=require("fs");
function edit(f,pairs){let s=fs.readFileSync(f,"utf8");const crlf=s.includes("\r\n");s=s.replace(/\r\n/g,"\n");
for(const [a,b] of pairs){if(!s.includes(a))throw new Error(f+" missing "+a.slice(0,80));s=s.replace(a,b);}
if(crlf)s=s.replace(/\n/g,"\r\n");fs.writeFileSync(f,s);}
edit("docs/CURSEFORGE_DESCRIPTION.md",[
["  **Sneak +** version, and holding **Left Alt** shows every ability's name on the HUD.\n",
 "  **Sneak +** version, and holding **Left Alt** shows every ability's name on the HUD.\n- **Flight** steers the same way for every hero: **W / S** fly forward / backward along where you look (look up to\n  climb, down to dive), **A / D** strafe, **Space / Sneak** straight up / down, and letting go is a dead hover.\n"],
["fly wherever you look -- **S** brakes to a hover, **Sprint**","fly wherever you look -- **S** flies backward, **Sprint**"]]);
console.log("ok");
