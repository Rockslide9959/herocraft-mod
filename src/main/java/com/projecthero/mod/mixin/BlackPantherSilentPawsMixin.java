package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4 Black Panther's Silent Paws: in the suit, the vibranium soles soak up his own vibrations -- every game event he is
 * the source of (steps, landing, hitting things...) is never emitted, so sculk sensors, shriekers and the Warden never hear him.
 * The same hook as the Mark 5's stealth ({@link IronManStealthVibrationMixin}).
 */
@Mixin(ServerLevel.class)
public abstract class BlackPantherSilentPawsMixin {
	@Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$silentPaws(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
		if (context.sourceEntity() instanceof Player player && com.projecthero.mod.blackpanther.BlackPanther.suited(player)) {
			ci.cancel();
		}
	}
}
