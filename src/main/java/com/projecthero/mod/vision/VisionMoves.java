package com.projecthero.mod.vision;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.beamclash.BeamClash;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.vision.data.VisionState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Vision: every move ({@code docs/VISION_DESIGN.md}). The running moves (the beam, the lance's charge, Unleashed, the dash,
 * the dive, the grip and the throw, a punched creature flying at a wall) live in {@link Run} per player and step in {@link #tick}. The
 * static map holds creatures (the gripped / thrown / punched ones), so it is dropped by {@link #clearSessionState}
 * ({@code ServerStateReset}).
 */
public final class VisionMoves {
	/** The running moves of one player. */
	public static final class Run {
		// R: the beam
		long beamStart;
		// Shift+Z: Unleashed
		Vec3 anchor;
		// G: a punched creature (hurt again if it hits a wall)
		LivingEntity punched;
		long punchedUntil;
		// X: Phase Dash
		int dashLeft;
		Vec3 dashDir = Vec3.ZERO;
		// Shift+X: Density Dive: 0 none, 1 at a target, 2 meteor drop, 3 forward rush
		int diveMode;
		int diveTicks;
		LivingEntity diveTarget;
		Vec3 diveDir = Vec3.ZERO;
		double meteorStartY;
		// C: Synthezoid Grip
		LivingEntity gripped;
		long gripStart;
		LivingEntity thrown;
		long thrownUntil;

		/** The Density Dive mode running (0 none, 1 target, 2 meteor, 3 rush). */
		public int diveMode() {
			return diveMode;
		}
	}

	public static final int DIVE_NONE = 0;
	public static final int DIVE_TARGET = 1;
	public static final int DIVE_METEOR = 2;
	public static final int DIVE_RUSH = 3;

	private static final Map<UUID, Run> RUNS = new ConcurrentHashMap<>();

	private VisionMoves() {
	}

	static void clearSessionState() {
		RUNS.clear();
	}

	static void forget(UUID id) {
		RUNS.remove(id);
	}

	static Run run(ServerPlayer p) {
		return RUNS.computeIfAbsent(p.getUUID(), k -> new Run());
	}

	public static Run runOf(ServerPlayer p) {
		return run(p);
	}

	/** Ends every running move (death, a world change, leaving, revoke). A gripped creature is let go. */
	static void endAll(ServerPlayer p) {
		Run r = RUNS.get(p.getUUID());
		if (r != null) {
			dropGripped(p, r, false);
			r.thrown = null;
			r.punched = null;
			r.dashLeft = 0;
			r.diveMode = DIVE_NONE;
			r.diveTarget = null;
			r.anchor = null;
		}
	}

	/** Going intangible: whatever touches the world stops (the beam, Unleashed, the lance, the grip). */
	static void endForPhase(ServerPlayer p) {
		stopBeam(p);
		Run r = run(p);
		if (Vision.unleashing(p)) {
			endUnleash(p, r);
		}
		if (Vision.chargingLance(p)) {
			VisionState n = Vision.state(p).copy();
			n.lanceStart = 0L;
			Vision.save(p, n);
		}
		dropGripped(p, r, true);
		r.diveMode = DIVE_NONE;
		r.diveTarget = null;
	}

	// ================================================================ routing

	/** A press. True if the move went off. */
	public static boolean use(ServerPlayer p, String id) {
		if (!canAct(p, id)) {
			return false;
		}
		return switch (id) {
			case VisionAbilityManager.LANCE -> lance(p);
			case VisionAbilityManager.PUNCH -> punch(p);
			case VisionAbilityManager.STRIKE -> phaseStrike(p);
			case VisionAbilityManager.PULSE -> pulse(p);
			case VisionAbilityManager.UNLEASH -> unleash(p);
			case VisionAbilityManager.DASH -> dash(p);
			case VisionAbilityManager.DIVE -> dive(p);
			case VisionAbilityManager.GRIP -> grip(p);
			case VisionAbilityManager.DENSITY -> toggle(p, id, Vision.toggleDensity(p), VisionConfig.DENSITY_TOGGLE_COOLDOWN);
			case VisionAbilityManager.HOLOGRAM -> toggle(p, id, Vision.toggleHologram(p), VisionConfig.HOLOGRAM_TOGGLE_COOLDOWN);
			case VisionAbilityManager.PHASE -> toggle(p, id, Vision.toggleIntangible(p), VisionConfig.PHASE_TOGGLE_COOLDOWN);
			default -> false;
		};
	}

	/** A hold begins (R, the beam). False if refused (its release is then ignored). */
	public static boolean startHold(ServerPlayer p, String id) {
		if (VisionAbilityManager.BEAM.equals(id) && canAct(p, id)) {
			return startBeam(p);
		}
		return false;
	}

	public static void release(ServerPlayer p, String id) {
		if (VisionAbilityManager.BEAM.equals(id)) {
			stopBeam(p);
		}
	}

	private static boolean ready(ServerPlayer p, String id) {
		return Vision.cooldownRemaining(p, id) <= 0;
	}

	/** Off cooldown, and not something he can't do right now (an attack while intangible, anything but the hologram while Unleashed). */
	private static boolean canAct(ServerPlayer p, String id) {
		if (!ready(p, id)) {
			return false;
		}
		if (Vision.intangible(p) && !VisionAbilityManager.worksWhilePhased(id)) {
			Vision.say(p, "message.projecthero.vision.phased", ChatFormatting.GRAY);
			return false;
		}
		if (Vision.unleashing(p) && !VisionAbilityManager.HOLOGRAM.equals(id)) {
			return false;
		}
		return true;
	}

	private static boolean toggle(ServerPlayer p, String id, boolean done, int cooldown) {
		if (done) {
			Vision.startCooldown(p, id, cooldown);
		}
		return done;
	}

	// ================================================================ R (hold): Mind Stone Beam

	private static boolean startBeam(ServerPlayer p) {
		if (Vision.beaming(p) || !Vision.spend(p, VisionConfig.BEAM_START_COST)) {
			return false;
		}
		VisionState n = Vision.state(p).copy();
		n.beam = true;
		Vision.save(p, n);
		run(p).beamStart = Vision.now(p);
		Vision.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.35f, 1.5f);
		return true;
	}

	static void stopBeam(ServerPlayer p) {
		if (!Vision.beaming(p)) {
			return;
		}
		VisionState n = Vision.state(p).copy();
		n.beam = false;
		Vision.save(p, n);
		Vision.startCooldown(p, VisionAbilityManager.BEAM, VisionConfig.BEAM_COOLDOWN);
	}

	private static void tickBeam(ServerPlayer p, Run r, long now) {
		if (!Vision.beaming(p)) {
			return;
		}
		if (!Vision.drain(p, VisionConfig.BEAM_COST_PER_SECOND) || now - r.beamStart > VisionConfig.BEAM_MAX_TICKS) {
			stopBeam(p);
			return;
		}
		Vec3 from = Vision.stonePos(p);
		Vec3 look = p.getLookAngle();
		BeamClash.report(p, from, look, VisionConfig.BEAM_RANGE, 1.1f, Vision.GOLD);
		if ((now - r.beamStart) % VisionConfig.BEAM_HIT_INTERVAL != 0) {
			return;
		}
		Vec3 end = beamEnd(p, from, look, VisionConfig.BEAM_RANGE);
		LivingEntity target = beamTarget(p, from, end, 1.0);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 at = target != null ? target.position().add(0, target.getBbHeight() * 0.6, 0) : end;
		if (target != null && !BeamClash.inClash(p)) {
			beamHit(p, target);
		}
		level.sendParticles(Vision.dust(Vision.GOLD, 1.3f), at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.1, 0.1, 0.1, 0.01);
		if ((now - r.beamStart) % 10 == 0) {
			Vision.sound(level, from, SoundEvents.BEACON_AMBIENT, 0.5f, 2.0f);
		}
	}

	/** One beam hit: 7 and a small push along the beam. Public for the tests. */
	public static boolean beamHit(ServerPlayer p, LivingEntity target) {
		boolean hit = Vision.hit(p, target, VisionConfig.BEAM_DAMAGE);
		if (hit) {
			Vec3 push = p.getLookAngle().scale(VisionConfig.BEAM_PUSH);
			target.push(push.x, 0.02, push.z);
			target.hurtMarked = true;
		}
		return hit;
	}

	static Vec3 beamEnd(ServerPlayer p, Vec3 from, Vec3 look, double range) {
		Vec3 to = from.add(look.scale(range));
		BlockHitResult bhr = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return bhr.getType() == HitResult.Type.MISS ? to : bhr.getLocation();
	}

	static LivingEntity beamTarget(ServerPlayer p, Vec3 from, Vec3 end, double inflate) {
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, from, end, new AABB(from, end).inflate(inflate),
				e -> e != p && e.isPickable() && e instanceof LivingEntity && !(e instanceof ArmorStand) && !e.isSpectator(), from.distanceToSqr(end));
		return ehr != null && ehr.getEntity() instanceof LivingEntity le ? le : null;
	}

	/** Every harmable creature whose box (grown by {@code radius}) the segment passes through. */
	static List<LivingEntity> along(ServerPlayer p, Vec3 from, Vec3 end, double radius) {
		return p.level().getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(radius + 1.0),
				e -> e != p && e.isAlive() && !(e instanceof ArmorStand) && !e.isSpectator() && HeroTargets.canHarm(p, e)
						&& (e.getBoundingBox().inflate(radius).clip(from, end).isPresent() || e.getBoundingBox().inflate(radius).contains(from)));
	}

	// ================================================================ Shift+R: Solar Lance

	static boolean lance(ServerPlayer p) {
		if (Vision.chargingLance(p) || !Vision.spend(p, VisionConfig.LANCE_COST)) {
			return false;
		}
		VisionState n = Vision.state(p).copy();
		n.lanceStart = Vision.now(p);
		Vision.save(p, n);
		Vision.startCooldown(p, VisionAbilityManager.LANCE, VisionConfig.LANCE_COOLDOWN);
		Vision.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.4f, 0.8f);
		return true;
	}

	private static void tickLance(ServerPlayer p, long now) {
		VisionState s = Vision.state(p);
		if (s.lanceStart <= 0L) {
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		Vec3 stone = Vision.stonePos(p);
		if (now - s.lanceStart < VisionConfig.LANCE_CHARGE_TICKS) {
			level.sendParticles(Vision.dust(Vision.GOLD, 0.8f), stone.x, stone.y, stone.z, 3, 0.25, 0.25, 0.25, 0.0);
			return;
		}
		VisionState n = s.copy();
		n.lanceStart = 0L;
		Vision.save(p, n);
		fireLance(p);
	}

	/** The lance: 20 to every creature on the 48-block line (blocks stop it). Public for the tests. Returns how many it hit. */
	public static int fireLance(ServerPlayer p) {
		Vec3 from = Vision.stonePos(p);
		Vec3 look = p.getLookAngle();
		Vec3 end = beamEnd(p, from, look, VisionConfig.LANCE_RANGE);
		int n = 0;
		if (!BeamClash.inClash(p)) {
			for (LivingEntity e : along(p, from, end, VisionConfig.LANCE_WIDTH)) {
				if (Vision.hit(p, e, VisionConfig.LANCE_DAMAGE)) {
					n++;
				}
			}
		}
		ServerLevel level = (ServerLevel) p.level();
		double len = from.distanceTo(end);
		for (double d = 0.6; d < len; d += 0.45) {
			Vec3 at = from.add(look.scale(d));
			level.sendParticles(Vision.dust(Vision.GOLD, 1.4f), at.x, at.y, at.z, 1, 0.03, 0.03, 0.03, 0.0);
			if (((int) (d * 3)) % 4 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		Vision.sound(level, from, SoundEvents.TRIDENT_THUNDER.value(), 0.25f, 1.9f);
		Vision.sound(level, from, SoundEvents.BEACON_DEACTIVATE, 0.4f, 2.0f);
		return n;
	}

	// ================================================================ G: Density Punch

	static boolean punch(ServerPlayer p) {
		LivingEntity target = lookTarget(p, VisionConfig.PUNCH_REACH, 0.6);
		if (target == null || !HeroTargets.canHarm(p, target)) {
			Vision.say(p, "message.projecthero.vision.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (!Vision.spend(p, VisionConfig.PUNCH_COST)) {
			return false;
		}
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		punchHit(p, target);
		Vision.startCooldown(p, VisionAbilityManager.PUNCH, VisionConfig.PUNCH_COOLDOWN);
		return true;
	}

	/** The punch lands: 18 and a launch; a wall it hits in the next 1.5 s does 6 more. Public for the tests. */
	public static boolean punchHit(ServerPlayer p, LivingEntity target) {
		boolean hit = Vision.hit(p, target, VisionConfig.PUNCH_DAMAGE);
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : flat.normalize();
		if (hit) {
			double resist = Mth.clamp(1.0 - target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE), 0.2, 1.0);
			target.push(flat.x * VisionConfig.PUNCH_LAUNCH * resist, VisionConfig.PUNCH_LIFT, flat.z * VisionConfig.PUNCH_LAUNCH * resist);
			target.hurtMarked = true;
			Run r = run(p);
			r.punched = target;
			r.punchedUntil = Vision.now(p) + VisionConfig.PUNCH_WALL_TICKS;
		}
		ServerLevel level = (ServerLevel) p.level();
		Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(Vision.dust(Vision.GOLD, 1.2f), at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.0);
		Vision.sound(level, at, SoundEvents.ANVIL_LAND, 0.35f, 0.6f);
		Vision.sound(level, at, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8f, 0.7f);
		return hit;
	}

	private static void tickPunched(ServerPlayer p, Run r, long now) {
		LivingEntity t = r.punched;
		if (t == null) {
			return;
		}
		if (!t.isAlive() || t.isRemoved() || now > r.punchedUntil || t.level() != p.level()) {
			r.punched = null;
			return;
		}
		if (t.horizontalCollision) {
			r.punched = null;
			Vision.hit(p, t, VisionConfig.PUNCH_WALL_DAMAGE);
			ServerLevel level = (ServerLevel) p.level();
			level.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 12, 0.3, 0.4, 0.3, 0.1);
			Vision.sound(level, t.position(), SoundEvents.STONE_BREAK, 0.8f, 0.7f);
		}
	}

	// ================================================================ Shift+G: Phase Strike

	static boolean phaseStrike(ServerPlayer p) {
		LivingEntity target = lookTarget(p, VisionConfig.STRIKE_REACH, 0.6);
		if (target == null || !HeroTargets.canHarm(p, target)) {
			Vision.say(p, "message.projecthero.vision.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (!Vision.spend(p, VisionConfig.STRIKE_COST)) {
			return false;
		}
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		strikeHit(p, target);
		Vision.startCooldown(p, VisionAbilityManager.STRIKE, VisionConfig.STRIKE_COOLDOWN);
		return true;
	}

	/** His hand phases in and re-solidifies: 14 that ignores armour (indirect magic, his), and a 2 s stun. Public for the tests. */
	public static boolean strikeHit(ServerPlayer p, LivingEntity target) {
		boolean hit = Vision.hit(p, target, VisionConfig.STRIKE_DAMAGE, p.damageSources().indirectMagic(p, p));
		if (hit) {
			stun(target, VisionConfig.STRIKE_STUN_TICKS);
		}
		ServerLevel level = (ServerLevel) p.level();
		Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
		level.sendParticles(Vision.dust(Vision.GOLD, 1.0f), at.x, at.y, at.z, 16, 0.2, 0.3, 0.2, 0.0);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 10, 0.2, 0.3, 0.2, 0.2);
		Vision.sound(level, at, SoundEvents.ILLUSIONER_CAST_SPELL, 0.5f, 1.6f);
		Vision.sound(level, at, SoundEvents.PLAYER_ATTACK_CRIT, 0.8f, 0.6f);
		return hit;
	}

	/** Dazed: Slowness VII and Weakness III, and a mob forgets where it was walking. */
	static void stun(LivingEntity target, int ticks) {
		target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, true));
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 2, false, true));
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
	}

	// ================================================================ Z: Mind Stone Pulse

	static boolean pulse(ServerPlayer p) {
		if (!Vision.spend(p, VisionConfig.PULSE_COST)) {
			return false;
		}
		pulseBlast(p);
		Vision.startCooldown(p, VisionAbilityManager.PULSE, VisionConfig.PULSE_COOLDOWN);
		return true;
	}

	/** The burst: 12, a hard shove outward and Glowing to everything in 7 blocks. Public for the tests. Returns how many it hit. */
	public static int pulseBlast(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1.0, 0);
		double r = VisionConfig.PULSE_RADIUS;
		int n = 0;
		for (LivingEntity e : HeroTargets.harmable(level, p, new AABB(c, c).inflate(r))) {
			if (e == p || e instanceof ArmorStand || e.distanceToSqr(c) > (r + 0.5) * (r + 0.5)) {
				continue;
			}
			if (Vision.hit(p, e, VisionConfig.PULSE_DAMAGE)) {
				n++;
			}
			Vec3 out = e.position().subtract(p.position());
			Vec3 h = out.horizontalDistanceSqr() < 1.0e-4 ? p.getLookAngle() : new Vec3(out.x, 0, out.z).normalize();
			e.push(h.x * VisionConfig.PULSE_KNOCKBACK, 0.45, h.z * VisionConfig.PULSE_KNOCKBACK);
			e.hurtMarked = true;
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, VisionConfig.PULSE_GLOW_TICKS, 0, false, false));
		}
		for (int i = 0; i < 48; i++) {
			double a = i * (Math.PI * 2 / 48);
			for (double rr = 2.0; rr <= r; rr += 2.5) {
				level.sendParticles(Vision.dust(Vision.GOLD, 1.6f), c.x + Math.cos(a) * rr, c.y - 0.4, c.z + Math.sin(a) * rr, 1, 0.05, 0.1, 0.05, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30, 0.6, 0.6, 0.6, 0.25);
		Vision.sound(level, c, SoundEvents.BEACON_DEACTIVATE, 0.6f, 0.6f);
		Vision.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.35f, 1.5f);
		return n;
	}

	// ================================================================ Shift+Z: Mind Stone Unleashed

	static boolean unleash(ServerPlayer p) {
		if (!Vision.spend(p, VisionConfig.UNLEASH_COST)) {
			return false;
		}
		stopBeam(p);
		Run r = run(p);
		Vec3 up = p.position().add(0, p.onGround() ? 0.8 : 0.0, 0);
		r.anchor = p.level().noCollision(p, p.getBoundingBox().move(up.subtract(p.position()))) ? up : p.position();
		VisionState n = Vision.state(p).copy();
		n.unleash = true;
		n.unleashStart = Vision.now(p);
		Vision.save(p, n);
		ServerLevel level = (ServerLevel) p.level();
		Vision.sound(level, p.position(), SoundEvents.BEACON_ACTIVATE, 0.7f, 0.6f);
		Vision.sound(level, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 0.35f, 1.6f);
		return true;
	}

	private static void tickUnleash(ServerPlayer p, Run r, long now) {
		VisionState s = Vision.state(p);
		if (!s.unleash) {
			return;
		}
		long age = now - s.unleashStart;
		if (age >= VisionConfig.UNLEASH_TICKS) {
			endUnleash(p, r);
			return;
		}
		// rooted in the air: he can look around to sweep it
		if (r.anchor != null) {
			if (p.position().distanceToSqr(r.anchor) > 0.25) {
				p.teleportTo(r.anchor.x, r.anchor.y, r.anchor.z);
			}
			p.setDeltaMovement(Vec3.ZERO);
			p.hurtMarked = true;
			p.resetFallDistance();
		}
		Vec3 from = Vision.stonePos(p);
		Vec3 look = p.getLookAngle();
		BeamClash.report(p, from, look, VisionConfig.UNLEASH_RANGE, 2.5f, Vision.GOLD);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 end = beamEnd(p, from, look, VisionConfig.UNLEASH_RANGE);
		if (age % VisionConfig.UNLEASH_HIT_INTERVAL == 0 && !BeamClash.inClash(p)) {
			unleashHit(p, from, end);
		}
		if (age % 2 == 0) {
			level.sendParticles(Vision.dust(Vision.GOLD, 2.2f), end.x, end.y, end.z, 8, 0.5, 0.5, 0.5, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 3, 0.3, 0.3, 0.3, 0.05);
		}
		if (age % 10 == 0) {
			Vision.sound(level, from, SoundEvents.BEACON_AMBIENT, 0.8f, 1.2f);
		}
	}

	/** One sweep of the wide beam: 7 to everything within 1.6 blocks of its line. Public for the tests. Returns how many it hit. */
	public static int unleashHit(ServerPlayer p, Vec3 from, Vec3 end) {
		int n = 0;
		for (LivingEntity e : along(p, from, end, VisionConfig.UNLEASH_RADIUS)) {
			if (Vision.hit(p, e, VisionConfig.UNLEASH_DAMAGE)) {
				n++;
			}
		}
		return n;
	}

	static void endUnleash(ServerPlayer p, Run r) {
		r.anchor = null;
		VisionState n = Vision.state(p).copy();
		n.unleash = false;
		n.unleashStart = 0L;
		Vision.save(p, n);
		Vision.startCooldown(p, VisionAbilityManager.UNLEASH, VisionConfig.UNLEASH_COOLDOWN);
		Vision.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.6f, 0.8f);
	}

	// ================================================================ X: Phase Dash

	static boolean dash(ServerPlayer p) {
		if (!Vision.spend(p, VisionConfig.DASH_COST)) {
			return false;
		}
		Run r = run(p);
		r.dashDir = p.getLookAngle().normalize();
		r.dashLeft = VisionConfig.DASH_TICKS;
		VisionState n = Vision.state(p).copy();
		n.dashUntil = Vision.now(p) + VisionConfig.DASH_IFRAMES;
		Vision.save(p, n);
		Vision.startCooldown(p, VisionAbilityManager.DASH, VisionConfig.DASH_COOLDOWN);
		Vision.sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.5f, 1.6f);
		return true;
	}

	/** Blocks a tick: 10 blocks over the dash. */
	public static double dashStep() {
		return VisionConfig.DASH_DISTANCE / VisionConfig.DASH_TICKS;
	}

	private static void tickDash(ServerPlayer p, Run r) {
		if (r.dashLeft <= 0) {
			return;
		}
		r.dashLeft--;
		p.setDeltaMovement(r.dashDir.scale(r.dashLeft > 0 ? dashStep() : 0.35));
		p.hurtMarked = true;
		p.resetFallDistance();
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1.0, 0);
		level.sendParticles(Vision.dust(Vision.GOLD, 1.3f), c.x, c.y, c.z, 6, 0.3, 0.5, 0.3, 0.0);
		level.sendParticles(Vision.dust(Vision.GREEN, 1.0f), c.x, c.y, c.z, 3, 0.3, 0.5, 0.3, 0.0);
	}

	// ================================================================ Shift+X: Density Dive

	static boolean dive(ServerPlayer p) {
		LivingEntity target = lookTarget(p, VisionConfig.DIVE_RANGE, 1.5);
		if (target != null && !HeroTargets.canHarm(p, target)) {
			target = null;
		}
		if (!Vision.spend(p, VisionConfig.DIVE_COST)) {
			return false;
		}
		Run r = run(p);
		r.diveTicks = 0;
		r.diveTarget = null;
		long now = Vision.now(p);
		int maxTicks;
		if (target != null) {
			r.diveMode = DIVE_TARGET;
			r.diveTarget = target;
			r.diveDir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(p.position().add(0, 0.9, 0)).normalize();
			maxTicks = VisionConfig.DIVE_MAX_TICKS;
		} else if (airBelow(p) >= VisionConfig.METEOR_MIN_HEIGHT) {
			r.diveMode = DIVE_METEOR;
			r.diveDir = new Vec3(0, -1, 0);
			r.meteorStartY = p.getY();
			maxTicks = VisionConfig.METEOR_MAX_TICKS;
		} else {
			r.diveMode = DIVE_RUSH;
			Vec3 look = p.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			r.diveDir = flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : flat.normalize();
			maxTicks = VisionConfig.RUSH_TICKS;
		}
		VisionState n = Vision.state(p).copy();
		n.diveUntil = now + maxTicks + 2;
		if (r.diveMode == DIVE_TARGET) {
			n.dashUntil = now + maxTicks + 4; // phased the whole way: straight through it
		}
		Vision.save(p, n);
		Vision.startCooldown(p, VisionAbilityManager.DIVE, VisionConfig.DIVE_COOLDOWN);
		Vision.sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.6f, 0.8f);
		Vision.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.3f, 0.7f);
		return true;
	}

	/** Blocks of air under his feet (up to 64). */
	static double airBelow(ServerPlayer p) {
		Vec3 from = p.position();
		Vec3 to = from.add(0, -64, 0);
		BlockHitResult bhr = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
		return bhr.getType() == HitResult.Type.MISS ? 64.0 : from.y - bhr.getLocation().y;
	}

	private static void tickDive(ServerPlayer p, Run r) {
		if (r.diveMode == DIVE_NONE) {
			return;
		}
		r.diveTicks++;
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1.0, 0);
		level.sendParticles(Vision.dust(Vision.GOLD, 1.6f), c.x, c.y, c.z, 8, 0.3, 0.5, 0.3, 0.0);
		p.resetFallDistance();
		switch (r.diveMode) {
			case DIVE_TARGET -> {
				LivingEntity t = r.diveTarget;
				if (t == null || !t.isAlive() || t.level() != p.level()) {
					r.diveMode = DIVE_NONE;
					p.setDeltaMovement(Vec3.ZERO);
					p.hurtMarked = true;
					return;
				}
				Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0);
				Vec3 me = p.position().add(0, 0.9, 0);
				Vec3 to = aim.subtract(me);
				double dist = to.length();
				if (dist < 1.4 || r.diveTicks > VisionConfig.DIVE_MAX_TICKS) {
					diveThrough(p, t);
					return;
				}
				r.diveDir = to.normalize();
				p.setDeltaMovement(r.diveDir.scale(Math.min(VisionConfig.DIVE_SPEED, dist)));
				p.hurtMarked = true;
			}
			case DIVE_METEOR -> {
				p.setDeltaMovement(0, -VisionConfig.METEOR_SPEED, 0);
				p.hurtMarked = true;
				level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 2, 0.2, 0.3, 0.2, 0.01);
				if ((r.diveTicks > 1 && (p.onGround() || p.verticalCollision)) || r.diveTicks > VisionConfig.METEOR_MAX_TICKS) {
					meteorImpact(p, r.meteorStartY - p.getY());
				}
			}
			case DIVE_RUSH -> {
				p.setDeltaMovement(r.diveDir.scale(VisionConfig.RUSH_SPEED).add(0, p.getDeltaMovement().y * 0.5, 0));
				p.hurtMarked = true;
				for (LivingEntity e : HeroTargets.harmable(level, p, p.getBoundingBox().inflate(0.8))) {
					if (e != p && !(e instanceof ArmorStand)) {
						diveThrough(p, e);
						return;
					}
				}
				if (r.diveTicks > VisionConfig.RUSH_TICKS || p.horizontalCollision) {
					r.diveMode = DIVE_NONE;
					p.setDeltaMovement(r.diveDir.scale(0.3));
					p.hurtMarked = true;
				}
			}
			default -> r.diveMode = DIVE_NONE;
		}
	}

	/**
	 * He re-solidifies inside the creature: 22, a launch along his path and a 1 s stun; he ends 2.5 blocks past it (if there's room)
	 * facing it. Public for the tests.
	 */
	public static boolean diveThrough(ServerPlayer p, LivingEntity t) {
		Run r = run(p);
		r.diveMode = DIVE_NONE;
		r.diveTarget = null;
		Vec3 dir = r.diveDir.lengthSqr() < 1.0e-4 ? p.getLookAngle() : r.diveDir;
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : flat.normalize();
		boolean hit = Vision.hit(p, t, VisionConfig.DIVE_DAMAGE);
		if (hit) {
			double resist = Mth.clamp(1.0 - t.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE), 0.2, 1.0);
			t.push(flat.x * VisionConfig.DIVE_KNOCKBACK * resist, 0.45, flat.z * VisionConfig.DIVE_KNOCKBACK * resist);
			t.hurtMarked = true;
			stun(t, VisionConfig.DIVE_STUN_TICKS);
		}
		// out the other side, facing it
		Vec3 behind = t.position().add(flat.scale(VisionConfig.DIVE_BEHIND + t.getBbWidth() * 0.5));
		AABB box = p.getBoundingBox().move(behind.subtract(p.position()));
		ServerLevel level = (ServerLevel) p.level();
		if (level.noCollision(p, box)) {
			float yaw = (float) (Mth.atan2(-flat.x, flat.z) * Mth.RAD_TO_DEG) + 180f;
			p.teleportTo(level, behind.x, behind.y, behind.z, yaw, p.getXRot());
		}
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
		VisionState n = Vision.state(p).copy();
		n.diveUntil = Vision.now(p) + 3;
		n.dashUntil = Math.min(n.dashUntil, Vision.now(p) + 3);
		Vision.save(p, n);
		Vec3 at = t.position().add(0, t.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(Vision.dust(Vision.GOLD, 2.0f), at.x, at.y, at.z, 30, 0.4, 0.5, 0.4, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.15);
		Vision.sound(level, at, SoundEvents.ANVIL_LAND, 0.4f, 0.5f);
		Vision.sound(level, at, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
		return hit;
	}

	/** The meteor drop lands: 14 + 0.6 a block fallen (up to +16) in 4.5 blocks, everything knocked up. Public for the tests. */
	public static int meteorImpact(ServerPlayer p, double fallen) {
		Run r = run(p);
		r.diveMode = DIVE_NONE;
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position();
		float damage = meteorDamage(fallen);
		double rad = VisionConfig.METEOR_RADIUS;
		int n = 0;
		for (LivingEntity e : HeroTargets.harmable(level, p, new AABB(c, c).inflate(rad))) {
			if (e == p || e instanceof ArmorStand || e.distanceToSqr(c) > (rad + 0.5) * (rad + 0.5)) {
				continue;
			}
			if (Vision.hit(p, e, damage)) {
				n++;
			}
			Vec3 out = e.position().subtract(c);
			Vec3 h = out.horizontalDistanceSqr() < 1.0e-4 ? Vec3.ZERO : new Vec3(out.x, 0, out.z).normalize();
			e.push(h.x * 0.8, 0.85, h.z * 0.8);
			e.hurtMarked = true;
		}
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
		VisionState s = Vision.state(p).copy();
		s.diveUntil = Vision.now(p) + 3;
		Vision.save(p, s);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.3, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(Vision.dust(Vision.GOLD, 2.4f), c.x, c.y + 0.4, c.z, 60, 2.0, 0.4, 2.0, 0.0);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y + 0.2, c.z, 40, 2.0, 0.2, 2.0, 0.3);
		Vision.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 0.7f);
		Vision.sound(level, c, SoundEvents.ANVIL_LAND, 0.5f, 0.4f);
		return n;
	}

	public static float meteorDamage(double fallen) {
		return VisionConfig.METEOR_DAMAGE + Math.min(VisionConfig.METEOR_BONUS_MAX, (float) Math.max(0.0, fallen) * VisionConfig.METEOR_PER_BLOCK);
	}

	// ================================================================ C: Synthezoid Grip

	public static LivingEntity gripped(ServerPlayer p) {
		Run r = RUNS.get(p.getUUID());
		return r == null ? null : r.gripped;
	}

	static boolean grip(ServerPlayer p) {
		Run r = run(p);
		if (r.gripped != null) {
			hurl(p, r);
			return true;
		}
		LivingEntity target = lookTarget(p, VisionConfig.GRIP_REACH, 0.6);
		if (target == null || !grippable(p, target)) {
			Vision.say(p, "message.projecthero.vision.grip_none", ChatFormatting.GRAY);
			return false;
		}
		if (!Vision.spend(p, VisionConfig.GRIP_COST)) {
			return false;
		}
		r.gripped = target;
		r.gripStart = Vision.now(p);
		Vision.sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.6f, 0.8f);
		return true;
	}

	public static boolean grippable(ServerPlayer p, LivingEntity t) {
		return t.isAlive() && !(t instanceof ArmorStand) && t.getMaxHealth() <= VisionConfig.GRIP_MAX_HEALTH && !t.isPassenger()
				&& HeroTargets.canHarm(p, t) && t.getBbHeight() <= 3.5f;
	}

	private static void holdGripped(ServerPlayer p, Run r, long now) {
		LivingEntity t = r.gripped;
		if (t == null) {
			return;
		}
		if (!t.isAlive() || t.isRemoved() || t.level() != p.level() || t.distanceToSqr(p) > 100 || now - r.gripStart > VisionConfig.GRIP_MAX_TICKS) {
			dropGripped(p, r, true);
			return;
		}
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 at = p.position().add(flat.scale(1.2 + t.getBbWidth() * 0.6)).add(0, Math.max(0.2, 1.2 - t.getBbHeight() * 0.5), 0);
		if (t instanceof ServerPlayer sp) {
			sp.teleportTo(at.x, at.y, at.z);
		} else {
			t.setPos(at.x, at.y, at.z);
		}
		t.setDeltaMovement(Vec3.ZERO);
		t.resetFallDistance();
		t.hurtMarked = true;
		if (now % 4 == 0) {
			((ServerLevel) p.level()).sendParticles(Vision.dust(Vision.GOLD, 0.8f), t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 2, 0.2,
					0.3, 0.2, 0.0);
		}
	}

	/** C again: hurled along his look; it takes 10 when it hits something in the next 2 s. */
	static void hurl(ServerPlayer p, Run r) {
		LivingEntity t = r.gripped;
		r.gripped = null;
		Vision.startCooldown(p, VisionAbilityManager.GRIP, VisionConfig.GRIP_COOLDOWN);
		if (t == null || !t.isAlive()) {
			return;
		}
		Vec3 v = p.getLookAngle().scale(VisionConfig.GRIP_THROW_SPEED).add(0, 0.25, 0);
		t.setDeltaMovement(v);
		t.hurtMarked = true;
		t.resetFallDistance();
		r.thrown = t;
		r.thrownUntil = Vision.now(p) + VisionConfig.GRIP_IMPACT_TICKS;
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		Vision.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 0.7f);
	}

	private static void dropGripped(ServerPlayer p, Run r, boolean cooldown) {
		LivingEntity t = r.gripped;
		r.gripped = null;
		if (t != null && t.isAlive()) {
			t.setDeltaMovement(Vec3.ZERO);
			t.hurtMarked = true;
		}
		if (cooldown && t != null) {
			Vision.startCooldown(p, VisionAbilityManager.GRIP, VisionConfig.GRIP_COOLDOWN);
		}
	}

	private static void tickThrown(ServerPlayer p, Run r, long now) {
		LivingEntity t = r.thrown;
		if (t == null) {
			return;
		}
		if (!t.isAlive() || t.isRemoved() || t.level() != p.level() || now > r.thrownUntil) {
			r.thrown = null;
			return;
		}
		boolean struck = t.horizontalCollision || (now - (r.thrownUntil - VisionConfig.GRIP_IMPACT_TICKS) > 3 && (t.verticalCollision || t.onGround()));
		LivingEntity other = null;
		for (LivingEntity e : HeroTargets.harmable(p.level(), p, t.getBoundingBox().inflate(0.3))) {
			if (e != p && e != t && !(e instanceof ArmorStand)) {
				other = e;
				break;
			}
		}
		if (!struck && other == null) {
			return;
		}
		r.thrown = null;
		Vision.hit(p, t, VisionConfig.GRIP_IMPACT_DAMAGE);
		if (other != null) {
			Vision.hit(p, other, VisionConfig.GRIP_IMPACT_DAMAGE);
		}
		ServerLevel level = (ServerLevel) p.level();
		level.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 14, 0.3, 0.4, 0.3, 0.1);
		Vision.sound(level, t.position(), SoundEvents.GENERIC_SMALL_FALL, 1.0f, 0.6f);
	}

	// ================================================================ shared

	/** The creature under his crosshair within {@code reach} (picked with {@code inflate} of slack). */
	static LivingEntity lookTarget(ServerPlayer p, double reach, double inflate) {
		Vec3 eye = p.getEyePosition();
		Vec3 to = eye.add(p.getLookAngle().scale(reach));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? to : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(inflate + 1.0),
				e -> e != p && e.isPickable() && e instanceof LivingEntity && !(e instanceof ArmorStand) && !e.isSpectator()
						&& e.getBoundingBox().inflate(inflate).clip(eye, end).isPresent(),
				eye.distanceToSqr(end));
		if (ehr != null && ehr.getEntity() instanceof LivingEntity le) {
			return le;
		}
		// the pick grows boxes by the entity's own pick radius only: try again with the slack
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(inflate + 1.0),
				e -> e != p && e.isAlive() && !(e instanceof ArmorStand) && !e.isSpectator())) {
			var hit = e.getBoundingBox().inflate(inflate).clip(eye, end);
			if (hit.isPresent() && eye.distanceToSqr(hit.get()) < bestD) {
				bestD = eye.distanceToSqr(hit.get());
				best = e;
			}
		}
		return best;
	}

	/** Per tick for every Vision. */
	static void tick(ServerPlayer p) {
		Run r = run(p);
		long now = Vision.now(p);
		tickDash(p, r);
		tickDive(p, r);
		tickPunched(p, r, now);
		holdGripped(p, r, now);
		tickThrown(p, r, now);
		tickBeam(p, r, now);
		tickLance(p, now);
		tickUnleash(p, r, now);
	}
}
