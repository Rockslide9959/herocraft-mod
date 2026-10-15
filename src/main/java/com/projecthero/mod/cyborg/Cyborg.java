package com.projecthero.mod.cyborg;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.cyborg.data.CyborgState;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The single server-side API for Cyborg (Victor Stone), the v0.16.5 Hero-Tier power. Nothing else touches
 * {@link CyborgState}; every change re-saves through {@link ServerPlayer#setAttached}. The moves live in
 * {@link CyborgAbilities}, the damage rules in {@link CyborgDamage}, the numbers in {@link CyborgConfig}.
 *
 * <h2>How it works</h2>
 * A Mother Box (the Darkseid raid's reward) used by someone on 4 hearts or less fuses with them: a 10 s conversion as
 * the plates spread over the body from the chest core. He is always Cyborg (v0.16.5 rework, user): <b>H</b> projects a
 * <b>hologram</b> of his old human skin from the light on his forehead, built over him a texel at a time -- a disguise
 * only; any move (or H again) drops it.
 *
 * <h2>The Power Core</h2>
 * 0..100, charging 2 a second (6 next to a block of redstone, none in water). A lightning strike fills it.
 */
public final class Cyborg {
	public static final String KEY = "cyborg";

	private static final ResourceLocation ARMOR_ID = PowerToggles.id("cyborg_plating");

	public static final DustParticleOptions BLUE = new DustParticleOptions(new Vector3f(0.35f, 0.75f, 1.0f), 1.2f);
	public static final DustParticleOptions BLUE_BIG = new DustParticleOptions(new Vector3f(0.45f, 0.85f, 1.0f), 2.2f);
	public static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.1f), 1.0f);

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TOGGLE = new ConcurrentHashMap<>();
	/** Food exhaustion seen last tick (the combat form halves every rise). */
	private static final Map<UUID, Float> LAST_EXHAUSTION = new ConcurrentHashMap<>();
	/** Whether the redstone recharge applies (re-checked once a second). */
	private static final Map<UUID, Boolean> NEAR_REDSTONE = new ConcurrentHashMap<>();

	private Cyborg() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		LAST_TOGGLE.clear();
		LAST_EXHAUSTION.clear();
		NEAR_REDSTONE.clear();
		CyborgAbilities.clearSessionState();
		CyborgAbilityManager.clearSessionState();
	}

	// ---------------------------------------------------------------- state

	public static CyborgState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.CYBORG_STATE);
	}

	static void save(ServerPlayer player, CyborgState state) {
		player.setAttached(ModAttachments.CYBORG_STATE, state);
	}

	private static CyborgState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.CYBORG_STATE, null);
	}

	public static boolean hasPower(Player player) {
		CyborgState s = peek(player);
		return s != null && s.hasPower;
	}

	/** Has the power: v0.16.5 rework, he is always Cyborg (the hologram is only a disguise). Client-safe. */
	public static boolean combat(Player player) {
		return hasPower(player);
	}

	/** In Jet Flight (the jetpack is out). Client-safe. */
	public static boolean flying(Player player) {
		CyborgState s = peek(player);
		return s != null && s.hasPower && s.hovering;
	}

	public static float core(Player player) {
		CyborgState s = peek(player);
		return s == null ? 0f : s.core;
	}

	public static boolean matrixUp(Player player) {
		CyborgState s = peek(player);
		return s != null && s.hasPower && s.shieldUntil > player.level().getGameTime() && s.shieldHp > 0f;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		CyborgState s = peek(player);
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

	// ---------------------------------------------------------------- the core

	public static boolean spendCore(ServerPlayer player, float cost) {
		if (cost <= 0f) {
			return true;
		}
		CyborgState s = state(player);
		if (s.core + 1.0e-3f < cost) {
			return false;
		}
		CyborgState n = s.copy();
		n.core = Math.max(0f, Math.min(CyborgConfig.CORE_MAX, s.core) - cost);
		save(player, n);
		return true;
	}

	public static void setCore(ServerPlayer player, float value) {
		CyborgState n = state(player).copy();
		n.core = Math.max(0f, Math.min(CyborgConfig.CORE_MAX, value));
		save(player, n);
	}

	// ---------------------------------------------------------------- grant / revoke

	/** The Mother Box / the command. False if the player is already Cyborg. */
	public static boolean grant(ServerPlayer player) {
		if (state(player).hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		long now = player.level().getGameTime();
		CyborgState s = new CyborgState();
		s.hasPower = true;
		s.core = CyborgConfig.CORE_MAX;
		s.combat = true;
		s.formChangeAt = now;
		s.animId = CyborgState.ANIM_CONVERT;
		s.animStart = now;
		save(player, s);
		reconcile(player);
		CyborgAbilities.beginConversionFx(player);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.cyborg.title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.cyborg.subtitle").withStyle(ChatFormatting.GRAY)));
		player.displayClientMessage(Component.translatable("message.projecthero.cyborg.acquired")
				.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.cyborg.acquired_hint")
				.withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		CyborgAbilities.clear(player);
		save(player, new CyborgState());
		reconcile(player);
		forget(player.getUUID());
	}

	private static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		LAST_TOGGLE.remove(id);
		LAST_EXHAUSTION.remove(id);
		NEAR_REDSTONE.remove(id);
		CyborgAbilityManager.forget(id);
	}

	// ---------------------------------------------------------------- the hologram (H)

	/**
	 * Whether the Mother Box is still fusing with him (the 10 s conversion) -- the only time his kit waits. v0.16.5
	 * rework (user): he has no human form any more, he is always Cyborg.
	 */
	public static boolean changing(Player player) {
		CyborgState s = peek(player);
		if (s == null || !s.hasPower || s.animId != CyborgState.ANIM_CONVERT) {
			return false;
		}
		long age = player.level().getGameTime() - s.formChangeAt;
		return age >= 0 && age < CyborgConfig.CONVERT_TICKS;
	}

	/** The holographic cover of his old human skin is up (or building). Client-safe. */
	public static boolean hologram(Player player) {
		CyborgState s = peek(player);
		return s != null && s.hasPower && !s.combat;
	}

	/** H: projects the hologram, or drops it. */
	public static void toggleForm(ServerPlayer player) {
		CyborgState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = LAST_TOGGLE.get(player.getUUID());
		if (last != null && now - last < CyborgConfig.TOGGLE_COOLDOWN && now >= last) {
			return;
		}
		if (changing(player)) {
			return; // let the conversion finish
		}
		LAST_TOGGLE.put(player.getUUID(), now);
		if (s.combat) {
			hologramOn(player);
		} else {
			dropHologram(player);
		}
	}

	/** Drops the hologram (H again, or any move): it dissolves from the feet up. Nothing if it is not up. */
	public static void dropHologram(ServerPlayer player) {
		CyborgState s = state(player);
		if (!s.hasPower || s.combat) {
			return;
		}
		long now = player.level().getGameTime();
		CyborgState n = s.copy();
		n.combat = true;
		n.formChangeAt = now;
		n.animId = CyborgState.ANIM_PLATES_ON;
		n.animStart = now;
		save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.sendParticles(BLUE, c.x, c.y, c.z, 24, 0.35, 0.8, 0.35, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.8f);
	}

	/** Projects the hologram from the light on his forehead; it builds over him top-down, a texel at a time. */
	public static void hologramOn(ServerPlayer player) {
		CyborgState s = state(player);
		if (!s.hasPower || !s.combat) {
			return;
		}
		long now = player.level().getGameTime();
		CyborgState n = s.copy();
		n.combat = false;
		n.formChangeAt = now;
		n.animId = CyborgState.ANIM_PLATES_OFF;
		n.animStart = now;
		save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.getEyePosition();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.9f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.4f);
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		if (hasPower(player)) {
			PowerToggles.modifier(player, Attributes.ARMOR, ARMOR_ID, CyborgConfig.ARMOR_BONUS, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ARMOR, ARMOR_ID);
		}
	}

	static boolean canAct(ServerPlayer player) {
		CyborgState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (changing(player)) {
			say(player, "message.projecthero.cyborg.converting", ChatFormatting.GRAY);
			return false;
		}
		// any move drops the hologram
		dropHologram(player);
		return true;
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		CyborgState s = player.getAttachedOrElse(ModAttachments.CYBORG_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		if (player.tickCount % 20 == 0) {
			reconcile(player);
			NEAR_REDSTONE.put(player.getUUID(), nearRedstone(player));
		}
		if (now % 5 == 0) {
			tickCore(player, s);
		}
		if (player.getAirSupply() < player.getMaxAirSupply()) {
			player.setAirSupply(player.getMaxAirSupply());
		}
		tickHunger(player);
		CyborgAbilities.tick(player);
	}

	private static void tickCore(ServerPlayer player, CyborgState s) {
		if (player.isInWater()) {
			return; // water stops the core charging
		}
		float rate = Boolean.TRUE.equals(NEAR_REDSTONE.get(player.getUUID())) ? CyborgConfig.CORE_REGEN_REDSTONE : CyborgConfig.CORE_REGEN;
		float cur = Float.isNaN(s.core) ? 0f : s.core;
		float next = Math.min(CyborgConfig.CORE_MAX, cur + rate / 4f);
		if (next != s.core) {
			CyborgState n = s.copy();
			n.core = next;
			save(player, n);
		}
	}

	private static boolean nearRedstone(ServerPlayer player) {
		BlockPos c = player.blockPosition();
		int r = CyborgConfig.REDSTONE_RANGE;
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
			if (player.level().getBlockState(p).is(Blocks.REDSTONE_BLOCK)) {
				return true;
			}
		}
		return false;
	}

	/** Every rise in food exhaustion is halved (his cybernetics run half his body). */
	private static void tickHunger(ServerPlayer player) {
		float e = player.getFoodData().getExhaustionLevel();
		Float last = LAST_EXHAUSTION.put(player.getUUID(), e);
		if (last != null && e > last) {
			float halved = last + (e - last) * CyborgConfig.HUNGER_FACTOR;
			player.getFoodData().setExhaustion(halved);
			LAST_EXHAUSTION.put(player.getUUID(), halved);
		}
	}

	/** A lightning strike fills the core (and does no harm); returns whether it was absorbed. */
	static boolean lightning(ServerPlayer player) {
		if (!combat(player)) {
			return false;
		}
		setCore(player, CyborgConfig.CORE_MAX);
		say(player, "message.projecthero.cyborg.lightning", ChatFormatting.AQUA);
		return true;
	}

	// ---------------------------------------------------------------- lifecycle

	public static void onPlayerJoin(ServerPlayer player) {
		CyborgState s = state(player);
		if (!s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		CyborgState n = s.copy();
		n.chargeStart = 0L;
		n.hovering = false;
		n.shieldUntil = 0L;
		n.fx = new CyborgState.Fx();
		n.scanUntil = 0L;
		n.formChangeAt = 0L;
		n.animId = CyborgState.ANIM_NONE;
		n.core = Float.isNaN(n.core) ? 0f : Math.max(0f, Math.min(CyborgConfig.CORE_MAX, n.core));
		n.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > now + CyborgConfig.BOOM_COOLDOWN + 20L);
		save(player, n);
		reconcile(player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		CyborgState s = state(player);
		if (!s.hasPower) {
			return;
		}
		CyborgState n = s.copy();
		n.combat = true;
		n.formChangeAt = 0L;
		n.chargeStart = 0L;
		n.hovering = false;
		n.shieldUntil = 0L;
		n.fx = new CyborgState.Fx();
		n.scanUntil = 0L;
		n.animId = CyborgState.ANIM_NONE;
		n.core = Math.max(n.core, CyborgConfig.CORE_MAX * 0.5f);
		n.abilityReadyAt.clear();
		save(player, n);
		reconcile(player);
	}

	/** Death / logout / dimension change: drop everything transient (the power, the form and the waypoint stay). */
	public static void clearTransient(ServerPlayer player) {
		CyborgAbilities.clear(player);
		CyborgAbilities.stopHover(player);
		forget(player.getUUID());
		CyborgState s = player.getAttachedOrElse(ModAttachments.CYBORG_STATE, null);
		if (s != null && s.hasPower && (s.chargeStart != 0L || s.hovering || s.shieldUntil != 0L || s.fx.beamUntil != 0L || s.fx.cableUntil != 0L)) {
			CyborgState n = s.copy();
			n.chargeStart = 0L;
			n.hovering = false;
			n.shieldUntil = 0L;
			n.fx = new CyborgState.Fx();
			save(player, n);
		}
	}

	static void setAnim(ServerPlayer player, int animId) {
		CyborgState n = state(player).copy();
		n.animId = animId;
		n.animStart = player.level().getGameTime();
		save(player, n);
	}
}
