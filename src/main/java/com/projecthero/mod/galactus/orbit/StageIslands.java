package com.projecthero.mod.galactus.orbit;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.galactus.GalactusCombat;
import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.GalactusEvent;
import com.projecthero.mod.galactus.GalactusFx;
import com.projecthero.mod.quirk.fx.QuirkCamera;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Galactus raid v2, <b>Stage 1: the Shattered Orbit</b> (the friend's notes). The party stands on meteor islands;
 * the Devourer -- 180 blocks tall, leaning in over the field -- smashes them: an island glows red for 3 s, his fist comes
 * down and it shatters into flying rock (it re-forms 20 s later). Then the fist <b>stays stuck</b> for 4 s, its own
 * hit-box: damage to it fills the <b>Stagger</b> bar; three staggers (a bigger bar each time, bigger for bigger parties)
 * end the stage. After the first stagger he smashes two islands at once; after the second he adds a backhand sweep along
 * a row. Falling, flying and the pads are the raid-wide rules ({@link OrbitRules}). Saved with the event.
 */
public final class StageIslands {
	public static final int IDLE = 0, WIND = 1, STRIKE = 2, STUCK = 3, RECOIL = 4, SWEEP_MARK = 5, SWEEP = 6, BREATHER = 7;

	public final List<OrbitIsland> islands = new ArrayList<>();
	public final List<OrbitArena.Pad> pads = new ArrayList<>();
	public int staggers;
	public double stagger;
	public int state = IDLE;
	public long stateAt;
	public int cooldown = 80;
	/** The islands this attack is on (one or two), the sweep's row. */
	public int targetR = -1, targetL = -1;
	public Vec3 sweepFrom = Vec3.ZERO, sweepTo = Vec3.ZERO;
	public int moves;

	/** Where he stands: {@link #BODY_R} blocks out from the origin at {@link #bodyAngle} (he drifts round toward the party), {@link #BODY_Y} below the plane. */
	public static final double BODY_R = 78.0;
	public static final double BODY_Y = -125.0;
	public double bodyAngle = Math.PI / 2;

	/** His feet for {@code angle}. */
	public static Vec3 bodyAt(BlockPos o, double angle) {
		return new Vec3(o.getX() + 0.5 + Math.cos(angle) * BODY_R, o.getY() + BODY_Y, o.getZ() + 0.5 + Math.sin(angle) * BODY_R);
	}

	/** The yaw that faces the origin from {@code angle}. */
	public static float facing(double angle) {
		return (float) (Mth.atan2(Math.cos(angle), -Math.sin(angle)) * Mth.RAD_TO_DEG);
	}

	/** Can his fist reach {@code target} (leaning as far as he can)? */
	public static boolean reachable(DevourerEntity d, Vec3 target) {
		float k = GalactusRig.scale(d.height());
		double h = Math.sqrt(target.distanceToSqr(d.getX(), target.y, d.getZ()));
		double dy = target.y - d.feet().y;
		double above = (90.9 - 62.5) * k, waist = 62.5 * k, lean = 0.85;
		double fwd = Math.sin(lean) * above, sy = waist + Math.cos(lean) * above;
		return Math.hypot(h - fwd, dy - sy) <= GalactusRig.reach(d.height()) * 0.97;
	}

	/** Between moves he drifts round the field toward where the party stands. */
	void drift(GalactusEvent ev, DevourerEntity d, List<ServerPlayer> fighters) {
		BlockPos o = GalactusOrbit.origin(ev.orbitSlot());
		double cx = 0, cz = 0;
		for (ServerPlayer p : fighters) {
			cx += p.getX() - (o.getX() + 0.5);
			cz += p.getZ() - (o.getZ() + 0.5);
		}
		if (!fighters.isEmpty() && cx * cx + cz * cz > 16 * fighters.size() * fighters.size()) {
			double want = Math.atan2(cz, cx);
			double diff = Mth.wrapDegrees((want - bodyAngle) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
			bodyAngle += Mth.clamp(diff, -0.004, 0.004);
		}
		place(o, d);
	}

	public void place(BlockPos o, DevourerEntity d) {
		Vec3 at = bodyAt(o, bodyAngle);
		d.setFeet(at.x, at.y, at.z);
		d.setFacing(facing(bodyAngle));
	}

	// ---------------------------------------------------------------- setup

	public void build(GalactusEvent ev, ServerLevel orbit, BlockPos o, int fighters) {
		GalactusConfig.Stage1 c = GalactusConfig.stage1();
		islands.clear();
		int outer = Math.min(12, c.outerIslands + c.outerPerExtraFighter * Math.max(0, fighters - 1));
		islands.addAll(OrbitIsland.field(o.getX(), o.getZ(), outer, ev.id().getLeastSignificantBits() ^ 0x5A17L));
		for (OrbitIsland i : islands) {
			i.build(orbit);
		}
		pads.clear();
		// the Anchor throws you to the far ring; each far island throws you home
		for (OrbitIsland i : islands) {
			if (i.index >= 7) {
				Vec3 dir = new Vec3(i.x - o.getX(), 0, i.z - o.getZ()).normalize();
				BlockPos fromAnchor = new BlockPos(o.getX() + (int) Math.round(dir.x * 4), GalactusOrbit.PLANE_Y, o.getZ() + (int) Math.round(dir.z * 4));
				if (pads.stream().noneMatch(p -> p.at().distSqr(fromAnchor) < 9)) {
					pads.add(new OrbitArena.Pad(fromAnchor, i.top()));
				}
				BlockPos fromOuter = new BlockPos(i.x - (int) Math.round(dir.x * 2), GalactusOrbit.PLANE_Y, i.z - (int) Math.round(dir.z * 2));
				pads.add(new OrbitArena.Pad(fromOuter, islands.get(0).top().add(dir.scale(-1))));
			}
		}
		for (OrbitArena.Pad p : pads) {
			OrbitArena.buildPad(orbit, p.at());
		}
	}

	/** Puts the pads back on an island that just re-formed. */
	void repad(ServerLevel orbit, OrbitIsland i) {
		for (OrbitArena.Pad p : pads) {
			if (i.holds(Vec3.atCenterOf(p.at()), 0.5)) {
				OrbitArena.buildPad(orbit, p.at());
			}
		}
	}

	public double staggerNeeded(int fighters) {
		GalactusConfig.Stage1 c = GalactusConfig.stage1();
		return (c.staggerHealth + c.staggerPerExtraFighter * Math.max(0, fighters - 1)) * Math.pow(c.staggerGrowth, staggers);
	}

	public OrbitIsland anchor() {
		return islands.isEmpty() ? null : islands.get(0);
	}

	/** The nearest standing island to {@code at} that is safe to land on (not marked), the Anchor as a last resort. */
	public OrbitIsland safest(Vec3 at) {
		OrbitIsland best = null;
		double bd = Double.MAX_VALUE;
		for (OrbitIsland i : islands) {
			if (i.state != OrbitIsland.INTACT) {
				continue;
			}
			double d = i.top().distanceToSqr(at.x, i.top().y, at.z);
			if (d < bd) {
				bd = d;
				best = i;
			}
		}
		return best != null ? best : anchor();
	}

	// ---------------------------------------------------------------- the tick

	public void tick(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, long now) {
		GalactusConfig.Stage1 c = GalactusConfig.stage1();
		List<ServerPlayer> fighters = ev.orbitFighters(orbit);
		tickIslands(orbit, now);
		if (d == null) {
			return;
		}
		long in = now - stateAt;
		Vec3 home = restLook(ev);
		switch (state) {
			case IDLE -> {
				ease(d, null, null, 0f);
				drift(ev, d, fighters);
				LivingEntity target = ev.devourerTarget(orbit, d);
				d.setLook(target != null ? target.getEyePosition() : home);
				if (--cooldown <= 0 && !fighters.isEmpty()) {
					begin(ev, orbit, d, now);
				}
			}
			case WIND -> {
				float k = (float) Mth.clamp(in / (double) c.windTicks, 0, 1);
				Vec3 tr = raised(ev, d, targetR), tl = raised(ev, d, targetL);
				ease(d, tr, tl, k);
				d.setLook(focus());
				markParticles(orbit, now);
				if (in % 20 == 0) {
					for (int idx : new int[] { targetR, targetL }) {
						if (idx >= 0) {
							OrbitIsland i = islands.get(idx);
							orbit.playSound(null, i.x + 0.5, GalactusOrbit.PLANE_Y + 1, i.z + 0.5, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE,
									3.0f, 0.5f + in / (float) c.windTicks * 0.3f);
						}
					}
				}
				if (in >= c.windTicks) {
					set(STRIKE, now);
				}
			}
			case STRIKE -> {
				double k = Mth.clamp(in / (double) c.strikeTicks, 0, 1);
				k = k * k;
				if (targetR >= 0) {
					d.setArm(true, raised(ev, d, targetR).lerp(impact(targetR), k), 1f);
				}
				if (targetL >= 0) {
					d.setArm(false, raised(ev, d, targetL).lerp(impact(targetL), k), 1f);
				}
				if (in >= c.strikeTicks) {
					for (int idx : new int[] { targetR, targetL }) {
						if (idx >= 0) {
							smash(ev, orbit, d, islands.get(idx));
						}
					}
					d.setFlag(DevourerEntity.FLAG_STUCK_R, targetR >= 0);
					d.setFlag(DevourerEntity.FLAG_STUCK_L, targetL >= 0);
					set(STUCK, now);
				}
			}
			case STUCK -> {
				if (in % 4 == 0) {
					for (boolean right : new boolean[] { true, false }) {
						if (right ? targetR >= 0 : targetL >= 0) {
							Vec3 f = right ? d.pose().fistR : d.pose().fistL;
							GalactusFx.forced(orbit, GalactusFx.EMBER, f.x, f.y, f.z, 10, 6, 5, 6, 0.02);
							GalactusFx.forced(orbit, ParticleTypes.ELECTRIC_SPARK, f.x, f.y + 6, f.z, 6, 6, 4, 6, 0.1);
						}
					}
				}
				if (in == 2) {
					for (ServerPlayer p : fighters) {
						p.displayClientMessage(Component.translatable("message.projecthero.galactus.fist_stuck").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
					}
				}
				if (in >= c.stuckTicks) {
					rip(ev, orbit, d);
					set(RECOIL, now);
				}
			}
			case RECOIL -> {
				float k = 1f - (float) Mth.clamp(in / (double) c.recoilTicks, 0, 1);
				d.setArm(true, targetR >= 0 ? raised(ev, d, targetR) : null, targetR >= 0 ? k : 0f);
				d.setArm(false, targetL >= 0 ? raised(ev, d, targetL) : null, targetL >= 0 ? k : 0f);
				if (in >= c.recoilTicks) {
					finishMove(d, now);
				}
			}
			case SWEEP_MARK -> {
				float k = (float) Mth.clamp(in / 30.0, 0, 1);
				ease(d, sweepFrom.add(0, 18, 0), null, k);
				d.setLook(sweepFrom.lerp(sweepTo, 0.5));
				if (in % 3 == 0) {
					GalactusFx.line(orbit, GalactusFx.EMBER, sweepFrom.add(0, 1.2, 0), sweepTo.add(0, 1.2, 0), 1.2);
				}
				if (in == 0) {
					orbit.playSound(null, sweepFrom.x, sweepFrom.y, sweepFrom.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 5.0f, 0.5f);
				}
				if (in >= 40) {
					set(SWEEP, now);
				}
			}
			case SWEEP -> {
				double k = Mth.clamp(in / 16.0, 0, 1);
				Vec3 hand = sweepFrom.lerp(sweepTo, k).add(0, 1.5, 0);
				d.setArm(true, hand.add(0, 4, 0), 1f);
				d.setFlag(DevourerEntity.FLAG_FIST_R, false);
				sweepHits(ev, orbit, d, hand);
				if (in % 2 == 0) {
					orbit.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 2.0f, 0.4f);
				}
				if (in >= 16) {
					targetR = -1;
					targetL = -1;
					set(RECOIL, now);
				}
			}
			case BREATHER -> {
				ease(d, null, null, 0f);
				drift(ev, d, fighters);
				d.setLook(home);
				float e = (float) Mth.clamp(1 - in / 60.0, 0.35, 1);
				d.setEyes(e);
				if (in >= c.breatherTicks) {
					d.setEyes(0.35f);
					set(IDLE, now);
					cooldown = 30;
				}
			}
			default -> set(IDLE, now);
		}
	}

	private Vec3 focus() {
		Vec3 a = targetR >= 0 ? islands.get(targetR).top() : null;
		Vec3 b = targetL >= 0 ? islands.get(targetL).top() : null;
		return a == null ? b : b == null ? a : a.lerp(b, 0.5);
	}

	/** Where he looks when nothing is going on: the middle of the field. */
	private Vec3 restLook(GalactusEvent ev) {
		BlockPos o = GalactusOrbit.origin(ev.orbitSlot());
		return Vec3.atCenterOf(o).add(0, 4, 0);
	}

	/** Arms and lean toward the current targets ({@code k}: how far in). */
	private void ease(DevourerEntity d, Vec3 tr, Vec3 tl, float k) {
		d.setArm(true, tr, tr == null ? 0f : k);
		d.setArm(false, tl, tl == null ? 0f : k);
		Vec3 aim = tr != null ? tr : tl;
		float lean = 0.22f;
		if (aim != null) {
			double h = Math.sqrt(aim.distanceToSqr(d.getX(), aim.y, d.getZ()));
			lean = Mth.lerp(k, 0.22f, GalactusRig.leanToward(d.height(), h, 0.8f) + 0.1f);
		}
		float cur = d.lean();
		d.setLean(cur + (lean - cur) * 0.12f);
	}

	private Vec3 impact(int idx) {
		return islands.get(idx).top().add(0, 2, 0);
	}

	/** The fist raised over island {@code idx} (null if none). */
	private Vec3 raised(GalactusEvent ev, DevourerEntity d, int idx) {
		if (idx < 0) {
			return null;
		}
		Vec3 top = islands.get(idx).top();
		Vec3 toward = new Vec3(d.getX() - top.x, 0, d.getZ() - top.z).normalize();
		return top.add(toward.scale(10)).add(0, 34, 0);
	}

	private void set(int s, long now) {
		state = s;
		stateAt = now;
	}

	private void finishMove(DevourerEntity d, long now) {
		GalactusConfig.Stage1 c = GalactusConfig.stage1();
		d.setFlag(DevourerEntity.FLAG_FIST_R, false);
		d.setFlag(DevourerEntity.FLAG_FIST_L, false);
		d.setFlag(DevourerEntity.FLAG_STUCK_R, false);
		d.setFlag(DevourerEntity.FLAG_STUCK_L, false);
		targetR = -1;
		targetL = -1;
		set(IDLE, now);
		cooldown = Math.max(c.cooldownMin, c.cooldownTicks - staggers * 14 - Math.min(moves, 8) * 2);
	}

	// ---------------------------------------------------------------- choosing a move

	private void begin(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, long now) {
		moves++;
		boolean sweep = staggers >= 2 && moves % 3 == 0;
		if (sweep && beginSweep(ev, orbit, d, now)) {
			return;
		}
		List<ServerPlayer> fighters = ev.orbitFighters(orbit);
		int first = pick(ev, orbit, d, fighters, -1);
		if (first < 0) {
			cooldown = 20;
			return;
		}
		int second = staggers >= 1 ? pick(ev, orbit, d, fighters, first) : -1;
		// the right fist takes the island on his right (he faces the field: his right is world -X when facing north)
		boolean firstRight = side(d, islands.get(first)) >= 0;
		targetR = firstRight ? first : second;
		targetL = firstRight ? second : first;
		for (int idx : new int[] { targetR, targetL }) {
			if (idx >= 0) {
				OrbitIsland i = islands.get(idx);
				i.state = OrbitIsland.MARKED;
				i.stateAt = now;
				orbit.playSound(null, i.x + 0.5, GalactusOrbit.PLANE_Y + 1, i.z + 0.5, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 4.0f, 0.6f);
			}
		}
		d.setFlag(DevourerEntity.FLAG_FIST_R, targetR >= 0);
		d.setFlag(DevourerEntity.FLAG_FIST_L, targetL >= 0);
		set(WIND, now);
	}

	/** + for his right side, - for his left. */
	private static double side(DevourerEntity d, OrbitIsland i) {
		float y = d.getYRot() * Mth.DEG_TO_RAD;
		double rx = -Mth.cos(y), rz = -Mth.sin(y);
		return (i.x + 0.5 - d.getX()) * rx + (i.z + 0.5 - d.getZ()) * rz;
	}

	/**
	 * The island to smash: never the Anchor, never one already marked or gone, never {@code not}; weighted by who stands on
	 * it (threat first, then headcount), so camping is never safe. Must be in his reach.
	 */
	private int pick(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, List<ServerPlayer> fighters, int not) {
		LivingEntity top = ev.devourerTarget(orbit, d);
		List<Integer> pool = new ArrayList<>();
		List<Double> weight = new ArrayList<>();
		for (OrbitIsland i : islands) {
			if (i.anchor() || i.state != OrbitIsland.INTACT || i.index == not) {
				continue;
			}
			if (!reachable(d, impact(i.index))) {
				continue;
			}
			double w = 1.0;
			for (ServerPlayer p : fighters) {
				if (i.holds(p.position(), 1.5)) {
					w += 4.0;
					if (p == top) {
						w += 6.0;
					}
				}
			}
			pool.add(i.index);
			weight.add(w);
		}
		if (pool.isEmpty()) {
			return -1;
		}
		double sum = 0;
		for (double w : weight) {
			sum += w;
		}
		double r = orbit.random.nextDouble() * sum;
		for (int k = 0; k < pool.size(); k++) {
			r -= weight.get(k);
			if (r <= 0) {
				return pool.get(k);
			}
		}
		return pool.get(pool.size() - 1);
	}

	private boolean beginSweep(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, long now) {
		BlockPos o = GalactusOrbit.origin(ev.orbitSlot());
		// a row across the field, perpendicular to his facing, at a random depth
		float y = d.getYRot() * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(y), 0, Mth.cos(y));
		Vec3 right = new Vec3(-Mth.cos(y), 0, -Mth.sin(y));
		double depth = (orbit.random.nextInt(3) - 1) * 15.0;
		Vec3 mid = Vec3.atCenterOf(o).add(fwd.scale(-depth)).add(0, GalactusOrbit.PLANE_Y + 1 - Vec3.atCenterOf(o).y, 0);
		boolean leftToRight = orbit.random.nextBoolean();
		sweepFrom = mid.add(right.scale(leftToRight ? -36 : 36));
		sweepTo = mid.add(right.scale(leftToRight ? 36 : -36));
		targetR = -1;
		targetL = -1;
		set(SWEEP_MARK, now);
		for (ServerPlayer p : ev.orbitFighters(orbit)) {
			p.displayClientMessage(Component.translatable("message.projecthero.galactus.sweep").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
		}
		return true;
	}

	// ---------------------------------------------------------------- hits

	private void smash(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, OrbitIsland i) {
		GalactusConfig.Stage1 c = GalactusConfig.stage1();
		Vec3 at = i.top();
		for (ServerPlayer p : ev.orbitFighters(orbit)) {
			if (i.holds(p.position(), 2.5) && Math.abs(p.getY() - at.y) < 8) {
				GalactusCombat.hit(orbit, d, p, (float) c.smashDamage);
				Vec3 away = p.position().subtract(at).multiply(1, 0, 1).normalize();
				p.setDeltaMovement(away.x * 1.2, 0.9, away.z * 1.2);
				p.hurtMarked = true;
			}
		}
		i.shatter(orbit, at.add(0, 3, 0), 70);
		orbit.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 6.0f, 0.5f);
		orbit.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 4.0f, 0.3f);
		orbit.playSound(null, at.x, at.y, at.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, 4.0f, 0.6f);
		GalactusFx.forced(orbit, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 3, 4, 2, 4, 0);
		GalactusFx.ring(orbit, GalactusFx.COSMIC, at, 10, 64);
		QuirkCamera.shakeAround(orbit, at, 60, 6f, 18);
	}

	/** The fist tears free: anyone right on it is knocked away. */
	private void rip(GalactusEvent ev, ServerLevel orbit, DevourerEntity d) {
		for (boolean right : new boolean[] { true, false }) {
			if (right ? targetR < 0 : targetL < 0) {
				continue;
			}
			Vec3 f = right ? d.pose().fistR : d.pose().fistL;
			for (ServerPlayer p : ev.orbitFighters(orbit)) {
				if (p.position().distanceTo(f) < 10) {
					Vec3 away = p.position().subtract(f).normalize();
					p.setDeltaMovement(away.x * 1.4, 0.7, away.z * 1.4);
					p.hurtMarked = true;
				}
			}
			orbit.playSound(null, f.x, f.y, f.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 4.0f, 0.4f);
		}
		d.setFlag(DevourerEntity.FLAG_STUCK_R, false);
		d.setFlag(DevourerEntity.FLAG_STUCK_L, false);
	}

	private void sweepHits(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, Vec3 hand) {
		GalactusConfig.Stage1 c = GalactusConfig.stage1();
		for (ServerPlayer p : ev.orbitFighters(orbit)) {
			if (ev.recentlyHit(p, "sweep")) {
				continue;
			}
			double dx = p.getX() - hand.x, dz = p.getZ() - hand.z;
			if (dx * dx + dz * dz < 6 * 6 && p.getY() < GalactusOrbit.PLANE_Y + 5) {
				GalactusCombat.hit(orbit, d, p, (float) c.sweepDamage);
				Vec3 dir = sweepTo.subtract(sweepFrom).normalize();
				p.setDeltaMovement(dir.x * 1.6, 0.6, dir.z * 1.6);
				p.hurtMarked = true;
				ev.markHit(p, "sweep", 20);
			}
		}
	}

	private void markParticles(ServerLevel orbit, long now) {
		if (now % 3 != 0) {
			return;
		}
		for (int idx : new int[] { targetR, targetL }) {
			if (idx < 0) {
				continue;
			}
			OrbitIsland i = islands.get(idx);
			Vec3 c = i.top().add(0, 0.2, 0);
			GalactusFx.ring(orbit, GalactusFx.EMBER, c, i.radius + 0.5, 40);
			GalactusFx.forced(orbit, net.minecraft.core.particles.ParticleTypes.FLAME, c.x, c.y, c.z, 6, i.radius * 0.6, 0.1, i.radius * 0.6, 0.0);
		}
	}

	// ---------------------------------------------------------------- the islands' own clock

	private void tickIslands(ServerLevel orbit, long now) {
		GalactusConfig.Orbit c = GalactusConfig.orbit();
		for (OrbitIsland i : islands) {
			if (i.state == OrbitIsland.GONE && now - i.stateAt >= c.reformTicks) {
				i.beginReform(orbit, 30, 60);
			} else if (i.state == OrbitIsland.REFORMING && now - i.stateAt >= 32) {
				i.finishReform(orbit);
				repad(orbit, i);
			}
		}
	}

	// ---------------------------------------------------------------- damage to the stuck fist

	/** A hit on one of his parts during Stage 1: only a stuck fist counts. True if it took. */
	public boolean onHit(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, DevourerPartEntity.Kind kind, float amount) {
		boolean stuckR = state == STUCK && targetR >= 0 && (kind == DevourerPartEntity.Kind.HAND_R || kind == DevourerPartEntity.Kind.FORE_R);
		boolean stuckL = state == STUCK && targetL >= 0 && (kind == DevourerPartEntity.Kind.HAND_L || kind == DevourerPartEntity.Kind.FORE_L);
		if (!stuckR && !stuckL) {
			return false;
		}
		int n = ev.orbitFighters(orbit).size();
		stagger += amount;
		if (stagger >= staggerNeeded(n)) {
			stagger = 0;
			staggers++;
			staggered(ev, orbit, d);
		}
		return true;
	}

	private void staggered(GalactusEvent ev, ServerLevel orbit, DevourerEntity d) {
		long now = orbit.getGameTime();
		rip(ev, orbit, d);
		finishMove(d, now);
		Vec3 face = d.pose().face;
		orbit.playSound(null, face.x, face.y, face.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8.0f, 0.4f);
		orbit.playSound(null, face.x, face.y, face.z, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 8.0f, 0.5f);
		d.setEyes(1f);
		d.setLean(0.0f);
		GalactusFx.forced(orbit, GalactusFx.COSMIC, face.x, face.y, face.z, 120, 12, 12, 12, 0.2);
		for (ServerPlayer p : ev.orbitFighters(orbit)) {
			QuirkCamera.shake(p, 7f, 24);
		}
		set(BREATHER, now);
		ev.onStaggered(orbit, staggers);
	}

	public boolean done() {
		return staggers >= GalactusConfig.stage1().staggers;
	}

	// ---------------------------------------------------------------- save

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		ListTag l = new ListTag();
		for (OrbitIsland i : islands) {
			l.add(i.save());
		}
		t.put("Islands", l);
		t.putInt("Staggers", staggers);
		t.putDouble("Stagger", stagger);
		t.putInt("Moves", moves);
		t.putDouble("Angle", bodyAngle);
		return t;
	}

	public void load(CompoundTag t) {
		islands.clear();
		ListTag l = t.getList("Islands", Tag.TAG_COMPOUND);
		for (int i = 0; i < l.size(); i++) {
			islands.add(OrbitIsland.load(l.getCompound(i)));
		}
		staggers = t.getInt("Staggers");
		stagger = t.getDouble("Stagger");
		moves = t.getInt("Moves");
		bodyAngle = t.contains("Angle") ? t.getDouble("Angle") : Math.PI / 2;
		state = IDLE;
		cooldown = 80;
	}
}
