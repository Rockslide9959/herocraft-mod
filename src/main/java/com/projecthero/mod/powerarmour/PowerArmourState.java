package com.projecthero.mod.powerarmour;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v0.17.5 Power Armour: one player's suit state ({@code projecthero:power_armour_state}) -- persistent and synced to every
 * client (others need it to draw the suit). {@link #suit} is the worn suit (null when not in one); {@link #phase} the step of
 * the enter / exit sequence. Only {@link PowerArmour} changes it, always through a {@link #copy()}.
 */
public final class PowerArmourState {
	public static final int NONE = 0;
	/** The frame splits open; the player turns to it. */
	public static final int ENTER_OPEN = 1;
	/** The player steps backwards into the open frame. */
	public static final int ENTER_WALK = 2;
	/** The suit is on the player and closes panel by panel (legs, arms, chest, helmet), then the visor lights. */
	public static final int SEALING = 3;
	public static final int SEALED = 4;
	/** Shift+V held: the suit opens on the player. */
	public static final int UNSEALING = 5;
	/** The player steps forward out of the open frame (which now stands on its own). */
	public static final int EXIT_WALK = 6;

	public SuitData suit;
	public int phase;
	public long phaseStart;
	/** The frame's feet and yaw (where the player ends up when entering, where they step out of when leaving). */
	public double fx, fy, fz;
	public float fyaw;
	/** Where the walk started (position and yaw). */
	public double sx, sy, sz;
	public float syaw;
	/** The frame entity being entered (-1 none). */
	public int frameId = -1;
	public boolean visor;
	/** The current move animation (PowerArmourAbilities.ANIM_*) and its start. */
	public int anim;
	public long animStart;
	/** A held key's charge (punch or rail cannon): which (0 none, 1 punch, 2 rail, 3 exit) and since when. */
	public int charge;
	public long chargeStart;
	public boolean thrusting;
	public long shieldUntil;
	public long droneUntil;
	public long overloadAt;
	/** The mob in the Servo Grip (-1 none). */
	public int grabbed = -1;
	public Map<String, Long> readyAt = new HashMap<>();

	public PowerArmourState() {
	}

	public PowerArmourState copy() {
		PowerArmourState s = new PowerArmourState();
		s.suit = suit == null ? null : suit.copy();
		s.phase = phase;
		s.phaseStart = phaseStart;
		s.fx = fx;
		s.fy = fy;
		s.fz = fz;
		s.fyaw = fyaw;
		s.sx = sx;
		s.sy = sy;
		s.sz = sz;
		s.syaw = syaw;
		s.frameId = frameId;
		s.visor = visor;
		s.anim = anim;
		s.animStart = animStart;
		s.charge = charge;
		s.chargeStart = chargeStart;
		s.thrusting = thrusting;
		s.shieldUntil = shieldUntil;
		s.droneUntil = droneUntil;
		s.overloadAt = overloadAt;
		s.grabbed = grabbed;
		s.readyAt = new HashMap<>(readyAt);
		return s;
	}

	/** Wearing the suit at all (sealing, sealed or opening up). */
	public boolean wearing() {
		return suit != null && (phase == SEALING || phase == SEALED || phase == UNSEALING);
	}

	/** Fully sealed: the kit is live. */
	public boolean sealed() {
		return suit != null && phase == SEALED;
	}

	/** Any part of the enter / exit sequence is running (the player is driven, keys swallowed). */
	public boolean busy() {
		return phase != NONE && phase != SEALED;
	}

	public CompoundTag toTag() {
		CompoundTag t = new CompoundTag();
		if (suit != null) {
			t.put("Suit", suit.toTag());
		}
		t.putInt("Phase", phase);
		t.putLong("PhaseStart", phaseStart);
		t.putDouble("FX", fx);
		t.putDouble("FY", fy);
		t.putDouble("FZ", fz);
		t.putFloat("FYaw", fyaw);
		t.putDouble("SX", sx);
		t.putDouble("SY", sy);
		t.putDouble("SZ", sz);
		t.putFloat("SYaw", syaw);
		t.putInt("Frame", frameId);
		t.putBoolean("Visor", visor);
		t.putInt("Anim", anim);
		t.putLong("AnimStart", animStart);
		t.putInt("Charge", charge);
		t.putLong("ChargeStart", chargeStart);
		t.putBoolean("Thrust", thrusting);
		t.putLong("Shield", shieldUntil);
		t.putLong("Drone", droneUntil);
		t.putLong("Overload", overloadAt);
		t.putInt("Grabbed", grabbed);
		CompoundTag cd = new CompoundTag();
		readyAt.forEach(cd::putLong);
		t.put("Ready", cd);
		return t;
	}

	public static PowerArmourState fromTag(CompoundTag t) {
		PowerArmourState s = new PowerArmourState();
		s.suit = t.contains("Suit") ? SuitData.fromTag(t.getCompound("Suit")) : null;
		s.phase = t.getInt("Phase");
		s.phaseStart = t.getLong("PhaseStart");
		s.fx = t.getDouble("FX");
		s.fy = t.getDouble("FY");
		s.fz = t.getDouble("FZ");
		s.fyaw = t.getFloat("FYaw");
		s.sx = t.getDouble("SX");
		s.sy = t.getDouble("SY");
		s.sz = t.getDouble("SZ");
		s.syaw = t.getFloat("SYaw");
		s.frameId = t.contains("Frame") ? t.getInt("Frame") : -1;
		s.visor = t.getBoolean("Visor");
		s.anim = t.getInt("Anim");
		s.animStart = t.getLong("AnimStart");
		s.charge = t.getInt("Charge");
		s.chargeStart = t.getLong("ChargeStart");
		s.thrusting = t.getBoolean("Thrust");
		s.shieldUntil = t.getLong("Shield");
		s.droneUntil = t.getLong("Drone");
		s.overloadAt = t.getLong("Overload");
		s.grabbed = t.contains("Grabbed") ? t.getInt("Grabbed") : -1;
		CompoundTag cd = t.getCompound("Ready");
		for (String k : cd.getAllKeys()) {
			s.readyAt.put(k, cd.getLong(k));
		}
		return s;
	}

	public static final Codec<PowerArmourState> CODEC = CompoundTag.CODEC.xmap(PowerArmourState::fromTag, PowerArmourState::toTag);
	public static final StreamCodec<RegistryFriendlyByteBuf, PowerArmourState> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG
			.<RegistryFriendlyByteBuf>cast().map(PowerArmourState::fromTag, PowerArmourState::toTag);
}
