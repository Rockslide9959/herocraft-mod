package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p05.GeoRockEntity;
import com.projecthero.mod.hero.power.p05.GeokinesisHandlers;
import com.projecthero.mod.hero.power.p05.GroundType;
import com.projecthero.mod.hero.power.p06.CrystalNodeEntity;
import com.projecthero.mod.hero.power.p08.PyrokinesisHandlers;
import com.projecthero.mod.hero.power.p09.FrostStacks;
import com.projecthero.mod.hero.power.p09.IceBladeItem;
import com.projecthero.mod.hero.power.p25.WaterHandlers;
import com.projecthero.mod.hero.revamp.BatchBScheduler;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 mutation revamp, batch B: regression coverage for the reworked Geokinesis, Crystalkinesis, Pyrokinesis,
 * Cryokinesis and Water Manipulation kits -- each power's signature mechanic, its H / N abilities and the entities,
 * items and temporary blocks they create. Mock players are not reliably ticked, so tests drive
 * {@link ExperimentalPowers#serverTick} themselves where a per-tick effect matters; world-level effects
 * (the batch scheduler, crystal nodes) tick with the server.
 */
public class RevampBatchBGameTests implements FabricGameTest {
	private static final String GEO = "power_05_geokinesis";
	private static final String CRYSTAL = "power_06_crystalkinesis";
	private static final String PYRO = "power_08_pyrokinesis";
	private static final String CRYO = "power_09_cryokinesis";
	private static final String WATER = "power_25_water_manipulation";

	/** A survival mock player at (2.5, 2, 2.5) facing +Z, in a cleared room on a floor of {@code floor}. */
	private static ServerPlayer hero(GameTestHelper helper, String key, BlockState floor) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYRot(0.0f);
		p.setXRot(0.0f);
		BlockPos base = BlockPos.containing(at);
		ServerLevel level = helper.getLevel();
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, -2), base.offset(2, 4, 8))) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(2, -1, 8))) {
			level.setBlock(pos, floor, 2);
		}
		Power power = Powers.byKey(key);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		return p;
	}

	private static ServerPlayer hero(GameTestHelper helper, String key) {
		return hero(helper, key, Blocks.STONE.defaultBlockState());
	}

	private static Zombie zombieAhead(GameTestHelper helper, ServerPlayer p, int dist) {
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2 + dist));
		p.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		return z;
	}

	private static float res(ServerPlayer p, String key, String name) {
		return ExperimentalPowers.getResource(p, Powers.byKey(key), name);
	}

	// ---------------- kit shape ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void batchPowersRegistered(GameTestHelper helper) {
		helper.assertTrue(Powers.count() == 27, "expected 27 powers");
		for (String key : new String[] { GEO, CRYSTAL, PYRO, CRYO, WATER }) {
			Power p = Powers.byKey(key);
			helper.assertTrue(p.abilities().size() == 8, key + " must define 8 abilities");
			for (AbilitySlot slot : AbilitySlot.values()) {
				Ability a = p.ability(slot);
				helper.assertTrue(a != null && a.slot() == slot, key + " slot " + slot + " mismapped");
				helper.assertTrue(AbilityHandlers.has(p, a), key + "/" + a.id() + " has no handler");
			}
		}
		helper.succeed();
	}

	// ---------------- 05 Geokinesis ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void groundTypesClassifyTheirMaterials(GameTestHelper helper) {
		helper.assertTrue(GroundType.of(Blocks.STONE.defaultBlockState()) == GroundType.STONE, "stone");
		helper.assertTrue(GroundType.of(Blocks.GRASS_BLOCK.defaultBlockState()) == GroundType.STONE, "dirt counts as plain earth");
		helper.assertTrue(GroundType.of(Blocks.DEEPSLATE.defaultBlockState()) == GroundType.DEEPSLATE, "deepslate");
		helper.assertTrue(GroundType.of(Blocks.SAND.defaultBlockState()) == GroundType.SAND, "sand");
		helper.assertTrue(GroundType.of(Blocks.GRAVEL.defaultBlockState()) == GroundType.SAND, "gravel");
		helper.assertTrue(GroundType.of(Blocks.NETHERRACK.defaultBlockState()) == GroundType.NETHER, "netherrack");
		helper.assertTrue(GroundType.of(Blocks.MAGMA_BLOCK.defaultBlockState()) == GroundType.NETHER, "magma");
		helper.assertTrue(GroundType.of(Blocks.IRON_ORE.defaultBlockState()) == GroundType.ORE, "ore");
		helper.assertTrue(GroundType.of(Blocks.PACKED_ICE.defaultBlockState()) == GroundType.FROST, "ice");
		helper.assertTrue(GroundType.of(Blocks.OAK_PLANKS.defaultBlockState()) == GroundType.NONE, "wood is not earth");
		helper.assertTrue(GroundType.DEEPSLATE.damageMult > 1.0f && GroundType.DEEPSLATE.cooldownMult > 1.0f,
				"deepslate is heavier and slower");
		helper.assertTrue(GroundType.ORE.damageMult > GroundType.STONE.damageMult, "ore hits harder");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void geoRockShotThrowsTheGroundUnderfoot(GameTestHelper helper) {
		ServerPlayer p = hero(helper, GEO, Blocks.DEEPSLATE.defaultBlockState());
		helper.assertTrue(GroundType.under(p).type() == GroundType.DEEPSLATE, "standing on deepslate");
		AbilityRouter.handleInput(p, 1, true);
		var rocks = helper.getLevel().getEntitiesOfClass(GeoRockEntity.class, p.getBoundingBox().inflate(6.0));
		helper.assertFalse(rocks.isEmpty(), "Rock Shot throws a real rock projectile");
		GeoRockEntity rock = rocks.get(0);
		helper.assertTrue(rock.getItem().is(Items.DEEPSLATE), "the rock is made of the ground (deepslate)");
		helper.assertTrue(Math.abs(rock.damage() - GeokinesisHandlers.ROCK_SHOT_DAMAGE * GroundType.DEEPSLATE.damageMult) < 0.01f,
				"deepslate rocks hit x1.35, got " + rock.damage());
		Power geo = Powers.byKey(GEO);
		int cd = ExperimentalPowers.cooldownRemainingTicks(p, geo, geo.ability(AbilitySlot.SLOT_1));
		helper.assertTrue(cd > com.projecthero.mod.hero.HeroConfig.get().scaledCooldown(34),
				"deepslate lengthens the cooldown, got " + cd);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void geoEarthSpikeLineRunsAlongTheGround(GameTestHelper helper) {
		ServerPlayer p = hero(helper, GEO);
		Zombie z = zombieAhead(helper, p, 6);
		p.setXRot(0.0f);
		float before = z.getHealth();
		AbilityRouter.handleInput(p, 2, true);
		helper.succeedWhen(() -> helper.assertTrue(z.getHealth() < before, "the spike line reaches the zombie 6 blocks out"));
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 220)
	public void geoSinkholeOpensAndClosesAPit(GameTestHelper helper) {
		ServerPlayer p = hero(helper, GEO);
		Zombie z = zombieAhead(helper, p, 5);
		BlockPos under = z.blockPosition().below();
		helper.assertTrue(helper.getLevel().getBlockState(under).is(Blocks.STONE), "the floor is stone");
		AbilityRouter.handleInput(p, 7, true);
		helper.assertTrue(helper.getLevel().getBlockState(under).isAir(), "Sinkhole opens the ground under the target");
		helper.assertTrue(BatchBScheduler.pendingRestores() > 0, "the pit is tracked for restoring");
		helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "the target is rooted");
		helper.runAfterDelay(GeokinesisHandlers.SINKHOLE_TICKS + 6, () -> {
			helper.assertTrue(helper.getLevel().getBlockState(under).is(Blocks.STONE), "the pit closes back up");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void geoTectonicPillarLaunchesTheTarget(GameTestHelper helper) {
		ServerPlayer p = hero(helper, GEO);
		Zombie z = zombieAhead(helper, p, 4);
		AbilityRouter.handleInput(p, 8, true);
		helper.assertTrue(z.getDeltaMovement().y > 1.0, "the pillar throws the target skyward, dy=" + z.getDeltaMovement().y);
		helper.succeed();
	}

	// ---------------- 06 Crystalkinesis ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void crystalShardPlantsANodeWhereItLands(GameTestHelper helper) {
		ServerPlayer p = hero(helper, CRYSTAL);
		BlockPos base = p.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, 5), base.offset(2, 3, 5))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		AbilityRouter.handleInput(p, 1, true);
		helper.succeedWhen(() -> helper.assertTrue(!CrystalNodeEntity.owned(p, false).isEmpty(),
				"a crystal node grows where the shard struck"));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crystalShatterDetonatesEveryNode(GameTestHelper helper) {
		ServerPlayer p = hero(helper, CRYSTAL);
		Zombie z = zombieAhead(helper, p, 4);
		ServerLevel level = helper.getLevel();
		CrystalNodeEntity.spawn(level, p, z.position().add(0.8, 0, 0), Direction.UP, false);
		CrystalNodeEntity.spawn(level, p, z.position().add(-0.8, 0, 0), Direction.UP, false);
		helper.assertTrue(CrystalNodeEntity.owned(p, true).size() == 2, "two nodes planted");
		float before = z.getHealth();
		AbilityRouter.handleInput(p, 2, true);
		helper.assertTrue(CrystalNodeEntity.owned(p, true).isEmpty(), "Shatter detonates every node");
		helper.assertTrue(z.getHealth() < before, "the burst hurts what stands by the nodes");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crystalNodeCapCracksTheOldest(GameTestHelper helper) {
		ServerPlayer p = hero(helper, CRYSTAL);
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < CrystalNodeEntity.MAX_NODES + 3; i++) {
			CrystalNodeEntity.spawn(level, p, p.position().add(0, 0, 2 + i * 0.3), Direction.UP, false);
		}
		helper.assertTrue(CrystalNodeEntity.owned(p, false).size() == CrystalNodeEntity.MAX_NODES,
				"never more than " + CrystalNodeEntity.MAX_NODES + " nodes, got " + CrystalNodeEntity.owned(p, false).size());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crystalRefractSplitsANodeIntoThree(GameTestHelper helper) {
		ServerPlayer p = hero(helper, CRYSTAL);
		ServerLevel level = helper.getLevel();
		Vec3 at = p.position().add(0, 0, 4);
		CrystalNodeEntity.spawn(level, p, at, Direction.UP, false);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, at.add(0, 0.4, 0));
		AbilityRouter.handleInput(p, 8, true);
		helper.assertTrue(CrystalNodeEntity.owned(p, false).size() == 3,
				"the struck node splits into three, got " + CrystalNodeEntity.owned(p, false).size());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crystalSpireIsASpireNode(GameTestHelper helper) {
		ServerPlayer p = hero(helper, CRYSTAL);
		p.setXRot(40.0f); // aim at the floor ahead
		AbilityRouter.handleInput(p, 7, true);
		var all = CrystalNodeEntity.owned(p, true);
		helper.assertTrue(all.size() == 1 && all.get(0).isSpire(), "Resonance Spire plants a spire");
		helper.assertTrue(CrystalNodeEntity.owned(p, false).isEmpty(), "a spire does not count against the node cap");
		helper.succeed();
	}

	// ---------------- 08 Pyrokinesis ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void pyroHeatBuildsAndOverheatLocksOut(GameTestHelper helper) {
		ServerPlayer p = hero(helper, PYRO);
		Power pyro = Powers.byKey(PYRO);
		AbilityRouter.handleInput(p, 1, true);
		helper.assertTrue(PyrokinesisHandlers.heat(p) > 5.0f, "a fireball stokes the Heat bar, got " + PyrokinesisHandlers.heat(p));
		ExperimentalPowers.setResource(p, pyro, "heat", 99.0f, PyrokinesisHandlers.MAX_HEAT);
		AbilityRouter.handleInput(p, 8, true); // Fire Whip: +7 heat -> 100
		helper.assertTrue(PyrokinesisHandlers.overheated(p), "hitting 100% overheats");
		AbilityRouter.handleInput(p, 2, true); // Flamethrower
		helper.assertTrue(res(p, PYRO, "flaming") < 0.5f, "no heat-building move while overheated");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void pyroHeatWaveVentsEverythingAndHits(GameTestHelper helper) {
		ServerPlayer p = hero(helper, PYRO);
		Power pyro = Powers.byKey(PYRO);
		Zombie z = zombieAhead(helper, p, 3);
		ExperimentalPowers.setResource(p, pyro, "heat", 100.0f, PyrokinesisHandlers.MAX_HEAT);
		ExperimentalPowers.setResource(p, pyro, "overheated", 1.0f, 1.0f);
		float before = z.getHealth();
		AbilityRouter.handleInput(p, 7, true);
		helper.assertTrue(PyrokinesisHandlers.heat(p) < 0.01f, "Heat Wave vents all heat");
		helper.assertFalse(PyrokinesisHandlers.overheated(p), "and ends the overheat");
		helper.assertTrue(z.getHealth() < before, "the ring burns what is close");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void pyroBurnsBlueAboveThreeQuarters(GameTestHelper helper) {
		ServerPlayer p = hero(helper, PYRO);
		Power pyro = Powers.byKey(PYRO);
		ExperimentalPowers.setResource(p, pyro, "heat", 40.0f, PyrokinesisHandlers.MAX_HEAT);
		helper.assertFalse(PyrokinesisHandlers.blue(p), "40% is not blue");
		float warm = PyrokinesisHandlers.heatMult(p);
		ExperimentalPowers.setResource(p, pyro, "heat", 80.0f, PyrokinesisHandlers.MAX_HEAT);
		helper.assertTrue(PyrokinesisHandlers.blue(p), "80% burns blue");
		helper.assertTrue(PyrokinesisHandlers.heatMult(p) > warm, "hotter hits harder");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void pyroNeverSetsItsUserAlight(GameTestHelper helper) {
		ServerPlayer p = hero(helper, PYRO);
		p.setRemainingFireTicks(200);
		ExperimentalPowers.serverTick(p);
		helper.assertTrue(p.getRemainingFireTicks() <= 0, "a pyrokinetic's fire is snuffed at once");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void pyroJetFlightThrustsAlongTheAim(GameTestHelper helper) {
		ServerPlayer p = hero(helper, PYRO);
		p.setXRot(0.0f);
		p.setYRot(0.0f);
		AbilityRouter.handleInput(p, 3, true);
		helper.assertTrue(PyrokinesisHandlers.jetting(p), "holding X lights the jets");
		ExperimentalPowers.serverTick(p);
		helper.assertTrue(p.getDeltaMovement().z > 0.3, "the jets drive you along your aim, v=" + p.getDeltaMovement());
		helper.assertTrue(PyrokinesisHandlers.heat(p) > 0.0f, "flying builds heat");
		AbilityRouter.handleInput(p, 3, false);
		helper.assertFalse(PyrokinesisHandlers.jetting(p), "letting go cuts the jets");
		helper.assertTrue(res(p, PYRO, "no_fall_until") > helper.getLevel().getGameTime(), "a coast with no fall damage");
		helper.succeed();
	}

	// ---------------- 09 Cryokinesis ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void cryoFrostStacksFreezeSolid(GameTestHelper helper) {
		ServerPlayer p = hero(helper, CRYO);
		Zombie z = zombieAhead(helper, p, 4);
		AbilityRouter.handleInput(p, 1, true); // Ice Bolt
		helper.assertTrue(FrostStacks.stacks(z) == 1, "Ice Bolt adds a frost stack, got " + FrostStacks.stacks(z));
		helper.assertTrue(z.getTicksFrozen() > 0, "the stack shows as freezing");
		FrostStacks.add(p, z, FrostStacks.MAX - 1);
		helper.assertTrue(FrostStacks.frozen(z), "five stacks freeze the target solid");
		helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "and pin it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cryoIceBladeCannotBeKept(GameTestHelper helper) {
		ServerPlayer p = hero(helper, CRYO);
		AbilityRouter.handleInput(p, 8, true);
		helper.assertTrue(IceBladeItem.isBlade(p.getMainHandItem()), "N conjures the Ice Blade into the hand");
		// a dropped copy melts as it enters the world
		ItemEntity dropped = new ItemEntity(helper.getLevel(), p.getX(), p.getY(), p.getZ(), p.getMainHandItem().copy());
		helper.getLevel().addFreshEntity(dropped);
		helper.assertTrue(dropped.isRemoved(), "a dropped Ice Blade never lies in the world");
		// switching away from it melts it
		p.getInventory().selected = (p.getInventory().selected + 1) % 9;
		p.getInventory().tick();
		boolean any = false;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			any |= IceBladeItem.isBlade(p.getInventory().getItem(i));
		}
		helper.assertFalse(any, "the blade melts the moment it leaves the hand");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cryoFlashFreezeFreezesWater(GameTestHelper helper) {
		ServerPlayer p = hero(helper, CRYO);
		BlockPos pool = p.blockPosition().offset(1, -1, 2);
		helper.getLevel().setBlock(pool, Blocks.WATER.defaultBlockState(), 2);
		Zombie z = zombieAhead(helper, p, 3);
		AbilityRouter.handleInput(p, 7, true);
		helper.assertTrue(helper.getLevel().getBlockState(pool).is(Blocks.ICE), "Flash Freeze turns the water to ice");
		helper.assertTrue(FrostStacks.stacks(z) >= 2, "and frosts the creatures around you");
		helper.succeed();
	}

	// ---------------- 25 Water Manipulation ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void waterSupplyStartsFullAndMovesSpendIt(GameTestHelper helper) {
		ServerPlayer p = hero(helper, WATER);
		ExperimentalPowers.serverTick(p);
		helper.assertTrue(WaterHandlers.supply(p) >= WaterHandlers.MAX_WATER - 0.5f, "a new owner starts with a full supply");
		AbilityRouter.handleInput(p, 1, true); // Water Shot
		float after = WaterHandlers.supply(p);
		helper.assertTrue(Math.abs(after - (WaterHandlers.MAX_WATER - WaterHandlers.COST_SHOT)) < 1.0f,
				"Water Shot spends its cost, got " + after);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void waterBottleRefillsTheSupply(GameTestHelper helper) {
		ServerPlayer p = hero(helper, WATER);
		ExperimentalPowers.setResource(p, Powers.byKey(WATER), WaterHandlers.SUPPLY, 100.0f, WaterHandlers.MAX_WATER);
		ItemStack bottle = new ItemStack(Items.POTION);
		bottle.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER));
		p.setItemInHand(InteractionHand.MAIN_HAND, bottle);
		p.gameMode.useItem(p, helper.getLevel(), bottle, InteractionHand.MAIN_HAND);
		helper.assertTrue(Math.abs(WaterHandlers.supply(p) - (100.0f + WaterHandlers.BOTTLE_REFILL)) < 1.0f,
				"a water bottle pours into the supply, got " + WaterHandlers.supply(p));
		helper.assertTrue(p.getMainHandItem().is(Items.GLASS_BOTTLE), "leaving an empty bottle");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void temporaryWaterDrainsOnItsOwnClock(GameTestHelper helper) {
		ServerPlayer p = hero(helper, WATER);
		BlockPos cell = p.blockPosition().offset(0, 1, 3);
		helper.assertTrue(BatchBScheduler.placeTemporarily(helper.getLevel(), cell, Blocks.WATER.defaultBlockState(), 5),
				"water goes into an air cell");
		helper.assertTrue(helper.getLevel().getBlockState(cell).is(Blocks.WATER), "and is there");
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(helper.getLevel().getBlockState(cell).isAir(), "and drains when its time is up");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void waterHealingWaterHeals(GameTestHelper helper) {
		ServerPlayer p = hero(helper, WATER);
		ExperimentalPowers.serverTick(p);
		p.setHealth(8.0f);
		float supply = WaterHandlers.supply(p);
		AbilityRouter.handleInput(p, 7, true);
		helper.assertTrue(p.getHealth() >= 8.0f + WaterHandlers.HEAL_AMOUNT - 0.01f, "Healing Water heals, got " + p.getHealth());
		helper.assertTrue(WaterHandlers.supply(p) < supply, "and costs water");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void waterGeyserLaunchesTheTarget(GameTestHelper helper) {
		ServerPlayer p = hero(helper, WATER);
		ExperimentalPowers.serverTick(p);
		Zombie z = zombieAhead(helper, p, 4);
		AbilityRouter.handleInput(p, 8, true);
		helper.assertTrue(z.getDeltaMovement().y > 1.0, "the geyser throws the target up, dy=" + z.getDeltaMovement().y);
		helper.succeed();
	}
}
