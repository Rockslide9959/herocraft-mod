package com.projecthero.mod.symbiote;

import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v0.17.5 (user: "make it so that youre able to change the colour"): the three colours of a Normal host's Symbiote suit, each a
 * plain {@code 0xRRGGBB} -- {@link #primary} the living body, {@link #secondary} the spider emblem, {@link #eyes} the lenses. Any
 * RGB is allowed; the suit keeps its shading because it is drawn from a luminance mask ({@code SymbioteSuitTint}). Stored per
 * player in {@code ModAttachments.SYMBIOTE_COLOURS} (persistent, kept through death, synced to everyone). Every way in --
 * the codec, the network, the screen, the command -- goes through {@link #sanitize}, so a stray alpha byte or a negative value
 * can never reach the renderer.
 *
 * <p>v0.17.5 (user: "Remove all the classic suit styles from this power and just make it the lethal protector variants"): there is
 * no suit style choice any more -- every Normal host wears the Lethal Protector suit ({@link SymbioteSuitStyle}). The short-lived
 * {@code style} field is gone: saves that still carry {@code "style": "classic"} (or anything else) load fine, the field is simply
 * ignored, so they show the Lethal Protector too.
 */
public record SymbioteColours(int primary, int secondary, int eyes) {
	/** The Classic colours: glossy blue-black, white emblem, white eyes. Same values as {@link SymbioteColourPreset#CLASSIC}. */
	public static final SymbioteColours CLASSIC = new SymbioteColours(0x14161F, 0xF2F4F8, 0xF4F6FA);

	public SymbioteColours {
		primary = rgb(primary);
		secondary = rgb(secondary);
		eyes = rgb(eyes);
	}

	/** Unknown keys (the old {@code style}) are ignored by the record codec, so pre-change saves still load. */
	public static final Codec<SymbioteColours> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("primary", CLASSIC.primary).forGetter(SymbioteColours::primary),
			Codec.INT.optionalFieldOf("secondary", CLASSIC.secondary).forGetter(SymbioteColours::secondary),
			Codec.INT.optionalFieldOf("eyes", CLASSIC.eyes).forGetter(SymbioteColours::eyes))
			.apply(i, SymbioteColours::new));

	public static final StreamCodec<ByteBuf, SymbioteColours> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, SymbioteColours::primary,
			ByteBufCodecs.INT, SymbioteColours::secondary,
			ByteBufCodecs.INT, SymbioteColours::eyes,
			SymbioteColours::new);

	/** Drops anything above the low 24 bits (alpha, sign), leaving a valid {@code 0xRRGGBB}. */
	public static int rgb(int value) {
		return value & 0xFFFFFF;
	}

	/** Packs three channels, each clamped to 0..255 (so 300 -> 255, -4 -> 0). */
	public static int rgb(int r, int g, int b) {
		return clampChannel(r) << 16 | clampChannel(g) << 8 | clampChannel(b);
	}

	public static int clampChannel(int c) {
		return Math.max(0, Math.min(255, c));
	}

	/** {@code this} with every channel made valid (already true for any instance; kept as the explicit entry point). */
	public SymbioteColours sanitize() {
		return new SymbioteColours(primary, secondary, eyes);
	}

	/** The Classic colours (the skin as painted). */
	public boolean isClassic() {
		return equals(CLASSIC);
	}

	/** Same as {@link #isClassic} (kept: the style that once made them differ is gone). */
	public boolean hasClassicColours() {
		return isClassic();
	}

	/** The same three colours. */
	public boolean sameColours(SymbioteColours other) {
		return equals(other);
	}

	public SymbioteColours withPrimary(int rgb) {
		return new SymbioteColours(rgb, secondary, eyes);
	}

	public SymbioteColours withSecondary(int rgb) {
		return new SymbioteColours(primary, rgb, eyes);
	}

	public SymbioteColours withEyes(int rgb) {
		return new SymbioteColours(primary, secondary, rgb);
	}

	/** The colour of channel {@code 0} primary, {@code 1} secondary, {@code 2} eyes. */
	public int channel(int index) {
		return switch (index) {
			case 1 -> secondary;
			case 2 -> eyes;
			default -> primary;
		};
	}

	public SymbioteColours withChannel(int index, int rgb) {
		return switch (index) {
			case 1 -> withSecondary(rgb);
			case 2 -> withEyes(rgb);
			default -> withPrimary(rgb);
		};
	}

	/**
	 * Reads {@code "#1a2b3c"}, {@code "1a2b3c"}, {@code "0x1a2b3c"} or the short {@code "#abc"}; anything else (bad digits, wrong
	 * length, empty) is empty.
	 */
	public static Optional<Integer> parseHex(String text) {
		if (text == null) {
			return Optional.empty();
		}
		String s = text.trim().toLowerCase(Locale.ROOT);
		if (s.startsWith("#")) {
			s = s.substring(1);
		} else if (s.startsWith("0x")) {
			s = s.substring(2);
		}
		if (s.length() == 3) {
			s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
		}
		if (s.length() != 6) {
			return Optional.empty();
		}
		for (int i = 0; i < 6; i++) {
			if (Character.digit(s.charAt(i), 16) < 0) {
				return Optional.empty();
			}
		}
		return Optional.of(Integer.parseInt(s, 16));
	}

	/** {@code "#RRGGBB"}, upper case. */
	public static String hex(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb(rgb));
	}

	/** A cache key for the colours (equal keys only for equal values): the plain colour triple. */
	public String key() {
		return String.format(Locale.ROOT, "%06x_%06x_%06x", primary, secondary, eyes);
	}
}
