package com.projecthero.mod.client.ironman;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.armor.SuperheroArmorVisuals;
import com.projecthero.mod.client.ironman.IronManBoxes.Box;
import com.projecthero.mod.ironman.IronManBladeLook;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * The first-person Iron Man arm, per mark. Drawn in the vanilla arm's own animated space ({@code ModelPart#translateAndRotate}),
 * so every swing / bob / place animation carries over, and textured from the mark's own 64x64 suit texture.
 *
 * <p>v0.15.1: every mark is now the user's Blockbench skin model, and the armour must look exactly like it -- so the
 * first-person arm is exactly the model's arm too: the base arm and the arm layer, the same two boxes the third-person
 * geometry uses (the v0.14.21 per-mark plates / cuffs / fins sampled arbitrary panels of the old procedural textures and
 * would add shapes the uploaded models do not have). The Mark V blade still slides out with the X toggle.
 * Every mark but the Mark 1 has a fullbright palm repulsor (a glow on the palm and on the end of the fist). The arm
 * follows the chestplate's build-on reveal (the palm lights only once it is locked in).
 */
public final class IronManFirstPersonGauntlets {
	private static final ResourceLocation PALM_GLOW = ProjectHeroMod.id("textures/misc/repulsor_palm_glow.png");
	private static final int TEX = 64;

	/** Texture panels as box-UV origins {right u, right v, left u, left v} and UV dims {w, h, d}. */
	private enum Panel {
		BASE(40, 16, 32, 48, 4, 12, 4),
		SHELL(40, 32, 48, 48, 4, 12, 4);

		final int ru, rv, lu, lv, w, h, d;

		Panel(int ru, int rv, int lu, int lv, int w, int h, int d) {
			this.ru = ru;
			this.rv = rv;
			this.lu = lu;
			this.lv = lv;
			this.w = w;
			this.h = h;
			this.d = d;
		}
	}

	/** A right-arm part: geometry in vanilla right-arm space (x -3..1 is the arm, -x outward, fist end at y 10). */
	private record Part(Panel panel, float x0, float y0, float z0, float x1, float y1, float z1, float inflate) {
		Box box(boolean right) {
			Box b = Box.uv(x0, y0, z0, x1, y1, z1, inflate, panel.ru, panel.rv, panel.w, panel.h, panel.d);
			return right ? b : b.mirrored(panel.lu, panel.lv);
		}
	}

	private static Part p(Panel panel, float x0, float y0, float z0, float x1, float y1, float z1, float inflate) {
		return new Part(panel, x0, y0, z0, x1, y1, z1, inflate);
	}

	private static final Part UNDERSUIT = p(Panel.BASE, -3, -2, -2, 1, 10, 2, 0.25f);
	private static final Part SLEEVE = p(Panel.SHELL, -3, -2, -2, 1, 10, 2, 0.45f);

	/** v0.15.1: the skin model's arm (base + layer) for every mark -- nothing added. */
	private static final List<Part> MODEL_ARM = List.of(UNDERSUIT, SLEEVE);

	private static final Map<String, List<Part>> DESIGNS = Map.of(
			"mark_1", MODEL_ARM, "mark_2", MODEL_ARM, "mark_iii", MODEL_ARM, "mark_4", MODEL_ARM,
			"mark_v", MODEL_ARM, "mark_6", MODEL_ARM, "mark_vii", MODEL_ARM, "mark_8", MODEL_ARM);

	/** Mark V blade in right-arm space: housing, blade, tip (blade swatch painted at 0..8 x 0..8 of mark_v.png). */
	private static final float BLADE_TOP = 4.6f;

	private IronManFirstPersonGauntlets() {
	}

	public static boolean handles(String setId) {
		return DESIGNS.containsKey(setId);
	}

	/** The gauntlet's boxes for one side (exposed for the asset gametest: every mark has a design). */
	public static List<Box> boxes(String setId, boolean right) {
		List<Box> out = new ArrayList<>();
		for (Part part : DESIGNS.getOrDefault(setId, List.of())) {
			out.add(part.box(right));
		}
		return out;
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, ModelPart vanillaArm, boolean right,
			String setId, Player player, float partialTick) {
		ResourceLocation texture = SuperheroArmorVisuals.get(setId).texture();
		float reveal = IronManSuitReveal.progress(player, EquipmentSlot.CHEST, partialTick);
		texture = IronManSuitReveal.texture(player, setId, EquipmentSlot.CHEST, texture, partialTick);
		pose.pushPose();
		vanillaArm.translateAndRotate(pose);
		// v0.14.21 self-assembly: the gauntlet flies in to the hand on the same timetable as the third-person bone
		if (!IronManAssemblyClient.applyFirstPerson(pose, player, right, partialTick)) {
			pose.popPose();
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.armorCutoutNoCull(texture));
		for (Box b : boxes(setId, right)) {
			IronManBoxes.draw(pose, vc, b, TEX, TEX, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
		}
		if ("mark_v".equals(setId) && reveal >= 1f) { // no blades mid-assembly
			float ext = IronManBladeClient.extension(player, partialTick);
			if (IronManBladeLook.visible(ext)) {
				for (Box b : bladeBoxes(right, ext)) {
					IronManBoxes.draw(pose, vc, b, TEX, TEX, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
				}
			}
		}
		if (!"mark_1".equals(setId) && reveal >= 1f) {
			VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(PALM_GLOW));
			int full = LightTexture.FULL_BRIGHT;
			float[] palm = palm(setId); // {inner face x, fist end y} of this design, right arm
			float palmX = right ? palm[0] : -palm[0]; // palm = the inner face of the hand
			IronManBoxes.glowQuad(pose, glow, 0, right ? 1 : -1, palmX, 8.6f, 0f, 1.25f, full, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
			float endX = right ? -1f : 1f;
			IronManBoxes.glowQuad(pose, glow, 1, 1, endX, palm[1], 0f, 1.1f, full, OverlayTexture.NO_OVERLAY, 0xE0FFFFFF);
		}
		pose.popPose();
	}

	/** Where the palm glow sits for a design (right arm): just outside the widest part around the palm, and the fist end. */
	static float[] palm(String setId) {
		float x = 1.3f;
		float y = 10.3f;
		for (Part part : DESIGNS.getOrDefault(setId, List.of())) {
			float g = part.inflate();
			if (part.y0() - g <= 8.6f && part.y1() + g >= 8.6f) {
				x = Math.max(x, part.x1() + g + 0.04f);
			}
			y = Math.max(y, part.y1() + g + 0.04f);
		}
		return new float[] { x, y };
	}

	/** The first-person Mark V blade at extension {@code ext} (0..1), grown down from the housing like the bone. */
	static List<Box> bladeBoxes(boolean right, float ext) {
		float s = IronManBladeLook.boneScale(ext);
		float len = 10.4f * s;
		float tip = 1.7f * s;
		List<Box> r = new ArrayList<>();
		r.add(Box.swatch(-4.35f, BLADE_TOP, -1.1f, -3.6f, BLADE_TOP + 3.0f * s, 1.1f, 6, 0, 2, 8));     // red housing
		r.add(Box.swatch(-4.1f, BLADE_TOP + 0.4f, -0.9f, -3.75f, BLADE_TOP + 0.4f + len, 0.9f, 0, 0, 5, 8)); // blade
		r.add(Box.swatch(-4.08f, BLADE_TOP + 0.4f + len, -0.42f, -3.77f, BLADE_TOP + 0.4f + len + tip, 0.42f, 0, 0, 5, 8));
		if (!right) {
			r.replaceAll(Box::mirrored);
		}
		return r;
	}
}
