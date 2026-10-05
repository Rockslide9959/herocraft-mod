// node resolve.js <file> <mode...> ; modes per conflict in order: ours|theirs|both|custom:<text-file>
const fs=require('fs');const [f,...modes]=process.argv.slice(2);
let s=fs.readFileSync(f,'utf8');let i=0;
s=s.replace(/<<<<<<< [^\r\n]*\r?\n([\s\S]*?)=======\r?\n([\s\S]*?)>>>>>>> [^\r\n]*\r?\n/g,(m,a,b)=>{const md=modes[i++]||modes[modes.length-1];
 if(md==='ours')return a; if(md==='theirs')return b; if(md==='both')return a+b; if(md.startsWith('custom:'))return fs.readFileSync(md.slice(7),'utf8'); throw md;});
fs.writeFileSync(f,s);console.log(f,'resolved',i);
