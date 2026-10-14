package com.projecthero.mod.gametest.city;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * v0.16.2 City part A (dev tooling, gametest source set, never shipped): the programmatic builders behind every
 * City NBT template. They started life as the design-preview harness ({@code scratchpad/CityPreviewBuilder}) and are
 * run by {@link CityTemplateExporter} to regenerate {@code data/projecthero/structure/city/**}.
 * <p>
 * Every building is authored in local coordinates: footprint x in [0, W-1], z in [0, D-1], the street-facing front at
 * z = D-1 (south), y = 0 is the first block above the ground (ground = y -1). The exporter adds a one-row apron in
 * front of the southern-most block and the street jigsaw on it, so every template's street side is its max-z edge.
 */
public final class CityBuilder {
	public final ServerLevel level;
	private BlockPos o = BlockPos.ZERO;
	private Rotation rot = Rotation.NONE;
	private final List<BlockPos> placed = new ArrayList<>();
	private final Random rnd = new Random(1234L);
	/** World-space bounds of everything this builder has placed (for the exporter). */
	public int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
	public int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

	/** The city's own villager pool (an alias the City structure points at the biome's vanilla villager pool). */
	public static final String VILLAGERS = "projecthero:city/villagers";
	public static final String GOLEM = "minecraft:village/common/iron_golem";

	public CityBuilder(ServerLevel level) {
		this.level = level;
	}

	public CityBuilder at(BlockPos origin, Rotation r) {
		o = origin;
		rot = r;
		return this;
	}

	public BlockPos origin() {
		return o;
	}

	public Rotation rotation() {
		return rot;
	}

	// ------------------------------------------------------------------ primitives

	private void track(BlockPos q) {
		minX = Math.min(minX, q.getX());
		minY = Math.min(minY, q.getY());
		minZ = Math.min(minZ, q.getZ());
		maxX = Math.max(maxX, q.getX());
		maxY = Math.max(maxY, q.getY());
		maxZ = Math.max(maxZ, q.getZ());
	}

	/** Local to world. */
	public BlockPos world(int x, int y, int z) {
		return p(x, y, z);
	}

	/**
	 * A jigsaw block at a local position, facing {@code front} (local; rotated with the build). Horizontal jigsaws are
	 * "aligned", vertical ones "rollable" (vanilla's convention).
	 */
	public void jigsaw(int x, int y, int z, Direction front, String name, String target, String pool, String finalState) {
		Direction f = front.getAxis().isHorizontal() ? rot.rotate(front) : front;
		net.minecraft.core.FrontAndTop ft = f.getAxis().isHorizontal() ? net.minecraft.core.FrontAndTop.fromFrontAndTop(f, Direction.UP)
				: net.minecraft.core.FrontAndTop.fromFrontAndTop(f, rot.rotate(Direction.NORTH));
		BlockPos q = p(x, y, z);
		level.setBlock(q, Blocks.JIGSAW.defaultBlockState().setValue(net.minecraft.world.level.block.JigsawBlock.ORIENTATION, ft), 2 | 16);
		track(q);
		if (level.getBlockEntity(q) instanceof net.minecraft.world.level.block.entity.JigsawBlockEntity be) {
			be.setName(net.minecraft.resources.ResourceLocation.parse(name));
			be.setTarget(net.minecraft.resources.ResourceLocation.parse(target));
			be.setPool(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEMPLATE_POOL,
					net.minecraft.resources.ResourceLocation.parse(pool)));
			be.setFinalState(finalState);
			be.setJoint(f.getAxis().isHorizontal() ? net.minecraft.world.level.block.entity.JigsawBlockEntity.JointType.ALIGNED
					: net.minecraft.world.level.block.entity.JigsawBlockEntity.JointType.ROLLABLE);
		}
	}

	/** The block currently at a local position, as a jigsaw final_state string. */
	public String stateString(int x, int y, int z) {
		return net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(level.getBlockState(p(x, y, z)));
	}

	/**
	 * A villager spawn point: a floor block (local y = the floor) becomes an upward jigsaw into the City villager pool
	 * (vanilla's {@code minecraft:bottom} connector), keeping the floor block as its final state.
	 */
	public void villagerSpot(int x, int floorY, int z) {
		String floor = stateString(x, floorY, z);
		if (floor.equals("minecraft:air") || floor.equals("minecraft:structure_void")) {
			floor = "minecraft:smooth_stone";
		}
		jigsaw(x, floorY, z, Direction.UP, "minecraft:bottom", "minecraft:bottom", VILLAGERS, floor);
	}

	/** An iron golem spawn point (vanilla's {@code village/common/iron_golem} pool), like a village meeting point. */
	public void golemSpot(int x, int floorY, int z) {
		String floor = stateString(x, floorY, z);
		if (floor.equals("minecraft:air") || floor.equals("minecraft:structure_void")) {
			floor = "minecraft:smooth_stone";
		}
		jigsaw(x, floorY, z, Direction.UP, "minecraft:bottom", "minecraft:bottom", GOLEM, floor);
	}

	private BlockPos p(int x, int y, int z) {
		int rx;
		int rz;
		switch (rot) {
			case CLOCKWISE_90 -> {
				rx = -z;
				rz = x;
			}
			case CLOCKWISE_180 -> {
				rx = -x;
				rz = -z;
			}
			case COUNTERCLOCKWISE_90 -> {
				rx = z;
				rz = -x;
			}
			default -> {
				rx = x;
				rz = z;
			}
		}
		return o.offset(rx, y, rz);
	}

	public void set(int x, int y, int z, BlockState s) {
		BlockPos q = p(x, y, z);
		level.setBlock(q, s.rotate(rot), 2 | 16);
		placed.add(q);
		track(q);
	}

	public void set(int x, int y, int z, Block b) {
		set(x, y, z, b.defaultBlockState());
	}

	public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
					set(x, y, z, s);
				}
			}
		}
	}

	public void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
		fill(x1, y1, z1, x2, y2, z2, b.defaultBlockState());
	}

	/** Hollow vertical perimeter. */
	public void walls(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
		for (int y = y1; y <= y2; y++) {
			for (int x = x1; x <= x2; x++) {
				set(x, y, z1, s);
				set(x, y, z2, s);
			}
			for (int z = z1; z <= z2; z++) {
				set(x1, y, z, s);
				set(x2, y, z, s);
			}
		}
	}

	/** Re-derives connection shapes (panes, fences, walls, stairs) once a build is complete. */
	public void finish() {
		for (BlockPos q : placed) {
			BlockState st = level.getBlockState(q);
			BlockState ns = Block.updateFromNeighbourShapes(st, level, q);
			if (ns != st) {
				level.setBlock(q, ns, 2 | 16);
			}
		}
		placed.clear();
	}

	// ------------------------------------------------------------------ state helpers

	static BlockState st(Block b) {
		return b.defaultBlockState();
	}

	static BlockState stair(Block b, Direction f, boolean top) {
		return b.defaultBlockState().setValue(StairBlock.FACING, f).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	static BlockState slab(Block b, boolean top) {
		return b.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	static BlockState axis(Block b, Direction.Axis a) {
		return b.defaultBlockState().setValue(BlockStateProperties.AXIS, a);
	}

	static BlockState facing(Block b, Direction d) {
		return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, d);
	}

	static BlockState hanging(Block lantern) {
		return lantern.defaultBlockState().setValue(LanternBlock.HANGING, true);
	}

	static BlockState leaves(Block b) {
		return b.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
	}

	static BlockState trap(Block b, Direction f, boolean top, boolean open) {
		return b.defaultBlockState().setValue(TrapDoorBlock.FACING, f).setValue(TrapDoorBlock.HALF, top ? Half.TOP : Half.BOTTOM)
				.setValue(TrapDoorBlock.OPEN, open);
	}

	public void door(int x, int y, int z, Block b, Direction f, boolean rightHinge) {
		BlockState s = b.defaultBlockState().setValue(DoorBlock.FACING, f).setValue(DoorBlock.HINGE, rightHinge ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
		set(x, y, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(x, y + 1, z, s.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	/** Bed with its foot at (x,y,z) and head one block toward {@code f}. */
	public void bed(int x, int y, int z, Block b, Direction f) {
		BlockState s = b.defaultBlockState().setValue(BedBlock.FACING, f);
		set(x, y, z, s.setValue(BedBlock.PART, BedPart.FOOT));
		set(x + f.getStepX(), y, z + f.getStepZ(), s.setValue(BedBlock.PART, BedPart.HEAD));
	}

	/**
	 * A straight flight from the floor at {@code fyA} up to the floor at {@code fyB}, starting at (x0, z0) and climbing
	 * toward {@code up}; the landing is the floor block one past the last step. The upper floor is opened above the
	 * steps that need the headroom. Villagers path up stairs (not ladders), so every bed and job site on an upper floor
	 * needs one of these to be claimed.
	 */
	public void stairRun(int x0, int z0, int fyA, int fyB, Direction up, Block stairs) {
		int h = fyB - fyA;
		for (int k = 0; k < h; k++) {
			int x = x0 + up.getStepX() * k;
			int z = z0 + up.getStepZ() * k;
			set(x, fyA + 1 + k, z, stair(stairs, up, false));
			for (int y = fyA + 2 + k; y <= fyA + 3 + k; y++) {
				if (y != fyB || k < h - 1) {
					set(x, y, z, Blocks.AIR.defaultBlockState());
				}
			}
		}
		// the stair-top block itself replaces the upper floor at k = h - 1; the two before it need the slab cut
		for (int k = Math.max(0, h - 4); k <= h - 2; k++) {
			set(x0 + up.getStepX() * k, fyB, z0 + up.getStepZ() * k, Blocks.AIR.defaultBlockState());
		}
	}

	public void bell(int x, int y, int z) {
		set(x, y, z, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING).setValue(BellBlock.FACING, Direction.SOUTH));
	}

	// ------------------------------------------------------------------ facade helpers

	public enum Side {
		S(Direction.NORTH), N(Direction.SOUTH), E(Direction.WEST), W(Direction.EAST);

		final Direction in;

		Side(Direction in) {
			this.in = in;
		}
	}

	static final Side[] SIDES = Side.values();

	static int len(Side s, int w, int d) {
		return s == Side.S || s == Side.N ? w : d;
	}

	/**
	 * A block on one face of the footprint [x0, x0+w-1] x [z0, z0+d-1]: u runs left to right as seen from
	 * outside, dep 0 is the wall plane, dep 1 one block out.
	 */
	public void face(Side s, int x0, int z0, int w, int d, int u, int y, int dep, BlockState st) {
		switch (s) {
			case S -> set(x0 + u, y, z0 + d - 1 + dep, st);
			case N -> set(x0 + w - 1 - u, y, z0 - dep, st);
			case E -> set(x0 + w - 1 + dep, y, z0 + d - 1 - u, st);
			case W -> set(x0 - dep, y, z0 + u, st);
		}
	}

	/** Upside-down (or upright) stairs ringing the footprint one block out: cornices, sills, eaves. */
	public void cornice(int x0, int z0, int w, int d, int y, Block stairs, boolean top) {
		for (Side s : SIDES) {
			int n = len(s, w, d);
			for (int u = -1; u <= n; u++) {
				face(s, x0, z0, w, d, u, y, 1, stair(stairs, s.in, top));
			}
		}
	}

	public void ring(int x0, int z0, int w, int d, int y, int dep, BlockState st) {
		for (Side s : SIDES) {
			int n = len(s, w, d);
			for (int u = -dep; u < n + dep; u++) {
				face(s, x0, z0, w, d, u, y, dep, st);
			}
		}
	}

	// ------------------------------------------------------------------ 3x5 pixel font

	private static final Map<Character, String[]> FONT = new HashMap<>();

	static {
		FONT.put('A', new String[] {"###", "#.#", "###", "#.#", "#.#"});
		FONT.put('B', new String[] {"##.", "#.#", "##.", "#.#", "##."});
		FONT.put('C', new String[] {"###", "#..", "#..", "#..", "###"});
		FONT.put('D', new String[] {"##.", "#.#", "#.#", "#.#", "##."});
		FONT.put('E', new String[] {"###", "#..", "##.", "#..", "###"});
		FONT.put('F', new String[] {"###", "#..", "##.", "#..", "#.."});
		FONT.put('H', new String[] {"#.#", "#.#", "###", "#.#", "#.#"});
		FONT.put('I', new String[] {"###", ".#.", ".#.", ".#.", "###"});
		FONT.put('K', new String[] {"#.#", "#.#", "##.", "#.#", "#.#"});
		FONT.put('L', new String[] {"#..", "#..", "#..", "#..", "###"});
		FONT.put('M', new String[] {"#...#", "##.##", "#.#.#", "#...#", "#...#"});
		FONT.put('N', new String[] {"#..#", "##.#", "#.##", "#..#", "#..#"});
		FONT.put('O', new String[] {"###", "#.#", "#.#", "#.#", "###"});
		FONT.put('P', new String[] {"###", "#.#", "###", "#..", "#.."});
		FONT.put('R', new String[] {"##.", "#.#", "##.", "#.#", "#.#"});
		FONT.put('S', new String[] {"###", "#..", "###", "..#", "###"});
		FONT.put('T', new String[] {"###", ".#.", ".#.", ".#.", ".#."});
		FONT.put('U', new String[] {"#.#", "#.#", "#.#", "#.#", "###"});
		FONT.put('Y', new String[] {"#.#", "#.#", ".#.", ".#.", ".#."});
		FONT.put(' ', new String[] {"...", "...", "...", "...", "..."});
	}

	/** Pixel text on a face; yTop is the top row, letters 3 wide with a 1-block gap. */
	public void text(Side s, int x0, int z0, int w, int d, String str, int u0, int yTop, int dep, BlockState fg) {
		int adv = 0;
		for (int i = 0; i < str.length(); i++) {
			String[] g = FONT.get(str.charAt(i));
			if (g == null) {
				continue;
			}
			int gw = g[0].length();
			int base = adv;
			adv += gw + 1;
			for (int r = 0; r < 5; r++) {
				for (int c = 0; c < gw; c++) {
					if (g[r].charAt(c) == '#') {
						face(s, x0, z0, w, d, u0 + base + c, yTop - r, dep, fg);
					}
				}
			}
		}
	}

	// ------------------------------------------------------------------ street furniture

	public void streetLight(int x, int z, Direction arm) {
		set(x, 0, z, Blocks.POLISHED_DEEPSLATE);
		for (int y = 1; y <= 4; y++) {
			set(x, y, z, Blocks.POLISHED_DEEPSLATE_WALL);
		}
		set(x, 5, z, slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
		set(x + arm.getStepX(), 5, z + arm.getStepZ(), slab(Blocks.POLISHED_DEEPSLATE_SLAB, false));
		set(x + arm.getStepX(), 4, z + arm.getStepZ(), hanging(Blocks.LANTERN));
	}

	public void parkLamp(int x, int z) {
		set(x, 0, z, Blocks.POLISHED_DEEPSLATE);
		set(x, 1, z, Blocks.POLISHED_DEEPSLATE_WALL);
		set(x, 2, z, Blocks.POLISHED_DEEPSLATE_WALL);
		set(x, 3, z, Blocks.LANTERN);
	}

	public void planterTree(int x, int z, Block log, Block leaves) {
		set(x, -1, z, Blocks.PODZOL);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			set(x + d.getStepX(), 0, z + d.getStepZ(), stair(Blocks.STONE_BRICK_STAIRS, d.getOpposite(), false));
		}
		for (int y = 0; y <= 2; y++) {
			set(x, y, z, axis(log, Direction.Axis.Y));
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int y = 3; y <= 5; y++) {
					double r = Math.sqrt(dx * dx + dz * dz + (y - 3.8) * (y - 3.8) * 1.6);
					if (r <= 2.4 && !(dx == 0 && dz == 0 && y == 3)) {
						set(x + dx, y, z + dz, leaves(leaves));
					}
				}
			}
		}
		set(x, 3, z, axis(log, Direction.Axis.Y));
	}

	public void tree(int x, int z, Block log, Block leaves, int trunk, double radius) {
		for (int y = 0; y < trunk; y++) {
			set(x, y, z, axis(log, Direction.Axis.Y));
		}
		int cy = trunk;
		int ri = (int) Math.ceil(radius);
		for (int dx = -ri; dx <= ri; dx++) {
			for (int dz = -ri; dz <= ri; dz++) {
				for (int dy = -ri; dy <= ri; dy++) {
					double r = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.7);
					if (r <= radius && rnd.nextFloat() > 0.08f && (dx != 0 || dz != 0 || dy > 0)) {
						set(x + dx, cy + dy, z + dz, leaves(leaves));
					}
				}
			}
		}
		for (int dy = -ri; dy <= 0; dy++) {
			set(x, cy + dy, z, axis(log, Direction.Axis.Y));
		}
	}

	public void bench(int x, int z, Direction faces, int length, boolean alongX) {
		for (int i = 0; i < length; i++) {
			int bx = alongX ? x + i : x;
			int bz = alongX ? z : z + i;
			set(bx, 0, bz, stair(Blocks.DARK_OAK_STAIRS, faces.getOpposite(), false));
		}
	}

	private Block asphalt() {
		float f = rnd.nextFloat();
		return f < 0.08f ? Blocks.GRAY_CONCRETE_POWDER : f < 0.12f ? Blocks.ANDESITE : Blocks.GRAY_CONCRETE;
	}

	/**
	 * A street segment: 7-wide asphalt with a dashed centre line, polished-andesite curbs, 3-wide smooth-stone
	 * sidewalks, a streetlight every 12 blocks on both sides. alongX: runs along x, centred on z = c.
	 */
	public void road(boolean alongX, int a1, int a2, int c, boolean lights) {
		for (int a = a1; a <= a2; a++) {
			for (int k = -7; k <= 7; k++) {
				int x = alongX ? a : c + k;
				int z = alongX ? c + k : a;
				int ak = Math.abs(k);
				BlockState s;
				if (ak <= 3) {
					s = st(k == 0 && Math.floorMod(a, 6) < 3 ? Blocks.YELLOW_CONCRETE : asphalt());
				} else if (ak == 4) {
					s = st(Blocks.POLISHED_ANDESITE);
				} else {
					s = st(Math.floorMod(a, 4) == 0 ? Blocks.STONE_BRICKS : Blocks.SMOOTH_STONE);
				}
				set(x, -1, z, s);
			}
			if (lights && Math.floorMod(a, 12) == 6) {
				if (alongX) {
					streetLight(a, c - 5, Direction.SOUTH);
					streetLight(a, c + 5, Direction.NORTH);
				} else {
					streetLight(c - 5, a, Direction.EAST);
					streetLight(c + 5, a, Direction.WEST);
				}
			}
		}
	}

	/** Zebra crossing across a road that runs along {@code alongX}, at position a. */
	public void crosswalk(boolean alongX, int a, int c) {
		for (int da = 0; da < 3; da++) {
			for (int k = -3; k <= 3; k++) {
				if (Math.floorMod(k, 2) == 0) {
					int x = alongX ? a + da : c + k;
					int z = alongX ? c + k : a + da;
					set(x, -1, z, Blocks.WHITE_CONCRETE);
				}
			}
		}
	}

	public void car(int x, int z, Block body, boolean police) {
		// 5 long along x, 2 wide along z
		for (int dz = 0; dz < 2; dz++) {
			for (int dx = 0; dx < 5; dx++) {
				boolean wheel = dx == 0 || dx == 4;
				set(x + dx, 0, z + dz, wheel ? Blocks.BLACK_CONCRETE : body);
			}
			set(x, 1, z + dz, slab(body == Blocks.WHITE_CONCRETE ? Blocks.SMOOTH_QUARTZ_SLAB : Blocks.SMOOTH_STONE_SLAB, false));
			set(x + 4, 1, z + dz, slab(body == Blocks.WHITE_CONCRETE ? Blocks.SMOOTH_QUARTZ_SLAB : Blocks.SMOOTH_STONE_SLAB, false));
			set(x + 1, 1, z + dz, Blocks.BLACK_STAINED_GLASS);
			set(x + 2, 1, z + dz, body);
			set(x + 3, 1, z + dz, Blocks.BLACK_STAINED_GLASS);
			set(x + 2, 2, z + dz, police ? (dz == 0 ? Blocks.RED_STAINED_GLASS : Blocks.BLUE_STAINED_GLASS) : slab(Blocks.SMOOTH_STONE_SLAB, false).getBlock());
		}
		if (!police) {
			set(x + 2, 2, z, slab(Blocks.SMOOTH_STONE_SLAB, false));
			set(x + 2, 2, z + 1, slab(Blocks.SMOOTH_STONE_SLAB, false));
		}
	}

	public void villager(int x, int y, int z, net.minecraft.world.entity.npc.VillagerProfession prof, float yaw) {
		BlockPos q = p(x, y, z);
		net.minecraft.world.entity.npc.Villager v = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
		if (v == null) {
			return;
		}
		v.setVillagerData(v.getVillagerData().setProfession(prof));
		v.moveTo(q.getX() + 0.5, q.getY(), q.getZ() + 0.5, yaw, 0f);
		v.setYHeadRot(yaw);
		v.setYBodyRot(yaw);
		v.setNoAi(true);
		v.setPersistenceRequired();
		level.addFreshEntity(v);
	}

	public void golem(int x, int y, int z, float yaw) {
		BlockPos q = p(x, y, z);
		var g = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(level);
		if (g == null) {
			return;
		}
		g.moveTo(q.getX() + 0.5, q.getY(), q.getZ() + 0.5, yaw, 0f);
		g.setYHeadRot(yaw);
		g.setYBodyRot(yaw);
		g.setNoAi(true);
		level.addFreshEntity(g);
	}

	/** Paves the ground layer under a rectangle. */
	public void pave(int x1, int z1, int x2, int z2, Block a, Block b) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
				set(x, -1, z, (x + z) % 2 == 0 ? a : b);
			}
		}
	}

	// ================================================================== BUILDINGS

	/** 01. Brick apartment tower, 17 x 13, 6 residential floors over shops, ~32 tall. */
	public void apartmentTower() {
		final int W = 17;
		final int D = 13;
		final int floors = 6;
		final int top = 5 + floors * 4; // 29
		pave(-1, -1, W + 1, D + 2, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE);
		// floor slabs and ceilings with a few warm lights
		for (int k = 0; k <= floors; k++) {
			int fy = k == 0 ? 0 : 5 + (k - 1) * 4;
			fill(1, fy, 1, W - 2, fy, D - 2, Blocks.SPRUCE_PLANKS);
		}
		fill(1, top, 1, W - 2, top, D - 2, Blocks.SMOOTH_STONE);
		for (Side s : SIDES) {
			int n = len(s, W, D);
			for (int u = 0; u < n; u++) {
				boolean pier = u % 4 == 0;
				for (int y = 0; y <= top; y++) {
					BlockState b;
					if (y <= 4) {
						// ground floor shops
						if (pier) {
							b = st(Blocks.POLISHED_ANDESITE);
						} else if (y == 0) {
							b = st(Blocks.POLISHED_ANDESITE);
						} else if (y == 4) {
							b = st(Blocks.STONE_BRICKS);
						} else if (s == Side.S || (s == Side.W && y >= 2)) {
							b = st(Blocks.GLASS_PANE);
						} else {
							b = st(y >= 2 && y <= 3 && u % 4 == 2 ? Blocks.GLASS_PANE : Blocks.BRICKS);
						}
					} else {
						int r = (y - 5) % 4;
						if (y == top) {
							b = st(Blocks.STONE_BRICKS);
						} else if (pier) {
							b = st(Blocks.BRICKS);
						} else if (r == 1 || r == 2) {
							b = st(u % 4 == 2 && s != Side.S ? Blocks.BRICKS : Blocks.GLASS_PANE);
							if (s != Side.S && u % 4 != 2) {
								b = st(Blocks.GLASS_PANE);
							}
						} else {
							b = st(Blocks.BRICKS);
						}
					}
					face(s, 0, 0, W, D, u, y, 0, b);
					// front piers stand proud of the wall
					if (s == Side.S && pier && y >= 5 && y < top) {
						face(s, 0, 0, W, D, u, y, 1, st(Blocks.BRICKS));
					}
					// window sills
					if (y > 4 && y < top && (y - 5) % 4 == 0 && !pier) {
						face(s, 0, 0, W, D, u, y, 1, stair(Blocks.STONE_BRICK_STAIRS, s.in, true));
					}
				}
			}
		}
		// shop entrance, awning and signage band
		door(8, 1, D - 1, Blocks.DARK_OAK_DOOR, Direction.NORTH, false);
		for (int u = 1; u < W - 1; u++) {
			if (u % 4 == 0) {
				continue;
			}
			set(u, 4, D, (u / 2) % 2 == 0 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL);
			set(u, 4, D + 1, slab(Blocks.SMOOTH_STONE_SLAB, false));
		}
		set(8, 4, D, Blocks.STONE_BRICKS);
		set(8, 3, D, hanging(Blocks.LANTERN));
		// balconies on the middle bays, alternate floors
		for (int k = 0; k < floors; k++) {
			int fy = 5 + k * 4;
			int bx = k % 2 == 0 ? 5 : 9;
			for (int x = bx; x <= bx + 2; x++) {
				set(x, fy, D, slab(Blocks.SMOOTH_STONE_SLAB, true));
				set(x, fy, D + 1, slab(Blocks.SMOOTH_STONE_SLAB, true));
				set(x, fy + 1, D + 1, Blocks.IRON_BARS);
			}
			set(bx - 1, fy + 1, D + 1, Blocks.IRON_BARS);
			set(bx + 3, fy + 1, D + 1, Blocks.IRON_BARS);
			set(bx + 1, fy + 1, D - 1, Blocks.GLASS_PANE);
		}
		// black iron fire escape on the east wall
		for (int k = 0; k < floors; k++) {
			int fy = 5 + k * 4;
			for (int z = 2; z <= 10; z++) {
				set(W, fy, z, trap(Blocks.IRON_TRAPDOOR, Direction.WEST, false, false));
				set(W + 1, fy, z, trap(Blocks.IRON_TRAPDOOR, Direction.WEST, false, false));
				set(W + 1, fy + 1, z, Blocks.IRON_BARS);
			}
			set(W, fy + 1, 2, Blocks.IRON_BARS);
			set(W, fy + 1, 10, Blocks.IRON_BARS);
			for (int y = fy + 1; y < fy + 4 && k < floors - 1; y++) {
				set(W, y, 3 + (k % 2) * 6, facing(Blocks.LADDER, Direction.EAST));
			}
		}
		// cornice, parapet, roof clutter
		cornice(0, 0, W, D, top, Blocks.STONE_BRICK_STAIRS, true);
		cornice(0, 0, W, D, 4, Blocks.STONE_BRICK_STAIRS, true);
		ring(0, 0, W, D, top + 1, 0, st(Blocks.BRICK_WALL));
		fill(2, top + 1, 2, 4, top + 3, 4, Blocks.BRICKS);
		set(3, top + 4, 3, slab(Blocks.STONE_BRICK_SLAB, false));
		fill(10, top + 1, 3, 11, top + 1, 4, Blocks.SMOOTH_STONE);
		set(10, top + 2, 3, trap(Blocks.IRON_TRAPDOOR, Direction.NORTH, false, false));
		set(11, top + 2, 3, trap(Blocks.IRON_TRAPDOOR, Direction.NORTH, false, false));
		set(10, top + 2, 4, Blocks.COPPER_GRATE);
		set(11, top + 2, 4, Blocks.COPPER_GRATE);
		set(14, top + 1, 9, Blocks.POLISHED_DEEPSLATE_WALL);
		set(14, top + 2, 9, Blocks.LIGHTNING_ROD);
		// warm lights inside
		for (int k = 0; k < floors; k++) {
			int cy = 5 + (k + 1) * 4;
			set(3 + (k * 5) % 11, cy, 6, Blocks.SHROOMLIGHT);
			set(13 - (k * 3) % 9, cy, 9, Blocks.GLOWSTONE);
		}
		// shop interior: counter, shelves
		fill(2, 1, 4, 6, 1, 4, Blocks.SPRUCE_PLANKS);
		fill(2, 2, 4, 6, 2, 4, slab(Blocks.SPRUCE_SLAB, false));
		fill(10, 1, 2, 15, 3, 2, Blocks.BOOKSHELF);
		set(4, 3, 6, hanging(Blocks.LANTERN));
		set(12, 3, 7, hanging(Blocks.LANTERN));
		set(14, 1, 5, Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		for (int k = 0; k < floors; k++) {
			int by = 6 + k * 4;
			bed(2, by, 2, k % 2 == 0 ? Blocks.RED_BED : Blocks.LIGHT_GRAY_BED, Direction.NORTH);
			bed(14, by, 2, k % 2 == 0 ? Blocks.BLUE_BED : Blocks.WHITE_BED, Direction.NORTH);
		}
		// switchback stairwell at the front-right of every floor (villagers can't use the fire-escape ladders)
		stairRun(9, 10, 0, 5, Direction.EAST, Blocks.SPRUCE_STAIRS);
		for (int k = 1; k < floors; k++) {
			int fa = 5 + (k - 1) * 4;
			if (k % 2 == 1) {
				stairRun(14, 11, fa, fa + 4, Direction.WEST, Blocks.SPRUCE_STAIRS);
			} else {
				stairRun(10, 10, fa, fa + 4, Direction.EAST, Blocks.SPRUCE_STAIRS);
			}
		}
		finish();
		// residents: the deli floor and three of the flats
		villagerSpot(8, 0, 7);
		villagerSpot(8, 5, 7);
		villagerSpot(8, 13, 7);
		villagerSpot(8, 21, 7);
	}

	/** 02. Glass-and-quartz office skyscraper, 16 x 16 with a setback, roof 50, mast to 58. */
	public void officeSkyscraper() {
		final int W = 16;
		final int lowTop = 33;
		final int highTop = 45;
		pave(-1, -1, W, W + 3, Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE);
		// ---- lobby y0..5
		fill(1, 0, 1, W - 2, 0, W - 2, Blocks.POLISHED_DIORITE);
		for (int x = 1; x < W - 1; x++) {
			for (int z = 1; z < W - 1; z++) {
				if ((x + z) % 4 == 0) {
					set(x, 0, z, Blocks.POLISHED_DEEPSLATE);
				}
			}
		}
		for (Side s : SIDES) {
			for (int u = 0; u < W; u++) {
				for (int y = 0; y <= 5; y++) {
					BlockState b;
					if (u == 0 || u == W - 1 || y == 5 || y == 0) {
						b = st(Blocks.POLISHED_DEEPSLATE);
					} else {
						b = st(Blocks.GLASS_PANE);
					}
					face(s, 0, 0, W, W, u, y, 0, b);
					if (s == Side.S && u % 3 == 0 && u > 0 && u < W - 1 && y <= 4) {
						face(s, 0, 0, W, W, u, y, 1, axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
					}
				}
			}
		}
		door(7, 1, W - 1, Blocks.IRON_DOOR, Direction.NORTH, false);
		door(8, 1, W - 1, Blocks.IRON_DOOR, Direction.NORTH, true);
		for (int x = 2; x <= 13; x++) {
			for (int z = W; z <= W + 2; z++) {
				set(x, 5, z, slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
			}
			set(x, 4, W + 2, x % 2 == 0 ? hanging(Blocks.LANTERN) : Blocks.AIR.defaultBlockState());
		}
		// lobby interior
		fill(5, 1, 8, 10, 1, 8, Blocks.SMOOTH_QUARTZ);
		fill(5, 2, 8, 10, 2, 8, slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
		fill(6, 1, 3, 9, 5, 5, Blocks.POLISHED_ANDESITE);
		door(7, 1, 6, Blocks.IRON_DOOR, Direction.NORTH, false);
		door(8, 1, 6, Blocks.IRON_DOOR, Direction.NORTH, true);
		set(2, 1, 13, Blocks.POTTED_FERN);
		set(13, 1, 13, Blocks.POTTED_FERN);
		set(2, 1, 2, Blocks.POTTED_FERN);
		set(13, 1, 2, Blocks.POTTED_FERN);
		set(2, 1, 6, Blocks.CARTOGRAPHY_TABLE);
		set(2, 1, 9, Blocks.CARTOGRAPHY_TABLE);
		set(13, 1, 6, Blocks.LECTERN);
		set(13, 1, 9, Blocks.LECTERN);
		for (int x = 3; x <= 12; x += 3) {
			for (int z = 3; z <= 12; z += 3) {
				set(x, 6, z, Blocks.SEA_LANTERN);
			}
		}
		// ---- curtain-wall shaft
		curtainWall(0, 0, W, 6, lowTop);
		for (int fy = 10; fy <= lowTop; fy += 4) {
			fill(1, fy, 1, W - 2, fy, W - 2, Blocks.SMOOTH_STONE);
			for (int x = 3; x < W - 2; x += 4) {
				for (int z = 3; z < W - 2; z += 4) {
					set(x, fy, z, Blocks.SEA_LANTERN);
				}
			}
		}
		fill(1, 6, 1, W - 2, 6, W - 2, Blocks.SMOOTH_STONE);
		fill(6, 6, 6, 9, lowTop, 9, Blocks.POLISHED_ANDESITE);
		// setback terrace
		fill(1, lowTop + 1, 1, W - 2, lowTop + 1, W - 2, Blocks.SMOOTH_STONE);
		ring(0, 0, W, W, lowTop + 1, 0, st(Blocks.POLISHED_DEEPSLATE));
		cornice(0, 0, W, W, lowTop + 1, Blocks.SMOOTH_QUARTZ_STAIRS, true);
		ring(0, 0, W, W, lowTop + 2, 0, st(Blocks.GLASS_PANE));
		// upper tower 10 x 10 (x3..12)
		curtainWall(3, 3, 10, lowTop + 2, highTop);
		for (int fy = lowTop + 2; fy <= highTop; fy += 4) {
			fill(4, fy, 4, 11, fy, 11, Blocks.SMOOTH_STONE);
			set(6, fy, 6, Blocks.SEA_LANTERN);
			set(9, fy, 9, Blocks.SEA_LANTERN);
		}
		// crown
		ring(3, 3, 10, 10, highTop + 1, 0, st(Blocks.POLISHED_DEEPSLATE));
		ring(3, 3, 10, 10, highTop + 2, 0, st(Blocks.SMOOTH_QUARTZ));
		cornice(3, 3, 10, 10, highTop + 2, Blocks.SMOOTH_QUARTZ_STAIRS, true);
		fill(4, highTop + 2, 4, 11, highTop + 2, 11, Blocks.SMOOTH_STONE);
		ring(3, 3, 10, 10, highTop + 3, 0, st(Blocks.POLISHED_DEEPSLATE_WALL));
		fill(5, highTop + 3, 5, 10, highTop + 5, 10, Blocks.DEEPSLATE_TILES);
		ring(5, 5, 6, 6, highTop + 4, 0, st(Blocks.IRON_BARS));
		cornice(5, 5, 6, 6, highTop + 5, Blocks.DEEPSLATE_TILE_STAIRS, true);
		fill(6, highTop + 6, 6, 9, highTop + 6, 9, Blocks.POLISHED_DEEPSLATE);
		for (int y = highTop + 7; y <= highTop + 12; y++) {
			set(7, y, 7, y < highTop + 10 ? st(Blocks.POLISHED_DEEPSLATE_WALL) : st(Blocks.IRON_BARS));
		}
		set(7, highTop + 13, 7, Blocks.LIGHTNING_ROD);
		set(8, highTop + 7, 8, Blocks.END_ROD);
		set(8, highTop + 8, 8, Blocks.END_ROD);
		finish();
		villagerSpot(4, 0, 11);
		villagerSpot(11, 0, 11);
	}

	/** Curtain wall on a square footprint: dark corner piers, quartz mullions every 3, dark spandrels every 4. */
	private void curtainWall(int x0, int z0, int w, int y1, int y2) {
		for (Side s : SIDES) {
			for (int u = 0; u < w; u++) {
				for (int y = y1; y <= y2; y++) {
					int r = (y - y1) % 4;
					BlockState b;
					if (u == 0 || u == w - 1) {
						b = st(Blocks.POLISHED_DEEPSLATE);
					} else if (u % 3 == 0) {
						b = axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y);
					} else if (r == 0) {
						b = st(Blocks.POLISHED_DEEPSLATE);
					} else {
						b = st(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
					}
					face(s, x0, z0, w, w, u, y, 0, b);
				}
			}
		}
	}

	/** 03. Stone clock tower with four clock faces, belfry with the city bell and a copper spire, 46 tall. */
	public void clockTower() {
		final int W = 9;
		pave(-3, -2, W + 2, W + 2, Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE);
		// base + shaft
		for (Side s : SIDES) {
			for (int u = 0; u < W; u++) {
				for (int y = 0; y <= 24; y++) {
					BlockState b = st(Blocks.STONE_BRICKS);
					boolean corner = u == 0 || u == W - 1;
					if (corner) {
						b = st(Blocks.DEEPSLATE_BRICKS);
					} else if (y == 0 || y == 6) {
						b = st(Blocks.POLISHED_ANDESITE);
					} else if (u == 4 && y >= 8 && y <= 22 && (y - 8) % 6 < 3) {
						b = st(Blocks.GLASS_PANE);
					} else if ((u == 2 || u == 6) && y >= 9 && y <= 21 && (y - 9) % 6 < 2) {
						b = st(Blocks.IRON_BARS);
					}
					face(s, 0, 0, W, W, u, y, 0, b);
					if (corner && y < 24) {
						face(s, 0, 0, W, W, u, y, 1, st(y < 6 ? Blocks.DEEPSLATE_BRICKS : Blocks.STONE_BRICKS));
					}
				}
				face(s, 0, 0, W, W, u, 0, 1, stair(Blocks.STONE_BRICK_STAIRS, s.in, false));
			}
		}
		// arched door, south
		for (int y = 1; y <= 3; y++) {
			set(3, y, W - 1, y < 3 ? Blocks.AIR.defaultBlockState() : stair(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
			set(4, y, W - 1, Blocks.AIR.defaultBlockState());
			set(5, y, W - 1, y < 3 ? Blocks.AIR.defaultBlockState() : stair(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
		}
		set(4, 4, W - 1, Blocks.CHISELED_STONE_BRICKS);
		fill(1, 0, 1, 7, 0, 7, Blocks.POLISHED_ANDESITE);
		set(4, 5, 4, hanging(Blocks.LANTERN));
		fill(1, 6, 1, 7, 6, 7, Blocks.STONE_BRICKS);
		set(4, 5, 4, hanging(Blocks.LANTERN));
		// corbels under the clock stage
		cornice(0, 0, W, W, 24, Blocks.STONE_BRICK_STAIRS, true);
		// clock stage, footprint -1..9 (11 wide), y25..33
		final int cw = 11;
		for (Side s : SIDES) {
			for (int u = 0; u < cw; u++) {
				for (int y = 25; y <= 33; y++) {
					BlockState b = st(Blocks.STONE_BRICKS);
					double dx = u - 5;
					double dy = y - 29;
					double r = Math.sqrt(dx * dx + dy * dy);
					if (u == 0 || u == cw - 1) {
						b = st(Blocks.POLISHED_ANDESITE);
					} else if (y == 25 || y == 33) {
						b = st(Blocks.POLISHED_ANDESITE);
					} else if (r <= 3.3) {
						b = st(Blocks.WHITE_CONCRETE);
						boolean minute = u == 5 && y > 29 && y <= 31;
						boolean hour = y == 29 && u > 5 && u <= 6;
						boolean marks = (u == 5 && (y == 32 || y == 26)) || (y == 29 && (u == 2 || u == 8));
						if (minute || hour || (u == 5 && y == 29)) {
							b = st(Blocks.BLACK_CONCRETE);
						} else if (marks) {
							b = st(Blocks.GOLD_BLOCK);
						}
					} else if (r <= 4.2) {
						b = st(Blocks.POLISHED_BLACKSTONE);
					}
					face(s, -1, -1, cw, cw, u, y, 0, b);
				}
			}
		}
		fill(0, 25, 0, 8, 25, 8, Blocks.STONE_BRICKS);
		fill(0, 33, 0, 8, 33, 8, Blocks.STONE_BRICKS);
		set(4, 32, 4, hanging(Blocks.LANTERN));
		cornice(-1, -1, cw, cw, 33, Blocks.STONE_BRICK_STAIRS, true);
		// belfry y34..38
		for (Side s : SIDES) {
			for (int u = 0; u < W; u++) {
				boolean col = u <= 1 || u >= W - 2;
				for (int y = 34; y <= 37; y++) {
					if (col) {
						face(s, 0, 0, W, W, u, y, 0, st(Blocks.STONE_BRICKS));
					} else if (y == 37) {
						face(s, 0, 0, W, W, u, y, 0, (u == 2 || u == W - 3) ? stair(Blocks.STONE_BRICK_STAIRS, u == 2 ? rightOf(s) : leftOf(s), true)
								: slab(Blocks.STONE_BRICK_SLAB, true));
					} else if (y == 34) {
						face(s, 0, 0, W, W, u, y, 0, st(Blocks.STONE_BRICK_WALL));
					}
				}
			}
		}
		fill(0, 38, 0, 8, 38, 8, Blocks.STONE_BRICKS);
		for (int x = 1; x <= 7; x++) {
			set(x, 37, 4, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
		}
		bell(4, 36, 4);
		set(2, 35, 2, Blocks.LANTERN);
		set(6, 35, 6, Blocks.LANTERN);
		// copper spire
		for (int i = 0; i <= 4; i++) {
			int a = -1 + i;
			int n = W + 2 - 2 * i;
			for (Side s : SIDES) {
				for (int u = 0; u < n; u++) {
					face(s, a, a, n, n, u, 39 + i, 0, stair(Blocks.OXIDIZED_CUT_COPPER_STAIRS, s.in, false));
				}
			}
			if (n > 2) {
				fill(a + 1, 39 + i, a + 1, a + n - 2, 39 + i, a + n - 2, Blocks.OXIDIZED_CUT_COPPER);
			}
		}
		set(4, 44, 4, Blocks.OXIDIZED_CUT_COPPER);
		set(4, 45, 4, Blocks.LIGHTNING_ROD);
		// plaza benches + lamps
		parkLamp(-3, W + 2);
		parkLamp(W + 2, W + 2);
		finish();
		// the clock square's iron golem
		golemSpot(-1, -1, 10);
	}

	private static Direction rightOf(Side s) {
		return s.in.getCounterClockWise();
	}

	private static Direction leftOf(Side s) {
		return s.in.getClockWise();
	}

	/** 04. Police precinct: 26 x 16, three floors, holding cells, rooftop sign, cruiser out front. */
	public void policeStation() {
		final int W = 26;
		final int D = 16;
		pave(-1, -1, W, D + 6, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE);
		fill(1, 0, 1, W - 2, 0, D - 2, Blocks.POLISHED_ANDESITE);
		fill(1, 5, 1, W - 2, 5, D - 2, Blocks.SMOOTH_STONE);
		fill(1, 9, 1, W - 2, 9, D - 2, Blocks.SMOOTH_STONE);
		fill(1, 13, 1, W - 2, 13, D - 2, Blocks.SMOOTH_STONE);
		for (Side s : SIDES) {
			int n = len(s, W, D);
			for (int u = 0; u < n; u++) {
				boolean pier = u % 5 == 0 || u == n - 1;
				int bu = u % 5;
				for (int y = 0; y <= 13; y++) {
					BlockState b;
					if (y == 0 || y == 13) {
						b = st(Blocks.POLISHED_ANDESITE);
					} else if (pier) {
						b = st(Blocks.POLISHED_ANDESITE);
					} else if (y == 5) {
						b = st(Blocks.BLUE_CONCRETE);
					} else if ((bu == 2 || bu == 3) && (y == 2 || y == 3 || y == 6 || y == 7 || y == 10 || y == 11)) {
						b = st(Blocks.GLASS_PANE);
					} else {
						b = st(Blocks.STONE_BRICKS);
					}
					face(s, 0, 0, W, D, u, y, 0, b);
					if (s == Side.S && pier && y < 13 && !(u == 10 || u == 15)) {
						face(s, 0, 0, W, D, u, y, 1, st(Blocks.POLISHED_ANDESITE));
					}
					if (!pier && (bu == 2 || bu == 3) && (y == 1 || y == 9)) {
						face(s, 0, 0, W, D, u, y, 1, stair(Blocks.POLISHED_ANDESITE_STAIRS, s.in, true));
					}
				}
			}
		}
		cornice(0, 0, W, D, 13, Blocks.POLISHED_ANDESITE_STAIRS, true);
		ring(0, 0, W, D, 14, 0, st(Blocks.ANDESITE_WALL));
		// entrance bay u11..14 (x11..14)
		for (int x = 11; x <= 14; x++) {
			for (int y = 1; y <= 4; y++) {
				set(x, y, D - 1, Blocks.GLASS_PANE);
			}
			set(x, 0, D, stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
			set(x, 4, D, slab(Blocks.POLISHED_ANDESITE_SLAB, false));
			set(x, 4, D + 1, slab(Blocks.POLISHED_ANDESITE_SLAB, false));
		}
		door(12, 1, D - 1, Blocks.DARK_OAK_DOOR, Direction.NORTH, false);
		door(13, 1, D - 1, Blocks.DARK_OAK_DOOR, Direction.NORTH, true);
		for (int x : new int[] {10, 15}) {
			set(x, 0, D + 1, Blocks.STONE_BRICKS);
			set(x, 1, D + 1, Blocks.STONE_BRICK_WALL);
			set(x, 2, D + 1, Blocks.SOUL_LANTERN);
		}
		// rooftop sign board
		for (int u = 1; u <= 24; u++) {
			for (int y = 15; y <= 21; y++) {
				face(Side.S, 0, 0, W, D, u, y, -1, st(Blocks.BLUE_CONCRETE));
			}
			face(Side.S, 0, 0, W, D, u, 14, -1, st(Blocks.POLISHED_DEEPSLATE_WALL));
		}
		text(Side.S, 0, 0, W, D, "POLICE", 1, 20, 0, st(Blocks.WHITE_CONCRETE));
		// flagpole
		for (int y = 15; y <= 24; y++) {
			set(1, y, 2, Blocks.END_ROD);
		}
		fill(2, 21, 2, 4, 23, 2, Blocks.BLUE_WOOL);
		set(3, 22, 2, Blocks.WHITE_WOOL);
		// interior: front desk, benches, holding cells with bunks
		fill(7, 1, 9, 18, 1, 9, Blocks.DARK_OAK_PLANKS);
		fill(7, 2, 9, 18, 2, 9, slab(Blocks.DARK_OAK_SLAB, false));
		bench(3, 12, Direction.SOUTH, 4, true);
		bench(19, 12, Direction.SOUTH, 4, true);
		set(22, 1, 7, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		set(23, 1, 7, Blocks.GRINDSTONE);
		set(24, 1, 7, Blocks.SMITHING_TABLE);
		// three Precinct Desks behind the front counter: the City Cops' job sites (projecthero:precinct_desk, City part B)
		for (int x : new int[] {8, 12, 16}) {
			set(x, 1, 7, facingIf(precinctDesk(), Direction.SOUTH));
		}
		for (int x = 1; x <= W - 2; x++) {
			for (int y = 1; y <= 4; y++) {
				set(x, y, 5, x % 5 == 0 ? st(Blocks.STONE_BRICKS) : st(Blocks.IRON_BARS));
			}
		}
		for (int x = 2; x <= 22; x += 5) {
			bed(x, 1, 2, Blocks.LIGHT_GRAY_BED, Direction.NORTH);
			// a steel bench, not a cauldron: a cauldron would be a leatherworker job site in the cells
			set(x + 2, 1, 1, slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
		}
		for (int x = 4; x <= 22; x += 6) {
			set(x, 4, 7, hanging(Blocks.LANTERN));
			set(x, 4, 12, hanging(Blocks.LANTERN));
		}
		for (int k = 0; k < 2; k++) {
			int cy = k == 0 ? 9 : 13;
			for (int x = 4; x <= 22; x += 6) {
				set(x, cy, 8, Blocks.SEA_LANTERN);
			}
		}
		// cruisers
		car(3, D + 4, Blocks.WHITE_CONCRETE, true);
		car(18, D + 4, Blocks.WHITE_CONCRETE, true);
		finish();
		villagerSpot(10, 0, 11);
		villagerSpot(15, 0, 11);
		villagerSpot(20, 0, 8);
	}

	// ------------------------------------------------------------------ City part B blocks (placed by id)

	/** A block registered by the City systems (part B); in part A's branch these are placeholders with the same ids. */
	static Block cityBlock(String path) {
		return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("projecthero", path));
	}

	static Block precinctDesk() {
		return cityBlock("precinct_desk");
	}

	static Block repairBench() {
		return cityBlock("repair_bench");
	}

	static Block safeDeposit() {
		return cityBlock("safe_deposit");
	}

	/** {@code facing} if the block has a horizontal facing, its default state otherwise. */
	static BlockState facingIf(Block b, Direction d) {
		BlockState s = b.defaultBlockState();
		return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(BlockStateProperties.HORIZONTAL_FACING, d) : s;
	}

	/** 05. Neoclassical bank: quartz temple front, six columns, gold lettering, vault inside. */
	public void bank() {
		final int W = 19;
		pave(-1, -1, W, 20, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE);
		// podium y0..1, steps at z17..18
		fill(0, 0, 0, W - 1, 1, 16, Blocks.SMOOTH_QUARTZ);
		for (int x = 1; x <= W - 2; x++) {
			set(x, 0, 18, stair(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.NORTH, false));
			set(x, 0, 17, Blocks.SMOOTH_QUARTZ);
			set(x, 1, 17, stair(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.NORTH, false));
		}
		// core x1..17, z0..11, y2..15
		final int cx0 = 1;
		final int cw = 17;
		final int cd = 12;
		for (Side s : SIDES) {
			int n = len(s, cw, cd);
			for (int u = 0; u < n; u++) {
				for (int y = 2; y <= 15; y++) {
					BlockState b;
					if (u % 4 == 0 || u == n - 1) {
						b = y == 2 ? st(Blocks.QUARTZ_BRICKS) : y >= 14 ? st(Blocks.CHISELED_QUARTZ_BLOCK) : axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y);
					} else if (u % 4 == 2 && y >= 4 && y <= 10 && s != Side.S) {
						b = st(Blocks.GLASS_PANE);
					} else {
						b = st(Blocks.SMOOTH_QUARTZ);
					}
					face(s, cx0, 0, cw, cd, u, y, 0, b);
				}
			}
		}
		// grand door (front wall z11)
		for (int y = 2; y <= 6; y++) {
			for (int x = 8; x <= 10; x++) {
				set(x, y, 11, y <= 5 ? st(Blocks.GLASS_PANE) : st(Blocks.CHISELED_QUARTZ_BLOCK));
			}
		}
		door(9, 2, 11, Blocks.DARK_OAK_DOOR, Direction.NORTH, false);
		set(8, 2, 11, Blocks.DARK_OAK_PLANKS);
		set(10, 2, 11, Blocks.DARK_OAK_PLANKS);
		set(8, 3, 11, Blocks.DARK_OAK_PLANKS);
		set(10, 3, 11, Blocks.DARK_OAK_PLANKS);
		// portico columns (z15)
		for (int x : new int[] {1, 4, 7, 11, 14, 17}) {
			set(x, 2, 15, Blocks.QUARTZ_BRICKS);
			for (int y = 3; y <= 8; y++) {
				set(x, y, 15, axis(Blocks.QUARTZ_PILLAR, Direction.Axis.Y));
			}
			set(x, 9, 15, Blocks.CHISELED_QUARTZ_BLOCK);
			set(x, 2, 12, Blocks.QUARTZ_BRICKS);
		}
		// portico ceiling with chandeliers
		fill(0, 10, 12, W - 1, 10, 16, Blocks.SMOOTH_QUARTZ);
		set(5, 9, 13, hanging(Blocks.LANTERN));
		set(13, 9, 13, hanging(Blocks.LANTERN));
		// entablature y10..16 front face z16 with the gold BANK frieze
		for (int x = 0; x < W; x++) {
			for (int y = 10; y <= 16; y++) {
				set(x, y, 16, Blocks.SMOOTH_QUARTZ);
			}
			set(x, 10, 17, slab(Blocks.SMOOTH_QUARTZ_SLAB, true));
		}
		text(Side.S, 0, 0, W, 17, "BANK", 2, 15, 0, st(Blocks.GOLD_BLOCK));
		for (int z = 0; z <= 16; z++) {
			for (int y = 10; y <= 16; y++) {
				set(0, y, z, Blocks.SMOOTH_QUARTZ);
				set(W - 1, y, z, Blocks.SMOOTH_QUARTZ);
			}
		}
		fill(1, 16, 0, W - 2, 16, 15, Blocks.SMOOTH_QUARTZ);
		cornice(0, 0, W, 17, 16, Blocks.SMOOTH_QUARTZ_STAIRS, true);
		// gable roof along z, ridge at x9
		for (int i = 0; i <= 9; i++) {
			int xl = -1 + i;
			int xr = W - i;
			int y = 17 + i;
			for (int z = -1; z <= 17; z++) {
				if (xl < xr) {
					set(xl, y, z, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.EAST, false));
					set(xr, y, z, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.WEST, false));
				} else if (xl == xr) {
					set(xl, y, z, slab(Blocks.DEEPSLATE_TILE_SLAB, false));
				}
			}
			// pediments
			for (int x = xl + 1; x <= xr - 1; x++) {
				set(x, y, 16, Blocks.SMOOTH_QUARTZ);
				set(x, y, 0, Blocks.SMOOTH_QUARTZ);
			}
		}
		set(9, 19, 17, Blocks.CHISELED_QUARTZ_BLOCK);
		set(9, 20, 17, Blocks.GOLD_BLOCK);
		set(8, 19, 17, stair(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.EAST, false));
		set(10, 19, 17, stair(Blocks.SMOOTH_QUARTZ_STAIRS, Direction.WEST, false));
		// interior: checker marble, teller line, vault
		for (int x = 2; x <= 16; x++) {
			for (int z = 1; z <= 10; z++) {
				set(x, 1, z, (x + z) % 2 == 0 ? Blocks.SMOOTH_QUARTZ : Blocks.POLISHED_BLACKSTONE);
			}
		}
		fill(2, 2, 6, 16, 2, 6, Blocks.DARK_OAK_PLANKS);
		fill(2, 3, 6, 16, 3, 6, slab(Blocks.DARK_OAK_SLAB, false));
		for (int x = 3; x <= 15; x += 3) {
			set(x, 4, 6, Blocks.IRON_BARS);
		}
		fill(2, 2, 4, 16, 12, 4, Blocks.POLISHED_DEEPSLATE);
		// the round vault door stands OPEN: the frame stays in the wall, the opening shows the vault, and the door
		// itself is swung back against the wall beside it (an edge-on iron disc)
		for (int x = 6; x <= 12; x++) {
			for (int y = 2; y <= 8; y++) {
				double r = Math.sqrt((x - 9) * (x - 9) + (y - 5) * (y - 5));
				if (r <= 3.2) {
					set(x, y, 4, r <= 2.2 ? Blocks.AIR.defaultBlockState() : st(Blocks.POLISHED_BLACKSTONE));
				}
			}
		}
		for (int y = 3; y <= 7; y++) {
			set(12, y, 5, y == 5 ? st(Blocks.CHISELED_POLISHED_BLACKSTONE) : st(Blocks.IRON_BLOCK));
		}
		set(12, 2, 5, Blocks.POLISHED_BLACKSTONE);
		for (int x = 3; x <= 15; x++) {
			for (int z = 1; z <= 3; z++) {
				set(x, 2, z, (x + z) % 3 == 0 ? Blocks.GOLD_BLOCK : Blocks.EMERALD_BLOCK);
				if (x % 2 == 1) {
					set(x, 3, z, Blocks.EMERALD_BLOCK);
				}
			}
		}
		// the safe-deposit wall at the back of the vault (projecthero:safe_deposit, City part B), lit from both ends
		for (int x = 5; x <= 13; x++) {
			set(x, 4, 1, facingIf(safeDeposit(), Direction.SOUTH));
			set(x, 5, 1, facingIf(safeDeposit(), Direction.SOUTH));
		}
		set(3, 4, 2, Blocks.LANTERN);
		set(15, 4, 2, Blocks.LANTERN);
		set(9, 7, 2, Blocks.GLOWSTONE);
		set(5, 2, 5, Blocks.LECTERN);
		set(13, 2, 5, Blocks.LECTERN);
		for (int x = 3; x <= 15; x += 4) {
			for (int z = 2; z <= 10; z += 4) {
				set(x, 13, z, Blocks.GLOWSTONE);
			}
		}
		set(3, 3, 6, Blocks.LANTERN);
		set(15, 3, 6, Blocks.LANTERN);
		fill(1, 13, 0, 17, 13, 11, Blocks.SMOOTH_QUARTZ);
		for (int x = 5; x <= 13; x += 4) {
			set(x, 12, 8, Blocks.CHAIN);
			set(x, 11, 8, Blocks.CHAIN);
			set(x, 10, 8, hanging(Blocks.LANTERN));
		}
		set(9, 12, 2, Blocks.GLOWSTONE);
		finish();
		villagerSpot(6, 1, 9);
		villagerSpot(12, 1, 9);
	}

	/** 06. Hospital: 23 x 17 white block, ribbon windows with sun ledges, ambulance canopy, cross + helipad. */
	public void hospital() {
		final int W = 23;
		final int D = 17;
		final int top = 20;
		pave(-1, -1, W, D + 6, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE);
		for (int fy = 0; fy <= top; fy += 4) {
			fill(1, fy, 1, W - 2, fy, D - 2, fy == 0 ? Blocks.POLISHED_DIORITE : Blocks.SMOOTH_STONE);
			if (fy > 0) {
				for (int x = 3; x < W - 2; x += 4) {
					set(x, fy, 4, Blocks.SEA_LANTERN);
					set(x, fy, 12, Blocks.SEA_LANTERN);
				}
			}
		}
		for (Side s : SIDES) {
			int n = len(s, W, D);
			for (int u = 0; u < n; u++) {
				boolean pier = u == 0 || u == n - 1 || u == n / 2;
				for (int y = 0; y <= top; y++) {
					int r = y % 4;
					BlockState b;
					if (pier) {
						b = st(Blocks.LIGHT_GRAY_CONCRETE);
					} else if (r == 1 || r == 2) {
						b = st(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
					} else {
						b = st(Blocks.WHITE_CONCRETE);
					}
					if (y == 0) {
						b = st(Blocks.LIGHT_GRAY_CONCRETE);
					}
					face(s, 0, 0, W, D, u, y, 0, b);
					if (pier && y < top) {
						face(s, 0, 0, W, D, u, y, 1, st(Blocks.LIGHT_GRAY_CONCRETE));
					}
					if (!pier && r == 3 && y < top) {
						face(s, 0, 0, W, D, u, y, 1, slab(Blocks.SMOOTH_QUARTZ_SLAB, true));
					}
				}
			}
		}
		ring(0, 0, W, D, top + 1, 0, st(Blocks.WHITE_CONCRETE));
		cornice(0, 0, W, D, top, Blocks.SMOOTH_QUARTZ_STAIRS, true);
		// entrance + ambulance canopy
		for (int x = 8; x <= 14; x++) {
			for (int y = 1; y <= 3; y++) {
				set(x, y, D - 1, Blocks.GLASS_PANE);
			}
		}
		door(10, 1, D - 1, Blocks.IRON_DOOR, Direction.NORTH, false);
		door(12, 1, D - 1, Blocks.IRON_DOOR, Direction.NORTH, true);
		for (int x = 5; x <= 17; x++) {
			for (int z = D; z <= D + 5; z++) {
				set(x, 4, z, slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
			}
			set(x, 4, D + 6, Blocks.RED_CONCRETE);
		}
		for (int x : new int[] {5, 17}) {
			for (int y = 0; y <= 3; y++) {
				set(x, y, D + 5, Blocks.WHITE_CONCRETE);
			}
		}
		set(8, 3, D + 3, hanging(Blocks.LANTERN));
		set(14, 3, D + 3, hanging(Blocks.LANTERN));
		// ambulance (white with red stripe)
		for (int dz = 0; dz < 2; dz++) {
			for (int dx = 0; dx < 6; dx++) {
				set(8 + dx, 0, D + 2 + dz, dx == 0 || dx == 5 ? Blocks.BLACK_CONCRETE : Blocks.WHITE_CONCRETE);
				set(8 + dx, 1, D + 2 + dz, dx == 5 ? Blocks.BLACK_STAINED_GLASS : Blocks.RED_CONCRETE);
				if (dx < 5) {
					set(8 + dx, 2, D + 2 + dz, Blocks.WHITE_CONCRETE);
				}
			}
			set(13, 2, D + 2 + dz, slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
			set(10, 3, D + 2 + dz, dz == 0 ? Blocks.RED_STAINED_GLASS : Blocks.BLUE_STAINED_GLASS);
		}
		// rooftop cross sign 9x9
		for (int u = 7; u <= 15; u++) {
			for (int y = top + 2; y <= top + 10; y++) {
				boolean cross = (u >= 10 && u <= 12 && y >= top + 3 && y <= top + 9) || (y >= top + 5 && y <= top + 7 && u >= 8 && u <= 14);
				face(Side.S, 0, 0, W, D, u, y, -1, st(cross ? Blocks.RED_CONCRETE : Blocks.WHITE_CONCRETE));
			}
			face(Side.S, 0, 0, W, D, u, top + 1, -1, st(Blocks.LIGHT_GRAY_CONCRETE));
		}
		// helipad
		for (int x = 3; x <= 19; x++) {
			for (int z = 3; z <= 13; z++) {
				double r = Math.sqrt((x - 11) * (x - 11) + (z - 8) * (z - 8));
				if (r <= 5.4) {
					set(x, top, z, r >= 4.5 ? Blocks.YELLOW_CONCRETE : Blocks.GRAY_CONCRETE);
				}
			}
		}
		for (int dz = -2; dz <= 2; dz++) {
			set(9, top, 8 + dz, Blocks.WHITE_CONCRETE);
			set(13, top, 8 + dz, Blocks.WHITE_CONCRETE);
		}
		for (int dx = 10; dx <= 12; dx++) {
			set(dx, top, 8, Blocks.WHITE_CONCRETE);
		}
		// ward interior: beds, brewing stands (cleric job sites)
		for (int x = 2; x <= 20; x += 3) {
			bed(x, 1, 2, x % 2 == 0 ? Blocks.WHITE_BED : Blocks.LIGHT_BLUE_BED, Direction.NORTH);
		}
		fill(4, 1, 9, 18, 1, 9, Blocks.WHITE_CONCRETE);
		fill(4, 2, 9, 18, 2, 9, slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
		set(6, 2, 9, Blocks.BREWING_STAND);
		set(16, 2, 9, Blocks.BREWING_STAND);
		finish();
		villagerSpot(5, 0, 12);
		villagerSpot(17, 0, 12);
	}

	/** 07. Fire station: two-bay red-brick hall with a calcite trim and a vertical-lettered hose tower. */
	public void fireStation() {
		final int W = 17;
		final int D = 17;
		pave(-2, -1, W + 7, D + 6, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE);
		fill(1, 0, 1, W - 2, 0, D - 2, Blocks.SMOOTH_STONE);
		fill(1, 6, 1, W - 2, 6, D - 2, Blocks.SPRUCE_PLANKS);
		fill(1, 11, 1, W - 2, 11, D - 2, Blocks.SMOOTH_STONE);
		for (Side s : SIDES) {
			int n = len(s, W, D);
			for (int u = 0; u < n; u++) {
				for (int y = 0; y <= 11; y++) {
					BlockState b;
					boolean corner = u == 0 || u == n - 1;
					if (corner || y == 0 || y == 6 || y == 11) {
						b = st(Blocks.CALCITE);
					} else if (y >= 7 && y <= 9 && u % 4 == 2) {
						b = y == 9 ? stair(Blocks.BRICK_STAIRS, s.in, true) : st(Blocks.GLASS_PANE);
					} else if (y >= 7 && y <= 9 && (u % 4 == 1 || u % 4 == 3)) {
						b = y == 9 ? st(Blocks.BRICKS) : st(Blocks.GLASS_PANE);
					} else {
						b = st(Blocks.BRICKS);
					}
					face(s, 0, 0, W, D, u, y, 0, b);
				}
			}
		}
		// apparatus bays on the front
		for (int bay = 0; bay < 2; bay++) {
			int x0 = bay == 0 ? 2 : 10;
			for (int x = x0 - 1; x <= x0 + 5; x++) {
				for (int y = 0; y <= 6; y++) {
					boolean frame = x == x0 - 1 || x == x0 + 5 || y == 6;
					if (frame) {
						set(x, y, D - 1, Blocks.CALCITE);
						set(x, y, D, Blocks.CALCITE);
					} else if (bay == 0) {
						set(x, y, D - 1, Blocks.AIR.defaultBlockState());
					} else {
						set(x, y, D - 1, y == 4 ? st(Blocks.GLASS_PANE) : y % 2 == 0 ? st(Blocks.RED_CONCRETE) : st(Blocks.RED_TERRACOTTA));
					}
				}
			}
			for (int x = x0 - 1; x <= x0 + 5; x++) {
				set(x, -1, D, Blocks.SMOOTH_STONE);
			}
		}
		cornice(0, 0, W, D, 11, Blocks.STONE_BRICK_STAIRS, true);
		ring(0, 0, W, D, 12, 0, st(Blocks.BRICK_WALL));
		// fire engine in the open bay, facing out
		for (int z = 4; z <= D - 1; z++) {
			for (int x = 2; x <= 6; x++) {
				boolean wheelRow = z == 6 || z == 13;
				boolean edge = x == 2 || x == 6;
				if (x == 4 || (!edge && !wheelRow)) {
					set(x, 0, z, Blocks.RED_CONCRETE);
				}
				if (edge && wheelRow) {
					set(x, 0, z, Blocks.BLACK_CONCRETE);
				}
				if (x >= 3 && x <= 5) {
					set(x, 1, z, z >= D - 3 ? st(Blocks.BLACK_STAINED_GLASS) : st(Blocks.RED_CONCRETE));
					if (z < D - 3) {
						set(x, 2, z, x == 4 ? st(Blocks.IRON_BARS) : slab(Blocks.SMOOTH_STONE_SLAB, false));
					} else {
						set(x, 2, z, Blocks.RED_CONCRETE);
					}
				}
			}
		}
		set(3, 3, D - 2, Blocks.RED_STAINED_GLASS);
		set(5, 3, D - 2, Blocks.RED_STAINED_GLASS);
		// hose tower beside the hall, x17..21 z11..15
		final int tx = 17;
		final int tz = 9;
		final int tw = 7;
		final int th = 30;
		for (Side s : SIDES) {
			for (int u = 0; u < tw; u++) {
				for (int y = 0; y <= th; y++) {
					BlockState b = (u == 0 || u == tw - 1 || y == 0 || y == th) ? st(Blocks.CALCITE) : st(Blocks.BRICKS);
					face(s, tx, tz, tw, tw, u, y, 0, b);
				}
			}
		}
		text(Side.S, tx, tz, tw, tw, "F", 2, 28, 0, st(Blocks.WHITE_CONCRETE));
		text(Side.S, tx, tz, tw, tw, "I", 2, 22, 0, st(Blocks.WHITE_CONCRETE));
		text(Side.S, tx, tz, tw, tw, "R", 2, 16, 0, st(Blocks.WHITE_CONCRETE));
		text(Side.S, tx, tz, tw, tw, "E", 2, 10, 0, st(Blocks.WHITE_CONCRETE));
		for (int y = 1; y <= 4; y++) {
			set(tx + 3, y, tz + tw - 1, y <= 2 ? Blocks.AIR.defaultBlockState() : st(Blocks.CALCITE));
		}
		door(tx + 3, 1, tz + tw - 1, Blocks.SPRUCE_DOOR, Direction.NORTH, false);
		// lookout and roof
		for (Side s : SIDES) {
			for (int u = 0; u < tw; u++) {
				face(s, tx, tz, tw, tw, u, th + 1, 0, u == 0 || u == tw - 1 ? st(Blocks.CALCITE) : st(Blocks.SPRUCE_FENCE));
				face(s, tx, tz, tw, tw, u, th + 2, 0, u == 0 || u == tw - 1 ? st(Blocks.CALCITE) : Blocks.AIR.defaultBlockState());
			}
		}
		for (int i = 0; i <= 4; i++) {
			int a = -1 + i;
			int nn = tw + 2 - 2 * i;
			if (nn <= 0) {
				break;
			}
			for (Side s : SIDES) {
				for (int u = 0; u < nn; u++) {
					face(s, tx + a, tz + a, nn, nn, u, th + 3 + i, 0, stair(Blocks.DEEPSLATE_TILE_STAIRS, s.in, false));
				}
			}
		}
		set(tx + 3, th + 7, tz + 3, Blocks.DEEPSLATE_TILES);
		set(tx + 3, th + 8, tz + 3, Blocks.LIGHTNING_ROD);
		set(tx + 3, th + 2, tz + 3, hanging(Blocks.LANTERN));
		fill(tx + 1, th + 3, tz + 1, tx + 5, th + 3, tz + 5, Blocks.DEEPSLATE_TILES);
		// apron details: hydrant, cones, lights
		set(-1, 0, D + 1, Blocks.RED_NETHER_BRICK_WALL);
		set(W + 1, 0, D + 1, Blocks.RED_NETHER_BRICK_WALL);
		for (int x = 1; x <= 15; x += 7) {
			set(x, 5, D, Blocks.LANTERN);
		}
		set(4, 5, 3, hanging(Blocks.LANTERN));
		set(12, 5, 3, hanging(Blocks.LANTERN));
		set(9, 1, 2, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		set(10, 1, 2, Blocks.WATER_CAULDRON.defaultBlockState().setValue(BlockStateProperties.LEVEL_CAULDRON, 3));
		for (int x = 2; x <= 14; x += 3) {
			bed(x, 7, 2, Blocks.RED_BED, Direction.NORTH);
		}
		set(8, 10, 8, Blocks.GLOWSTONE);
		// stairs up to the crew bunks along the east wall
		stairRun(15, 13, 0, 6, Direction.NORTH, Blocks.SPRUCE_STAIRS);
		finish();
		villagerSpot(12, 0, 10);
		villagerSpot(8, 6, 8);
	}

	/** 08. Market square in front of a two-storey mall with a glass atrium; stalls host vanilla job sites. */
	public void marketMall() {
		final int W = 31;
		final int D = 14;
		// plaza paving
		for (int x = -2; x <= W + 1; x++) {
			for (int z = D; z <= D + 18; z++) {
				boolean border = x == -2 || x == W + 1 || z == D + 18;
				set(x, -1, z, border ? Blocks.STONE_BRICKS : ((x / 2 + z / 2) % 2 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE));
			}
		}
		for (int x = 0; x < W; x++) {
			for (int z = 0; z < D; z++) {
				set(x, -1, z, Blocks.SMOOTH_STONE);
			}
		}
		fill(1, 0, 1, W - 2, 0, D - 2, Blocks.POLISHED_DIORITE);
		fill(1, 5, 1, W - 2, 5, D - 2, Blocks.SMOOTH_STONE);
		for (int x = 2; x < W - 1; x += 5) {
			set(x, 4, 4, hanging(Blocks.LANTERN));
			set(x, 4, 9, hanging(Blocks.LANTERN));
		}
		for (Side s : SIDES) {
			int n = len(s, W, D);
			for (int u = 0; u < n; u++) {
				boolean pier = u % 6 == 0 || u == n - 1;
				for (int y = 0; y <= 10; y++) {
					BlockState b;
					if (pier) {
						b = st(Blocks.CUT_COPPER);
					} else if (y == 0 || y == 10) {
						b = st(Blocks.WHITE_TERRACOTTA);
					} else if (y == 5) {
						b = st(Blocks.TERRACOTTA);
					} else if (y <= 4) {
						b = s == Side.S || y >= 2 ? st(Blocks.GLASS_PANE) : st(Blocks.WHITE_TERRACOTTA);
					} else if (y >= 6 && y <= 8 && u % 2 == 1) {
						b = st(Blocks.GLASS_PANE);
					} else {
						b = st(Blocks.WHITE_TERRACOTTA);
					}
					face(s, 0, 0, W, D, u, y, 0, b);
					if (s == Side.S && pier && y <= 10) {
						face(s, 0, 0, W, D, u, y, 1, st(Blocks.CUT_COPPER));
					}
				}
			}
		}
		// shop awnings in five colours
		Block[] awn = {Blocks.RED_WOOL, Blocks.LIME_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL};
		for (int bay = 0; bay < 5; bay++) {
			for (int u = bay * 6 + 1; u <= bay * 6 + 5; u++) {
				set(u, 4, D, u % 2 == 0 ? Blocks.WHITE_WOOL : awn[bay]);
				set(u, 4, D + 1, slab(Blocks.SMOOTH_STONE_SLAB, false));
			}
			door(bay * 6 + 3, 1, D - 1, Blocks.SPRUCE_DOOR, Direction.NORTH, false);
		}
		cornice(0, 0, W, D, 10, Blocks.SMOOTH_SANDSTONE_STAIRS, true);
		// MALL sign board + glass atrium
		// the sign: white raised letters on a sky-blue wool board in a striped awning-colour frame, with a white
		// terracotta back (the preview's black board read as a black slab from every angle)
		Block[] stripe = {Blocks.RED_WOOL, Blocks.WHITE_WOOL, Blocks.LIME_WOOL, Blocks.WHITE_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.WHITE_WOOL,
				Blocks.ORANGE_WOOL, Blocks.WHITE_WOOL, Blocks.MAGENTA_WOOL, Blocks.WHITE_WOOL};
		for (int u = 5; u <= 25; u++) {
			for (int y = 11; y <= 18; y++) {
				boolean frame = u == 5 || u == 25 || y == 11 || y == 18;
				face(Side.S, 0, 0, W, D, u, y, 0, st(frame ? stripe[Math.floorMod(u + y, stripe.length)] : Blocks.LIGHT_BLUE_WOOL));
				face(Side.S, 0, 0, W, D, u, y, -1, st(Blocks.WHITE_TERRACOTTA));
			}
		}
		text(Side.S, 0, 0, W, D, "MALL", 7, 16, 1, st(Blocks.WHITE_CONCRETE));
		// little shopping bags either side of the lettering
		for (int[] bag : new int[][] {{6, 0}, {24, 1}}) {
			Block c = bag[1] == 0 ? Blocks.MAGENTA_CONCRETE : Blocks.ORANGE_CONCRETE;
			face(Side.S, 0, 0, W, D, bag[0], 13, 1, st(c));
			face(Side.S, 0, 0, W, D, bag[0], 14, 1, st(c));
			face(Side.S, 0, 0, W, D, bag[0], 15, 1, st(Blocks.WHITE_CONCRETE));
		}
		for (int x = 10; x <= 20; x++) {
			for (int z = 2; z <= 11; z++) {
				for (int y = 11; y <= 18; y++) {
					boolean edgeX = x == 10 || x == 20;
					boolean edgeZ = z == 2 || z == 11;
					if (y == 18 || edgeX || edgeZ) {
						boolean frame = (edgeX && edgeZ) || (y == 18 && (edgeX || edgeZ)) || (y == 18 && x % 3 == 0);
						set(x, y, z, frame ? Blocks.WAXED_CUT_COPPER : Blocks.GLASS);
					}
				}
			}
		}
		fill(1, 10, 1, W - 2, 10, D - 2, Blocks.SMOOTH_STONE);
		// market stalls with job-site blocks
		Block[][] stalls = {
				{Blocks.RED_WOOL, Blocks.SMOKER},
				{Blocks.YELLOW_WOOL, Blocks.COMPOSTER},
				{Blocks.BLUE_WOOL, Blocks.CARTOGRAPHY_TABLE},
				{Blocks.GREEN_WOOL, Blocks.FLETCHING_TABLE},
				{Blocks.PURPLE_WOOL, Blocks.LOOM},
				{Blocks.ORANGE_WOOL, Blocks.BARREL}};
		for (int i = 0; i < 6; i++) {
			int sx = 1 + (i % 3) * 11;
			int sz = D + 4 + (i / 3) * 8;
			if (i % 3 == 1) {
				sx = 13;
			}
			stall(sx, sz, stalls[i][0], stalls[i][1]);
		}
		// central bell post (the village meeting point)
		int bx = 15;
		int bz = D + 10;
		fill(bx - 1, 0, bz - 1, bx + 1, 0, bz + 1, Blocks.STONE_BRICKS);
		for (int y = 1; y <= 3; y++) {
			set(bx - 1, y, bz, Blocks.STONE_BRICK_WALL);
			set(bx + 1, y, bz, Blocks.STONE_BRICK_WALL);
		}
		for (int x = bx - 1; x <= bx + 1; x++) {
			set(x, 4, bz, x == bx ? st(Blocks.STONE_BRICKS) : slab(Blocks.STONE_BRICK_SLAB, false));
			set(x, 5, bz, stair(Blocks.OXIDIZED_CUT_COPPER_STAIRS, Direction.SOUTH, false));
		}
		bell(bx, 3, bz);
		// v0.16.2: the Food Bank (projecthero:food_bank) between the west stalls and the bell, a hay bale beside it
		set(10, 0, bz, facingIf(cityBlock("food_bank"), Direction.EAST));
		set(10, 0, bz + 1, Blocks.HAY_BLOCK);
		planterTree(-1 + 1, D + 16, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
		planterTree(W - 2, D + 16, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
		parkLamp(7, D + 10);
		parkLamp(23, D + 10);
		finish();
		villagerSpot(9, -1, D + 2);
		villagerSpot(21, -1, D + 2);
		villagerSpot(11, -1, D + 15);
		villagerSpot(5, 0, 7);
		villagerSpot(25, 0, 7);
		golemSpot(19, -1, D + 15);
	}

	private void stall(int x, int z, Block canopy, Block job) {
		for (int dx = 0; dx <= 4; dx += 4) {
			for (int dz = 0; dz <= 3; dz += 3) {
				for (int y = 0; y <= 2; y++) {
					set(x + dx, y, z + dz, Blocks.SPRUCE_FENCE);
				}
			}
		}
		for (int dx = -1; dx <= 5; dx++) {
			for (int dz = -1; dz <= 4; dz++) {
				set(x + dx, 3, z + dz, dx % 2 == 0 ? canopy : Blocks.WHITE_WOOL);
			}
			set(x + dx, 4, z + 1, slab(Blocks.WHITE_WOOL == canopy ? Blocks.SMOOTH_STONE_SLAB : Blocks.SPRUCE_SLAB, false));
			set(x + dx, 4, z + 2, slab(Blocks.SPRUCE_SLAB, false));
		}
		set(x + 1, 0, z + 3, Blocks.BARREL);
		set(x + 2, 0, z + 3, job);
		set(x + 3, 0, z + 3, Blocks.SPRUCE_PLANKS);
		set(x + 3, 1, z + 3, Blocks.MELON);
		set(x + 1, 1, z + 3, Blocks.PUMPKIN);
		set(x + 2, 2, z + 1, hanging(Blocks.LANTERN));
	}

	/** 09. Park: hedged 33 x 33 square, fountain with four cascades, hero statue, trees, beds, benches. */
	public void park() {
		final int N = 33;
		final int c = 16;
		for (int x = 0; x < N; x++) {
			for (int z = 0; z < N; z++) {
				double r = Math.sqrt((x - c) * (x - c) + (z - c) * (z - c));
				boolean edge = x == 0 || z == 0 || x == N - 1 || z == N - 1;
				boolean gate = Math.abs(x - c) <= 1 || Math.abs(z - c) <= 1;
				if (edge && !gate) {
					set(x, 0, z, leaves(Blocks.AZALEA_LEAVES));
					set(x, -1, z, Blocks.GRASS_BLOCK);
				} else if (Math.abs(x - c) <= 1 || Math.abs(z - c) <= 1 || (r >= 7.5 && r <= 9.5)) {
					set(x, -1, z, rnd.nextFloat() < 0.15f ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH);
				} else if (r < 7.5 && r > 6.3) {
					set(x, -1, z, Blocks.STONE_BRICKS);
				}
			}
		}
		// fountain: basin radius 5, two deep, centre column with a source on top
		for (int x = c - 6; x <= c + 6; x++) {
			for (int z = c - 6; z <= c + 6; z++) {
				double r = Math.sqrt((x - c) * (x - c) + (z - c) * (z - c));
				if (r <= 4.5) {
					set(x, -3, z, Blocks.STONE_BRICKS);
					set(x, -2, z, Blocks.WATER);
					set(x, -1, z, Blocks.WATER);
				} else if (r <= 5.5) {
					set(x, -3, z, Blocks.STONE_BRICKS);
					set(x, -2, z, Blocks.STONE_BRICKS);
					set(x, -1, z, Blocks.STONE_BRICKS);
					set(x, 0, z, slab(Blocks.STONE_BRICK_SLAB, false));
				} else if (r <= 6.3) {
					set(x, -1, z, Blocks.POLISHED_ANDESITE);
				}
			}
		}
		set(c - 2, -3, c, Blocks.SEA_LANTERN);
		set(c + 2, -3, c, Blocks.SEA_LANTERN);
		set(c, -3, c - 2, Blocks.SEA_LANTERN);
		set(c, -3, c + 2, Blocks.SEA_LANTERN);
		// centre column with four spouts: each source falls as a thin stream into the basin
		for (int y = -2; y <= 3; y++) {
			set(c, y, c, y == 3 ? Blocks.CHISELED_STONE_BRICKS : Blocks.STONE_BRICKS);
		}
		for (Direction d : Direction.Plane.HORIZONTAL) {
			set(c + d.getStepX(), 4, c + d.getStepZ(), stair(Blocks.STONE_BRICK_STAIRS, d.getOpposite(), false));
			set(c + d.getStepX(), 3, c + d.getStepZ(), Blocks.WATER);
		}
		set(c, 4, c, Blocks.STONE_BRICK_WALL);
		set(c, 5, c, Blocks.LANTERN);
		// hero statue on a plinth (NE quadrant)
		int sx = c + 9;
		int sz = c - 9;
		fill(sx - 2, 0, sz - 2, sx + 2, 0, sz + 2, Blocks.STONE_BRICKS);
		fill(sx - 1, 1, sz - 1, sx + 1, 1, sz + 1, Blocks.POLISHED_ANDESITE);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			for (int k = -2; k <= 2; k++) {
				int px = sx + d.getStepX() * 3 + (d.getAxis() == Direction.Axis.Z ? k : 0);
				int pz = sz + d.getStepZ() * 3 + (d.getAxis() == Direction.Axis.X ? k : 0);
				set(px, 0, pz, stair(Blocks.STONE_BRICK_STAIRS, d.getOpposite(), false));
			}
		}
		// a patina-copper hero, fist raised, cape behind (faces the fountain path, south)
		Block cu = Blocks.WAXED_OXIDIZED_COPPER;
		Block cc = Blocks.WAXED_OXIDIZED_CUT_COPPER;
		for (int y = 2; y <= 4; y++) {
			set(sx - 1, y, sz, cu);
			set(sx + 1, y, sz, cu);
		}
		fill(sx - 1, 5, sz, sx + 1, 5, sz, st(cc));
		fill(sx - 1, 6, sz, sx + 1, 7, sz, st(cu));
		fill(sx - 2, 8, sz, sx + 2, 8, sz, st(cu));
		set(sx - 2, 7, sz, cu);
		set(sx - 2, 6, sz, cu);
		for (int y = 9; y <= 11; y++) {
			set(sx + 2, y, sz, cu);
		}
		set(sx, 9, sz, cu);
		set(sx, 10, sz, slab(Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB, false));
		fill(sx - 1, 3, sz - 1, sx + 1, 8, sz - 1, st(cc));
		set(sx - 2, 3, sz - 1, cc);
		set(sx + 2, 3, sz - 1, cc);
		set(sx - 2, 2, sz - 1, stair(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Direction.EAST, false));
		set(sx + 2, 2, sz - 1, stair(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, Direction.WEST, false));
		// copper-roofed bell pavilion (SW): the park's meeting point
		final int px = 8;
		final int pz = 24;
		fill(px - 2, 0, pz - 2, px + 2, 0, pz + 2, slab(Blocks.STONE_BRICK_SLAB, false));
		for (int[] q : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
			for (int y = 1; y <= 3; y++) {
				set(px + q[0], y, pz + q[1], Blocks.SPRUCE_FENCE);
			}
		}
		for (int i = 0; i < 3; i++) {
			int a = 3 - i;
			int y = 4 + i;
			for (int dx = -a; dx <= a; dx++) {
				for (int dz = -a; dz <= a; dz++) {
					boolean edge = Math.abs(dx) == a || Math.abs(dz) == a;
					if (!edge) {
						set(px + dx, y, pz + dz, cc);
					} else if (Math.abs(dz) == a) {
						set(px + dx, y, pz + dz, stair(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, dz < 0 ? Direction.SOUTH : Direction.NORTH, false));
					} else {
						set(px + dx, y, pz + dz, stair(Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS, dx < 0 ? Direction.EAST : Direction.WEST, false));
					}
				}
			}
		}
		set(px, 7, pz, Blocks.LIGHTNING_ROD);
		bell(px, 3, pz);
		// trees
		int[][] trees = {{5, 7}, {24, 27}, {3, 13}, {29, 21}, {11, 3}, {20, 29}};
		for (int i = 0; i < trees.length; i++) {
			int[] t = trees[i];
			if (i <= 1) {
				tree(t[0], t[1], Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, 4, 3.2);
			} else if (i >= 4) {
				tree(t[0], t[1], Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, 5, 2.6);
			} else {
				tree(t[0], t[1], Blocks.OAK_LOG, Blocks.OAK_LEAVES, 4, 3.4);
			}
		}
		// flower beds
		Block[] fl = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY};
		for (int x = 2; x < N - 2; x++) {
			for (int z = 2; z < N - 2; z++) {
				double r = Math.sqrt((x - c) * (x - c) + (z - c) * (z - c));
				boolean path = Math.abs(x - c) <= 1 || Math.abs(z - c) <= 1 || (r >= 6.3 && r <= 9.5);
				boolean keepOut = (Math.abs(x - 8) <= 3 && Math.abs(z - 24) <= 3) || (Math.abs(x - 25) <= 3 && Math.abs(z - 7) <= 3);
				boolean bed = r > 10.5 && r < 12.5 && !path && !keepOut;
				if (bed && rnd.nextFloat() < 0.75f && level.getBlockState(p(x, 0, z)).isAir()) {
					set(x, 0, z, fl[(x * 7 + z * 3) % fl.length]);
				} else if (!path && !keepOut && r >= 13 && rnd.nextFloat() < 0.06f && level.getBlockState(p(x, 0, z)).isAir()) {
					set(x, 0, z, Blocks.SHORT_GRASS);
				}
			}
		}
		// benches facing the fountain, lamps on the ring path
		bench(c - 1, c - 10, Direction.SOUTH, 3, true);
		bench(c - 1, c + 10, Direction.NORTH, 3, true);
		bench(c - 10, c - 1, Direction.EAST, 3, false);
		bench(c + 10, c - 1, Direction.WEST, 3, false);
		for (int[] l : new int[][] {{c - 7, c - 7}, {c + 7, c + 7}, {c - 7, c + 7}, {c + 7, c - 7}}) {
			parkLamp(l[0], l[1]);
		}
		finish();
		villagerSpot(c, -1, c + 8);
		villagerSpot(c + 8, -1, c);
		golemSpot(12, -1, 24);
	}

	/** 10. Train station: sandstone facade with a great arched window, STATION frieze, clock, copper towers, glass shed. */
	public void trainStation() {
		final int W = 31;
		final int D = 12;
		final int top = 22;
		pave(-4, D, W + 3, D + 6, Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE);
		fill(1, 0, 1, W - 2, 0, D - 2, Blocks.POLISHED_DIORITE);
		for (Side s : SIDES) {
			int n = len(s, W, D);
			for (int u = 0; u < n; u++) {
				for (int y = 0; y <= top; y++) {
					BlockState b = st(Blocks.SMOOTH_SANDSTONE);
					if (y == 0 || y == 16 || y == top) {
						b = st(Blocks.CUT_SANDSTONE);
					}
					if (s == Side.S) {
						double dx = u - 15;
						double dy = y - 8;
						double r = Math.sqrt(dx * dx + dy * dy);
						boolean inArch = (y >= 1 && y <= 8 && Math.abs(dx) <= 6.5) || (y > 8 && r <= 6.5);
						if (inArch) {
							boolean mull = Math.floorMod(u - 15, 3) == 0 || Math.floorMod(y - 1, 4) == 3;
							b = mull ? st(Blocks.POLISHED_DEEPSLATE) : st(Blocks.GLASS_PANE);
						} else if (y > 8 && r <= 7.6 && y < 16) {
							b = st(Blocks.CHISELED_SANDSTONE);
						} else if ((u == 5 || u == 6 || u == 24 || u == 25) && y >= 2 && y <= 11) {
							b = y == 11 ? st(Blocks.CHISELED_SANDSTONE) : st(Blocks.GLASS_PANE);
						}
					} else if (s != Side.N && u % 4 == 2 && y >= 2 && y <= 12) {
						b = st(Blocks.GLASS_PANE);
					}
					face(s, 0, 0, W, D, u, y, 0, b);
				}
			}
		}
		// projecting archivolt
		for (int u = 6; u <= 24; u++) {
			for (int y = 9; y <= 16; y++) {
				double r = Math.sqrt((u - 15) * (u - 15) + (y - 8) * (y - 8));
				if (r > 6.5 && r <= 7.6) {
					set(u, y, D, Blocks.CUT_SANDSTONE);
				}
			}
		}
		for (int y = 1; y <= 8; y++) {
			set(8, y, D, Blocks.CUT_SANDSTONE);
			set(22, y, D, Blocks.CUT_SANDSTONE);
		}
		set(15, 15, D, Blocks.CHISELED_SANDSTONE);
		door(14, 1, D - 1, Blocks.SPRUCE_DOOR, Direction.NORTH, false);
		door(16, 1, D - 1, Blocks.SPRUCE_DOOR, Direction.NORTH, true);
		// frieze and cornice
		text(Side.S, 0, 0, W, D, "STATION", 2, 21, 0, st(Blocks.POLISHED_DEEPSLATE));
		cornice(0, 0, W, D, top, Blocks.SMOOTH_SANDSTONE_STAIRS, true);
		cornice(0, 0, W, D, 16, Blocks.SMOOTH_SANDSTONE_STAIRS, true);
		fill(1, top, 1, W - 2, top, D - 2, Blocks.SMOOTH_SANDSTONE);
		set(4, 1, 3, Blocks.CARTOGRAPHY_TABLE);
		set(26, 1, 3, Blocks.CARTOGRAPHY_TABLE);
		for (int x = 4; x <= 26; x += 6) {
			set(x, 15, 6, hanging(Blocks.LANTERN));
			set(x, 16, 6, Blocks.CHAIN);
		}
		// clock pediment
		for (int i = 0; i <= 5; i++) {
			for (int u = 9 + i; u <= 21 - i; u++) {
				set(u, top + 1 + i, D - 1, u == 9 + i ? stair(Blocks.SMOOTH_SANDSTONE_STAIRS, Direction.EAST, false)
						: u == 21 - i ? stair(Blocks.SMOOTH_SANDSTONE_STAIRS, Direction.WEST, false) : st(Blocks.SMOOTH_SANDSTONE));
			}
		}
		for (int u = 12; u <= 18; u++) {
			for (int y = top + 1; y <= top + 5; y++) {
				double r = Math.sqrt((u - 15) * (u - 15) + (y - (top + 3)) * (y - (top + 3)));
				if (r <= 2.3) {
					BlockState b = st(Blocks.WHITE_CONCRETE);
					if ((u == 15 && y >= top + 3 && y <= top + 4) || (y == top + 3 && u == 16)) {
						b = st(Blocks.BLACK_CONCRETE);
					}
					set(u, y, D, b);
				} else if (r <= 2.9) {
					set(u, y, D, Blocks.GOLD_BLOCK);
				}
			}
		}
		// corner towers with copper caps
		for (int t = 0; t < 2; t++) {
			int tx = t == 0 ? -5 : W;
			int tz = D - 4;
			for (Side s : SIDES) {
				for (int u = 0; u < 5; u++) {
					for (int y = 0; y <= 27; y++) {
						BlockState b = (u == 0 || u == 4 || y == 16 || y == 27) ? st(Blocks.CUT_SANDSTONE) : st(Blocks.SMOOTH_SANDSTONE);
						if (u == 2 && y > 3 && y < 26 && y % 6 < 3) {
							b = st(Blocks.GLASS_PANE);
						}
						face(s, tx, tz, 5, 5, u, y, 0, b);
					}
				}
			}
			cornice(tx, tz, 5, 5, 27, Blocks.SMOOTH_SANDSTONE_STAIRS, true);
			for (int i = 0; i < 4; i++) {
				int a = -1 + i;
				int nn = 7 - 2 * i;
				for (Side s : SIDES) {
					for (int u = 0; u < nn; u++) {
						face(s, tx + a, tz + a, nn, nn, u, 28 + i, 0, stair(Blocks.WEATHERED_CUT_COPPER_STAIRS, s.in, false));
					}
				}
				if (nn > 2) {
					fill(tx + a + 1, 28 + i, tz + a + 1, tx + a + nn - 2, 28 + i, tz + a + nn - 2, Blocks.WEATHERED_CUT_COPPER);
				}
			}
			set(tx + 2, 31, tz + 2, Blocks.WEATHERED_CUT_COPPER);
			set(tx + 2, 32, tz + 2, Blocks.LIGHTNING_ROD);
		}
		// platforms, rails and a glass train shed behind (north)
		for (int x = -8; x <= W + 7; x++) {
			for (int z = -26; z <= -1; z++) {
				set(x, -1, z, Blocks.GRAVEL);
			}
			for (int z : new int[] {-6, -20}) {
				set(x, -1, z, Blocks.STRIPPED_SPRUCE_LOG);
				set(x, 0, z, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
			}
			for (int z = -15; z <= -11; z++) {
				set(x, -1, z, Blocks.STONE_BRICKS);
				set(x, 0, z, z == -15 || z == -11 ? st(Blocks.YELLOW_CONCRETE) : st(Blocks.SMOOTH_STONE));
			}
			for (int z = -26; z <= -1; z++) {
				double dz = z + 13.5;
				for (int y = 1; y <= 16; y++) {
					double r = Math.sqrt(dz * dz + (y - 2) * (y - 2) * 1.0);
					if (r >= 13.0 && r < 14.0 && y >= 2) {
						set(x, y, z, Math.floorMod(x, 6) == 0 ? st(Blocks.WEATHERED_CUT_COPPER) : st(Blocks.GLASS));
					}
				}
			}
			if (Math.floorMod(x, 6) == 0) {
				for (int y = 1; y <= 4; y++) {
					set(x, y, -26, Blocks.WEATHERED_CUT_COPPER);
					set(x, y, -1, Blocks.WEATHERED_CUT_COPPER);
				}
				set(x, 1, -13, Blocks.SMOOTH_STONE);
				set(x, 2, -13, Blocks.POLISHED_DEEPSLATE_WALL);
				set(x, 3, -13, Blocks.LANTERN);
			}
		}
		// commuter train on the far track
		for (int x = 2; x <= 25; x++) {
			for (int z = -21; z <= -19; z++) {
				boolean gap = x == 13 || x == 14;
				if (gap) {
					if (z == -20) {
						set(x, 2, z, Blocks.IRON_BARS);
					}
					continue;
				}
				set(x, 1, z, Blocks.LIGHT_GRAY_CONCRETE);
				set(x, 2, z, Blocks.RED_CONCRETE);
				set(x, 3, z, (z != -20 && x % 3 != 0) ? st(Blocks.BLACK_STAINED_GLASS) : st(Blocks.WHITE_CONCRETE));
				set(x, 4, z, Blocks.WHITE_CONCRETE);
				set(x, 5, z, slab(Blocks.SMOOTH_STONE_SLAB, false));
			}
		}
		// front plaza
		parkLamp(-3, D + 3);
		parkLamp(W + 2, D + 3);
		bench(3, D + 4, Direction.SOUTH, 3, true);
		bench(25, D + 4, Direction.SOUTH, 3, true);
		// a door through to the platforms
		door(15, 1, 0, Blocks.SPRUCE_DOOR, Direction.SOUTH, false);
		finish();
		villagerSpot(10, 0, 6);
		villagerSpot(20, 0, 6);
	}

	/** 11. Construction site: hoarding, half-built concrete frame with steel above, scaffold, tower crane, site office. */
	public void constructionSite() {
		final int N = 25;
		for (int x = 0; x < N; x++) {
			for (int z = 0; z < N; z++) {
				float f = rnd.nextFloat();
				set(x, -1, z, f < 0.4f ? Blocks.COARSE_DIRT : f < 0.6f ? Blocks.GRAVEL : f < 0.75f ? Blocks.PACKED_MUD : Blocks.DIRT);
			}
		}
		// hoarding with a gate gap
		for (int x = 0; x < N; x++) {
			for (int z = 0; z < N; z++) {
				boolean edge = x == 0 || z == 0 || x == N - 1 || z == N - 1;
				if (!edge || (z == N - 1 && x >= 9 && x <= 15)) {
					continue;
				}
				set(x, 0, z, Blocks.SPRUCE_PLANKS);
				set(x, 1, z, (x + z) % 8 < 4 ? Blocks.SPRUCE_PLANKS : Blocks.STRIPPED_SPRUCE_WOOD);
				set(x, 2, z, Blocks.SPRUCE_FENCE);
			}
		}
		for (int x = 1; x < N - 1; x += 6) {
			set(x, 1, N - 1, (x / 6) % 2 == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE);
		}
		// frame x5..17, z4..14 ; completed floors 1-2, partial 3, steel skeleton 4-6
		for (int fl = 0; fl <= 6; fl++) {
			int fy = fl * 4;
			for (int x = 5; x <= 17; x++) {
				for (int z = 4; z <= 14; z++) {
					boolean col = (x - 5) % 4 == 0 && (z - 4) % 5 == 0;
					if (fl < 6) {
						for (int y = fy; y < fy + 4; y++) {
							if (col) {
								set(x, y, z, fl < 3 ? st(Blocks.LIGHT_GRAY_CONCRETE) : st(Blocks.POLISHED_DEEPSLATE_WALL));
							}
						}
					}
					boolean edgeBeam = x == 5 || x == 17 || z == 4 || z == 14;
					if (fl > 0 && fl <= 3) {
						if (fl < 3 || x + z * 2 < 40) {
							set(x, fy, z, Blocks.SMOOTH_STONE);
						}
					} else if (fl > 3 && edgeBeam) {
						set(x, fy, z, Blocks.POLISHED_DEEPSLATE_WALL);
					}
				}
			}
		}
		for (int x = 5; x <= 17; x++) {
			for (int y = 1; y <= 7; y++) {
				if (y % 4 == 0) {
					continue;
				}
				boolean glazed = x < 12 || y < 4;
				set(x, y, 4, (x - 5) % 4 == 0 ? st(Blocks.LIGHT_GRAY_CONCRETE) : glazed ? st(Blocks.GLASS_PANE) : st(Blocks.BRICKS));
				if (y < 4) {
					set(5, y, x - 1 > 14 ? 14 : x - 1, Blocks.BRICKS);
				}
			}
		}
		fill(6, 0, 5, 16, 0, 13, Blocks.SMOOTH_STONE);
		// scaffold on the south face
		for (int x = 4; x <= 18; x++) {
			for (int lv = 1; lv <= 3; lv++) {
				int y = lv * 4 - 1;
				set(x, y, 15, slab(Blocks.SPRUCE_SLAB, true));
				set(x, y, 16, slab(Blocks.SPRUCE_SLAB, true));
				set(x, y + 1, 17, Blocks.SPRUCE_FENCE);
			}
			if ((x - 4) % 4 == 0) {
				for (int y = 0; y <= 12; y++) {
					set(x, y, 17, Blocks.SPRUCE_FENCE);
				}
			}
		}
		for (int y = 0; y <= 11; y++) {
			set(19, y, 16, facing(Blocks.LADDER, Direction.EAST).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
		}
		fill(18, 0, 16, 18, 11, 16, Blocks.SPRUCE_PLANKS);
		// tower crane, mast at x20..22 z2..4
		final int mx = 20;
		final int mz = 2;
		final int mh = 40;
		for (int y = 0; y <= mh; y++) {
			for (int dx = 0; dx <= 2; dx++) {
				for (int dz = 0; dz <= 2; dz++) {
					boolean corner = (dx != 1) && (dz != 1);
					boolean brace = y % 4 == 0 && (dx != 1 || dz != 1);
					if (corner || brace) {
						set(mx + dx, y, mz + dz, Blocks.YELLOW_CONCRETE);
					}
				}
			}
		}
		fill(mx, mh - 3, mz, mx + 2, mh - 1, mz + 2, Blocks.WHITE_CONCRETE);
		set(mx + 1, mh - 2, mz + 3 - 1 + 1, Blocks.LIGHT_BLUE_STAINED_GLASS);
		for (int dx = 0; dx <= 2; dx++) {
			set(mx + dx, mh - 2, mz + 2, Blocks.LIGHT_BLUE_STAINED_GLASS);
			set(mx + dx, mh - 2, mz, Blocks.LIGHT_BLUE_STAINED_GLASS);
		}
		// jib along -x (over the site), counter-jib +x
		for (int x = mx - 22; x <= mx + 10; x++) {
			set(x, mh + 1, mz, Blocks.YELLOW_CONCRETE);
			set(x, mh + 1, mz + 2, Blocks.YELLOW_CONCRETE);
			if (Math.floorMod(x, 3) == 0) {
				set(x, mh + 1, mz + 1, Blocks.YELLOW_CONCRETE);
				set(x, mh + 2, mz + 1, Blocks.YELLOW_CONCRETE);
			} else {
				set(x, mh + 2, mz + 1, Blocks.IRON_BARS);
			}
		}
		fill(mx + 6, mh - 1, mz, mx + 10, mh, mz + 2, Blocks.GRAY_CONCRETE);
		for (int y = mh + 1; y <= mh + 6; y++) {
			set(mx + 1, y, mz + 1, Blocks.YELLOW_CONCRETE);
		}
		set(mx + 1, mh + 7, mz + 1, Blocks.RED_STAINED_GLASS);
		// hook and a pallet of bricks
		int hx = mx - 14;
		for (int y = mh; y >= 18; y--) {
			set(hx, y, mz + 1, Blocks.CHAIN);
		}
		set(hx, 17, mz + 1, Blocks.IRON_BLOCK);
		fill(hx - 1, 16, mz, hx + 1, 16, mz + 2, slab(Blocks.SPRUCE_SLAB, false));
		fill(hx - 1, 16, mz + 1, hx + 1, 16, mz + 1, Blocks.BRICKS);
		// materials, site office, portable toilet
		fill(1, 0, 18, 3, 0, 20, Blocks.SAND);
		set(2, 1, 19, Blocks.SAND);
		fill(1, 0, 14, 2, 0, 15, Blocks.GRAVEL);
		fill(20, 0, 8, 22, 0, 9, slab(Blocks.SPRUCE_SLAB, false));
		fill(20, 1, 8, 22, 1, 9, Blocks.BRICKS);
		fill(20, 0, 12, 22, 1, 12, Blocks.STONE_BRICKS);
		set(21, 0, 15, Blocks.BARREL);
		set(22, 0, 15, Blocks.BARREL);
		set(21, 1, 15, Blocks.BARREL);
		for (int x = 1; x <= 7; x++) {
			for (int z = 21; z <= 23; z++) {
				for (int y = 0; y <= 2; y++) {
					boolean wall = x == 1 || x == 7 || z == 21 || z == 23 || y == 2;
					if (wall) {
						set(x, y, z, y == 1 && (x == 3 || x == 5) && z == 21 ? st(Blocks.GLASS_PANE) : st(Blocks.WHITE_CONCRETE));
					}
				}
				set(x, 3, z, slab(Blocks.SMOOTH_STONE_SLAB, false));
			}
		}
		set(23, 0, 22, Blocks.BLUE_CONCRETE);
		set(23, 1, 22, Blocks.BLUE_CONCRETE);
		set(23, 2, 22, slab(Blocks.SMOOTH_STONE_SLAB, false));
		set(3, 0, 22, Blocks.STONECUTTER);
		set(5, 0, 22, Blocks.SMITHING_TABLE);
		// the site office needs a way in (its job sites are claimed by walking to them)
		door(7, 0, 22, Blocks.SPRUCE_DOOR, Direction.WEST, false);
		// traffic cones
		for (int x = 9; x <= 15; x += 2) {
			set(x, 0, N, Blocks.ORANGE_CONCRETE);
			set(x, 1, N, Blocks.END_ROD);
		}
		set(22, 0, 15, Blocks.SPRUCE_PLANKS);
		set(21, 1, 15, Blocks.SPRUCE_SLAB);
		finish();
		villagerSpot(12, -1, 20);
	}

	/** 12. The Daily Bulletin: art-deco tuff newspaper tower, ziggurat setbacks, red rooftop sign. */
	public void dailyBulletin() {
		final int W = 21;
		final int D = 15;
		pave(-6, -1, W + 4, D + 1, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE);
		decoBlock(0, 0, W, D, 0, 20, true);
		decoBlock(2, 2, W - 4, D - 4, 21, 24, false);
		decoBlock(4, 4, W - 8, D - 8, 25, 28, false);
		fill(5, 28, 5, W - 6, 28, D - 6, Blocks.POLISHED_TUFF);
		for (int fy = 0; fy <= 20; fy += 4) {
			fill(1, fy, 1, W - 2, fy, D - 2, fy == 0 ? Blocks.POLISHED_DEEPSLATE : Blocks.SPRUCE_PLANKS);
			if (fy > 0) {
				set(5, fy, 7, Blocks.GLOWSTONE);
				set(15, fy, 7, Blocks.GLOWSTONE);
			}
		}
		fill(3, 21, 3, W - 4, 21, D - 4, Blocks.POLISHED_TUFF);
		fill(5, 25, 5, W - 6, 25, D - 6, Blocks.POLISHED_TUFF);
		// grand entrance portal
		for (int x = 8; x <= 12; x++) {
			for (int y = 1; y <= 7; y++) {
				boolean frame = x == 8 || x == 12 || y == 7;
				set(x, y, D - 1, frame ? st(Blocks.WAXED_CUT_COPPER) : st(Blocks.GLASS_PANE));
				if (frame) {
					set(x, y, D, Blocks.WAXED_CUT_COPPER);
				}
			}
		}
		door(9, 1, D - 1, Blocks.DARK_OAK_DOOR, Direction.NORTH, false);
		door(11, 1, D - 1, Blocks.DARK_OAK_DOOR, Direction.NORTH, true);
		set(10, 8, D, Blocks.WAXED_CHISELED_COPPER);
		set(10, 6, D, hanging(Blocks.LANTERN));
		for (int x = 3; x <= 17; x += 7) {
			set(x, 1, 3, Blocks.LECTERN);
			set(x, 5, 3, Blocks.LECTERN);
		}
		fill(2, 1, 1, 18, 3, 1, Blocks.BOOKSHELF);
		// rooftop sign: DAILY over BULLETIN, red letters on an iron-bar lattice
		final int zs = 7;
		for (int x = -5; x <= 25; x++) {
			if (Math.floorMod(x + 5, 6) == 0) {
				for (int y = 29; y <= 40; y++) {
					if (x >= 5 && x <= 15 || y >= 29) {
						set(x, y, zs + 1, Blocks.IRON_BARS);
					}
				}
			}
			set(x, 34, zs + 1, Blocks.IRON_BARS);
			set(x, 28, zs + 1, x < 4 || x > 16 ? Blocks.IRON_BARS : Blocks.POLISHED_TUFF);
		}
		for (int x = -5; x <= 25; x++) {
			if (x < 4 || x > 16) {
				// outrigger supports down to the setback roofs
				if (Math.floorMod(x + 5, 6) == 0) {
					for (int y = x < 0 || x > 20 ? 29 : 21; y <= 28; y++) {
						set(x, y, zs + 1, Blocks.IRON_BARS);
					}
				}
			}
		}
		text(Side.S, -5, 0, 31, zs + 1, "BULLETIN", -1, 33, 0, st(Blocks.RED_CONCRETE));
		text(Side.S, -5, 0, 31, zs + 1, "DAILY", 6, 40, 0, st(Blocks.RED_CONCRETE));
		// stairs to the first-floor newsroom (its lecterns are job sites too)
		stairRun(13, 12, 0, 4, Direction.EAST, Blocks.DARK_OAK_STAIRS);
		finish();
		villagerSpot(6, 0, 9);
		villagerSpot(14, 0, 8);
	}

	private void decoBlock(int x0, int z0, int w, int d, int y1, int y2, boolean base) {
		for (Side s : SIDES) {
			int n = len(s, w, d);
			for (int u = 0; u < n; u++) {
				boolean pier = u % 4 == 0 || u == n - 1;
				for (int y = y1; y <= y2; y++) {
					int r = (y - y1) % 4;
					BlockState b;
					if (base && y <= 0) {
						b = st(Blocks.POLISHED_DEEPSLATE);
					} else if (y == y2) {
						b = st(Blocks.CHISELED_TUFF);
					} else if (pier) {
						b = st(Blocks.TUFF_BRICKS);
					} else if (r == 0) {
						b = st(Blocks.CHISELED_TUFF_BRICKS);
					} else {
						b = st(Blocks.GRAY_STAINED_GLASS_PANE);
					}
					face(s, x0, z0, w, d, u, y, 0, b);
					if (pier && y < y2) {
						face(s, x0, z0, w, d, u, y, 1, st(Blocks.TUFF_BRICKS));
					}
				}
				face(s, x0, z0, w, d, u, y2, 1, (u % 4 == 0 || u == n - 1) ? st(Blocks.CHISELED_TUFF) : stair(Blocks.TUFF_BRICK_STAIRS, s.in, true));
			}
		}
		ring(x0, z0, w, d, y2 + 1, 0, st(Blocks.TUFF_BRICK_WALL));
	}

	/** 13. Brownstone walk-up with the rooftop set piece: timber water tower and a HERO COLA billboard. */
	public void rooftopSetPiece() {
		final int W = 23;
		final int D = 13;
		final int top = 16;
		pave(-2, -2, W + 1, D + 3, Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE);
		for (int fy = 0; fy <= top; fy += 4) {
			fill(1, fy, 1, W - 2, fy, D - 2, fy == top ? Blocks.SMOOTH_STONE : Blocks.SPRUCE_PLANKS);
		}
		for (Side s : SIDES) {
			int n = len(s, W, D);
			for (int u = 0; u < n; u++) {
				boolean pier = u == 0 || u == n - 1 || u == 11;
				for (int y = 0; y <= top; y++) {
					int r = y % 4;
					BlockState b = st(Blocks.MUD_BRICKS);
					if (pier) {
						b = st(Blocks.PACKED_MUD);
					} else if (y == 0 || y == top) {
						b = st(Blocks.STONE_BRICKS);
					} else if ((r == 1 || r == 2) && u % 3 != 0) {
						b = st(Blocks.GLASS_PANE);
					}
					face(s, 0, 0, W, D, u, y, 0, b);
					if (!pier && u % 3 != 0 && r == 0 && y > 0 && y < top) {
						face(s, 0, 0, W, D, u, y, 1, stair(Blocks.MUD_BRICK_STAIRS, s.in, true));
					}
					if (!pier && u % 3 != 0 && r == 3) {
						face(s, 0, 0, W, D, u, y, 1, trap(Blocks.SPRUCE_TRAPDOOR, s.in.getOpposite(), true, false));
					}
				}
			}
		}
		// stoop
		door(5, 1, D - 1, Blocks.SPRUCE_DOOR, Direction.NORTH, false);
		door(17, 1, D - 1, Blocks.SPRUCE_DOOR, Direction.NORTH, false);
		for (int x : new int[] {5, 17}) {
			set(x, 0, D, stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
			set(x, 3, D, hanging(Blocks.LANTERN));
			set(x, 4, D, slab(Blocks.STONE_BRICK_SLAB, false));
		}
		cornice(0, 0, W, D, top, Blocks.DARK_OAK_STAIRS, true);
		ring(0, 0, W, D, top + 1, 0, st(Blocks.MUD_BRICK_WALL));
		// billboard on the back-left of the roof: x0..16 at z3, face y20..32
		final int bz = 3;
		for (int x = 0; x <= 16; x++) {
			for (int y = 20; y <= 32; y++) {
				boolean frame = x == 0 || x == 16 || y == 20 || y == 32;
				// front: a red poster in a white-and-gold frame; back: a timber billboard back (planks on a log truss),
				// so no side of it reads as a black slab from the skyline
				boolean gold = frame && (x + y) % 2 == 0;
				set(x, y, bz, frame ? (gold ? Blocks.YELLOW_CONCRETE : Blocks.WHITE_CONCRETE) : (y == 26 ? Blocks.WHITE_CONCRETE : Blocks.RED_CONCRETE));
				boolean truss = frame || x % 4 == 0 || y == 26;
				set(x, y, bz - 1, truss ? axis(Blocks.STRIPPED_DARK_OAK_LOG, x % 4 == 0 && !(y == 20 || y == 32 || y == 26) ? Direction.Axis.Y
						: Direction.Axis.X) : st(Blocks.SPRUCE_PLANKS));
			}
			set(x, 19, bz + 1, trap(Blocks.IRON_TRAPDOOR, Direction.NORTH, true, false));
			if (x % 3 == 0) {
				set(x, 33, bz + 1, Blocks.END_ROD.defaultBlockState());
			}
		}
		for (int x : new int[] {2, 8, 14}) {
			for (int y = top + 1; y <= 19; y++) {
				set(x, y, bz - 1, Blocks.IRON_BARS);
				set(x, y, bz, Blocks.IRON_BARS);
			}
		}
		text(Side.S, 0, 0, 17, bz + 1, "HERO", 1, 31, 0, st(Blocks.WHITE_CONCRETE));
		text(Side.S, 0, 0, 17, bz + 1, "COLA", 1, 25, 0, st(Blocks.YELLOW_CONCRETE));
		// timber water tower on the front-right of the roof, centre (18.5, 7.5)
		double cx = 18.5;
		double cz = 7.5;
		for (int x = 15; x <= 22; x++) {
			for (int z = 4; z <= 11; z++) {
				double r = Math.sqrt((x + 0.5 - cx - 0.5) * (x + 0.5 - cx - 0.5) + (z + 0.5 - cz - 0.5) * (z + 0.5 - cz - 0.5));
				for (int y = 21; y <= 27; y++) {
					if (r <= 3.0 && r > 2.0) {
						set(x, y, z, y == 22 || y == 26 ? st(Blocks.DARK_OAK_PLANKS) : axis(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
					}
				}
				if (r <= 3.0) {
					set(x, 20, z, Blocks.SPRUCE_PLANKS);
					set(x, 28, z, r > 2.0 ? slab(Blocks.SPRUCE_SLAB, false) : st(Blocks.SPRUCE_PLANKS));
				}
				if (r <= 2.0) {
					set(x, 29, z, r > 1.0 ? slab(Blocks.SPRUCE_SLAB, false) : st(Blocks.SPRUCE_PLANKS));
				}
				if (r <= 1.0) {
					set(x, 30, z, slab(Blocks.SPRUCE_SLAB, false));
				}
			}
		}
		for (int[] leg : new int[][] {{16, 5}, {21, 5}, {16, 10}, {21, 10}}) {
			for (int y = top + 1; y <= 19; y++) {
				set(leg[0], y, leg[1], Blocks.DARK_OAK_FENCE);
			}
		}
		for (int x = 16; x <= 21; x++) {
			set(x, 18, 5, Blocks.DARK_OAK_FENCE);
			set(x, 18, 10, Blocks.DARK_OAK_FENCE);
		}
		for (int y = top + 1; y <= 27; y++) {
			set(22, y, 7, facing(Blocks.LADDER, Direction.EAST));
		}
		fill(21, top + 1, 7, 21, 27, 7, Blocks.SPRUCE_PLANKS);
		// chimneys and a skylight
		fill(1, top + 1, 9, 2, top + 3, 10, Blocks.BRICKS);
		set(1, top + 4, 9, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false));
		for (int x = 3; x <= 9; x++) {
			for (int z = 8; z <= 11; z++) {
				boolean water = x == 6 && z == 9;
				set(x, top, z, water ? st(Blocks.WATER) : Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
				if (!water) {
					set(x, top + 1, z, (x + z) % 3 == 0 ? Blocks.CARROTS.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7)
							: Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7));
				}
			}
		}
		set(10, top + 1, 8, Blocks.COMPOSTER);
		// the garden's water source sits on a block, not over the top-floor room
		set(6, top - 1, 9, Blocks.SMOOTH_STONE);
		for (int fy = 0; fy < top; fy += 4) {
			bed(2, fy + 1, 2, Blocks.WHITE_BED, Direction.NORTH);
			bed(20, fy + 1, 2, Blocks.CYAN_BED, Direction.NORTH);
		}
		// switchback stairs from the hall to the roof garden
		for (int fy = 0; fy < top; fy += 4) {
			if ((fy / 4) % 2 == 0) {
				stairRun(9, 9, fy, fy + 4, Direction.EAST, Blocks.SPRUCE_STAIRS);
			} else {
				stairRun(13, 10, fy, fy + 4, Direction.WEST, Blocks.SPRUCE_STAIRS);
			}
		}
		finish();
		villagerSpot(6, 0, 6);
		villagerSpot(16, 4, 6);
		villagerSpot(6, 8, 6);
	}

	/** 14. Warehouse and docks: timber warehouse, stone quay, pier, water basin, stacked shipping containers. */
	public void warehouseDocks() {
		final int W = 27;
		final int D = 15;
		// warehouse
		fill(1, 0, 1, W - 2, 0, D - 2, Blocks.SMOOTH_STONE);
		for (Side s : SIDES) {
			int n = len(s, W, D);
			for (int u = 0; u < n; u++) {
				boolean post = u % 4 == 0 || u == n - 1;
				for (int y = 0; y <= 9; y++) {
					BlockState b;
					if (y <= 1) {
						b = st(Blocks.STONE_BRICKS);
					} else if (post) {
						b = axis(Blocks.DARK_OAK_LOG, Direction.Axis.Y);
					} else if (y == 9 || y == 5) {
						b = axis(Blocks.DARK_OAK_LOG, s == Side.S || s == Side.N ? Direction.Axis.X : Direction.Axis.Z);
					} else if (y >= 6 && y <= 7 && u % 4 == 2) {
						b = st(Blocks.GLASS_PANE);
					} else {
						b = st(Blocks.SPRUCE_PLANKS);
					}
					face(s, 0, 0, W, D, u, y, 0, b);
				}
			}
		}
		// loading doors on the front: open bays 1 and 3, shuttered bays 2 and 4
		for (int bay = 0; bay < 4; bay++) {
			int x0 = 2 + bay * 6;
			for (int x = x0; x <= x0 + 3; x++) {
				for (int y = 1; y <= 4; y++) {
					set(x, y, D - 1, bay % 2 == 0 ? Blocks.AIR.defaultBlockState() : trap(Blocks.SPRUCE_TRAPDOOR, Direction.NORTH, false, true));
				}
				set(x, 0, D - 1, Blocks.SMOOTH_STONE);
			}
			for (int x = x0 - 1; x <= x0 + 4; x++) {
				set(x, 5, D, slab(Blocks.DEEPSLATE_TILE_SLAB, false));
			}
			set(x0 + 1, 4, D, hanging(Blocks.LANTERN));
		}
		// gable roof along x
		for (int i = 0; i <= 8; i++) {
			int zl = -1 + i;
			int zr = D - i;
			int y = 10 + i;
			for (int x = -1; x <= W; x++) {
				if (zl < zr) {
					set(x, y, zl, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.SOUTH, false));
					set(x, y, zr, stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.NORTH, false));
				} else if (zl == zr) {
					set(x, y, zl, slab(Blocks.DEEPSLATE_TILE_SLAB, false));
				}
			}
			for (int z = zl + 1; z <= zr - 1; z++) {
				set(0, y, z, Blocks.SPRUCE_PLANKS);
				set(W - 1, y, z, Blocks.SPRUCE_PLANKS);
			}
		}
		set(0, 14, 7, Blocks.GLASS_PANE);
		set(W - 1, 14, 7, Blocks.GLASS_PANE);
		// interior stacks
		for (int x = 2; x <= 24; x += 3) {
			// three barrels (fishermen), the rest crates: every barrel is a job site
			set(x, 1, 2, x % 9 == 2 ? st(Blocks.BARREL) : axis(Blocks.STRIPPED_SPRUCE_WOOD, Direction.Axis.Y));
			set(x, 2, 2, axis(Blocks.STRIPPED_SPRUCE_WOOD, Direction.Axis.Y));
			set(x + 1, 1, 2, Blocks.HAY_BLOCK);
		}
		set(13, 1, 5, Blocks.SMOKER);
		set(14, 1, 5, Blocks.SMOKER);
		for (int x = 4; x <= 22; x += 6) {
			set(x, 9, 7, hanging(Blocks.LANTERN));
		}
		// quay z15..19
		for (int x = -3; x <= W + 2; x++) {
			for (int z = D; z <= D + 4; z++) {
				set(x, -1, z, z == D + 4 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS);
			}
			if (Math.floorMod(x, 7) == 0) {
				set(x, 0, D + 4, Blocks.POLISHED_DEEPSLATE_WALL);
			}
		}
		// basin z20..38
		for (int x = -3; x <= W + 2; x++) {
			for (int z = D + 5; z <= D + 23; z++) {
				set(x, -4, z, Blocks.STONE);
				set(x, -3, z, Blocks.WATER);
				set(x, -2, z, Blocks.WATER);
				set(x, -1, z, Blocks.WATER);
			}
		}
		// pier
		for (int x = 10; x <= 14; x++) {
			for (int z = D + 5; z <= D + 18; z++) {
				set(x, -1, z, x == 10 || x == 14 ? axis(Blocks.SPRUCE_LOG, Direction.Axis.Z) : st(Blocks.SPRUCE_PLANKS));
				if ((x == 10 || x == 14) && (z - D) % 4 == 0) {
					set(x, -2, z, Blocks.SPRUCE_LOG);
					set(x, -3, z, Blocks.SPRUCE_LOG);
					set(x, 0, z, Blocks.SPRUCE_FENCE);
				}
			}
		}
		set(10, 1, D + 17, Blocks.LANTERN);
		set(14, 1, D + 17, Blocks.LANTERN);
		// a small tug moored beside the pier
		for (int x = 16; x <= 25; x++) {
			for (int z = D + 9; z <= D + 12; z++) {
				boolean edge = z == D + 9 || z == D + 12 || x == 16 || x == 25;
				set(x, -1, z, edge ? Blocks.RED_CONCRETE : Blocks.SPRUCE_PLANKS);
				if (edge) {
					set(x, 0, z, Blocks.BLACK_CONCRETE);
				}
			}
		}
		fill(19, 1, D + 10, 22, 3, D + 11, Blocks.WHITE_CONCRETE);
		for (int x = 19; x <= 22; x++) {
			set(x, 2, D + 10, Blocks.BLACK_STAINED_GLASS);
			set(x, 2, D + 11, Blocks.BLACK_STAINED_GLASS);
		}
		fill(19, 4, D + 10, 22, 4, D + 11, slab(Blocks.SMOOTH_STONE_SLAB, false));
		set(20, 5, D + 10, Blocks.BLACK_CONCRETE);
		set(20, 6, D + 10, Blocks.RED_CONCRETE);
		// shipping containers on the quay, west end
		container(-3, D + 1, 0, Blocks.ORANGE_CONCRETE, Blocks.ORANGE_TERRACOTTA);
		container(-3, D + 1, 3, Blocks.BLUE_CONCRETE, Blocks.BLUE_TERRACOTTA);
		container(20, D + 1, 0, Blocks.RED_CONCRETE, Blocks.RED_TERRACOTTA);
		container(20, D + 1, 3, Blocks.GREEN_CONCRETE, Blocks.GREEN_TERRACOTTA);
		set(6, 0, D + 2, Blocks.BARREL);
		set(7, 0, D + 2, Blocks.HAY_BLOCK);
		set(6, 1, D + 2, Blocks.HAY_BLOCK);
		// the street door is on the north wall (the template is exported turned 180 so the quay faces away from the road)
		door(13, 1, 0, Blocks.SPRUCE_DOOR, Direction.NORTH, false);
		finish();
		villagerSpot(6, 0, 8);
		villagerSpot(20, 0, 8);
		villagerSpot(8, -1, D + 2);
	}

	private void container(int x, int z, int y, Block main, Block rib) {
		for (int dx = 0; dx < 7; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				for (int dy = 0; dy < 3; dy++) {
					boolean shell = dx == 0 || dx == 6 || dz == 0 || dz == 2 || dy == 0 || dy == 2;
					if (!shell) {
						continue;
					}
					Block b = (dx % 2 == 1 && (dz == 0 || dz == 2)) ? rib : main;
					if (dx == 6 && dy == 1) {
						b = Blocks.IRON_BLOCK;
					}
					set(x + dx, y + dy, z + dz, b);
				}
			}
		}
	}

	/** 15. Maintenance depot: the Repair Worker's home, yard, materials and work benches. */
	public void maintenanceDepot() {
		final int W = 21;
		final int D = 13;
		pave(-1, -1, W, D + 7, Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE);
		fill(1, 0, 1, W - 2, 0, D - 2, Blocks.SMOOTH_STONE);
		fill(1, 7, 1, W - 2, 7, D - 2, Blocks.SMOOTH_STONE);
		for (Side s : SIDES) {
			int n = len(s, W, D);
			for (int u = 0; u < n; u++) {
				boolean pier = u % 5 == 0 || u == n - 1;
				for (int y = 0; y <= 7; y++) {
					BlockState b;
					if (pier) {
						b = st(Blocks.TUFF_BRICKS);
					} else if (y == 5) {
						b = st(u % 2 == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE);
					} else if (y == 0 || y == 7) {
						b = st(Blocks.POLISHED_TUFF);
					} else if (y >= 2 && y <= 3 && u % 5 == 2 && s != Side.S) {
						b = st(Blocks.GLASS_PANE);
					} else {
						b = st(Blocks.POLISHED_TUFF);
					}
					face(s, 0, 0, W, D, u, y, 0, b);
				}
			}
		}
		// two roll-up doors: one up (open), one down (iron trapdoor slats)
		for (int x = 1; x <= 4; x++) {
			for (int y = 1; y <= 4; y++) {
				set(x, y, D - 1, Blocks.AIR.defaultBlockState());
			}
			set(x, 4, D - 1, trap(Blocks.IRON_TRAPDOOR, Direction.NORTH, true, false));
		}
		for (int x = 6; x <= 9; x++) {
			for (int y = 1; y <= 4; y++) {
				set(x, y, D - 1, trap(Blocks.IRON_TRAPDOOR, Direction.NORTH, false, true));
			}
		}
		door(12, 1, D - 1, Blocks.IRON_DOOR, Direction.NORTH, false);
		for (int x = 14; x <= 18; x++) {
			set(x, 2, D - 1, Blocks.GLASS_PANE);
			set(x, 3, D - 1, Blocks.GLASS_PANE);
		}
		// DEPOT sign
		for (int u = 0; u <= 20; u++) {
			for (int y = 8; y <= 14; y++) {
				if (u >= 0 && u <= 20) {
					face(Side.S, 0, 0, W, D, u, y, 0, st(u == 0 || u == 20 || y == 8 || y == 14 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE));
					// a tuff back, so the sign is not a black slab from behind
					face(Side.S, 0, 0, W, D, u, y, -1, st(Blocks.POLISHED_TUFF));
				}
			}
		}
		text(Side.S, 0, 0, W, D, "DEPOT", 1, 13, 1, st(Blocks.YELLOW_CONCRETE));
		cornice(0, 0, W, D, 7, Blocks.POLISHED_TUFF_STAIRS, true);
		// beacon lamp + mast
		for (int y = 8; y <= 13; y++) {
			set(2, y, 2, Blocks.POLISHED_DEEPSLATE_WALL);
		}
		set(2, 14, 2, Blocks.ORANGE_STAINED_GLASS);
		set(2, 15, 2, Blocks.LIGHTNING_ROD);
		// workshop interior
		set(2, 1, 2, Blocks.SMITHING_TABLE);
		set(3, 1, 2, Blocks.ANVIL);
		set(4, 1, 2, Blocks.STONECUTTER);
		set(5, 1, 2, Blocks.CRAFTING_TABLE);
		set(6, 1, 2, Blocks.GRINDSTONE);
		// two Repair Benches (projecthero:repair_bench, City part B): the Repair Workers' job sites
		set(7, 1, 2, facingIf(repairBench(), Direction.SOUTH));
		set(19, 1, 3, facingIf(repairBench(), Direction.WEST));
		for (int x = 8; x <= 18; x += 2) {
			set(x, 1, 2, axis(Blocks.STRIPPED_SPRUCE_WOOD, Direction.Axis.Y));
			set(x, 2, 2, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
		}
		fill(14, 1, 6, 15, 1, 7, slab(Blocks.SPRUCE_SLAB, false));
		fill(14, 2, 6, 15, 3, 7, Blocks.STONE_BRICKS);
		fill(17, 1, 6, 18, 1, 7, slab(Blocks.SPRUCE_SLAB, false));
		fill(17, 2, 6, 18, 2, 7, Blocks.BRICKS);
		set(17, 3, 6, Blocks.GLASS);
		for (int x = 4; x <= 16; x += 6) {
			set(x, 6, 6, hanging(Blocks.LANTERN));
		}
		bed(19, 1, 9, Blocks.YELLOW_BED, Direction.NORTH);
		bed(19, 1, 6, Blocks.YELLOW_BED, Direction.NORTH);
		// yard: maintenance truck, cones, pallets
		for (int z = D + 1; z <= D + 6; z++) {
			for (int x = 1; x <= 3; x++) {
				boolean wheel = (z == D + 2 || z == D + 5) && x != 2;
				set(x, 0, z, wheel ? Blocks.BLACK_CONCRETE : Blocks.YELLOW_CONCRETE);
				set(x, 1, z, z >= D + 5 ? st(Blocks.BLACK_STAINED_GLASS) : st(Blocks.YELLOW_CONCRETE));
				if (z >= D + 5) {
					set(x, 2, z, Blocks.YELLOW_CONCRETE);
				} else {
					set(x, 2, z, x == 2 ? st(Blocks.BRICKS) : slab(Blocks.SMOOTH_STONE_SLAB, false));
				}
			}
		}
		set(2, 3, D + 6, Blocks.ORANGE_STAINED_GLASS);
		for (int x = 7; x <= 11; x += 2) {
			set(x, 0, D + 3, Blocks.ORANGE_CONCRETE);
			set(x, 1, D + 3, Blocks.END_ROD);
		}
		fill(15, 0, D + 2, 17, 0, D + 3, slab(Blocks.SPRUCE_SLAB, false));
		fill(15, 1, D + 2, 17, 1, D + 3, Blocks.STONE_BRICKS);
		set(16, 2, D + 2, Blocks.STONE_BRICKS);
		finish();
		villagerSpot(10, 0, 8);
		villagerSpot(5, 0, 8);
	}

	// ================================================================== VILLAGE-FRIENDLY PIECES

	/**
	 * 16. Townhouse row: narrow two-storey homes in the vanilla plains palette (cobblestone plinth, oak-log
	 * frame, plank / white-terracotta walls, oak or spruce gable roofs facing the street). They bridge a
	 * vanilla village house and a city block. Each unit has 2 beds and a vanilla job site.
	 */
	public void townhouseRow(int count) {
		townhouseRow(count, false);
	}

	/** A row with (optionally) a fenced back garden behind every unit, 12 deep, so the lot reads as a full block. */
	public void townhouseRow(int count, boolean yards) {
		for (int i = 0; i < count; i++) {
			townhouse(i * 7, i);
			if (yards) {
				backyard(i * 7, i, i == count - 1);
			}
		}
		finish();
		for (int i = 0; i < count; i++) {
			villagerSpot(i * 7 + 3, 0, 3);
		}
	}

	private void backyard(int x0, int v, boolean last) {
		for (int x = x0; x <= x0 + 6; x++) {
			for (int z = -13; z <= -1; z++) {
				set(x, -1, z, z == -2 && x == x0 + 3 ? Blocks.DIRT_PATH : Blocks.GRASS_BLOCK);
			}
			set(x, 0, -13, Blocks.OAK_FENCE);
		}
		for (int z = -13; z <= -2; z++) {
			set(x0, 0, z, Blocks.OAK_FENCE);
			if (last) {
				set(x0 + 6, 0, z, Blocks.OAK_FENCE);
			}
		}
		set(x0 + 3, -1, -3, Blocks.DIRT_PATH);
		switch (v % 3) {
			case 0 -> {
				for (int y = 0; y <= 2; y++) {
					set(x0 + 3, y, -9, axis(Blocks.OAK_LOG, Direction.Axis.Y));
				}
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						for (int y = 3; y <= 4; y++) {
							if (Math.abs(dx) + Math.abs(dz) <= (y == 3 ? 3 : 2) && !(dx == 0 && dz == 0 && y == 3)) {
								set(x0 + 3 + dx, y, -9 + dz, leaves(Blocks.OAK_LEAVES));
							}
						}
					}
				}
				set(x0 + 3, 3, -9, axis(Blocks.OAK_LOG, Direction.Axis.Y));
				set(x0 + 3, 5, -9, leaves(Blocks.OAK_LEAVES));
			}
			case 1 -> {
				Block[] fl = {Blocks.POPPY, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.DANDELION, Blocks.ALLIUM};
				for (int x = x0 + 1; x <= x0 + 5; x++) {
					for (int z = -12; z <= -10; z++) {
						set(x, 0, z, fl[Math.floorMod(x * 3 + z, fl.length)]);
					}
				}
				set(x0 + 5, 0, -5, Blocks.OAK_FENCE);
				set(x0 + 5, 1, -5, Blocks.LANTERN);
			}
			default -> {
				fill(x0 + 1, 0, -12, x0 + 3, 0, -11, axis(Blocks.HAY_BLOCK, Direction.Axis.X));
				set(x0 + 1, 1, -12, axis(Blocks.HAY_BLOCK, Direction.Axis.Z));
				fill(x0 + 5, 0, -12, x0 + 5, 1, -10, axis(Blocks.SPRUCE_LOG, Direction.Axis.Z));
				set(x0 + 2, 0, -6, Blocks.POTTED_FERN);
			}
		}
	}

	private void townhouse(int x0, int v) {
		Block wall = switch (v % 3) {
			case 0 -> Blocks.OAK_PLANKS;
			case 1 -> Blocks.WHITE_TERRACOTTA;
			default -> Blocks.SPRUCE_PLANKS;
		};
		Block roof = v % 2 == 0 ? Blocks.SPRUCE_STAIRS : Blocks.DARK_OAK_STAIRS;
		Block roofSlab = v % 2 == 0 ? Blocks.SPRUCE_SLAB : Blocks.DARK_OAK_SLAB;
		Block frame = v % 3 == 2 ? Blocks.DARK_OAK_LOG : Blocks.OAK_LOG;
		Block[] jobs = {Blocks.COMPOSTER, Blocks.LOOM, Blocks.FLETCHING_TABLE, Blocks.BARREL, Blocks.LECTERN, Blocks.CARTOGRAPHY_TABLE};
		final int d = 9;
		fill(x0, 0, 0, x0 + 6, 0, d - 1, Blocks.COBBLESTONE);
		fill(x0 + 1, 0, 1, x0 + 5, 0, d - 2, Blocks.OAK_PLANKS);
		fill(x0 + 1, 4, 1, x0 + 5, 4, d - 2, Blocks.OAK_PLANKS);
		for (int y = 1; y <= 7; y++) {
			for (int x = x0; x <= x0 + 6; x++) {
				for (int z = 0; z < d; z++) {
					boolean edgeX = x == x0 || x == x0 + 6;
					boolean edgeZ = z == 0 || z == d - 1;
					if (!edgeX && !edgeZ) {
						continue;
					}
					BlockState b;
					if (edgeX && edgeZ) {
						b = axis(frame, Direction.Axis.Y);
					} else if (y == 4) {
						b = axis(frame, edgeX ? Direction.Axis.Z : Direction.Axis.X);
					} else if (y <= 3) {
						b = st(Blocks.COBBLESTONE);
						if (edgeZ && y == 2 && (x == x0 + 1 || x == x0 + 5)) {
							b = st(Blocks.GLASS_PANE);
						}
						if (edgeX && y == 2 && (z == 3 || z == 5)) {
							b = st(Blocks.GLASS_PANE);
						}
					} else {
						b = st(wall);
						if (edgeZ && (y == 5 || y == 6) && x >= x0 + 2 && x <= x0 + 4) {
							b = st(Blocks.GLASS_PANE);
						}
					}
					set(x, y, z, b);
				}
			}
		}
		// front door, stoop, lamp, window box
		door(x0 + 3, 1, d - 1, Blocks.OAK_DOOR, Direction.NORTH, v % 2 == 0);
		set(x0 + 3, 0, d, stair(Blocks.COBBLESTONE_STAIRS, Direction.NORTH, false));
		set(x0 + 3, 3, d, hanging(Blocks.LANTERN));
		set(x0 + 3, 4, d, slab(roofSlab, false));
		for (int x = x0 + 2; x <= x0 + 4; x++) {
			set(x, 4, d, trap(Blocks.SPRUCE_TRAPDOOR, Direction.NORTH, true, false));
		}
		set(x0 + 1, 1, d, Blocks.POTTED_RED_TULIP);
		// gable roof, ridge running back from the street
		for (int i = 0; i <= 3; i++) {
			int y = 8 + i;
			for (int z = -1; z <= d; z++) {
				if (i < 3) {
					set(x0 + i, y, z, stair(roof, Direction.EAST, false));
					set(x0 + 6 - i, y, z, stair(roof, Direction.WEST, false));
				} else {
					set(x0 + 3, y, z, slab(roofSlab, false));
				}
			}
			for (int x = x0 + i + 1; x <= x0 + 5 - i; x++) {
				set(x, y, 0, wall);
				set(x, y, d - 1, i == 1 && x == x0 + 3 ? st(Blocks.GLASS_PANE) : st(wall));
			}
		}
		if (v % 2 == 1) {
			fill(x0 + 5, 8, 2, x0 + 5, 12, 2, Blocks.COBBLESTONE);
			set(x0 + 5, 13, 2, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false));
		}
		// homes: two beds upstairs, a job site downstairs, a light
		bed(x0 + 1, 5, 2, Blocks.WHITE_BED, Direction.NORTH);
		bed(x0 + 5, 5, 2, v % 2 == 0 ? Blocks.YELLOW_BED : Blocks.RED_BED, Direction.NORTH);
		set(x0 + 2, 1, 1, jobs[v % jobs.length]);
		set(x0 + 4, 1, 1, Blocks.CRAFTING_TABLE);
		set(x0 + 3, 3, 4, hanging(Blocks.LANTERN));
		// a back door to the garden, and the stairs up to the bedrooms
		door(x0 + 3, 1, 0, Blocks.OAK_DOOR, Direction.SOUTH, false);
		stairRun(x0 + 1, 6, 0, 4, Direction.EAST, Blocks.OAK_STAIRS);
	}

	/** Village lane: 5-wide cobblestone/andesite/gravel mix with dirt-path shoulders and plains-style lamp posts. */
	public void lane(int a1, int a2, int c) {
		for (int a = a1; a <= a2; a++) {
			for (int k = -3; k <= 3; k++) {
				float f = rnd.nextFloat();
				Block b;
				if (Math.abs(k) == 3) {
					b = f < 0.7f ? Blocks.DIRT_PATH : Blocks.COARSE_DIRT;
				} else {
					b = f < 0.45f ? Blocks.COBBLESTONE : f < 0.75f ? Blocks.ANDESITE : f < 0.9f ? Blocks.STONE : Blocks.GRAVEL;
				}
				set(a, -1, c + k, b);
			}
			if (Math.floorMod(a, 14) == 7) {
				villageLamp(a, c - 4);
				villageLamp(a, c + 4);
			}
		}
	}

	/** Vanilla-style dirt path (3 wide). */
	public void path(int a1, int a2, int c) {
		for (int a = a1; a <= a2; a++) {
			for (int k = -1; k <= 1; k++) {
				set(a, -1, c + k, rnd.nextFloat() < 0.85f ? Blocks.DIRT_PATH : Blocks.GRASS_BLOCK);
			}
		}
	}

	/** Blend zone between two road styles: per-block random pick weighted along the run. */
	public void blend(int a1, int a2, int c, int half, Block[] from, Block[] to) {
		for (int a = a1; a <= a2; a++) {
			double t = (a - a1) / (double) Math.max(1, a2 - a1);
			for (int k = -half; k <= half; k++) {
				Block[] pal = rnd.nextDouble() < t ? to : from;
				set(a, -1, c + k, pal[rnd.nextInt(pal.length)]);
			}
		}
	}

	/** The plains-village lamp (fence post, wool-free): oak fence on cobble, lantern on top. */
	public void villageLamp(int x, int z) {
		set(x, 0, z, Blocks.COBBLESTONE);
		set(x, 1, z, Blocks.OAK_FENCE);
		set(x, 2, z, Blocks.OAK_FENCE);
		set(x, 3, z, Blocks.LANTERN);
	}

	public net.minecraft.core.Vec3i vanillaSize(String id) {
		return level.getStructureManager().get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(id))
				.map(t -> t.getSize()).orElse(net.minecraft.core.Vec3i.ZERO);
	}

	/** Places a vanilla template (for example a plains village house) with jigsaws resolved; returns its size. */
	public net.minecraft.core.Vec3i vanilla(String id, int x, int y, int z, Rotation r) {
		var mgr = level.getStructureManager();
		var opt = mgr.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(id));
		if (opt.isEmpty()) {
			System.out.println("[HARNESS] missing template " + id);
			return net.minecraft.core.Vec3i.ZERO;
		}
		var t = opt.get();
		var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(r)
				.addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor.INSTANCE);
		BlockPos q = p(x, y, z);
		t.placeInWorld(level, q, q, settings, level.getRandom(), 2);
		return t.getSize();
	}
	// ================================================================== STREET KIT (terrain-matching templates)
	//
	// Street templates are authored with "forward" = +x (east), the back edge at x = 0, "left" = north (z = 0).
	// The road surface is local y -1 (template y 0, which the terrain-matching projection lays over the top terrain
	// block), street furniture stands on y 0+, and connectors sit at local y 0 (template y 1) like vanilla streets.

	public static final String STREET = "projecthero:street";
	public static final String LOT = "projecthero:lot";
	public static final String BLOCK = "projecthero:block";
	public static final String VOID = "minecraft:structure_void";

	/**
	 * A street connector on a street edge. Every connector targets {@code projecthero:street}, but only a piece's BACK
	 * connector (the one with no pool) is named that: forward connectors are named {@code projecthero:street_out}, so a
	 * segment can only ever attach by its back end (otherwise the jigsaw happily attaches a segment by its front and
	 * the whole arm runs backwards, its blocks on the wrong side).
	 */
	public void streetJigsaw(int x, int z, Direction f, String pool) {
		boolean back = pool.equals("minecraft:empty");
		jigsaw(x, 0, z, f, back ? STREET : STREET_OUT, STREET, pool, VOID);
	}

	public static final String STREET_OUT = "projecthero:street_out";

	/** A lot connector: the building/block jigsaw it faces has the name {@code projecthero:lot}. */
	public void lotJigsaw(int x, int z, Direction f, String pool, String finalState) {
		jigsaw(x, 0, z, f, LOT, LOT, pool, finalState);
	}

	private Block asphaltMix() {
		float f = rnd.nextFloat();
		return f < 0.07f ? Blocks.GRAY_CONCRETE_POWDER : f < 0.11f ? Blocks.ANDESITE : Blocks.GRAY_CONCRETE;
	}

	private Block laneMix() {
		float f = rnd.nextFloat();
		return f < 0.45f ? Blocks.COBBLESTONE : f < 0.72f ? Blocks.ANDESITE : f < 0.88f ? Blocks.STONE : Blocks.GRAVEL;
	}

	private Block shoulderMix() {
		return rnd.nextFloat() < 0.72f ? Blocks.DIRT_PATH : Blocks.COARSE_DIRT;
	}

	/** The village-edge road: a lane that is mostly packed earth, the way the town turns back into a village. */
	private Block edgeRoadMix() {
		float f = rnd.nextFloat();
		return f < 0.46f ? Blocks.DIRT_PATH : f < 0.68f ? Blocks.COBBLESTONE : f < 0.80f ? Blocks.GRAVEL : f < 0.90f ? Blocks.COARSE_DIRT : Blocks.ANDESITE;
	}

	/** A single-column avenue street light (single column so a terrain-following street can't shear it). */
	public void avenueLamp(int x, int z) {
		set(x, 0, z, Blocks.POLISHED_DEEPSLATE);
		for (int y = 1; y <= 3; y++) {
			set(x, y, z, Blocks.POLISHED_DEEPSLATE_WALL);
		}
		set(x, 4, z, Blocks.LANTERN);
	}

	private BlockState avenueCell(int a, int k) {
		int ak = Math.abs(k);
		if (ak <= 3) {
			return st(k == 0 && Math.floorMod(a, 6) < 3 ? Blocks.YELLOW_CONCRETE : asphaltMix());
		}
		if (ak == 4) {
			return st(Blocks.POLISHED_ANDESITE);
		}
		return st(Math.floorMod(a, 4) == 0 ? Blocks.STONE_BRICKS : Blocks.SMOOTH_STONE);
	}

	/** The centre crossing: two avenues meet; four corner kerbs, zebra crossings on every approach. */
	public void crossing() {
		for (int x = 0; x < 15; x++) {
			for (int z = 0; z < 15; z++) {
				int kx = x - 7;
				int kz = z - 7;
				int ax = Math.abs(kx);
				int az = Math.abs(kz);
				BlockState s;
				if (ax <= 3 || az <= 3) {
					boolean zebraX = ax <= 3 && az >= 5 && Math.floorMod(kx, 2) == 0;
					boolean zebraZ = az <= 3 && ax >= 5 && Math.floorMod(kz, 2) == 0;
					s = st(zebraX || zebraZ ? Blocks.WHITE_CONCRETE : (ax <= 1 && az <= 1 ? Blocks.POLISHED_ANDESITE : asphaltMix()));
				} else if (Math.min(ax, az) == 4) {
					s = st(Blocks.POLISHED_ANDESITE);
				} else {
					s = st((x + z) % 4 == 0 ? Blocks.STONE_BRICKS : Blocks.SMOOTH_STONE);
				}
				set(x, -1, z, s);
			}
		}
		avenueLamp(1, 1);
		avenueLamp(13, 1);
		avenueLamp(1, 13);
		avenueLamp(13, 13);
		set(13, 0, 5, Blocks.RED_NETHER_BRICK_WALL);
	}

	/**
	 * An avenue segment: 7-wide asphalt with a dashed centre line, kerbs, 3-wide sidewalks, a street light every 12
	 * blocks, a zebra crossing at the near end, a hydrant and a bench.
	 */
	public void avenue(int length) {
		for (int a = 0; a < length; a++) {
			for (int z = 0; z < 15; z++) {
				int k = z - 7;
				BlockState s = avenueCell(a, k);
				if (a >= 1 && a <= 3 && Math.abs(k) <= 3 && Math.floorMod(k, 2) == 0) {
					s = st(Blocks.WHITE_CONCRETE);
				}
				set(a, -1, z, s);
			}
			if (Math.floorMod(a, 12) == 6) {
				avenueLamp(a, 2);
				avenueLamp(a, 12);
			}
		}
		set(15, 0, 13, Blocks.RED_NETHER_BRICK_WALL);
		bench(length - 14, 13, Direction.NORTH, 3, true);
		bench(length - 30, 1, Direction.SOUTH, 3, true);
	}

	/** Avenue to lane: the asphalt breaks up into cobble over the run, the kerbs fade into dirt-path shoulders. */
	public void blendAvenueLane(int length) {
		Block[] ave = {Blocks.GRAY_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.POLISHED_ANDESITE, Blocks.ANDESITE};
		for (int a = 0; a < length; a++) {
			double t = (a + 0.5) / length;
			for (int z = 0; z < 9; z++) {
				int ak = Math.abs(z - 4);
				Block b;
				if (ak <= 2) {
					b = rnd.nextDouble() < t ? laneMix() : ave[rnd.nextInt(ave.length)];
				} else if (ak == 3) {
					b = rnd.nextDouble() < t ? shoulderMix() : ave[rnd.nextInt(ave.length)];
				} else {
					b = rnd.nextDouble() < t * 1.6 ? null : Blocks.POLISHED_ANDESITE;
				}
				if (b != null) {
					set(a, -1, z, b);
				}
			}
		}
	}

	/** A town lane: 5-wide cobble/andesite/stone/gravel mix, dirt-path shoulders, plains lamp posts every 14. */
	public void lane(int length, int lampPhase) {
		for (int a = 0; a < length; a++) {
			for (int z = 1; z <= 7; z++) {
				int ak = Math.abs(z - 4);
				set(a, -1, z, ak <= 2 ? laneMix() : shoulderMix());
			}
			if (Math.floorMod(a - lampPhase, 14) == 0) {
				villageLamp(a, 0);
				villageLamp(a, 8);
			}
		}
	}

	/** The ring road at the village edge: mostly packed earth with cobble patches; lamps on the town side only. */
	public void edgeRoad(int length, int[] lampsAt) {
		for (int a = 0; a < length; a++) {
			for (int z = 1; z <= 7; z++) {
				int ak = Math.abs(z - 4);
				Block b = ak <= 2 ? edgeRoadMix() : (rnd.nextFloat() < 0.6f ? Blocks.DIRT_PATH : rnd.nextFloat() < 0.5f ? Blocks.COARSE_DIRT : Blocks.GRASS_BLOCK);
				set(a, -1, z, b);
			}
		}
		for (int a : lampsAt) {
			villageLamp(a, 0);
		}
	}

	/** A vanilla-style dirt path running out from the ring road into the village, a plains lamp at its end. */
	public void edgePath(int length) {
		for (int a = 0; a < length; a++) {
			for (int z = 2; z <= 4; z++) {
				set(a, -1, z, rnd.nextFloat() < 0.85f ? Blocks.DIRT_PATH : Blocks.GRASS_BLOCK);
			}
		}
		villageLamp(length - 1, 3);
	}

	/** A 9 x 9 lane crossing / turn: lane surface in a plus, shoulders, lamps at the corners. */
	public void laneSquare(boolean edge) {
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				int m = Math.min(Math.abs(x - 4), Math.abs(z - 4));
				int mx = Math.max(Math.abs(x - 4), Math.abs(z - 4));
				if (mx == 4 && m >= 3) {
					continue;
				}
				Block b = m <= 2 ? (edge ? edgeRoadMix() : laneMix()) : shoulderMix();
				set(x, -1, z, b);
			}
		}
		villageLamp(0, 0);
		villageLamp(8, 8);
	}

	// ================================================================== SMALL TOWN FILLERS

	/** 13 x 13 pocket park: a lawn with a big oak, flower beds, benches and a lamp. */
	public void pocketPark() {
		for (int x = 0; x < 13; x++) {
			for (int z = 0; z < 13; z++) {
				boolean path = (x == 6 || z == 12) || (z >= 5 && z <= 7 && x >= 4 && x <= 8);
				set(x, -1, z, path ? Blocks.DIRT_PATH : Blocks.GRASS_BLOCK);
				boolean hedge = (x == 0 || x == 12 || z == 0) && !(x == 6);
				if (hedge) {
					set(x, 0, z, leaves(Blocks.AZALEA_LEAVES));
				}
			}
		}
		tree(3, 3, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 4, 2.8);
		tree(9, 9, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, 5, 2.2);
		bench(4, 8, Direction.NORTH, 2, true);
		bench(7, 4, Direction.SOUTH, 2, true);
		parkLamp(8, 6);
		Block[] fl = {Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.AZURE_BLUET};
		for (int x = 1; x <= 4; x++) {
			for (int z = 9; z <= 10; z++) {
				set(x, 0, z, fl[(x + z) % fl.length]);
			}
		}
		finish();
	}

	/** 15 x 11 allotment: fenced crop beds round a water channel, a composter-free tool shed. */
	public void gardenPlot() {
		for (int x = 0; x < 15; x++) {
			for (int z = 0; z < 11; z++) {
				boolean edge = x == 0 || x == 14 || z == 0 || z == 10;
				boolean water = z == 5 && x >= 2 && x <= 12;
				boolean path = x == 7 || z == 10;
				if (edge && !(x == 7 && z == 10)) {
					set(x, -1, z, Blocks.GRASS_BLOCK);
					set(x, 0, z, Blocks.OAK_FENCE);
				} else if (water) {
					set(x, -1, z, Blocks.WATER);
				} else if (path) {
					set(x, -1, z, Blocks.DIRT_PATH);
				} else {
					set(x, -1, z, Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE, 7));
					Block crop = x < 7 ? (z < 5 ? Blocks.WHEAT : Blocks.CARROTS) : (z < 5 ? Blocks.POTATOES : Blocks.BEETROOTS);
					int age = crop == Blocks.BEETROOTS ? 3 : 7;
					BlockState cs = crop.defaultBlockState();
					cs = crop == Blocks.BEETROOTS ? cs.setValue(BlockStateProperties.AGE_3, age) : cs.setValue(BlockStateProperties.AGE_7, age);
					set(x, 0, z, cs);
				}
			}
		}
		set(7, 0, 5, Blocks.SPRUCE_PLANKS);
		set(7, -1, 5, Blocks.WATER);
		set(7, 0, 5, slab(Blocks.SPRUCE_SLAB, false));
		finish();
	}

	/** 9 x 9 corner plaza: a news kiosk, a lamp, a bench and a planter. */
	public void kioskPlaza() {
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				set(x, -1, z, (x + z) % 2 == 0 ? Blocks.SMOOTH_STONE : Blocks.POLISHED_ANDESITE);
			}
		}
		fill(1, 0, 1, 3, 2, 3, Blocks.GREEN_TERRACOTTA);
		set(2, 1, 3, Blocks.AIR.defaultBlockState());
		set(2, 0, 3, Blocks.AIR.defaultBlockState());
		fill(0, 3, 0, 4, 3, 4, slab(Blocks.DARK_OAK_SLAB, false));
		set(0, 0, 2, Blocks.BLUE_CONCRETE);
		set(0, 1, 2, Blocks.RED_CONCRETE);
		parkLamp(7, 1);
		bench(5, 6, Direction.NORTH, 3, true);
		finish();
	}
}

