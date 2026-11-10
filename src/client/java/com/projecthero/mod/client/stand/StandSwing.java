package com.projecthero.mod.client.stand;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.stand.StandClips;
import com.projecthero.mod.stand.Stands;
import com.projecthero.mod.stand.data.StandState;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 (user: "left clicking with the stand doesn't make them punch anymore"). The root cause: Star Platinum's punch only ever ran
 * from the server's {@code AttackEntityCallback}, i.e. only when a left-click connected with a creature. Since the Stand began copying
 * its user from behind (3c2a1cc1) every other left-click -- at the air, at a block, or held down to mine -- just showed the user's own
 * small arm swing copied on the Stand at their back, so to the player the Stand had stopped punching.
 *
 * <p>Now every arm swing of a Stand user with an empty hand (their own left-clicks, and other players' swings, which the game already
 * sends to everyone) plays a Stand jab here, alternating fists, out in front -- including every swing while mining, so it is seen
 * punching the block it breaks ({@code StandMiningMixin}). A real hit still comes from the server (with its damage and its heavy
 * third blow); a jab the server answers within a few ticks keeps playing rather than restarting.
 */
public final class StandSwing {
	/** One jab: when, and with which fist. */
	private record Jab(long at, boolean right) {
	}

	private static final Map<Integer, Jab> JABS = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> LAST_SWING_TIME = new ConcurrentHashMap<>();
	private static final Map<Integer, Boolean> WAS_SWINGING = new ConcurrentHashMap<>();

	private StandSwing() {
	}

	static void reset() {
		JABS.clear();
		LAST_SWING_TIME.clear();
		WAS_SWINGING.clear();
	}

	/** Client tick: spot each new arm swing of a Stand user whose Stand is out. */
	static void tick(ClientLevel level) {
		long now = level.getGameTime();
		for (Player p : level.players()) {
			int id = p.getId();
			int prev = LAST_SWING_TIME.getOrDefault(id, 0);
			boolean was = WAS_SWINGING.getOrDefault(id, false);
			LAST_SWING_TIME.put(id, p.swingTime);
			WAS_SWINGING.put(id, p.swinging);
			boolean fresh = p.swinging && (!was || p.swingTime < prev);
			if (fresh && Stands.summoned(p) && p.getMainHandItem().isEmpty()) {
				Jab last = JABS.get(id);
				JABS.put(id, new Jab(now, last == null || now - last.at > 20 || !last.right));
			}
		}
		if (JABS.size() > 64) {
			JABS.entrySet().removeIf(e -> now - e.getValue().at > 40 || now < e.getValue().at - 40);
		}
	}

	/** The state to draw: the server's, or a copy playing the latest jab when nothing of the server's own is running. */
	static StandState overlay(Player p, StandState s, long now) {
		Jab j = JABS.get(p.getId());
		if (j == null || !p.getMainHandItem().isEmpty()) {
			return s;
		}
		int len = StandClips.length(StandState.ANIM_PUNCH_R);
		long age = now - j.at;
		if (age < 0 || age >= len) {
			return s;
		}
		if (s.rushing || s.guarding || s.chargeStart != 0L || s.grabbedEntity != -1 || s.chaseStart != 0L || s.dashUntil > now || s.inhaleUntil != 0L) {
			return s;
		}
		boolean serverPunch = s.animId == StandState.ANIM_PUNCH_R || s.animId == StandState.ANIM_PUNCH_L;
		float serverAge = now - s.animStart;
		int serverLen = StandClips.length(s.animId);
		boolean serverPlaying = serverLen > 0 && serverAge >= 0 && serverAge < serverLen && s.animId != StandState.ANIM_SUMMON;
		if (serverPlaying && !(serverPunch && Math.abs(s.animStart - j.at) <= 3)) {
			return s; // the server's own move (a heavy blow, a skill) wins
		}
		StandState c = s.copy();
		c.animId = j.right ? StandState.ANIM_PUNCH_R : StandState.ANIM_PUNCH_L;
		c.animStart = j.at;
		return c;
	}
}
