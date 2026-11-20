package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteAbilityManager;
import com.projecthero.mod.symbiote.SymbioteAnim;
import com.projecthero.mod.symbiote.SymbioteState;
import com.projecthero.mod.symbiote.SymbioteVitals;
import com.projecthero.mod.symbiote.SymbioteVitalsManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Symbiote moveset v2 ({@code docs/SYMBIOTE_MOVESET_V2_DESIGN.md}): tap vs hold on R / G / Z / C, the Tendril Slam on the 3rd
 * strike in a row, Impaling Spear, Spike Rain, Dive Bomb, Ground Eruption, Maw Bite, Devour (execute under 25%, Maw Bite above, a heavy
 * bite on a boss), the V Form cycle, the suit-on punch passives, and the old moves still firing. Mock players are not reliably ticked,
 * so the tests call {@link SymbioteAbilityManager#serverTick} themselves (every tick function is safe to run twice in one tick).
 * Damage is asserted as "took at least a share of it" (other tests can leave the world on Hard; mobs are fire resistant in the sun).
 */
public class SymbioteMovesetV2GameTests implements FabricGameTest {

	/** A survival host, suit on, at (3.5, 2, 1.5) facing +Z on a stone floor in a cleared room. */
	private static ServerPlayer host(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, 0, -1), base.offset(3, 5, 6))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, -1, -1), base.offset(3, -1, 6))) {
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
		return p;
	}

	private static void press(ServerPlayer p, AbilitySlot slot, boolean down) {
		SymbioteAbilityManager.handle(p, slot, down);
	}

	private static void tap(ServerPlayer p, AbilitySlot slot) {
		press(p, slot, true);
		press(p, slot, false);
	}

	/** Key down now; after the hold time the server ticks (the hold move starts) and {@code check} runs with the key still down. */
	private static void hold(GameTestHelper helper, ServerPlayer p, AbilitySlot slot, Runnable whileHeld) {
		press(p, slot, true);
		helper.runAfterDelay(SymbioteAbilityManager.HOLD_TICKS + 1, () -> {
			SymbioteAbilityManager.serverTick(p);
			whileHeld.run();
		});
	}

	private static long cd(ServerPlayer p, int index) {
		return Math.max(0L, Symbiote.state(p).abilityCooldowns.get(index) - p.level().getGameTime());
	}

	private static void clearCooldowns(ServerPlayer p) {
		SymbioteState c = Symbiote.state(p).copy();
		for (int i = 0; i < c.abilityCooldowns.size(); i++) {
			c.abilityCooldowns.set(i, 0L);
		}
		p.setAttached(ModAttachments.SYMBIOTE_STATE, c);
	}

	private static int anim(ServerPlayer p) {
		return p.getAttachedOrCreate(ModAttachments.SYMBIOTE_VITALS).animId;
	}

	private static void setBiomass(ServerPlayer p, float hp) {
		SymbioteVitals c = SymbioteVitalsManager.vitals(p).copy();
		c.hp = hp;
		c.broken = false;
		p.setAttached(ModAttachments.SYMBIOTE_VITALS, c);
	}

	private static IronGolem golem(GameTestHelper helper, double x, double z) {
		IronGolem g = helper.spawn(EntityType.IRON_GOLEM, new Vec3(x, 2, z));
		g.setPersistenceRequired();
		g.setNoAi(true);
		return g;
	}

	private static Husk husk(GameTestHelper helper, double x, double z) {
		Husk h = helper.spawn(EntityType.HUSK, new Vec3(x, 2, z));
		h.setPersistenceRequired();
		h.setNoAi(true);
		h.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60, 0, false, false));
		return h;
	}

	private static float lost(LivingEntity e) {
		return e.getMaxHealth() - e.getHealth();
	}

	// ---------------- tap vs hold ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void aTapFiresOnReleaseAndAHoldFiresTheHoldMoveOnly(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		press(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(SymbioteAbilityManager.pending(p, AbilitySlot.SLOT_1), "the R press waits");
		helper.assertTrue(cd(p, 0) == 0L, "nothing fired on the press");
		press(p, AbilitySlot.SLOT_1, false);
		helper.assertTrue(cd(p, 0) > 0L, "a quick release is the tap: Tendril Strike fired");
		helper.assertTrue(cd(p, SymbioteAbilityManager.CDI_SPEAR) == 0L && !SymbioteAbilityManager.spearCharging(p), "no Spear on a tap");
		hold(helper, p, AbilitySlot.SLOT_2, () -> {
			helper.assertTrue(cd(p, SymbioteAbilityManager.CDI_RAIN) > 0L, "held: Spike Rain started");
			helper.assertTrue(cd(p, 1) == 0L, "and no Spike Shot");
			press(p, AbilitySlot.SLOT_2, false);
			helper.assertTrue(cd(p, 1) == 0L, "the release after a hold does not fire the tap as well");
			helper.succeed();
		});
	}

	// ---------------- R ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyThirdTendrilStrikeInARowIsTheSlam(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		IronGolem g = golem(helper, 3.5, 5.5);
		float[] taken = new float[3];
		int[] anims = new int[3];
		for (int i = 0; i < 3; i++) {
			clearCooldowns(p);
			g.invulnerableTime = 0;
			float before = g.getHealth();
			tap(p, AbilitySlot.SLOT_1);
			taken[i] = before - g.getHealth();
			anims[i] = anim(p);
		}
		helper.assertTrue(anims[0] == SymbioteAnim.TENDRIL_STRIKE && anims[1] == SymbioteAnim.TENDRIL_STRIKE,
				"strikes 1 and 2 are Tendril Strikes, got " + anims[0] + ", " + anims[1]);
		helper.assertTrue(anims[2] == SymbioteAnim.TENDRIL_SLAM, "strike 3 is the Tendril Slam, got " + anims[2]);
		helper.assertTrue(taken[0] > 0f && taken[2] > taken[0] * 1.2f, "the Slam hits harder: " + taken[0] + " then " + taken[2]);
		helper.assertTrue(g.distanceTo(p) < 4.0f, "the golem was yanked in, now " + g.distanceTo(p) + " away");
		// the chain starts over: the 4th is a plain strike again
		clearCooldowns(p);
		g.invulnerableTime = 0;
		tap(p, AbilitySlot.SLOT_1);
		helper.assertTrue(anim(p) == SymbioteAnim.TENDRIL_STRIKE, "the 4th strike is a plain one");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theImpalingSpearPiercesTheLineAndPinsTheFirst(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		IronGolem near = golem(helper, 3.5, 4.0);
		IronGolem far = golem(helper, 3.5, 6.5);
		hold(helper, p, AbilitySlot.SLOT_1, () -> {
			helper.assertTrue(SymbioteAbilityManager.spearCharging(p), "holding R charges the Spear");
			helper.assertTrue(cd(p, 0) == 0L, "no Tendril Strike while charging");
			press(p, AbilitySlot.SLOT_1, false);
			helper.assertTrue(!SymbioteAbilityManager.spearCharging(p) && cd(p, SymbioteAbilityManager.CDI_SPEAR) > 0L, "released: thrown");
			helper.assertTrue(lost(near) >= SymbioteAbilityManager.SPEAR_MIN_DAMAGE * 0.5f, "the first golem is run through: " + lost(near));
			helper.assertTrue(lost(far) >= SymbioteAbilityManager.SPEAR_MIN_DAMAGE * 0.5f, "and the one behind it: " + lost(far));
			helper.assertTrue(SymbioteAbilityManager.pinnedTarget(p) == near.getId(), "the first one is pinned");
			helper.assertTrue(cd(p, 0) == 0L, "the release did not fire a Tendril Strike too");
			helper.succeed();
		});
	}

	// ---------------- G ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void spikeRainFallsOnTheAimedArea(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		Husk h = husk(helper, 3.5, 5.5);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0.0f, 22.0f); // looking down at the husk
		hold(helper, p, AbilitySlot.SLOT_2, () -> {
			helper.assertTrue(SymbioteAbilityManager.raining(p), "the rain started");
			press(p, AbilitySlot.SLOT_2, false);
		});
		helper.onEachTick(() -> SymbioteAbilityManager.serverTick(p));
		helper.runAfterDelay(SymbioteAbilityManager.HOLD_TICKS + 75, () -> {
			helper.assertTrue(!h.isAlive() || lost(h) > 0f, "the husk in the circle was hit by the rain");
			helper.assertTrue(!SymbioteAbilityManager.raining(p), "it stops after 3 s");
			helper.succeed();
		});
	}

	// ---------------- X ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void xInTheAirIsADiveBombAndTheLandingSlams(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		// on the ground X is still the Lunge
		tap(p, AbilitySlot.SLOT_3);
		helper.assertTrue(anim(p) == SymbioteAnim.LUNGE && !SymbioteAbilityManager.diving(p), "grounded: the Lunge");
		clearCooldowns(p);
		Husk h = husk(helper, 5.0, 1.5);
		Vec3 ground = p.position();
		p.moveTo(ground.x, ground.y + 4.0, ground.z, 0.0f, 0.0f);
		p.setOnGround(false);
		helper.assertTrue(SymbioteAbilityManager.canDiveBomb(p), "4 blocks up counts as in the air");
		tap(p, AbilitySlot.SLOT_3);
		helper.assertTrue(SymbioteAbilityManager.diving(p) && anim(p) == SymbioteAnim.DIVE_BOMB, "airborne X is the Dive Bomb");
		helper.assertTrue(p.getDeltaMovement().y < -1.0, "driven straight down, vy " + p.getDeltaMovement().y);
		helper.runAfterDelay(2, () -> {
			p.moveTo(ground.x, ground.y, ground.z, 0.0f, 0.0f);
			p.setOnGround(true);
			SymbioteAbilityManager.serverTick(p);
			helper.assertTrue(!SymbioteAbilityManager.diving(p), "landed");
			helper.assertTrue(lost(h) >= SymbioteAbilityManager.DIVE_BASE_DAMAGE * 0.5f || !h.isAlive(), "the slam hit the husk: " + lost(h));
			helper.succeed();
		});
	}

	// ---------------- Z ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void holdZEruptsALineOfTendrils(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		IronGolem a = golem(helper, 3.5, 3.5);
		IronGolem b = golem(helper, 3.5, 6.0);
		hold(helper, p, AbilitySlot.SLOT_4, () -> {
			helper.assertTrue(cd(p, SymbioteAbilityManager.CDI_ERUPTION) > 0L, "held: Ground Eruption");
			press(p, AbilitySlot.SLOT_4, false);
			helper.assertTrue(cd(p, 3) == 0L, "no Barrage");
		});
		helper.onEachTick(() -> SymbioteAbilityManager.serverTick(p));
		helper.runAfterDelay(SymbioteAbilityManager.HOLD_TICKS + 14, () -> {
			helper.assertTrue(lost(a) >= SymbioteAbilityManager.ERUPTION_DAMAGE * 0.5f, "the near golem: " + lost(a));
			helper.assertTrue(lost(b) >= SymbioteAbilityManager.ERUPTION_DAMAGE * 0.5f, "the far golem: " + lost(b));
			helper.assertTrue(lost(a) < SymbioteAbilityManager.ERUPTION_DAMAGE * 1.6f, "each is hit once: " + lost(a));
			helper.succeed();
		});
	}

	// ---------------- C ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void mawBiteBitesAndFeeds(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		setBiomass(p, 100.0f);
		IronGolem g = golem(helper, 3.5, 4.0);
		tap(p, AbilitySlot.SLOT_6);
		helper.assertTrue(anim(p) == SymbioteAnim.MAW_BITE, "C is the Maw Bite");
		helper.assertTrue(lost(g) >= SymbioteAbilityManager.MAW_DAMAGE * 0.5f, "it bit: " + lost(g));
		float bio = SymbioteVitalsManager.biomass(p);
		helper.assertTrue(bio >= 100.0f + SymbioteAbilityManager.MAW_BIOMASS * 0.9f, "and fed: Biomass " + bio);
		helper.assertTrue(cd(p, SymbioteAbilityManager.CDI_MAW) > 0L && cd(p, 5) == 0L, "Maw Bite's own cooldown, not the grab's");
		helper.assertTrue(!SymbioteVitalsManager.thornsMode(p), "C no longer toggles the Thorns");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void devourEatsATargetUnderAQuarter(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		setBiomass(p, 100.0f);
		Husk h = husk(helper, 3.5, 3.5);
		h.setHealth(h.getMaxHealth() * 0.2f);
		hold(helper, p, AbilitySlot.SLOT_6, () -> {
			helper.assertTrue(SymbioteAbilityManager.devouring(p) && anim(p) == SymbioteAnim.DEVOUR, "Devour started (tongue anim)");
			press(p, AbilitySlot.SLOT_6, false);
		});
		helper.onEachTick(() -> SymbioteAbilityManager.serverTick(p));
		helper.runAfterDelay(SymbioteAbilityManager.HOLD_TICKS + SymbioteAbilityManager.DEVOUR_PULL_TICKS + 6, () -> {
			helper.assertFalse(h.isAlive(), "the husk was eaten");
			float bio = SymbioteVitalsManager.biomass(p);
			helper.assertTrue(bio >= 100.0f + SymbioteAbilityManager.DEVOUR_BIOMASS * 0.9f, "a big Biomass meal: " + bio);
			helper.assertTrue(cd(p, SymbioteAbilityManager.CDI_DEVOUR) > 0L, "Devour cooling down");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void devourAboveAQuarterIsAMawBite(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		IronGolem g = golem(helper, 3.5, 3.5);
		hold(helper, p, AbilitySlot.SLOT_6, () -> {
			helper.assertTrue(!SymbioteAbilityManager.devouring(p), "a healthy golem is not devoured");
			helper.assertTrue(anim(p) == SymbioteAnim.MAW_BITE, "it gets a Maw Bite instead");
			helper.assertTrue(g.isAlive() && lost(g) >= SymbioteAbilityManager.MAW_DAMAGE * 0.5f, "bitten: " + lost(g));
			helper.assertTrue(cd(p, SymbioteAbilityManager.CDI_DEVOUR) == 0L, "Devour's cooldown untouched");
			press(p, AbilitySlot.SLOT_6, false);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossesAreNotDevouredTheyTakeAHeavyBite(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		Husk boss = husk(helper, 3.5, 3.5);
		boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400.0);
		boss.setHealth(40.0f); // 10%: devourable by health, but a boss
		helper.assertTrue(SymbioteAbilityManager.isBoss(boss), "400 max health is a boss");
		hold(helper, p, AbilitySlot.SLOT_6, () -> {
			helper.assertTrue(!SymbioteAbilityManager.devouring(p), "a boss is never devoured");
			helper.assertTrue(boss.isAlive(), "still alive");
			helper.assertTrue(40.0f - boss.getHealth() >= SymbioteAbilityManager.HEAVY_BITE_DAMAGE * 0.5f,
					"it took the heavy bite: " + (40.0f - boss.getHealth()));
			helper.assertTrue(cd(p, SymbioteAbilityManager.CDI_DEVOUR) > 0L, "the heavy bite uses Devour's cooldown");
			press(p, AbilitySlot.SLOT_6, false);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cAgainStillThrowsAGrabbedTargetWithoutBiting(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		husk(helper, 3.5, 4.0);
		p.setShiftKeyDown(true);
		press(p, AbilitySlot.SLOT_6, true);
		press(p, AbilitySlot.SLOT_6, false);
		helper.assertTrue(Symbiote.state(p).tendrilGrabHeld, "Shift+C grabbed the husk");
		p.setShiftKeyDown(false);
		tap(p, AbilitySlot.SLOT_6);
		helper.assertTrue(!Symbiote.state(p).tendrilGrabHeld, "C again threw it");
		helper.assertTrue(cd(p, SymbioteAbilityManager.CDI_MAW) == 0L, "and did not bite as well");
		helper.succeed();
	}

	// ---------------- V ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void vCyclesNormalBladeThorns(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		helper.assertTrue(SymbioteVitalsManager.form(p) == SymbioteVitalsManager.FORM_NORMAL, "starts Normal");
		tap(p, AbilitySlot.SLOT_5);
		helper.assertTrue(SymbioteVitalsManager.form(p) == SymbioteVitalsManager.FORM_BLADE && SymbioteVitalsManager.bladeActive(p), "-> Blade");
		// v0.17.5 weapons: Axe, Mace and Whip come between the Blade and Thorns (SymbioteWeaponGameTests checks each one)
		tap(p, AbilitySlot.SLOT_5);
		tap(p, AbilitySlot.SLOT_5);
		tap(p, AbilitySlot.SLOT_5);
		helper.assertTrue(SymbioteVitalsManager.form(p) == SymbioteVitalsManager.FORM_WHIP, "-> Axe -> Mace -> Whip");
		tap(p, AbilitySlot.SLOT_5);
		helper.assertTrue(SymbioteVitalsManager.form(p) == SymbioteVitalsManager.FORM_THORNS && !SymbioteVitalsManager.bladeActive(p)
				&& SymbioteVitalsManager.thornsMode(p), "-> Thorns (weapon gone)");
		tap(p, AbilitySlot.SLOT_5);
		helper.assertTrue(SymbioteVitalsManager.form(p) == SymbioteVitalsManager.FORM_NORMAL && !SymbioteVitalsManager.thornsMode(p), "-> Normal");
		// with something in the hand there is no Blade: Normal goes straight to Thorns
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		tap(p, AbilitySlot.SLOT_5);
		helper.assertTrue(SymbioteVitalsManager.form(p) == SymbioteVitalsManager.FORM_THORNS, "full hand: Normal -> Thorns");
		// Shift+V is still the shield
		p.setShiftKeyDown(true);
		tap(p, AbilitySlot.SLOT_5);
		helper.assertTrue(SymbioteAbilityManager.shieldActive(p), "Shift+V raises the Shield");
		helper.succeed();
	}

	// ---------------- the old moves ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void theOldMovesStillFire(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		husk(helper, 3.5, 4.5);
		tap(p, AbilitySlot.SLOT_2);
		helper.assertTrue(cd(p, 1) > 0L, "G tap: Spike Shot");
		tap(p, AbilitySlot.SLOT_4);
		helper.assertTrue(cd(p, 3) > 0L && anim(p) == SymbioteAnim.BARRAGE, "Z tap: Tendril Barrage");
		clearCooldowns(p);
		p.setShiftKeyDown(true);
		press(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(cd(p, SymbioteAbilityManager.CDI_SWEEP) > 100L, "Shift+R fires on the press: Tendril Sweep (10 s)");
		press(p, AbilitySlot.SLOT_1, false);
		press(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(SymbioteVitalsManager.vitals(p).spikeConeReadyAt > p.level().getGameTime(), "Shift+G: Spike Fan");
		press(p, AbilitySlot.SLOT_2, false);
		press(p, AbilitySlot.SLOT_3, true);
		helper.assertTrue(anim(p) == SymbioteAnim.GRAPPLE || anim(p) == SymbioteAnim.TENDRIL_STRIKE, "Shift+X: Grapple");
		press(p, AbilitySlot.SLOT_3, false);
		press(p, AbilitySlot.SLOT_4, true);
		helper.assertTrue(SymbioteAbilityManager.onslaughtCharging(p), "Shift + hold Z charges the Monster form (v0.17.5: it replaced Onslaught)");
		press(p, AbilitySlot.SLOT_4, false);
		helper.assertTrue(!SymbioteAbilityManager.onslaughtCharging(p), "an early release cancels it");
		helper.succeed();
	}

	// ---------------- v0.17.5: every variant on its own cooldown ----------------

	/** User: "if i use shift+R on symbiote i cant use regular R while Shift+R is on cooldown". */
	@GameTest(template = EMPTY_STRUCTURE)
	public void shiftVariantsNeverBlockThePlainMove(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		husk(helper, 3.5, 4.5);
		// Shift+R (Tendril Sweep) goes on cooldown ...
		p.setShiftKeyDown(true);
		press(p, AbilitySlot.SLOT_1, true);
		press(p, AbilitySlot.SLOT_1, false);
		helper.assertTrue(cd(p, SymbioteAbilityManager.CDI_SWEEP) > 100L, "Tendril Sweep cooling down");
		helper.assertTrue(cd(p, 0) == 0L, "Tendril Strike's own cooldown untouched by the Sweep");
		// ... and a plain R tap still fires Tendril Strike
		p.setShiftKeyDown(false);
		tap(p, AbilitySlot.SLOT_1);
		helper.assertTrue(cd(p, 0) > 0L, "R still fires while Shift+R is on cooldown");
		// and the other way round: R cooling down, Shift+R is refused only by its own entry
		long sweep = cd(p, SymbioteAbilityManager.CDI_SWEEP);
		clearCooldowns(p);
		tap(p, AbilitySlot.SLOT_1);
		p.setShiftKeyDown(true);
		press(p, AbilitySlot.SLOT_1, true);
		press(p, AbilitySlot.SLOT_1, false);
		helper.assertTrue(sweep > 0L && cd(p, SymbioteAbilityManager.CDI_SWEEP) > 100L, "Shift+R fires while R is on cooldown");
		// the Monster form (Shift + hold Z; v0.17.5, it replaced Onslaught) vs Barrage (Z tap): separate entries
		int monster = com.projecthero.mod.symbiote.monster.SymbioteMonster.CDI_MONSTER;
		clearCooldowns(p);
		SymbioteState c = Symbiote.state(p).copy();
		c.abilityCooldowns.set(monster, p.level().getGameTime() + 1000L);
		p.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		p.setShiftKeyDown(false);
		tap(p, AbilitySlot.SLOT_4);
		helper.assertTrue(cd(p, 3) > 0L, "Z still fires a Barrage while the Monster form is on cooldown");
		helper.assertTrue(cd(p, monster) > 900L, "and the form's own entry is unchanged");
		helper.assertTrue(SymbioteAbilityManager.onslaughtReady(p, p.level().getGameTime()) == false, "Shift + hold Z reads the form's own entry");
		// Grapple (Shift+X) on its own synced entry, not the Lunge's
		clearCooldowns(p);
		c = Symbiote.state(p).copy();
		c.abilityCooldowns.set(SymbioteAbilityManager.CDI_GRAPPLE, p.level().getGameTime() + 1000L);
		p.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		helper.assertTrue(!SymbioteAbilityManager.grappleReady(p, p.level().getGameTime()), "Grapple reads its own entry");
		helper.assertTrue(SymbioteAbilityManager.leapReady(p, p.level().getGameTime()), "the Lunge is still ready");
		helper.assertTrue(SymbioteState.COOLDOWN_SLOTS == 18 && Symbiote.state(p).abilityCooldowns.size() == 18, "18 cooldown entries (v0.17.5: 15 = Symbiote Wings, 17 = Monster form)");
		helper.succeed();
	}

	// ---------------- passives ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitOnPunchesReachFurtherAndEveryThirdKnocksBack(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		double base = p.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getBaseValue();
		SymbioteAbilityManager.serverTick(p);
		double range = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		helper.assertTrue(Math.abs(range - base - SymbioteAbilityManager.SUIT_REACH_BONUS) < 1.0e-6, "+1 reach: " + base + " -> " + range);
		Husk h = husk(helper, 3.5, 3.0);
		helper.assertFalse(SymbioteAbilityManager.onMeleeSwing(p, h), "punch 1");
		helper.assertFalse(SymbioteAbilityManager.onMeleeSwing(p, h), "punch 2");
		Vec3 before = h.getDeltaMovement();
		helper.assertTrue(SymbioteAbilityManager.onMeleeSwing(p, h), "punch 3 is the knockback one");
		helper.assertTrue(h.getDeltaMovement().subtract(before).horizontalDistance() > 0.3, "it was thrown back");
		helper.assertFalse(SymbioteAbilityManager.onMeleeSwing(p, h), "and the count starts over");
		// suit off (a lone host keeps the kit, not the suit passives)
		Symbiote.state(p).toggleReadyAt = 0L;
		Symbiote.toggle(p);
		Symbiote.state(p).transformStartTick -= 10_000L;
		Symbiote.tick(p);
		SymbioteAbilityManager.serverTick(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - base) < 1.0e-6, "suit off: normal reach");
		helper.assertFalse(SymbioteAbilityManager.suitPassivesLive(p), "suit off: no punch combo");
		helper.succeed();
	}

}
