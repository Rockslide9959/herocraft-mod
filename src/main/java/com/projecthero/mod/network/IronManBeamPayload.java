package com.projecthero.mod.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Server -&gt; client "draw a repulsor / Unibeam beam from A to B" packet. Purely visual -- all
 * damage and energy is already resolved server-side. Broadcast to the shooter and everyone tracking
 * them, so the beam is visible in first <em>and</em> third person. The client
 * ({@code IronManBeamClient}) spawns a dense particle line, which is visible from the player's own
 * eyes (a server-spawned particle line starting at the eye is not).
 *
 * @param kind 0 = repulsor, 1 = charged repulsor, 2 = Unibeam, 3 = Mark 4 wrist laser (thin red)
 */
public record IronManBeamPayload(Vec3 start, Vec3 end, int kind) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<IronManBeamPayload> TYPE =
			new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "iron_man_beam"));

	private static final StreamCodec<RegistryFriendlyByteBuf, Vec3> VEC3 = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, Vec3::x,
			ByteBufCodecs.DOUBLE, Vec3::y,
			ByteBufCodecs.DOUBLE, Vec3::z,
			Vec3::new);

	public static final StreamCodec<RegistryFriendlyByteBuf, IronManBeamPayload> CODEC = StreamCodec.composite(
			VEC3, IronManBeamPayload::start,
			VEC3, IronManBeamPayload::end,
			ByteBufCodecs.VAR_INT, IronManBeamPayload::kind,
			IronManBeamPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
