package com.projecthero.mod.speedforce;

/**
 * v0.17.1 (user: "players should be able to change their lightning colour between 10 different choices"): the Speed Force
 * Speedster's lightning colours, picked in the Shift+N menu and saved in the state (so everyone sees your colour). Each is
 * four tones, outside in: the soft glow, the body, the bright edge and the white-hot core.
 */
public final class SpeedForceColours {
	public static final int COUNT = 10;

	private static final String[] IDS = { "gold", "red", "orange", "green", "light_blue", "dark_blue", "purple", "pink", "white", "black" };

	private static final int[][] TONES = {
			{ 0xFF7A10, 0xFFB02A, 0xFFE070, 0xFFFFFF }, // gold (the Flash)
			{ 0xB00808, 0xE8201A, 0xFF6A50, 0xFFE8E0 }, // red (Reverse-Flash)
			{ 0xE04A00, 0xFF7A1A, 0xFFB060, 0xFFF4E0 }, // orange
			{ 0x0A8A18, 0x2AD040, 0x90FF90, 0xF0FFF0 }, // green
			{ 0x1A90E0, 0x50C8FF, 0xB0EAFF, 0xFFFFFF }, // light blue (Kid Flash, the Flash in the show's first lightning)
			{ 0x0A1A90, 0x2040E0, 0x7090FF, 0xE8F0FF }, // dark blue (Zoom)
			{ 0x5A10A0, 0x9A3AE0, 0xD090FF, 0xFAF0FF }, // purple
			{ 0xC01870, 0xFF4AA8, 0xFFA0D8, 0xFFF0F8 }, // pink
			{ 0x9AA8C0, 0xD8E0F0, 0xF4F8FF, 0xFFFFFF }, // white (Godspeed)
			{ 0x101018, 0x2A2A3A, 0x6A0A10, 0xFF4040 }, // black (the Black Flash: dark bolts, a red core)
	};

	private SpeedForceColours() {
	}

	public static int clamp(int index) {
		return index < 0 || index >= COUNT ? 0 : index;
	}

	public static String id(int index) {
		return IDS[clamp(index)];
	}

	/** Tone 0 (glow) .. 3 (core), 0xRRGGBB. */
	public static int tone(int index, int tone) {
		return TONES[clamp(index)][Math.max(0, Math.min(3, tone))];
	}

	/** The colour a player chose (client-safe; gold for anyone else). */
	public static int of(net.minecraft.world.entity.player.Player p) {
		com.projecthero.mod.speedforce.data.SpeedForceState s = SpeedForceSpeedster.peek(p);
		return s == null ? 0 : clamp(s.colour);
	}
}
