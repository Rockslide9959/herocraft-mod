package com.projecthero.mod.ultron;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

/**
 * v0.16.1: Ultron "in your systems" -- jammed ability keys. A Hijacker that latches on kills one random ability key (one
 * of the six universal slots, which every hero, Iron Man and mutation kit uses) for 6 s; Prime's Network Override kills
 * all eight for 4 s. {@code AbilityRouter#handleInput} asks {@link #blocks} before anything else, so a jammed key does
 * nothing (key releases still go through, so a held ability always ends).
 *
 * <p>Static state ({@link #JAMMED}, per player: the game tick each slot stays dead until) is cleared in
 * {@code ServerStateReset}. The client only gets a cosmetic {@link UltronSignalPayload} for its overlay.
 */
public final class UltronJam {
	/** Every slot (1..8). */
	public static final int ALL = 0xFF;
	private static final Map<UUID, long[]> JAMMED = new HashMap<>();

	private UltronJam() {
	}

	/** The bit for ability slot {@code slot} (1..8). */
	public static int bit(int slot) {
		return 1 << (slot - 1);
	}

	/** One of the six universal ability slots, at random (1..6). */
	public static int randomSlot(RandomSource random) {
		return 1 + random.nextInt(AbilitySlot.CORE_COUNT);
	}

	/**
	 * Jams {@code player}'s slots in {@code mask} for {@code ticks}. {@code override} picks the client's look: the full red
	 * static (Network Override) or a one-key warning (a Hijacker).
	 */
	public static void jam(ServerPlayer player, int mask, int ticks, boolean override) {
		long until = player.serverLevel().getGameTime() + ticks;
		long[] slots = JAMMED.computeIfAbsent(player.getUUID(), u -> new long[8]);
		for (int s = 1; s <= 8; s++) {
			if ((mask & bit(s)) != 0) {
				slots[s - 1] = Math.max(slots[s - 1], until);
			}
		}
		if (ServerPlayNetworking.canSend(player, UltronSignalPayload.TYPE)) {
			ServerPlayNetworking.send(player, new UltronSignalPayload(UltronSignalPayload.JAM, override ? 1 : 0, mask, ticks));
		}
	}

	/** Is {@code player}'s ability slot {@code slot} (1..8) jammed right now? */
	public static boolean isJammed(ServerPlayer player, int slot) {
		long[] slots = JAMMED.get(player.getUUID());
		if (slots == null || slot < 1 || slot > 8) {
			return false;
		}
		return slots[slot - 1] > player.serverLevel().getGameTime();
	}

	/** How many of {@code player}'s slots are jammed right now. */
	public static int jammedCount(ServerPlayer player) {
		int n = 0;
		for (int s = 1; s <= 8; s++) {
			if (isJammed(player, s)) {
				n++;
			}
		}
		return n;
	}

	/**
	 * The ability router's gate: true (and a short action-bar note) when a key press on {@code slot} must be refused. Key
	 * releases are never blocked.
	 */
	public static boolean blocks(ServerPlayer player, int slot, boolean pressed) {
		if (!pressed || !isJammed(player, slot)) {
			return false;
		}
		long[] slots = JAMMED.get(player.getUUID());
		int secs = (int) Math.ceil((slots[slot - 1] - player.serverLevel().getGameTime()) / 20.0);
		player.displayClientMessage(Component.translatable("message.projecthero.ultron.jammed", secs).withStyle(ChatFormatting.RED), true);
		return true;
	}

	/** Prime's Network Override telegraph: a red reticle above {@code target} for {@code ticks}, for everyone near. */
	public static void reticle(ServerLevel level, Entity target, int ticks) {
		UltronSignalPayload p = new UltronSignalPayload(UltronSignalPayload.RETICLE, target.getId(), 0, ticks);
		for (ServerPlayer viewer : PlayerLookup.around(level, target.position(), 96.0)) {
			if (ServerPlayNetworking.canSend(viewer, UltronSignalPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, p);
			}
		}
	}

	/** Lifts every jam on {@code player} (death, tests). */
	public static void clear(ServerPlayer player) {
		JAMMED.remove(player.getUUID());
	}

	public static void clearSessionState() {
		JAMMED.clear();
	}
}
