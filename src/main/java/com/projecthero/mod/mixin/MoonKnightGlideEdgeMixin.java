package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.moonknight.ability.MoonKnightCape;

import net.minecraft.world.entity.player.Player;

/**
 * v0.14.4: a smooth Cape Glide near the floor. The glide is held with Sneak, and vanilla treats a sneaking player as
 * "staying on the ground surface": {@code Player#maybeBackOffFromEdge} then clips every move that would carry him off
 * an edge whenever he counts as above ground -- which, with the glide resetting his fall distance every tick and his
 * 1-block step height, was any time the floor was within a block below. On his own client that snagged the glide at
 * every dip and ledge; on the server (which replays his moves through the same check) it put him somewhere else than
 * his client did, and the correction read as lag / rubber-banding. A glide is not walking: no edge guard.
 */
@Mixin(Player.class)
public abstract class MoonKnightGlideEdgeMixin {
	@Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
	private void projecthero$glidingIsNotStayingOnTheGround(CallbackInfoReturnable<Boolean> cir) {
		if (MoonKnightCape.isGliding((Player) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
