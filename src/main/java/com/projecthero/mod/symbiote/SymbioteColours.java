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
 * <p>v0.17.5 (Lethal Protector): also carries the suit {@link #style} (which model), so the style saves, syncs and survives death with
 * the colours. Saves from before have no {@code style} field and read as {@link SymbioteSuitStyle#CLASSIC}; the three-colour
 * constructor means Classic too. {@link #isClassic} is the whole default look (Classic colours on the Classic suit);
 * {@link #hasClassicColours} asks only about the colours.
 */
public record SymbioteColours(int primary, int secondary, int eyes, SymbioteSuitStyle style) {
	/** The default look: glossy blue-black, white emblem, white eyes. Same values as {@link SymbioteColourPreset#CLASSIC}. */
	public static final SymbioteColours CLASSIC = new SymbioteColours(0x14161F, 0xF2F4F8, 0xF4F6FA);

	public SymbioteColours {
		primary = rgb(primary);
		secondary = rgb(secondary);
		eyes = rgb(eyes);
		style = style == null ? SymbioteSuitStyle.CLASSIC : style;
	}

	/** Colours on the Classic suit. */
	public SymbioteColours(int primary, int secondary, int eyes) {
		this(primary, secondary, eyes, SymbioteSuitStyle.CLASSIC);
	}

	public static final Codec<SymbioteColours> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("primary", CLASSIC.primary).forGetter(SymbioteColours::primary),
			Codec.INT.optionalFieldOf("secondary", CLASSIC.secondary).forGetter(SymbioteColours::secondary),
			Codec.INT.optionalFieldOf("eyes", CLASSIC.eyes).forGetter(SymbioteColours::eyes),
			SymbioteSuitStyle.CODEC.optionalFieldOf("style", SymbioteSuitStyle.CLASSIC).forGetter(SymbioteColours::style))
			.apply(i, SymbioteColours::new));

	public static final StreamCodec<ByteBuf, SymbioteColours> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, SymbioteColours::primary,
			ByteBufCodecs.INT, SymbioteColours::secondary,
			ByteBufCodecs.INT, SymbioteColours::eyes,
			ByteBufCodecs.VAR_INT, c -> c.style().ordinal(),
			(p, s, e, st) -> new SymbioteColours(p, s, e, SymbioteSuitStyle.byOrdinal(st)));

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
		return new SymbioteColours(primary, secondary, eyes, style);
	}

	/** The whole default look: Classic colours on the Classic suit. */
	public boolean isClassic() {
		return equals(CLASSIC);
	}

	/** The Classic colours, on either suit style. */
	public boolean hasClassicColours() {
		return sameColours(CLASSIC);
	}

	/** The same three colours (the style is ignored). */
	public boolean sameColours(SymbioteColours other) {
		return other != null && primary == other.primary && secondary == other.secondary && eyes == other.eyes;
	}

	public SymbioteColours withStyle(SymbioteSuitStyle s) {
		return new SymbioteColours(primary, secondary, eyes, s);
	}

	public SymbioteColours withPrimary(int rgb) {
		return new SymbioteColours(rgb, secondary, eyes, style);
	}

	public SymbioteColours withSecondary(int rgb) {
		return new SymbioteColours(primary, rgb, eyes, style);
	}

	public SymbioteColours withEyes(int rgb) {
		return new SymbioteColours(primary, secondary, rgb, style);
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

	/** A cache key for the colours + style (equal keys only for equal values; Classic-style keys are the plain colour triple). */
	public String key() {
		String k = String.format(Locale.ROOT, "%06x_%06x_%06x", primary, secondary, eyes);
		return style == SymbioteSuitStyle.CLASSIC ? k : k + "_" + style.id();
	}
}
