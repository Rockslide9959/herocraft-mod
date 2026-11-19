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

	/** Stores the colours exactly (sanitised), no other checks -- the screen's payload, the commands, the tests. */
	public static void set(ServerPlayer player, SymbioteColours colours) {
		SymbioteColours clean = (colours == null ? SymbioteColours.CLASSIC : colours).sanitize();
		player.setAttached(ModAttachments.SYMBIOTE_COLOURS, clean);
	}

	/** New colours (the colour commands). */
	public static void setColours(ServerPlayer player, SymbioteColours colours) {
		set(player, colours);
	}

	/** A preset's colours. */
	public static void setPreset(ServerPlayer player, SymbioteColourPreset preset) {
		setColours(player, preset.colours());
	}

	// ------------------------------------------------------------------ v0.17.5: a random scheme on getting the Symbiote

	/** Share of fresh grants that get one of the seven presets; the rest get a random harmonious custom scheme. */
	public static final float PRESET_CHANCE = 0.7f;

	/**
	 * v0.17.5 (user: "when player gets the symbiote they get a random colour"): called by {@link Symbiote#grant} -- the single
	 * place a bond is made (Symbiote bonding, serums / PowerGrants, {@code /symbiote bond}, the Black Suit command) -- so it runs on a
	 * fresh bond only: never on relog or death (the bond persists, nothing re-grants), but re-bonding after losing the Symbiote rolls
	 * again. Draws from the player's own RNG (seed it with {@code player.getRandom().setSeed(..)} for a deterministic test).
	 */
	public static SymbioteColours rollOnGrant(ServerPlayer player) {
		SymbioteColours c = randomScheme(player.getRandom());
		set(player, c);
		return c;
	}

	/** {@link #PRESET_CHANCE}: a random preset; otherwise {@link #harmonious}. Pure: the same RNG state gives the same scheme. */
	public static SymbioteColours randomScheme(net.minecraft.util.RandomSource r) {
		if (r.nextFloat() < PRESET_CHANCE) {
			SymbioteColourPreset[] all = SymbioteColourPreset.values();
			return all[r.nextInt(all.length)].colours();
		}
		return harmonious(r);
	}

	/**
	 * A random custom scheme that still reads as a Symbiote: one of three body kinds, each with an emblem that contrasts with it and
	 * eyes that read against the body.
	 * <ul>
	 *   <li>dark body (any hue, deep): a pale tinted emblem or a vivid accent near the complementary hue; near-white eyes (or the
	 *   accent colour);</li>
	 *   <li>vivid body (saturated, mid-bright): a near-black emblem with a hint of the hue or its complement; near-white eyes;</li>
	 *   <li>pale body (Anti-Venom-like): a dark emblem; dark or vivid eyes.</li>
	 * </ul>
	 */
	public static SymbioteColours harmonious(net.minecraft.util.RandomSource r) {
		float h = r.nextFloat();
		int kind = r.nextInt(3);
		int body, emblem, eyes;
		switch (kind) {
			case 0 -> {
				body = hsv(h, range(r, 0.35f, 0.75f), range(r, 0.10f, 0.24f));
				if (r.nextBoolean()) {
					emblem = hsv(h, range(r, 0.04f, 0.15f), range(r, 0.88f, 0.97f));
					eyes = hsv(h, range(r, 0f, 0.06f), range(r, 0.95f, 1f));
				} else {
					emblem = hsv(h + 0.5f + range(r, -0.08f, 0.08f), range(r, 0.70f, 0.90f), range(r, 0.75f, 0.92f));
					eyes = r.nextFloat() < 0.3f ? emblem : hsv(h, range(r, 0f, 0.06f), range(r, 0.95f, 1f));
				}
			}
			case 1 -> {
				body = hsv(h, range(r, 0.65f, 0.90f), range(r, 0.55f, 0.80f));
				float eh = r.nextBoolean() ? h + range(r, -0.05f, 0.05f) : h + 0.5f;
				emblem = hsv(eh, range(r, 0.30f, 0.60f), range(r, 0.06f, 0.14f));
				eyes = hsv(h, range(r, 0f, 0.06f), range(r, 0.95f, 1f));
			}
			default -> {
				body = hsv(h, range(r, 0.03f, 0.12f), range(r, 0.86f, 0.95f));
				float eh = r.nextFloat();
				emblem = hsv(eh, range(r, 0.20f, 0.60f), range(r, 0.06f, 0.14f));
				eyes = r.nextBoolean() ? hsv(eh, range(r, 0.15f, 0.40f), range(r, 0.05f, 0.10f))
						: hsv(r.nextFloat(), range(r, 0.70f, 0.90f), range(r, 0.70f, 0.90f));
			}
		}
		// a deep blue or violet "vivid" body is dark in luminance: make sure the emblem still stands out (pale on dark, dark on light)
		if (Math.abs(luminance(body) - luminance(emblem)) < 0.3f) {
			emblem = luminance(body) < 0.45f ? hsv(h, 0.08f, 0.93f) : hsv(h + 0.5f, 0.4f, 0.1f);
		}
		return new SymbioteColours(body, emblem, eyes);
	}

	/** Perceived brightness of {@code 0xRRGGBB}, 0..1. */
	public static float luminance(int rgb) {
		return (0.299f * ((rgb >> 16) & 0xFF) + 0.587f * ((rgb >> 8) & 0xFF) + 0.114f * (rgb & 0xFF)) / 255f;
	}

	private static float range(net.minecraft.util.RandomSource r, float lo, float hi) {
		return lo + r.nextFloat() * (hi - lo);
	}

	private static int hsv(float h, float s, float v) {
		float hue = h - (float) Math.floor(h);
		return net.minecraft.util.Mth.hsvToRgb(Math.min(hue, 0.9999f), s, v) & 0xFFFFFF;
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
