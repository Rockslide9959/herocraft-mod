package com.projecthero.mod.gametest;

import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteState;
import com.projecthero.mod.symbiote.SymbioteTransform;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

/**
 * v0.17.5 round 2 (user: "right now as it is the player grows as the suit covers them"): the suit now spreads over a normal-sized host
 * and only bulks them up once it is on ({@link Symbiote#surge}); taking it off deflates first. The clock is 3 s on, 1.8 s off. The
 * transform clock is set directly (no waiting), so the test does not depend on how fast the server ticks.
 */
public class SymbioteSuitUpGameTests implements FabricGameTest {
	private static ServerPlayer host(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Symbiote.grant(player);
		return player;
	}

	/** Puts the running transform clock at {@code raw} (0..1 of its length) and runs one Symbiote tick. */
	private static void at(ServerPlayer p, float raw) {
		SymbioteState s = Symbiote.state(p);
		s.transformStartTick = p.level().getGameTime() - Math.round(raw * s.transformDurationTicks);
		Symbiote.tick(p);
	}

	private static double scale(ServerPlayer p) {
		return p.getAttributeValue(Attributes.SCALE);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void surgeWaitsUntilTheSuitIsOn(GameTestHelper helper) {
		helper.assertTrue(Symbiote.surge(0f) == 0f && Symbiote.surge(0.5f) == 0f && Symbiote.surge(Symbiote.SURGE_FROM) == 0f,
				"no growth while the suit spreads");
		helper.assertTrue(Symbiote.surge(1f) == 1f, "full size once it is on");
		float last = 0f;
		for (int i = 0; i <= 100; i++) {
			float v = Symbiote.surge(i / 100f);
			helper.assertTrue(v >= last - 1e-6f && v >= 0f && v <= 1f, "the surge only ever grows, 0..1 (step " + i + ")");
			// each 1% step adds a bounded amount: a bulk-up over the last 14%, not a pop
			helper.assertTrue(v - last <= 0.12f, "a smooth surge at step " + i + ": +" + (v - last));
			last = v;
		}
		helper.assertTrue(Symbiote.SURGE_FROM >= 0.8f, "the surge starts after the head is covered");
		helper.assertTrue(Symbiote.TRANSFORM_TICKS == 60 && Symbiote.RETRACT_TICKS == 36, "3 s on, 1.8 s off");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hostKeepsItsSizeWhileTheSuitSpreads(GameTestHelper helper) {
		ServerPlayer p = host(helper);
		Symbiote.state(p).toggleReadyAt = 0L;
		Symbiote.toggle(p);
		SymbioteState s = Symbiote.state(p);
		helper.assertTrue(s.transformDir == SymbioteState.DIR_UP && s.transformDurationTicks == Symbiote.TRANSFORM_TICKS, "the suit-up runs 60 ticks");
		for (float raw : new float[] { 0.1f, 0.4f, 0.7f, 0.85f }) {
			at(p, raw);
			helper.assertTrue(Math.abs(scale(p) - 1.0) < 1e-3, "normal size at " + raw + " of the suit-up, scale " + scale(p));
		}
		at(p, 0.95f);
		double mid = scale(p);
		helper.assertTrue(mid > 1.05 && mid < 1.5, "bulking up near the end, scale " + mid);
		// finish: settle, then the host is full size
		s.transformStartTick -= 10_000L;
		Symbiote.tick(p);
		helper.assertTrue(Symbiote.state(p).transformDir == SymbioteState.DIR_IDLE && Symbiote.state(p).active, "the suit settles on");
		Symbiote.tick(p);
		helper.assertTrue(Math.abs(scale(p) - 1.5) < 0.01, "full size with the suit on, scale " + scale(p));
		// off: deflate first, then the suit recedes over a normal-sized body
		Symbiote.state(p).toggleReadyAt = 0L;
		Symbiote.toggle(p);
		s = Symbiote.state(p);
		helper.assertTrue(s.transformDir == SymbioteState.DIR_DOWN && s.transformDurationTicks == Symbiote.RETRACT_TICKS, "the retract runs 36 ticks");
		at(p, 0.05f);
		helper.assertTrue(scale(p) < 1.5 && scale(p) > 1.0, "deflating as it starts to come off, scale " + scale(p));
		for (float raw : new float[] { 0.2f, 0.5f, 0.9f }) {
			at(p, raw);
			helper.assertTrue(Math.abs(scale(p) - 1.0) < 1e-3, "normal size at " + raw + " of the retract, scale " + scale(p));
			helper.assertTrue(SymbioteTransform.effectiveProgress(Symbiote.state(p), p.level().getGameTime()) < Symbiote.SURGE_FROM,
					"the clock runs backwards");
		}
		helper.succeed();
	}
}
