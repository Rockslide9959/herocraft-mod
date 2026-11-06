package com.projecthero.mod.stand;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.stand.data.StandState;
import com.projecthero.mod.stand.network.TimeStopPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 <b>Star Platinum: The World</b> (Shift+Z) -- a custom time stop. Not vanilla's {@code /tick freeze}: that stops
 * the game clock (the mod's cooldowns and animations all run on absolute game time) and leaves players free. Instead:
 * <ul>
 *   <li>{@code StandTimeStopServerLevelMixin} skips {@code ServerLevel#tickNonPassenger} for every entity in the caster's
 *       dimension except the caster and its mount ({@link #frozen}); {@code StandTimeStopLevelMixin} skips block entities.
 *       The game clock keeps running.</li>
 *   <li>A projectile the caster throws or fires flies {@link #PROJECTILE_FREE_TICKS} ticks, then hangs in the air (canon)
 *       and flies on when time resumes.</li>
 *   <li>Damage the caster deals to a frozen creature is queued with its knockback ({@link #queue}) and lands all at once on
 *       resume: players at most {@link StandConfig#TIME_STOP_PLAYER_CAP} of their max health, bosses reduced.</li>
 *   <li>Inside the stop the caster's moves cost no Stamina and the bar doesn't refill; the stop is paid for when time resumes.</li>
 *   <li>Frozen players' ability keys, attacks, item use and block breaking are refused server-side; the caster can't use
 *       blocks or break them (fighting and moving only).</li>
 *   <li>Every client gets {@link TimeStopPayload}: a Stand user's client freezes everything but the caster and drains
 *       the colour; a non-Stand user's client freezes EVERYTHING, the caster too, with no cue at all -- they just see
 *       the world jump when it resumes (the user's rule: they can't perceive the Stand or its power).</li>
 * </ul>
 * Only one stop runs at a time. It ends early when the caster dies, logs out, changes dimension or loses the Stand, and
 * {@code ServerStateReset} clears it with the server.
 */
public final class TimeStop {
	/** A projectile the caster lets go of travels this many ticks before it stops in the air. */
	public static final int PROJECTILE_FREE_TICKS = 3;

	/** One queued hit-stack on a frozen victim. */
	static final class Queued {
		final LivingEntity victim;
		float damage;
		Vec3 knock = Vec3.ZERO;

		Queued(LivingEntity victim) {
			this.victim = victim;
		}
	}

	/** The running stop. */
	static final class Stop {
		final UUID caster;
		final int casterId;
		final ResourceKey<Level> dimension;
		final long end;
		/** 0 = the whole dimension (gametests use a small radius so the parallel tests around them keep running). */
		final double radius;
		final Vec3 centre;
		final Map<UUID, Queued> queue = new LinkedHashMap<>();

		Stop(UUID caster, int casterId, ResourceKey<Level> dimension, long end, double radius, Vec3 centre) {
			this.caster = caster;
			this.casterId = casterId;
			this.dimension = dimension;
			this.end = end;
			this.radius = radius;
			this.centre = centre;
		}
	}

	private static volatile Stop active;

	/** Shift+Z pressed, the voice line playing: the stop starts at {@code at}. */
	private record Windup(UUID caster, long at, int ticks, double radius) {
	}

	private static volatile Windup windup;
	/** Test hook: a radius for the next stop (0 = whole dimension). */
	public static double testRadius = 0.0;
	/** Test hook: a length for the next stop (0 = the real {@link StandConfig#TIME_STOP_TICKS}). */
	public static int testTicks = 0;

	private TimeStop() {
	}

	public static void clear() {
		active = null;
		windup = null;
	}

	/** The voice line is playing and the stop is about to start. */
	public static boolean windingUp() {
		return windup != null;
	}

	/** Test hook: start a winding-up stop now instead of at the end of the voice line. */
	public static void skipWindup(MinecraftServer server) {
		Windup w = windup;
		ServerPlayer caster = w == null ? null : server.getPlayerList().getPlayer(w.caster);
		windup = null;
		if (caster != null) {
			stopTime(caster, w);
		}
	}

	public static boolean active() {
		return active != null;
	}

	/** Game time the running stop ends (0 = none). */
	public static long endTime() {
		Stop s = active;
		return s == null ? 0L : s.end;
	}

	public static boolean isCaster(Entity e) {
		Stop s = active;
		return s != null && e != null && e.getUUID().equals(s.caster);
	}

	/** Is this entity held still by the running stop (server side)? */
	public static boolean frozen(Entity e) {
		Stop s = active;
		if (s == null || e == null || e.level().isClientSide() || e.level().dimension() != s.dimension) {
			return false;
		}
		return !exempt(s, e);
	}

	/** The block-entity tick of this level is held (server side; a test-radius stop leaves block entities alone). */
	public static boolean freezesBlockEntities(Level level) {
		Stop s = active;
		return s != null && !level.isClientSide() && s.radius <= 0.0 && level.dimension() == s.dimension;
	}

	private static boolean exempt(Stop s, Entity e) {
		if (e.getUUID().equals(s.caster)) {
			return true;
		}
		Entity caster = e.level().getEntity(s.casterId);
		if (caster != null && (e == caster.getRootVehicle() || e.getRootVehicle() == caster.getRootVehicle())) {
			return true;
		}
		if (e instanceof Projectile p && p.tickCount < PROJECTILE_FREE_TICKS && p.getOwner() != null && p.getOwner().getUUID().equals(s.caster)) {
			return true;
		}
		return s.radius > 0.0 && e.position().distanceToSqr(s.centre) > s.radius * s.radius;
	}

	// ---------------------------------------------------------------- begin / end

	/** Shift+Z. True if time stopped. */
	public static boolean begin(ServerPlayer caster) {
		StandState s = Stands.state(caster);
		if (active != null || windup != null) {
			Stands.say(caster, "message.projecthero.stand.time_stop_busy", ChatFormatting.GRAY);
			return false;
		}
		if (!Stands.ready(caster, StandMove.THE_WORLD)) {
			Stands.say(caster, "message.projecthero.stand.cooldown", ChatFormatting.GRAY,
					(Stands.cooldownRemaining(caster, StandMove.THE_WORLD) + 19) / 20);
			return false;
		}
		if (s.stamina + 1.0e-3f < StandConfig.TIME_STOP_COST) {
			Stands.say(caster, "message.projecthero.stand.time_stop_stamina", ChatFormatting.GRAY, Math.round(StandConfig.TIME_STOP_COST));
			return false;
		}
		ServerLevel level = (ServerLevel) caster.level();
		long now = level.getGameTime();
		int ticks = testTicks > 0 ? testTicks : StandConfig.TIME_STOP_TICKS;
		StandState n = s.copy();
		n.abilityReadyAt.put(StandMove.THE_WORLD, now + StandConfig.TIME_STOP_COOLDOWN);
		n.animId = StandState.ANIM_THE_WORLD;
		n.animStart = now;
		Stands.save(caster, n);
		// the voice line first; time stops on its burst (tick())
		Stands.sound(level, caster.position().add(0, 1.2, 0), StandSounds.TIME_STOP, 1.2f, 1.0f);
		windup = new Windup(caster.getUUID(), now + StandConfig.TIME_STOP_WINDUP_TICKS, ticks, testRadius);
		return true;
	}

	private static void stopTime(ServerPlayer caster, Windup w) {
		ServerLevel level = (ServerLevel) caster.level();
		long now = level.getGameTime();
		active = new Stop(caster.getUUID(), caster.getId(), level.dimension(), now + w.ticks, w.radius, caster.position());
		broadcast(level.getServer());
		Stands.vfx(level, com.projecthero.mod.stand.network.StandFxPayload.WORLD, caster.position(), new Vec3(0, 1, 0), 1.0f);
		caster.displayClientMessage(stopMessage("message.projecthero.stand.time_stop", w.ticks / 20f), true);
	}

	private static net.minecraft.network.chat.Component stopMessage(String key, float seconds) {
		return net.minecraft.network.chat.Component.translatable(key, String.format(java.util.Locale.ROOT, "%.0f", seconds))
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD);
	}

	/** Server tick: the clock tick sound, and the end. */
	public static void tick(MinecraftServer server) {
		Windup w = windup;
		if (w != null) {
			ServerPlayer caster = server.getPlayerList().getPlayer(w.caster);
			if (caster == null || !caster.isAlive() || caster.isRemoved() || !Stands.isStandUser(caster)) {
				windup = null; // the caster went down (or away) mid-line: no stop
			} else if (caster.level().getGameTime() >= w.at) {
				windup = null;
				stopTime(caster, w);
			}
		}
		Stop s = active;
		if (s == null) {
			return;
		}
		ServerLevel level = server.getLevel(s.dimension);
		ServerPlayer caster = server.getPlayerList().getPlayer(s.caster);
		if (level == null) {
			active = null;
			broadcast(server);
			return;
		}
		boolean casterGone = caster == null || !caster.isAlive() || caster.isRemoved() || caster.level() != level || !Stands.isStandUser(caster);
		long now = level.getGameTime();
		if (casterGone || now >= s.end) {
			resume(server, caster);
			return;
		}
		if (caster != null && (s.end - now) % 20 == 0) {
			// v0.17.2 (user: "make it sound like a clock is ticking during time stop like how it is in the anime"): a ringing
			// clock tick each second, alternating tick (higher) and tock (lower)
			boolean tock = ((s.end - now) / 20) % 2 == 0;
			Stands.sound(level, caster.position(), StandSounds.CLOCK_TICK, 0.9f, tock ? 0.8f : 1.0f);
		}
	}

	/** Death / logout / dimension change / revoke of the caster: time resumes now. */
	public static void onCasterGone(ServerPlayer player) {
		Windup w = windup;
		if (w != null && w.caster.equals(player.getUUID())) {
			windup = null;
		}
		Stop s = active;
		if (s != null && s.caster.equals(player.getUUID()) && player.getServer() != null) {
			resume(player.getServer(), null);
		}
	}

	/** Time flows again: the queued damage and knockback land, the colour comes back, the caster is winded. */
	static void resume(MinecraftServer server, ServerPlayer caster) {
		Stop s = active;
		if (s == null) {
			return;
		}
		active = null;
		broadcast(server);
		ServerLevel level = server.getLevel(s.dimension);
		for (Queued q : s.queue.values()) {
			land(level, caster, q);
		}
		if (caster != null && Stands.isStandUser(caster)) {
			Stands.setStamina(caster, Stands.stamina(caster) - StandConfig.TIME_STOP_COST); // the stop is paid for as time resumes
		}
		if (caster != null && caster.isAlive()) {
			caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, StandConfig.TIME_STOP_WINDED_TICKS, 1, false, false, true));
			if (level != null) {
				Stands.sound(level, caster.position(), StandSounds.TIME_RESUME, 1.0f, 1.0f);
			}
		}
	}

	private static void land(ServerLevel level, ServerPlayer caster, Queued q) {
		LivingEntity v = q.victim;
		if (v == null || !v.isAlive() || v.isRemoved()) {
			return;
		}
		float dmg = q.damage;
		boolean boss = StandCombat.isBoss(v);
		if (v instanceof Player) {
			dmg = Math.min(dmg, v.getMaxHealth() * StandConfig.TIME_STOP_PLAYER_CAP);
		} else if (boss) {
			dmg = Math.min(dmg * StandConfig.TIME_STOP_BOSS_FACTOR, v.getMaxHealth() * StandConfig.TIME_STOP_BOSS_CAP);
		}
		if (dmg > 0f) {
			DamageSource src = caster != null ? v.damageSources().playerAttack(caster) : v.damageSources().magic();
			v.invulnerableTime = 0;
			v.hurt(src, dmg);
		}
		if (!boss && q.knock.lengthSqr() > 1.0e-4) {
			Vec3 k = q.knock;
			if (k.length() > StandConfig.TIME_STOP_MAX_KNOCK) {
				k = k.normalize().scale(StandConfig.TIME_STOP_MAX_KNOCK);
			}
			v.setDeltaMovement(v.getDeltaMovement().add(k));
			v.hurtMarked = true;
		}
		if (level != null) {
			Vec3 c = v.position().add(0, v.getBbHeight() * 0.6, 0);
			level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.2); // the hits landing: everyone sees them
		}
	}

	// ---------------------------------------------------------------- queued hits

	/** Is a hit by {@code attacker} on {@code victim} one to queue (the caster striking something frozen)? */
	public static boolean shouldQueue(Entity attacker, LivingEntity victim) {
		Stop s = active;
		return s != null && attacker != null && attacker.getUUID().equals(s.caster) && frozen(victim);
	}

	/** Adds damage and knockback to a frozen victim's stack. */
	public static void queue(LivingEntity victim, float damage, Vec3 knock) {
		Stop s = active;
		if (s == null) {
			return;
		}
		Queued q = s.queue.computeIfAbsent(victim.getUUID(), k -> new Queued(victim));
		q.damage += Math.max(0f, damage);
		if (knock != null) {
			q.knock = q.knock.add(knock);
		}
	}

	/** Damage queued on this victim so far (tests, the HUD). */
	public static float queued(LivingEntity victim) {
		Stop s = active;
		if (s == null) {
			return 0f;
		}
		Queued q = s.queue.get(victim.getUUID());
		return q == null ? 0f : q.damage;
	}

	/**
	 * {@code ALLOW_DAMAGE}: any other hit the caster lands on a frozen creature (a sword, a thrown block) is queued too, with a
	 * little knockback away from the caster.
	 */
	public static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		Stop s = active;
		if (s == null) {
			return true;
		}
		Entity attacker = source.getEntity();
		if (shouldQueue(attacker, victim)) {
			Vec3 away = victim.position().subtract(attacker.position());
			away = new Vec3(away.x, 0, away.z);
			queue(victim, amount, away.lengthSqr() > 1.0e-4 ? away.normalize().scale(0.35).add(0, 0.1, 0) : Vec3.ZERO);
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- sync

	private static TimeStopPayload payload() {
		Stop s = active;
		if (s == null) {
			return TimeStopPayload.NONE;
		}
		return new TimeStopPayload(s.casterId, s.dimension.location().toString(), s.end, s.radius, s.centre.x, s.centre.y, s.centre.z);
	}

	private static void broadcast(MinecraftServer server) {
		if (server == null) {
			return;
		}
		TimeStopPayload p = payload();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, TimeStopPayload.TYPE)) {
				ServerPlayNetworking.send(player, p);
			}
		}
	}

	/** A player joining (or awakening a Stand) mid-stop learns of it. */
	public static void sendStateTo(ServerPlayer player) {
		if (active != null && ServerPlayNetworking.canSend(player, TimeStopPayload.TYPE)) {
			ServerPlayNetworking.send(player, payload());
		}
	}
}
