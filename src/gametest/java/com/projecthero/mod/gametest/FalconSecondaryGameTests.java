package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.falcon.Falcon;
import com.projecthero.mod.falcon.FalconAbilities;
import com.projecthero.mod.falcon.FalconAbilityManager;
import com.projecthero.mod.falcon.FalconConfig;
import com.projecthero.mod.falcon.data.FalconState;
import com.projecthero.mod.falcon.item.FalconItems;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerClass;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.kryptonian.KryptonianAbilityManager;
import com.projecthero.mod.powerarmour.PowerArmourAttachments;
import com.projecthero.mod.powerarmour.PowerArmourState;

import com.mojang.serialization.JsonOps;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 (user: "make falcons powers a secondary power, while wearing the armour you get his powers and moveset but when you
 * take it off you go back to what you had before"): the Falcon gear is a Secondary power on Power Armour's terms. Wearing it
 * routes the ability keys to the Falcon kit ahead of the Primary, taking it off hands them back, it never claims or replaces a
 * Primary, a player with no Primary can still use it, Power Armour comes first, and old Primary-days saves migrate. Mock players
 * are not ticked by the server, so the tests drive {@link Falcon#tick} themselves.
 */
public class FalconSecondaryGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	private static void tick(ServerPlayer p) {
		Falcon.forgetTick(p);
		Falcon.tick(p);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wearingTheSuitRoutesKeysToFalconAndTakingItOffGivesThemBack(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Kryptonian.grant(p);
		helper.assertTrue(KryptonianAbilityManager.hasContext(p) && !FalconAbilityManager.hasContext(p), "unsuited: the Kryptonian has the keys");
		FalconItems.equipFullSet(p);
		tick(p);
		helper.assertTrue(Falcon.hasPower(p) && Falcon.ownsKeys(p) && FalconAbilityManager.hasContext(p), "suited: the Falcon kit owns the keys");
		helper.assertTrue(Kryptonian.hasPower(p), "and the Kryptonian is still held underneath (passives keep running)");
		// G through the real router: the Wing Slash fires (its cooldown starts), ahead of the Primary
		AbilityRouter.handleInput(p, 2, true);
		AbilityRouter.handleInput(p, 2, false);
		helper.assertTrue(Falcon.cooldownRemaining(p, FalconAbilities.SLASH) == FalconConfig.SLASH_COOLDOWN,
				"G went to the Falcon kit (Wing Slash on cooldown: " + Falcon.cooldownRemaining(p, FalconAbilities.SLASH) + ")");
		// H / N presses are swallowed while suited (nothing to assert beyond "no throw"; the router returns early)
		AbilityRouter.handleInput(p, 7, true);
		AbilityRouter.handleInput(p, 7, false);
		// a piece off: the kit goes, the Kryptonian's keys are back
		p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		tick(p);
		helper.assertTrue(!Falcon.hasPower(p) && !FalconAbilityManager.hasContext(p), "wing pack off: no Falcon kit");
		helper.assertTrue(Kryptonian.hasPower(p) && KryptonianAbilityManager.hasContext(p), "the Kryptonian has the keys again");
		int before = Falcon.cooldownRemaining(p, FalconAbilities.GUNS);
		AbilityRouter.handleInput(p, 1, true);
		AbilityRouter.handleInput(p, 1, false);
		helper.assertTrue(Falcon.cooldownRemaining(p, FalconAbilities.GUNS) == before && Falcon.ammo(p) >= FalconConfig.AMMO_MAX - 0.01f,
				"R no longer reaches the Falcon kit");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theFalconNeverClaimsOrReplacesAPrimary(GameTestHelper helper) {
		helper.assertTrue(Falcon.POWER_CLASS == PowerClass.SECONDARY, "the Falcon is a Secondary power");
		helper.assertFalse(HeroTiers.HERO_KEYS.contains(Falcon.KEY), "not a Primary key");
		ServerPlayer p = mock(helper);
		Kryptonian.grant(p);
		FalconItems.equipFullSet(p);
		tick(p);
		helper.assertTrue(Falcon.hasPower(p) && HeroTiers.heroCount(p) == 1 && HeroTiers.holdsHero(p, "kryptonian"),
				"suited up: still exactly one Primary, the Kryptonian");
		String order = p.getAttachedOrElse(ModAttachments.PRIMARY_ORDER, "");
		helper.assertFalse(order.contains(Falcon.KEY), "the Primary-slot order never lists the Falcon: " + order);
		// another Primary arrives while suited: the one-Primary rule swaps the Primary, the gear stays
		com.projecthero.mod.cyborg.Cyborg.grant(p);
		tick(p);
		helper.assertTrue(com.projecthero.mod.cyborg.Cyborg.hasPower(p) && !Kryptonian.hasPower(p), "Cyborg replaced the Kryptonian (one Primary)");
		helper.assertTrue(Falcon.hasPower(p) && FalconAbilityManager.hasContext(p), "the Falcon gear was not touched");
		// the gear off and on again: the Primary is untouched both ways
		p.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		tick(p);
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(FalconItems.BOOTS));
		tick(p);
		helper.assertTrue(Falcon.hasPower(p) && com.projecthero.mod.cyborg.Cyborg.hasPower(p) && HeroTiers.heroCount(p) == 1,
				"off and on: the kit is back and Cyborg is still the one Primary");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void noPrimaryNeededAndTheGrantAddsOnTop(GameTestHelper helper) {
		ServerPlayer bare = mock(helper);
		FalconItems.equipFullSet(bare);
		tick(bare);
		helper.assertTrue(Falcon.hasPower(bare) && FalconAbilityManager.hasContext(bare) && HeroTiers.heroCount(bare) == 0,
				"no Primary: the suit still gives the whole kit");
		ServerPlayer p = mock(helper);
		Kryptonian.grant(p);
		PowerGrants.Result r = PowerGrants.grantHero(p, Falcon.KEY);
		helper.assertTrue(r.ok() && Falcon.hasPower(p) && Kryptonian.hasPower(p), "the grant puts the gear on over the Kryptonian: " + r.message().getString());
		helper.assertTrue(PowerGrants.NOT_FROM_SERUMS.contains(Falcon.KEY) && !PowerGrants.missingHeroTiers(p).contains(Falcon.KEY),
				"no serum rolls the Falcon (it is a suit you craft)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void powerArmourComesFirstAndTheKitResumesAfter(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		FalconItems.equipFullSet(p);
		tick(p);
		Falcon.spendThrust(p, 30f);
		float thrust = Falcon.thrust(p);
		// stepping into a frame (any phase of Power Armour owns the keys): the kit stands down
		PowerArmourState pa = new PowerArmourState();
		pa.phase = PowerArmourState.SEALED;
		p.setAttached(PowerArmourAttachments.STATE, pa);
		tick(p);
		helper.assertTrue(!Falcon.hasPower(p) && !FalconAbilityManager.hasContext(p) && !Falcon.ownsKeys(p), "in Power Armour: the Falcon kit rests");
		helper.assertFalse(Falcon.state(p).blocked, "resting is not a revoke");
		// out again: the kit comes back by itself with the tanks as they were
		p.removeAttached(PowerArmourAttachments.STATE);
		tick(p);
		helper.assertTrue(Falcon.hasPower(p) && FalconAbilityManager.hasContext(p), "out of the armour: the Falcon kit is back");
		helper.assertTrue(Math.abs(Falcon.thrust(p) - thrust) < 0.01f, "Thrust kept across the rest (" + thrust + " -> " + Falcon.thrust(p) + ")");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void oldPrimarySavesMigrate(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Kryptonian.grant(p);
		p.setAttached(ModAttachments.PRIMARY_ORDER, "falcon,kryptonian");
		helper.assertTrue(Falcon.migrateFromPrimary(p), "a Primary-days order is migrated");
		helper.assertTrue("kryptonian".equals(p.getAttachedOrElse(ModAttachments.PRIMARY_ORDER, "")), "only the Falcon entry goes");
		helper.assertFalse(Falcon.migrateFromPrimary(p), "and only once");
		helper.assertTrue(Kryptonian.hasPower(p), "the Primary is untouched");
		// the Primary-days "blocked" (replaced by another power) flag is dropped on load; the new "revoked" one is kept
		com.google.gson.JsonObject old = new com.google.gson.JsonObject();
		old.addProperty("blocked", true);
		FalconState legacy = FalconState.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
		helper.assertFalse(legacy.blocked, "an old replaced-by-a-Primary block is dropped, so the kit comes back on top");
		com.google.gson.JsonObject now = new com.google.gson.JsonObject();
		now.addProperty("revoked", true);
		helper.assertTrue(FalconState.CODEC.parse(JsonOps.INSTANCE, now).getOrThrow().blocked, "an operator revoke still sticks");
		// a geared old save: the kit keeps working (the power always followed the gear)
		FalconItems.equipFullSet(p);
		p.setAttached(ModAttachments.FALCON_STATE, legacy);
		tick(p);
		helper.assertTrue(Falcon.hasPower(p) && Kryptonian.hasPower(p), "geared old save: the kit, on top of the Primary");
		helper.succeed();
	}
}
