package com.projecthero.mod.power;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * v0.14.20: the last counted step of a player's Mjolnir / Stormbreaker melee combo -- which step it was (1..3), which
 * weapon swung it and the game tick it landed. Synced to every viewer (the swing poses play off it), never persisted.
 * The server reads it back as the combo's own state: {@link WeaponCombo} decides from {@link #start} whether the
 * window for the next step is still open. Written only by {@link WeaponCombo}.
 */
public record WeaponComboState(int step, int weapon, long start) {
	public static final int WEAPON_NONE = 0;
	public static final int WEAPON_MJOLNIR = 1;
	public static final int WEAPON_STORMBREAKER = 2;

	public static final WeaponComboState EMPTY = new WeaponComboState(0, WEAPON_NONE, 0L);

	public static final StreamCodec<ByteBuf, WeaponComboState> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, WeaponComboState::step,
			ByteBufCodecs.VAR_INT, WeaponComboState::weapon,
			ByteBufCodecs.VAR_LONG, WeaponComboState::start,
			WeaponComboState::new);
}
