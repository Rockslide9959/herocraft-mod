package com.projecthero.mod.client.supersoldier;

import java.util.Objects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.projecthero.mod.supersoldier.entity.SoldierShieldEntity;
import com.projecthero.mod.supersoldier.item.SuperSoldierItems;

import net.minecraft.client.model.ShieldModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.phys.Vec3;

/**
 * The thrown shield (v0.14.8; v0.14.9 it draws the real stack being thrown): laid flat, tilted along its flight and spun
 * like a discus. The Adamantium Shield is its round disc ({@link AdamantiumShieldRenderer}); a vanilla shield is the
 * vanilla shield model with its banner pattern (the same calls the vanilla item renderer makes); anything else (another
 * mod's shield) falls back to its item model. All are drawn about their own centre so the spin does not wobble.
 */
public class SoldierShieldRenderer extends EntityRenderer<SoldierShieldEntity> {
	private final ItemRenderer items;
	private final ShieldModel shieldModel;
	private final AdamantiumShieldRenderer disc = new AdamantiumShieldRenderer();
	private ItemStack fallback;

	public SoldierShieldRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.items = context.getItemRenderer();
		this.shieldModel = new ShieldModel(context.bakeLayer(ModelLayers.SHIELD));
		this.shadowRadius = 0.15f;
	}

	@Override
	public void render(SoldierShieldEntity e, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		ItemStack stack = e.getItem();
		if (stack.isEmpty()) {
			if (fallback == null) {
				fallback = new ItemStack(SuperSoldierItems.SOLDIER_SHIELD);
			}
			stack = fallback;
		}
		Vec3 v = e.getDeltaMovement();
		float yaw = v.lengthSqr() > 1.0e-6 ? (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG) : 0.0f;
		float pitch = v.lengthSqr() > 1.0e-6 ? (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG) : 0.0f;
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.mulPose(Axis.XP.rotationDegrees(-pitch));
		// Lay it flat like a discus with the decorated FRONT up. Both shield models (the vanilla one and the Adamantium disc,
		// drawn in the same space) face their front along +Z after their scale(1, -1, -1) flip, with the grip toward -Z. A
		// +90 X rotation turns +Z to -Y -- front down, grip up: the v0.14.8 "upside-down" shield. -90 turns +Z to +Y.
		pose.mulPose(Axis.XP.rotationDegrees(-90.0f));
		pose.mulPose(Axis.ZP.rotationDegrees((e.tickCount + partialTick) * 40.0f));
		if (stack.is(SuperSoldierItems.ADAMANTIUM_SHIELD)) {
			pose.scale(1.1f, 1.1f, 1.1f);
			disc.render(stack, ItemDisplayContext.NONE, pose, buffers, light, OverlayTexture.NO_OVERLAY);
		} else if (stack.is(Items.SHIELD)) {
			pose.scale(0.9f, 0.9f, 0.9f);
			vanillaShield(stack, pose, buffers, light);
		} else {
			pose.scale(1.1f, 1.1f, 1.1f);
			items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, e.level(), e.getId());
		}
		pose.popPose();
		super.render(e, entityYaw, partialTick, pose, buffers, light);
	}

	/** Exactly what {@code BlockEntityWithoutLevelRenderer} does for {@code minecraft:shield}, banner included. */
	private void vanillaShield(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light) {
		BannerPatternLayers patterns = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		DyeColor base = stack.get(DataComponents.BASE_COLOR);
		boolean banner = !patterns.layers().isEmpty() || base != null;
		pose.pushPose();
		pose.scale(1.0f, -1.0f, -1.0f);
		pose.translate(0.0f, 0.0f, 1.5f / 16.0f); // centre the plate (z -2..-1) on the spin axis
		Material material = banner ? ModelBakery.SHIELD_BASE : ModelBakery.NO_PATTERN_SHIELD;
		VertexConsumer vc = material.sprite().wrap(ItemRenderer.getFoilBufferDirect(buffers,
				shieldModel.renderType(material.atlasLocation()), true, stack.hasFoil()));
		shieldModel.handle().render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		if (banner) {
			BannerRenderer.renderPatterns(pose, buffers, light, OverlayTexture.NO_OVERLAY, shieldModel.plate(), material, false,
					Objects.requireNonNullElse(base, DyeColor.WHITE), patterns, stack.hasFoil());
		} else {
			shieldModel.plate().render(pose, vc, light, OverlayTexture.NO_OVERLAY);
		}
		pose.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(SoldierShieldEntity e) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
