package com.projecthero.mod.saiyan.moves;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.beamclash.BeamClash;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.SaiyanForm;
import com.projecthero.mod.saiyan.SaiyanSounds;
import com.projecthero.mod.saiyan.moves.network.SaiyanFxPayload;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: the Saiyan's moves. The core's ability manager calls {@link #press} with a move id and the key going down or up,
 * and {@link #tick} every tick (it is also ticked from a server tick of its own, once a tick at most, so a move keeps running
 * whoever calls it). Everything here is server-side; the look is sent to every viewer as {@link SaiyanFxPayload}s and drawn
 * by {@code SaiyanFxClient}.
 * <ul>
 *   <li>{@code combo} (left click): three quick hits, then a launching kick.</li>
 *   <li>{@code feint} (R): a stunning jab, then a guard-breaking kick.</li>
 *   <li>{@code ki_beam} (G): hold to charge, let go to fire; it beam-clashes ({@link BeamClash}).</li>
 *   <li>{@code spirit_orb} (Shift+G): a long, rooted charge, a giant ball thrown overhead; base form only.</li>
 *   <li>{@code barrage} (Z): rapid punches and a knockback finisher.</li>
 *   <li>{@code sky_combo} (Shift+Z): kick them up, chase, two kicks in the air, smash them down.</li>
 *   <li>{@code dragon_rush} (C): a wind-up, then a rushing punch; Super Saiyan 3 and up.</li>
 *   <li>{@code instant_step} (V): teleport to where he looks, behind a creature he looks at.</li>
 * </ul>
 * Every map here is dropped by {@link #clearSessionState} and {@link #forget}.
 */
public final class SaiyanMoves {
	public static final String COMBO = "combo";
	public static final String FEINT = "feint";
	public static final String KI_BEAM = "ki_beam";
	public static final String SPIRIT_ORB = "spirit_orb";
	public static final String BARRAGE = "barrage";
	public static final String SKY_COMBO = "sky_combo";
	public static final String DRAGON_RUSH = "dragon_rush";
	public static final String INSTANT_STEP = "instant_step";
	public static final String[] IDS = { COMBO, FEINT, KI_BEAM, SPIRIT_ORB, BARRAGE, SKY_COMBO, DRAGON_RUSH, INSTANT_STEP };

	/** Pose ids sent to the client (SaiyanMovePose). */
	public static final int ANIM_JAB_R = 1, ANIM_JAB_L = 2, ANIM_COMBO_KICK = 3, ANIM_FEINT_JAB = 4, ANIM_FEINT_KICK = 5, ANIM_BEAM_CHARGE = 6,
			ANIM_BEAM_FIRE = 7, ANIM_ORB_CHARGE = 8, ANIM_ORB_THROW = 9, ANIM_BARRAGE = 10, ANIM_SKY_KICK = 11, ANIM_SKY_SMASH = 12,
			ANIM_RUSH_WINDUP = 13, ANIM_RUSH = 14, ANIM_STEP = 15, ANIM_NONE = 0;

	/** A move in progress (one at a time; the combo and a flying orb are tracked apart). */
	static final class Run {
		final String id;
		final long start;
		/** Ki beam / orb: the key is still held (charging). */
		boolean held = true;
		/** Ki beam / orb: when the charge ended and the release began (-1: still charging). */
		long fired = -1;
		float charge;
		LivingEntity target;
		Vec3 dir = Vec3.ZERO;
		int hits;
		boolean done;

		Run(String id, long start) {
			this.id = id;
			this.start = start;
		}
	}

	/** A thrown Spirit Orb in flight. */
	static final class Orb {
		final int id;
		final UUID owner;
		final ServerLevel level;
		Vec3 pos;
		final Vec3 vel;
		final float charge;
		int age;

		Orb(int id, UUID owner, ServerLevel level, Vec3 pos, Vec3 vel, float charge) {
			this.id = id;
			this.owner = owner;
			this.level = level;
			this.pos = pos;
			this.vel = vel;
			this.charge = charge;
		}
	}

	/** A test's stand-in for the core's form and Ki (never set in play). */
	public record TestOverride(SaiyanForm form, float[] ki) {
	}

	private static final Map<UUID, Run> RUNS = new ConcurrentHashMap<>();
	private static final Map<UUID, Map<String, Long>> READY = new ConcurrentHashMap<>();
	/** step (0-3), last hit game time. */
	private static final Map<UUID, long[]> COMBO_STATE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> TICKED = new ConcurrentHashMap<>();
	private static final Map<UUID, TestOverride> OVERRIDES = new ConcurrentHashMap<>();
	private static final List<Orb> ORBS = new ArrayList<>();
	private static int nextOrb = 1;

	private SaiyanMoves() {
	}

	// ---------------------------------------------------------------- lifecycle

	public static void initialize() {
		PayloadTypeRegistry.playS2C().register(SaiyanFxPayload.TYPE, SaiyanFxPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(SaiyanMoves::serverTick);
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> forget(h.player.getUUID()));
	}

	public static void clearSessionState() {
		RUNS.clear();
		READY.clear();
		COMBO_STATE.clear();
		TICKED.clear();
		OVERRIDES.clear();
		synchronized (ORBS) {
			ORBS.clear();
		}
		nextOrb = 1;
	}

	public static void forget(UUID id) {
		RUNS.remove(id);
		READY.remove(id);
		COMBO_STATE.remove(id);
		TICKED.remove(id);
		OVERRIDES.remove(id);
	}

	private static void serverTick(MinecraftServer server) {
		for (UUID id : new ArrayList<>(RUNS.keySet())) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) {
				RUNS.remove(id);
				continue;
			}
			tick(p);
		}
		tickOrbs(server);
	}

	// ---------------------------------------------------------------- the core's side

	/** A test sets the form and Ki the moves see for this player (the Ki array is spent from). Null clears it. */
	public static void overrideForTest(Player p, SaiyanForm form, float ki) {
		if (form == null) {
			OVERRIDES.remove(p.getUUID());
		} else {
			OVERRIDES.put(p.getUUID(), new TestOverride(form, new float[] { ki }));
		}
	}

	static SaiyanForm form(Player p) {
		TestOverride o = OVERRIDES.get(p.getUUID());
		return o != null ? o.form() : Saiyan.form(p);
	}

	public static float kiOf(Player p) {
		TestOverride o = OVERRIDES.get(p.getUUID());
		return o != null ? o.ki()[0] : Saiyan.ki(p);
	}

	static boolean spend(ServerPlayer p, float amount) {
		TestOverride o = OVERRIDES.get(p.getUUID());
		if (o != null) {
			if (o.ki()[0] < amount) {
				return false;
			}
			o.ki()[0] -= amount;
			return true;
		}
		return Saiyan.spendKi(p, amount);
	}

	/** Ticks of cooldown left on a move (0: ready). */
	public static int cooldownRemaining(Player p, String id) {
		Map<String, Long> m = READY.get(p.getUUID());
		if (m == null) {
			return 0;
		}
		Long at = m.get(id);
		return at == null ? 0 : (int) Math.max(0L, at - p.level().getGameTime());
	}

	public static int maxCooldown(String id) {
		return switch (id) {
			case COMBO -> SaiyanMoveConfig.COMBO_RECOVERY;
			case FEINT -> SaiyanMoveConfig.FEINT_COOLDOWN;
			case KI_BEAM -> SaiyanMoveConfig.BEAM_COOLDOWN;
			case SPIRIT_ORB -> SaiyanMoveConfig.ORB_COOLDOWN;
			case BARRAGE -> SaiyanMoveConfig.BARRAGE_COOLDOWN;
			case SKY_COMBO -> SaiyanMoveConfig.SKY_COOLDOWN;
			case DRAGON_RUSH -> SaiyanMoveConfig.RUSH_COOLDOWN;
			case INSTANT_STEP -> SaiyanMoveConfig.STEP_COOLDOWN;
			default -> 1;
		};
	}

	/** The move running now ({@code null}: none). */
	public static String running(Player p) {
		Run r = RUNS.get(p.getUUID());
		return r == null ? null : r.id;
	}

	/** Ki beam / orb: the charge 0..1 while charging or firing (0 when neither). */
	public static float charge(Player p) {
		Run r = RUNS.get(p.getUUID());
		return r == null ? 0f : r.charge;
	}

	private static void cooldown(ServerPlayer p, String id, int ticks) {
		READY.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>()).put(id, p.level().getGameTime() + ticks);
	}

	private static void say(ServerPlayer p, String key, Object... args) {
		p.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.GRAY), true);
	}

	/**
	 * The key for {@code id} went down ({@code down}) or up. Charged moves (Ki Beam, Spirit Orb) charge while held and fire on
	 * release; everything else acts on the press and ignores the release.
	 */
	public static void press(ServerPlayer p, String id, boolean down) {
		if (!p.isAlive() || p.isSpectator()) {
			return;
		}
		Run cur = RUNS.get(p.getUUID());
		if (!down) {
			if (cur != null && cur.id.equals(id) && cur.held) {
				release(p, cur);
			}
			return;
		}
		if (id.equals(COMBO)) {
			if (cur == null) {
				combo(p);
			}
			return;
		}
		if (cur != null) {
			return; // one move at a time
		}
		int cd = cooldownRemaining(p, id);
		if (cd > 0) {
			say(p, "message.projecthero.saiyan.cooldown", Component.translatable("projecthero.saiyan.move." + id),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return;
		}
		switch (id) {
			case FEINT -> feint(p);
			case KI_BEAM -> beamStart(p);
			case SPIRIT_ORB -> orbStart(p);
			case BARRAGE -> barrage(p);
			case SKY_COMBO -> sky(p);
			case DRAGON_RUSH -> rushStart(p);
			case INSTANT_STEP -> step(p);
			default -> {
			}
		}
	}

	/** Every tick (idempotent within a game tick). */
	public static void tick(ServerPlayer p) {
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
			end(p, r);
			return;
		}
		long age = now - r.start;
		switch (r.id) {
			case FEINT -> tickFeint(p, r, age);
			case KI_BEAM -> tickBeam(p, r, now);
			case SPIRIT_ORB -> tickOrbCharge(p, r, age);
			case BARRAGE -> tickBarrage(p, r, age);
			case SKY_COMBO -> tickSky(p, r, age);
			case DRAGON_RUSH -> tickRush(p, r, age);
			default -> end(p, r);
		}
	}

	private static void begin(ServerPlayer p, Run r) {
		RUNS.put(p.getUUID(), r);
	}

	private static void end(ServerPlayer p, Run r) {
		r.done = true;
		RUNS.remove(p.getUUID(), r);
	}

	// ---------------------------------------------------------------- shared helpers

	static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	static void soundAt(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	static void fx(Entity near, int kind, int entity, Vec3 a, Vec3 b, float f, int rgb, int extra) {
		if (near.level().isClientSide) {
			return;
		}
		SaiyanFxPayload pl = new SaiyanFxPayload(kind, entity, a, b, f, rgb, extra);
		for (ServerPlayer viewer : PlayerLookup.tracking(near)) {
			ServerPlayNetworking.send(viewer, pl);
		}
		if (near instanceof ServerPlayer self && !PlayerLookup.tracking(near).contains(self) && self.connection != null) {
			ServerPlayNetworking.send(self, pl);
		}
	}

	static void fxAt(ServerLevel level, Vec3 at, int kind, int entity, Vec3 a, Vec3 b, float f, int rgb, int extra) {
		SaiyanFxPayload pl = new SaiyanFxPayload(kind, entity, a, b, f, rgb, extra);
		for (ServerPlayer viewer : PlayerLookup.around(level, at, 96.0)) {
			ServerPlayNetworking.send(viewer, pl);
		}
	}

	static void anim(ServerPlayer p, int anim) {
		fx(p, SaiyanFxPayload.ANIM, p.getId(), Vec3.ZERO, Vec3.ZERO, 0f, 0, anim);
	}

	static void impact(ServerPlayer p, LivingEntity t, float size, int rgb) {
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.55, 0);
		fx(p, SaiyanFxPayload.IMPACT, t.getId(), c, p.getLookAngle(), size, rgb, 0);
	}

	/** The Ki colour of the form: the beam, the impacts, the orb's rim. */
	public static int kiColor(SaiyanForm f) {
		return switch (f) {
			case GOD -> 0xFF5A50;
			case BLUE -> 0x58D6FF;
			case BLUE_OVERDRIVE -> 0x7AC0FF;
			case INSTINCT -> 0xE6F0FF;
			case SSJ, SSJ2, SSJ3 -> 0x8FE0FF;
			default -> 0x6FCBFF;
		};
	}

	static boolean isBoss(LivingEntity t) {
		return t.getMaxHealth() >= SaiyanMoveConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(t);
	}

	/** One hit: base damage times the form, capped on bosses; knockback away from {@code from} and a lift. */
	static boolean strike(ServerPlayer p, LivingEntity t, Vec3 from, float base, double knockback, double lift) {
		if (t == p || !t.isAlive() || !HeroTargets.canHarm(p, t)) {
			return false;
		}
		float d = base * form(p).damageMult();
		boolean boss = isBoss(t);
		if (boss) {
			d = Math.min(d, t.getMaxHealth() * SaiyanMoveConfig.BOSS_MAX_FRACTION);
		}
		if (!AbilityHelpers.hurtBurst(p, t, p.level().damageSources().playerAttack(p), Math.max(0.5f, d))) {
			return false;
		}
		if (!boss) {
			if (knockback > 0) {
				AbilityHelpers.knockbackFrom(t, from, knockback);
			}
			if (lift != 0) {
				AbilityHelpers.push(t, new Vec3(0, lift, 0));
			}
		}
		return true;
	}

	/** Who he would hit in front of him, nearest first: within {@code range}, inside a cone of about 60 degrees. */
	static List<LivingEntity> inFront(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		AABB box = p.getBoundingBox().inflate(range);
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : HeroTargets.harmable(p.level(), p, box)) {
			if (e == p || !e.isAlive()) {
				continue;
			}
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			Vec3 to = c.subtract(eye);
			double dist = to.length() - e.getBbWidth() * 0.5;
			if (dist > range) {
				continue;
			}
			if (to.lengthSqr() > 0.25 && to.normalize().dot(look) < 0.5) {
				continue;
			}
			out.add(e);
		}
		out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		return out;
	}

	static LivingEntity nearestInFront(ServerPlayer p, double range) {
		List<LivingEntity> l = inFront(p, range);
		return l.isEmpty() ? null : l.get(0);
	}

	private static boolean needKi(ServerPlayer p, float ki) {
		if (spend(p, ki)) {
			return true;
		}
		say(p, "message.projecthero.saiyan.no_ki");
		return false;
	}

	// ---------------------------------------------------------------- left click: Saiyan combo

	private static void combo(ServerPlayer p) {
		long now = p.level().getGameTime();
		if (cooldownRemaining(p, COMBO) > 0) {
			return;
		}
		long[] st = COMBO_STATE.computeIfAbsent(p.getUUID(), k -> new long[] { 0, -100 });
		if (now - st[1] < SaiyanMoveConfig.COMBO_STEP_TICKS) {
			return;
		}
		if (now - st[1] > SaiyanMoveConfig.COMBO_RESET_TICKS) {
			st[0] = 0;
		}
		int step = (int) st[0];
		st[1] = now;
		LivingEntity t = nearestInFront(p, SaiyanMoveConfig.COMBO_RANGE);
		int rgb = kiColor(form(p));
		if (step < 3) {
			anim(p, step == 1 ? ANIM_JAB_L : ANIM_JAB_R);
			p.swing(step == 1 ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND, true);
			if (t != null && strike(p, t, p.position(), SaiyanMoveConfig.COMBO_HIT_DAMAGE, 0.15, 0.0)) {
				impact(p, t, 0.6f, rgb);
				sound(p, SaiyanSounds.PUNCH_HIT_LIGHT, 0.6f, 1.0f + step * 0.08f);
			} else {
				sound(p, SaiyanSounds.KI_DASH, 0.25f, 1.6f);
			}
			st[0] = step + 1;
		} else {
			anim(p, ANIM_COMBO_KICK);
			if (t != null && strike(p, t, p.position(), SaiyanMoveConfig.COMBO_KICK_DAMAGE, SaiyanMoveConfig.COMBO_KICK_KNOCKBACK,
					SaiyanMoveConfig.COMBO_KICK_LIFT)) {
				impact(p, t, 1.2f, rgb);
				sound(p, SaiyanSounds.PUNCH_HIT_HEAVY, 0.75f, 0.9f);
			} else {
				sound(p, SaiyanSounds.KI_DASH, 0.4f, 1.2f);
			}
			st[0] = 0;
			cooldown(p, COMBO, SaiyanMoveConfig.COMBO_RECOVERY);
		}
	}

	/** The combo's next hit (0-3) for a test or the HUD. */
	public static int comboStep(Player p) {
		long[] st = COMBO_STATE.get(p.getUUID());
		return st == null ? 0 : (int) st[0];
	}

	// ---------------------------------------------------------------- R: Feint Strike

	private static void feint(ServerPlayer p) {
		LivingEntity t = nearestInFront(p, SaiyanMoveConfig.FEINT_RANGE);
		if (!needKi(p, SaiyanMoveConfig.FEINT_KI)) {
			return;
		}
		cooldown(p, FEINT, SaiyanMoveConfig.FEINT_COOLDOWN);
		Run r = new Run(FEINT, p.level().getGameTime());
		r.target = t;
		begin(p, r);
		anim(p, ANIM_FEINT_JAB);
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		if (t != null && strike(p, t, p.position(), SaiyanMoveConfig.FEINT_JAB_DAMAGE, 0.05, 0.0)) {
			t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SaiyanMoveConfig.FEINT_STUN_TICKS, 6, false, false, true));
			t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SaiyanMoveConfig.FEINT_STUN_TICKS, 2, false, false, true));
			impact(p, t, 0.8f, kiColor(form(p)));
			sound(p, SaiyanSounds.PUNCH_HIT_LIGHT, 0.6f, 0.8f);
		}
	}

	private static void tickFeint(ServerPlayer p, Run r, long age) {
		if (age == SaiyanMoveConfig.FEINT_KICK_AT) {
			anim(p, ANIM_FEINT_KICK);
			LivingEntity t = r.target != null && r.target.isAlive() && r.target.distanceTo(p) < SaiyanMoveConfig.FEINT_RANGE + 1.5 ? r.target
					: nearestInFront(p, SaiyanMoveConfig.FEINT_RANGE);
			if (t != null) {
				boolean guarding = t instanceof Player tp && tp.isBlocking();
				if (guarding) {
					// the guard break: the shield goes down, and the kick lands through it
					((Player) t).getCooldowns().addCooldown(((Player) t).getUseItem().getItem(), SaiyanMoveConfig.FEINT_SHIELD_BREAK_TICKS);
					((Player) t).stopUsingItem();
					p.level().broadcastEntityEvent(t, (byte) 30);
				}
				if (strike(p, t, p.position(), SaiyanMoveConfig.FEINT_KICK_DAMAGE, 0.7, 0.2)) {
					impact(p, t, 1.4f, kiColor(form(p)));
					sound(p, SaiyanSounds.PUNCH_HIT_HEAVY, 0.75f, 0.75f);
				}
			}
		}
		if (age >= SaiyanMoveConfig.FEINT_KICK_AT + 4) {
			end(p, r);
		}
	}

	// ---------------------------------------------------------------- G: Ki Beam

	private static void beamStart(ServerPlayer p) {
		if (!needKi(p, SaiyanMoveConfig.BEAM_KI)) {
			return;
		}
		Run r = new Run(KI_BEAM, p.level().getGameTime());
		begin(p, r);
		anim(p, ANIM_BEAM_CHARGE);
		fx(p, SaiyanFxPayload.BEAM_CHARGE, p.getId(), Vec3.ZERO, Vec3.ZERO, SaiyanMoveConfig.BEAM_MAX_CHARGE, kiColor(form(p)), 0);
		sound(p, SaiyanSounds.BEAM_CHARGE, 0.8f, 1.0f);
	}

	private static void release(ServerPlayer p, Run r) {
		r.held = false;
	}

	/** Where the beam starts: just in front of his cupped hands at chest height. */
	public static Vec3 beamFrom(Player p) {
		return p.getEyePosition().add(0, -0.45, 0).add(p.getLookAngle().scale(0.7));
	}

	/** The beam this tick: its start, where it stops (the first block or creature), and the creature it stops on. */
	static Vec3[] beamSegment(ServerPlayer p, double range, LivingEntity[] hit) {
		Vec3 from = beamFrom(p);
		Vec3 far = from.add(p.getLookAngle().scale(range));
		BlockHitResult bhr = p.level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		// the nearest creature whose box (a little fattened: the beam is thick) the line passes through
		double best = from.distanceToSqr(end);
		for (LivingEntity e : HeroTargets.harmable(p.level(), p, new AABB(from, end).inflate(1.0))) {
			if (e == p || !e.isAlive()) {
				continue;
			}
			java.util.Optional<Vec3> at = e.getBoundingBox().inflate(0.35).clip(from, end);
			if (at.isPresent() && from.distanceToSqr(at.get()) < best) {
				best = from.distanceToSqr(at.get());
				hit[0] = e;
			}
		}
		if (hit[0] != null) {
			end = from.add(p.getLookAngle().scale(Math.sqrt(best)));
		}
		return new Vec3[] { from, end };
	}

	private static void tickBeam(ServerPlayer p, Run r, long now) {
		long age = now - r.start;
		SaiyanForm f = form(p);
		int rgb = kiColor(f);
		if (r.fired < 0) {
			r.charge = Math.min(1f, age / (float) SaiyanMoveConfig.BEAM_MAX_CHARGE);
			// charging: a slow walk at most
			p.setDeltaMovement(p.getDeltaMovement().multiply(0.4, 1.0, 0.4));
			p.hurtMarked = true;
			if (age % 6 == 0) {
				level(p).sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, beamFrom(p).x, beamFrom(p).y, beamFrom(p).z, 2, 0.1, 0.1,
						0.1, 0.02);
			}
			boolean full = age >= SaiyanMoveConfig.BEAM_MAX_CHARGE;
			if ((!r.held && age >= SaiyanMoveConfig.BEAM_MIN_CHARGE) || full) {
				r.fired = now;
				anim(p, ANIM_BEAM_FIRE);
				fx(p, SaiyanFxPayload.BEAM_FIRE, p.getId(), Vec3.ZERO, Vec3.ZERO, r.charge, rgb, SaiyanMoveConfig.BEAM_FIRE_TICKS);
				// the firing roar is a looping sound on every viewer's client (SaiyanFxClient), held while the beam lives
			}
			return;
		}
		long fireAge = now - r.fired;
		if (fireAge >= SaiyanMoveConfig.BEAM_FIRE_TICKS || !spend(p, SaiyanMoveConfig.BEAM_KI_PER_TICK)) {
			beamEnd(p, r);
			return;
		}
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.2, 1.0, 0.2));
		p.hurtMarked = true;
		BeamClash.report(p, beamFrom(p), p.getLookAngle(), SaiyanMoveConfig.BEAM_RANGE, beamPower(f, r.charge), rgb);
		LivingEntity[] hit = new LivingEntity[1];
		Vec3[] seg = beamSegment(p, SaiyanMoveConfig.BEAM_RANGE, hit);
		if (fireAge % SaiyanMoveConfig.BEAM_HIT_EVERY == 0 && !BeamClash.inClash(p)) {
			float base = SaiyanMoveConfig.BEAM_HIT_DAMAGE * (1f + r.charge);
			if (hit[0] != null && strike(p, hit[0], seg[0], base, 0.25, 0.0)) {
				r.hits++;
			}
			// the splash where it lands: anyone right next to the end point takes a little too
			for (LivingEntity e : HeroTargets.harmable(p.level(), p, new AABB(seg[1], seg[1]).inflate(1.3))) {
				if (e != hit[0] && e != p) {
					strike(p, e, seg[1], base * 0.4f, 0.15, 0.0);
				}
			}
			if (fireAge % (SaiyanMoveConfig.BEAM_HIT_EVERY * 3) == 0) {
				soundAt(level(p), seg[1], SaiyanSounds.BEAM_IMPACT, 0.6f, 1.2f);
			}
		}
	}

	/** How hard the beam pushes in a clash: an ordinary beam is 1. */
	public static float beamPower(SaiyanForm f, float charge) {
		return f.damageMult() * (0.6f + 0.4f * charge);
	}

	private static void beamEnd(ServerPlayer p, Run r) {
		fx(p, SaiyanFxPayload.BEAM_END, p.getId(), Vec3.ZERO, Vec3.ZERO, 0f, 0, 0);
		anim(p, ANIM_NONE);
		cooldown(p, KI_BEAM, SaiyanMoveConfig.BEAM_COOLDOWN);
		end(p, r);
	}

	// ---------------------------------------------------------------- Shift+G: Spirit Orb

	private static void orbStart(ServerPlayer p) {
		if (form(p) != SaiyanForm.BASE) {
			say(p, "message.projecthero.saiyan.orb_base_only");
			return;
		}
		if (kiOf(p) < SaiyanMoveConfig.ORB_KI && !p.getAbilities().instabuild) {
			say(p, "message.projecthero.saiyan.no_ki");
			return;
		}
		Run r = new Run(SPIRIT_ORB, p.level().getGameTime());
		r.dir = p.position(); // where he is rooted
		begin(p, r);
		anim(p, ANIM_ORB_CHARGE);
		fx(p, SaiyanFxPayload.ORB_CHARGE, p.getId(), Vec3.ZERO, Vec3.ZERO, SaiyanMoveConfig.ORB_MAX_CHARGE, kiColor(SaiyanForm.BASE), 0);
		sound(p, SaiyanSounds.ORB_CHARGE, 1.0f, 0.8f);
	}

	/** Where the orb hangs while charging: high over his raised hands. */
	public static Vec3 orbHold(Player p, float charge) {
		float size = SaiyanMoveConfig.ORB_SIZE_MIN + (SaiyanMoveConfig.ORB_SIZE_MAX - SaiyanMoveConfig.ORB_SIZE_MIN) * charge;
		return p.position().add(0, 2.6 + size, 0);
	}

	private static void tickOrbCharge(ServerPlayer p, Run r, long age) {
		if (form(p) != SaiyanForm.BASE) {
			cancelOrb(p, r);
			return;
		}
		r.charge = Math.min(1f, age / (float) SaiyanMoveConfig.ORB_MAX_CHARGE);
		// rooted: walking away pulls him back to where he stood (no slowness: it would zoom his view)
		if (p.position().distanceToSqr(r.dir) > 0.04) {
			p.teleportTo(r.dir.x, r.dir.y, r.dir.z);
		}
		p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
		p.hurtMarked = true;
		if (age % 20 == 0 && age > 0) {
			sound(p, SaiyanSounds.ORB_CHARGE, 0.6f, 0.8f + 0.3f * r.charge);
		}
		boolean full = age >= SaiyanMoveConfig.ORB_MAX_CHARGE;
		if ((!r.held && age >= SaiyanMoveConfig.ORB_MIN_CHARGE) || full) {
			throwOrb(p, r);
		} else if (!r.held) {
			cancelOrb(p, r); // let go too soon: the energy fades, nothing spent
		}
	}

	private static void cancelOrb(ServerPlayer p, Run r) {
		fx(p, SaiyanFxPayload.ORB_CANCEL, p.getId(), Vec3.ZERO, Vec3.ZERO, 0f, 0, 0);
		anim(p, ANIM_NONE);
		end(p, r);
	}

	private static void throwOrb(ServerPlayer p, Run r) {
		if (!spend(p, SaiyanMoveConfig.ORB_KI)) {
			say(p, "message.projecthero.saiyan.no_ki");
			cancelOrb(p, r);
			return;
		}
		Vec3 at = orbHold(p, r.charge);
		Vec3 aim = p.getLookAngle();
		Vec3 vel = aim.scale(SaiyanMoveConfig.ORB_SPEED);
		Orb o = new Orb(nextOrb++, p.getUUID(), level(p), at, vel, r.charge);
		synchronized (ORBS) {
			ORBS.add(o);
		}
		anim(p, ANIM_ORB_THROW);
		fx(p, SaiyanFxPayload.ORB_FLY, p.getId(), at, vel, orbSize(r.charge), kiColor(SaiyanForm.BASE), o.id);
		sound(p, SaiyanSounds.KI_DASH, 1.0f, 0.5f);
		cooldown(p, SPIRIT_ORB, SaiyanMoveConfig.ORB_COOLDOWN);
		end(p, r);
	}

	public static float orbSize(float charge) {
		return SaiyanMoveConfig.ORB_SIZE_MIN + (SaiyanMoveConfig.ORB_SIZE_MAX - SaiyanMoveConfig.ORB_SIZE_MIN) * charge;
	}

	private static void tickOrbs(MinecraftServer server) {
		List<Orb> copy;
		synchronized (ORBS) {
			if (ORBS.isEmpty()) {
				return;
			}
			copy = new ArrayList<>(ORBS);
		}
		for (Orb o : copy) {
			o.age++;
			Vec3 next = o.pos.add(o.vel);
			ServerPlayer owner = server.getPlayerList().getPlayer(o.owner);
			boolean boom = o.age >= SaiyanMoveConfig.ORB_LIFE;
			float size = orbSize(o.charge);
			BlockHitResult bhr = o.level.clip(new ClipContext(o.pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
					net.minecraft.world.phys.shapes.CollisionContext.empty()));
			if (bhr.getType() != HitResult.Type.MISS) {
				next = bhr.getLocation();
				boom = true;
			}
			if (!boom) {
				AABB box = new AABB(next, next).inflate(size * 0.6);
				for (LivingEntity e : o.level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.getUUID().equals(o.owner)
						&& (owner == null || HeroTargets.canHarm(owner, e)))) {
					boom = true;
					break;
				}
			}
			o.pos = next;
			if (boom) {
				explodeOrb(o, owner);
				synchronized (ORBS) {
					ORBS.remove(o);
				}
			}
		}
	}

	private static void explodeOrb(Orb o, ServerPlayer owner) {
		float radius = SaiyanMoveConfig.ORB_MIN_RADIUS + (SaiyanMoveConfig.ORB_MAX_RADIUS - SaiyanMoveConfig.ORB_MIN_RADIUS) * o.charge;
		float dmg = SaiyanMoveConfig.ORB_MIN_DAMAGE + (SaiyanMoveConfig.ORB_MAX_DAMAGE - SaiyanMoveConfig.ORB_MIN_DAMAGE) * o.charge;
		fxAt(o.level, o.pos, SaiyanFxPayload.ORB_BOOM, owner == null ? -1 : owner.getId(), o.pos, Vec3.ZERO, radius, kiColor(SaiyanForm.BASE), o.id);
		soundAt(o.level, o.pos, SaiyanSounds.ORB_EXPLODE, 0.85f, 0.7f);
		o.level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER, o.pos.x, o.pos.y, o.pos.z, 1, 0, 0, 0, 0);
		if (owner == null) {
			return;
		}
		for (LivingEntity e : HeroTargets.harmable(o.level, owner, new AABB(o.pos, o.pos).inflate(radius))) {
			if (e == owner) {
				continue;
			}
			double d = e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(o.pos);
			if (d > radius + e.getBbWidth() * 0.5) {
				continue;
			}
			float falloff = (float) (1.0 - 0.5 * Math.min(1.0, d / radius));
			// base form only: the form multiplier is always 1, kept for consistency
			strike(owner, e, o.pos, dmg * falloff, 1.6 * falloff, 0.5 * falloff);
		}
	}

	/** Thrown orbs in flight (tests). */
	public static int orbsInFlight() {
		synchronized (ORBS) {
			return ORBS.size();
		}
	}

	// ---------------------------------------------------------------- Z: Barrage

	private static void barrage(ServerPlayer p) {
		if (!needKi(p, SaiyanMoveConfig.BARRAGE_KI)) {
			return;
		}
		cooldown(p, BARRAGE, SaiyanMoveConfig.BARRAGE_COOLDOWN);
		begin(p, new Run(BARRAGE, p.level().getGameTime()));
		anim(p, ANIM_BARRAGE);
		sound(p, SaiyanSounds.BARRAGE_RAPID, 0.8f, 1.0f);
	}

	private static void tickBarrage(ServerPlayer p, Run r, long age) {
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 1.0, 0.3));
		p.hurtMarked = true;
		int rgb = kiColor(form(p));
		if (age < SaiyanMoveConfig.BARRAGE_TICKS) {
			if (age % SaiyanMoveConfig.BARRAGE_HIT_EVERY == 0) {
				p.swing(age % 4 == 0 ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND, true);
				for (LivingEntity t : inFront(p, SaiyanMoveConfig.BARRAGE_RANGE)) {
					if (strike(p, t, p.position(), SaiyanMoveConfig.BARRAGE_HIT_DAMAGE, 0.0, 0.0)) {
						r.hits++;
						if (age % 4 == 0) {
							impact(p, t, 0.45f, rgb);
						}
						// held in place: the punches keep them in front of him
						t.setDeltaMovement(t.getDeltaMovement().multiply(0.2, 0.6, 0.2));
					}
				}
			}
			if (age % 8 == 0) {
				sound(p, SaiyanSounds.BARRAGE_RAPID, 0.7f, 0.95f + p.getRandom().nextFloat() * 0.1f);
			}
			return;
		}
		// the finisher
		anim(p, ANIM_COMBO_KICK);
		for (LivingEntity t : inFront(p, SaiyanMoveConfig.BARRAGE_RANGE + 0.5)) {
			if (strike(p, t, p.position(), SaiyanMoveConfig.BARRAGE_FINISHER_DAMAGE, SaiyanMoveConfig.BARRAGE_KNOCKBACK, 0.35)) {
				impact(p, t, 1.4f, rgb);
			}
		}
		sound(p, SaiyanSounds.PUNCH_HIT_HEAVY, 0.75f, 0.8f);
		end(p, r);
	}

	// ---------------------------------------------------------------- Shift+Z: Sky Combo

	private static void sky(ServerPlayer p) {
		LivingEntity t = nearestInFront(p, SaiyanMoveConfig.SKY_RANGE);
		if (t == null) {
			cooldown(p, SKY_COMBO, SaiyanMoveConfig.SKY_WHIFF_COOLDOWN);
			anim(p, ANIM_SKY_KICK);
			say(p, "message.projecthero.saiyan.no_target");
			return;
		}
		if (!needKi(p, SaiyanMoveConfig.SKY_KI)) {
			return;
		}
		cooldown(p, SKY_COMBO, SaiyanMoveConfig.SKY_COOLDOWN);
		Run r = new Run(SKY_COMBO, p.level().getGameTime());
		r.target = t;
		begin(p, r);
		anim(p, ANIM_SKY_KICK);
		if (strike(p, t, p.position(), SaiyanMoveConfig.SKY_LAUNCH_DAMAGE, 0.0, 0.0)) {
			impact(p, t, 1.0f, kiColor(form(p)));
			sound(p, SaiyanSounds.PUNCH_HIT_HEAVY, 0.75f, 1.1f);
		}
		if (!isBoss(t)) {
			t.setDeltaMovement(0, 1.3, 0);
			t.hurtMarked = true;
		}
		// he chases them up
		AbilityHelpers.launchSelf(p, new Vec3(0, 1.25, 0));
		fx(p, SaiyanFxPayload.RUSH, p.getId(), Vec3.ZERO, Vec3.ZERO, 0f, kiColor(form(p)), 10);
	}

	private static void tickSky(ServerPlayer p, Run r, long age) {
		LivingEntity t = r.target;
		p.resetFallDistance();
		if (t == null || !t.isAlive() || t.level() != p.level() || age > SaiyanMoveConfig.SKY_MAX) {
			end(p, r);
			return;
		}
		int rgb = kiColor(form(p));
		boolean boss = isBoss(t);
		if (age > 4 && age < SaiyanMoveConfig.SKY_SMASH) {
			// hang them both in the air: he holds just below and in front of them
			if (!boss) {
				t.setDeltaMovement(t.getDeltaMovement().multiply(0.5, 0.0, 0.5).add(0, age < 8 ? 0.15 : 0.02, 0));
				t.hurtMarked = true;
				t.resetFallDistance();
			}
			Vec3 want = t.position().add(0, -0.6, 0).subtract(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.4));
			AbilityHelpers.launchSelf(p, want.subtract(p.position()).scale(0.5));
		}
		if (age == SaiyanMoveConfig.SKY_AIR_1 || age == SaiyanMoveConfig.SKY_AIR_2) {
			anim(p, ANIM_SKY_KICK);
			if (strike(p, t, p.position(), SaiyanMoveConfig.SKY_AIR_DAMAGE, 0.0, 0.0)) {
				impact(p, t, 0.9f, rgb);
				sound(p, SaiyanSounds.PUNCH_HIT_LIGHT, 0.6f, 1.2f);
			}
		}
		if (age == SaiyanMoveConfig.SKY_SMASH) {
			anim(p, ANIM_SKY_SMASH);
			if (strike(p, t, p.position(), SaiyanMoveConfig.SKY_SMASH_DAMAGE, 0.0, 0.0)) {
				impact(p, t, 1.6f, rgb);
				sound(p, SaiyanSounds.PUNCH_HIT_HEAVY, 0.75f, 0.7f);
			}
			if (!boss) {
				t.setDeltaMovement(0, -2.6, 0);
				t.hurtMarked = true;
			}
			AbilityHelpers.launchSelf(p, new Vec3(0, -0.6, 0));
			r.hits = 1; // smashed: wait for the landing
		}
		if (r.hits == 1 && age > SaiyanMoveConfig.SKY_SMASH + 1 && (t.onGround() || t.isInWater() || age >= SaiyanMoveConfig.SKY_MAX)) {
			// the shockwave where they land
			Vec3 at = t.position();
			fxAt(level(p), at, SaiyanFxPayload.SHOCKWAVE, p.getId(), at, Vec3.ZERO, (float) SaiyanMoveConfig.SKY_SHOCKWAVE_RADIUS, rgb, 0);
			soundAt(level(p), at, SaiyanSounds.BEAM_IMPACT, 1.2f, 0.6f);
			for (LivingEntity e : HeroTargets.harmable(p.level(), p, new AABB(at, at).inflate(SaiyanMoveConfig.SKY_SHOCKWAVE_RADIUS))) {
				if (e != p && e != t) {
					strike(p, e, at, SaiyanMoveConfig.SKY_SHOCKWAVE_DAMAGE, 0.8, 0.3);
				}
			}
			level(p).sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, 0.6, 0.1, 0.6, 0);
			end(p, r);
		}
	}

	// ---------------------------------------------------------------- C: Dragon Rush

	/** Dragon Rush needs Super Saiyan 3 or a stronger form. */
	public static boolean rushAllowed(SaiyanForm f) {
		return f.ordinal() >= SaiyanForm.SSJ3.ordinal();
	}

	private static void rushStart(ServerPlayer p) {
		if (!rushAllowed(form(p))) {
			say(p, "message.projecthero.saiyan.rush_ssj3");
			return;
		}
		if (!needKi(p, SaiyanMoveConfig.RUSH_KI)) {
			return;
		}
		cooldown(p, DRAGON_RUSH, SaiyanMoveConfig.RUSH_COOLDOWN);
		Run r = new Run(DRAGON_RUSH, p.level().getGameTime());
		r.dir = p.getLookAngle();
		begin(p, r);
		anim(p, ANIM_RUSH_WINDUP);
		// hop back to wind up
		AbilityHelpers.launchSelf(p, p.getLookAngle().multiply(-0.6, 0, -0.6).add(0, 0.35, 0));
		sound(p, SaiyanSounds.KI_CHARGE_LOOP, 0.8f, 1.3f);
	}

	private static void tickRush(ServerPlayer p, Run r, long age) {
		p.resetFallDistance();
		int rgb = kiColor(form(p));
		if (age < SaiyanMoveConfig.RUSH_WINDUP) {
			if (age > 5) {
				p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 0.3, 0.3));
				p.hurtMarked = true;
			}
			return;
		}
		long rushAge = age - SaiyanMoveConfig.RUSH_WINDUP;
		if (rushAge == 0) {
			r.dir = p.getLookAngle();
			anim(p, ANIM_RUSH);
			fx(p, SaiyanFxPayload.RUSH, p.getId(), Vec3.ZERO, Vec3.ZERO, 1f, rgb, SaiyanMoveConfig.RUSH_TICKS + 4);
			sound(p, SaiyanSounds.DRAGON_RUSH, 1.2f, 1.0f);
		}
		if (rushAge >= SaiyanMoveConfig.RUSH_TICKS) {
			p.setDeltaMovement(r.dir.scale(0.3));
			p.hurtMarked = true;
			end(p, r);
			return;
		}
		Vec3 before = p.position();
		AbilityHelpers.launchSelf(p, r.dir.scale(SaiyanMoveConfig.RUSH_SPEED));
		AABB swept = p.getBoundingBox().expandTowards(r.dir.scale(SaiyanMoveConfig.RUSH_SPEED)).inflate(0.8);
		for (LivingEntity t : HeroTargets.harmable(p.level(), p, swept)) {
			if (t == p) {
				continue;
			}
			if (strike(p, t, before, SaiyanMoveConfig.RUSH_DAMAGE, SaiyanMoveConfig.RUSH_KNOCKBACK, 0.5)) {
				impact(p, t, 2.0f, rgb);
				soundAt(level(p), t.position(), SaiyanSounds.PUNCH_HIT_HEAVY, 0.75f, 0.6f);
				fxAt(level(p), t.position(), SaiyanFxPayload.SHOCKWAVE, p.getId(), t.position().add(0, t.getBbHeight() * 0.5, 0), r.dir, 2.5f, rgb, 1);
				r.hits++;
				p.setDeltaMovement(r.dir.scale(0.2));
				p.hurtMarked = true;
				end(p, r);
				return;
			}
		}
	}

	// ---------------------------------------------------------------- V: Instant Step

	private static void step(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 far = eye.add(look.scale(SaiyanMoveConfig.STEP_RANGE));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity && e != p && e.isAlive() && e.isPickable(), eye.distanceToSqr(end));
		Vec3 to;
		float yaw = p.getYRot();
		if (ehr != null) {
			// behind them, facing their back
			Entity t = ehr.getEntity();
			Vec3 back = Vec3.directionFromRotation(0f, t.getYRot()).scale(-SaiyanMoveConfig.STEP_BEHIND);
			to = t.position().add(back);
			Vec3 face = t.position().subtract(to);
			yaw = (float) (Math.toDegrees(Math.atan2(-face.x, face.z)));
			if (!clear(p, to)) {
				to = t.position().subtract(look.multiply(1, 0, 1).normalize().scale(SaiyanMoveConfig.STEP_BEHIND));
			}
		} else if (bhr.getType() != HitResult.Type.MISS) {
			to = safeSpot(p, bhr);
		} else {
			to = far.subtract(0, p.getEyeHeight(), 0);
		}
		if (to == null || !clear(p, to)) {
			say(p, "message.projecthero.saiyan.no_room");
			return;
		}
		if (!needKi(p, SaiyanMoveConfig.STEP_KI)) {
			return;
		}
		cooldown(p, INSTANT_STEP, SaiyanMoveConfig.STEP_COOLDOWN);
		Vec3 from = p.position();
		int rgb = kiColor(form(p));
		fx(p, SaiyanFxPayload.AFTERIMAGE, p.getId(), from, to, p.getYRot(), rgb, 0);
		sound(p, SaiyanSounds.INSTANT_STEP, 0.9f, 1.0f);
		p.teleportTo(level(p), to.x, to.y, to.z, yaw, p.getXRot());
		p.setYHeadRot(yaw);
		p.resetFallDistance();
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
		anim(p, ANIM_STEP);
		soundAt(level(p), to, SaiyanSounds.INSTANT_STEP, 0.9f, 1.3f);
	}

	/** A standing spot at the block the ray hit: on top of a floor, in front of a wall. */
	private static Vec3 safeSpot(ServerPlayer p, BlockHitResult bhr) {
		BlockPos hit = bhr.getBlockPos();
		Vec3 loc = bhr.getLocation();
		switch (bhr.getDirection()) {
			case UP -> {
				return new Vec3(loc.x, hit.getY() + 1.0, loc.z);
			}
			case DOWN -> {
				return new Vec3(loc.x, hit.getY() - p.getBbHeight() - 0.05, loc.z);
			}
			default -> {
				Vec3 n = Vec3.atLowerCornerOf(bhr.getDirection().getNormal());
				Vec3 at = loc.add(n.scale(p.getBbWidth() * 0.5 + 0.05));
				return new Vec3(at.x, Math.floor(at.y - 0.5) + 0.0, at.z);
			}
		}
	}

	private static boolean clear(ServerPlayer p, Vec3 feet) {
		AABB box = p.getDimensions(p.getPose()).makeBoundingBox(feet);
		return p.level().noCollision(p, box);
	}

	// ---------------------------------------------------------------- tests

	/** The running move's hit count (tests). */
	public static int runHits(Player p) {
		Run r = RUNS.get(p.getUUID());
		return r == null ? -1 : r.hits;
	}

	/** Clears this player's cooldowns (tests and the harness). */
	public static void resetCooldowns(Player p) {
		READY.remove(p.getUUID());
		COMBO_STATE.remove(p.getUUID());
	}

}
