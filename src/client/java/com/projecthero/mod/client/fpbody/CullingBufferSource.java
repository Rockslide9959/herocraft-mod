package com.projecthero.mod.client.fpbody;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * v0.15.15: the buffer source the full first-person body ({@link FirstPersonBody}) is drawn through. Every quad any
 * layer emits is held until its four corners are known, then dropped if its centre lies inside the head's volume
 * (in the head's own frame, so it follows the head's turn and nod) and passed on otherwise. That hides the head, the
 * hat layer, helmets, glasses and any GeckoLib head bone without knowing anything about who drew them -- while a helmet
 * held overhead or out in the hands, outside the volume, still shows.
 *
 * <p>The underlying {@link MultiBufferSource.BufferSource} ends the previous batch when a different render type is
 * asked for, so every pending vertex is pushed through before each {@link #getBuffer} and at {@link #flush}.
 */
public final class CullingBufferSource implements MultiBufferSource {
	/** The head volume, in head-frame blocks (the head cube is x/z -0.25..0.25, y -0.5..0; helmets sit a little outside). */
	private static final float HALF_WIDTH = 0.34f;
	private static final float TOP = -0.66f;
	/** The neck plane band (head-frame y): above it everything inside goes, within it only downward faces. */
	private static final float NECK_ABOVE = -0.03f;
	private static final float NECK_BELOW = 0.09f;

	/** The upright volume over the shoulders (world blocks, from the neck pivot; z along the body's facing). */
	private static final float UPRIGHT_HALF_WIDTH = 0.32f;
	private static final float UPRIGHT_BOTTOM = 0.05f;
	private static final float UPRIGHT_TOP = 0.62f;
	private static final float UPRIGHT_BACK = -0.45f;
	private static final float UPRIGHT_FRONT = 0.30f;

	private final MultiBufferSource delegate;
	private final Map<RenderType, Culling> open = new IdentityHashMap<>();

	public CullingBufferSource(MultiBufferSource delegate) {
		this.delegate = delegate;
	}

	@Override
	public VertexConsumer getBuffer(RenderType type) {
		flush();
		VertexConsumer real = delegate.getBuffer(type);
		if (type.mode() != VertexFormat.Mode.QUADS) {
			return real;
		}
		Culling c = open.get(type);
		if (c == null || c.out != real) {
			c = new Culling(real);
			open.put(type, c);
		}
		return c;
	}

	/** Pushes every held vertex through (call before the real source ends a batch, and when the body is done). */
	public void flush() {
		for (Culling c : open.values()) {
			c.flush();
		}
	}

	/**
	 * Is a quad centred at {@code c} with normal {@code n} part of the head? Inside the head volume, yes -- except right at
	 * the neck plane, where the head's (and helmet's) underside and the torso's top share a centre: there only a face
	 * pointing out of the bottom of the head (head-frame +y, model space is y-down) goes.
	 */
	static boolean inHead(float cx, float cy, float cz, float nx, float ny, float nz) {
		Matrix4f inv = FirstPersonBody.headInverse();
		if (inv == null) {
			return false;
		}
		Matrix4f neck = FirstPersonBody.neckFrame();
		if (neck != null) {
			// the upright space above the shoulders, whatever the head is doing: a hood or collar rising behind the head,
			// a helmet still sitting where the head was before it nodded down
			Vector3f u = neck.transformPosition(cx, cy, cz, new Vector3f());
			if (Math.abs(u.x) < UPRIGHT_HALF_WIDTH && u.y > UPRIGHT_BOTTOM && u.y < UPRIGHT_TOP && u.z > UPRIGHT_BACK
					&& u.z < UPRIGHT_FRONT) {
				return true;
			}
		}
		Vector3f v = inv.transformPosition(cx, cy, cz, new Vector3f());
		if (Math.abs(v.x) >= HALF_WIDTH || Math.abs(v.z) >= HALF_WIDTH || v.y <= TOP || v.y >= NECK_BELOW) {
			return false;
		}
		if (v.y < NECK_ABOVE) {
			return true;
		}
		Vector3f n = inv.transformDirection(nx, ny, nz, new Vector3f());
		float len = n.length();
		return len > 1e-4f && n.y / len > 0.7f;
	}

	/** One held vertex. */
	private static final class Vtx {
		float x, y, z, u, v, nx, ny = 1f, nz;
		int color = -1, overlay, light;
	}

	/** One render type's consumer: collects quads, drops those in the head volume. */
	private static final class Culling implements VertexConsumer {
		final VertexConsumer out;
		private final List<Vtx> quad = new ArrayList<>(4);
		/** The vertex being written through the chained API (finished by the next addVertex or a flush). */
		private Vtx cur;

		Culling(VertexConsumer out) {
			this.out = out;
		}

		private void finishCurrent() {
			if (cur != null) {
				quad.add(cur);
				cur = null;
				if (quad.size() == 4) {
					emitQuad();
				}
			}
		}

		private void emitQuad() {
			float cx = 0f, cy = 0f, cz = 0f, nx = 0f, ny = 0f, nz = 0f;
			for (Vtx v : quad) {
				cx += v.x;
				cy += v.y;
				cz += v.z;
				nx += v.nx;
				ny += v.ny;
				nz += v.nz;
			}
			boolean drop = quad.size() == 4 && inHead(cx / 4f, cy / 4f, cz / 4f, nx, ny, nz);
			if (!drop) {
				for (Vtx v : quad) {
					out.addVertex(v.x, v.y, v.z, v.color, v.u, v.v, v.overlay, v.light, v.nx, v.ny, v.nz);
				}
			}
			quad.clear();
		}

		void flush() {
			finishCurrent();
			if (!quad.isEmpty()) {
				emitQuad(); // an incomplete quad (never expected) passes through as it came
			}
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			finishCurrent();
			cur = new Vtx();
			cur.x = x;
			cur.y = y;
			cur.z = z;
			return this;
		}

		@Override
		public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny,
				float nz) {
			finishCurrent();
			Vtx t = new Vtx();
			t.x = x;
			t.y = y;
			t.z = z;
			t.color = color;
			t.u = u;
			t.v = v;
			t.overlay = overlay;
			t.light = light;
			t.nx = nx;
			t.ny = ny;
			t.nz = nz;
			cur = t;
			finishCurrent();
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			if (cur != null) {
				cur.color = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | b & 255;
			}
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			if (cur != null) {
				cur.u = u;
				cur.v = v;
			}
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			if (cur != null) {
				cur.overlay = u & 0xFFFF | v << 16;
			}
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			if (cur != null) {
				cur.light = u & 0xFFFF | v << 16;
			}
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			if (cur != null) {
				cur.nx = x;
				cur.ny = y;
				cur.nz = z;
			}
			return this;
		}
	}
}
