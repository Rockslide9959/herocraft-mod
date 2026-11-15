package com.projecthero.mod.omnitrix;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixSecrets;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.5 (user: "change how master control is gained, it should be a random 6 number combination, add a way for the player to
 * slowly discover random numbers for the master control please like how its done in alien evo"): every player's codes and what
 * he has found of them.
 *
 * <ul>
 *   <li><b>The secrets</b> ({@link OmnitrixSecrets}, attachment {@code omnitrix_secrets}, never synced): his own random six-digit
 *       Master Control code and one random code per hidden alien, rolled the first time anything asks ({@link #secrets}) -- all
 *       different from each other and from {@link OmnitrixConfig#SELF_DESTRUCT_CODE}.</li>
 *   <li><b>What he knows</b> (the synced, persisted {@link OmnitrixState} timers, all under {@link #PREFIX} so
 *       {@code Omnitrix.keepPlayerData} carries them when the watch comes off): {@code omnitrix.code.d<i>} = the digit in place i
 *       (0-5), {@code omnitrix.code.a.<alien>} = a hidden alien's whole code. The client sees nothing else.</li>
 *   <li><b>Finding them:</b> the watch's flicker ({@link #onTransform}, {@link #onTransformedMinute}: a small chance, a 20-minute
 *       cooldown) and the Omnitrix Data Fragment ({@link #useFragment}).</li>
 * </ul>
 */
public final class OmnitrixCodes {
	/** Every timer key this class owns starts with this (player data, kept across taking the watch off). */
	public static final String PREFIX = "omnitrix.code.";
	/** The last flicker that showed something (game time), for the cooldown. */
	public static final String REVEAL_AT = PREFIX + "reveal_at";
	/** The last reveal of any kind (game time): the client flashes the face and the HUD. */
	public static final String FLASH_AT = PREFIX + "flash_at";

	private OmnitrixCodes() {
	}

	public static String digitKey(int i) {
		return PREFIX + "d" + i;
	}

	public static String alienKey(String id) {
		return PREFIX + "a." + id;
	}

	// ---------------------------------------------------------------- the secrets (server only)

	/** The hidden aliens a code can unlock: every one a mistransform can find (Way Big stays the To'kustar Shard's). */
	public static List<Aliens.Alien> codeable() {
		return Aliens.mistransformFinds();
	}

	/** {@code p}'s codes, rolled now if they were not yet (and a code added for any hidden alien new since). */
	public static OmnitrixSecrets secrets(ServerPlayer p) {
		OmnitrixSecrets s = p.getAttachedOrElse(OmnitrixAttachments.SECRETS, OmnitrixSecrets.EMPTY);
		OmnitrixSecrets n = s;
		RandomSource r = p.getRandom();
		if (!OmnitrixCodeEntry.valid(n.master())) {
			n = n.withMaster(roll(r, n));
		}
		for (Aliens.Alien a : codeable()) {
			if (!OmnitrixCodeEntry.valid(n.aliens().get(a.id()))) {
				n = n.withAlien(a.id(), roll(r, n));
			}
		}
		if (n != s) {
			p.setAttached(OmnitrixAttachments.SECRETS, n);
		}
		return n;
	}

	/** Gametests: sets {@code p}'s Master Control code. */
	public static void setMasterCode(ServerPlayer p, String code) {
		p.setAttached(OmnitrixAttachments.SECRETS, secrets(p).withMaster(code));
	}

	/** A fresh code that clashes with nothing {@code s} already holds (nor the self-destruct code). */
	private static String roll(RandomSource r, OmnitrixSecrets s) {
		while (true) {
			StringBuilder b = new StringBuilder();
			for (int i = 0; i < OmnitrixConfig.CODE_LENGTH; i++) {
				b.append((char) ('0' + r.nextInt(10)));
			}
			String c = b.toString();
			if (!c.equals(OmnitrixConfig.SELF_DESTRUCT_CODE) && !c.equals(s.master()) && !s.aliens().containsValue(c)) {
				return c;
			}
		}
	}

	// ---------------------------------------------------------------- what he knows (client-safe: the synced state)

	/** The digit {@code p} has found in place {@code i} (0-5), or -1. */
	public static int knownDigit(Player p, int i) {
		if (p == null || p.getAttached(OmnitrixAttachments.STATE) == null) {
			return -1;
		}
		Long v = Omnitrix.state(p).timers.get(digitKey(i));
		return v == null ? -1 : (int) (long) v;
	}

	/** How many of the six Master Control digits {@code p} has found. */
	public static int knownCount(Player p) {
		int n = 0;
		for (int i = 0; i < OmnitrixConfig.CODE_LENGTH; i++) {
			if (knownDigit(p, i) >= 0) {
				n++;
			}
		}
		return n;
	}

	/** The known digits as a mask, unknown ones as {@code blank}: "_ 7 _ _ 3 _" with {@code sep} = " ". */
	public static String mask(Player p, String blank, String sep) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < OmnitrixConfig.CODE_LENGTH; i++) {
			if (i > 0) {
				b.append(sep);
			}
			int d = knownDigit(p, i);
			b.append(d < 0 ? blank : String.valueOf(d));
		}
		return b.toString();
	}

	/** Every hidden-alien code {@code p} has found: alien id -> the code, in dial order. */
	public static Map<String, String> knownAlienCodes(Player p) {
		Map<String, String> out = new LinkedHashMap<>();
		if (p == null || p.getAttached(OmnitrixAttachments.STATE) == null) {
			return out;
		}
		OmnitrixState s = Omnitrix.state(p);
		for (Aliens.Alien a : codeable()) {
			Long v = s.timers.get(alienKey(a.id()));
			if (v != null) {
				out.put(a.id(), String.format(java.util.Locale.ROOT, "%06d", v));
			}
		}
		return out;
	}

	/** Whether {@code p} knows anything at all (a digit or an alien code): the Guidebook's Master Control page shows from then on. */
	public static boolean anythingKnown(Player p) {
		return knownCount(p) > 0 || !knownAlienCodes(p).isEmpty();
	}

	// ---------------------------------------------------------------- learning

	/** What a reveal showed. */
	public enum Kind {
		NONE, DIGIT, ALIEN
	}

	/** @param index the digit's place (DIGIT), @param value the digit (DIGIT), @param alien the alien id (ALIEN), @param code its code */
	public record Reveal(Kind kind, int index, int value, String alien, String code) {
		static final Reveal NONE = new Reveal(Kind.NONE, -1, -1, "", "");
	}

	/** Marks digit {@code i} of {@code p}'s Master Control code known. */
	public static void learnDigit(ServerPlayer p, int i) {
		String code = secrets(p).master();
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(digitKey(i), (long) (code.charAt(i) - '0'));
		Omnitrix.save(p, n);
	}

	/** Marks all six known (he entered the code: he knows it). */
	public static void learnAll(ServerPlayer p) {
		String code = secrets(p).master();
		OmnitrixState n = Omnitrix.state(p).copy();
		for (int i = 0; i < OmnitrixConfig.CODE_LENGTH; i++) {
			n.timers.put(digitKey(i), (long) (code.charAt(i) - '0'));
		}
		Omnitrix.save(p, n);
	}

	/** Marks hidden alien {@code id}'s code known. */
	public static void learnAlien(ServerPlayer p, String id) {
		String code = secrets(p).aliens().get(id);
		if (code == null) {
			return;
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(alienKey(id), Long.parseLong(code));
		Omnitrix.save(p, n);
	}

	/** Gametests / the tune command: forgets every digit and alien code (the secrets themselves stay). */
	public static void forgetAll(ServerPlayer p) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.keySet().removeIf(k -> k.startsWith(PREFIX));
		Omnitrix.save(p, n);
	}

	/** The places of the digits {@code p} has not found yet. */
	private static List<Integer> unknownDigits(Player p) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < OmnitrixConfig.CODE_LENGTH; i++) {
			if (knownDigit(p, i) < 0) {
				out.add(i);
			}
		}
		return out;
	}

	/** The hidden aliens whose code {@code p} has not found and that are not on his dial yet. */
	private static List<String> unknownAliens(ServerPlayer p) {
		List<String> out = new ArrayList<>();
		OmnitrixState s = Omnitrix.state(p);
		for (Aliens.Alien a : codeable()) {
			if (!s.timers.containsKey(alienKey(a.id())) && !Aliens.found(p, a.id())) {
				out.add(a.id());
			}
		}
		return out;
	}

	/** Reveals one random unknown digit (and its place), or NONE when all six are known. */
	public static Reveal revealDigit(ServerPlayer p) {
		List<Integer> left = unknownDigits(p);
		if (left.isEmpty()) {
			return Reveal.NONE;
		}
		int i = left.get(p.getRandom().nextInt(left.size()));
		learnDigit(p, i);
		return new Reveal(Kind.DIGIT, i, knownDigit(p, i), "", "");
	}

	/** Reveals one random unknown hidden-alien code (whole), or NONE when there is none left to find. */
	public static Reveal revealAlien(ServerPlayer p) {
		List<String> left = unknownAliens(p);
		if (left.isEmpty()) {
			return Reveal.NONE;
		}
		String id = left.get(p.getRandom().nextInt(left.size()));
		learnAlien(p, id);
		return new Reveal(Kind.ALIEN, -1, -1, id, secrets(p).aliens().get(id));
	}

	// ---------------------------------------------------------------- the watch's flicker

	/** A transformation landed: maybe the flicker. */
	public static Reveal onTransform(ServerPlayer p) {
		return flicker(p, OmnitrixConfig.REVEAL_TRANSFORM_CHANCE);
	}

	/** Another full minute as an alien: maybe the flicker. */
	public static Reveal onTransformedMinute(ServerPlayer p) {
		return flicker(p, OmnitrixConfig.REVEAL_MINUTE_CHANCE);
	}

	/** Whether the flicker's cooldown has run out for {@code p}. */
	public static boolean flickerReady(ServerPlayer p) {
		OmnitrixState s = Omnitrix.state(p);
		long now = p.level().getGameTime();
		long at = s.readyAt(REVEAL_AT);
		return !s.timers.containsKey(REVEAL_AT) || now < at || now - at >= OmnitrixConfig.REVEAL_COOLDOWN_TICKS;
	}

	/** Gametests: the flicker's cooldown over at once. */
	public static void clearFlickerCooldown(ServerPlayer p) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(REVEAL_AT);
		Omnitrix.save(p, n);
	}

	/** Rolls {@code chance} (cooldown permitting) and, on a hit, reveals. Public for the gametests (chance 1). */
	public static Reveal flicker(ServerPlayer p, float chance) {
		if (!Omnitrix.hasPower(p) || !flickerReady(p) || p.getRandom().nextFloat() >= chance) {
			return Reveal.NONE;
		}
		Reveal r = revealDigit(p);
		if (r.kind() == Kind.NONE && p.getRandom().nextFloat() < OmnitrixConfig.REVEAL_ALIEN_CODE_CHANCE) {
			r = revealAlien(p);
		}
		if (r.kind() == Kind.NONE) {
			return r; // all six known and no code this time: nothing shows, and the cooldown is not spent
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(REVEAL_AT, p.level().getGameTime());
		Omnitrix.save(p, n);
		announce(p, r, "message.projecthero.omnitrix.code.flicker");
		return r;
	}

	// ---------------------------------------------------------------- the Data Fragment

	/** What using a Data Fragment did. */
	public enum FragmentResult {
		NO_WATCH, NOTHING_NEW, DIGIT, ALIEN
	}

	/**
	 * An Omnitrix Data Fragment used by {@code p}: his watch reads it and shows a Master Control digit -- or, now and then
	 * ({@link OmnitrixConfig#FRAGMENT_ALIEN_CODE_CHANCE}), a whole hidden-alien code. Ignores the flicker's cooldown. The item is
	 * consumed only on DIGIT / ALIEN.
	 */
	public static FragmentResult useFragment(ServerPlayer p) {
		if (!Omnitrix.hasPower(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.fragment.no_watch").withStyle(ChatFormatting.GRAY), true);
			return FragmentResult.NO_WATCH;
		}
		boolean digits = !unknownDigits(p).isEmpty();
		boolean aliens = !unknownAliens(p).isEmpty();
		Reveal r;
		if (aliens && (!digits || p.getRandom().nextFloat() < OmnitrixConfig.FRAGMENT_ALIEN_CODE_CHANCE)) {
			r = revealAlien(p);
		} else {
			r = revealDigit(p);
		}
		if (r.kind() == Kind.NONE) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.fragment.nothing").withStyle(ChatFormatting.GRAY), true);
			return FragmentResult.NOTHING_NEW;
		}
		announce(p, r, "message.projecthero.omnitrix.code.fragment");
		return r.kind() == Kind.DIGIT ? FragmentResult.DIGIT : FragmentResult.ALIEN;
	}

	// ---------------------------------------------------------------- telling him

	/** The face and HUD flash, a blip, the action bar and a chat line he can scroll back to. */
	private static void announce(ServerPlayer p, Reveal r, String prefixKey) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(FLASH_AT, p.level().getGameTime());
		Omnitrix.save(p, n);
		OmnitrixSounds.notify(p, OmnitrixSounds.WARNING, 1.8f);
		OmnitrixSounds.notify(p, OmnitrixSounds.SELECT, 1.3f);
		Component head = Component.translatable(prefixKey).withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC);
		Component body;
		if (r.kind() == Kind.DIGIT) {
			body = Component.translatable("message.projecthero.omnitrix.code.digit", r.index() + 1, r.value(), mask(p, "_", " "))
					.withStyle(ChatFormatting.GREEN);
		} else {
			body = Component.translatable("message.projecthero.omnitrix.code.alien", r.code()).withStyle(ChatFormatting.GREEN);
		}
		p.displayClientMessage(Component.empty().append(head).append(" ").append(body), true);
		p.sendSystemMessage(Component.empty().append(head).append(" ").append(body));
	}

	/** Ticks since the last reveal flash for the client ({@code Float.MAX_VALUE} = none). */
	public static float flashAge(OmnitrixState s, long now, float partial) {
		return s.timers.containsKey(FLASH_AT) ? now + partial - s.readyAt(FLASH_AT) : Float.MAX_VALUE;
	}
}
