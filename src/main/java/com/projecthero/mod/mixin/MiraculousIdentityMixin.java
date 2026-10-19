package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.miraculous.MiraculousShowExtras;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (user): secret identity -- a transformed Miraculous holder is known only by their hero name (name tag, chat,
 * death messages, anything that asks {@code getDisplayName}). Reads only the synced Miraculous state, so every client
 * and the server agree. See {@link MiraculousShowExtras}.
 */
@Mixin(Player.class)
public abstract class MiraculousIdentityMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void projecthero$secretIdentity(CallbackInfoReturnable<Component> cir) {
		Component hero = MiraculousShowExtras.heroName((Player) (Object) this);
		if (hero != null) {
			cir.setReturnValue(hero);
		}
	}
}
