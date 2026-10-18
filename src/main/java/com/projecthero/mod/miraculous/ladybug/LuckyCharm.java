package com.projecthero.mod.miraculous.ladybug;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.ladybug.item.LadybugItems;
import com.projecthero.mod.miraculous.ladybug.network.LadybugGlowPayload;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: Shift+Z -- <b>Lucky Charm</b>, then <b>Miraculous Ladybug</b>.
 *
 * <ol>
 *   <li>Shift+Z: the Yo-yo goes up, a burst of ladybug-red light, and a spotted object drops out of the sky that suits
 *       the moment ({@link #choose}): a golden apple when she is hurt, TNT when surrounded, a parachute when falling
 *       (straight into her hand), otherwise a sword -- or a bow when the nearest threat is far. Then 10 s of Ladybug
 *       Vision: the world goes grey for her and the charm and the threats near her glow red. The 3-minute countdown
 *       starts. One charm per transformation.</li>
 *   <li>Shift+Z again, holding the charm: it is thrown up and a swarm of ladybugs flies out -- every block broken near
 *       her in the last 5 minutes comes back ({@link LadybugRestore}) and she and her squadmates heal to full. The
 *       countdown is NOT restarted.</li>
 * </ol>
 */
public final class LuckyCharm {
	/** Which charm (the synced {@code lb_charm} value). */
	public enum Kind {
		APPLE(1), TNT(2), CHUTE(3), SWORD(4), BOW(5);

		public final int id;

		Kind(int id) {
			this.id = id;
		}

		public static Kind byId(long id) {
			for (Kind k : values()) {
				if (k.id == id) {
					return k;
				}
			}
			return null;
		}

		public Item item() {
			return switch (this) {
				case APPLE -> LadybugItems.LUCKY_APPLE;
				case TNT -> LadybugItems.LUCKY_TNT;
				case CHUTE -> LadybugItems.LUCKY_PARACHUTE;
				case SWORD -> LadybugItems.LUCKY_SWORD;
				case BOW -> LadybugItems.LUCKY_BOW;
			};
		}
	}

	/** owner -> the charm item entities lying about. */
	private static final Map<UUID, Set<Integer>> DROPPED = new ConcurrentHashMap<>();

	private LuckyCharm() {
	}

	static void clearSessionState() {
		DROPPED.clear();
	}

	static void forget(UUID id) {
		DROPPED.remove(id);
	}

	/** Shift+Z. */
	public static void handle(ServerPlayer p) {
		MiraculousState s = Miraculous.state(p);
		if (s.val(LadybugKeys.V_CHARM) != 0L) {
			if (holding(p)) {
				miraculousLadybug(p);
			} else {
				Miraculous.say(p, "message.projecthero.ladybug.hold_charm", ChatFormatting.RED);
			}
			return;
		}
		if (s.val(LadybugKeys.V_CHARM_USED) != 0L) {
			Miraculous.say(p, "message.projecthero.ladybug.charm_spent", ChatFormatting.GRAY);
			return;
		}
		summon(p);
	}

	public static boolean holding(Player p) {
		return LadybugItems.isCharm(p.getMainHandItem()) || LadybugItems.isCharm(p.getOffhandItem());
	}

	/** What the moment needs. */
	public static Kind choose(ServerPlayer p) {
		List<LivingEntity> near = AbilityHelpers.hostilesAround(p, p.position(), LadybugConfig.CHARM_SURROUND_RADIUS);
		boolean falling = !p.onGround() && !p.isInWater() && (p.getDeltaMovement().y < LadybugConfig.CHARM_FALL_SPEED
				|| p.fallDistance > LadybugConfig.CHARM_FALL_DISTANCE);
		double nearest = -1.0;
		for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.position(), 48.0)) {
			double d = e.distanceTo(p);
			nearest = nearest < 0.0 ? d : Math.min(nearest, d);
		}
		return pick(p.getHealth() / p.getMaxHealth(), near.size(), falling, nearest);
	}

	/**
	 * The rule (in this order): hurt below {@link LadybugConfig#CHARM_LOW_HEALTH} -> apple; {@link LadybugConfig#CHARM_SURROUNDED}
	 * threats close -> TNT; falling -> parachute; the nearest threat further than {@link LadybugConfig#CHARM_BOW_RANGE}
	 * -> bow; otherwise (or nothing about) -> sword. {@code nearest} -1 = none.
	 */
	public static Kind pick(float healthShare, int surrounding, boolean falling, double nearest) {
		if (healthShare < LadybugConfig.CHARM_LOW_HEALTH) {
			return Kind.APPLE;
		}
		if (surrounding >= LadybugConfig.CHARM_SURROUNDED) {
			return Kind.TNT;
		}
		if (falling) {
			return Kind.CHUTE;
		}
		return nearest > LadybugConfig.CHARM_BOW_RANGE ? Kind.BOW : Kind.SWORD;
	}

	public static Kind summon(ServerPlayer p) {
		Kind kind = choose(p);
		ServerLevel level = (ServerLevel) p.level();
		long now = level.getGameTime();
		// the charm must be bound to this transformation before it exists (its own tick checks the synced values)
		Miraculous.edit(p, n -> {
			n.vals.put(LadybugKeys.V_CHARM, (long) kind.id);
			n.vals.put(LadybugKeys.V_CHARM_USED, 1L);
			n.vals.put(LadybugKeys.V_VISION_UNTIL, now + LadybugConfig.VISION_TICKS);
		});
		ItemStack stack = LadybugItems.bound(kind.item(), p);
		Vec3 burst;
		if (kind == Kind.CHUTE) {
			// falling: straight into her hand (it could never catch her up)
			if (p.getMainHandItem().isEmpty()) {
				p.setItemInHand(InteractionHand.MAIN_HAND, stack);
			} else if (p.getOffhandItem().isEmpty()) {
				p.setItemInHand(InteractionHand.OFF_HAND, stack);
			} else if (!p.getInventory().add(stack)) {
				p.drop(stack, false);
			}
			burst = p.position().add(0, 2.6, 0);
		} else {
			Vec3 look = p.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(0.8);
			Vec3 base = p.position().add(flat).add(0, 1.0, 0);
			Vec3 top = base.add(0, LadybugConfig.CHARM_DROP_HEIGHT, 0);
			var hit = level.clip(new ClipContext(base, top, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			Vec3 at = hit.getType() == HitResult.Type.MISS ? top : hit.getLocation().add(0, -0.4, 0);
			ItemEntity drop = new ItemEntity(level, at.x, at.y, at.z, stack, 0.0, -0.05, 0.0);
			drop.setTarget(p.getUUID());
			drop.setPickUpDelay(8);
			drop.setUnlimitedLifetime();
			level.addFreshEntity(drop);
			track(p, drop);
			Miraculous.setVal(p, LadybugKeys.V_CHARM_ITEM, drop.getId() + 1L);
			burst = at;
		}
		level.sendParticles(LadybugMoves.RED, burst.x, burst.y, burst.z, 60, 0.8, 0.8, 0.8, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, burst.x, burst.y, burst.z, 20, 0.4, 0.4, 0.4, 0.08);
		level.sendParticles(ParticleTypes.FLASH, burst.x, burst.y, burst.z, 1, 0, 0, 0, 0);
		Miraculous.edit(p, n -> n.vecs.put(LadybugKeys.P_ML, List.of(burst.x, burst.y, burst.z)));
		Miraculous.setAnim(p, LadybugKeys.ANIM_CHARM);
		Miraculous.startCountdown(p);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.2f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.6f);
		p.displayClientMessage(Component.translatable("message.projecthero.ladybug.lucky_charm",
				Component.translatable(kind.item().getDescriptionId())).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
		sendVisionGlow(p);
		return kind;
	}

	private static void track(ServerPlayer p, ItemEntity e) {
		DROPPED.computeIfAbsent(p.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(e.getId());
	}

	/** Any charm item entity appearing (dropped again with Q) belongs to its stack's owner's list. */
	static void onEntityLoad(Entity e, ServerLevel level) {
		if (e instanceof ItemEntity ie && LadybugItems.isCharm(ie.getItem())) {
			var data = ie.getItem().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
			if (data != null && data.copyTag().hasUUID("lb_owner")) {
				DROPPED.computeIfAbsent(data.copyTag().getUUID("lb_owner"), k -> ConcurrentHashMap.newKeySet()).add(ie.getId());
			} else {
				ie.discard();
			}
		}
	}

	/** Every charm she has: out of her inventory, off the ground. */
	static void removeAll(ServerPlayer p) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (LadybugItems.isCharm(p.getInventory().getItem(i))) {
				p.getInventory().setItem(i, ItemStack.EMPTY);
			}
		}
		Set<Integer> ids = DROPPED.remove(p.getUUID());
		if (ids != null && p.getServer() != null) {
			for (ServerLevel l : p.getServer().getAllLevels()) {
				for (int id : ids) {
					Entity e = l.getEntity(id);
					if (e instanceof ItemEntity) {
						e.discard();
					}
				}
			}
		}
	}

	/** The Vision outlines: the charm and the threats near her (re-sent every second while it lasts). */
	static void sendVisionGlow(ServerPlayer p) {
		if (!ServerPlayNetworking.canSend(p, LadybugGlowPayload.TYPE)) {
			return;
		}
		List<Integer> ids = new ArrayList<>();
		for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.position(), LadybugConfig.VISION_RADIUS)) {
			ids.add(e.getId());
		}
		Set<Integer> dropped = DROPPED.get(p.getUUID());
		if (dropped != null) {
			ids.addAll(dropped);
		}
		ServerPlayNetworking.send(p, new LadybugGlowPayload(ids, 30));
	}

	/** Per tick while transformed: the Vision's outlines, the parachute, the dropped charm's id. */
	static void tick(ServerPlayer p, MiraculousState s) {
		long now = p.level().getGameTime();
		if (s.val(LadybugKeys.V_VISION_UNTIL) > now && now % 20 == 0) {
			sendVisionGlow(p);
		}
		long item = s.val(LadybugKeys.V_CHARM_ITEM);
		if (item != 0L && now % 10 == 0) {
			Entity e = p.level().getEntity((int) (item - 1L));
			if (e == null || !e.isAlive()) {
				Miraculous.setVal(p, LadybugKeys.V_CHARM_ITEM, 0L);
			}
		}
		boolean chute = (p.getMainHandItem().is(LadybugItems.LUCKY_PARACHUTE) || p.getOffhandItem().is(LadybugItems.LUCKY_PARACHUTE))
				&& !p.onGround() && !p.isInWater() && p.getDeltaMovement().y < -0.08;
		if (chute) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, false, false, false));
			p.resetFallDistance();
		}
		if (chute != (s.val(LadybugKeys.V_CHUTE) != 0L)) {
			Miraculous.setVal(p, LadybugKeys.V_CHUTE, chute ? 1L : 0L);
			if (chute) {
				p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
			}
		}
	}

	/** Shift+Z holding the charm. Returns the number of blocks queued to come back. */
	public static int miraculousLadybug(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		long now = level.getGameTime();
		removeAll(p);
		Vec3 up = p.position().add(0, 3.0, 0);
		Miraculous.edit(p, n -> {
			n.vals.remove(LadybugKeys.V_CHARM);
			n.vals.remove(LadybugKeys.V_CHARM_ITEM);
			n.vals.remove(LadybugKeys.V_VISION_UNTIL);
			n.vals.remove(LadybugKeys.V_CHUTE);
			n.vals.put(LadybugKeys.V_ML_AT, now);
			n.vecs.put(LadybugKeys.P_ML, List.of(up.x, up.y, up.z));
		});
		Miraculous.setAnim(p, LadybugKeys.ANIM_MIRACULOUS);
		int restored = LadybugRestore.restoreAround(level, p.position(), LadybugConfig.RESTORE_RADIUS);
		heal(p);
		int healed = 1;
		if (p.getServer() != null) {
			for (ServerPlayer m : level.players()) {
				if (m != p && Squads.areAllies(p, m) && m.distanceTo(p) <= LadybugConfig.HEAL_RADIUS) {
					heal(m);
					healed++;
				}
			}
		}
		level.sendParticles(LadybugMoves.RED, up.x, up.y, up.z, 120, 2.5, 2.0, 2.5, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, up.x, up.y, up.z, 40, 1.5, 1.5, 1.5, 0.1);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.8f);
		p.displayClientMessage(Component.translatable("message.projecthero.ladybug.miraculous", restored, healed)
				.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
		return restored;
	}

	private static void heal(ServerPlayer p) {
		p.setHealth(p.getMaxHealth());
		p.clearFire();
		p.removeEffect(MobEffects.POISON);
		p.removeEffect(MobEffects.WITHER);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.5f);
	}
}
