package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.hero.power.p09.CryokinesisHandlers;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A cryokinetic moves through powder snow as if it were air (v0.10.10).
 *
 * <p>{@link PowderSnowBlockMixin} already lets them <em>walk on top of</em> a powder-snow surface, but
 * the moment they deliberately drop into a drift -- or walk into the side of one, where there is no top
 * face to stand on -- vanilla clamps them to a crawl: {@code PowderSnowBlock.entityInside} rewrites
 * their velocity through {@code Entity.makeStuckInBlock} with a 0.9 / 1.5 / 0.9 multiplier. Cancelling
 * that one call is all it takes; everything else powder snow does (the freeze counter, extinguishing
 * fire, the "in powder snow" flag) is untouched, and the freeze itself is already handled by the
 * power's own resistance passive.
 *
 * <p>Deliberately narrow, exactly like {@code EntityWebMixin}: powder snow only, and only for a player
 * whose active power is Cryokinesis. Every other entity -- including other players -- is slowed by a
 * drift exactly as vanilla intends.
 */
@Mixin(Entity.class)
public abstract class PowderSnowSlowMixin {
	@Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
	private void projecthero$cryoIgnoresPowderSnow(BlockState state, Vec3 motionMultiplier, CallbackInfo ci) {
		if (!state.is(Blocks.POWDER_SNOW)) {
			return;
		}
		if ((Entity) (Object) this instanceof Player player && CryokinesisHandlers.active(player)) {
			ci.cancel();
		}
	}
}
