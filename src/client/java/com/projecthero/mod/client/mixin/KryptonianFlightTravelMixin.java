package com.projecthero.mod.client.mixin;

import com.projecthero.mod.client.kryptonian.KryptonianFlightClient;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v0.14.8: hands the local player's movement to {@link KryptonianFlightClient} while the Kryptonian flies (directional
 * flight). Same hook as {@link GreenLanternFlightTravelMixin}: {@code Player#travel} itself, guarded to the local player.
 */
@Mixin(Player.class)
public abstract class KryptonianFlightTravelMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void projecthero$kryptonianFlight(Vec3 travelVector, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player && KryptonianFlightClient.travel(player)) {
			player.calculateEntityAnimation(false);
			ci.cancel();
		}
	}
}
