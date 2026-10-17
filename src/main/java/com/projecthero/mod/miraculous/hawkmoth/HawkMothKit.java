package com.projecthero.mod.miraculous.hawkmoth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.KwamiKit;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.hawkmoth.network.HawkMothLinkPayload;
import com.projecthero.mod.miraculous.hawkmoth.network.HawkMothSensePayload;
import com.projecthero.mod.miraculous.hawkmoth.network.HawkMothSwarmPayload;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: <b>Hawk Moth</b>'s six keys (Nooroo, the Butterfly Miraculous). Numbers: {@link HawkMothConfig}. What the
 * akumas do once they land: {@link HawkMothAkumas}.
 *
 * <pre>
 *   R  Cane Strike (3-hit combo)          Shift+R  Cane Sword (draw the blade, lunge)
 *   G  Akuma (a mob fights for you)       Shift+G  Recall (every akuma home, cooldown back)
 *   X  Butterfly Swarm Flight             Shift+X  Swarm Dash (a cloud of butterflies)
 *   Z  Butterfly Shield (hold)            Shift+Z  RISE, AKUMA! (a Champion; starts the countdown)
 *   V  Swarm Blind                        Shift+V  Hawk Moth's Window (see through an akuma's eyes)
 *   C  Emotion Sense                      Shift+C  Akuma Link (a target for the mobs / a word to the Champion)
 * </pre>
 *
 * Synced kit values ({@code vals} / {@code vecs}, prefix {@code hm_}) carry everything the other clients draw: the combo
 * step, the blade, the flight, the dash, the shield, the Rise moment, the window and the sense.
 */
public final class HawkMothKit implements KwamiKit {
	// ability ids
	public static final String CANE_STRIKE = "hawk_moth_cane_strike";
	public static final String CANE_SWORD = "hawk_moth_cane_sword";
	public static final String AKUMA = "hawk_moth_akuma";
	public static final String RECALL = "hawk_moth_recall";
	public static final String SWARM_FLIGHT = "hawk_moth_swarm_flight";
	public static final String SWARM_DASH = "hawk_moth_swarm_dash";
	public static final String SHIELD = "hawk_moth_butterfly_shield";
	public static final String RISE = "hawk_moth_rise_akuma";
	public static final String BLIND = "hawk_moth_swarm_blind";
	public static final String WINDOW = "hawk_moth_window";
	public static final String SENSE = "hawk_moth_emotion_sense";
	public static final String LINK = "hawk_moth_akuma_link";

	// animation ids (80..99)
	public static final int ANIM_STRIKE_1 = 80;
	public static final int ANIM_STRIKE_2 = 81;
	public static final int ANIM_STRIKE_3 = 82;
	public static final int ANIM_SWORD = 83;
	public static final int ANIM_AKUMA = 84;
	public static final int ANIM_RECALL = 85;
	public static final int ANIM_DASH = 86;
	public static final int ANIM_RISE = 87;
	public static final int ANIM_BLIND = 88;
	public static final int ANIM_SENSE = 89;
	public static final int ANIM_LINK = 90;

	// synced kit values
	public static final String V_COMBO_STEP = "hm_combo_step";
	public static final String V_COMBO_AT = "hm_combo_at";
	/** Cane Sword: the draw started (game time); the blade goes back in at {@link #V_SWORD_END}. */
	public static final String V_SWORD_AT = "hm_sword_at";
	public static final String V_SWORD_END = "hm_sword_end";
	public static final String V_FLIGHT_AT = "hm_flight_at";
	public static final String V_FLIGHT_UNTIL = "hm_flight_until";
	public static final String V_DASH_AT = "hm_dash_at";
	public static final String V_SHIELD_AT = "hm_shield_at";
	public static final String V_RISE_AT = "hm_rise_at";
	public static final String V_RISE_TARGET = "hm_rise_target";
	public static final String V_WINDOW_MOB = "hm_window_mob";
	public static final String V_WINDOW_UNTIL = "hm_window_until";
	public static final String V_SENSE_UNTIL = "hm_sense_until";
	public static final String VEC_DASH_FROM = "hm_dash_from";
	public static final String VEC_DASH_TO = "hm_dash_to";
	public static final String VEC_LUNGE_DIR = "hm_lunge_dir";

	private static final ResourceLocation WINDOW_SPEED_ID = PowerToggles.id("hawk_moth_window_speed");
	private static final ResourceLocation WINDOW_JUMP_ID = PowerToggles.id("hawk_moth_window_jump");

	/** Per player: what the current lunge already cut. */
	private static final Map<UUID, Set<Integer>> LUNGE_HITS = new ConcurrentHashMap<>();
	/** Per player: Hawk Moth's own flight is on (so only that is taken back off). */
	private static final Set<UUID> FLYING = ConcurrentHashMap.newKeySet();

	// ---------------------------------------------------------------- KwamiKit

	@Override
	public String[] idsOf(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> new String[] { CANE_STRIKE, CANE_SWORD };
			case SLOT_2 -> new String[] { AKUMA, RECALL };
			case SLOT_3 -> new String[] { SWARM_FLIGHT, SWARM_DASH };
			case SLOT_4 -> new String[] { SHIELD, RISE };
			case SLOT_5 -> new String[] { BLIND, WINDOW };
			case SLOT_6 -> new String[] { SENSE, LINK };
			default -> new String[0];
		};
	}

	@Override
	public int maxCooldown(String abilityId) {
		return switch (abilityId) {
			case CANE_STRIKE -> HawkMothConfig.STRIKE_FINISH_COOLDOWN;
			case CANE_SWORD -> HawkMothConfig.SWORD_COOLDOWN;
			case AKUMA -> HawkMothConfig.AKUMA_COOLDOWN;
			case RECALL -> HawkMothConfig.RECALL_COOLDOWN;
			case SWARM_FLIGHT -> HawkMothConfig.FLIGHT_COOLDOWN;
			case SWARM_DASH -> HawkMothConfig.DASH_COOLDOWN;
			case SHIELD -> HawkMothConfig.SHIELD_COOLDOWN;
			case RISE -> HawkMothConfig.RISE_COOLDOWN;
			case BLIND -> HawkMothConfig.BLIND_COOLDOWN;
			case WINDOW -> HawkMothConfig.WINDOW_COOLDOWN;
			case SENSE -> HawkMothConfig.SENSE_COOLDOWN;
			case LINK -> HawkMothConfig.LINK_COOLDOWN;
			default -> 0;
		};
	}

	@Override
	public boolean active(Player player, AbilitySlot slot) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || player.level() == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return switch (slot) {
			case SLOT_1 -> s.val(V_SWORD_END) > now;
			case SLOT_2 -> false;
			case SLOT_3 -> s.val(V_FLIGHT_UNTIL) > now || s.val(V_DASH_AT) > 0L;
			case SLOT_4 -> s.val(V_SHIELD_AT) > 0L || s.val(V_RISE_AT) > 0L;
			case SLOT_5 -> s.val(V_WINDOW_MOB) != 0L;
			case SLOT_6 -> s.val(V_SENSE_UNTIL) > now;
			default -> false;
		};
	}

	@Override
	public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
		if (slot == AbilitySlot.SLOT_4 && !pressed) {
			lowerShield(player);
			return;
		}
		if (!pressed) {
			return;
		}
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					caneSword(player);
				} else {
					caneStrike(player);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					recall(player);
				} else {
					akuma(player);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					swarmDash(player);
				} else {
					swarmFlight(player);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					riseAkuma(player);
				} else {
					raiseShield(player);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					window(player);
				} else {
					swarmBlind(player);
				}
			}
			case SLOT_6 -> {
				if (shift) {
					akumaLink(player);
				} else {
					emotionSense(player);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player) {
		MiraculousState s = Miraculous.state(player);
		if (!s.transformed) {
			return;
		}
		long now = player.level().getGameTime();
		tickSword(player, s, now);
		tickDash(player, s, now);
		tickFlight(player, s, now);
		tickShield(player, s, now);
		tickRise(player, s, now);
		tickWindow(player, s, now);
		tickSense(player, s, now);
	}

	@Override
	public void onDetransform(ServerPlayer player) {
		endFlight(player);
		MiraculousState s = Miraculous.state(player);
		if (s.val(V_DASH_AT) > 0L) {
			player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
		}
		unfreeze(player);
		LUNGE_HITS.remove(player.getUUID());
		HawkMothAkumas.releaseAll(player);
	}

	@Override
	public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
		return blocks(player, source) ? 0f : amount;
	}

	@Override
	public void clearSessionState() {
		LUNGE_HITS.clear();
		FLYING.clear();
		HawkMothAkumas.clearSessionState();
	}

	@Override
	public void forget(UUID id) {
		LUNGE_HITS.remove(id);
		FLYING.remove(id);
	}

	// ---------------------------------------------------------------- helpers

	private static boolean ready(ServerPlayer player, String id) {
		if (Miraculous.ready(player, id)) {
			return true;
		}
		Miraculous.say(player, "message.projecthero.hawk_moth.cooldown", ChatFormatting.GRAY,
				Component.translatable("projecthero.miraculous.ability." + id),
				String.format("%.1f", Miraculous.cooldownRemaining(player, id) / 20f));
		return false;
	}

	private static long now(ServerPlayer player) {
		return player.level().getGameTime();
	}

	/** Where the off hand is (the akuma lifts off the palm of the hand without the cane). */
	public static Vec3 offHand(Player player) {
		Vec3 eye = player.getEyePosition();
		float yaw = player.yBodyRot * ((float) Math.PI / 180f);
		Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		double side = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? -1 : 1;
		return eye.add(fwd.scale(0.55)).add(right.scale(0.3 * side)).add(0, -0.5, 0);
	}

	/** Between his cupped hands (RISE, AKUMA!): where the white butterfly darkens and lifts off. */
	public static Vec3 cupPos(Player player) {
		float yaw = player.yBodyRot * ((float) Math.PI / 180f);
		return player.position().add(-Math.sin(yaw) * 0.62, 1.2, Math.cos(yaw) * 0.62);
	}

	/** Is {@code e} on Hawk Moth's own side (never a target of his moves)? */
	static boolean ownSide(ServerPlayer player, Entity e) {
		return HawkMothAkumas.isOwnedBy(e, player.getUUID());
	}

	private static void dust(ServerLevel level, Vec3 at, int count, double spread) {
		level.sendParticles(new DustParticleOptions(new Vector3f(0.45f, 0.12f, 0.62f), 1.0f), at.x, at.y, at.z, count, spread, spread, spread, 0.02);
	}

	// ---------------------------------------------------------------- R: Cane Strike / Cane Sword

	public static void caneStrike(ServerPlayer player) {
		MiraculousState s = Miraculous.state(player);
		long now = now(player);
		long last = s.val(V_COMBO_AT);
		if (last > 0 && now - last < HawkMothConfig.STRIKE_GAP && now >= last) {
			return;
		}
		if (!ready(player, CANE_STRIKE)) {
			return;
		}
		int step = (int) s.val(V_COMBO_STEP);
		step = last > 0 && now - last <= HawkMothConfig.STRIKE_WINDOW && step < 3 ? step + 1 : 1;
		int st = step;
		Miraculous.edit(player, n -> {
			n.vals.put(V_COMBO_STEP, (long) st);
			n.vals.put(V_COMBO_AT, now);
		});
		Miraculous.setAnim(player, ANIM_STRIKE_1 + step - 1);
		LivingEntity t = AbilityHelpers.raycastEntity(player, HawkMothConfig.STRIKE_REACH);
		if (t == null || ownSide(player, t) || !HeroTargets.canHarm(player, t)) {
			t = null;
			Vec3 look = player.getLookAngle();
			double best = Double.MAX_VALUE;
			for (LivingEntity e : AbilityHelpers.enemiesAround(player, player.getEyePosition().add(look.scale(1.6)), 1.6)) {
				if (ownSide(player, e)) {
					continue;
				}
				double d = e.distanceToSqr(player);
				if (d < best) {
					best = d;
					t = e;
				}
			}
		}
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f,
				1.1f + 0.1f * step);
		if (t != null) {
			if (AbilityHelpers.hurtBurst(player, t, HawkMothConfig.STRIKE_DAMAGE) || AbilityHelpers.hurtLands(player, t, HawkMothConfig.STRIKE_DAMAGE)) {
				player.setLastHurtMob(t);
				level.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 8, 0.25, 0.25, 0.25, 0.2);
				if (step == 3 && !HawkMothAkumas.isBoss(t)) {
					AbilityHelpers.knockbackFrom(t, player.position(), HawkMothConfig.STRIKE_FINISH_KNOCKBACK);
				}
				level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.9f, 1.2f);
			}
		}
		if (step == 3) {
			Miraculous.setCooldown(player, CANE_STRIKE, HawkMothConfig.STRIKE_FINISH_COOLDOWN);
		}
	}

	public static void caneSword(ServerPlayer player) {
		MiraculousState s = Miraculous.state(player);
		if (s.val(V_SWORD_AT) > 0L || !ready(player, CANE_SWORD)) {
			return;
		}
		long now = now(player);
		Vec3 look = player.getLookAngle();
		Vec3 dir = new Vec3(look.x, Math.max(-0.3, Math.min(0.3, look.y)), look.z).normalize();
		Miraculous.edit(player, n -> {
			n.vals.put(V_SWORD_AT, now);
			n.vals.put(V_SWORD_END, now + HawkMothConfig.SWORD_DRAW_TICKS + HawkMothConfig.SWORD_LUNGE_TICKS + HawkMothConfig.SWORD_SHOW_TICKS);
			n.vecs.put(VEC_LUNGE_DIR, List.of(dir.x, dir.y, dir.z));
			n.animId = ANIM_SWORD;
			n.animStart = now;
		});
		Miraculous.setCooldown(player, CANE_SWORD, HawkMothConfig.SWORD_COOLDOWN);
		LUNGE_HITS.put(player.getUUID(), new HashSet<>());
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS,
				1f, 1.6f);
	}

	private static void tickSword(ServerPlayer player, MiraculousState s, long now) {
		long at = s.val(V_SWORD_AT);
		if (at <= 0L) {
			return;
		}
		long t = now - at;
		Vec3 dir = Miraculous.vec(s, VEC_LUNGE_DIR);
		if (t >= HawkMothConfig.SWORD_DRAW_TICKS && t < HawkMothConfig.SWORD_DRAW_TICKS + HawkMothConfig.SWORD_LUNGE_TICKS && dir != null) {
			AbilityHelpers.launchSelf(player, dir.scale(HawkMothConfig.SWORD_LUNGE_SPEED));
			Set<Integer> hit = LUNGE_HITS.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
			Vec3 from = player.position();
			Vec3 to = from.add(dir.scale(HawkMothConfig.SWORD_LUNGE_SPEED * 1.5));
			AABB box = new AABB(from, to).inflate(HawkMothConfig.SWORD_WIDTH, 0.6, HawkMothConfig.SWORD_WIDTH).expandTowards(0, player.getBbHeight(), 0);
			for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
					e -> e != player && HeroTargets.canHarm(player, e) && !ownSide(player, e))) {
				if (hit.add(e.getId())) {
					if (AbilityHelpers.hurtBurst(player, e, HawkMothConfig.SWORD_DAMAGE) || AbilityHelpers.hurtLands(player, e, HawkMothConfig.SWORD_DAMAGE)) {
						player.setLastHurtMob(e);
						player.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1, 0, 0, 0, 0);
						dust(player.serverLevel(), e.position().add(0, e.getBbHeight() * 0.5, 0), 8, 0.2);
					}
				}
			}
			player.resetFallDistance();
		} else if (t == HawkMothConfig.SWORD_DRAW_TICKS + HawkMothConfig.SWORD_LUNGE_TICKS) {
			AbilityHelpers.launchSelf(player, player.getDeltaMovement().multiply(0.15, 0.5, 0.15));
			LUNGE_HITS.remove(player.getUUID());
		}
		if (now >= s.val(V_SWORD_END)) {
			Miraculous.edit(player, n -> {
				n.vals.remove(V_SWORD_AT);
				n.vals.remove(V_SWORD_END);
				n.vecs.remove(VEC_LUNGE_DIR);
			});
			player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS,
					0.8f, 1.9f);
		}
	}

	// ---------------------------------------------------------------- G: Akuma / Recall

	public static void akuma(ServerPlayer player) {
		if (!ready(player, AKUMA)) {
			return;
		}
		LivingEntity t = AbilityHelpers.raycastEntity(player, HawkMothConfig.AKUMA_RANGE);
		if (t == null) {
			Miraculous.say(player, "message.projecthero.hawk_moth.no_target", ChatFormatting.GRAY);
			return;
		}
		if (t instanceof Player) {
			Miraculous.say(player, "message.projecthero.hawk_moth.player_immune", ChatFormatting.GRAY);
			return;
		}
		if (!(t instanceof Mob mob) || HawkMothAkumas.isBoss(t)) {
			Miraculous.say(player, "message.projecthero.hawk_moth.boss_immune", ChatFormatting.GRAY, t.getDisplayName());
			return;
		}
		if (HawkMothAkumas.isAkumatized(mob)) {
			Miraculous.say(player, "message.projecthero.hawk_moth.already", ChatFormatting.GRAY, t.getDisplayName());
			return;
		}
		if (HeroTargets.isFriendlyPet(player, mob)) {
			Miraculous.say(player, "message.projecthero.hawk_moth.pet_immune", ChatFormatting.GRAY, t.getDisplayName());
			return;
		}
		if (HawkMothAkumas.akumasOut(player.getUUID()) >= HawkMothConfig.AKUMA_MAX) {
			Miraculous.say(player, "message.projecthero.hawk_moth.max_akumas", ChatFormatting.GRAY, HawkMothConfig.AKUMA_MAX);
			return;
		}
		HawkMothAkumas.send(player, offHand(player), mob, com.projecthero.mod.miraculous.hawkmoth.entity.AkumaEntity.SEND);
		Miraculous.setAnim(player, ANIM_AKUMA);
		Miraculous.setCooldown(player, AKUMA, HawkMothConfig.AKUMA_COOLDOWN);
		player.serverLevel().playSound(null, player.getX(), player.getY() + 1, player.getZ(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS,
				0.7f, 1.4f);
	}

	public static void recall(ServerPlayer player) {
		if (!ready(player, RECALL)) {
			return;
		}
		int n = HawkMothAkumas.recall(player);
		if (n == 0) {
			Miraculous.say(player, "message.projecthero.hawk_moth.no_akumas", ChatFormatting.GRAY);
			return;
		}
		Miraculous.setAnim(player, ANIM_RECALL);
		Miraculous.setCooldown(player, RECALL, HawkMothConfig.RECALL_COOLDOWN);
		Miraculous.setCooldown(player, AKUMA, 0);
		Miraculous.say(player, "message.projecthero.hawk_moth.recalled", ChatFormatting.DARK_PURPLE, n);
	}

	// ---------------------------------------------------------------- X: Swarm Flight / Swarm Dash

	public static boolean flying(Player player) {
		MiraculousState s = Miraculous.peek(player);
		return s != null && s.val(V_FLIGHT_UNTIL) > player.level().getGameTime();
	}

	public static void swarmFlight(ServerPlayer player) {
		MiraculousState s = Miraculous.state(player);
		long now = now(player);
		if (s.val(V_FLIGHT_UNTIL) > now) {
			endFlight(player); // a second press lands him
			return;
		}
		if (s.val(V_WINDOW_MOB) != 0L || !ready(player, SWARM_FLIGHT)) {
			return;
		}
		Miraculous.edit(player, n -> {
			n.vals.put(V_FLIGHT_AT, now);
			n.vals.put(V_FLIGHT_UNTIL, now + HawkMothConfig.FLIGHT_TICKS);
		});
		Miraculous.setCooldown(player, SWARM_FLIGHT, HawkMothConfig.FLIGHT_COOLDOWN);
		if (!player.getAbilities().instabuild && !player.isSpectator()) {
			FLYING.add(player.getUUID());
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			player.getAbilities().setFlyingSpeed(HawkMothConfig.FLIGHT_SPEED);
			player.onUpdateAbilities();
		}
		AbilityHelpers.launchSelf(player, player.getDeltaMovement().add(0, 0.45, 0));
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.35f, 2.0f);
	}

	static void endFlight(ServerPlayer player) {
		MiraculousState s = Miraculous.state(player);
		if (s.val(V_FLIGHT_UNTIL) > 0L || s.val(V_FLIGHT_AT) > 0L) {
			Miraculous.edit(player, n -> {
				n.vals.remove(V_FLIGHT_UNTIL);
				n.vals.remove(V_FLIGHT_AT);
			});
		}
		if (FLYING.remove(player.getUUID()) && !player.getAbilities().instabuild && !player.isSpectator()) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.getAbilities().setFlyingSpeed(0.05f);
			player.onUpdateAbilities();
			player.resetFallDistance();
		}
	}

	private static void tickFlight(ServerPlayer player, MiraculousState s, long now) {
		long until = s.val(V_FLIGHT_UNTIL);
		if (until <= 0L) {
			return;
		}
		if (now >= until || (now - s.val(V_FLIGHT_AT) > com.projecthero.mod.flight.FlightLanding.LIFTOFF_GRACE_TICKS
				&& com.projecthero.mod.flight.FlightLanding.landed(player, s.val(V_FLIGHT_AT)))) {
			endFlight(player);
			return;
		}
		if (FLYING.contains(player.getUUID())) {
			player.getAbilities().mayfly = true;
		}
		player.resetFallDistance();
		if (now % 4 == 0) {
			player.serverLevel().sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 0.3, player.getZ(), 4, 0.4, 0.3, 0.4, 0.0);
		}
	}

	public static void swarmDash(ServerPlayer player) {
		MiraculousState s = Miraculous.state(player);
		if (s.val(V_DASH_AT) > 0L || s.val(V_WINDOW_MOB) != 0L || !ready(player, SWARM_DASH)) {
			return;
		}
		Vec3 look = player.getLookAngle();
		Vec3 eye = player.getEyePosition();
		BlockHitResult bhr = player.level().clip(new ClipContext(eye, eye.add(look.scale(HawkMothConfig.DASH_BLOCKS)), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player));
		double dist = bhr.getType() == HitResult.Type.MISS ? HawkMothConfig.DASH_BLOCKS : Math.max(0, eye.distanceTo(bhr.getLocation()) - 0.7);
		Vec3 from = player.position();
		Vec3 to = from;
		for (double d = dist; d > 0.4; d -= 0.5) {
			Vec3 c = from.add(look.scale(d));
			if (player.level().noCollision(player, player.getBoundingBox().move(c.subtract(from)))) {
				to = c;
				break;
			}
		}
		long now = now(player);
		Vec3 dest = to;
		Miraculous.edit(player, n -> {
			n.vals.put(V_DASH_AT, now);
			n.vecs.put(VEC_DASH_FROM, List.of(from.x, from.y, from.z));
			n.vecs.put(VEC_DASH_TO, List.of(dest.x, dest.y, dest.z));
			n.animId = ANIM_DASH;
			n.animStart = now;
		});
		Miraculous.setCooldown(player, SWARM_DASH, HawkMothConfig.DASH_COOLDOWN);
		player.setInvisible(true);
		player.serverLevel().playSound(null, from.x, from.y, from.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.5f, 2.0f);
	}

	private static void tickDash(ServerPlayer player, MiraculousState s, long now) {
		long at = s.val(V_DASH_AT);
		if (at <= 0L) {
			return;
		}
		long t = now - at;
		Vec3 from = Miraculous.vec(s, VEC_DASH_FROM);
		Vec3 to = Miraculous.vec(s, VEC_DASH_TO);
		if (from == null || to == null) {
			Miraculous.setVal(player, V_DASH_AT, 0L);
			player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
			return;
		}
		player.setInvisible(true);
		player.resetFallDistance();
		if (t >= 1 && t <= HawkMothConfig.DASH_TICKS) {
			Vec3 p = from.lerp(to, t / (double) HawkMothConfig.DASH_TICKS);
			player.teleportTo(player.serverLevel(), p.x, p.y, p.z, player.getYRot(), player.getXRot());
			player.setDeltaMovement(Vec3.ZERO);
		}
		if (t >= HawkMothConfig.DASH_TICKS + HawkMothConfig.DASH_REFORM_TICKS) {
			Miraculous.edit(player, n -> {
				n.vals.remove(V_DASH_AT);
				n.vecs.remove(VEC_DASH_FROM);
				n.vecs.remove(VEC_DASH_TO);
			});
			player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
		}
	}

	public static boolean dashing(Player player) {
		MiraculousState s = Miraculous.peek(player);
		return s != null && s.val(V_DASH_AT) > 0L;
	}

	// ---------------------------------------------------------------- Z: Butterfly Shield / RISE, AKUMA!

	public static boolean shieldUp(Player player) {
		MiraculousState s = Miraculous.peek(player);
		return s != null && s.val(V_SHIELD_AT) > 0L;
	}

	public static void raiseShield(ServerPlayer player) {
		MiraculousState s = Miraculous.state(player);
		if (s.val(V_SHIELD_AT) > 0L || !ready(player, SHIELD)) {
			return;
		}
		Miraculous.setVal(player, V_SHIELD_AT, now(player));
		player.serverLevel().playSound(null, player.getX(), player.getY() + 1, player.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.3f, 2.0f);
	}

	public static void lowerShield(ServerPlayer player) {
		MiraculousState s = Miraculous.state(player);
		if (s.val(V_SHIELD_AT) <= 0L) {
			return;
		}
		Miraculous.setVal(player, V_SHIELD_AT, 0L);
		Miraculous.setCooldown(player, SHIELD, HawkMothConfig.SHIELD_COOLDOWN);
	}

	/** The wall's centre (in front of him, along where he looks horizontally) and its facing. */
	public static Vec3 shieldCentre(Player player) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		return player.position().add(flat.scale(HawkMothConfig.SHIELD_DISTANCE)).add(0, player.getBbHeight() * 0.55, 0);
	}

	private static void tickShield(ServerPlayer player, MiraculousState s, long now) {
		long at = s.val(V_SHIELD_AT);
		if (at <= 0L) {
			return;
		}
		if (now - at >= HawkMothConfig.SHIELD_MAX_TICKS) {
			lowerShield(player);
			return;
		}
		// projectiles meeting the wall are knocked out of the air
		Vec3 c = shieldCentre(player);
		double half = HawkMothConfig.SHIELD_WIDTH * 0.5;
		AABB box = new AABB(c, c).inflate(half + 0.6, HawkMothConfig.SHIELD_HEIGHT * 0.5, half + 0.6);
		ServerLevel level = player.serverLevel();
		for (Projectile p : level.getEntitiesOfClass(Projectile.class, box, p -> p.getOwner() != player && !ownSide(player, p.getOwner()))) {
			if (p.getDeltaMovement().lengthSqr() < 0.01 || !inFront(player, p.position())) {
				continue;
			}
			level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 6, 0.15, 0.15, 0.15, 0.02);
			level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.6f, 1.5f);
			p.discard();
		}
	}

	private static boolean inFront(Player player, Vec3 at) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		Vec3 d = at.subtract(player.position());
		Vec3 df = new Vec3(d.x, 0, d.z);
		if (flat.lengthSqr() < 1.0e-4 || df.lengthSqr() < 1.0e-4) {
			return true;
		}
		return flat.normalize().dot(df.normalize()) > 0.15;
	}

	/** Is this hit stopped by Hawk Moth's moves (the dash's butterflies, the shield's wall)? */
	static boolean blocks(ServerPlayer player, DamageSource source) {
		if (!Miraculous.transformedAs(player, Kwami.NOOROO) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		if (dashing(player)) {
			return true;
		}
		if (shieldUp(player) && source.is(DamageTypeTags.IS_PROJECTILE)) {
			Entity direct = source.getDirectEntity();
			Vec3 from = direct != null ? direct.position() : source.getSourcePosition();
			if (from == null || inFront(player, from)) {
				if (direct instanceof Projectile p) {
					p.discard();
				}
				return true;
			}
		}
		return false;
	}

	public static void riseAkuma(ServerPlayer player) {
		MiraculousState s = Miraculous.state(player);
		if (s.val(V_RISE_AT) > 0L || !ready(player, RISE)) {
			return;
		}
		if (HawkMothAkumas.championOut(player.getUUID())) {
			Miraculous.say(player, "message.projecthero.hawk_moth.one_champion", ChatFormatting.GRAY);
			return;
		}
		LivingEntity t = AbilityHelpers.raycastEntity(player, HawkMothConfig.RISE_RANGE);
		if (t == null) {
			Miraculous.say(player, "message.projecthero.hawk_moth.no_target", ChatFormatting.GRAY);
			return;
		}
		if (HawkMothAkumas.isBoss(t) || (!(t instanceof Mob) && !(t instanceof ServerPlayer))) {
			Miraculous.say(player, "message.projecthero.hawk_moth.boss_immune", ChatFormatting.GRAY, t.getDisplayName());
			return;
		}
		if (t instanceof ServerPlayer tp && (HawkMothAkumas.champion(tp) != null || HawkMothAkumas.offer(tp) != null)) {
			Miraculous.say(player, "message.projecthero.hawk_moth.already", ChatFormatting.GRAY, t.getDisplayName());
			return;
		}
		HawkMothAkumas.Bond b = HawkMothAkumas.bond(t);
		if (b != null && !b.owner.equals(player.getUUID())) {
			Miraculous.say(player, "message.projecthero.hawk_moth.already", ChatFormatting.GRAY, t.getDisplayName());
			return;
		}
		long now = now(player);
		Miraculous.edit(player, n -> {
			n.vals.put(V_RISE_AT, now + HawkMothConfig.RISE_CUP_TICKS);
			n.vals.put(V_RISE_TARGET, (long) t.getId());
			n.animId = ANIM_RISE;
			n.animStart = now;
		});
		endFlight(player);
		player.serverLevel().playSound(null, player.getX(), player.getY() + 1, player.getZ(), SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.PLAYERS,
				0.8f, 0.8f);
	}

	private static void tickRise(ServerPlayer player, MiraculousState s, long now) {
		long at = s.val(V_RISE_AT);
		if (at <= 0L) {
			return;
		}
		if (now % 4 == 0) {
			dust(player.serverLevel(), cupPos(player), 2, 0.08);
		}
		if (now < at) {
			return;
		}
		int id = (int) s.val(V_RISE_TARGET);
		Miraculous.edit(player, n -> {
			n.vals.remove(V_RISE_AT);
			n.vals.remove(V_RISE_TARGET);
		});
		Entity e = player.level().getEntity(id);
		if (!(e instanceof LivingEntity t) || !t.isAlive() || t.distanceTo(player) > HawkMothConfig.RISE_RANGE + 8) {
			Miraculous.say(player, "message.projecthero.hawk_moth.target_lost", ChatFormatting.GRAY);
			return;
		}
		Vec3 from = cupPos(player);
		HawkMothAkumas.send(player, from, t, com.projecthero.mod.miraculous.hawkmoth.entity.AkumaEntity.RISE);
		Miraculous.startCountdown(player);
		Miraculous.setCooldown(player, RISE, HawkMothConfig.RISE_COOLDOWN);
		player.serverLevel().playSound(null, player.getX(), player.getY() + 1, player.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1f, 0.7f);
		player.sendSystemMessage(Component.translatable("message.projecthero.hawk_moth.rise").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
	}

	/** A Rise akuma that never took (refused, ran out, lost its target): the cooldown is cut short. */
	static void riseFailed(ServerPlayer owner) {
		if (Miraculous.cooldownRemaining(owner, RISE) > HawkMothConfig.RISE_REFUSED_COOLDOWN) {
			Miraculous.setCooldown(owner, RISE, HawkMothConfig.RISE_REFUSED_COOLDOWN);
		}
	}

	// ---------------------------------------------------------------- V: Swarm Blind / Hawk Moth's Window

	public static void swarmBlind(ServerPlayer player) {
		if (!ready(player, BLIND)) {
			return;
		}
		List<LivingEntity> hit = new ArrayList<>();
		for (LivingEntity e : AbilityHelpers.hostilesAround(player, player.position().add(0, 1, 0), HawkMothConfig.BLIND_RADIUS)) {
			if (ownSide(player, e)) {
				continue;
			}
			AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, HawkMothConfig.BLIND_TICKS, 0);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, HawkMothConfig.BLIND_TICKS, HawkMothConfig.BLIND_SLOW_AMP);
			if (e instanceof Mob m && m.getTarget() == player) {
				m.setTarget(null);
			}
			hit.add(e);
		}
		Miraculous.setAnim(player, ANIM_BLIND);
		if (hit.isEmpty()) {
			Miraculous.say(player, "message.projecthero.hawk_moth.no_hostiles", ChatFormatting.GRAY);
			Miraculous.setCooldown(player, BLIND, 20);
			return;
		}
		Miraculous.setCooldown(player, BLIND, HawkMothConfig.BLIND_COOLDOWN);
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : hit) {
			ids.add(e.getId());
		}
		HawkMothSwarmPayload payload = new HawkMothSwarmPayload(ids, HawkMothConfig.BLIND_TICKS);
		for (ServerPlayer p : PlayerLookup.around(player.serverLevel(), player.position(), 64)) {
			if (ServerPlayNetworking.canSend(p, HawkMothSwarmPayload.TYPE)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
		player.serverLevel().playSound(null, player.getX(), player.getY() + 1, player.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.6f, 1.8f);
	}

	public static void window(ServerPlayer player) {
		MiraculousState s = Miraculous.state(player);
		if (s.val(V_WINDOW_MOB) != 0L) {
			closeWindow(player);
			return;
		}
		if (!ready(player, WINDOW)) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Mob best = null;
		double bestScore = -2;
		for (HawkMothAkumas.Bond b : HawkMothAkumas.bondsOf(player.getUUID())) {
			if (b.mob.level() != player.level()) {
				continue;
			}
			Vec3 d = b.mob.getEyePosition().subtract(eye);
			double score = look.dot(d.normalize()) - d.length() * 0.002;
			if (score > bestScore) {
				bestScore = score;
				best = b.mob;
			}
		}
		if (best == null) {
			Miraculous.say(player, "message.projecthero.hawk_moth.no_akumas", ChatFormatting.GRAY);
			return;
		}
		endFlight(player);
		long now = now(player);
		int id = best.getId();
		Miraculous.edit(player, n -> {
			n.vals.put(V_WINDOW_MOB, (long) id);
			n.vals.put(V_WINDOW_UNTIL, now + HawkMothConfig.WINDOW_TICKS);
		});
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, WINDOW_SPEED_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, WINDOW_JUMP_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
		player.hurtMarked = true;
		Miraculous.say(player, "message.projecthero.hawk_moth.window_open", ChatFormatting.DARK_PURPLE, best.getDisplayName());
	}

	public static void closeWindow(ServerPlayer player) {
		MiraculousState s = Miraculous.state(player);
		if (s.val(V_WINDOW_MOB) == 0L) {
			return;
		}
		Miraculous.edit(player, n -> {
			n.vals.remove(V_WINDOW_MOB);
			n.vals.remove(V_WINDOW_UNTIL);
		});
		unfreeze(player);
		Miraculous.setCooldown(player, WINDOW, HawkMothConfig.WINDOW_COOLDOWN);
	}

	private static void unfreeze(ServerPlayer player) {
		PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, WINDOW_SPEED_ID);
		PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, WINDOW_JUMP_ID);
	}

	public static boolean windowOpen(Player player) {
		MiraculousState s = Miraculous.peek(player);
		return s != null && s.val(V_WINDOW_MOB) != 0L;
	}

	private static void tickWindow(ServerPlayer player, MiraculousState s, long now) {
		long id = s.val(V_WINDOW_MOB);
		if (id == 0L) {
			return;
		}
		Entity e = player.level().getEntity((int) id);
		if (now >= s.val(V_WINDOW_UNTIL) || !(e instanceof Mob mob) || !mob.isAlive() || !HawkMothAkumas.isOwnedBy(mob, player.getUUID())
				|| mob.distanceToSqr(player) > 64.0 * 64.0) {
			closeWindow(player);
		}
	}

	// ---------------------------------------------------------------- C: Emotion Sense / Akuma Link

	public static void emotionSense(ServerPlayer player) {
		MiraculousState s = Miraculous.state(player);
		long now = now(player);
		if (s.val(V_SENSE_UNTIL) > now) {
			Miraculous.setVal(player, V_SENSE_UNTIL, 0L); // a second press closes it
			sendSense(player, List.of(), List.of(), 1);
			return;
		}
		if (!ready(player, SENSE)) {
			return;
		}
		Miraculous.setVal(player, V_SENSE_UNTIL, now + HawkMothConfig.SENSE_TICKS);
		Miraculous.setCooldown(player, SENSE, HawkMothConfig.SENSE_COOLDOWN);
		Miraculous.setAnim(player, ANIM_SENSE);
		refreshSense(player);
		player.serverLevel().playSound(null, player.getX(), player.getY() + 1, player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.6f);
	}

	private static void tickSense(ServerPlayer player, MiraculousState s, long now) {
		long until = s.val(V_SENSE_UNTIL);
		if (until <= 0L) {
			return;
		}
		if (now >= until) {
			Miraculous.setVal(player, V_SENSE_UNTIL, 0L);
			return;
		}
		if ((now - until) % HawkMothConfig.SENSE_REFRESH == 0) {
			refreshSense(player);
		}
	}

	/** Every creature within {@link HawkMothConfig#SENSE_RANGE} and its mood colour (also used by the tests). */
	public static Map<Integer, Integer> moods(ServerPlayer player) {
		Map<Integer, Integer> out = new java.util.LinkedHashMap<>();
		for (LivingEntity e : AbilityHelpers.living(player.serverLevel(), player.position(), HawkMothConfig.SENSE_RANGE,
				e -> e != player && !(e instanceof ArmorStand) && !e.isSpectator())) {
			out.put(e.getId(), mood(e));
		}
		return out;
	}

	/** Hostile red, calm white, scared / fleeing blue; players by health (low = blue). */
	public static int mood(LivingEntity e) {
		if (e instanceof Player p) {
			float f = p.getHealth() / Math.max(1f, p.getMaxHealth());
			return f <= HawkMothConfig.SENSE_PLAYER_LOW ? HawkMothConfig.SENSE_SCARED
					: f <= HawkMothConfig.SENSE_PLAYER_MID ? HawkMothConfig.SENSE_PLAYER_HURT : HawkMothConfig.SENSE_CALM;
		}
		if (e instanceof Mob m) {
			if (scared(m)) {
				return HawkMothConfig.SENSE_SCARED;
			}
			if (m.getTarget() != null || (m instanceof NeutralMob n && n.isAngry()) || e instanceof Enemy) {
				return HawkMothConfig.SENSE_HOSTILE;
			}
		}
		return HawkMothConfig.SENSE_CALM;
	}

	private static boolean scared(Mob m) {
		for (WrappedGoal w : HawkMothAkumas.goals(m).getAvailableGoals()) {
			if (w.isRunning() && (w.getGoal() instanceof PanicGoal || w.getGoal() instanceof AvoidEntityGoal<?>)) {
				return true;
			}
		}
		if (m.getBrain().checkMemory(MemoryModuleType.IS_PANICKING, net.minecraft.world.entity.ai.memory.MemoryStatus.VALUE_PRESENT)) {
			return true;
		}
		if (m instanceof Enemy) {
			return false;
		}
		// a passive creature that was just hurt is fleeing
		return m.hasEffect(MobEffects.BLINDNESS) || (m.getLastHurtByMob() != null && m.tickCount - m.getLastHurtByMobTimestamp() < 100);
	}

	private static void refreshSense(ServerPlayer player) {
		List<Integer> ids = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		moods(player).forEach((id, c) -> {
			ids.add(id);
			colors.add(c);
		});
		sendSense(player, ids, colors, HawkMothConfig.SENSE_REFRESH + 15);
	}

	private static void sendSense(ServerPlayer player, List<Integer> ids, List<Integer> colors, int ticks) {
		if (ServerPlayNetworking.canSend(player, HawkMothSensePayload.TYPE)) {
			ServerPlayNetworking.send(player, new HawkMothSensePayload(ids, colors, ticks));
		}
	}

	public static void akumaLink(ServerPlayer player) {
		if (!ready(player, LINK)) {
			return;
		}
		List<HawkMothAkumas.Bond> bonds = HawkMothAkumas.bondsOf(player.getUUID());
		LivingEntity t = AbilityHelpers.raycastEntity(player, HawkMothConfig.LINK_RANGE);
		ServerPlayer champion = HawkMothAkumas.championPlayerOf(player);
		Miraculous.setCooldown(player, LINK, HawkMothConfig.LINK_COOLDOWN);
		if (t != null && !bonds.isEmpty() && !ownSide(player, t)) {
			int n = HawkMothAkumas.link(player, t);
			Miraculous.setAnim(player, ANIM_LINK);
			Miraculous.say(player, "message.projecthero.hawk_moth.linked", ChatFormatting.DARK_PURPLE, n, t.getDisplayName());
			dust(player.serverLevel(), t.getEyePosition(), 10, 0.3);
			return;
		}
		if (champion != null) {
			Miraculous.setAnim(player, ANIM_LINK);
			if (ServerPlayNetworking.canSend(player, HawkMothLinkPayload.TYPE)) {
				ServerPlayNetworking.send(player, new HawkMothLinkPayload(champion.getGameProfile().getName()));
			}
			Miraculous.say(player, "message.projecthero.hawk_moth.link_speak", ChatFormatting.DARK_PURPLE, champion.getDisplayName());
			return;
		}
		if (!bonds.isEmpty()) {
			HawkMothAkumas.unlink(player);
			Miraculous.setAnim(player, ANIM_LINK);
			Miraculous.say(player, "message.projecthero.hawk_moth.link_guard", ChatFormatting.DARK_PURPLE);
			return;
		}
		Miraculous.say(player, "message.projecthero.hawk_moth.no_akumas", ChatFormatting.GRAY);
	}
}
