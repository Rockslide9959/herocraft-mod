package com.projecthero.mod.shazam;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.shazam.data.ShazamState;
import com.projecthero.mod.squad.Squads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.3 Shazam (Billy Batson): the single server-side API of the power. Nothing else changes {@link ShazamState}. The numbers
 * are in {@link ShazamConfig}; the moves are {@link ShazamMoves}' (routed by {@link ShazamAbilityManager}), the flight
 * {@link ShazamFlight}'s, the family {@link ShazamFamily}'s and the Wizard {@code entity.WizardShazamEntity}'s.
 *
 * <ul>
 *   <li><b>The word</b> ({@link #speak}): typed in chat ({@link ShazamWord}), never posted. Magic lightning strikes him: he
 *   transforms, or turns back. The bolt hits every creature within 3 blocks for 12 (never him or a squadmate) and refills the
 *   Lightning bar. 4 s between words; no voice, no word.</li>
 *   <li><b>Passives</b> (transformed only, {@link #reconcile}, {@link ShazamDamage}): 50 health, 35% resistance, no hunger drain,
 *   +10 melee, +40% speed, 0.1 blocks taller, immune to fear; magic hurts him 50% more.</li>
 *   <li><b>Shazam Family</b>: half the health and damage, no Eternity Strike.</li>
 * </ul>
 */
public final class Shazam {
	public static final String KEY = "shazam";

	/** Shazam's lightning: his own damage, armour still counts ({@code data/projecthero/damage_type/shazam_lightning.json}). */
	public static final ResourceKey<DamageType> LIGHTNING = ResourceKey.create(Registries.DAMAGE_TYPE, ProjectHeroMod.id("shazam_lightning"));

	/** The name his lightning bolts carry: the client turns their thunder down ({@code ShazamBoltSoundMixin}). */
	public static final String BOLT_NAME = "projecthero:shazam_bolt";

	private static final ResourceLocation HEALTH_ID = PowerToggles.id("shazam_health");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("shazam_speed");
	private static final ResourceLocation ATTACK_ID = PowerToggles.id("shazam_attack");
	private static final ResourceLocation SCALE_ID = PowerToggles.id("shazam_scale");

	public static final int RED = 0xC8141E;
	public static final int GOLD = 0xFFC83A;
	public static final int BOLT_WHITE = 0xE8F4FF;

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private Shazam() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		ShazamAbilityManager.clearSessionState();
		ShazamMoves.clearSessionState();
		ShazamFlight.clearSessionState();
		ShazamWizards.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		ShazamMoves.forget(id);
		ShazamFlight.forget(id);
	}

	// ---------------------------------------------------------------- state (client and server)

	public static ShazamState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.SHAZAM_STATE);
	}

	static void save(ServerPlayer player, ShazamState state) {
		player.setAttached(ModAttachments.SHAZAM_STATE, state);
	}

	public static ShazamState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.SHAZAM_STATE, null);
	}

	/** The champion (whether transformed or not). */
	public static boolean hasPower(Player player) {
		ShazamState s = peek(player);
		return s != null && s.hasPower;
	}

	/** Transformed right now (champion or family member). Client-safe. */
	public static boolean transformed(Player player) {
		ShazamState s = peek(player);
		return s != null && s.transformed && s.canSpeak(now(player));
	}

	/** A Shazam Family member right now (shared power, not the champion). Client-safe. */
	public static boolean isFamily(Player player) {
		ShazamState s = peek(player);
		return s != null && s.family(now(player));
	}

	/** May say the word (champion or family). Client-safe. */
	public static boolean canSpeak(Player player) {
		ShazamState s = peek(player);
		return s != null && s.canSpeak(now(player));
	}

	public static boolean isFlying(Player player) {
		ShazamState s = peek(player);
		return s != null && s.flying && s.transformed;
	}

	/** Courage of Achilles: transformed, so immune to fear (Warden darkness, roars, Raven's fear). Other powers ask this. */
	public static boolean fearImmune(Entity entity) {
		return entity instanceof Player p && transformed(p);
	}

	public static float lightning(Player player) {
		ShazamState s = peek(player);
		return s == null ? 0f : s.lightning;
	}

	static long now(Player player) {
		return player.level().getGameTime();
	}

	/** A family member's moves and health bonus are halved. */
	public static float share(Player player) {
		return isFamily(player) ? ShazamConfig.FAMILY_SHARE : 1f;
	}

	public static boolean standing(Player player) {
		ShazamState s = peek(player);
		return s != null && s.transformed && s.standUntil > now(player);
	}

	public static boolean wise(Player player) {
		ShazamState s = peek(player);
		return s != null && s.transformed && s.wisdomUntil > now(player);
	}

	public static boolean storming(Player player) {
		ShazamState s = peek(player);
		return s != null && s.transformed && s.stormUntil > now(player);
	}

	/** Eternity Strike's ticks so far (-1 when not running). Client-safe. */
	public static int eternityAge(Player player) {
		ShazamState s = peek(player);
		return s == null || s.eternityStart <= 0L ? -1 : (int) Math.max(0L, now(player) - s.eternityStart);
	}

	public static int cooldownRemaining(Player player, String id) {
		ShazamState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(id);
		return ready == null ? 0 : (int) Math.max(0L, ready - now(player));
	}

	static void startCooldown(ServerPlayer player, String id, int ticks) {
		ShazamState n = state(player).copy();
		n.abilityReadyAt.put(id, now(player) + ticks);
		save(player, n);
	}

	public static void clearCooldowns(ServerPlayer player) {
		ShazamState n = state(player).copy();
		n.abilityReadyAt.clear();
		n.lastWord = 0L;
		save(player, n);
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

	// ---------------------------------------------------------------- the Lightning bar

	public static boolean hasLightning(ServerPlayer player, float amount) {
		return player.getAbilities().instabuild || state(player).lightning + 1.0e-3f >= amount;
	}

	/** Spends Lightning if there is enough; false ("Not enough lightning") if not. Creative never pays. */
	public static boolean spend(ServerPlayer player, float amount) {
		if (player.getAbilities().instabuild || amount <= 0f) {
			return true;
		}
		ShazamState s = state(player);
		if (s.lightning + 1.0e-3f < amount) {
			say(player, "message.projecthero.shazam.no_lightning", ChatFormatting.GRAY);
			return false;
		}
		ShazamState n = s.copy();
		n.lightning = Math.max(0f, s.lightning - amount);
		save(player, n);
		return true;
	}

	public static void setLightning(ServerPlayer player, float value) {
		ShazamState n = state(player).copy();
		n.lightning = Math.max(0f, Math.min(ShazamConfig.LIGHTNING_MAX, value));
		save(player, n);
	}

	// ---------------------------------------------------------------- hits and bolts

	/** Shazam's lightning as {@code player}'s own damage. */
	public static DamageSource lightningSource(ServerPlayer player) {
		return player.damageSources().source(LIGHTNING, player);
	}

	/**
	 * One of his moves hits {@code target} for {@code amount} x his {@link #share} (lightning damage when {@code lightning}, else his
	 * punch). Never himself; squadmates, their pets and players with PvP off are vetoed by {@link HeroTargets#canHarm}.
	 */
	public static boolean hit(ServerPlayer player, LivingEntity target, float amount, boolean lightning) {
		if (target == null || target == player || !target.isAlive() || !HeroTargets.canHarm(player, target)) {
			return false;
		}
		float dmg = amount * share(player);
		DamageSource source = lightning ? lightningSource(player) : player.damageSources().playerAttack(player);
		float before = target.getHealth();
		boolean landed = com.projecthero.mod.hero.power.AbilityHelpers.hurtBurst(player, target, source, dmg);
		return landed || target.getHealth() < before;
	}

	/** May an area of his (a bolt's splash, a blast) touch {@code e}: never him, never a squadmate, never a protected target. */
	public static boolean areaVictim(ServerPlayer player, Entity e) {
		return e instanceof LivingEntity le && le != player && le.isAlive() && !le.isSpectator()
				&& !Squads.areAllies(player, le) && HeroTargets.canHarm(player, le);
	}

	/**
	 * A real lightning bolt at {@code at}, for the look: {@code visualOnly} (so vanilla's own strike hurts and burns nothing) and named
	 * {@link #BOLT_NAME} (so his clients play its thunder quietly). The move does its own damage.
	 */
	public static LightningBolt bolt(ServerLevel level, Vec3 at) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt == null) {
			return null;
		}
		bolt.moveTo(at.x, at.y, at.z);
		bolt.setVisualOnly(true);
		bolt.setCustomName(Component.literal(BOLT_NAME));
		level.addFreshEntity(bolt);
		return bolt;
	}

	public static boolean isOurBolt(Entity e) {
		return e instanceof LightningBolt && e.getCustomName() != null && BOLT_NAME.equals(e.getCustomName().getString());
	}

	// ---------------------------------------------------------------- the word

	/** What saying the word did. */
	public enum Spoken {
		/** He can't say it (not the champion, not family): the message is ordinary chat. */
		NOT_HIS,
		TRANSFORMED,
		TURNED_BACK,
		/** Within the 4 s gap: nothing happens (the word is still swallowed). */
		TOO_SOON,
		/** No voice: "You can't say it!". */
		NO_VOICE
	}

	/**
	 * The word was said (from chat, {@link ShazamMod#onChat}): transform or turn back, with the lightning. Callers have checked
	 * {@link ShazamWord#isWord}.
	 */
	public static Spoken speak(ServerPlayer player) {
		ShazamState s = state(player);
		long now = now(player);
		if (!s.canSpeak(now)) {
			return Spoken.NOT_HIS;
		}
		if (!player.isAlive() || player.isSpectator()) {
			return Spoken.TOO_SOON;
		}
		if (ShazamWord.voice(player) != ShazamWord.Voice.OK) {
			say(player, "message.projecthero.shazam.no_voice", ChatFormatting.RED);
			return Spoken.NO_VOICE;
		}
		if (s.lastWord != 0L && now - s.lastWord < ShazamConfig.WORD_GAP && now >= s.lastWord) {
			say(player, "message.projecthero.shazam.too_soon", ChatFormatting.GRAY);
			return Spoken.TOO_SOON;
		}
		boolean turnBack = s.transformed;
		ShazamState n = s.copy();
		n.lastWord = now == 0L ? 1L : now; // (0 = never spoken)
		n.lightning = ShazamConfig.LIGHTNING_MAX; // every transform bolt refills the bar
		n.transformed = !turnBack;
		if (turnBack) {
			endTransient(n);
		}
		save(player, n);
		if (turnBack) {
			ShazamFlight.stop(player);
			ShazamMoves.forget(player.getUUID());
		}
		reconcile(player);
		if (!turnBack) {
			float bonus = (float) (isFamily(player) ? ShazamConfig.FAMILY_HEALTH_BONUS : ShazamConfig.HEALTH_BONUS);
			player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + bonus));
		}
		wordStrike(player);
		return turnBack ? Spoken.TURNED_BACK : Spoken.TRANSFORMED;
	}

	/** The magic lightning that answers the word: the bolt on him (harmless to him), the splash, "SHAZAM!" for everyone near. */
	static void wordStrike(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		Vec3 c = player.position();
		bolt(level, c);
		float dmg = ShazamConfig.WORD_BOLT_DAMAGE;
		double r = ShazamConfig.WORD_BOLT_RADIUS;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r),
				e -> areaVictim(player, e) && e.distanceToSqr(player) <= r * r)) {
			hit(player, e, dmg / share(player), true); // the transform bolt is the same for the family
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1.0, c.z, 40, 0.6, 1.0, 0.6, 0.15);
		level.sendParticles(dust(GOLD, 1.6f), c.x, c.y + 1.0, c.z, 24, 0.5, 0.9, 0.5, 0.0);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
		level.playSound(null, c.x, c.y, c.z, ShazamSounds.WORD, SoundSource.PLAYERS, 0.7f, 1.0f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5f, 0.9f);
		Component sub = Component.translatable("message.projecthero.shazam.word").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		double heard = ShazamConfig.WORD_HEARD_RANGE;
		for (ServerPlayer other : level.players()) {
			if (other.distanceToSqr(player) <= heard * heard) {
				other.connection.send(new ClientboundSetTitlesAnimationPacket(3, 22, 8));
				other.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
				other.connection.send(new ClientboundSetSubtitleTextPacket(sub));
			}
		}
	}

	/** The moves that end when he turns back (or dies, or leaves). */
	private static void endTransient(ShazamState n) {
		n.flying = false;
		n.standUntil = 0L;
		n.wisdomUntil = 0L;
		n.stormUntil = 0L;
		n.eternityStart = 0L;
	}

	/** Turns him back without the word (death, family time up, revoke). No lightning. */
	public static void forceUntransform(ServerPlayer player) {
		ShazamState s = state(player);
		if (!s.transformed && !s.flying) {
			return;
		}
		ShazamFlight.stop(player);
		ShazamState n = state(player).copy();
		n.transformed = false;
		endTransient(n);
		save(player, n);
		ShazamMoves.forget(player.getUUID());
		reconcile(player);
	}

	// ---------------------------------------------------------------- the power

	/** The Wizard's gift (and the command). False if he already has it or the unique-powers rule refuses. */
	public static boolean grant(ServerPlayer player) {
		ShazamState old = state(player);
		if (old.hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		ShazamState s = new ShazamState();
		s.hasPower = true;
		s.lightning = ShazamConfig.LIGHTNING_MAX;
		s.wizardBanUntil = old.wizardBanUntil;
		s.innocentKillAt = old.innocentKillAt;
		save(player, s);
		reconcile(player);
		player.connection.send(new ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.shazam.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.connection.send(new ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.shazam.subtitle").withStyle(ChatFormatting.RED)));
		player.displayClientMessage(Component.translatable("message.projecthero.shazam.acquired").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.shazam.acquired_hint").withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		ShazamFlight.stop(player);
		ShazamFamily.disband(player);
		ShazamState old = state(player);
		ShazamState n = new ShazamState();
		n.wizardBanUntil = old.wizardBanUntil;
		n.innocentKillAt = old.innocentKillAt;
		save(player, n);
		reconcile(player);
		forget(player.getUUID());
	}

	/** Death, a new world, leaving: the running moves end (he stays transformed across a world change). */
	public static void clearTransient(ServerPlayer player) {
		ShazamState s = peek(player);
		if (s == null) {
			return;
		}
		ShazamFlight.stop(player);
		ShazamMoves.forget(player.getUUID());
		if (s.standUntil != 0L || s.stormUntil != 0L || s.eternityStart != 0L || s.flying) {
			ShazamState n = state(player).copy();
			boolean was = n.transformed;
			endTransient(n);
			n.transformed = was;
			save(player, n);
		}
		reconcile(player);
	}

	public static void onPlayerJoin(ServerPlayer player) {
		if (peek(player) != null) {
			clearTransient(player);
		}
	}

	/** A death turns him back into Billy, with a full bar. */
	public static void onPlayerRespawn(ServerPlayer player) {
		ShazamState s = peek(player);
		if (s == null) {
			return;
		}
		ShazamState n = state(player).copy();
		n.transformed = false;
		endTransient(n);
		if (n.hasPower) {
			n.lightning = ShazamConfig.LIGHTNING_MAX;
		}
		save(player, n);
		ShazamMoves.forget(player.getUUID());
		reconcile(player);
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		if (!transformed(player)) {
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
			PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			return;
		}
		boolean family = isFamily(player);
		PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID,
				family ? ShazamConfig.FAMILY_HEALTH_BONUS : ShazamConfig.HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, ShazamConfig.SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, ShazamConfig.MELEE_BONUS * share(player), AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.SCALE, SCALE_ID, ShazamConfig.SCALE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	// ---------------------------------------------------------------- tick

	/** Every player, every tick (from {@code AbilityRouter.serverTick}): cheap unless he is the champion or family. */
	public static void tick(ServerPlayer player) {
		ShazamState s = player.getAttachedOrElse(ModAttachments.SHAZAM_STATE, null);
		if (s == null || (!s.hasPower && s.familyUntil == 0L && !s.transformed)) {
			return;
		}
		long now = now(player);
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		// the family's 10 minutes
		if (!s.hasPower && s.familyUntil != 0L && now >= s.familyUntil) {
			ShazamFamily.expire(player);
			return;
		}
		if (!s.hasPower && s.familyUntil == 0L) {
			forceUntransform(player); // a stale transformation (the power was taken)
			return;
		}
		if (s.transformed) {
			// Stamina of Atlas: no hunger drain
			player.getFoodData().setExhaustion(0f);
			// Courage of Achilles: Warden darkness and fear don't take
			if (player.hasEffect(MobEffects.DARKNESS)) {
				player.removeEffect(MobEffects.DARKNESS);
			}
			// the Lightning bar fills in a thunderstorm (in place; re-sent below)
			if (player.level().isThundering()) {
				s.lightning = Math.min(ShazamConfig.LIGHTNING_MAX, s.lightning + ShazamConfig.STORM_FILL_PER_TICK);
			}
			if (player.tickCount % 20 == 0) {
				reconcile(player);
			}
			ShazamAbilityManager.tick(player);
			ShazamMoves.tick(player);
			ShazamFlight.tick(player);
		} else if (s.flying) {
			ShazamFlight.stop(player);
		}
		if (s.hasPower && !s.family.isEmpty() && player.tickCount % 20 == 0) {
			ShazamFamily.prune(player);
		}
		if (s.transformed && player.level().isThundering() && now % ShazamConfig.SYNC_INTERVAL == 0) {
			// the storm fills the bar in place: re-send it now and then
			save(player, state(player).copy());
		}
	}

	/** A lightning strike landed (any bolt, from {@code ShazamMod}): a transformed Shazam near it, not his own, soaks it up. */
	static void onLightning(ServerLevel level, LightningBolt bolt) {
		if (isOurBolt(bolt)) {
			return;
		}
		double r = ShazamConfig.STRIKE_NEAR;
		for (ServerPlayer p : level.players()) {
			if (transformed(p) && p.distanceToSqr(bolt) <= r * r) {
				setLightning(p, state(p).lightning + ShazamConfig.STRIKE_FILL);
			}
		}
	}
}
