package com.projecthero.mod.client.hulk;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hulk.gladiator.GladiatorGear;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * v0.15.3: Gladiator Hulk's armour, war paint and weapons.
 *
 * <p>The pieces are bones of {@code geo/hulk.geo.json} itself whose names start with {@value #PREFIX} (added by
 * {@code scratchpad/gen_hulk_gladiator_v0153.js}) -- children of {@code head}, {@code body}, the arms and the legs, so
 * they follow every Hulk animation and his scale for free. Their UVs point into their own texture,
 * {@code textures/entity/hulk_gladiator.png}, which is why they are drawn here, in a second pass:
 * <ol>
 *   <li>the main pass ({@link #baseVisibility}, from {@code HulkRenderer.preRender}) hides every gladiator bone, so a
 *       Hulk without the full kit looks exactly as before (and {@code hulk.png}, also the first-person arm skin, is
 *       untouched);</li>
 *   <li>this layer, only for {@link GladiatorGear#isGladiator}, hides the Hulk's own cubes (not their children) and
 *       re-renders the same animated model with the gladiator texture -- just the gear. A weapon that is out of his hand
 *       ({@link GladiatorGear#weaponAway}) stays hidden.</li>
 * </ol>
 * The hand weapons are the bones {@value #HAMMER_BONE} (child of {@code right_arm}) and {@value #AXE_BONE} (child of
 * {@code left_arm}).
 */
public class HulkGladiatorLayer extends GeoRenderLayer<HulkAnimatable> {
	public static final String PREFIX = "gladiator";
	public static final String HAMMER_BONE = "gladiator_hammer";
	public static final String AXE_BONE = "gladiator_axe";
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/hulk_gladiator.png");

	private final HulkRenderer hulk;

	public HulkGladiatorLayer(HulkRenderer renderer) {
		super(renderer);
		this.hulk = renderer;
	}

	private static boolean isGladiatorBone(GeoBone bone) {
		return bone.getName().startsWith(PREFIX);
	}

	/** Main pass: the Hulk's own cubes drawn, every gladiator bone (and its children) hidden. */
	public static void baseVisibility(BakedGeoModel model) {
		for (GeoBone bone : model.topLevelBones()) {
			base(bone);
		}
	}

	private static void base(GeoBone bone) {
		bone.setHidden(isGladiatorBone(bone)); // also hides / shows the children
		if (!isGladiatorBone(bone)) {
			for (GeoBone child : bone.getChildBones()) {
				base(child);
			}
		}
	}

	/** Gear pass: only the gladiator bones, minus a thrown weapon. */
	private static void gearVisibility(GeoBone bone, boolean hammerAway, boolean axeAway) {
		if (isGladiatorBone(bone)) {
			String name = bone.getName();
			bone.setHidden((hammerAway && name.equals(HAMMER_BONE)) || (axeAway && name.equals(AXE_BONE)));
		} else {
			bone.setHidden(true);
			bone.setChildrenHidden(false);
		}
		for (GeoBone child : bone.getChildBones()) {
			gearVisibility(child, hammerAway, axeAway);
		}
	}

	@Override
	public void render(PoseStack poseStack, HulkAnimatable animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
			MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
		AbstractClientPlayer player = hulk.getCurrentEntity();
		if (player == null || !GladiatorGear.isGladiator(player)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (player.isInvisible() && (mc.player == null || player.isInvisibleTo(mc.player))) {
			return;
		}
		boolean hammerAway = GladiatorGear.weaponAway(player, false);
		boolean axeAway = GladiatorGear.weaponAway(player, true);
		for (GeoBone bone : bakedModel.topLevelBones()) {
			gearVisibility(bone, hammerAway, axeAway);
		}
		try {
			float fade = hulk.fade();
			RenderType type = fade < 0.999f ? RenderType.entityTranslucent(TEXTURE) : RenderType.entityCutoutNoCull(TEXTURE);
			int alpha = Math.round(255 * Math.max(0.0f, Math.min(1.0f, fade)));
			getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type),
					partialTick, packedLight, packedOverlay, (alpha << 24) | 0xFFFFFF);
		} finally {
			baseVisibility(bakedModel);
		}
	}
}
