package com.projecthero.mod.client.miraculous;

import java.util.EnumMap;
import java.util.Map;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.network.MiraculousActionPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: the Miraculous client side -- the core (the HUD, the Kwami's renderer, the Miraculous Box's item renderer,
 * the worn jewels, the transformation's light show and pose) and the
 * dispatch to the three kits' {@link KwamiClientKit}s (poses, suit layers), each registered from its own client init.
 */
public final class MiraculousClient {
	private static final Map<Kwami, KwamiClientKit> KITS = new EnumMap<>(Kwami.class);

	private MiraculousClient() {
	}

	public static void initialize() {
		com.projecthero.mod.client.miraculous.ladybug.LadybugClient.initialize();
		com.projecthero.mod.client.miraculous.catnoir.CatNoirClient.initialize();
		com.projecthero.mod.client.miraculous.renarouge.RenaRougeClient.initialize();
		// the core -- registered after the kits so the jewels draw over their suits
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
			if (entityRenderer instanceof PlayerRenderer playerRenderer) {
				helper.register(new MiraculousJewelLayer(playerRenderer));
			}
		});
		EntityRendererRegistry.register(com.projecthero.mod.miraculous.entity.MiraculousEntityTypes.KWAMI, KwamiRenderer::new);
		HudRenderCallback.EVENT.register(MiraculousHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(MiraculousFx::tick);
		com.projecthero.mod.miraculous.item.MiraculousBoxItem.rendererFactory = consumer -> consumer.accept(new software.bernie.geckolib.animatable.client.GeoRenderProvider() {
			private KwamiRenderer.BoxRenderer renderer;

			@Override
			public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
				if (renderer == null) {
					renderer = new KwamiRenderer.BoxRenderer();
				}
				return renderer;
			}
		});
	}

	public static void register(Kwami kwami, KwamiClientKit kit) {
		KITS.put(kwami, kit);
	}

	private static KwamiClientKit kitOf(Player player) {
		Kwami k = Miraculous.kwami(player);
		return k == null ? null : KITS.get(k);
	}

	/**
	 * v0.16.6 (user): never -- the transformation is SPOKEN in chat ("Tikki, spots on!", see {@code MiraculousPhrases}),
	 * so plain H falls through to whatever else uses it. ({@link #pressH} and the payload stay for commands / tests.)
	 */
	public static boolean ownsH(LocalPlayer player) {
		return false;
	}

	public static void pressH() {
		ClientPlayNetworking.send(new MiraculousActionPayload(MiraculousActionPayload.Action.TOGGLE_FORM));
	}

	/** From HumanoidModelMixin (setupAnim TAIL): the shared transformation pose, then the kit's own poses. */
	public static void applyPose(Player player, HumanoidModel<?> model) {
		com.projecthero.mod.miraculous.data.MiraculousState s = Miraculous.peek(player);
		if (s != null && s.hasPower) {
			MiraculousPose.apply(player, model, s);
		}
		KwamiClientKit kit = kitOf(player);
		if (kit != null) {
			kit.applyPose(player, model);
		}
	}

	/** From PlayerModelMixin / HumanoidArmorLayerMixin. */
	public static boolean hidesSkinOverlay(Player player) {
		KwamiClientKit kit = kitOf(player);
		return kit != null && kit.hidesSkinOverlay(player);
	}
}
