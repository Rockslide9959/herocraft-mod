package com.projecthero.mod.gametest;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.MiraculousConfig;
import com.projecthero.mod.miraculous.MiraculousPhrases;
import com.projecthero.mod.miraculous.MiraculousSequence;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user: "can you add it to the other miraculous as well please"): every hero's long transformation, mirroring
 * Ladybug's ({@code MiraculousCoreGameTests#tikkiSpotsOnPlaysTheLongSequence}): the full phrase with the Kwami's name
 * plays it (held in the air, unhurt, no powers, no changing back), it ends transformed with the powers on, and the short
 * phrase alone is still the quick one. Mock players are not ticked by the server, so the tests drive
 * {@link Miraculous#tick} themselves.
 */
public class MiraculousSequenceGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static ServerPlayer holder(GameTestHelper helper, Kwami k) {
		ServerPlayer p = mock(helper);
		Miraculous.grant(p, k);
		Miraculous.edit(p, n -> n.hungry = false);
		return p;
	}

	/** The full phrase: the long sequence -- held, unhurt, powerless, can't change back -- and transformed with powers at the end. */
	private static void fullPhrase(GameTestHelper helper, Kwami k) {
		String full = MiraculousPhrases.transformPhrase(k);
		String hero = MiraculousPhrases.heroName(k);
		helper.assertTrue(MiraculousPhrases.match(k, full) == MiraculousPhrases.Spoken.TRANSFORM_LONG, hero + ": the full phrase is the long one");
		MiraculousSequence.Timeline tl = MiraculousSequence.of(k);
		helper.assertTrue(tl != null && tl.total() >= 150 && tl.total() <= 190, hero + ": about as long as Ladybug's");
		helper.assertTrue(tl.cover(0f) == 0f && tl.cover(tl.total()) >= 0.999f, hero + ": the suit goes on from nothing to all of it");
		float last = 0f;
		for (int t = 0; t <= tl.total(); t++) {
			float c = tl.cover(t);
			helper.assertTrue(c >= last - 1.0e-4f, hero + ": the suit never comes off mid-sequence (tick " + t + ")");
			last = c;
		}
		ServerPlayer p = holder(helper, k);
		double y0 = p.getY();
		helper.assertTrue(Miraculous.onChat(p, full) && Miraculous.transformedAs(p, k), hero + ": transforms");
		MiraculousState s = Miraculous.state(p);
		helper.assertTrue(s.animId == MiraculousSequence.ANIM, hero + ": as the long sequence");
		helper.assertTrue(Miraculous.formTicks(s) == tl.total(), hero + ": timed by the hero's own sequence");
		helper.assertTrue(MiraculousSequence.running(p), hero + ": it is running");
		helper.assertFalse(Miraculous.canAct(p), hero + ": no powers while it plays");
		helper.assertFalse(p.hurt(p.damageSources().generic(), 5f), hero + ": nothing can hurt them");
		Miraculous.onChat(p, MiraculousPhrases.detransformPhrase(k));
		helper.assertTrue(Miraculous.transformedAs(p, k), hero + ": can't change back mid-sequence");
		helper.onEachTick(() -> Miraculous.tick(p));
		int mid = tl.total() / 2;
		helper.runAfterDelay(mid, () -> {
			MiraculousState m = Miraculous.state(p);
			long age = p.level().getGameTime() - m.formChangeAt;
			helper.assertTrue(MiraculousSequence.running(p), hero + ": still playing half way");
			helper.assertTrue(Math.abs(p.getY() - (y0 + tl.lift(age))) < 0.15, hero + ": held in the air where they began, at " + p.getY() + " vs "
					+ (y0 + tl.lift(age)));
		});
		helper.runAfterDelay(tl.total() + 3, () -> {
			helper.assertFalse(MiraculousSequence.running(p), hero + ": over");
			helper.assertTrue(Miraculous.transformedAs(p, k), hero + ": ends transformed");
			helper.assertTrue(Miraculous.canAct(p), hero + ": the powers are on");
			helper.succeed();
		});
	}

	/** The short phrase alone: the quick transformation, as before. */
	private static void shortPhrase(GameTestHelper helper, Kwami k) {
		String full = MiraculousPhrases.transformPhrase(k);
		String quick = full.substring(full.indexOf(',') + 1).trim();
		String hero = MiraculousPhrases.heroName(k);
		helper.assertTrue(MiraculousPhrases.match(k, quick) == MiraculousPhrases.Spoken.TRANSFORM, hero + ": '" + quick + "' is the quick one");
		ServerPlayer p = holder(helper, k);
		helper.assertTrue(Miraculous.onChat(p, quick) && Miraculous.transformedAs(p, k), hero + ": transforms");
		MiraculousState s = Miraculous.state(p);
		helper.assertTrue(s.animId == MiraculousState.ANIM_TRANSFORM, hero + ": the quick look");
		helper.assertFalse(MiraculousSequence.running(p), hero + ": no long sequence");
		helper.assertTrue(Miraculous.formTicks(s) == MiraculousConfig.TRANSFORM_TICKS, hero + ": the quick length");
		helper.runAfterDelay(MiraculousConfig.TRANSFORM_TICKS + 2, () -> {
			helper.assertTrue(Miraculous.canAct(p), hero + ": the powers come on after the quick one");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void plaggClawsOutPlaysTheLongSequence(GameTestHelper helper) {
		fullPhrase(helper, Kwami.PLAGG);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void clawsOutAloneIsTheQuickOne(GameTestHelper helper) {
		shortPhrase(helper, Kwami.PLAGG);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void trixxLetsPouncePlaysTheLongSequence(GameTestHelper helper) {
		fullPhrase(helper, Kwami.TRIXX);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void letsPounceAloneIsTheQuickOne(GameTestHelper helper) {
		shortPhrase(helper, Kwami.TRIXX);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void noorooDarkWingsRisePlaysTheLongSequence(GameTestHelper helper) {
		fullPhrase(helper, Kwami.NOOROO);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void darkWingsRiseAloneIsTheQuickOne(GameTestHelper helper) {
		shortPhrase(helper, Kwami.NOOROO);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void wayzzShellOnPlaysTheLongSequence(GameTestHelper helper) {
		fullPhrase(helper, Kwami.WAYZZ);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void shellOnAloneIsTheQuickOne(GameTestHelper helper) {
		shortPhrase(helper, Kwami.WAYZZ);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void kaalkiFullGallopPlaysTheLongSequence(GameTestHelper helper) {
		fullPhrase(helper, Kwami.KAALKI);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void fullGallopAloneIsTheQuickOne(GameTestHelper helper) {
		shortPhrase(helper, Kwami.KAALKI);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void sassScalesSlitherPlaysTheLongSequence(GameTestHelper helper) {
		fullPhrase(helper, Kwami.SASS);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void scalesSlitherAloneIsTheQuickOne(GameTestHelper helper) {
		shortPhrase(helper, Kwami.SASS);
	}

	/** Ladybug's is unchanged on the shared frame: the same length, ANIM id and reveal. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void ladybugsSequenceIsUnchanged(GameTestHelper helper) {
		MiraculousSequence.Timeline tl = MiraculousSequence.of(Kwami.TIKKI);
		helper.assertTrue(tl == com.projecthero.mod.miraculous.ladybug.LadybugSequence.TIMELINE && tl.total() == 170
				&& com.projecthero.mod.miraculous.ladybug.LadybugSequence.ANIM == 5, "the same 8.5 s sequence, the same anim id");
		helper.assertTrue(Math.abs(tl.cover(92f) - 0.84f) < 1.0e-4f && tl.cover(30f) == 0f && Math.abs(tl.spin(92f) - 720f) < 1.0e-3f
				&& Math.abs(tl.lift(60f) - 1.0) < 1.0e-6, "the same reveal, twirl and lift");
		for (Kwami k : Kwami.values()) {
			helper.assertTrue(MiraculousSequence.of(k) != null, k.id + " has a long transformation");
		}
		helper.succeed();
	}
}
