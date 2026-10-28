package com.projecthero.mod.omnitrix.alien.upgrade;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.1 Upgrade: short-lived "jammed" zones where redstone gives no signal ({@code OmnitrixUpgradeEmpMixin}). A jam and its
 * end both re-update every block in the zone, so lamps, dust, pistons and dispensers notice at once. Server-wide (not
 * per player): zones simply lapse; {@link #clearSessionState} drops them on server stop.
 */
public final class UpgradeEmp {
	private record Zone(ResourceKey<Level> dim, BlockPos centre, int radius, long until) {
		boolean holds(BlockPos p) {
			return Math.abs(p.getX() - centre.getX()) <= radius && Math.abs(p.getY() - centre.getY()) <= radius
					&& Math.abs(p.getZ() - centre.getZ()) <= radius;
		}
	}

	private static final List<Zone> ZONES = new CopyOnWriteArrayList<>();
	private static volatile boolean any;

	private UpgradeEmp() {
	}

	/** Jams redstone in the cube of {@code radius} around {@code centre} for {@code ticks}. */
	public static void jam(ServerLevel level, BlockPos centre, int radius, int ticks) {
		ZONES.add(new Zone(level.dimension(), centre.immutable(), radius, level.getGameTime() + ticks));
		any = true;
		refresh(level, centre, radius);
	}

	/** Mixin entry: is {@code pos} inside a live jam? (server levels only; nil cost while there is none) */
	public static boolean jammed(BlockGetter getter, BlockPos pos) {
		if (!any || !(getter instanceof Level level) || level.isClientSide()) {
			return false;
		}
		long now = level.getGameTime();
		for (Zone z : ZONES) {
			if (z.until() > now && z.dim() == level.dimension() && z.holds(pos)) {
				return true;
			}
		}
		return false;
	}

	public static boolean anyJam() {
		return any;
	}

	/** Every server tick: lapsed zones are dropped and their blocks re-updated (power comes back). */
	public static void tick(MinecraftServer server) {
		if (!any) {
			return;
		}
		for (Zone z : ZONES) {
			ServerLevel level = server.getLevel(z.dim());
			if (level == null || level.getGameTime() >= z.until()) {
				ZONES.remove(z);
				if (level != null) {
					refresh(level, z.centre(), z.radius());
				}
			}
		}
		any = !ZONES.isEmpty();
	}

	private static void refresh(ServerLevel level, BlockPos c, int r) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int y = -r - 1; y <= r + 1; y++) {
				for (int z = -r - 1; z <= r + 1; z++) {
					m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					if (!level.isLoaded(m)) {
						continue;
					}
					BlockState s = level.getBlockState(m);
					if (!s.isAir()) {
						BlockPos at = m.immutable();
						level.neighborChanged(at, Blocks.AIR, at);
					}
				}
			}
		}
	}

	public static void clearSessionState() {
		ZONES.clear();
		any = false;
	}
}
