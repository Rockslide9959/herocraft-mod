package com.projecthero.mod.undead;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Undead kit: its two packets. {@link Fx} (server to client): a one-off effect at a place every nearby client draws --
 * the meteor's fall and impact, the Vortex Bullet's shot and burst. {@link ShoulderSword} (client to server): Shift + right click
 * (the client can't send an empty-handed right click into the air any other way); the server re-checks everything.
 */
public final class UndeadKitNet {
	public static final int VORTEX_FIRE = 1;
	public static final int VORTEX_BURST = 2;
	/** a = where it starts in the sky, b = where it lands, extra = ticks to land. */
	public static final int METEOR_FALL = 3;
	/** a = where it landed. */
	public static final int METEOR_IMPACT = 4;
	/** a = the ribs' middle, b = which way they face. */
	public static final int RIB_BLOCK = 5;

	/** How far away the effects are sent. */
	private static final double RANGE = 160.0;

	public record Fx(int kind, Vec3 a, Vec3 b, int extra) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Fx> TYPE = new CustomPacketPayload.Type<>(
				ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "undead_kit_fx"));
		private static final StreamCodec<RegistryFriendlyByteBuf, Vec3> VEC = StreamCodec.composite(ByteBufCodecs.DOUBLE, Vec3::x, ByteBufCodecs.DOUBLE, Vec3::y,
				ByteBufCodecs.DOUBLE, Vec3::z, Vec3::new);
		public static final StreamCodec<RegistryFriendlyByteBuf, Fx> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Fx::kind, VEC, Fx::a, VEC, Fx::b,
				ByteBufCodecs.VAR_INT, Fx::extra, Fx::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record ShoulderSword() implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ShoulderSword> TYPE = new CustomPacketPayload.Type<>(
				ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "undead_shoulder_sword"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ShoulderSword> CODEC = StreamCodec.unit(new ShoulderSword());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private UndeadKitNet() {
	}

	static void initialize() {
		PayloadTypeRegistry.playS2C().register(Fx.TYPE, Fx.CODEC);
		PayloadTypeRegistry.playC2S().register(ShoulderSword.TYPE, ShoulderSword.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ShoulderSword.TYPE, (payload, context) -> context.server().execute(() -> {
			ServerPlayer p = context.player();
			if (p != null && !p.isRemoved()) {
				UndeadShoulderSword.interact(p);
			}
		}));
	}

	/** Sends an effect to everyone near {@code a}. */
	public static void fx(ServerLevel level, int kind, Vec3 a, Vec3 b, int extra) {
		Fx fx = new Fx(kind, a, b, extra);
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(a) <= RANGE * RANGE && ServerPlayNetworking.canSend(p, Fx.TYPE)) {
				ServerPlayNetworking.send(p, fx);
			}
		}
	}
}
