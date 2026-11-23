package com.projecthero.mod.gametest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.city.CityStructure;
import com.projecthero.mod.loot.HeroVault;
import com.projecthero.mod.loot.LootItems;
import com.projecthero.mod.loot.finds.AbandonedWorkshopPiece;
import com.projecthero.mod.loot.finds.CrashedParademonPodPiece;
import com.projecthero.mod.loot.finds.FindMobs;
import com.projecthero.mod.loot.finds.FindPiece;
import com.projecthero.mod.loot.finds.FindStructure;
import com.projecthero.mod.loot.finds.FindsMod;
import com.projecthero.mod.loot.finds.HeroOutpostPiece;
import com.projecthero.mod.loot.finds.HiddenCachePiece;
import com.projecthero.mod.loot.finds.RooftopStashPiece;
import com.projecthero.mod.loot.finds.RuinedHeroBasePiece;
import com.projecthero.mod.loot.finds.SmugglersDenPiece;
import com.projecthero.mod.loot.finds.SupplyDropEntity;
import com.projecthero.mod.loot.finds.TechMeteorPiece;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.7 small world places (and the Tier 1 finds' new layouts): every place and every layout builds on a stone plinth
 * high above the test area and holds a Tier 1 Hero Vault and its themed container (with the right loot table) where it
 * says; the guarded ones have their mobs; the Smugglers' Den carves into a cliff and its waterfall runs; the Rooftop Stash
 * sits on a roof and a whole City gets one or two; the Supply Drop's dressing is placed and packed away again; the
 * structures, sets and tags load with unique salts. Each test is its own batch (the plinths are wide).
 */
public class WorldPlacesGameTests implements FabricGameTest {
	private static final int LIFT = 40;

	private static BlockPos centre(GameTestHelper helper) {
		return helper.absolutePos(new BlockPos(3, 1, 3)).offset(0, LIFT, 0);
	}

	/** A stone plinth (grass on top at {@code at.y}), radius r, air above. */
	private static void plinth(ServerLevel level, BlockPos at, int r) {
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-r, -9, -r), at.offset(r, 16, r))) {
			level.setBlock(q, (q.getY() > at.getY() ? Blocks.AIR : q.getY() == at.getY() ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState(), 2);
		}
	}

	private static void wipe(ServerLevel level, BlockPos at, int r) {
		for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(at).inflate(r + 3, 20, r + 3), e -> !(e instanceof Player))) {
			e.discard();
		}
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-r, -9, -r), at.offset(r, 16, r))) {
			level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
		}
	}

	private static FindPiece build(GameTestHelper helper, FindStructure.Kind kind, int variant, int layout, int rotation, int r) {
		ServerLevel level = helper.getLevel();
		BlockPos at = centre(helper);
		plinth(level, at, r);
		FindPiece piece = FindStructure.createPiece(kind, at.getX(), at.getY() + 1, at.getZ()).force(variant, layout, rotation);
		piece.build(level, piece.getBoundingBox());
		return piece;
	}

	private static void assertVault(GameTestHelper helper, FindPiece piece, String name) {
		BlockPos v = piece.vaultPos();
		helper.assertTrue(helper.getLevel().getBlockState(v).is(LootItems.HERO_VAULT), name + ": a hero_vault at " + v
				+ " (found " + helper.getLevel().getBlockState(v) + ")");
		helper.assertTrue(HeroVault.tierAt(helper.getLevel(), v) == 1, name + ": the vault is tier 1");
	}

	private static void assertDoorClear(GameTestHelper helper, FindPiece piece, String name) {
		BlockPos v = piece.vaultPos();
		Direction door = helper.getLevel().getBlockState(v).getValue(HorizontalDirectionalBlock.FACING);
		helper.assertTrue(helper.getLevel().getBlockState(v.relative(door)).isAir(), name + ": the vault's door opens onto air");
	}

	private static void assertStash(GameTestHelper helper, FindPiece piece, ResourceKey<LootTable> table, String name) {
		BlockPos s = piece.stashPos();
		helper.assertTrue(s != null && helper.getLevel().getBlockEntity(s) instanceof RandomizableContainerBlockEntity c
				&& table.equals(c.getLootTable()), name + ": its container at " + s + " rolls " + table.location()
						+ " (found " + (s == null ? "none" : helper.getLevel().getBlockState(s)) + ")");
	}

	private static List<Mob> findMobs(ServerLevel level, FindPiece piece) {
		return level.getEntitiesOfClass(Mob.class, AABB.of(piece.getBoundingBox()).inflate(2), m -> m.getTags().contains(FindMobs.TAG));
	}

	// ---------------------------------------------------------------- the six new places

	@GameTest(template = EMPTY_STRUCTURE, batch = "world_places_ruined_base")
	public void ruinedHeroBaseLayoutsHoldVaultCaseNotesAndLocker(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int layout = 0; layout < 3; layout++) {
			RuinedHeroBasePiece piece = (RuinedHeroBasePiece) build(helper, FindStructure.Kind.RUINED_HERO_BASE, layout, layout, layout, 10);
			String name = "ruined hero base layout " + layout;
			assertVault(helper, piece, name);
			assertDoorClear(helper, piece, name);
			assertStash(helper, piece, RuinedHeroBasePiece.LOCKER, name);
			helper.assertTrue(level.getBlockEntity(piece.lecternPos()) instanceof LecternBlockEntity l && l.hasBook(), name + ": the case notes");
			List<ArmorStand> suit = level.getEntitiesOfClass(ArmorStand.class, new AABB(piece.costumePos()).inflate(0.6));
			helper.assertTrue(suit.size() == 1 && !suit.get(0).getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(),
					name + ": the suit stands in its case");
			wipe(level, centre(helper), 10);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "world_places_pod")
	public void parademonPodLayoutsHoldCacheAndTwoParademons(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int layout = 0; layout < 3; layout++) {
			CrashedParademonPodPiece piece = (CrashedParademonPodPiece) build(helper, FindStructure.Kind.PARADEMON_POD, layout, layout, layout, 10);
			String name = "parademon pod layout " + layout;
			assertVault(helper, piece, name);
			assertDoorClear(helper, piece, name);
			assertStash(helper, piece, CrashedParademonPodPiece.CACHE, name);
			List<Mob> mobs = findMobs(level, piece);
			helper.assertTrue(mobs.size() == 2 && mobs.stream().allMatch(m -> m.getType() == com.projecthero.mod.darkseid.entity.DarkseidEntityTypes.PARADEMON)
					&& mobs.stream().allMatch(Mob::isPersistenceRequired), name + ": two persistent Parademons (got " + mobs.size() + ")");
			helper.assertFalse(mobs.stream().anyMatch(m -> m.getTags().contains("projecthero.site_guard")), name + ": not alarm-zone guards");
			wipe(level, centre(helper), 10);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "world_places_workshop")
	public void workshopLayoutsHoldPartsBinVaultAndSuitFrame(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int layout = 0; layout < 3; layout++) {
			AbandonedWorkshopPiece piece = (AbandonedWorkshopPiece) build(helper, FindStructure.Kind.ABANDONED_WORKSHOP, layout, layout, layout, 10);
			String name = "workshop layout " + layout;
			assertVault(helper, piece, name);
			assertDoorClear(helper, piece, name);
			assertStash(helper, piece, AbandonedWorkshopPiece.PARTS, name);
			helper.assertTrue(level.getEntitiesOfClass(ArmorStand.class, new AABB(piece.suitFramePos()).inflate(0.6)).size() == 1,
					name + ": the broken suit frame");
			wipe(level, centre(helper), 10);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "world_places_meteor")
	public void techMeteorLayoutsHoldVaultAndScrap(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int layout = 0; layout < 3; layout++) {
			TechMeteorPiece piece = (TechMeteorPiece) build(helper, FindStructure.Kind.TECH_METEOR, layout, layout, layout, 9);
			String name = "tech meteor layout " + layout;
			assertVault(helper, piece, name);
			assertDoorClear(helper, piece, name);
			assertStash(helper, piece, TechMeteorPiece.SCRAP, name);
			helper.assertTrue(findMobs(level, piece).isEmpty(), name + ": nobody guards it");
			wipe(level, centre(helper), 9);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "world_places_den", timeoutTicks = 60)
	public void smugglersDenCarvesIntoACliff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = centre(helper);
		for (int variant = 0; variant < 3; variant++) {
			int layout = variant % 2;
			plinth(level, at, 13);
			// a cliff: everything from local z = -2 northwards is rock 12 high (rotation 0: local north = world north)
			for (BlockPos q : BlockPos.betweenClosed(at.offset(-13, 1, -13), at.offset(13, 12, -2))) {
				level.setBlock(q, (q.getY() == at.getY() + 12 ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState(), 2);
			}
			SmugglersDenPiece piece = (SmugglersDenPiece) FindStructure.createPiece(FindStructure.Kind.SMUGGLERS_DEN, at.getX(), at.getY() + 1, at.getZ())
					.force(variant, layout, 0);
			piece.measure(level);
			String name = "smugglers den variant " + variant + " layout " + layout;
			helper.assertTrue(piece.faceZ() == -2, name + ": the cliff face is measured at local z -2 (got " + piece.faceZ() + ")");
			piece.build(level, piece.getBoundingBox());
			assertVault(helper, piece, name);
			assertDoorClear(helper, piece, name);
			assertStash(helper, piece, SmugglersDenPiece.CONTRABAND, name);
			helper.assertTrue(level.getBlockState(at.offset(0, 2, -8)).isAir(), name + ": the cave room is carved out");
			helper.assertFalse(level.getBlockState(at.offset(0, 6, -8)).isAir(), name + ": with rock over it");
			List<Mob> mobs = findMobs(level, piece);
			helper.assertTrue(mobs.size() == piece.mobCount(), name + ": " + piece.mobCount() + " smugglers (got " + mobs.size() + ")");
			if (variant == 1) {
				helper.assertTrue(level.getBlockState(at.offset(0, 12, -1)).is(Blocks.WATER), name + ": the waterfall's spout");
				helper.assertTrue(level.getBlockState(at.offset(0, 0, -1)).is(Blocks.WATER), name + ": the pool at its foot");
			}
			wipe(level, at, 13);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "world_places_rooftop")
	public void rooftopStashSitsOnTheRoof(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = centre(helper);
		for (int layout = 0; layout < 3; layout++) {
			// a "building": a box of stone 6 high with a flat roof, and an air vent sticking out of one corner
			for (BlockPos q : BlockPos.betweenClosed(at.offset(-6, -6, -6), at.offset(6, 8, 6))) {
				level.setBlock(q, (q.getY() <= at.getY() ? Blocks.SMOOTH_STONE : Blocks.AIR).defaultBlockState(), 2);
			}
			level.setBlock(at.offset(3, 1, 3), Blocks.IRON_BLOCK.defaultBlockState(), 2);
			RooftopStashPiece piece = new RooftopStashPiece(at.getX(), at.getY() + 1, at.getZ(), at.getY() - 6);
			piece.force(layout, layout, layout);
			piece.build(level, piece.getBoundingBox());
			String name = "rooftop stash layout " + layout;
			helper.assertTrue(piece.roofY() == at.getY(), name + ": found the roof at " + at.getY() + " (got " + piece.roofY() + ")");
			assertVault(helper, piece, name);
			assertStash(helper, piece, RooftopStashPiece.STASH, name);
			helper.assertTrue(piece.vaultPos().getY() == at.getY() + 1, name + ": the vault stands on the roof");
			for (BlockPos q : BlockPos.betweenClosed(at.offset(-6, -6, -6), at.offset(6, 8, 6))) {
				level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "world_places_city", timeoutTicks = 400)
	public void aCityGetsRooftopStashes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Structure city = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(CityStructure.KEY);
		int found = 0;
		for (int i = 0; i < 2; i++) {
			ChunkPos at = i == 0 ? new ChunkPos(40_000, 40_000) : new ChunkPos(30_000, 30_000);
			StructureStart start = city.generate(level.registryAccess(), level.getChunkSource().getGenerator(),
					level.getChunkSource().getGenerator().getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(),
					level.getSeed(), at, 0, level, b -> true);
			if (!start.isValid()) {
				continue;
			}
			int n = 0;
			for (StructurePiece piece : start.getPieces()) {
				if (piece instanceof RooftopStashPiece r) {
					n++;
					int local = Math.floorMod(r.getBoundingBox().minX(), 16);
					helper.assertTrue(local + 6 <= 15 && Math.floorMod(r.getBoundingBox().minZ(), 16) + 6 <= 15,
							"a stash's footprint stays in one chunk (" + r.getBoundingBox() + ")");
				}
			}
			helper.assertTrue(n >= 1 && n <= 2, "a City gets one or two rooftop stashes (got " + n + ")");
			found++;
		}
		helper.assertTrue(found >= 1, "at least one test City assembled");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Tier 1 finds' layouts

	@GameTest(template = EMPTY_STRUCTURE, batch = "world_places_find_layouts")
	public void tierOneFindLayoutsAllHoldTheirVaults(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int layout = 0; layout < 3; layout++) {
			for (int variant = 0; variant < 3; variant++) {
				FindPiece drone = build(helper, FindStructure.Kind.CRASHED_DRONE, variant, layout, variant + layout, 9);
				String n = "crashed drone layout " + layout + " variant " + variant;
				assertVault(helper, drone, n);
				assertDoorClear(helper, drone, n);
				wipe(level, centre(helper), 9);
			}
			HiddenCachePiece cache = (HiddenCachePiece) build(helper, FindStructure.Kind.HIDDEN_CACHE, layout, layout, layout, 9);
			String n = "hidden cache layout " + layout;
			assertVault(helper, cache, n);
			int depth = cache.groundY() - cache.vaultPos().getY();
			helper.assertTrue(depth >= 3 && depth <= 5, n + ": buried 3-5 below the grass (got " + depth + ")");
			helper.assertFalse(level.getBlockState(cache.markerPos()).isAir(), n + ": a marker at " + cache.markerPos());
			helper.assertTrue(level.getBlockState(cache.markerPos().below()).is(Blocks.COARSE_DIRT), n + ": on churned earth");
			wipe(level, centre(helper), 9);

			HeroOutpostPiece outpost = (HeroOutpostPiece) build(helper, FindStructure.Kind.HERO_OUTPOST, layout, layout, layout, 9);
			n = "hero outpost layout " + layout;
			assertVault(helper, outpost, n);
			helper.assertTrue(level.getBlockEntity(outpost.lecternPos()) instanceof LecternBlockEntity l && l.hasBook(), n + ": the field notes");
			List<Mob> mobs = level.getEntitiesOfClass(Mob.class, AABB.of(outpost.getBoundingBox()));
			helper.assertTrue(mobs.size() >= outpost.mobCount(), n + ": its lingering mobs (got " + mobs.size() + ")");
			wipe(level, centre(helper), 9);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "world_places_supply_deco")
	public void supplyDropDressingIsPlacedAndPackedAway(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = centre(helper);
		plinth(level, at, 5);
		BlockPos vault = at.above();
		for (int variant = 0; variant < 3; variant++) {
			SupplyDropEntity crate = SupplyDropEntity.create(level, vault.getX() + 0.5, vault.getY(), vault.getZ() + 0.5, 1, Direction.NORTH);
			level.addFreshEntity(crate);
			HeroVault.place(level, vault, 1, Direction.NORTH);
			crate.decorate(level, vault, variant);
			helper.assertTrue(crate.decoration().size() >= 4, "variant " + variant + ": the parachute and its dressing (" + crate.decoration().size() + " blocks)");
			helper.assertTrue(level.getBlockState(vault.south()).is(Blocks.RED_CARPET) || level.getBlockState(vault.south()).is(Blocks.WHITE_CARPET),
					"variant " + variant + ": the canopy lies behind the crate");
			List<BlockPos> placed = List.copyOf(crate.decoration());
			crate.packUp(level);
			helper.assertTrue(placed.stream().allMatch(p -> level.getBlockState(p).isAir()), "variant " + variant + ": packed away with the crate");
			helper.assertTrue(level.getBlockState(vault).isAir(), "variant " + variant + ": the vault goes too");
		}
		wipe(level, at, 5);
		helper.succeed();
	}

	// ---------------------------------------------------------------- data

	@GameTest(template = EMPTY_STRUCTURE, batch = "world_places_data")
	public void placesLoadWithUniqueSalts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
		for (ResourceKey<Structure> k : List.of(FindsMod.RUINED_HERO_BASE, FindsMod.PARADEMON_POD, FindsMod.ABANDONED_WORKSHOP,
				FindsMod.SMUGGLERS_DEN, FindsMod.TECH_METEOR)) {
			Structure s = structures.get(k);
			helper.assertTrue(s instanceof FindStructure, k.location() + " loads as a Find structure");
			helper.assertTrue(s.biomes().size() > 3, k.location() + " has biomes (" + s.biomes().size() + ")");
		}
		// every projecthero structure set's salt, straight from the data files (the placement's salt() is protected)
		Map<Integer, String> salts = new HashMap<>();
		var rm = level.getServer().getResourceManager();
		for (var e : rm.listResources("worldgen/structure_set", id -> id.getNamespace().equals("projecthero") && id.getPath().endsWith(".json")).entrySet()) {
			try (var reader = e.getValue().openAsReader()) {
				var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("placement");
				if (json != null && json.has("salt")) {
					int salt = json.get("salt").getAsInt();
					String id = e.getKey().getPath().replace("worldgen/structure_set/", "").replace(".json", "");
					String other = salts.put(salt, id);
					helper.assertTrue(other == null, "salt " + salt + " is used by both " + other + " and " + id);
				}
			} catch (java.io.IOException ex) {
				throw new RuntimeException(ex);
			}
		}
		Registry<StructureSet> sets = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
		for (String id : new String[] { "ruined_hero_base", "crashed_parademon_pod", "abandoned_workshop", "smugglers_den", "tech_meteor" }) {
			helper.assertTrue(salts.containsValue(id) && sets.containsKey(com.projecthero.mod.ProjectHeroMod.id(id)), "the " + id + " structure set loads");
		}
		helper.succeed();
	}
}
