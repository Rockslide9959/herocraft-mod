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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Variant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 the Spin, Shift+X: whistle for <b>Valkyrie</b>. She is one horse per player (her UUID in {@link SpinState#valkyrie}):
 * tamed to him, saddled, in diamond horse armour, 40 health and very fast (0.42 speed). The whistle brings her galloping to him --
 * from wherever she is if she's near, else round from behind him -- and he is put in the saddle when she arrives (or after
 * {@link SpinConfig#VALKYRIE_GALLOP_TICKS} at most). If she dies, no whistling for 60 s; then a new Valkyrie comes.
 * A stray copy of her that loads in later (she was in an unloaded chunk when a new one came) is sent away
 * ({@link #onLoad}). {@link #CALLS} holds horses; {@code ServerStateReset} clears it.
 */
public final class SpinValkyrie {
	private record Call(AbstractHorse horse, int[] left) {
	}

	private static final Map<UUID, Call> CALLS = new ConcurrentHashMap<>();

	private SpinValkyrie() {
	}

	static void clearSessionState() {
		CALLS.clear();
	}

	/** Is {@code e} somebody's Valkyrie? */
	public static boolean isValkyrie(Entity e) {
		return e instanceof AbstractHorse && e.getTags().contains(SpinConfig.VALKYRIE_TAG);
	}

	/** {@code player}'s Valkyrie if she is loaded anywhere on the server (null otherwise). */
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
			if (e instanceof AbstractHorse h && h.isAlive()) {
				return h;
			}
		}
		return null;
	}

	/** Shift+X. False (with the reason shown) if she can't come. */
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
			horse.discard(); // she can't cross dimensions on her own: a fresh Valkyrie comes instead
			horse = null;
		}
		if (horse == null) {
			horse = spawn(player, level);
			if (horse == null) {
				return false;
			}
		} else {
			horse.ejectPassengers();
			if (horse.distanceTo(player) > SpinConfig.VALKYRIE_FETCH_DISTANCE) {
				Vec3 at = behind(player, level);
				horse.teleportTo(at.x, at.y, at.z);
			}
		}
		Spin.sound(player, SpinSounds.WHISTLE, 0.9f, 1.0f);
		CALLS.put(player.getUUID(), new Call(horse, new int[] { SpinConfig.VALKYRIE_GALLOP_TICKS }));
		player.displayClientMessage(Component.translatable("message.projecthero.spin.valkyrie_called").withStyle(ChatFormatting.GOLD), true);
		return true;
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

	/** Her gallop in, and the mount. */
	static void tick(ServerPlayer player) {
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
		if (d > 2.5 && call.left[0] > 0) {
			h.getNavigation().moveTo(player, 2.4);
			return;
		}
		CALLS.remove(player.getUUID());
		if (d > 3.0) {
			h.teleportTo(player.getX() + 1.0, player.getY(), player.getZ());
		}
		h.getNavigation().stop();
		player.startRiding(h, true);
		player.level().playSound(null, h.getX(), h.getY(), h.getZ(), net.minecraft.sounds.SoundEvents.HORSE_GALLOP, SoundSource.NEUTRAL, 0.8f, 1.0f);
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

	/** A Valkyrie that isn't her owner's current one (an old copy from an unloaded chunk) leaves. */
	static void onLoad(Entity e) {
		if (!isValkyrie(e) || e.getServer() == null || !(e instanceof AbstractHorse h) || h.getOwnerUUID() == null) {
			return;
		}
		ServerPlayer owner = e.getServer().getPlayerList().getPlayer(h.getOwnerUUID());
		if (owner != null && Spin.hasPower(owner) && !e.getUUID().toString().equals(Spin.state(owner).valkyrie)) {
			e.discard();
		}
	}
}
