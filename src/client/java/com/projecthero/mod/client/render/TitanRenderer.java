package com.projecthero.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.titan.entity.TitanBoulderEntity;
import com.projecthero.mod.titan.entity.TitanEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * v0.14.20: the Titan's own GeckoLib model -- a hunched, rotting giant zombie with long arms ending in oversized
 * clawed hands, an exposed ribcage and spine spikes, shackles with broken chains, ragged trousers, a jaw that
 * opens and burning eyes (glowmask). Every asset comes from {@code scratchpad/gen_v01420_titan_model.js}; the
 * clips are picked by {@link TitanEntity#registerControllers} from the synced {@link TitanEntity.Anim}.
 *
 * <p>The model is authored in plain model pixels and scaled by {@link #MODEL_SCALE} (printed by the generator) so
 * its rest pose is exactly the 18-block hit-box height; the scale is applied on the main pass only, since the
 * glow layer re-renders inside the already-scaled stack. Here, on top of the clips:
 * <ul>
 * <li>the head follows its look target through the never-keyed {@code neck} bone (set absolutely each frame, so it
 * can't accumulate), faded out during moves that own the head;</li>
 * <li>a hurt flinch on the never-keyed {@code flinch} bone (between waist and chest);</li>
 * <li>the torn-up boulder drawn between its hands during the Boulder clip;</li>
 * <li>no vanilla tip-over or red tint while dying -- the death clip is the collapse.</li>
 * </ul>
 */
public class TitanRenderer extends GeoEntityRenderer<TitanEntity> {
	/** Rest model height 49.42 px -> 18 blocks (gen_v01420_titan_model.js prints this). */
	public static final float MODEL_SCALE = 5.8277f;

	public TitanRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
		this.shadowRadius = 4.6f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
		addRenderLayer(new HeldBoulderLayer(this));
	}

	@Override
	public void preRender(PoseStack poseStack, TitanEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
			VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		if (!isReRender) {
			poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
		}
	}

	@Override
	protected float getDeathMaxRotation(TitanEntity animatable) {
		return 0.0f;
	}

	@Override
	public int getPackedOverlay(TitanEntity animatable, float u, float partialTick) {
		boolean flash = animatable.hurtTime > 0 && !animatable.isDeadOrDying();
		return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(flash));
	}

	static final class Model extends GeoModel<TitanEntity> {
		private static final ResourceLocation MODEL = ProjectHeroMod.id("geo/titan.geo.json");
		private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/titan.png");
		private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/titan.animation.json");

		@Override
		public ResourceLocation getModelResource(TitanEntity animatable) {
			return MODEL;
		}

		@Override
		public ResourceLocation getTextureResource(TitanEntity animatable) {
			return TEXTURE;
		}

		@Override
		public ResourceLocation getAnimationResource(TitanEntity animatable) {
			return ANIMATION;
		}

		@Override
		public void setCustomAnimations(TitanEntity titan, long instanceId, AnimationState<TitanEntity> state) {
			// Head look. "neck" is never keyed by a clip, so an absolute set each frame never accumulates.
			GeoBone neck = getAnimationProcessor().getBone("neck");
			if (neck != null) {
				float target = lookWeight(titan.isDeadOrDying() ? TitanEntity.Anim.DEATH : titan.clientAnim());
				titan.clientHeadWeight += (target - titan.clientHeadWeight) * 0.12f;
				EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
				float yaw = data == null ? 0.0f : Mth.clamp(data.netHeadYaw(), -60.0f, 60.0f);
				float pitch = data == null ? 0.0f : Mth.clamp(data.headPitch(), -35.0f, 30.0f);
				float w = titan.clientHeadWeight;
				neck.setRotY(yaw * w * Mth.DEG_TO_RAD);
				neck.setRotX(pitch * w * Mth.DEG_TO_RAD);
			}
			// Hurt flinch: the upper body jerks back and settles over the 10-tick hurt timer.
			GeoBone flinch = getAnimationProcessor().getBone("flinch");
			if (flinch != null) {
				float k = 0.0f;
				if (titan.hurtTime > 0 && !titan.isDeadOrDying()) {
					float t = (10.0f - titan.hurtTime + state.getPartialTick()) / 10.0f;
					k = Mth.sin(Mth.clamp(t, 0.0f, 1.0f) * Mth.PI) * (1.0f - t * 0.5f);
				}
				flinch.setRotX(k * 7.0f * Mth.DEG_TO_RAD);
				flinch.setRotZ(k * 2.5f * Mth.DEG_TO_RAD);
			}
		}

		/** How much the head tracks the target during each clip (moves that throw the head around own it). */
		private static float lookWeight(TitanEntity.Anim anim) {
			return switch (anim) {
				case NONE, SWAT -> 1.0f;
				case PUNCH, GRAB, BOULDER, SWEEP, STOMP, SLAM, SHOCKWAVE, CHARGE -> 0.45f;
				default -> 0.0f;
			};
		}
	}

	/**
	 * The rock torn out of the ground, drawn between its hands through the Boulder clip -- from the scoop until the
	 * release (clip ticks {@link TitanEntity#BOULDER_SHOWN_FROM}..{@link TitanEntity#BOULDER_SHOWN_TO}, after the
	 * controller's blend-in), in the right hand's frame so it follows the heave exactly. The block is the ground
	 * the Titan stands on (sand/dirt/stone), matching the thrown {@link TitanBoulderEntity}.
	 */
	static final class HeldBoulderLayer extends GeoRenderLayer<TitanEntity> {
		/**
		 * The rock's centre in the right hand's rest space (model px, json axes) -- midway between the two palms, which
		 * the generator holds 5 blocks apart through the whole carry (it prints this point for each carry pose).
		 */
		private static final float ROCK_X = -7.1f, ROCK_Y = 15.1f, ROCK_Z = 1.5f;
		/** Rock edge in model blocks (x MODEL_SCALE in the world: ~4.5 blocks). */
		private static final float ROCK_SIZE = 0.77f;

		HeldBoulderLayer(GeoRenderer<TitanEntity> renderer) {
			super(renderer);
		}

		@Override
		public void renderForBone(PoseStack poseStack, TitanEntity titan, GeoBone bone, RenderType renderType,
				MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
			if (!"hand_r".equals(bone.getName()) || titan.clientAnim() != TitanEntity.Anim.BOULDER || titan.isDeadOrDying()) {
				return;
			}
			float t = titan.tickCount + partialTick - titan.clientAnimStart() - TitanEntity.ANIM_TRANSITION;
			if (t < TitanEntity.BOULDER_SHOWN_FROM || t >= TitanEntity.BOULDER_SHOWN_TO) {
				return;
			}
			Block block = Block.byItem(TitanBoulderEntity.itemForGround(
					titan.level().getBlockState(titan.blockPosition().below())));
			BlockState state = block == Blocks.AIR ? Blocks.COBBLESTONE.defaultBlockState() : block.defaultBlockState();
			poseStack.pushPose();
			// GeckoLib bakes model x mirrored: json x -> -x
			poseStack.translate(-ROCK_X / 16.0f, ROCK_Y / 16.0f, ROCK_Z / 16.0f);
			poseStack.mulPose(Axis.YP.rotationDegrees(23.0f));
			poseStack.mulPose(Axis.XP.rotationDegrees(14.0f));
			// a little lumpy rather than a perfect crate, and it grows out of the ground over the first ticks
			float grow = Mth.clamp((t - TitanEntity.BOULDER_SHOWN_FROM) / 3.0f, 0.35f, 1.0f);
			poseStack.scale(ROCK_SIZE * grow, ROCK_SIZE * 0.86f * grow, ROCK_SIZE * 0.93f * grow);
			poseStack.translate(-0.5f, -0.5f, -0.5f);
			Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, bufferSource, packedLight,
					OverlayTexture.NO_OVERLAY);
			poseStack.popPose();
		}
	}
}
