package com.projecthero.mod.speedforce.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server (v0.17.1): a speedster's own settings -- Ground-Stick on or off (Shift+N for the plain Speedster, the
 * Shift+N menu for the Speed Force Speedster), the Speed Force Speedster's lightning colour ({@code -1} = unchanged) and,
 * from the menu's Suit row, a Flash Suit style for the suit they wear or carry in the ring
 * ({@link com.projecthero.mod.flash.FlashSuitStyle} ordinal, {@code -1} = unchanged). The style is not saved in
 * SpeedForceState: it is stamped on the suit's items. v0.17.1 (user) {@code assists}: the menu's run assists packed by
 * {@link #assists(boolean, boolean, int)} ({@code -1} = unchanged).
 */
public record SpeedsterPrefsPayload(boolean groundStick, int colour, int suitStyle, int assists) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpeedsterPrefsPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "speedster_prefs"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpeedsterPrefsPayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeBoolean(p.groundStick);
				buf.writeVarInt(p.colour + 1);
				buf.writeVarInt(p.suitStyle + 1);
				buf.writeVarInt(p.assists + 1);
			},
			buf -> new SpeedsterPrefsPayload(buf.readBoolean(), buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readVarInt() - 1));

	/** Ground-Stick and colour only (the suit style and the assists unchanged). */
	public SpeedsterPrefsPayload(boolean groundStick, int colour) {
		this(groundStick, colour, -1, -1);
	}

	public SpeedsterPrefsPayload(boolean groundStick, int colour, int suitStyle) {
		this(groundStick, colour, suitStyle, -1);
	}

	/** Auto-Slowdown (bit 0), vein mining (bit 1), the speed cap in tenths 1..10 (bits 4-7). */
	public static int assists(boolean autoSlow, boolean veinMine, int speedCap) {
		return (autoSlow ? 1 : 0) | (veinMine ? 2 : 0) | ((Math.max(1, Math.min(10, speedCap)) & 0x0F) << 4);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
