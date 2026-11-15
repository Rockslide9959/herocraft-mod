package com.projecthero.mod.targetdummy;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.5, server to client: one hit on a {@link TargetDummyEntity} (or a stats reset) plus the combat totals after it,
 * sent to every player tracking the dummy. The client draws the floating number, starts the wobble and refreshes the
 * readout from it; the server's numbers are the real ones.
 *
 * @param entityId     the dummy
 * @param raw          the hit before armour / protection / Resistance
 * @param dealt        what landed after them
 * @param kind         {@link DummyDamageKind} ordinal (colour)
 * @param flags        {@link #CRIT} / {@link #DOT} / {@link #NEW_COMBAT} / {@link #RESET} / {@link #COOLDOWN}
 * @param label        where it came from ("Diamond Sword", "Arrow", "R ability", "Burning")
 * @param total        combat total dealt
 * @param totalRaw     combat total before armour
 * @param hits         combat hit count (0 with {@link #RESET})
 * @param biggest      biggest single hit dealt this combat
 * @param combatTicks  ticks from the combat's first hit to this one
 * @param hitYaw       direction the hit travelled, degrees (atan2 of z, x), NaN when unknown -- the wobble leans that way
 */
public record DummyHitPayload(int entityId, float raw, float dealt, byte kind, byte flags, Component label, float total, float totalRaw,
		int hits, float biggest, int combatTicks, float hitYaw) implements CustomPacketPayload {
	public static final byte CRIT = 1;
	public static final byte DOT = 2;
	public static final byte NEW_COMBAT = 4;
	/** Not a hit: the stats were cleared (right-click). */
	public static final byte RESET = 8;
	/** The dummy runs with vanilla hit cooldown (mob-accurate) instead of "every hit counts". */
	public static final byte COOLDOWN = 16;

	public static final CustomPacketPayload.Type<DummyHitPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("target_dummy_hit"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DummyHitPayload> CODEC = StreamCodec.of(DummyHitPayload::write, DummyHitPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, DummyHitPayload p) {
		buf.writeVarInt(p.entityId);
		buf.writeFloat(p.raw);
		buf.writeFloat(p.dealt);
		buf.writeByte(p.kind);
		buf.writeByte(p.flags);
		ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.label);
		buf.writeFloat(p.total);
		buf.writeFloat(p.totalRaw);
		buf.writeVarInt(p.hits);
		buf.writeFloat(p.biggest);
		buf.writeVarInt(p.combatTicks);
		buf.writeFloat(p.hitYaw);
	}

	private static DummyHitPayload read(RegistryFriendlyByteBuf buf) {
		return new DummyHitPayload(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readByte(),
				ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readFloat(),
				buf.readVarInt(), buf.readFloat());
	}

	public boolean has(byte flag) {
		return (flags & flag) != 0;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
