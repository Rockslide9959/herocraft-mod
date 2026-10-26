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
import net.minecraft.server.level.ServerLevel;
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
	public void hitsCostMomentumAndTheLaunchLocksIt(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		SpeedForceSpeedster.setMomentum(p, 0.8f);
		p.hurt(p.damageSources().generic(), 4f);
		helper.assertTrue(Math.abs(SpeedForceSpeedster.momentum(p) - 0.65f) < 0.01f, "a hit costs 15%, got " + SpeedForceSpeedster.momentum(p));
		// the hold-C launch locks the momentum at 100% for its 2 s
		SpeedForceSpeedster.setMomentum(p, 1f);
		SpeedForceSpeedster.peek(p).surgeUntil = p.level().getGameTime() + SpeedForceConfig.LAUNCH_HOLD_TICKS;
		for (int i = 0; i < 20; i++) {
			SpeedForceSpeedster.stepRun(p, Vec3.ZERO);
		}
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().generic(), 4f);
		helper.assertTrue(SpeedForceSpeedster.momentum(p) >= 0.999f, "locked at 100% through standing still and hits");
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
	/** v0.17.1 (user): tap R is the plain Speedster's Rapid Assault, its numbers (four punches of 5). */
	public void rapidAssaultIsThePlainFlurry(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 1.5);
		Zombie z = zombie(helper, 2.5, 2.0, 3.0, 100);
		// a TAP: press and let go before the hold
		com.projecthero.mod.speedforce.SpeedForceAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1, true);
		p.tickCount += 3;
		SpeedForceSpeedster.tick(p);
		helper.assertTrue(z.getHealth() >= 100f - 1.0e-3f, "nothing on the press: a tap fires on the release");
		com.projecthero.mod.speedforce.SpeedForceAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1, false);
		float dealt = 100f - z.getHealth();
		helper.assertTrue(dealt >= 29.5f && dealt <= 30.5f, "four blows of 5, x1.5 for the Speed Force: 30, got " + dealt);
		helper.assertTrue(SpeedForceSpeedster.cooldownRemaining(p, SpeedForceAbilities.RAPID) > 0, "on its own cooldown");
		helper.assertFalse(SpeedForceAbilities.massWindingUp(p), "a tap never winds up the Mass Punch");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void infiniteMassPunchSpendsTheRun(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 1.5);
		Zombie z = zombie(helper, 2.5, 2.0, 3.5, 200);
		SpeedForceSpeedster.setMomentum(p, 1f);
		helper.assertTrue(SpeedForceAbilities.infiniteMassPunch(p), "the punch lands");
		float dealt = 200f - z.getHealth();
		helper.assertTrue(dealt >= 104.5f && dealt <= 105.5f, "(10 + 60) x1.5 at full momentum, got " + dealt);
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
		helper.assertTrue(Math.abs((100f - a.getHealth()) - SpeedForceConfig.THROW_MIN * SpeedForceConfig.DAMAGE_BONUS) < 0.5f, "8 x1.5 at no momentum");
		helper.assertTrue(Math.abs((100f - b.getHealth()) - SpeedForceConfig.THROW_MIN * SpeedForceConfig.DAMAGE_BONUS * 0.5f) < 0.5f, "and it arcs on for half");
		helper.succeed();
	}

	/**
	 * v0.17.1 (user): C turns Speed Mode on and off; off, no momentum, no speed, no step assist; on, 20 b/s and (user: "increase
	 * speed force speedsters step assist") a 6-block step, 8 at full momentum.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void cTogglesSpeedModeWithTheTwentyBaseAndSixBlockStep(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		helper.assertTrue(SpeedForceSpeedster.speedActive(p), "the awakening comes out running (Speed Mode on)");
		p.setSprinting(true);
		p.tickCount++;
		SpeedForceSpeedster.tick(p);
		double on = p.getAttributeValue(Attributes.MOVEMENT_SPEED) * SpeedForceSpeedster.BLOCKS_PER_SECOND_PER_SPEED;
		helper.assertTrue(Math.abs(on - 20.0) < 1.0, "Speed Mode starts at 20 b/s, got " + on);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - 6.0) < 0.01, "a 6-block step assist, got " + p.getAttributeValue(Attributes.STEP_HEIGHT));
		SpeedForceSpeedster.setMomentum(p, 1f);
		SpeedForceSpeedster.resetRun(p);
		p.tickCount++;
		SpeedForceSpeedster.tick(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - 8.0) < 0.01, "8 blocks at full momentum, got " + p.getAttributeValue(Attributes.STEP_HEIGHT));
		// tap C (the toggle fires on the release)
		com.projecthero.mod.speedforce.SpeedForceAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_6, true);
		com.projecthero.mod.speedforce.SpeedForceAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_6, false);
		helper.assertFalse(SpeedForceSpeedster.speedActive(p), "a tap of C: Speed Mode off");
		helper.assertTrue(SpeedForceAbilities.toggleSpeedMode(p), "C");
		helper.assertTrue(SpeedForceSpeedster.speedActive(p), "back on");
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

	/** v0.17.1 (user): HOLD C for 3 s, let go, and you are at full speed at once; let go early and it fizzles. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theHoldCChargeLaunchesAtFullSpeed(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		SpeedForceAbilities.toggleSpeedMode(p); // starting from Speed Mode off
		// holding C past the hold time drops into the stance (no toggle)
		com.projecthero.mod.speedforce.SpeedForceAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_6, true);
		p.tickCount += SpeedForceConfig.HOLD_TICKS;
		SpeedForceSpeedster.tick(p);
		helper.assertTrue(SpeedForceSpeedster.chargeStart(p) != 0L, "held C: the charge stance");
		helper.assertFalse(SpeedForceSpeedster.speedActive(p), "and no Speed Mode toggle");
		com.projecthero.mod.speedforce.SpeedForceAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_6, false);
		helper.assertTrue(SpeedForceSpeedster.chargeStart(p) == 0L && !SpeedForceSpeedster.speedActive(p), "let go at once: it fizzles");
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
		helper.assertTrue(p.getFoodData().getFoodLevel() == 15, "ate one bread (+5), got " + p.getFoodData().getFoodLevel() + " full=" + FlashSuit.wearsFull(p) + " sf=" + SpeedForceSpeedster.hasPower(p)
				+ " creative=" + p.isCreative() + " using=" + p.isUsingItem() + " alive=" + p.isAlive() + " tc=" + p.tickCount + " bread=" + p.getInventory().countItem(Items.BREAD)
				+ " head=" + p.getItemBySlot(EquipmentSlot.HEAD));
		helper.assertTrue(p.getInventory().countItem(Items.BREAD) == 3 && p.getInventory().countItem(Items.ROTTEN_FLESH) == 4,
				"bread, never the rotten flesh");
		p.getFoodData().setFoodLevel(19);
		p.tickCount = 120;
		com.projecthero.mod.flash.FlashSuitAutoEat.tick(p);
		helper.assertTrue(p.getFoodData().getFoodLevel() == 20, "half a bar down is enough: it eats straight away, got " + p.getFoodData().getFoodLevel());
		p.getFoodData().setFoodLevel(15);
		p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		p.tickCount = 80;
		com.projecthero.mod.flash.FlashSuitAutoEat.tick(p);
		helper.assertTrue(p.getFoodData().getFoodLevel() == 15, "no suit, no feeding");
		helper.succeed();
	}

	/** v0.17.1 (user): a speedster's sprint costs an ordinary sprint's hunger however fast they run. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void sprintingAtSpeedCostsOrdinaryHunger(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		p.setSprinting(true);
		p.setOnGround(true);
		float before = p.getFoodData().getExhaustionLevel();
		p.checkMovementStatistics(10.0, 0.0, 0.0); // 10 blocks in a tick: 200 b/s
		float fast = p.getFoodData().getExhaustionLevel() - before;
		helper.assertTrue(fast > 0f && fast < 0.05f, "200 b/s costs about a normal sprint tick (0.028), not 1.0: got " + fast);
		SpeedForceAbilities.toggleSpeedMode(p);
		p.setSprinting(true);
		before = p.getFoodData().getExhaustionLevel();
		p.checkMovementStatistics(10.0, 0.0, 0.0);
		float normal = p.getFoodData().getExhaustionLevel() - before;
		helper.assertTrue(normal > 0.9f, "Speed Mode off: the vanilla charge, got " + normal);
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

	private static void press(ServerPlayer p, com.projecthero.mod.hero.AbilitySlot slot, boolean down) {
		com.projecthero.mod.speedforce.SpeedForceAbilityManager.handle(p, slot, down);
	}

	/** {@code n} ticks of the runner's own clock (the per-tick upkeep; mock players never tick on their own). */
	private static void ticks(ServerPlayer p, int n) {
		for (int i = 0; i < n; i++) {
			p.tickCount++;
			SpeedForceSpeedster.tick(p);
		}
	}

	/** v0.17.1 (user): hold X -- Tornado Arms: the cone of wind pushes a mob back and lifts it, blows arrows away, puts out fire. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void tornadoArmsPushesAMobBack(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 1.5);
		Zombie z = zombie(helper, 2.5, 2.0, 4.5, 100);
		Vec3 from = helper.absoluteVec(new Vec3(2.5, 3.0, 6.5));
		net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, helper.getLevel());
		arrow.moveTo(from.x, from.y, from.z);
		arrow.setDeltaMovement(0, 0, -1.5); // straight at the runner, through the cone
		helper.getLevel().addFreshEntity(arrow);
		helper.setBlock(new BlockPos(2, 1, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 2, 3), Blocks.FIRE);
		// hold X: nothing on the press, Tornado Arms once held (no Momentum Dash)
		press(p, com.projecthero.mod.hero.AbilitySlot.SLOT_3, true);
		ticks(p, SpeedForceConfig.HOLD_TICKS - 1);
		helper.assertFalse(SpeedForceAbilities.tornadoArms(p), "not yet: still a possible tap");
		p.tickCount = (p.tickCount / 4 + 1) * 4 - 1; // the next tick puts fire out (every 4th)
		ticks(p, 1);
		helper.assertTrue(SpeedForceAbilities.tornadoArms(p), "held X: Tornado Arms");
		helper.assertTrue(z.getDeltaMovement().z > 0.3, "the zombie is blown back along the cone: " + z.getDeltaMovement());
		helper.assertTrue(z.getDeltaMovement().y >= SpeedForceConfig.ARMS_LIFT - 1.0e-3, "and lifted: " + z.getDeltaMovement());
		helper.assertTrue(arrow.getDeltaMovement().z > 0.3, "the arrow is blown away: " + arrow.getDeltaMovement());
		helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 3)).isAir(), "the fire in the cone is out");
		helper.assertTrue(z.getHealth() >= 100f - 1.0e-3f, "wind, no damage");
		float before = SpeedForceSpeedster.meter(p);
		ticks(p, 20);
		float spent = before - SpeedForceSpeedster.meter(p);
		helper.assertTrue(Math.abs(spent - SpeedForceConfig.ARMS_COST_PER_SECOND) < 0.01f, "8 Speed Force a second, got " + spent);
		press(p, com.projecthero.mod.hero.AbilitySlot.SLOT_3, false);
		helper.assertFalse(SpeedForceAbilities.tornadoArms(p), "let go: the wind drops");
		helper.assertTrue(SpeedForceSpeedster.cooldownRemaining(p, SpeedForceAbilities.ARMS) > 0, "its short cooldown");
		helper.assertTrue(SpeedForceSpeedster.cooldownRemaining(p, SpeedForceAbilities.DASH) == 0, "and no Momentum Dash fired");
		helper.succeed();
	}

	/** v0.17.1 (user): N is Rescue Run; every key is tap / Shift / hold (H and N never on the HUD). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void nIsRescueRunAndTheKeysAreTapShiftHold(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 1.5);
		Zombie z = zombie(helper, 2.5, 2.0, 3.5, 100);
		String[] v = com.projecthero.mod.speedforce.SpeedForceAbilityManager.idsOf(com.projecthero.mod.hero.AbilitySlot.SLOT_5);
		helper.assertTrue(v.length == 3 && v[0].equals(SpeedForceAbilities.DECOYS) && v[1].equals(SpeedForceAbilities.HEAL)
				&& v[2].equals(SpeedForceAbilities.REWIND), "V: Afterimage Decoys / Speed Heal / hold Rewind");
		String[] r = com.projecthero.mod.speedforce.SpeedForceAbilityManager.idsOf(com.projecthero.mod.hero.AbilitySlot.SLOT_1);
		helper.assertTrue(r[0].equals(SpeedForceAbilities.RAPID) && r[1].equals(SpeedForceAbilities.MACH) && r[2].equals(SpeedForceAbilities.MASS),
				"R: Rapid Assault / Mach Punch / hold Infinite Mass Punch");
		com.projecthero.mod.hero.AbilityRouter.handleInput(p, 8, true);
		com.projecthero.mod.hero.AbilityRouter.handleInput(p, 8, false);
		helper.assertTrue(SpeedForceSpeedster.state(p).carryId == z.getId(), "N picks the zombie up (Rescue Run)");
		helper.succeed();
	}

	/**
	 * v0.17.1 (user: "make the infinite mass punch auto track to the nearest enemy"): on release it locks onto the nearest foe
	 * within 8 blocks, the crosshair cone first, zips onto it and hits.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theMassPunchTracksTheNearestFoe(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 4.5, 2.0, 1.5); // facing +Z
		Zombie side = zombie(helper, 7.5, 2.0, 2.5, 200); // nearer, but well off the crosshair
		Zombie ahead = zombie(helper, 4.5, 2.0, 7.5, 200); // farther, under the crosshair
		helper.assertTrue(SpeedForceAbilities.massTarget(p) == ahead, "the one under the crosshair wins");
		ahead.discard();
		helper.assertTrue(SpeedForceAbilities.massTarget(p) == side, "else the nearest at all");
		Zombie far = zombie(helper, 4.5, 2.0, 7.5, 200);
		side.discard();
		// hold R, let go: the wind-up, then the punch goes to it
		SpeedForceSpeedster.setMomentum(p, 1f);
		press(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1, true);
		ticks(p, SpeedForceConfig.HOLD_TICKS);
		helper.assertTrue(SpeedForceAbilities.massWindingUp(p), "held R: winding up");
		SpeedForceSpeedster.setMomentum(p, 1f);
		press(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1, false);
		float dealt = 200f - far.getHealth();
		helper.assertTrue(dealt >= 104.5f && dealt <= 105.5f, "it locked on 6 blocks off and landed (10 + 60) x1.5, got " + dealt);
		helper.assertTrue(p.position().distanceTo(far.position()) < 2.5, "it zipped the runner up against it: " + p.position().distanceTo(far.position()));
		helper.assertTrue(SpeedForceSpeedster.momentum(p) < 0.001f, "and spent the run");
		helper.assertFalse(SpeedForceAbilities.massWindingUp(p), "the wind-up is over");
		far.discard();
		SpeedForceSpeedster.peek(p).abilityReadyAt.clear();
		helper.assertFalse(SpeedForceAbilities.infiniteMassPunch(p), "nobody within 8 blocks: no punch");
		helper.succeed();
	}

	/** v0.17.1 (user: "make flash time a held down ability that drains speed force while active"). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void holdZFlashTimeDrainsTheSpeedForce(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow.end(p, false);
		if (com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow.anyActive()) {
			helper.succeed(); // another test's slow holds the world: one at a time
			return;
		}
		try {
			press(p, com.projecthero.mod.hero.AbilitySlot.SLOT_4, true);
			helper.assertTrue(com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow.isCasting(p) && SpeedForceAbilities.flashHeld(p),
					"Z down: Flash Time from the press");
			float before = SpeedForceSpeedster.meter(p);
			ticks(p, 40);
			float spent = before - SpeedForceSpeedster.meter(p);
			helper.assertTrue(Math.abs(spent - 2f * SpeedForceConfig.FLASH_COST_PER_SECOND) < 0.01f, "8 Speed Force a second for 2 s, got " + spent);
			helper.assertTrue(com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow.isCasting(p), "still running while held");
			press(p, com.projecthero.mod.hero.AbilitySlot.SLOT_4, false);
			helper.assertFalse(com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow.isCasting(p) || SpeedForceAbilities.flashHeld(p),
					"let go: time runs again");
			helper.assertTrue(SpeedForceSpeedster.cooldownRemaining(p, SpeedForceAbilities.FLASH) > 0, "its short cooldown");
			// an empty meter ends it too
			SpeedForceSpeedster.peek(p).abilityReadyAt.clear();
			SpeedForceSpeedster.spendMeter(p, SpeedForceSpeedster.meter(p) - 6f);
			press(p, com.projecthero.mod.hero.AbilitySlot.SLOT_4, true);
			helper.assertTrue(com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow.isCasting(p), "started on 6 Speed Force");
			ticks(p, 30);
			helper.assertFalse(com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow.isCasting(p), "the meter ran out: it ended");
			press(p, com.projecthero.mod.hero.AbilitySlot.SLOT_4, false);
		} finally {
			com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow.end(p, false);
		}
		helper.succeed();
	}

	/** One tick of flat-out running for the portal (the mock never moves: the run is fed, then the upkeep runs). */
	private static void runTick(ServerPlayer p) {
		SpeedForceSpeedster.stepRun(p, new Vec3(12.0, 0, 0));
		ticks(p, 1);
	}

	/**
	 * v0.17.1 (user: "holding z for 5 seconds while running at max momentum open up the speed force ... the portal should start
	 * forming in front of them"): the charge grows only at max momentum; stopping collapses it; 5 s in, it takes them.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void thePortalChargesAtFullMomentumAndCollapsesWhenStopping(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow.end(p, false);
		if (com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow.anyActive()) {
			helper.succeed();
			return;
		}
		ServerLevel realm = helper.getLevel().getServer().getLevel(com.projecthero.mod.speedforce.SpeedForceRealm.DIMENSION);
		try {
			p.setSprinting(true);
			SpeedForceSpeedster.setMomentum(p, 0.5f);
			press(p, com.projecthero.mod.hero.AbilitySlot.SLOT_4, true);
			for (int i = 0; i < 10; i++) {
				runTick(p);
			}
			helper.assertTrue(SpeedForceAbilities.portalCharge(p) == 0, "50% momentum: Flash Time, but no portal");
			SpeedForceSpeedster.setMomentum(p, 1f);
			for (int i = 0; i < 40; i++) {
				runTick(p);
			}
			int charged = SpeedForceAbilities.portalCharge(p);
			helper.assertTrue(charged >= 30 && charged < SpeedForceConfig.PORTAL_TICKS, "at 100% it forms, got " + charged);
			// stopping (the momentum is frozen in Flash Time -- the sprint itself is read: letting go ends it)
			p.setSprinting(false);
			ticks(p, 1);
			helper.assertTrue(SpeedForceAbilities.portalCharge(p) == 0, "stopped: it collapses");
			helper.assertTrue(com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow.isCasting(p), "Flash Time itself goes on while Z is held");
			p.setSprinting(true);
			for (int i = 0; i < SpeedForceConfig.PORTAL_TICKS + 10 && !com.projecthero.mod.speedforce.SpeedForceRealm.inside(p); i++) {
				runTick(p);
			}
			if (realm == null) {
				helper.succeed(); // no datapack dimensions on this GameTest server
				return;
			}
			helper.assertTrue(com.projecthero.mod.speedforce.SpeedForceRealm.inside(p), "5 s of it: the portal took them in");
			helper.assertFalse(com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow.isCasting(p), "Flash Time ended on the way in");
			com.projecthero.mod.speedforce.SpeedForceRealm.exit(p);
		} finally {
			com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow.end(p, false);
		}
		helper.succeed();
	}

	/** The travel mapping: the road's length is the way they faced, its width their right; x100, capped at 30,000. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void speedForceTravelMapsTheRoadOntoTheWorld(GameTestHelper helper) {
		Vec3 south = com.projecthero.mod.speedforce.SpeedForceRealm.travelOffset(0f, 10, 0);
		helper.assertTrue(south.distanceTo(new Vec3(0, 0, 1000)) < 1.0e-6, "facing south, 10 blocks up the road: 1,000 south, got " + south);
		Vec3 east = com.projecthero.mod.speedforce.SpeedForceRealm.travelOffset(-90f, 10, 0);
		helper.assertTrue(east.distanceTo(new Vec3(1000, 0, 0)) < 1.0e-6, "facing east: 1,000 east, got " + east);
		Vec3 right = com.projecthero.mod.speedforce.SpeedForceRealm.travelOffset(0f, 0, 3);
		helper.assertTrue(right.distanceTo(new Vec3(-300, 0, 0)) < 1.0e-6, "the road's right (facing south: west) x100, got " + right);
		Vec3 back = com.projecthero.mod.speedforce.SpeedForceRealm.travelOffset(180f, -2, 0);
		helper.assertTrue(back.distanceTo(new Vec3(0, 0, 200)) < 1.0e-6, "running back down the road goes the other way, got " + back);
		Vec3 cap = com.projecthero.mod.speedforce.SpeedForceRealm.travelOffset(37f, 5000, 0);
		helper.assertTrue(Math.abs(cap.length() - SpeedForceConfig.REALM_TRAVEL_MAX) < 1.0e-6, "capped at 30,000, got " + cap.length());
		helper.assertTrue(SpeedForceConfig.REALM_EXIT_DISTANCE * SpeedForceConfig.REALM_TRAVEL_SCALE == SpeedForceConfig.REALM_TRAVEL_MAX,
				"the exit portal at the road's end is the full 30,000");
		helper.succeed();
	}

	/** In: full health, a full meter, cooldowns reset, 100%; out: where the run took them, drained. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void intoTheSpeedForceAndOutWhereTheRunTookThem(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		ServerLevel realm = helper.getLevel().getServer().getLevel(com.projecthero.mod.speedforce.SpeedForceRealm.DIMENSION);
		if (realm == null) {
			helper.succeed(); // no datapack dimensions on this GameTest server
			return;
		}
		ServerLevel home = (ServerLevel) p.level();
		Vec3 start = p.position();
		p.setHealth(5f);
		SpeedForceSpeedster.spendMeter(p, 70f);
		SpeedForceAbilities.cooldown(p, SpeedForceAbilities.REWIND, 1200);
		helper.assertTrue(com.projecthero.mod.speedforce.SpeedForceRealm.enter(p), "into the Speed Force");
		helper.assertFalse(com.projecthero.mod.speedforce.SpeedForceRealm.enter(p), "never twice");
		helper.assertTrue(p.level() == realm, "in its dimension");
		helper.assertTrue(realm.getBlockState(BlockPos.containing(p.position()).below()).is(Blocks.GOLD_BLOCK)
				|| realm.getBlockState(BlockPos.containing(p.position()).below()).is(Blocks.OCHRE_FROGLIGHT), "on the glowing road");
		helper.assertTrue(p.getHealth() >= p.getMaxHealth() - 1.0e-3f, "full health");
		helper.assertTrue(SpeedForceSpeedster.meter(p) >= SpeedForceConfig.METER_MAX - 0.01f, "the Speed Force full");
		helper.assertTrue(SpeedForceSpeedster.cooldownRemaining(p, SpeedForceAbilities.REWIND) == 0, "every cooldown reset");
		ticks(p, 2);
		helper.assertTrue(SpeedForceSpeedster.momentum(p) >= 0.999f, "momentum held at 100%");
		// 5 blocks up the road (facing south when they went in): 500 blocks south
		Vec3 up = p.position().add(5.0, 0, 0);
		p.teleportTo(realm, up.x, up.y, up.z, p.getYRot(), p.getXRot());
		helper.assertTrue(com.projecthero.mod.speedforce.SpeedForceRealm.exit(p), "out");
		helper.assertTrue(p.level() == home, "back in the dimension they came from");
		Vec3 off = p.position().subtract(start);
		helper.assertTrue(Math.abs(off.z - 500.0) < 30.0 && Math.abs(off.x) < 30.0, "500 blocks south of where they went in, got " + off);
		helper.assertTrue(SpeedForceSpeedster.drained(p) == SpeedForceConfig.DRAINED_TICKS, "drained for 2 minutes");
		helper.assertTrue(SpeedForceSpeedster.cooldownRemaining(p, SpeedForceAbilities.PORTAL) > 0, "the portal on its cooldown");
		helper.assertTrue(p.getAttachedOrElse(com.projecthero.mod.speedforce.SpeedForceAttachments.TRIP, null) == null, "the trip is over");
		helper.succeed();
	}

	/** v0.17.1 (user): "they cant gain speed force energy, they are 50% slower and 50% weaker aswell, this debuff lasts 2 minutes". */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theDrainAfterTheSpeedForce(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 1.5);
		Zombie z = zombie(helper, 2.5, 2.0, 3.0, 100);
		helper.assertTrue(SpeedForceConfig.DRAINED_TICKS == 2 * 60 * 20, "2 minutes");
		p.setSprinting(true);
		ticks(p, 1);
		double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		double damage = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		SpeedForceSpeedster.drain(p, SpeedForceConfig.DRAINED_TICKS);
		helper.assertTrue(SpeedForceSpeedster.drained(p) == SpeedForceConfig.DRAINED_TICKS, "the timer");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed * 0.5) < 1.0e-4, "50% slower");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - damage * 0.5) < 1.0e-4, "50% weaker fists");
		helper.assertTrue(Math.abs(SpeedForceSpeedster.damageScale(p) - 0.5f) < 1.0e-6, "50% weaker moves");
		SpeedForceAbilities.rapidAssault(p);
		float dealt = 100f - z.getHealth();
		helper.assertTrue(dealt >= 14.5f && dealt <= 15.5f, "Rapid Assault x1.5, halved: 15, got " + dealt);
		SpeedForceSpeedster.spendMeter(p, SpeedForceSpeedster.meter(p) - 50f);
		SpeedForceSpeedster.addMeter(p, 20f);
		helper.assertTrue(Math.abs(SpeedForceSpeedster.meter(p) - 50f) < 0.01f, "no Speed Force gain");
		SpeedForceSpeedster.resetRun(p);
		for (int i = 0; i < 20; i++) {
			SpeedForceSpeedster.stepRun(p, new Vec3(3.0, 0, 0)); // 60 blocks run: 3 points, normally
		}
		helper.assertTrue(Math.abs(SpeedForceSpeedster.meter(p) - 50f) < 0.01f, "not even from running");
		// it lasts through a logout (saved in the state's flags)
		var saved = com.projecthero.mod.speedforce.data.SpeedForceState.CODEC
				.encodeStart(com.mojang.serialization.JavaOps.INSTANCE, SpeedForceSpeedster.peek(p)).getOrThrow();
		var back = com.projecthero.mod.speedforce.data.SpeedForceState.CODEC.parse(com.mojang.serialization.JavaOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(back.drained == SpeedForceConfig.DRAINED_TICKS && back.colour == SpeedForceSpeedster.peek(p).colour
				&& back.speedMode == SpeedForceSpeedster.peek(p).speedMode, "saved and loaded with the colour and Speed Mode intact");
		SpeedForceSpeedster.drain(p, 0);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 1.0e-4, "over: full speed again");
		helper.succeed();
	}

	/** v0.17.1 (user): "when they first get the power they get a random colour". */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theAwakeningPicksARandomLightningColour(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		java.util.Set<Integer> seen = new java.util.HashSet<>();
		for (int i = 0; i < 40; i++) {
			helper.assertTrue(SpeedForceSpeedster.grant(p), "granted");
			int c = SpeedForceSpeedster.peek(p).colour;
			helper.assertTrue(c >= 0 && c < com.projecthero.mod.speedforce.SpeedForceColours.COUNT, "one of the ten, got " + c);
			seen.add(c);
			SpeedForceSpeedster.revoke(p);
		}
		helper.assertTrue(seen.size() >= 3, "random, not always gold: saw " + seen);
		helper.succeed();
	}

	/**
	 * v0.17.1 (user: "they slow down slightly when running up blocks with the step assist"): a speedster's move up a staircase
	 * covers its full distance (vanilla's step-up climbs one step a move and then zeroes the speed against the next).
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void speedstersKeepTheirSpeedUpSteps(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			helper.setBlock(new BlockPos(x, 1, 2), Blocks.STONE);
		}
		// a staircase: one block up every block from x = 3
		for (int x = 3; x <= 6; x++) {
			for (int y = 2; y <= 2 + (x - 3); y++) {
				helper.setBlock(new BlockPos(x, y, 2), Blocks.STONE);
			}
		}
		ServerPlayer p = flash(helper, 1.5, 2.0, 2.5);
		p.setSprinting(true);
		ticks(p, 1); // Speed Mode: the 6-block step assist
		helper.assertTrue(p.maxUpStep() >= 5.99f, "the step assist is on");
		Vec3 start = p.position();
		p.setOnGround(true);
		p.setDeltaMovement(5.0, 0, 0);
		p.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(5.0, -0.08, 0));
		double ran = p.getX() - start.x;
		helper.assertTrue(ran >= 4.99, "the whole 5 blocks up four steps in one move, got " + ran);
		helper.assertTrue(p.getY() - start.y >= 3.99, "up to the top step, got " + (p.getY() - start.y));
		helper.assertFalse(p.horizontalCollision, "no collision: nothing zeroes the speed");
		helper.assertTrue(p.getDeltaMovement().x > 4.9, "the velocity is kept, got " + p.getDeltaMovement());
		// a real wall (taller than the step assist) still stops them
		ServerPlayer q = flash(helper, 1.5, 2.0, 5.5);
		for (int y = 2; y <= 9; y++) {
			helper.setBlock(new BlockPos(3, y, 5), Blocks.STONE);
		}
		helper.setBlock(new BlockPos(1, 1, 5), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 1, 5), Blocks.STONE);
		q.setSprinting(true);
		ticks(q, 1);
		q.setOnGround(true);
		double qx = q.getX();
		q.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(5.0, -0.08, 0));
		helper.assertTrue(q.getX() - qx < 1.5, "a wall taller than the step assist stops them, moved " + (q.getX() - qx));
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
