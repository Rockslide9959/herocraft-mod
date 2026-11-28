package com.projecthero.mod.hero.power.p04;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.network.TimeSlowStatePayload;
import com.projecthero.mod.time.LevelTimeScale;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Super Speed Z -- Time Slow (and, v0.17.1, the Speed Force Speedster's Flash Time). For 45 s everything in the caster's
 * <b>dimension</b> except the caster runs at 5% -- every entity and player, block entities, scheduled and random block
 * ticks, fluids, redstone, the day / night cycle, weather, particles, clouds.
 *
 * <p>v0.17.7 (user: "change all the time stops and time slows so that they still work worldwide but not across
 * dimensions"): it used to drop the whole server's tick rate (vanilla {@code /tick rate}), which slowed every dimension.
 * Now it scales only the dimension it was cast in ({@link LevelTimeScale}): the server keeps running at 20 ticks a second,
 * that dimension runs one tick in {@link #TICK_DIVISOR} and holds on the rest, and only the players standing in it get the
 * slowed tick rate on their clients. Other dimensions -- and the players in them -- run at full speed, and a second
 * speedster can slow their own dimension at the same time (one Time Slow per dimension).
 * <ul>
 *   <li>The caster (and their mount / whoever they carry) is never held, so they simply keep living at 20 ticks a second:
 *       movement, physics, food, attack cooldown, mining, i-frames and every per-player system of the mod. The game clock
 *       (cooldowns, move animations, Super Speed timers) is shared by every dimension and no longer slows, so nothing has to
 *       be pulled in for them any more.</li>
 *   <li>On each held tick the caster's level skips, items near the caster still count their pickup delay on the caster's
 *       clock ({@link #tickLootPickupDelays}) and the Black Flash hunting them keeps their pace.</li>
 *   <li>Client: everyone in the dimension gets the slowed tick rate; the caster's own client keeps its 20 tick/s timer
 *       (the rest of its world is stepped once every 20 of its ticks -- see {@code SuperSpeedClientV0145} /
 *       {@code TimeSlowClient}).</li>
 *   <li>Damage: slowed victims take hits as usual -- damage, death, loot and XP land at once -- and a victim the caster hit
 *       at least {@link #CASTER_HIT_COOLDOWN} caster ticks ago has its (slowed) hit-invulnerability window cleared, so the
 *       caster can keep hitting at the normal cadence ({@link #onIncomingDamage}).</li>
 * </ul>
 *
 * <p>The slow stays with the dimension it was cast in: if the caster walks through a portal it runs on there until its
 * timer ends (they are a normal-speed player in the new dimension; coming back puts them in it again). It ends when Z is
 * pressed again, after 45 s, or when the caster dies, logs out or loses the power; the dimension is always put back (also
 * on server stop). Press Z again to end it early; the 300 s cooldown starts when it ends. v0.14.8: Z has to be HELD for 5 s
 * to charge it ({@code SuperSpeedHandlers#timeSlowChargeTick}); while it runs the caster gets no movement boosts from the
 * passive, Speed Mode or Overdrive (the extra ticks are the advantage); and when it ends they are exhausted for 30 s
 * ({@code SuperSpeedHandlers#startExhaustion}).
 */
public final class SuperSpeedTimeSlow {
	public static final int DURATION_TICKS = 45 * 20; // v0.14.7: 45 s (was 30)
	public static final int COOLDOWN_TICKS = 300 * 20; // v0.14.8: 300 s (was 150)
	/** The slowed dimension's tick rate on its players' clients: 1 tick a second, 5% of 20. */
	public static final float SLOW_RATE = 1.0f;
	/** The slowed dimension runs one server tick in this many (20 ticks a second / {@link #SLOW_RATE}). */
	public static final int TICK_DIVISOR = 20;
	/** HUD countdown resource (the caster's own ticks left). */
	public static final String LEFT = "time_slow_ticks";
	/** A victim the caster hit this many caster ticks ago can take a full hit from them again (vanilla: 10). */
	public static final int CASTER_HIT_COOLDOWN = 10;
	/** Gametests only: hold just the entities this close to the caster (0 = the whole dimension). */
	public static double testRadius = 0.0;

	/** One running slow (keyed by its dimension). Ids and keys only -- no entities or worlds. */
	static final class Slow {
		final UUID caster;
		final ResourceKey<Level> dimension;
		int left;
		/** Ticks the caster has lived through since the slow began. */
		long casterTicks;
		/** Victim entity id -> {@link #casterTicks} when the caster last hit it. */
		final Map<Integer, Long> lastCasterHit = new HashMap<>();

		Slow(UUID caster, ResourceKey<Level> dimension, int left) {
			this.caster = caster;
			this.dimension = dimension;
			this.left = left;
		}
	}

	private static final Map<ResourceKey<Level>, Slow> SLOWS = new LinkedHashMap<>();

	private SuperSpeedTimeSlow() {
	}

	private static Slow slowOf(Entity p) {
		if (p == null || SLOWS.isEmpty()) {
			return null;
		}
		UUID id = p.getUUID();
		for (Slow s : SLOWS.values()) {
			if (s.caster.equals(id)) {
				return s;
			}
		}
		return null;
	}

	public static boolean isCasting(ServerPlayer p) {
		return slowOf(p) != null;
	}

	/** Any Time Slow running in any dimension (tests). */
	public static boolean anyActive() {
		return !SLOWS.isEmpty();
	}

	/** v0.17.7: a Time Slow is running in this level's dimension. */
	public static boolean activeIn(Level level) {
		return level != null && SLOWS.containsKey(level.dimension());
	}

	/** v0.17.7: someone else's Time Slow already holds the dimension {@code p} stands in (a second one is refused). */
	public static boolean takenFor(ServerPlayer p) {
		Slow s = SLOWS.get(p.level().dimension());
		return s != null && !s.caster.equals(p.getUUID());
	}

	/** Whether {@code e} is a running Time Slow's caster (server side). */
	public static boolean isCaster(Entity e) {
		return slowOf(e) != null;
	}

	/** v0.17.1: the caster's ticks left (v0.17.7: per caster, one slow per dimension); 0 = not casting. */
	public static int ticksLeft(ServerPlayer p) {
		Slow s = slowOf(p);
		return s == null ? 0 : Math.max(0, s.left);
	}

	/** v0.17.7: the dimension {@code p}'s slow holds (null = not casting). */
	public static ResourceKey<Level> dimensionOf(ServerPlayer p) {
		Slow s = slowOf(p);
		return s == null ? null : s.dimension;
	}

	/** Whether {@code p} may hold the slow: the Super Speed mutation, or (v0.17.1) the Speed Force Speedster's Flash Time. */
	private static boolean mayCast(ServerPlayer p) {
		return SuperSpeedHandlers.owns(p) || com.projecthero.mod.speedforce.SpeedForceSpeedster.hasPower(p);
	}

	public static void start(ServerPlayer p) {
		start(p, DURATION_TICKS);
	}

	/** v0.17.1: a slow of {@code durationTicks} on the caster's clock (Flash Time scales it with the momentum). */
	public static void start(ServerPlayer p, int durationTicks) {
		MinecraftServer server = p.getServer();
		if (server == null || isCasting(p)) {
			return;
		}
		ResourceKey<Level> dim = p.level().dimension();
		if (SLOWS.containsKey(dim)) {
			p.displayClientMessage(Component.translatable("message.projecthero.speed.time_taken"), true);
			return;
		}
		Slow s = new Slow(p.getUUID(), dim, durationTicks);
		SLOWS.put(dim, s);
		LevelTimeScale.set(server, dim, TICK_DIVISOR, p.getUUID(), testRadius, testRadius > 0.0 ? p.position() : Vec3.ZERO);
		if (SuperSpeedHandlers.owns(p)) {
			BatchA.set(p, SuperSpeedHandlers.KEY, LEFT, durationTicks, 1e9f);
			BatchA.play(p, SuperSpeedHandlers.KEY, "power_up", 16);
		}
		AbilityHelpers.sound(p, SoundEvents.BEACON_ACTIVATE, 1.2f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.5f);
		broadcast(server, dim);
	}

	/** Ends {@code p}'s Time Slow (no-op if they are not a caster); {@code cooldown} starts the cooldown. */
	public static void end(ServerPlayer p, boolean cooldown) {
		Slow s = slowOf(p);
		if (s != null) {
			finish(p.getServer(), s, p, cooldown);
		}
	}

	private static void finish(MinecraftServer server, Slow s, ServerPlayer p, boolean cooldown) {
		if (SLOWS.get(s.dimension) != s) {
			return;
		}
		SLOWS.remove(s.dimension);
		s.lastCasterHit.clear();
		if (server != null) {
			LevelTimeScale.clear(server, s.dimension);
			broadcast(server, s.dimension);
		}
		if (p == null) {
			return; // the caster is gone (logged out before this was noticed): nothing of theirs to reset
		}
		// v0.17.1: the Speed Force Speedster's Flash Time keeps its own cooldown (and has no exhaustion -- the mutation's
		// startExhaustion below does nothing for someone without it)
		com.projecthero.mod.speedforce.SpeedForceAbilities.onFlashTimeEnded(p);
		if (SuperSpeedHandlers.owns(p)) {
			BatchA.set(p, SuperSpeedHandlers.KEY, LEFT, 0, 1e9f);
		}
		if (cooldown && SuperSpeedHandlers.owns(p)) {
			Power power = Powers.byKey(SuperSpeedHandlers.KEY);
			if (power != null && power.ability(AbilitySlot.SLOT_4) != null) {
				ExperimentalPowers.triggerCooldown(p, power, power.ability(AbilitySlot.SLOT_4),
						HeroConfig.get().scaledCooldown(COOLDOWN_TICKS));
			}
		}
		AbilityHelpers.sound(p, SoundEvents.BEACON_DEACTIVATE, 1.2f, 0.6f);
		// v0.14.8: 30 s of exhaustion follow (not for a caster who died or logged out -- death clears it anyway)
		if (p.isAlive() && !p.isRemoved()) {
			SuperSpeedHandlers.startExhaustion(p);
		}
	}

	// ---- client sync ---------------------------------------------------------------------------------

	/** What a player standing in {@code dim} should know: the slow running there, or none (public for the gametests). */
	public static TimeSlowStatePayload payloadFor(ResourceKey<Level> dim) {
		Slow s = SLOWS.get(dim);
		return s == null ? TimeSlowStatePayload.off(dim.location().toString())
				: new TimeSlowStatePayload(true, s.caster, dim.location().toString());
	}

	/** Every player standing in {@code dim} learns the slow there started or ended. */
	private static void broadcast(MinecraftServer server, ResourceKey<Level> dim) {
		ServerLevel level = server.getLevel(dim);
		if (level == null) {
			return;
		}
		TimeSlowStatePayload payload = payloadFor(dim);
		for (ServerPlayer sp : level.players()) {
			if (ServerPlayNetworking.canSend(sp, TimeSlowStatePayload.TYPE)) {
				ServerPlayNetworking.send(sp, payload);
			}
		}
	}

	/** v0.17.7: join / dimension change / respawn -- the player learns the slow (or none) of the dimension they are in now. */
	public static void sendStateTo(ServerPlayer p) {
		if (p.connection != null && ServerPlayNetworking.canSend(p, TimeSlowStatePayload.TYPE)) {
			ServerPlayNetworking.send(p, payloadFor(p.level().dimension()));
		}
	}

	// ---- ticking -----------------------------------------------------------------------------------

	/** One tick of the caster's own clock. */
	private static void casterTick(MinecraftServer server, Slow s, ServerPlayer caster) {
		s.casterTicks++;
		s.left--;
		if (s.left % 5 == 0 && SuperSpeedHandlers.owns(caster)) {
			BatchA.set(caster, SuperSpeedHandlers.KEY, LEFT, Math.max(0, s.left), 1e9f);
		}
		if (s.left <= 0) {
			finish(server, s, caster, true);
		}
	}

	/** End of every server tick: each running slow's caster lives one tick, and gets their held-tick extras. */
	public static void serverTick(MinecraftServer server) {
		if (SLOWS.isEmpty()) {
			return;
		}
		for (Slow s : SLOWS.values().toArray(new Slow[0])) {
			ServerPlayer caster = server.getPlayerList().getPlayer(s.caster);
			if (caster == null || caster.isRemoved() || !caster.isAlive() || !mayCast(caster)) {
				finish(server, s, caster, true);
				continue;
			}
			if (caster.level() instanceof ServerLevel sl && sl.dimension() == s.dimension && LevelTimeScale.phaseHeld(sl)) {
				heldTick(caster, sl);
			}
			casterTick(server, s, caster);
		}
	}

	/** On a tick the caster's dimension holds: what lives at the caster's pace around them. */
	private static void heldTick(ServerPlayer p, ServerLevel sl) {
		tickLootPickupDelays(p, sl);
		// v0.17.1 (user: "if you activate flash time you both move at the same speed still, he is a speedster"): the Black
		// Flash hunting the caster lives through the slowed world at the caster's pace
		com.projecthero.mod.speedforce.BlackFlash.extraTick(p, sl);
	}

	/**
	 * Gametests: one full caster tick while the world around them holds (mock players are not ticked by the server) -- their
	 * connection and entity tick, the held-tick extras, and one tick off the slow. Outside a slow it does nothing.
	 */
	public static void extraCasterTick(ServerPlayer p) {
		Slow s = slowOf(p);
		if (s == null || p.isRemoved() || !p.isAlive() || !(p.level() instanceof ServerLevel sl)) {
			return;
		}
		p.connection.tick();
		if (p.isRemoved() || slowOf(p) != s) {
			return;
		}
		LevelTimeScale.forceTick(sl, p);
		if (sl.dimension() == s.dimension) {
			heldTick(p, sl);
		}
		casterTick(sl.getServer(), s, p);
	}

	/** How far around the caster dropped items have their pickup delay counted on the caster's clock. */
	public static final double LOOT_RADIUS = 24.0;
	/** {@code ItemEntity.INFINITE_PICKUP_DELAY}: never-collectable items (the /give decoration) are left alone. */
	private static final int NEVER_PICK_UP = 32767;

	/**
	 * v0.14.8: an item's pickup delay (vanilla: 10 ticks for loot, 40 for a thrown item) only counts down on the
	 * item's own ticks -- one a second while slowed -- so loot from a kill sat untouchable for ~10 s. Each held tick now
	 * counts one tick off every nearby item's delay as well, so for the caster items become collectable after the same real
	 * time as normal (their own thrown items included: ~2 s, as always). XP orbs have no delay of their own -- the caster's
	 * {@code takeXpDelay} already runs on the caster's ticks. Outside Time Slow nothing calls this.
	 */
	public static void tickLootPickupDelays(ServerPlayer p, ServerLevel level) {
		for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(
				net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(LOOT_RADIUS))) {
			if (LevelTimeScale.exempt(item)) {
				continue; // (outside a test-radius slow: this item runs normally anyway)
			}
			int delay = ((com.projecthero.mod.mixin.ItemEntityPickupAccessor) item).projecthero$getPickupDelay();
			if (delay > 0 && delay != NEVER_PICK_UP) {
				item.setPickUpDelay(delay - 1);
			}
		}
	}

	// ---- damage --------------------------------------------------------------------------------------

	/**
	 * {@code ALLOW_DAMAGE} hook: a slowed victim's hit-invulnerability only counts down on its own (slowed) ticks,
	 * so on a hit from the caster the window is measured in the caster's ticks instead -- {@link #CASTER_HIT_COOLDOWN}
	 * caster ticks after their last hit on it, it can take a full hit from them again.
	 */
	public static void onIncomingDamage(LivingEntity victim, DamageSource source) {
		if (SLOWS.isEmpty()) {
			return;
		}
		Slow s = slowOf(source.getEntity());
		if (s == null || victim.getUUID().equals(s.caster) || victim.level().dimension() != s.dimension) {
			return;
		}
		Long last = s.lastCasterHit.get(victim.getId());
		if (victim.invulnerableTime > 0 && (last == null || s.casterTicks - last >= CASTER_HIT_COOLDOWN)) {
			victim.invulnerableTime = 0;
		}
		s.lastCasterHit.put(victim.getId(), s.casterTicks);
	}

	// ---- joins, logouts, safety ----------------------------------------------------------------------

	public static void onJoin(ServerPlayer p) {
		sendStateTo(p);
	}

	public static void onDisconnect(ServerPlayer p) {
		Slow s = slowOf(p);
		if (s != null) {
			finish(p.getServer(), s, p, true);
		}
	}

	/** Server stopping: put every dimension back and forget everything. */
	public static void onServerStopping(MinecraftServer server) {
		for (ResourceKey<Level> dim : new java.util.ArrayList<>(SLOWS.keySet())) {
			LevelTimeScale.clear(server, dim);
		}
		SLOWS.clear();
	}

	/** Server stopped: drop everything. */
	public static void clearSessionState() {
		SLOWS.clear();
		testRadius = 0.0;
	}
}
