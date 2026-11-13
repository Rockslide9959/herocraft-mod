package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.flight.landing.HeroLanding;
import com.projecthero.mod.flight.landing.HeroLandingStyle;
import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.SaiyanConfig;
import com.projecthero.mod.saiyan.SaiyanForm;
import com.projecthero.mod.saiyan.SaiyanUltraInstinct;
import com.projecthero.mod.saiyan.data.SaiyanState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Saiyan extras: the hero landing (tester note), and the secret Ultra Instinct -- its unlock (low health against a
 * stronger foe, enough hits survived), entering it from Super Saiyan on H, the auto-dodge (melee and projectile chances,
 * the afterimage sidestep, the counter bonus, what can't be dodged) and its end (Ki, Super Saiyan going with it, exhaustion,
 * the lockout). Mock players are not ticked by the server, so tests over time drive {@link Saiyan#tick} once a game tick.
 */
public class SaiyanUltraInstinctGameTests implements FabricGameTest {
	private static ServerPlayer saiyan(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		try { // a fresh mock player is spawn-protected
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		helper.assertTrue(Saiyan.grant(p), "the grant works");
		return p;
	}

	private static Zombie bigZombie(GameTestHelper helper) {
		Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(5.5, 2.0, 5.5));
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		z.setHealth(200f);
		return z;
	}

	private static void hitBy(ServerPlayer p, Zombie z, float amount) {
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().mobAttack(z), amount);
	}

	/** Unlocked, Super Saiyan, full Ki, then H: Ultra Instinct. */
	private static void intoUltra(GameTestHelper helper, ServerPlayer p) {
		SaiyanUltraInstinct.unlock(p);
		Saiyan.setRage(p, SaiyanConfig.RAGE_MAX);
		helper.assertTrue(Saiyan.transform(p), "H: Super Saiyan");
		Saiyan.setKi(p, SaiyanConfig.KI_MAX);
		helper.assertTrue(Saiyan.transform(p), "H again: Ultra Instinct");
		helper.assertTrue(SaiyanUltraInstinct.active(p) && Saiyan.form(p) == SaiyanForm.ULTRA, "in Ultra Instinct");
	}

	// ---------------------------------------------------------------- the landing (tester note)

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aFastSaiyanFlightTouchdownIsASaiyanHeroLanding(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Vec3 fast = new Vec3(0.0, -0.9, 0.0);
		for (int i = 0; i < 4; i++) {
			helper.runAfterDelay(i, () -> {
				SaiyanState n = Saiyan.state(p).copy();
				n.flying = true;
				p.setAttached(ModAttachments.SAIYAN_STATE, n);
				p.setOnGround(false);
				p.setKnownMovement(fast);
				HeroLanding.tick(p);
			});
		}
		helper.runAfterDelay(4, () -> {
			p.setOnGround(true);
			HeroLanding.tick(p);
			HeroLanding.Active a = HeroLanding.active(p);
			helper.assertTrue(a != null && a.style() == HeroLandingStyle.SAIYAN, "a fast Saiyan touchdown is the Saiyan landing, was " + a);
			helper.assertTrue(HeroLandingStyle.byId(HeroLandingStyle.SAIYAN.id) == HeroLandingStyle.SAIYAN, "its id round-trips");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the secret unlock

	/**
	 * Held at 8 of 40 health (under a quarter) against a 200-health zombie (a stronger foe), hit every 20 ticks: unlocked once
	 * both 10 s low and 5 hits are reached -- not before. Each tick adds exactly one step to the low-health count.
	 */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 300)
	public void ultraInstinctUnlocksLowAgainstAStrongerFoe(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Zombie z = bigZombie(helper);
		helper.assertTrue(!SaiyanUltraInstinct.unlocked(p), "locked to start");
		int[] t = { 0 };
		int[] lastTicks = { -1 };
		helper.onEachTick(() -> {
			if (SaiyanUltraInstinct.unlocked(p)) {
				return;
			}
			p.setHealth(8f);
			if (t[0] % 20 == 0) {
				hitBy(p, z, 0.5f);
				p.setHealth(8f);
			}
			int before = SaiyanUltraInstinct.progress(p)[0];
			Saiyan.tick(p);
			int[] pr = SaiyanUltraInstinct.progress(p);
			if (!SaiyanUltraInstinct.unlocked(p) && t[0] > 0) {
				helper.assertTrue(pr[0] - before == 1, "one low tick a tick (step " + (pr[0] - before) + ")");
			}
			if (!SaiyanUltraInstinct.unlocked(p)) {
				helper.assertTrue(pr[0] < SaiyanUltraInstinct.UNLOCK_TICKS || pr[1] < SaiyanUltraInstinct.UNLOCK_HITS, "not before both are met");
				lastTicks[0] = pr[0];
			}
			t[0]++;
			if (SaiyanUltraInstinct.unlocked(p)) {
				helper.assertTrue(lastTicks[0] >= SaiyanUltraInstinct.UNLOCK_TICKS - 1, "unlocked after 10 s low, at " + lastTicks[0]);
				helper.assertTrue(Saiyan.strongFoe(p), "in a fight with a stronger foe");
				helper.succeed();
			}
		});
	}

	/** Healing above 40%, or no stronger foe, starts the count over; a weak foe never counts. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void theUnlockResetsWhenHealedOrTheFightEnds(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Zombie z = bigZombie(helper);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			if (t[0] < 20) {
				p.setHealth(8f);
				if (t[0] == 0) {
					hitBy(p, z, 0.5f);
					p.setHealth(8f);
				}
			} else if (t[0] == 20) {
				helper.assertTrue(SaiyanUltraInstinct.progress(p)[0] >= 19, "counting while low: " + SaiyanUltraInstinct.progress(p)[0]);
				p.setHealth(20f); // half health: above 40%
			} else if (t[0] == 22) {
				helper.assertTrue(SaiyanUltraInstinct.progress(p)[0] == 0, "healed above 40%: the count starts over");
				helper.succeed();
				return;
			}
			Saiyan.tick(p);
			t[0]++;
		});
	}

	// ---------------------------------------------------------------- the form and the auto-dodge

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void ultraInstinctOnlyFromSuperSaiyanAndOnlyOnceUnlocked(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Saiyan.setRage(p, SaiyanConfig.RAGE_MAX);
		helper.assertTrue(Saiyan.transform(p), "Super Saiyan");
		helper.assertTrue(!Saiyan.transform(p) && !SaiyanUltraInstinct.active(p), "locked: H as a Super Saiyan does nothing new");
		SaiyanUltraInstinct.unlock(p);
		Saiyan.setKi(p, 10f);
		helper.assertTrue(!Saiyan.transform(p), "needs 50 Ki");
		Saiyan.setKi(p, SaiyanConfig.KI_MAX);
		helper.assertTrue(Saiyan.transform(p) && SaiyanUltraInstinct.active(p), "unlocked, 50+ Ki: Ultra Instinct");
		helper.assertTrue(SaiyanUltraInstinct.ticksLeft(p) == SaiyanUltraInstinct.UI_TICKS, "30 s");
		helper.assertTrue(Math.abs(Saiyan.damageMult(p, false) - (1f + SaiyanConfig.SSJ_DAMAGE + SaiyanUltraInstinct.DAMAGE)) < 0.01f,
				"+25% on top of Super Saiyan");
		helper.assertTrue(Saiyan.kiRegenPerTick(p) == 0f, "no Ki regen in it");
		// (the server may tick him too: Saiyan.tick runs once a game tick whoever calls it, so measure one tick's step)
		float[] ki0 = { 0f };
		helper.runAfterDelay(1, () -> {
			Saiyan.tick(p);
			ki0[0] = Saiyan.ki(p);
		});
		helper.runAfterDelay(2, () -> {
			Saiyan.tick(p);
			float step = ki0[0] - Saiyan.ki(p);
			helper.assertTrue(Math.abs(step - SaiyanUltraInstinct.KI_DRAIN_PER_TICK) < 0.01f, "drains 3 Ki a second, step " + step);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void ultraInstinctAutoDodgesMeleeAndProjectiles(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		Zombie z = bigZombie(helper);
		intoUltra(helper, p);
		float hp = p.getHealth();
		helper.runAfterDelay(1, () -> {
			// a winning roll: the punch is sidestepped, he moves, an afterimage stays behind, the counter window opens
			SaiyanUltraInstinct.forceRoll(0f);
			Vec3 before = p.position();
			hitBy(p, z, 6f);
			helper.assertTrue(p.getHealth() == hp, "dodged: no damage (" + p.getHealth() + " of " + hp + ")");
			helper.assertTrue(p.position().distanceTo(before) > 0.5, "sidestepped " + p.position().distanceTo(before));
			helper.assertTrue(Math.abs(SaiyanUltraInstinct.damageBonus(p) - (SaiyanUltraInstinct.DAMAGE + SaiyanUltraInstinct.COUNTER_DAMAGE)) < 0.01f,
					"a counter right after: +50% more");
			// within the gap no second dodge, whatever the roll
			hitBy(p, z, 2f);
			helper.assertTrue(p.getHealth() < hp, "at most one dodge every 6 ticks");
		});
		helper.runAfterDelay(1 + SaiyanUltraInstinct.DODGE_GAP + 1, () -> {
			// 0.7: over the melee chance (60%), under the projectile chance (75%)
			SaiyanUltraInstinct.forceRoll(0.7f);
			float h0 = p.getHealth();
			Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
			arrow.setOwner(z);
			arrow.setPos(z.position().add(0, 1.4, 0));
			arrow.setDeltaMovement(p.position().subtract(z.position()).normalize().scale(2.0));
			helper.getLevel().addFreshEntity(arrow);
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().arrow(arrow, z), 4f);
			helper.assertTrue(p.getHealth() == h0, "an arrow at 0.7: dodged (75% chance)");
		});
		helper.runAfterDelay(1 + 2 * (SaiyanUltraInstinct.DODGE_GAP + 1), () -> {
			float h0 = p.getHealth();
			hitBy(p, z, 3f);
			helper.assertTrue(p.getHealth() < h0, "a punch at 0.7: lands (60% chance)");
			SaiyanUltraInstinct.forceRoll(0f);
		});
		helper.runAfterDelay(2 + 3 * (SaiyanUltraInstinct.DODGE_GAP + 1), () -> {
			float h0 = p.getHealth();
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().explosion(z, z), 3f);
			helper.assertTrue(p.getHealth() < h0, "an explosion can't be sidestepped");
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().onFire(), 1f);
			SaiyanUltraInstinct.forceRoll(null);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void ultraInstinctEndsAtNoKiTakingSuperSaiyanAndExhausting(GameTestHelper helper) {
		ServerPlayer p = saiyan(helper);
		intoUltra(helper, p);
		Saiyan.setKi(p, 0.05f);
		helper.runAfterDelay(1, () -> {
			Saiyan.tick(p);
			helper.assertTrue(!SaiyanUltraInstinct.active(p) && !Saiyan.ssj(p), "out of Ki: Ultra Instinct ends and Super Saiyan with it");
			helper.assertTrue(Saiyan.form(p) == SaiyanForm.BASE, "back to Base");
			helper.assertTrue(SaiyanUltraInstinct.lockoutTicksLeft(p) > SaiyanUltraInstinct.LOCKOUT_TICKS - 5, "a 3 minute lockout");
			helper.assertTrue(Saiyan.lockoutTicksLeft(p) > 0, "and the usual Super Saiyan lockout");
			helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && p.hasEffect(MobEffects.WEAKNESS), "exhausted");
			helper.succeed();
		});
	}
}
