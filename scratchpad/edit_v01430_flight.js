const fs=require('fs');
function edit(f,pairs){let s=fs.readFileSync(f,'utf8').replace(/\r\n/g,'\n');for(const [a,b] of pairs){const n=s.split(a).length-1;if(n!==1)throw f+': '+n+' matches for: '+a.slice(0,80);s=s.replace(a,b);}fs.writeFileSync(f,s.replace(/\n/g,'\r\n'));}
const P='src/main/java/com/projecthero/mod/ironman/';
edit(P+'suit/IronManSuit.java',[
 ['	private final float flightDrainMultiplier;   // scales the tiered base flight energy cost (hover/walk/sprint/supersonic) for this mark\n',
  '	private final float flightDrainMultiplier;   // scales the tiered base flight energy cost (hover/walk/sprint/supersonic) for this mark\n	private final float flatFlightDrainPerSecond; // v0.14.30: > 0 replaces the tiered cost with one flat energy/sec for every kind of flight\n'],
 ['		this.flightDrainMultiplier = b.flightDrainMultiplier;\n','		this.flightDrainMultiplier = b.flightDrainMultiplier;\n		this.flatFlightDrainPerSecond = b.flatFlightDrainPerSecond;\n'],
 ['	public float flightDrainMultiplier() { return flightDrainMultiplier; }\n','	public float flightDrainMultiplier() { return flightDrainMultiplier; }\n	public float flatFlightDrainPerSecond() { return flatFlightDrainPerSecond; }\n'],
 ['		private float flightDrainMultiplier = 1.0f;\n','		private float flightDrainMultiplier = 1.0f;\n		private float flatFlightDrainPerSecond = 0f;\n'],
 ['		public Builder flightDrain(float multiplier) { this.flightDrainMultiplier = multiplier; return this; }\n',
  '		public Builder flightDrain(float multiplier) { this.flightDrainMultiplier = multiplier; return this; }\n		/** v0.14.30: one flat energy/sec drain for all flight (hover, moving, sprinting, supersonic). */\n		public Builder flatFlightDrain(float perSecond) { this.flatFlightDrainPerSecond = perSecond; return this; }\n'],
]);
edit(P+'IronManFlight.java',[[
`	private static float flightCostPerTick(ServerPlayer player, IronManSuit suit, boolean supersonic) {
		float base;`,
`	private static float flightCostPerTick(ServerPlayer player, IronManSuit suit, boolean supersonic) {
		if (suit.flatFlightDrainPerSecond() > 0f) {
			return suit.flatFlightDrainPerSecond() / 20f; // v0.14.30: Mark 2 / 3 = 2/s, Mark 4 / 5 = 1/s, explicit user request
		}
		float base;`]]);
// suits: find each block's flightDrain line by suit section
const S=P+'suit/IronManSuits.java';
let s=fs.readFileSync(S,'utf8').replace(/\r\n/g,'\n');
const want={mark_2:2,mark_iii:2,mark_4:1,mark_v:1};
for(const [id,v] of Object.entries(want)){
  const start=s.indexOf('IronManSuit.Builder.of("'+id+'")');if(start<0)throw id;
  const end=s.indexOf('.build());',start);
  let blk=s.slice(start,end);
  const m=blk.match(/\t+\.flightDrain\([^)]*\)[^\n]*\n/);if(!m)throw 'nodrain '+id;
  blk=blk.replace(m[0], m[0]+`\t\t\t.flatFlightDrain(${v}f) // v0.14.30: all flight drains ${v} energy/sec, explicit user request\n`);
  s=s.slice(0,start)+blk+s.slice(end);
}
fs.writeFileSync(S,s.replace(/\n/g,'\r\n'));
console.log('ok');
