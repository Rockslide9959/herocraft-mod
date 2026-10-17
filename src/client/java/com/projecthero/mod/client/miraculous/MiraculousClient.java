package com.projecthero.mod.client.miraculous;

import java.util.EnumMap;
import java.util.Map;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.network.MiraculousActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: the Miraculous client side -- the core (H, the HUD, the Kwami entity, the transformation sequence) and the
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
	}

	public static void register(Kwami kwami, KwamiClientKit kit) {
		KITS.put(kwami, kit);
	}

	private static KwamiClientKit kitOf(Player player) {
		Kwami k = Miraculous.kwami(player);
		return k == null ? null : KITS.get(k);
	}

	/** Whether plain H belongs to a Miraculous holder (transform / detransform). */
	public static boolean ownsH(LocalPlayer player) {
		return Miraculous.hasPower(player);
	}

	public static void pressH() {
		ClientPlayNetworking.send(new MiraculousActionPayload(MiraculousActionPayload.Action.TOGGLE_FORM));
	}

	/** From HumanoidModelMixin (setupAnim TAIL). */
	public static void applyPose(Player player, HumanoidModel<?> model) {
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
