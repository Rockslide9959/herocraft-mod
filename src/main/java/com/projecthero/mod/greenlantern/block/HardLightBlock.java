package com.projecthero.mod.greenlantern.block;

import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import org.joml.Vector3f;

/**
 * v0.13.21: the block every block-based hard-light construct (Wall, Platform, Bridge, Containment Cage, Carry
 * Platform) is now built from, replacing the plain green/lime stained glass they used before. Translucent
 * Lantern-Corps green with a bright rim on every face, drawn full-bright ({@code emissiveRendering}) so it reads as
 * light rather than glass, gives off a soft light of its own, and occasionally sheds a green mote.
 *
 * <p>It is never obtainable (no item, no loot table), unbreakable by normal means (the construct system's own
 * punch-to-damage / dismiss path is the only way to remove one) and immovable by pistons. <b>Orphan cleanup</b>: every
 * placed cell schedules a block tick; if the construct system no longer tracks that position (a crash or restart
 * while a construct was up, a chunk that was unloaded when its construct ended, ...) the block removes itself, so a
 * hard-light block can never be left behind in a world permanently -- the old glass could.
 *
 * <p>{@link #BRIGHT} is the paler, brighter variant the Carry Platform uses so it stands apart from a plain Platform.
 */
public class HardLightBlock extends TransparentBlock {
	public static final BooleanProperty BRIGHT = BooleanProperty.create("bright");
	/** How often a placed cell re-checks that a live construct still owns it. */
	public static final int ORPHAN_CHECK_TICKS = 40;
	static final DustParticleOptions MOTE = new DustParticleOptions(new Vector3f(0.36f, 1.0f, 0.55f), 0.7f);

	public HardLightBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(BRIGHT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BRIGHT);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		scheduleOrphanCheck(level, pos, this);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		orphanCheck(level, pos, this);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(28) == 0) {
			level.addParticle(MOTE, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
					pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
		}
	}

	// ---------------- shared by every hard-light block ----------------

	static void scheduleOrphanCheck(Level level, BlockPos pos, Block block) {
		if (!level.isClientSide()) {
			level.scheduleTick(pos, block, ORPHAN_CHECK_TICKS);
		}
	}

	/** Removes the block if no live construct owns this cell any more, otherwise re-arms the check. */
	static void orphanCheck(ServerLevel level, BlockPos pos, Block block) {
		if (GreenLanternConstructs.isTrackedCell(level, pos)) {
			level.scheduleTick(pos, block, ORPHAN_CHECK_TICKS);
		} else {
			level.removeBlock(pos, false);
		}
	}
}
