package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.quirk.overhaul.OverhaulRestore;

import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

/**
 * v0.16.7 (Overhaul revamp): before an explosion breaks its blocks, Overhaul's Restore ledger notes what they were
 * ({@link OverhaulRestore}; it does nothing unless somebody in that level holds Overhaul).
 */
@Mixin(Explosion.class)
public abstract class OverhaulExplosionMixin {
	@Shadow
	@Final
	private Level level;

	@Inject(method = "finalizeExplosion", at = @At("HEAD"))
	private void projecthero$overhaulLedger(boolean spawnParticles, CallbackInfo ci) {
		Explosion self = (Explosion) (Object) this;
		if (!level.isClientSide() && self.interactsWithBlocks()) {
			OverhaulRestore.recordExplosion(level, self.getToBlow());
		}
	}
}
