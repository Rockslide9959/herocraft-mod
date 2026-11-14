package com.projecthero.mod.blackpanther;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.blackpanther.data.BlackPantherState;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The single server-side API for Black Panther (T'Challa), the v0.17.3 Hero-Tier power. Nothing else touches
 * {@link BlackPantherState}; every change re-saves through {@link ServerPlayer#setAttached}. Moves in
 * {@link BlackPantherAbilities}, damage rules in {@link BlackPantherDamage}, numbers in {@link BlackPantherConfig}.
 *
 * <h2>How it works</h2>
 * Heart-Shaped Herbs grow in jungles; three in a bowl make the Heart-Shaped Herb Soup. Drinking it runs ten seconds of the
 * Ancestral Plane ({@link #drink}), then the power and the necklace ({@link #grant}). The necklace is part of the power, not
 * an item. <b>H</b> runs the suit out of it, texel by texel, the head last; H again runs it back in.
 *
 * <h2>Kinetic Energy</h2>
 * 0..100. The suit soaks 40% of every hit and stores it (1 damage = 4 Kinetic); it drains slowly out of combat. The moves
 * that spend it are on R; the bar lights the suit's trim purple as it fills.
 */
public final class BlackPanther {
	public static final String KEY = "black_panther";

	private static final ResourceLocation MELEE_ID = PowerToggles.id("black_panther_melee");
	private static final ResourceLocation ABSORB_ID = PowerToggles.id("black_panther_absorb");
	private static final ResourceLocation ARMOR_ID = PowerToggles.id("black_panther_armor");
	private static final ResourceLocation TOUGHNESS_ID = PowerToggles.id("black_panther_toughness");
	private static final ResourceLocation HERB_ID = PowerToggles.id("black_panther_herb");

	public static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.62f, 0.25f, 1.0f), 1.2f);
	public static final DustParticleOptions PURPLE_BIG = new DustParticleOptions(new Vector3f(0.72f, 0.4f, 1.0f), 2.2f);
	public static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.65f, 0.05f, 0.08f), 0.9f);

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TOGGLE = new ConcurrentHashMap<>();
	/** v0.17.4 Night Eyes: who has the darkness night vision on right now (it switches with a gap, so it never flickers). */
	private static final Map<UUID, Boolean> NIGHT_EYES = new ConcurrentHashMap<>();

	private BlackPanther() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		LAST_TOGGLE.clear();
		NIGHT_EYES.clear();
		BlackPantherAbilities.clearSessionState();
		BlackPantherUnsuited.clearSessionState();
		BlackPantherAbilityManager.clearSessionState();
		BlackPantherDamage.clearSessionState();
	}

	// ---------------------------------------------------------------- state

	public static BlackPantherState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.BLACK_PANTHER_STATE);
	}

	static void save(ServerPlayer player, BlackPantherState state) {
		player.setAttached(ModAttachments.BLACK_PANTHER_STATE, state);
	}

	public static BlackPantherState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
	}

	public static boolean hasPower(Player player) {
		BlackPantherState s = peek(player);
		return s != null && s.hasPower;
	}

	public static boolean suited(Player player) {
		BlackPantherState s = peek(player);
		return s != null && s.hasPower && s.suited;
	}

	public static boolean stalking(Player player) {
		BlackPantherState s = peek(player);
		return s != null && s.suited && s.stalking;
	}

	public static boolean absorbing(Player player) {
		BlackPantherState s = peek(player);
		return s != null && s.suited && s.absorbing;
	}

	/** N: the vibranium claws are out (only ever while suited). */
	public static boolean clawsOut(Player player) {
		BlackPantherState s = peek(player);
		return s != null && s.hasPower && s.suited && s.clawsOut;
	}

	/** Shift+N: the mask is off. */
	public static boolean unmasked(Player player) {
		BlackPantherState s = peek(player);
		return s != null && s.hasPower && s.suited && s.unmasked;
	}

	/** Panther Senses is on. */
	public static boolean sensing(Player player) {
		BlackPantherState s = peek(player);
		return s != null && s.hasPower && s.suited && s.senses;
	}

	/** The power is held but the necklace is off (hold H): no suit, no moves, the herb's physical passives only. */
	public static boolean necklaceOff(Player player) {
		BlackPantherState s = peek(player);
		return s != null && s.hasPower && s.necklaceOff;
	}

	/** v0.17.4: the crafted Panther Habit is in his necklace, so H can run the suit out. */
	public static boolean hasHabit(Player player) {
		BlackPantherState s = peek(player);
		return s != null && s.hasPower && s.habit;
	}

	/** v0.17.4 Night Eyes is on (the server's view; the client reads the night vision effect). */
	public static boolean nightEyes(Player player) {
		return Boolean.TRUE.equals(NIGHT_EYES.get(player.getUUID()));
	}

	public static float kinetic(Player player) {
		BlackPantherState s = peek(player);
		return s == null ? 0f : s.kinetic;
	}

	/** The Ancestral Plane is showing. Client-safe. */
	public static boolean inVision(Player player) {
		BlackPantherState s = peek(player);
		return s != null && !s.hasPower && s.visionUntil > 0L;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		BlackPantherState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		long now = player.level().getGameTime();
		Long last = LAST_MESSAGE.get(player.getUUID());
		if (last != null && now - last < 10 && now >= last) {
			return;
		}
		LAST_MESSAGE.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable(key, args).withStyle(colour), true);
	}

	// ---------------------------------------------------------------- Kinetic Energy

	public static void setKinetic(ServerPlayer player, float value) {
		BlackPantherState n = state(player).copy();
		n.kinetic = Math.max(0f, Math.min(BlackPantherConfig.KINETIC_MAX, value));
		save(player, n);
	}

	/** Stores Kinetic from a hit; {@code absorbed} also counts it against the current stance. */
	static void store(ServerPlayer player, float amount, boolean absorbed) {
		BlackPantherState n = state(player).copy();
		n.kinetic = Math.max(0f, Math.min(BlackPantherConfig.KINETIC_MAX, n.kinetic + amount));
		n.lastFightAt = player.level().getGameTime();
		if (absorbed) {
			n.absorbed += amount;
		}
		save(player, n);
	}

	/** A hit landed: keeps the bar from draining mid-fight. */
	static void fought(ServerPlayer player) {
		BlackPantherState n = state(player).copy();
		n.lastFightAt = player.level().getGameTime();
		save(player, n);
	}

	static boolean spend(ServerPlayer player, float cost) {
		BlackPantherState s = state(player);
		if (s.kinetic + 1.0e-3f < cost) {
			return false;
		}
		setKinetic(player, s.kinetic - cost);
		return true;
	}

	// ---------------------------------------------------------------- the soup and the power

	/** May this player drink the soup right now? Says why not (nothing is consumed on a refusal). */
	public static boolean canDrink(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (s.hasPower) {
			say(player, "message.projecthero.black_panther.already", ChatFormatting.GRAY);
			return false;
		}
		if (s.visionUntil > 0L) {
			return false;
		}
		return !com.projecthero.mod.hero.UniquePowers.refuse(player, KEY);
	}

	/** The soup is drunk: the Ancestral Plane opens. The power follows when it closes ({@link #tick}), even after a death. */
	public static void drink(ServerPlayer player) {
		BlackPantherState n = state(player).copy();
		n.visionUntil = player.level().getGameTime() + BlackPantherConfig.VISION_TICKS;
		save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.PLAYERS, 0.8f, 0.6f);
		player.displayClientMessage(Component.translatable("message.projecthero.black_panther.vision").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
	}

	/** The Ancestral Plane's end, or the command. False if the player already holds the power. */
	public static boolean grant(ServerPlayer player) {
		BlackPantherState old = state(player);
		if (old.hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			if (old.visionUntil > 0L) {
				BlackPantherState n = old.copy();
				n.visionUntil = 0L;
				save(player, n);
			}
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		BlackPantherState s = new BlackPantherState();
		s.hasPower = true;
		s.v174 = true; // v0.17.4: a new Black Panther starts without the suit; the Panther Habit is crafted
		save(player, s);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.2, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.5f, 1.5f);
		level.sendParticles(PURPLE_BIG, c.x, c.y, c.z, 30, 0.5, 0.8, 0.5, 0.0);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.black_panther.title").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.black_panther.subtitle").withStyle(ChatFormatting.LIGHT_PURPLE)));
		player.displayClientMessage(Component.translatable("message.projecthero.black_panther.acquired")
				.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.black_panther.acquired_hint")
				.withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		boolean hadHabit = state(player).hasPower && state(player).habit;
		BlackPantherAbilities.clear(player);
		BlackPantherUnsuited.clear(player);
		save(player, new BlackPantherState());
		if (hadHabit) {
			// v0.17.4: the crafted suit is not lost with the power -- it comes back out of the necklace as the item
			net.minecraft.world.item.ItemStack habit = new net.minecraft.world.item.ItemStack(com.projecthero.mod.blackpanther.item.BlackPantherItems.HABIT);
			if (!player.getInventory().add(habit)) {
				player.drop(habit, false);
			}
		}
		reconcile(player);
		PowerToggles.clearEffect(player, MobEffects.MOVEMENT_SPEED);
		PowerToggles.clearEffect(player, MobEffects.JUMP);
		PowerToggles.clearEffect(player, MobEffects.NIGHT_VISION);
		forget(player.getUUID());
	}

	private static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		LAST_TOGGLE.remove(id);
		NIGHT_EYES.remove(id);
		BlackPantherUnsuited.forget(id);
		BlackPantherAbilityManager.forget(id);
		BlackPantherDamage.forget(id);
	}

	/** Death, a new world, leaving: the running moves end. The power, the bar and the suit stay. */
	public static void clearTransient(ServerPlayer player) {
		BlackPantherAbilities.clear(player);
		BlackPantherUnsuited.clear(player);
	}

	public static void onPlayerJoin(ServerPlayer player) {
		migrate(player);
		reconcile(player);
	}

	/**
	 * v0.17.4: the suit became a crafted item (the Panther Habit). A Black Panther from 0.17.3 got the suit with the power, so
	 * they keep it: their state is marked as already holding the habit.
	 */
	static void migrate(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (s.hasPower && !s.v174) {
			BlackPantherState n = s.copy();
			n.v174 = true;
			n.habit = true;
			save(player, n);
		}
	}

	/**
	 * v0.17.4: the Panther Habit goes into the necklace (using the item). False, with the reason said, when he is not the Black
	 * Panther or the necklace already holds one (nothing is used up then).
	 */
	public static boolean bindHabit(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower) {
			say(player, "message.projecthero.black_panther.habit_no_power", ChatFormatting.GRAY);
			return false;
		}
		if (s.habit) {
			say(player, "message.projecthero.black_panther.habit_already", ChatFormatting.GRAY);
			return false;
		}
		BlackPantherState n = s.copy();
		n.habit = true;
		n.v174 = true;
		save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 neck = necklacePos(player);
		level.playSound(null, neck.x, neck.y, neck.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.7f);
		level.playSound(null, neck.x, neck.y, neck.z, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.8f, 1.6f);
		level.sendParticles(PURPLE_BIG, neck.x, neck.y, neck.z, 24, 0.3, 0.25, 0.3, 0.0);
		player.displayClientMessage(Component.translatable("message.projecthero.black_panther.habit_bound")
				.withStyle(ChatFormatting.LIGHT_PURPLE), false);
		return true;
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		reconcile(player);
	}

	// ---------------------------------------------------------------- the suit (H)

	/** The suit is still running out of the necklace. */
	public static boolean changing(Player player) {
		BlackPantherState s = peek(player);
		if (s == null || !s.hasPower || !s.suited) {
			return false;
		}
		long age = player.level().getGameTime() - s.suitChangeAt;
		return age >= 0 && age < BlackPantherConfig.SUIT_UP_TICKS;
	}

	public static void toggleSuit(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator() || changing(player)) {
			return;
		}
		if (s.necklaceOff) {
			say(player, "message.projecthero.black_panther.no_necklace", ChatFormatting.GRAY);
			return;
		}
		if (!s.suited && !s.habit) {
			say(player, "message.projecthero.black_panther.no_habit", ChatFormatting.GRAY);
			return;
		}
		long now = player.level().getGameTime();
		Long last = LAST_TOGGLE.get(player.getUUID());
		if (last != null && now - last < BlackPantherConfig.TOGGLE_COOLDOWN && now >= last) {
			return;
		}
		LAST_TOGGLE.put(player.getUUID(), now);
		if (s.suited) {
			suitDown(player);
		} else {
			suitUp(player);
		}
	}

	public static void suitUp(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower || s.suited) {
			return;
		}
		if (s.necklaceOff) {
			say(player, "message.projecthero.black_panther.no_necklace", ChatFormatting.GRAY);
			return;
		}
		if (!s.habit) {
			say(player, "message.projecthero.black_panther.no_habit", ChatFormatting.GRAY);
			return;
		}
		// v0.17.4: the unsuited kit's running moves and toggles end -- the suited kit takes the keys
		BlackPantherUnsuited.clear(player);
		long now = player.level().getGameTime();
		BlackPantherState n = state(player).copy();
		n.suited = true;
		n.senses = false;
		n.stalking = false;
		n.absorbing = false;
		n.suitChangeAt = now;
		n.animId = BlackPantherState.ANIM_SUIT_UP;
		n.animStart = now;
		n.unmasked = false;
		n.clawsOut = false;
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.4, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 1.4f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.8f);
		// a puff of violet out of the necklace as the suit starts to run
		Vec3 neck = necklacePos(player);
		level.sendParticles(PURPLE, neck.x, neck.y, neck.z, 14, 0.22, 0.12, 0.22, 0.0);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, neck.x, neck.y, neck.z, 6, 0.2, 0.1, 0.2, 0.0);
	}

	/** Where the necklace hangs: the collar, a little in front of the chest. */
	static Vec3 necklacePos(ServerPlayer player) {
		Vec3 f = Vec3.directionFromRotation(0f, player.yBodyRot);
		return player.position().add(f.x * 0.2, player.getBbHeight() * 0.78, f.z * 0.2);
	}

	public static void suitDown(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.suited) {
			return;
		}
		BlackPantherAbilities.clear(player);
		BlackPantherUnsuited.clear(player);
		long now = player.level().getGameTime();
		BlackPantherState n = state(player).copy();
		n.suited = false;
		n.suitChangeAt = now;
		n.stalking = false;
		n.absorbing = false;
		n.senses = false;
		n.clawsOut = false;
		n.unmasked = false;
		n.animId = BlackPantherState.ANIM_SUIT_DOWN;
		n.animStart = now;
		save(player, n);
		reconcile(player);
		PowerToggles.clearEffect(player, MobEffects.NIGHT_VISION);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.4, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.6f);
	}

	// ---------------------------------------------------------------- the necklace (hold H), the claws (N), the mask (Shift+N)

	/**
	 * Hold H: the necklace comes off (the suit drains back into it first) or goes back on. Without it there is no suit and no
	 * move, but the herb is still in him: Speed I and Jump Boost I stay.
	 */
	public static void toggleNecklace(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 neck = necklacePos(player);
		if (s.necklaceOff) {
			BlackPantherState n = state(player).copy();
			n.necklaceOff = false;
			save(player, n);
			say(player, "message.projecthero.black_panther.necklace_on", ChatFormatting.LIGHT_PURPLE);
			level.playSound(null, neck.x, neck.y, neck.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.2f);
			level.sendParticles(PURPLE, neck.x, neck.y, neck.z, 10, 0.2, 0.1, 0.2, 0.0);
			return;
		}
		if (s.suited) {
			suitDown(player);
		}
		BlackPantherAbilities.clear(player);
		BlackPantherState n = state(player).copy();
		n.necklaceOff = true;
		n.stalking = false;
		n.absorbing = false;
		n.senses = false;
		n.clawsOut = false;
		save(player, n);
		reconcile(player);
		PowerToggles.clearEffect(player, MobEffects.NIGHT_VISION);
		LAST_MESSAGE.remove(player.getUUID());
		say(player, "message.projecthero.black_panther.necklace_off", ChatFormatting.GRAY);
		level.playSound(null, neck.x, neck.y, neck.z, SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 1.0f, 1.3f);
	}

	/** N: the claws out of the fists or back in. Suited only. */
	public static void toggleClaws(ServerPlayer player) {
		if (!canAct(player)) {
			return;
		}
		setClaws(player, !state(player).clawsOut);
	}

	/** Claw Combo calls this to pop them out by itself. */
	static void setClaws(ServerPlayer player, boolean out) {
		BlackPantherState s = state(player);
		if (s.clawsOut == out || (out && !s.suited)) {
			return;
		}
		BlackPantherState n = s.copy();
		n.clawsOut = out;
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
				out ? SoundEvents.TRIDENT_RETURN : SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.9f, out ? 1.7f : 1.3f);
	}

	/** Shift+N: the mask off or back on. Suited only. */
	public static void toggleMask(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return;
		}
		if (s.necklaceOff) {
			say(player, "message.projecthero.black_panther.no_necklace", ChatFormatting.GRAY);
			return;
		}
		if (!s.suited) {
			say(player, "message.projecthero.black_panther.not_suited", ChatFormatting.GRAY);
			return;
		}
		BlackPantherState n = s.copy();
		n.unmasked = !s.unmasked;
		save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.7f,
				n.unmasked ? 0.9f : 1.5f);
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		// v0.17.4 the herb's strength, suit on or off
		if (hasPower(player)) {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, HERB_ID, BlackPantherConfig.HERB_MELEE, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, HERB_ID);
		}
		if (clawsOut(player)) {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID, BlackPantherConfig.SUIT_MELEE, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID);
		}
		if (suited(player)) {
			PowerToggles.modifier(player, Attributes.ARMOR, ARMOR_ID, BlackPantherConfig.SUIT_ARMOR, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, BlackPantherConfig.SUIT_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ARMOR, ARMOR_ID);
			PowerToggles.clearModifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID);
		}
		BlackPantherState st = peek(player);
		if (absorbing(player) || (st != null && st.hasPower && !st.suited && st.absorbing)) { // v0.17.4: Parry slows him too
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, ABSORB_ID, BlackPantherConfig.ABSORB_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, ABSORB_ID);
		}
	}

	static boolean canAct(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (s.necklaceOff) {
			say(player, "message.projecthero.black_panther.no_necklace", ChatFormatting.GRAY);
			return false;
		}
		if (!s.suited) {
			say(player, "message.projecthero.black_panther.not_suited", ChatFormatting.GRAY);
			return false;
		}
		if (changing(player)) {
			say(player, "message.projecthero.black_panther.changing", ChatFormatting.GRAY);
			return false;
		}
		return true;
	}

	/** v0.17.4: may the unsuited kit (T'Challa without the suit) be used right now? Works with the necklace off too. */
	static boolean canActBare(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		return !s.suited;
	}

	/** v0.17.4: the moveset the keys run right now -- the suited kit once the suit is on (or forming), the unsuited one otherwise. */
	public static boolean suitedKit(Player player) {
		BlackPantherState s = peek(player);
		return s != null && s.hasPower && s.suited;
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		BlackPantherState s = player.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
		if (s == null) {
			return;
		}
		long now = player.level().getGameTime();
		if (!s.hasPower) {
			if (s.visionUntil > 0L && s.visionUntil <= now && player.isAlive()) {
				grant(player); // the Ancestral Plane closes
			}
			return;
		}
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		if (player.tickCount % 20 == 0) {
			if (!s.v174) {
				migrate(player);
			}
			reconcile(player);
		}
		if (player.tickCount % 40 == 0) {
			// the herb: Speed I and Jump Boost I, suit on or off, necklace on or off
			PowerToggles.effect(player, MobEffects.MOVEMENT_SPEED, 0, false);
			PowerToggles.effect(player, MobEffects.JUMP, 0, false);
		}
		if (player.tickCount % 10 == 0) {
			nightEyes(player, s);
		}
		vitality(player, now);
		if (now % 5 == 0 && s.kinetic > 0f && now - s.lastFightAt > BlackPantherConfig.DRAIN_DELAY) {
			setKinetic(player, s.kinetic - BlackPantherConfig.DRAIN / 4f);
		}
		BlackPantherAbilities.tick(player);
		BlackPantherUnsuited.tick(player);
	}

	/**
	 * v0.17.4 Night Eyes: the suit's lenses give night vision all the time; out of it, the herb gives it whenever it is dark
	 * where he stands (on at light 6 or less, off again over 9, so it does not flicker at a torch's edge).
	 */
	private static void nightEyes(ServerPlayer player, BlackPantherState s) {
		boolean on;
		if (s.suited) {
			on = true;
		} else {
			int light = player.level().getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition()));
			boolean was = nightEyes(player);
			on = was ? light <= BlackPantherConfig.NIGHT_EYES_BRIGHT : light <= BlackPantherConfig.NIGHT_EYES_DARK;
		}
		if (on) {
			PowerToggles.effect(player, MobEffects.NIGHT_VISION, 0, false);
			NIGHT_EYES.put(player.getUUID(), true);
		} else if (nightEyes(player) || player.tickCount % 40 == 0) {
			PowerToggles.clearEffect(player, MobEffects.NIGHT_VISION);
			NIGHT_EYES.remove(player.getUUID());
		}
	}

	/** v0.17.4 Herb Vitality: 1 health every 3 s once he has gone 5 s without being hurt, suit on or off. */
	private static void vitality(ServerPlayer player, long now) {
		if (now % BlackPantherConfig.HEAL_EVERY != 0 || !player.isAlive() || player.getHealth() >= player.getMaxHealth()) {
			return;
		}
		if (now - BlackPantherDamage.lastHurt(player) < BlackPantherConfig.HEAL_DELAY) {
			return;
		}
		player.heal(1.0f);
	}

	/** v0.17.4: Herb Vitality is mending him right now (hurt, and it has been 5 s since the last hit). Server side. */
	public static boolean healing(ServerPlayer player) {
		return hasPower(player) && player.getHealth() < player.getMaxHealth()
				&& player.level().getGameTime() - BlackPantherDamage.lastHurt(player) >= BlackPantherConfig.HEAL_DELAY;
	}
}
