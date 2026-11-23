package com.projecthero.mod.skyfortress;

import com.projecthero.mod.alarm.AlarmGuards;
import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.skyfortress.entity.SkyGunner;
import com.projecthero.mod.skyfortress.entity.SkyTrooper;
import com.projecthero.mod.syndicate.entity.SyndicateGunman;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * v0.17.7: the Sky Fortress's guards. Every one is an alarm guard ({@link AlarmGuards#makeGuard}), persistent, tagged
 * {@link #TAG}, held near its post ({@code restrictTo}) until it has someone to chase; its alarm role (if any) is set
 * before it joins the level. {@link #spawn} works on a {@code WorldGenLevel} and a {@code ServerLevel}.
 */
public final class SkyFortressGuards {
	public static final String TAG = "projecthero.sky_guard";

	public enum Kind {
		/** A deck trooper (baton / cutlass). */
		TROOPER,
		/** A gunner with a sidearm. */
		GUNNER,
		/** A gunner with a rifle (pair with {@code GuardRole.SNIPER}). */
		RIFLE,
		/** The Launch Pad's Pad Sergeant (drops the Fortress Pass). */
		SERGEANT
	}

	private SkyFortressGuards() {
	}

	public static boolean isGuard(Mob mob) {
		return mob.getTags().contains(TAG);
	}

	public static Mob spawn(ServerLevelAccessor level, Kind kind, BlockPos pos, float yaw, int leash, GuardRole role) {
		Mob mob = switch (kind) {
			case TROOPER, SERGEANT -> SkyFortressEntityTypes.TROOPER.create(level.getLevel());
			case GUNNER, RIFLE -> SkyFortressEntityTypes.GUNNER.create(level.getLevel());
		};
		if (mob == null) {
			return null;
		}
		mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
		if (kind == Kind.RIFLE && mob instanceof SkyGunner g) {
			g.setKind(SyndicateGunman.Kind.SNIPER);
		}
		if (kind == Kind.SERGEANT && mob instanceof SkyTrooper t) {
			t.setVariant(SkyTrooper.SERGEANT);
			t.setCustomName(net.minecraft.network.chat.Component.translatable("entity.projecthero.pad_sergeant"));
		}
		mob.setPersistenceRequired();
		mob.addTag(TAG);
		AlarmGuards.makeGuard(mob);
		if (leash > 0) {
			mob.restrictTo(pos, leash);
		}
		if (role != null) {
			AlarmGuards.setRole(mob, role);
		}
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/** An alarm reinforcement at {@code spot}: two troopers to every gunner, dropping in on a cloud of jet wash. */
	public static Mob reinforcement(ServerLevel level, BlockPos spot, RandomSource random) {
		Mob mob = spawn(level, random.nextInt(3) == 0 ? Kind.GUNNER : Kind.TROOPER, spot, random.nextFloat() * 360f, 14, null);
		if (mob != null) {
			level.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 0.3, mob.getZ(), 14, 0.4, 0.2, 0.4, 0.05);
			level.sendParticles(ParticleTypes.FLAME, mob.getX(), mob.getY() + 0.1, mob.getZ(), 6, 0.2, 0.05, 0.2, 0.01);
			level.playSound(null, spot, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.HOSTILE, 0.8f, 0.6f);
		}
		return mob;
	}
}
