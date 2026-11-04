package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * v0.17.2 (Way Big, 100 blocks tall): vanilla puts the third-person camera 4 blocks from the eyes (times the scale attribute) --
 * for a giant that is a close-up of his ear. A kit's {@link AlienClientKit#cameraDistance} pulls it back far enough to see him.
 */
@Mixin(Camera.class)
public abstract class OmnitrixCameraMixin {
	@ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"), index = 0)
	private float projecthero$alienCameraDistance(float distance) {
		if (Minecraft.getInstance().getCameraEntity() instanceof AbstractClientPlayer p) {
			AlienClientKit kit = AlienClients.shownKit(p);
			if (kit != null) {
				float d = kit.cameraDistance(p);
				if (d > 0f) {
					return d;
				}
			}
		}
		return distance;
	}
}
