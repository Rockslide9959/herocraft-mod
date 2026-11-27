package com.projecthero.mod.worthy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hammer.MjolnirRecall;
import com.projecthero.mod.hammer.MjolnirRegistry;
import com.projecthero.mod.hammer.ThorWeapon;
import com.projecthero.mod.item.ModDataComponents;
import com.projecthero.mod.power.ThorThrown;
import com.projecthero.mod.squad.SquadManager;
import com.projecthero.mod.squad.Squads;
import com.projecthero.mod.supersoldier.SuperSoldier;
import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Thor x Super Soldier (user: "Thor squad mate can shift+Right click a super soldier squad mate to assign either Mjolnir or
 * stormbreaker to that player, in the weapon menu thor player can retrieve the weapons back to themselves whenever they want"):
 * lending a Thor weapon, calling it back, and every way a loan ends.
 *
 * <h2>Lending</h2>
 * A worthy player holding Mjolnir or Stormbreaker (main hand first, else the off hand) sneak + right-clicks another player. Only a
 * Super Soldier who is their squadmate ({@link Squads#areAllies}) accepts it; anyone else gets a refusal line and nothing moves. The
 * weapon must be bound to the lender (it stays theirs: the stack keeps its {@code BOUND_OWNER}, and {@link WorthyLoans} records the
 * loan). The stack MOVES along a spark line into the Super Soldier's PACK (v0.17.7: never his hand -- a free slot that is not the
 * selected one; with a full pack it lies at his feet as the usual callable weapon entity), with a visual bolt and a chat line for both.
 * One weapon or both, to the same or to different Super Soldiers.
 *
 * <h2>His R: Call Mjolnir / Call Stormbreaker (v0.17.7)</h2>
 * User: "When player is lent the powers their regular r ability should be replaced with a weapon call ability and once they get the
 * hammer or axe they have that moveset". While a weapon lent to him is not in his hands (no lent-weapon kit), plain R in his normal
 * Super Soldier kit is the call ({@link #callTarget}, {@link #callLent}; Shift+R stays his own move): the weapon comes to his MAIN hand
 * from wherever it is through Thor's own recall ({@code MjolnirRecall.summonLent}) -- from his pack at once, else flying in from the
 * ground, a chest in a loaded chunk, another player (its Thor included) or rebuilt -- and lands by the catch-hand rules
 * ({@code ThorWeaponHands}, which send a lent weapon called by its Super Soldier to his main hand). With both lent and neither in hand,
 * R alternates. Once it is in his hand the weapon's kit owns the keys again.
 *
 * <h2>Calling it back</h2>
 * The Thor weapon screen (N) shows "Lent to ..." and a Call back button per lent weapon ({@link #callBack}). The loan ends and the
 * weapon goes home through Thor's own recall ({@link MjolnirRecall#callBack}): torn from the Super Soldier's hand / pack with the
 * "it visibly leaves them" flight, from a loaded entity or container, or -- the Super Soldier offline or out of reach -- rebuilt at a
 * new generation, which retires the copy in his saved inventory the moment it next ticks (the registry's own no-dupe rule).
 *
 * <h2>Other ends</h2>
 * Checked every {@link WorthyConfig#VALIDATE_EVERY_TICKS} for every online player in a loan: the two are no longer squadmates (left,
 * kicked, disbanded), the borrower lost the Super Soldier power, or the weapon got bound to someone else; and at once on the
 * borrower's death (before anything drops). With the owner online the weapon flies home; with the owner offline it is taken out of the
 * Super Soldier's inventory and dropped at his feet, where it lies as the usual resting, still-bound, callable weapon entity.
 * A weapon out on one of his throws ({@link ThorThrown}) is handed back to him first. No static state.
 */
public final class WorthyLending {
	public enum Reason {
		RECALL("recall"), SQUAD("squad"), POWER("power"), DEATH("death"), UNBOUND("unbound");

		final String key;

		Reason(String key) {
			this.key = key;
		}
	}

	private WorthyLending() {
	}

	// ================================================================== queries (both sides: the view list is synced)

	/** Whether {@code stack} is a Thor weapon lent to {@code player}. */
	public static boolean isLent(Player player, ItemStack stack) {
		return ThorWeapon.of(stack) != null && WorthyState.of(player).borrows(stack.get(ModDataComponents.HAMMER_ID));
	}

	/** Whether {@code player} may pick {@code stack} up off the ground: a Super Soldier it is lent to. */
	public static boolean mayLift(Player player, ItemStack stack) {
		return SuperSoldier.hasPower(player) && isLent(player, stack);
	}

	/** The hand holding a weapon lent to {@code player} (main hand first; a weapon out on his throw counts for the hand it left). */
	@Nullable
	public static InteractionHand lentHand(Player player) {
		if (!SuperSoldier.hasPower(player)) {
			return null;
		}
		for (InteractionHand hand : InteractionHand.values()) {
			if (isLent(player, ThorThrown.inHand(player, hand))) {
				return hand;
			}
		}
		return null;
	}

	/** The lent weapon {@code player} wields right now, or null. */
	@Nullable
	public static ThorWeapon lentWeapon(Player player) {
		InteractionHand hand = lentHand(player);
		return hand == null ? null : ThorWeapon.of(ThorThrown.inHand(player, hand));
	}

	/** Whether a weapon of kind {@code weapon} is lent to {@code player} and he is a Super Soldier (both sides: the view is synced). */
	public static boolean borrowsKind(Player player, @Nullable ThorWeapon weapon) {
		if (weapon == null || !SuperSoldier.hasPower(player)) {
			return false;
		}
		for (WorthyState.Borrowed b : WorthyState.of(player).borrowed) {
			if (b.weapon() == weapon.ordinal()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * What his plain R calls right now (both sides; the HUD names it): a weapon lent to this Super Soldier while none is in his hands
	 * (no lent-weapon kit). One lent: that one. Two: the one R did not call last (they alternate). Null = R is his own move.
	 */
	@Nullable
	public static WorthyState.Borrowed callTarget(Player player) {
		if (!SuperSoldier.hasPower(player)) {
			return null;
		}
		WorthyState s = WorthyState.of(player);
		if (s.borrowed.isEmpty() || WorthyKit.kit(player) != WorthyKit.Kit.NONE) {
			return null;
		}
		for (WorthyState.Borrowed b : s.borrowed) {
			if (s.lastCall == null || !b.hammerId().equals(s.lastCall)) {
				return b;
			}
		}
		return s.borrowed.get(0);
	}

	/** The ThorWeapon of a view entry. */
	public static ThorWeapon weaponOf(WorthyState.Borrowed b) {
		return ThorWeapon.values()[Math.max(0, Math.min(ThorWeapon.values().length - 1, b.weapon()))];
	}

	/**
	 * His R as the call (server; {@code WorthyKit.handle}): brings {@link #callTarget} to his main hand by Thor's recall. Public for
	 * the tests.
	 *
	 * @return true if a weapon is on its way or now in his hand (false: nothing to call, or the call is cooling down)
	 */
	public static boolean callLent(ServerPlayer p) {
		WorthyState.Borrowed b = callTarget(p);
		if (b == null) {
			return false;
		}
		WorthyLoans.Loan loan = WorthyLoans.get(p.server).byHammer(b.hammerId());
		if (loan == null || !loan.borrower().equals(p.getUUID())) {
			refreshView(p.server, p.getUUID()); // a stale view: the loan is gone
			return false;
		}
		if (!WorthyMoves.begin(p, WorthyKit.CALL_WEAPON, WorthyConfig.CALL_WEAPON_CD, WorthyConfig.CALL_WEAPON_LOCK)) {
			return false;
		}
		UUID id = b.hammerId();
		WorthyState.edit(p, n -> n.lastCall = id);
		boolean answered = MjolnirRecall.summonLent(p, loan.weapon(), id);
		if (!answered) {
			WorthyMoves.refund(p, WorthyKit.CALL_WEAPON);
			return false;
		}
		WorthyFx.crackle(p.serverLevel(), p.position().add(0, 1.4, 0), 10, 0.35, loan.weapon() == ThorWeapon.STORMBREAKER);
		return true;
	}

	/** Server: whether {@code owner} has {@code weapon} out on a loan (the call key leaves it alone then). */
	public static boolean lentOut(ServerPlayer owner, ThorWeapon weapon) {
		return WorthyLoans.get(owner.server).lentBy(owner.getUUID(), weapon) != null;
	}

	// ================================================================== lending

	/** {@code UseEntityCallback} (server): a sneaking worthy player with a Thor weapon in hand right-clicking a player. */
	static InteractionResult onUseEntity(Player player, Level world, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (world.isClientSide || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer thor)
				|| !(entity instanceof ServerPlayer target) || !thor.isShiftKeyDown() || weaponHand(thor) == null
				|| !Worthiness.isWorthy(thor)) {
			return InteractionResult.PASS;
		}
		lend(thor, target);
		// SUCCESS either way: a sneak-click on a player with the weapon is never the Mjolnir bind toggle
		return InteractionResult.SUCCESS;
	}

	/** Which of {@code thor}'s hands holds a Thor weapon to lend (main first), or null. */
	@Nullable
	static InteractionHand weaponHand(Player thor) {
		if (ThorWeapon.of(thor.getMainHandItem()) != null) {
			return InteractionHand.MAIN_HAND;
		}
		if (ThorWeapon.of(thor.getOffhandItem()) != null) {
			return InteractionHand.OFF_HAND;
		}
		return null;
	}

	/**
	 * Lends the Thor weapon in {@code thor}'s hand to {@code target}, or refuses (with a line to {@code thor}). Public for the tests.
	 *
	 * @return true if the weapon moved
	 */
	public static boolean lend(ServerPlayer thor, ServerPlayer target) {
		InteractionHand hand = weaponHand(thor);
		if (hand == null || !Worthiness.isWorthy(thor)) {
			return false;
		}
		ItemStack stack = thor.getItemInHand(hand);
		ThorWeapon weapon = ThorWeapon.of(stack);
		Component weaponName = Component.translatable(weapon.item().getDescriptionId());
		UUID id = stack.get(ModDataComponents.HAMMER_ID);
		UUID boundTo = stack.get(ModDataComponents.BOUND_OWNER);
		if (id == null || boundTo == null || !boundTo.equals(thor.getUUID())) {
			refuse(thor, "message.projecthero.worthy.not_bound", weaponName);
			return false;
		}
		if (!SuperSoldier.hasPower(target) || !target.isAlive() || target.isSpectator()) {
			refuse(thor, "message.projecthero.worthy.not_soldier", target.getDisplayName());
			return false;
		}
		if (!Squads.areAllies(thor, target)) {
			refuse(thor, "message.projecthero.worthy.not_squad", target.getDisplayName());
			return false;
		}
		WorthyLoans loans = WorthyLoans.get(thor.server);
		if (loans.byHammer(id) != null) {
			loans.remove(id); // a stale record of this same weapon (it is in the lender's hand right now): replaced
		}

		// the loan first (so the stack counts as lent to him the moment it lands), then the move: out of Thor's hand, along the spark
		// line into the Super Soldier's PACK -- never his hand (v0.17.7 user: "their regular r ability should be replaced with a weapon
		// call ability and once they get the hammer or axe they have that moveset"); with a full pack it lies at his feet, callable
		loans.put(new WorthyLoans.Loan(id, weapon, thor.getUUID(), thor.getGameProfile().getName(), target.getUUID(),
				target.getGameProfile().getName(), thor.level().getGameTime()));
		refreshView(thor.server, thor.getUUID());
		refreshView(thor.server, target.getUUID());
		thor.setItemInHand(hand, ItemStack.EMPTY);
		if (stowInPack(target, stack)) {
			MjolnirRegistry.get(target.serverLevel()).noteCarried(stack, target, false);
		} else {
			target.drop(stack, false); // a dropped Thor weapon lies as its own resting, still-bound, callable entity
		}
		thor.containerMenu.broadcastChanges();
		target.containerMenu.broadcastChanges();

		flourish(thor, target);
		thor.sendSystemMessage(Component.translatable("message.projecthero.worthy.lent_owner", weaponName, target.getDisplayName())
				.withStyle(ChatFormatting.GOLD));
		target.sendSystemMessage(Component.translatable("message.projecthero.worthy.lent_borrower", thor.getDisplayName(), weaponName)
				.withStyle(ChatFormatting.AQUA));
		return true;
	}

	/**
	 * Puts {@code stack} in a free slot of {@code p}'s pack that is NOT the selected hotbar slot (the backpack rows first, then the
	 * hotbar), so a lent weapon never lands straight in his hand. False if there is no such slot (nothing moved).
	 */
	static boolean stowInPack(ServerPlayer p, ItemStack stack) {
		Inventory inv = p.getInventory();
		int size = inv.items.size();
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i < size; i++) {
				boolean hotbar = Inventory.isHotbarSlot(i);
				if (hotbar != (pass == 1) || i == inv.selected || !inv.items.get(i).isEmpty()) {
					continue;
				}
				inv.setItem(i, stack);
				return true;
			}
		}
		return false;
	}

	private static void refuse(ServerPlayer thor, String key, Component arg) {
		thor.displayClientMessage(Component.translatable(key, arg).withStyle(ChatFormatting.GRAY), true);
		thor.level().playSound(null, thor.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.4f);
	}

	/** A visual bolt on the Super Soldier, sparks between the two, thunder and a beacon chime. */
	private static void flourish(ServerPlayer thor, ServerPlayer target) {
		ServerLevel level = target.serverLevel();
		WorthyFx.bolt(level, target, target.position());
		Vec3 a = thor.position().add(0, 1.2, 0);
		Vec3 b = target.position().add(0, 1.2, 0);
		if (thor.level() == target.level()) {
			int n = (int) Math.max(4, a.distanceTo(b) * 3);
			for (int i = 0; i <= n; i++) {
				Vec3 at = a.lerp(b, i / (double) n);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 2, 0.05, 0.05, 0.05, 0.02);
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, 40, 0.4, 0.8, 0.4, 0.2);
		level.playSound(null, target.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9f, 1.3f);
		level.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.7f, 1.2f);
	}

	// ================================================================== calling it back

	/** The screen's Call back (server): ends the loan of {@code weapon} that {@code owner} has out. */
	public static boolean callBack(ServerPlayer owner, ThorWeapon weapon) {
		WorthyLoans.Loan loan = WorthyLoans.get(owner.server).lentBy(owner.getUUID(), weapon);
		if (loan == null) {
			owner.displayClientMessage(Component.translatable("message.projecthero.worthy.not_lent",
					Component.translatable(weapon.item().getDescriptionId())).withStyle(ChatFormatting.GRAY), true);
			refreshView(owner.server, owner.getUUID());
			return false;
		}
		end(owner.server, loan, Reason.RECALL);
		return true;
	}

	/** Ends {@code loan}: the weapon goes home (owner online) or is dropped at the Super Soldier (owner offline). Never two copies. */
	public static void end(MinecraftServer server, WorthyLoans.Loan loan, Reason reason) {
		WorthyLoans loans = WorthyLoans.get(server);
		if (!loans.remove(loan.hammerId())) {
			return; // already ended (two reasons in one tick)
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(loan.owner());
		ServerPlayer borrower = server.getPlayerList().getPlayer(loan.borrower());
		Component weaponName = Component.translatable(loan.weapon().item().getDescriptionId());
		if (borrower != null) {
			// a weapon out on one of his throws comes back to his hand first, where the recall can find it
			ThorThrown.returnAll(borrower);
			WorthyMoves.cancelThrow(borrower);
		}
		boolean home = false;
		if (owner != null) {
			home = MjolnirRecall.callBack(owner, loan.weapon(), loan.hammerId());
			if (!home && borrower != null) {
				// the registry could not place it (no record): hand the stack over directly -- still the one stack, moved
				ItemStack stack = take(borrower, loan.hammerId());
				if (!stack.isEmpty()) {
					if (!owner.getInventory().add(stack)) {
						owner.drop(stack, false);
					}
					home = true;
				}
			}
		} else if (borrower != null) {
			ItemStack stack = take(borrower, loan.hammerId());
			if (!stack.isEmpty()) {
				borrower.drop(stack, false); // a dropped Thor weapon lies as its own resting, still-bound, callable entity
				borrower.sendSystemMessage(Component.translatable("message.projecthero.worthy.dropped", weaponName)
						.withStyle(ChatFormatting.GRAY));
			}
		}
		if (owner != null) {
			owner.sendSystemMessage(Component.translatable("message.projecthero.worthy.end." + reason.key + ".owner", weaponName,
					loan.borrowerName()).withStyle(ChatFormatting.GOLD));
		}
		if (borrower != null) {
			borrower.sendSystemMessage(Component.translatable("message.projecthero.worthy.end." + reason.key + ".borrower", weaponName,
					loan.ownerName()).withStyle(ChatFormatting.AQUA));
		}
		refreshView(server, loan.owner());
		refreshView(server, loan.borrower());
	}

	/** Takes the stack with {@code hammerId} out of {@code player} (every slot, then the cursor), or EMPTY. */
	static ItemStack take(ServerPlayer player, UUID hammerId) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && hammerId.equals(s.get(ModDataComponents.HAMMER_ID))) {
				inv.setItem(i, ItemStack.EMPTY);
				player.containerMenu.broadcastChanges();
				return s;
			}
		}
		ItemStack carried = player.containerMenu.getCarried();
		if (!carried.isEmpty() && hammerId.equals(carried.get(ModDataComponents.HAMMER_ID))) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
			return carried;
		}
		return ItemStack.EMPTY;
	}

	/** Where the lent stack is on {@code player} right now (every slot, out on a throw, the cursor), or EMPTY. Read only. */
	static ItemStack find(ServerPlayer player, UUID hammerId) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && hammerId.equals(s.get(ModDataComponents.HAMMER_ID))) {
				return s;
			}
		}
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack s = ThorThrown.of(player).in(hand);
			if (!s.isEmpty() && hammerId.equals(s.get(ModDataComponents.HAMMER_ID))) {
				return s;
			}
		}
		ItemStack carried = player.containerMenu.getCarried();
		return !carried.isEmpty() && hammerId.equals(carried.get(ModDataComponents.HAMMER_ID)) ? carried : ItemStack.EMPTY;
	}

	// ================================================================== upkeep

	/** Rebuilds {@code playerId}'s view lists from {@link WorthyLoans} (only writes, and so syncs, when they changed). */
	public static void refreshView(MinecraftServer server, UUID playerId) {
		ServerPlayer p = server.getPlayerList().getPlayer(playerId);
		if (p == null) {
			return;
		}
		WorthyLoans loans = WorthyLoans.get(server);
		List<WorthyState.Lent> lent = new ArrayList<>();
		for (WorthyLoans.Loan loan : loans.ownedBy(playerId)) {
			lent.add(new WorthyState.Lent(loan.weapon().ordinal(), loan.borrowerName()));
		}
		List<WorthyState.Borrowed> borrowed = new ArrayList<>();
		for (WorthyLoans.Loan loan : loans.borrowedBy(playerId)) {
			borrowed.add(new WorthyState.Borrowed(loan.hammerId(), loan.weapon().ordinal(), loan.ownerName()));
		}
		WorthyState cur = WorthyState.of(p);
		if (cur.lent.equals(lent) && cur.borrowed.equals(borrowed)) {
			return;
		}
		WorthyState.edit(p, s -> {
			s.lent.clear();
			s.lent.addAll(lent);
			s.borrowed.clear();
			s.borrowed.addAll(borrowed);
		});
	}

	/** Every tick for every player; the checks run every {@link WorthyConfig#VALIDATE_EVERY_TICKS}. Public for the tests. */
	public static void tick(ServerPlayer p) {
		if (p.server.getTickCount() % WorthyConfig.VALIDATE_EVERY_TICKS != 0) {
			return;
		}
		validate(p);
	}

	/** The loan checks for every loan {@code p} is part of: squad, power, binding. Public for the tests. */
	public static void validate(ServerPlayer p) {
		MinecraftServer server = p.server;
		WorthyLoans loans = WorthyLoans.get(server);
		SquadManager squads = SquadManager.get(server);
		for (WorthyLoans.Loan loan : loans.involving(p.getUUID())) {
			if (!squads.sameSquad(loan.owner(), loan.borrower())) {
				end(server, loan, Reason.SQUAD);
				continue;
			}
			ServerPlayer borrower = server.getPlayerList().getPlayer(loan.borrower());
			if (borrower == null) {
				continue;
			}
			if (!SuperSoldier.hasPower(borrower)) {
				end(server, loan, Reason.POWER);
				continue;
			}
			ItemStack stack = find(borrower, loan.hammerId());
			UUID boundTo = stack.isEmpty() ? null : stack.get(ModDataComponents.BOUND_OWNER);
			if (!stack.isEmpty() && (boundTo == null || !boundTo.equals(loan.owner()))) {
				end(server, loan, Reason.UNBOUND);
			}
		}
		refreshView(server, p.getUUID());
	}

	/** {@code ALLOW_DEATH} (before anything drops): a dying Super Soldier gives back everything lent to him. */
	static void onDeath(ServerPlayer p) {
		for (WorthyLoans.Loan loan : WorthyLoans.get(p.server).borrowedBy(p.getUUID())) {
			end(p.server, loan, Reason.DEATH);
		}
	}

	/** Login: the view lists (whatever happened while he was away), then the checks. */
	static void onJoin(ServerPlayer p) {
		refreshView(p.server, p.getUUID());
		validate(p);
	}
}
