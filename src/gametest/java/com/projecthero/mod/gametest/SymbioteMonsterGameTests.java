package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteAbilityManager;
import com.projecthero.mod.symbiote.SymbioteState;
import com.projecthero.mod.symbiote.SymbioteVitals;
import com.projecthero.mod.symbiote.SymbioteVitalsManager;
import com.projecthero.mod.symbiote.monster.SymbioteMonster;
import com.projecthero.mod.symbiote.monster.SymbioteMonsterMoves;
import com.projecthero.mod.symbiote.monster.SymbioteMonsterState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Symbiote Monster form (Shift + hold Z): the charge (an early release cancels it with no cooldown, 3 s transforms), the
 * 50-Biomass cost and refusal, the cocoon -> monster -> melt clock (stepped by moving the phase start, as CLAUDE.md asks of
 * time-based tests), the monster's R and G damage, the 4-minute cooldown and the form ending on death. Mock players are not reliably
 * ticked, so the tests run {@link SymbioteAbilityManager#serverTick} themselves (every tick function is safe to run twice a tick).
 * Damage is asserted as "took at least half of it" (other tests can leave the world on Hard).
 */
public class SymbioteMonsterGameTests implements FabricGameTest {

	/** A survival host, suit on, at (3.5, 2, 1.5) facing +Z, with the barriers cleared for a 7-block body. */
	private static ServerPlayer host(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-5, 0, -4), base.offset(5, 10, 8))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-5, -1, -4), base.offset(5, -1, 8))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0);
		p.setOnGround(true);
		Symbiote.grant(p);
		Symbiote.state(p).toggleReadyAt = 0L;
		Symbiote.toggle(p);
		Symbiote.state(p).transformStartTick -= 10_000L;
		Symbiote.tick(p);
		helper.assertTrue(Symbiote.isActive(p) && SymbioteAbilityManager.hasContext(p), "suited Normal host");
		setBiomass(p, SymbioteVitalsManager.MAX_HP);
		return p;
	}

	private static void setBiomass(ServerPlayer p, float hp) {
		SymbioteVitals c = SymbioteVitalsManager.vitals(p).copy();
		c.hp = hp;
		c.broken = false;
		p.setAttached(ModAttachments.SYMBIOTE_VITALS, c);
	}

	private static void shiftZ(ServerPlayer p, boolean down) {
		p.setShiftKeyDown(true);
		SymbioteAbilityManager.handle(p, AbilitySlot.SLOT_4, down);
	}

	private static void press(ServerPlayer p, AbilitySlot slot) {
		p.setShiftKeyDown(false);
		SymbioteAbilityManager.handle(p, slot, true);
		SymbioteAbilityManager.handle(p, slot, false);
	}

	private static boolean charging(ServerPlayer p) {
		return Symbiote.state(p).onslaughtChargeStart >= 0;
	}

	private static long monsterCooldown(ServerPlayer p) {
		return Math.max(0L, Symbiote.state(p).abilityCooldowns.get(SymbioteMonster.CDI_MONSTER) - p.level().getGameTime());
	}

	/** Straight into the monster (the charge and cocoon skipped). */
	private static void monster(ServerPlayer p) {
		SymbioteMonster.beginCocoon(p, p.level().getGameTime());
		SymbioteMonster.burst(p, p.level().getGameTime());
		p.setShiftKeyDown(false);
	}

	/** Moves the current phase's start back so the next tick sees it {@code age} ticks old. */
	private static void age(ServerPlayer p, long age) {
		SymbioteMonsterState s = SymbioteMonster.state(p);
		SymbioteMonster.setState(p, new SymbioteMonsterState(s.phase(), p.level().getGameTime() - age, s.move(), s.moveStart(), s.ready()));
	}

	private static int phase(ServerPlayer p) {
		return SymbioteMonster.state(p).phase();
	}

	private static float lost(LivingEntity e) {
		return e.getMaxHealth() - e.getHealth();
	}

	// ---------------- the charge ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void anEarlyReleaseCancelsWithNoCooldown(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		shiftZ(p, true);
		helper.assertTrue(charging(p), "Shift + hold Z starts the charge");
		helper.runAfterDelay(20, () -> {
			SymbioteAbilityManager.serverTick(p);
			helper.assertTrue(charging(p) && phase(p) == SymbioteMonsterState.NONE, "still charging after 1 s, not transformed");
			helper.assertTrue(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) < 0.01,
					"rooted while it charges");
			shiftZ(p, false);
			helper.assertTrue(!charging(p), "let go early: cancelled");
			helper.assertTrue(monsterCooldown(p) == 0L, "no cooldown for a cancelled charge");
			helper.assertTrue(SymbioteVitalsManager.biomass(p) >= SymbioteVitalsManager.MAX_HP - 0.01f, "no Biomass spent");
			SymbioteAbilityManager.serverTick(p);
			helper.assertTrue(p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) > 0.05, "free to move again");
			helper.assertTrue(phase(p) == SymbioteMonsterState.NONE, "never transformed");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void holdingThreeSecondsTransforms(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		float health = p.getMaxHealth();
		shiftZ(p, true);
		helper.runAfterDelay(SymbioteMonster.CHARGE_TICKS - 2, () -> {
			SymbioteAbilityManager.serverTick(p);
			helper.assertTrue(charging(p) && phase(p) == SymbioteMonsterState.NONE, "just under 3 s: still charging");
		});
		helper.runAfterDelay(SymbioteMonster.CHARGE_TICKS + 1, () -> {
			SymbioteAbilityManager.serverTick(p);
			helper.assertTrue(!charging(p), "the charge is over");
			helper.assertTrue(phase(p) == SymbioteMonsterState.COCOON, "3 s held: the cocoon");
			helper.assertTrue(Math.abs(SymbioteVitalsManager.biomass(p) - (SymbioteVitalsManager.MAX_HP - SymbioteMonster.BIOMASS_COST)) < 0.01f,
					"50 Biomass spent");
			// the cocoon: invulnerable
			helper.assertFalse(p.hurt(p.damageSources().generic(), 5.0f), "untouchable in the cocoon");
			age(p, SymbioteMonster.COCOON_TICKS);
			SymbioteAbilityManager.serverTick(p);
			helper.assertTrue(phase(p) == SymbioteMonsterState.MONSTER, "the cocoon bursts: the monster");
			helper.assertTrue(Math.abs(p.getBbHeight() - SymbioteMonster.HEIGHT) < 0.15, "7 blocks tall, was " + p.getBbHeight());
			helper.assertTrue(p.getMaxHealth() >= health * 2 - 0.01f, "+100% max health");
			helper.assertTrue(SymbioteMonster.damageFactor(p) == 0.5f, "half damage");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void itCostsFiftyBiomassAndRefusesWithLess(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		setBiomass(p, 40.0f);
		shiftZ(p, true);
		helper.assertTrue(!charging(p), "under 50 Biomass: refused");
		shiftZ(p, false);
		setBiomass(p, 120.0f);
		shiftZ(p, true);
		helper.assertTrue(charging(p), "with 120 Biomass it charges");
		// (the bar regenerates while it charges out of combat: set it again just before the charge completes)
		helper.runAfterDelay(SymbioteMonster.CHARGE_TICKS - 1, () -> {
			helper.assertTrue(charging(p), "still charging");
			setBiomass(p, 120.0f);
		});
		helper.runAfterDelay(SymbioteMonster.CHARGE_TICKS + 1, () -> {
			SymbioteAbilityManager.serverTick(p);
			helper.assertTrue(phase(p) == SymbioteMonsterState.COCOON, "transforming");
			helper.assertTrue(Math.abs(SymbioteVitalsManager.biomass(p) - 70.0f) < 1.0f, "120 - 50 = 70 Biomass left, was " + SymbioteVitalsManager.biomass(p));
			SymbioteMonster.endNow(p, null);
			helper.succeed();
		});
	}

	// ---------------- the clock ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void theFormLastsFortySecondsThenMelts(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		monster(p);
		age(p, SymbioteMonster.FORM_TICKS - 1);
		SymbioteAbilityManager.serverTick(p);
		helper.assertTrue(phase(p) == SymbioteMonsterState.MONSTER, "39.95 s: still the monster");
		helper.assertTrue(monsterCooldown(p) == 0L, "no cooldown while the form runs");
		age(p, SymbioteMonster.FORM_TICKS);
		SymbioteAbilityManager.serverTick(p);
		helper.assertTrue(phase(p) == SymbioteMonsterState.MELT, "40 s: the melt");
		long cd = monsterCooldown(p);
		helper.assertTrue(cd > SymbioteMonster.COOLDOWN_TICKS - 5 && cd <= SymbioteMonster.COOLDOWN_TICKS, "the cooldown starts as the form ends, was " + cd);
		age(p, SymbioteMonster.MELT_TICKS - 1);
		SymbioteAbilityManager.serverTick(p);
		helper.assertTrue(phase(p) == SymbioteMonsterState.MELT, "still melting");
		age(p, SymbioteMonster.MELT_TICKS);
		SymbioteAbilityManager.serverTick(p);
		helper.assertTrue(phase(p) == SymbioteMonsterState.NONE, "back in the suit");
		helper.assertTrue(p.getBbHeight() < 3.0f, "the host's own size again, was " + p.getBbHeight());
		helper.assertTrue(SymbioteMonster.damageFactor(p) == 1.0f, "no damage cut any more");
		helper.succeed();
	}

	// ---------------- the moves ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theMonsterSwipeAndGroundPoundHit(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		monster(p);
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new Vec3(3.5, 2, 4.8));
		golem.setPersistenceRequired();
		golem.setNoAi(true);
		Husk husk = helper.spawn(EntityType.HUSK, new Vec3(6.2, 2, 1.5));
		husk.setPersistenceRequired();
		husk.setNoAi(true);
		husk.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60, 0, false, false));
		helper.onEachTick(() -> SymbioteAbilityManager.serverTick(p));
		press(p, AbilitySlot.SLOT_1);
		helper.assertTrue(SymbioteMonster.state(p).move() == SymbioteMonsterState.MOVE_SWIPE, "R: the Claw Swipe plays");
		helper.assertTrue(lost(golem) < 1f, "the claws land on the strike frame, not the press");
		helper.runAfterDelay(SymbioteMonsterMoves.SWIPE_HIT_TICK + 2, () -> {
			helper.assertTrue(lost(golem) >= SymbioteMonsterMoves.SWIPE_DAMAGE * 0.5f, "the golem in front took the swipe, lost " + lost(golem));
			helper.assertTrue(lost(husk) < 1f, "the husk off to the side (outside the arc) did not");
			press(p, AbilitySlot.SLOT_2);
			helper.assertTrue(SymbioteMonster.state(p).move() == SymbioteMonsterState.MOVE_POUND, "G: the Ground Pound plays");
		});
		helper.runAfterDelay(SymbioteMonsterMoves.SWIPE_HIT_TICK + 2 + SymbioteMonsterMoves.POUND_HIT_TICK + 2, () -> {
			helper.assertTrue(lost(husk) >= SymbioteMonsterMoves.POUND_DAMAGE * 0.5f, "the pound hit the husk, lost " + lost(husk));
			helper.assertTrue(husk.getDeltaMovement().y > 0.2 || husk.getY() > helper.absoluteVec(new Vec3(0, 2.3, 0)).y, "and launched it");
			helper.assertTrue(SymbioteMonster.state(p).readyAt(AbilitySlot.SLOT_2.ordinal()) > p.level().getGameTime() + 80, "6 s cooldown");
			helper.succeed();
		});
	}

	// ---------------- the cooldown ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void theFormHasAFourMinuteCooldown(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		monster(p);
		p.setShiftKeyDown(true);
		SymbioteAbilityManager.handle(p, AbilitySlot.SLOT_4, true); // Shift+Z in the form: end it early
		helper.assertTrue(phase(p) == SymbioteMonsterState.MELT, "Shift+Z ends the form early");
		long cd = monsterCooldown(p);
		helper.assertTrue(cd > SymbioteMonster.COOLDOWN_TICKS - 5 && cd <= SymbioteMonster.COOLDOWN_TICKS, "4 minutes, was " + cd);
		SymbioteMonster.endNow(p, null);
		SymbioteAbilityManager.handle(p, AbilitySlot.SLOT_4, false);
		shiftZ(p, true);
		helper.assertTrue(!charging(p), "cooling down: refused");
		shiftZ(p, false);
		SymbioteState c = Symbiote.state(p).copy();
		c.abilityCooldowns.set(SymbioteMonster.CDI_MONSTER, p.level().getGameTime() - 1);
		p.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		shiftZ(p, true);
		helper.assertTrue(charging(p), "ready again: it charges");
		helper.assertTrue(SymbioteState.COOLDOWN_SLOTS == 18 && Symbiote.state(p).abilityCooldowns.size() == 18, "18 cooldown entries (17 = the form)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void dyingEndsTheForm(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		monster(p);
		helper.assertTrue(SymbioteMonster.isMonster(p) && p.getBbHeight() > 6.0f, "transformed");
		p.die(p.damageSources().generic());
		helper.assertTrue(phase(p) == SymbioteMonsterState.NONE, "death ends the form");
		helper.assertTrue(monsterCooldown(p) > SymbioteMonster.COOLDOWN_TICKS - 5, "and starts the cooldown");
		helper.assertTrue(p.getBbHeight() < 3.0f, "the 7-block body is gone, was " + p.getBbHeight());
		helper.succeed();
	}
}
