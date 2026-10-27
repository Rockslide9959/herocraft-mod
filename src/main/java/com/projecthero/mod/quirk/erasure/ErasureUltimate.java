package com.projecthero.mod.quirk.erasure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.quirk.QuirkThreat;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.quirk.fx.QuirkParticles;
import com.projecthero.mod.quirk.fx.QuirkSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Quirks revamp (docs/QUIRKS_REVAMP_PROPOSAL.md 3.5): Eraser Head's Plus Ultra ultimate, <b>Underground Takedown</b>
 * (his USJ entrance). Hold C at a full gauge:
 * <ol>
 *   <li>the goggles snap down and everything in a {@link ErasureConfig#TD_RANGE}-block cone is erased for 6 s (Exposed: +20%
 *       damage taken) -- bosses for 4 s, never bound -- with a red flash across the cone;</li>
 *   <li>he chains through up to six of them, nearest first, one every {@link ErasureConfig#TD_STEP_TICKS} ticks: zip (the scarf
 *       yanks him to it), kick ({@link ErasureConfig#TD_KICK_DAMAGE}), bind;</li>
 *   <li>it ends with all of them strung up on the scarf for 2 s, the ribbons linking every one ({@code ErasureWorld} draws them
 *       from the synced chain), then the drawback: his eyes are spent (Dry Eyes 100, forced shut).</li>
 * </ol>
 * He cannot be hit while he chains (the Evade Flip's i-frames cover each hop). State: one session per Eraser, entity ids only;
 * cleared by {@link #forget} / {@link #clearSessionState} and ended by {@link #stop} (stopAll: death, logout, a dimension
 * change, Erased).
 */
public final class ErasureUltimate {
	private static final class Session {
		final List<Integer> chain;
		final long start;
		int step;
		long hangUntil;

		Session(List<Integer> chain, long start) {
			this.chain = chain;
			this.start = start;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private ErasureUltimate() {
	}

	static void clearSessionState() {
		SESSIONS.clear();
	}

	static void forget(UUID id) {
		SESSIONS.remove(id);
	}

	/** Running (tests / HUD). */
	public static boolean running(ServerPlayer p) {
		return SESSIONS.containsKey(p.getUUID());
	}

	/** The chain's creatures (tests). */
	public static List<Integer> chain(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? List.of() : List.copyOf(s.chain);
	}

	/** Who the cone catches: erasable, in sight, nearest first. */
	static List<LivingEntity> cone(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double cos = Math.cos(Math.toRadians(ErasureConfig.TD_CONE_DEGREES));
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(ErasureConfig.TD_RANGE),
				e -> ErasureMoves.erasable(p, e))) {
			Vec3 to = ErasureMoves.mid(e).subtract(eye);
			double d = to.length();
			if (d > ErasureConfig.TD_RANGE + e.getBbWidth() || (d > 0.5 && to.scale(1.0 / d).dot(look) < cos) || !p.hasLineOfSight(e)) {
				continue;
			}
			out.add(e);
		}
		out.sort((a, b) -> Double.compare(a.distanceToSqr(p), b.distanceToSqr(p)));
		return out;
	}

	/** Hold C at 100 (the gauge is already spent): goggles down, the cone erased, the chain starts. Returns how many it erased. */
	public static int fire(ServerPlayer p) {
		stop(p);
		ErasureMoves.eraseUp(p);
		ErasureMoves.detach(p, false);
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		ErasureMoves.setGoggles(p, true);
		Quirks.setAnim(p, ErasureConfig.ANIM_TAKEDOWN);
		List<LivingEntity> caught = cone(p);
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		// the red stare flash across the cone
		for (int i = 1; i <= 4; i++) {
			double d = i * ErasureConfig.TD_RANGE / 4.5;
			float r = (float) (Math.tan(Math.toRadians(ErasureConfig.TD_CONE_DEGREES)) * d);
			QuirkFx.ring(level, eye.add(look.scale(d))).colours(0xFF4040, 0x900808).size(r).thickness(0.35f).life(10 + i * 2)
					.flags(QuirkFx.F_VERTICAL).vec(look).spawn();
		}
		QuirkCamera.vignette(p, 0xC01010, 0.85f, 40);
		ErasureSounds.play(level, eye, ErasureSounds.GLARE, 1.2f, 0.75f);
		List<Integer> chain = new ArrayList<>();
		for (LivingEntity e : caught) {
			boolean boss = ErasureMoves.isBoss(e);
			PowerSuppression.suppress(e, boss ? ErasureConfig.TD_BOSS_ERASE_TICKS : ErasureConfig.TD_ERASE_TICKS);
			Vec3 c = e.position().add(0, e.getBbHeight() + 0.35, 0);
			QuirkFx.ring(level, c).colours(0xFF3030, 0x800808).size(1.3f).thickness(0.14f).life(16).spawn();
			level.sendParticles(ErasureMoves.RED, c.x, c.y, c.z, 6, 0.25, 0.1, 0.25, 0.0);
			if (boss) {
				QuirkThreat.add(e, p, 20f);
			}
			if (chain.size() < ErasureConfig.TD_TARGETS) {
				chain.add(e.getId());
			}
		}
		Session s = new Session(chain, now);
		SESSIONS.put(p.getUUID(), s);
		Quirks.edit(p, n -> {
			for (int i = 0; i < ErasureConfig.TD_TARGETS; i++) {
				if (i < chain.size()) {
					n.vals.put(ErasureConfig.V_TD_PREFIX + i, chain.get(i) + 1L);
				} else {
					n.vals.remove(ErasureConfig.V_TD_PREFIX + i);
				}
			}
			n.vals.put(ErasureConfig.V_TD_AT, now);
			n.vals.put(ErasureConfig.V_TD_STEP, 0L);
			n.vals.remove(ErasureConfig.V_TD_HANG_UNTIL);
			n.vals.put(ErasureConfig.V_FLIP_UNTIL, now + ErasureConfig.TD_STARTUP_TICKS + (long) chain.size() * ErasureConfig.TD_STEP_TICKS + 4);
		});
		return caught.size();
	}

	/** Every tick of the Eraser while it runs. */
	static void tick(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return;
		}
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		long age = now - s.start;
		p.resetFallDistance();
		if (s.hangUntil != 0L) {
			hang(p, s, now);
			if (now >= s.hangUntil) {
				finish(p);
			}
			return;
		}
		if (age < ErasureConfig.TD_STARTUP_TICKS) {
			return;
		}
		long due = (age - ErasureConfig.TD_STARTUP_TICKS) / ErasureConfig.TD_STEP_TICKS;
		while (s.step <= due && s.step < s.chain.size()) {
			hop(p, s, s.step);
			s.step++;
			Quirks.setVal(p, ErasureConfig.V_TD_STEP, s.step);
		}
		if (s.step >= s.chain.size() && age >= ErasureConfig.TD_STARTUP_TICKS + (long) s.chain.size() * ErasureConfig.TD_STEP_TICKS) {
			startHang(p, s, now);
		}
	}

	/** One link of the chain: the scarf yanks him to it, the kick, the bind. */
	private static void hop(ServerPlayer p, Session s, int i) {
		Entity e = p.level().getEntity(s.chain.get(i));
		if (!(e instanceof LivingEntity t) || !t.isAlive()) {
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 from = p.position();
		Vec3 away = from.subtract(t.position());
		away = new Vec3(away.x, 0, away.z);
		away = away.lengthSqr() < 1.0e-4 ? p.getLookAngle().scale(-1).multiply(1, 0, 1).normalize() : away.normalize();
		Vec3 spot = t.position().add(away.scale(t.getBbWidth() * 0.5 + 0.95));
		// do not land inside a wall: fall back to the line toward him
		var hit = level.clip(new ClipContext(t.position().add(0, 0.6, 0), spot.add(0, 0.6, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (hit.getType() != HitResult.Type.MISS) {
			spot = t.position().add(0, t.getBbHeight() + 0.1, 0);
		}
		Vec3 to = ErasureMoves.mid(t).subtract(spot.add(0, p.getEyeHeight(), 0));
		float yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
		QuirkFx.ribbon(level, from.add(0, 1.1, 0), ErasureMoves.mid(t)).style(QuirkFx.RIBBON_CLOTH).colours(0xE4E4E8, 0x8A8A92).size(0.16f).life(8)
				.spawn();
		p.teleportTo(level, spot.x, spot.y, spot.z, yaw, pitch);
		p.resetFallDistance();
		QuirkFx.trail(level, p).colours(0xE8E8EC, 0x6A6A74).style(QuirkFx.RIBBON_CLOTH).size(0.12f).size2(6f).life(10).vec(new Vec3(0.3, 1.2, 0))
				.spawn();
		Quirks.setAnim(p, ErasureConfig.ANIM_TD_KICK);
		t.invulnerableTime = 0;
		if (ErasureMoves.strike(p, t, spot, ErasureConfig.TD_KICK_DAMAGE, 0.25, 0.15)) {
			Vec3 c = ErasureMoves.mid(t);
			QuirkFx.ring(level, c).colours(0xFFFFFF, 0xD0D0D8).size(2.0f).thickness(0.3f).life(8).flags(QuirkFx.F_VERTICAL).vec(to.normalize()).spawn();
			level.sendParticles(QuirkParticles.SPARK, c.x, c.y, c.z, 8, 0.2, 0.2, 0.2, 0.25);
			QuirkSounds.play(level, c, QuirkSounds.IMPACT_HEAVY, 0.9f, 1.0f + i * 0.04f);
			QuirkCamera.preset(p, i == s.chain.size() - 1 ? QuirkCamera.Preset.FINISHER : QuirkCamera.Preset.HEAVY_HIT);
		}
		ErasureSounds.play(level, ErasureMoves.mid(t), ErasureSounds.SCARF_SNAP, 1.0f, 0.9f + i * 0.05f);
		if (!ErasureMoves.isBoss(t)) {
			ErasureMoves.root(p, t, ErasureConfig.TD_STEP_TICKS * (s.chain.size() - i) + ErasureConfig.TD_HANG_TICKS + 4);
			if (t instanceof Mob m) {
				m.getNavigation().stop();
			}
		}
	}

	/** The finish: every bound one hoisted on the scarf, the ribbons linking them all. */
	private static void startHang(ServerPlayer p, Session s, long now) {
		s.hangUntil = now + ErasureConfig.TD_HANG_TICKS;
		long until = s.hangUntil;
		Quirks.setVal(p, ErasureConfig.V_TD_HANG_UNTIL, until);
		ServerLevel level = p.serverLevel();
		Vec3 prev = p.position().add(0, 1.2, 0);
		for (int id : s.chain) {
			if (p.level().getEntity(id) instanceof LivingEntity t && t.isAlive() && !ErasureMoves.isBoss(t)) {
				ErasureMoves.root(p, t, ErasureConfig.TD_HANG_TICKS + 2);
				ErasureMoves.hoist(t, t.getY() + ErasureConfig.REEL_LIFT);
				QuirkFx.ribbon(level, prev, ErasureMoves.mid(t)).style(QuirkFx.RIBBON_CLOTH).colours(0xE4E4E8, 0x8A8A92).size(0.14f).life(14).spawn();
				prev = ErasureMoves.mid(t);
			}
		}
		ErasureSounds.play(level, p.position(), ErasureSounds.BIND, 1.2f, 0.7f);
		QuirkCamera.preset(p, QuirkCamera.Preset.FINISHER);
		Quirks.setAnim(p, ErasureConfig.ANIM_REEL);
	}

	private static void hang(ServerPlayer p, Session s, long now) {
		for (int id : s.chain) {
			if (p.level().getEntity(id) instanceof LivingEntity t && t.isAlive() && !ErasureMoves.isBoss(t) && !(t instanceof Player)) {
				t.resetFallDistance();
			}
		}
	}

	/** It is over: the chain lets go and his eyes are spent (the drawback). */
	private static void finish(ServerPlayer p) {
		stop(p);
		Quirks.setMeter(p, 100f);
		ErasureMoves.shutEyes(p);
	}

	/** Ends a running takedown (no drawback): stopAll, a new fire. */
	static void stop(ServerPlayer p) {
		Session s = SESSIONS.remove(p.getUUID());
		if (s == null && Quirks.state(p).val(ErasureConfig.V_TD_AT) == 0L) {
			return;
		}
		if (s != null) {
			for (int id : s.chain) {
				ErasureMoves.unhoist(p.level().getEntity(id));
			}
		}
		Quirks.edit(p, n -> {
			for (int i = 0; i < ErasureConfig.TD_TARGETS; i++) {
				n.vals.remove(ErasureConfig.V_TD_PREFIX + i);
			}
			n.vals.remove(ErasureConfig.V_TD_AT);
			n.vals.remove(ErasureConfig.V_TD_STEP);
			n.vals.remove(ErasureConfig.V_TD_HANG_UNTIL);
		});
	}

	/** Tests: run the rest of it now (every hop, the hang, the finish). */
	public static void completeForTest(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return;
		}
		while (s.step < s.chain.size()) {
			hop(p, s, s.step);
			s.step++;
		}
		startHang(p, s, p.level().getGameTime());
		finish(p);
	}

	/** Tests: one more hop now. */
	public static void hopForTest(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.step < s.chain.size()) {
			hop(p, s, s.step);
			s.step++;
		}
	}
}
