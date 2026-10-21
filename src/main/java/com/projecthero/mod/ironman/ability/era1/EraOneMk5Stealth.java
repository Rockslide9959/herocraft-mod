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
 * v0.16.7 (user) Era I Mk V Stealth: everything the Mk V's moves leave RUNNING -- Active Camo (the wearer and the suit
 * invisible, mobs lose him; a hit given or taken breaks it for 5 s -- it replaced the Silent Glide once the Mk V flew), Silent Running
 * (mobs cannot pick the wearer as a target; attacking breaks it for 5 s), Thermal Vision (private marks refreshed every
 * second), and mobs put out of action: the Sleeper Dart's sleep (any hit wakes it) and the EMP's shutdown. A frozen mob's
 * AI step is skipped ({@code IronManMk5MobMixin}) and it cannot take a target, so it can neither move nor attack.
 *
 * <p>All static server state, cleared by {@link #clearSessionState} (ServerStateReset); a player's statuses end when the
 * Mk V comes off or is disabled.
 */
public final class EraOneMk5Stealth {
	public static final String SUIT = "mark_v";
	public static final int SLEEP_TICKS = 8 * 20;
	public static final int THERMAL_TICKS = 10 * 20;
	public static final int RUNNING_BREAK_TICKS = 5 * 20;
	public static final double THERMAL_RANGE = 32.0;
	public static final double RUNNING_SHAKE_RANGE = 48.0;
	/** v0.16.7 (user): Active Camo is broken this long by a hit given or taken. */
	public static final int CAMO_BREAK_TICKS = 5 * 20;

	private static final DustParticleOptions SLEEP_DUST = new DustParticleOptions(new org.joml.Vector3f(0.7f, 0.85f, 1.0f), 0.8f);

	static final class Mk5 {
		boolean camo;
		long camoBrokenUntil;
		/** The camo put this Invisibility on (so switching off only takes away our own). */
		boolean camoInvis;
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
		return target instanceof ServerPlayer p && (hidden(p) || camoHidden(p));
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

	/** v0.16.7 (user): Active Camo is switched on (it may be broken for a moment by a hit). */
	public static boolean camo(ServerPlayer p) {
		Mk5 s = peek(p);
		return s != null && s.camo;
	}

	/** Active Camo is on and not broken: invisible, suit and all, and no mob can pick him. */
	public static boolean camoHidden(ServerPlayer p) {
		Mk5 s = peek(p);
		return s != null && s.camo && s.camoBrokenUntil <= p.level().getGameTime();
	}

	/** Gametest helper: was the camo broken by a hit (still on, showing for a moment)? */
	public static boolean camoBroken(ServerPlayer p) {
		Mk5 s = peek(p);
		return s != null && s.camo && s.camoBrokenUntil > p.level().getGameTime();
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
		// v0.16.7 (user): Active Camo flickers out for 5 s on any hit the wearer gives OR takes
		if (attacker instanceof ServerPlayer p && attacker != entity) {
			breakCamo(p);
		}
		if (entity instanceof ServerPlayer hurt) {
			breakCamo(hurt);
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

	private static void breakCamo(ServerPlayer p) {
		Mk5 s = peek(p);
		if (s == null || !s.camo) {
			return;
		}
		boolean wasHidden = s.camoBrokenUntil <= p.level().getGameTime();
		s.camoBrokenUntil = p.level().getGameTime() + CAMO_BREAK_TICKS;
		dropCamoInvisibility(p, s);
		if (wasHidden) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk5.camo_broken", CAMO_BREAK_TICKS / 20), true);
		}
	}

	// ------------------------------------------------------------------ statuses

	/** v0.16.7 (user): Active Camo on / off. Off takes the camo's own Invisibility straight away. */
	static void setCamo(ServerPlayer p, boolean on) {
		Mk5 s = state(p);
		s.camo = on;
		s.camoBrokenUntil = 0L;
		if (on) {
			applyCamoInvisibility(p, s);
		} else {
			dropCamoInvisibility(p, s);
		}
	}

	/** A short, ambient, icon-less Invisibility -- refreshed by the tick, gone on its own the moment the camo stops. */
	private static void applyCamoInvisibility(ServerPlayer p, Mk5 s) {
		MobEffectInstance inv = p.getEffect(MobEffects.INVISIBILITY);
		if (inv == null || (inv.isAmbient() && inv.getDuration() < 10)) {
			p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, true, false, false));
		}
		s.camoInvis = true;
	}

	private static void dropCamoInvisibility(ServerPlayer p, Mk5 s) {
		if (!s.camoInvis) {
			return;
		}
		s.camoInvis = false;
		MobEffectInstance inv = p.getEffect(MobEffects.INVISIBILITY);
		if (inv != null && inv.isAmbient() && !inv.isVisible() && inv.getDuration() <= 20) {
			p.removeEffect(MobEffects.INVISIBILITY);
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
		if (s.camo) {
			setCamo(p, false);
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
		boolean anything = s.camo || s.running || s.thermalUntil != 0L;
		if (!anything) {
			return;
		}
		if (!suited(p)) {
			shutDown(p);
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		long now = level.getGameTime();
		// ---- Active Camo (v0.16.7 (user)): pays 3/s even while a hit has it broken; a brown-out / dry reactor ends it
		if (s.camo) {
			if (!EraOneMk5Moves.drain(p, "e1mk5_active_camo")) {
				setCamo(p, false);
				p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk5.camo_off"), true);
			} else if (s.camoBrokenUntil <= now) {
				if (s.camoBrokenUntil != 0L) {
					s.camoBrokenUntil = 0L;
					p.displayClientMessage(Component.translatable("message.projecthero.ironman.e1mk5.camo_back"), true);
				}
				applyCamoInvisibility(p, s);
				if (now % 5 == 0) {
					shake(p, RUNNING_SHAKE_RANGE);
				}
			} else {
				dropCamoInvisibility(p, s);
			}
		}
		// ---- Silent Running
		if (s.running) {
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

	/** Gametest helper: {@code entity} was hurt by {@code attacker} (mock players never fire the damage events themselves). */
	public static void damagedForTests(LivingEntity entity, net.minecraft.world.entity.Entity attacker) {
		onDamaged(entity, attacker);
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
