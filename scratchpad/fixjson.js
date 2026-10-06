// add missing commas between adjacent JSON lines after a union merge, then validate
const fs=require('fs');for(const f of process.argv.slice(2)){let L=fs.readFileSync(f,'utf8').split(/\r?\n/);
for(let i=0;i<L.length-1;i++){let j=i+1;while(j<L.length&&L[j].trim()==='')j++;if(j>=L.length)break;
 if(/["\]}0-9el]\s*$/.test(L[i])&&!/[,\[{]\s*$/.test(L[i])&&/^\s*["{\[]/.test(L[j]))L[i]=L[i].replace(/\s*$/,',');}
const s=L.join('\r\n');JSON.parse(s);fs.writeFileSync(f,s);console.log('ok',f);}
