const fs=require('fs');const P='docs/CURSEFORGE_DESCRIPTION.md';let s=fs.readFileSync(P,'utf8');const nl=s.includes('\r\n')?'\r\n':'\n';s=s.replace(/\r\n/g,'\n');
const rep=(a,b)=>{if(!s.includes(a))throw new Error('missing '+a.slice(0,60));s=s.replace(a,b);};
rep(`- **H** calls down lightning and conjures **Thor's Armour** onto you.`,
`- **H** calls down lightning and forges **Thor's Armour** onto you piece by piece -- boots, greaves, then chestplate and
  cape, each arriving with its own bolt from the sky.
- Every move has its own animation, the Beam and Chain Lightning are thick forking bolts, Thunderclap sends a shockwave
  ring across the ground -- and none of it ever hurts your squad.`);
rep(`- Six Turbo modes, each with its own suit: Blast, Strength, Speed,`,`- Six Turbo modes, each with its own suit: Blast, Strength (with Resistance I), Speed,`);
fs.writeFileSync(P,s.replace(/\n/g,nl));console.log('ok');
