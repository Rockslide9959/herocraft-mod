package com.projecthero.mod.ironman.ability;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlade;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user): "the silver centurion armour should have a blade ability where it deploys blades to use". The Mk VII
 * Silver Centurion ({@code mark_6}) snikts one long, curved, polished-silver claw-style blade out of a red-and-silver
 * housing on the back of each gauntlet, over the knuckles and past the fist (client {@code CenturionBladeLayer}, modelled
 * on Wolverine's claws; in / out over {@code IronManBladeLook.EXTEND_TICKS}).
 * <ul>
 *   <li><b>V</b> -- Deploy Blades: a toggle (the V = mode key). Out: 10 energy, then 1 per second while they stay out
 *       ({@link IronManBlade#tick} drains it and retracts them on a dry reactor / brownout); in again: free.</li>
 *   <li>While out: melee +{@link IronManBlade#CENTURION_MELEE_BONUS} and every swing that connects also slashes enemies
 *       within {@link #SWEEP_RADIUS} blocks of the target for {@link #SWEEP_DAMAGE} (a sweep arc).</li>
 *   <li><b>Shift+V</b> -- Blade Whirl: snaps the blades out if they are in, lunges forward, then two full spinning slashes
 *       ({@code move.damage()} each) round you, 4.5 blocks, with knockback.</li>
 * </ul>
 * The blades share the {@code IRON_MAN_BLADES} flag with the old Mark V blades (synced to every viewer).
 */
public final class IronManCenturionBlades {
	public static final String SUIT = "mark_6";
	public static final float SWEEP_DAMAGE = 6f;
	public static final double SWEEP_RADIUS = 2.5;
	public static final double WHIRL_RADIUS = 4.5;
	/** The two spin hits of the Blade Whirl (ticks after the press). */
	public static final int[] WHIRL_HITS = { 5, 10 };
	public static final int WHIRL_TICKS = 16;
	public static final int DEPLOY_TICKS = 10;

	private IronManCenturionBlades() {
	}

	public static void initialize() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer sp && entity instanceof LivingEntity target && out(sp)) {
				sweep(sp, target);
			}
			return InteractionResult.PASS;
		});
	}

	/** Are the Silver Centurion's blades out on {@code p}? */
	public static boolean out(ServerPlayer p) {
		return SUIT.equals(IronManArmor.wornSuitId(p)) && IronManBlade.active(p);
	}

	/** V: deploy (true -- the router pays 10 and starts the 1 s cooldown) or retract (false: free). */
	static boolean toggle(ServerPlayer p) {
		if (IronManBlade.active(p)) {
			p.setAttached(ModAttachments.IRON_MAN_BLADES, false);
			IronManComicFx.pose(p, IronManAbilityFx.COMIC_BLADE_DEPLOY, DEPLOY_TICKS);
			IronManSounds.move(p, IronManSounds.BLADE_RETRACT, 0.9f, 0.85f);
			snikt(p, 1.15f);
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.ironman.blades_in"), true);
			return false;
		}
		extend(p, true);
		return true;
	}

	private static void extend(ServerPlayer p, boolean announce) {
		p.setAttached(ModAttachments.IRON_MAN_BLADES, true);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_BLADE_DEPLOY, DEPLOY_TICKS);
		IronManSounds.move(p, IronManSounds.BLADE_EXTEND, 1.0f, 1.1f);
		snikt(p, 1.35f);
		IronManSounds.play(p, IronManSounds.BLADE_RING, 0.8f, 1.15f);
		ServerLevel level = IronManComicFx.level(p);
		for (int side = 0; side < 2; side++) {
			Vec3 w = wrist(p, side);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, w.x, w.y, w.z, 6, 0.08, 0.08, 0.08, 0.12);
			level.sendParticles(ParticleTypes.END_ROD, w.x, w.y, w.z, 2, 0.05, 0.05, 0.05, 0.02);
		}
		if (announce) {
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.ironman.blades_out"), true);
		}
	}

	/** v0.16.7 (user): the claw-style "snikt" of a blade sliding out of / back into its housing (metal on metal). */
	private static void snikt(ServerPlayer p, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CHAIN_BREAK, net.minecraft.sounds.SoundSource.PLAYERS,
				0.8f, pitch);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE,
				net.minecraft.sounds.SoundSource.PLAYERS, 0.3f, pitch + 0.25f);
	}

	/** Shift+V: the Blade Whirl. */
	static boolean whirl(ServerPlayer p, float damage) {
		if (!IronManBlade.active(p)) {
			extend(p, false);
		}
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_BLADE_WHIRL, WHIRL_TICKS);
		IronManComicFx.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
		// the lunge: a short low burst along the look (flat)
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() > 1.0e-4) {
			flat = flat.normalize().scale(1.15);
			p.setDeltaMovement(flat.x, Math.max(0.15, p.getDeltaMovement().y), flat.z);
			p.hurtMarked = true;
		}
		for (int i = 0; i < WHIRL_HITS.length; i++) {
			final int n = i;
			IronManComicState.later(p, WHIRL_HITS[i], w -> {
				if (!SUIT.equals(IronManArmor.wornSuitId(w))) {
					return;
				}
				ServerLevel level = IronManComicFx.level(w);
				Vec3 c = w.position().add(0, w.getBbHeight() * 0.5, 0);
				for (LivingEntity e : IronManComicFx.around(w, c, WHIRL_RADIUS)) {
					IronManComicFx.hit(w, e, damage);
					IronManComicFx.shove(e, c, n == 0 ? 0.5 : 1.1, 0.25);
					Vec3 ec = IronManComicFx.centre(e);
					level.sendParticles(ParticleTypes.CRIT, ec.x, ec.y, ec.z, 8, 0.2, 0.3, 0.2, 0.3);
				}
				// the silver arc: sweep marks round the wearer at blade height, sparks off the edge
				for (int k = 0; k < 10; k++) {
					double a = (k / 10.0) * Math.PI * 2 + n * 0.3;
					Vec3 at = c.add(Math.cos(a) * 2.6, n == 0 ? -0.1 : 0.2, Math.sin(a) * 2.6);
					level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.05);
				}
				IronManComicFx.sound(w, SoundEvents.PLAYER_ATTACK_SWEEP, 1.1f, n == 0 ? 0.9f : 0.75f);
				IronManComicFx.sound(w, IronManSounds.BLADE_RING, 0.7f, 1.3f + n * 0.15f);
			});
		}
		return true;
	}

	/** A connected melee swing with the blades out: a silver sweep arc that also slashes enemies round the target. */
	static void sweep(ServerPlayer p, LivingEntity target) {
		ServerLevel level = IronManComicFx.level(p);
		Vec3 c = IronManComicFx.centre(target);
		for (LivingEntity e : IronManComicFx.around(p, c, SWEEP_RADIUS)) {
			if (e != target) {
				IronManComicFx.hit(p, e, SWEEP_DAMAGE);
				IronManComicFx.shove(e, p.position(), 0.4, 0.1);
			}
		}
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 5, 0.2, 0.2, 0.2, 0.1);
		IronManComicFx.soundAt(level, c, IronManSounds.BLADE_RING, 0.45f, 1.5f + p.getRandom().nextFloat() * 0.2f);
	}

	/** Gametests: a blade-out melee sweep on {@code target}. */
	public static void sweepForTests(ServerPlayer p, LivingEntity target) {
		sweep(p, target);
	}

	/** Roughly where a wrist is (for sparks). */
	private static Vec3 wrist(ServerPlayer p, int side) {
		Vec3 look = p.getLookAngle();
		Vec3 right = IronManAbilities.rightOf(p, look);
		return p.position().add(0, p.getBbHeight() * 0.45, 0).add(look.scale(0.3)).add(right.scale(side == 0 ? 0.4 : -0.4));
	}

	/** The worn suit if it is the Silver Centurion, else null. */
	static IronManSuit suit(ServerPlayer p) {
		return SUIT.equals(IronManArmor.wornSuitId(p)) ? IronManSuits.byId(SUIT) : null;
	}
}
