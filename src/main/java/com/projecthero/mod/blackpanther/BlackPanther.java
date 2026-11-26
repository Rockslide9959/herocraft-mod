package com.projecthero.mod.blackpanther;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.blackpanther.data.BlackPantherState;
import com.projecthero.mod.blackpanther.item.BlackPantherItems;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The single server-side API for Black Panther (T'Challa), the v0.17.3 Hero-Tier power. Nothing else touches
 * {@link BlackPantherState}; every change re-saves through {@link ServerPlayer#setAttached}. Moves in
 * {@link BlackPantherAbilities} (suited) and {@link BlackPantherUnsuited}, damage rules in {@link BlackPantherDamage}, numbers in
 * {@link BlackPantherConfig}.
 *
 * <h2>How it works</h2>
 * Heart-Shaped Herbs grow in jungles; three in a bowl make the Heart-Shaped Herb Soup. Drinking it runs ten seconds of the
 * Ancestral Plane ({@link #drink}), then the power and the silver necklace ({@link #grant}).
 *
 * <h2>The suit (v0.17.7)</h2>
 * The user: "make the suit craftable instead please like the flash suit and you need to equip it, when the suit is worn you get
 * the new moveset". The suit is four crafted armour pieces ({@link BlackPantherItems#SUIT_HELMET} and the rest). Wearing all four
 * as the Black Panther runs the suit-up (the texel build, the head last) and the keys switch to the suited kit; taking any piece
 * off runs it back and the keys go back to the unsuited kit ({@link #syncSuit}, every tick). There is no H suit-up, no necklace
 * toggle and no claw toggle any more: the claws are always out in the suit.
 *
 * <h2>Kinetic Energy</h2>
 * 0..100. The suit soaks 40% of every hit and stores it (1 damage = 4 Kinetic); it drains slowly out of combat. Shift+R spends
 * it; the bar lights the suit's trim purple as it fills.
 */
public final class BlackPanther {
	public static final String KEY = "black_panther";

	/** Legacy modifier ids (the claw toggle, the old suit armour): only ever cleared now. */
	private static final ResourceLocation MELEE_ID = PowerToggles.id("black_panther_melee");
	private static final ResourceLocation ARMOR_ID = PowerToggles.id("black_panther_armor");
	private static final ResourceLocation TOUGHNESS_ID = PowerToggles.id("black_panther_toughness");
	private static final ResourceLocation ABSORB_ID = PowerToggles.id("black_panther_absorb");
	/** The herb's (and the suit's) melee: 5 a bare-handed hit out of the suit, 10 in it. */
	private static final ResourceLocation HERB_ID = PowerToggles.id("black_panther_herb");

	public static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.62f, 0.25f, 1.0f), 1.2f);
	public static final DustParticleOptions PURPLE_BIG = new DustParticleOptions(new Vector3f(0.72f, 0.4f, 1.0f), 2.2f);
	public static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.65f, 0.05f, 0.08f), 0.9f);

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	/** v0.17.4 Night Eyes: who has the darkness night vision on right now (it switches with a gap, so it never flickers). */
	private static final Map<UUID, Boolean> NIGHT_EYES = new ConcurrentHashMap<>();

	private BlackPanther() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
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

	/** v0.17.7 (user: "claws should always be active"): the vibranium claws are out whenever the suit is on. */
	public static boolean clawsOut(Player player) {
		return suited(player);
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
		s.v174 = true;
		s.v177 = true; // v0.17.7: a new Black Panther crafts the suit and wears it
		save(player, s);
		syncSuit(player); // already wearing the suit: it runs out at once
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
		BlackPantherAbilities.clear(player);
		BlackPantherUnsuited.clear(player);
		save(player, new BlackPantherState());
		reconcile(player);
		PowerToggles.clearEffect(player, MobEffects.MOVEMENT_SPEED);
		PowerToggles.clearEffect(player, MobEffects.JUMP);
		PowerToggles.clearEffect(player, MobEffects.NIGHT_VISION);
		PowerToggles.clearEffect(player, MobEffects.REGENERATION);
		forget(player.getUUID());
	}

	private static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		NIGHT_EYES.remove(id);
		BlackPantherUnsuited.forget(id);
		BlackPantherAbilityManager.forget(id);
		BlackPantherDamage.forget(id);
	}

	/** Death, a new world, leaving: the running moves end. The power and the bar stay. */
	public static void clearTransient(ServerPlayer player) {
		BlackPantherAbilities.clear(player);
		BlackPantherUnsuited.clear(player);
	}

	public static void onPlayerJoin(ServerPlayer player) {
		migrate(player);
		syncSuit(player);
		reconcile(player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		syncSuit(player);
		reconcile(player);
	}

	/**
	 * v0.17.7: the suit became four wearable armour pieces. A Black Panther who already had the suit (the v0.17.4-0.17.6 Panther
	 * Habit in the necklace, or a 0.17.3 power, which came with it) is handed the four pieces once -- worn straight away if he
	 * had the suit on and the slot is free, otherwise into the inventory (dropped at his feet if it is full). The old necklace and
	 * claw toggles are cleared.
	 */
	static void migrate(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower || s.v177) {
			return;
		}
		boolean hadSuit = s.habit || !s.v174;
		boolean wasSuited = s.suited;
		BlackPantherState n = s.copy();
		n.v174 = true;
		n.v177 = true;
		n.habit = false;
		n.necklaceOff = false;
		n.clawsOut = false;
		save(player, n);
		if (hadSuit) {
			giveSuit(player, wasSuited);
		}
	}

	/** The four suit pieces into the armour slots ({@code wear}, where free) or the inventory. */
	static void giveSuit(ServerPlayer player, boolean wear) {
		Item[] pieces = { BlackPantherItems.SUIT_HELMET, BlackPantherItems.SUIT_CHESTPLATE, BlackPantherItems.SUIT_LEGGINGS, BlackPantherItems.SUIT_BOOTS };
		EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
		for (int i = 0; i < pieces.length; i++) {
			ItemStack stack = new ItemStack(pieces[i]);
			if (wear && player.getItemBySlot(slots[i]).isEmpty()) {
				player.setItemSlot(slots[i], stack);
			} else if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
		}
	}

	// ---------------------------------------------------------------- the suit (worn)

	/** All four Black Panther suit pieces are worn (anyone may wear them; only the Black Panther gets the moveset). */
	public static boolean wearsFullSuit(Player player) {
		return player.getItemBySlot(EquipmentSlot.HEAD).is(BlackPantherItems.SUIT_HELMET)
				&& player.getItemBySlot(EquipmentSlot.CHEST).is(BlackPantherItems.SUIT_CHESTPLATE)
				&& player.getItemBySlot(EquipmentSlot.LEGS).is(BlackPantherItems.SUIT_LEGGINGS)
				&& player.getItemBySlot(EquipmentSlot.FEET).is(BlackPantherItems.SUIT_BOOTS);
	}

	/** v0.17.7: the suited kit follows the worn suit -- the full set on runs the suit-up, any piece off runs it back. */
	public static void syncSuit(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower) {
			return;
		}
		boolean wearing = wearsFullSuit(player) && !player.isSpectator();
		if (wearing && !s.suited) {
			suitUp(player);
		} else if (!wearing && s.suited) {
			suitDown(player);
		}
	}

	/** The suit is still running out over him. */
	public static boolean changing(Player player) {
		BlackPantherState s = peek(player);
		if (s == null || !s.hasPower || !s.suited) {
			return false;
		}
		long age = player.level().getGameTime() - s.suitChangeAt;
		return age >= 0 && age < BlackPantherConfig.SUIT_UP_TICKS;
	}

	/** The suited kit takes over (called by {@link #syncSuit} once the full suit is worn). */
	public static void suitUp(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower || s.suited) {
			return;
		}
		// the unsuited kit's running moves and toggles end -- the suited kit takes the keys
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
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.4, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 1.4f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.8f);
		Vec3 neck = necklacePos(player);
		level.sendParticles(PURPLE, neck.x, neck.y, neck.z, 14, 0.22, 0.12, 0.22, 0.0);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, neck.x, neck.y, neck.z, 6, 0.2, 0.1, 0.2, 0.0);
	}

	/** Where the necklace hangs: the collar, a little in front of the chest. */
	static Vec3 necklacePos(ServerPlayer player) {
		Vec3 f = Vec3.directionFromRotation(0f, player.yBodyRot);
		return player.position().add(f.x * 0.2, player.getBbHeight() * 0.78, f.z * 0.2);
	}

	/** Back to the unsuited kit (called by {@link #syncSuit} once a piece comes off). */
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
		n.unmasked = false;
		n.animId = BlackPantherState.ANIM_SUIT_DOWN;
		n.animStart = now;
		save(player, n);
		reconcile(player);
		nightEyes(player, n); // v0.17.5: in the dark the herb's Night Eyes carries straight on, no blink
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.4, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.6f);
	}

	/** Shift+N: the mask off or back on. Suited only. */
	public static void toggleMask(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
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
		// legacy: the claw toggle's +5 and the old suit's armour now live in the herb modifier and the worn suit pieces
		PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID);
		PowerToggles.clearModifier(player, Attributes.ARMOR, ARMOR_ID);
		PowerToggles.clearModifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID);
		BlackPantherState st = peek(player);
		boolean power = st != null && st.hasPower;
		boolean suited = power && st.suited;
		if (power) {
			// v0.17.7 (user): unsuited melee 5, suited 10 (the bare fist's 1 is already there)
			double melee = (suited ? BlackPantherConfig.SUITED_MELEE : BlackPantherConfig.UNSUITED_MELEE) - 1.0;
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, HERB_ID, melee, AttributeModifier.Operation.ADD_VALUE);
			// v0.17.7 (user): Regeneration I without the suit, II with it (our own endless II is taken off first: vanilla never
			// lowers an effect's level by adding the weaker one; a timed buff such as Ancestral Resolve is left alone)
			int regen = suited ? BlackPantherConfig.REGEN_SUITED : BlackPantherConfig.REGEN_UNSUITED;
			net.minecraft.world.effect.MobEffectInstance cur = player.getEffect(MobEffects.REGENERATION);
			if (cur != null && cur.isInfiniteDuration() && cur.getAmplifier() > regen) {
				player.removeEffect(MobEffects.REGENERATION);
			}
			PowerToggles.effect(player, MobEffects.REGENERATION, regen, false);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, HERB_ID);
		}
		if (power && st.absorbing) { // Absorb Stance in the suit, Block out of it: he moves slowly while it is up
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, ABSORB_ID, suited ? BlackPantherConfig.ABSORB_SLOW : BlackPantherConfig.BLOCK_SLOW,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, ABSORB_ID);
		}
	}

	static boolean canAct(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
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

	/** v0.17.4: may the unsuited kit (T'Challa without the suit) be used right now? */
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
		if (!s.v177) {
			migrate(player);
		}
		syncSuit(player);
		s = state(player);
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
		if (player.tickCount % 40 == 0) {
			// the herb: Speed I and Jump Boost I, suit on or off
			PowerToggles.effect(player, MobEffects.MOVEMENT_SPEED, 0, false);
			PowerToggles.effect(player, MobEffects.JUMP, 0, false);
		}
		if (player.tickCount % 10 == 0) {
			nightEyes(player, s);
		}
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
}
