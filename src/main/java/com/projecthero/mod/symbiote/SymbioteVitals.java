package com.projecthero.mod.symbiote;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The Symbiote's own "life" -- a second, small attachment ({@code projecthero:symbiote_vitals})
 * separate from {@link SymbioteState} purely because that record is already at the 16-field
 * {@code RecordCodecBuilder.group} ceiling.
 *
 * <ul>
 *   <li>{@link #hp} -- Biomass, 0..{@link SymbioteVitalsManager#MAX_HP}. The host takes every hit in full and
 *       the Symbiote loses a share of it from here; it regenerates on its own ({@link SymbioteVitalsManager}).</li>
 *   <li>{@link #broken} -- true once {@link #hp} has hit zero. While it is set the Symbiote's abilities
 *       are unusable; it clears again once {@link #hp} climbs back to
 *       {@link SymbioteVitalsManager#RECOVER_FRACTION} of the maximum.</li>
 *   <li>{@link #bladeActive} -- Symbiote Blade (V). v0.13.19: no time limit any more, it drains Biomass
 *       instead; {@link #bladeCharge} is kept only so old saves still decode. v0.17.5 weapons: set while ANY weapon Form is out
 *       (the hand-empty rule and the Biomass drain cover them all); {@link #weapon} says which.</li>
 *   <li>{@link #thornsMode} -- Symbiote Spikes (C): a toggle that reflects melee damage like Thorns IV.</li>
 *   <li>{@link #resurrectReadyAt} -- v0.13.19: game time the Symbiote can next pull its host back from death
 *       (a flat 10-minute cooldown, no Biomass cost). Persistent, so a relog or a restart cannot reset it.</li>
 *   <li>{@link #spikeConeReadyAt} -- v0.13.19: game time the Shift+G spike fan is ready again.</li>
 *   <li>{@link #animId} / {@link #animStart} -- v0.13.19: the move the host is animating and when it started
 *       ({@code SymbioteAnim}), synced so every viewer poses the host the same way.</li>
 *   <li>{@link #weapon} -- v0.17.5 weapons: the {@link SymbioteWeapon} ordinal (Blade, Axe, Mace, Whip) the arm grows while
 *       {@link #bladeActive} is set. {@link #formSince} / {@link #prevForm}: when the Form last changed and what it was
 *       ({@code SymbioteVitalsManager.FORM_*}), so every viewer plays the new weapon growing and the old one melting back.</li>
 *   <li>{@link #wingsStart} / {@link #wingsLanded} -- v0.17.5 Symbiote Wings: game time the wings grew (0 = none) and the game time
 *       the host touched down (0 = still in the air); {@link SymbioteWings} derives grow / fly / glide / dissolve from them.</li>
 * </ul>
 *
 * <p>Persistent (the bar must not silently refill across a relog or a death) and synced to everyone so
 * the HUD, the blade model and another player's view all read the real values.
 */
public final class SymbioteVitals {
	public float hp;
	public boolean broken;
	public boolean bladeActive;
	public float bladeCharge;
	public boolean thornsMode;
	/**
	 * Game time the initial bonding phase ends. While {@code > level.getGameTime()} the fresh host
	 * has the Symbiote's protection but no abilities, feels sick, and the organism talks them through
	 * it. 0 once the bond has settled (the normal state).
	 */
	public long bondingUntil;
	public long resurrectReadyAt;
	public long spikeConeReadyAt;
	public int animId;
	public long animStart;
	public int weapon;
	public long formSince;
	public int prevForm;
	public long wingsStart;
	public long wingsLanded;

	public SymbioteVitals() {
		this(SymbioteVitalsManager.MAX_HP, false, false, SymbioteVitalsManager.BLADE_MAX, false, 0L, 0L, 0L, 0, 0L);
	}

	public SymbioteVitals(float hp, boolean broken, boolean bladeActive, float bladeCharge, boolean thornsMode,
			long bondingUntil, long resurrectReadyAt, long spikeConeReadyAt, int animId, long animStart) {
		this(hp, broken, bladeActive, bladeCharge, thornsMode, bondingUntil, resurrectReadyAt, spikeConeReadyAt, animId, animStart,
				0, 0L, 0, 0L, 0L);
	}

	public SymbioteVitals(float hp, boolean broken, boolean bladeActive, float bladeCharge, boolean thornsMode,
			long bondingUntil, long resurrectReadyAt, long spikeConeReadyAt, int animId, long animStart,
			int weapon, long formSince, int prevForm, long wingsStart, long wingsLanded) {
		this.hp = hp;
		this.broken = broken;
		this.bladeActive = bladeActive;
		this.bladeCharge = bladeCharge;
		this.thornsMode = thornsMode;
		this.bondingUntil = bondingUntil;
		this.resurrectReadyAt = resurrectReadyAt;
		this.spikeConeReadyAt = spikeConeReadyAt;
		this.animId = animId;
		this.animStart = animStart;
		this.weapon = weapon;
		this.formSince = formSince;
		this.prevForm = prevForm;
		this.wingsStart = wingsStart;
		this.wingsLanded = wingsLanded;
	}

	public SymbioteVitals copy() {
		return new SymbioteVitals(hp, broken, bladeActive, bladeCharge, thornsMode, bondingUntil, resurrectReadyAt,
				spikeConeReadyAt, animId, animStart, weapon, formSince, prevForm, wingsStart, wingsLanded);
	}

	public static final Codec<SymbioteVitals> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.FLOAT.optionalFieldOf("hp", SymbioteVitalsManager.MAX_HP).forGetter(s -> s.hp),
			Codec.BOOL.optionalFieldOf("broken", false).forGetter(s -> s.broken),
			Codec.BOOL.optionalFieldOf("blade_active", false).forGetter(s -> s.bladeActive),
			Codec.FLOAT.optionalFieldOf("blade_charge", SymbioteVitalsManager.BLADE_MAX).forGetter(s -> s.bladeCharge),
			Codec.BOOL.optionalFieldOf("thorns_mode", false).forGetter(s -> s.thornsMode),
			Codec.LONG.optionalFieldOf("bonding_until", 0L).forGetter(s -> s.bondingUntil),
			Codec.LONG.optionalFieldOf("resurrect_ready_at", 0L).forGetter(s -> s.resurrectReadyAt),
			Codec.LONG.optionalFieldOf("spike_cone_ready_at", 0L).forGetter(s -> s.spikeConeReadyAt),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.INT.optionalFieldOf("weapon", 0).forGetter(s -> s.weapon),
			Codec.LONG.optionalFieldOf("form_since", 0L).forGetter(s -> s.formSince),
			Codec.INT.optionalFieldOf("prev_form", 0).forGetter(s -> s.prevForm),
			Codec.LONG.optionalFieldOf("wings_start", 0L).forGetter(s -> s.wingsStart),
			Codec.LONG.optionalFieldOf("wings_landed", 0L).forGetter(s -> s.wingsLanded)
	).apply(instance, SymbioteVitals::new));
}
