package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * v0.17.1: an Omnitrix alien's sense outlines (Ghostfreak's Spirit Sight), {@link AlienClientKit#glows}. Hooked on
 * {@code Minecraft#shouldEntityAppearGlowing}, which only this client's renderer asks -- the outlines are the wearer's alone.
 */
@Mixin(Minecraft.class)
public abstract class OmnitrixGlowMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void projecthero$alienSense(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		LocalPlayer me = ((Minecraft) (Object) this).player;
		if (me == null || entity == me) {
			return;
		}
		AlienClientKit kit = AlienClients.shownKit(me);
		if (kit != null && kit.glows(me, entity)) {
			cir.setReturnValue(true);
		}
	}
}
