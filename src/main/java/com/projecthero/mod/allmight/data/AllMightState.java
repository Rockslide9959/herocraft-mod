package com.projecthero.mod.allmight.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The whole All Might / One For All power for one player in one attachment ({@code projecthero:all_might_state}):
 * persistent, {@code copyOnDeath} and synced to every client (other players render the form and the poses from it).
 * The server is authoritative; this is only ever mutated through {@code AllMight}.
 *
 * <p>v0.16.6 (user: "Lets try the new output limit"): the 300-point OFA timer is gone -- the old {@code ofa} key in a save is
 * simply ignored by the codec (unknown keys are skipped), so an old save loads at 20% Output with no Strain. The new
 * Output / Strain / loan fields live in their own {@link #EXTRA} map codec (flattened into the same NBT compound) because a
 * {@code RecordCodecBuilder} group tops out at 16 fields.
 */
public final class AllMightState {
	/** Animation ids (see {@code AllMightPose} on the client). */
	public static final int ANIM_NONE = 0;
	public static final int ANIM_TRANSFORM_UP = 1;
	public static final int ANIM_TRANSFORM_DOWN = 2;
	public static final int ANIM_DETROIT = 3;
	public static final int ANIM_TEXAS = 4;
	public static final int ANIM_CAROLINA = 5;
	public static final int ANIM_NEW_HAMPSHIRE = 6;
	/** Plus Ultra's flex (the old Full Cowl pose). */
	public static final int ANIM_COWL = 7;
	public static final int ANIM_UNITED_STATES = 8;
	public static final int ANIM_LEAP = 9;
	// v0.16.6: the new Shift moves
	public static final int ANIM_DELAWARE = 10;
	public static final int ANIM_MANCHESTER = 11;
	public static final int ANIM_OKLAHOMA = 12;
	public static final int ANIM_MISSOURI = 13;
	public static final int ANIM_ST_LOUIS = 14;
	/** v0.16.6: the Pass On "Eat this" offering (a hand held out). */
	public static final int ANIM_PASS_ON = 15;

	/** v0.16.6: Output levels (20% / 45% / 100%). */
	public static final int OUTPUT_20 = 0;
	public static final int OUTPUT_45 = 1;
	public static final int OUTPUT_100 = 2;

	/** v0.16.6: the player's side of a One For All loan. */
	public static final int LOAN_NONE = 0;
	/** Lent their One For All out: has no power for the loan, exhausted, gets it back when it ends. */
	public static final int LOAN_LENDER = 1;
	/** Holds a borrowed, capped One For All until the loan ends. */
	public static final int LOAN_BORROWER = 2;

	public boolean hasPower;
	/** false = Base Form (a plain player), true = Power Form (H). */
	public boolean fullPower;
	/** Plus Ultra (C) is on: Output is forced to 100% and Strain does not build. */
	public boolean plusUltra;
	/** Damage-proof while {@code gameTime < transformUntil}. */
	public long transformUntil;
	/** No new ability may start before this game time (wind-ups, transformation). */
	public long busyUntil;
	public long formChangedAt;
	public long lastCombatTick;
	/** Fall damage is fully cancelled until this game time (his own launches). */
	public long noFallUntil;
	public int animId;
	public long animStart;
	/** v0.12.38: game time the United States of Smash charge began, 0 when not charging (drives the HUD's charge bar). */
	public long chargeStart;
	/** v0.12.39: game time Plus Ultra runs out (it lasts a fixed 22 s); 0 when it is off. */
	public long plusUltraUntil;
	/** {@code abilityId} -> absolute game-time it is ready again. */
	public final Map<String, Long> abilityReadyAt;

	// ---------------------------------------------------------------- v0.16.6: Output / Strain
	/** {@link #OUTPUT_20}, {@link #OUTPUT_45} or {@link #OUTPUT_100}. */
	public int output;
	/** 0..1 -- how far landed hits have charged toward the next Output level. */
	public float outputProgress;
	/** Game time a hit last landed (Output drops one level after {@code AllMightConfig.OUTPUT_DECAY_TICKS} without one). */
	public long lastHitAt;
	/** 0..{@code AllMightConfig.STRAIN_MAX}. At full Strain his arms give out. */
	public float strain;
	/** The "broken arms" recoil: every Smash is locked until this game time. */
	public long recoilUntil;

	// ---------------------------------------------------------------- v0.16.6: lending
	/** {@link #LOAN_NONE} / {@link #LOAN_LENDER} / {@link #LOAN_BORROWER}. */
	public int loanRole;
	/** The other player of the loan (UUID string), "" when none. */
	public String loanPartner;
	/** Their name, for the HUD and messages. */
	public String loanPartnerName;
	/** Game time the loan ends by itself. */
	public long loanUntil;
	/** A lender may not lend again before this game time. */
	public long lendReadyAt;

	public AllMightState() {
		this.abilityReadyAt = new HashMap<>();
		this.loanPartner = "";
		this.loanPartnerName = "";
	}

	public AllMightState copy() {
		AllMightState n = new AllMightState();
		n.hasPower = hasPower;
		n.fullPower = fullPower;
		n.plusUltra = plusUltra;
		n.transformUntil = transformUntil;
		n.busyUntil = busyUntil;
		n.formChangedAt = formChangedAt;
		n.lastCombatTick = lastCombatTick;
		n.noFallUntil = noFallUntil;
		n.animId = animId;
		n.animStart = animStart;
		n.chargeStart = chargeStart;
		n.plusUltraUntil = plusUltraUntil;
		n.abilityReadyAt.putAll(abilityReadyAt);
		n.output = output;
		n.outputProgress = outputProgress;
		n.lastHitAt = lastHitAt;
		n.strain = strain;
		n.recoilUntil = recoilUntil;
		n.loanRole = loanRole;
		n.loanPartner = loanPartner;
		n.loanPartnerName = loanPartnerName;
		n.loanUntil = loanUntil;
		n.lendReadyAt = lendReadyAt;
		return n;
	}

	/** v0.16.6: the Output / Strain / loan fields, flattened into the same compound as the rest. */
	private record Extra(int output, float outputProgress, long lastHitAt, float strain, long recoilUntil, int loanRole, String loanPartner,
			String loanPartnerName, long loanUntil, long lendReadyAt) {
		static Extra of(AllMightState s) {
			return new Extra(s.output, s.outputProgress, s.lastHitAt, s.strain, s.recoilUntil, s.loanRole, s.loanPartner, s.loanPartnerName,
					s.loanUntil, s.lendReadyAt);
		}
	}

	private static final MapCodec<Extra> EXTRA = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.INT.optionalFieldOf("output", 0).forGetter(Extra::output),
			Codec.FLOAT.optionalFieldOf("output_progress", 0f).forGetter(Extra::outputProgress),
			Codec.LONG.optionalFieldOf("last_hit_at", 0L).forGetter(Extra::lastHitAt),
			Codec.FLOAT.optionalFieldOf("strain", 0f).forGetter(Extra::strain),
			Codec.LONG.optionalFieldOf("recoil_until", 0L).forGetter(Extra::recoilUntil),
			Codec.INT.optionalFieldOf("loan_role", 0).forGetter(Extra::loanRole),
			Codec.STRING.optionalFieldOf("loan_partner", "").forGetter(Extra::loanPartner),
			Codec.STRING.optionalFieldOf("loan_partner_name", "").forGetter(Extra::loanPartnerName),
			Codec.LONG.optionalFieldOf("loan_until", 0L).forGetter(Extra::loanUntil),
			Codec.LONG.optionalFieldOf("lend_ready_at", 0L).forGetter(Extra::lendReadyAt)
	).apply(i, Extra::new));

	public static final Codec<AllMightState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.BOOL.optionalFieldOf("full_power", false).forGetter(s -> s.fullPower),
			Codec.BOOL.optionalFieldOf("plus_ultra", false).forGetter(s -> s.plusUltra),
			Codec.LONG.optionalFieldOf("transform_until", 0L).forGetter(s -> s.transformUntil),
			Codec.LONG.optionalFieldOf("busy_until", 0L).forGetter(s -> s.busyUntil),
			Codec.LONG.optionalFieldOf("form_changed_at", 0L).forGetter(s -> s.formChangedAt),
			Codec.LONG.optionalFieldOf("last_combat_tick", 0L).forGetter(s -> s.lastCombatTick),
			Codec.LONG.optionalFieldOf("no_fall_until", 0L).forGetter(s -> s.noFallUntil),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.LONG.optionalFieldOf("charge_start", 0L).forGetter(s -> s.chargeStart),
			Codec.LONG.optionalFieldOf("plus_ultra_until", 0L).forGetter(s -> s.plusUltraUntil),
			EXTRA.forGetter(Extra::of)
	).apply(i, (hasPower, fullPower, plusUltra, transformUntil, busyUntil, formChangedAt, lastCombatTick, noFallUntil, animId, animStart,
			ready, chargeStart, plusUltraUntil, extra) -> {
		AllMightState s = new AllMightState();
		s.hasPower = hasPower;
		s.fullPower = fullPower;
		s.plusUltra = plusUltra;
		s.transformUntil = transformUntil;
		s.busyUntil = busyUntil;
		s.formChangedAt = formChangedAt;
		s.lastCombatTick = lastCombatTick;
		s.noFallUntil = noFallUntil;
		s.animId = animId;
		s.animStart = animStart;
		s.abilityReadyAt.putAll(ready);
		s.chargeStart = chargeStart;
		s.plusUltraUntil = plusUltraUntil;
		s.output = Math.max(OUTPUT_20, Math.min(OUTPUT_100, extra.output()));
		s.outputProgress = extra.outputProgress();
		s.lastHitAt = extra.lastHitAt();
		s.strain = extra.strain();
		s.recoilUntil = extra.recoilUntil();
		s.loanRole = extra.loanRole();
		s.loanPartner = extra.loanPartner();
		s.loanPartnerName = extra.loanPartnerName();
		s.loanUntil = extra.loanUntil();
		s.lendReadyAt = extra.lendReadyAt();
		return s;
	}));
}
