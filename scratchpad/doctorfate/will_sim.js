// v0.17.4 will test tuning: a bot (reaction delay in ticks, look-ahead lead in ticks) against the hold-to-rise physics and band (mirrors DoctorFateConfig). node will_sim.js
const RISE=0.0052,SINK=0.0024,SPA=0.00001,DAMP=0.9,BOUNCE=0.3;
function run(anger,lead,delay,seed){
 let r=seed; const rnd=()=>{r=(r*16807)%2147483647;return r/2147483647;};
 let w=0.5,v=0,band=0.5,from=0.5,to=0.5,bt=0,dur=1,prog=0,out=0,floor=0,ticks=0,maxOut=0,nextL=100+rnd()*120,warn=0;
 const a=anger/100, half=0.15-0.05*a; const hist=[];
 while(ticks<2400){ticks++;
  // band
  if(warn>0){warn--; if(warn==0){from=band; to = (band-0.22 > 0.80-band) ? 0.22+rnd()*(band-0.25-0.22) : band+0.25+rnd()*(0.80-band-0.25);bt=0;dur=5; nextL=ticks+ (160+rnd()*120)*(1-0.4*a);} }
  else { if(ticks>=nextL){warn=10; nextL=1e9;} bt++; if(bt>=dur){from=band;to=0.22+rnd()*0.58;bt=0;dur=Math.round((50+rnd()*40)*(1-0.35*a));}
   const s=Math.min(1,bt/dur); band=from+(to-from)*s*s*(3-2*s);}
  // bot sees state delay ticks ago
  const past=hist[Math.max(0,hist.length-delay)]||{w,v,band};
  const held = past.w+past.v*lead < past.band;
  v=(v+(held?RISE:0)-(SINK+SPA*anger))*DAMP; w+=v;
  if(w<0){w=0;v=-v*BOUNCE} if(w>1){w=1;v=-v*BOUNCE}
  hist.push({w,v,band});
  if(Math.abs(w-band)<=half){prog++;out=0}else{prog=Math.max(0,prog-0.5);out++}
  maxOut=Math.max(maxOut,out); floor = w<=0.02?floor+1:0;
  if(prog>=200) return {ok:true,ticks,maxOut};
  if(out>=100||floor>=40) return {ok:false,ticks,maxOut};
 }
 return {ok:false,ticks,maxOut,prog};
}
for (const anger of [0,50,90]) for (const delay of [1,4,6,8]) for (const lead of [0,6,10]) {
 let ok=0,t=0,mo=0; for(let s=1;s<=40;s++){const x=run(anger,lead,delay,s*7919);if(x.ok){ok++;t+=x.ticks} mo=Math.max(mo,x.maxOut)}
 console.log(`anger ${anger} delay ${delay} lead ${lead}: ${ok}/40 avg ${ok?Math.round(t/ok):'-'} ticks maxOut ${mo}`);
}
