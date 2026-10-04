package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.darkseid.DarkseidConfig;
import com.projecthero.mod.darkseid.entity.DarkseidCombat;
import com.projecthero.mod.darkseid.entity.DarkseidEntity;
import com.projecthero.mod.darkseid.entity.DarkseidEntityTypes;
import com.projecthero.mod.darkseid.entity.MotherBoxEntity;
import com.projecthero.mod.darkseid.entity.OmegaBeamEntity;
import com.projecthero.mod.darkseid.entity.ParademonBoltEntity;
import com.projecthero.mod.darkseid.entity.ParademonEntity;
import com.projecthero.mod.darkseid.raid.DarkseidRaid;
import com.projecthero.mod.event.EventSavedData;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.18: coverage for the Darkseid Raid's pieces. The full raid is deliberately never run here (its Boom Tubes
 * would pour Parademons into the neighbouring tests); each test drives one piece directly -- the shield, the
 * phase/health rules, the Omega Beam and cover, the Omega Annihilation interrupt, the Parademon variants, the
 * orphan guards, activation + roster + cleanup, and save/load. v0.13.19 adds: five waves (and old saves), the v1 -> v2
 * config migration, tougher Parademons, the zig-zag Omega Beam, gunners strafing while they shoot, and the Mother Boxes
 * coming back online mid-fight.
 */
public class DarkseidRaidGameTests implements FabricGameTest {
	private static DarkseidEntity darkseid(GameTestHelper helper, Vec3 rel) {
		DarkseidEntity d = DarkseidEntityTypes.DARKSEID.create(helper.getLevel());
		helper.assertTrue(d != null, "Darkseid constructs");
		Vec3 at = helper.absoluteVec(rel);
		d.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(d);
		return d;
	}

	private static ServerPlayer survivor(GameTestHelper helper, Vec3 rel) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(rel);
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void healthScalesWithParticipantsAsTheSpecTableSays(GameTestHelper helper) {
		double[] expected = { 3000, 3600, 4200, 4800, 5400, 6000, 6600, 7200 };
		for (int n = 1; n <= 8; n++) {
			helper.assertTrue(Math.abs(DarkseidConfig.healthFor(n) - expected[n - 1]) < 0.01,
					n + " participants should give " + expected[n - 1] + ", got " + DarkseidConfig.healthFor(n));
		}
		DarkseidEntity d = darkseid(helper, new Vec3(2, 2, 2));
		d.scaleHealthTo(DarkseidConfig.healthFor(4));
		helper.assertTrue(Math.abs(d.getMaxHealth() - 4800) < 0.01, "max health above vanilla's 1024 cap, got " + d.getMaxHealth());
		d.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void motherBoxShieldBlocksAllDamageUntilItFalls(GameTestHelper helper) {
		DarkseidEntity d = darkseid(helper, new Vec3(2, 2, 2));
		d.setPhaseImmediate(0);
		d.setShield(0.25f);
		float before = d.getHealth();
		helper.assertFalse(d.hurt(d.damageSources().generic(), 200.0f), "a shielded Darkseid refuses the hit");
		helper.assertTrue(d.getHealth() == before, "and loses nothing");
		d.setPhaseImmediate(1);
		d.setShield(0.0f);
		helper.assertTrue(d.hurt(d.damageSources().generic(), 200.0f), "the shield is down: the hit lands");
		helper.assertTrue(d.getHealth() < before, "and hurts him");
		d.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void reactivatedBoxesReduceDamageOutsideTheShieldPhase(GameTestHelper helper) {
		DarkseidEntity plain = darkseid(helper, new Vec3(1, 2, 1));
		DarkseidEntity boxed = darkseid(helper, new Vec3(6, 2, 6));
		plain.setPhaseImmediate(2);
		boxed.setPhaseImmediate(2);
		boxed.setShield(0.5f);
		float p0 = plain.getHealth();
		float b0 = boxed.getHealth();
		plain.hurt(plain.damageSources().generic(), 200.0f);
		boxed.hurt(boxed.damageSources().generic(), 200.0f);
		float lostPlain = p0 - plain.getHealth();
		float lostBoxed = b0 - boxed.getHealth();
		helper.assertTrue(lostBoxed > 0.0f, "a reactivated box does not make him immune outside the Mother Box phase");
		helper.assertTrue(Math.abs(lostBoxed - lostPlain * 0.5f) < 1.0f,
				"two boxes back up halve the damage: plain " + lostPlain + ", with boxes " + lostBoxed);
		plain.discard();
		boxed.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void omegaBeamHitsInTheOpenButNotThroughCover(GameTestHelper helper) {
		var level = helper.getLevel();
		Pig open = helper.spawn(EntityType.PIG, new Vec3(1.5, 2, 6.5));
		open.setNoAi(true);
		Pig hidden = helper.spawn(EntityType.PIG, new Vec3(4.5, 2, 6.5));
		hidden.setNoAi(true);
		// a wall between the second beam and its target
		for (int x = 3; x <= 6; x++) {
			for (int y = 1; y <= 5; y++) {
				helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
			}
		}
		float openBefore = open.getHealth();
		float hiddenBefore = hidden.getHealth();
		Vec3 a = helper.absoluteVec(new Vec3(1.5, 2.8, 1.5));
		Vec3 b = helper.absoluteVec(new Vec3(4.5, 2.8, 1.5));
		OmegaBeamEntity.fire(level, null, a, new Vec3(0, 0, 1), open, 1.0f, 0.8, 10.0, 40);
		OmegaBeamEntity.fire(level, null, b, new Vec3(0, 0, 1), hidden, 1.0f, 0.8, 10.0, 40);
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(open.getHealth() < openBefore || !open.isAlive(), "the beam in the open strikes its target");
			helper.assertTrue(hidden.getHealth() == hiddenBefore && hidden.isAlive(), "the wall stops the other beam");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void dealingEnoughDamageInterruptsOmegaAnnihilation(GameTestHelper helper) {
		DarkseidEntity d = darkseid(helper, new Vec3(2, 2, 2));
		ServerPlayer p = survivor(helper, new Vec3(6, 2, 6));
		// mock players always report creative (never raid targets), so the mark goes on a pig
		Pig marked = helper.spawn(EntityType.PIG, new Vec3(7, 2, 2));
		marked.setNoAi(true);
		d.setPhaseImmediate(3);
		helper.runAfterDelay(2, () -> {
			boolean started = d.combat().forceAttack(helper.getLevel(), DarkseidCombat.Attack.OMEGA_ANNIHILATION, marked);
			helper.assertTrue(started, "he begins Omega Annihilation");
			helper.assertTrue(d.combat().isChargingAnnihilation(), "and is charging it");
			float need = (float) (d.getMaxHealth() * DarkseidConfig.abilities().omegaAnnihilationInterruptFraction);
			for (int i = 0; i < 6 && d.combat().isChargingAnnihilation(); i++) {
				d.invulnerableTime = 0;
				d.hurt(d.damageSources().playerAttack(p), need);
			}
			helper.assertFalse(d.combat().isChargingAnnihilation(), "the team's damage interrupted it");
			helper.assertTrue(d.combat().isStaggered(), "and staggered him");
			d.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void parademonVariantsHaveTheirOwnStats(GameTestHelper helper) {
		ParademonEntity brute = DarkseidEntityTypes.PARADEMON.create(helper.getLevel());
		ParademonEntity ranged = DarkseidEntityTypes.PARADEMON.create(helper.getLevel());
		brute.setup(ParademonEntity.Variant.BRUTE, null);
		ranged.setup(ParademonEntity.Variant.RANGED, null);
		helper.assertTrue(brute.getMaxHealth() == (float) DarkseidConfig.parademons().bruteHealth, "brute health");
		helper.assertTrue(brute.getBbHeight() > ranged.getBbHeight() * 1.3f, "the brute is bigger, got " + brute.getBbHeight());
		helper.assertFalse(ParademonEntity.Variant.BRUTE.canFly(), "brutes cannot fly");
		helper.assertTrue(ParademonEntity.Variant.RANGED.canFly(), "gunners can");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void raidEntitiesRemoveThemselvesWhenTheirRaidIsGone(GameTestHelper helper) {
		var level = helper.getLevel();
		UUID ghostRaid = UUID.randomUUID();
		MotherBoxEntity box = MotherBoxEntity.spawn(level, helper.absoluteVec(new Vec3(2, 2, 2)), ghostRaid, 0);
		DarkseidEntity d = darkseid(helper, new Vec3(5, 2, 5));
		d.bindToRaid(ghostRaid);
		ParademonEntity pd = DarkseidEntityTypes.PARADEMON.create(level);
		pd.setup(ParademonEntity.Variant.STANDARD, ghostRaid);
		Vec3 at = helper.absoluteVec(new Vec3(2, 2, 5));
		pd.moveTo(at.x, at.y, at.z);
		level.addFreshEntity(pd);
		helper.runAfterDelay(85, () -> {
			helper.assertTrue(box.isRemoved(), "the orphaned Mother Box removed itself");
			helper.assertTrue(d.isRemoved(), "the orphaned Darkseid removed himself");
			helper.assertTrue(pd.isRemoved(), "the orphaned Parademon removed itself");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void activationStartsInPreparationRefusesCreativeAndAbortCleansUp(GameTestHelper helper) {
		var level = helper.getLevel();
		// vanilla mock players always report isCreative() -- exactly the kind of player the roster must refuse
		ServerPlayer p = survivor(helper, new Vec3(3, 2, 3));
		DarkseidRaid raid = DarkseidRaid.startAt(level, BlockPos.containing(helper.absoluteVec(new Vec3(2, 2, 2))), p);
		try {
			helper.assertTrue(raid != null, "the raid starts");
			helper.assertFalse(raid.isParticipant(p.getUUID()), "a creative player is never a participant");
			helper.assertTrue(raid.stage() == DarkseidRaid.Stage.PREPARATION, "it begins in PREPARATION, got " + raid.stage());
			helper.assertTrue(DarkseidRaid.find(level, raid.id()) == raid, "it can be found by id");
			helper.assertTrue(DarkseidRaid.startAt(level, BlockPos.containing(helper.absoluteVec(new Vec3(4, 2, 4))), p) == null,
					"a second invasion right beside it is refused");
		} finally {
			if (raid != null) {
				raid.abort(level);
				EventSavedData.get(level).remove(raid.id());
			}
		}
		helper.assertTrue(DarkseidRaid.find(level, raid.id()) == null, "after an abort nothing of it remains");
		helper.succeed();
	}

	// ================================================================ v0.13.19

	@GameTest(template = EMPTY_STRUCTURE)
	public void fiveInvasionWavesEachBiggerThanBefore(GameTestHelper helper) {
		helper.assertTrue(DarkseidRaid.Stage.MAX_WAVES == 5, "five waves at most");
		helper.assertTrue(DarkseidRaid.waveCount() == 5, "five waves by default, got " + DarkseidRaid.waveCount());
		for (int n = 1; n <= 5; n++) {
			DarkseidRaid.Stage s = DarkseidRaid.Stage.wave(n);
			helper.assertTrue(s.isWave() && s.waveNumber() == n, "wave " + n + " maps to " + s);
			helper.assertFalse(s.isFight(), "a wave is not the fight");
		}
		helper.assertTrue(DarkseidRaid.Stage.INVASION_WAVE_5.ordinal() < DarkseidRaid.Stage.DARKSEID_ENTRANCE.ordinal(),
				"the new waves come before Darkseid");
		// v0.13.18 solo totals were 8 / 11 / 15; every old wave now has at least 25% more, and it keeps ramping
		int[] old = { 8, 11, 15 };
		int prev = 0;
		for (int n = 1; n <= 5; n++) {
			int[] mix = DarkseidRaid.waveComposition(n);
			int total = mix[0] + mix[1] + mix[2] + mix[3];
			helper.assertTrue(total > prev, "wave " + n + " (" + total + ") outnumbers wave " + (n - 1) + " (" + prev + ")");
			if (n <= 3) {
				helper.assertTrue(total >= old[n - 1] * 1.25, "wave " + n + " has 25%+ more Parademons than before, got " + total);
			}
			prev = total;
		}
		int[] w5 = DarkseidRaid.waveComposition(5);
		helper.assertTrue(w5[2] > 0 && w5[3] > 0 && w5[1] > 0, "the last wave brings elites, brutes and gunners");
		helper.assertTrue(DarkseidConfig.raid().enemyCap >= 30, "the enemy cap was raised, got " + DarkseidConfig.raid().enemyCap);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void newWaveStagesSaveAndOldSavesStillLoad(GameTestHelper helper) {
		for (DarkseidRaid.Stage s : new DarkseidRaid.Stage[] { DarkseidRaid.Stage.INVASION_WAVE_3, DarkseidRaid.Stage.INVASION_WAVE_4,
				DarkseidRaid.Stage.INVASION_WAVE_5, DarkseidRaid.Stage.DARKSEID_PHASE_2 }) {
			DarkseidRaid raid = new DarkseidRaid(UUID.randomUUID());
			raid.debugSetStage(s);
			CompoundTag tag = raid.save();
			helper.assertTrue(s.name().equals(findStage(tag)), "the stage is saved by name: " + s + ", found " + findStage(tag));
			DarkseidRaid copy = new DarkseidRaid(raid.id());
			copy.load(tag);
			helper.assertTrue(copy.stage() == s, "stage " + s + " reloads, got " + copy.stage());
			helper.assertFalse(copy.motherBoxesWaking(), "no Mother Box warning out of nowhere");
		}
		helper.succeed();
	}

	/** The saved stage name, wherever the framework nests the raid's own keys. */
	private static String findStage(CompoundTag tag) {
		if (tag.contains("Stage")) {
			return tag.getString("Stage");
		}
		for (String k : tag.getAllKeys()) {
			if (tag.get(k) instanceof CompoundTag inner) {
				String s = findStage(inner);
				if (s != null) {
					return s;
				}
			}
		}
		return null;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aVersionOneConfigFileMigratesToTheNewBalance(GameTestHelper helper) {
		DarkseidConfig c = DarkseidConfig.migrateForTest("{\"configVersion\":1,"
				+ "\"raid\":{\"enemyCap\":26,\"wave1Standard\":8,\"wave3Elite\":4,\"maxParticipants\":6},"
				+ "\"boss\":{\"globalCooldownTicks\":30,\"baseDarkseidHealth\":4000.0},"
				+ "\"abilities\":{\"omegaBeamCooldown\":220,\"reinforcementCooldown\":520,\"omegaBeamDamage\":20.0},"
				+ "\"parademons\":{\"standardHealth\":30.0,\"bruteDamage\":15.0}}");
		helper.assertTrue(c.configVersion == 4, "migrated to v4, got " + c.configVersion);
		helper.assertTrue(c.raid.enemyCap == 34 && c.raid.wave1Standard == 11 && c.raid.wave3Elite == 5, "wave numbers moved");
		helper.assertTrue(c.raid.invasionWaves == 5 && c.raid.wave5Brute > 0, "new keys arrive at their defaults");
		helper.assertTrue(c.boss.globalCooldownTicks == 19, "shared cooldown moved, got " + c.boss.globalCooldownTicks);
		helper.assertTrue(c.abilities.omegaBeamCooldown == 140 && c.abilities.reinforcementCooldown == 400, "beam/reinforcement cooldowns moved");
		helper.assertTrue(Math.abs(c.parademons.standardHealth - 30.0) < 1e-6 && Math.abs(c.parademons.bruteDamage - 15.0) < 1e-6,
				"Parademons at their (v0.13.21 restored) original strength");
		helper.assertTrue(c.raid.maxParticipants == 6 && Math.abs(c.boss.baseDarkseidHealth - 4000.0) < 1e-6
				&& Math.abs(c.abilities.omegaBeamDamage - 20.0f) < 1e-6, "keys this pass did not touch keep the file's values");
		helper.assertTrue(c.motherBoxes.fightReactivateMinSeconds == 70 && c.motherBoxes.fightReactivateMaxSeconds == 100,
				"a missing section lands at its defaults");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aVersionTwoConfigFileGetsTheOriginalParademons(GameTestHelper helper) {
		DarkseidConfig c = DarkseidConfig.migrateForTest("{\"configVersion\":2,"
				+ "\"raid\":{\"enemyCap\":34,\"wave1Standard\":11},"
				+ "\"parademons\":{\"standardHealth\":36.0,\"standardDamage\":6.9,\"bruteHealth\":120.0,\"rangedBoltDamage\":5.75}}");
		helper.assertTrue(c.configVersion == 4, "migrated to v4, got " + c.configVersion);
		helper.assertTrue(Math.abs(c.parademons.standardHealth - 30.0) < 1e-6 && Math.abs(c.parademons.standardDamage - 6.0) < 1e-6
				&& Math.abs(c.parademons.bruteHealth - 100.0) < 1e-6 && Math.abs(c.parademons.rangedBoltDamage - 5.0f) < 1e-6,
				"Parademon stats back to v0.13.18");
		helper.assertTrue(c.raid.enemyCap == 34 && c.raid.wave1Standard == 11, "wave numbers untouched");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void parademonsAreBackToTheirOriginalStrength(GameTestHelper helper) {
		DarkseidConfig.Parademons cfg = DarkseidConfig.parademons();
		helper.assertTrue(cfg.standardHealth == 30.0 && cfg.eliteHealth == 60.0 && cfg.bruteHealth == 100.0
				&& cfg.rangedHealth == 24.0, "v0.13.18 health");
		helper.assertTrue(cfg.standardDamage == 6.0 && cfg.eliteDamage == 10.0 && cfg.bruteDamage == 15.0
				&& cfg.rangedBoltDamage == 5.0f, "v0.13.18 damage");
		ParademonEntity elite = DarkseidEntityTypes.PARADEMON.create(helper.getLevel());
		elite.setup(ParademonEntity.Variant.ELITE, null);
		helper.assertTrue(Math.abs(elite.getMaxHealth() - (float) cfg.eliteHealth) < 0.01f, "setup applies the new health");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void zigZagOmegaBeamTakesSharpTurns(GameTestHelper helper) {
		var level = helper.getLevel();
		Pig target = helper.spawn(EntityType.PIG, new Vec3(1.5, 2, 1.5));
		target.setNoAi(true);
		Vec3 from = helper.absoluteVec(new Vec3(1.5, 26, 1.5));
		double startDist = from.distanceTo(target.position());
		OmegaBeamEntity beam = OmegaBeamEntity.fire(level, null, from, new Vec3(0.2, -1, 0), target, 1.0f, 0.95, 8.0, 40, 4);
		helper.assertTrue(beam.isZigZagging(), "a zig-zag beam starts in its zig-zag");
		List<Vec3> velocities = new ArrayList<>();
		double[] closest = { startDist };
		helper.onEachTick(() -> {
			if (!beam.isRemoved()) {
				velocities.add(beam.getDeltaMovement());
				closest[0] = Math.min(closest[0], beam.position().distanceTo(target.position()));
			}
		});
		helper.runAfterDelay(30, () -> {
			int sharp = 0;
			for (int i = 1; i < velocities.size(); i++) {
				Vec3 a = velocities.get(i - 1);
				Vec3 b = velocities.get(i);
				if (a.lengthSqr() > 1e-6 && b.lengthSqr() > 1e-6
						&& Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, a.normalize().dot(b.normalize()))))) > 25.0) {
					sharp++;
				}
			}
			helper.assertTrue(sharp >= 2, "the beam snapped to a new heading at least twice, got " + sharp + " over "
					+ velocities.size() + " ticks");
			helper.assertTrue(closest[0] < startDist - 8.0, "and still closed in on its target (" + startDist + " -> " + closest[0] + ")");
			beam.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void rangedParademonStrafesWhileItShoots(GameTestHelper helper) {
		var level = helper.getLevel();
		// both inside the test's own area (outside it, line of sight is blocked)
		Pig target = helper.spawn(EntityType.PIG, new Vec3(6.5, 2, 6.5));
		target.setNoAi(true);
		ParademonEntity gunner = DarkseidEntityTypes.PARADEMON.create(level);
		gunner.setup(ParademonEntity.Variant.RANGED, null);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
		gunner.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		level.addFreshEntity(gunner);
		gunner.setTarget(target);
		boolean[] fired = { false };
		double[] travelled = { 0.0 };
		Vec3[] last = { gunner.position() };
		helper.onEachTick(() -> {
			if (gunner.getTarget() != target && target.isAlive()) {
				gunner.setTarget(target);
			}
			Vec3 p = gunner.position();
			travelled[0] += Math.sqrt((p.x - last[0].x) * (p.x - last[0].x) + (p.z - last[0].z) * (p.z - last[0].z));
			last[0] = p;
			if (!fired[0] && !level.getEntitiesOfClass(ParademonBoltEntity.class, new AABB(p, p).inflate(24.0)).isEmpty()) {
				fired[0] = true;
			}
		});
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(fired[0], "the gunner fired (removed=" + gunner.isRemoved() + ", target=" + gunner.getTarget()
					+ ", travelled=" + travelled[0] + ", dist=" + gunner.distanceTo(target) + ", difficulty=" + level.getDifficulty()
					+ ", los=" + gunner.hasLineOfSight(target) + ", pigHp=" + target.getHealth() + ")");
			helper.assertTrue(travelled[0] > 2.0, "and kept moving while it did, travelled " + travelled[0]);
			gunner.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void motherBoxesComeBackDuringTheFightAfterAWarning(GameTestHelper helper) {
		var level = helper.getLevel();
		DarkseidConfig.MotherBoxes cfg = DarkseidConfig.motherBoxes();
		double oldDistance = cfg.distanceFromCenter;
		cfg.distanceFromCenter = 3.0; // keep the four boxes inside this test's loaded area
		DarkseidRaid raid = DarkseidRaid.startAt(level, BlockPos.containing(helper.absoluteVec(new Vec3(4, 2, 4))), null);
		try {
			helper.assertTrue(raid != null, "the raid starts");
			int warn = cfg.fightReactivateWarningSeconds * 20;
			int min = Math.min(cfg.fightReactivateMinSeconds, cfg.fightReactivateMaxSeconds) * 20;
			int max = Math.max(cfg.fightReactivateMinSeconds, cfg.fightReactivateMaxSeconds) * 20;
			// phase 3: two boxes come back
			raid.debugEnterFight(level, 3);
			helper.assertTrue(raid.activeMotherBoxes() == 0, "every box starts dark, got " + raid.activeMotherBoxes());
			raid.debugTickBoxReturn(level, min - 20);
			helper.assertFalse(raid.motherBoxesWaking(), "nothing before the minimum delay");
			raid.debugTickBoxReturn(level, max - min + 40);
			helper.assertTrue(raid.motherBoxesWaking(), "a warning once the delay runs out");
			helper.assertTrue(raid.activeMotherBoxes() == 0, "the warning comes first -- no box is on yet");
			raid.debugTickBoxReturn(level, warn + 10);
			helper.assertFalse(raid.motherBoxesWaking(), "the warning ends");
			helper.assertTrue(raid.activeMotherBoxes() == cfg.fightReactivateBoxesPhase3,
					"phase 3 wakes " + cfg.fightReactivateBoxesPhase3 + ", got " + raid.activeMotherBoxes());
			raid.debugTickBoxReturn(level, max * 3);
			helper.assertFalse(raid.motherBoxesWaking(), "the clock does not run while a box is on");
			// phase 1: one box
			raid.debugEnterFight(level, 1);
			helper.assertTrue(raid.activeMotherBoxes() == 0, "all dark again");
			raid.debugTickBoxReturn(level, max + 20);
			helper.assertTrue(raid.motherBoxesWaking(), "phase 1 warns too");
			raid.debugTickBoxReturn(level, warn + 10);
			helper.assertTrue(raid.activeMotherBoxes() == cfg.fightReactivateBoxesPhase1,
					"phase 1 wakes " + cfg.fightReactivateBoxesPhase1 + ", got " + raid.activeMotherBoxes());
		} finally {
			cfg.distanceFromCenter = oldDistance;
			if (raid != null) {
				raid.abort(level);
				EventSavedData.get(level).remove(raid.id());
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rosterSurvivesSaveAndLoad(GameTestHelper helper) {
		DarkseidRaid raid = new DarkseidRaid(UUID.randomUUID());
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		raid.roster().add(a, "Alpha", true);
		raid.roster().add(b, "Beta", false).deaths = 2;
		CompoundTag tag = raid.save();
		DarkseidRaid copy = new DarkseidRaid(raid.id());
		copy.load(tag);
		helper.assertTrue(copy.roster().size() == 2, "both members reload");
		helper.assertTrue(copy.roster().get(a).original, "Alpha was an original participant");
		helper.assertFalse(copy.roster().get(b).original, "Beta joined later");
		helper.assertTrue(copy.roster().get(b).deaths == 2, "deaths persist");
		helper.succeed();
	}
}
