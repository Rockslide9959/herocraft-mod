package com.projecthero.mod.quirk.explosion;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.QuirkKits;

import net.minecraft.server.level.ServerPlayer;

/** v0.16.7: the Explosion Quirk's server init (STUB -- the kit agent replaces this with the real kit). */
public final class ExplosionMod {
	private ExplosionMod() {
	}

	public static void initialize() {
		QuirkKits.register(Quirk.EXPLOSION, new QuirkKit() {
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
