package com.projecthero.mod.doctorfate.network;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to the possessed player (v0.17.4 round 2, Doctor Fate): the trial in Nabu's library ({@code DoctorFateNabuTrial}).
 * <ul>
 *   <li>{@link #STATE}: {@code data} = stage, anger, pages found, pages needed, wraths, will test running (0/1), will x1000, band
 *   centre x1000, will progress, progress needed, ticks out of the band, then one entry per lectern (0 unread, 1 a tome read,
 *   2 a page found).</li>
 *   <li>{@link #BOOK}: open the reading view: lectern, kind (0 Nabu's tome, 1 Kent's page), text id.</li>
 *   <li>{@link #QUESTION}: open Nabu's challenge: question id, then the three answer ids in the order shown.</li>
 *   <li>{@link #VERDICT}: something happened the library should show: one of the {@code V_*} codes.</li>
 *   <li>{@link #END}: he's free (the camera flies into the screen).</li>
 *   <li>{@link #WILL}: every tick of the willpower test (v0.17.4 round 2 rebuild): will x10000, velocity x100000, band centre
 *   x10000, band half-width x10000, progress x10, progress needed x10, ticks out of the band in a row, ticks on the floor, lunge
 *   telegraph ticks left (0 none), the hold state the server is using (0/1).</li>
 * </ul>
 */
public record FateTrialPayload(int op, int[] data) implements CustomPacketPayload {
	public static final int STATE = 0;
	public static final int BOOK = 1;
	public static final int QUESTION = 2;
	public static final int VERDICT = 3;
	public static final int END = 4;
	public static final int WILL = 5;

	public static final int V_RIGHT = 0;
	public static final int V_WRONG = 1;
	public static final int V_WRATH = 2;
	public static final int V_WILL_FAILED = 3;
	public static final int V_EMPTY_HANDED = 4;
	public static final int V_PAGES_FOUND = 5;
	public static final int V_WILL_BEGINS = 6;
	public static final int V_TOME = 7;

	public static final CustomPacketPayload.Type<FateTrialPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("doctor_fate_trial"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FateTrialPayload> CODEC = StreamCodec.of((buf, p) -> {
		buf.writeVarInt(p.op);
		buf.writeVarIntArray(p.data);
	}, buf -> new FateTrialPayload(buf.readVarInt(), buf.readVarIntArray(256)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
