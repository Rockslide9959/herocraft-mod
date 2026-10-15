package com.projecthero.mod.bluebeetle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.bluebeetle.data.BlueBeetleState;
import com.projecthero.mod.bluebeetle.entity.BlueBeetleEntityTypes;
import com.projecthero.mod.bluebeetle.entity.ScarabDroneEntity;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Blue Beetle's twelve moves (v0.16.5). Each is gated by {@link #begin} (suited, off cooldown, enough Scarab Energy --
 * free while Khaji-Da is unleashed), runs as a small per-player session ticked from {@link #tick}, and deals damage
 * through {@link BlueBeetleCombat}.
 *
 * <pre>
 *   R (1)  Plasma Blaster (hold)    Shift+R  Mega Cannon
 *   G (2)  Energy Blade (combo)     Shift+G  Scarab Grip
 *   X (3)  Wings (flight toggle)    Shift+X  Afterburner
 *   Z (4)  Energy Shield (hold)     Shift+Z  Shield Pulse
 *   V (5)  Scarab Swarm             Shift+V  Bubble Prison
 *   C (6)  Camouflage               Shift+C  Khaji-Da Unleashed
 * </pre>
 */
public final class BlueBeetleAbilities {
	public static final String BLAST = "plasma_blaster";
	public static final String CANNON = "mega_cannon";
	public static final String BLADE = "energy_blade";
	public static final String GRIP = "scarab_grip";
	public static final String WINGS = "wings";
	public static final String AFTERBURNER = "afterburner";
	public static final String SHIELD = "energy_shield";
	public static final String PULSE = "shield_pulse";
	public static final String SWARM = "scarab_swarm";
	public static final String BUBBLE = "bubble_prison";
	public static final String CAMO = "camouflage";
	public static final String UNLEASH = "khaji_da_unleashed";

	private static final class Bolt {
		Vec3 pos;
		final Vec3 vel;
		int age;

		Bolt(Vec3 pos, Vec3 vel) {
			this.pos = pos;
			this.vel = vel;
		}
	}

	private static final class Burn {
		final Vec3 dir;
		final long start;
		Vec3 last;
		double travelled;
		final Set<Integer> hit = new HashSet<>();

		Burn(Vec3 dir, long start, Vec3 last) {
			this.dir = dir;
			this.start = start;
			this.last = last;
		}
	}

	private static final class Grip {
		final int target;
		final long start;
		final double baseY;

		Grip(int target, long start, double baseY) {
			this.target = target;
			this.start = start;
			this.baseY = baseY;
		}
	}

	private static final Map<UUID, Long> BLASTS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Bolt>> BOLTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Burn> BURNS = new ConcurrentHashMap<>();
	private static final Map<UUID, Grip> GRIPS = new ConcurrentHashMap<>();
	/** When the held shield went up (its upkeep clock). */
	private static final Map<UUID, Long> SHIELDS = new ConcurrentHashMap<>();

	private BlueBeetleAbilities() {
	}

	static void clearSessionState() {
		BLASTS.clear();
		BOLTS.clear();
		BURNS.clear();
		GRIPS.clear();
		SHIELDS.clear();
	}

	public static void clear(ServerPlayer p) {
		UUID id = p.getUUID();
		BLASTS.remove(id);
		BOLTS.remove(id);
		BURNS.remove(id);
		Grip g = GRIPS.remove(id);
		if (g != null && p.level().getEntity(g.target) instanceof LivingEntity le) {
			le.setNoGravity(false);
		}
		SHIELDS.remove(id);
		ScarabDroneEntity.recallAll(p);
		breakCamo(p);
	}

	public static boolean running(ServerPlayer p, String id) {
		UUID u = p.getUUID();
		return switch (id) {
			case BLAST -> BLASTS.containsKey(u);
			case CANNON -> BlueBeetle.state(p).fx.chargeStart != 0L;
			case AFTERBURNER -> BURNS.containsKey(u);
			case GRIP -> GRIPS.containsKey(u);
			case SHIELD -> BlueBeetle.shieldUp(p);
			case WINGS -> BlueBeetle.state(p).flying;
			case CAMO -> BlueBeetle.state(p).camoUntil > p.level().getGameTime();
			case UNLEASH -> BlueBeetle.unleashed(p);
			default -> false;
		};
	}

	// ---------------------------------------------------------------- the gate

	static boolean begin(ServerPlayer p, String id, float cost) {
		if (!BlueBeetle.canAct(p)) {
			return false;
		}
		int cd = BlueBeetle.cooldownRemaining(p, id);
		if (cd > 0) {
			BlueBeetle.say(p, "message.projecthero.blue_beetle.cooldown", ChatFormatting.GRAY,
					Component.translatable("projecthero.blue_beetle.ability." + id), String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		if (!BlueBeetle.spendEnergy(p, cost)) {
			BlueBeetle.say(p, "message.projecthero.blue_beetle.low_energy", ChatFormatting.BLUE, (int) Math.ceil(cost),
					(int) Math.floor(BlueBeetle.energy(p)));
			return false;
		}
		return true;
	}

	/** Starts a cooldown (halved while Khaji-Da is unleashed). */
	static void cooldown(ServerPlayer p, String id, int ticks) {
		int t = BlueBeetle.unleashed(p) ? Math.round(ticks * BlueBeetleConfig.UNLEASH_COOLDOWN_FACTOR) : ticks;
		BlueBeetleState n = BlueBeetle.state(p).copy();
		n.abilityReadyAt.put(id, p.level().getGameTime() + t);
		BlueBeetle.save(p, n);
	}

	private static void fx(ServerPlayer p, java.util.function.Consumer<BlueBeetleState> edit) {
		BlueBeetleState n = BlueBeetle.state(p).copy();
		edit.accept(n);
		BlueBeetle.save(p, n);
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
		level(p).playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	static Vec3 hand(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		return eye.add(look.scale(0.7)).add(right.scale(0.35)).add(0, -0.35, 0);
	}

	// ================================================================ R: Plasma Blaster (held)

	public static void startBlast(ServerPlayer p) {
		if (BLASTS.containsKey(p.getUUID()) || !begin(p, BLAST, 0f)) {
			return;
		}
		BLASTS.put(p.getUUID(), p.level().getGameTime());
		fireBolt(p);
	}

	public static void stopBlast(ServerPlayer p) {
		if (BLASTS.remove(p.getUUID()) != null) {
			cooldown(p, BLAST, BlueBeetleConfig.BLAST_COOLDOWN);
		}
	}

	private static void fireBolt(ServerPlayer p) {
		if (!BlueBeetle.spendEnergy(p, BlueBeetleConfig.BLAST_COST)) {
			BlueBeetle.say(p, "message.projecthero.blue_beetle.energy_empty", ChatFormatting.BLUE);
			stopBlast(p);
			return;
		}
		BlueBeetle.setAnim(p, BlueBeetleState.ANIM_BLAST); // keeps the blaster arm up while R is held
		Vec3 from = hand(p);
		Vec3 aim = AbilityHelpers.aimPoint(p, BlueBeetleConfig.BLAST_RANGE);
		Vec3 dir = aim.subtract(from);
		dir = dir.lengthSqr() < 1.0e-4 ? p.getLookAngle() : dir.normalize();
		BOLTS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new Bolt(from, dir.scale(BlueBeetleConfig.BLAST_SPEED)));
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.5f, 2.0f);
		sound(p, SoundEvents.FIREWORK_ROCKET_BLAST, 0.4f, 1.8f);
	}

	private static void tickBlast(ServerPlayer p, long start, long now) {
		if (!BlueBeetle.suited(p) || !p.isAlive()) {
			stopBlast(p);
			return;
		}
		if (now != start && (now - start) % BlueBeetleConfig.BLAST_INTERVAL == 0) {
			fireBolt(p);
		}
	}

	private static void tickBolts(ServerPlayer p, List<Bolt> bolts) {
		ServerLevel level = level(p);
		Iterator<Bolt> it = bolts.iterator();
		while (it.hasNext()) {
			Bolt b = it.next();
			b.age++;
			Vec3 next = b.pos.add(b.vel);
			BlockHitResult bhr = level.clip(new ClipContext(b.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			Vec3 end = bhr.getType() == HitResult.Type.MISS ? next : bhr.getLocation();
			LivingEntity hit = null;
			double best = Double.MAX_VALUE;
			for (Entity e : level.getEntities(p, new AABB(b.pos, end).inflate(0.7),
					e -> e instanceof LivingEntity le && BlueBeetleCombat.isTarget(p, le) && e.isPickable())) {
				AABB box = e.getBoundingBox().inflate(0.4);
				Vec3 at = box.contains(b.pos) ? b.pos : box.clip(b.pos, end).orElse(null);
				if (at != null && b.pos.distanceToSqr(at) < best) {
					best = b.pos.distanceToSqr(at);
					hit = (LivingEntity) e;
				}
			}
			AbilityHelpers.line(level, b.pos, end, BlueBeetle.BLUE, 2.5);
			level.sendParticles(BlueBeetle.BLUE_BIG, end.x, end.y, end.z, 3, 0.06, 0.06, 0.06, 0.0);
			if (hit != null) {
				BlueBeetleCombat.strike(p, hit, b.pos, BlueBeetleConfig.BLAST_DAMAGE, 0.3, 0.1);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 8, 0.2, 0.2, 0.2, 0.15);
				it.remove();
				continue;
			}
			if (bhr.getType() != HitResult.Type.MISS || b.age * BlueBeetleConfig.BLAST_SPEED > BlueBeetleConfig.BLAST_RANGE) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 6, 0.15, 0.15, 0.15, 0.1);
				it.remove();
				continue;
			}
			b.pos = end;
		}
	}

	// ================================================================ Shift+R: Mega Cannon

	public static void cannon(ServerPlayer p) {
		if (BlueBeetle.state(p).fx.chargeStart != 0L || !begin(p, CANNON, BlueBeetleConfig.CANNON_COST)) {
			return;
		}
		cooldown(p, CANNON, BlueBeetleConfig.CANNON_COOLDOWN);
		long now = p.level().getGameTime();
		fx(p, n -> {
			n.fx.chargeStart = now;
			n.animId = BlueBeetleState.ANIM_CANNON;
			n.animStart = now;
		});
		sound(p, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.4f);
	}

	private static void tickCannon(ServerPlayer p, BlueBeetleState s, long now) {
		long age = now - s.fx.chargeStart;
		if (!BlueBeetle.suited(p) || !p.isAlive()) {
			fx(p, n -> n.fx.chargeStart = 0L);
			return;
		}
		Vec3 h = hand(p);
		if (age % 2 == 0) {
			level(p).sendParticles(BlueBeetle.BLUE_BIG, h.x, h.y, h.z, 2 + (int) (age / 6), 0.1, 0.1, 0.1, 0.0);
		}
		if (age < BlueBeetleConfig.CANNON_CHARGE_TICKS) {
			return;
		}
		// fire: everything along the line
		Vec3 eye = p.getEyePosition();
		Vec3 dir = p.getLookAngle();
		Vec3 far = eye.add(dir.scale(BlueBeetleConfig.CANNON_RANGE));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		for (LivingEntity e : level(p).getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(BlueBeetleConfig.CANNON_RADIUS + 1),
				e -> BlueBeetleCombat.isTarget(p, e))) {
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			double t = c.subtract(eye).dot(dir);
			if (t < 0 || t > eye.distanceTo(end) + 0.5) {
				continue;
			}
			Vec3 onLine = eye.add(dir.scale(t));
			if (onLine.distanceTo(c) <= BlueBeetleConfig.CANNON_RADIUS + e.getBbWidth() * 0.5) {
				BlueBeetleCombat.strike(p, e, eye, BlueBeetleConfig.CANNON_DAMAGE, 1.2, 0.3);
			}
		}
		Vec3 endF = end;
		fx(p, n -> {
			n.fx.chargeStart = 0L;
			n.fx.beamUntil = now + 10;
			n.fx.beamEnd = List.of(endF.x, endF.y, endF.z);
		});
		ServerLevel level = level(p);
		level.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		level.sendParticles(BlueBeetle.BLUE_BIG, end.x, end.y, end.z, 30, 0.6, 0.6, 0.6, 0.0);
		sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.3f);
		sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.6f);
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(dir.scale(-0.4)));
	}

	// ================================================================ G: Energy Blade (combo)

	public static void blade(ServerPlayer p) {
		BlueBeetleState s = BlueBeetle.state(p);
		long now = p.level().getGameTime();
		boolean combo = s.bladeCombo > 0 && s.bladeCombo < 3 && s.fx.bladeUntil > now;
		if (!combo) {
			if (!begin(p, BLADE, BlueBeetleConfig.BLADE_COST)) {
				return;
			}
		} else if (!BlueBeetle.canAct(p)) {
			return;
		}
		int swing = combo ? s.bladeCombo + 1 : 1;
		fx(p, n -> {
			n.bladeCombo = swing;
			n.fx.bladeUntil = now + BlueBeetleConfig.BLADE_WINDOW;
			n.animId = swing == 1 ? BlueBeetleState.ANIM_BLADE_1 : swing == 2 ? BlueBeetleState.ANIM_BLADE_2 : BlueBeetleState.ANIM_BLADE_3;
			n.animStart = now;
		});
		boolean last = swing == 3;
		LivingEntity[] hit = new LivingEntity[1];
		BlueBeetleCombat.ray(p, BlueBeetleConfig.BLADE_REACH, 0.8, hit);
		// the blade sweeps: whatever is close in front too
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Set<Integer> done = new HashSet<>();
		if (hit[0] != null) {
			BlueBeetleCombat.strike(p, hit[0], p.position(), BlueBeetleConfig.BLADE_DAMAGE, last ? 1.6 : 0.2, last ? 0.4 : 0.0);
			done.add(hit[0].getId());
		}
		for (LivingEntity e : BlueBeetleCombat.within(p, eye.add(look.scale(1.6)), 1.8, e -> BlueBeetleCombat.isTarget(p, e))) {
			if (done.add(e.getId())) {
				BlueBeetleCombat.strike(p, e, p.position(), BlueBeetleConfig.BLADE_DAMAGE, last ? 1.6 : 0.2, last ? 0.4 : 0.0);
			}
		}
		Vec3 sweep = eye.add(look.scale(1.8));
		level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, sweep.x, sweep.y - 0.3, sweep.z, 1, 0, 0, 0, 0);
		level(p).sendParticles(BlueBeetle.BLUE, sweep.x, sweep.y - 0.3, sweep.z, 8, 0.5, 0.3, 0.5, 0.0);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, last ? 0.8f : 1.3f);
		sound(p, SoundEvents.AMETHYST_BLOCK_HIT, 0.8f, 1.6f);
		if (last) {
			cooldown(p, BLADE, BlueBeetleConfig.BLADE_COOLDOWN);
			fx(p, n -> {
				n.bladeCombo = 0;
				n.fx.bladeUntil = now + 8;
			});
		}
	}

	// ================================================================ Shift+G: Scarab Grip

	public static void grip(ServerPlayer p) {
		if (GRIPS.containsKey(p.getUUID())) {
			return;
		}
		LivingEntity[] hit = new LivingEntity[1];
		BlueBeetleCombat.ray(p, BlueBeetleConfig.GRIP_RANGE, 0.6, hit);
		if (hit[0] == null || !BlueBeetleCombat.isMovable(p, hit[0]) || hit[0] instanceof Player) {
			if (BlueBeetle.canAct(p)) {
				BlueBeetle.say(p, "message.projecthero.blue_beetle.grip_nothing", ChatFormatting.GRAY);
			}
			return;
		}
		if (!begin(p, GRIP, BlueBeetleConfig.GRIP_COST)) {
			return;
		}
		cooldown(p, GRIP, BlueBeetleConfig.GRIP_COOLDOWN);
		LivingEntity t = hit[0];
		long now = p.level().getGameTime();
		GRIPS.put(p.getUUID(), new Grip(t.getId(), now, t.getY()));
		fx(p, n -> {
			n.fx.gripTarget = t.getId();
			n.animId = BlueBeetleState.ANIM_GRIP;
			n.animStart = now;
		});
		t.setNoGravity(true);
		if (t instanceof Mob m) {
			m.getNavigation().stop();
		}
		sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.8f);
	}

	private static void tickGrip(ServerPlayer p, Grip g, long now) {
		Entity e = p.level().getEntity(g.target);
		long age = now - g.start;
		if (!(e instanceof LivingEntity t) || !t.isAlive() || !BlueBeetle.suited(p)) {
			endGrip(p, g);
			return;
		}
		ServerLevel level = level(p);
		if (age <= 12) {
			// lifted in the energy hand
			t.setDeltaMovement(0, 0.32, 0);
			t.hurtMarked = true;
		} else if (age == 13) {
			t.setNoGravity(false);
			t.setDeltaMovement(0, -2.2, 0);
			t.hurtMarked = true;
		} else if (t.onGround() || age > 30) {
			BlueBeetleCombat.strike(p, t, t.position().add(0, 1, 0), BlueBeetleConfig.GRIP_DAMAGE, 0.0, 0.0);
			level.sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY(), t.getZ(), 1, 0, 0, 0, 0);
			level.sendParticles(BlueBeetle.BLUE_BIG, t.getX(), t.getY() + 0.2, t.getZ(), 20, 0.8, 0.1, 0.8, 0.0);
			level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 1.3f);
			endGrip(p, g);
			return;
		}
		// the glowing fingers round it
		double r = t.getBbWidth() * 0.6 + 0.4;
		for (int i = 0; i < 5; i++) {
			double a = Math.PI * 2 * i / 5 + age * 0.2;
			level.sendParticles(BlueBeetle.BLUE_BIG, t.getX() + Math.cos(a) * r, t.getY() + t.getBbHeight() * 0.6, t.getZ() + Math.sin(a) * r, 1, 0.02,
					t.getBbHeight() * 0.3, 0.02, 0.0);
		}
	}

	private static void endGrip(ServerPlayer p, Grip g) {
		GRIPS.remove(p.getUUID());
		if (p.level().getEntity(g.target) instanceof LivingEntity t) {
			t.setNoGravity(false);
		}
		fx(p, n -> n.fx.gripTarget = -1);
	}

	// ================================================================ X: Wings / Shift+X: Afterburner

	public static void toggleWings(ServerPlayer p) {
		BlueBeetleState s = BlueBeetle.state(p);
		if (s.flying) {
			BlueBeetle.landWings(p);
			sound(p, SoundEvents.ELYTRA_FLYING, 0.3f, 1.8f);
			return;
		}
		if (!begin(p, WINGS, 0f)) {
			return;
		}
		if (BlueBeetle.energy(p) < BlueBeetleConfig.FLIGHT_DRAIN && !BlueBeetle.unleashed(p)) {
			BlueBeetle.say(p, "message.projecthero.blue_beetle.low_energy", ChatFormatting.BLUE, (int) BlueBeetleConfig.FLIGHT_DRAIN,
					(int) Math.floor(BlueBeetle.energy(p)));
			return;
		}
		fx(p, n -> n.flying = true);
		BlueBeetle.openWings(p);
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(0, 0.5, 0));
		sound(p, SoundEvents.ELYTRA_FLYING, 0.5f, 1.4f);
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.6f);
	}

	public static void afterburner(ServerPlayer p) {
		if (BURNS.containsKey(p.getUUID()) || !begin(p, AFTERBURNER, BlueBeetleConfig.AFTERBURNER_COST)) {
			return;
		}
		cooldown(p, AFTERBURNER, BlueBeetleConfig.AFTERBURNER_COOLDOWN);
		BlueBeetle.setAnim(p, BlueBeetleState.ANIM_AFTERBURNER);
		BURNS.put(p.getUUID(), new Burn(p.getLookAngle().normalize(), p.level().getGameTime(), p.position()));
		sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.4f, 0.6f);
	}

	private static void tickBurn(ServerPlayer p, Burn b, long now) {
		b.travelled += p.position().distanceTo(b.last);
		b.last = p.position();
		long age = now - b.start;
		if (b.travelled >= BlueBeetleConfig.AFTERBURNER_DISTANCE || age > 14 || (age > 2 && (p.horizontalCollision || p.verticalCollision))
				|| !p.isAlive()) {
			BURNS.remove(p.getUUID());
			AbilityHelpers.launchSelf(p, p.getDeltaMovement().scale(0.25));
			p.resetFallDistance();
			return;
		}
		AbilityHelpers.launchSelf(p, b.dir.scale(BlueBeetleConfig.AFTERBURNER_SPEED));
		ServerLevel level = level(p);
		Vec3 back = p.position().add(0, 1.0, 0).subtract(b.dir.scale(0.8));
		level.sendParticles(ParticleTypes.FLAME, back.x, back.y, back.z, 4, 0.1, 0.1, 0.1, 0.02);
		level.sendParticles(BlueBeetle.BLUE_BIG, back.x, back.y, back.z, 3, 0.2, 0.2, 0.2, 0.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.0).expandTowards(b.dir.scale(1.2)),
				e -> BlueBeetleCombat.isTarget(p, e))) {
			if (b.hit.add(e.getId())) {
				Vec3 side = b.dir.cross(new Vec3(0, 1, 0));
				side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
				Vec3 from = e.position().subtract(p.position()).dot(side) >= 0 ? e.position().subtract(side) : e.position().add(side);
				BlueBeetleCombat.strike(p, e, from, BlueBeetleConfig.AFTERBURNER_DAMAGE, 1.4, 0.3);
			}
		}
	}

	// ================================================================ Z: Energy Shield (held) / Shift+Z: Shield Pulse

	public static void raiseShield(ServerPlayer p) {
		if (BlueBeetle.shieldUp(p) || !begin(p, SHIELD, 0f)) {
			return;
		}
		if (BlueBeetle.energy(p) < BlueBeetleConfig.SHIELD_COST_PER_SECOND && !BlueBeetle.unleashed(p)) {
			BlueBeetle.say(p, "message.projecthero.blue_beetle.low_energy", ChatFormatting.BLUE, (int) BlueBeetleConfig.SHIELD_COST_PER_SECOND,
					(int) Math.floor(BlueBeetle.energy(p)));
			return;
		}
		SHIELDS.put(p.getUUID(), p.level().getGameTime());
		fx(p, n -> n.fx.shield = true);
		sound(p, SoundEvents.BEACON_ACTIVATE, 0.7f, 1.8f);
	}

	public static void lowerShield(ServerPlayer p) {
		if (SHIELDS.remove(p.getUUID()) != null || BlueBeetle.state(p).fx.shield) {
			fx(p, n -> n.fx.shield = false);
			sound(p, SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.8f);
		}
	}

	private static void tickShield(ServerPlayer p, long start, long now) {
		if (!BlueBeetle.suited(p) || !p.isAlive()) {
			lowerShield(p);
			return;
		}
		if ((now - start) % 20 == 19 && !BlueBeetle.spendEnergy(p, BlueBeetleConfig.SHIELD_COST_PER_SECOND)) {
			BlueBeetle.say(p, "message.projecthero.blue_beetle.energy_empty", ChatFormatting.BLUE);
			lowerShield(p);
		}
	}

	public static void pulse(ServerPlayer p) {
		if (!begin(p, PULSE, BlueBeetleConfig.PULSE_COST)) {
			return;
		}
		cooldown(p, PULSE, BlueBeetleConfig.PULSE_COOLDOWN);
		BlueBeetle.setAnim(p, BlueBeetleState.ANIM_PULSE);
		ServerLevel level = level(p);
		Vec3 c = p.position().add(0, 1.0, 0);
		for (LivingEntity e : BlueBeetleCombat.within(p, c, BlueBeetleConfig.PULSE_RADIUS, e -> BlueBeetleCombat.isMovable(p, e))) {
			AbilityHelpers.knockbackFrom(e, c, 2.0);
			AbilityHelpers.push(e, new Vec3(0, 0.4, 0));
		}
		// arrows and other projectiles turn round and fly back at whoever shot them
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(BlueBeetleConfig.PULSE_RADIUS),
				pr -> pr.getOwner() != p)) {
			Vec3 v = proj.getDeltaMovement();
			Entity shooter = proj.getOwner();
			Vec3 back = shooter != null ? shooter.getEyePosition().subtract(proj.position()).normalize().scale(Math.max(1.2, v.length()))
					: v.scale(-1.0);
			proj.setDeltaMovement(back);
			proj.setOwner(p);
			proj.hasImpulse = true;
		}
		for (int r = 1; r <= 6; r++) {
			for (int i = 0; i < 16; i++) {
				double a = Math.PI * 2 * i / 16;
				level.sendParticles(BlueBeetle.BLUE_BIG, c.x + Math.cos(a) * r, c.y - 0.6 + r * 0.05, c.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0.0);
			}
		}
		sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.6f);
		sound(p, SoundEvents.SHIELD_BLOCK, 1.0f, 0.6f);
	}

	// ================================================================ V: Scarab Swarm / Shift+V: Bubble Prison

	public static void swarm(ServerPlayer p) {
		if (!begin(p, SWARM, BlueBeetleConfig.SWARM_COST)) {
			return;
		}
		cooldown(p, SWARM, BlueBeetleConfig.SWARM_COOLDOWN);
		BlueBeetle.setAnim(p, BlueBeetleState.ANIM_SWARM);
		LivingEntity[] hit = new LivingEntity[1];
		BlueBeetleCombat.ray(p, 32.0, 0.6, hit);
		LivingEntity target = hit[0] != null && BlueBeetleCombat.isTarget(p, hit[0]) ? hit[0] : null;
		ServerLevel level = level(p);
		ScarabDroneEntity.recallAll(p);
		for (int i = 0; i < BlueBeetleConfig.SWARM_COUNT; i++) {
			ScarabDroneEntity d = BlueBeetleEntityTypes.SCARAB_DRONE.create(level);
			if (d == null) {
				continue;
			}
			double a = Math.PI * 2 * i / BlueBeetleConfig.SWARM_COUNT;
			d.moveTo(p.getX() + Math.cos(a) * 0.6, p.getY() + 1.6, p.getZ() + Math.sin(a) * 0.6, p.getYRot(), 0f);
			d.setup(p, target, BlueBeetleConfig.SWARM_TICKS);
			level.addFreshEntity(d);
		}
		level.sendParticles(BlueBeetle.BLUE_BIG, p.getX(), p.getY() + 1.5, p.getZ(), 20, 0.4, 0.3, 0.4, 0.0);
		sound(p, SoundEvents.BEEHIVE_EXIT, 1.2f, 1.6f);
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.7f, 2.0f);
	}

	public static void bubble(ServerPlayer p) {
		LivingEntity[] hit = new LivingEntity[1];
		BlueBeetleCombat.ray(p, BlueBeetleConfig.BUBBLE_RANGE, 0.6, hit);
		if (hit[0] == null || !BlueBeetleCombat.isMovable(p, hit[0])) {
			if (BlueBeetle.canAct(p)) {
				BlueBeetle.say(p, "message.projecthero.blue_beetle.bubble_nothing", ChatFormatting.GRAY);
			}
			return;
		}
		if (!begin(p, BUBBLE, BlueBeetleConfig.BUBBLE_COST)) {
			return;
		}
		cooldown(p, BUBBLE, BlueBeetleConfig.BUBBLE_COOLDOWN);
		LivingEntity t = hit[0];
		long now = p.level().getGameTime();
		BlueBeetleDamage.bubble(t, BlueBeetleConfig.BUBBLE_TICKS);
		fx(p, n -> {
			n.fx.bubbleTarget = t.getId();
			n.fx.bubbleUntil = now + BlueBeetleConfig.BUBBLE_TICKS;
			n.animId = BlueBeetleState.ANIM_BUBBLE;
			n.animStart = now;
		});
		if (t instanceof Mob m) {
			m.setTarget(null);
			m.getNavigation().stop();
		}
		level(p).playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 1.5f, 1.2f);
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.8f);
	}

	private static void tickBubble(ServerPlayer p, BlueBeetleState s, long now) {
		Entity e = p.level().getEntity(s.fx.bubbleTarget);
		if (!(e instanceof LivingEntity t) || !t.isAlive() || now >= s.fx.bubbleUntil) {
			if (e instanceof LivingEntity t2) {
				BlueBeetleDamage.pop(t2);
				level(p).playSound(null, t2.getX(), t2.getY(), t2.getZ(), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS, 1.5f, 1.0f);
			}
			fx(p, n -> {
				n.fx.bubbleTarget = -1;
				n.fx.bubbleUntil = 0L;
			});
			return;
		}
		// held fast: no moving, no attacking
		t.setDeltaMovement(0, 0, 0);
		t.hurtMarked = true;
		if (t instanceof Mob m) {
			m.setTarget(null);
			m.getNavigation().stop();
		}
	}

	// ================================================================ C: Camouflage / Shift+C: Khaji-Da Unleashed

	public static void camo(ServerPlayer p) {
		if (BlueBeetle.state(p).camoUntil > p.level().getGameTime() || !begin(p, CAMO, BlueBeetleConfig.CAMO_COST)) {
			return;
		}
		cooldown(p, CAMO, BlueBeetleConfig.CAMO_COOLDOWN);
		long now = p.level().getGameTime();
		fx(p, n -> {
			n.camoUntil = now + BlueBeetleConfig.CAMO_TICKS;
			n.animId = BlueBeetleState.ANIM_CAMO;
			n.animStart = now;
		});
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, BlueBeetleConfig.CAMO_TICKS, 0, false, false, true));
		loseTrack(p);
		level(p).sendParticles(BlueBeetle.BLUE, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.35, 0.8, 0.35, 0.0);
		sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
	}

	private static void loseTrack(ServerPlayer p) {
		for (Mob m : level(p).getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), m -> m.getTarget() == p)) {
			m.setTarget(null);
		}
	}

	/** Camouflage ends (he attacked, suited down, or it ran out). */
	public static void breakCamo(ServerPlayer p) {
		BlueBeetleState s = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.BLUE_BEETLE_STATE, null);
		if (s == null || s.camoUntil == 0L) {
			return;
		}
		fx(p, n -> n.camoUntil = 0L);
		p.removeEffect(MobEffects.INVISIBILITY);
		level(p).sendParticles(BlueBeetle.BLUE, p.getX(), p.getY() + 1.0, p.getZ(), 15, 0.35, 0.8, 0.35, 0.0);
	}

	public static void unleash(ServerPlayer p) {
		if (BlueBeetle.unleashed(p)) {
			return;
		}
		if (BlueBeetle.energy(p) + 1.0e-3f < BlueBeetleConfig.ENERGY_MAX) {
			if (BlueBeetle.canAct(p)) {
				BlueBeetle.say(p, "message.projecthero.blue_beetle.unleash_full", ChatFormatting.BLUE);
			}
			return;
		}
		if (!begin(p, UNLEASH, 0f)) {
			return;
		}
		long now = p.level().getGameTime();
		BlueBeetleState n = BlueBeetle.state(p).copy();
		n.abilityReadyAt.put(UNLEASH, now + BlueBeetleConfig.UNLEASH_COOLDOWN);
		n.unleashedUntil = now + BlueBeetleConfig.UNLEASH_TICKS;
		n.animId = BlueBeetleState.ANIM_UNLEASH;
		n.animStart = now;
		BlueBeetle.save(p, n);
		ServerLevel level = level(p);
		level.sendParticles(BlueBeetle.RED, p.getX(), p.getY() + 1.0, p.getZ(), 60, 0.5, 1.0, 0.5, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.5, 1.0, 0.5, 0.3);
		sound(p, SoundEvents.WARDEN_ROAR, 0.6f, 1.6f);
		sound(p, SoundEvents.BEACON_ACTIVATE, 1.0f, 0.6f);
		KhajiDa.speak(p, KhajiDa.Topic.UNLEASHED);
	}

	static void endUnleashed(ServerPlayer p) {
		long now = p.level().getGameTime();
		fx(p, n -> {
			n.unleashedUntil = 0L;
			n.energy = 0f;
			n.slowRegenUntil = now + BlueBeetleConfig.UNLEASH_AFTER_TICKS;
		});
		sound(p, SoundEvents.BEACON_DEACTIVATE, 1.0f, 0.6f);
		KhajiDa.speak(p, KhajiDa.Topic.UNLEASH_END);
	}

	// ================================================================ tick

	static void tick(ServerPlayer p) {
		UUID u = p.getUUID();
		long now = p.level().getGameTime();
		Long blast = BLASTS.get(u);
		if (blast != null) {
			tickBlast(p, blast, now);
		}
		List<Bolt> bolts = BOLTS.get(u);
		if (bolts != null) {
			tickBolts(p, bolts);
			if (bolts.isEmpty()) {
				BOLTS.remove(u);
			}
		}
		Burn b = BURNS.get(u);
		if (b != null) {
			tickBurn(p, b, now);
		}
		Grip g = GRIPS.get(u);
		if (g != null) {
			tickGrip(p, g, now);
		}
		Long shield = SHIELDS.get(u);
		if (shield != null) {
			tickShield(p, shield, now);
		}
		BlueBeetleState s = BlueBeetle.state(p);
		if (s.fx.chargeStart != 0L) {
			tickCannon(p, s, now);
			s = BlueBeetle.state(p);
		}
		if (s.fx.bubbleTarget != -1) {
			tickBubble(p, s, now);
			s = BlueBeetle.state(p);
		}
		if (s.camoUntil != 0L) {
			if (s.camoUntil <= now) {
				breakCamo(p);
			} else if (now % 10 == 0) {
				loseTrack(p);
			}
		}
		if (s.unleashedUntil > now && now % 3 == 0) {
			level(p).sendParticles(BlueBeetle.RED, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.35, 0.8, 0.35, 0.0);
		}
		if (s.bladeCombo > 0 && s.fx.bladeUntil <= now) {
			fx(p, n -> n.bladeCombo = 0);
		}
	}
}
