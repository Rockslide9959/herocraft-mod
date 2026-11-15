package com.projecthero.mod.vaultdungeon;

import com.projecthero.mod.vaultdungeon.block.VaultBlocks;
import com.projecthero.mod.vaultdungeon.item.VaultItems;
import com.projecthero.mod.vaultdungeon.worldgen.VaultWorldgen;

/**
 * v0.17.5: <b>The Vault</b>, the Tier 4 dungeon -- an underground super-prison. A fortified guard post on the surface,
 * a ladder shaft down to three floors: the Cell Block (escaped inmates), the Security Floor (laser tripwires, ceiling
 * turrets, a lockdown gate opened from the security office) and the Warden's Level (the arena of <b>Warden Kross</b>
 * and the sealed Dungeon Vaults behind it). Kross dead = the dungeon is cleared for good ({@link VaultDungeonState})
 * and the sealed vaults open into Tier 4 Hero Vaults (per-player loot). See {@code docs/VAULT_DUNGEON_REFERENCE.md}.
 *
 * <p>No static per-world state: the cleared dungeons live in {@link VaultDungeonState} (world storage).
 */
public final class VaultDungeonMod {
	private VaultDungeonMod() {
	}

	public static void initialize() {
		VaultBlocks.initialize();
		VaultItems.initialize();
		VaultEntityTypes.initialize();
		VaultWorldgen.initialize();
		NearVaultCondition.initialize();
		VaultItems.registerEvents();
	}
}
