package com.projecthero.mod.temple;

import com.projecthero.mod.alarm.AlarmGuards;
import com.projecthero.mod.alarm.GuardRole;
import com.projecthero.mod.temple.entity.CultAcolyte;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.17.7: the Ancient Temple's guards -- temple guardians, wrapped revenants and cult acolytes (sniper acolytes carry a
 * bow). A guard is persistent, tagged {@link #TAG} and an alarm-framework guard ({@link AlarmGuards#makeGuard}), kept near
 * its post, and given its alarm role before it joins the level (worldgen saves an added mob straight into the chunk).
 * {@link #spawn} works on a {@code WorldGenLevel} and a {@code ServerLevel}; {@link #reinforcement} is the temple's
 * alarm reinforcer: guardians and revenants clawing up out of the sand.
 */
public final class TempleGuards {
	public static final String TAG = "projecthero.temple_guard";

	public enum Role {
		GUARDIAN, REVENANT, ACOLYTE, ARCHER
	}

	private TempleGuards() {
	}

	public static boolean isGuard(Mob mob) {
		return mob.getTags().contains(TAG);
	}

	private static EntityType<? extends Mob> typeOf(Role role) {
		return switch (role) {
			case GUARDIAN -> TempleEntityTypes.GUARDIAN;
			case REVENANT -> TempleEntityTypes.REVENANT;
			case ACOLYTE, ARCHER -> TempleEntityTypes.ACOLYTE;
		};
	}

	/** A guard of {@code role} at {@code pos} (feet), facing {@code yaw}, leashed {@code leash}, with an alarm role (or null). */
	public static Mob spawn(ServerLevelAccessor level, Role role, BlockPos pos, float yaw, int leash, GuardRole alarmRole) {
		return spawn(level, role, pos, yaw, leash, alarmRole, true);
	}

	/** As above; {@code guard} false = an ordinary temple mob (boss summons), not an alarm guard. */
	public static Mob spawn(ServerLevelAccessor level, Role role, BlockPos pos, float yaw, int leash, GuardRole alarmRole, boolean guard) {
		Mob mob = typeOf(role).create(level.getLevel());
		if (mob == null) {
			return null;
		}
		mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
		if (role == Role.ARCHER && mob instanceof CultAcolyte a) {
			a.holdBow();
		}
		mob.setPersistenceRequired();
		if (guard) {
			mob.addTag(TAG);
			AlarmGuards.makeGuard(mob);
		}
		if (leash > 0) {
			mob.restrictTo(pos, leash);
		}
		if (alarmRole != null) {
			AlarmGuards.setRole(mob, alarmRole);
		}
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/** The temple's alarm reinforcer: a guardian (2 in 3) or a revenant rising out of the sand at {@code spot}. */
	public static Mob reinforcement(ServerLevel level, BlockPos spot, RandomSource random) {
		Mob m = spawn(level, random.nextInt(3) == 0 ? Role.REVENANT : Role.GUARDIAN, spot, random.nextFloat() * 360f, 14, null);
		if (m != null) {
			sandRise(level, spot);
		}
		return m;
	}

	/** The sand burst of something rising out of the floor. */
	public static void sandRise(ServerLevel level, BlockPos spot) {
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), spot.getX() + 0.5, spot.getY() + 0.3,
				spot.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.1);
		level.sendParticles(ParticleTypes.SOUL, spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5, 6, 0.3, 0.5, 0.3, 0.03);
		level.playSound(null, spot, net.minecraft.sounds.SoundEvents.SAND_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 1.5f, 0.6f);
	}

	/** A free standing spot (solid floor, two air, no fluid... water ankle-deep is fine) {@code min}..{@code max} from {@code around}. */
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
						&& level.getFluidState(feet.above()).isEmpty()) {
					return feet;
				}
			}
		}
		return null;
	}
}
