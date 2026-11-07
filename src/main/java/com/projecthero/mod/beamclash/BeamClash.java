package com.projecthero.mod.beamclash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.beamclash.network.BeamClashMashPayload;
import com.projecthero.mod.beamclash.network.BeamClashPayload;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: beam clashing. Two beams that meet head on lock together: a glowing struggle point forms where they touch, the two
 * owners stop hurting each other, and both push. Players push by mashing left click; a mob pushes on its own, by its beam's
 * power. The point reaching a beam's owner blows them back. Letting go, running out or turning away just ends the clash, and
 * the other beam carries on.
 *
 * <p>Every beam calls {@link #report} from its own tick with where it starts, where it points and how far it reaches. That is
 * deliberately not where it stops: most beams end on the first creature, so two people firing at each other would only ever
 * touch bodies. A clash needs a player on at least one side and two sides that may hurt each other. A mob's beam-on-mob
 * beam is left alone. Reports are read at the start of the next server tick, so it doesn't matter who ticks first.
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
		/** Where the beams first met, as a fraction of the way from a to b. */
		final double meet;
		float balance;
		final long start;
		float tokensA = BURST;
		float tokensB = BURST;
		/** Decided: the backlash is landing, so the two are no longer locked together. */
		boolean over;

		Clash(int id, Report a, Report b, double meet, long start) {
			this.id = id;
			this.a = a;
			this.b = b;
			this.meet = meet;
			this.start = start;
		}

		public float balance() {
			return balance;
		}

		public Vec3 point() {
			double t = balance >= 0f ? meet + balance * (1.0 - meet) : meet + balance * meet;
			return a.from().lerp(b.from(), t);
		}

		boolean has(LivingEntity e) {
			return a.owner() == e || b.owner() == e;
		}
	}

	/** Beams must face each other at least this much (the dot of their directions). */
	static final double FACING = -0.35;
	/** The beams pass this close (blocks) to count as touching. */
	static final double TOUCH = 1.6;
	/** Ticks a beam may skip reporting before its side has let go. */
	static final int GRACE = 3;
	/** Turn more than about 45 degrees off the point and the beam leaves the clash. */
	static final double HOLD_AIM = 0.7;
	static final int MAX_TICKS = 300;
	static final int PAIR_COOLDOWN = 60;

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
		ServerPlayNetworking.registerGlobalReceiver(BeamClashMashPayload.TYPE, (payload, context) -> mash(context.player()));
		ServerTickEvents.START_SERVER_TICK.register(BeamClash::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !lockedTogether(entity, source));
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

	public static Clash clashOf(LivingEntity e) {
		for (Clash c : ACTIVE) {
			if (!c.over && c.has(e)) {
				return c;
			}
		}
		return null;
	}

	/** The two sides of a clash can't hurt each other while it lasts: the beams are spent on each other. */
	static boolean lockedTogether(LivingEntity target, DamageSource source) {
		if (ACTIVE.isEmpty() || !(source.getEntity() instanceof LivingEntity attacker)) {
			return false;
		}
		Clash c = clashOf(target);
		return c != null && attacker != target && c.has(attacker);
	}

	// ---------------------------------------------------------------- the player's push

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

	// ---------------------------------------------------------------- the tick

	static void tick(MinecraftServer server) {
		if (REPORTS.isEmpty() && ACTIVE.isEmpty()) {
			return;
		}
		Iterator<Clash> it = ACTIVE.iterator();
		while (it.hasNext()) {
			if (step(it.next())) {
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
			if (r.owner().isAlive() && !inClash(r.owner()) && r.owner().level().getGameTime() - r.at() <= 1) {
				open.add(r);
			}
		}
		for (int i = 0; i < open.size(); i++) {
			for (int j = i + 1; j < open.size(); j++) {
				Report a = open.get(i);
				Report b = open.get(j);
				if (a != null && b != null && !inClash(a.owner()) && !inClash(b.owner())) {
					tryStart(a, b);
				}
			}
		}
	}

	private static void tryStart(Report a, Report b) {
		LivingEntity x = a.owner();
		LivingEntity y = b.owner();
		if (x.level() != y.level() || !(x instanceof ServerPlayer || y instanceof ServerPlayer) || !enemies(x, y)
				|| a.dir().dot(b.dir()) > FACING) {
			return;
		}
		long now = x.level().getGameTime();
		Long until = COOLDOWNS.get(pair(x, y));
		if (until != null && until > now) {
			return;
		}
		Vec3 meet = meeting(a, b);
		if (meet == null || !clear(x, a.from(), meet) || !clear(y, b.from(), meet)) {
			return;
		}
		double da = a.from().distanceTo(meet);
		double db = b.from().distanceTo(meet);
		Clash c = new Clash(nextId++, a, b, da / Math.max(0.1, da + db), now);
		ACTIVE.add(c);
		ServerLevel level = (ServerLevel) x.level();
		level.playSound(null, meet.x, meet.y, meet.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 1.6f);
		level.playSound(null, meet.x, meet.y, meet.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.8f);
		level.sendParticles(ParticleTypes.FLASH, meet.x, meet.y, meet.z, 1, 0, 0, 0, 0);
		broadcast(level, c, false);
	}

	private static boolean enemies(LivingEntity x, LivingEntity y) {
		return (!(x instanceof ServerPlayer) || HeroTargets.canHarm(x, y)) && (!(y instanceof ServerPlayer) || HeroTargets.canHarm(y, x));
	}

	private static String pair(LivingEntity x, LivingEntity y) {
		return Math.min(x.getId(), y.getId()) + "|" + Math.max(x.getId(), y.getId());
	}

	/** Where the two beams touch, or null if they pass too far apart. */
	static Vec3 meeting(Report a, Report b) {
		// the closest points of the two segments
		Vec3 w = a.from().subtract(b.from());
		double d = a.dir().dot(b.dir());
		double e = a.dir().dot(w);
		double f = b.dir().dot(w);
		double denom = 1.0 - d * d;
		// nearly parallel (two people aimed dead at each other, the usual case): meet halfway between them
		double s = denom < 0.02 ? -e * 0.5 : (d * f - e) / denom;
		s = Math.max(0.0, Math.min(a.range(), s));
		double t = Math.max(0.0, Math.min(b.range(), d * s + f));
		s = Math.max(0.0, Math.min(a.range(), d * t - e));
		Vec3 pa = a.from().add(a.dir().scale(s));
		Vec3 pb = b.from().add(b.dir().scale(t));
		if (pa.distanceToSqr(pb) > TOUCH * TOUCH || s < 0.5 || t < 0.5) {
			return null;
		}
		return pa.add(pb).scale(0.5);
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
		Report ra = REPORTS.get(x.getUUID());
		Report rb = REPORTS.get(y.getUUID());
		if (!x.isAlive() || !y.isAlive() || x.level() != y.level() || !live(ra, now, c) || !live(rb, now, c)
				|| now - c.start > MAX_TICKS) {
			finish(c, level, now);
			return true;
		}
		c.a = ra;
		c.b = rb;
		c.tokensA = Math.min(BURST, c.tokensA + CLICKS_PER_SECOND / 20f);
		c.tokensB = Math.min(BURST, c.tokensB + CLICKS_PER_SECOND / 20f);
		c.balance += LEAN * (ra.power() - rb.power());
		if (!(x instanceof ServerPlayer)) {
			c.balance += MOB_PUSH * ra.power();
		}
		if (!(y instanceof ServerPlayer)) {
			c.balance -= MOB_PUSH * rb.power();
		}
		lock(x);
		lock(y);
		if (now % 20 == 0) {
			Vec3 p = c.point();
			level.playSound(null, p.x, p.y, p.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.6f, 1.6f);
		}
		if (c.balance >= 1f) {
			c.balance = 1f;
			backlash(c, ra, rb, level, now);
			return true;
		}
		if (c.balance <= -1f) {
			c.balance = -1f;
			backlash(c, rb, ra, level, now);
			return true;
		}
		broadcast(level, c, false);
		return false;
	}

	/** Still firing, and still aimed somewhere near the point. */
	private static boolean live(Report r, long now, Clash c) {
		if (r == null || now - r.at() > GRACE) {
			return false;
		}
		Vec3 to = c.point().subtract(r.from());
		return to.lengthSqr() < 1.0 || to.normalize().dot(r.dir()) >= HOLD_AIM;
	}

	/** Pushing against a beam roots you, near enough. */
	private static void lock(LivingEntity e) {
		if (e instanceof ServerPlayer) {
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 2, false, false, false));
		}
	}

	private static void finish(Clash c, ServerLevel level, long now) {
		COOLDOWNS.put(pair(c.a.owner(), c.b.owner()), now + PAIR_COOLDOWN);
		broadcast(level, c, true);
	}

	/** The point has reached the loser. */
	private static void backlash(Clash c, Report winner, Report loser, ServerLevel level, long now) {
		finish(c, level, now);
		c.over = true; // the backlash has to land (step's caller drops it from the list)
		LivingEntity w = winner.owner();
		LivingEntity l = loser.owner();
		float damage = BACKLASH_BASE + BACKLASH_PER_POWER * winner.power();
		boolean boss = l.getMaxHealth() >= 300f || TitanCombat.isBoss(l);
		if (boss) {
			damage = Math.min(damage, l.getMaxHealth() * BOSS_MAX_FRACTION);
		}
		l.invulnerableTime = 0;
		DamageSource source = w instanceof ServerPlayer sp ? level.damageSources().playerAttack(sp) : level.damageSources().mobAttack(w);
		l.hurt(source, damage);
		if (!boss) {
			Vec3 away = l.position().subtract(w.position());
			Vec3 flat = new Vec3(away.x, 0, away.z);
			Vec3 push = (flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize()).scale(1.4).add(0, 0.5, 0);
			if (l instanceof ServerPlayer sp) {
				com.projecthero.mod.hero.power.AbilityHelpers.launchSelf(sp, push);
			} else {
				com.projecthero.mod.hero.power.AbilityHelpers.push(l, push);
			}
		}
		Vec3 p = l.position().add(0, l.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4f, 0.9f);
	}

	private static void broadcast(ServerLevel level, Clash c, boolean over) {
		Vec3 p = c.point();
		BeamClashPayload payload = new BeamClashPayload(c.id, c.a.owner().getId(), c.b.owner().getId(), c.a.from(), c.b.from(), p, c.a.rgb(), c.b.rgb(),
				c.balance, over);
		for (ServerPlayer viewer : PlayerLookup.around(level, p, 128.0)) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}
}
