package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.snareoh.SnareohKit;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Snare-oh (user: "make aliens based on their abilities and stuff they do in the show please"): a hidden prototype alien (glyph
 * U+E01B), his key layout, and his main numbers -- the whip, the bind (a root), the grapple's yank, Mummy Wrap round him, Unravel Spin's
 * three lashes, Scatter's untouchable second and a half, and the Hollow Body / Dry Wrappings damage changes. v0.17.4: the revamped
 * layout (the new moves' riskier logic is in {@code OmnitrixPrototypeRevampGameTests}).
 */
public class OmnitrixSnareohGameTests extends OmnitrixSnareohTestBase {
	private static SnareohKit kit() {
		return (SnareohKit) Aliens.kit(SnareohKit.ID);
	}

	private static boolean rooted(net.minecraft.world.entity.LivingEntity e) {
		MobEffectInstance s = e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		return s != null && s.getAmplifier() >= 9;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void snareohIsAHiddenPrototypeAlien(GameTestHelper helper) {
		hiddenAlienChecks(helper, Aliens.SNARE_OH, '');
		keyLayoutChecks(helper, kit());
		helper.succeed();
	}

	/** R: 7 to a golem; Shift+R: 6 and bound (Slowness X); G: the zombie is yanked toward him. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void whipBindAndPull(GameTestHelper helper) {
		ServerPlayer p = alien(helper, SnareohKit.ID);
		IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 6.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 6.5, 2, 6.5);
		helper.runAfterDelay(2, () -> {
			lookAt(p, g);
			float h = g.getHealth();
			tap(p, AbilitySlot.SLOT_1, false);
			near(helper, h - g.getHealth(), SnareohKit.WHIP_DAMAGE, 0.01f, "Bandage Whip on a golem");
			g.invulnerableTime = 0;
			h = g.getHealth();
			tap(p, AbilitySlot.SLOT_1, true);
			near(helper, h - g.getHealth(), SnareohKit.BIND_DAMAGE, 0.01f, "Bandage Bind on a golem");
			helper.assertTrue(rooted(g), "the golem is bound in place");
			lookAt(p, z);
			double before = z.distanceTo(p);
			tap(p, AbilitySlot.SLOT_2, false);
			Vec3 v = z.getDeltaMovement();
			Vec3 toMe = p.position().subtract(z.position()).normalize();
			helper.assertTrue(v.x * toMe.x + v.z * toMe.z > 0.5, "Grapple Pull yanks the zombie at him (" + v + ", was " + before + " away)");
			helper.succeed();
		});
	}

	/** Z binds and hurts everything round him; C lands three lashes over the spin. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void mummyWrapAndUnravelSpin(GameTestHelper helper) {
		ServerPlayer p = alien(helper, SnareohKit.ID);
		IronGolem a = mob(helper, EntityType.IRON_GOLEM, 1.5, 2, 3.5);
		IronGolem b = mob(helper, EntityType.IRON_GOLEM, 5.5, 2, 3.5);
		float[] start = new float[2];
		helper.runAfterDelay(2, () -> {
			float ha = a.getHealth(), hb = b.getHealth();
			tap(p, AbilitySlot.SLOT_4, false);
			near(helper, ha - a.getHealth(), SnareohKit.WRAP_DAMAGE, 0.01f, "Mummy Wrap, first golem");
			near(helper, hb - b.getHealth(), SnareohKit.WRAP_DAMAGE, 0.01f, "Mummy Wrap, second golem");
			helper.assertTrue(rooted(a) && rooted(b), "both bound");
			start[0] = a.getHealth();
			start[1] = b.getHealth();
			tap(p, AbilitySlot.SLOT_6, false);
		});
		helper.runAfterDelay(30, () -> {
			near(helper, start[0] - a.getHealth(), SnareohKit.SPIN_DAMAGE * SnareohKit.SPIN_HITS, 0.01f, "Unravel Spin: three lashes");
			near(helper, start[1] - b.getHealth(), SnareohKit.SPIN_DAMAGE * SnareohKit.SPIN_HITS, 0.01f, "Unravel Spin, the other golem");
			helper.assertFalse(kit().flag(p, SnareohKit.F_SPIN), "the spin is over");
			helper.succeed();
		});
	}

	/** v0.17.4: Detach is gone; X is the zip, Shift+X Recoil, hold X the swing (a real hold now), and the new holds are declared. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void revampedKeyLayout(GameTestHelper helper) {
		SnareohKit k = kit();
		helper.assertTrue(k.move("detach") == null, "Detach is gone");
		helper.assertTrue(SnareohKit.ZIP.equals(k.moveOn(AbilitySlot.SLOT_3, false, false).id()), "X: Bandage Zip");
		helper.assertTrue(SnareohKit.RECOIL.equals(k.moveOn(AbilitySlot.SLOT_3, true, false).id()), "Shift+X: Recoil");
		helper.assertTrue(SnareohKit.SWING.equals(k.moveOn(AbilitySlot.SLOT_3, false, true).id()) && k.holdable(AbilitySlot.SLOT_3), "hold X: Bandage Swing");
		helper.assertTrue(SnareohKit.SCATTER.equals(k.moveOn(AbilitySlot.SLOT_5, false, false).id()), "V: Scatter");
		helper.assertTrue(SnareohKit.NET.equals(k.moveOn(AbilitySlot.SLOT_5, true, false).id()), "Shift+V: Bandage Net");
		for (AbilitySlot s : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
				AbilitySlot.SLOT_6 }) {
			helper.assertTrue(k.moveOn(s, true, false) != null, "a Shift move on " + s);
		}
		helper.succeed();
	}

	/** V (v0.17.4: was Shift+V): scattered he takes nothing; Hollow Body halves an arrow; Dry Wrappings: fire x1.5. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void scatterHollowBodyAndDryWrappings(GameTestHelper helper) {
		ServerPlayer p = alien(helper, SnareohKit.ID);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2, 5.5);
		helper.runAfterDelay(2, () -> {
			Arrow arrow = EntityType.ARROW.create(helper.getLevel());
			float shot = kit().incomingDamage(p, p.damageSources().arrow(arrow, z), 10f);
			near(helper, shot, 5f, 0.01f, "an arrow through his empty wrappings");
			float fire = kit().incomingDamage(p, p.damageSources().inFire(), 4f);
			near(helper, fire, 6f, 0.01f, "fire on dry bandages");
			tap(p, AbilitySlot.SLOT_5, false);
			helper.assertTrue(SnareohKit.scattered(p), "scattered");
			float hp = p.getHealth();
			p.invulnerableTime = 0;
			helper.assertFalse(p.hurt(p.damageSources().mobAttack(z), 6f), "nothing hits a cloud of bandages");
			helper.assertTrue(p.getHealth() >= hp, "no damage");
		});
		helper.runAfterDelay(2 + SnareohKit.SCATTER_TICKS + 3, () -> {
			helper.assertFalse(SnareohKit.scattered(p), "pulled back together after 1.5 s");
			helper.succeed();
		});
	}

	/** Bind with nothing in sight refuses and costs no cooldown. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void bindWithoutATargetRefuses(GameTestHelper helper) {
		ServerPlayer p = alien(helper, SnareohKit.ID);
		helper.runAfterDelay(2, () -> {
			p.setXRot(-80f);
			tap(p, AbilitySlot.SLOT_1, true);
			tap(p, AbilitySlot.SLOT_2, false);
			helper.assertTrue(kit().cooldownLeft(p, SnareohKit.BIND) == 0 && kit().cooldownLeft(p, SnareohKit.PULL) == 0, "no cooldown spent");
			helper.succeed();
		});
	}
}
