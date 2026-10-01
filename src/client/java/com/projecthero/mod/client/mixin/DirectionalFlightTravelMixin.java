package com.projecthero.mod.client.mixin;

import com.projecthero.mod.client.flight.DirectionalFlight;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * v0.14.16: hands the local player's movement to {@link DirectionalFlight} while any mod flight is engaged (replaces the
 * v0.13.21 Green Lantern and v0.14.8 Kryptonian travel mixins). Same hook as before: {@code Player#travel} itself rather
 * than {@code LivingEntity#travel}, because Player's creative-flight branch overwrites the vertical velocity with its own
 * damped value after the move, which would fight the flight model. Lives in the client config and is guarded to the
 * local player, so only the machine that owns the keyboard ever replaces its own movement.
 */
@Mixin(Player.class)
public abstract class DirectionalFlightTravelMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void projecthero$directionalFlight(Vec3 travelVector, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player && DirectionalFlight.travel(player)) {
			// vanilla's travel ends by advancing the limb-swing animation; keep that
			player.calculateEntityAnimation(false);
			ci.cancel();
		}
	}
}
