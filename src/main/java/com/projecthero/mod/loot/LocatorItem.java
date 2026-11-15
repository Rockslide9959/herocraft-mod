package com.projecthero.mod.loot;

import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
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

/**
 * v0.17.5: the Locator -- a tracker that points at the nearest place of its tier (2 Site, 3 Hideout, 4 Dungeon). Right-click
 * searches {@code #projecthero:locator/tier<N>} ({@link LocatorSearch}) and binds to the find like a lodestone compass:
 * the needle points there, the tooltip and a small HUD line show the place's name and distance. Sneak + right-click on a
 * bound Locator searches again from where you stand. Nothing found: a message, and the Locator stays unbound.
 */
public class LocatorItem extends Item {
	/** Ticks between searches (the search is the vanilla /locate ring search). */
	public static final int SEARCH_COOLDOWN = 40;

	/** What {@link #locate} did. */
	public enum LocateResult { BOUND, NOT_FOUND, NONE_EXIST }

	public LocatorItem(Properties properties) {
		super(properties);
	}

	/** A fresh Locator for a tier (2-4), with that tier's rarity. */
	public static ItemStack forTier(int tier) {
		int t = LocatorTarget.clampTier(tier);
		ItemStack stack = new ItemStack(LootItems.LOCATOR);
		stack.set(LootItems.LOCATOR_TARGET, LocatorTarget.unbound(t));
		stack.set(HeroRarity.COMPONENT, rarityFor(t));
		return stack;
	}

	public static HeroRarity rarityFor(int tier) {
		return switch (tier) {
			case 3 -> HeroRarity.RARE;
			case 4 -> HeroRarity.EPIC;
			default -> HeroRarity.UNCOMMON;
		};
	}

	public static LocatorTarget target(ItemStack stack) {
		LocatorTarget t = stack.get(LootItems.LOCATOR_TARGET);
		return t == null ? LocatorTarget.unbound(LocatorTarget.MIN_TIER) : t;
	}

	/** Binds the stack to a find (also what a structure / command can call to hand out a pre-aimed Locator). */
	public static void bind(ItemStack stack, GlobalPos at, ResourceLocation structure) {
		stack.set(LootItems.LOCATOR_TARGET, target(stack).bound(at, structure));
	}

	/** {@code structure.<namespace>.<path>}, falling back to the path. */
	public static MutableComponent structureName(ResourceLocation id) {
		return Component.translatableWithFallback("structure." + id.getNamespace() + "." + id.getPath(), id.getPath());
	}

	/** {@code projecthero.locator.tier<N>} -- "Site", "Hideout", "Dungeon". */
	public static MutableComponent tierName(int tier) {
		return Component.translatable("projecthero.locator.tier" + LocatorTarget.clampTier(tier));
	}

	/** Horizontal distance in blocks from {@code from} to the bound place, or -1 when unbound / another dimension. */
	public static int distance(ItemStack stack, Level level, double x, double z) {
		Optional<GlobalPos> pos = target(stack).pos();
		if (pos.isEmpty() || !pos.get().dimension().equals(level.dimension())) {
			return -1;
		}
		BlockPos p = pos.get().pos();
		double dx = p.getX() + 0.5 - x;
		double dz = p.getZ() + 0.5 - z;
		return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.translatable("item.projecthero.locator.named", tierName(target(stack).tier()));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return target(stack).isBound() || super.isFoil(stack);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(stack);
		}
		LocatorTarget t = target(stack);
		if (t.isBound() && !sp.isShiftKeyDown()) {
			int d = distance(stack, level, sp.getX(), sp.getZ());
			Component name = structureName(t.structure().orElseThrow());
			sp.displayClientMessage(d >= 0
					? Component.translatable("item.projecthero.locator.status", name, d).withStyle(ChatFormatting.AQUA)
					: Component.translatable("item.projecthero.locator.other_dimension", name).withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.success(stack);
		}
		locate(sp, stack);
		sp.getCooldowns().addCooldown(this, SEARCH_COOLDOWN);
		return InteractionResultHolder.success(stack);
	}

	/** Searches from the player's position and binds the stack on a find. Messages the player either way. */
	public static LocateResult locate(ServerPlayer player, ItemStack stack) {
		ServerLevel level = player.serverLevel();
		LocatorTarget t = target(stack);
		if (LocatorSearch.members(level.registryAccess(), t.tier()).isEmpty()) {
			player.displayClientMessage(Component.translatable("item.projecthero.locator.none_exist", tierName(t.tier()))
					.withStyle(ChatFormatting.RED), true);
			return LocateResult.NONE_EXIST;
		}
		Optional<LocatorSearch.Found> found = LocatorSearch.finder.find(level, player.blockPosition(), t.tier());
		if (found.isEmpty()) {
			player.displayClientMessage(Component.translatable("item.projecthero.locator.not_found", tierName(t.tier()))
					.withStyle(ChatFormatting.RED), true);
			level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.4f);
			return LocateResult.NOT_FOUND;
		}
		LocatorSearch.Found f = found.get();
		bind(stack, GlobalPos.of(level.dimension(), f.pos()), f.structure());
		int d = distance(stack, level, player.getX(), player.getZ());
		player.displayClientMessage(Component.translatable("item.projecthero.locator.bound", structureName(f.structure()), d)
				.withStyle(ChatFormatting.AQUA), true);
		level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0f, 1.0f);
		return LocateResult.BOUND;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		LocatorTarget t = target(stack);
		if (t.isBound()) {
			tooltip.add(Component.translatable("item.projecthero.locator.target", structureName(t.structure().orElseThrow())
					.withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
			// the distance line is added on the client (it needs the reader's position): LootClient
			tooltip.add(Component.translatable("item.projecthero.locator.hint_bound").withStyle(ChatFormatting.DARK_GRAY));
		} else {
			tooltip.add(Component.translatable("item.projecthero.locator.hint", tierName(t.tier())).withStyle(ChatFormatting.GRAY));
		}
	}
}
