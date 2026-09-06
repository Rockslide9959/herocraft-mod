package com.projecthero.mod.power;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * Everything the player is <em>told</em> about Mjolnir: action-bar lines and the sound cues that go
 * with them.
 *
 * <p>Kept in one place so the wording and the cues stay consistent, and -- more importantly -- so
 * the anti-spam rule is enforced in exactly one spot. Every method here fires once per triggering
 * event (a keypress, an arrival, a bind), never on a tick.
 */
public final class ThorFeedback {
	private ThorFeedback() {
	}

	// ---------------- recall ----------------

	/** The hammer is loaded and nearby -- it will visibly fly in from wherever it is. */
	public static void recallStartedNear(Player player) {
		actionBar(player, "message.projecthero.recall.returning", ChatFormatting.AQUA);
		thunderCue(player, 1.2f);
	}

	/** The hammer was far away, in an unloaded chunk, or in another dimension. */
	public static void recallStartedFar(Player player) {
		actionBar(player, "message.projecthero.recall.answering", ChatFormatting.AQUA);
		thunderCue(player, 0.8f);
	}

	public static void recallArrived(Player player) {
		actionBar(player, "message.projecthero.recall.returned", ChatFormatting.GOLD);
	}

	/** Already in hand or inventory -- nothing to do, but say so rather than failing silently. */
	public static void recallAlreadyHeld(Player player) {
		actionBar(player, "message.projecthero.recall.already_held", ChatFormatting.GRAY);
	}

	public static void recallNoHammer(Player player) {
		actionBar(player, "message.projecthero.recall.none", ChatFormatting.RED);
	}

	public static void recallBlocked(Player player) {
		actionBar(player, "message.projecthero.recall.blocked", ChatFormatting.RED);
		player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 0.7f);
	}

	public static void recallInventoryFull(Player player) {
		actionBar(player, "message.projecthero.recall.inventory_full", ChatFormatting.YELLOW);
	}

	/**
	 * Sent to a temporary wielder (a worthy non-owner) the instant the bound owner calls the hammer
	 * out of their hand or inventory. Deliberately mild -- they did nothing wrong, ownership just
	 * outranks a worthy borrower -- so this is a single subdued action-bar line, not a chat message
	 * or anything that reads as a penalty.
	 */
	public static void hammerTakenByOwner(Player wielder) {
		actionBar(wielder, "message.projecthero.recall.taken_from_you", ChatFormatting.GRAY);
	}

	// ---------------- hammerless flight grace ----------------

	/** A flying throw just started the 15-second grace -- see {@link ThorPowers#throwMjolnir}. */
	public static void hammerlessFlightStarted(Player player) {
		actionBar(player, "message.projecthero.hammerless_flight.started", ChatFormatting.AQUA);
	}

	/** The grace ran out before Mjolnir came back -- flight has just ended. */
	public static void hammerlessFlightFaded(Player player) {
		actionBar(player, "message.projecthero.hammerless_flight.faded", ChatFormatting.GRAY);
	}

	// ---------------- binding ----------------

	/**
	 * The Power of Thor announcement. Sent as chat (not the action bar) because it is a one-off
	 * milestone the player should be able to scroll back to -- and only ever on a successful bind,
	 * never on login or on a tick.
	 */
	public static void bound(Player player) {
		player.sendSystemMessage(Component.empty());
		player.sendSystemMessage(Component.translatable("message.projecthero.bind.worthy")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		player.sendSystemMessage(Component.translatable("message.projecthero.bind.power")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	public static void unbound(Player player) {
		actionBar(player, "message.projecthero.bind.released", ChatFormatting.GRAY);
	}

	/** Someone tried to bind a hammer that already answers to another player. */
	public static void boundToSomeoneElse(Player player, String ownerName) {
		player.displayClientMessage(
				Component.translatable("message.projecthero.bind.taken",
						Component.literal(ownerName).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.RED),
				true);
		player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 0.7f);
	}

	// ---------------- internals ----------------

	private static void actionBar(Player player, String key, ChatFormatting color) {
		player.displayClientMessage(Component.translatable(key).withStyle(color), true);
	}

	/** Subtle distant-thunder cue. Quiet and pitched so it reads as a cue, not a strike. */
	private static void thunderCue(Player player, float pitch) {
		player.level().playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
				SoundSource.PLAYERS, 0.25f, pitch);
	}
}
