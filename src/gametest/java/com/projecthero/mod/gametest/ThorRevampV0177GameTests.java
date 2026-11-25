package com.projecthero.mod.gametest;

import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.StormEnergy;
import com.projecthero.mod.power.ThorAbility;
import com.projecthero.mod.power.ThorFx;
import com.projecthero.mod.power.ThorKit;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.power.ThorState;
import com.projecthero.mod.power.ThorStormbreakerKit;
import com.projecthero.mod.power.ThorSunder;
import com.projecthero.mod.power.ThorUltimates;
import com.projecthero.mod.power.ThorVisuals;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Thor Cosmic revamp ({@link ThorKit}): the weapon state is picked from the hands; each state routes the keys to its
 * own moves; damage sits in the Cosmic range and no hit passes 60; Sunder adds 20%; Executioner's Drop is x1.5 under 30% but
 * never over 30; Twin Gods pay double Storm Energy; no flight unarmed; Unarmed cooldowns are 25% shorter; each ultimate fires.
 * Mock players are never ticked, so melee modifiers are reconciled by hand; timed moves run on the server tick
 * ({@code ThorSequences}). Each test has its own batch, high above the structure.
 */
public class ThorRevampV0177GameTests implements FabricGameTest {
	private static final int ALT = 60;

	private static Vec3 at(GameTestHelper helper, double x, double z) {
		return helper.absoluteVec(new Vec3(x, 2.0 + ALT, z));
	}

	/** A worthy Thor bound to a hammer (so Unarmed has the kit), holding {@code main} / {@code off} (null = empty). */
	private static ServerPlayer thor(GameTestHelper helper, net.minecraft.world.item.Item main, net.minecraft.world.item.Item off) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 pos = at(helper, 1.5, 1.5);
		p.moveTo(pos.x, pos.y, pos.z, 0.0f, 0.0f); // facing +Z
		p.setNoGravity(true);
		Worthiness.setScore(p, Worthiness.TEST_WORTHY_SCORE);
		p.setAttached(ModAttachments.BOUND_HAMMER_ID, UUID.randomUUID());
		p.getInventory().selected = 0;
		p.setItemInHand(InteractionHand.MAIN_HAND, main == null ? ItemStack.EMPTY : new ItemStack(main));
		p.setItemInHand(InteractionHand.OFF_HAND, off == null ? ItemStack.EMPTY : new ItemStack(off));
		StormEnergy.set(p, StormEnergy.MAX);
		return p;
	}

	private static Zombie dummy(GameTestHelper helper, double x, double z) {
		Zombie z0 = EntityType.HUSK.create(helper.getLevel());
		Vec3 pos = at(helper, x, z);
		z0.moveTo(pos.x, pos.y, pos.z, 180.0f, 0.0f);
		z0.setNoAi(true);
		z0.setNoGravity(true);
		z0.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		z0.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		z0.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		z0.setHealth(500.0f);
		helper.getLevel().addFreshEntity(z0);
		return z0;
	}

	private static void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot.number(), true);
		AbilityRouter.handleInput(p, slot.number(), false);
		p.setShiftKeyDown(false);
	}

	private static boolean cooling(ServerPlayer p, ThorAbility a) {
		return !ThorKit.ready(p, a);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0177_state")
	public void theStateComesFromTheHands(GameTestHelper helper) {
		ServerPlayer p = thor(helper, null, null);
		helper.assertTrue(ThorState.of(p) == ThorState.UNARMED, "empty hands: Unarmed");
		helper.assertTrue(ThorState.hasKit(p), "a bound, worthy Thor has the kit with empty hands");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MJOLNIR));
		helper.assertTrue(ThorState.of(p) == ThorState.MJOLNIR, "Mjolnir in hand: Mjolnir");
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.STORMBREAKER));
		helper.assertTrue(ThorState.of(p) == ThorState.STORMBREAKER, "Stormbreaker in the off hand: Stormbreaker");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MJOLNIR));
		helper.assertTrue(ThorState.of(p) == ThorState.TWIN, "both: Twin Gods");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STORMBREAKER));
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.MJOLNIR));
		helper.assertTrue(ThorState.of(p) == ThorState.TWIN, "both, the other way round: Twin Gods");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.STICK));
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		helper.assertTrue(ThorState.of(p) == ThorState.UNARMED, "anything else in hand: Unarmed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0177_routing", timeoutTicks = 60)
	public void eachStateRoutesTheKeysToItsOwnMoves(GameTestHelper helper) {
		Object[][] cases = {
				{ null, null, AbilitySlot.SLOT_2, false, ThorAbility.U_LIGHTNING_PALM },
				{ null, null, AbilitySlot.SLOT_6, false, ThorAbility.U_STORM_RUSH },
				{ null, null, AbilitySlot.SLOT_6, true, ThorAbility.U_STATIC_BURST },
				{ null, null, AbilitySlot.SLOT_4, false, ThorAbility.U_GROUND_POUND },
				{ null, null, AbilitySlot.SLOT_3, false, ThorAbility.U_STORM_SPRINT },
				{ null, null, AbilitySlot.SLOT_3, true, ThorAbility.U_THUNDER_LEAP },
				{ ModItems.MJOLNIR, null, AbilitySlot.SLOT_2, false, ThorAbility.LIGHTNING_STRIKE },
				{ ModItems.MJOLNIR, null, AbilitySlot.SLOT_6, false, ThorAbility.CHAIN_LIGHTNING },
				{ ModItems.MJOLNIR, null, AbilitySlot.SLOT_4, false, ThorAbility.THUNDERCLAP },
				{ ModItems.MJOLNIR, null, AbilitySlot.SLOT_3, false, ThorAbility.M_STORM_DASH },
				{ ModItems.MJOLNIR, null, AbilitySlot.SLOT_1, true, ThorAbility.M_WHIRLWIND },
				{ ModItems.STORMBREAKER, null, AbilitySlot.SLOT_2, false, ThorAbility.S_STORM_CLEAVE },
				{ ModItems.STORMBREAKER, null, AbilitySlot.SLOT_6, false, ThorAbility.S_RENDING_ARC },
				{ ModItems.STORMBREAKER, null, AbilitySlot.SLOT_4, false, ThorAbility.S_EXECUTIONER },
				{ ModItems.STORMBREAKER, null, AbilitySlot.SLOT_3, false, ThorAbility.S_BIFROST_DASH },
				{ ModItems.STORMBREAKER, null, AbilitySlot.SLOT_2, true, ThorAbility.S_BIFROST_STEP },
				{ ModItems.MJOLNIR, ModItems.STORMBREAKER, AbilitySlot.SLOT_2, false, ThorAbility.T_THUNDERSTRUCK },
				{ ModItems.MJOLNIR, ModItems.STORMBREAKER, AbilitySlot.SLOT_6, false, ThorAbility.T_CROSSFIRE },
				{ ModItems.MJOLNIR, ModItems.STORMBREAKER, AbilitySlot.SLOT_4, false, ThorAbility.TWIN_THUNDER },
				{ ModItems.MJOLNIR, ModItems.STORMBREAKER, AbilitySlot.SLOT_1, true, ThorAbility.T_TWIN_THROW },
				{ ModItems.MJOLNIR, ModItems.STORMBREAKER, AbilitySlot.SLOT_3, false, ThorAbility.M_STORM_DASH },
				{ null, null, AbilitySlot.SLOT_5, false, ThorAbility.STORM_CALL },
				{ ModItems.STORMBREAKER, null, AbilitySlot.SLOT_5, true, ThorAbility.GOD_OF_THUNDER_BUFF },
		};
		for (Object[] c : cases) {
			ServerPlayer p = thor(helper, (net.minecraft.world.item.Item) c[0], (net.minecraft.world.item.Item) c[1]);
			ThorState st = ThorState.of(p);
			AbilitySlot slot = (AbilitySlot) c[2];
			boolean shift = (Boolean) c[3];
			ThorAbility want = (ThorAbility) c[4];
			helper.assertTrue(ThorKit.cooldownOf(st, slot, shift) == want, st + " " + slot + " shift=" + shift + " shows " + want + " on the HUD");
			press(p, slot, shift);
			helper.assertTrue(cooling(p, want), st + " " + slot + " shift=" + shift + " fires " + want);
			p.discard();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0177_damage", timeoutTicks = 80)
	public void damageIsCosmicAndNeverOverSixty(GameTestHelper helper) {
		ServerPlayer p = thor(helper, ModItems.MJOLNIR, null);
		Zombie z = dummy(helper, 1.5, 5.5);
		ThorPowers.lightningStrike(p);
		float strike = 500f - z.getHealth();
		helper.assertTrue(Math.abs(strike - ThorKit.STRIKE_DAMAGE) < 0.01f, "Lightning Strike lands 26 (got " + strike + ")");
		helper.assertTrue(strike >= 22f && strike <= 30f, "inside the Cosmic ability range");
		// the hard cap: a huge request lands 60
		Zombie z2 = dummy(helper, 4.5, 5.5);
		ThorKit.hit(p, z2, 500f, ThorKit.Kind.PHYSICAL);
		helper.assertTrue(Math.abs(500f - z2.getHealth() - ThorKit.MAX_HIT) < 0.01f, "no single hit over 60 (got " + (500f - z2.getHealth()) + ")");
		// Storm-Charged (his own Storm Call) adds 30% but still never past 60
		ThorPowers.stormCall(p);
		Zombie z3 = dummy(helper, -2.5, 5.5);
		ThorKit.hit(p, z3, 20f, ThorKit.Kind.PHYSICAL);
		helper.assertTrue(Math.abs(500f - z3.getHealth() - 26f) < 0.01f, "Storm Call charges him: 20 -> 26 (got " + (500f - z3.getHealth()) + ")");
		Zombie z4 = dummy(helper, -2.5, 8.5);
		ThorKit.hit(p, z4, ThorKit.WRATH_FINAL, ThorKit.Kind.LIGHTNING);
		helper.assertTrue(Math.abs(500f - z4.getHealth() - ThorKit.MAX_HIT) < 0.01f, "60 x 1.3 is still capped at 60 (got " + (500f - z4.getHealth()) + ")");
		// melee per state (a mock player is never ticked: reconcile by hand)
		double[] want = { 24, 24, 26, 27 };
		net.minecraft.world.item.Item[][] hands = { { null, null }, { ModItems.MJOLNIR, null }, { ModItems.STORMBREAKER, null },
				{ ModItems.STORMBREAKER, ModItems.MJOLNIR } };
		for (int i = 0; i < 4; i++) {
			ServerPlayer m = thor(helper, hands[i][0], hands[i][1]);
			ItemStack held = m.getMainHandItem();
			held.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
					(attr, mod) -> m.getAttribute(attr).addOrUpdateTransientModifier(mod));
			com.projecthero.mod.power.ThorPassives.reconcile(m);
			ThorKit.reconcileMelee(m);
			double dmg = m.getAttributeValue(Attributes.ATTACK_DAMAGE);
			helper.assertTrue(Math.abs(dmg - want[i]) < 0.01, ThorState.of(m) + " melee " + want[i] + " (got " + dmg + ")");
			helper.assertTrue(dmg >= 21 && dmg <= 28, "inside the Cosmic melee range");
			m.discard();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0177_sunder")
	public void sunderAddsTwentyPercent(GameTestHelper helper) {
		ServerPlayer p = thor(helper, ModItems.MJOLNIR, null);
		Zombie z = dummy(helper, 1.5, 5.5);
		ThorSunder.apply(z);
		helper.assertTrue(ThorSunder.isSundered(z), "Bifrost Hurl's mark is on");
		ThorKit.hit(p, z, 25f, ThorKit.Kind.PHYSICAL);
		helper.assertTrue(Math.abs(500f - z.getHealth() - 30f) < 0.01f, "25 on a Sundered foe lands 30 (got " + (500f - z.getHealth()) + ")");
		Zombie plain = dummy(helper, 4.5, 5.5);
		ThorKit.hit(p, plain, 25f, ThorKit.Kind.PHYSICAL);
		helper.assertTrue(Math.abs(500f - plain.getHealth() - 25f) < 0.01f, "an unmarked foe takes 25");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0177_execute")
	public void executionersDropIsOneAndAHalfUnderThirtyPercentCappedAtThirty(GameTestHelper helper) {
		ServerPlayer p = thor(helper, ModItems.STORMBREAKER, null);
		Zombie healthy = dummy(helper, 2.5, 3.0);
		Zombie weak = dummy(helper, 0.5, 3.0);
		weak.setHealth(100f); // 20%
		helper.assertTrue(ThorStormbreakerKit.executionDamage(healthy) == ThorKit.EXEC_DAMAGE, "28 on a healthy foe");
		helper.assertTrue(ThorStormbreakerKit.executionDamage(weak) == ThorKit.EXEC_MAX, "x1.5 = 42 capped at 30 under 30%");
		ThorStormbreakerKit.executionLanding(p, p.position());
		helper.assertTrue(Math.abs(500f - healthy.getHealth() - 28f) < 0.01f, "the landing hits the healthy foe for 28 (got " + (500f - healthy.getHealth()) + ")");
		helper.assertTrue(Math.abs(100f - weak.getHealth() - 30f) < 0.01f, "and the weakened one for 30 (got " + (100f - weak.getHealth()) + ")");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0177_energy")
	public void twinGodsPayDoubleStormEnergy(GameTestHelper helper) {
		ServerPlayer single = thor(helper, ModItems.MJOLNIR, null);
		press(single, AbilitySlot.SLOT_6, false); // Chain Lightning, 20
		helper.assertTrue(Math.abs(StormEnergy.MAX - StormEnergy.get(single) - ThorKit.CHAIN_COST) < 0.01f, "Mjolnir pays 20");
		ServerPlayer twin = thor(helper, ModItems.MJOLNIR, ModItems.STORMBREAKER);
		press(twin, AbilitySlot.SLOT_6, false); // Crossfire, 20 x 2
		helper.assertTrue(Math.abs(StormEnergy.MAX - StormEnergy.get(twin) - 2 * ThorKit.CROSSFIRE_COST) < 0.01f,
				"Twin Gods pay double (got " + (StormEnergy.MAX - StormEnergy.get(twin)) + ")");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0177_flight")
	public void unarmedCannotFly(GameTestHelper helper) {
		ServerPlayer p = thor(helper, null, null);
		ThorPowers.toggleFlight(p);
		helper.assertFalse(ThorPowers.isFlying(p), "no flight with empty hands");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MJOLNIR));
		ThorPowers.toggleFlight(p);
		helper.assertTrue(ThorPowers.isFlying(p), "Mjolnir in hand flies");
		helper.assertTrue(Math.abs(p.getAbilities().getFlyingSpeed() - ThorState.MJOLNIR.flyingSpeed) < 1e-6, "at Mjolnir's speed");
		helper.assertTrue(ThorState.MJOLNIR.flyingSpeed > ThorState.TWIN.flyingSpeed && ThorState.TWIN.flyingSpeed > ThorState.STORMBREAKER.flyingSpeed,
				"Mjolnir fastest, Twin medium, Stormbreaker slow");
		ThorPowers.toggleFlight(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0177_cooldowns")
	public void unarmedCooldownsAreAQuarterShorter(GameTestHelper helper) {
		ServerPlayer bare = thor(helper, null, null);
		ServerPlayer armed = thor(helper, ModItems.MJOLNIR, null);
		press(bare, AbilitySlot.SLOT_5, false);
		press(armed, AbilitySlot.SLOT_5, false);
		long now = helper.getLevel().getGameTime();
		int b = bare.getAttachedOrCreate(ModAttachments.COOLDOWNS).remainingTicks(ThorAbility.STORM_CALL, now);
		int a = armed.getAttachedOrCreate(ModAttachments.COOLDOWNS).remainingTicks(ThorAbility.STORM_CALL, now);
		helper.assertTrue(a == ThorKit.STORM_CALL_CD, "Mjolnir: the full 60 s (got " + a + ")");
		helper.assertTrue(b == Math.round(ThorKit.STORM_CALL_CD * 0.75f), "Unarmed: 25% shorter (got " + b + ")");
		helper.succeed();
	}

	private static void ultimate(ServerPlayer p) {
		press(p, AbilitySlot.SLOT_4, true);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0177_ult_incarnate", timeoutTicks = 260)
	public void lightningIncarnateFires(GameTestHelper helper) {
		ServerPlayer p = thor(helper, null, null);
		Zombie z = dummy(helper, 1.5, 3.5);
		ultimate(p);
		helper.assertTrue(cooling(p, ThorAbility.ULTIMATE), "the shared ultimate cooldown starts");
		helper.assertTrue(ThorKit.active(p, ThorAbility.INCARNATE_ACTIVE), "he becomes lightning");
		helper.runAfterDelay(ThorKit.INCARNATE_TICKS + 8, () -> {
			helper.assertTrue(ThorVisuals.fx(p).anim() == ThorFx.ANIM_INCARNATE_BURST, "it ends in the explosion");
			helper.assertTrue(500f - z.getHealth() >= ThorKit.INCARNATE_BURST, "the explosion and the aura hit (took " + (500f - z.getHealth()) + ")");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0177_ult_wrath", timeoutTicks = 160)
	public void wrathOfTheStormFires(GameTestHelper helper) {
		ServerPlayer p = thor(helper, ModItems.MJOLNIR, null);
		Zombie z = dummy(helper, 1.5, 7.5);
		ultimate(p);
		helper.assertTrue(ThorVisuals.fx(p).anim() == ThorFx.ANIM_WRATH_STORM, "Wrath of the Storm starts");
		helper.runAfterDelay(ThorUltimates.WRATH_FINAL + 6, () -> {
			helper.assertTrue(500f - z.getHealth() >= ThorKit.WRATH_FINAL, "the rain and the giant bolt hit (took " + (500f - z.getHealth()) + ")");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0177_ult_judgement", timeoutTicks = 120)
	public void bifrostJudgementFires(GameTestHelper helper) {
		ServerPlayer p = thor(helper, ModItems.STORMBREAKER, null);
		Zombie z = dummy(helper, 1.5, 7.5);
		ultimate(p);
		helper.assertTrue(ThorVisuals.fx(p).anim() == ThorFx.ANIM_JUDGEMENT, "Bifrost Judgement starts");
		helper.runAfterDelay(ThorUltimates.JUDGE_BURST + 4, () -> {
			float took = 500f - z.getHealth();
			helper.assertTrue(took >= ThorKit.JUDGEMENT_IMPALE + ThorKit.JUDGEMENT_BURST - 0.01f, "impaled (60) then the Bifrost (45) (took " + took + ")");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_v0177_ult_ragnarok", timeoutTicks = 160)
	public void ragnarokFires(GameTestHelper helper) {
		ServerPlayer p = thor(helper, ModItems.MJOLNIR, ModItems.STORMBREAKER);
		ultimate(p);
		helper.assertTrue(ThorVisuals.fx(p).anim() == ThorFx.ANIM_RAGNAROK, "Ragnarok starts");
		helper.assertTrue(Math.abs(StormEnergy.MAX - StormEnergy.get(p) - 2 * ThorKit.ULT_COST) < 0.01f, "Twin Gods pay double for it");
		Vec3 centre = new Vec3(ThorVisuals.fx(p).x(), ThorVisuals.fx(p).y(), ThorVisuals.fx(p).z());
		Zombie z = EntityType.HUSK.create(helper.getLevel());
		z.moveTo(centre.x, centre.y, centre.z, 0f, 0f);
		z.setNoAi(true);
		z.setNoGravity(true);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		z.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		z.setHealth(500f);
		helper.getLevel().addFreshEntity(z);
		helper.runAfterDelay(ThorUltimates.RAG_IMPACT + 4, () -> {
			helper.assertTrue(500f - z.getHealth() >= ThorKit.RAGNAROK_FINAL - 0.01f, "the hurled axe lands on the centre (took " + (500f - z.getHealth()) + ")");
			helper.assertFalse(ThorKit.ready(p, ThorAbility.ULTIMATE), "on the shared cooldown");
			z.discard();
			helper.succeed();
		});
	}
}
