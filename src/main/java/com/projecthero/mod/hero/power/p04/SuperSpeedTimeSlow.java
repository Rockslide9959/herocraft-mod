package com.projecthero.mod.hero.power.p04;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.revamp.batcha.BatchA;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Super Speed Z -- Time Slow (v0.14.5). For 30 s everything within {@link #RADIUS} blocks of the caster runs at 5%:
 *
 * <ul>
 *   <li>every non-player entity (mobs, animals, projectiles, items, falling blocks, TNT ...) only gets one server
 *       tick in {@link #TICK_DIVISOR} ({@code SuperSpeedTimeSlowMixin} on {@code ServerLevel#tickNonPassenger},
 *       mirrored client-side so client-simulated projectiles don't run ahead);</li>
 *   <li>other players get transient ~5% movement / attack-speed / mining-speed / gravity / jump modifiers
 *       (their movement is client-simulated, so skipping their server tick would do nothing).</li>
 * </ul>
 *
 * <p>Press Z again to end it early. The 150 s cooldown starts when it ends. All state is static, keyed by caster,
 * and dropped on server stop ({@link #clearSessionState}).
 */
public final class SuperSpeedTimeSlow {
	public static final int DURATION_TICKS = 30 * 20;
	public static final int COOLDOWN_TICKS = 150 * 20;
	public static final double RADIUS = 96.0;
	private static final double RADIUS_SQ = RADIUS * RADIUS;
	/** Slowed entities tick once every this many ticks (1/20 = 5%). */
	public static final int TICK_DIVISOR = 20;
	/** HUD countdown resource. */
	public static final String LEFT = "time_slow_ticks";

	private static final ResourceLocation MOVE = com.projecthero.mod.ProjectHeroMod.id("time_slow_move");
	private static final ResourceLocation ATTACK = com.projecthero.mod.ProjectHeroMod.id("time_slow_attack");
	private static final ResourceLocation MINE = com.projecthero.mod.ProjectHeroMod.id("time_slow_mine");
	private static final ResourceLocation GRAVITY = com.projecthero.mod.ProjectHeroMod.id("time_slow_gravity");
	private static final ResourceLocation JUMP = com.projecthero.mod.ProjectHeroMod.id("time_slow_jump");

	private record Caster(ServerPlayer player, long until) {
	}

	private static final Map<UUID, Caster> ACTIVE = new HashMap<>();
	/** Players currently carrying the slow modifiers (so they can be cleared even after the caster is gone). */
	private static final Set<UUID> SLOWED = new HashSet<>();

	private SuperSpeedTimeSlow() {
	}

	public static boolean isCasting(ServerPlayer p) {
		return ACTIVE.containsKey(p.getUUID());
	}

	public static boolean anyActive() {
		return !ACTIVE.isEmpty();
	}

	public static void start(ServerPlayer p) {
		long now = p.level().getGameTime();
		ACTIVE.put(p.getUUID(), new Caster(p, now + DURATION_TICKS));
		BatchA.set(p, SuperSpeedHandlers.KEY, LEFT, DURATION_TICKS, 1e9f);
		BatchA.play(p, SuperSpeedHandlers.KEY, "power_up", 16);
		AbilityHelpers.sound(p, SoundEvents.BEACON_ACTIVATE, 1.2f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.5f);
		if (p.getServer() != null) {
			reconcilePlayers(p.getServer());
		}
	}

	/** Ends {@code p}'s Time Slow (no-op if none is running); {@code cooldown} starts the 150 s cooldown. */
	public static void end(ServerPlayer p, boolean cooldown) {
		if (ACTIVE.remove(p.getUUID()) == null) {
			return;
		}
		BatchA.set(p, SuperSpeedHandlers.KEY, LEFT, 0, 1e9f);
		if (cooldown) {
			Power power = Powers.byKey(SuperSpeedHandlers.KEY);
			if (power != null && power.ability(AbilitySlot.SLOT_4) != null) {
				ExperimentalPowers.triggerCooldown(p, power, power.ability(AbilitySlot.SLOT_4),
						HeroConfig.get().scaledCooldown(COOLDOWN_TICKS));
			}
		}
		AbilityHelpers.sound(p, SoundEvents.BEACON_DEACTIVATE, 1.2f, 0.6f);
		if (p.getServer() != null) {
			reconcilePlayers(p.getServer());
		}
	}

	/**
	 * Whether the server should skip this tick of {@code e}. Called for every non-passenger entity every tick, so
	 * the common case (no Time Slow anywhere) returns on the first check.
	 */
	public static boolean skipTick(Entity e) {
		if (ACTIVE.isEmpty() || e instanceof Player) {
			return false;
		}
		long t = e.level().getGameTime();
		if ((t + e.getId()) % TICK_DIVISOR == 0) {
			return false; // the one tick in twenty it still gets
		}
		if (e.hasPassenger(x -> x instanceof Player)) {
			return false; // skipping a vehicle would skip its rider's tick with it
		}
		for (Caster c : ACTIVE.values()) {
			ServerPlayer p = c.player();
			if (p.level() == e.level() && !p.isRemoved() && c.until() > t && p.distanceToSqr(e) <= RADIUS_SQ) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code target} (not a caster) stands inside someone's Time Slow. */
	private static boolean inField(ServerPlayer target) {
		if (ACTIVE.containsKey(target.getUUID())) {
			return false; // a speedster in their own (or anyone's) slowed time moves freely
		}
		for (Caster c : ACTIVE.values()) {
			ServerPlayer p = c.player();
			if (p.level() == target.level() && !p.isRemoved() && p.distanceToSqr(target) <= RADIUS_SQ) {
				return true;
			}
		}
		return false;
	}

	/** Every server tick: expiry, HUD countdown, and (every 5 ticks) the other players' slow modifiers. */
	public static void serverTick(MinecraftServer server) {
		if (ACTIVE.isEmpty() && SLOWED.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		List<Caster> ended = new ArrayList<>();
		for (Caster c : ACTIVE.values()) {
			ServerPlayer p = c.player();
			if (c.until() <= now || p.isRemoved() || !p.isAlive() || !SuperSpeedHandlers.owns(p)) {
				ended.add(c);
			} else if (now % 5 == 0) {
				BatchA.set(p, SuperSpeedHandlers.KEY, LEFT, c.until() - now, 1e9f);
			}
		}
		for (Caster c : ended) {
			ServerPlayer p = c.player();
			// a caster who logged out keeps the cooldown on their state too (setResource works on the detached entity)
			end(p, true);
		}
		if (now % 5 == 0 || !ended.isEmpty()) {
			reconcilePlayers(server);
		}
	}

	/** Puts the 5% modifiers on exactly the non-caster players inside a field, and takes them off everyone else. */
	public static void reconcilePlayers(MinecraftServer server) {
		Set<UUID> seen = new HashSet<>();
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			seen.add(sp.getUUID());
			boolean slow = !ACTIVE.isEmpty() && inField(sp);
			if (slow) {
				apply(sp);
				SLOWED.add(sp.getUUID());
			} else if (SLOWED.remove(sp.getUUID())) {
				clear(sp);
			}
		}
		SLOWED.retainAll(seen); // logged-out players: transient modifiers never saved, nothing to clear
	}

	private static void apply(ServerPlayer p) {
		mod(p, Attributes.MOVEMENT_SPEED, MOVE, -0.95);
		mod(p, Attributes.ATTACK_SPEED, ATTACK, -0.95);
		mod(p, Attributes.BLOCK_BREAK_SPEED, MINE, -0.95);
		mod(p, Attributes.GRAVITY, GRAVITY, -0.95);
		mod(p, Attributes.JUMP_STRENGTH, JUMP, -0.8);
	}

	public static void clear(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, MOVE);
		PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, ATTACK);
		PowerToggles.clearModifier(p, Attributes.BLOCK_BREAK_SPEED, MINE);
		PowerToggles.clearModifier(p, Attributes.GRAVITY, GRAVITY);
		PowerToggles.clearModifier(p, Attributes.JUMP_STRENGTH, JUMP);
	}

	private static void mod(ServerPlayer p, Holder<Attribute> attr, ResourceLocation id, double amount) {
		PowerToggles.modifier(p, attr, id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** Whether {@code p} carries the Time Slow movement modifier (both sides: the attribute syncs to its owner). */
	public static boolean slowedByAttribute(Player p) {
		var inst = p.getAttribute(Attributes.MOVEMENT_SPEED);
		return inst != null && inst.getModifier(MOVE) != null;
	}

	/** Server stop: drop everything (the entities these hold belong to a dead world). */
	public static void clearSessionState() {
		ACTIVE.clear();
		SLOWED.clear();
	}
}
