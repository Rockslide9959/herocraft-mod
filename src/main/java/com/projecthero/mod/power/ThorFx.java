package com.projecthero.mod.power;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v0.14.4: what every viewer needs to animate Thor -- the last one-shot move (id, start tick and the world point it
 * landed on), the moves being channelled right now (bit flags, plus when the Wrath charge began) and the Thor's
 * Armour suit clock (direction + start tick). Synced to everyone, never persisted: the client pose code
 * ({@code ThorPose}), the effect renderer ({@code ThorFxRenderer}) and the piece-by-piece suit reveal
 * ({@code ThorSuitReveal}) all read it. Written only through {@link ThorVisuals}.
 *
 * <p>v0.16.1: also the storm buff ({@link #stormPct}: the bonus in percent while storm-charged, 0 otherwise -- the HUD
 * line and the crackle around him) and the last Thunder Field ({@link #fieldX} .. {@link #fieldRadius}: where it lies,
 * when it formed and when it ends), kept apart from the one-shot move so another move doesn't wipe the field's
 * effect.
 */
public record ThorFx(int anim, long animStart, double x, double y, double z, int channels, long chargeStart,
		int suitDir, long suitStart, int stormPct, double fieldX, double fieldY, double fieldZ, long fieldStart,
		long fieldEnd, float fieldRadius) {
	public static final int ANIM_NONE = 0;
	/** Lightning Strike: Mjolnir to the sky, then brought down on the target. Point = the strike. */
	public static final int ANIM_STRIKE = 1;
	/** Thunderclap: a two-handed overhead slam into the ground. Point = the caster's feet. */
	public static final int ANIM_THUNDERCLAP = 2;
	/** Chain Lightning: the hammer thrust out along the aim, the free hand flung wide. */
	public static final int ANIM_CHAIN = 3;
	/** Throw: a wind-up and follow-through (the hammer has already left the hand). */
	public static final int ANIM_THROW = 4;
	/** Hammer Volley: Mjolnir flung up and out, the free hand directing it. */
	public static final int ANIM_VOLLEY = 5;
	/** God of Thunder's Wrath released: the charged hammer brought down. Point = the impact. */
	public static final int ANIM_WRATH = 6;
	/** Storm Call: the hammer raised to the sky. */
	public static final int ANIM_STORM = 7;
	/**
	 * v0.16.1 Twin Thunder Strike (the dual-wield combo's finisher): both weapons raised, struck by lightning, then
	 * slammed together into the ground. Point = the slam (his feet).
	 */
	public static final int ANIM_TWIN_THUNDER = 8;
	/** v0.16.1 Thunder Field: the weapon hurled down at the aimed ground (Shift + G). Point = where it was aimed. */
	public static final int ANIM_FIELD_THROW = 9;

	// ---- v0.17.7 Cosmic revamp ({@code ThorKit}); the point is the move's target / origin as noted ----
	/** Unarmed Thunder Fist released (point = the punch's end). */
	public static final int ANIM_THUNDER_FIST = 10;
	/** Unarmed Lightning Palm (point = where the bolt lands). */
	public static final int ANIM_LIGHTNING_PALM = 11;
	/** Unarmed Asgardian Grapple: the grab, the lift, the hurl (point = the victim at the grab). */
	public static final int ANIM_GRAPPLE = 12;
	/** Unarmed Storm Rush: four punches. */
	public static final int ANIM_STORM_RUSH = 13;
	/** Unarmed Static Burst (point = his feet). */
	public static final int ANIM_STATIC_BURST = 14;
	/** Unarmed Ground Pound (point = the impact). */
	public static final int ANIM_GROUND_POUND = 15;
	/** Thunder Leap / Executioner's Drop / Meteor Dive take-off (point = where he left). */
	public static final int ANIM_LEAP = 16;
	/** The landing slam of a leap or dive (point = the impact). */
	public static final int ANIM_LEAP_SLAM = 17;
	/** Mjolnir Whirlwind Throw: spun by the strap, then hurled. */
	public static final int ANIM_WHIRLWIND = 18;
	/** Storm Dash (point = where it began). */
	public static final int ANIM_STORM_DASH = 19;
	/** Meteor Dive take-off (point = where he left). */
	public static final int ANIM_METEOR_DIVE = 20;
	/** Stormbreaker Storm Hurl. */
	public static final int ANIM_BIFROST_HURL = 22;
	/** Stormbreaker Storm Cleave (point = the target). */
	public static final int ANIM_STORM_CLEAVE = 23;
	/** Stormbreaker Thunder Step (point = where he left; he is where it lands). */
	public static final int ANIM_BIFROST_STEP = 24;
	/** Stormbreaker Rending Arc. */
	public static final int ANIM_RENDING_ARC = 25;
	/** Stormbreaker Executioner's Drop take-off (point = where he left). */
	public static final int ANIM_EXECUTIONER = 26;
	/** Stormbreaker Executioner's Drop landing (point = the impact). */
	public static final int ANIM_EXECUTIONER_SLAM = 27;
	/** Stormbreaker Lightning Dash (point = where it began). */
	public static final int ANIM_BIFROST_DASH = 28;
	/** Twin Throw (point = where the two weapons meet). */
	public static final int ANIM_TWIN_THROW = 29;
	/** Thunderstruck Cleave (point = where the wave ends). */
	public static final int ANIM_THUNDERSTRUCK = 30;
	/** Crossfire (point = the target). */
	public static final int ANIM_CROSSFIRE = 31;
	/** Lightning Incarnate's closing explosion (point = his feet). */
	public static final int ANIM_INCARNATE_BURST = 32;
	/** Wrath of the Storm (point = the giant bolt's target). */
	public static final int ANIM_WRATH_STORM = 33;
	/** Stormbreaker's Judgement (point = the impaled target). */
	public static final int ANIM_JUDGEMENT = 34;
	/** Ragnarok (point = the centre). */
	public static final int ANIM_RAGNAROK = 35;
	/** God of Thunder (Shift+V): the roar as the eyes light. */
	public static final int ANIM_GOD_OF_THUNDER = 36;
	/** Storm Anchor (both weapons hurled down; point = the aim). */
	public static final int ANIM_STORM_ANCHOR = 37;
	/** God of Thunder's chain bolt from the struck foe to the next (point = the second foe). */
	public static final int ANIM_GOD_CHAIN = 38;

	/** Lightning Beam is being channelled. */
	public static final int CH_BEAM = 1;
	/** God of Thunder's Wrath is charging (since {@link #chargeStart}). (v0.17.7: retired with the hold-Z ultimate.) */
	public static final int CH_WRATH = 2;
	/** v0.17.7: Thunder Fist is charging (since {@link #chargeStart}). */
	public static final int CH_FIST = 4;
	/** v0.17.7: Lightning Incarnate -- he is lightning. */
	public static final int CH_INCARNATE = 8;
	/** v0.17.7: God of Thunder -- glowing eyes. */
	public static final int CH_GOD = 16;
	/** v0.17.7: Storm Sprint -- the electric run. */
	public static final int CH_SPRINT = 32;

	public static final int SUIT_NONE = 0;
	public static final int SUIT_UP = 1;
	public static final int SUIT_DOWN = 2;

	public static final ThorFx EMPTY = new ThorFx(ANIM_NONE, 0L, 0.0, 0.0, 0.0, 0, 0L, SUIT_NONE, 0L, 0, 0.0, 0.0, 0.0,
			0L, 0L, 0f);

	public static final StreamCodec<ByteBuf, ThorFx> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public ThorFx decode(ByteBuf buf) {
			return new ThorFx(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
					ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.DOUBLE.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.DOUBLE.decode(buf),
					ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.FLOAT.decode(buf));
		}

		@Override
		public void encode(ByteBuf buf, ThorFx fx) {
			ByteBufCodecs.VAR_INT.encode(buf, fx.anim());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.animStart());
			ByteBufCodecs.DOUBLE.encode(buf, fx.x());
			ByteBufCodecs.DOUBLE.encode(buf, fx.y());
			ByteBufCodecs.DOUBLE.encode(buf, fx.z());
			ByteBufCodecs.VAR_INT.encode(buf, fx.channels());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.chargeStart());
			ByteBufCodecs.VAR_INT.encode(buf, fx.suitDir());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.suitStart());
			ByteBufCodecs.VAR_INT.encode(buf, fx.stormPct());
			ByteBufCodecs.DOUBLE.encode(buf, fx.fieldX());
			ByteBufCodecs.DOUBLE.encode(buf, fx.fieldY());
			ByteBufCodecs.DOUBLE.encode(buf, fx.fieldZ());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.fieldStart());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.fieldEnd());
			ByteBufCodecs.FLOAT.encode(buf, fx.fieldRadius());
		}
	};

	public boolean has(int channel) {
		return (channels & channel) != 0;
	}

	public ThorFx withAnim(int id, long start, double px, double py, double pz) {
		return new ThorFx(id, start, px, py, pz, channels, chargeStart, suitDir, suitStart, stormPct, fieldX, fieldY,
				fieldZ, fieldStart, fieldEnd, fieldRadius);
	}

	public ThorFx withChannel(int channel, boolean on, long start) {
		int ch = on ? channels | channel : channels & ~channel;
		long charge = (channel == CH_WRATH || channel == CH_FIST) && on ? start : chargeStart;
		return new ThorFx(anim, animStart, x, y, z, ch, charge, suitDir, suitStart, stormPct, fieldX, fieldY, fieldZ,
				fieldStart, fieldEnd, fieldRadius);
	}

	public ThorFx withSuit(int dir, long start) {
		return new ThorFx(anim, animStart, x, y, z, channels, chargeStart, dir, start, stormPct, fieldX, fieldY, fieldZ,
				fieldStart, fieldEnd, fieldRadius);
	}

	/** v0.16.1: the storm buff in percent (0 = not storm-charged). */
	public ThorFx withStorm(int pct) {
		return new ThorFx(anim, animStart, x, y, z, channels, chargeStart, suitDir, suitStart, pct, fieldX, fieldY, fieldZ,
				fieldStart, fieldEnd, fieldRadius);
	}

	/** v0.16.1: a Thunder Field lying at {@code (px, py, pz)} from {@code start} to {@code end}. */
	public ThorFx withField(double px, double py, double pz, long start, long end, float radius) {
		return new ThorFx(anim, animStart, x, y, z, channels, chargeStart, suitDir, suitStart, stormPct, px, py, pz, start,
				end, radius);
	}
}
