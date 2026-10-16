package com.projecthero.mod.bluebeetle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.bluebeetle.data.BlueBeetleState;
import com.projecthero.mod.bluebeetle.entity.BlueBeetleEntityTypes;
import com.projecthero.mod.bluebeetle.entity.PlasmaBoltEntity;
import com.projecthero.mod.bluebeetle.entity.ScarabDroneEntity;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Blue Beetle's twelve moves (v0.16.5 rework). Each is gated by {@link #begin} (suited, off cooldown, enough Scarab
 * Energy -- free while Khaji-Da is unleashed; using any move drops Camouflage), runs as a small per-player session
 * ticked from {@link #tick}, and deals damage through {@link BlueBeetleCombat}. Flight is double-tap jump
 * ({@link BlueBeetle#toggleFlight}), not a key.
 *
 * <pre>
 *   R (1)  Plasma Blaster (hold)    Shift+R  Mega Cannon
 *   G (2)  Scarab Hammer            Shift+G  Scarab Grab
 *   X (3)  Scarab Swarm             Shift+X  Bubble Prison
 *   Z (4)  Plasma Spikes            Shift+Z  Shield Pulse
 *   V (5)  Energy Shield (hold)     Shift+V  Camouflage
 *   C (6)  Energy Blade (toggle)    Shift+C  Khaji-Da Unleashed (the takeover, {@link KhajiDaTakeover})
 * </pre>
 */
public final class BlueBeetleAbilities {
	public static final String BLAST = "plasma_blaster";
	public static final String CANNON = "mega_cannon";
	public static final String HAMMER = "scarab_hammer";
	public static final String GRAB = "scarab_grab";
	public static final String SWARM = "scarab_swarm";
	public static final String BUBBLE = "bubble_prison";
	public static final String SPIKES = "plasma_spikes";
	public static final String PULSE = "shield_pulse";
	public static final String SHIELD = "energy_shield";
	public static final String CAMO = "camouflage";
	public static final String BLADE = "energy_blade";
	public static final String UNLEASH = "khaji_da_unleashed";

	private record Grab(int target, long start) {
	}

	private static final class Spikes {
		final long start;
		final Set<Integer> hit = new HashSet<>();
		/** x, z, dx, dz, then the ground y under each spike (NaN = no ground there). */
		double[] line;

		Spikes(long start) {
			this.start = start;
		}
	}

	private static final Map<UUID, Long> BLASTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> HAMMERS = new ConcurrentHashMap<>();
	private static final Map<UUID, Grab> GRABS = new ConcurrentHashMap<>();
	private static final Map<UUID, Spikes> SPIKE_RUNS = new ConcurrentHashMap<>();
	/** When the held shield went up (its upkeep clock). */
	private static final Map<UUID, Long> SHIELDS = new ConcurrentHashMap<>();

	private BlueBeetleAbilities() {
	}

	static void clearSessionState() {
		BLASTS.clear();
		HAMMERS.clear();
		GRABS.clear();
		SPIKE_RUNS.clear();
		SHIELDS.clear();
	}

	public static void clear(ServerPlayer p) {
		UUID id = p.getUUID();
		BLASTS.remove(id);
		HAMMERS.remove(id);
		GRABS.remove(id);
		SPIKE_RUNS.remove(id);
		SHIELDS.remove(id);
		ScarabDroneEntity.recallAll(p);
		breakCamo(p);
	}

	public static boolean running(ServerPlayer p, String id) {
		UUID u = p.getUUID();
		BlueBeetleState s = BlueBeetle.state(p);
		return switch (id) {
			case BLAST -> BLASTS.containsKey(u);
			case CANNON -> s.fx.chargeStart != 0L;
			case HAMMER -> HAMMERS.containsKey(u);
			case GRAB -> GRABS.containsKey(u);
			case SPIKES -> SPIKE_RUNS.containsKey(u);
			case SHIELD -> BlueBeetle.shieldUp(p);
			case CAMO -> s.camo;
			case BLADE -> s.fx.blade;
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
		if (!CAMO.equals(id)) {
			breakCamo(p); // v0.16.5 (user): using any ability shows him
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
		boolean rightMain = p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
		return eye.add(look.scale(0.9)).add(right.scale(rightMain ? 0.35 : -0.35)).add(0, -0.35, 0);
	}

	/** Where he faces on the ground plane (never zero). */
	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 flat = new Vec3(l.x, 0, l.z);
		if (flat.lengthSqr() < 1.0e-4) {
			double yaw = Math.toRadians(p.getYRot());
			flat = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		}
		return flat.normalize();
	}

	// ================================================================ R: Plasma Blaster (held)

	public static void startBlast(ServerPlayer p) {
		if (BLASTS.containsKey(p.getUUID()) || !begin(p, BLAST, 0f)) {
			return;
		}
		BLASTS.put(p.getUUID(), p.level().getGameTime());
		fx(p, n -> n.fx.blasting = true); // the blaster builds itself round the forearm
		fireBolt(p);
	}

	public static void stopBlast(ServerPlayer p) {
		if (BLASTS.remove(p.getUUID()) != null) {
			cooldown(p, BLAST, BlueBeetleConfig.BLAST_COOLDOWN);
		}
		if (BlueBeetle.state(p).fx.blasting) {
			fx(p, n -> n.fx.blasting = false); // ... and comes apart again
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
		PlasmaBoltEntity.shoot(p, from, dir.scale(BlueBeetleConfig.BLAST_SPEED));
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
		// the muzzle of the twin cannon, in front of the joined fists
		Vec3 h = p.getEyePosition().add(p.getLookAngle().scale(1.3)).add(0, -0.4, 0);
		if (age % 4 == 0) {
			// a few sparks only: the twin cannon building on his arms is the show
			level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, h.x, h.y, h.z, 1 + (int) (age / 20), 0.08, 0.08, 0.08, 0.05);
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
			n.animId = BlueBeetleState.ANIM_CANNON;
			n.animStart = now - BlueBeetleConfig.CANNON_CHARGE_TICKS; // the recoil half of the pose
		});
		ServerLevel level = level(p);
		level.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		level.sendParticles(BlueBeetle.BLUE_BIG, end.x, end.y, end.z, 30, 0.6, 0.6, 0.6, 0.0);
		sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.3f);
		sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.6f);
		AbilityHelpers.launchSelf(p, p.getDeltaMovement().add(dir.scale(-0.4)));
	}

	// ================================================================ G: Scarab Hammer

	/** The arm forms a hammer of energy and smashes it down in front of him (20, knock-up). */
	public static void hammer(ServerPlayer p) {
		if (HAMMERS.containsKey(p.getUUID()) || !begin(p, HAMMER, BlueBeetleConfig.HAMMER_COST)) {
			return;
		}
		cooldown(p, HAMMER, BlueBeetleConfig.HAMMER_COOLDOWN);
		BlueBeetle.setAnim(p, BlueBeetleState.ANIM_HAMMER);
		HAMMERS.put(p.getUUID(), p.level().getGameTime());
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.7f);
		sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.8f, 0.8f);
	}

	private static void tickHammer(ServerPlayer p, long start, long now) {
		long age = now - start;
		if (!BlueBeetle.suited(p) || !p.isAlive()) {
			HAMMERS.remove(p.getUUID());
			return;
		}
		if (age < BlueBeetleConfig.HAMMER_HIT_TICK) {
			return;
		}
		HAMMERS.remove(p.getUUID());
		Vec3 fwd = flatLook(p);
		Vec3 base = p.position();
		for (LivingEntity e : BlueBeetleCombat.within(p, base.add(0, 1.0, 0), BlueBeetleConfig.HAMMER_REACH + 1.5,
				e -> BlueBeetleCombat.isTarget(p, e))) {
			Vec3 rel = new Vec3(e.getX() - base.x, 0, e.getZ() - base.z);
			double dist = rel.length() - e.getBbWidth() * 0.5;
			if (dist > BlueBeetleConfig.HAMMER_REACH || Math.abs(e.getY() - base.y) > 2.5) {
				continue;
			}
			if (dist > 1.0 && rel.normalize().dot(fwd) < BlueBeetleConfig.HAMMER_ARC) {
				continue;
			}
			BlueBeetleCombat.strike(p, e, base, BlueBeetleConfig.HAMMER_DAMAGE, 0.7, 0.8);
		}
		ServerLevel level = level(p);
		Vec3 at = base.add(fwd.scale(1.9));
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 1, 0, 0, 0, 0);
		for (int i = 0; i < 14; i++) {
			double a = Math.PI * 2 * i / 14;
			level.sendParticles(BlueBeetle.BLUE_BIG, at.x + Math.cos(a) * 1.4, at.y + 0.15, at.z + Math.sin(a) * 1.4, 1, 0, 0.05, 0, 0.0);
		}
		BlockState under = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
		if (!under.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), at.x, at.y + 0.1, at.z, 24, 0.8, 0.1, 0.8, 0.15);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.4f);
	}

	// ================================================================ Shift+G: Scarab Grab

	/** Two tentacles shoot out of his back at what he aims at, strike it once each, and knock it far back. */
	public static void grab(ServerPlayer p) {
		if (GRABS.containsKey(p.getUUID())) {
			return;
		}
		LivingEntity[] hit = new LivingEntity[1];
		BlueBeetleCombat.ray(p, BlueBeetleConfig.GRAB_RANGE, 0.6, hit);
		if (hit[0] == null || !BlueBeetleCombat.isTarget(p, hit[0])) {
			if (BlueBeetle.canAct(p)) {
				BlueBeetle.say(p, "message.projecthero.blue_beetle.grab_nothing", ChatFormatting.GRAY);
			}
			return;
		}
		if (!begin(p, GRAB, BlueBeetleConfig.GRAB_COST)) {
			return;
		}
		cooldown(p, GRAB, BlueBeetleConfig.GRAB_COOLDOWN);
		LivingEntity t = hit[0];
		long now = p.level().getGameTime();
		GRABS.put(p.getUUID(), new Grab(t.getId(), now));
		fx(p, n -> {
			n.fx.grabTarget = t.getId();
			n.fx.grabStart = now;
			n.animId = BlueBeetleState.ANIM_GRAB;
			n.animStart = now;
		});
		sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.8f);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 0.8f, 0.7f);
	}

	private static void tickGrab(ServerPlayer p, Grab g, long now) {
		long age = now - g.start();
		Entity e = p.level().getEntity(g.target());
		if (!(e instanceof LivingEntity t) || !t.isAlive() || !BlueBeetle.suited(p) || age >= BlueBeetleConfig.GRAB_TICKS) {
			endGrab(p);
			return;
		}
		if (age == BlueBeetleConfig.GRAB_FIRST_HIT || age == BlueBeetleConfig.GRAB_SECOND_HIT) {
			boolean last = age == BlueBeetleConfig.GRAB_SECOND_HIT;
			BlueBeetleCombat.strike(p, t, p.position(), BlueBeetleConfig.GRAB_DAMAGE, last ? 2.8 : 0.2, last ? 0.55 : 0.0);
			level(p).playSound(null, t.getX(), t.getY(), t.getZ(), last ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_STRONG,
					SoundSource.PLAYERS, 1.0f, last ? 0.7f : 1.1f);
			Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
			level(p).sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.2);
		}
	}

	private static void endGrab(ServerPlayer p) {
		GRABS.remove(p.getUUID());
		if (BlueBeetle.state(p).fx.grabTarget != -1) {
			fx(p, n -> n.fx.grabTarget = -1);
		}
	}

	// ================================================================ X: Scarab Swarm / Shift+X: Bubble Prison

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

	// ================================================================ Z: Plasma Spikes / Shift+Z: Shield Pulse

	/** He slams a fist down and a line of energy spikes erupts along the ground in front of him, one a tick. */
	public static void spikes(ServerPlayer p) {
		if (SPIKE_RUNS.containsKey(p.getUUID()) || !begin(p, SPIKES, BlueBeetleConfig.SPIKE_COST)) {
			return;
		}
		cooldown(p, SPIKES, BlueBeetleConfig.SPIKE_COOLDOWN);
		BlueBeetle.setAnim(p, BlueBeetleState.ANIM_SPIKES);
		SPIKE_RUNS.put(p.getUUID(), new Spikes(p.level().getGameTime()));
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.2f);
	}

	/** Game time spike {@code i} breaks the ground, from the slam ({@code fx.spikeStart}). */
	public static long spikeTime(long slam, int i) {
		return slam + 1 + i;
	}

	private static void tickSpikes(ServerPlayer p, Spikes run, long now) {
		long age = now - run.start;
		if (!BlueBeetle.suited(p) || !p.isAlive()) {
			endSpikes(p);
			return;
		}
		ServerLevel level = level(p);
		long slam = run.start + BlueBeetleConfig.SPIKE_SLAM_TICK;
		if (age == BlueBeetleConfig.SPIKE_SLAM_TICK) {
			Vec3 d = flatLook(p);
			double x0 = p.getX() + d.x * BlueBeetleConfig.SPIKE_FIRST;
			double z0 = p.getZ() + d.z * BlueBeetleConfig.SPIKE_FIRST;
			double[] line = new double[4 + BlueBeetleConfig.SPIKE_COUNT];
			line[0] = x0;
			line[1] = z0;
			line[2] = d.x;
			line[3] = d.z;
			double y = p.getY();
			for (int i = 0; i < BlueBeetleConfig.SPIKE_COUNT; i++) {
				double g = ground(level, x0 + d.x * i, y, z0 + d.z * i);
				line[4 + i] = g;
				if (!Double.isNaN(g)) {
					y = g; // follow the ground up and down hills
				}
			}
			run.line = line;
			List<Double> synced = new ArrayList<>();
			for (double v : line) {
				synced.add(Double.isNaN(v) ? -9999.0 : v);
			}
			fx(p, n -> {
				n.fx.spikeStart = slam;
				n.fx.spikeLine = synced;
			});
			Vec3 fist = p.position().add(d.scale(0.8));
			level.sendParticles(ParticleTypes.EXPLOSION, fist.x, fist.y + 0.2, fist.z, 1, 0, 0, 0, 0);
			level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.5f);
		}
		if (run.line == null) {
			return;
		}
		for (int i = 0; i < BlueBeetleConfig.SPIKE_COUNT; i++) {
			if (spikeTime(slam, i) != now || Double.isNaN(run.line[4 + i])) {
				continue;
			}
			Vec3 at = new Vec3(run.line[0] + run.line[2] * i, run.line[4 + i], run.line[1] + run.line[3] * i);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
					new AABB(at, at).inflate(BlueBeetleConfig.SPIKE_RADIUS + 0.5, 0, BlueBeetleConfig.SPIKE_RADIUS + 0.5).expandTowards(0, 2.6, 0)
							.expandTowards(0, -0.8, 0),
					e -> e != p && BlueBeetleCombat.isTarget(p, e))) {
				double dx = e.getX() - at.x, dz = e.getZ() - at.z;
				if (Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() * 0.5 > BlueBeetleConfig.SPIKE_RADIUS || !run.hit.add(e.getId())) {
					continue;
				}
				BlueBeetleCombat.strike(p, e, at.subtract(run.line[2], 0, run.line[3]), BlueBeetleConfig.SPIKE_DAMAGE, 0.3, 1.0);
			}
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.6, at.z, 5, 0.2, 0.5, 0.2, 0.1);
			BlockState under = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
			if (!under.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), at.x, at.y + 0.1, at.z, 8, 0.3, 0.05, 0.3, 0.1);
			}
			if (i % 2 == 0) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f + i * 0.03f);
			}
		}
		if (now > spikeTime(slam, BlueBeetleConfig.SPIKE_COUNT - 1) + BlueBeetleConfig.SPIKE_LIFE) {
			endSpikes(p);
		}
	}

	private static void endSpikes(ServerPlayer p) {
		SPIKE_RUNS.remove(p.getUUID());
		if (BlueBeetle.state(p).fx.spikeStart != 0L) {
			fx(p, n -> {
				n.fx.spikeStart = 0L;
				n.fx.spikeLine = List.of();
			});
		}
	}

	/** The top of the ground near ({@code x}, {@code y}, {@code z}): searched 2 blocks up and 4 down; NaN if none. */
	private static double ground(ServerLevel level, double x, double y, double z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int by = (int) Math.floor(y);
		for (int dy = 2; dy >= -4; dy--) {
			pos.set(Math.floor(x), by + dy - 1, Math.floor(z));
			BlockState below = level.getBlockState(pos);
			BlockState at = level.getBlockState(pos.above());
			if (below.isFaceSturdy(level, pos, Direction.UP) && !at.isFaceSturdy(level, pos.above(), Direction.UP)) {
				return by + dy;
			}
		}
		return Double.NaN;
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

	// ================================================================ V: Energy Shield (held) / Shift+V: Camouflage

	/**
	 * V held: the shield goes up with a fresh 100-point pool (v0.16.5, user: "the same as the cyborgs shield" -- Cyborg's
	 * Defense Matrix). It soaks hits and projectiles from the front ({@code BlueBeetleDamage}) until V is let go or the
	 * pool runs dry and it breaks; either way an 8 s cooldown. No energy drain.
	 */
	public static void raiseShield(ServerPlayer p) {
		if (BlueBeetle.shieldUp(p) || !begin(p, SHIELD, 0f)) {
			return;
		}
		SHIELDS.put(p.getUUID(), p.level().getGameTime());
		fx(p, n -> {
			n.fx.shield = true;
			n.fx.shieldHp = BlueBeetleConfig.SHIELD_HP;
		});
		sound(p, SoundEvents.BEACON_ACTIVATE, 0.7f, 1.8f);
	}

	public static void lowerShield(ServerPlayer p) {
		if (SHIELDS.remove(p.getUUID()) != null || BlueBeetle.state(p).fx.shield) {
			fx(p, n -> {
				n.fx.shield = false;
				n.fx.shieldHp = 0f;
			});
			cooldown(p, SHIELD, BlueBeetleConfig.SHIELD_COOLDOWN); // v0.16.5 (user): 8 s before it goes up again
			sound(p, SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.8f);
		}
	}

	private static void tickShield(ServerPlayer p, long start, long now) {
		if (!BlueBeetle.suited(p) || !p.isAlive()) {
			lowerShield(p);
		} else if (!BlueBeetle.shieldUp(p)) {
			SHIELDS.remove(p.getUUID()); // it broke (BlueBeetleDamage put it down and started the cooldown)
		}
	}

	/** The pool ran dry: the shield breaks (the same 8 s cooldown as letting go). */
	static void breakShield(ServerPlayer p) {
		SHIELDS.remove(p.getUUID());
		fx(p, n -> {
			n.fx.shield = false;
			n.fx.shieldHp = 0f;
		});
		cooldown(p, SHIELD, BlueBeetleConfig.SHIELD_COOLDOWN);
	}

	/**
	 * v0.16.5 (user: "make it last as long as the player doesnt touch anything, hit anything, fly or use an ability"):
	 * invisible with no timer. Pressing it again also drops it. Its cooldown starts when it breaks.
	 */
	public static void camo(ServerPlayer p) {
		BlueBeetleState s = BlueBeetle.state(p);
		if (s.camo) {
			breakCamo(p);
			return;
		}
		if (s.flying || p.getAbilities().flying) {
			if (BlueBeetle.canAct(p)) {
				BlueBeetle.say(p, "message.projecthero.blue_beetle.camo_flying", ChatFormatting.GRAY);
			}
			return;
		}
		if (!begin(p, CAMO, BlueBeetleConfig.CAMO_COST)) {
			return;
		}
		long now = p.level().getGameTime();
		fx(p, n -> {
			n.camo = true;
			n.animId = BlueBeetleState.ANIM_CAMO;
			n.animStart = now;
		});
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
		loseTrack(p);
		level(p).sendParticles(BlueBeetle.BLUE, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.35, 0.8, 0.35, 0.0);
		sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);
	}

	private static void loseTrack(ServerPlayer p) {
		for (Mob m : level(p).getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), m -> m.getTarget() == p)) {
			m.setTarget(null);
		}
	}

	/** Camouflage ends (he touched, hit or used something, flew, suited down, or pressed it again); its cooldown starts. */
	public static void breakCamo(ServerPlayer p) {
		BlueBeetleState s = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.BLUE_BEETLE_STATE, null);
		if (s == null || !s.camo) {
			return;
		}
		fx(p, n -> n.camo = false);
		cooldown(p, CAMO, BlueBeetleConfig.CAMO_COOLDOWN);
		p.removeEffect(MobEffects.INVISIBILITY);
		level(p).sendParticles(BlueBeetle.BLUE, p.getX(), p.getY() + 1.0, p.getZ(), 15, 0.35, 0.8, 0.35, 0.0);
		sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.6f, 0.8f);
	}

	/**
	 * Touching anything -- using or hitting a block, an item or an entity -- shows a camouflaged Blue Beetle. And while
	 * Khaji-Da has the body (the takeover) the player's own clicks do nothing at all.
	 */
	static void registerCamoBreakers() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			touched(player);
			return KhajiDaTakeover.active(player) ? InteractionResult.FAIL : InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			touched(player);
			return KhajiDaTakeover.active(player) ? InteractionResultHolder.fail(player.getItemInHand(hand))
					: InteractionResultHolder.pass(player.getItemInHand(hand));
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			touched(player);
			return KhajiDaTakeover.active(player) ? InteractionResult.FAIL : InteractionResult.PASS;
		});
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			touched(player);
			return KhajiDaTakeover.active(player) ? InteractionResult.FAIL : InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			touched(player);
			return KhajiDaTakeover.active(player) ? InteractionResult.FAIL : InteractionResult.PASS;
		});
	}

	static void touched(Player player) {
		if (player instanceof ServerPlayer sp && BlueBeetle.hasPower(sp)) {
			breakCamo(sp);
		}
	}

	// ================================================================ C: Energy Blade (toggle) / Shift+C: Khaji-Da Unleashed

	/** v0.16.5 (user): C forms the energy blade on the arm, C again folds it away; +7 melee while it is out. */
	public static void toggleBlade(ServerPlayer p) {
		long now = p.level().getGameTime();
		if (BlueBeetle.state(p).fx.blade) {
			if (!BlueBeetle.suited(p)) {
				return;
			}
			breakCamo(p);
			fx(p, n -> {
				n.fx.blade = false;
				n.animId = BlueBeetleState.ANIM_BLADE;
				n.animStart = now;
			});
			BlueBeetle.reconcile(p);
			sound(p, SoundEvents.BEACON_DEACTIVATE, 0.5f, 2.0f);
			return;
		}
		if (!begin(p, BLADE, BlueBeetleConfig.BLADE_COST)) {
			return;
		}
		cooldown(p, BLADE, BlueBeetleConfig.BLADE_COOLDOWN);
		fx(p, n -> {
			n.fx.blade = true;
			n.animId = BlueBeetleState.ANIM_BLADE;
			n.animStart = now;
		});
		BlueBeetle.reconcile(p);
		sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f);
		sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.9f);
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
		KhajiDaTakeover.start(p); // v0.16.5 (user): Khaji-Da takes the body for the 15 s
		ServerLevel level = level(p);
		level.sendParticles(BlueBeetle.RED, p.getX(), p.getY() + 1.0, p.getZ(), 60, 0.5, 1.0, 0.5, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.5, 1.0, 0.5, 0.3);
		sound(p, SoundEvents.WARDEN_ROAR, 0.6f, 1.6f);
		sound(p, SoundEvents.BEACON_ACTIVATE, 1.0f, 0.6f);
		KhajiDa.speak(p, KhajiDa.Topic.UNLEASHED);
	}

	/** The takeover is over (time up, or nothing left to fight); {@code speak}: Khaji-Da's usual parting line. */
	static void endUnleashed(ServerPlayer p, boolean speak) {
		KhajiDaTakeover.stop(p);
		long now = p.level().getGameTime();
		fx(p, n -> {
			n.unleashedUntil = 0L;
			n.energy = 0f;
			n.slowRegenUntil = now + BlueBeetleConfig.UNLEASH_AFTER_TICKS;
		});
		sound(p, SoundEvents.BEACON_DEACTIVATE, 1.0f, 0.6f);
		if (speak) {
			KhajiDa.speak(p, KhajiDa.Topic.UNLEASH_END);
		}
	}

	// ================================================================ tick

	static void tick(ServerPlayer p) {
		UUID u = p.getUUID();
		long now = p.level().getGameTime();
		Long blast = BLASTS.get(u);
		if (blast != null) {
			tickBlast(p, blast, now);
		}
		Long hammer = HAMMERS.get(u);
		if (hammer != null) {
			tickHammer(p, hammer, now);
		}
		Grab g = GRABS.get(u);
		if (g != null) {
			tickGrab(p, g, now);
		}
		Spikes sp = SPIKE_RUNS.get(u);
		if (sp != null) {
			tickSpikes(p, sp, now);
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
		if (s.fx.blasting && !BLASTS.containsKey(u)) {
			fx(p, n -> n.fx.blasting = false);
		}
		if (s.fx.grabTarget != -1 && !GRABS.containsKey(u)) {
			fx(p, n -> n.fx.grabTarget = -1);
		}
		if (s.camo && now % 10 == 0) {
			loseTrack(p);
		}
		if (s.unleashedUntil > now && now % 3 == 0) {
			level(p).sendParticles(BlueBeetle.RED, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.35, 0.8, 0.35, 0.0);
		}
	}
}
