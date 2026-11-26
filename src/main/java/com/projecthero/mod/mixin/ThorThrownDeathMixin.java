package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * v0.17.7: a Thor weapon out on a scripted throw ({@link com.projecthero.mod.power.ThorThrown}) is back in his hands before
 * anything about the death runs -- drops, keepInventory, gravestone mods -- so a death mid-throw treats it exactly like a held
 * weapon and it can never vanish with the old player.
 */
@Mixin(ServerPlayer.class)
public abstract class ThorThrownDeathMixin {
	@Inject(method = "die", at = @At("HEAD"))
	private void projecthero$returnThrownThorWeapons(DamageSource source, CallbackInfo ci) {
		com.projecthero.mod.power.ThorThrown.returnAll((ServerPlayer) (Object) this);
	}
}
