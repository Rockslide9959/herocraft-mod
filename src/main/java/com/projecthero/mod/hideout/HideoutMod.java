package com.projecthero.mod.hideout;

import com.projecthero.mod.hideout.block.HideoutBlocks;
import com.projecthero.mod.hideout.item.HideoutItems;
import com.projecthero.mod.hideout.worldgen.HideoutWorldgen;

/**
 * v0.17.5: <b>Tier 3 Villain Hideouts</b> -- Kingpin's Front, the Ultron Forge, the Apokolips Outpost, the Symbiote Hive
 * and the Frost Giant Hall. Rare worldgen structures, each guarded by its faction's mobs, trapped, and ruled by a
 * mini-boss; mini-boss dead + core broken = cleared: the sealed Hideout Vault opens and that faction's raids calm within
 * 512 blocks for five days. See {@code docs/VILLAIN_HIDEOUTS_REFERENCE.md}.
 *
 * <p>No static per-world state: the cleared hideouts live in {@link HideoutState} (world storage).
 */
public final class HideoutMod {
	private HideoutMod() {
	}

	public static void initialize() {
		HideoutBlocks.initialize();
		HideoutItems.initialize();
		HideoutEntityTypes.initialize();
		HideoutWorldgen.initialize();
		NearHideoutCondition.initialize();
		HideoutAlarmProfiles.register(); // v0.17.7: each hideout is an alarm zone
	}
}
