package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.galactus.Galactus;
import com.projecthero.mod.galactus.GalactusEvent;
import com.projecthero.mod.galactus.entity.GalactusEntityTypes;
import com.projecthero.mod.galactus.orbit.DevourerEntity;
import com.projecthero.mod.galactus.orbit.DevourerPartEntity;
import com.projecthero.mod.galactus.orbit.GalactusOrbit;
import com.projecthero.mod.galactus.orbit.StageIslands;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4: the Galactus raid fixes ("I can't hit the hand"). The orbit dimension is not in the gametest server, so these
 * drive the pieces in the overworld: the Devourer's hit-boxes are where they should be and only the chosen ones are out,
 * area searches find a hit-box much bigger than an entity section (the crosshair pick, arrows, powers), a real
 * {@code Player.attack} on a stuck fist fills the Stagger bar, and the orbit grave leaves a saving totem alone. The full
 * raid (orbit, every stage, the real client attack key) is proven with a client run.
 */
public class GalactusRaidFixGameTests implements FabricGameTest {
	/** A Devourer standing on the test floor (feet at the structure's floor). */
	private static DevourerEntity devourer(GameTestHelper helper, float height) {
		DevourerEntity d = GalactusEntityTypes.DEVOURER.create(helper.getLevel());
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		d.setHeight(height);
		d.moveToFeet(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f);
		helper.getLevel().addFreshEntity(d);
		// both fists reach in over the test floor (their chunks tick; parts are only made in ticking chunks)
		Vec3 over = new Vec3(at.getX() + 0.5, at.getY() + 24, at.getZ() + 0.5);
		d.setArm(true, over.add(-4, 0, 0), 1f);
		d.setArm(false, over.add(4, 0, 0), 1f);
		// his fists hang ~20 blocks either side of him: keep those chunks entity-ticking (parts are only made where they are)
		for (net.minecraft.world.phys.Vec3 v : new net.minecraft.world.phys.Vec3[] { d.pose().fistR, d.pose().fistL, d.pose().chest }) {
			helper.getLevel().setChunkForced(net.minecraft.core.SectionPos.blockToSectionCoord(v.x), net.minecraft.core.SectionPos.blockToSectionCoord(v.z), true);
		}
		return d;
	}

	private static void release(GameTestHelper helper, DevourerEntity d) {
		for (net.minecraft.world.phys.Vec3 v : new net.minecraft.world.phys.Vec3[] { d.pose().fistR, d.pose().fistL, d.pose().chest }) {
			helper.getLevel().setChunkForced(net.minecraft.core.SectionPos.blockToSectionCoord(v.x), net.minecraft.core.SectionPos.blockToSectionCoord(v.z), false);
		}
		d.discard();
	}

	private static void clearRaids(GameTestHelper helper) {
		for (GalactusEvent old : new java.util.ArrayList<>(GalactusEvent.all(helper.getLevel().getServer()))) {
			if (old.state().active()) {
				old.abort(helper.getLevel());
			}
			EventSavedData.get(helper.getLevel()).remove(old.id());
		}
	}

	/** The fist that is out (right first): parts are only made in entity-ticking chunks, and test chunks tick unevenly. */
	private static String diag(GameTestHelper helper, DevourerEntity d) {
		StringBuilder b = new StringBuilder("[removed=" + d.isRemoved() + " on=" + d.partsOn() + " tick=" + d.tickCount);
		for (DevourerPartEntity.Kind k : DevourerEntity.FISTS) {
			b.append(" ").append(k).append(helper.getLevel().isPositionEntityTicking(BlockPos.containing(k.centre(d.pose()))) ? ":ticking" : ":idle").append(d.part(k) != null ? "+part" : "");
		}
		return b.append("]").toString();
	}

	private static DevourerPartEntity anyHand(DevourerEntity d) {
		DevourerPartEntity r = d.part(DevourerPartEntity.Kind.HAND_R);
		return r != null ? r : d.part(DevourerPartEntity.Kind.HAND_L);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void onlyTheChosenHitBoxesAreOutAndTheyFollowTheFists(GameTestHelper helper) {
		DevourerEntity d = devourer(helper, 180f);
		d.setParts(DevourerEntity.FISTS);
		helper.startSequence().thenWaitUntil(() -> helper.assertTrue(anyHand(d) != null, "a fist hit-box comes out " + diag(helper, d))).thenExecute(() -> {
			for (DevourerPartEntity.Kind k : DevourerPartEntity.Kind.values()) {
				if (!DevourerEntity.FISTS.contains(k)) {
					helper.assertTrue(d.part(k) == null, k + " is out but should not be");
				}
			}
			DevourerPartEntity hand = anyHand(d);
			net.minecraft.world.phys.Vec3 fist = hand.kind() == DevourerPartEntity.Kind.HAND_R ? d.pose().fistR : d.pose().fistL;
			helper.assertTrue(hand.getBoundingBox().getCenter().distanceTo(fist) < 0.5, "the hand box is centred on the fist");
			helper.assertTrue(hand.getBbWidth() > 16f, "a 180-block Galactus has a fist wider than an entity section (" + hand.getBbWidth() + ")");
			d.setPartsOn(false);
		}).thenWaitUntil(() -> {
					for (DevourerPartEntity.Kind k : DevourerPartEntity.Kind.values()) {
						helper.assertTrue(d.part(k) == null, k + " was left behind");
					}
				}).thenExecute(() -> release(helper, d)).thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void areaSearchesAndTheCrosshairRayFindAFistFarFromItsPosition(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		DevourerEntity d = devourer(helper, 180f);
		d.setParts(DevourerEntity.FISTS);
		helper.startSequence().thenWaitUntil(() -> helper.assertTrue(anyHand(d) != null, "a fist hit-box comes out " + diag(helper, d))).thenExecute(() -> {
			DevourerPartEntity hand = anyHand(d);
			AABB box = hand.getBoundingBox();
			// a small search box just inside the top corner of the fist: its section is far from the entity's position
			Vec3 corner = new Vec3(box.maxX - 0.5, box.maxY - 0.5, box.maxZ - 0.5);
			helper.assertTrue(corner.distanceTo(hand.position()) > 20, "the corner is far from the position");
			AABB probe = new AABB(corner, corner).inflate(0.4);
			List<DevourerPartEntity> typed = level.getEntitiesOfClass(DevourerPartEntity.class, probe, e -> true);
			helper.assertTrue(typed.contains(hand), "getEntitiesOfClass finds the fist from its corner");
			List<Entity> plain = level.getEntities((Entity) null, probe, e -> true);
			helper.assertTrue(plain.contains(hand), "getEntities finds the fist from its corner");
			helper.assertTrue(plain.stream().filter(e -> e == hand).count() == 1, "found once, not twice");
			// the crosshair: a 3-block ray from just outside the fist's top face, straight down into it
			Vec3 eye = new Vec3(box.maxX - 1.0, box.maxY + 1.5, box.maxZ - 1.0);
			Vec3 end = eye.add(0, -3, 0);
			ServerPlayer p = helper.makeMockServerPlayerInLevel();
			p.moveTo(eye.x, eye.y - 1.62, eye.z, 0f, 90f);
			EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, p.getBoundingBox().expandTowards(0, -3, 0).inflate(1.0),
					e -> !e.isSpectator() && e.isPickable(), 9.0);
			helper.assertTrue(hit != null && hit.getEntity() instanceof DevourerPartEntity, "the pick ray lands on the fist");
			// a search nowhere near it finds nothing
			Vec3 away = new Vec3(box.maxX + 6, box.maxY + 6, box.maxZ + 6);
			helper.assertTrue(level.getEntitiesOfClass(DevourerPartEntity.class, new AABB(away, away).inflate(1), e -> true).isEmpty(),
					"no false hits outside the box");
			release(helper, d);
		}).thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "galactus_v0174_fist", timeoutTicks = 120)
	public void aRealPlayerAttackOnTheStuckFistFillsTheStaggerBar(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		clearRaids(helper);
		GalactusEvent e = Galactus.start(level, helper.absolutePos(new BlockPos(4, 2, 4)), null, Galactus.Source.COMMAND, x -> {
			x.instant = true;
			x.noGuards = true;
		});
		helper.assertTrue(e != null, "Galactus starts");
		e.debugSetPhase(GalactusEvent.Phase.STAGE1, level.getGameTime());
		DevourerEntity d = devourer(helper, 180f);
		d.bindToEvent(e.id());
		d.setParts(DevourerEntity.FISTS);
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		// bare-handed: a sword would sweep onto the neighbouring hit-boxes and blur what each hit did
		helper.startSequence().thenWaitUntil(() -> {
			DevourerPartEntity h = anyHand(d);
			helper.assertTrue(h != null, "a fist is out in Stage 1 " + diag(helper, d));
		}).thenExecute(() -> {
			DevourerPartEntity hand = anyHand(d);
			boolean right = hand.kind() == DevourerPartEntity.Kind.HAND_R;
			e.stage1.targetR = right ? 0 : -1;
			e.stage1.targetL = right ? -1 : 0;
			e.stage1.state = StageIslands.IDLE;
			p.attack(hand);
			helper.assertTrue(e.stage1.stagger == 0, "a fist that isn't stuck shrugs it off");
			e.stage1.state = StageIslands.STUCK;
			p.attack(hand);
			double after = e.stage1.stagger;
			helper.assertTrue(after > 0, "the stuck fist took the hit into the Stagger bar");
			DevourerPartEntity fore = d.part(right ? DevourerPartEntity.Kind.FORE_R : DevourerPartEntity.Kind.FORE_L);
			if (fore != null) {
				double b4 = e.stage1.stagger;
				p.attack(fore);
				helper.assertTrue(e.stage1.stagger > b4, "the stuck arm's forearm counts too");
			}
			DevourerPartEntity other = d.part(right ? DevourerPartEntity.Kind.HAND_L : DevourerPartEntity.Kind.HAND_R);
			if (other != null) {
				double before = e.stage1.stagger;
				p.attack(other);
				helper.assertTrue(e.stage1.stagger == before, "the free fist doesn't");
			}
			helper.assertTrue(hand.isAlive() && hand.getHealth() == hand.getMaxHealth(), "the hit-box itself never loses health");
			release(helper, d);
			e.abort(level);
			EventSavedData.get(level).remove(e.id());
		}).thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theOrbitGraveLeavesASavingTotemAlone(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		helper.assertFalse(GalactusOrbit.totemWillSave(p, level.damageSources().magic()), "no totem, no save");
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		helper.assertTrue(GalactusOrbit.totemWillSave(p, level.damageSources().magic()), "a totem in the off hand will fire");
		helper.assertFalse(GalactusOrbit.totemWillSave(p, level.damageSources().fellOutOfWorld()), "the void goes through totems");
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.17.5: one Board per raid

	private static ServerPlayer fresh(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.getInventory().clearContent();
		return p;
	}

	private static int boards(ServerPlayer p) {
		return p.getInventory().countItem(com.projecthero.mod.galactus.item.GalactusItems.SURFBOARD);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void onlyTheTopDamageDealerGetsTheOneBoard(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer a = fresh(helper), b = fresh(helper), c = fresh(helper);
		List<ServerPlayer> all = List.of(a, b, c);
		try {
			java.util.Map<java.util.UUID, Double> dmg = new java.util.HashMap<>();
			dmg.put(a.getUUID(), 10.0);
			dmg.put(b.getUUID(), 50.0);
			dmg.put(c.getUUID(), 30.0);
			ServerPlayer pick = com.projecthero.mod.galactus.GalactusRewards.boardCandidate(all, dmg, level.random);
			helper.assertTrue(pick == b, "the top damage dealer is offered the roll");
			helper.assertTrue(com.projecthero.mod.galactus.GalactusRewards.awardBoard(pick, all, level.random, 1.0), "a sure roll drops it");
			for (ServerPlayer p : all) {
				com.projecthero.mod.galactus.GalactusRewards.grantClear(p);
			}
			helper.assertTrue(boards(a) + boards(b) + boards(c) == 1 && boards(b) == 1, "exactly one Board, to the top damage dealer");
			helper.assertTrue(a.getInventory().countItem(com.projecthero.mod.galactus.item.GalactusItems.POWER_COSMIC) == 1,
					"every first clear still gets the Power Cosmic");
			// next raid: b already has one, so the chance passes to the next highest; a failed roll gives nobody anything
			ServerPlayer next = com.projecthero.mod.galactus.GalactusRewards.boardCandidate(all, dmg, level.random);
			helper.assertTrue(next == c, "a Board owner is skipped: the next highest gets the chance");
			helper.assertFalse(com.projecthero.mod.galactus.GalactusRewards.awardBoard(next, all, level.random, 0.0), "a failed roll");
			helper.assertTrue(boards(a) + boards(b) + boards(c) == 1, "a failed roll drops nothing for anyone");
			// a player who cleared before 0.17.5 (when every first clear got one) never gets another
			ServerPlayer old = fresh(helper);
			old.setAttached(com.projecthero.mod.galactus.GalactusRewards.CLEARS, 2);
			dmg.put(old.getUUID(), 999.0);
			helper.assertTrue(com.projecthero.mod.galactus.GalactusRewards.boardCandidate(List.of(old, c), dmg, level.random) == c,
					"a pre-0.17.5 clearer already had theirs");
			level.getServer().getPlayerList().remove(old);
		} finally {
			for (ServerPlayer p : all) {
				level.getServer().getPlayerList().remove(p);
			}
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.17.5: every player in the raid sees the cutscenes

	@GameTest(template = EMPTY_STRUCTURE, batch = "galactus_v0175_scenes", timeoutTicks = 60)
	public void theOpeningCutsceneReachesEveryoneInTheRaid(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		clearRaids(helper);
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		double arena = GalactusEvent.arena();
		ServerPlayer near = fresh(helper), edge = fresh(helper), watcher = fresh(helper);
		near.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5);
		edge.moveTo(at.getX() + arena + 30, at.getY(), at.getZ() + 0.5);       // outside the arena, inside the border's reach
		watcher.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + arena + 60);    // in the raid's area, beyond the border
		watcher.setGameMode(GameType.CREATIVE);                                // an onlooker, not a fighter
		List<ServerPlayer> ps = new java.util.ArrayList<>(List.of(near, edge, watcher));
		GalactusEvent e = Galactus.start(level, at, near, Galactus.Source.COMMAND);
		try {
			helper.assertTrue(e != null && e.phase() == GalactusEvent.Phase.ARRIVAL, "the raid opens on C1");
			for (ServerPlayer p : ps) {
				helper.assertTrue(com.projecthero.mod.galactus.cinematic.GalactusCinematics.held(p),
						"C1 was sent to " + p.getGameProfile().getName() + " (held for the scene)");
			}
			helper.assertTrue(e.isFighter(near.getUUID()) && e.isFighter(edge.getUUID()), "both survivors are in the fight");
			helper.assertFalse(e.isFighter(watcher.getUUID()), "the creative onlooker watches but does not fight");
			// someone arriving mid-scene gets it from where it is
			ServerPlayer late = fresh(helper);
			ps.add(late);
			late.moveTo(at.getX() - 6.5, at.getY(), at.getZ() + 0.5);
			helper.assertFalse(com.projecthero.mod.galactus.cinematic.GalactusCinematics.held(late), "not yet");
			e.catchUpOverworldScene(level);
			helper.assertTrue(com.projecthero.mod.galactus.cinematic.GalactusCinematics.held(late), "a late arrival is caught up");
		} finally {
			for (ServerPlayer p : ps) {
				com.projecthero.mod.galactus.cinematic.GalactusCinematics.release(p);
				level.getServer().getPlayerList().remove(p);
			}
			if (e != null) {
				if (e.state().active()) {
					e.abort(level);
				}
				EventSavedData.get(level).remove(e.id());
			}
		}
		helper.succeed();
	}
}
