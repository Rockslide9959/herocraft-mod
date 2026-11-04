package com.projecthero.mod.gametest;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.event.EventTypes;
import com.projecthero.mod.galactus.Galactus;
import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.GalactusEvent;
import com.projecthero.mod.galactus.block.GalactusBlocks;
import com.projecthero.mod.galactus.block.HeraldsBeaconBlock;
import com.projecthero.mod.galactus.cinematic.GalactusScenes;
import com.projecthero.mod.galactus.entity.GalactusEntityTypes;
import com.projecthero.mod.galactus.entity.SilverSurferEntity;
import com.projecthero.mod.galactus.item.GalactusItems;
import com.projecthero.mod.galactus.orbit.DevourerEntity;
import com.projecthero.mod.galactus.orbit.DevourerPartEntity;
import com.projecthero.mod.galactus.orbit.GalactusOrbit;
import com.projecthero.mod.galactus.orbit.GalactusRig;
import com.projecthero.mod.galactus.orbit.OrbitArena;
import com.projecthero.mod.galactus.orbit.OrbitIsland;
import com.projecthero.mod.galactus.orbit.OrbitRules;
import com.projecthero.mod.galactus.orbit.StageDevourer;
import com.projecthero.mod.galactus.orbit.StageIslands;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Galactus raid v2 (the Devourer Descends). Like the other raid tests, the timed raid is never run end to end:
 * each test drives one piece directly -- the shared rig, the orbit's islands and hull (in a test instance slot of the
 * orbit dimension), the orbit rules, each stage's damage rules, the grave -- and every event is aborted and removed before
 * the test ends (one Galactus per 256 blocks). Mock players report creative, so they are switched to Survival to count as
 * fighters; nothing here moves a mock player across dimensions.
 */
public class GalactusGameTests implements FabricGameTest {
	/** A test instance slot far from any real raid's. */
	private static final int TEST_SLOT = 60;

	private static GalactusEvent startPinned(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		for (GalactusEvent old : new java.util.ArrayList<>(GalactusEvent.all(helper.getLevel().getServer()))) {
			if (old.state().active()) {
				old.abort(helper.getLevel());
			}
			EventSavedData.get(helper.getLevel()).remove(old.id());
		}
		GalactusEvent e = Galactus.start(helper.getLevel(), at, null, Galactus.Source.COMMAND, x -> {
			x.instant = true;
			x.noGuards = true;
		});
		helper.assertTrue(e != null, "Galactus starts");
		return e;
	}

	private static void end(ServerLevel level, GalactusEvent e) {
		if (e != null && e.state().active()) {
			e.abort(level);
		}
		if (e != null) {
			EventSavedData.get(level).remove(e.id());
		}
	}

	private static ServerPlayer survivor(GameTestHelper helper, boolean tough) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		if (tough) {
			p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 2000, 4));
		}
		return p;
	}

	private static DevourerEntity devourer(GameTestHelper helper, float height) {
		DevourerEntity d = GalactusEntityTypes.DEVOURER.create(helper.getLevel());
		BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
		d.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		d.setHeight(height);
		helper.getLevel().addFreshEntity(d);
		return d;
	}

	// ---------------------------------------------------------------- the contract and the config

	@GameTest(template = EMPTY_STRUCTURE)
	public void galactusRaidIsRegisteredWithItsContractAndConfig(GameTestHelper helper) {
		helper.assertTrue(EventTypes.isRegistered(GalactusEvent.TYPE_ID), "galactus is a registered event type");
		helper.assertTrue(EventTypes.create(GalactusEvent.TYPE_ID, UUID.randomUUID()) instanceof GalactusEvent, "loads back as a Galactus event");
		helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ProjectHeroMod.id("heralds_beacon")).isPresent(), "the Herald's Beacon recipe");
		var shards = helper.getLevel().getRecipeManager().byKey(ProjectHeroMod.id("heralds_beacon_from_shards"));
		helper.assertTrue(shards.isPresent(), "the Cosmic Shard recipe");
		boolean shard = false;
		for (var ing : shards.orElseThrow().value().getIngredients()) {
			shard |= ing.test(new ItemStack(GalactusItems.COSMIC_SHARD));
		}
		helper.assertTrue(shard, "it takes Cosmic Shards");
		helper.assertTrue(GalactusOrbit.level(helper.getLevel().getServer()) != null, "the orbit dimension is loaded");
		helper.assertTrue(GalactusConfig.stage1().staggers == 3 && GalactusConfig.stage3().health == 8000.0 && GalactusConfig.nullifier().useTicks == 80,
				"three staggers, 8000 health, a 4 s Nullifier");
		helper.assertTrue(GalactusConfig.rewards().valuablesMultiplier == 1.5, "a bigger chest");
		for (GalactusScenes.Scene s : GalactusScenes.Scene.values()) {
			helper.assertTrue(GalactusScenes.Scene.byId(s.ordinal()) == s && s.length > 0, s + ": round-trips its id");
		}
		helper.assertTrue(GalactusScenes.HERALD_BOOM < GalactusScenes.HERALD_LAND && GalactusScenes.HERALD_LAND < GalactusScenes.Scene.HERALD.length,
				"C1 beats in order");
		helper.assertTrue(GalactusScenes.DEVOURER_ROAR < GalactusScenes.DEVOURER_LIFT && GalactusScenes.DEVOURER_WHITE < GalactusScenes.Scene.DEVOURER.length,
				"C2 beats in order");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the rig

	@GameTest(template = EMPTY_STRUCTURE)
	public void theRigPutsAFistExactlyWhereItReaches(GameTestHelper helper) {
		GalactusRig.Input in = new GalactusRig.Input();
		in.feet = new Vec3(100, 0, 100);
		in.height = 180f;
		in.yawDeg = 180f;
		in.lean = 0.4f;
		double reach = GalactusRig.reach(180f);
		GalactusRig.Pose rest = GalactusRig.solve(in);
		Vec3 target = rest.shoulderR.add(-10, -30, -25);
		helper.assertTrue(target.distanceTo(rest.shoulderR) < reach, "the target is in reach");
		in.targetR = target;
		in.weightR = 1f;
		GalactusRig.Pose p = GalactusRig.solve(in);
		helper.assertTrue(p.fistR.distanceTo(target) < 0.6, "the fist lands on the target: " + p.fistR + " vs " + target);
		helper.assertTrue(Math.abs(p.elbowR.distanceTo(p.shoulderR) - GalactusRig.UPPER * p.k) < 0.05, "the upper arm keeps its length");
		helper.assertTrue(Math.abs(p.fistR.distanceTo(p.elbowR) - GalactusRig.LOWER * p.k) < 0.05, "the forearm keeps its length");
		// out of reach: the arm points straight at it, fully out
		in.targetR = p.shoulderR.add(0, -400, 0);
		GalactusRig.Pose far = GalactusRig.solve(in);
		helper.assertTrue(Math.abs(far.fistR.distanceTo(far.shoulderR) - reach) < 1.0, "out of reach: the arm is straight");
		// a blended pose agrees with the drawn one (weight 0 = rest)
		in.weightR = 0f;
		GalactusRig.Pose z = GalactusRig.solve(in);
		helper.assertTrue(z.fistR.distanceTo(rest.fistR) < 1.0e-3, "weight 0 is the rest pose");
		// the parts sit on the skeleton
		for (DevourerPartEntity.Kind k : DevourerPartEntity.Kind.values()) {
			Vec3 c = k.centre(p);
			helper.assertTrue(Double.isFinite(c.x) && Double.isFinite(c.y) && Double.isFinite(c.z), k + " has a place");
		}
		helper.assertTrue(DevourerPartEntity.Kind.HAND_R.centre(p).distanceTo(p.fistR) < 1.0e-6, "the hand hit-box is on the fist");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the orbit

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void islandsBuildShatterReformAndTheInstanceWipesClean(GameTestHelper helper) {
		ServerLevel orbit = GalactusOrbit.level(helper.getLevel().getServer());
		GalactusOrbit.hold(orbit, TEST_SLOT);
		try {
			GalactusOrbit.wipe(orbit, TEST_SLOT);
			BlockPos o = GalactusOrbit.origin(TEST_SLOT);
			List<OrbitIsland> field = OrbitIsland.field(o.getX(), o.getZ(), 4, 99L);
			helper.assertTrue(field.size() == 11 && field.get(0).anchor() && field.get(0).radius == 7, "the Anchor, the inner six, four outer");
			for (OrbitIsland i : field) {
				i.build(orbit);
			}
			OrbitIsland one = field.get(1);
			BlockPos top = BlockPos.containing(one.top()).below();
			helper.assertFalse(orbit.getBlockState(top).isAir(), "island 1 stands");
			one.shatter(orbit, one.top(), 30);
			helper.assertTrue(orbit.getBlockState(top).isAir() && one.state == OrbitIsland.GONE, "smashed: gone");
			one.beginReform(orbit, 20, 20);
			one.finishReform(orbit);
			helper.assertFalse(orbit.getBlockState(top).isAir(), "it re-forms");
			helper.assertTrue(one.state == OrbitIsland.INTACT, "intact again");
			// the same seed is the same island, block for block
			java.util.List<BlockState> a = new java.util.ArrayList<>(), b = new java.util.ArrayList<>();
			one.blocks((p, s) -> a.add(s));
			new OrbitIsland(1, one.x, one.z, one.radius, one.seed).blocks((p, s) -> b.add(s));
			helper.assertTrue(a.equals(b), "an island is a pure function of its seed");
			GalactusOrbit.wipe(orbit, TEST_SLOT);
			helper.assertTrue(orbit.getBlockState(top).isAir() && orbit.getBlockState(o).isAir(), "the wipe leaves nothing");
			// the hull and its pillars
			OrbitArena.buildHull(orbit, o);
			helper.assertTrue(orbit.getBlockState(o).is(Blocks.LODESTONE), "the hull's centre");
			BlockPos pillar = OrbitArena.pillar(o, 0);
			OrbitArena.buildPillar(orbit, pillar);
			helper.assertTrue(orbit.getBlockState(pillar).is(Blocks.POLISHED_BLACKSTONE_BRICKS), "a pillar");
			OrbitArena.collapsePillar(orbit, pillar);
			helper.assertTrue(orbit.getBlockState(pillar).isAir(), "a pillar collapses");
			int cleared = GalactusOrbit.wipe(orbit, TEST_SLOT);
			helper.assertTrue(cleared > 2000 && orbit.getBlockState(o).isAir(), "the hull is wiped: " + cleared);
		} finally {
			GalactusOrbit.wipe(orbit, TEST_SLOT);
			GalactusOrbit.release(orbit, TEST_SLOT);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void padsLaunchOnAnArcThatLandsOnTarget(GameTestHelper helper) {
		Vec3 from = new Vec3(0, 129, 0), to = new Vec3(26, 129, 4);
		Vec3 v = OrbitArena.launch(from, to, 32);
		// simulate a player's flight: horizontal drag 0.91, vertical (v - 0.08) * 0.98
		double x = 0, y = 0, z = 0, vx = v.x, vy = v.y, vz = v.z;
		for (int i = 0; i < 32; i++) {
			x += vx;
			y += vy;
			z += vz;
			vx *= 0.91;
			vz *= 0.91;
			vy = (vy - 0.08) * 0.98;
		}
		helper.assertTrue(Math.abs(x - 26) < 0.5 && Math.abs(z - 4) < 0.5 && Math.abs(y) < 0.5, "lands on the pad's target: " + x + ", " + y + ", " + z);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the orbit rules

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void aFallerIsCaughtAndSetDownForALittleDamage(GameTestHelper helper) {
		ServerPlayer p = survivor(helper, false);
		GalactusEvent ev = new GalactusEvent(UUID.randomUUID());
		Vec3 safe = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
		float before = p.getHealth();
		OrbitRules.rescue(ev, helper.getLevel(), p, safe);
		helper.assertTrue(p.position().distanceTo(safe) < 0.5, "set down on safe ground");
		helper.assertTrue(p.getHealth() < before && before - p.getHealth() <= GalactusConfig.orbit().fallDamage + 0.01, "a little damage");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void aFlyerIsWarnedThenBeamedDownAndGrounded(GameTestHelper helper) {
		ServerPlayer p = survivor(helper, true);
		DevourerEntity d = devourer(helper, 100f);
		GalactusEvent ev = new GalactusEvent(UUID.randomUUID());
		p.teleportTo(p.getX(), GalactusOrbit.PLANE_Y + 20, p.getZ());
		p.getAbilities().mayfly = true;
		p.getAbilities().flying = true;
		p.setOnGround(false);
		GalactusConfig.Orbit c = GalactusConfig.orbit();
		for (int i = 0; i < c.flyBeamTicks - 1; i++) {
			p.setOnGround(false);
			OrbitRules.tickPlayer(ev, helper.getLevel(), p, v -> v, null, true, d);
		}
		helper.assertFalse(OrbitRules.grounded(p), "not before the beam");
		p.setOnGround(false);
		OrbitRules.tickPlayer(ev, helper.getLevel(), p, v -> v, null, true, d);
		helper.assertTrue(OrbitRules.grounded(p), "beamed and grounded");
		helper.assertFalse(p.getAbilities().flying, "flight is off");
		helper.assertTrue(p.getDeltaMovement().y < -1.0, "slammed down");
		d.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- Stage 1

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void onlyTheStuckFistFillsTheStaggerBar(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		DevourerEntity d = devourer(helper, 180f);
		GalactusEvent ev = new GalactusEvent(UUID.randomUUID());
		StageIslands s = new StageIslands();
		BlockPos o = helper.absolutePos(new BlockPos(4, 2, 4));
		s.islands.addAll(OrbitIsland.field(o.getX(), o.getZ(), 0, 7L));
		s.targetR = 1;
		s.state = StageIslands.IDLE;
		helper.assertFalse(s.onHit(ev, level, d, DevourerPartEntity.Kind.HAND_R, 50f), "a fist that isn't stuck takes nothing");
		s.state = StageIslands.STUCK;
		helper.assertFalse(s.onHit(ev, level, d, DevourerPartEntity.Kind.HEAD, 50f), "his head is out of play in Stage 1");
		helper.assertFalse(s.onHit(ev, level, d, DevourerPartEntity.Kind.HAND_L, 50f), "the other (free) hand takes nothing");
		helper.assertTrue(s.onHit(ev, level, d, DevourerPartEntity.Kind.HAND_R, 50f) && s.stagger == 50, "the stuck fist fills the bar");
		double need = s.staggerNeeded(1);
		s.onHit(ev, level, d, DevourerPartEntity.Kind.FORE_R, (float) need);
		helper.assertTrue(s.staggers == 1 && s.stagger == 0, "a full bar is a stagger");
		helper.assertTrue(s.staggerNeeded(1) > need, "each stagger needs more");
		helper.assertFalse(s.done(), "three are needed");
		d.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- Stage 3

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void stageThreePartsTakeTheirOwnDamageAndTheCoreOpensLast(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		DevourerEntity d = devourer(helper, 100f);
		GalactusEvent ev = new GalactusEvent(UUID.randomUUID());
		StageDevourer s = new StageDevourer();
		s.begin(ev, 1);
		GalactusConfig.Stage3 c = GalactusConfig.stage3();
		helper.assertTrue(s.health == c.health, "full health");
		var src = level.damageSources().magic();
		s.onHit(ev, level, d, DevourerPartEntity.Kind.HEAD, src, 10f);
		helper.assertTrue(Math.abs(s.health - (c.health - 10 * c.headMultiplier)) < 1e-6, "the head takes double");
		double h = s.health;
		helper.assertFalse(s.onHit(ev, level, d, DevourerPartEntity.Kind.CHEST, src, 10f), "the core is sealed");
		helper.assertTrue(s.health == h, "sealed: nothing through");
		s.onHit(ev, level, d, DevourerPartEntity.Kind.UPPER_R, src, (float) c.gauntletBreak);
		helper.assertTrue(s.armBroke[0] && !s.armBroke[1], "the right gauntlet breaks");
		s.onHit(ev, level, d, DevourerPartEntity.Kind.FORE_L, src, (float) c.gauntletBreak);
		helper.assertTrue(s.armBroke[1], "the left gauntlet breaks");
		h = s.health;
		helper.assertTrue(s.onHit(ev, level, d, DevourerPartEntity.Kind.CHEST, src, 10f), "the core is open");
		helper.assertTrue(Math.abs(s.health - (h - 10 * c.coreMultiplier)) < 1e-6, "the core takes x1.5");
		helper.assertTrue(s.cracked == s.fraction() <= 0.5, "the helmet cracks at half");
		// down to 5%: he kneels for the Nullifier and takes nothing more
		s.health = s.maxHealth * (c.finaleFraction + 0.001);
		s.onHit(ev, level, d, DevourerPartEntity.Kind.HEAD, src, 50f);
		helper.assertTrue(s.finale && s.cracked, "the finale (and the crack on the way)");
		helper.assertTrue(Math.abs(s.health - s.maxHealth * c.finaleFraction) < 1e-6, "held at 5%");
		helper.assertFalse(s.onHit(ev, level, d, DevourerPartEntity.Kind.HEAD, src, 50f), "nothing gets through in the finale");
		d.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- Stage 2

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void aCarrierDropsTheCellOnlyToABigHit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = survivor(helper, true);
		GalactusEvent ev = new GalactusEvent(UUID.randomUUID());
		p.getInventory().add(new ItemStack(GalactusItems.COSMIC_CELL));
		ev.stage2.onCarrierHurt(ev, level, p, (float) GalactusConfig.stage2().carrierDropDamage - 1f);
		helper.assertTrue(p.getInventory().countItem(GalactusItems.COSMIC_CELL) == 1, "a small hit: still carried");
		ev.stage2.onCarrierHurt(ev, level, p, (float) GalactusConfig.stage2().carrierDropDamage);
		helper.assertTrue(p.getInventory().countItem(GalactusItems.COSMIC_CELL) == 0, "a big hit: knocked loose");
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4), ie -> ie.getItem().is(GalactusItems.COSMIC_CELL));
		helper.assertTrue(drops.size() == 1, "it lies on the deck");
		drops.forEach(ItemEntity::discard);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the grave

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void deathInOrbitKeepsYourItemsButNotTheRaidsOwn(GameTestHelper helper) {
		ServerPlayer p = survivor(helper, false);
		p.getInventory().add(new ItemStack(Items.DIAMOND, 5));
		p.getInventory().add(new ItemStack(GalactusItems.COSMIC_CELL));
		GalactusOrbit.stashOnDeath(p);
		helper.assertTrue(p.getInventory().isEmpty(), "nothing drops (into the void)");
		List<ItemStack> grave = GalactusOrbit.grave(p);
		helper.assertTrue(grave.size() == 1 && grave.get(0).is(Items.DIAMOND) && grave.get(0).getCount() == 5, "the diamonds are kept, the cell is not");
		GalactusOrbit.returnGrave(p);
		helper.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 5, "handed back on respawn");
		helper.assertTrue(GalactusOrbit.grave(p).isEmpty(), "once");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the raid's life

	@GameTest(template = EMPTY_STRUCTURE, batch = "galactus_v2_start", timeoutTicks = 40)
	public void theRaidStartsTheHeraldKneelsAndAnAbortLeavesNothing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GalactusEvent e = startPinned(helper);
		SilverSurferEntity s;
		try {
			helper.assertTrue(e.state() == EventState.RUNNING && e.phase() == GalactusEvent.Phase.HERALD, "instant: straight to the duel");
			BlockState beacon = level.getBlockState(e.center());
			helper.assertTrue(beacon.is(GalactusBlocks.HERALDS_BEACON) && beacon.getValue(HeraldsBeaconBlock.ACTIVE), "the Herald's Beacon is lit");
			helper.assertTrue(Galactus.start(level, helper.absolutePos(new BlockPos(5, 2, 5)), null, Galactus.Source.COMMAND) == null,
					"a second one nearby is refused");
			s = e.surfer(level);
			helper.assertTrue(s != null && e.ownsEntity(s), "the Herald, owned by the raid");
			ServerPlayer p = survivor(helper, true);
			e.addFighter(p.getUUID());
			s.invulnerableTime = 0;
			s.hurt(level.damageSources().playerAttack(p), 100000f);
			helper.assertTrue(s.isAlive() && Math.abs(s.getHealth() - s.turnHealth()) < 0.01, "he can't be killed: he stops at 10%");
			s.checkTurn(level);
			e.serverTick(level);
			helper.assertTrue(e.phase() == GalactusEvent.Phase.KNEEL && s.holdTurn && !s.isAlly(), "he kneels (no turn yet)");
			helper.assertFalse(e.orbitActive(), "still on the ground");
		} finally {
			end(level, e);
		}
		BlockState after = level.getBlockState(e.center());
		helper.assertTrue(after.is(GalactusBlocks.HERALDS_BEACON) && !after.getValue(HeraldsBeaconBlock.ACTIVE), "abort: the beacon goes dormant");
		helper.assertTrue(!s.isAlive() || s.isRemoved(), "the Herald is gone");
		helper.assertTrue(GalactusEvent.find(level, e.id()) == null, "nothing of it remains");
		level.setBlockAndUpdate(e.center(), Blocks.AIR.defaultBlockState());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anOldV016SaveLoadsIntoTheNewRaid(GameTestHelper helper) {
		GalactusEvent e = new GalactusEvent(UUID.randomUUID());
		CompoundTag tag = e.save();
		tag.putString("GalPhase", "GALACTUS");
		GalactusEvent back = new GalactusEvent(e.id());
		back.load(tag);
		helper.assertTrue(back.phase() == GalactusEvent.Phase.KNEEL, "a v0.16 Act 3 save restarts at the Devourer's reveal: " + back.phase());
		tag.putString("GalPhase", "STAGE2");
		tag.putInt("GalSlot", 3);
		GalactusEvent mid = new GalactusEvent(e.id());
		mid.load(tag);
		helper.assertTrue(mid.phase() == GalactusEvent.Phase.STAGE2 && mid.orbitSlot() == 3, "a v2 save keeps its stage and instance");
		helper.succeed();
	}
}
