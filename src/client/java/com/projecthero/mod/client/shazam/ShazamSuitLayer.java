package com.projecthero.mod.client.shazam;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.shazam.Shazam;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.17.3 Shazam: the suit, drawn over the wearer's own skin while transformed (every viewer: the state is synced). The textures are
 * the user's chosen skin ("Shazam (Justice League)" by outofspace2, PlanetMinecraft; licence to check before a public release) with
 * its face, hair, neck, hands and painted cape made transparent, so the wearer's own head and skin show
 * ({@code scratchpad/shazam/make_shazam_suit.js}); a Shazam Family member's adds gold piping. The head is left alone (his own face). The suit's model sits just outside the skin; the wearer's jacket / sleeve / trouser layers are hidden while it's on
 * ({@link #hidesSkinOverlay}), so nothing of the skin's second layer pokes through. Wide and slim arms follow the wearer's skin. The short cape is
 * {@link ShazamCapeLayer}.
 */
public class ShazamSuitLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation SUIT = tex("shazam_suit");
	private static final ResourceLocation SUIT_SLIM = tex("shazam_suit_slim");
	private static final ResourceLocation FAMILY = tex("shazam_suit_family");
	private static final ResourceLocation FAMILY_SLIM = tex("shazam_suit_family_slim");

	private static PlayerModel<LivingEntity> wide;
	private static PlayerModel<LivingEntity> slim;

	public ShazamSuitLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	private static ResourceLocation tex(String name) {
		return ProjectHeroMod.id("textures/entity/shazam/" + name + ".png");
	}

	private static PlayerModel<LivingEntity> model(boolean slimArms) {
		if (slimArms) {
			if (slim == null) {
				MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(0.05f), true);
				slim = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), true);
			}
			return slim;
		}
		if (wide == null) {
			MeshDefinition mesh = PlayerModel.createMesh(new CubeDeformation(0.05f), false);
			wide = new PlayerModel<>(LayerDefinition.create(mesh, 64, 64).bakeRoot(), false);
		}
		return wide;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible() || !Shazam.transformed(player)) {
			return;
		}
		boolean slimArms = player.getSkin().model() == PlayerSkin.Model.SLIM;
		PlayerModel<LivingEntity> m = posed(getParentModel(), slimArms);
		boolean family = Shazam.isFamily(player);
		ResourceLocation tex = family ? (slimArms ? FAMILY_SLIM : FAMILY) : (slimArms ? SUIT_SLIM : SUIT);
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY);
		// v0.17.4 (user): no glowing eyes and no arcs round his hands any more (v0.17.3 drew them while charged)
	}

	/**
	 * From {@code PlayerModelMixin}: while the suit is on, the wearer's jacket / sleeve / trouser layers are hidden (the suit's own
	 * outer layer is mostly clear now, so they would poke through it); the hat layer stays, it's his own head.
	 */
	public static boolean hidesSkinOverlay(net.minecraft.world.entity.player.Player player) {
		return !player.isInvisible() && Shazam.transformed(player);
	}

	/** Copies the wearer's pose onto the suit model; the head stays the wearer's own. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	static PlayerModel<LivingEntity> posed(HumanoidModel<?> parent, boolean slimArms) {
		PlayerModel<LivingEntity> m = model(slimArms);
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.setAllVisible(true);
		m.head.visible = false;
		m.hat.visible = false;
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		return m;
	}
}
