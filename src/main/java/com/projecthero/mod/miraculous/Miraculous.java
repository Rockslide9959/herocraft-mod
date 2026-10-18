package com.projecthero.mod.miraculous;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.entity.KwamiEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: the Miraculous core -- one API for all three Kwamis (Tikki / Ladybug, Plagg / Cat Noir, Trixx / Rena Rouge).
 *
 * <h2>For the kits</h2>
 * Read with {@link #state} / {@link #peek}; change only through {@link #edit} (copy, change, save). Cooldowns:
 * {@link #ready} + {@link #setCooldown}. Poses: {@link #setAnim}. Kit-owned synced values: {@link #setVal} /
 * {@link #setVec} (cleared by the core on detransformation). Shift+Z: {@link #startCountdown} when the special fires.
 *
 * <h2>The core</h2>
 * grant / revoke, the spoken phrases ({@link #onChat}; {@link #toggleForm} for commands), the transformation sequence, the countdown and the hungry Kwami, the Kwami
 * entity, the shared transformed stats, the lifecycle. See {@code docs/MIRACULOUS_REFERENCE.md}.
 */
public final class Miraculous {
	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private Miraculous() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		MiraculousAbilityManager.clearSessionState();
		MiraculousKits.clearSessionState();
		KwamiCompanion.clearSessionState();
	}

	// ---------------------------------------------------------------- state

	public static MiraculousState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.MIRACULOUS_STATE);
	}

	/** Client-safe read (null if never attached). */
	public static @Nullable MiraculousState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.MIRACULOUS_STATE, null);
	}

	public static void save(ServerPlayer player, MiraculousState state) {
		player.setAttached(ModAttachments.MIRACULOUS_STATE, state);
	}

	/** Copy, change, save. */
	public static void edit(ServerPlayer player, Consumer<MiraculousState> change) {
		MiraculousState n = state(player).copy();
		change.accept(n);
		save(player, n);
	}

	/** Client-safe: holds any Kwami. */
	public static boolean hasPower(Player player) {
		MiraculousState s = peek(player);
		return s != null && s.hasPower && Kwami.byId(s.kwami) != null;
	}

	/** Client-safe: which Kwami (null without the power). */
	public static @Nullable Kwami kwami(Player player) {
		MiraculousState s = peek(player);
		return s == null || !s.hasPower ? null : Kwami.byId(s.kwami);
	}

	/** Client-safe: holds this particular Kwami. */
	public static boolean holds(Player player, Kwami kwami) {
		return kwami(player) == kwami;
	}

	/** Client-safe: transformed. */
	public static boolean transformed(Player player) {
		MiraculousState s = peek(player);
		return s != null && s.hasPower && s.transformed;
	}

	/** Client-safe: transformed as this Kwami's hero. */
	public static boolean transformedAs(Player player, Kwami kwami) {
		return transformed(player) && holds(player, kwami);
	}

	/** Client-safe: ticks until a move is ready (0 = ready). */
	public static int cooldownRemaining(Player player, String abilityId) {
		MiraculousState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	public static boolean ready(ServerPlayer player, String abilityId) {
		return cooldownRemaining(player, abilityId) <= 0;
	}

	public static void setCooldown(ServerPlayer player, String abilityId, int ticks) {
		long at = player.level().getGameTime() + ticks;
		edit(player, n -> n.abilityReadyAt.put(abilityId, at));
	}

	public static void setAnim(ServerPlayer player, int animId) {
		long now = player.level().getGameTime();
		edit(player, n -> {
			n.animId = animId;
			n.animStart = now;
		});
	}

	public static void setVal(ServerPlayer player, String key, long value) {
		edit(player, n -> {
			if (value == 0L) {
				n.vals.remove(key);
			} else {
				n.vals.put(key, value);
			}
		});
	}

	public static void setVec(ServerPlayer player, String key, @Nullable Vec3 v) {
		edit(player, n -> {
			if (v == null) {
				n.vecs.remove(key);
			} else {
				n.vecs.put(key, List.of(v.x, v.y, v.z));
			}
		});
	}

	/** Client-safe helper for {@link MiraculousState#vec}: the 3-vector, or null. */
	public static @Nullable Vec3 vec(MiraculousState s, String key) {
		List<Double> v = s.vec(key);
		return v.size() < 3 ? null : new Vec3(v.get(0), v.get(1), v.get(2));
	}

	/** Shift+Z fired: the 5-minute countdown to the forced detransformation starts (no-op if one is already running). */
	public static void startCountdown(ServerPlayer player) {
		MiraculousState s = state(player);
		if (!s.transformed || s.countdownEnd > 0L) {
			return;
		}
		long end = player.level().getGameTime() + MiraculousConfig.COUNTDOWN_TICKS;
		edit(player, n -> n.countdownEnd = end);
	}

	/** Client-safe: ticks left on the countdown (0 = none running). */
	public static int countdownLeft(Player player) {
		MiraculousState s = peek(player);
		if (s == null || s.countdownEnd <= 0L) {
			return 0;
		}
		return (int) Math.max(0L, s.countdownEnd - player.level().getGameTime());
	}

	/** Transformed, done transforming, alive: the kits' moves may run. */
	public static boolean canAct(ServerPlayer player) {
		MiraculousState s = state(player);
		if (!s.hasPower || !s.transformed || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		return player.level().getGameTime() - s.formChangeAt >= MiraculousConfig.TRANSFORM_TICKS;
	}

	/** Action-bar message, rate limited. */
	public static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		long now = player.level().getGameTime();
		Long last = LAST_MESSAGE.get(player.getUUID());
		if (last != null && now - last < 10 && now >= last) {
			return;
		}
		LAST_MESSAGE.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable(key, args).withStyle(colour), true);
	}

	// ---------------------------------------------------------------- the Kwami's food

	/** The Kwami's favourite food: cookies (Tikki), Camembert (Plagg), macarons (Trixx), a honey bottle (Nooroo), dried kelp (Wayzz), sugar (Kaalki). */
	public static net.minecraft.world.item.Item foodOf(Kwami kwami) {
		return switch (kwami) {
			case TIKKI -> net.minecraft.world.item.Items.COOKIE;
			case PLAGG -> com.projecthero.mod.miraculous.item.MiraculousItems.CAMEMBERT;
			case TRIXX -> com.projecthero.mod.miraculous.item.MiraculousItems.MACARON;
			case NOOROO -> net.minecraft.world.item.Items.HONEY_BOTTLE;
			case WAYZZ -> net.minecraft.world.item.Items.DRIED_KELP;
			case KAALKI -> net.minecraft.world.item.Items.SUGAR;
		};
	}

	/** Client-safe: the Kwami is worn out and must be fed before the next transformation. */
	public static boolean hungry(Player player) {
		MiraculousState s = peek(player);
		return s != null && s.hasPower && s.hungry;
	}

	/**
	 * Feeds the Kwami one of {@code stack} (right-clicking the Kwami with it, or eating it while the Kwami is hungry). Only
	 * its favourite food, only while detransformed, not while it is still eating. Hungry: that clears it (the next
	 * transformation is allowed again). Not hungry: it eats anyway and the holder gets a little Regeneration.
	 */
	public static boolean feed(ServerPlayer player, ItemStack stack) {
		MiraculousState s = state(player);
		Kwami k = kwami(player);
		if (k == null || stack.isEmpty()) {
			return false;
		}
		boolean favourite = stack.is(foodOf(k));
		// v0.16.6 (user): 10 pieces of any other food will do instead of the favourite
		if (!favourite && !isSubstituteFood(stack)) {
			return false;
		}
		if (s.transformed) {
			say(player, "message.projecthero.miraculous.feed_transformed", ChatFormatting.GOLD, kwamiName(k));
			return false;
		}
		long now = player.level().getGameTime();
		if (s.val(V_EAT_UNTIL) > now) {
			say(player, "message.projecthero.miraculous.still_eating", ChatFormatting.GOLD, kwamiName(k),
					(int) Math.ceil((s.val(V_EAT_UNTIL) - now) / 20.0));
			return false;
		}
		KwamiEntity e = KwamiCompanion.ensure(player);
		if (!favourite) {
			if (!s.hungry) {
				say(player, "message.projecthero.miraculous.not_hungry_substitute", ChatFormatting.GRAY, kwamiName(k));
				return false;
			}
			// collected a piece at a time across feeds (a stack hands over as much as is still needed)
			int have = (int) s.val(V_SUB_FOOD);
			int take = player.getAbilities().instabuild ? MiraculousConfig.SUBSTITUTE_FOOD_COUNT - have
					: Math.min(stack.getCount(), MiraculousConfig.SUBSTITUTE_FOOD_COUNT - have);
			consumeFood(player, stack, take);
			int now2 = have + take;
			edit(player, n -> n.vals.put(V_SUB_FOOD, (long) now2));
			if (e != null) {
				player.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5f, 1.3f);
			}
			if (now2 < MiraculousConfig.SUBSTITUTE_FOOD_COUNT) {
				say(player, "message.projecthero.miraculous.substitute_progress", ChatFormatting.YELLOW, kwamiName(k), now2,
						MiraculousConfig.SUBSTITUTE_FOOD_COUNT, Component.translatable(foodOf(k).getDescriptionId()));
				return true;
			}
		} else {
			consumeFood(player, stack, 1);
		}
		// it eats for 10 s (v0.16.6, user); a hungry Kwami is only fed (and the next transformation allowed) at the end
		long until = now + MiraculousConfig.EAT_TICKS;
		boolean wasHungry = s.hungry;
		edit(player, n -> {
			n.vals.put(V_EAT_UNTIL, until);
			n.vals.put(V_EAT_FEEDS, wasHungry ? 1L : 0L);
			n.vals.remove(V_SUB_FOOD);
		});
		if (e != null) {
			e.setMode(KwamiEntity.MODE_EAT, MiraculousConfig.EAT_TICKS);
			player.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.6f, 1.5f);
		}
		say(player, "message.projecthero.miraculous.eating", ChatFormatting.GREEN, kwamiName(k), MiraculousConfig.EAT_TICKS / 20);
		return true;
	}

	/** Ends a meal in progress right now (tests, the operator feed command). False if the Kwami was not eating. */
	public static boolean finishMeal(ServerPlayer player) {
		MiraculousState s = state(player);
		Kwami k = Kwami.byId(s.kwami);
		if (k == null || s.val(V_EAT_UNTIL) <= 0L) {
			return false;
		}
		finishEating(player, s, k);
		return true;
	}

	/** The meal is over: a hungry Kwami is fed; a snack gives the holder a little Regeneration. */
	private static void finishEating(ServerPlayer player, MiraculousState s, Kwami k) {
		boolean feeds = s.val(V_EAT_FEEDS) != 0L;
		edit(player, n -> {
			n.vals.remove(V_EAT_UNTIL);
			n.vals.remove(V_EAT_FEEDS);
			if (feeds) {
				n.hungry = false;
			}
		});
		if (feeds) {
			KwamiCompanion.say(player, "message.projecthero.miraculous.fed." + k.id);
		} else {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MiraculousConfig.SNACK_REGEN_TICKS, 0, false, true));
			say(player, "message.projecthero.miraculous.snack", ChatFormatting.GREEN, kwamiName(k));
		}
	}

	private static void consumeFood(ServerPlayer player, ItemStack stack, int count) {
		if (player.getAbilities().instabuild || count <= 0) {
			return;
		}
		ItemStack left = stack.getItem().getCraftingRemainingItem() == null ? ItemStack.EMPTY
				: new ItemStack(stack.getItem().getCraftingRemainingItem(), count);
		if (stack.is(net.minecraft.world.item.Items.HONEY_BOTTLE)) {
			left = new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE, count); // Nooroo hands the bottle back
		}
		stack.shrink(count);
		if (!left.isEmpty() && !player.getInventory().add(left)) {
			player.drop(left, false);
		}
	}

	/** v0.16.6: anything edible that is not the favourite counts toward the 10-piece substitute meal. */
	public static boolean isSubstituteFood(ItemStack stack) {
		return stack.has(net.minecraft.core.component.DataComponents.FOOD);
	}

	/** Client-safe: seconds left of the Kwami's meal (0 = not eating). */
	public static int eatingSecondsLeft(Player player) {
		MiraculousState s = peek(player);
		if (s == null) {
			return 0;
		}
		long left = s.val(V_EAT_UNTIL) - player.level().getGameTime();
		return left <= 0L ? 0 : (int) Math.ceil(left / 20.0);
	}

	/** Core-owned keys in {@link MiraculousState#vals} (survive until the meal is over; detransforming can't happen meanwhile). */
	public static final String V_EAT_UNTIL = "core_eat_until";
	public static final String V_EAT_FEEDS = "core_eat_feeds";
	public static final String V_SUB_FOOD = "core_sub_food";

	/**
	 * {@code UseItemCallback}: eating the Kwami's FAVOURITE food while it is hungry and you are detransformed feeds it
	 * instead (other food is eaten normally; hand it to the Kwami by right-clicking it).
	 */
	public static boolean wantsFood(Player player, ItemStack stack) {
		Kwami k = kwami(player);
		return k != null && hungry(player) && !transformed(player) && stack.is(foodOf(k));
	}

	public static Component kwamiName(Kwami k) {
		return Component.translatable("entity.projecthero.kwami." + k.id);
	}

	// ---------------------------------------------------------------- grant / revoke

	/** A jewel / the command. False if refused (already holds this Kwami, UniquePowers). */
	public static boolean grant(ServerPlayer player, Kwami kwami) {
		MiraculousState s = state(player);
		if (s.hasPower && kwami.id.equals(s.kwami)) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, kwami.heroKey)) {
			return false;
		}
		if (s.hasPower) {
			revoke(player);
		}
		HeroTiers.claimPrimary(player, kwami.heroKey);
		MiraculousState n = new MiraculousState();
		n.hasPower = true;
		n.kwami = kwami.id;
		save(player, n);
		MiraculousStats.reconcile(player);
		KwamiCompanion.introduce(player, kwami);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.3f);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ALLAY_AMBIENT_WITH_ITEM, SoundSource.PLAYERS, 0.8f, 1.2f);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		MiraculousState s = state(player);
		if (s.transformed) {
			MiraculousKits.of(Kwami.byId(s.kwami)).onDetransform(player);
		}
		KwamiCompanion.remove(player);
		save(player, new MiraculousState());
		MiraculousStats.reconcile(player);
		forget(player);
		KwamiCompanion.forget(player.getUUID());
	}

	private static void forget(ServerPlayer player) {
		UUID id = player.getUUID();
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		MiraculousAbilityManager.forget(id);
		for (Kwami k : Kwami.values()) {
			MiraculousKits.of(k).forget(id);
		}
	}

	// ---------------------------------------------------------------- transforming

	/**
	 * Transform / detransform (the payload, commands and tests; players SAY the phrases, see {@link #onChat}). Debounced
	 * by {@link MiraculousConfig#TOGGLE_COOLDOWN}; a transformation can't be undone before it has finished.
	 */
	public static void toggleForm(ServerPlayer player) {
		MiraculousState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator() || settling(player, s)) {
			return;
		}
		if (s.transformed) {
			detransform(player, false);
		} else {
			transform(player);
		}
	}

	/** Too soon after the last change. */
	private static boolean settling(ServerPlayer player, MiraculousState s) {
		long since = player.level().getGameTime() - s.formChangeAt;
		return s.formChangeAt > 0L && since >= 0L && since < (s.transformed ? MiraculousConfig.TRANSFORM_TICKS : MiraculousConfig.TOGGLE_COOLDOWN);
	}

	/**
	 * v0.16.6 (user): the spoken phrase ({@code ServerMessageEvents.CHAT_MESSAGE}). True if it was one of the holder's
	 * phrases (the chat line still goes out either way).
	 */
	public static boolean onChat(ServerPlayer player, String message) {
		Kwami k = kwami(player);
		if (k == null) {
			return false;
		}
		MiraculousPhrases.Spoken spoken = MiraculousPhrases.match(k, message);
		if (spoken == null) {
			return false;
		}
		MiraculousState s = state(player);
		if (settling(player, s)) {
			return true;
		}
		if (spoken == MiraculousPhrases.Spoken.TRANSFORM && !s.transformed) {
			transform(player);
		} else if (spoken == MiraculousPhrases.Spoken.DETRANSFORM && s.transformed) {
			detransform(player, false);
		}
		return true;
	}

	/**
	 * v0.16.6 (user): N -- tuck the Kwami away (it ducks into a pocket: gone from the world, no following) or call it back
	 * out. Detransformed only (transformed, it is inside the jewel). A hidden Kwami blocks transforming until called out.
	 */
	public static void toggleKwamiHidden(ServerPlayer player) {
		MiraculousState s = state(player);
		Kwami k = Kwami.byId(s.kwami);
		if (!s.hasPower || k == null || !player.isAlive() || player.isSpectator()) {
			return;
		}
		if (s.transformed) {
			say(player, "message.projecthero.miraculous.kwami_in_jewel", ChatFormatting.GRAY, kwamiName(k));
			return;
		}
		boolean hide = !s.kwamiHidden;
		KwamiEntity e = KwamiCompanion.find(player);
		if (hide && e != null) {
			((net.minecraft.server.level.ServerLevel) player.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
					e.getX(), e.getY() + 0.3, e.getZ(), 8, 0.12, 0.12, 0.12, 0.01);
		}
		edit(player, n -> n.kwamiHidden = hide);
		if (hide) {
			KwamiCompanion.remove(player);
			sound(player, SoundEvents.BUNDLE_INSERT, 0.8f, 1.4f);
		} else {
			KwamiCompanion.mood(player, KwamiEntity.MODE_POP_OUT, MiraculousConfig.POP_OUT_TICKS / 2);
			sound(player, SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 1.3f);
		}
		say(player, hide ? "message.projecthero.miraculous.kwami_tucked" : "message.projecthero.miraculous.kwami_out", ChatFormatting.LIGHT_PURPLE, kwamiName(k));
	}

	public static void transform(ServerPlayer player) {
		MiraculousState s = state(player);
		if (!s.hasPower || s.transformed || !player.isAlive() || player.isSpectator() || settling(player, s)) {
			return;
		}
		Kwami k = Kwami.byId(s.kwami);
		if (k == null) {
			return;
		}
		if (s.kwamiHidden) {
			// v0.16.6 (user): only with the Kwami out
			say(player, "message.projecthero.miraculous.kwami_hidden", ChatFormatting.GOLD, kwamiName(k));
			return;
		}
		if (s.val(V_EAT_UNTIL) > player.level().getGameTime()) {
			// v0.16.6 (user): a meal takes 10 s
			say(player, "message.projecthero.miraculous.still_eating", ChatFormatting.GOLD, kwamiName(k), eatingSecondsLeft(player));
			return;
		}
		if (s.hungry) {
			say(player, "message.projecthero.miraculous.hungry", ChatFormatting.GOLD, kwamiName(k),
					Component.translatable(foodOf(k).getDescriptionId()));
			return;
		}
		long now = player.level().getGameTime();
		edit(player, n -> {
			n.transformed = true;
			n.formChangeAt = now;
			n.animId = MiraculousState.ANIM_TRANSFORM;
			n.animStart = now;
		});
		MiraculousStats.reconcile(player);
		KwamiCompanion.mood(player, KwamiEntity.MODE_INTO_JEWEL, MiraculousConfig.INTO_JEWEL_TICKS);
		sound(player, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
		sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.5f);
		sound(player, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.4f, 1.6f);
	}

	/**
	 * {@code exhausted}: the countdown ran out. v0.16.6 (user): after EVERY detransformation the Kwami comes out tired and
	 * hungry, and won't transform the holder again until it has been fed.
	 */
	public static void detransform(ServerPlayer player, boolean exhausted) {
		MiraculousState s = state(player);
		if (!s.transformed) {
			return;
		}
		MiraculousKits.of(Kwami.byId(s.kwami)).onDetransform(player);
		long now = player.level().getGameTime();
		edit(player, n -> {
			n.transformed = false;
			n.formChangeAt = now;
			n.countdownEnd = 0L;
			n.hungry = true;
			n.animId = MiraculousState.ANIM_DETRANSFORM;
			n.animStart = now;
			n.vals.clear();
			n.vecs.clear();
		});
		MiraculousStats.reconcile(player);
		KwamiCompanion.mood(player, KwamiEntity.MODE_POP_TIRED, MiraculousConfig.POP_TIRED_TICKS);
		sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.9f);
		sound(player, SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.7f);
		Kwami k = Kwami.byId(s.kwami);
		if (k != null && !exhausted) {
			KwamiCompanion.say(player, "message.projecthero.miraculous.tired." + k.id);
		}
	}

	private static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static void sound(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		MiraculousState s = player.getAttachedOrElse(ModAttachments.MIRACULOUS_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		if (player.tickCount % 20 == 0) {
			MiraculousStats.reconcile(player);
		}
		// v0.16.6: the end of a meal
		long eatUntil = s.val(V_EAT_UNTIL);
		if (eatUntil > 0L && now >= eatUntil) {
			Kwami eating = Kwami.byId(s.kwami);
			if (eating != null) {
				finishEating(player, s, eating);
			}
			s = state(player);
		}
		if (s.transformed) {
			long age = now - s.formChangeAt;
			if (age == MiraculousConfig.SWIRL_SOUND_TICK) {
				sound(player, SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.6f);
			} else if (age == MiraculousConfig.FLASH_TICK) {
				sound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
				sound(player, SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.9f, 1.2f);
				sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.8f);
			}
			if (s.countdownEnd > 0L) {
				long left = s.countdownEnd - now;
				if (left > 0L && left < MiraculousConfig.COUNTDOWN_TICKS && left % 1200L == 0L) {
					// a soft beep for the holder at each minute mark (the jewel loses a spot)
					player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
				} else if (left > 0L && left <= 200L && left % 20L == 0L) {
					player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.45f, 1.5f);
				}
			}
		}
		if (s.transformed && s.countdownEnd > 0L && now >= s.countdownEnd) {
			detransform(player, true);
			say(player, "message.projecthero.miraculous.time_up", ChatFormatting.RED);
			s = state(player);
		}
		KwamiCompanion.tick(player, s);
		MiraculousKits.of(Kwami.byId(s.kwami)).tick(player);
	}

	// ---------------------------------------------------------------- lifecycle

	public static void onPlayerJoin(ServerPlayer player) {
		MiraculousState s = state(player);
		if (!s.hasPower) {
			return;
		}
		edit(player, n -> {
			n.animId = MiraculousState.ANIM_NONE;
			n.formChangeAt = 0L;
		});
		MiraculousStats.reconcile(player);
	}

	/** v0.16.6 (user): dying transformed is a detransformation too -- the Kwami comes back hungry. */
	public static void onPlayerRespawn(ServerPlayer player) {
		MiraculousState s = state(player);
		if (!s.hasPower) {
			return;
		}
		edit(player, n -> {
			n.hungry = n.hungry || n.transformed;
			n.transformed = false;
			n.formChangeAt = 0L;
			n.countdownEnd = 0L;
			n.animId = MiraculousState.ANIM_NONE;
			n.abilityReadyAt.clear();
			n.vals.clear();
			n.vecs.clear();
		});
		MiraculousStats.reconcile(player);
	}

	/** Death / logout / dimension change: running moves end (the power and the form stay, except on death). */
	public static void clearTransient(ServerPlayer player) {
		MiraculousState s = player.getAttachedOrElse(ModAttachments.MIRACULOUS_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		if (s.transformed) {
			MiraculousKits.of(Kwami.byId(s.kwami)).onDetransform(player);
		}
		if (!s.vals.isEmpty() || !s.vecs.isEmpty()) {
			edit(player, n -> {
				// the kits' values go; the core's own (a meal in progress, substitute food handed over) stay
				n.vals.keySet().removeIf(key -> !key.startsWith("core_"));
				n.vecs.clear();
			});
		}
		KwamiCompanion.remove(player);
		forget(player);
	}
}
