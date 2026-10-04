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
 * v0.14.21 round two: a distinct first-person gauntlet per Iron Man mark (it used to be one shared crimson_vanguard
 * arm for all of them). Drawn in the vanilla arm's own animated space ({@code ModelPart#translateAndRotate}), so every
 * swing / bob / place animation carries over, and textured from the mark's own 64x64 suit texture with the same panels
 * the GeckoLib model uses (base arm, arm shell, shoulder panel, gauntlet panel), so the colours match the body.
 *
 * <ul>
 *   <li><b>Mark 1</b> -- bulky, crude: an oversized boxy gauntlet with a thick cuff, a slab shoulder and rivets.</li>
 *   <li><b>Mark 2</b> -- smooth silver: slim rounded gauntlet with an end cap, a domed shoulder.</li>
 *   <li><b>Mark III / 4</b> -- plated: outer forearm plate and knuckle plate, two-tier shoulder (Mark 4 adds a wrist
 *       ring and splits the forearm plate in two).</li>
 *   <li><b>Mark V</b> -- slim, with the blade housing ridge; its blade slides out with the X toggle.</li>
 *   <li><b>Mark 6</b> -- angular: forearm fins and a stepped shoulder.</li>
 *   <li><b>Mark VII</b> -- the sleekest: banded gauntlet, a wrist missile pod, swept shoulder flap.</li>
 * </ul>
 * Every mark but the Mark 1 has a fullbright palm repulsor (a glow on the palm and on the end of the fist). The arm
 * follows the chestplate's build-on reveal (the palm lights only once it is locked in).
 */
public final class IronManFirstPersonGauntlets {
	private static final ResourceLocation PALM_GLOW = ProjectHeroMod.id("textures/misc/repulsor_palm_glow.png");
	private static final int TEX = 64;

	/** Texture panels as box-UV origins {right u, right v, left u, left v} and UV dims {w, h, d}. */
	private enum Panel {
		BASE(40, 16, 32, 48, 4, 12, 4),
		SHELL(40, 32, 48, 48, 4, 12, 4),
		SHOULDER(40, 32, 48, 48, 4, 5, 4),
		GAUNTLET(40, 38, 48, 54, 4, 6, 4),
		/** a narrow strip of the gauntlet panel for plates / bands / rivets */
		TRIM(44, 44, 52, 60, 2, 2, 1);

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

	private static Part p(Panel panel, float x0, float y0, float z0, float x1, float y1, float z1) {
		return new Part(panel, x0, y0, z0, x1, y1, z1, 0f);
	}

	private static Part p(Panel panel, float x0, float y0, float z0, float x1, float y1, float z1, float inflate) {
		return new Part(panel, x0, y0, z0, x1, y1, z1, inflate);
	}

	private static final Part UNDERSUIT = p(Panel.BASE, -3, -2, -2, 1, 10, 2, 0.25f);
	private static final Part SLEEVE = p(Panel.SHELL, -3, -2, -2, 1, 10, 2, 0.45f);

	private static final Map<String, List<Part>> DESIGNS = Map.of(
			"mark_1", List.of(UNDERSUIT,
					p(Panel.SHELL, -3, -2, -2, 1, 4.5f, 2, 0.55f),
					p(Panel.SHOULDER, -4.3f, -2.7f, -3.0f, 1.5f, 2.3f, 3.0f),        // slab shoulder
					p(Panel.GAUNTLET, -3.7f, 4.2f, -2.7f, 1.7f, 10.35f, 2.7f),      // oversized boxy gauntlet
					p(Panel.TRIM, -4.0f, 4.0f, -3.0f, 2.0f, 5.3f, 3.0f),            // thick cuff
					p(Panel.TRIM, -3.5f, 9.3f, -3.0f, 1.5f, 10.4f, -2.6f),          // crude knuckle bar
					// rivets: outer face and back of the gauntlet
					p(Panel.TRIM, -4.05f, 6.0f, -1.9f, -3.7f, 6.6f, -1.3f), p(Panel.TRIM, -4.05f, 6.0f, 1.3f, -3.7f, 6.6f, 1.9f),
					p(Panel.TRIM, -4.05f, 8.6f, -1.9f, -3.7f, 9.2f, -1.3f), p(Panel.TRIM, -4.05f, 8.6f, 1.3f, -3.7f, 9.2f, 1.9f),
					p(Panel.TRIM, -2.6f, 6.6f, -3.05f, -2.0f, 7.2f, -2.7f), p(Panel.TRIM, 0.0f, 6.6f, -3.05f, 0.6f, 7.2f, -2.7f),
					p(Panel.TRIM, -4.6f, -0.6f, -1.0f, -4.3f, 0.6f, 1.0f)),         // shoulder bolt
			"mark_2", List.of(UNDERSUIT, SLEEVE,
					p(Panel.SHOULDER, -3.5f, -2.5f, -2.5f, 1.3f, 1.4f, 2.5f),        // domed shoulder: body ..
					p(Panel.SHOULDER, -3.1f, -2.95f, -2.1f, 0.9f, -2.45f, 2.1f),     // .. and its crown
					p(Panel.GAUNTLET, -3.3f, 4.7f, -2.3f, 1.3f, 9.95f, 2.3f),        // slim, smooth gauntlet
					p(Panel.GAUNTLET, -3.1f, 9.9f, -2.1f, 1.1f, 10.4f, 2.1f)),       // rounded end cap
			"mark_iii", List.of(UNDERSUIT, SLEEVE,
					p(Panel.SHOULDER, -3.7f, -2.6f, -2.6f, 1.4f, 1.5f, 2.6f),
					p(Panel.SHOULDER, -4.0f, -1.6f, -2.2f, -3.6f, 2.3f, 2.2f),       // side tier
					p(Panel.GAUNTLET, -3.4f, 4.4f, -2.4f, 1.4f, 10.2f, 2.4f),
					p(Panel.TRIM, -3.85f, 4.7f, -1.6f, -3.4f, 8.8f, 1.6f),           // outer forearm plate
					p(Panel.TRIM, -3.2f, 9.1f, -2.75f, 1.2f, 10.2f, -2.4f)),         // knuckle plate
			"mark_4", List.of(UNDERSUIT, SLEEVE,
					p(Panel.SHOULDER, -3.7f, -2.6f, -2.6f, 1.4f, 1.5f, 2.6f),
					p(Panel.SHOULDER, -4.0f, -1.6f, -2.2f, -3.6f, 2.3f, 2.2f),
					p(Panel.GAUNTLET, -3.4f, 4.4f, -2.4f, 1.4f, 10.2f, 2.4f),
					p(Panel.TRIM, -3.65f, 4.1f, -2.65f, 1.65f, 4.8f, 2.65f),         // wrist ring
					p(Panel.TRIM, -3.85f, 5.1f, -1.6f, -3.4f, 6.7f, 1.6f),           // split forearm plates
					p(Panel.TRIM, -3.85f, 7.1f, -1.6f, -3.4f, 8.7f, 1.6f),
					p(Panel.TRIM, -3.2f, 9.1f, -2.75f, 1.2f, 10.2f, -2.4f)),
			"mark_v", List.of(UNDERSUIT, SLEEVE,
					p(Panel.SHOULDER, -3.4f, -2.4f, -2.4f, 1.2f, 1.1f, 2.4f),        // slim shoulder
					p(Panel.GAUNTLET, -3.25f, 4.8f, -2.25f, 1.25f, 10.1f, 2.25f),
					p(Panel.TRIM, -3.75f, 4.9f, -0.75f, -3.25f, 8.1f, 0.75f)),       // blade housing ridge
			"mark_6", List.of(UNDERSUIT, SLEEVE,
					p(Panel.SHOULDER, -3.6f, -2.5f, -2.5f, 1.3f, 0.8f, 2.5f),        // stepped, angular shoulder
					p(Panel.SHOULDER, -3.95f, -2.0f, -2.0f, -3.6f, 1.7f, 2.0f),
					p(Panel.SHOULDER, -3.3f, -2.9f, -1.8f, 0.6f, -2.5f, 1.8f),
					p(Panel.GAUNTLET, -3.3f, 4.5f, -2.3f, 1.3f, 10.1f, 2.3f),
					p(Panel.TRIM, -3.8f, 5.0f, -1.0f, -3.3f, 9.2f, 1.0f),            // forearm fin
					p(Panel.TRIM, -3.6f, 5.6f, -1.85f, -3.3f, 7.0f, 1.85f)),
			"mark_vii", List.of(UNDERSUIT, SLEEVE,
					p(Panel.SHOULDER, -3.45f, -2.45f, -2.45f, 1.25f, 0.9f, 2.45f),
					p(Panel.SHOULDER, -3.7f, -0.6f, -1.9f, -3.45f, 2.0f, 1.9f),      // swept flap
					p(Panel.GAUNTLET, -3.2f, 4.6f, -2.2f, 1.2f, 10.05f, 2.2f),
					p(Panel.TRIM, -3.35f, 5.2f, -2.35f, 1.35f, 5.65f, 2.35f),        // three bands
					p(Panel.TRIM, -3.35f, 6.6f, -2.35f, 1.35f, 7.05f, 2.35f),
					p(Panel.TRIM, -3.35f, 8.0f, -2.35f, 1.35f, 8.45f, 2.35f),
					p(Panel.TRIM, -3.75f, 5.4f, -0.9f, -3.2f, 7.6f, 0.9f)));         // wrist missile pod

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
