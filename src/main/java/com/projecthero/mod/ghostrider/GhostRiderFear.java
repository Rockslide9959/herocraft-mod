package com.projecthero.mod.ghostrider;

import java.util.UUID;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 rework (user): "he looks like a demon and entities should fear him". While the Spirit is out, a per-player scan
 * every {@link GhostRiderConfig#FEAR_INTERVAL} ticks (no per-entity AI injection):
 * <ul>
 *   <li><b>Golems</b> (iron and snow) within 16 blocks attack him. A player-built iron golem still won't (vanilla's own
 *       rule); turning back calms every golem that was after him.</li>
 *   <li><b>Villagers</b> panic and run (their brain's own panic, fed his presence as if he'd hurt them -- the memory only,
 *       so no gossip and no reputation loss) within 12 blocks; <b>wandering traders</b> run too. Both refuse to trade
 *       and shake their heads ({@link #initialize}).</li>
 *   <li><b>Animals</b> within 10 blocks bolt away from him. <b>Cats and wolves</b> that aren't his hiss / growl and keep
 *       their distance; his own pets stay loyal.</li>
 *   <li>Creatures under his <b>Judgement Mark</b> cower: slowed (bosses aside).</li>
 * </ul>
 * Undead keep the older rule ({@code GhostRider#tickUndeadFear}: they lose their nerve).
 */
public final class GhostRiderFear {
	private GhostRiderFear() {
	}

	public static void initialize() {
		// villagers and traders won't deal with a demon: a head shake instead of the trade screen
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(entity instanceof AbstractVillager villager) || !GhostRider.transformed(player) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (!world.isClientSide()) {
				villager.setUnhappyCounter(40);
				world.playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0f, 1.0f);
				if (player instanceof ServerPlayer sp) {
					GhostRider.say(sp, "message.projecthero.ghost_rider.no_trade", ChatFormatting.GRAY);
				}
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** The scan (from {@code GhostRider.tick}, transformed only). */
	static void tick(ServerPlayer player, long now) {
		if (now % GhostRiderConfig.FEAR_INTERVAL != 0 || !player.isAlive() || player.isSpectator()) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		AABB box = player.getBoundingBox().inflate(GhostRiderConfig.GOLEM_RANGE);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && (!m.isNoAi() || m instanceof AbstractGolem))) {
			double d2 = mob.distanceToSqr(player);
			if (mob instanceof IronGolem || mob instanceof SnowGolem) {
				if (d2 <= GhostRiderConfig.GOLEM_RANGE * GhostRiderConfig.GOLEM_RANGE && mob.getTarget() != player && mob.canAttack(player)
						&& !player.getAbilities().invulnerable && mob.hasLineOfSight(player)) { // (creative: invulnerable)
					mob.setTarget(player);
				}
			} else if (mob instanceof AbstractVillager v) {
				if (d2 <= sq(GhostRiderConfig.VILLAGER_FEAR_RANGE) && !mob.isNoAi()) {
					scare(v, player);
				}
			} else if (mob instanceof Cat || mob instanceof Wolf) {
				TamableAnimal pet = (TamableAnimal) mob;
				if (d2 <= sq(GhostRiderConfig.ANIMAL_FEAR_RANGE) && !mob.isNoAi() && !(pet.isTame() && pet.isOwnedBy(player))) {
					if (mob.getTarget() == player) {
						mob.setTarget(null);
					}
					flee(pet, player, 1.3);
					if (mob.getRandom().nextInt(4) == 0) {
						level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), mob instanceof Cat ? SoundEvents.CAT_HISS : SoundEvents.WOLF_GROWL,
								SoundSource.NEUTRAL, 0.8f, 1.0f);
					}
				}
			} else if (mob instanceof Animal animal) {
				if (d2 <= sq(GhostRiderConfig.ANIMAL_FEAR_RANGE) && !mob.isNoAi() && !(animal instanceof TamableAnimal t && t.isOwnedBy(player))) {
					flee(animal, player, 1.6);
				}
			}
		}
		// the marked cower
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(GhostRiderConfig.MARK_RANGE),
				e -> e != player && GhostRiderDamage.isMarked(e) && !GhostRiderCombat.isBoss(e))) {
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, GhostRiderConfig.FEAR_INTERVAL + 10, 1, false, false, true));
		}
	}

	private static double sq(double r) {
		return r * r;
	}

	/**
	 * Runs {@code mob} to a spot away from him (re-issued every scan, so wandering goals can't take over). With no path
	 * there (open water, a ledge, nowhere walkable picked), it just walks straight away.
	 */
	static void flee(PathfinderMob mob, Player from, double speed) {
		Vec3 to = DefaultRandomPos.getPosAway(mob, 16, 7, from.position());
		if (to != null) {
			net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(net.minecraft.core.BlockPos.containing(to), 0);
			if (path != null && path.getNodeCount() > 2 && path.getEndNode() != null
					&& path.getEndNode().asVec3().distanceToSqr(from.position()) > mob.distanceToSqr(from) + 9.0
					&& mob.getNavigation().moveTo(path, speed)) {
				return;
			}
		}
		Vec3 away = mob.position().subtract(from.position());
		away = away.horizontalDistanceSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
		Vec3 step = mob.position().add(away.scale(6.0));
		mob.getNavigation().stop();
		mob.getMoveControl().setWantedPosition(step.x, mob.getY(), step.z, speed);
	}

	private static void scare(AbstractVillager v, ServerPlayer player) {
		if (v instanceof Villager villager) {
			// the brain's own panic: it runs from whoever is in HURT_BY_ENTITY (setting memories adds no gossip)
			DamageSource src = player.damageSources().playerAttack(player);
			villager.getBrain().setMemoryWithExpiry(MemoryModuleType.HURT_BY, src, 100L);
			villager.getBrain().setMemoryWithExpiry(MemoryModuleType.HURT_BY_ENTITY, player, 100L);
		} else {
			flee(v, player, 0.7);
		}
	}

	/** Turning back: golems that were after him stand down. */
	static void calm(ServerPlayer player) {
		UUID id = player.getUUID();
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48.0),
				m -> m instanceof AbstractGolem && m.getTarget() != null && m.getTarget().getUUID().equals(id))) {
			mob.setTarget(null);
			if (mob instanceof IronGolem golem) {
				golem.stopBeingAngry();
			}
		}
	}
}
