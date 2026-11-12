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

	/** The index of the own ball in {@code hand}, or -1. Client-safe. */
	public static int inHand(Player p, InteractionHand hand) {
		ItemStack st = p.getItemInHand(hand);
		return isOwn(p, st) ? id(st).index() : -1;
	}

	/** How many hands hold one of his balls (0..2). Client-safe. */
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

	/** Empties {@code hand} (the ball leaves it). */
	static void take(ServerPlayer p, InteractionHand hand) {
		p.setItemInHand(hand, ItemStack.EMPTY);
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
		// where his balls are, while he has them
		if (p.tickCount % 10 == 0) {
			for (int i = 0; i < SpinConfig.BALLS; i++) {
				if (inInventory(p, i)) {
					seen(p.getUUID(), i, p.level().dimension(), p.getEyePosition());
				}
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
					Vec3 look = p.getViewVector(1f);
					from = p.getEyePosition().add(look.x * -6, 8, look.z * -6);
				}
				SpinState n = Spin.state(p).copy();
				n.bumpGen(i); // the copy in the chest (or wherever) is no longer his ball
				Spin.save(p, n);
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
