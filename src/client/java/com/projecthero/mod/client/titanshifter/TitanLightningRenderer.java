package com.projecthero.mod.client.titanshifter;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.titanshifter.entity.TitanLightningBolt;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import org.joml.Matrix4f;

/**
 * v0.13.11: the Titan transformation bolt, drawn yellow. The geometry is vanilla's
 * {@code LightningBoltRenderer} line for line (same seed walk, same four widening passes, same three
 * branches). Vanilla blends its bolt additively, which washes any tint out to white against a blue sky, so
 * this one uses ordinary translucency instead ({@link RenderType#debugQuads}) and colours each pass: a pale
 * white-gold core fading out to deep gold at the widest pass.
 */
public class TitanLightningRenderer extends EntityRenderer<TitanLightningBolt> {
	/** Per widening pass (0 = the thin core): red, green, blue, alpha. */
	private static final float[][] PASS_COLOUR = {
			{ 1.0f, 0.98f, 0.78f, 0.6f },
			{ 1.0f, 0.9f, 0.35f, 0.42f },
			{ 1.0f, 0.8f, 0.14f, 0.3f },
			{ 0.95f, 0.68f, 0.05f, 0.22f } };

	public TitanLightningRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(TitanLightningBolt bolt, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
		float[] xs = new float[8];
		float[] zs = new float[8];
		float x = 0.0f;
		float z = 0.0f;
		RandomSource random = RandomSource.create(bolt.seed);
		for (int i = 7; i >= 0; i--) {
			xs[i] = x;
			zs[i] = z;
			x += (float) (random.nextInt(11) - 5);
			z += (float) (random.nextInt(11) - 5);
		}
		VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
		Matrix4f pose = poseStack.last().pose();
		for (int pass = 0; pass < 4; pass++) {
			RandomSource branchRandom = RandomSource.create(bolt.seed);
			float[] c = PASS_COLOUR[pass];
			for (int branch = 0; branch < 3; branch++) {
				int top = 7;
				int bottom = 0;
				if (branch > 0) {
					top = 7 - branch;
					bottom = top - 2;
				}
				float bx = xs[top] - x;
				float bz = zs[top] - z;
				for (int seg = top; seg >= bottom; seg--) {
					float prevX = bx;
					float prevZ = bz;
					if (branch == 0) {
						bx += (float) (branchRandom.nextInt(11) - 5);
						bz += (float) (branchRandom.nextInt(11) - 5);
					} else {
						bx += (float) (branchRandom.nextInt(31) - 15);
						bz += (float) (branchRandom.nextInt(31) - 15);
					}
					float upper = 0.1f + pass * 0.2f;
					if (branch == 0) {
						upper *= seg * 0.1f + 1.0f;
					}
					float lower = 0.1f + pass * 0.2f;
					if (branch == 0) {
						lower *= (seg - 1.0f) * 0.1f + 1.0f;
					}
					quad(pose, consumer, bx, bz, seg, prevX, prevZ, upper, lower, c, false, false, true, false);
					quad(pose, consumer, bx, bz, seg, prevX, prevZ, upper, lower, c, true, false, true, true);
					quad(pose, consumer, bx, bz, seg, prevX, prevZ, upper, lower, c, true, true, false, true);
					quad(pose, consumer, bx, bz, seg, prevX, prevZ, upper, lower, c, false, true, false, false);
				}
			}
		}
	}

	private static void quad(Matrix4f pose, VertexConsumer consumer, float x1, float z1, int index, float x2, float z2,
			float upper, float lower, float[] col, boolean a, boolean b, boolean c, boolean d) {
		consumer.addVertex(pose, x1 + (a ? lower : -lower), index * 16.0f, z1 + (b ? lower : -lower)).setColor(col[0], col[1], col[2], col[3]);
		consumer.addVertex(pose, x2 + (a ? upper : -upper), (index + 1) * 16.0f, z2 + (b ? upper : -upper)).setColor(col[0], col[1], col[2], col[3]);
		consumer.addVertex(pose, x2 + (c ? upper : -upper), (index + 1) * 16.0f, z2 + (d ? upper : -upper)).setColor(col[0], col[1], col[2], col[3]);
		consumer.addVertex(pose, x1 + (c ? lower : -lower), index * 16.0f, z1 + (d ? lower : -lower)).setColor(col[0], col[1], col[2], col[3]);
	}

	@Override
	public ResourceLocation getTextureLocation(TitanLightningBolt bolt) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
