package com.projecthero.mod.undead;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: NBC Heroes' segmentation controls): the Regen Menu. Shift + hold V opens it (and closes it again); while it is
 * open the ability keys grow or strip his ten segments instead of the moves:
 * <pre>
 *   R  right arm        Shift+R  right hand
 *   G  left arm         Shift+G  left hand
 *   Z  right leg        Shift+Z  right foot
 *   X  left leg         Shift+X  left foot
 *   C  body             Shift+C  head
 *   V  tap: Eject Head  hold 2 s: Regenerate Whole Body    Shift+V: Toggle Regen (auto-regrowth)    Shift + hold V: close
 * </pre>
 * A segment's key, NBC's way ({@link #press}):
 * <ul>
 *   <li>with auto-regrowth OFF (Shift+V), each press grows the segment to its next stage ({@link UndeadConfig#MENU_TARGETS}):
 *       the end of the bone stage, then the end of the flesh stage, then whole -- so a part can be kept at bone on purpose. It
 *       grows from what it grows from (the body from the head ...): until that is far enough along the press is refused;</li>
 *   <li>with auto-regrowth on, a missing segment simply grows straight back;</li>
 *   <li>on a WHOLE segment the press strips it off (a blood burst), with everything that grows from it -- the head is only
 *       stripped back to bone (he can't do without it).</li>
 * </ul>
 */
public final class UndeadRegenMenu {
	public static final String MENU = "regen_menu";
	public static final String WHOLE = "regen_whole";
	public static final String EJECT = "regen_eject";
	/** One id a segment: {@code regen_seg_<index>}. */
	public static final String SEG_PREFIX = "regen_seg_";

	/** A plain V held in the menu: when it went down, 1 once Regenerate Whole Body has fired. */
	private static final Map<UUID, long[]> V_HELD = new ConcurrentHashMap<>();

	private UndeadRegenMenu() {
	}

	static void clearSessionState() {
		V_HELD.clear();
	}

	static void forget(UUID id) {
		V_HELD.remove(id);
	}

	public static String segId(int seg) {
		return SEG_PREFIX + seg;
	}

	/** The menu is open (client-safe). */
	public static boolean open(Player p) {
		UndeadState s = Undead.peek(p);
		return s != null && s.hasPower && s.flag(UndeadState.FLAG_REGEN_MENU);
	}

	public static void toggle(ServerPlayer p) {
		UndeadState n = Undead.state(p).copy();
		boolean open = !n.flag(UndeadState.FLAG_REGEN_MENU);
		n.setFlag(UndeadState.FLAG_REGEN_MENU, open);
		Undead.save(p, n);
		V_HELD.remove(p.getUUID());
		p.level().playSound(null, p.getX(), p.getEyeY(), p.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, open ? 1.2f : 0.8f);
		Undead.say(p, open ? "message.projecthero.undead.menu_open" : "message.projecthero.undead.menu_closed", ChatFormatting.RED);
	}

	/** The segments on the menu's keys: {plain, Shift} for R, G, X, Z, C. */
	static int[] segsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new int[] { UndeadState.RIGHT_ARM, UndeadState.RIGHT_HAND };
			case SLOT_2 -> new int[] { UndeadState.LEFT_ARM, UndeadState.LEFT_HAND };
			case SLOT_4 -> new int[] { UndeadState.RIGHT_LEG, UndeadState.RIGHT_FOOT };
			case SLOT_3 -> new int[] { UndeadState.LEFT_LEG, UndeadState.LEFT_FOOT };
			case SLOT_6 -> new int[] { UndeadState.BODY, UndeadState.HEAD };
			default -> null;
		};
	}

	/**
	 * A key while the menu is open (from {@link UndeadAbilityManager#handle}). V's tap / hold and Shift+V are read here too, except
	 * Shift + hold V (closing the menu), which the manager's Shift+V hold handles.
	 */
	static void handle(ServerPlayer p, AbilitySlot slot, boolean pressed, boolean shift) {
		if (slot == AbilitySlot.SLOT_5) {
			if (pressed && !shift) {
				V_HELD.put(p.getUUID(), new long[] { p.level().getGameTime(), 0L });
			} else if (!pressed) {
				long[] v = V_HELD.remove(p.getUUID());
				if (v != null && v[1] == 0L) {
					eject(p); // a tap
				}
			}
			return;
		}
		int[] segs = segsOf(slot);
		if (pressed && segs != null) {
			press(p, segs[shift ? 1 : 0]);
		}
	}

	/** Every tick: V held in the menu for {@link UndeadConfig#WHOLE_BODY_HOLD} regrows the whole body. */
	static void tick(ServerPlayer p) {
		long[] v = V_HELD.get(p.getUUID());
		if (v == null || v[1] != 0L) {
			return;
		}
		if (p.level().getGameTime() - v[0] >= UndeadConfig.WHOLE_BODY_HOLD) {
			v[1] = 1L;
			whole(p);
		}
	}

	/** A segment's key (see the class comment). */
	public static void press(ServerPlayer p, int seg) {
		if (!Undead.canAct(p)) {
			return;
		}
		UndeadState s = Undead.state(p);
		long now = p.level().getGameTime();
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = UndeadParts.centre(p, UndeadState.limbOf(seg));
		if (!s.broken(seg)) {
			strip(p, seg);
			return;
		}
		if (!s.flag(UndeadState.FLAG_REGEN_OFF)) {
			// auto-regrowth on: a missing segment just grows straight back (one the menu held at a stage grows on)
			long st = s.regrowStart(seg);
			if (st > 0L && s.regrowEnd(seg) - st < UndeadConfig.FRAMES[seg]) {
				UndeadState n = s.copy();
				long from = now - Math.min(now - st, s.regrowEnd(seg) - st);
				n.setTimes(seg, s.brokenAt(seg), from, from + UndeadConfig.FRAMES[seg]);
				Undead.save(p, n);
			}
			return;
		}
		if (!Undead.mayStart(s, seg, now)) {
			Undead.say(p, "message.projecthero.undead.menu_needs", ChatFormatting.GRAY,
					Component.translatable("projecthero.undead.seg." + UndeadState.parentOf(seg)));
			level.playSound(null, c.x, c.y, c.z, SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.4f, 1.6f);
			return;
		}
		int frame = (int) Math.floor(s.frame(seg, now));
		int past = Math.max(frame, s.targetFrame(seg));
		int target = -1;
		for (int t : UndeadConfig.MENU_TARGETS[seg]) {
			if (t > past) {
				target = t;
				break;
			}
		}
		if (target < 0) {
			return; // already growing to whole
		}
		UndeadState n = s.copy();
		long start = now - frame;
		n.setTimes(seg, s.brokenAt(seg), start, start + target);
		Undead.save(p, n);
		if (s.regrowStart(seg) <= 0L) {
			Undead.startedRegrowing(p, seg, s.kind(seg));
		}
		level.sendParticles(Undead.BLOOD, c.x, c.y, c.z, 6, 0.15, 0.2, 0.15, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.6f, 0.8f);
	}

	/** A whole segment's key: it comes off, with everything that grows from it (the head only back to bone). */
	static void strip(ServerPlayer p, int seg) {
		if (seg == UndeadState.HEAD) {
			UndeadState s = Undead.state(p);
			long now = Math.max(1L, p.level().getGameTime());
			UndeadState n = s.copy();
			int bone = UndeadConfig.MENU_TARGETS[UndeadState.HEAD][0];
			n.setTimes(UndeadState.HEAD, now, now - bone, now); // held at the end of its bone stage
			n.setKind(UndeadState.HEAD, UndeadState.KIND_BURST);
			Undead.save(p, n);
			Vec3 c = UndeadParts.centre(p, UndeadState.HEAD);
			((ServerLevel) p.level()).sendParticles(Undead.BLOOD, c.x, c.y, c.z, 12, 0.2, 0.2, 0.2, 0.0);
			UndeadSfx.partBreak(p.level(), c);
			return;
		}
		Undead.breakPart(p, seg, UndeadState.KIND_BURST);
		if (seg == UndeadState.BODY) {
			for (int limb = UndeadState.RIGHT_ARM; limb <= UndeadState.LEFT_LEG; limb++) {
				Undead.breakPart(p, limb, UndeadState.KIND_BURST);
			}
		}
	}

	/** Regenerate Whole Body: everything grows back now, whatever auto-regrowth is set to. */
	public static void whole(ServerPlayer p) {
		if (!Undead.canAct(p)) {
			return;
		}
		Undead.growAll(p);
		long now = p.level().getGameTime();
		UndeadState s = Undead.state(p);
		UndeadState n = s.copy();
		boolean changed = false;
		for (int i = 0; i < UndeadState.SEGS; i++) {
			long st = n.regrowStart(i);
			if (n.broken(i) && st > 0L && n.regrowEnd(i) - st < UndeadConfig.FRAMES[i]) {
				long from = now - Math.min(now - st, n.regrowEnd(i) - st);
				n.setTimes(i, n.brokenAt(i), from, from + UndeadConfig.FRAMES[i]);
				changed = true;
			}
		}
		if (changed) {
			Undead.save(p, n);
		}
		p.level().playSound(null, p.getX(), p.getEyeY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 0.9f);
	}

	/** Eject Head: every segment but the head comes off and the head flies where he looks (the Head Launch). */
	public static void eject(ServerPlayer p) {
		UndeadLaunch.headLaunch(p);
	}

	/** The menu's buttons on a slot (plain, Shift), for the HUD. */
	public static String[] idsOf(AbilitySlot slot) {
		if (slot == AbilitySlot.SLOT_5) {
			return new String[] { EJECT, UndeadAbilities.REGEN };
		}
		int[] segs = segsOf(slot);
		return segs == null ? null : new String[] { segId(segs[0]), segId(segs[1]) };
	}
}
