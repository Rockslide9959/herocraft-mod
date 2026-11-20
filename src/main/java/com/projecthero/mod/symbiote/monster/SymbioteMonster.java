package com.projecthero.mod.symbiote.monster;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteAbilityManager;
import com.projecthero.mod.symbiote.SymbioteAnim;
import com.projecthero.mod.symbiote.SymbioteSounds;
import com.projecthero.mod.symbiote.SymbioteState;
import com.projecthero.mod.symbiote.SymbioteVfx;
import com.projecthero.mod.symbiote.SymbioteVitals;
import com.projecthero.mod.symbiote.SymbioteVitalsManager;
import com.projecthero.mod.symbiote.entity.SymbioteTendrilEntity;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Symbiote Monster form -- the Normal host's Shift+Z ultimate (replaces Onslaught; user: "make a new and better ultimate for
 * symbiotes shift+Z ... hold shift+Z for 3 seconds and then they transform into a monster form ... tendrils erupt from the players
 * feet and circle them and then create almost a cocoon, coccon grows to 7 blocks and player model changes into a hulking monster
 * symbiote form ... this form lasts 40 seconds").
 *
 * <pre>
 *   charge   Shift + hold Z 3 s (rooted, the mouth roars, the HUD box fills); let go early = cancelled, no cooldown.
 *            Needs 50 Biomass (spent when the charge completes) and room for a 7-block body; 4-minute cooldown (list entry 17)
 *            starting when the form ends.
 *   COCOON   2.5 s, invulnerable: tendrils erupt from the ground round the feet, spiral up faster and faster and weave a cocoon
 *            that swells to 7 blocks (the hitbox grows with it), beats like a heart and bursts -- tendrils whip out, everything
 *            within 4 blocks is thrown back, a roar.
 *   MONSTER  40 s: 7 blocks (scale attribute), +100% max health (healed in), 50% damage reduction, no knockback, a jump boost,
 *            walking 15% slower (sprinting normal), +3 reach, +8 claw damage. The normal kit is locked; R G X Z C V are the
 *            monster's moves ({@link SymbioteMonsterMoves}); Shift+Z ends it early.
 *   MELT     1.5 s, invulnerable: the monster melts back down into goo and the host re-forms in the suit.
 * </pre>
 * Also ends (at once, no melt) on death, a dimension change / relog ({@code Symbiote.clearTransient}), losing the suit or the kit, and
 * a server stop (the attachment is not saved; {@link #clearSessionState} drops the move maps). The attributes are reconciled from
 * the synced {@link SymbioteMonsterState} every tick ({@link #reconcile}), so nothing can be left on a player.
 */
public final class SymbioteMonster {
	public static final int CHARGE_TICKS = 60;
	public static final float BIOMASS_COST = 50.0f;
	public static final int COOLDOWN_TICKS = 4800;
	/** The form's cooldown entry in {@code SymbioteState.abilityCooldowns} ({@code COOLDOWN_SLOTS} 18). */
	public static final int CDI_MONSTER = 17;
	public static final int COCOON_TICKS = 50;
	public static final int FORM_TICKS = 800;
	public static final int MELT_TICKS = 30;
	/** The monster's height in blocks, and the scale bonus that makes a suited host (1.5x, 2.7 blocks) that tall. */
	public static final double HEIGHT = 7.0;
	public static final double SCALE_BONUS = HEIGHT / 1.8 - 1.5;
	public static final double BURST_RADIUS = 4.0;
	public static final float DAMAGE_FACTOR = 0.5f;

	private static final ResourceLocation SCALE_ID = ProjectHeroMod.id("symbiote_monster_scale");
	private static final ResourceLocation HEALTH_ID = ProjectHeroMod.id("symbiote_monster_health");
	private static final ResourceLocation KNOCKBACK_ID = ProjectHeroMod.id("symbiote_monster_knockback");
	private static final ResourceLocation SPEED_ID = ProjectHeroMod.id("symbiote_monster_speed");
	private static final ResourceLocation JUMP_ID = ProjectHeroMod.id("symbiote_monster_jump");
	private static final ResourceLocation STEP_ID = ProjectHeroMod.id("symbiote_monster_step");
	private static final ResourceLocation REACH_ID = ProjectHeroMod.id("symbiote_monster_reach");
	private static final ResourceLocation BLOCK_REACH_ID = ProjectHeroMod.id("symbiote_monster_block_reach");
	private static final ResourceLocation ATTACK_ID = ProjectHeroMod.id("symbiote_monster_attack");

	private SymbioteMonster() {
	}

	public static void registerEvents() {
		// touching the TYPE registers the attachment (common init, both sides)
		ProjectHeroMod.LOGGER.debug("[ProjectHero] symbiote monster attachment {}", SymbioteMonsterState.TYPE.identifier());
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer p)) {
				return true;
			}
			int phase = state(p).phase();
			if ((phase == SymbioteMonsterState.COCOON || phase == SymbioteMonsterState.MELT)
					&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return false; // the transformation and the melt-back are untouchable
			}
			return !(phase == SymbioteMonsterState.MONSTER && source.is(DamageTypes.IN_WALL)); // a 7-block body under a low roof
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && (state(p).inForm() || Symbiote.state(p).onslaughtChargeStart >= 0)) {
				endNow(p, null);
			}
		});
	}

	// ------------------------------------------------------------------------------------------------ state

	public static SymbioteMonsterState state(net.minecraft.world.entity.player.Player player) {
		return player.getAttachedOrElse(SymbioteMonsterState.TYPE, SymbioteMonsterState.EMPTY);
	}

	public static void setState(ServerPlayer player, SymbioteMonsterState s) {
		player.setAttached(SymbioteMonsterState.TYPE, s);
	}

	/** In the cocoon, the monster or the melt (the normal kit is locked out). */
	public static boolean inForm(net.minecraft.world.entity.player.Player player) {
		return state(player).inForm();
	}

	public static boolean isMonster(net.minecraft.world.entity.player.Player player) {
		return state(player).phase() == SymbioteMonsterState.MONSTER;
	}

	/** {@code SymbioteDamageRules}: the monster takes half damage. */
	public static float damageFactor(ServerPlayer player) {
		return isMonster(player) ? DAMAGE_FACTOR : 1.0f;
	}

	/** Can Shift+Z start the charge right now (cooldown, Biomass, not already transformed)? Also the Symbiote's voice lines. */
	public static boolean ready(ServerPlayer player, long now) {
		SymbioteState s = Symbiote.state(player);
		return !inForm(player) && s.onslaughtChargeStart < 0 && now >= cooldownReadyAt(player)
				&& SymbioteVitalsManager.biomass(player) >= BIOMASS_COST;
	}

	public static long cooldownReadyAt(ServerPlayer player) {
		SymbioteState s = Symbiote.state(player);
		return CDI_MONSTER < s.abilityCooldowns.size() ? s.abilityCooldowns.get(CDI_MONSTER) : 0L;
	}

	// ------------------------------------------------------------------------------------------------ the charge (Shift + hold Z)

	/** Shift+Z down (starts the charge) or Z up (a release before 3 s cancels it for free). From {@code SymbioteAbilityManager.handle}. */
	public static void handleCharge(ServerPlayer player, boolean pressed, long now) {
		SymbioteState s = Symbiote.state(player);
		if (pressed) {
			if (s.onslaughtChargeStart >= 0) {
				return;
			}
			if (!SymbioteVitalsManager.usable(player)) {
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.spent"), true);
				return;
			}
			long readyAt = cooldownReadyAt(player);
			if (now < readyAt) {
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.monster.cooldown",
						String.format(java.util.Locale.ROOT, "%d:%02d", (readyAt - now + 19) / 20 / 60, (readyAt - now + 19) / 20 % 60))
						.withStyle(ChatFormatting.GRAY), true);
				return;
			}
			if (SymbioteVitalsManager.biomass(player) < BIOMASS_COST) {
				player.displayClientMessage(Component.translatable("message.projecthero.symbiote.monster.no_biomass",
						Math.round(BIOMASS_COST)).withStyle(ChatFormatting.RED), true);
				return;
			}
			SymbioteState c = s.copy();
			c.onslaughtChargeStart = now;
			c.shieldHeld = false;
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
			SymbioteAnim.play(player, SymbioteAnim.ONSLAUGHT_CHARGE); // the charge pose + the Lethal Protector's held roar
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.monster.charging")
					.withStyle(ChatFormatting.DARK_PURPLE), true);
			AbilityHelpers.sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.5f);
			AbilityHelpers.sound(player, SoundEvents.WARDEN_AGITATED, 0.9f, 0.6f);
		} else if (s.onslaughtChargeStart >= 0 && now - s.onslaughtChargeStart < CHARGE_TICKS) {
			cancelCharge(player, "message.projecthero.symbiote.monster.cancelled");
		}
	}

	public static void cancelCharge(ServerPlayer player, String messageKey) {
		SymbioteState s = Symbiote.state(player);
		if (s.onslaughtChargeStart < 0) {
			return;
		}
		SymbioteState c = s.copy();
		c.onslaughtChargeStart = -1L;
		player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		SymbioteAnim.stop(player, SymbioteAnim.ONSLAUGHT_CHARGE);
		if (messageKey != null) {
			player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GRAY), true);
		}
	}

	/** Every tick while charging: heartbeats quickening, tendrils clawing up round the feet; at 3 s the transformation begins. */
	public static void tickCharge(ServerPlayer player, long now) {
		SymbioteState s = Symbiote.state(player);
		if (s.onslaughtChargeStart < 0) {
			return;
		}
		long held = now - s.onslaughtChargeStart;
		if (held >= CHARGE_TICKS) {
			completeCharge(player, now);
			return;
		}
		player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
		float frac = held / (float) CHARGE_TICKS;
		int beat = Math.max(4, 14 - Math.round(frac * 10));
		if (held % beat == 0) {
			AbilityHelpers.sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.5f + frac * 0.6f);
		}
		if (held == 30) {
			AbilityHelpers.sound(player, SoundEvents.WARDEN_ROAR, 0.6f, 1.3f);
		}
	}

	private static void completeCharge(ServerPlayer player, long now) {
		cancelCharge(player, null);
		if (SymbioteVitalsManager.biomass(player) < BIOMASS_COST) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.monster.no_biomass",
					Math.round(BIOMASS_COST)).withStyle(ChatFormatting.RED), true);
			return;
		}
		if (!hasRoom(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.monster.no_room")
					.withStyle(ChatFormatting.RED), true);
			return;
		}
		SymbioteVitals c = SymbioteVitalsManager.vitals(player).copy();
		c.hp = Math.max(0.0f, c.hp - BIOMASS_COST);
		player.setAttached(ModAttachments.SYMBIOTE_VITALS, c);
		beginCocoon(player, now);
	}

	/** Is there room above the host for the 7-block body (the full-grown hitbox, blocks only)? */
	public static boolean hasRoom(ServerPlayer player) {
		double half = 0.3 * (1.5 + SCALE_BONUS);
		Vec3 p = player.position();
		AABB box = new AABB(p.x - half, p.y + 0.01, p.z - half, p.x + half, p.y + HEIGHT, p.z + half);
		return player.level().noCollision(player, box);
	}

	// ------------------------------------------------------------------------------------------------ the phases

	public static void beginCocoon(ServerPlayer player, long now) {
		SymbioteAbilityManager.clearFor(player); // drop anything in flight (a grab, the spear, a dive...)
		SymbioteMonsterMoves.clear(player);
		setState(player, state(player).withPhase(SymbioteMonsterState.COCOON, now));
		SymbioteVfx.groundBurst(player, player.position(), 1.6f);
		AbilityHelpers.sound(player, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.0f, 0.5f);
		SymbioteSounds.organic(player, 1.4f, 0.4f);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.monster.transforming")
				.withStyle(ChatFormatting.DARK_PURPLE), true);
	}

	/** The cocoon bursts: the monster is out. Also the test hook that skips the cocoon. */
	public static void burst(ServerPlayer player, long now) {
		setState(player, state(player).withPhase(SymbioteMonsterState.MONSTER, now).withMove(SymbioteMonsterState.MOVE_NONE, now));
		reconcile(player);
		player.heal(player.getMaxHealth() * 0.5f); // the +100% comes in full
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 c = player.position();
		for (LivingEntity e : SymbioteAbilityManager.enemiesAround(player, c, BURST_RADIUS + 1.2)) {
			if (SymbioteAbilityManager.isBoss(e)) {
				continue;
			}
			Vec3 away = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
			e.setDeltaMovement(away.scale(1.5).add(0, 0.55, 0));
			e.hurtMarked = true;
			e.hasImpulse = true;
			if (e instanceof ServerPlayer sp) {
				sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
			}
		}
		// tendrils whip outward from the burst shell
		for (int i = 0; i < 14; i++) {
			double a = Math.PI * 2 * i / 14 + player.getRandom().nextDouble() * 0.3;
			Vec3 from = c.add(Math.cos(a) * 1.2, 1.5 + (i % 4) * 1.1, Math.sin(a) * 1.2);
			Vec3 to = c.add(Math.cos(a) * (BURST_RADIUS + 1.5), 0.3 + (i % 3) * 0.8, Math.sin(a) * (BURST_RADIUS + 1.5));
			SymbioteTendrilEntity.fromPoint(player, from, to, null, 16, 3, 0.26f);
		}
		SymbioteVfx.groundBurst(player, c, (float) BURST_RADIUS + 0.5f);
		SymbioteVfx.bodyBurst(player, 2.4f);
		AbilityHelpers.sound(player, SoundEvents.WARDEN_ROAR, 2.0f, 0.65f);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 1.6f, 0.5f);
		AbilityHelpers.sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 0.7f);
		AbilityHelpers.sound(player, SoundEvents.SLIME_BLOCK_BREAK, 1.5f, 0.5f);
		SymbioteSounds.lash(player, 1.6f, 0.5f);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION, c.x, c.y + 2.5, c.z, 1, 0, 0, 0, 0);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.monster.unleashed")
				.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
	}

	/** The form is over (40 s, or Shift+Z): melt back down into the suit. The cooldown starts now. */
	public static void beginMelt(ServerPlayer player, long now) {
		SymbioteMonsterMoves.clear(player);
		setState(player, state(player).withPhase(SymbioteMonsterState.MELT, now));
		startCooldown(player, now);
		reconcile(player);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
		AbilityHelpers.sound(player, SoundEvents.HONEY_BLOCK_SLIDE, 1.5f, 0.5f);
		AbilityHelpers.sound(player, SoundEvents.WARDEN_DEATH, 0.7f, 1.4f);
		SymbioteSounds.organic(player, 1.4f, 0.35f);
	}

	private static void finishMelt(ServerPlayer player) {
		setState(player, SymbioteMonsterState.EMPTY.withPhase(SymbioteMonsterState.NONE, 0L));
		reconcile(player);
		SymbioteVfx.bodyBurst(player, 1.0f);
		AbilityHelpers.sound(player, SoundEvents.SCULK_CLICKING, 1.0f, 0.8f);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.monster.ended").withStyle(ChatFormatting.GRAY), true);
	}

	/** Ends everything at once (death, dimension change, lost suit, test cleanup): no melt; a form that was running starts its cooldown. */
	public static void endNow(ServerPlayer player, String messageKey) {
		cancelCharge(player, null);
		SymbioteMonsterMoves.clear(player);
		boolean was = inForm(player);
		if (was) {
			if (state(player).phase() != SymbioteMonsterState.MELT || cooldownReadyAt(player) <= player.level().getGameTime()) {
				startCooldown(player, player.level().getGameTime());
			}
			setState(player, SymbioteMonsterState.EMPTY);
		}
		reconcile(player);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
		if (was && messageKey != null) {
			player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.GRAY), true);
		}
	}

	private static void startCooldown(ServerPlayer player, long now) {
		SymbioteState c = Symbiote.state(player).copy();
		if (CDI_MONSTER < c.abilityCooldowns.size()) {
			c.abilityCooldowns.set(CDI_MONSTER, now + COOLDOWN_TICKS);
			player.setAttached(ModAttachments.SYMBIOTE_STATE, c);
		}
	}

	/** {@code Symbiote.clearTransient}: a relog / dimension change / power loss ends the form at once. */
	public static void clearTransient(ServerPlayer player) {
		endNow(player, null);
	}

	public static void clearSessionState() {
		SymbioteMonsterMoves.clearAll();
	}

	// ------------------------------------------------------------------------------------------------ per tick

	/** Every tick for every bonded player ({@code SymbioteAbilityManager.serverTick}): the phase clock, the moves, the attributes. */
	public static void tick(ServerPlayer player) {
		SymbioteMonsterState s = state(player);
		long now = player.level().getGameTime();
		if (s.inForm()) {
			if (!Symbiote.isActive(player) || !SymbioteAbilityManager.hasContext(player) || !player.isAlive()) {
				endNow(player, "message.projecthero.symbiote.monster.lost_suit");
				return;
			}
			long age = now - s.phaseStart();
			switch (s.phase()) {
				case SymbioteMonsterState.COCOON -> {
					player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
					player.resetFallDistance();
					cocoonSounds(player, age);
					if (age >= COCOON_TICKS) {
						burst(player, now);
					}
				}
				case SymbioteMonsterState.MONSTER -> {
					player.resetFallDistance();
					if (age >= FORM_TICKS) {
						beginMelt(player, now);
					} else {
						SymbioteMonsterMoves.tick(player, now);
						if (age == FORM_TICKS - 100) {
							player.displayClientMessage(Component.translatable("message.projecthero.symbiote.monster.fading")
									.withStyle(ChatFormatting.GRAY), true);
						}
					}
				}
				case SymbioteMonsterState.MELT -> {
					player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
					if (age >= MELT_TICKS) {
						finishMelt(player);
					}
				}
				default -> {
				}
			}
		}
		reconcile(player);
	}

	private static void cocoonSounds(ServerPlayer player, long age) {
		if (age == 4 || age == 12) {
			AbilityHelpers.sound(player, SoundEvents.SCULK_VEIN_PLACE, 1.4f, 0.5f);
			SymbioteSounds.organic(player, 1.2f, 0.5f);
		}
		if (age == 18) {
			AbilityHelpers.sound(player, SoundEvents.HONEY_BLOCK_SLIDE, 1.5f, 0.4f);
		}
		if (age == 30 || age == 37 || age == 42 || age == 46) {
			AbilityHelpers.sound(player, SoundEvents.WARDEN_HEARTBEAT, 2.0f, 0.5f + (age - 30) * 0.02f);
		}
	}

	/** 0 -> 1 between {@code a} and {@code b} (smoothstep). */
	private static double smooth(double a, double b, double x) {
		double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
		return t * t * (3 - 2 * t);
	}

	/** How much of {@link #SCALE_BONUS} the host has at {@code phase} / {@code age}: grows with the cocoon, shrinks with the melt. */
	public static double scaleFraction(int phase, double age) {
		return switch (phase) {
			case SymbioteMonsterState.COCOON -> smooth(16, 42, age);
			case SymbioteMonsterState.MONSTER -> 1.0;
			case SymbioteMonsterState.MELT -> 1.0 - smooth(4, 26, age);
			default -> 0.0;
		};
	}

	/** Puts exactly the modifiers the current state wants on the player (and nothing else). */
	public static void reconcile(ServerPlayer player) {
		SymbioteMonsterState s = state(player);
		long now = player.level().getGameTime();
		boolean charging = Symbiote.state(player).onslaughtChargeStart >= 0;
		boolean monster = s.phase() == SymbioteMonsterState.MONSTER;
		boolean rooted = charging || s.phase() == SymbioteMonsterState.COCOON || s.phase() == SymbioteMonsterState.MELT;
		double scale = Math.round(SCALE_BONUS * scaleFraction(s.phase(), now - s.phaseStart()) * 200.0) / 200.0;
		double before = player.getScale();
		set(player, Attributes.SCALE, SCALE_ID, scale, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		if (Math.abs(player.getScale() - before) > 1.0e-4) {
			player.refreshDimensions(); // at once, not on the next entity tick (the hitbox and eye height follow the body)
		}
		set(player, Attributes.MAX_HEALTH, HEALTH_ID, monster ? 1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, s.inForm() || charging ? 1.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
		double speed = rooted ? -1.0 : monster && !player.isSprinting() ? -0.15 : 0.0;
		set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (rooted) {
			set(player, Attributes.JUMP_STRENGTH, JUMP_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			set(player, Attributes.JUMP_STRENGTH, JUMP_ID, monster ? 0.32 : 0.0, AttributeModifier.Operation.ADD_VALUE);
		}
		set(player, Attributes.STEP_HEIGHT, STEP_ID, monster ? 1.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, monster ? 3.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, monster ? 2.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, monster ? 8.0 : 0.0, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void set(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {
		if (amount == 0.0) {
			var inst = player.getAttribute(attribute);
			if (inst != null && inst.getModifier(id) != null) {
				PowerToggles.clearModifier(player, attribute, id);
			}
		} else {
			PowerToggles.modifier(player, attribute, id, amount, op);
		}
	}
}
