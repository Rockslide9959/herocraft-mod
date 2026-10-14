package com.projecthero.mod.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spider.item.SpiderManArmorItem;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * The Spider-Man costume's removable mask (v0.6.20) -- the direct parallel to
 * {@link com.projecthero.mod.ironman.IronManFaceplate} and {@link com.projecthero.mod.maxsteel.MaxSteelFaceplate}.
 * Pressing <b>H</b> while wearing the {@link SpiderManArmorItem} head piece pulls the mask off to show
 * the wearer's face; pressing it again pulls it back on.
 *
 * <p>Purely cosmetic and tied to the <em>costume</em>, not the Hero Class: anyone wearing the head
 * piece can do it. {@link ModAttachments#SPIDER_MAN_MASK_OPEN} is synced to everyone and not persisted.
 *
 * <p>v0.16.2 (user: "make it like how he does it in the movies"): the toggle is a one-second animation, not a pop.
 * {@link ModAttachments#SPIDER_MAN_MASK_OPEN} flips at once to the state being animated TOWARD, and
 * {@link ModAttachments#SPIDER_MAN_MASK_ANIM_START} records the tick it began (both synced to every viewer). Taking it
 * off: the right hand reaches up under the chin, peels the mask up over the top of the head -- the face uncovered from
 * the chin upward -- and brings it down in the hand. Putting it on: both hands lift the bunched mask above the head and
 * pull it down over it -- the face covered from the forehead down -- then a tug at the neck. {@link #coverage} is how
 * much of the head the mask covers at a given moment (shared by the renderer and the gametest). Presses during the
 * animation are ignored. Nothing about it blocks movement or the other keys.
 */
public final class SpiderMask {
	/** "No animation has run": far enough in the past that {@link #progress} is always 1. */
	public static final long NO_ANIM = -100_000L;
	/** Taking the mask off / putting it on, in ticks. */
	public static final int OFF_TICKS = 20;
	public static final int ON_TICKS = 20;
	/** Taking it off: the peel runs over this part of the animation (0..1). */
	public static final float OFF_PEEL_START = 0.30f;
	public static final float OFF_PEEL_END = 0.62f;
	/** Putting it on: the pull-down runs over this part. */
	public static final float ON_PULL_START = 0.28f;
	public static final float ON_PULL_END = 0.68f;

	private SpiderMask() {
	}

	/** The state the mask is in, or animating toward (true = off). */
	public static boolean isOpen(Player player) {
		return player.getAttachedOrElse(ModAttachments.SPIDER_MAN_MASK_OPEN, false);
	}

	/** Whether the given player is wearing the Spider-Man Suit head piece. */
	public static boolean wearingHood(Player player) {
		return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof SpiderManArmorItem;
	}

	public static long animStart(Player player) {
		return player.getAttachedOrElse(ModAttachments.SPIDER_MAN_MASK_ANIM_START, NO_ANIM);
	}

	public static int duration(boolean open) {
		return open ? OFF_TICKS : ON_TICKS;
	}

	/** How far through the current mask animation (0..1; 1 = none running) at {@code time} (game ticks + partial). */
	public static float progress(Player player, double time) {
		return progress(isOpen(player), animStart(player), time);
	}

	public static float progress(boolean open, long start, double time) {
		return (float) Mth.clamp((time - start) / duration(open), 0.0, 1.0);
	}

	/** Is a take-off / put-on animation playing? */
	public static boolean animating(Player player) {
		return progress(player, player.level().getGameTime()) < 1f;
	}

	/** How much of the head the mask covers (1 = all of it, 0 = off), from the crown down. */
	public static float coverage(Player player, double time) {
		return coverage(isOpen(player), progress(player, time));
	}

	/** {@link #coverage} for a direction ({@code open} = taking it off) and animation progress. */
	public static float coverage(boolean open, float p) {
		if (open) {
			return 1f - smooth((p - OFF_PEEL_START) / (OFF_PEEL_END - OFF_PEEL_START));
		}
		return smooth((p - ON_PULL_START) / (ON_PULL_END - ON_PULL_START));
	}

	public static float smooth(float f) {
		f = Mth.clamp(f, 0f, 1f);
		return f * f * (3f - 2f * f);
	}

	/** H key. Toggles the mask while the Spider-Man costume head piece is worn (ignored while it is already moving). */
	public static void toggle(ServerPlayer player) {
		if (!wearingHood(player) || animating(player)) {
			return;
		}
		boolean open = !isOpen(player);
		player.setAttached(ModAttachments.SPIDER_MAN_MASK_OPEN, open);
		player.setAttached(ModAttachments.SPIDER_MAN_MASK_ANIM_START, player.level().getGameTime());
		player.displayClientMessage(Component.translatable(open
				? "message.projecthero.spider_man.mask_off" : "message.projecthero.spider_man.mask_on"), true);
	}

	/**
	 * Every tick, for every player: the cloth sound as the mask leaves / meets the head, and the mask can't stay off once
	 * the head piece comes off -- called from lifecycle cleanup.
	 */
	public static void reconcile(ServerPlayer player) {
		if (!wearingHood(player)) {
			if (isOpen(player)) {
				player.setAttached(ModAttachments.SPIDER_MAN_MASK_OPEN, false);
			}
			if (animStart(player) != NO_ANIM) {
				player.setAttached(ModAttachments.SPIDER_MAN_MASK_ANIM_START, NO_ANIM);
			}
			return;
		}
		long start = animStart(player);
		if (start == NO_ANIM) {
			return;
		}
		boolean open = isOpen(player);
		long t = player.level().getGameTime() - start;
		long cue = Math.round(duration(open) * (open ? OFF_PEEL_START : ON_PULL_START));
		if (t == cue && player.level() instanceof ServerLevel level) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
					SoundSource.PLAYERS, 0.7f, open ? 1.25f : 1.0f);
		}
	}
}
