package com.projecthero.mod.client.ironman;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.IronManBladeLook;
import com.projecthero.mod.ironman.item.IronManArmorItem;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (user): the Mk VII Silver Centurion's blades -- rebuilt on Wolverine's claws ("make it more similar to
 * wolverines claws but just 1 blade"). ONE blade per gauntlet: a long, thin, slightly curved blade with a diamond
 * cross-section (a bright bevelled cutting edge, a darker spine, two flat faces) tapering to a needle point. It lies along
 * the back of the hand, slides out of a small red-and-silver housing on the back of the gauntlet, runs over the knuckles
 * and on past the fist, bending gently in toward the line of the fist like a claw. Polished silver with a bright edge
 * highlight and a faint cool glint that runs along the edge; no glow slab.
 * <p>Drawn in each arm's own space, so it follows every swing and ability pose (the Blade Whirl included) for every
 * viewer (the {@code IRON_MAN_BLADES} flag is synced); {@link #renderFirstPerson} draws the same blade, longer (as
 * Wolverine's first-person claws), over each first-person fist. The extension is {@link IronManBladeClient}'s, snapped
 * (fast out, settling -- the "snikt") and the hidden part is clipped at the housing's mouth, so it really slides out of
 * the housing and back in.
 */
public class CenturionBladeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/mutation/white.png");
	private static final String SUIT = "mark_6";
	private static final float PX = 1f / 16f;
	private static final int EDGE = 0xF6F9FF, FACE = 0xD4DAE3, SPINE = 0xA3ACB9;
	private static final int H_SILVER = 0xC9CFD8, H_DARK = 0x5E6672, H_RED = 0xB51D2A, H_RED_DARK = 0x6A0E16, SLOT = 0x15181D;
	private static final int GLINT = 0xCFEFFF;
	/** Arm pixels (y down from the shoulder pivot; the fist ends at y 10): the housing on the back of the gauntlet. */
	private static final float HOUSING_TOP = 5.4f, HOUSING_BOTTOM = 8.6f;
	/** The blade's root (inside the housing) and its full length: on the body, and in first person (as Wolverine's). */
	private static final float ROOT_Y = 5.8f, WORLD_LENGTH = 14.5f, FIRST_PERSON_LENGTH = 19f;
	/** How far proud of the arm's outer face the blade's spine runs (the gauntlet is ~1 px proud of the arm). */
	private static final float LIFT = 1.15f;
	/** Blade width (spine to edge) and thickness at the root (px); the bend toward the fist line, root to tip (radians). */
	private static final float WIDTH = 1.5f, THICKNESS = 0.42f, CURVE = 0.34f;
	private static final int SEGMENTS = 18;

	public CenturionBladeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	/** Is the Silver Centurion worn (its blades can show)? */
	public static boolean wearing(Player player) {
		return !player.isInvisible() && player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof IronManArmorItem a
				&& SUIT.equals(a.suitId());
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (!wearing(player)) {
			return;
		}
		float ext = IronManBladeClient.extension(player, partialTick);
		if (!IronManBladeLook.visible(ext)) {
			return;
		}
		boolean slim = player.getSkin().model() == PlayerSkin.Model.SLIM;
		PlayerModel<AbstractClientPlayer> model = getParentModel();
		if (model.rightArm.visible) {
			drawOnArm(pose, buffers, light, model.rightArm, true, slim, ext, ageInTicks, WORLD_LENGTH);
		}
		if (model.leftArm.visible) {
			drawOnArm(pose, buffers, light, model.leftArm, false, slim, ext, ageInTicks, WORLD_LENGTH);
		}
	}

	/** First person: the blade on the hand that was just drawn ({@code arm} still holds vanilla's animated pose). */
	public static void renderFirstPerson(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			ModelPart arm, boolean right, float partialTick) {
		if (!wearing(player)) {
			return;
		}
		float ext = IronManBladeClient.extension(player, partialTick);
		if (IronManBladeLook.visible(ext)) {
			drawOnArm(pose, buffers, light, arm, right, player.getSkin().model() == PlayerSkin.Model.SLIM, ext,
					player.tickCount + partialTick, FIRST_PERSON_LENGTH);
		}
	}

	/** The snap of the slide: most of the travel in the first ticks, settling at full length (the "snikt"). */
	static float snap(float ext) {
		float x = Mth.clamp(ext, 0f, 1f);
		return 1f - (1f - x) * (1f - x) * (1f - x);
	}

	private static void drawOnArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right,
			boolean slim, float ext, float age, float length) {
		pose.pushPose();
		arm.translateAndRotate(pose);
		// the arm box: right arm x -3..1 (slim -2..1), left arm x -1..3 (slim -1..2); the outer face (back of the hand)
		// is the far side
		float w = slim ? 3f : 4f;
		float outer = right ? 1f - w : -1f + w;
		float out = right ? -1f : 1f; // outward along x
		drawHousing(pose, buffers, light, outer, out);
		drawBlade(pose, buffers, light, outer, out, snap(ext), age, length);
		pose.popPose();
	}

	/** The housing: a small silver sleeve with a red band on the back of the gauntlet, the blade's dark slot at its mouth. */
	private static void drawHousing(PoseStack pose, MultiBufferSource buffers, int light, float outer, float out) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
		float face = outer + out * (LIFT + 0.75f);
		box(pose, vc, outer + out * 0.6f, HOUSING_TOP, -1.25f, face, HOUSING_BOTTOM, 1.25f, H_SILVER, light);
		box(pose, vc, outer + out * 0.6f, HOUSING_TOP + 0.9f, -1.3f, face + out * 0.06f, HOUSING_TOP + 1.7f, 1.3f, H_RED, light);
		box(pose, vc, outer + out * 0.6f, HOUSING_TOP - 0.25f, -1.05f, face - out * 0.2f, HOUSING_TOP, 1.05f, H_DARK, light);
		// the mouth: a red lip and the dark slot the blade runs through
		box(pose, vc, outer + out * 0.6f, HOUSING_BOTTOM - 0.45f, -1.3f, face + out * 0.06f, HOUSING_BOTTOM, 1.3f, H_RED_DARK, light);
		box(pose, vc, outer + out * (LIFT - 0.15f), HOUSING_BOTTOM - 0.02f, -0.45f, outer + out * (LIFT + WIDTH * 0.5f + 0.2f),
				HOUSING_BOTTOM + 0.04f, 0.45f, SLOT, light);
	}

	/**
	 * The blade: rings along a centreline that starts in the housing, runs down the back of the hand and past the fist,
	 * bending in toward the fist line; every ring a diamond (edge out, spine in toward the arm, front / back faces), the
	 * edge sweeping in to a needle point. Rings still inside the housing are clipped at its mouth.
	 */
	private static void drawBlade(PoseStack pose, MultiBufferSource buffers, int light, float outer, float out, float ext,
			float age, float length) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
		float slide = (1f - ext) * length;
		float step = length / SEGMENTS;
		Vector3f[] edge = new Vector3f[SEGMENTS + 1], spine = new Vector3f[SEGMENTS + 1];
		Vector3f[] front = new Vector3f[SEGMENTS + 1], back = new Vector3f[SEGMENTS + 1];
		float[] cy = new float[SEGMENTS + 1];
		float x = outer + out * (LIFT + WIDTH * 0.4f);
		float y = ROOT_Y - slide;
		for (int k = 0; k <= SEGMENTS; k++) {
			float u = k / (float) SEGMENTS;
			if (k > 0) {
				float am = CURVE * ((k - 0.5f) / SEGMENTS) * ((k - 0.5f) / SEGMENTS);
				x -= out * (float) Math.sin(am) * step; // bends in toward the line of the fist
				y += (float) Math.cos(am) * step;
			}
			float a = CURVE * u * u;
			// across the blade, toward the edge (outward, turning with the bend)
			float nx = out * (float) Math.cos(a), ny = (float) Math.sin(a);
			float e = 0.55f * WIDTH * (float) Math.pow(1f - u, 0.8f);
			float sp = 0.45f * WIDTH * (1f - u * u * u * u);
			float th = 0.5f * THICKNESS * (1f - 0.5f * u) * (u >= 1f ? 0f : 1f);
			float mid = -0.08f * WIDTH * (1f - u);
			edge[k] = new Vector3f(x + nx * e, y + ny * e, 0f);
			spine[k] = new Vector3f(x - nx * sp, y - ny * sp, 0f);
			front[k] = new Vector3f(x + nx * mid, y + ny * mid, -th);
			back[k] = new Vector3f(x + nx * mid, y + ny * mid, th);
			cy[k] = y;
		}
		// clip at the housing's mouth
		int first = 0;
		while (first < SEGMENTS && cy[first + 1] <= HOUSING_BOTTOM) {
			first++;
		}
		if (cy[first] < HOUSING_BOTTOM && first < SEGMENTS) {
			float t = (HOUSING_BOTTOM - cy[first]) / Math.max(1.0e-4f, cy[first + 1] - cy[first]);
			edge[first] = new Vector3f(edge[first]).lerp(edge[first + 1], t);
			spine[first] = new Vector3f(spine[first]).lerp(spine[first + 1], t);
			front[first] = new Vector3f(front[first]).lerp(front[first + 1], t);
			back[first] = new Vector3f(back[first]).lerp(back[first + 1], t);
		}
		if (first >= SEGMENTS) {
			return;
		}
		PoseStack.Pose p = pose.last();
		for (int k = first; k < SEGMENTS; k++) {
			// the two edge bevels bright (the polished cutting edge), the spine faces a darker silver
			face(p, vc, edge[k], front[k], front[k + 1], edge[k + 1], EDGE, light);
			face(p, vc, front[k], spine[k], spine[k + 1], front[k + 1], SPINE, light);
			face(p, vc, spine[k], back[k], back[k + 1], spine[k + 1], SPINE, light);
			face(p, vc, back[k], edge[k], edge[k + 1], back[k + 1], FACE, light);
		}
		// the flat root end at the housing's mouth (seen as the blade starts to come out)
		face(p, vc, edge[first], spine[first], front[first], front[first], FACE, light);
		face(p, vc, spine[first], edge[first], back[first], back[first], FACE, light);
		// the glint: a faint cool highlight running down the edge now and then, only on a fully drawn blade
		if (ext > 0.95f) {
			float g = ((age * 0.07f) % 1.6f) - 0.3f; // 0..1 along the blade, then a pause
			if (g > 0f && g < 1f) {
				VertexConsumer gv = buffers.getBuffer(RenderType.lightning());
				int k = Math.max(first, Math.min(SEGMENTS - 1, (int) (g * SEGMENTS)));
				int alpha = Math.round(120 * (float) Math.sin(Math.PI * g));
				glint(p, gv, edge[k], edge[Math.min(SEGMENTS, k + 2)], out, alpha);
			}
		}
	}

	/** One quad of the blade, with its real normal (and a light shading by facing, so the bevels read). */
	private static void face(PoseStack.Pose p, VertexConsumer vc, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int rgb,
			int light) {
		Vector3f n = new Vector3f(c).sub(a).cross(new Vector3f(d).sub(b));
		if (n.lengthSquared() < 1.0e-8f) {
			n = new Vector3f(b).sub(a).cross(new Vector3f(d).sub(a));
		}
		if (n.lengthSquared() < 1.0e-10f) {
			return;
		}
		n.normalize();
		float shade = 0.82f + 0.18f * Math.abs(n.z) + 0.1f * Math.max(0f, -n.y);
		int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * shade)), g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * shade)),
				bl = Math.min(255, Math.round((rgb & 0xFF) * shade));
		Vector3f[] v = { a, b, c, d };
		for (Vector3f q : v) {
			vc.addVertex(p, q.x * PX, q.y * PX, q.z * PX).setColor(r, g, bl, 255).setUv(0.5f, 0.5f)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, n.x, n.y, n.z);
		}
	}

	/** A thin additive streak just outside the edge between {@code a} and {@code b} (both faces). */
	private static void glint(PoseStack.Pose p, VertexConsumer vc, Vector3f a, Vector3f b, float out, int alpha) {
		int r = (GLINT >> 16) & 0xFF, g = (GLINT >> 8) & 0xFF, bl = GLINT & 0xFF;
		float o = 0.18f * out;
		float[][] q = { { a.x, a.y, -0.25f }, { b.x, b.y, -0.25f }, { b.x + o, b.y, 0.25f }, { a.x + o, a.y, 0.25f } };
		for (int side = 0; side < 2; side++) {
			for (int i = 0; i < 4; i++) {
				float[] v = q[side == 0 ? i : 3 - i];
				vc.addVertex(p, v[0] * PX, v[1] * PX, v[2] * PX).setColor(r, g, bl, alpha);
			}
		}
	}

	/** An axis-aligned box in arm pixels, flat colour, shaded per face (x from {@code x0} toward {@code x1}, any order). */
	private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
			int rgb, int light) {
		PoseStack.Pose p = pose.last();
		float ax = Math.min(x0, x1) * PX, bx = Math.max(x0, x1) * PX;
		float ay = y0 * PX, az = z0 * PX, by = y1 * PX, bz = z1 * PX;
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		quad(vc, p, ax, ay, az, bx, ay, az, bx, by, az, ax, by, az, r, g, b, light, 0, 0, -1, 0.85f);
		quad(vc, p, ax, ay, bz, ax, by, bz, bx, by, bz, bx, ay, bz, r, g, b, light, 0, 0, 1, 0.85f);
		quad(vc, p, ax, ay, az, ax, by, az, ax, by, bz, ax, ay, bz, r, g, b, light, -1, 0, 0, 0.95f);
		quad(vc, p, bx, ay, az, bx, ay, bz, bx, by, bz, bx, by, az, r, g, b, light, 1, 0, 0, 0.95f);
		quad(vc, p, ax, ay, az, ax, ay, bz, bx, ay, bz, bx, ay, az, r, g, b, light, 0, -1, 0, 1.0f);
		quad(vc, p, ax, by, az, bx, by, az, bx, by, bz, ax, by, bz, r, g, b, light, 0, 1, 0, 0.6f);
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose p, float x1, float y1, float z1, float x2, float y2, float z2,
			float x3, float y3, float z3, float x4, float y4, float z4, int r, int g, int b, int light, float nx, float ny,
			float nz, float shade) {
		int sr = Math.round(r * shade), sg = Math.round(g * shade), sb = Math.round(b * shade);
		vc.addVertex(p, x1, y1, z1).setColor(sr, sg, sb, 255).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, x2, y2, z2).setColor(sr, sg, sb, 255).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, x3, y3, z3).setColor(sr, sg, sb, 255).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
		vc.addVertex(p, x4, y4, z4).setColor(sr, sg, sb, 255).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}
}
