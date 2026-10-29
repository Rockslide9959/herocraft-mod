// v0.17.1 third-person button press: the right arm's rotations (ZYX, as ModelPart.translateAndRotate) that put the right
// fingertips on the Omnitrix's side button while the left arm holds the raised-watch pose (OmnitrixPose.WATCH_ARM, its shoulder
// dropped 6 px). Model space: +x = left, +y = down, -z = forward. The button sits on the cuff's front (-z) side in the left
// arm's space at ((L-1)/2, 7.1, -2.93), its lens facing -z.
// node scratchpad/omnitrix_button_pose_solver.js [hover px] [reach px] [L]
function Rx(a){const c=Math.cos(a),s=Math.sin(a);return [[1,0,0],[0,c,-s],[0,s,c]];}
function Ry(a){const c=Math.cos(a),s=Math.sin(a);return [[c,0,s],[0,1,0],[-s,0,c]];}
function Rz(a){const c=Math.cos(a),s=Math.sin(a);return [[c,-s,0],[s,c,0],[0,0,1]];}
function mm(A,B){return A.map((r,i)=>B[0].map((_,j)=>r.reduce((s,_,k)=>s+A[i][k]*B[k][j],0)));}
function mv(A,v){return A.map(r=>r[0]*v[0]+r[1]*v[1]+r[2]*v[2]);}
function R(x,y,z){return mm(Rz(z),mm(Ry(y),Rx(x)));}
const norm=v=>{const l=Math.hypot(...v);return v.map(a=>a/l)};
const dot=(a,b)=>a[0]*b[0]+a[1]*b[1]+a[2]*b[2];
const REF=[-0.615,0.489,-1.266], REG=+(process.argv[5]||0);
function solve(score0){let best=null;const score=(M,x,y,z)=>score0(M)+REG*((x-REF[0])**2+(y-REF[1])**2+(z-REF[2])**2);
 for(let x=-3.2;x<=1;x+=0.05)for(let y=-1.6;y<=1.6;y+=0.05)for(let z=-3.2;z<=3.2;z+=0.05){const s=score(R(x,y,z),x,y,z);if(!best||s<best.s)best={s,x,y,z};}
 let {x,y,z}=best;for(let st=0.02;st>0.001;st/=2){for(const dx of[-st,0,st])for(const dy of[-st,0,st])for(const dz of[-st,0,st]){const s=score(R(x+dx,y+dy,z+dz),x+dx,y+dy,z+dz);if(s<best.s)best={s,x:x+dx,y:y+dy,z:z+dz};}x=best.x;y=best.y;z=best.z;}
 return best;}
const hover=+(process.argv[2]||1.2), reach=+(process.argv[3]||10.5), L=+(process.argv[4]||3);
const ML=R(-2.169,0.040,-1.809), piv=[5,2+6,0];
const btn=mv(ML,[(L-1)/2,7.1,-2.93]).map((v,i)=>v+piv[i]);
const nb=norm(mv(ML,[0,0,-1]));
const tgt=btn.map((v,i)=>v+nb[i]*hover);
const rp=[-5,2,0];
const right=solve(M=>{const h=mv(M,[-1,reach,-1]).map((v,i)=>v+rp[i]);const e=Math.hypot(...h.map((v,i)=>v-tgt[i]));const f=norm(mv(M,[0,1,0]));const palm=mv(M,[1,0,0]);
 return e*e*0.3+(1-dot(f,nb.map(v=>-v)))*0.15+(1-dot(palm,[0,1,0]))*0.15;});
const MR=R(right.x,right.y,right.z);
console.log('button',btn.map(v=>v.toFixed(2)),'normal',nb.map(v=>v.toFixed(2)),'target',tgt.map(v=>v.toFixed(2)));
console.log('right',right.x.toFixed(3),right.y.toFixed(3),right.z.toFixed(3),'tip',mv(MR,[-1,reach,-1]).map((v,i)=>(v+rp[i]).toFixed(2)),'score',right.s.toFixed(3));
