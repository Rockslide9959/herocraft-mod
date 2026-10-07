package com.projecthero.mod.gametest;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.event.EventTypes;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ultron.MindStoneCharm;
import com.projecthero.mod.ultron.Ultron;
import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronConfig;
import com.projecthero.mod.ultron.UltronEntityTypes;
import com.projecthero.mod.ultron.UltronRewards;
import com.projecthero.mod.ultron.UltronUprising;
import com.projecthero.mod.ultron.entity.UltronDroneEntity;
import com.projecthero.mod.ultron.entity.UltronHeavyEntity;
import com.projecthero.mod.ultron.entity.UltronPrimeEntity;
import com.projecthero.mod.ultron.entity.UltronPylonEntity;
import com.projecthero.mod.ultron.entity.UltronSentryEntity;
import com.projecthero.mod.ultron.item.UltronItems;
import com.projecthero.mod.ultron.item.VibraniumPlating;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;

/**
 * v0.15.12: the Ultron Uprising. Like the Sentinel Purge tests, the timed raid is never run: each test drives one piece
 * directly inside its own space -- pylons rise 3 blocks out, arrivals land 1.5 blocks from the centre -- and every
 * uprising is aborted and removed before the test ends. Tests that start one each get their own batch (only one may run
 * within 256 blocks of another). Monsters need a non-Peaceful world; mock players report creative, so the raid checks
 * abilities and a survival mock player counts as a fighter.
 */
public class UltronV01512GameTests implements FabricGameTest {

	private static UltronUprising startPinned(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		UltronUprising u = Ultron.start(helper.getLevel(), at, null, x -> {
			x.pylonRingOverride = 3.0;
			x.arrivalRadiusOverride = 1.5;
			x.arrivalHeightOverride = 1.0;
		});
		helper.assertTrue(u != null, "the uprising starts");
		return u;
	}

	private static void end(ServerLevel level, UltronUprising u) {
		if (u != null) {
			u.abort(level);
			EventSavedData.get(level).remove(u.id());
		}
	}

	// ---------------------------------------------------------------- registration, stats, damage

	@GameTest(template = EMPTY_STRUCTURE)
	public void ultronIsRegisteredWithItsRecipes(GameTestHelper helper) {
		helper.assertTrue(EventTypes.isRegistered(UltronUprising.TYPE_ID), "ultron is a registered event type");
		helper.assertTrue(EventTypes.create(UltronUprising.TYPE_ID, UUID.randomUUID()) instanceof UltronUprising, "loads back as an uprising");
		for (String r : new String[] { "ultron_beacon", "vibranium_plating" }) {
			helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ProjectHeroMod.id(r)).isPresent(), "recipe " + r);
		}
		var beacon = helper.getLevel().getRecipeManager().byKey(ProjectHeroMod.id("ultron_beacon")).orElseThrow().value();
		boolean core = false;
		for (var ing : beacon.getIngredients()) {
			core |= ing.test(new ItemStack(com.projecthero.mod.sentinel.item.SentinelItems.MASTER_MOLD_CORE));
		}
		helper.assertTrue(core, "the beacon is built round a Master Mold Core");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void robotsHaveTheirStatsAndSizes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UltronDroneEntity drone = UltronEntityTypes.DRONE.create(level);
		UltronHeavyEntity heavy = UltronEntityTypes.HEAVY.create(level);
		UltronPrimeEntity prime = UltronEntityTypes.PRIME.create(level);
		UltronSentryEntity sentry = UltronEntityTypes.SENTRY.create(level);
		helper.assertTrue(drone.getMaxHealth() == 30f, "drone 30 health, got " + drone.getMaxHealth());
		helper.assertTrue(heavy.getMaxHealth() == 120f && heavy.getAttributeValue(Attributes.ARMOR) == 10.0, "heavy 120 / 10 armour");
		helper.assertTrue(Math.abs(prime.getBbHeight() - 1.8f * 1.4f) < 0.05f, "Prime is x1.4, got " + prime.getBbHeight());
		helper.assertTrue(Math.abs(sentry.getBbHeight() - 1.8f * 3.4f) < 0.05f, "the Sentry is ~6 blocks, got " + sentry.getBbHeight());
		prime.configure(1);
		helper.assertTrue(prime.getMaxHealth() == 900f && prime.getHealth() == 900f, "Prime solo 900");
		prime.configure(3);
		helper.assertTrue(prime.getMaxHealth() == 1500f, "+300 per extra fighter, got " + prime.getMaxHealth());
		prime.configure(50);
		helper.assertTrue(prime.getMaxHealth() == 3000f, "capped at 3000, got " + prime.getMaxHealth());
		sentry.configure(2);
		helper.assertTrue(sentry.getMaxHealth() == 1900f, "Sentry 1500 + 400, got " + sentry.getMaxHealth());
		helper.assertTrue(drone.isAlliedTo(sentry) && heavy.isAlliedTo(prime), "the robots are on one side");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void lightningHurtsRobotsHalfAgainAndPoisonDoesNothing(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		UltronDroneEntity drone = helper.spawn(UltronEntityTypes.DRONE, new BlockPos(2, 2, 2));
		drone.getAttribute(Attributes.ARMOR).setBaseValue(0);
		float before = drone.getHealth();
		drone.hurt(helper.getLevel().damageSources().lightningBolt(), 10f);
		helper.assertTrue(Math.abs(before - drone.getHealth() - 15f) < 0.01f, "lightning x1.5: lost " + (before - drone.getHealth()));
		drone.invulnerableTime = 0;
		float mid = drone.getHealth();
		drone.hurt(helper.getLevel().damageSources().generic(), 4f);
		helper.assertTrue(Math.abs(mid - drone.getHealth() - 4f) < 0.01f, "ordinary damage x1, lost " + (mid - drone.getHealth()));
		helper.assertTrue(UltronCombat.multiplier(helper.getLevel().damageSources().lightningBolt(), 1f) == 1.5f, "electric multiplier 1.5");
		helper.assertFalse(drone.addEffect(new MobEffectInstance(MobEffects.POISON, 100)), "robots can't be poisoned");
		helper.assertFalse(drone.addEffect(new MobEffectInstance(MobEffects.WITHER, 100)), "or withered");
		UltronHeavyEntity heavy = helper.spawn(UltronEntityTypes.HEAVY, new BlockPos(5, 2, 5));
		helper.assertTrue(drone.isInvulnerableTo(helper.getLevel().damageSources().mobAttack(heavy)), "Ultron never hurts his own");
		drone.discard();
		heavy.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- waves

	@GameTest(template = EMPTY_STRUCTURE)
	public void waveMixGrowsAndScalesWithFighters(GameTestHelper helper) {
		helper.assertTrue(UltronUprising.waveCount() == 5, "five waves");
		for (int n = 1; n <= 2; n++) {
			int[] w = UltronUprising.composition(n);
			helper.assertTrue(w[0] > 0 && w[1] == 0 && w[2] == 0 && w[3] == 0, "wave " + n + " is drones only");
		}
		int[] w3 = UltronUprising.composition(3);
		helper.assertTrue(w3[1] > 0 && w3[2] == 0 && w3[3] == 0, "wave 3 adds Sentinel Drones");
		int[] w4 = UltronUprising.composition(4);
		helper.assertTrue(w4[2] > 0 && w4[3] > 0, "wave 4 adds a Heavy and snipers");
		int[] w5 = UltronUprising.composition(5);
		helper.assertTrue(w5[0] > 0 && w5[1] > 0 && w5[2] > 0 && w5[3] > 0, "wave 5 is everything");
		helper.assertTrue(UltronUprising.scaled(4, 1) == 4 && UltronUprising.scaled(4, 3) == 8 && UltronUprising.scaled(2, 2) == 3,
				"x(1 + 0.5 per extra fighter)");
		helper.assertTrue(UltronUprising.scaled(0, 4) == 0, "an empty entry stays empty");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "ultron_waves", timeoutTicks = 40)
	public void uprisingRaisesPylonsAndSpawnsItsWaves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UltronUprising u = startPinned(helper);
		try {
			helper.assertTrue(u.state() == EventState.RUNNING && u.phase() == UltronUprising.Phase.COUNTDOWN, "running, counting down");
			helper.assertTrue(u.livePylons(level).size() == 3, "three relay pylons, got " + u.livePylons(level).size());
			helper.assertTrue(level.getBlockState(u.center()).is(com.projecthero.mod.ultron.block.UltronBlocks.ULTRON_BEACON),
					"the uplink stands at the centre");
			helper.assertTrue(Ultron.start(level, helper.absolutePos(new BlockPos(5, 1, 5)), null) == null, "a second one nearby is refused");
			u.startWave(level, 4);
			int f = u.fighterCount();
			int[] live = u.liveCounts(level);
			int[] mix = UltronUprising.composition(4);
			int expected = 0;
			for (int m : mix) {
				expected += UltronUprising.scaled(m, f);
			}
			helper.assertTrue(live[0] + live[1] + live[2] + live[3] + u.owed() == expected, "wave 4 brings its full mix: "
					+ java.util.Arrays.toString(live) + " + " + u.owed() + " owed vs " + expected);
			helper.assertTrue(live[2] > 0 && live[3] > 0, "a Heavy and snipers came first");
			// repairs near a pylon
			Mob hurt = null;
			for (Mob m : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(u.center()).inflate(40), u::ownsEntity)) {
				hurt = m;
				break;
			}
			helper.assertTrue(hurt != null, "a unit to repair");
			UltronPylonEntity near = u.livePylons(level).get(0);
			hurt.teleportTo(near.getX() + 1.5, near.getY(), near.getZ());
			hurt.setHealth(hurt.getMaxHealth() - 10);
			float h0 = hurt.getHealth();
			u.repairNow(level);
			helper.assertTrue(hurt.getHealth() > h0, "a unit near a standing pylon repairs");
		} finally {
			end(level, u);
		}
		helper.assertTrue(UltronUprising.find(level, u.id()) == null, "nothing of it remains");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the gate and the body jump

	@GameTest(template = EMPTY_STRUCTURE, batch = "ultron_gate", timeoutTicks = 40)
	public void bossCannotStartWhileAPylonStands(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UltronUprising u = startPinned(helper);
		try {
			List<UltronPylonEntity> pylons = u.livePylons(level);
			helper.assertTrue(pylons.size() == 3, "three pylons");
			helper.assertFalse(u.tryStartBoss(level), "no boss while three pylons stand");
			pylons.get(0).kill();
			pylons.get(1).kill();
			helper.assertFalse(u.tryStartBoss(level), "no boss while one pylon stands");
			helper.assertTrue(u.phase() != UltronUprising.Phase.PRIME, "still not the boss phase");
			pylons.get(2).kill();
			helper.assertTrue(u.tryStartBoss(level), "every pylon down: Ultron Prime comes");
			helper.assertTrue(u.phase() == UltronUprising.Phase.PRIME && u.boss(level) instanceof UltronPrimeEntity, "Prime is the boss");
			helper.assertTrue(u.livePylons(level).size() == UltronConfig.arena().reservePylons, "he raised his reserve pylons, got "
					+ u.livePylons(level).size());
		} finally {
			end(level, u);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "ultron_jump", timeoutTicks = 40)
	public void bodyJumpConsumesAPylonAndRestoresHalfHealth(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UltronUprising u = startPinned(helper);
		try {
			for (UltronPylonEntity p : u.livePylons(level)) {
				p.kill();
			}
			helper.assertTrue(u.tryStartBoss(level), "Prime comes");
			UltronPrimeEntity prime = (UltronPrimeEntity) u.boss(level);
			int pylonsBefore = u.livePylons(level).size();
			helper.assertTrue(pylonsBefore >= 1, "a spare body stands");
			prime.hurt(level.damageSources().genericKill(), 100000f);
			helper.assertTrue(prime.isAlive() && !prime.isDeadOrDying() && prime.isJumping(), "at 0 health he uploads instead of dying");
			prime.finishJump(level);
			helper.assertTrue(u.livePylons(level).size() == pylonsBefore - 1, "the pylon is consumed");
			helper.assertTrue(Math.abs(prime.getHealth() - prime.getMaxHealth() * 0.5f) < 0.5f, "new body at half health, got "
					+ prime.getHealth() + " / " + prime.getMaxHealth());
			helper.assertTrue(prime.bodyIndex() == 2, "body two");
			// with no pylon left he really falls
			for (UltronPylonEntity p : u.livePylons(level)) {
				p.discard();
			}
			prime.invulnerableTime = 0;
			prime.hurt(level.damageSources().genericKill(), 100000f);
			helper.assertTrue(prime.isDeadOrDying() && !prime.isJumping(), "no pylon: he falls");
		} finally {
			end(level, u);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "ultron_save", timeoutTicks = 40)
	public void uprisingSurvivesASaveRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		UltronUprising u = startPinned(helper);
		try {
			u.addFighter(UUID.randomUUID());
			u.startWave(level, 2);
			CompoundTag tag = u.save();
			UltronUprising copy = (UltronUprising) EventTypes.create(UltronUprising.TYPE_ID, u.id());
			copy.load(tag);
			helper.assertTrue(copy.phase() == UltronUprising.Phase.WAVE && copy.wave() == 2, "phase and wave survive");
			helper.assertTrue(copy.pylonIds().equals(u.pylonIds()) && copy.pylonIds().size() == 3, "the pylons survive");
			helper.assertTrue(copy.fighters().equals(u.fighters()), "the fighters survive");
			helper.assertTrue(copy.owed() == u.owed(), "the owed units survive");
		} finally {
			end(level, u);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- rewards, plating, mind stone, config

	@GameTest(template = EMPTY_STRUCTURE)
	public void rewardsPayPlatingPerPartyAndTheMindStoneOnlyOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int party : new int[] { 1, 3 }) {
			List<ItemStack> loot = UltronRewards.roll(RandomSource.create(42), level.registryAccess(), party);
			int plating = 0;
			boolean core = false;
			for (ItemStack s : loot) {
				if (s.is(UltronItems.VIBRANIUM_PLATING)) {
					plating += s.getCount();
				}
				core |= s.is(UltronItems.ULTRON_CORE);
				helper.assertFalse(s.is(UltronItems.MIND_STONE), "the Mind Stone never goes in the shared chest");
			}
			helper.assertTrue(plating == 2 + party, "Vibranium Plating x(2 + " + party + "), got " + plating);
			helper.assertTrue(core, "an Ultron Core");
		}
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		helper.assertTrue(UltronRewards.recordClear(p).is(UltronItems.MIND_STONE), "first clear: a Mind Stone");
		helper.assertTrue(UltronRewards.recordClear(p).isEmpty(), "second clear: none");
		helper.assertTrue(UltronRewards.clears(p) == 2, "clears counted");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vibraniumPlatingAddsTwoArmourAtTheSmithingTable(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack chest = new ItemStack(IronManItems.armor("mark_iii", ArmorItem.Type.CHESTPLATE));
		ItemStack plate = new ItemStack(UltronItems.VIBRANIUM_PLATING);
		SmithingRecipeInput input = new SmithingRecipeInput(ItemStack.EMPTY, chest, plate);
		var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, level);
		helper.assertTrue(recipe.isPresent(), "the smithing table takes an Iron Man piece + plating, no template");
		ItemStack out = recipe.get().value().assemble(input, level.registryAccess());
		double base = VibraniumPlating.armorValue(chest);
		double plated = VibraniumPlating.armorValue(out);
		helper.assertTrue(base > 0 && Math.abs(plated - base - 2.0) < 1.0e-6, "+2 armour: " + base + " -> " + plated);
		helper.assertTrue(VibraniumPlating.isPlated(out), "marked plated");
		helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, new SmithingRecipeInput(ItemStack.EMPTY, out, plate), level)
				.isEmpty(), "a piece is plated only once");
		helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.SMITHING,
				new SmithingRecipeInput(ItemStack.EMPTY, new ItemStack(Items.DIAMOND_CHESTPLATE), plate), level).isEmpty(), "Iron Man armour only");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mindStoneCharmTurnsAMobOnTheHostilesNearIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Zombie a = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
		Zombie b = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
		try {
			a.setTarget(p);
			MindStoneCharm.charm(level, a, p, 200);
			helper.assertTrue(MindStoneCharm.isCharmed(a), "charmed");
			a.setTarget(p);
			MindStoneCharm.tick(level.getServer());
			helper.assertTrue(a.getTarget() == b, "it turns on the hostile beside it, got " + a.getTarget());
		} finally {
			MindStoneCharm.clearSessionState();
			a.discard();
			b.discard();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ultronConfigMigratesAndKeepsServerChoices(GameTestHelper helper) {
		UltronConfig fresh = UltronConfig.SPEC.fresh();
		helper.assertTrue(fresh.configVersion != null && fresh.configVersion == UltronConfig.SPEC.currentVersion(), "fresh is stamped");
		helper.assertFalse(fresh.trigger.naturalTriggerEnabled, "the natural trigger is off by default");
		UltronConfig old = UltronConfig.SPEC.fromJson("{\"trigger\":{\"naturalTriggerEnabled\":true},\"prime\":{\"health\":1234.0,\"armor\":\"lots\"}}");
		helper.assertTrue(old.trigger.naturalTriggerEnabled, "a server's toggle is kept");
		helper.assertTrue(old.prime.health == 1234.0, "a version-1 value is kept, got " + old.prime.health);
		helper.assertTrue(old.prime.armor == 12.0, "a wrong-typed value gets its default, got " + old.prime.armor);
		helper.assertTrue(old.sentry.health == 1500.0 && old.waves.drones.length == 5, "missing sections get defaults");
		helper.assertTrue(old.configVersion == UltronConfig.SPEC.currentVersion(), "stamped with the current version");
		helper.succeed();
	}

}
