package com.projecthero.mod.client.greenlantern.mech;

import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import com.projecthero.mod.client.greenlantern.HardLightDraw;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.greenlantern.mech.GreenLanternMechConfig;
import com.projecthero.mod.greenlantern.mech.HardLightMechEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.Color;

/**
 * v0.17.1: draws the Hard-Light Mech -- the whole model translucent and full-bright ({@code entityTranslucentEmissive}: the
 * dark inner frame and the pilot show through the plates), then an additive pass of the edge-glow texture on top, so
 * the edge lines, the lantern on the chest, the visor and the thrusters burn bright. Rank 5 (Corps Legend) turns it
 * golden-green. It fades out while dissolving and flashes while it assembles.
 *
 * <p>Plus the hard light that is not part of the body: the Chest Lantern Beam, the Hard-Light Shield wall and the Ground
 * Slam's shockwave ring ({@link HardLightDraw}). In first person the pilot sees out of the cockpit: the torso, head and
 * backpack are not drawn for them (the shoulders, arms and fists still are).
 */
public class HardLightMechRenderer extends GeoEntityRenderer<HardLightMechEntity> {
	private static final Set<String> COCKPIT_BONES = Set.of("torso", "head", "thrusters", "jaw", "shades"); // v0.17.1 Gurren rework: + the jaw, the shades
	private boolean hideCockpit;

	public HardLightMechRenderer(EntityRendererProvider.Context context) {
		super(context, new HardLightMechModel());
		this.shadowRadius = 1.6f;
		addRenderLayer(new GlowLayer(this));
	}

	@Override
	public boolean shouldRender(HardLightMechEntity entity, Frustum frustum, double x, double y, double z) {
		return true; // the beam reaches far outside the body
	}

	@Override
	public RenderType getRenderType(HardLightMechEntity animatable, ResourceLocation texture, MultiBufferSource bufferSource,
			float partialTick) {
		return RenderType.entityTranslucentEmissive(texture);
	}

	@Override
	public void render(HardLightMechEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		// v0.17.1 Gurren rework: everything the mech draws in code (weapons, beam, shield, shockwave, forming cells) has green
		// edge lines, not the constructs' near-white ones (HardLightDraw#tint)
		HardLightDraw.mechGreen = true;
		try {
			renderMech(e, yaw, partial, pose, buffers, light);
		} finally {
			HardLightDraw.mechGreen = false;
		}
	}

	private void renderMech(HardLightMechEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		Minecraft mc = Minecraft.getInstance();
		hideCockpit = mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() != null && e.hasPassenger(mc.getCameraEntity());
		// v0.17.1 construct combos: the drill replaces the right fist; the bones' poses are caught for the weapons
		hideFist = MechComboRender.drillScale(e, partial) > 0.3f;
		haveFist = haveForearm = haveForearmL = false;
		buildP = buildProgress(e, partial);
		building = buildP < 1f;
		hotCount = 0;
		try {
			super.render(e, yaw, partial, pose, buffers, LightTexture.FULL_BRIGHT);
		} finally {
			building = false;
			glowPass = false;
		}
		hideCockpit = false;
		hideFist = false;
		HardLightDraw.golden = e.golden();
		if (hotCount > 0) {
			drawHot(HardLightDraw.buffer(buffers));
		}
		try {
			renderFx(e, partial, pose, buffers);
			MechComboRender.render(e, partial, pose, buffers, haveFist ? fistPose : null, haveForearm ? forearmPose : null);
			MechMoveRender.render(e, partial, pose, buffers, haveFist ? fistPose : null, haveForearmL ? forearmLPose : null);
		} finally {
			HardLightDraw.golden = false;
		}
	}

	private boolean hideFist;
	private boolean haveFist;
	private boolean haveForearm;
	private final PoseStack.Pose fistPose = new PoseStack().last();
	private final PoseStack.Pose forearmPose = new PoseStack().last();
	private boolean haveForearmL;
	private final PoseStack.Pose forearmLPose = new PoseStack().last();

	private static void copy(PoseStack.Pose from, PoseStack.Pose to) {
		to.pose().set(from.pose());
		to.normal().set(from.normal());
	}

	/** 0..1 how solid the mech is: it fades out while dissolving (v0.17.1: it builds texel by texel, see {@link #buildProgress}). */
	public static float solidity(HardLightMechEntity e, float partial) {
		float t = e.stateTicks() + partial;
		return switch (e.state()) {
			case HardLightMechEntity.STATE_DISSOLVING -> Mth.clamp(1f - t / GreenLanternMechConfig.DISSOLVE_TICKS, 0f, 1f);
			default -> 1f;
		};
	}

	// ---------------------------------------------------------------- v0.17.1 the pixel-by-pixel build

	/**
	 * v0.17.1 (user: "when the player uses it make it build pixel by pixel piece by piece"): the build runs over the
	 * first {@link GreenLanternMechConfig#BUILD_TICKS} of the assembly, piece by piece -- feet, shins, thighs, hips, torso,
	 * backpack, shoulders, arms, forearms, fists, missile pods and last the head -- each piece materialising texel by texel
	 * (its quads cut into 1-pixel cells, each with its own moment in the piece's window: a sweep along the piece, up the
	 * legs and body, down the arms, scattered), the newest cells white-hot where the light is forming. A piece not reached
	 * yet is a faint blueprint ghost. The mech straightens up once whole and can only be piloted after the assembly ends.
	 */
	public static float buildProgress(HardLightMechEntity e, float partial) {
		if (e.state() != HardLightMechEntity.STATE_ASSEMBLING) {
			return 1f;
		}
		return Mth.clamp((e.stateTicks() + partial) / GreenLanternMechConfig.BUILD_TICKS, 0f, 1f);
	}

	/** Each piece's window of the build (start, end) and whether its sweep runs down (arms) instead of up. */
	private static final java.util.Map<String, float[]> WINDOWS = java.util.Map.ofEntries(
			java.util.Map.entry("foot_r", new float[] { 0.00f, 0.15f, 0 }), java.util.Map.entry("foot_l", new float[] { 0.02f, 0.17f, 0 }),
			java.util.Map.entry("shin_r", new float[] { 0.08f, 0.25f, 0 }), java.util.Map.entry("shin_l", new float[] { 0.10f, 0.27f, 0 }),
			java.util.Map.entry("leg_r", new float[] { 0.18f, 0.34f, 0 }), java.util.Map.entry("leg_l", new float[] { 0.20f, 0.36f, 0 }),
			java.util.Map.entry("pelvis", new float[] { 0.28f, 0.42f, 0 }), java.util.Map.entry("torso", new float[] { 0.36f, 0.58f, 0 }),
			// v0.17.1 Gurren rework: the chest face's jaw forms with the torso, then the shades light up across it
			java.util.Map.entry("jaw", new float[] { 0.40f, 0.56f, 0 }), java.util.Map.entry("shades", new float[] { 0.58f, 0.72f, 0 }),
			java.util.Map.entry("thrusters", new float[] { 0.48f, 0.62f, 0 }),
			java.util.Map.entry("shoulder_r", new float[] { 0.54f, 0.68f, 1 }), java.util.Map.entry("shoulder_l", new float[] { 0.56f, 0.70f, 1 }),
			java.util.Map.entry("arm_r", new float[] { 0.62f, 0.74f, 1 }), java.util.Map.entry("arm_l", new float[] { 0.64f, 0.76f, 1 }),
			java.util.Map.entry("forearm_r", new float[] { 0.70f, 0.82f, 1 }), java.util.Map.entry("forearm_l", new float[] { 0.72f, 0.84f, 1 }),
			java.util.Map.entry("fist_r", new float[] { 0.78f, 0.88f, 1 }), java.util.Map.entry("fist_l", new float[] { 0.80f, 0.90f, 1 }),
			java.util.Map.entry("pod_r", new float[] { 0.80f, 0.90f, 0 }), java.util.Map.entry("pod_l", new float[] { 0.82f, 0.92f, 0 }),
			java.util.Map.entry("head", new float[] { 0.86f, 1.00f, 0 }));

	private boolean building;
	private boolean glowPass;
	private float buildP = 1f;
	/** The bone being drawn: its piece's progress (below 0 not started, 0..1 forming, 1 done), sweep direction and y range. */
	private float boneU = 1f;
	private boolean boneDown;
	private float boneLo, boneHi;
	private final java.util.Map<String, float[]> boneBounds = new java.util.HashMap<>();
	/** The white-hot cells of this frame (camera-space corners + normal, 15 floats each), drawn after the body. */
	private float[] hot = new float[15 * 512];
	private int hotCount;

	private void enterBone(GeoBone bone) {
		if (!building) {
			boneU = 1f;
			return;
		}
		float[] w = WINDOWS.get(bone.getName());
		if (w == null) {
			boneU = 1f;
			return;
		}
		boneU = Math.min(1f, (buildP - w[0]) / (w[1] - w[0]));
		boneDown = w[2] > 0f;
		float[] b = boneBounds.computeIfAbsent(bone.getName(), k -> {
			float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
			for (GeoCube c : bone.getCubes()) {
				for (GeoQuad q : c.quads()) {
					if (q == null) {
						continue;
					}
					for (GeoVertex v : q.vertices()) {
						lo = Math.min(lo, v.position().y());
						hi = Math.max(hi, v.position().y());
					}
				}
			}
			return lo > hi ? new float[] { 0f, 1f } : new float[] { lo, Math.max(hi, lo + 0.01f) };
		});
		boneLo = b[0];
		boneHi = b[1];
	}

	@Override
	public void createVerticesOfQuad(GeoQuad quad, Matrix4f m, Vector3f normal, VertexConsumer buffer, int light, int overlay, int colour) {
		if (!building || boneU >= 1f) {
			super.createVerticesOfQuad(quad, m, normal, buffer, light, overlay, colour);
			return;
		}
		if (boneU <= 0f) {
			if (!glowPass) {
				// not reached yet: a faint blueprint ghost of the piece
				int a = Math.round(((colour >>> 24) & 0xFF) * 0.08f);
				super.createVerticesOfQuad(quad, m, normal, buffer, light, overlay, (a << 24) | (colour & 0xFFFFFF));
			}
			return;
		}
		GeoVertex[] v = quad.vertices();
		Vector3f p0 = v[0].position(), p1 = v[1].position(), p3 = v[3].position();
		// GeckoLib vertex positions are in blocks: one cell per model pixel (capped for the biggest faces)
		int n1 = Mth.clamp(Mth.ceil(p0.distance(p1) * 16f - 0.01f), 1, 48);
		int n2 = Mth.clamp(Mth.ceil(p0.distance(p3) * 16f - 0.01f), 1, 48);
		Vector4f tmp = new Vector4f();
		float[] cell = new float[20];
		for (int i = 0; i < n1; i++) {
			for (int j = 0; j < n2; j++) {
				float s0 = i / (float) n1, s1 = (i + 1) / (float) n1, t0 = j / (float) n2, t1 = (j + 1) / (float) n2;
				// the cell's moment: a sweep along the piece plus a scatter
				float cy = bilerp(v, (s0 + s1) * 0.5f, (t0 + t1) * 0.5f, 1);
				float cx = bilerp(v, (s0 + s1) * 0.5f, (t0 + t1) * 0.5f, 0);
				float cz = bilerp(v, (s0 + s1) * 0.5f, (t0 + t1) * 0.5f, 2);
				float sweep = (cy - boneLo) / (boneHi - boneLo);
				if (boneDown) {
					sweep = 1f - sweep;
				}
				float t = (0.6f * Mth.clamp(sweep, 0f, 1f) + 0.4f * hash(Math.round(cx * 160f), Math.round(cy * 160f), Math.round(cz * 160f))) * 0.9f;
				if (t > boneU) {
					continue;
				}
				boolean isHot = boneU - t < 0.1f;
				if (glowPass && isHot) {
					continue;
				}
				float[][] st = { { s0, t0 }, { s1, t0 }, { s1, t1 }, { s0, t1 } };
				for (int k = 0; k < 4; k++) {
					float s = st[k][0], tt = st[k][1];
					tmp.set(bilerp(v, s, tt, 0), bilerp(v, s, tt, 1), bilerp(v, s, tt, 2), 1f);
					m.transform(tmp);
					cell[k * 5] = tmp.x;
					cell[k * 5 + 1] = tmp.y;
					cell[k * 5 + 2] = tmp.z;
					cell[k * 5 + 3] = bilerp(v, s, tt, 3);
					cell[k * 5 + 4] = bilerp(v, s, tt, 4);
				}
				if (isHot) {
					addHot(cell, normal);
					continue;
				}
				for (int k = 0; k < 4; k++) {
					buffer.addVertex(cell[k * 5], cell[k * 5 + 1], cell[k * 5 + 2], colour, cell[k * 5 + 3], cell[k * 5 + 4], overlay, light,
							normal.x(), normal.y(), normal.z());
				}
			}
		}
	}

	/** Bilinear over the quad's four corners: {@code c} 0-2 = position x/y/z, 3-4 = u/v. */
	private static float bilerp(GeoVertex[] v, float s, float t, int c) {
		float a = comp(v[0], c), b = comp(v[1], c), cc = comp(v[2], c), d = comp(v[3], c);
		return (1 - s) * (1 - t) * a + s * (1 - t) * b + s * t * cc + (1 - s) * t * d;
	}

	private static float comp(GeoVertex v, int c) {
		return switch (c) {
			case 0 -> v.position().x();
			case 1 -> v.position().y();
			case 2 -> v.position().z();
			case 3 -> v.texU();
			default -> v.texV();
		};
	}

	private static float hash(int x, int y, int z) {
		int n = x * 73856093 ^ y * 19349663 ^ z * 83492791;
		n = (n << 13) ^ n;
		n = n * (n * n * 15731 + 789221) + 1376312589;
		return (n & 0x7fffffff) / (float) 0x7fffffff;
	}

	private void addHot(float[] cell, Vector3f normal) {
		if ((hotCount + 1) * 15 > hot.length) {
			if (hot.length >= 15 * 65536) {
				return;
			}
			hot = java.util.Arrays.copyOf(hot, hot.length * 2);
		}
		int o = hotCount * 15;
		for (int k = 0; k < 4; k++) {
			hot[o + k * 3] = cell[k * 5];
			hot[o + k * 3 + 1] = cell[k * 5 + 1];
			hot[o + k * 3 + 2] = cell[k * 5 + 2];
		}
		hot[o + 12] = normal.x();
		hot[o + 13] = normal.y();
		hot[o + 14] = normal.z();
		hotCount++;
	}

	/** The white-hot cells where the light is still forming (they are already in camera space). */
	private void drawHot(VertexConsumer vc) {
		int rgb = HardLightDraw.tint(HardLightDraw.HOT);
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		for (int q = 0; q < hotCount; q++) {
			int o = q * 15;
			for (int k = 0; k < 4; k++) {
				vc.addVertex(hot[o + k * 3], hot[o + k * 3 + 1], hot[o + k * 3 + 2]).setColor(r, g, b, 235).setUv(0.5f, 0.5f)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(hot[o + 12], hot[o + 13], hot[o + 14]);
			}
		}
		hotCount = 0;
	}

	@Override
	public Color getRenderColor(HardLightMechEntity e, float partialTick, int packedLight) {
		float a = solidity(e, partialTick);
		if (e.golden()) {
			return Color.ofRGBA(1.0f, 0.95f, 0.5f, a);
		}
		return Color.ofRGBA(1.0f, 1.0f, 1.0f, a);
	}

	@Override
	public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
		String name = bone.getName();
		enterBone(bone); // v0.17.1: the piece's place in the build
		if ("fist_r".equals(name)) {
			copy(poseStack.last(), fistPose);
			haveFist = true;
			if (hideFist) {
				return;
			}
		} else if ("forearm_r".equals(name)) {
			copy(poseStack.last(), forearmPose);
			haveForearm = true;
		} else if ("forearm_l".equals(name)) {
			copy(poseStack.last(), forearmLPose); // v0.17.1 round 2: the Lantern Cannon's arm
			haveForearmL = true;
		}
		if (hideCockpit && COCKPIT_BONES.contains(name)) {
			return;
		}
		super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
	}

	// ---------------------------------------------------------------- beam / shield / shockwave

	private void renderFx(HardLightMechEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		VertexConsumer vc = HardLightDraw.buffer(buffers);
		float age = e.tickCount + partial;
		Vec3 origin = e.getPosition(partial);
		float bodyYaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
		if (e.hasFlag(HardLightMechEntity.FLAG_BEAM) && e.isActive()) {
			beam(e, vc, pose, origin, bodyYaw, age);
		}
		if (e.hasFlag(HardLightMechEntity.FLAG_SHIELD) && e.isActive()) {
			shield(vc, pose, bodyYaw, age);
		}
		float since = (float) (e.level().getGameTime() - e.slamAt()) + partial;
		if (since >= 0f && since < 16f) {
			slamRing(vc, pose, bodyYaw, since);
		}
	}

	private static Vec3 local(Vec3 origin, float yawDeg, double x, double y, double z) {
		double yaw = Math.toRadians(yawDeg);
		double sin = Math.sin(yaw), cos = Math.cos(yaw);
		return new Vec3(x * cos - z * sin, y, x * sin + z * cos);
	}

	private static void beam(HardLightMechEntity e, VertexConsumer vc, PoseStack pose, Vec3 origin, float yaw, float age) {
		Vec3 chest = local(origin, yaw, 0, 4.78, 1.0); // v0.17.1 Gurren rework: out of the chest face's mouth
		Vec3 end = e.beamEnd().subtract(origin);
		Vec3 d = end.subtract(chest);
		float len = (float) d.length();
		if (len < 0.5f) {
			return;
		}
		pose.pushPose();
		pose.translate(chest.x, chest.y, chest.z);
		float ry = (float) Math.atan2(d.x, d.z);
		float rx = (float) -Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z));
		pose.mulPose(Axis.YP.rotation(ry));
		pose.mulPose(Axis.XP.rotation(rx));
		float pulse = 1f + 0.12f * Mth.sin(age * 1.3f);
		HardLightDraw.streak(vc, pose, len, 0.62f * pulse, age, 0.95f);
		HardLightDraw.streak(vc, pose, len, 1.05f * pulse, -age, 0.35f);
		// the lantern on the chest blazes, and a flare where the beam lands
		HardLightDraw.emblem(vc, pose, 1.3f * pulse, 0.7f); // v0.17.1 Gurren rework: smaller, it rings the open mouth
		HardLightDraw.glow(vc, pose, 0f, 0f, 0.05f, 0.26f * pulse, 0.2f * pulse, 0.06f, 0.6f);
		pose.translate(0f, 0f, len);
		pose.mulPose(Axis.ZP.rotationDegrees(age * 12f));
		HardLightDraw.glow(vc, pose, 0f, 0f, 0f, 0.55f * pulse, 0.55f * pulse, 0.55f * pulse, 0.85f);
		TurboDraw.box(vc, pose.last(), 1.2f * pulse, 1.2f * pulse, 0.04f, HardLightDraw.tint(HardLightDraw.GREEN), 0.3f);
		pose.popPose();
	}

	/** A wall of hard-light hexes in front of the mech, the size of its body, shimmering. */
	private static void shield(VertexConsumer vc, PoseStack pose, float yaw, float age) {
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-yaw));
		pose.translate(0f, 3.6f, 2.7f); // v0.17.1 Gurren rework: sized to the taller body
		float w = 2.9f, h = 3.7f;
		// the frame
		HardLightDraw.part(vc, pose, 0f, h, 0f, w, 0.09f, 0.09f, 0.9f);
		HardLightDraw.part(vc, pose, 0f, -h, 0f, w, 0.09f, 0.09f, 0.9f);
		HardLightDraw.part(vc, pose, -w, 0f, 0f, 0.09f, h, 0.09f, 0.9f);
		HardLightDraw.part(vc, pose, w, 0f, 0f, 0.09f, h, 0.09f, 0.9f);
		// the cells: a staggered honeycomb of thin panes, each breathing on its own
		int cols = 6, rows = 7;
		float cw = w * 2f / cols, ch = h * 2f / rows;
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < cols; c++) {
				float x = -w + cw * (c + 0.5f) + (r % 2 == 0 ? 0f : cw * 0.25f) - cw * 0.125f;
				float y = -h + ch * (r + 0.5f);
				float k = 0.55f + 0.45f * Mth.sin(age * 0.25f + c * 1.3f + r * 0.9f);
				pose.pushPose();
				pose.translate(x, y, 0f);
				TurboDraw.box(vc, pose.last(), cw * 0.40f, ch * 0.38f, 0.03f, HardLightDraw.tint(HardLightDraw.GREEN), 0.16f + 0.14f * k);
				TurboDraw.box(vc, pose.last(), cw * 0.46f, ch * 0.44f, 0.035f, HardLightDraw.tint(HardLightDraw.PALE), 0.08f + 0.1f * k);
				pose.popPose();
			}
		}
		HardLightDraw.emblem(vc, pose, 2.2f, 0.55f + 0.2f * Mth.sin(age * 0.3f));
		pose.popPose();
	}

	/** The Ground Slam's shockwave: a ring of hard light racing out along the ground and fading. */
	private static void slamRing(VertexConsumer vc, PoseStack pose, float yaw, float since) {
		float k = Math.min(1f, since / GreenLanternMechConfig.SLAM_WAVE_TICKS);
		float r = (float) (1.2 + (GreenLanternMechConfig.SLAM_RADIUS - 1.2) * (1f - (1f - k) * (1f - k)));
		float alpha = since < GreenLanternMechConfig.SLAM_WAVE_TICKS ? 0.95f : Math.max(0f, 0.95f * (1f - (since - GreenLanternMechConfig.SLAM_WAVE_TICKS) / 6f));
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-yaw));
		pose.translate(0f, 0.15f, 1.6f);
		int n = 40;
		float seg = (float) (Math.PI * 2 * r / n) * 0.55f;
		for (int i = 0; i < n; i++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotation((float) (i * Math.PI * 2 / n)));
			pose.translate(0f, 0f, r);
			float hgt = 0.35f + 0.5f * (1f - k);
			HardLightDraw.part(vc, pose, 0f, hgt * 0.5f, 0f, seg, hgt * 0.5f, 0.07f, alpha);
			TurboDraw.box(vc, pose.last(), seg, 0.03f, 0.5f * (1f - k) + 0.12f, HardLightDraw.tint(HardLightDraw.PALE), 0.35f * alpha);
			pose.popPose();
		}
		pose.popPose();
	}

	/** The edge lines, the lantern, the visor and the thrusters, additive on top (a real glow over the translucent body). */
	private static class GlowLayer extends GeoRenderLayer<HardLightMechEntity> {
		GlowLayer(GeoRenderer<HardLightMechEntity> renderer) {
			super(renderer);
		}

		@Override
		public void render(PoseStack poseStack, HardLightMechEntity animatable, BakedGeoModel bakedModel, RenderType renderType,
				MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
			float a = solidity(animatable, partialTick);
			// v0.17.1: no flicker while assembling any more -- the build itself (texel by texel) is the show
			float pulse = 0.85f + 0.15f * Mth.sin((animatable.tickCount + partialTick) * 0.12f);
			float k = Mth.clamp(a * pulse, 0f, 1f);
			int c = animatable.golden()
					? Color.ofRGBA(1.0f * k, 0.9f * k, 0.35f * k, 1f).argbInt()
					: Color.ofRGBA(k, k, k, 1f).argbInt();
			RenderType type = RenderType.eyes(HardLightMechModel.GLOW);
			HardLightMechRenderer r = (HardLightMechRenderer) getRenderer();
			r.glowPass = true;
			try {
				getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type), partialTick,
						LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, c);
			} finally {
				r.glowPass = false;
			}
		}
	}
}
