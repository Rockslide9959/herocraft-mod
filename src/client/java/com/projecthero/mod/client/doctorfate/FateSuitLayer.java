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
import net.minecraft.client.renderer.LightTexture;
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
		FateHelmetAnim a = FateHelmetAnim.of(player, partialTick);
		if (shown <= 0.01f && !a.held() && !a.onHead()) {
			return;
		}
		renderOn(pose, buffers, light, getParentModel(), player.getSkin().model() == PlayerSkin.Model.SLIM, shown, a);
	}

	/** The suit as on a player wearing the helmet (the Avatar, harness shots). */
	public static void renderOn(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, boolean slimArms, float shown) {
		renderOn(pose, buffers, light, parent, slimArms, shown, shown > 0.01f ? FateHelmetAnim.WORN : FateHelmetAnim.NONE);
	}

	/**
	 * Draws the outfit posed like {@code parent} at {@code shown} 0..1 opacity, and the helmet: on the head, or (v0.17.4, the put-on /
	 * lift-off sequences) held in the hands at {@link FateHelmetAnim#hy} / {@link FateHelmetAnim#hz}.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void renderOn(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent, boolean slimArms, float shown,
			FateHelmetAnim anim) {
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
		if (shown > 0.01f) {
			m.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, colour);
		}
		m.head.visible = true;
		m.hat.visible = true;
		if (anim.onHead()) {
			// the helmet: the skin's head + hat layer, on the head
			m.head.render(pose, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			m.hat.render(pose, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			if (anim.glow() > 0.01f) {
				sheen(pose, buffers, m.hat, anim.glow());
			}
		} else if (anim.held() && anim.alpha() > 0.01f) {
			// v0.17.4: in the hands, upright and facing the way the body faces
			float hx = m.head.x, hy = m.head.y, hz = m.head.z, rx = m.head.xRot, ry = m.head.yRot, rz = m.head.zRot;
			m.head.xRot = 0f;
			m.head.yRot = 0f;
			m.head.zRot = 0f;
			m.head.y = parent.head.y + anim.hy();
			m.head.z = parent.head.z + anim.hz();
			m.hat.copyFrom(m.head);
			int ac = (Math.round(255 * Math.min(1f, anim.alpha())) << 24) | 0xFFFFFF;
			m.head.render(pose, vc, light, OverlayTexture.NO_OVERLAY, ac);
			m.hat.render(pose, vc, light, OverlayTexture.NO_OVERLAY, ac);
			if (anim.glow() > 0.01f) {
				sheen(pose, buffers, m.hat, anim.glow() * Math.min(1f, anim.alpha() + 0.3f));
			}
			m.head.x = hx;
			m.head.y = hy;
			m.head.z = hz;
			m.head.xRot = rx;
			m.head.yRot = ry;
			m.head.zRot = rz;
			m.hat.copyFrom(m.head);
		}
	}

	/** A gold sheen over the helmet (forming in the hands, held high under the ankh, settling, turning to gold). */
	static void sheen(PoseStack pose, MultiBufferSource buffers, ModelPart hat, float k) {
		int g = Math.round(255 * Math.min(1f, k) * 0.7f);
		int c = 0xFF000000 | (g << 16) | (Math.round(g * 0.78f) << 8) | Math.round(g * 0.25f);
		hat.render(pose, buffers.getBuffer(RenderType.eyes(SUIT)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, c);
	}

	/** The helmet alone (the suit's head and hat layer) around the current pose origin, for the placed helmet and the summon. */
	public static void renderHelmetAlone(PoseStack pose, MultiBufferSource buffers, int light, float alpha, float glow) {
		PlayerModel<AbstractClientPlayer> m = suitModel(false);
		m.head.resetPose();
		m.head.xRot = 0f;
		m.head.yRot = 0f;
		m.head.zRot = 0f;
		m.hat.copyFrom(m.head);
		var vc = buffers.getBuffer(RenderType.entityTranslucent(SUIT));
		int ac = (Math.round(255 * Math.min(1f, Math.max(0f, alpha))) << 24) | 0xFFFFFF;
		m.head.render(pose, vc, light, OverlayTexture.NO_OVERLAY, ac);
		m.hat.render(pose, vc, light, OverlayTexture.NO_OVERLAY, ac);
		if (glow > 0.01f) {
			sheen(pose, buffers, m.hat, glow * alpha);
		}
	}

	/**
	 * The wearer's own outer skin layer (hat / hair, jacket, sleeves, trousers) is hidden while the suit is mostly on: it would
	 * poke through the suit drawn a hair over the base skin. Their base skin still shows through the helmet's side openings.
	 */
	public static boolean hidesSkinOverlay(Player player) {
		return DoctorFate.possessed(player) || DoctorFate.helmetShown(player, 0f) > 0.3f;
	}
}
