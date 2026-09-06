package com.projecthero.mod.symbiote;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The Symbiote's own "life" -- a second, small attachment ({@code projecthero:symbiote_vitals})
 * separate from {@link SymbioteState} purely because that record is already at the 16-field
 * {@code RecordCodecBuilder.group} ceiling.
 *
 * <ul>
 *   <li>{@link #hp} -- the Symbiote health bar (v0.9.23), 0..{@link SymbioteVitalsManager#MAX_HP}. Half
 *       of every hit the bonded player takes is dealt to this instead of to the player
 *       ({@link SymbioteDamageRules}); it regenerates on its own ({@link SymbioteVitalsManager}).</li>
 *   <li>{@link #broken} -- true once {@link #hp} has hit zero. While it is set the Symbiote's abilities
 *       are unusable; it clears again once {@link #hp} climbs back to
 *       {@link SymbioteVitalsManager#RECOVER_FRACTION} of the maximum.</li>
 *   <li>{@link #bladeActive} / {@link #bladeCharge} -- Symbiote Blade (V): the arm turns to living
 *       black, +5 melee and much faster swings, at the cost of a bar that drains while it is out
 *       ({@link SymbioteVitalsManager#BLADE_MAX} ticks ~= 30 s) and refills while it is sheathed.</li>
 *   <li>{@link #thornsMode} -- Symbiote Spikes (C): a toggle that reflects melee damage like Thorns IV.</li>
 * </ul>
 *
 * <p>Persistent (the bar should not silently refill to full across a relog) and synced to everyone so
 * the HUD, the black-arm render and another player's view all read the real values. Death / relog /
 * dimension change resets it to a healthy default via {@link SymbioteVitalsManager#clearTransient}.
 */
public final class SymbioteVitals {
	public float hp;
	public boolean broken;
	public boolean bladeActive;
	public float bladeCharge;
	public boolean thornsMode;

	public SymbioteVitals() {
		this(SymbioteVitalsManager.MAX_HP, false, false, SymbioteVitalsManager.BLADE_MAX, false);
	}

	public SymbioteVitals(float hp, boolean broken, boolean bladeActive, float bladeCharge, boolean thornsMode) {
		this.hp = hp;
		this.broken = broken;
		this.bladeActive = bladeActive;
		this.bladeCharge = bladeCharge;
		this.thornsMode = thornsMode;
	}

	public SymbioteVitals copy() {
		return new SymbioteVitals(hp, broken, bladeActive, bladeCharge, thornsMode);
	}

	public static final Codec<SymbioteVitals> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.FLOAT.optionalFieldOf("hp", SymbioteVitalsManager.MAX_HP).forGetter(s -> s.hp),
			Codec.BOOL.optionalFieldOf("broken", false).forGetter(s -> s.broken),
			Codec.BOOL.optionalFieldOf("blade_active", false).forGetter(s -> s.bladeActive),
			Codec.FLOAT.optionalFieldOf("blade_charge", SymbioteVitalsManager.BLADE_MAX).forGetter(s -> s.bladeCharge),
			Codec.BOOL.optionalFieldOf("thorns_mode", false).forGetter(s -> s.thornsMode)
	).apply(instance, SymbioteVitals::new));
}
