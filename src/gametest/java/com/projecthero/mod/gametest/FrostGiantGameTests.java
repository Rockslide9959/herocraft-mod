package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.event.EventTypes;
import com.projecthero.mod.frostgiant.FrostBestiary;
import com.projecthero.mod.frostgiant.FrostCleanup;
import com.projecthero.mod.frostgiant.FrostCombat;
import com.projecthero.mod.frostgiant.FrostGiantAnims;
import com.projecthero.mod.frostgiant.FrostGiantAssault;
import com.projecthero.mod.frostgiant.FrostGiantConfig;
import com.projecthero.mod.frostgiant.FrostGiantEntityTypes;
import com.projecthero.mod.frostgiant.FrostGiantRewards;
import com.projecthero.mod.frostgiant.FrostGiants;
import com.projecthero.mod.frostgiant.block.FrostGiantBlocks;
import com.projecthero.mod.frostgiant.block.JotunheimRuneStoneBlock;
import com.projecthero.mod.frostgiant.entity.FrostGiantWarlordEntity;
import com.projecthero.mod.frostgiant.entity.IceCasketEntity;
import com.projecthero.mod.frostgiant.entity.JotunRaiderEntity;
import com.projecthero.mod.frostgiant.entity.KingLaufeyEntity;
import com.projecthero.mod.frostgiant.entity.RimeHoundEntity;
import com.projecthero.mod.frostgiant.entity.RuneBrazierEntity;
import com.projecthero.mod.frostgiant.item.FrostGear;
import com.projecthero.mod.frostgiant.item.FrostGiantItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.16.1: the Frost Giant Assault. Like the Ultron tests, the timed raid is never run end to end: each test drives one
 * piece directly inside its own space -- braziers 3 blocks out, arrivals land 2 blocks from the centre without the beam,
 * no snowfall -- and every assault is aborted / finished and removed before the test ends. Tests that start an assault each
 * get their own batch (only one may run within 256 blocks of another). Monsters need a non-Peaceful world; mock players
 * report creative, so they are switched to Survival to count as fighters.
 */
public class FrostGiantGameTests implements FabricGameTest {

	private static FrostGiantAssault startPinned(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		FrostGiantAssault a = FrostGiants.start(helper.getLevel(), at, null, x -> {
			x.brazierRingOverride = 3.0;
			x.arrivalRadiusOverride = 2.0;
			x.instantArrivals = true;
			x.noSnow = true;
		});
		helper.assertTrue(a != null, "the assault starts");
		return a;
	}

	private static void end(ServerLevel level, FrostGiantAssault a) {
		if (a != null && a.state().active()) {
			a.abort(level);
		}
		if (a != null) {
			EventSavedData.get(level).remove(a.id());
		}
	}

	private static ServerPlayer survivor(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		return p;
	}

	// ---------------------------------------------------------------- registration, stats, damage

	@GameTest(template = EMPTY_STRUCTURE)
	public void frostGiantsAreRegisteredWithTheirRecipes(GameTestHelper helper) {
		helper.assertTrue(EventTypes.isRegistered(FrostGiantAssault.TYPE_ID), "frost_giants is a registered event type");
		helper.assertTrue(EventTypes.create(FrostGiantAssault.TYPE_ID, UUID.randomUUID()) instanceof FrostGiantAssault, "loads back as an assault");
		var recipes = helper.getLevel().getRecipeManager();
		for (String r : new String[] { "jotunheim_rune_stone", "jotunheim_rune_stone_from_shard", "rime_forging" }) {
			helper.assertTrue(recipes.byKey(ProjectHeroMod.id(r)).isPresent(), "recipe " + r);
		}
		boolean heart = false;
		for (var ing : recipes.byKey(ProjectHeroMod.id("jotunheim_rune_stone")).orElseThrow().value().getIngredients()) {
			heart |= ing.test(new ItemStack(Items.HEART_OF_THE_SEA));
		}
		helper.assertTrue(heart, "the Rune Stone is built round a Heart of the Sea");
		boolean shard = false;
		for (var ing : recipes.byKey(ProjectHeroMod.id("jotunheim_rune_stone_from_shard")).orElseThrow().value().getIngredients()) {
			shard |= ing.test(new ItemStack(FrostGiantItems.RIME_SHARD));
		}
		helper.assertTrue(shard, "repeat runs: a Rime Shard in place of the Heart of the Sea");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void unitsHaveTheirStatsAndSizes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RimeHoundEntity hound = FrostGiantEntityTypes.HOUND.create(level);
		JotunRaiderEntity raider = FrostGiantEntityTypes.RAIDER.create(level);
		var shaman = FrostGiantEntityTypes.SHAMAN.create(level);
		var brute = FrostGiantEntityTypes.BRUTE.create(level);
		FrostGiantWarlordEntity warlord = FrostGiantEntityTypes.WARLORD.create(level);
		KingLaufeyEntity laufey = FrostGiantEntityTypes.LAUFEY.create(level);
		helper.assertTrue(hound.getMaxHealth() == 40f && Math.abs(hound.getBbHeight() - 1.6f) < 0.01f, "hound 40 health, 1.6 tall");
		helper.assertTrue(raider.getMaxHealth() == 140f && raider.getAttributeValue(Attributes.ARMOR) == 6.0 && Math.abs(raider.getBbHeight() - 4.5f) < 0.01f,
				"raider 140 / 6 armour / 4.5 tall");
		helper.assertTrue(shaman.getMaxHealth() == 160f && Math.abs(shaman.getBbHeight() - 5.0f) < 0.01f, "shaman 160 / 5 tall");
		helper.assertTrue(brute.getMaxHealth() == 450f && brute.getAttributeValue(Attributes.ARMOR) == 12.0 && Math.abs(brute.getBbHeight() - 7.0f) < 0.01f,
				"brute 450 / 12 armour / 7 tall");
		helper.assertTrue(Math.abs(warlord.getBbHeight() - 11.0f) < 0.01f && Math.abs(laufey.getBbHeight() - 14.0f) < 0.01f, "warlord 11, Laufey 14");
		warlord.configure(1);
		helper.assertTrue(warlord.getMaxHealth() == 1200f, "Warlord solo 1200, got " + warlord.getMaxHealth());
		warlord.configure(3);
		helper.assertTrue(warlord.getMaxHealth() == 1800f, "+300 per extra fighter, got " + warlord.getMaxHealth());
		laufey.configure(1);
		helper.assertTrue(laufey.getMaxHealth() == 2500f && laufey.getHealth() == 2500f, "Laufey solo 2500");
		laufey.configure(3);
		helper.assertTrue(laufey.getMaxHealth() == 3700f, "+600 per extra fighter, got " + laufey.getMaxHealth());
		laufey.configure(50);
		helper.assertTrue(laufey.getMaxHealth() == 6000f, "capped at 6000, got " + laufey.getMaxHealth());
		helper.assertTrue(laufey.getAttributeValue(Attributes.ARMOR) == 14.0 && laufey.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 8.0,
				"Laufey 14 armour / 8 toughness");
		helper.assertTrue(raider.isAlliedTo(laufey) && hound.isAlliedTo(brute), "Jotunheim is one side");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fireHurtsHalfAgainLightningAQuarterAndGiantsShrugOffTheCold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		JotunRaiderEntity raider = helper.spawn(FrostGiantEntityTypes.RAIDER, new BlockPos(2, 2, 2));
		raider.setNoAi(true);
		raider.getAttribute(Attributes.ARMOR).setBaseValue(0);
		float h0 = raider.getHealth();
		raider.hurt(level.damageSources().lightningBolt(), 8f);
		helper.assertTrue(Math.abs(h0 - raider.getHealth() - 10f) < 0.01f, "lightning x1.25: lost " + (h0 - raider.getHealth()));
		raider.invulnerableTime = 0;
		float h1 = raider.getHealth();
		raider.hurt(level.damageSources().inFire(), 4f);
		helper.assertTrue(Math.abs(h1 - raider.getHealth() - 6f) < 0.01f, "fire x1.5: lost " + (h1 - raider.getHealth()));
		raider.invulnerableTime = 0;
		float h2 = raider.getHealth();
		raider.hurt(level.damageSources().generic(), 4f);
		helper.assertTrue(Math.abs(h2 - raider.getHealth() - 4f) < 0.01f, "ordinary damage x1, lost " + (h2 - raider.getHealth()));
		helper.assertTrue(FrostCombat.multiplier(level.damageSources().lava()) == 1.5f, "lava counts as fire");
		helper.assertFalse(raider.canFreeze(), "giants never freeze");
		helper.assertFalse(raider.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100)), "or slow down");
		helper.assertTrue(raider.isInvulnerableTo(level.damageSources().fall()), "or take fall damage");
		RimeHoundEntity hound = helper.spawn(FrostGiantEntityTypes.HOUND, new BlockPos(5, 2, 5));
		hound.setNoAi(true);
		helper.assertTrue(raider.isInvulnerableTo(level.damageSources().mobAttack(hound)), "giants' hits never hurt each other");
		raider.discard();
		hound.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- waves

	@GameTest(template = EMPTY_STRUCTURE)
	public void waveMixMatchesTheSpecAndScalesWithFighters(GameTestHelper helper) {
		helper.assertTrue(FrostGiantAssault.waveCount() == 8, "eight waves");
		int[][] spec = { { 6, 0, 0, 0, 0 }, { 4, 3, 0, 0, 0 }, { 4, 3, 2, 0, 0 }, { 3, 3, 2, 1, 0 }, { 3, 2, 2, 1, 1 },
				{ 4, 4, 2, 2, 0 }, { 3, 4, 3, 2, 0 }, { 3, 3, 3, 2, 2 } };
		for (int n = 1; n <= 8; n++) {
			helper.assertTrue(java.util.Arrays.equals(FrostGiantAssault.composition(n), spec[n - 1]),
					"wave " + n + " solo mix, got " + java.util.Arrays.toString(FrostGiantAssault.composition(n)));
		}
		helper.assertTrue(FrostGiantAssault.scaled(4, 1) == 4 && FrostGiantAssault.scaled(4, 3) == 8 && FrostGiantAssault.scaled(3, 2) == 5,
				"round(base x (1 + 0.5 x (fighters - 1)))");
		helper.assertTrue(FrostGiantAssault.scaled(0, 4) == 0, "an empty entry stays empty");
		helper.assertTrue(FrostGiantConfig.waves().enemyCap == 16, "never more than 16 giants at once");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "frost_start", timeoutTicks = 40)
	public void assaultStartsLightsItsBraziersAndEnds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FrostGiantAssault a = startPinned(helper);
		try {
			helper.assertTrue(a.state() == EventState.RUNNING && a.phase() == FrostGiantAssault.Phase.COUNTDOWN, "running, counting down");
			helper.assertTrue(a.liveBraziers(level).size() == 4 && a.litBraziers(level) == 4, "four lit braziers, got " + a.litBraziers(level));
			var anchor = level.getBlockState(a.center());
			helper.assertTrue(anchor.is(FrostGiantBlocks.RUNE_STONE) && anchor.getValue(JotunheimRuneStoneBlock.ACTIVE), "the Rift Anchor is lit");
			helper.assertTrue(FrostGiants.start(level, helper.absolutePos(new BlockPos(5, 1, 5)), null) == null, "a second one nearby is refused");
			a.startWave(level, 4);
			int f = a.fighterCount();
			int[] live = a.liveCounts(level);
			int[] mix = FrostGiantAssault.composition(4);
			int expected = 0;
			for (int i = 0; i < mix.length; i++) {
				expected += i == FrostGiantAssault.WARLORD ? mix[i] : FrostGiantAssault.scaled(mix[i], f);
			}
			int have = 0;
			for (int n : live) {
				have += n;
			}
			helper.assertTrue(have + a.owed() == expected, "wave 4 brings its full mix: " + java.util.Arrays.toString(live) + " + " + a.owed()
					+ " owed vs " + expected);
			helper.assertTrue(live[FrostGiantAssault.BRUTE] == 1, "the Brute came first");
		} finally {
			end(level, a);
		}
		var after = level.getBlockState(a.center());
		helper.assertTrue(after.is(FrostGiantBlocks.RUNE_STONE) && !after.getValue(JotunheimRuneStoneBlock.ACTIVE), "abort: the anchor goes dormant");
		helper.assertTrue(a.liveBraziers(level).isEmpty(), "the braziers are gone");
		helper.assertTrue(FrostGiantAssault.find(level, a.id()) == null, "nothing of it remains");
		helper.succeed();
	}

	// ---------------------------------------------------------------- braziers and the cold

	@GameTest(template = EMPTY_STRUCTURE, batch = "frost_braziers", timeoutTicks = 40)
	public void braziersWarmGoOutAndRelight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FrostGiantAssault a = startPinned(helper);
		try {
			List<RuneBrazierEntity> braziers = a.liveBraziers(level);
			RuneBrazierEntity b = braziers.get(0);
			ServerPlayer p = survivor(helper);
			p.setPos(b.getX() + 1, b.getY(), b.getZ());
			p.setTicksFrozen(120);
			a.applyCold(level, p, braziers, 1.0, level.getGameTime(), FrostGiantConfig.cold());
			helper.assertTrue(p.getTicksFrozen() < 120, "warm: the frost thaws, got " + p.getTicksFrozen());
			helper.assertTrue(p.hasEffect(MobEffects.REGENERATION), "warm: Regeneration I");
			// every fire out: the cold builds up
			JotunRaiderEntity raider = helper.spawn(FrostGiantEntityTypes.RAIDER, new BlockPos(1, 2, 1));
			raider.setNoAi(true);
			for (RuneBrazierEntity x : braziers) {
				x.invulnerableTime = 0;
				x.hurt(level.damageSources().mobAttack(raider), 100000f);
				helper.assertFalse(x.isLit(), "a brazier at 0 health goes out");
				helper.assertTrue(x.isAlive(), "...but it doesn't break");
			}
			helper.assertTrue(a.litBraziers(level) == 0, "all four out");
			p.setTicksFrozen(0);
			a.applyCold(level, p, braziers, 2.0, level.getGameTime(), FrostGiantConfig.cold());
			helper.assertTrue(p.getTicksFrozen() > 0, "cold: the frost builds, got " + p.getTicksFrozen());
			p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
			helper.assertTrue(FrostGiantAssault.isProtectedFromCold(p), "Fire Resistance keeps the cold off");
			// relight by hand (3 s of holding use)
			b.addRelightProgress(level, p, FrostGiantConfig.braziers().relightTicks);
			helper.assertTrue(b.isLit(), "relit");
			helper.assertTrue(Math.abs(b.getHealth() - b.getMaxHealth() * 0.5f) < 0.5f, "back at half health, got " + b.getHealth());
			b.invulnerableTime = 0;
			helper.assertFalse(b.hurt(level.damageSources().playerAttack(p), 5f), "a fighter's blow passes through a lit brazier");
			raider.discard();
		} finally {
			end(level, a);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "frost_target", timeoutTicks = 40)
	public void giantsGoForLitBraziersAndIgnoreColdOnes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FrostGiantAssault a = startPinned(helper);
		try {
			JotunRaiderEntity raider = (JotunRaiderEntity) a.spawnUnit(level, FrostGiantAssault.RAIDER, null);
			helper.assertTrue(raider != null && a.ownsEntity(raider), "a raider of the assault");
			var target = raider.rethinkTargetForTest(level);
			helper.assertTrue(target instanceof RuneBrazierEntity b && b.isLit(), "no fighter near: it goes for a lit brazier, got " + target);
			JotunRaiderEntity probe = FrostGiantEntityTypes.RAIDER.create(level);
			for (RuneBrazierEntity b : a.liveBraziers(level)) {
				b.invulnerableTime = 0;
				b.hurt(level.damageSources().mobAttack(probe), 100000f);
			}
			var after = raider.rethinkTargetForTest(level);
			helper.assertFalse(after instanceof RuneBrazierEntity, "a cold brazier is never a target, got " + after);
		} finally {
			end(level, a);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- temporary blocks

	private static List<BlockPos> placeTemporaryBlocks(GameTestHelper helper, FrostGiantAssault a) {
		ServerLevel level = helper.getLevel();
		List<BlockPos> placed = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			BlockPos floor = helper.absolutePos(new BlockPos(1 + i * 2, 1, 6));
			level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
			helper.assertTrue(a.tempBlocks().addSnow(level, floor.above(), 2), "a snow layer on the stone");
			placed.add(floor.above());
		}
		BlockPos ice = helper.absolutePos(new BlockPos(4, 3, 7));
		helper.assertTrue(a.placeIce(level, ice, Blocks.PACKED_ICE.defaultBlockState(), -1), "an ice wall block");
		placed.add(ice);
		BlockPos spike = helper.absolutePos(new BlockPos(7, 3, 4));
		helper.assertTrue(a.placeIce(level, spike, Blocks.BLUE_ICE.defaultBlockState(), 600), "a spike block");
		placed.add(spike);
		helper.assertTrue(FrostCleanup.get(level).pending(a.id()) == placed.size(), "each is mirrored into the orphan ledger");
		return placed;
	}

	private static void assertAllGone(GameTestHelper helper, FrostGiantAssault a, List<BlockPos> placed) {
		ServerLevel level = helper.getLevel();
		for (BlockPos p : placed) {
			helper.assertTrue(level.getBlockState(p).isAir(), "temporary block at " + p + " was taken back, found " + level.getBlockState(p));
		}
		helper.assertTrue(a.tempBlocks().size() == 0, "nothing left tracked");
		helper.assertTrue(FrostCleanup.get(level).pending(a.id()) == 0, "and nothing left owed in the orphan ledger");
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "frost_blocks_win", timeoutTicks = 40)
	public void temporaryBlocksAreRemovedOnVictory(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FrostGiantAssault a = startPinned(helper);
		List<BlockPos> placed;
		try {
			placed = placeTemporaryBlocks(helper, a);
			a.complete(level);
			assertAllGone(helper, a, placed);
			helper.assertTrue(level.getBlockState(a.center()).is(Blocks.CHEST), "victory: the anchor becomes the reward chest");
		} finally {
			end(level, a);
		}
		level.setBlockAndUpdate(a.center(), Blocks.AIR.defaultBlockState());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "frost_blocks_loss", timeoutTicks = 40)
	public void temporaryBlocksAreRemovedOnDefeat(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FrostGiantAssault a = startPinned(helper);
		try {
			List<BlockPos> placed = placeTemporaryBlocks(helper, a);
			a.fail(level, net.minecraft.network.chat.Component.literal("test defeat"));
			assertAllGone(helper, a, placed);
			helper.assertTrue(level.getBlockState(a.center()).isAir(), "defeat: the anchor shatters");
		} finally {
			end(level, a);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "frost_blocks_abort", timeoutTicks = 40)
	public void temporaryBlocksAreRemovedOnAbort(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FrostGiantAssault a = startPinned(helper);
		List<BlockPos> placed;
		try {
			placed = placeTemporaryBlocks(helper, a);
		} finally {
			end(level, a);
		}
		assertAllGone(helper, a, placed);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aPlayersBlockIsNeverTouched(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FrostGiantAssault a = new FrostGiantAssault(UUID.randomUUID());
		BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
		level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
		helper.assertTrue(a.tempBlocks().addSnow(level, floor.above(), 2), "our snow");
		level.setBlockAndUpdate(floor.above(), Blocks.OAK_PLANKS.defaultBlockState()); // a player builds over it
		helper.assertFalse(a.tempBlocks().place(level, floor, Blocks.ICE.defaultBlockState(), -1), "never into a solid block");
		a.tempBlocks().restoreAll(level);
		helper.assertTrue(level.getBlockState(floor.above()).is(Blocks.OAK_PLANKS), "the player's planks stay");
		helper.assertTrue(level.getBlockState(floor).is(Blocks.STONE), "and the ground stays");
		FrostCleanup.get(level).sweep(level.getServer());
		helper.succeed();
	}

	// ---------------------------------------------------------------- King Laufey and the Casket

	@GameTest(template = EMPTY_STRUCTURE, batch = "frost_casket", timeoutTicks = 60)
	public void theCasketCanOnlyBeBrokenWhileLaufeyStaggers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		KingLaufeyEntity laufey = helper.spawn(FrostGiantEntityTypes.LAUFEY, new BlockPos(4, 2, 4));
		laufey.forcePhase(level, 3);
		helper.assertTrue(laufey.phase() == 3, "phase 3, got " + laufey.phase());
		helper.assertTrue(laufey.casketId() != null && level.getEntity(laufey.casketId()) instanceof IceCasketEntity, "the Casket floats above him");
		IceCasketEntity casket = (IceCasketEntity) level.getEntity(laufey.casketId());
		helper.runAfterDelay(2, () -> {
			float before = casket.getHealth();
			casket.hurt(level.damageSources().generic(), 50f);
			helper.assertTrue(casket.getHealth() == before, "no stagger: the Casket shrugs it off");
			helper.assertTrue(laufey.tryStagger(level, "lightning"), "lightning staggers him");
			helper.assertFalse(laufey.tryStagger(level, "burst"), "not twice at once");
		});
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(casket.isVulnerable(), "staggered: the Casket is open");
			float before = casket.getHealth();
			casket.hurt(level.damageSources().generic(), 50f);
			helper.assertTrue(casket.getHealth() < before, "staggered: the Casket takes the hit");
			casket.invulnerableTime = 0;
			casket.hurt(level.damageSources().generic(), 100000f);
			helper.assertTrue(laufey.isDrained(), "the Casket broke: Laufey is drained");
			laufey.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "frost_hold", timeoutTicks = 40)
	public void laufeyHoldsAtOneHealthUntilTheCasketBreaks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		KingLaufeyEntity laufey = helper.spawn(FrostGiantEntityTypes.LAUFEY, new BlockPos(4, 2, 4));
		laufey.hurt(level.damageSources().generic(), 1000000f);
		helper.assertTrue(laufey.isAlive() && !laufey.isDeadOrDying(), "he refuses to fall");
		helper.assertTrue(laufey.getHealth() == 1f && laufey.isHoldingAtOne(), "kneeling at 1 health, got " + laufey.getHealth());
		helper.assertTrue(laufey.exposesCasket(), "kneeling, the Casket is exposed");
		laufey.invulnerableTime = 0;
		helper.assertFalse(laufey.hurt(level.damageSources().generic(), 50f), "invulnerable while he rebuilds");
		laufey.onCasketBroken(level);
		laufey.invulnerableTime = 0;
		laufey.hurt(level.damageSources().generic(), 50f);
		helper.assertTrue(laufey.isDeadOrDying(), "the Casket broken: now he falls");
		helper.assertTrue(laufey.currentClip() == FrostGiantAnims.LAUFEY_DEATH, "and plays the death collapse, got " + laufey.currentClip());
		helper.runAfterDelay(30, () -> {
			helper.assertFalse(laufey.isRemoved(), "the 4 s collapse is still playing at 1.5 s");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- rewards, gear, bestiary, config

	@GameTest(template = EMPTY_STRUCTURE)
	public void rewardsPayShardsPerPartyAndTheCasketOnlyOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int party : new int[] { 1, 3 }) {
			List<ItemStack> loot = FrostGiantRewards.roll(RandomSource.create(7), level.registryAccess(), party);
			int shards = 0;
			boolean skull = false;
			for (ItemStack s : loot) {
				if (s.is(FrostGiantItems.RIME_SHARD)) {
					shards += s.getCount();
				}
				skull |= s.is(FrostGiantItems.JOTUN_SKULL);
				helper.assertFalse(s.is(FrostGiantItems.CASKET), "the Casket never goes in the shared chest");
			}
			helper.assertTrue(shards == 4 + 2 * party, "Rime Shards 4 + 2 x " + party + ", got " + shards);
			helper.assertTrue(skull, "a Jotun Skull");
		}
		ServerPlayer p = survivor(helper);
		helper.assertTrue(FrostGiantRewards.recordClear(p).is(FrostGiantItems.CASKET), "first clear: the Casket of Ancient Winters");
		helper.assertTrue(FrostGiantRewards.recordClear(p).isEmpty(), "second clear: none");
		helper.assertTrue(FrostGiantRewards.clears(p) == 2, "clears counted");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rimeShardsForgeRimedNetheriteAndRimeTouchedHammers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack shard = new ItemStack(FrostGiantItems.RIME_SHARD);
		SmithingRecipeInput armour = new SmithingRecipeInput(ItemStack.EMPTY, new ItemStack(Items.NETHERITE_CHESTPLATE), shard);
		var r = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, armour, level);
		helper.assertTrue(r.isPresent(), "netherite + shard, no template");
		ItemStack rimed = r.get().value().assemble(armour, level.registryAccess());
		helper.assertTrue(FrostGear.isRimed(rimed), "Rimed");
		helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, new SmithingRecipeInput(ItemStack.EMPTY, rimed, shard), level)
				.isEmpty(), "only once");
		helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.SMITHING,
				new SmithingRecipeInput(ItemStack.EMPTY, new ItemStack(Items.DIAMOND_CHESTPLATE), shard), level).isEmpty(), "netherite only");
		SmithingRecipeInput hammer = new SmithingRecipeInput(ItemStack.EMPTY, new ItemStack(com.projecthero.mod.item.ModItems.STORMBREAKER), shard);
		var hr = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, hammer, level);
		helper.assertTrue(hr.isPresent() && FrostGear.isRimeTouched(hr.get().value().assemble(hammer, level.registryAccess())),
				"Stormbreaker + shard: Rime-touched");
		ServerPlayer p = survivor(helper);
		helper.assertTrue(p.canFreeze(), "a plain player can freeze");
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(FrostGiantItems.CASKET));
		helper.assertTrue(FrostGear.coldImmune(p) && !p.canFreeze(), "the Casket in the off hand: immune to freezing");
		helper.assertTrue(FrostGear.walksOnPowderSnow(p), "and walks on powder snow");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBestiaryUnlocksEachEntryOnce(GameTestHelper helper) {
		ServerPlayer p = survivor(helper);
		helper.assertFalse(FrostBestiary.isUnlocked(p, FrostBestiary.SHAMAN), "locked to begin with");
		helper.assertTrue(FrostBestiary.unlock(p, FrostBestiary.SHAMAN), "the first kill unlocks it");
		helper.assertFalse(FrostBestiary.unlock(p, FrostBestiary.SHAMAN), "only once");
		helper.assertTrue(FrostBestiary.isUnlocked(p, FrostBestiary.SHAMAN) && !FrostBestiary.isUnlocked(p, FrostBestiary.LAUFEY), "just that one");
		var chapter = com.projecthero.mod.hero.guide.HeroPackGuide.frostGiantsChapter(p);
		boolean shamanShown = chapter.lines().stream().anyMatch(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
				&& t.getKey().equals("projecthero.guide.frost_giants.unit.frost_shaman"));
		boolean laufeyHidden = chapter.lines().stream().noneMatch(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
				&& t.getKey().equals("projecthero.guide.frost_giants.unit.king_laufey"));
		helper.assertTrue(shamanShown && laufeyHidden, "the guide shows what this player has learned and no more");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void frostConfigMigratesAndKeepsServerChoices(GameTestHelper helper) {
		FrostGiantConfig fresh = FrostGiantConfig.SPEC.fresh();
		helper.assertTrue(fresh.configVersion != null && fresh.configVersion == FrostGiantConfig.SPEC.currentVersion(), "fresh is stamped");
		helper.assertFalse(fresh.trigger.naturalTriggerEnabled, "the natural trigger is off by default");
		helper.assertTrue(fresh.arena.radius == 56.0 && fresh.braziers.count == 4 && fresh.braziers.warmRadius == 7.0, "arena 56, 4 braziers, 7 warmth");
		FrostGiantConfig old = FrostGiantConfig.SPEC.fromJson("{\"trigger\":{\"naturalTriggerEnabled\":true},\"laufey\":{\"health\":3000.0,\"armor\":\"lots\"}}");
		helper.assertTrue(old.trigger.naturalTriggerEnabled, "a server's toggle is kept");
		helper.assertTrue(old.laufey.health == 3000.0, "a version-1 value is kept, got " + old.laufey.health);
		helper.assertTrue(old.laufey.armor == 14.0, "a wrong-typed value gets its default, got " + old.laufey.armor);
		helper.assertTrue(old.warlord.health == 1200.0 && old.waves.hounds.length == 5, "missing sections get defaults");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "frost_save", timeoutTicks = 40)
	public void assaultSurvivesASaveRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FrostGiantAssault a = startPinned(helper);
		try {
			a.addFighter(UUID.randomUUID());
			a.startWave(level, 2);
			BlockPos ice = helper.absolutePos(new BlockPos(4, 3, 7));
			a.placeIce(level, ice, Blocks.PACKED_ICE.defaultBlockState(), -1);
			CompoundTag tag = a.save();
			FrostGiantAssault copy = (FrostGiantAssault) EventTypes.create(FrostGiantAssault.TYPE_ID, a.id());
			copy.load(tag);
			helper.assertTrue(copy.phase() == FrostGiantAssault.Phase.WAVE && copy.wave() == 2, "phase and wave survive");
			helper.assertTrue(copy.brazierIds().equals(a.brazierIds()) && copy.brazierIds().size() == 4, "the braziers survive");
			helper.assertTrue(copy.fighters().equals(a.fighters()), "the fighters survive");
			helper.assertTrue(copy.owed() == a.owed(), "the owed units survive");
			helper.assertTrue(copy.tempBlocks().tracks(ice) && copy.tempBlocks().size() == a.tempBlocks().size(), "the placed blocks survive");
		} finally {
			end(level, a);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the model contract

	private static JsonObject read(String path) {
		try (InputStream in = FrostGiantAnims.class.getResourceAsStream("/assets/projecthero/" + path)) {
			if (in == null) {
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyClipExistsAndStrikesLandOnTheDamageTick(GameTestHelper helper) {
		for (String model : FrostGiantAnims.MODELS) {
			JsonObject file = read("animations/frost_giant/" + model + ".animation.json");
			helper.assertTrue(file != null, model + ".animation.json must be on the classpath");
			JsonObject clips = file.getAsJsonObject("animations");
			for (FrostGiantAnims.Clip c : FrostGiantAnims.of(model)) {
				helper.assertTrue(clips.has(c.id()), "missing clip " + c.id());
				JsonObject clip = clips.getAsJsonObject(c.id());
				double len = clip.get("animation_length").getAsDouble();
				if (!c.loops()) {
					helper.assertTrue(len * 20.0 + FrostGiantAnims.TRANSITION <= c.length() + 0.5,
							c.id() + ": the clip must finish before the server drops it (" + len + " s vs " + c.length() + " ticks)");
				}
				double strike = c.strikeSeconds();
				if (strike < 0) {
					continue;
				}
				boolean found = false;
				JsonObject bones = clip.getAsJsonObject("bones");
				for (String bone : bones.keySet()) {
					JsonObject channels = bones.getAsJsonObject(bone);
					for (String ch : channels.keySet()) {
						if (!channels.get(ch).isJsonObject()) {
							continue;
						}
						for (String k : channels.getAsJsonObject(ch).keySet()) {
							found |= Math.abs(Double.parseDouble(k) - strike) < 1.0e-3;
						}
					}
				}
				helper.assertTrue(found, c.id() + ": no keyframe on the strike time " + strike + " s (server tick " + c.hit() + ")");
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void modelsHaveTheBonesTheCodeDrivesAndNeverKeyThem(GameTestHelper helper) {
		String[] jotun = { "root", "hips", "waist", "chest", "flinch", "neck", "head", "jaw", "beard", "shoulder_l", "shoulder_r", "arm_l", "arm_r",
				"forearm_l", "forearm_r", "hand_l", "hand_r", "fingers_l", "fingers_r", "weapon_r", "thigh_l", "thigh_r", "shin_l", "shin_r",
				"foot_l", "foot_r", "crystals", "mantle", "helm" };
		String[] hound = { "root", "body", "chest", "neck", "head", "jaw", "tail", "leg_fl", "leg_fr", "leg_bl", "leg_br" };
		for (String model : FrostGiantAnims.MODELS) {
			JsonObject geo = read("geo/frost_giant/" + model + ".geo.json");
			helper.assertTrue(geo != null, model + ".geo.json must be on the classpath");
			Set<String> names = new HashSet<>();
			geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")
					.forEach(b -> names.add(b.getAsJsonObject().get("name").getAsString()));
			String[] need = switch (model) {
				case FrostGiantAnims.HOUND -> hound;
				case FrostGiantAnims.YMIR, FrostGiantAnims.CASKET -> new String[] { "root" };
				default -> jotun;
			};
			for (String b : need) {
				helper.assertTrue(names.contains(b), model + ": missing bone " + b);
			}
			if (model.equals(FrostGiantAnims.LAUFEY)) {
				helper.assertTrue(names.contains("casket"), "Laufey holds the Casket in its own bone");
			}
			JsonObject clips = read("animations/frost_giant/" + model + ".animation.json").getAsJsonObject("animations");
			for (String clip : clips.keySet()) {
				JsonObject keyed = clips.getAsJsonObject(clip).getAsJsonObject("bones");
				for (String never : FrostGiantAnims.CODE_DRIVEN_BONES) {
					helper.assertFalse(keyed != null && keyed.has(never), clip + " must not key the code-driven bone " + never);
				}
			}
			for (String tex : new String[] { model + ".png", model + "_glowmask.png" }) {
				try (InputStream in = FrostGiantAnims.class.getResourceAsStream("/assets/projecthero/textures/entity/frost_giant/" + tex)) {
					helper.assertTrue(in != null, "missing texture " + tex);
				} catch (java.io.IOException e) {
					helper.fail("unreadable texture " + tex);
				}
			}
		}
		helper.succeed();
	}
}
