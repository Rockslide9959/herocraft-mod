package com.projecthero.mod.raven.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client (v0.17.3 Raven): one effect for the client to draw. What the fields mean depends on the kind:
 * <ul>
 *   <li>{@link #CLAWS}: black claws rise out of the ground at {@code a} round a creature of width {@code b.x} and hold for {@code ticks}.</li>
 *   <li>{@link #TENTACLE}: a tentacle of dark energy reaches from {@code a} to entity {@code entity} for {@code ticks}.</li>
 *   <li>{@link #WAVE}: a dark ring spreads from {@code a} out to radius {@code b.x}.</li>
 *   <li>{@link #STREAK}: a streak of shadow from {@code a} to {@code b} (Shadow Dash / Shadow Step).</li>
 *   <li>{@link #HEAL}: a pale thread of light from {@code a} to {@code b} (Empath Heal).</li>
 *   <li>{@link #SINK}: a pool of shadow at {@code a} she sinks into (Shadow Step).</li>
 * </ul>
 * The revamp's (2026-11-13), {@code entity} is the caster unless it says otherwise:
 * <ul>
 *   <li>{@link #GUARD_UP}: the Soul Shield snaps up round her (the bubble itself is drawn from the synced state).</li>
 *   <li>{@link #BURST}: a ball of dark energy bursting at {@code a}, radius {@code b.x} ({@code b.y} 1 = a misfire's wild one).</li>
 *   <li>{@link #CHARGE}: dark energy gathering at her hands (Dark Energy Wave); with {@code b.y} 1, a ring of radius {@code b.x}
 *   tightening round her (Soul Detonation).</li>
 *   <li>{@link #WAVE_FRONT}: the Dark Energy Wave rolling out from {@code a} along {@code b} (flat, unit).</li>
 *   <li>{@link #ASCEND}: Demonic Ascension's eruption at her feet {@code a}.</li>
 *   <li>{@link #VORTEX}: Void Vortex at {@code a}, pull radius {@code b.x}.</li>
 *   <li>{@link #BIND}: Shadow Binding's circle of radius {@code b.x} at {@code a}.</li>
 *   <li>{@link #MARK}: a Soul Detonation sigil over entity {@code entity} (the marked creature).</li>
 *   <li>{@link #BEAM} / {@link #BEAM_END}: Soul Devour's drain beam from her to entity {@code (int) a.x}, and its end.</li>
 *   <li>{@link #RIFT}: a Dimensional Rift portal at {@code a}, facing {@code b}.</li>
 *   <li>{@link #SANCTUARY}: Astral Sanctuary's dome at {@code a}, radius {@code b.x}.</li>
 *   <li>{@link #CHANNEL}: Soul Restoration's channel round her.</li>
 *   <li>{@link #VEIL}: shadow swallowing her (Shadow Veil).</li>
 * </ul>
 */
public record RavenFxPayload(int kind, int entity, Vec3 a, Vec3 b, int ticks) implements CustomPacketPayload {
	public static final int CLAWS = 0;
	public static final int TENTACLE = 1;
	public static final int WAVE = 2;
	public static final int STREAK = 3;
	public static final int HEAL = 4;
	public static final int SINK = 5;
	public static final int GUARD_UP = 6;
	public static final int BURST = 7;
	public static final int CHARGE = 8;
	public static final int WAVE_FRONT = 9;
	public static final int ASCEND = 10;
	public static final int VORTEX = 11;
	public static final int BIND = 12;
	public static final int MARK = 13;
	public static final int BEAM = 14;
	public static final int BEAM_END = 15;
	public static final int RIFT = 16;
	public static final int SANCTUARY = 17;
	public static final int CHANNEL = 18;
	public static final int VEIL = 19;

	public static final CustomPacketPayload.Type<RavenFxPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("raven_fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RavenFxPayload> CODEC = StreamCodec.of(RavenFxPayload::write, RavenFxPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, RavenFxPayload p) {
		buf.writeVarInt(p.kind);
		buf.writeVarInt(p.entity);
		writeVec(buf, p.a);
		writeVec(buf, p.b);
		buf.writeVarInt(p.ticks);
	}

	private static RavenFxPayload read(RegistryFriendlyByteBuf buf) {
		return new RavenFxPayload(buf.readVarInt(), buf.readVarInt(), readVec(buf), readVec(buf), buf.readVarInt());
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
