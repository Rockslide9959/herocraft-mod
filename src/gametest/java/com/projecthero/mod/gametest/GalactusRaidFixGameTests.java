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
}
