package com.projecthero.mod.client.titanshifter;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import com.projecthero.mod.titanshifter.entity.TitanCorpseEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import org.joml.Vector3f;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws the Titan's dissolving corpse (v0.12.36): each {@code piece_*} bone of the sliced model starts to shake loose, drifts
 * outward and sinks while it shrinks, then is gone -- one piece after another across the whole minute (the order and timing
 * live in {@link TitanCorpseEntity}).
 */
public class TitanCorpseRenderer extends GeoEntityRenderer<TitanCorpseEntity> {
	/** How long (ticks) a piece crumbles before it disappears. */
	private static final float CRUMBLE_TICKS = 90.0f;
	private static final Map<String, Vector3f> CENTRES = new HashMap<>();

	public TitanCorpseRenderer(EntityRendererProvider.Context context) {
		super(context, new TitanCorpseModel());
		this.shadowRadius = 0.0f;
	}

	@Override
	public void preRender(PoseStack poseStack, TitanCorpseEntity animatable, BakedGeoModel model,
			MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
			int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight,
				packedOverlay, colour);
		float scale = animatable.getBbHeight() / animatable.titanType().modelHeight();
		if (Math.abs(scale - 1.0f) > 0.01f) {
			poseStack.scale(scale, scale, scale);
		}
	}

	@Override
	public void renderRecursively(PoseStack poseStack, TitanCorpseEntity animatable, GeoBone bone, RenderType renderType,
			MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
			int packedOverlay, int colour) {
		int index = TitanCorpseEntity.pieceIndex(bone.getName());
		if (index < 0) {
			super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
					packedLight, packedOverlay, colour);
			return;
		}
		float age = animatable.dissolveAge(partialTick);
		float vanishAt = TitanCorpseEntity.vanishFraction(index) * TitanCorpseEntity.dissolveTicks();
		if (age >= vanishAt) {
			return; // gone
		}
		float c = Math.max(0.0f, Math.min(1.0f, (age - (vanishAt - CRUMBLE_TICKS)) / CRUMBLE_TICKS));
		if (c <= 0.0f) {
			super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
					packedLight, packedOverlay, colour);
			return;
		}
		Vector3f centre = CENTRES.computeIfAbsent(bone.getName(), n -> centreOf(bone));
		float e = c * c;
		float side = Math.signum(centre.x) * 0.9f + (index % 3 - 1) * 0.35f;
		float shake = (float) Math.sin(age * 1.7f + index * 2.3f) * 0.012f * c;
		float s = 1.0f - 0.65f * e;
		poseStack.pushPose();
		poseStack.translate(centre.x + side * 0.08f * e + shake, centre.y - 0.10f * e, centre.z + 0.05f * e - shake);
		poseStack.scale(s, s, s);
		poseStack.translate(-centre.x, -centre.y, -centre.z);
		super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
				packedLight, packedOverlay, colour);
		poseStack.popPose();
	}

	/** The middle of a piece's cubes in render space (blocks), so it can shrink about its own centre. */
	private static Vector3f centreOf(GeoBone bone) {
		Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);
		for (GeoCube cube : bone.getCubes()) {
			for (GeoQuad quad : cube.quads()) {
				for (GeoVertex v : quad.vertices()) {
					min.min(v.position());
					max.max(v.position());
				}
			}
		}
		return min.x > max.x ? new Vector3f() : min.add(max).mul(0.5f);
	}
}
