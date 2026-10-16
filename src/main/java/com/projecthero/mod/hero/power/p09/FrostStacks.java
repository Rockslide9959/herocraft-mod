package com.projecthero.mod.hero.power.p09;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

/**
 * Cryokinesis signature -- <b>Frost, then frozen solid</b> (v0.16.5 rework, docs/V0165_POWERS_PROPOSAL.md section 9).
 *
 * <p>Ice hits add Frost to their target (Ice Bolt 1, each Ice Shard 1, the Freeze Beam 1 every half second, Glacier
 * Spikes 2, the Ice Blade 1 a hit, Frozen Armor 1 to a melee attacker). Frost shows as a ring of ice pips over the
 * target and the creeping vanilla freeze, and falls off one at a time after 3 s without a new one. At {@value #MAX} the
 * target is <b>frozen solid</b> for {@value #FREEZE_TICKS} ticks (2.5 s): encased in a block of ice (the
 * {@link FrostShellEntity} model), pinned in place, unable to jump or attack. Ice Prison freezes for its own 4 s; Shatter
 * breaks the ice early. A freshly thawed target cannot be re-frozen for 2 s.
 *
 * <p>State is server-only, keyed by entity identity, dropped as soon as the entity is gone and cleared when the server
 * stops ({@link #clearSessionState}).
 */
public final class FrostStacks {
	public static final int MAX = 5;
	/** Frozen solid: 2.5 s, mobs and players alike. */
	public static final int FREEZE_TICKS = 50;
	private static final int DECAY_TICKS = 60;
	private static final int THAW_IMMUNITY = 40;
	private static final BlockParticleOption ICE_DUST = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());

	private static final class State {
		int stacks;
		long lastAdd;
		long frozenUntil;
		long immuneUntil;
		boolean prison;
		double px;
		double py;
		double pz;
		FrostShellEntity shell;
	}

	private static final Map<LivingEntity, State> BY_ENTITY = new IdentityHashMap<>();

	private FrostStacks() {
	}

	/** Frozen creatures cannot strike anything (melee or their own projectiles). */
	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			Entity atk = source.getEntity();
			return !(atk instanceof LivingEntity le && atk != entity && frozen(le));
		});
	}

	public static int stacks(LivingEntity e) {
		State s = BY_ENTITY.get(e);
		return s == null ? 0 : s.stacks;
	}

	public static boolean frozen(LivingEntity e) {
		State s = BY_ENTITY.get(e);
		return s != null && s.frozenUntil > e.level().getGameTime();
	}

	/** Ticks of freeze left (0 when not frozen). */
	public static int frozenTicksLeft(LivingEntity e) {
		State s = BY_ENTITY.get(e);
		return s == null ? 0 : (int) Math.max(0, s.frozenUntil - e.level().getGameTime());
	}

	private static boolean affects(LivingEntity target) {
		return !(target instanceof Player pl) || !pl.isCreative() && !pl.isSpectator()
				&& com.projecthero.mod.hero.HeroConfig.get().abilityHardCrowdControlOnPlayers;
	}

	/**
	 * Adds {@code n} Frost to {@code target} on behalf of {@code source} (null for a Gravebound boss). Freezes it solid on
	 * reaching {@link #MAX}. Players are only affected when the server allows hard crowd control on players.
	 *
	 * @return the target's stack count afterwards ({@link #MAX} while frozen)
	 */
	public static int add(ServerPlayer source, LivingEntity target, int n) {
		if (target == source || !target.isAlive() || !(target.level() instanceof ServerLevel level) || !affects(target)) {
			return 0;
		}
		long now = level.getGameTime();
		State s = BY_ENTITY.computeIfAbsent(target, k -> new State());
		if (s.frozenUntil > now) {
			return MAX;
		}
		if (s.immuneUntil > now) {
			return s.stacks;
		}
		s.stacks = Math.min(MAX, s.stacks + Math.max(0, n));
		s.lastAdd = now;
		target.setTicksFrozen(Math.max(target.getTicksFrozen(), s.stacks * 28));
		if (s.stacks >= MAX) {
			freeze(target, s, FREEZE_TICKS, false);
			return MAX;
		}
		sync(level, target, s);
		return s.stacks;
	}

	/** Straight to frozen solid (Absolute Zero): all five stacks at once. */
	public static void freezeNow(ServerPlayer source, LivingEntity target) {
		add(source, target, MAX);
	}

	/**
	 * Ice Prison: encases {@code target} for {@code ticks} whatever its Frost (ignores the thaw immunity). Returns false
	 * for a target that cannot be frozen.
	 */
	public static boolean imprison(LivingEntity target, int ticks) {
		if (!target.isAlive() || !(target.level() instanceof ServerLevel) || !affects(target)) {
			return false;
		}
		State s = BY_ENTITY.computeIfAbsent(target, k -> new State());
		freeze(target, s, ticks, true);
		return true;
	}

	/** Shatter: breaks a target out of its ice now. Returns whether it was frozen. */
	public static boolean shatter(LivingEntity target) {
		State s = BY_ENTITY.get(target);
		if (s == null || !(target.level() instanceof ServerLevel level) || s.frozenUntil <= level.getGameTime()) {
			return false;
		}
		long now = level.getGameTime();
		s.frozenUntil = now;
		s.immuneUntil = now + THAW_IMMUNITY;
		s.stacks = 0;
		for (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> h : java.util.List.of(MobEffects.MOVEMENT_SLOWDOWN,
				MobEffects.JUMP, MobEffects.WEAKNESS, MobEffects.DIG_SLOWDOWN)) {
			MobEffectInstance inst = target.getEffect(h);
			if (inst != null && !inst.isVisible()) {
				target.removeEffect(h); // only the freeze's own hidden effects
			}
		}
		target.setTicksFrozen(0);
		sync(level, target, s);
		return true;
	}

	private static void freeze(LivingEntity t, State s, int ticks, boolean prison) {
		ServerLevel level = (ServerLevel) t.level();
		long now = level.getGameTime();
		s.stacks = 0;
		s.prison = prison;
		s.frozenUntil = now + ticks;
		s.immuneUntil = now + ticks + THAW_IMMUNITY;
		s.px = t.getX();
		s.py = t.getY();
		s.pz = t.getZ();
		t.clearFire();
		t.setTicksFrozen(t.getTicksRequiredToFreeze() + 40);
		t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
		t.hurtMarked = true;
		t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false, false));
		t.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, -10, false, false, false));
		t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 9, false, false, false));
		t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 4, false, false, false));
		if (t instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		sync(level, t, s);
		if (s.shell != null) {
			s.shell.startFreeze(now, ticks, prison);
		}
		level.sendParticles(ICE_DUST, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 30,
				t.getBbWidth() * 0.6, t.getBbHeight() * 0.5, t.getBbWidth() * 0.6, 0.1);
		level.sendParticles(ParticleTypes.SNOWFLAKE, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 16,
				0.6, 0.8, 0.6, 0.04);
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.2f, 0.6f);
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	/** Keeps the target's {@link FrostShellEntity} (the pips and the ice block) in step with its state. */
	private static void sync(ServerLevel level, LivingEntity t, State s) {
		if (s.shell == null || s.shell.isRemoved()) {
			s.shell = FrostShellEntity.spawn(level, t);
		}
		if (s.shell != null) {
			s.shell.setStacks(s.stacks);
			if (s.frozenUntil <= level.getGameTime()) {
				s.shell.endFreeze();
			}
		}
	}

	/** Once per server tick: pin the frozen, decay idle stacks, drop dead entries (and their shells). */
	public static void tick(MinecraftServer server) {
		if (BY_ENTITY.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<LivingEntity, State>> it = BY_ENTITY.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<LivingEntity, State> en = it.next();
			LivingEntity e = en.getKey();
			State s = en.getValue();
			if (e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel level)) {
				discardShell(s);
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			if (s.frozenUntil > now) {
				e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
				if (!(e instanceof Player) && e.distanceToSqr(s.px, e.getY(), s.pz) > 0.01) {
					e.teleportTo(s.px, Math.min(e.getY(), s.py + 0.01), s.pz);
				}
				e.hurtMarked = true;
				e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze()));
				if (e instanceof Mob mob) {
					mob.getNavigation().stop();
				}
				if ((now & 7) == 0) {
					level.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(),
							2, e.getBbWidth() * 0.5, e.getBbHeight() * 0.4, e.getBbWidth() * 0.5, 0.0);
				}
				continue;
			}
			if (s.frozenUntil != 0 && s.frozenUntil == now) {
				// thaw: the ice cracks off
				level.sendParticles(ICE_DUST, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 20, 0.4, 0.6, 0.4, 0.08);
				level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 1.3f);
				s.prison = false;
				sync(level, e, s);
			}
			if (s.stacks > 0 && now - s.lastAdd >= DECAY_TICKS) {
				s.stacks--;
				s.lastAdd = now;
				sync(level, e, s);
			}
			if (s.stacks <= 0 && s.immuneUntil <= now && s.frozenUntil <= now) {
				discardShell(s);
				it.remove();
			}
		}
	}

	private static void discardShell(State s) {
		if (s.shell != null && !s.shell.isRemoved()) {
			s.shell.discard();
		}
		s.shell = null;
	}

	public static int tracked() {
		return BY_ENTITY.size();
	}

	/** Ends every freeze and stack now (gametests; the shells go with them). */
	public static void clearAll() {
		for (State s : BY_ENTITY.values()) {
			discardShell(s);
		}
		BY_ENTITY.clear();
	}

	public static void clearSessionState() {
		BY_ENTITY.clear();
	}
}
