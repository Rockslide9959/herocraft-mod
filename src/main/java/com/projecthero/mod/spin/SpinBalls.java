package com.projecthero.mod.spin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.spin.data.SpinState;
import com.projecthero.mod.spin.entity.SteelBallEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 round 2: the two Steel Balls are real items he has to hold. Each {@code projecthero:steel_ball} stack carries a
 * {@link BallId} (owner, ball 0 / 1, generation). The rules:
 * <ul>
 *   <li><b>What needs what</b> ({@link SpinAbilityManager#ballsNeeded}): one own ball in the main hand for most moves, one in each
 *   hand for Twin Balls and Spin Shield, none for Spin Leap and Valkyrie.</li>
 *   <li><b>Throwing</b> empties that hand ({@link #take}); the {@link SteelBallEntity} carries the ball's index and hand, and the catch
 *   puts it back ({@link #giveBack}): the same hand if empty, else a free slot, else the hand anyway, dropping what was there. Scan,
 *   Quake and Shield take the ball(s) out while they run ({@link Spin#takeBusy}) and give them back after ({@link #tick}).</li>
 *   <li><b>Lost balls</b>: kept on death ({@link #ensure} on respawn: a ball he no longer has is remade and the dropped copy goes
 *   stale). Only the owner picks one up (an ItemEntity mixin). R with an empty main hand recalls a ball left on the ground or in a
 *   chest ({@link #recall}). A ball destroyed (lava, the void) comes back after {@link SpinConfig#BALL_RESPAWN_TICKS}
 *   ({@link #onItemEntityGone}).</li>
 *   <li><b>Staleness</b>: every remake bumps that ball's generation in {@link SpinState}; an item with an older generation (or whose
 *   owner no longer has the Spin) vanishes wherever it ticks.</li>
 * </ul>
 * Static state: {@link #LAST_SEEN} (UUID-keyed positions, no worlds); {@link #clearSessionState} via {@code Spin}.
 */
public final class SpinBalls {
	/** Which ball this is and whose. */
	public record BallId(UUID owner, int index, int gen) {
		public static final Codec<BallId> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(BallId::owner),
				Codec.INT.fieldOf("index").forGetter(BallId::index),
				Codec.INT.optionalFieldOf("gen", 0).forGetter(BallId::gen)
		).apply(i, BallId::new));
		public static final StreamCodec<io.netty.buffer.ByteBuf, BallId> STREAM_CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, BallId::owner, ByteBufCodecs.VAR_INT, BallId::index, ByteBufCodecs.VAR_INT, BallId::gen, BallId::new);
	}

	public static final DataComponentType<BallId> BALL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ProjectHeroMod.id("steel_ball"), DataComponentType.<BallId>builder().persistent(BallId.CODEC).networkSynchronized(BallId.STREAM_CODEC)
					.build());

	/** Where each ball was last seen (owner -> [dimension, x, y, z] per ball), for the recall's flight. */
	private record Seen(ResourceKey<Level> dim, Vec3 pos) {
	}

	private static final Map<UUID, Seen[]> LAST_SEEN = new ConcurrentHashMap<>();

	private SpinBalls() {
	}

	/** Loads the class (registers the component); called by {@code SpinItems}. */
	static void bootstrap() {
	}

	static void clearSessionState() {
		LAST_SEEN.clear();
	}

	static void forget(UUID id) {
		LAST_SEEN.remove(id);
	}

	// ---------------------------------------------------------------- what a stack is

	public static BallId id(ItemStack stack) {
		return stack.isEmpty() || !stack.is(SpinItems.STEEL_BALL) ? null : stack.get(BALL);
	}

	/** A fresh ball item: {@code owner}'s ball {@code index}, at its current generation. */
	public static ItemStack stack(Player owner, int index) {
		SpinState s = Spin.peek(owner);
		ItemStack st = new ItemStack(SpinItems.STEEL_BALL);
		st.set(BALL, new BallId(owner.getUUID(), index, s == null ? 0 : s.gen(index)));
		return st;
	}

	/** {@code stack} is {@code p}'s own current ball (not someone else's, not a stale copy). Client-safe. */
	public static boolean isOwn(Player p, ItemStack stack) {
		BallId id = id(stack);
		if (id == null || !id.owner().equals(p.getUUID()) || id.index() < 0 || id.index() >= SpinConfig.BALLS) {
			return false;
		}
		SpinState s = Spin.peek(p);
		return s != null && s.hasPower && s.gen(id.index()) == id.gen();
	}

	/** The index of the own ball really held in {@code hand}, or -1. Client-safe. */
	public static int heldIndex(Player p, InteractionHand hand) {
		ItemStack st = p.getItemInHand(hand);
		return isOwn(p, st) ? id(st).index() : -1;
	}

	/**
	 * The index of the ball {@code hand} works with, or -1. Client-safe. Normally the own ball held in it; v0.17.4 (user: "the player
	 * doesn't have to hold the balls to throw them, they automatically equip"): in Gyro's suit ({@link GyroSuitItem#suitWorn}) a hand
	 * without one takes a ball from the holsters -- one anywhere else in his inventory -- the main hand the lowest-numbered, the off hand
	 * the other. Throwing it takes it from its slot ({@link #takeOut}) and the catch puts it back there ({@link #giveBack}).
	 */
	public static int inHand(Player p, InteractionHand hand) {
		int main = heldIndex(p, InteractionHand.MAIN_HAND), off = heldIndex(p, InteractionHand.OFF_HAND);
		int held = hand == InteractionHand.MAIN_HAND ? main : off;
		if (held >= 0 || !GyroSuitItem.suitWorn(p)) {
			return held;
		}
		int first = -1, second = -1;
		for (int i = 0; i < SpinConfig.BALLS; i++) {
			if (i != main && i != off && holstered(p, i)) {
				if (first < 0) {
					first = i;
				} else if (second < 0) {
					second = i;
				}
			}
		}
		if (hand == InteractionHand.MAIN_HAND) {
			return first;
		}
		return main >= 0 ? first : second;
	}

	/** v0.17.4: owed-field bits -- ball i came off the holsters (it goes back there), ball i hangs on the belt (for every viewer). */
	public static int fromHolsterBit(int i) {
		return 1 << (4 + i);
	}

	public static int onBeltBit(int i) {
		return 1 << (6 + i);
	}

	/**
	 * v0.17.4: ball {@code i} is in his inventory but in neither hand (where the suit's holsters carry it). On a client, for anyone but
	 * the local player (whose inventory is not sent), the synced belt bits answer.
	 */
	public static boolean holstered(Player p, int i) {
		if (heldIndex(p, InteractionHand.MAIN_HAND) == i || heldIndex(p, InteractionHand.OFF_HAND) == i) {
			return false;
		}
		if (p.level().isClientSide() && !isLocal(p)) {
			SpinState s = Spin.peek(p);
			return s != null && (s.owed & onBeltBit(i)) != 0;
		}
		return inInventory(p, i);
	}

	/** How many of his balls hang on the belt (0..2). Client-safe. */
	public static int holsteredCount(Player p) {
		int n = 0;
		for (int i = 0; i < SpinConfig.BALLS; i++) {
			if (holstered(p, i)) {
				n++;
			}
		}
		return n;
	}

	private static boolean isLocal(Player p) {
		return p.isLocalPlayer();
	}

	/** How many hands hold one of his balls (0..2; in Gyro's suit the holstered balls count). Client-safe. */
	public static int handsHolding(Player p) {
		return (inHand(p, InteractionHand.MAIN_HAND) >= 0 ? 1 : 0) + (inHand(p, InteractionHand.OFF_HAND) >= 0 ? 1 : 0);
	}

	/** Ball {@code index} is in one of his hands. Client-safe. */
	public static boolean ballInHand(Player p, int index) {
		return inHand(p, InteractionHand.MAIN_HAND) == index || inHand(p, InteractionHand.OFF_HAND) == index;
	}

	/** Ball {@code index} is somewhere in his inventory (hands, armour, the slot under the cursor). */
	public static boolean inInventory(Player p, int index) {
		Inventory inv = p.getInventory();
		for (int k = 0; k < inv.getContainerSize(); k++) {
			ItemStack st = inv.getItem(k);
			if (isOwn(p, st) && id(st).index() == index) {
				return true;
			}
		}
		ItemStack carried = p.containerMenu == null ? ItemStack.EMPTY : p.containerMenu.getCarried();
		return isOwn(p, carried) && id(carried).index() == index;
	}

	/**
	 * A stack that should vanish: a ball whose owner is online and either no longer has the Spin or has a newer generation of it.
	 * An offline owner's balls are left alone.
	 */
	public static boolean stale(MinecraftServer server, ItemStack stack) {
		BallId id = id(stack);
		if (id == null || server == null) {
			return false;
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(id.owner());
		if (owner == null) {
			return false;
		}
		SpinState s = Spin.peek(owner);
		return s == null || !s.hasPower || id.index() < 0 || id.index() >= SpinConfig.BALLS || s.gen(id.index()) != id.gen();
	}

	// ---------------------------------------------------------------- taking and giving

	/**
	 * Ball {@code index} leaves {@code hand} -- or, in Gyro's suit, its holster slot (v0.17.4: then it is marked to come back to the
	 * holsters, not the hand). Whatever else the hand holds stays.
	 */
	public static void takeOut(ServerPlayer p, InteractionHand hand, int index) {
		if (heldIndex(p, hand) == index) {
			p.setItemInHand(hand, ItemStack.EMPTY);
			SpinState n = Spin.state(p).copy();
			n.owed &= ~fromHolsterBit(index);
			Spin.save(p, n);
			return;
		}
		Inventory inv = p.getInventory();
		for (int k = 0; k < inv.getContainerSize(); k++) {
			ItemStack st = inv.getItem(k);
			if (isOwn(p, st) && id(st).index() == index) {
				inv.setItem(k, ItemStack.EMPTY);
				SpinState n = Spin.state(p).copy();
				n.owed |= fromHolsterBit(index);
				n.owed &= ~onBeltBit(index);
				Spin.save(p, n);
				return;
			}
		}
	}

	/**
	 * Ball {@code index} comes back: into {@code hand} if it is empty, else the first free slot, else into {@code hand} anyway and
	 * what was there drops at his feet (like Mjolnir's recall).
	 */
	public static void giveBack(ServerPlayer p, int index, InteractionHand hand) {
		if (!Spin.hasPower(p) || inInventory(p, index)) {
			return;
		}
		ItemStack ball = stack(p, index);
		// v0.17.4: a ball that came off the suit's holsters goes back onto them (a free inventory slot that isn't in his hand)
		SpinState cur = Spin.state(p);
		if ((cur.owed & fromHolsterBit(index)) != 0) {
			SpinState n = cur.copy();
			n.owed &= ~fromHolsterBit(index);
			Spin.save(p, n);
			int slot = holsterSlot(p);
			if (slot >= 0) {
				p.getInventory().setItem(slot, ball);
				return;
			}
		}
		if (p.getItemInHand(hand).isEmpty()) {
			p.setItemInHand(hand, ball);
			return;
		}
		int free = p.getInventory().getFreeSlot();
		if (free >= 0) {
			p.getInventory().setItem(free, ball);
			return;
		}
		ItemStack old = p.getItemInHand(hand);
		p.setItemInHand(hand, ball);
		p.drop(old, false, false);
	}

	/** v0.17.4: a free slot for a holstered ball: the main inventory first, then the hotbar (never the selected slot), or -1. */
	static int holsterSlot(ServerPlayer p) {
		Inventory inv = p.getInventory();
		for (int k = 9; k < 36; k++) {
			if (inv.getItem(k).isEmpty()) {
				return k;
			}
		}
		for (int k = 0; k < 9; k++) {
			if (k != inv.selected && inv.getItem(k).isEmpty()) {
				return k;
			}
		}
		return -1;
	}

	/** A new ball goes to him: {@code first} hand if empty, else the other hand, else a free slot, else at his feet. */
	static void give(ServerPlayer p, int index, InteractionHand first) {
		if (inInventory(p, index)) {
			return;
		}
		ItemStack ball = stack(p, index);
		InteractionHand other = first == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		if (p.getItemInHand(first).isEmpty()) {
			p.setItemInHand(first, ball);
		} else if (p.getItemInHand(other).isEmpty()) {
			p.setItemInHand(other, ball);
		} else if (!p.getInventory().add(ball)) {
			p.drop(ball, false, false);
		}
	}

	/** Learning / a grant / the tune command: both balls, ball 0 to the main hand and ball 1 to the off hand when they're free. */
	static void giveBoth(ServerPlayer p) {
		give(p, 0, InteractionHand.MAIN_HAND);
		give(p, 1, InteractionHand.OFF_HAND);
	}

	/** Every Steel Ball of his (any generation) leaves his inventory (revoke, the tune command). */
	static void removeAll(ServerPlayer p) {
		Inventory inv = p.getInventory();
		for (int k = 0; k < inv.getContainerSize(); k++) {
			BallId id = id(inv.getItem(k));
			if (id != null && id.owner().equals(p.getUUID())) {
				inv.setItem(k, ItemStack.EMPTY);
			}
		}
		if (p.containerMenu != null) {
			BallId id = id(p.containerMenu.getCarried());
			if (id != null && id.owner().equals(p.getUUID())) {
				p.containerMenu.setCarried(ItemStack.EMPTY);
			}
		}
	}

	/** {@code /projecthero power tune spin balls}: a fresh pair; every older copy goes stale. */
	public static void replace(ServerPlayer p) {
		SpinMoves.recallBalls(p);
		removeAll(p);
		SpinState n = Spin.state(p).copy();
		n.thrown = 0;
		n.owed = 0;
		n.busy0 = 0L;
		n.busy1 = 0L;
		for (int i = 0; i < SpinConfig.BALLS; i++) {
			n.bumpGen(i);
			n.setRespawn(i, 0L);
		}
		Spin.save(p, n);
		giveBoth(p);
	}

	/**
	 * After a death: a ball he doesn't have any more (it dropped with the rest, or it was out) is remade in his inventory, and the
	 * copy left behind goes stale. With keepInventory he still has them and nothing happens.
	 */
	static void ensure(ServerPlayer p) {
		SpinState s = Spin.state(p);
		SpinState n = null;
		for (int i = 0; i < SpinConfig.BALLS; i++) {
			if (inInventory(p, i)) {
				continue;
			}
			if (n == null) {
				n = s.copy();
			}
			n.bumpGen(i);
			n.setRespawn(i, 0L);
		}
		if (n == null) {
			return;
		}
		Spin.save(p, n);
		giveBoth(p);
	}

	/** A ball in {@code state} that is not thrown, not owed, not waiting to respawn and not in his inventory (dropped, in a chest). */
	static boolean missing(ServerPlayer p, SpinState s, int i) {
		return (s.thrown & (1 << i)) == 0 && (s.owed & (1 << i)) == 0 && s.respawn(i) <= 0L && !inInventory(p, i);
	}

	// ---------------------------------------------------------------- the tick

	static void tick(ServerPlayer p) {
		SpinState s = Spin.state(p);
		long now = Spin.now(p);
		// owed balls come back once Scan / Quake / Shield are done with them
		for (int i = 0; i < SpinConfig.BALLS; i++) {
			if ((s.owed & (1 << i)) != 0 && now >= s.busy(i)) {
				boolean off = (s.owed & (1 << (i + 2))) != 0;
				SpinState n = Spin.state(p).copy();
				n.owed &= ~((1 << i) | (1 << (i + 2)));
				Spin.save(p, n);
				giveBack(p, i, off ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
			}
		}
		// a destroyed ball comes back
		for (int i = 0; i < SpinConfig.BALLS; i++) {
			long at = Spin.state(p).respawn(i);
			if (at > 0L && now >= at) {
				SpinState n = Spin.state(p).copy();
				n.setRespawn(i, 0L);
				n.bumpGen(i);
				Spin.save(p, n);
				give(p, i, InteractionHand.MAIN_HAND);
				Spin.say(p, "message.projecthero.spin.ball_respawned", ChatFormatting.GOLD);
			}
		}
		// a thrown ball that stopped ticking (it flew into a chunk that doesn't tick entities) or vanished without being removed
		// would never come back: catch it now
		if (p.tickCount % 10 == 0 && Spin.state(p).thrown != 0) {
			settleStuckBalls(p);
		}
		// v0.17.4: which balls hang on his belt, for every viewer (their copy of his inventory has only his hands)
		if (p.tickCount % 5 == 0) {
			int belt = 0;
			for (int i = 0; i < SpinConfig.BALLS; i++) {
				if (holstered(p, i)) {
					belt |= onBeltBit(i);
				}
			}
			SpinState cur = Spin.state(p);
			if ((cur.owed & (onBeltBit(0) | onBeltBit(1))) != belt) {
				SpinState n = cur.copy();
				n.owed = (n.owed & ~(onBeltBit(0) | onBeltBit(1))) | belt;
				Spin.save(p, n);
			}
		}
		// where his balls are, while he has them
		if (p.tickCount % 10 == 0) {
			for (int i = 0; i < SpinConfig.BALLS; i++) {
				if (inInventory(p, i)) {
					seen(p.getUUID(), i, p.level().dimension(), p.getEyePosition());
				}
			}
		}
	}

	/**
	 * His thrown balls: one sitting where entities don't tick is caught at once (its {@code remove} hands the item back); a ball
	 * marked out with no entity anywhere near him is given back.
	 */
	public static void settleStuckBalls(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		int found = 0;
		for (SteelBallEntity ball : level.getEntitiesOfClass(SteelBallEntity.class, p.getBoundingBox().inflate(SpinConfig.STUCK_BALL_SEARCH),
				b -> b.getOwner() == p && !b.isRemoved())) {
			if (!level.isPositionEntityTicking(ball.blockPosition())) {
				ball.discard();
				continue;
			}
			found |= 1 << ball.index();
		}
		int lost = Spin.state(p).thrown & ~found;
		for (int i = 0; i < SpinConfig.BALLS; i++) {
			if ((lost & (1 << i)) != 0) {
				Spin.setThrown(p, i, false);
				giveBack(p, i, InteractionHand.MAIN_HAND);
			}
		}
	}

	private static void seen(UUID owner, int index, ResourceKey<Level> dim, Vec3 pos) {
		Seen[] arr = LAST_SEEN.computeIfAbsent(owner, k -> new Seen[SpinConfig.BALLS]);
		if (index >= 0 && index < arr.length) {
			arr[index] = new Seen(dim, pos);
		}
	}

	// ---------------------------------------------------------------- loose balls (ItemEntity hooks)

	/** A ball lying in the world, each tick: stale copies vanish; current ones never despawn and are tracked for the recall. */
	public static boolean onItemEntityTick(ItemEntity e) {
		if (!(e.level() instanceof ServerLevel level)) {
			return false;
		}
		BallId id = id(e.getItem());
		if (id == null) {
			return false;
		}
		if (stale(level.getServer(), e.getItem())) {
			e.setItem(ItemStack.EMPTY);
			e.discard();
			return true;
		}
		if (e.tickCount <= 1) {
			e.setUnlimitedLifetime();
		}
		seen(id.owner(), id.index(), level.dimension(), e.position());
		return false;
	}

	/** Only the owner picks a ball up. */
	public static boolean mayPickUp(ItemEntity e, Player player) {
		BallId id = id(e.getItem());
		return id == null || id.owner().equals(player.getUUID());
	}

	/**
	 * A ball's ItemEntity left the world. Picked up or put in a hopper leaves it empty; anything else that removes it with the ball
	 * still inside (lava, fire, cactus, the void, /kill) destroyed it, and a new one comes in {@link SpinConfig#BALL_RESPAWN_TICKS}.
	 */
	public static void onItemEntityGone(ItemEntity e) {
		if (!(e.level() instanceof ServerLevel level) || e.getRemovalReason() == null) {
			return;
		}
		Entity.RemovalReason why = e.getRemovalReason();
		if (why != Entity.RemovalReason.KILLED && why != Entity.RemovalReason.DISCARDED) {
			return;
		}
		ItemStack st = e.getItem();
		BallId id = id(st);
		if (id == null || stale(level.getServer(), st)) {
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(id.owner());
		if (owner == null || !Spin.hasPower(owner) || inInventory(owner, id.index())) {
			return;
		}
		SpinState s = Spin.state(owner);
		if (s.respawn(id.index()) > 0L) {
			return;
		}
		SpinState n = s.copy();
		n.setRespawn(id.index(), Spin.now(owner) + SpinConfig.BALL_RESPAWN_TICKS);
		Spin.save(owner, n);
		Spin.say(owner, "message.projecthero.spin.ball_destroyed", ChatFormatting.GRAY);
	}

	// ---------------------------------------------------------------- the recall (R with an empty main hand)

	/**
	 * Every ball he has lost track of (dropped, left in a chest or another inventory) flies back to his hand. One on the ground nearby
	 * lifts off from where it lies; one somewhere else is remade (the old copy goes stale) and flies in from where it was last seen.
	 * False if no ball is missing.
	 */
	static boolean recall(ServerPlayer p) {
		SpinState s = Spin.state(p);
		boolean any = false;
		for (int i = 0; i < SpinConfig.BALLS; i++) {
			if (!missing(p, s, i)) {
				continue;
			}
			any = true;
			Vec3 from = null;
			ServerLevel level = p.serverLevel();
			final int idx = i;
			for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(SpinConfig.RECALL_RANGE), e -> {
				BallId id = id(e.getItem());
				return id != null && id.owner().equals(p.getUUID()) && id.index() == idx && isOwn(p, e.getItem());
			})) {
				from = e.position().add(0, 0.25, 0);
				e.setItem(ItemStack.EMPTY); // (not "destroyed")
				e.discard();
				break;
			}
			if (from == null) {
				Seen[] arr = LAST_SEEN.get(p.getUUID());
				Seen seen = arr == null ? null : arr[i];
				if (seen != null && seen.dim() == level.dimension() && seen.pos().distanceTo(p.position()) < SpinConfig.RECALL_RANGE) {
					from = seen.pos();
				} else {
					from = p.getEyePosition().add(0, 8, 0); // straight above him: always in his own (ticking) chunk
				}
				SpinState n = Spin.state(p).copy();
				n.bumpGen(i); // the copy in the chest (or wherever) is no longer his ball
				Spin.save(p, n);
			}
			// the flight starts no further than RECALL_FLIGHT_MAX away (toward where it was), so it is in entity-ticking range
			Vec3 eye = p.getEyePosition();
			if (from.distanceTo(eye) > SpinConfig.RECALL_FLIGHT_MAX) {
				from = eye.add(from.subtract(eye).normalize().scale(SpinConfig.RECALL_FLIGHT_MAX));
			}
			SteelBallEntity.recall(p, i, from, InteractionHand.MAIN_HAND);
		}
		if (any) {
			p.level().playSound(null, p.getX(), p.getY() + 1, p.getZ(), SpinSounds.BALL_THROW, SoundSource.PLAYERS, 0.6f, 1.4f);
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.spin.recall")
					.withStyle(ChatFormatting.GOLD), true);
		}
		return any;
	}

	/** For the tests: the box a ball ItemEntity search uses round {@code p}. */
	public static AABB searchBox(Player p) {
		return p.getBoundingBox().inflate(SpinConfig.RECALL_RANGE);
	}
}
