package com.projecthero.mod.saiyan.kit;

import static com.projecthero.mod.saiyan.kit.SaiyanKit.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.beamclash.BeamClash;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.saiyan.kit.SaiyanKit.Run;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * v0.17.3 Saiyan revamp, the Ki half of the kit:
 * <ul>
 *   <li>G (hold), {@code ki_blast}: Ki Blast Volley -- glowing spheres from alternating hands, 4 a second, each a small blast.</li>
 *   <li>Shift+G (hold, release), {@code beam}: Kamehameha -- cupped hands at his side gather a ball of Ki; let go and a big blue
 *       beam fires. It beam-clashes ({@link BeamClash#report} every live tick).</li>
 *   <li>C, {@code aoe}: Explosive Wave -- a 360 degree shockwave round him.</li>
 *   <li>Shift+C, {@code aoe2}: Ki Rain -- leap up and rain blasts onto the area at the crosshair.</li>
 *   <li>Shift+V, {@code utility2}: Solar Flare -- a blinding flash for everyone facing him; mobs lose their target.</li>
 * </ul>
 * Ki spheres are simulated here (not entities) and drawn by every client from {@link SaiyanKitFxPayload#SHOT}. No block is
 * ever changed.
 */
final class SaiyanKitKi {
	/** A Ki sphere in flight. Holds its level: dropped by {@link #clearSessionState}. */
	static final class Shot {
		final int id;
		final UUID owner;
		final ServerLevel level;
		Vec3 pos;
		final Vec3 vel;
		final boolean rain;
		final Vec3 aim;
		final int life;
		final float size;
		int age;

		Shot(int id, UUID owner, ServerLevel level, Vec3 pos, Vec3 vel, boolean rain, Vec3 aim, int life, float size) {
			this.id = id;
			this.owner = owner;
			this.level = level;
			this.pos = pos;
			this.vel = vel;
			this.rain = rain;
			this.aim = aim;
			this.life = life;
			this.size = size;
		}
	}

	/** A mob the Solar Flare blinded: it keeps losing its target until {@code until}. */
	record Flared(UUID owner, Mob mob, long until) {
	}

	private static final List<Shot> SHOTS = new ArrayList<>();
	private static final List<Flared> FLARED = new ArrayList<>();
	private static int nextShot = 1;

	private SaiyanKitKi() {
	}

	static void clearSessionState() {
		synchronized (SHOTS) {
			SHOTS.clear();
		}
		synchronized (FLARED) {
			FLARED.clear();
		}
		nextShot = 1;
	}

	static void forget(UUID id) {
		synchronized (SHOTS) {
			SHOTS.removeIf(s -> s.owner.equals(id));
		}
		synchronized (FLARED) {
			FLARED.removeIf(f -> f.owner().equals(id));
		}
	}

	/** Spheres in flight (tests). */
	static int shotsInFlight() {
		synchronized (SHOTS) {
			return SHOTS.size();
		}
	}

	static void serverTick(MinecraftServer server) {
		tickShots(server);
		tickFlared();
	}

	// ---------------------------------------------------------------- aiming

	private static float side(Player p, int hand) {
		float main = p.getMainArm() == HumanoidArm.RIGHT ? 1f : -1f;
		return hand % 2 == 0 ? main : -main;
	}

	/** One hand in front of his chest: {@code side} +1 his right, -1 his left. */
	static Vec3 handPos(Player p, float side) {
		float yaw = (float) Math.toRadians(p.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		return p.getEyePosition().add(0, -0.35, 0).add(p.getLookAngle().scale(0.6)).add(right.scale(0.35 * side));
	}

	/** What the crosshair rests on within {@code range}: a block face, a creature, or the far point. */
	static Vec3 crosshair(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 far = eye.add(p.getLookAngle().scale(range));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity && e != p && e.isAlive() && e.isPickable(), eye.distanceToSqr(end));
		if (ehr != null) {
			return ehr.getEntity().position().add(0, ehr.getEntity().getBbHeight() * 0.55, 0);
		}
		return end;
	}

	private static Shot launch(ServerPlayer p, Vec3 from, Vec3 vel, boolean rain, Vec3 aim, int life, float size) {
		Shot s = new Shot(nextShot++, p.getUUID(), level(p), from, vel, rain, aim, life, size);
		synchronized (SHOTS) {
			SHOTS.add(s);
		}
		fx(p, SaiyanKitFxPayload.SHOT, p.getId(), from, vel, size, kiColor(form(p)), s.id);
		return s;
	}

	// ---------------------------------------------------------------- G: Ki Blast Volley

	static void volley(ServerPlayer p) {
		Run r = new Run(KI_BLAST, p.level().getGameTime(), false);
		if (!fireBlast(p, r)) {
			return;
		}
		begin(p, r);
	}

	static void tickVolley(ServerPlayer p, Run r, long now) {
		if (!r.held || now - r.start > 300) {
			end(p, r);
			return;
		}
		if (now - r.lastShot >= SaiyanKitConfig.BLAST_EVERY && !fireBlast(p, r)) {
			end(p, r);
		}
	}

	private static boolean fireBlast(ServerPlayer p, Run r) {
		if (!needKi(p, SaiyanKitConfig.BLAST_KI)) {
			return false;
		}
		long now = p.level().getGameTime();
		int hand = r.shotHand++;
		Vec3 from = handPos(p, side(p, hand));
		Vec3 aim = crosshair(p, SaiyanKitConfig.BLAST_SPEED * SaiyanKitConfig.BLAST_LIFE);
		Vec3 dir = aim.subtract(from);
		dir = dir.lengthSqr() < 0.01 ? p.getLookAngle() : dir.normalize();
		launch(p, from, dir.scale(SaiyanKitConfig.BLAST_SPEED), false, null, SaiyanKitConfig.BLAST_LIFE, SaiyanKitConfig.BLAST_SIZE);
		anim(p, hand % 2 == 0 ? ANIM_BLAST_R : ANIM_BLAST_L);
		sound(p, SaiyanKitSounds.KI_SHOT, 0.35f, 0.95f + p.getRandom().nextFloat() * 0.12f);
		r.lastShot = now;
		cooldown(p, KI_BLAST, SaiyanKitConfig.BLAST_EVERY);
		return true;
	}

	// ---------------------------------------------------------------- Shift+G: Kamehameha

	/** Where the beam leaves his hands: chest height, just in front. */
	public static Vec3 beamFrom(Player p) {
		return p.getEyePosition().add(0, -0.45, 0).add(p.getLookAngle().scale(0.8));
	}

	static void beamStart(ServerPlayer p) {
		if (!needKi(p, SaiyanKitConfig.BEAM_START_KI)) {
			return;
		}
		Run r = new Run(BEAM, p.level().getGameTime(), true);
		r.kiPaid = SaiyanKitConfig.BEAM_START_KI;
		begin(p, r);
		fx(p, SaiyanKitFxPayload.BEAM_CHARGE, p.getId(), Vec3.ZERO, Vec3.ZERO, SaiyanKitConfig.BEAM_FULL_CHARGE, kiColor(form(p)), 0);
		sound(p, SaiyanKitSounds.BEAM_CHARGE, 0.55f, 1.0f);
	}

	/** The beam's total damage on one creature at charge {@code c} (0..1), before the damage multiplier. */
	static float beamDamage(float c) {
		return SaiyanKitConfig.BEAM_MIN_DAMAGE + (SaiyanKitConfig.BEAM_MAX_DAMAGE - SaiyanKitConfig.BEAM_MIN_DAMAGE) * c;
	}

	/** How hard the beam pushes in a clash (an ordinary beam is 1). */
	static float beamPower(Player p, float c) {
		return com.projecthero.mod.saiyan.Saiyan.damageMult(p, false) * (0.7f + 0.6f * c);
	}

	static void tickBeam(ServerPlayer p, Run r, long now) {
		long age = now - r.start;
		int rgb = kiColor(form(p));
		if (r.fired < 0) {
			if (r.stage < SaiyanKitConfig.BEAM_FULL_CHARGE) {
				// the charge only grows while there is Ki to pay for it (stage = ticks paid for)
				float per = (SaiyanKitConfig.BEAM_MAX_KI - SaiyanKitConfig.BEAM_START_KI) / SaiyanKitConfig.BEAM_FULL_CHARGE;
				if (spendQuiet(p, per)) {
					r.kiPaid += per;
					r.stage++;
					r.charge = Math.min(1f, r.stage / (float) SaiyanKitConfig.BEAM_FULL_CHARGE);
				}
			}
			p.setDeltaMovement(p.getDeltaMovement().multiply(0.45, 1.0, 0.45));
			p.hurtMarked = true;
			if (age % 8 == 0) {
				Vec3 at = beamFrom(p);
				level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 2, 0.15, 0.15, 0.15, 0.02);
			}
			if ((!r.held && age >= SaiyanKitConfig.BEAM_MIN_CHARGE) || age >= SaiyanKitConfig.BEAM_FULL_CHARGE + SaiyanKitConfig.BEAM_MAX_HOLD) {
				r.fired = now;
				anim(p, ANIM_KAME_FIRE);
				fx(p, SaiyanKitFxPayload.BEAM_FIRE, p.getId(), Vec3.ZERO, Vec3.ZERO, r.charge, rgb, SaiyanKitConfig.BEAM_FIRE_TICKS);
				sound(p, SaiyanKitSounds.BEAM_FIRE, 0.8f, 1.05f - 0.1f * r.charge);
			}
			return;
		}
		long fireAge = now - r.fired;
		if (fireAge >= SaiyanKitConfig.BEAM_FIRE_TICKS) {
			beamEnd(p, r, true);
			return;
		}
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.15, 0.6, 0.15));
		p.hurtMarked = true;
		p.resetFallDistance();
		Vec3 from = beamFrom(p);
		Vec3 look = p.getLookAngle();
		BeamClash.report(p, from, look, SaiyanKitConfig.BEAM_RANGE, beamPower(p, r.charge), rgb);
		// pulses on fire ticks 1, 7, 13, 19, 25: all five land inside the 30-tick beam
		if (fireAge % SaiyanKitConfig.BEAM_PULSE_EVERY != 1 || BeamClash.inClash(p)) {
			return;
		}
		Vec3 far = from.add(look.scale(SaiyanKitConfig.BEAM_RANGE));
		BlockHitResult bhr = p.level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		double width = 0.6 + 0.5 * r.charge;
		float pulse = beamDamage(r.charge) / SaiyanKitConfig.BEAM_PULSES;
		// it is a big beam: everyone it passes through, plus a splash where it meets a wall
		Set<LivingEntity> hit = new HashSet<>();
		for (LivingEntity e : HeroTargets.harmable(p.level(), p, new AABB(from, end).inflate(width + 1.0))) {
			if (e == p || !e.isAlive()) {
				continue;
			}
			Optional<Vec3> at = e.getBoundingBox().inflate(width).clip(from, end);
			if (at.isPresent() || e.getBoundingBox().inflate(width).contains(from)) {
				hit.add(e);
			}
		}
		if (bhr.getType() != HitResult.Type.MISS) {
			hit.addAll(around(p, end, width + 1.0));
		}
		for (LivingEntity e : hit) {
			int n = r.pulses.getOrDefault(e.getId(), 0);
			if (n >= SaiyanKitConfig.BEAM_PULSES) {
				continue;
			}
			if (strike(p, e, from, pulse, 0.3, 0.0)) {
				r.pulses.put(e.getId(), n + 1);
				r.hits++;
			}
		}
		if (fireAge % (SaiyanKitConfig.BEAM_PULSE_EVERY * 2) == 1) {
			soundAt(level(p), end, SaiyanKitSounds.BLAST, 0.3f, 0.9f);
		}
	}

	static void beamEnd(ServerPlayer p, Run r, boolean cool) {
		fx(p, SaiyanKitFxPayload.BEAM_END, p.getId(), Vec3.ZERO, Vec3.ZERO, 0f, 0, 0);
		anim(p, ANIM_NONE);
		if (cool) {
			cooldown(p, BEAM, SaiyanKitConfig.BEAM_COOLDOWN);
		}
		end(p, r);
	}

	/** The Ki the running Kamehameha has spent so far (tests). */
	static float beamKiPaid(Player p) {
		Run r = RUNS.get(p.getUUID());
		return r == null ? 0f : r.kiPaid;
	}

	// ---------------------------------------------------------------- C: Explosive Wave

	static void wave(ServerPlayer p) {
		if (!needKi(p, SaiyanKitConfig.WAVE_KI)) {
			return;
		}
		cooldown(p, AOE, SaiyanKitConfig.WAVE_COOLDOWN);
		begin(p, new Run(AOE, p.level().getGameTime(), true));
		anim(p, ANIM_WAVE);
		Vec3 c = p.position().add(0, 1.0, 0);
		for (LivingEntity e : around(p, c, SaiyanKitConfig.WAVE_RADIUS)) {
			if (strike(p, e, p.position(), SaiyanKitConfig.WAVE_DAMAGE, SaiyanKitConfig.WAVE_KNOCKBACK, SaiyanKitConfig.WAVE_LIFT)) {
				impact(p, e, 0.8f);
			}
		}
		fx(p, SaiyanKitFxPayload.DOME, p.getId(), c, Vec3.ZERO, (float) SaiyanKitConfig.WAVE_RADIUS, kiColor(form(p)), 0);
		sound(p, SaiyanKitSounds.WAVE, 0.7f, 1.0f);
	}

	// ---------------------------------------------------------------- Shift+C: Ki Rain

	/** Where Ki Rain falls: the block at the crosshair, else the ground under the far point. */
	static Vec3 rainTarget(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 far = eye.add(p.getLookAngle().scale(SaiyanKitConfig.RAIN_REACH));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (bhr.getType() != HitResult.Type.MISS) {
			return bhr.getLocation();
		}
		BlockHitResult down = p.level().clip(new ClipContext(far, far.add(0, -SaiyanKitConfig.RAIN_REACH, 0), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, p));
		return down.getType() == HitResult.Type.MISS ? far : down.getLocation();
	}

	private static int rainTicks() {
		return SaiyanKitConfig.RAIN_FIRE_AT + SaiyanKitConfig.RAIN_EVERY * SaiyanKitConfig.RAIN_SHOTS + 4;
	}

	static void rain(ServerPlayer p) {
		Vec3 aim = rainTarget(p);
		if (!needKi(p, SaiyanKitConfig.RAIN_KI)) {
			return;
		}
		cooldown(p, AOE2, SaiyanKitConfig.RAIN_COOLDOWN);
		Run r = new Run(AOE2, p.level().getGameTime(), true);
		r.aim = aim;
		begin(p, r);
		anim(p, ANIM_RAIN);
		AbilityHelpers.launchSelf(p, new Vec3(0, 1.0, 0));
		fallSafeFor(p, SaiyanKitConfig.FALL_SAFE_TICKS);
		fxAt(level(p), aim, SaiyanKitFxPayload.RAIN_MARK, p.getId(), aim, Vec3.ZERO, (float) SaiyanKitConfig.RAIN_RADIUS, kiColor(form(p)), rainTicks());
		sound(p, SaiyanKitSounds.WHOOSH, 0.45f, 0.9f);
	}

	static void tickRain(ServerPlayer p, Run r, long age) {
		p.resetFallDistance();
		if (age >= SaiyanKitConfig.RAIN_FIRE_AT - 3) {
			p.setDeltaMovement(Vec3.ZERO);
			p.hurtMarked = true;
		}
		if (age >= SaiyanKitConfig.RAIN_FIRE_AT && (age - SaiyanKitConfig.RAIN_FIRE_AT) % SaiyanKitConfig.RAIN_EVERY == 0
				&& r.stage < SaiyanKitConfig.RAIN_SHOTS) {
			List<LivingEntity> foes = around(p, r.aim, SaiyanKitConfig.RAIN_RADIUS);
			foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(r.aim)));
			Vec3 point;
			if (!foes.isEmpty()) {
				LivingEntity e = foes.get(r.stage % foes.size());
				point = e.position().add(0, e.getBbHeight() * 0.5, 0);
			} else {
				double a = p.getRandom().nextDouble() * Math.PI * 2, d = Math.sqrt(p.getRandom().nextDouble()) * SaiyanKitConfig.RAIN_RADIUS;
				point = r.aim.add(Math.cos(a) * d, 0.1, Math.sin(a) * d);
			}
			Vec3 from = handPos(p, side(p, r.stage));
			Vec3 d = point.subtract(from);
			Vec3 vel = (d.lengthSqr() < 0.01 ? new Vec3(0, -1, 0) : d.normalize()).scale(SaiyanKitConfig.RAIN_SHOT_SPEED);
			int life = (int) Math.ceil(d.length() / SaiyanKitConfig.RAIN_SHOT_SPEED) + 2;
			launch(p, from, vel, true, point, life, 0.34f);
			sound(p, SaiyanKitSounds.RAIN_SHOT, 0.25f, 1.0f + p.getRandom().nextFloat() * 0.15f);
			r.stage++;
		}
		if (age >= rainTicks()) {
			end(p, r);
			fallSafeFor(p, SaiyanKitConfig.FALL_SAFE_TICKS);
		}
	}

	// ---------------------------------------------------------------- Shift+V: Solar Flare

	/** True if {@code e} is facing {@code p} closely enough to be blinded, with nothing in between. */
	static boolean facing(LivingEntity e, Player p) {
		Vec3 to = p.getEyePosition().subtract(e.getEyePosition());
		if (to.lengthSqr() < 1.0e-4) {
			return true;
		}
		return e.getViewVector(1.0f).dot(to.normalize()) > SaiyanKitConfig.FLARE_FACING && e.hasLineOfSight(p);
	}

	static void flare(ServerPlayer p) {
		if (!needKi(p, SaiyanKitConfig.FLARE_KI)) {
			return;
		}
		cooldown(p, UTILITY2, SaiyanKitConfig.FLARE_COOLDOWN);
		begin(p, new Run(UTILITY2, p.level().getGameTime(), true));
		anim(p, ANIM_FLARE);
		Vec3 eye = p.getEyePosition();
		fx(p, SaiyanKitFxPayload.FLASH, p.getId(), eye.add(p.getLookAngle().scale(0.25)), Vec3.ZERO, (float) SaiyanKitConfig.FLARE_RANGE,
				0xFFFFF4, 0);
		sound(p, SaiyanKitSounds.VANISH, 0.5f, 1.35f);
		long until = p.level().getGameTime() + SaiyanKitConfig.FLARE_TICKS;
		for (LivingEntity e : HeroTargets.harmable(p.level(), p, p.getBoundingBox().inflate(SaiyanKitConfig.FLARE_RANGE))) {
			if (e == p || !e.isAlive() || e.distanceTo(p) > SaiyanKitConfig.FLARE_RANGE || !facing(e, p)) {
				continue;
			}
			AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, SaiyanKitConfig.FLARE_TICKS, 0);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, SaiyanKitConfig.FLARE_TICKS, 1);
			if (e instanceof Mob m) {
				dropTarget(m);
				synchronized (FLARED) {
					FLARED.add(new Flared(p.getUUID(), m, until));
				}
			}
		}
	}

	private static void tickFlared() {
		synchronized (FLARED) {
			if (FLARED.isEmpty()) {
				return;
			}
			FLARED.removeIf(f -> f.mob().isRemoved() || !f.mob().isAlive() || f.mob().level().getGameTime() > f.until());
			for (Flared f : FLARED) {
				if (f.mob().getTarget() != null) {
					dropTarget(f.mob());
				}
			}
		}
	}

	/** True while the Solar Flare keeps this mob from targeting anyone (tests). */
	static boolean flared(Mob m) {
		synchronized (FLARED) {
			for (Flared f : FLARED) {
				if (f.mob() == m) {
					return true;
				}
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- the spheres

	private static void tickShots(MinecraftServer server) {
		List<Shot> copy;
		synchronized (SHOTS) {
			if (SHOTS.isEmpty()) {
				return;
			}
			copy = new ArrayList<>(SHOTS);
		}
		for (Shot s : copy) {
			ServerPlayer owner = server.getPlayerList().getPlayer(s.owner);
			if (owner == null || owner.level() != s.level) {
				remove(s);
				continue;
			}
			s.age++;
			Vec3 next = s.pos.add(s.vel);
			boolean boom = false;
			LivingEntity direct = null;
			if (s.rain && s.pos.distanceTo(s.aim) <= s.vel.length()) {
				next = s.aim;
				boom = true;
			}
			BlockHitResult bhr = s.level.clip(new ClipContext(s.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
			if (bhr.getType() != HitResult.Type.MISS) {
				next = bhr.getLocation();
				boom = true;
			}
			double best = s.pos.distanceToSqr(next);
			for (LivingEntity e : HeroTargets.harmable(s.level, owner, new AABB(s.pos, next).inflate(s.size + 0.5))) {
				if (e == owner || !e.isAlive()) {
					continue;
				}
				Optional<Vec3> at = e.getBoundingBox().inflate(s.size).clip(s.pos, next);
				if (at.isPresent() && s.pos.distanceToSqr(at.get()) <= best) {
					best = s.pos.distanceToSqr(at.get());
					direct = e;
					next = at.get();
					boom = true;
				}
			}
			s.pos = next;
			if (boom) {
				burst(owner, s, direct);
				remove(s);
			} else if (s.age >= s.life) {
				fxAt(s.level, s.pos, SaiyanKitFxPayload.SHOT_END, owner.getId(), s.pos, Vec3.ZERO, s.size * 0.6f, kiColor(form(owner)), s.id);
				remove(s);
			}
		}
	}

	private static void remove(Shot s) {
		synchronized (SHOTS) {
			SHOTS.remove(s);
		}
	}

	private static void burst(ServerPlayer owner, Shot s, LivingEntity direct) {
		int rgb = kiColor(form(owner));
		fxAt(s.level, s.pos, SaiyanKitFxPayload.SHOT_END, owner.getId(), s.pos, Vec3.ZERO, s.rain ? 1.4f : 1.0f, rgb, s.id);
		soundAt(s.level, s.pos, SaiyanKitSounds.BLAST, s.rain ? 0.26f : 0.3f, (s.rain ? 1.15f : 1.0f) + owner.getRandom().nextFloat() * 0.15f);
		if (s.rain) {
			for (LivingEntity e : around(owner, s.pos, SaiyanKitConfig.RAIN_SPLASH)) {
				strike(owner, e, s.pos, SaiyanKitConfig.RAIN_DAMAGE, 0.2, 0.05);
			}
			return;
		}
		if (direct != null) {
			strike(owner, direct, s.pos.subtract(s.vel), SaiyanKitConfig.BLAST_DAMAGE, 0.25, 0.0);
		}
		for (LivingEntity e : around(owner, s.pos, SaiyanKitConfig.BLAST_SPLASH_RADIUS)) {
			if (e != direct) {
				strike(owner, e, s.pos, SaiyanKitConfig.BLAST_SPLASH_DAMAGE, 0.15, 0.0);
			}
		}
	}
}
