package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadFlight;
import com.projecthero.mod.undead.data.UndeadState;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: Undead's flight moves the local player itself (movement is the client's to make): every tick exactly
 * {@link UndeadFlight#speed} blocks along the look (25 / 40 / 32 b/s), no input acceleration, no gravity, no drag -- so the speed
 * is the real one. The server sets the same velocity and lifts the floating kick ({@code UndeadFlightKickMixin}). On letting go
 * the last velocity carries on and decays the vanilla way.
 */
@Mixin(Player.class)
public abstract class UndeadFlightTravelMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void projecthero$undeadFlight(Vec3 travelVector, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player && UndeadFlight.isFlying(player) && !player.isPassenger()) {
			UndeadState s = Undead.peek(player);
			Vec3 v = player.getLookAngle().scale(UndeadFlight.speed(s, player.level().getGameTime()));
			player.setDeltaMovement(v);
			player.move(MoverType.SELF, v);
			player.resetFallDistance();
			player.calculateEntityAnimation(false);
			ci.cancel();
		}
	}
}
