package com.projecthero.mod.client;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.p26.MagneticMaterials;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-only, performance-bounded block indicator for Magnetic Manipulation's <b>Magnetic Sense</b>:
 * every few ticks it scans a small radius around the local player and drops a couple of restrained
 * particles onto magnetically-reactive blocks (iron blue, lodestone soul-flame, netherite dark).
 * Entity highlighting is handled separately by {@code EntityGlowMixin}. Nothing here touches the
 * server, and the scan is bounded in radius, cadence and particle count so it can never hitch.
 */
public final class MagneticSenseClient {
	private static final int INTERVAL_TICKS = 6;
	private static final int RADIUS = 10;
	private static final int MAX_MARKS = 36;

	private static final String KEY = "power_26_magnetic_manipulation";
	private static int counter;

	private MagneticSenseClient() {
	}

	public static void clientTick(Minecraft client) {
		LocalPlayer player = client.player;
		Level level = client.level;
		if (player == null || level == null) {
			return;
		}
		if (++counter % INTERVAL_TICKS != 0) {
			return;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !st.ownedPowers.contains(KEY) || !st.activeToggles.contains(KEY + "/magnetic_sense")) {
			return;
		}

		BlockPos origin = player.blockPosition();
		int marks = 0;
		for (BlockPos bp : BlockPos.betweenClosed(origin.offset(-RADIUS, -RADIUS, -RADIUS),
				origin.offset(RADIUS, RADIUS, RADIUS))) {
			if (marks >= MAX_MARKS) {
				break;
			}
			BlockState state = level.getBlockState(bp);
			if (!MagneticMaterials.isMagnetic(state) || level.random.nextInt(3) != 0) {
				continue;
			}
			ParticleOptions particle = MagneticMaterials.isLodestone(state) ? ParticleTypes.SOUL_FIRE_FLAME
					: MagneticMaterials.isNetherite(state) ? ParticleTypes.SMOKE
					: ParticleTypes.ELECTRIC_SPARK;
			level.addParticle(particle, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5,
					(level.random.nextDouble() - 0.5) * 0.02, 0.02, (level.random.nextDouble() - 0.5) * 0.02);
			marks++;
		}
	}
}
