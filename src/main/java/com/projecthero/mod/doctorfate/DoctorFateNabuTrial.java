package com.projecthero.mod.doctorfate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.doctorfate.network.FateTrialPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;

/**
 * v0.17.4 round 2 (user: "when nabu takes control of you there is no time limit you have to convince him to let you go similar to
 * alien X ... convincing Nabu to let the player go should be difficult and it should feel like the player is trapped in there"): the
 * trial in Nabu's library. While Nabu fights with the body, Kent's mind walks a long hallway into a great round library
 * ({@code client.doctorfate.FateLibrary}, the Hulk / Alien X mind-hall illusion) and must argue his way out, in three stages:
 * <ol>
 *   <li><b>The pages</b>: {@link DoctorFateConfig#TRIAL_LECTERNS} lecterns stand round the library. {@value #NEEDED_DOC} of them hold
 *   a page of Kent Nelson's journal (three of the five arguments, chosen and placed at random each time); every other one holds one of
 *   Nabu's tomes, and reading a tome angers him ({@link DoctorFateConfig#TRIAL_ANGER_TOME}). Nothing tells them apart until read.</li>
 *   <li><b>The challenges</b>: at the Eye of Nabu in the library's heart he puts one challenge per page found. Three answers, one
 *   right (the page's argument), in a shuffled order. Right: he calms a little. <b>Wrong</b>: he's angrier
 *   ({@link DoctorFateConfig#TRIAL_ANGER_WRONG}), tears that page out of your hands and it flies to another lectern: you're back in
 *   stage 1 until you find it again (challenges already won stay won).</li>
 *   <li><b>The will</b>: once every challenge is won, Nabu pushes on your mind. Hold Space and your will rises, let go and he pushes
 *   it down (harder when angry; {@link DoctorFateWill}), and it must stay inside a band that drifts and now and then lunges (with a
 *   half-second warning). Progress fills inside the band and drains at half speed outside; {@link DoctorFateConfig#WILL_NEEDED}
 *   frees you. {@link DoctorFateConfig#WILL_FAIL_OUT} ticks outside in a row, or {@link DoctorFateConfig#WILL_FLOOR_FAIL} on the
 *   floor, fails, angers him and re-opens one challenge (back to stage 2).</li>
 * </ol>
 * <b>His wrath</b>: anger reaching {@link DoctorFateConfig#TRIAL_ANGER_MAX} scatters everything (a new layout, every page lost, every
 * challenge re-opened, back to stage 1) and his body lashes out at once. There is no time limit and no other way out (dying is
 * impossible while possessed; logging out resumes the possession with a fresh trial). Everything is decided here; the client only
 * walks, reads and picks. Static state (UUID keyed) is cleared by {@link DoctorFate#clearSessionState}.
 */
public final class DoctorFateNabuTrial {
	public static final int STAGE_PAGES = 1;
	public static final int STAGE_CHALLENGES = 2;
	public static final int STAGE_WILL = 3;
	/** Kent's journal pages (each one argument, each with its challenge and three answers) and Nabu's tomes. */
	public static final int PAGES = 5;
	public static final int TOMES = 8;
	public static final int TAUNTS = 10;
	private static final int NEEDED_DOC = 3;

	static final class Trial {
		int stage = STAGE_PAGES;
		float anger;
		/** Per lectern: a page id (0..PAGES-1), or -(tome id + 1). */
		final int[] lectern = new int[DoctorFateConfig.TRIAL_LECTERNS];
		/** Per lectern: 0 unread, 1 read. */
		final int[] read = new int[DoctorFateConfig.TRIAL_LECTERNS];
		/** The pages in play this layout, and which of them are found / whose challenge is won. */
		final List<Integer> pages = new ArrayList<>();
		final boolean[] found = new boolean[PAGES];
		final boolean[] won = new boolean[PAGES];
		/** The challenge open on his screen (page id, -1 none) and the answer order shown. */
		int asking = -1;
		final int[] order = { 0, 1, 2 };
		boolean willOn;
		/** The will test (v0.17.4 round 2 rebuild): position and velocity, and whether Space is held (the client says when it changes). */
		float will = 0.5f;
		float vel;
		boolean held;
		/** The band: its centre now, and the eased drift it's on (from, to, tick in it, length). */
		float band = 0.5f;
		float bandFrom = 0.5f;
		float bandTo = 0.5f;
		int bandT;
		int bandDur = 1;
		/** Ticks of the test so far, when his next lunge starts, and the lunge telegraph ticks left (0 none). */
		int willTicks;
		int nextLunge;
		int lungeWarn;
		float progress;
		int outTicks;
		int floorTicks;
		boolean said50;
		boolean said90;
		int wraths;
		long nextTaunt;
		boolean entered;
	}

	private static final Map<UUID, Trial> TRIALS = new ConcurrentHashMap<>();

	private DoctorFateNabuTrial() {
	}

	static void clearSessionState() {
		TRIALS.clear();
	}

	static void forget(UUID id) {
		TRIALS.remove(id);
	}

	// ---------------------------------------------------------------- lifecycle

	/** Nabu has taken the body: a fresh trial, a fresh layout. */
	static void begin(ServerPlayer p) {
		Trial t = new Trial();
		layout(t, p.getRandom());
		t.nextTaunt = p.level().getGameTime() + 200;
		TRIALS.put(p.getUUID(), t);
		title(p, "message.projecthero.doctor_fate.trial.title", "message.projecthero.doctor_fate.trial.subtitle");
		nabu(p, "message.projecthero.doctor_fate.trial.begin");
		sync(p, t);
	}

	/** The possession ended (freed, death, the power taken). */
	static void end(ServerPlayer p) {
		if (TRIALS.remove(p.getUUID()) != null) {
			DoctorFate.send(p, new FateTrialPayload(FateTrialPayload.END, new int[0]));
		}
	}

	static Trial trial(ServerPlayer p) {
		if (!DoctorFate.possessed(p)) {
			return null;
		}
		Trial t = TRIALS.get(p.getUUID());
		if (t == null) {
			begin(p); // a relog or a restart mid-possession: the trial starts over
			t = TRIALS.get(p.getUUID());
		}
		return t;
	}

	/** Three of the five pages, each on its own random lectern; Nabu's tomes on all the rest. */
	static void layout(Trial t, RandomSource r) {
		t.pages.clear();
		List<Integer> all = new ArrayList<>();
		for (int i = 0; i < PAGES; i++) {
			all.add(i);
			t.found[i] = false;
			t.won[i] = false;
		}
		for (int i = 0; i < DoctorFateConfig.TRIAL_PAGES_NEEDED; i++) {
			t.pages.add(all.remove(r.nextInt(all.size())));
		}
		List<Integer> spots = new ArrayList<>();
		for (int i = 0; i < t.lectern.length; i++) {
			spots.add(i);
			t.read[i] = 0;
			t.lectern[i] = -(r.nextInt(TOMES) + 1);
		}
		for (int page : t.pages) {
			t.lectern[spots.remove(r.nextInt(spots.size()))] = page;
		}
		t.stage = STAGE_PAGES;
		t.asking = -1;
		t.willOn = false;
		t.progress = 0;
		t.outTicks = 0;
	}

	// ---------------------------------------------------------------- the player's moves (FateActionPayload)

	/** Read the book on lectern {@code i}. */
	public static void read(ServerPlayer p, int i) {
		Trial t = trial(p);
		if (t == null || i < 0 || i >= t.lectern.length) {
			return;
		}
		int what = t.lectern[i];
		boolean first = t.read[i] == 0;
		t.read[i] = 1;
		if (what >= 0) {
			DoctorFate.send(p, new FateTrialPayload(FateTrialPayload.BOOK, new int[] { i, 1, what }));
			if (!t.found[what]) {
				t.found[what] = true;
				DoctorFate.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.5f);
				if (t.stage == STAGE_PAGES && allFound(t)) {
					t.stage = STAGE_CHALLENGES;
					nabu(p, "message.projecthero.doctor_fate.trial.pages_found");
					verdict(p, FateTrialPayload.V_PAGES_FOUND);
				}
			}
		} else {
			DoctorFate.send(p, new FateTrialPayload(FateTrialPayload.BOOK, new int[] { i, 0, -what - 1 }));
			if (first) {
				nabu(p, "message.projecthero.doctor_fate.trial.tome_read");
				verdict(p, FateTrialPayload.V_TOME);
				anger(p, t, DoctorFateConfig.TRIAL_ANGER_TOME);
			}
		}
		sync(p, t);
	}

	/** At the Eye of Nabu: his next challenge, or the willpower test. */
	public static void confront(ServerPlayer p) {
		Trial t = trial(p);
		if (t == null) {
			return;
		}
		switch (t.stage) {
			case STAGE_PAGES -> {
				nabu(p, "message.projecthero.doctor_fate.trial.empty_handed", foundCount(t), DoctorFateConfig.TRIAL_PAGES_NEEDED);
				verdict(p, FateTrialPayload.V_EMPTY_HANDED);
			}
			case STAGE_CHALLENGES -> ask(p, t);
			default -> {
				if (!t.willOn) {
					startWill(t, p.getRandom());
					nabu(p, "message.projecthero.doctor_fate.trial.will_begins");
					verdict(p, FateTrialPayload.V_WILL_BEGINS);
				}
			}
		}
		sync(p, t);
	}

	private static void ask(ServerPlayer p, Trial t) {
		List<Integer> open = new ArrayList<>();
		for (int page : t.pages) {
			if (t.found[page] && !t.won[page]) {
				open.add(page);
			}
		}
		if (open.isEmpty()) {
			return;
		}
		t.asking = open.get(p.getRandom().nextInt(open.size()));
		shuffle(t.order, p.getRandom());
		DoctorFate.send(p, new FateTrialPayload(FateTrialPayload.QUESTION, new int[] { t.asking, t.order[0], t.order[1], t.order[2] }));
		DoctorFate.sound(p, SoundEvents.ELDER_GUARDIAN_AMBIENT, 0.25f, 0.6f);
	}

	/** His answer: the row {@code shown} (0-2) of the challenge on screen. */
	public static void answer(ServerPlayer p, int shown) {
		Trial t = trial(p);
		if (t == null || t.stage != STAGE_CHALLENGES || t.asking < 0 || shown < 0 || shown > 2) {
			return;
		}
		int page = t.asking;
		t.asking = -1;
		if (t.order[shown] == 0) {
			t.won[page] = true;
			t.anger = Math.max(0f, t.anger - DoctorFateConfig.TRIAL_CALM_RIGHT);
			DoctorFate.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.3f);
			if (allWon(t)) {
				t.stage = STAGE_WILL;
				nabu(p, "message.projecthero.doctor_fate.trial.all_won");
			} else {
				nabu(p, "message.projecthero.doctor_fate.trial.right");
			}
			verdict(p, FateTrialPayload.V_RIGHT);
		} else {
			// wrong: angrier, and that page is torn away to another lectern: back to the pages
			t.found[page] = false;
			movePage(t, page, p.getRandom());
			t.stage = STAGE_PAGES;
			nabu(p, "message.projecthero.doctor_fate.trial.wrong");
			verdict(p, FateTrialPayload.V_WRONG);
			DoctorFate.sound(p, SoundEvents.ELDER_GUARDIAN_CURSE, 0.3f, 0.8f);
			anger(p, t, DoctorFateConfig.TRIAL_ANGER_WRONG);
		}
		sync(p, t);
	}

	/**
	 * Space held down / let go (v0.17.4 round 2 rebuild: the client sends the hold state when it changes; the physics run every tick
	 * in {@link #tick}). True if it counted (only during the willpower test).
	 */
	public static boolean hold(ServerPlayer p, boolean down) {
		Trial t = trial(p);
		if (t == null || !t.willOn) {
			return false;
		}
		t.held = down;
		return true;
	}

	private static void startWill(Trial t, RandomSource r) {
		t.willOn = true;
		t.will = 0.5f;
		t.vel = 0f;
		t.held = false;
		t.band = 0.5f;
		t.bandFrom = 0.5f;
		t.bandTo = 0.5f;
		t.bandT = 0;
		t.bandDur = 30; // a moment to find the band before it starts to drift
		t.willTicks = 0;
		t.nextLunge = DoctorFateConfig.WILL_LUNGE_FIRST + r.nextInt(DoctorFateConfig.WILL_LUNGE_SPREAD);
		t.lungeWarn = 0;
		t.progress = 0f;
		t.outTicks = 0;
		t.floorTicks = 0;
		t.said50 = false;
		t.said90 = false;
	}

	/** The band: slow eased drifts between random heights, and now and then a telegraphed lunge. */
	private static void moveBand(ServerPlayer p, Trial t) {
		RandomSource r = p.getRandom();
		float a = Math.min(1f, t.anger / DoctorFateConfig.TRIAL_ANGER_MAX);
		float lo = DoctorFateConfig.WILL_BAND_MIN;
		float hi = DoctorFateConfig.WILL_BAND_MAX;
		if (t.lungeWarn > 0) {
			// the telegraph: the band holds still and flashes, the Eye flares; then it jumps to the far side
			t.lungeWarn--;
			if (t.lungeWarn == 0) {
				float jump = DoctorFateConfig.WILL_LUNGE_MIN_JUMP;
				t.bandFrom = t.band;
				t.bandTo = t.band - lo > hi - t.band ? lo + r.nextFloat() * Math.max(0f, t.band - jump - lo)
						: t.band + jump + r.nextFloat() * Math.max(0f, hi - t.band - jump);
				t.bandT = 0;
				t.bandDur = DoctorFateConfig.WILL_LUNGE_MOVE;
				t.nextLunge = t.willTicks + Math.round((DoctorFateConfig.WILL_LUNGE_EVERY + r.nextInt(DoctorFateConfig.WILL_LUNGE_SPREAD)) * (1f - 0.4f * a));
				DoctorFate.sound(p, SoundEvents.ELDER_GUARDIAN_AMBIENT, 0.3f, 1.4f);
			}
			return;
		}
		if (t.willTicks >= t.nextLunge) {
			t.lungeWarn = DoctorFateConfig.WILL_LUNGE_WARN;
			t.nextLunge = Integer.MAX_VALUE;
			return;
		}
		t.bandT++;
		if (t.bandT >= t.bandDur) {
			t.band = t.bandTo;
			t.bandFrom = t.band;
			t.bandTo = lo + r.nextFloat() * (hi - lo);
			t.bandT = 0;
			t.bandDur = Math.max(20, Math.round((DoctorFateConfig.WILL_WANDER_TICKS + r.nextInt(DoctorFateConfig.WILL_WANDER_SPREAD)) * (1f - 0.35f * a)));
		}
		t.band = t.bandFrom + (t.bandTo - t.bandFrom) * DoctorFateWill.ease(t.bandT / (float) t.bandDur);
	}

	/** The walker came out of the hallway: the door shuts behind him. */
	public static void entered(ServerPlayer p) {
		Trial t = trial(p);
		if (t == null || t.entered) {
			return;
		}
		t.entered = true;
		nabu(p, "message.projecthero.doctor_fate.trial.door");
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerPlayer p) {
		Trial t = trial(p);
		if (t == null) {
			return;
		}
		long now = p.level().getGameTime();
		if (now >= t.nextTaunt) {
			t.nextTaunt = now + DoctorFateConfig.TRIAL_TAUNT_TICKS + p.getRandom().nextInt(DoctorFateConfig.TRIAL_TAUNT_SPREAD);
			nabu(p, "message.projecthero.doctor_fate.trial.taunt." + p.getRandom().nextInt(TAUNTS));
		}
		if (!t.willOn) {
			if (now % 20 == 0) {
				sync(p, t);
			}
			return;
		}
		t.willTicks++;
		moveBand(p, t);
		// hold Space and the will climbs; let go and Nabu pushes it down (harder when angry): DoctorFateWill, the client predicts the same
		float[] wv = { t.will, t.vel };
		DoctorFateWill.step(wv, t.held, t.anger);
		t.will = wv[0];
		t.vel = wv[1];
		if (Math.abs(t.will - t.band) <= DoctorFateWill.halfBand(t.anger)) {
			t.progress += 1f;
			t.outTicks = 0;
		} else {
			t.progress = Math.max(0f, t.progress - DoctorFateConfig.WILL_OUT_DRAIN); // drains at half speed, never reset
			t.outTicks++;
		}
		t.floorTicks = t.will <= DoctorFateConfig.WILL_FLOOR ? t.floorTicks + 1 : 0;
		if (t.held && now % 4 == 0 && p.level() instanceof net.minecraft.server.level.ServerLevel sl) {
			sl.sendParticles(DoctorFate.dust(DoctorFate.BLUE, 1.0f), p.getX(), p.getY() + 1, p.getZ(), 2, 0.4, 0.6, 0.4, 0.0);
		}
		if (t.progress >= DoctorFateConfig.WILL_NEEDED) {
			free(p);
			return;
		}
		if (!t.said50 && t.progress >= DoctorFateConfig.WILL_NEEDED * 0.5f) {
			t.said50 = true;
			nabu(p, "message.projecthero.doctor_fate.trial.will_half");
		}
		if (!t.said90 && t.progress >= DoctorFateConfig.WILL_NEEDED * 0.9f) {
			t.said90 = true;
			nabu(p, "message.projecthero.doctor_fate.trial.will_almost");
		}
		if (t.outTicks >= DoctorFateConfig.WILL_FAIL_OUT || t.floorTicks >= DoctorFateConfig.WILL_FLOOR_FAIL) {
			willFailed(p, t);
			return;
		}
		syncWill(p, t);
		if (now % 20 == 0) {
			sync(p, t);
		}
	}

	/** Every tick of the will test: the Gauge's numbers (a small packet; the client predicts in between and eases to these). */
	static void syncWill(ServerPlayer p, Trial t) {
		DoctorFate.send(p, new FateTrialPayload(FateTrialPayload.WILL, new int[] { Math.round(t.will * 10000f), Math.round(t.vel * 100000f),
				Math.round(t.band * 10000f), Math.round(DoctorFateWill.halfBand(t.anger) * 10000f), Math.round(t.progress * 10f),
				DoctorFateConfig.WILL_NEEDED * 10, t.outTicks, t.floorTicks, t.lungeWarn, t.held ? 1 : 0 }));
	}

	private static void willFailed(ServerPlayer p, Trial t) {
		t.willOn = false;
		t.held = false;
		t.progress = 0f;
		t.outTicks = 0;
		t.floorTicks = 0;
		// one challenge re-opens: back to stage 2
		List<Integer> won = new ArrayList<>();
		for (int page : t.pages) {
			if (t.won[page]) {
				won.add(page);
			}
		}
		if (!won.isEmpty()) {
			t.won[won.get(p.getRandom().nextInt(won.size()))] = false;
		}
		t.stage = STAGE_CHALLENGES;
		nabu(p, "message.projecthero.doctor_fate.trial.will_failed");
		verdict(p, FateTrialPayload.V_WILL_FAILED);
		DoctorFate.sound(p, SoundEvents.ELDER_GUARDIAN_CURSE, 0.3f, 0.7f);
		anger(p, t, DoctorFateConfig.TRIAL_ANGER_WILL_FAIL);
		sync(p, t);
	}

	/** He convinced Nabu: the Possession ends ({@link DoctorFatePossession#end}). */
	private static void free(ServerPlayer p) {
		title(p, "message.projecthero.doctor_fate.trial.freed_title", "message.projecthero.doctor_fate.trial.freed_subtitle");
		nabu(p, "message.projecthero.doctor_fate.trial.freed");
		DoctorFatePossession.end(p);
	}

	private static void anger(ServerPlayer p, Trial t, float amount) {
		t.anger = Math.min(DoctorFateConfig.TRIAL_ANGER_MAX, t.anger + amount);
		if (t.anger >= DoctorFateConfig.TRIAL_ANGER_MAX) {
			wrath(p, t);
		}
	}

	/** Anger at 100: everything scattered and every challenge re-opened, back to the pages; the body lashes out. */
	private static void wrath(ServerPlayer p, Trial t) {
		layout(t, p.getRandom());
		t.anger = DoctorFateConfig.TRIAL_ANGER_AFTER_WRATH;
		t.wraths++;
		title(p, "message.projecthero.doctor_fate.trial.wrath_title", "message.projecthero.doctor_fate.trial.wrath_subtitle");
		nabu(p, "message.projecthero.doctor_fate.trial.wrath");
		verdict(p, FateTrialPayload.V_WRATH);
		DoctorFate.sound(p, SoundEvents.WARDEN_ROAR, 0.35f, 1.6f);
		DoctorFatePossession.lashOut(p);
	}

	// ---------------------------------------------------------------- helpers

	private static void movePage(Trial t, int page, RandomSource r) {
		int from = -1;
		List<Integer> unread = new ArrayList<>();
		List<Integer> tomes = new ArrayList<>();
		for (int i = 0; i < t.lectern.length; i++) {
			if (t.lectern[i] == page) {
				from = i;
			} else if (t.lectern[i] < 0) {
				tomes.add(i);
				if (t.read[i] == 0) {
					unread.add(i);
				}
			}
		}
		List<Integer> pool = unread.isEmpty() ? tomes : unread;
		if (from < 0 || pool.isEmpty()) {
			return;
		}
		int to = pool.get(r.nextInt(pool.size()));
		t.lectern[from] = -(r.nextInt(TOMES) + 1);
		t.read[from] = 1; // he's read that lectern: it stays dim (a tome now)
		t.lectern[to] = page;
		t.read[to] = 0; // the page's new home glows again
	}

	static boolean allFound(Trial t) {
		for (int page : t.pages) {
			if (!t.found[page]) {
				return false;
			}
		}
		return true;
	}

	static boolean allWon(Trial t) {
		for (int page : t.pages) {
			if (!t.won[page]) {
				return false;
			}
		}
		return true;
	}

	static int foundCount(Trial t) {
		int n = 0;
		for (int page : t.pages) {
			if (t.found[page]) {
				n++;
			}
		}
		return n;
	}

	private static void shuffle(int[] a, RandomSource r) {
		for (int i = a.length - 1; i > 0; i--) {
			int j = r.nextInt(i + 1);
			int x = a[i];
			a[i] = a[j];
			a[j] = x;
		}
	}

	static void sync(ServerPlayer p, Trial t) {
		int n = t.lectern.length;
		int[] d = new int[11 + n];
		d[0] = t.stage;
		d[1] = Math.round(t.anger);
		d[2] = foundCount(t);
		d[3] = DoctorFateConfig.TRIAL_PAGES_NEEDED;
		d[4] = t.wraths;
		d[5] = t.willOn ? 1 : 0;
		d[6] = Math.round(t.will * 1000f);
		d[7] = Math.round(t.band * 1000f);
		d[8] = Math.round(t.progress);
		d[9] = DoctorFateConfig.WILL_NEEDED;
		d[10] = t.outTicks;
		for (int i = 0; i < n; i++) {
			d[11 + i] = t.read[i] == 0 ? 0 : t.lectern[i] >= 0 && t.found[t.lectern[i]] ? 2 : 1;
		}
		DoctorFate.send(p, new FateTrialPayload(FateTrialPayload.STATE, d));
	}

	private static void verdict(ServerPlayer p, int code) {
		DoctorFate.send(p, new FateTrialPayload(FateTrialPayload.VERDICT, new int[] { code }));
	}

	/** Nabu speaks: a gold line in chat. */
	static void nabu(ServerPlayer p, String key, Object... args) {
		p.displayClientMessage(Component.translatable("message.projecthero.doctor_fate.trial.nabu_tag").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				.append(Component.translatable(key, args).withStyle(ChatFormatting.YELLOW)), false);
	}

	private static void title(ServerPlayer p, String title, String subtitle) {
		if (p.connection == null) {
			return;
		}
		p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
		p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(subtitle).withStyle(ChatFormatting.YELLOW)));
	}

	// ---------------------------------------------------------------- tests

	public static int stage(ServerPlayer p) {
		Trial t = TRIALS.get(p.getUUID());
		return t == null ? 0 : t.stage;
	}

	public static float angerOf(ServerPlayer p) {
		Trial t = TRIALS.get(p.getUUID());
		return t == null ? 0f : t.anger;
	}

	public static int pagesFound(ServerPlayer p) {
		Trial t = TRIALS.get(p.getUUID());
		return t == null ? 0 : foundCount(t);
	}

	public static int wraths(ServerPlayer p) {
		Trial t = TRIALS.get(p.getUUID());
		return t == null ? 0 : t.wraths;
	}

	/** Tests: the lecterns holding Kent's pages (in play) and those holding Nabu's tomes. */
	public static List<Integer> lecterns(ServerPlayer p, boolean pages) {
		Trial t = TRIALS.get(p.getUUID());
		List<Integer> out = new ArrayList<>();
		if (t != null) {
			for (int i = 0; i < t.lectern.length; i++) {
				if ((t.lectern[i] >= 0) == pages) {
					out.add(i);
				}
			}
		}
		return out;
	}

	/** Tests: the row (as shown) holding the right / a wrong answer to the open challenge (-1 none open). */
	public static int row(ServerPlayer p, boolean right) {
		Trial t = TRIALS.get(p.getUUID());
		if (t == null || t.asking < 0) {
			return -1;
		}
		for (int i = 0; i < 3; i++) {
			if ((t.order[i] == 0) == right) {
				return i;
			}
		}
		return -1;
	}

	/** Tests: put the will on the band's centre, at rest, for this tick (as a player holding it perfectly would). */
	public static void holdWillForTest(ServerPlayer p) {
		Trial t = TRIALS.get(p.getUUID());
		if (t != null && t.willOn) {
			t.will = t.band;
			t.vel = 0f;
		}
	}

	/** Tests: let go and drop the will to the floor (nobody pressing), with the band high above it. */
	public static void dropWillForTest(ServerPlayer p) {
		Trial t = TRIALS.get(p.getUUID());
		if (t != null && t.willOn) {
			t.held = false;
			t.will = 0f;
			t.vel = 0f;
			pinBandForTest(p, DoctorFateConfig.WILL_BAND_MAX);
		}
	}

	/** Tests: the will at this height, at rest. */
	public static void placeWillForTest(ServerPlayer p, float will) {
		Trial t = TRIALS.get(p.getUUID());
		if (t != null && t.willOn) {
			t.will = will;
			t.vel = 0f;
		}
	}

	/** Tests: hold the band still at this height (no drift, no lunge). */
	public static void pinBandForTest(ServerPlayer p, float band) {
		Trial t = TRIALS.get(p.getUUID());
		if (t != null && t.willOn) {
			t.band = band;
			t.bandFrom = band;
			t.bandTo = band;
			t.bandT = 0;
			t.bandDur = Integer.MAX_VALUE / 2;
			t.lungeWarn = 0;
			t.nextLunge = Integer.MAX_VALUE;
		}
	}

	public static boolean willOn(ServerPlayer p) {
		Trial t = TRIALS.get(p.getUUID());
		return t != null && t.willOn;
	}

	public static float willProgress(ServerPlayer p) {
		Trial t = TRIALS.get(p.getUUID());
		return t == null ? 0f : t.progress;
	}

	/** Tests: the will's height, velocity and the band's centre. */
	public static float willOf(ServerPlayer p) {
		Trial t = TRIALS.get(p.getUUID());
		return t == null ? 0f : t.will;
	}

	public static float willVelocity(ServerPlayer p) {
		Trial t = TRIALS.get(p.getUUID());
		return t == null ? 0f : t.vel;
	}

	public static float bandOf(ServerPlayer p) {
		Trial t = TRIALS.get(p.getUUID());
		return t == null ? 0f : t.band;
	}

	public static int outTicks(ServerPlayer p) {
		Trial t = TRIALS.get(p.getUUID());
		return t == null ? 0 : t.outTicks;
	}

	public static void tickForTest(ServerPlayer p) {
		tick(p);
	}
}
