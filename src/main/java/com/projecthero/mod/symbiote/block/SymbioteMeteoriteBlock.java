package com.projecthero.mod.symbiote.block;

import com.projecthero.mod.symbiote.SymbioteSounds;
import com.projecthero.mod.symbiote.entity.SymbioteEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Symbiote Meteorite: the chunk of alien rock a Symbiote rode down to Earth inside. It sits at the
 * core of every {@code symbiote_meteor} crater with the organism dormant inside it.
 *
 * <p><b>Breaking it releases the organism</b>: a free {@link SymbioteEntity} crawls out of the shattered
 * rock (burst of ichor, a wet sculk crack, and a chat line to everyone nearby). That happens on
 * {@link #playerWillDestroy} -- which runs for every player break, survival or creative, with or
 * without the right tool -- and on {@link #wasExploded}, so a creeper cannot silently delete the
 * organism either. The block itself drops only a little blackstone (see its loot table).
 */
public class SymbioteMeteoriteBlock extends Block {
	public SymbioteMeteoriteBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server) {
			release(server, pos);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}

	@Override
	public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
		super.wasExploded(level, pos, explosion);
		if (level instanceof ServerLevel server) {
			release(server, pos);
		}
	}

	/** The organism crawls out of the rock at {@code pos}. Returns the new free Symbiote (or null). */
	public static SymbioteEntity release(ServerLevel level, BlockPos pos) {
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.05;
		double z = pos.getZ() + 0.5;
		SymbioteEntity symbiote = SymbioteEntity.spawn(level, x, y, z);
		if (symbiote == null) {
			return null;
		}
		// A moment to be seen before it goes hunting -- it has only just woken up.
		symbiote.setHuntDelay(60);
		symbiote.recoil();
		level.sendParticles(ParticleTypes.SQUID_INK, x, y + 0.5, z, 18, 0.35, 0.35, 0.35, 0.05);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.5, z, 30, 0.4, 0.4, 0.4, 0.05);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.4, z, 5, 0.3, 0.2, 0.3, 0.01);
		SymbioteSounds.organic(level, x, y, z, 1.4f, 0.45f);
		level.playSound(null, x, y, z, SoundEvents.SCULK_SHRIEKER_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
		for (Player p : level.getEntitiesOfClass(Player.class, symbiote.getBoundingBox().inflate(24.0))) {
			p.displayClientMessage(Component.translatable("message.projecthero.symbiote.meteorite_breach")
					.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
		}
		return symbiote;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		// Something alive is in there: the odd violet drip and, rarely, a slow heartbeat.
		if (random.nextInt(4) == 0) {
			double x = pos.getX() + random.nextDouble();
			double z = pos.getZ() + random.nextDouble();
			level.addParticle(ParticleTypes.REVERSE_PORTAL, x, pos.getY() + 1.02, z, 0.0, 0.01, 0.0);
		}
		if (random.nextInt(10) == 0) {
			level.addParticle(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, pos.getX() + random.nextDouble(),
					pos.getY() - 0.05, pos.getZ() + random.nextDouble(), 0.0, 0.0, 0.0);
		}
		if (random.nextInt(90) == 0) {
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.WARDEN_HEARTBEAT,
					SoundSource.BLOCKS, 0.35f, 1.3f, false);
		}
	}
}
