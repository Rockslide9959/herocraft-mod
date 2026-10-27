package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.UniquePowers;
import com.projecthero.mod.quirk.PlusUltra;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkAbilityManager;
import com.projecthero.mod.quirk.QuirkCombos;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.hellflame.HellflameConfig;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.erasure.ErasureConfig;
import com.projecthero.mod.quirk.erasure.ErasureMeter;
import com.projecthero.mod.quirk.explosion.ExplosionConfig;
import com.projecthero.mod.quirk.explosion.ExplosionMeter;
import com.projecthero.mod.quirk.meter.QuirkBand;
import com.projecthero.mod.quirk.meter.QuirkMeterSpec;
import com.projecthero.mod.quirk.meter.QuirkMeters;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Quirk framework ({@code docs/QUIRK_FRAMEWORK_0171.md}): Plus Ultra (fill from real hits, the once-a-long-fight
 * pacing, the 30 s decay, the hold-C ultimate at 100 with the tap still reaching the kit), the three-band meters and the push
 * past 100, the user's meter-pacing rule (a scripted heavy-use loop must not reach any Quirk's danger zone inside 45 s of game
 * time, and resting must bring it back to safe in at most 15 s), and combo-route detection. Mock players are not ticked by the
 * server: tests drive {@code Quirks.tick} themselves.
 */
public class QuirkFrameworkGameTests implements FabricGameTest {
	// ---------------------------------------------------------------- helpers

	@SuppressWarnings("removal")
	private static ServerPlayer holder(GameTestHelper helper, Quirk q, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		helper.assertTrue(UniquePowers.forced(() -> Quirks.grant(p, q)), q.id + " is granted");
		Quirks.edit(p, n -> {
			n.animId = QuirkState.ANIM_NONE;
			n.abilityReadyAt.clear();
		});
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Quirks.tick(p);
		});
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400.0);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		m.setHealth(400f);
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	// ---------------------------------------------------------------- Plus Ultra

	@GameTest(template = EMPTY_STRUCTURE)
	public void plusUltraIsPacedForOncePerLongFight(GameTestHelper helper) {
		// the reference "solid fight": an 8-damage hit landed every second while taking 1 HP a second
		float s = PlusUltra.secondsToFill(8f, 20, 1f);
		helper.assertTrue(s >= 60f && s <= 90f, "60-90 s of solid fighting fills it, got " + s);
		// ten mobs hit at once by one blast count once
		helper.assertTrue(PlusUltra.secondsToFill(8f, 1, 0f) >= PlusUltra.secondsToFill(8f, PlusUltra.HIT_INTERVAL, 0f) - 0.01f,
				"hits are credited at most once every " + PlusUltra.HIT_INTERVAL + " ticks");
		helper.assertTrue(PlusUltra.secondsToFill(40f, 20, 0f) >= 50f, "even huge hits every second take 50 s+, got " + PlusUltra.secondsToFill(40f, 20, 0f));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void plusUltraFillsFromRealHitsAndDamageTaken(GameTestHelper helper) {
		ServerPlayer p = holder(helper, Quirk.ERASURE, 1.5, 1.5);
		Zombie z = zombie(helper, 1.5, 3.5);
		helper.assertTrue(PlusUltra.gauge(p) == 0f, "starts empty");
		z.hurt(p.damageSources().playerAttack(p), 8f);
		float one = Math.min(PlusUltra.HIT_MAX, PlusUltra.HIT_BASE + PlusUltra.HIT_PER_HP * 8f);
		helper.assertTrue(Math.abs(PlusUltra.gauge(p) - one) < 0.01f, "a landed hit: " + PlusUltra.gauge(p));
		z.invulnerableTime = 0;
		z.hurt(p.damageSources().playerAttack(p), 8f);
		helper.assertTrue(Math.abs(PlusUltra.gauge(p) - one) < 0.01f, "a second hit the same tick is not credited again");
		helper.runAfterDelay(PlusUltra.HIT_INTERVAL + 1, () -> {
			z.invulnerableTime = 0;
			z.hurt(p.damageSources().playerAttack(p), 8f);
			helper.assertTrue(Math.abs(PlusUltra.gauge(p) - 2 * one) < 0.01f, "the next hit counts: " + PlusUltra.gauge(p));
			float before = PlusUltra.gauge(p);
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().mobAttack(z), 4f);
			float gained = PlusUltra.gauge(p) - before;
			helper.assertTrue(gained > 0f && gained <= 4f * PlusUltra.TAKEN_PER_HP + 0.01f, "damage taken fills it a little: " + gained);
			helper.assertTrue(Quirks.state(p).val(PlusUltra.V_GAUGE) > 0L, "synced to the HUD");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void plusUltraDrainsSlowlyOnlyOutOfCombat(GameTestHelper helper) {
		ServerPlayer p = holder(helper, Quirk.ZERO_GRAVITY, 1.5, 1.5);
		PlusUltra.set(p, 50f);
		PlusUltra.lastCombatForTest(p, 10);
		pump(helper, p);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(PlusUltra.gauge(p) == 50f, "in a fight: no drain, " + PlusUltra.gauge(p));
			PlusUltra.lastCombatForTest(p, PlusUltra.DECAY_DELAY + 1);
		});
		helper.runAfterDelay(60, () -> {
			float lost = 50f - PlusUltra.gauge(p);
			float want = PlusUltra.DECAY_PER_SECOND * 2f;
			helper.assertTrue(Math.abs(lost - want) < 0.3f, "30 s out of combat it drains slowly: lost " + lost + " in 2 s");
			helper.succeed();
		});
	}

	/**
	 * Through the real key path with the real Hellflame kit (C = Fire Wall, Shift+C = Flame Cloak), so no global kit is
	 * swapped while other tests run: under 100 C is the wall; at 100 a tap is still the wall and spends nothing; Shift+C is
	 * still the Cloak; a 1 s hold fires the ultimate once, spends the gauge and swallows the release.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void holdingCAtFullFiresTheUltimateAndATapIsStillTheCMove(GameTestHelper helper) {
		floor(helper);
		ServerPlayer a = holder(helper, Quirk.HELLFLAME, 1.5, 1.5);
		ServerPlayer b = holder(helper, Quirk.HELLFLAME, 4.5, 1.5);
		ServerPlayer c = holder(helper, Quirk.HELLFLAME, 6.5, 1.5);
		// under 100: C is the Fire Wall
		PlusUltra.set(a, 99f);
		QuirkAbilityManager.handle(a, AbilitySlot.SLOT_6, true);
		QuirkAbilityManager.handle(a, AbilitySlot.SLOT_6, false);
		helper.assertTrue(!Quirks.ready(a, HellflameConfig.WALL) && !PlusUltra.holding(a), "under 100 C is the kit's C move");
		// at 100: the press waits for the hold; a quick release is the normal C move and spends nothing
		PlusUltra.set(b, 100f);
		QuirkAbilityManager.handle(b, AbilitySlot.SLOT_6, true);
		helper.assertTrue(PlusUltra.holding(b) && Quirks.ready(b, HellflameConfig.WALL), "at 100 the press starts the hold, the kit waits");
		helper.assertTrue(PlusUltra.clientHold(b) >= 0f, "the hold is synced (the C ring on the HUD)");
		QuirkAbilityManager.handle(b, AbilitySlot.SLOT_6, false);
		helper.assertTrue(!Quirks.ready(b, HellflameConfig.WALL) && !PlusUltra.holding(b), "a tap is the normal C move");
		helper.assertTrue(PlusUltra.gauge(b) == 100f && PlusUltra.firedCount(b) == 0, "and spends nothing");
		// Shift+C at 100 is the kit's Shift+C
		b.setShiftKeyDown(true);
		QuirkAbilityManager.handle(b, AbilitySlot.SLOT_6, true);
		b.setShiftKeyDown(false);
		helper.assertTrue(com.projecthero.mod.quirk.hellflame.Hellflame.cloaked(b) && !PlusUltra.holding(b), "Shift+C goes through (the Cloak)");
		// the hold
		PlusUltra.set(c, 100f);
		QuirkAbilityManager.handle(c, AbilitySlot.SLOT_6, true);
		pump(helper, c);
		helper.runAfterDelay(PlusUltra.HOLD_TICKS / 2, () -> helper.assertTrue(PlusUltra.firedCount(c) == 0 && PlusUltra.holding(c), "half a hold: not yet"));
		helper.runAfterDelay(PlusUltra.HOLD_TICKS + 3, () -> {
			helper.assertTrue(PlusUltra.firedCount(c) == 1, "held 1 s at 100: the ultimate fired once");
			helper.assertTrue(PlusUltra.gauge(c) == 0f, "it spent the whole gauge");
			helper.assertTrue(Quirks.ready(c, HellflameConfig.WALL), "the kit's C never fired");
			QuirkAbilityManager.handle(c, AbilitySlot.SLOT_6, false);
			helper.assertTrue(Quirks.ready(c, HellflameConfig.WALL), "and the release was swallowed");
			helper.assertTrue(PlusUltra.sinceFired(c) < 10, "the fire time is synced (first-person body, banner)");
			helper.succeed();
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new net.minecraft.core.BlockPos(x, 1, z)),
						net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void theDefaultUltimateIsTheFrameworkPlaceholder(GameTestHelper helper) {
		ServerPlayer p = holder(helper, Quirk.OVERHAUL, 3.5, 3.5);
		Zombie z = zombie(helper, 3.5, 5.0);
		float hp = z.getHealth();
		PlusUltra.set(p, 100f);
		PlusUltra.fire(p);
		helper.assertTrue(PlusUltra.gauge(p) == 0f, "spent");
		helper.assertTrue(z.getHealth() == hp, "the placeholder deals no damage");
		helper.assertTrue(z.getDeltaMovement().horizontalDistance() > 0.2, "but shoves what is near");
		helper.assertTrue(!helper.getLevel().getEntitiesOfClass(com.projecthero.mod.quirk.fx.QuirkFxEntity.class, p.getBoundingBox().inflate(4)).isEmpty(),
				"and spawns its flash effects");
		helper.assertFalse(PlusUltra.applies(holder(helper, Quirk.ONE_FOR_ALL, 5.5, 1.5)), "One For All keeps its own engine (no gauge)");
		helper.succeed();
	}

	// ---------------------------------------------------------------- bands

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void everyKitQuirkHasThreeBandsAtTheDocsThresholds(GameTestHelper helper) {
		float[][] want = { // sweet from, red from (docs/QUIRKS_REVAMP_PROPOSAL.md section 3)
				{ 40, 80 }, { 25, 80 }, { 30, 80 }, { 30, 75 }, { 50, 90 }, { 30, 80 }, { 50, 85 } };
		Quirk[] qs = { Quirk.HELLFLAME, Quirk.OVERHAUL, Quirk.ENGINE, Quirk.DARK_SHADOW, Quirk.ERASURE, Quirk.EXPLOSION, Quirk.ZERO_GRAVITY };
		for (int i = 0; i < qs.length; i++) {
			QuirkMeterSpec s = QuirkMeters.spec(qs[i]);
			helper.assertTrue(s != null, qs[i].id + " registers a meter spec");
			helper.assertTrue(s.sweetFrom == want[i][0] && s.redFrom == want[i][1], qs[i].id + " bands " + s.sweetFrom + "/" + s.redFrom);
			helper.assertTrue(s.bandOf(0f) == QuirkBand.LOW && s.bandOf(s.sweetFrom - 0.01f) == QuirkBand.LOW, qs[i].id + " LOW");
			helper.assertTrue(s.bandOf(s.sweetFrom) == QuirkBand.SWEET && s.bandOf(s.redFrom - 0.01f) == QuirkBand.SWEET, qs[i].id + " SWEET");
			helper.assertTrue(s.bandOf(s.redFrom) == QuirkBand.RED && s.bandOf(100f) == QuirkBand.RED, qs[i].id + " RED");
		}
		helper.assertTrue(QuirkMeters.spec(Quirk.ONE_FOR_ALL) == null, "One For All has no band spec");
		// the synced band (the HUD's) follows the meter
		ServerPlayer p = holder(helper, Quirk.HELLFLAME, 1.5, 1.5);
		Quirks.setMeter(p, 85f);
		p.tickCount++;
		Quirks.tick(p);
		helper.assertTrue(QuirkMeters.band(p) == QuirkBand.RED && QuirkMeters.clientBand(p) == QuirkBand.RED, "85 Heat is Inferno (RED)");
		helper.assertTrue(QuirkMeters.damageMult(p) == QuirkMeters.spec(Quirk.HELLFLAME).damage[2], "the RED band's damage multiplier");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void pushingPast100CostsHpThenCrashes(GameTestHelper helper) {
		QuirkMeterSpec real = QuirkMeters.spec(Quirk.HELLFLAME);
		QuirkMeterSpec push = QuirkMeterSpec.builder(Quirk.HELLFLAME).bands(40f, 80f).pushPast(30, 1f, 40).build();
		QuirkMeters.register(push);
		ServerPlayer p = holder(helper, Quirk.HELLFLAME, 1.5, 1.5);
		try {
			helper.assertFalse(QuirkMeters.pushPast(p), "under 100 there is nothing to push past");
			Quirks.setMeter(p, 100f);
			helper.assertTrue(QuirkMeters.pushPast(p), "at 100: push past it");
			helper.assertTrue(QuirkMeters.beyond(p) && QuirkMeters.band(p) == QuirkBand.BEYOND, "the BEYOND band");
			helper.assertFalse(QuirkMeters.pushPast(p), "once at a time");
		} catch (RuntimeException e) {
			QuirkMeters.register(real);
			throw e;
		}
		float hp = p.getHealth();
		pump(helper, p);
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(p.getHealth() < hp, "it costs HP: " + hp + " -> " + p.getHealth());
			helper.assertTrue(PlusUltra.gauge(p) == 0f, "its own HP cost is not a hit taken");
		});
		helper.runAfterDelay(34, () -> {
			try {
				helper.assertFalse(QuirkMeters.beyond(p), "the push ends");
				helper.assertTrue(Quirks.overloaded(p), "and he crashes");
			} finally {
				QuirkMeters.register(real);
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- meter pacing (the user's "build gently" rule)

	/**
	 * Drives the spec's scripted heavy use through the REAL paced channel for up to {@code ticks}: the sustained rate every
	 * tick, the next ready attack in the rotation every {@code pressEvery} ticks, the in-use refill (SPENDS meters). Returns the
	 * first tick the channel was in its danger zone, or -1.
	 */
	private static int heavyUse(ServerPlayer p, QuirkMeterSpec s, int ticks) {
		QuirkMeterSpec.HeavyUse h = s.heavyUse();
		float dir = s.channelDirection == QuirkMeterSpec.Direction.BUILDS ? 1f : -1f;
		long[] ready = new long[h.moves.size()];
		int next = 0;
		for (int t = 0; t < ticks; t++) {
			if (h.sustainedPerSecond != 0f) {
				s.channel.add(p, dir * h.sustainedAt(t) / 20f);
			}
			if (h.regenPerSecond != 0f) {
				s.channel.add(p, -dir * h.regenPerSecond / 20f);
			}
			if (!h.moves.isEmpty() && t % h.pressEvery == 0) {
				for (int k = 0; k < h.moves.size(); k++) {
					int i = (next + k) % h.moves.size();
					if (ready[i] <= t) {
						s.channel.add(p, dir * h.moves.get(i)[0]);
						ready[i] = t + (long) h.moves.get(i)[1];
						next = i + 1;
						break;
					}
				}
			}
			if (s.dangerous(s.channel.read(p))) {
				return t;
			}
		}
		return -1;
	}

	/** From the danger threshold, resting: the kit's own recovery (after its delay), paced. Ticks to "safe", or -1. */
	private static int recovery(ServerPlayer p, QuirkMeterSpec s, int ticks) {
		QuirkMeterSpec.HeavyUse h = s.heavyUse();
		float dir = s.channelDirection == QuirkMeterSpec.Direction.BUILDS ? 1f : -1f;
		s.channel.set(p, s.danger);
		for (int t = 0; t < ticks; t++) {
			if (t >= h.recoverDelay) {
				s.channel.add(p, -dir * h.recoverPerSecond / 20f);
			}
			if (s.recovered(s.channel.read(p))) {
				return t;
			}
		}
		return -1;
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void heavyUseNeverReachesTheRedZoneWithin45Seconds(GameTestHelper helper) {
		StringBuilder report = new StringBuilder();
		double x = 0.5;
		for (Quirk q : Quirk.kitQuirks()) {
			QuirkMeterSpec s = QuirkMeters.spec(q);
			helper.assertTrue(s != null, q.id + " has a meter spec");
			ServerPlayer p = holder(helper, q, x, 1.5);
			x += 1.0;
			s.channel.set(p, s.channelDirection == QuirkMeterSpec.Direction.BUILDS ? 0f : 100f);
			int hit = heavyUse(p, s, 45 * 20);
			report.append(q.id).append('=').append(hit < 0 ? ">45s" : (hit / 20f) + "s").append(' ');
			helper.assertTrue(hit < 0, q.id + ": steady heavy use reached the danger zone after only " + (hit / 20f) + " s (" + report + ")");
			// and it is not toothless: an hour of the same use does get there (or, for a refilling meter, never empties)
			if (s.channelDirection == QuirkMeterSpec.Direction.BUILDS) {
				s.channel.set(p, 0f);
				int late = heavyUse(p, s, 180 * 20);
				helper.assertTrue(late > 0, q.id + ": three minutes of heavy use does reach the danger zone");
			}
			Quirks.revoke(p);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void restingBringsEveryMeterBackToSafeQuickly(GameTestHelper helper) {
		double x = 0.5;
		for (Quirk q : Quirk.kitQuirks()) {
			QuirkMeterSpec s = QuirkMeters.spec(q);
			ServerPlayer p = holder(helper, q, x, 4.5);
			x += 1.0;
			int t = recovery(p, s, 40 * 20);
			helper.assertTrue(t >= 0 && t <= 15 * 20, q.id + ": from the danger zone back to safe took " + (t / 20f) + " s (max 15)");
			Quirks.revoke(p);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void pacingIsAppliedOnlyToNaturalBuildAndRecovery(GameTestHelper helper) {
		ServerPlayer p = holder(helper, Quirk.ERASURE, 1.5, 1.5);
		Quirks.setMeter(p, 0f);
		Quirks.addMeterPaced(p, 10f);
		helper.assertTrue(Math.abs(Quirks.meter(p) - 10f * ErasureMeter.BUILD) < 0.001f, "a natural build is paced: " + Quirks.meter(p));
		Quirks.addMeter(p, 10f);
		helper.assertTrue(Math.abs(Quirks.meter(p) - 10f * (1f + ErasureMeter.BUILD)) < 0.001f, "a deliberate change is raw");
		Quirks.addMeterPaced(p, -1f);
		helper.assertTrue(Math.abs(Quirks.meter(p) - (10f * (1f + ErasureMeter.BUILD) - ErasureMeter.RECOVER)) < 0.001f, "recovery x RECOVER");
		// Explosion: Sweat (fuel) is never paced, the Arm Strain channel is
		ServerPlayer b = holder(helper, Quirk.EXPLOSION, 3.5, 1.5);
		Quirks.setMeter(b, 0f);
		Quirks.addMeterPaced(b, 10f);
		helper.assertTrue(Math.abs(Quirks.meter(b) - 10f) < 0.001f, "Sweat builds unpaced");
		com.projecthero.mod.quirk.explosion.ExplosionMoves.setStrain(b, 0f);
		com.projecthero.mod.quirk.explosion.ExplosionMoves.strain(b, ExplosionConfig.AP_STRAIN);
		helper.assertTrue(Math.abs(com.projecthero.mod.quirk.explosion.ExplosionMoves.strain(b) - ExplosionConfig.AP_STRAIN * ExplosionMeter.BUILD) < 0.001f,
				"Arm Strain is paced");
		helper.succeed();
	}

	// ---------------------------------------------------------------- combo routes

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void comboRoutesLandInOrderInsideTheirWindow(GameTestHelper helper) {
		ServerPlayer p = holder(helper, Quirk.EXPLOSION, 1.5, 1.5);
		helper.assertTrue(QuirkCombos.used(p, ExplosionConfig.AP_SHOT) == null, "a finisher alone is no combo");
		helper.assertTrue(QuirkCombos.used(p, ExplosionConfig.STUN_GRENADE) == null, "the opener alone is no combo");
		helper.assertTrue(QuirkCombos.openWindow(p) == ExplosionMeter.STUN_SHOT, "the opener opens the window (the HUD flashes the finisher)");
		float before = PlusUltra.gauge(p);
		QuirkCombos.Route r = QuirkCombos.used(p, ExplosionConfig.AP_SHOT);
		helper.assertTrue(r == ExplosionMeter.STUN_SHOT && QuirkCombos.mult(r) == 1.5f, "Stun Grenade then AP Shot: Flashbang Snipe");
		helper.assertTrue(QuirkCombos.last(p) == r && QuirkCombos.count(p) == 1, "recorded");
		helper.assertTrue(Math.abs(PlusUltra.gauge(p) - before - PlusUltra.COMBO) < 0.01f, "a combo feeds Plus Ultra");
		helper.assertTrue(QuirkCombos.used(p, ExplosionConfig.AP_SHOT) == null, "one opener, one finish");
		helper.assertTrue(QuirkCombos.mult(null) == 1f, "no route: x1");
		// another Quirk's routes never fire for this one
		helper.assertTrue(QuirkCombos.used(p, ErasureConfig.BIND) == null && QuirkCombos.used(p, ErasureConfig.SPIN) == null, "only this Quirk's routes");
		// the window closes
		QuirkCombos.used(p, ExplosionConfig.STUN_GRENADE);
		helper.runAfterDelay(ExplosionMeter.STUN_SHOT.window + 2, () -> {
			helper.assertTrue(QuirkCombos.openWindow(p) == null, "the window closed");
			helper.assertTrue(QuirkCombos.used(p, ExplosionConfig.AP_SHOT) == null, "too late: no combo");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sameTargetRoutesNeedTheSameCreature(GameTestHelper helper) {
		ServerPlayer p = holder(helper, Quirk.ERASURE, 1.5, 1.5);
		Zombie a = zombie(helper, 1.5, 4.5);
		Zombie b = zombie(helper, 4.5, 4.5);
		QuirkCombos.hit(p, a, ErasureConfig.BIND);
		helper.assertTrue(QuirkCombos.hit(p, b, ErasureConfig.SPIN) == null, "bound one, spun another: no combo");
		QuirkCombos.hit(p, a, ErasureConfig.BIND);
		helper.assertTrue(QuirkCombos.used(p, ErasureConfig.SPIN) == null, "a same-target route needs the target");
		QuirkCombos.hit(p, a, ErasureConfig.BIND);
		helper.assertTrue(QuirkCombos.hit(p, a, ErasureConfig.SPIN) == ErasureMeter.BIND_SPIN, "Bind then Spin on the same zombie: Strung Up");
		helper.succeed();
	}

	// ---------------------------------------------------------------- state hygiene

	@GameTest(template = EMPTY_STRUCTURE)
	public void frameworkStateIsDroppedOnRevokeAndServerReset(GameTestHelper helper) {
		ServerPlayer p = holder(helper, Quirk.ENGINE, 1.5, 1.5);
		PlusUltra.set(p, 100f);
		QuirkCombos.used(p, ExplosionConfig.STUN_GRENADE);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		helper.assertTrue(PlusUltra.holding(p), "holding");
		Quirks.revoke(p);
		helper.assertFalse(PlusUltra.holding(p), "revoke drops the hold");
		helper.assertTrue(Quirks.state(p).val(PlusUltra.V_GAUGE) == 0L, "and the gauge");
		helper.assertTrue(UniquePowers.forced(() -> Quirks.grant(p, Quirk.ENGINE)), "regranted");
		helper.assertTrue(PlusUltra.gauge(p) == 0f, "a fresh Quirk starts empty");
		PlusUltra.set(p, 100f);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, false);
		QuirkAbilityManager.handle(p, AbilitySlot.SLOT_6, true);
		helper.assertTrue(PlusUltra.holding(p), "holding again");
		// death / logout / a dimension change end the hold too (the gauge itself is saved in the synced vals)
		Quirks.clearTransient(p);
		helper.assertFalse(PlusUltra.holding(p), "clearTransient drops the hold");
		helper.assertTrue(PlusUltra.gauge(p) == 100f, "but keeps the saved gauge");
		Quirks.revoke(p);
		helper.succeed();
	}
}
