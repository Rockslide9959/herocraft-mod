package com.projecthero.mod.vaultdungeon.block;

import com.mojang.serialization.MapCodec;
import com.projecthero.mod.vaultdungeon.entity.WardenKrossBoss;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.5: a <b>Lockdown Shutter</b> -- the blast plate Warden Kross slams across his arena's doorways during a Lockdown.
 * Unbreakable. Kross lifts them himself when the Lockdown ends or he dies; as a safety net every shutter checks every
 * {@link #CHECK_TICKS} that a Warden in Lockdown is still near and removes itself when none is (so a discarded boss or a
 * reload never leaves a doorway welded shut).
 */
public class LockdownShutterBlock extends Block {
	public static final MapCodec<LockdownShutterBlock> CODEC = simpleCodec(LockdownShutterBlock::new);
	public static final int CHECK_TICKS = 100;
	public static final double WARDEN_SEARCH = 40.0;

	public LockdownShutterBlock(BlockBehaviour.Properties props) {
		super(props);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
		super.onPlace(state, level, pos, old, moving);
		if (!level.isClientSide()) {
			level.scheduleTick(pos, this, CHECK_TICKS);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		boolean held = !level.getEntitiesOfClass(WardenKrossBoss.class, new AABB(pos).inflate(WARDEN_SEARCH),
				w -> w.isAlive() && w.inLockdown()).isEmpty();
		if (held) {
			level.scheduleTick(pos, this, CHECK_TICKS);
		} else {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}
	}
}
