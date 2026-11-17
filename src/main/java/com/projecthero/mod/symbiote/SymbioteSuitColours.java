package com.projecthero.mod.symbiote;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.network.SymbioteColourPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5: the server side of the Symbiote suit colours. The colours live in the synced, persistent
 * {@link ModAttachments#SYMBIOTE_COLOURS} attachment (no static state, so nothing for {@code ServerStateReset}); the colour
 * screen sends {@link SymbioteColourPayload}, re-checked here; {@code /symbiote colour} sets them directly. The look itself is
 * drawn client side ({@code client/symbiote/SymbioteSuitTint}).
 */
public final class SymbioteSuitColours {
	private SymbioteSuitColours() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playC2S().register(SymbioteColourPayload.TYPE, SymbioteColourPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SymbioteColourPayload.TYPE, (payload, context) -> context.server().execute(() -> {
			ServerPlayer p = context.player();
			if (p != null) {
				apply(p, payload.colours());
			}
		}));
	}

	/** The player's suit colours (Classic until they pick something). Client-safe: the attachment is synced to everyone. */
	public static SymbioteColours get(Player player) {
		SymbioteColours c = player.getAttachedOrElse(ModAttachments.SYMBIOTE_COLOURS, SymbioteColours.CLASSIC);
		return c == null ? SymbioteColours.CLASSIC : c;
	}

	/** Stores the colours and style exactly (sanitised), no other checks -- the screen's payload, the tests. */
	public static void set(ServerPlayer player, SymbioteColours colours) {
		SymbioteColours clean = (colours == null ? SymbioteColours.CLASSIC : colours).sanitize();
		player.setAttached(ModAttachments.SYMBIOTE_COLOURS, clean);
	}

	/** New colours, keeping the player's suit style (the colour commands). */
	public static void setColours(ServerPlayer player, SymbioteColours colours) {
		set(player, (colours == null ? SymbioteColours.CLASSIC : colours).withStyle(get(player).style()));
	}

	/** A preset's colours, keeping the player's suit style. */
	public static void setPreset(ServerPlayer player, SymbioteColourPreset preset) {
		setColours(player, preset.colours());
	}

	/** v0.17.5: the suit style, keeping the colours ({@code /symbiote style}). */
	public static void setStyle(ServerPlayer player, SymbioteSuitStyle style) {
		set(player, get(player).withStyle(style));
	}

	/**
	 * The colour screen's request: only a bonded player may recolour their Symbiote. Returns whether it was applied.
	 */
	public static boolean apply(ServerPlayer player, SymbioteColours colours) {
		if (!Symbiote.hasSymbiote(player) || colours == null) {
			return false;
		}
		set(player, colours);
		return true;
	}
}
