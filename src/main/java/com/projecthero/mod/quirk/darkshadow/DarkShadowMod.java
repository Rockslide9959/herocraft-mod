package com.projecthero.mod.quirk.darkshadow;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;

import com.projecthero.mod.hero.PowerSuppression;

import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: the Dark Shadow Quirk's server init -- loads its config ({@code config/projecthero_quirk_dark_shadow.json}),
 * registers {@link DarkShadowKit}, and makes Abyssal Black Ankh's wings a custom elytra (vanilla elytra flight -- steer
 * with your look, dive for speed -- while the wings are out; client-safe, it reads the synced state).
 */
public final class DarkShadowMod {
	private DarkShadowMod() {
	}

	public static void initialize() {
		DarkShadowConfig.load();
		DarkShadowSounds.initialize();
		QuirkKits.register(Quirk.DARK_SHADOW, new DarkShadowKit());
		DarkShadowMeter.register(); // v0.17.1 framework: Shadow bands + pacing
		// v0.16.7 (user): N hides / calls out Dark Shadow (the client sends it only for a holder with no mutation selected)
		PayloadTypeRegistry.playC2S().register(DarkShadowHidePayload.TYPE, DarkShadowHidePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DarkShadowHidePayload.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
			if (!p.isAlive() || p.isSpectator() || !DarkShadow.is(p)) {
				return;
			}
			// Erased: it can't be hidden or called out (it is already powerless); a hidden one stays hidden
			if (PowerSuppression.refuse(p)) {
				return;
			}
			DarkShadowMoves.toggleHidden(p);
		});
		EntityElytraEvents.CUSTOM.register((entity, tickElytra) ->
				entity instanceof Player pl && (DarkShadow.abyssal(pl) || DarkShadow.blackBody(pl)) && DarkShadow.is(pl) && !pl.getAbilities().flying);
	}
}
