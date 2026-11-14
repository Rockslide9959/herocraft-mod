package com.projecthero.mod.client.spin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.moonknight.MoonKnightCapeLayer;
import com.projecthero.mod.spin.GyroSuitItem;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * v0.17.4 (user: "make a cape model and remove the cape from the back of the skin"): Gyro's green cape as a real cloth panel instead of
 * paint on the suit's back. Drawn while his coat is worn, on the vanilla cape model ({@code PlayerModel.renderCloak}: 10 x 16 x 1 px
 * from the shoulders) with exactly vanilla's cape swing ({@link MoonKnightCapeLayer#cloakSwing}: lags behind when he moves, bobs
 * with his walk, lifts when he crouches); the wearer's own Minecraft cape is hidden meanwhile ({@code GyroCoatCapeMixin}). Texture
 * {@code textures/entity/spin/gyro_cape.png} (vanilla cape layout) comes from {@code scratchpad/spin/fix_gyro_suit_skin.js}, like the
 * suit skins; without it nothing is drawn.
 */
public class GyroCapeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/spin/gyro_cape.png");
	/** The texture is there (null: not looked up since the last resource reload). */
	private static Boolean present;

	public GyroCapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	static void initialize() {
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public ResourceLocation getFabricId() {
				return ProjectHeroMod.id("gyro_cape");
			}

			@Override
			public void onResourceManagerReload(ResourceManager manager) {
				present = null;
			}
		});
	}

	/** Gyro's cape is drawn on this player (the coat is on and the cape texture exists). */
	public static boolean shown(AbstractClientPlayer player) {
		if (!GyroSuitItem.wornIn(player, EquipmentSlot.CHEST)) {
			return false;
		}
		if (present == null) {
			present = Minecraft.getInstance().getResourceManager().getResource(TEXTURE).isPresent();
		}
		return present;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !shown(player)) {
			return;
		}
		pose.pushPose();
		pose.translate(0.0F, 0.0F, 0.125F + 0.01F); // vanilla's offset, a hair further out over the suit's base layer
		float[] cloak = MoonKnightCapeLayer.cloakSwing(player, partialTick);
		pose.mulPose(Axis.XP.rotationDegrees(cloak[0]));
		pose.mulPose(Axis.ZP.rotationDegrees(cloak[1] / 2.0F));
		pose.mulPose(Axis.YP.rotationDegrees(180.0F - cloak[1] / 2.0F));
		getParentModel().renderCloak(pose, buffers.getBuffer(RenderType.entitySolid(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}
}
