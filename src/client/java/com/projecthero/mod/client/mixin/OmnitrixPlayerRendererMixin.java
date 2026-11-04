package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.OmnitrixFirstPerson;
import com.projecthero.mod.client.omnitrix.OmnitrixWatch;
import com.projecthero.mod.omnitrix.Omnitrix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: an Omnitrix alien is drawn as the player body wearing the alien's skin -- in third person and on the first-person
 * arm -- always on the WIDE arm model (a slim player's renderer hands the alien to the wide one). The human wearer's watch is
 * drawn on the first-person left arm too.
 */
@Mixin(PlayerRenderer.class)
public abstract class OmnitrixPlayerRendererMixin {
	@Unique
	private static PlayerRenderer projecthero$renderer(PlayerSkin.Model model) {
		EntityRenderer<? extends Player> r = ((OmnitrixRenderDispatcherAccessor) Minecraft.getInstance().getEntityRenderDispatcher())
				.projecthero$playerRenderers().get(model);
		return r instanceof PlayerRenderer pr ? pr : null;
	}

	/** v0.17.2 (user: "make spidermonkeys arms slim"): the wide renderer, or the slim one for a kit with slim arms. */
	@Unique
	private static PlayerRenderer projecthero$wide(AbstractClientPlayer player) {
		AlienClientKit kit = AlienClients.shownKit(player);
		return projecthero$renderer(kit != null && kit.slimArms(player) ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE);
	}

	@Unique
	private boolean projecthero$redirect(AbstractClientPlayer player) {
		if (AlienClients.shown(player) == null) {
			return false;
		}
		PlayerRenderer target = projecthero$wide(player);
		return target != null && target != (Object) this;
	}

	/** v0.17.2 (Way Big, 100 blocks tall): a kit's render scale on top of the hitbox's ({@link AlienClientKit#renderScale}). */
	@Inject(method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("TAIL"))
	private void projecthero$alienRenderScale(AbstractClientPlayer player, PoseStack pose, float partial, CallbackInfo ci) {
		AlienClientKit kit = AlienClients.shownKit(player);
		if (kit != null) {
			float k = kit.renderScale(player);
			if (k != 1f) {
				pose.scale(k, k, k);
			}
		}
	}

	/** v0.17.1 (Wildmutt): the custom-body aliens' renderers are built from the player renderers' context. */
	@Inject(method = "<init>", at = @At("TAIL"))
	private void projecthero$buildAlienBodies(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context, boolean slim,
			CallbackInfo ci) {
		AlienClients.buildBodies(context);
	}

	@Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"), cancellable = true)
	private void projecthero$alienWide(AbstractClientPlayer player, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		// v0.17.1 (Wildmutt): an alien with its own body is drawn by its renderer instead of the player model
		AlienClientKit custom = AlienClients.shownKit(player);
		if (custom != null && custom.customBody(player)) {
			ci.cancel();
			if (!player.isInvisible()) {
				custom.renderBody(player, yaw, partial, pose, buffers, light);
			}
			return;
		}
		if (projecthero$redirect(player)) {
			ci.cancel();
			projecthero$wide(player).render(player, yaw, partial, pose, buffers, light);
		}
	}

	@Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
			at = @At("RETURN"), cancellable = true)
	private void projecthero$alienSkin(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> cir) {
		AlienClientKit kit = AlienClients.shownKit(player);
		if (kit != null) {
			cir.setReturnValue(kit.skin(player));
		}
	}

	@Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
	private void projecthero$alienRightHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, CallbackInfo ci) {
		AlienClientKit custom = AlienClients.shownKit(player);
		if (custom != null && custom.customBody(player) && custom.renderFirstPersonHand(pose, buffers, light, player, true)) {
			ci.cancel();
			return;
		}
		if (projecthero$redirect(player)) {
			ci.cancel();
			projecthero$wide(player).renderRightHand(pose, buffers, light, player);
		}
	}

	@Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
	private void projecthero$alienLeftHand(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, CallbackInfo ci) {
		AlienClientKit custom = AlienClients.shownKit(player);
		if (custom != null && custom.customBody(player) && custom.renderFirstPersonHand(pose, buffers, light, player, false)) {
			ci.cancel();
			return;
		}
		if (projecthero$redirect(player)) {
			ci.cancel();
			projecthero$wide(player).renderLeftHand(pose, buffers, light, player);
		}
	}

	@Inject(method = "renderHand", at = @At("HEAD"))
	private void projecthero$handBegin(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm, ModelPart sleeve,
			CallbackInfo ci) {
		OmnitrixFirstPerson.inHandRender = true;
	}

	@ModifyVariable(method = "renderHand", at = @At("STORE"), ordinal = 0)
	private ResourceLocation projecthero$alienHandSkin(ResourceLocation texture, PoseStack pose, MultiBufferSource buffers, int light,
			AbstractClientPlayer player, ModelPart arm, ModelPart sleeve) {
		AlienClientKit kit = AlienClients.shownKit(player);
		return kit != null ? kit.skin(player) : texture;
	}

	@Inject(method = "renderHand", at = @At("RETURN"))
	private void projecthero$handEnd(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ModelPart arm, ModelPart sleeve,
			CallbackInfo ci) {
		OmnitrixFirstPerson.inHandRender = false;
		if (!Omnitrix.hasPower(player) || player.isInvisible()) {
			return;
		}
		PlayerRenderer self = (PlayerRenderer) (Object) this;
		AlienClientKit kit = AlienClients.shownKit(player);
		if (kit != null) {
			pose.pushPose();
			arm.translateAndRotate(pose);
			kit.renderFirstPersonArm(pose, buffers, light, player, arm, arm == self.getModel().rightArm);
			pose.popPose();
			return;
		}
		if (arm == self.getModel().leftArm) {
			pose.pushPose();
			arm.translateAndRotate(pose);
			OmnitrixWatch.render(pose, buffers, light, player, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false),
					player.getSkin().model() == PlayerSkin.Model.SLIM, true);
			pose.popPose();
		}
	}
}
