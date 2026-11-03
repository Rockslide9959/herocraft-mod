package com.projecthero.mod.omnitrix.alien.stinkfly;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.2 (user: "yes build these things please i like them" -- the Stinkfly revamp's Sticky Goo rule): <b>Gooed</b>. A creature
 * standing in Stinkfly's goo (a patch, a pool, the Goo Wall, the Goo Cocoon's spray) wades as if through honey: much slower, no
 * jumping at all, no sprinting. Goo is flammable (reboot): fire on a Gooed creature lights it -- one burst of 6 round it and 4 s
 * burning, and the goo is gone. A real mob effect, so vanilla applies and removes the modifiers (expiry, milk, death) and
 * nothing can be left behind. Given hidden (no particles, no icon): the goo decals are the look.
 */
public final class StinkflyGooEffect extends MobEffect {
	public static final Holder<MobEffect> HOLDER = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ProjectHeroMod.id("stinkfly_goo"),
			new StinkflyGooEffect()
					.addAttributeModifier(Attributes.MOVEMENT_SPEED, ProjectHeroMod.id("stinkfly_goo_speed"), -0.6,
							AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
					.addAttributeModifier(Attributes.JUMP_STRENGTH, ProjectHeroMod.id("stinkfly_goo_jump"), -1.0,
							AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

	/** The burst when the goo on a creature catches fire. */
	public static final float BURN_DAMAGE = 10f; // v0.17.2 (user: "can you make the damage of all of ben 10s alien stronger ... make each alien stronger accordingly please"): was 6
	public static final double BURN_RADIUS = 2.0;
	public static final int BURN_SECONDS = 4;

	private StinkflyGooEffect() {
		super(MobEffectCategory.HARMFUL, StinkflyKit.GOO);
	}

	/** Class-load hook (registers the effect; from {@code StinkflyKit.register}). */
	public static void initialize() {
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		return true;
	}

	@Override
	public boolean applyEffectTick(LivingEntity e, int amplifier) {
		if (!(e.level() instanceof ServerLevel l)) {
			return true;
		}
		if (e instanceof Player pl && pl.isSprinting()) {
			pl.setSprinting(false);
		}
		if (e.isOnFire()) {
			// the goo goes up: a burst of 6 round it, everything in it burning for 4 s; the goo is burnt away (false removes it)
			ignite(l, e.position().add(0, e.getBbHeight() * 0.4, 0), null, e);
			return false;
		}
		return true;
	}

	/**
	 * The goo burning at {@code at}: 6 to everything within 2 and 4 s alight (fire damage; never {@code spare} -- Stinkfly
	 * himself when it is his own patch; {@code ticking}: the creature whose Gooed effect is ticking right now, which drops it by
	 * the false return -- never removed mid-iteration).
	 */
	public static void ignite(ServerLevel l, net.minecraft.world.phys.Vec3 at, @org.jetbrains.annotations.Nullable LivingEntity spare,
			@org.jetbrains.annotations.Nullable LivingEntity ticking) {
		com.projecthero.mod.quirk.fx.QuirkFx.blast(l, at).colours(0xFFE890, 0xFF7A1A).size(2.2f).life(12)
				.flags(com.projecthero.mod.quirk.fx.QuirkFx.F_NO_SMOKE).spawn();
		l.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.9f, 0.8f);
		for (LivingEntity v : l.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(BURN_RADIUS),
				v -> v.isAlive() && v != spare && v.distanceToSqr(at) <= BURN_RADIUS * BURN_RADIUS * 1.5)) {
			if (v != ticking) {
				v.removeEffect(HOLDER);
			}
			v.hurt(l.damageSources().inFire(), BURN_DAMAGE);
			if (!v.fireImmune()) {
				v.igniteForSeconds(BURN_SECONDS);
			}
		}
	}
}
