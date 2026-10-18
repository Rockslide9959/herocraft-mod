package com.projecthero.mod.mixin;

import com.projecthero.mod.quirk.erasure.ErasureMobs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.16.7 Erasure: whatever an erased mob launches or summons (arrows, fireballs, skulls, bullets, potions, fangs,
 * vexes, dragon's breath) is never added to the world ({@link ErasureMobs#blocksSpawn}).
 */
@Mixin(ServerLevel.class)
public abstract class ErasureServerLevelSpawnMixin {
	@Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
	private void projecthero$erasedLaunchesNothing(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (ErasureMobs.blocksSpawn(entity)) {
			cir.setReturnValue(false);
		}
	}
}
