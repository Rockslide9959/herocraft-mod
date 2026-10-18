package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkAbilityManager;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.explosion.Explosion;
import com.projecthero.mod.quirk.explosion.ExplosionConfig;
import com.projecthero.mod.quirk.explosion.ExplosionMoves;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 Explosion (Bakugo / Ground Zero): one test per move -- the server effect and its number -- plus the custom
 * explosions' rules (no blocks broken with mobGriefing off, never a container with it on, the user never hurt) and the
 * Arm Strain overload. Mock players are not ticked by the server, so tests that run over time drive the Quirk tick
 * themselves ({@link #pump}). Every move runs at 50 Sweat (power 1.0) unless the test says otherwise.
 */
public class QuirkExplosionGameTests implements FabricGameTest {
	// ---------------------------------------------------------------- helpers

	@SuppressWarnings("removal")
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

	/** Bakugo facing +Z, every move ready, 50 Sweat, no strain. */
	private static ServerPlayer bakugo(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(Quirks.grant(p, Quirk.EXPLOSION), "Explosion is granted");
		Quirks.edit(p, n -> {
			n.animId = QuirkState.ANIM_NONE;
			n.abilityReadyAt.clear();
		});
		Quirks.setMeter(p, 50f);
		ExplosionMoves.setStrain(p, 0f);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Quirks.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
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
		m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0);
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static float taken(Zombie z, double max) {
		return (float) max - z.getHealth();
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE)
	public void apShotExplodesOnTheTargetForTen(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 0.5);
		Zombie z = zombie(helper, 3.5, 2.0, 6.5, 100);
		Vec3 end = ExplosionMoves.apShot(p);
		float t = taken(z, 100);
		helper.assertTrue(end.distanceTo(z.position()) < 2.5, "it stopped at the zombie: " + end);
		helper.assertTrue(t >= ExplosionConfig.AP_DAMAGE * 0.8f && t <= ExplosionConfig.AP_DAMAGE + 0.6f, "about 10, took " + t);
		helper.assertTrue(!Quirks.ready(p, ExplosionConfig.AP_SHOT), "cooldown");
		helper.assertTrue(Quirks.meter(p) < 50f, "it spent Sweat");
		helper.assertTrue(ExplosionMoves.strain(p) > 0f, "it strained the arm");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void autoCannonFiresEightSmallShots(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 0.5);
		Zombie z = zombie(helper, 3.5, 2.0, 5.5, 200);
		pump(helper, p);
		ExplosionMoves.autoCannon(p);
		helper.assertTrue(Explosion.running(p, Explosion.V_AUTO), "the volley runs");
		helper.runAfterDelay(ExplosionConfig.AUTO_SHOTS * ExplosionConfig.AUTO_INTERVAL + 6, () -> {
			float t = taken(z, 200);
			// eight shots of 4 (the spread may graze some, the falloff softens them)
			helper.assertTrue(t >= ExplosionConfig.AUTO_DAMAGE * 4 && t <= ExplosionConfig.AUTO_DAMAGE * ExplosionConfig.AUTO_SHOTS * 1.3f,
					"most of 8 x 4 landed, took " + t);
			helper.assertTrue(!Explosion.running(p, Explosion.V_AUTO), "the volley is over");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE)
	public void blastPunchHitsPointBlankForFourteenAndKnocksBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.5);
		Zombie z = zombie(helper, 3.5, 2.0, 3.6, 100);
		int n = ExplosionMoves.blastPunch(p);
		float t = taken(z, 100);
		helper.assertTrue(n == 1, "one hit, got " + n);
		helper.assertTrue(t >= ExplosionConfig.PUNCH_DAMAGE * 0.8f && t <= ExplosionConfig.PUNCH_DAMAGE + 0.6f, "about 14, took " + t);
		helper.assertTrue(z.getDeltaMovement().z > 0.5, "blown away from him: " + z.getDeltaMovement());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void uppercutLaunchesTheTargetUp(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.5);
		Zombie z = zombie(helper, 3.5, 2.0, 3.5, 100);
		int n = ExplosionMoves.uppercut(p);
		helper.assertTrue(n == 1, "one hit, got " + n);
		helper.assertTrue(taken(z, 100) >= ExplosionConfig.UPPERCUT_DAMAGE * 0.75f, "about 12, took " + taken(z, 100));
		helper.assertTrue(z.getDeltaMovement().y > 0.9, "launched up: " + z.getDeltaMovement());
		helper.succeed();
	}

	// ---------------------------------------------------------------- X

	@GameTest(template = EMPTY_STRUCTURE)
	public void stunGrenadeBlindsAndDropsTargets(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.5);
		Zombie z = zombie(helper, 3.5, 2.0, 5.5, 100);
		z.setTarget(p);
		int n = ExplosionMoves.stunGrenade(p);
		helper.assertTrue(n >= 1, "caught it");
		helper.assertTrue(z.hasEffect(MobEffects.BLINDNESS) && z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "blinded and slowed");
		helper.assertTrue(z.getTarget() == null, "it lost its target");
		helper.assertTrue(!p.hasEffect(MobEffects.BLINDNESS), "never himself");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void smokeBlastHidesHimAndBlindsWhatIsInside(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 3.5);
		Zombie z = zombie(helper, 3.5, 2.0, 6.5, 100);
		z.setTarget(p);
		pump(helper, p);
		ExplosionMoves.smokeBlast(p);
		helper.assertTrue(Explosion.running(p, Explosion.V_SMOKE), "the smoke hangs");
		helper.assertTrue(ExplosionMoves.inSmoke(p, z.position()), "the zombie is inside it");
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(z.hasEffect(MobEffects.BLINDNESS), "blinded in the smoke");
			helper.assertTrue(z.getTarget() == null, "it lost him");
			helper.assertTrue(p.hasEffect(MobEffects.INVISIBILITY), "he is hidden in it");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void blastRushFliesWhileHeldAndEndsOnRelease(GameTestHelper helper) {
		ServerPlayer p = bakugo(helper, 3.5, 4.0, 3.5);
		pump(helper, p);
		QuirkKits.of(Quirk.EXPLOSION).handle(p, AbilitySlot.SLOT_4, true, false);
		helper.assertTrue(Explosion.rushing(p) && ExplosionMoves.rushing(p), "rushing");
		helper.assertTrue(QuirkKits.of(Quirk.EXPLOSION).active(p, AbilitySlot.SLOT_4), "the HUD box is lit");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(ExplosionMoves.rushing(p), "still going while held");
			helper.assertTrue(ExplosionMoves.strain(p) > 0f, "the arms strain while he rushes");
			helper.assertTrue(ExplosionMoves.softLanding(p), "no fall damage while rushing");
			QuirkKits.of(Quirk.EXPLOSION).handle(p, AbilitySlot.SLOT_4, false, false);
			helper.assertTrue(!Explosion.rushing(p), "let go: it ends");
			helper.assertTrue(!Quirks.ready(p, ExplosionConfig.BLAST_RUSH), "cooldown on release");
			helper.assertTrue(ExplosionMoves.softLanding(p), "a soft landing after it");
			helper.assertTrue(!p.getAbilities().mayfly, "the float permission is gone again");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void blastDashThrowsHimTheWayHeLooks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.5);
		ExplosionMoves.blastDash(p);
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(v.z > 0.9, "launched forward (+Z): " + v);
		helper.assertTrue(ExplosionMoves.softLanding(p), "no fall damage from it");
		helper.assertTrue(!Quirks.ready(p, ExplosionConfig.BLAST_DASH), "cooldown");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void howitzerImpactSpinsAndDetonates(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.5);
		Quirks.setMeter(p, 100f);
		Zombie z = zombie(helper, 3.5, 2.0, 5.5, 250);
		helper.assertTrue(ExplosionMoves.howitzer(p), "it starts");
		helper.assertTrue(Explosion.spinning(p), "spinning");
		helper.assertTrue(Quirks.meter(p) <= 100f * (1f - ExplosionConfig.HOWITZER_SWEAT_FRACTION) + 0.5f, "most of his Sweat");
		pump(helper, p);
		helper.runAfterDelay(ExplosionConfig.HOWITZER_SPIN_TICKS + 4, () -> {
			helper.assertTrue(!Explosion.spinning(p), "it detonated");
			float t = taken(z, 250);
			helper.assertTrue(t >= 20f, "a huge blast (30 x 1.4 at the centre), took " + t);
			helper.assertTrue(p.getHealth() >= p.getMaxHealth(), "he is untouched");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void howitzerAndMaxShotNeedSweat(GameTestHelper helper) {
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 3.5);
		Quirks.setMeter(p, 10f);
		helper.assertTrue(!ExplosionMoves.howitzer(p), "refused dry");
		helper.assertTrue(!ExplosionMoves.maxShot(p), "refused dry");
		helper.assertTrue(Quirks.ready(p, ExplosionConfig.HOWITZER) && Quirks.ready(p, ExplosionConfig.MAX_SHOT), "no cooldown spent");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void maxShotChargesThenBlastsDownTheLine(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 0.5);
		Quirks.setMeter(p, 100f);
		Zombie near = zombie(helper, 3.5, 2.0, 3.0, 200);
		Zombie far = zombie(helper, 3.5, 2.0, 6.0, 200);
		// a wall at the end of the area stops the cannon (it never reaches the neighbouring tests)
		for (int x = 0; x <= 7; x++) {
			for (int y = 2; y <= 5; y++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, 7)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		helper.assertTrue(ExplosionMoves.maxShot(p), "charging");
		helper.assertTrue(Quirks.meter(p) <= 0.01f, "all his Sweat");
		helper.assertTrue(Explosion.charging(p), "the charge shows");
		pump(helper, p);
		helper.runAfterDelay(ExplosionConfig.MAX_SHOT_CHARGE + 3, () -> {
			helper.assertTrue(!Explosion.charging(p), "fired");
			for (Zombie z : new Zombie[] { near, far }) {
				float t = taken(z, 200);
				helper.assertTrue(t >= ExplosionConfig.MAX_SHOT_DAMAGE * 0.7f && t <= ExplosionConfig.MAX_SHOT_DAMAGE * 1.4f + 0.6f,
						"each in the path hit once (26 x 1.4), took " + t);
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 30)
	public void clusterHitsBothRingsOnce(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.0);
		Zombie inner = zombie(helper, 3.5, 2.0, 3.5, 100);
		Zombie outer = zombie(helper, 3.5, 2.0, 7.2, 100);
		pump(helper, p);
		int n = ExplosionMoves.cluster(p);
		helper.assertTrue(n == 1, "the inner ring hit the near one, got " + n);
		helper.runAfterDelay(ExplosionConfig.CLUSTER_RING_DELAY + 2, () -> {
			for (Zombie z : new Zombie[] { inner, outer }) {
				helper.assertTrue(Math.abs(taken(z, 100) - ExplosionConfig.CLUSTER_DAMAGE) < 0.6f, "9 each, once: " + taken(z, 100));
			}
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void explosiveCounterBlocksTheHitAndBlastsTheAttacker(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 2.5);
		Zombie z = zombie(helper, 3.5, 2.0, 4.0, 100);
		ExplosionMoves.counter(p);
		helper.assertTrue(ExplosionMoves.counterOpen(p), "the window is open");
		boolean hurt = p.hurt(helper.getLevel().damageSources().mobAttack(z), 6f);
		helper.assertTrue(!hurt && p.getHealth() >= p.getMaxHealth(), "the hit was blocked");
		helper.assertTrue(taken(z, 100) >= ExplosionConfig.COUNTER_DAMAGE * 0.7f, "the attacker was blasted, took " + taken(z, 100));
		helper.assertTrue(!ExplosionMoves.counterOpen(p), "one counter only");
		p.invulnerableTime = 0;
		helper.assertTrue(p.hurt(helper.getLevel().damageSources().mobAttack(z), 2f), "the next hit lands");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the rules

	@GameTest(template = EMPTY_STRUCTURE)
	public void noBlocksBreakWithMobGriefingOffAndNeverAContainer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		// a dirt wall with a chest in it, right where the punch lands
		for (int x = 2; x <= 5; x++) {
			for (int y = 2; y <= 4; y++) {
				level.setBlock(helper.absolutePos(new BlockPos(x, y, 4)), Blocks.DIRT.defaultBlockState(), 3);
			}
		}
		BlockPos chest = helper.absolutePos(new BlockPos(3, 3, 4));
		level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 2.0);
		Quirks.setMeter(p, 100f);
		GameRules.BooleanValue rule = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
		boolean was = rule.get();
		try {
			rule.set(false, level.getServer());
			ExplosionMoves.blastPunch(p);
			ExplosionMoves.apShot(p);
			ExplosionMoves.fireMaxShot(p);
			int dirt = 0;
			for (int x = 2; x <= 5; x++) {
				for (int y = 2; y <= 4; y++) {
					if (level.getBlockState(helper.absolutePos(new BlockPos(x, y, 4))).is(Blocks.DIRT)) {
						dirt++;
					}
				}
			}
			helper.assertTrue(dirt == 11, "mobGriefing off: no block broke, dirt left " + dirt);
			rule.set(true, level.getServer());
			ExplosionMoves.setStrain(p, 0f);
			Quirks.edit(p, n -> n.abilityReadyAt.clear());
			ExplosionMoves.blastPunch(p);
			helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST), "never a container");
		} finally {
			rule.set(was, level.getServer());
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void theUserNeverTakesDamageFromHisOwnBlasts(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 3.5);
		Quirks.setMeter(p, 100f);
		zombie(helper, 3.5, 2.0, 5.5, 300);
		pump(helper, p);
		// every move point blank, the wall of blasts right on top of him
		p.setXRot(80f);
		ExplosionMoves.apShot(p);
		ExplosionMoves.blastPunch(p);
		ExplosionMoves.uppercut(p);
		ExplosionMoves.stunGrenade(p);
		ExplosionMoves.cluster(p);
		ExplosionMoves.blastDash(p);
		ExplosionMoves.autoCannon(p);
		ExplosionMoves.setStrain(p, 0f);
		Quirks.setMeter(p, 100f);
		ExplosionMoves.howitzer(p);
		helper.assertTrue(!p.hurt(helper.getLevel().damageSources().fall(), 8f), "no fall damage after his own launch");
		helper.runAfterDelay(ExplosionConfig.HOWITZER_SPIN_TICKS + 6, () -> {
			ExplosionMoves.setStrain(p, 0f);
			Quirks.setMeter(p, 100f);
			Quirks.edit(p, n -> {
				n.abilityReadyAt.clear();
				n.overloadUntil = 0L;
			});
			ExplosionMoves.fireMaxShot(p);
			helper.assertTrue(!p.hurt(helper.getLevel().damageSources().explosion(p, p), 10f), "his own explosion can't hurt him");
			helper.assertTrue(p.getHealth() >= p.getMaxHealth(), "full health after everything: " + p.getHealth());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fullArmStrainOverloadsHim(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 3.5);
		ExplosionMoves.startRush(p);
		ExplosionMoves.setStrain(p, 98f);
		ExplosionMoves.apShot(p);
		helper.assertTrue(Quirks.overloaded(p), "Arm Strain");
		helper.assertTrue(Quirks.overloadLeft(p) >= ExplosionConfig.STRAIN_TICKS - 1, "for 5 s");
		helper.assertTrue(p.hasEffect(MobEffects.WEAKNESS), "Weakness");
		helper.assertTrue(!Explosion.rushing(p), "the Rush stopped");
		helper.assertTrue(ExplosionMoves.strain(p) == 0f, "the strain reset");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		float sweat = Quirks.meter(p);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(Quirks.ready(p, ExplosionConfig.BLAST_PUNCH) && Quirks.meter(p) == sweat, "no moves while strained");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void moreSweatMeansBiggerBlasts(GameTestHelper helper) {
		floor(helper);
		helper.assertTrue(Math.abs(ExplosionConfig.powerAt(0f) - ExplosionConfig.POWER_DRY) < 1e-4
				&& Math.abs(ExplosionConfig.powerAt(100f) - ExplosionConfig.POWER_SOAKED) < 1e-4, "0.6x dry, 1.4x soaked");
		ServerPlayer p = bakugo(helper, 1.5, 2.0, 1.5);
		Zombie a = zombie(helper, 1.5, 2.0, 3.6, 100);
		Quirks.setMeter(p, 0f);
		ExplosionMoves.blastPunch(p);
		ServerPlayer q = bakugo(helper, 5.5, 2.0, 1.5);
		Zombie b = zombie(helper, 5.5, 2.0, 3.6, 100);
		Quirks.setMeter(q, 100f);
		ExplosionMoves.blastPunch(q);
		helper.assertTrue(taken(b, 100) > taken(a, 100) * 1.8f, "soaked hits much harder: " + taken(a, 100) + " vs " + taken(b, 100));
		float before = Quirks.meter(p);
		ExplosionMoves.markCombat(p, ExplosionConfig.SWEAT_HIT_TAKEN, false);
		helper.assertTrue(Quirks.meter(p) > before, "a fight makes him sweat");
		helper.succeed();
	}

	/** Every key resolves to two moves with a cooldown (HUD / guide). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyKeyHasTwoMoves(GameTestHelper helper) {
		var kit = QuirkKits.of(Quirk.EXPLOSION);
		for (AbilitySlot slot : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4,
				AbilitySlot.SLOT_5, AbilitySlot.SLOT_6 }) {
			String[] ids = kit.idsOf(slot);
			helper.assertTrue(ids.length == 2, slot + " has two moves");
			for (String id : ids) {
				helper.assertTrue(id.startsWith("quirk_explosion_") && kit.maxCooldown(id) > 0, id + " has a cooldown");
			}
		}
		helper.succeed();
	}

}
