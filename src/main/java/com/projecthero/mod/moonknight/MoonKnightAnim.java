package com.projecthero.mod.moonknight;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.moonknight.data.MoonKnightAction;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Moon Knight's move poses: the server stamps an id + start time into the synced {@link MoonKnightAction}; the
 * client's {@code MoonKnightPose} turns it into keyframed arm / body angles (and the GeckoLib suit copies the vanilla
 * bones, so it follows). Every ability plays one of these.
 */
public final class MoonKnightAnim {
	public static final int NONE = 0;
	public static final int TRANSFORM = 1;
	public static final int DART_THROW = 2;
	public static final int DART_CHARGE = 3;
	public static final int DART_FAN = 4;
	public static final int MOON_MARK = 5;
	public static final int GRAPPLE_FIRE = 6;
	public static final int DIVE_KICK = 7;
	public static final int YANK = 8;
	public static final int TRUNCHEON_DRAW = 9;
	public static final int STAFF_SPIN = 10;
	public static final int GROUND_SLAM = 11;
	public static final int DIVE_SLAM = 12;
	public static final int SHROUD = 13;
	public static final int SHADOW_STEP = 14;
	public static final int ALTER_SWAP = 15;
	public static final int FIST_OF_KHONSHU = 16;
	public static final int SCHOLARS_SIGHT = 17;
	public static final int VANISH = 18;
	public static final int MOONBEAM = 19;
	public static final int EYE_CHARGE = 20;
	public static final int EYE_RELEASE = 21;
	public static final int JUDGEMENT = 22;
	public static final int RESURRECT = 23;
	public static final int UNTRANSFORM = 24;
	public static final int TRUNCHEON_SLAM = 25;
	/**
	 * v0.14.4 truncheon: the combo's forehand (hit 1) and backhand (hit 2) -- hit 3 is {@link #TRUNCHEON_SLAM}, the
	 * overhead smash -- and putting the truncheon away. Numbered well clear of the other moves' ids.
	 */
	public static final int TRUNCHEON_HIT_1 = 60;
	public static final int TRUNCHEON_HIT_2 = 61;
	public static final int TRUNCHEON_STOW = 62;
	/** v0.13.21: X Dash, and a glide kick landing. */
	public static final int DASH = 26;
	public static final int GLIDE_KICK = 27;

	private MoonKnightAnim() {
	}

	public static MoonKnightAction action(Player player) {
		return player.getAttachedOrElse(ModAttachments.MOON_KNIGHT_ACTION, new MoonKnightAction());
	}

	public static void save(ServerPlayer player, MoonKnightAction a) {
		player.setAttached(ModAttachments.MOON_KNIGHT_ACTION, a);
	}

	/** Play {@code animId} from now. */
	public static void play(ServerPlayer player, int animId) {
		MoonKnightAction c = action(player).copy();
		c.animId = animId;
		c.animStart = player.level().getGameTime();
		save(player, c);
	}

	/** Stop {@code animId} if it is the one playing (a newer move is never cut off). */
	public static void stop(ServerPlayer player, int animId) {
		MoonKnightAction a = action(player);
		if (a.animId == animId) {
			MoonKnightAction c = a.copy();
			c.animId = NONE;
			save(player, c);
		}
	}

	public static boolean flag(Player player, int flag) {
		return action(player).has(flag);
	}

	/**
	 * v0.13.21: the alter just changed from {@code fromAlter} while suited -- every client rematerialises the new
	 * alter's suit over the old one, pixel by pixel, from now ({@code MoonKnightConfig.ALTER_SWAP_TICKS}).
	 */
	public static void markSwap(ServerPlayer player, int fromAlter) {
		MoonKnightAction c = action(player).copy();
		c.swapFrom = fromAlter;
		c.swapStart = player.level().getGameTime();
		save(player, c);
	}

	public static void setFlag(ServerPlayer player, int flag, boolean on) {
		MoonKnightAction a = action(player);
		if (a.has(flag) != on) {
			save(player, a.with(flag, on));
		}
	}
}
