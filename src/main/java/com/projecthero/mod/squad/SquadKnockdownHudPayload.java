package com.projecthero.mod.squad;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * v0.17.5 squad knockdown: server &rarr; client, every tick, to the two players a knockdown concerns -- the knocked player
 * (their countdown and, while a squadmate holds right-click on them, the revive progress) and the reviver (the revive
 * progress only). The top "Knocked down" bar itself is a real vanilla boss bar; this only feeds the middle revive bar.
 * {@link #MODE_NONE} clears it (the client also drops a state that has gone 10 ticks without a refresh).
 *
 * @param mode      {@link #MODE_NONE}, {@link #MODE_KNOCKED} or {@link #MODE_REVIVING}
 * @param ticksLeft the knocked player's countdown (paused while being revived)
 * @param total     the countdown's full length this time (75 s, shorter on a quick re-knock)
 * @param progress  revive ticks held so far (0 = nobody reviving)
 * @param needed    revive ticks needed
 * @param name      the other player: who is reviving you, or who you are reviving
 */
public record SquadKnockdownHudPayload(byte mode, int ticksLeft, int total, int progress, int needed, String name)
		implements CustomPacketPayload {
	public static final byte MODE_NONE = 0;
	public static final byte MODE_KNOCKED = 1;
	public static final byte MODE_REVIVING = 2;

	public static final CustomPacketPayload.Type<SquadKnockdownHudPayload> TYPE =
			new CustomPacketPayload.Type<>(ProjectHeroMod.id("squad_knockdown_hud"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SquadKnockdownHudPayload> CODEC =
			StreamCodec.ofMember(SquadKnockdownHudPayload::write, SquadKnockdownHudPayload::read);

	public static SquadKnockdownHudPayload none() {
		return new SquadKnockdownHudPayload(MODE_NONE, 0, 0, 0, 0, "");
	}

	private static void write(SquadKnockdownHudPayload p, RegistryFriendlyByteBuf buf) {
		buf.writeByte(p.mode);
		buf.writeVarInt(p.ticksLeft);
		buf.writeVarInt(p.total);
		buf.writeVarInt(p.progress);
		buf.writeVarInt(p.needed);
		buf.writeUtf(p.name);
	}

	private static SquadKnockdownHudPayload read(RegistryFriendlyByteBuf buf) {
		return new SquadKnockdownHudPayload(buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readUtf());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
