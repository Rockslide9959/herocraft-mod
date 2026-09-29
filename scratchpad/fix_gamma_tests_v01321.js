const fs=require('fs');
function edit(f, pairs){let raw=fs.readFileSync(f,'utf8');const crlf=raw.includes('\r\n');let s=raw.replace(/\r\n/g,'\n');
 for(const [a,b] of pairs){ if(!s.includes(a)) throw new Error(f+': not found: '+a.slice(0,80)); s=s.replace(a,b);} fs.writeFileSync(f,crlf?s.replace(/\n/g,'\r\n'):s);}
edit('src/main/java/com/projecthero/mod/hulk/GammaOverload.java',[[`	public static boolean overloading(BlockPos reactor) {
		return ACTIVE.containsKey(reactor);
	}`,`	public static boolean overloading(BlockPos reactor) {
		return ACTIVE.containsKey(reactor);
	}

	/** Stop an overload before it goes off (tests -- a real blast would flatten the neighbouring gametests). */
	public static void cancel(BlockPos reactor) {
		ACTIVE.remove(reactor);
	}`]]);
edit('src/gametest/java/com/projecthero/mod/gametest/HulkGameTests.java',[[`	@GameTest(template = EMPTY_STRUCTURE)
	public void drinkingTheGammaSerumGrantsThePower(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.getAbilities().instabuild = false;
		ItemStack serum = new ItemStack(com.projecthero.mod.hulk.item.HulkItems.GAMMA_SERUM);
		ItemStack left = serum.getItem().finishUsingItem(serum, helper.getLevel(), p);
		helper.assertTrue(Hulk.hasPower(p), "the serum gives the Gamma power");
		helper.assertTrue(left.is(Items.GLASS_BOTTLE), "and leaves an empty bottle");
		helper.succeed();
	}`,`	@GameTest(template = EMPTY_STRUCTURE)
	public void drinkingTheGammaSerumDosesButDoesNotGrant(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.getAbilities().instabuild = false;
		ItemStack serum = new ItemStack(com.projecthero.mod.hulk.item.HulkItems.GAMMA_SERUM);
		ItemStack left = serum.getItem().finishUsingItem(serum, helper.getLevel(), p);
		helper.assertFalse(Hulk.hasPower(p), "v0.13.21: the serum alone no longer gives the power");
		helper.assertTrue(com.projecthero.mod.hulk.GammaOverload.isDosed(p), "it doses the drinker");
		helper.assertTrue(left.is(Items.GLASS_BOTTLE), "and leaves an empty bottle");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aDosedPlayerOverloadsTheReactor(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		var at = net.minecraft.core.BlockPos.containing(p.position()).offset(2, 0, 0);
		helper.getLevel().setBlock(at, com.projecthero.mod.hulk.item.HulkItems.GAMMA_REACTOR.defaultBlockState(), 2);
		com.projecthero.mod.hulk.GammaOverload.onReactorUsed(p, at);
		helper.assertFalse(com.projecthero.mod.hulk.GammaOverload.overloading(at), "no serum in the blood: nothing happens");
		com.projecthero.mod.hulk.GammaOverload.setDosed(p, true);
		com.projecthero.mod.hulk.GammaOverload.onReactorUsed(p, at);
		boolean started = com.projecthero.mod.hulk.GammaOverload.overloading(at);
		// never let it actually go off in the test world
		com.projecthero.mod.hulk.GammaOverload.cancel(at);
		helper.assertTrue(started, "dosed: the reactor goes critical");
		helper.assertFalse(com.projecthero.mod.hulk.GammaOverload.isDosed(p), "the dose is spent");
		helper.succeed();
	}`]]);
edit('docs/HULK_REFERENCE.md',[[`| 4 | Origin: Gamma Serum (loot only), rare Gamma Lab ruin with a glowing Gamma Reactor block | v0.13.12 |`,`| 4 | Origin: Gamma Serum (loot only), rare Gamma Lab ruin with a glowing Gamma Reactor block | v0.13.12 |
| - | v0.13.21: the serum only doses you (\`GAMMA_DOSED\` attachment); right-clicking a Gamma Reactor then overloads it (\`hulk/GammaOverload\`: 3 s charge, core blast power 18, ring of 8 power-9 blasts at 14 blocks, power-12 after-blast; BLOCK interaction so drop decay applies). The power is granted at detonation with a 10 s explosion/fall/fire shield, and the Hulk comes out | v0.13.21 |`]]);
console.log('ok');
