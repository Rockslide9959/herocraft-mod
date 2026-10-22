package com.projecthero.mod.quirk.overhaul;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 revamp: what Overhaul's <b>Restore</b> (Shift+V) can put back -- a per-level ledger of the blocks explosions
 * broke (creepers, TNT, ghast fireballs, the mod's own blasts: {@code OverhaulExplosionMixin} records every
 * {@code Explosion.finalizeExplosion} that interacts with blocks), kept only while somebody in that level holds Overhaul
 * (nobody else can use it), for {@link OverhaulConfig#RESTORE_MEMORY_TICKS}, capped at
 * {@link OverhaulConfig#RESTORE_LEDGER_CAP} per level. Blocks with a block entity (chests, furnaces...) are never
 * recorded: their contents are gone.
 *
 * <p>Static state (holds no level, only its key): dropped by {@link #clearSessionState} through the kit
 * ({@code ServerStateReset} -> {@code QuirkMod.clearSessionState}).
 */
public final class OverhaulRestore {
	public record Broken(BlockPos pos, BlockState state, long at) {
	}

	private static final Map<ResourceKey<Level>, Deque<Broken>> LEDGER = new ConcurrentHashMap<>();

	private OverhaulRestore() {
	}

	public static void clearSessionState() {
		LEDGER.clear();
	}

	/** Somebody in {@code level} holds Overhaul (only then is anything recorded). */
	static boolean anyHolder(ServerLevel level) {
		for (var p : level.players()) {
			if (Quirks.holds(p, Quirk.OVERHAUL)) {
				return true;
			}
		}
		return false;
	}

	/** From {@code OverhaulExplosionMixin}: an explosion is about to break these blocks. */
	public static void recordExplosion(Level level, List<BlockPos> toBlow) {
		if (!(level instanceof ServerLevel sl) || toBlow.isEmpty() || !anyHolder(sl)) {
			return;
		}
		long now = sl.getGameTime();
		for (BlockPos pos : toBlow) {
			BlockState st = sl.getBlockState(pos);
			if (st.isAir() || st.hasBlockEntity() || !st.getFluidState().isEmpty() || com.projecthero.mod.hero.power.p05.GeoTerrain.isTemp(sl, pos)) {
				continue;
			}
			record(sl, pos, st, now);
		}
	}

	/** Records one broken block (tests use this directly). */
	public static void record(ServerLevel level, BlockPos pos, BlockState state, long at) {
		Deque<Broken> d = LEDGER.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>());
		synchronized (d) {
			d.addLast(new Broken(pos.immutable(), state, at));
			while (d.size() > OverhaulConfig.RESTORE_LEDGER_CAP) {
				d.removeFirst();
			}
		}
	}

	/**
	 * Takes (removes) every remembered block within {@code radius} of {@code centre} still worth rebuilding -- the spot
	 * empty again (air / a replaceable plant, no fluid) -- nearest first, at most {@code max}. Stale entries are dropped.
	 */
	public static List<Broken> take(ServerLevel level, Vec3 centre, double radius, int max) {
		Deque<Broken> d = LEDGER.get(level.dimension());
		List<Broken> out = new ArrayList<>();
		if (d == null) {
			return out;
		}
		long now = level.getGameTime();
		double r2 = radius * radius;
		synchronized (d) {
			Iterator<Broken> it = d.iterator();
			while (it.hasNext()) {
				Broken b = it.next();
				if (now - b.at() > OverhaulConfig.RESTORE_MEMORY_TICKS || now < b.at()) {
					it.remove();
					continue;
				}
				if (Vec3.atCenterOf(b.pos()).distanceToSqr(centre) > r2) {
					continue;
				}
				BlockState there = level.getBlockState(b.pos());
				if (!(there.isAir() || there.canBeReplaced()) || there.hasBlockEntity()) {
					it.remove();
					continue;
				}
				out.add(b);
				it.remove();
			}
		}
		out.sort((a, b) -> Double.compare(Vec3.atCenterOf(a.pos()).distanceToSqr(centre), Vec3.atCenterOf(b.pos()).distanceToSqr(centre)));
		if (out.size() > max) {
			// put the far ones back for the next Restore
			List<Broken> rest = out.subList(max, out.size());
			synchronized (d) {
				rest.forEach(d::addLast);
			}
			out = new ArrayList<>(out.subList(0, max));
		}
		return out;
	}

	/** How many blocks the ledger remembers in {@code level} (tests). */
	public static int remembered(ServerLevel level) {
		Deque<Broken> d = LEDGER.get(level.dimension());
		return d == null ? 0 : d.size();
	}
}
