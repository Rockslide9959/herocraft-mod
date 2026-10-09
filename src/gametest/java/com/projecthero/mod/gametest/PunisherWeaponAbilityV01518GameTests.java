package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.firearm.FirearmData;
import com.projecthero.mod.firearm.FirearmHooks;
import com.projecthero.mod.firearm.FirearmReload;
import com.projecthero.mod.firearm.FirearmShooting;
import com.projecthero.mod.firearm.FirearmStack;
import com.projecthero.mod.firearm.Firearms;
import com.projecthero.mod.firearm.item.FirearmItems;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.punisher.ability.PunisherWeaponAbilities;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.18: the Punisher's Weapon Ability (V) -- one move per gun plus a Shift move, every shot through the real
 * firing path. Targets are AI-less villagers with 1000 health (a player's gunfire on a villager is plain, unscaled
 * attack damage; not an Enemy, so No Mercy never applies), the shooter a survival mock player with the power.
 */
public class PunisherWeaponAbilityV01518GameTests implements FabricGameTest {
	private static final String BATCH = "punisher_weapon_v01518";

	private static ServerPlayer punisher(GameTestHelper helper, ItemStack gun) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Punisher.grant(p);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.getInventory().selected = 0;
		p.setItemInHand(InteractionHand.MAIN_HAND, gun);
		return p;
	}

	private static Villager dummy(GameTestHelper helper, double relZ, double armor) {
		Villager v = helper.spawn(EntityType.VILLAGER, new Vec3(1.5, 1.0, relZ));
		v.setNoAi(true);
		v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		v.setHealth(1000f);
		v.getAttribute(Attributes.ARMOR).setBaseValue(armor);
		return v;
	}

	/** Point the player's look at {@code target}'s body (well below the head box). */
	private static void aimAtBody(ServerPlayer p, Villager target) {
		Vec3 to = target.position().add(0.0, 0.8, 0.0).subtract(p.getEyePosition());
		double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
		float yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(to.y, horiz));
		p.moveTo(p.getX(), p.getY(), p.getZ(), yaw, pitch);
		p.setYHeadRot(yaw);
	}

	private static void readyToFire(ServerPlayer p, ItemStack gun) {
		FirearmStack.setLastFired(gun, p.level().getGameTime() - 1000);
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 0.3f;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void pistolVDoublesNextThreeShots(GameTestHelper helper) {
		ItemStack gun = new ItemStack(FirearmItems.PUNISHER_PISTOL);
		ServerPlayer p = punisher(helper, gun);
		FirearmData d = Firearms.get(Firearms.PISTOL);
		Villager v = dummy(helper, 5.5, 0.0);
		aimAtBody(p, v);

		PunisherWeaponAbilities.handle(p, true);
		helper.assertTrue(PunisherWeaponAbilities.pistolCharges(p) == 3, "V charges three shots");
		helper.assertFalse(Punisher.abilityReady(p, PunisherWeaponAbilities.cooldownId(Firearms.PISTOL, false)),
				"V goes on cooldown");
		helper.assertTrue(Punisher.abilityReady(p, PunisherWeaponAbilities.cooldownId(Firearms.PISTOL, true)),
				"Shift+V has its own cooldown, untouched");
		float[] dealt = new float[4];
		for (int i = 0; i < 4; i++) {
			readyToFire(p, gun);
			float before = v.getHealth();
			helper.assertTrue(FirearmShooting.fire(p, gun, d) == FirearmShooting.Result.FIRED, "shot " + i + " fires");
			dealt[i] = before - v.getHealth();
		}
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(near(dealt[i], d.bodyDamage * 2f), "charged shot " + i + " deals double, got " + dealt[i]);
		}
		helper.assertTrue(near(dealt[3], d.bodyDamage), "the fourth shot is back to normal, got " + dealt[3]);
		helper.assertTrue(PunisherWeaponAbilities.pistolCharges(p) == 0, "all three charges spent");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void pistolShiftVFiresAnEighteenDamageSlowingShot(GameTestHelper helper) {
		ItemStack gun = new ItemStack(FirearmItems.PUNISHER_PISTOL);
		ServerPlayer p = punisher(helper, gun);
		Villager v = dummy(helper, 5.5, 0.0);
		aimAtBody(p, v);
		FirearmStack.setLastFired(gun, p.level().getGameTime()); // just fired: the ability ignores the fire-rate gate
		p.setShiftKeyDown(true);
		PunisherWeaponAbilities.handle(p, true);
		helper.assertTrue(near(1000f - v.getHealth(), PunisherWeaponAbilities.PISTOL_SHOT_DAMAGE),
				"Shift+V deals 18, got " + (1000f - v.getHealth()));
		helper.assertTrue(v.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "and slows the target");
		helper.assertTrue(FirearmStack.magazine(gun, Firearms.get(Firearms.PISTOL)) == 11, "it is a real round from the magazine");
		helper.assertFalse(Punisher.abilityReady(p, PunisherWeaponAbilities.cooldownId(Firearms.PISTOL, true)), "Shift+V cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void shotgunVNeedsThreeShellsAndSpendsThem(GameTestHelper helper) {
		ItemStack gun = new ItemStack(FirearmItems.PUNISHER_SHOTGUN);
		ServerPlayer p = punisher(helper, gun);
		FirearmData d = Firearms.get(Firearms.SHOTGUN);
		Villager v = dummy(helper, 4.5, 0.0);
		aimAtBody(p, v);

		FirearmStack.setMagazine(gun, 2);
		PunisherWeaponAbilities.handle(p, true);
		helper.assertTrue(FirearmStack.magazine(gun, d) == 2, "two shells: refused, nothing spent");
		helper.assertTrue(Punisher.abilityReady(p, PunisherWeaponAbilities.cooldownId(Firearms.SHOTGUN, false)),
				"a refused blast costs no cooldown");
		helper.assertTrue(v.getHealth() == 1000f, "and fires nothing");

		FirearmStack.setMagazine(gun, 6);
		PunisherWeaponAbilities.handle(p, true);
		helper.assertTrue(FirearmStack.magazine(gun, d) == 3, "the blast spends three shells, left " + FirearmStack.magazine(gun, d));
		float dealt = 1000f - v.getHealth();
		helper.assertTrue(dealt >= PunisherWeaponAbilities.SHOTGUN_PELLET_DAMAGE - 0.01f,
				"pellets land 15 each, got " + dealt);
		helper.assertFalse(Punisher.abilityReady(p, PunisherWeaponAbilities.cooldownId(Firearms.SHOTGUN, false)), "V cooldown");

		// Shift+V: the wide fan, also three shells
		p.setShiftKeyDown(true);
		PunisherWeaponAbilities.handle(p, true);
		helper.assertTrue(FirearmStack.magazine(gun, d) == 0, "the wide blast spends the last three shells");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void rifleVDoublesFireRateAndHalvesReload(GameTestHelper helper) {
		ItemStack gun = new ItemStack(FirearmItems.PUNISHER_ASSAULT_RIFLE);
		ServerPlayer p = punisher(helper, gun);
		FirearmData d = Firearms.get(Firearms.RIFLE);
		int before = FirearmReload.stepTicks(p, d);
		float rateBefore = FirearmHooks.get().fireIntervalFactor(p);

		PunisherWeaponAbilities.handle(p, true);
		helper.assertTrue(PunisherWeaponAbilities.rapidFire(p), "Rapid Fire is running");
		int after = FirearmReload.stepTicks(p, d);
		helper.assertTrue(after <= (before + 1) / 2 && after < before, "reload halves: " + before + " -> " + after);
		helper.assertTrue(Math.abs(FirearmHooks.get().fireIntervalFactor(p) - rateBefore * 0.5f) < 0.001f,
				"fire interval halves (= double fire rate)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 200)
	public void rifleLockOnEmptiesTheMagazineAndBlocksReload(GameTestHelper helper) {
		ItemStack gun = new ItemStack(FirearmItems.PUNISHER_ASSAULT_RIFLE);
		ServerPlayer p = punisher(helper, gun);
		FirearmData d = Firearms.get(Firearms.RIFLE);
		Villager v = dummy(helper, 7.5, 0.0);
		aimAtBody(p, v);
		FirearmStack.setMagazine(gun, 10);

		p.setShiftKeyDown(true);
		PunisherWeaponAbilities.handle(p, true);
		helper.assertTrue(PunisherWeaponAbilities.lockedOn(p), "Shift+V locks onto the villager under the crosshair");
		FirearmReload.start(p, gun, d);
		helper.assertFalse(FirearmStack.isReloading(gun), "no reloading while locked on");
		helper.onEachTick(() -> PunisherWeaponAbilities.tick(p));
		helper.succeedWhen(() -> {
			helper.assertTrue(FirearmStack.magazine(gun, d) == 0, "the lock empties the magazine");
			helper.assertFalse(PunisherWeaponAbilities.lockedOn(p), "and ends once it is empty");
			float dealt = 1000f - v.getHealth();
			helper.assertTrue(dealt >= 10 * d.bodyDamage - 0.5f, "every round went into the target, dealt " + dealt);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void sniperVPiercesAndIgnoresArmour(GameTestHelper helper) {
		ItemStack gun = new ItemStack(FirearmItems.PUNISHER_SNIPER);
		ServerPlayer p = punisher(helper, gun);
		FirearmData d = Firearms.get(Firearms.SNIPER);
		Villager front = dummy(helper, 5.5, 20.0);
		Villager back = dummy(helper, 8.5, 20.0);
		aimAtBody(p, front);

		PunisherWeaponAbilities.handle(p, true);
		helper.assertTrue(PunisherWeaponAbilities.piercingArmed(p), "V chambers a piercing round");
		readyToFire(p, gun);
		FirearmShooting.fire(p, gun, d);
		float frontDealt = 1000f - front.getHealth();
		float backDealt = 1000f - back.getHealth();
		helper.assertTrue(backDealt > 0f, "the round went through into the second target");
		// 28 vs armour 20: 21.28 normally; with 40% of the armour ignored (12): 25.31
		helper.assertTrue(frontDealt > 24.5f, "armour partly ignored, dealt " + frontDealt);
		helper.assertFalse(PunisherWeaponAbilities.piercingArmed(p), "used up by the shot");

		front.setHealth(1000f);
		back.setHealth(1000f);
		readyToFire(p, gun);
		FirearmShooting.fire(p, gun, d);
		helper.assertTrue(back.getHealth() == 1000f, "a normal round stops at the first target");
		helper.assertTrue(1000f - front.getHealth() < 22.5f, "and pays full armour, dealt " + (1000f - front.getHealth()));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH, timeoutTicks = 200)
	public void sniperShiftVLocksScopesAndFiresDoubleHeadshot(GameTestHelper helper) {
		ItemStack gun = new ItemStack(FirearmItems.PUNISHER_SNIPER);
		ServerPlayer p = punisher(helper, gun);
		FirearmData d = Firearms.get(Firearms.SNIPER);
		Villager v = dummy(helper, 7.5, 0.0);
		aimAtBody(p, v);

		p.setShiftKeyDown(true);
		PunisherWeaponAbilities.handle(p, true);
		helper.assertTrue(PunisherWeaponAbilities.lockedOn(p), "Shift+V locks on");
		helper.assertTrue(p.getAttachedOrElse(ModAttachments.FIREARM_AIMING, false), "and raises the scope");
		helper.assertTrue(v.getHealth() == 1000f, "nothing fired yet");
		helper.onEachTick(() -> PunisherWeaponAbilities.tick(p));
		float expected = d.headDamage * PunisherWeaponAbilities.SNIPER_LOCK_FACTOR;
		helper.succeedWhen(() -> {
			helper.assertFalse(PunisherWeaponAbilities.lockedOn(p), "the lock fires once and ends");
			float dealt = 1000f - v.getHealth();
			helper.assertTrue(near(dealt, expected), "double headshot damage, expected " + expected + " got " + dealt);
			helper.assertFalse(p.getAttachedOrElse(ModAttachments.FIREARM_AIMING, false), "scope lowered again");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void swappingWeaponsCancelsTheAbility(GameTestHelper helper) {
		ItemStack pistol = new ItemStack(FirearmItems.PUNISHER_PISTOL);
		ServerPlayer p = punisher(helper, pistol);
		PunisherWeaponAbilities.handle(p, true);
		helper.assertTrue(PunisherWeaponAbilities.pistolCharges(p) == 3, "charged");
		p.getInventory().selected = 1; // another hotbar slot
		PunisherWeaponAbilities.tick(p);
		helper.assertTrue(PunisherWeaponAbilities.pistolCharges(p) == 0, "swapping away cancels the charged shots");
		p.getInventory().selected = 0;

		// the sniper lock, cancelled by a different gun in the same slot
		ItemStack sniper = new ItemStack(FirearmItems.PUNISHER_SNIPER);
		p.setItemInHand(InteractionHand.MAIN_HAND, sniper);
		Villager v = dummy(helper, 7.5, 0.0);
		aimAtBody(p, v);
		p.setShiftKeyDown(true);
		PunisherWeaponAbilities.handle(p, true);
		helper.assertTrue(PunisherWeaponAbilities.lockedOn(p), "locked on");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FirearmItems.PUNISHER_PISTOL));
		PunisherWeaponAbilities.tick(p);
		helper.assertFalse(PunisherWeaponAbilities.lockedOn(p), "a different gun cancels the lock");
		helper.assertTrue(v.getHealth() == 1000f, "and the shot never fires");

		// nothing (or not a gun) in hand: refused, no cooldown spent
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		p.setShiftKeyDown(false);
		PunisherWeaponAbilities.handle(p, true);
		helper.assertTrue(PunisherWeaponAbilities.pistolCharges(p) == 0, "no gun: nothing happens");
		helper.succeed();
	}
}
