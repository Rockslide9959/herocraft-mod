package com.projecthero.mod.gametest.city;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * v0.16.2 City part A (dev tooling, never shipped): regenerates every City NBT template and template pool from the
 * {@link CityBuilder} routines. Run it with the gated gametest ({@code CityV0162GameTests.exportCityTemplates}, enabled
 * by the env var {@code PROJECTHERO_CITY_EXPORT=1}); it writes straight into {@code src/main/resources}.
 * <p>
 * Conventions (see docs/CITY_REFERENCE.md):
 * <ul>
 * <li>Buildings and blocks are <b>rigid</b>; their street side is the template's max-z edge, where a one-row apron
 * carries the {@code projecthero:lot} / {@code projecthero:block} jigsaw at template y = (builder y 0). Below-ground
 * layers keep only what was placed (structure void elsewhere, so terrain shows through); above ground every empty
 * cell is saved as air, so a building clears its own box.</li>
 * <li>Streets are <b>terrain-matching</b>: road surface at template y 0, furniture above, structure void everywhere
 * else, box 8 tall. Connectors are {@code projecthero:street} at template y 1.</li>
 * <li>No template uses a random processor: the City Repair blueprint (part B) reads these templates back as-is.</li>
 * </ul>
 */
public final class CityTemplateExporter {
	public static final String NS = "projecthero";

	/** A measured building/lot template: size and the template-local x of its street jigsaw. */
	public record Built(String name, int w, int h, int d, int jx) {
	}

	private final ServerLevel level;
	private final Path resources;
	private final Map<String, Built> built = new LinkedHashMap<>();
	private final Map<String, String> pools = new LinkedHashMap<>();
	private final List<String> log = new ArrayList<>();
	private int slot;

	public CityTemplateExporter(ServerLevel level, Path resources) {
		this.level = level;
		this.resources = resources;
	}

	public List<String> log() {
		return log;
	}

	public Map<String, Built> built() {
		return built;
	}

	/** Finds src/main/resources by walking up from the working directory to the project root. */
	public static Path findResources() {
		Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (p != null && !Files.exists(p.resolve("gradle.properties"))) {
			p = p.getParent();
		}
		if (p == null) {
			throw new IllegalStateException("project root not found from " + System.getProperty("user.dir"));
		}
		return p.resolve("src/main/resources");
	}

	private BlockPos nextOrigin() {
		int s = slot++;
		return new BlockPos(200_000 + (s % 12) * 160, 120, 200_000 + (s / 12) * 160);
	}

	// ------------------------------------------------------------------ low-level save

	private static void placeJigsaw(ServerLevel level, BlockPos pos, Direction front, String name, String target, String pool, String finalState) {
		FrontAndTop ft = front.getAxis().isHorizontal() ? FrontAndTop.fromFrontAndTop(front, Direction.UP) : FrontAndTop.fromFrontAndTop(front, Direction.NORTH);
		level.setBlock(pos, Blocks.JIGSAW.defaultBlockState().setValue(JigsawBlock.ORIENTATION, ft), 2 | 16);
		if (level.getBlockEntity(pos) instanceof JigsawBlockEntity be) {
			be.setName(ResourceLocation.parse(name));
			be.setTarget(ResourceLocation.parse(target));
			be.setPool(ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.parse(pool)));
			be.setFinalState(finalState);
			be.setJoint(front.getAxis().isHorizontal() ? JigsawBlockEntity.JointType.ALIGNED : JigsawBlockEntity.JointType.ROLLABLE);
		}
	}

	/**
	 * Saves the world box [lo, hi] as {@code city/<name>}. Cells below {@code groundTop} (inclusive) that are air become
	 * structure void; with {@code voidAbove} every air cell does (streets, blocks).
	 */
	private void save(String name, BlockPos lo, BlockPos hi, int groundTop, boolean voidAbove) {
		for (BlockPos q : BlockPos.betweenClosed(lo, hi)) {
			BlockState s = level.getBlockState(q);
			// streets and blocks keep two rows of air over their ground so they clear the grass and flowers that
			// neighbouring chunks' vegetation can plant on the site before the City is placed
			boolean clearance = voidAbove && q.getY() > groundTop && q.getY() <= groundTop + 2;
			if (s.isAir() && !clearance && (voidAbove || q.getY() <= groundTop)) {
				level.setBlock(q, Blocks.STRUCTURE_VOID.defaultBlockState(), 2 | 16);
			}
		}
		Vec3i size = new Vec3i(hi.getX() - lo.getX() + 1, hi.getY() - lo.getY() + 1, hi.getZ() - lo.getZ() + 1);
		StructureTemplate t = new StructureTemplate();
		t.fillFromWorld(level, lo, size, false, Blocks.STRUCTURE_VOID);
		CompoundTag tag = t.save(new CompoundTag());
		Path out = resources.resolve("data/" + NS + "/structure/city/" + name + ".nbt");
		try {
			Files.createDirectories(out.getParent());
			NbtIo.writeCompressed(tag, out);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		// leave the export site clean for the next template
		for (BlockPos q : BlockPos.betweenClosed(lo.offset(-2, -2, -2), hi.offset(2, 2, 2))) {
			if (!level.getBlockState(q).isAir()) {
				level.setBlock(q, Blocks.AIR.defaultBlockState(), 2 | 16);
			}
		}
		log.add(String.format("%-34s %3d x %3d x %3d", name, size.getX(), size.getY(), size.getZ()));
	}

	// ------------------------------------------------------------------ buildings

	/**
	 * Builds and saves a building: adds a one-row apron in front of its southern-most block (world +z) and the lot
	 * jigsaw on it at the builder x {@code jx} (builder y 0).
	 */
	public Built building(String name, Rotation rot, int jx, Consumer<CityBuilder> fn) {
		BlockPos o = nextOrigin();
		CityBuilder b = new CityBuilder(level).at(o, rot);
		fn.accept(b);
		int fz = b.maxZ + 1;
		int groundY = o.getY() - 1;
		for (int x = b.minX; x <= b.maxX; x++) {
			BlockPos behind = new BlockPos(x, groundY, fz - 1);
			BlockState s = level.getBlockState(behind);
			Block apron = s.isAir() || !s.isCollisionShapeFullBlock(level, behind) || s.is(Blocks.WATER) ? Blocks.SMOOTH_STONE : s.getBlock();
			if (apron == Blocks.GRASS_BLOCK || apron == Blocks.FARMLAND) {
				apron = Blocks.DIRT_PATH;
			}
			level.setBlock(new BlockPos(x, groundY, fz), apron.defaultBlockState(), 2 | 16);
		}
		int wx = b.world(jx, 0, 0).getX();
		BlockPos jig = new BlockPos(wx, o.getY(), fz);
		if (!level.getBlockState(jig).isAir()) {
			log.add("WARN " + name + ": street jigsaw spot is not air: " + level.getBlockState(jig));
		}
		placeJigsaw(level, jig, Direction.SOUTH, CityBuilder.LOT, CityBuilder.LOT, "minecraft:empty", "minecraft:air");
		BlockPos lo = new BlockPos(b.minX, Math.min(b.minY, groundY), b.minZ);
		BlockPos hi = new BlockPos(b.maxX, b.maxY, fz);
		Built r = new Built(name, hi.getX() - lo.getX() + 1, hi.getY() - lo.getY() + 1, hi.getZ() - lo.getZ() + 1, wx - lo.getX());
		save("building/" + name, lo, hi, groundY, false);
		built.put(name, r);
		return r;
	}

	public void filler(String name, Consumer<CityBuilder> fn, int jx) {
		building(name, Rotation.NONE, jx, fn);
	}

	// ------------------------------------------------------------------ streets

	/** Builds a street template whose box is [0, length-1] x [-1, 6] x [0, width-1] in builder coordinates. */
	private void street(String name, int length, int width, Consumer<CityBuilder> fn) {
		BlockPos o = nextOrigin();
		CityBuilder b = new CityBuilder(level).at(o, Rotation.NONE);
		fn.accept(b);
		BlockPos lo = o.offset(0, -1, 0);
		BlockPos hi = o.offset(length - 1, 6, width - 1);
		if (b.minX < lo.getX() || b.maxX > hi.getX() || b.minZ < lo.getZ() || b.maxZ > hi.getZ() || b.maxY > hi.getY()) {
			log.add("WARN street " + name + " spills out of its box");
		}
		save("street/" + name, lo, hi, o.getY() - 1, true);
	}

	// ------------------------------------------------------------------ blocks

	public enum LotKind {
		FRONT_LEFT, FRONT_RIGHT, FRONT_CENTER, BACK_WEST
	}

	public record Lot(LotKind kind, String pool, List<String> candidates) {
	}

	/**
	 * A downtown city block: a 48 x 48 rigid ground plate (box 64 tall, void above the ground so it never clears
	 * terrain itself) with lot jigsaws for its buildings and a courtyard garden in whatever corner is left over.
	 * Front (avenue A) = z 47, west (avenue B) = x 0. The block attaches to its avenue at (ax, 47).
	 */
	public void block(String name, int ax, List<Lot> lots) {
		final int n = 48;
		BlockPos o = nextOrigin();
		CityBuilder b = new CityBuilder(level).at(o, Rotation.NONE);
		for (int x = 0; x < n; x++) {
			for (int z = 0; z < n; z++) {
				boolean edge = z >= n - 2 || x <= 1;
				b.set(x, -1, z, edge ? ((x + z) % 4 == 0 ? Blocks.STONE_BRICKS : Blocks.SMOOTH_STONE)
						: ((x / 2 + z / 2) % 2 == 0 ? Blocks.POLISHED_ANDESITE : Blocks.SMOOTH_STONE));
			}
		}
		int frontMinZ = n;
		int westMaxX = 0;
		int westMaxZ = -1;
		List<int[]> jigs = new ArrayList<>();
		for (Lot lot : lots) {
			int maxW = 0;
			int maxD = 0;
			int maxJx = 0;
			int maxRight = 0;
			for (String c : lot.candidates()) {
				Built bb = built.get(c);
				maxW = Math.max(maxW, bb.w());
				maxD = Math.max(maxD, bb.d());
				maxJx = Math.max(maxJx, bb.jx());
				maxRight = Math.max(maxRight, bb.w() - 1 - bb.jx());
			}
			switch (lot.kind()) {
				case FRONT_LEFT, FRONT_RIGHT, FRONT_CENTER -> {
					Built bb = built.get(lot.candidates().get(0));
					int a = switch (lot.kind()) {
						case FRONT_LEFT -> bb.jx();
						case FRONT_RIGHT -> n - bb.w() + bb.jx();
						default -> (n - bb.w()) / 2 + bb.jx();
					};
					jigs.add(new int[] {a, n - 1, 0});
					b.lotJigsaw(a, n - 1, Direction.NORTH, lot.pool(), "minecraft:air");
					frontMinZ = Math.min(frontMinZ, n - 1 - bb.d());
					log.add(String.format("  %s %s: %s x %d..%d z %d..%d", name, lot.kind(), bb.name(), a - bb.jx(), a - bb.jx() + bb.w() - 1, n - 1 - bb.d(),
							n - 2));
				}
				case BACK_WEST -> {
					int bz = maxJx;
					b.lotJigsaw(0, bz, Direction.EAST, lot.pool(), "minecraft:air");
					westMaxX = Math.max(westMaxX, maxD);
					westMaxZ = Math.max(westMaxZ, bz + maxRight);
					log.add(String.format("  %s BACK_WEST: %s x 1..%d z 0..%d", name, lot.candidates(), maxD, bz + maxRight));
				}
			}
		}
		if (westMaxZ >= frontMinZ) {
			log.add("WARN " + name + ": the back-west lot (z <= " + westMaxZ + ") reaches the front lots (z >= " + frontMinZ + ")");
		}
		// courtyard garden in the back-right corner
		int cx0 = westMaxX + 3;
		int cz1 = frontMinZ - 2;
		if (cx0 < n - 6 && cz1 > 6) {
			for (int x = cx0; x <= n - 2; x++) {
				for (int z = 1; z <= cz1; z++) {
					boolean border = x == cx0 || x == n - 2 || z == 1 || z == cz1;
					b.set(x, -1, z, border ? Blocks.STONE_BRICKS : ((x - cx0) % 6 == 3 || (z - 1) % 6 == 3 ? Blocks.DIRT_PATH : Blocks.GRASS_BLOCK));
				}
			}
			int midX = (cx0 + n - 2) / 2;
			int midZ = (1 + cz1) / 2;
			b.tree(cx0 + 3, 4, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 4, 2.6);
			if (cz1 - 4 > 8) {
				b.tree(n - 5, cz1 - 3, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, 5, 2.2);
			}
			b.parkLamp(midX, midZ);
			b.bench(midX - 1, midZ + 2, Direction.NORTH, 3, true);
			b.finish();
			log.add(String.format("  %s courtyard x %d..%d z 1..%d", name, cx0, n - 2, cz1));
		}
		// the block's own connector to its avenue
		b.jigsaw(ax, 0, n - 1, Direction.SOUTH, CityBuilder.BLOCK, CityBuilder.BLOCK, "minecraft:empty", "minecraft:air");
		for (int[] j : jigs) {
			if (j[0] == ax) {
				log.add("WARN " + name + ": a lot jigsaw shares x " + ax + " with the attach jigsaw");
			}
		}
		save("block/" + name, o.offset(0, -1, 0), o.offset(n - 1, 62, n - 1), o.getY() - 1, true);
	}

	// ------------------------------------------------------------------ pools

	private static String element(String location, String projection, boolean legacy, String processors) {
		return "{\"element\":{\"element_type\":\"minecraft:" + (legacy ? "legacy_" : "") + "single_pool_element\",\"location\":\"" + location
				+ "\",\"processors\":\"" + processors + "\",\"projection\":\"" + projection + "\"},\"weight\":";
	}

	/** A pool of our own templates: entries are "path" or "path*weight". */
	private void pool(String name, String projection, String fallback, String... entries) {
		StringBuilder sb = new StringBuilder("{\n  \"fallback\": \"" + fallback + "\",\n  \"elements\": [\n");
		for (int i = 0; i < entries.length; i++) {
			String e = entries[i];
			int w = 1;
			int star = e.indexOf('*');
			if (star >= 0) {
				w = Integer.parseInt(e.substring(star + 1));
				e = e.substring(0, star);
			}
			sb.append("    ").append(element(NS + ":city/" + e, projection, false, "minecraft:empty")).append(w).append("}");
			sb.append(i < entries.length - 1 ? ",\n" : "\n");
		}
		sb.append("  ]\n}\n");
		pools.put(name, sb.toString());
	}

	private void rawPool(String name, String json) {
		pools.put(name, json);
	}

	public void writePools() {
		for (Map.Entry<String, String> e : pools.entrySet()) {
			Path out = resources.resolve("data/" + NS + "/worldgen/template_pool/city/" + e.getKey() + ".json");
			try {
				Files.createDirectories(out.getParent());
				Files.writeString(out, e.getValue());
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}
		log.add("wrote " + pools.size() + " pools");
	}

	// ------------------------------------------------------------------ the whole city

	/** Arm letters, each with its downtown block and its two radial town lots (left = guaranteed, right = random). */
	private static final String[] ARMS = {"a", "b", "c", "d"};

	public void exportAll() {
		// ---- 1. buildings (their measured sizes drive the block layouts)
		building("police_precinct", Rotation.NONE, 12, CityBuilder::policeStation);
		building("office_skyscraper", Rotation.NONE, 7, CityBuilder::officeSkyscraper);
		building("city_bank", Rotation.NONE, 9, CityBuilder::bank);
		building("hospital", Rotation.NONE, 11, CityBuilder::hospital);
		building("apartment_tower", Rotation.NONE, 8, CityBuilder::apartmentTower);
		building("daily_bulletin", Rotation.NONE, 10, CityBuilder::dailyBulletin);
		building("clock_tower", Rotation.NONE, 4, CityBuilder::clockTower);
		building("train_station", Rotation.NONE, 15, CityBuilder::trainStation);
		building("brownstone_walkup", Rotation.NONE, 11, CityBuilder::rooftopSetPiece);
		building("fire_station", Rotation.NONE, 8, CityBuilder::fireStation);
		building("market_square", Rotation.NONE, 15, CityBuilder::marketMall);
		building("central_park", Rotation.NONE, 16, CityBuilder::park);
		building("construction_site", Rotation.NONE, 12, CityBuilder::constructionSite);
		building("warehouse_docks", Rotation.CLOCKWISE_180, 13, CityBuilder::warehouseDocks);
		building("maintenance_depot", Rotation.NONE, 12, CityBuilder::maintenanceDepot);
		building("townhouse_row_3", Rotation.NONE, 10, b -> b.townhouseRow(3, true));
		building("townhouse_row_4", Rotation.NONE, 13, b -> b.townhouseRow(4, true));
		building("townhouse_row_5", Rotation.NONE, 17, b -> b.townhouseRow(5, true));
		filler("pocket_park", CityBuilder::pocketPark, 6);
		filler("garden_plot", CityBuilder::gardenPlot, 7);
		filler("kiosk_plaza", CityBuilder::kioskPlaza, 2);

		// ---- 2. downtown blocks: civic (police + office, bank behind), medical, news, station
		block("civic", 24, List.of(new Lot(LotKind.FRONT_LEFT, "projecthero:city/lots/police", List.of("police_precinct")),
				new Lot(LotKind.FRONT_RIGHT, "projecthero:city/lots/office", List.of("office_skyscraper")),
				new Lot(LotKind.BACK_WEST, "projecthero:city/lots/bank", List.of("city_bank"))));
		block("medical", 24, List.of(new Lot(LotKind.FRONT_LEFT, "projecthero:city/lots/hospital", List.of("hospital")),
				new Lot(LotKind.FRONT_RIGHT, "projecthero:city/lots/apartment", List.of("apartment_tower")),
				new Lot(LotKind.BACK_WEST, "projecthero:city/lots/core_back", List.of("apartment_tower", "office_skyscraper"))));
		block("news", 24, List.of(new Lot(LotKind.FRONT_LEFT, "projecthero:city/lots/bulletin", List.of("daily_bulletin")),
				new Lot(LotKind.FRONT_RIGHT, "projecthero:city/lots/clock", List.of("clock_tower")),
				new Lot(LotKind.BACK_WEST, "projecthero:city/lots/core_back_wide", List.of("brownstone_walkup", "apartment_tower", "office_skyscraper"))));
		int stationJig = (48 - built.get("train_station").w()) / 2 + built.get("train_station").jx();
		block("station", stationJig == 24 ? 22 : 24, List.of(new Lot(LotKind.FRONT_CENTER, "projecthero:city/lots/station", List.of("train_station"))));
		int stationAttach = stationJig == 24 ? 22 : 24;

		// ---- 3. streets
		street("crossing", 15, 15, b -> {
			b.crossing();
			b.streetJigsaw(14, 7, Direction.EAST, "projecthero:city/arms/a");
			b.streetJigsaw(7, 0, Direction.NORTH, "projecthero:city/arms/b");
			b.streetJigsaw(0, 7, Direction.WEST, "projecthero:city/arms/c");
			b.streetJigsaw(7, 14, Direction.SOUTH, "projecthero:city/arms/d");
		});
		String[] blocks = {"civic", "medical", "news", "station"};
		String[] guaranteed = {"market", "park", "depot", "fire"};
		for (int i = 0; i < 4; i++) {
			String arm = ARMS[i];
			int attach = i == 3 ? stationAttach : 24;
			street("avenue_" + arm, 48, 15, b -> {
				b.avenue(48);
				b.streetJigsaw(0, 7, Direction.WEST, "minecraft:empty");
				b.streetJigsaw(47, 7, Direction.EAST, "projecthero:city/arms/" + arm + "/blend");
				b.jigsaw(attach, 0, 0, Direction.NORTH, CityBuilder.BLOCK, CityBuilder.BLOCK, "projecthero:city/blocks/" + arm, CityBuilder.VOID);
			});
			street("blend_" + arm, 8, 9, b -> {
				b.blendAvenueLane(8);
				b.streetJigsaw(0, 4, Direction.WEST, "minecraft:empty");
				b.streetJigsaw(7, 4, Direction.EAST, "projecthero:city/arms/" + arm + "/radial");
			});
			String left = guaranteed[i];
			street("radial_" + arm, 28, 9, b -> {
				b.lane(28, 3);
				b.streetJigsaw(0, 4, Direction.WEST, "minecraft:empty");
				b.streetJigsaw(27, 4, Direction.EAST, "projecthero:city/junction");
				b.lotJigsaw(10, 0, Direction.NORTH, "projecthero:city/lots/" + left, CityBuilder.VOID);
				b.lotJigsaw(10, 8, Direction.SOUTH, "projecthero:city/lots/town_big", CityBuilder.VOID);
			});
			pool("arms/" + arm, "rigid", "minecraft:empty", "street/avenue_" + arm);
			pool("arms/" + arm + "/blend", "terrain_matching", "minecraft:empty", "street/blend_" + arm);
			pool("arms/" + arm + "/radial", "terrain_matching", "minecraft:empty", "street/radial_" + arm);
			pool("blocks/" + arm, "rigid", "minecraft:empty", "block/" + blocks[i]);
		}
		street("junction", 9, 9, b -> {
			b.laneSquare(false);
			b.streetJigsaw(0, 4, Direction.WEST, "minecraft:empty");
			b.streetJigsaw(4, 0, Direction.NORTH, "projecthero:city/ring/1");
			// the arm runs on out of town as a vanilla village street
			b.jigsaw(8, 0, 4, Direction.EAST, "minecraft:street", "minecraft:street", "projecthero:city/village/streets", CityBuilder.VOID);
		});
		String[] next = {"projecthero:city/ring/2", "projecthero:city/ring/3", "projecthero:city/ring/corner", "projecthero:city/ring/5",
				"projecthero:city/ring/6", "minecraft:empty"};
		int[] ringIdx = {1, 2, 3, 4, 5, 6};
		for (int r = 0; r < 6; r++) {
			int idx = ringIdx[r];
			String fwd = next[r];
			boolean streetFirst = idx % 2 == 0;
			street("ring_" + idx, 29, 9, b -> {
				b.edgeRoad(29, new int[] {4, 24});
				b.streetJigsaw(0, 4, Direction.WEST, "minecraft:empty");
				b.jigsaw(28, 0, 4, Direction.EAST, CityBuilder.STREET_OUT, CityBuilder.STREET, fwd, CityBuilder.VOID);
				b.lotJigsaw(14, 0, Direction.NORTH, "projecthero:city/lots/ring", CityBuilder.VOID);
				// the outer side is the village: a vanilla house and a vanilla street per segment
				int house = streetFirst ? 21 : 7;
				int lane = streetFirst ? 7 : 21;
				b.jigsaw(house, 0, 8, Direction.SOUTH, "minecraft:building_entrance", "minecraft:building_entrance", "projecthero:city/village/houses",
						CityBuilder.VOID);
				// a short village path with houses either side (the junctions and corners grow full vanilla streets)
				b.jigsaw(lane, 0, 8, Direction.SOUTH, CityBuilder.STREET_OUT, CityBuilder.STREET, "projecthero:city/village/edge_path", CityBuilder.VOID);
			});
			pool("ring/" + idx, "terrain_matching", "minecraft:empty", "street/ring_" + idx);
		}
		street("ring_corner", 9, 9, b -> {
			b.laneSquare(true);
			b.streetJigsaw(0, 4, Direction.WEST, "minecraft:empty");
			b.streetJigsaw(4, 0, Direction.NORTH, "projecthero:city/ring/4");
			b.jigsaw(8, 0, 4, Direction.EAST, "minecraft:street", "minecraft:street", "projecthero:city/village/streets", CityBuilder.VOID);
		});
		pool("ring/corner", "terrain_matching", "minecraft:empty", "street/ring_corner");
		street("edge_path", 15, 7, b -> {
			b.edgePath(15);
			b.streetJigsaw(0, 3, Direction.WEST, "minecraft:empty");
			for (int x : new int[] {5, 11}) {
				b.jigsaw(x, 0, 0, Direction.NORTH, "minecraft:building_entrance", "minecraft:building_entrance", "projecthero:city/village/houses", CityBuilder.VOID);
				b.jigsaw(x, 0, 6, Direction.SOUTH, "minecraft:building_entrance", "minecraft:building_entrance", "projecthero:city/village/houses", CityBuilder.VOID);
			}
		});
		pool("village/edge_path", "terrain_matching", "minecraft:empty", "street/edge_path");
		pool("center", "rigid", "minecraft:empty", "street/crossing");
		pool("junction", "terrain_matching", "minecraft:empty", "street/junction");

		// ---- 4. lot pools
		for (String[] g : new String[][] {{"police", "police_precinct"}, {"office", "office_skyscraper"}, {"bank", "city_bank"}, {"hospital", "hospital"},
				{"apartment", "apartment_tower"}, {"bulletin", "daily_bulletin"}, {"clock", "clock_tower"}, {"station", "train_station"},
				{"market", "market_square"}, {"park", "central_park"}, {"depot", "maintenance_depot"}, {"fire", "fire_station"}}) {
			pool("lots/" + g[0], "rigid", "minecraft:empty", "building/" + g[1]);
		}
		pool("lots/core_back", "rigid", "projecthero:city/lots/town_small", "building/apartment_tower", "building/office_skyscraper");
		pool("lots/core_back_wide", "rigid", "projecthero:city/lots/town_small", "building/brownstone_walkup*2", "building/apartment_tower",
				"building/office_skyscraper");
		pool("lots/town_big", "rigid", "projecthero:city/lots/town_small", "building/construction_site*2", "building/warehouse_docks*2",
				"building/brownstone_walkup*2", "building/townhouse_row_5*3", "building/townhouse_row_4*2");
		pool("lots/ring", "rigid", "projecthero:city/lots/town_small", "building/townhouse_row_3*5", "building/townhouse_row_4*4",
				"building/brownstone_walkup*1", "building/pocket_park*1", "building/garden_plot*1");
		pool("lots/town_small", "rigid", "minecraft:empty", "building/pocket_park*2", "building/garden_plot*2", "building/kiosk_plaza*1");

		// ---- 5. the village-edge alias pools' defaults (the City structure redirects them per biome)
		rawPool("villagers", vanillaCopy("village/plains/villagers"));
		rawPool("village/houses", vanillaCopy("village/plains/houses"));
		rawPool("village/streets", vanillaCopy("village/plains/streets"));
		writePools();
	}

	/** The default for an alias pool: vanilla's own pool JSON, copied from the game's built-in datapack. */
	private String vanillaCopy(String id) {
		try (var in = CityTemplateExporter.class.getResourceAsStream("/data/minecraft/worldgen/template_pool/" + id + ".json")) {
			if (in == null) {
				throw new IllegalStateException("vanilla pool " + id + " not found on the classpath");
			}
			return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
