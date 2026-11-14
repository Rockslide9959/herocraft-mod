package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.hulk.HulkMindAmbience;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;

/**
 * v0.16.4 (user): "creepy cave sounds playing all the time so that the player feels eerie and put off" -- inside the Hulk's
 * mind (the origin dimension, or the mind hall of a death rampage) the game's music is replaced by a sparse eerie mood track
 * ({@link HulkMindAmbience#MUSIC}; it replaces whatever was playing).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftHulkMindMusicMixin {
	@Inject(method = "getSituationalMusic", at = @At("HEAD"), cancellable = true)
	private void projecthero$hulkMindMusic(CallbackInfoReturnable<Music> cir) {
		if (HulkMindAmbience.active()) {
			cir.setReturnValue(HulkMindAmbience.MUSIC);
		} else if (com.projecthero.mod.client.omnitrix.alien.alienx.AlienXHall.showing()) {
			cir.setReturnValue(net.minecraft.sounds.Musics.END); // v0.17.2 round 4: the council's own music, as it was in the old dimension
		} else if (com.projecthero.mod.client.doctorfate.FateLibrary.showing()) {
			// v0.17.4 round 2: Nabu's library -- the deep dark's music, low and uneasy
			cir.setReturnValue(net.minecraft.sounds.Musics.createGameMusic(net.minecraft.sounds.SoundEvents.MUSIC_BIOME_DEEP_DARK));
		}
	}
}
