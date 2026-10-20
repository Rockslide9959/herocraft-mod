package com.projecthero.mod.ironman.ability.era1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManIntegrity;
import com.projecthero.mod.ironman.ability.IronManComicMk5Access;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Era I Mk V Stealth: everything the Mk V's moves leave RUNNING -- the Silent Glide hover, Silent Running
 * (mobs cannot pick the wearer as a target; attacking breaks it for 5 s), Thermal Vision (private marks refreshed every
 * second), and mobs put out of action: the Sleeper Dart's sleep (any hit wakes it) and the EMP's shutdown. A frozen mob's
 * AI step is skipped ({@code IronManMk5MobMixin}) and it cannot take a target, so it can neither move nor attack.
 *
 * <p>All static server state, cleared by {@link #clearSessionState} (ServerStateReset); a player's statuses end when the
 * Mk V comes off or is disabled.
 */
public final class EraOneMk5Stealth {
	public static final String SUIT = "mark_v";
	public static final int GLIDE_TICKS = 10 * 20;
	public static final int SLEEP_TICKS = 8 * 20;
	public static final int THERMAL_TICKS = 10 * 20;
	public static final int RUNNING_BREAK_TICKS = 5 * 20;
	public static final double THERMAL_RANGE = 32.0;
	public static final double RUNNING_SHAKE_RANGE = 48.0;
	/** Silent Glide speeds (blocks / tick) the client flies with. */
	public static final double GLIDE_SPEED = 0.32;
	public static final double GLIDE_VERTICAL = 0.22;

	private static final DustParticleOptions SLEEP_DUST = new DustParticleOptions(new org.joml.Vector3f(0.7f, 0.85f, 1.0f), 0.8f);

	static final class Mk5 {
		long glideUntil;
		long glideStart;
		boolean glideMayfly;
		boolean running;
		long runningBrokenUntil;
		long thermalUntil;
	}

	private record Freeze(long until, boolean wakeOnHit) {
	}

	private static final Map<UUID, Mk5> PLAYERS = new HashMap<>();
	private static final Map<Mob, Freeze> FROZEN = new HashMap<>();
	private static boolean initialized;

	private EraOneMk5Stealth() {
	}

	/** Registers the server tick and the wake-on-hit / break-on-attack hook. Called once from {@link EraOneMk5Moves#init}. */
	static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(EraOneMk5Stealth::serverTick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (blocked) {
				return;
			}
			onDamaged(entity, source.getEntity());
		});
	}

	public static void clearSessionState() {
		PLAYERS.clear();
		FROZEN.clear();
	}

	static Mk5 state(ServerPlayer p) {
		return PLAYERS.computeIfAbsent(p.getUUID(), k -> new Mk5());
	}

	private static Mk5 peek(LivingEntity p) {
		return PLAYERS.isEmpty() ? null : PLAYERS.get(p.getUUID());
	}

	// ------------------------------------------------------------------ queries (mixins, HUD, tests)

	/** {@code Mob.serverAiStep}: is this mob asleep / EMP-shut-down right now (skip its AI)? */
	public static boolean frozen(Mob mob) {
		if (FROZEN.isEmpty()) {
			return false;
		}
		Freeze f = FROZEN.get(mob);
		return f != null && f.until > mob.level().getGameTime();
	}

	public static boolean asleep(Mob mob) {
		if (FROZEN.isEmpty()) {
			return false;
		}
		Freeze f = FROZEN.get(mob);
		return f != null && f.wakeOnHit && f.until > mob.level().getGameTime();
	}

	/** {@code Mob.setTarget}: a frozen mob takes no target; nothing picks a Silent Running wearer. */
	public static boolean refusesTarget(Mob self, LivingEntity target) {
		if (target == null) {
			return false;
		}
		if (frozen(self)) {
			return true;
		}
		return target instanceof ServerPlayer p && hidden(p);
	}

	/** Silent Running is on and not broken by an attack. */
	public static boolean hidden(ServerPlayer p) {
		Mk5 s = peek(p);
		return s != null && s.running && s.runningBrokenUntil <= p.level().getGameTime();
	}

	public static boolean silentRunning(ServerPlayer p) {
		Mk5 s = peek(p);
		return s != null && s.running;
	}

	public static boolean gliding(ServerPlayer p) {
		Mk5 s = peek(p);
		return s != null && s.glideUntil > p.level().getGameTime();
	}

	public static boolean thermal(ServerPlayer p) {
		Mk5 s = peek(p);
		return s != null && s.thermalUntil > p.level().getGameTime();
	}

	// ------------------------------------------------------------------ mobs out of action

	/** Put {@code mob} out of action for {@code ticks}: no AI, no target. {@code wakeOnHit}: a sleep any hit ends. */
	static void freeze(Mob mob, int ticks, boolean wakeOnHit) {
		Freeze old = FROZEN.get(mob);
		long until = mob.level().getGameTime() + ticks;
		if (old != null && old.until > until && old.wakeOnHit == wakeOnHit) {
			return;
		}
		// an EMP shutdown is not ended by a hit even when the mob was asleep before it
		FROZEN.put(mob, new Freeze(until, wakeOnHit && (old == null || old.wakeOnHit || old.until <= mob.level().getGameTime())));
		mob.setTarget(null);
		forgetBrainTarget(mob, null);
		mob.getNavigation().stop();
		halt(mob);
	}

	static void wake(Mob mob) {
		if (FROZEN.remove(mob) != null && mob.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 4, 0.2, 0.1, 0.2, 0.01);
		}
	}

	private static void halt(Mob mob) {
		mob.setZza(0f);
		mob.setXxa(0f);
		mob.setYya(0f);
		mob.setJumping(false);
		Vec3 v = mob.getDeltaMovement();
		mob.setDeltaMovement(0, Math.min(v.y, 0.0), 0);
		if (mob instanceof Creeper c) {
			c.setSwellDir(-1);
		}
	}

	/** Brain mobs (piglins, hoglins, the warden...) keep their target in a memory, not in {@code setTarget}. */
	private static void forgetBrainTarget(Mob mob, LivingEntity only) {
		try {
			if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
				LivingEntity t = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
				if (only == null || t == only) {
					mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
				}
			}
		} catch (RuntimeException ignored) {
			// a mob whose brain has no such memory slot
		}
	}

	/** Mobs within {@code range} that are on {@code p} lose them (Silent Running coming on, the blink). */
	static int shake(ServerPlayer p, double range) {
		int n = 0;
		for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(range), m -> m.isAlive())) {
			boolean on = m.getTarget() == p;
			if (on) {
				m.setTarget(null);
				m.getNavigation().stop();
				n++;
			}
			forgetBrainTarget(m, p);
		}
		return n;
	}

	private static void onDamaged(LivingEntity entity, net.minecraft.world.entity.Entity attacker) {
		if (entity instanceof Mob mob && !FROZEN.isEmpty()) {
			Freeze f = FROZEN.get(mob);
			if (f != null && f.wakeOnHit) {
				wake(mob);
			}
		}
		if (attacker instanceof ServerPlayer p && attacker != entity) {
			Mk5 s = peek(p);
			if (s != null && s.running) {
				boolean wasHidden = s.runningBrokenUntil <= p.level().getGameTime();
				s.runningBrokenUntil = p.level().getGameTime() + RUNNING_BREAK_TICKS;
				if (wasHidden) {
					p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk5.running_broken",
							RUNNING_BREAK_TICKS / 20), true);
				}
			}
		}
	}

	// ------------------------------------------------------------------ statuses

	static void startGlide(ServerPlayer p) {
		Mk5 s = state(p);
		long now = p.level().getGameTime();
		s.glideStart = now;
		s.glideUntil = now + GLIDE_TICKS;
		floatTolerance(p, s, true);
	}

	static void endGlide(ServerPlayer p) {
		Mk5 s = peek(p);
		if (s == null) {
			return;
		}
		s.glideUntil = 0L;
		floatTolerance(p, s, false);
		MobEffectInstance sf = p.getEffect(MobEffects.SLOW_FALLING);
		if (sf != null && sf.isAmbient() && !sf.isVisible()) {
			p.removeEffect(MobEffects.SLOW_FALLING);
		}
	}

	/**
	 * Vanilla kicks a player who floats ("flying is not enabled") -- a hover looks just like that. {@code mayfly} for the
	 * glide (with {@code flying} kept off), handed back after unless another flight still needs it (the Hellflame pattern).
	 */
	private static void floatTolerance(ServerPlayer p, Mk5 s, boolean on) {
		if (p.getAbilities().instabuild || p.isSpectator()) {
			return;
		}
		if (on) {
			if (!p.getAbilities().mayfly) {
				p.getAbilities().mayfly = true;
				p.getAbilities().flying = false;
				p.onUpdateAbilities();
				s.glideMayfly = true;
			}
			return;
		}
		if (s.glideMayfly) {
			s.glideMayfly = false;
			if (p.getAbilities().mayfly && !com.projecthero.mod.hero.power.HeroFlight.isFlying(p)
					&& !com.projecthero.mod.power.ThorPowers.isFlying(p)
					&& !p.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)
					&& !p.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)) {
				p.getAbilities().mayfly = false;
				p.getAbilities().flying = false;
				p.onUpdateAbilities();
			}
		}
	}

	static void setRunning(ServerPlayer p, boolean on) {
		Mk5 s = state(p);
		s.running = on;
		s.runningBrokenUntil = 0L;
	}

	static void startThermal(ServerPlayer p) {
		state(p).thermalUntil = p.level().getGameTime() + THERMAL_TICKS;
		thermalPing(p);
	}

	/** Every living thing within 32 (not the wearer) outlined for the wearer alone, through walls. */
	static List<LivingEntity> thermalPing(ServerPlayer p) {
		List<LivingEntity> warm = p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(THERMAL_RANGE),
				e -> e != p && e.isAlive() && e.distanceToSqr(p) <= THERMAL_RANGE * THERMAL_RANGE);
		IronManComicMk5Access.privateMark(p, warm, 30);
		return warm;
	}

	/** Everything this player has running ends (the suit came off / was disabled). */
	static void shutDown(ServerPlayer p) {
		Mk5 s = peek(p);
		if (s == null) {
			return;
		}
		if (s.glideUntil != 0L || s.glideMayfly) {
			endGlide(p);
		}
		s.running = false;
		s.thermalUntil = 0L;
	}

	/** True while {@code p} wears the full-power Mk V (chest on, not disabled). */
	static boolean suited(ServerPlayer p) {
		return SUIT.equals(IronManArmor.wornSuitId(p)) && IronManArmor.hasChestplate(p, SUIT) && !IronManIntegrity.disabled(p, SUIT);
	}

	// ------------------------------------------------------------------ ticking

	private static void serverTick(MinecraftServer server) {
		if (!FROZEN.isEmpty()) {
			tickFrozen();
		}
		if (PLAYERS.isEmpty()) {
			return;
		}
		List<UUID> gone = new ArrayList<>();
		for (Map.Entry<UUID, Mk5> en : PLAYERS.entrySet()) {
			ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
			if (p == null) {
				gone.add(en.getKey());
				continue;
			}
			tickPlayer(p, en.getValue());
		}
		for (UUID id : gone) {
			PLAYERS.remove(id);
		}
	}

	private static void tickFrozen() {
		for (Iterator<Map.Entry<Mob, Freeze>> it = FROZEN.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Mob, Freeze> en = it.next();
			Mob mob = en.getKey();
			if (!mob.isAlive() || mob.isRemoved() || en.getValue().until <= mob.level().getGameTime()) {
				it.remove();
				continue;
			}
			mob.setTarget(null);
			mob.getNavigation().stop();
			halt(mob);
			if (mob.level() instanceof ServerLevel sl && mob.tickCount % 10 == 0) {
				if (en.getValue().wakeOnHit) {
					// drifting pale "z"s over a sleeping head
					sl.sendParticles(SLEEP_DUST, mob.getX() + 0.2, mob.getY() + mob.getBbHeight() + 0.25, mob.getZ(), 2, 0.1, 0.15, 0.1, 0.0);
				} else {
					sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getY() + mob.getBbHeight() * 0.7, mob.getZ(), 3, 0.25,
							0.25, 0.25, 0.05);
				}
			}
		}
	}

	/** One player's Mk V statuses, once a tick. Package-visible for the gametests (mock players never tick on their own). */
	static void tickPlayer(ServerPlayer p, Mk5 s) {
		boolean anything = s.glideUntil != 0L || s.running || s.thermalUntil != 0L || s.glideMayfly;
		if (!anything) {
			return;
		}
		if (!suited(p)) {
			shutDown(p);
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		long now = level.getGameTime();
		// ---- Silent Glide
		if (s.glideUntil > now) {
			// TODO(CORE drain): 8 energy / s while the glide runs (IronManComicKit.energyPerSecond("e1mk5_silent_glide"))
			boolean landed = now - s.glideStart > 10 && (p.onGround() || p.isInWater());
			if (landed || !EraOneMk5Moves.drain(p, "e1mk5_silent_glide")) {
				endGlide(p);
			} else {
				if (p.getAbilities().flying && !p.getAbilities().instabuild) {
					p.getAbilities().flying = false; // a double-tap of Space while mayfly is on: no creative flight
					p.onUpdateAbilities();
				}
				p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, true, false, false));
				p.resetFallDistance();
				IronManAbilityFx.hold(p, IronManAbilityFx.COMIC_GLIDE);
				if (now % 6 == 0) {
					// quiet: a faint shimmer under the boots, no thruster roar
					level.sendParticles(ParticleTypes.WHITE_ASH, p.getX(), p.getY() - 0.1, p.getZ(), 2, 0.2, 0.05, 0.2, 0.0);
				}
			}
		} else if (s.glideUntil != 0L) {
			endGlide(p);
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk5.glide_off"), true);
		}
		// ---- Silent Running
		if (s.running) {
			// TODO(CORE drain): 5 energy / s while Silent Running is on (IronManComicKit.energyPerSecond("e1mk5_silent_running"))
			if (!EraOneMk5Moves.drain(p, "e1mk5_silent_running")) {
				s.running = false;
				p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk5.running_off"), true);
			} else if (s.runningBrokenUntil <= now) {
				if (s.runningBrokenUntil != 0L) {
					s.runningBrokenUntil = 0L;
					p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk5.running_back"), true);
				}
				if (now % 5 == 0) {
					shake(p, RUNNING_SHAKE_RANGE);
				}
				if (now % 20 == 0) {
					level.sendParticles(p, ParticleTypes.SMOKE, false, p.getX(), p.getY() + 0.1, p.getZ(), 2, 0.2, 0.0, 0.2, 0.0);
				}
			}
		}
		// ---- Thermal Vision
		if (s.thermalUntil > now) {
			if ((s.thermalUntil - now) % 20 == 0) {
				thermalPing(p);
			}
		} else if (s.thermalUntil != 0L) {
			s.thermalUntil = 0L;
		}
	}

	/** Gametest helper: run one Mk V tick for {@code p} (mock players are not in the server's tick the usual way). */
	public static void tickForTests(ServerPlayer p) {
		Mk5 s = peek(p);
		if (s != null) {
			tickPlayer(p, s);
		}
	}

	/** Gametest helper: end everything {@code p} has running (so one test's statuses never leak into the next). */
	public static void shutDownForTests(ServerPlayer p) {
		shutDown(p);
		PLAYERS.remove(p.getUUID());
	}

	/** Gametest helper: one frozen-mob pass, as the server tick would. */
	public static void tickFrozenForTests() {
		if (!FROZEN.isEmpty()) {
			tickFrozen();
		}
	}

	/** Gametest helper: was {@code p}'s Silent Running broken by an attack (still on, not hiding)? */
	public static boolean runningBroken(ServerPlayer p) {
		Mk5 s = peek(p);
		return s != null && s.running && s.runningBrokenUntil > p.level().getGameTime();
	}
}
