package com.projecthero.mod.titanshifter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.data.TitanShifterState;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;
import com.projecthero.mod.titanshifter.entity.TitanShifterEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The single server-side API for the Titan Shifter Hero-Tier power (v0.12.31). Nothing else pokes
 * {@link TitanShifterState}; every mutator re-saves via {@link ServerPlayer#setAttached}, and every phase
 * change goes through {@link #setPhase}, which refuses transitions {@link TitanPhase#canGoTo} does not allow.
 *
 * <pre>
 *   Titan Serum -> {@link #grant}  (unlock; never transforms unless the config says so)
 *   Titan Shift key -> {@link #requestToggle}: HUMAN -> TRANSFORMING -> TITAN, or TITAN -> REVERTING -> HUMAN
 *   Titan HP 0 -> {@link #onTitanDefeated}: TITAN -> DEFEATED -> RECOVERING -> HUMAN
 * </pre>
 *
 * Everything is server-authoritative: the client sends key requests only, and the Titan itself is a real
 * entity the shifter rides (see {@link TitanFormEntity}).
 */
public final class TitanShifter {
	public static final String KEY = "titan_shifter";

	/** Per-player throttle for action-bar feedback so a held key cannot spam. */
	private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();
	/** Shifters whose Sprint key is currently held (sent by the client: a rider never reports sprinting on its own). */
	private static final java.util.Set<UUID> SPRINT_HELD = new java.util.HashSet<>();

	private TitanShifter() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		SPRINT_HELD.clear();
	}

	public static void setSprintHeld(ServerPlayer player, boolean held) {
		if (held) {
			SPRINT_HELD.add(player.getUUID());
		} else {
			SPRINT_HELD.remove(player.getUUID());
		}
	}

	public static boolean sprintHeld(UUID id) {
		return SPRINT_HELD.contains(id);
	}

	// ---------------- state access ----------------

	public static TitanShifterState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.TITAN_SHIFTER_STATE);
	}

	static void save(ServerPlayer player, TitanShifterState state) {
		player.setAttached(ModAttachments.TITAN_SHIFTER_STATE, state);
	}

	/** Safe on the client too (the attachment is synced to the owner). */
	public static boolean isShifter(Player player) {
		TitanShifterState s = player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		return s != null && s.unlocked;
	}

	public static TitanPhase phase(Player player) {
		TitanShifterState s = player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		return s == null || !s.unlocked ? TitanPhase.HUMAN : s.phase();
	}

	public static boolean inTitan(Player player) {
		return phase(player) == TitanPhase.TITAN;
	}

	/** The form the player is currently riding as their own Titan (either side), or null. */
	public static TitanFormEntity formOf(Player player) {
		return player.getVehicle() instanceof TitanFormEntity f && player.getUUID().equals(f.ownerId()) ? f : null;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		TitanShifterState s = player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	public static int transformCooldownRemaining(Player player) {
		TitanShifterState s = player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		return s == null ? 0 : (int) Math.max(0L, s.cooldownUntil - player.level().getGameTime());
	}

	static void startCooldown(ServerPlayer player, String abilityId, int ticks, int actionSlot) {
		long now = player.level().getGameTime();
		TitanShifterState s = state(player).copy();
		s.abilityReadyAt.put(abilityId, now + ticks);
		s.lastAction = actionSlot;
		s.lastActionTick = now;
		save(player, s);
	}

	/** Rate-limited action-bar message (one per key per second per player). */
	static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		long now = player.level().getGameTime();
		Long last = LAST_MESSAGE.get(player.getUUID());
		if (last != null && now - last < 20) {
			return;
		}
		LAST_MESSAGE.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable(key, args).withStyle(colour), true);
	}

	private static void setPhase(ServerPlayer player, TitanPhase next, long until) {
		TitanShifterState s = state(player).copy();
		TitanPhase current = s.phase();
		if (current != next && !current.canGoTo(next)) {
			return;
		}
		s.phase = next.name();
		s.phaseStartedAt = player.level().getGameTime();
		s.phaseUntil = until;
		save(player, s);
	}

	// ---------------- unlock ----------------

	/** Titan Serum / command: unlock Titan Shifting. Returns false if already unlocked. */
	public static boolean grant(ServerPlayer player) {
		if (state(player).unlocked) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		TitanShifterState s = state(player).copy();
		s.unlocked = true;
		s.phase = TitanPhase.HUMAN.name();
		s.cooldownUntil = 0L;
		s.abilityReadyAt.clear();
		s.energy = (float) TitanShifterConfig.energy().max; // a fresh shifter starts with a full Titan Energy bar
		save(player, s);

		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE,
				SoundSource.PLAYERS, 1.0f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT,
				SoundSource.PLAYERS, 2.0f, 0.7f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 50, 0.5, 0.9, 0.5, 0.3);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 20, 0.5, 0.9, 0.5, 0.05);
		player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.unlocked")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.unlocked_hint")
				.withStyle(ChatFormatting.YELLOW), false);
		if (TitanShifterConfig.transformation().serumTransformsImmediately) {
			transform(player);
		}
		return true;
	}

	/** Admin / power suppressor: remove the power and any active Titan. */
	public static void revoke(ServerPlayer player) {
		forceEnd(player, false);
		TitanShifterState s = state(player).copy();
		s.unlocked = false;
		s.phase = TitanPhase.HUMAN.name();
		s.cooldownUntil = 0L;
		s.regenUntil = 0L;
		s.hardenUntil = 0L;
		s.titanHealth = 0f;
		s.energy = 0f;
		s.abilityReadyAt.clear();
		save(player, s);
	}

	// ---------------- transformation ----------------

	/** Titan Energy (0..max) as the HUD and the transformation gate see it. */
	public static float energy(Player player) {
		TitanShifterState s = player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		return s == null ? 0f : s.energy;
	}

	/** Energy needed to transform: 90% of the bar by default. */
	public static float energyNeeded() {
		var e = TitanShifterConfig.energy();
		return (float) (e.max * e.transformMinFraction);
	}

	/** The Titan Shift key: transform when human, revert when a Titan. */
	public static void requestToggle(ServerPlayer player) {
		TitanShifterState s = state(player);
		if (!s.unlocked) {
			return;
		}
		switch (s.phase()) {
			case HUMAN -> transform(player);
			case TITAN -> revert(player);
			case TRANSFORMING, REVERTING, DEFEATED -> say(player, "message.projecthero.titan_shifter.busy", ChatFormatting.GRAY);
			case RECOVERING -> say(player, "message.projecthero.titan_shifter.recovering", ChatFormatting.GRAY);
		}
	}

	public static boolean transform(ServerPlayer player) {
		TitanShifterState s = state(player);
		if (!s.unlocked) {
			return false;
		}
		if (s.phase() != TitanPhase.HUMAN) {
			say(player, "message.projecthero.titan_shifter.already", ChatFormatting.GRAY);
			return false;
		}
		long now = player.level().getGameTime();
		if (now < s.cooldownUntil) {
			say(player, "message.projecthero.titan_shifter.cooldown", ChatFormatting.RED,
					String.format(java.util.Locale.ROOT, "%.0f", (s.cooldownUntil - now) / 20.0));
			return false;
		}
		if (s.energy + 1.0e-3f < energyNeeded()) {
			say(player, "message.projecthero.titan_shifter.low_energy", ChatFormatting.RED,
					(int) Math.ceil(s.energy), (int) Math.ceil(energyNeeded()));
			return false;
		}
		if (player.isSpectator() || player.isSleeping() || player.isPassenger() || !player.isAlive()) {
			say(player, "message.projecthero.titan_shifter.cannot_now", ChatFormatting.RED);
			return false;
		}
		ServerLevel level = (ServerLevel) player.level();
		TitanType type = TitanType.byId(s.typeId);
		TitanFormEntity form = TitanShifterEntities.TITAN_FORM.create(level);
		if (form == null) {
			return false;
		}
		form.bind(player, type);
		if (!placeForm(level, form, player)) {
			form.discard();
			say(player, "message.projecthero.titan_shifter.no_room", ChatFormatting.RED);
			return false;
		}
		form.setFormState(TitanFormEntity.FORM_TRANSFORMING);
		if (!level.addFreshEntity(form)) {
			form.discard();
			return false;
		}
		if (!player.startRiding(form, true)) {
			form.discard();
			say(player, "message.projecthero.titan_shifter.cannot_now", ChatFormatting.RED);
			return false;
		}
		player.fallDistance = 0.0f;
		player.clearFire();

		TitanShifterState next = state(player).copy();
		next.titanHealth = form.getHealth();
		next.titanMaxHealth = form.getMaxHealth();
		next.regenUntil = 0L;
		next.hardenUntil = 0L;
		save(player, next);
		setPhase(player, TitanPhase.TRANSFORMING, now + TitanShifterConfig.transformation().transformTicks);

		transformFx(level, player, form);
		return true;
	}

	/** Finds a free spot for the Titan's hit-box near the player, clearing foliage first. */
	private static boolean placeForm(ServerLevel level, TitanFormEntity form, ServerPlayer player) {
		double[][] offsets = { { 0, 0 }, { 1.5, 0 }, { -1.5, 0 }, { 0, 1.5 }, { 0, -1.5 }, { 1.5, 1.5 }, { -1.5, 1.5 },
				{ 1.5, -1.5 }, { -1.5, -1.5 }, { 3, 0 }, { -3, 0 }, { 0, 3 }, { 0, -3 } };
		for (double dy : new double[] { 0.0, 1.0 }) {
			for (double[] o : offsets) {
				form.moveTo(player.getX() + o[0], player.getY() + dy, player.getZ() + o[1], player.getYRot(), 0.0f);
				form.setYBodyRot(player.getYRot());
				form.setYHeadRot(player.getYRot());
				if (TitanShifterConfig.transformation().clearWeakBlocksOnTransform) {
					// only clear when this spot is otherwise clear of solid blocks
					var box = form.getBoundingBox();
					if (level.noCollision(form, box)) {
						return true;
					}
					if (blockedOnlyByFoliage(level, box)) {
						TitanCombat.breakWeakBlocks(level, box, 600);
						if (level.noCollision(form, form.getBoundingBox())) {
							return true;
						}
					}
				} else if (level.noCollision(form, form.getBoundingBox())) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean blockedOnlyByFoliage(ServerLevel level, net.minecraft.world.phys.AABB box) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
			for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY); y++) {
				for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
					pos.set(x, y, z);
					var state = level.getBlockState(pos);
					if (!state.getCollisionShape(level, pos).isEmpty() && !TitanCombat.isFoliage(state)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private static void lightning(ServerLevel level, Vec3 at) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true); // no fire, no damage, no terrain
			level.addFreshEntity(bolt);
		}
	}

	private static void transformFx(ServerLevel level, ServerPlayer player, TitanFormEntity form) {
		Vec3 p = form.position();
		lightning(level, p);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 0.5f);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 3.0f, 0.45f);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 1.0, p.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.FLASH, p.x, p.y + 2.0, p.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 4.0, p.z, 120, 1.6, 4.0, 1.6, 0.4);
		level.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 1.0, p.z, 60, 1.8, 0.6, 1.8, 0.15);
		form.steamBurst(level, 8);
		// a shockwave that shoves bystanders clear (no damage) so nobody is standing inside the Titan
		for (LivingEntity e : AbilityHelpers.living(level, p, 6.0, x -> x != player && x != form)) {
			AbilityHelpers.knockbackFrom(e, p, 1.2);
		}
		TitanCombat.shake(level, p, 1.1f, TitanShifterConfig.transformation().transformTicks);
		player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.transforming")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
	}

	// ---------------- reversion ----------------

	/** Manual reversion: the Titan steams away over {@code revertTicks}, then the shifter is human again. */
	public static void revert(ServerPlayer player) {
		TitanShifterState s = state(player);
		TitanFormEntity form = formOf(player);
		if (s.phase() != TitanPhase.TITAN || form == null) {
			return;
		}
		long now = player.level().getGameTime();
		setPhase(player, TitanPhase.REVERTING, now + TitanShifterConfig.transformation().revertTicks);
		form.setFormState(TitanFormEntity.FORM_REVERTING);
		form.setHardened(false);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 p = form.position();
		level.playSound(null, p.x, p.y, p.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 2.5f, 0.5f);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 3.0f, 0.5f);
		form.steamBurst(level, 10);
		TitanCombat.shake(level, p, 0.5f, 20);
	}

	private static void finishRevert(ServerPlayer player, TitanFormEntity form) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 p = form.position();
		release(player, form);
		steamPoof(level, p, form.getBbHeight());
		level.playSound(null, p.x, p.y, p.z, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 2.0f, 0.5f);
		finishToHuman(player, TitanPhase.HUMAN, 0);
	}

	// ---------------- defeat ----------------

	/** Called by the Titan when its health hits zero. */
	public static void onTitanDefeated(ServerPlayer player, TitanFormEntity form) {
		TitanShifterState s = state(player);
		if (s.phase() != TitanPhase.TITAN) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		setPhase(player, TitanPhase.DEFEATED, now + TitanShifterConfig.transformation().defeatTicks);
		form.setFormState(TitanFormEntity.FORM_DEFEATED);
		form.setHardened(false);
		Vec3 p = form.position();
		level.playSound(null, p.x, p.y, p.z, SoundEvents.ENDER_DRAGON_DEATH, SoundSource.PLAYERS, 2.5f, 0.55f);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f, 0.4f);
		form.steamBurst(level, 12);
		TitanCombat.shake(level, p, 0.9f, 30);
		player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.defeated")
				.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
	}

	private static void finishDefeat(ServerPlayer player, TitanFormEntity form) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 p = form.position();
		release(player, form);
		steamPoof(level, p, form.getBbHeight());
		var t = TitanShifterConfig.transformation();
		finishToHuman(player, TitanPhase.RECOVERING, t.recoveryTicks);
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t.recoveryTicks, 2, false, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t.recoveryTicks, 1, false, true, true));
	}

	// ---------------- shared ending ----------------

	/** Steps the shifter off the Titan onto the ground where it stood and removes the form without any death logic. */
	private static void release(ServerPlayer player, TitanFormEntity form) {
		Vec3 p = form.position();
		float yaw = form.getYRot();
		SPRINT_HELD.remove(player.getUUID());
		form.releaseHeld();
		player.stopRiding();
		form.discard();
		player.teleportTo((ServerLevel) player.level(), p.x, p.y, p.z, yaw, player.getXRot());
		player.fallDistance = 0.0f;
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;
	}

	private static void finishToHuman(ServerPlayer player, TitanPhase next, int recoveryTicks) {
		long now = player.level().getGameTime();
		TitanShifterState s = state(player).copy();
		TitanPhase cur = s.phase();
		s.phase = (cur.canGoTo(next) ? next : TitanPhase.HUMAN).name();
		s.phaseStartedAt = now;
		s.phaseUntil = now + recoveryTicks;
		s.cooldownUntil = now + TitanShifterConfig.transformation().cooldownTicks;
		s.regenUntil = 0L;
		s.hardenUntil = 0L;
		s.titanHealth = 0f;
		s.energy = 0f; // leaving the Titan always drains the bar; it refills at 1% a second
		save(player, s);
	}

	private static void steamPoof(ServerLevel level, Vec3 p, float height) {
		level.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 1.0, p.z, 50, 1.6, 0.5, 1.6, 0.08);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y + height * 0.5, p.z, 30, 1.5, height * 0.35, 1.5, 0.03);
		level.sendParticles(ParticleTypes.POOF, p.x, p.y + 0.5, p.z, 25, 1.4, 0.4, 1.4, 0.05);
	}

	/**
	 * Cleanly ends whatever Titan state the player is in, immediately: the form is removed, the shifter is
	 * put on the ground where the Titan stood, and the state returns to HUMAN. Used for logout, death,
	 * dimension change, admin commands and any inconsistent state.
	 *
	 * @param withCooldown whether the normal transformation cooldown applies afterwards
	 */
	public static void forceEnd(ServerPlayer player, boolean withCooldown) {
		TitanShifterState s = state(player);
		if (!s.unlocked || (s.phase() == TitanPhase.HUMAN && formOf(player) == null)) {
			return;
		}
		TitanFormEntity form = formOf(player);
		if (form != null) {
			release(player, form);
		} else {
			// a stray form (e.g. the owner changed dimension) is removed by its own watchdog
			player.fallDistance = 0.0f;
		}
		long now = player.level().getGameTime();
		TitanShifterState n = state(player).copy();
		n.phase = TitanPhase.HUMAN.name();
		n.phaseStartedAt = now;
		n.phaseUntil = now;
		n.cooldownUntil = withCooldown ? now + TitanShifterConfig.transformation().cooldownTicks : 0L;
		n.regenUntil = 0L;
		n.hardenUntil = 0L;
		n.titanHealth = 0f;
		n.energy = 0f;
		save(player, n);
	}

	// ---------------- per-tick ----------------

	public static void tick(ServerPlayer player) {
		TitanShifterState s = player.getAttachedOrElse(ModAttachments.TITAN_SHIFTER_STATE, null);
		if (s == null || !s.unlocked) {
			return;
		}
		TitanPhase phase = s.phase();
		if (phase == TitanPhase.HUMAN) {
			tickEnergy(player, s);
			tickBaseFormRegen(player);
			return;
		}
		long now = player.level().getGameTime();
		if (phase == TitanPhase.RECOVERING) {
			tickEnergy(player, s);
			if (now >= s.phaseUntil) {
				setPhase(player, TitanPhase.HUMAN, now);
			}
			return;
		}
		TitanFormEntity form = formOf(player);
		if (form == null || !form.isAlive()) {
			// the Titan or the seat is gone (teleport, dimension change, /ride ...): end cleanly
			forceEnd(player, true);
			return;
		}
		switch (phase) {
			case TRANSFORMING -> {
				if (now >= s.phaseUntil) {
					finishTransform(player, form);
				}
			}
			case TITAN -> tickTitan(player, s, form, now);
			case REVERTING -> {
				if (now >= s.phaseUntil) {
					finishRevert(player, form);
				}
			}
			case DEFEATED -> {
				if (now >= s.phaseUntil) {
					finishDefeat(player, form);
				}
			}
			default -> {
			}
		}
		player.fallDistance = 0.0f;
		if (player.isOnFire()) {
			player.clearFire();
		}
	}

	private static void finishTransform(ServerPlayer player, TitanFormEntity form) {
		ServerLevel level = (ServerLevel) player.level();
		setPhase(player, TitanPhase.TITAN, 0L);
		form.setFormState(TitanFormEntity.FORM_NORMAL);
		Vec3 p = form.position();
		lightning(level, p);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 3.5f, 0.6f);
		level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.5, p.z, 4, 1.5, 0.2, 1.5, 0.0);
		TitanCombat.ring(level, p, 4.0, ParticleTypes.CLOUD, 30);
		TitanCombat.shake(level, p, 0.8f, 20);
		form.steamBurst(level, 8);
		player.displayClientMessage(Component.translatable("message.projecthero.titan_shifter.transformed")
				.withStyle(ChatFormatting.GOLD), true);
	}

	private static void tickTitan(ServerPlayer player, TitanShifterState s, TitanFormEntity form, long now) {
		ServerLevel level = (ServerLevel) player.level();
		var a = TitanShifterConfig.abilities();
		if (now < s.regenUntil) {
			form.heal((float) (a.regenPerSecond / 20.0));
			if (player.tickCount % 6 == 0) {
				Vec3 p = form.position();
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y + form.getBbHeight() * 0.5, p.z, 6,
						form.getBbWidth() * 0.5, form.getBbHeight() * 0.35, form.getBbWidth() * 0.5, 0.0);
				form.steamBurst(level, 2);
			}
		}
		if (player.tickCount % 10 == 0) {
			redirectMobs(level, player, form);
		}
		if (form.isHardened() && now >= s.hardenUntil) {
			form.setHardened(false);
			level.playSound(null, form.getX(), form.getY(), form.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK,
					SoundSource.PLAYERS, 2.0f, 0.5f);
		}
		if (player.tickCount % 10 == 0) {
			float hp = form.getHealth();
			float max = form.getMaxHealth();
			if (Math.abs(hp - s.titanHealth) >= 1.0f || Math.abs(max - s.titanMaxHealth) >= 1.0f) {
				TitanShifterState n = s.copy();
				n.titanHealth = hp;
				n.titanMaxHealth = max;
				save(player, n);
			}
		}
	}

	/**
	 * v0.12.34 -- mobs that were hunting the shifter cannot reach a rider sitting 10 blocks up, so they are re-aimed at the
	 * Titan itself (whose hit-box they can reach); its hurt() then lets their hits through armour.
	 */
	private static void redirectMobs(ServerLevel level, ServerPlayer player, TitanFormEntity form) {
		var box = form.getBoundingBox().inflate(28.0, 6.0, 28.0);
		for (net.minecraft.world.entity.Mob mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
				m -> m.getTarget() == player)) {
			mob.setTarget(form);
		}
	}

	/** Outside the Titan the bar refills: {@code regenPerSecond} (1%) every second, in whole steps so it syncs rarely. */
	private static void tickEnergy(ServerPlayer player, TitanShifterState s) {
		var e = TitanShifterConfig.energy();
		if (player.level().getGameTime() % 20L != 0L || s.energy >= e.max) {
			return;
		}
		TitanShifterState n = s.copy();
		n.energy = (float) Math.min(e.max, s.energy + e.regenPerSecond);
		save(player, n);
	}

	/**
	 * v0.12.34 -- the passive regeneration belongs to the shifter's BASE (human) form only: a short, hidden Regeneration
	 * effect kept topped up while the phase is HUMAN. Inside the Titan the Titan has no passive regeneration at all
	 * (only the C ability heals it), and the effect is simply not renewed, so it lapses within two seconds of shifting.
	 */
	private static void tickBaseFormRegen(ServerPlayer player) {
		int amp = TitanShifterConfig.energy().baseFormRegenAmplifier;
		if (amp < 0) {
			return;
		}
		MobEffectInstance cur = player.getEffect(MobEffects.REGENERATION);
		if (cur == null || (!cur.isInfiniteDuration() && cur.getAmplifier() <= amp && cur.getDuration() <= 20)) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, amp, false, false, false));
		}
	}

	// ---------------- lifecycle hooks ----------------

	/** Reconnect: a Titan never survives a logout, so put the shifter back on the ground as a human. */
	public static void onPlayerJoin(ServerPlayer player) {
		TitanShifterState s = state(player);
		if (!s.unlocked || (s.phase() == TitanPhase.HUMAN && s.titanHealth == 0f)) {
			return;
		}
		if (s.phase().insideForm() || s.phase() == TitanPhase.TITAN) {
			snapToGround(player);
		}
		long now = player.level().getGameTime();
		TitanShifterState n = s.copy();
		n.phase = TitanPhase.HUMAN.name();
		n.phaseUntil = now;
		n.regenUntil = 0L;
		n.hardenUntil = 0L;
		n.titanHealth = 0f;
		n.energy = 0f;
		// game time is per-world: keep a sane cooldown that cannot exceed the configured one
		n.cooldownUntil = Math.min(n.cooldownUntil, now + TitanShifterConfig.transformation().cooldownTicks);
		n.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > now + 20L * 60L * 5L);
		save(player, n);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		TitanShifterState s = state(player);
		if (!s.unlocked) {
			return;
		}
		TitanShifterState n = s.copy();
		n.phase = TitanPhase.HUMAN.name();
		n.phaseUntil = 0L;
		n.cooldownUntil = 0L;
		n.regenUntil = 0L;
		n.hardenUntil = 0L;
		n.titanHealth = 0f;
		n.abilityReadyAt.clear();
		save(player, n);
	}

	/** Drop straight down to the first solid ground below (used after a logout from inside a Titan). */
	private static void snapToGround(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(player.getBlockX(), player.getBlockY(), player.getBlockZ());
		for (int i = 0; i < 40 && pos.getY() > level.getMinBuildHeight(); i++) {
			if (!level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
				player.teleportTo(level, player.getX(), pos.getY(), player.getZ(), player.getYRot(), player.getXRot());
				player.fallDistance = 0.0f;
				return;
			}
			pos.move(0, -1, 0);
		}
	}

	public static void clearTransient(ServerPlayer player) {
		LAST_MESSAGE.remove(player.getUUID());
		forceEnd(player, true);
	}

	public static void onServerStopping(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			forceEnd(p, true);
		}
	}
}
