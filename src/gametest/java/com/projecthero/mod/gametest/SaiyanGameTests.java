package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.SaiyanConfig;
import com.projecthero.mod.saiyan.SaiyanForm;
import com.projecthero.mod.saiyan.data.SaiyanState;
import com.projecthero.mod.saiyan.item.SaiyanItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Saiyan (Wave 1, the core): the grants, Ki (charge, spend), Base and Super Saiyan only (the other forms unreachable,
 * old saves mapped to Super Saiyan), the revert at 0 Ki. Mock players are not ticked by the server, so tests over time drive
 * {@link Saiyan#tick} themselves (one call per game tick, the clock moved by the helper).
 */
public class SaiyanGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	private static ServerPlayer saiyan(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		helper.assertTrue(Saiyan.grant(p), "the grant works");
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<SaiyanState> change) {
		SaiyanState n = Saiyan.state(p).copy();
		change.accept(n);
		p.setAttached(ModAttachments.SAIYAN_STATE, n);
		Saiyan.reconcile(p);
	}

	/** Runs {@code ticks} Saiyan ticks, one per game tick, then {@code then}. */
	private static void ticks(GameTestHelper helper, ServerPlayer p, int ticks, Runnable then) {
		int[] left = { ticks };
		helper.onEachTick(() -> {
			if (left[0] > 0) {
				Saiyan.tick(p);
				left[0]--;
				if (left[0] == 0) {
					then.run();
				}
			}
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void bloodGrantsThePowerAsTheOnePrimary(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		com.projecthero.mod.kryptonian.Kryptonian.grant(p);
		ItemStack blood = new ItemStack(SaiyanItems.SAIYAN_BLOOD);
		p.setItemInHand(InteractionHand.MAIN_HAND, blood);
		ItemStack left = blood.finishUsingItem(p.level(), p);
		helper.assertTrue(Saiyan.hasPower(p), "drinking Saiyan Blood makes a Saiyan");
		helper.assertTrue(left.is(Items.GLASS_BOTTLE), "the bottle comes back");
		helper.assertTrue(!com.projecthero.mod.kryptonian.Kryptonian.hasPower(p), "it replaced the Kryptonian (one Primary)");
		helper.assertTrue(HeroTiers.holdsHero(p, Saiyan.KEY), "HeroTiers knows the key");
		helper.assertTrue(Saiyan.ki(p) == SaiyanConfig.KI_MAX, "a full bar to start");
		helper.assertTrue(Saiyan.form(p) == SaiyanForm.BASE, "in Base");
		helper.assertTrue(!Saiyan.grant(p), "a second grant is refused");
		helper.assertTrue(com.projecthero.mod.hero.PowerGrants.HERO_TIER_KEYS.contains("saiyan")
				&& !com.projecthero.mod.hero.PowerGrants.NOT_FROM_SERUMS.contains("saiyan"), "the random serums can roll it");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void chargingFillsKiAndSpendingTakesIt(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Saiyan.setKi(p, 10f);
		helper.assertTrue(Saiyan.spendKi(p, 6f), "6 of 10 can be spent");
		helper.assertTrue(Math.abs(Saiyan.ki(p) - 4f) < 0.01f, "4 left: " + Saiyan.ki(p));
		helper.assertTrue(!Saiyan.spendKi(p, 6f), "6 of 4 is refused");
		helper.assertTrue(Math.abs(Saiyan.ki(p) - 4f) < 0.01f, "and nothing was taken");
		Saiyan.setCharging(p, true);
		helper.assertTrue(Saiyan.charging(p), "Q held: charging");
		double slowed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		ticks(helper, p, 20, () -> {
			float expected = 4f + SaiyanConfig.KI_CHARGE_PER_TICK * 20;
			helper.assertTrue(Math.abs(Saiyan.ki(p) - expected) < 0.2f, "a second of charging adds 12 Ki: " + Saiyan.ki(p));
			Saiyan.setCharging(p, false);
			helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > slowed, "letting go lifts the charge slow");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void superSaiyanCostsKiButDoesNotDrainIt(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		helper.assertTrue(Saiyan.transform(p, SaiyanForm.SSJ), "into Super Saiyan");
		helper.assertTrue(Math.abs(Saiyan.ki(p) - (SaiyanConfig.KI_MAX - SaiyanForm.SSJ.cost)) < 0.01f, "the form's cost was paid");
		edit(p, s -> s.ki = 50f);
		ticks(helper, p, 40, () -> {
			helper.assertTrue(Saiyan.ki(p) >= 50f - 0.01f, "Super Saiyan doesn't drain Ki: " + Saiyan.ki(p));
			helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) >= 1.0 + SaiyanForm.SSJ.attackBonus - 0.01, "and hits harder");
			helper.succeed();
		});
	}

	/** v0.17.3 (user): "only super saiyan for now" -- the later forms can't be reached at all. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void onlyBaseAndSuperSaiyanAreInPlay(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		helper.assertTrue(java.util.Arrays.equals(SaiyanForm.PLAYABLE, new SaiyanForm[] { SaiyanForm.BASE, SaiyanForm.SSJ }), "Base and SSJ");
		helper.assertTrue(Saiyan.unlocked(p, SaiyanForm.SSJ), "Super Saiyan is open from the start");
		for (SaiyanForm f : SaiyanForm.values()) {
			if (f.playable()) {
				continue;
			}
			helper.assertTrue(!Saiyan.unlocked(p, f), f + " is not available");
			helper.assertTrue(Saiyan.refusal(p, f) != null, f + " is refused");
			helper.assertTrue(!Saiyan.transform(p, f), "and can't be entered: " + f);
		}
		helper.assertTrue(Saiyan.form(p) == SaiyanForm.BASE, "still Base");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anOldSaveInARemovedFormLoadsAsSuperSaiyan(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		edit(p, s -> s.form = SaiyanForm.BLUE.ordinal());
		helper.assertTrue(Saiyan.form(p) == SaiyanForm.SSJ, "a save left in Blue reads as Super Saiyan");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) < 1.0 + SaiyanForm.BLUE.attackBonus - 0.01, "with Super Saiyan's stats");
		ticks(helper, p, 1, () -> {
			helper.assertTrue(Saiyan.state(p).form == SaiyanForm.SSJ.ordinal(), "and is stored as Super Saiyan after a tick");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void zeroKiRevertsAndExhausts(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		// Super Saiyan doesn't drain Ki by itself any more: flying does
		edit(p, s -> {
			s.form = SaiyanForm.SSJ.ordinal();
			s.flying = true;
			s.ki = SaiyanConfig.KI_FLIGHT_PER_TICK * 2.5f;
		});
		double before = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		ticks(helper, p, 4, () -> {
			helper.assertTrue(Saiyan.form(p) == SaiyanForm.BASE, "out of Ki: back to Base");
			helper.assertTrue(Saiyan.exhausted(p), "exhausted");
			helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < before, "and slowed");
			Saiyan.setKi(p, SaiyanConfig.KI_MAX);
			helper.assertTrue(!Saiyan.transform(p, SaiyanForm.SSJ), "no transforming while exhausted");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void superSaiyanCutsDamageAndPowerDownWorks(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		helper.assertTrue(Saiyan.transform(p, SaiyanForm.SSJ), "into Super Saiyan");
		try { // a fresh mock player is spawn-protected
			java.lang.reflect.Field fld = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			fld.setAccessible(true);
			fld.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		p.invulnerableTime = 0;
		p.setHealth(p.getMaxHealth());
		float before = p.getHealth();
		p.hurt(p.damageSources().generic(), 10f);
		float taken = before - p.getHealth();
		helper.assertTrue(taken > 0f && taken < 10f * (1f - SaiyanForm.SSJ.damageReduction) + 0.5f, "Super Saiyan takes 25% less: " + taken);
		helper.assertTrue(Saiyan.powerDown(p), "Shift+V powers down");
		helper.assertTrue(Saiyan.form(p) == SaiyanForm.BASE, "Base");
		helper.assertTrue(Saiyan.transformingTicks(p) == 0, "the burst clock restarted");
		Saiyan.revoke(p);
		helper.assertTrue(!Saiyan.hasPower(p), "revoked");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) <= 1.01, "the form's attack bonus is gone");
		helper.succeed();
	}


	// ---------------------------------------------------------------- v0.17.3 (user): V charges (tap or hold), X / Shift+X

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aTapOfVChargesUntilFull(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Saiyan.setKi(p, 90f);
		com.projecthero.mod.saiyan.SaiyanAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, true);
		com.projecthero.mod.saiyan.SaiyanAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_5, false);
		helper.assertTrue(Saiyan.charging(p), "a tap keeps charging with V up");
		ticks(helper, p, 30, () -> {
			helper.assertTrue(Saiyan.ki(p) >= SaiyanConfig.KI_MAX - 0.01f, "full: " + Saiyan.ki(p));
			helper.assertTrue(!Saiyan.charging(p), "a tapped charge stops at full Ki");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSecondTapOfVStopsTheCharge(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Saiyan.setKi(p, 10f);
		var slot = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.saiyan.SaiyanAbilityManager.handle(p, slot, true);
		com.projecthero.mod.saiyan.SaiyanAbilityManager.handle(p, slot, false);
		ticks(helper, p, 5, () -> {
			helper.assertTrue(Saiyan.charging(p), "still charging");
			com.projecthero.mod.saiyan.SaiyanAbilityManager.handle(p, slot, true);
			helper.assertTrue(!Saiyan.charging(p), "the second tap stops it");
			com.projecthero.mod.saiyan.SaiyanAbilityManager.handle(p, slot, false);
			helper.assertTrue(!Saiyan.charging(p), "and letting go doesn't restart it");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void holdingVChargesUntilLetGo(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Saiyan.setKi(p, 10f);
		var slot = com.projecthero.mod.hero.AbilitySlot.SLOT_5;
		com.projecthero.mod.saiyan.SaiyanAbilityManager.handle(p, slot, true);
		ticks(helper, p, 15, () -> {
			helper.assertTrue(Saiyan.charging(p), "held: charging");
			com.projecthero.mod.saiyan.SaiyanAbilityManager.handle(p, slot, false);
			helper.assertTrue(!Saiyan.charging(p), "a hold stops on release");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void xIsKiDashAndShiftXIsInstantStep(GameTestHelper helper) {
		String[] x = com.projecthero.mod.saiyan.SaiyanAbilityManager.idsOf(com.projecthero.mod.hero.AbilitySlot.SLOT_3);
		helper.assertTrue(x.length == 2 && x[0].equals("ki_dash") && x[1].equals("instant_step"), "X: Ki Dash / Shift+X: Instant Step");
		String[] v = com.projecthero.mod.saiyan.SaiyanAbilityManager.idsOf(com.projecthero.mod.hero.AbilitySlot.SLOT_5);
		helper.assertTrue(v.length == 2 && v[0].equals("charge") && v[1].equals("power_down"), "V: charge / Shift+V: power down");
		ServerPlayer p = saiyan(helper);
		Saiyan.setKi(p, 50f);
		Vec3 before = p.position();
		com.projecthero.mod.saiyan.SaiyanAbilityManager.handle(p, com.projecthero.mod.hero.AbilitySlot.SLOT_3, true);
		helper.assertTrue(Saiyan.ki(p) < 50f, "the dash spent Ki");
		helper.succeed();
	}
}
