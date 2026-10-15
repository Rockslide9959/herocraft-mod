package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ghostrider.GhostRider;
import com.projecthero.mod.ghostrider.GhostRiderAbilities;
import com.projecthero.mod.ghostrider.GhostRiderConfig;
import com.projecthero.mod.ghostrider.data.GhostRiderState;
import com.projecthero.mod.ghostrider.entity.HellCycleEntity;
import com.projecthero.mod.ghostrider.entity.HellfireBlastEntity;
import com.projecthero.mod.kryptonian.KryptonianConfig;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Ghost Rider rework (the user's list): +11 melee, Regeneration III paid in Hellfire, Superman's sun rate for
 * Hellfire, double hunger, no turning back in combat or near monsters, the death he cheats and the lock until dawn, the
 * Hellfire Blast as a projectile, and the bike's new keys and numbers. Mock players are not ticked by the server, so
 * time-based checks drive {@link GhostRider#tick} themselves. The revert test sits in its own batch: it looks 20 blocks
 * out for monsters, and other tests' zombies would be that close in the shared grid.
 */
public class GhostRiderReworkGameTests implements FabricGameTest {
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

	private static ServerPlayer rider(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		GhostRider.grant(p);
		GhostRider.transform(p);
		GhostRiderState n = GhostRider.state(p).copy();
		n.formChangeAt = helper.getLevel().getGameTime() - 200L;
		n.animId = GhostRiderState.ANIM_NONE;
		p.setAttached(ModAttachments.GHOST_RIDER_STATE, n);
		return p;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		m.setHealth(100f);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	// ---------------------------------------------------------------- numbers

	@GameTest(template = EMPTY_STRUCTURE)
	public void theReworkNumbers(GameTestHelper helper) {
		helper.assertTrue(GhostRiderConfig.WAVE_DAMAGE == 30f, "Hellfire Wave 30");
		helper.assertTrue(GhostRiderConfig.WHIP_DAMAGE == 18f, "Chain Whip 18");
		helper.assertTrue(GhostRiderConfig.SPIN_DAMAGE == 13f, "Chain Spin 13 a hit");
		helper.assertTrue(GhostRiderConfig.HELLFIRE_REGEN == KryptonianConfig.SOLAR_SUN_PER_SECOND, "Hellfire refills at Superman's sun rate");
		// the bike's speed value settles at 15 / 30 blocks a second on ordinary ground (0.6 friction)
		for (double bps : new double[] { GhostRiderConfig.CYCLE_CRUISE_BPS, GhostRiderConfig.CYCLE_SPRINT_BPS }) {
			double speed = GhostRiderConfig.cycleSpeedFor(bps);
			double v = 0;
			for (int i = 0; i < 200; i++) {
				v = (v + speed * 0.98) * 0.6f * 0.91f;
			}
			double perSecond = v / (0.6f * 0.91f) * 20.0; // the move happens before the friction
			helper.assertTrue(Math.abs(perSecond - bps) < 0.1, bps + " b/s wanted, the physics settles at " + perSecond);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void meleeBonusWhileTransformed(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		GhostRider.grant(p);
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) == base, "human: no bonus");
		GhostRider.transform(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (base + 11.0)) < 1.0e-6,
				"+11 melee transformed, got " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		GhostRider.revert(p);
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) == base, "gone when he turns back");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Regeneration III and its Hellfire

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void regenerationCostsTwoHellfireASecond(GameTestHelper helper) {
		ServerPlayer gr = rider(helper, 2.5, 2.0, 2.5);
		ServerPlayer human = mock(helper, 5.5, 2.0, 2.5);
		GhostRider.grant(human);
		GhostRider.setHellfire(gr, 20f);
		GhostRider.setHellfire(human, 20f);
		helper.onEachTick(() -> {
			gr.tickCount++;
			human.tickCount++;
			GhostRider.tick(gr);
			GhostRider.tick(human);
		});
		helper.runAfterDelay(41, () -> {
			MobEffectInstance regen = gr.getEffect(MobEffects.REGENERATION);
			helper.assertTrue(regen != null && regen.getAmplifier() == GhostRiderConfig.REGEN_AMPLIFIER, "Regeneration III transformed: " + regen);
			helper.assertTrue(human.getEffect(MobEffects.REGENERATION) == null, "none while human");
			float gainGr = GhostRider.hellfire(gr) - 20f;
			float gainHuman = GhostRider.hellfire(human) - 20f;
			helper.assertTrue(gainHuman > 5f, "human: the full sun rate, gained " + gainHuman);
			float expected = gainHuman * (GhostRiderConfig.HELLFIRE_REGEN - GhostRiderConfig.REGEN_HELLFIRE_PER_SECOND) / GhostRiderConfig.HELLFIRE_REGEN;
			helper.assertTrue(Math.abs(gainGr - expected) < 0.01f, "transformed: 2 a second less (" + expected + "), gained " + gainGr);
			GhostRider.revert(gr);
			helper.assertTrue(gr.getEffect(MobEffects.REGENERATION) == null, "the regeneration ends with the change");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- food

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void foodGoesTwiceAsFastTransformed(GameTestHelper helper) {
		ServerPlayer gr = rider(helper, 2.5, 2.0, 2.5);
		ServerPlayer human = mock(helper, 5.5, 2.0, 2.5);
		GhostRider.grant(human);
		gr.getFoodData().setExhaustion(0f);
		human.getFoodData().setExhaustion(0f);
		GhostRider.tick(gr);
		GhostRider.tick(human);
		helper.runAfterDelay(1, () -> {
			gr.causeFoodExhaustion(1f);
			human.causeFoodExhaustion(1f);
			GhostRider.tick(gr);
			GhostRider.tick(human);
			float g = gr.getFoodData().getExhaustionLevel();
			float h = human.getFoodData().getExhaustionLevel();
			helper.assertTrue(Math.abs(g - 2f) < 1.0e-3f, "transformed: 1 exhaustion counts double, got " + g);
			helper.assertTrue(Math.abs(h - 1f) < 1.0e-3f, "human: unchanged, got " + h);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- turning back

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ghost_rider_revert")
	public void noTurningBackNearMonstersOrInAFight(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rider(helper, 1.5, 2.0, 1.5);
		Zombie z = zombie(helper, 1.5, 2.0, 11.5); // 10 blocks off
		GhostRider.toggleForm(p);
		helper.assertTrue(GhostRider.transformed(p), "H refused: a zombie within 20 blocks");
		helper.assertTrue("message.projecthero.ghost_rider.revert_hostile".equals(GhostRider.revertRefusal(p)), "the reason: the monster");
		ServerPlayer q = rider(helper, 5.5, 2.0, 1.5);
		q.invulnerableTime = 0;
		q.hurt(q.damageSources().mobAttack(z), 1f);
		z.discard();
		helper.assertTrue(GhostRider.inCombat(q), "a blow taken puts him in combat");
		helper.assertTrue("message.projecthero.ghost_rider.revert_combat".equals(GhostRider.revertRefusal(q)), "the reason: the fight");
		helper.assertTrue(GhostRider.revertRefusal(p) == null, "the zombie is gone: nothing holds p");
		helper.runAfterDelay(21, () -> { // past H's debounce
			GhostRider.toggleForm(p);
			helper.assertFalse(GhostRider.transformed(p), "out of combat, nothing near: H turns him back");
			GhostRider.toggleForm(q);
			helper.assertTrue(GhostRider.transformed(q), "still within 5 s of the blow: refused");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- death

	@GameTest(template = EMPTY_STRUCTURE)
	public void heCannotDieAndTheSpiritHoldsOnUntilDawn(GameTestHelper helper) {
		ServerPlayer p = rider(helper, 2.5, 2.0, 2.5);
		long day = helper.getLevel().getServer().overworld().getDayTime();
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().generic(), 500f);
		helper.assertTrue(p.isAlive() && !p.isDeadOrDying(), "transformed: a killing blow doesn't kill");
		helper.assertTrue(Math.abs(p.getHealth() - GhostRiderConfig.DEATH_SAVE_HEALTH) < 0.01f, "left on 6 health, got " + p.getHealth());
		long lock = GhostRider.state(p).dayLockUntil;
		helper.assertTrue(lock == GhostRider.nextSunrise(day), "locked until the next sunrise: " + lock + " (day time " + day + ")");
		helper.assertTrue("message.projecthero.ghost_rider.revert_locked".equals(GhostRider.revertRefusal(p)), "H can't turn him back");
		GhostRider.tickDayLock(p, lock - 6000L); // the night before
		helper.assertTrue(GhostRider.dayLocked(p), "still night: still locked");
		GhostRider.tickDayLock(p, lock + 13000L); // sunset the next day... (night, still past the lock)
		helper.assertTrue(GhostRider.dayLocked(p), "night: still locked");
		GhostRider.tickDayLock(p, lock + 1000L);
		helper.assertFalse(GhostRider.dayLocked(p), "morning: the lock lifts");
		// the lock is persistent state (it survives a relog through the codec)
		GhostRiderState locked = GhostRider.state(p).copy();
		locked.dayLockUntil = 12345L;
		var json = GhostRiderState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, locked).getOrThrow();
		helper.assertTrue(GhostRiderState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow().dayLockUntil == 12345L,
				"day_lock_until round-trips the codec");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aHumanDeathCallsTheSpiritOut(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		GhostRider.grant(p);
		helper.assertFalse(GhostRider.transformed(p), "human");
		p.hurt(p.damageSources().generic(), 500f);
		helper.assertTrue(p.isAlive() && !p.isDeadOrDying(), "human: a killing blow doesn't kill either");
		helper.assertTrue(GhostRider.transformed(p), "the Spirit takes over");
		helper.assertTrue(GhostRider.dayLocked(p), "and holds on until dawn");
		ServerPlayer q = mock(helper, 5.5, 2.0, 2.5);
		GhostRider.grant(q);
		q.hurt(q.damageSources().genericKill(), Float.MAX_VALUE);
		helper.assertTrue(q.isDeadOrDying(), "/kill still kills");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the projectile and the bike

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void hellfireBlastIsAProjectile(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rider(helper, 3.5, 2.0, 1.5);
		Zombie z = zombie(helper, 3.5, 2.0, 7.5);
		GhostRiderAbilities.startBlast(p);
		GhostRiderAbilities.stopBlast(p);
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(HellfireBlastEntity.class, box).size() == 1, "R threw one bolt entity");
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(HellfireBlastEntity.class, box).isEmpty(), "it burst on the zombie");
			float taken = 100f - z.getHealth();
			helper.assertTrue(taken >= GhostRiderConfig.BLAST_DAMAGE - 0.6f, "20 damage, took " + taken);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bikeKeysLeapAndSendBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rider(helper, 3.5, 2.0, 3.5);
		GhostRider.setHellfire(p, GhostRiderConfig.HELLFIRE_MAX);
		GhostRiderAbilities.cycleKey(p);
		helper.assertTrue(p.getVehicle() instanceof HellCycleEntity, "X: the bike under him");
		HellCycleEntity bike = (HellCycleEntity) p.getVehicle();
		helper.assertTrue(bike.getAttributeValue(Attributes.STEP_HEIGHT) == GhostRiderConfig.CYCLE_STEP, "a 4-block step");
		GhostRiderAbilities.cycleKey(p);
		helper.assertTrue(GhostRider.cooldownRemaining(p, GhostRiderAbilities.CYCLE_JUMP) > 0, "X while riding: the Cycle Jump");
		helper.assertTrue(bike.getDeltaMovement().y > 0.1, "the bike leaps, v=" + bike.getDeltaMovement());
		helper.assertTrue(!bike.isRemoved() && p.getVehicle() == bike, "and he stays on it");
		GhostRiderAbilities.sendCycleBack(p);
		helper.assertTrue(bike.isRemoved() && !p.isPassenger(), "Shift+X sends it back");
		helper.succeed();
	}
}
