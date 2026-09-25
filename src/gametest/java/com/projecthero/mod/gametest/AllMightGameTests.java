package com.projecthero.mod.gametest;

import com.mojang.serialization.JsonOps;

import com.projecthero.mod.allmight.AllMight;
import com.projecthero.mod.allmight.AllMightAbilities;
import com.projecthero.mod.allmight.AllMightConfig;
import com.projecthero.mod.allmight.AllMightSuit;
import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.HeroTiers;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side coverage for All Might / One For All (v0.12.33): the grant, the two forms and their stats (no stacking),
 * the OFA resource, the cooldown / OFA gates, Detroit and United States of Smash landing on real mobs at their impact
 * frame, Full Cowl not stacking, the damage factors, persistence and revoke. Mock players are not reliably ticked by the
 * server, so each test drives {@link AllMight#tick} itself, exactly as the server tick would.
 */
public class AllMightGameTests implements FabricGameTest {
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		// the test volume is walled in with barriers: open a room in front for the Smashes
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 5, 8))) {
			helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
		}
		AllMight.grant(p);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> AllMight.tick(p));
	}

	private static Zombie zombieAhead(GameTestHelper helper, ServerPlayer p, double distance) {
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 fwd = Vec3.directionFromRotation(0, p.getYRot());
		z.moveTo(p.getX() + fwd.x * distance, p.getY(), p.getZ() + fwd.z * distance);
		z.setNoAi(true);
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void grantGivesThePowerTheCostumeAndTheContainedStats(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		helper.assertFalse(AllMight.hasPower(p), "no power yet");
		helper.assertTrue(AllMight.grant(p), "first grant works");
		helper.assertFalse(AllMight.grant(p), "second grant refused");
		helper.assertTrue(AllMight.hasPower(p) && HeroTiers.holdsHero(p, AllMight.KEY), "a registered Hero-Tier Primary power");
		helper.assertTrue(AllMight.ofa(p) == AllMightConfig.OFA_MAX, "starts with a full OFA bar");
		helper.assertTrue(AllMightSuit.wearing(p), "the costume forms on the wearer");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (1.0 + AllMightConfig.BASE_ATTACK_BONUS)) < 1e-6,
				"contained melee is 1 + 25, got " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		helper.assertTrue(Math.abs(p.getMaxHealth() - (20.0 + AllMightConfig.BASE_HEALTH_BONUS)) < 1e-6, "+40 max health, got " + p.getMaxHealth());
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - AllMightConfig.BASE_KNOCKBACK_RESISTANCE) < 1e-6, "80% knockback resistance");
		helper.assertTrue(p.getAttributeValue(Attributes.JUMP_STRENGTH) > 0.42 * 1.4, "jumps clearly higher than vanilla");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void hTogglesTheFormWithoutStackingModifiers(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		pump(helper, p);
		AllMight.toggleForm(p);
		helper.assertTrue(AllMight.isFullPower(p), "H -> full power");
		AllMight.toggleForm(p); // pressed again inside the debounce / transformation window: ignored
		helper.assertTrue(AllMight.isFullPower(p), "a spammed H does nothing while transforming");
		helper.assertTrue(AllMight.transforming(p), "damage-proof while transforming");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (1.0 + AllMightConfig.FULL_ATTACK_BONUS)) < 1e-6,
				"full-power melee is 1 + 35, got " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		helper.runAfterDelay(AllMightConfig.TRANSFORM_TICKS + 5, () -> {
			AllMight.reconcile(p);
			AllMight.reconcile(p);
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (1.0 + AllMightConfig.FULL_ATTACK_BONUS)) < 1e-6, "reconciling never stacks");
			AllMight.toggleForm(p);
			helper.assertFalse(AllMight.isFullPower(p), "H again -> contained");
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (1.0 + AllMightConfig.BASE_ATTACK_BONUS)) < 1e-6,
					"back to the contained stats, got " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void formDamageFactorsAndFallReduction(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		helper.assertTrue(Math.abs(AllMight.damageTakenFactor(p) - 0.65f) < 1e-4f, "contained takes 65%");
		helper.assertTrue(Math.abs(AllMight.fallReduction(p) - 0.75f) < 1e-4f, "75% fall reduction");
		AllMightState s = AllMight.state(p).copy();
		s.fullPower = true;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.ALL_MIGHT_STATE, s);
		helper.assertTrue(Math.abs(AllMight.damageTakenFactor(p) - 0.5f) < 1e-4f, "full power takes 50%");
		helper.assertTrue(Math.abs(AllMight.fallReduction(p) - 0.90f) < 1e-4f, "90% fall reduction");
		s = AllMight.state(p).copy();
		s.cowlUntil = p.level().getGameTime() + 100;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.ALL_MIGHT_STATE, s);
		helper.assertTrue(Math.abs(AllMight.damageTakenFactor(p) - 0.4f) < 1e-4f, "Full Cowl multiplies in a further 20% (0.5 x 0.8)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ofaNeverGoesNegativeAndGatesAbilities(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		helper.assertFalse(AllMight.spendOfa(p, 101.0f), "cannot spend more than the bar holds");
		helper.assertTrue(AllMight.ofa(p) == 100.0f, "a refused spend costs nothing");
		helper.assertTrue(AllMight.spendOfa(p, 100.0f), "can spend it all");
		helper.assertTrue(AllMight.ofa(p) == 0.0f, "empty, never negative");
		AbilityRouter.handleInput(p, 1, true); // R with no OFA
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.DETROIT) == 0, "no OFA -> Detroit Smash does not start");
		helper.assertTrue(AllMight.ofa(p) == 0.0f, "still zero");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void ofaRegenerates(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		AllMight.spendOfa(p, 50.0f);
		pump(helper, p);
		helper.runAfterDelay(100, () -> {
			float ofa = AllMight.ofa(p);
			helper.assertTrue(ofa > 50.0f + 4.0f && ofa < 100.0f, "regenerated some OFA, got " + ofa);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void detroitSmashHitsAtTheImpactFrameAndCostsOfaAndCooldown(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		pump(helper, p);
		Zombie z = zombieAhead(helper, p, 3.0);
		float before = z.getHealth();
		AbilityRouter.handleInput(p, 1, true); // R = Detroit Smash
		helper.assertTrue(AllMight.ofa(p) == 100.0f - AllMightConfig.DETROIT_COST, "spent 10 OFA, has " + AllMight.ofa(p));
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.DETROIT) > 0, "on cooldown");
		helper.assertTrue(z.getHealth() == before, "nothing lands before the wind-up ends");
		AbilityRouter.handleInput(p, 1, true); // spammed: locked / cooling down
		helper.assertTrue(AllMight.ofa(p) == 100.0f - AllMightConfig.DETROIT_COST, "a second press costs nothing");
		helper.runAfterDelay(AllMightConfig.DETROIT_WINDUP + 4, () -> {
			helper.assertTrue(z.getHealth() < before || !z.isAlive(), "the punch hurt the zombie at the impact frame");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void unitedStatesOfSmashIsStagedAndDeadly(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		pump(helper, p);
		Zombie z = zombieAhead(helper, p, 5.0);
		AbilityRouter.handleInput(p, 5, true); // V
		helper.assertTrue(AllMight.ofa(p) == 0.0f, "costs the whole bar");
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.UNITED_STATES) >= AllMightConfig.UNITED_STATES_COOLDOWN - 2, "60 s cooldown");
		helper.assertTrue(z.isAlive() && z.getHealth() == z.getMaxHealth(), "the charge is not an instant hit");
		helper.runAfterDelay(AllMightConfig.UNITED_STATES_WINDUP + 4, () -> {
			helper.assertFalse(z.isAlive(), "250 damage kills a zombie");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fullCowlDoesNotStack(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		AbilityRouter.handleInput(p, 6, true); // C
		helper.assertTrue(AllMight.cowlActive(p), "Full Cowl on");
		float ofa = AllMight.ofa(p);
		helper.assertTrue(ofa == 100.0f - AllMightConfig.COWL_OFA_COST, "costs 20 OFA");
		double attack = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		helper.assertTrue(attack > (1.0 + AllMightConfig.BASE_ATTACK_BONUS) * 1.4, "+50% melee while active, got " + attack);
		AbilityRouter.handleInput(p, 6, true); // pressed again while active
		helper.assertTrue(AllMight.ofa(p) == ofa, "no second charge");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - attack) < 1e-6, "no duplicated buff");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void fullCowlExpires(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		pump(helper, p);
		AllMightState s = AllMight.state(p).copy();
		s.cowlUntil = p.level().getGameTime() + 10;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.ALL_MIGHT_STATE, s);
		AllMight.reconcile(p);
		helper.runAfterDelay(20, () -> {
			helper.assertFalse(AllMight.cowlActive(p), "expired by itself");
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (1.0 + AllMightConfig.BASE_ATTACK_BONUS)) < 1e-6, "the buff was removed");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stateSurvivesTheCodecAndRevokeCleansUp(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		AllMightState s = AllMight.state(p).copy();
		s.fullPower = true;
		s.ofa = 42.0f;
		s.abilityReadyAt.put(AllMightAbilities.TEXAS, 999L);
		var json = AllMightState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
		AllMightState back = AllMightState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.hasPower && back.fullPower && back.ofa == 42.0f && back.abilityReadyAt.get(AllMightAbilities.TEXAS) == 999L,
				"power, form, OFA and cooldowns persist");
		AllMight.revoke(p);
		helper.assertFalse(AllMight.hasPower(p), "revoked");
		helper.assertFalse(AllMightSuit.wearing(p), "costume removed");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1.0) < 1e-6, "every modifier removed");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20.0) < 1e-6, "max health back to normal");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void leapIsCheapAndOnCooldown(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		AllMightAbilities.leap(p);
		helper.assertTrue(AllMight.ofa(p) == 100.0f - AllMightConfig.LEAP_COST, "costs 5 OFA");
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.LEAP) > 0, "5 s cooldown");
		helper.assertTrue(p.getDeltaMovement().y > 1.5, "launched hard upward, vy=" + p.getDeltaMovement().y);
		helper.succeed();
	}
}
