package com.projecthero.mod.mixin;

import com.projecthero.mod.miraculous.viperion.SecondChance;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * v0.16.7 (user): Viperion's Second Chance rewinds the whole world -- whatever is ADDED to it while a checkpoint is live
 * (a natural spawn, a mob's drops, an XP orb, an arrow) is remembered, so the rewind can take it away again. Entities
 * loaded from disk never come through here. One empty-map check unless a checkpoint is live.
 */
@Mixin(ServerLevel.class)
public abstract class SecondChanceSpawnMixin {
	@Inject(method = "addFreshEntity", at = @At("RETURN"))
	private void projecthero$secondChanceNew(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			SecondChance.onFreshEntity(entity);
		}
	}
}
