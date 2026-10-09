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

	/** v0.13.9: right-click guard -- only with the claws out and empty hands, and it drops when that stops. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void clawGuardNeedsClawsAndDropsWhenTheyGo(GameTestHelper helper) {
		ServerPlayer p = wolverine(helper);
		com.projecthero.mod.wolverine.WolverineBlock.start(p);
		helper.assertFalse(com.projecthero.mod.wolverine.WolverineBlock.isBlocking(p), "no guard with the claws away");
		Wolverine.setClaws(p, true);
		com.projecthero.mod.wolverine.WolverineBlock.start(p);
		helper.assertTrue(com.projecthero.mod.wolverine.WolverineBlock.isBlocking(p), "claws out: the guard goes up");
		com.projecthero.mod.wolverine.WolverineBlock.stop(p);
		helper.assertFalse(com.projecthero.mod.wolverine.WolverineBlock.isBlocking(p), "releasing lowers it");
		com.projecthero.mod.wolverine.WolverineBlock.start(p);
		Wolverine.setClaws(p, false);
		com.projecthero.mod.wolverine.WolverineAbilityManager.serverTick(p);
		helper.assertFalse(p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.WOLVERINE_BLOCKING, false),
				"retracting the claws drops the guard flag on the next tick");
		helper.assertTrue(WolverineConfig.BLOCK_DAMAGE_REDUCTION == 0.30f, "the guard cuts 30%");
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
	public void healingIsPaidFromThePoolAndStopsAtZero(GameTestHelper helper) {
		ServerPlayer p = wolverine(helper);
		helper.assertTrue(Wolverine.state(p).healPool == WolverineConfig.HEAL_POOL_MAX, "starts with a full 250 HP pool");
		p.setHealth(5.0f);
		Wolverine.markHurt(p);
		for (int i = 0; i < 40; i++) {
			p.tickCount = 5 * (i + 1); // every REGEN_INTERVAL_TICKS
			com.projecthero.mod.wolverine.WolverinePassives.tick(p);
		}
		com.projecthero.mod.wolverine.data.WolverineState st = Wolverine.state(p);
		helper.assertTrue(p.getHealth() > 5.0f && st.healPool < WolverineConfig.HEAL_POOL_MAX, "healing drains the pool");
		com.projecthero.mod.wolverine.data.WolverineState empty = st.copy();
		empty.healPool = 0.0f;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.WOLVERINE_STATE, empty);
		float hp = p.getHealth();
		p.tickCount = 5000;
		com.projecthero.mod.wolverine.WolverinePassives.tick(p);
		helper.assertTrue(p.getHealth() == hp, "an empty pool means no healing");
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

	// ---------------- v0.15.18 Death Surge rework ----------------

	private static void setPool(ServerPlayer p, float pool) {
		com.projecthero.mod.wolverine.data.WolverineState c = Wolverine.state(p).copy();
		c.healPool = pool;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.WOLVERINE_STATE, c);
	}

	private static int surgeTick = 0;

	/** Runs the Wolverine passive tick {@code steps} times on the 5-tick skin-recovery cadence. */
	private static void surgeSteps(ServerPlayer p, int steps) {
		for (int i = 0; i < steps; i++) {
			surgeTick += 5;
			p.tickCount = 100000 + surgeTick;
			WolverinePassives.tick(p);
		}
	}

	private static boolean hasSurgeDebuffs(ServerPlayer p) {
		return p.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)
				&& p.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
				&& p.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
	}

	private static boolean hasAnySurgeDebuff(ServerPlayer p) {
		return p.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)
				|| p.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)
				|| p.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "wolverine_surge")
	public void deathSurgeFiresOnEveryLethalHitAndCosts30(GameTestHelper helper) {
		ServerPlayer p = wolverine(helper);
		var generic = p.damageSources().generic();
		helper.assertTrue(Wolverine.state(p).healPool == WolverineConfig.HEAL_POOL_MAX, "starts with a full pool");
		p.setHealth(1.0f);
		helper.assertFalse(com.projecthero.mod.wolverine.WolverineDamage.allowDeath(p, generic, 100.0f), "the first lethal hit is survived");
		helper.assertTrue(Math.abs(p.getHealth() - p.getMaxHealth() * WolverineConfig.EMERGENCY_HEAL_FRACTION) < 1e-3f, "rises at 30% health");
		var st = Wolverine.state(p);
		helper.assertTrue(Math.abs(st.healPool - (WolverineConfig.HEAL_POOL_MAX - 30.0f)) < 1e-3f, "costs 30 Healing Factor, has " + st.healPool);
		helper.assertTrue(st.skinRecovery == 0.0f, "raw flesh");
		helper.assertTrue(Wolverine.invulnerable(p), "the damage-proof opening seconds are kept");
		helper.assertTrue(Wolverine.resurrecting(p) && Wolverine.surgeRecovering(p), "surge running");
		// part-way through a recovery, a second lethal hit fires again (no one-use limit) and restarts the flesh
		var partial = Wolverine.state(p).copy();
		partial.skinRecovery = 0.3f;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.WOLVERINE_STATE, partial);
		p.setHealth(1.0f);
		helper.assertFalse(com.projecthero.mod.wolverine.WolverineDamage.allowDeath(p, generic, 100.0f), "the second lethal hit is survived too");
		st = Wolverine.state(p);
		helper.assertTrue(Math.abs(st.healPool - (WolverineConfig.HEAL_POOL_MAX - 60.0f)) < 1e-3f, "another 30, has " + st.healPool);
		helper.assertTrue(st.skinRecovery == 0.0f, "a surge during recovery restarts the flesh state");
		helper.assertTrue(com.projecthero.mod.wolverine.WolverineDamage.allowDeath(p, p.damageSources().genericKill(), 100.0f), "/kill still kills");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "wolverine_surge")
	public void deathSurgeNeeds30HealingFactorOrHeDies(GameTestHelper helper) {
		ServerPlayer p = wolverine(helper);
		setPool(p, 29.5f);
		p.setHealth(1.0f);
		helper.assertTrue(com.projecthero.mod.wolverine.WolverineDamage.allowDeath(p, p.damageSources().generic(), 100.0f),
				"with less than 30 Healing Factor the surge does not fire: he dies");
		var st = Wolverine.state(p);
		helper.assertTrue(st.healPool == 29.5f && st.skinRecovery == 1.0f, "nothing spent, no flesh");
		helper.assertFalse(WolverinePassives.surgeAffordable(st), "HUD marker off");
		setPool(p, 30.0f);
		helper.assertTrue(WolverinePassives.surgeAffordable(Wolverine.state(p)), "exactly 30 is enough");
		helper.assertFalse(com.projecthero.mod.wolverine.WolverineDamage.allowDeath(p, p.damageSources().generic(), 100.0f), "fires at 30");
		helper.assertTrue(Wolverine.state(p).healPool == 0.0f, "drained to 0");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "wolverine_surge")
	public void skinOnlyHealsAtFullHpAndDebuffsLastUntilHalfway(GameTestHelper helper) {
		ServerPlayer p = wolverine(helper);
		p.setHealth(1.0f);
		helper.assertTrue(WolverinePassives.tryEmergency(p), "surge");
		setPool(p, 0.0f); // no healing factor: his health stays where the test puts it
		Wolverine.markHurt(p); // ...and the pool does not refill (game time does not move inside this test)
		float max = p.getMaxHealth();

		p.setHealth(max - 1.0f);
		surgeSteps(p, 20);
		helper.assertTrue(Wolverine.state(p).skinRecovery == 0.0f, "below full HP the skin does not recover, has " + Wolverine.state(p).skinRecovery);
		helper.assertTrue(hasSurgeDebuffs(p), "debuffed");

		p.setHealth(max);
		surgeSteps(p, 20); // 100 ticks of full HP = 25%
		helper.assertTrue(Wolverine.state(p).skinRecovery == 0.25f, "full HP: 25% after 5 s, has " + Wolverine.state(p).skinRecovery);

		p.setHealth(max - 1.0f);
		surgeSteps(p, 10);
		helper.assertTrue(Wolverine.state(p).skinRecovery == 0.25f, "hurt again: paused, not reset");

		p.removeAllEffects(); // milk
		surgeSteps(p, 1);
		helper.assertTrue(hasSurgeDebuffs(p), "milk cannot clear the surge debuffs early");

		p.setHealth(max);
		surgeSteps(p, 19); // 95%... of the way to 50%: 0.4875
		helper.assertTrue(Wolverine.state(p).skinRecovery < WolverineConfig.SURGE_DEBUFF_UNTIL && hasSurgeDebuffs(p),
				"still debuffed just under 50%");
		helper.assertTrue(Wolverine.resurrecting(p), "red border / torn suit still on");
		surgeSteps(p, 1);
		helper.assertTrue(Wolverine.state(p).skinRecovery == 0.5f, "exactly 50%, has " + Wolverine.state(p).skinRecovery);
		helper.assertFalse(hasAnySurgeDebuff(p), "the debuffs come off at 50%");
		helper.assertFalse(Wolverine.resurrecting(p), "surge window over");
		helper.assertTrue(Wolverine.surgeRecovering(p), "skin still growing back");

		surgeSteps(p, 39);
		helper.assertTrue(Wolverine.surgeRecovering(p) && Wolverine.state(p).skinRecovery < 1.0f, "not quite whole");
		surgeSteps(p, 1);
		helper.assertTrue(Wolverine.state(p).skinRecovery == 1.0f, "20 s of full HP in total: skin fully back");
		helper.assertFalse(Wolverine.surgeRecovering(p), "recovery over");
		helper.assertFalse(hasAnySurgeDebuff(p), "no debuffs re-applied after 50%");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "wolverine_surge")
	public void skinRecoveryIsSavedAndOldSavesLoadClean(GameTestHelper helper) {
		var codec = com.projecthero.mod.wolverine.data.WolverineState.CODEC;
		var old = codec.parse(net.minecraft.nbt.NbtOps.INSTANCE, new net.minecraft.nbt.CompoundTag()).getOrThrow();
		helper.assertTrue(old.skinRecovery == 1.0f, "a pre-0.15.18 save has whole skin");
		var s = new com.projecthero.mod.wolverine.data.WolverineState();
		s.skinRecovery = 0.35f;
		var tag = codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, s).getOrThrow();
		var back = codec.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(back.skinRecovery == 0.35f, "progress round-trips, got " + back.skinRecovery);
		ServerPlayer p = wolverine(helper);
		WolverinePassives.tryEmergency(p);
		Wolverine.onPlayerRespawn(p);
		helper.assertTrue(Wolverine.state(p).skinRecovery == 1.0f, "a respawn brings the skin back");
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

	@GameTest(template = EMPTY_STRUCTURE)
	public void aLethalFallLeavesHalfAHeartAndTheHealingFactorTakesIt(GameTestHelper helper) {
		ServerPlayer p = wolverine(helper);
		p.setHealth(5.0f);
		helper.assertTrue(com.projecthero.mod.wolverine.WolverineDamage.survivedLethalFall(p, 25.0f), "a 25-damage fall (100 after his 75% reduction) is lethal at 5 HP, so it is survived"); // mock players do not take real damage in GameTests
		helper.assertTrue(p.isAlive() && Math.abs(p.getHealth() - 1.0f) < 1e-3f, "left at half a heart, has " + p.getHealth());
		var st = Wolverine.state(p);
		helper.assertTrue(Math.abs(st.healPool - (WolverineConfig.HEAL_POOL_MAX - 25.0f)) < 1e-3f, "the pool took the damage, has " + st.healPool);
		helper.assertTrue(st.legFleshStartedAt > 0L, "legs turn to flesh");
		var slow = p.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
		helper.assertTrue(slow != null && slow.getAmplifier() == 5, "Slowness VI");
		// a fall far beyond 100 only costs the pool 100
		p.setHealth(5.0f);
		var reset = st.copy();
		reset.healPool = WolverineConfig.HEAL_POOL_MAX;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.WOLVERINE_STATE, reset);
		com.projecthero.mod.wolverine.WolverineDamage.survivedLethalFall(p, 2000.0f);
		helper.assertTrue(p.isAlive() && Wolverine.state(p).healPool == WolverineConfig.HEAL_POOL_MAX - 100.0f, "at most 100 is taken from the pool, has " + Wolverine.state(p).healPool);
		helper.succeed();
	}
}
