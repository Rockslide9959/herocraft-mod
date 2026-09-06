package com.projecthero.mod.maxsteel;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The retractable Max Steel helmet -- the direct parallel to {@link com.projecthero.mod.ironman.IronManFaceplate}.
 * Pressing <b>H</b> while transformed retracts the helmet crown / faceplate / chin guard to reveal the
 * pilot's face; pressing it again seals it. Purely cosmetic -- {@link ModAttachments#MAX_STEEL_FACEPLATE_OPEN}
 * is a synced-to-everyone, non-persistent boolean, read on the client by
 * {@code SuperheroArmorRenderer.setHelmetHidden} and {@code PlayerModelMixin}.
 */
public final class MaxSteelFaceplate {
	private MaxSteelFaceplate() {
	}

	public static boolean isOpen(Player player) {
		return player.getAttachedOrElse(ModAttachments.MAX_STEEL_FACEPLATE_OPEN, false);
	}

	/** H key. Toggles the helmet while the Max Steel suit is on. */
	public static void toggle(ServerPlayer player) {
		if (!MaxSteel.isTransformed(player)) {
			return;
		}
		boolean open = !isOpen(player);
		player.setAttached(ModAttachments.MAX_STEEL_FACEPLATE_OPEN, open);
		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
					SoundSource.PLAYERS, 0.5f, 1.6f);
		}
		player.displayClientMessage(Component.translatable(open
				? "message.projecthero.max_steel.helmet_open" : "message.projecthero.max_steel.helmet_closed"), true);
	}

	/** A helmet can't stay "open" once the suit is off -- called from lifecycle cleanup and suit-down. */
	public static void reconcile(ServerPlayer player) {
		if (isOpen(player) && !MaxSteel.isTransformed(player)) {
			player.setAttached(ModAttachments.MAX_STEEL_FACEPLATE_OPEN, false);
		}
	}
}
