package com.projecthero.mod.quirk.zerogravity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.quirk.QuirkThreat;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.quirk.fx.QuirkParticles;
import com.projecthero.mod.quirk.meter.QuirkMeters;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Quirks revamp (docs/QUIRKS_REVAMP_PROPOSAL.md 3.7): Uravity's Plus Ultra ultimate, <b>Zero Gravity -- Full Release</b>.
 * Hold C at a full gauge:
 * <ol>
 *   <li>fingertips spread: every mob she may harm, every dropped item and the loose ground within
 *       {@link ZeroGravityConfig#FR_RADIUS} float up (bosses: rooted and lifted {@link ZeroGravityConfig#FR_BOSS_LIFT}), a pink
 *       dome bursting out round her, the prints on everything;</li>
 *   <li>after {@link ZeroGravityConfig#FR_FLOAT_TICKS} she claps her fingertips together -- "Release!" -- and it all crashes
 *       down: each creature is slammed into the ground ({@link ZeroGravityConfig#FR_SLAM_DAMAGE} plus its fall), the rubble rains
 *       on them as a Meteor Shower, a dust shockwave;</li>
 *   <li>the drawback: Nausea 100, she is sick ({@link ZeroGravityMoves#sick}).</li>
 * </ol>
 * Nausea does not build while it runs (the drawback is the whole cost). A boss's gravity is put back whatever happens
 * ({@link #stop}, and the float tag's safety net on reload). One session per user, entity ids only; dropped by {@link #forget}
 * / {@link #clearSessionState}.
 */
public final class ZeroGravityUltimate {
	private static final class Session {
		final long start;
		final List<Integer> bosses = new ArrayList<>();
		final List<Double> bossY = new ArrayList<>();
		final List<Integer> bodies = new ArrayList<>();
		final List<Integer> slams = new ArrayList<>();
		long clapAt;
		long sickAt;

		Session(long start) {
			this.start = start;
			this.clapAt = start + ZeroGravityConfig.FR_FLOAT_TICKS;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private ZeroGravityUltimate() {
	}

	static void clearSessionState() {
		SESSIONS.clear();
	}

	static void forget(UUID id) {
		SESSIONS.remove(id);
	}

	public static boolean running(ServerPlayer p) {
		return SESSIONS.containsKey(p.getUUID());
	}

	/** The creatures it floats (tests). */
	public static List<Integer> bodies(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? List.of() : List.copyOf(s.bodies);
	}

	/** Hold C at 100 (the gauge is already spent): everything round her floats up. Returns how many things float. */
	public static int fire(ServerPlayer p) {
		stop(p);
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		Session s = new Session(now);
		SESSIONS.put(p.getUUID(), s);
		Quirks.setAnim(p, ZeroGravity.ANIM_FULL_RELEASE);
		Quirks.setVal(p, ZeroGravity.V_FULL, s.clapAt);
		double r = ZeroGravityConfig.FR_RADIUS;
		int n = 0;
		for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(r), e -> e.isAlive() && (e instanceof LivingEntity || e instanceof ItemEntity))) {
			if (e.distanceToSqr(p) > r * r) {
				continue;
			}
			if (e instanceof LivingEntity le && ZeroGravityCombat.isBoss(le)) {
				if (ZeroGravityCombat.isTarget(p, le)) {
					liftBoss(p, s, le);
					n++;
				}
				continue;
			}
			if (ZeroGravityFloat.floatedBy(p, e) || ZeroGravityFloat.start(p, e, true, ZeroGravityConfig.FR_RISE)) {
				ZeroGravityFloat.liftTo(p, e, ZeroGravityConfig.FR_RISE, 0.3);
				if (e instanceof LivingEntity) {
					s.bodies.add(e.getId());
				}
				n++;
			}
		}
		n += ZeroGravityDebris.liftAround(p, r, ZeroGravityConfig.FR_PIECES);
		// the pink dome bursting out, rings climbing it
		Vec3 c = p.position();
		QuirkFx.blast(level, c.add(0, 1.0, 0)).colours(0xFFFFFF, 0xFF8FC8).size(5.5f).life(20).flags(QuirkFx.F_NO_SMOKE).spawn();
		QuirkFx.ring(level, c.add(0, 0.05, 0)).colours(0xFFFFFF, 0xFF8FC8).size((float) r * 2f).thickness(1.0f).life(22).ground().spawn();
		for (int i = 1; i <= 4; i++) {
			double h = i * 2.2;
			float rr = (float) Math.sqrt(Math.max(1.0, r * r * 0.35 - h * h)) * 1.4f;
			QuirkFx.ring(level, c.add(0, h, 0)).style(QuirkFx.RING_SOFT).colours(0xFFE6F2, 0xFF8FC8).size(rr).thickness(0.5f).life(14 + i * 3).spawn();
		}
		level.sendParticles(QuirkParticles.PINK_SPARKLE, c.x, c.y + 1.2, c.z, 60, 4.0, 2.0, 4.0, 0.06);
		ZeroGravitySounds.play(level, c, ZeroGravitySounds.FLOAT, 1.5f, 0.7f);
		ZeroGravitySounds.play(level, c, ZeroGravitySounds.METEOR, 1.2f, 0.6f);
		QuirkCamera.shakeAround(level, c, 24, 0.35f, 16);
		ZeroGravityMoves.syncCount(p);
		return n;
	}

	private static void liftBoss(ServerPlayer p, Session s, LivingEntity boss) {
		boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ZeroGravityConfig.FR_FLOAT_TICKS + 10, 6, false, false, false));
		if (!boss.isNoGravity()) {
			boss.setNoGravity(true);
			boss.addTag(ZeroGravity.TAG_FLOAT);
			s.bosses.add(boss.getId());
			s.bossY.add(boss.getY() + ZeroGravityConfig.FR_BOSS_LIFT);
		}
		QuirkThreat.add(boss, p, 25f);
		ZeroGravityCombat.touchFx(p.serverLevel(), ZeroGravityCombat.center(boss));
	}

	static void tick(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return;
		}
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		if (s.sickAt != 0L) {
			watchSlams(p, s);
			if (now >= s.sickAt) {
				SESSIONS.remove(p.getUUID());
				restoreBosses(p, s);
				Quirks.setVal(p, ZeroGravity.V_FULL, 0L);
				Quirks.setMeter(p, 100f);
				ZeroGravityMoves.sick(p);
			}
			return;
		}
		// the float phase: bosses rise into their 3 blocks, held there
		for (int i = 0; i < s.bosses.size(); i++) {
			if (level.getEntity(s.bosses.get(i)) instanceof LivingEntity b && b.isAlive()) {
				double dy = s.bossY.get(i) - b.getY();
				b.setDeltaMovement(0.0, Math.max(-0.1, Math.min(0.2, dy * 0.2)), 0.0);
				b.hurtMarked = true;
				if (b instanceof Mob m) {
					m.getNavigation().stop();
				}
			}
		}
		if (now % 5 == 0) {
			level.sendParticles(QuirkParticles.PINK_SPARKLE, p.getX(), p.getY() + 1.4, p.getZ(), 4, 0.6, 0.4, 0.6, 0.02);
		}
		if (now >= s.clapAt) {
			clap(p, s);
		}
	}

	/** "Release!": the clap -- everything crashes down. */
	static void clap(ServerPlayer p, Session s) {
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		Quirks.setAnim(p, ZeroGravity.ANIM_CLAP);
		ZeroGravityMoves.releaseFx(p);
		ZeroGravitySounds.play(level, p.position(), ZeroGravitySounds.RELEASE, 1.6f, 0.8f);
		for (int id : s.bodies) {
			Entity e = level.getEntity(id);
			if (e instanceof LivingEntity le && le.isAlive() && ZeroGravityFloat.release(p, le)) {
				Vec3 v = le.getDeltaMovement();
				le.setDeltaMovement(v.x * 0.2, -ZeroGravityConfig.FR_SLAM_SPEED, v.z * 0.2);
				le.hurtMarked = true;
				s.slams.add(id);
			}
		}
		// everything else she floats (items) just drops
		ZeroGravityFloat.releaseAll(p, false);
		restoreBosses(p, s);
		for (int id : s.bosses) {
			if (level.getEntity(id) instanceof LivingEntity b && b.isAlive()) {
				b.setDeltaMovement(0.0, -ZeroGravityConfig.FR_SLAM_SPEED, 0.0);
				b.hurtMarked = true;
				s.slams.add(id);
			}
		}
		ZeroGravityDebris.crashLifted(p);
		QuirkCamera.preset(p, QuirkCamera.Preset.FINISHER);
		QuirkCamera.shakeAround(level, p.position(), 28, 0.6f, 18);
		s.sickAt = now + ZeroGravityConfig.FR_SICK_DELAY;
		Quirks.setVal(p, ZeroGravity.V_FULL, 0L);
		ZeroGravityMoves.syncCount(p);
	}

	private static void watchSlams(ServerPlayer p, Session s) {
		ServerLevel level = p.serverLevel();
		float mult = QuirkMeters.damageMult(p);
		for (Iterator<Integer> it = s.slams.iterator(); it.hasNext();) {
			Entity e = level.getEntity(it.next());
			if (!(e instanceof LivingEntity le) || !le.isAlive()) {
				it.remove();
				continue;
			}
			if (le.onGround() || le.verticalCollisionBelow) {
				it.remove();
				le.invulnerableTime = 0;
				ZeroGravityCombat.strike(p, le, le.position(), ZeroGravityConfig.FR_SLAM_DAMAGE * mult, 0.0, 0.0);
				BlockPos under = le.blockPosition().below();
				ZeroGravityMoves.impactFx(p, le.position(), level.getBlockState(under), 2.6f);
			}
		}
	}

	private static void restoreBosses(ServerPlayer p, Session s) {
		ServerLevel level = p.serverLevel();
		for (int id : s.bosses) {
			Entity b = level.getEntity(id);
			if (b != null) {
				b.setNoGravity(false);
				b.removeTag(ZeroGravity.TAG_FLOAT);
			}
		}
		s.bosses.clear();
		s.bossY.clear();
	}

	/** Ends it (stopAll, a new fire): every boss gets its gravity back; nothing else (the floats are let go by the caller). */
	static void stop(ServerPlayer p) {
		Session s = SESSIONS.remove(p.getUUID());
		if (s != null) {
			restoreBosses(p, s);
		}
		if (Quirks.hasPower(p) && Quirks.state(p).val(ZeroGravity.V_FULL) != 0L) {
			Quirks.setVal(p, ZeroGravity.V_FULL, 0L);
		}
	}

	/** Tests: the clap now. */
	public static void clapForTest(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.sickAt == 0L) {
			clap(p, s);
		}
	}

	/** Tests: the sickness now. */
	public static void finishForTest(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			s.sickAt = p.level().getGameTime();
			tick(p);
		}
	}
}
