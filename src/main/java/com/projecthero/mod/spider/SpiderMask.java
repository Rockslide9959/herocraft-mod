package com.projecthero.mod.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spider.item.SpiderManArmorItem;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 */
public final class SpiderMask {
	private SpiderMask() {
	}

	public static boolean isOpen(Player player) {
		return player.getAttachedOrElse(ModAttachments.SPIDER_MAN_MASK_OPEN, false);
	}

	/** Whether the given player is wearing the Spider-Man Suit head piece. */
	public static boolean wearingHood(Player player) {
		return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof SpiderManArmorItem;
	}

	/** H key. Toggles the mask while the Spider-Man costume head piece is worn. */
	public static void toggle(ServerPlayer player) {
		if (!wearingHood(player)) {
			return;
		}
		boolean open = !isOpen(player);
		player.setAttached(ModAttachments.SPIDER_MAN_MASK_OPEN, open);
		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.SPIDER_STEP, SoundSource.PLAYERS, 0.4f, open ? 1.5f : 1.0f);
		}
		player.displayClientMessage(Component.translatable(open
				? "message.projecthero.spider_man.mask_off" : "message.projecthero.spider_man.mask_on"), true);
	}

	/** The mask can't stay off once the head piece comes off -- called from lifecycle cleanup. */
	public static void reconcile(ServerPlayer player) {
		if (isOpen(player) && !wearingHood(player)) {
			player.setAttached(ModAttachments.SPIDER_MAN_MASK_OPEN, false);
		}
	}
}
