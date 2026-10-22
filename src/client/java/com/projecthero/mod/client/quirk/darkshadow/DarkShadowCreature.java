package com.projecthero.mod.client.quirk.darkshadow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.Color;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtil;

/**
 * v0.16.7 (user) Quirk revamp: Dark Shadow as GeckoLib models, drawn WITHOUT an entity (one {@link Shape} animatable per
 * player, fed the synced Quirk state every frame by {@link DarkShadowRender} / {@link DarkShadowAnkhLayer}).
 * <ul>
 *   <li>{@link Shape} + {@link CreatureModel} + {@link CreatureRenderer}: the shadow spirit
 *       ({@code geo/quirk/dark_shadow.geo.json}, clips {@code animation.dark_shadow.*}, from
 *       {@code scratchpad/gen_dark_shadow_v0167.js}). Translucent (living shadow); the eyes, talon tips and berserk spike tips
 *       glow ({@code creature_glowmask.png}) -- red-tinted while berserk.</li>
 *   <li>{@link Shape} + {@link AnkhModel} + {@link AnkhRenderer}: the Black Ankh parts (helmet, plumes, gauntlets, back
 *       ridge, wings), each drawn on its own player limb ({@link #ankhOnly}).</li>
 * </ul>
 * The object renderer's block-centring is skipped ({@link CreatureRenderer#preRender}): the caller's pose is the model's
 * origin. Any bone a clip scales under 0.05 is hidden (the clips fold away the wings / spikes that way).
 */
public final class DarkShadowCreature {
	public static final ResourceLocation GEO = ProjectHeroMod.id("geo/quirk/dark_shadow.geo.json");
	public static final ResourceLocation ANIM = ProjectHeroMod.id("animations/quirk/dark_shadow.animation.json");
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/quirk/dark_shadow/creature.png");
	public static final ResourceLocation ANKH_GEO = ProjectHeroMod.id("geo/quirk/dark_shadow_ankh.geo.json");
	public static final ResourceLocation ANKH_ANIM = ProjectHeroMod.id("animations/quirk/dark_shadow_ankh.animation.json");
	public static final ResourceLocation ANKH_TEXTURE = ProjectHeroMod.id("textures/entity/quirk/dark_shadow/ankh_parts.png");

	/** Bones a clip may fold away (scale 0.01): hidden while under 0.05. */
	private static final List<String> FOLDING = List.of("float", "tail", "wings", "spikes", "spikes_mid", "head_spikes", "neck1", "head");
	private static final List<String> ANKH_FOLDING = List.of("wings");

	private static final Map<String, RawAnimation> RAW = new HashMap<>();

	private DarkShadowCreature() {
	}

	static RawAnimation raw(String prefix, String clip) {
		return RAW.computeIfAbsent(prefix + clip, k -> RawAnimation.begin().thenPlay("animation." + prefix + "." + clip));
	}

	/** One player's creature (or Ankh parts): the clip to play and the per-frame look / tint, set by the caller. */
	public static final class Shape implements GeoAnimatable {
		private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
		private final String prefix;
		/** The clip, and a key that changes whenever a one-shot clip must restart (the synced animStart). */
		String clip = "idle";
		long clipKey;
		private long playingKey = Long.MIN_VALUE;
		private String playingClip = "";
		/** The look bone (radians). */
		float lookPitch, lookYaw;
		boolean berserk;
		int alpha = 255;
		/** Client-side smoothing: its size and facing. */
		float size = -1f;
		float yaw = Float.NaN;
		long lastFrameNanos;

		Shape(String prefix) {
			this.prefix = prefix;
		}

		@Override
		public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
			controllers.add(new AnimationController<>(this, "main", 3, this::predicate));
		}

		private PlayState predicate(AnimationState<Shape> state) {
			if (!clip.equals(playingClip) || clipKey != playingKey) {
				boolean sameClip = clip.equals(playingClip);
				playingClip = clip;
				playingKey = clipKey;
				if (sameClip) {
					state.getController().forceAnimationReset(); // the same one-shot again: restart it
				}
			}
			return state.setAndContinue(raw(prefix, clip));
		}

		@Override
		public AnimatableInstanceCache getAnimatableInstanceCache() {
			return cache;
		}

		@Override
		public double getTick(Object object) {
			return RenderUtil.getCurrentTick();
		}

		@Override
		public boolean shouldPlayAnimsWhileGamePaused() {
			return false;
		}
	}

	private static void foldTiny(GeoModel<Shape> model, List<String> names) {
		for (String n : names) {
			model.getBone(n).ifPresent(b -> b.setHidden(b.getScaleX() < 0.05f));
		}
	}

	// ================================================================ the creature

	public static final class CreatureModel extends GeoModel<Shape> {
		@Override
		public ResourceLocation getModelResource(Shape animatable) {
			return GEO;
		}

		@Override
		public ResourceLocation getTextureResource(Shape animatable) {
			return TEXTURE;
		}

		@Override
		public ResourceLocation getAnimationResource(Shape animatable) {
			return ANIM;
		}

		@Override
		public RenderType getRenderType(Shape animatable, ResourceLocation texture) {
			return RenderType.entityTranslucent(texture);
		}

		@Override
		public void setCustomAnimations(Shape animatable, long instanceId, AnimationState<Shape> state) {
			getBone("look").ifPresent(b -> {
				b.setRotX(animatable.lookPitch);
				b.setRotY(animatable.lookYaw);
			});
			// unhide parents before children re-hide themselves
			getBone("float").ifPresent(b -> b.setHidden(false));
			foldTiny(this, FOLDING);
		}
	}

	public static final class CreatureRenderer extends GeoObjectRenderer<Shape> {
		public CreatureRenderer() {
			super(new CreatureModel());
			addRenderLayer(new EyesLayer(this));
		}

		@Override
		public void preRender(PoseStack poseStack, Shape animatable, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
				@Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
			// no block-centring: the caller's pose is the model's origin (also safe for the glow layer's re-render)
			this.objectRenderTranslations = new Matrix4f(poseStack.last().pose());
		}

		@Override
		public Color getRenderColor(Shape animatable, float partialTick, int packedLight) {
			return animatable.berserk ? Color.ofARGB(animatable.alpha, 255, 196, 206) : Color.ofARGB(animatable.alpha, 255, 255, 255);
		}
	}

	/** The glowing eyes / talon tips: full-bright, red-tinted while berserk. */
	static final class EyesLayer extends GeoRenderLayer<Shape> {
		EyesLayer(GeoRenderer<Shape> renderer) {
			super(renderer);
		}

		@Override
		public void render(PoseStack poseStack, Shape animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
				MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
			RenderType glow = AutoGlowingTexture.getRenderType(TEXTURE);
			int colour = animatable.berserk ? 0xFFFF3A30 : 0xFFFFFFFF;
			getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick,
					LightTexture.FULL_BRIGHT, packedOverlay, colour);
		}
	}

	// ================================================================ the Black Ankh parts

	/** Which top-level parts the next Ankh draw shows (one limb at a time). */
	static Set<String> ankhOnly = Set.of();

	public static final class AnkhModel extends GeoModel<Shape> {
		@Override
		public ResourceLocation getModelResource(Shape animatable) {
			return ANKH_GEO;
		}

		@Override
		public ResourceLocation getTextureResource(Shape animatable) {
			return ANKH_TEXTURE;
		}

		@Override
		public ResourceLocation getAnimationResource(Shape animatable) {
			return ANKH_ANIM;
		}

		@Override
		public RenderType getRenderType(Shape animatable, ResourceLocation texture) {
			return RenderType.entityCutoutNoCull(texture);
		}
	}

	public static final class AnkhRenderer extends GeoObjectRenderer<Shape> {
		public AnkhRenderer() {
			super(new AnkhModel());
			addRenderLayer(new GeoRenderLayer<>(this) {
				@Override
				public void render(PoseStack poseStack, Shape animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
						MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
					RenderType glow = AutoGlowingTexture.getRenderType(ANKH_TEXTURE);
					getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick,
							LightTexture.FULL_BRIGHT, packedOverlay, 0xFFFFFFFF);
				}
			});
		}

		@Override
		public void preRender(PoseStack poseStack, Shape animatable, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
				@Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
			this.objectRenderTranslations = new Matrix4f(poseStack.last().pose());
			for (GeoBone root : model.topLevelBones()) {
				root.setHidden(false);
				for (GeoBone part : root.getChildBones()) {
					boolean show = ankhOnly.contains(part.getName());
					part.setHidden(!show);
					if (show && ANKH_FOLDING.contains(part.getName()) && part.getScaleX() < 0.05f) {
						part.setHidden(true);
					}
				}
			}
		}
	}
}
