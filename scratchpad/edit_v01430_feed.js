const fs=require('fs');const f='src/main/java/com/projecthero/mod/ironman/IronManAutoFeed.java';
let s=fs.readFileSync(f,'utf8').replace(/\r\n/g,'\n');
function rep(a,b){if(s.split(a).length!==2)throw 'nomatch: '+a;s=s.replace(a,b);}
rep(` * v0.14.26: an Iron Man suit feeds its wearer. Every two seconds, if you're hungry enough that a meal won't be wasted
 * (or you're down to three drumsticks), the suit takes the most filling safe food in your inventory and feeds it to
 * you -- bowls and bottles come back.`,
` * v0.14.26: an Iron Man suit feeds its wearer. v0.14.30: every two seconds, whenever your hunger bar is below full
 * (even at 19/20), the suit feeds you the best safe food in your inventory -- most nutrition first, then most
 * saturation -- and bowls and bottles come back.`);
rep(`		int bestNutrition = 0;
`,`		int bestNutrition = 0;
		float bestSaturation = -1f;
`);
rep(`			int n = s.get(DataComponents.FOOD).nutrition();
			// a meal is eaten once it fits without waste, or straight away when the bar is nearly empty
			if ((food + n <= 20 || food <= 6) && n > bestNutrition) {
				best = s;
				bestNutrition = n;
`,`			FoodProperties fp = s.get(DataComponents.FOOD);
			int n = fp.nutrition();
			// v0.14.30: no waste rule any more -- any missing hunger point means the best food goes in
			if (n > bestNutrition || (n == bestNutrition && fp.saturation() > bestSaturation)) {
				best = s;
				bestNutrition = n;
				bestSaturation = fp.saturation();
`);
fs.writeFileSync(f,s.replace(/\n/g,'\r\n'));
// test: at 19/20 it still eats the best food
const T='src/gametest/java/com/projecthero/mod/gametest/IronManAutoFeedV01429GameTests.java';
let t=fs.readFileSync(T,'utf8').replace(/\r\n/g,'\n');
const anchor='\t@GameTest(template = EMPTY_STRUCTURE)\n\tpublic void autoFeedReturnsBowls';
if(t.split(anchor).length!==2)throw 'test anchor';
t=t.replace(anchor,`	/** v0.14.30: no waste rule -- at 19/20 the suit still feeds the best food it can find. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void autoFeedEatsTheBestFoodWheneverNotFull(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_iii");
		p.getInventory().add(new ItemStack(Items.APPLE, 2));
		p.getInventory().add(new ItemStack(Items.BREAD, 2));
		p.getInventory().add(new ItemStack(Items.COOKED_PORKCHOP, 2));
		p.getInventory().add(new ItemStack(Items.COOKED_BEEF, 2));
		p.getFoodData().setFoodLevel(19);
		h.assertTrue(IronManAutoFeed.feedNow(p), "19/20 is hungry enough to eat");
		h.assertTrue(p.getFoodData().getFoodLevel() == 20, "hunger topped up, got " + p.getFoodData().getFoodLevel());
		// cooked porkchop and steak tie on nutrition (8) and saturation, so exactly one of them went
		int meat = p.getInventory().countItem(Items.COOKED_PORKCHOP) + p.getInventory().countItem(Items.COOKED_BEEF);
		h.assertTrue(meat == 3, "the best food (an 8-hunger meat) was eaten, not the apple or bread");
		h.assertTrue(p.getInventory().countItem(Items.APPLE) == 2 && p.getInventory().countItem(Items.BREAD) == 2, "lesser food untouched");
		h.assertFalse(IronManAutoFeed.feedNow(p), "a full bar eats nothing");
		h.succeed();
	}

` + anchor);
fs.writeFileSync(T,t.replace(/\n/g,'\r\n'));
// guide + version
const L='src/main/resources/assets/projecthero/lang/en_us.json';
let l=fs.readFileSync(L,'utf8');
const k='"projecthero.guide.iron_man.auto_feed.body": ';
const i=l.indexOf(k);if(i<0)throw 'lang';const e=l.indexOf('\n',i);
const v="Every suit from the Mark 3 up feeds you from your inventory whenever your hunger bar isn't full -- even at 19 of 20 -- picking the best food you carry (most filling first). Any safe food counts, modded food included, and bowls and bottles come back. It never touches raw meat or fish, food with side effects (rotten flesh, spider eyes, pufferfish), chorus fruit, suspicious stew or golden apples. The Mark 1 and Mark 2 don't feed you.";
const line=l.slice(i,e);const comma=/,\s*$/.test(line)?',':'';
l=l.slice(0,i)+k+JSON.stringify(v)+comma+(line.endsWith('\r')?'\r':'')+l.slice(e);
JSON.parse(l);fs.writeFileSync(L,l);
const G='gradle.properties';let g=fs.readFileSync(G,'utf8');if(!g.includes('version=0.14.29'))throw 'ver';fs.writeFileSync(G,g.replace('version=0.14.29','version=0.14.30'));
console.log('ok');
