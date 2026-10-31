package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadBlocks;
import com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadKit;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: Diamondhead's client kit -- the faceted green Petrosapien skin, a pointed crystal head (a stepped diamond turned 45
 * degrees, cheek fins), four faceted crystal spikes out of his back (two long, two short) and shards over his shoulders, and the crystal BLADE that grows out of his
 * right forearm while Blade Arm is on (third and first person). A pose for every move. Also hands his crystal block to the
 * translucent render layer.
 */
public final class DiamondheadClient implements AlienClientKit {
	// v0.17.1 (user: "add abilities to the V key for every alien"): the V move's pose and first-person clip
	private static final float[][] REGROW = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -1.25f, 0.65f, 0f, -1.25f, -0.65f, 0f, 0.3f, 0f, -0.35f, 0f, 0.05f, -0.35f, 0f, -0.05f, 0.35f),
			QuirkKeyframes.f(56, -1.2f, 0.7f, 0f, -1.2f, -0.7f, 0f, 0.3f, 0f, -0.35f, 0f, 0.05f, -0.35f, 0f, -0.05f, 0.3f), QuirkKeyframes.rest(62) };
	private static final FirstPersonClip FP_REGROW = FirstPersonClip.of(FirstPersonClip.f(0, 0f, 0f, 0f, 0f, 0f, 0f), FirstPersonClip.f(4, -40f, 35f, 0f, 0.1f, 0.15f, 0.05f),
			FirstPersonClip.f(56, -40f, 35f, 0f, 0.1f, 0.15f, 0.05f), FirstPersonClip.f(62, 0f, 0f, 0f, 0f, 0f, 0f)).both();
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/diamondhead.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/diamondhead_extras.png");

	private static final float[] CRYSTAL = { 0f, 0f, 0.5f, 0.5f };
	private static final float[] DARK = { 0.5f, 0f, 0.75f, 0.25f };
	private static final float[] GLOW = { 0.75f, 0f, 1f, 0.25f };
	private static final float[] PALE = { 0.5f, 0.25f, 0.75f, 0.5f };

	private static final float[][] VOLLEY = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.3f, 0.6f, -0.3f, -0.3f, -0.6f, 0.3f, -0.1f, 0, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -1.6f, -0.35f, 0, -1.6f, 0.35f, 0, 0.2f, 0, 0.2f, 0, 0, -0.2f, 0, 0, 0), QuirkKeyframes.rest(12) };
	private static final float[][] SPEAR = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.9f, 0.3f, 0.3f, -0.8f, 0, -0.3f, -0.25f, 0.5f, 0.2f, 0, 0, -0.3f, 0, 0, -0.2f),
			QuirkKeyframes.f(7, -1.2f, -0.2f, 0, 0.4f, 0, 0, 0.4f, -0.4f, -0.3f, 0, 0, 0.4f, 0, 0, 0.2f), QuirkKeyframes.rest(15) };
	private static final float[][] BLADE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.4f, -0.7f, 0, 0, 0, 0, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(9, -1.3f, -0.6f, 0.1f, 0, 0, 0, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0.3f), QuirkKeyframes.rest(13) };
	private static final float[][] SPIKES = { QuirkKeyframes.rest(0), QuirkKeyframes.fc(3, -2.9f, 0, 0.2f, -2.9f, 0, -0.2f, -0.2f, 0, 0, 0, 0, 0, 0, 0, -0.3f, 0),
			QuirkKeyframes.fc(6, -0.5f, 0, 0.1f, -0.5f, 0, -0.1f, 0.7f, 0, -0.6f, 0, 0.2f, -0.6f, 0, -0.2f, 0.4f, 0.8f),
			QuirkKeyframes.fc(14, -0.4f, 0, 0.1f, -0.4f, 0, -0.1f, 0.6f, 0, -0.5f, 0, 0.2f, -0.5f, 0, -0.2f, 0.3f, 0.7f), QuirkKeyframes.restC(20) };
	private static final float[][] SLIDE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.2f, 0, 1.3f, -0.2f, 0, -1.3f, 0.2f, 0.9f, -0.4f, 0.3f, 0.2f, 0.3f, 0.3f, -0.2f, 0),
			QuirkKeyframes.f(18, -0.2f, 0, 1.2f, -0.2f, 0, -1.2f, 0.2f, 0.9f, -0.4f, 0.3f, 0.2f, 0.3f, 0.3f, -0.2f, 0), QuirkKeyframes.rest(22) };
	private static final float[][] VAULT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -3.0f, 0, 0.25f, -3.0f, 0, -0.25f, -0.2f, 0, 0.2f, 0, 0.1f, 0.2f, 0, -0.1f, -0.5f),
			QuirkKeyframes.f(14, -2.8f, 0, 0.3f, -2.8f, 0, -0.3f, -0.15f, 0, 0.3f, 0, 0.1f, 0.3f, 0, -0.1f, -0.4f), QuirkKeyframes.rest(20) };
	private static final float[][] WALL = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.5f, -0.3f, 0, -1.5f, 0.3f, 0, 0.3f, 0, -0.5f, 0, 0, 0.5f, 0, 0, 0),
			QuirkKeyframes.f(10, -1.55f, -0.35f, 0, -1.55f, 0.35f, 0, 0.3f, 0, -0.5f, 0, 0, 0.5f, 0, 0, 0), QuirkKeyframes.rest(15) };
	private static final float[][] DOME = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -2.6f, 0, 0.9f, -2.6f, 0, -0.9f, -0.15f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.5f),
			QuirkKeyframes.f(14, -2.5f, 0, 1.0f, -2.5f, 0, -1.0f, -0.15f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.5f), QuirkKeyframes.rest(20) };
	private static final float[][] BARRAGE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -3.1f, 0, 0.1f, -0.3f, 0, -0.4f, -0.2f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.6f),
			QuirkKeyframes.f(12, -3.1f, 0, 0.1f, -0.3f, 0, -0.4f, -0.2f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.6f),
			QuirkKeyframes.f(16, -1.4f, 0, 0, -0.3f, 0, -0.3f, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0.1f), QuirkKeyframes.rest(24) };
	private static final float[][] ARMOUR = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -1.0f, 0.9f, -0.4f, -1.0f, -0.9f, 0.4f, 0.35f, 0, -0.2f, 0, 0.2f, -0.2f, 0, -0.2f, 0.4f),
			QuirkKeyframes.f(8, -0.4f, 0, 0.6f, -0.4f, 0, -0.6f, -0.1f, 0, 0, 0, 0.25f, 0, 0, -0.25f, -0.3f), QuirkKeyframes.rest(16) };
	private static final float[][] SLASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.9f, 0.9f, 0.4f, 0.2f, 0, 0, 0, 0.5f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(5, -1.2f, -1.0f, -0.2f, 0.3f, 0, 0, 0.2f, -0.5f, 0.2f, 0, 0, -0.2f, 0, 0, 0), QuirkKeyframes.rest(10) };
	private static final float[][] PLACE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.1f, 0, 0, 0, 0, 0, 0.15f, 0, 0, 0, 0, 0, 0, 0, 0.3f), QuirkKeyframes.rest(8) };

	private static final FirstPersonClip FP_VOLLEY = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 15, -15, 0, -0.1f, -0.1f, 0.1f),
			FirstPersonClip.f(5, -25, 25, 0, 0.2f, 0.12f, -0.25f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SPEAR = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -80, 0, 15, 0.05f, 0.5f, 0.25f),
			FirstPersonClip.f(7, -10, 25, 0, 0.15f, 0.05f, -0.3f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_BLADE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 45, -30, 0.25f, 0.2f, -0.05f),
			FirstPersonClip.f(9, -28, 42, -28, 0.24f, 0.2f, -0.05f), FirstPersonClip.f(13, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_SLAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -80, 5, 0, 0.05f, 0.5f, 0.2f),
			FirstPersonClip.f(6, 35, 10, 0, 0.05f, -0.35f, -0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUSH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 30, 0, 0.2f, 0.15f, -0.3f),
			FirstPersonClip.f(10, -30, 30, 0, 0.2f, 0.15f, -0.3f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RAISE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -70, -20, 20, -0.1f, 0.45f, 0.1f),
			FirstPersonClip.f(14, -70, -20, 20, -0.1f, 0.45f, 0.1f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SURF = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 10, -45, 40, -0.35f, 0.05f, 0.1f),
			FirstPersonClip.f(18, 10, -45, 40, -0.35f, 0.05f, 0.1f), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SLASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -40, -40, -20, -0.2f, 0.25f, 0),
			FirstPersonClip.f(5, -10, 50, 20, 0.35f, 0.0f, -0.25f), FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_PLACE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -15, 15, 0, 0.1f, 0.0f, -0.2f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_FLEX = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -20, 40, 0, 0.25f, 0.15f, 0.05f),
			FirstPersonClip.f(8, -20, -30, 30, -0.2f, 0.1f, 0), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();

	public DiamondheadClient() {
		// his conjured crystal is see-through
		net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(DiamondheadBlocks.CRYSTAL, RenderType.translucent());
	}

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, float[] uv, int light) {
		AlienParts.box(pose, vc, x0, y0, z0, x1, y1, z1, uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF, light);
	}

	// a crystal's profile along its length: {t, radius scale} -- a base, a facet bulge a third of the way up, the long taper
	private static final float[][] CRYSTAL_PROFILE = { { 0f, 0.85f }, { 0.12f, 1.0f }, { 0.38f, 1.12f }, { 0.78f, 0.55f }, { 1f, 0f } };

	/**
	 * A faceted crystal spike: a flattened hexagonal prism from {@code (x, y, z)} pointing up its local -Y (tipped by {@code rx}
	 * then {@code rz}), {@code r} wide ({@code flat} times as deep), {@code len} long, every facet shaded by which way it faces.
	 */
	private static void crystal(PoseStack pose, VertexConsumer vc, float x, float y, float z, float rx, float rz, float r, float flat, float len, int light) {
		pose.pushPose();
		pose.translate(x / 16f, y / 16f, z / 16f);
		pose.mulPose(Axis.XP.rotation(rx));
		pose.mulPose(Axis.ZP.rotation(rz));
		PoseStack.Pose p = pose.last();
		int n = 6;
		float[][][] ring = new float[CRYSTAL_PROFILE.length][n][3];
		for (int k = 0; k < CRYSTAL_PROFILE.length; k++) {
			float t = CRYSTAL_PROFILE[k][0], rs = CRYSTAL_PROFILE[k][1] * r;
			// each ring a little twisted against the last: the facets read as cut, not lathed
			double twist = k * 0.18;
			for (int i = 0; i < n; i++) {
				double a = i * Math.PI * 2 / n + twist;
				ring[k][i][0] = (float) Math.cos(a) * rs / 16f;
				ring[k][i][1] = -t * len / 16f;
				ring[k][i][2] = (float) Math.sin(a) * rs * flat / 16f;
			}
		}
		for (int k = 0; k < ring.length - 1; k++) {
			for (int i = 0; i < n; i++) {
				float[] a = ring[k][i], b = ring[k][(i + 1) % n], c = ring[k + 1][(i + 1) % n], d = ring[k + 1][i];
				// the facet's outward normal (from the lower edge and the side), and its shade from that: light from the top / front
				float ex = b[0] - a[0], ey = b[1] - a[1], ez = b[2] - a[2];
				float fx = d[0] - a[0], fy = d[1] - a[1], fz = d[2] - a[2];
				float nx = ey * fz - ez * fy, ny = ez * fx - ex * fz, nz = ex * fy - ey * fx;
				float nl = Mth.sqrt(nx * nx + ny * ny + nz * nz);
				if (nl < 1e-7f) {
					// the tip ring is one point: take the side edge against the upper edge instead
					fx = c[0] - b[0];
					fy = c[1] - b[1];
					fz = c[2] - b[2];
					nx = ey * fz - ez * fy;
					ny = ez * fx - ex * fz;
					nz = ex * fy - ey * fx;
					nl = Math.max(1e-7f, Mth.sqrt(nx * nx + ny * ny + nz * nz));
				}
				nx /= nl;
				ny /= nl;
				nz /= nl;
				float lit = -ny * 0.5f + nx * 0.35f - nz * 0.15f;
				float[] uv = lit > 0.08f ? PALE : lit > -0.4f ? CRYSTAL : DARK;
				AlienParts.quad(vc, p, 0xFFFFFFFF, light, a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2], nx, ny, nz, uv[0], uv[1], uv[2],
						uv[3]);
			}
		}
		pose.popPose();
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(EXTRAS));
		// v0.17.1 (user: "remove the spikes on diamondheads head and shoulders"): no crystal crest, cheek or shoulder shards -- the
		// skin's own head; only the four back crystals and the blade are drawn
		// v0.17.1 (user: "he has 4 spikes coming out of his back, 2 longer ones and 2 smaller ones"): two long faceted crystals
		// rising from his shoulder blades in a V past his head, two short chunky ones under them
		AlienParts.on(pose, model.body, () -> {
			for (int side = -1; side <= 1; side += 2) {
				crystal(pose, vc, side * 2.1f, 2.6f, 1.6f, -0.42f, side * 0.5f, 1.7f, 1.1f, 17f, light);
				crystal(pose, vc, side * 1.7f, 7.6f, 1.7f, -0.95f, side * 0.75f, 1.35f, 1.0f, 4.6f, light);
			}
		});
		// the blade
		boolean blade = (Omnitrix.state(p).kitFlags & DiamondheadKit.F_BLADE) != 0;
		if (blade) {
			// last: its glowing edge fetches another buffer type, which ends the translucent one
			AlienParts.on(pose, model.rightArm, () -> blade(pose, buffers, vc, light, age));
		}
	}

	/** The crystal blade on the right forearm (arm-bone space, pixels). */
	private static void blade(PoseStack pose, MultiBufferSource buffers, VertexConsumer vc, int light, float age) {
		box(pose, vc, -1.4f, 4.0f, -2.0f, -0.6f, 18.5f, 2.0f, PALE, light);
		box(pose, vc, -1.3f, 18.5f, -1.2f, -0.7f, 21.5f, 1.2f, PALE, light);
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(EXTRAS));
		float pulse = 0.75f + 0.25f * Mth.sin(age * 0.2f);
		int a = (int) (255 * pulse);
		AlienParts.box(pose, glow, -1.45f, 6.0f, -2.15f, -0.55f, 20.0f, -1.95f, GLOW[0], GLOW[1], GLOW[2], GLOW[3], (a << 24) | 0xFFFFFF,
				LightTexture.FULL_BRIGHT);
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		if (right && (Omnitrix.state(p).kitFlags & DiamondheadKit.F_BLADE) != 0) {
			VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(EXTRAS));
			blade(pose, buffers, vc, light, p.tickCount);
		}
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case DiamondheadKit.ANIM_VOLLEY -> VOLLEY;
			case DiamondheadKit.ANIM_SPEAR -> SPEAR;
			case DiamondheadKit.ANIM_BLADE -> BLADE;
			case DiamondheadKit.ANIM_SPIKES -> SPIKES;
			case DiamondheadKit.ANIM_SLIDE -> SLIDE;
			case DiamondheadKit.ANIM_VAULT -> VAULT;
			case DiamondheadKit.ANIM_WALL -> WALL;
			case DiamondheadKit.ANIM_DOME -> DOME;
			case DiamondheadKit.ANIM_BARRAGE -> BARRAGE;
			case DiamondheadKit.ANIM_ARMOUR -> ARMOUR;
			case DiamondheadKit.ANIM_SLASH -> SLASH;
			case DiamondheadKit.ANIM_PLACE -> PLACE;
			case DiamondheadKit.ANIM_REGROW -> REGROW;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played && (Omnitrix.state(p).kitFlags & DiamondheadKit.F_SLIDING) != 0) {
			// v0.17.2: the surfer's crouch held for the whole ride
			return QuirkKeyframes.play(m, SLIDE, 10f, 1f);
		}
		if (!played && (Omnitrix.state(p).kitFlags & DiamondheadKit.F_BLADE) != 0) {
			// the blade held ready, a little out from the body
			m.rightArm.xRot = m.rightArm.xRot * 0.5f - 0.35f;
			m.rightArm.zRot += 0.15f;
			return true;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case DiamondheadKit.ANIM_VOLLEY, DiamondheadKit.ANIM_BARRAGE -> FP_VOLLEY;
			case DiamondheadKit.ANIM_SPEAR -> FP_SPEAR;
			case DiamondheadKit.ANIM_BLADE -> FP_BLADE;
			case DiamondheadKit.ANIM_SPIKES -> FP_SLAM;
			case DiamondheadKit.ANIM_WALL -> FP_PUSH;
			case DiamondheadKit.ANIM_DOME, DiamondheadKit.ANIM_VAULT -> FP_RAISE;
			case DiamondheadKit.ANIM_SLIDE -> FP_SURF;
			case DiamondheadKit.ANIM_SLASH -> FP_SLASH;
			case DiamondheadKit.ANIM_PLACE -> FP_PLACE;
			case DiamondheadKit.ANIM_ARMOUR -> FP_FLEX;
			case DiamondheadKit.ANIM_REGROW -> FP_REGROW;
			default -> null;
		};
	}

	/** The Crystal Slide's eased ride velocity (CryokinesisClient.ride). */
	private static final double[] RIDE = { Double.NaN, 0 };

	@Override
	public void tick(AbstractClientPlayer p) {
		var r = p.getRandom();
		if (r.nextInt(6) == 0) {
			p.level().addParticle(ParticleTypes.WAX_OFF, p.getX() + (r.nextDouble() - 0.5) * 0.8, p.getY() + r.nextDouble() * p.getBbHeight(),
					p.getZ() + (r.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
		}
		int flags = Omnitrix.state(p).kitFlags;
		if (p instanceof net.minecraft.client.player.LocalPlayer me) {
			// v0.17.2 (user: "work similar to cryokinesis ice slide"): the Ice Slide's ride, on his own client
			com.projecthero.mod.client.mutation.CryokinesisClient.ride(me, (flags & DiamondheadKit.F_SLIDING) != 0, RIDE);
		}
		if ((flags & DiamondheadKit.F_SLIDING) != 0 && p.onGround()) {
			p.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, DiamondheadBlocks.CRYSTAL.defaultBlockState()), p.getX(), p.getY() + 0.1,
					p.getZ(), 0, 0.1, 0);
		}
		if ((flags & DiamondheadKit.F_ARMOUR) != 0 && r.nextInt(2) == 0) {
			p.level().addParticle(ParticleTypes.END_ROD, p.getX() + (r.nextDouble() - 0.5) * 1.2, p.getY() + r.nextDouble() * p.getBbHeight(),
					p.getZ() + (r.nextDouble() - 0.5) * 1.2, 0, 0, 0);
		}
	}

	@Override
	public float[] badge() {
		// v0.17.1 (user: "position our alien badges more on top of those alien badges"): over the skin's own symbol (now painted
		// out), high on his left breast
		// v0.17.2 (user: "its not covering the badge thats on the skin and also make the badge slightly bigger"): the symbol is
		// jacket texels 25..26 (the paint-out had cleared 26..27, a texel to its right), so 1 px in; 3 px across (was 2.4)
		return new float[] { 2.0f, 2.0f, -2.06f, 3.0f };
	}
}
