const fs=require("fs");
function edit(f,pairs){let s=fs.readFileSync(f,"utf8");const crlf=s.includes("\r\n");s=s.replace(/\r\n/g,"\n");
for(const [a,b] of pairs){if(!s.includes(a))throw new Error(f+" missing "+a.slice(0,80));s=s.replace(a,b);}
if(f.endsWith(".json"))JSON.parse(s);if(crlf)s=s.replace(/\n/g,"\r\n");fs.writeFileSync(f,s);}
edit("src/main/resources/assets/projecthero/lang/en_us.json",[["S to brake to a stop,","S to fly backwards,"]]);
edit("src/main/java/com/projecthero/mod/kryptonian/KryptonianFlight.java",[[
" * the client's ({@code KryptonianFlightClient}): true directional flight along the look vector, W to fly, S to brake,",
" * the client's (the shared {@code client.flight.DirectionalFlight}, v0.14.16): true directional flight along the look\n * vector, W to fly, S to fly backwards,"]]);
console.log("ok");
