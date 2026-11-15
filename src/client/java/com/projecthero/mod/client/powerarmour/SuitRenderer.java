package com.projecthero.mod.client.powerarmour;

import java.util.Map;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.ironman.IronManBoxes;
import com.projecthero.mod.client.powerarmour.SuitPanel.Group;
import com.projecthero.mod.client.powerarmour.SuitPanel.Part;
import com.projecthero.mod.powerarmour.ArmourTier;
import com.projecthero.mod.powerarmour.SuitData;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.5 Power Armour: draws a suit. Four voxel layers ({@link SuitMesh}) on the six posed parts:
 * <ol>
 *   <li><b>frame</b> -- the dark under-suit skeleton (servos, cables, ribs), tinted per tier;</li>
 *   <li><b>plates</b> -- the armour plates, greyscale shading times the primary paint, each tier with its own plate shapes;</li>
 *   <li><b>trim</b> -- raised edges and ridges in the secondary paint;</li>
 *   <li><b>glow</b> -- visor, core and light strips in the glow paint, full-bright (dimmed when unpowered).</li>
 * </ol>
 * Every texel of every layer rides its {@link SuitPanel}, so the plates hinge open with their trim and lights on them; pistons
 * stretch between the frame and the opening plates. Modules mount on top ({@link SuitModules}).
 */
public final class SuitRenderer {
	public static final ResourceLocation FRAME_TEX = ProjectHeroMod.id("textures/entity/power_armour/frame.png");
	public static final ResourceLocation MODULE_TEX = ProjectHeroMod.id("textures/entity/power_armour/modules.png");

	public static final float FRAME_IN = 0.2f, FRAME_D = 0.35f;
	public static final float PLATE_IN = 0.6f, PLATE_D = 0.55f;
	public static final float TRIM_IN = 1.15f, TRIM_D = 0.3f;

	private SuitRenderer() {
	}

	public static ResourceLocation plates(ArmourTier t) {
		return ProjectHeroMod.id("textures/entity/power_armour/" + t.id() + "_plates.png");
	}

	public static ResourceLocation trim(ArmourTier t) {
		return ProjectHeroMod.id("textures/entity/power_armour/" + t.id() + "_trim.png");
	}

	public static ResourceLocation glow(ArmourTier t) {
		return ProjectHeroMod.id("textures/entity/power_armour/" + t.id() + "_glow.png");
	}

	/** How the suit looks this frame. */
	public static final class Look {
		public SuitData suit;
		/** Openness per {@link Group} (0 closed .. 1 open), indexed by ordinal. */
		public final float[] open = new float[Group.values().length];
		/** Glow brightness 0..1 (visor lit, powered). */
		public float glow = 1f;
		public float time;
		public boolean thrust;
		public boolean jets;
		/** Arm weapon firing (gatling spin, muzzle glow). */
		public boolean firing;
		/** Show the back module docked (false while the drone / turret is out). */
		public boolean backDocked = true;
		public boolean firstPerson;

		public Look(SuitData suit) {
			this.suit = suit;
		}

		public float open(Group g) {
			return g == Group.NONE ? 0f : open[g.ordinal()];
		}

		/** Sets every group from a sequence progress: {@code opening} plays the open order, otherwise the closing order. */
		public Look sequence(float progress, boolean opening) {
			for (Group g : Group.values()) {
				if (g == Group.NONE) {
					continue;
				}
				float[] w = opening ? SuitPanel.openWindow(g) : SuitPanel.closeWindow(g);
				float k = Mth.clamp((progress - w[0]) / (w[1] - w[0]), 0f, 1f);
				float e = opening ? easeOpen(k) : easeClose(k);
				open[g.ordinal()] = opening ? e : 1f - e;
			}
			return this;
		}

		public Look allOpen(float v) {
			java.util.Arrays.fill(open, v);
			open[Group.NONE.ordinal()] = 0f;
			return this;
		}
	}

	/** Opening: a quick unlatch then a smooth swing. */
	static float easeOpen(float k) {
		return k < 0.12f ? k * 0.4f : 0.048f + (1f - 0.048f) * smooth((k - 0.12f) / 0.88f);
	}

	/** Closing: a swing that lands with a small overshoot and settles (a mechanical clunk). */
	static float easeClose(float k) {
		if (k < 0.8f) {
			float q = k / 0.8f;
			return 1.04f * q * q * (3f - 2f * q);
		}
		float q = (k - 0.8f) / 0.2f;
		return 1.04f - 0.04f * q;
	}

	private static float smooth(float k) {
		return k * k * (3f - 2f * k);
	}

	// ================================================================== whole suit

	/** {@code parts}: head, body, right arm, left arm, right leg, left leg -- already posed. */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, int overlay, ModelPart[] parts, Look look) {
		Part[] order = Part.values();
		for (int i = 0; i < order.length; i++) {
			ModelPart mp = parts[i];
			if (mp == null || !mp.visible) {
				continue;
			}
			pose.pushPose();
			mp.translateAndRotate(pose);
			renderPart(pose, buffers, light, overlay, order[i], look);
			pose.popPose();
		}
	}

	/** One arm in first person ({@code arm} posed by the hand renderer). */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right, Look look) {
		pose.pushPose();
		arm.translateAndRotate(pose);
		renderPart(pose, buffers, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, right ? Part.RIGHT_ARM : Part.LEFT_ARM,
				look);
		pose.popPose();
	}

	public static void renderPart(PoseStack pose, MultiBufferSource buffers, int light, int overlay, Part part, Look look) {
		SuitData suit = look.suit;
		ArmourTier tier = suit.tier;
		int plateLight = tier == ArmourTier.CELESTIAL ? Math.max(light & 0xFFFF, 0xA0) | (light & 0xFFFF0000) : light;
		layer(pose, buffers.getBuffer(RenderType.entityCutout(FRAME_TEX)), SuitMesh.of(FRAME_TEX, FRAME_IN, FRAME_D), part, look,
				tier.frameTint, 1f, light, overlay);
		layer(pose, buffers.getBuffer(RenderType.entityCutout(plates(tier))), SuitMesh.of(plates(tier), PLATE_IN, PLATE_D), part, look,
				suit.primaryColour(), 1f, plateLight, overlay);
		layer(pose, buffers.getBuffer(RenderType.entityCutout(trim(tier))), SuitMesh.of(trim(tier), TRIM_IN, TRIM_D), part, look,
				suit.secondaryColour(), 1f, plateLight, overlay);
		if (look.glow > 0.02f) {
			int g = scale(suit.glowColour(), 0.25f + 0.75f * look.glow);
			layer(pose, buffers.getBuffer(RenderType.eyes(glow(tier))), SuitMesh.of(glow(tier), TRIM_IN, TRIM_D), part, look, g, 1f,
					LightTexture.FULL_BRIGHT, overlay);
		}
		pistons(pose, buffers, light, overlay, part, look);
		SuitModules.render(pose, buffers, light, overlay, part, look);
	}

	private static void layer(PoseStack pose, VertexConsumer vc, SuitMesh mesh, Part part, Look look, int rgb, float alpha, int light,
			int overlay) {
		if (mesh == null) {
			return;
		}
		for (Map.Entry<SuitPanel, float[]> e : mesh.quads(part).entrySet()) {
			SuitPanel panel = e.getKey();
			if (look.firstPerson && panel.group == Group.ARMS && look.open(Group.ARMS) <= 0f) {
				SuitMesh.emit(pose, vc, e.getValue(), rgb, alpha, light, overlay);
				continue;
			}
			pose.pushPose();
			applyPanel(pose, panel, look);
			SuitMesh.emit(pose, vc, e.getValue(), rgb, alpha, light, overlay);
			pose.popPose();
		}
	}

	/** The panel's hinge / slide at its group's openness (parents first). */
	static void applyPanel(PoseStack pose, SuitPanel panel, Look look) {
		if (panel == SuitPanel.STATIC) {
			return;
		}
		SuitPanel parent = panel.parent();
		if (parent != null) {
			applyPanel(pose, parent, look);
		}
		float k = look.open(panel.group);
		if (k <= 0f) {
			return;
		}
		if (panel.angle != 0f) {
			pose.translate(panel.px / 16f, panel.py / 16f, panel.pz / 16f);
			pose.mulPose(panel.axis == 0 ? Axis.XP.rotationDegrees(panel.angle * k) : Axis.YP.rotationDegrees(panel.angle * k));
			pose.translate(-panel.px / 16f, -panel.py / 16f, -panel.pz / 16f);
		}
		if (panel.tx != 0f || panel.ty != 0f || panel.tz != 0f) {
			pose.translate(panel.tx * k / 16f, panel.ty * k / 16f, panel.tz * k / 16f);
		}
	}

	/** Where a point on {@code panel} (part space, px) is once it has opened by its group's amount. */
	static float[] movedPoint(SuitPanel panel, Look look, float x, float y, float z) {
		PoseStack ps = new PoseStack();
		applyPanel(ps, panel, look);
		org.joml.Vector4f v = new org.joml.Vector4f(x / 16f, y / 16f, z / 16f, 1f);
		ps.last().pose().transform(v);
		return new float[] { v.x * 16f, v.y * 16f, v.z * 16f };
	}

	// ================================================================== pistons

	/** Pistons: (panel, anchor on the frame, point on the plate). Drawn while their plate is open. */
	private record Piston(SuitPanel panel, float ax, float ay, float az, float bx, float by, float bz) {
	}

	private static final Piston[] PISTONS = {
			new Piston(SuitPanel.CHEST_R, -3.2f, 2.5f, -1.0f, -1.6f, 2.5f, -2.9f),
			new Piston(SuitPanel.CHEST_L, 3.2f, 2.5f, -1.0f, 1.6f, 2.5f, -2.9f),
			new Piston(SuitPanel.ABDOMEN, -2.5f, 8.0f, -1.0f, -2.5f, 8.6f, -2.9f),
			new Piston(SuitPanel.ABDOMEN, 2.5f, 8.0f, -1.0f, 2.5f, 8.6f, -2.9f),
			new Piston(SuitPanel.BACK_R, -3.2f, 4.0f, 1.0f, -1.4f, 4.0f, 2.9f),
			new Piston(SuitPanel.BACK_R, -3.2f, 9.0f, 1.0f, -1.4f, 9.0f, 2.9f),
			new Piston(SuitPanel.BACK_L, 3.2f, 4.0f, 1.0f, 1.4f, 4.0f, 2.9f),
			new Piston(SuitPanel.BACK_L, 3.2f, 9.0f, 1.0f, 1.4f, 9.0f, 2.9f),
			new Piston(SuitPanel.HELMET, -2.6f, -0.5f, 2.6f, -2.6f, -3.5f, 4.9f),
			new Piston(SuitPanel.HELMET, 2.6f, -0.5f, 2.6f, 2.6f, -3.5f, 4.9f),
			new Piston(SuitPanel.RLEG_BACK, -1.2f, 2.0f, 1.0f, 0.4f, 3.5f, 2.9f),
			new Piston(SuitPanel.LLEG_BACK, 1.2f, 2.0f, 1.0f, -0.4f, 3.5f, 2.9f),
			new Piston(SuitPanel.RARM_FRONT, -2.4f, 3.0f, -0.8f, -0.6f, 3.0f, -2.9f),
			new Piston(SuitPanel.LARM_FRONT, 2.4f, 3.0f, -0.8f, 0.6f, 3.0f, -2.9f),
	};

	private static void pistons(PoseStack pose, MultiBufferSource buffers, int light, int overlay, Part part, Look look) {
		VertexConsumer vc = null;
		for (Piston p : PISTONS) {
			if (p.panel.part != part || look.open(p.panel.group) < 0.03f) {
				continue;
			}
			if (vc == null) {
				vc = buffers.getBuffer(RenderType.entityCutout(MODULE_TEX));
			}
			float[] b = movedPoint(p.panel, look, p.bx, p.by, p.bz);
			rod(pose, vc, p.ax, p.ay, p.az, b[0], b[1], b[2], light, overlay);
		}
	}

	/** A piston from a to b (px): a fat barrel over the first 55%, a bright rod the rest. */
	static void rod(PoseStack pose, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz, int light, int overlay) {
		float dx = bx - ax, dy = by - ay, dz = bz - az;
		float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 0.05f) {
			return;
		}
		pose.pushPose();
		pose.translate(ax / 16f, ay / 16f, az / 16f);
		pose.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, dx / len, dy / len, dz / len));
		IronManBoxes.draw(pose, vc, IronManBoxes.Box.swatch(-0.45f, 0f, -0.45f, 0.45f, len * 0.55f, 0.45f, 1f, 1f, 2f, 2f), 32, 32, light,
				overlay, 0xFFFFFFFF);
		IronManBoxes.draw(pose, vc, IronManBoxes.Box.swatch(-0.22f, len * 0.5f, -0.22f, 0.22f, len, 0.22f, 9f, 1f, 2f, 2f), 32, 32, light,
				overlay, 0xFFFFFFFF);
		pose.popPose();
	}

	static int scale(int rgb, float k) {
		int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * k));
		int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * k));
		int b = Math.min(255, Math.round((rgb & 0xFF) * k));
		return (r << 16) | (g << 8) | b;
	}
}
