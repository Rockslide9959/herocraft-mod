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
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 Doctor Fate's look on every player wearing the Helmet of Fate (the state is synced, so every viewer sees it): the user's
 * chosen DCEU skin (JMCT, PlanetMinecraft -- see docs/reference/doctorfate/SOURCE.txt) drawn as a second skin a hair over the
 * wearer's, wide or slim to match their model ({@code scratchpad/doctorfate/build_fate_skin.js} makes both). Its head IS the Helmet
 * of Fate: the helmet's side openings are cut out of the texture so the wearer's own skin shows there, and the wearer's outer layer
 * is hidden while the suit is on ({@link #hidesSkinOverlay}) so nothing pokes through. The cape painted on the skin's back was taken
 * off; the cloth Cloak of Destiny is {@link FateCloakLayer}. V's 1 s animation fades the suit in and lowers the helmet onto the head.
 *
 * <p>The old separate helmet / amulet model ({@link #helmetModel}) is no longer drawn on the player (it clashed with the skin's own
 * helmet and chest emblem); the Avatar of Order ({@link FateWorldRender}) still wears it.
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
		int alpha = Math.round(255 * Math.min(1f, shown * 1.4f));
		int colour = (alpha << 24) | 0xFFFFFF;
		var vc = buffers.getBuffer(RenderType.entityTranslucent(slimArms ? SUIT_SLIM : SUIT));
		// the body, arms and legs (the head and hat are drawn on their own below)
		m.head.visible = false;
		m.hat.visible = false;
		m.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, colour);
		m.head.visible = true;
		m.hat.visible = true;
		// the helmet: the skin's head + hat layer, lowered onto the head over V's 1 s
		float lift = (1f - shown) * 12f;
		m.head.y -= lift;
		m.hat.copyFrom(m.head);
		int hc = (Math.round(255 * shown) << 24) | 0xFFFFFF;
		m.head.render(pose, vc, light, OverlayTexture.NO_OVERLAY, hc);
		m.hat.render(pose, vc, light, OverlayTexture.NO_OVERLAY, hc);
		m.head.y += lift;
		m.hat.copyFrom(m.head);
	}

	/**
	 * The wearer's own outer skin layer (hat / hair, jacket, sleeves, trousers) is hidden while the suit is mostly on: it would
	 * poke through the suit drawn a hair over the base skin. Their base skin still shows through the helmet's side openings.
	 */
	public static boolean hidesSkinOverlay(Player player) {
		return DoctorFate.possessed(player) || DoctorFate.helmetShown(player, 0f) > 0.3f;
	}
}
