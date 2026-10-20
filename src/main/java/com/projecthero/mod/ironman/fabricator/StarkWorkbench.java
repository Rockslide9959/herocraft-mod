package com.projecthero.mod.ironman.fabricator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.era1.IronManEraOne;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.item.BlueprintItem;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.7 (user): the Stark Workbench's rules shared by the screen (client), the block entity and the menu (server):
 * which suits each era tab lists (read from the progression {@link IronManItems#MARK_ORDER} -- never hardcoded), the
 * owned / buildable / locked state of a suit and its four pieces (from the synced {@link TonyStarkState}, so the client
 * screen and the server agree), the normalised stat bars, and the Build button: load the selected piece's blueprint and
 * components from the player's inventory into the machine, after which the unchanged Fabricator logic
 * ({@link FabricatorRecipes}, machine energy, the Tony-Stark-nearby rule) fabricates it.
 */
public final class StarkWorkbench {
	/** The four era tabs; only Era I is live in v0.16.7. */
	public static final int ERA_COUNT = 4;
	public static final int LIVE_ERAS = 1;

	/** Menu button ids: {@code BUILD_BASE + suitIndex * 10 + piece} = Build that piece (1..4) of {@code MARK_ORDER[suitIndex]}. */
	public static final int BUILD_BASE = 1000;

	private static final String[] PIECES = { "helmet", "chestplate", "leggings", "boots" };
	private static final ArmorItem.Type[] TYPES = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
			ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	public enum Status { OWNED, BUILDABLE, LOCKED }

	private StarkWorkbench() {
	}

	public static int buildButtonId(int suitIndex, int piece) {
		return BUILD_BASE + suitIndex * 10 + piece;
	}

	/** The suits an era tab lists, in progression order (era 1 = the Era I suits of {@link IronManItems#MARK_ORDER}). */
	public static List<String> eraSuits(int era) {
		List<String> out = new ArrayList<>();
		if (era == 1) {
			for (String id : IronManItems.MARK_ORDER) {
				if (IronManEraOne.isEraOne(id) && IronManSuits.byId(id) != null) {
					out.add(id);
				}
			}
		}
		return out;
	}

	public static ArmorItem.Type pieceType(int piece) {
		return piece >= 1 && piece <= 4 ? TYPES[piece - 1] : null;
	}

	public static TonyStarkState state(Player player) {
		return player == null ? null : player.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
	}

	/** Has this player built (fabricated / crafted) piece {@code piece} (1..4) of {@code suitId}? */
	public static boolean pieceBuilt(TonyStarkState s, String suitId, int piece) {
		if (s == null || suitId == null || piece < 1 || piece > 4) {
			return false;
		}
		return s.builtSuits.contains(suitId) || s.builtSuits.contains(suitId + "/" + PIECES[piece - 1]);
	}

	/** The whole suit built (same rule as {@link TonyStark#hasFabricatedFullSuit}). */
	public static boolean owned(TonyStarkState s, String suitId) {
		if (s == null || suitId == null) {
			return false;
		}
		if (s.builtSuits.contains(suitId)) {
			return true;
		}
		for (int p = 1; p <= 4; p++) {
			if (!pieceBuilt(s, suitId, p)) {
				return false;
			}
		}
		return true;
	}

	/** Unlocked = its blueprint can be had: the previous suit in the progression is fully built (as the Blank Blueprint). */
	public static boolean unlocked(TonyStarkState s, String suitId) {
		String prereq = IronManItems.prerequisiteSuit(suitId);
		return prereq == null || owned(s, prereq);
	}

	public static Status status(TonyStarkState s, String suitId) {
		return owned(s, suitId) ? Status.OWNED : unlocked(s, suitId) ? Status.BUILDABLE : Status.LOCKED;
	}

	/** The first piece (1..4) not yet built, or 1 if all are. */
	public static int firstUnbuiltPiece(TonyStarkState s, String suitId) {
		for (int p = 1; p <= 4; p++) {
			if (!pieceBuilt(s, suitId, p)) {
				return p;
			}
		}
		return 1;
	}

	/** The Workbench recipe for one piece, or null (the Mk I is built at a crafting table). */
	public static FabricationRecipe recipe(String suitId, int piece) {
		ArmorItem.Type type = pieceType(piece);
		return suitId == null || type == null ? null : FabricatorRecipes.byId("iron_man_" + suitId + "_" + type.getName());
	}

	/** How many of {@code item} the player's main inventory (not the worn armour) holds. */
	public static int countInInventory(Inventory inv, Item item) {
		int n = 0;
		for (ItemStack s : inv.items) {
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	// ------------------------------------------------------------------ stat bars

	private static final Map<String, float[]> STATS = new HashMap<>();
	private static float[] statMax;

	/** Raw stat values: armour points (sum of the four pieces), max integrity, reactor capacity, flight speed (b/s). */
	public static float[] rawStats(IronManSuit suit) {
		float armour = 0f;
		for (ArmorItem.Type t : TYPES) {
			IronManArmorItem a = suit.armor(t);
			if (a != null) {
				armour += a.getDefense();
			}
		}
		float flight = suit.manualFlight() ? (float) Math.max(suit.flightNormalBps(), suit.flightSprintBps()) : 0f;
		if (suit.manualFlight() && flight <= 0f) {
			flight = 8f;
		}
		return new float[] { armour, suit.maxIntegrity(), suit.energyCapacity(), flight };
	}

	/**
	 * The four bars (Armour, Integrity, Reactor energy, Flight / mobility) in 0..1, normalised by the best suit of all.
	 * A suit with no sustained flight shows a short "jumps / hover" stub rather than an empty bar.
	 */
	public static synchronized float[] statBars(IronManSuit suit) {
		if (suit == null) {
			return new float[4];
		}
		if (statMax == null) {
			statMax = new float[4];
			for (IronManSuit s : IronManSuits.all()) {
				float[] r = rawStats(s);
				for (int i = 0; i < 4; i++) {
					statMax[i] = Math.max(statMax[i], r[i]);
				}
			}
		}
		return STATS.computeIfAbsent(suit.id(), id -> {
			float[] r = rawStats(suit);
			float[] out = new float[4];
			for (int i = 0; i < 4; i++) {
				out[i] = statMax[i] <= 0f ? 0f : Math.min(1f, r[i] / statMax[i]);
			}
			if (out[3] <= 0f) {
				out[3] = 0.12f;
			}
			return out;
		});
	}

	// ------------------------------------------------------------------ the Build button (server)

	/**
	 * Build piece {@code piece} of {@code suitId}: put that suit's blueprint into the blueprint slot (from the player's
	 * inventory; a Blank Blueprint is stamped first if they have one and the suit is unlocked -- the same unlock rule as
	 * the Blank Blueprint picker), select the piece, and move every missing component from the player's inventory into the
	 * input grid. The machine then fabricates it exactly as before. Reports what happened on the action bar.
	 */
	public static void build(ServerPlayer player, StarkFabricatorBlockEntity be, String suitId, int piece) {
		if (!TonyStark.hasPower(player) || IronManSuits.byId(suitId) == null || piece < 1 || piece > 4) {
			return;
		}
		TonyStarkState s = state(player);
		if (!unlocked(s, suitId)) {
			IronManSuit pre = IronManSuits.byId(IronManItems.prerequisiteSuit(suitId));
			say(player, Component.translatable("message.projecthero.stark_workbench.locked",
					pre != null ? Component.translatable(pre.nameKey()) : Component.literal("?")), ChatFormatting.RED);
			return;
		}
		FabricationRecipe recipe = recipe(suitId, piece);
		if (recipe == null) {
			say(player, Component.translatable("message.projecthero.stark_workbench.table_built"), ChatFormatting.GOLD);
			return;
		}
		if (!loadBlueprint(player, be, suitId)) {
			say(player, Component.translatable("message.projecthero.stark_workbench.no_blueprint"), ChatFormatting.RED);
			return;
		}
		be.setSelectedPiece(piece);
		int missing = 0;
		for (FabricationRecipe.Input in : recipe.inputs()) {
			int have = haveInGrid(be, in.item());
			int need = in.count() - have;
			if (need > 0) {
				need -= moveIntoGrid(player.getInventory(), be, in.item(), need);
			}
			missing += Math.max(0, need);
		}
		be.setChanged();
		player.containerMenu.broadcastChanges();
		if (missing > 0) {
			say(player, Component.translatable("message.projecthero.stark_workbench.missing", missing), ChatFormatting.RED);
		} else {
			player.level().playSound(null, be.getBlockPos(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.6f);
			say(player, Component.translatable("message.projecthero.stark_workbench.building"), ChatFormatting.AQUA);
		}
	}

	private static void say(ServerPlayer player, Component c, ChatFormatting colour) {
		player.displayClientMessage(c.copy().withStyle(colour), true);
	}

	private static int haveInGrid(StarkFabricatorBlockEntity be, Item item) {
		int n = 0;
		for (int i = 0; i < 9; i++) {
			ItemStack s = be.getItem(i);
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** The suit's blueprint into the slot (swapping any other blueprint back into the player's inventory). */
	private static boolean loadBlueprint(ServerPlayer player, StarkFabricatorBlockEntity be, String suitId) {
		Item wanted = IronManItems.blueprintFor(suitId);
		if (wanted == null) {
			return false;
		}
		ItemStack current = be.getItem(StarkFabricatorBlockEntity.BLUEPRINT_SLOT);
		if (current.is(wanted)) {
			return true;
		}
		Inventory inv = player.getInventory();
		int slot = find(inv, wanted);
		if (slot < 0 && find(inv, IronManItems.BLANK_BLUEPRINT) >= 0) {
			TonyStark.stampBlueprint(player, suitId); // re-validates the unlock, consumes one Blank Blueprint
			slot = find(inv, wanted);
		}
		if (slot < 0) {
			return false;
		}
		ItemStack bp = inv.items.get(slot).split(1);
		if (!current.isEmpty()) {
			ItemStack old = current.copy();
			be.setItem(StarkFabricatorBlockEntity.BLUEPRINT_SLOT, ItemStack.EMPTY);
			if (!inv.add(old)) {
				player.drop(old, false);
			}
		}
		be.setItem(StarkFabricatorBlockEntity.BLUEPRINT_SLOT, bp);
		return be.getItem(StarkFabricatorBlockEntity.BLUEPRINT_SLOT).getItem() instanceof BlueprintItem;
	}

	private static int find(Inventory inv, Item item) {
		for (int i = 0; i < inv.items.size(); i++) {
			if (inv.items.get(i).is(item)) {
				return i;
			}
		}
		return -1;
	}

	/** Moves up to {@code count} of {@code item} from the inventory into the input grid. Returns how many moved. */
	private static int moveIntoGrid(Inventory inv, StarkFabricatorBlockEntity be, Item item, int count) {
		int moved = 0;
		for (int i = 0; i < inv.items.size() && moved < count; i++) {
			ItemStack src = inv.items.get(i);
			if (!src.is(item)) {
				continue;
			}
			// first top up stacks of the same item already in the grid, then use empty cells
			for (int pass = 0; pass < 2 && moved < count && !src.isEmpty(); pass++) {
				for (int g = 0; g < 9 && moved < count && !src.isEmpty(); g++) {
					ItemStack dst = be.getItem(g);
					if (pass == 0 && ItemStack.isSameItemSameComponents(dst, src) && dst.getCount() < dst.getMaxStackSize()) {
						int n = Math.min(Math.min(count - moved, src.getCount()), dst.getMaxStackSize() - dst.getCount());
						dst.grow(n);
						src.shrink(n);
						moved += n;
					} else if (pass == 1 && dst.isEmpty()) {
						int n = Math.min(Math.min(count - moved, src.getCount()), src.getMaxStackSize());
						be.setItem(g, src.split(n));
						moved += n;
					}
				}
			}
		}
		return moved;
	}
}
