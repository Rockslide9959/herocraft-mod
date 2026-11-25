package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.thoracquire.CraterGuard;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

/** v0.17.7: explosions leave a Mjolnir's Crater alone while its hammer is still in it ({@link CraterGuard}). */
@Mixin(Explosion.class)
public abstract class ThorAcquireExplosionMixin {
	@Shadow
	@Final
	private Level level;

	@Inject(method = "finalizeExplosion", at = @At("HEAD"))
	private void projecthero$guardCrater(boolean spawnParticles, CallbackInfo ci) {
		if (level instanceof ServerLevel server && CraterGuard.anyActive(server)) {
			((Explosion) (Object) this).getToBlow().removeIf(pos -> CraterGuard.isProtected(server, pos));
		}
	}
}
