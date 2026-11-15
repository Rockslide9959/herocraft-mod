package com.projecthero.mod.syndicate.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.resources.ResourceLocation;

/**
 * v0.14.25: the Syndicate's faces -- player skins from The Skindex (minecraftskins.com), credited in
 * {@code docs/SYNDICATE_REFERENCE.md}. {@code slim} skins have 3-pixel arms (the renderer swaps to the slim player model).
 */
public enum SyndicateSkin {
	/** "Malak gang thug 1" by Bakugo417: a bald bruiser in a flannel. */
	THUG_A("thug_a", false),
	/** "Malak gang thug 5" by Bakugo417: a bald heavy in a blue track jacket. */
	THUG_C("thug_c", false),
	/** "thug" by KINGJS189: beanie, black jacket. The pistol gunman. */
	GUNMAN("gunman", true),
	/** "thug" by hell1234568789: balaclava and a striped jumper. The shotgunner. */
	MASKED("masked", false),
	/** "thug" by Danroc: a dark hood. The sniper. */
	HOOD("hood", false),
	/** "Wilson Fisk - MCU" by wolflywood01: the white suit. */
	KINGPIN("kingpin", false),
	/**
	 * v0.17.5: Kingpin's Enforcer, the Kingpin's Front mini-boss -- painted procedurally from {@code thug_a} (black
	 * three-piece suit, gold chain, dark glasses) by {@code scratchpad/gen_hideouts_v0175.js}.
	 */
	HIDEOUT_ENFORCER("hideout_enforcer", false),
	/** v0.17.5 The Vault: an escaped inmate in an orange jumpsuit (recolour of {@code thug_a}, {@code scratchpad/gen_vault_v0175.js}). */
	VAULT_INMATE_A("vault/inmate_a", false),
	/** v0.17.5 The Vault: a second inmate (recolour of {@code thug_c}, shaved head, orange jumpsuit). */
	VAULT_INMATE_B("vault/inmate_b", false),
	/** v0.17.5 The Vault: a riot warden -- black tactical armour, riot helmet and visor (painted over {@code thug_c}). */
	VAULT_RIOT_WARDEN("vault/riot_warden", false),
	/** v0.17.5 The Vault: a riot gunner -- the warden kit with a grey ballistic vest. */
	VAULT_RIOT_GUNNER("vault/riot_gunner", false),
	/** v0.17.5 The Vault: Warden Kross -- charcoal greatcoat, red trim, peaked officer cap, scarred face (over {@code thug_a}). */
	VAULT_WARDEN_KROSS("vault/warden_kross", false);

	private final ResourceLocation texture;
	private final boolean slim;

	SyndicateSkin(String name, boolean slim) {
		this.texture = ProjectHeroMod.id("textures/entity/syndicate/" + name + ".png");
		this.slim = slim;
	}

	public ResourceLocation texture() {
		return texture;
	}

	public boolean slim() {
		return slim;
	}
}
