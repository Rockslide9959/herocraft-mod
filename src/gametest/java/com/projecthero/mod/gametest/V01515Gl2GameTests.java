package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternAbilityManager;
import com.projecthero.mod.greenlantern.GreenLanternBattery;
import com.projecthero.mod.greenlantern.GreenLanternCombat;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternConstructAttacks;
import com.projecthero.mod.greenlantern.block.GreenLanternBlocks;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity;
import com.projecthero.mod.hero.AbilitySlot;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15 Green Lantern batch 2: the off-hand Power Battery charge (Sneak + use, damage cancels it, a full ring at the
 * end), the Shift+R Blast Wave, tap-vs-hold R, and constructs without time limits or cooldowns.
 */
public class V01515Gl2GameTests implements FabricGameTest {
	private static ServerPlayer lantern(GameTestHelper helper, float charge) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		// mock players are placed at world spawn -- bring them into the test's own (ticking) area, facing +Z
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 1.5));
		player.moveTo(at.x, at.y, at.z, 0f, 0f);
		player.setYHeadRot(0f);
		player.yBodyRot = 0f;
		GreenLantern.bond(player);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = charge;
		GreenLantern.save(player, s);
		return player;
	}

	private static void holdBattery(ServerPlayer player) {
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(GreenLanternBlocks.POWER_BATTERY_ITEM));
	}

	private static float charge(ServerPlayer player) {
		return GreenLantern.state(player).ringCharge;
	}

	private static Husk husk(GameTestHelper helper, ServerPlayer player, double dx, double dz) {
		Husk h = EntityType.HUSK.create(helper.getLevel());
		h.moveTo(player.getX() + dx, player.getY(), player.getZ() + dz, 180f, 0f);
		h.setNoAi(true);
		helper.getLevel().addFreshEntity(h);
		return h;
	}

	// ---------------------------------------------------------------- charging at the battery

	@GameTest(template = EMPTY_STRUCTURE)
	public void sneakUseWithTheBatteryInTheOffHandStartsCharging(GameTestHelper helper) {
		ServerPlayer player = lantern(helper, 500f);
		// in the MAIN hand it is just a block item: no charge
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GreenLanternBlocks.POWER_BATTERY_ITEM));
		player.setShiftKeyDown(true);
		player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
		helper.assertFalse(GreenLanternBattery.isRecitingOath(player), "the battery in the main hand must not charge the ring");
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		// off hand, not sneaking: no charge either
		holdBattery(player);
		player.setShiftKeyDown(false);
		player.getOffhandItem().use(helper.getLevel(), player, InteractionHand.OFF_HAND);
		helper.assertFalse(GreenLanternBattery.isRecitingOath(player), "a plain right-click must not start the charge");
		// off hand + Sneak: the ritual starts, rooted, visible to everyone
		player.setShiftKeyDown(true);
		player.getOffhandItem().use(helper.getLevel(), player, InteractionHand.OFF_HAND);
		helper.assertTrue(GreenLanternBattery.isRecitingOath(player), "Sneak + use with the battery in the off hand charges the ring");
		GreenLanternFx fx = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX, GreenLanternFx.EMPTY);
		helper.assertTrue(fx.has(GreenLanternFx.CH_CHARGE) && fx.chargeStart() > 0L, "the charge is synced for the pose / glow");
		helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < 1.0e-4, "the Lantern is rooted while charging");
		helper.assertTrue(player.getOffhandItem().getCount() == 1, "the battery is not used up");
		// letting go of the battery ends it
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		GreenLanternBattery.tick(player);
		helper.assertFalse(GreenLanternBattery.isRecitingOath(player), "putting the battery away cancels the charge");
		helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.01, "the root is lifted");
		helper.assertTrue(charge(player) == 500f, "a cancelled charge gives nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theFullOathFillsTheRing(GameTestHelper helper) {
		ServerPlayer player = lantern(helper, 500f);
		holdBattery(player);
		helper.assertTrue(GreenLanternBattery.beginCharge(player), "the charge should start");
		helper.runAfterDelay(GreenLanternBattery.CHARGE_TICKS + 1, () -> {
			GreenLanternBattery.tick(player);
			helper.assertFalse(GreenLanternBattery.isRecitingOath(player), "the Oath should be over");
			helper.assertTrue(charge(player) == GreenLanternConfig.MAX_RING_CHARGE,
					"a completed charge fills the ring to exactly max, was " + charge(player));
			GreenLanternFx fx = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX, GreenLanternFx.EMPTY);
			helper.assertFalse(fx.has(GreenLanternFx.CH_CHARGE), "the charge pose ends");
			helper.assertTrue(fx.anim() == GreenLanternFx.ANIM_CHARGED, "the flash / ring-up animation plays");
			helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.01, "the root is lifted");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void takingDamageCancelsTheCharge(GameTestHelper helper) {
		ServerPlayer player = lantern(helper, 500f);
		holdBattery(player);
		GreenLanternBattery.beginCharge(player);
		helper.assertTrue(GreenLanternBattery.isRecitingOath(player), "the charge should start");
		// a fresh mock player still has its spawn invulnerability -- clear it so the hit really lands
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(player, 0);
		} catch (ReflectiveOperationException ignored) {
		}
		player.invulnerableTime = 0;
		player.hurt(helper.getLevel().damageSources().magic(), 2f);
		helper.assertFalse(GreenLanternBattery.isRecitingOath(player), "taking damage must cancel the charge");
		helper.assertTrue(charge(player) == 500f, "no charge gained");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R: tap / hold / Shift

	@GameTest(template = EMPTY_STRUCTURE)
	public void tapRFiresTheRingBolt(GameTestHelper helper) {
		ServerPlayer player = lantern(helper, 5000f);
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_1, true);
		helper.assertTrue(charge(player) == 5000f, "nothing fires on the press itself");
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_1, false);
		helper.assertTrue(charge(player) == 5000f - GreenLanternConfig.BOLT_COST, "a quick tap fires the Ring Bolt");
		helper.assertFalse(GreenLanternCombat.isChannellingBeam(player), "a tap is not the beam");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void holdRChannelsTheBeam(GameTestHelper helper) {
		ServerPlayer player = lantern(helper, 5000f);
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_1, true);
		helper.runAfterDelay(GreenLanternConfig.BEAM_HOLD_TICKS + 2, () -> {
			GreenLanternAbilityManager.serverTick(player);
			helper.assertTrue(GreenLanternCombat.isChannellingBeam(player), "holding R past the threshold starts the beam");
			helper.assertTrue(GreenLantern.abilityReady(player, "ring_bolt"), "no bolt is fired by a hold");
			GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_1, false);
			helper.assertFalse(GreenLanternCombat.isChannellingBeam(player), "letting go of R ends the beam");
			helper.assertTrue(GreenLantern.abilityReady(player, "ring_bolt"), "releasing a hold fires no bolt either");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void shiftRBlastWaveHitsTheConeSlowsAndCosts(GameTestHelper helper) {
		ServerPlayer player = lantern(helper, 5000f);
		Husk front = husk(helper, player, 0.0, 4.0);
		Husk side = husk(helper, player, 2.5, 3.0);
		Husk behind = husk(helper, player, 0.0, -4.0);
		Husk far = husk(helper, player, 0.0, 11.0);
		player.setShiftKeyDown(true);
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_1, true);
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_1, false);
		player.setShiftKeyDown(false);
		helper.assertTrue(charge(player) == 5000f - GreenLanternConfig.BLAST_WAVE_COST, "the wave costs 150 charge, was "
				+ (5000f - charge(player)));
		helper.assertTrue(GreenLantern.cooldownRemaining(player, GreenLanternCombat.BLAST_CD) >= GreenLanternConfig.BLAST_WAVE_COOLDOWN_TICKS - 1,
				"a 7 s cooldown");
		helper.assertFalse(GreenLantern.abilityReady(player, "ring_bolt"), "Shift+R is not the bolt");
		// a second press on cooldown does nothing
		player.setShiftKeyDown(true);
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_1, true);
		player.setShiftKeyDown(false);
		helper.assertTrue(charge(player) == 5000f - GreenLanternConfig.BLAST_WAVE_COST, "no second wave on cooldown");
		float max = front.getMaxHealth();
		helper.runAfterDelay(GreenLanternConfig.BLAST_WAVE_TRAVEL_TICKS + 4, () -> {
			helper.assertTrue(front.getHealth() < max - 10f || !front.isAlive(), "the wave hits what is in front, health "
					+ front.getHealth());
			helper.assertTrue(side.getHealth() < max - 10f || !side.isAlive(), "and what is across the cone");
			MobEffectInstance slow = front.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
			helper.assertTrue(!front.isAlive() || (slow != null && slow.getAmplifier() == GreenLanternConfig.BLAST_WAVE_SLOW_AMPLIFIER),
					"Slowness III on whatever it hits");
			helper.assertTrue(behind.getHealth() == max, "nothing behind the Lantern is hit");
			helper.assertTrue(far.getHealth() == max, "nothing beyond 8 blocks is hit");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- constructs: no time limits, no cooldowns

	@GameTest(template = EMPTY_STRUCTURE)
	public void constructsHaveNoTimeLimitsOrCooldownsButTheTurret(GameTestHelper helper) {
		for (ConstructType t : ConstructType.values()) {
			if (t == ConstructType.SENTRY_TURRET) {
				helper.assertTrue(t.maxDurationTicks() > 0, "the Sentry Turret keeps its time limit");
			} else {
				helper.assertTrue(t.maxDurationTicks() == 0, t + " has no time limit any more");
			}
		}
		ServerPlayer player = lantern(helper, 9000f);
		husk(helper, player, 0.0, 3.0);
		GreenLanternConstructs.deploy(player, ConstructType.EMERALD_WARRIOR);
		GreenLanternConstructs.deploy(player, ConstructType.CHAIN_SNARE);
		for (HardLightConstructEntity e : GreenLanternConstructAttacks.live(player.getUUID())) {
			helper.assertTrue(e.life() == 0, e.shape() + " lives until dismissed");
		}
		helper.assertTrue(GreenLanternConstructAttacks.liveCount(player.getUUID(), HardLightConstructEntity.Shape.WARRIOR) == 1,
				"the warrior is up");
		for (ConstructType t : ConstructType.values()) {
			if (t != ConstructType.SENTRY_TURRET) {
				helper.assertTrue(GreenLanternConstructs.cooldownRemainingFor(player, t) == 0, t + " has no cooldown");
			}
		}
		// summoning a second warrior right away works (it replaces the first) -- no cooldown in the way
		float before = charge(player);
		GreenLanternConstructs.deploy(player, ConstructType.EMERALD_WARRIOR);
		helper.assertTrue(charge(player) < before, "the second warrior is paid for (costs stay)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void theWarriorOutlastsItsOldThirtySecondTimer(GameTestHelper helper) {
		ServerPlayer player = lantern(helper, 9000f);
		GreenLanternConstructs.deploy(player, ConstructType.EMERALD_WARRIOR);
		HardLightConstructEntity warrior = GreenLanternConstructAttacks.live(player.getUUID()).get(0);
		// jump its clock past the old 30 s life: it must still be standing
		warrior.tickCount = GreenLanternConfig.WARRIOR_DURATION_TICKS + 20;
		helper.runAfterDelay(5, () -> {
			helper.assertFalse(warrior.isRemoved(), "the warrior no longer times out");
			GreenLanternAbilityManager.clearConstructs(player);
			helper.assertTrue(warrior.isRemoved(), "N still dismisses it");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Z: the hard-light bubble (ForceBubble)

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void zHoldsABubbleThatDrainsFortyASecondAndBlocksBlows(GameTestHelper helper) {
		ServerPlayer player = lantern(helper, 5000f);
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_4, true);
		helper.assertTrue(com.projecthero.mod.greenlantern.GreenLanternShield.isBubble(player), "holding Z raises the bubble");
		helper.assertTrue(charge(player) == 5000f, "no up-front cost");
		Husk attacker = husk(helper, player, 0.0, -1.5); // behind: the bubble covers every side
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(player, 0);
		} catch (ReflectiveOperationException ignored) {
		}
		float hp = player.getHealth();
		player.invulnerableTime = 0;
		player.hurt(helper.getLevel().damageSources().mobAttack(attacker), 6f);
		helper.assertTrue(player.getHealth() == hp, "a blow from behind is stopped by the bubble");
		for (int i = 0; i < 20; i++) {
			com.projecthero.mod.greenlantern.GreenLanternShield.tickShieldUpkeep(player);
		}
		float drained = 5000f - charge(player);
		helper.assertTrue(Math.abs(drained - GreenLanternConfig.BUBBLE_SHIELD_DRAIN_PER_SEC) < 0.5f,
				"a second of bubble costs 40 charge, was " + drained);
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_4, false);
		helper.assertFalse(com.projecthero.mod.greenlantern.GreenLanternShield.isActive(player), "letting go of Z drops it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBubbleDropsWhenTheRingRunsDry(GameTestHelper helper) {
		ServerPlayer player = lantern(helper, GreenLanternConfig.EMERGENCY_RESERVE + 5f);
		GreenLanternAbilityManager.handle(player, AbilitySlot.SLOT_4, true);
		helper.assertTrue(com.projecthero.mod.greenlantern.GreenLanternShield.isBubble(player), "the bubble goes up");
		for (int i = 0; i < 20; i++) {
			com.projecthero.mod.greenlantern.GreenLanternShield.tickShieldUpkeep(player);
		}
		helper.assertFalse(com.projecthero.mod.greenlantern.GreenLanternShield.isActive(player),
				"out of charge, the bubble drops even with Z still held");
		helper.succeed();
	}
}
