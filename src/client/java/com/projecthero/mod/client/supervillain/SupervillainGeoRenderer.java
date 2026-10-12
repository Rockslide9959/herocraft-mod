package com.projecthero.mod.client.supervillain;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.frostgiant.FrostHaloLayer;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.event.entity.SupervillainVariant;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * v0.16.1: one Supervillain look -- the Chimera, the Arsenal or the Omega Mage -- drawn as its own GeckoLib model over the
 * {@link EmpoweredZombie} ({@code geo/supervillain/<variant>.geo.json}, texture + glowmask, animations; built by
 * {@code scratchpad/gen_supervillain_models.js}).
 * <ul>
 *   <li>Sized so the body (without horns, cannon, staff, crown ...) is {@code variant.scale() x 1.95} blocks tall -- the
 *       look the raid always intended; the hit-box is unchanged (smaller than the model, spec section 51).</li>
 *   <li>GeckoLib's auto-glow pass (core / reactor / crystal / eyes / runes) plus the Frost Giants' additive halo pass.</li>
 *   <li>The head follows the look target through the never-keyed {@code neck} bone, faded out during moves; the
 *       never-keyed {@code flinch} bone jolts on every hit.</li>
 *   <li>No vanilla tip-over when dying: the death clip is the collapse.</li>
 * </ul>
 */
public class SupervillainGeoRenderer extends GeoReplacedEntityRenderer<EmpoweredZombie, SupervillainAnimatable> {
	/** Bones left out of the rest-height fit (the generator's {@code ignoreFit}), so the BODY fills the target height. */
	private static final Set<String> FIT_IGNORE = Set.of("horns", "tail1", "ruff", "cannon", "antenna", "staff", "crown", "collar", "orbit",
			"shard_r", "shard_l", "spell_l");
	private static final Map<BakedGeoModel, Float> HEIGHTS = new WeakHashMap<>();

	private final SupervillainVariant variant;

	public SupervillainGeoRenderer(EntityRendererProvider.Context context, SupervillainVariant variant) {
		super(context, new Model(variant), new SupervillainAnimatable(variant));
		this.variant = variant;
		this.shadowRadius = 0.5f * variant.scale();
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
		addRenderLayer(new FrostHaloLayer<>(this, 0.8f));
	}

	@Override
	public void preRender(PoseStack poseStack, SupervillainAnimatable animatable, BakedGeoModel model, MultiBufferSource bufferSource,
			VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		if (!isReRender) {
			float s = variant.scale() * 1.95f / restHeight(model);
			poseStack.scale(s, s, s);
		}
	}

	@Override
	protected float getDeathMaxRotation(SupervillainAnimatable animatable) {
		return 0.0f;
	}

	@Override
	public int getPackedOverlay(SupervillainAnimatable animatable, float u, float partialTick) {
		EmpoweredZombie e = getCurrentEntity();
		boolean flash = e != null && e.hurtTime > 0 && !e.isDeadOrDying();
		return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(flash));
	}

	static float restHeight(BakedGeoModel model) {
		synchronized (HEIGHTS) {
			return HEIGHTS.computeIfAbsent(model, m -> {
				float[] mm = { Float.MAX_VALUE, -Float.MAX_VALUE };
				for (GeoBone b : m.topLevelBones()) {
					walk(b, mm);
				}
				float h = mm[1] - mm[0];
				return h > 0.01f && h < 1000f ? h : 1.0f;
			});
		}
	}

	private static void walk(GeoBone bone, float[] mm) {
		if (FIT_IGNORE.contains(bone.getName())) {
			return;
		}
		for (GeoCube cube : bone.getCubes()) {
			for (GeoQuad quad : cube.quads()) {
				if (quad == null) {
					continue;
				}
				for (GeoVertex v : quad.vertices()) {
					mm[0] = Math.min(mm[0], v.position().y());
					mm[1] = Math.max(mm[1], v.position().y());
				}
			}
		}
		for (GeoBone child : bone.getChildBones()) {
			walk(child, mm);
		}
	}

	static final class Model extends GeoModel<SupervillainAnimatable> {
		private final ResourceLocation geo;
		private final ResourceLocation texture;
		private final ResourceLocation animation;

		Model(SupervillainVariant v) {
			this.geo = ProjectHeroMod.id("geo/supervillain/" + v.id() + ".geo.json");
			this.texture = ProjectHeroMod.id("textures/entity/supervillain/" + v.id() + ".png");
			this.animation = ProjectHeroMod.id("animations/supervillain/" + v.id() + ".animation.json");
		}

		@Override
		public ResourceLocation getModelResource(SupervillainAnimatable animatable) {
			return geo;
		}

		@Override
		public ResourceLocation getTextureResource(SupervillainAnimatable animatable) {
			return texture;
		}

		@Override
		public ResourceLocation getAnimationResource(SupervillainAnimatable animatable) {
			return animation;
		}

		@Override
		public void setCustomAnimations(SupervillainAnimatable animatable, long instanceId, AnimationState<SupervillainAnimatable> state) {
			if (!(state.getData(DataTickets.ENTITY) instanceof EmpoweredZombie boss)) {
				return;
			}
			SupervillainAnimatable.State st = animatable.state(boss);
			GeoBone neck = getAnimationProcessor().getBone("neck");
			if (neck != null) {
				boolean move = boss.clientAnim != null && boss.tickCount - boss.clientAnimStart < boss.clientAnim.length();
				float target = boss.isDeadOrDying() || move ? 0.3f : 1.0f;
				st.headWeight += (target - st.headWeight) * 0.12f;
				EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
				float yaw = data == null ? 0.0f : Mth.clamp(data.netHeadYaw(), -50.0f, 50.0f);
				float pitch = data == null ? 0.0f : Mth.clamp(data.headPitch(), -30.0f, 30.0f);
				float w = boss.isDeadOrDying() ? 0f : st.headWeight;
				neck.setRotY(yaw * w * Mth.DEG_TO_RAD);
				neck.setRotX(pitch * w * Mth.DEG_TO_RAD);
			}
			GeoBone flinch = getAnimationProcessor().getBone("flinch");
			if (flinch != null) {
				float k = 0.0f;
				if (boss.hurtTime > 0 && !boss.isDeadOrDying()) {
					float t = (10.0f - boss.hurtTime + state.getPartialTick()) / 10.0f;
					k = Mth.sin(Mth.clamp(t, 0.0f, 1.0f) * Mth.PI) * (1.0f - t * 0.5f);
				}
				flinch.setRotX(-k * 7.0f * Mth.DEG_TO_RAD);
				flinch.setRotZ(k * 2.5f * Mth.DEG_TO_RAD);
			}
		}
	}
}
