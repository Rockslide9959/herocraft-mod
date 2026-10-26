package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HoldTap;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p22.ChloroFxEntity;
import com.projecthero.mod.hero.power.p22.ChloroSurvival;
import com.projecthero.mod.hero.power.p22.PlantEntities;
import com.projecthero.mod.hero.power.p22.PlantManipulationHandlers;
import com.projecthero.mod.hero.power.p22.ThornProjectile;
import com.projecthero.mod.hero.power.p22.TreantEntity;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.hero.visual.ShiftStrips;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Chlorokinesis revamp (docs/CHLOROKINESIS_REVAMP_0171.md): every key's tap and Shift move does what it says,
 * the hold-G Thorn Sentry, the Chlorophyll meter's costs / refusal / refill, and the survival side (Overgrowth harvest
 * growth, Bloom Burst, the Living Architecture builds, Green Thumb, Tree Feller, Root Sense, fruit). Keys are driven
 * through {@link AbilityRouter#dispatchExperimental}, with {@link AbilityRouter#serverTick} run every game tick (mock
 * players are not reliably ticked by the server). The cage is 8 wide: everything stays within it.
 */
public class ChlorokinesisGameTests implements FabricGameTest {
	private static final String K = PlantManipulationHandlers.KEY;
	private static final int HELD = HoldTap.HOLD_TICKS + 2;

	// ---------------- helpers ----------------

	/** A survival mock chlorokinetic at (2.5, 2, 1.5) on a stone floor, facing +Z, in a cleared 5x5x8 room; ticked every tick. */
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, -1), base.offset(2, 4, 5))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -1), base.offset(2, -1, 5))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		Power power = Powers.byKey(K);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		PlantManipulationHandlers.setChlorophyll(p, PlantManipulationHandlers.MAX_CHLOROPHYLL);
		helper.onEachTick(() -> {
			if (helper.getLevel().getServer().getPlayerList().getPlayer(p.getUUID()) != p) {
				AbilityRouter.serverTick(p);
			}
		});
		return p;
	}

	private static Power power() {
		return Powers.byKey(K);
	}

	private static void down(ServerPlayer p, int slot) {
		AbilityRouter.dispatchExperimental(p, AbilitySlot.byNumber(slot), true);
	}

	private static void up(ServerPlayer p, int slot) {
		AbilityRouter.dispatchExperimental(p, AbilitySlot.byNumber(slot), false);
	}

	/** A tap: press and release at once. */
	private static void tap(ServerPlayer p, int slot) {
		down(p, slot);
		up(p, slot);
	}

	/** A Shift press: Sneak held through the press and release. */
	private static void shift(ServerPlayer p, int slot) {
		p.setShiftKeyDown(true);
		down(p, slot);
		up(p, slot);
		p.setShiftKeyDown(false);
	}

	private static float meter(ServerPlayer p) {
		return PlantManipulationHandlers.chlorophyll(p);
	}

	/** The meter after spending {@code cost} once, give or take the refill of a few ticks. */
	private static void assertSpent(GameTestHelper helper, ServerPlayer p, float cost, String what) {
		float m = meter(p);
		helper.assertTrue(m <= 100 - cost + 1.5f && m >= 100 - cost - 0.05f, what + ": " + cost + " Chlorophyll spent, meter at " + m);
	}

	/** A no-AI husk dummy (a hostile) at relative (x, 2, z), with plenty of health. */
	private static Husk husk(GameTestHelper helper, int x, int z) {
		Husk h = helper.spawn(EntityType.HUSK, new BlockPos(x, 2, z));
		h.setPersistenceRequired();
		h.setNoAi(true);
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
		h.setHealth(100);
		return h;
	}

	private static List<ChloroFxEntity> fx(GameTestHelper helper, ServerPlayer p, int kind) {
		return helper.getLevel().getEntitiesOfClass(ChloroFxEntity.class, p.getBoundingBox().inflate(40),
				e -> e.kind() == kind && p.getUUID().equals(e.ownerId()));
	}

	private static BlockPos rel(GameTestHelper helper, int x, int y, int z) {
		return helper.absolutePos(new BlockPos(x, y, z));
	}

	// ---------------- registry / HUD ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void sixKeysMeterStripsAndHold(GameTestHelper helper) {
		Power power = power();
		helper.assertTrue(power.abilities().size() == 6, "six abilities (no H / N), got " + power.abilities().size());
		helper.assertTrue(!power.hasSlot(AbilitySlot.SLOT_7) && !power.hasSlot(AbilitySlot.SLOT_8), "H and N are never moves");
		String[] ids = { "thorn_volley", "root_snare", "vine_grapple", "overgrowth", "bloom_burst", "verdant_form" };
		for (int i = 0; i < ids.length; i++) {
			Ability a = power.ability(AbilitySlot.byNumber(i + 1));
			helper.assertTrue(a != null && a.id().equals(ids[i]), "slot " + (i + 1) + " should be " + ids[i]);
			helper.assertTrue(AbilityHandlers.has(power, a), ids[i] + " has a handler");
		}
		var chl = MutationMeters.get(K, "chlorophyll");
		helper.assertTrue(chl != null && chl.always(), "Chlorophyll is an always-on HUD meter");
		for (AbilitySlot s : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
				AbilitySlot.SLOT_6 }) {
			helper.assertTrue(ShiftStrips.get(K, s) != null, "a Shift cooldown strip on " + s);
		}
		helper.assertTrue(HoldTap.hasHold(K, "root_snare"), "hold G = Thorn Sentry");
		for (String flag : new String[] { "p22.blessing", "p22.overgrowth", "p22.glide" }) {
			helper.assertTrue(MutationVisuals.registeredFlags().contains(flag), flag + " is synced");
		}
		helper.succeed();
	}

	// ---------------- the meter ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void shortMeterRefusesThenRefills(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		PlantManipulationHandlers.setChlorophyll(p, 3.0f);
		tap(p, 1); // Thorn Volley costs 5
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(ThornProjectile.class, p.getBoundingBox().inflate(20)).isEmpty(),
				"no thorns on 3 Chlorophyll");
		helper.assertTrue(Math.abs(meter(p) - 3.0f) < 0.2f, "nothing spent");
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(meter(p) > 3.0f + 1.5f, "the meter refills over 2 s, at " + meter(p));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void coveredGroundRefillsSlowerThanOpenSky(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		float open = PlantManipulationHandlers.regenPerSecond(p);
		BlockPos head = BlockPos.containing(p.getEyePosition());
		for (BlockPos pos : BlockPos.betweenClosed(head.offset(-1, 1, -1), head.offset(1, 1, 1))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		float covered = PlantManipulationHandlers.regenPerSecond(p);
		helper.assertTrue(covered <= open, "under a roof (" + covered + "/s) is slower than the open sky (" + open + "/s)");
		helper.assertTrue(Math.abs(covered - PlantManipulationHandlers.REGEN_DARK) < 0.01f
				|| Math.abs(covered - PlantManipulationHandlers.REGEN_DARK * PlantManipulationHandlers.LUSH_FACTOR) < 0.01f,
				"dark and covered = the slowest rate, got " + covered);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fruitFeedsTheMeter(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		PlantManipulationHandlers.setChlorophyll(p, 40);
		ChloroSurvival.eatFruit(p);
		helper.assertTrue(Math.abs(meter(p) - 52) < 0.6f, "a fruit gives 12 Chlorophyll, at " + meter(p));
		helper.succeed();
	}

	// ---------------- R ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void thornVolleyFiresThreeThornsThatHit(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Husk h = husk(helper, 2, 5);
		p.setXRot(8.0f);
		tap(p, 1);
		var thorns = helper.getLevel().getEntitiesOfClass(ThornProjectile.class, p.getBoundingBox().inflate(10),
				t -> t.getOwner() == p);
		helper.assertTrue(thorns.size() == 3, "three thorns, got " + thorns.size());
		assertSpent(helper, p, PlantManipulationHandlers.COST_VOLLEY, "Thorn Volley");
		helper.assertFalse(ExperimentalPowers.cooldownReady(p, power(), power().ability(AbilitySlot.SLOT_1)), "R goes on its short cooldown");
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(h.getHealth() < 100, "a thorn hit the husk");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vineLashDamagesAndYanks(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 5));
		z.setPersistenceRequired();
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		float before = z.getHealth();
		shift(p, 1);
		helper.assertTrue(z.getHealth() <= before - 9.0f, "Vine Lash hits for 12 (less armour): " + before + " -> " + z.getHealth());
		Vec3 toward = p.position().subtract(z.position()).normalize();
		helper.assertTrue(z.getDeltaMovement().dot(toward) > 0.3, "and yanks it toward you: " + z.getDeltaMovement());
		helper.assertTrue(PlantManipulationHandlers.countdown(p, PlantManipulationHandlers.LASH_CD_RES) > 20, "Shift+R has its own cooldown");
		helper.assertTrue(ExperimentalPowers.cooldownReady(p, power(), power().ability(AbilitySlot.SLOT_1)),
				"which leaves the tap move free");
		helper.assertTrue(!fx(helper, p, ChloroFxEntity.VINE).isEmpty(), "the vine is drawn");
		helper.succeed();
	}

	// ---------------- G ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void rootSnarePinsAndBleeds(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 5));
		z.setPersistenceRequired();
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		float before = z.getHealth();
		tap(p, 2);
		float afterEruption = z.getHealth();
		helper.assertTrue(afterEruption < before, "the roots erupt for 6");
		var roots = fx(helper, p, ChloroFxEntity.ROOTS);
		helper.assertTrue(roots.size() == 1 && roots.get(0).victim() == z, "roots hold the zombie");
		assertSpent(helper, p, PlantManipulationHandlers.COST_SNARE, "Root Snare");
		Vec3 pinned = z.position();
		helper.runAfterDelay(3, () -> z.setDeltaMovement(0.8, 0, -0.8));
		helper.runAfterDelay(35, () -> {
			helper.assertTrue(z.position().distanceTo(pinned) < 0.35, "pinned in place, moved " + z.position().distanceTo(pinned));
			helper.assertTrue(z.getHealth() < afterEruption, "and bleeding 2 a second");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rootSnareOnNothingCostsNothing(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setXRot(-60.0f); // at the sky
		tap(p, 2);
		helper.assertTrue(meter(p) > 99.0f, "nothing spent");
		helper.assertTrue(ExperimentalPowers.cooldownReady(p, power(), power().ability(AbilitySlot.SLOT_2)), "no cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sporeCloudHasItsOwnCooldown(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		shift(p, 2);
		helper.assertTrue(!fx(helper, p, ChloroFxEntity.SPORES).isEmpty(), "the cloud is drawn");
		assertSpent(helper, p, PlantManipulationHandlers.COST_SPORES, "Spore Cloud");
		float m = meter(p);
		shift(p, 2);
		helper.assertTrue(Math.abs(meter(p) - m) < 0.5f, "a second Shift+G is refused while it cools down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void holdGPlantsTheThornSentry(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		down(p, 2);
		helper.runAfterDelay(HELD, () -> {
			up(p, 2);
			var sentries = helper.getLevel().getEntities(PlantEntities.THORN_SENTRY, e -> p.getUUID().equals(e.ownerId()));
			helper.assertTrue(sentries.size() == 1, "holding G plants one sentry, found " + sentries.size());
			helper.assertTrue(PlantManipulationHandlers.countdown(p, PlantManipulationHandlers.SENTRY_CD_RES) > 0, "with its own cooldown");
			helper.assertTrue(meter(p) < 100 - PlantManipulationHandlers.COST_SENTRY + 2, "30 Chlorophyll spent");
			helper.assertTrue(fx(helper, p, ChloroFxEntity.ROOTS).isEmpty(), "and no Root Snare went off");
			helper.succeed();
		});
	}

	// ---------------- X ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void vineGrappleReelsYouToTheWall(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		for (BlockPos pos : BlockPos.betweenClosed(rel(helper, 0, 2, 6), rel(helper, 4, 5, 6))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.setXRot(-10.0f);
		tap(p, 3);
		helper.assertTrue(p.getDeltaMovement().z > 0.8, "launched toward the wall: " + p.getDeltaMovement());
		helper.assertTrue(ExperimentalPowers.getResource(p, power(), "no_fall_until") > helper.getLevel().getGameTime(), "no fall damage after");
		assertSpent(helper, p, PlantManipulationHandlers.COST_GRAPPLE, "Vine Grapple");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void leafGlideTogglesOnShiftX(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		shift(p, 3);
		helper.assertTrue(PlantManipulationHandlers.gliding(p), "Shift+X opens the canopy");
		helper.assertTrue(ExperimentalPowers.isToggled(p, power(), power().ability(AbilitySlot.SLOT_3)), "the X box lights");
		shift(p, 3);
		helper.assertFalse(PlantManipulationHandlers.gliding(p), "Shift+X again closes it");
		helper.succeed();
	}

	// ---------------- Z ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void overgrowthMaturesCropsAndStrikes(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		BlockPos soil = rel(helper, 4, 1, 3);
		helper.getLevel().setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 2);
		helper.getLevel().setBlock(soil.above(), Blocks.WHEAT.defaultBlockState(), 2);
		Husk h = husk(helper, 1, 4);
		tap(p, 4);
		helper.assertTrue(PlantManipulationHandlers.overgrowthCharging(p), "the gather starts");
		helper.runAfterDelay(PlantManipulationHandlers.OVERGROWTH_GATHER + 4, () -> {
			BlockState wheat = helper.getLevel().getBlockState(soil.above());
			helper.assertTrue(wheat.getBlock() instanceof CropBlock c && c.isMaxAge(wheat), "the wheat is ripe: " + wheat);
			helper.assertTrue(h.getHealth() <= 100 - 20, "the eruption hit the husk: " + h.getHealth());
			helper.assertFalse(ExperimentalPowers.cooldownReady(p, power(), power().ability(AbilitySlot.SLOT_4)), "Z goes on cooldown");
			helper.assertFalse(PlantManipulationHandlers.overgrowthCharging(p), "the gather is over");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void treantGrowsOneAtATime(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setXRot(40.0f);
		shift(p, 4);
		var first = helper.getLevel().getEntities(PlantEntities.TREANT, t -> t.ownerId().map(p.getUUID()::equals).orElse(false));
		helper.assertTrue(first.size() == 1, "a Treant grows, found " + first.size());
		helper.assertTrue(first.get(0).getMaxHealth() >= PlantManipulationHandlers.TREANT_HEALTH - 0.1, "80 HP");
		helper.assertTrue(PlantManipulationHandlers.countdown(p, PlantManipulationHandlers.TREANT_CD_RES) > 0, "Shift+Z cools down");
		ExperimentalPowers.setResource(p, power(), PlantManipulationHandlers.TREANT_CD_RES, 0, 1.0e9f);
		PlantManipulationHandlers.setChlorophyll(p, 100);
		shift(p, 4);
		helper.runAfterDelay(2, () -> {
			var now = helper.getLevel().getEntities(PlantEntities.TREANT, t -> t.ownerId().map(p.getUUID()::equals).orElse(false));
			helper.assertTrue(now.size() == 1, "a second one replaces the first, found " + now.size());
			helper.assertFalse(((TreantEntity) now.get(0)).mayTarget(p), "it never turns on its grower");
			helper.succeed();
		});
	}

	// ---------------- V ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void bloomBurstCleansesAndHeals(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setHealth(10.0f);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
		tap(p, 5);
		helper.assertFalse(p.hasEffect(MobEffects.POISON), "the bloom cleanses poison");
		helper.assertTrue(!fx(helper, p, ChloroFxEntity.BLOOM).isEmpty(), "the flowers are drawn");
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(p.getHealth() > 10.0f, "and heals 2 a second, at " + p.getHealth());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vineLadderGrowsClimbableVines(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		for (BlockPos pos : BlockPos.betweenClosed(rel(helper, 1, 2, 3), rel(helper, 3, 7, 3))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.setXRot(0.0f);
		helper.assertTrue(ChloroSurvival.buildFor(p) == ChloroSurvival.Build.LADDER, "facing a wall = Vine Ladder");
		shift(p, 5);
		int vines = 0;
		for (int y = 2; y <= 7; y++) {
			BlockState st = helper.getLevel().getBlockState(rel(helper, 2, y, 2));
			if (st.is(Blocks.VINE)) {
				vines++;
				helper.assertTrue(st.is(BlockTags.CLIMBABLE), "vines are climbable");
			}
		}
		helper.assertTrue(vines >= 4, "a ladder of vines up the wall, got " + vines);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rootBridgeIsWalkable(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		for (int z = 2; z <= 5; z++) {
			helper.getLevel().setBlock(rel(helper, 2, 1, z), Blocks.AIR.defaultBlockState(), 2);
		}
		p.setXRot(80.0f);
		helper.assertTrue(ChloroSurvival.buildFor(p) == ChloroSurvival.Build.BRIDGE, "looking down = Root Bridge");
		helper.assertTrue(ChloroSurvival.rootBridge(p, 4) >= 4, "roots fill the gap");
		BlockPos at = rel(helper, 2, 1, 3);
		BlockState st = helper.getLevel().getBlockState(at);
		helper.assertTrue(st.is(Blocks.MANGROVE_ROOTS), "mangrove roots under the next step: " + st);
		helper.assertFalse(st.getCollisionShape(helper.getLevel(), at).isEmpty(), "and you can stand on them");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void growShelterBuildsAHut(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		// stand in the middle of the room so the 5x5 fits
		Vec3 mid = helper.absoluteVec(new Vec3(2.5, 2.0, 3.5));
		p.moveTo(mid.x, mid.y, mid.z, 0.0f, -80.0f);
		helper.assertTrue(ChloroSurvival.buildFor(p) == ChloroSurvival.Build.SHELTER, "looking up = Grow Shelter");
		shift(p, 5);
		BlockPos c = p.blockPosition();
		helper.assertTrue(helper.getLevel().getBlockState(c.offset(2, 0, 2)).is(Blocks.OAK_LOG), "log corner posts");
		helper.assertTrue(helper.getLevel().getBlockState(c.offset(0, 3, 0)).is(Blocks.SHROOMLIGHT), "a shroomlight in the roof");
		BlockState wall = helper.getLevel().getBlockState(c.offset(2, 1, 1));
		helper.assertTrue(wall.getBlock() instanceof LeavesBlock && wall.getValue(LeavesBlock.PERSISTENT), "persistent leaf walls: " + wall);
		helper.assertTrue(helper.getLevel().getBlockState(c.offset(0, 0, 2)).isAir(), "a doorway where you look");
		helper.assertTrue(PlantManipulationHandlers.countdown(p, PlantManipulationHandlers.ARCH_CD_RES) > 20 * 20, "the shelter's long cooldown");
		helper.succeed();
	}

	// ---------------- C ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void verdantFormAddsBarkArmour(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		double before = p.getAttributeValue(Attributes.ARMOR);
		tap(p, 6);
		helper.assertTrue(PlantManipulationHandlers.blessingOn(p), "Verdant Form on");
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) >= before + 5.9, "+6 armour");
		helper.assertTrue(ExperimentalPowers.isToggled(p, power(), power().ability(AbilitySlot.SLOT_6)), "the C box lights");
		tap(p, 6);
		helper.assertFalse(PlantManipulationHandlers.blessingOn(p), "and off again");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - before) < 0.01, "the armour goes");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rootSenseFindsOre(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		helper.getLevel().setBlock(rel(helper, 4, 0, 4), Blocks.DIAMOND_ORE.defaultBlockState(), 2);
		ChloroSurvival.SenseReport r = ChloroSurvival.rootSense(p);
		helper.assertTrue(r.ores().getOrDefault("diamond", 0) >= 1, "the roots feel the diamond: " + r.ores());
		helper.assertTrue(r.trails().contains(rel(helper, 4, 0, 4)), "and a sap trail runs to it");
		helper.assertTrue(meter(p) < 100 - PlantManipulationHandlers.COST_SENSE + 1, "15 Chlorophyll");
		helper.succeed();
	}

	// ---------------- survival passives ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void greenThumbHarvestsAndReplants(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		BlockPos soil = rel(helper, 3, 1, 3);
		helper.getLevel().setBlock(soil, Blocks.FARMLAND.defaultBlockState(), 2);
		BlockState ripe = ((CropBlock) Blocks.WHEAT).getStateForAge(7);
		helper.getLevel().setBlock(soil.above(), ripe, 2);
		helper.assertTrue(ChloroSurvival.harvest(p, helper.getLevel(), soil.above(), ripe), "a ripe crop is picked");
		BlockState now = helper.getLevel().getBlockState(soil.above());
		helper.assertTrue(now.is(Blocks.WHEAT) && ((CropBlock) Blocks.WHEAT).getAge(now) == 0, "and replanted: " + now);
		var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(soil).inflate(2),
				i -> i.getItem().is(Items.WHEAT));
		helper.assertTrue(!drops.isEmpty(), "the wheat drops");
		// purify: sneak on dirt -> grass
		BlockPos dirt = rel(helper, 1, 1, 4);
		helper.getLevel().setBlock(dirt, Blocks.DIRT.defaultBlockState(), 2);
		helper.assertTrue(ChloroSurvival.purify(p, helper.getLevel(), dirt, Blocks.DIRT.defaultBlockState(), Direction.UP), "purify");
		helper.assertTrue(helper.getLevel().getBlockState(dirt).is(Blocks.GRASS_BLOCK), "bare dirt greens");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void treeFellerFellsAndReplants(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		BlockPos ground = rel(helper, 4, 1, 4);
		helper.getLevel().setBlock(ground, Blocks.DIRT.defaultBlockState(), 2);
		BlockPos stump = ground.above();
		for (int y = 1; y <= 4; y++) {
			helper.getLevel().setBlock(ground.above(y), Blocks.OAK_LOG.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(ground.offset(-1, 4, -1), ground.offset(1, 5, 1))) {
			if (helper.getLevel().getBlockState(pos).isAir()) {
				helper.getLevel().setBlock(pos, Blocks.OAK_LEAVES.defaultBlockState(), 2);
			}
		}
		// the player broke the stump
		BlockState log = Blocks.OAK_LOG.defaultBlockState();
		helper.getLevel().setBlock(stump, Blocks.AIR.defaultBlockState(), 2);
		int felled = ChloroSurvival.fellTree(p, helper.getLevel(), stump, log);
		helper.assertTrue(felled == 3, "the other three logs fall, got " + felled);
		for (int y = 2; y <= 4; y++) {
			helper.assertFalse(helper.getLevel().getBlockState(ground.above(y)).is(BlockTags.LOGS), "log " + y + " is gone");
		}
		helper.assertTrue(helper.getLevel().getBlockState(stump).is(Blocks.OAK_SAPLING), "an oak sapling is replanted");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void treeFellerLeavesBuildingsAlone(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		BlockPos ground = rel(helper, 4, 1, 4);
		for (int y = 1; y <= 3; y++) {
			helper.getLevel().setBlock(ground.above(y), Blocks.OAK_LOG.defaultBlockState(), 2);
		}
		int felled = ChloroSurvival.fellTree(p, helper.getLevel(), ground.above(1), Blocks.OAK_LOG.defaultBlockState());
		helper.assertTrue(felled == 0, "a log post with no leaves is not a tree");
		helper.assertTrue(helper.getLevel().getBlockState(ground.above(3)).is(Blocks.OAK_LOG), "it stays");
		helper.succeed();
	}
}
