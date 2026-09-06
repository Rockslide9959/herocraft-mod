package com.projecthero.mod.gametest;

import com.projecthero.mod.firearm.FirearmData;
import com.projecthero.mod.firearm.FirearmHooks;
import com.projecthero.mod.firearm.FirearmReload;
import com.projecthero.mod.firearm.FirearmShooting;
import com.projecthero.mod.firearm.FirearmStack;
import com.projecthero.mod.firearm.Firearms;
import com.projecthero.mod.firearm.HeadshotResolver;
import com.projecthero.mod.firearm.item.FirearmItems;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.punisher.PunisherConfig;
import com.projecthero.mod.punisher.ability.PunisherAdrenaline;
import com.projecthero.mod.punisher.ability.PunisherC4;
import com.projecthero.mod.punisher.ability.PunisherRoll;
import com.projecthero.mod.punisher.ability.PunisherSuppressive;
import com.projecthero.mod.punisher.data.PunisherState;
import com.projecthero.mod.punisher.entity.C4ChargeEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side coverage for the Punisher: the firearm engine's magazine / reload / ammo rules, the
 * personal-reserve seam, headshot resolution, and the power's ability gates + lifecycle cleanup.
 * Anything that needs {@code player.hurt()} on a mock player, live movement, or worldgen is in the
 * manual test plan (mock players can't be reliably damaged -- see the project notes).
 */
public class PunisherGameTests implements FabricGameTest {

	private static ServerPlayer punisher(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Punisher.grant(p);
		return p;
	}

	private static ServerPlayer plain(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		return p;
	}

	private static ItemStack pistol() {
		return new ItemStack(FirearmItems.PUNISHER_PISTOL);
	}

	private static void readyToFire(ServerPlayer p, ItemStack gun) {
		FirearmStack.setLastFired(gun, p.level().getGameTime() - 1000);
	}

	// ---------------- firearm engine ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void pistolMagazineDrainsAndReloads(GameTestHelper helper) {
		ServerPlayer p = punisher(helper);
		ItemStack gun = pistol();
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
		FirearmData d = Firearms.get(Firearms.PISTOL);

		helper.assertTrue(FirearmStack.magazine(gun, d) == 12, "a fresh pistol is loaded");
		for (int i = 0; i < 12; i++) {
			readyToFire(p, gun);
			FirearmShooting.fire(p, gun, d);
		}
		helper.assertTrue(FirearmStack.magazine(gun, d) == 0, "12 shots empty the magazine, got " + FirearmStack.magazine(gun, d));
		readyToFire(p, gun);
		helper.assertTrue(FirearmShooting.fire(p, gun, d) == FirearmShooting.Result.EMPTY, "an empty magazine can't fire");

		FirearmReload.start(p, gun, d);
		// finish the reload immediately
		FirearmStack.setReloadEnd(gun, p.level().getGameTime());
		FirearmReload.tick(p, gun, d);
		helper.assertTrue(FirearmStack.magazine(gun, d) == 12, "reload tops the magazine off, got " + FirearmStack.magazine(gun, d));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void punisherReloadConsumesNoAmmoItem(GameTestHelper helper) {
		ServerPlayer p = punisher(helper);
		ItemStack gun = pistol();
		FirearmData d = Firearms.get(Firearms.PISTOL);
		FirearmStack.setMagazine(gun, 0);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);

		FirearmReload.start(p, gun, d);
		FirearmStack.setReloadEnd(gun, p.level().getGameTime());
		FirearmReload.tick(p, gun, d);

		helper.assertTrue(FirearmStack.magazine(gun, d) == 12, "a Punisher reloads to full with no ammo carried");
		helper.assertTrue(p.getInventory().countItem(FirearmItems.PISTOL_AMMO) == 0, "no ammo item existed to consume");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void normalPlayerReloadConsumesCarriedAmmo(GameTestHelper helper) {
		ServerPlayer p = plain(helper);
		ItemStack gun = pistol();
		FirearmData d = Firearms.get(Firearms.PISTOL);
		FirearmStack.setMagazine(gun, 0);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
		p.getInventory().add(new ItemStack(FirearmItems.PISTOL_AMMO, 5));

		FirearmReload.start(p, gun, d);
		FirearmStack.setReloadEnd(gun, p.level().getGameTime());
		FirearmReload.tick(p, gun, d);

		helper.assertTrue(FirearmStack.magazine(gun, d) == 5, "a normal player only loads what they carry, got " + FirearmStack.magazine(gun, d));
		helper.assertTrue(p.getInventory().countItem(FirearmItems.PISTOL_AMMO) == 0, "the 5 rounds carried were consumed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void personalReserveHookOnlyForPunisher(GameTestHelper helper) {
		ServerPlayer punisher = punisher(helper);
		ServerPlayer plain = plain(helper);
		helper.assertTrue(FirearmHooks.get().usesPersonalReserve(punisher), "a Punisher draws on a personal reserve");
		helper.assertFalse(FirearmHooks.get().usesPersonalReserve(plain), "a normal player does not");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void punisherReserveDepletesOnReloadAndRefills(GameTestHelper helper) {
		ServerPlayer p = punisher(helper);
		ItemStack gun = pistol();
		FirearmData d = Firearms.get(Firearms.PISTOL);
		int cap = com.projecthero.mod.punisher.PunisherAmmoReserve.capacity(com.projecthero.mod.firearm.AmmoKind.PISTOL);
		helper.assertTrue(cap == d.magazineSize * 3, "reserve capacity is three magazines, got " + cap);
		helper.assertTrue(com.projecthero.mod.punisher.PunisherAmmoReserve.count(p, com.projecthero.mod.firearm.AmmoKind.PISTOL) == cap,
				"a fresh Punisher reserve starts full");

		FirearmStack.setMagazine(gun, 0);
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
		FirearmReload.start(p, gun, d);
		FirearmStack.setReloadEnd(gun, p.level().getGameTime());
		FirearmReload.tick(p, gun, d);

		int afterReload = com.projecthero.mod.punisher.PunisherAmmoReserve.count(p, com.projecthero.mod.firearm.AmmoKind.PISTOL);
		helper.assertTrue(afterReload == cap - d.magazineSize,
				"the reload drew a full magazine from the reserve, got " + afterReload);

		// regen fires on tickCount % 14 == 0, adding ~1% of capacity each time -- run enough steps to
		// cross a whole round back.
		for (int i = 0; i < 84; i++) {
			com.projecthero.mod.punisher.PunisherAmmoReserve.tickRegen(p);
			p.tickCount++;
		}
		helper.assertTrue(com.projecthero.mod.punisher.PunisherAmmoReserve.count(p, com.projecthero.mod.firearm.AmmoKind.PISTOL) > afterReload,
				"the reserve regenerates over time");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void headshotResolverScalesWithTarget(GameTestHelper helper) {
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 base = new Vec3(0, 0, 0);
		zombie.moveTo(base.x, base.y, base.z, 0f, 0f);
		double h = zombie.getBbHeight();
		helper.assertTrue(HeadshotResolver.isHeadshot(zombie, base.add(0, h - 0.1, 0)), "a hit near the crown is a headshot");
		helper.assertFalse(HeadshotResolver.isHeadshot(zombie, base.add(0, h * 0.25, 0)), "a hit at the shins is not");
		zombie.discard();
		helper.succeed();
	}

	// ---------------- abilities ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void adrenalineAppliesBuffsThenClears(GameTestHelper helper) {
		ServerPlayer p = punisher(helper);
		double before = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		PunisherAdrenaline.activate(p);
		helper.assertTrue(Punisher.adrenalineActive(p), "Adrenaline should be active after activation");
		helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED),
				"Adrenaline grants Speed II");
		helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE),
				"Adrenaline grants Resistance II");
		helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED),
				"Adrenaline grants Haste II");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > before, "Speed II speeds the player up");

		Punisher.clearTransient(p);
		helper.assertFalse(Punisher.adrenalineActive(p), "clearTransient ends the Adrenaline window");
		helper.assertTrue(Punisher.state(p).adrenalineCrashAt == 0L, "clearTransient clears the pending crash");
		helper.assertTrue(Punisher.hasPower(p), "clearTransient never removes the power");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suppressiveFireNeedsTheRifle(GameTestHelper helper) {
		ServerPlayer p = punisher(helper);
		PunisherSuppressive.activate(p);
		helper.assertTrue(Punisher.state(p).suppressiveUntil == 0L, "Suppressive Fire is refused without the rifle unlocked");

		Punisher.unlockWeapon(p, Firearms.RIFLE);
		PunisherSuppressive.activate(p);
		helper.assertTrue(Punisher.state(p).suppressiveUntil > p.level().getGameTime(), "with the rifle unlocked it activates");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void tacticalRollSetsCooldown(GameTestHelper helper) {
		ServerPlayer p = punisher(helper);
		p.setDeltaMovement(0.2, 0, 0);
		p.setOnGround(true);
		PunisherRoll.roll(p);
		helper.assertFalse(Punisher.abilityReady(p, PunisherRoll.ABILITY), "the roll goes on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void c4CapsAtThreeAndSeparatesOwners(GameTestHelper helper) {
		ServerPlayer a = punisher(helper);
		ServerPlayer b = punisher(helper);
		net.minecraft.core.BlockPos ground = helper.absolutePos(new net.minecraft.core.BlockPos(1, 0, 1));
		a.moveTo(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, 0f, 89f); // look almost straight down
		// place four -- only three should exist
		for (int i = 0; i < 4; i++) {
			Punisher.triggerCooldown(a, PunisherC4.ABILITY, 0);
			PunisherC4.place(a);
		}
		helper.assertTrue(PunisherC4.activeCount(a.getUUID()) <= PunisherConfig.C4_MAX_ACTIVE,
				"never more than 3 charges, got " + PunisherC4.activeCount(a.getUUID()));

		int aCount = PunisherC4.activeCount(a.getUUID());
		PunisherC4.detonateAll(b); // B has none
		helper.assertTrue(PunisherC4.activeCount(a.getUUID()) == aCount, "one Punisher cannot detonate another's charges");

		PunisherC4.detonateAll(a);
		helper.assertTrue(PunisherC4.activeCount(a.getUUID()) == 0, "detonating clears the owner's charge list");
		helper.succeed();
	}

	// ---------------- power lifecycle ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void powerPersistsAndIsHeroTierExclusive(GameTestHelper helper) {
		ServerPlayer p = punisher(helper);
		helper.assertTrue(com.projecthero.mod.hero.HeroTiers.hasHeroTier(p), "the Punisher counts as a Hero-Tier power");
		com.projecthero.mod.hero.HeroTiers.wipeAll(p);
		helper.assertFalse(Punisher.hasPower(p), "wipeAll strips the Punisher like every other Hero-Tier power");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void trainingCompletionGrantsThePower(GameTestHelper helper) {
		ServerPlayer p = plain(helper);
		PunisherState s = Punisher.state(p).copy();
		s.trainingActive = true;
		s.killCount = PunisherConfig.TRAIN_KILLS;
		s.rangedKillCount = PunisherConfig.TRAIN_RANGED_KILLS;
		s.headshotCount = PunisherConfig.TRAIN_HEADSHOTS;
		s.craftedFirearm = true;
		s.defeatedCaptain = true;
		Punisher.save(p, s);
		helper.assertTrue(s.trainingDone(), "all objectives met => training done");
		com.projecthero.mod.punisher.VigilanteTraining.onHeadshot(p); // any progress call re-checks completion
		helper.assertTrue(Punisher.hasPower(p), "completing Vigilante Training grants the Punisher");
		helper.succeed();
	}
}
