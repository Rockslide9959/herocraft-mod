package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.power.p04.SuperSpeedTimeSlow;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.5 Super Speed rework. Mock players are not reliably ticked, so the tests drive handlers directly; everything
 * stays within a few blocks of (2, 2, 2) inside the 8x8x8 barrier cage. Time Slow reaches 96 blocks, so its test runs
 * in a batch of its own (no other test's mobs are around to be slowed).
 */
public class SuperSpeedV0145GameTests implements FabricGameTest {

	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYRot(0);
		p.setXRot(0);
		p.setYHeadRot(0);
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 3, 4))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, -1, -1), base.offset(1, -1, 4))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		Power power = Powers.byKey(SuperSpeedHandlers.KEY);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		return p;
	}

	private static <T extends Mob> T mobAhead(GameTestHelper helper, ServerPlayer p, EntityType<T> type, double dist) {
		T m = type.create(helper.getLevel());
		m.moveTo(p.getX(), p.getY(), p.getZ() + dist, 180f, 0f);
		m.setNoAi(true);
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static Power power() {
		return Powers.byKey(SuperSpeedHandlers.KEY);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void powerIsRegistered(GameTestHelper helper) {
		helper.assertTrue(Powers.byKey(SuperSpeedHandlers.KEY) != null, "Super Speed is registered");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sixKeysPlusCarryOnN(GameTestHelper helper) {
		// v0.14.7: G is Blitz, Speed Carry moved to N -- and no H move (H stays the power wheel)
		Power power = power();
		String[] ids = { "rapid_assault", "blitz", "momentum_dash", "time_slow", "overdrive", "speed_mode" };
		helper.assertTrue(power.abilities().size() == 7, "the six keys plus N, got " + power.abilities().size());
		for (int i = 0; i < ids.length; i++) {
			var a = power.ability(AbilitySlot.byNumber(i + 1));
			helper.assertTrue(a != null && ids[i].equals(a.id()), "slot " + (i + 1) + " should be " + ids[i]);
			helper.assertTrue(AbilityHandlers.has(power, a), ids[i] + " has a handler");
		}
		helper.assertTrue(power.ability(AbilitySlot.SLOT_7) == null && !power.hasSlot(AbilitySlot.SLOT_7), "no H ability");
		var carry = power.ability(AbilitySlot.SLOT_8);
		helper.assertTrue(carry != null && "speed_carry".equals(carry.id()) && AbilityHandlers.has(power, carry),
				"N is Speed Carry");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void passiveSpeedAndSwimModifiers(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		var move = p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(SuperSpeedHandlers.PASSIVE_SPEED);
		helper.assertTrue(move != null && Math.abs(move.amount() - 0.30) < 1e-6, "+30% movement speed modifier");
		helper.assertTrue(p.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY) > 0.05, "faster swimming");
		ExperimentalPowers.forget(p, power());
		helper.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(SuperSpeedHandlers.PASSIVE_SPEED) == null,
				"removed with the power");
		helper.succeed();
	}

	/** v0.14.9: no step assist on foot; 3 blocks in Speed Mode, 10 in Overdrive. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void stepAssistOnlyInSpeedModeAndOverdrive(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		ExperimentalPowers.serverTick(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - 0.6) < 1e-6,
				"no step assist outside the modes, got " + p.getAttributeValue(Attributes.STEP_HEIGHT));
		AbilityRouter.handleInput(p, 6, true); // C = Speed Mode on
		AbilityRouter.handleInput(p, 6, false);
		ExperimentalPowers.serverTick(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - 3.0) < 1e-6,
				"3-block step assist in Speed Mode, got " + p.getAttributeValue(Attributes.STEP_HEIGHT));
		AbilityRouter.handleInput(p, 5, true); // V = Overdrive
		AbilityRouter.handleInput(p, 5, false);
		ExperimentalPowers.serverTick(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - 10.0) < 1e-6,
				"10-block step assist in Overdrive, got " + p.getAttributeValue(Attributes.STEP_HEIGHT));
		ExperimentalPowers.forget(p, power());
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - 0.6) < 1e-6, "back to vanilla without the power");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "speed_v0145_assault")
	public void rapidAssaultPunchesForEight(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem golem = mobAhead(helper, p, EntityType.IRON_GOLEM, 2.0);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, golem.getEyePosition());
		float before = golem.getHealth();
		AbilityRouter.handleInput(p, 1, true); // R
		float dealt = before - golem.getHealth();
		float expected = SuperSpeedHandlers.PUNCH_DAMAGE * SuperSpeedHandlers.PUNCHES;
		helper.assertTrue(Math.abs(dealt - expected) < 0.01f, "4 punches of 8 = " + expected + ", dealt " + dealt);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void momentumDashFollowsTheLook(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setXRot(-90f); // straight up
		AbilityRouter.handleInput(p, 3, true); // X
		helper.assertTrue(p.getDeltaMovement().y > 1.0, "looking up dashes upward (" + p.getDeltaMovement() + ")");
		Vec3 down = SuperSpeedHandlers.dashVelocity(new Vec3(0, -1, 0), false, 1.7);
		helper.assertTrue(down.y < -1.0, "and looking down in the air dashes downward");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "speed_v0145_carry")
	public void carriedEntityTakesNoFallDamage(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = mobAhead(helper, p, EntityType.ZOMBIE, 2.0);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		AbilityRouter.handleInput(p, 8, true); // N (v0.14.7: Speed Carry moved from G)
		helper.assertTrue(z.getVehicle() == p, "the zombie is carried");
		float before = z.getHealth();
		z.hurt(helper.getLevel().damageSources().fall(), 10f);
		helper.assertTrue(z.getHealth() == before, "no fall damage while carried");
		helper.assertFalse(ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p,
				helper.getLevel().damageSources().mobAttack(z), 3f), "the carried zombie can't hurt its carrier");
		AbilityRouter.handleInput(p, 8, true); // N again: set it down
		helper.assertTrue(z.getVehicle() == null, "set down");
		z.hurt(helper.getLevel().damageSources().fall(), 10f);
		helper.assertTrue(z.getHealth() == before, "and still safe from the fall for 3 s");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "speed_v0145_phase")
	public void phaseHoldMakesYouUntouchable(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = mobAhead(helper, p, EntityType.ZOMBIE, 2.0);
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, 6, true); // Shift+C
		p.setShiftKeyDown(false); // Shift only picks the variant on the press
		helper.assertTrue(SuperSpeedHandlers.phasing(p), "Shift+C starts Phase");
		helper.assertFalse(SuperSpeedHandlers.speedMode(p), "and does not toggle Speed Mode");
		var allow = ServerLivingEntityEvents.ALLOW_DAMAGE.invoker();
		helper.assertFalse(allow.allowDamage(p, helper.getLevel().damageSources().mobAttack(z), 5f), "no damage taken");
		helper.assertFalse(allow.allowDamage(z, helper.getLevel().damageSources().playerAttack(p), 5f), "no damage dealt");
		AbilityRouter.handleInput(p, 1, true); // R while phasing: swallowed
		helper.assertTrue(ExperimentalPowers.cooldownReady(p, power(), power().ability(AbilitySlot.SLOT_1)),
				"no other ability fires while phasing");
		AbilityRouter.handleInput(p, 6, false); // release C
		helper.assertFalse(SuperSpeedHandlers.phasing(p), "releasing C ends Phase");
		helper.assertTrue(allow.allowDamage(p, helper.getLevel().damageSources().mobAttack(z), 5f), "vulnerable again");
		AbilityRouter.handleInput(p, 6, true); // plain C: Speed Mode as before
		helper.assertTrue(SuperSpeedHandlers.speedMode(p), "plain C still toggles Speed Mode");
		helper.succeed();
	}
}
