package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderMoves;
import com.projecthero.mod.spider.SpiderPerfectDodge;
import com.projecthero.mod.spider.SpiderStealth;
import com.projecthero.mod.spider.SpiderTakedown;
import com.projecthero.mod.spider.SpiderWrap;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** v0.16.2: the Inverted Takedown (cocoon, stays hidden, cooldown) and the perfect dodge (window, evasion, slow, x2 counter). */
public class SpiderReflexGameTests implements FabricGameTest {

	private static ServerPlayer spider(GameTestHelper helper, Vec3 rel) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		ExperimentalPowers.grant(p, Powers.byKey(SpiderMan.SPIDER_ADHESION_KEY));
		SpiderMan.evolveFromAdhesion(p);
		SpiderStealth.clearFor(p);
		SpiderMoves.clear(p);
		Vec3 at = helper.absoluteVec(rel);
		p.moveTo(at.x, at.y, at.z, 0f, 0f); // facing +z
		p.setNoGravity(true);
		return p;
	}

	private static Zombie zombie(GameTestHelper helper, Vec3 rel) {
		Zombie z = EntityType.HUSK.create(helper.getLevel()); // a husk: no burning in the sun to muddy the health checks
		Vec3 at = helper.absoluteVec(rel);
		z.moveTo(at.x, at.y, at.z, 180f, 0f);
		z.setNoAi(true);
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	// ---------------- Inverted Takedown ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void takedownCocoonsAnUnawareMobBesideHimAndKeepsHimHidden(GameTestHelper helper) {
		ServerPlayer p = spider(helper, new Vec3(3.5, 6.0, 2.5));
		Zombie z = zombie(helper, new Vec3(3.5, 1.0, 4.5));
		Zombie aware = zombie(helper, new Vec3(5.5, 1.0, 4.5));
		aware.setTarget(p);
		Vec3 head = new Vec3(p.getX(), p.getY() + 0.1, p.getZ());
		helper.assertTrue(SpiderTakedown.validTarget(p, z, head, 0f), "an unaware zombie below and in front is a target");
		helper.assertFalse(SpiderTakedown.validTarget(p, aware, head, 0f), "one already hunting him is not");
		helper.assertFalse(SpiderTakedown.validTarget(p, z, head, 180f), "nor one behind him");
		Vec3 anchor = p.position().add(0, 2.2, 0);
		helper.assertTrue(SpiderTakedown.start(p, z, anchor, false), "the takedown starts");
		helper.assertFalse(SpiderMan.abilityReady(p, SpiderTakedown.TAKEDOWN), "and goes on its 8 s cooldown");
		helper.assertTrue(SpiderMan.abilityCooldownRemaining(p, SpiderTakedown.TAKEDOWN) > 150, "8 seconds of it");
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(SpiderWrap.isWrapped(z), "it is cocooned");
			long until = z.getAttachedOrElse(ModAttachments.SPIDER_WRAP_UNTIL, 0L);
			helper.assertTrue(until > helper.getLevel().getGameTime() + 400, "for about 25 s from a ceiling hang (" + until + ")");
			helper.assertTrue(z.getY() > p.getY() - 1.0 && z.distanceTo(p) < 2.5,
					"hauled up to hang beside him (dy " + (z.getY() - p.getY()) + ", d " + z.distanceTo(p) + ")");
			helper.assertTrue(z.getHealth() == z.getMaxHealth(), "silently: no damage");
			helper.assertTrue(SpiderStealth.level(p) != SpiderStealth.EXPOSED, "and he stays hidden");
			SpiderWrap.release(z, false);
			SpiderMoves.clear(p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void takedownFromHisOwnWebIsAPermanentCocoon(GameTestHelper helper) {
		ServerPlayer p = spider(helper, new Vec3(3.5, 5.0, 2.5));
		Vec3 a = helper.absoluteVec(new Vec3(0.5, 7.4, 2.5));
		Vec3 b = helper.absoluteVec(new Vec3(7.5, 7.4, 2.5));
		SpiderMoves.buildTightrope(p, a, b);
		Zombie z = zombie(helper, new Vec3(3.5, 1.0, 4.0));
		Vec3 anchor = a.lerp(b, 0.5);
		helper.assertTrue(SpiderTakedown.start(p, z, anchor, true), "the takedown starts");
		helper.runAfterDelay(18, () -> {
			helper.assertTrue(SpiderWrap.isWrapped(z), "it is cocooned on the strand");
			helper.assertTrue(z.getAttachedOrElse(ModAttachments.SPIDER_WRAP_UNTIL, 0L) == 0L, "for good (the web holds it)");
			helper.assertTrue(Math.abs(z.getY() + z.getBbHeight() * 0.6 - anchor.y) < 0.3, "hanging with the strand through its chest");
			SpiderWrap.release(z, false);
			SpiderMoves.clear(p);
			helper.succeed();
		});
	}

	// ---------------- perfect dodge ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void jumpOutsideAWarningIsJustAJump(GameTestHelper helper) {
		ServerPlayer p = spider(helper, new Vec3(3.5, 1.0, 3.5));
		helper.assertFalse(SpiderPerfectDodge.arm(p), "no warning: nothing armed");
		SpiderPerfectDodge.warn(p, 6, null);
		helper.assertTrue(SpiderPerfectDodge.warned(p), "a warning opens");
		helper.assertTrue(SpiderPerfectDodge.arm(p), "and a Jump inside it arms the dodge");
		SpiderMoves.clear(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void perfectDodgeEvadesSlowsNearbyAndCountersForDouble(GameTestHelper helper) {
		ServerPlayer p = spider(helper, new Vec3(3.5, 1.0, 3.5));
		p.setNoGravity(false);
		Zombie attacker = zombie(helper, new Vec3(3.5, 1.0, 5.0));
		Zombie near = zombie(helper, new Vec3(1.5, 1.0, 2.0));
		Zombie far = zombie(helper, new Vec3(3.5, 1.0, 11.0));
		Zombie control = zombie(helper, new Vec3(6.5, 1.0, 3.5));
		for (Zombie z : new Zombie[] {attacker, near, far, control}) {
			z.setTarget(p); // aware of him: no ambush bonus muddying the numbers
		}
		// a freshly joined player is spawn-invulnerable for 3 s (vanilla): wait that out so the hit really reaches him
		helper.runAfterDelay(62, () -> dodgeAndCounter(helper, p, attacker, near, far, control));
	}

	private static void dodgeAndCounter(GameTestHelper helper, ServerPlayer p, Zombie attacker, Zombie near, Zombie far, Zombie control) {
		SpiderPerfectDodge.warn(p, 8, attacker.position());
		helper.assertTrue(SpiderPerfectDodge.arm(p), "Jump during the warning arms it");
		float hp = p.getHealth();
		boolean hurt = p.hurt(helper.getLevel().damageSources().mobAttack(attacker), 6.0f);
		helper.assertFalse(hurt, "the blow is fully evaded");
		helper.assertTrue(p.getHealth() == hp, "no damage at all");
		helper.assertTrue(attacker.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
				&& attacker.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() >= 4, "the attacker is heavily slowed");
		helper.assertTrue(near.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "so is an enemy within 4 blocks");
		helper.assertFalse(far.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "but not one 7+ blocks off");
		helper.assertTrue(SpiderPerfectDodge.counterActive(p), "his next hit is a counter");
		helper.assertFalse(SpiderMan.abilityReady(p, SpiderPerfectDodge.PERFECT_DODGE), "the dodge is on its 5 s cooldown");
		SpiderPerfectDodge.warn(p, 8, attacker.position());
		helper.assertFalse(SpiderPerfectDodge.arm(p), "so a second Jump arms nothing");

		float before = attacker.getHealth();
		AbilityHelpers.hurt(p, attacker, 4.0f);
		float counterLoss = before - attacker.getHealth();
		helper.assertFalse(SpiderPerfectDodge.counterActive(p), "the counter is spent");
		float before2 = control.getHealth();
		AbilityHelpers.hurt(p, control, 4.0f);
		float plainLoss = before2 - control.getHealth();
		helper.assertTrue(plainLoss > 0f && counterLoss >= plainLoss * 1.9f,
				"the counter did double (" + counterLoss + " vs " + plainLoss + ")");
		SpiderMoves.clear(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spiderSenseForeseesAMeleeBlowAndAnArrowOnCourse(GameTestHelper helper) {
		ServerPlayer p = spider(helper, new Vec3(3.5, 1.0, 3.5));
		helper.assertTrue(SpiderPerfectDodge.predict(p, helper.getLevel()) == null, "nothing coming: no warning");
		Zombie z = zombie(helper, new Vec3(3.5, 1.0, 4.6));
		z.setTarget(p);
		SpiderPerfectDodge.Threat melee = SpiderPerfectDodge.predict(p, helper.getLevel());
		helper.assertTrue(melee != null && melee.eta() <= SpiderPerfectDodge.WARN_LEAD, "a hunting zombie in reach, swing ready: warned");
		z.discard();

		Arrow arrow = new Arrow(helper.getLevel(), p.getX(), p.getY() + 1.2, p.getZ() + 9.0, new ItemStack(Items.ARROW), null);
		arrow.setDeltaMovement(0, 0.02, -1.8);
		helper.getLevel().addFreshEntity(arrow);
		SpiderPerfectDodge.Threat shot = SpiderPerfectDodge.predict(p, helper.getLevel());
		helper.assertTrue(shot != null && shot.eta() >= 3 && shot.eta() <= 7, "an arrow 9 blocks out at 1.8/tick: about 5 ticks ("
				+ (shot == null ? "none" : shot.eta()) + ")");
		arrow.setDeltaMovement(1.8, 0.0, 0.0);
		helper.assertTrue(SpiderPerfectDodge.predict(p, helper.getLevel()) == null, "one flying past is no threat");
		arrow.discard();
		SpiderMoves.clear(p);
		helper.succeed();
	}
}
