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
 * like a discus. v0.17.5: the three Captain America shields are the stepped dish ({@link CapShieldModel}), spun faster with
 * three see-through after-images trailing it; a vanilla shield is the vanilla shield model with its banner pattern (the same
 * calls the vanilla item renderer makes); anything else (another mod's shield) falls back to its item model. All are drawn
 * about their own centre so the spin does not wobble.
 */
public class SoldierShieldRenderer extends EntityRenderer<SoldierShieldEntity> {
	private final ItemRenderer items;
	private final ShieldModel shieldModel;
	private ItemStack fallback;

	public SoldierShieldRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.items = context.getItemRenderer();
		this.shieldModel = new ShieldModel(context.bakeLayer(ModelLayers.SHIELD));
		this.shadowRadius = 0.15f;
	}

	/** Spin (degrees a tick) -- fast, a blur of rings at speed. */
	private static final float SPIN = 62.0f;
	/** The after-images: how far back along the last tick's travel, how see-through, how far behind in the spin. */
	private static final float[] TRAIL_BACK = { 0.22f, 0.44f, 0.66f };
	private static final float[] TRAIL_ALPHA = { 0.34f, 0.20f, 0.09f };
	/** v0.17.7: the Shield Storm's longer, brighter ghost trail. */
	private static final float[] STORM_BACK = { 0.18f, 0.36f, 0.54f, 0.72f, 0.9f };
	private static final float[] STORM_ALPHA = { 0.5f, 0.38f, 0.27f, 0.17f, 0.08f };

	@Override
	public void render(SoldierShieldEntity e, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		ItemStack stack = e.getItem();
		if (stack.isEmpty()) {
			if (fallback == null) {
				fallback = new ItemStack(SuperSoldierItems.SOLDIER_SHIELD);
			}
			stack = fallback;
		}
		if (ownViewTooClose(e, partialTick)) {
			return;
		}
		Vec3 v = e.getDeltaMovement();
		float yaw = v.lengthSqr() > 1.0e-6 ? (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG) : 0.0f;
		float pitch = v.lengthSqr() > 1.0e-6 ? (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG) : 0.0f;
		float spin = (e.tickCount + partialTick) * SPIN;
		if (CapShieldModel.isCapShield(stack)) {
			// v0.17.5: the stepped dish, flat and face up, spinning fast about its own centre, with three fading after-images
			ResourceLocation tex = CapShieldModel.texture(stack);
			boolean storm = e.isStorm();
			float[] back = storm ? STORM_BACK : TRAIL_BACK;
			float[] alpha = storm ? STORM_ALPHA : TRAIL_ALPHA;
			for (int i = back.length - 1; i >= 0; i--) {
				if (v.lengthSqr() < 0.04) {
					break;
				}
				pose.pushPose();
				pose.translate(-v.x * back[i], -v.y * back[i], -v.z * back[i]);
				flat(pose, yaw, pitch, spin - 14f * (i + 1));
				// v0.17.7: the Shield Storm's ghosts are brighter and longer, and full-bright so they read against any sky
				CapShieldModel.renderGhost(pose, buffers, tex, storm ? 0xF000F0 : light, OverlayTexture.NO_OVERLAY, alpha[i]);
				pose.popPose();
			}
			pose.pushPose();
			flat(pose, yaw, pitch, spin);
			CapShieldModel.render(pose, buffers, tex, stack.hasFoil(), light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
		} else {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(yaw));
			pose.mulPose(Axis.XP.rotationDegrees(-pitch));
			// Lay it flat like a discus with the decorated FRONT up. The vanilla shield model faces its front along +Z after its
			// scale(1, -1, -1) flip, with the grip toward -Z; -90 about X turns +Z to +Y (v0.14.8 had +90: upside down).
			pose.mulPose(Axis.XP.rotationDegrees(-90.0f));
			pose.mulPose(Axis.ZP.rotationDegrees(spin));
			if (stack.is(Items.SHIELD)) {
				pose.scale(0.9f, 0.9f, 0.9f);
				vanillaShield(stack, pose, buffers, light);
			} else {
				pose.scale(1.1f, 1.1f, 1.1f);
				items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, e.level(), e.getId());
			}
			pose.popPose();
		}
		super.render(e, entityYaw, partialTick, pose, buffers, light);
	}

	/**
	 * v0.17.5: in his own first-person view the shield is not drawn while it is right at the lens -- the first two ticks of the
	 * throw (it starts 0.6 blocks in front of the eyes and would fill the screen) and the last metre home (the catch) -- the
	 * hand animations carry those moments.
	 */
	private static boolean ownViewTooClose(SoldierShieldEntity e, float partial) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null || e.ownerEntityId() != mc.player.getId() || !mc.options.getCameraType().isFirstPerson()
				|| mc.getCameraEntity() != mc.player) {
			return false;
		}
		if (!e.isReturning() && e.tickCount < 2) {
			return true;
		}
		return e.getPosition(partial).distanceTo(mc.player.getEyePosition(partial)) < 1.3;
	}

	/** Along the flight, laid flat face up (shield +Z to world up), tipped a little like a banking disc, spun, centred on its depth. */
	private static void flat(PoseStack pose, float yaw, float pitch, float spin) {
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.mulPose(Axis.XP.rotationDegrees(-pitch));
		pose.mulPose(Axis.ZP.rotationDegrees(8.0f));
		pose.mulPose(Axis.XP.rotationDegrees(-90.0f));
		pose.mulPose(Axis.ZP.rotationDegrees(spin));
		pose.scale(SCALE, SCALE, SCALE);
		pose.translate(0.0f, 0.0f, -CapShieldModel.MID_Z / 16.0f);
	}

	private static final float SCALE = 0.9f;

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
