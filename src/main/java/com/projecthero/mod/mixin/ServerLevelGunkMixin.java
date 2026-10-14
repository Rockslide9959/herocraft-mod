package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * v0.16.2 (user request): a creature caught in Spider-Man's Web Bomb cannot fire projectiles while it is webbed up --
 * every arrow, bolt, fireball, trident, potion or bullet it would launch simply never spawns
 * ({@link com.projecthero.mod.spider.SpiderWebs#gunked}). Mobs and players alike.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelGunkMixin {
	@Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
	private void projecthero$webbedShooterCannotFire(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Projectile projectile && projectile.getOwner() != null
				&& com.projecthero.mod.spider.SpiderWebs.gunked(projectile.getOwner())) {
			cir.setReturnValue(false);
		}
	}
}
