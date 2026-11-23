package com.projecthero.mod.loot.finds;

import java.util.List;

import com.projecthero.mod.loot.HeroVault;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Shared base of the Tier 1 Find pieces: a square footprint of {@code 2*half+1} centred on the chunk middle, a
 * ground level fixed at construction (the generator's own terrain height, so the build sits at the same level in every
 * chunk it crosses), a per-piece seed for deterministic variation, a rotation (0-3 quarter turns), a variant and
 * (v0.17.7) a layout.
 *
 * <p>Pieces are written in <em>local</em> coordinates: {@code (dx, dz)} with the front (door / open side) towards
 * local south (+z); {@link #wx}/{@link #wz} turn them by the piece's rotation, and {@link #rot} turns block states.
 * Every write is clipped to the chunk box being generated.
 *
 * <p>v0.17.7: {@link #layout} is a second, independent choice (the build's floor plan) next to {@link #variant} (its
 * materials / small details). Pieces saved before v0.17.7 have no "Layout" tag and read as layout 0, the old look.
 * {@link #force} pins all three for tests and the screenshot harness.
 */
public abstract class FindPiece extends StructurePiece {
	/** The top block of the ground (one below the first air block). */
	protected final int groundY;
	protected final long seed;
	protected int rotation;
	protected int variant;
	protected int layout;
	protected final int half;
	protected final int cx;
	protected final int cz;

	protected FindPiece(StructurePieceType type, int centerX, int surfaceY, int centerZ, int half, int below, int above, int variants) {
		this(type, centerX, surfaceY, centerZ, half, below, above, variants, 1);
	}

	protected FindPiece(StructurePieceType type, int centerX, int surfaceY, int centerZ, int half, int below, int above, int variants, int layouts) {
		super(type, 0, new BoundingBox(centerX - half, surfaceY - below, centerZ - half, centerX + half, surfaceY + above, centerZ + half));
		this.groundY = surfaceY - 1;
		this.seed = ((long) centerX * 341873128712L) ^ ((long) centerZ * 132897987541L) ^ (half * 0x9E3779B97F4A7C15L);
		this.rotation = (int) Math.floorMod(seed >>> 7, 4L);
		this.variant = (int) Math.floorMod(seed >>> 13, (long) variants);
		this.layout = (int) Math.floorMod(seed >>> 29, (long) Math.max(1, layouts));
		this.half = half;
		this.cx = centerX;
		this.cz = centerZ;
	}

	protected FindPiece(StructurePieceType type, CompoundTag tag) {
		super(type, tag);
		this.groundY = tag.getInt("GroundY");
		this.seed = tag.getLong("Seed");
		this.rotation = tag.getInt("Rot");
		this.variant = tag.getInt("Variant");
		this.layout = tag.getInt("Layout");
		this.half = tag.getInt("Half");
		this.cx = (this.boundingBox.minX() + this.boundingBox.maxX()) / 2;
		this.cz = (this.boundingBox.minZ() + this.boundingBox.maxZ()) / 2;
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putInt("GroundY", groundY);
		tag.putLong("Seed", seed);
		tag.putInt("Rot", rotation);
		tag.putInt("Variant", variant);
		tag.putInt("Layout", layout);
		tag.putInt("Half", half);
	}

	public int variant() {
		return variant;
	}

	public int layout() {
		return layout;
	}

	public int rotation() {
		return rotation;
	}

	public int groundY() {
		return groundY;
	}

	/** Pins the variant, layout and rotation (tests, the harness, placements that must face a cliff). Returns this. */
	public FindPiece force(int variant, int layout, int rotation) {
		this.variant = variant;
		this.layout = layout;
		this.rotation = Math.floorMod(rotation, 4);
		return this;
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
			BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
		build(level, chunkBox);
	}

	/** Builds the part of the find inside {@code box} (the whole find when given {@link #getBoundingBox()}). */
	public abstract void build(WorldGenLevel level, BoundingBox box);

	/** Where the vault is placed (world coordinates), for tests and the harness. */
	public abstract BlockPos vaultPos();

	/** Where the place's themed container is (world coordinates), or null if it has none. */
	public BlockPos stashPos() {
		return null;
	}

	// ---------------------------------------------------------------- helpers

	/** A stable 0..1 value per (x, z, salt). */
	protected double noise(int x, int z, int salt) {
		long h = seed ^ (x * 73856093L) ^ (z * 19349663L) ^ (salt * 83492791L);
		h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
		h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
		return ((h ^ (h >>> 33)) >>> 11) / (double) (1L << 53);
	}

	/** {@link #noise} on local coordinates (stable whatever the rotation). */
	protected double lnoise(int dx, int dz, int salt) {
		return noise(dx * 31 + 7, dz * 17 + 3, salt);
	}

	protected int wx(int dx, int dz) {
		return cx + switch (rotation) {
			case 1 -> -dz;
			case 2 -> -dx;
			case 3 -> dz;
			default -> dx;
		};
	}

	protected int wz(int dx, int dz) {
		return cz + switch (rotation) {
			case 1 -> dx;
			case 2 -> -dz;
			case 3 -> -dx;
			default -> dz;
		};
	}

	protected Rotation rotationEnum() {
		return switch (rotation) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
	}

	/** A local direction turned to world space. */
	protected Direction dir(Direction local) {
		return rotationEnum().rotate(local);
	}

	protected BlockState rot(BlockState state) {
		return state.rotate(rotationEnum());
	}

	protected static boolean inBox(BoundingBox box, int x, int z) {
		return x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
	}

	/** Sets a block at a local position (rotating the state). */
	protected void put(WorldGenLevel level, BoundingBox box, int dx, int y, int dz, BlockState state) {
		putWorld(level, box, wx(dx, dz), y, wz(dx, dz), rot(state));
	}

	protected void putWorld(WorldGenLevel level, BoundingBox box, int x, int y, int z, BlockState state) {
		if (!inBox(box, x, z)) {
			return;
		}
		level.setBlock(new BlockPos(x, y, z), state, 2);
	}

	/** Sets a block only where there is air / a replaceable plant now (so decoration never eats walls). */
	protected void putSoft(WorldGenLevel level, BoundingBox box, int dx, int y, int dz, BlockState state) {
		int x = wx(dx, dz), z = wz(dx, dz);
		if (!inBox(box, x, z)) {
			return;
		}
		BlockPos at = new BlockPos(x, y, z);
		BlockState now = level.getBlockState(at);
		if (now.isAir() || (now.canBeReplaced() && level.getFluidState(at).isEmpty())) {
			level.setBlock(at, rot(state), 2);
		}
	}

	/** Fills a local cuboid (corners inclusive, any order). */
	protected void fill(WorldGenLevel level, BoundingBox box, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
					put(level, box, x, y, z, state);
				}
			}
		}
	}

	protected BlockState get(WorldGenLevel level, int dx, int y, int dz) {
		return level.getBlockState(new BlockPos(wx(dx, dz), y, wz(dx, dz)));
	}

	/** Fills air / fluid / plants under a local column from {@code fromY} down (at most 6 blocks) with {@code fill}. */
	protected void footing(WorldGenLevel level, BoundingBox box, int dx, int dz, int fromY, BlockState fill) {
		footing(level, box, dx, dz, fromY, fill, 6);
	}

	protected void footing(WorldGenLevel level, BoundingBox box, int dx, int dz, int fromY, BlockState fill, int depth) {
		int x = wx(dx, dz), z = wz(dx, dz);
		if (!inBox(box, x, z)) {
			return;
		}
		BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
		for (int y = fromY; y >= fromY - depth; y--) {
			c.set(x, y, z);
			BlockState s = level.getBlockState(c);
			if (!s.isAir() && level.getFluidState(c).isEmpty() && !s.canBeReplaced()) {
				break;
			}
			level.setBlock(c, fill, 2);
		}
	}

	/** Clears a local column from {@code y0} to {@code y1} (inclusive). */
	protected void clear(WorldGenLevel level, BoundingBox box, int dx, int dz, int y0, int y1) {
		for (int y = y0; y <= y1; y++) {
			put(level, box, dx, y, dz, Blocks.AIR.defaultBlockState());
		}
	}

	/** Places the Tier 1 Hero Vault at a local position, its door towards local {@code facing}. */
	protected void vault(WorldGenLevel level, BoundingBox box, int dx, int y, int dz, Direction facing) {
		int x = wx(dx, dz), z = wz(dx, dz);
		if (!inBox(box, x, z)) {
			return;
		}
		HeroVault.place(level, new BlockPos(x, y, z), 1, dir(facing));
	}

	protected BlockPos localToWorld(int dx, int y, int dz) {
		return new BlockPos(wx(dx, dz), y, wz(dx, dz));
	}

	/** A stair state facing local {@code facing} (rotated by {@link #put}). */
	protected static BlockState stair(Block block, Direction facing, boolean top) {
		return block.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	/** Any block with a horizontal FACING, facing local {@code facing}. */
	protected static BlockState facing(Block block, Direction facing) {
		BlockState s = block.defaultBlockState();
		return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
				: s.hasProperty(BlockStateProperties.FACING) ? s.setValue(BlockStateProperties.FACING, facing) : s;
	}

	/**
	 * A container (chest / barrel / trapped chest) with a loot table at a local position. The table rolls when a player
	 * first opens it (vanilla behaviour), with a per-position seed.
	 */
	protected void lootContainer(WorldGenLevel level, BoundingBox box, int dx, int y, int dz, BlockState container, ResourceKey<LootTable> table) {
		int x = wx(dx, dz), z = wz(dx, dz);
		if (!inBox(box, x, z)) {
			return;
		}
		BlockPos at = new BlockPos(x, y, z);
		level.setBlock(at, rot(container), 2);
		RandomizableContainer.setBlockEntityLootTable(level, RandomSource.create(seed ^ at.asLong()), at, table);
	}

	/** A lectern holding {@code book} at a local position, its reading side towards local {@code facing}. */
	protected void lectern(WorldGenLevel level, BoundingBox box, int dx, int y, int dz, Direction facing, ItemStack book) {
		int x = wx(dx, dz), z = wz(dx, dz);
		if (!inBox(box, x, z)) {
			return;
		}
		put(level, box, dx, y, dz, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing).setValue(LecternBlock.HAS_BOOK, true));
		if (level.getBlockEntity(new BlockPos(x, y, z)) instanceof LecternBlockEntity lectern) {
			lectern.setBook(book);
		}
	}

	/** A written book of translatable pages ({@code key + 1 .. key + pages}). */
	public static ItemStack notes(String title, String author, String key, int pages) {
		List<Filterable<Component>> list = new java.util.ArrayList<>();
		for (int p = 1; p <= pages; p++) {
			list.add(Filterable.passThrough(Component.translatable(key + p)));
		}
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), author, 0, list, true));
		return book;
	}

	/**
	 * A persistent, invulnerable-to-nothing armour stand (with arms, no base plate) at a local block position looking
	 * towards local {@code facing}, wearing {@code head, chest, legs, feet} (null = empty).
	 */
	protected ArmorStand armourStand(WorldGenLevel level, BoundingBox box, int dx, int y, int dz, Direction facing,
			ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
		int x = wx(dx, dz), z = wz(dx, dz);
		if (!inBox(box, x, z)) {
			return null;
		}
		ArmorStand stand = new ArmorStand(level.getLevel(), x + 0.5, y, z + 0.5);
		float yaw = dir(facing).toYRot();
		stand.setYRot(yaw);
		stand.setYBodyRot(yaw);
		stand.setYHeadRot(yaw);
		stand.setShowArms(true);
		stand.setNoBasePlate(true);
		if (head != null) {
			stand.setItemSlot(EquipmentSlot.HEAD, head);
		}
		if (chest != null) {
			stand.setItemSlot(EquipmentSlot.CHEST, chest);
		}
		if (legs != null) {
			stand.setItemSlot(EquipmentSlot.LEGS, legs);
		}
		if (feet != null) {
			stand.setItemSlot(EquipmentSlot.FEET, feet);
		}
		level.addFreshEntity(stand);
		return stand;
	}

	/** The top non-air block of a local column at or below {@code fromY} (down to {@code minY}), or {@code minY - 1}. */
	protected int topSolid(WorldGenLevel level, int dx, int dz, int fromY, int minY) {
		BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos(wx(dx, dz), fromY, wz(dx, dz));
		for (int y = fromY; y >= minY; y--) {
			c.setY(y);
			BlockState s = level.getBlockState(c);
			if (!s.isAir() && !s.canBeReplaced()) {
				return y;
			}
		}
		return minY - 1;
	}
}
