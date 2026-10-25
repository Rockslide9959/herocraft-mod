package com.projecthero.mod.client.galactus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.frostgiant.FrostHaloLayer;
import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.entity.GalactusEntity;
import com.projecthero.mod.galactus.entity.GalactusHandEntity;
import com.projecthero.mod.galactus.entity.GalactusMob;
import com.projecthero.mod.galactus.entity.PowerPylonEntity;
import com.projecthero.mod.galactus.entity.SilverSurferEntity;
import com.projecthero.mod.galactus.entity.SurferBoardEntity;
import com.projecthero.mod.galactus.entity.WorldshipEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/**
 * v0.16.1: Galactus: The Devourer on the client. Every model is a GeckoLib asset at {@code geo/galactus/<name>.geo.json}
 * (+ texture, glowmask, animations), drawn at a size measured from its rest pose ({@link GalactusModelFit}: the placeholders
 * today, the real models later, no code change): Galactus to {@link GalactusConfig.Colossus#renderHeight} (~60 blocks,
 * render only -- his hit-box is far smaller), the Surfer / Punishers / converters to their hit-box height, the grabbing
 * hand to 9 blocks, the Worldship to 40. Heads follow their look target through the never-keyed {@code neck} / {@code head}
 * bones; hurt flinches go on {@code flinch}. The weak points draw nothing (the server marks them with particles); the Power
 * Pylons are drawn with blocks.
 */
public final class GalactusRenderers {
	private GalactusRenderers() {
	}

	/** v0.16.7 (perf): asked for every frame by the renderers -- built once per name. */
	private static final java.util.Map<String, ResourceLocation> GEO_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	public static ResourceLocation geo(String model) {
		return GEO_CACHE.computeIfAbsent(model, k -> ProjectHeroMod.id("geo/galactus/" + k + ".geo.json"));
	}

	/** v0.16.7 (perf): asked for every frame by the renderers -- built once per name. */
	private static final java.util.Map<String, ResourceLocation> TEXTURE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	public static ResourceLocation texture(String model) {
		return TEXTURE_CACHE.computeIfAbsent(model, k -> ProjectHeroMod.id("textures/entity/galactus/" + k + ".png"));
	}

	/** v0.16.7 (perf): asked for every frame by the renderers -- built once per name. */
	private static final java.util.Map<String, ResourceLocation> ANIMATION_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

	public static ResourceLocation animation(String model) {
		return ANIMATION_CACHE.computeIfAbsent(model, k -> ProjectHeroMod.id("animations/galactus/" + k + ".animation.json"));
	}

	/** A model at a fixed asset name. */
	public static final class Fixed<T extends GeoAnimatable> extends GeoModel<T> {
		private final String name;

		public Fixed(String name) {
			this.name = name;
		}

		@Override
		public ResourceLocation getModelResource(T animatable) {
			return geo(name);
		}

		@Override
		public ResourceLocation getTextureResource(T animatable) {
			return texture(name);
		}

		@Override
		public ResourceLocation getAnimationResource(T animatable) {
			return animation(name);
		}
	}

	/** The per-entity model: its own asset name, the head look and the hurt flinch. */
	static final class MobModel<T extends GalactusMob> extends GeoModel<T> {
		@Override
		public ResourceLocation getModelResource(T animatable) {
			return geo(animatable.model());
		}

		@Override
		public ResourceLocation getTextureResource(T animatable) {
			return texture(animatable.model());
		}

		@Override
		public ResourceLocation getAnimationResource(T animatable) {
			return animation(animatable.model());
		}

		@Override
		public void setCustomAnimations(T mob, long instanceId, AnimationState<T> state) {
			EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
			float yaw = data == null ? 0.0f : Mth.clamp(data.netHeadYaw(), -50.0f, 50.0f);
			float pitch = data == null ? 0.0f : Mth.clamp(data.headPitch(), -30.0f, 30.0f);
			float w = mob.isDeadOrDying() ? 0f : mob.clientClip() != null ? 0.35f : 1.0f;
			GeoBone neck = getAnimationProcessor().getBone("neck");
			if (neck != null) {
				neck.setRotY(yaw * w * 0.5f * Mth.DEG_TO_RAD);
				neck.setRotX(pitch * w * 0.5f * Mth.DEG_TO_RAD);
			}
			GeoBone head = getAnimationProcessor().getBone("head");
			if (head != null) {
				head.setRotY(yaw * w * 0.5f * Mth.DEG_TO_RAD);
				head.setRotX(pitch * w * 0.5f * Mth.DEG_TO_RAD);
			}
			GeoBone flinch = getAnimationProcessor().getBone("flinch");
			if (flinch != null) {
				float k = 0.0f;
				if (mob.hurtTime > 0 && !mob.isDeadOrDying()) {
					float t = (10.0f - mob.hurtTime + state.getPartialTick()) / 10.0f;
					k = Mth.sin(Mth.clamp(t, 0.0f, 1.0f) * Mth.PI) * (1.0f - t * 0.5f);
				}
				flinch.setRotX(k * 6.0f * Mth.DEG_TO_RAD);
				flinch.setRotZ(k * 2.0f * Mth.DEG_TO_RAD);
			}
		}
	}

	// ---------------------------------------------------------------- the mobs

	public static class Mob<T extends GalactusMob> extends GeoEntityRenderer<T> {
		private final float fixedHeight;

		/** {@code fixedHeight} &lt;= 0: fit to the hit-box height. */
		public Mob(EntityRendererProvider.Context context, float shadow, float fixedHeight) {
			super(context, new MobModel<>());
			this.shadowRadius = shadow;
			this.fixedHeight = fixedHeight;
			addRenderLayer(new AutoGlowingGeoLayer<>(this));
			addRenderLayer(new FrostHaloLayer<>(this, 0.55f)); // additive bloom + the halo shells around the glowing parts
		}

		protected float targetHeight(T animatable) {
			return fixedHeight > 0 ? fixedHeight : animatable.getBbHeight();
		}

		@Override
		public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z) {
			// drawn taller than its hit-box (the converter): don't let the small box cull the big model
			return fixedHeight > entity.getBbHeight() + 1.0f || super.shouldRender(entity, frustum, x, y, z);
		}

		@Override
		public void preRender(PoseStack poseStack, T animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
				boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
			super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
			if (!isReRender) {
				float s = GalactusModelFit.fit(model, targetHeight(animatable));
				poseStack.scale(s, s, s);
			}
		}

		@Override
		protected float getDeathMaxRotation(T animatable) {
			return 0.0f;
		}

		@Override
		public int getPackedOverlay(T animatable, float u, float partialTick) {
			boolean flash = animatable.hurtTime > 0 && !animatable.isDeadOrDying();
			return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(flash));
		}
	}

	public static final class Colossus extends Mob<GalactusEntity> {
		public Colossus(EntityRendererProvider.Context context) {
			super(context, 6.0f, 0f);
		}

		@Override
		protected float targetHeight(GalactusEntity animatable) {
			return (float) GalactusConfig.galactus().renderHeight;
		}

		@Override
		public boolean shouldRender(GalactusEntity entity, Frustum frustum, double x, double y, double z) {
			return true; // far bigger than his hit-box: never cull him
		}

		@Override
		protected int getBlockLightLevel(GalactusEntity e, BlockPos pos) {
			return 12;
		}
	}

	// ---------------------------------------------------------------- the Silver Surfer: a player model on his board

	public static final ResourceLocation SURFER_SKIN = ProjectHeroMod.id("textures/entity/galactus/silver_surfer.png");
	public static final ResourceLocation SURFER_GLOW = ProjectHeroMod.id("textures/entity/galactus/silver_surfer_glowmask.png");
	/** The board's thickness (blocks): he stands this high on it. */
	public static final float BOARD_TOP = 0.25f;

	/**
	 * User decision (v0.16.1): the Herald is the standard <b>player rig</b> (wide arms -- his skin is a Steve-shape skin; head,
	 * body, arms, legs and the hat / jacket / sleeve / pants layers) with code-driven poses instead of GeckoLib clips:
	 * the surf stance (feet apart along the board, knees soft, arms out for balance), blast (the right arm thrown forward
	 * at the target), dash (crouched, arms swept back), shield (both forearms crossed in front), lift (both arms raised), the
	 * turn (head bowed, then the right hand lifted) and the ally's easy stance.
	 */
	public static final class SurferModel extends net.minecraft.client.model.PlayerModel<SilverSurferEntity> {
		public SurferModel(net.minecraft.client.model.geom.ModelPart root) {
			super(root, false);
		}

		@Override
		public void setupAnim(SilverSurferEntity s, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
			super.setupAnim(s, 0f, 0f, ageInTicks, netHeadYaw, headPitch);
			float pt = ageInTicks - s.tickCount;
			var pose = s.pose();
			float age = s.poseAge(pt);
			float bob = Mth.sin(ageInTicks * 0.08f);
			// the surf stance: left foot forward, right foot back, knees soft, arms out
			this.leftLeg.xRot = -0.38f;
			this.rightLeg.xRot = 0.32f;
			this.leftLeg.zRot = -0.06f;
			this.rightLeg.zRot = 0.06f;
			this.body.xRot = 0.08f;
			this.leftArm.xRot = -0.1f;
			this.rightArm.xRot = -0.1f;
			this.leftArm.zRot = -0.75f - bob * 0.06f;
			this.rightArm.zRot = 0.75f + bob * 0.06f;
			this.leftArm.yRot = 0f;
			this.rightArm.yRot = 0f;
			if (pose == null || pose == com.projecthero.mod.galactus.GalactusAnims.SURFER_IDLE || pose == com.projecthero.mod.galactus.GalactusAnims.SURFER_FLY) {
				// as authored
			} else if (pose == com.projecthero.mod.galactus.GalactusAnims.SURFER_ALLY_IDLE) {
				this.rightArm.zRot = 0.35f;
				this.rightArm.xRot = -0.5f + bob * 0.05f;
			} else if (pose == com.projecthero.mod.galactus.GalactusAnims.SURFER_BLAST) {
				float k = Mth.clamp(age / 8f, 0f, 1f);
				this.rightArm.xRot = Mth.lerp(k, -0.1f, -Mth.HALF_PI + headPitch * Mth.DEG_TO_RAD);
				this.rightArm.yRot = this.head.yRot;
				this.rightArm.zRot = Mth.lerp(k, 0.75f, 0f);
				this.leftArm.xRot = Mth.lerp(k, -0.1f, 0.5f);
			} else if (pose == com.projecthero.mod.galactus.GalactusAnims.SURFER_DASH) {
				float k = Mth.clamp(age / 10f, 0f, 1f);
				this.body.xRot = Mth.lerp(k, 0.08f, 0.45f);
				this.leftArm.xRot = this.rightArm.xRot = Mth.lerp(k, -0.1f, 1.1f);
				this.leftArm.zRot = -0.25f;
				this.rightArm.zRot = 0.25f;
				this.leftLeg.xRot = -0.6f;
				this.rightLeg.xRot = 0.5f;
			} else if (pose == com.projecthero.mod.galactus.GalactusAnims.SURFER_SHIELD) {
				float k = Mth.clamp(age / 6f, 0f, 1f);
				this.leftArm.xRot = this.rightArm.xRot = Mth.lerp(k, -0.1f, -1.45f);
				this.leftArm.yRot = Mth.lerp(k, 0f, 0.55f);
				this.rightArm.yRot = Mth.lerp(k, 0f, -0.55f);
				this.leftArm.zRot = Mth.lerp(k, -0.75f, 0f);
				this.rightArm.zRot = Mth.lerp(k, 0.75f, 0f);
			} else if (pose == com.projecthero.mod.galactus.GalactusAnims.SURFER_LIFT) {
				float k = Mth.clamp(age / 16f, 0f, 1f);
				this.leftArm.xRot = this.rightArm.xRot = Mth.lerp(k, -0.6f, -2.9f);
				this.leftArm.zRot = -0.3f;
				this.rightArm.zRot = 0.3f;
			} else if (pose == com.projecthero.mod.galactus.GalactusAnims.SURFER_TURN_ALLY) {
				// he lowers his arms and bows his head; then lifts his right hand, open, to the fighters
				float k = Mth.clamp(age / 30f, 0f, 1f);
				float r = Mth.clamp((age - 60f) / 30f, 0f, 1f);
				this.leftArm.zRot = Mth.lerp(k, -0.75f, -0.08f);
				this.rightArm.zRot = Mth.lerp(k, 0.75f, 0.08f);
				this.head.xRot = Mth.lerp(k, this.head.xRot, 0.55f) * (1f - r) + r * 0.0f;
				this.rightArm.xRot = Mth.lerp(r, -0.1f, -1.9f);
				this.leftLeg.xRot = Mth.lerp(k, -0.38f, -0.12f);
				this.rightLeg.xRot = Mth.lerp(k, 0.32f, 0.12f);
			}
			// the outer layers follow the posed limbs
			this.leftSleeve.copyFrom(this.leftArm);
			this.rightSleeve.copyFrom(this.rightArm);
			this.leftPants.copyFrom(this.leftLeg);
			this.rightPants.copyFrom(this.rightLeg);
			this.jacket.copyFrom(this.body);
			this.hat.copyFrom(this.head);
		}
	}

	/** The board's GeckoLib animatable (one shared client-side instance: every board idles the same way). */
	public static final class BoardAnimatable implements software.bernie.geckolib.animatable.GeoAnimatable {
		public static final BoardAnimatable INSTANCE = new BoardAnimatable();
		private final software.bernie.geckolib.animatable.instance.AnimatableInstanceCache cache = software.bernie.geckolib.util.GeckoLibUtil
				.createInstanceCache(this);

		@Override
		public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
			controllers.add(new software.bernie.geckolib.animation.AnimationController<BoardAnimatable>(this, "main", 0,
					state -> state.setAndContinue(GalactusMob.raw(com.projecthero.mod.galactus.GalactusAnims.BOARD_IDLE))));
		}

		@Override
		public software.bernie.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() {
			return cache;
		}

		@Override
		public double getTick(Object object) {
			Minecraft mc = Minecraft.getInstance();
			return mc.level == null ? 0 : mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(true);
		}
	}

	public static final class BoardObjectRenderer extends software.bernie.geckolib.renderer.GeoObjectRenderer<BoardAnimatable> {
		public BoardObjectRenderer() {
			super(new Fixed<>(com.projecthero.mod.galactus.GalactusAnims.BOARD));
			addRenderLayer(new AutoGlowingGeoLayer<>(this));
			addRenderLayer(new FrostHaloLayer<>(this, 0.55f)); // additive bloom + the halo shells around the glowing parts
		}

		/**
		 * v0.16.4 (user: "a weird second board next to him"): GeckoLib's GeoObjectRenderer.preRender centres the model on a
		 * block (+0.5, +0.51, +0.5) unconditionally -- and the glow / halo layers re-render through preRender on a pose stack
		 * that already carries that offset, so the glowing copy of the board was drawn half a block up and to the side. A
		 * re-render takes the offset back off.
		 */
		@Override
		public void preRender(PoseStack poseStack, BoardAnimatable animatable, BakedGeoModel model, MultiBufferSource bufferSource,
				VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
			super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
			if (isReRender) {
				poseStack.translate(-0.5f, -0.51f, -0.5f);
			}
		}
	}

	public static final class Surfer extends net.minecraft.client.renderer.entity.MobRenderer<SilverSurferEntity, SurferModel> {
		private final BoardObjectRenderer board = new BoardObjectRenderer();

		public Surfer(EntityRendererProvider.Context context) {
			super(context, new SurferModel(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER)), 0.5f);
			addLayer(new com.projecthero.mod.client.render.raid.RaidPlayerRenderers.GlowLayer<>(this, s -> SURFER_GLOW));
		}

		@Override
		public ResourceLocation getTextureLocation(SilverSurferEntity s) {
			return SURFER_SKIN;
		}

		@Override
		public void render(SilverSurferEntity s, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
			// the board under his feet, turned with his body
			pose.pushPose();
			float bodyYaw = Mth.rotLerp(pt, s.yBodyRotO, s.yBodyRot);
			pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f - bodyYaw));
			if (s.isTurning() || s.pose() == com.projecthero.mod.galactus.GalactusAnims.SURFER_DASH) {
				pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(s.isTurning() ? 0f : -8f));
			}
			pose.translate(-0.5, -0.51, -0.5); // GeoObjectRenderer centres on a block
			board.render(pose, BoardAnimatable.INSTANCE, buffers, null, null, Math.max(light, LightTexture.pack(10, 10)), pt);
			pose.popPose();
			pose.pushPose();
			pose.translate(0, BOARD_TOP, 0);
			super.render(s, yaw, pt, pose, buffers, light);
			pose.popPose();
		}

		@Override
		protected void scale(SilverSurferEntity s, PoseStack pose, float pt) {
			pose.scale(0.9375f, 0.9375f, 0.9375f); // the player renderer's own scale
		}

		@Override
		protected boolean isShaking(SilverSurferEntity s) {
			return s.shieldUp() && s.tickCount % 8 < 2;
		}

		@Override
		protected int getBlockLightLevel(SilverSurferEntity e, BlockPos pos) {
			return Math.max(10, super.getBlockLightLevel(e, pos));
		}
	}

	public static final class Hand extends Mob<GalactusHandEntity> {
		public Hand(EntityRendererProvider.Context context) {
			super(context, 0f, 9.0f);
		}

		@Override
		public boolean shouldRender(GalactusHandEntity entity, Frustum frustum, double x, double y, double z) {
			return true;
		}
	}

	// ---------------------------------------------------------------- the Worldship

	public static final class Worldship extends GeoEntityRenderer<WorldshipEntity> {
		public Worldship(EntityRendererProvider.Context context) {
			super(context, new Fixed<>(com.projecthero.mod.galactus.GalactusAnims.WORLDSHIP));
			this.shadowRadius = 0f;
			addRenderLayer(new AutoGlowingGeoLayer<>(this));
			addRenderLayer(new FrostHaloLayer<>(this, 0.55f)); // additive bloom + the halo shells around the glowing parts
		}

		@Override
		public void preRender(PoseStack poseStack, WorldshipEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
				VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
			super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
			if (!isReRender) {
				float s = GalactusModelFit.fit(model, 40f);
				poseStack.scale(s, s, s);
			}
		}

		@Override
		public boolean shouldRender(WorldshipEntity entity, Frustum frustum, double x, double y, double z) {
			return true;
		}

		@Override
		protected int getBlockLightLevel(WorldshipEntity e, BlockPos pos) {
			return 11;
		}
	}

	// ---------------------------------------------------------------- the board (raw model scale: 16 px = 1 block)

	public static final class Board extends GeoEntityRenderer<SurferBoardEntity> {
		public Board(EntityRendererProvider.Context context) {
			super(context, new Fixed<>(com.projecthero.mod.galactus.GalactusAnims.BOARD));
			this.shadowRadius = 0.4f;
			addRenderLayer(new AutoGlowingGeoLayer<>(this));
			addRenderLayer(new FrostHaloLayer<>(this, 0.55f)); // additive bloom + the halo shells around the glowing parts
		}

		@Override
		protected int getBlockLightLevel(SurferBoardEntity e, BlockPos pos) {
			return Math.max(8, super.getBlockLightLevel(e, pos));
		}
	}

	// ---------------------------------------------------------------- the weak points: nothing (particles mark them)

	public static final class Invisible<T extends Entity> extends EntityRenderer<T> {
		public Invisible(EntityRendererProvider.Context context) {
			super(context);
			this.shadowRadius = 0f;
		}

		@Override
		public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z) {
			return false;
		}

		@Override
		public ResourceLocation getTextureLocation(T entity) {
			return InventoryMenu.BLOCK_ATLAS;
		}
	}

	// ---------------------------------------------------------------- the Power Pylon: crying obsidian, violet glass, amethyst

	public static final class Pylon extends EntityRenderer<PowerPylonEntity> {
		public Pylon(EntityRendererProvider.Context context) {
			super(context);
			this.shadowRadius = 0.8f;
		}

		@Override
		public void render(PowerPylonEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
			var blocks = Minecraft.getInstance().getBlockRenderer();
			float h = e.getBbHeight();
			pose.pushPose();
			pose.translate(-0.7, 0, -0.7);
			pose.scale(1.4f, 1.2f, 1.4f);
			blocks.renderSingleBlock(Blocks.CRYING_OBSIDIAN.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			float pulse = 1f + 0.05f * Mth.sin((e.tickCount + pt) * 0.3f);
			pose.pushPose();
			pose.translate(-0.4 * pulse, 1.2, -0.4 * pulse);
			pose.scale(0.8f * pulse, h - 2.2f, 0.8f * pulse);
			blocks.renderSingleBlock(Blocks.PURPLE_STAINED_GLASS.defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			pose.pushPose();
			pose.translate(-0.15, 1.2, -0.15);
			pose.scale(0.3f, h - 2.2f, 0.3f);
			blocks.renderSingleBlock(Blocks.AMETHYST_BLOCK.defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			pose.pushPose();
			pose.translate(-0.5, h - 1.0, -0.5);
			blocks.renderSingleBlock(Blocks.AMETHYST_CLUSTER.defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			super.render(e, yaw, pt, pose, buffers, light);
		}

		@Override
		public ResourceLocation getTextureLocation(PowerPylonEntity e) {
			return InventoryMenu.BLOCK_ATLAS;
		}

		@Override
		protected int getBlockLightLevel(PowerPylonEntity e, BlockPos pos) {
			return 15;
		}
	}
}
