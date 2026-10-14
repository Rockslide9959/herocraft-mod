package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.projecthero.mod.city.CityStructure;
import com.projecthero.mod.city.CityWorldgen;
import com.projecthero.mod.gametest.city.CityTemplateExporter;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * v0.16.2 City part A: the City structure, its set and tags, every template and pool, the guaranteed buildings and
 * the village POIs (beds, job sites, bells) in them, a real placement of a piece, and a full-city layout assembled in
 * memory (with its timing). Plus the gated template exporter (dev tooling).
 */
public class CityV0162GameTests implements FabricGameTest {
	private static final String NS = "projecthero";

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(NS, path);
	}

	private static StructureTemplate template(ServerLevel level, String path) {
		return level.getStructureManager().get(id("city/" + path)).orElseThrow(() -> new AssertionError("missing template city/" + path));
	}

	/** Every block of a kind in a template (template-local positions). */
	private static List<StructureTemplate.StructureBlockInfo> find(StructureTemplate t, Block b) {
		return t.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), b);
	}

	private static int beds(StructureTemplate t) {
		int n = 0;
		for (Block b : BuiltInRegistries.BLOCK) {
			if (b instanceof BedBlock) {
				for (StructureTemplate.StructureBlockInfo i : find(t, b)) {
					if (i.state().getValue(BedBlock.PART) == BedPart.HEAD) {
						n++;
					}
				}
			}
		}
		return n;
	}

	// ------------------------------------------------------------------ dev tooling (gated)

	/**
	 * Regenerates every City template and pool into src/main/resources when PROJECTHERO_CITY_EXPORT=1 (a no-op
	 * otherwise, so it passes in CI). Run alone: {@code ./gradlew runGameTest -PtestFilter=exportCityTemplates}.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 2400)
	public void exportCityTemplates(GameTestHelper helper) {
		if (!"1".equals(System.getenv("PROJECTHERO_CITY_EXPORT"))) {
			helper.succeed();
			return;
		}
		long t0 = System.currentTimeMillis();
		CityTemplateExporter ex = new CityTemplateExporter(helper.getLevel(), CityTemplateExporter.findResources());
		ex.exportAll();
		for (String line : ex.log()) {
			System.out.println("[CITY EXPORT] " + line);
		}
		System.out.println("[CITY EXPORT] done in " + (System.currentTimeMillis() - t0) + " ms");
		helper.succeed();
	}

	// ------------------------------------------------------------------ registration and data

	@GameTest(template = EMPTY_STRUCTURE)
	public void cityStructureSetAndTagsLoad(GameTestHelper helper) {
		var access = helper.getLevel().registryAccess();
		Registry<Structure> structures = access.registryOrThrow(Registries.STRUCTURE);
		Structure city = structures.get(CityStructure.KEY);
		helper.assertTrue(city instanceof CityStructure, "projecthero:city is registered as the City structure type");
		helper.assertTrue(city.type() == CityWorldgen.CITY_TYPE, "with the projecthero:city structure type");
		helper.assertTrue(city.terrainAdaptation() == net.minecraft.world.level.levelgen.structure.TerrainAdjustment.BEARD_BOX,
				"beard_box terrain adaptation (foundations under, terrain cleared over)");
		Holder<Structure> holder = structures.getHolderOrThrow(CityStructure.KEY);
		helper.assertTrue(holder.is(CityWorldgen.CITY_TAG), "#projecthero:city holds it (for /locate)");
		helper.assertTrue(holder.is(CityWorldgen.ON_CITY_MAPS), "#projecthero:on_city_maps holds it (City Map target)");
		for (String b : new String[] {"plains", "sunflower_plains", "meadow", "savanna"}) {
			var biome = access.registryOrThrow(Registries.BIOME).getHolderOrThrow(
					net.minecraft.resources.ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace(b)));
			helper.assertTrue(city.biomes().contains(biome), "a City can start in " + b);
		}
		var desert = access.registryOrThrow(Registries.BIOME).getHolderOrThrow(net.minecraft.world.level.biome.Biomes.DESERT);
		helper.assertFalse(city.biomes().contains(desert), "but not (yet) in a desert");

		StructureSet set = access.registryOrThrow(Registries.STRUCTURE_SET).get(id("city"));
		helper.assertTrue(set != null, "the projecthero:city structure set loads");
		helper.assertTrue(set.structures().size() == 1 && set.structures().get(0).structure().is(CityStructure.KEY), "it places the City");
		helper.assertTrue(set.placement() instanceof RandomSpreadStructurePlacement, "random spread");
		RandomSpreadStructurePlacement p = (RandomSpreadStructurePlacement) set.placement();
		helper.assertTrue(p.spacing() == 160 && p.separation() == 100, "spacing 160, separation 100 (got " + p.spacing() + "/" + p.separation() + ")");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void everyCityTemplateAndPoolLoads(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StructureTemplateManager mgr = level.getStructureManager();
		// every NBT file under structure/city
		var files = level.getServer().getResourceManager().listResources("structure",
				rl -> rl.getNamespace().equals(NS) && rl.getPath().startsWith("structure/city/") && rl.getPath().endsWith(".nbt"));
		helper.assertTrue(files.size() >= 45, "the City ships its templates (found " + files.size() + ")");
		for (ResourceLocation file : files.keySet()) {
			String path = file.getPath().substring("structure/".length(), file.getPath().length() - ".nbt".length());
			StructureTemplate t = mgr.get(id(path)).orElse(null);
			helper.assertTrue(t != null && t.getSize().getX() > 0, "template " + path + " loads");
			helper.assertTrue(t.getSize().getX() <= 48 && t.getSize().getZ() <= 48 && t.getSize().getY() <= 64,
					"template " + path + " stays within 48 x 64 x 48 (is " + t.getSize() + ")");
		}
		// every pool under projecthero:city/ resolves every element
		Registry<StructureTemplatePool> pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
		int n = 0;
		for (var e : pools.entrySet()) {
			if (!e.getKey().location().getNamespace().equals(NS) || !e.getKey().location().getPath().startsWith("city/")) {
				continue;
			}
			n++;
			for (StructurePoolElement el : e.getValue().getShuffledTemplates(RandomSource.create(1))) {
				if (el == net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement.INSTANCE) {
					continue; // vanilla's own house pool keeps a few empty slots
				}
				helper.assertTrue(el.getSize(mgr, Rotation.NONE).getX() > 0, "pool " + e.getKey().location() + " element " + el + " has a template");
			}
		}
		helper.assertTrue(n >= 40, "the City's template pools load (" + n + ")");
		helper.succeed();
	}

	// ------------------------------------------------------------------ the village inside the buildings

	@GameTest(template = EMPTY_STRUCTURE)
	public void buildingsCarryTheirBedsJobSitesAndBells(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Block desk = BuiltInRegistries.BLOCK.get(id("precinct_desk"));
		Block bench = BuiltInRegistries.BLOCK.get(id("repair_bench"));
		Block safe = BuiltInRegistries.BLOCK.get(id("safe_deposit"));
		helper.assertTrue(desk != Blocks.AIR && bench != Blocks.AIR && safe != Blocks.AIR, "the three City part B block ids exist");

		StructureTemplate police = template(level, "building/police_precinct");
		helper.assertTrue(find(police, desk).size() == 3, "three Precinct Desks (one per City Cop)");
		helper.assertTrue(beds(police) == 5, "five holding-cell bunks");
		helper.assertTrue(find(police, Blocks.BLAST_FURNACE).size() == 1 && find(police, Blocks.GRINDSTONE).size() == 1
				&& find(police, Blocks.SMITHING_TABLE).size() == 1, "the armoury: blast furnace, grindstone, smithing table");
		helper.assertTrue(find(police, Blocks.CAULDRON).isEmpty() && find(police, Blocks.LECTERN).isEmpty(),
				"no stray cauldrons or lecterns pulling leatherworkers / librarians into the cells");

		StructureTemplate depot = template(level, "building/maintenance_depot");
		helper.assertTrue(find(depot, bench).size() == 2, "two Repair Benches (two Repair Workers per City)");
		helper.assertTrue(beds(depot) == 2, "and two bunks for them");

		StructureTemplate bank = template(level, "building/city_bank");
		helper.assertTrue(find(bank, safe).size() >= 9, "a wall of Safe Deposit boxes in the vault");
		helper.assertTrue(find(bank, Blocks.EMERALD_BLOCK).size() > 10 && find(bank, Blocks.LECTERN).size() == 2, "emeralds and two teller lecterns");

		helper.assertTrue(beds(template(level, "building/apartment_tower")) == 12, "the apartment tower sleeps 12");
		helper.assertTrue(beds(template(level, "building/hospital")) == 7, "the hospital ward has 7 beds");
		helper.assertTrue(find(template(level, "building/hospital"), Blocks.BREWING_STAND).size() == 2, "and a pharmacy (2 brewing stands)");
		helper.assertTrue(beds(template(level, "building/fire_station")) == 5, "5 crew bunks");
		helper.assertTrue(find(template(level, "building/fire_station"), Blocks.WATER_CAULDRON).size() == 2, "water cauldrons (firefighters)");
		helper.assertTrue(beds(template(level, "building/brownstone_walkup")) == 8, "the walk-up sleeps 8");
		helper.assertTrue(beds(template(level, "building/townhouse_row_4")) == 8, "two beds per townhouse");

		for (String belled : new String[] {"building/clock_tower", "building/market_square", "building/central_park"}) {
			helper.assertTrue(find(template(level, belled), Blocks.BELL).size() == 1, belled + " has its meeting-point bell");
		}
		// villagers and golems arrive through jigsaws into the alias villager pool / the vanilla golem pool
		int spots = 0;
		int golems = 0;
		for (String b : new String[] {"apartment_tower", "police_precinct", "market_square", "central_park", "clock_tower"}) {
			for (StructureTemplate.StructureBlockInfo j : find(template(level, "building/" + b), Blocks.JIGSAW)) {
				String pool = j.nbt().getString("pool");
				if (pool.equals("projecthero:city/villagers")) {
					spots++;
				}
				if (pool.equals("minecraft:village/common/iron_golem")) {
					golems++;
				}
			}
		}
		helper.assertTrue(spots >= 10, "villagers spawn with the buildings (" + spots + " spots)");
		helper.assertTrue(golems == 3, "three iron golems: clock square, market, park");
		helper.succeed();
	}

	/** Upper-floor beds are only claimable if villagers can walk up: every multi-storey home has stairs. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void multiStoreyHomesHaveStairs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(find(template(level, "building/apartment_tower"), Blocks.SPRUCE_STAIRS).size() >= 20, "the apartment stairwell climbs six floors");
		helper.assertTrue(find(template(level, "building/brownstone_walkup"), Blocks.SPRUCE_STAIRS).size() >= 16, "the walk-up climbs to the roof garden");
		helper.assertTrue(find(template(level, "building/fire_station"), Blocks.SPRUCE_STAIRS).size() >= 6, "stairs to the bunks");
		helper.assertTrue(find(template(level, "building/townhouse_row_3"), Blocks.OAK_STAIRS).size() >= 12, "every townhouse has a staircase");
		helper.succeed();
	}

	// ------------------------------------------------------------------ placement

	/** A real piece placed through the pool element, the same code path worldgen uses: the kiosk corner plaza. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aCityPiecePlacesInTheWorld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StructureTemplatePool pool = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).get(id("city/lots/town_small"));
		StructurePoolElement kiosk = null;
		for (StructurePoolElement el : pool.getShuffledTemplates(RandomSource.create(3))) {
			if (el.toString().contains("kiosk_plaza")) {
				kiosk = el;
			}
		}
		helper.assertTrue(kiosk != null, "the kiosk plaza is in the small-lot pool");
		BlockPos at = helper.absolutePos(new BlockPos(0, 1, 0));
		var size = kiosk.getSize(level.getStructureManager(), Rotation.NONE);
		BoundingBox box = kiosk.getBoundingBox(level.getStructureManager(), at, Rotation.NONE);
		kiosk.place(level.getStructureManager(), level, level.structureManager(), level.getChunkSource().getGenerator(), at, at, Rotation.NONE, box,
				level.random, LiquidSettings.IGNORE_WATERLOGGING, false);
		int paved = 0;
		int jigsaws = 0;
		for (BlockPos q : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			var s = level.getBlockState(q);
			if (s.is(Blocks.SMOOTH_STONE) || s.is(Blocks.POLISHED_ANDESITE)) {
				paved++;
			}
			if (s.is(Blocks.JIGSAW)) {
				jigsaws++;
			}
		}
		helper.assertTrue(paved >= 70, "the plaza is paved (" + paved + " of " + size.getX() * size.getZ() + ")");
		helper.assertTrue(jigsaws == 0, "its street jigsaw was replaced by its final state");
		helper.assertTrue(level.getBlockState(at.offset(7, 4, 1)).is(Blocks.LANTERN), "and the lamp stands");
		// tidy
		for (BlockPos q : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY() + 2, box.maxZ())) {
			level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
		}
		helper.succeed();
	}

	/**
	 * The whole City assembled in memory at a far-away chunk (no blocks placed): the jigsaw layout reaches every ring,
	 * all the guaranteed buildings are there, nothing leaves the 128-block jigsaw radius, and it is quick.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void theWholeCityLayoutAssembles(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Structure city = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(CityStructure.KEY);
		ChunkPos at = new ChunkPos(40_000, 40_000);
		// warm-up at another chunk (the first call also loads every template from disk), then time a fresh city
		city.generate(level.registryAccess(), level.getChunkSource().getGenerator(), level.getChunkSource().getGenerator().getBiomeSource(),
				level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), new ChunkPos(30_000, 30_000), 0, level, b -> true);
		long t0 = System.nanoTime();
		StructureStart start = city.generate(level.registryAccess(), level.getChunkSource().getGenerator(), level.getChunkSource().getGenerator().getBiomeSource(),
				level.getChunkSource().randomState(), level.getStructureManager(), level.getSeed(), at, 0, level, b -> true);
		long ms = (System.nanoTime() - t0) / 1_000_000L;
		helper.assertTrue(start.isValid(), "a City assembles on flat ground");
		Map<String, Integer> counts = new HashMap<>();
		Set<String> templates = new HashSet<>();
		int vanilla = 0;
		for (StructurePiece piece : start.getPieces()) {
			if (piece instanceof PoolElementStructurePiece p) {
				String s = p.getElement().toString();
				if (System.getenv("PROJECTHERO_CITY_DEBUG") != null) {
					BoundingBox b = p.getBoundingBox();
					System.out.println("[CITY] piece " + s + " rot " + p.getRotation() + " box " + (b.minX() - at.getMinBlockX()) + "," + b.minY() + ","
							+ (b.minZ() - at.getMinBlockZ()) + " .. " + (b.maxX() - at.getMinBlockX()) + "," + b.maxY() + "," + (b.maxZ() - at.getMinBlockZ()));
				}
				int a = s.indexOf("city/");
				if (a >= 0) {
					String name = s.substring(a + 5, s.indexOf(']', a));
					templates.add(name);
					counts.merge(name, 1, Integer::sum);
				} else if (s.contains("village/")) {
					vanilla++;
				}
			}
		}
		for (String must : new String[] {"street/crossing", "block/civic", "block/medical", "block/news", "block/station", "building/police_precinct",
				"building/city_bank", "building/hospital", "building/office_skyscraper", "building/clock_tower", "building/daily_bulletin",
				"building/train_station", "building/market_square", "building/central_park", "building/maintenance_depot", "building/fire_station",
				"street/junction", "street/ring_corner", "street/ring_6"}) {
			helper.assertTrue(templates.contains(must), "the City has " + must + " (pieces: " + templates + ")");
		}
		helper.assertTrue(counts.getOrDefault("street/junction", 0) == 4 && counts.getOrDefault("street/ring_corner", 0) == 4,
				"four arms, four junctions, four ring corners");
		helper.assertTrue(vanilla >= 10, "the village edge is real vanilla village (" + vanilla + " vanilla pieces)");
		// the start's own box is inflated by 12 for terrain adaptation; the real extent is the union of the pieces
		BoundingBox box = BoundingBox.encapsulatingBoxes(start.getPieces().stream().map(StructurePiece::getBoundingBox).toList()).orElseThrow();
		int cx = at.getMinBlockX();
		int cz = at.getMinBlockZ();
		helper.assertTrue(box.minX() >= cx - 128 && box.maxX() <= cx + 143 && box.minZ() >= cz - 128 && box.maxZ() <= cz + 143,
				"every piece stays inside the 8-chunk reference range of the start chunk (" + box + ")");
		helper.assertTrue(box.getXSpan() >= 200 && box.getZSpan() >= 200, "about 240 x 240 (" + box.getXSpan() + " x " + box.getZSpan() + ")");
		helper.assertTrue(ms < 2000, "the layout assembles quickly (" + ms + " ms)");
		System.out.println("[CITY] layout: " + start.getPieces().size() + " pieces (" + vanilla + " vanilla) in " + ms + " ms, box " + box);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cartographersSellCityMaps(GameTestHelper helper) {
		List<VillagerTrades.ItemListing> found = new ArrayList<>();
		var byLevel = VillagerTrades.TRADES.get(VillagerProfession.CARTOGRAPHER);
		for (VillagerTrades.ItemListing l : byLevel.get(CityWorldgen.CITY_MAP_LEVEL)) {
			if (l instanceof VillagerTrades.TreasureMapForEmeralds) {
				found.add(l);
			}
		}
		helper.assertTrue(found.size() >= 2, "a journeyman cartographer has the woodland map and the City Map (" + found.size() + " explorer maps)");
		helper.succeed();
	}

}
