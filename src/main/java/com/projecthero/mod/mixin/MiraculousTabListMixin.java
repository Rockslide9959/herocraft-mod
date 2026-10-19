package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.miraculous.MiraculousShowExtras;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** v0.16.7 (user): secret identity -- the tab list shows a transformed holder's hero name (re-sent on every change). */
@Mixin(ServerPlayer.class)
public abstract class MiraculousTabListMixin {
	@Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
	private void projecthero$secretIdentityTab(CallbackInfoReturnable<Component> cir) {
		Component hero = MiraculousShowExtras.heroName((ServerPlayer) (Object) this);
		if (hero != null) {
			cir.setReturnValue(hero);
		}
	}
}
