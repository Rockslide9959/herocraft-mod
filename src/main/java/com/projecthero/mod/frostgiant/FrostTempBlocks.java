package com.projecthero.mod.frostgiant;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * v0.16.1: every block a Frost Giant Assault puts into the world -- the blizzard's snow layers, Shaman ice walls, Brute
 * spikes, Laufey's ice pillars, frozen water -- tracked in the assault's own saved state so it can all be taken back:
 * on expiry, and on win, loss and abort ({@link #restoreAll}).
 *
 * <p>The rules that keep it safe: a block only ever goes into <b>air</b> or a replaceable non-solid spot (snow onto air
 * above a solid block, ice into air or a still water source), never into a cell a living thing is standing in, never in
 * an unloaded or frozen (non-ticking) chunk; and it is only ever removed if the block there is <b>still the one we
 * placed</b> -- a player's block is never touched. Each entry is mirrored into {@link FrostCleanup}, the server-wide
 * orphan ledger, so a block can still be taken back if its assault vanishes without cleaning up (or its chunk was
 * unloaded at the end).
 */
public final class FrostTempBlocks {
	/** One placed block. {@code expiresAt < 0}: kept until the assault ends. */
	public record Entry(long pos, BlockState placed, BlockState previous, long expiresAt) {
	}

	private final UUID owner;
	private final Map<Long, Entry> entries = new LinkedHashMap<>();
	private int snowCount;

	public FrostTempBlocks(UUID owner) {
		this.owner = owner;
	}

	public int size() {
		return entries.size();
	}

	public int snowCount() {
		return snowCount;
	}

	public boolean tracks(BlockPos pos) {
		return entries.containsKey(pos.asLong());
	}

	public List<BlockPos> positions() {
		List<BlockPos> out = new ArrayList<>();
		for (long l : entries.keySet()) {
			out.add(BlockPos.of(l));
		}
		return out;
	}

	/** Is {@code pos} somewhere we may build right now (loaded, ticking, inside the build height)? */
	public static boolean buildable(ServerLevel level, BlockPos pos) {
		return !level.isOutsideBuildHeight(pos) && level.isLoaded(pos) && level.shouldTickBlocksAt(pos);
	}

	private static boolean occupied(ServerLevel level, BlockPos pos) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), e -> e.isAlive() && !e.isSpectator()).isEmpty();
	}

	/**
	 * Puts {@code state} at {@code pos} for {@code ttl} ticks ({@code < 0}: until the end) if the rules allow it. Ice may go
	 * into a still water source (and turns back into water); everything else only into air.
	 */
	public boolean place(ServerLevel level, BlockPos pos, BlockState state, int ttl) {
		if (!buildable(level, pos)) {
			return false;
		}
		BlockState current = level.getBlockState(pos);
		long key = pos.asLong();
		Entry mine = entries.get(key);
		boolean water = current.is(Blocks.WATER) && current.getFluidState().isSource() && state.is(Blocks.ICE);
		if (mine == null && !current.isAir() && !water) {
			return false;
		}
		if (mine != null && !current.is(mine.placed().getBlock())) {
			entries.remove(key); // someone changed it since -- it is theirs now
			return false;
		}
		if (!state.is(Blocks.SNOW) && occupied(level, pos)) {
			return false;
		}
		BlockState previous = mine != null ? mine.previous() : current;
		level.setBlock(pos, state, Block.UPDATE_ALL);
		long expires = ttl < 0 ? -1 : level.getGameTime() + ttl;
		if (mine != null && mine.expiresAt() < 0 && ttl >= 0) {
			expires = -1;
		}
		entries.put(key, new Entry(key, state, previous, expires));
		if (mine == null && state.is(Blocks.SNOW)) {
			snowCount++;
		}
		FrostCleanup.get(level).add(owner, level, pos, state, previous);
		return true;
	}

	/** One snow layer on top of the solid ground at {@code pos} (air), or a second on our own first, up to {@code maxDepth}. */
	public boolean addSnow(ServerLevel level, BlockPos pos, int maxDepth) {
		BlockState current = level.getBlockState(pos);
		Entry mine = entries.get(pos.asLong());
		if (mine != null && current.is(Blocks.SNOW)) {
			int layers = current.getValue(SnowLayerBlock.LAYERS);
			if (layers >= Math.max(1, maxDepth)) {
				return false;
			}
			return place(level, pos, current.setValue(SnowLayerBlock.LAYERS, layers + 1), -1);
		}
		if (!current.isAir() || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
			return false;
		}
		BlockState snow = Blocks.SNOW.defaultBlockState();
		if (!snow.canSurvive(level, pos)) {
			return false;
		}
		return place(level, pos, snow, -1);
	}

	/** Takes back everything whose time is up (at most {@code budget} this call). */
	public void tickExpiry(ServerLevel level, long now, int budget) {
		int done = 0;
		for (Iterator<Entry> it = entries.values().iterator(); it.hasNext() && done < budget;) {
			Entry e = it.next();
			if (e.expiresAt() >= 0 && now >= e.expiresAt()) {
				if (restore(level, e)) {
					it.remove();
					done++;
				}
			}
		}
	}

	/** Melts {@code count} snow layers (the victory thaw). */
	public void meltSnow(ServerLevel level, int count) {
		int done = 0;
		for (Iterator<Entry> it = entries.values().iterator(); it.hasNext() && done < count;) {
			Entry e = it.next();
			if (e.placed().is(Blocks.SNOW) && restore(level, e)) {
				it.remove();
				done++;
				if (done % 8 == 0) {
					BlockPos p = BlockPos.of(e.pos());
					level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 0.2, p.getZ() + 0.5, 1, 0.2, 0.0,
							0.2, 0.01);
				}
			}
		}
	}

	/** Takes back every block now. Ones in unloaded chunks stay in {@link FrostCleanup} for when they load. */
	public void restoreAll(ServerLevel level) {
		for (Entry e : entries.values()) {
			restore(level, e);
		}
		entries.clear();
		snowCount = 0;
	}

	/** True once it is dealt with (restored now, or left to the orphan ledger because its chunk isn't loaded). */
	private boolean restore(ServerLevel level, Entry e) {
		BlockPos pos = BlockPos.of(e.pos());
		if (!level.isLoaded(pos)) {
			return true; // FrostCleanup still holds it and restores it when the chunk is next loaded
		}
		BlockState now = level.getBlockState(pos);
		if (now.is(e.placed().getBlock())) {
			level.setBlock(pos, e.previous(), Block.UPDATE_ALL);
		}
		if (e.placed().is(Blocks.SNOW)) {
			snowCount = Math.max(0, snowCount - 1);
		}
		FrostCleanup.get(level).remove(owner, pos);
		return true;
	}

	// ---------------------------------------------------------------- save

	public ListTag save() {
		ListTag list = new ListTag();
		for (Entry e : entries.values()) {
			CompoundTag t = new CompoundTag();
			t.putLong("P", e.pos());
			t.put("S", NbtUtils.writeBlockState(e.placed()));
			t.put("O", NbtUtils.writeBlockState(e.previous()));
			t.putLong("E", e.expiresAt());
			list.add(t);
		}
		return list;
	}

	public void load(ListTag list, ServerLevel levelOrNull) {
		entries.clear();
		snowCount = 0;
		var blocks = levelOrNull != null ? levelOrNull.holderLookup(Registries.BLOCK)
				: net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup();
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			BlockState placed = NbtUtils.readBlockState(blocks, t.getCompound("S"));
			BlockState previous = NbtUtils.readBlockState(blocks, t.getCompound("O"));
			long pos = t.getLong("P");
			entries.put(pos, new Entry(pos, placed, previous, t.getLong("E")));
			if (placed.is(Blocks.SNOW)) {
				snowCount++;
			}
		}
	}

	/** Gametest hook: every tracked block's position and whether it still holds what we placed. */
	public int stillPlaced(ServerLevel level) {
		int n = 0;
		for (Entry e : entries.values()) {
			if (level.getBlockState(BlockPos.of(e.pos())).is(e.placed().getBlock())) {
				n++;
			}
		}
		return n;
	}

}
