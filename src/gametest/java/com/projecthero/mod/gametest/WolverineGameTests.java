package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.wolverine.Wolverine;
import com.projecthero.mod.wolverine.WolverineAbilities;
import com.projecthero.mod.wolverine.WolverineConfig;
import com.projecthero.mod.wolverine.WolverinePassives;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side coverage for Wolverine (v0.12.1): the Super Regeneration prerequisite, the state and
 * attribute lifecycle, claw toggling, Rage rules, the R/G/Z/X/C/V slot routing, ability damage against a
 * real target, and the emergency heal. Incoming damage against a mock player is not reliable in
 * GameTests (see the Punisher tests), so the damage-reduction maths is covered by the manual plan.
 */
public class WolverineGameTests implements FabricGameTest {

	private static ServerPlayer wolverine(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		ExperimentalPowers.grant(p, Powers.byKey(Wolverine.SUPER_REGENERATION_KEY));
		Wolverine.ascendFromSuperRegeneration(p);
		Wolverine.setClawTier(p, com.projecthero.mod.wolverine.data.ClawTier.ADAMANTIUM, false);
		return p;
	}

	private static Zombie zombieInFront(GameTestHelper helper, ServerPlayer p, double distance) {
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 look = p.getLookAngle();
		z.moveTo(p.getX() + look.x * distance, p.getY(), p.getZ() + look.z * distance);
		z.setNoAi(true);
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wolverineRequiresSuperRegeneration(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		helper.assertFalse(Wolverine.ascendFromSuperRegeneration(p), "no Super Regeneration -> no ascension");
		helper.assertFalse(Wolverine.hasPower(p), "still not Wolverine");
		ExperimentalPowers.grant(p, Powers.byKey(Wolverine.SUPER_REGENERATION_KEY));
		helper.assertTrue(Wolverine.ascendFromSuperRegeneration(p), "with Super Regeneration it ascends");
		helper.assertTrue(Wolverine.hasPower(p), "now Wolverine");
		helper.assertFalse(Wolverine.hasSuperRegeneration(p), "the mutation is consumed by the ascension");
		helper.assertTrue(HeroTiers.holdsHero(p, "wolverine"), "registered as a Hero-Tier Primary power");
		helper.assertFalse(Wolverine.ascendFromSuperRegeneration(p), "cannot ascend twice");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void statsApplyAndAreRemovedWithThePower(GameTestHelper helper) {
		ServerPlayer p = wolverine(helper);
		WolverinePassives.reconcile(p);
		double baseAttack = 1.0 + WolverineConfig.MELEE_BONUS_DAMAGE;
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - baseAttack) < 1e-6,
				"+8 melee, got " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 0.75 - 1e-6, "75% knockback resistance");
		helper.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().isEmpty(), "no passive speed modifier (v0.12.21)");
		WolverinePassives.reconcile(p);
		WolverinePassives.reconcile(p);
		helper.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().isEmpty(), "reconcile is idempotent");
		Wolverine.revoke(p);
		helper.assertFalse(Wolverine.hasPower(p), "revoked");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1.0) < 1e-6, "melee bonus gone");
		helper.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().isEmpty(), "speed modifier gone");
		helper.assertTrue(p.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getModifiers().isEmpty(), "knockback modifier gone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void clawsToggleAndBuffMelee(GameTestHelper helper) {
		ServerPlayer p = wolverine(helper);
		helper.assertFalse(Wolverine.clawsOut(p), "claws start retracted");
		Wolverine.setClaws(p, true);
		helper.assertTrue(Wolverine.clawsOut(p), "deployed");
		double expected = 1.0 + WolverineConfig.MELEE_BONUS_DAMAGE + WolverineConfig.CLAW_MELEE_BONUS;
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - expected) < 1e-6,
				"claw melee is 12, got " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		Wolverine.setClaws(p, false);
		helper.assertFalse(Wolverine.clawsOut(p), "retracted");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE)
				- (1.0 + WolverineConfig.MELEE_BONUS_DAMAGE)) < 1e-6, "claw bonus removed on retract");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void clawProgression(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		ExperimentalPowers.grant(p, Powers.byKey(Wolverine.SUPER_REGENERATION_KEY));
		Wolverine.ascendFromSuperRegeneration(p);
		com.projecthero.mod.wolverine.data.ClawTier none = com.projecthero.mod.wolverine.data.ClawTier.NONE;
		helper.assertTrue(Wolverine.clawTier(p) == none, "a fresh Wolverine has no claws");
		helper.assertFalse(Wolverine.setClaws(p, true), "no claws to deploy");
		helper.assertFalse(Wolverine.upgradeToAdamantium(p), "cannot skip the bone claws");
		helper.assertTrue(Wolverine.unlockBoneClaws(p), "bone serum unlocks bone claws");
		helper.assertFalse(Wolverine.unlockBoneClaws(p), "bone serum only works once");
		Wolverine.setClaws(p, true);
		double bone = 1.0 + WolverineConfig.MELEE_BONUS_DAMAGE + WolverineConfig.BONE_CLAW_MELEE_BONUS;
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - bone) < 1e-6, "bone melee is 9");
		helper.assertTrue(Wolverine.upgradeToAdamantium(p), "adamantium serum upgrades bone claws");
		helper.assertFalse(Wolverine.upgradeToAdamantium(p), "cannot upgrade twice");
		double adam = 1.0 + WolverineConfig.MELEE_BONUS_DAMAGE + WolverineConfig.CLAW_MELEE_BONUS;
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - adam) < 1e-6, "adamantium melee is 12");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rageAppliesOnceAndCannotStack(GameTestHelper helper) {
		ServerPlayer p = wolverine(helper);
		AbilityRouter.handleInput(p, 6, true);
		helper.assertFalse(Wolverine.raging(p), "C does nothing while the rage bar is empty");
		Wolverine.addRage(p, WolverineConfig.RAGE_BAR_MAX);
		helper.assertTrue(Wolverine.state(p).rageMeter >= WolverineConfig.RAGE_BAR_MAX, "the bar fills");
		AbilityRouter.handleInput(p, 6, true);
		helper.assertTrue(Wolverine.raging(p), "C starts Berserker Rage from a full bar");
		helper.assertTrue(Wolverine.state(p).rageMeter == 0.0f, "starting the rage empties the bar");
		long until = Wolverine.state(p).rageUntil;
		helper.assertTrue(until - p.level().getGameTime() == WolverineConfig.RAGE_TICKS, "lasts 30 seconds");
		double rageSpeed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		AbilityRouter.handleInput(p, 6, true);
		helper.assertTrue(Wolverine.state(p).rageUntil == until, "a second press does not extend or stack it");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) == rageSpeed, "no stacked speed");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0 - 1e-6, "near-total knockback resistance");
		Wolverine.clearTransient(p);
		helper.assertFalse(Wolverine.raging(p), "rage ends with transient state");
		helper.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().isEmpty(), "rage speed modifier removed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void slotRoutingAndCooldownGate(GameTestHelper helper) {
		ServerPlayer p = wolverine(helper);
		p.setYRot(0.0f);
		p.setXRot(0.0f);
		Zombie z = zombieInFront(helper, p, 2.0);
		float before = z.getHealth();
		AbilityRouter.handleInput(p, 1, true); // R -> Claw Slash
		helper.assertTrue(Wolverine.clawsOut(p), "a claw move deploys the claws");
		helper.assertTrue(before - z.getHealth() >= WolverineConfig.SLASH_DAMAGE - 0.5f,
				"Claw Slash deals 18, dealt " + (before - z.getHealth()));
		helper.assertFalse(Wolverine.abilityReady(p, WolverineAbilities.SLASH), "cooldown started");
		float after = z.getHealth();
		AbilityRouter.handleInput(p, 1, true);
		helper.assertTrue(z.getHealth() == after, "cannot bypass the cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void frenzyNeedsATargetAndDoesNotSpendCooldownWithout(GameTestHelper helper) {
		ServerPlayer p = wolverine(helper);
		// neighbouring tests may have left mobs nearby on CI: clear the area so "nobody near" is true
		for (var e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
				p.getBoundingBox().inflate(8.0), x -> x != p && !(x instanceof net.minecraft.world.entity.player.Player))) {
			e.discard();
		}
		AbilityRouter.handleInput(p, 5, true); // C -> Frenzy with nobody near
		helper.assertTrue(Wolverine.abilityReady(p, WolverineAbilities.FRENZY), "no target: no cooldown spent");
		Zombie z = zombieInFront(helper, p, 2.0);
		AbilityRouter.handleInput(p, 5, true);
		helper.assertFalse(Wolverine.abilityReady(p, WolverineAbilities.FRENZY), "with a target it fires and cools down");
		helper.assertTrue(z.isAlive() || z.getHealth() <= 0, "target valid");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void emergencyHealFiresOnceThenCoolsDown(GameTestHelper helper) {
		ServerPlayer p = wolverine(helper);
		p.setHealth(p.getMaxHealth() * 0.10f);
		helper.assertTrue(WolverinePassives.tryEmergency(p), "below 15% health starts the emergency heal");
		helper.assertTrue(Wolverine.state(p).emergencyHealUntil > p.level().getGameTime(), "heal window running");
		helper.assertTrue(Wolverine.state(p).emergencyReadyAt - p.level().getGameTime() == WolverineConfig.EMERGENCY_COOLDOWN_TICKS,
				"60-second internal cooldown");
		helper.assertFalse(WolverinePassives.tryEmergency(p), "cannot retrigger during the cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void otherPowersKeepTheirSlotsWhenNotWolverine(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		ExperimentalPowers.grant(p, Powers.byKey(Wolverine.SUPER_REGENERATION_KEY));
		helper.assertFalse(com.projecthero.mod.wolverine.WolverineAbilityManager.hasContext(p),
				"plain Super Regeneration is not in Wolverine's context");
		AbilityRouter.handleInput(p, 6, true); // C must not start a Wolverine rage
		helper.assertFalse(Wolverine.raging(p), "no Wolverine behaviour without the power");
		helper.succeed();
	}
}
