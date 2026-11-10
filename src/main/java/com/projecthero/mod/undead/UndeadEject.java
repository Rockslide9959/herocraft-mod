package com.projecthero.mod.undead;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadPartEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user): the shared ejection every blood move uses. Undead blows his own ends off: the hands with half the forearm,
 * the feet with half the calf ({@link #eject}). Each end is its NBC segment ({@link UndeadState#RIGHT_HAND} ...) broken as
 * {@link UndeadState#KIND_LAUNCHED}, with its bit in {@link UndeadState#X_EJECTED} set so every client draws the limb cut at
 * {@link UndeadEjectConfig#EJECT_ROWS} (not at the wrist) and grows it back from there with the hand / foot. The piece that came
 * off is left where it came off ({@link #leaveBehind}: the ONE place a left-behind piece is spawned), and blood sprays from the
 * stumps (client-side, from the bit turning on: {@code UndeadClient.watchCuts}).
 *
 * <p>While a move is running it {@link #hold}s the regrowth (nothing starts growing back: {@link UndeadLaunch#flying}, read by
 * {@code Undead.tick}); once the last hold is {@link #release}d the normal regrowth takes the ends back. Static state: dropped
 * by {@link #clearSessionState} / {@link #forget} (from {@link UndeadAbilityManager}).
 */
public final class UndeadEject {
	/** What to eject: both hands, both feet, or just the hand of the arm a one-arm move uses. */
	public static final int HANDS = 1, FEET = 2, ONE_HAND = 4;

	/** The moves that keep the ends off while they run. */
	public enum Hold {
		FLIGHT, SPRAY, WHIRL, FEET, COMBUSTION, DEADLINE
	}

	private static final Map<UUID, EnumSet<Hold>> HOLDS = new ConcurrentHashMap<>();

	private UndeadEject() {
	}

	static void clearSessionState() {
		HOLDS.clear();
	}

	static void forget(UUID id) {
		HOLDS.remove(id);
	}

	// ---------------------------------------------------------------- holding the regrowth

	public static void hold(ServerPlayer p, Hold h) {
		HOLDS.computeIfAbsent(p.getUUID(), k -> EnumSet.noneOf(Hold.class)).add(h);
	}

	public static void release(ServerPlayer p, Hold h) {
		EnumSet<Hold> set = HOLDS.get(p.getUUID());
		if (set != null) {
			set.remove(h);
			if (set.isEmpty()) {
				HOLDS.remove(p.getUUID());
			}
		}
	}

	/** Some ejection move is running (the regrowth waits). */
	public static boolean holding(ServerPlayer p) {
		EnumSet<Hold> set = HOLDS.get(p.getUUID());
		return set != null && !set.isEmpty();
	}

	public static boolean holding(ServerPlayer p, Hold h) {
		EnumSet<Hold> set = HOLDS.get(p.getUUID());
		return set != null && set.contains(h);
	}

	// ---------------------------------------------------------------- the ejection

	/** The segment came off with half its limb and is not growing back yet (client-safe). */
	public static boolean ejected(UndeadState s, int seg) {
		return s != null && (s.ex(UndeadState.X_EJECTED) & (1L << seg)) != 0L && s.broken(seg) && s.regrowStart(seg) <= 0L;
	}

	/** The hand that goes for a one-arm move: the main arm's, else the other's; -1 with no arm there. */
	public static int oneHand(ServerPlayer p) {
		UndeadState s = Undead.state(p);
		int main = Undead.mainArm(p);
		int arm = !s.broken(main) ? main : !s.broken(Undead.otherArm(main)) ? Undead.otherArm(main) : -1;
		return arm < 0 ? -1 : UndeadState.extremity(arm);
	}

	/**
	 * Blows off {@code what} ({@link #HANDS} / {@link #FEET} / {@link #ONE_HAND}, or'ed): every end whose limb is there comes off
	 * with half the limb, the piece left at {@code at}'s side of him (with {@code pieces}). An end already off from another move
	 * stays off. Returns how many ends are off now (0: no limb to eject from).
	 */
	public static int eject(ServerPlayer p, int what, boolean pieces) {
		UndeadState s = Undead.state(p);
		if (!s.hasPower) {
			return 0;
		}
		int[] segs = new int[4];
		int n = 0;
		if ((what & HANDS) != 0) {
			segs[n++] = UndeadState.RIGHT_HAND;
			segs[n++] = UndeadState.LEFT_HAND;
		} else if ((what & ONE_HAND) != 0) {
			int h = oneHand(p);
			if (h >= 0) {
				segs[n++] = h;
			}
		}
		if ((what & FEET) != 0) {
			segs[n++] = UndeadState.RIGHT_FOOT;
			segs[n++] = UndeadState.LEFT_FOOT;
		}
		int off = 0;
		long now = Math.max(1L, p.level().getGameTime());
		for (int k = 0; k < n; k++) {
			int seg = segs[k];
			UndeadState cur = Undead.state(p);
			if (cur.broken(UndeadState.limbOf(seg))) {
				continue; // no arm / leg to eject it from
			}
			off++;
			if (ejected(cur, seg)) {
				continue; // already off (another move): it stays off
			}
			boolean wasGone = cur.broken(seg) && cur.regrowStart(seg) <= 0L;
			UndeadState m = cur.copy();
			m.setEx(UndeadState.X_EJECTED, m.ex(UndeadState.X_EJECTED) | (1L << seg));
			Undead.save(p, m);
			Undead.breakPart(p, seg, UndeadState.KIND_LAUNCHED);
			if (!wasGone && pieces) {
				leaveBehind(p, seg, UndeadPartEntity.STYLE_EJECT_END, endPos(p, seg), p.yBodyRot, kick(p, seg));
			}
		}
		if (off > 0) {
			ServerLevel level = (ServerLevel) p.level();
			Vec3 c = p.position().add(0, 0.8, 0);
			UndeadSfx.bloodBurst(level, c, 0.35f);
			UndeadSfx.partBreak(level, c);
		}
		return off;
	}

	/** Combustion Punch: the torso cut to its top third (the rest of it left behind with the legs and the other arm). */
	static void cutTorso(ServerPlayer p, boolean on) {
		UndeadState n = Undead.state(p).copy();
		long bit = 1L << UndeadState.BODY;
		n.setEx(UndeadState.X_EJECTED, on ? n.ex(UndeadState.X_EJECTED) | bit : n.ex(UndeadState.X_EJECTED) & ~bit);
		Undead.save(p, n);
	}

	/**
	 * THE one place a piece Undead ejected is spawned and left behind ({@code style} an {@link UndeadPartEntity} style; for a
	 * hand / foot {@link UndeadPartEntity#STYLE_EJECT_END}, the Combustion Punch's lower torso
	 * {@link UndeadPartEntity#STYLE_EJECT_TORSO}, whole legs / arm {@link UndeadPartEntity#STYLE_LEFT_BODY}). {@code at}: the
	 * piece's middle for an end, his feet for the standing styles. (Merge note: switch this to the shared left-behind helper once
	 * the regen rework's lifecycle lands; the eject styles carry their own short life meanwhile.)
	 */
	public static UndeadPartEntity leaveBehind(ServerPlayer p, int seg, int style, Vec3 at, float yaw, Vec3 velocity) {
		// TODO(merge, regen rework): switch to UndeadPartEntity.leaveBehind(owner, part, fromRow, toRow, at) -- at = p.position():
		//   STYLE_EJECT_END   -> leaveBehind(p, UndeadState.limbOf(seg), UndeadEjectConfig.EJECT_ROWS, 12f, p.position())
		//   STYLE_EJECT_TORSO -> leaveBehind(p, UndeadState.BODY, UndeadEjectConfig.COMBUSTION_BODY_ROWS, 12f, p.position())
		//   STYLE_LEFT_BODY / _LEGLESS (whole parts) -> leaveBehind(p, seg, 0f, 12f, p.position())
		// then the STYLE_EJECT_* styles, UndeadEjectRender and their branch in UndeadPartRenderer can go.
		return UndeadPartEntity.drop(p, seg, style, at, yaw, velocity);
	}

	/** Where an end's piece (the hand / foot and half the forearm / calf) is on his standing body: its middle. */
	static Vec3 endPos(ServerPlayer p, int seg) {
		Vec3 r = UndeadParts.right(p);
		boolean right = seg == UndeadState.RIGHT_HAND || seg == UndeadState.RIGHT_FOOT;
		boolean hand = seg == UndeadState.RIGHT_HAND || seg == UndeadState.LEFT_HAND;
		double side = (right ? 1 : -1) * (hand ? 0.35 : 0.12);
		double y = hand ? 0.73 : 0.15;
		if (p.getBbHeight() < 1.0f) {
			y *= p.getBbHeight() / 1.8; // a short box (a crawl): about where his ends are
		}
		return p.position().add(r.scale(side)).add(0, y, 0);
	}

	/** The little kick a piece leaves with: out to its side and down, none of his own speed (it stays where it came off). */
	private static Vec3 kick(ServerPlayer p, int seg) {
		Vec3 r = UndeadParts.right(p);
		boolean right = seg == UndeadState.RIGHT_HAND || seg == UndeadState.RIGHT_FOOT;
		return r.scale(right ? 0.06 : -0.06).add(0, 0.04, 0);
	}

	// ---------------------------------------------------------------- tick

	/** From {@link UndeadAbilityManager#tickHold}: a bit whose segment is whole again is forgotten. */
	static void tick(ServerPlayer p) {
		UndeadState s = Undead.state(p);
		long bits = s.ex(UndeadState.X_EJECTED);
		if (bits == 0L) {
			return;
		}
		long keep = bits;
		for (int seg = UndeadState.RIGHT_HAND; seg <= UndeadState.LEFT_FOOT; seg++) {
			if ((bits & (1L << seg)) != 0L && !s.broken(seg)) {
				keep &= ~(1L << seg);
			}
		}
		if ((bits & (1L << UndeadState.BODY)) != 0L && !holding(p, Hold.COMBUSTION)) {
			keep &= ~(1L << UndeadState.BODY); // (a relog / a shatter mid-punch: the cut torso is not left hanging)
		}
		if (keep != bits) {
			UndeadState n = s.copy();
			n.setEx(UndeadState.X_EJECTED, keep);
			Undead.save(p, n);
		}
	}

	/** Client-safe: any end is off with half its limb right now. */
	public static boolean anyEjected(Player p) {
		UndeadState s = Undead.peek(p);
		return s != null && s.hasPower && s.ex(UndeadState.X_EJECTED) != 0L;
	}
}
