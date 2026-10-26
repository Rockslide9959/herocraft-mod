package com.projecthero.mod.miraculous.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.joml.Vector3f;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;

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

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.6: <b>the Miraculous Box</b> -- the Guardian's hexagonal black-lacquer box (a GeckoLib item model). Right-click:
 * it opens with a chime and a burst of red and gold, is used up, and leaves ONE random jewel in your hands. With the
 * {@code projectheroUniquePowers} gamerule on it prefers a jewel whose Kwami nobody holds (and never one you already
 * hold, when another is free).
 */
public class MiraculousBoxItem extends Item implements GeoItem {
	/** Set by the client (its GeckoLib item renderer). */
	public static Consumer<Consumer<GeoRenderProvider>> rendererFactory;
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public MiraculousBoxItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(stack);
		}
		ItemStack jewel = open(sp);
		if (!sp.getAbilities().instabuild) {
			stack.shrink(1);
		}
		if (stack.isEmpty()) {
			return InteractionResultHolder.consume(jewel);
		}
		if (!sp.getInventory().add(jewel)) {
			sp.drop(jewel, false);
		}
		return InteractionResultHolder.consume(stack);
	}

	/** The jewel this player gets from a box (with the sound and the sparkle). Public for tests. */
	public static ItemStack open(ServerPlayer player) {
		Kwami k = pick(player);
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 0.6f, 1.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 1.2f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.4f);
		double x = player.getX() + player.getLookAngle().x * 0.6;
		double y = player.getEyeY() - 0.35;
		double z = player.getZ() + player.getLookAngle().z * 0.6;
		level.sendParticles(new DustParticleOptions(new Vector3f(0.88f, 0.12f, 0.14f), 1.0f), x, y, z, 24, 0.25, 0.2, 0.25, 0.02);
		level.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.25f), 0.8f), x, y, z, 18, 0.3, 0.25, 0.3, 0.02);
		level.sendParticles(ParticleTypes.END_ROD, x, y, z, 8, 0.2, 0.2, 0.2, 0.03);
		player.displayClientMessage(Component.translatable("message.projecthero.miraculous.box_opened",
				Component.translatable(MiraculousItems.jewelOf(k).getDescriptionId())).withStyle(ChatFormatting.GOLD), true);
		return new ItemStack(MiraculousItems.jewelOf(k));
	}

	/**
	 * What the box can hold (v0.16.6: never the Butterfly Brooch -- Hawk Moth's jewel is found on its own; the Turtle
	 * Bracelet and the Horseshoe Glasses joined the pool with Carapace and Pegasus).
	 */
	public static final List<Kwami> BOX_KWAMIS = List.of(Kwami.TIKKI, Kwami.PLAGG, Kwami.TRIXX, Kwami.WAYZZ, Kwami.KAALKI, Kwami.SASS, Kwami.MULLO);

	private static Kwami pick(ServerPlayer player) {
		List<Kwami> free = new ArrayList<>();
		List<Kwami> freeNotMine = new ArrayList<>();
		for (Kwami k : BOX_KWAMIS) {
			if (com.projecthero.mod.hero.UniquePowers.blockingHolder(player, k.heroKey) == null) {
				free.add(k);
				if (!Miraculous.holds(player, k)) {
					freeNotMine.add(k);
				}
			}
		}
		List<Kwami> from = !freeNotMine.isEmpty() ? freeNotMine : !free.isEmpty() ? free : BOX_KWAMIS;
		return from.get(player.getRandom().nextInt(from.size()));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.miraculous_box.tooltip").withStyle(ChatFormatting.RED));
		tooltip.add(Component.translatable("item.projecthero.miraculous_box.tooltip2").withStyle(ChatFormatting.GRAY));
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
		if (rendererFactory != null) {
			rendererFactory.accept(consumer);
		}
	}
}
