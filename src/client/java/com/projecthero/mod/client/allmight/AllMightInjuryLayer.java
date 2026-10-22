package com.projecthero.mod.client.allmight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.allmight.AllMightStrain;
import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.allmight.data.AllMightState.Limb;
import com.projecthero.mod.allmight.item.AllMightArmorItem;
import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * v0.16.7: One For All's injuries, drawn on the limbs (every viewer sees them: it reads the synced {@code AllMightState}).
 * A <b>Strained</b> limb (40+) shows faint bruises, a <b>Hurt</b> one (70+) dark purple bruising and scrapes, a <b>Broken</b> one is
 * wrapped in blood-spotted bandages ({@code textures/entity/all_might/injury_*.png}, player-skin UV layout). Drawn as a thin
 * shell just outside the skin's second layer -- or, in the Power Form costume, just outside the costume -- on the arm / both legs.
 *
 * <p>This replaced {@code AllMightGlowLayer} (v0.16.6), the gold glow shells and arcs drawn around his arms at high Output: the user
 * asked for the arm glow to go. The body sparks, steam and shockwaves are server particles and stay.
 */
public class AllMightInjuryLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public static final ResourceLocation BRUISE = ProjectHeroMod.id("textures/entity/all_might/injury_bruise.png");
	public static final ResourceLocation BANDAGE = ProjectHeroMod.id("textures/entity/all_might/injury_bandage.png");
	/** Shell inflation (model pixels): over the skin (its second layer is 0.25) and over the costume (its outer cubes are 0.55). */
	private static final float SKIN_INFLATE = 0.32f;
	private static final float COSTUME_INFLATE = 0.62f;

	private static PlayerModel<AbstractClientPlayer> wide;
	private static PlayerModel<AbstractClientPlayer> slim;
	private static PlayerModel<AbstractClientPlayer> costume;

	public AllMightInjuryLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	private static PlayerModel<AbstractClientPlayer> bake(float inflate, boolean slimArms) {
		LayerDefinition def = LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(inflate), slimArms), 64, 64);
		return new PlayerModel<>(def.bakeRoot(), slimArms);
	}

	private static PlayerModel<AbstractClientPlayer> model(boolean slimArms, boolean inCostume) {
		if (inCostume) {
			if (costume == null) {
				costume = bake(COSTUME_INFLATE, false); // the costume's arms are always 4 px
			}
			return costume;
		}
		if (slimArms) {
			if (slim == null) {
				slim = bake(SKIN_INFLATE, true);
			}
			return slim;
		}
		if (wide == null) {
			wide = bake(SKIN_INFLATE, false);
		}
		return wide;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		PlayerModel<AbstractClientPlayer> parent = getParentModel();
		boolean slimArms = player.getSkin().model() == PlayerSkin.Model.SLIM;
		for (Limb l : Limb.values()) {
			int tier = AllMightStrain.tier(s, l);
			if (tier == 0) {
				continue;
			}
			EquipmentSlot slot = l == Limb.LEGS ? EquipmentSlot.LEGS : EquipmentSlot.CHEST;
			boolean inCostume = player.getItemBySlot(slot).getItem() instanceof AllMightArmorItem;
			PlayerModel<AbstractClientPlayer> m = model(slimArms, inCostume);
			// a strained limb: faint bruising; hurt: dark bruising; broken: bandaged
			ResourceLocation tex = tier == 3 ? BANDAGE : BRUISE;
			int colour = tier == 1 ? FastColor.ARGB32.color(130, 255, 255, 255) : FastColor.ARGB32.color(255, 255, 255, 255);
			VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(tex));
			switch (l) {
				case RIGHT_ARM -> draw(m.rightArm, parent.rightArm, pose, vc, light, colour);
				case LEFT_ARM -> draw(m.leftArm, parent.leftArm, pose, vc, light, colour);
				case LEGS -> {
					draw(m.rightLeg, parent.rightLeg, pose, vc, light, colour);
					draw(m.leftLeg, parent.leftLeg, pose, vc, light, colour);
				}
			}
		}
	}

	private static void draw(ModelPart part, ModelPart from, PoseStack pose, VertexConsumer vc, int light, int colour) {
		if (!from.visible) {
			return;
		}
		part.copyFrom(from);
		part.visible = true;
		part.render(pose, vc, light, OverlayTexture.NO_OVERLAY, colour);
	}
}
