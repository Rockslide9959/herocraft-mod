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
 *   <li>v0.17.3 (the user's rework): the stop drains {@link StandConfig#TIME_STOP_STAMINA_PER_SECOND} Stamina a second and runs
 *       while there is any (Shift+Z again ends it sooner); the caster's moves inside it cost nothing, the caster's damage is
 *       {@link StandConfig#TIME_STOP_DAMAGE_BONUS}x, the caster flies at 10 blocks a second, and for 20 s after it the bar refills
 *       at half speed. It freezes every entity in every loaded chunk of the dimension: there is no radius (only the gametests set
 *       one, {@link #testRadius}, so the tests around them keep running).</li>
 *   <li>Frozen players' ability keys, attacks, item use and block breaking are refused server-side; the caster can't use
 *       blocks or break them (fighting and moving only).</li>
 *   <li>Every client gets {@link TimeStopPayload}: a Stand user's client freezes everything but the caster and drains
 *       the colour; a non-Stand user's client freezes EVERYTHING, the caster too, with no cue at all -- they just see
 *       the world jump when it resumes (the user's rule: they can't perceive the Stand or its power).</li>
 * </ul>
 * Only one stop runs at a time. It ends early when the caster dies, logs out, changes dimension or loses the Stand, and
 * {@code ServerStateReset} clears it with the server.
 * <p>
 * v0.17.4 (user: "players with super speed and star platinum and xlr8 can move during time stop"): some players are not
 * frozen by someone else's stop ({@link #classify}, {@link Mobility}):
 * <ul>
 *   <li>{@link Mobility#FREE} -- other Star Platinum users: they move and fight as if time ran for them. Their hits on frozen
 *       creatures are queued like the caster's (without the caster's +30%), their moves cost Stamina as usual, and what they
 *       throw or fire flies {@link #PROJECTILE_FREE_TICKS} ticks and then hangs, like the caster's throws.</li>
 *   <li>{@link Mobility#SPRINT} -- XLR8 and Speed Force speedsters: they move at most at a normal player's sprint speed
 *       ({@link #SPRINT_CAP}). Movement only: ability keys, attacks, item use and blocks are refused ({@link #actionsLocked}).</li>
 *   <li>{@link Mobility#WALK} -- the Super Speed mutation: the same, at walking speed ({@link #WALK_CAP}).</li>
 * </ul>
 * The server lets them tick, the cap is applied to every self-move ({@link #capMove}, {@code StandTimeStopMoveMixin}), and the
 * payload lists them so their own client (camera, keys, animation) and every other perceiving client keep them moving.
 */
public final class TimeStop {
	/** A projectile the caster lets go of travels this many ticks before it stops in the air. */
	public static final int PROJECTILE_FREE_TICKS = 3;

	/** v0.17.4: how a player moves inside someone else's time stop (the ordinal is sent to clients). */
	public enum Mobility { FROZEN, WALK, SPRINT, FREE }

	/** v0.17.4: a player's walking speed, blocks a tick (4.317 b/s). */
	public static final double WALK_CAP = 4.317 / 20.0;
	/** v0.17.4: a player's sprinting speed, blocks a tick (5.612 b/s). */
	public static final double SPRINT_CAP = 5.612 / 20.0;

	/** Client hook (set by {@code TimeStopClient}): the local player's speed cap in blocks a tick, 0 = none. */
	public static volatile java.util.function.ToDoubleFunction<Entity> clientCap = e -> 0.0;

	/** One queued hit-stack on a frozen victim, from one attacker (the caster or a free Star Platinum user). */
	static final class Queued {
		final LivingEntity victim;
		final UUID attacker;
		float damage;
		Vec3 knock = Vec3.ZERO;

		Queued(LivingEntity victim, UUID attacker) {
			this.victim = victim;
			this.attacker = attacker;
		}
	}

	/** The running stop. */
	static final class Stop {
		final UUID caster;
		final int casterId;
		final ResourceKey<Level> dimension;
		long end;
		/** 0 = the whole dimension (gametests use a small radius so the parallel tests around them keep running). */
		final double radius;
		final Vec3 centre;
		/** Keyed by attacker + victim UUID. */
		final Map<String, Queued> queue = new LinkedHashMap<>();
		/** The mobile-player list last sent to clients (entity ids and levels; rebroadcast when it changes). */
		String mobileSig = "";
		/** The caster's flight before the stop (restored when time resumes). */
		boolean hadMayfly, wasFlying;
		float flySpeed = 0.05f;

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

	/** This player's voice line is playing (their bar holds still until the stop). */
	public static boolean windingUpFor(Entity e) {
		Windup w = windup;
		return w != null && e != null && w.caster.equals(e.getUUID());
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
		if (exempt(s, e)) {
			return false;
		}
		return !(e instanceof ServerPlayer p && classify(p) != Mobility.FROZEN);
	}

	/**
	 * v0.17.4: how this player would move inside someone else's stop, whatever is running (server side). Other Star Platinum
	 * users are free; XLR8 (the alien worn right now) and Speed Force speedsters sprint; the Super Speed mutation walks.
	 */
	public static Mobility classify(ServerPlayer p) {
		if (p == null) {
			return Mobility.FROZEN;
		}
		if (Stands.isStandUser(p) && Stands.type(p) == StandType.STAR_PLATINUM) {
			return Mobility.FREE;
		}
		if (com.projecthero.mod.speedforce.SpeedForceSpeedster.hasPower(p)
				|| com.projecthero.mod.omnitrix.alien.Aliens.XLR8.id().equals(com.projecthero.mod.omnitrix.Omnitrix.active(p))) {
			return Mobility.SPRINT;
		}
		if (com.projecthero.mod.hero.ExperimentalPowers.owns(p, com.projecthero.mod.hero.power.p04.SuperSpeedHandlers.KEY)) {
			return Mobility.WALK;
		}
		return Mobility.FROZEN;
	}

	/** v0.17.4: how this entity moves in the running stop (server side): the caster and everything outside it are FREE. */
	public static Mobility mobility(Entity e) {
		Stop s = active;
		if (s == null || e == null || e.level().isClientSide() || e.level().dimension() != s.dimension || exempt(s, e)) {
			return Mobility.FREE;
		}
		return e instanceof ServerPlayer p ? classify(p) : Mobility.FROZEN;
	}

	/** v0.17.4: moving through the stop at a capped speed (Super Speed, XLR8, Speed Force): movement only, no actions. */
	public static boolean limited(Entity e) {
		Mobility m = active == null ? Mobility.FREE : mobility(e);
		return m == Mobility.WALK || m == Mobility.SPRINT;
	}

	/** v0.17.4: refuse ability keys, attacks, item use and block use / breaking (frozen, or a speedster moving through the stop). */
	public static boolean actionsLocked(Entity e) {
		return active != null && (frozen(e) || limited(e));
	}

	/** v0.17.4: a free Star Platinum user inside the running stop (not the caster): fights as if time ran. */
	public static boolean freeInside(Entity e) {
		Stop s = active;
		return s != null && e instanceof ServerPlayer p && !e.level().isClientSide() && e.level().dimension() == s.dimension
				&& !e.getUUID().equals(s.caster) && !(s.radius > 0.0 && e.position().distanceToSqr(s.centre) > s.radius * s.radius)
				&& classify(p) == Mobility.FREE;
	}

	/** v0.17.4: the caster or a free Star Platinum user: they act on the frozen world (their hits on it are queued). */
	public static boolean unbound(Entity e) {
		return isCaster(e) || freeInside(e);
	}

	/** v0.17.4: this entity's speed cap in blocks a tick right now (0 = none). Players only; the client asks its hook. */
	public static double speedCap(Entity e) {
		if (!(e instanceof Player)) {
			return 0.0;
		}
		if (e.level().isClientSide()) {
			return clientCap.applyAsDouble(e);
		}
		if (active == null) {
			return 0.0;
		}
		return capFor(mobility(e));
	}

	public static double capFor(Mobility m) {
		return switch (m) {
			case WALK -> WALK_CAP;
			case SPRINT -> SPRINT_CAP;
			default -> 0.0;
		};
	}

	/**
	 * v0.17.4 ({@code StandTimeStopMoveMixin}, every {@code MoverType.SELF} move): a capped player's horizontal move is cut to
	 * the cap, and their velocity with it (so nothing builds up while time is stopped). Falling and jumping are left alone.
	 */
	public static Vec3 capMove(Entity e, Vec3 move) {
		double cap = speedCap(e);
		if (cap <= 0.0 || move == null) {
			return move;
		}
		double h = Math.sqrt(move.x * move.x + move.z * move.z);
		Vec3 d = e.getDeltaMovement();
		double dh = Math.sqrt(d.x * d.x + d.z * d.z);
		if (dh > cap) {
			e.setDeltaMovement(d.x * cap / dh, d.y, d.z * cap / dh);
		}
		if (h <= cap) {
			return move;
		}
		return new Vec3(move.x * cap / h, move.y, move.z * cap / h);
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
		if (e instanceof Projectile p && p.tickCount < PROJECTILE_FREE_TICKS && p.getOwner() != null
				&& (p.getOwner().getUUID().equals(s.caster) || p.getOwner() instanceof ServerPlayer op && classify(op) == Mobility.FREE)) {
			return true; // (v0.17.4: a free Star Platinum user's throws too)
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
		if (s.stamina + 1.0e-3f < StandConfig.TIME_STOP_MIN_STAMINA) {
			Stands.say(caster, "message.projecthero.stand.time_stop_stamina", ChatFormatting.GRAY, Math.round(StandConfig.TIME_STOP_MIN_STAMINA));
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
		Stands.sound(level, caster.position().add(0, 1.2, 0), StandSounds.TIME_STOP, 0.8f, 1.0f);
		windup = new Windup(caster.getUUID(), now + StandConfig.TIME_STOP_WINDUP_TICKS, ticks, testRadius);
		return true;
	}

	private static void stopTime(ServerPlayer caster, Windup w) {
		ServerLevel level = (ServerLevel) caster.level();
		long now = level.getGameTime();
		// it lasts while the Stamina does (10 a second): the end sent to clients is that estimate (a resume always follows)
		int ticks = Math.min(w.ticks, Math.max(1, (int) Math.ceil(Stands.stamina(caster) / (StandConfig.TIME_STOP_STAMINA_PER_SECOND / 20f))));
		Stop stop = new Stop(caster.getUUID(), caster.getId(), level.dimension(), now + ticks, w.radius, caster.position());
		var ab = caster.getAbilities();
		stop.hadMayfly = ab.mayfly;
		stop.wasFlying = ab.flying;
		stop.flySpeed = ab.getFlyingSpeed();
		active = stop;
		grantFlight(caster);
		broadcast(level.getServer());
		Stands.vfx(level, com.projecthero.mod.stand.network.StandFxPayload.WORLD, caster.position(), new Vec3(0, 1, 0), 1.0f);
		caster.displayClientMessage(stopMessage("message.projecthero.stand.time_stop", ticks / 20f), true);
	}

	/** v0.17.3 (user: "player can fly at 10 blocks per second during time stop"). */
	private static void grantFlight(ServerPlayer caster) {
		var ab = caster.getAbilities();
		if (caster.isSpectator()) {
			return;
		}
		if (!ab.mayfly || Math.abs(ab.getFlyingSpeed() - StandConfig.TIME_STOP_FLY_SPEED) > 1.0e-4f) {
			ab.mayfly = true;
			ab.setFlyingSpeed(StandConfig.TIME_STOP_FLY_SPEED);
			caster.onUpdateAbilities();
		}
	}

	private static void restoreFlight(ServerPlayer caster, Stop s) {
		if (caster == null || caster.isSpectator()) {
			return;
		}
		var ab = caster.getAbilities();
		boolean creative = caster.isCreative();
		ab.mayfly = s.hadMayfly || creative;
		ab.flying = ab.mayfly && (s.wasFlying || creative && ab.flying);
		ab.setFlyingSpeed(s.flySpeed > 0f ? s.flySpeed : 0.05f);
		caster.onUpdateAbilities();
		caster.fallDistance = 0f;
	}

	/** Shift+Z inside your own stop: time resumes now. */
	public static void endEarly(ServerPlayer caster) {
		Stop s = active;
		if (s != null && s.caster.equals(caster.getUUID()) && caster.getServer() != null) {
			resume(caster.getServer(), caster);
		}
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
		// v0.17.3 (user: "time stop drains 10 stamina per second"): it runs on the bar, and ends when the bar is empty
		float st = Stands.stamina(caster) - StandConfig.TIME_STOP_STAMINA_PER_SECOND / 20f;
		Stands.setStamina(caster, st);
		if (st <= 0f) {
			resume(server, caster);
			return;
		}
		grantFlight(caster); // (another power's tick may have taken it)
		if (now % 5 == 0 && !mobileSig(server, s).equals(s.mobileSig)) {
			broadcast(server); // v0.17.4: someone became able (or unable) to move through the stop
		}
		if (now % 20 == 0) {
			// v0.17.2 (user: "make it sound like a clock is ticking during time stop like how it is in the anime"): a ringing
			// clock tick each second, alternating tick (higher) and tock (lower)
			boolean tock = (now / 20) % 2 == 0;
			Stands.sound(level, caster.position(), StandSounds.CLOCK_TICK, 0.5f, tock ? 0.8f : 1.0f);
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
			restoreFlight(player, s);
			resume(player.getServer(), null);
		}
	}

	/**
	 * Time flows again: the queued damage and knockback land, the colour comes back, the caster's flight goes and (v0.17.3, user:
	 * "after time stop ends stamina regenerates 50% slower for 20 seconds") the bar refills at half speed for 20 s.
	 */
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
		if (caster != null) {
			restoreFlight(caster, s);
		}
		if (caster != null && Stands.isStandUser(caster)) {
			StandState n = Stands.state(caster).copy();
			n.regenSlowUntil = caster.level().getGameTime() + StandConfig.REGEN_SLOW_TICKS;
			Stands.save(caster, n);
		}
		if (caster != null && caster.isAlive() && level != null) {
			Stands.sound(level, caster.position(), StandSounds.TIME_RESUME, 0.7f, 1.0f);
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
			ServerPlayer attacker = level == null || q.attacker == null ? caster : level.getServer().getPlayerList().getPlayer(q.attacker);
		DamageSource src = attacker != null ? v.damageSources().playerAttack(attacker) : v.damageSources().magic();
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

	/**
	 * Is a hit by {@code attacker} on {@code victim} one to queue (the caster -- or, v0.17.4, a free Star Platinum user --
	 * striking something frozen)?
	 */
	public static boolean shouldQueue(Entity attacker, LivingEntity victim) {
		Stop s = active;
		return s != null && attacker != null && unbound(attacker) && frozen(victim);
	}

	/** Adds damage and knockback to a frozen victim's stack (the caster's hit). */
	public static void queue(LivingEntity victim, float damage, Vec3 knock) {
		Stop s = active;
		if (s != null) {
			queue(s.caster, victim, damage, knock);
		}
	}

	/** v0.17.4: adds {@code attacker}'s hit to a frozen victim's stack (the caster's with the +30%, a free user's as it is). */
	public static void queue(Entity attacker, LivingEntity victim, float damage, Vec3 knock) {
		Stop s = active;
		if (s != null) {
			queue(attacker == null ? s.caster : attacker.getUUID(), victim, damage, knock);
		}
	}

	private static void queue(UUID attacker, LivingEntity victim, float damage, Vec3 knock) {
		Stop s = active;
		if (s == null) {
			return;
		}
		Queued q = s.queue.computeIfAbsent(attacker + "/" + victim.getUUID(), k -> new Queued(victim, attacker));
		// v0.17.3 (user: "player deals 30% increased damage during time stop"): the caster's hits only
		float bonus = attacker.equals(s.caster) ? StandConfig.TIME_STOP_DAMAGE_BONUS : 1f;
		q.damage += Math.max(0f, damage) * bonus;
		if (knock != null) {
			q.knock = q.knock.add(knock);
		}
	}

	/** Damage queued on this victim so far, from everyone (tests, the HUD). */
	public static float queued(LivingEntity victim) {
		Stop s = active;
		if (s == null) {
			return 0f;
		}
		float sum = 0f;
		for (Queued q : s.queue.values()) {
			if (q.victim == victim || q.victim.getUUID().equals(victim.getUUID())) {
				sum += q.damage;
			}
		}
		return sum;
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
			queue(attacker, victim, amount, away.lengthSqr() > 1.0e-4 ? away.normalize().scale(0.35).add(0, 0.1, 0) : Vec3.ZERO);
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- sync

	private static TimeStopPayload payload(MinecraftServer server) {
		Stop s = active;
		if (s == null) {
			return TimeStopPayload.NONE;
		}
		return new TimeStopPayload(s.casterId, s.dimension.location().toString(), s.end, s.radius, s.centre.x, s.centre.y, s.centre.z,
				mobileIds(s, server));
	}

	/** v0.17.4: entity id * 4 + {@link Mobility} ordinal of every player in the stop who isn't frozen by it (not the caster). */
	private static java.util.List<Integer> mobileIds(Stop s, MinecraftServer server) {
		java.util.List<Integer> out = new java.util.ArrayList<>();
		ServerLevel level = server == null ? null : server.getLevel(s.dimension);
		if (level == null) {
			return out;
		}
		for (ServerPlayer p : level.players()) {
			if (p.getUUID().equals(s.caster) || s.radius > 0.0 && p.position().distanceToSqr(s.centre) > s.radius * s.radius) {
				continue;
			}
			Mobility m = classify(p);
			if (m != Mobility.FROZEN) {
				out.add(p.getId() * 4 + m.ordinal());
			}
		}
		return out;
	}

	private static String mobileSig(MinecraftServer server, Stop s) {
		return mobileIds(s, server).toString();
	}

	private static void broadcast(MinecraftServer server) {
		if (server == null) {
			return;
		}
		Stop s = active;
		if (s != null) {
			s.mobileSig = mobileSig(server, s);
		}
		TimeStopPayload p = payload(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, TimeStopPayload.TYPE)) {
				ServerPlayNetworking.send(player, p);
			}
		}
	}

	/** A player joining (or awakening a Stand) mid-stop learns of it. */
	public static void sendStateTo(ServerPlayer player) {
		if (active != null && ServerPlayNetworking.canSend(player, TimeStopPayload.TYPE)) {
			ServerPlayNetworking.send(player, payload(player.getServer()));
		}
	}
}
