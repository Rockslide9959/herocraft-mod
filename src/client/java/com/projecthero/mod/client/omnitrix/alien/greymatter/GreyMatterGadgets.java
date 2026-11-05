package com.projecthero.mod.client.omnitrix.alien.greymatter;

import java.util.List;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterMechEntity;
import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterSentryEntity;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.2 round 3: Grey Matter's two machines, modelled in code and skinned with the block they were built from: the walls take that
 * block's side texture (the tint a grass or leaf block has), the upward faces its top, so a mech of oak logs is ringed bark with end
 * grain on its shoulders. Joints and pistons are iron. Everything is in blocks, feet at the origin, +z forward and +x his left.
 * <ul>
 *   <li>{@link Mech}: 1.5 blocks to the top of the glass dome he sits in. Digitigrade legs solved with two-bone IK off the walk cycle,
 *   piston arms with big block fists, assembled part by part round him, and falling to bits when it ends.</li>
 *   <li>{@link Sentry}: a tripod with a turning, pitching twin autocannon, a recoil on each barrel, a spinning ammo drum, a red
 *   laser on what it has locked.</li>
 * </ul>
 */
public final class GreyMatterGadgets {
	private static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/mutation/white.png");
	private static final ResourceLocation SOFT = ProjectHeroMod.id("textures/entity/omnitrix/pod_soft_glow.png");
	private static final RandomSource QUAD_RANDOM = RandomSource.create(42L);
	private static final int LIGHT_CYAN = 0xFF7FE6FF;

	private GreyMatterGadgets() {
	}

	public static void register() {
		EntityRendererRegistry.register(GreyMatterMechEntity.TYPE, Mech::new);
		EntityRendererRegistry.register(GreyMatterSentryEntity.TYPE, Sentry::new);
	}

	// ---------------------------------------------------------------- the block skin

	/** One texture of a block: its sprite and the tint its quad takes. */
	private record Tex(TextureAtlasSprite sprite, int tint) {
	}

	/** The side and top textures of {@code state}, and iron for the joints. */
	private record Skin(Tex side, Tex top, Tex metal) {
		static Skin of(BlockState state, Level level, BlockPos pos) {
			return new Skin(tex(state, Direction.NORTH, level, pos), tex(state, Direction.UP, level, pos), tex(Blocks.IRON_BLOCK.defaultBlockState(), Direction.NORTH, level, pos));
		}
	}

	private static Tex tex(BlockState state, Direction face, Level level, BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		BakedModel model = mc.getBlockRenderer().getBlockModel(state);
		QUAD_RANDOM.setSeed(42L);
		List<BakedQuad> quads = model.getQuads(state, face, QUAD_RANDOM);
		if (quads.isEmpty()) {
			QUAD_RANDOM.setSeed(42L);
			quads = model.getQuads(state, null, QUAD_RANDOM);
		}
		if (quads.isEmpty()) {
			return new Tex(model.getParticleIcon(), 0xFFFFFF);
		}
		BakedQuad q = quads.get(0);
		int tint = q.isTinted() ? mc.getBlockColors().getColor(state, level, pos, q.getTintIndex()) : 0xFFFFFF;
		return new Tex(q.getSprite(), tint & 0xFFFFFF);
	}

	// ---------------------------------------------------------------- mesh helpers

	/** Writes facets into one buffer: a sprite, a tint, one light. */
	private static final class Mesh {
		final PoseStack pose;
		VertexConsumer vc;
		int light, overlay = OverlayTexture.NO_OVERLAY;
		float shade = 1f;
		TextureAtlasSprite sprite;
		int tint = 0xFFFFFF, alpha = 255;
		/** The whole texture on this quad (a glow), not a true-scale patch of it. */
		boolean full;
		private int facet;

		Mesh(PoseStack pose) {
			this.pose = pose;
		}

		void use(Tex t) {
			sprite = t.sprite();
			tint = t.tint();
		}

		private float u(float f) {
			return sprite == null ? f : sprite.getU(f);
		}

		private float v(float f) {
			return sprite == null ? f : sprite.getV(f);
		}

		private void vertex(PoseStack.Pose p, float x, float y, float z, float uu, float vv, float nx, float ny, float nz) {
			int r = (int) (((tint >> 16) & 0xFF) * shade), g = (int) (((tint >> 8) & 0xFF) * shade), b = (int) ((tint & 0xFF) * shade);
			vc.addVertex(p, x, y, z).setColor(r, g, b, alpha).setUv(u(uu), v(vv)).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
		}

		/** One flat facet, corners counter-clockwise from outside; the texture is laid at true scale (a block = the whole sprite). */
		void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
			Vector3f e1 = new Vector3f(bx - ax, by - ay, bz - az), e2 = new Vector3f(dx - ax, dy - ay, dz - az);
			Vector3f n = new Vector3f(e2).cross(e1);
			if (n.lengthSquared() < 1e-10f) {
				n.set(new Vector3f(e2).cross(new Vector3f(cx - ax, cy - ay, cz - az)));
			}
			if (n.lengthSquared() > 1e-10f) {
				n.normalize();
			}
			float w = full ? 1f : Math.min(1f, Math.max(e1.length(), new Vector3f(cx - dx, cy - dy, cz - dz).length()));
			float h = full ? 1f : Math.min(1f, e2.length());
			// each facet starts somewhere else on the sprite, so a hull is not one tile repeated
			facet = (facet + 7) % 16;
			float u0 = (1f - w) * (facet / 15f), v0 = (1f - h) * (((facet * 5) % 16) / 15f);
			PoseStack.Pose p = pose.last();
			vertex(p, ax, ay, az, u0, v0, n.x, n.y, n.z);
			vertex(p, dx, dy, dz, u0, v0 + h, n.x, n.y, n.z);
			vertex(p, cx, cy, cz, u0 + w, v0 + h, n.x, n.y, n.z);
			vertex(p, bx, by, bz, u0 + w, v0, n.x, n.y, n.z);
		}

		void box(float x0, float y0, float z0, float x1, float y1, float z1) {
			quad(x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1); // front +z
			quad(x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0); // back -z
			quad(x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1); // +x
			quad(x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0); // -x
			quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1); // top
			quad(x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0); // bottom
		}

		/** The eight corners of a chamfered ring at height y. */
		private static float[][] ring(float y, float hx, float hz, float c) {
			float cx = Math.min(c, hx * 0.9f), cz = Math.min(c, hz * 0.9f);
			return new float[][] { { hx - cx, y, hz }, { hx, y, hz - cz }, { hx, y, -hz + cz }, { hx - cx, y, -hz }, { -hx + cx, y, -hz }, { -hx, y, -hz + cz },
					{ -hx, y, hz - cz }, { -hx + cx, y, hz } };
		}

		/**
		 * A smooth-shouldered hull lofted through {@code rings} (bottom to top, {y, half x, half z, chamfer}), capped at both ends. The
		 * walls take {@code side}, the upward slopes and the top cap {@code top}.
		 */
		void loft(float[][] rings, Tex side, Tex top) {
			for (int r = 0; r + 1 < rings.length; r++) {
				float[][] a = ring(rings[r][0], rings[r][1], rings[r][2], rings[r][3]), b = ring(rings[r + 1][0], rings[r + 1][1], rings[r + 1][2], rings[r + 1][3]);
				float rise = rings[r + 1][0] - rings[r][0], in = rings[r][1] - rings[r + 1][1];
				use(in > rise * 1.5f ? top : side);
				for (int i = 0; i < 8; i++) {
					int j = (i + 1) % 8;
					quad(b[i][0], b[i][1], b[i][2], b[j][0], b[j][1], b[j][2], a[j][0], a[j][1], a[j][2], a[i][0], a[i][1], a[i][2]);
				}
			}
			use(top);
			float[] t = rings[rings.length - 1];
			cap(ring(t[0], t[1], t[2], t[3]), false);
			float[] f = rings[0];
			use(side);
			cap(ring(f[0], f[1], f[2], f[3]), true);
		}

		private void cap(float[][] o, boolean down) {
			int[][] fan = { { 0, 1, 2, 3 }, { 0, 3, 4, 7 }, { 4, 5, 6, 7 } };
			for (int[] q : fan) {
				float[] a = o[q[down ? 3 : 0]], b = o[q[down ? 2 : 1]], c = o[q[down ? 1 : 2]], d = o[q[down ? 0 : 3]];
				quad(a[0], a[1], a[2], d[0], d[1], d[2], c[0], c[1], c[2], b[0], b[1], b[2]);
			}
		}

		/** A round bar along -y from the origin, {@code len} long, radius r0 narrowing to r1. */
		void tube(float r0, float r1, float len, int sides) {
			for (int i = 0; i < sides; i++) {
				float a0 = i * Mth.TWO_PI / sides, a1 = (i + 1) * Mth.TWO_PI / sides;
				float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
				quad(c1 * r0, 0, s1 * r0, c0 * r0, 0, s0 * r0, c0 * r1, -len, s0 * r1, c1 * r1, -len, s1 * r1);
				quad(c0 * r1, -len, s0 * r1, c0 * r1 * 0.4f, -len, s0 * r1 * 0.4f, c1 * r1 * 0.4f, -len, s1 * r1 * 0.4f, c1 * r1, -len, s1 * r1);
			}
		}
	}

	/** Bends along the bone: a bar of {@code len} down -y, wide at both ends and pinched in the middle, in the block's skin. */
	private static void armour(Mesh m, Tex side, Tex top, float hx, float hz, float len) {
		m.loft(new float[][] { { -len, hx * 0.72f, hz * 0.72f, 0.03f }, { -len * 0.82f, hx, hz, 0.04f }, { -len * 0.2f, hx, hz, 0.04f }, { 0f, hx * 0.78f, hz * 0.78f, 0.03f } }, side, top);
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/** Eases out with a little overshoot: parts snap home. */
	private static float back(float x) {
		x = Mth.clamp(x, 0f, 1f);
		float o = 1.70158f, k = x - 1f;
		return 1f + (o + 1f) * k * k * k + o * k * k;
	}

	/** Samples {tick, values...} frames, smoothstepped. */
	private static float[] keys(float[][] frames, float t) {
		int n = frames[0].length - 1;
		float[] out = new float[n];
		if (t <= frames[0][0]) {
			System.arraycopy(frames[0], 1, out, 0, n);
			return out;
		}
		for (int i = 0; i + 1 < frames.length; i++) {
			if (t <= frames[i + 1][0]) {
				float k = smooth((t - frames[i][0]) / (frames[i + 1][0] - frames[i][0]));
				for (int c = 0; c < n; c++) {
					out[c] = Mth.lerp(k, frames[i][c + 1], frames[i + 1][c + 1]);
				}
				return out;
			}
		}
		System.arraycopy(frames[frames.length - 1], 1, out, 0, n);
		return out;
	}

	// ---------------------------------------------------------------- the Block Mech

	static final class Mech extends EntityRenderer<GreyMatterMechEntity> {
		private static final float HIP_Y = 0.60f, HIP_X = 0.19f, THIGH = 0.33f, SHIN = 0.35f;
		private static final float SHOULDER_Y = 1.0f, SHOULDER_X = 0.47f, UPPER = 0.25f, FORE = 0.24f;
		/** Shoulder pitch (0 = hanging, + forward), elbow bend, piston extension: at rest. */
		private static final float[] ARM_REST = { 0.3f, 0.9f, 0f };
		// {tick, shoulder, elbow, extension, hull twist weight, hull lean}
		private static final float[][] PUNCH = { { 0, 0.3f, 0.9f, 0, 0, 0 }, { 2.5f, -0.45f, 1.7f, 0, -0.6f, -0.05f }, { 4.5f, 1.55f, 0.05f, 0.2f, 1f, 0.16f },
				{ 7.5f, 1.5f, 0.1f, 0.14f, 0.8f, 0.12f }, { 14, 0.3f, 0.9f, 0, 0, 0 } };
		private static final float[][] SLAM = { { 0, 0.3f, 0.9f, 0, 0, 0 }, { 6, 2.95f, 0.5f, 0.05f, 0, -0.14f }, { 8.5f, 0.42f, 0.02f, 0.2f, 0, 0.2f }, { 14, 0.42f, 0.02f, 0.18f, 0, 0.18f },
				{ 24, 0.3f, 0.9f, 0, 0, 0 } };
		private static final float[][] HOP = { { 0, 0.3f, 0.9f, 0, 0, 0 }, { 3, -0.7f, 0.3f, 0, 0, -0.12f }, { 10, -0.5f, 0.4f, 0, 0, -0.06f }, { 18, 0.3f, 0.9f, 0, 0, 0 } };

		Mech(EntityRendererProvider.Context ctx) {
			super(ctx);
			this.shadowRadius = 0.55f;
		}

		@Override
		public ResourceLocation getTextureLocation(GreyMatterMechEntity e) {
			return InventoryMenu.BLOCK_ATLAS;
		}

		/** Scales a part in around {@code (0, py, 0)} as the mech goes up, and drops it apart when it ends. Returns false when it is not there yet. */
		private static boolean part(GreyMatterMechEntity e, PoseStack pose, float t, float delay, float py, float spread, int seed) {
			if (e.state() == GreyMatterMechEntity.STATE_ASSEMBLING) {
				float k = (t - delay) / 7f;
				if (k <= 0f) {
					return false;
				}
				float s = back(k);
				pose.translate(0, py + (1f - s) * 0.5f, 0);
				pose.scale(s, s, s);
				pose.translate(0, -py, 0);
			} else if (e.state() == GreyMatterMechEntity.STATE_COLLAPSING) {
				float k = t / GreyMatterMechEntity.COLLAPSE_TICKS;
				float drop = Math.min(py, 0.5f * t * t * 0.018f);
				pose.translate(spread * k, -drop, (seed - 2) * 0.06f * k);
				pose.translate(0, py, 0);
				pose.mulPose(Axis.ZP.rotation(spread * 1.6f * k));
				pose.mulPose(Axis.XP.rotation((seed - 2) * 0.4f * k));
				float s = 1f - smooth((k - 0.65f) / 0.35f);
				pose.scale(s, s, s);
				pose.translate(0, -py, 0);
			}
			return true;
		}

		@Override
		public void render(GreyMatterMechEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
			Minecraft mc = Minecraft.getInstance();
			long now = e.level().getGameTime();
			float t = e.stateTicks() + partial;
			float act = now - e.actionAt() + partial;
			int action = e.action();
			float bodyYaw = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
			float walk = e.walkAnimation.position(partial) * 1.1f;
			float speed = Math.min(1f, e.walkAnimation.speed(partial) * 1.6f);
			boolean air = !e.onGround();
			Skin skin = Skin.of(e.material(), e.level(), e.blockPosition());
			float hp = Mth.clamp(e.getHealth() / Math.max(1f, e.getMaxHealth()), 0f, 1f);
			Entity cam = mc.getCameraEntity();
			boolean cockpit = cam != null && cam.getVehicle() == e && mc.options.getCameraType().isFirstPerson();

			// the arms' pose this frame
			float[][] clip = action == GreyMatterMechEntity.ACT_SLAM ? SLAM : action == GreyMatterMechEntity.ACT_HOP ? HOP
					: action == GreyMatterMechEntity.ACT_PUNCH_R || action == GreyMatterMechEntity.ACT_PUNCH_L ? PUNCH : null;
			float[] c = clip != null && act <= clip[clip.length - 1][0] ? keys(clip, act) : null;
			boolean both = action == GreyMatterMechEntity.ACT_SLAM || action == GreyMatterMechEntity.ACT_HOP;
			boolean rightActive = c != null && (both || action == GreyMatterMechEntity.ACT_PUNCH_R), leftActive = c != null && (both || action == GreyMatterMechEntity.ACT_PUNCH_L);
			float twist = c == null || both ? 0f : c[3] * (action == GreyMatterMechEntity.ACT_PUNCH_R ? 0.22f : -0.22f);
			float lean = c == null ? 0f : c[4];
			float bob = speed * Math.abs(Mth.sin(walk)) * -0.025f + Mth.sin((e.tickCount + partial) * 0.09f) * 0.006f;

			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));

			Mesh m = new Mesh(pose);
			m.vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
			m.light = light;
			m.overlay = OverlayTexture.pack(OverlayTexture.u(0f), OverlayTexture.v(e.hurtTime > 0));
			m.shade = 0.62f + 0.38f * hp; // it scorches as it takes hits

			for (int side = -1; side <= 1; side += 2) {
				pose.pushPose();
				if (part(e, pose, t, side < 0 ? 0f : 2f, 0.2f, side * 0.3f, side + 2)) {
					leg(m, skin, side, walk + (side < 0 ? 0f : Mth.PI), speed, air, HIP_Y + bob);
				}
				pose.popPose();
			}

			pose.pushPose();
			pose.translate(0, HIP_Y + bob, 0);
			pose.mulPose(Axis.YP.rotation(twist));
			pose.mulPose(Axis.XP.rotation(lean));
			pose.translate(0, -HIP_Y, 0);
			pose.pushPose();
			if (part(e, pose, t, 5f, 0.7f, 0f, 2)) {
				hull(m, skin, cockpit);
			}
			pose.popPose();
			for (int side = -1; side <= 1; side += 2) {
				boolean right = side < 0;
				float[] a = right ? rightActive ? c : ARM_REST : leftActive ? c : ARM_REST;
				float swing = a == ARM_REST ? Mth.sin(walk + (right ? Mth.PI : 0f)) * 0.3f * speed : 0f;
				pose.pushPose();
				if (part(e, pose, t, right ? 9f : 10f, SHOULDER_Y, side * 0.5f, right ? 1 : 3)) {
					arm(m, skin, side, a[0] + swing, a[1], a[2]);
				}
				pose.popPose();
			}
			pose.popPose();

			// fetching the next buffer ends this one, so the lights and the glass come last
			float lit = e.state() == GreyMatterMechEntity.STATE_ASSEMBLING ? smooth((t - 15f) / 4f) : e.state() == GreyMatterMechEntity.STATE_COLLAPSING ? 0f : 1f;
			if (lit > 0f) {
				pose.pushPose();
				pose.translate(0, HIP_Y + bob, 0);
				pose.mulPose(Axis.YP.rotation(twist));
				pose.mulPose(Axis.XP.rotation(lean));
				pose.translate(0, -HIP_Y, 0);
				Mesh g = new Mesh(pose);
				g.vc = buffers.getBuffer(RenderType.eyes(WHITE));
				g.light = LightTexture.FULL_BRIGHT;
				boolean flicker = hp < 0.3f && (e.tickCount / 2) % 3 == 0;
				float pulse = (flicker ? 0.3f : 0.82f + 0.18f * Mth.sin((e.tickCount + partial) * 0.2f)) * lit;
				g.tint = rgbScale(LIGHT_CYAN, pulse);
				for (int side = -1; side <= 1; side += 2) {
					g.box(side * 0.13f - 0.045f, 0.92f, 0.302f, side * 0.13f + 0.045f, 0.955f, 0.312f);
				}
				g.box(-0.19f, 0.75f, 0.293f, 0.19f, 0.765f, 0.303f);
				pose.popPose();
			}
			if (!cockpit && e.state() != GreyMatterMechEntity.STATE_COLLAPSING) {
				float k = e.state() == GreyMatterMechEntity.STATE_ASSEMBLING ? back((t - 13f) / 7f) : 1f;
				if (k > 0.01f) {
					pose.pushPose();
					pose.translate(0, 1.11f + bob, 0);
					pose.mulPose(Axis.YP.rotation(twist));
					pose.scale(k, k, k);
					dome(new Mesh(pose), buffers, light);
					pose.popPose();
				}
			}
			pose.popPose();
			super.render(e, yaw, partial, pose, buffers, light);
		}

		private static int rgbScale(int rgb, float k) {
			return ((int) (((rgb >> 16) & 0xFF) * k) << 16) | ((int) (((rgb >> 8) & 0xFF) * k) << 8) | (int) ((rgb & 0xFF) * k);
		}

		/** The pelvis to the rim of the cockpit, the back stacks, the shoulder mounts. */
		private static void hull(Mesh m, Skin s, boolean cockpit) {
			m.loft(new float[][] { { 0.50f, 0.2f, 0.17f, 0.06f }, { 0.60f, 0.27f, 0.22f, 0.08f }, { 0.72f, 0.33f, 0.28f, 0.09f }, { 0.98f, 0.37f, 0.30f, 0.1f }, { 1.08f, 0.35f, 0.28f, 0.1f },
					{ 1.12f, 0.30f, 0.24f, 0.08f } }, s.side(), s.top());
			// the seat's pan and back, inside the rim
			if (!cockpit) { // from his own seat they would only fill the view
				m.use(s.metal());
				m.box(-0.13f, 1.12f, -0.2f, 0.13f, 1.125f, 0.2f);
				m.box(-0.12f, 1.12f, -0.2f, 0.12f, 1.3f, -0.15f);
			}
			// a dashboard hood under the dome
			m.use(s.side());
			m.box(-0.2f, 1.1f, 0.15f, 0.2f, 1.17f, 0.24f);
			// armoured chest plate, proud of the hull
			m.use(s.top());
			m.loft(new float[][] { { 0.68f, 0.2f, 0.02f, 0.015f }, { 0.74f, 0.24f, 0.02f, 0.015f }, { 0.9f, 0.24f, 0.02f, 0.015f } }, s.top(), s.top());
			// back stacks
			m.use(s.metal());
			for (int side = -1; side <= 1; side += 2) {
				m.pose.pushPose();
				m.pose.translate(side * 0.14f, 1.2f, -0.29f);
				m.tube(0.04f, 0.05f, 0.34f, 8);
				m.pose.popPose();
			}
			// hip block (the legs hang off it)
			m.box(-0.26f, 0.54f, -0.07f, 0.26f, 0.64f, 0.07f);
		}

		/** Two-bone IK in the leg's own plane: the knee kicks backward (a bird's leg). */
		private static void leg(Mesh m, Skin s, int side, float phase, float speed, boolean air, float hipY) {
			float sw = Mth.sin(phase), up = Math.max(0f, Mth.cos(phase));
			float fz = air ? 0.08f : sw * 0.2f * speed, fy = air ? 0.16f : up * 0.11f * speed;
			float dz = fz, dy = fy + 0.055f - hipY;
			float d = Mth.clamp(Mth.sqrt(dz * dz + dy * dy), Math.abs(THIGH - SHIN) + 0.01f, THIGH + SHIN - 0.01f);
			float a = (float) Math.acos(Mth.clamp((THIGH * THIGH + d * d - SHIN * SHIN) / (2f * THIGH * d), -1f, 1f));
			float base = (float) Mth.atan2(dz, -dy);
			float thigh = base - a;
			float kneeZ = Mth.sin(thigh) * THIGH, kneeY = hipY - Mth.cos(thigh) * THIGH;
			float shin = (float) Mth.atan2(fz - kneeZ, kneeY - (fy + 0.055f));
			PoseStack pose = m.pose;
			float x = side * HIP_X;
			// the thigh
			pose.pushPose();
			pose.translate(x, hipY, 0);
			pose.mulPose(Axis.XP.rotation(-thigh));
			armour(m, s.side(), s.top(), 0.085f, 0.1f, THIGH);
			pose.popPose();
			// the knee
			pose.pushPose();
			pose.translate(x, kneeY, kneeZ);
			pose.mulPose(Axis.ZP.rotation(Mth.HALF_PI));
			m.use(s.metal());
			pose.translate(0, 0.1f, 0);
			m.tube(0.055f, 0.055f, 0.2f, 8);
			pose.popPose();
			// the shin, a piston behind it
			pose.pushPose();
			pose.translate(x, kneeY, kneeZ);
			pose.mulPose(Axis.XP.rotation(-shin));
			armour(m, s.side(), s.top(), 0.075f, 0.085f, SHIN);
			pose.popPose();
			// the foot: a wedge, pitched flat to the ground
			pose.pushPose();
			pose.translate(x, fy, fz);
			m.use(s.side());
			m.quad(-0.09f, 0.1f, 0.03f, 0.09f, 0.1f, 0.03f, 0.1f, 0f, 0.2f, -0.1f, 0f, 0.2f);
			m.use(s.top());
			m.quad(-0.09f, 0.1f, -0.1f, 0.09f, 0.1f, -0.1f, 0.09f, 0.1f, 0.03f, -0.09f, 0.1f, 0.03f);
			m.use(s.side());
			m.quad(0.09f, 0.1f, -0.1f, -0.09f, 0.1f, -0.1f, -0.1f, 0f, -0.12f, 0.1f, 0f, -0.12f);
			m.quad(0.09f, 0.1f, 0.03f, 0.09f, 0.1f, -0.1f, 0.1f, 0f, -0.12f, 0.1f, 0f, 0.2f);
			m.quad(-0.09f, 0.1f, -0.1f, -0.09f, 0.1f, 0.03f, -0.1f, 0f, 0.2f, -0.1f, 0f, -0.12f);
			m.quad(-0.1f, 0f, 0.2f, 0.1f, 0f, 0.2f, 0.1f, 0f, -0.12f, -0.1f, 0f, -0.12f);
			pose.popPose();
		}

		/** Shoulder pod, upper arm, elbow, sliding piston forearm, and a fist nearly a block's third across. */
		private static void arm(Mesh m, Skin s, int side, float shoulder, float elbow, float extend) {
			PoseStack pose = m.pose;
			pose.pushPose();
			pose.translate(side * SHOULDER_X, SHOULDER_Y, 0.02f);
			// the pod
			m.loft(new float[][] { { -0.13f, 0.09f, 0.1f, 0.04f }, { -0.1f, 0.13f, 0.13f, 0.05f }, { 0.08f, 0.13f, 0.13f, 0.05f }, { 0.12f, 0.09f, 0.09f, 0.04f } }, s.side(), s.top());
			pose.mulPose(Axis.ZP.rotation(side * 0.06f));
			pose.mulPose(Axis.XP.rotation(-shoulder));
			m.use(s.side());
			armour(m, s.side(), s.top(), 0.085f, 0.09f, UPPER);
			pose.translate(0, -UPPER, 0);
			// the elbow
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotation(Mth.HALF_PI));
			pose.translate(0, 0.1f, 0);
			m.use(s.metal());
			m.tube(0.058f, 0.058f, 0.2f, 8);
			pose.popPose();
			pose.mulPose(Axis.XP.rotation(-elbow));
			// the forearm: a sleeve, and the iron ram sliding out of it for a punch
			m.use(s.metal());
			m.tube(0.04f, 0.04f, FORE + extend, 8);
			armour(m, s.side(), s.top(), 0.08f, 0.085f, FORE * 0.78f);
			pose.translate(0, -FORE - extend, 0);
			// the fist
			m.loft(new float[][] { { -0.25f, 0.095f, 0.1f, 0.03f }, { -0.21f, 0.125f, 0.13f, 0.04f }, { -0.03f, 0.125f, 0.13f, 0.04f }, { 0.01f, 0.09f, 0.1f, 0.03f } }, s.side(), s.top());
			pose.popPose();
		}

		/** A glass bubble over the seat (y 0 = the cockpit's rim, 1.5 blocks at its crown). */
		private static void dome(Mesh m, MultiBufferSource buffers, int light) {
			m.vc = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
			m.light = light;
			m.tint = 0xBFE9F4;
			m.alpha = 72;
			int rings = 6, sides = 14;
			float h = GreyMatterMechEntity.MECH_HEIGHT - 1.11f, rx = 0.3f, rz = 0.25f;
			for (int r = 0; r < rings; r++) {
				float a0 = r * Mth.HALF_PI / rings, a1 = (r + 1) * Mth.HALF_PI / rings;
				float y0 = Mth.sin(a0) * h, y1 = Mth.sin(a1) * h, k0 = Mth.cos(a0), k1 = Mth.cos(a1);
				for (int i = 0; i < sides; i++) {
					float b0 = i * Mth.TWO_PI / sides, b1 = (i + 1) * Mth.TWO_PI / sides;
					float c0 = Mth.cos(b0), s0 = Mth.sin(b0), c1 = Mth.cos(b1), s1 = Mth.sin(b1);
					m.quad(c1 * rx * k1, y1, s1 * rz * k1, c0 * rx * k1, y1, s0 * rz * k1, c0 * rx * k0, y0, s0 * rz * k0, c1 * rx * k0, y0, s1 * rz * k0);
				}
			}
		}
	}

	// ---------------------------------------------------------------- the Galvan Sentry

	static final class Sentry extends EntityRenderer<GreyMatterSentryEntity> {
		private static final float[] LEG_ANGLES = { 0f, Mth.TWO_PI / 3f, -Mth.TWO_PI / 3f };

		Sentry(EntityRendererProvider.Context ctx) {
			super(ctx);
			this.shadowRadius = 0.4f;
		}

		@Override
		public ResourceLocation getTextureLocation(GreyMatterSentryEntity e) {
			return InventoryMenu.BLOCK_ATLAS;
		}

		@Override
		public void render(GreyMatterSentryEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
			float age = e.tickCount + partial;
			float deploy = back(age / GreyMatterSentryEntity.DEPLOY_TICKS);
			float head = GreyMatterSentryEntity.HEAD_Y * Mth.lerp(deploy, 0.45f, 1f);
			float aimYaw = Mth.rotLerp(partial, e.aimYawO, e.aimYaw), aimPitch = Mth.lerp(partial, e.aimPitchO, e.aimPitch);
			float sinceShot = e.level().getGameTime() - e.shotAt() + partial;
			float kick = sinceShot < 5f ? (float) Math.exp(-sinceShot * 0.75f) * 0.09f : 0f;
			Skin skin = Skin.of(e.material(), e.level(), e.blockPosition());
			float hp = Mth.clamp(e.getHealth() / Math.max(1f, e.getMaxHealth()), 0f, 1f);

			Mesh m = new Mesh(pose);
			m.vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
			m.light = light;
			m.overlay = OverlayTexture.pack(OverlayTexture.u(0f), OverlayTexture.v(e.hurtTime > 0));
			m.shade = 0.65f + 0.35f * hp;

			// the tripod: its feet stay where it was set down
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot)));
			float hub = head - 0.2f;
			float reach = 0.37f * deploy;
			for (float a : LEG_ANGLES) {
				float fx = Mth.sin(a) * reach, fz = Mth.cos(a) * reach;
				float len = Mth.sqrt(reach * reach + hub * hub);
				pose.pushPose();
				pose.translate(0, hub, 0);
				pose.mulPose(Axis.YP.rotation(a));
				pose.mulPose(Axis.XP.rotation(-(float) Mth.atan2(reach, hub)));
				m.use(skin.metal());
				m.tube(0.035f, 0.024f, len, 8);
				pose.popPose();
				// a footpad in the block
				pose.pushPose();
				pose.translate(fx, 0, fz);
				pose.mulPose(Axis.YP.rotation(a));
				m.loft(new float[][] { { 0f, 0.085f, 0.11f, 0.03f }, { 0.04f, 0.075f, 0.1f, 0.03f }, { 0.07f, 0.04f, 0.055f, 0.02f } }, skin.side(), skin.top());
				pose.popPose();
			}
			pose.popPose();

			// the turntable and everything on it follows the aim's yaw
			pose.pushPose();
			pose.translate(0, hub, 0);
			pose.mulPose(Axis.YP.rotationDegrees(-aimYaw));
			m.loft(new float[][] { { -0.05f, 0.15f, 0.15f, 0.06f }, { 0f, 0.19f, 0.19f, 0.075f }, { 0.04f, 0.19f, 0.19f, 0.075f }, { 0.07f, 0.15f, 0.15f, 0.06f } }, skin.side(), skin.top());
			// the yoke's cheeks, either side of the gun
			for (int side = -1; side <= 1; side += 2) {
				m.use(skin.side());
				m.box(side * 0.17f - 0.025f, 0.05f, -0.1f, side * 0.17f + 0.025f, 0.33f, 0.1f);
			}
			pose.translate(0, head - hub, 0);
			pose.mulPose(Axis.XP.rotationDegrees(aimPitch));
			// the gun's breech, armoured in the block
			pose.pushPose();
			pose.mulPose(Axis.XP.rotation(Mth.HALF_PI));
			m.loft(new float[][] { { -0.24f, 0.11f, 0.08f, 0.04f }, { -0.2f, 0.14f, 0.11f, 0.05f }, { 0.15f, 0.14f, 0.11f, 0.05f }, { 0.2f, 0.1f, 0.075f, 0.03f } }, skin.side(), skin.top());
			pose.popPose();
			// a spare drum of rounds on its flank, clicking round a step with every shot
			pose.pushPose();
			pose.translate(0.2f, 0.01f, -0.04f);
			pose.mulPose(Axis.ZP.rotation(Mth.HALF_PI));
			m.use(skin.metal());
			m.tube(0.085f, 0.085f, 0.09f, 10);
			pose.popPose();
			// twin barrels with muzzle brakes, each kicking back when it fires
			for (int side = -1; side <= 1; side += 2) {
				boolean fired = e.shotLeft() == (side > 0);
				pose.pushPose();
				pose.translate(side * 0.085f, 0f, 0.2f - (fired ? kick : 0f));
				pose.mulPose(Axis.XP.rotation(-Mth.HALF_PI));
				m.use(skin.metal());
				m.tube(0.04f, 0.03f, 0.44f, 8);
				pose.translate(0, -0.34f, 0);
				m.tube(0.045f, 0.045f, 0.1f, 8);
				pose.popPose();
			}
			// the sensor block on top
			m.use(skin.top());
			m.box(-0.05f, 0.11f, -0.03f, 0.05f, 0.17f, 0.11f);

			// the red eye, the lock laser and the muzzle flash, in glow
			float blink = deploy >= 0.99f ? 1f : 0.2f;
			Entity target = e.target();
			Mesh g = new Mesh(pose);
			g.vc = buffers.getBuffer(RenderType.eyes(WHITE));
			g.light = LightTexture.FULL_BRIGHT;
			g.tint = target != null ? 0xFF3020 : (e.tickCount / 8) % 2 == 0 ? (int) (0x90 * blink) << 16 | 0x1810 : 0x301008;
			g.box(-0.028f, 0.125f, 0.11f, 0.028f, 0.155f, 0.114f);
			if (target != null) {
				double dist = Math.sqrt(target.getBoundingBox().getCenter().distanceToSqr(e.getPosition(partial).add(0, GreyMatterSentryEntity.HEAD_Y, 0)));
				g.tint = 0x801810;
				g.box(-0.006f, 0.134f, 0.114f, 0.006f, 0.146f, (float) dist);
			}
			pose.popPose();

			if (sinceShot < 2.2f) {
				pose.pushPose();
				pose.translate(0, head, 0);
				pose.mulPose(Axis.YP.rotationDegrees(-aimYaw));
				pose.mulPose(Axis.XP.rotationDegrees(aimPitch));
				pose.translate(e.shotLeft() ? 0.085f : -0.085f, 0f, 0.68f);
				float s = (1f - sinceShot / 2.2f) * 0.34f + 0.08f;
				Mesh f = new Mesh(pose);
				f.vc = buffers.getBuffer(RenderType.eyes(SOFT));
				f.light = LightTexture.FULL_BRIGHT;
				f.tint = 0xFFC060;
				f.full = true;
				pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
				f.quad(-s, s, 0, s, s, 0, s, -s, 0, -s, -s, 0);
				pose.popPose();
			}
			super.render(e, yaw, partial, pose, buffers, light);
		}
	}
}
