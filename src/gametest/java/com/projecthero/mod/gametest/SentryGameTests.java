package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.sentry.Sentry;
import com.projecthero.mod.sentry.SentryAbilityManager;
import com.projecthero.mod.sentry.SentryConfig;
import com.projecthero.mod.sentry.LetItOut;
import com.projecthero.mod.sentry.SentryDamage;
import com.projecthero.mod.sentry.SentryFlight;
import com.projecthero.mod.sentry.SentryMoves;
import com.projecthero.mod.sentry.VoidMoves;
import com.projecthero.mod.sentry.block.SentryBlocks;
import com.projecthero.mod.sentry.block.SerumVatBlock;
import com.projecthero.mod.sentry.data.SentryAttachments;
import com.projecthero.mod.sentry.data.SentryState;
import com.projecthero.mod.sentry.item.SentryItems;
import com.projecthero.mod.sentry.item.SentrySuitItem;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Sentry (round 3, 2026-11-18: the new key layout, Solar Gaze at Heat Vision's numbers with no cap, Solar Flare, Golden Sense,
 * the Flight Boost, Umbral Shroud / Shadow Meld, +50% merged, the Void rising only from the powers and draining by sleep and calm tasks,
 * the controlled-Void suit look with and without the suit, H = Let It Out, the landing slam): the grant, the two meters (step sizes per
 * second, never elapsed totals), what feeds and drains the Void, the damage
 * rules, every Sentry move and every Void move of moveset 2 (2026-11-18) on a zombie, merged buffs included, (hits asserted as ratios: other tests can leave the world on Hard), the
 * Void Form trial (v0.17.5 rework: Let It Out won or lost, Shift+V back below 90), double-tap-Space flight and the X dash, the
 * craftable self-mending suit and its Void / Sun bonus, 40 max health in both forms, and where the serum comes from (only The Vault's vat: no recipe, never a random
 * serum). Mock players aren't ticked, so the meters and moves are ticked by hand.
 */
public class SentryGameTests implements FabricGameTest {
	static ServerPlayer mock(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		try { // a fresh mock player is spawn-protected
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	static ServerPlayer sentry(GameTestHelper helper, double x, double z) {
		ServerPlayer p = mock(helper, x, z);
		helper.assertTrue(Sentry.grant(p), "the grant works");
		return p;
	}

	static void edit(ServerPlayer p, java.util.function.Consumer<SentryState> change) {
		SentryState n = Sentry.state(p).copy();
		change.accept(n);
		p.setAttached(SentryAttachments.STATE, n);
	}

	static Zombie zombie(GameTestHelper helper, double x, double z, double maxHealth) {
		Zombie zb = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(x, 2.0, z));
		zb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		zb.setHealth((float) maxHealth);
		zb.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		return zb;
	}

	static void look(ServerPlayer p, LivingEntity at) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, at.position().add(0, at.getBbHeight() * 0.55, 0));
	}

	static boolean near(float got, float expected) {
		return got > expected * 0.8f && got < expected * 1.2f;
	}

	// ---------------------------------------------------------------- grant and meters

	@GameTest(template = EMPTY_STRUCTURE)
	public void grantGivesFullSunAndNoVoid(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		SentryState s = Sentry.state(p);
		helper.assertTrue(s.hasPower && s.sun == SentryConfig.SUN_MAX && s.voidLevel == 0f, "full Sun, no Void");
		helper.assertTrue(HeroTiers.holdsHero(p, Sentry.KEY), "a Hero-Tier power");
		helper.assertTrue(p.getAttribute(Attributes.MAX_HEALTH).getValue() == 40.0, "40 max health");
		helper.assertFalse(Sentry.grant(p), "no second grant");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sunAndVoidStepWithTheLight(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		edit(p, s -> {
			s.sun = 50f;
			s.voidLevel = 40f;
		});
		for (Sentry.Sun sun : Sentry.Sun.values()) {
			Sentry.pinSun(p, sun);
			float sunBefore = Sentry.state(p).sun;
			float voidBefore = Sentry.state(p).voidLevel;
			Sentry.tickMeters(p);
			float dSun = Sentry.state(p).sun - sunBefore;
			float dVoid = Sentry.state(p).voidLevel - voidBefore;
			helper.assertTrue(Math.abs(dSun - Sentry.sunPerSecond(sun)) < 1.0e-3f, sun + ": Sun step " + dSun);
			helper.assertTrue(Math.abs(dVoid - Sentry.voidPerSecond(sun)) < 1.0e-3f, sun + ": Void step " + dVoid);
		}
		helper.assertTrue(Sentry.sunPerSecond(Sentry.Sun.DIRECT) > Sentry.sunPerSecond(Sentry.Sun.NIGHT)
				&& Sentry.sunPerSecond(Sentry.Sun.DARK) == 0f, "fast in daylight, slow at night, none in the dark");
		helper.assertTrue(Sentry.voidPerSecond(Sentry.Sun.DIRECT) < Sentry.voidPerSecond(Sentry.Sun.DARK) && Sentry.voidPerSecond(Sentry.Sun.DARK) < 0f
				&& Sentry.voidPerSecond(Sentry.Sun.NIGHT) < 0f, "round 3: it drains everywhere, faster in sunlight; night and the dark feed nothing");
		Sentry.pinSun(p, null);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void voidNeverRisesAtNightOrInTheDarkAndDrainsByDefault(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		edit(p, s -> s.voidLevel = 60f);
		for (Sentry.Sun sun : new Sentry.Sun[] { Sentry.Sun.NIGHT, Sentry.Sun.DARK, Sentry.Sun.SHADE }) {
			Sentry.pinSun(p, sun);
			float before = Sentry.state(p).voidLevel;
			Sentry.tickMeters(p);
			float step = Sentry.state(p).voidLevel - before;
			helper.assertTrue(Math.abs(step - SentryConfig.VOID_DRAIN_PER_SECOND) < 1.0e-4f, sun + ": the slow drain, not a gain (" + step + ")");
		}
		Sentry.pinSun(p, Sentry.Sun.DIRECT);
		float before = Sentry.state(p).voidLevel;
		Sentry.tickMeters(p);
		float step = Sentry.state(p).voidLevel - before;
		helper.assertTrue(Math.abs(step - (SentryConfig.VOID_DRAIN_PER_SECOND + SentryConfig.VOID_DAYLIGHT_PER_SECOND)) < 1.0e-4f, "faster in sunlight: " + step);
		helper.assertTrue(SentryConfig.VOID_DRAIN_PER_SECOND < 0f && SentryConfig.VOID_DRAIN_PER_SECOND > -0.2f, "slowly");
		Sentry.pinSun(p, null);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sleepingDrainsTheVoidALot(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		Sentry.pinSun(p, Sentry.Sun.DARK);
		edit(p, s -> s.voidLevel = 80f);
		p.startSleeping(p.blockPosition());
		helper.assertTrue(p.isSleeping(), "in bed");
		float before = Sentry.state(p).voidLevel;
		Sentry.tickMeters(p);
		float step = Sentry.state(p).voidLevel - before;
		helper.assertTrue(Math.abs(step - (SentryConfig.VOID_DRAIN_PER_SECOND + SentryConfig.VOID_SLEEP_PER_SECOND)) < 1.0e-4f, "asleep: -1 more a second (" + step + ")");
		p.stopSleeping(); // (the real wake hook may already take the night's chunk if the test world's clock reads morning)
		edit(p, s -> s.voidLevel = 80f);
		before = Sentry.state(p).voidLevel;
		Sentry.woke(p, false);
		helper.assertTrue(Sentry.state(p).voidLevel == before, "out of bed early: no chunk");
		Sentry.woke(p, true);
		helper.assertTrue(Math.abs(Sentry.state(p).voidLevel - (before + SentryConfig.VOID_NIGHT_SLEPT)) < 1.0e-3f, "a night slept through: -40");
		helper.assertTrue(SentryConfig.VOID_NIGHT_SLEPT <= -30f, "a lot");
		Sentry.pinSun(p, null);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void calmTasksDrainTheVoid(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		edit(p, s -> s.voidLevel = 50f);
		float before = Sentry.state(p).voidLevel;
		com.projecthero.mod.sentry.SentryCalmTasks.fished(p);
		helper.assertTrue(Math.abs(Sentry.state(p).voidLevel - (before + SentryConfig.VOID_TASK_FISH)) < 1.0e-3f, "a fish reeled in: -2");
		var wheat = (net.minecraft.world.level.block.CropBlock) net.minecraft.world.level.block.Blocks.WHEAT;
		helper.assertFalse(com.projecthero.mod.sentry.SentryCalmTasks.ripe(wheat.getStateForAge(3)), "a young crop isn't a harvest");
		helper.assertTrue(com.projecthero.mod.sentry.SentryCalmTasks.ripe(wheat.getStateForAge(wheat.getMaxAge())), "a ripe one is");
		before = Sentry.state(p).voidLevel;
		com.projecthero.mod.sentry.SentryCalmTasks.broke(p, wheat.getStateForAge(wheat.getMaxAge()));
		helper.assertTrue(Math.abs(Sentry.state(p).voidLevel - (before + SentryConfig.VOID_TASK_HARVEST)) < 1.0e-3f, "a harvest: -0.4");
		before = Sentry.state(p).voidLevel;
		com.projecthero.mod.sentry.SentryCalmTasks.broke(p, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
		helper.assertTrue(Sentry.state(p).voidLevel == before, "mining isn't a calm task");
		// the suit's gain cut never touches a drain
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SentrySuitItem.TOP));
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(SentrySuitItem.TROUSERS));
		before = Sentry.state(p).voidLevel;
		com.projecthero.mod.sentry.SentryCalmTasks.task(p, SentryConfig.VOID_TASK_TRADE);
		helper.assertTrue(Math.abs(Sentry.state(p).voidLevel - (before + SentryConfig.VOID_TASK_TRADE)) < 1.0e-3f, "a trade: -1.5, unscaled");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void voidRisesOnlyFromThePowers(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		SentryMoves.millionSuns(p);
		helper.assertTrue(Math.abs(Sentry.state(p).voidLevel - SentryConfig.SUNS_VOID) < 1.0e-3f, "A Million Suns: +8 Void");
		// a kill: nothing (round 3)
		float before = Sentry.state(p).voidLevel;
		Zombie z = zombie(helper, 3.5, 2, 4);
		Sentry.hit(p, z, 50f, false);
		helper.assertTrue(!z.isAlive(), "killed");
		helper.assertTrue(Sentry.state(p).voidLevel == before, "a kill no longer feeds the Void");
		// a heavy hit: nothing (round 3)
		before = Sentry.state(p).voidLevel;
		p.setHealth(p.getMaxHealth());
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().generic(), 40f); // (no difficulty scaling: the test world may be on Peaceful)
		helper.assertTrue(Sentry.state(p).voidLevel == before, "a heavy hit no longer feeds the Void");
		// healing others lowers it
		before = Sentry.state(p).voidLevel;
		Sentry.setSun(p, 100f);
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		helper.assertTrue(Sentry.state(p).voidLevel <= before + SentryConfig.GUARDIAN_VOID + 1.0e-3f, "Golden Guardian: -8 Void at least");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void damageTakenIsCut(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		var src = p.damageSources().generic();
		helper.assertTrue(Math.abs(SentryDamage.factor(p, src) - (1f - SentryConfig.SENTRY_RESIST)) < 1.0e-3f, "70% off as the Sentry");
		edit(p, s -> s.voidForm = true);
		helper.assertTrue(Math.abs(SentryDamage.factor(p, src) - (1f - SentryConfig.VOID_RESIST)) < 1.0e-3f, "75% off in Void Form");
		edit(p, s -> {
			s.voidForm = false;
			s.flying = true;
		});
		helper.assertTrue(SentryDamage.factor(p, p.damageSources().fall()) == 0f, "no fall damage flying");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the moves damage

	@GameTest(template = EMPTY_STRUCTURE)
	public void millionSunsIsTheBigHitter(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		Zombie z = zombie(helper, 3.0, 2, 1000);
		z.setHealth(1000f);
		z.invulnerableTime = 0;
		SentryMoves.millionSuns(p);
		float lost = 1000f - z.getHealth();
		double d = Math.sqrt(z.distanceToSqr(p.position().add(0, 1.0, 0))) / SentryConfig.SUNS_RADIUS;
		float expected = (float) (SentryConfig.SUNS_DAMAGE + (SentryConfig.SUNS_EDGE_DAMAGE - SentryConfig.SUNS_DAMAGE) * Math.min(1.0, d));
		helper.assertTrue(near(lost, expected), "A Million Suns ~" + expected + ", got " + lost);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void millionSunsChargesFourSeconds(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		Zombie z = zombie(helper, 3.0, 2, 1000);
		edit(p, s -> s.abilityReadyAt.clear());
		p.setShiftKeyDown(true);
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_4, true);
		p.setShiftKeyDown(false);
		helper.assertTrue(Sentry.state(p).sunsStart != 0L, "charging");
		helper.assertTrue(Sentry.state(p).sun <= SentryConfig.SUN_MAX - SentryConfig.SUNS_COST + 1.0e-3f, "60 Sun paid");
		// mock players are ticked by the server like everyone else (AbilityRouter.serverTick), so the charge runs on its own
		helper.runAfterDelay(SentryConfig.SUNS_CHARGE_TICKS - 10, () -> {
			helper.assertTrue(Sentry.state(p).sunsStart != 0L, "still charging before 4 s");
			z.setHealth(1000f);
		});
		helper.runAfterDelay(SentryConfig.SUNS_CHARGE_TICKS + 5, () -> {
			helper.assertTrue(Sentry.state(p).sunsStart == 0L, "the charge is over");
			helper.assertTrue(z.getHealth() < 1000f - 60f, "the light went off after 4 s (zombie " + z.getHealth() + ")");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- moveset 2 (2026-11-18): the Sentry

	@GameTest(template = EMPTY_STRUCTURE)
	public void keysMapBothNewKits(GameTestHelper helper) {
		for (AbilitySlot slot : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_6 }) {
			helper.assertTrue(SentryAbilityManager.idsOf(slot, false).length == 2, "Sentry " + slot + ": a plain and a Shift move");
			helper.assertTrue(SentryAbilityManager.idsOf(slot, true).length == 2, "Void " + slot + ": a plain and a Shift move");
		}
		helper.assertTrue(SentryAbilityManager.DASH.equals(SentryAbilityManager.sentryMoveFor(AbilitySlot.SLOT_3, false)), "X: the dash (movement)");
		helper.assertTrue(SentryAbilityManager.SHADOW_STEP.equals(SentryAbilityManager.voidMoveFor(AbilitySlot.SLOT_3, true)), "merged X: Shadow Step");
		// round 3: the user's layout
		String[][] sentry = { { "SLOT_1", "golden_fury", "solar_flare" }, { "SLOT_2", "solar_gaze", "disassemble" }, { "SLOT_4", "sunfall", "million_suns" },
				{ "SLOT_6", "golden_guardian", "skyward_hurl" }, { "SLOT_5", "molecular_shield", "golden_sense" } };
		for (String[] row : sentry) {
			AbilitySlot slot = AbilitySlot.valueOf(row[0]);
			helper.assertTrue(row[1].equals(SentryAbilityManager.sentryMoveFor(slot, false)) && row[2].equals(SentryAbilityManager.sentryMoveFor(slot, true)),
					"Sentry " + slot + ": " + row[1] + " / Shift " + row[2]);
		}
		String[][] dark = { { "SLOT_1", "tendril_lash", "erase" }, { "SLOT_2", "shadow_pool", "void_sphere" }, { "SLOT_4", "nightmare", "shadow_eclipse" },
				{ "SLOT_6", "blot_out", "shadow_clones" }, { "SLOT_5", "umbral_shroud", "shadow_meld" } };
		for (String[] row : dark) {
			AbilitySlot slot = AbilitySlot.valueOf(row[0]);
			helper.assertTrue(row[1].equals(SentryAbilityManager.voidMoveFor(slot, false)) && row[2].equals(SentryAbilityManager.voidMoveFor(slot, true)),
					"Void " + slot + ": " + row[1] + " / Shift " + row[2]);
		}
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (AbilitySlot slot : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5, AbilitySlot.SLOT_6 }) {
			for (boolean shift : new boolean[] { false, true }) {
				if (slot != AbilitySlot.SLOT_3) {
					helper.assertTrue(seen.add("s" + SentryAbilityManager.sentryMoveFor(slot, shift)), "no Sentry duplicate: " + SentryAbilityManager.sentryMoveFor(slot, shift));
					helper.assertTrue(seen.add("v" + SentryAbilityManager.voidMoveFor(slot, shift)), "no Void duplicate: " + SentryAbilityManager.voidMoveFor(slot, shift));
				}
			}
		}
		// an old save with moveset 1 cooldown ids loads and is harmless
		ServerPlayer p = sentry(helper, 2, 2);
		edit(p, s -> {
			s.abilityReadyAt.put("sunbeam", p.level().getGameTime() + 100);
			s.abilityReadyAt.put("grasp", p.level().getGameTime() + 100);
		});
		SentryState back = SentryState.load(Sentry.state(p).save());
		p.setAttached(SentryAttachments.STATE, back);
		helper.assertTrue(Sentry.cooldownRemaining(p, "sunbeam") > 0 && SentryAbilityManager.maxCooldown("sunbeam") == 1, "old ids load, unused");
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void goldenFuryComboEndsInALaunchingHaymaker(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 1.5, 2);
		Zombie z = zombie(helper, 3.5, 2, 250);
		look(p, z);
		float[] got = new float[3];
		for (int i = 0; i < 3; i++) {
			edit(p, s -> s.abilityReadyAt.clear());
			look(p, z);
			float hp = z.getHealth();
			SentryAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
			got[i] = hp - z.getHealth();
		}
		helper.assertTrue(near(got[0], SentryConfig.FURY_DAMAGE), "first punch ~22, got " + got[0]);
		helper.assertTrue(near(got[2], SentryConfig.FURY_HAYMAKER_DAMAGE), "the Haymaker ~34, got " + got[2]);
		helper.assertTrue(z.getDeltaMovement().horizontalDistance() >= SentryConfig.FURY_LAUNCH_SPEED * 0.9, "launched far: " + z.getDeltaMovement());
		helper.assertTrue(SentryMoves.launched(p) == z.getId(), "tracked as launched (the wall-slam check)");
		helper.assertTrue(Math.abs(Sentry.state(p).sun - (SentryConfig.SUN_MAX - 3 * SentryConfig.FURY_COST)) < 1.0e-3f, "2 Sun a punch");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void goldenFuryLungesAtSomethingOutOfReach(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 1.5, 1.5);
		Zombie z = zombie(helper, 1.5, 8.0, 500);
		look(p, z);
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(z.getHealth() < 500f, "a creature 6.5 away is still punched (the lunge)");
		helper.assertTrue(p.getDeltaMovement().z > 0.5, "he is thrown at it: " + p.getDeltaMovement());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void solarGazeHasNoTimeCapAndMatchesHeatVision(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 1, 2);
		Zombie z = zombie(helper, 6, 2, 500);
		look(p, z);
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_2, true); // round 3: G
		helper.assertTrue(Sentry.state(p).beamStart != 0L, "the gaze is on");
		SentryMoves.tick(p);
		helper.assertTrue(near(500f - z.getHealth(), SentryConfig.GAZE_DAMAGE), "first burn ~4, got " + (500f - z.getHealth()));
		helper.assertTrue(z.isOnFire(), "it sets the target alight");
		// Heat Vision parity: the same damage a second, range and fire
		float gazeDps = SentryConfig.GAZE_DAMAGE * 20f / SentryConfig.GAZE_DAMAGE_INTERVAL;
		float heatDps = com.projecthero.mod.kryptonian.KryptonianConfig.HEAT_DAMAGE * 20f / com.projecthero.mod.kryptonian.KryptonianConfig.HEAT_HIT_INTERVAL;
		helper.assertTrue(Math.abs(gazeDps - heatDps) < 1.0e-3f, "the same DPS as heat vision: " + gazeDps + " vs " + heatDps);
		helper.assertTrue(SentryConfig.GAZE_RANGE == com.projecthero.mod.kryptonian.KryptonianConfig.HEAT_RANGE
				&& SentryConfig.GAZE_FIRE_SECONDS == com.projecthero.mod.kryptonian.KryptonianConfig.HEAT_FIRE_SECONDS, "same range and fire");
		// no time cap: held a whole minute on, still burning
		long t0 = p.level().getGameTime();
		edit(p, s -> s.beamStart = t0 - 1200);
		z.setHealth(500f);
		z.invulnerableTime = 0;
		SentryMoves.tick(p);
		helper.assertTrue(Sentry.state(p).beamStart != 0L, "still on after 60 s held");
		helper.assertTrue(z.getHealth() < 500f, "still burning after 60 s");
		// an exact ray: a creature just beside the line is not hit
		Zombie side = zombie(helper, 3.5, 3.4, 200);
		edit(p, s -> s.beamStart = t0 - 1195);
		SentryMoves.tick(p);
		helper.assertTrue(side.getHealth() == 200f, "no snapping onto a creature beside the beam");
		// one second in: a quarter-second's Sun is paid and the Void stirs
		float sun = Sentry.state(p).sun;
		float vd = Sentry.state(p).voidLevel;
		long now = p.level().getGameTime();
		edit(p, s -> s.beamStart = now - 5);
		SentryMoves.tick(p);
		helper.assertTrue(Math.abs(sun - Sentry.state(p).sun - SentryConfig.GAZE_COST_PER_SECOND / 4f) < 1.0e-3f, "Sun step per 5 ticks");
		helper.assertTrue(Sentry.state(p).voidLevel > vd, "the Void stirs");
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
		helper.assertTrue(Sentry.state(p).beamStart == 0L, "G released: it ends");
		helper.assertTrue(Sentry.cooldownRemaining(p, SentryAbilityManager.SOLAR_GAZE) == SentryConfig.GAZE_COOLDOWN, "its cooldown starts when it ends");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void disassembleUnravelsThenComesApart(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 1, 2);
		Zombie z = zombie(helper, 4, 2, 500);
		look(p, z);
		p.setShiftKeyDown(true); // round 3: Shift+G
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		p.setShiftKeyDown(false);
		helper.assertTrue(Sentry.state(p).gripTarget == z.getId(), "held");
		helper.assertTrue(Math.abs(Sentry.state(p).sun - (SentryConfig.SUN_MAX - SentryConfig.UNMAKE_COST)) < 1.0e-3f, "10 Sun");
		helper.runAfterDelay(SentryConfig.UNMAKE_TEAR_TICKS / 2, () -> {
			helper.assertTrue(z.getHealth() < 500f && Sentry.state(p).gripTarget == z.getId(), "unravelling while held");
		});
		helper.runAfterDelay(SentryConfig.UNMAKE_TEAR_TICKS + 4, () -> {
			float lost = 500f - z.getHealth();
			float ticks = SentryConfig.UNMAKE_TEAR_TICKS / (float) SentryConfig.UNMAKE_TICK_INTERVAL * SentryConfig.UNMAKE_TICK_DAMAGE;
			helper.assertTrue(lost >= SentryConfig.UNMAKE_TEAR_DAMAGE * 0.8f && lost <= (SentryConfig.UNMAKE_TEAR_DAMAGE + ticks) * 1.3f,
					"came apart (~50 + the unravelling), lost " + lost);
			helper.assertTrue(Sentry.state(p).gripTarget < 0, "it ends");
			helper.assertTrue(Sentry.state(p).voidLevel >= SentryConfig.UNMAKE_TEAR_VOID * 0.99f, "+1.7 Void");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void disassembleLetGoEarlyThrows(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 1, 2);
		Zombie z = zombie(helper, 4, 2, 250);
		look(p, z);
		p.setShiftKeyDown(true); // round 3: Shift+G
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		p.setShiftKeyDown(false);
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
		helper.assertTrue(near(250f - z.getHealth(), SentryConfig.UNMAKE_THROW_DAMAGE), "thrown ~12, got " + (250f - z.getHealth()));
		helper.assertTrue(z.getDeltaMovement().length() > 1.5, "flying: " + z.getDeltaMovement());
		helper.assertTrue(Sentry.cooldownRemaining(p, SentryAbilityManager.DISASSEMBLE) == SentryConfig.UNMAKE_COOLDOWN, "the cooldown starts");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void skywardHurlThrowsItUpOrSlamsABoss(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 1.5, 2);
		Zombie z = zombie(helper, 3.5, 2, 100);
		look(p, z);
		p.setShiftKeyDown(true); // round 3: Shift+C
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		p.setShiftKeyDown(false);
		helper.assertTrue(near(100f - z.getHealth(), SentryConfig.HURL_DAMAGE), "~16, got " + (100f - z.getHealth()));
		helper.assertTrue(z.getDeltaMovement().y >= SentryConfig.HURL_SPEED * 0.95, "straight up: " + z.getDeltaMovement());
		helper.assertTrue(Math.abs(Sentry.state(p).sun - (SentryConfig.SUN_MAX - SentryConfig.HURL_COST)) < 1.0e-3f, "14 Sun");
		helper.assertTrue(Sentry.cooldownRemaining(p, SentryAbilityManager.SKYWARD_HURL) == SentryConfig.HURL_COOLDOWN, "10 s cooldown");
		// too big to throw: slammed
		ServerPlayer p2 = sentry(helper, 5.5, 2);
		Zombie big = zombie(helper, 5.5, 4, 400);
		look(p2, big);
		p2.setShiftKeyDown(true);
		SentryAbilityManager.handle(p2, AbilitySlot.SLOT_6, true);
		p2.setShiftKeyDown(false);
		helper.assertTrue(near(400f - big.getHealth(), SentryConfig.HURL_BOSS_DAMAGE), "a boss-sized one: ~30, got " + (400f - big.getHealth()));
		helper.assertTrue(big.getDeltaMovement().y < 1.0, "not thrown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sunfallLeapsThenSlamsInTwoRings(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 0.5, 0.5);
		p.setOnGround(true);
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_4, true);
		helper.assertTrue(SentryMoves.sunfallPhase(p) == 0, "from the ground: the leap first");
		helper.assertTrue(Math.abs(p.getDeltaMovement().y - SentryConfig.SUNFALL_LEAP) < 1.0e-3, "up at the leap speed: " + p.getDeltaMovement().y);
		helper.assertTrue(Math.abs(Sentry.state(p).sun - (SentryConfig.SUN_MAX - SentryConfig.SUNFALL_COST)) < 1.0e-3f, "12 Sun");
		Zombie inner = zombie(helper, 3.0, 0.5, 500);
		Zombie outer = zombie(helper, 7.0, 4.5, 500); // ~7.6 out: the outer ring
		SentryMoves.slam(p);
		helper.assertTrue(near(500f - inner.getHealth(), SentryConfig.SUNFALL_DAMAGE), "inner ~34, got " + (500f - inner.getHealth()));
		helper.assertTrue(near(500f - outer.getHealth(), SentryConfig.SUNFALL_RING_DAMAGE), "outer ring ~12, got " + (500f - outer.getHealth()));
		helper.assertTrue(SentryDamage.factor(p, p.damageSources().fall()) == 0f, "no fall damage while it runs");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void goldenGuardianHealsAlliesAndBurnsTheUndead(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		edit(p, s -> s.voidLevel = 30f);
		p.setHealth(20f);
		Zombie z = zombie(helper, 5, 2, 200);
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		helper.assertTrue(p.getHealth() >= 20f + SentryConfig.GUARDIAN_HEAL - 0.01f, "healed 8: " + p.getHealth());
		helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)
				&& p.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION), "Regeneration and Absorption");
		helper.assertTrue(near(200f - z.getHealth(), SentryConfig.GUARDIAN_UNDEAD_DAMAGE), "the undead burn ~12, got " + (200f - z.getHealth()));
		helper.assertTrue(Sentry.state(p).voidLevel <= 30f + SentryConfig.GUARDIAN_VOID + 1.0e-3f, "the Void falls 8 at least");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void molecularShieldTurnsShotsToLightAndDeflects(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 3, 3);
		Sentry.setSun(p, 50f);
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_5, true); // round 3: V
		helper.assertTrue(SentryMoves.shielded(p), "the aura is up");
		float sun = Sentry.state(p).sun;
		helper.assertTrue(Math.abs(sun - (50f - SentryConfig.MSHIELD_COST)) < 1.0e-3f, "15 Sun");
		var arrow = new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, helper.getLevel());
		arrow.setPos(p.getX() + 1.5, p.getY() + 1.0, p.getZ());
		helper.getLevel().addFreshEntity(arrow);
		SentryMoves.tick(p);
		helper.assertTrue(arrow.isRemoved(), "the arrow is turned to light");
		helper.assertTrue(Math.abs(Sentry.state(p).sun - sun - SentryConfig.MSHIELD_SUN_PER_PROJECTILE) < 1.0e-3f, "+2 Sun from it");
		var src = p.damageSources().generic();
		float f = SentryDamage.factor(p, src);
		helper.assertTrue(Math.abs(f - (1f - SentryConfig.SENTRY_RESIST) * (1f - SentryConfig.MSHIELD_RESIST)) < 1.0e-3f, "40% off on top: " + f);
		Zombie z = zombie(helper, 4, 3, 200);
		SentryDamage.allowDamage(p, p.damageSources().mobAttack(z), 4f);
		helper.assertTrue(near(200f - z.getHealth(), SentryConfig.MSHIELD_DEFLECT_DAMAGE), "melee deflected ~6, got " + (200f - z.getHealth()));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void millionSunsHalvesDamageWhileCharging(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		var src = p.damageSources().generic();
		float before = SentryDamage.factor(p, src);
		p.setShiftKeyDown(true);
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_4, true);
		p.setShiftKeyDown(false);
		helper.assertTrue(SentryMoves.chargingSuns(p), "charging");
		helper.assertTrue(Math.abs(SentryDamage.factor(p, src) - before * (1f - SentryConfig.SUNS_CHARGE_RESIST)) < 1.0e-3f, "half again while charging");
		edit(p, s -> s.sunsStart = 0L); // (no explosion over the neighbouring tests)
		helper.succeed();
	}

	// ---------------------------------------------------------------- round 3: the new Sentry moves, the boost, the landing

	@GameTest(template = EMPTY_STRUCTURE)
	public void solarFlareBurstsAllAround(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 3.5, 3.5);
		Zombie front = zombie(helper, 5.5, 3.5, 300);
		Zombie back = zombie(helper, 1.5, 3.5, 300);
		p.setShiftKeyDown(true);
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_1, true); // Shift+R
		p.setShiftKeyDown(false);
		helper.assertTrue(front.getHealth() < 300f && back.getHealth() < 300f, "both sides hit (an area, not a cone)");
		double f = Math.min(1.0, Math.sqrt(front.distanceToSqr(p.position().add(0, 1.0, 0))) / SentryConfig.FLARE_RADIUS);
		float expected = (float) (SentryConfig.FLARE_DAMAGE + (SentryConfig.FLARE_EDGE_DAMAGE - SentryConfig.FLARE_DAMAGE) * f) * SentryConfig.FLARE_UNDEAD;
		helper.assertTrue(near(300f - front.getHealth(), expected), "a zombie (undead) takes x1.5: ~" + expected + ", got " + (300f - front.getHealth()));
		helper.assertTrue(front.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS), "dazzled");
		helper.assertTrue(Math.abs(Sentry.state(p).sun - (SentryConfig.SUN_MAX - SentryConfig.FLARE_COST)) < 1.0e-3f, "18 Sun");
		helper.assertTrue(Sentry.cooldownRemaining(p, SentryAbilityManager.SOLAR_FLARE) == SentryConfig.FLARE_COOLDOWN, "its cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void goldenSenseRevealsCreaturesAndSeesInTheDark(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		Zombie z = zombie(helper, 6, 6, 50);
		p.setShiftKeyDown(true);
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_5, true); // Shift+V
		p.setShiftKeyDown(false);
		helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING), "the zombie glows (seen through walls)");
		helper.assertTrue(z.getHealth() == 50f, "a utility: no damage");
		helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION), "Night Vision");
		helper.assertTrue(Sentry.state(p).senseUntil > p.level().getGameTime(), "the HUD sees it");
		helper.assertTrue(Sentry.cooldownRemaining(p, SentryAbilityManager.GOLDEN_SENSE) == SentryConfig.SENSE_COOLDOWN, "its cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void xWhileFlyingBoostsFlightFiftyPercentForThirtySeconds(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		SentryFlight.toggle(p);
		helper.assertTrue(Sentry.selfFlying(p), "flying");
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		long now = p.level().getGameTime();
		helper.assertTrue(SentryFlight.boosting(p), "X in the air: the Flight Boost");
		helper.assertFalse(SentryFlight.dashing(p), "not the dash");
		helper.assertTrue(Sentry.state(p).boostUntil - now == SentryConfig.BOOST_TICKS && SentryConfig.BOOST_TICKS == 600, "for 600 ticks (30 s)");
		helper.assertTrue(SentryConfig.BOOST_SPEED == 1.5, "+50% flight speed");
		helper.assertTrue(Sentry.cooldownRemaining(p, SentryAbilityManager.FLIGHT_BOOST) == SentryConfig.BOOST_COOLDOWN, "its own cooldown");
		helper.assertTrue(Math.abs(Sentry.state(p).sun - (SentryConfig.SUN_MAX - SentryConfig.BOOST_COST)) < 1.0e-3f, "10 Sun");
		helper.assertTrue(SentryFlight.boostLeft(p) == SentryConfig.BOOST_TICKS, "the HUD timer");
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(SentryFlight.boostLeft(p) == SentryConfig.BOOST_TICKS - 20, "counts down a tick at a time");
			SentryFlight.stop(p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void landingSlamHurtsAroundHim(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 3.5, 3.5);
		Zombie z = zombie(helper, 5.0, 3.5, 300);
		int n = SentryMoves.landing(p, 1f, false);
		helper.assertTrue(n >= 1, "the impact hit it");
		helper.assertTrue(near(300f - z.getHealth(), SentryConfig.LANDING_DAMAGE + SentryConfig.LANDING_DAMAGE_PER_STRENGTH), "~22 at full strength, got " + (300f - z.getHealth()));
		helper.assertTrue(com.projecthero.mod.flight.landing.HeroLandingStyle.byId(com.projecthero.mod.flight.landing.HeroLandingStyle.SENTRY.id)
				== com.projecthero.mod.flight.landing.HeroLandingStyle.SENTRY, "a registered landing style");
		edit(p, s -> s.flying = true);
		helper.assertTrue(com.projecthero.mod.flight.landing.HeroLanding.claim(p) == com.projecthero.mod.flight.landing.HeroLandingStyle.SENTRY, "his flight claims the golden landing");
		edit(p, s -> s.voidForm = true);
		helper.assertTrue(com.projecthero.mod.flight.landing.HeroLanding.claim(p) == com.projecthero.mod.flight.landing.HeroLandingStyle.SENTRY_VOID, "merged: the dark one");
		edit(p, s -> {
			s.flying = false;
			s.voidForm = false;
		});
		helper.succeed();
	}

	// ---------------------------------------------------------------- moveset 2: the Void

	@GameTest(template = EMPTY_STRUCTURE)
	public void tendrilLashWhipsThenImpalesWithMergedBuffs(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 1.5, 2);
		Zombie z = zombie(helper, 4.5, 2, 1000);
		edit(p, s -> {
			s.voidForm = true;
			s.voidLevel = 40f;
		});
		float[] got = new float[3];
		for (int i = 0; i < 3; i++) {
			edit(p, s -> s.abilityReadyAt.clear());
			look(p, z);
			float hp = z.getHealth();
			SentryAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
			got[i] = hp - z.getHealth();
		}
		helper.assertTrue(near(got[0], SentryConfig.LASH_DAMAGE * SentryConfig.MERGED_DAMAGE), "whip ~20 merged, got " + got[0]);
		helper.assertTrue(near(got[2], SentryConfig.IMPALE_DAMAGE * SentryConfig.MERGED_DAMAGE), "Impale ~37.5 merged, got " + got[2]);
		helper.assertTrue(Sentry.cooldownRemaining(p, SentryAbilityManager.TENDRIL_LASH) == Math.round(SentryConfig.LASH_COOLDOWN * SentryConfig.MERGED_COOLDOWN),
				"merged: cooldown x0.75");
		helper.assertTrue(Sentry.state(p).voidLevel > 40f, "it feeds the Void");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void controlledVoidDealsFiftyPercentMore(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 1.5, 2);
		Zombie a = zombie(helper, 3.5, 2, 300);
		helper.assertTrue(SentryConfig.MERGED_DAMAGE == 1.5f, "+50%");
		Sentry.hit(p, a, 10f, false);
		float plain = 300f - a.getHealth();
		Zombie b = zombie(helper, 3.5, 5, 300);
		edit(p, s -> s.voidForm = true);
		Sentry.hit(p, b, 10f, false);
		float merged = 300f - b.getHealth();
		helper.assertTrue(plain > 0f && Math.abs(merged / plain - 1.5f) < 0.05f, "merged hits x1.5 (ratio " + merged / plain + ")");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void umbralShroudCutsHitsAndWithersAttackers(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 3, 3);
		edit(p, s -> s.voidForm = true);
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_5, true); // merged V
		helper.assertTrue(VoidMoves.shrouded(p), "the shroud is up");
		float f = SentryDamage.factor(p, p.damageSources().generic());
		helper.assertTrue(Math.abs(f - (1f - SentryConfig.VOID_RESIST) * (1f - SentryConfig.SHROUD_RESIST)) < 1.0e-3f, "40% off on top: " + f);
		var arrow = new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, helper.getLevel());
		arrow.setPos(p.getX() + 1.5, p.getY() + 1.0, p.getZ());
		helper.getLevel().addFreshEntity(arrow);
		VoidMoves.tick(p);
		helper.assertTrue(arrow.isRemoved(), "the arrow is swallowed");
		Zombie z = zombie(helper, 4, 3, 200);
		SentryDamage.allowDamage(p, p.damageSources().mobAttack(z), 4f);
		helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.WITHER), "a melee attacker withers");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shadowMeldHidesHimUntilHeAttacks(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		edit(p, s -> s.voidForm = true);
		Zombie z = zombie(helper, 6, 6, 200);
		z.setTarget(p);
		p.setShiftKeyDown(true);
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_5, true); // merged Shift+V
		p.setShiftKeyDown(false);
		helper.assertTrue(VoidMoves.melded(p) && p.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY), "melded: invisible");
		helper.assertTrue(z.getTarget() == null, "the hunter lost him");
		look(p, z);
		edit(p, s -> s.abilityReadyAt.clear());
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_4, true); // Nightmare: an attack
		helper.assertFalse(VoidMoves.melded(p), "an attack brings him out");
		helper.assertFalse(p.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY), "visible again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void eraseSeizesThenWipesIntoAShadow(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 1, 2);
		Zombie z = zombie(helper, 4, 2, 20);
		look(p, z);
		BlockPos feet = z.blockPosition();
		edit(p, s -> s.voidForm = true);
		helper.assertTrue(VoidMoves.erase(p, z), "seized");
		helper.assertTrue(z.isAlive(), "not yet: it darkens first");
		helper.runAfterDelay(SentryConfig.ERASE_DELAY_TICKS + 4, () -> {
			helper.assertTrue(!z.isAlive(), "wiped out");
			boolean shadow = false;
			for (int dy = 0; dy >= -2; dy--) {
				shadow |= helper.getLevel().getBlockState(feet.offset(0, dy, 0)).is(SentryBlocks.BURNT_SHADOW);
			}
			helper.assertTrue(shadow, "a burnt shadow imprint where it stood");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shadowPoolSpreadsHoldsAndHurts(GameTestHelper helper) {
		helper.assertTrue(VoidMoves.poolRadius(0) == SentryConfig.POOL_START_RADIUS, "starts at 2");
		double step = VoidMoves.poolRadius(SentryConfig.POOL_GROW_TICKS / 2) - VoidMoves.poolRadius(0);
		helper.assertTrue(Math.abs(step - (SentryConfig.POOL_RADIUS - SentryConfig.POOL_START_RADIUS) / 2.0) < 1.0e-6, "spreads evenly: " + step);
		helper.assertTrue(VoidMoves.poolRadius(SentryConfig.POOL_TICKS) == SentryConfig.POOL_RADIUS, "stops at 6");
		ServerPlayer p = sentry(helper, 1, 1);
		Zombie z = zombie(helper, 4, 4, 200);
		edit(p, s -> s.voidForm = true);
		helper.assertTrue(VoidMoves.pool(p, z.position()), "a pool under it");
		helper.assertTrue(VoidMoves.zones(p, 0) == 1, "one pool running");
		helper.runAfterDelay(SentryConfig.POOL_DAMAGE_INTERVAL * 2 + 2, () -> {
			helper.assertTrue(z.getHealth() < 200f, "it bites (" + z.getHealth() + ")");
			helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "held down");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void swallowHoldsBlindsAndSpitsOut(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 1, 2);
		Zombie z = zombie(helper, 5, 2, 300);
		edit(p, s -> s.voidForm = true);
		helper.assertTrue(VoidMoves.swallow(p, z), "swallowed");
		helper.runAfterDelay(SentryConfig.SPHERE_TICKS / 2, () -> {
			helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS), "blind inside");
			helper.assertTrue(z.getHealth() < 300f, "hurt inside");
		});
		helper.runAfterDelay(SentryConfig.SPHERE_TICKS + 4, () -> {
			helper.assertTrue(VoidMoves.zones(p, 1) == 0, "spat out");
			float lost = 300f - z.getHealth();
			helper.assertTrue(lost >= (SentryConfig.SPHERE_SPIT_DAMAGE + SentryConfig.SPHERE_DAMAGE * 4) * SentryConfig.MERGED_DAMAGE * 0.8f,
					"the sphere and the spit: lost " + lost);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void nightmareHurtsWeakensAndScaresMobs(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		Zombie z = zombie(helper, 5, 5, 200);
		edit(p, s -> s.voidForm = true);
		helper.assertTrue(VoidMoves.nightmare(p) >= 1, "caught");
		helper.assertTrue(near(200f - z.getHealth(), SentryConfig.NIGHTMARE_DAMAGE * SentryConfig.MERGED_DAMAGE), "~10 merged, got " + (200f - z.getHealth()));
		helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS), "weakened");
		helper.assertTrue(VoidMoves.zones(p, 3) >= 1, "the mob is fleeing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void shadowEclipseDrainsAndHeals(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		Zombie z = zombie(helper, 5, 5, 500);
		edit(p, s -> s.voidForm = true);
		helper.assertTrue(VoidMoves.eclipse(p), "the eclipse");
		helper.assertTrue(Sentry.state(p).nightfallUntil > p.level().getGameTime(), "the HUD sees it");
		helper.runAfterDelay(SentryConfig.ECLIPSE_WINDUP_TICKS - 5, () -> {
			helper.assertFalse(VoidMoves.eclipseOpen(p), "still winding up");
			p.setHealth(20f);
		});
		helper.runAfterDelay(SentryConfig.ECLIPSE_WINDUP_TICKS + 45, () -> {
			helper.assertTrue(VoidMoves.eclipseOpen(p), "open");
			helper.assertTrue(500f - z.getHealth() >= SentryConfig.ECLIPSE_DAMAGE * 0.8f, "drained (" + z.getHealth() + ")");
			helper.assertTrue(p.getHealth() >= 20f + SentryConfig.ECLIPSE_HEAL, "he regenerates (" + p.getHealth() + ")");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void blotOutDrainsLifeAndSunToHeal(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		Zombie a = zombie(helper, 4, 2, 200);
		Zombie b = zombie(helper, 2, 5, 200);
		edit(p, s -> s.voidForm = true);
		Sentry.setSun(p, 50f);
		p.setHealth(10f);
		int n = VoidMoves.blotOut(p);
		helper.assertTrue(n >= 2, "drained both");
		helper.assertTrue(near(200f - a.getHealth(), SentryConfig.BLOT_DAMAGE * SentryConfig.MERGED_DAMAGE), "~12.5 merged, got " + (200f - a.getHealth()));
		float expected = 10f + Math.min(SentryConfig.BLOT_HEAL_MAX, n * SentryConfig.BLOT_HEAL_PER_VICTIM) + 50f / SentryConfig.BLOT_SUN_PER_HEALTH;
		helper.assertTrue(Math.abs(p.getHealth() - Math.min(p.getMaxHealth(), expected)) < 0.01f, "healed from them and the Sun: " + p.getHealth());
		helper.assertTrue(Sentry.state(p).sun == 0f, "the Sun burned away");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void shadowClonesRiseAndHunt(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		Zombie z = zombie(helper, 6, 6, 500);
		edit(p, s -> s.voidForm = true);
		helper.assertTrue(VoidMoves.clones(p), "the clones");
		helper.assertTrue(VoidMoves.clonePositions(p).size() == SentryConfig.CLONE_COUNT, "three of them");
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(z.getHealth() < 500f, "they hunted it down (" + z.getHealth() + ")");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shadowStepBlinksThroughWhatIsInTheWay(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 1.5, 1.5);
		Zombie z = zombie(helper, 1.5, 4.0, 200);
		edit(p, s -> s.voidForm = true);
		p.setYRot(0f); // looking +z
		p.setXRot(0f);
		Vec3 from = p.position();
		helper.assertTrue(VoidMoves.shadowStep(p), "stepped");
		helper.assertTrue(p.position().distanceTo(from) >= 3.0, "it went a fair way: " + p.position().distanceTo(from));
		helper.assertTrue(z.getHealth() < 200f, "the zombie in the way was hit");
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.17.5 rework: the trial, the toggle, flight and the dash

	@GameTest(template = EMPTY_STRUCTURE)
	public void letItOutTrialLostRaisesTheVoid(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		edit(p, s -> s.voidLevel = 10f); // no Void gate any more: the trial replaces it
		SentryAbilityManager.pressH(p); // round 3: H
		helper.assertTrue(LetItOut.running(Sentry.state(p)), "H at 10 Void starts the trial");
		helper.assertFalse(Sentry.voidForm(p), "not merged yet");
		float before = Sentry.state(p).voidLevel;
		helper.assertTrue(LetItOut.report(p, false) == LetItOut.Result.LOST, "a lost trial");
		helper.assertTrue(Math.abs(Sentry.state(p).voidLevel - before - SentryConfig.TRIAL_LOSE_VOID) < 1.0e-3f, "lost: +15 Void (step)");
		helper.assertFalse(Sentry.voidForm(p) || LetItOut.running(Sentry.state(p)), "lost: nothing else, the trial is over");
		helper.assertTrue(LetItOut.report(p, true) == LetItOut.Result.IGNORED, "no trial running: ignored");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void letItOutTrialWonMergesAndHTogglesBack(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		edit(p, s -> s.voidLevel = 20f);
		helper.assertTrue(LetItOut.begin(p), "the trial starts");
		// a "win" quicker than an honest one counts as a loss
		helper.assertTrue(LetItOut.report(p, true) == LetItOut.Result.LOST, "a win at once is too quick: lost");
		helper.assertTrue(LetItOut.begin(p), "the trial starts again");
		long now = p.level().getGameTime();
		edit(p, s -> s.letOutTrialStart = now - SentryConfig.TRIAL_MIN_WIN_TICKS - 5);
		helper.assertTrue(LetItOut.report(p, true) == LetItOut.Result.WON, "won");
		helper.assertTrue(Sentry.voidForm(p) && Sentry.merged(p), "merged with the Void");
		helper.assertTrue(p.getAttribute(Attributes.MAX_HEALTH).getValue() == 40.0, "still 40 max health merged");
		// merged: the Void rises faster (x1.5 every gain)
		float before = Sentry.state(p).voidLevel;
		Sentry.addVoid(p, 2f);
		helper.assertTrue(Math.abs(Sentry.state(p).voidLevel - before - 2f * SentryConfig.MERGED_VOID_GAIN) < 1.0e-3f, "merged: gains x1.5");
		// H back (round 3), only below 90
		edit(p, s -> s.voidLevel = 95f);
		SentryAbilityManager.pressH(p);
		helper.assertTrue(Sentry.voidForm(p), "no way back at 95");
		edit(p, s -> s.voidLevel = 60f);
		SentryAbilityManager.pressH(p);
		helper.assertFalse(Sentry.voidForm(p), "H again at 60: the Sentry");
		helper.assertFalse(LetItOut.running(Sentry.state(p)), "and no trial started by it");
		// the lock after a takeover still holds
		edit(p, s -> s.letOutLockedUntil = p.level().getGameTime() + 1000);
		SentryAbilityManager.pressH(p);
		helper.assertFalse(LetItOut.running(Sentry.state(p)), "H locked");
		helper.assertFalse(LetItOut.begin(p), "locked");
		// and the ability keys no longer do it: Shift+V is Golden Sense now
		edit(p, s -> s.letOutLockedUntil = 0L);
		p.setShiftKeyDown(true);
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		p.setShiftKeyDown(false);
		helper.assertFalse(LetItOut.running(Sentry.state(p)), "Shift+V doesn't start the trial any more");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void controlledVoidSuitLookWithAndWithoutTheSuit(GameTestHelper helper) {
		// without the suit: the Void suit appears over his skin when he merges, and goes when he is back
		ServerPlayer p = sentry(helper, 2, 2);
		helper.assertTrue(Sentry.look(p) == Sentry.Look.NONE, "no suit, not merged: his own skin");
		helper.assertTrue(LetItOut.begin(p), "the trial");
		long now = p.level().getGameTime();
		edit(p, s -> s.letOutTrialStart = now - SentryConfig.TRIAL_MIN_WIN_TICKS - 5);
		helper.assertTrue(LetItOut.report(p, true) == LetItOut.Result.WON, "won");
		helper.assertTrue(Sentry.look(p) == Sentry.Look.VOID_OVER_SKIN, "merged without the suit: the Void suit over his skin");
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && p.getItemBySlot(EquipmentSlot.LEGS).isEmpty(), "a visual: no item given");
		SentryAbilityManager.pressH(p);
		helper.assertFalse(Sentry.voidForm(p), "back");
		helper.assertTrue(Sentry.look(p) == Sentry.Look.VOID_OVER_SKIN, "fading out right after");
		edit(p, s -> s.formChangedAt = p.level().getGameTime() - SentryConfig.FORM_FADE_TICKS - 1);
		helper.assertTrue(Sentry.look(p) == Sentry.Look.NONE, "faded away: his own skin again");
		// with the suit: the comics suit fades into the Void suit and back to the comics suit
		ServerPlayer q = sentry(helper, 5, 5);
		q.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SentrySuitItem.TOP));
		q.setItemSlot(EquipmentSlot.LEGS, new ItemStack(SentrySuitItem.TROUSERS));
		helper.assertTrue(Sentry.look(q) == Sentry.Look.SUIT, "the comics suit");
		edit(q, s -> {
			s.voidForm = true;
			s.formChangedAt = q.level().getGameTime() - SentryConfig.FORM_FADE_TICKS - 1;
		});
		helper.assertTrue(Sentry.look(q) == Sentry.Look.VOID_OVER_SUIT, "merged in the suit: the Void suit over it");
		edit(q, s -> {
			s.voidForm = false;
			s.formChangedAt = q.level().getGameTime() - SentryConfig.FORM_FADE_TICKS - 1;
		});
		helper.assertTrue(Sentry.look(q) == Sentry.Look.SUIT, "back: the comics suit stays");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void letItOutTrialTimesOut(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		helper.assertTrue(LetItOut.begin(p), "the trial starts");
		long now = p.level().getGameTime();
		edit(p, s -> s.letOutTrialStart = now - SentryConfig.TRIAL_TICKS - SentryConfig.TRIAL_GRACE_TICKS - 2);
		float before = Sentry.state(p).voidLevel;
		Sentry.tick(p);
		helper.assertFalse(LetItOut.running(Sentry.state(p)), "no word from the client: over");
		helper.assertTrue(Sentry.state(p).voidLevel - before >= SentryConfig.TRIAL_LOSE_VOID - 1.0e-3f, "and lost");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void doubleTapSpaceTogglesFlightInBothForms(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		SentryFlight.toggle(p);
		helper.assertTrue(Sentry.isFlying(p) && p.getAbilities().mayfly, "double-tap Space: flying");
		SentryFlight.toggle(p);
		helper.assertFalse(Sentry.isFlying(p), "again: landed");
		SentryFlight.toggle(p);
		helper.assertFalse(Sentry.isFlying(p), "a take-off the same tick as a landing is ignored (vanilla double-tap)");
		edit(p, s -> s.voidForm = true);
		helper.runAfterDelay(SentryConfig.FLIGHT_TOGGLE_DEBOUNCE + 2, () -> {
			SentryFlight.toggle(p);
			helper.assertTrue(Sentry.isFlying(p), "merged with the Void: flies the same way");
			Sentry.tick(p); // the flight tick no longer drops it in Void Form
			helper.assertTrue(Sentry.isFlying(p), "stays flying merged");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void xOnTheGroundIsTheDash(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		helper.assertTrue(SentryAbilityManager.DASH.equals(SentryAbilityManager.sentryMoveFor(AbilitySlot.SLOT_3, false)), "X = the dash");
		SentryAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		helper.assertFalse(Sentry.isFlying(p), "X never takes off");
		helper.assertTrue(SentryFlight.dashing(p), "X dashes");
		helper.assertTrue(Sentry.cooldownRemaining(p, SentryAbilityManager.DASH) == SentryConfig.DASH_COOLDOWN, "the dash's cooldown starts");
		helper.assertTrue(Math.abs(Sentry.state(p).sun - (SentryConfig.SUN_MAX - SentryConfig.DASH_COST)) < 1.0e-3f, "5 Sun");
		helper.assertTrue(SentryAbilityManager.SHADOW_STEP.equals(SentryAbilityManager.voidMoveFor(AbilitySlot.SLOT_3, false)),
				"merged X stays Shadow Step");
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.17.5 rework: the suit

	@GameTest(template = EMPTY_STRUCTURE)
	public void grantDoesNotEquipTheSuit(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			helper.assertTrue(p.getItemBySlot(slot).isEmpty(), "the power puts nothing in " + slot);
		}
		helper.assertFalse(Sentry.suitWorn(p), "no suit");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitIsCraftable(GameTestHelper helper) {
		boolean top = false, trousers = false;
		for (RecipeHolder<?> r : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
			ItemStack out = r.value().getResultItem(helper.getLevel().registryAccess());
			top |= out.is(SentrySuitItem.TOP);
			trousers |= out.is(SentrySuitItem.TROUSERS);
		}
		helper.assertTrue(top && trousers, "both suit pieces have a recipe");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitMendsItself(GameTestHelper helper) {
		ItemStack stack = new ItemStack(SentrySuitItem.TOP);
		stack.setDamageValue(10);
		long t = SentryConfig.SUIT_REPAIR_INTERVAL * 100L;
		helper.assertTrue(SentrySuitItem.selfRepair(stack, t), "mends on the interval");
		helper.assertTrue(stack.getDamageValue() == 9, "1 point a step");
		helper.assertFalse(SentrySuitItem.selfRepair(stack, t + 1), "not between steps");
		helper.assertTrue(stack.getDamageValue() == 9, "unchanged between steps");
		SentrySuitItem.selfRepair(stack, t + SentryConfig.SUIT_REPAIR_INTERVAL);
		helper.assertTrue(stack.getDamageValue() == 8, "the next step mends 1 more");
		// through the real inventory tick, in an armour slot
		ServerPlayer p = mock(helper, 2, 2);
		ItemStack worn = new ItemStack(SentrySuitItem.TROUSERS);
		worn.setDamageValue(5);
		p.setItemSlot(EquipmentSlot.LEGS, worn);
		helper.onEachTick(() -> p.getInventory().tick()); // (mock players aren't ticked: the inventory tick by hand)
		helper.runAfterDelay(SentryConfig.SUIT_REPAIR_INTERVAL + 2, () -> {
			int got = p.getItemBySlot(EquipmentSlot.LEGS).getDamageValue();
			// 62 ticks cross one or two repair boundaries: 1 point each, never more
			helper.assertTrue(got == 4 || got == 3, "worn: 1 point per interval (5 -> " + got + ")");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitSlowsTheVoidAndSpeedsTheSun(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(SentrySuitItem.TOP));
		helper.assertFalse(Sentry.suitWorn(p), "the top alone isn't the suit");
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(SentrySuitItem.TROUSERS));
		helper.assertTrue(Sentry.suitWorn(p), "top + trousers: the suit");
		edit(p, s -> {
			s.sun = 10f;
			s.voidLevel = 20f;
		});
		float before = Sentry.state(p).voidLevel;
		Sentry.addVoid(p, 5f);
		helper.assertTrue(Math.abs(Sentry.state(p).voidLevel - before - 5f * SentryConfig.SUIT_VOID_GAIN) < 1.0e-3f, "a Void gain x0.6");
		Sentry.pinSun(p, Sentry.Sun.DIRECT);
		float sunBefore = Sentry.state(p).sun;
		Sentry.tickMeters(p);
		float dSun = Sentry.state(p).sun - sunBefore;
		helper.assertTrue(Math.abs(dSun - SentryConfig.SUN_DIRECT_PER_SECOND * SentryConfig.SUIT_SUN_GAIN) < 1.0e-3f, "sunlight x1.5: " + dSun);
		Sentry.pinSun(p, Sentry.Sun.DARK);
		before = Sentry.state(p).voidLevel;
		Sentry.tickMeters(p);
		float dVoid = Sentry.state(p).voidLevel - before;
		helper.assertTrue(Math.abs(dVoid - SentryConfig.VOID_DRAIN_PER_SECOND) < 1.0e-4f, "round 3: the dark only drains (unscaled): " + dVoid);
		Sentry.pinSun(p, null);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void voidFillsAThirdAsFast(GameTestHelper helper) {
		helper.assertTrue(Sentry.voidPerSecond(Sentry.Sun.NIGHT) < 0f && Sentry.voidPerSecond(Sentry.Sun.DARK) < 0f, "round 3: no passive gains at all");
		helper.assertTrue(SentryConfig.SUNS_VOID <= 25f / 2.5f && SentryConfig.ERASE_VOID <= 6f / 2.5f, "event gains cut");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void maxHealthIsFortyInBothForms(GameTestHelper helper) {
		ServerPlayer p = sentry(helper, 2, 2);
		helper.assertTrue(p.getAttribute(Attributes.MAX_HEALTH).getValue() == 40.0, "Sentry: 40");
		edit(p, s -> s.voidForm = true);
		Sentry.reconcile(p);
		helper.assertTrue(p.getAttribute(Attributes.MAX_HEALTH).getValue() == 40.0, "merged with the Void: 40");
		helper.succeed();
	}


	// ---------------------------------------------------------------- where it comes from

	@GameTest(template = EMPTY_STRUCTURE)
	public void serumOnlyFromTheVault(GameTestHelper helper) {
		helper.assertTrue(PowerGrants.NOT_FROM_SERUMS.contains(Sentry.KEY), "no random serum rolls the Sentry");
		helper.assertTrue(PowerGrants.HERO_TIER_KEYS.contains(Sentry.KEY), "an operator can still grant it");
		for (RecipeHolder<?> r : helper.getLevel().getServer().getRecipeManager().getRecipes()) {
			ItemStack out = r.value().getResultItem(helper.getLevel().registryAccess());
			helper.assertFalse(out.is(SentryItems.SENTRY_SERUM), "no recipe makes the Sentry Serum: " + r.id());
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sealedVatOpensWithoutTheWardenAndGivesOneEach(GameTestHelper helper) {
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.setBlock(rel, SentryBlocks.SERUM_VAT.defaultBlockState());
		BlockPos at = helper.absolutePos(rel);
		ServerPlayer a = mock(helper, 2, 2);
		ServerPlayer b = mock(helper, 4, 4);
		helper.assertTrue(SerumVatBlock.use(helper.getLevel(), at, helper.getLevel().getBlockState(at), a), "no Warden near: it opens and gives a serum");
		helper.assertTrue(helper.getLevel().getBlockState(at).getValue(SerumVatBlock.OPEN), "open now");
		helper.assertTrue(a.getInventory().countItem(SentryItems.SENTRY_SERUM) == 1, "one serum");
		helper.assertFalse(SerumVatBlock.use(helper.getLevel(), at, helper.getLevel().getBlockState(at), a), "not a second one");
		helper.assertTrue(SerumVatBlock.use(helper.getLevel(), at, helper.getLevel().getBlockState(at), b), "the next player gets theirs");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vatStaysSealedWhileKrossLivesAndOpensWhenTheVaultClears(GameTestHelper helper) {
		BlockPos rel = new BlockPos(3, 2, 3);
		helper.setBlock(rel, SentryBlocks.SERUM_VAT.defaultBlockState());
		BlockPos at = helper.absolutePos(rel);
		var kross = helper.spawnWithNoFreeWill(com.projecthero.mod.vaultdungeon.VaultEntityTypes.WARDEN_KROSS, new Vec3(5, 2, 5));
		ServerPlayer a = mock(helper, 2, 2);
		helper.assertFalse(SerumVatBlock.use(helper.getLevel(), at, helper.getLevel().getBlockState(at), a), "sealed while Kross lives");
		helper.assertFalse(helper.getLevel().getBlockState(at).getValue(SerumVatBlock.OPEN), "still sealed");
		kross.discard();
		com.projecthero.mod.vaultdungeon.VaultClearing.unsealVaults(helper.getLevel(), at.offset(2, 0, 2));
		helper.assertTrue(helper.getLevel().getBlockState(at).getValue(SerumVatBlock.OPEN), "Kross down: the vault room opens the vat too");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void drinkingTheSerumMakesTheSentry(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2, 2);
		ItemStack serum = new ItemStack(SentryItems.SENTRY_SERUM);
		ItemStack left = serum.finishUsingItem(helper.getLevel(), p);
		helper.assertTrue(Sentry.hasPower(p), "drunk: the Sentry");
		helper.assertTrue(left.is(net.minecraft.world.item.Items.GLASS_BOTTLE) || left.isEmpty(), "a bottle left");
		helper.succeed();
	}
}
