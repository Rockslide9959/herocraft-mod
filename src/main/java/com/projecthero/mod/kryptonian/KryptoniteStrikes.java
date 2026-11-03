package com.projecthero.mod.kryptonian;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.kryptonian.item.KryptoniteTools;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

/**
 * v0.17.2 (user: "make it deal 2 times damage to kryptonian beings(this includes doomsday please), should completely bypass their
 * defences, should stop their healing and powers for 20 seconds after getting hit 3 times"): what a kryptonite tool does to a
 * Kryptonian being -- a Kryptonian-power player or Doomsday.
 *
 * <ul>
 *   <li>A melee hit with one is re-issued as {@code projecthero:kryptonite_strike} at x{@link #MULT}: that damage type bypasses
 *       armour, Resistance and Protection, and the Kryptonian 80% reduction and Doomsday's adaptation / Cable Guard skip it.</li>
 *   <li>The {@link #HITS}rd strike within {@link #WINDOW_TICKS} poisons him for {@link #SUPPRESS_TICKS}: no powers, no healing
 *       at all (the heal mixin cancels every {@code heal}).</li>
 * </ul>
 * The hit counts and timers are session statics, cleared by {@code ServerStateReset}.
 */
public final class KryptoniteStrikes {
	public static final ResourceKey<DamageType> STRIKE = ResourceKey.create(Registries.DAMAGE_TYPE, ProjectHeroMod.id("kryptonite_strike"));
	public static final float MULT = 2.0f;
	public static final int HITS = 3;
	public static final int WINDOW_TICKS = 200;
	public static final int SUPPRESS_TICKS = 400;
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.3f, 1.0f, 0.25f), 1.2f);

	/** {hits, game time of the last} per target. */
	private static final Map<UUID, long[]> HIT_COUNTS = new ConcurrentHashMap<>();
	/** Game time each poisoned target recovers. */
	private static final Map<UUID, Long> SUPPRESSED = new ConcurrentHashMap<>();

	private KryptoniteStrikes() {
	}

	/** A Kryptonian-power player or Doomsday. */
	public static boolean isKryptonianBeing(Entity e) {
		return e instanceof Player p && Kryptonian.hasPower(p) || e instanceof com.projecthero.mod.doomsday.entity.DoomsdayEntity;
	}

	/** A plain melee hit from someone holding a kryptonite tool (not already a re-issued strike). */
	public static boolean isToolHit(DamageSource source) {
		if (source.is(STRIKE) || !(source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK))) {
			return false;
		}
		return source.getEntity() instanceof LivingEntity le && source.getDirectEntity() == le && KryptoniteTools.isTool(le.getMainHandItem());
	}

	public static DamageSource strike(ServerLevel level, Entity attacker) {
		return level.damageSources().source(STRIKE, attacker);
	}

	/** A strike landed on {@code target}: count it, and the third poisons him. */
	public static void onStrike(LivingEntity target, Entity attacker) {
		if (!(target.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		long[] c = HIT_COUNTS.computeIfAbsent(target.getUUID(), k -> new long[2]);
		if (now - c[1] > WINDOW_TICKS) {
			c[0] = 0;
		}
		c[0]++;
		c[1] = now;
		level.sendParticles(GREEN, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 10, target.getBbWidth() * 0.4,
				target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.0);
		if (c[0] < HITS) {
			if (attacker instanceof ServerPlayer sp) {
				sp.displayClientMessage(Component.translatable("message.projecthero.kryptonite.strike", c[0]).withStyle(ChatFormatting.GREEN), true);
			}
			return;
		}
		HIT_COUNTS.remove(target.getUUID());
		suppress(target, SUPPRESS_TICKS);
		if (attacker instanceof ServerPlayer sp) {
			sp.displayClientMessage(Component.translatable("message.projecthero.kryptonite.suppressed_target", target.getDisplayName())
					.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
		}
	}

	/** No powers and no healing for {@code ticks}. */
	public static void suppress(LivingEntity target, int ticks) {
		if (!(target.level() instanceof ServerLevel level)) {
			return;
		}
		long until = level.getGameTime() + ticks;
		SUPPRESSED.merge(target.getUUID(), until, Math::max);
		if (target instanceof ServerPlayer p && Kryptonian.hasPower(p)) {
			Kryptonian.kryptoniteSicken(p, ticks);
			p.displayClientMessage(Component.translatable("message.projecthero.kryptonite.suppressed").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
					true);
		}
		level.sendParticles(GREEN, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 40, target.getBbWidth() * 0.5,
				target.getBbHeight() * 0.4, target.getBbWidth() * 0.5, 0.0);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 2.0f, 0.5f);
	}

	/** Poisoned by kryptonite right now: no powers. */
	public static boolean suppressed(LivingEntity e) {
		Long until = SUPPRESSED.get(e.getUUID());
		return until != null && until > e.level().getGameTime();
	}

	/** Poisoned by kryptonite right now: no healing of any kind. */
	public static boolean healingBlocked(LivingEntity e) {
		return !e.level().isClientSide && suppressed(e);
	}

	public static void pruneExpired(long now) {
		SUPPRESSED.values().removeIf(t -> t <= now);
		HIT_COUNTS.values().removeIf(c -> now - c[1] > WINDOW_TICKS);
	}

	public static void clearSessionState() {
		SUPPRESSED.clear();
		HIT_COUNTS.clear();
	}
}
