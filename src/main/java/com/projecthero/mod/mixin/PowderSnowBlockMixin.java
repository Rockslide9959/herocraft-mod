package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.hero.power.p09.CryokinesisHandlers;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.PowderSnowBlock;

/**
 * A cryokinetic walks on top of powder snow with no leather boots (v0.10.9). Vanilla's own
 * {@code getCollisionShape} already only gives the solid top face when the entity is <em>not</em>
 * descending, so sneaking still sinks them straight in.
 */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockMixin {
	@Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
	private static void projecthero$cryoWalk(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player player && CryokinesisHandlers.active(player)) {
			cir.setReturnValue(true);
		}
	}
}
