package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.andy.AndyFlight;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.3: Andy flying level on his blood jets for more than 4 s would trip vanilla's "Flying is not enabled on this server"
 * kick on a server with allow-flight off (singleplayer allows it). While {@link AndyFlight#isFlying} the floating limit is
 * lifted; it applies again the moment he lands or lets go.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class AndyFlightKickMixin {
	@Inject(method = "getMaximumFlyingTicks", at = @At("HEAD"), cancellable = true)
	private void projecthero$andyFlightNoKick(Entity entity, CallbackInfoReturnable<Integer> cir) {
		if (entity instanceof ServerPlayer p && AndyFlight.isFlying(p)) {
			cir.setReturnValue(Integer.MAX_VALUE / 2);
		}
	}
}
