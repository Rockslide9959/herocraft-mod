package com.projecthero.mod.spin.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client (v0.17.3 the Spin): one Spin effect for the client to draw. What each field means depends on the kind:
 * <ul>
 *   <li>{@link #SCAN_BALL}: a ball spinning on the ground at {@code a} for {@code ticks} (everyone near).</li>
 *   <li>{@link #SCAN_GLOW}: {@code ids} glow through walls for {@code ticks} (the scanning player only).</li>
 *   <li>{@link #GOLDEN_LINE}: the Golden Spin's line from {@code a} to {@code b}; {@code flag} = a Perfect Spin.</li>
 *   <li>{@link #BREAKER}: the Ball Breaker figure bursts out at {@code a} and strikes entity {@code entity} after {@code ticks}.</li>
 *   <li>{@link #QUAKE}: the Spin Quake's ring at {@code a}.</li>
 *   <li>{@link #PERFECT}: the caster's own "Perfect Spin" flash on the HUD ({@code flag} = perfect, else a miss).</li>
 * </ul>
 */
public record SpinFxPayload(int kind, int entity, Vec3 a, Vec3 b, int ticks, boolean flag, int[] ids) implements CustomPacketPayload {
	public static final int SCAN_BALL = 0;
	public static final int SCAN_GLOW = 1;
	public static final int GOLDEN_LINE = 2;
	public static final int BREAKER = 3;
	public static final int QUAKE = 4;
	public static final int PERFECT = 5;

	public static final CustomPacketPayload.Type<SpinFxPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("spin_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpinFxPayload> CODEC = StreamCodec.of(SpinFxPayload::write, SpinFxPayload::read);

	public static SpinFxPayload at(int kind, Vec3 a, int ticks) {
		return new SpinFxPayload(kind, -1, a, Vec3.ZERO, ticks, false, new int[0]);
	}

	private static void write(RegistryFriendlyByteBuf buf, SpinFxPayload p) {
		buf.writeVarInt(p.kind);
		buf.writeVarInt(p.entity);
		writeVec(buf, p.a);
		writeVec(buf, p.b);
		buf.writeVarInt(p.ticks);
		buf.writeBoolean(p.flag);
		buf.writeVarIntArray(p.ids);
	}

	private static SpinFxPayload read(RegistryFriendlyByteBuf buf) {
		int kind = buf.readVarInt();
		int entity = buf.readVarInt();
		Vec3 a = readVec(buf);
		Vec3 b = readVec(buf);
		int ticks = buf.readVarInt();
		boolean flag = buf.readBoolean();
		int[] ids = buf.readVarIntArray(4096);
		return new SpinFxPayload(kind, entity, a, b, ticks, flag, ids);
	}

	private static void writeVec(RegistryFriendlyByteBuf buf, Vec3 v) {
		buf.writeDouble(v.x);
		buf.writeDouble(v.y);
		buf.writeDouble(v.z);
	}

	private static Vec3 readVec(RegistryFriendlyByteBuf buf) {
		return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
