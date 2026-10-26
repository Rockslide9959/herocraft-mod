package com.projecthero.mod.gametest;

import com.projecthero.mod.flash.FlashSuit;
import com.projecthero.mod.flash.SpeedForce;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.hero.device.ModDevices;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.speedforce.SpeedForceAbilities;
import com.projecthero.mod.speedforce.SpeedForceConfig;
import com.projecthero.mod.speedforce.SpeedForceMod;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;
import com.projecthero.mod.speedforce.SpeedForceUpgrade;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Speed Force Speedster: the awakening (each missing piece refuses; the plate run over and the lightning strike
 * both take a ready Speedster every time), the momentum curve (build, stop, walk, wall, hits, the Surge lock), the Flash
 * Suit's +50%, and smoke tests of the moves. Mock players never move or tick on their own: the run is fed through
 * {@link SpeedForceSpeedster#stepRun} and the per-tick upkeep through {@link SpeedForceSpeedster#tick}.
 */
public class SpeedForceSpeedsterGameTests implements FabricGameTest {
	private static final double EPS = 0.04;

	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
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

	/** A plain Speedster with everything the awakening needs (the Speed effect, a Tachyon Device, Overdrive). */
	private static ServerPlayer ready(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		SpeedForce.grant(p);
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1));
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(SpeedForceMod.TACHYON_DEVICE));
		overdrive(p);
		return p;
	}

	private static void overdrive(ServerPlayer p) {
		BatchA.set(p, SuperSpeedHandlers.KEY, SuperSpeedHandlers.OVERDRIVE_UNTIL, p.level().getGameTime() + 600, 1e12f);
	}

	/** A Speed Force Speedster with a full meter and no momentum, facing +Z. */
	private static ServerPlayer flash(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		SpeedForceSpeedster.grant(p);
		SpeedForceSpeedster.setMomentum(p, 0f);
		SpeedForceSpeedster.resetRun(p);
		p.removeAllEffects();
		return p;
	}

	/** {@code ticks} ticks of flat-out running along +X at whatever the current momentum is worth. */
	private static void run(ServerPlayer p, int ticks) {
		p.setSprinting(true);
		for (int i = 0; i < ticks; i++) {
			double perTick = SpeedForceSpeedster.targetPerTick(p);
			SpeedForceSpeedster.stepRun(p, new Vec3(Math.max(0.5, perTick * 1.1), 0, 0));
		}
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z, double health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	// ---------------------------------------------------------------- the awakening

	@GameTest(template = EMPTY_STRUCTURE)
	public void upgradeRefusesEachMissingPiece(GameTestHelper helper) {
		helper.assertTrue(HeroTiers.HERO_KEYS.contains(SpeedForceSpeedster.KEY) && PowerGrants.HERO_TIER_KEYS.contains(SpeedForceSpeedster.KEY)
				&& PowerGrants.NOT_FROM_SERUMS.contains(SpeedForceSpeedster.KEY), "a Hero-Tier key no serum rolls");
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		helper.assertTrue(SpeedForceUpgrade.check(p) == SpeedForceUpgrade.Check.NOT_SPEEDSTER, "not a Speedster yet");
		helper.assertFalse(SpeedForceUpgrade.trySurge(p), "a non-Speedster is never taken");
		SpeedForce.grant(p);
		helper.assertTrue(SuperSpeedHandlers.owns(p), "a plain Speedster");
		helper.assertTrue(SpeedForceUpgrade.check(p) == SpeedForceUpgrade.Check.NO_SPEED, "needs the Speed effect");
		helper.assertFalse(SpeedForceUpgrade.trySurge(p), "refused without Speed");
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));
		helper.assertTrue(SpeedForceUpgrade.check(p) == SpeedForceUpgrade.Check.NO_TACHYON, "needs the Tachyon Device");
		helper.assertFalse(SpeedForceUpgrade.trySurge(p), "refused without the Tachyon Device");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SpeedForceMod.TACHYON_DEVICE));
		helper.assertTrue(SpeedForceUpgrade.check(p) == SpeedForceUpgrade.Check.NOT_OVERDRIVE, "needs Overdrive");
		helper.assertFalse(SpeedForceUpgrade.trySurge(p), "refused outside Overdrive");
		helper.assertTrue(SuperSpeedHandlers.owns(p) && !SpeedForceSpeedster.hasPower(p), "every refusal leaves the plain Speedster as it was");
		overdrive(p);
		helper.assertTrue(SpeedForceUpgrade.check(p) == SpeedForceUpgrade.Check.READY, "everything in place");
		helper.assertTrue(SpeedForceUpgrade.trySurge(p), "the surge: the Speed Force takes them");
		helper.assertTrue(SpeedForceSpeedster.hasPower(p) && !SuperSpeedHandlers.owns(p), "the mutation is gone, the Speed Force Speedster is in");
		helper.assertTrue(HeroTiers.heroCount(p) == 1 && HeroTiers.holdsHero(p, SpeedForceSpeedster.KEY), "the one Primary");
		helper.assertFalse(p.hasEffect(MobEffects.MOVEMENT_SPEED), "the surge burned the Speed effect off");
		helper.assertTrue(p.getMainHandItem().is(SpeedForceMod.TACHYON_DEVICE), "the Tachyon Device is not used up");
		helper.assertTrue(FlashSuit.mayWear(p), "the Speed Force Speedster may wear the Flash Suit");
		helper.assertTrue(SpeedForceUpgrade.check(p) == SpeedForceUpgrade.Check.ALREADY, "already taken");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void runningOverChargedCopperPlatesAwakens(GameTestHelper helper) {
		BlockPos plate = new BlockPos(4, 1, 2);
		helper.setBlock(plate, ModDevices.CHARGED_COPPER_PLATES);
		helper.setBlock(new BlockPos(1, 1, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(7, 1, 2), Blocks.STONE);
		ServerPlayer p = ready(helper, 1.5, 2.0, 2.5);
		Vec3 a = p.position();
		Vec3 b = helper.absoluteVec(new Vec3(7.5, 2.0, 2.5));
		helper.assertTrue(SpeedForceUpgrade.crossesPlate(p, a, b), "the 6-block path in one tick crosses the plate");
		helper.assertFalse(SpeedForceUpgrade.crossesPlate(p, a, a.add(1.0, 0, 0)), "a path that stops short does not");
		SpeedForceSpeedster.tick(p); // remembers where the run started
		p.moveTo(b.x, b.y, b.z);
		SpeedForceSpeedster.tick(p); // ... and sweeps the path: over the plate at Overdrive speed
		helper.assertTrue(SpeedForceSpeedster.hasPower(p), "run over the plate: the Speed Force took them");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void lightningAwakensAReadySpeedsterAndFeedsTheMeter(GameTestHelper helper) {
		ServerPlayer p = ready(helper, 2.5, 2.0, 2.5);
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		helper.assertFalse(SpeedForceUpgrade.onLightning(p), "struck without the Tachyon Device: nothing");
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(SpeedForceMod.TACHYON_DEVICE));
		helper.assertTrue(SpeedForceUpgrade.onLightning(p), "struck with everything in place: taken, every time");
		SpeedForceSpeedster.spendMeter(p, SpeedForceSpeedster.meter(p));
		SpeedForceUpgrade.onLightning(p);
		helper.assertTrue(Math.abs(SpeedForceSpeedster.meter(p) - SpeedForceConfig.LIGHTNING_GAIN) < 0.01f, "a strike on the Speed Force Speedster fills 25");
		helper.succeed();
	}

	// ---------------------------------------------------------------- momentum

	@GameTest(template = EMPTY_STRUCTURE)
	public void momentumBuildsOnTheCurve(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		run(p, 3 * 20 + 2);
		float m3 = SpeedForceSpeedster.momentum(p);
		helper.assertTrue(Math.abs(m3 - 0.25f) < EPS, "about 25% after 3 s of running, got " + m3);
		run(p, 7 * 20);
		float m10 = SpeedForceSpeedster.momentum(p);
		helper.assertTrue(Math.abs(m10 - 0.60f) < EPS, "about 60% after 10 s, got " + m10);
		run(p, 10 * 20);
		float m20 = SpeedForceSpeedster.momentum(p);
		helper.assertTrue(m20 > 0.98f, "full after 20 s, got " + m20);
		helper.assertTrue(Math.abs(SpeedForceSpeedster.targetBps(p) - SpeedForceConfig.TOP_SPEED_BPS / SpeedForceConfig.SUIT_SPEED_MULTIPLIER) < 0.5,
				"full momentum without the suit: 167 b/s");
		helper.assertTrue(SpeedForceSpeedster.meter(p) >= SpeedForceConfig.METER_MAX - 0.01f, "the run filled the Speed Force");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void momentumIsLostStoppingWalkingAndAtWalls(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		// stopping: all of it in 1.5 s
		SpeedForceSpeedster.setMomentum(p, 1f);
		p.setSprinting(true);
		for (int i = 0; i < 32; i++) {
			SpeedForceSpeedster.stepRun(p, Vec3.ZERO);
		}
		helper.assertTrue(SpeedForceSpeedster.momentum(p) < 0.01f, "standing 1.6 s: momentum gone, got " + SpeedForceSpeedster.momentum(p));
		// walking: bleeds away slowly
		SpeedForceSpeedster.resetRun(p);
		SpeedForceSpeedster.setMomentum(p, 1f);
		p.setSprinting(false);
		for (int i = 0; i < 40; i++) {
			SpeedForceSpeedster.stepRun(p, new Vec3(0.45, 0, 0));
		}
		float walked = SpeedForceSpeedster.momentum(p);
		helper.assertTrue(walked < 0.85f && walked > 0.75f, "2 s of walking costs ~20%, got " + walked);
		// a stall with nothing in front (a lag spike): not a wall
		SpeedForceSpeedster.resetRun(p);
		SpeedForceSpeedster.setMomentum(p, 1f);
		p.setSprinting(true);
		SpeedForceSpeedster.stepRun(p, new Vec3(8.0, 0, 0));
		SpeedForceSpeedster.stepRun(p, new Vec3(8.0, 0, 0));
		SpeedForceSpeedster.stepRun(p, Vec3.ZERO);
		SpeedForceSpeedster.stepRun(p, Vec3.ZERO);
		helper.assertTrue(SpeedForceSpeedster.momentum(p) > 0.9f, "a stall in the open is no wall, got " + SpeedForceSpeedster.momentum(p));
		// a wall: flat out, then nothing, with a wall right in front
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 3, 2), Blocks.STONE);
		SpeedForceSpeedster.resetRun(p);
		SpeedForceSpeedster.setMomentum(p, 1f);
		SpeedForceSpeedster.stepRun(p, new Vec3(8.0, 0, 0));
		SpeedForceSpeedster.stepRun(p, new Vec3(8.0, 0, 0));
		SpeedForceSpeedster.stepRun(p, Vec3.ZERO);
		SpeedForceSpeedster.stepRun(p, Vec3.ZERO);
		float wall = SpeedForceSpeedster.momentum(p);
		helper.assertTrue(wall < 0.35f && wall > 0.2f, "a wall keeps 30%, got " + wall);
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.AIR);
		helper.setBlock(new BlockPos(3, 3, 2), Blocks.AIR);
		// a released key only slows by half a tick: no wall
		SpeedForceSpeedster.resetRun(p);
		SpeedForceSpeedster.setMomentum(p, 1f);
		SpeedForceSpeedster.stepRun(p, new Vec3(8.0, 0, 0));
		SpeedForceSpeedster.stepRun(p, new Vec3(8.0, 0, 0));
		SpeedForceSpeedster.stepRun(p, new Vec3(4.4, 0, 0));
		SpeedForceSpeedster.stepRun(p, new Vec3(2.4, 0, 0));
		helper.assertTrue(SpeedForceSpeedster.momentum(p) > 0.9f, "easing off is not a wall");
		// a sharp turn costs a little
		SpeedForceSpeedster.resetRun(p);
		SpeedForceSpeedster.setMomentum(p, 1f);
		SpeedForceSpeedster.stepRun(p, new Vec3(8.0, 0, 0));
		SpeedForceSpeedster.stepRun(p, new Vec3(-8.0, 0, 0));
		float turned = SpeedForceSpeedster.momentum(p);
		helper.assertTrue(turned < 0.95f && turned > 0.85f, "a U-turn costs ~10%, got " + turned);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hitsCostMomentumAndTheSurgeLocksIt(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		SpeedForceSpeedster.setMomentum(p, 0.8f);
		p.hurt(p.damageSources().generic(), 4f);
		helper.assertTrue(Math.abs(SpeedForceSpeedster.momentum(p) - 0.65f) < 0.01f, "a hit costs 15%, got " + SpeedForceSpeedster.momentum(p));
		helper.assertTrue(SpeedForceAbilities.surge(p), "Surge on (50 Speed Force)");
		helper.assertTrue(SpeedForceSpeedster.momentum(p) >= 0.999f, "the Surge sets 100%");
		for (int i = 0; i < 40; i++) {
			SpeedForceSpeedster.stepRun(p, Vec3.ZERO);
		}
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().generic(), 4f);
		helper.assertTrue(SpeedForceSpeedster.momentum(p) >= 0.999f, "locked at 100% through standing still and hits");
		helper.assertFalse(SpeedForceAbilities.surge(p), "not twice at once");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theFlashSuitMakesThemHalfAgainFaster(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		SpeedForceSpeedster.setMomentum(p, 1f);
		p.setSprinting(true);
		SpeedForceSpeedster.resetRun(p);
		p.tickCount++;
		SpeedForceSpeedster.tick(p);
		double bare = p.getAttributeValue(Attributes.MOVEMENT_SPEED) * SpeedForceSpeedster.BLOCKS_PER_SECOND_PER_SPEED;
		helper.assertTrue(Math.abs(bare - 166.7) < 1.5, "full momentum, no suit: ~167 b/s on flat ground, got " + bare);
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(FlashSuit.HELMET));
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(FlashSuit.CHESTPLATE));
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(FlashSuit.LEGGINGS));
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(FlashSuit.BOOTS));
		SpeedForceSpeedster.setMomentum(p, 1f);
		SpeedForceSpeedster.resetRun(p);
		p.tickCount++;
		SpeedForceSpeedster.tick(p);
		double suited = p.getAttributeValue(Attributes.MOVEMENT_SPEED) * SpeedForceSpeedster.BLOCKS_PER_SECOND_PER_SPEED;
		helper.assertTrue(Math.abs(suited - SpeedForceConfig.TOP_SPEED_BPS) < 1.5, "full momentum in the full suit: 250 b/s, got " + suited);
		helper.assertTrue(Math.abs(SpeedForceConfig.sprintBps(0.25, true) - 60.0) < 0.1 && Math.abs(SpeedForceConfig.sprintBps(0.6, true) - 120.0) < 0.1,
				"the suit curve: 60 at 25%, 120 at 60%");
		p.setSprinting(false);
		SpeedForceSpeedster.resetRun(p);
		p.tickCount++;
		SpeedForceSpeedster.tick(p);
		double walk = p.getAttributeValue(Attributes.MOVEMENT_SPEED) * SpeedForceSpeedster.BLOCKS_PER_SECOND_PER_SPEED;
		helper.assertTrue(Math.abs(walk - SpeedForceConfig.WALK_BPS) < 0.5, "walking in Speed Mode is the 20 b/s base, got " + walk);
		helper.succeed();
	}

	// ---------------------------------------------------------------- moves

	@GameTest(template = EMPTY_STRUCTURE)
	public void speedPunchesScaleWithMomentum(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 1.5);
		Zombie z = zombie(helper, 2.5, 2.0, 3.0, 100);
		helper.assertTrue(SpeedForceAbilities.speedPunches(p) == 1, "the flurry lands");
		float slow = 100f - z.getHealth();
		helper.assertTrue(slow >= 11.5f && slow <= 12.5f, "three blows of 4 at no momentum, got " + slow);
		z.setHealth(100f);
		SpeedForceSpeedster.setMomentum(p, 1f);
		SpeedForceSpeedster.peek(p).abilityReadyAt.clear();
		SpeedForceAbilities.speedPunches(p);
		float fast = 100f - z.getHealth();
		helper.assertTrue(fast >= 29.5f && fast <= 30.5f, "three blows of 10 at full momentum, got " + fast);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void infiniteMassPunchSpendsTheRun(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 1.5);
		Zombie z = zombie(helper, 2.5, 2.0, 3.5, 200);
		SpeedForceSpeedster.setMomentum(p, 1f);
		helper.assertTrue(SpeedForceAbilities.infiniteMassPunch(p), "the punch lands");
		float dealt = 200f - z.getHealth();
		helper.assertTrue(dealt >= 69.5f && dealt <= 70.5f, "10 + 60 at full momentum, got " + dealt);
		helper.assertTrue(SpeedForceSpeedster.momentum(p) < 0.001f, "it spent all the momentum");
		helper.assertTrue(SpeedForceSpeedster.cooldownRemaining(p, SpeedForceAbilities.MASS) > 0, "on cooldown");
		// a boss takes at most 8% of its health a hit
		Zombie boss = zombie(helper, 5.5, 2.0, 3.5, 500);
		z.discard();
		p.moveTo(helper.absoluteVec(new Vec3(5.5, 2.0, 1.5)).x, p.getY(), helper.absoluteVec(new Vec3(5.5, 2.0, 1.5)).z, 0f, 0f);
		SpeedForceSpeedster.peek(p).abilityReadyAt.clear();
		SpeedForceSpeedster.setMomentum(p, 1f);
		SpeedForceAbilities.infiniteMassPunch(p);
		float bossDealt = 500f - boss.getHealth();
		helper.assertTrue(bossDealt > 0f && bossDealt <= 40.5f, "a boss is capped at 8% (40), got " + bossDealt);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void lightningThrowHitsAndChains(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 1.5);
		Zombie a = zombie(helper, 2.5, 2.0, 5.5, 100);
		Zombie b = zombie(helper, 5.5, 2.0, 5.5, 100);
		helper.assertTrue(SpeedForceAbilities.fireBolt(p) == a, "the bolt strikes the zombie under the crosshair");
		helper.assertTrue(Math.abs((100f - a.getHealth()) - SpeedForceConfig.THROW_MIN) < 0.5f, "8 at no momentum");
		helper.assertTrue(Math.abs((100f - b.getHealth()) - SpeedForceConfig.THROW_MIN * 0.5f) < 0.5f, "and it arcs on for half");
		helper.succeed();
	}

	/** v0.17.1 (user): C turns Speed Mode on and off; off, no momentum, no speed, no step assist; on, 20 b/s and 4 blocks. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void cTogglesSpeedModeWithTheTwentyBaseAndFourBlockStep(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		helper.assertTrue(SpeedForceSpeedster.speedActive(p), "the awakening comes out running (Speed Mode on)");
		p.setSprinting(true);
		p.tickCount++;
		SpeedForceSpeedster.tick(p);
		double on = p.getAttributeValue(Attributes.MOVEMENT_SPEED) * SpeedForceSpeedster.BLOCKS_PER_SECOND_PER_SPEED;
		helper.assertTrue(Math.abs(on - 20.0) < 1.0, "Speed Mode starts at 20 b/s, got " + on);
		helper.assertTrue(p.getAttributeValue(Attributes.STEP_HEIGHT) >= 3.99, "a 4-block step assist, got " + p.getAttributeValue(Attributes.STEP_HEIGHT));
		helper.assertTrue(SpeedForceAbilities.toggleSpeedMode(p), "C");
		helper.assertFalse(SpeedForceSpeedster.speedActive(p), "Speed Mode off");
		SpeedForceSpeedster.setMomentum(p, 0.7f);
		p.tickCount++;
		SpeedForceSpeedster.tick(p);
		helper.assertTrue(SpeedForceSpeedster.momentum(p) == 0f, "no momentum with Speed Mode off");
		helper.assertTrue(p.getAttributeValue(Attributes.STEP_HEIGHT) < 0.61, "vanilla step height");
		double off = p.getAttributeValue(Attributes.MOVEMENT_SPEED) * SpeedForceSpeedster.BLOCKS_PER_SECOND_PER_SPEED;
		helper.assertTrue(off < 7.0, "an ordinary run, got " + off);
		helper.assertTrue(SpeedForceAbilities.toggleSpeedMode(p) && SpeedForceSpeedster.speedActive(p), "C again: back on");
		helper.succeed();
	}

	/** v0.17.1 (user): hold Shift+C for 3 s, let go, and you are at full speed at once; let go early and it fizzles. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theShiftCChargeLaunchesAtFullSpeed(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		SpeedForceAbilities.toggleSpeedMode(p); // starting from Speed Mode off
		helper.assertTrue(SpeedForceAbilities.startCharge(p), "into the charge stance");
		helper.runAfterDelay(20, () -> {
			helper.assertFalse(SpeedForceAbilities.releaseCharge(p), "let go after 1 s: it fizzles");
			helper.assertFalse(SpeedForceSpeedster.speedActive(p), "still not running");
			helper.assertTrue(SpeedForceAbilities.startCharge(p), "charge again");
		});
		helper.runAfterDelay(20 + SpeedForceConfig.CHARGE_TICKS + 2, () -> {
			helper.assertTrue(SpeedForceAbilities.releaseCharge(p), "let go after the full 3 s: launch");
			helper.assertTrue(SpeedForceSpeedster.speedActive(p) && SpeedForceSpeedster.momentum(p) >= 0.999f, "Speed Mode on at 100% at once");
			helper.assertFalse(SpeedForceAbilities.startCharge(p), "on cooldown");
			helper.succeed();
		});
	}

	/** v0.17.1 (user): the full Flash Suit feeds a hungry speedster -- the best fit, never a food with a side effect. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theFlashSuitFeedsAHungrySpeedster(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		p.getInventory().clearContent(); // before the suit goes on: this clears the armour slots too
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(FlashSuit.HELMET));
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(FlashSuit.CHESTPLATE));
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(FlashSuit.LEGGINGS));
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(FlashSuit.BOOTS));
		p.getInventory().add(new ItemStack(Items.ROTTEN_FLESH, 4));
		p.getInventory().add(new ItemStack(Items.BREAD, 4));
		p.getFoodData().setFoodLevel(10);
		p.tickCount = 40;
		com.projecthero.mod.flash.FlashSuitAutoEat.tick(p);
		helper.assertTrue(p.getFoodData().getFoodLevel() == 15, "ate one bread (+5), got " + p.getFoodData().getFoodLevel());
		helper.assertTrue(p.getInventory().countItem(Items.BREAD) == 3 && p.getInventory().countItem(Items.ROTTEN_FLESH) == 4,
				"bread, never the rotten flesh");
		p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		p.tickCount = 80;
		com.projecthero.mod.flash.FlashSuitAutoEat.tick(p);
		helper.assertTrue(p.getFoodData().getFoodLevel() == 15, "no suit, no feeding");
		helper.succeed();
	}

	/** v0.17.1 (user): in Speed Mode the Speed Force Speedster runs through leaves (still solid from above). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void speedModeRunsThroughLeaves(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		helper.assertTrue(com.projecthero.mod.hero.power.p04.SpeedLeaves.active(p), "Speed Mode: leaves let you through");
		SpeedForceAbilities.toggleSpeedMode(p);
		helper.assertFalse(com.projecthero.mod.hero.power.p04.SpeedLeaves.active(p), "Speed Mode off: leaves are solid");
		helper.succeed();
	}

	/** v0.17.1 (user): Ground-Stick pulls never cost fall damage; real drops still hurt. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void groundStickPullsTakeNoFallDamage(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		SpeedForceSpeedster.setMomentum(p, 0f);
		com.projecthero.mod.speedforce.SpeedsterPrefs.apply(p, new com.projecthero.mod.speedforce.network.SpeedsterPrefsPayload(true, 4));
		helper.assertTrue(SpeedForceSpeedster.state(p).groundStick && SpeedForceSpeedster.state(p).colour == 4, "the menu's choices are saved");
		p.fallDistance = 7f;
		float hp = p.getHealth();
		p.hurt(p.damageSources().fall(), 4f);
		helper.assertTrue(p.getHealth() >= hp - 1.0e-3f, "a Ground-Stick pull: no fall damage");
		p.invulnerableTime = 0;
		p.fallDistance = 20f;
		p.hurt(p.damageSources().fall(), 4f);
		helper.assertTrue(p.getHealth() < hp, "a real drop still hurts");
		helper.succeed();
	}

	/** v0.17.1 (user): V is the Speed Force Barrier now -- arrows are batted out, mobs pushed out, no damage. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theBarrierDeflectsArrowsAndPushesMobsOut(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 3.5, 2.0, 3.5);
		Zombie z = zombie(helper, 5.0, 2.0, 3.5, 100);
		Vec3 from = helper.absoluteVec(new Vec3(3.5, 3.0, 6.0));
		net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, helper.getLevel());
		arrow.moveTo(from.x, from.y, from.z);
		arrow.setDeltaMovement(0, 0, -1.5); // straight at the runner
		helper.getLevel().addFreshEntity(arrow);
		helper.assertTrue(SpeedForceAbilities.speedForceBarrier(p), "the Barrier goes up (15 Speed Force)");
		helper.assertTrue(SpeedForceAbilities.barrierUp(p), "and is up");
		p.tickCount++;
		SpeedForceSpeedster.tick(p);
		helper.assertTrue(arrow.getDeltaMovement().z > 0.3, "the arrow is knocked back out: " + arrow.getDeltaMovement());
		helper.assertTrue(z.getDeltaMovement().x > 0.3, "the zombie is pushed out of the ring: " + z.getDeltaMovement());
		helper.assertTrue(z.getHealth() >= 100f - 1.0e-3f, "no damage");
		helper.assertTrue(SpeedForceAbilities.speedForceBarrier(p), "V again drops it");
		helper.assertFalse(SpeedForceAbilities.barrierUp(p), "it is down");
		helper.assertFalse(SpeedForceAbilities.speedForceBarrier(p), "and on cooldown");
		helper.succeed();
	}

	/** v0.17.1 (user): Rescue Run moved from V to N; V is the Barrier. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void nIsRescueRunAndVIsTheBarrier(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 1.5);
		Zombie z = zombie(helper, 2.5, 2.0, 3.5, 100);
		helper.assertTrue(com.projecthero.mod.speedforce.SpeedForceAbilityManager.idsOf(com.projecthero.mod.hero.AbilitySlot.SLOT_5)[0]
				.equals(SpeedForceAbilities.BARRIER), "V's move is the Barrier");
		com.projecthero.mod.hero.AbilityRouter.handleInput(p, 8, true);
		com.projecthero.mod.hero.AbilityRouter.handleInput(p, 8, false);
		helper.assertTrue(SpeedForceSpeedster.state(p).carryId == z.getId(), "N picks the zombie up (Rescue Run)");
		helper.assertFalse(SpeedForceAbilities.barrierUp(p), "N raised no barrier");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rewindGoesBackFiveSeconds(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 1.5, 2.0, 1.5);
		Vec3 start = p.position();
		for (int i = 0; i < SpeedForceConfig.REWIND_TICKS + 1; i++) {
			p.tickCount++;
			SpeedForceSpeedster.tick(p);
		}
		Vec3 moved = helper.absoluteVec(new Vec3(6.5, 2.0, 6.5));
		p.moveTo(moved.x, moved.y, moved.z);
		p.setHealth(6f);
		helper.assertTrue(SpeedForceAbilities.rewind(p), "Rewind (60 Speed Force)");
		helper.assertTrue(p.position().distanceTo(start) < 0.01, "back where they stood 5 s ago");
		helper.assertTrue(p.getHealth() >= 19.9f, "with the health they had then");
		helper.assertFalse(SpeedForceAbilities.rewind(p), "on cooldown (and the meter is short)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void noFallDamageAtSpeedAndTheCommandRevokes(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		SpeedForceSpeedster.setMomentum(p, 0.5f);
		float before = p.getHealth();
		p.hurt(p.damageSources().fall(), 6f);
		helper.assertTrue(p.getHealth() == before, "no fall damage above 25% momentum");
		SpeedForceSpeedster.setMomentum(p, 0.1f);
		p.hurt(p.damageSources().fall(), 6f);
		helper.assertTrue(p.getHealth() < before, "slow, a fall hurts");
		PowerGrants.revokeHeroTier(p, SpeedForceSpeedster.KEY);
		helper.assertFalse(SpeedForceSpeedster.hasPower(p), "revoked");
		helper.succeed();
	}
}
