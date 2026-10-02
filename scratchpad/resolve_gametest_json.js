// Resolves a conflict in the gametest fabric.mod.json by keeping BOTH sides' entrypoint lines.
const fs=require('fs');const f='src/gametest/resources/fabric.mod.json';let s=fs.readFileSync(f,'utf8').replace(/\r\n/g,'\n');
s=s.replace(/<<<<<<< [^\n]*\n([\s\S]*?)=======\n([\s\S]*?)>>>>>>> [^\n]*\n/g,(m,a,b)=>{
 const lines=(a+b).split('\n').map(l=>l.trim().replace(/,$/,'')).filter(l=>l.startsWith('"'));
 return [...new Set(lines)].map(l=>'      '+l).join(',\n')+'\n';});
// fix missing commas between consecutive entries
s=s.replace(/("com\.projecthero[^"]*")\n(\s*")/g,'$1,\n$2');
JSON.parse(s);fs.writeFileSync(f,s.replace(/\n/g,'\r\n'));console.log('ok');
