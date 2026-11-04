package com.projecthero.mod.behemoth.item;

import java.util.List;

import com.projecthero.mod.behemoth.BehemothConfig;
import com.projecthero.mod.behemoth.BehemothSpawner;
import com.projecthero.mod.behemoth.entity.AbyssalBehemothEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "create an item that lures him in aswell that will spawn him in to fight please"): <b>the Abyssal Lure</b>
 * -- a crying-obsidian cage of ghast tears around a blaze-lit netherite heart. Used in the Nether it calls the Abyssal
 * Behemoth up: it rises in genuinely open air ({@link BehemothSpawner#findSpot}, its whole box clear, below the roof)
 * {@code lureMinDistance}-{@code lureMaxDistance} blocks away -- never on top of the user -- preferring a spot that can
 * see them, and comes for them ({@link AbyssalBehemothEntity#lure}). Refused (not consumed) outside the Nether, while a
 * Behemoth is already alive within {@code lureActiveRadius}, or when there is no cavern big enough for it nearby.
 * It does not touch the natural-spawn cooldown.
 */
public class AbyssalLureItem extends Item {
	public enum Result {
		SUMMONED("message.projecthero.behemoth.lure.summoned"),
		NOT_NETHER("message.projecthero.behemoth.lure.not_nether"),
		ALREADY_ACTIVE("message.projecthero.behemoth.lure.already"),
		NO_SPACE("message.projecthero.behemoth.lure.no_space");

		public final String key;

		Result(String key) {
			this.key = key;
		}
	}

	/** What a summon did, and the Behemoth it called (null unless {@link Result#SUMMONED}). */
	public record Summon(Result result, AbyssalBehemothEntity behemoth) {
	}

	public AbyssalLureItem(Properties properties) {
		super(properties);
	}

	/** The lure's effect at {@code from} in {@code level}, for {@code user} (who it then hunts; may be null in tests). */
	public static Summon summon(ServerLevel level, Vec3 from, LivingEntity user) {
		var cfg = BehemothConfig.world();
		if (level.dimension() != Level.NETHER) {
			return new Summon(Result.NOT_NETHER, null);
		}
		if (!BehemothSpawner.activeNear(level, from, cfg.lureActiveRadius).isEmpty()) {
			return new Summon(Result.ALREADY_ACTIVE, null);
		}
		Vec3 spot = BehemothSpawner.findSpot(level, from, cfg.lureMinDistance, cfg.lureMaxDistance, true);
		if (spot == null) {
			return new Summon(Result.NO_SPACE, null);
		}
		AbyssalBehemothEntity boss = BehemothSpawner.spawnAt(level, spot, MobSpawnType.MOB_SUMMONED);
		if (boss == null) {
			return new Summon(Result.NO_SPACE, null);
		}
		if (user != null) {
			boss.lure(user);
		}
		Vec3 mid = spot.add(0, AbyssalBehemothEntity.SIZE * 0.5, 0);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mid.x, mid.y, mid.z, 80, 4.0, 4.0, 4.0, 0.08);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, mid.x, mid.y, mid.z, 60, 4.5, 4.5, 4.5, 0.03);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5.0f, 0.5f);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 2.0f, 0.4f);
		com.projecthero.mod.ProjectHeroMod.LOGGER.info("[Behemoth] Lured in at {} ({} blocks from {})", spot,
				Math.round(Math.sqrt(spot.distanceToSqr(from.x, spot.y, from.z))), user == null ? "-" : user.getName().getString());
		return new Summon(Result.SUMMONED, boss);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server)) {
			return level.dimension() == Level.NETHER ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
		}
		Summon s = summon(server, player.position(), player);
		boolean ok = s.result() == Result.SUMMONED;
		player.displayClientMessage(Component.translatable(s.result().key).withStyle(ok ? ChatFormatting.DARK_RED : ChatFormatting.GOLD), true);
		if (!ok) {
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7f, 0.6f);
			return InteractionResultHolder.fail(stack);
		}
		server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.4, 0.6, 0.4, 0.04);
		stack.consume(1, player);
		player.getCooldowns().addCooldown(this, 40);
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.abyssal_lure.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.abyssal_lure.tooltip2").withStyle(ChatFormatting.DARK_RED));
	}
}
