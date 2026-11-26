package com.projecthero.mod.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hammer.ThorWeapon;
import com.projecthero.mod.hammer.ThorWeaponHands;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.7 (user: "for the dual throw ability make the weapons actually leave the players hands and then return after"): a Thor
 * weapon that a scripted move has thrown -- Twin Throw (both), Stormbreaker's Judgement and Ragnarok (the axe). The real stack
 * leaves the hand for the flight and is held here, on the player, until {@code mainBack} / {@code offBack} (game time), when
 * it lands back in the hand it left ({@link ThorWeaponHands#place}). The flight itself is drawn by the client from the move's
 * timeline ({@code ThorKitFxRenderer}), out and back to the hand.
 * <p>
 * Why on the player and not as an entity: the moves are timed to the tick (the impale, the burst), and a stack that never
 * exists in the world cannot be lost in an unloaded chunk, picked up, pushed into a portal or duplicated. Safety nets:
 * <ul>
 *   <li>persistent -- a logout or a server stop saves it with the player and the next tick hands it back;</li>
 *   <li>death -- {@link #returnAll} runs first thing in {@code ServerPlayer.die} ({@code ThorThrownDeathMixin}), so the weapon is
 *   back in the inventory before anything drops or is kept; {@code copyOnDeath} too, in case a death skips {@code die};</li>
 *   <li>a dimension change keeps the same player, so the return simply happens there;</li>
 *   <li>the call key treats a weapon in here as already with him ({@code MjolnirRecall}), so it is never rebuilt elsewhere.</li>
 * </ul>
 * While a weapon is out, {@link ThorState#of} still counts it as held: the state, its perks, flight and the HUD carry on through
 * the throw. Synced to every viewer so their hands show empty and the client knows which hand it left.
 */
public record ThorThrown(ItemStack main, ItemStack off, long mainBack, long offBack) {
	public static final ThorThrown EMPTY = new ThorThrown(ItemStack.EMPTY, ItemStack.EMPTY, 0L, 0L);

	/** Never out longer than this, whatever a save says (a corrupted return time hands it straight back). */
	public static final int MAX_OUT_TICKS = 20 * 10;

	public static final Codec<ThorThrown> CODEC = RecordCodecBuilder.create(i -> i.group(
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("main", ItemStack.EMPTY).forGetter(ThorThrown::main),
			ItemStack.OPTIONAL_CODEC.optionalFieldOf("off", ItemStack.EMPTY).forGetter(ThorThrown::off),
			Codec.LONG.optionalFieldOf("main_back", 0L).forGetter(ThorThrown::mainBack),
			Codec.LONG.optionalFieldOf("off_back", 0L).forGetter(ThorThrown::offBack)).apply(i, ThorThrown::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, ThorThrown> STREAM_CODEC = StreamCodec.composite(
			ItemStack.OPTIONAL_STREAM_CODEC, ThorThrown::main,
			ItemStack.OPTIONAL_STREAM_CODEC, ThorThrown::off,
			ByteBufCodecs.VAR_LONG, ThorThrown::mainBack,
			ByteBufCodecs.VAR_LONG, ThorThrown::offBack,
			ThorThrown::new);

	public static ThorThrown of(Player player) {
		return player.getAttachedOrElse(ModAttachments.THOR_THROWN, EMPTY);
	}

	public boolean isEmpty() {
		return main.isEmpty() && off.isEmpty();
	}

	public ItemStack in(InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? main : off;
	}

	public long backAt(InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? mainBack : offBack;
	}

	public boolean has(ThorWeapon weapon) {
		return weapon.is(main) || weapon.is(off);
	}

	private ThorThrown with(InteractionHand hand, ItemStack stack, long back) {
		return hand == InteractionHand.MAIN_HAND ? new ThorThrown(stack, off, back, offBack) : new ThorThrown(main, stack, mainBack, back);
	}

	// ------------------------------------------------------------------ queries (both sides)

	/** Whether {@code player} has a Thor weapon out on a scripted throw. */
	public static boolean any(Player player) {
		return !of(player).isEmpty();
	}

	/** The stack in {@code hand}, or -- while that hand is empty because its weapon is out on a throw -- the thrown one. */
	public static ItemStack inHand(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		return held.isEmpty() ? of(player).in(hand) : held;
	}

	/** Whether {@code weapon} is in either hand or out on a throw from one. */
	public static boolean holds(Player player, ThorWeapon weapon) {
		return weapon.is(player.getMainHandItem()) || weapon.is(player.getOffhandItem()) || of(player).has(weapon);
	}

	/** The hand holding {@code weapon} right now, or null. */
	public static InteractionHand handOf(Player player, ThorWeapon weapon) {
		if (weapon.is(player.getMainHandItem())) {
			return InteractionHand.MAIN_HAND;
		}
		if (weapon.is(player.getOffhandItem())) {
			return InteractionHand.OFF_HAND;
		}
		return null;
	}

	// ------------------------------------------------------------------ server

	/**
	 * The {@code weapon} in his hand leaves it for {@code ticks} ticks: the stack moves in here and the hand is emptied.
	 *
	 * @return the hand it left, or null if he is not holding it (or that hand already has a weapon out)
	 */
	public static InteractionHand launch(ServerPlayer player, ThorWeapon weapon, int ticks) {
		InteractionHand hand = handOf(player, weapon);
		if (hand == null) {
			return null;
		}
		ThorThrown out = of(player);
		if (!out.in(hand).isEmpty()) {
			return null;
		}
		ItemStack stack = player.getItemInHand(hand);
		player.setItemInHand(hand, ItemStack.EMPTY);
		long back = player.level().getGameTime() + Math.max(1, Math.min(ticks, MAX_OUT_TICKS));
		player.setAttached(ModAttachments.THOR_THROWN, out.with(hand, stack, back));
		return hand;
	}

	/** Every server tick: hands back whatever is due (or overdue, or saved with a nonsense time). */
	public static void tick(ServerPlayer player) {
		ThorThrown out = of(player);
		if (out.isEmpty()) {
			return;
		}
		long now = player.level().getGameTime();
		for (InteractionHand hand : InteractionHand.values()) {
			long back = out.backAt(hand);
			if (!out.in(hand).isEmpty() && (now >= back || back - now > MAX_OUT_TICKS)) {
				returnNow(player, hand);
			}
		}
	}

	/** Everything comes back at once (death, logout, the power being taken away). */
	public static void returnAll(ServerPlayer player) {
		if (!any(player)) {
			return;
		}
		returnNow(player, InteractionHand.MAIN_HAND);
		returnNow(player, InteractionHand.OFF_HAND);
	}

	private static void returnNow(ServerPlayer player, InteractionHand hand) {
		ThorThrown out = of(player);
		ItemStack stack = out.in(hand);
		if (stack.isEmpty()) {
			return;
		}
		// clear first, so nothing below can ever see the stack in two places
		ThorThrown rest = out.with(hand, ItemStack.EMPTY, 0L);
		player.setAttached(ModAttachments.THOR_THROWN, rest.isEmpty() ? EMPTY : rest);
		ThorWeaponHands.Landing landing = ThorWeaponHands.place(player, stack, ThorWeapon.of(stack), hand, false);
		if (landing == ThorWeaponHands.Landing.NOWHERE) {
			// hand taken and the pack full: it drops at his feet (a dropped Mjolnir / Stormbreaker lies there as its own
			// tracked, callable entity)
			player.drop(stack, false);
		} else if (player.isAlive()) {
			// the catch: the same metallic thud a returning Mjolnir / Stormbreaker makes
			player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
					net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
			player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.MACE_SMASH_GROUND,
					net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.3f);
		}
	}
}
