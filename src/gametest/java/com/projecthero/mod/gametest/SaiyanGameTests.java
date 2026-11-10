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
 * v0.17.3 Saiyan (Wave 1, the core): the grants, Ki (charge, spend, drain), form gating and mastery, Blue Overdrive's burn,
 * Instinct's low-health gate, the revert at 0 Ki. Mock players are not ticked by the server, so tests over time drive
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
	public void ssj2DrainsKiAndBaseDoesNot(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Saiyan.unlockAll(p);
		helper.assertTrue(Saiyan.transform(p, SaiyanForm.SSJ), "into Super Saiyan");
		helper.assertTrue(Math.abs(Saiyan.ki(p) - (SaiyanConfig.KI_MAX - SaiyanForm.SSJ.cost)) < 0.01f, "the form's cost was paid");
		edit(p, s -> {
			s.form = SaiyanForm.SSJ2.ordinal();
			s.ki = 50f;
		});
		ticks(helper, p, 40, () -> {
			float expected = 50f - SaiyanForm.SSJ2.kiDrainPerTick() * 40;
			helper.assertTrue(Math.abs(Saiyan.ki(p) - expected) < 0.2f, "Super Saiyan 2 drains 1 Ki a second: " + Saiyan.ki(p));
			helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) > 1.0 + SaiyanForm.SSJ.attackBonus, "and hits harder than SSJ");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void formsAreGatedByMastery(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		helper.assertTrue(Saiyan.unlocked(p, SaiyanForm.SSJ), "Super Saiyan is open from the start");
		helper.assertTrue(!Saiyan.unlocked(p, SaiyanForm.SSJ2), "Super Saiyan 2 is locked");
		helper.assertTrue(!Saiyan.transform(p, SaiyanForm.SSJ2), "and refused");
		helper.assertTrue(Saiyan.form(p) == SaiyanForm.BASE, "still Base");
		helper.assertTrue(Saiyan.transform(p, SaiyanForm.SSJ), "SSJ works");
		// almost mastered: one more tick in SSJ unlocks SSJ2
		edit(p, s -> s.mastery.set(SaiyanForm.SSJ.ordinal(), SaiyanConfig.MASTERY_SSJ2 - 1));
		ticks(helper, p, 1, () -> {
			helper.assertTrue(Saiyan.unlocked(p, SaiyanForm.SSJ2), "time in SSJ unlocked SSJ2");
			helper.assertTrue(!Saiyan.unlocked(p, SaiyanForm.SSJ3), "but not SSJ3");
			helper.assertTrue(Saiyan.transform(p, SaiyanForm.SSJ2) || Saiyan.state(p).lastTransformAt == p.level().getGameTime(),
					"SSJ2 can be entered (the transform cooldown aside)");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void overdriveOnlyFromBlueAndItBurns(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Saiyan.unlockAll(p);
		helper.assertTrue(Saiyan.refusal(p, SaiyanForm.BLUE_OVERDRIVE) != null, "Overdrive is refused outside Blue");
		edit(p, s -> {
			s.form = SaiyanForm.BLUE.ordinal();
			s.lastTransformAt = p.level().getGameTime() - 100;
		});
		helper.assertTrue(Saiyan.transform(p, SaiyanForm.BLUE_OVERDRIVE), "from Blue it works");
		p.setHealth(p.getMaxHealth());
		float before = p.getHealth();
		ticks(helper, p, SaiyanConfig.OVERDRIVE_SELF_DAMAGE_INTERVAL + 1, () -> {
			helper.assertTrue(p.getHealth() < before, "Overdrive burns health: " + before + " -> " + p.getHealth());
			p.setHealth(SaiyanConfig.OVERDRIVE_FLOOR + 0.5f);
			Saiyan.overdriveBurn(p);
			helper.assertTrue(Saiyan.form(p) == SaiyanForm.BLUE, "near the floor it drops back to Blue instead of killing");
			helper.assertTrue(p.isAlive(), "alive");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void instinctNeedsLowHealth(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Saiyan.unlockAll(p);
		p.setHealth(p.getMaxHealth());
		helper.assertTrue(!Saiyan.transform(p, SaiyanForm.INSTINCT), "refused at full health");
		helper.assertTrue(Saiyan.form(p) == SaiyanForm.BASE, "still Base");
		p.setHealth(p.getMaxHealth() * SaiyanConfig.INSTINCT_HEALTH - 0.5f);
		Saiyan.setKi(p, SaiyanConfig.INSTINCT_KI - 5f);
		helper.assertTrue(!Saiyan.transform(p, SaiyanForm.INSTINCT), "refused without 30 Ki");
		Saiyan.setKi(p, SaiyanConfig.KI_MAX);
		helper.assertTrue(Saiyan.transform(p, SaiyanForm.INSTINCT), "low health and Ki: Instinct awakens");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void zeroKiRevertsAndExhausts(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Saiyan.unlockAll(p);
		edit(p, s -> {
			s.form = SaiyanForm.SSJ3.ordinal();
			s.ki = SaiyanForm.SSJ3.kiDrainPerTick() * 2.5f;
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
	public void formsCutDamageAndPowerDownWorks(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Saiyan.unlockAll(p);
		edit(p, s -> s.form = SaiyanForm.BLUE.ordinal());
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
		helper.assertTrue(taken > 0f && taken < 10f * (1f - SaiyanForm.BLUE.damageReduction) + 0.5f, "Blue takes 60% less: " + taken);
		helper.assertTrue(Saiyan.powerDown(p), "Shift+V powers down");
		helper.assertTrue(Saiyan.form(p) == SaiyanForm.BASE, "Base");
		helper.assertTrue(Saiyan.transformingTicks(p) == 0, "the burst clock restarted");
		Saiyan.revoke(p);
		helper.assertTrue(!Saiyan.hasPower(p), "revoked");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) <= 1.01, "the form's attack bonus is gone");
		helper.succeed();
	}
}
