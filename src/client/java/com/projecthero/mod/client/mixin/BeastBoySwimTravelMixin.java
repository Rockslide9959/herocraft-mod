package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.beastboy.BeastSwim;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4 Beast Boy: the local player's movement goes to {@link BeastSwim} while he swims as a sea animal (same hook and guard as
 * {@code DirectionalFlightTravelMixin}: {@code Player#travel}, local player only).
 */
@Mixin(Player.class)
public abstract class BeastBoySwimTravelMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void projecthero$beastSwim(Vec3 travelVector, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player && BeastSwim.travel(player)) {
			player.calculateEntityAnimation(false);
			ci.cancel();
		}
	}
}
