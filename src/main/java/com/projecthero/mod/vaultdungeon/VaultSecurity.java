package com.projecthero.mod.vaultdungeon;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.alarm.AlarmProfile;
import com.projecthero.mod.alarm.AlarmProfiles;
import com.projecthero.mod.alarm.AlarmZone;
import com.projecthero.mod.alarm.AlarmZones;
import com.projecthero.mod.alarm.block.AlarmBlocks;
import com.projecthero.mod.alarm.block.AlarmPanelBlock;
import com.projecthero.mod.vaultdungeon.block.VaultBarrierBlock;
import com.projecthero.mod.vaultdungeon.block.VaultBlocks;
import com.projecthero.mod.vaultdungeon.block.VaultBreakerBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.7: The Vault's security systems on the two new floors, and its alarm profile.
 * <ul>
 * <li><b>The cell-block power</b> -- {@link #shutDown}: the Control Room breaker cuts it for good. Every blue
 * {@link VaultBarrierBlock.Kind#POWER} field within {@link #POWER_REACH} drops (the stair down to the Warden's Level
 * opens, and so do the Maximum Security pods: their inmates come out fighting), and every Alarm Panel within
 * {@link #POWER_REACH} of the breaker except the Control Room's own goes dead -- the Maximum Security wing's cameras and
 * lasers with it.</li>
 * <li><b>The Riot Captain's lock</b> -- {@link #captainDown}: the red {@link VaultBarrierBlock.Kind#CAPTAIN} fields
 * within {@link #CAPTAIN_REACH} of his post drop when he dies.</li>
 * </ul>
 * The Vault is an alarm zone (profile {@link #PROFILE}, tier 4): reinforcement waves are riot wardens and gunners.
 * No static per-world state.
 */
public final class VaultSecurity {
	public static final String PROFILE = "the_vault";
	/** Pod inmates (behind the blue fields in the Maximum Security hall). */
	public static final String POD_TAG = "projecthero.vault_pod";
	public static final int POWER_REACH = 34;
	public static final int CAPTAIN_REACH = 30;
	/** The Control Room's own panel is spared: within this many blocks of the breaker. */
	static final int SPARE_PANEL = 10;
	/** Panels on the breaker's floor this close die with the power (the Maximum Security wing's). */
	public static final int PANEL_REACH = 50;

	private VaultSecurity() {
	}

	/** Registers the alarm profile (from {@link VaultDungeonMod#initialize}). */
	public static void registerAlarm() {
		AlarmProfiles.register(new AlarmProfile(PROFILE, 4, 0xC0392B, Component.translatable("structure.projecthero.the_vault"),
				(level, spot, random) -> {
					VaultGuards.Role role = random.nextInt(3) == 0 ? VaultGuards.Role.GUNNER : VaultGuards.Role.WARDEN;
					Mob m = VaultGuards.spawn(level, role, spot, random.nextFloat() * 360f, 14);
					if (m != null) {
						level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 0.8, m.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
					}
					return m;
				}, SoundEvents.BELL_BLOCK, 0.5f));
		AlarmProfiles.registerStructure(ProjectHeroMod.id("the_vault"), PROFILE);
	}

	/**
	 * Cuts the cell-block power at the breaker {@code breaker}. Returns how many blue field blocks dropped (0 if the
	 * breaker was already off).
	 */
	public static int shutDown(ServerLevel level, BlockPos breaker, Player player) {
		BlockState s = level.getBlockState(breaker);
		if (s.is(VaultBlocks.BREAKER)) {
			if (!s.getValue(VaultBreakerBlock.POWERED)) {
				return 0;
			}
			level.setBlock(breaker, s.setValue(VaultBreakerBlock.POWERED, false), 3);
		}
		int fields = dropFields(level, breaker, POWER_REACH, 8, VaultBarrierBlock.Kind.POWER);
		// the Maximum Security wing's panels die with the power (its cameras / lasers are wired to them)
		int panels = 0;
		AlarmZone z = AlarmZones.find(level, breaker);
		if (z != null) {
			AlarmZones.scan(level, z);
			for (long l : new ArrayList<>(z.panels)) {
				BlockPos p = BlockPos.of(l);
				if (!level.isLoaded(p) || p.distSqr(breaker) > (double) PANEL_REACH * PANEL_REACH || p.distSqr(breaker) <= SPARE_PANEL * SPARE_PANEL
						|| Math.abs(p.getY() - breaker.getY()) > 3) {
					continue;
				}
				BlockState ps = level.getBlockState(p);
				if (ps.is(AlarmBlocks.PANEL) && !ps.getValue(AlarmPanelBlock.SABOTAGED)) {
					level.setBlock(p, ps.setValue(AlarmPanelBlock.SABOTAGED, true).setValue(AlarmPanelBlock.ALERT, false), 3);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.2);
					panels++;
				}
			}
			AlarmZones.get(level).changed();
		}
		// the pods are open: their inmates come out
		List<ServerPlayer> players = VaultClearing.playersNear(level, breaker, 48);
		for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(breaker).inflate(POWER_REACH, 10, POWER_REACH), m -> m.getTags().contains(POD_TAG))) {
			m.restrictTo(m.blockPosition(), 24);
			if (!players.isEmpty()) {
				m.setTarget(nearest(m, players));
			}
		}
		level.playSound(null, breaker, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0f, 0.5f);
		level.playSound(null, breaker, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6f, 1.8f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, breaker.getX() + 0.5, breaker.getY() + 0.8, breaker.getZ() + 0.5, 40, 0.5, 0.5, 0.5, 0.3);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, breaker.getX() + 0.5, breaker.getY() + 1.0, breaker.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.02);
		Component msg = Component.translatable("projecthero.vault.power_down").withStyle(ChatFormatting.GOLD);
		for (ServerPlayer p : players) {
			p.displayClientMessage(msg, false);
		}
		ProjectHeroMod.LOGGER.debug("Vault power down at {}: {} field blocks, {} panels", breaker, fields, panels);
		return fields;
	}

	/** The Riot Captain died at his post {@code home}: his red fields drop. Returns how many blocks dropped. */
	public static int captainDown(ServerLevel level, BlockPos home) {
		int n = dropFields(level, home, CAPTAIN_REACH, 8, VaultBarrierBlock.Kind.CAPTAIN);
		if (n > 0) {
			level.playSound(null, home, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0f, 1.2f);
			Component msg = Component.translatable("projecthero.vault.captain_down").withStyle(ChatFormatting.GOLD);
			for (ServerPlayer p : VaultClearing.playersNear(level, home, 48)) {
				p.displayClientMessage(msg, false);
			}
		}
		return n;
	}

	/** Removes every field of {@code kind} within {@code r} (horizontal) / {@code dy} (vertical) of {@code centre}. */
	public static int dropFields(ServerLevel level, BlockPos centre, int r, int dy, VaultBarrierBlock.Kind kind) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -dy, -r), centre.offset(r, dy, r))) {
			BlockState s = level.getBlockState(p);
			if (s.is(VaultBlocks.BARRIER) && s.getValue(VaultBarrierBlock.KIND) == kind) {
				level.removeBlock(p, false);
				if (n % 3 == 0) {
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
				}
				n++;
			}
		}
		return n;
	}

	private static ServerPlayer nearest(Mob m, List<ServerPlayer> players) {
		ServerPlayer best = null;
		double bd = Double.MAX_VALUE;
		for (ServerPlayer p : players) {
			if (p.isCreative() || p.isSpectator()) {
				continue;
			}
			double d = p.distanceToSqr(m);
			if (d < bd) {
				bd = d;
				best = p;
			}
		}
		return best;
	}
}
