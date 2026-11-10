package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.undead.UndeadFlight;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.3: Undead flying level on his blood jets for more than 4 s would trip vanilla's "Flying is not enabled on this server"
 * kick on a server with allow-flight off (singleplayer allows it). While {@link UndeadFlight#isFlying} the floating limit is
 * lifted; it applies again the moment he lands or lets go.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class UndeadFlightKickMixin {
	@Inject(method = "getMaximumFlyingTicks", at = @At("HEAD"), cancellable = true)
	private void projecthero$undeadFlightNoKick(Entity entity, CallbackInfoReturnable<Integer> cir) {
		if (entity instanceof ServerPlayer p && UndeadFlight.isFlying(p)) {
			cir.setReturnValue(Integer.MAX_VALUE / 2);
		}
	}
}
