package com.projecthero.mod.power;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.world.entity.player.Player;

/**
 * v0.17.7 Cosmic revamp: Thor's four weapon states, picked from what is in his hands (either hand, either way round). The
 * same key does a different move in each ({@link ThorKit}); each state has its own perk and drawback:
 * <ul>
 *   <li>{@link #UNARMED} -- Warrior of Asgard: fists 24 at the fastest swing, every cooldown 25% shorter; no flight.</li>
 *   <li>{@link #MJOLNIR} -- Storm Caller: the fastest flight, lightning stuns briefly, Storm Call lasts longer.</li>
 *   <li>{@link #STORMBREAKER} -- Executioner: 26 slow swings, slow flight, ability hits ignore armour and finish weakened
 *   enemies, Bifrost travel on Shift+X.</li>
 *   <li>{@link #TWIN} -- Twin Gods: the highest damage, medium flight, but every move costs double Storm Energy and there is
 *   no Bifrost travel.</li>
 * </ul>
 * Pure functions of the player's hands, so the client (HUD, poses, effect palette) gets the same answer as the server.
 */
public enum ThorState {
	UNARMED("unarmed", 0.75f, 1.0f, 0.0f, 24.0),
	MJOLNIR("mjolnir", 1.0f, 1.0f, 0.080f, 24.0),
	STORMBREAKER("stormbreaker", 1.0f, 1.0f, 0.052f, 26.0),
	TWIN("twin", 1.0f, 2.0f, 0.066f, 27.0);

	/** Lang suffix: {@code hud.projecthero.thor.state.<id>}, {@code projecthero.guide.thor.state.<id>}. */
	public final String id;
	/** Every cooldown is multiplied by this (Unarmed: 25% shorter). */
	public final float cooldownMul;
	/** Every Storm Energy cost is multiplied by this (Twin Gods: double). */
	public final float energyMul;
	/** Vanilla flying speed while flying in this state (0 = no flight). Was one shared 0.069. */
	public final float flyingSpeed;
	/** The melee hit this state lands at full strength (Cosmic melee 21-28), set through {@link ThorKit#reconcileMelee}. */
	public final double melee;

	ThorState(String id, float cooldownMul, float energyMul, float flyingSpeed, double melee) {
		this.id = id;
		this.cooldownMul = cooldownMul;
		this.energyMul = energyMul;
		this.flyingSpeed = flyingSpeed;
		this.melee = melee;
	}

	/** The state {@code player}'s hands put him in (hands only; whether he has Thor's kit at all is {@link #hasKit}). */
	public static ThorState of(Player player) {
		ThorThrown out = ThorThrown.of(player);
		if (!out.isEmpty()) {
			// v0.17.7: a weapon out on a scripted throw still counts as in his hand (the move, its perks and flight carry on)
			boolean mj = ThorThrown.holds(player, com.projecthero.mod.hammer.ThorWeapon.MJOLNIR);
			boolean sb = ThorThrown.holds(player, com.projecthero.mod.hammer.ThorWeapon.STORMBREAKER);
			return mj && sb ? TWIN : mj ? MJOLNIR : sb ? STORMBREAKER : UNARMED;
		}
		if (WeaponCombo.isDualWielding(player)) {
			return TWIN;
		}
		if (ThorPowers.isHoldingMjolnir(player)) {
			return MJOLNIR;
		}
		if (ThorPowers.isHoldingStormbreaker(player)) {
			return STORMBREAKER;
		}
		return UNARMED;
	}

	/**
	 * Whether {@code player} has Thor's kit right now: worthy, and holding Mjolnir / Stormbreaker, or bound to Mjolnir (the
	 * Power of Thor) or to a Stormbreaker -- the binding API the acquisition code plugs into is unchanged.
	 */
	public static boolean hasKit(Player player) {
		return Worthiness.isWorthy(player) && (ThorPowers.isHoldingThorWeapon(player) || ThorThrown.any(player)
				|| ThorPassives.hasPowerOfThor(player)
				|| player.getAttachedOrElse(ModAttachments.BOUND_STORMBREAKER_ID, null) != null);
	}

	public int cooldown(int baseTicks) {
		return Math.max(1, Math.round(baseTicks * cooldownMul));
	}

	public float cost(float base) {
		return base * energyMul;
	}

	public boolean flies() {
		return flyingSpeed > 0.0f;
	}
}
