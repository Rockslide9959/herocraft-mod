package com.projecthero.mod.hideout.worldgen;

import java.util.function.Function;

import com.projecthero.mod.hideout.HideoutBoss;
import com.projecthero.mod.hideout.HideoutFaction;
import com.projecthero.mod.hideout.HideoutGuards;
import com.projecthero.mod.hideout.block.HideoutAlarmBlock;
import com.projecthero.mod.hideout.block.HideoutBlocks;
import com.projecthero.mod.hideout.block.HideoutCoreBlock;
import com.projecthero.mod.hideout.block.HideoutSealBlock;
import com.projecthero.mod.hideout.block.HideoutVentBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * v0.17.5: what a hideout builder draws on. Local coordinates: {@code (0, 0, 0)} is the hideout's origin, {@code y = 0}
 * its main floor layer, the entrance faces local north ({@code -z}). Every write is rotated by {@link #rot} into the
 * world and clipped to the chunk box being generated, so a builder just draws the whole hideout every time and each
 * chunk keeps its own share (the same discipline as the other mod structures). Guards and the boss are only spawned by
 * the chunk that contains their feet, so each appears exactly once.
 *
 * <p>Works on a {@code WorldGenLevel} (worldgen) and on a plain {@code ServerLevel} (gametests, the harness).
 */
public final class HideoutCanvas {
	public final ServerLevelAccessor level;
	public final BoundingBox clip;
	public final BlockPos origin;
	public final Rotation rot;
	public final long seed;
	public final HideoutFaction faction;

	public HideoutCanvas(ServerLevelAccessor level, BoundingBox clip, BlockPos origin, Rotation rot, long seed, HideoutFaction faction) {
		this.level = level;
		this.clip = clip;
		this.origin = origin;
		this.rot = rot;
		this.seed = seed;
		this.faction = faction;
	}

	// ---------------------------------------------------------------- coordinates

	/** Local -> world. */
	public BlockPos at(int x, int y, int z) {
		int wx;
		int wz;
		switch (rot) {
			case CLOCKWISE_90 -> {
				wx = -z;
				wz = x;
			}
			case CLOCKWISE_180 -> {
				wx = -x;
				wz = -z;
			}
			case COUNTERCLOCKWISE_90 -> {
				wx = z;
				wz = -x;
			}
			default -> {
				wx = x;
				wz = z;
			}
		}
		return origin.offset(wx, y, wz);
	}

	/** A local horizontal direction -> world. */
	public Direction dir(Direction local) {
		return rot.rotate(local);
	}

	public boolean inClip(BlockPos p) {
		return clip.isInside(p);
	}

	/** A stable 0..1 value per (x, z, salt), the same in every chunk. */
	public double noise(int x, int z, int salt) {
		long h = seed ^ (x * 73856093L) ^ (z * 19349663L) ^ (salt * 83492791L);
		h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
		h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return ((h ^ (h >>> 33)) >>> 11) / (double) (1L << 53);
	}

	public double noise(int x, int y, int z, int salt) {
		return noise(x * 31 + y * 7919, z, salt);
	}

	// ---------------------------------------------------------------- blocks

	public BlockState get(int x, int y, int z) {
		return level.getBlockState(at(x, y, z));
	}

	/** Sets a block (local state, rotated into the world) if it falls in this chunk. */
	public void set(int x, int y, int z, BlockState state) {
		BlockPos p = at(x, y, z);
		if (clip.isInside(p)) {
			level.setBlock(p, state.rotate(rot), 2);
		}
	}

	public void set(int x, int y, int z, net.minecraft.world.level.block.Block block) {
		set(x, y, z, block.defaultBlockState());
	}

	/** Fills the local box (inclusive, any corner order). */
	public void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					set(x, y, z, state);
				}
			}
		}
	}

	public void fill(int x0, int y0, int z0, int x1, int y1, int z1, net.minecraft.world.level.block.Block block) {
		fill(x0, y0, z0, x1, y1, z1, block.defaultBlockState());
	}

	/** Fills with a per-block choice (noise palettes). */
	public void fill(int x0, int y0, int z0, int x1, int y1, int z1, Function<int[], BlockState> pick) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
					BlockState s = pick.apply(new int[] { x, y, z });
					if (s != null) {
						set(x, y, z, s);
					}
				}
			}
		}
	}

	public void air(int x0, int y0, int z0, int x1, int y1, int z1) {
		fill(x0, y0, z0, x1, y1, z1, Blocks.AIR.defaultBlockState());
	}

	/** The four vertical walls of a box (no floor or ceiling). */
	public void walls(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		fill(x0, y0, z0, x1, y1, z0, state);
		fill(x0, y0, z1, x1, y1, z1, state);
		fill(x0, y0, z0, x0, y1, z1, state);
		fill(x1, y0, z0, x1, y1, z1, state);
	}

	/** A hollow room: walls / floor / ceiling of {@code shell}, air inside. */
	public void room(int x0, int y0, int z0, int x1, int y1, int z1, BlockState shell) {
		fill(x0, y0, z0, x1, y1, z1, shell);
		air(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1);
	}

	/**
	 * Solid footing under the local column {@code (x, z)} from {@code y} down to the first solid block (at most
	 * {@code depth} blocks), so nothing floats over a slope.
	 */
	public void footing(int x, int y, int z, int depth, BlockState state) {
		for (int d = 0; d < depth; d++) {
			BlockPos p = at(x, y - d, z);
			if (!clip.isInside(p)) {
				return;
			}
			BlockState here = level.getBlockState(p);
			if (!here.isAir() && level.getFluidState(p).isEmpty() && !here.canBeReplaced()) {
				return;
			}
			level.setBlock(p, state, 2);
		}
	}

	/** A door (both halves) facing {@code facing} (local). */
	public void door(int x, int y, int z, net.minecraft.world.level.block.Block door, Direction facing) {
		BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
		set(x, y, z, lower);
		set(x, y + 1, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	/** A barrel / chest with a loot table. */
	public void container(int x, int y, int z, BlockState state, ResourceKey<LootTable> table) {
		BlockPos p = at(x, y, z);
		if (!clip.isInside(p)) {
			return;
		}
		level.setBlock(p, state.rotate(rot), 2);
		if (level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity c) {
			c.setLootTable(table, seed ^ p.asLong());
		}
	}

	public static ResourceKey<LootTable> table(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, com.projecthero.mod.ProjectHeroMod.id(path));
	}

	// ---------------------------------------------------------------- hideout pieces

	/** The hideout's core (sealed). */
	public void core(int x, int y, int z) {
		set(x, y, z, HideoutBlocks.CORE.defaultBlockState().setValue(HideoutCoreBlock.FACTION, faction).setValue(HideoutCoreBlock.SEALED, true));
	}

	/** World position of the core at local {@code (x, y, z)} (bosses remember it). */
	public BlockPos corePos(int x, int y, int z) {
		return at(x, y, z);
	}

	/** A sealed Hideout Vault whose door faces local {@code facing}. */
	public void sealedVault(int x, int y, int z, Direction facing) {
		set(x, y, z, HideoutBlocks.SEAL.defaultBlockState().setValue(HideoutSealBlock.FACTION, faction).setValue(HideoutSealBlock.FACING, facing));
	}

	public void vent(int x, int y, int z, HideoutVentBlock.Kind kind) {
		set(x, y, z, HideoutBlocks.VENT.defaultBlockState().setValue(HideoutVentBlock.KIND, kind));
	}

	public BlockState alarmState() {
		return HideoutBlocks.ALARM.defaultBlockState().setValue(HideoutAlarmBlock.FACTION, faction);
	}

	/**
	 * A tripwire alarm along local x at height {@code y}, row {@code z}: an alarm block at {@code x0 - 1}, hooks at
	 * {@code x0} (facing east) and {@code x1} (facing west, hung on whatever stands at {@code x1 + 1}), string between.
	 */
	public void tripwireX(int x0, int x1, int y, int z) {
		set(x0 - 1, y, z, alarmState());
		BlockState hook = Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.ATTACHED, true);
		set(x0, y, z, hook.setValue(TripWireHookBlock.FACING, Direction.EAST));
		set(x1, y, z, hook.setValue(TripWireHookBlock.FACING, Direction.WEST));
		BlockState wire = Blocks.TRIPWIRE.defaultBlockState().setValue(TripWireBlock.ATTACHED, true)
				.setValue(TripWireBlock.EAST, true).setValue(TripWireBlock.WEST, true);
		for (int x = x0 + 1; x < x1; x++) {
			set(x, y, z, wire);
		}
	}

	/** As {@link #tripwireX} along local z: the alarm at {@code z0 - 1}, hooks at {@code z0} (south) and {@code z1} (north). */
	public void tripwireZ(int z0, int z1, int y, int x) {
		set(x, y, z0 - 1, alarmState());
		BlockState hook = Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.ATTACHED, true);
		set(x, y, z0, hook.setValue(TripWireHookBlock.FACING, Direction.SOUTH));
		set(x, y, z1, hook.setValue(TripWireHookBlock.FACING, Direction.NORTH));
		BlockState wire = Blocks.TRIPWIRE.defaultBlockState().setValue(TripWireBlock.ATTACHED, true)
				.setValue(TripWireBlock.NORTH, true).setValue(TripWireBlock.SOUTH, true);
		for (int z = z0 + 1; z < z1; z++) {
			set(x, y, z, wire);
		}
	}

	/** A dispenser loaded with arrows, facing local {@code facing} (wire a pressure plate next to it). */
	public void arrowDispenser(int x, int y, int z, Direction facing) {
		BlockPos p = at(x, y, z);
		if (!clip.isInside(p)) {
			return;
		}
		level.setBlock(p, Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, rot.rotate(facing)), 2);
		if (level.getBlockEntity(p) instanceof DispenserBlockEntity d) {
			d.setItem(0, new ItemStack(Items.ARROW, 32));
			d.setItem(4, new ItemStack(Items.ARROW, 32));
		}
	}

	// ---------------------------------------------------------------- mobs

	/** World yaw for a mob facing local {@code facing}. */
	public float yaw(Direction localFacing) {
		return dir(localFacing).toYRot();
	}

	/** A guard standing at local {@code (x, y, z)} (feet), spawned only by the chunk that holds it. */
	public Mob guard(HideoutGuards.Role role, int x, int y, int z, Direction facing, int leash) {
		BlockPos p = at(x, y, z);
		if (!clip.isInside(p)) {
			return null;
		}
		return HideoutGuards.spawn(level, role, p, yaw(facing), leash);
	}

	/** The mini-boss, bound to the core at local {@code core}, spawned only by the chunk that holds its feet. */
	public <T extends Mob & HideoutBoss> T boss(net.minecraft.world.entity.EntityType<T> type, int x, int y, int z, Direction facing, BlockPos core) {
		BlockPos p = at(x, y, z);
		if (!clip.isInside(p)) {
			return null;
		}
		ServerLevel server = level.getLevel();
		T mob = type.create(server);
		if (mob == null) {
			return null;
		}
		float yaw = yaw(facing);
		mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0f);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.STRUCTURE, null);
		mob.hideoutLink().bindCore(core);
		mob.setPersistenceRequired();
		mob.restrictTo(core, 16);
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}
}
