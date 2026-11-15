package com.projecthero.mod.client.gear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.5: the Celestial armour -- the plain vanilla armour layer (so enchantment glint behaves), then the gold trims
 * and star motes again from a {@code _glow} texture on a shell a hair bigger, drawn at full brightness so they shine
 * in the dark. (A same-size additive pass got painted over by the armour layer when the buffers flushed in the other
 * order; the slightly larger cutout shell sits in front of it whatever the order.)
 */
public final class CelestialArmorRenderer implements ArmorRenderer {
	private static final ResourceLocation LAYER_1 = ProjectHeroMod.id("textures/models/armor/celestial_layer_1.png");
	private static final ResourceLocation LAYER_2 = ProjectHeroMod.id("textures/models/armor/celestial_layer_2.png");
	private static final ResourceLocation GLOW_1 = ProjectHeroMod.id("textures/models/armor/celestial_layer_1_glow.png");
	private static final ResourceLocation GLOW_2 = ProjectHeroMod.id("textures/models/armor/celestial_layer_2_glow.png");
	private static final float SHELL = 0.04f;

	private HumanoidModel<LivingEntity> inner, outer, innerGlow, outerGlow;

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, ItemStack stack, LivingEntity entity, EquipmentSlot slot, int light,
			HumanoidModel<LivingEntity> context) {
		if (outer == null) {
			var models = Minecraft.getInstance().getEntityModels();
			inner = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
			outer = new HumanoidModel<>(models.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
			innerGlow = new HumanoidModel<>(LayerDefinition.create(HumanoidArmorModel.createBodyLayer(new CubeDeformation(0.5f + SHELL)), 64, 32).bakeRoot());
			outerGlow = new HumanoidModel<>(LayerDefinition.create(HumanoidArmorModel.createBodyLayer(new CubeDeformation(1.0f + SHELL)), 64, 32).bakeRoot());
		}
		boolean legs = slot == EquipmentSlot.LEGS;
		HumanoidModel<LivingEntity> m = legs ? inner : outer;
		HumanoidModel<LivingEntity> g = legs ? innerGlow : outerGlow;
		if (!show(context, m, slot) || !show(context, g, slot)) {
			return;
		}
		ArmorRenderer.renderPart(pose, buffers, light, stack, m, legs ? LAYER_2 : LAYER_1);
		g.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(legs ? GLOW_2 : GLOW_1)), LightTexture.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY);
	}

	/** Copies the wearer's pose and shows only the parts this slot covers (like vanilla's armour layer). */
	private static boolean show(HumanoidModel<LivingEntity> context, HumanoidModel<LivingEntity> m, EquipmentSlot slot) {
		context.copyPropertiesTo(m);
		m.setAllVisible(false);
		switch (slot) {
			case HEAD -> {
				m.head.visible = true;
				m.hat.visible = true;
			}
			case CHEST -> {
				m.body.visible = true;
				m.rightArm.visible = true;
				m.leftArm.visible = true;
			}
			case LEGS -> {
				m.body.visible = true;
				m.rightLeg.visible = true;
				m.leftLeg.visible = true;
			}
			case FEET -> {
				m.rightLeg.visible = true;
				m.leftLeg.visible = true;
			}
			default -> {
				return false;
			}
		}
		return true;
	}
}
