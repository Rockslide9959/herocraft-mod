package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.symbiote.monster.SymbioteMonsterAnimatable;
import com.projecthero.mod.client.symbiote.monster.SymbioteMonsterRenderer;
import com.projecthero.mod.symbiote.monster.SymbioteMonster;
import com.projecthero.mod.symbiote.monster.SymbioteMonsterState;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;

/**
 * v0.17.5 Symbiote Monster form: a transformed host is drawn as the monster ({@link SymbioteMonsterRenderer}) instead of the player
 * + suit; inside the closed cocoon the host is not drawn at all; in the melt the suited host rises back up out of the goo pool
 * (lowered through the ground by {@link #projecthero$riseFromGoo}) while the monster melts away. Invisible players stay invisible.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererSymbioteMonsterMixin {
	/** The host is hidden from this point of the cocoon on (the shell has closed over them). */
	private static final int HIDDEN_FROM = 26;
	/** In the melt the host is drawn rising from this tick. */
	private static final int RISE_FROM = 12;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void projecthero$buildSymbioteMonsterRenderer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
		SymbioteMonsterRenderer.rebuild(context);
	}

	@Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$renderSymbioteMonster(AbstractClientPlayer player, float entityYaw, float partialTicks, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
		SymbioteMonsterState s = SymbioteMonster.state(player);
		if (!s.inForm()) {
			return;
		}
		long age = player.level().getGameTime() - s.phaseStart();
		if (SymbioteMonsterAnimatable.drawn(player)) {
			SymbioteMonsterRenderer renderer = SymbioteMonsterRenderer.get();
			if (renderer != null && !player.isInvisible()) {
				renderer.renderBody(player, entityYaw, partialTicks, poseStack, buffer, packedLight);
			}
			if (!(s.phase() == SymbioteMonsterState.MELT && age >= RISE_FROM)) {
				ci.cancel();
			}
			return;
		}
		if (s.phase() == SymbioteMonsterState.COCOON && age >= HIDDEN_FROM) {
			ci.cancel();
		}
	}

	/** The melt: the suited host comes back up out of the pool (drawn lower, through the ground, and rising). */
	@Inject(method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("TAIL"))
	private void projecthero$riseFromGoo(AbstractClientPlayer player, PoseStack poseStack, float partialTick, CallbackInfo ci) {
		SymbioteMonsterState s = SymbioteMonster.state(player);
		if (s.phase() != SymbioteMonsterState.MELT) {
			return;
		}
		float age = player.level().getGameTime() - s.phaseStart() + partialTick;
		float t = Mth.clamp((age - RISE_FROM) / (SymbioteMonster.MELT_TICKS - RISE_FROM - 2f), 0f, 1f);
		float rise = t * t * (3f - 2f * t);
		float drop = (1f - rise) * 2.8f; // world blocks below the ground
		// here the stack is flipped (y down) and scaled by the entity's scale and the player's 0.9375
		float units = Math.max(0.01f, player.getScale() * 0.9375f);
		poseStack.translate(0f, drop / units, 0f);
	}
}
