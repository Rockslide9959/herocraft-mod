package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Moon Knight Phase 6: server to client visuals for Khonshu's moves that particles alone can't carry.
 * <ul>
 *   <li>{@link Kind#MOONBEAM} -- a beacon-style column of moonlight at ({@code x, y, z}), drawn by every nearby client
 *       ({@code arg} = beam lifetime in ticks);</li>
 *   <li>{@link Kind#RESURRECTION_FLASH} -- the bright white screen flash of Khonshu's Resurrection, sent only to the
 *       player who was saved ({@code arg} = flash length in ticks);</li>
 *   <li>{@link Kind#EYE_SKULL} -- the Eye of Khonshu: trace Khonshu's skull ({@code MoonKnightSkull}) in the sky
 *       centred on ({@code x, y, z}), facing yaw {@code arg} degrees, for every client in range.</li>
 * </ul>
 * Cosmetic only; every rule is decided server-side. Kept separate from anything the Temple ritual adds.
 */
public record MoonKnightKhonshuFxPayload(Kind kind, double x, double y, double z, int arg) implements CustomPacketPayload {
	public enum Kind {
		MOONBEAM,
		RESURRECTION_FLASH,
		EYE_SKULL
	}

	public static final CustomPacketPayload.Type<MoonKnightKhonshuFxPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "moon_knight_khonshu_fx"));

	public static final StreamCodec<FriendlyByteBuf, MoonKnightKhonshuFxPayload> CODEC =
			StreamCodec.ofMember(MoonKnightKhonshuFxPayload::write, MoonKnightKhonshuFxPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(kind.ordinal());
		buf.writeDouble(x);
		buf.writeDouble(y);
		buf.writeDouble(z);
		buf.writeVarInt(arg);
	}

	private static MoonKnightKhonshuFxPayload read(FriendlyByteBuf buf) {
		Kind[] kinds = Kind.values();
		Kind kind = kinds[Math.floorMod(buf.readVarInt(), kinds.length)];
		return new MoonKnightKhonshuFxPayload(kind, buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
