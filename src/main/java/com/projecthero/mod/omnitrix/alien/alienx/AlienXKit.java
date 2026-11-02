package com.projecthero.mod.omnitrix.alien.alienx;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;
import com.projecthero.mod.quirk.QuirkThreat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Alien X, the Celestialsapien (Ben 10: Alien Force) -- a tall black body full of stars, white horns, glowing green
 * eyes, and the power to rewrite reality... once his two other personalities agree. A BASIC kit (user: "Just make them basic
 * versions"; approved: "Debate before each move").
 *
 * <h2>The debate</h2>
 * Every move is first put to Serena (reason, kindness) and Bellicus (rage, aggression): {@link #DEBATE_TICKS} (1.5 s) in which
 * Alien X is frozen in place (no walking, no jumping, no punches) while they argue on the action bar, then a
 * {@link #PASS_CHANCE} roll -- the motion is carried and the move happens (its own cooldown starts), or "The motion is denied"
 * and a short {@link #DENIED_TICKS} cooldown on that move. Transforming opens with a {@link #OPENING_TICKS} (5 s) debate in which
 * no move works. A debate ALWAYS ends: it resolves on its end tick, a stale one (clock jumps, a lost entry) is dropped, and the
 * freeze comes off on revert. Ending a running toggle (Cosmic Flight off) never needs a vote.
 *
 * <pre>
 *   R        Reality Bolt       17 to the first creature in a 32-block line         Shift+R  Cosmic Ray      20 to everything in a wide 40-block beam
 *   G        Telekinetic Hurl   lift the look target (24) and hurl it: 16, launched  Shift+G  Gravity Crush   r 6: 18, slammed down, slowed
 *   X        Blink              teleport where he looks (48)                        Shift+X  Cosmic Flight   flight toggle (off: no vote)
 *   Z        Time Stop          r 12: every creature frozen 5 s (bosses 1.25 s)      Shift+Z  Reality Rift    a starfield tear (24 away) pulls in r 6 for
 *                                                                                                          3 s: 3 every 0.5 s (18)
 *   V        Restore            +24 health, harmful effects and fire undone          Shift+V  Rewind          back to where (and as healthy as) he was 5 s ago
 *   C        Cosmic Shockwave   r 8 ring: 19 + knockback                             Shift+C  Big Bang        ULTIMATE: r 10, 65 at the centre to 45 at the rim
 * </pre>
 *
 * Passives: a Celestialsapien breathes nothing (no drowning) and falls without harm. Static per-player state (debates, the rewind
 * history, rifts, the tick guard, a forced roll) is cleared in {@link #onRevert} and {@link #clearSessionState}.
 */
public final class AlienXKit extends AlienKit {
	public static final String ID = Aliens.ALIEN_X.id();

	// ---------------------------------------------------------------- the debate
	public static final int DEBATE_TICKS = 30;
	public static final int OPENING_TICKS = 100;
	public static final float PASS_CHANCE = 0.75f;
	public static final int DENIED_TICKS = 60;
	/** The opening debate's lines come every this many ticks (Serena first, then they alternate). */
	public static final int OPENING_LINE_TICKS = 12;
	public static final int OPENING_LINES = 8;
	/** A move debate: Ben's motion at 0, then the two at these ticks. */
	public static final int FIRST_VOICE_AT = 7, SECOND_VOICE_AT = 18;
	/** How many line pairs there are to pick from (lang {@code .debate.serena.N} / {@code .debate.bellicus.N}). */
	public static final int LINE_PAIRS = 8;

	// ---------------------------------------------------------------- moves
	public static final String REALITY_BOLT = "reality_bolt";
	public static final String COSMIC_RAY = "cosmic_ray";
	public static final String HURL = "telekinetic_hurl";
	public static final String CRUSH = "gravity_crush";
	public static final String BLINK = "blink";
	public static final String FLIGHT = "cosmic_flight";
	public static final String TIME_STOP = "time_stop";
	public static final String RIFT = "reality_rift";
	public static final String RESTORE = "restore";
	public static final String REWIND = "rewind";
	public static final String SHOCKWAVE = "cosmic_shockwave";
	public static final String BIG_BANG = "big_bang";
	/** The synced {@code debate_move} timer holds the move's index here + 1. */
	public static final List<String> MOVE_IDS = List.of(REALITY_BOLT, COSMIC_RAY, HURL, CRUSH, BLINK, FLIGHT, TIME_STOP, RIFT, RESTORE, REWIND,
			SHOCKWAVE, BIG_BANG);

	public static final float BOLT_DAMAGE = 17f, RAY_DAMAGE = 20f, HURL_DAMAGE = 16f, CRUSH_DAMAGE = 18f, RIFT_DAMAGE = 3f,
			SHOCKWAVE_DAMAGE = 19f, BANG_CORE = 65f, BANG_RIM = 45f, RESTORE_HEAL = 24f;
	public static final double BOLT_RANGE = 32, RAY_RANGE = 40, RAY_WIDTH = 1.6, HURL_RANGE = 24, CRUSH_RADIUS = 6, BLINK_RANGE = 48,
			STOP_RADIUS = 12, RIFT_RANGE = 24, RIFT_RADIUS = 6, SHOCKWAVE_RADIUS = 8, BANG_RADIUS = 10;
	public static final int STOP_TICKS = 100, RIFT_TICKS = 60, RIFT_PULSE = 10, REWIND_TICKS = 100, HISTORY_EVERY = 5;

	// ---------------------------------------------------------------- synced pose ids / flags / timers
	public static final int ANIM_DEBATE = 1, ANIM_BOLT = 2, ANIM_RAY = 3, ANIM_HURL = 4, ANIM_CRUSH = 5, ANIM_BLINK = 6, ANIM_FLIGHT = 7,
			ANIM_STOP = 8, ANIM_RIFT = 9, ANIM_RESTORE = 10, ANIM_REWIND = 11, ANIM_SHOCKWAVE = 12, ANIM_BANG = 13, ANIM_DENIED = 14,
			ANIM_SEATED = 15;
	public static final int F_DEBATE = 1, F_OPENING = 2, F_FLIGHT = 4;
	public static final String T_DEBATE_START = "debate_start", T_DEBATE_END = "debate_end", T_DEBATE_MOVE = "debate_move",
			T_STOP_END = "stop_end", T_RIFT_END = "rift_end", T_HURL_TARGET = "hurl_target";
	/** Positions ride in the synced timers as fixed-point longs (see {@link #enc}): {@code <name>_x / _y / _z}. */
	public static final String P_STOP = "stop", P_RIFT = "rift", P_BLINK = "blink";

	private static final ResourceLocation FREEZE = PowerToggles.id("omnitrix_alien_x_debate");

	// ---------------------------------------------------------------- sounds (vanilla samples, quiet, layered)
	/** The council convenes: a low chime and a beacon hum. */
	public static final OmnitrixSfx.Cue DEBATE = OmnitrixSfx.cue("alien_x_debate")
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.3f, 0.6f)
			.layer(SoundEvents.BEACON_AMBIENT, 0.22f, 1.4f)
			.layer(SoundEvents.ENCHANTMENT_TABLE_USE, 0.18f, 0.7f, 2).gap(6);
	/** Serena speaks: a bright, soft resonance. */
	public static final OmnitrixSfx.Cue SERENA = OmnitrixSfx.cue("alien_x_serena")
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.25f, 1.5f)
			.layer(SoundEvents.NOTE_BLOCK_CHIME, 0.12f, 1.25f, 1).gap(4);
	/** Bellicus speaks: a low growl under a hot breath. */
	public static final OmnitrixSfx.Cue BELLICUS = OmnitrixSfx.cue("alien_x_bellicus")
			.layer(SoundEvents.RAVAGER_ROAR, 0.1f, 0.55f)
			.layer(SoundEvents.BLAZE_AMBIENT, 0.15f, 0.5f, 1).gap(4);
	/** The motion is carried. */
	public static final OmnitrixSfx.Cue CARRIED = OmnitrixSfx.cue("alien_x_carried")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.25f, 1.6f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.3f, 1.8f, 1);
	/** The motion is denied. */
	public static final OmnitrixSfx.Cue DENIED = OmnitrixSfx.cue("alien_x_denied")
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.28f, 0.8f)
			.layer(SoundEvents.NOTE_BLOCK_BASS, 0.25f, 0.6f, 1);
	public static final OmnitrixSfx.Cue BOLT_SOUND = OmnitrixSfx.cue("alien_x_bolt")
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.3f, 1.8f)
			.layer(SoundEvents.ILLUSIONER_CAST_SPELL, 0.22f, 1.2f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.25f, 0.9f, 2);
	public static final OmnitrixSfx.Cue RAY_SOUND = OmnitrixSfx.cue("alien_x_ray")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.35f, 0.6f)
			.layer(SoundEvents.GUARDIAN_ATTACK, 0.18f, 1.4f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.15f, 1.6f, 4);
	public static final OmnitrixSfx.Cue HURL_SOUND = OmnitrixSfx.cue("alien_x_hurl")
			.layer(SoundEvents.ENDER_DRAGON_FLAP, 0.3f, 1.2f)
			.layer(SoundEvents.ILLUSIONER_CAST_SPELL, 0.22f, 0.8f)
			.layer(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.3f, 0.7f, 3);
	public static final OmnitrixSfx.Cue CRUSH_SOUND = OmnitrixSfx.cue("alien_x_crush")
			.layer(SoundEvents.ANVIL_LAND, 0.2f, 0.5f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.22f, 0.6f, 1)
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.2f, 0.6f);
	public static final OmnitrixSfx.Cue BLINK_SOUND = OmnitrixSfx.cue("alien_x_blink")
			.layer(SoundEvents.ENDERMAN_TELEPORT, 0.3f, 0.8f)
			.layer(SoundEvents.CHORUS_FRUIT_TELEPORT, 0.22f, 1.2f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.2f, 1.5f, 1);
	public static final OmnitrixSfx.Cue FLIGHT_SOUND = OmnitrixSfx.cue("alien_x_flight")
			.layer(SoundEvents.BEACON_AMBIENT, 0.25f, 1.6f)
			.layer(SoundEvents.PHANTOM_FLAP, 0.2f, 0.6f);
	public static final OmnitrixSfx.Cue STOP_SOUND = OmnitrixSfx.cue("alien_x_time_stop")
			.layer(SoundEvents.BELL_RESONATE, 0.3f, 1.2f)
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.3f, 0.5f)
			.layer(SoundEvents.CONDUIT_ACTIVATE, 0.22f, 0.7f, 2);
	public static final OmnitrixSfx.Cue RIFT_SOUND = OmnitrixSfx.cue("alien_x_rift")
			.layer(SoundEvents.PORTAL_TRIGGER, 0.18f, 1.4f)
			.layer(SoundEvents.END_PORTAL_SPAWN, 0.1f, 1.6f)
			.layer(SoundEvents.BEACON_ACTIVATE, 0.2f, 0.7f, 2);
	public static final OmnitrixSfx.Cue RIFT_HUM = OmnitrixSfx.cue("alien_x_rift_hum")
			.layer(SoundEvents.PORTAL_AMBIENT, 0.15f, 0.8f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.12f, 0.6f).gap(8);
	public static final OmnitrixSfx.Cue RESTORE_SOUND = OmnitrixSfx.cue("alien_x_restore")
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.3f, 1.4f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.22f, 1.2f)
			.layer(SoundEvents.PLAYER_LEVELUP, 0.1f, 1.6f, 3);
	public static final OmnitrixSfx.Cue REWIND_SOUND = OmnitrixSfx.cue("alien_x_rewind")
			.layer(SoundEvents.ENDERMAN_TELEPORT, 0.25f, 0.5f)
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.2f, 1.6f)
			.layer(SoundEvents.BELL_RESONATE, 0.15f, 1.8f, 2);
	public static final OmnitrixSfx.Cue SHOCKWAVE_SOUND = OmnitrixSfx.cue("alien_x_shockwave")
			.layer(SoundEvents.WARDEN_SONIC_BOOM, 0.16f, 1.3f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.2f, 0.9f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.25f, 0.7f, 2);
	public static final OmnitrixSfx.Cue BANG_SOUND = OmnitrixSfx.cue("alien_x_big_bang")
			.layer(SoundEvents.END_PORTAL_SPAWN, 0.22f, 0.8f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.35f, 0.5f)
			.layer(SoundEvents.BEACON_ACTIVATE, 0.3f, 0.5f)
			.layer(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.18f, 0.7f, 3);
	public static final OmnitrixSfx.Cue AWAKEN = OmnitrixSfx.cue("alien_x_awaken")
			.layer(SoundEvents.CONDUIT_ACTIVATE, 0.3f, 0.6f)
			.layer(SoundEvents.BEACON_AMBIENT, 0.25f, 0.8f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.2f, 0.5f, 4);

	// ---------------------------------------------------------------- static per-player state

	private static final class Debate {
		final String move; // null = the opening debate
		final long start;
		final long end;
		final int serena;
		final int bellicus;
		final boolean serenaFirst;
		final int target; // Telekinetic Hurl's creature (entity id), or -1
		int lastLine = -1;

		Debate(String move, long start, long end, int serena, int bellicus, boolean serenaFirst, int target) {
			this.move = move;
			this.start = start;
			this.end = end;
			this.serena = serena;
			this.bellicus = bellicus;
			this.serenaFirst = serenaFirst;
			this.target = target;
		}
	}

	private record Snapshot(long time, ResourceKey<Level> dim, Vec3 pos, float yaw, float pitch, float health) {
	}

	private static final class Rift {
		final ResourceKey<Level> dim;
		final Vec3 at;
		final long until;

		Rift(ResourceKey<Level> dim, Vec3 at, long until) {
			this.dim = dim;
			this.at = at;
			this.until = until;
		}
	}

	/** player -> the debate running now. */
	private static final Map<UUID, Debate> PENDING = new ConcurrentHashMap<>();
	/** player -> where he was every quarter second (Rewind). */
	private static final Map<UUID, ArrayDeque<Snapshot>> HISTORY = new ConcurrentHashMap<>();
	/** player -> his Reality Rift. */
	private static final Map<UUID, Rift> RIFTS = new ConcurrentHashMap<>();
	/** player -> the game tick his kit last ran (a mock player can be ticked twice in a tick). */
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	/** player -> the outcome of his next vote (tests). */
	private static final Map<UUID, Boolean> FORCED_ROLL = new ConcurrentHashMap<>();

	public static void register() {
		Aliens.register(new AlienXKit());
	}

	/** From {@code OmnitrixMod.clearSessionState} (the ServerStateReset hook): clear every static per-player map. */
	public static void clearSessionState() {
		PENDING.clear();
		HISTORY.clear();
		RIFTS.clear();
		LAST_TICK.clear();
		FORCED_ROLL.clear();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		return AlienStats.builder().health(60).speed(1.0f).scale(1.15f).armor(14, 4).knockbackResist(0.4f).attack(5).step(0.5f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(
				AlienMove.tap(REALITY_BOLT, AbilitySlot.SLOT_1, 50), AlienMove.shift(COSMIC_RAY, AbilitySlot.SLOT_1, 200),
				AlienMove.tap(HURL, AbilitySlot.SLOT_2, 120), AlienMove.shift(CRUSH, AbilitySlot.SLOT_2, 240),
				AlienMove.tap(BLINK, AbilitySlot.SLOT_3, 60), AlienMove.shift(FLIGHT, AbilitySlot.SLOT_3, 20),
				AlienMove.tap(TIME_STOP, AbilitySlot.SLOT_4, 500), AlienMove.shift(RIFT, AbilitySlot.SLOT_4, 300),
				AlienMove.tap(RESTORE, AbilitySlot.SLOT_5, 600), AlienMove.shift(REWIND, AbilitySlot.SLOT_5, 400),
				AlienMove.tap(SHOCKWAVE, AbilitySlot.SLOT_6, 280), AlienMove.shift(BIG_BANG, AbilitySlot.SLOT_6, 2400));
	}

	// ---------------------------------------------------------------- client-safe reads

	/** Whether {@code p} (Alien X) is debating right now (synced). */
	public static boolean debating(Player p) {
		AlienKit k = activeKit(p);
		return k instanceof AlienXKit && k.flag(p, F_DEBATE);
	}

	/** Whether the debate running now is the transformation's opening one (synced). */
	public static boolean openingDebate(Player p) {
		AlienKit k = activeKit(p);
		return k instanceof AlienXKit && k.flag(p, F_DEBATE) && k.flag(p, F_OPENING);
	}

	/** The move being debated (synced), or null (none / the opening debate). */
	@Nullable
	public static String debatedMove(Player p) {
		int i = (int) Omnitrix.state(p).readyAt(ID + "." + T_DEBATE_MOVE) - 1;
		return i >= 0 && i < MOVE_IDS.size() ? MOVE_IDS.get(i) : null;
	}

	/** Who speaks at {@code elapsed} ticks into a debate: 0 Ben (the motion), 1 Serena, 2 Bellicus. */
	public static int speaker(boolean opening, boolean serenaFirst, float elapsed) {
		if (opening) {
			int line = Mth.clamp((int) (elapsed / OPENING_LINE_TICKS), 0, OPENING_LINES - 1);
			return line % 2 == 0 ? 1 : 2;
		}
		if (elapsed < FIRST_VOICE_AT) {
			return 0;
		}
		boolean first = elapsed < SECOND_VOICE_AT;
		return first == serenaFirst ? 1 : 2;
	}

	/** Fixed-point position encoding for the synced timers (always > 0, so an unset timer reads as "none"). */
	public static long enc(double v) {
		return Math.round(v * 64.0) + (1L << 40);
	}

	public static double dec(long v) {
		return (v - (1L << 40)) / 64.0;
	}

	/** A position stored with {@link #putPos}, or null. */
	@Nullable
	public static Vec3 pos(Player p, String name) {
		OmnitrixState s = Omnitrix.state(p);
		long x = s.readyAt(ID + "." + name + "_x");
		if (x <= 0) {
			return null;
		}
		return new Vec3(dec(x), dec(s.readyAt(ID + "." + name + "_y")), dec(s.readyAt(ID + "." + name + "_z")));
	}

	// ---------------------------------------------------------------- tests

	/** Makes {@code p}'s next vote come out {@code pass} (tests; consumed by the vote). */
	public static void forceNextRoll(ServerPlayer p, boolean pass) {
		FORCED_ROLL.put(p.getUUID(), pass);
	}

	/** Whether the debate freeze is on {@code p}'s speed. */
	public static boolean frozen(Player p) {
		var a = p.getAttribute(Attributes.MOVEMENT_SPEED);
		return a != null && a.getModifier(FREEZE) != null;
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		long now = p.level().getGameTime();
		HISTORY.remove(p.getUUID());
		startDebate(p, null, now, OPENING_TICKS, -1);
		OmnitrixSfx.play(level(p), p.position(), AWAKEN, 1.0f, 1.0f);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		UUID me = p.getUUID();
		PENDING.remove(me);
		HISTORY.remove(me);
		RIFTS.remove(me);
		LAST_TICK.remove(me);
		FORCED_ROLL.remove(me);
		freeze(p, false);
		if (flag(p, F_FLIGHT)) {
			flight(p, false);
		}
		OmnitrixState n = Omnitrix.state(p).copy();
		// every synced kit timer but the moves' cooldowns (those carry on as with any alien)
		n.timers.keySet().removeIf(k -> k.startsWith(ID + ".") && !MOVE_IDS.contains(k.substring(ID.length() + 1)));
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		Long last = LAST_TICK.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		Debate d = PENDING.get(p.getUUID());
		if (d != null) {
			if (now < d.start || now > d.end + 40) {
				endDebate(p); // a stale debate (the clock jumped): never lock him forever
			} else if (now >= d.end) {
				resolve(p, d, now);
			} else {
				holdDebate(p, d, now);
			}
		} else if ((s.kitFlags & F_DEBATE) != 0 || frozen(p)) {
			endDebate(p); // a flag with no debate behind it
		}
		if (now % HISTORY_EVERY == 0) {
			ArrayDeque<Snapshot> h = HISTORY.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
			h.addLast(new Snapshot(now, p.level().dimension(), p.position(), p.getYRot(), p.getXRot(), p.getHealth()));
			while (h.size() > REWIND_TICKS / HISTORY_EVERY + 2) {
				h.removeFirst();
			}
		}
		tickRift(p, now);
		if (flag(p, F_FLIGHT)) {
			p.resetFallDistance();
		}
	}

	// ---------------------------------------------------------------- keys

	@Override
	public boolean keyActive(Player p, AbilitySlot slot) {
		return slot == AbilitySlot.SLOT_3 && flag(p, F_FLIGHT);
	}

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		AlienMove m = moveOn(slot, shift, false);
		if (m == null) {
			return;
		}
		if (PENDING.containsKey(p.getUUID())) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.alien_x.busy").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		// ending a running toggle needs no vote
		if (m.id().equals(FLIGHT) && flag(p, F_FLIGHT)) {
			flight(p, false);
			return;
		}
		int left = cooldownLeft(p, m.id());
		if (left > 0) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.cooldown",
					Component.translatable("projecthero.omnitrix." + ID + "." + m.id()),
					String.format(java.util.Locale.ROOT, "%.1f", left / 20f)).withStyle(ChatFormatting.GRAY), true);
			return;
		}
		int target = -1;
		if (m.id().equals(HURL)) {
			LivingEntity t = lookTarget(p, HURL_RANGE);
			if (t == null) {
				p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.alien_x.no_target").withStyle(ChatFormatting.GRAY), true);
				return;
			}
			target = t.getId();
		}
		if (m.id().equals(RESTORE) && p.getHealth() >= p.getMaxHealth() && !p.isOnFire() && !hasHarmful(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.alien_x.whole").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		startDebate(p, m.id(), p.level().getGameTime(), DEBATE_TICKS, target);
	}

	/** Frozen while debating: no punches either. */
	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		return PENDING.containsKey(p.getUUID());
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_FALL) && !source.is(DamageTypes.DROWN);
	}

	// ---------------------------------------------------------------- the debate

	private void startDebate(ServerPlayer p, @Nullable String move, long now, int ticks, int target) {
		var r = p.getRandom();
		Debate d = new Debate(move, now, now + ticks, 1 + r.nextInt(LINE_PAIRS), 1 + r.nextInt(LINE_PAIRS), move == null || r.nextBoolean(), target);
		PENDING.put(p.getUUID(), d);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(T_DEBATE_START), now);
		n.timers.put(key(T_DEBATE_END), now + ticks);
		if (move != null) {
			n.timers.put(key(T_DEBATE_MOVE), (long) MOVE_IDS.indexOf(move) + 1);
			n.animId = ANIM_DEBATE;
			n.animStart = now;
		} else {
			n.timers.remove(key(T_DEBATE_MOVE)); // (the transform's own anim keeps playing: the green flash)
		}
		n.kitFlags = (n.kitFlags | F_DEBATE) & ~F_OPENING | (move == null ? F_OPENING : 0);
		n.timers.put(key("debate_first"), d.serenaFirst ? 1L : 2L);
		Omnitrix.save(p, n);
		freeze(p, true);
		OmnitrixSfx.play(level(p), p.position(), DEBATE, 0.9f, move == null ? 0.8f : 1.0f);
		holdDebate(p, d, now);
	}

	private void holdDebate(ServerPlayer p, Debate d, long now) {
		freeze(p, true);
		if (p.getAbilities().flying) {
			p.setDeltaMovement(Vec3.ZERO);
			p.hurtMarked = true;
		}
		int elapsed = (int) (now - d.start);
		int line;
		if (d.move == null) {
			line = Math.min(OPENING_LINES - 1, elapsed / OPENING_LINE_TICKS);
		} else {
			line = elapsed >= SECOND_VOICE_AT ? 2 : elapsed >= FIRST_VOICE_AT ? 1 : 0;
		}
		if (line <= d.lastLine) {
			return;
		}
		d.lastLine = line;
		MutableComponent msg;
		if (d.move == null) {
			boolean serena = line % 2 == 0;
			msg = voice(serena, Component.translatable("projecthero.omnitrix.alien_x.opening." + (serena ? "serena." : "bellicus.") + (line / 2 + 1)));
			OmnitrixSfx.play(level(p), p.position(), serena ? SERENA : BELLICUS, 0.8f, 1.0f);
		} else if (line == 0) {
			msg = Component.translatable("projecthero.omnitrix.alien_x.voice.ben").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
					.append(Component.literal(": ").withStyle(ChatFormatting.GREEN))
					.append(Component.translatable("message.projecthero.omnitrix.alien_x.motion",
							Component.translatable("projecthero.omnitrix." + ID + "." + d.move)).withStyle(ChatFormatting.WHITE));
		} else {
			boolean serena = (line == 1) == d.serenaFirst;
			msg = voice(serena, Component.translatable("projecthero.omnitrix.alien_x.debate." + (serena ? "serena." : "bellicus.")
					+ (serena ? d.serena : d.bellicus)));
			OmnitrixSfx.play(level(p), p.position(), serena ? SERENA : BELLICUS, 0.8f, 1.0f);
		}
		p.displayClientMessage(msg, true);
	}

	private static MutableComponent voice(boolean serena, MutableComponent line) {
		ChatFormatting name = serena ? ChatFormatting.AQUA : ChatFormatting.RED;
		ChatFormatting text = serena ? ChatFormatting.WHITE : ChatFormatting.GOLD;
		return Component.translatable("projecthero.omnitrix.alien_x.voice." + (serena ? "serena" : "bellicus")).withStyle(name, ChatFormatting.BOLD)
				.append(Component.literal(": ").withStyle(name)).append(line.withStyle(text));
	}

	private void resolve(ServerPlayer p, Debate d, long now) {
		endDebate(p);
		if (d.move == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.alien_x.seated").withStyle(ChatFormatting.GREEN), true);
			OmnitrixSfx.play(level(p), p.position(), CARRIED, 0.8f, 0.9f);
			anim(p, ANIM_SEATED);
			return;
		}
		Boolean forced = FORCED_ROLL.remove(p.getUUID());
		boolean pass = forced != null ? forced : p.getRandom().nextFloat() < PASS_CHANCE;
		Component name = Component.translatable("projecthero.omnitrix." + ID + "." + d.move);
		if (!pass) {
			cooldown(p, d.move, DENIED_TICKS);
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.alien_x.denied").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
					true);
			OmnitrixSfx.play(level(p), p.position(), DENIED, 0.9f, 1.0f);
			anim(p, ANIM_DENIED);
			return;
		}
		cooldown(p, d.move, -1);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.alien_x.carried", name).withStyle(ChatFormatting.GREEN), true);
		OmnitrixSfx.play(level(p), p.position(), CARRIED, 0.6f, 1.0f);
		perform(p, d.move, d.target);
	}

	/** Ends the running debate (if any): the freeze, the flags and the synced timers. */
	private void endDebate(ServerPlayer p) {
		PENDING.remove(p.getUUID());
		freeze(p, false);
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & (F_DEBATE | F_OPENING)) != 0 || s.timers.containsKey(key(T_DEBATE_END))) {
			OmnitrixState n = s.copy();
			n.kitFlags &= ~(F_DEBATE | F_OPENING);
			n.timers.remove(key(T_DEBATE_START));
			n.timers.remove(key(T_DEBATE_END));
			n.timers.remove(key(T_DEBATE_MOVE));
			n.timers.remove(key("debate_first"));
			Omnitrix.save(p, n);
		}
	}

	private static void freeze(ServerPlayer p, boolean on) {
		if (on) {
			boolean fresh = !frozen(p);
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, FREEZE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			PowerToggles.modifier(p, Attributes.JUMP_STRENGTH, FREEZE, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			if (fresh) {
				p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
				p.hurtMarked = true;
			}
		} else {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, FREEZE);
			PowerToggles.clearModifier(p, Attributes.JUMP_STRENGTH, FREEZE);
		}
	}

	// ---------------------------------------------------------------- the moves (after a carried motion)

	/** Runs a carried motion; false if it fizzled. */
	private boolean perform(ServerPlayer p, String move, int target) {
		return switch (move) {
			case REALITY_BOLT -> realityBolt(p);
			case COSMIC_RAY -> cosmicRay(p);
			case HURL -> hurl(p, target);
			case CRUSH -> crush(p);
			case BLINK -> blink(p);
			case FLIGHT -> flight(p, true);
			case TIME_STOP -> timeStop(p);
			case RIFT -> rift(p);
			case RESTORE -> restore(p);
			case REWIND -> rewind(p);
			case SHOCKWAVE -> shockwave(p);
			case BIG_BANG -> bigBang(p);
			default -> false;
		};
	}

	/** Where the look ray first meets a block within {@code range} (or its end). */
	private static Vec3 lookEnd(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		var hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	private boolean realityBolt(ServerPlayer p) {
		anim(p, ANIM_BOLT);
		OmnitrixSfx.play(level(p), p.position(), BOLT_SOUND, 1.0f, 1.0f);
		LivingEntity t = lookTarget(p, BOLT_RANGE);
		Vec3 end = t != null ? t.position().add(0, t.getBbHeight() * 0.5, 0) : lookEnd(p, BOLT_RANGE);
		level(p).sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 6, 0.15, 0.15, 0.15, 0.05);
		if (t != null) {
			hurt(p, t, BOLT_DAMAGE, false);
		}
		return true;
	}

	private boolean cosmicRay(ServerPlayer p) {
		anim(p, ANIM_RAY);
		OmnitrixSfx.play(level(p), p.position(), RAY_SOUND, 1.3f, 1.0f);
		Vec3 eye = p.getEyePosition();
		Vec3 end = lookEnd(p, RAY_RANGE);
		Vec3 dir = end.subtract(eye);
		double len = dir.length();
		if (len < 1.0e-3) {
			return true;
		}
		Vec3 u = dir.scale(1.0 / len);
		for (LivingEntity e : around(p, eye.add(dir.scale(0.5)), len * 0.5 + RAY_WIDTH)) {
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double t = c.dot(u);
			if (t < 0 || t > len + 0.5) {
				continue;
			}
			if (c.subtract(u.scale(t)).length() <= RAY_WIDTH + e.getBbWidth() * 0.5) {
				hurt(p, e, RAY_DAMAGE, false);
			}
		}
		level(p).sendParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 10, 0.4, 0.4, 0.4, 0.08);
		return true;
	}

	private boolean hurl(ServerPlayer p, int targetId) {
		Entity found = targetId >= 0 ? p.level().getEntity(targetId) : null;
		LivingEntity t = found instanceof LivingEntity le && le.isAlive() && le.distanceTo(p) <= HURL_RANGE + 8 ? le : lookTarget(p, HURL_RANGE);
		anim(p, ANIM_HURL);
		if (t == null) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.alien_x.no_target").withStyle(ChatFormatting.GRAY), true);
			cooldown(p, HURL, DENIED_TICKS);
			return false;
		}
		putTimer(p, T_HURL_TARGET, t.getId() + 1L);
		OmnitrixSfx.play(level(p), t.position(), HURL_SOUND, 1.0f, 1.0f);
		hurt(p, t, HURL_DAMAGE, false);
		Vec3 look = p.getLookAngle();
		double res = 1.0 - Math.min(1.0, t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		double k = AlienStatus.isBoss(t) ? 0.25 : 1.0;
		t.push(look.x * 2.2 * res * k, (0.7 + Math.max(0, look.y)) * Math.max(0.3, res) * k, look.z * 2.2 * res * k);
		t.hurtMarked = true;
		return true;
	}

	private boolean crush(ServerPlayer p) {
		anim(p, ANIM_CRUSH);
		OmnitrixSfx.play(level(p), p.position(), CRUSH_SOUND, 1.2f, 1.0f);
		for (LivingEntity e : around(p, p.position(), CRUSH_RADIUS)) {
			hurt(p, e, CRUSH_DAMAGE, false);
			e.setDeltaMovement(e.getDeltaMovement().x * 0.2, -1.2, e.getDeltaMovement().z * 0.2);
			e.hurtMarked = true;
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, AlienStatus.isBoss(e) ? 15 : 60, 1), p);
		}
		return true;
	}

	private boolean blink(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 end = lookEnd(p, BLINK_RANGE);
		double dist = end.distanceTo(eye);
		Vec3 from = p.position();
		for (double back = 0.6; back < dist; back += 0.5) {
			Vec3 spot = end.subtract(look.scale(back));
			for (double up = 0; up <= 2.0; up += 0.5) {
				Vec3 feet = new Vec3(spot.x, spot.y - p.getBbHeight() * 0.5 + up, spot.z);
				AABB box = p.getBoundingBox().move(feet.subtract(p.position()));
				if (p.level().noCollision(p, box)) {
					if (feet.distanceToSqr(from) < 1.0) {
						break;
					}
					putPos(p, P_BLINK, from.add(0, p.getBbHeight() * 0.5, 0));
					anim(p, ANIM_BLINK);
					OmnitrixSfx.play(level(p), from, BLINK_SOUND, 1.0f, 1.0f);
					p.teleportTo(feet.x, feet.y, feet.z);
					p.resetFallDistance();
					OmnitrixSfx.play(level(p), feet, BLINK_SOUND, 0.8f, 1.2f);
					return true;
				}
			}
		}
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.alien_x.no_room").withStyle(ChatFormatting.GRAY), true);
		cooldown(p, BLINK, 0);
		return false;
	}

	private boolean flight(ServerPlayer p, boolean on) {
		setFlag(p, F_FLIGHT, on);
		if (on) {
			p.getAbilities().mayfly = true;
			if (!p.onGround()) {
				p.getAbilities().flying = true;
			}
			anim(p, ANIM_FLIGHT);
			OmnitrixSfx.play(level(p), p.position(), FLIGHT_SOUND, 1.0f, 1.0f);
		} else if (!p.isCreative() && !p.isSpectator()) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
		}
		p.onUpdateAbilities();
		p.resetFallDistance();
		return true;
	}

	private boolean timeStop(ServerPlayer p) {
		anim(p, ANIM_STOP);
		OmnitrixSfx.play(level(p), p.position(), STOP_SOUND, 1.3f, 1.0f);
		Vec3 c = p.position();
		putPos(p, P_STOP, c);
		putTimer(p, T_STOP_END, p.level().getGameTime() + STOP_TICKS);
		for (LivingEntity e : around(p, c, STOP_RADIUS)) {
			// stun: a boss is held only a quarter as long, keeps its target, and its threat table hears it
			AlienStatus.stun(p, e, STOP_TICKS);
			e.setDeltaMovement(Vec3.ZERO);
			e.hurtMarked = true;
		}
		for (Projectile pr : p.level().getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(STOP_RADIUS))) {
			if (pr.getOwner() != p) {
				pr.setDeltaMovement(Vec3.ZERO);
				pr.hurtMarked = true;
			}
		}
		return true;
	}

	private boolean rift(ServerPlayer p) {
		LivingEntity t = lookTarget(p, RIFT_RANGE);
		Vec3 at = t != null ? t.position().add(0, 1.0, 0) : lookEnd(p, RIFT_RANGE).subtract(p.getLookAngle().scale(0.5));
		anim(p, ANIM_RIFT);
		OmnitrixSfx.play(level(p), at, RIFT_SOUND, 1.2f, 1.0f);
		long now = p.level().getGameTime();
		RIFTS.put(p.getUUID(), new Rift(p.level().dimension(), at, now + RIFT_TICKS));
		putPos(p, P_RIFT, at);
		putTimer(p, T_RIFT_END, now + RIFT_TICKS);
		return true;
	}

	private void tickRift(ServerPlayer p, long now) {
		Rift r = RIFTS.get(p.getUUID());
		if (r == null) {
			return;
		}
		if (now >= r.until || !p.level().dimension().equals(r.dim)) {
			RIFTS.remove(p.getUUID());
			return;
		}
		boolean pulse = (r.until - now) % RIFT_PULSE == 0;
		for (LivingEntity e : around(p, r.at, RIFT_RADIUS)) {
			Vec3 d = r.at.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
			double len = d.length();
			if (len > 0.3) {
				double k = AlienStatus.isBoss(e) ? 0.03 : 0.18;
				e.push(d.x / len * k, d.y / len * k * 0.6, d.z / len * k);
				e.hurtMarked = true;
			}
			if (pulse) {
				hurt(p, e, RIFT_DAMAGE, false);
			}
		}
		if (pulse) {
			OmnitrixSfx.play(level(p), r.at, RIFT_HUM, 0.8f, 1.0f);
		}
	}

	private static boolean hasHarmful(ServerPlayer p) {
		for (MobEffectInstance e : p.getActiveEffects()) {
			if (e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
				return true;
			}
		}
		return false;
	}

	private boolean restore(ServerPlayer p) {
		anim(p, ANIM_RESTORE);
		OmnitrixSfx.play(level(p), p.position(), RESTORE_SOUND, 1.0f, 1.0f);
		p.heal(RESTORE_HEAL);
		p.clearFire();
		p.getActiveEffects().stream().filter(e -> e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL).map(MobEffectInstance::getEffect)
				.toList().forEach(p::removeEffect);
		return true;
	}

	private boolean rewind(ServerPlayer p) {
		ArrayDeque<Snapshot> h = HISTORY.get(p.getUUID());
		long now = p.level().getGameTime();
		Snapshot pick = null;
		if (h != null) {
			// the newest snapshot at least 5 s old -- or, early in a transformation, the oldest there is
			for (Snapshot s : h) {
				if (!s.dim().equals(p.level().dimension())) {
					continue;
				}
				if (pick == null || now - s.time() >= REWIND_TICKS) {
					pick = s;
				}
			}
		}
		if (pick == null || pick.pos().distanceToSqr(p.position()) < 0.01 && pick.health() <= p.getHealth()) {
			p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.alien_x.no_rewind").withStyle(ChatFormatting.GRAY), true);
			cooldown(p, REWIND, 0);
			return false;
		}
		putPos(p, P_BLINK, p.position().add(0, p.getBbHeight() * 0.5, 0));
		anim(p, ANIM_REWIND);
		OmnitrixSfx.play(level(p), p.position(), REWIND_SOUND, 1.0f, 1.0f);
		p.teleportTo(pick.pos().x, pick.pos().y, pick.pos().z);
		p.setYRot(pick.yaw());
		p.setXRot(pick.pitch());
		p.resetFallDistance();
		if (pick.health() > p.getHealth()) {
			p.setHealth(Math.min(p.getMaxHealth(), pick.health()));
		}
		p.clearFire();
		OmnitrixSfx.play(level(p), p.position(), REWIND_SOUND, 0.7f, 1.3f);
		return true;
	}

	private boolean shockwave(ServerPlayer p) {
		anim(p, ANIM_SHOCKWAVE);
		OmnitrixSfx.play(level(p), p.position(), SHOCKWAVE_SOUND, 1.3f, 1.0f);
		for (LivingEntity e : around(p, p.position(), SHOCKWAVE_RADIUS)) {
			hurt(p, e, SHOCKWAVE_DAMAGE, false);
			shove(e, p.position(), 1.6, 0.5);
		}
		return true;
	}

	private boolean bigBang(ServerPlayer p) {
		anim(p, ANIM_BANG);
		OmnitrixSfx.play(level(p), p.position(), BANG_SOUND, 1.6f, 1.0f);
		Vec3 c = p.position().add(0, 1.0, 0);
		for (LivingEntity e : around(p, c, BANG_RADIUS)) {
			double d = Math.min(BANG_RADIUS, e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(c));
			float dmg = (float) Mth.lerp(d / BANG_RADIUS, BANG_CORE, BANG_RIM);
			hurt(p, e, dmg, false);
			shove(e, c, 2.5, 0.9);
			QuirkThreat.add(e, p, 6f);
		}
		ServerLevel l = level(p);
		l.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		l.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 24, 0.5, 0.5, 0.5, 0.35);
		return true;
	}

	// ---------------------------------------------------------------- synced timer helpers

	private void putTimer(ServerPlayer p, String name, long value) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(name), value);
		Omnitrix.save(p, n);
	}

	private void putPos(ServerPlayer p, String name, Vec3 at) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(key(name + "_x"), enc(at.x));
		n.timers.put(key(name + "_y"), enc(at.y));
		n.timers.put(key(name + "_z"), enc(at.z));
		Omnitrix.save(p, n);
	}
}
