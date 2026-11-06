package com.projecthero.mod.gametest;

import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.jetray.JetrayKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Jetray revamp (docs/OMNITRIX_REVAMP_JETRAY.md): Neuroshock stacks (the third paralyses), Tail Blast's paralysis, the
 * Tail Grab carry and drop, strong winds / heavy hits knocking him out of the air (stall), Barrel Roll, Insulated, Wing Ram's speed
 * bonus, the Neuroshock Overload on Shift+Z, and that turning back leaves no state behind. Mock players are not ticked: tests over
 * time drive {@link Omnitrix#tick} themselves.
 */
public class OmnitrixJetrayGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z, float pitch) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, pitch);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** A recalibrated wearer, transformed into Jetray. */
	private static ServerPlayer jetray(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 3.5, 2, 1.5, 0f);
		helper.assertTrue(Omnitrix.grant(p, true), "the Recalibrated Omnitrix goes on");
		helper.assertTrue(Omnitrix.select(p, JetrayKit.ID), "selected Jetray");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Jetray");
		p.setOnGround(true);
		return p;
	}

	private static JetrayKit kit() {
		return (JetrayKit) Aliens.kit(JetrayKit.ID);
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 9; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static Villager prey(GameTestHelper helper, double x, double y, double z, float health) {
		Villager m = EntityType.VILLAGER.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth(health);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0);
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void near(GameTestHelper helper, float actual, float expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 0.05f, what + " (expected " + expected + ", got " + actual + ")");
	}

	private static void clearCooldown(ServerPlayer p, String move) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(JetrayKit.ID + "." + move);
		Omnitrix.save(p, n);
	}

	private static boolean stunned(LivingEntity e) {
		var s = e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		return s != null && s.getAmplifier() >= 9 && e.hasEffect(MobEffects.WEAKNESS);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void jetrayNeuroshockStacksParalyseOnTheThird(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = jetray(helper);
		JetrayKit k = kit();
		Villager v = prey(helper, 3.5, 2, 6.5, 100f);
		for (int i = 1; i <= 2; i++) {
			clearCooldown(p, JetrayKit.EYES);
			v.invulnerableTime = 0;
			helper.assertTrue(k.eyes(p), "eye beam " + i);
			helper.assertTrue(JetrayKit.stacks(p, v) == i, i + " stack(s) (got " + JetrayKit.stacks(p, v) + ")");
			helper.assertFalse(stunned(v), "not paralysed yet");
		}
		clearCooldown(p, JetrayKit.EYES);
		v.invulnerableTime = 0;
		k.eyes(p);
		helper.assertTrue(stunned(v), "the third paralyses");
		helper.assertTrue(JetrayKit.stacks(p, v) == 0, "and clears the stacks");
		near(helper, v.getHealth(), 100f - 3 * JetrayKit.EYES_DAMAGE, "three beams of 11");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void jetrayTailBlastParalysesAndTheOverloadIsOnShiftZ(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = jetray(helper);
		JetrayKit k = kit();
		Villager v = prey(helper, 3.5, 2, 6.5, 200f);
		helper.assertTrue(k.tailBlast(p), "the tail fires");
		near(helper, v.getHealth(), 200f - JetrayKit.TAIL_DAMAGE, "14");
		helper.assertTrue(stunned(v), "paralysed");
		helper.assertTrue(JetrayKit.OVERLOAD_MOVE.equals(k.moveOn(AbilitySlot.SLOT_4, true, false).id()), "the ultimate is on Shift+Z");
		v.invulnerableTime = 0;
		k.fireOverload(p, helper.getLevel());
		near(helper, v.getHealth(), 200f - JetrayKit.TAIL_DAMAGE - JetrayKit.OVERLOAD_DAMAGE, "the overload beam: 100");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void jetrayTailGrabCarriesAndDrops(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = jetray(helper);
		JetrayKit k = kit();
		Villager v = prey(helper, 3.5, 2, 4.5, 40f);
		helper.assertTrue(k.tailGrab(p), "the tail grabs it");
		helper.assertTrue(k.flag(p, JetrayKit.F_CARRY) && k.keyActive(p, AbilitySlot.SLOT_2), "carrying: the G box is lit");
		near(helper, v.getHealth(), 40f - JetrayKit.GRAB_DAMAGE, "9 on the grab");
		helper.assertTrue(k.cooldownLeft(p, JetrayKit.TAIL_GRAB) == 0, "the cooldown waits for the drop");
		// up he goes
		Vec3 up = helper.absoluteVec(new Vec3(3.5, 7, 1.5));
		p.teleportTo(up.x, up.y, up.z);
		k.wingBeat(p);
		pump(helper, p);
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(v.getY() < p.getY() && v.distanceTo(p) < 3.5, "carried under him (" + v.position() + " vs " + p.position() + ")");
			k.press(p, AbilitySlot.SLOT_2, true);
			helper.assertFalse(k.flag(p, JetrayKit.F_CARRY), "Shift+G again lets go");
			helper.assertTrue(k.cooldownLeft(p, JetrayKit.TAIL_GRAB) > 0, "and the cooldown starts");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void jetrayWindAndHeavyHitsKnockHimOutOfTheAir(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = jetray(helper);
		JetrayKit k = kit();
		if (!p.getAbilities().flying) {
			k.wingStroke(p);
		}
		helper.assertTrue(p.getAbilities().flying, "flying");
		near(helper, k.incomingDamage(p, helper.getLevel().damageSources().explosion(null, null), 4f), 6f, "a blast does 50% more");
		helper.assertTrue(k.flag(p, JetrayKit.F_STALL) && !p.getAbilities().flying && !p.getAbilities().mayfly, "and knocks him out of the air");
		clearCooldown(p, JetrayKit.WING_BEAT);
		helper.assertFalse(k.wingBeat(p), "no Wing Beat while stalled");
		near(helper, k.incomingDamage(p, helper.getLevel().damageSources().lightningBolt(), 10f), 5f, "Insulated: lightning halved");
		pump(helper, p);
		helper.runAfterDelay(JetrayKit.STALL_TICKS + 2, () -> {
			helper.assertFalse(k.flag(p, JetrayKit.F_STALL), "the stall wears off");
			helper.assertTrue(p.getAbilities().mayfly, "the wings are back");
			clearCooldown(p, JetrayKit.WING_BEAT);
			if (!p.getAbilities().flying) {
				k.wingStroke(p);
			}
			helper.assertTrue(p.getAbilities().flying, "flying again");
			k.incomingDamage(p, helper.getLevel().damageSources().generic(), 10f);
			helper.assertTrue(k.flag(p, JetrayKit.F_STALL), "a heavy hit on the wing stalls him too");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void jetrayBarrelRollDodgesShotsAndWingRamScalesWithSpeed(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = jetray(helper);
		JetrayKit k = kit();
		var shot = helper.getLevel().damageSources().source(DamageTypes.ARROW);
		helper.assertTrue(k.allowDamage(p, shot, 5f), "an arrow hits him normally");
		helper.assertTrue(k.roll(p), "the roll");
		helper.assertFalse(k.allowDamage(p, shot, 5f), "rolling: shots slip past");
		near(helper, k.incomingDamage(p, helper.getLevel().damageSources().generic(), 6f), 3f, "other hits do half");
		helper.assertFalse(k.roll(p), "the roll cools down");
		near(helper, JetrayKit.ramBonus(0.1), 0f, "no bonus standing still");
		near(helper, JetrayKit.ramBonus(0.85), 2 * JetrayKit.RAM_PER, "+1.6 for every 0.4 blocks a tick");
		near(helper, JetrayKit.ramBonus(5.0), JetrayKit.RAM_MAX, "capped at +8");
		helper.succeed();
	}

	/** Level flight along the look for {@code ticks} ticks of the shared directional model: the speed it settles at (blocks/tick). */
	private static double settled(com.projecthero.mod.flight.DirectionalFlightModel.Tune tune) {
		Vec3 look = new Vec3(0, 0, 1);
		Vec3 v = Vec3.ZERO;
		for (int i = 0; i < 200; i++) {
			Vec3 wanted = com.projecthero.mod.flight.DirectionalFlightModel.wantedVelocity(look, 0f, 1f, 0f, 0, tune);
			v = com.projecthero.mod.flight.DirectionalFlightModel.step(v, wanted, true, tune, false);
		}
		return v.length();
	}

	/**
	 * v0.17.2 (user: "give jetray the directional flight and edit his flight speed, make it 25 blocks per second cruise and 45 blocks
	 * per second sprinting"): the client's directional flight settles at 1.25 blocks a tick cruising and 2.25 sprinting.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void jetrayDirectionalFlightCruises25AndSprints45(GameTestHelper helper) {
		double cruise = settled(com.projecthero.mod.flight.DirectionalFlightModel.jetray(JetrayKit.FLY_SPEED, false, false));
		double sprint = settled(com.projecthero.mod.flight.DirectionalFlightModel.jetray(JetrayKit.FLY_SPEED, true, false));
		double stream = settled(com.projecthero.mod.flight.DirectionalFlightModel.jetray(JetrayKit.STREAM_FLY_SPEED, false, true));
		near(helper, (float) cruise, 1.25f, "cruise 25 blocks a second");
		near(helper, (float) sprint, 2.25f, "sprint 45 blocks a second");
		near(helper, (float) stream, 2.5f, "Streamline doubles it");
		// and the kit feeds it: X takes off with the wings and the synced flag the client's flight reads
		floor(helper);
		ServerPlayer p = jetray(helper);
		// the mock transformed mid-air (already on the wings): set him down first
		p.getAbilities().flying = false;
		p.onUpdateAbilities();
		helper.assertTrue(kit().wingBeat(p), "X");
		kit().wingStroke(p);
		helper.assertTrue(p.getAbilities().flying, "flying (abilities)");
		helper.assertTrue((Omnitrix.state(p).kitFlags & JetrayKit.F_FLYING) != 0, "the synced flying flag the client's wings read");
		helper.assertTrue("jetray".equals(Omnitrix.active(p)), "the directional flight's switch sees Jetray (got " + Omnitrix.active(p) + ")");
		helper.succeed();
	}

	/** On the wings, Shift down. */
	private static ServerPlayer airborne(GameTestHelper helper) {
		ServerPlayer p = jetray(helper);
		p.getAbilities().mayfly = true;
		p.getAbilities().flying = true;
		p.onUpdateAbilities();
		p.setShiftKeyDown(true);
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void jetraySupersonicChargesThreeSecondsThenFliesUntilReleased(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = airborne(helper);
		JetrayKit k = kit();
		helper.assertTrue(JetrayKit.SUPERSONIC.equals(k.moveOn(AbilitySlot.SLOT_3, true, false).id()), "Supersonic Flight is on Shift+X");
		helper.assertTrue(JetrayKit.WING_BEAT.equals(k.moveOn(AbilitySlot.SLOT_3, false, false).id()), "and flight is off X");
		k.press(p, AbilitySlot.SLOT_3, true);
		helper.assertTrue(JetrayKit.supersonicCharging(p) && k.keyActive(p, AbilitySlot.SLOT_3), "charging");
		helper.assertFalse(JetrayKit.supersonic(p), "not yet");
		helper.assertTrue(k.cooldownLeft(p, JetrayKit.SUPERSONIC) == 0, "no cooldown while it runs");
		pump(helper, p);
		helper.runAfterDelay(JetrayKit.SONIC_CHARGE - 5, () -> {
			helper.assertTrue(JetrayKit.supersonicCharging(p) && !JetrayKit.supersonic(p), "still charging just before 3 s");
			helper.runAfterDelay(8, () -> {
				helper.assertTrue(JetrayKit.supersonic(p) && !JetrayKit.supersonicCharging(p), "through the barrier at 3 s");
				helper.assertTrue(JetrayKit.fastMove(p), "the server takes his 10 blocks a tick");
				k.release(p, AbilitySlot.SLOT_3, true, JetrayKit.SONIC_CHARGE + 8);
				helper.assertFalse(JetrayKit.supersonic(p), "it stops with the keys");
				helper.assertTrue(k.cooldownLeft(p, JetrayKit.SUPERSONIC) > JetrayKit.SONIC_COOLDOWN - 3, "the 5 s cooldown runs from the release");
				helper.assertTrue(JetrayKit.fastMove(p), "and his last fast moves still get through");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void jetraySupersonicNeedsFlightAndBothKeysHeld(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = jetray(helper);
		JetrayKit k = kit();
		p.getAbilities().flying = false;
		p.onUpdateAbilities();
		helper.assertFalse(k.supersonicStart(p), "not on the ground");
		helper.assertTrue(k.cooldownLeft(p, JetrayKit.SUPERSONIC) == 0, "and that costs nothing");
		p.getAbilities().flying = true;
		p.onUpdateAbilities();
		p.setShiftKeyDown(true);
		helper.assertTrue(k.supersonicStart(p), "flying, it starts");
		k.release(p, AbilitySlot.SLOT_3, true, 5);
		helper.assertFalse(JetrayKit.supersonicCharging(p), "X up drops the charge");
		helper.assertTrue(k.cooldownLeft(p, JetrayKit.SUPERSONIC) <= JetrayKit.SONIC_CANCEL_COOLDOWN, "with the short cooldown");
		clearCooldown(p, JetrayKit.SUPERSONIC);
		helper.assertTrue(k.supersonicStart(p), "again");
		p.setShiftKeyDown(false);
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			helper.assertFalse(JetrayKit.supersonicCharging(p), "Shift up drops it too");
			clearCooldown(p, JetrayKit.SUPERSONIC);
			p.setShiftKeyDown(true);
			helper.assertTrue(k.supersonicStart(p), "once more");
			k.stall(p);
			helper.assertFalse(JetrayKit.supersonicCharging(p), "a clipped wing ends it");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void jetraySupersonicHolds200BlocksASecondAndBrakesForUnloadedChunks(GameTestHelper helper) {
		Vec3 look = new Vec3(0.6, -0.1, 0.8).normalize();
		DirectionalFlightModel.Tune t = DirectionalFlightModel.jetraySupersonic(DirectionalFlightModel.SUPERSONIC_LOOK);
		Vec3 v = Vec3.ZERO;
		for (int i = 0; i < 40; i++) {
			Vec3 wanted = DirectionalFlightModel.wantedVelocity(look, 0f, 1f, 1f, 1, t);
			v = DirectionalFlightModel.step(v, wanted, true, t, false);
			helper.assertTrue(v.length() <= 10.0 + 1.0e-6, "never over 10 blocks a tick (" + v.length() + ")");
		}
		helper.assertTrue(v.length() > 9.9, "200 blocks a second (" + v.length() * 20 + ")");
		helper.assertTrue(DirectionalFlightModel.verticalInput(false, true, true) == 0, "Shift is the key chord, not a descent");
		near(helper, (float) DirectionalFlightModel.supersonicLimit(96), 10f, "all clear: full speed");
		near(helper, (float) DirectionalFlightModel.supersonicLimit(54), 5f, "the edge coming up: easing down");
		helper.assertTrue(DirectionalFlightModel.supersonicLimit(20) < 0.1, "held short of the edge");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void jetraySupersonicCannotTunnelThroughAWall(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = airborne(helper);
		// a wall one block thick, 4 blocks ahead: a single 10-block step would land well past it
		for (int x = 0; x <= 7; x++) {
			for (int y = 2; y <= 5; y++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, 6)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		double wall = helper.absoluteVec(new Vec3(0, 0, 6)).z;
		p.setPos(p.getX(), p.getY() + 0.2, p.getZ());
		p.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, 0, 10));
		helper.assertTrue(p.getZ() + p.getBbWidth() / 2 <= wall + 1.0e-6, "stopped at the wall, not through it (z " + p.getZ() + ", wall " + wall + ")");
		helper.assertTrue(p.getZ() > wall - 2, "and he did travel");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void jetrayWingBeatThrowsHimAndTheDownwashHits(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = jetray(helper);
		JetrayKit k = kit();
		p.getAbilities().flying = false;
		p.onUpdateAbilities();
		p.setOnGround(true);
		Villager v = prey(helper, 3.5, 2, 4.5, 40f);
		helper.assertTrue(k.wingBeat(p), "the wings go up");
		near(helper, v.getHealth(), 40f, "no hit until the stroke");
		k.wingStroke(p);
		helper.assertTrue(p.getAbilities().flying && k.flag(p, JetrayKit.F_FLYING), "off the ground it is a take-off");
		near(helper, v.getHealth(), 40f - JetrayKit.BEAT_DAMAGE, "the downwash");
		helper.assertTrue(p.getDeltaMovement().y > 1.5, "thrown up (" + p.getDeltaMovement() + ")");
		helper.assertTrue(k.cooldownLeft(p, JetrayKit.WING_BEAT) > JetrayKit.BEAT_COOLDOWN - 5, "3 s cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void jetrayTurningBackLeavesNothingBehind(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = jetray(helper);
		JetrayKit k = kit();
		Villager v = prey(helper, 3.5, 2, 4.5, 40f);
		k.tailGrab(p);
		k.eyes(p);
		k.wingBeat(p);
		k.stall(p);
		k.roll(p);
		k.tick(p, Omnitrix.state(p), p.level().getGameTime());
		p.getAbilities().mayfly = true;
		p.getAbilities().flying = true;
		k.supersonicStart(p);
		helper.assertTrue(JetrayKit.stacks(p, v) > 0 || v.isAlive(), "state built up");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.assertFalse(JetrayKit.holds(p), "no static state left");
		helper.assertTrue(Omnitrix.state(p).kitFlags == 0, "no kit flags left");
		helper.assertTrue(Omnitrix.state(p).readyAt(JetrayKit.ID + "." + JetrayKit.CARRY_TARGET) <= 0, "no carry left");
		helper.assertFalse(p.getAbilities().mayfly || p.getAbilities().flying, "no flight left");
		helper.succeed();
	}
}
