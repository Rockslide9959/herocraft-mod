package com.projecthero.mod.power;

import com.projecthero.mod.hammer.ThorWeapon;

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
	// v0.15.1: every recall line names the weapon that is answering -- the call key now brings Mjolnir OR
	// Stormbreaker (see MjolnirRecall). The no-argument forms are Mjolnir's, unchanged.

	/** The hammer is loaded and nearby -- it will visibly fly in from wherever it is. */
	public static void recallStartedNear(Player player) {
		recallStartedNear(player, ThorWeapon.MJOLNIR);
	}

	public static void recallStartedNear(Player player, ThorWeapon weapon) {
		actionBar(player, key("message.projecthero.recall.returning", weapon), ChatFormatting.AQUA);
		thunderCue(player, 1.2f);
	}

	/** The hammer was far away, in an unloaded chunk, or in another dimension. */
	public static void recallStartedFar(Player player) {
		recallStartedFar(player, ThorWeapon.MJOLNIR);
	}

	public static void recallStartedFar(Player player, ThorWeapon weapon) {
		actionBar(player, key("message.projecthero.recall.answering", weapon), ChatFormatting.AQUA);
		thunderCue(player, 0.8f);
	}

	public static void recallArrived(Player player) {
		recallArrived(player, ThorWeapon.MJOLNIR);
	}

	public static void recallArrived(Player player, ThorWeapon weapon) {
		actionBar(player, key("message.projecthero.recall.returned", weapon), ChatFormatting.GOLD);
	}

	/** Already in hand -- nothing to do, but say so rather than failing silently. */
	public static void recallAlreadyHeld(Player player) {
		recallAlreadyHeld(player, ThorWeapon.MJOLNIR);
	}

	public static void recallAlreadyHeld(Player player, ThorWeapon weapon) {
		actionBar(player, key("message.projecthero.recall.already_held", weapon), ChatFormatting.GRAY);
	}

	/** v0.15.1: Mjolnir and Stormbreaker are both already on the player -- there is nothing left to call. */
	public static void recallBothWithYou(Player player) {
		actionBar(player, "message.projecthero.recall.both_with", ChatFormatting.GRAY);
	}

	/** v0.15.3: both weapons are switched off on the N screen -- R calls nothing. */
	public static void recallNoneActive(Player player) {
		actionBar(player, "message.projecthero.recall.none_active", ChatFormatting.YELLOW);
	}

	/** v0.14.0: was elsewhere in the backpack -- swapped straight into the main hand mid-fight. */
	public static void recallEquipped(Player player) {
		recallEquipped(player, ThorWeapon.MJOLNIR);
	}

	public static void recallEquipped(Player player, ThorWeapon weapon) {
		actionBar(player, key("message.projecthero.recall.equipped", weapon), ChatFormatting.AQUA);
		thunderCue(player, 0.6f);
	}

	public static void recallNoHammer(Player player) {
		recallNoHammer(player, ThorWeapon.MJOLNIR);
	}

	public static void recallNoHammer(Player player, ThorWeapon weapon) {
		actionBar(player, key("message.projecthero.recall.none", weapon), ChatFormatting.RED);
	}

	public static void recallBlocked(Player player) {
		actionBar(player, "message.projecthero.recall.blocked", ChatFormatting.RED);
		player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 0.7f);
	}

	public static void recallInventoryFull(Player player) {
		recallInventoryFull(player, ThorWeapon.MJOLNIR);
	}

	public static void recallInventoryFull(Player player, ThorWeapon weapon) {
		actionBar(player, key("message.projecthero.recall.inventory_full", weapon), ChatFormatting.YELLOW);
	}

	/**
	 * Sent to a temporary wielder (a worthy non-owner) the instant the bound owner calls the hammer
	 * out of their hand or inventory. Deliberately mild -- they did nothing wrong, ownership just
	 * outranks a worthy borrower -- so this is a single subdued action-bar line, not a chat message
	 * or anything that reads as a penalty.
	 */
	public static void hammerTakenByOwner(Player wielder) {
		hammerTakenByOwner(wielder, ThorWeapon.MJOLNIR);
	}

	public static void hammerTakenByOwner(Player wielder, ThorWeapon weapon) {
		actionBar(wielder, key("message.projecthero.recall.taken_from_you", weapon), ChatFormatting.GRAY);
	}

	/** v0.15.1: a worthy player's first Stormbreaker has just bound to them. One line, once per bind. */
	public static void stormbreakerBound(Player player) {
		actionBar(player, "message.projecthero.bind.stormbreaker", ChatFormatting.AQUA);
	}

	/** Mjolnir keeps the original keys; Stormbreaker's are the same keys with a {@code .stormbreaker} suffix. */
	private static String key(String base, ThorWeapon weapon) {
		return weapon == ThorWeapon.STORMBREAKER ? base + ".stormbreaker" : base;
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
				.withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD));
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
