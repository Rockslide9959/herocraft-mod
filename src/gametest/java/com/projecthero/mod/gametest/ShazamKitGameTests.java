package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.PowerRank;
import com.projecthero.mod.shazam.Shazam;
import com.projecthero.mod.shazam.ShazamAbilityManager;
import com.projecthero.mod.shazam.ShazamAnim;
import com.projecthero.mod.shazam.ShazamCombat;
import com.projecthero.mod.shazam.ShazamConfig;
import com.projecthero.mod.shazam.ShazamMod;
import com.projecthero.mod.shazam.ShazamMoves;
import com.projecthero.mod.shazam.data.ShazamState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Shazam, Planet tier ("A" tier, user): the new S-H-A-Z-A-M kit -- every number inside Planet and nothing over 40, the hold
 * moves charging / streaming / hopping / lifting and releasing through the real key path ({@link ShazamAbilityManager#handle}), the
 * ultimate and its chain buff, Wisdom's crit and its cap, the boss cap, and Shift / hold moves on their own cooldowns. Mock players
 * aren't ticked, so {@link Shazam#tick} is called by hand once a tick. Mob hits are asserted as ratios (zombies wear a little armour
 * and other tests can leave the world on Hard).
 */
public class ShazamKitGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		try { // a fresh mock player is spawn-protected
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** A champion, already transformed, with a full bar. */
	private static ServerPlayer shazam(GameTestHelper helper, double x, double z) {
		ServerPlayer p = mock(helper, x, z);
		helper.assertTrue(Shazam.grant(p), "the grant works");
		helper.assertTrue(ShazamMod.onChat(p, "Shazam!") == Shazam.Spoken.TRANSFORMED, "the word transforms him");
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<ShazamState> change) {
		ShazamState n = Shazam.state(p).copy();
		change.accept(n);
		p.setAttached(ModAttachments.SHAZAM_STATE, n);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z, double maxHealth) {
		Zombie zb = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(x, 2.0, z));
		zb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		zb.setHealth((float) maxHealth);
		return zb;
	}

	private static void look(ServerPlayer p, LivingEntity at) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, at.position().add(0, at.getBbHeight() * 0.55, 0));
	}

	private static float lost(float before, LivingEntity e) {
		return before - e.getHealth();
	}

	private static boolean near(float got, float expected) {
		return got > expected * 0.85f && got < expected * 1.15f;
	}

	/** Ticks {@code p} by hand from delay {@code from} to {@code to} (inclusive). */
	private static void tickFor(GameTestHelper helper, ServerPlayer p, int from, int to) {
		tickFor(helper, p, from, to, null);
	}

	/**
	 * The same, keeping his aim on {@code target} (the spawned mobs fall onto the floor while the un-ticked mock player stays put, so
	 * a look set once drifts over their heads; a real player keeps aiming).
	 */
	private static void tickFor(GameTestHelper helper, ServerPlayer p, int from, int to, LivingEntity target) {
		for (int i = from; i <= to; i++) {
			helper.runAfterDelay(i, () -> {
				if (target != null && target.isAlive()) {
					look(p, target);
				}
				Shazam.tick(p);
			});
		}
	}

	// ---------------------------------------------------------------- keys and numbers

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void keysMapToTheShazamKit(GameTestHelper helper) {
		String[][] spec = { // slot: plain, shift, hold
				{ ShazamAbilityManager.HERCULES_COMBO, ShazamAbilityManager.UPPERCUT, ShazamAbilityManager.HAYMAKER },
				{ ShazamAbilityManager.ZEUS_LIGHTNING, ShazamAbilityManager.THUNDERHEAD_SLAM, ShazamAbilityManager.ZEUS_WRATH },
				{ ShazamAbilityManager.MERCURY_DASH, ShazamAbilityManager.SONIC_LEAP, null },
				{ ShazamAbilityManager.ATLAS_QUAKE, ShazamAbilityManager.THUNDERSTRIKE, ShazamAbilityManager.TITANS_GRIP },
				{ ShazamAbilityManager.WISDOM, ShazamAbilityManager.COURAGE, ShazamAbilityManager.SHARE_POWER },
				{ ShazamAbilityManager.MERCURY_BLITZ, ShazamAbilityManager.THUNDERCLAP, ShazamAbilityManager.MERCURY_RUSH } };
		AbilitySlot[] slots = { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5, AbilitySlot.SLOT_6 };
		for (int i = 0; i < slots.length; i++) {
			helper.assertTrue(spec[i][0].equals(ShazamAbilityManager.moveFor(slots[i], false)), "plain " + slots[i]);
			helper.assertTrue(spec[i][1].equals(ShazamAbilityManager.moveFor(slots[i], true)), "shift " + slots[i]);
			helper.assertTrue(java.util.Objects.equals(spec[i][2], ShazamAbilityManager.holdFor(slots[i])), "hold " + slots[i]);
		}
		for (AbilitySlot s : AbilitySlot.values()) {
			if (s.ordinal() >= AbilitySlot.CORE_COUNT) {
				helper.assertTrue(ShazamAbilityManager.moveFor(s, false) == null && ShazamAbilityManager.moveFor(s, true) == null
						&& ShazamAbilityManager.holdFor(s) == null, "H / N are never moves");
			}
		}
		helper.assertTrue("Hold R".equals(ShazamAbilityManager.keyOf(ShazamAbilityManager.HAYMAKER)), "key labels");
		helper.assertTrue("Shift+Z".equals(ShazamAbilityManager.keyOf(ShazamAbilityManager.THUNDERSTRIKE)), "key labels");
		helper.assertTrue(PowerRank.ofGuideKey("projecthero.guide.shazam") == PowerRank.PLANET, "placed as Planet (the badge)");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyNumberSitsInPlanet(GameTestHelper helper) {
		PowerRank r = PowerRank.PLANET;
		helper.assertTrue(ShazamConfig.MAX_HIT == 40f && ShazamConfig.MAX_HIT == r.ultimateMax, "MAX_HIT 40");
		float fist = 1f + (float) ShazamConfig.MELEE_BONUS;
		for (float m : new float[] { fist, ShazamConfig.COMBO_DAMAGE_1, ShazamConfig.COMBO_DAMAGE_2, ShazamConfig.COMBO_DAMAGE_3 }) {
			helper.assertTrue(m >= r.meleeMin && m <= r.meleeMax, "melee in 16-19: " + m);
		}
		for (float a : new float[] { ShazamConfig.UPPERCUT_DAMAGE, ShazamConfig.HAYMAKER_MIN, ShazamConfig.HAYMAKER_MAX, ShazamConfig.HAYMAKER_SHOCK_DAMAGE,
				ShazamConfig.ZEUS_DAMAGE, ShazamConfig.ZEUS_CHAIN_DAMAGE, ShazamConfig.SLAM_DAMAGE, ShazamConfig.WRATH_DAMAGE, ShazamConfig.BLITZ_DAMAGE,
				ShazamConfig.CLAP_DAMAGE, ShazamConfig.RUSH_DAMAGE, ShazamConfig.QUAKE_DAMAGE, ShazamConfig.GRIP_DAMAGE, ShazamConfig.ULT_CHAIN_DAMAGE }) {
			helper.assertTrue(a >= r.abilityMin && a <= r.abilityMax, "ability in 16-20: " + a);
		}
		helper.assertTrue(ShazamConfig.ULT_DAMAGE >= r.ultimateMin && ShazamConfig.ULT_DAMAGE <= r.ultimateMax, "ultimate in 32-40");
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 17.0) < 1e-3, "a 17 fist");
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) >= 20.0 && p.getAttributeValue(Attributes.ARMOR_TOUGHNESS) >= 12.0,
				"netherite-level toughness");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void noHitOfHisGoesOverForty(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		Zombie z = zombie(helper, 4.5, 4.5, 250);
		helper.assertTrue(ShazamCombat.shape(p, z, 100f) == 40f, "shaped to 40");
		float h = z.getHealth();
		z.hurt(p.damageSources().playerAttack(p), 100f); // a huge punch (say, a modded weapon) still lands at 40
		helper.assertTrue(near(lost(h, z), 40f), "a 100 punch lands as 40, took " + lost(h, z));
		h = z.getHealth();
		z.invulnerableTime = 0;
		Shazam.hit(p, z, 75f, true);
		helper.assertTrue(near(lost(h, z), 40f), "a 75 lightning hit lands as 40, took " + lost(h, z));
		helper.succeed();
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void herculesComboIsThreePunchesAndTheThirdKnocksBack(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 2.0);
		Zombie z = zombie(helper, 4.5, 4.5, 200);
		look(p, z);
		float[] expect = { ShazamConfig.COMBO_DAMAGE_1, ShazamConfig.COMBO_DAMAGE_2, ShazamConfig.COMBO_DAMAGE_3 };
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(ShazamMoves.comboStep(p) == i, "step " + i);
			float h = z.getHealth();
			z.invulnerableTime = 0;
			z.setDeltaMovement(Vec3.ZERO);
			Shazam.clearCooldowns(p);
			helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.HERCULES_COMBO), "punch " + (i + 1));
			helper.assertTrue(near(lost(h, z), expect[i]), "punch " + (i + 1) + " " + expect[i] + ", took " + lost(h, z));
			helper.assertTrue(Shazam.state(p).anim == ShazamAnim.COMBO_1 + i, "its clip");
		}
		Vec3 v = z.getDeltaMovement();
		helper.assertTrue(Math.sqrt(v.x * v.x + v.z * v.z) > 1.0 && v.z > 0, "the finisher knocks it away, v " + v);
		helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.HERCULES_COMBO) == ShazamConfig.COMBO_FINISH_COOLDOWN, "the finisher's pause");
		helper.assertTrue(Shazam.lightning(p) == ShazamConfig.LIGHTNING_MAX, "the combo is free");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void uppercutLaunchesAndShiftKeepsItsOwnCooldown(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 2.0);
		Zombie z = zombie(helper, 4.5, 4.5, 200);
		look(p, z);
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.HERCULES_COMBO), "a jab first");
		float h = z.getHealth();
		z.invulnerableTime = 0;
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.UPPERCUT), "Shift+R while R cools down");
		helper.assertTrue(near(lost(h, z), ShazamConfig.UPPERCUT_DAMAGE), "18, took " + lost(h, z));
		helper.assertTrue(z.getDeltaMovement().y > 1.0, "launched up");
		helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.UPPERCUT) == ShazamConfig.UPPERCUT_COOLDOWN, "4 s");
		helper.assertTrue(Math.abs(Shazam.lightning(p) - (100f - ShazamConfig.UPPERCUT_COST)) < 0.01f, "10 lightning");
		helper.succeed();
	}

	/** Hold R: the press waits, the hold starts at 8 ticks, the charge builds for 1.5 s, the release hits 20 with a 16 shockwave. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void haymakerChargesWhileHeldAndReleasesWithAShockwave(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 2.0);
		Zombie a = zombie(helper, 4.5, 4.5, 200);
		Zombie b = zombie(helper, 5.8, 5.2, 200);
		look(p, a);
		float[] h = new float[2];
		helper.runAfterDelay(1, () -> {
			ShazamAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
			helper.assertTrue(ShazamMoves.holding(p) == ShazamAnim.HOLD_NONE, "a press alone does nothing yet");
		});
		tickFor(helper, p, 2, 50, a);
		helper.runAfterDelay(1 + ShazamConfig.HOLD_TICKS + 1, () -> {
			helper.assertTrue(ShazamMoves.holding(p) == ShazamAnim.HOLD_HAYMAKER, "held 8 ticks: the Haymaker charges");
			helper.assertTrue(ShazamMoves.haymakerCharge(p) < 0.2f, "barely charged yet: " + ShazamMoves.haymakerCharge(p));
		});
		helper.runAfterDelay(1 + ShazamConfig.HOLD_TICKS + 16, () -> {
			float c = ShazamMoves.haymakerCharge(p);
			helper.assertTrue(c > 0.4f && c < 0.7f, "half charged after ~0.75 s: " + c);
		});
		helper.runAfterDelay(1 + ShazamConfig.HOLD_TICKS + ShazamConfig.HAYMAKER_CHARGE_TICKS + 2, () -> {
			helper.assertTrue(ShazamMoves.haymakerCharge(p) == 1f, "full after 1.5 s");
			h[0] = a.getHealth();
			h[1] = b.getHealth();
			a.invulnerableTime = 0;
			b.invulnerableTime = 0;
			ShazamAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
			helper.assertTrue(near(lost(h[0], a), ShazamConfig.HAYMAKER_MAX), "the full Haymaker hits 20, took " + lost(h[0], a) + " p " + p.position() + " rot " + p.getYRot() + "/" + p.getXRot() + " a " + a.position() + " b " + lost(h[1], b));
			helper.assertTrue(near(lost(h[1], b), ShazamConfig.HAYMAKER_SHOCK_DAMAGE), "the shockwave 16, took " + lost(h[1], b));
			helper.assertTrue(ShazamMoves.holding(p) == ShazamAnim.HOLD_NONE && Shazam.state(p).anim == ShazamAnim.HAYMAKER, "released");
			helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.HAYMAKER) == ShazamConfig.HAYMAKER_COOLDOWN, "its own cooldown");
			helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.HERCULES_COMBO) == 0, "R's tap is still ready");
			helper.assertTrue(ShazamMoves.haymakerDamage(0f) == 16f && ShazamMoves.haymakerDamage(0.5f) == 18f, "16 -> 20");
		});
		helper.runAfterDelay(55, helper::succeed);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40)
	public void aQuickTapIsTheComboNotTheHold(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 2.0);
		Zombie a = zombie(helper, 4.5, 4.5, 200);
		look(p, a);
		helper.runAfterDelay(1, () -> ShazamAbilityManager.handle(p, AbilitySlot.SLOT_1, true));
		tickFor(helper, p, 2, 4);
		helper.runAfterDelay(4, () -> {
			float h = a.getHealth();
			ShazamAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
			helper.assertTrue(near(lost(h, a), ShazamConfig.COMBO_DAMAGE_1), "the tap jabbed on the release, took " + lost(h, a));
			helper.assertTrue(ShazamMoves.holding(p) == ShazamAnim.HOLD_NONE, "no Haymaker");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void lightningOfZeusStrikesAndChainsToTwoMore(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		Zombie a = zombie(helper, 1.5, 5.5, 200);
		Zombie b = zombie(helper, 3.5, 5.5, 200);
		Zombie c = zombie(helper, 5.5, 6.5, 200);
		Zombie d = zombie(helper, 6.5, 1.0, 200);
		look(p, a);
		float ha = a.getHealth(), hb = b.getHealth(), hc = c.getHealth(), hd = d.getHealth();
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.ZEUS_LIGHTNING), "it fires");
		helper.assertTrue(near(lost(ha, a), 18f), "18 to the target, took " + lost(ha, a));
		helper.assertTrue(near(lost(hb, b), 16f) && near(lost(hc, c), 16f), "16 to the 2 chained");
		helper.assertTrue(lost(hd, d) < 15f, "the chain stops at 2 more");
		helper.assertTrue(Math.abs(Shazam.lightning(p) - (100f - ShazamConfig.ZEUS_COST)) < 0.01f, "20 lightning");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40)
	public void thunderheadSlamLandsAfterItsWindup(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 4.5);
		Zombie z = zombie(helper, 4.5, 7.5, 200);
		float[] h = new float[1];
		helper.runAfterDelay(1, () -> {
			p.setOnGround(true);
			h[0] = z.getHealth();
			helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.THUNDERHEAD_SLAM), "the slam starts");
			helper.assertTrue(z.getHealth() == h[0], "nothing yet: the fists go up first");
		});
		tickFor(helper, p, 2, 2 + ShazamConfig.SLAM_WINDUP);
		helper.runAfterDelay(3 + ShazamConfig.SLAM_WINDUP, () -> {
			helper.assertTrue(near(lost(h[0], z), ShazamConfig.SLAM_DAMAGE), "18 within 5, took " + lost(h[0], z));
			helper.succeed();
		});
	}

	/** Hold G: the stream reports to the beam clash and pulses 16 a quarter second for 4 lightning; the release ends it. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void zeusWrathStreamsWhileHeldAndStopsOnRelease(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 1.5);
		Zombie z = zombie(helper, 4.5, 7.0, 400);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(250); // not a boss (300+)
		z.setHealth(250f);
		look(p, z);
		float[] h = new float[1];
		helper.runAfterDelay(1, () -> ShazamAbilityManager.handle(p, AbilitySlot.SLOT_2, true));
		tickFor(helper, p, 2, 30, z);
		helper.runAfterDelay(1 + ShazamConfig.HOLD_TICKS + 1, () -> {
			helper.assertTrue(ShazamMoves.holding(p) == ShazamAnim.HOLD_WRATH, "held: the stream is on");
			h[0] = z.getHealth();
			float bar = Shazam.lightning(p);
			z.invulnerableTime = 0;
			helper.assertTrue(ShazamMoves.wrathPulse(p), "a pulse");
			helper.assertTrue(near(lost(h[0], z), ShazamConfig.WRATH_DAMAGE), "16 a pulse, took " + lost(h[0], z) + " p " + p.position() + " rot " + p.getYRot() + "/" + p.getXRot() + " z " + z.position());
			helper.assertTrue(Math.abs(bar - Shazam.lightning(p) - ShazamConfig.WRATH_COST) < 0.3f, "4 lightning a pulse");
			h[0] = z.getHealth();
		});
		helper.runAfterDelay(1 + ShazamConfig.HOLD_TICKS + 1 + 3 * ShazamConfig.WRATH_EVERY, () -> {
			helper.assertTrue(lost(h[0], z) >= 2 * ShazamConfig.WRATH_DAMAGE * 0.85f, "it kept pulsing while held, took " + lost(h[0], z));
			ShazamAbilityManager.handle(p, AbilitySlot.SLOT_2, false);
			helper.assertTrue(ShazamMoves.holding(p) == ShazamAnim.HOLD_NONE, "let go: it stops");
			helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.ZEUS_WRATH) == ShazamConfig.WRATH_COOLDOWN, "its own cooldown");
			helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.ZEUS_LIGHTNING) == 0, "G's tap is still ready");
			h[0] = z.getHealth();
		});
		helper.runAfterDelay(36, () -> {
			helper.assertTrue(z.getHealth() == h[0], "no more pulses after the release");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void mercuryBlitzHitsThreeInALine(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 0.8);
		p.setYRot(0f);
		p.setXRot(0f);
		Zombie[] zs = { zombie(helper, 1.5, 2.5, 200), zombie(helper, 1.5, 4.0, 200), zombie(helper, 1.5, 5.5, 200), zombie(helper, 1.5, 6.8, 200) };
		float[] h = new float[4];
		for (int i = 0; i < 4; i++) {
			h[i] = zs[i].getHealth();
		}
		p.setYRot(0f);
		p.setXRot(0f);
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.MERCURY_BLITZ), "the blitz");
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(near(lost(h[i], zs[i]), ShazamConfig.BLITZ_DAMAGE), "16 to number " + (i + 1) + ", took " + lost(h[i], zs[i]));
		}
		helper.assertTrue(zs[3].getHealth() == h[3], "up to 3");
		helper.assertTrue(p.getZ() > helper.absoluteVec(new Vec3(1.5, 2, 5.0)).z, "he went through them");
		helper.succeed();
	}

	/** Hold C: hops between the enemies round him, 5 hits of 16 at most, then its own cooldown. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void mercuryRushHopsForFiveHits(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 4.5);
		Zombie a = zombie(helper, 2.0, 2.0, 200);
		Zombie b = zombie(helper, 6.5, 6.5, 200);
		float total = a.getHealth() + b.getHealth();
		helper.runAfterDelay(1, () -> ShazamAbilityManager.handle(p, AbilitySlot.SLOT_6, true));
		tickFor(helper, p, 2, 50);
		helper.runAfterDelay(1 + ShazamConfig.HOLD_TICKS + 1, () -> helper.assertTrue(ShazamMoves.holding(p) == ShazamAnim.HOLD_RUSH
				|| ShazamMoves.rushHits(p) > 0, "held: the rush starts"));
		helper.runAfterDelay(50, () -> {
			helper.assertTrue(ShazamMoves.rushHits(p) == ShazamConfig.RUSH_MAX_HITS, "5 hits, got " + ShazamMoves.rushHits(p));
			float took = total - a.getHealth() - b.getHealth();
			helper.assertTrue(near(took, ShazamConfig.RUSH_MAX_HITS * ShazamConfig.RUSH_DAMAGE), "5 x 16 shared out, took " + took);
			helper.assertTrue(a.getHealth() < 200f && b.getHealth() < 200f, "both were visited");
			helper.assertTrue(ShazamMoves.holding(p) == ShazamAnim.HOLD_NONE, "over by itself after 5");
			helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.MERCURY_RUSH) > 0, "its own cooldown");
			helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.MERCURY_BLITZ) == 0, "C's tap is still ready");
			ShazamAbilityManager.handle(p, AbilitySlot.SLOT_6, false); // the late release does nothing
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40)
	public void atlasQuakeStompsAfterItsWindup(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 4.5);
		Zombie z = zombie(helper, 4.5, 7.0, 200);
		float[] h = new float[1];
		helper.runAfterDelay(1, () -> {
			h[0] = z.getHealth();
			helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.ATLAS_QUAKE), "the knee comes up");
		});
		tickFor(helper, p, 2, 2 + ShazamConfig.QUAKE_WINDUP);
		helper.runAfterDelay(3 + ShazamConfig.QUAKE_WINDUP, () -> {
			helper.assertTrue(near(lost(h[0], z), ShazamConfig.QUAKE_DAMAGE), "19 within 5, took " + lost(h[0], z));
			helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "staggered");
			helper.succeed();
		});
	}

	/** Hold Z: grabbed and held overhead while the key is down, hurled on the release, 20 when it lands. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void titansGripHoldsOverheadAndHurlsOnRelease(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 2.0);
		Zombie z = zombie(helper, 4.5, 4.5, 200);
		look(p, z);
		helper.runAfterDelay(1, () -> ShazamAbilityManager.handle(p, AbilitySlot.SLOT_4, true));
		tickFor(helper, p, 2, 25, z);
		helper.runAfterDelay(1 + ShazamConfig.HOLD_TICKS + 1, () -> {
			helper.assertTrue(ShazamMoves.holding(p) == ShazamAnim.HOLD_GRIP && ShazamMoves.lifted(p) == z, "grabbed: hold " + ShazamMoves.holding(p) + " p " + p.position() + " rot " + p.getYRot() + "/" + p.getXRot() + " z " + z.position() + " cd " + Shazam.cooldownRemaining(p, ShazamAbilityManager.TITANS_GRIP) + " bar " + Shazam.lightning(p));
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(ShazamMoves.lifted(p) == z && z.getY() > p.getY() + 1.5, "still held overhead while the key is down");
			ShazamAbilityManager.handle(p, AbilitySlot.SLOT_4, false);
			helper.assertTrue(ShazamMoves.lifted(p) == null && ShazamMoves.inFlight(p, z), "let go: hurled");
			helper.assertTrue(z.getDeltaMovement().length() > 1.5, "hard");
			float h = z.getHealth();
			z.invulnerableTime = 0;
			helper.assertTrue(ShazamMoves.impact(p, z), "it lands");
			helper.assertTrue(near(lost(h, z), ShazamConfig.GRIP_DAMAGE), "20, took " + lost(h, z));
			helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.TITANS_GRIP) == ShazamConfig.GRIP_COOLDOWN, "its own cooldown");
			helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.ATLAS_QUAKE) == 0, "Z's tap is still ready");
			helper.succeed();
		});
	}

	/** Shift+Z: he shouts, rises, the colossal bolt hits him, he releases it -- 40 within 8 -- then 8 s of chain lightning on every hit. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void thunderstrikeFiresAndEveryHitChainsAfter(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		Zombie a = zombie(helper, 1.5, 6.0, 200);
		float[] h = new float[1];
		helper.runAfterDelay(1, () -> {
			Shazam.setLightning(p, 99f);
			helper.assertFalse(ShazamMoves.use(p, ShazamAbilityManager.THUNDERSTRIKE), "needs a full bar");
			Shazam.setLightning(p, 100f);
			h[0] = a.getHealth();
			helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.THUNDERSTRIKE), "SHAZAM!");
			helper.assertTrue(Shazam.eternityAge(p) == 0 && Shazam.state(p).anim == ShazamAnim.ULT, "rising, its clip");
			helper.assertTrue(Shazam.lightning(p) == 0f, "the whole bar");
			helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.THUNDERSTRIKE) == ShazamConfig.ULT_COOLDOWN, "90 s");
			helper.assertFalse(ShazamMoves.use(p, ShazamAbilityManager.HERCULES_COMBO), "no other move while it runs");
		});
		tickFor(helper, p, 2, 2 + ShazamConfig.ULT_RELEASE_AT);
		helper.runAfterDelay(4 + ShazamConfig.ULT_RELEASE_AT, () -> {
			helper.assertTrue(Shazam.eternityAge(p) < 0, "released");
			helper.assertTrue(near(lost(h[0], a), ShazamConfig.ULT_DAMAGE), "40 within 8, took " + lost(h[0], a));
			helper.assertTrue(p.getHealth() == p.getMaxHealth(), "the bolt never hurts him");
			helper.assertTrue(ShazamCombat.thunderCharged(p), "thunder-charged");
			helper.assertTrue(Shazam.state(p).thunderUntil - helper.getLevel().getGameTime() <= ShazamConfig.ULT_CHARGED_TICKS
					&& Shazam.state(p).thunderUntil - helper.getLevel().getGameTime() > ShazamConfig.ULT_CHARGED_TICKS - 5, "for 8 s");
			a.discard();
			Zombie b = zombie(helper, 5.0, 5.0, 200);
			Zombie c = zombie(helper, 6.5, 6.5, 200);
			float hb = b.getHealth(), hc = c.getHealth();
			Shazam.hit(p, b, 17f, false);
			helper.assertTrue(near(lost(hb, b), 17f), "the hit lands");
			helper.assertTrue(near(lost(hc, c), ShazamConfig.ULT_CHAIN_DAMAGE), "and chains 16 to the nearest other enemy, took " + lost(hc, c));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theFamilyHasNoUltimate(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		edit(p, s -> {
			s.hasPower = false;
			s.familyUntil = helper.getLevel().getGameTime() + 1000;
		});
		helper.assertTrue(Shazam.isFamily(p), "family");
		helper.assertFalse(ShazamMoves.use(p, ShazamAbilityManager.THUNDERSTRIKE), "no Thunderstrike");
		helper.assertFalse(ShazamAbilityManager.allowed(p, ShazamAbilityManager.THUNDERSTRIKE), "greyed");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void wisdomMarksEnemiesForCappedCrits(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		Zombie z = zombie(helper, 5.5, 5.5, 250);
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.WISDOM), "Wisdom");
		helper.assertTrue(Shazam.wise(p) && Shazam.state(p).wisdomUntil - helper.getLevel().getGameTime() == ShazamConfig.WISDOM_TICKS, "10 s");
		helper.assertTrue(ShazamCombat.marked(p, z) && z.hasEffect(MobEffects.GLOWING), "the enemy glows");
		float h = z.getHealth();
		Shazam.hit(p, z, 16f, true);
		helper.assertTrue(near(lost(h, z), 16f * ShazamConfig.WISDOM_CRIT), "16 crits to 20, took " + lost(h, z));
		helper.assertTrue(ShazamCombat.shape(p, z, 40f) == 40f, "the crit never lifts a hit past 40");
		h = z.getHealth();
		z.invulnerableTime = 0;
		Shazam.hit(p, z, 36f, true);
		helper.assertTrue(near(lost(h, z), 40f), "36 x 1.25 = 45 lands as 40, took " + lost(h, z));
		ServerPlayer other = mock(helper, 6.5, 1.5);
		helper.assertFalse(ShazamCombat.marked(other, z), "marks are his alone");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 20)
	public void courageOfAchillesIsResistanceTwoAndNoKnockback(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 1.5, 1.5);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - ShazamConfig.KNOCKBACK_RESIST) < 1e-3, "40% normally");
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.COURAGE), "Courage");
		var res = p.getEffect(MobEffects.DAMAGE_RESISTANCE);
		helper.assertTrue(res != null && res.getAmplifier() == 1 && res.getDuration() == ShazamConfig.COURAGE_TICKS, "Resistance II for 8 s");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "no knockback");
		helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.COURAGE) == ShazamConfig.COURAGE_COOLDOWN, "30 s");
		helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.WISDOM) == 0, "V's tap is still ready");
		edit(p, s -> s.standUntil = helper.getLevel().getGameTime());
		helper.runAfterDelay(1, () -> {
			Shazam.tick(p);
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - ShazamConfig.KNOCKBACK_RESIST) < 1e-3, "back to 40% after");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- X

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40)
	public void sonicLeapTurnsIntoFlight(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 4.5);
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.SONIC_LEAP), "the leap");
			helper.assertTrue(Math.abs(p.getDeltaMovement().y - ShazamConfig.LEAP_UP) < 1e-3, "a huge leap up");
			helper.assertTrue(ShazamMoves.fallSafe(p), "no fall damage");
		});
		tickFor(helper, p, 2, 1 + ShazamConfig.LEAP_TO_FLIGHT);
		helper.runAfterDelay(4 + ShazamConfig.LEAP_TO_FLIGHT, () -> {
			helper.assertTrue(Shazam.isFlying(p), "flying at the top");
			helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.MERCURY_DASH), "X while flying: Mercury Burst");
			helper.assertTrue(ShazamMoves.boosting(p), "boosting");
			helper.assertTrue(Shazam.cooldownRemaining(p, ShazamAbilityManager.MERCURY_DASH) == ShazamConfig.BURST_COOLDOWN, "3 s");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- bosses

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void bossesKeepTheirCapAndAreNeverLifted(GameTestHelper helper) {
		ServerPlayer p = shazam(helper, 4.5, 2.0);
		Zombie boss = zombie(helper, 4.5, 4.5, 300); // a "boss": 6% cap = 18 per hit
		helper.assertTrue(ShazamCombat.isBoss(boss), "counts as a boss");
		look(p, boss);
		float h = boss.getHealth();
		Shazam.hit(p, boss, ShazamConfig.ULT_DAMAGE, true);
		helper.assertTrue(lost(h, boss) <= 300f * ShazamConfig.BOSS_MAX_FRACTION_PER_HIT + 0.05f, "40 capped at 18, took " + lost(h, boss));
		h = boss.getHealth();
		boss.invulnerableTime = 0;
		boss.setDeltaMovement(Vec3.ZERO);
		helper.assertTrue(ShazamMoves.use(p, ShazamAbilityManager.UPPERCUT), "the uppercut lands");
		helper.assertTrue(lost(h, boss) <= 18.05f, "capped");
		helper.assertTrue(boss.getDeltaMovement().y < 0.3, "but a boss isn't launched, vy " + boss.getDeltaMovement().y);
		Shazam.clearCooldowns(p);
		helper.assertTrue(ShazamMoves.holdStart(p, ShazamAbilityManager.TITANS_GRIP, helper.getLevel().getGameTime()), "Titan's Grip on a boss");
		helper.assertTrue(ShazamMoves.lifted(p) == null && ShazamMoves.holding(p) == ShazamAnim.HOLD_NONE, "it takes the hit but isn't lifted");
		helper.succeed();
	}
}
