package com.projecthero.mod.client.beastboy.morph;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy morph round: a buffer source that bends everything drawn into it through a {@link MorphField}. Vertices arrive
 * already in camera space, so each is taken back into the body's feet frame ({@code base}, the pose at his feet turned to his body yaw),
 * deformed there and put back -- which means any renderer (the vanilla player with his armour and held items, the box animals, the mesh
 * animals) can be morphed without knowing about it.
 *
 * <p>Two modes: SKIN (the body itself, its colour pulled toward Beast Boy green) and GLOW (every part redrawn additive and full-bright in
 * green, brightest along the seams between parts -- the morph energy). Text (name tags) passes through untouched.
 */
public final class MorphBuffers implements MultiBufferSource {
	private static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/mutation/white.png");
	private static final RenderType GLOW = RenderType.eyes(WHITE);

	final MultiBufferSource delegate;
	final MorphField field;
	final boolean glow;
	private final Matrix4f base, baseInv;
	private final Matrix3f nrm, nrmInv;

	MorphBuffers(MultiBufferSource delegate, MorphField field, Matrix4f base, Matrix3f normal, boolean glow) {
		this.delegate = delegate;
		this.field = field;
		this.glow = glow;
		this.base = base;
		this.baseInv = new Matrix4f(base).invert();
		this.nrm = normal;
		this.nrmInv = new Matrix3f(normal).invert();
	}

	/** The buffers under any morph wrapping (drawing something that must not bend, like a quick-morph partial). */
	public static MultiBufferSource unwrap(MultiBufferSource b) {
		while (b instanceof MorphBuffers m) {
			b = m.delegate;
		}
		return b;
	}

	@Override
	public VertexConsumer getBuffer(RenderType type) {
		if (type.toString().startsWith("RenderType[text")) {
			return delegate.getBuffer(type);
		}
		return new Consumer(delegate.getBuffer(glow ? GLOW : type));
	}

	private final class Consumer implements VertexConsumer {
		private final VertexConsumer out;
		private final Vector4f v = new Vector4f();
		private final Vector3f p = new Vector3f(), n = new Vector3f();
		private float seam;
		private int dom;

		Consumer(VertexConsumer out) {
			this.out = out;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			baseInv.transform(v.set(x, y, z, 1f));
			p.set(v.x, v.y, v.z);
			field.deform(p);
			seam = field.seam;
			dom = field.dom;
			base.transform(v.set(p, 1f));
			out.addVertex(v.x, v.y, v.z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			if (glow) {
				float k = Mth.clamp(field.glowBase + seam * field.glowSeam, 0f, 1.2f) * a / 255f;
				out.setColor(Math.min(255, (int) (80 * k)), Math.min(255, (int) (255 * k)), Math.min(255, (int) (60 * k)), 255);
			} else {
				float gk = field.green;
				out.setColor((int) (r * (1f - gk * 0.58f)), g, (int) (b * (1f - gk * 0.68f)), a);
			}
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			out.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			if (glow) {
				out.setOverlay(OverlayTexture.NO_OVERLAY);
			} else {
				out.setUv1(u, v);
			}
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			if (glow) {
				out.setLight(LightTexture.FULL_BRIGHT);
			} else {
				out.setUv2(u, v);
			}
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			nrmInv.transform(n.set(x, y, z));
			field.delta[dom].transform(n);
			nrm.transform(n);
			float l = n.length();
			if (l > 1.0e-5f) {
				n.div(l);
			}
			out.setNormal(n.x, n.y, n.z);
			return this;
		}
	}
}
