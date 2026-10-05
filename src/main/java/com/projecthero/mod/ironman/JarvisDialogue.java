package com.projecthero.mod.ironman;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.network.IronManJarvisPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.14.29 (agent F): JARVIS, the suit's voice -- the Iron Man counterpart of {@code SymbioteDialogue}.
 *
 * <p>Once per {@link #EVAL_INTERVAL} ticks it compares the suit's state with what it saw last time and reacts to the
 * <b>edges</b>: the suit coming online after a suit-up, energy dropping under 25% / 10%, integrity under 35%, the first
 * target lock, the faceplate going up or down (only now and then), Protocol Phoenix firing, going under water (the
 * suit seals up), the Mark 2 nearing its icing ceiling, and the suit being stored. Every line has its own cooldown, and
 * there is a per-player gap between any two lines; urgent lines ({@link Line#urgent}) skip the gap but never their own
 * cooldown. The first look at a player only records a baseline -- logging in already suited says nothing.
 *
 * <p>The Mark 1 is the cave suit -- no JARVIS. It only gets a crude {@code SYSTEM:} readout for power-on, low power and
 * hull damage. The client draws the line in a wrapped speech box ({@code JarvisClient}) and can mute it with
 * {@code /jarvis off}. Static per-player state, cleared in {@code ServerStateReset}.
 */
public final class JarvisDialogue {
	public static final int EVAL_INTERVAL = 5;
	/** Minimum gap between two non-urgent lines to the same player. */
	public static final int GLOBAL_GAP_TICKS = 60;

	/** Every JARVIS line: id, per-line cooldown, whether it may cut in on the global gap, Mark 1 crude variant. */
	public enum Line {
		ONLINE("online", 20 * 20, false, "system_online"),
		ENERGY_25("energy_25", 45 * 20, false, "system_energy_low"),
		ENERGY_10("energy_10", 45 * 20, true, "system_energy_critical"),
		INTEGRITY_35("integrity_35", 60 * 20, true, "system_hull"),
		TARGET_LOCK("target_lock", 30 * 20, false, null),
		FACEPLATE_UP("faceplate_up", 90 * 20, false, null),
		FACEPLATE_DOWN("faceplate_down", 90 * 20, false, null),
		PHOENIX("phoenix", 30 * 20, true, null),
		WATER("water", 45 * 20, false, null),
		CEILING("ceiling", 30 * 20, true, null),
		STORED("stored", 20 * 20, false, null);

		public final String id;
		public final int cooldownTicks;
		public final boolean urgent;
		public final String crudeId;

		Line(String id, int cooldownTicks, boolean urgent, String crudeId) {
			this.id = id;
			this.cooldownTicks = cooldownTicks;
			this.urgent = urgent;
			this.crudeId = crudeId;
		}
	}

	/** What the previous evaluation saw. */
	private static final class Seen {
		boolean online;
		boolean anyWorn;
		float energy = 1f;
		float integrity = 1f;
		boolean locked;
		boolean faceOpen;
		boolean underwater;
		boolean nearCeiling;
		boolean phoenix;
		String suitId;
		final Map<Line, Long> lineReadyAt = new HashMap<>();
		long lastSpokenAt = Long.MIN_VALUE / 2;
		String lastLine;
		int spokenCount;
	}

	private static final Map<UUID, Seen> SEEN = new HashMap<>();

	private JarvisDialogue() {
	}

	public static void tick(ServerPlayer player) {
		if (!TonyStark.hasPower(player)) {
			SEEN.remove(player.getUUID());
			return;
		}
		if (player.tickCount % EVAL_INTERVAL != 0) {
			return;
		}
		long now = player.level().getGameTime();
		TonyStarkState s = TonyStark.state(player);
		String suitId = IronManArmor.wornSuitId(player);
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
		boolean anyWorn = IronManArmor.wearingAnyIronMan(player);
		boolean online = suit != null && IronManArmor.wearingFullSuit(player, suitId) && !IronManSuitUpManager.inTransition(player);
		boolean phoenix = s.phoenixEmergencyUntil != 0L;

		Seen prev = SEEN.get(player.getUUID());
		Seen cur = prev == null ? new Seen() : prev;
		boolean baseline = prev == null;
		boolean wasOnline = cur.online;
		boolean wasAny = cur.anyWorn;
		float wasEnergy = cur.energy;
		float wasIntegrity = cur.integrity;
		boolean wasLocked = cur.locked;
		boolean wasFace = cur.faceOpen;
		boolean wasWater = cur.underwater;
		boolean wasCeiling = cur.nearCeiling;
		boolean wasPhoenix = cur.phoenix;
		String prevSuit = cur.suitId;
		boolean sameSuit = suitId != null && suitId.equals(prevSuit);

		float energy = suit == null ? 1f : IronManEnergy.energyFraction(player, suitId);
		float integrity = suit == null ? 1f : IronManEnergy.integrity(player, suitId) / IronManEnergy.maxIntegrity(suitId);
		boolean locked = online && IronManTargeting.locked(player) != null;
		boolean faceOpen = suit != null && IronManArmor.hasHelmet(player, suitId) && IronManFaceplate.isOpen(player);
		boolean underwater = online && player.isUnderWater();
		boolean nearCeiling = online && suit.altitudeCeiling() > 0.0 && player.getY() >= suit.altitudeCeiling() - 20.0;

		cur.online = online;
		cur.anyWorn = anyWorn;
		cur.energy = energy;
		cur.integrity = integrity;
		cur.locked = locked;
		cur.faceOpen = faceOpen;
		cur.underwater = underwater;
		cur.nearCeiling = nearCeiling;
		cur.phoenix = phoenix;
		if (suitId != null) {
			cur.suitId = suitId;
		}
		SEEN.put(player.getUUID(), cur);
		if (baseline) {
			return;
		}

		boolean crude = "mark_1".equals(suitId);
		// strict priority: the first edge that may speak wins this evaluation
		if (phoenix && !wasPhoenix && attempt(player, cur, Line.PHOENIX, false, now)) {
			return;
		}
		if (suit == null) {
			if (wasAny && !anyWorn && !phoenix) {
				attempt(player, cur, Line.STORED, "mark_1".equals(prevSuit), now);
			}
			return;
		}
		if (sameSuit && online && wasIntegrity >= 0.35f && integrity < 0.35f && attempt(player, cur, Line.INTEGRITY_35, crude, now)) {
			return;
		}
		if (sameSuit && online && wasEnergy >= 0.10f && energy < 0.10f && attempt(player, cur, Line.ENERGY_10, crude, now)) {
			return;
		}
		if (sameSuit && online && wasEnergy >= 0.25f && energy < 0.25f && energy >= 0.10f && attempt(player, cur, Line.ENERGY_25, crude, now)) {
			return;
		}
		if (suit.ceilingFreeze() && nearCeiling && !wasCeiling && attempt(player, cur, Line.CEILING, crude, now)) {
			return;
		}
		if (online && !wasOnline && attempt(player, cur, Line.ONLINE, crude, now)) {
			return;
		}
		if (underwater && !wasWater && attempt(player, cur, Line.WATER, crude, now)) {
			return;
		}
		if (locked && !wasLocked && attempt(player, cur, Line.TARGET_LOCK, crude, now)) {
			return;
		}
		if (online && faceOpen != wasFace && player.getRandom().nextInt(3) == 0) {
			attempt(player, cur, faceOpen ? Line.FACEPLATE_UP : Line.FACEPLATE_DOWN, crude, now);
		}
	}

	/** Speak {@code line} if its cooldown and the player's gap allow it. The Mark 1 only has the crude lines. */
	private static boolean attempt(ServerPlayer player, Seen seen, Line line, boolean crude, long now) {
		if (crude && line.crudeId == null) {
			return false;
		}
		Long ready = seen.lineReadyAt.get(line);
		if (ready != null && now < ready) {
			return false;
		}
		if (!line.urgent && now - seen.lastSpokenAt < GLOBAL_GAP_TICKS) {
			return false;
		}
		String id = crude ? line.crudeId : line.id;
		seen.lineReadyAt.put(line, now + line.cooldownTicks);
		seen.lastSpokenAt = now;
		seen.lastLine = id;
		seen.spokenCount++;
		send(player, id, crude);
		return true;
	}

	private static void send(ServerPlayer player, String id, boolean crude) {
		if (ServerPlayNetworking.canSend(player, IronManJarvisPayload.TYPE)) {
			ServerPlayNetworking.send(player, new IronManJarvisPayload(id, crude));
		} else {
			player.displayClientMessage(Component.translatable(crude ? "message.projecthero.ironman.jarvis.prefix_system"
					: "message.projecthero.ironman.jarvis.prefix").append(" ")
					.append(Component.translatable("message.projecthero.ironman.jarvis." + id))
					.withStyle(crude ? ChatFormatting.GOLD : ChatFormatting.AQUA), true);
		}
	}

	// ---------------- test / debug access ----------------

	/** The id of the last line spoken to this player (null if none). */
	public static String lastLine(ServerPlayer player) {
		Seen s = SEEN.get(player.getUUID());
		return s == null ? null : s.lastLine;
	}

	/** How many lines this player has heard since JARVIS started watching them. */
	public static int spokenCount(ServerPlayer player) {
		Seen s = SEEN.get(player.getUUID());
		return s == null ? 0 : s.spokenCount;
	}

	public static void clearFor(ServerPlayer player) {
		SEEN.remove(player.getUUID());
	}

	public static void clearSessionState() {
		SEEN.clear();
	}
}
