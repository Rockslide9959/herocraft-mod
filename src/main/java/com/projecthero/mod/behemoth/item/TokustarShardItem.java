package com.projecthero.mod.behemoth.item;

import java.util.List;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.joml.Vector3f;

/**
 * v0.17.2 (user: "create an item that lets the user unlock this alien please, make it something you need to get in the
 * nether, maybe an abyssal behemoth drop"): <b>the To'kustar Shard</b> -- a sliver of cosmic-storm crystal the ancient
 * Behemoth swallowed long ago, carrying To'kustar DNA. One drops from every Behemoth killed. Right-clicked by a wearer of the
 * <em>prototype</em> Omnitrix it is absorbed and unlocks Way Big ({@link Aliens#discover}); refused on the Recalibrated
 * Omnitrix, without a watch, or once Way Big is already unlocked -- and never consumed when refused.
 */
public class TokustarShardItem extends Item {
	/** The alien this shard carries. */
	public static final String ALIEN = "way_big";

	public enum Result {
		UNLOCKED("message.projecthero.tokustar_shard.unlocked"),
		NO_WATCH("message.projecthero.tokustar_shard.no_watch"),
		RECALIBRATED("message.projecthero.tokustar_shard.recalibrated"),
		ALREADY("message.projecthero.tokustar_shard.already");

		public final String key;

		Result(String key) {
			this.key = key;
		}
	}

	public TokustarShardItem(Properties properties) {
		super(properties);
	}

	/** What using a shard would do for {@code p} right now (client-safe: reads the synced Omnitrix state). */
	public static Result check(Player p) {
		if (!Omnitrix.hasPower(p)) {
			return Result.NO_WATCH;
		}
		if (Omnitrix.recalibrated(p)) {
			return Result.RECALIBRATED;
		}
		if (Omnitrix.state(p).readyAt(Aliens.foundKey(ALIEN)) == 1L) {
			return Result.ALREADY;
		}
		return Result.UNLOCKED;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		Result r = check(player);
		if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
			return r == Result.UNLOCKED ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
		}
		if (r != Result.UNLOCKED) {
			sp.displayClientMessage(Component.translatable(r.key).withStyle(ChatFormatting.GOLD), true);
			server.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.8f, 0.6f);
			return InteractionResultHolder.fail(stack);
		}
		Aliens.discover(sp, ALIEN, true);
		stack.consume(1, sp);
		sp.displayClientMessage(Component.translatable(r.key).withStyle(ChatFormatting.YELLOW), true);
		double x = sp.getX(), y = sp.getY() + 1.0, z = sp.getZ();
		server.playSound(null, x, y, z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 0.7f);
		server.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.5f);
		server.playSound(null, x, y, z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
		server.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
		server.sendParticles(ParticleTypes.END_ROD, x, y, z, 60, 0.6, 1.0, 0.6, 0.12);
		server.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 40, 0.5, 0.8, 0.5, 0.3);
		server.sendParticles(new DustParticleOptions(new Vector3f(0.55f, 1.0f, 0.35f), 1.6f), x, y, z, 50, 0.7, 1.0, 0.7, 0.0);
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.tokustar_shard.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.tokustar_shard.tooltip2").withStyle(ChatFormatting.GREEN));
	}
}
