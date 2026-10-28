// v0.17.1 Omnitrix raised-watch pose solver: finds ModelPart rotations (ZYX order, as ModelPart.translateAndRotate) for the
// left arm (forearm across the chest, the watch face up toward the eyes) and the right hand resting on the dial.
// node scratchpad/omnitrix_pose_solver.js "[dx,dy,dz]" "[nx,ny,nz]" L hover     (model space: +x = left, +y = down, -z = forward)
function Rx(a){const c=Math.cos(a),s=Math.sin(a);return [[1,0,0],[0,c,-s],[0,s,c]];}
function Ry(a){const c=Math.cos(a),s=Math.sin(a);return [[c,0,s],[0,1,0],[-s,0,c]];}
function Rz(a){const c=Math.cos(a),s=Math.sin(a);return [[c,-s,0],[s,c,0],[0,0,1]];}
function mm(A,B){return A.map((r,i)=>B[0].map((_,j)=>r.reduce((s,_,k)=>s+A[i][k]*B[k][j],0)));}
function mv(A,v){return A.map(r=>r[0]*v[0]+r[1]*v[1]+r[2]*v[2]);}
function R(x,y,z){return mm(Rz(z),mm(Ry(y),Rx(x)));}
const norm=v=>{const l=Math.hypot(...v);return v.map(a=>a/l)};
const dot=(a,b)=>a[0]*b[0]+a[1]*b[1]+a[2]*b[2];
function solve(score){let best=null;
 for(let x=-3.2;x<=1;x+=0.05)for(let y=-1.6;y<=1.6;y+=0.05)for(let z=-3.2;z<=3.2;z+=0.05){const s=score(R(x,y,z));if(!best||s<best.s)best={s,x,y,z};}
 let {x,y,z}=best;for(let st=0.02;st>0.001;st/=2){for(const dx of[-st,0,st])for(const dy of[-st,0,st])for(const dz of[-st,0,st]){const s=score(R(x+dx,y+dy,z+dz));if(s<best.s)best={s,x:x+dx,y:y+dy,z:z+dz};}x=best.x;y=best.y;z=best.z;}
 return best;}
const dT=norm(JSON.parse(process.argv[2]||'[-0.5,0.3,-0.81]'));
const nT=norm(JSON.parse(process.argv[3]||'[0,-1,0.35]'));
const L=+(process.argv[4]||3);
const eye=[0,-4,-3.5];
const left=solve(M=>{const d=mv(M,[0,1,0]),n=mv(M,[1,0,0]);const f=mv(M,[L+1.25,7.1,0]).map((v,i)=>v+[5,2+(+(process.argv[7]||0)),0][i]);const toEye=norm(eye.map((v,i)=>v-f[i]));return (1-dot(d,dT))*3+(1-dot(n,toEye));});
const ML=R(left.x,left.y,left.z);
const drop=+(process.argv[7]||0);const piv=[5,2+drop,0];
const face=mv(ML,[L+1.25,7.1,0]).map((v,i)=>v+piv[i]);
const n=mv(ML,[1,0,0]);
console.log('left',left.x.toFixed(3),left.y.toFixed(3),left.z.toFixed(3),'face',face.map(v=>v.toFixed(2)),'n',n.map(v=>v.toFixed(2)),'d',mv(ML,[0,1,0]).map(v=>v.toFixed(2)));
const hover=+(process.argv[5]||1.2);
const dl=mv(ML,[0,1,0]);const along=+(process.argv[9]||0);const tgt=face.map((v,i)=>v+n[i]*hover+dl[i]*along);
const rp=[-5,2+(+(process.argv[8]||0)),0];
const dist=Math.hypot(...tgt.map((v,i)=>v-rp[i]));
console.log('target',tgt.map(v=>v.toFixed(2)),'dist from right shoulder',dist.toFixed(2));
const reach=+(process.argv[6]||9.5);
const right=solve(M=>{const h=mv(M,[-1,reach,0]).map((v,i)=>v+rp[i]);const e=Math.hypot(...h.map((v,i)=>v-tgt[i]));const palm=mv(M,[1,0,0]);return e*e*0.2+(1+dot(palm,n))*0.5;});
const MR=R(right.x,right.y,right.z);
console.log('right',right.x.toFixed(3),right.y.toFixed(3),right.z.toFixed(3),'hand',mv(MR,[-1,reach,0]).map((v,i)=>(v+rp[i]).toFixed(2)),'palm',mv(MR,[1,0,0]).map(v=>v.toFixed(2)));
