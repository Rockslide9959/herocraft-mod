package com.projecthero.mod.vaultdungeon;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.5: The Vault's guards -- inmates, riot wardens, riot gunners. A guard is persistent (never despawns), tagged
 * {@link #TAG}, and kept near its post ({@code restrictTo}) until it has someone to chase. {@link #spawn} works on a
 * {@code WorldGenLevel} (the structure piece) and a {@code ServerLevel} (alarms, the Lockdown, tests).
 */
public final class VaultGuards {
	public static final String TAG = "projecthero.vault_guard";

	public enum Role {
		INMATE, WARDEN, GUNNER
	}

	private VaultGuards() {
	}

	public static boolean isGuard(Mob mob) {
		return mob.getTags().contains(TAG);
	}

	private static EntityType<? extends Mob> typeOf(Role role) {
		return switch (role) {
			case INMATE -> VaultEntityTypes.INMATE;
			case WARDEN -> VaultEntityTypes.RIOT_WARDEN;
			case GUNNER -> VaultEntityTypes.RIOT_GUNNER;
		};
	}

	/** A guard of {@code role} at {@code pos} (feet), facing {@code yaw}, kept within {@code leash} of its post. */
	public static Mob spawn(ServerLevelAccessor level, Role role, BlockPos pos, float yaw, int leash) {
		return spawn(level, role, pos, yaw, leash, false, null);
	}

	/**
	 * v0.17.7: as {@link #spawn(ServerLevelAccessor, Role, BlockPos, float, int)}, but optionally an alarm-zone guard
	 * ({@code alarmGuard}: it only fights once it has spotted you) with an alarm {@code alarmRole} -- both applied before
	 * the mob joins the level (worldgen saves an added mob straight into the chunk).
	 */
	public static Mob spawn(ServerLevelAccessor level, Role role, BlockPos pos, float yaw, int leash, boolean alarmGuard,
			com.projecthero.mod.alarm.GuardRole alarmRole) {
		return spawn(level, role, pos, yaw, leash, alarmGuard, alarmRole, null);
	}

	/** As above with one more entity tag set before it joins (the Maximum Security pod inmates). */
	public static Mob spawn(ServerLevelAccessor level, Role role, BlockPos pos, float yaw, int leash, boolean alarmGuard,
			com.projecthero.mod.alarm.GuardRole alarmRole, String extraTag) {
		Mob mob = typeOf(role).create(level.getLevel());
		if (mob == null) {
			return null;
		}
		mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
		mob.setPersistenceRequired();
		mob.addTag(TAG);
		if (leash > 0) {
			mob.restrictTo(pos, leash);
		}
		if (alarmGuard) {
			com.projecthero.mod.alarm.AlarmGuards.makeGuard(mob);
		}
		if (alarmRole != null) {
			com.projecthero.mod.alarm.AlarmGuards.setRole(mob, alarmRole);
		}
		if (extraTag != null) {
			mob.addTag(extraTag);
		}
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/** A laser tripwire snapped at {@code alarm}: sirens, and riot wardens rush in (3, +1 per extra player, max 5). */
	public static void alarm(ServerLevel level, BlockPos alarm) {
		List<ServerPlayer> players = VaultClearing.playersNear(level, alarm, 24);
		int squad = Math.min(5, 3 + Math.max(0, players.size() - 1));
		level.playSound(null, alarm, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.5f, 0.6f);
		level.playSound(null, alarm, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.6f, 1.6f);
		Component msg = Component.translatable("projecthero.vault.alarm").withStyle(ChatFormatting.RED);
		for (ServerPlayer p : players) {
			p.displayClientMessage(msg, true);
		}
		for (int i = 0; i < squad; i++) {
			BlockPos spot = findSpot(level, alarm, 3, 8, level.random);
			if (spot == null) {
				continue;
			}
			Mob m = spawn(level, i == 1 ? Role.GUNNER : Role.WARDEN, spot, level.random.nextFloat() * 360f, 14);
			if (m != null) {
				level.sendParticles(ParticleTypes.POOF, spot.getX() + 0.5, spot.getY() + 0.6, spot.getZ() + 0.5, 8, 0.3, 0.4, 0.3, 0.02);
				if (!players.isEmpty()) {
					m.setTarget(players.get(level.random.nextInt(players.size())));
				}
			}
		}
	}

	/**
	 * A free standing spot (solid floor, two air blocks) between {@code min} and {@code max} blocks from
	 * {@code around}, at roughly its height. Null after 32 misses.
	 */
	public static BlockPos findSpot(ServerLevel level, BlockPos around, int min, int max, RandomSource random) {
		for (int i = 0; i < 32; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double r = min + random.nextDouble() * (max - min);
			int x = around.getX() + (int) Math.round(Math.cos(a) * r);
			int z = around.getZ() + (int) Math.round(Math.sin(a) * r);
			for (int dy = 2; dy >= -3; dy--) {
				BlockPos feet = new BlockPos(x, around.getY() + dy, z);
				BlockState floor = level.getBlockState(feet.below());
				if (floor.isFaceSturdy(level, feet.below(), Direction.UP)
						&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
						&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
						&& level.getFluidState(feet).isEmpty()) {
					return feet;
				}
			}
		}
		return null;
	}
}
