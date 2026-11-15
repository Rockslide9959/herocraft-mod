package com.projecthero.mod.hideout;

import java.util.List;

import com.projecthero.mod.hideout.block.HideoutBlocks;
import com.projecthero.mod.hideout.block.HideoutCoreBlock;
import com.projecthero.mod.hideout.block.HideoutSealBlock;
import com.projecthero.mod.loot.HeroVault;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import org.joml.Vector3f;

/**
 * v0.17.5: how a hideout is cleared.
 * <ol>
 * <li>The mini-boss dies -> {@link #bossDefeated}: recorded in {@link HideoutState}, the core's seal drops.</li>
 * <li>A player breaks the (unsealed) core -> {@link #coreBroken}: the hideout is marked cleared for good, every sealed
 * vault near the core opens into a Tier 3 Hero Vault ({@link #unsealVaults}) and the faction's raids calm nearby
 * ({@link HideoutCalm}).</li>
 * </ol>
 * A sealed core someone hits checks that its guardian still exists ({@link #pokeSealed}) -- if the mini-boss vanished
 * without dying (Peaceful, a command) the seal drops anyway, so a hideout can never get stuck.
 */
public final class HideoutClearing {
	/** How far from the core sealed vaults are looked for. */
	public static final int VAULT_SEARCH = 20;
	/** How far from the core the guardian is looked for before a sealed core gives up on it. */
	public static final double GUARDIAN_SEARCH = 96.0;

	private HideoutClearing() {
	}

	/** The mini-boss of the hideout whose core is at {@code core} has died. */
	public static void bossDefeated(ServerLevel level, BlockPos core, HideoutFaction faction) {
		if (core == null) {
			return; // a spawn-egg boss belongs to no hideout
		}
		HideoutState.get(level).markBossDead(core, faction);
		if (level.isLoaded(core)) {
			unsealCore(level, core);
		}
		Component msg = Component.translatable("projecthero.hideout.boss_down", faction.bossName(), faction.hideoutName())
				.withStyle(s -> s.withColor(faction.colour()));
		for (ServerPlayer p : playersNear(level, core, 64)) {
			p.displayClientMessage(msg, false);
		}
	}

	/** Drops the core's seal (if it is still a sealed core). True if it changed. */
	public static boolean unsealCore(ServerLevel level, BlockPos core) {
		BlockState s = level.getBlockState(core);
		if (!s.is(HideoutBlocks.CORE) || !s.getValue(HideoutCoreBlock.SEALED)) {
			return false;
		}
		level.setBlock(core, s.setValue(HideoutCoreBlock.SEALED, false), 3);
		level.playSound(null, core, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0f, 0.6f);
		level.playSound(null, core, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.5f, 0.7f);
		level.sendParticles(dust(s.getValue(HideoutCoreBlock.FACTION).colour(), 2.0f), core.getX() + 0.5, core.getY() + 0.5,
				core.getZ() + 0.5, 60, 0.8, 0.8, 0.8, 0.05);
		return true;
	}

	/** Someone hit or used a sealed core: tell them why, and drop the seal if the guardian is gone. */
	public static void pokeSealed(ServerLevel level, BlockPos core, BlockState state, Player player) {
		HideoutFaction faction = state.getValue(HideoutCoreBlock.FACTION);
		if (HideoutState.get(level).bossDead(core) || !guardianPresent(level, core)) {
			HideoutState.get(level).markBossDead(core, faction);
			unsealCore(level, core);
			return;
		}
		player.displayClientMessage(Component.translatable("block.projecthero.hideout_core.sealed", faction.bossName())
				.withStyle(s -> s.withColor(faction.colour())), true);
	}

	/** True when a living mini-boss bound to {@code core} is loaded within {@link #GUARDIAN_SEARCH}. */
	public static boolean guardianPresent(ServerLevel level, BlockPos core) {
		AABB box = new AABB(core).inflate(GUARDIAN_SEARCH);
		return !level.getEntitiesOfClass(Mob.class, box,
				m -> m.isAlive() && m instanceof HideoutBoss b && core.equals(b.hideoutLink().core())).isEmpty();
	}

	/** The hint shown on an open core: "Break the core to clear the hideout." */
	public static Component coreOpenHint(HideoutFaction faction) {
		return Component.translatable("block.projecthero.hideout_core.open", faction.hideoutName()).withStyle(s -> s.withColor(faction.colour()));
	}

	/** The unsealed core at {@code core} was broken: the hideout is cleared. */
	public static void coreBroken(ServerLevel level, BlockPos core, HideoutFaction faction) {
		boolean first = HideoutState.get(level).markCleared(core, faction, level.getGameTime());
		int opened = unsealVaults(level, core);
		level.playSound(null, core, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.4f, 1.2f);
		level.playSound(null, core, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5, 1, 0, 0, 0, 0);
		level.sendParticles(dust(faction.colour(), 2.4f), core.getX() + 0.5, core.getY() + 0.8, core.getZ() + 0.5, 120, 2.0, 1.5, 2.0, 0.1);
		if (!first) {
			return;
		}
		Component title = Component.translatable("projecthero.hideout.cleared.title").withStyle(s -> s.withColor(faction.colour()).withBold(true));
		Component sub = Component.translatable("projecthero.hideout.cleared.sub", faction.hideoutName()).withStyle(ChatFormatting.GRAY);
		for (ServerPlayer p : playersNear(level, core, 64)) {
			p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
			p.connection.send(new ClientboundSetTitleTextPacket(title));
			p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
			p.displayClientMessage(Component.translatable("projecthero.hideout.cleared.chat", faction.hideoutName()).withStyle(s -> s.withColor(faction.colour())), false);
			if (opened > 0) {
				p.displayClientMessage(Component.translatable("projecthero.hideout.cleared.vault").withStyle(ChatFormatting.LIGHT_PURPLE), false);
			}
			p.displayClientMessage(HideoutCalm.clearedNote(faction), false);
		}
	}

	/** Every sealed vault within {@link #VAULT_SEARCH} of {@code core} becomes an open Tier 3 Hero Vault. Returns how many. */
	public static int unsealVaults(ServerLevel level, BlockPos core) {
		int n = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -VAULT_SEARCH; dx <= VAULT_SEARCH; dx++) {
			for (int dy = -8; dy <= 8; dy++) {
				for (int dz = -VAULT_SEARCH; dz <= VAULT_SEARCH; dz++) {
					m.set(core.getX() + dx, core.getY() + dy, core.getZ() + dz);
					BlockState s = level.getBlockState(m);
					if (s.is(HideoutBlocks.SEAL)) {
						BlockPos at = m.immutable();
						HeroVault.place(level, at, 3, s.getValue(HideoutSealBlock.FACING));
						level.sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.05);
						level.playSound(null, at, SoundEvents.VAULT_OPEN_SHUTTER, SoundSource.BLOCKS, 1.5f, 0.8f);
						n++;
					}
				}
			}
		}
		return n;
	}

	static List<ServerPlayer> playersNear(ServerLevel level, BlockPos pos, double radius) {
		return level.getPlayers(p -> p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= radius * radius);
	}

	static DustParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), size);
	}
}
