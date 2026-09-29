package com.projecthero.mod.hero.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerGrants;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * v0.13.18: the random-power serums. Drink one and it grants a random power you do not already have:
 * <ul>
 *   <li>{@link Pool#ANY} -- the Prismatic Serum: any power in the mod, Experimental or Hero-Tier;</li>
 *   <li>{@link Pool#EXPERIMENTAL} -- the Mutagenic Serum: one of the Experimental (mutation) powers;</li>
 *   <li>{@link Pool#HERO} -- the Heroic Serum: one of the Hero-Tier powers (including the Symbiote).</li>
 * </ul>
 * The grant is additive and goes through {@link PowerGrants} -- the same routine as {@code /projecthero power
 * stack}: an Experimental power needs a free mutation slot, a Hero-Tier power claims a Primary slot (a player
 * already holding two Primaries loses the oldest), and each hero's prerequisites are handled for them. Candidates
 * that cannot be granted right now are skipped; if nothing in the pool can be, the serum is not used up.
 */
public class RandomPowerSerumItem extends Item {
	public enum Pool {
		ANY, EXPERIMENTAL, HERO
	}

	public static Item ANY_SERUM;
	public static Item EXPERIMENTAL_SERUM;
	public static Item HERO_SERUM;

	private final Pool pool;

	public RandomPowerSerumItem(Pool pool, Properties properties) {
		super(properties.stacksTo(16));
		this.pool = pool;
	}

	public static void initialize() {
		ANY_SERUM = register("prismatic_serum", new RandomPowerSerumItem(Pool.ANY, new Item.Properties().rarity(Rarity.EPIC)));
		EXPERIMENTAL_SERUM = register("mutagenic_serum", new RandomPowerSerumItem(Pool.EXPERIMENTAL, new Item.Properties().rarity(Rarity.RARE)));
		HERO_SERUM = register("heroic_serum", new RandomPowerSerumItem(Pool.HERO, new Item.Properties().rarity(Rarity.EPIC)));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(ANY_SERUM);
		output.accept(EXPERIMENTAL_SERUM);
		output.accept(HERO_SERUM);
	}

	private static Item register(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
	}

	public Pool pool() {
		return pool;
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.DRINK;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 32;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		return ItemUtils.startUsingInstantly(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
		if (!(level instanceof ServerLevel server) || !(user instanceof ServerPlayer player)) {
			return stack;
		}
		Component granted = grantRandom(player, pool, new Random(server.random.nextLong()));
		if (granted == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.random_serum.nothing")
					.withStyle(ChatFormatting.GRAY), true);
			return stack; // nothing could be granted -- keep the serum
		}
		player.sendSystemMessage(Component.translatable("message.projecthero.random_serum.granted", granted)
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		server.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
		server.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 0.8f);
		server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.6, 0.4, 0.3);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
			ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
			if (stack.isEmpty()) {
				return bottle;
			}
			if (!player.getInventory().add(bottle)) {
				player.drop(bottle, false);
			}
		}
		return stack;
	}

	/**
	 * Grant one random power from {@code pool} the player does not already have. Tries candidates in random order
	 * until one succeeds (a full mutation capacity makes Experimental ones fail).
	 *
	 * @return the granted power's display name, or null if nothing could be granted
	 */
	public static Component grantRandom(ServerPlayer player, Pool pool, Random random) {
		List<Object> candidates = new ArrayList<>();
		if (pool != Pool.HERO) {
			candidates.addAll(PowerGrants.missingExperimental(player));
		}
		if (pool != Pool.EXPERIMENTAL) {
			candidates.addAll(PowerGrants.missingHeroTiers(player));
		}
		Collections.shuffle(candidates, random);
		for (Object c : candidates) {
			if (c instanceof Power power) {
				if (PowerGrants.grantExperimental(player, power)) {
					return Component.translatable(power.nameKey());
				}
			} else if (c instanceof String hero && PowerGrants.grantHero(player, hero, false).ok()) {
				return Component.translatable("projecthero.hero_tier." + hero);
			}
		}
		return null;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return pool != Pool.EXPERIMENTAL;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		String key = switch (pool) {
			case ANY -> "item.projecthero.prismatic_serum.hint";
			case EXPERIMENTAL -> "item.projecthero.mutagenic_serum.hint";
			case HERO -> "item.projecthero.heroic_serum.hint";
		};
		tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.random_serum.rule").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
