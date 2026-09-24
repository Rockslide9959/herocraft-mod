package com.projecthero.mod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.worthiness.Worthiness;
import com.projecthero.mod.worthiness.WorthinessEnforcer;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Two jobs, both about Mjolnir never behaving like an ordinary dropped item:
 *
 * <ol>
 *   <li><b>Promotion.</b> Any loose Mjolnir {@link ItemEntity} is swapped for a real
 *   {@link MjolnirEntity} on its first tick, so it stays trackable/summon-able and never despawns
 *   or burns, per THOR_DESIGN.md section 3. This is a catch-all: it covers the drop key, dropping
 *   out of the inventory screen, death drops, dispensers, and anything else that can put the hammer
 *   on the ground -- rather than intercepting each call site that drops an item.</li>
 *   <li><b>Worthiness.</b> Physically picking one up (walking over it) is cancelled for unworthy
 *   players, so it never even briefly enters their inventory during the tick before promotion.</li>
 * </ol>
 *
 * <p>Promotion deliberately happens on {@code tick} rather than by intercepting
 * {@code Player.drop}: {@code GiveCommand} calls {@code drop()} with a <em>real</em> stack purely
 * to spawn the short-lived decorative item that flies into you on a successful {@code /give}, and
 * flags it with {@code makeFakeItem()} immediately afterward. Hijacking that call turned the
 * decoration into a second, genuine hammer -- the "duplicate hammers on give" bug. By the time an
 * entity first ticks, that flag is set, so {@link #pickupDelay} tells the two apart exactly.
 *
 * <p>Server-authoritative only (guarded by the {@code isClientSide()} checks): worthiness isn't
 * synced to clients (it's meant to be discovered, not queryable), and spawning the replacement
 * entity is the server's job.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
	/** {@code ItemEntity.INFINITE_PICKUP_DELAY} -- what {@code setNeverPickUp()}/fake items use. */
	private static final int NEVER_PICK_UP = 32767;

	@Shadow
	private int pickupDelay;

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void projecthero$promoteToMjolnirEntity(CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (self.level().isClientSide() || self.isRemoved()) {
			return;
		}

		ItemStack stack = self.getItem();
		if (!stack.is(ModItems.MJOLNIR)) {
			return;
		}
		if (this.pickupDelay == NEVER_PICK_UP) {
			// A decorative/never-collectable item -- most importantly the one /give spawns to play
			// the "item flies into you" animation. Not a hammer that's actually in the world.
			return;
		}

		MjolnirEntity hammer = MjolnirEntity.createResting(self.level(), self.getOwner(), stack,
				self.position(), self.getDeltaMovement());
		self.level().addFreshEntity(hammer);
		self.discard();
		ci.cancel();
	}

	/**
	 * A Green Lantern Hard-Light Tool Kit piece must never actually land on the ground -- v0.11.9,
	 * explicit user request ("if the player drops any part of the tool kit all of it must disappear and
	 * none of the tool kit items must fall onto the ground"). Before this fix, dropping one piece (Q,
	 * dragging it out of the inventory screen, a death drop, ...) correctly tore down the rest of the kit
	 * (see {@code GreenLanternConstructs#tickKind}'s TOOL_KIT case, which ends the construct once a piece
	 * leaves the tracked player's inventory/offhand/cursor), but the dropped piece itself still spawned a
	 * real, visible {@link ItemEntity} that sat on the ground until walked over -- at which point it
	 * silently vanished again the moment the next duplicate sweep ran, reading as a buggy "item
	 * disappears when picked up" rather than "never touches the ground at all". Same catch-all-on-tick
	 * approach as {@link #projecthero$promoteToMjolnirEntity} above, for the same reason: it covers every
	 * way an item can end up dropped without needing to hook each call site individually.
	 */
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void projecthero$discardLooseToolKitPieces(CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (self.level().isClientSide() || self.isRemoved()) {
			return;
		}
		if (com.projecthero.mod.greenlantern.construct.GreenLanternConstructs.isToolKitPiece(self.getItem())) {
			self.discard();
			ci.cancel();
		}
	}

	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void projecthero$blockUnworthyPickup(Player player, CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (self.level().isClientSide()) {
			return;
		}

		ItemStack stack = self.getItem();
		if (!stack.is(ModItems.MJOLNIR)) {
			return;
		}
		if (WorthinessEnforcer.bypassesWorthiness(player) || Worthiness.isWorthy(player)) {
			return;
		}
		if (Worthiness.hasAscensionEffect(player) && player.getInventory().getFreeSlot() >= 0
					&& player instanceof net.minecraft.server.level.ServerPlayer sp) {
			// Hero of the Village lifting it: becomes Thor and the hammer arrives bound (mutated in place
			// before vanilla's own pickup moves it into the inventory).
			Worthiness.ascend(sp, stack);
			return;
		}

		ci.cancel();
		WorthinessEnforcer.playRejectionFeedback(player, self.position());
	}
}
