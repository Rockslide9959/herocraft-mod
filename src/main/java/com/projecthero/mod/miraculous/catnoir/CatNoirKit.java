package com.projecthero.mod.miraculous.catnoir;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.miraculous.KwamiKit;

import net.minecraft.server.level.ServerPlayer;

/** v0.16.6: CatNoir six keys (STUB: the kit build fills this in). */
public final class CatNoirKit implements KwamiKit {
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
}
