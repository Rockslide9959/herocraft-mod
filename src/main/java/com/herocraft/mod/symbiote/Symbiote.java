package com.herocraft.mod.symbiote;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.spider.SpiderMan;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The one server-side API for the Symbiote -- a full standalone power (v0.9.14) any player can bond
 * with, not only Spider-Man.
 *
 * <p>A player must have bonded ({@link SymbioteState#hasSymbiote}) before H does anything -- see
 * {@link SymbioteBonding} for how a bond is formed and {@link SymbioteCompatibility} for what
 * happens to a player's other powers when it does. Which of the two host variants a bonded player
 * gets ({@link SymbioteHostType}) is derived live from whether they currently hold Spider-Man: a
 * Spider-Man keeps that power and becomes Black Suit Spider-Man; anyone else becomes a Normal
 * Symbiote Host with the plain black armour and its own six abilities
 * ({@link SymbioteAbilityManager}). When the suit retracts, and on death / relog / dimension change,
 * the suit and every suit-only buff are removed and the player's real armour is handed back exactly
 * as it was. The doubled web capacity (Spider-Man variant only) is the one exception: it is a
 * permanent perk of having bonded at all
 * ({@link com.herocraft.mod.spider.SpiderWebReserve#maxFor}), not of the suit being worn, so it
 * survives a retract and is only lost by fully unbonding ({@link #remove}).
 *
 * <h2>The suit-up / suit-down animation</h2>
 * Directly mirrors {@code MaxSteelTransform}: pressing H does not instantly swap the suit on or off,
 * it starts a {@link SymbioteState#transformDir} clock ({@link #TRANSFORM_TICKS} /
 * {@link #RETRACT_TICKS} long) that {@link #tick} settles. {@link SymbioteState#active} is true for
 * the whole "on or coming on" span, exactly like {@code MaxSteelState#transformed}. The suit is
 * equipped in full the instant the clock starts (so the model exists to reveal) -- the client-side
 * {@code SymbioteReveal} is what hides bones progressively, keyed off the same clock via
 * {@link SymbioteTransform}, and {@code SymbioteFxClient} times its particle sweep off it too.
 *
 * <h2>Division of responsibility</h2>
 * <ul>
 *   <li>{@link Symbiote} -- ownership, the toggle, the animation clock, lifecycle, the H-key entry.</li>
 *   <li>{@link SymbioteSuit} -- the four synthesised, curse-locked black-suit armour pieces.</li>
 *   <li>{@link SymbioteTransform} -- the clock maths, shared with the client.</li>
 *   <li>{@link SymbioteModifiers} -- the stat changes while active (web capacity clamp; armour is the
 *       suit material itself).</li>
 *   <li>{@code SymbioteReveal} / {@code SymbioteFxClient} (client) -- the bone reveal and the particle
 *       sweep, both driven off {@link SymbioteTransform}.</li>
 * </ul>
 *
 * <h2>Adding real Symbiote abilities later</h2>
 * Give the ability its own class, register its key in {@code SpiderManAbilityManager} (or a dedicated
 * manager once the Symbiote becomes its own power), gate it on {@link #isActive}, and -- if it needs a
 * persistent stat -- add a field to {@link SymbioteState} and a reconcile line to
 * {@link SymbioteModifiers}. Nothing about tendrils, rage, regeneration, a health bar, Venom or
 * sonic/fire weakness exists yet, on purpose.
 */
public final class Symbiote {
	/** ~0.75 s anti-spam gate on the H toggle -- independent of the animation length. */
	public static final int TOGGLE_COOLDOWN_TICKS = 15;
	/**
	 * Ticks the suit-up animation runs: the particles sweep chest, then arms + legs, then head, and
	 * each part's armour only appears once its particles have fully covered it -- three clearly
	 * readable stages need more than an instant, hence longer than {@link #RETRACT_TICKS}.
	 */
	public static final int TRANSFORM_TICKS = 42;
	/** Ticks the shorter retraction runs when deactivating -- peels off in the reverse order. */
	public static final int RETRACT_TICKS = 24;

	/**
	 * How long (in ticks) a bonded, suited-up player must stand in continuous fire/lava before the
	 * Symbiote can't take it any more and tears itself off -- a real, playable version of the
	 * organism's classic fire weakness ({@link SymbioteDamageRules}'s +40% fire damage is the other
	 * half of it), and the one "natural" (non-admin-command) way to be rid of a bond. Resets the
	 * instant the burning stops, so it is sustained exposure, not a stacking counter.
	 */
	private static final int FIRE_EXPOSURE_REMOVE_TICKS = 100; // ~5s
	private static final java.util.Map<Integer, Integer> FIRE_EXPOSURE = new java.util.concurrent.ConcurrentHashMap<>();

	private Symbiote() {
	}

	// ---------------- state ----------------

	public static SymbioteState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.SYMBIOTE_STATE);
	}

	private static void save(ServerPlayer player, SymbioteState state) {
		player.setAttached(ModAttachments.SYMBIOTE_STATE, state);
	}

	/**
	 * Whether the player has bonded with a Symbiote. Safe on the client (the attachment is synced) --
	 * used there only to decide whether H toggles the Symbiote or the costume mask; every gameplay
	 * check runs server-side.
	 */
	public static boolean hasSymbiote(Player player) {
		SymbioteState s = player.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
		return s != null && s.hasSymbiote;
	}

	/**
	 * Whether the black suit is on, or in the middle of coming on / going off. Synced, so client-safe
	 * for rendering/HUD decisions.
	 */
	public static boolean isActive(Player player) {
		SymbioteState s = player.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
		return s != null && s.active;
	}

	/**
	 * Client + server: may this player's H key toggle the Symbiote right now? (Has bonded -- any
	 * bonded player may toggle, Spider-Man or Normal host alike.) The cooldown and the mid-animation
	 * guard are checked server-side in {@link #toggle}.
	 */
	public static boolean canToggle(Player player) {
		return hasSymbiote(player);
	}

	// ---------------- unlock ----------------

	/** Bond the player with a Symbiote (admin/testing entry for now; a real quest can call this later). */
	public static boolean grant(ServerPlayer player) {
		SymbioteState s = state(player);
		if (s.hasSymbiote) {
			return false;
		}
		SymbioteState c = s.copy();
		c.hasSymbiote = true;
		save(player, c);
		player.displayClientMessage(Component.translatable("message.herocraft.symbiote.bonded")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD), false);
		return true;
	}

	/** Remove the bond entirely -- retracts the suit first (immediately, not animated) if it is on. */
	public static boolean remove(ServerPlayer player) {
		SymbioteState s = state(player);
		if (!s.hasSymbiote) {
			return false;
		}
		if (s.active || SymbioteSuit.wearing(player)) {
			hardDeactivate(player);
		}
		SymbioteState c = state(player).copy();
		c.hasSymbiote = false;
		save(player, c);
		SymbioteModifiers.onUnbond(player);
		player.displayClientMessage(Component.translatable("message.herocraft.symbiote.rejected"), false);
		return true;
	}

	// ---------------- the toggle ----------------

	/**
	 * The H key (server side). Re-validates everything -- bond, cooldown, mid-animation -- so a
	 * hacked client that sends the packet without the prerequisites gains nothing. Works identically
	 * for a Normal host or a Spider-Man; {@link SymbioteSuit} is what decides which armour variant to
	 * synthesise.
	 */
	public static void toggle(ServerPlayer player) {
		SymbioteState s = state(player);
		if (!s.hasSymbiote) {
			player.displayClientMessage(
					Component.translatable("message.herocraft.symbiote.not_bonded"), true);
			return;
		}
		long now = player.level().getGameTime();
		if (now < s.toggleReadyAt || SymbioteTransform.isAnimating(s)) {
			return;
		}
		if (s.active) {
			beginSuitDown(player);
		} else {
			beginSuitUp(player);
		}
	}

	private static void beginSuitUp(ServerPlayer player) {
		ItemStack[] displaced = SymbioteSuit.equipAndCapture(player);

		SymbioteState c = state(player).copy();
		c.active = true;
		c.transformDir = SymbioteState.DIR_UP;
		c.transformStartTick = player.level().getGameTime();
		c.transformDurationTicks = TRANSFORM_TICKS;
		c.toggleReadyAt = c.transformStartTick + TOGGLE_COOLDOWN_TICKS;
		c.stowedArmor = ItemContainerContents.fromItems(java.util.List.of(displaced));
		save(player, c);

		SymbioteModifiers.onActivate(player);
		fx(player, true);
		player.displayClientMessage(Component.translatable("message.herocraft.symbiote.activated")
				.withStyle(ChatFormatting.DARK_GRAY), true);
	}

	private static void beginSuitDown(ServerPlayer player) {
		SymbioteState c = state(player).copy();
		c.transformDir = SymbioteState.DIR_DOWN;
		c.transformStartTick = player.level().getGameTime();
		c.transformDurationTicks = RETRACT_TICKS;
		c.toggleReadyAt = c.transformStartTick + TOGGLE_COOLDOWN_TICKS;
		save(player, c);

		fx(player, false);
		player.displayClientMessage(Component.translatable("message.herocraft.symbiote.deactivated")
				.withStyle(ChatFormatting.GRAY), true);
	}

	/** Settle a finished suit-up / suit-down. Called from {@link #tick} once the clock runs out. */
	private static void settle(ServerPlayer player) {
		SymbioteState s = state(player);
		if (s.transformDir == SymbioteState.DIR_UP) {
			SymbioteState c = s.copy();
			c.transformDir = SymbioteState.DIR_IDLE;
			save(player, c);
		} else if (s.transformDir == SymbioteState.DIR_DOWN) {
			restoreArmorAndFinish(player);
		}
	}

	/**
	 * Retract the suit right now, with no animation: strip it, hand back the real armour it displaced,
	 * clear every buff. Used for anything that is not the player pressing H -- death, relog, dimension
	 * change, losing the Spider-Man power, {@code /spiderman symbiote remove}.
	 */
	private static void hardDeactivate(ServerPlayer player) {
		restoreArmorAndFinish(player);
	}

	/** The one place that strips the suit, hands back stowed armour, clears buffs, and settles idle. */
	private static void restoreArmorAndFinish(ServerPlayer player) {
		SymbioteState s = state(player);
		var stowed = net.minecraft.core.NonNullList.<ItemStack>withSize(SymbioteSuit.SLOTS.length, ItemStack.EMPTY);
		s.stowedArmor.copyInto(stowed);

		SymbioteSuit.strip(player);
		SymbioteSuit.restoreCaptured(player, stowed.toArray(new ItemStack[0]));

		SymbioteState c = state(player).copy();
		c.active = false;
		c.transformDir = SymbioteState.DIR_IDLE;
		c.stowedArmor = ItemContainerContents.EMPTY;
		save(player, c);

		// v0.9.13: web capacity is a bond perk now, not a suit-on perk (SpiderWebReserve.maxFor keys off
		// hasSymbiote), so retracting the suit no longer lowers the cap -- nothing to clamp here.
	}

	// ---------------- per-tick + lifecycle ----------------

	/**
	 * Per-player server tick, called unconditionally for every player from
	 * {@code AbilityRouter.serverTick} (v0.9.14 -- previously piggybacked on
	 * {@code SpiderManAbilityManager}'s own tick, which silently never ran it for a non-Spider-Man
	 * host). Advances/settles the transform clock and keeps an active Symbiote's suit honest (re-equip
	 * a slot that came up empty, delete any loose copy, swap variant if the host type changed -- all
	 * should be unreachable in ordinary play now that the pieces are curse-locked, but they cost
	 * nothing to keep as a backstop). A no-op for a player with no Symbiote bond.
	 */
	public static void tick(ServerPlayer player) {
		SymbioteState s = state(player);
		if (SymbioteTransform.isAnimating(s)
				&& player.level().getGameTime() - s.transformStartTick >= s.transformDurationTicks) {
			settle(player);
			s = state(player);
		}

		if (!s.hasSymbiote) {
			return;
		}
		if (player.tickCount % 20 == 0) {
			SymbioteModifiers.reconcileBlackSuit(player);
		}
		tickFireWeakness(player, s);
		// tickFireWeakness may have just severed the bond outright (#remove), which strips the suit and
		// hands back real armour -- re-read rather than trust the pre-removal copy, or the reconciliation
		// below would see stale active=true and immediately re-equip the suit it was just removed from.
		s = state(player);
		if (!s.hasSymbiote) {
			return;
		}
		if (s.active) {
			// Edge case: a bonded Spider-Man who loses the Spider-Man power some other way (e.g. an
			// admin `/heropower revoke hero spider_man`) demotes to a Normal host rather than losing the
			// suit outright -- the bond persists, only the variant changes. Swap immediately (no
			// animation) if the worn pieces no longer match the current host type.
			if (SymbioteSuit.wearing(player) && !SymbioteSuit.wearingCorrectVariant(player)) {
				SymbioteSuit.strip(player);
				SymbioteSuit.equipAndCapture(player);
			}
			SymbioteSuit.reequipMissing(player);
			SymbioteSuit.deleteLoose(player);
		}
	}

	/**
	 * The "you cannot keep this" gate, called for <em>every</em> player from
	 * {@code AbilityRouter.serverTick} (like {@code PunisherArmorGate}). Anyone who is not an active
	 * Symbiote Spider-Man has every Symbiote piece removed from them -- so a piece cannot be traded to
	 * another player, stored in a chest and taken back out, or picked up off the ground. Curse of
	 * Binding should make this unreachable for a live wearer now; it remains the backstop for anything
	 * that got a piece some other way.
	 */
	public static void enforce(ServerPlayer player) {
		if (player.isSpectator()) {
			return;
		}
		boolean eligible = isActive(player) && hasSymbiote(player);
		if (eligible) {
			return;
		}
		boolean wearing = SymbioteSuit.wearing(player);
		// The common case is a player who has never touched a Symbiote: a cheap 4-slot check and out.
		// Only sweep the whole inventory if a piece is worn, the player has ever bonded, or on a slow
		// 2-second cadence (catches a piece that was traded to an outsider).
		if (!wearing && !hasSymbiote(player) && player.tickCount % 40 != 0) {
			return;
		}
		if (wearing || SymbioteSuit.deleteLoose(player)) {
			SymbioteSuit.stripEverywhere(player);
		}
	}

	/**
	 * Death / relog / dimension change / power revoke: retract the suit immediately (no animation),
	 * hand back real armour, and drop every buff (the bond itself is kept). Wired into
	 * {@link SpiderMan#clearTransient}.
	 */
	public static void clearTransient(ServerPlayer player) {
		SymbioteState s = player.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
		if (s != null && (s.active || SymbioteTransform.isAnimating(s) || SymbioteSuit.wearing(player))) {
			hardDeactivate(player);
			return;
		}
		SymbioteSuit.stripEverywhere(player);
		// Belt and braces: a fresh entity that inherited active=true / a stale animation via
		// copyOnDeath must not survive into the new life/session.
		if (s != null && (s.active || s.transformDir != SymbioteState.DIR_IDLE || s.onslaughtChargeStart >= 0)) {
			SymbioteState c = s.copy();
			c.active = false;
			c.transformDir = SymbioteState.DIR_IDLE;
			c.stowedArmor = ItemContainerContents.EMPTY;
			c.onslaughtChargeStart = -1L;
			save(player, c);
		}
	}

	/**
	 * Sustained fire/lava contact while suited eventually severs the bond outright -- see
	 * {@link #FIRE_EXPOSURE_REMOVE_TICKS}. Skips creative/invulnerable players (they were never in
	 * danger from the fire in the first place) and resets the moment the burning stops.
	 */
	private static void tickFireWeakness(ServerPlayer player, SymbioteState s) {
		if (!s.active || player.getAbilities().invulnerable) {
			FIRE_EXPOSURE.remove(player.getId());
			return;
		}
		if (!(player.isOnFire() || player.isInLava())) {
			FIRE_EXPOSURE.remove(player.getId());
			return;
		}
		int ticks = FIRE_EXPOSURE.merge(player.getId(), 1, Integer::sum);
		if (ticks == 1) {
			player.displayClientMessage(Component.translatable("message.herocraft.symbiote.fire_recoil")
					.withStyle(ChatFormatting.RED), true);
		}
		if (ticks < FIRE_EXPOSURE_REMOVE_TICKS) {
			return;
		}
		FIRE_EXPOSURE.remove(player.getId());
		ServerLevel level = player.serverLevel();
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
				player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.8, 0.5, 0.06);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK,
				player.getX(), player.getY() + 1, player.getZ(), 60, 0.5, 0.8, 0.5, 0.1);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.5f);
		player.displayClientMessage(Component.translatable("message.herocraft.symbiote.burned_off")
				.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
		remove(player);
	}

	/** Server-stop cleanup for {@link #FIRE_EXPOSURE} -- same discipline as every other static session map. */
	public static void clearSessionState() {
		FIRE_EXPOSURE.clear();
	}

	private static void fx(ServerPlayer player, boolean on) {
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				on ? SoundEvents.SLIME_SQUISH : SoundEvents.HONEY_BLOCK_SLIDE,
				SoundSource.PLAYERS, 0.7f, on ? 0.5f : 0.8f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.4f, on ? 0.6f : 1.4f);
	}
}
