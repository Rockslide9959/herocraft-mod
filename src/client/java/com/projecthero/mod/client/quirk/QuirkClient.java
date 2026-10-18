package com.projecthero.mod.client.quirk;

import java.util.EnumMap;
import java.util.Map;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;

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
		com.projecthero.mod.client.quirk.hellflame.HellflameClient.initialize();
		com.projecthero.mod.client.quirk.overhaul.OverhaulClient.initialize();
		com.projecthero.mod.client.quirk.engine.EngineClient.initialize();
		com.projecthero.mod.client.quirk.darkshadow.DarkShadowClient.initialize();
		com.projecthero.mod.client.quirk.erasure.ErasureClient.initialize();
		com.projecthero.mod.client.quirk.explosion.ExplosionClient.initialize();
		com.projecthero.mod.client.quirk.zerogravity.ZeroGravityClient.initialize();
	}

	public static void register(Quirk quirk, QuirkClientKit kit) {
		KITS.put(quirk, kit);
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
	}

	/** From PlayerModelMixin. */
	public static boolean hidesSkinOverlay(Player player) {
		QuirkClientKit kit = kitOf(player);
		return kit != null && kit.hidesSkinOverlay(player);
	}
}
