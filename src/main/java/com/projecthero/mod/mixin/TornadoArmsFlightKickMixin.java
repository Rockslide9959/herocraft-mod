package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.7: Tornado Arms blasted at the ground lifts the Speed Force Speedster and lets him hover as long as he holds it -- past
 * vanilla's 4 s floating limit, so on a server with allow-flight off he would be kicked for flying. While the arms spin the limit
 * is lifted (like {@code BifrostFlightKickMixin}); it applies again the moment he lets go.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class TornadoArmsFlightKickMixin {
	@Inject(method = "getMaximumFlyingTicks", at = @At("HEAD"), cancellable = true)
	private void projecthero$tornadoArmsNoKick(Entity entity, CallbackInfoReturnable<Integer> cir) {
		if (entity instanceof ServerPlayer p && com.projecthero.mod.speedforce.SpeedForceAbilities.tornadoArms(p)) {
			cir.setReturnValue(Integer.MAX_VALUE / 2);
		}
	}
}
