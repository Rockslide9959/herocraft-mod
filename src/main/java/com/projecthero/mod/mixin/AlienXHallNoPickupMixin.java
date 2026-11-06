package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.omnitrix.alien.alienx.AlienXKit;

import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/** v0.17.2 round 4: the player in Alien X's hall stands in the world, but he is not there to collect what falls round his body. */
@Mixin({ ItemEntity.class, ExperienceOrb.class })
public abstract class AlienXHallNoPickupMixin {
	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void projecthero$noPickupInHall(Player player, CallbackInfo ci) {
		if (AlienXKit.inHall(player)) {
			ci.cancel();
		}
	}
}
