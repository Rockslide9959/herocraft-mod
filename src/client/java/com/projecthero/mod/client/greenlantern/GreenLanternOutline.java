package com.projecthero.mod.client.greenlantern;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLanternSuitStyle;
import com.projecthero.mod.greenlantern.item.GreenLanternArmorItem;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.3 (user: "turn green lantern body glow into a bright green thin layer ... that only covers the outside of his body"):
 * Green Lantern's Ring Flight glow is a thin, bright neon outline round his silhouette -- the comic look -- instead of the
 * translucent skin of light ({@code BodyGlow}, still used by Nova, Gamma and the rest).
 *
 * <p>Technique: an inverted hull. Each posed part (head, body, arms, legs) is redrawn as a box a little bigger than whatever is
 * worn over it (skin overlay, the Lantern suit, armour), and only its FAR faces are drawn (the quads are wound inwards and the
 * render type culls back faces). Inside the body's outline the body hides them; only the thin band past the edge shows. Every
 * vertex is also pushed {@link #PUSH} blocks further away from the camera along its own view ray (its place on screen is
 * unchanged), so a limb's band sinks behind any part of him just behind it -- an arm held across his chest gets no green line
 * over the chest -- and the line only shows against what is behind him: the silhouette alone.
 *
 * <p>Flat colour at full brightness (unlit, so it reads the same by day and night), plus a faint, wider green halo just
 * outside it for the neon bloom. The line keeps a roughly even width on screen: a little thicker in model terms the further
 * away he is.
 *
 * <p>{@link #PUSH} is a trade-off: big enough to sink a limb's band behind the rest of him (0.75 started losing bits of the
 * near arm's line in side views), small enough that a wall or the ground has to be very close behind him to clip the line.
 */
public final class GreenLanternOutline {
	/** The line's colour: a saturated neon green, and the hotter one while boosting. */
	private static final int LINE = 0x3DFF2E;
	private static final int LINE_BOOST = 0x6BFF4F;
	/** The line's width past the worn silhouette, model px, close up (+ {@link #WIDTH_PER_BLOCK} per block of distance). */
	private static final float WIDTH = 0.18f; // v0.17.3 (user: "a bit thick"): was 0.3
	private static final float WIDTH_PER_BLOCK = 0.018f; // was 0.03
	private static final float WIDTH_MAX = 1.0f; // was 1.6
	/** Each vertex goes this far further from the camera along its view ray (blocks). */
	private static final float PUSH = 0.5f;

	private GreenLanternOutline() {
	}

	/**
	 * Draws the outline. {@code strength} 0..1 (the caller's eased fade), {@code boost} 0..1 (thicker and hotter while
	 * sprint-flying), {@code forceWide}: wide arms whatever the skin.
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model,
			float strength, float boost, boolean forceWide) {
		if (player.isInvisible() || strength <= 0.01f) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		// the level's perspective camera sits at the pose origin; a GUI paper doll (or a shadow pass) is orthographic -- no push
		boolean perspective = RenderSystem.getProjectionMatrix().m23() != 0f;
		Matrix4f root = pose.last().pose();
		float dist = perspective ? root.transformPosition(new Vector3f()).length() : 0f;
		float width = Math.min(WIDTH_MAX, WIDTH + WIDTH_PER_BLOCK * dist) * (1f + 0.2f * boost);
		float push = perspective ? PUSH : 0f;
		// what each part wears over the bare model (px): the skin's overlay layer, the Lantern suit or other armour -- the line
		// starts just outside the outermost of them, so it stays thin whatever is worn
		float helm = worn(player, EquipmentSlot.HEAD), chest = worn(player, EquipmentSlot.CHEST);
		float legs = worn(player, EquipmentSlot.LEGS), feet = worn(player, EquipmentSlot.FEET);
		float head = Math.max(helm, model.hat.visible ? 0.5f : 0f);
		float body = Math.max(Math.max(chest, legs), model.jacket.visible ? 0.25f : 0f);
		float rArm = Math.max(chest, model.rightSleeve.visible ? 0.25f : 0f);
		float lArm = Math.max(chest, model.leftSleeve.visible ? 0.25f : 0f);
		float rLeg = Math.max(Math.max(legs, feet), model.rightPants.visible ? 0.25f : 0f);
		float lLeg = Math.max(Math.max(legs, feet), model.leftPants.visible ? 0.25f : 0f);
		// slim skins have 3 px arms, but every suit / armour sleeve is 4 px wide
		boolean slim = !forceWide && chest <= 0f && player.getSkin().model() == PlayerSkin.Model.SLIM;

		int rgb = boost > 0.5f ? LINE_BOOST : LINE;
		// the faint additive bloom first (wider), then the flat line over it
		for (int pass = 0; pass < 2; pass++) {
			VertexConsumer vc = buffers.getBuffer(pass == 0 ? Types.HALO : Types.LINE);
			float w = pass == 0 ? width * 1.6f : width; // halo was 2x
			float a = pass == 0 ? strength * (0.25f + 0.1f * boost) : strength;
			part(vc, pose, model.head, -4, -8, -4, 4, 0, 4, head + w, rgb, a, push);
			part(vc, pose, model.body, -4, 0, -2, 4, 12, 2, body + w, rgb, a, push);
			part(vc, pose, model.rightArm, slim ? -2 : -3, -2, -2, 1, 10, 2, rArm + w, rgb, a, push);
			part(vc, pose, model.leftArm, -1, -2, -2, slim ? 2 : 3, 10, 2, lArm + w, rgb, a, push);
			part(vc, pose, model.rightLeg, -2, 0, -2, 2, 12, 2, rLeg + w, rgb, a, push);
			part(vc, pose, model.leftLeg, -2, 0, -2, 2, 12, 2, lLeg + w, rgb, a, push);
		}
	}

	/**
	 * How far (px) the piece in {@code slot} stands off the bare model: the Lantern suit's own geometry (the Default rig's
	 * mask 0.8 / body 0.55, the skin-based styles' slim rig 0.62 / 0.3), vanilla-style armour 1.0 (leggings 0.5), 0 bare.
	 */
	private static float worn(AbstractClientPlayer player, EquipmentSlot slot) {
		ItemStack stack = player.getItemBySlot(slot);
		if (stack.isEmpty()) {
			return 0f;
		}
		if (stack.getItem() instanceof GreenLanternArmorItem) {
			var st = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
			boolean defaultRig = st == null || GreenLanternSuitStyle.byOrdinal(st.suitStyle) == GreenLanternSuitStyle.DEFAULT;
			return slot == EquipmentSlot.HEAD ? (defaultRig ? 0.8f : 0.62f) : (defaultRig ? 0.55f : 0.3f);
		}
		return slot == EquipmentSlot.LEGS ? 0.5f : 1.0f;
	}

	/** One inflated, inward-wound box (px extents in the part's space) round a posed model part. */
	private static void part(VertexConsumer vc, PoseStack pose, ModelPart p, float x0, float y0, float z0, float x1, float y1, float z1,
			float grow, int rgb, float alpha, float push) {
		if (!p.visible || alpha <= 0.004f) {
			return;
		}
		pose.pushPose();
		p.translateAndRotate(pose);
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		Matrix4f m = pose.last().pose();
		float ax = x0 - grow, ay = y0 - grow, az = z0 - grow;
		float bx = x1 + grow, by = y1 + grow, bz = z1 + grow;
		int argb = (Math.max(0, Math.min(255, Math.round(alpha * 255))) << 24) | (rgb & 0xFFFFFF);
		// the 8 corners (bit 0 = x, 1 = y, 2 = z), transformed and pushed once
		Vector3f[] c = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			Vector3f v = m.transformPosition(new Vector3f((i & 1) == 0 ? ax : bx, (i & 2) == 0 ? ay : by, (i & 4) == 0 ? az : bz));
			float len = v.length();
			if (push > 0f && len > 1.0e-4f) {
				v.mul((len + push) / len);
			}
			c[i] = v;
		}
		pose.popPose();
		// each face listed counter-clockwise seen from outside; quad() emits it reversed, so with back-face culling only the
		// far faces draw
		quad(vc, c[0], c[1], c[5], c[4], argb); // -y
		quad(vc, c[2], c[6], c[7], c[3], argb); // +y
		quad(vc, c[0], c[2], c[3], c[1], argb); // -z
		quad(vc, c[4], c[5], c[7], c[6], argb); // +z
		quad(vc, c[0], c[4], c[6], c[2], argb); // -x
		quad(vc, c[1], c[3], c[7], c[5], argb); // +x
	}

	private static void quad(VertexConsumer vc, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int argb) {
		vc.addVertex(a.x, a.y, a.z).setColor(argb);
		vc.addVertex(d.x, d.y, d.z).setColor(argb);
		vc.addVertex(c.x, c.y, c.z).setColor(argb);
		vc.addVertex(b.x, b.y, b.z).setColor(argb);
	}

	/**
	 * The two render types: untextured position+colour, unlit, depth-tested (the body and walls hide them), no depth write,
	 * BACK faces culled (the quads are wound inwards). {@link #LINE} alpha-blended (keeps its saturated green against a bright
	 * sky); {@link #HALO} is the same, drawn wider and faint (additive light washed out to cyan-white against the day sky).
	 * The subclass only exists to reach the protected shards.
	 */
	private static final class Types extends RenderType {
		static final RenderType LINE = new Types("projecthero_gl_outline", () -> {
			POSITION_COLOR_SHADER.setupRenderState();
			TRANSLUCENT_TRANSPARENCY.setupRenderState();
			COLOR_WRITE.setupRenderState();
			CULL.setupRenderState();
			LEQUAL_DEPTH_TEST.setupRenderState();
		}, () -> {
			LEQUAL_DEPTH_TEST.clearRenderState();
			CULL.clearRenderState();
			COLOR_WRITE.clearRenderState();
			TRANSLUCENT_TRANSPARENCY.clearRenderState();
			POSITION_COLOR_SHADER.clearRenderState();
		});

		static final RenderType HALO = new Types("projecthero_gl_outline_halo", () -> {
			POSITION_COLOR_SHADER.setupRenderState();
			TRANSLUCENT_TRANSPARENCY.setupRenderState();
			COLOR_WRITE.setupRenderState();
			CULL.setupRenderState();
			LEQUAL_DEPTH_TEST.setupRenderState();
		}, () -> {
			LEQUAL_DEPTH_TEST.clearRenderState();
			CULL.clearRenderState();
			COLOR_WRITE.clearRenderState();
			TRANSLUCENT_TRANSPARENCY.clearRenderState();
			POSITION_COLOR_SHADER.clearRenderState();
		});

		private Types(String name, Runnable setup, Runnable clear) {
			super(name, DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 8192, false, false, setup, clear);
		}
	}
}
