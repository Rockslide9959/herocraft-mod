package com.projecthero.mod.raven;

import com.projecthero.mod.combat.HeroTargets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Raven: the <b>emotional leak</b>. Under {@link RavenConfig#LEAK_BELOW} Composure, now and then (about every 5 s) her power
 * slips out: a pane of glass or a lamp nearby cracks, or a creature near her is shoved away. Glass and lamps only really break with the
 * {@code mobGriefing} gamerule on; with it off the crack is cosmetic (the shatter, the sound, nothing changes). Never block entities.
 */
public final class RavenLeak {
	private RavenLeak() {
	}

	static void tick(ServerPlayer player) {
		if (player.getRandom().nextFloat() >= RavenConfig.LEAK_CHANCE_PER_TICK || Raven.meditating(player)) {
			return;
		}
		leak(player);
	}

	/** One leak right now (the tests call it directly). Returns what happened: 1 = a crack, 2 = a push, 0 = just a shudder. */
	public static int leak(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		boolean preferPush = player.getRandom().nextInt(3) == 0;
		if (!preferPush) {
			BlockPos crack = findFragile(player);
			if (crack != null) {
				crack(level, crack);
				return 1;
			}
		}
		Mob mob = level.getNearestEntity(Mob.class, net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat(), player,
				player.getX(), player.getY(), player.getZ(), player.getBoundingBox().inflate(RavenConfig.LEAK_RADIUS));
		if (mob != null && HeroTargets.canHarm(player, mob) && !Raven.isBoss(mob)) {
			Vec3 away = mob.position().subtract(player.position()).multiply(1, 0, 1);
			if (away.lengthSqr() < 1.0e-4) {
				away = new Vec3(1, 0, 0);
			}
			away = away.normalize();
			mob.push(away.x * 0.9, 0.35, away.z * 0.9);
			mob.hurtMarked = true;
			Raven.darkPuff(level, mob.position().add(0, mob.getBbHeight() * 0.5, 0), 10, 0.4);
			return 2;
		}
		if (preferPush) {
			BlockPos crack = findFragile(player);
			if (crack != null) {
				crack(level, crack);
				return 1;
			}
		}
		Raven.darkPuff(level, player.position().add(0, 1.0, 0), 12, 0.7);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), RavenSounds.SHIELD_HIT, SoundSource.PLAYERS, 0.25f, 0.5f);
		return 0;
	}

	/** Glass, glass panes, glowstone, sea lanterns, redstone lamps, lanterns -- anything that would shatter. */
	public static boolean fragile(BlockState state) {
		if (state.isAir() || state.hasBlockEntity()) {
			return false;
		}
		return state.getSoundType() == SoundType.GLASS || state.getBlock() instanceof LanternBlock;
	}

	private static BlockPos findFragile(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos c = player.blockPosition();
		int r = RavenConfig.LEAK_RADIUS;
		for (int tries = 0; tries < 48; tries++) {
			BlockPos pos = c.offset(player.getRandom().nextInt(2 * r + 1) - r, player.getRandom().nextInt(7) - 2,
					player.getRandom().nextInt(2 * r + 1) - r);
			if (fragile(level.getBlockState(pos))) {
				return pos;
			}
		}
		// a careful sweep when the random tries missed (small rooms)
		for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 4, r))) {
			if (fragile(level.getBlockState(pos))) {
				return pos.immutable();
			}
		}
		return null;
	}

	/** It cracks: really broken with mob griefing on, just the shatter with it off. */
	public static void crack(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		Vec3 c = Vec3.atCenterOf(pos);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), c.x, c.y, c.z, 20, 0.3, 0.3, 0.3, 0.1);
		level.sendParticles(Raven.dust(Raven.VOID, 1.2f), c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.0);
		if (RavenRestore.griefing(level)) {
			level.destroyBlock(pos, false);
		} else {
			level.playSound(null, pos, RavenSounds.CRACK, SoundSource.BLOCKS, 0.35f, 1.3f);
		}
	}
}
