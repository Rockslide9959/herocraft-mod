package com.projecthero.mod.ultron.contagion;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ultron.contagion.entity.UltronNexusEntity;
import com.projecthero.mod.ultron.contagion.entity.UltronOmegaEntity;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the Contagion's packets.
 * <ul>
 *   <li>{@link Hud} (server -> client, every 10 ticks): everything the Contagion HUD draws -- the Saturation Hairline, the
 *       Nexus count and Traces, the hijack "OVERRIDE ATTEMPT" Hairline / lock / static flash, the capture circle readout,
 *       the finale's red diamond, and the Vision moment's prompt and gold tint.</li>
 *   <li>{@link Beam} (server -> nearby clients): a coloured beam (the Stark Lift's blue, the Mind Stone's gold).</li>
 *   <li>{@link MindHold} (client -> server): "I am holding right-click with a Mind Stone" (the Vision beam / the Empowered
 *       Mind Stone Beam, {@link MindStoneBeam}).</li>
 * </ul>
 */
public final class ContagionNet {
	public static final int F_ACTIVE = 1, F_LOCKED = 2, F_GLITCH = 4, F_IN_CORRUPTION = 8, F_MARKER = 16, F_VISION_PROMPT = 32, F_GOLD = 64,
			F_CAPTURE_MIND = 128;

	public record Hud(int flags, float saturation, byte nexus, byte traces, byte version, float hijack, float capture, short captureSeconds,
			byte capturePlayers, int markerX, int markerZ, float visionHold) implements CustomPacketPayload {
		public static final Type<Hud> TYPE = new Type<>(ProjectHeroMod.id("contagion_hud"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hud> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeVarInt(p.flags);
			buf.writeFloat(p.saturation);
			buf.writeByte(p.nexus);
			buf.writeByte(p.traces);
			buf.writeByte(p.version);
			buf.writeFloat(p.hijack);
			buf.writeFloat(p.capture);
			buf.writeShort(p.captureSeconds);
			buf.writeByte(p.capturePlayers);
			buf.writeInt(p.markerX);
			buf.writeInt(p.markerZ);
			buf.writeFloat(p.visionHold);
		}, buf -> new Hud(buf.readVarInt(), buf.readFloat(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readFloat(), buf.readFloat(),
				buf.readShort(), buf.readByte(), buf.readInt(), buf.readInt(), buf.readFloat()));

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Beam(float ax, float ay, float az, float bx, float by, float bz, int color, float width, byte life) implements CustomPacketPayload {
		public static final Type<Beam> TYPE = new Type<>(ProjectHeroMod.id("contagion_beam"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Beam> CODEC = StreamCodec.of((buf, p) -> {
			buf.writeFloat(p.ax);
			buf.writeFloat(p.ay);
			buf.writeFloat(p.az);
			buf.writeFloat(p.bx);
			buf.writeFloat(p.by);
			buf.writeFloat(p.bz);
			buf.writeInt(p.color);
			buf.writeFloat(p.width);
			buf.writeByte(p.life);
		}, buf -> new Beam(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt(),
				buf.readFloat(), buf.readByte()));

		public Vec3 from() {
			return new Vec3(ax, ay, az);
		}

		public Vec3 to() {
			return new Vec3(bx, by, bz);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record MindHold(boolean holding) implements CustomPacketPayload {
		public static final Type<MindHold> TYPE = new Type<>(ProjectHeroMod.id("mind_stone_hold"));
		public static final StreamCodec<RegistryFriendlyByteBuf, MindHold> CODEC = StreamCodec.of((buf, p) -> buf.writeBoolean(p.holding),
				buf -> new MindHold(buf.readBoolean()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private ContagionNet() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playS2C().register(Hud.TYPE, Hud.CODEC);
		PayloadTypeRegistry.playS2C().register(Beam.TYPE, Beam.CODEC);
		PayloadTypeRegistry.playC2S().register(MindHold.TYPE, MindHold.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MindHold.TYPE, (payload, ctx) -> ctx.server().execute(
				() -> MindStoneBeam.setHolding(ctx.player(), payload.holding())));
	}

	/** A coloured beam {@code a -> b} for every player within 128 blocks. */
	public static void beam(ServerLevel level, Vec3 a, Vec3 b, int color, float width, int life) {
		Beam payload = new Beam((float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z, color, width, (byte) life);
		for (ServerPlayer p : PlayerLookup.around(level, a.add(b).scale(0.5), 128 + a.distanceTo(b) * 0.5)) {
			if (ServerPlayNetworking.canSend(p, Beam.TYPE)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	/** Sends every online player their Contagion HUD state. */
	public static void syncAll(ServerLevel overworld, ContagionState st) {
		for (ServerPlayer p : overworld.getServer().getPlayerList().getPlayers()) {
			if (!ServerPlayNetworking.canSend(p, Hud.TYPE)) {
				continue;
			}
			ServerPlayNetworking.send(p, build(p, st));
		}
	}

	static Hud build(ServerPlayer p, ContagionState st) {
		boolean running = st.stage == ContagionState.Stage.ACTIVE || st.stage == ContagionState.Stage.FINALE;
		if (!running || p.level().dimension() != Level.OVERWORLD || !ContagionConfig.get().enabled) {
			return new Hud(0, 0f, (byte) 0, (byte) 0, (byte) 1, -1f, -1f, (short) 0, (byte) 0, 0, 0, -1f);
		}
		ServerLevel level = p.serverLevel();
		int flags = F_ACTIVE;
		if (SuitHijack.locked(p)) {
			flags |= F_LOCKED;
		}
		if (SuitHijack.glitchFlag(p)) {
			flags |= F_GLITCH;
		}
		if (Contagion.corruptionAt(level, p.blockPosition()) != null) {
			flags |= F_IN_CORRUPTION;
		}
		float capture = -1f;
		short secs = 0;
		byte players = 0;
		for (UltronNexusEntity n : level.getEntitiesOfClass(UltronNexusEntity.class, new AABB(p.blockPosition()).inflate(48))) {
			if (n.capturing()) {
				capture = n.captureProgress();
				secs = (short) Math.min(Short.MAX_VALUE, n.captureSecondsLeft());
				players = (byte) n.capturePlayers();
				if (n.captureMindStone()) {
					flags |= F_CAPTURE_MIND;
				}
				break;
			}
		}
		int mx = 0, mz = 0;
		float hold = -1f;
		SokoviaFinale f = st.finale;
		if (f != null && f.phase != SokoviaFinale.Phase.ENDING && f.site != null) {
			flags |= F_MARKER;
			mx = f.site.getX();
			mz = f.site.getZ();
			UltronOmegaEntity o = f.omega(level);
			if (o != null && o.inVision() && o.distanceToSqr(p) < 64 * 64) {
				flags |= F_GOLD;
				if (Contagion.holdsMindStoneAny(p) && o.distanceToSqr(p) < MindStoneBeam.VISION_RANGE * MindStoneBeam.VISION_RANGE) {
					flags |= F_VISION_PROMPT;
					hold = MindStoneBeam.visionHold(p);
				}
			}
		}
		return new Hud(flags, (float) st.saturation, (byte) st.nexus.size(), (byte) st.traces, (byte) st.version, SuitHijack.attemptProgress(p), capture,
				secs, players, mx, mz, hold);
	}
}
