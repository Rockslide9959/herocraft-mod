package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.16.6: Rena Rouge's illusion walls (Fake Wall, the Mirage) are not solid, but no mob can see through one --
 * {@code hasLineOfSight} (target searches, melee / ranged attack goals, sensing) answers false across a live wall
 * ({@link com.projecthero.mod.miraculous.renarouge.entity.FakeWallEntity#blocksSight}; free while no wall stands).
 */
@Mixin(LivingEntity.class)
public abstract class RenaRougeFakeWallSightMixin {
	@Inject(method = "hasLineOfSight", at = @At("HEAD"), cancellable = true)
	private void projecthero$renaRougeFakeWall(Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (com.projecthero.mod.miraculous.renarouge.entity.FakeWallEntity.blocksSight((LivingEntity) (Object) this, target)) {
			cir.setReturnValue(false);
		}
	}
}
