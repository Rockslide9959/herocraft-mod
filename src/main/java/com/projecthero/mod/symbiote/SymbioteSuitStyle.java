package com.projecthero.mod.symbiote;

import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;

/**
 * v0.17.5 (user: "add another suit model and make it this skin ... make the mouth animated"): which suit model a Normal host wears.
 * Stored with the colours in {@link SymbioteColours#style} ({@code ModAttachments.SYMBIOTE_COLOURS}), so it saves, syncs and survives
 * death the same way; old saves have no {@code style} field and read as {@link #CLASSIC}.
 * <ul>
 *   <li>{@link #CLASSIC}: the mod's own v0.17.5 suit (original art, {@code geo/symbiote_host.geo.json}).</li>
 *   <li>{@link #LETHAL_PROTECTOR}: the user's PlanetMinecraft skin "Venom: Lethal Protector" on {@code geo/symbiote_host_lethal.geo.json},
 *   with a 3D animated jaw, teeth and tongue. The processed textures are not in git (borrowed skin, licence unchecked), so a build
 *   without them draws this style as Classic -- see {@code client/symbiote/SymbioteSuitTint#lethalAvailable}.</li>
 * </ul>
 * Both styles take the player's colours (the Lethal Protector through its own tint mask, made from the skin).
 */
public enum SymbioteSuitStyle {
	CLASSIC,
	LETHAL_PROTECTOR;

	/** Lenient: an unknown or missing id reads as {@link #CLASSIC}, so a bad save can never break the attachment. */
	public static final Codec<SymbioteSuitStyle> CODEC = Codec.STRING.xmap(s -> byId(s).orElse(CLASSIC), SymbioteSuitStyle::id);

	/** Lower-case id: the save value, the command argument and the lang key suffix. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String langKey() {
		return "projecthero.symbiote.style." + id();
	}

	/** Does this style take the player's colours? (Both do; kept as the one switch the screen and the tests ask.) */
	public boolean recolourable() {
		return true;
	}

	public static Optional<SymbioteSuitStyle> byId(String id) {
		if (id == null) {
			return Optional.empty();
		}
		for (SymbioteSuitStyle s : values()) {
			if (s.id().equalsIgnoreCase(id.trim())) {
				return Optional.of(s);
			}
		}
		return Optional.empty();
	}

	/** Out-of-range ordinals (a junk packet) read as {@link #CLASSIC}. */
	public static SymbioteSuitStyle byOrdinal(int ordinal) {
		SymbioteSuitStyle[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : CLASSIC;
	}
}
