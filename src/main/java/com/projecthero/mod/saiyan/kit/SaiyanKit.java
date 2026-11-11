package com.projecthero.mod.saiyan.kit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.SaiyanForm;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Saiyan revamp: the move kit. CONTRACT shared by the parallel builds -- the core (keys, Ki, rage, Super Saiyan,
 * Kaioken, flight) routes each key to {@link #press}; the kit owns every move. Move ids:
 * <pre>
 *   R combo        Shift+R martial      G ki_blast       Shift+G beam        Z strong        Shift+Z spirit_bomb
 *   X dash         Shift+X rush         C aoe            Shift+C aoe2        V utility (tap) Shift+V utility2
 * </pre>
 * {@code spirit_bomb} is delegated to {@link SaiyanSpiritBomb}. Ki is spent through the core's {@code Saiyan.spendKi}.
 *
 * <p>The moves themselves are in {@link SaiyanKitMelee} (combo, Rising Dragon Kick, Meteor Smash, Ki Dash, Dragon Rush) and
 * {@link SaiyanKitKi} (Ki Blast Volley, Kamehameha, Explosive Wave, Ki Rain, Solar Flare); the numbers in
 * {@link SaiyanKitConfig}. Everything is server-side; the look goes to every viewer as {@link SaiyanKitFxPayload}s.
 * {@code utility} (V) is the core's Ki charge now and is a no-op here.
 *
 * <p>Every static map here holds players or creatures and is dropped by {@link #clearSessionState}.
 */
public final class SaiyanKit {
	public static final String COMBO = "combo", MARTIAL = "martial", KI_BLAST = "ki_blast", BEAM = "beam", STRONG = "strong",
			SPIRIT_BOMB = "spirit_bomb", DASH = "dash", RUSH = "rush", AOE = "aoe", AOE2 = "aoe2", UTILITY = "utility", UTILITY2 = "utility2";
	/** Every id, in key order (for the HUD and the tests). */
	public static final String[] IDS = { COMBO, MARTIAL, KI_BLAST, BEAM, STRONG, SPIRIT_BOMB, DASH, RUSH, AOE, AOE2, UTILITY, UTILITY2 };

	/** Pose ids sent to the client ({@code client.saiyan.kit.SaiyanKitPose}). */
	public static final int ANIM_NONE = 0, ANIM_JAB_R = 1, ANIM_JAB_L = 2, ANIM_KICK = 3, ANIM_HOOK = 4, ANIM_FINISH = 5, ANIM_FLIP_KICK = 6,
			ANIM_AXE_KICK = 7, ANIM_BLAST_R = 8, ANIM_BLAST_L = 9, ANIM_KAME_FIRE = 10, ANIM_SMASH = 11, ANIM_DASH = 12, ANIM_RUSH = 13,
			ANIM_RUSH_FINISH = 14, ANIM_WAVE = 15, ANIM_RAIN = 16, ANIM_FLARE = 17, ANIM_WHIFF_KICK = 18;

	/** A move in progress: one at a time (the combo is tracked apart). */
	static final class Run {
		final String id;
		final long start;
		/** Holds the player's other inputs ({@link #busy}). The Ki Blast Volley does not. */
		final boolean busy;
		/** Hold moves (volley, Kamehameha): the key is still down. */
		boolean held = true;
		/** Kamehameha: when it fired (-1: still charging). */
		long fired = -1;
		float charge;
		float kiPaid;
		long lastShot = Long.MIN_VALUE;
		int shotHand;
		LivingEntity target;
		Vec3 origin = Vec3.ZERO;
		Vec3 dir = Vec3.ZERO;
		Vec3 aim = Vec3.ZERO;
		double reach;
		int hits;
		int stage;
		final Set<Integer> hitIds = new HashSet<>();
		final Map<Integer, Integer> pulses = new HashMap<>();
		final List<LivingEntity> struck = new ArrayList<>();

		Run(String id, long start, boolean busy) {
			this.id = id;
			this.start = start;
			this.busy = busy;
		}
	}

	/** A test's stand-in for the core's form and Ki (never set in play). */
	record TestOverride(SaiyanForm form, float[] ki) {
	}

	static final Map<UUID, Run> RUNS = new ConcurrentHashMap<>();
	static final Map<UUID, Map<String, Long>> READY = new ConcurrentHashMap<>();
	/** The combo: {next hit 0-4, last hit game time}. */
	static final Map<UUID, long[]> COMBO_STATE = new ConcurrentHashMap<>();
	/** No fall damage until this game time or the first landing (Ki Dash, Rising Dragon Kick, Meteor Smash, Ki Rain). */
	static final Map<UUID, Long> FALL_SAFE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> TICKED = new ConcurrentHashMap<>();
	private static final Map<UUID, TestOverride> OVERRIDES = new ConcurrentHashMap<>();

	private SaiyanKit() {
	}

	// ---------------------------------------------------------------- lifecycle

	/** The packet and the kit's own server tick (so a move keeps running whoever calls {@link #tick}). From ProjectHeroMod. */
	public static void initialize() {
		PayloadTypeRegistry.playS2C().register(SaiyanKitFxPayload.TYPE, SaiyanKitFxPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(SaiyanKit::serverTick);
		ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> forget(h.player.getUUID()));
	}

	/** A test sets the form and Ki the kit sees for this player (Ki is spent from it). Null clears it. */
	public static void overrideForTest(Player p, SaiyanForm form, float ki) {
		if (form == null) {
			OVERRIDES.remove(p.getUUID());
		} else {
			OVERRIDES.put(p.getUUID(), new TestOverride(form, new float[] { ki }));
		}
	}

	/** Clears this player's cooldowns, combo and running move (tests and the harness). */
	public static void resetForTest(Player p) {
		READY.remove(p.getUUID());
		COMBO_STATE.remove(p.getUUID());
		RUNS.remove(p.getUUID());
	}

	private static void forget(UUID id) {
		RUNS.remove(id);
		READY.remove(id);
		COMBO_STATE.remove(id);
		FALL_SAFE.remove(id);
		TICKED.remove(id);
		OVERRIDES.remove(id);
		SaiyanKitKi.forget(id);
	}

	private static void serverTick(MinecraftServer server) {
		for (UUID id : new ArrayList<>(RUNS.keySet())) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) {
				RUNS.remove(id);
				continue;
			}
			tickKit(p);
		}
		for (UUID id : new ArrayList<>(FALL_SAFE.keySet())) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) {
				FALL_SAFE.remove(id);
			} else {
				tickFallSafe(p);
			}
		}
		SaiyanKitKi.serverTick(server);
	}

	// ---------------------------------------------------------------- the contract

	/** A key went down ({@code down}) or up for move {@code id}. */
	public static void press(ServerPlayer p, String id, boolean down) {
		if (SPIRIT_BOMB.equals(id)) {
			SaiyanSpiritBomb.press(p, down);
			return;
		}
		if (!p.isAlive() || p.isSpectator()) {
			return;
		}
		Run cur = RUNS.get(p.getUUID());
		if (!down) {
			if (cur != null && cur.id.equals(id)) {
				cur.held = false;
			}
			return;
		}
		if (SaiyanSpiritBomb.busy(p) || UTILITY.equals(id)) {
			return;
		}
		if (COMBO.equals(id)) {
			if (cur == null) {
				SaiyanKitMelee.combo(p);
			}
			return;
		}
		if (cur != null) {
			if (!KI_BLAST.equals(cur.id)) {
				return; // one move at a time
			}
			end(p, cur); // a new move cuts the volley short
		}
		int cd = cooldownRemaining(p, id);
		if (cd > 0) {
			if (KI_BLAST.equals(id)) {
				return; // the volley's own rate, not worth a message
			}
			say(p, "message.projecthero.saiyan.kit.cooldown", Component.translatable("projecthero.saiyan.ability." + id),
					String.format(Locale.ROOT, "%.1f", cd / 20.0f));
			return;
		}
		switch (id) {
			case MARTIAL -> SaiyanKitMelee.martial(p);
			case STRONG -> SaiyanKitMelee.smash(p);
			case DASH -> SaiyanKitMelee.dash(p);
			case RUSH -> SaiyanKitMelee.rush(p);
			case KI_BLAST -> SaiyanKitKi.volley(p);
			case BEAM -> SaiyanKitKi.beamStart(p);
			case AOE -> SaiyanKitKi.wave(p);
			case AOE2 -> SaiyanKitKi.rain(p);
			case UTILITY2 -> SaiyanKitKi.flare(p);
			default -> {
			}
		}
	}

	/** Every server tick for every Saiyan. */
	public static void tick(ServerPlayer p) {
		SaiyanSpiritBomb.tick(p);
		tickKit(p);
	}

	/** Left click on a creature (the combo's melee hit hook). */
	public static void attack(ServerPlayer p) {
		if (!p.isAlive() || p.isSpectator() || RUNS.containsKey(p.getUUID()) || SaiyanSpiritBomb.busy(p)) {
			return;
		}
		SaiyanKitMelee.combo(p);
	}

	public static int cooldownRemaining(Player p, String id) {
		if (SPIRIT_BOMB.equals(id)) {
			return SaiyanSpiritBomb.cooldownRemaining(p);
		}
		Map<String, Long> m = READY.get(p.getUUID());
		if (m == null) {
			return 0;
		}
		Long at = m.get(id);
		return at == null ? 0 : (int) Math.max(0L, at - p.level().getGameTime());
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case SPIRIT_BOMB -> SaiyanSpiritBomb.maxCooldown();
			case COMBO -> SaiyanKitConfig.COMBO_RECOVERY;
			case MARTIAL -> SaiyanKitConfig.MARTIAL_COOLDOWN;
			case KI_BLAST -> SaiyanKitConfig.BLAST_EVERY;
			case BEAM -> SaiyanKitConfig.BEAM_COOLDOWN;
			case STRONG -> SaiyanKitConfig.SMASH_COOLDOWN;
			case DASH -> SaiyanKitConfig.DASH_COOLDOWN;
			case RUSH -> SaiyanKitConfig.RUSH_COOLDOWN;
			case AOE -> SaiyanKitConfig.WAVE_COOLDOWN;
			case AOE2 -> SaiyanKitConfig.RAIN_COOLDOWN;
			case UTILITY2 -> SaiyanKitConfig.FLARE_COOLDOWN;
			default -> 1;
		};
	}

	/** True while a move holds the player (rooted / channelling), so the core can block other inputs. */
	public static boolean busy(Player p) {
		Run r = RUNS.get(p.getUUID());
		return (r != null && r.busy) || SaiyanSpiritBomb.busy(p);
	}

	/** Session reset / player leave. */
	public static void clearSessionState() {
		SaiyanSpiritBomb.clearSessionState();
		RUNS.clear();
		READY.clear();
		COMBO_STATE.clear();
		FALL_SAFE.clear();
		TICKED.clear();
		OVERRIDES.clear();
		SaiyanKitKi.clearSessionState();
	}

	// ---------------------------------------------------------------- hooks for the core

	/**
	 * The kit deals BASE damage: the core's {@code SaiyanDamage} multiplies every hit a Saiyan deals by {@code Saiyan.damageMult}
	 * (Super Saiyan, Kaioken, Zenkai, the strong-enemy rage), so applying it here too would double it up. Kept as 1 for callers.
	 */
	public static float damageMult(Player p) {
		return 1f;
	}

	/** True while a kit move protects him from fall damage (the core's damage rules can consult it). */
	public static boolean fallSafe(Player p) {
		Long until = FALL_SAFE.get(p.getUUID());
		return (until != null && until >= p.level().getGameTime()) || RUNS.containsKey(p.getUUID());
	}

	/** The move running now ({@code null}: none). */
	public static String running(Player p) {
		Run r = RUNS.get(p.getUUID());
		return r == null ? null : r.id;
	}

	/** Kamehameha: the charge 0..1 (0 when not charging or firing). */
	public static float charge(Player p) {
		Run r = RUNS.get(p.getUUID());
		return r == null ? 0f : r.charge;
	}

	/** Ki spheres in flight (tests). */
	public static int shotsInFlight() {
		return SaiyanKitKi.shotsInFlight();
	}

	/** True while a Solar Flare keeps this mob off any target (tests). */
	public static boolean flared(Mob m) {
		return SaiyanKitKi.flared(m);
	}

	/** The Ki the running Kamehameha has spent (tests). */
	public static float beamKiPaid(Player p) {
		return SaiyanKitKi.beamKiPaid(p);
	}

	/** The Kamehameha's total damage on one creature at charge 0..1, before {@link #damageMult}. */
	public static float beamDamage(float charge) {
		return SaiyanKitKi.beamDamage(charge);
	}

	/** The combo's next hit, 0-4 (tests and the HUD). */
	public static int comboStep(Player p) {
		long[] st = COMBO_STATE.get(p.getUUID());
		return st == null ? 0 : (int) st[0];
	}

	// ---------------------------------------------------------------- the tick

	private static void tickKit(ServerPlayer p) {
		long now = p.level().getGameTime();
		Long last = TICKED.put(p.getUUID(), now);
		if (last != null && last == now) {
			return;
		}
		Run r = RUNS.get(p.getUUID());
		if (r == null) {
			return;
		}
		if (!p.isAlive() || p.isSpectator()) {
			if (BEAM.equals(r.id)) {
				SaiyanKitKi.beamEnd(p, r, false);
			}
			end(p, r);
			return;
		}
		long age = now - r.start;
		switch (r.id) {
			case MARTIAL -> SaiyanKitMelee.tickMartial(p, r, age);
			case STRONG -> SaiyanKitMelee.tickSmash(p, r, age);
			case DASH -> SaiyanKitMelee.tickDash(p, r, age);
			case RUSH -> SaiyanKitMelee.tickRush(p, r, age);
			case KI_BLAST -> SaiyanKitKi.tickVolley(p, r, now);
			case BEAM -> SaiyanKitKi.tickBeam(p, r, now);
			case AOE -> {
				if (age >= SaiyanKitConfig.SHORT_BUSY) {
					end(p, r);
				}
			}
			case AOE2 -> SaiyanKitKi.tickRain(p, r, age);
			case UTILITY2 -> {
				if (age >= SaiyanKitConfig.SHORT_BUSY) {
					end(p, r);
				}
			}
			default -> end(p, r);
		}
	}

	private static void tickFallSafe(ServerPlayer p) {
		Long until = FALL_SAFE.get(p.getUUID());
		if (until == null) {
			return;
		}
		long now = p.level().getGameTime();
		boolean running = RUNS.containsKey(p.getUUID());
		if (!running && (now > until || (p.onGround() && now > until - SaiyanKitConfig.FALL_SAFE_TICKS + 3))) {
			FALL_SAFE.remove(p.getUUID());
			return;
		}
		p.resetFallDistance();
	}

	// ---------------------------------------------------------------- shared helpers

	static void begin(ServerPlayer p, Run r) {
		RUNS.put(p.getUUID(), r);
	}

	static void end(ServerPlayer p, Run r) {
		RUNS.remove(p.getUUID(), r);
	}

	static void fallSafeFor(ServerPlayer p, int ticks) {
		FALL_SAFE.put(p.getUUID(), p.level().getGameTime() + ticks);
		p.resetFallDistance();
	}

	static SaiyanForm form(Player p) {
		TestOverride o = OVERRIDES.get(p.getUUID());
		return o != null ? o.form() : Saiyan.form(p);
	}

	public static float kiOf(Player p) {
		TestOverride o = OVERRIDES.get(p.getUUID());
		return o != null ? o.ki()[0] : Saiyan.ki(p);
	}

	/** Spends Ki (the core's bar, or a test's stand-in). False and nothing spent if there isn't enough. */
	static boolean spend(ServerPlayer p, float amount) {
		TestOverride o = OVERRIDES.get(p.getUUID());
		if (o != null) {
			if (o.ki()[0] + 1.0e-3f < amount) {
				return false;
			}
			o.ki()[0] = Math.max(0f, o.ki()[0] - amount);
			return true;
		}
		return Saiyan.spendKi(p, amount);
	}

	/** Spends the move's Ki, or says there isn't enough. */
	static boolean needKi(ServerPlayer p, float amount) {
		if (spend(p, amount)) {
			return true;
		}
		say(p, "message.projecthero.saiyan.no_ki");
		return false;
	}

	/** Spends Ki only if there's enough, without a message (the Kamehameha's charge). */
	static boolean spendQuiet(ServerPlayer p, float amount) {
		if (!p.getAbilities().instabuild && OVERRIDES.get(p.getUUID()) == null && kiOf(p) + 1.0e-3f < amount) {
			return false;
		}
		return spend(p, amount);
	}

	static void cooldown(ServerPlayer p, String id, int ticks) {
		READY.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>()).put(id, p.level().getGameTime() + ticks);
	}

	static void say(ServerPlayer p, String key, Object... args) {
		p.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.GRAY), true);
	}

	static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	static void soundAt(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	/** One effect for the Saiyan and everyone who can see him. */
	static void fx(Entity near, int kind, int entity, Vec3 a, Vec3 b, float f, int rgb, int extra) {
		if (near.level().isClientSide) {
			return;
		}
		SaiyanKitFxPayload pl = new SaiyanKitFxPayload(kind, entity, a, b, f, rgb, extra);
		for (ServerPlayer viewer : PlayerLookup.tracking(near)) {
			send(viewer, pl);
		}
		if (near instanceof ServerPlayer self && !PlayerLookup.tracking(near).contains(self)) {
			send(self, pl);
		}
	}

	/** One effect for everyone near a point. */
	static void fxAt(ServerLevel level, Vec3 at, int kind, int entity, Vec3 a, Vec3 b, float f, int rgb, int extra) {
		SaiyanKitFxPayload pl = new SaiyanKitFxPayload(kind, entity, a, b, f, rgb, extra);
		for (ServerPlayer viewer : PlayerLookup.around(level, at, 96.0)) {
			send(viewer, pl);
		}
	}

	private static void send(ServerPlayer viewer, SaiyanKitFxPayload pl) {
		if (viewer.connection != null) {
			ServerPlayNetworking.send(viewer, pl);
		}
	}

	static void anim(ServerPlayer p, int anim) {
		fx(p, SaiyanKitFxPayload.ANIM, p.getId(), Vec3.ZERO, Vec3.ZERO, 0f, 0, anim);
	}

	static void impact(ServerPlayer p, LivingEntity t, float size) {
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.55, 0);
		fx(p, SaiyanKitFxPayload.IMPACT, t.getId(), c, p.getLookAngle(), size, kiColor(form(p)), 0);
	}

	/** The Ki colour of the form: blasts, the beam, impacts (Base a deep sky blue, Super Saiyan paler and brighter). */
	public static int kiColor(SaiyanForm f) {
		return f == SaiyanForm.BASE ? 0x4FB6FF : 0x8FE0FF;
	}

	static boolean isBoss(LivingEntity t) {
		return t.getMaxHealth() >= 300f || TitanCombat.isBoss(t);
	}

	/** One hit of base damage (the core scales it); knockback away from {@code from} and a lift (never on a boss). */
	static boolean strike(ServerPlayer p, LivingEntity t, Vec3 from, float base, double knockback, double lift) {
		if (t == p || !t.isAlive() || !HeroTargets.canHarm(p, t)) {
			return false;
		}
		float d = Math.max(0.5f, base);
		float before = t.getHealth() + t.getAbsorptionAmount();
		boolean dealt = AbilityHelpers.hurtBurst(p, t, p.level().damageSources().playerAttack(p), d);
		// the core's SaiyanDamage cancels a Saiyan's hit and re-issues it scaled, so hurt() reports false although it landed
		if (!dealt && !(t.getHealth() + t.getAbsorptionAmount() < before - 1.0e-4f) && t.isAlive()) {
			return false;
		}
		if (!isBoss(t)) {
			if (knockback > 0) {
				AbilityHelpers.knockbackFrom(t, from, knockback);
			}
			if (lift != 0) {
				AbilityHelpers.push(t, new Vec3(0, lift, 0));
			}
		}
		return true;
	}

	/** Who he would hit in front of him, nearest first: within {@code range}, inside a cone ({@code minDot} of the look). */
	static List<LivingEntity> inFront(ServerPlayer p, double range, double minDot) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : HeroTargets.harmable(p.level(), p, p.getBoundingBox().inflate(range))) {
			if (e == p || !e.isAlive()) {
				continue;
			}
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			Vec3 to = c.subtract(eye);
			if (to.length() - e.getBbWidth() * 0.5 > range) {
				continue;
			}
			if (to.lengthSqr() > 0.25 && to.normalize().dot(look) < minDot) {
				continue;
			}
			out.add(e);
		}
		out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		return out;
	}

	static LivingEntity nearestInFront(ServerPlayer p, double range) {
		List<LivingEntity> l = inFront(p, range, 0.5);
		return l.isEmpty() ? null : l.get(0);
	}

	/** Creatures whose box is within {@code radius} of {@code c}. */
	static List<LivingEntity> around(ServerPlayer p, Vec3 c, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : HeroTargets.harmable(p.level(), p, new AABB(c, c).inflate(radius + 2))) {
			if (e != p && e.isAlive() && Math.sqrt(AbilityHelpers.distanceSqToBox(e, c)) <= radius) {
				out.add(e);
			}
		}
		return out;
	}

	/** A mob that loses its target (Solar Flare). */
	static void dropTarget(Mob m) {
		m.setTarget(null);
		m.getNavigation().stop();
		m.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
	}
}
