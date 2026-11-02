// tiny exact-replace helper: node rep.js <file> <spec.json>  (spec: [[old,new,count?],...]); fails loudly on a miss; keeps CRLF files CRLF
const fs=require('fs');const [f,spec]=process.argv.slice(2);let s=fs.readFileSync(f,'utf8');const crlf=s.includes('\r\n');
for(let [o,n,c] of JSON.parse(fs.readFileSync(spec,'utf8'))){if(crlf){o=o.replace(/\r?\n/g,'\r\n');n=n.replace(/\r?\n/g,'\r\n');}const k=s.split(o).length-1;if(k===0||(c&&k!==c)||(!c&&k!==1)){console.error('MISS ('+k+'):',o.slice(0,90));process.exit(1);}s=s.split(o).join(n);}
fs.writeFileSync(f,s);console.log('ok',f);
