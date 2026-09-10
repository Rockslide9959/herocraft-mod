package com.projecthero.mod.hero;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.data.ResearchStage;

import net.minecraft.server.level.ServerPlayer;

/**
 * The single server-side API for everything about a player's experimental mutations: ownership,
 * which power is active, cooldowns, toggle/cycle/resource state, research progression. Nothing else
 * pokes {@link ExperimentalState} directly.
 *
 * <p>All state lives in one isolated attachment ({@link ModAttachments#EXPERIMENTAL_STATE}); every
 * mutator here re-saves it via {@link ServerPlayer#setAttached} so the change is persisted and synced.
 */
public final class ExperimentalPowers {
	private ExperimentalPowers() {
	}

	public static ExperimentalState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.EXPERIMENTAL_STATE);
	}

	private static void save(ServerPlayer player, ExperimentalState state) {
		player.setAttached(ModAttachments.EXPERIMENTAL_STATE, state);
	}

	// ---------------- ownership ----------------

	public static boolean owns(ServerPlayer player, Power power) {
		return state(player).ownedPowers.contains(power.key());
	}

	public static boolean owns(ServerPlayer player, String powerKey) {
		return state(player).ownedPowers.contains(powerKey);
	}

	public static int ownedCount(ServerPlayer player) {
		return state(player).ownedPowers.size();
	}

	public static int capacity() {
		return Math.max(1, HeroConfig.get().mutationCapacity);
	}

	public static boolean atCapacity(ServerPlayer player) {
		return ownedCount(player) >= capacity();
	}

	public static List<Power> ownedPowers(ServerPlayer player) {
		List<Power> out = new ArrayList<>();
		for (Power p : Powers.all()) {
			if (state(player).ownedPowers.contains(p.key())) {
				out.add(p);
			}
		}
		return out;
	}

	/**
	 * Permanently unlock a power. No-op (returns false) if already owned or at capacity. On success
	 * also records the research as fully confirmed and, if the player has no active power, makes this
	 * one active. Re-granting an already-owned power can never stack anything -- that is the whole
	 * point of the {@code contains} guard (spec section 20).
	 *
	 * <p>Every newly-owned Experimental Tier power's passives switch on immediately, whether or not it
	 * becomes the selected power -- owned means live (spec: persistent passive stacking).
	 */
	public static boolean grant(ServerPlayer player, Power power) {
		ExperimentalState s = state(player).copy();
		if (s.ownedPowers.contains(power.key())) {
			return false;
		}
		if (s.ownedPowers.size() >= capacity()) {
			return false;
		}
		s.ownedPowers.add(power.key());
		s.researchStage.put(power.key(), ResearchStage.MUTATION_CONFIRMED.ordinal());
		if (s.activePower.isEmpty()) {
			s.activePower = power.key();
		}
		save(player, s);
		PowerPassives.activate(player, power);
		return true;
	}

	/**
	 * Permanently give up one owned power, freeing the mutation slot it occupied. The counterpart to
	 * {@link #grant}, and the mechanism behind an <em>evolution</em>: Spider Adhesion is forgotten in
	 * the same breath that the Spider-Man Hero Class is granted, so the player is never holding both
	 * halves of the same power at once (see {@code SpiderMan#evolveFromAdhesion}).
	 *
	 * <p>Everything belonging to the power goes with it -- toggles, cycles, resources, markers and its
	 * cooldowns -- so nothing can be left behind to affect the player afterwards. Research progress is
	 * deliberately kept: the player did do that research, and it costs nothing to remember it.
	 *
	 * @return false if the power was not owned, in which case nothing changed
	 */
	public static boolean forget(ServerPlayer player, Power power) {
		if (!state(player).ownedPowers.contains(power.key())) {
			return false;
		}
		// Turn the power fully off first -- its toggled modes (onToggleOff) and its power-wide passives --
		// while it is still "owned", so every teardown hook sees a consistent state. This replaces the
		// old "route through setActive(null)", which no longer clears anything on a plain slot switch.
		String prefix = power.key() + "/";
		ExperimentalState s = state(player).copy();
		for (Ability ability : power.abilities()) {
			String toggleKey = prefix + ability.id();
			if (s.activeToggles.remove(toggleKey)) {
				AbilityHandler handler = AbilityHandlers.get(power, ability);
				if (handler != null) {
					handler.onToggleOff(new AbilityContext(player, power, ability, false));
				}
			}
		}
		AbilityHandler.deactivatePassives(player, power);

		s.ownedPowers.remove(power.key());
		s.activeToggles.removeIf(k -> k.startsWith(prefix));
		s.cycleModes.keySet().removeIf(k -> k.startsWith(prefix));
		s.resources.keySet().removeIf(k -> k.startsWith(prefix));
		s.markers.keySet().removeIf(k -> k.startsWith(prefix));
		s.markerDims.keySet().removeIf(k -> k.startsWith(prefix));
		s.abilityReadyAt.keySet().removeIf(k -> k.startsWith(prefix));
		if (power.key().equals(s.activePower)) {
			s.activePower = s.ownedPowers.isEmpty() ? "" : s.ownedPowers.iterator().next();
		}
		save(player, s);
		return true;
	}

	// ---------------- active power ----------------

	public static Power getActive(ServerPlayer player) {
		String key = state(player).activePower;
		return key.isEmpty() ? null : Powers.byKey(key);
	}

	/**
	 * Switch which owned Experimental Tier power occupies the six hotbar slots.
	 *
	 * <p>v0.9.3 -- persistent power stacking: this now ONLY re-points the selected slot-set. It does
	 * <b>not</b> clear the previous power's toggled modes, does <b>not</b> touch anyone's power-wide
	 * passives, and (as always) never resets a cooldown. Every owned power's passives and modes keep
	 * running in the background regardless of which one is selected -- switching is purely "which
	 * power do my six keys cast from now".
	 *
	 * <p>The one thing it does end is any HOLD / CHARGE <em>channel</em> on the power being left: you
	 * are physically no longer holding that power's key, so a beam / brace / charge-up stops. That is
	 * not a mode and not a passive.
	 *
	 * <p>{@code null} / not-owned clears the selection (no power casts from the six slots).
	 */
	public static void setActive(ServerPlayer player, Power power) {
		ExperimentalState s = state(player).copy();
		String newKey = (power != null && s.ownedPowers.contains(power.key())) ? power.key() : "";
		if (newKey.equals(s.activePower)) {
			return;
		}

		Power previous = s.activePower.isEmpty() ? null : Powers.byKey(s.activePower);
		s.activePower = newKey;
		save(player, s);

		if (previous != null) {
			for (Ability ability : previous.abilities()) {
				if (ability.activation() == AbilityActivation.HOLD
						|| ability.activation() == AbilityActivation.CHARGE) {
					AbilityHandler handler = AbilityHandlers.get(previous, ability);
					if (handler != null) {
						handler.onRelease(new AbilityContext(player, previous, ability, false));
					}
				}
			}
		}
	}

	// ---------------- cooldowns (absolute ready-at game time; survive relog/death/dim) ----------------

	private static String cdKey(Power power, Ability ability) {
		return power.key() + "/" + ability.id();
	}

	public static boolean cooldownReady(ServerPlayer player, Power power, Ability ability) {
		Long readyAt = state(player).abilityReadyAt.get(cdKey(power, ability));
		return readyAt == null || player.level().getGameTime() >= readyAt;
	}

	public static int cooldownRemainingTicks(ServerPlayer player, Power power, Ability ability) {
		Long readyAt = state(player).abilityReadyAt.get(cdKey(power, ability));
		if (readyAt == null) {
			return 0;
		}
		return (int) Math.max(0L, readyAt - player.level().getGameTime());
	}

	public static void triggerCooldown(ServerPlayer player, Power power, Ability ability, int ticks) {
		if (ticks <= 0) {
			return;
		}
		ExperimentalState s = state(player).copy();
		s.abilityReadyAt.put(cdKey(power, ability), player.level().getGameTime() + ticks);
		save(player, s);
	}

	// ---------------- toggles ----------------

	public static boolean isToggled(ServerPlayer player, Power power, Ability ability) {
		return state(player).activeToggles.contains(cdKey(power, ability));
	}

	public static void setToggled(ServerPlayer player, Power power, Ability ability, boolean on) {
		ExperimentalState s = state(player).copy();
		String key = cdKey(power, ability);
		boolean changed = on ? s.activeToggles.add(key) : s.activeToggles.remove(key);
		if (changed) {
			save(player, s);
		}
	}

	// ---------------- cycles ----------------

	public static int cycleMode(ServerPlayer player, Power power, Ability ability) {
		return state(player).cycleModes.getOrDefault(cdKey(power, ability), 0);
	}

	public static void setCycleMode(ServerPlayer player, Power power, Ability ability, int mode) {
		ExperimentalState s = state(player).copy();
		s.cycleModes.put(cdKey(power, ability), mode);
		save(player, s);
	}

	// ---------------- resources ----------------

	public static float getResource(ServerPlayer player, Power power, String name) {
		return state(player).resources.getOrDefault(power.key() + "/" + name, 0.0f);
	}

	public static void setResource(ServerPlayer player, Power power, String name, float value, float max) {
		ExperimentalState s = state(player).copy();
		s.resources.put(power.key() + "/" + name, Math.max(0.0f, Math.min(max, value)));
		save(player, s);
	}

	public static void addResource(ServerPlayer player, Power power, String name, float delta, float max) {
		setResource(player, power, name, getResource(player, power, name) + delta, max);
	}

	public static boolean spendResource(ServerPlayer player, Power power, String name, float amount) {
		float have = getResource(player, power, name);
		if (have < amount) {
			return false;
		}
		setResource(player, power, name, have - amount, Float.MAX_VALUE);
		return true;
	}

	// ---------------- markers (named world positions; may be negative) ----------------

	public static net.minecraft.core.BlockPos getMarker(ServerPlayer player, Power power, String name) {
		Long packed = state(player).markers.get(power.key() + "/" + name);
		return packed == null ? null : net.minecraft.core.BlockPos.of(packed);
	}

	/**
	 * The dimension a marker was recorded in, or {@code null} if there is no marker (or it predates
	 * dimension tracking, in which case the caller should assume the player's current dimension).
	 */
	public static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> getMarkerDimension(
			ServerPlayer player, Power power, String name) {
		String dim = state(player).markerDims.get(power.key() + "/" + name);
		if (dim == null) {
			return null;
		}
		net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse(dim);
		return loc == null ? null : net.minecraft.resources.ResourceKey.create(
				net.minecraft.core.registries.Registries.DIMENSION, loc);
	}

	public static void setMarker(ServerPlayer player, Power power, String name, net.minecraft.core.BlockPos pos) {
		setMarker(player, power, name, pos, player.level().dimension().location());
	}

	/** Record a marker in an explicit dimension (Teleportation's chosen-destination portal). */
	public static void setMarker(ServerPlayer player, Power power, String name, net.minecraft.core.BlockPos pos,
			net.minecraft.resources.ResourceLocation dimension) {
		ExperimentalState s = state(player).copy();
		String k = power.key() + "/" + name;
		s.markers.put(k, pos.asLong());
		s.markerDims.put(k, dimension.toString());
		save(player, s);
	}

	public static void clearMarker(ServerPlayer player, Power power, String name) {
		ExperimentalState s = state(player).copy();
		String k = power.key() + "/" + name;
		boolean changed = s.markers.remove(k) != null;
		changed |= s.markerDims.remove(k) != null;
		if (changed) {
			save(player, s);
		}
	}

	// ---------------- research ----------------

	public static ResearchStage researchStage(ServerPlayer player, Power power) {
		return ResearchStage.byOrdinal(state(player).researchStage.getOrDefault(power.key(), 0));
	}

	/** Advances the stage only if {@code stage} is further along than the current one. */
	public static boolean advanceResearch(ServerPlayer player, Power power, ResearchStage stage) {
		ExperimentalState s = state(player).copy();
		int current = s.researchStage.getOrDefault(power.key(), 0);
		if (stage.ordinal() <= current) {
			return false;
		}
		s.researchStage.put(power.key(), stage.ordinal());
		save(player, s);
		return true;
	}

	// ---------------- per-tick ----------------

	/**
	 * Per-player, per-tick upkeep for <b>every owned Experimental Tier power</b> (v0.9.3), not just the
	 * selected one: per-ability {@code onServerTick} (grab / charge / timed-state upkeep),
	 * {@code onToggleTick} for each active toggle, and the power-wide passive tick. This is what keeps
	 * a second owned power's aura / stance / timed buff alive while a different power holds the six
	 * slots.
	 */
	public static void serverTick(ServerPlayer player) {
		ExperimentalState s = state(player);
		if (s.ownedPowers.isEmpty()) {
			return;
		}
		for (String key : new java.util.ArrayList<>(s.ownedPowers)) {
			Power power = Powers.byKey(key);
			if (power == null) {
				continue;
			}
			for (Ability ability : power.abilities()) {
				AbilityHandler handler = AbilityHandlers.get(power, ability);
				if (handler == null) {
					continue;
				}
				AbilityContext ctx = new AbilityContext(player, power, ability, true);
				handler.onServerTick(ctx);
				if (s.activeToggles.contains(power.key() + "/" + ability.id())) {
					handler.onToggleTick(ctx);
				}
			}
			AbilityHandler.tickPassives(player, power);
		}
	}

	/**
	 * Re-establishes the active power's toggle effects on a fresh player entity (join / respawn),
	 * since infinite effects and transient modifiers do not survive a new entity instance. Safe to
	 * call anytime -- toggle handlers must make their {@code onToggleTick} idempotent.
	 */
	public static void reconcileToggles(ServerPlayer player) {
		ExperimentalState s = state(player);
		if (s.ownedPowers.isEmpty()) {
			return;
		}
		for (String key : new java.util.ArrayList<>(s.ownedPowers)) {
			Power power = Powers.byKey(key);
			if (power == null) {
				continue;
			}
			for (Ability ability : power.abilities()) {
				if (s.activeToggles.contains(power.key() + "/" + ability.id())) {
					AbilityHandler handler = AbilityHandlers.get(power, ability);
					if (handler != null) {
						handler.onToggleOn(new AbilityContext(player, power, ability, true));
					}
				}
			}
		}
	}

	// ---------------- debug / admin helpers ----------------

	/**
	 * Cleanly switch off <em>every</em> owned Experimental Tier power before the state is wiped: each
	 * power's active toggles get their {@code onToggleOff}, each power's HOLD / CHARGE channel gets its
	 * {@code onRelease}, and each power's power-wide passives are deactivated. Must run before
	 * {@link #clearAll} so non-attribute mode effects (an infinite hidden potion effect, a noclip flag,
	 * mayfly) are actually removed rather than orphaned. v0.9.3 -- needed now that {@link #setActive}
	 * no longer tears anything down on a plain slot switch.
	 */
	public static void tearDownAll(ServerPlayer player) {
		ExperimentalState s = state(player);
		for (String key : new java.util.ArrayList<>(s.ownedPowers)) {
			Power power = Powers.byKey(key);
			if (power == null) {
				continue;
			}
			for (Ability ability : power.abilities()) {
				AbilityHandler handler = AbilityHandlers.get(power, ability);
				if (handler == null) {
					continue;
				}
				if (s.activeToggles.contains(power.key() + "/" + ability.id())) {
					handler.onToggleOff(new AbilityContext(player, power, ability, false));
				}
				if (ability.activation() == AbilityActivation.HOLD || ability.activation() == AbilityActivation.CHARGE) {
					handler.onRelease(new AbilityContext(player, power, ability, false));
				}
			}
			AbilityHandler.deactivatePassives(player, power);
		}
	}

	public static void clearAll(ServerPlayer player) {
		tearDownAll(player);
		save(player, new ExperimentalState());
	}

	/** Read-only snapshot of cooldown map for HUD/debug. */
	public static Map<String, Long> cooldownSnapshot(ServerPlayer player) {
		return state(player).abilityReadyAt;
	}

	static Iterator<String> ownedKeys(ServerPlayer player) {
		return state(player).ownedPowers.iterator();
	}
}
