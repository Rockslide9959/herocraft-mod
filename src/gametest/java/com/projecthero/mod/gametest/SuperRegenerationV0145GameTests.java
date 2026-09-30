package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.mutation.ModMobEffects;
import com.projecthero.mod.hero.power.p12.SuperRegenerationHandlers;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.5 Super Regeneration rework: passive-only (no keys), 10 HP / 5 ticks healing, 2 s harmful-effect cleanse
 * and three independently recharging revive charges. Mock players are not reliably ticked, so the tick methods
 * are driven directly (with explicit game times where timing matters).
 */
public class SuperRegenerationV0145GameTests implements FabricGameTest {
	private static final String KEY = SuperRegenerationHandlers.KEY;

	private static ServerPlayer regen(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		Power power = Powers.byKey(KEY);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void powerIsRegistered(GameTestHelper helper) {
		helper.assertTrue(Powers.byKey(KEY) != null, "Super Regeneration is registered");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void passiveOnlyAndKeyPressesDoNothing(GameTestHelper helper) {
		Power power = Powers.byKey(KEY);
		helper.assertTrue(power.abilities().isEmpty(), "no abilities at all");
		helper.assertTrue(power.passiveKeys().size() == 3, "exactly three passives");
		ServerPlayer p = regen(helper);
		p.setHealth(10f);
		for (int slot = 1; slot <= 8; slot++) {
			AbilityRouter.handleInput(p, slot, true);
			AbilityRouter.handleInput(p, slot, false);
		}
		ExperimentalState s = ExperimentalPowers.state(p);
		helper.assertTrue(s.abilityReadyAt.keySet().stream().noneMatch(k -> k.startsWith(KEY + "/")), "no cooldowns started");
		helper.assertTrue(s.activeToggles.stream().noneMatch(k -> k.startsWith(KEY + "/")), "no toggles");
		helper.assertTrue(p.getHealth() == 10f, "a key press does nothing");
		helper.assertTrue(com.projecthero.mod.wolverine.Wolverine.hasSuperRegeneration(p),
				"the Wolverine ascension prerequisite still reads this power");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void healTickRestoresTenAndShowsTheVeins(GameTestHelper helper) {
		ServerPlayer p = regen(helper);
		p.setHealth(4f);
		helper.assertTrue(SuperRegenerationHandlers.healTick(p), "hurt: it heals");
		helper.assertTrue(Math.abs(p.getHealth() - 14f) < 0.01f, "10 HP per heal tick, got " + p.getHealth());
		for (int i = 0; i < 4; i++) {
			p.tickCount = i;
			MutationVisuals.tick(p);
		}
		helper.assertTrue(MutationVisuals.hasFlag(p, SuperRegenerationHandlers.VEINS_FLAG), "the red veins show while healing");
		p.setHealth(p.getMaxHealth());
		helper.assertFalse(SuperRegenerationHandlers.healTick(p), "full health: nothing to heal");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void healsWhileAnotherPowerIsSelected(GameTestHelper helper) {
		ServerPlayer p = regen(helper);
		Power other = Powers.byKey("power_09_cryokinesis");
		ExperimentalPowers.grant(p, other);
		ExperimentalPowers.setActive(p, other);
		helper.assertTrue(ExperimentalPowers.owns(p, KEY), "still owns Super Regeneration");
		p.setHealth(2f);
		helper.startSequence()
				.thenExecuteFor(12, () -> ExperimentalPowers.serverTick(p))
				.thenExecute(() -> helper.assertTrue(p.getHealth() >= 12f,
						"the passive tick heals every 5 ticks whatever is selected, got " + p.getHealth()))
				.thenSucceed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void harmfulEffectsDissolveAfterTwoSeconds(GameTestHelper helper) {
		ServerPlayer p = regen(helper);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 1));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 0));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));
		p.addEffect(new MobEffectInstance(ModMobEffects.UNSTABLE_MUTATION, 1200, 0));
		long t0 = helper.getLevel().getGameTime();
		SuperRegenerationHandlers.tickCleanse(p, t0);
		SuperRegenerationHandlers.tickCleanse(p, t0 + SuperRegenerationHandlers.CLEANSE_TICKS - 1);
		helper.assertTrue(p.hasEffect(MobEffects.POISON), "poison lasts until the 2 s are up");
		SuperRegenerationHandlers.tickCleanse(p, t0 + SuperRegenerationHandlers.CLEANSE_TICKS);
		helper.assertFalse(p.hasEffect(MobEffects.POISON), "poison is burned off after 40 ticks");
		helper.assertFalse(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "so is slowness");
		helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "beneficial effects stay");
		helper.assertTrue(p.hasEffect(ModMobEffects.UNSTABLE_MUTATION), "the unstable mutation is never touched");
		// a fresh application gets its own 2 s
		p.addEffect(new MobEffectInstance(MobEffects.WITHER, 600, 0));
		SuperRegenerationHandlers.tickCleanse(p, t0 + 50);
		SuperRegenerationHandlers.tickCleanse(p, t0 + 60);
		helper.assertTrue(p.hasEffect(MobEffects.WITHER), "a new effect starts its own timer");
		SuperRegenerationHandlers.tickCleanse(p, t0 + 90);
		helper.assertFalse(p.hasEffect(MobEffects.WITHER), "and is burned off 2 s later");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void reviveChargesSpendAndRechargeIndependently(GameTestHelper helper) {
		ServerPlayer p = regen(helper);
		helper.assertTrue(SuperRegenerationHandlers.readyCharges(p) == 3, "three charges to start");
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 600, 0));
		p.setHealth(0.5f);
		boolean dies = ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(p, p.damageSources().generic(), 100f);
		helper.assertFalse(dies, "a lethal hit spends a charge and cancels the death");
		helper.assertTrue(Math.abs(p.getHealth() - p.getMaxHealth() * 0.5f) < 0.01f, "back at half health");
		helper.assertFalse(p.hasEffect(MobEffects.POISON), "harmful effects are cleared");
		helper.assertFalse(ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(p, p.damageSources().generic(), 5f),
				"1 s of damage immunity");
		helper.assertTrue(SuperRegenerationHandlers.readyCharges(p) == 2, "two left");
		helper.assertTrue(SuperRegenerationHandlers.chargeCooldown(p, 0) == SuperRegenerationHandlers.REVIVE_COOLDOWN,
				"the spent charge starts its own 60 s cooldown (saved in the power's resources)");

		SuperRegenerationHandlers.tickCharges(p, 600);
		helper.assertTrue(SuperRegenerationHandlers.tryRevive(p, p.damageSources().generic()), "second charge");
		helper.assertTrue(SuperRegenerationHandlers.chargeCooldown(p, 0) == 600f, "charge 1 is half-way back");
		helper.assertTrue(SuperRegenerationHandlers.chargeCooldown(p, 1) == SuperRegenerationHandlers.REVIVE_COOLDOWN,
				"charge 2 has its own full cooldown");
		SuperRegenerationHandlers.tickCharges(p, 600);
		helper.assertTrue(SuperRegenerationHandlers.chargeCooldown(p, 0) == 0f, "charge 1 is ready again after 60 s");
		helper.assertTrue(SuperRegenerationHandlers.chargeCooldown(p, 1) == 600f, "charge 2 is not");
		helper.assertTrue(SuperRegenerationHandlers.readyCharges(p) == 2, "so two are ready");

		helper.assertTrue(SuperRegenerationHandlers.tryRevive(p, p.damageSources().generic()), "spend");
		helper.assertTrue(SuperRegenerationHandlers.tryRevive(p, p.damageSources().generic()), "spend");
		helper.assertTrue(SuperRegenerationHandlers.readyCharges(p) == 0, "all three spent");
		helper.assertFalse(SuperRegenerationHandlers.tryRevive(p, p.damageSources().generic()), "no charge, no revive");
		helper.assertTrue(ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(p, p.damageSources().generic(), 100f),
				"with every charge recharging the death goes through");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void killCommandIgnoresReviveCharges(GameTestHelper helper) {
		ServerPlayer p = regen(helper);
		helper.assertFalse(SuperRegenerationHandlers.tryRevive(p, p.damageSources().genericKill()), "/kill still kills");
		helper.assertFalse(SuperRegenerationHandlers.tryRevive(p, p.damageSources().fellOutOfWorld()), "so does the void");
		helper.assertTrue(SuperRegenerationHandlers.readyCharges(p) == 3, "and no charge is spent");
		helper.succeed();
	}
}
