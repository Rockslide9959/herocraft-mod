package com.projecthero.mod.ironman.suit;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.entity.IronManSuitPartEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The reusable {@code IronManSuitSummonSystem} (spec section 21). One entry point,
 * {@link #summon(ServerPlayer, String)}, used by every mark -- suit recall is <em>not</em>
 * reimplemented per suit. Behaviour varies by the suit's {@link SummonType}:
 *
 * <ul>
 *   <li>{@code FLYING_SET} / {@code FLYING_MODULAR}: each missing armour piece is pulled from the
 *       player's inventory (or a bound Suit Platform) and launched as an
 *       {@link IronManSuitPartEntity} that flies to the player and locks on -- the Mark III / Mark 42
 *       "pieces physically fly toward me" cinematic. Modular also allows partial summons.</li>
 *   <li>{@code TRACKING_POD}: same courier entities for now, faster and from further out (Mark VII).</li>
 *   <li>{@code NANOTECH_ONBOARD}: no entities -- the suit forms out of the chest housing directly via
 *       {@link IronManSuitUpManager#beginSuitUp} with nanite FX (Mark 50).</li>
 *   <li>{@code SUITCASE_ITEM}: handled by the Mark V Suitcase item, not here.</li>
 * </ul>
 */
public final class IronManSuitSummonManager {
	private IronManSuitSummonManager() {
	}

	/**
	 * Call the player's armour to them: their last active suit if they have developed it, otherwise
	 * the most advanced suit they have built. Used by the {@code C} slot when no suit is worn and by
	 * the double-tap-jump gesture (spec "changes 8").
	 */
	public static boolean summonBest(ServerPlayer player) {
		if (!TonyStark.hasPower(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.armor_rejects")
					.withStyle(net.minecraft.ChatFormatting.RED), true);
			return false;
		}
		String active = TonyStark.activeSuitId(player);
		String best = null;
		int bestTech = -1;
		for (IronManSuit suit : IronManSuits.all()) {
			// a suit is a candidate if the player has developed it OR physically has any of its pieces
			if ((TonyStark.hasBuilt(player, suit.id()) || hasAnyPiece(player, suit.id()))
					&& suit.techLevel() > bestTech) {
				bestTech = suit.techLevel();
				best = suit.id();
			}
		}
		if (active != null && !active.isEmpty()
				&& (TonyStark.hasBuilt(player, active) || hasAnyPiece(player, active))) {
			best = active;
		}
		if (best == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.no_suit_built"), true);
			return false;
		}
		return summon(player, best);
	}

	public static boolean summon(ServerPlayer player, String suitId) {
		if (!TonyStark.hasPower(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.armor_rejects")
					.withStyle(net.minecraft.ChatFormatting.RED), true);
			return false;
		}
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit == null) {
			return false;
		}
		if (!TonyStark.hasBuilt(player, suitId) && TonyStark.techLevel(player) < suit.techLevel()
				&& !hasAnyPiece(player, suitId)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.not_developed",
					Component.translatable(suit.nameKey())), true);
			return false;
		}

		return switch (suit.summonType()) {
			case NANOTECH_ONBOARD, SUITCASE_ITEM -> IronManSuitUpManager.beginSuitUp(player, suitId);
			case FLYING_SET, FLYING_MODULAR, TRACKING_POD -> flyingSummon(player, suit);
		};
	}

	/**
	 * Summon a <em>single</em> armour piece to the player -- the partial / modular calls (spec sections
	 * 25, 32). Sources it from the inventory or a nearby Suit Platform and flies it in.
	 */
	public static boolean summonPart(ServerPlayer player, String suitId, ArmorItem.Type type) {
		if (!TonyStark.hasPower(player) || IronManSuits.byId(suitId) == null) {
			return false;
		}
		if (IronManArmor.isPieceWorn(player, IronManSuitUpManager.slotFor(type), suitId)) {
			return false;
		}
		if (launchPiece(player, suitId, type, 0)) {
			ServerLevel level = (ServerLevel) player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.NETHERITE_BLOCK_FALL, SoundSource.PLAYERS, 0.8f, 1.1f);
			return true;
		}
		return false;
	}

	/**
	 * Fly-in order ("changes 10"): chestplate, then boots, then leggings, then helmet -- and each
	 * courier is delayed by {@link #LAUNCH_STAGGER} so the pieces arrive and equip one at a time.
	 */
	private static final ArmorItem.Type[] FLY_IN_ORDER = {
			ArmorItem.Type.CHESTPLATE, ArmorItem.Type.BOOTS, ArmorItem.Type.LEGGINGS, ArmorItem.Type.HELMET };
	private static final int LAUNCH_STAGGER = 16;

	private static boolean flyingSummon(ServerPlayer player, IronManSuit suit) {
		ServerLevel level = (ServerLevel) player.level();
		String suitId = suit.id();
		int launched = 0;
		for (ArmorItem.Type type : FLY_IN_ORDER) {
			if (IronManArmor.isPieceWorn(player, IronManSuitUpManager.slotFor(type), suitId)) {
				continue;
			}
			if (launchPiece(player, suitId, type, launched * LAUNCH_STAGGER)) {
				launched++;
			}
		}
		if (launched == 0) {
			// nothing to fly -- fall back to a direct suit-up (pieces may already be worn / stored elsewhere)
			return IronManSuitUpManager.beginSuitUp(player, suitId);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.NETHERITE_BLOCK_FALL, SoundSource.PLAYERS, 1.0f, 0.8f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.suit_incoming",
				Component.translatable(suit.nameKey())).withStyle(net.minecraft.ChatFormatting.AQUA), true);

		// The couriers equip each piece on arrival (receivePart); the transition state here just drives
		// the HUD + swirl particles, so its mask is empty (nothing for the staged timer to equip).
		var s = TonyStark.state(player);
		s.transitionSuit = suitId;
		s.transitionUp = true;
		s.transitionTotal = Math.max(20, suit.suitUpType().durationTicks()
				+ Math.max(0, launched - 1) * LAUNCH_STAGGER + 40);
		s.transitionTicks = s.transitionTotal;
		s.transitionMask = 0;
		return true;
	}

	/**
	 * Launch one armour piece toward the player as an {@link IronManSuitPartEntity}, taking it from
	 * (in priority order) the player's inventory, then the nearest loaded Iron Man Suit Platform
	 * within {@value #PLATFORM_SEARCH_CHUNKS} chunks that holds it. Returns false if the piece is
	 * nowhere to be found.
	 */
	private static boolean launchPiece(ServerPlayer player, String suitId, ArmorItem.Type type, int launchDelay) {
		ServerLevel level = (ServerLevel) player.level();

		int idx = findInInventory(player, suitId, type);
		if (idx >= 0) {
			player.getInventory().removeItem(idx, 1);
			double ang = level.random.nextDouble() * Math.PI * 2;
			Vec3 from = player.position().add(Math.cos(ang) * 6, 3 + level.random.nextDouble() * 2, Math.sin(ang) * 6);
			IronManSuitPartEntity.spawn(level, from, player, suitId, type, launchDelay);
			return true;
		}

		var platform = nearestPlatformWith(level, player.blockPosition(), suitId, type);
		if (platform != null && platform.getKey().takePiece(suitId, type)) {
			net.minecraft.core.BlockPos pos = platform.getValue();
			Vec3 from = Vec3.atCenterOf(pos).add(0, 1.0, 0);
			IronManSuitPartEntity.spawn(level, from, player, suitId, type, launchDelay);
			return true;
		}
		return false;
	}

	private static final int PLATFORM_SEARCH_CHUNKS = 4;

	private static java.util.Map.Entry<com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity,
			net.minecraft.core.BlockPos> nearestPlatformWith(ServerLevel level, net.minecraft.core.BlockPos center,
			String suitId, ArmorItem.Type type) {
		int cx = center.getX() >> 4;
		int cz = center.getZ() >> 4;
		com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity best = null;
		net.minecraft.core.BlockPos bestPos = null;
		double bestSq = Double.MAX_VALUE;
		for (int dx = -PLATFORM_SEARCH_CHUNKS; dx <= PLATFORM_SEARCH_CHUNKS; dx++) {
			for (int dz = -PLATFORM_SEARCH_CHUNKS; dz <= PLATFORM_SEARCH_CHUNKS; dz++) {
				var chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
				if (chunk == null) {
					continue;
				}
				for (var e : chunk.getBlockEntities().entrySet()) {
					if (e.getValue() instanceof com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity p
							&& (type == null ? suitId.equals(p.storedSuitId()) : p.holds(suitId, type))) {
						double d = e.getKey().distSqr(center);
						if (d < bestSq) {
							bestSq = d;
							best = p;
							bestPos = e.getKey().immutable();
						}
					}
				}
			}
		}
		return best == null ? null : java.util.Map.entry(best, bestPos);
	}

	/** Does the player hold any piece of this suit -- worn, in the inventory, or on a nearby platform? */
	private static boolean hasAnyPiece(ServerPlayer player, String suitId) {
		if (IronManArmor.wornSuitId(player) != null && IronManArmor.wornSuitId(player).equals(suitId)) {
			return true;
		}
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (inv.getItem(i).getItem() instanceof IronManArmorItem p && p.suitId().equals(suitId)) {
				return true;
			}
		}
		ServerLevel level = (ServerLevel) player.level();
		return nearestPlatformWith(level, player.blockPosition(), suitId, null) != null;
	}

	private static int findInInventory(ServerPlayer player, String suitId, ArmorItem.Type type) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.getItem() instanceof IronManArmorItem piece
					&& piece.suitId().equals(suitId) && piece.getType() == type) {
				return i;
			}
		}
		return -1;
	}
}
