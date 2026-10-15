package com.projecthero.mod.gametest;

import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkAbilities;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.HulkEarthWall;
import com.projecthero.mod.hulk.HulkGrab;
import com.projecthero.mod.hulk.HulkLeap;
import com.projecthero.mod.hulk.data.HulkState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user): the Hulk's rebuilt kit -- Super Leap distances and the drag-free flight, the Super Punch's cone (and
 * the blocks it smashes), the charged V throw (20 blocks for a tap, 200 at full charge), the four-impact Loki slam, the debris made of the ground under him and
 * the Earth Wall. Mock players are not ticked by the server (and never move), so each test drives {@link Hulk#tick}
 * itself and the flights are checked against the same per-tick step the client runs.
 */
public class HulkMovesV0164GameTests implements FabricGameTest {
	/** A Gamma player already out as the Hulk, standing in a cleared room facing +Z. */
	private static ServerPlayer hulk(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-4, 0, -6), base.offset(4, 6, 9))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		helper.assertTrue(Hulk.grant(p), "the Gamma power is granted");
		Hulk.setRage(p, 100.0f);
		Hulk.tryTransform(p);
		helper.assertTrue(Hulk.isHulk(p), "precondition: Hulk");
		Hulk.setRage(p, 30.0f);
		// whichever change it was, it is over: abilities are allowed
		HulkState n = Hulk.state(p).copy();
		n.formChangedAt = p.level().getGameTime() - 20 * 60 * 5;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.HULK_STATE, n);
		return p;
	}

	private static Husk mob(GameTestHelper helper, Vec3 at, double health) {
		Husk z = EntityType.HUSK.create(helper.getLevel());
		z.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		z.setNoAi(true);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		z.setHealth((float) health);
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	private static void look(ServerPlayer p, net.minecraft.world.entity.Entity e) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, e.position().add(0, e.getBbHeight() * 0.5, 0));
		p.setYHeadRot(p.getYRot()); // a living thing aims along its HEAD yaw (getViewVector)
	}

	// ---------------------------------------------------------------- X Super Leap

	@GameTest(template = EMPTY_STRUCTURE)
	public void leapChargeGivesTwentyFiveHundredAndFiveHundredBlocks(GameTestHelper helper) {
		HulkConfig.Abilities c = HulkConfig.abilities();
		helper.assertTrue(Math.abs(HulkLeap.blocksFor(0.0f) - c.superLeapTapBlocks) < 0.01, "a tap is 25 blocks");
		helper.assertTrue(Math.abs(HulkLeap.blocksFor(0.5f) - c.superLeapHalfBlocks) < 0.01, "half charge is 100");
		helper.assertTrue(Math.abs(HulkLeap.blocksFor(1.0f) - c.superLeapFullBlocks) < 0.01, "full charge is 500");
		for (Vec3 look : new Vec3[] { new Vec3(0, 0, 1), new Vec3(1, 1, 0).normalize(), new Vec3(0.3, -0.5, -0.8).normalize() }) {
			for (float charge : new float[] { 0.0f, 0.5f, 1.0f }) {
				double want = HulkLeap.blocksFor(charge);
				Vec3 v = HulkLeap.launchVelocity(look, want, 0.08);
				double got = HulkLeap.simulateRange(v, 0.08, 2000);
				helper.assertTrue(Math.abs(got - want) <= want * 0.08 + 1.0,
						"a " + want + "-block leap lands " + Math.round(got) + " blocks away (look " + look + ")");
				helper.assertTrue(Math.sqrt(v.x * v.x + v.z * v.z) <= c.superLeapMaxSpeed + 1.0e-6, "within the speed cap");
				helper.assertTrue(v.y / 0.08 < 80.0, "climbs for under 80 ticks (the server's floating check)");
				// heading along the look
				Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
				helper.assertTrue(new Vec3(v.x, 0, v.z).normalize().dot(flat) > 0.999, "along the look direction");
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void leapKeepsItsSpeedUntilItLands(GameTestHelper helper) {
		Vec3 v = HulkLeap.launchVelocity(new Vec3(0, 0, 1), 500.0, 0.08);
		HulkState.Moves m = new HulkState.Moves();
		m.leapVx = v.x;
		m.leapVy = v.y;
		m.leapVz = v.z;
		Vec3 vel = v;
		for (int t = 0; t < 60; t++) {
			Vec3 vanilla = new Vec3(vel.x * 0.91, (vel.y - 0.08) * 0.98, vel.z * 0.91); // vanilla's airborne step
			Vec3 next = HulkLeap.cancelDrag(vanilla, m);
			helper.assertTrue(Math.abs(next.z - v.z) < 1.0e-9 && Math.abs(next.x - v.x) < 1.0e-9, "no horizontal slow-down at tick " + t);
			helper.assertTrue(Math.abs((vel.y - next.y) - 0.08) < 1.0e-9, "only gravity on the climb/fall at tick " + t);
			vel = next;
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_v0164_leap")
	public void releasingXLaunchesOnTheSolvedArc(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		p.setOnGround(true);
		p.setXRot(0.0f);
		HulkAbilities.beginLeap(p);
		helper.assertTrue(Hulk.state(p).leapChargeStart > 0L, "holding X charges");
		HulkAbilities.releaseLeap(p);
		HulkState s = Hulk.state(p);
		helper.assertTrue(s.leaping, "releasing launches him");
		helper.assertTrue(s.animId == HulkState.ANIM_LEAP_LAUNCH, "with the launch animation");
		Vec3 want = HulkLeap.launchVelocity(p.getLookAngle(), HulkLeap.blocksFor(0.0f), p.getGravity());
		helper.assertTrue(Math.abs(s.moves.leapVx - want.x) < 1.0e-6 && Math.abs(s.moves.leapVz - want.z) < 1.0e-6,
				"the synced launch velocity is the solved one");
		helper.assertTrue(p.getDeltaMovement().distanceTo(want) < 1.0e-6, "and he leaves with it");
		helper.assertTrue(HulkAbilities.cooldownRemaining(p, HulkAbilities.SUPER_LEAP) > 0, "the cooldown starts");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R Super Punch

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "hulk_v0164_punch")
	public void superPunchHitsTheConeOnly(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		var front = mob(helper, p.position().add(0, 0, 5), 200.0);
		var side = mob(helper, p.position().add(5, 0, 0.5), 200.0);
		var behind = mob(helper, p.position().add(0, 0, -4), 200.0);
		var far = mob(helper, p.position().add(0, 0, 8.0 + p.getBbHeight()), 200.0);
		look(p, front);
		HulkAbilities.superPunch(p);
		helper.assertTrue(HulkAbilities.cooldownRemaining(p, HulkAbilities.SUPER_PUNCH) > 0, "Super Punch goes on cooldown");
		helper.onEachTick(() -> {
			look(p, front);
			Hulk.tick(p);
		});
		helper.runAfterDelay(HulkAbilities.SUPER_PUNCH_IMPACT_TICKS + 4, () -> {
			helper.assertTrue(front.getHealth() < front.getMaxHealth(), "the mob in the cone is punched");
			helper.assertTrue(side.getLastHurtByMob() != p, "the one off to the side is not");
			helper.assertTrue(behind.getLastHurtByMob() != p, "nor the one behind");
			helper.assertTrue(far.getLastHurtByMob() != p, "nor one past the 8-block reach");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "hulk_v0164_punch_blocks")
	public void superPunchSmashesTheBlocksInItsCone(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		p.setYRot(0.0f);
		p.setXRot(0.0f);
		BlockPos chest = BlockPos.containing(p.position().add(0, p.getBbHeight() * 0.55, 0));
		BlockPos inCone = chest.south(4);
		BlockPos behind = chest.north(3);
		BlockPos bedrock = chest.south(3).above();
		helper.getLevel().setBlock(inCone, Blocks.STONE.defaultBlockState(), 2);
		helper.getLevel().setBlock(behind, Blocks.STONE.defaultBlockState(), 2);
		helper.getLevel().setBlock(bedrock, Blocks.BEDROCK.defaultBlockState(), 2);
		HulkAbilities.superPunch(p);
		helper.onEachTick(() -> {
			p.setYRot(0.0f);
			p.setXRot(0.0f);
			Hulk.tick(p);
		});
		helper.runAfterDelay(HulkAbilities.SUPER_PUNCH_IMPACT_TICKS + 4, () -> {
			helper.assertTrue(helper.getLevel().getBlockState(inCone).isAir(), "the stone in front is smashed");
			helper.assertTrue(helper.getLevel().getBlockState(behind).is(Blocks.STONE), "the stone behind him is not");
			helper.assertTrue(helper.getLevel().getBlockState(bedrock).is(Blocks.BEDROCK), "bedrock never breaks");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- V grab + far throw

	/**
	 * v0.16.4 (user): "make the hulk have to charge up his throw by holding v, at max charge he throws entities 200 blocks away".
	 * V picks it up; V down again only starts the charge; V up throws it -- {@code charge} 0 (a tap) or 1 (held the full
	 * {@code throwChargeTicks}).
	 */
	private static void chargedThrow(GameTestHelper helper, float charge, double wantBlocks) {
		ServerPlayer p = hulk(helper);
		var target = mob(helper, p.position().add(0, 0, 3), 200.0);
		look(p, target);
		HulkGrab.press(p, false);
		helper.assertTrue(Hulk.state(p).combat.holding, "V picks the mob up");
		helper.assertTrue(Hulk.state(p).animId == HulkState.ANIM_GRAB, "with the grab animation");
		HulkGrab.releaseV(p);
		helper.assertTrue(Hulk.state(p).combat.holding, "letting go of the V that grabbed it throws nothing");
		p.setXRot(0.0f);
		p.setYHeadRot(p.getYRot());
		HulkGrab.press(p, false);
		helper.assertTrue(HulkGrab.chargingThrow(p), "V down again winds the throw up");
		helper.assertTrue(HulkAbilities.cooldownRemaining(p, HulkAbilities.GRAB) == 0, "nothing is thrown while V is held");
		HulkGrab.press(p, false); // the OS key repeat of a held V: still the same charge
		if (charge > 0.0f) {
			// held for the whole charge
			HulkState n = Hulk.state(p).copy();
			n.moves.throwChargeStart = p.level().getGameTime() - Math.round(HulkConfig.abilities().throwChargeTicks * charge);
			p.setAttached(com.projecthero.mod.attachment.ModAttachments.HULK_STATE, n);
		}
		helper.assertTrue(Math.abs(HulkGrab.throwCharge(p) - charge) < 1.0e-4f, "charge " + charge + ", got " + HulkGrab.throwCharge(p));
		Vec3 launch = HulkGrab.aimedThrowVelocity(p, target, HulkGrab.throwBlocksFor(charge), target.getGravity());
		HulkGrab.releaseV(p);
		helper.assertFalse(HulkGrab.chargingThrow(p), "V up ends the charge");
		helper.assertTrue(HulkAbilities.cooldownRemaining(p, HulkAbilities.GRAB) > 0, "and throws it (cooldown starts)");
		helper.assertTrue(Hulk.state(p).animId == HulkState.ANIM_THROW_FAR, "with the throw animation");
		helper.onEachTick(() -> Hulk.tick(p));
		helper.runAfterDelay(HulkAbilities.THROW_FAR_RELEASE_TICKS + 2, () -> {
			helper.assertFalse(Hulk.state(p).combat.holding, "it left his hands on the release frame");
			helper.assertTrue(HulkGrab.inFlight(p, target), "and is flying");
			Vec3 v = target.getDeltaMovement();
			double h = Math.sqrt(v.x * v.x + v.z * v.z);
			double want = Math.sqrt(launch.x * launch.x + launch.z * launch.z);
			helper.assertTrue(Math.abs(h - want) < want * 0.2 + 0.02,
					"held at its throw speed (the mob moves overhead before the release frame, so the aim re-solves from there): want " + want + ", got " + h);
			// v0.16.4 (user): "make it throw where hes aiming" -- aiming level at open air, it is thrown straight along the
			// look, at the point the charge's distance away on that line
			Vec3 look = p.getLookAngle();
			helper.assertTrue((v.x * look.x + v.z * look.z) / Math.max(1.0e-6, h) > 0.99, "along his aim, got " + v);
			Vec3 aim = p.getEyePosition().add(look.scale(wantBlocks));
			Vec3 start = target.position().add(0, target.getBbHeight() * 0.5, 0);
			helper.assertTrue(Math.abs(aim.subtract(start).horizontalDistance() - wantBlocks) < 4.0, "aimed " + wantBlocks + " blocks out");
			HulkGrab.release(p);
			target.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "hulk_v0164_throw")
	public void tappingVTossesItTwentyBlocks(GameTestHelper helper) {
		helper.assertTrue(Math.abs(HulkGrab.throwBlocksFor(0.0f) - 20.0) < 1.0e-6, "a tap is 20 blocks");
		chargedThrow(helper, 0.0f, 20.0);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "hulk_v0164_throw_full")
	public void holdingVThrowsItTwoHundredBlocks(GameTestHelper helper) {
		helper.assertTrue(Math.abs(HulkGrab.throwBlocksFor(1.0f) - 200.0) < 1.0e-6, "full charge is 200 blocks");
		chargedThrow(helper, 1.0f, 200.0);
	}

	/** v0.16.4 (user): "make it throw where hes aiming" -- down at the floor goes down there, up goes up, not up and ahead. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "hulk_v0164_throw_aim")
	public void throwingGoesWhereHeAims(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		var target = mob(helper, p.position().add(0, 0, 3), 200.0);
		look(p, target);
		HulkGrab.press(p, false);
		helper.assertTrue(Hulk.state(p).combat.holding, "V picks the mob up");
		p.setYRot(0.0f);
		p.setYHeadRot(0.0f);
		p.setXRot(50.0f); // down at the floor ahead
		Vec3 down = HulkGrab.aimedThrowVelocity(p, target, 200.0, target.getGravity());
		helper.assertTrue(down.y < 0.0 && down.z > 0.0, "looking down: thrown down and ahead, at the floor he aims at, got " + down);
		p.setXRot(-60.0f); // up at the sky
		Vec3 up = HulkGrab.aimedThrowVelocity(p, target, 200.0, target.getGravity());
		helper.assertTrue(up.y > Math.abs(up.z), "looking up: thrown steeply up, got " + up);
		p.setXRot(0.0f);
		p.setYRot(90.0f); // level, to the side (-X)
		p.setYHeadRot(90.0f);
		Vec3 side = HulkGrab.aimedThrowVelocity(p, target, 50.0, target.getGravity());
		helper.assertTrue(side.x < 0.0 && Math.abs(side.z) < Math.abs(side.x) * 0.6, "level to the side (from where the mob is held): thrown that way, got " + side);
		HulkGrab.release(p);
		target.discard();
		Hulk.revoke(p);
		helper.succeed();
	}

	// ---------------------------------------------------------------- Shift+V Loki slam

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "hulk_v0164_loki")
	public void lokiSlamHitsTheFloorFourTimes(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		// 150 health: survives four slams at full rage (9 x 4 x 1.67) -- a 1000-health mob counts as a boss and is refused
		var victim = mob(helper, p.position().add(0, 0, 6), 150.0);
		look(p, victim);
		HulkGrab.press(p, true);
		helper.assertTrue(HulkGrab.slamming(p), "Shift+V on a target within 10 blocks starts the slam");
		helper.assertTrue(Hulk.state(p).animId == HulkState.ANIM_LOKI_SLAM, "with its animation");
		helper.onEachTick(() -> Hulk.tick(p));
		helper.runAfterDelay(HulkGrab.LOKI_TOTAL_TICKS + 4, () -> {
			helper.assertFalse(HulkGrab.slamming(p), "it is over after ~3 s");
			helper.assertTrue(HulkGrab.lastSlamImpacts(p) == 4, "four impacts, got " + HulkGrab.lastSlamImpacts(p));
			helper.assertTrue(victim.getHealth() < victim.getMaxHealth(), "and they hurt");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_v0164_loki_boss")
	public void lokiSlamRefusesWhatIsTooBig(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		var ghast = EntityType.GHAST.create(helper.getLevel());
		ghast.moveTo(p.getX(), p.getY() + 1.0, p.getZ() + 7.0, 0.0f, 0.0f);
		ghast.setNoAi(true);
		helper.getLevel().addFreshEntity(ghast);
		look(p, ghast);
		HulkGrab.press(p, true);
		helper.assertFalse(HulkGrab.slamming(p), "a 4-block ghast cannot be slammed");
		HulkGrab.release(p);
		ghast.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- Shift+V debris

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_v0164_debris")
	public void debrisIsMadeOfTheGroundUnderHim(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		p.setXRot(60.0f); // looking at the floor: no mob in front
		BlockPos floor = BlockPos.containing(p.position()).below();
		for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-3, 0, -3), floor.offset(3, 0, 3))) {
			helper.getLevel().setBlock(pos, Blocks.SAND.defaultBlockState(), 2);
			helper.getLevel().setBlock(pos.below(), Blocks.SANDSTONE.defaultBlockState(), 2);
		}
		HulkGrab.press(p, true);
		helper.assertTrue(Hulk.state(p).combat.holding, "Shift+V rips a chunk of ground up");
		helper.assertTrue(Hulk.state(p).animId == HulkState.ANIM_DEBRIS_RIP, "with the rip animation");
		var blocks = HulkGrab.heldDebris(p);
		helper.assertTrue(blocks != null, "a debris entity is over his head");
		helper.assertTrue(blocks[0].is(Blocks.SAND), "its top is the sand he stood on, got " + blocks[0]);
		helper.assertTrue(blocks[1].is(Blocks.SANDSTONE), "and under it the sandstone, got " + blocks[1]);
		HulkGrab.release(p);
		helper.succeed();
	}

	// ---------------------------------------------------------------- Shift+C Earth Wall

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "hulk_v0164_wall")
	public void earthWallRisesInFront(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		p.setYRot(0.0f);
		p.setXRot(0.0f);
		BlockPos floor = BlockPos.containing(p.position()).below();
		for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-5, 0, -2), floor.offset(5, 0, 8))) {
			helper.getLevel().setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
			helper.getLevel().setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), 2);
		}
		int before = HulkEarthWall.standing();
		HulkAbilities.earthWall(p);
		helper.onEachTick(() -> Hulk.tick(p));
		helper.runAfterDelay(HulkAbilities.EARTH_WALL_IMPACT_TICKS + HulkConfig.abilities().earthWallHeight * 2 + 6, () -> {
			helper.assertTrue(HulkEarthWall.standing() > before, "a wall is standing");
			int solid = 0;
			for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-4, 1, 2), floor.offset(4, 5, 7))) {
				if (!helper.getLevel().getBlockState(pos).isAir()) {
					solid++;
				}
			}
			helper.assertTrue(solid >= 10, "made of earth blocks in front of him, got " + solid);
			helper.succeed();
		});
	}
}
