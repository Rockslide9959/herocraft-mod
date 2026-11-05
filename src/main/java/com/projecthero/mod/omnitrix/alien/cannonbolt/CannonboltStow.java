package com.projecthero.mod.omnitrix.alien.cannonbolt;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 round 3 (user: "if player is in ball form then up to 2 squad mates can right click the player to climb inside them, they are
 * only released when player uncurls and get nausea from the experience, dont combine hitboxes and model just make the squadmates
 * disappear so you cant see them").
 *
 * <p>A mate inside is simply a passenger of the ball: vanilla carries them, and the ride is the only state there is, so nothing can
 * go stale and they can never be lost. If the ride ever ends any way at all (he dies, logs out, either of them leaves) they are just
 * an ordinary player again. While it lasts they are hidden from every client ({@code CannonboltHideStowedMixin}), have no hitbox of
 * their own ({@code CannonboltStowMixin}), cannot Sneak out, take no damage, cannot fight or use anything, and mobs lose them.
 * {@link #releaseAll} puts them down beside him with Nausea.
 */
public final class CannonboltStow {
	public static final int MAX_INSIDE = 2;
	/** Nausea after getting out (ticks). */
	public static final int NAUSEA_TICKS = 140;

	private CannonboltStow() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof Player p && stowed(p)
				&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)));
		AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> stowed(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> stowed(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> stowed(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> stowed(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> stowed(player) ? InteractionResultHolder.fail(player.getItemInHand(hand))
				: InteractionResultHolder.pass(player.getItemInHand(hand)));
	}

	/** Is {@code p} inside a Cannonbolt ball right now? Both sides: it is read straight off the synced ride. */
	public static boolean stowed(Entity p) {
		return p instanceof Player && p.getVehicle() instanceof Player host && CannonboltKit.rolling(host);
	}

	/** The players inside {@code host}. */
	public static List<ServerPlayer> inside(ServerPlayer host) {
		List<ServerPlayer> out = new ArrayList<>();
		for (Entity e : host.getPassengers()) {
			if (e instanceof ServerPlayer m) {
				out.add(m);
			}
		}
		return out;
	}

	/**
	 * {@code mate} right-clicked the ball {@code host} (from {@code OmnitrixRiding.onUse}). True if the click was this; a stranger's
	 * click falls through. A ball with someone inside can't be climbed into by another ball, and nobody ends up in two at once.
	 */
	public static boolean climbIn(ServerPlayer mate, ServerPlayer host) {
		if (!CannonboltKit.rolling(host) || !Squads.areAllies(mate, host) || mate == host) {
			return false;
		}
		if (mate.isSpectator() || !mate.isAlive() || !host.isAlive() || host.isPassenger() || mate.isVehicle()) {
			return true;
		}
		if (inside(host).size() >= MAX_INSIDE) {
			mate.displayClientMessage(Component.translatable("message.projecthero.omnitrix.cannonbolt.full").withStyle(ChatFormatting.GRAY), true);
			return true;
		}
		if (mate.isPassenger()) {
			mate.stopRiding();
		}
		if (!mate.startRiding(host, true)) {
			return true;
		}
		mate.resetFallDistance();
		mate.displayClientMessage(Component.translatable("message.projecthero.omnitrix.cannonbolt.inside", host.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
		host.displayClientMessage(Component.translatable("message.projecthero.omnitrix.cannonbolt.carrying", mate.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
		OmnitrixSounds.play((ServerLevel) host.level(), host.position(), CannonboltKit.SND_CURL, 0.7f, 1.5f);
		return true;
	}

	/** Every player inside {@code host} out beside him, dizzy. Safe to call at any time, for any reason, with nobody inside. */
	public static void releaseAll(ServerPlayer host) {
		for (ServerPlayer m : inside(host)) {
			m.stopRiding();
			if (!m.isAlive()) {
				continue;
			}
			Vec3 at = spotFor(m, host);
			m.teleportTo(at.x, at.y, at.z);
			m.setDeltaMovement(Vec3.ZERO);
			m.resetFallDistance();
			m.addEffect(new MobEffectInstance(MobEffects.CONFUSION, NAUSEA_TICKS, 0));
		}
	}

	/** A free spot beside the ball for {@code m}: all round him at his feet or a step up, else on top of him, else right where he is. */
	private static Vec3 spotFor(ServerPlayer m, ServerPlayer host) {
		Vec3 c = host.position();
		double out = host.getBbWidth() * 0.5 + m.getBbWidth() * 0.5 + 0.1;
		float start = m.getRandom().nextFloat() * Mth2PI;
		for (int up = 0; up <= 2; up++) {
			for (int i = 0; i < 8; i++) {
				double a = start + i * Mth2PI / 8.0;
				Vec3 at = c.add(Math.cos(a) * out, up, Math.sin(a) * out);
				if (free(m, at) && (hasFloor(m, at) || up == 2)) {
					return at;
				}
			}
		}
		Vec3 top = c.add(0, host.getBbHeight(), 0);
		return free(m, top) ? top : c;
	}

	private static final float Mth2PI = (float) (Math.PI * 2.0);

	private static boolean free(ServerPlayer m, Vec3 at) {
		AABB box = m.getDimensions(m.getPose()).makeBoundingBox(at);
		return m.level().noCollision(m, box.deflate(1.0e-3));
	}

	private static boolean hasFloor(ServerPlayer m, Vec3 at) {
		for (int d = 1; d <= 3; d++) {
			BlockPos below = BlockPos.containing(at.x, at.y - d + 0.5, at.z);
			if (!m.level().getBlockState(below).getCollisionShape(m.level(), below).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Mobs lose whoever climbed in: anything hunting a mate near him turns on the ball instead (every half second). */
	static void hide(ServerPlayer host) {
		List<ServerPlayer> in = inside(host);
		if (in.isEmpty()) {
			return;
		}
		for (Mob mob : host.level().getEntitiesOfClass(Mob.class, host.getBoundingBox().inflate(32.0), m -> in.contains(m.getTarget()))) {
			mob.setTarget(host);
		}
	}
}
