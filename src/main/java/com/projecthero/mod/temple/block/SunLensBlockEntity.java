package com.projecthero.mod.temple.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * v0.17.7: the Sun Lens's beam. Every {@link #STEP} ticks it is traced ({@link #trace}): straight from the lens, turned by
 * mirrors, stopped by anything else with a collision shape, at most {@link #MAX_LENGTH} blocks. It is drawn as a line of
 * gold dust (only while a player is within {@link #SHOW_RANGE}); when it ends on an unbroken Seal of Light that seal
 * breaks. Nothing is saved. Not a combat beam (it hurts nobody), so it stays out of the beam-clash system.
 */
public class SunLensBlockEntity extends BlockEntity {
	public static final int STEP = 5;
	public static final int MAX_LENGTH = 48;
	public static final double SHOW_RANGE = 40.0;
	private static final DustParticleOptions DUST = new DustParticleOptions(new Vector3f(1.0f, 0.86f, 0.35f), 1.1f);

	public SunLensBlockEntity(BlockPos pos, BlockState state) {
		super(TempleBlocks.SUN_LENS_BE, pos, state);
	}

	/** The beam's path: every block centre it passes, and where it ended (the block that stopped it, or null). */
	public record Beam(List<BlockPos> path, BlockPos end) {
	}

	public static Beam trace(Level level, BlockPos lens, Direction facing) {
		List<BlockPos> path = new ArrayList<>();
		BlockPos.MutableBlockPos m = lens.mutable();
		Direction d = facing;
		for (int i = 0; i < MAX_LENGTH; i++) {
			m.move(d);
			BlockState s = level.getBlockState(m);
			path.add(m.immutable());
			if (s.is(TempleBlocks.MIRROR)) {
				Direction out = TempleMirrorBlock.reflect(s.getValue(TempleMirrorBlock.FACING), d);
				if (out == null) {
					return new Beam(path, m.immutable());
				}
				d = out;
				continue;
			}
			if (!s.getCollisionShape(level, m).isEmpty()) {
				return new Beam(path, m.immutable());
			}
		}
		return new Beam(path, null);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, SunLensBlockEntity be) {
		if (!(level instanceof ServerLevel server) || server.getGameTime() % STEP != 0) {
			return;
		}
		if (server.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SHOW_RANGE, false) == null) {
			return;
		}
		Beam beam = trace(server, pos, state.getValue(SunLensBlock.FACING));
		Vec3 prev = Vec3.atCenterOf(pos);
		for (BlockPos p : beam.path()) {
			Vec3 c = Vec3.atCenterOf(p);
			if (p.equals(beam.end()) && !server.getBlockState(p).is(TempleBlocks.MIRROR)) {
				c = prev.add(c.subtract(prev).scale(0.5)); // stop at the face of the block that took the beam
			}
			for (int k = 1; k <= 2; k++) {
				Vec3 at = prev.lerp(c, k / 2.0);
				server.sendParticles(DUST, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
			prev = c;
		}
		if (beam.end() != null) {
			BlockState hit = server.getBlockState(beam.end());
			if (hit.is(TempleBlocks.SEAL) && hit.getValue(TempleSealBlock.KIND) == TempleSealBlock.Kind.LIGHT && !hit.getValue(TempleSealBlock.BROKEN)
					&& server.getBlockEntity(beam.end()) instanceof TempleSealBlockEntity seal) {
				seal.breakSeal(server);
			}
		}
	}
}
