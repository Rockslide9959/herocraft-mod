package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.gyro.Gyro;
import com.projecthero.mod.gyro.GyroAbilityManager;
import com.projecthero.mod.gyro.GyroConfig;
import com.projecthero.mod.gyro.GyroDamage;
import com.projecthero.mod.gyro.GyroDisguise;
import com.projecthero.mod.gyro.GyroItems;
import com.projecthero.mod.gyro.GyroLearning;
import com.projecthero.mod.gyro.GyroMarks;
import com.projecthero.mod.gyro.GyroMoves;
import com.projecthero.mod.gyro.GyroValkyrie;
import com.projecthero.mod.gyro.data.GyroState;
import com.projecthero.mod.gyro.entity.SteelBallEntity;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Gyro Zeppeli (the Spin): the item and the grant, the stats, the Spin bar, every move's damage and cost, the balls going out
 * and coming back (and the moves greyed meanwhile), the Golden Rectangle's Perfect Spin, Ball Breaker needing one, Valkyrie, Healing
 * Spin, Face Spin and squad safety. Mock players are not ticked by the server, so tests over time drive {@link Gyro#tick} themselves
 * (one onEachTick per test, registered at the start; every check is a runAfterDelay registered at the start too). Mob hits are
 * asserted as ratios of the expected damage.
 */
public class GyroGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double z) {
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

	private static ServerPlayer gyro(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 1.5);
		helper.assertTrue(Gyro.grant(p), "the grant works");
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<GyroState> change) {
		GyroState n = Gyro.state(p).copy();
		change.accept(n);
		p.setAttached(ModAttachments.GYRO_STATE, n);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z, double maxHealth) {
		Zombie zb = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(x, 2.0, z));
		zb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		zb.setHealth((float) maxHealth);
		return zb;
	}

	private static void look(ServerPlayer p, LivingEntity at) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, at.position().add(0, at.getBbHeight() * 0.55, 0));
	}

	/** Lost health as a share of what was expected (1.0 = exactly). */
	private static float ratio(float before, LivingEntity e, float expected) {
		return (before - e.getHealth()) / expected;
	}

	private static boolean near(float ratio) {
		return ratio > 0.85f && ratio < 1.15f;
	}

	/** Gyro.tick for {@code p} once a game tick for the whole test (registered once, at the start). */
	private static void ticker(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> Gyro.tick(p));
	}

	// ---------------------------------------------------------------- the power

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void steelBallsTeachTheSpinAsTheOnePrimary(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 1.5);
		com.projecthero.mod.kryptonian.Kryptonian.grant(p);
		helper.assertTrue(GyroLearning.item() == GyroItems.ZEPPELI_STEEL_BALLS, "the right-click hook finds ART's item by id");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GyroItems.ZEPPELI_STEEL_BALLS));
		helper.assertTrue(GyroLearning.learn(p, InteractionHand.MAIN_HAND), "right-click learns the Spin");
		helper.assertTrue(Gyro.hasPower(p) && HeroTiers.holdsHero(p, Gyro.KEY), "Gyro is a Hero-Tier power");
		helper.assertTrue(p.getMainHandItem().isEmpty(), "the balls are spent");
		helper.assertTrue(!com.projecthero.mod.kryptonian.Kryptonian.hasPower(p), "it replaced the Kryptonian (one Primary)");
		helper.assertTrue(!PowerGrants.missingHeroTiers(p).contains(Gyro.KEY) && PowerGrants.NOT_FROM_SERUMS.contains(Gyro.KEY),
				"random serums never give it");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GyroItems.ZEPPELI_STEEL_BALLS));
		helper.assertTrue(!GyroLearning.learn(p, InteractionHand.MAIN_HAND) && !p.getMainHandItem().isEmpty(), "a second set does nothing");
		helper.assertTrue(Gyro.spin(p) == GyroConfig.SPIN_MAX && Gyro.ballsReady(p) == 2, "a full bar and both balls to start");
		Gyro.revoke(p);
		helper.assertTrue(!Gyro.hasPower(p), "revoked");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void statsAndDamageResistance(GameTestHelper helper) {
		ServerPlayer p = gyro(helper);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 36f) < 0.01f, "+8 hearts (36), got " + p.getMaxHealth());
		double speedBase = p.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - speedBase * 1.15) < 1.0e-4, "+15% speed");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (p.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() + 4)) < 1.0e-4,
				"+4 melee");
		p.setHealth(36f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), 10f);
		helper.assertTrue(Math.abs(p.getHealth() - (36f - 8.5f)) < 0.05f, "15% off a hit (10 -> 8.5), health " + p.getHealth());
		Gyro.revoke(p);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20f) < 0.01f, "the hearts go with the power");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void spinBarRefillsFourASecond(GameTestHelper helper) {
		ServerPlayer p = gyro(helper);
		Gyro.setSpin(p, 10f);
		float[] last = { -1f };
		boolean[] ok = { true };
		helper.onEachTick(() -> {
			Gyro.tick(p);
			float now = Gyro.spin(p);
			if (last[0] >= 0f && now < GyroConfig.SPIN_MAX && Math.abs(now - last[0] - GyroConfig.SPIN_REGEN_PER_TICK) > 1.0e-3f) {
				ok[0] = false;
			}
			last[0] = now;
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(ok[0], "every tick adds exactly 0.2 Spin (4 a second)");
			helper.assertTrue(Gyro.spin(p) > 10f && Gyro.spin(p) <= GyroConfig.SPIN_MAX, "and it's filling");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the balls

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void steelBallHitsSpinsAndComesBack(GameTestHelper helper) {
		ServerPlayer p = gyro(helper);
		Zombie z = zombie(helper, 5.5, 1.5, 100);
		look(p, z);
		float before = z.getHealth();
		helper.assertTrue(GyroMoves.use(p, GyroAbilityManager.STEEL_BALL), "R throws");
		helper.assertTrue(Math.abs(Gyro.spin(p) - (100f - GyroConfig.BALL_COST)) < 0.01f, "15 Spin");
		helper.assertTrue(Gyro.ballsReady(p) == 1 && (Gyro.state(p).thrown & 1) != 0, "ball 0 is out");
		helper.assertTrue(!GyroAbilityManager.ballsAllow(p, GyroAbilityManager.TWIN_BALLS)
				&& !GyroAbilityManager.ballsAllow(p, GyroAbilityManager.SPIN_SHIELD), "two-ball moves are greyed while it's out");
		helper.assertTrue(GyroAbilityManager.ballsAllow(p, GyroAbilityManager.SCAN), "one-ball moves still have the other ball");
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(near(ratio(before, z, GyroConfig.BALL_DAMAGE)), "10 damage, lost " + (before - z.getHealth()));
			helper.assertTrue(GyroMarks.spun(z), "the target spins");
			z.setTarget(p);
			helper.assertTrue(z.getTarget() == null, "a spun mob can't aim (no target)");
		});
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(Gyro.ballsReady(p) == 2 && Gyro.state(p).thrown == 0, "the ball came back to the hand");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(SteelBallEntity.class, p.getBoundingBox().inflate(40)).isEmpty(), "no ball left out");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void twinBallsTakeBothAndGreyTheRest(GameTestHelper helper) {
		ServerPlayer p = gyro(helper);
		helper.assertTrue(GyroMoves.use(p, GyroAbilityManager.TWIN_BALLS), "Shift+R throws both");
		helper.assertTrue(Math.abs(Gyro.spin(p) - (100f - GyroConfig.TWIN_COST)) < 0.01f, "30 Spin");
		helper.assertTrue(Gyro.state(p).thrown == 3 && Gyro.ballsReady(p) == 0, "both balls are out");
		helper.assertTrue(!GyroMoves.use(p, GyroAbilityManager.STEEL_BALL), "R is refused with no ball in hand");
		helper.assertTrue(!GyroMoves.startCharge(p, GyroAbilityManager.GOLDEN_SPIN), "and so is the Golden Spin");
		helper.assertTrue(GyroAbilityManager.ballsAllow(p, GyroAbilityManager.SPIN_LEAP), "Spin Leap needs no ball");
		helper.assertTrue(Math.abs(Gyro.spin(p) - 70f) < 0.01f, "refused moves cost nothing");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Golden Rectangle

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void perfectSpinWindowAndGoldenSpinDamage(GameTestHelper helper) {
		ServerPlayer p = gyro(helper);
		int t = GyroConfig.GOLDEN_CHARGE_TICKS;
		helper.assertTrue(Gyro.isPerfect(t, t) && Gyro.isPerfect(t - GyroConfig.PERFECT_WINDOW, t) && Gyro.isPerfect(t + GyroConfig.PERFECT_WINDOW, t),
				"the window is the close +-3 ticks");
		helper.assertTrue(!Gyro.isPerfect(t - GyroConfig.PERFECT_WINDOW - 1, t) && !Gyro.isPerfect(t + GyroConfig.PERFECT_WINDOW + 1, t), "outside it isn't");
		Zombie a = zombie(helper, 4.5, 1.5, 200);
		Zombie b = zombie(helper, 6.5, 1.5, 200);
		b.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		b.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		b.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		b.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		look(p, a);
		float[] b1 = new float[1];
		// a plain release (the armour has had a tick to count)
		helper.runAfterDelay(3, () -> {
			float a0 = a.getHealth(), b0 = b.getHealth();
			helper.assertTrue(GyroMoves.startCharge(p, GyroAbilityManager.GOLDEN_SPIN), "Shift+G starts the rectangle");
			helper.assertTrue(Gyro.charging(p) == GyroState.CHARGE_GOLDEN && Gyro.chargeTarget(p) == t, "charging, 30 ticks to close");
			helper.assertTrue(GyroMoves.releaseCharge(p, GyroAbilityManager.GOLDEN_SPIN), "released at once: still fires, not Perfect");
			helper.assertTrue(GyroMoves.lastGoldenHits == 2, "the line pierces both zombies, got " + GyroMoves.lastGoldenHits);
			helper.assertTrue(near(ratio(a0, a, GyroConfig.GOLDEN_DAMAGE)), "35 to the first, lost " + (a0 - a.getHealth()));
			helper.assertTrue(b0 - b.getHealth() < GyroConfig.GOLDEN_DAMAGE * 0.9f, "armour softens a plain Golden Spin, lost " + (b0 - b.getHealth()));
			helper.assertTrue(Math.abs(Gyro.spin(p) - 50f) < 0.01f && GyroAbilityManager.cooldownRemaining(p, GyroAbilityManager.GOLDEN_SPIN) > 0,
					"50 Spin and the 12 s cooldown");
			// get ready for the Perfect one: the ball home, the bar full, no cooldown
			helper.getLevel().getEntitiesOfClass(SteelBallEntity.class, p.getBoundingBox().inflate(60)).forEach(SteelBallEntity::discard);
			Gyro.clearCooldowns(p);
			Gyro.setSpin(p, 100f);
			b1[0] = b.getHealth();
			helper.assertTrue(GyroMoves.startCharge(p, GyroAbilityManager.GOLDEN_SPIN), "charging again");
		});
		// released right on the close: a Perfect Spin, through the armour
		helper.runAfterDelay(3 + t, () -> {
			b.invulnerableTime = 0;
			helper.assertTrue(GyroMoves.releaseCharge(p, GyroAbilityManager.GOLDEN_SPIN), "released on the close");
			helper.assertTrue(near(ratio(b1[0], b, GyroConfig.GOLDEN_PERFECT_DAMAGE)), "Perfect: 70 straight through diamond, lost " + (b1[0] - b.getHealth()));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void ridingHitsHarderAndClosesTwiceAsFast(GameTestHelper helper) {
		ServerPlayer p = gyro(helper);
		Horse h = helper.spawnWithNoFreeWill(EntityType.HORSE, new Vec3(1.5, 2.0, 1.5));
		p.startRiding(h, true);
		helper.assertTrue(Gyro.riding(p), "on a horse");
		helper.assertTrue(Gyro.chargeTicksFor(p) == GyroConfig.GOLDEN_CHARGE_TICKS / 2, "the rectangle closes twice as fast");
		Zombie z = zombie(helper, 1.5, 4.5, 100);
		float before = z.getHealth();
		helper.assertTrue(Gyro.hit(p, z, 10f, false), "a Spin hit lands");
		helper.assertTrue(near(ratio(before, z, 13f)), "+30% riding (13), lost " + (before - z.getHealth()));
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void ballBreakerNeedsAPerfectSpin(GameTestHelper helper) {
		ServerPlayer p = gyro(helper);
		Zombie z = zombie(helper, 5.5, 1.5, 200);
		look(p, z);
		int t = GyroConfig.GOLDEN_CHARGE_TICKS;
		// not Perfect (let go at once): it fails, 20 Spin, no cooldown
		helper.assertTrue(GyroMoves.startCharge(p, GyroAbilityManager.BALL_BREAKER), "Shift+Z starts the rectangle (full bar)");
		helper.assertTrue(!GyroMoves.releaseCharge(p, GyroAbilityManager.BALL_BREAKER), "not Perfect: Ball Breaker won't come out");
		helper.assertTrue(!GyroMoves.strikePending(p) && Math.abs(Gyro.spin(p) - 80f) < 0.01f, "the miss costs 20");
		helper.assertTrue(GyroAbilityManager.cooldownRemaining(p, GyroAbilityManager.BALL_BREAKER) == 0, "and no cooldown");
		helper.assertTrue(!GyroMoves.startCharge(p, GyroAbilityManager.BALL_BREAKER), "under 100 Spin it can't even start");
		// Perfect: charge now, let go on the close
		Gyro.setSpin(p, 100f);
		helper.assertTrue(GyroMoves.startCharge(p, GyroAbilityManager.BALL_BREAKER), "again at 100");
		float[] before = new float[1];
		ticker(helper, p);
		helper.runAfterDelay(t, () -> {
			look(p, z); // (it may have settled onto the floor meanwhile)
			helper.assertTrue(GyroMoves.aimed(p, GyroConfig.BREAKER_RANGE) == z, "he's aiming at the zombie");
			helper.assertTrue(GyroMoves.releaseCharge(p, GyroAbilityManager.BALL_BREAKER), "Perfect: Ball Breaker bursts out (" + GyroMoves.lastRefusal + ")");
			helper.assertTrue(GyroMoves.strikePending(p) && Gyro.spin(p) < 0.01f, "100 Spin, the figure on its way");
			helper.assertTrue(GyroAbilityManager.cooldownRemaining(p, GyroAbilityManager.BALL_BREAKER) > GyroConfig.BREAKER_COOLDOWN - 5, "3 min cooldown");
			before[0] = z.getHealth();
		});
		helper.runAfterDelay(t + GyroConfig.BREAKER_STRIKE_DELAY + 4, () -> {
			helper.assertTrue(!GyroMoves.strikePending(p), "it struck");
			helper.assertTrue(near(ratio(before[0], z, GyroConfig.BREAKER_DAMAGE)), "60 damage, lost " + (before[0] - z.getHealth()));
			helper.assertTrue(GyroMarks.aged(z) && z.hasEffect(MobEffects.WITHER) && z.hasEffect(MobEffects.WEAKNESS)
					&& z.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 2, "aged: Weakness II, Slowness III, Wither");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the other moves

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void touchQuakeScanLeapCostsAndDamage(GameTestHelper helper) {
		ServerPlayer p = gyro(helper);
		Zombie near = zombie(helper, 1.5, 3.5, 100);
		look(p, near);
		float n0 = near.getHealth();
		helper.assertTrue(GyroMoves.use(p, GyroAbilityManager.SPIN_TOUCH), "G touches");
		helper.assertTrue(near(ratio(n0, near, GyroConfig.TOUCH_DAMAGE)) && GyroMarks.locked(near), "6 damage and its joints lock");
		helper.assertTrue(Math.abs(Gyro.spin(p) - 80f) < 0.01f, "20 Spin");
		Gyro.setSpin(p, 100f);
		float q0 = near.getHealth();
		near.invulnerableTime = 0;
		helper.assertTrue(GyroMoves.use(p, GyroAbilityManager.SPIN_QUAKE), "C quakes");
		helper.assertTrue(near(ratio(q0, near, GyroConfig.QUAKE_DAMAGE)) && near.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "14 damage, slowed");
		helper.assertTrue(Math.abs(Gyro.spin(p) - 75f) < 0.01f && Gyro.ballsReady(p) == 1, "25 Spin; the ball is busy a moment");
		Gyro.setSpin(p, 100f);
		edit(p, s -> { s.busy0 = 0L; s.busy1 = 0L; });
		helper.assertTrue(GyroMoves.use(p, GyroAbilityManager.SCAN), "Z scans");
		helper.assertTrue(GyroMoves.lastScan >= 1 && Math.abs(Gyro.spin(p) - 85f) < 0.01f, "it found the zombie; 15 Spin");
		helper.assertTrue(Gyro.ballsReady(p) == 1, "the ball spins on the ground meanwhile");
		Gyro.setSpin(p, 100f);
		p.setYRot(0f);
		p.setXRot(0f);
		helper.assertTrue(GyroMoves.use(p, GyroAbilityManager.SPIN_LEAP), "X leaps");
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(Math.abs(v.y - GyroConfig.LEAP_UP) < 1.0e-3 && Math.abs(Math.hypot(v.x, v.z) - GyroConfig.LEAP_FORWARD) < 1.0e-3,
				"forward and up");
		helper.assertTrue(Math.abs(Gyro.spin(p) - 90f) < 0.01f, "10 Spin");
		helper.assertTrue(!GyroDamage.allowDamage(p, p.damageSources().fall(), 10f), "no fall damage after the leap");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void spinShieldDeflectsArrows(GameTestHelper helper) {
		ServerPlayer p = gyro(helper);
		helper.assertTrue(GyroMoves.use(p, GyroAbilityManager.SPIN_SHIELD), "Shift+C");
		helper.assertTrue(Gyro.shielding(p) && Gyro.ballsReady(p) == 0 && Math.abs(Gyro.spin(p) - 70f) < 0.01f, "both balls orbit; 30 Spin");
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.moveTo(p.getX() + 2.0, p.getY() + 1.0, p.getZ());
		arrow.setDeltaMovement(-1.5, 0, 0);
		helper.getLevel().addFreshEntity(arrow);
		int before = GyroMoves.deflected;
		Gyro.tick(p);
		helper.assertTrue(GyroMoves.deflected == before + 1 && arrow.getDeltaMovement().x > 0 && arrow.getOwner() == p,
				"the arrow is knocked back out, and it's his now");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void valkyrieComesAndHasADeathCooldown(GameTestHelper helper) {
		ServerPlayer p = gyro(helper);
		helper.assertTrue(GyroMoves.use(p, GyroAbilityManager.VALKYRIE), "Shift+X whistles");
		AbstractHorse h = GyroValkyrie.find(p);
		helper.assertTrue(h != null, "Valkyrie is in the world (state " + Gyro.state(p).valkyrie + ")");
		helper.assertTrue(h.isTamed() && p.getUUID().equals(h.getOwnerUUID()) && GyroValkyrie.isValkyrie(h), "Valkyrie, tamed to him: tamed "
				+ h.isTamed() + " owner " + h.getOwnerUUID() + " tags " + h.getTags());
		helper.assertTrue(h.isSaddled() && h.getBodyArmorItem().is(Items.DIAMOND_HORSE_ARMOR), "saddled and armoured");
		helper.assertTrue(Math.abs(h.getAttributeValue(Attributes.MOVEMENT_SPEED) - GyroConfig.VALKYRIE_SPEED) < 1.0e-4, "very fast");
		helper.assertTrue(GyroValkyrie.galloping(p), "she gallops in");
		ticker(helper, p);
		helper.runAfterDelay(GyroConfig.VALKYRIE_GALLOP_TICKS + 5, () -> {
			helper.assertTrue(p.getVehicle() == h, "he's in the saddle");
			p.stopRiding();
			h.kill();
		});
		helper.runAfterDelay(GyroConfig.VALKYRIE_GALLOP_TICKS + 8, () -> {
			helper.assertTrue(Gyro.valkyrieCooldown(p) > GyroConfig.VALKYRIE_DEAD_COOLDOWN - 20, "her death starts the 60 s cooldown");
			helper.assertTrue(!GyroMoves.use(p, GyroAbilityManager.VALKYRIE), "no whistling her back yet");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void healingSpinHealsSelfAndSquadmate(GameTestHelper helper) {
		ServerPlayer p = gyro(helper);
		ServerPlayer mate = mock(helper, 1.5, 4.5);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("GyroHeal" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		p.setHealth(10f);
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200));
		p.setXRot(90f); // looking at the floor: nobody to heal but himself
		helper.assertTrue(GyroMoves.use(p, GyroAbilityManager.HEALING_SPIN), "V heals");
		helper.assertTrue(!p.hasEffect(MobEffects.POISON), "poison cleared");
		helper.assertTrue(Math.abs(Gyro.spin(p) - 75f) < 0.01f && GyroAbilityManager.cooldownRemaining(p, GyroAbilityManager.HEALING_SPIN) > 0,
				"25 Spin, 15 s cooldown");
		mate.setHealth(6f);
		look(p, mate);
		helper.assertTrue(GyroMoves.healTarget(p) == mate, "aimed at a squadmate: the heal goes to them");
		helper.runAfterDelay(GyroConfig.HEAL_TICKS + 5, () -> {
			helper.assertTrue(Math.abs(p.getHealth() - (10f + GyroConfig.HEAL_TOTAL)) < 0.6f, "6 hearts over 3 s, health " + p.getHealth());
			squads.disband(squad);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void squadmatesAndTheirPetsAreNeverHurt(GameTestHelper helper) {
		ServerPlayer p = gyro(helper);
		ServerPlayer mate = mock(helper, 3.5, 1.5);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("GyroSafe" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		try {
			Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, new Vec3(1.5, 2.0, 3.5));
			wolf.tame(mate);
			Zombie z = zombie(helper, 3.5, 3.5, 100);
			float m0 = mate.getHealth(), w0 = wolf.getHealth(), z0 = z.getHealth();
			helper.assertTrue(!Gyro.hit(p, mate, 20f, true) && !Gyro.hit(p, wolf, 20f, false), "a Spin hit never lands on a squadmate or their pet");
			helper.assertTrue(GyroMoves.use(p, GyroAbilityManager.SPIN_QUAKE), "C quakes with them all in range");
			helper.assertTrue(mate.getHealth() == m0 && wolf.getHealth() == w0, "the squadmate and the wolf are untouched");
			helper.assertTrue(near(ratio(z0, z, GyroConfig.QUAKE_DAMAGE)), "the zombie isn't");
		} finally {
			squads.disband(squad);
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void faceSpinHidesHimFromMobsThatHaventSeenHimAttack(GameTestHelper helper) {
		ServerPlayer p = gyro(helper);
		Zombie z = zombie(helper, 5.5, 1.5, 100);
		z.setTarget(p);
		helper.assertTrue(z.getTarget() == p, "hunting him");
		helper.assertTrue(GyroMoves.use(p, GyroAbilityManager.FACE_SPIN), "Shift+V");
		helper.assertTrue(Gyro.disguised(p) && Math.abs(Gyro.spin(p) - 80f) < 0.01f, "disguised, 20 Spin");
		helper.assertTrue(z.getTarget() == null, "the zombie lost him");
		z.setTarget(p);
		helper.assertTrue(z.getTarget() == null, "and can't take him as a target");
		z.invulnerableTime = 0;
		z.hurt(p.damageSources().playerAttack(p), 2f);
		helper.assertTrue(GyroDisguise.isWitness(p, z), "struck: it saw him attack");
		z.setTarget(p);
		helper.assertTrue(z.getTarget() == p, "a witness may come for him");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void keysRouteToTheSpinAndHAndNDoNothing(GameTestHelper helper) {
		ServerPlayer p = gyro(helper);
		helper.assertTrue(GyroAbilityManager.moveFor(AbilitySlot.SLOT_3, false).equals(GyroAbilityManager.SPIN_LEAP)
				&& GyroAbilityManager.moveFor(AbilitySlot.SLOT_3, true).equals(GyroAbilityManager.VALKYRIE), "X = Spin Leap, Shift+X = Valkyrie");
		helper.assertTrue(GyroAbilityManager.moveFor(AbilitySlot.SLOT_7, false) == null && GyroAbilityManager.moveFor(AbilitySlot.SLOT_8, true) == null,
				"no H or N ability");
		p.setShiftKeyDown(true);
		GyroAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(Gyro.charging(p) == GyroState.CHARGE_GOLDEN, "Shift+G held charges");
		p.setShiftKeyDown(false);
		GyroAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
		helper.assertTrue(Gyro.charging(p) == GyroState.CHARGE_NONE, "letting go fires (even after letting go of Shift)");
		helper.succeed();
	}
}
