package com.projecthero.mod.ironman.ability.era1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManIntegrity;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.ability.IronManComicAccess;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Era I: everything the Mk I Cave-Forged / Mk II Golden Avenger moves leave RUNNING -- the flamethrower
 * stream, the Haymaker / Charged Repulsor wind-ups, rocket-jump fall safety, the Brace, Reactor Overload, the boot hover,
 * the Ground Pound slam, the jet skates, the magnetic shield, Golden Glare's dazzled mobs and the Fire Wall areas.
 *
 * <p>Static server state keyed by player UUID (+ the fire-wall list), cleared by {@link #clearSessionState}
 * ({@code ServerStateReset}) and per player on disconnect. Ticked on the server tick (not the suit ticker), and every
 * status ends the moment its suit comes off, is swapped or is disabled ({@link #shutDown}). The damage hooks
 * ({@link #incomingFactor}, {@link #outgoingMultiplier}) are read by {@code IronManComicState}.
 */
public final class EraOneMk12State {
	// ------------------------------------------------------------------ the numbers (plan rows Mk I / Mk II)
	/** Flamethrower: a 6-block cone, 8 damage a second (2 every 5 ticks) + burning. */
	public static final double FLAME_RANGE = 6.0;
	public static final double FLAME_HALF_DEG = 30.0;
	public static final int FLAME_HIT_EVERY = 5;
	public static final int FLAME_FIRE_TICKS = 80;
	/** Fire Wall: a 5-wide line of fire 3 blocks ahead for 4 s, 3 damage a second to anything touching it. */
	public static final double FIRE_WALL_AHEAD = 3.0;
	public static final double FIRE_WALL_HALF_WIDTH = 2.5;
	public static final int FIRE_WALL_TICKS = 4 * 20;
	/** Haymaker / Charged Repulsor: full power after a 1 s hold; let go sooner = scaled down to half; a 3 s hold fires itself. */
	public static final int CHARGE_TICKS = 20;
	public static final int CHARGE_MAX_HOLD = 60;
	/** Haymaker stagger: stunned (no target, barely moves) for 1.5 s. */
	public static final int STAGGER_TICKS = 30;
	/** Rocket jump / leap: fall-safe until landing or 5 s. */
	public static final int ROCKET_SAFE_TICKS = 100;
	/** Brace: 60% of every hit stopped, no knockback, rooted, 4 s. */
	public static final float BRACE_FACTOR = 0.4f;
	public static final int BRACE_TICKS = 4 * 20;
	/** Reactor Overload: armed for 10 s; the next hit lands x2, or (Mk I) the next 3 s of flamethrower. */
	public static final float OVERLOAD_MULT = 2f;
	public static final int OVERLOAD_ARMED_TICKS = 10 * 20;
	public static final int OVERLOAD_FLAME_TICKS = 3 * 20;
	/** Boot hover 5 s, Jet Skates 8 s, Magnetic Shield 5 s, Golden Glare 4 s, Smoke Screen 6 s. */
	public static final int HOVER_TICKS = 5 * 20;
	public static final int SKATES_TICKS = 8 * 20;
	public static final int SHIELD_TICKS = 5 * 20;
	public static final int GLARE_TICKS = 4 * 20;
	public static final int SMOKE_TICKS = 6 * 20;
	/** Ground Pound: the slam's radius, and how long the dive may last before it pounds where it is. */
	public static final double POUND_RADIUS = 4.0;
	public static final int POUND_MAX_TICKS = 60;

	private static final ResourceLocation BRACE_ROOT = ResourceLocation.fromNamespaceAndPath("projecthero", "e1_brace_root");
	private static final ResourceLocation BRACE_JUMP = ResourceLocation.fromNamespaceAndPath("projecthero", "e1_brace_jump");
	private static final ResourceLocation BRACE_KNOCK = ResourceLocation.fromNamespaceAndPath("projecthero", "e1_brace_knockback");
	private static final ResourceLocation SKATES_STEP = ResourceLocation.fromNamespaceAndPath("projecthero", "e1_skates_step");
	private static final DustParticleOptions GOLD = new DustParticleOptions(new org.joml.Vector3f(1f, 0.8f, 0.2f), 1.2f);

	private EraOneMk12State() {
	}

	// ------------------------------------------------------------------ per-player runtime

	static final class Rt {
		String suit;
		boolean flame;
		long flameStart;
		long hayStart;
		long chargeStart;
		long rocketUntil;
		long rocketStart;
		long braceUntil;
		long overloadUntil;
		long overloadFlameUntil;
		long hoverUntil;
		long skatesUntil;
		long shieldUntil;
		long smokeUntil;
		Vec3 smokeAt;
		boolean pounding;
		long poundStart;
		final Map<Integer, Long> glared = new HashMap<>();

		boolean idle(long now) {
			return !flame && hayStart == 0L && chargeStart == 0L && rocketUntil <= now && braceUntil <= now && overloadUntil <= now
					&& overloadFlameUntil <= now && hoverUntil == 0L && skatesUntil <= now && shieldUntil <= now && smokeUntil <= now
					&& !pounding && glared.isEmpty();
		}
	}

	/** A Fire Wall: a line {@code 2 * halfWidth} long across {@code right}, centred on {@code centre}. */
	static final class FireWall {
		final UUID owner;
		final ResourceKey<Level> dim;
		final Vec3 centre;
		final Vec3 right;
		final long born;
		final long until;

		FireWall(UUID owner, ResourceKey<Level> dim, Vec3 centre, Vec3 right, long born, long until) {
			this.owner = owner;
			this.dim = dim;
			this.centre = centre;
			this.right = right;
			this.born = born;
			this.until = until;
		}
	}

	private static final Map<UUID, Rt> RT = new HashMap<>();
	private static final List<FireWall> WALLS = new ArrayList<>();
	/** Set while the flamethrower deals its own damage, so {@link #outgoingMultiplier} knows the hit is flame. */
	private static final ThreadLocal<Boolean> FLAME_HIT = ThreadLocal.withInitial(() -> false);
	private static boolean initialized;

	static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(EraOneMk12State::serverTick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUUID()));
	}

	public static void clearSessionState() {
		RT.clear();
		WALLS.clear();
	}

	public static void forget(UUID player) {
		RT.remove(player);
		WALLS.removeIf(w -> w.owner.equals(player));
	}

	static Rt rt(ServerPlayer p, IronManSuit suit) {
		Rt r = RT.computeIfAbsent(p.getUUID(), k -> new Rt());
		r.suit = suit.id();
		return r;
	}

	static Rt peek(ServerPlayer p) {
		return RT.get(p.getUUID());
	}

	static void addFireWall(ServerPlayer p, Vec3 centre, Vec3 right) {
		long now = p.level().getGameTime();
		WALLS.removeIf(w -> w.owner.equals(p.getUUID())); // one wall at a time
		WALLS.add(new FireWall(p.getUUID(), p.level().dimension(), centre, right, now, now + FIRE_WALL_TICKS));
	}

	// ------------------------------------------------------------------ queries (HUD / gametests)

	public static boolean flaming(ServerPlayer p) {
		Rt r = peek(p);
		return r != null && r.flame;
	}

	public static boolean braced(ServerPlayer p) {
		Rt r = peek(p);
		return r != null && r.braceUntil > p.level().getGameTime();
	}

	public static boolean overloadArmed(ServerPlayer p) {
		Rt r = peek(p);
		return r != null && r.overloadUntil > p.level().getGameTime();
	}

	public static boolean hovering(ServerPlayer p) {
		Rt r = peek(p);
		return r != null && r.hoverUntil != 0L;
	}

	public static boolean skating(ServerPlayer p) {
		Rt r = peek(p);
		return r != null && r.skatesUntil > p.level().getGameTime();
	}

	public static boolean magnetShield(ServerPlayer p) {
		Rt r = peek(p);
		return r != null && r.shieldUntil > p.level().getGameTime();
	}

	public static boolean rocketSafe(ServerPlayer p) {
		Rt r = peek(p);
		return r != null && r.rocketUntil > p.level().getGameTime();
	}

	public static boolean pounding(ServerPlayer p) {
		Rt r = peek(p);
		return r != null && r.pounding;
	}

	public static boolean glared(ServerPlayer p, LivingEntity e) {
		Rt r = peek(p);
		Long until = r == null ? null : r.glared.get(e.getId());
		return until != null && until > p.level().getGameTime();
	}

	public static int fireWalls(ServerPlayer p) {
		int n = 0;
		for (FireWall w : WALLS) {
			n += w.owner.equals(p.getUUID()) ? 1 : 0;
		}
		return n;
	}

	// ------------------------------------------------------------------ damage hooks (read by IronManComicState)

	/** Incoming: the Brace stops 60%; the Magnetic Shield stops every projectile outright. 1 = untouched. */
	public static float incomingFactor(ServerPlayer player, DamageSource source) {
		Rt r = peek(player);
		if (r == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return 1f;
		}
		long now = player.level().getGameTime();
		float f = 1f;
		if (r.shieldUntil > now && (source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof Projectile)) {
			f = 0f;
			IronManSounds.play(player, IronManSounds.SHIELD_DEFLECT, 0.8f, 1.3f);
		}
		if (r.braceUntil > now) {
			f *= BRACE_FACTOR;
		}
		return f;
	}

	/**
	 * Outgoing: an armed Reactor Overload makes the wearer's next hit land x2 (and is spent); the flamethrower instead
	 * turns it into 3 s of double flame. 1 = untouched.
	 */
	public static float outgoingMultiplier(ServerPlayer attacker, LivingEntity target) {
		Rt r = peek(attacker);
		if (r == null) {
			return 1f;
		}
		long now = attacker.level().getGameTime();
		if (FLAME_HIT.get()) {
			if (r.overloadFlameUntil > now) {
				return OVERLOAD_MULT;
			}
			if (r.overloadUntil > now) {
				r.overloadUntil = 0L;
				r.overloadFlameUntil = now + OVERLOAD_FLAME_TICKS;
				attacker.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk12.overload_flame"), true);
				return OVERLOAD_MULT;
			}
			return 1f;
		}
		if (r.overloadUntil > now) {
			r.overloadUntil = 0L;
			if (attacker.level() instanceof ServerLevel sl) {
				Vec3 c = target.position().add(0, target.getBbHeight() * 0.6, 0);
				sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
				sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 30, 0.4, 0.4, 0.4, 0.3);
				sl.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 16, 0.3, 0.3, 0.3, 0.08);
				IronManComicAccess.soundAt(sl, c, IronManSounds.ENERGY_IMPACT, 1.3f, 0.6f);
			}
			return OVERLOAD_MULT;
		}
		return 1f;
	}

	/** Run {@code r} as a flamethrower hit (see {@link #outgoingMultiplier}). */
	static void asFlame(Runnable r) {
		FLAME_HIT.set(true);
		try {
			r.run();
		} finally {
			FLAME_HIT.set(false);
		}
	}

	// ------------------------------------------------------------------ the tick

	private static void serverTick(MinecraftServer server) {
		if (RT.isEmpty() && WALLS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Rt>> it = RT.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Rt> en = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
			if (p == null) {
				it.remove();
				continue;
			}
			Rt r = en.getValue();
			if (!p.isAlive() || r.suit == null || !r.suit.equals(IronManArmor.wornSuitId(p)) || IronManIntegrity.disabled(p, r.suit)
					|| !IronManArmor.hasChestplate(p, r.suit)) {
				shutDown(p, r);
				it.remove();
				continue;
			}
			tickPlayer(p, r);
			if (r.idle(p.level().getGameTime())) {
				it.remove();
			}
		}
		for (Iterator<FireWall> it = WALLS.iterator(); it.hasNext();) {
			FireWall w = it.next();
			ServerLevel level = server.getLevel(w.dim);
			if (level == null || w.until <= level.getGameTime()) {
				it.remove();
				continue;
			}
			tickWall(level, server.getPlayerList().getPlayer(w.owner), w);
		}
	}

	/** Gametest helper: one server tick of everything here. */
	public static void tickForTests(MinecraftServer server) {
		serverTick(server);
	}

	private static void tickPlayer(ServerPlayer p, Rt r) {
		ServerLevel level = (ServerLevel) p.level();
		long now = level.getGameTime();
		IronManSuit suit = IronManSuits.byId(r.suit);
		if (r.flame) {
			if (suit == null || !EraOneMk1Moves.flameTick(p, suit, r, now)) {
				r.flame = false;
			}
		}
		if (r.hayStart != 0L) {
			EraOneMk1Moves.haymakerWindup(p, r, now);
		}
		if (r.chargeStart != 0L) {
			EraOneMk2Moves.chargeWindup(p, r, now);
		}
		if (r.rocketUntil > now) {
			p.resetFallDistance();
			if (now - r.rocketStart < 14 && now % 2 == 0) {
				level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY(), p.getZ(), 6, 0.15, 0.05, 0.15, 0.02);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY(), p.getZ(), 2, 0.2, 0.05, 0.2, 0.01);
			}
			if (now - r.rocketStart > 6 && (p.onGround() || p.isInWater())) {
				r.rocketUntil = 0L;
			}
		}
		if (r.braceUntil > now) {
			IronManAbilityFx.hold(p, IronManAbilityFx.COMIC_GUARD);
			if (now % 6 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.5, 0.05, 0.5, 0.02);
				level.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.4, 0.6, 0.4, 0.02);
			}
		} else if (r.braceUntil != 0L) {
			endBrace(p, r);
		}
		if (r.overloadUntil > now && now % 5 == 0) {
			Vec3 c = IronManComicAccess.chest(p);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 3, 0.15, 0.15, 0.15, 0.05);
		} else if (r.overloadUntil != 0L && r.overloadUntil <= now) {
			r.overloadUntil = 0L;
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk12.overload_faded"), true);
		}
		if (r.hoverUntil != 0L) {
			EraOneMk2Moves.hoverTick(p, suit, r, now);
		}
		if (r.pounding) {
			EraOneMk2Moves.poundTick(p, r, now);
		}
		if (r.skatesUntil > now) {
			if (p.onGround() && now % 2 == 0) {
				level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.05, p.getZ(), 3, 0.2, 0.0, 0.2, 0.01);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.05, p.getZ(), 1, 0.2, 0.0, 0.2, 0.05);
				level.sendParticles(GOLD, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0.2, 0.0, 0.2, 0.0);
			}
			if (now % 10 == 0 && p.onGround()) {
				IronManSounds.loop(p, IronManSounds.THRUSTER, 0.35f, 1.6f);
			}
		} else if (r.skatesUntil != 0L) {
			endSkates(p, r);
		}
		if (r.shieldUntil > now) {
			EraOneMk2Moves.shieldTick(p, r, now);
		} else if (r.shieldUntil != 0L) {
			r.shieldUntil = 0L;
			IronManSounds.play(p, IronManSounds.SHIELD_DOWN, 0.7f, 1.2f);
		}
		if (r.smokeUntil > now && r.smokeAt != null && p.position().distanceToSqr(r.smokeAt) < 5.5 * 5.5) {
			// standing in your own smoke: nothing out to 16 blocks can keep you as its target
			for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16.0), m -> m.getTarget() == p)) {
				m.setTarget(null);
			}
		}
		if (!r.glared.isEmpty()) {
			for (Iterator<Map.Entry<Integer, Long>> it = r.glared.entrySet().iterator(); it.hasNext();) {
				Map.Entry<Integer, Long> g = it.next();
				Entity e = level.getEntity(g.getKey());
				if (g.getValue() <= now || !(e instanceof Mob m) || !m.isAlive()) {
					it.remove();
					continue;
				}
				// dazzled: it cannot pick the wearer out
				if (m.getTarget() == p) {
					m.setTarget(null);
				}
				if (now % 4 == 0) {
					level.sendParticles(GOLD, m.getX(), m.getEyeY() + 0.2, m.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
				}
			}
		}
	}

	private static void tickWall(ServerLevel level, ServerPlayer owner, FireWall w) {
		long age = level.getGameTime() - w.born;
		Vec3 a = w.centre.add(w.right.scale(-FIRE_WALL_HALF_WIDTH));
		Vec3 b = w.centre.add(w.right.scale(FIRE_WALL_HALF_WIDTH));
		if (age % 2 == 0) {
			for (double d = 0; d <= FIRE_WALL_HALF_WIDTH * 2 + 0.01; d += 0.5) {
				Vec3 at = a.add(w.right.scale(d));
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 2, 0.12, 0.35, 0.12, 0.01);
				if (((int) (d * 2) + age) % 4 == 0) {
					level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.4, at.z, 1, 0.1, 0.2, 0.1, 0.01);
				}
			}
		}
		if (age % 20 == 0) {
			IronManComicAccess.soundAt(level, w.centre, SoundEvents.FIRE_AMBIENT, 1.0f, 0.8f);
		}
		if (owner == null || age % 10 != 0) {
			return;
		}
		// 3 a second (1.5 every half second) to everything touching the line
		for (LivingEntity e : IronManComicAccess.along(owner, a.add(0, 0.8, 0), b.add(0, 0.8, 0), 0.7)) {
			IronManComicAccess.burn(owner, e, 1.5f, FLAME_FIRE_TICKS);
		}
	}

	// ------------------------------------------------------------------ ends

	static void startBrace(ServerPlayer p, Rt r, long now) {
		r.braceUntil = now + BRACE_TICKS;
		modifier(p, Attributes.MOVEMENT_SPEED, BRACE_ROOT, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(p, Attributes.JUMP_STRENGTH, BRACE_JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(p, Attributes.KNOCKBACK_RESISTANCE, BRACE_KNOCK, 1.0, AttributeModifier.Operation.ADD_VALUE);
	}

	static void endBrace(ServerPlayer p, Rt r) {
		r.braceUntil = 0L;
		clear(p, Attributes.MOVEMENT_SPEED, BRACE_ROOT);
		clear(p, Attributes.JUMP_STRENGTH, BRACE_JUMP);
		clear(p, Attributes.KNOCKBACK_RESISTANCE, BRACE_KNOCK);
		IronManSounds.play(p, IronManSounds.CLAMP, 0.8f, 1.3f);
	}

	static void startSkates(ServerPlayer p, Rt r, long now) {
		r.skatesUntil = now + SKATES_TICKS;
		modifier(p, Attributes.STEP_HEIGHT, SKATES_STEP, 0.5, AttributeModifier.Operation.ADD_VALUE);
	}

	static void endSkates(ServerPlayer p, Rt r) {
		r.skatesUntil = 0L;
		clear(p, Attributes.STEP_HEIGHT, SKATES_STEP);
		net.minecraft.world.effect.MobEffectInstance speed = p.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
		if (speed != null && speed.isAmbient()) {
			p.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
		}
	}

	/** The suit came off / was swapped / was disabled: every running status ends, held wind-ups are dropped. */
	static void shutDown(ServerPlayer p, Rt r) {
		r.flame = false;
		r.hayStart = 0L;
		r.chargeStart = 0L;
		r.pounding = false;
		r.overloadUntil = 0L;
		r.overloadFlameUntil = 0L;
		r.shieldUntil = 0L;
		r.smokeUntil = 0L;
		r.glared.clear();
		if (r.braceUntil != 0L) {
			endBrace(p, r);
		}
		if (r.skatesUntil != 0L) {
			endSkates(p, r);
		}
		if (r.hoverUntil != 0L) {
			r.hoverUntil = 0L;
			if (IronManFlight.isFlying(p)) {
				IronManFlight.setFlying(p, false);
			}
		}
	}

	private static void modifier(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
			ResourceLocation id, double amount, AttributeModifier.Operation op) {
		AttributeInstance inst = p.getAttribute(attr);
		if (inst != null) {
			inst.removeModifier(id);
			inst.addTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	private static void clear(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
			ResourceLocation id) {
		AttributeInstance inst = p.getAttribute(attr);
		if (inst != null) {
			inst.removeModifier(id);
		}
	}

	// ------------------------------------------------------------------ shared bits

	/** How far the ground (or water) is below the wearer's feet, up to {@code reach} (reach if none). */
	static double groundGap(ServerPlayer p, double reach) {
		Vec3 from = p.position().add(0, 0.05, 0);
		var hit = p.level().clip(new ClipContext(from, from.subtract(0, reach, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
		return hit.getType() == HitResult.Type.MISS ? reach : from.y - hit.getLocation().y;
	}
}
