package com.projecthero.mod.greenlantern.construct;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternRingVoice;
import com.projecthero.mod.network.GreenLanternConstructHealthPayload;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * v0.17.1 (user-approved revamp): a hard-light block construct with HP -- the Hard-Light Wall and the Containment Cage --
 * shows how healthy it is. The blocks themselves stay the one {@code HardLightBlock} state; the look is layered on:
 *
 * <ul>
 * <li><b>Cracks</b> under {@link GreenLanternConfig#DURABILITY_CRACK_BELOW} (50%): vanilla's block-breaking overlay on every
 * cell ({@code ServerLevel#destroyBlockProgress}, already synced to everyone within 32 blocks), deepening from stage 0 at
 * 50% to stage 9 at empty. Each cell has its own breaker id ({@link #breakerId}) so the cells never steal each other's
 * crack. Re-sent every {@link GreenLanternConfig#DURABILITY_RESEND_TICKS} (the client forgets a crack after 20 s).</li>
 * <li><b>Dimming, darkening and the stutter</b>: the health fraction goes to nearby players as a
 * {@link GreenLanternConstructHealthPayload}; {@code GreenLanternConstructHealthClient} draws a darker green skin over each
 * cell that thickens as the HP falls and flickers under {@link GreenLanternConfig#DURABILITY_FLICKER_BELOW} (20%).</li>
 * <li>At 0 the existing shatter (glass break + sparks) runs, and everything above is taken back.</li>
 * </ul>
 *
 * <p>The ring's own voice warns its Lantern once a construct drops under 20% ("Construct integrity failing.").
 * Static state, cleared with {@link GreenLanternConstructs#clearSessionState}.
 */
public final class GreenLanternConstructHealth {
	/** Construct instance id -> the damaged construct (only damaged ones are tracked). */
	private static final Map<Integer, Construct> DAMAGED = new ConcurrentHashMap<>();
	/** Vanilla crack ids start here -- far above any entity id. */
	private static final int BREAKER_BASE = 0x40000000;
	private static final double SEND_RADIUS = 96.0;

	private GreenLanternConstructHealth() {
	}

	public static void clearSessionState() {
		DAMAGED.clear();
	}

	/** 0..1 of the construct's HP (1 for HP-less kinds). */
	public static float fraction(Construct c) {
		float max = c.type.maxHp();
		return max <= 0f ? 1f : Mth.clamp(c.hp / max, 0f, 1f);
	}

	/** Crack stage for a fraction: -1 (none) at or above 50%, then 0..9 down to empty. */
	public static int crackStage(float fraction) {
		if (fraction >= GreenLanternConfig.DURABILITY_CRACK_BELOW) {
			return -1;
		}
		float t = 1f - fraction / GreenLanternConfig.DURABILITY_CRACK_BELOW;
		return Mth.clamp((int) (t * 10f), 0, 9);
	}

	public static int breakerId(Construct c, int cell) {
		return BREAKER_BASE + (c.instanceId & 0x3FFFF) * 512 + (cell & 511);
	}

	/** The construct just lost HP (still standing): update its cracks, tell nearby clients, warn its Lantern. */
	public static void onDamaged(Construct c, float before) {
		if (c.type.maxHp() <= 0f || c.hp <= 0f) {
			return;
		}
		DAMAGED.put(c.instanceId, c);
		float frac = fraction(c);
		float was = Mth.clamp(before / c.type.maxHp(), 0f, 1f);
		send(c, frac);
		if (frac < GreenLanternConfig.DURABILITY_FLICKER_BELOW && was >= GreenLanternConfig.DURABILITY_FLICKER_BELOW
				&& c.level.getServer() != null) {
			ServerPlayer owner = c.level.getServer().getPlayerList().getPlayer(c.owner);
			GreenLanternRingVoice.say(owner, GreenLanternRingVoice.Line.INTEGRITY);
		}
	}

	/** The construct is gone (broken, dismissed, expired...): take its cracks and tint back. */
	public static void onEnded(Construct c) {
		if (DAMAGED.remove(c.instanceId) == null) {
			return;
		}
		for (int i = 0; i < c.cells.size(); i++) {
			c.level.destroyBlockProgress(breakerId(c, i), c.cells.get(i).pos(), -1);
		}
		broadcast(c, new GreenLanternConstructHealthPayload(c.instanceId, -1f, List.of()));
	}

	/** Once a server tick (from {@link GreenLanternConstructs#tick}): keep the cracks and tint fresh for damaged ones. */
	public static void tick(MinecraftServer server) {
		if (DAMAGED.isEmpty() || server.overworld().getGameTime() % GreenLanternConfig.DURABILITY_RESEND_TICKS != 0) {
			return;
		}
		for (Construct c : DAMAGED.values()) {
			if (c.hp <= 0f || !GreenLanternConstructs.of(c.owner).contains(c)) {
				onEnded(c);
				continue;
			}
			send(c, fraction(c));
		}
	}

	private static void send(Construct c, float frac) {
		int stage = crackStage(frac);
		List<BlockPos> cells = new ArrayList<>();
		int placed = Math.min(c.built, c.cells.size());
		for (int i = 0; i < placed; i++) {
			BlockPos pos = c.cells.get(i).pos();
			if (c.level.getBlockState(pos) != c.cells.get(i).placed()) {
				continue;
			}
			cells.add(pos);
			c.level.destroyBlockProgress(breakerId(c, i), pos, stage);
		}
		if (cells.size() > 512) {
			cells = cells.subList(0, 512);
		}
		broadcast(c, new GreenLanternConstructHealthPayload(c.instanceId, frac, cells));
	}

	private static void broadcast(Construct c, GreenLanternConstructHealthPayload payload) {
		for (ServerPlayer p : PlayerLookup.around(c.level, c.anchor, SEND_RADIUS)) {
			if (ServerPlayNetworking.canSend(p, GreenLanternConstructHealthPayload.TYPE)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	/** Tests: whether this construct is being shown as damaged. */
	public static boolean isTracked(Construct c) {
		return DAMAGED.containsKey(c.instanceId);
	}
}
