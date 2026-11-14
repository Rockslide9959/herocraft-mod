package com.projecthero.mod.gametest;

import com.projecthero.mod.entity.GiantTracking;
import com.projecthero.mod.galactus.entity.GalactusEntityTypes;
import com.projecthero.mod.galactus.entity.WorldshipEntity;
import com.projecthero.mod.galactus.orbit.DevourerEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4: Galactus was invisible at a low render distance because the server never sent him: vanilla only pairs an
 * entity with a player whose chunk view holds the entity's chunk, within {@code min(trackingRange, viewDistance * 16)}.
 * {@link GiantTracking.AlwaysTracked} giants are paired within their own tracking range whatever the view distance, and
 * the raid keeps their far-off chunk loaded. (Mock players ask for view distance 2, the low-render-distance case.) The
 * client half (ticking and drawing outside the compiled sections) is proven with a real client at render distance 2/4.
 */
public class GiantTrackingGameTests implements FabricGameTest {
	private static GiantTracking.Tracked tracker(ServerLevel level, Entity e) {
		return ((GiantTracking.Map) level.getChunkSource().chunkMap).projecthero$tracked(e.getId());
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
		p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void aGiantFarOutsideAViewDistanceOfTwoIsStillSentAZombieIsNot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = player(helper);
		Vec3 far = p.position().add(120, 20, 0);
		GiantTracking.keepLoaded(level, far);
		Entity[] made = new Entity[3];
		helper.startSequence().thenWaitUntil(() -> {
			GiantTracking.keepLoaded(level, far);
			helper.assertTrue(level.isPositionEntityTicking(BlockPos.containing(far)), "keepLoaded makes the giant's far chunk entity-ticking");
		}).thenExecute(() -> {
			helper.assertTrue(p.requestedViewDistance() <= 4, "the mock player asks for a low view distance (" + p.requestedViewDistance() + ")");
			DevourerEntity d = GalactusEntityTypes.DEVOURER.create(level);
			d.setHeight(180f);
			d.moveTo(far.x, far.y, far.z, 0f, 0f);
			level.addFreshEntity(d);
			WorldshipEntity w = GalactusEntityTypes.WORLDSHIP.create(level);
			w.moveTo(far.x, far.y + 10, far.z + 4, 0f, 0f);
			level.addFreshEntity(w);
			Zombie z = EntityType.ZOMBIE.create(level);
			z.setNoAi(true);
			z.setPersistenceRequired();
			z.moveTo(far.x, far.y, far.z, 0f, 0f);
			level.addFreshEntity(z);
			made[0] = d;
			made[1] = w;
			made[2] = z;
		}).thenWaitUntil(() -> {
			for (Entity e : made) {
				helper.assertTrue(tracker(level, e) != null, e.getType().toShortString() + " is in the chunk map");
				tracker(level, e).projecthero$updatePlayer(p);
			}
			helper.assertTrue(tracker(level, made[0]).projecthero$sees(p), "the Devourer 120 blocks out is sent at view distance 2");
			helper.assertTrue(tracker(level, made[1]).projecthero$sees(p), "the Worldship 120 blocks out is sent at view distance 2");
		}).thenExecute(() -> {
			helper.assertFalse(tracker(level, made[2]).projecthero$sees(p), "a plain zombie at the same spot is not sent (the vanilla rule still applies to it)");
			helper.assertFalse(made[0].isAlwaysTicking(), "on the server a giant still only ticks in an entity-ticking chunk");
			for (Entity e : made) {
				e.discard();
			}
		}).thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void aGiantBeyondItsOwnTrackingRangeIsNotSent(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = player(helper);
		// the Devourer tracks 32 chunks (512 blocks at the default broadcast range): put him well past that
		Vec3 far = p.position().add(0, 20, 640);
		GiantTracking.keepLoaded(level, far);
		DevourerEntity[] d = new DevourerEntity[1];
		helper.startSequence().thenWaitUntil(() -> {
			GiantTracking.keepLoaded(level, far);
			helper.assertTrue(level.isPositionEntityTicking(BlockPos.containing(far)), "the far chunk loads");
		}).thenExecute(() -> {
			d[0] = GalactusEntityTypes.DEVOURER.create(level);
			d[0].moveTo(far.x, far.y, far.z, 0f, 0f);
			level.addFreshEntity(d[0]);
		}).thenWaitUntil(() -> helper.assertTrue(tracker(level, d[0]) != null, "the Devourer is in the chunk map")).thenExecute(() -> {
			tracker(level, d[0]).projecthero$updatePlayer(p);
			helper.assertFalse(tracker(level, d[0]).projecthero$sees(p), "640 blocks out is past his own tracking range: not sent");
			d[0].discard();
		}).thenSucceed();
	}
}
