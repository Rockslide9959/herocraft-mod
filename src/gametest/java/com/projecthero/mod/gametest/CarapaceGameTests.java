package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousKits;
import com.projecthero.mod.miraculous.carapace.Carapace;
import com.projecthero.mod.miraculous.carapace.CarapaceConfig;
import com.projecthero.mod.miraculous.carapace.CarapaceMoves;
import com.projecthero.mod.miraculous.carapace.entity.ShieldThrowEntity;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.punisher.PunisherControl;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Carapace (Wayzz): one test per move -- the server effect and its number -- and the clean-up on
 * detransformation. Mock players are not ticked by the server, so tests that run over time drive the Miraculous tick
 * themselves ({@link #pump}); the mock player faces +Z.
 */
public class CarapaceGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	// ---------------------------------------------------------------- helpers

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

	/** Carapace facing +Z, the transformation finished, every move ready. */
	private static ServerPlayer carapace(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(Miraculous.grant(p, Kwami.WAYZZ), "Wayzz is granted");
		Miraculous.transform(p);
		ready(helper, p);
		helper.assertTrue(Miraculous.canAct(p), "the transformation has finished");
		return p;
	}

	private static void ready(GameTestHelper helper, ServerPlayer p) {
		long now = helper.getLevel().getGameTime();
		Miraculous.edit(p, n -> {
			n.formChangeAt = now - 200L;
			n.animId = MiraculousState.ANIM_NONE;
			n.abilityReadyAt.clear();
		});
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Miraculous.tick(p);
		});
	}

	private static void floor(GameTestHelper helper, int size) {
		for (int x = 0; x <= size; x++) {
			for (int z = 0; z <= size; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z, double health, boolean ai) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(!ai);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		if (m.getAttribute(Attributes.ARMOR) != null) {
			m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		}
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static float incoming(ServerPlayer p, net.minecraft.world.damagesource.DamageSource src, float amount) {
		return MiraculousKits.of(Kwami.WAYZZ).modifyIncomingDamage(p, src, amount);
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE)
	public void shieldBashHitsForTwelveWithKnockback(GameTestHelper helper) {
		floor(helper, 7);
		ServerPlayer p = carapace(helper, 3.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 3.9, 100, false);
		helper.assertTrue(CarapaceMoves.bash(p) == z, "the bash meets the zombie in front");
		helper.assertTrue(Math.abs((100f - z.getHealth()) - CarapaceConfig.BASH_DAMAGE) < EPS, "12 damage, took " + (100f - z.getHealth()));
		helper.assertTrue(z.getDeltaMovement().z > 0.3, "thrown back, vz " + z.getDeltaMovement().z);
		helper.assertTrue(!Miraculous.ready(p, CarapaceConfig.BASH), "the bash is on its cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void shieldThrowRicochetsBetweenThreeForTenEach(GameTestHelper helper) {
		floor(helper, 9);
		ServerPlayer p = carapace(helper, 1.5, 2.0, 0.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 1.5, 2.0, 4.5, 100, false);
		Zombie b = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.5, 100, false);
		Zombie c = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 1.5, 100, false);
		Zombie d = mob(helper, EntityType.ZOMBIE, 7.0, 2.0, 0.5, 100, false);
		pump(helper, p);
		ShieldThrowEntity e = CarapaceMoves.throwShield(p);
		helper.assertTrue(e != null, "the shield flies");
		helper.assertTrue(Carapace.shieldOut(p), "the shield is out");
		helper.assertTrue(CarapaceMoves.throwShield(p) == null, "a second throw is refused while it is out");
		helper.succeedWhen(() -> {
			helper.assertTrue(!Carapace.shieldOut(p), "the shield is back on his arm");
			for (Zombie z : new Zombie[] { a, b, c }) {
				helper.assertTrue(Math.abs((100f - z.getHealth()) - CarapaceConfig.THROW_DAMAGE) < EPS, "10 to each of three, took " + (100f - z.getHealth()));
			}
			helper.assertTrue(d.getHealth() >= 100f, "only three bounces, d took " + (100f - d.getHealth()) + " src " + d.getLastDamageSource() + " at " + d.position().subtract(helper.absoluteVec(Vec3.ZERO)) + " hits " + e.hits() + " a " + a.getHealth() + " b " + b.getHealth() + " c " + c.getHealth());
			helper.assertTrue(!Miraculous.ready(p, CarapaceConfig.THROW), "the throw's cooldown starts on the catch");
		});
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE)
	public void shieldSlamHitsWithinFiveForTenAndStuns(GameTestHelper helper) {
		floor(helper, 9);
		ServerPlayer p = carapace(helper, 4.5, 2.0, 4.5);
		Zombie near = mob(helper, EntityType.ZOMBIE, 6.5, 2.0, 4.5, 100, false);
		Zombie side = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 1.0, 100, false);
		Zombie far = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 10.5, 100, false);
		helper.assertTrue(CarapaceMoves.slam(p) == 2, "two within 5 blocks");
		helper.assertTrue(Math.abs((100f - near.getHealth()) - CarapaceConfig.SLAM_DAMAGE) < EPS, "10 damage, took " + (100f - near.getHealth()));
		helper.assertTrue(Math.abs((100f - side.getHealth()) - CarapaceConfig.SLAM_DAMAGE) < EPS, "10 damage behind too");
		helper.assertTrue(PunisherControl.isStunned(near) && PunisherControl.isStunned(side), "both stunned");
		helper.assertTrue(far.getHealth() >= 100f && !PunisherControl.isStunned(far), "the one 6 blocks out is untouched");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void tauntDrawsTheMobsAndCutsDamageByThirtyPercent(GameTestHelper helper) {
		floor(helper, 7);
		ServerPlayer p = carapace(helper, 3.5, 2.0, 3.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 6.5, 2.0, 6.5, 100, true);
		z.setTarget(null);
		helper.assertTrue(CarapaceMoves.taunt(p) >= 1, "the zombie is taunted");
		helper.assertTrue(z.getTarget() == p, "it comes for him");
		helper.assertTrue(Carapace.running(p, Carapace.V_TAUNT), "the taunt runs");
		float got = incoming(p, p.damageSources().mobAttack(z), 10f);
		helper.assertTrue(Math.abs(got - 7f) < 0.01f, "30% less damage: 10 -> " + got);
		helper.succeed();
	}

	// ---------------------------------------------------------------- X

	@GameTest(template = EMPTY_STRUCTURE)
	public void shieldSurfTogglesWithStepHeight(GameTestHelper helper) {
		ServerPlayer p = carapace(helper, 1.5, 2.0, 1.5);
		double step = p.getAttributeValue(Attributes.STEP_HEIGHT);
		helper.assertTrue(CarapaceMoves.surf(p), "surf on");
		helper.assertTrue(Carapace.surfing(p), "riding the shield");
		helper.assertTrue(p.getAttributeValue(Attributes.STEP_HEIGHT) > step + 0.5, "steps up blocks");
		helper.assertTrue(!CarapaceMoves.surf(p), "surf off");
		helper.assertTrue(!Carapace.surfing(p) && Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - step) < 1.0e-6, "back to normal");
		helper.assertTrue(!Miraculous.ready(p, CarapaceConfig.SURF), "its short cooldown starts when it ends");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shellRollTakesNoDamage(GameTestHelper helper) {
		ServerPlayer p = carapace(helper, 1.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 1.5, 2.0, 3.0, 20, false);
		CarapaceMoves.roll(p);
		helper.assertTrue(Carapace.rolling(p), "rolling");
		helper.assertTrue(incoming(p, p.damageSources().mobAttack(z), 10f) == 0f, "no damage while rolling");
		helper.assertTrue(incoming(p, p.damageSources().magic(), 10f) == 0f, "none at all");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void shellGuardBlocksTheFrontAndReflectsArrows(GameTestHelper helper) {
		floor(helper, 7);
		ServerPlayer p = carapace(helper, 3.5, 2.0, 2.5);
		Zombie front = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 4.0, 20, false);
		Zombie back = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 1.0, 20, false);
		MiraculousKits.of(Kwami.WAYZZ).handle(p, AbilitySlot.SLOT_4, true, false);
		helper.assertTrue(Carapace.guarding(p), "the guard is up");
		helper.assertTrue(incoming(p, p.damageSources().mobAttack(front), 10f) == 0f, "a hit from the front is blocked");
		helper.assertTrue(incoming(p, p.damageSources().mobAttack(back), 10f) == 10f, "a hit from behind is not");
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.setOwner(front);
		Vec3 at = p.position().add(0, 1.3, 2.2);
		arrow.moveTo(at.x, at.y, at.z, 0f, 0f);
		arrow.setDeltaMovement(0, 0, -1.0);
		helper.getLevel().addFreshEntity(arrow);
		pump(helper, p);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(arrow.getOwner() == p, "the arrow is his now");
			helper.assertTrue(arrow.isRemoved() || arrow.getDeltaMovement().z > 0.0, "sent back the way it came, vz " + arrow.getDeltaMovement().z);
			MiraculousKits.of(Kwami.WAYZZ).handle(p, AbilitySlot.SLOT_4, false, false);
			helper.assertTrue(!Carapace.guarding(p), "released: the guard drops");
			helper.assertTrue(!Miraculous.ready(p, CarapaceConfig.GUARD), "its cooldown starts on release");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void shelterPushesMobsOutStartsTheCountdownAndHeals(GameTestHelper helper) {
		floor(helper, 15);
		ServerPlayer p = carapace(helper, 7.5, 2.0, 7.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 9.5, 2.0, 7.5, 100, false);
		helper.assertTrue(CarapaceMoves.shelter(p), "the dome rises");
		helper.assertTrue(Miraculous.countdownLeft(p) > 0, "the countdown started");
		helper.assertTrue(!CarapaceMoves.shelter(p), "only one at a time");
		Vec3 c = Miraculous.vec(Miraculous.state(p), Carapace.X_SHELTER);
		helper.assertTrue(c != null, "the dome's centre is synced");
		pump(helper, p);
		helper.runAfterDelay(25, () -> {
			double d = z.position().add(0, z.getBbHeight() * 0.5, 0).distanceTo(c);
			helper.assertTrue(d >= CarapaceConfig.SHELTER_RADIUS, "the zombie is outside the dome, " + d);
			MobEffectInstance regen = p.getEffect(MobEffects.REGENERATION);
			helper.assertTrue(regen != null && regen.getAmplifier() >= CarapaceConfig.SHELTER_REGEN_AMP, "he regenerates inside");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- V

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void shellBarrierStopsMobsAndArrows(GameTestHelper helper) {
		floor(helper, 9);
		ServerPlayer p = carapace(helper, 4.5, 2.0, 1.5);
		CarapaceMoves.barrier(p);
		Vec3 c = Miraculous.vec(Miraculous.state(p), Carapace.X_BARRIER);
		Vec3 dir = Miraculous.vec(Miraculous.state(p), Carapace.X_BARRIER_DIR);
		helper.assertTrue(c != null && dir != null && Carapace.running(p, Carapace.V_BARRIER), "the wall stands");
		helper.assertTrue(Math.abs(c.z - p.getZ() - CarapaceConfig.BARRIER_DISTANCE) < 0.01, "3 blocks ahead");
		// a zombie that came from the far side, now in the wall
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.6, 100, false);
		z.xo = z.getX();
		z.yo = z.getY();
		z.zo = z.getZ() + 1.0;
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.setOwner(z);
		Vec3 at = c.add(0, 1.5, 1.2);
		arrow.moveTo(at.x, at.y, at.z, 0f, 0f);
		arrow.setDeltaMovement(0, 0, -1.5);
		helper.getLevel().addFreshEntity(arrow);
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			double depth = (z.getZ() - c.z);
			helper.assertTrue(depth > 0.3, "the zombie is pushed back to its own side, " + depth);
			helper.assertTrue(arrow.isRemoved(), "the arrow stopped at the wall");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void protectGivesTenHeartsOfAbsorption(GameTestHelper helper) {
		floor(helper, 7);
		ServerPlayer p = carapace(helper, 3.5, 2.0, 1.5);
		helper.assertTrue(CarapaceMoves.protect(p) == p, "nobody looked at: on himself");
		helper.assertTrue(p.getAbsorptionAmount() >= CarapaceConfig.PROTECT_ABSORB - 0.01f, "10 hearts, " + p.getAbsorptionAmount());
		helper.assertTrue(Carapace.running(p, Carapace.V_PROTECT), "the bubble is synced");
		ServerPlayer friend = mock(helper, 3.5, 2.0, 5.5);
		ready(helper, p);
		p.setXRot(10f);
		helper.assertTrue(CarapaceMoves.protect(p) == friend, "on the player he looks at");
		helper.assertTrue(friend.getAbsorptionAmount() >= CarapaceConfig.PROTECT_ABSORB - 0.01f, "the friend has 10 hearts");
		helper.assertTrue(Carapace.val(p, Carapace.V_PROTECT_ID) == friend.getId() + 1L, "the bubble follows the friend");
		helper.succeed();
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE)
	public void steadfastResistanceTwoAndNoKnockback(GameTestHelper helper) {
		ServerPlayer p = carapace(helper, 1.5, 2.0, 1.5);
		CarapaceMoves.steadfast(p);
		MobEffectInstance res = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
		helper.assertTrue(res != null && res.getAmplifier() == 1 && res.getDuration() >= CarapaceConfig.STEADFAST_TICKS - 1, "Resistance II for 8 s");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "knockback immune");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void tidalSwimBreathesAndSwimsFast(GameTestHelper helper) {
		ServerPlayer p = carapace(helper, 1.5, 2.0, 1.5);
		double swim = p.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
		CarapaceMoves.tidal(p);
		helper.assertTrue(p.hasEffect(MobEffects.WATER_BREATHING) && p.hasEffect(MobEffects.DOLPHINS_GRACE), "water breathing and Dolphin's Grace");
		helper.assertTrue(p.getEffect(MobEffects.WATER_BREATHING).getDuration() >= CarapaceConfig.TIDAL_TICKS - 1, "60 s");
		helper.assertTrue(p.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY) > swim, "faster swimming");
		helper.succeed();
	}

	// ---------------------------------------------------------------- clean-up

	@GameTest(template = EMPTY_STRUCTURE)
	public void detransformingEndsEverything(GameTestHelper helper) {
		floor(helper, 7);
		ServerPlayer p = carapace(helper, 3.5, 2.0, 1.5);
		mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100, false);
		double step = p.getAttributeValue(Attributes.STEP_HEIGHT);
		CarapaceMoves.steadfast(p);
		CarapaceMoves.barrier(p);
		CarapaceMoves.shelter(p);
		ready(helper, p);
		CarapaceMoves.surf(p);
		Miraculous.detransform(p, false);
		MiraculousState s = Miraculous.state(p);
		helper.assertTrue(s.vals.keySet().stream().noneMatch(k -> k.startsWith("cp_")), "his values are gone: " + s.vals.keySet());
		helper.assertTrue(s.vecs.keySet().stream().noneMatch(k -> k.startsWith("cp_")), "the dome and the wall are gone");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - step) < 1.0e-6, "no surf step height");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1.0, "no Steadfast knockback immunity");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(ShieldThrowEntity.class, new AABB(p.blockPosition()).inflate(32)).isEmpty(),
				"the thrown shield is gone");
		helper.succeed();
	}
}
