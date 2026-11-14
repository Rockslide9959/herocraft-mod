package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.projecthero.mod.entity.GiantTracking;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.4: the world pass skips an entity unless the chunk section at its block position has been compiled (or it is
 * outside the build height). A {@link GiantTracking.AlwaysTracked} giant usually stands in a section the client never
 * builds (outside the render distance), so the entity loop's one {@code entity.blockPosition()} call hands back a
 * position below the world for it: the "outside the build height" branch lets it through. That position is only used
 * for this check; the giant is drawn where it really is.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererGiantMixin {
	@Shadow
	private ClientLevel level;

	@WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;blockPosition()Lnet/minecraft/core/BlockPos;"))
	private BlockPos projecthero$giantSection(Entity entity, Operation<BlockPos> original) {
		BlockPos pos = original.call(entity);
		if (entity instanceof GiantTracking.AlwaysTracked && level != null) {
			return new BlockPos(pos.getX(), level.getMinBuildHeight() - 1, pos.getZ());
		}
		return pos;
	}
}
