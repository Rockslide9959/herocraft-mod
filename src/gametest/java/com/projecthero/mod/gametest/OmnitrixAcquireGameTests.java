package com.projecthero.mod.gametest;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.entity.OmnitrixMeteorEntity;
import com.projecthero.mod.omnitrix.entity.OmnitrixPodEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 getting the Omnitrix: the capsule opens only on a right-click, the open capsule's watch leaps at a player within 3
 * blocks and latches on (the power, the shake-off's slow, no raising the watch until it is over), and the meteor's crater is
 * a real bowl. Their own batch, so their pods never sit within the Meteor Scanner test's refusal radius.
 */
public class OmnitrixAcquireGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "omnitrix_acquire")
	public void theCapsuleOpensOnlyWhenRightClicked(GameTestHelper helper) {
		floor(helper);
		OmnitrixPodEntity pod = OmnitrixPodEntity.place(helper.getLevel(), helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
		ServerPlayer p = mock(helper, 3.5, 2, 5.0);
		helper.runAfterDelay(OmnitrixConfig.POD_OPEN_DELAY + 40, () -> {
			helper.assertFalse(pod.opened(), "long after landing it is still shut (no auto-open)");
			helper.assertTrue(pod.jumpTime(0f) < 0f && !Omnitrix.hasPower(p), "a shut capsule never lets the watch out, even right beside it");
			helper.assertFalse(pod.latchOnto(p), "and nothing latches through the shell");
			pod.interact(p, InteractionHand.MAIN_HAND);
			helper.assertTrue(pod.opened(), "the right-click opens it");
			helper.assertFalse(pod.open(), "the lid is still swinging up");
			helper.runAfterDelay(OmnitrixConfig.POD_LID_TICKS + 1, () -> {
				helper.assertTrue(pod.open(), "and then it is open");
				pod.discard();
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260, batch = "omnitrix_acquire")
	public void theWatchLatchesOnWithin3BlocksAndTheShakeOffSlowsYou(GameTestHelper helper) {
		floor(helper);
		OmnitrixPodEntity pod = OmnitrixPodEntity.place(helper.getLevel(), helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
		Vec3 c = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
		ServerPlayer p = mock(helper, 3.5, 2, 3.5 + OmnitrixConfig.POD_LATCH_RANGE + 1.2);
		helper.assertTrue(pod.openLid(p), "opened");
		// mock players are not ticked by the server: drive the wearer's tick (it does nothing until the watch is on)
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
		helper.runAfterDelay(OmnitrixConfig.POD_LID_TICKS + 10, () -> {
			helper.assertTrue(pod.open(), "the lid is up");
			helper.assertTrue(pod.jumpTime(0f) < 0f && !pod.empty(), "4 blocks off: the watch stays put");
			p.moveTo(c.x, c.y, c.z + OmnitrixConfig.POD_LATCH_RANGE - 0.6, 0f, 0f);
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(pod.jumpTime(0f) >= 0f && pod.jumpTarget() == p, "2.4 blocks off: it leaps at them");
				helper.assertFalse(Omnitrix.hasPower(p), "but they have nothing until it lands");
				helper.runAfterDelay(OmnitrixConfig.POD_JUMP_TICKS + 1, () -> {
					helper.assertTrue(Omnitrix.hasPower(p) && pod.empty(), "it latched on; the capsule is empty");
					helper.assertTrue(Omnitrix.state(p).animId == Omnitrix.ANIM_LATCH && Omnitrix.latching(p), "the shake-off plays");
					helper.assertTrue(Omnitrix.latchSlowed(p), "they are slowed meanwhile");
					helper.assertFalse(Omnitrix.activate(p), "and cannot raise the watch while fighting it");
					helper.runAfterDelay(OmnitrixConfig.LATCH_TICKS + 2, () -> {
						helper.assertFalse(Omnitrix.latching(p), "the shake-off is over");
						helper.assertFalse(Omnitrix.latchSlowed(p), "the slow is gone");
						helper.assertTrue(Omnitrix.activate(p), "R raises the watch again");
						pod.discard();
						helper.succeed();
					});
				});
			});
		});
	}

	/** Blown open: air, or one of the flames the blast leaves licking the slopes. */
	private static boolean open(ServerLevel level, BlockPos pos) {
		var s = level.getBlockState(pos);
		return s.isAir() || s.is(Blocks.FIRE);
	}

	/** A stone slab high over the test (nothing else is up there), its centre's top block returned. */
	private static BlockPos skySlab(GameTestHelper helper, int half, int thick) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(new BlockPos(3, 1, 3));
		int y = Math.min(level.getMaxBuildHeight() - 30, base.getY() + 140);
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				for (int k = 0; k < thick; k++) {
					level.setBlock(new BlockPos(base.getX() + dx, y - k, base.getZ() + dz), k == 0 ? Blocks.GRASS_BLOCK.defaultBlockState()
							: Blocks.STONE.defaultBlockState(), 2);
				}
			}
		}
		return new BlockPos(base.getX(), y, base.getZ());
	}

	private static void clearSky(GameTestHelper helper, BlockPos top, int half, int thick) {
		ServerLevel level = helper.getLevel();
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				for (int y = top.getY() - thick; y <= top.getY() + 4; y++) {
					level.setBlock(new BlockPos(top.getX() + dx, y, top.getZ() + dz), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		level.getEntitiesOfClass(FallingBlockEntity.class, new AABB(top).inflate(40)).forEach(e -> e.discard());
		level.getEntitiesOfClass(OmnitrixPodEntity.class, new AABB(top).inflate(40)).forEach(e -> e.discard());
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "omnitrix_acquire_crater") // alone: its sky slab would overlap the meteor test's
	public void theCraterIsABigBowl(GameTestHelper helper) {
		int half = OmnitrixConfig.CRATER_RADIUS + 7, thick = OmnitrixConfig.CRATER_DEPTH + 4;
		BlockPos top = skySlab(helper, half, thick);
		ServerLevel level = helper.getLevel();
		BlockPos bottom = OmnitrixMeteorEntity.crater(level, top);
		int r = OmnitrixConfig.CRATER_RADIUS;
		helper.assertTrue(r >= 9 && OmnitrixConfig.CRATER_DEPTH >= 5, "much bigger than the old radius-4 dent");
		helper.assertTrue(bottom.getY() == top.getY() - OmnitrixConfig.CRATER_DEPTH + 1, "its bottom is " + OmnitrixConfig.CRATER_DEPTH + " deep");
		helper.assertTrue(open(level, bottom) && level.getBlockState(bottom.below()).isSolid(), "open at the bottom, a lined floor under it");
		// a bowl: deep in the middle, shallower out to the rim, the surface level open right across it
		for (int d = 0; d <= r - 1; d++) {
			for (int[] dir : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
				BlockPos at = top.offset(dir[0] * d, 0, dir[1] * d);
				helper.assertTrue(open(level, at), "the surface is blown open " + d + " blocks out");
				int depth = OmnitrixMeteorEntity.depthAt(d);
				helper.assertTrue(level.getBlockState(at.below(depth)).isSolid() && (depth == 0 || open(level, at.below(depth - 1))),
						"the floor " + d + " out is " + depth + " down");
			}
		}
		helper.assertTrue(OmnitrixMeteorEntity.depthAt(0) > OmnitrixMeteorEntity.depthAt(r / 2) && OmnitrixMeteorEntity.depthAt(r / 2) > OmnitrixMeteorEntity.depthAt(r),
				"it shelves up toward the rim");
		// scorched: no grass left on the floor, magma or blackstone in the middle
		var mid = level.getBlockState(bottom.below());
		helper.assertTrue(mid.is(Blocks.MAGMA_BLOCK) || mid.is(Blocks.BLACKSTONE) || mid.is(Blocks.OBSIDIAN), "the hot middle is scorched: " + mid);
		// beyond the rim the ground is intact (a bowl, not a pit to bedrock)
		helper.assertTrue(level.getBlockState(top.offset(r + 6, 0, 0)).isSolid(), "the ground past the rim is still there");
		clearSky(helper, top, half, thick);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "omnitrix_acquire_meteor") // alone: its crater spans neighbouring tests
	public void theMeteorLandsCarvesOverTicksAndLeavesTheCapsule(GameTestHelper helper) {
		int half = OmnitrixConfig.CRATER_RADIUS + 7, thick = OmnitrixConfig.CRATER_DEPTH + 4;
		BlockPos top = skySlab(helper, half, thick);
		ServerLevel level = helper.getLevel();
		Vec3 impact = Vec3.atBottomCenterOf(top.above());
		OmnitrixMeteorEntity m = OmnitrixMeteorEntity.launch(level, impact);
		helper.assertTrue(m != null && m.getY() > impact.y + 30, "it starts far up");
		// the flung debris would fall on the tests below: catch it as it flies
		helper.onEachTick(() -> level.getEntitiesOfClass(FallingBlockEntity.class, new AABB(top).inflate(40, 60, 40)).forEach(e -> e.discard()));
		helper.succeedWhen(() -> {
			helper.assertTrue(m.isRemoved(), "the meteor has landed and finished carving");
			var pods = level.getEntitiesOfClass(OmnitrixPodEntity.class, new AABB(top).inflate(3, 10, 3));
			helper.assertTrue(pods.size() == 1, "one capsule in the crater");
			helper.assertTrue(pods.get(0).getY() < top.getY() - 2, "at the bottom of the bowl");
			helper.assertTrue(open(level, top.offset(OmnitrixConfig.CRATER_RADIUS - 1, 0, 0)), "the crater reaches out to the rim");
			clearSky(helper, top, half, thick);
		});
	}
}
