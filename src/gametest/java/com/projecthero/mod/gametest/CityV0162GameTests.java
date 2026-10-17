package com.projecthero.mod.gametest;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.city.CityArea;
import com.projecthero.mod.city.CityBlocks;
import com.projecthero.mod.city.CityBlueprint;
import com.projecthero.mod.city.CityConfig;
import com.projecthero.mod.city.CityCops;
import com.projecthero.mod.city.CityDamageTracker;
import com.projecthero.mod.city.CityLocator;
import com.projecthero.mod.city.CityRepair;
import com.projecthero.mod.city.CitySavedData;
import com.projecthero.mod.city.CityServices;
import com.projecthero.mod.city.DailyBulletin;
import com.projecthero.mod.city.bank.BankAccounts;
import com.projecthero.mod.city.bank.SafeDepositBlock;
import com.projecthero.mod.city.bank.SafeDepositMenu;
import com.projecthero.mod.crime.CrimeEvent;
import com.projecthero.mod.crime.CrimeType;
import com.projecthero.mod.crime.Crimes;
import com.projecthero.mod.event.EventSavedData;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2 City part B. Cities here are hand-built: a template is captured from blocks the test places
 * ({@code fillFromWorld}), wrapped in a real {@link PoolElementStructurePiece} and registered with
 * {@link CityLocator#register}, so nothing depends on City part A's worldgen. Tests that need quiet surroundings (no
 * monsters, no crimes) run in their own batches.
 */
public class CityV0162GameTests implements FabricGameTest {

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
	}

	/** Captures the blocks in {@code size} at relative {@code corner} into a fresh template and returns a piece of it. */
	private static PoolElementStructurePiece piece(GameTestHelper helper, String name, BlockPos corner, Vec3i size, Rotation rotation,
			BlockPos placeAt) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("projecthero", "gametest/city_" + name + "_" + UUID.randomUUID().toString().substring(0, 8));
		StructureTemplate template = level.getStructureManager().getOrCreate(id);
		template.fillFromWorld(level, helper.absolutePos(corner), size, false, Blocks.STRUCTURE_VOID);
		SinglePoolElement element = StructurePoolElement.single(id.toString()).apply(StructureTemplatePool.Projection.RIGID);
		BlockPos at = helper.absolutePos(placeAt);
		BoundingBox box = element.getBoundingBox(level.getStructureManager(), at, rotation);
		return new PoolElementStructurePiece(level.getStructureManager(), element, at, 0, rotation, box, LiquidSettings.IGNORE_WATERLOGGING);
	}

	private static CityArea city(GameTestHelper helper, StructurePiece... pieces) {
		return CityLocator.register(helper.getLevel(), List.of(pieces));
	}

	private static void forget(GameTestHelper helper, CityArea city) {
		CityLocator.unregister(helper.getLevel(), city);
		CitySavedData.get(helper.getLevel()).forget(city.id());
	}

	private static Villager villager(GameTestHelper helper, VillagerProfession profession, BlockPos rel, boolean ai) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.moveTo(helper.absoluteVec(Vec3.atBottomCenterOf(rel)), 0f, 0f);
		v.setVillagerData(v.getVillagerData().setProfession(profession).setLevel(1));
		v.setVillagerXp(1); // keeps the profession without a job site
		v.setNoAi(!ai);
		helper.getLevel().addFreshEntity(v);
		v.refreshBrain(helper.getLevel()); // the brain is built for a profession: rebuild it for the new one
		return v;
	}

	private static CityConfig testConfig() {
		CityConfig cfg = CityConfig.fresh();
		cfg.repair.repairDelayTicks = 20;
		cfg.repair.workHoursOnly = false;
		cfg.repair.ticksPerBlock = 2;
		cfg.repair.reachTimeoutTicks = 160;
		return cfg;
	}

	// ---------------------------------------------------------------- config

	@GameTest(template = EMPTY_STRUCTURE)
	public void cityConfigDefaults(GameTestHelper helper) {
		CityConfig c = CityConfig.fresh();
		helper.assertTrue(c.configVersion != null && c.configVersion == CityConfig.SPEC.currentVersion(), "fresh config is stamped");
		helper.assertTrue(c.repair.repairDelayTicks == 12000, "10 in-game minutes before a repair");
		helper.assertTrue(c.repair.blocksPerTrip == 512 && c.repair.ticksPerBlock == 10, "512 blocks a trip, one per 10 ticks");
		helper.assertTrue(c.bank.interestPercentPerDay == 1.0 && c.bank.interestCapEmeralds == 64, "1%/day, capped at +64");
		helper.assertTrue(c.cops.crimeEngageDelaySeconds == 20 && c.cops.crookDamageMultiplier < 1f, "cops leave crimes to players first");
		CityConfig edited = CityConfig.SPEC.fromJson("{\"repair\": {\"enabled\": false}}");
		helper.assertFalse(edited.repair.enabled, "the server's repair switch survives a load");
		helper.assertTrue(edited.cops.attackDamage == c.cops.attackDamage, "missing sections get today's defaults");
		helper.succeed();
	}

	// ---------------------------------------------------------------- blueprint

	@GameTest(template = EMPTY_STRUCTURE)
	public void cityBlueprintResolvesPlacedPieces(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// an asymmetric 3x2x3 sample: rotation-sensitive states (stairs, a chest) and plain blocks
		helper.setBlock(new BlockPos(0, 1, 0), Blocks.STONE_BRICKS);
		helper.setBlock(new BlockPos(2, 1, 0), Blocks.GLASS);
		helper.setBlock(new BlockPos(0, 1, 2), Blocks.OAK_PLANKS);
		helper.setBlock(new BlockPos(1, 1, 1), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.NORTH));
		helper.setBlock(new BlockPos(0, 2, 0), Blocks.LANTERN);
		PoolElementStructurePiece rigid = piece(helper, "sample", new BlockPos(0, 1, 0), new Vec3i(3, 2, 3), Rotation.NONE, new BlockPos(0, 1, 0));
		int checked = 0;
		for (Rotation rot : new Rotation[]{Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90}) {
			PoolElementStructurePiece p = new PoolElementStructurePiece(level.getStructureManager(), rigid.getElement(), helper.absolutePos(new BlockPos(4, 3, 4)),
					0, rot, rigid.getElement().getBoundingBox(level.getStructureManager(), helper.absolutePos(new BlockPos(4, 3, 4)), rot),
					LiquidSettings.IGNORE_WATERLOGGING);
			p.place(level, level.structureManager(), level.getChunkSource().getGenerator(), level.random, BoundingBox.infinite(), p.getPosition(), false);
			int found = 0;
			for (BlockPos pos : BlockPos.betweenClosed(p.getBoundingBox().minX(), p.getBoundingBox().minY(), p.getBoundingBox().minZ(),
					p.getBoundingBox().maxX(), p.getBoundingBox().maxY(), p.getBoundingBox().maxZ())) {
				BlockState expected = CityBlueprint.expectedIn(level, p, pos);
				BlockState actual = level.getBlockState(pos);
				if (expected != null) {
					found++;
					helper.assertTrue(expected == actual, rot + ": blueprint says " + expected + " at " + pos + " but the placed piece has " + actual);
				} else {
					helper.assertTrue(actual.isAir(), rot + ": blueprint has nothing at " + pos + " but the piece placed " + actual);
				}
			}
			helper.assertTrue(found == 6, rot + ": six blocks in the blueprint, found " + found);
			// the City-level lookup agrees
			CityArea area = city(helper, p);
			helper.assertTrue(CityLocator.cityAt(level, p.getPosition()) == area, "the registered City is found at its piece");
			helper.assertTrue(CityBlueprint.expectedAt(level, area, p.getPosition()) == CityBlueprint.expectedIn(level, p, p.getPosition()),
					"expectedAt reads the City's pieces");
			forget(helper, area);
			for (BlockPos pos : BlockPos.betweenClosed(p.getBoundingBox().minX(), p.getBoundingBox().minY(), p.getBoundingBox().minZ(),
					p.getBoundingBox().maxX(), p.getBoundingBox().maxY(), p.getBoundingBox().maxZ())) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
			}
			checked++;
		}
		helper.assertTrue(checked == 4, "all four rotations checked");
		helper.succeed();
	}

	// ---------------------------------------------------------------- repair

	@GameTest(template = EMPTY_STRUCTURE, batch = "city_repair", timeoutTicks = 900)
	public void cityRepairWorkerRebuildsButNeverOverwrites(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CityConfig before = CityConfig.get();
		CityConfig.setForTest(testConfig());
		floor(helper);
		for (int x = 0; x < 8; x++) {
			for (int y = 2; y < 5; y++) {
				helper.setBlock(new BlockPos(x, y, 6), Blocks.STONE_BRICKS);
			}
		}
		CityArea city = city(helper, piece(helper, "wall", new BlockPos(0, 2, 6), new Vec3i(8, 3, 1), Rotation.NONE, new BlockPos(0, 2, 6)));
		CitySavedData data = CitySavedData.get(level);
		BlockPos a = new BlockPos(0, 2, 6), b = new BlockPos(1, 2, 6), c = new BlockPos(1, 4, 6), own = new BlockPos(6, 3, 6);
		helper.setBlock(a, Blocks.AIR);
		helper.setBlock(b, Blocks.AIR);
		helper.setBlock(c, Blocks.FIRE); // fire damage counts too
		helper.setBlock(own, Blocks.AIR);
		CityDamageTracker.flush(level);
		// a player puts their own block in a damaged slot: that cell is theirs now
		CityDamageTracker.playerPlacing(true);
		helper.setBlock(own, Blocks.OAK_PLANKS);
		CityDamageTracker.playerPlacing(false);
		CityDamageTracker.flush(level);
		for (BlockPos p : new BlockPos[]{a, b, c}) {
			helper.assertTrue(data.isQueued(city.id(), helper.absolutePos(p)), "damage at " + p + " is queued");
		}
		helper.assertFalse(data.isQueued(city.id(), helper.absolutePos(own)), "a player's block drops the slot from the queue");
		helper.assertTrue(data.isOptedOut(city.id(), helper.absolutePos(own)), "and opts the cell out (a renovation)");
		Villager worker = villager(helper, CityBlocks.REPAIR_WORKER, new BlockPos(4, 2, 2), true);
		helper.succeedWhen(() -> {
			for (BlockPos p : new BlockPos[]{a, b, c}) {
				helper.assertBlockPresent(Blocks.STONE_BRICKS, p);
			}
			helper.assertBlockPresent(Blocks.OAK_PLANKS, own); // never overwritten
			helper.assertTrue(CityRepair.suppressDrop(level, helper.absolutePos(a)), "a rebuilt block drops nothing for a day");
			helper.assertTrue(CityRepair.job(level, city.id()) == null, "the trip is over");
			helper.assertFalse(worker.isNoAi(), "the worker has its AI back");
			forget(helper, city);
			CityConfig.setForTest(before);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "city_repair", timeoutTicks = 900)
	public void cityRepairClimbsScaffoldingForHighBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CityConfig before = CityConfig.get();
		CityConfig.setForTest(testConfig());
		floor(helper);
		for (int y = 2; y < 11; y++) {
			helper.setBlock(new BlockPos(4, y, 6), Blocks.STONE_BRICKS);
		}
		CityArea city = city(helper, piece(helper, "pillar", new BlockPos(4, 2, 6), new Vec3i(1, 9, 1), Rotation.NONE, new BlockPos(4, 2, 6)));
		BlockPos top = new BlockPos(4, 10, 6);
		helper.setBlock(top, Blocks.AIR);
		CityDamageTracker.flush(level);
		villager(helper, CityBlocks.REPAIR_WORKER, new BlockPos(4, 2, 2), true);
		int[] maxScaffold = {0};
		helper.onEachTick(() -> {
			CityRepair.Job job = CityRepair.job(level, city.id());
			if (job != null) {
				maxScaffold[0] = Math.max(maxScaffold[0], job.scaffoldsPlaced());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.STONE_BRICKS, top);
			helper.assertTrue(CityRepair.job(level, city.id()) == null, "the trip is over");
			helper.assertTrue(maxScaffold[0] > 0, "the worker climbed a scaffold to reach it");
			for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(7, 12, 7)))) {
				helper.assertFalse(level.getBlockState(p).is(Blocks.SCAFFOLDING), "the scaffolding came down again (" + p + ")");
			}
			forget(helper, city);
			CityConfig.setForTest(before);
		});
	}

	/** v0.16.2 (user): a road hole is filled from its rim -- the worker never ends up in it, and a villager down there is lifted out. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "city_repair", timeoutTicks = 900)
	public void cityRepairFillsAHoleWithoutTrappingAnyone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CityConfig before = CityConfig.get();
		CityConfig.setForTest(testConfig());
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				for (int y = 1; y <= 3; y++) {
					helper.setBlock(new BlockPos(x, y, z), y == 3 ? Blocks.GRAY_CONCRETE : Blocks.STONE);
				}
			}
		}
		CityArea city = city(helper, piece(helper, "road", new BlockPos(0, 1, 0), new Vec3i(8, 3, 8), Rotation.NONE, new BlockPos(0, 1, 0)));
		List<BlockPos> hole = new java.util.ArrayList<>();
		for (int x = 3; x <= 4; x++) {
			for (int z = 3; z <= 4; z++) {
				for (int y = 1; y <= 3; y++) {
					BlockPos p = new BlockPos(x, y, z);
					helper.setBlock(p, Blocks.AIR);
					hole.add(p);
				}
			}
		}
		CityDamageTracker.flush(level);
		Villager stuck = villager(helper, VillagerProfession.NONE, new BlockPos(3, 1, 3), false); // fell in
		Villager worker = villager(helper, CityBlocks.REPAIR_WORKER, new BlockPos(0, 4, 0), true);
		int surface = helper.absolutePos(new BlockPos(0, 4, 0)).getY();
		helper.onEachTick(() -> {
			if (CityRepair.job(level, city.id()) != null) {
				helper.assertFalse(level.getBlockState(worker.blockPosition()).isCollisionShapeFullBlock(level, worker.blockPosition()),
						"the worker is never built into a block");
			}
		});
		helper.succeedWhen(() -> {
			for (BlockPos p : hole) {
				helper.assertTrue(level.getBlockState(helper.absolutePos(p)).is(p.getY() == 3 ? Blocks.GRAY_CONCRETE : Blocks.STONE),
						"hole block " + p + " not refilled; stuck villager at " + helper.relativePos(stuck.blockPosition()) + " worker at " + helper.relativePos(worker.blockPosition()) + " job=" + (CityRepair.job(level, city.id()) != null));
			}
			helper.assertTrue(CityRepair.job(level, city.id()) == null, "the trip is over");
			for (Villager v : new Villager[]{stuck, worker}) {
				helper.assertTrue(v.getY() >= surface - 0.01, v.getVillagerData().getProfession() + " ends up on the road, not sealed under it (y=" + v.getY() + ")");
				helper.assertTrue(level.getBlockState(v.blockPosition()).getCollisionShape(level, v.blockPosition()).isEmpty(), "and not inside a block");
			}
			stuck.discard();
			forget(helper, city);
			CityConfig.setForTest(before);
		});
	}

	// ---------------------------------------------------------------- food bank

	private static com.projecthero.mod.city.FoodBankBlockEntity foodBank(GameTestHelper helper, BlockPos rel) {
		helper.setBlock(rel, CityBlocks.FOOD_BANK);
		return (com.projecthero.mod.city.FoodBankBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
	}

	/** v0.16.2 (user): food goes in -- safe food only -- and a villager with spare food hands it over. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "city_food_bank", timeoutTicks = 300)
	public void cityFoodBankTakesFoodFromPlayersAndVillagers(GameTestHelper helper) {
		floor(helper);
		var bank = foodBank(helper, new BlockPos(3, 2, 3));
		helper.assertTrue(bank.deposit(new ItemStack(net.minecraft.world.item.Items.BREAD, 2), true) == 10, "two loaves are 10 food");
		helper.assertTrue(com.projecthero.mod.city.FoodBankBlockEntity.value(new ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH)) == 0,
				"rotten flesh is refused");
		bank.setFoodForTest(0);
		Villager farmer = villager(helper, VillagerProfession.FARMER, new BlockPos(4, 2, 3), false);
		farmer.getInventory().addItem(new ItemStack(net.minecraft.world.item.Items.BREAD, 10));
		helper.succeedWhen(() -> {
			helper.assertTrue(bank.food() == 35, "the farmer handed over 7 loaves (food " + bank.food() + ")");
			helper.assertTrue(farmer.getInventory().countItem(net.minecraft.world.item.Items.BREAD) == 3, "and kept 3 for itself");
			farmer.discard();
		});
	}

	/** v0.16.2 (user): 60 food and a free bed become a newborn villager. */
	@GameTest(template = EMPTY_STRUCTURE, batch = "city_food_bank_birth", timeoutTicks = 300)
	public void cityFoodBankTurnsFoodIntoANewcomer(GameTestHelper helper) {
		floor(helper);
		var bank = foodBank(helper, new BlockPos(3, 2, 3));
		helper.assertTrue(bank.roomForNewcomers(helper.getLevel()) == 0, "no bed, no newcomer");
		helper.setBlock(new BlockPos(6, 2, 6), Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.PART,
				net.minecraft.world.level.block.state.properties.BedPart.HEAD));
		bank.setFoodForTest(70);
		helper.succeedWhen(() -> {
			List<Villager> babies = helper.getLevel().getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(3, 2, 3))).inflate(4), Villager::isBaby);
			helper.assertTrue(babies.size() == 1, "a newborn villager next to the bank");
			helper.assertTrue(bank.food() == 10, "60 food spent (" + bank.food() + " left)");
			babies.forEach(Villager::discard);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "city_repair")
	public void cityDepotHiresAWorker(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		helper.setBlock(new BlockPos(3, 2, 3), CityBlocks.REPAIR_BENCH);
		CityArea city = city(helper, piece(helper, "maintenance_depot", new BlockPos(0, 1, 0), new Vec3i(8, 4, 8), Rotation.NONE, new BlockPos(0, 1, 0)));
		Villager hired = CityRepair.hireAt(level, city);
		helper.assertTrue(hired != null, "the depot hires a worker at its free Repair Bench");
		helper.assertTrue(hired.getVillagerData().getProfession() == CityBlocks.REPAIR_WORKER, "as a Repair Worker");
		helper.assertTrue(hired.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null)
				.equals(helper.absolutePos(new BlockPos(3, 2, 3))), "with that bench as its job site");
		helper.assertTrue(CityRepair.hireAt(level, city) == null, "a bench takes one worker");
		hired.discard();
		forget(helper, city);
		helper.succeed();
	}

	// ---------------------------------------------------------------- cops

	// its own batch: a villager from a neighbouring test in the same batch could claim this desk first (seen on a full run)
	@GameTest(template = EMPTY_STRUCTURE, batch = "city_cop_claim_solo", timeoutTicks = 1600)
	public void cityCopClaimsThePrecinctDesk(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(5, 2, 5), CityBlocks.PRECINCT_DESK);
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.moveTo(helper.absoluteVec(new Vec3(2.5, 2, 2.5)), 0f, 0f);
		helper.getLevel().addFreshEntity(v);
		helper.succeedWhen(() -> {
			helper.assertTrue(v.getVillagerData().getProfession() == CityBlocks.CITY_COP,
					"an unemployed villager takes the Precinct Desk and becomes a City Cop (is " + v.getVillagerData().getProfession() + ")");
			helper.assertTrue(v.getMaxHealth() >= 40.0f, "a cop has 40 health");
			v.discard();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "city_cop", timeoutTicks = 400)
	public void cityCopFightsZombiesNotPlayers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		Villager cop = villager(helper, CityBlocks.CITY_COP, new BlockPos(2, 2, 4), true);
		Zombie zombie = EntityType.ZOMBIE.create(level);
		zombie.moveTo(helper.absoluteVec(new Vec3(5.5, 2, 4.5)), 0f, 0f);
		zombie.setNoAi(true);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // no sunburn
		level.addFreshEntity(zombie);
		ServerPlayer player = survivalPlayer(helper, new Vec3(2.5, 2, 1.5));
		float playerHealth = player.getHealth();
		float zombieHealth = zombie.getHealth();
		boolean[] hit = {false};
		helper.succeedWhen(() -> {
			if (!hit[0]) {
				helper.assertTrue(cop.getTarget() != player, "the cop never targets an innocent player");
				helper.assertTrue(player.getHealth() >= playerHealth, "the player was not hit");
				helper.assertTrue(!zombie.isAlive() || zombie.getHealth() < zombieHealth, "the cop hit the zombie");
				// hit the cop, and the player becomes fair game
				hit[0] = true;
				cop.invulnerableTime = 0;
				cop.hurt(level.damageSources().playerAttack(player), 1.0f);
			}
			helper.assertTrue(CityCops.findTarget(cop, level, CityConfig.cops()) == player, "a player who hits a cop is fought back");
			zombie.discard();
			cop.discard();
			level.getServer().getPlayerList().remove(player);
		});
	}

	/** A real survival player (the gametest mock player always counts as creative). */
	private static ServerPlayer survivalPlayer(GameTestHelper helper, Vec3 rel) {
		ServerLevel level = helper.getLevel();
		com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "city-test");
		net.minecraft.server.network.CommonListenerCookie cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
		ServerPlayer p = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
		net.minecraft.network.Connection conn = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(conn);
		level.getServer().getPlayerList().placeNewPlayer(conn, p, cookie);
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(rel);
		p.teleportTo(level, at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	// ---------------------------------------------------------------- bank

	@GameTest(template = EMPTY_STRUCTURE)
	public void cityVaultDepositWithdrawAndInterest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BankAccounts bank = BankAccounts.get(level.getServer());
		BankAccounts.Account acc = bank.account(UUID.randomUUID());
		acc.items.addItem(new ItemStack(Items.EMERALD, 64));
		acc.items.addItem(new ItemStack(Items.EMERALD_BLOCK, 4));
		helper.assertTrue(acc.value() == 100, "64 emeralds + 4 blocks are worth 100");
		long day = 1000;
		helper.assertTrue(bank.applyInterest(acc, day) == 0, "the first look only starts the clock");
		helper.assertTrue(bank.applyInterest(acc, day + 1) == 1, "1% of 100 after a day");
		helper.assertTrue(acc.value() == 101, "the interest is in the vault");
		helper.assertTrue(bank.applyInterest(acc, day + 3) == 2, "two more days, two more emeralds");
		acc.interestPaid = 63;
		helper.assertTrue(bank.applyInterest(acc, day + 30) == 1, "the cap (64) stops it");
		helper.assertTrue(bank.applyInterest(acc, day + 60) == 0 && acc.interestPaid == 64, "and nothing more is ever paid");
		// the menu: only emeralds go in; withdraw by shift-click
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		SimpleContainer vault = acc.items;
		SafeDepositMenu menu = new SafeDepositMenu(1, player.getInventory(), vault, new SimpleContainerData(3), null);
		helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)), "dirt doesn't go in a vault");
		helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(Items.EMERALD_BLOCK)), "emerald blocks do");
		int before = acc.value();
		menu.quickMoveStack(player, 0); // the first vault slot (64 emeralds) to the player
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 64, "withdrawn to the inventory");
		helper.assertTrue(acc.value() == before - 64, "and gone from the vault");
		player.getInventory().setItem(0, new ItemStack(Items.DIRT, 5));
		int dirtSlot = SafeDepositMenu.ROWS * 9 + 27; // hotbar slot 0
		menu.quickMoveStack(player, dirtSlot);
		helper.assertTrue(BankAccounts.value(vault) == before - 64 && player.getInventory().getItem(0).is(Items.DIRT), "dirt is refused");
		int emeraldSlot = -1;
		for (int i = SafeDepositMenu.ROWS * 9; i < menu.slots.size(); i++) {
			if (menu.getSlot(i).getItem().is(Items.EMERALD)) {
				emeraldSlot = i;
			}
		}
		menu.quickMoveStack(player, emeraldSlot);
		helper.assertTrue(acc.value() == before, "deposited back");
		// opening the real block screen
		SafeDepositBlock.open(player, helper.absolutePos(new BlockPos(1, 1, 1)));
		helper.assertTrue(player.containerMenu instanceof SafeDepositMenu, "the Safe Deposit opens the vault menu");
		player.closeContainer();
		helper.succeed();
	}

	// ---------------------------------------------------------------- doctor + firefighter

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void cityDoctorHeals(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		BlockPos stand = new BlockPos(1, 2, 1);
		helper.setBlock(stand, Blocks.BREWING_STAND);
		CityArea city = city(helper, piece(helper, "hospital_ward", new BlockPos(0, 1, 0), new Vec3i(8, 4, 8), Rotation.NONE, new BlockPos(0, 1, 0)));
		Villager doctor = villager(helper, VillagerProfession.CLERIC, new BlockPos(2, 2, 2), true);
		BlockPos abs = helper.absolutePos(stand);
		level.getPoiManager().take(h -> h.is(PoiTypes.CLERIC), (h, p) -> p.equals(abs), abs, 1);
		doctor.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), abs));
		Villager patient = villager(helper, VillagerProfession.FARMER, new BlockPos(5, 2, 5), false);
		patient.setHealth(5.0f);
		helper.succeedWhen(() -> {
			helper.assertTrue(CityServices.isDoctor(doctor, level), "a cleric working in the hospital is a doctor");
			helper.assertTrue(patient.hasEffect(MobEffects.REGENERATION), "the ward gives Regeneration");
			helper.assertTrue(patient.getHealth() > 5.0f, "the patient heals");
			doctor.discard();
			patient.discard();
			forget(helper, city);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void cityFirefighterPutsOutFires(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		BlockPos cauldron = new BlockPos(1, 2, 1);
		helper.setBlock(cauldron, Blocks.WATER_CAULDRON);
		CityArea city = city(helper, piece(helper, "fire_station", new BlockPos(0, 1, 0), new Vec3i(8, 4, 8), Rotation.NONE, new BlockPos(0, 1, 0)));
		Villager ff = villager(helper, VillagerProfession.LEATHERWORKER, new BlockPos(2, 2, 2), true);
		BlockPos abs = helper.absolutePos(cauldron);
		level.getPoiManager().take(h -> h.is(PoiTypes.LEATHERWORKER), (h, p) -> p.equals(abs), abs, 1);
		ff.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), abs));
		BlockPos fire = new BlockPos(6, 2, 6);
		helper.setBlock(fire.below(), Blocks.NETHERRACK); // burns forever on its own
		helper.setBlock(fire, Blocks.FIRE);
		helper.succeedWhen(() -> {
			helper.assertTrue(CityServices.isFirefighter(ff, level), "a leatherworker in the fire station is a firefighter");
			helper.assertBlockPresent(Blocks.AIR, fire);
			ff.discard();
			forget(helper, city);
		});
	}

	// ---------------------------------------------------------------- newspaper

	@GameTest(template = EMPTY_STRUCTURE, batch = "city_bulletin")
	public void cityBulletinListsActiveCrime(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		BlockPos scene = helper.absolutePos(new BlockPos(4, 2, 4));
		CrimeEvent crime = Crimes.start(level, CrimeType.ARSON, scene, false, false);
		helper.assertTrue(crime != null, "an arson crime started");
		try {
			BlockPos reader = scene.offset(-120, 0, 30);
			List<DailyBulletin.Report> reports = DailyBulletin.reports(level, reader, 1024);
			DailyBulletin.Report found = null;
			for (DailyBulletin.Report r : reports) {
				if (r.crime()) {
					found = r;
				}
			}
			helper.assertTrue(found != null, "the newspaper lists the running crime");
			helper.assertTrue(Math.abs(found.distance() - Math.sqrt(120 * 120 + 30 * 30)) < 2.0, "with its distance");
			helper.assertTrue(found.hint().getString().length() > 0, "and a direction");
			helper.assertTrue(DailyBulletin.pages(level, reader).size() >= 2, "front page + the story");
			// reading the item fills in the book screen's pages
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.moveTo(Vec3.atBottomCenterOf(reader));
			ItemStack paper = new ItemStack(CityBlocks.DAILY_BULLETIN);
			player.setItemInHand(InteractionHand.MAIN_HAND, paper);
			paper.use(level, player, InteractionHand.MAIN_HAND);
			var content = player.getItemInHand(InteractionHand.MAIN_HAND).get(DataComponents.WRITTEN_BOOK_CONTENT);
			helper.assertTrue(content != null && content.pages().size() >= 2, "the Daily Bulletin prints today's pages when read");
			// and the city services see the newspaper office's librarian
			Villager lib = villager(helper, VillagerProfession.LIBRARIAN, new BlockPos(1, 2, 1), false);
			CityServices.ensureBulletinTrade(lib);
			helper.assertTrue(lib.getOffers().stream().anyMatch(o -> o.getResult().is(CityBlocks.DAILY_BULLETIN)), "the librarian sells it");
			lib.discard();
		} finally {
			crime.abort(level);
			EventSavedData.get(level).remove(crime.id());
		}
		helper.succeed();
	}
}
