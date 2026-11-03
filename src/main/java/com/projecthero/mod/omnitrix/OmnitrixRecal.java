package com.projecthero.mod.omnitrix;

import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.omnitrix.item.OmnitrixItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.2 (user: "the player can right click the chip and then an option to choose to recalibrate or not will pop up, if the player
 * says no nothing happens but if they say yes then the player recalibrates, an animation starts playing, the dial of the omnitrix
 * pops up and a little slot reveals open up too, the player then slots the recalibration chip into the slots and then the slot
 * closes, the player then looks at the omnitrix watch face as it turns blue, the player then looks at the watch from a few angles
 * turning their wrist a bit to get a better look. The omnitrix then enters recalibration mode for 3 minutes, if the player dies it
 * wont halt anything, the watch should stay recalibrating no matter what. After the 3 minutes the player lifts the omnitrix to look
 * at it, the omnitrix then flashes green and black before slowly reforming into the recalibrated omnitrix and recalibration mode
 * finishes"): the Recalibration Chip's own recalibration (it replaced the Containment Pod's).
 *
 * <p>Three phases, all on the synced, persisted timers (so every client renders the same thing, and death / respawn / relog keep it):
 * <ol>
 *   <li><b>The insert</b> ({@link #INSERT}, its start tick; {@link #INSERT_TOTAL} ticks): the wrist comes up, the dial pops, a slot
 *       opens in the side of the watch, the right hand slots the chip in (it is taken on {@link #CHIP_IN}), the slot closes, the dial
 *       sinks, a press turns the face blue, and he turns his wrist to look at it from a few angles.</li>
 *   <li><b>Recalibration mode</b> ({@link #END}, the absolute game tick it ends): {@link #MODE_TICKS} (3 minutes) from the end of the
 *       insert. The watch cannot be raised or used, the face stays blue, the HUD counts it down. Nothing stops it: death, respawn,
 *       relog -- the end time is fixed when the chip goes in.</li>
 *   <li><b>The finish</b> ({@link #FINISH}, its start tick; {@link #FINISH_TOTAL} ticks): when the time is up (or as soon as he is
 *       alive again after it), he lifts the watch, it flashes green and black and slowly reforms into the Recalibrated Omnitrix
 *       ({@link Omnitrix#recalibrate}).</li>
 * </ol>
 * Beats that do something on the server (sounds, the chip) fire once ({@link #BEATS}, a bit per beat) however often the tick runs.
 * The client half is {@code OmnitrixRecalClient}.
 */
public final class OmnitrixRecal {
	/** The insert's start tick. */
	public static final String INSERT = "omnitrix.recal_insert";
	/** The game tick recalibration mode ends (absolute: set when the chip goes in). */
	public static final String END = "omnitrix.recal_end";
	/** The finish's start tick. */
	public static final String FINISH = "omnitrix.recal_finish";
	/** The running sequence's beats already fired (bits). */
	public static final String BEATS = "omnitrix.recal_beats";

	/** Recalibration mode's length (3 minutes). */
	public static final int MODE_TICKS = OmnitrixConfig.RECAL_MODE_TICKS;

	// ---------------------------------------------------------------- the insert's beats (ticks after Yes)
	/** The wrist is up. */
	public static final int RAISE_T = 10;
	/** The dial pops up (over {@link #POP_T}). */
	public static final int POP_AT = 9, POP_T = 5;
	/** The slot's panel swings open (over {@link #SLOT_T}). Its two clicks: open and latched. */
	public static final int SLOT_OPEN_AT = 15, SLOT_T = 6;
	/** The right hand, the chip between its fingers, comes in to the slot; pushes it home; the chip is in (taken). */
	public static final int HAND_IN_AT = 12, HAND_AT_SLOT = 32, CHIP_IN = 41;
	/** The slot closes; the dial sinks back. */
	public static final int SLOT_CLOSE_AT = 46, DIAL_DOWN_AT = 52, DIAL_DOWN_T = 6;
	/** The fingertip presses the face: the click, and the face turns blue (flickering a moment first). */
	public static final int PRESS_AT = 64, BLUE_AT = 65, BLUE_FLICKER_T = 12;
	/** He turns the wrist to look at it from a few angles; then lowers it. */
	public static final int LOOK_AT = 76, LOWER_AT = 140, INSERT_TOTAL = 156;
	/** The click sound's first click is 0.13 s in, the press sound's click 0.2 s in: started that much before their beats. */
	private static final int CLICK_LEAD = 3, PRESS_LEAD = 4;

	// ---------------------------------------------------------------- the finish's beats (ticks after the time ran out)
	/** The wrist comes up (the pulse sound starts with it). */
	public static final int F_RAISE_T = 12;
	/** The face goes green and the watch starts to glow. */
	public static final int F_GREEN_AT = 36;
	/** The green-and-black flashing (faster and faster). */
	public static final int F_FLASH_AT = 52;
	/** The reform sound (its burst is loudest ~1.4 s in -- the peak). */
	public static final int F_REFORM_SFX = 54;
	/** The old watch melts into the new one; the white-green peak. */
	public static final int F_MORPH_AT = 66, F_MORPH_T = 26, F_PEAK_AT = 82;
	/** The glow fades off the new watch; the wrist lowers; done (recalibrated). */
	public static final int F_FADE_AT = 94, F_LOWER_AT = 104, FINISH_TOTAL = 118;

	// beat bits
	private static final long B_CLICK_OPEN = 1, B_CHIP = 2, B_CLICK_CLOSE = 4, B_PRESS = 8, B_REFORM = 16, B_PULSE = 32;

	/** Set by the client: opens the Yes / No screen for the local player. */
	public static java.util.function.Consumer<Player> openConfirm = p -> {
	};

	private OmnitrixRecal() {
	}

	// ---------------------------------------------------------------- client-safe reads

	/** Any part of it is running (the insert, recalibration mode or the finish): the watch is not his to use. */
	public static boolean busy(OmnitrixState s) {
		return s.hasPower && (s.timers.containsKey(INSERT) || s.timers.containsKey(END) || s.timers.containsKey(FINISH));
	}

	public static boolean busy(Player p) {
		return p != null && Omnitrix.hasPower(p) && busy(Omnitrix.state(p));
	}

	/** Ticks of recalibration mode left (the whole 3 minutes while the insert plays; 0 once the time is up). */
	public static int modeLeft(OmnitrixState s, long now) {
		if (s.timers.containsKey(FINISH)) {
			return 0;
		}
		Long end = s.timers.get(END);
		if (end == null) {
			return s.timers.containsKey(INSERT) ? MODE_TICKS : 0;
		}
		return (int) Math.max(0L, Math.min(MODE_TICKS, end - now));
	}

	/** Ticks into the insert (with {@code partial}), or -1. */
	public static float insertAge(OmnitrixState s, long now, float partial) {
		return age(s, INSERT, INSERT_TOTAL, now, partial);
	}

	/** Ticks into the finish (with {@code partial}), or -1. */
	public static float finishAge(OmnitrixState s, long now, float partial) {
		return age(s, FINISH, FINISH_TOTAL, now, partial);
	}

	private static float age(OmnitrixState s, String key, int total, long now, float partial) {
		Long start = s.timers.get(key);
		if (start == null) {
			return -1f;
		}
		float t = now - start + partial;
		if (t < 0f && t > -40f) {
			t = 0f; // a client clock a little behind the server's
		}
		return t < 0f ? -1f : Math.min(t, total);
	}

	/** Whether the chip has gone in (the mode's end is fixed). */
	public static boolean chipIn(OmnitrixState s) {
		return s.timers.containsKey(END);
	}

	/**
	 * Why {@code p} cannot recalibrate with a chip right now (a message for the action bar), or null if it can. Client-safe: the client
	 * asks it before opening the Yes / No screen, the server again on the item and again on Yes.
	 */
	public static MutableComponent refusal(Player p) {
		String m = "message.projecthero.omnitrix.recal_chip.";
		if (!Omnitrix.hasPower(p)) {
			return Component.translatable(m + "no_watch").withStyle(ChatFormatting.YELLOW);
		}
		OmnitrixState s = Omnitrix.state(p);
		if (s.readyAt(Omnitrix.RECAL) == 1L) {
			return Component.translatable(m + "already").withStyle(ChatFormatting.GRAY);
		}
		if (busy(s)) {
			return Component.translatable(m + "running").withStyle(ChatFormatting.GRAY);
		}
		if (s.transformed()) {
			return Component.translatable(m + "transformed").withStyle(ChatFormatting.YELLOW);
		}
		int done = Omnitrix.prototypeTransforms(p);
		if (done < OmnitrixConfig.RECAL_MIN_TRANSFORMS) {
			return Component.translatable(m + "untrained", done, OmnitrixConfig.RECAL_MIN_TRANSFORMS, OmnitrixConfig.RECAL_MIN_TRANSFORMS - done)
					.withStyle(ChatFormatting.YELLOW);
		}
		if (Omnitrix.extracting(p) || Omnitrix.latching(p) || OmnitrixLongTransform.playing(p)) {
			return Component.translatable(m + "not_now").withStyle(ChatFormatting.GRAY);
		}
		return null;
	}

	/** The refusal shown on {@code p}'s action bar (with the refused buzz). True if there was one. */
	public static boolean tellRefusal(ServerPlayer p) {
		MutableComponent why = refusal(p);
		if (why == null) {
			return false;
		}
		p.displayClientMessage(why, true);
		OmnitrixSounds.notify(p, OmnitrixSounds.DENIED);
		return true;
	}

	/** The "the watch is recalibrating" refusal for the watch's own controls (m:ss left). */
	public static void tellBusy(ServerPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		int left = (modeLeft(s, p.level().getGameTime()) + 19) / 20;
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.recal_chip.busy", String.format("%d:%02d", left / 60, left % 60))
				.withStyle(ChatFormatting.AQUA), true);
		OmnitrixSounds.notify(p, OmnitrixSounds.DENIED);
	}

	// ---------------------------------------------------------------- the server

	/**
	 * Yes on the confirm screen: every check again, a chip still in a hand, then the insert starts. False (and why) if it cannot.
	 */
	public static boolean request(ServerPlayer p) {
		if (tellRefusal(p)) {
			return false;
		}
		if (findChip(p) == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.recal_chip.no_chip").withStyle(ChatFormatting.YELLOW), true);
			return false;
		}
		long now = p.level().getGameTime();
		OmnitrixState n = Omnitrix.state(p).copy();
		n.selecting = false;
		n.selectStart = 0L;
		n.timers.put(INSERT, now);
		n.timers.put(BEATS, 0L);
		n.timers.remove(END);
		n.timers.remove(FINISH);
		Omnitrix.save(p, n);
		return true;
	}

	/** The Recalibration Chip {@code p} holds (main hand first), or one anywhere in the inventory, or null. */
	private static ItemStack findChip(ServerPlayer p) {
		for (InteractionHand h : InteractionHand.values()) {
			ItemStack st = p.getItemInHand(h);
			if (st.is(OmnitrixItems.RECALIBRATION_CHIP)) {
				return st;
			}
		}
		for (ItemStack st : p.getInventory().items) {
			if (st.is(OmnitrixItems.RECALIBRATION_CHIP)) {
				return st;
			}
		}
		return null;
	}

	private static boolean fired(OmnitrixState s, long bit) {
		return (s.readyAt(BEATS) & bit) != 0;
	}

	private static void fire(ServerPlayer p, long bit) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(BEATS, n.readyAt(BEATS) | bit);
		Omnitrix.save(p, n);
	}

	/** Every server tick while {@link #busy} (from {@link Omnitrix#tick}, before anything else of the watch's). */
	static void tick(ServerPlayer p, long now) {
		if (!p.isAlive()) {
			return; // a dead body runs nothing -- the respawned one picks it up
		}
		OmnitrixState s = Omnitrix.state(p);
		if (s.timers.containsKey(INSERT)) {
			tickInsert(p, s, now);
			return;
		}
		if (s.timers.containsKey(FINISH)) {
			tickFinish(p, s, now);
			return;
		}
		Long end = s.timers.get(END);
		if (end != null && now >= end) {
			// the three minutes are up (or were while he was dead / away): the finish, now that he is here and alive
			if (s.transformed()) {
				return; // cannot happen (transforming is refused), but never reform the watch under an alien
			}
			OmnitrixState n = s.copy();
			n.selecting = false;
			n.timers.put(FINISH, now);
			n.timers.put(BEATS, 0L);
			Omnitrix.save(p, n);
			OmnitrixSounds.playOn(p, OmnitrixSounds.RECAL_CHIP_PULSE);
			fire(p, B_PULSE);
		}
	}

	private static void tickInsert(ServerPlayer p, OmnitrixState s, long now) {
		long start = s.readyAt(INSERT);
		long t = now - start;
		if (t < 0 || t > INSERT_TOTAL + 40) {
			endInsert(p, !chipIn(s));
			return;
		}
		if (t >= SLOT_OPEN_AT - CLICK_LEAD && !fired(s, B_CLICK_OPEN)) {
			fire(p, B_CLICK_OPEN);
			OmnitrixSounds.playOn(p, OmnitrixSounds.RECAL_CHIP_CLICK);
		}
		if (t >= CHIP_IN && !fired(Omnitrix.state(p), B_CHIP)) {
			fire(p, B_CHIP);
			ItemStack chip = findChip(p);
			if (chip == null) {
				// the chip is gone (dropped, stored) before it went in: nothing happens
				endInsert(p, true);
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.recal_chip.no_chip").withStyle(ChatFormatting.YELLOW), true);
				return;
			}
			if (!p.getAbilities().instabuild) {
				chip.shrink(1);
			}
			OmnitrixState n = Omnitrix.state(p).copy();
			n.timers.put(END, start + INSERT_TOTAL + MODE_TICKS);
			Omnitrix.save(p, n);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.recal_chip.inserted").withStyle(ChatFormatting.AQUA), true);
		}
		if (t >= SLOT_CLOSE_AT - CLICK_LEAD && !fired(Omnitrix.state(p), B_CLICK_CLOSE)) {
			fire(p, B_CLICK_CLOSE);
			OmnitrixSounds.playOn(p, OmnitrixSounds.RECAL_CHIP_CLICK);
		}
		if (t >= PRESS_AT - PRESS_LEAD && !fired(Omnitrix.state(p), B_PRESS)) {
			fire(p, B_PRESS);
			OmnitrixSounds.playOn(p, OmnitrixSounds.RECAL_CHIP_PRESS);
		}
		if (t >= INSERT_TOTAL) {
			endInsert(p, false);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.recal_chip.mode").withStyle(ChatFormatting.AQUA), true);
		}
	}

	/** The insert is over ({@code cancel}: before the chip went in -- nothing happened). */
	private static void endInsert(ServerPlayer p, boolean cancel) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(INSERT);
		n.timers.remove(BEATS);
		if (cancel) {
			n.timers.remove(END);
		}
		Omnitrix.save(p, n);
	}

	private static void tickFinish(ServerPlayer p, OmnitrixState s, long now) {
		long t = now - s.readyAt(FINISH);
		if (t < 0 || t > FINISH_TOTAL + 40) {
			complete(p);
			return;
		}
		if (!fired(s, B_PULSE)) {
			fire(p, B_PULSE);
			OmnitrixSounds.playOn(p, OmnitrixSounds.RECAL_CHIP_PULSE);
		}
		if (t >= F_REFORM_SFX && !fired(Omnitrix.state(p), B_REFORM)) {
			fire(p, B_REFORM);
			OmnitrixSounds.playOn(p, OmnitrixSounds.RECAL_CHIP_REFORM);
		}
		if (t >= FINISH_TOTAL) {
			complete(p);
		}
	}

	/** The finish is done: the Recalibrated Omnitrix, recalibration mode over. */
	private static void complete(ServerPlayer p) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(INSERT);
		n.timers.remove(END);
		n.timers.remove(FINISH);
		n.timers.remove(BEATS);
		Omnitrix.save(p, n);
		if (Omnitrix.recalibrate(p, true)) {
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(6, 60, 20));
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
					Component.translatable("message.projecthero.omnitrix.recal_subtitle").withStyle(ChatFormatting.GRAY)));
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
					Component.translatable("message.projecthero.omnitrix.recal_title").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.recal_chip.done").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
					false);
		}
	}

	/**
	 * Death / logout: the sequence on screen stops (an insert before the chip went in is called off -- nothing was taken); recalibration
	 * mode and its end time are kept, and a finish cut short plays again from the start once he is back and alive.
	 */
	static void interrupt(ServerPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		if (!busy(s)) {
			return;
		}
		OmnitrixState n = s.copy();
		n.timers.remove(INSERT); // (before the chip went in there is no END: nothing more to undo)
		n.timers.remove(FINISH);
		n.timers.remove(BEATS);
		Omnitrix.save(p, n);
	}

	// ---------------------------------------------------------------- tests and the screenshot harness

	/** Moves the running insert / finish {@code ticks} further on (their start ticks back). */
	public static void skip(ServerPlayer p, int ticks) {
		OmnitrixState n = Omnitrix.state(p).copy();
		for (String k : new String[] { INSERT, FINISH }) {
			if (n.timers.containsKey(k)) {
				n.timers.put(k, n.readyAt(k) - ticks);
			}
		}
		Omnitrix.save(p, n);
	}

	/** Ends recalibration mode now (the 3 minutes skipped): the finish starts on the next tick. */
	public static void skipMode(ServerPlayer p) {
		OmnitrixState n = Omnitrix.state(p).copy();
		if (n.timers.containsKey(END)) {
			n.timers.put(END, p.level().getGameTime());
			Omnitrix.save(p, n);
		}
	}
}
