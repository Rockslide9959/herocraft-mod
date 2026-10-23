package com.projecthero.mod.gametest;

import java.util.List;
import java.util.Random;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.titanshifter.TitanEmergencyBuild;
import com.projecthero.mod.titanshifter.TitanMove;
import com.projecthero.mod.titanshifter.TitanPhase;
import com.projecthero.mod.titanshifter.TitanShifter;
import com.projecthero.mod.titanshifter.TitanShifterAbilityManager;
import com.projecthero.mod.titanshifter.TitanShifterConfig;
import com.projecthero.mod.titanshifter.TitanType;
import com.projecthero.mod.titanshifter.data.TitanShifterState;
import com.projecthero.mod.titanshifter.entity.TitanCrystalWallEntity;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;
import com.projecthero.mod.titanshifter.entity.TitanLimbChunkEntity;
import com.projecthero.mod.titanshifter.entity.TitanShifterEntities;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user, Titan fixes): severed limbs drop a chunk that lands, steams and dissolves (capped, never saved); the
 * new moves' numbers come from {@link TitanShifterConfig} (defaults = the old constants); the Crystal Wall works with
 * ability terrain damage off and never touches a block; the Emergency Titan's own rushed build.
 */
public class TitanFixesGameTests implements FabricGameTest {
	private static final int SETTLE = 80;

	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-9, 0, -9), base.offset(9, 18, 9))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		// a floor to stand walls and limbs on
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-9, -1, -9), base.offset(9, -1, 9))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		return p;
	}

	private static ServerPlayer shifter(GameTestHelper helper, TitanType type) {
		ServerPlayer p = mock(helper);
		TitanShifter.grant(p, type);
		return p;
	}

	private static List<TitanLimbChunkEntity> chunksNear(GameTestHelper helper, Vec3 c) {
		return helper.getLevel().getEntitiesOfClass(TitanLimbChunkEntity.class, new AABB(c, c).inflate(40));
	}

	// ---------------- 12: severed limbs drop a chunk ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1400)
	public void aSeveredLimbIsFlungOffLandsAndDissolves(GameTestHelper helper) {
		ServerPlayer p = shifter(helper, TitanType.GENERIC_TITAN);
		helper.onEachTick(() -> TitanShifter.tick(p));
		helper.assertFalse(TitanShifterEntities.TITAN_LIMB_CHUNK.canSerialize(), "limb chunks are never saved");
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			int before = TitanLimbChunkEntity.liveCount();
			helper.assertTrue(form.sever(TitanFormEntity.LIMB_R_ARM), "the arm comes off");
			List<TitanLimbChunkEntity> chunks = chunksNear(helper, form.position());
			helper.assertTrue(chunks.size() == 1 && TitanLimbChunkEntity.liveCount() == before + 1, "one chunk flies: " + chunks.size());
			TitanLimbChunkEntity c = chunks.get(0);
			helper.assertTrue(c.limb() == TitanFormEntity.LIMB_R_ARM && c.titanType() == TitanType.GENERIC_TITAN, "it is that Titan's right arm");
			helper.assertTrue(Math.abs(c.titanHeight() - form.getBbHeight()) < 0.01f, "at the Titan's scale");
			helper.assertTrue(c.getDeltaMovement().y > 0.0 && !c.hasLanded(), "flung up and out");
			Vec3 right = Vec3.directionFromRotation(0, form.getYRot() + 90.0f);
			helper.assertTrue(c.getDeltaMovement().dot(right) > 0.1, "outward from the right side");
			// poll, not fixed delays: on a busy CI server the flung chunk can sit in a chunk that ticks it late
			helper.startSequence()
					.thenWaitUntil(() -> helper.assertTrue(c.hasLanded() && c.isAlive(), "it landed and lies there: landed "
							+ c.landedAt() + " removed " + c.getRemovalReason() + " tc " + c.tickCount + " y " + c.getY()))
					.thenExecute(() -> helper.assertTrue(c.dissolveProgress(0) == 0.0f
							|| c.landedAt() + TitanLimbChunkEntity.STEAM_TICKS < c.chunkAge(0), "steaming before it dissolves"))
					.thenWaitUntil(() -> {
						helper.assertTrue(c.isRemoved(), "dissolved away after ~18 s");
						helper.assertTrue(c.dissolveProgress(0) >= 1.0f, "fully dissolved");
					})
					.thenSucceed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "titanLimbCap") // alone: it trims every live chunk
	public void limbChunksAreCappedAndForgottenOnReset(GameTestHelper helper) {
		ServerPlayer p = shifter(helper, TitanType.BEAST_TITAN);
		helper.onEachTick(() -> TitanShifter.tick(p));
		TitanShifter.transform(p);
		helper.runAfterDelay(SETTLE, () -> {
			TitanFormEntity form = TitanShifter.formOf(p);
			int max = TitanShifterConfig.limbs().maxChunks;
			for (int i = 0; i < max + 4; i++) {
				TitanLimbChunkEntity.fling(form, TitanFormEntity.LIMBS[i % 4], form.limbCentre(TitanFormEntity.LIMBS[i % 4]));
			}
			helper.assertTrue(TitanLimbChunkEntity.liveCount() <= max, "never more than " + max + ": " + TitanLimbChunkEntity.liveCount());
			helper.assertTrue(chunksNear(helper, form.position()).size() <= max, "the oldest were removed from the world");
			TitanShifterAbilityManager.clearSessionState(); // what ServerStateReset calls
			helper.assertTrue(TitanLimbChunkEntity.liveCount() == 0, "forgotten on reset");
			chunksNear(helper, form.position()).forEach(e -> e.discard());
			helper.succeed();
		});
	}

	// ---------------- 13: the move numbers are config ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void theMoveNumbersComeFromTheConfigWithTheOldDefaults(GameTestHelper helper) {
		// a fresh config's values are exactly the constants the moves had before
		TitanShifterConfig fresh = TitanShifterConfig.SPEC.fresh();
		var saved = TitanShifterConfig.get();
		for (TitanMove m : TitanMove.values()) {
			if (m.defaultCooldown() >= 0) {
				helper.assertTrue(m.cooldown() == m.defaultCooldown(), m.id + " cooldown " + m.cooldown() + " vs " + m.defaultCooldown());
			}
			if (m.defaultDamage() > 0) {
				helper.assertTrue(Math.abs(m.damage() - m.defaultDamage()) < 1.0e-9, m.id + " damage " + m.damage() + " vs " + m.defaultDamage());
			}
		}
		helper.assertTrue(fresh.beastTitan.fastballStones == 14 && fresh.beastTitan.fastballPerTarget == 3 && fresh.beastTitan.fastballSpeed == 3.1
				&& fresh.beastTitan.fastballLife == 28 && fresh.beastTitan.scatterChunks == 18 && fresh.beastTitan.maxRocksInFlight == 180,
				"the Beast's rock numbers");
		helper.assertTrue(fresh.armoredTitan.shoulderChargeTicks == 30 && fresh.armoredTitan.shoulderChargeBlockCap == 160
				&& fresh.armoredTitan.braceDamageFactor == 0.25 && fresh.attackTitan.rageTicks == 200 && fresh.femaleTitan.crystalFistsTicks == 240,
				"the timed states");
		// and they are read live: a tuned value changes the move
		int old = saved.attackTitan.uppercutCooldown;
		saved.attackTitan.uppercutCooldown = 77;
		try {
			helper.assertTrue(TitanMove.UPPERCUT.cooldown() == 77, "the live config is read");
		} finally {
			saved.attackTitan.uppercutCooldown = old;
		}
		helper.succeed();
	}

	// ---------------- 14: the Crystal Wall ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theCrystalWallWorksWithTerrainDamageOffAndPlacesNoBlocks(GameTestHelper helper) {
		boolean was = HeroConfig.get().abilityTerrainDamage;
		HeroConfig.get().abilityTerrainDamage = false;
		ServerPlayer p = shifter(helper, TitanType.ARMORED_TITAN);
		BlockPos base = p.blockPosition();
		try {
			AbilityRouter.handleInput(p, 3, true); // X = Crystal Wall
		} finally {
			HeroConfig.get().abilityTerrainDamage = was;
		}
		helper.assertTrue(TitanShifter.cooldownRemaining(p, TitanMove.CRYSTAL_WALL.id) > 0, "it fired with terrain damage off");
		List<TitanCrystalWallEntity> wall = TitanCrystalWallEntity.wallOf(p.getUUID());
		helper.assertTrue(wall.size() == 4, "four columns: " + wall.size());
		helper.assertFalse(TitanShifterEntities.TITAN_CRYSTAL_WALL.canSerialize(), "never saved");
		for (TitanCrystalWallEntity c : wall) {
			helper.assertTrue(c.canBeCollidedWith() && c.isPickable(), "solid and hit by projectiles");
			helper.assertTrue(c.life() == TitanShifterConfig.human().wallTicks, "25 s");
			helper.assertTrue(c.getZ() > p.getZ() + 1.5 && c.getZ() < p.getZ() + 3.5, "two blocks ahead");
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-4, 0, -4), base.offset(4, 6, 4))) {
			helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "no block placed at " + pos);
		}
		// a mob walking into it is stopped by it
		TitanCrystalWallEntity mid = wall.get(1);
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		z.moveTo(mid.getX(), mid.getY(), mid.getZ() + 1.5, 180f, 0f);
		z.setNoAi(true);
		helper.getLevel().addFreshEntity(z);
		AABB into = z.getBoundingBox().expandTowards(0, 0, -1.5);
		helper.assertFalse(helper.getLevel().getEntityCollisions(z, into).isEmpty(), "the wall is in the zombie's way");
		// an arrow flying at it from the far side does not get through
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.moveTo(mid.getX(), mid.getY() + 2.0, mid.getZ() + 5.0, 0f, 0f);
		arrow.setDeltaMovement(0, 0, -1.6);
		arrow.setNoGravity(true);
		helper.getLevel().addFreshEntity(arrow);
		double plane = mid.getZ() - 0.5;
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(arrow.isRemoved() || arrow.getZ() > plane, "the arrow was stopped at the wall, z " + arrow.getZ() + " plane " + plane);
			// a second wall replaces the first
			TitanShifterState s = TitanShifter.state(p).copy();
			s.abilityReadyAt.remove(TitanMove.CRYSTAL_WALL.id);
			p.setAttached(com.projecthero.mod.attachment.ModAttachments.TITAN_SHIFTER_STATE, s);
			AbilityRouter.handleInput(p, 3, true);
			helper.assertTrue(wall.stream().allMatch(c -> c.isRemoved()), "the old wall shattered");
			helper.assertTrue(TitanCrystalWallEntity.wallOf(p.getUUID()).size() == 4, "the new one stands");
			TitanCrystalWallEntity.dismiss(p.getUUID());
			TitanCrystalWallEntity.clearSessionState(); // (TitanHumanKit.clearSessionState, from ServerStateReset)
			helper.assertTrue(TitanCrystalWallEntity.wallOf(p.getUUID()).isEmpty(), "forgotten on reset");
			z.discard();
			helper.succeed();
		});
	}

	// ---------------- 15: the Emergency Titan's own transformation ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void theEmergencyTimetableIsRushedFlickeringAndPatchy(GameTestHelper helper) {
		Random r = new Random(7);
		// the skeleton flickers: some texel that has appeared is hidden at a later step before the flesh covers it, and
		// shows again; past SKELETON_STEADY it never flickers
		int flickering = 0;
		for (int i = 0; i < 400; i++) {
			float rank = r.nextFloat(), region = r.nextFloat(), jit = r.nextFloat();
			float ap = TitanEmergencyBuild.appear(0, rank, 0.5f, 0.5f, jit);
			helper.assertTrue(ap <= 0.3f, "the bones are all out within 30% of the build");
			boolean seenOff = false, seenOnAfter = false;
			for (float p = ap + 0.001f; p < TitanEmergencyBuild.SKELETON_STEADY; p += 0.004f) {
				boolean v = TitanEmergencyBuild.visible(0, ap, region, jit, p);
				if (!v) {
					seenOff = true;
				} else if (seenOff) {
					seenOnAfter = true;
				}
			}
			if (seenOff && seenOnAfter) {
				flickering++;
			}
			for (float p = TitanEmergencyBuild.SKELETON_STEADY; p <= 1.0f; p += 0.01f) {
				helper.assertTrue(TitanEmergencyBuild.visible(0, ap, region, jit, p), "the skeleton holds steady at the end");
			}
		}
		helper.assertTrue(flickering > 300, "most bones flicker in and out: " + flickering);
		// the flesh: uneven -- two texels of the same rank appear far apart when their blotch noise differs -- but complete
		float a = TitanEmergencyBuild.appear(1, 0.5f, 0.2f, 0.5f, 0.5f), b = TitanEmergencyBuild.appear(1, 0.5f, 0.8f, 0.5f, 0.5f);
		helper.assertTrue(b - a > 0.15f, "blotchy flesh: " + a + " vs " + b);
		for (int i = 0; i < 400; i++) {
			helper.assertTrue(TitanEmergencyBuild.appear(1, r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()) < 1.0f, "every bit of flesh forms");
		}
		// the skin: only part forms -- the patches never do
		int n = 20000;
		float[] patch = new float[n], jit = new float[n];
		for (int i = 0; i < n; i++) {
			patch[i] = TitanEmergencyBuild.noise(r.nextFloat() * 40f, r.nextFloat() * 32f, r.nextFloat() * 8f)[1];
			jit[i] = r.nextFloat();
		}
		float cover = TitanEmergencyBuild.skinCoverage(patch, jit);
		helper.assertTrue(cover > 0.6f && cover < 0.85f, "the skin only partly forms: " + cover);
		helper.assertTrue(TitanEmergencyBuild.appear(2, 0.5f, 0.5f, 0.1f, 0.5f) >= TitanEmergencyBuild.NEVER, "a patch never forms");
		// the jerky clip clock never runs backwards and finishes just ahead of the build
		double last = 0;
		for (double e = 0.5; e <= 40.0; e += 0.5) {
			double w = TitanEmergencyBuild.jerkyWarp(e, 1234);
			helper.assertTrue(w >= last - 1.0e-9, "monotonic at " + e);
			last = w;
		}
		helper.assertTrue(Math.abs(TitanEmergencyBuild.jerkyWarp(40.0, 99) / 40.0 - TitanEmergencyBuild.CLIP_LEAD) < 0.35, "on time overall");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theEmergencyShiftIsFasterThanTheNormalOne(GameTestHelper helper) {
		ServerPlayer p = shifter(helper, TitanType.FEMALE_TITAN);
		helper.onEachTick(() -> TitanShifter.tick(p));
		TitanShifterState s = TitanShifter.state(p).copy();
		s.energy = 50f;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.TITAN_SHIFTER_STATE, s);
		helper.assertTrue(TitanShifter.transform(p, true), "an emergency shift with half a bar");
		TitanFormEntity form = TitanShifter.formOf(p);
		int ticks = TitanShifterConfig.emergency().transformTicks;
		helper.assertTrue(form.isEmergency() && form.formStateTicks() == ticks, "its own length: " + form.formStateTicks());
		helper.assertTrue(ticks < TitanShifterConfig.transformation().transformTicks, "rushed");
		helper.runAfterDelay(ticks + 3, () -> {
			helper.assertTrue(TitanShifter.phase(p) == TitanPhase.TITAN, "done in " + ticks + " ticks");
			helper.succeed();
		});
	}
}
