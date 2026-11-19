package com.projecthero.mod.beamclash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.beamclash.network.BeamClashForfeitPayload;
import com.projecthero.mod.beamclash.network.BeamClashMashPayload;
import com.projecthero.mod.beamclash.network.BeamClashPayload;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Beam clashing (v0.17.3, reworked v0.17.5). Two beams fired at each other lock together: a glowing struggle point forms between the
 * two shooters and both push. Players push by mashing left click; a mob pushes on its own, by its beam's power. The point reaching
 * a shooter blows them back.
 *
 * <p>v0.17.5 (user: "some powers cant beam clash because you need to line it up perfectly ... while beam clashing the player get
 * locked into it until 1 player wins or 1 player loses ... it shouldnt drain extra energy ... releasing your laser shooting ability
 * keybind shouldnt cancel the beam clash ... players can press escape to end the beam clash early ... sentry gets set on fire or I
 * take damage from the laser vision"):
 * <ul>
 * <li><b>Forgiving start</b> ({@link #starts}): the beams point at least 120 degrees apart ({@link #FACING}), the two shooters stand
 * within both beams' reach plus {@link #REACH_MARGIN}, and either the two beam segments pass within {@link #TOUCH} blocks of each
 * other or each beam is aimed within {@link #AIM_CONE_DEG} degrees of the other shooter.</li>
 * <li><b>Locked</b>: once started the clash has its own life. It no longer needs the beams to keep reporting (a short beam, a let-go
 * key or a beam that ran out does not end it) and it draws both beams itself. It ends only when the point reaches a shooter, a
 * shooter forfeits ({@link #forfeit}, Escape on the client), dies or leaves, or after {@link #MAX_TICKS} (whoever is ahead wins).
 * Players are rooted and keep their hotbar slot, and on the client every key but the push, chat and camera keys is held off. Mobs
 * stand still.</li>
 * <li><b>Free</b>: every beam's own tick checks {@link #locked} and skips its energy drain, damage, fire and duration count.</li>
 * <li><b>Harmless meanwhile</b>: nothing a locked side does hurts anyone ({@link #blocksDamage}), and neither side can be set on
 * fire ({@code BeamClashFireMixin}) or burn. Only the outcome deals damage.</li>
 * </ul>
 *
 * <p>Every beam calls {@link #report} from its own tick with where it starts, where it points and how far it reaches. That is
 * deliberately not where it stops: most beams end on the first creature, so two people firing at each other would only ever touch
 * bodies. A clash needs a player on at least one side and two sides that may hurt each other. Reports are read at the start of the
 * next server tick, so it doesn't matter who ticks first.
 */
public final class BeamClash {
	/** One beam, this tick. */
	public record Report(LivingEntity owner, Vec3 from, Vec3 dir, double range, float power, int rgb, long at) {
	}

	/** Two beams locked together. {@code balance} runs -1 (the point has reached a) .. 1 (it has reached b). */
	public static final class Clash {
		final int id;
		Report a;
		Report b;
		/** Where each beam starts relative to its owner's feet, for when the owner's beam has stopped reporting. */
		Vec3 offA;
		Vec3 offB;
		/** Where the beams first met, as a fraction of the way from a to b. */
		final double meet;
		float balance;
		final long start;
		final double startGap;
		float tokensA = BURST;
		float tokensB = BURST;
		final int slotA;
		final int slotB;
		/** Decided: the outcome is landing, so the two are no longer locked together. */
		boolean over;
		/** Who won, once it is over (null: nobody). */
		LivingEntity winner;

		Clash(int id, Report a, Report b, double meet, long start) {
			this.id = id;
			this.a = a;
			this.b = b;
			this.offA = a.from().subtract(a.owner().position());
			this.offB = b.from().subtract(b.owner().position());
			this.meet = meet;
			this.start = start;
			this.startGap = a.owner().distanceTo(b.owner());
			this.slotA = a.owner() instanceof ServerPlayer sp ? sp.getInventory().selected : -1;
			this.slotB = b.owner() instanceof ServerPlayer sp ? sp.getInventory().selected : -1;
		}

		public LivingEntity winner() {
			return winner;
		}

		public boolean over() {
			return over;
		}

		public float balance() {
			return balance;
		}

		public long start() {
			return start;
		}

		public Vec3 fromA() {
			return a.owner().position().add(offA);
		}

		public Vec3 fromB() {
			return b.owner().position().add(offB);
		}

		public Vec3 point() {
			double t = balance >= 0f ? meet + balance * (1.0 - meet) : meet + balance * meet;
			return fromA().lerp(fromB(), t);
		}

		public LivingEntity partnerOf(LivingEntity e) {
			return a.owner() == e ? b.owner() : b.owner() == e ? a.owner() : null;
		}

		boolean has(LivingEntity e) {
			return a.owner() == e || b.owner() == e;
		}
	}

	/** The beams must point at least 120 degrees apart (the dot of their directions is at most this). */
	public static final double FACING = -0.5;
	/** The two beam segments pass this close (blocks) to count as touching. */
	public static final double TOUCH = 2.0;
	/** ...or each beam is aimed within this many degrees of the other shooter's body. */
	public static final double AIM_CONE_DEG = 25.0;
	static final double AIM_CONE = Math.cos(Math.toRadians(AIM_CONE_DEG));
	/** The shooters may stand this much further apart than their two beams reach together. */
	public static final double REACH_MARGIN = 3.0;
	/** A report at most this old can start a clash. */
	static final int FRESH = 1;
	/** Reports older than this are dropped. */
	static final int GRACE = 3;
	/** 20 s: then whoever is ahead wins (a dead heat blows both back). */
	public static final int MAX_TICKS = 400;
	static final int PAIR_COOLDOWN = 60;
	/** Dragged (or teleported) this much further apart than at the start, the clash breaks with no outcome. */
	static final double BREAK_DISTANCE = 8.0;

	/** One click moves the point this far, times the beam's power. About 9 clicks a second holds off an ordinary boss. */
	public static final float MASH_GAIN = 0.03f;
	/** Mobs push this much a tick, times the beam's power. */
	public static final float MOB_PUSH = 0.012f;
	/** The stronger beam leans on the weaker every tick by this, times the difference in power. */
	static final float LEAN = 0.0015f;

	static final float CLICKS_PER_SECOND = 20f;
	static final float BURST = 3f;

	public static final float BACKLASH_BASE = 14f;
	public static final float BACKLASH_PER_POWER = 10f;
	static final float BOSS_MAX_FRACTION = 0.08f;

	private static final Map<UUID, Report> REPORTS = new ConcurrentHashMap<>();
	private static final List<Clash> ACTIVE = new ArrayList<>();
	/** "smaller id|larger id" -> game time they may clash again. */
	private static final Map<String, Long> COOLDOWNS = new HashMap<>();
	private static int nextId = 1;

	private BeamClash() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playS2C().register(BeamClashPayload.TYPE, BeamClashPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(BeamClashMashPayload.TYPE, BeamClashMashPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(BeamClashForfeitPayload.TYPE, BeamClashForfeitPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BeamClashMashPayload.TYPE, (payload, context) -> mash(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(BeamClashForfeitPayload.TYPE, (payload, context) -> forfeit(context.player()));
		ServerTickEvents.START_SERVER_TICK.register(BeamClash::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !blocksDamage(entity, source));
	}

	public static void clearSessionState() {
		REPORTS.clear();
		ACTIVE.clear();
		COOLDOWNS.clear();
		nextId = 1;
	}

	// ---------------------------------------------------------------- what beams call

	/**
	 * This beam is firing this tick. Call it every tick the beam is live, not just on its damage ticks.
	 *
	 * @param from where the beam starts
	 * @param dir which way it points (any length)
	 * @param range how far it can reach, ignoring whoever it is hitting
	 * @param power how hard it pushes; 1 is an ordinary beam
	 * @param rgb the beam's colour, for the struggle
	 */
	public static void report(LivingEntity owner, Vec3 from, Vec3 dir, double range, float power, int rgb) {
		if (owner.level().isClientSide || dir.lengthSqr() < 1.0e-6) {
			return;
		}
		REPORTS.put(owner.getUUID(), new Report(owner, from, dir.normalize(), range, power, rgb, owner.level().getGameTime()));
	}

	public static boolean inClash(LivingEntity e) {
		return clashOf(e) != null;
	}

	/**
	 * v0.17.5: this beam's owner is locked in a clash. The clash owns the beam: a beam's own tick keeps reporting but skips its
	 * energy drain, its damage / fire / knockback and its duration count while this is true.
	 */
	public static boolean locked(LivingEntity e) {
		return clashOf(e) != null;
	}

	public static Clash clashOf(LivingEntity e) {
		if (ACTIVE.isEmpty()) {
			return null;
		}
		for (Clash c : ACTIVE) {
			if (!c.over && c.has(e)) {
				return c;
			}
		}
		return null;
	}

	/**
	 * Nothing a locked shooter does hurts anyone (its beam is spent on the clash and everything else is held off), so the two can't
	 * hurt each other, and a locked shooter doesn't burn. The outcome is dealt after the clash is marked over, so it lands.
	 */
	public static boolean blocksDamage(LivingEntity target, DamageSource source) {
		if (ACTIVE.isEmpty()) {
			return false;
		}
		if (source.getEntity() instanceof LivingEntity le && le != target && locked(le)) {
			return true;
		}
		if (source.getDirectEntity() instanceof LivingEntity le && le != target && locked(le)) {
			return true;
		}
		return locked(target) && (source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.IN_FIRE));
	}

	// ---------------------------------------------------------------- the player's push and forfeit

	/** One left click. The server counts them: more than 20 a second are ignored. */
	public static void mash(ServerPlayer player) {
		Clash c = clashOf(player);
		if (c == null) {
			return;
		}
		boolean sideA = c.a.owner() == player;
		float tokens = sideA ? c.tokensA : c.tokensB;
		if (tokens < 1f) {
			return;
		}
		float push = MASH_GAIN * (sideA ? c.a : c.b).power();
		if (sideA) {
			c.tokensA = tokens - 1f;
			c.balance += push;
		} else {
			c.tokensB = tokens - 1f;
			c.balance -= push;
		}
	}

	/**
	 * Escape (or anything else that opens the pause menu) during a clash: the player gives it up. The other side wins, the quitter
	 * is shoved back a little but takes no damage, and both are let go.
	 */
	public static void forfeit(LivingEntity quitter) {
		Clash c = clashOf(quitter);
		if (c == null) {
			return;
		}
		ServerLevel level = (ServerLevel) quitter.level();
		LivingEntity winner = c.partnerOf(quitter);
		c.balance = c.a.owner() == quitter ? -1f : 1f;
		c.over = true;
		c.winner = winner;
		COOLDOWNS.put(pair(c.a.owner(), c.b.owner()), level.getGameTime() + PAIR_COOLDOWN);
		shove(quitter, winner, 0.7, 0.3);
		Vec3 p = quitter.position().add(0, quitter.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 1.4f);
		broadcast(level, c, true, winner, true);
	}

	// ---------------------------------------------------------------- the tick

	static void tick(MinecraftServer server) {
		if (REPORTS.isEmpty() && ACTIVE.isEmpty()) {
			return;
		}
		Iterator<Clash> it = ACTIVE.iterator();
		while (it.hasNext()) {
			Clash c = it.next();
			if (c.over || step(c)) {
				it.remove();
			}
		}
		startClashes();
		REPORTS.values().removeIf(r -> r.owner().isRemoved() || r.owner().level().getGameTime() - r.at() > GRACE);
		if (!COOLDOWNS.isEmpty()) {
			long now = server.overworld().getGameTime();
			COOLDOWNS.values().removeIf(until -> until < now);
		}
	}

	private static void startClashes() {
		List<Report> open = new ArrayList<>();
		for (Report r : REPORTS.values()) {
			if (r.owner().isAlive() && !inClash(r.owner()) && r.owner().level().getGameTime() - r.at() <= FRESH) {
				open.add(r);
			}
		}
		for (int i = 0; i < open.size(); i++) {
			for (int j = i + 1; j < open.size(); j++) {
				Report a = open.get(i);
				Report b = open.get(j);
				if (!inClash(a.owner()) && !inClash(b.owner())) {
					tryStart(a, b);
				}
			}
		}
	}

	private static void tryStart(Report a, Report b) {
		LivingEntity x = a.owner();
		LivingEntity y = b.owner();
		if (x == y || x.level() != y.level() || !(x instanceof ServerPlayer || y instanceof ServerPlayer) || !enemies(x, y)) {
			return;
		}
		long now = x.level().getGameTime();
		Long until = COOLDOWNS.get(pair(x, y));
		if (until != null && until > now) {
			return;
		}
		double meet = starts(a, b, centre(y), centre(x));
		if (Double.isNaN(meet)) {
			return;
		}
		Vec3 point = a.from().lerp(b.from(), meet);
		if (!clear(x, a.from(), point) || !clear(y, b.from(), point)) {
			return;
		}
		Clash c = new Clash(nextId++, a, b, meet, now);
		ACTIVE.add(c);
		ServerLevel level = (ServerLevel) x.level();
		level.playSound(null, point.x, point.y, point.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 1.6f);
		level.playSound(null, point.x, point.y, point.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.8f);
		level.sendParticles(ParticleTypes.FLASH, point.x, point.y, point.z, 1, 0, 0, 0, 0);
		x.clearFire();
		y.clearFire();
		broadcast(level, c, false, null, false);
	}

	private static Vec3 centre(LivingEntity e) {
		return e.getBoundingBox().getCenter();
	}

	/**
	 * The forgiving start rule. Returns where the struggle point forms as a fraction of the way from a's start to b's, or NaN when
	 * these two beams don't clash. {@code bodyB} / {@code bodyA} are the shooters' body centres (for the aim cone).
	 */
	public static double starts(Report a, Report b, Vec3 bodyB, Vec3 bodyA) {
		Vec3 ab = b.from().subtract(a.from());
		double gap = ab.length();
		if (gap < 1.5 || gap > a.range() + b.range() + REACH_MARGIN || a.dir().dot(b.dir()) > FACING) {
			return Double.NaN;
		}
		double[] st = closest(a.from(), a.dir().scale(a.range()), b.from(), b.dir().scale(b.range()));
		Vec3 pa = a.from().add(a.dir().scale(a.range() * st[0]));
		Vec3 pb = b.from().add(b.dir().scale(b.range() * st[1]));
		double meet;
		if (pa.distanceToSqr(pb) <= TOUCH * TOUCH) {
			meet = pa.add(pb).scale(0.5).subtract(a.from()).dot(ab) / (gap * gap);
		} else if (aimedAt(a, bodyB) && aimedAt(b, bodyA)) {
			// each has the other in his sights but the beams pass wide (one too short, or eye beams at a flyer): they meet where the
			// shorter one ends, or halfway
			double half = gap * 0.5;
			meet = a.range() < half ? a.range() / gap : b.range() < half ? 1.0 - b.range() / gap : 0.5;
		} else {
			return Double.NaN;
		}
		return Math.max(0.1, Math.min(0.9, meet));
	}

	static boolean aimedAt(Report r, Vec3 body) {
		Vec3 to = body.subtract(r.from());
		return to.lengthSqr() < 1.0e-6 || to.normalize().dot(r.dir()) >= AIM_CONE;
	}

	/** The closest points of segments p1 + s d1 and p2 + t d2, s and t in 0..1 (Ericson, Real-Time Collision Detection 5.1.9). */
	static double[] closest(Vec3 p1, Vec3 d1, Vec3 p2, Vec3 d2) {
		Vec3 r = p1.subtract(p2);
		double a = d1.dot(d1);
		double e = d2.dot(d2);
		double f = d2.dot(r);
		double c = d1.dot(r);
		double b = d1.dot(d2);
		double denom = a * e - b * b;
		double s = denom > 1.0e-9 ? clamp01((b * f - c * e) / denom) : 0.5;
		double t = (b * s + f) / e;
		if (t < 0.0) {
			t = 0.0;
			s = clamp01(-c / a);
		} else if (t > 1.0) {
			t = 1.0;
			s = clamp01((b - c) / a);
		}
		return new double[] { s, t };
	}

	private static double clamp01(double v) {
		return v < 0.0 ? 0.0 : v > 1.0 ? 1.0 : v;
	}

	private static boolean enemies(LivingEntity x, LivingEntity y) {
		return (!(x instanceof ServerPlayer) || HeroTargets.canHarm(x, y)) && (!(y instanceof ServerPlayer) || HeroTargets.canHarm(y, x));
	}

	private static String pair(LivingEntity x, LivingEntity y) {
		return Math.min(x.getId(), y.getId()) + "|" + Math.max(x.getId(), y.getId());
	}

	private static boolean clear(LivingEntity owner, Vec3 from, Vec3 to) {
		return owner.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getType() == HitResult.Type.MISS;
	}

	/** One tick of a clash. True when it is over. */
	private static boolean step(Clash c) {
		LivingEntity x = c.a.owner();
		LivingEntity y = c.b.owner();
		ServerLevel level = (ServerLevel) x.level();
		long now = level.getGameTime();
		boolean xGone = !x.isAlive() || x.isRemoved();
		boolean yGone = !y.isAlive() || y.isRemoved();
		if (xGone || yGone || x.level() != y.level() || x.distanceTo(y) > c.startGap + BREAK_DISTANCE) {
			// someone died, left or was dragged away: no outcome to land
			c.over = true;
			COOLDOWNS.put(pair(x, y), now + PAIR_COOLDOWN);
			broadcast(level, c, true, xGone && !yGone ? y : yGone && !xGone ? x : null, false);
			return true;
		}
		// a beam that is still firing updates its start, look and power; one that stopped is held by the clash where it was
		Report ra = REPORTS.get(x.getUUID());
		Report rb = REPORTS.get(y.getUUID());
		if (ra != null && now - ra.at() <= GRACE && ra != c.a) {
			c.a = ra;
			c.offA = ra.from().subtract(x.position());
		}
		if (rb != null && now - rb.at() <= GRACE && rb != c.b) {
			c.b = rb;
			c.offB = rb.from().subtract(y.position());
		}
		c.tokensA = Math.min(BURST, c.tokensA + CLICKS_PER_SECOND / 20f);
		c.tokensB = Math.min(BURST, c.tokensB + CLICKS_PER_SECOND / 20f);
		c.balance += LEAN * (c.a.power() - c.b.power());
		if (!(x instanceof ServerPlayer)) {
			c.balance += MOB_PUSH * c.a.power();
		}
		if (!(y instanceof ServerPlayer)) {
			c.balance -= MOB_PUSH * c.b.power();
		}
		hold(x, c.slotA);
		hold(y, c.slotB);
		if (now % 20 == 0) {
			Vec3 p = c.point();
			level.playSound(null, p.x, p.y, p.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.6f, 1.6f);
		}
		boolean timeUp = now - c.start >= MAX_TICKS;
		if (c.balance >= 1f || (timeUp && c.balance > 0f)) {
			c.balance = 1f;
			backlash(c, c.a, c.b, level, now);
			return true;
		}
		if (c.balance <= -1f || (timeUp && c.balance < 0f)) {
			c.balance = -1f;
			backlash(c, c.b, c.a, level, now);
			return true;
		}
		if (timeUp) {
			// a dead heat: the point bursts and throws both back
			c.over = true;
			COOLDOWNS.put(pair(x, y), now + PAIR_COOLDOWN);
			shove(x, y, 0.9, 0.4);
			shove(y, x, 0.9, 0.4);
			Vec3 p = c.point();
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4f, 1.1f);
			broadcast(level, c, true, null, false);
			return true;
		}
		broadcast(level, c, false, null, false);
		return false;
	}

	/** Locked in place: rooted, no burning, the same item in hand; a mob stands still. Looking around stays free. */
	private static void hold(LivingEntity e, int slot) {
		e.clearFire();
		if (e instanceof ServerPlayer sp) {
			sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 9, false, false, false));
			if (slot >= 0 && sp.getInventory().selected != slot) {
				sp.getInventory().selected = slot;
				sp.connection.send(new ClientboundSetCarriedItemPacket(slot));
			}
		} else {
			if (e instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
		}
	}

	private static void shove(LivingEntity who, LivingEntity from, double out, double up) {
		if (TitanCombat.isBoss(who) || who.getMaxHealth() >= 300f) {
			return;
		}
		Vec3 away = from == null ? who.getLookAngle().scale(-1) : who.position().subtract(from.position());
		Vec3 flat = new Vec3(away.x, 0, away.z);
		Vec3 push = (flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize()).scale(out).add(0, up, 0);
		if (who instanceof ServerPlayer sp) {
			AbilityHelpers.launchSelf(sp, push);
		} else {
			AbilityHelpers.push(who, push);
		}
	}

	/** The point has reached the loser: the winner's beam drives through. */
	private static void backlash(Clash c, Report winner, Report loser, ServerLevel level, long now) {
		c.over = true; // first: the outcome has to land past blocksDamage
		COOLDOWNS.put(pair(c.a.owner(), c.b.owner()), now + PAIR_COOLDOWN);
		LivingEntity w = winner.owner();
		LivingEntity l = loser.owner();
		c.winner = w;
		broadcast(level, c, true, w, false);
		float damage = BACKLASH_BASE + BACKLASH_PER_POWER * winner.power();
		boolean boss = l.getMaxHealth() >= 300f || TitanCombat.isBoss(l);
		if (boss) {
			damage = Math.min(damage, l.getMaxHealth() * BOSS_MAX_FRACTION);
		}
		l.invulnerableTime = 0;
		DamageSource source = w instanceof ServerPlayer sp ? level.damageSources().playerAttack(sp) : level.damageSources().mobAttack(w);
		l.hurt(source, damage);
		shove(l, w, 1.4, 0.5);
		Vec3 p = l.position().add(0, l.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4f, 0.9f);
	}

	private static void broadcast(ServerLevel level, Clash c, boolean over, LivingEntity winner, boolean forfeit) {
		Vec3 p = c.point();
		int left = (int) Math.max(0, MAX_TICKS - (level.getGameTime() - c.start));
		BeamClashPayload payload = new BeamClashPayload(c.id, c.a.owner().getId(), c.b.owner().getId(), c.fromA(), c.fromB(), p, c.a.rgb(), c.b.rgb(),
				c.balance, over, left, winner == null ? -1 : winner.getId(), forfeit);
		List<ServerPlayer> sent = new ArrayList<>(PlayerLookup.around(level, p, 128.0));
		for (LivingEntity e : new LivingEntity[] { c.a.owner(), c.b.owner() }) {
			// the two shooters always hear about their own clash, however far apart
			if (e instanceof ServerPlayer sp && !sent.contains(sp)) {
				sent.add(sp);
			}
		}
		for (ServerPlayer viewer : sent) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}
}
