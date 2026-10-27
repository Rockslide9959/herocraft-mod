package com.projecthero.mod.client.quirk;

import java.util.EnumMap;
import java.util.Map;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: the Quirk client side -- the core (HUD, costume layer: added by the core agent) and the dispatch to the
 * seven kits' {@link QuirkClientKit}s, each registered from its own client init.
 */
public final class QuirkClient {
	private static final Map<Quirk, QuirkClientKit> KITS = new EnumMap<>(Quirk.class);

	private QuirkClient() {
	}

	public static void initialize() {
		// the core: the worn-suit layer (registered BEFORE the kits, so a kit's own layers -- Dark Shadow, flames -- draw
		// over the suit; v0.16.7: on every humanoid, so armour stands / mannequins show a suit too), the HUD (meter, the
		// six keys, ERASED) and the Erasure sync
		QuirkCostume.initialize();
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(new QuirkCostumeLayer<>(entityRenderer));
			}
		});
		HudRenderCallback.EVENT.register(QuirkHud::render);
		PowerSuppressionClient.initialize();
		initializeFramework();

		com.projecthero.mod.client.quirk.hellflame.HellflameClient.initialize();
		com.projecthero.mod.client.quirk.overhaul.OverhaulClient.initialize();
		com.projecthero.mod.client.quirk.engine.EngineClient.initialize();
		com.projecthero.mod.client.quirk.darkshadow.DarkShadowClient.initialize();
		com.projecthero.mod.client.quirk.erasure.ErasureClient.initialize();
		com.projecthero.mod.client.quirk.explosion.ExplosionClient.initialize();
		com.projecthero.mod.client.quirk.zerogravity.ZeroGravityClient.initialize();
		// v0.16.7: One For All's HUD block (its poses, injury layer and Pass On screen are AllMightClient's)
		register(Quirk.ONE_FOR_ALL, new com.projecthero.mod.client.allmight.OneForAllClientKit());
	}

	/**
	 * v0.17.1 framework (client): the effect renderer and particles, the camera juice and HUD cues, the first-person arm
	 * clips / full-body view and the tells layer.
	 */
	private static void initializeFramework() {
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(com.projecthero.mod.quirk.fx.QuirkFx.TYPE,
				com.projecthero.mod.client.quirk.fx.QuirkFxRenderer::new);
		com.projecthero.mod.client.quirk.fx.QuirkParticleClient.register();
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.quirk.fx.QuirkCameraPayload.TYPE,
				(payload, context) -> context.client().execute(() -> QuirkCameraFx.accept(payload)));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(com.projecthero.mod.quirk.fx.QuirkCuePayload.TYPE,
				(payload, context) -> context.client().execute(() -> QuirkCuePopup.accept(payload)));
		HudRenderCallback.EVENT.register(QuirkCuePopup::render);
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> QuirkCameraFx.tick());
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(QuirkCameraFx::reset));
		QuirkFirstPersonPose.initialize();
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
				helper.register(new QuirkTellLayer(pr));
			}
		});
	}

	/** The framework's Plus Ultra hold stance: braced low, fists clenched at the hips, head down, trembling as it fills. */
	private static final float[] HOLD_STANCE = QuirkKeyframes.f(0, 0.45f, 0.1f, 0.35f, 0.45f, -0.1f, -0.35f, 0.28f, 0f, -0.35f, 0.1f, 0.12f, 0.3f,
			-0.1f, -0.12f, 0.25f);

	/** Applies the Plus Ultra hold pose (or the kit's own) while C is held. */
	public static void ultimateHoldPose(Player player, HumanoidModel<?> model, QuirkClientKit kit) {
		float hold = com.projecthero.mod.quirk.PlusUltra.clientHold(player);
		if (hold < 0f || (kit != null && kit.ultimateHoldPose(player, model, hold))) {
			return;
		}
		float shake = net.minecraft.util.Mth.sin(player.tickCount * 2.7f) * 0.03f * (0.4f + hold);
		float[] p = HOLD_STANCE.clone();
		p[QuirkKeyframes.R_ARM_X] += shake;
		p[QuirkKeyframes.L_ARM_X] -= shake;
		p[QuirkKeyframes.HEAD_X] += shake;
		QuirkKeyframes.apply(model, p, Math.min(1f, 0.35f + hold));
	}

	public static void register(Quirk quirk, QuirkClientKit kit) {
		KITS.put(quirk, kit);
	}

	/** The client kit of a Quirk (null: none registered). */
	public static QuirkClientKit kitFor(Quirk quirk) {
		return quirk == null ? null : KITS.get(quirk);
	}

	private static QuirkClientKit kitOf(Player player) {
		Quirk q = Quirks.quirk(player);
		return q == null ? null : KITS.get(q);
	}

	/** From HumanoidModelMixin (setupAnim TAIL). */
	public static void applyPose(Player player, HumanoidModel<?> model) {
		QuirkClientKit kit = kitOf(player);
		if (kit != null) {
			kit.applyPose(player, model);
		}
		Quirk q = Quirks.quirk(player);
		if (q != null && !q.standalone) {
			ultimateHoldPose(player, model, kit); // v0.17.1 framework
		}
	}

	/**
	 * From PlayerModelMixin: the kit says its effect covers the whole body. (v0.16.7: the worn suit pieces hide only the
	 * parts they cover, through {@link QuirkCostume#hideCoveredOverlay}.)
	 */
	public static boolean hidesSkinOverlay(Player player) {
		QuirkClientKit kit = kitOf(player);
		return kit != null && kit.hidesSkinOverlay(player);
	}
}
