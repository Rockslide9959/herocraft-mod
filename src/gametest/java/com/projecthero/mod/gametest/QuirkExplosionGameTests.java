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

	// ---------------------------------------------------------------- G: Explosive Punch

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

	// ---------------------------------------------------------------- V: utility

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

	// ---------------------------------------------------------------- X: movement

	@GameTest(template = EMPTY_STRUCTURE)
	public void blastDashThrowsHimTheWayHeLooks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.5);
		ExplosionMoves.blastDash(p);
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(v.z > 0.9, "launched forward (+Z): " + v);
		helper.assertTrue(ExplosionMoves.softLanding(p), "no fall damage from it");
		helper.assertTrue(!Quirks.ready(p, ExplosionConfig.DASH), "cooldown");
		helper.succeed();
	}

	// ---------------------------------------------------------------- C: Howitzer Impact

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void howitzerImpactRisesSpinsAndDetonates(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.5);
		p.setOnGround(true);
		Quirks.setMeter(p, 100f);
		Zombie z = zombie(helper, 3.5, 2.0, 5.5, 250);
		helper.assertTrue(ExplosionMoves.howitzer(p), "it starts");
		helper.assertTrue(Explosion.spinning(p) && ExplosionMoves.howitzerKind(p) == 1, "spinning");
		helper.assertTrue(Explosion.val(p, Explosion.V_HOWITZER_DIVE) >= helper.getLevel().getGameTime() + ExplosionConfig.HOWITZER_RISE_TICKS,
				"from the ground he rises first, then dives");
		helper.assertTrue(Quirks.meter(p) <= 100f * (1f - ExplosionConfig.HOWITZER_SWEAT_FRACTION) + 0.5f, "most of his Sweat");
		pump(helper, p);
		helper.runAfterDelay(2, () -> helper.assertTrue(p.getDeltaMovement().y > 0.3, "the rise blasts him up: " + p.getDeltaMovement()));
		helper.runAfterDelay(ExplosionConfig.HOWITZER_RISE_TICKS + ExplosionConfig.HOWITZER_SPIN_TICKS + 4, () -> {
			helper.assertTrue(!Explosion.spinning(p), "it detonated");
			float t = taken(z, 250);
			helper.assertTrue(t >= 20f, "a huge blast (30 x 1.4 at the centre), took " + t);
			helper.assertTrue(p.getHealth() >= p.getMaxHealth(), "he is untouched");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Z: Cluster

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
			ExplosionMoves.fireGauntlet(p);
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
		ExplosionMoves.stunGrenade(p);
		ExplosionMoves.cluster(p);
		ExplosionMoves.blastDash(p);
		ExplosionMoves.autoCannon(p);
		ExplosionMoves.setStrain(p, 0f);
		Quirks.setMeter(p, 100f);
		ExplosionMoves.howitzer(p);
		helper.assertTrue(!p.hurt(helper.getLevel().damageSources().fall(), 8f), "no fall damage after his own launch");
		helper.runAfterDelay(ExplosionConfig.HOWITZER_RISE_TICKS + ExplosionConfig.HOWITZER_SPIN_TICKS + 6, () -> {
			ExplosionMoves.setStrain(p, 0f);
			Quirks.setMeter(p, 100f);
			Quirks.edit(p, n -> {
				n.abilityReadyAt.clear();
				n.overloadUntil = 0L;
			});
			ExplosionMoves.fireGauntlet(p);
			helper.assertTrue(!p.hurt(helper.getLevel().damageSources().explosion(p, p), 10f), "his own explosion can't hurt him");
			helper.assertTrue(p.getHealth() >= p.getMaxHealth(), "full health after everything: " + p.getHealth());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fullArmStrainOverloadsHim(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 3.5);
		ExplosionMoves.startPropel(p);
		ExplosionMoves.setStrain(p, 98f);
		ExplosionMoves.apShot(p);
		helper.assertTrue(Quirks.overloaded(p), "Arm Strain");
		helper.assertTrue(Quirks.overloadLeft(p) >= ExplosionConfig.STRAIN_TICKS - 1, "for 5 s");
		helper.assertTrue(p.hasEffect(MobEffects.WEAKNESS), "Weakness");
		helper.assertTrue(!Explosion.propelling(p), "the flight stopped");
		helper.assertTrue(ExplosionMoves.strain(p) == 0f, "the strain reset");
		Quirks.edit(p, n -> n.abilityReadyAt.clear());
		float sweat = Quirks.meter(p);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(Quirks.ready(p, ExplosionConfig.PUNCH) && Quirks.meter(p) == sweat, "no moves while strained");
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

	// ---------------------------------------------------------------- v0.16.7 revamp: the new moves

	@GameTest(template = EMPTY_STRUCTURE)
	public void xIsMovementVIsUtility(GameTestHelper helper) {
		var kit = QuirkKits.of(Quirk.EXPLOSION);
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_3)[0].equals(ExplosionConfig.PROPULSION) && kit.idsOf(AbilitySlot.SLOT_3)[1].equals(ExplosionConfig.DASH),
				"X: Propulsion / Explosion Dash");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_5)[0].equals(ExplosionConfig.STUN_GRENADE) && kit.idsOf(AbilitySlot.SLOT_5)[1].equals(ExplosionConfig.COOL_OFF),
				"V: Stun Grenade / Cool Off");
		helper.assertTrue(kit.usableWhileOverloaded(AbilitySlot.SLOT_5, true) && !kit.usableWhileOverloaded(AbilitySlot.SLOT_5, false),
				"only Cool Off works while strained");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void propulsionFliesWhileHeldBurnsSweatAndEndsOnRelease(GameTestHelper helper) {
		ServerPlayer p = bakugo(helper, 3.5, 4.0, 3.5);
		Quirks.setMeter(p, 50f);
		pump(helper, p);
		QuirkKits.of(Quirk.EXPLOSION).handle(p, AbilitySlot.SLOT_3, true, false);
		helper.assertTrue(Explosion.propelling(p) && ExplosionMoves.propelling(p), "flying");
		helper.assertTrue(QuirkKits.of(Quirk.EXPLOSION).active(p, AbilitySlot.SLOT_3), "the HUD box is lit");
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(ExplosionMoves.propelling(p), "still going while held");
			helper.assertTrue(Quirks.meter(p) < 50f - 2f, "it burns Sweat: " + Quirks.meter(p));
			helper.assertTrue(ExplosionMoves.strain(p) > 0f, "the arms strain while he flies");
			helper.assertTrue(ExplosionMoves.softLanding(p), "no fall damage while flying");
			QuirkKits.of(Quirk.EXPLOSION).handle(p, AbilitySlot.SLOT_3, false, false);
			helper.assertTrue(!Explosion.propelling(p), "let go: it ends");
			helper.assertTrue(!Quirks.ready(p, ExplosionConfig.PROPULSION), "cooldown on release");
			helper.assertTrue(ExplosionMoves.softLanding(p), "a soft landing after it");
			helper.assertTrue(!p.getAbilities().mayfly, "the float permission is gone again");
			helper.succeed();
		});
	}

	/** v0.16.7 (user): the sweat charges on its own, 4 a second, standing still out of a fight. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void sweatChargesFourASecondOnItsOwn(GameTestHelper helper) {
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 3.5);
		Quirks.setMeter(p, 0f);
		pump(helper, p);
		helper.runAfterDelay(20, () -> {
			float m = Quirks.meter(p);
			helper.assertTrue(m > 3.4f && m < 6f, "about 4 sweat after a second standing still, got " + m);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void propulsionSputtersOutWhenDry(GameTestHelper helper) {
		ServerPlayer p = bakugo(helper, 3.5, 4.0, 3.5);
		Quirks.setMeter(p, 1f);
		helper.assertTrue(!ExplosionMoves.startPropel(p), "too dry to take off");
		Quirks.setMeter(p, 4f);
		helper.assertTrue(ExplosionMoves.startPropel(p), "a little sweat: off he goes");
		pump(helper, p);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(!ExplosionMoves.propelling(p), "out of sweat, the blasts sputter out");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void blastRushMarchesAChainOfBlastsDownTheLine(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 0.5);
		Zombie near = zombie(helper, 3.5, 2.0, 2.5, 100);
		Zombie far = zombie(helper, 3.5, 2.0, 7.0, 100);
		Zombie side = zombie(helper, 7.5, 2.0, 2.5, 100);
		pump(helper, p);
		ExplosionMoves.blastRush(p);
		helper.assertTrue(near.getHealth() < 100f, "the first blast lands at once");
		helper.assertTrue(far.getHealth() == 100f, "the far one isn't reached yet");
		helper.runAfterDelay(ExplosionConfig.BLAST_RUSH_COUNT * ExplosionConfig.BLAST_RUSH_INTERVAL + 2, () -> {
			helper.assertTrue(ExplosionMoves.blastRushHit(p).contains(far.getId()), "the chain reached the far one");
			helper.assertTrue(taken(near, 100) <= ExplosionConfig.BLAST_RUSH_DAMAGE * 1.4f + 0.6f, "each is hit once, took " + taken(near, 100));
			helper.assertTrue(side.getHealth() == 100f, "4 blocks off the line is safe");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void howitzerClusterIsAQuickSmallerDive(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.5);
		Quirks.setMeter(p, 10f);
		helper.assertTrue(!ExplosionMoves.howitzerCluster(p), "needs 20 Sweat");
		Quirks.setMeter(p, 60f);
		Zombie z = zombie(helper, 3.5, 2.0, 4.5, 200);
		helper.assertTrue(ExplosionMoves.howitzerCluster(p) && ExplosionMoves.howitzerKind(p) == 2, "it starts, no rise");
		helper.assertTrue(Explosion.val(p, Explosion.V_HOWITZER_DIVE) <= helper.getLevel().getGameTime(), "it dives at once");
		pump(helper, p);
		helper.runAfterDelay(ExplosionConfig.HCLUSTER_SPIN_TICKS + 3, () -> {
			helper.assertTrue(ExplosionMoves.howitzerKind(p) == 0, "it detonated");
			float t = taken(z, 200);
			helper.assertTrue(t >= 8f && t < ExplosionConfig.HOWITZER_DAMAGE * 1.4f, "a smaller blast, took " + t);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void howitzerAndGauntletNeedSweat(GameTestHelper helper) {
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 3.5);
		Quirks.setMeter(p, 25f);
		helper.assertTrue(!ExplosionMoves.howitzer(p), "Howitzer refused under 30");
		Quirks.setMeter(p, 45f);
		helper.assertTrue(!ExplosionMoves.gauntlet(p), "Gauntlet refused under 50");
		helper.assertTrue(Quirks.ready(p, ExplosionConfig.HOWITZER) && Quirks.ready(p, ExplosionConfig.GAUNTLET), "no cooldown spent");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void gauntletGrenadeFiresAllTheSweatDownTheLine(GameTestHelper helper) {
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
		helper.assertTrue(ExplosionMoves.gauntlet(p), "the pin is pulled");
		helper.assertTrue(Quirks.meter(p) <= 0.01f, "all his Sweat");
		helper.assertTrue(Explosion.charging(p), "the charge shows");
		pump(helper, p);
		helper.runAfterDelay(ExplosionConfig.GAUNTLET_CHARGE + 3, () -> {
			helper.assertTrue(!Explosion.charging(p), "fired");
			for (Zombie z : new Zombie[] { near, far }) {
				float t = taken(z, 200);
				helper.assertTrue(t >= ExplosionConfig.GAUNTLET_DAMAGE * 0.7f && t <= ExplosionConfig.GAUNTLET_DAMAGE * 1.4f + 0.6f,
						"each in the path hit once (28 x 1.4), took " + t);
			}
			helper.assertTrue(p.getDeltaMovement().z < -0.5, "the recoil throws him back: " + p.getDeltaMovement());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void coolOffDriesSweatAndEasesStrainEvenWhileStrained(GameTestHelper helper) {
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 3.5);
		Quirks.setMeter(p, 70f);
		ExplosionMoves.setStrain(p, 98f);
		ExplosionMoves.apShot(p); // full strain
		helper.assertTrue(Quirks.overloaded(p), "strained");
		ExplosionMoves.setStrain(p, 80f);
		float sweat = Quirks.meter(p);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		helper.assertTrue(Quirks.ready(p, ExplosionConfig.COOL_OFF), "a bare V press does nothing (not sneaking)");
		p.setShiftKeyDown(true);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		helper.assertTrue(!Quirks.ready(p, ExplosionConfig.COOL_OFF), "Shift+V works while strained");
		helper.assertTrue(Math.abs(Quirks.meter(p) - Math.max(0f, sweat - ExplosionConfig.COOL_SWEAT)) < 0.5f, "dried 40 Sweat");
		helper.assertTrue(Math.abs(ExplosionMoves.strain(p) - 30f) < 0.5f, "eased 50 strain: " + ExplosionMoves.strain(p));
		helper.succeed();
	}
}
