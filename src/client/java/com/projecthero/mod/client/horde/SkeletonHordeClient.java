package com.projecthero.mod.client.horde;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.horde.entity.BoneTyrant;
import com.projecthero.mod.horde.entity.HordeEntityTypes;
import com.projecthero.mod.horde.entity.skeleton.BlastArrow;
import com.projecthero.mod.horde.entity.skeleton.HordeSkeleton;
import com.projecthero.mod.horde.entity.skeleton.SkeletonHordeEntityTypes;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;

import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * v0.14.16: the Skeleton Horde's renderers -- the six horde skeletons on vanilla's skeleton mesh with their own recoloured
 * textures ({@code textures/entity/horde_skeleton/}; the Runner and the Brute are resized by their SCALE attribute), the
 * Bone Bomber's burning arrow, and the rebuilt Bone Tyrant through GeckoLib (his eyes, soul core, crown gem, palm flame
 * and blade runes glow in the dark through {@code bone_tyrant_glowmask.png}).
 */
public final class SkeletonHordeClient {
	private SkeletonHordeClient() {
	}

	public static void initialize() {
		skeleton(SkeletonHordeEntityTypes.BONE_RUNNER, "bone_runner");
		skeleton(SkeletonHordeEntityTypes.BONE_KNIGHT, "bone_knight");
		skeleton(SkeletonHordeEntityTypes.BLIGHT_ARCHER, "blight_archer");
		skeleton(SkeletonHordeEntityTypes.BONE_BOMBER, "bone_bomber");
		skeleton(SkeletonHordeEntityTypes.BONE_BRUTE, "bone_brute");
		skeleton(SkeletonHordeEntityTypes.NECROMANCER, "necromancer");
		EntityRendererRegistry.register(SkeletonHordeEntityTypes.BLAST_ARROW, BlastArrowRenderer::new);
		EntityRendererRegistry.register(HordeEntityTypes.BONE_TYRANT, BoneTyrantRenderer::new);
	}

	private static <T extends HordeSkeleton> void skeleton(EntityType<T> type, String texture) {
		ResourceLocation tex = ProjectHeroMod.id("textures/entity/horde_skeleton/" + texture + ".png");
		EntityRendererRegistry.register(type, context -> new SkeletonRenderer<T>(context) {
			@Override
			public ResourceLocation getTextureLocation(T entity) {
				return tex;
			}
		});
	}

	static final class BlastArrowRenderer extends ArrowRenderer<BlastArrow> {
		private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");

		BlastArrowRenderer(EntityRendererProvider.Context context) {
			super(context);
		}

		@Override
		public ResourceLocation getTextureLocation(BlastArrow entity) {
			return TEXTURE;
		}
	}

	/** The Bone Tyrant: the geo model is 2 blocks tall; GeckoLib applies his SCALE attribute (3.5) itself. */
	public static final class BoneTyrantRenderer extends GeoEntityRenderer<BoneTyrant> {
		public BoneTyrantRenderer(EntityRendererProvider.Context context) {
			super(context, new Model());
			this.shadowRadius = 0.6f;
			addRenderLayer(new AutoGlowingGeoLayer<>(this));
		}
	}

	static final class Model extends GeoModel<BoneTyrant> {
		private static final ResourceLocation MODEL = ProjectHeroMod.id("geo/bone_tyrant.geo.json");
		private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/bone_tyrant.png");
		private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/bone_tyrant.animation.json");

		@Override
		public ResourceLocation getModelResource(BoneTyrant animatable) {
			return MODEL;
		}

		@Override
		public ResourceLocation getTextureResource(BoneTyrant animatable) {
			return TEXTURE;
		}

		@Override
		public ResourceLocation getAnimationResource(BoneTyrant animatable) {
			return ANIMATION;
		}

		/**
		 * While he isn't mid-attack his skull turns to follow his gaze. Yaw only, and set (not added): the idle and walk
		 * clips never turn the head sideways, and adding to a bone's rotation every frame accumulates whenever a clip
		 * stops touching it (the Hulk lesson).
		 */
		@Override
		public void setCustomAnimations(BoneTyrant animatable, long instanceId, AnimationState<BoneTyrant> state) {
			super.setCustomAnimations(animatable, instanceId, state);
			if (animatable.isBusy() || animatable.isDeadOrDying()) {
				return;
			}
			GeoBone head = getAnimationProcessor().getBone("head");
			EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
			if (head != null && data != null) {
				head.setRotY(Mth.clamp(data.netHeadYaw(), -45f, 45f) * Mth.DEG_TO_RAD);
			}
		}
	}
}
