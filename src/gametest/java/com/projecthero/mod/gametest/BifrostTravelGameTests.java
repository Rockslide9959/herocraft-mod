package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.stormbreaker.Bifrost;
import com.projecthero.mod.stormbreaker.BifrostTravel;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Bifrost Travel cinematic ({@link BifrostTravel}): who the beam takes, the 10-block rise over 60 ticks (asserted on
 * per-tick step sizes), the gentle landing at the destination with no fall damage, a ceiling stopping the rise without
 * putting anyone in a block, a logout mid-rise putting the rider back on the ground with gravity on, and a second trip
 * refused while one runs. Each test stands on a platform {@link #ALT} blocks above its structure and travels to one
 * {@link #DEST} blocks up, in its own column and batch.
 */
public class BifrostTravelGameTests implements FabricGameTest {
	private static final String BATCH = "bifrost_travel_v0177";
	private static final int ALT = 20;
	private static final int DEST = 60;
	private static final int LANDED = BifrostTravel.LAND + 4;

	private static ServerPlayer player(GameTestHelper helper, double x, double z, boolean thor) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, ALT + 1, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		if (thor) {
			Worthiness.setScore(p, Worthiness.TEST_WORTHY_SCORE);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STORMBREAKER));
			Bifrost.resetCooldownForTests(p);
		}
		return p;
	}

	private static void platform(GameTestHelper helper, int y, Block block) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(new BlockPos(x, y, z), block);
			}
		}
	}

	private static Bifrost.Result travel(GameTestHelper helper, ServerPlayer p, int rx, int ry, int rz) {
		BlockPos abs = helper.absolutePos(new BlockPos(rx, ry, rz));
		return Bifrost.travelTo(p, abs.getX(), abs.getY(), abs.getZ());
	}

	private static void leave(GameTestHelper helper, ServerPlayer... players) {
		for (ServerPlayer p : players) {
			if (!p.isRemoved()) {
				helper.getLevel().getServer().getPlayerList().remove(p);
			}
		}
	}

	/** Every tick: the Y of each tracked entity. */
	private static List<double[]> track(GameTestHelper helper, Entity... who) {
		List<double[]> rows = new ArrayList<>();
		helper.onEachTick(() -> {
			double[] row = new double[who.length];
			for (int i = 0; i < who.length; i++) {
				row[i] = who[i].getY();
			}
			rows.add(row);
		});
		return rows;
	}

	/** Per-tick rise steps of column {@code i} between ticks {@code from} and {@code to} of {@code rows}. */
	private static double[] steps(List<double[]> rows, int i, int from, int to) {
		double[] out = new double[Math.max(0, to - from)];
		for (int k = from; k < to && k + 1 < rows.size(); k++) {
			out[k - from] = rows.get(k + 1)[i] - rows.get(k)[i];
		}
		return out;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = LANDED + 40)
	public void theBeamLiftsEveryoneNearbyTenBlocksAndSetsThemDownSafely(GameTestHelper helper) {
		platform(helper, ALT, Blocks.STONE);
		platform(helper, DEST, Blocks.STONE);
		ServerPlayer thor = player(helper, 1.5, 1.5, true);
		ServerPlayer friend = player(helper, 3.5, 1.5, false); // 2 blocks away, not in any squad: anyone standing in it comes
		ServerPlayer far = player(helper, 7.5, 7.5, false); // 8.5 blocks away and no squadmate: stays
		Wolf wolf = helper.spawn(EntityType.WOLF, new Vec3(1.5, ALT + 1, 3.5));
		wolf.tame(thor);
		wolf.setOrderedToSit(false);
		wolf.setHealth(wolf.getMaxHealth());
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(3.5, ALT + 1, 3.5));
		zombie.setNoAi(true);
		Cow cow = helper.spawn(EntityType.COW, new Vec3(2.5, ALT + 1, 4.5));
		cow.setNoAi(true);
		Vec3 thorStart = thor.position();
		Vec3 friendStart = friend.position();
		Vec3 wolfStart = wolf.position();
		float friendHealth = friend.getHealth();

		List<Entity> riders = BifrostTravel.ridersFor(thor);
		helper.assertTrue(riders.contains(thor) && riders.contains(friend) && riders.contains(wolf),
				"the caster, the player beside him and his wolf are in the beam, got " + riders.size());
		helper.assertFalse(riders.contains(far), "a non-squad player 8.5 blocks away is not");
		helper.assertFalse(riders.contains(zombie), "hostile mobs never ride the Bifrost");
		helper.assertFalse(riders.contains(cow), "nor creatures nobody owns");

		Bifrost.Result result = travel(helper, thor, 2, DEST + 1, 2);
		helper.assertTrue(result == Bifrost.Result.OK, "the Bifrost opens, got " + result);
		helper.assertTrue(BifrostTravel.busy(thor) && BifrostTravel.busy(friend) && BifrostTravel.busy(wolf), "all three are on the trip");
		helper.assertTrue(travel(helper, thor, 5, DEST + 1, 5) == Bifrost.Result.BUSY, "a second trip mid-cinematic is refused");
		List<double[]> ys = track(helper, thor, friend, wolf);

		helper.runAtTickTime(BifrostTravel.RISE_START - 2, () -> {
			helper.assertTrue(thor.position().distanceTo(thorStart) < 1.0E-3, "nobody moves while the axe is raised");
			helper.assertTrue(BifrostTravel.CAST != null && thor.getAttachedOrElse(BifrostTravel.CAST, 0L) != 0L, "the caster's raise clock runs");
		});
		helper.runAtTickTime(BifrostTravel.TRANSPORT - 2, () -> {
			// the rise: every per-tick step small and upward, the top about 10 blocks up
			for (int i = 0; i < 3; i++) {
				double[] s = steps(ys, i, BifrostTravel.RISE_START, BifrostTravel.TRANSPORT - 4);
				double max = 0;
				for (double d : s) {
					helper.assertTrue(d >= -1.0E-6, "never sinks on the way up (rider " + i + ", step " + d + ")");
					max = Math.max(max, d);
				}
				helper.assertTrue(max > 0.1 && max <= 0.3, "a slow, smooth rise: biggest step " + max + " blocks a tick (rider " + i + ")");
			}
			helper.assertTrue(Math.abs(thor.getY() - thorStart.y - BifrostTravel.RISE_HEIGHT) < 0.35, "Thor is about 10 blocks up, got " + (thor.getY() - thorStart.y));
			helper.assertTrue(Math.abs(friend.getY() - friendStart.y - BifrostTravel.RISE_HEIGHT) < 0.35, "so is his friend");
			helper.assertTrue(Math.abs(wolf.getY() - wolfStart.y - BifrostTravel.RISE_HEIGHT) < 0.35, "and the wolf, got " + (wolf.getY() - wolfStart.y));
			helper.assertTrue(thor.isNoGravity() && thor.fallDistance == 0f && wolf.fallDistance == 0f, "held in the beam: no gravity, no fall distance");
			helper.assertTrue(new Vec3(thor.getX() - thorStart.x, 0, thor.getZ() - thorStart.z).length() < 0.05, "straight up, no drift");
		});
		helper.runAtTickTime(LANDED, () -> {
			// the descent: per-tick steps downward and never a drop
			double[] s = steps(ys, 0, BifrostTravel.TRANSPORT + 2, BifrostTravel.LAND - 1);
			for (double d : s) {
				helper.assertTrue(d <= 1.0E-6 && d >= -0.75, "lowered gently, step " + d);
			}
			BlockPos land = helper.absolutePos(new BlockPos(2, DEST + 1, 2));
			helper.assertTrue(thor.blockPosition().equals(land), "Thor lands at the destination, got " + thor.blockPosition() + " want " + land);
			helper.assertTrue(friend.blockPosition().equals(land.east(2)), "his friend keeps the 2-block offset, got " + friend.blockPosition());
			helper.assertTrue(wolf.blockPosition().equals(land.south(2)), "the wolf keeps its offset too, got " + wolf.blockPosition());
			for (Entity e : new Entity[] {thor, friend, wolf}) {
				helper.assertFalse(e.isNoGravity(), "gravity is back for " + e.getName().getString());
				helper.assertTrue(e.fallDistance == 0f, "no fall distance for " + e.getName().getString());
				helper.assertFalse(BifrostTravel.busy(e), "the trip is over for " + e.getName().getString());
			}
			helper.assertTrue(friend.getHealth() == friendHealth, "no fall damage to the friend");
			helper.assertTrue(wolf.getHealth() == wolf.getMaxHealth(), "nor to the wolf, got " + wolf.getHealth());
			helper.assertTrue(far.position().distanceTo(Vec3.atBottomCenterOf(land)) > 20, "the far player stayed behind");
			helper.assertTrue(cow.position().distanceTo(Vec3.atBottomCenterOf(land)) > 20, "the cow stayed behind");
			helper.assertTrue(zombie.isRemoved() || zombie.position().distanceTo(Vec3.atBottomCenterOf(land)) > 20, "the zombie stayed behind");
			helper.assertTrue(thor.getAttachedOrElse(BifrostTravel.CAST, 0L) == 0L, "the raise clock is cleared");
			helper.assertTrue(Bifrost.cooldownRemaining(thor) > 0, "the cooldown was spent");
			platform(helper, ALT, Blocks.AIR);
			platform(helper, DEST, Blocks.AIR);
			wolf.discard();
			leave(helper, thor, friend, far);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = LANDED + 40)
	public void aRiderLoggingOutMidRiseIsPutBackOnTheGround(GameTestHelper helper) {
		platform(helper, ALT, Blocks.STONE);
		platform(helper, DEST, Blocks.STONE);
		ServerPlayer thor = player(helper, 1.5, 1.5, true);
		ServerPlayer friend = player(helper, 3.5, 1.5, false);
		Vec3 friendStart = friend.position();
		helper.assertTrue(travel(helper, thor, 2, DEST + 1, 2) == Bifrost.Result.OK, "the Bifrost opens");
		helper.runAtTickTime(BifrostTravel.RISE_START + 30, () -> {
			helper.assertTrue(friend.getY() > friendStart.y + 2.0, "the friend is up in the beam, got " + (friend.getY() - friendStart.y));
			BifrostTravel.onLeave(friend); // what the DISCONNECT handler does
			helper.assertTrue(friend.position().distanceTo(friendStart) < 1.0E-3, "back where they stood, got " + friend.position());
			helper.assertFalse(friend.isNoGravity(), "with gravity on (not saved floating)");
			helper.assertTrue(friend.fallDistance == 0f, "and no fall distance");
			helper.assertFalse(BifrostTravel.busy(friend), "and off the trip");
			leave(helper, friend);
		});
		helper.runAtTickTime(LANDED, () -> {
			BlockPos land = helper.absolutePos(new BlockPos(2, DEST + 1, 2));
			helper.assertTrue(thor.blockPosition().equals(land), "the caster carries on and lands, got " + thor.blockPosition());
			helper.assertFalse(thor.isNoGravity(), "gravity back");
			platform(helper, ALT, Blocks.AIR);
			platform(helper, DEST, Blocks.AIR);
			leave(helper, thor);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = LANDED + 40)
	public void theCasterLoggingOutMidRiseIsSafeAndTheOthersStillArrive(GameTestHelper helper) {
		platform(helper, ALT, Blocks.STONE);
		platform(helper, DEST, Blocks.STONE);
		ServerPlayer thor = player(helper, 1.5, 1.5, true);
		ServerPlayer friend = player(helper, 3.5, 1.5, false);
		Vec3 thorStart = thor.position();
		helper.assertTrue(travel(helper, thor, 2, DEST + 1, 2) == Bifrost.Result.OK, "the Bifrost opens");
		helper.runAtTickTime(BifrostTravel.RISE_START + 20, () -> {
			BifrostTravel.onLeave(thor);
			helper.assertTrue(thor.position().distanceTo(thorStart) < 1.0E-3, "the caster is put back where he stood");
			helper.assertFalse(thor.isNoGravity(), "with gravity on");
			helper.assertTrue(thor.getAttachedOrElse(BifrostTravel.CAST, 0L) == 0L, "and his raise clock cleared");
			leave(helper, thor);
		});
		helper.runAtTickTime(LANDED, () -> {
			BlockPos land = helper.absolutePos(new BlockPos(4, DEST + 1, 2));
			helper.assertTrue(friend.blockPosition().equals(land), "the bridge stays open for the others, got " + friend.blockPosition());
			helper.assertFalse(friend.isNoGravity() || BifrostTravel.busy(friend), "who land normally");
			platform(helper, ALT, Blocks.AIR);
			platform(helper, DEST, Blocks.AIR);
			leave(helper, friend);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = LANDED + 40)
	public void aCeilingStopsTheRiseAndADestinationInsideBlocksLandsOnTop(GameTestHelper helper) {
		platform(helper, ALT, Blocks.STONE);
		platform(helper, DEST, Blocks.STONE);
		helper.setBlock(new BlockPos(1, ALT + 5, 1), Blocks.STONE); // a ceiling 4 blocks over Thor's feet: 2.2 blocks of headroom
		for (int y = 1; y <= 3; y++) {
			helper.setBlock(new BlockPos(5, DEST + y, 5), Blocks.STONE); // a pillar at the destination
		}
		ServerPlayer thor = player(helper, 1.5, 1.5, true);
		Vec3 start = thor.position();
		List<Boolean> clear = new ArrayList<>();
		helper.onEachTick(() -> clear.add(helper.getLevel().noCollision(thor, thor.getBoundingBox().deflate(1.0E-3))));
		helper.assertTrue(travel(helper, thor, 5, DEST + 2, 5) == Bifrost.Result.OK, "aimed into the pillar");
		helper.runAtTickTime(BifrostTravel.TRANSPORT - 2, () -> {
			double rose = thor.getY() - start.y;
			helper.assertTrue(rose > 2.0 && rose <= 2.21, "stops with his head under the ceiling, rose " + rose);
		});
		helper.runAtTickTime(LANDED, () -> {
			helper.assertTrue(thor.blockPosition().equals(helper.absolutePos(new BlockPos(5, DEST + 4, 5))),
					"lands on top of the pillar, not inside it, got " + thor.blockPosition());
			helper.assertFalse(clear.contains(Boolean.FALSE), "never inside a block on the way");
			platform(helper, ALT, Blocks.AIR);
			platform(helper, DEST, Blocks.AIR);
			helper.setBlock(new BlockPos(1, ALT + 5, 1), Blocks.AIR);
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(new BlockPos(5, DEST + y, 5), Blocks.AIR);
			}
			leave(helper, thor);
			helper.succeed();
		});
	}
}
