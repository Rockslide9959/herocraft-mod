package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server &rarr; client (v0.17.1): the Hard-Light Drone and Gateway's messages to their own Lantern only.
 * <ul>
 *   <li>{@link #MARKS}: the hostiles the drone just swept ({@code ids}), outlined for {@code a} ticks on this client alone
 *   (the Ring Scan's private-glow pattern -- nothing is set on the mobs).</li>
 *   <li>{@link #SCOUT_REPORT}: the scout run's summary popup -- {@code a} hostiles, {@code b} chests, {@code c} other
 *   creatures (and {@code ids} = the hostiles, outlined like {@link #MARKS}).</li>
 *   <li>{@link #OPEN_PICKER} / {@link #OPEN_WAYPOINTS}: open the gateway's waypoint picker / the Waypoints screen
 *   ({@code a} = seconds of cooldown left, 0 = ready).</li>
 * </ul>
 */
public record GreenLanternReconPayload(int action, int[] ids, int a, int b, int c) implements CustomPacketPayload {
	public static final int MARKS = 0;
	public static final int SCOUT_REPORT = 1;
	public static final int OPEN_PICKER = 2;
	public static final int OPEN_WAYPOINTS = 3;

	public static final CustomPacketPayload.Type<GreenLanternReconPayload> TYPE = new CustomPacketPayload.Type<>(
			ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "green_lantern_recon"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GreenLanternReconPayload> CODEC =
			StreamCodec.ofMember(GreenLanternReconPayload::write, GreenLanternReconPayload::read);

	private static void write(GreenLanternReconPayload p, RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(p.action);
		buf.writeVarIntArray(p.ids);
		buf.writeVarInt(p.a);
		buf.writeVarInt(p.b);
		buf.writeVarInt(p.c);
	}

	private static GreenLanternReconPayload read(RegistryFriendlyByteBuf buf) {
		return new GreenLanternReconPayload(buf.readVarInt(), buf.readVarIntArray(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
