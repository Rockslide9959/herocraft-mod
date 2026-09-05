package com.herocraft.mod.ironman;

import java.util.Map;
import java.util.WeakHashMap;

import com.herocraft.mod.ironman.item.IronManArmorItem;
import com.herocraft.mod.ironman.item.MarkVSuitcaseItem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * "changes 20": Stark technology is not something you can just build at a workbench. A suit is
 * craftable only by a player who actually has the Tony Stark power.
 *
 * <p>The two <em>machine</em> routes to a suit were already gated -- the Stark Fabricator
 * ({@link com.herocraft.mod.ironman.fabricator.StarkFabricatorBlockEntity}) and the suit platform
 * ({@link com.herocraft.mod.ironman.fabricator.IronManSuitPlatformBlockEntity}) both refuse to open
 * or run for a player failing {@link TonyStark#hasPower}. The hole was the ordinary crafting grid:
 * the four Mark 1 pieces ship as plain {@code minecraft:crafting_shaped} recipes, so anyone with
 * iron and a basic circuit could build the starter suit and (thanks to
 * {@link IronManArmor#enforce} ejecting it again) end up with a set of armour they can never wear.
 * {@code CraftingMenuMixin} closes it by blanking the result slot; this class is the shared policy
 * both the mixin and any future crafting route consult.
 *
 * <p>Deliberately <em>not</em> gated: the Arc Reactor itself, the components, the blueprints and the
 * suit platform block. The Arc Reactor is what grants the power in the first place
 * ({@link com.herocraft.mod.ironman.item.ArcReactorItem}), so gating it would make the whole tree
 * unreachable; the rest are inert on their own and are the intended pre-power build-up.
 */
public final class IronManCrafting {
	/**
	 * How long a player is left alone after being told why the grid produced nothing, in ticks.
	 * The gate re-evaluates on every single grid change, so without a throttle simply nudging items
	 * around a crafting table would spam the action bar.
	 */
	private static final long WARN_COOLDOWN_TICKS = 60L;

	/**
	 * Last game-time each player was warned. Weak-keyed so a disconnecting player's entry can be
	 * collected, and only ever touched from the server thread (crafting menus are server-side), so
	 * no synchronisation is needed.
	 */
	private static final Map<ServerPlayer, Long> LAST_WARNED = new WeakHashMap<>();

	private IronManCrafting() {
	}

	/** True if {@code stack} is a finished Iron Man suit, i.e. something only Tony Stark may build. */
	public static boolean requiresTonyStark(ItemStack stack) {
		Item item = stack.getItem();
		return item instanceof IronManArmorItem || item instanceof MarkVSuitcaseItem;
	}

	/** True if {@code player} is allowed to craft {@code result}. */
	public static boolean canCraft(ServerPlayer player, ItemStack result) {
		return !requiresTonyStark(result) || TonyStark.hasPower(player);
	}

	/**
	 * Tells the player why the output slot is empty, at most once per {@link #WARN_COOLDOWN_TICKS}.
	 * Without this the block is indistinguishable from a mistyped recipe.
	 */
	public static void warnLocked(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = LAST_WARNED.get(player);
		if (last != null && now - last < WARN_COOLDOWN_TICKS) {
			return;
		}
		LAST_WARNED.put(player, now);
		player.displayClientMessage(Component.translatable("message.herocraft.ironman.craft_requires_tony_stark")
				.withStyle(ChatFormatting.RED), true);
	}
}
