package com.projecthero.mod.sentry;

import com.projecthero.mod.sentry.data.SentryState;
import com.projecthero.mod.sentry.network.ShameRoomPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.5: the server side of the Shame Rooms, the mind screen of a takeover. The client plays a room (clicking drifting lights to
 * fill a Light bar while darkness shapes chase the cursor and drain it) and reports how it went ({@link ShameRoomPayload}); this is the
 * authority:
 * <ul>
 *   <li>Only during a takeover, only once the mind is open (30 s in, or after a failed room's 20 s), only for the room he is in.</li>
 *   <li>A win counts only if the room ran at least {@link SentryConfig#ROOM_MIN_TICKS} on the server's clock and at least five lights
 *   were caught (a loose check: nobody honest wins faster). Three wins throw the Void out.</li>
 *   <li>A loss sends him back a room and shuts the mind for 20 s more.</li>
 * </ul>
 */
public final class ShameRooms {
	/** What a report did. */
	public enum Result {
		IGNORED, ADVANCED, WON, FAILED, TOO_FAST
	}

	private ShameRooms() {
	}

	/** Whether the mind screen is open for him right now (client-safe: it reads the synced state). */
	public static boolean mindOpen(SentryState s, long now) {
		return s != null && s.inTakeover() && now >= s.mindLockedUntil && s.room < SentryConfig.ROOMS;
	}

	public static Result report(ServerPlayer p, ShameRoomPayload r) {
		SentryState s = Sentry.state(p);
		long now = p.level().getGameTime();
		if (!mindOpen(s, now) || r.room() != s.room) {
			return Result.IGNORED;
		}
		if (!r.won()) {
			SentryState n = s.copy();
			n.room = Math.max(0, s.room - 1);
			n.mindLockedUntil = now + SentryConfig.ROOM_FAIL_TICKS;
			n.roomStartedAt = n.mindLockedUntil;
			Sentry.save(p, n);
			Sentry.say(p, "message.projecthero.sentry.room_failed", ChatFormatting.DARK_RED);
			return Result.FAILED;
		}
		long started = Math.max(s.roomStartedAt, s.mindLockedUntil);
		if (now - started < SentryConfig.ROOM_MIN_TICKS || r.clicks() < 5) {
			return Result.TOO_FAST;
		}
		SentryState n = s.copy();
		n.room = s.room + 1;
		n.roomStartedAt = now;
		Sentry.save(p, n);
		if (n.room >= SentryConfig.ROOMS) {
			VoidTakeover.end(p, VoidTakeover.End.WON);
			return Result.WON;
		}
		Sentry.say(p, "message.projecthero.sentry.room_cleared", ChatFormatting.GOLD, n.room, SentryConfig.ROOMS);
		return Result.ADVANCED;
	}
}
