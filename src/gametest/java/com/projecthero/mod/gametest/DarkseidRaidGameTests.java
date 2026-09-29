package com.projecthero.mod.gametest;

import java.util.UUID;

import com.projecthero.mod.darkseid.DarkseidConfig;
import com.projecthero.mod.darkseid.entity.DarkseidCombat;
import com.projecthero.mod.darkseid.entity.DarkseidEntity;
import com.projecthero.mod.darkseid.entity.DarkseidEntityTypes;
import com.projecthero.mod.darkseid.entity.MotherBoxEntity;
import com.projecthero.mod.darkseid.entity.OmegaBeamEntity;
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
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.18: coverage for the Darkseid Raid's pieces. The full raid is deliberately never run here (its Boom Tubes
 * would pour Parademons into the neighbouring tests); each test drives one piece directly -- the shield, the
 * phase/health rules, the Omega Beam and cover, the Omega Annihilation interrupt, the Parademon variants, the
 * orphan guards, activation + roster + cleanup, and save/load.
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
