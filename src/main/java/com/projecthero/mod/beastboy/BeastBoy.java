package com.projecthero.mod.beastboy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.beastboy.data.BeastBoyState;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Beast Boy (Garfield Logan): the single server-side API of the power. Nothing else changes {@link BeastBoyState}. The
 * numbers are in {@link BeastBoyConfig}; per-form behaviour in {@link BeastBoyForms}; the moves are {@link BeastBoyMoves}'
 * (routed by {@link BeastBoyAbilityManager}).
 *
 * <ul>
 *   <li><b>Forms</b>: he is human or one {@link BeastForm}. The player's hitbox follows the form ({@code BeastBoyBodyMixin}).</li>
 *   <li><b>Form health</b>: in an animal form every hit comes off the form's own pool, never his hearts. When it's gone he's
 *   knocked back to human, dazed 1 s, and can't take that form again for 10 s.</li>
 *   <li><b>Morph Stamina</b> (Meter): forms drain it (big ones fast), human form refills it; at zero he turns back.</li>
 *   <li><b>Morph Chain</b>: each quick-morph hit with a different animal than the last adds +1 (max 5, +8% damage each);
 *   repeating an animal resets it.</li>
 *   <li><b>Instincts</b>: night vision always on.</li>
 * </ul>
 */
public final class BeastBoy {
	public static final String KEY = "beast_boy";
	public static final int GREEN = 0x58C83C;

	private static final ResourceLocation SPEED_ID = PowerToggles.id("beast_boy_speed");
	private static final ResourceLocation ATTACK_ID = PowerToggles.id("beast_boy_attack");
	private static final ResourceLocation STEP_ID = PowerToggles.id("beast_boy_step");
	private static final ResourceLocation SAFE_FALL_ID = PowerToggles.id("beast_boy_safe_fall");

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	/** The form pool's hurt window: the last hit's time and size. */
	private static final Map<UUID, float[]> POOL_HURT = new ConcurrentHashMap<>();
	/** Players whose flight this power switched on (so only we switch it off). */
	private static final java.util.Set<UUID> FLIGHT = ConcurrentHashMap.newKeySet();

	private BeastBoy() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		POOL_HURT.clear();
		FLIGHT.clear();
		BeastBoyAbilityManager.clearSessionState();
		BeastBoyMoves.clearSessionState();
		BeastBoyLearning.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		POOL_HURT.remove(id);
		BeastBoyAbilityManager.forget(id);
		BeastBoyMoves.forget(id);
	}

	// ---------------------------------------------------------------- state (client and server)

	public static BeastBoyState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.BEAST_BOY_STATE);
	}

	static void save(ServerPlayer player, BeastBoyState state) {
		player.setAttached(ModAttachments.BEAST_BOY_STATE, state);
	}

	public static BeastBoyState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.BEAST_BOY_STATE, null);
	}

	public static boolean hasPower(Player player) {
		BeastBoyState s = peek(player);
		return s != null && s.hasPower;
	}

	static long now(Player player) {
		return player.level().getGameTime();
	}

	/** The form he is in (HUMAN without the power). Client-safe. */
	public static BeastForm form(Player player) {
		BeastBoyState s = peek(player);
		return s == null || !s.hasPower ? BeastForm.HUMAN : BeastForm.byId(s.form);
	}

	/** In any animal form (the body is not his own). Client-safe. */
	public static boolean morphed(Player player) {
		return form(player) != BeastForm.HUMAN;
	}

	public static float stamina(Player player) {
		BeastBoyState s = peek(player);
		return s == null ? 0f : s.stamina;
	}

	public static float formHp(Player player) {
		BeastBoyState s = peek(player);
		return s == null ? 0f : s.formHp;
	}

	public static int chain(Player player) {
		BeastBoyState s = peek(player);
		if (s == null || !s.hasPower) {
			return 0;
		}
		return isBeast(player) ? BeastBoyConfig.CHAIN_MAX : s.chain;
	}

	public static boolean isBeast(Player player) {
		return form(player) == BeastForm.BEAST;
	}

	public static int beastTicksLeft(Player player) {
		BeastBoyState s = peek(player);
		return s == null || !isBeast(player) ? 0 : (int) Math.max(0L, s.timers.beastUntil() - now(player));
	}

	public static boolean dazed(Player player) {
		BeastBoyState s = peek(player);
		return s != null && s.hasPower && s.timers.dazedUntil() > now(player);
	}

	public static boolean sensing(Player player) {
		BeastBoyState s = peek(player);
		return s != null && s.hasPower && s.timers.sensesUntil() > now(player);
	}

	public static int sensesTicksLeft(Player player) {
		BeastBoyState s = peek(player);
		return s == null ? 0 : (int) Math.max(0L, s.timers.sensesUntil() - now(player));
	}

	/** In the Sakutia fever (before the power settles). Client-safe. */
	public static int feverTicksLeft(Player player) {
		BeastBoyState s = peek(player);
		return s == null ? 0 : (int) Math.max(0L, s.timers.feverUntil() - now(player));
	}

	public static int cooldownRemaining(Player player, String id) {
		BeastBoyState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.readyAt.get(id);
		return ready == null ? 0 : (int) Math.max(0L, ready - now(player));
	}

	/** Ticks a form he was knocked out of stays locked. Client-safe. */
	public static int formLockout(Player player, BeastForm form) {
		return cooldownRemaining(player, "form_" + form.name().toLowerCase(java.util.Locale.ROOT));
	}

	static void startCooldown(ServerPlayer player, String id, int ticks) {
		BeastBoyState n = state(player).copy();
		n.readyAt.put(id, now(player) + ticks);
		save(player, n);
	}

	public static void clearCooldowns(ServerPlayer player) {
		BeastBoyState n = state(player).copy();
		n.readyAt.clear();
		save(player, n);
	}

	/** The animating move right now and its progress 0..1 (null when none). Client-safe. */
	public static String move(Player player) {
		BeastBoyState s = peek(player);
		if (s == null || s.anim.move().isEmpty() || s.anim.moveTicks() <= 0) {
			return null;
		}
		long e = now(player) - s.anim.moveStart();
		return e >= 0 && e < s.anim.moveTicks() ? s.anim.move() : null;
	}

	public static float moveProgress(Player player, float partial) {
		BeastBoyState s = peek(player);
		if (s == null || s.anim.moveTicks() <= 0) {
			return 0f;
		}
		return Math.max(0f, Math.min(1f, (now(player) - s.anim.moveStart() + partial) / s.anim.moveTicks()));
	}

	/** Morph round: the form he left in the last form change (null when none). Client-safe. */
	public static BeastForm morphFrom(Player player) {
		BeastBoyState s = peek(player);
		return s == null || !s.hasPower || s.morph.from() < 0 ? null : BeastForm.byId(s.morph.from());
	}

	/** Morph round: game ticks (with the partial tick) since the last form change; -1 when there was none. Client-safe. */
	public static float morphAge(Player player, float partial) {
		BeastBoyState s = peek(player);
		if (s == null || !s.hasPower || s.morph.from() < 0 || s.morph.at() <= 0L) {
			return -1f;
		}
		return now(player) - s.morph.at() + partial;
	}

	/** The human-form quick morph flashing right now (null when none). Client-safe. */
	public static BeastForm quickForm(Player player) {
		BeastBoyState s = peek(player);
		if (s == null || !s.hasPower || s.anim.quickTicks() <= 0) {
			return null;
		}
		long e = now(player) - s.anim.quickStart();
		return e >= 0 && e < s.anim.quickTicks() ? BeastForm.byId(s.anim.quickForm()) : null;
	}

	public static float quickProgress(Player player, float partial) {
		BeastBoyState s = peek(player);
		if (s == null || s.anim.quickTicks() <= 0) {
			return 0f;
		}
		return Math.max(0f, Math.min(1f, (now(player) - s.anim.quickStart() + partial) / s.anim.quickTicks()));
	}

	static void animate(ServerPlayer player, String move, int ticks) {
		BeastBoyState n = state(player).copy();
		n.anim = new BeastBoyState.Anim(n.anim.quickForm(), n.anim.quickStart(), n.anim.quickTicks(), move, now(player), ticks);
		save(player, n);
	}

	/**
	 * Morph round: the body-reshaping sound under every morph (layered vanilla: a wet stretch and a soft whoosh), pitched by the size of
	 * the new body (big = lower).
	 */
	static void morphSound(ServerPlayer player, BeastForm form) {
		float size = Math.max(form.width, form.height);
		float pitch = (float) Math.max(0.6, Math.min(1.7, 1.45 - Math.log(Math.max(0.2, size) / 1.8) * 0.35));
		sound(player, SoundEvents.HONEY_BLOCK_SLIDE, 0.55f, pitch);
		sound(player, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.25f, pitch * 1.15f);
	}

	static void flashQuick(ServerPlayer player, BeastForm form) {
		BeastBoyState n = state(player).copy();
		n.anim = new BeastBoyState.Anim(form.ordinal(), now(player), BeastBoyConfig.QUICK_TICKS, n.anim.move(), n.anim.moveStart(),
				n.anim.moveTicks());
		save(player, n);
		morphSound(player, form);
	}

	static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		long now = now(player);
		Long last = LAST_MESSAGE.get(player.getUUID());
		if (last != null && now - last < 10 && now >= last) {
			return;
		}
		LAST_MESSAGE.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable(key, args).withStyle(colour), true);
	}

	static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	static DustParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), size);
	}

	static void puff(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, player.getBbHeight() * 0.5, 0);
		double w = Math.max(0.3, player.getBbWidth() * 0.6);
		level.sendParticles(dust(GREEN, 1.2f), c.x, c.y, c.z, 24, w, player.getBbHeight() * 0.4, w, 0.0);
	}

	// ---------------------------------------------------------------- stamina and the chain

	/** Spends Morph Stamina if there is enough. False ("Not enough stamina") if not. Creative never pays. */
	public static boolean spendStamina(ServerPlayer player, float amount) {
		BeastBoyState s = state(player);
		if (!s.hasPower) {
			return false;
		}
		if (player.getAbilities().instabuild || amount <= 0f) {
			return true;
		}
		if (s.stamina + 1.0e-3f < amount) {
			say(player, "message.projecthero.beast_boy.no_stamina", ChatFormatting.GRAY);
			return false;
		}
		BeastBoyState n = s.copy();
		n.stamina = Math.max(0f, s.stamina - amount);
		save(player, n);
		return true;
	}

	public static void setStamina(ServerPlayer player, float value) {
		BeastBoyState n = state(player).copy();
		n.stamina = Math.max(0f, Math.min(BeastBoyConfig.STAMINA_MAX, value));
		save(player, n);
	}

	/** The chain's damage multiplier at {@code chain} links. */
	public static float chainMult(int chain) {
		return 1f + BeastBoyConfig.CHAIN_DAMAGE_PER * Math.max(0, Math.min(BeastBoyConfig.CHAIN_MAX, chain));
	}

	/**
	 * A quick-morph hit with {@code animal}: a different animal than last time adds a link (max 5); the same one resets the chain.
	 * Returns the damage multiplier for this hit (the chain after it). The Beast's chain is locked at 5.
	 */
	public static float chainHit(ServerPlayer player, BeastForm animal) {
		if (isBeast(player)) {
			return chainMult(BeastBoyConfig.CHAIN_MAX);
		}
		BeastBoyState n = state(player).copy();
		if (n.chainForm == animal.ordinal()) {
			n.chain = 0;
		} else if (n.chainForm < 0) {
			n.chain = 0; // the first animal starts the chain; the next different one is the first link
		} else {
			n.chain = Math.min(BeastBoyConfig.CHAIN_MAX, n.chain + 1);
		}
		n.chainForm = animal.ordinal();
		n.timers = n.timers.withChainAt(now(player));
		save(player, n);
		return chainMult(n.chain);
	}

	// ---------------------------------------------------------------- hits

	/** A Beast Boy move hits {@code target} for {@code amount}. False when it can't be harmed or nothing landed. */
	public static boolean hit(ServerPlayer player, LivingEntity target, float amount) {
		if (target == null || target == player || !com.projecthero.mod.combat.HeroTargets.canHarm(player, target)) {
			return false;
		}
		float before = target.getHealth();
		boolean landed = com.projecthero.mod.hero.power.AbilityHelpers.hurtBurst(player, target, player.damageSources().playerAttack(player), amount);
		return landed || target.getHealth() < before;
	}

	// ---------------------------------------------------------------- forms

	/** Is there room for {@code form}'s body where he stands? */
	public static boolean roomFor(ServerPlayer player, BeastForm form) {
		AABB box = BeastBoyForms.dimensions(form).makeBoundingBox(player.position());
		return player.level().noCollision(player, box.deflate(1.0e-4));
	}

	/**
	 * Morphs into {@code form} (HUMAN = back to himself). Refused (false, with a message) while dazed, in The Beast (unless the Beast
	 * is ending), when the form is locked out, with no room or too little stamina. {@code cost} is the stamina it takes.
	 */
	public static boolean morph(ServerPlayer player, BeastForm form, float cost) {
		BeastBoyState s = state(player);
		if (!s.hasPower) {
			return false;
		}
		BeastForm cur = BeastForm.byId(s.form);
		if (cur == form) {
			return false;
		}
		if (cur == BeastForm.BEAST && s.timers.beastUntil() > now(player)) {
			say(player, "message.projecthero.beast_boy.beast_locked", ChatFormatting.DARK_GREEN);
			return false;
		}
		if (form != BeastForm.HUMAN) {
			if (dazed(player)) {
				return false;
			}
			if (formLockout(player, form) > 0) {
				say(player, "message.projecthero.beast_boy.form_locked", ChatFormatting.GRAY,
						Component.translatable("projecthero.beast_boy.form." + form.name().toLowerCase(java.util.Locale.ROOT)));
				return false;
			}
			if (!player.getAbilities().instabuild && form != BeastForm.BEAST && s.stamina + 1.0e-3f < Math.max(cost, cur == BeastForm.HUMAN ? BeastBoyConfig.MORPH_MIN : cost)) {
				say(player, "message.projecthero.beast_boy.no_stamina", ChatFormatting.GRAY);
				return false;
			}
		}
		if (!roomFor(player, form)) {
			say(player, "message.projecthero.beast_boy.no_room", ChatFormatting.GRAY);
			return false;
		}
		if (form != BeastForm.HUMAN && cost > 0f && !spendStamina(player, cost)) {
			return false;
		}
		setForm(player, form);
		return true;
	}

	/** Sets the form outright (no checks): pool full, the body refreshed, flight and riders sorted. */
	public static void setForm(ServerPlayer player, BeastForm form) {
		BeastForm old = form(player);
		BeastBoyState n = state(player).copy();
		n.form = form.ordinal();
		n.formHp = form.formHealth;
		n.anim = new BeastBoyState.Anim(0, 0L, 0, form == BeastForm.HUMAN ? "morph_out" : "morph_in", now(player), 8);
		if (old != form) {
			n.morph = new BeastBoyState.Morph(old.ordinal(), now(player)); // morph round: every client plays the transition from this
		}
		if (form != BeastForm.BEAST) {
			n.timers = n.timers.withBeast(0L);
		}
		save(player, n);
		POOL_HURT.remove(player.getUUID());
		// riders get off a body that can't carry them
		if (BeastBoyForms.seats(form) == 0 && !player.getPassengers().isEmpty()) {
			player.ejectPassengers();
		}
		player.refreshDimensions();
		updateFlight(player, form, old);
		reconcile(player);
		puff(player);
		sound(player, SoundEvents.SLIME_SQUISH_SMALL, 0.35f, 1.5f);
		morphSound(player, form);
		SoundEvent voice = voice(form);
		if (voice != null) {
			sound(player, voice, 0.3f, 1.0f);
		}
	}

	/** Back to human (no daze). */
	public static void toHuman(ServerPlayer player) {
		if (morphed(player)) {
			setForm(player, BeastForm.HUMAN);
		}
	}

	/** The form's health ran out: back to human, dazed 1 s, that form locked for 10 s. */
	public static void knockOut(ServerPlayer player) {
		BeastForm f = form(player);
		if (f == BeastForm.HUMAN) {
			return;
		}
		setForm(player, BeastForm.HUMAN);
		BeastBoyState n = state(player).copy();
		n.timers = n.timers.withDazed(now(player) + BeastBoyConfig.DAZE_TICKS);
		if (f != BeastForm.BEAST) {
			n.readyAt.put("form_" + f.name().toLowerCase(java.util.Locale.ROOT), now(player) + BeastBoyConfig.FORM_LOCKOUT_TICKS);
		}
		save(player, n);
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BeastBoyConfig.DAZE_TICKS, 3, false, false, true));
		say(player, "message.projecthero.beast_boy.knocked_out", ChatFormatting.RED);
		sound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.5f, 0.8f);
	}

	/** The animal's voice when he becomes it (quiet vanilla sounds). */
	static SoundEvent voice(BeastForm f) {
		return switch (f) {
			case CHEETAH, TIGER -> SoundEvents.OCELOT_AMBIENT;
			case HAWK -> SoundEvents.PARROT_AMBIENT;
			case DOLPHIN, SHARK, WHALE -> SoundEvents.DOLPHIN_AMBIENT;
			case FLY -> SoundEvents.BEE_LOOP;
			case TREX -> SoundEvents.RAVAGER_AMBIENT;
			case GORILLA -> SoundEvents.IRON_GOLEM_REPAIR;
			case RHINO -> SoundEvents.HOGLIN_AMBIENT;
			case ELEPHANT -> SoundEvents.SNIFFER_IDLE;
			case WOLF -> SoundEvents.WOLF_AMBIENT;
			case BEAR, BEAST -> SoundEvents.POLAR_BEAR_WARNING;
			case PTERODACTYL -> SoundEvents.PHANTOM_AMBIENT;
			case BAT -> SoundEvents.BAT_TAKEOFF;
			case OCTOPUS -> SoundEvents.SQUID_AMBIENT;
			case MOUSE, SNAKE -> SoundEvents.SILVERFISH_AMBIENT;
			default -> null;
		};
	}

	private static void updateFlight(ServerPlayer player, BeastForm form, BeastForm old) {
		if (player.getAbilities().instabuild || player.isSpectator()) {
			return;
		}
		if (BeastBoyForms.flies(form)) {
			FLIGHT.add(player.getUUID());
			boolean airborne = !player.onGround() && !player.isInWater();
			player.getAbilities().mayfly = true;
			if (airborne || form == BeastForm.FLY) {
				player.getAbilities().flying = true;
			}
			player.onUpdateAbilities();
		} else if (FLIGHT.remove(player.getUUID())) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
			player.resetFallDistance();
		}
	}

	/** Is his flight on (a flying form in the air)? Client-safe. */
	public static boolean isFlying(Player player) {
		return BeastBoyForms.flies(form(player)) && player.getAbilities().flying;
	}

	// ---------------------------------------------------------------- damage into the form's pool

	/**
	 * {@code ALLOW_DAMAGE}: in an animal form a hit comes off the form's own health instead of his hearts. Void / kill damage goes
	 * through (and turns him back). Flying forms take no fall damage. Returns whether vanilla should apply the damage.
	 */
	public static boolean allowDamage(ServerPlayer player, DamageSource source, float amount) {
		BeastBoyState s = peek(player);
		if (s == null || !s.hasPower) {
			return true;
		}
		BeastForm f = BeastForm.byId(s.form);
		if (f == BeastForm.HUMAN || f.formHealth <= 0f) {
			return true;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			setForm(player, BeastForm.HUMAN);
			return true;
		}
		if (source.is(DamageTypeTags.IS_FALL) && (BeastBoyForms.flies(f) || BeastBoyForms.tiny(f))) {
			return false;
		}
		if (source.is(DamageTypeTags.IS_DROWNING) && BeastBoyForms.aquatic(f)) {
			return false;
		}
		if (player.getAbilities().invulnerable || player.isSpectator()) {
			return true;
		}
		long now = now(player);
		float[] last = POOL_HURT.get(player.getUUID());
		float dealt = amount;
		if (last != null && now - (long) last[0] < BeastBoyConfig.POOL_HURT_WINDOW && now >= (long) last[0]) {
			if (amount <= last[1]) {
				return false;
			}
			dealt = amount - last[1];
			POOL_HURT.put(player.getUUID(), new float[] { last[0], amount });
		} else {
			POOL_HURT.put(player.getUUID(), new float[] { now, amount });
		}
		absorb(player, dealt, source);
		return false;
	}

	/** Takes {@code amount} off the form's pool (with the hurt flash and a little knockback); knocks him out when it's gone. */
	public static void absorb(ServerPlayer player, float amount, DamageSource source) {
		BeastBoyState n = state(player).copy();
		n.formHp = Math.max(0f, n.formHp - amount);
		save(player, n);
		if (source != null) {
			player.level().broadcastDamageEvent(player, source);
			if (source.getEntity() instanceof LivingEntity attacker && attacker != player) {
				player.knockback(0.35, attacker.getX() - player.getX(), attacker.getZ() - player.getZ());
				player.hurtMarked = true;
			}
		}
		if (n.formHp <= 0f) {
			knockOut(player);
		}
	}

	// ---------------------------------------------------------------- the power

	/** The green ocelot's bite (after the fever), the serum, the command. False if he already has it (or unique powers refuse). */
	public static boolean grant(ServerPlayer player) {
		BeastBoyState old = state(player);
		if (old.hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		BeastBoyState s = new BeastBoyState();
		s.hasPower = true;
		s.stamina = BeastBoyConfig.STAMINA_MAX;
		save(player, s);
		reconcile(player);
		player.removeEffect(MobEffects.CONFUSION);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.OCELOT_AMBIENT, SoundSource.PLAYERS, 0.6f, 0.8f);
		level.sendParticles(dust(GREEN, 1.4f), c.x, c.y, c.z, 40, 0.5, 0.9, 0.5, 0.0);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.beast_boy.title").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.beast_boy.subtitle").withStyle(ChatFormatting.DARK_GREEN)));
		player.displayClientMessage(Component.translatable("message.projecthero.beast_boy.acquired").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.beast_boy.acquired_hint").withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		if (morphed(player)) {
			setForm(player, BeastForm.HUMAN);
		}
		save(player, new BeastBoyState());
		player.refreshDimensions();
		reconcile(player);
		forget(player.getUUID());
	}

	/** Death, a new world, leaving: back to human, the transient moves end. */
	public static void clearTransient(ServerPlayer player) {
		BeastBoyState s = peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		if (s.form != 0) {
			setForm(player, BeastForm.HUMAN);
		}
		BeastBoyState n = state(player).copy();
		n.timers = n.timers.withBeast(0L).withDazed(0L).withLeap(0L);
		n.chain = 0;
		n.chainForm = -1;
		n.anim = BeastBoyState.Anim.NONE;
		save(player, n);
		BeastBoyMoves.forget(player.getUUID());
		reconcile(player);
	}

	public static void onPlayerJoin(ServerPlayer player) {
		if (hasPower(player)) {
			clearTransient(player);
		}
	}

	/** A death: stamina comes back full; a fever in progress is lost. Cooldowns stay. */
	public static void onPlayerRespawn(ServerPlayer player) {
		BeastBoyState s = peek(player);
		if (s == null) {
			return;
		}
		if (!s.hasPower) {
			if (s.timers.feverUntil() > 0L) {
				BeastBoyState n = s.copy();
				n.timers = n.timers.withFever(0L);
				save(player, n);
			}
			return;
		}
		clearTransient(player);
		BeastBoyState n = state(player).copy();
		n.stamina = BeastBoyConfig.STAMINA_MAX;
		save(player, n);
		reconcile(player);
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		BeastBoyState s = peek(player);
		if (s == null || !s.hasPower) {
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
			PowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, STEP_ID);
			PowerToggles.clearModifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID);
			return;
		}
		BeastForm f = BeastForm.byId(s.form);
		double speed = BeastBoyForms.speedBonus(f);
		if (BeastBoyForms.aquatic(f) && player.isInWater()) {
			speed = 0.0;
		}
		if (speed != 0.0) {
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
		}
		double melee = BeastBoyForms.meleeBonus(f);
		if (melee != 0.0) {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, melee, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
		}
		if (BeastBoyForms.tallStep(f)) {
			PowerToggles.modifier(player, Attributes.STEP_HEIGHT, STEP_ID, 0.5, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, STEP_ID);
		}
		if (f == BeastForm.CHEETAH || f == BeastForm.WOLF || f == BeastForm.BEAST || f == BeastForm.TIGER) {
			PowerToggles.modifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID, 6.0, AttributeModifier.Operation.ADD_VALUE); // cats land on their feet
		} else {
			PowerToggles.clearModifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID);
		}
	}

	// ---------------------------------------------------------------- tick

	/** Every player, every tick (from {@code AbilityRouter.serverTick}): the fever, the bite, and the power itself. */
	public static void tick(ServerPlayer player) {
		BeastBoyState s = player.getAttachedOrElse(ModAttachments.BEAST_BOY_STATE, null);
		if (s == null || !s.hasPower) {
			BeastBoyLearning.tickHuman(player, s);
			return;
		}
		long now = now(player);
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		BeastForm f = BeastForm.byId(s.form);
		// Morph Stamina (in place; re-sent every few ticks)
		if (f == BeastForm.HUMAN) {
			s.stamina = Math.min(BeastBoyConfig.STAMINA_MAX, s.stamina + BeastBoyConfig.STAMINA_REGEN_PER_TICK * com.projecthero.mod.titans.TitansTogether.regenMult(player));
		} else if (!player.getAbilities().instabuild) {
			s.stamina = Math.max(0f, s.stamina - BeastBoyForms.drainPerTick(f));
		}
		// the chain fades (before any form change below re-saves the state)
		if ((s.chain > 0 || s.chainForm >= 0) && now - s.timers.chainAt() > BeastBoyConfig.CHAIN_TIMEOUT) {
			s.chain = 0;
			s.chainForm = -1;
		}
		if (f == BeastForm.BEAST) {
			if (now >= s.timers.beastUntil()) {
				setForm(player, BeastForm.HUMAN);
				say(player, "message.projecthero.beast_boy.beast_end", ChatFormatting.DARK_GREEN);
			}
		} else if (f != BeastForm.HUMAN && s.stamina <= 0f) {
			setForm(player, BeastForm.HUMAN);
			say(player, "message.projecthero.beast_boy.tired", ChatFormatting.GRAY);
		}
		// instincts: night vision
		MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
		if (nv == null || (!nv.isInfiniteDuration() && nv.getDuration() < 220)) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, BeastBoyConfig.NIGHT_VISION_TICKS, 0, true, false, false));
		}
		f = form(player);
		// v0.17.4: Regeneration II in an animal form -- the effect, and the form's own pool healing at its rate (in place, synced below)
		formRegen(player, state(player), f);
		formTick(player, f, now);
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
		BeastBoyAbilityManager.tick(player);
		BeastBoyMoves.tick(player);
		if (now % BeastBoyConfig.SYNC_INTERVAL == 0) {
			save(player, state(player).copy());
		}
	}

	/**
	 * v0.17.4 (user: "Give beast boy regen 2 in his animal forms please"): Regeneration II while he is an animal (any form with its own
	 * health), taken away as he turns human. Hits come off the form's pool, not his hearts, so the pool heals at Regeneration II's rate
	 * too (1 health every 25 ticks, as a small step every tick).
	 */
	static void formRegen(ServerPlayer player, BeastBoyState s, BeastForm f) {
		MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
		if (f == BeastForm.HUMAN || f.formHealth <= 0f) {
			// only ours: an ambient Regeneration II no longer than ours (a potion or a golden apple is left alone)
			if (regen != null && regen.isAmbient() && regen.getAmplifier() == BeastBoyConfig.FORM_REGEN_AMPLIFIER
					&& regen.getDuration() <= BeastBoyConfig.FORM_REGEN_TICKS) {
				player.removeEffect(MobEffects.REGENERATION);
			}
			return;
		}
		if (regen == null || (regen.getAmplifier() <= BeastBoyConfig.FORM_REGEN_AMPLIFIER && !regen.isInfiniteDuration()
				&& regen.getDuration() < BeastBoyConfig.FORM_REGEN_TICKS - 20)) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, BeastBoyConfig.FORM_REGEN_TICKS, BeastBoyConfig.FORM_REGEN_AMPLIFIER, true, false, true));
		}
		if (s.formHp > 0f && s.formHp < f.formHealth) {
			s.formHp = Math.min(f.formHealth, s.formHp + BeastBoyConfig.FORM_REGEN_PER_TICK);
		}
	}

	private static void formTick(ServerPlayer player, BeastForm f, long now) {
		if (f == BeastForm.HUMAN) {
			return;
		}
		// tiny forms: mobs lose interest
		if (BeastBoyForms.tiny(f) && now % 5 == 0) {
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24.0), m -> m.getTarget() == player)) {
				mob.setTarget(null);
			}
		}
		// water forms
		if (BeastBoyForms.aquatic(f)) {
			player.setAirSupply(player.getMaxAirSupply());
			if (player.isInWater()) {
				int amp = f == BeastForm.WHALE ? 0 : 1;
				MobEffectInstance g = player.getEffect(MobEffects.DOLPHINS_GRACE);
				if (g == null || g.getDuration() < 30) {
					player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, amp, true, false, false));
				}
			}
		}
		// flying forms keep their wings (someone else may have switched flight off)
		if (BeastBoyForms.flies(f) && !player.getAbilities().instabuild && !player.isSpectator() && !player.getAbilities().mayfly) {
			FLIGHT.add(player.getUUID());
			player.getAbilities().mayfly = true;
			player.onUpdateAbilities();
		}
		if (BeastBoyForms.flies(f)) {
			player.resetFallDistance();
		}
		// the Beast and the rhino smash through soft blocks
		if (BeastBoyForms.smashes(f)) {
			BeastBoyMoves.smashAhead(player, f);
		}
		// the Beast's leap lands with a slam
		BeastBoyState s = state(player);
		if (s.timers.leapAt() > 0L && now - s.timers.leapAt() > 3 && (player.onGround() || now - s.timers.leapAt() > 60)) {
			BeastBoyState n = s.copy();
			n.timers = n.timers.withLeap(0L);
			save(player, n);
			BeastBoyMoves.leapSlam(player);
		}
	}

	/** Mod id helper for the moves' resource keys. */
	static ResourceLocation id(String path) {
		return ProjectHeroMod.id(path);
	}
}
