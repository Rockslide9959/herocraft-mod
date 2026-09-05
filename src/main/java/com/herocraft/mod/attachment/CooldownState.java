package com.herocraft.mod.attachment;

import java.util.EnumMap;
import java.util.Map;

import com.herocraft.mod.power.ThorAbility;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Per-player, per-ability cooldown tracker. Deliberately independent of vanilla
 * {@code ItemCooldowns} (which is keyed by item, and Mjolnir is a single item shared by several
 * abilities) so that e.g. Lightning Strike being on cooldown never blocks Flight or Thunderclap.
 * Not persisted -- cooldowns resetting across a server restart is an acceptable simplification.
 *
 * <p>v0.6.22: synced to the owning client (target-only) so the Thor HUD can shade each ability box
 * while it recharges, the way the Spider-Man HUD already does.
 */
public final class CooldownState {
	private static final ThorAbility[] ABILITIES = ThorAbility.values();

	/** Writes each entry as (ordinal byte, readyAt varlong). Absolute game-tick values, which are
	 *  identical on both sides, so no rebasing is needed on the client. */
	public static final StreamCodec<ByteBuf, CooldownState> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public CooldownState decode(ByteBuf buf) {
			CooldownState state = new CooldownState();
			int count = ByteBufCodecs.VAR_INT.decode(buf);
			for (int i = 0; i < count; i++) {
				int ordinal = buf.readUnsignedByte();
				long readyAt = ByteBufCodecs.VAR_LONG.decode(buf);
				if (ordinal >= 0 && ordinal < ABILITIES.length) {
					state.readyAtTick.put(ABILITIES[ordinal], readyAt);
				}
			}
			return state;
		}

		@Override
		public void encode(ByteBuf buf, CooldownState state) {
			ByteBufCodecs.VAR_INT.encode(buf, state.readyAtTick.size());
			state.readyAtTick.forEach((ability, readyAt) -> {
				buf.writeByte(ability.ordinal());
				ByteBufCodecs.VAR_LONG.encode(buf, readyAt);
			});
		}
	};

	private final Map<ThorAbility, Long> readyAtTick = new EnumMap<>(ThorAbility.class);

	public boolean isReady(ThorAbility ability, long currentTick) {
		Long readyAt = readyAtTick.get(ability);
		return readyAt == null || currentTick >= readyAt;
	}

	public void trigger(ThorAbility ability, long currentTick, int cooldownTicks) {
		readyAtTick.put(ability, currentTick + cooldownTicks);
	}

	public int remainingTicks(ThorAbility ability, long currentTick) {
		Long readyAt = readyAtTick.get(ability);
		return readyAt == null ? 0 : (int) Math.max(0L, readyAt - currentTick);
	}
}
