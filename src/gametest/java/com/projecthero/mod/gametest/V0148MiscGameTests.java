package com.projecthero.mod.gametest;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.hero.PowerItems;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.item.HeroPackItems;
import com.projecthero.mod.hero.mutation.ModSerums;
import com.projecthero.mod.hero.power.p02.LaserVisionHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;
import com.projecthero.mod.mixin.ItemEntityPickupAccessor;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.8 batch: Super Speed (loot pickup during Time Slow, the 5 s Time Slow charge, no movement boosts while it
 * runs, the exhaustion after it, the untimed carry), Laser Vision (heat vents only after 5 s idle, at 3 a second, and
 * never scales damage) and the disabled experimental powers ({@code Powers.ENABLED}). Mock players are not reliably
 * ticked, so every per-tick system is stepped by hand and asserted one step at a time.
 */
public class V0148MiscGameTests implements FabricGameTest {

	// ---------------- helpers ----------------

	/** A survival mock player holding {@code key} at (2.5, 2, 1.5) facing +Z, in a cleared room with a stone floor. */
	private static ServerPlayer hero(GameTestHelper helper, String key) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(6, 5, 6)))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(6, 1, 6)))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYRot(0);
		p.setXRot(0);
		p.setYHeadRot(0);
		Power power = Powers.byKey(key);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		return p;
	}

	private static Power speed() {
		return Powers.byKey(SuperSpeedHandlers.KEY);
	}

	private static float res(ServerPlayer p, String key, String name) {
		return ExperimentalPowers.getResource(p, Powers.byKey(key), name);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z) {
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0);
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
		m.setHealth(100);
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static boolean hasModifier(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
			String path) {
		var inst = p.getAttribute(attr);
		return inst != null && inst.getModifier(ProjectHeroMod.id(path)) != null;
	}

	/** Holds Z until Time Slow fires (at most one tick past the charge time). */
	private static int chargeTimeSlow(ServerPlayer p) {
		AbilityRouter.handleInput(p, 4, true);
		int ticks = 0;
		while (!SuperSpeedTimeSlow.isCasting(p) && ticks <= SuperSpeedHandlers.TS_CHARGE_TICKS) {
			ExperimentalPowers.serverTick(p);
			ticks++;
		}
		return ticks;
	}

	// ---------------- Super Speed: Time Slow ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "v0148_ts_loot")
	public void timeSlowCountsLootPickupDelayOnTheCastersClock(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperSpeedHandlers.KEY);
		Vec3 near = helper.absoluteVec(new Vec3(2.5, 2.0, 4.5));
		ItemEntity loot = new ItemEntity(helper.getLevel(), near.x, near.y, near.z, new ItemStack(Items.ROTTEN_FLESH));
		loot.setDefaultPickUpDelay(); // 10 ticks, as for mob drops
		ItemEntity never = new ItemEntity(helper.getLevel(), near.x, near.y, near.z + 0.5, new ItemStack(Items.BONE));
		never.setNeverPickUp();
		helper.getLevel().addFreshEntity(loot);
		helper.getLevel().addFreshEntity(never);

		// outside Time Slow an extra caster tick does nothing at all
		SuperSpeedTimeSlow.extraCasterTick(p);
		helper.assertTrue(((ItemEntityPickupAccessor) loot).projecthero$getPickupDelay() == 10, "no Time Slow: delay untouched");

		chargeTimeSlow(p);
		try {
			helper.assertTrue(SuperSpeedTimeSlow.isCasting(p), "Time Slow is running");
			int before = ((ItemEntityPickupAccessor) loot).projecthero$getPickupDelay();
			SuperSpeedTimeSlow.extraCasterTick(p);
			int after = ((ItemEntityPickupAccessor) loot).projecthero$getPickupDelay();
			helper.assertTrue(before - after == 1, "each caster tick counts one tick off the delay (" + before + " -> " + after + ")");
			for (int i = 0; i < 12 && ((ItemEntityPickupAccessor) loot).projecthero$getPickupDelay() > 0; i++) {
				SuperSpeedTimeSlow.extraCasterTick(p);
			}
			helper.assertTrue(loot.isRemoved() || !loot.hasPickUpDelay(), "the loot is collectable after ~10 caster ticks");
			helper.assertTrue(((ItemEntityPickupAccessor) never).projecthero$getPickupDelay() == 32767,
					"a never-collectable item stays never-collectable");
		} finally {
			SuperSpeedTimeSlow.end(p, false);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v0148_ts_cancel")
	public void timeSlowChargeReleasedEarlyCostsNothing(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperSpeedHandlers.KEY);
		AbilityRouter.handleInput(p, 4, true); // Z down
		helper.assertTrue(SuperSpeedHandlers.timeSlowCharging(p), "holding Z starts the charge");
		float last = res(p, SuperSpeedHandlers.KEY, SuperSpeedHandlers.TS_CHARGE);
		for (int i = 0; i < SuperSpeedHandlers.TS_CHARGE_TICKS / 2; i++) {
			ExperimentalPowers.serverTick(p);
			float now = res(p, SuperSpeedHandlers.KEY, SuperSpeedHandlers.TS_CHARGE);
			helper.assertTrue(Math.abs(now - last - 1f) < 1e-3, "the charge builds one tick at a time (" + last + " -> " + now + ")");
			last = now;
		}
		helper.assertFalse(SuperSpeedTimeSlow.isCasting(p), "half a charge fires nothing");
		AbilityRouter.handleInput(p, 4, false); // let go early
		helper.assertFalse(SuperSpeedHandlers.timeSlowCharging(p), "letting go cancels");
		helper.assertTrue(res(p, SuperSpeedHandlers.KEY, SuperSpeedHandlers.TS_CHARGE) == 0f, "and empties the bar");
		helper.assertTrue(ExperimentalPowers.cooldownReady(p, speed(), speed().ability(AbilitySlot.SLOT_4)), "with no cooldown");
		helper.assertFalse(SuperSpeedHandlers.exhausted(p), "and no exhaustion");
		helper.succeed();
	}

	/** v0.14.9: Z still held when the charge fires sends key-repeat presses; only a press after letting go ends it. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "v0149_ts_repeat")
	public void heldZKeyRepeatDoesNotEndTimeSlow(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperSpeedHandlers.KEY);
		chargeTimeSlow(p);
		try {
			helper.assertTrue(SuperSpeedTimeSlow.isCasting(p), "the charge fired Time Slow");
			for (int i = 0; i < 5; i++) {
				AbilityRouter.handleInput(p, 4, true); // OS key-repeat while Z is still held
				ExperimentalPowers.serverTick(p);
			}
			helper.assertTrue(SuperSpeedTimeSlow.isCasting(p), "repeat presses while Z is still held keep it running");
			AbilityRouter.handleInput(p, 4, false); // let go
			helper.assertTrue(SuperSpeedTimeSlow.isCasting(p), "letting go keeps it running");
			AbilityRouter.handleInput(p, 4, true); // a fresh press
			helper.assertFalse(SuperSpeedTimeSlow.isCasting(p), "a fresh Z press after letting go ends it early");
		} finally {
			SuperSpeedTimeSlow.end(p, false);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v0148_ts_full")
	public void fullChargeFiresWithoutSpeedBoostsThenExhausts(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperSpeedHandlers.KEY);
		MinecraftServer server = helper.getLevel().getServer();
		float rateBefore = server.tickRateManager().tickrate();
		ExperimentalPowers.serverTick(p);
		AbilityRouter.handleInput(p, 6, true); // C: Speed Mode on
		ExperimentalPowers.serverTick(p);
		helper.assertTrue(SuperSpeedHandlers.speedMode(p) && hasModifier(p, Attributes.MOVEMENT_SPEED, "speed_mode_speed")
				&& hasModifier(p, Attributes.MOVEMENT_SPEED, "speed_passive_speed"), "Speed Mode and the passive boost you");
		int ticks = chargeTimeSlow(p);
		try {
			helper.assertTrue(SuperSpeedTimeSlow.isCasting(p), "a full 5 s charge fires Time Slow by itself");
			helper.assertTrue(ticks == SuperSpeedHandlers.TS_CHARGE_TICKS, "after exactly the charge time (" + ticks + ")");
			ExperimentalPowers.serverTick(p);
			helper.assertFalse(hasModifier(p, Attributes.MOVEMENT_SPEED, "speed_mode_speed")
					|| hasModifier(p, Attributes.MOVEMENT_SPEED, "speed_passive_speed")
					|| hasModifier(p, Attributes.STEP_HEIGHT, "speed_passive_step"),
					"no movement boost at all while Time Slow runs");
			helper.assertTrue(SuperSpeedHandlers.speedMode(p), "Speed Mode itself stays on (suspended, not lost)");
			AbilityRouter.handleInput(p, 4, false);
			AbilityRouter.handleInput(p, 4, true); // Z again: end it
		} finally {
			SuperSpeedTimeSlow.end(p, false);
		}
		helper.assertTrue(server.tickRateManager().tickrate() == rateBefore, "the tick rate is back");
		int cd = ExperimentalPowers.cooldownRemainingTicks(p, speed(), speed().ability(AbilitySlot.SLOT_4));
		int expected = HeroConfig.get().scaledCooldown(SuperSpeedTimeSlow.COOLDOWN_TICKS);
		helper.assertTrue(SuperSpeedTimeSlow.COOLDOWN_TICKS == 300 * 20 && Math.abs(cd - expected) <= 1,
				"300 s cooldown (" + cd + " / " + expected + ")");

		// exhausted: modes off, every key refused, no speed passives, the bar drains one tick at a time
		helper.assertTrue(SuperSpeedHandlers.exhausted(p), "exhausted after Time Slow");
		helper.assertFalse(SuperSpeedHandlers.speedMode(p), "Speed Mode is switched off");
		AbilityRouter.handleInput(p, 6, true);
		helper.assertFalse(SuperSpeedHandlers.speedMode(p), "and can't come back on");
		AbilityRouter.handleInput(p, 5, true);
		helper.assertFalse(SuperSpeedHandlers.overdrive(p), "Overdrive is refused");
		AbilityRouter.handleInput(p, 1, true);
		helper.assertTrue(ExperimentalPowers.cooldownReady(p, speed(), speed().ability(AbilitySlot.SLOT_1)), "R is refused");
		float left = res(p, SuperSpeedHandlers.KEY, SuperSpeedHandlers.EXHAUST);
		ExperimentalPowers.serverTick(p);
		float left2 = res(p, SuperSpeedHandlers.KEY, SuperSpeedHandlers.EXHAUST);
		helper.assertTrue(Math.abs(left - left2 - 1f) < 1e-3, "the exhaustion bar drains a tick per tick (" + left + " -> " + left2 + ")");
		helper.assertFalse(hasModifier(p, Attributes.MOVEMENT_SPEED, "speed_passive_speed"), "no speed passive while exhausted");
		for (int i = 0; i < SuperSpeedHandlers.EXHAUST_TICKS + 5 && SuperSpeedHandlers.exhausted(p); i++) {
			ExperimentalPowers.serverTick(p);
		}
		helper.assertFalse(SuperSpeedHandlers.exhausted(p), "the exhaustion wears off");
		ExperimentalPowers.serverTick(p);
		helper.assertTrue(hasModifier(p, Attributes.MOVEMENT_SPEED, "speed_passive_speed"), "and the passive speed is back");
		AbilityRouter.handleInput(p, 6, true);
		helper.assertTrue(SuperSpeedHandlers.speedMode(p), "Speed Mode can be switched on again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v0148_carry")
	public void carryHasNoTimeLimitButExhaustionDropsIt(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperSpeedHandlers.KEY);
		Zombie z = zombie(helper, 2.5, 3.5);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		AbilityRouter.handleInput(p, 8, true); // N
		helper.assertTrue(z.getVehicle() == p, "carried");
		for (int i = 0; i < 40 * 20; i++) { // longer than the old 30 s limit
			ExperimentalPowers.serverTick(p);
		}
		helper.assertTrue(z.getVehicle() == p, "still carried after 40 s: no time limit");
		SuperSpeedHandlers.startExhaustion(p);
		helper.assertTrue(z.getVehicle() == null, "exhaustion sets it down");
		helper.succeed();
	}

	/** v0.14.9: a carried creature that hops off on its own ends the carry cleanly -- N grabs again at once. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "v0149_carry_hop")
	public void carriedHopsOffEndsTheCarryCleanly(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SuperSpeedHandlers.KEY);
		Zombie z = zombie(helper, 2.5, 3.5);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		AbilityRouter.handleInput(p, 8, true); // N
		helper.assertTrue(z.getVehicle() == p, "carried");
		z.stopRiding(); // hops off by itself (a player pressing Sneak)
		ExperimentalPowers.serverTick(p);
		helper.assertTrue(p.getPassengers().isEmpty(), "nobody left on the carrier");
		helper.assertTrue(z.getVehicle() == null, "the creature is free");
		p.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		AbilityRouter.handleInput(p, 8, false);
		AbilityRouter.handleInput(p, 8, true); // N again: a fresh grab, not a 'release'
		helper.assertTrue(z.getVehicle() == p, "N picks it straight back up");
		helper.succeed();
	}

	// ---------------- Laser Vision ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "v0148_lv_vent")
	public void laserHeatWaitsFiveSecondsThenCoolsThreeASecond(GameTestHelper helper) {
		ServerPlayer p = hero(helper, LaserVisionHandlers.KEY);
		String k = LaserVisionHandlers.KEY;
		LaserVisionHandlers.addHeat(p, 40f);
		for (int i = 0; i < LaserVisionHandlers.VENT_DELAY - 10; i++) {
			ExperimentalPowers.serverTick(p);
		}
		helper.assertTrue(Math.abs(res(p, k, "heat") - 40f) < 1e-3, "no venting in the first 5 s (" + res(p, k, "heat") + ")");
		LaserVisionHandlers.addHeat(p, 2f); // using Laser Vision again restarts the 5 s
		for (int i = 0; i < LaserVisionHandlers.VENT_DELAY - 10; i++) {
			ExperimentalPowers.serverTick(p);
		}
		helper.assertTrue(Math.abs(res(p, k, "heat") - 42f) < 1e-3, "every use restarts the wait (" + res(p, k, "heat") + ")");
		for (int i = 0; i < 40 && res(p, k, "heat") >= 42f - 1e-3; i++) {
			ExperimentalPowers.serverTick(p);
		}
		float a = res(p, k, "heat");
		helper.assertTrue(a < 42f, "then it vents");
		ExperimentalPowers.serverTick(p);
		float b = res(p, k, "heat");
		helper.assertTrue(Math.abs((a - b) - 0.15f) < 1e-3, "at 3 heat a second = 0.15 a tick (" + a + " -> " + b + ")");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v0148_lv_damage")
	public void laserDamageIgnoresHeat(GameTestHelper helper) {
		ServerPlayer p = hero(helper, LaserVisionHandlers.KEY);
		Power lv = Powers.byKey(LaserVisionHandlers.KEY);
		float[] dealt = new float[2];
		float[] heat = { 0f, 80f };
		for (int i = 0; i < 2; i++) {
			Zombie z = zombie(helper, 2.5, 4.5);
			ExperimentalPowers.setResource(p, lv, "heat", heat[i], 1e9f);
			p.setShiftKeyDown(true);
			AbilityRouter.handleInput(p, 1, true); // Shift+R Piercing Blast
			AbilityRouter.handleInput(p, 1, false);
			p.setShiftKeyDown(false);
			dealt[i] = 100f - z.getHealth();
			z.discard();
			ExperimentalPowers.advanceCooldowns(p, 100000);
		}
		helper.assertTrue(dealt[0] > 0f, "the blast hits (" + dealt[0] + ")");
		helper.assertTrue(Math.abs(dealt[0] - dealt[1]) < 1e-3, "a hot blast hits exactly as hard as a cold one ("
				+ dealt[0] + " vs " + dealt[1] + ")");
		helper.succeed();
	}

	// ---------------- disabled experimental powers ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void onlyTheFourRemadePowersAreEnabled(GameTestHelper helper) {
		java.util.Set<String> keys = new java.util.HashSet<>();
		for (Power p : Powers.enabled()) {
			keys.add(p.key());
		}
		helper.assertTrue(keys.equals(java.util.Set.of("power_01_super_strength", "power_02_laser_vision", "power_04_super_speed",
				"power_12_super_regeneration")), "enabled: " + keys);
		helper.assertTrue(Powers.count() - Powers.enabled().size() == 22, "22 disabled, got " + (Powers.count() - keys.size()));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void joinPrunesDisabledPowers(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Power flight = Powers.byKey("power_03_flight");
		Power strength = Powers.byKey("power_01_super_strength");
		ExperimentalPowers.grant(p, flight); // a save from before v0.14.8 (grant itself is ungated)
		ExperimentalPowers.grant(p, strength);
		ExperimentalPowers.setActive(p, flight);
		helper.assertTrue(ExperimentalPowers.pruneRemovedPowers(p), "something was pruned");
		helper.assertFalse(ExperimentalPowers.owns(p, flight), "the disabled power is gone");
		helper.assertTrue(ExperimentalPowers.owns(p, strength), "the enabled one stays");
		helper.assertTrue(strength.equals(ExperimentalPowers.getActive(p)), "and becomes the active power");
		helper.assertFalse(ExperimentalPowers.pruneRemovedPowers(p), "a second prune finds nothing");

		ServerPlayer q = helper.makeMockServerPlayerInLevel();
		ExperimentalPowers.grant(q, flight);
		ExperimentalPowers.pruneRemovedPowers(q);
		helper.assertTrue(ExperimentalPowers.getActive(q) == null && ExperimentalPowers.ownedCount(q) == 0,
				"with only disabled powers the player is left with none");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void disabledPowersCannotBeObtained(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Power flight = Powers.byKey("power_03_flight");
		Power speedPower = speed();
		helper.assertFalse(PowerGrants.grantExperimental(p, flight), "a random serum can't hand out a disabled power");
		helper.assertFalse(PowerGrants.missingExperimental(p).contains(flight), "nor even roll it");
		helper.assertTrue(PowerGrants.missingExperimental(p).contains(speedPower), "v0.14.21: Super Speed is a mutation again");
		helper.assertTrue(PowerGrants.missingExperimental(p).contains(Powers.byKey("power_01_super_strength")), "an enabled mutation it can");
		Power strengthPower = Powers.byKey("power_01_super_strength");

		// items: reagents / serums / research notes of disabled powers are recognised, enabled ones are not
		helper.assertTrue(PowerItems.isDisabledPowerItem(new ItemStack(HeroPackItems.reagent(flight))), "flight reagent");
		helper.assertFalse(PowerItems.isDisabledPowerItem(new ItemStack(HeroPackItems.reagent(strengthPower))), "strength reagent");
		helper.assertFalse(PowerItems.isDisabledPowerItem(new ItemStack(HeroPackItems.reagent(speedPower))), "v0.14.21: the speed reagent is back");
		helper.assertFalse(PowerItems.isDisabledPowerItem(PotionContents.createItemStack(Items.POTION, ModSerums.serum(speedPower))),
				"v0.14.21: and its serum");
		helper.assertTrue(PowerItems.isDisabledPowerItem(PotionContents.createItemStack(Items.POTION, ModSerums.serum(flight))),
				"flight serum");
		helper.assertFalse(PowerItems.isDisabledPowerItem(PotionContents.createItemStack(Items.POTION, ModSerums.serum(strengthPower))),
				"strength serum");
		helper.assertTrue(PowerItems.isDisabledPowerItem(com.projecthero.mod.hero.item.ResearchNoteItem.forPower(flight, 1)),
				"flight research note");

		// loot: a table that can only roll the disabled reagent yields nothing; the enabled reagent comes through
		var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY);
		LootTable off = LootTable.lootTable().withPool(LootPool.lootPool().add(LootItem.lootTableItem(HeroPackItems.reagent(flight))))
				.build();
		LootTable on = LootTable.lootTable().withPool(LootPool.lootPool().add(LootItem.lootTableItem(HeroPackItems.reagent(strengthPower))))
				.build();
		helper.assertTrue(off.getRandomItems(params).isEmpty(), "loot never drops a disabled power's reagent");
		helper.assertTrue(on.getRandomItems(params).size() == 1, "an enabled power's reagent still drops");
		helper.succeed();
	}
}
