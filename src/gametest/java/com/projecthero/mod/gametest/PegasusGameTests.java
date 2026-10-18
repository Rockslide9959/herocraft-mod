package com.projecthero.mod.gametest;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.pegasus.Pegasus;
import com.projecthero.mod.miraculous.pegasus.PegasusConfig;
import com.projecthero.mod.miraculous.pegasus.PegasusMoves;
import com.projecthero.mod.miraculous.pegasus.PegasusTeleport;
import com.projecthero.mod.miraculous.pegasus.PegasusWaypoints;
import com.projecthero.mod.miraculous.pegasus.entity.HorseshoeEntity;
import com.projecthero.mod.miraculous.pegasus.entity.PegasusPortalEntity;
import com.projecthero.mod.miraculous.pegasus.network.PegasusVoyageMenuPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Pegasus (Kaalki): one test per move -- the server effect and its number -- plus the safe-landing rules, the
 * waypoints outlasting a detransformation and a save / load, and the clean-up on detransformation. Mock players are
 * not ticked by the server, so tests that run over time drive the Miraculous tick themselves ({@link #pump}).
 */
public class PegasusGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	// ---------------------------------------------------------------- helpers

	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** Pegasus facing +Z, the transformation finished, every move ready. */
	private static ServerPlayer pegasus(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(Miraculous.grant(p, Kwami.KAALKI), "Kaalki is granted");
		Miraculous.transform(p);
		ready(helper, p);
		helper.assertTrue(Miraculous.canAct(p), "the transformation has finished");
		return p;
	}

	private static void ready(GameTestHelper helper, ServerPlayer p) {
		long now = helper.getLevel().getGameTime();
		Miraculous.edit(p, n -> {
			n.formChangeAt = now - 200L;
			n.animId = MiraculousState.ANIM_NONE;
			n.abilityReadyAt.clear();
		});
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Miraculous.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z, double health, boolean ai) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(!ai);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		if (m.getAttribute(Attributes.ARMOR) != null) {
			m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		}
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static List<PegasusPortalEntity> portals(GameTestHelper helper, ServerPlayer p) {
		return helper.getLevel().getEntitiesOfClass(PegasusPortalEntity.class, p.getBoundingBox().inflate(64.0), e -> p.getUUID().equals(e.ownerId()));
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void horseshoeThrowHitsForTwelveAndComesBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = pegasus(helper, 4.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.5, 100, false);
		pump(helper, p);
		HorseshoeEntity h = PegasusMoves.throwShoe(p);
		helper.assertTrue(h != null && Pegasus.shoeOut(p), "the Horseshoe is out");
		helper.assertTrue(PegasusMoves.throwShoe(p) == null, "a second throw is refused while it is out");
		helper.runAfterDelay(80, () -> {
			float taken = 100f - z.getHealth();
			helper.assertTrue(taken >= PegasusConfig.THROW_DAMAGE - EPS && taken <= 2 * PegasusConfig.THROW_DAMAGE + EPS, "12 a pass, took " + taken);
			helper.assertTrue(h.isRemoved() && !Pegasus.shoeOut(p), "it came back and was caught");
			helper.assertTrue(Miraculous.state(p).abilityReadyAt.containsKey(PegasusConfig.THROW), "the throw's cooldown was set on the catch");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void horseshoeVolleyFansThreeForSevenEach(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = pegasus(helper, 4.5, 2.0, 0.5);
		// far enough out that the fan has spread past it (up close all three can hit)
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 7.5, 100, false);
		List<HorseshoeEntity> shoes = PegasusMoves.volley(p);
		helper.assertTrue(shoes.size() == PegasusConfig.VOLLEY_COUNT, "three horseshoes, got " + shoes.size());
		helper.runAfterDelay(20, () -> {
			float taken = 100f - z.getHealth();
			helper.assertTrue(Math.abs(taken - PegasusConfig.VOLLEY_DAMAGE) < EPS, "the middle one hits for 7, took " + taken);
			helper.assertTrue(shoes.stream().allMatch(HorseshoeEntity::isRemoved), "all gone (a hit, or out of range)");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE)
	public void hoofKickHitsFrontAndBehindForFourteen(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = pegasus(helper, 3.5, 2.0, 3.5);
		Zombie front = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.5, 100, false);
		Zombie back = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 1.5, 100, false);
		Zombie side = mob(helper, EntityType.ZOMBIE, 5.8, 2.0, 3.5, 100, false);
		int n = PegasusMoves.kick(p);
		helper.assertTrue(n == 2, "front and behind, got " + n);
		for (Zombie z : new Zombie[] { front, back }) {
			helper.assertTrue(Math.abs((100f - z.getHealth()) - PegasusConfig.KICK_DAMAGE) < EPS, "14 each, took " + (100f - z.getHealth()));
			Vec3 away = z.position().subtract(p.position()).multiply(1, 0, 1).normalize();
			helper.assertTrue(z.getDeltaMovement().dot(away) > 0.5 && z.getDeltaMovement().y > 0.2, "launched away and up");
		}
		helper.assertTrue(side.getHealth() == 100f, "not the one off to the side");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void gallopTrampleHitsAlongThePathForTen(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = pegasus(helper, 3.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.9, 2.0, 6.5, 100, false);
		pump(helper, p);
		PegasusMoves.trample(p);
		helper.assertTrue(Pegasus.running(p, Pegasus.V_TRAMPLE), "the charge runs");
		helper.runAfterDelay(PegasusConfig.TRAMPLE_TICKS + 3, () -> {
			helper.assertTrue(Math.abs((100f - z.getHealth()) - PegasusConfig.TRAMPLE_DAMAGE) < EPS, "trampled for 10, took " + (100f - z.getHealth()));
			helper.assertTrue(PegasusMoves.trampled(p).size() == 1, "once");
			helper.assertTrue(!Pegasus.running(p, Pegasus.V_TRAMPLE), "the charge is over");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- X

	@GameTest(template = EMPTY_STRUCTURE)
	public void portalHopLandsOnTheGroundWhereHeLooks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = pegasus(helper, 3.5, 2.0, 0.5);
		p.setXRot(22f); // the floor about four blocks ahead
		Vec3 start = p.position();
		helper.assertTrue(PegasusMoves.hop(p), "hopped");
		Vec3 now = p.position();
		helper.assertTrue(now.distanceTo(start) > 2.5 && now.z > start.z, "he went forward, " + now.distanceTo(start));
		helper.assertTrue(Math.abs(now.y - start.y) < 0.01, "standing on the floor, y " + now.y);
		helper.assertTrue(PegasusTeleport.fits(helper.getLevel(), p, now), "not in a block");
		helper.assertTrue(portals(helper, p).size() == 2, "a portal at each end");
		helper.assertTrue(!Miraculous.ready(p, PegasusConfig.HOP), "cooldown");
		helper.succeed();
	}

	/** Safe landings: never into a wall, never over the void, never above the Nether roof. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void safeLandingRefusesWallsVoidAndTheNetherRoof(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = mock(helper, 3.5, 2.0, 3.5);
		// a solid 5x5x6 block of stone: no spot inside it or round its middle
		for (int x = 1; x <= 5; x++) {
			for (int y = 1; y <= 6; y++) {
				for (int z = 1; z <= 5; z++) {
					level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState(), 3);
				}
			}
		}
		helper.assertTrue(PegasusTeleport.landing(level, p, helper.absoluteVec(new Vec3(3.5, 3.0, 3.5)), 2) == null, "inside stone: refused");
		// open air with nothing under it for far more than the search: refused (the void)
		Vec3 high = new Vec3(p.getX() + 400, level.getMaxBuildHeight() - 5, p.getZ() + 400);
		level.getChunk(BlockPos.containing(high));
		helper.assertTrue(PegasusTeleport.landing(level, p, high, 0) == null || PegasusTeleport.landing(level, p, high, 0).y < high.y - 1.0,
				"never left hanging in the open sky");
		// an unloaded chunk: refused
		Vec3 far = new Vec3(p.getX() + 20000, 80, p.getZ() + 20000);
		helper.assertTrue(!PegasusTeleport.fits(level, p, far), "an unloaded chunk is never a destination");
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		if (nether != null) {
			helper.assertTrue(PegasusTeleport.ceiling(nether) <= 127, "the Nether's ceiling is under its bedrock roof: " + PegasusTeleport.ceiling(nether));
			BlockPos roof = new BlockPos(0, 128, 0);
			nether.getChunk(roof);
			helper.assertTrue(!PegasusTeleport.fits(nether, p, Vec3.atBottomCenterOf(roof)), "never on top of the Nether roof");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void gallopTogglesSpeedThree(GameTestHelper helper) {
		ServerPlayer p = pegasus(helper, 3.5, 2.0, 3.5);
		helper.assertTrue(PegasusMoves.gallop(p), "on");
		MobEffectInstance s = p.getEffect(MobEffects.MOVEMENT_SPEED);
		helper.assertTrue(s != null && s.getAmplifier() == PegasusConfig.GALLOP_SPEED_AMPLIFIER, "Speed III");
		helper.assertTrue(Pegasus.galloping(p), "synced on");
		helper.assertTrue(!PegasusMoves.gallop(p), "off again");
		helper.assertTrue(!p.hasEffect(MobEffects.MOVEMENT_SPEED), "the speed goes");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void portalTrapDropsTheMobFromHighAbove(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = pegasus(helper, 3.5, 2.0, 0.5);
		// the test frame's barrier ceiling is opened up over the zombie: open sky
		for (int y = 2; y <= 26; y++) {
			helper.getLevel().setBlock(helper.absolutePos(new BlockPos(3, y, 6)), Blocks.AIR.defaultBlockState(), 3);
		}
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100, true);
		z.getNavigation().stop();
		double y0 = z.getY();
		pump(helper, p);
		helper.assertTrue(com.projecthero.mod.miraculous.pegasus.PegasusCombat.aimed(p, PegasusConfig.TRAP_RANGE, 5.0) == z, "aimed at the zombie");
		helper.assertTrue(PegasusTeleport.openAbove(helper.getLevel(), z, PegasusConfig.TRAP_HEIGHT, PegasusConfig.TRAP_MIN_HEIGHT) != null,
				"open sky above it");
		helper.assertTrue(PegasusMoves.trap(p) == z, "trapped");
		helper.assertTrue(z.getY() - y0 >= PegasusConfig.TRAP_MIN_HEIGHT, "it is high above now: +" + (z.getY() - y0));
		helper.assertTrue(z.getY() - y0 <= PegasusConfig.TRAP_HEIGHT + 0.01, "at most 20 up");
		helper.assertTrue(portals(helper, p).size() == 2, "a portal under it and one above");
		helper.assertTrue(PegasusTeleport.fits(helper.getLevel(), z, z.position()), "not in a block");
		helper.succeedWhen(() -> helper.assertTrue(z.onGround() && z.getHealth() < 100f - 5f, "it fell and took the fall damage: " + z.getHealth()));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void portalTrapNeverTakesABoss(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = pegasus(helper, 3.5, 2.0, 0.5);
		Zombie boss = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 400, false);
		double y0 = boss.getY();
		helper.assertTrue(PegasusMoves.trap(p) == null, "refused");
		helper.assertTrue(Math.abs(boss.getY() - y0) < 0.01, "the boss stays put");
		helper.assertTrue(Miraculous.ready(p, PegasusConfig.TRAP), "no cooldown spent");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void voyageMenuOpensAPortalAnyoneCanWalkThrough(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = pegasus(helper, 3.5, 2.0, 1.5);
		List<PegasusMoves.Target> menu = PegasusMoves.voyage(p);
		helper.assertTrue(menu.stream().anyMatch(t -> t.kind() == PegasusVoyageMenuPayload.WORLD_SPAWN), "the world spawn is offered");
		helper.assertTrue(Miraculous.ready(p, PegasusConfig.VOYAGE), "opening the menu costs nothing");
		Vec3 dest = helper.absoluteVec(new Vec3(6.5, 2.0, 6.5));
		helper.assertTrue(PegasusMoves.openVoyage(p, helper.getLevel(), dest, Component.literal("test")), "opened");
		helper.assertTrue(Miraculous.countdownLeft(p) > 0, "the Miraculous countdown started");
		helper.assertTrue(!Miraculous.ready(p, PegasusConfig.VOYAGE), "on cooldown");
		List<PegasusPortalEntity> ps = portals(helper, p);
		helper.assertTrue(ps.size() == 2 && ps.stream().allMatch(PegasusPortalEntity::transports), "two linked portals");
		PegasusPortalEntity src = ps.stream().min((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p))).get();
		Vec3 exit = src.exitFeet();
		ItemEntity item = new ItemEntity(helper.getLevel(), src.getX(), src.getY(), src.getZ(), new ItemStack(Items.SUGAR));
		item.setNoGravity(true);
		item.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(item);
		helper.succeedWhen(() -> helper.assertTrue(item.position().distanceTo(exit) < 1.5, "the item came out of the far portal"));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void voyageRefusesAnUnsafeDestination(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = pegasus(helper, 3.5, 2.0, 1.5);
		Vec3 deep = new Vec3(p.getX() + 3000, helper.getLevel().getMinBuildHeight() + 2, p.getZ() + 3000); // bedrock / deepslate
		boolean ok = PegasusMoves.openVoyage(p, helper.getLevel(), deep, Component.literal("rock"));
		if (ok) {
			// some worlds have open caves there: then it must at least have put the far end somewhere safe
			for (PegasusPortalEntity e : portals(helper, p)) {
				helper.assertTrue(e.exitFeet() == null || PegasusTeleport.landing(helper.getLevel(), p, e.exitFeet(), 0) != null, "a safe exit");
			}
		} else {
			helper.assertTrue(Miraculous.ready(p, PegasusConfig.VOYAGE), "refused: no cooldown spent");
			helper.assertTrue(Miraculous.countdownLeft(p) == 0, "and no countdown");
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- V

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void portalShieldSendsAnArrowBackAtTheShooter(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = pegasus(helper, 3.5, 2.0, 0.5);
		Zombie shooter = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 7.5, 100, false);
		pump(helper, p);
		helper.assertTrue(PegasusMoves.shield(p), "the shield is up");
		helper.assertTrue(portals(helper, p).size() == 2, "the shield and its exit");
		Arrow arrow = new Arrow(helper.getLevel(), shooter, new ItemStack(Items.ARROW), null);
		Vec3 eye = p.getEyePosition();
		arrow.setPos(eye.x, eye.y - 0.3, eye.z + 4.5);
		arrow.setDeltaMovement(0, 0, -1.5);
		arrow.setNoGravity(true);
		helper.getLevel().addFreshEntity(arrow);
		helper.succeedWhen(() -> {
			helper.assertTrue(arrow.getOwner() == p, "swallowed: the arrow is his now");
			Vec3 to = shooter.position().subtract(arrow.position());
			helper.assertTrue(arrow.getDeltaMovement().dot(to) > 0, "flying back at the shooter");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void skyGallopHoldsHimUpForTenSeconds(GameTestHelper helper) {
		ServerPlayer p = pegasus(helper, 3.5, 2.0, 3.5);
		pump(helper, p);
		PegasusMoves.sky(p);
		helper.assertTrue(Pegasus.skyGalloping(p), "running on air");
		helper.assertTrue(p.getAbilities().mayfly && !p.getAbilities().flying, "no floating kick (mayfly, never flying)");
		helper.runAfterDelay(PegasusConfig.SKY_TICKS + 4, () -> {
			helper.assertTrue(!Pegasus.skyGalloping(p), "over after 10 s");
			helper.assertTrue(!p.getAbilities().mayfly, "mayfly handed back");
			helper.assertTrue(!Miraculous.ready(p, PegasusConfig.SKY), "its cooldown started");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE)
	public void waypointsKeepThreeAndOutlastDetransformAndSaving(GameTestHelper helper) {
		ServerPlayer p = pegasus(helper, 1.5, 2.0, 1.5);
		PegasusWaypoints w = PegasusWaypoints.get(p.server);
		w.clear(p.getUUID());
		int[] slots = new int[4];
		for (int i = 0; i < 4; i++) {
			Vec3 at = helper.absoluteVec(new Vec3(1.5 + i, 2.0, 1.5));
			p.teleportTo(at.x, at.y, at.z);
			ready(helper, p);
			// distinct save times so "oldest" is well defined
			slots[i] = w.add(p.getUUID(), new PegasusWaypoints.Waypoint(p.level().dimension().location().toString(), at.x, at.y, at.z, 0f, 1000L + i));
		}
		helper.assertTrue(slots[3] == 1, "the fourth replaces the oldest (slot 1), got " + slots[3]);
		List<PegasusWaypoints.Waypoint> list = w.of(p.getUUID());
		helper.assertTrue(list.size() == PegasusConfig.WAYPOINT_SLOTS, "three kept");
		helper.assertTrue(list.stream().noneMatch(x -> x.savedAt() == 1000L), "the oldest went");
		// the move itself
		ready(helper, p);
		int slot = PegasusMoves.markWaypoint(p);
		helper.assertTrue(slot >= 1 && slot <= 3, "Mark Waypoint saved into a slot");
		// detransforming forgets the kit's values -- not the waypoints
		Miraculous.detransform(p, false);
		helper.assertTrue(w.of(p.getUUID()).size() == 3, "still there after detransforming");
		// saved with the world: write it out and read it back
		CompoundTag tag = w.save(new CompoundTag(), helper.getLevel().registryAccess());
		PegasusWaypoints back = PegasusWaypoints.load(tag, helper.getLevel().registryAccess());
		helper.assertTrue(back.of(p.getUUID()).equals(w.of(p.getUUID())), "the same after a save / load");
		helper.assertTrue(back.of(UUID.randomUUID()).isEmpty(), "nobody else's");
		// the Voyage menu offers them
		Miraculous.edit(p, n -> n.hungry = false);
		Miraculous.transform(p);
		ready(helper, p);
		long n = PegasusMoves.destinations(p).stream().filter(t -> t.kind() == PegasusVoyageMenuPayload.WAYPOINT).count();
		helper.assertTrue(n == 3, "all three in the Voyage menu, got " + n);
		w.clear(p.getUUID());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void linkedPortalsCarryWhateverPassesThrough(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = pegasus(helper, 3.5, 2.0, 0.5);
		p.setXRot(28f); // B on the floor a few blocks ahead
		PegasusPortalEntity[] ab = PegasusMoves.link(p);
		helper.assertTrue(ab != null, "both portals open");
		helper.assertTrue(ab[0].transports() && ab[1].transports(), "linked");
		helper.assertTrue(ab[1].distanceTo(ab[0]) >= 2.5, "B is out where he looked");
		helper.assertTrue(Pegasus.running(p, Pegasus.V_LINK), "timer running");
		Vec3 exit = ab[0].exitFeet();
		ItemEntity item = new ItemEntity(helper.getLevel(), ab[0].getX(), ab[0].getY(), ab[0].getZ(), new ItemStack(Items.SUGAR));
		item.setNoGravity(true);
		item.setDeltaMovement(Vec3.ZERO);
		helper.getLevel().addFreshEntity(item);
		helper.succeedWhen(() -> helper.assertTrue(item.position().distanceTo(exit) < 1.5, "the item came out of B"));
	}

	// ---------------------------------------------------------------- clean-up

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void detransformingClosesEveryPortalAndCallsTheHorseshoeBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = pegasus(helper, 3.5, 2.0, 0.5);
		p.setXRot(28f);
		helper.assertTrue(PegasusMoves.link(p) != null, "linked portals");
		helper.assertTrue(PegasusMoves.shield(p), "and the shield");
		helper.assertTrue(PegasusMoves.throwShoe(p) != null, "and the Horseshoe out");
		helper.assertTrue(PegasusMoves.gallop(p), "and galloping");
		Miraculous.detransform(p, false);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(portals(helper, p).isEmpty(), "every portal gone");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(HorseshoeEntity.class, p.getBoundingBox().inflate(64), h -> h.ownerEntityId() == p.getId())
					.isEmpty(), "the Horseshoe gone");
			helper.assertTrue(!p.hasEffect(MobEffects.MOVEMENT_SPEED), "the gallop's speed gone");
			helper.assertTrue(!Pegasus.shoeOut(p) && !Pegasus.galloping(p), "his values cleared");
			helper.succeed();
		});
	}
}
