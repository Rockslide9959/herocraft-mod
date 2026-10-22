package com.projecthero.mod.client.ironman;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.ironman.HulkbusterParts;
import com.projecthero.mod.ironman.IronManDamageTiers;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * v0.16.7 (user) Hulkbuster "Veronica" overhaul: drawing the six parts of the Hulkbuster body
 * ({@link HulkbusterParts}) each at its own damage.
 *
 * <p>Every bone belongs to one part (its nearest ancestor among {@code head}, {@code shoulder_l/r}, {@code thigh_l/r},
 * else the torso). A part's damage picks a battle-damage tier ({@link IronManDamageTiers}, the same nine procedural tiers
 * every Iron Man suit uses: grime, scratches, chips, dents, cracks, then missing plates showing the hot inner frame at
 * tier 6+), generated from the Hulkbuster body texture by {@link IronManBattleDamage#hulkbusterBody}. Because each tier
 * only adds to the one before, a part repairing (its damage falling) visibly clears texel by texel.
 *
 * <p>How: the body's normal pass draws only the CLEAN visible parts (bones of the other parts are hidden, children kept);
 * the glow layer follows the same flags; then {@link Layer} re-renders the model once per damage tier in use, showing
 * only the parts at that tier, with that tier's damaged texture and glowmask. {@link #visible} also hides parts that are
 * not there at all (the mid-air build: pieces not yet arrived).
 */
public final class HulkbusterPartRender {
	private static final Map<String, Integer> PART_OF = new HashMap<>();

	private HulkbusterPartRender() {
	}

	/** Which part {@code bone} belongs to. */
	public static int partOf(GeoBone bone) {
		Integer cached = PART_OF.get(bone.getName());
		if (cached != null) {
			return cached;
		}
		int part = HulkbusterParts.TORSO;
		for (GeoBone b = bone; b != null; b = b.getParent()) {
			Integer p = rootPart(b.getName());
			if (p != null) {
				part = p;
				break;
			}
		}
		PART_OF.put(bone.getName(), part);
		return part;
	}

	private static Integer rootPart(String name) {
		return switch (name) {
			case "head", "neck" -> HulkbusterParts.HEAD;
			case "shoulder_l" -> HulkbusterParts.LARM;
			case "shoulder_r" -> HulkbusterParts.RARM;
			case "thigh_l" -> HulkbusterParts.LLEG;
			case "thigh_r" -> HulkbusterParts.RLEG;
			case "chest", "hips", "waist", "root", "flinch" -> HulkbusterParts.TORSO;
			default -> null;
		};
	}

	/** The root bone name of {@code part} (for the build's flying pieces). */
	public static String rootBone(int part) {
		return switch (part) {
			case HulkbusterParts.HEAD -> "head";
			case HulkbusterParts.LARM -> "shoulder_l";
			case HulkbusterParts.RARM -> "shoulder_r";
			case HulkbusterParts.LLEG -> "thigh_l";
			case HulkbusterParts.RLEG -> "thigh_r";
			default -> "chest";
		};
	}

	/** Damage tier (0 clean .. 9) per part. */
	public static int[] tiers(float[] eff) {
		int[] t = new int[HulkbusterParts.COUNT];
		for (int i = 0; i < t.length; i++) {
			t[i] = eff == null ? 0 : IronManDamageTiers.tier(1f - eff[i]);
		}
		return t;
	}

	/** Hide every bone whose part is not in {@code showMask} (children are always walked). */
	public static void show(BakedGeoModel model, int showMask) {
		for (GeoBone b : model.topLevelBones()) {
			show(b, showMask);
		}
	}

	private static void show(GeoBone bone, int mask) {
		bone.setHidden((mask & (1 << partOf(bone))) == 0);
		bone.setChildrenHidden(false);
		for (GeoBone c : bone.getChildBones()) {
			show(c, mask);
		}
	}

	/** The parts drawn in the base pass: visible and clean. */
	public static int baseMask(int[] tiers, int visible) {
		int m = 0;
		for (int i = 0; i < HulkbusterParts.COUNT; i++) {
			if ((visible & (1 << i)) != 0 && tiers[i] == 0) {
				m |= 1 << i;
			}
		}
		return m;
	}

	public static final int ALL = (1 << HulkbusterParts.COUNT) - 1;

	/** What a renderer tells the layer about the body it is drawing this frame. */
	public interface View {
		/** Per-part tiers of the body being drawn (null = clean). */
		@Nullable
		int[] partTiers();

		/** Bit mask of the parts that are there at all. */
		int visibleParts();
	}

	/** The per-tier damaged passes. Restores the base-pass flags afterwards. */
	public static final class Layer<T extends GeoAnimatable> extends GeoRenderLayer<T> {
		private final View view;

		public Layer(GeoRenderer<T> renderer, View view) {
			super(renderer);
			this.view = view;
		}

		@Override
		public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
				MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
			int[] tiers = view.partTiers();
			if (tiers == null) {
				return;
			}
			int visible = view.visibleParts();
			boolean drew = false;
			for (int tier = 1; tier <= IronManDamageTiers.MAX_TIER; tier++) {
				int mask = 0;
				for (int i = 0; i < HulkbusterParts.COUNT; i++) {
					if (tiers[i] == tier && (visible & (1 << i)) != 0) {
						mask |= 1 << i;
					}
				}
				if (mask == 0) {
					continue;
				}
				ResourceLocation base = IronManBattleDamage.hulkbusterBody(tier, false);
				if (base == null) {
					continue;
				}
				show(bakedModel, mask);
				drew = true;
				RenderType type = RenderType.entityCutoutNoCull(base);
				getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type), partialTick,
						packedLight, packedOverlay, 0xFFFFFFFF);
				ResourceLocation glow = IronManBattleDamage.hulkbusterBody(tier, true);
				if (glow != null) {
					RenderType eyes = RenderType.eyes(glow);
					getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, eyes, bufferSource.getBuffer(eyes), partialTick,
							LightTexture.FULL_SKY, packedOverlay, 0xFFFFFFFF);
				}
			}
			if (drew) {
				show(bakedModel, baseMask(tiers, visible));
			}
		}
	}
}
