package com.projecthero.mod.client.falcon;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.falcon.Falcon;
import com.projecthero.mod.falcon.item.FalconItems;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: the Falcon's suit -- the user's chosen Planet Minecraft skin (the classic red-and-white Falcon) with the whole
 * head cut out except the Falcon Goggles ({@code scratchpad/suits/make_falcon_assets.js}), in a wide-arm and a slim-arm cut
 * so the suit's arms always line up with the wearer's. Drawn as an overlay a hair over the wearer's own skin (the Ladybug /
 * Nova technique), so their face and hair show through; while it shows, the wearer's jacket / sleeve / trouser layers and
 * the worn armour are hidden. Shown whenever the full set is worn (the gear is the power).
 *
 * <p>Also keeps each Falcon's wing spread (0 folded .. 1 spread), eased a little every frame.
 */
public final class FalconRender {
	public static final ResourceLocation SKIN_WIDE = ProjectHeroMod.id("textures/entity/falcon/suit_wide.png");
	public static final ResourceLocation SKIN_SLIM = ProjectHeroMod.id("textures/entity/falcon/suit_slim.png");

	private static PlayerModel<LivingEntity> wide;
	private static PlayerModel<LivingEntity> slim;
	/** entity id -> {spread, wrap, last update nanos}. */
	private static final Map<Integer, float[]> WINGS = new ConcurrentHashMap<>();

	private FalconRender() {
	}

	public static void clearSessionState() {
		WINGS.clear();
	}

	/** The suit shows: the full set is on. Client-safe. */
	public static boolean suited(Player player) {
		return FalconItems.WING_PACK != null && FalconItems.fullSet(player);
	}

	public static boolean hidesSkinOverlay(Player player) {
		return suited(player);
	}

	public static PlayerModel<LivingEntity> model(boolean slimArms) {
		if (slimArms) {
			if (slim == null) {
				slim = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.04f), true), 64, 64).bakeRoot(), true);
			}
			return slim;
		}
		if (wide == null) {
			wide = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.04f), false), 64, 64).bakeRoot(), false);
		}
		return wide;
	}

	static boolean slim(Player p) {
		return p instanceof net.minecraft.client.player.AbstractClientPlayer cp && cp.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
	}

	static ResourceLocation skin(boolean slimArms) {
		return slimArms ? SKIN_SLIM : SKIN_WIDE;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static PlayerModel<LivingEntity> posed(HumanoidModel<?> parent, boolean slimArms) {
		PlayerModel<LivingEntity> m = model(slimArms);
		((HumanoidModel) parent).copyPropertiesTo(m);
		m.jacket.copyFrom(m.body);
		m.leftSleeve.copyFrom(m.leftArm);
		m.rightSleeve.copyFrom(m.rightArm);
		m.leftPants.copyFrom(m.leftLeg);
		m.rightPants.copyFrom(m.rightLeg);
		m.hat.copyFrom(m.head);
		m.setAllVisible(true);
		return m;
	}

	public static void render(PoseStack pose, MultiBufferSource buffers, int light, Player player, HumanoidModel<?> parent) {
		boolean sl = slim(player);
		PlayerModel<LivingEntity> m = posed(parent, sl);
		m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(skin(sl))), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
	}

	/** First person: the suit's sleeve and glove over the arm vanilla just drew. */
	public static void renderArm(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart arm, boolean right) {
		boolean sl = slim(player);
		PlayerModel<LivingEntity> m = model(sl);
		ModelPart a = right ? m.rightArm : m.leftArm;
		ModelPart sleeve = right ? m.rightSleeve : m.leftSleeve;
		a.copyFrom(arm);
		sleeve.copyFrom(arm);
		a.visible = true;
		sleeve.visible = true;
		a.render(pose, buffers.getBuffer(RenderType.entityTranslucent(skin(sl))), light, OverlayTexture.NO_OVERLAY);
		sleeve.render(pose, buffers.getBuffer(RenderType.entityTranslucent(skin(sl))), light, OverlayTexture.NO_OVERLAY);
		// the wrist launcher on it, flashing as it fires
		pose.pushPose();
		arm.translateAndRotate(pose);
		float partial = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		FalconGeometry.wristLauncher(pose, buffers, light, right, sl, FalconClient.flash(player, right, partial), FalconClient.flashSeed(player));
		pose.popPose();
	}

	// ---------------------------------------------------------------- the wings

	/** {spread, wrap}: eased toward spread in flight (1) / folded (0), and wrapped in front of him while the Wing Shield is up. */
	public static float[] wings(Player player) {
		boolean spread = Falcon.flying(player) && !player.onGround();
		boolean wrap = Falcon.shieldUp(player);
		float[] w = WINGS.computeIfAbsent(player.getId(), k -> new float[] { spread ? 1f : 0f, 0f, 0f });
		long nowNanos = System.nanoTime();
		float dt = w[2] == 0f ? 0f : Math.min(0.1f, (float) ((nowNanos / 1.0e6 % 1.0e7) - w[2]) / 1000f);
		if (dt < 0f) {
			dt = 0.016f;
		}
		w[2] = (float) (nowNanos / 1.0e6 % 1.0e7);
		float targetSpread = spread || wrap ? 1f : 0f;
		// v0.17.1 second pass: a steady unfold / fold over WING_UNFOLD_TICKS (the geometry staggers it joint by joint)
		float step = dt * 20f / com.projecthero.mod.falcon.FalconConfig.WING_UNFOLD_TICKS;
		w[0] = targetSpread > w[0] ? Math.min(targetSpread, w[0] + step) : Math.max(targetSpread, w[0] - step);
		w[1] = Mth.clamp(w[1] + ((wrap ? 1f : 0f) - w[1]) * Math.min(1f, dt * 12f), 0f, 1f);
		return new float[] { w[0], w[1] };
	}
}
