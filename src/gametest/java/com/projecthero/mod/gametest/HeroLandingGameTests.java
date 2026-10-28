package com.projecthero.mod.gametest;

import java.util.function.Consumer;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.flight.landing.HeroLanding;
import com.projecthero.mod.flight.landing.HeroLandingStyle;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternFlight;
import com.projecthero.mod.kryptonian.Kryptonian;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the shared superhero landing ({@link HeroLanding}). A fast touchdown out of a hero flight starts the landing
 * hold (Green Lantern, Thor and a Kryptonian's long drop); a gentle one does not; a hero landing never takes fall damage;
 * the hold ends on its own and early when walking off; never in water or while riding.
 *
 * <p>Mock players never move, so the descent is the "known movement" a client would report ({@code setKnownMovement}) and
 * ground contact is {@code setOnGround}; {@link HeroLanding#tick} is called directly (repeating it within a tick is
 * harmless, the server loop also runs it).
 */
public class HeroLandingGameTests implements FabricGameTest {
	private static final Vec3 FAST = new Vec3(0.0, -0.9, 0.0);
	private static final Vec3 GENTLE = new Vec3(0.05, -0.2, 0.0);

	private static ServerPlayer player(GameTestHelper h, BlockPos rel) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		BlockPos at = h.absolutePos(rel);
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		return p;
	}

	private static ServerPlayer lantern(GameTestHelper h, BlockPos rel) {
		ServerPlayer p = player(h, rel);
		GreenLantern.bond(p);
		GreenLanternFlight.onEnter(p);
		return p;
	}

	/**
	 * Keeps {@code p} airborne descending at {@code motion} for 4 ticks ({@code flying} re-applied each tick, as the
	 * power's own tick would), touches down, then runs {@code then} the same tick.
	 */
	private static void descendAndLand(GameTestHelper h, ServerPlayer p, Vec3 motion, Runnable flying, Consumer<ServerPlayer> then) {
		for (int i = 0; i < 4; i++) {
			h.runAfterDelay(i, () -> {
				flying.run();
				p.setOnGround(false);
				p.setKnownMovement(motion);
				HeroLanding.tick(p);
			});
		}
		h.runAfterDelay(4, () -> {
			p.setOnGround(true);
			HeroLanding.tick(p);
			then.accept(p);
		});
	}

	// ------------------------------------------------------------------ fast landings trigger

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void greenLanternFastLandingIsAHeroLanding(GameTestHelper h) {
		ServerPlayer p = lantern(h, new BlockPos(1, 2, 1));
		descendAndLand(h, p, FAST, () -> p.setAttached(ModAttachments.GREEN_LANTERN_FLYING, true), q -> {
			HeroLanding.Active a = HeroLanding.active(q);
			h.assertTrue(a != null, "a fast Ring Flight touchdown starts the hero landing");
			h.assertTrue(a.style() == HeroLandingStyle.GREEN_LANTERN, "in the Green Lantern style, was " + a.style());
			h.assertTrue(a.strength() > 0.15f, "with some strength");
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void thorFastLandingIsHammerFirst(GameTestHelper h) {
		ServerPlayer p = player(h, new BlockPos(1, 2, 1));
		descendAndLand(h, p, FAST, () -> p.setAttached(ModAttachments.FLYING, true), q -> {
			HeroLanding.Active a = HeroLanding.active(q);
			h.assertTrue(a != null && a.style() == HeroLandingStyle.THOR, "a fast Thor flight touchdown is a hammer-first landing");
			h.assertTrue(a.style().pose == HeroLandingStyle.Pose.HAMMER, "the hammer pose");
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void kryptonianLongDropIsAHeroLanding(GameTestHelper h) {
		ServerPlayer p = player(h, new BlockPos(1, 2, 1));
		Kryptonian.grant(p);
		h.assertTrue(Kryptonian.empowered(p), "precondition: an empowered Kryptonian");
		// not flying at all: a long, fast drop (about 9 blocks) still lands like Superman
		descendAndLand(h, p, new Vec3(0.0, -1.1, 0.0), () -> {
		}, q -> {
			HeroLanding.Active a = HeroLanding.active(q);
			h.assertTrue(a != null && a.style() == HeroLandingStyle.KRYPTONIAN, "a Kryptonian's long drop ends in the three-point landing");
			h.succeed();
		});
	}

	// ------------------------------------------------------------------ gentle touchdowns do not

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aGentleTouchdownJustLands(GameTestHelper h) {
		ServerPlayer p = lantern(h, new BlockPos(1, 2, 1));
		descendAndLand(h, p, GENTLE, () -> p.setAttached(ModAttachments.GREEN_LANTERN_FLYING, true), q -> {
			h.assertFalse(HeroLanding.landing(q), "a slow touchdown is not a hero landing");
			h.assertFalse(HeroLanding.fast(0.3, 0.5), "0.3 b/t down, 0.5 b/t total: not fast");
			h.assertTrue(HeroLanding.fast(HeroLanding.FAST_DESCENT, 0.0), "the descent threshold itself is fast");
			h.assertTrue(HeroLanding.fast(HeroLanding.DIVE_DESCENT, HeroLanding.DIVE_SPEED), "so is a dive at the dive thresholds");
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aFastDropWithoutAHeroFlightIsNothing(GameTestHelper h) {
		ServerPlayer p = player(h, new BlockPos(1, 2, 1));
		descendAndLand(h, p, FAST, () -> {
		}, q -> {
			h.assertFalse(HeroLanding.landing(q), "no hero flight, no fall-immune power: an ordinary fall");
			h.succeed();
		});
	}

	// ------------------------------------------------------------------ no fall damage

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aHeroLandingTakesNoFallDamage(GameTestHelper h) {
		// Thor's flight flag on a powerless player: no other fall immunity, so only the hero landing can cancel the fall
		ServerPlayer p = player(h, new BlockPos(1, 2, 1));
		descendAndLand(h, p, FAST, () -> p.setAttached(ModAttachments.FLYING, true), q -> {
			h.assertTrue(HeroLanding.landing(q), "precondition: landing");
			float before = q.getHealth();
			q.fallDistance = 30f;
			boolean hurt = q.hurt(q.damageSources().fall(), 12f);
			h.assertFalse(hurt, "the fall is cancelled on a hero landing");
			h.assertTrue(q.getHealth() == before, "health unchanged");
			h.assertTrue(HeroLanding.fallSafe(q), "the landing marks the fall safe (any earlier fall handler may cancel it first)");
			h.succeed();
		});
	}

	// ------------------------------------------------------------------ the hold ends

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void theHoldClearsOnItsOwn(GameTestHelper h) {
		ServerPlayer p = lantern(h, new BlockPos(1, 2, 1));
		descendAndLand(h, p, FAST, () -> p.setAttached(ModAttachments.GREEN_LANTERN_FLYING, true), q -> {
			h.assertTrue(HeroLanding.landing(q), "precondition: landing");
			q.setKnownMovement(Vec3.ZERO);
			int hold = HeroLandingStyle.GREEN_LANTERN.holdTicks;
			h.runAfterDelay(hold - 4, () -> {
				q.setKnownMovement(Vec3.ZERO);
				HeroLanding.tick(q);
				h.assertTrue(HeroLanding.landing(q), "standing still: still holding the pose");
			});
			h.runAfterDelay(hold + 2, () -> {
				HeroLanding.tick(q);
				h.assertFalse(HeroLanding.landing(q), "the hold ended by itself");
				h.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void walkingOffEndsTheHoldEarly(GameTestHelper h) {
		ServerPlayer p = lantern(h, new BlockPos(1, 2, 1));
		descendAndLand(h, p, FAST, () -> p.setAttached(ModAttachments.GREEN_LANTERN_FLYING, true), q -> {
			h.assertTrue(HeroLanding.landing(q), "precondition: landing");
			q.setKnownMovement(new Vec3(0.3, 0.0, 0.0));
			HeroLanding.tick(q);
			h.assertTrue(HeroLanding.landing(q), "moving inside the impact lock does not cut it");
			h.runAfterDelay(HeroLandingStyle.GREEN_LANTERN.lockTicks + 1, () -> {
				q.setKnownMovement(new Vec3(0.3, 0.0, 0.0));
				HeroLanding.tick(q);
				h.assertFalse(HeroLanding.landing(q), "walking off ends it early");
				h.succeed();
			});
		});
	}

	// ------------------------------------------------------------------ never in water or riding

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void neverInWater(GameTestHelper h) {
		BlockPos rel = new BlockPos(1, 2, 1);
		h.setBlock(rel, Blocks.WATER);
		ServerPlayer p = lantern(h, rel);
		p.baseTick();
		h.assertTrue(p.isInWater(), "precondition: standing in water");
		descendAndLand(h, p, FAST, () -> {
			p.setAttached(ModAttachments.GREEN_LANTERN_FLYING, true);
			p.baseTick();
		}, q -> {
			h.assertFalse(HeroLanding.landing(q), "no hero landing in water");
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void neverWhileRiding(GameTestHelper h) {
		ServerPlayer p = lantern(h, new BlockPos(1, 2, 1));
		ArmorStand seat = EntityType.ARMOR_STAND.create(h.getLevel());
		seat.moveTo(p.getX(), p.getY(), p.getZ());
		h.getLevel().addFreshEntity(seat);
		h.assertTrue(p.startRiding(seat, true), "precondition: riding");
		descendAndLand(h, p, FAST, () -> p.setAttached(ModAttachments.GREEN_LANTERN_FLYING, true), q -> {
			h.assertFalse(HeroLanding.landing(q), "no hero landing while riding");
			HeroLanding.trigger(q, HeroLandingStyle.GREEN_LANTERN, 1f);
			h.assertFalse(HeroLanding.landing(q), "not even when asked directly");
			q.stopRiding();
			h.succeed();
		});
	}
}
