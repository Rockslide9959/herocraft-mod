package com.projecthero.mod.spin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.spin.data.SpinState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Variant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 the Spin, Shift+X: <b>Valkyrie</b>. She is one horse per player (her UUID in {@link SpinState#valkyrie}): tamed to him,
 * saddled, in diamond horse armour, 40 health and very fast (0.42 speed).
 * <ul>
 *   <li><b>Tap (the call).</b> No Valkyrie out: a new one comes round from behind him. Already out (user, 2026-11-12): <i>no second
 *   horse</i> -- the one he has runs to him (path-finding at a gallop) and he is put in the saddle when she reaches him. Further than
 *   {@link SpinConfig#VALKYRIE_FETCH_DISTANCE} she is first brought round behind him; stuck ({@link SpinConfig#VALKYRIE_STUCK_TICKS}
 *   without getting closer) or still not there after {@link SpinConfig#VALKYRIE_GALLOP_TICKS}, she is brought next to him and he
 *   mounts. In another dimension she can't follow: she leaves and a fresh Valkyrie comes (still only one).</li>
 *   <li><b>Hold 1 s (the dismissal, user 2026-11-12)</b>, only while she is out ({@link SpinAbilityManager} tells a tap from the
 *   hold): he is put down if riding her, she runs off away from him and is gone after {@link SpinConfig#VALKYRIE_LEAVE_TICKS} (sooner
 *   once she's out of his sight), and the call waits {@link SpinConfig#VALKYRIE_DISMISS_COOLDOWN}.</li>
 *   <li>If she dies, no whistling for 60 s; then a new Valkyrie comes.</li>
 * </ul>
 * A stray copy of her that loads in later (she was in an unloaded chunk when a new one came, or was leaving when he logged off)
 * is sent away ({@link #onLoad}); every call also sends away any other loaded Valkyrie of his ({@link #sweepStrays}).
 * {@link #CALLS} and {@link #LEAVING} hold horses; {@code ServerStateReset} clears them (via {@link Spin#clearSessionState}).
 */
public final class SpinValkyrie {
	/** A call running: the horse, ticks left, the best distance so far and when it last got better. */
	private record Call(AbstractHorse horse, int[] left, double[] best, int[] sinceBetter) {
	}

	/** A dismissed horse running off: ticks left. */
	private record Leave(AbstractHorse horse, int[] left) {
	}

	private static final Map<UUID, Call> CALLS = new ConcurrentHashMap<>();
	private static final Map<UUID, Leave> LEAVING = new ConcurrentHashMap<>();

	private SpinValkyrie() {
	}

	static void clearSessionState() {
		CALLS.clear();
		LEAVING.clear();
	}

	/** He left: a horse still running off goes now; a call ends. */
	static void forget(UUID id) {
		CALLS.remove(id);
		Leave leave = LEAVING.remove(id);
		if (leave != null && !leave.horse.isRemoved()) {
			leave.horse.discard();
		}
	}

	/** Is {@code e} somebody's Valkyrie? */
	public static boolean isValkyrie(Entity e) {
		return e instanceof AbstractHorse && e.getTags().contains(SpinConfig.VALKYRIE_TAG);
	}

	/** {@code player}'s Valkyrie if she is loaded anywhere on the server (null otherwise, or while she's leaving). */
	public static AbstractHorse find(ServerPlayer player) {
		String id = Spin.state(player).valkyrie;
		if (id == null || id.isEmpty()) {
			return null;
		}
		UUID uuid;
		try {
			uuid = UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			return null;
		}
		for (ServerLevel level : player.server.getAllLevels()) {
			Entity e = level.getEntity(uuid);
			if (e instanceof AbstractHorse h && h.isAlive() && !h.getTags().contains(SpinConfig.VALKYRIE_LEAVING_TAG)) {
				return h;
			}
		}
		return null;
	}

	/** How many of {@code player}'s Valkyries are loaded in his level, leaving ones aside (for the tests: always 0 or 1). */
	public static int countLoaded(ServerPlayer player) {
		return player.serverLevel().getEntities(EntityTypeTest.forClass(AbstractHorse.class),
				h -> isValkyrie(h) && h.isAlive() && player.getUUID().equals(h.getOwnerUUID())
						&& !h.getTags().contains(SpinConfig.VALKYRIE_LEAVING_TAG)).size();
	}

	/** Shift+X tap. False (with the reason shown) if she can't come. */
	static boolean whistle(ServerPlayer player) {
		int dead = Spin.valkyrieCooldown(player);
		if (dead > 0) {
			Spin.say(player, "message.projecthero.spin.valkyrie_dead", ChatFormatting.RED, (dead + 19) / 20);
			return false;
		}
		if (player.getVehicle() != null && isValkyrie(player.getVehicle())) {
			Spin.say(player, "message.projecthero.spin.valkyrie_riding", ChatFormatting.GRAY);
			return false;
		}
		ServerLevel level = (ServerLevel) player.level();
		AbstractHorse horse = find(player);
		if (horse != null && horse.level() != level) {
			horse.discard(); // she can't cross dimensions on her own: a fresh Valkyrie comes instead (still only one)
			horse = null;
		}
		boolean running = horse != null;
		if (horse == null) {
			horse = spawn(player, level);
			if (horse == null) {
				return false;
			}
		} else {
			horse.ejectPassengers();
			if (horse.isLeashed()) {
				horse.dropLeash(true, true);
			}
			if (horse.distanceTo(player) > SpinConfig.VALKYRIE_FETCH_DISTANCE) {
				Vec3 at = behind(player, level);
				horse.teleportTo(at.x, at.y, at.z);
			}
		}
		sweepStrays(player, horse);
		reach(horse);
		Spin.sound(player, SpinSounds.WHISTLE, 0.9f, 1.0f);
		double d = horse.distanceTo(player);
		CALLS.put(player.getUUID(), new Call(horse, new int[] { SpinConfig.VALKYRIE_GALLOP_TICKS }, new double[] { d }, new int[] { 0 }));
		player.displayClientMessage(Component.translatable(running ? "message.projecthero.spin.valkyrie_running" : "message.projecthero.spin.valkyrie_called")
				.withStyle(ChatFormatting.GOLD), true);
		return true;
	}

	/** Any other loaded Valkyrie of his (an old copy, one left over from before) leaves: only ever one. */
	private static void sweepStrays(ServerPlayer player, AbstractHorse keep) {
		for (ServerLevel level : player.server.getAllLevels()) {
			for (AbstractHorse h : level.getEntities(EntityTypeTest.forClass(AbstractHorse.class),
					h -> h != keep && isValkyrie(h) && player.getUUID().equals(h.getOwnerUUID()))) {
				Leave leave = LEAVING.get(player.getUUID());
				if (leave == null || leave.horse != h) {
					h.discard();
				}
			}
		}
	}

	/** She plans her path far enough to reach him from {@link SpinConfig#VALKYRIE_FOLLOW_RANGE} away. */
	private static void reach(AbstractHorse horse) {
		AttributeInstance range = horse.getAttribute(Attributes.FOLLOW_RANGE);
		if (range != null && range.getBaseValue() < SpinConfig.VALKYRIE_FOLLOW_RANGE) {
			range.setBaseValue(SpinConfig.VALKYRIE_FOLLOW_RANGE);
		}
	}

	/** Where she comes round from: 10 blocks behind him on the ground (3 blocks behind underground). */
	private static Vec3 behind(ServerPlayer player, ServerLevel level) {
		Vec3 look = player.getViewVector(1f);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 far = player.position().subtract(flat.scale(10));
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(far));
		if (Math.abs(top.getY() - player.getY()) <= 6) {
			return new Vec3(far.x, top.getY(), far.z);
		}
		Vec3 near = player.position().subtract(flat.scale(3));
		return new Vec3(near.x, player.getY(), near.z);
	}

	private static AbstractHorse spawn(ServerPlayer player, ServerLevel level) {
		Horse horse = EntityType.HORSE.create(level);
		if (horse == null) {
			return null;
		}
		Vec3 at = behind(player, level);
		horse.moveTo(at.x, at.y, at.z, player.getYRot(), 0f);
		horse.setVariant(Variant.DARK_BROWN);
		horse.setTamed(true);
		horse.setOwnerUUID(player.getUUID());
		horse.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SpinConfig.VALKYRIE_HEALTH);
		horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SpinConfig.VALKYRIE_SPEED);
		horse.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(SpinConfig.VALKYRIE_JUMP);
		horse.setHealth(horse.getMaxHealth());
		horse.equipSaddle(new ItemStack(Items.SADDLE), null);
		horse.setBodyArmorItem(new ItemStack(Items.DIAMOND_HORSE_ARMOR));
		horse.setCustomName(Component.translatable("entity.projecthero.valkyrie"));
		horse.addTag(SpinConfig.VALKYRIE_TAG);
		horse.setPersistenceRequired();
		// remember her BEFORE she joins the world: onLoad sends away any Valkyrie that isn't the owner's current one
		SpinState n = Spin.state(player).copy();
		String previous = n.valkyrie;
		n.valkyrie = horse.getUUID().toString();
		Spin.save(player, n);
		if (!level.addFreshEntity(horse)) {
			SpinState back = Spin.state(player).copy();
			back.valkyrie = previous;
			Spin.save(player, back);
			return null;
		}
		return horse;
	}

	// ---------------------------------------------------------------- the dismissal (hold Shift+X 1 s)

	/** The hold's progress on his action bar: "Sending Valkyrie away" and a ten-step bar. */
	static void showDismissProgress(ServerPlayer player, int held) {
		int steps = 10;
		int filled = Math.min(steps, held * steps / Math.max(1, SpinConfig.VALKYRIE_DISMISS_HOLD));
		String bar = "▮".repeat(filled) + "▯".repeat(steps - filled);
		player.displayClientMessage(Component.translatable("message.projecthero.spin.valkyrie_dismissing", bar).withStyle(ChatFormatting.GOLD), true);
	}

	/** Held 1 s: she runs off and is gone; the call waits 10 s. False if she isn't out. */
	public static boolean dismiss(ServerPlayer player) {
		AbstractHorse horse = find(player);
		if (horse == null) {
			return false;
		}
		CALLS.remove(player.getUUID());
		if (player.getVehicle() == horse) {
			player.stopRiding();
		}
		horse.ejectPassengers();
		horse.addTag(SpinConfig.VALKYRIE_LEAVING_TAG);
		// she is no longer his current Valkyrie: the next call brings a new one
		SpinState n = Spin.state(player).copy();
		n.valkyrie = "";
		n.abilityReadyAt.put(SpinAbilityManager.VALKYRIE, Spin.now(player) + SpinConfig.VALKYRIE_DISMISS_COOLDOWN);
		Spin.save(player, n);
		if (horse.level() != player.level()) {
			horse.discard();
		} else {
			reach(horse);
			runAway(player, horse);
			Leave old = LEAVING.put(player.getUUID(), new Leave(horse, new int[] { SpinConfig.VALKYRIE_LEAVE_TICKS }));
			if (old != null && old.horse != horse && !old.horse.isRemoved()) {
				old.horse.discard();
			}
			player.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(), net.minecraft.sounds.SoundEvents.HORSE_GALLOP, SoundSource.NEUTRAL,
					0.8f, 1.0f);
		}
		Spin.sound(player, SpinSounds.WHISTLE, 0.7f, 0.75f);
		player.displayClientMessage(Component.translatable("message.projecthero.spin.valkyrie_dismissed").withStyle(ChatFormatting.GOLD), true);
		return true;
	}

	/** Off she goes: a point 24 blocks straight away from him. */
	private static void runAway(ServerPlayer player, AbstractHorse horse) {
		Vec3 away = horse.position().subtract(player.position()).multiply(1, 0, 1);
		if (away.lengthSqr() < 1.0e-4) {
			Vec3 look = player.getViewVector(1f);
			away = new Vec3(look.x, 0, look.z);
			if (away.lengthSqr() < 1.0e-4) {
				away = new Vec3(0, 0, 1);
			}
		}
		Vec3 to = horse.position().add(away.normalize().scale(24));
		horse.getNavigation().moveTo(to.x, horse.getY(), to.z, 2.4);
	}

	/** A horse is leaving for him right now (for the tests). */
	public static boolean leaving(ServerPlayer player) {
		return LEAVING.containsKey(player.getUUID());
	}

	// ---------------------------------------------------------------- the tick

	/** Her run in and the mount; a dismissed horse running off. */
	static void tick(ServerPlayer player) {
		tickLeave(player);
		Call call = CALLS.get(player.getUUID());
		if (call == null) {
			return;
		}
		AbstractHorse h = call.horse;
		if (!h.isAlive() || h.isRemoved() || h.level() != player.level() || !player.isAlive()) {
			CALLS.remove(player.getUUID());
			return;
		}
		call.left[0]--;
		double d = h.distanceTo(player);
		if (d < call.best[0] - 1.0) {
			call.best[0] = d;
			call.sinceBetter[0] = 0;
		} else {
			call.sinceBetter[0]++;
		}
		boolean stuck = call.sinceBetter[0] >= SpinConfig.VALKYRIE_STUCK_TICKS;
		if (d > 2.5 && call.left[0] > 0 && !stuck) {
			// running to him: re-plan every few ticks (he may be moving), or when the last path ran out
			if (h.getNavigation().isDone() || call.left[0] % 5 == 0) {
				h.getNavigation().moveTo(player, 2.4);
			}
			return;
		}
		CALLS.remove(player.getUUID());
		if (d > 3.0) {
			// stuck, or too slow: she is brought next to him
			h.teleportTo(player.getX() + 1.0, player.getY(), player.getZ());
		}
		h.getNavigation().stop();
		player.startRiding(h, true);
		player.level().playSound(null, h.getX(), h.getY(), h.getZ(), net.minecraft.sounds.SoundEvents.HORSE_GALLOP, SoundSource.NEUTRAL, 0.8f, 1.0f);
	}

	private static void tickLeave(ServerPlayer player) {
		Leave leave = LEAVING.get(player.getUUID());
		if (leave == null) {
			return;
		}
		AbstractHorse h = leave.horse;
		leave.left[0]--;
		boolean gone = h.isRemoved() || !h.isAlive() || h.level() != player.level();
		boolean outOfSight = !gone && h.distanceTo(player) > 16.0 && !player.hasLineOfSight(h);
		if (gone || outOfSight || leave.left[0] <= 0) {
			LEAVING.remove(player.getUUID());
			if (!h.isRemoved()) {
				h.discard();
			}
			return;
		}
		if (h.getNavigation().isDone()) {
			runAway(player, h);
		}
	}

	/** She is coming right now (for the tests). */
	public static boolean galloping(ServerPlayer player) {
		return CALLS.containsKey(player.getUUID());
	}

	/** Valkyrie died: 60 s before he can whistle again, and the next whistle brings a new horse. */
	static void onDeath(AbstractHorse horse) {
		if (!isValkyrie(horse) || horse.getServer() == null || horse.getOwnerUUID() == null) {
			return;
		}
		ServerPlayer owner = horse.getServer().getPlayerList().getPlayer(horse.getOwnerUUID());
		if (owner == null || !horse.getUUID().toString().equals(Spin.state(owner).valkyrie)) {
			return;
		}
		SpinState n = Spin.state(owner).copy();
		n.valkyrie = "";
		n.valkyrieDeadUntil = owner.level().getGameTime() + SpinConfig.VALKYRIE_DEAD_COOLDOWN;
		Spin.save(owner, n);
		CALLS.remove(owner.getUUID());
		owner.displayClientMessage(Component.translatable("message.projecthero.spin.valkyrie_fell").withStyle(ChatFormatting.RED), false);
	}

	/** A Valkyrie that isn't her owner's current one (an old copy from an unloaded chunk, or one that was leaving) leaves. */
	static void onLoad(Entity e) {
		if (!isValkyrie(e) || e.getServer() == null || !(e instanceof AbstractHorse h) || h.getOwnerUUID() == null) {
			return;
		}
		if (e.getTags().contains(SpinConfig.VALKYRIE_LEAVING_TAG)) {
			Leave leave = LEAVING.get(h.getOwnerUUID());
			if (leave == null || leave.horse != h) {
				e.discard();
			}
			return;
		}
		ServerPlayer owner = e.getServer().getPlayerList().getPlayer(h.getOwnerUUID());
		if (owner != null && Spin.hasPower(owner) && !e.getUUID().toString().equals(Spin.state(owner).valkyrie)) {
			e.discard();
		}
	}
}
