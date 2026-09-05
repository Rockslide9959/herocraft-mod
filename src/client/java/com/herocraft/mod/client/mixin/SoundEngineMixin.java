package com.herocraft.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.herocraft.mod.punisher.Punisher;
import com.herocraft.mod.punisher.PunisherConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;

/**
 * Punisher Adrenaline (v0.9.4): while it is running, the player's game audio is dulled by
 * {@link PunisherConfig#ADRENALINE_AUDIO_MUFFLE} — a tunnel-vision "in the zone" feel. Purely local
 * (reads the synced {@code PunisherState}); no other player is affected.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
	@Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
			at = @At("RETURN"), cancellable = true)
	private void herocraft$adrenalineMuffle(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && Punisher.adrenalineActive(player)) {
			cir.setReturnValue(cir.getReturnValueF() * (1.0f - PunisherConfig.ADRENALINE_AUDIO_MUFFLE));
		}
	}
}
