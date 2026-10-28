package com.projecthero.mod.omnitrix;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * v0.17.1: the Omnitrix's damage.
 * <ul>
 *   <li>{@code projecthero:omnitrix_extraction} -- the Containment Pod's pulses. In {@code bypasses_armor},
 *       {@code bypasses_enchantments}, {@code bypasses_effects} (so no Resistance) and {@code bypasses_invulnerability} (so a
 *       totem of undying never pops: vanilla's totem check skips such damage), never in {@code bypasses_absorption}-style
 *       tags (there are none in 1.21.1: Absorption always soaks it). No difficulty scaling, no exhaustion, no knockback.</li>
 *   <li>{@code projecthero:alien_strike} -- an alien's physical moves (a player attack: it feeds boss threat tables).</li>
 *   <li>{@code projecthero:alien_fire} -- Heatblast's fire (in {@code is_fire}: fire-immune mobs shrug it off).</li>
 * </ul>
 * The transformed wearer's kit vetoes or changes incoming damage here ({@link AlienKit#allowDamage},
 * {@link AlienKit#incomingDamage}): a change cancels and re-issues the hit behind a re-entrancy guard (the
 * {@code QuirkDamage} pattern).
 */
public final class OmnitrixDamage {
	public static final ResourceKey<DamageType> EXTRACTION = key("omnitrix_extraction");
	public static final ResourceKey<DamageType> ALIEN_STRIKE = key("alien_strike");
	public static final ResourceKey<DamageType> ALIEN_FIRE = key("alien_fire");

	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private OmnitrixDamage() {
	}

	private static ResourceKey<DamageType> key(String path) {
		return ResourceKey.create(Registries.DAMAGE_TYPE, ProjectHeroMod.id(path));
	}

	static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(OmnitrixDamage::allowDamage);
	}

	public static DamageSource extraction(Level level) {
		return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(EXTRACTION));
	}

	public static DamageSource alienStrike(Entity attacker) {
		return new DamageSource(attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ALIEN_STRIKE),
				attacker);
	}

	public static DamageSource alienFire(Entity attacker) {
		return new DamageSource(attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ALIEN_FIRE),
				attacker);
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0f || !(entity instanceof ServerPlayer player)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true; // /kill, the void and the pod's own pulses are never touched
		}
		if (!Omnitrix.hasPower(player)) {
			return true;
		}
		AlienKit kit = Aliens.kit(Omnitrix.state(player).active);
		if (kit == null) {
			return true;
		}
		if (!kit.allowDamage(player, source, amount)) {
			return false;
		}
		float changed = kit.incomingDamage(player, source, amount);
		if (Math.abs(changed - amount) < 1.0e-4f) {
			return true;
		}
		if (changed > 0.01f) {
			if (source.scalesWithDifficulty()) {
				changed = switch (player.level().getDifficulty()) {
					case HARD -> changed / 1.5f;
					case EASY -> changed >= 2f ? 2f * (changed - 1f) : changed;
					default -> changed;
				};
			}
			REENTRANT.set(true);
			try {
				player.hurt(source, changed);
			} finally {
				REENTRANT.set(false);
			}
		}
		return false;
	}
}
