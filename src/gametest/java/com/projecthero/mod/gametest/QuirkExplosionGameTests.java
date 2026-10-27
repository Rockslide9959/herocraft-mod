package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkAbilityManager;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.explosion.Explosion;
import com.projecthero.mod.quirk.explosion.ExplosionConfig;
import com.projecthero.mod.quirk.explosion.ExplosionMeter;
import com.projecthero.mod.quirk.explosion.ExplosionMoves;
import com.projecthero.mod.quirk.explosion.ExplosionTerrain;
import com.projecthero.mod.quirk.PlusUltra;
import com.projecthero.mod.quirk.QuirkCombos;
import com.projecthero.mod.quirk.meter.QuirkMeters;
import net.minecraft.core.Direction;

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
 * v0.16.7 Explosion (Bakugo / Ground Zero), v0.17.1 Quirks revamp: one test per move -- the server effect and its number -- plus the custom
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

	// ---------------------------------------------------------------- v0.17.1: his blasts really break the terrain

	/** A test-only guard on the block-break event (a claim / protected structure stand-in): these cells may never break. */
	private static final java.util.Set<BlockPos> GUARDED = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static boolean guardRegistered;

	private static synchronized void guard(BlockPos pos) {
		if (!guardRegistered) {
			guardRegistered = true;
			net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((level, player, p, state, be) -> !GUARDED.contains(p));
		}
		GUARDED.add(pos.immutable());
	}

	private static void wall(GameTestHelper helper, int z, net.minecraft.world.level.block.Block block) {
		for (int x = 1; x <= 6; x++) {
			for (int y = 2; y <= 5; y++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)), block.defaultBlockState(), 3);
			}
		}
	}

	private static int count(GameTestHelper helper, int z, net.minecraft.world.level.block.Block block) {
		int n = 0;
		for (int x = 1; x <= 6; x++) {
			for (int y = 2; y <= 5; y++) {
				if (helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z))).is(block)) {
					n++;
				}
			}
		}
		return n;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void blastsBreakBlocksTheApShotTunnelsAndNeverAContainer(GameTestHelper helper) {
		floor(helper);
		wall(helper, 5, Blocks.STONE);
		BlockPos chest = helper.absolutePos(new BlockPos(3, 3, 5));
		helper.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.5);
		ExplosionTerrain.setBlockDamageForTest(true);
		ExplosionMoves.apShot(p);
		int left = count(helper, 5, Blocks.STONE);
		helper.assertTrue(left < 23, "the AP Shot blew a hole in the wall, stone left " + left);
		helper.assertTrue(helper.getLevel().getBlockState(chest).is(Blocks.CHEST), "never a container");
		helper.assertTrue(ExplosionTerrain.blow(p, helper.absoluteVec(new Vec3(5.5, 3.5, 5.5)), ExplosionTerrain.Tier.CRATER) > 0, "a crater breaks more");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bedrockAndObsidianSurviveTheBlasts(GameTestHelper helper) {
		floor(helper);
		wall(helper, 5, Blocks.OBSIDIAN);
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(3, 3, 5)), Blocks.BEDROCK.defaultBlockState(), 3);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.5);
		ExplosionTerrain.setBlockDamageForTest(true);
		ExplosionMoves.apShot(p);
		ExplosionTerrain.blow(p, helper.absoluteVec(new Vec3(3.5, 3.5, 5.0)), ExplosionTerrain.Tier.CRATER);
		ExplosionTerrain.blow(p, helper.absoluteVec(new Vec3(3.5, 3.5, 5.0)), ExplosionTerrain.Tier.HUGE);
		helper.assertTrue(count(helper, 5, Blocks.OBSIDIAN) == 23, "obsidian is too hard for any blast");
		helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(3, 3, 5));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aProtectedSpotIsLeftAlone(GameTestHelper helper) {
		floor(helper);
		wall(helper, 5, Blocks.DIRT);
		for (int x = 1; x <= 6; x++) {
			for (int y = 2; y <= 5; y++) {
				guard(helper.absolutePos(new BlockPos(x, y, 5)));
			}
		}
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.5);
		ExplosionTerrain.setBlockDamageForTest(true);
		ExplosionMoves.apShot(p);
		ExplosionTerrain.blow(p, helper.absoluteVec(new Vec3(3.5, 3.5, 5.0)), ExplosionTerrain.Tier.CRATER);
		helper.assertTrue(count(helper, 5, Blocks.DIRT) == 24, "the guarded wall is untouched (the break event said no)");
		helper.assertTrue(ExplosionMoves.blastMine(p, helper.absolutePos(new BlockPos(3, 3, 5)), Direction.NORTH) <= 0, "blast mining obeys it too");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theToggleOffMeansNoBlockDamage(GameTestHelper helper) {
		floor(helper);
		wall(helper, 5, Blocks.DIRT);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.5);
		ExplosionTerrain.setBlockDamageForTest(false);
		try {
			ExplosionMoves.apShot(p);
			ExplosionMoves.blastPunch(p);
			helper.assertTrue(ExplosionTerrain.blow(p, helper.absoluteVec(new Vec3(3.5, 3.5, 5.0)), ExplosionTerrain.Tier.HUGE) == 0, "nothing breaks");
			helper.assertTrue(count(helper, 5, Blocks.DIRT) == 24, "the wall is whole");
		} finally {
			ExplosionTerrain.setBlockDamageForTest(true);
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
		ExplosionMoves.setStrain(p, 99.5f); // v0.17.1: blast strain is paced (ExplosionMeter.BUILD)
		ExplosionMoves.apShot(p);
		helper.assertTrue(Quirks.overloaded(p), "Arm Strain");
		helper.assertTrue(Quirks.overloadLeft(p) >= ExplosionConfig.STRAIN_TICKS - 1, "for 6 s (arms shot)");
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
				&& Math.abs(ExplosionConfig.powerAt(100f) - ExplosionConfig.POWER_SOAKED) < 1e-4
				&& Math.abs(ExplosionConfig.powerAt(50f) - ExplosionConfig.POWER_PRIMED) < 1e-4 && ExplosionConfig.powerAt(29f) == ExplosionConfig.POWER_DRY
				&& ExplosionConfig.powerAt(80f) == ExplosionConfig.POWER_SOAKED, "v0.17.1 bands: Dry 0.6x, Primed 1.0x, Soaked 1.4x");
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

	/** v0.16.7 (user): the sweat charges on its own -- v0.17.1 (user): 2 a second (was 4), standing still out of a fight. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void sweatChargesTwoASecondOnItsOwn(GameTestHelper helper) {
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 3.5);
		Quirks.setMeter(p, 0f);
		pump(helper, p);
		helper.runAfterDelay(20, () -> {
			float m = Quirks.meter(p);
			helper.assertTrue(m > 1.6f && m < 3f, "about 2 sweat after a second standing still, got " + m);
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
		ExplosionMoves.setStrain(p, 99.5f); // v0.17.1: blast strain is paced (ExplosionMeter.BUILD)
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

	// ---------------------------------------------------------------- v0.17.1 Quirks revamp

	/** R, G and X have a tap, a Shift and a hold move; Z, C and V a tap and a Shift move. Every one has a cooldown. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyKeyHasItsTapShiftAndHoldMoves(GameTestHelper helper) {
		var kit = QuirkKits.of(Quirk.EXPLOSION);
		for (AbilitySlot slot : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4,
				AbilitySlot.SLOT_5, AbilitySlot.SLOT_6 }) {
			String[] ids = kit.idsOf(slot);
			boolean hold = slot == AbilitySlot.SLOT_1 || slot == AbilitySlot.SLOT_2 || slot == AbilitySlot.SLOT_3;
			helper.assertTrue(ids.length == (hold ? 3 : 2), slot + " has " + (hold ? "tap / Shift / hold" : "tap / Shift"));
			for (String id : ids) {
				helper.assertTrue(id.startsWith("quirk_explosion_") && kit.maxCooldown(id) > 0, id + " has a cooldown");
			}
		}
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_3)[0].equals(ExplosionConfig.DASH) && kit.idsOf(AbilitySlot.SLOT_3)[1].equals(ExplosionConfig.SPIN_DODGE)
				&& kit.idsOf(AbilitySlot.SLOT_3)[2].equals(ExplosionConfig.PROPULSION), "X (movement): Dash / Spin Dodge / Propulsion");
		helper.assertTrue(kit.idsOf(AbilitySlot.SLOT_5)[0].equals(ExplosionConfig.STUN_GRENADE) && kit.idsOf(AbilitySlot.SLOT_5)[1].equals(ExplosionConfig.COOL_OFF),
				"V (utility): Stun Grenade / Cool Off");
		helper.assertTrue(kit.usableWhileOverloaded(AbilitySlot.SLOT_5, true) && !kit.usableWhileOverloaded(AbilitySlot.SLOT_5, false),
				"only Cool Off works while the arms are shot");
		helper.assertTrue(kit.hasUltimate(), "C held at a full gauge is the ultimate");
		helper.succeed();
	}

	/** The heavy-use pacing script carries the new moves (the framework's 45-second test drives it). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theNewMovesAreInThePacingScript(GameTestHelper helper) {
		var h = QuirkMeters.spec(Quirk.EXPLOSION).heavyUse();
		helper.assertTrue(h.moves.size() == 11, "eleven costed moves in the rotation, got " + h.moves.size());
		boolean pierce = false, grip = false;
		for (float[] m : h.moves) {
			pierce |= m[0] == ExplosionConfig.PIERCE_STRAIN && m[1] == ExplosionConfig.PIERCE_COOLDOWN;
			grip |= m[0] == ExplosionConfig.GRIP_STRAIN && m[1] == ExplosionConfig.GRIP_COOLDOWN;
		}
		helper.assertTrue(pierce && grip, "Pierce and Crush Grip are in it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 30)
	public void aQuickRTapIsAnApShotOnTheRelease(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 0.5);
		Zombie z = zombie(helper, 3.5, 2.0, 6.5, 100);
		pump(helper, p);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(Quirks.ready(p, ExplosionConfig.AP_SHOT) && ExplosionMoves.pressPending(p, AbilitySlot.SLOT_1.ordinal()),
				"the press waits to see whether it is a tap or a hold");
		helper.runAfterDelay(2, () -> {
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
			helper.assertTrue(!Quirks.ready(p, ExplosionConfig.AP_SHOT), "released quickly: the AP Shot fired");
			helper.assertTrue(!ExplosionMoves.piercing(p) && Quirks.ready(p, ExplosionConfig.PIERCE), "no Pierce");
			helper.assertTrue(taken(z, 100) > 5f, "it landed, took " + taken(z, 100));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void holdingRChargesThePierceThroughALine(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 0.5);
		Zombie a = zombie(helper, 3.5, 2.0, 3.0, 100);
		Zombie b = zombie(helper, 3.5, 2.0, 5.5, 100);
		pump(helper, p);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		helper.runAfterDelay(ExplosionConfig.HOLD_TICKS + 2, () -> {
			helper.assertTrue(ExplosionMoves.piercing(p) && Explosion.piercing(p), "held past the tap window: the Pierce charges");
			helper.assertTrue(Explosion.pierceCharge(p, 0f) >= 0f, "the charge is synced (the R ring)");
		});
		// a full charge fires on its own
		helper.runAfterDelay(ExplosionConfig.HOLD_TICKS + ExplosionConfig.PIERCE_CHARGE_TICKS + 4, () -> {
			helper.assertTrue(!ExplosionMoves.piercing(p), "fired at full charge");
			float max = ExplosionConfig.PIERCE_MAX_DAMAGE;
			helper.assertTrue(taken(a, 100) >= max * 0.8f && taken(b, 100) >= max * 0.8f, "it pierced both (22 each): " + taken(a, 100) + ", " + taken(b, 100));
			helper.assertTrue(!Quirks.ready(p, ExplosionConfig.PIERCE) && Quirks.ready(p, ExplosionConfig.AP_SHOT), "the Pierce's cooldown, not the AP Shot's");
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
			helper.assertTrue(Quirks.ready(p, ExplosionConfig.AP_SHOT), "the release after a hold is not a tap");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void crushGripBlastsThreeTimesThenThrows(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.0);
		Zombie z = zombie(helper, 3.5, 2.0, 3.0, 100);
		pump(helper, p);
		helper.assertTrue(ExplosionMoves.crushGrip(p) == z && ExplosionMoves.gripping(p), "the face in reach is grabbed");
		helper.assertTrue(ExplosionMoves.busy(p), "nothing else while he holds it");
		helper.runAfterDelay(ExplosionConfig.GRIP_THROW_AT + 2, () -> {
			float sum = ExplosionConfig.GRIP_DAMAGE[0] + ExplosionConfig.GRIP_DAMAGE[1] + ExplosionConfig.GRIP_DAMAGE[2];
			helper.assertTrue(Math.abs(taken(z, 100) - sum) < 1.5f, "6 + 6 + 12, took " + taken(z, 100));
			helper.assertTrue(!ExplosionMoves.gripping(p), "then thrown");
			helper.assertTrue(z.getDeltaMovement().z > 0.2 || z.getZ() > helper.absoluteVec(new Vec3(0, 0, 3.2)).z, "thrown along his look: " + z.getDeltaMovement());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aBossTakesTheGripBlastsButIsNeverGrabbed(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.0);
		Zombie boss = zombie(helper, 3.5, 2.0, 3.0, 400);
		Vec3 at = boss.position();
		pump(helper, p);
		helper.assertTrue(ExplosionMoves.crushGrip(p) == boss, "the boss is the target");
		helper.runAfterDelay(ExplosionConfig.GRIP_THROW_AT + 2, () -> {
			helper.assertTrue(taken(boss, 400) > 15f, "it took the blasts: " + taken(boss, 400));
			helper.assertTrue(boss.position().distanceTo(at) < 0.6, "never lifted or thrown");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void nothingInReachIsAWhiffAndCostsNothing(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.0);
		float sweat = Quirks.meter(p);
		helper.assertTrue(ExplosionMoves.crushGrip(p) == null, "no target");
		helper.assertTrue(Quirks.meter(p) == sweat && ExplosionMoves.strain(p) == 0f, "nothing spent");
		helper.assertTrue(Quirks.cooldownRemaining(p, ExplosionConfig.CRUSH_GRIP) <= ExplosionConfig.GRIP_WHIFF_COOLDOWN, "only a short cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theDashGoesTheWayWasdPoints(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 3.5);
		ExplosionMoves.inputForTest(p, 0, 1); // A: his left (+X facing +Z)
		ExplosionMoves.blastDash(p);
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(v.x > 0.8 && Math.abs(v.z) < 0.3, "A held: he blasts to his left, " + v);
		ServerPlayer q = bakugo(helper, 1.5, 2.0, 1.5);
		ExplosionMoves.blastDash(q);
		helper.assertTrue(q.getDeltaMovement().z > 0.9, "no keys: the way he looks, " + q.getDeltaMovement());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void spinDodgeIsUntouchableForAMoment(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 3.5);
		Zombie z = zombie(helper, 3.5, 2.0, 5.5, 100);
		ExplosionMoves.inputForTest(p, 0, -1); // D: his right
		helper.assertTrue(ExplosionMoves.spinDodge(p) == 1, "D: a spin to his right");
		helper.assertTrue(p.getDeltaMovement().x < -0.4, "blasted to his right (-X), " + p.getDeltaMovement());
		helper.assertTrue(Explosion.dodging(p) && ExplosionMoves.invulnerable(p), "spinning, invulnerable");
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().mobAttack(z), 6f);
		helper.assertTrue(p.getHealth() == hp, "a hit in the dodge does nothing");
		pump(helper, p);
		helper.runAfterDelay(ExplosionConfig.DODGE_IFRAMES + 2, () -> {
			helper.assertTrue(!ExplosionMoves.invulnerable(p), "only for a moment");
			p.invulnerableTime = 0;
			p.hurt(helper.getLevel().damageSources().mobAttack(z), 6f);
			helper.assertTrue(p.getHealth() < hp, "then he can be hit again");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void holdingXFliesAndATapDashes(GameTestHelper helper) {
		ServerPlayer p = bakugo(helper, 3.5, 4.0, 3.5);
		ServerPlayer q = bakugo(helper, 1.5, 4.0, 1.5);
		pump(helper, p);
		pump(helper, q);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		QuirkAbilityManager.handle(q, AbilitySlot.SLOT_3, true);
		helper.runAfterDelay(1, () -> QuirkAbilityManager.handle(q, AbilitySlot.SLOT_3, false));
		helper.runAfterDelay(ExplosionConfig.HOLD_TICKS + 3, () -> {
			helper.assertTrue(ExplosionMoves.propelling(p) && Explosion.propelling(p), "held: Explosion Propulsion");
			helper.assertTrue(QuirkKits.of(Quirk.EXPLOSION).active(p, AbilitySlot.SLOT_3), "the HUD box is lit");
			helper.assertTrue(!ExplosionMoves.propelling(q) && !Quirks.ready(q, ExplosionConfig.DASH), "tapped: the Dash, no flight");
		});
		helper.runAfterDelay(ExplosionConfig.HOLD_TICKS + 20, () -> {
			helper.assertTrue(Quirks.meter(p) < 50f, "the flight burns Sweat: " + Quirks.meter(p));
			QuirkAbilityManager.handle(p, AbilitySlot.SLOT_3, false);
			helper.assertTrue(!Explosion.propelling(p), "let go: it ends");
			helper.assertTrue(!Quirks.ready(p, ExplosionConfig.PROPULSION) && Quirks.ready(p, ExplosionConfig.DASH), "the flight's cooldown, no Dash");
			helper.assertTrue(!p.getAbilities().mayfly, "the float permission is gone again");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theUppercutLaunchesTheTargetAndHimThenTheSkyShotRoute(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.0);
		Zombie z = zombie(helper, 3.5, 2.0, 3.0, 100);
		helper.assertTrue(ExplosionMoves.uppercut(p) == z, "it hit the one in front");
		helper.assertTrue(Math.abs(taken(z, 100) - ExplosionConfig.UPPERCUT_DAMAGE) < 0.8f, "12, took " + taken(z, 100));
		helper.assertTrue(z.getDeltaMovement().y > 0.8, "launched: " + z.getDeltaMovement());
		helper.assertTrue(p.getDeltaMovement().y > 0.8 && ExplosionMoves.softLanding(p), "and he rockets up after it");
		ExplosionMoves.apShot(p);
		helper.assertTrue(QuirkCombos.last(p) == ExplosionMeter.SKY_SHOT, "Uppercut then AP Shot: the Sky Shot route");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stunGrenadeThenApShotIsTheFlashbangSnipe(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 6.5, 2.0, 0.5);
		ServerPlayer q = bakugo(helper, 0.5, 2.0, 0.5);
		Zombie a = zombie(helper, 6.5, 2.0, 6.5, 200);
		Zombie b = zombie(helper, 0.5, 2.0, 6.5, 200);
		ExplosionMoves.stunGrenade(p);
		float stun = taken(a, 200), bStun = taken(b, 200);
		ExplosionMoves.apShot(p);
		helper.assertTrue(QuirkCombos.last(p) == ExplosionMeter.STUN_SHOT, "the route landed");
		ExplosionMoves.apShot(q);
		float crit = taken(a, 200) - stun, plain = taken(b, 200) - bStun;
		helper.assertTrue(crit > plain * 1.35f, "the shot after the flash hits 1.5x: " + crit + " vs " + plain);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void strainedArmsHitHarderButCostHealth(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 6.5, 2.0, 0.5);
		ServerPlayer q = bakugo(helper, 0.5, 2.0, 0.5);
		Zombie a = zombie(helper, 6.5, 2.0, 6.5, 100);
		Zombie b = zombie(helper, 0.5, 2.0, 6.5, 100);
		ExplosionMoves.setStrain(p, 75f);
		ExplosionMoves.apShot(p);
		ExplosionMoves.apShot(q);
		helper.assertTrue(taken(a, 100) > taken(b, 100) * 1.1f, "past 70 strain the blast is +15%: " + taken(a, 100) + " vs " + taken(b, 100));
		helper.assertTrue(p.getHealth() <= p.getMaxHealth() - ExplosionConfig.STRAIN_HOT_HP + 0.01f, "and it costs half a heart");
		helper.assertTrue(q.getHealth() >= q.getMaxHealth(), "cool arms cost nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theGauntletsBankAGrenade(GameTestHelper helper) {
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 3.5);
		Quirks.setMeter(p, 10f);
		helper.assertTrue(!ExplosionMoves.gauntlet(p), "10 Sweat and no bank: refused");
		ExplosionMoves.bankForTest(p, ExplosionConfig.BANK_MAX);
		helper.assertTrue(ExplosionMoves.gauntlet(p), "the full bank fires it");
		helper.assertTrue(ExplosionMoves.bank(p) == 0f, "the bank is spent");
		helper.assertTrue(Quirks.meter(p) >= 9.9f, "his own Sweat untouched");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aLongFallIsBrokenByALandingBlast(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 3.5);
		Quirks.setMeter(p, 50f);
		p.fallDistance = 12f;
		helper.assertTrue(ExplosionMoves.cancelsDamage(p, helper.getLevel().damageSources().fall()), "a blast under him breaks the fall");
		helper.assertTrue(Math.abs(Quirks.meter(p) - (50f - ExplosionConfig.LANDING_SWEAT)) < 0.5f, "10 Sweat");
		ServerPlayer q = bakugo(helper, 1.5, 2.0, 1.5);
		Quirks.setMeter(q, 5f);
		q.fallDistance = 12f;
		helper.assertTrue(!ExplosionMoves.cancelsDamage(q, helper.getLevel().damageSources().fall()), "too dry: he takes the fall");
		ServerPlayer r = bakugo(helper, 5.5, 2.0, 5.5);
		r.fallDistance = 5f;
		helper.assertTrue(!ExplosionMoves.cancelsDamage(r, helper.getLevel().damageSources().fall()) && Quirks.meter(r) == 50f, "a short fall: nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void blastMiningBreaksAThreeByThreeOfSoftStone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		for (int x = 2; x <= 4; x++) {
			for (int y = 2; y <= 4; y++) {
				level.setBlock(helper.absolutePos(new BlockPos(x, y, 5)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		level.setBlock(helper.absolutePos(new BlockPos(4, 4, 5)), Blocks.OBSIDIAN.defaultBlockState(), 3);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 2.5);
		int n = ExplosionMoves.blastMine(p, helper.absolutePos(new BlockPos(3, 3, 5)), Direction.NORTH);
		helper.assertTrue(n == 8, "the 3x3 broke but the obsidian, got " + n);
		helper.assertBlockPresent(Blocks.OBSIDIAN, new BlockPos(4, 4, 5));
		helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(3, 3, 5));
		helper.assertTrue(Math.abs(Quirks.meter(p) - (50f - ExplosionConfig.MINING_SWEAT)) < 0.01f, "5 Sweat");
		helper.assertTrue(ExplosionMoves.blastMine(p, helper.absolutePos(new BlockPos(2, 2, 5)), Direction.NORTH) == -1, "a short cooldown between blasts");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theUltimateMarksChainsThroughEachAndShootsTheArms(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.0);
		Zombie a = zombie(helper, 1.5, 2.0, 5.0, 200);
		Zombie b = zombie(helper, 5.5, 2.0, 5.0, 200);
		Zombie c = zombie(helper, 3.5, 2.0, 7.0, 200);
		PlusUltra.set(p, 100f);
		PlusUltra.fire(p);
		helper.assertTrue(PlusUltra.firedCount(p) == 1 && PlusUltra.gauge(p) == 0f, "it fired and spent the gauge");
		helper.assertTrue(ExplosionMoves.howitzerKind(p) == 2 && Explosion.ultimate(p), "Howitzer Impact: Cluster is running");
		// the marking reaches 20 blocks -- past this 8x8 cage into the neighbouring tests, so on CI a stray creature there could
		// be marked as well (got [8, 9, 10, 3]): what matters is that all three of these are marked
		var marked = ExplosionMoves.ultimateTargets(p);
		helper.assertTrue(marked.containsAll(java.util.List.of(a.getId(), b.getId(), c.getId())) && marked.size() <= ExplosionConfig.ULT_TARGETS,
				"it marked all three, " + marked);
		helper.assertTrue(a.hasEffect(MobEffects.GLOWING), "the marks glow");
		ExplosionMoves.finishUltimateForTest(p);
		for (Zombie z : new Zombie[] { a, b, c }) {
			helper.assertTrue(taken(z, 200) >= ExplosionConfig.ULT_TARGET_DAMAGE, "each took the dive and the blast: " + taken(z, 200));
		}
		helper.assertTrue(ExplosionMoves.howitzerKind(p) == 0, "it ended");
		helper.assertTrue(Quirks.overloaded(p) && p.hasEffect(MobEffects.WEAKNESS), "the drawback: his arms are shot");
		helper.assertTrue(p.getHealth() >= p.getMaxHealth(), "his own blasts never touch him");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void holdingCAtAFullGaugeFiresTheHowitzerCluster(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = bakugo(helper, 3.5, 2.0, 1.0);
		zombie(helper, 3.5, 2.0, 6.0, 200);
		PlusUltra.set(p, 100f);
		pump(helper, p);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		helper.runAfterDelay(PlusUltra.HOLD_TICKS + 2, () -> {
			helper.assertTrue(PlusUltra.firedCount(p) == 1, "the hold fired the ultimate");
			helper.assertTrue(ExplosionMoves.howitzerKind(p) == 2, "the kit's ultimate (not the placeholder)");
			helper.assertTrue(Quirks.ready(p, ExplosionConfig.HOWITZER), "the C move itself never fired");
			helper.succeed();
		});
	}
}
