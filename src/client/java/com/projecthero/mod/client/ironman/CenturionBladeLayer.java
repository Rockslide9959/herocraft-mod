package com.projecthero.mod.client.ironman;

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
 * v0.16.7 (user): the Mk VII Silver Centurion's forearm blades ("the silver centurion armour should have a blade ability
 * where it deploys blades"). Drawn in each arm's own space (so they follow every swing and ability pose, for every
 * viewer -- the {@code IRON_MAN_BLADES} flag is synced to all): a silver housing with a red stripe on the outside of each
 * gauntlet, and from it a thin silver blade plate with a red spine that slides out along the forearm and past the fist,
 * tapering to a point, with a soft cyan-white glow along the cutting edge. The length is the eased extension from
 * {@link IronManBladeClient} ({@link IronManBladeLook#EXTEND_TICKS} ticks each way), so it slides out and back in;
 * the glow fades in over the last third of the extension. {@link #renderFirstPerson} draws the same blade on the
 * first-person arm.
 */
public class CenturionBladeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/mutation/white.png");
	private static final String SUIT = "mark_6";
	private static final float PX = 1f / 16f;
	private static final int SILVER = 0xDDE3EA, SILVER_DARK = 0x8E96A2, RED = 0xB81C2A, RED_DARK = 0x6A0E16;
	private static final int GLOW = 0xA8E8FF;
	/** Arm pixels: where the housing sits (y down from the shoulder pivot; the fist ends at y 10). */
	private static final float HOUSING_TOP = 4.6f, HOUSING_BOTTOM = 8.8f;
	/** The blade root (inside the housing) and its full length past it. */
	private static final float BLADE_ROOT = 6.0f, BLADE_LEN = 11.5f, TIP = 2.2f;
	/** How far out from the arm's outer face the housing stands (the gauntlet itself is ~1 px proud of the arm). */
	private static final float PROUD = 1.1f;
	/** How far the blade plate reaches out from the housing (its width), and its thickness. */
	private static final float BLADE_W = 1.9f, BLADE_T = 0.22f;

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
			drawOnArm(pose, buffers, light, model.rightArm, true, slim, ext, ageInTicks);
		}
		if (model.leftArm.visible) {
			drawOnArm(pose, buffers, light, model.leftArm, false, slim, ext, ageInTicks);
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
					player.tickCount + partialTick);
		}
	}

	private static void drawOnArm(PoseStack pose, MultiBufferSource buffers, int light, ModelPart arm, boolean right,
			boolean slim, float ext, float age) {
		pose.pushPose();
		arm.translateAndRotate(pose);
		// the arm box: right arm x -3..1 (slim -2..1), left arm x -1..3 (slim -1..2); the outer face is the far side
		float w = slim ? 3f : 4f;
		float outer = right ? 1f - w : -1f + w;
		float out = right ? -1f : 1f; // outward along x
		drawBlade(pose, buffers, light, outer, out, ext, age);
		pose.popPose();
	}

	private static void drawBlade(PoseStack pose, MultiBufferSource buffers, int light, float outer, float out, float ext,
			float age) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
		float face = outer + out * PROUD; // the housing's outer face
		// housing: a silver sleeve with a red stripe, standing proud of the gauntlet
		box(pose, vc, outer + out * 0.2f, HOUSING_TOP, -1.3f, face, HOUSING_BOTTOM, 1.3f, out, SILVER_DARK, light);
		box(pose, vc, face - out * 0.02f, HOUSING_TOP + 1.2f, -1.32f, face + out * 0.05f, HOUSING_TOP + 1.9f, 1.32f, out, RED, light);
		box(pose, vc, face - out * 0.02f, HOUSING_BOTTOM - 0.5f, -1.32f, face + out * 0.05f, HOUSING_BOTTOM - 0.2f, 1.32f, out, RED_DARK, light);
		// the blade: a flat plate standing out from the housing (thin in z), sliding down the arm past the fist
		float len = BLADE_LEN * IronManBladeLook.boneScale(ext);
		float y0 = BLADE_ROOT;
		float body = Math.max(0f, len - TIP);
		float edge = face + out * BLADE_W;
		box(pose, vc, face, y0, -BLADE_T, edge, y0 + body, BLADE_T, out, SILVER, light);
		// the red spine along the inner (housing) side of the blade
		box(pose, vc, face - out * 0.05f, y0, -BLADE_T - 0.04f, face + out * 0.42f, y0 + body, BLADE_T + 0.04f, out, RED, light);
		// the tip: two narrowing steps toward the cutting edge (the point leans outward)
		float tip = Math.min(TIP, len);
		box(pose, vc, face + out * 0.6f, y0 + body, -BLADE_T * 0.8f, edge, y0 + body + tip * 0.55f, BLADE_T * 0.8f, out, SILVER, light);
		box(pose, vc, face + out * 1.25f, y0 + body + tip * 0.55f, -BLADE_T * 0.6f, edge, y0 + body + tip, BLADE_T * 0.6f, out, SILVER, light);
		// the glow along the cutting edge -- additive, fading in over the last third of the extension, a soft shimmer
		float glow = Mth.clamp((ext - 0.66f) / 0.34f, 0f, 1f) * (0.75f + 0.25f * Mth.sin(age * 0.45f));
		if (glow > 0.02f) {
			VertexConsumer gv = buffers.getBuffer(RenderType.lightning());
			int a = Math.round(150 * glow);
			glowBox(pose, gv, edge - out * 0.25f, y0 + 0.4f, -BLADE_T - 0.18f, edge + out * 0.35f, y0 + body + tip * 0.6f, BLADE_T + 0.18f, GLOW, a);
			glowBox(pose, gv, edge - out * 0.6f, y0 + 0.2f, -BLADE_T - 0.4f, edge + out * 0.7f, y0 + body + tip, BLADE_T + 0.4f, GLOW, a / 3);
		}
	}

	/** An axis-aligned box in arm pixels, flat colour, shaded per face (x from {@code x0} toward {@code x1}, any order). */
	private static void box(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
			float out, int rgb, int light) {
		PoseStack.Pose p = pose.last();
		float ax = Math.min(x0, x1) * PX, bx = Math.max(x0, x1) * PX;
		float ay = y0 * PX, az = z0 * PX, by = y1 * PX, bz = z1 * PX;
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		quad(vc, p, ax, ay, az, bx, ay, az, bx, by, az, ax, by, az, r, g, b, light, 0, 0, -1, 0.85f);
		quad(vc, p, ax, ay, bz, ax, by, bz, bx, by, bz, bx, ay, bz, r, g, b, light, 0, 0, 1, 0.85f);
		quad(vc, p, ax, ay, az, ax, by, az, ax, by, bz, ax, ay, bz, r, g, b, light, -1, 0, 0, out < 0 ? 1.0f : 0.7f);
		quad(vc, p, bx, ay, az, bx, ay, bz, bx, by, bz, bx, by, az, r, g, b, light, 1, 0, 0, out > 0 ? 1.0f : 0.7f);
		quad(vc, p, ax, ay, az, ax, ay, bz, bx, ay, bz, bx, ay, az, r, g, b, light, 0, -1, 0, 0.95f);
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

	/** An additive glow box (the lightning render type: position + colour only, both faces). */
	private static void glowBox(PoseStack pose, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
			int rgb, int alpha) {
		PoseStack.Pose p = pose.last();
		float ax = Math.min(x0, x1) * PX, bx = Math.max(x0, x1) * PX;
		float ay = y0 * PX, az = z0 * PX, by = y1 * PX, bz = z1 * PX;
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		float[][] faces = {
				{ ax, ay, az, bx, ay, az, bx, by, az, ax, by, az },
				{ ax, ay, bz, ax, by, bz, bx, by, bz, bx, ay, bz },
				{ ax, ay, az, ax, by, az, ax, by, bz, ax, ay, bz },
				{ bx, ay, az, bx, ay, bz, bx, by, bz, bx, by, az },
		};
		for (float[] f : faces) {
			for (int i = 0; i < 4; i++) {
				vc.addVertex(p, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).setColor(r, g, b, alpha);
			}
			for (int i = 3; i >= 0; i--) {
				vc.addVertex(p, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).setColor(r, g, b, alpha);
			}
		}
	}
}
