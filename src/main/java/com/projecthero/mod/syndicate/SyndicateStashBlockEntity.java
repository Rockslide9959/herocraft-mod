package com.projecthero.mod.syndicate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.25: watches the warehouse. Once a second it looks for a survival player inside the walls (or within 10 blocks of
 * the roller door, about to walk in) and, if it finds one, starts the {@link SyndicateBust}. Nothing is stored -- the
 * block state holds the facing and whether a bust is running.
 */
public class SyndicateStashBlockEntity extends BlockEntity {
	public SyndicateStashBlockEntity(BlockPos pos, BlockState state) {
		super(SyndicateItems.STASH_BLOCK_ENTITY, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, SyndicateStashBlockEntity be) {
		if (!(level instanceof ServerLevel server) || server.getGameTime() % 20 != Math.floorMod(pos.asLong(), 20)) {
			return;
		}
		if (state.getValue(SyndicateStashBlock.ACTIVE) || server.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
			return;
		}
		var forward = state.getValue(SyndicateStashBlock.FACING);
		BlockPos origin = SyndicateHideout.originFromStash(pos, forward);
		Vec3 door = SyndicateHideout.point(origin, forward, 0, 0, -SyndicateHideout.HALF);
		for (ServerPlayer p : server.players()) {
			if (p.isSpectator() || p.isCreative() || !p.isAlive()) {
				continue;
			}
			boolean in = SyndicateHideout.inside(origin, forward, p.position());
			boolean atDoor = p.position().distanceToSqr(door) < 10 * 10 && Math.abs(p.getY() - origin.getY()) < 6;
			if (in || atDoor) {
				SyndicateBust.start(server, pos);
				return;
			}
		}
	}
}
