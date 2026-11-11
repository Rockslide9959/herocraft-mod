package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.eyeguy.EyeGuyKit;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;

/**
 * v0.17.3 Eye Guy (user: "make aliens based on their abilities and stuff they do in the show please"): a hidden prototype alien found by a
 * mistransform (the draw itself is {@code OmnitrixCannonboltGameTests}, pool-driven); glyph U+E01C; every key has a move; the numbers on
 * the damage scale; and each move doing what it says -- the laser, the six-eye volley, the freeze and fire beams, the charged chest beam,
 * the flash, All-Seeing, the dodge, and his passives / weakness.
 */
public class OmnitrixEyeGuyGameTests extends OmnitrixNewAlienTestBase {
	private static EyeGuyKit kit() {
		return (EyeGuyKit) Aliens.kit(EyeGuyKit.ID);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void eyeGuyIsAHiddenPrototypeAlien(GameTestHelper helper) {
		hiddenAlienChecks(helper, Aliens.EYE_GUY, '');
		keyLayoutChecks(helper, kit());
		helper.assertTrue(kit().moveOn(AbilitySlot.SLOT_1, true, false) != null && kit().moveOn(AbilitySlot.SLOT_2, true, false) != null,
				"Shift+R (Laser Volley) and Shift+G (Fire Beam)");
		helper.succeed();
	}

	/** Basic 5-8, specials 12-17, ultimates 21-36. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void numbersSitOnTheDamageScale(GameTestHelper helper) {
		helper.assertTrue(EyeGuyKit.LASER_DAMAGE >= 5 && EyeGuyKit.LASER_DAMAGE <= 8, "Eye Laser is basic");
		helper.assertTrue(EyeGuyKit.VOLLEY_DAMAGE >= 5 && EyeGuyKit.VOLLEY_DAMAGE <= 8, "each Volley laser is basic");
		helper.assertTrue(EyeGuyKit.FLASH_DAMAGE >= 5 && EyeGuyKit.FLASH_DAMAGE <= 8, "Eye Flash is basic (it is a blind, not a hit)");
		helper.assertTrue(EyeGuyKit.FREEZE_DAMAGE >= 12 && EyeGuyKit.FREEZE_DAMAGE <= 17 && EyeGuyKit.FIRE_DAMAGE >= 12 && EyeGuyKit.FIRE_DAMAGE <= 17,
				"Freeze and Fire Beam are specials");
		helper.assertTrue(EyeGuyKit.MEGA_DAMAGE >= 21 && EyeGuyKit.MEGA_DAMAGE <= 36, "the Chest Eye Beam is an ultimate");
		helper.assertTrue(kit().stats().maxHealth() == 28f, "frail: 28 health");
		helper.succeed();
	}

	/** R: about 7 at the crosshair. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void eyeLaserHitsTheCrosshair(GameTestHelper helper) {
		ServerPlayer p = alien(helper, EyeGuyKit.ID);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 6.5);
		helper.runAfterDelay(2, () -> {
			lookAt(p, g);
			float h = g.getHealth();
			tap(p, AbilitySlot.SLOT_1, false);
			near(helper, h - g.getHealth(), EyeGuyKit.LASER_DAMAGE, 0.05f, "Eye Laser");
			helper.assertTrue(kit().cooldownLeft(p, EyeGuyKit.LASER_ID) > 0, "on cooldown");
			helper.succeed();
		});
	}

	/** Shift+R: all six eyes into a lone creature (6 x 6). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void laserVolleyPutsSixEyesOnALoneTarget(GameTestHelper helper) {
		ServerPlayer p = alien(helper, EyeGuyKit.ID);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 6.5);
		helper.runAfterDelay(2, () -> {
			lookAt(p, g);
			float h = g.getHealth();
			tap(p, AbilitySlot.SLOT_1, true);
			near(helper, h - g.getHealth(), EyeGuyKit.VOLLEY_DAMAGE * EyeGuyKit.VOLLEY_EYES, 0.1f, "six Volley lasers");
			helper.succeed();
		});
	}

	/** G: 12, slowed hard and frozen over. Shift+G: 12 and alight (and the frost burnt off). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void freezeAndFireBeams(GameTestHelper helper) {
		ServerPlayer p = alien(helper, EyeGuyKit.ID);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 6.5);
		helper.runAfterDelay(2, () -> {
			lookAt(p, g);
			float h = g.getHealth();
			tap(p, AbilitySlot.SLOT_2, false);
			near(helper, h - g.getHealth(), EyeGuyKit.FREEZE_DAMAGE, 0.05f, "Freeze Beam");
			MobEffectInstance slow = g.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
			helper.assertTrue(slow != null && slow.getAmplifier() == 3, "Slowness IV");
			helper.assertTrue(g.getTicksFrozen() >= g.getTicksRequiredToFreeze(), "frozen solid");
			h = g.getHealth();
			tap(p, AbilitySlot.SLOT_2, true);
			near(helper, h - g.getHealth(), EyeGuyKit.FIRE_DAMAGE, 0.05f, "Fire Beam");
			helper.assertTrue(g.isOnFire() && g.getTicksFrozen() == 0, "set alight, the frost gone");
			helper.succeed();
		});
	}

	/** Z: charges 1.5 s (nothing fires before), then 32 to everything on the line. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void chestEyeBeamChargesThenPierces(GameTestHelper helper) {
		ServerPlayer p = alien(helper, EyeGuyKit.ID);
		IronGolem a = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 3.5);
		IronGolem b = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 5.5); // both well on the floor (knocked back, not off it)
		float[] h = new float[2];
		helper.runAfterDelay(2, () -> {
			lookAt(p, a);
			p.setXRot(0f);
			h[0] = a.getHealth();
			h[1] = b.getHealth();
			tap(p, AbilitySlot.SLOT_4, false);
			helper.assertTrue(kit().flag(p, EyeGuyKit.F_MEGA), "charging");
			tap(p, AbilitySlot.SLOT_1, false);
			helper.assertTrue(a.getHealth() == h[0], "nothing else while the chest eye charges");
		});
		helper.runAfterDelay(2 + EyeGuyKit.MEGA_CHARGE_TICKS - 5, () -> helper.assertTrue(a.getHealth() == h[0], "not fired yet"));
		helper.runAfterDelay(2 + EyeGuyKit.MEGA_CHARGE_TICKS + 4, () -> {
			helper.assertFalse(kit().flag(p, EyeGuyKit.F_MEGA), "fired");
			near(helper, h[0] - a.getHealth(), EyeGuyKit.MEGA_DAMAGE, 0.1f, "the near golem");
			near(helper, h[1] - b.getHealth(), EyeGuyKit.MEGA_DAMAGE, 0.1f, "and the one behind it (it pierces)");
			helper.succeed();
		});
	}

	/** C: everything within 9 takes 5 and is blinded. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void eyeFlashBlindsEverythingRound(GameTestHelper helper) {
		ServerPlayer p = alien(helper, EyeGuyKit.ID);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 5.5);
		IronGolem behind = mob(helper, EntityType.IRON_GOLEM, 6.5, 2, 1.5);
		helper.runAfterDelay(2, () -> {
			float h = g.getHealth(), hb = behind.getHealth();
			tap(p, AbilitySlot.SLOT_6, false);
			near(helper, h - g.getHealth(), EyeGuyKit.FLASH_DAMAGE, 0.05f, "Eye Flash");
			near(helper, hb - behind.getHealth(), EyeGuyKit.FLASH_DAMAGE, 0.05f, "Eye Flash (all round him)");
			helper.assertTrue(g.hasEffect(MobEffects.BLINDNESS) && behind.hasEffect(MobEffects.BLINDNESS), "blinded");
			helper.succeed();
		});
	}

	/** V: All-Seeing for 12 s, with night vision, then it ends. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 320)
	public void allSeeingLastsTwelveSeconds(GameTestHelper helper) {
		ServerPlayer p = alien(helper, EyeGuyKit.ID);
		helper.runAfterDelay(2, () -> {
			tap(p, AbilitySlot.SLOT_5, false);
			helper.assertTrue(EyeGuyKit.seeing(p) && p.hasEffect(MobEffects.NIGHT_VISION), "All-Seeing up, the dark no bar");
		});
		helper.runAfterDelay(2 + EyeGuyKit.SIGHT_TICKS - 10, () -> helper.assertTrue(EyeGuyKit.seeing(p), "still up near the end"));
		helper.runAfterDelay(2 + EyeGuyKit.SIGHT_TICKS + 5, () -> {
			helper.assertFalse(EyeGuyKit.seeing(p), "over after 12 s");
			helper.assertFalse(p.hasEffect(MobEffects.NIGHT_VISION), "night vision with it");
			helper.succeed();
		});
	}

	/** X: a hop, untouchable for a moment. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void eyeDodgeHopsClear(GameTestHelper helper) {
		ServerPlayer p = alien(helper, EyeGuyKit.ID);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2, 5.5);
		helper.runAfterDelay(2, () -> {
			tap(p, AbilitySlot.SLOT_3, false);
			helper.assertTrue(p.getDeltaMovement().y > 0.3 && p.getDeltaMovement().horizontalDistance() > 0.8, "hopped: " + p.getDeltaMovement());
			float h = p.getHealth();
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().mobAttack(z), 4f);
			helper.assertTrue(p.getHealth() == h, "untouchable mid-hop");
		});
		helper.runAfterDelay(2 + EyeGuyKit.DODGE_TICKS + 4, () -> {
			helper.assertFalse(kit().flag(p, EyeGuyKit.F_DODGE), "the window closes");
			helper.succeed();
		});
	}

	/** Opticoid: a hit from behind does 25% less. Many Eyes: Blindness never takes him. Dazzled: burning, his beams do half. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void passivesAndWeakness(GameTestHelper helper) {
		ServerPlayer p = alien(helper, EyeGuyKit.ID);
		Zombie front = mob(helper, EntityType.ZOMBIE, 3.5, 2, 4.5);
		Zombie back = mob(helper, EntityType.ZOMBIE, 3.5, 2, -1.5);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 6.5, 2, 6.0); // off to the side: the front zombie is not in the way
		helper.runAfterDelay(2, () -> {
			p.setYRot(0f);
			DamageSource fromFront = p.damageSources().mobAttack(front), fromBack = p.damageSources().mobAttack(back);
			near(helper, kit().incomingDamage(p, fromFront, 8f), 8f, 0.01f, "a hit from the front");
			near(helper, kit().incomingDamage(p, fromBack, 8f), 8f * EyeGuyKit.BEHIND_TAKEN, 0.01f, "a hit from behind");
			p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200));
		});
		helper.runAfterDelay(5, () -> {
			helper.assertFalse(p.hasEffect(MobEffects.BLINDNESS), "Many Eyes: never blinded");
			p.igniteForSeconds(5);
			lookAt(p, g);
			float h = g.getHealth();
			tap(p, AbilitySlot.SLOT_1, false);
			near(helper, h - g.getHealth(), EyeGuyKit.LASER_DAMAGE * EyeGuyKit.DAZZLED_DEALT, 0.05f, "Dazzled: half while burning");
			helper.succeed();
		});
	}
}
