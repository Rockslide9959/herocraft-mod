package com.projecthero.mod.client.quirk.overhaul;

import static com.projecthero.mod.client.quirk.QuirkFirstPersonPose.clip;
import static com.projecthero.mod.client.quirk.QuirkFirstPersonPose.f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.quirk.QuirkClient;
import com.projecthero.mod.client.quirk.QuirkClientKit;
import com.projecthero.mod.client.quirk.QuirkFirstPersonPose;
import com.projecthero.mod.client.quirk.QuirkHud;
import com.projecthero.mod.client.quirk.fx.QuirkFxDraw;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.overhaul.Overhaul;
import com.projecthero.mod.quirk.overhaul.OverhaulConfig;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 Overhaul revamp: the Quirk's client side --
 * <ul>
 *   <li>the keyframed third-person poses ({@link OverhaulPose}) and the Fusion form's plates and extra stone arms
 *       ({@link OverhaulFusionLayer});</li>
 *   <li>a first-person arm clip for every move (the touch, the palm slapped to the floor, both palms down, the heave, the
 *       upward sweep...), and the whole body in first person for Total Disassembly;</li>
 *   <li>the tell: his black gloves, drawn on both hands, gone while a move has tugged them off (bare hands disassemble);</li>
 *   <li>the HUD: Matter as a stack of stone segments over the shared bands, and the hold rings on G / X / V.</li>
 * </ul>
 * The spikes, shards and cracks are server-spawned toolkit effects every viewer sees.
 */
public final class OverhaulClient implements QuirkClientKit {
	private static final int GLOVE = 0x5A6464;
	private static final int CUFF = 0x7FB0A8;
	/** A dark leather-ish texture for the gloves (vanilla's black concrete, tinted). */
	private static final net.minecraft.resources.ResourceLocation GLOVE_TEX = net.minecraft.resources.ResourceLocation
			.withDefaultNamespace("textures/block/black_concrete_powder.png");

	private OverhaulClient() {
	}

	public static void initialize() {
		QuirkClient.register(Quirk.OVERHAUL, new OverhaulClient());
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new OverhaulFusionLayer(playerRenderer));
			}
		});
		registerFirstPerson();
	}

	/** First-person arms (pitch negative = up into view; yaw + toward the centre; offsets x centre-ward, y up, z into the screen). */
	private static void registerFirstPerson() {
		Quirk q = Quirk.OVERHAUL;
		// R: the glove tug, then the two-finger touch thrust out at the target
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_DISASSEMBLE, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(2, -14, 20, 0, 0.12f, 0.06f, 0.02f, -22, 34, 0, 0.22f, 0.08f, -0.06f),
				f(4, 12, -8, -18, -0.05f, -0.06f, 0.12f, 6, 10, 0, 0.05f, -0.05f, 0.05f),
				f(6, -30, 20, 12, 0.14f, 0.14f, -0.38f, 0, 0, 0, 0, 0, 0),
				f(10, -27, 18, 10, 0.13f, 0.13f, -0.34f, 0, 0, 0, 0, 0, 0),
				f(16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// Shift+R: a quick wrist grab, then the wrenching twist
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_DISARM, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(3, -26, 16, 0, 0.1f, 0.12f, -0.28f, 0, 0, 0, 0, 0, 0),
				f(6, -24, 22, 70, 0.1f, 0.1f, -0.26f, 0, 0, 0, 0, 0, 0),
				f(9, -18, 20, 80, 0.08f, 0.08f, -0.2f, 0, 0, 0, 0, 0, 0),
				f(14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
		// G: the right palm slapped to the floor
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_SPIKE_WAVE, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(2, -40, 8, 0, 0.02f, 0.22f, -0.12f, 0, 0, 0, 0, 0, 0),
				f(3, 48, 14, 0, 0.08f, -0.36f, -0.24f, 0, 0, 0, 0, 0, 0),
				f(10, 46, 14, 0, 0.08f, -0.34f, -0.22f, 0, 0, 0, 0, 0, 0),
				f(16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
		// Shift+G: both palms flat on the ground
		float[] down = { 50, 16, 0, 0.12f, -0.4f, -0.26f };
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_SPIKE_FIELD, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(2, -42, 10, 0, 0.04f, 0.24f, -0.14f, -42, 10, 0, 0.04f, 0.24f, -0.14f),
				f(4, down[0], down[1], down[2], down[3], down[4], down[5], down[0], down[1], down[2], down[3], down[4], down[5]),
				f(12, down[0], down[1], down[2], down[3], down[4], down[5], down[0], down[1], down[2], down[3], down[4], down[5]),
				f(18, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// hold G: palms pressed down and held while he aims (the aim keeps this anim up to its 5 s cap)
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_THORN, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(3, 52, 18, 0, 0.13f, -0.42f, -0.26f, 52, 18, 0, 0.13f, -0.42f, -0.26f),
				f(40, 44, 16, 0, 0.12f, -0.36f, -0.26f, 44, 16, 0, 0.12f, -0.36f, -0.26f),
				f(80, 52, 18, 0, 0.13f, -0.42f, -0.26f, 52, 18, 0, 0.13f, -0.42f, -0.26f),
				f(104, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// C: the palm low, then swept up and out as the shards fly
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_SHRAPNEL, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(2, 32, 6, -10, 0.02f, -0.3f, -0.1f, 0, 0, 0, 0, 0, 0),
				f(5, -52, 22, -20, 0.12f, 0.3f, -0.36f, 0, 0, 0, 0, 0, 0),
				f(10, -48, 20, -18, 0.12f, 0.28f, -0.34f, 0, 0, 0, 0, 0, 0),
				f(16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
		// Shift+C: both palms down, then a two-handed heave up
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_WALL, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(3, down[0], down[1], down[2], down[3], down[4], down[5], down[0], down[1], down[2], down[3], down[4], down[5]),
				f(5, down[0], down[1], down[2], down[3], down[4], down[5], down[0], down[1], down[2], down[3], down[4], down[5]),
				f(9, -58, 10, 0, 0.1f, 0.36f, -0.3f, -58, 10, 0, 0.1f, 0.36f, -0.3f),
				f(16, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// Shift+Z: the palm slammed down, then the fist closing up
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_PIT, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(3, 46, 14, 0, 0.08f, -0.36f, -0.22f, 0, 0, 0, 0, 0, 0),
				f(6, 46, 14, 0, 0.08f, -0.36f, -0.22f, 0, 0, 0, 0, 0, 0),
				f(10, -30, 16, 30, 0.1f, 0.14f, -0.3f, 0, 0, 0, 0, 0, 0),
				f(18, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
		// Z: the hand out ahead through the dash
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_STRIKE, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(2, -22, 16, 8, 0.12f, 0.08f, -0.46f, 0, 0, 0, 0, 0, 0),
				f(8, -22, 16, 8, 0.12f, 0.08f, -0.46f, 0, 0, 0, 0, 0, 0),
				f(13, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
		// V: both palms laid on the body (or the ally), then easing out
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_REASSEMBLE, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(5, -18, 34, 0, 0.2f, 0.06f, -0.22f, -18, 34, 0, 0.2f, 0.06f, -0.22f),
				f(12, -20, 30, 0, 0.18f, 0.08f, -0.24f, -20, 30, 0, 0.18f, 0.08f, -0.24f),
				f(20, -32, 8, 0, 0.08f, 0.16f, -0.3f, -32, 8, 0, 0.08f, 0.16f, -0.3f),
				f(26, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// Shift+V: arms crossed as the ground streams in, then flung wide
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_FUSION, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(6, -22, 48, 10, 0.34f, 0.06f, -0.12f, -26, 46, 10, 0.3f, 0.1f, -0.12f),
				f(11, -24, 50, 12, 0.36f, 0.08f, -0.12f, -28, 48, 12, 0.32f, 0.12f, -0.12f),
				f(14, -16, -34, -30, -0.14f, 0.12f, -0.18f, -16, -34, -30, -0.14f, 0.12f, -0.18f),
				f(24, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// Shift+V (Mend): the item cupped in both hands
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_MEND, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(4, -24, 38, 0, 0.26f, 0.1f, -0.22f, -24, 38, 0, 0.26f, 0.1f, -0.22f),
				f(14, -26, 36, 0, 0.25f, 0.12f, -0.22f, -26, 36, 0, 0.25f, 0.12f, -0.22f),
				f(20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// hold V: Restore -- palms pushed out, swept slowly wide
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_RESTORE, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(4, -36, 18, 0, 0.12f, 0.2f, -0.36f, -36, 18, 0, 0.12f, 0.2f, -0.36f),
				f(18, -34, -22, -10, -0.08f, 0.18f, -0.32f, -34, -22, -10, -0.08f, 0.18f, -0.32f),
				f(26, -30, -26, -12, -0.1f, 0.16f, -0.3f, -30, -26, -12, -0.1f, 0.16f, -0.3f),
				f(32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// Shift+X: the palm slapped down, then both arms flung overhead off the pillar
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_PILLAR, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(2, 46, 14, 0, 0.08f, -0.36f, -0.22f, 0, 0, 0, 0, 0, 0),
				f(6, -64, 4, 0, 0.02f, 0.42f, -0.18f, -64, 4, 0, 0.02f, 0.42f, -0.18f),
				f(18, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// X: Ground Shift -- arms a little out for balance, carried
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_SHIFT, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(3, 14, -24, -22, -0.1f, -0.08f, 0.02f, 14, -24, -22, -0.1f, -0.08f, 0.02f),
				f(8, 12, -26, -24, -0.11f, -0.06f, 0.02f, 12, -26, -24, -0.11f, -0.06f, 0.02f),
				f(13, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// hold X: Terrain Surf -- the arms out wide for balance
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_SURF, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(4, 8, -30, -30, -0.14f, 0.0f, 0.04f, 8, -30, -30, -0.14f, 0.0f, 0.04f),
				f(200, 8, -30, -30, -0.14f, 0.0f, 0.04f, 8, -30, -30, -0.14f, 0.0f, 0.04f),
				f(204, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// the bare-hand touch / mining
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_TOUCH, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(3, -24, 16, 0, 0.1f, 0.06f, -0.34f, 0, 0, 0, 0, 0, 0),
				f(8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
		// the glove tug
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_GLOVES_OFF, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(4, -18, 30, 0, 0.18f, 0.06f, -0.12f, -24, 40, 0, 0.26f, 0.1f, -0.14f),
				f(10, -18, 30, 0, 0.18f, 0.06f, -0.12f, -6, -8, -20, -0.06f, -0.04f, 0.02f),
				f(15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		// Total Disassembly: both palms slammed flat and held (the whole body shows too)
		QuirkFirstPersonPose.register(q, Overhaul.ANIM_ULTIMATE, clip(
				f(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				f(2, -50, 8, 0, 0.04f, 0.3f, -0.14f, -50, 8, 0, 0.04f, 0.3f, -0.14f),
				f(4, 54, 18, 0, 0.13f, -0.44f, -0.28f, 54, 18, 0, 0.13f, -0.44f, -0.28f),
				f(58, 52, 18, 0, 0.13f, -0.42f, -0.28f, 52, 18, 0, 0.13f, -0.42f, -0.28f),
				f(66, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).twoHanded());
		QuirkFirstPersonPose.fullBody(q, Overhaul.ANIM_ULTIMATE);
	}

	@Override
	public void applyPose(Player player, HumanoidModel<?> model) {
		OverhaulPose.apply(player, model);
	}

	@Override
	public boolean ultimateHoldPose(Player player, HumanoidModel<?> model, float charge) {
		OverhaulPose.hold(model, charge, player.tickCount);
		return true;
	}

	@Override
	public boolean fullBodyFirstPerson(Player player) {
		return Overhaul.ultimate(player);
	}

	@Override
	public float holdCharge(Player player, AbilitySlot slot) {
		return switch (slot) {
			case SLOT_2 -> Overhaul.thornAiming(player) ? 1f : Overhaul.holdCharge(player, slot, OverhaulConfig.HOLD_TICKS);
			case SLOT_3 -> Overhaul.running(player, Overhaul.V_SURF) ? 1f : Overhaul.holdCharge(player, slot, OverhaulConfig.HOLD_TICKS);
			case SLOT_5 -> Overhaul.holdCharge(player, slot, OverhaulConfig.RESTORE_HOLD_TICKS);
			default -> -1f;
		};
	}

	/** Matter: the shared banded bar, cut into ten stone segments. */
	@Override
	public boolean drawMeterWidget(GuiGraphics g, Player player, QuirkHud.MeterView v) {
		g.fill(v.x() - 1, v.y() - 1, v.x() + v.w() + 1, v.y() + v.h() + 1, 0xFF4A4A4A);
		g.fill(v.x(), v.y(), v.x() + v.w(), v.y() + v.h(), 0xC0141414);
		QuirkHud.drawBands(g, v);
		for (int i = 1; i < 10; i++) {
			int x = v.x() + Math.round(v.w() * i / 10f);
			g.fill(x, v.y(), x + 1, v.y() + v.h(), 0x90101010);
		}
		// a lighter top edge on the filled part: chunks of cut stone
		int fill = Math.round(v.w() * Math.max(0f, Math.min(100f, v.value())) / 100f);
		if (fill > 0) {
			g.fill(v.x(), v.y(), v.x() + fill, v.y() + 1, 0x40FFFFFF);
		}
		return true;
	}

	/**
	 * The tell: his black gloves on both hands (the default), drawn over the skin and any costume; gone while a move has tugged
	 * them off. Returns false so the framework's hold glow still shows at the hands.
	 */
	@Override
	public boolean renderTell(PoseStack pose, MultiBufferSource buffers, int light, Player player, HumanoidModel<?> model, float partial) {
		if (!Overhaul.gloved(player) || Overhaul.fused(player)) {
			return false;
		}
		boolean slim = player instanceof AbstractClientPlayer acp && acp.getSkin().model() == PlayerSkin.Model.SLIM;
		VertexConsumer vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(GLOVE_TEX));
		glove(vc, pose, model.rightArm, true, slim, light);
		glove(vc, pose, model.leftArm, false, slim, light);
		return false;
	}

	/** One glove: a slightly inflated box over the lower arm (pixels 6.5..10.3 below the shoulder pivot) with a cuff band. */
	private static void glove(VertexConsumer vc, PoseStack pose, ModelPart arm, boolean right, boolean slim, int light) {
		pose.pushPose();
		arm.translateAndRotate(pose);
		float w = slim ? 3f : 4f;
		float x0 = right ? -(w - 1f) : -1f, x1 = x0 + w;
		float g = 0.32f;
		box(vc, pose.last(), (x0 - g) / 16f, 7.0f / 16f, (-2f - g) / 16f, (x1 + g) / 16f, (10f + g) / 16f, (2f + g) / 16f, GLOVE, light);
		box(vc, pose.last(), (x0 - g - 0.15f) / 16f, 6.4f / 16f, (-2f - g - 0.15f) / 16f, (x1 + g + 0.15f) / 16f, 7.2f / 16f, (2f + g + 0.15f) / 16f,
				CUFF, light);
		pose.popPose();
	}

	private static void box(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int light) {
		// y grows DOWN the arm in model space
		float[][][] faces = {
				{ { x0, y1, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x0, y1, z1 }, { 0, 1, 0 } },
				{ { x0, y0, z1 }, { x1, y0, z1 }, { x1, y0, z0 }, { x0, y0, z0 }, { 0, -1, 0 } },
				{ { x0, y0, z0 }, { x1, y0, z0 }, { x1, y1, z0 }, { x0, y1, z0 }, { 0, 0, -1 } },
				{ { x1, y0, z1 }, { x0, y0, z1 }, { x0, y1, z1 }, { x1, y1, z1 }, { 0, 0, 1 } },
				{ { x0, y0, z1 }, { x0, y0, z0 }, { x0, y1, z0 }, { x0, y1, z1 }, { -1, 0, 0 } },
				{ { x1, y0, z0 }, { x1, y0, z1 }, { x1, y1, z1 }, { x1, y1, z0 }, { 1, 0, 0 } } };
		for (float[][] f : faces) {
			float[] n = f[4];
			int shade = n[1] != 0 ? rgb : QuirkFxDraw.lerpRgb(0.15f, rgb, 0x000000);
			for (int i = 0; i < 4; i++) {
				QuirkFxDraw.mv(vc, p, f[i][0], f[i][1], f[i][2], i == 1 || i == 2 ? 1 : 0, i >= 2 ? 1 : 0, shade, 1f, light, n[0], n[1], n[2]);
			}
		}
	}
}
