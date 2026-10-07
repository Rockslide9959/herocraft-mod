package com.projecthero.mod.ironman;

import com.projecthero.mod.attachment.ModAttachments;

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
		// v0.14.21: the visor now swings (helmet_open / helmet_close clips) instead of popping -- every viewer gets the clock
		com.projecthero.mod.ironman.suit.IronManSuitFx.faceplateMoved(player);
		if (player.level() instanceof ServerLevel) {
			IronManSounds.play(player, open ? IronManSounds.FACEPLATE_OPEN : IronManSounds.FACEPLATE_SEAL, 0.6f, 1.0f);
		}
		player.displayClientMessage(Component.translatable(open
				? "message.projecthero.ironman.faceplate_open" : "message.projecthero.ironman.faceplate_closed"), true);
	}

	/**
	 * v0.15.3, explicit user request: an open faceplate snaps shut on its own the moment the wearer takes off, uses an
	 * Iron Man ability or takes damage -- the same swing clip and seal sound as pressing H. No-op when it is already
	 * closed, and never during a suit-up (a Suit Platform deploy holds the visor up on purpose until the suit is online).
	 * Returns true if it closed.
	 */
	public static boolean autoClose(ServerPlayer player) {
		if (!isOpen(player) || com.projecthero.mod.ironman.suit.IronManSuitUpManager.assembling(player)) {
			return false;
		}
		player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
		com.projecthero.mod.ironman.suit.IronManSuitFx.faceplateMoved(player);
		if (player.level() instanceof ServerLevel) {
			IronManSounds.play(player, IronManSounds.FACEPLATE_SEAL, 0.6f, 1.0f);
		}
		return true;
	}

	/**
	 * v0.15.11, explicit user request: a regular melee hit (left-click attack) on a living thing also snaps an open
	 * faceplate shut, exactly like taking off or using an ability. Wired to {@code AttackEntityCallback} in
	 * {@link #register}; public so the gametests can drive it (mock players never send the attack packet).
	 * Returns true if it closed.
	 */
	public static boolean onMeleeHit(ServerPlayer player, net.minecraft.world.entity.Entity target) {
		if (player.isSpectator() || !(target instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive()
				|| target instanceof net.minecraft.world.entity.decoration.ArmorStand
				|| !IronManArmor.wearingAnyIronMan(player)) {
			return false;
		}
		return autoClose(player);
	}

	/** v0.15.11: hooks {@link #onMeleeHit} into the server's attack event (never cancels the attack). */
	public static void register() {
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp) {
				onMeleeHit(sp, entity);
			}
			return net.minecraft.world.InteractionResult.PASS;
		});
	}

	/** Called each tick from {@link IronManSuitTicker}: a faceplate can't stay "open" once the armour is off. */
	public static void reconcile(ServerPlayer player) {
		if (isOpen(player) && !IronManArmor.wearingAnyIronMan(player)) {
			player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
		}
	}
}
