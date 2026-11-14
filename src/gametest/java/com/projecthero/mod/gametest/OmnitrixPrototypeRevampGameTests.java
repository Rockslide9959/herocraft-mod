package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.alien.AlienConfusion;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.arctiguana.ArctiguanaKit;
import com.projecthero.mod.omnitrix.alien.bigchill.BigChillKit;
import com.projecthero.mod.omnitrix.alien.eyeguy.EyeGuyKit;
import com.projecthero.mod.omnitrix.alien.shockrock.ShockRockKit;
import com.projecthero.mod.omnitrix.alien.snareoh.SnareohKit;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;

/**
 * v0.17.4 (docs/OMNITRIX_PROTOTYPE_REVAMP_0174.md): the riskier new logic of the prototype aliens' revamp -- Sand Coffin crushes only what
 * Snare-oh has bound, Shatter breaks only what is frozen solid, the Cocoon blocks damage and heals in steady steps, the holds stop by
 * themselves at their longest run (with steady pulse sizes on the way), Thermal Shock shatters a creature it held the whole time, and Hypno
 * Gaze turns a mob on its friends. Iron golems (no armour) give exact numbers; every timing check is on pulse sizes, not totals.
 */
public class OmnitrixPrototypeRevampGameTests extends OmnitrixNewAlienTestBase {
	private static <K> K kit(String id) {
		@SuppressWarnings("unchecked")
		K k = (K) Aliens.kit(id);
		return k;
	}

	private static boolean rooted(LivingEntity e) {
		MobEffectInstance s = e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		return s != null && s.getAmplifier() >= 9;
	}

	/** Every health drop of {@code e} (tick by tick), for step-size checks. */
	private static List<Float> drops(GameTestHelper helper, LivingEntity e) {
		List<Float> out = new ArrayList<>();
		float[] last = { e.getHealth() };
		helper.onEachTick(() -> {
			float h = e.getHealth();
			if (h < last[0] - 0.01f) {
				out.add(last[0] - h);
			}
			last[0] = h;
		});
		return out;
	}

	/** Shift+Z Sand Coffin: 20 to the golem he bound, nothing to the one he did not; the binding ends; nothing bound costs no cooldown. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void sandCoffinCrushesOnlyWhatHeBound(GameTestHelper helper) {
		ServerPlayer p = alien(helper, SnareohKit.ID);
		SnareohKit k = kit(SnareohKit.ID);
		IronGolem bound = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 6.5);
		IronGolem free = mob(helper, EntityType.IRON_GOLEM, 6.5, 2, 3.5);
		helper.runAfterDelay(2, () -> {
			lookAt(p, bound);
			tap(p, AbilitySlot.SLOT_1, true); // Bandage Bind
			helper.assertTrue(SnareohKit.boundBy(p, bound) && rooted(bound), "the first golem is bound");
			helper.assertFalse(SnareohKit.boundBy(p, free), "the second is not");
			float hb = bound.getHealth(), hf = free.getHealth();
			tap(p, AbilitySlot.SLOT_4, true); // Sand Coffin
			near(helper, hb - bound.getHealth(), SnareohKit.COFFIN_DAMAGE, 0.01f, "Sand Coffin on the bound golem");
			near(helper, hf - free.getHealth(), 0f, 0.01f, "Sand Coffin leaves the unbound golem alone");
			helper.assertFalse(SnareohKit.boundBy(p, bound) || rooted(bound), "the binding ends");
			k.cooldown(p, SnareohKit.COFFIN, 0);
			helper.assertTrue(k.sandCoffin(p) == 0 && k.cooldownLeft(p, SnareohKit.COFFIN) == 0, "nothing bound: nothing happens, no cooldown");
			helper.succeed();
		});
	}

	/** Shift+Z Shatter: 14 to the frozen golem, nothing to the other; the ice is gone; nothing frozen costs no cooldown. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void shatterBreaksOnlyWhatIsFrozen(GameTestHelper helper) {
		ServerPlayer p = alien(helper, ArctiguanaKit.ID);
		ArctiguanaKit k = kit(ArctiguanaKit.ID);
		IronGolem frozen = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 5.5);
		IronGolem warm = mob(helper, EntityType.IRON_GOLEM, 6.5, 2, 3.5);
		helper.runAfterDelay(2, () -> {
			ArctiguanaKit.freeze(p, frozen, 60);
			helper.assertTrue(BigChillKit.shelled(frozen) && !BigChillKit.shelled(warm), "one golem frozen solid");
			float hf = frozen.getHealth(), hw = warm.getHealth();
			tap(p, AbilitySlot.SLOT_4, true); // Shatter
			near(helper, hf - frozen.getHealth(), ArctiguanaKit.SHATTER_DAMAGE, 0.01f, "Shatter on the frozen golem");
			near(helper, hw - warm.getHealth(), 0f, 0.01f, "Shatter leaves the unfrozen golem alone");
			helper.assertFalse(BigChillKit.shelled(frozen) || rooted(frozen), "out of the ice");
			k.cooldown(p, ArctiguanaKit.SHATTER, 0);
			helper.assertTrue(k.shatter(p) == 0 && k.cooldownLeft(p, ArctiguanaKit.SHATTER) == 0, "nothing frozen: nothing happens, no cooldown");
			helper.succeed();
		});
	}

	/** Hold Z Cocoon: no damage gets in, it heals 2 a step, it ends by itself at 4 s with a burst (6) and its cooldown. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void cocoonBlocksDamageHealsAndEndsAtItsLimit(GameTestHelper helper) {
		ServerPlayer p = alien(helper, SnareohKit.ID);
		SnareohKit k = kit(SnareohKit.ID);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2, 3.5);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 2.6);
		List<Float> heals = new ArrayList<>();
		float[] last = { -1f };
		// (registered up front: a tick callback added from inside another one would change the test's schedule while it runs)
		helper.onEachTick(() -> {
			if (last[0] < 0f) {
				return;
			}
			float h = p.getHealth();
			if (h > last[0] + 0.01f) {
				heals.add(h - last[0]);
			}
			last[0] = h;
		});
		helper.runAfterDelay(2, () -> {
			p.setHealth(10f);
			p.getFoodData().setFoodLevel(10); // (no natural regeneration muddying the heal steps)
			p.getFoodData().setSaturation(0f);
			k.holdStart(p, AbilitySlot.SLOT_4);
			helper.assertTrue(SnareohKit.cocooned(p), "wrapped up");
			p.invulnerableTime = 0;
			helper.assertFalse(p.hurt(p.damageSources().mobAttack(z), 6f), "nothing gets through the cocoon");
			last[0] = p.getHealth();
		});
		float[] golem = new float[1];
		helper.runAfterDelay(2 + SnareohKit.COCOON_MAX - 4, () -> {
			helper.assertTrue(SnareohKit.cocooned(p), "still wrapped near the end of the 4 s");
			golem[0] = g.getHealth();
		});
		helper.runAfterDelay(2 + SnareohKit.COCOON_MAX + 3, () -> {
			helper.assertFalse(SnareohKit.cocooned(p), "the cocoon bursts by itself at 4 s");
			helper.assertTrue(k.cooldownLeft(p, SnareohKit.COCOON) > 0, "and its cooldown starts");
			near(helper, golem[0] - g.getHealth(), SnareohKit.COCOON_BURST, 0.01f, "the burst");
			helper.assertTrue(!heals.isEmpty(), "it healed");
			for (float s : heals) {
				near(helper, s, SnareohKit.COCOON_HEAL, 0.01f, "each heal step");
			}
			helper.succeed();
		});
	}

	/** Hold R Lash Flurry, never let go: every lash is 4, and it stops by itself at 2 s with its cooldown. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void lashFlurryPulsesAndStopsAtItsLimit(GameTestHelper helper) {
		ServerPlayer p = alien(helper, SnareohKit.ID);
		SnareohKit k = kit(SnareohKit.ID);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 5.0);
		List<Float> d = drops(helper, g);
		helper.runAfterDelay(2, () -> {
			lookAt(p, g);
			k.holdStart(p, AbilitySlot.SLOT_1);
			helper.assertTrue(k.flag(p, SnareohKit.F_LASH), "lashing");
		});
		helper.onEachTick(() -> lookAt(p, g));
		helper.runAfterDelay(2 + SnareohKit.LASH_MAX + 3, () -> {
			helper.assertFalse(k.flag(p, SnareohKit.F_LASH), "the flurry stops by itself at 2 s");
			helper.assertTrue(k.cooldownLeft(p, SnareohKit.LASH) > 0, "and its cooldown starts");
			helper.assertTrue(d.size() >= 3 && d.size() <= SnareohKit.LASH_MAX / SnareohKit.LASH_EVERY + 1, "a lash every quarter second: " + d.size());
			for (float s : d) {
				near(helper, s, SnareohKit.LASH_DAMAGE, 0.01f, "each lash");
			}
			helper.succeed();
		});
	}

	/** Hold R Sweeping Laser, never let go: 2 a pulse to the golem in the beam, and it stops by itself at 3 s. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void sweepingLaserPulsesAndStopsAtItsLimit(GameTestHelper helper) {
		ServerPlayer p = alien(helper, EyeGuyKit.ID);
		EyeGuyKit k = kit(EyeGuyKit.ID);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 6.0);
		List<Float> d = drops(helper, g);
		helper.onEachTick(() -> lookAt(p, g));
		helper.runAfterDelay(2, () -> {
			lookAt(p, g);
			k.holdStart(p, AbilitySlot.SLOT_1);
			helper.assertTrue(k.flag(p, EyeGuyKit.F_SWEEP), "the beam is on");
		});
		helper.runAfterDelay(2 + EyeGuyKit.SWEEP_MAX + 3, () -> {
			helper.assertFalse(k.flag(p, EyeGuyKit.F_SWEEP), "the beam stops by itself at 3 s");
			helper.assertTrue(k.cooldownLeft(p, EyeGuyKit.SWEEP) > 0, "and its cooldown starts");
			helper.assertTrue(d.size() >= 5, "it kept hitting: " + d.size());
			for (float s : d) {
				near(helper, s, EyeGuyKit.SWEEP_DAMAGE, 0.01f, "each pulse");
			}
			helper.succeed();
		});
	}

	/** Hold Z Core Overload Burst held past full: it goes off by itself (the full 28 within 9) at its limit. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void coreOverloadGoesOffAtItsLimit(GameTestHelper helper) {
		ServerPlayer p = alien(helper, ShockRockKit.ID);
		ShockRockKit k = kit(ShockRockKit.ID);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 6.5);
		float[] h = new float[1];
		helper.runAfterDelay(2, () -> {
			h[0] = g.getHealth();
			k.holdStart(p, AbilitySlot.SLOT_4);
			helper.assertTrue(k.flag(p, ShockRockKit.F_CORE), "charging");
		});
		helper.runAfterDelay(2 + ShockRockKit.CORE_MAX - 3, () -> {
			helper.assertTrue(k.flag(p, ShockRockKit.F_CORE), "still charging just before the limit");
			near(helper, h[0] - g.getHealth(), 0f, 0.01f, "nothing hit yet");
		});
		helper.runAfterDelay(2 + ShockRockKit.CORE_MAX + 3, () -> {
			helper.assertFalse(k.flag(p, ShockRockKit.F_CORE), "it went off by itself");
			near(helper, h[0] - g.getHealth(), ShockRockKit.CORE_DMG_MAX, 0.01f, "fully charged: 28");
			helper.assertTrue(k.cooldownLeft(p, ShockRockKit.CORE_BURST) > 0, "and its cooldown starts");
			helper.succeed();
		});
	}

	/** Hold G Thermal Shock held to its limit on one golem: 4 a pulse, then a single shatter of 10 when it ends. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void thermalShockShattersWhatItHeldTheWholeTime(GameTestHelper helper) {
		ServerPlayer p = alien(helper, EyeGuyKit.ID);
		EyeGuyKit k = kit(EyeGuyKit.ID);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 5.5);
		List<Float> d = drops(helper, g);
		helper.onEachTick(() -> {
			lookAt(p, g);
			g.clearFire(); // (the burn ticks would muddy the step sizes)
		});
		helper.runAfterDelay(2, () -> k.holdStart(p, AbilitySlot.SLOT_2));
		helper.runAfterDelay(2 + EyeGuyKit.THERMAL_MAX + 3, () -> {
			helper.assertFalse(k.flag(p, EyeGuyKit.F_THERMAL), "it stops by itself at 2.5 s");
			int shatters = 0, pulses = 0;
			for (float s : d) {
				if (Math.abs(s - EyeGuyKit.THERMAL_SHATTER) < 0.01f) {
					shatters++;
				} else {
					near(helper, s, EyeGuyKit.THERMAL_DAMAGE, 0.01f, "each pulse");
					pulses++;
				}
			}
			helper.assertTrue(pulses >= EyeGuyKit.THERMAL_MIN_PULSES, "pulses: " + pulses);
			helper.assertTrue(shatters == 1, "one shatter at the end: " + shatters);
			helper.succeed();
		});
	}

	/** Shift+C Hypno Gaze: the zombie forgets him and goes for the other zombie, and lets go when it ends; a boss can't be turned. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void hypnoGazeTurnsAMobOnItsFriend(GameTestHelper helper) {
		ServerPlayer p = alien(helper, EyeGuyKit.ID);
		Zombie a = mob(helper, EntityType.ZOMBIE, 3.5, 2, 5.5);
		Zombie b = mob(helper, EntityType.ZOMBIE, 6.5, 2, 6.5);
		helper.runAfterDelay(2, () -> {
			a.setTarget(p);
			lookAt(p, a);
			tap(p, AbilitySlot.SLOT_6, true);
			helper.assertTrue(AlienConfusion.turned(p, a) && a.getTarget() == b, "it goes for the other zombie: " + a.getTarget());
			helper.assertFalse(AlienConfusion.turnable(new net.minecraft.world.entity.boss.wither.WitherBoss(EntityType.WITHER, helper.getLevel())),
					"a boss can't be turned");
		});
		helper.runAfterDelay(2 + EyeGuyKit.HYPNO_TICKS + 3, () -> {
			helper.assertFalse(AlienConfusion.turned(p, a) || a.getTarget() == b, "it comes to its senses after 5 s");
			helper.succeed();
		});
	}
}
