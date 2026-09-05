package com.herocraft.mod.ironman;

import com.herocraft.mod.attachment.ModAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * "changes 19": the openable helmet faceplate. Pressing <b>H</b> while wearing any Iron Man armour
 * retracts the helmet to reveal the pilot's face; pressing it again closes it. Purely cosmetic --
 * {@code IRON_MAN_FACEPLATE_OPEN} is a synced-to-everyone, non-persistent boolean attachment, read on
 * the client by {@code SuperheroArmorRenderer.setHelmetHidden} (which hides the GeckoLib helmet's
 * {@code helmet} shell <em>and</em> {@code faceplate} visor bones) and by {@code PlayerModelMixin}
 * (which stops suppressing the wearer's skin overlay while the head is bare).
 *
 * <p>"changes 20": hiding the visor bone alone used to leave the solid helmet boxes covering the
 * face, so the toggle appeared to do nothing -- see {@code SuperheroArmorRenderer.setHelmetHidden}.
 */
public final class IronManFaceplate {
	private IronManFaceplate() {
	}

	public static boolean isOpen(Player player) {
		return player.getAttachedOrElse(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
	}

	/** H key. Toggles the visor while any Iron Man armour is worn. */
	public static void toggle(ServerPlayer player) {
		if (!IronManArmor.wearingAnyIronMan(player)) {
			return;
		}
		boolean open = !isOpen(player);
		player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, open);
		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
					SoundSource.PLAYERS, 0.5f, 1.5f);
		}
		player.displayClientMessage(Component.translatable(open
				? "message.herocraft.ironman.faceplate_open" : "message.herocraft.ironman.faceplate_closed"), true);
	}

	/** Called each tick from {@link IronManSuitTicker}: a faceplate can't stay "open" once the armour is off. */
	public static void reconcile(ServerPlayer player) {
		if (isOpen(player) && !IronManArmor.wearingAnyIronMan(player)) {
			player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
		}
	}
}
