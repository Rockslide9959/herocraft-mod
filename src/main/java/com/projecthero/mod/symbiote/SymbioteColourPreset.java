package com.projecthero.mod.symbiote;

import java.util.Locale;
import java.util.Optional;

/**
 * v0.17.5: the ready-made Symbiote suit colourings on the colour screen (and {@code /symbiote colour <preset>}). Original colour
 * schemes named after the look they evoke -- only colours, no other characters' art. Keep the RGBs in step with
 * {@code scratchpad/symbiote/gen_symbiote_suit_v0175.js} (its preview sheet).
 */
public enum SymbioteColourPreset {
	CLASSIC(0x14161F, 0xF2F4F8, 0xF4F6FA),
	ANTI_VENOM(0xEEF0F2, 0x16171C, 0x101114),
	CARNAGE(0xB3121B, 0x1A0A0C, 0xF6F1EA),
	SCREAM(0xE6C21E, 0xC2241F, 0xF7F4E8),
	AGONY(0x5B2A86, 0x121016, 0xF2E9FF),
	RIOT(0x8E959E, 0x2F6FD6, 0xF0F4FA),
	TOXIN(0x1E4FB8, 0xC81E2A, 0xF4F6FA);

	private final SymbioteColours colours;

	SymbioteColourPreset(int primary, int secondary, int eyes) {
		this.colours = new SymbioteColours(primary, secondary, eyes);
	}

	public SymbioteColours colours() {
		return colours;
	}

	/** Lower-case id, used by the command and the lang keys ({@code projecthero.symbiote.colour.preset.<id>}). */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String langKey() {
		return "projecthero.symbiote.colour.preset." + id();
	}

	public static Optional<SymbioteColourPreset> byId(String id) {
		for (SymbioteColourPreset p : values()) {
			if (p.id().equalsIgnoreCase(id)) {
				return Optional.of(p);
			}
		}
		return Optional.empty();
	}

	/** The preset these colours match exactly, if any (the screen highlights it). */
	public static Optional<SymbioteColourPreset> matching(SymbioteColours colours) {
		for (SymbioteColourPreset p : values()) {
			if (p.colours.sameColours(colours)) { // v0.17.5: on either suit style
				return Optional.of(p);
			}
		}
		return Optional.empty();
	}
}
