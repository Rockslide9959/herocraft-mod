package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.beastboy.BeastBoy;
import com.projecthero.mod.beastboy.BeastForm;
import com.projecthero.mod.client.beastboy.BeastBodyRenderer;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;

/**
 * v0.17.3 Beast Boy: in an animal form he is drawn as the animal instead of the player model ({@link BeastBodyRenderer}); a
 * human-form quick morph flashes the animal for half a second (the art's partial animal on his body, or the whole fallback
 * animal in his place). In first person his hands are hidden while he is an animal.
 */
@Mixin(PlayerRenderer.class)
public abstract class BeastBoyPlayerRendererMixin {
	@Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$beastBody(AbstractClientPlayer player, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		if (!BeastBoy.hasPower(player)) {
			return;
		}
		BeastForm form = BeastBoy.form(player);
		if (form != BeastForm.HUMAN) {
			ci.cancel();
			if (!player.isInvisible()) {
				BeastBodyRenderer.render(form, player, partial, pose, buffers, light);
			}
			return;
		}
		BeastForm quick = BeastBoy.quickForm(player);
		if (quick != null && !player.isInvisible() && BeastBodyRenderer.renderQuick(quick, player, partial, pose, buffers, light)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
	private void projecthero$beastRightHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, CallbackInfo ci) {
		if (BeastBoy.morphed(player)) {
			ci.cancel();
		}
	}

	@Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
	private void projecthero$beastLeftHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, CallbackInfo ci) {
		if (BeastBoy.morphed(player)) {
			ci.cancel();
		}
	}
}
