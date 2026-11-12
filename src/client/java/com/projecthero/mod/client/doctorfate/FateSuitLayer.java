package com.projecthero.mod.client.doctorfate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.doctorfate.DoctorFate;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.3 Doctor Fate's look on every player wearing the Helmet of Fate (the state is synced, so every viewer sees it): the
 * blue-and-gold outfit over the skin (a player model a hair larger, wide or slim to match), the golden Helmet of Fate over the head
 * (a 10-pixel dome with the T-shaped eye slit, side fins and a crest -- the face is hidden), and the Amulet of Anubis on the chest.
 * The Cloak of Destiny is {@link FateCloakLayer}. V's 1 s animation fades the outfit in and lowers the helmet onto the head.
 */
public class FateSuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public static final ResourceLocation SUIT = ProjectHeroMod.id("textures/entity/doctor_fate/suit.png");
	public static final ResourceLocation SUIT_SLIM = ProjectHeroMod.id("textures/entity/doctor_fate/suit_slim.png");
	public static final ResourceLocation HELMET = ProjectHeroMod.id("textures/entity/doctor_fate/helmet.png");

	private static PlayerModel<AbstractClientPlayer> wide;
	private static PlayerModel<AbstractClientPlayer> slim;
	private static ModelPart helmetRoot;

	public FateSuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	static PlayerModel<AbstractClientPlayer> suitModel(boolean slimArms) {
		if (wide == null) {
			wide = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.06f), false), 64, 64).bakeRoot(), false);
			slim = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.06f), true), 64, 64).bakeRoot(), true);
		}
		return slimArms ? slim : wide;
	}

	/** The helmet (child "helmet": dome, fins, crest) and the amulet, on a 64 x 32 texture. */
	static ModelPart helmetModel() {
		if (helmetRoot == null) {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			PartDefinition helm = root.addOrReplaceChild("helmet", CubeListBuilder.create().texOffs(0, 0).addBox(-5f, -10.5f, -5f, 10, 10, 10),
					PartPose.ZERO);
			helm.addOrReplaceChild("fin_left", CubeListBuilder.create().texOffs(40, 0).addBox(5f, -8f, -3f, 1, 6, 6), PartPose.ZERO);
			helm.addOrReplaceChild("fin_right", CubeListBuilder.create().texOffs(40, 0).mirror().addBox(-6f, -8f, -3f, 1, 6, 6), PartPose.ZERO);
			helm.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(40, 12).addBox(-0.5f, -12.5f, -4.5f, 1, 2, 9), PartPose.ZERO);
			root.addOrReplaceChild("amulet", CubeListBuilder.create().texOffs(0, 20).addBox(-1.5f, 2.5f, -3.1f, 3, 3, 1), PartPose.ZERO);
			helmetRoot = LayerDefinition.create(mesh, 64, 32).bakeRoot();
		}
		return helmetRoot;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		float shown = DoctorFate.possessed(player) ? 1f : DoctorFate.helmetShown(player, partialTick);
		if (shown <= 0.01f) {
			return;
		}
		renderOn(pose, buffers, light, getParentModel(), player.getSkin().model() == PlayerSkin.Model.SLIM, shown);
	}

	/** Draws the outfit, helmet and amulet posed like {@code parent}; {@code shown} 0..1 is V's animation. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void renderOn(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, boolean slimArms, float shown) {
		PlayerModel<AbstractClientPlayer> m = suitModel(slimArms);
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		m.head.visible = false; // the helmet covers it
		m.hat.visible = false;
		int alpha = Math.round(255 * Math.min(1f, shown * 1.4f));
		int colour = (alpha << 24) | 0xFFFFFF;
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(slimArms ? SUIT_SLIM : SUIT)), light, OverlayTexture.NO_OVERLAY, colour);
		m.head.visible = true;
		m.hat.visible = true;

		ModelPart root = helmetModel();
		ModelPart helmet = root.getChild("helmet");
		ModelPart amulet = root.getChild("amulet");
		helmet.copyFrom(parent.head);
		helmet.y -= (1f - shown) * 12f; // lowered onto the head over the 1 s
		amulet.copyFrom(parent.body);
		int hc = (Math.round(255 * shown) << 24) | 0xFFFFFF;
		var vc = buffers.getBuffer(RenderType.entityTranslucent(HELMET));
		helmet.render(pose, vc, light, OverlayTexture.NO_OVERLAY, hc);
		amulet.render(pose, vc, light, OverlayTexture.NO_OVERLAY, colour);
	}
}
