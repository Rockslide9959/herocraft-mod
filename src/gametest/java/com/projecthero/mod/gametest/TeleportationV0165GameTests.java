package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p11.TeleportationHandlers;
import com.projecthero.mod.hero.visual.MutationMeters;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Teleportation rework: six keys (+ Shift versions) on a 0-100 Strain meter -- one smoke test per move plus the
 * Strain / overload / landing rules. Mock players are not reliably ticked by the server, so the tests drive
 * {@link ExperimentalPowers#serverTick} themselves.
 */
public class TeleportationV0165GameTests implements FabricGameTest {
	private static final String K = TeleportationHandlers.KEY;

	// ---------------- helpers ----------------

	/** A survival mock Teleporter at (2.5, 2, 1.5) on a stone floor, facing +Z, with a clear 5x4x7 room. */
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
		return p;
	}

	private static float res(ServerPlayer p, String name) {
		return ExperimentalPowers.getResource(p, Powers.byKey(K), name);
	}

	private static void tick(ServerPlayer p, int n) {
		for (int i = 0; i < n; i++) {
			ExperimentalPowers.serverTick(p);
		}
	}

	private static void press(ServerPlayer p, int slot) {
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
	}

	private static void shiftPress(ServerPlayer p, int slot) {
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
		p.setShiftKeyDown(false);
	}

	private static void assertStrain(GameTestHelper helper, ServerPlayer p, float expected, String what) {
		float s = TeleportationHandlers.strain(p);
		helper.assertTrue(Math.abs(s - expected) < 0.01f, what + ": expected strain " + expected + ", got " + s);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z) {
		Zombie zb = helper.spawn(EntityType.ZOMBIE, new BlockPos((int) x, 2, (int) z));
		zb.setPersistenceRequired();
		zb.setNoAi(true);
		return zb;
	}

	// ---------------- registry / HUD ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void sixKeysStrainMeterAndEnabled(GameTestHelper helper) {
		Power power = Powers.byKey(K);
		helper.assertTrue(Powers.isEnabled(power) && Powers.isMutation(power), "Teleportation is an enabled mutation again");
		helper.assertTrue(power.abilities().size() == 6, "six abilities (no H / N), got " + power.abilities().size());
		String[] ids = { "blink", "bamf_strike", "escape_blink", "phase_step", "rift", "spatial_fold" };
		for (int i = 0; i < ids.length; i++) {
			Ability a = power.ability(AbilitySlot.byNumber(i + 1));
			helper.assertTrue(a != null && a.id().equals(ids[i]), "slot " + (i + 1) + " should be " + ids[i]);
			helper.assertTrue(AbilityHandlers.has(power, a), ids[i] + " has a handler");
		}
		helper.assertTrue(power.ability(AbilitySlot.byNumber(5)).cooldownTicks() == 60 * 20, "only Rift has a cooldown (60s)");
		var strain = MutationMeters.get(K, "strain");
		helper.assertTrue(strain != null && strain.always(), "Strain is an always-on HUD meter");
		helper.succeed();
	}

	// ---------------- Strain rules ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void strainDrainsAndOverloadLocksOut(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		TeleportationHandlers.addStrain(p, 15);
		assertStrain(helper, p, 15, "one Blink's worth");
		tick(p, 10);
		helper.assertTrue(TeleportationHandlers.strain(p) <= 10.01f, "drains 10 a second, got " + TeleportationHandlers.strain(p));

		TeleportationHandlers.addStrain(p, 100);
		helper.assertTrue(TeleportationHandlers.overloaded(p), "100 strain overloads");
		helper.assertTrue(p.hasEffect(MobEffects.CONFUSION), "overload brings Nausea");
		Vec3 before = p.position();
		press(p, 3); // X: Escape Blink -- refused
		helper.assertTrue(p.position().distanceTo(before) < 0.01, "no teleports while overloaded");
		tick(p, TeleportationHandlers.OVERLOAD_TICKS + 1);
		helper.assertFalse(TeleportationHandlers.overloaded(p), "the lockout lasts 4s");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void noFallDamageForASecondAfterAJump(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		TeleportationHandlers.addStrain(p, 15);
		float hp = p.getHealth();
		p.hurt(p.damageSources().fall(), 12f);
		helper.assertTrue(p.getHealth() >= hp - 0.01f, "a fall right after a jump does nothing");
		tick(p, TeleportationHandlers.JUMP_GRACE_TICKS + 1);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().fall(), 12f);
		helper.assertTrue(p.getHealth() < hp, "after the grace a big fall hurts again");
		helper.succeed();
	}

	// ---------------- R / G / X / Z / V / C ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void blinkJumpsToTheCrosshair(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setXRot(28f); // looking at the floor ~3 blocks ahead
		double z0 = p.getZ();
		press(p, 1);
		helper.assertTrue(p.getZ() > z0 + 1.5, "Blink moves you forward, z " + z0 + " -> " + p.getZ());
		assertStrain(helper, p, TeleportationHandlers.STRAIN_BLINK, "Blink");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void bamfStrikeHitsAndSwapTradesPlaces(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 2, 5);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, z.position().add(0, 1, 0));
		float hp = z.getHealth();
		press(p, 2); // G: Bamf Strike
		helper.assertTrue(z.getHealth() < hp, "Bamf Strike hits the aimed target");
		assertStrain(helper, p, TeleportationHandlers.STRAIN_BAMF, "Bamf Strike");

		ServerPlayer q = hero(helper);
		Zombie z2 = zombie(helper, 0, 4);
		q.lookAt(EntityAnchorArgument.Anchor.EYES, z2.position().add(0, 1, 0));
		Vec3 mine = q.position();
		Vec3 theirs = z2.position();
		shiftPress(q, 2); // Shift+G: Swap
		helper.assertTrue(q.position().distanceTo(theirs) < 1.0, "Swap moves you to the target's spot");
		helper.assertTrue(z2.position().distanceTo(mine) < 1.0, "Swap moves the target to your spot");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void escapeBlinkBackAndSkyBlinkUp(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.moveTo(p.getX(), p.getY(), p.getZ() + 4, 0, 0); // room to fall back into
		double z0 = p.getZ();
		press(p, 3); // X: Escape Blink
		helper.assertTrue(p.getZ() < z0 - 1.0, "Escape Blink goes backwards, z " + z0 + " -> " + p.getZ());
		helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "with Resistance on arrival");
		assertStrain(helper, p, TeleportationHandlers.STRAIN_ESCAPE, "Escape Blink");

		double y0 = p.getY();
		shiftPress(p, 3); // Shift+X: Sky Blink
		helper.assertTrue(p.getY() > y0 + 2.0, "Sky Blink goes up, y " + y0 + " -> " + p.getY());
		helper.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), "with slow-fall");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void phaseStepCrossesAThickWall(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		BlockPos base = BlockPos.containing(p.position());
		for (int dz = 1; dz <= 2; dz++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dy = 0; dy <= 2; dy++) {
					helper.getLevel().setBlock(base.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 2);
				}
			}
		}
		double z0 = p.getZ();
		press(p, 4); // Z: Phase Step
		helper.assertTrue(p.getZ() > base.getZ() + 3, "Phase Step lands past a 2-thick wall, z " + z0 + " -> " + p.getZ());
		assertStrain(helper, p, TeleportationHandlers.STRAIN_PHASE, "Phase Step");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true)
	public void displaceThrowsTheTargetUp(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 2, 5);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, z.position().add(0, 1, 0));
		double y0 = z.getY();
		shiftPress(p, 4); // Shift+Z: Displace
		helper.assertTrue(z.getY() > y0 + 3.0, "Displace lifts the target, y " + y0 + " -> " + z.getY());
		assertStrain(helper, p, TeleportationHandlers.STRAIN_DISPLACE, "Displace");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markRecallAndRiftOpenClose(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Power power = Powers.byKey(K);
		Vec3 home = p.position();
		shiftPress(p, 5); // Shift+V: Mark
		helper.assertTrue(ExperimentalPowers.getMarker(p, power, "return") != null, "the mark is placed");
		p.teleportTo(home.x, home.y, home.z + 3);
		shiftPress(p, 5); // Shift+V: Recall
		helper.assertTrue(p.position().distanceTo(home) < 1.0, "Recall brings you back to the mark");
		assertStrain(helper, p, TeleportationHandlers.STRAIN_RECALL, "Recall");

		BlockPos far = BlockPos.containing(home).offset(0, 0, 4);
		TeleportationHandlers.createDestinationPortal(p, far.getX(), far.getY(), far.getZ(),
				helper.getLevel().dimension().location().toString());
		helper.assertTrue(ExperimentalPowers.getMarker(p, power, "rift_a") != null
				&& ExperimentalPowers.getMarker(p, power, "rift_b") != null, "the Rift opens a gate pair");
		helper.assertFalse(ExperimentalPowers.cooldownReady(p, power, power.ability(AbilitySlot.SLOT_5)), "Rift goes on cooldown");
		press(p, 5); // V while open: close it
		helper.assertTrue(ExperimentalPowers.getMarker(p, power, "rift_a") == null, "tapping V closes the open pair");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spatialFoldSendsArrowsBack(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie shooter = zombie(helper, 2, 6);
		press(p, 6); // C: Spatial Fold
		helper.assertTrue(TeleportationHandlers.foldActive(p), "the fold is up");
		assertStrain(helper, p, TeleportationHandlers.STRAIN_FOLD, "Spatial Fold");

		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.setOwner(shooter);
		arrow.setPos(p.getX(), p.getY() + 1.0, p.getZ() + 2.0);
		arrow.setDeltaMovement(0, 0, -1.5); // straight at the player
		helper.getLevel().addFreshEntity(arrow);
		tick(p, 1);
		helper.assertTrue(arrow.getOwner() == p, "the folded arrow is now yours");
		Vec3 toShooter = shooter.position().subtract(arrow.position());
		helper.assertTrue(arrow.getDeltaMovement().dot(toShooter) > 0, "and flies at whoever shot it");
		helper.succeed();
	}
}
