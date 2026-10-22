package com.projecthero.mod.quirk.darkshadow;

import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;

import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;

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
		QuirkKits.register(Quirk.DARK_SHADOW, new DarkShadowKit());
		EntityElytraEvents.CUSTOM.register((entity, tickElytra) ->
				entity instanceof Player pl && DarkShadow.abyssal(pl) && DarkShadow.is(pl) && !pl.getAbilities().flying);
	}
}
