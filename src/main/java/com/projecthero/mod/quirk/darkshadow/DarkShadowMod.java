package com.projecthero.mod.quirk.darkshadow;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.QuirkKits;

import net.minecraft.server.level.ServerPlayer;

/** v0.16.7: the DarkShadow Quirk's server init (STUB -- the kit agent replaces this with the real kit). */
public final class DarkShadowMod {
	private DarkShadowMod() {
	}

	public static void initialize() {
		QuirkKits.register(Quirk.DARK_SHADOW, new QuirkKit() {
			@Override
			public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
			}

			@Override
			public String[] idsOf(AbilitySlot slot) {
				return new String[0];
			}

			@Override
			public int maxCooldown(String abilityId) {
				return 0;
			}
		});
	}
}
