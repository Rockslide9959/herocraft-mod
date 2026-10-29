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
import com.projecthero.mod.omnitrix.alien.ripjaws.RipjawsKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: Ripjaws' client kit -- the grey-blue fish-man skin (pale belly, gills, dark trunks) (v0.17.1: the huge jaw is gone, user's skin face instead; it used to be an
 * upper snout and a hinged lower jaw, both lined with teeth, that gape and snap on every bite), a dorsal fin on the head, fins
 * on the forearms, and a FISH TAIL: a short finned tail behind him on land; in water the legs vanish into a long undulating
 * tail. The hydration meter over the keys turns orange, then red, as he dries out. A pose for every move.
 */
public final class RipjawsClient implements AlienClientKit {
	// v0.17.1 (user: "add abilities to the V key for every alien"): the V move's pose and first-person clip
	private static final float[][] BUBBLE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.9f, -0.5f, 0f, -1.9f, 0.5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -0.3f),
			QuirkKeyframes.f(7, -1.2f, 0f, 0.9f, -1.2f, 0f, -0.9f, -0.1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, -0.2f), QuirkKeyframes.rest(14) };
	private static final FirstPersonClip FP_BUBBLE = FirstPersonClip.of(FirstPersonClip.f(0, 0f, 0f, 0f, 0f, 0f, 0f), FirstPersonClip.f(3, -65f, 30f, 0f, 0.1f, 0.35f, 0.1f),
			FirstPersonClip.f(7, -30f, -30f, 20f, -0.2f, 0.15f, 0f), FirstPersonClip.f(14, 0f, 0f, 0f, 0f, 0f, 0f)).both();
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/ripjaws.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/ripjaws_extras.png");

	private static final float[] FIN = { 24, 0, 40, 16 };
	private static final float[] TAIL = { 0, 8, 16, 16 };
	private static final float[] TAIL_FIN = { 40, 0, 56, 16 };

	private static final float[][] BITE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.4f, 0, 0.3f, -0.4f, 0, -0.3f, 0.3f, 0, 0, 0, 0, 0, 0, 0, 0.15f),
			QuirkKeyframes.rest(10) };
	private static final float[][] LUNGE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.9f, 0, 0.3f, 0.9f, 0, -0.3f, 0.55f, 0, 0.4f, 0, 0, 0.4f, 0, 0, -0.5f),
			QuirkKeyframes.f(10, 0.9f, 0, 0.3f, 0.9f, 0, -0.3f, 0.55f, 0, 0.4f, 0, 0, 0.4f, 0, 0, -0.5f), QuirkKeyframes.rest(14) };
	private static final float[][] SLAM = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.4f, 0, 0.8f, -0.4f, 0, -0.8f, 0.1f, 1.3f, 0, 0, 0.2f, 0, 0, -0.2f, 0),
			QuirkKeyframes.f(7, -0.3f, 0, 0.6f, -0.3f, 0, -0.6f, 0.2f, -0.7f, -0.3f, 0, 0, 0.3f, 0, 0, 0.1f), QuirkKeyframes.rest(14) };
	private static final float[][] SLASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.2f, 0, 1.5f, -0.2f, 0, -1.5f, 0, 2.4f, 0, 0, 0.2f, 0, 0, -0.2f, 0),
			QuirkKeyframes.f(8, -0.2f, 0, 1.5f, -0.2f, 0, -1.5f, 0, -2.4f, 0, 0, 0.2f, 0, 0, -0.2f, 0), QuirkKeyframes.rest(12) };
	private static final float[][] DASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.9f, 0, 0.2f, 0.9f, 0, -0.2f, 0.4f, 0, 0.3f, 0, 0, 0.3f, 0, 0, -0.3f),
			QuirkKeyframes.f(12, 0.9f, 0, 0.2f, 0.9f, 0, -0.2f, 0.4f, 0, 0.3f, 0, 0, 0.3f, 0, 0, -0.3f), QuirkKeyframes.rest(16) };
	private static final float[][] BREACH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.9f, 0, 0.15f, -2.9f, 0, -0.15f, -0.1f, 0, 0.2f, 0, 0, 0.2f, 0, 0, -0.3f),
			QuirkKeyframes.f(16, -2.9f, 0, 0.15f, -2.9f, 0, -0.15f, -0.1f, 0, 0.2f, 0, 0, 0.2f, 0, 0, -0.3f), QuirkKeyframes.rest(22) };
	private static final float[][] WHIRL = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.6f, 0, 1.0f, -1.6f, 0, -1.0f, 0, 0.6f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(10, -1.6f, 0, 1.0f, -1.6f, 0, -1.0f, 0, -0.6f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.f(17, -1.6f, 0, 1.0f, -1.6f, 0, -1.0f, 0, 0.6f, 0, 0, 0, 0,
					0, 0, 0), QuirkKeyframes.rest(22) };
	private static final float[][] MUD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, 0.5f, 0, 0.3f, 0.5f, 0, -0.3f, 0.5f, 0, -0.4f, 0, 0, 0.3f, 0, 0, 0.3f),
			QuirkKeyframes.f(6, -1.8f, 0, 0.2f, -1.8f, 0, -0.2f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.2f), QuirkKeyframes.rest(14) };
	private static final float[][] ROAR = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.5f, 0, 1.3f, -0.5f, 0, -1.3f, -0.3f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.7f),
			QuirkKeyframes.f(22, -0.6f, 0, 1.4f, -0.6f, 0, -1.4f, -0.3f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.7f), QuirkKeyframes.rest(28) };
	private static final float[][] FRENZY = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.3f, -0.5f, 0, -1.3f, 0.5f, 0, 0.35f, 0, -0.2f, 0, 0, 0.2f, 0, 0, -0.2f),
			QuirkKeyframes.f(12, -1.4f, -0.6f, 0, -1.4f, 0.6f, 0, 0.35f, 0, -0.2f, 0, 0, 0.2f, 0, 0, -0.2f), QuirkKeyframes.rest(18) };
	private static final float[][] DRENCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.9f, 0, 0.4f, -2.9f, 0, -0.4f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.4f),
			QuirkKeyframes.rest(14) };
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.7f, 0.25f, 0, 0.4f, 0, 0, 0.1f, -0.4f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(8) };
	private static final float[][] DIG = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -0.9f, 0, 0, -0.3f, 0, 0, 0.35f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.rest(6) };
	private static final float[][] GASP = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.9f, -0.6f, 0, 0.1f, 0, -0.1f, -0.1f, 0, 0, 0, 0, 0, 0, 0, -0.6f),
			QuirkKeyframes.rest(16) };

	private static final FirstPersonClip FP_CLAW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -25, 25, 0, 0.15f, 0.1f, -0.2f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_BITE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 20, -10, 0, -0.05f, -0.2f, 0.05f),
			FirstPersonClip.f(10, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_BACK = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 30, 10, 0, 0.05f, -0.35f, 0.2f),
			FirstPersonClip.f(12, 30, 10, 0, 0.05f, -0.35f, 0.2f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -35, -35, 25, -0.2f, 0.2f, 0),
			FirstPersonClip.f(8, -35, 40, 0, 0.3f, 0.2f, -0.1f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_UP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -70, -10, 15, -0.05f, 0.5f, 0.1f),
			FirstPersonClip.f(14, -70, -10, 15, -0.05f, 0.5f, 0.1f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_GRAB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 30, 0, 0.2f, 0.15f, -0.1f),
			FirstPersonClip.f(12, -32, 32, 0, 0.2f, 0.15f, -0.1f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_DIG = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 25, 15, 0, 0.1f, -0.15f, -0.15f),
			FirstPersonClip.f(6, 0, 0, 0, 0, 0, 0));

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	/**
	 * One extras cube, cut-out or translucent ({@code glass}: the fins). The buffer is fetched for every cube: asking a buffer
	 * source for another render type ends the previous one, so two consumers must never be held at once.
	 */
	private static void box(PoseStack pose, MultiBufferSource buffers, boolean glass, float x0, float y0, float z0, float x1, float y1, float z1,
			float[] uv, int light) {
		VertexConsumer vc = buffers.getBuffer(glass ? RenderType.entityTranslucent(EXTRAS) : RenderType.entityCutoutNoCull(EXTRAS));
		AlienParts.box(pose, vc, x0, y0, z0, x1, y1, z1, uv[0] / 64f, uv[1] / 64f, uv[2] / 64f, uv[3] / 64f, 0xFFFFFFFF, light);
	}

	private static boolean wet(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & RipjawsKit.F_WET) != 0 || p.isInWater();
	}

	/** How far the lower jaw hangs open (radians): a gape and a snap on the bites, an idle breath, wide on the roar. */
	private static float gape(OmnitrixState s, float t, float age) {
		return switch (s.animId) {
			case RipjawsKit.ANIM_BITE, RipjawsKit.ANIM_LUNGE -> t < 3 ? 0.8f * t / 3f : t < 5 ? 0.8f * (1f - (t - 3f) / 2f) : 0f;
			case RipjawsKit.ANIM_ROAR, RipjawsKit.ANIM_FRENZY, RipjawsKit.ANIM_GASP -> t < 22 ? 0.75f : 0.1f;
			default -> 0.08f + Mth.sin(age * 0.08f) * 0.05f;
		};
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		OmnitrixState s = Omnitrix.state(p);
		float t = p.level().getGameTime() + partial - s.animStart;
		boolean wet = wet(p);
		if ((s.kitFlags & RipjawsKit.F_BUBBLE) != 0) {
			// v0.17.1: the Water Bubble -- a wobbling globe of water round his head
			float w = 1f + Mth.sin(age * 0.3f) * 0.03f;
			AlienParts.on(pose, model.head, () -> {
				pose.pushPose();
				pose.translate(0, -4.5f / 16f, -1.5f / 16f);
				pose.scale(w, 2f - w, w);
				AlienParts.box(pose, buffers.getBuffer(RenderType.entityTranslucent(EXTRAS)), -6.5f, -6.5f, -7.5f, 6.5f, 6.5f, 5.5f, FIN[0] / 64f, FIN[1] / 64f,
						FIN[2] / 64f, FIN[3] / 64f, 0x5570B8FF, light);
				pose.popPose();
			});
		}
		AlienParts.on(pose, model.head, () -> {
			// v0.17.1 (user: "remove the huge jaws from ripjaws"): no snout, lower jaw, teeth or snout eyes any more -- the user's
			// skin has his face; only the dorsal fin stays on the head
			// the dorsal fin
			box(pose, buffers, true, -0.4f, -12.0f, -2.5f, 0.4f, -8.0f, 4.0f, FIN, light);
		});
		// fins on the forearms
		for (ModelPart arm : new ModelPart[] { model.rightArm, model.leftArm }) {
			boolean right = arm == model.rightArm;
			AlienParts.on(pose, arm, () -> {
				float x = right ? -3.2f : 3.2f;
				box(pose, buffers, true, x - 0.3f, 3.0f, 0.5f, x + 0.3f, 9.0f, 4.5f, FIN, light);
			});
		}
		// the tail: a short one on land; in water the legs become a long undulating one
		AlienParts.on(pose, model.body, () -> {
			pose.pushPose();
			if (wet) {
				pose.translate(0, 11.5f / 16f, 0);
				float[][] seg = { { 3.6f, 2.4f, 5f }, { 3.0f, 2.0f, 5f }, { 2.4f, 1.6f, 4.5f }, { 1.6f, 1.2f, 4f } };
				for (int i = 0; i < seg.length; i++) {
					float[] g = seg[i];
					pose.mulPose(Axis.XP.rotation(Mth.sin(age * 0.35f - i * 0.9f) * 0.22f));
					box(pose, buffers, false, -g[0], 0f, -g[1], g[0], g[2], g[1], TAIL, light);
					pose.translate(0, (g[2] - 0.4f) / 16f, 0);
				}
				pose.mulPose(Axis.XP.rotation(Mth.sin(age * 0.35f - 4 * 0.9f) * 0.3f));
				box(pose, buffers, true, -5.5f, -0.5f, -0.5f, 5.5f, 5.5f, 0.5f, TAIL_FIN, light);
			} else {
				pose.translate(0, 10.5f / 16f, 1.8f / 16f);
				pose.mulPose(Axis.XP.rotation(-0.9f));
				pose.mulPose(Axis.YP.rotation(Mth.sin(age * 0.12f) * 0.15f));
				box(pose, buffers, false, -1.8f, -1.6f, 0f, 1.8f, 1.6f, 4.5f, TAIL, light);
				pose.translate(0, 0, 4.2f / 16f);
				pose.mulPose(Axis.XP.rotation(0.6f));
				box(pose, buffers, false, -1.2f, -1.1f, 0f, 1.2f, 1.1f, 3.5f, TAIL, light);
				pose.translate(0, 0, 3.2f / 16f);
				box(pose, buffers, true, -0.4f, -3.5f, 0f, 0.4f, 3.5f, 3.5f, TAIL_FIN, light);
			}
			pose.popPose();
		});
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		if (wet(p)) {
			// in water the legs are the tail
			m.rightLeg.visible = false;
			m.leftLeg.visible = false;
			if (m instanceof PlayerModel<?> pm) {
				pm.rightPants.visible = false;
				pm.leftPants.visible = false;
			}
		}
		float[][] clip = switch (animId) {
			case RipjawsKit.ANIM_BITE -> BITE;
			case RipjawsKit.ANIM_LUNGE -> LUNGE;
			case RipjawsKit.ANIM_SLAM -> SLAM;
			case RipjawsKit.ANIM_SLASH -> SLASH;
			case RipjawsKit.ANIM_DASH -> DASH;
			case RipjawsKit.ANIM_BREACH -> BREACH;
			case RipjawsKit.ANIM_WHIRL -> WHIRL;
			case RipjawsKit.ANIM_MUD -> MUD;
			case RipjawsKit.ANIM_ROAR -> ROAR;
			case RipjawsKit.ANIM_FRENZY -> FRENZY;
			case RipjawsKit.ANIM_DRENCH -> DRENCH;
			case RipjawsKit.ANIM_PUNCH -> PUNCH;
			case RipjawsKit.ANIM_DIG -> DIG;
			case RipjawsKit.ANIM_GASP -> GASP;
			case RipjawsKit.ANIM_BUBBLE -> BUBBLE;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played && (Omnitrix.state(p).kitFlags & RipjawsKit.F_CHOKING) != 0) {
			// choking on dry land: a hand at the gills
			m.rightArm.xRot = -1.8f;
			m.rightArm.yRot = -0.6f;
			m.head.xRot -= 0.25f;
			return true;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case RipjawsKit.ANIM_PUNCH -> FP_CLAW;
			case RipjawsKit.ANIM_BITE -> FP_BITE;
			case RipjawsKit.ANIM_LUNGE, RipjawsKit.ANIM_DASH -> FP_BACK;
			case RipjawsKit.ANIM_SLAM, RipjawsKit.ANIM_SLASH, RipjawsKit.ANIM_ROAR, RipjawsKit.ANIM_WHIRL -> FP_WIDE;
			case RipjawsKit.ANIM_BREACH, RipjawsKit.ANIM_DRENCH, RipjawsKit.ANIM_MUD -> FP_UP;
			case RipjawsKit.ANIM_FRENZY, RipjawsKit.ANIM_GASP -> FP_GRAB;
			case RipjawsKit.ANIM_DIG -> FP_DIG;
			case RipjawsKit.ANIM_BUBBLE -> FP_BUBBLE;
			default -> null;
		};
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		float x = right ? -3.2f : 3.2f;
		box(pose, buffers, true, x - 0.3f, 3.0f, 0.5f, x + 0.3f, 9.0f, 4.5f, FIN, light);
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		var r = p.getRandom();
		var level = p.level();
		if (p.isInWater()) {
			if (r.nextInt(6) == 0) {
				level.addParticle(ParticleTypes.BUBBLE, p.getX() + (r.nextDouble() - 0.5) * 0.4, p.getY() + p.getBbHeight() * 0.85,
						p.getZ() + (r.nextDouble() - 0.5) * 0.4, 0, 0.05, 0);
			}
			return;
		}
		float hyd = RipjawsKit.hydration(p, level.getGameTime());
		if (hyd > 0.6f && r.nextInt(12) == 0) {
			level.addParticle(ParticleTypes.DRIPPING_WATER, p.getX() + (r.nextDouble() - 0.5) * 0.5, p.getY() + p.getBbHeight() * 0.6,
					p.getZ() + (r.nextDouble() - 0.5) * 0.5, 0, 0, 0);
		} else if (hyd < 0.25f && r.nextInt(8) == 0) {
			level.addParticle(ParticleTypes.WHITE_ASH, p.getX() + (r.nextDouble() - 0.5) * 0.6, p.getY() + r.nextDouble() * p.getBbHeight(),
					p.getZ() + (r.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
		}
	}

	@Override
	public int hudHeight() {
		return 10; // one meter row over the name line
	}

	/**
	 * The hydration meter, in the Omnitrix HUD's meter style: a blue bar (orange under 25%, flashing red when he is choking),
	 * the seconds left, "+" while water or rain refills it.
	 */
	@Override
	public void hud(GuiGraphics g, LocalPlayer p, int x, int y, int w, float partial) {
		Minecraft mc = Minecraft.getInstance();
		long now = p.level().getGameTime();
		int dry = RipjawsKit.dryness(p, now);
		float hyd = 1f - dry / (float) RipjawsKit.MAX_DRY;
		boolean choking = dry >= RipjawsKit.MAX_DRY;
		boolean wet = (Omnitrix.state(p).kitFlags & (RipjawsKit.F_WET | RipjawsKit.F_RAIN)) != 0;
		boolean flash = (now / 5) % 2 == 0;
		int col = choking ? (flash ? 0xFFFF3A2E : 0xFF801810) : hyd < 0.25f ? 0xFFF0A020 : 0xFF3AA8F0;
		int secs = Math.max(0, (RipjawsKit.MAX_DRY - dry) / 20);
		String value = choking ? "!" : wet ? "+" : secs + "s";
		int valueCol = choking ? (flash ? 0xFFFF6A5A : 0xFFE2E2E2) : wet ? 0xFF8AD0FF : hyd < 0.25f ? 0xFFF0C060 : 0xFFE2E2E2;
		com.projecthero.mod.client.omnitrix.OmnitrixHud.meter(g, mc, x, y - 6, Component.translatable("hud.projecthero.omnitrix.ripjaws.meter"), hyd, col,
				value, valueCol);
	}


	@Override
	public float[] badge() {
		return new float[] { 0f, 4.0f, -2.05f, 3.0f };
	}
}
