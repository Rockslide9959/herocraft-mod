package com.projecthero.mod.ultron.contagion;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.reward.Valuables;
import com.projecthero.mod.horde.HordeRewards;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ultron.contagion.item.ContagionItems;
import com.projecthero.mod.ultron.item.UltronItems;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * v0.16.1: what beating Ultron Omega pays. The Vibranium Core becomes a reward chest on the ground below the citadel:
 * Vibranium Ingots x (2 + fighters), the Omega Core trophy, Ultron Scrap, netherite scrap, diamonds, emeralds and two
 * enchanted books, scaled for the party. A fighter's <b>first ever</b> Omega win ({@link #OMEGA_WINS}, persistent) also
 * empowers their Mind Stone (or hands them an empowered one) and gives JARVIS the permanent Firewall protocol (every suit
 * hijack-immune, {@link SuitHijack#FIREWALL_PROTOCOL}). Once per player, ever.
 */
public final class ContagionRewards {
	public static final AttachmentType<Integer> OMEGA_WINS = AttachmentRegistry.create(ProjectHeroMod.id("omega_wins"),
			b -> b.persistent(Codec.INT).copyOnDeath().initializer(() -> 0));

	private ContagionRewards() {
	}

	public static void initialize() {
		// loads the attachment
	}

	public static int ingotsFor(int fighters) {
		return Math.max(0, ContagionConfig.rewards().ingotBase + Math.max(1, fighters));
	}

	public static List<ItemStack> roll(RandomSource r, RegistryAccess registries, int fighters) {
		ContagionConfig.Rewards cfg = ContagionConfig.rewards();
		double more = Valuables.partyScale(fighters);
		double val = more * Math.max(0.0, cfg.valuablesMultiplier);
		List<ItemStack> out = new ArrayList<>();
		Valuables.stacks(out, ContagionItems.VIBRANIUM_INGOT, ingotsFor(fighters));
		out.add(new ItemStack(ContagionItems.OMEGA_CORE));
		Valuables.add(out, r, ContagionItems.ULTRON_SCRAP, cfg.scrapMin, Math.max(cfg.scrapMin, cfg.scrapMax), more);
		Valuables.add(out, r, Items.NETHERITE_SCRAP, cfg.netheriteMin, Math.max(cfg.netheriteMin, cfg.netheriteMax), more);
		Valuables.add(out, r, Items.DIAMOND, 8, 16, val);
		Valuables.add(out, r, Items.EMERALD, 16, 28, val);
		out.add(Valuables.book(r, registries, 30));
		out.add(Valuables.book(r, registries, 30));
		return out;
	}

	public static void placeChest(ServerLevel level, BlockPos pos, int fighters) {
		level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), Block.UPDATE_ALL);
		if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			HordeRewards.fill(chest, roll(level.random, level.registryAccess(), fighters), level.random);
			chest.setChanged();
		}
		level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0f, 0.8f);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 80, 0.5, 0.8, 0.5, 0.3);
	}

	public static int wins(Player p) {
		return p.getAttachedOrElse(OMEGA_WINS, 0);
	}

	/** Records a win; on the first, empowers the Mind Stone and turns on JARVIS's Firewall. True if it was the first. */
	public static boolean grantFirstWin(ServerPlayer p) {
		int before = wins(p);
		p.setAttached(OMEGA_WINS, before + 1);
		if (before > 0) {
			return false;
		}
		ContagionConfig.Rewards cfg = ContagionConfig.rewards();
		if (cfg.empoweredMindStone) {
			ItemStack stone = findMindStone(p);
			if (stone != null) {
				stone.set(ContagionItems.MIND_STONE_EMPOWERED, true);
			} else {
				ItemStack fresh = new ItemStack(UltronItems.MIND_STONE);
				fresh.set(ContagionItems.MIND_STONE_EMPOWERED, true);
				if (!p.getInventory().add(fresh)) {
					p.drop(fresh, false);
				}
			}
			p.sendSystemMessage(Component.translatable("message.projecthero.contagion.mind_stone_empowered").withStyle(ChatFormatting.GOLD));
		}
		if (cfg.firewallProtocol && TonyStark.hasPower(p)) {
			p.setAttached(SuitHijack.FIREWALL_PROTOCOL, true);
			p.sendSystemMessage(Component.translatable("message.projecthero.contagion.firewall_protocol").withStyle(ChatFormatting.AQUA));
		}
		p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.5f);
		return true;
	}

	private static ItemStack findMindStone(ServerPlayer p) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(UltronItems.MIND_STONE) && !ContagionItems.empowered(s)) {
				return s;
			}
		}
		return null;
	}
}
