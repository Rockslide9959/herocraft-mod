package com.projecthero.mod.hero.power.p09;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.TempBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.13.22 Cryokinesis signature -- <b>frost stacks, then frozen solid.</b>
 *
 * <p>Every Cryokinesis hit adds frost stacks to its target (Ice Bolt 1, Freeze Beam 1 per pulse, Ice Blade 1 per
 * swing, Frozen Armor's aura 1 every 2 s, Flash Freeze 2, Absolute Zero all of them). Stacks show as the vanilla
 * freezing effect (20% of a full freeze per stack) plus a ring of snowflakes, and fall off one at a time after 3 s
 * without a new one. At {@value #MAX} stacks the target is <b>frozen solid</b>: sealed in a shell of packed ice
 * (a real temporary block shell around a mob when terrain effects are on; players get the synced
 * {@code p09.frozen_solid} ice-shell overlay instead) and pinned in place -- no movement, no jumping, no attacking --
 * for {@value #FREEZE_TICKS_MOB} ticks (players {@value #FREEZE_TICKS_PLAYER}). The shatter deals
 * {@value #SHATTER_DAMAGE} on freezing, and a freshly thawed target cannot be re-frozen for 2 s.
 *
 * <p>State is server-only, keyed by entity identity, dropped as soon as the entity is gone and cleared when the
 * server stops.
 */
public final class FrostStacks {
	public static final int MAX = 5;
	public static final int FREEZE_TICKS_MOB = 60;
	public static final int FREEZE_TICKS_PLAYER = 30;
	public static final float SHATTER_DAMAGE = 6.0f;
	private static final int DECAY_TICKS = 60;
	private static final int THAW_IMMUNITY = 40;
	private static final BlockState SHELL = Blocks.PACKED_ICE.defaultBlockState();
	private static final BlockParticleOption ICE_DUST = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());

	private static final class State {
		int stacks;
		long lastAdd;
		long frozenUntil;
		long immuneUntil;
		double px;
		double py;
		double pz;
	}

	private static final Map<LivingEntity, State> BY_ENTITY = new IdentityHashMap<>();

	private FrostStacks() {
	}

	public static int stacks(LivingEntity e) {
		State s = BY_ENTITY.get(e);
		return s == null ? 0 : s.stacks;
	}

	public static boolean frozen(LivingEntity e) {
		State s = BY_ENTITY.get(e);
		return s != null && s.frozenUntil > e.level().getGameTime();
	}

	/**
	 * Adds {@code n} frost stacks to {@code target} on behalf of {@code source}. Freezes it solid on reaching
	 * {@link #MAX}. Players are only affected when the server allows hard crowd control on players.
	 *
	 * @return the target's stack count afterwards ({@link #MAX} if it just froze)
	 */
	public static int add(ServerPlayer source, LivingEntity target, int n) {
		if (target == source || !target.isAlive() || !(target.level() instanceof ServerLevel)) {
			return 0;
		}
		if (target instanceof Player && !com.projecthero.mod.hero.HeroConfig.get().abilityHardCrowdControlOnPlayers) {
			return 0;
		}
		long now = target.level().getGameTime();
		State s = BY_ENTITY.computeIfAbsent(target, k -> new State());
		if (s.frozenUntil > now || s.immuneUntil > now) {
			return s.frozenUntil > now ? MAX : s.stacks;
		}
		s.stacks = Math.min(MAX, s.stacks + Math.max(0, n));
		s.lastAdd = now;
		target.setTicksFrozen(Math.max(target.getTicksFrozen(), s.stacks * 28));
		if (s.stacks >= MAX) {
			freezeSolid(source, target, s);
			return MAX;
		}
		return s.stacks;
	}

	/** Straight to frozen solid (Absolute Zero). */
	public static void freezeNow(ServerPlayer source, LivingEntity target) {
		add(source, target, MAX);
	}

	private static void freezeSolid(ServerPlayer source, LivingEntity t, State s) {
		ServerLevel level = (ServerLevel) t.level();
		long now = level.getGameTime();
		boolean isPlayer = t instanceof Player;
		int ticks = isPlayer ? FREEZE_TICKS_PLAYER : FREEZE_TICKS_MOB;
		s.stacks = 0;
		s.frozenUntil = now + ticks;
		s.immuneUntil = now + ticks + THAW_IMMUNITY;
		s.px = t.getX();
		s.py = t.getY();
		s.pz = t.getZ();
		t.clearFire();
		t.setTicksFrozen(t.getTicksRequiredToFreeze() + 40);
		t.setDeltaMovement(0, Math.min(0, t.getDeltaMovement().y), 0);
		t.hurtMarked = true;
		t.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false, false));
		t.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.JUMP, ticks, -10, false, false, false));
		t.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.WEAKNESS, ticks, 9, false, false, false));
		t.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.DIG_SLOWDOWN, ticks, 4, false, false, false));
		if (t instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (source != null) {
			AbilityHelpers.hurtBurst(source, t, AbilityHelpers.freeze(source), SHATTER_DAMAGE);
		} else {
			// v0.14.21: a Gravebound Cryokinesis boss adds stacks with no player source (it used to NPE here)
			t.hurt(level.damageSources().freeze(), SHATTER_DAMAGE);
		}
		if (!isPlayer && AbilityHelpers.canGrief()) {
			encase(level, t, ticks);
		}
		level.sendParticles(ICE_DUST, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 40,
				t.getBbWidth() * 0.6, t.getBbHeight() * 0.5, t.getBbWidth() * 0.6, 0.1);
		level.sendParticles(ParticleTypes.SNOWFLAKE, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 30,
				0.6, 0.8, 0.6, 0.05);
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.1f, 0.6f);
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	/** A packed-ice shell hugging the target's bounding box (air cells only), melting when the freeze ends. */
	private static void encase(ServerLevel level, LivingEntity t, int ticks) {
		BlockPos base = t.blockPosition();
		int w = Math.max(0, (int) Math.ceil(t.getBbWidth() / 2.0 - 0.5));
		int h = Math.max(1, (int) Math.ceil(t.getBbHeight()));
		for (int dy = 0; dy < h; dy++) {
			for (int dx = -w - 1; dx <= w + 1; dx++) {
				for (int dz = -w - 1; dz <= w + 1; dz++) {
					boolean edge = Math.abs(dx) == w + 1 || Math.abs(dz) == w + 1;
					if (edge && !(Math.abs(dx) == w + 1 && Math.abs(dz) == w + 1)) {
						TempBlocks.place(level, base.offset(dx, dy, dz), SHELL, ticks);
					}
				}
			}
		}
		for (int dx = -w; dx <= w; dx++) {
			for (int dz = -w; dz <= w; dz++) {
				TempBlocks.place(level, base.offset(dx, h, dz), SHELL, ticks);
			}
		}
	}

	/** Once per server tick: pin the frozen, decay idle stacks, drop dead entries. */
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
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			if (s.frozenUntil > now) {
				e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
				if (!(e instanceof Player) && e.distanceToSqr(s.px, s.py, s.pz) > 0.01) {
					e.teleportTo(s.px, s.py, s.pz);
				}
				e.hurtMarked = true;
				e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze()));
				if (e instanceof Mob mob) {
					mob.getNavigation().stop();
				}
				if ((now & 3) == 0) {
					level.sendParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(),
							3, e.getBbWidth() * 0.5, e.getBbHeight() * 0.4, e.getBbWidth() * 0.5, 0.0);
				}
				continue;
			}
			if (s.frozenUntil != 0 && s.frozenUntil <= now && s.frozenUntil > now - 2) {
				level.sendParticles(ICE_DUST, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 20,
						0.4, 0.6, 0.4, 0.08);
				level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8f, 1.3f);
			}
			if (s.stacks > 0) {
				if (now - s.lastAdd >= DECAY_TICKS) {
					s.stacks--;
					s.lastAdd = now;
				}
				if ((now & 7) == 0) {
					for (int i = 0; i < s.stacks; i++) {
						double a = (now * 0.2) + i * (Math.PI * 2 / Math.max(1, s.stacks));
						level.sendParticles(ParticleTypes.SNOWFLAKE, e.getX() + Math.cos(a) * 0.6,
								e.getY() + e.getBbHeight() + 0.2, e.getZ() + Math.sin(a) * 0.6, 1, 0, 0, 0, 0);
					}
				}
			}
			if (s.stacks <= 0 && s.immuneUntil <= now) {
				it.remove();
			}
		}
	}

	public static int tracked() {
		return BY_ENTITY.size();
	}

	public static void clearSessionState() {
		BY_ENTITY.clear();
	}
}
