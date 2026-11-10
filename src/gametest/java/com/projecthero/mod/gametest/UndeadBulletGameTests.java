package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadAbilities;
import com.projecthero.mod.undead.UndeadAbilityManager;
import com.projecthero.mod.undead.UndeadBulletConfig;
import com.projecthero.mod.undead.UndeadHeadThrow;
import com.projecthero.mod.undead.UndeadStages;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadCloneEntity;
import com.projecthero.mod.undead.entity.UndeadPartEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Undead's rework of R (Parts Bullet: Finger / Hand / Arm), hold X (Head Throw) and Shift+C (Division Bullet): the
 * numbers, the key flow (one shot a press, the arm at one second), the 60-block range, the head's flight holding the regrowth
 * until it touches a block (or right click), the trajectory prediction through a 1-block gap, and the guard-dog clone. Mock
 * players are not ticked by the server, so tests over time drive {@link Undead#tick}.
 */
public class UndeadBulletGameTests implements FabricGameTest {
	private static final AbilitySlot R = AbilitySlot.SLOT_1;
	private static final AbilitySlot C = AbilitySlot.SLOT_6;

	private static ServerPlayer undead(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.setYHeadRot(0f);
		p.yBodyRot = 0f;
		Undead.grant(p);
		return p;
	}

	private static Zombie zombie(GameTestHelper helper, int x, int z) {
		return zombie(helper, x, 2, z);
	}

	/** A still zombie with no armour (its natural 2 points would shave the numbers), 200 HP. */
	private static Zombie zombie(GameTestHelper helper, int x, int y, int z) {
		Zombie zo = helper.spawn(EntityType.ZOMBIE, new BlockPos(x, y, z));
		zo.setNoAi(true);
		zo.getAttribute(Attributes.ARMOR).setBaseValue(0);
		zo.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CARVED_PUMPKIN)); // no sunburn
		zo.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		zo.setHealth(200);
		return zo;
	}

	/**
	 * The test area is walled and roofed with barriers a few blocks out, so long shots are fired from well above it (a mock player
	 * is not moved by physics, and a no-AI zombie hangs where it is put).
	 */
	private static final int HIGH = 40;

	private static List<UndeadPartEntity> parts(GameTestHelper helper, ServerPlayer p, int style) {
		return helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, new AABB(p.blockPosition()).inflate(80), e -> e.ownerIs(p) && e.style() == style);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void bulletNumbers(GameTestHelper helper) {
		helper.assertTrue(UndeadBulletConfig.FINGER_DAMAGE == 7f && UndeadBulletConfig.HAND_DAMAGE == 12f && UndeadBulletConfig.ARM_DAMAGE == 20f,
				"Finger 7, Hand 12, Arm 20");
		helper.assertTrue(UndeadBulletConfig.RANGE == 60.0, "60 block range");
		helper.assertTrue(UndeadBulletConfig.ARM_HOLD == 20, "hold R one second for the arm");
		for (String id : new String[] { UndeadAbilities.PARTS_BULLET, UndeadAbilities.PARTS_BULLET_HAND, UndeadAbilities.PUNCH }) {
			helper.assertTrue(UndeadAbilityManager.maxCooldown(id) == 0, "no cooldown: " + id);
		}
		helper.assertTrue(UndeadAbilityManager.maxCooldown(UndeadAbilities.DIVISION) == 200, "Division Bullet: 10 s");
		helper.assertTrue(UndeadBulletConfig.CLONE_LIFE == 400 && UndeadBulletConfig.CLONE_DAMAGE == 8f && UndeadBulletConfig.CLONE_HEALTH == 30f,
				"clone: 20 s, 8 a hit, 30 HP");
		// the head's arc: 45 degrees over flat ground lands about 25 blocks away
		Vec3 pos = Vec3.ZERO;
		Vec3 v = new Vec3(0, 1, 1).normalize().scale(UndeadBulletConfig.HEAD_SPEED);
		for (int t = 0; t < 200 && (t == 0 || pos.y > 0); t++) {
			pos = pos.add(v);
			v = UndeadHeadThrow.step(v);
		}
		helper.assertTrue(pos.z > 23 && pos.z < 27, "the head flies about 25 blocks: " + pos.z);
		helper.succeed();
	}

	/** Tap R: one finger per tap, at once again (no cooldown); held, nothing streams out. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void tapRFiresOneFingerEachTapAndHoldingDoesNotStream(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setXRot(-85f);
		UndeadAbilityManager.handle(p, R, true);
		UndeadAbilityManager.handle(p, R, false);
		UndeadAbilityManager.handle(p, R, true);
		UndeadAbilityManager.handle(p, R, false);
		helper.assertTrue(parts(helper, p, UndeadPartEntity.STYLE_FINGER).size() == 2, "two taps, two fingers");
		helper.assertTrue(!Undead.state(p).anyBroken(), "nothing is used up");
		UndeadAbilityManager.handle(p, R, true);
		for (int t = 1; t < UndeadBulletConfig.ARM_HOLD; t += 3) {
			UndeadAbilityManager.forTestsHoldR(p, t);
		}
		helper.assertTrue(parts(helper, p, UndeadPartEntity.STYLE_FINGER).size() == 2, "a held R fires no stream of fingers");
		helper.assertTrue(Undead.state(p).flag(UndeadState.FLAG_PUNCH), "the arm is drawn back");
		UndeadAbilityManager.forTestsHoldR(p, UndeadBulletConfig.ARM_HOLD);
		helper.assertTrue(parts(helper, p, UndeadPartEntity.STYLE_PUNCH).size() == 1, "at one second the arm flies");
		UndeadAbilityManager.forTestsHoldR(p, UndeadBulletConfig.ARM_HOLD + 5);
		UndeadAbilityManager.handle(p, R, false);
		helper.assertTrue(parts(helper, p, UndeadPartEntity.STYLE_PUNCH).size() == 1 && parts(helper, p, UndeadPartEntity.STYLE_FINGER).size() == 2,
				"held on and let go: still one arm, no extra finger");
		helper.succeed();
	}

	/** Shift+R: the whole hand flies; the hand segment is gone (the arm stays) and the next one comes off the other hand. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shiftRFiresTheHand(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setXRot(-85f);
		p.setShiftKeyDown(true);
		UndeadAbilityManager.handle(p, R, true);
		List<UndeadPartEntity> hands = parts(helper, p, UndeadPartEntity.STYLE_HAND);
		helper.assertTrue(hands.size() == 1, "a hand flies");
		int seg = hands.get(0).bodyPart();
		helper.assertTrue(seg == UndeadState.RIGHT_HAND || seg == UndeadState.LEFT_HAND, "it is a hand segment: " + seg);
		helper.assertTrue(Undead.missing(p, seg) && Undead.state(p).kind(seg) == UndeadState.KIND_LAUNCHED, "that hand is gone, launched");
		helper.assertTrue(!Undead.missing(p, UndeadState.limbOf(seg)), "the arm stays on");
		UndeadAbilityManager.handle(p, R, false);
		UndeadAbilityManager.handle(p, R, true);
		List<UndeadPartEntity> two = parts(helper, p, UndeadPartEntity.STYLE_HAND);
		helper.assertTrue(two.size() == 2 && two.stream().map(UndeadPartEntity::bodyPart).distinct().count() == 2, "the second comes off the other hand");
		UndeadAbilityManager.handle(p, R, false);
		UndeadAbilityManager.handle(p, R, true);
		helper.assertTrue(parts(helper, p, UndeadPartEntity.STYLE_HAND).size() == 2, "no hands left: nothing (until they regrow)");
		p.setShiftKeyDown(false);
		helper.succeed();
	}

	/** Each bullet lands its damage on a zombie down range. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void eachBulletHitsForItsDamage(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Vec3 high = helper.absoluteVec(new Vec3(2.5, HIGH, 2.5));
		p.moveTo(high.x, high.y, high.z, 0f, 0f);
		p.setXRot(0f);
		Zombie z = zombie(helper, 2, HIGH, 12); // 10 blocks down range, the eye line through its head
		// each bullet stepped by hand (the test world may not tick the chunk it crosses)
		UndeadAbilities.partsBullet(p);
		fly(helper, p, UndeadPartEntity.STYLE_FINGER);
		helper.assertTrue(Math.abs(z.getHealth() - (200f - UndeadBulletConfig.FINGER_DAMAGE)) < 0.01f, "the finger: 7 (" + z.getHealth() + ")");
		z.invulnerableTime = 0;
		UndeadAbilities.handBullet(p);
		fly(helper, p, UndeadPartEntity.STYLE_HAND);
		helper.assertTrue(Math.abs(z.getHealth() - (200f - 19f)) < 0.01f, "the hand: 12 (" + z.getHealth() + ")");
		z.invulnerableTime = 0;
		Undead.restore(p);
		UndeadAbilities.startPunch(p);
		UndeadAbilities.throwPunch(p);
		fly(helper, p, UndeadPartEntity.STYLE_PUNCH);
		helper.assertTrue(Math.abs(z.getHealth() - (200f - 39f)) < 0.01f, "the arm: 20 (" + z.getHealth() + ")");
		helper.succeed();
	}

	/** Steps his flying pieces of that style 10 ticks. */
	private static void fly(GameTestHelper helper, ServerPlayer p, int style) {
		for (int t = 0; t < 10; t++) {
			for (UndeadPartEntity e : parts(helper, p, style)) {
				if (e.inFlight()) {
					e.tick();
				}
			}
		}
	}

	/** Fired level into open air a bullet is gone 60 blocks out, and it never drops. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void bulletsGiveOutAt60Blocks(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Vec3 high = helper.absoluteVec(new Vec3(2.5, HIGH, 2.5));
		p.moveTo(high.x, high.y, high.z, 0f, 0f);
		p.setXRot(0f);
		UndeadAbilities.partsBullet(p);
		UndeadPartEntity f = parts(helper, p, UndeadPartEntity.STYLE_FINGER).get(0);
		Vec3 from = f.position();
		double[] far = { 0, 0 };
		// stepped by hand: 60 blocks out it would leave the test world's ticking chunks and freeze there
		for (int t = 0; t < 40 && !f.isRemoved(); t++) {
			f.tick();
			if (!f.isRemoved()) {
				far[0] = Math.max(far[0], f.position().distanceTo(from));
				far[1] = Math.max(far[1], Math.abs(f.getY() - from.y));
			}
		}
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(f.isRemoved(), "gone");
			helper.assertTrue(far[0] > 55 && far[0] <= UndeadBulletConfig.RANGE + UndeadBulletConfig.FINGER_SPEED + 0.01, "it flew 60 blocks: " + far[0]);
			helper.assertTrue(far[1] < 1.5, "dead straight, no drop: " + far[1]);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the Head Throw

	/** Hold X: aiming roots him; letting go leaves the body and the head flies with nothing growing until it touches a block. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void theHeadThrowWaitsForABlockToRegrow(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setXRot(-30f);
		UndeadHeadThrow.windUp(p, 20);
		helper.assertTrue(UndeadHeadThrow.ripping(p), "the struggle shows");
		helper.assertTrue(UndeadHeadThrow.start(p), "the head comes off at 2 s");
		helper.assertTrue(UndeadHeadThrow.aiming(p), "aiming");
		helper.assertTrue(p.getAttributeValue(Attributes.JUMP_STRENGTH) < 1.0e-6, "and rooted (no jump; the client holds the movement keys still)");
		helper.assertTrue(!Undead.missing(p, UndeadState.HEAD) && !Undead.missing(p, UndeadState.BODY), "nothing breaks while it is held");
		UndeadHeadThrow.tickHeld(p);
		Vec3 head = UndeadHeadThrow.heldHeadPos(p, 1f);
		UndeadHeadThrow.release(p);
		helper.assertTrue(!UndeadHeadThrow.aiming(p) && p.getAttributeValue(Attributes.JUMP_STRENGTH) > 0.01, "thrown: no longer rooted");
		helper.assertTrue(!Undead.missing(p, UndeadState.HEAD), "he keeps the head (he is it)");
		for (int i = UndeadState.BODY; i < UndeadState.SEGS; i++) {
			helper.assertTrue(Undead.missing(p, i), "everything else is left behind: " + i);
		}
		helper.assertTrue(p.position().distanceTo(head.subtract(0, UndeadHeadThrow.HEAD_SIZE * 0.5, 0)) < 0.01, "he is moved to the held head");
		helper.assertTrue(UndeadHeadThrow.flying(p) && UndeadHeadThrow.holdsRegen(p), "in the air, regrowth held");
		helper.assertTrue(p.getDeltaMovement().length() > UndeadBulletConfig.HEAD_SPEED * 0.9, "thrown along the look");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, new AABB(p.blockPosition()).inflate(8), e -> e.isLeftBehind() && e.ownerIs(p))
				.size() >= 5, "the body stays where he stood");
		p.setOnGround(false);
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(UndeadHeadThrow.flying(p), "still in the air");
			helper.assertTrue(Undead.state(p).regrowStart(UndeadState.BODY) <= 0L, "nothing grows while the head is in the air");
			helper.assertTrue(UndeadStages.stage(Undead.state(p), helper.getLevel().getGameTime()) == UndeadStages.ROLL, "just a head: the half-block box");
			helper.assertTrue(UndeadStages.dims(UndeadStages.ROLL).width() <= 0.5f + 1.0e-4f, "small enough for a 1-block gap");
			p.setOnGround(true); // it lands
		});
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(!UndeadHeadThrow.flying(p) && !UndeadHeadThrow.holdsRegen(p), "touching a block ends the flight");
			helper.assertTrue(Undead.state(p).regrowStart(UndeadState.BODY) > 0L, "and the body starts growing back around the head");
			helper.succeed();
		});
	}

	/** Right click mid-air starts the regrowth early; the head keeps flying. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void rightClickMidAirRegrowsEarly(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setXRot(-30f);
		UndeadHeadThrow.start(p);
		UndeadHeadThrow.release(p);
		p.setOnGround(false);
		helper.onEachTick(() -> Undead.tick(p));
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(Undead.state(p).regrowStart(UndeadState.BODY) <= 0L, "held while flying");
			UndeadHeadThrow.regrowNow(p);
			helper.assertTrue(UndeadHeadThrow.flying(p) && !UndeadHeadThrow.holdsRegen(p), "still flying, regrowth allowed");
		});
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(Undead.state(p).regrowStart(UndeadState.BODY) > 0L, "the body grows mid-air");
			helper.succeed();
		});
	}

	/** Let go of the hold early, or a cancel: nothing comes off and he can walk. No body: no throw. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void cancellingTheAimPutsTheHeadBack(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		UndeadHeadThrow.start(p);
		UndeadHeadThrow.cancel(p);
		helper.assertTrue(!UndeadHeadThrow.aiming(p) && !Undead.state(p).anyBroken(), "cancelled: nothing broken");
		helper.assertTrue(p.getAttributeValue(Attributes.JUMP_STRENGTH) > 0.01, "and free to move");
		UndeadHeadThrow.release(p);
		helper.assertTrue(!UndeadHeadThrow.flying(p), "a release without an aim throws nothing");
		Undead.breakPart(p, UndeadState.BODY, UndeadState.KIND_BURST);
		helper.assertTrue(!UndeadHeadThrow.start(p), "no body, no throw");
		helper.succeed();
	}

	/** The trajectory line's prediction goes through a 1-block hole and stops at a wall. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePredictedArcFitsThroughAOneBlockGap(GameTestHelper helper) {
		// a 3x3 wall 2.5 blocks ahead with a 1-block hole in the middle (x 2, y 3)
		for (int x = 1; x <= 3; x++) {
			for (int y = 2; y <= 4; y++) {
				if (x != 2 || y != 3) {
					helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE.defaultBlockState());
				}
			}
		}
		// a low lob: the head's box stays between y 3.25 and 3.9, inside the hole's 3..4
		Vec3 start = helper.absoluteVec(new Vec3(2.5, 3.5, 1.5));
		boolean[] landed = new boolean[1];
		List<Vec3> through = UndeadHeadThrow.predict(helper.getLevel(), start, new Vec3(0, 0.1, 1.0), 5, landed);
		double wallBack = helper.absoluteVec(new Vec3(0, 0, 5)).z;
		helper.assertTrue(through.stream().anyMatch(v -> v.z > wallBack + 0.2), "the arc passes the hole: " + through.get(through.size() - 1));
		Vec3 beside = helper.absoluteVec(new Vec3(1.5, 3.5, 1.5));
		List<Vec3> blocked = UndeadHeadThrow.predict(helper.getLevel(), beside, new Vec3(0, 0.1, 1.0), 5, landed);
		double wallFront = helper.absoluteVec(new Vec3(0, 0, 4)).z;
		Vec3 end = blocked.get(blocked.size() - 1);
		helper.assertTrue(landed[0] && end.z < wallFront && end.z > wallFront - 0.6, "beside the hole it ends against the wall: " + end + " / " + wallFront);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Division Bullet

	/** Shift+C: one finger, 10 s cooldown; where it lands a clone with 30 HP and 20 s grows. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shiftCFiresTheDivisionFinger(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		p.setXRot(-85f);
		p.setShiftKeyDown(true);
		UndeadAbilityManager.handle(p, C, true);
		UndeadAbilityManager.handle(p, C, false);
		helper.assertTrue(parts(helper, p, UndeadPartEntity.STYLE_DIVISION).size() == 1, "a finger flies");
		helper.assertTrue(Undead.cooldownRemaining(p, UndeadAbilities.DIVISION) == UndeadBulletConfig.DIVISION_COOLDOWN, "10 s cooldown");
		UndeadAbilityManager.handle(p, C, true);
		helper.assertTrue(parts(helper, p, UndeadPartEntity.STYLE_DIVISION).size() == 1, "not again until it is up");
		p.setShiftKeyDown(false);
		UndeadCloneEntity c = UndeadAbilities.divisionClone(p, p.position().add(2, 0, 0));
		helper.assertTrue(c != null && c.getMaxHealth() == 30f && c.getHealth() == 30f, "30 HP");
		helper.assertTrue(c.lifeLeft() == UndeadBulletConfig.CLONE_LIFE, "20 s");
		helper.assertTrue(!com.projecthero.mod.combat.HeroTargets.canHarm(p, c), "his own moves pass through it");
		c.discard();
		helper.succeed();
	}

	/** The clone goes for whatever hurt its owner and hits for 8; never for the owner. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void theCloneGuardsItsOwner(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		Zombie z = zombie(helper, 2, 6);
		UndeadCloneEntity c = UndeadAbilities.divisionClone(p, p.position().add(1, 0, 0));
		helper.assertTrue(!c.mayTarget(p, p), "never its owner");
		helper.assertTrue(c.threat(p) == null, "at peace it hunts nothing");
		p.setLastHurtByMob(z);
		helper.assertTrue(c.threat(p) == z, "what hurt the owner is the threat");
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(c.getTarget() == z, "and it goes for it");
			float before = z.getHealth();
			z.invulnerableTime = 0;
			c.doHurtTarget(z);
			helper.assertTrue(Math.abs(before - z.getHealth() - UndeadBulletConfig.CLONE_DAMAGE) < 0.01f, "8 a hit: " + (before - z.getHealth()));
			c.discard();
			helper.succeed();
		});
	}

	/** It goes when its time is up, and can be killed. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40)
	public void theCloneRunsOutOrDies(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		UndeadCloneEntity brief = UndeadCloneEntity.spawn(p, p.getX() + 1, p.getY(), p.getZ(), 5, UndeadBulletConfig.CLONE_DAMAGE);
		UndeadCloneEntity doomed = UndeadAbilities.divisionClone(p, p.position().add(-1, 0, 0));
		Zombie z = zombie(helper, 4, 2);
		doomed.hurt(helper.getLevel().damageSources().mobAttack(z), 31f);
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(brief.isRemoved(), "its time ran out");
			helper.assertTrue(doomed.isRemoved(), "30 HP: a 31 hit kills it");
			helper.succeed();
		});
	}
}
