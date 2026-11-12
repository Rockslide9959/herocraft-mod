package com.projecthero.mod.client.mixin;

import net.minecraft.world.entity.LightningBolt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * v0.17.3 Shazam: his lightning bolts (named {@code Shazam.BOLT_NAME}) are real bolts for the look, but vanilla plays a bolt's
 * thunder at volume 10000 (heard across the world). For his, the thunder and the impact are turned right down so a fight full of
 * lightning stays quiet. Every other bolt is untouched.
 */
@Mixin(LightningBolt.class)
public abstract class ShazamBoltSoundMixin {
	@ModifyArg(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/Level;playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"),
			index = 5, require = 0)
	private float projecthero$quietShazamThunder(float volume) {
		LightningBolt self = (LightningBolt) (Object) this;
		if (com.projecthero.mod.shazam.Shazam.isOurBolt(self)) {
			return volume > 100f ? 1.2f : volume * 0.25f; // the thunder: heard nearby only; the impact: soft
		}
		return volume;
	}
}
