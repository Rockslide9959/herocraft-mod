package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.projecthero.mod.beastboy.BeastBoyLearning;
import com.projecthero.mod.client.beastboy.BeastGreen;

import net.minecraft.client.renderer.entity.OcelotRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Ocelot;

/** v0.17.3 Beast Boy: the green (Sakutia) ocelot is drawn with a green copy of the ocelot's own texture. */
@Mixin(OcelotRenderer.class)
public abstract class BeastBoyOcelotMixin {
	@Inject(method = "getTextureLocation(Lnet/minecraft/world/entity/animal/Ocelot;)Lnet/minecraft/resources/ResourceLocation;",
			at = @At("RETURN"), cancellable = true)
	private void projecthero$greenOcelot(Ocelot ocelot, CallbackInfoReturnable<ResourceLocation> cir) {
		if (BeastBoyLearning.isGreenOcelot(ocelot)) {
			cir.setReturnValue(BeastGreen.of(cir.getReturnValue()));
		}
	}
}
