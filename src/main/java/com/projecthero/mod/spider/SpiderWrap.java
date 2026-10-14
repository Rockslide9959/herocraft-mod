package com.projecthero.mod.spider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the web cocoon a Web Shot spins round something stuck on one of Spider-Man's tightropes or woven webs.
 *
 * <p>It is <b>permanent</b>: the cocooned body hangs exactly where it was, can do nothing hostile -- no movement, no
 * melee, no shooting, no exploding, no abilities -- until a Spider-Man sneak + right-clicks it ({@link #onUseEntity}) or
 * deliberately pulls it loose (the stuck-pull rule, {@code WebStructures.tearFree}). The flag is a persistent, synced
 * attachment ({@link ModAttachments#SPIDER_WRAPPED}), so it survives a chunk reload and a world save, and every client
 * draws the cocoon ({@code client.spider.SpiderWrapRenderer}).
 *
 * <ul>
 *   <li><b>Mobs:</b> their AI step is skipped outright ({@code MobWrapMixin}: no goals, no brain, no navigation, no
 *       ranged attack, no special attack), a creeper's swell is held at rest and its explosion cancelled
 *       ({@code CreeperWrapMixin}), and any damage they would still deal (an arrow already in flight) is cancelled.</li>
 *   <li><b>Players:</b> attacking, using items and using blocks are refused, and they are held in place.</li>
 * </ul>
 *
 * The server keeps only the pins (where each cocooned entity hangs) of the entities loaded right now, rebuilt from the
 * attachment as entities load; {@link #clearSessionState} drops them with the world.
 */
public final class SpiderWrap {
	/** v0.16.2: ticks after a cocoon is pinned (or loaded) before a missing web frees it. */
	private static final long WEB_GONE_GRACE = 40L;

	private record Pin(ResourceKey<Level> dim, Vec3 at, long since) {
	}

	/** entity id -> where it hangs (server, loaded entities only). */
	private static final Map<Integer, Pin> PINS = new ConcurrentHashMap<>();

	private SpiderWrap() {
	}

	public static void initialize() {
		ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof LivingEntity && isWrapped(e)) {
				PINS.put(e.getId(), new Pin(level.dimension(), e.position(), level.getGameTime()));
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> PINS.remove(e.getId()));
		ServerTickEvents.END_SERVER_TICK.register(SpiderWrap::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SpiderWrap::allowDamage);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
				isWrapped(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, world, hand) ->
				isWrapped(player) ? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand)));
		UseBlockCallback.EVENT.register((player, world, hand, hit) ->
				isWrapped(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register(SpiderWrap::onUseEntity);
	}

	public static void clearSessionState() {
		PINS.clear();
	}

	/** Cocooned (either side: the attachment is synced to everyone). */
	public static boolean isWrapped(Entity e) {
		return e != null && e.getAttachedOrElse(ModAttachments.SPIDER_WRAPPED, false);
	}

	/** Spin the cocoon round {@code e} (a Web Shot hit while it was stuck on a tightrope or a woven web). */
	public static void wrap(LivingEntity e) {
		if (!(e.level() instanceof ServerLevel level)) {
			return;
		}
		boolean fresh = !isWrapped(e);
		e.setAttached(ModAttachments.SPIDER_WRAPPED, true);
		e.removeAttached(ModAttachments.SPIDER_WRAP_UNTIL); // the tightrope / weave cocoon is for good
		PINS.putIfAbsent(e.getId(), new Pin(level.dimension(), e.position(), level.getGameTime()));
		hold(e, PINS.get(e.getId()).at());
		if (fresh) {
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, c.x, c.y, c.z, 16, e.getBbWidth() * 0.4, e.getBbHeight() * 0.4,
					e.getBbWidth() * 0.4, 0.02);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1.0f, 0.7f);
		}
	}

	/**
	 * v0.16.2 (user request): the same full cocoon for {@code ticks} only -- three quick Web Shots on a creature. A
	 * cocoon that is already permanent stays permanent. The deadline is saved with the entity.
	 */
	public static void wrapFor(LivingEntity e, int ticks) {
		boolean permanent = isWrapped(e) && e.getAttachedOrElse(ModAttachments.SPIDER_WRAP_UNTIL, 0L) == 0L;
		if (permanent) {
			return;
		}
		wrap(e);
		e.setAttached(ModAttachments.SPIDER_WRAP_UNTIL, e.level().getGameTime() + ticks);
	}

	/** Cut the cocoon off {@code e} (sneak + right-click, or a pull). Returns true when it was cocooned. */
	public static boolean release(Entity e, boolean effects) {
		PINS.remove(e.getId());
		if (!isWrapped(e)) {
			return false;
		}
		e.removeAttached(ModAttachments.SPIDER_WRAPPED);
		e.removeAttached(ModAttachments.SPIDER_WRAP_UNTIL);
		if (effects && e.level() instanceof ServerLevel level) {
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, c.x, c.y, c.z, 14, e.getBbWidth() * 0.4, e.getBbHeight() * 0.4,
					e.getBbWidth() * 0.4, 0.05);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.9f, 1.2f);
		}
		return true;
	}

	private static void hold(LivingEntity e, Vec3 pin) {
		if (e.position().distanceToSqr(pin) > 0.0025) {
			e.teleportTo(pin.x, pin.y, pin.z);
		}
		e.setDeltaMovement(Vec3.ZERO);
		e.hurtMarked = true;
		e.resetFallDistance();
		if (e instanceof Mob mob) {
			mob.setTarget(null);
			mob.getNavigation().stop();
			mob.setAggressive(false);
		}
		if (e instanceof Creeper creeper) {
			creeper.setSwellDir(-1);
		}
		if (e.isUsingItem()) {
			e.stopUsingItem(); // a drawn bow / crossbow is let down
		}
	}

	private static void tick(MinecraftServer server) {
		if (PINS.isEmpty()) {
			return;
		}
		PINS.entrySet().removeIf(en -> {
			ServerLevel level = server.getLevel(en.getValue().dim());
			Entity e = level == null ? null : level.getEntity(en.getKey());
			if (!(e instanceof LivingEntity le) || !le.isAlive() || !isWrapped(le)) {
				return true;
			}
			long until = le.getAttachedOrElse(ModAttachments.SPIDER_WRAP_UNTIL, 0L);
			if (until > 0L && level.getGameTime() >= until) {
				release(le, true); // v0.16.2: a timed (three-Web-Shot) cocoon breaks open
				return true;
			}
			// v0.16.2 (user): a web cocoon (permanent) breaks open once no web is holding it -- the web faded, or the
			// world was left and rejoined (webs do not survive that) -- instead of leaving it floating forever. A short
			// grace after loading lets the webs around it load first.
			if (until == 0L && level.getGameTime() % 20 == 0
					&& level.getGameTime() - en.getValue().since() > WEB_GONE_GRACE
					&& com.projecthero.mod.spider.web.WebStructures.strandHolding(le) == null) {
				release(le, true);
				return true;
			}
			hold(le, en.getValue().at());
			return false;
		});
	}

	/** A cocooned entity cannot hurt anything -- melee, its projectiles, its explosion. */
	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		return attacker == null || attacker == victim || !isWrapped(attacker);
	}

	/** Sneak + right-click a cocooned entity as Spider-Man: cut it free. */
	private static InteractionResult onUseEntity(Player player, Level world, InteractionHand hand, Entity entity,
			net.minecraft.world.phys.EntityHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !isWrapped(entity) || !SpiderMan.hasPower(player)) {
			return InteractionResult.PASS;
		}
		if (world.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		release(entity, true);
		if (player instanceof ServerPlayer sp) {
			sp.swing(hand, true);
		}
		return InteractionResult.SUCCESS;
	}
}
