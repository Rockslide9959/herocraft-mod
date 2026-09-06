package com.projecthero.mod.symbiote;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.symbiote.entity.SymbioteEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * A rare naturally spawning hostile mob that a Symbiote has taken over. It is one of the three ways to
 * obtain the Symbiote (spec: "a mob can randomly spawn with it very rarely and the symbiote changes
 * its behaviour").
 *
 * <h3>How the Symbiote changes it</h3>
 * <ul>
 *   <li>tougher (+60% max health), faster (+35% move speed), hits harder (+5 melee), spots you from
 *       much further away (+24 follow range), and shrugs off knockback;</li>
 *   <li>a constant black particle aura and an italic "Symbiote Host" name on look;</li>
 *   <li>it never de-spawns, so you can hunt it down;</li>
 *   <li>when it dies the Symbiote <b>leaves the corpse</b> as a free-floating {@link SymbioteEntity} --
 *       walk into it (or right-click it) as a Spider-Man to bond.</li>
 * </ul>
 *
 * <p>Attribute changes are applied to the mob's <em>base</em> values (which vanilla persists), so a
 * chunk reload needs no re-apply and repeated marking is impossible -- {@link #mark} runs exactly once,
 * from {@code SymbioteHostSpawns} on the spawn path.
 */
public final class SymbioteHost {
	private SymbioteHost() {
	}

	public static boolean is(Mob mob) {
		return mob.getAttachedOrElse(ModAttachments.SYMBIOTE_HOST, false);
	}

	/** Take over a freshly spawned mob. Called once, from the spawn hook. */
	public static void mark(Mob mob) {
		mob.setAttached(ModAttachments.SYMBIOTE_HOST, true);

		scaleBase(mob, Attributes.MAX_HEALTH, 1.6);
		mob.setHealth(mob.getMaxHealth());
		scaleBase(mob, Attributes.MOVEMENT_SPEED, 1.35);
		addBase(mob, Attributes.ATTACK_DAMAGE, 5.0);
		addBase(mob, Attributes.FOLLOW_RANGE, 24.0);
		AttributeInstance kb = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kb != null) {
			kb.setBaseValue(Math.min(1.0, kb.getBaseValue() + 0.4));
		}

		mob.setCustomName(Component.translatable("entity.projecthero.symbiote_host")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		mob.setPersistenceRequired();
	}

	/** Per-tick upkeep, from the {@code Mob#aiStep} mixin. A no-op unless this mob is a host. */
	public static void serverTick(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level) || !is(mob)) {
			return;
		}
		if (mob.tickCount % 6 == 0) {
			level.sendParticles(ParticleTypes.SQUID_INK,
					mob.getX(), mob.getY() + mob.getBbHeight() * 0.55, mob.getZ(),
					2, mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.4, mob.getBbWidth() * 0.4, 0.005);
		}
		if (mob.tickCount % 14 == 0) {
			level.sendParticles(ParticleTypes.SMOKE,
					mob.getX(), mob.getY() + mob.getBbHeight() * 0.4, mob.getZ(),
					3, mob.getBbWidth() * 0.5, mob.getBbHeight() * 0.5, mob.getBbWidth() * 0.5, 0.01);
		}
	}

	/** Death hook: the Symbiote abandons its dead host and waits nearby for a new one. */
	public static void onDeath(LivingEntity entity) {
		if (!(entity instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || !is(mob)) {
			return;
		}
		SymbioteEntity dropped = SymbioteEntity.spawn(level,
				mob.getX(), mob.getY() + mob.getBbHeight() * 0.5 + 0.2, mob.getZ());
		level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + 0.6, mob.getZ(),
				50, 0.5, 0.5, 0.5, 0.15);
		if (dropped != null) {
			for (Player p : level.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(28.0))) {
				p.displayClientMessage(Component.translatable("message.projecthero.symbiote.host_freed")
						.withStyle(ChatFormatting.DARK_GRAY), false);
			}
		}
	}

	private static void scaleBase(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double factor) {
		AttributeInstance instance = mob.getAttribute(attr);
		if (instance != null) {
			instance.setBaseValue(instance.getBaseValue() * factor);
		}
	}

	private static void addBase(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double delta) {
		AttributeInstance instance = mob.getAttribute(attr);
		if (instance != null) {
			instance.setBaseValue(instance.getBaseValue() + delta);
		}
	}
}
