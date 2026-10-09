package com.projecthero.mod.symbiote;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.punisher.Punisher;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.11: <b>Agent Venom</b> -- the Symbiote on a bonded Punisher ({@link SymbioteHostType#AGENT_VENOM}).
 * The Punisher keeps his whole kit (R/G/X/Z/V/C, the guns, the satchel); the suit adds Symbiote extras on
 * top the same way the Black Suit does for Spider-Man -- <em>sneak-modified variants</em> of his existing
 * keys, never new keybinds. Everything here needs the suit on (H).
 *
 * <ul>
 *   <li><b>Sneak + X (Tactical Roll's key; replaces Tactical Advance while suited)</b> -- Tendril Swing: a tendril shoots up to 36 blocks at the
 *       block you are aiming at and hauls you to it; no fall damage for 3 s after.</li>
 *   <li><b>Sneak + Z (Frag Grenade's key; replaces Warzone while suited)</b> -- Tendril Snatch: yank the mob you are aiming at
 *       (18 blocks) to you, bound (Slowness III), and rip the weapon out of its hand.</li>
 *   <li><b>Sneak + V (the weapon-ability key)</b> -- Symbiote Unleashed: 10 s of +50% melee, +20% speed and
 *       20% life steal on punches (v0.13.21: was 30%).</li>
 * </ul>
 *
 * <p>Passives while suited (applied where the numbers live): <b>Symbiote Rounds</b> -- firearm damage +20%
 * and every hit lashes the target with tendrils (brief Slowness) ({@code PunisherPassives.Hooks});
 * <b>Living Ammunition</b> -- the suit feeds the guns: reserve ammo regenerates three times as fast and
 * reloads are 25% quicker ({@code PunisherAmmoReserve}, {@code PunisherPassives.Hooks}); and the suit's
 * own stats ({@link #reconcile}): +25% melee, +15% speed, +15% jump, +15% knockback resistance.
 * v0.13.21 trims how tanky the suit is: its own armour material ({@code ModArmorMaterials#AGENT_VENOM}, 22 armour /
 * 2.5 toughness instead of the Black Suit's 24 / 3), knockback resistance 25% -> 15%, Unleashed life steal
 * 30% -> 20%, and a 20-minute Symbiote revive ({@link SymbioteVitalsManager#HERO_HOST_RESURRECT_COOLDOWN_TICKS}).
 * The Symbiote's weaknesses (fire, lava, sound) and its instincts (wrapping its host at low health) apply
 * exactly as they do to every other host -- see {@link Symbiote#tick}.
 */
public final class SymbioteAgentVenomAbilities {
	/** Cooldown keys in the Punisher's synced {@code abilityReadyAt} map, so the HUD can read them. */
	public static final String TENDRIL_SWING = "agent_venom_tendril_swing";
	public static final String TENDRIL_SNATCH = "agent_venom_tendril_snatch";
	public static final String UNLEASHED = "agent_venom_unleashed";

	/** The three extras in HUD order, with the Punisher key each rides on. */
	public static final String[] ABILITIES = { TENDRIL_SWING, TENDRIL_SNATCH, UNLEASHED };
	public static final AbilitySlot[] KEYED_ON = { AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5 };

	public static final int CD_SWING = 60;       // 3 s
	public static final int CD_SNATCH = 160;     // 8 s
	public static final int CD_UNLEASHED = 900;  // 45 s, counted from activation

	private static final double SWING_RANGE = 36.0;
	private static final int SWING_SAFE_TICKS = 60;
	private static final double SNATCH_RANGE = 18.0;
	private static final float SNATCH_DAMAGE = 5.0f;
	/** Public (v0.13.21) so the HUD can tell the live 10 s of Unleashed apart from the rest of its cooldown. */
	public static final int UNLEASHED_TICKS = 200;
	/** v0.13.21: 30% -> 20%. */
	public static final float LIFE_STEAL = 0.20f;
	/** v0.13.21: the suit's knockback resistance, 25% -> 15%. */
	public static final double KNOCKBACK_RESIST = 0.15;

	/** Firearm damage bonus while suited (Symbiote Rounds). */
	public static final float ROUNDS_DAMAGE_BONUS = 0.20f;
	/** Reserve regen multiplier while suited (Living Ammunition). */
	public static final int AMMO_REGEN_MULTIPLIER = 3;
	/** Reload-time multiplier while suited (Living Ammunition). */
	public static final float RELOAD_FACTOR = 0.75f;

	private static final ResourceLocation ATTACK = ProjectHeroMod.id("agent_venom_attack");
	private static final ResourceLocation SPEED = ProjectHeroMod.id("agent_venom_speed");
	private static final ResourceLocation JUMP = ProjectHeroMod.id("agent_venom_jump");
	private static final ResourceLocation KNOCKBACK = ProjectHeroMod.id("agent_venom_knockback");
	private static final ResourceLocation UNLEASHED_ATTACK = ProjectHeroMod.id("agent_venom_unleashed_attack");
	private static final ResourceLocation UNLEASHED_SPEED = ProjectHeroMod.id("agent_venom_unleashed_speed");

	/** Game time the current Tendril Swing's fall protection ends. Transient. */
	private static final Map<Integer, Long> SWING_SAFE_UNTIL = new ConcurrentHashMap<>();
	/** Game time the current Symbiote Unleashed ends. Transient. */
	private static final Map<Integer, Long> UNLEASHED_UNTIL = new ConcurrentHashMap<>();

	private SymbioteAgentVenomAbilities() {
	}

	/** Life steal on Unleashed punches. Registered once from {@link SymbioteVitalsManager#initialize}. */
	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0f && source.getDirectEntity() instanceof ServerPlayer sp && source.getEntity() == sp
					&& entity != sp && unleashed(sp) && !com.projecthero.mod.squad.Squads.areAllies(sp, entity)) {
				sp.heal(taken * LIFE_STEAL);
			}
		});
	}

	/** Suited up as Agent Venom right now (server or client -- everything read here is synced). */
	public static boolean agentVenom(Player player) {
		return Symbiote.isActive(player) && SymbioteHostType.of(player) == SymbioteHostType.AGENT_VENOM;
	}

	public static boolean unleashed(ServerPlayer player) {
		Long until = UNLEASHED_UNTIL.get(player.getId());
		return until != null && player.level().getGameTime() < until;
	}

	/**
	 * Called from {@code PunisherAbilityManager.handle} before the Punisher kit. Consumes (returns true)
	 * Sneak + X / Z / V while suited as Agent Venom -- press and release alike, so the release never
	 * reaches the ordinary ability either.
	 */
	public static boolean handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		if (!player.isShiftKeyDown() || !agentVenom(player)) {
			return false;
		}
		if (slot != AbilitySlot.SLOT_3 && slot != AbilitySlot.SLOT_4 && slot != AbilitySlot.SLOT_5) {
			return false;
		}
		if (!pressed) {
			return true;
		}
		if (Symbiote.sonicLocked(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.agent_venom.sonic")
					.withStyle(ChatFormatting.RED), true);
			return true;
		}
		switch (slot) {
			case SLOT_3 -> tendrilSwing(player);
			case SLOT_4 -> tendrilSnatch(player);
			case SLOT_5 -> unleash(player);
			default -> {
			}
		}
		return true;
	}

	// ---------------- Tendril Swing ----------------

	private static void tendrilSwing(ServerPlayer player) {
		if (!Punisher.abilityReady(player, TENDRIL_SWING)) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(SWING_RANGE));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			player.displayClientMessage(Component.translatable("message.projecthero.agent_venom.no_anchor")
					.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		Vec3 anchor = hit.getLocation();
		Vec3 to = anchor.subtract(player.position());
		double dist = to.length();
		double speed = Math.min(2.8, 0.9 + dist * 0.07);
		Vec3 v = to.normalize().scale(speed).add(0.0, 0.35, 0.0);
		AbilityHelpers.launchSelf(player, v);
		long now = player.level().getGameTime();
		SWING_SAFE_UNTIL.put(player.getId(), now + SWING_SAFE_TICKS);

		ServerLevel level = AbilityHelpers.level(player);
		Vec3 hand = eye.add(player.getLookAngle().scale(0.6)).add(0, -0.3, 0);
		AbilityHelpers.line(level, hand, anchor, ParticleTypes.SQUID_INK, 3.0);
		AbilityHelpers.burst(level, anchor, ParticleTypes.SQUID_INK, 12, 0.2);
		SymbioteSounds.organic(player, 0.9f, 0.7f);
		Punisher.triggerCooldown(player, TENDRIL_SWING, CD_SWING);
	}

	// ---------------- Tendril Snatch ----------------

	private static void tendrilSnatch(ServerPlayer player) {
		if (!Punisher.abilityReady(player, TENDRIL_SNATCH)) {
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(player, SNATCH_RANGE);
		if (target == null || !target.isAlive() || com.projecthero.mod.squad.Squads.areAllies(player, target)) {
			return; // v0.13.21: never a squadmate
		}
		boolean boss = com.projecthero.mod.titanshifter.TitanCombat.isBoss(target);
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 from = target.position().add(0, target.getBbHeight() * 0.5, 0);
		AbilityHelpers.line(level, SymbioteHands.right(player), from,
				ParticleTypes.SQUID_INK, 3.0);
		AbilityHelpers.hurt(player, target, SNATCH_DAMAGE);
		AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 60, 2);
		if (!boss) {
			Vec3 pull = player.position().subtract(target.position());
			double d = Math.max(1.0, pull.length());
			Vec3 v = pull.normalize().scale(Math.min(1.8, 0.5 + d * 0.09)).add(0, 0.35, 0);
			target.setDeltaMovement(v);
			target.hurtMarked = true;
			disarm(player, target);
		}
		AbilityHelpers.burst(level, from, ParticleTypes.SQUID_INK, 16, 0.3);
		SymbioteSounds.organic(player, 1.0f, 0.5f);
		Punisher.triggerCooldown(player, TENDRIL_SNATCH, CD_SNATCH);
	}

	/** The tendril tears whatever a mob is holding out of its hand and drops it at the Punisher's feet. Never another player's. */
	private static void disarm(ServerPlayer player, LivingEntity target) {
		if (!(target instanceof Mob mob)) {
			return;
		}
		ItemStack held = mob.getMainHandItem();
		if (held.isEmpty()) {
			return;
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), held.copy());
		drop.setDefaultPickUpDelay();
		player.level().addFreshEntity(drop);
		player.displayClientMessage(Component.translatable("message.projecthero.agent_venom.disarmed",
				held.getHoverName()).withStyle(ChatFormatting.DARK_GRAY), true);
	}

	// ---------------- Symbiote Unleashed ----------------

	private static void unleash(ServerPlayer player) {
		if (!Punisher.abilityReady(player, UNLEASHED) || unleashed(player)) {
			return;
		}
		long now = player.level().getGameTime();
		UNLEASHED_UNTIL.put(player.getId(), now + UNLEASHED_TICKS);
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, UNLEASHED_ATTACK, 0.5,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, UNLEASHED_SPEED, 0.2,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		Punisher.triggerCooldown(player, UNLEASHED, CD_UNLEASHED);
		ServerLevel level = AbilityHelpers.level(player);
		AbilityHelpers.burst(level, player.position().add(0, 1, 0), ParticleTypes.SQUID_INK, 50, 0.6);
		AbilityHelpers.sound(player, SoundEvents.WARDEN_ROAR, 0.7f, 1.4f);
		SymbioteSounds.organic(player, 1.2f, 0.4f);
		SymbioteDialogue.say(player, "agent_venom_unleashed");
	}

	private static void endUnleashed(ServerPlayer player) {
		UNLEASHED_UNTIL.remove(player.getId());
		PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, UNLEASHED_ATTACK);
		PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, UNLEASHED_SPEED);
	}

	// ---------------- suit stats ----------------

	/** The suit's own stat changes -- present only while suited as Agent Venom. Idempotent (fixed ids). */
	public static void reconcile(ServerPlayer player) {
		if (agentVenom(player)) {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, JUMP, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, KNOCKBACK_RESIST, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, JUMP);
			PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK);
		}
	}

	/** Symbiote Rounds: a bullet hit lashes the target with tendrils. Called from the Punisher's firearm hook. */
	public static void onBulletHit(ServerPlayer player, LivingEntity target) {
		AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 30, 0);
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SQUID_INK, target.getX(), target.getY() + target.getBbHeight() * 0.5,
					target.getZ(), 5, 0.2, 0.25, 0.2, 0.01);
		}
	}

	// ---------------- tick + lifecycle ----------------

	/** Every player, every tick (from {@code AbilityRouter.serverTick}); cheap map lookups when idle. */
	public static void serverTick(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long safe = SWING_SAFE_UNTIL.get(player.getId());
		if (safe != null) {
			if (now >= safe || (player.onGround() && now > safe - SWING_SAFE_TICKS + 5)) {
				SWING_SAFE_UNTIL.remove(player.getId());
			}
			player.resetFallDistance();
		}
		Long until = UNLEASHED_UNTIL.get(player.getId());
		if (until != null) {
			if (now >= until || !agentVenom(player)) {
				endUnleashed(player);
			} else if (now % 3L == 0L) {
				AbilityHelpers.level(player).sendParticles(ParticleTypes.SQUID_INK, player.getX(),
						player.getY() + player.getBbHeight() * 0.55, player.getZ(), 3, 0.35, 0.5, 0.35, 0.02);
			}
		}
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
	}

	/** Death / relog / dimension change / power loss. */
	public static void clearFor(ServerPlayer player) {
		SWING_SAFE_UNTIL.remove(player.getId());
		endUnleashed(player);
		reconcile(player);
	}

	/** Server-stop cleanup, same discipline as every other static session map. */
	public static void clearSessionState() {
		SWING_SAFE_UNTIL.clear();
		UNLEASHED_UNTIL.clear();
	}
}
