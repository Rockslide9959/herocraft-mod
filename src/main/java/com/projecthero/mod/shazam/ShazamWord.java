package com.projecthero.mod.shazam;

import java.util.Locale;

import com.projecthero.mod.shazam.data.ShazamState;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.17.3 Shazam: the magic word. A chat message that is <b>just</b> the word -- any case, with optional punctuation, quotes or
 * spaces around it ("shazam", "SHAZAM!", "Shazam.", "\"Shazam!!\"") -- is the word. Anything more ("shazam now", "shazamm",
 * "sha zam") is ordinary chat. Saying it needs a voice ({@link #voice}).
 */
public final class ShazamWord {
	public static final String WORD = "shazam";

	private ShazamWord() {
	}

	/** True if {@code message} is just the word. */
	public static boolean isWord(String message) {
		if (message == null) {
			return false;
		}
		String s = message.strip();
		int a = 0;
		int b = s.length();
		while (a < b && trim(s.charAt(a))) {
			a++;
		}
		while (b > a && trim(s.charAt(b - 1))) {
			b--;
		}
		return s.substring(a, b).toLowerCase(Locale.ROOT).equals(WORD);
	}

	/** Punctuation / spacing allowed around the word. */
	private static boolean trim(char c) {
		return Character.isWhitespace(c) || "!?.,;:~*\"'`()[]-_¡¿…“”‘’".indexOf(c) >= 0;
	}

	/** Why the word can't be said right now. */
	public enum Voice {
		OK, UNDERWATER, POWDER_SNOW, MUFFLED
	}

	/**
	 * He needs his voice: not with his head underwater outside an air bubble (a bubble column), not muffled (Black Adam, later), not
	 * inside a powder snow block.
	 */
	public static Voice voice(ServerPlayer player) {
		ShazamState s = Shazam.peek(player);
		if (s != null && s.muffledUntil > player.level().getGameTime()) {
			return Voice.MUFFLED;
		}
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		var fluid = player.level().getFluidState(eye);
		boolean eyeInWater = player.isEyeInFluid(FluidTags.WATER)
				|| (fluid.is(FluidTags.WATER) && player.getEyeY() < eye.getY() + fluid.getHeight(player.level(), eye));
		if (eyeInWater && !player.level().getBlockState(eye).is(Blocks.BUBBLE_COLUMN)) {
			return Voice.UNDERWATER;
		}
		if (player.isInPowderSnow || player.level().getBlockState(eye).is(Blocks.POWDER_SNOW)
				|| player.level().getBlockState(player.blockPosition()).is(Blocks.POWDER_SNOW)) {
			return Voice.POWDER_SNOW;
		}
		return Voice.OK;
	}
}
