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
 * <p>v0.14.4: a free Symbiote can now also take over passive animals ({@link #isPassiveHostSpecies}) -- they
 * turn on players ({@code SymbioteMobGoals}) -- and a tamed wolf or cat, which becomes a loyal
 * {@link SymbiotePet} instead ({@link #takeOver} dispatches). Hosts are drawn black on the client (the attachment
 * is synced now).
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

	/**
	 * Take over a mob. Called once per mob: from the natural-spawn hook, or when a free
	 * {@link SymbioteEntity} crawls onto a mob and takes control of it (never onto an existing host).
	 */
	/**
	 * v0.14.4: the one entry point for "a Symbiote takes this mob": a tamed wolf or cat becomes a loyal
	 * {@link SymbiotePet}; anything else becomes a hostile host ({@link #mark}).
	 */
	public static void takeOver(Mob mob) {
		if (SymbiotePet.canBond(mob) && mob instanceof net.minecraft.world.entity.TamableAnimal pet) {
			SymbiotePet.bond(pet, SymbiotePet.WILD);
			return;
		}
		mark(mob);
	}

	/**
	 * v0.14.4: the passive animals a free Symbiote may now take over, on top of every mob that can already fight.
	 * Goal-driven animals only -- brain-driven ones (goats, axolotls, frogs, camels, sniffers, armadillos) would
	 * run the Symbiote's goals and their own brain at once.
	 */
	public static boolean isPassiveHostSpecies(Mob mob) {
		return mob instanceof net.minecraft.world.entity.animal.Cow
				|| mob instanceof net.minecraft.world.entity.animal.Pig
				|| mob instanceof net.minecraft.world.entity.animal.Sheep
				|| mob instanceof net.minecraft.world.entity.animal.Chicken
				|| mob instanceof net.minecraft.world.entity.animal.Rabbit
				|| mob instanceof net.minecraft.world.entity.animal.Fox
				|| mob instanceof net.minecraft.world.entity.animal.Ocelot
				|| mob instanceof net.minecraft.world.entity.animal.Wolf
				|| mob instanceof net.minecraft.world.entity.animal.Cat;
	}

	public static void mark(Mob mob) {
		mob.setAttached(ModAttachments.SYMBIOTE_HOST, true);
		// v0.14.4: an infested animal stops being prey -- it hunts players (SymbioteMobGoals), its wool turns
		// black, and the goo hardens over it a little.
		if (SymbioteMobGoals.isInfestedAnimal(mob)) {
			if (mob instanceof net.minecraft.world.entity.animal.Sheep sheep) {
				sheep.setColor(net.minecraft.world.item.DyeColor.BLACK);
			}
			if (mob instanceof net.minecraft.world.entity.animal.Animal animal) {
				animal.resetLove();
			}
			addBase(mob, Attributes.ARMOR, 4.0);
			SymbioteMobGoals.installHostile(mob);
		}

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
		// v0.14.4: someone tamed an infested wolf / stray cat -- the Symbiote takes its new owner's side.
		if (mob instanceof net.minecraft.world.entity.TamableAnimal tamed && tamed.isTame()
				&& SymbiotePet.isPetSpecies(mob)) {
			convertTamed(tamed);
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

	/** An infested wolf/cat was tamed: stop being a hostile host and become a (wild-origin) Symbiote Pet. */
	static void convertTamed(net.minecraft.world.entity.TamableAnimal pet) {
		pet.removeAttached(ModAttachments.SYMBIOTE_HOST);
		SymbioteMobGoals.removeAll(pet);
		pet.setTarget(null);
		Component name = pet.getCustomName();
		if (name != null && name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
				&& "entity.projecthero.symbiote_host".equals(t.getKey())) {
			pet.setCustomName(null);
		}
		SymbiotePet.bond(pet, SymbiotePet.WILD);
	}

	/** Death hook: the Symbiote abandons its dead host and waits nearby for a new one. */
	public static void onDeath(LivingEntity entity) {
		SymbiotePet.onDeath(entity);
		if (!(entity instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || !is(mob)) {
			return;
		}
		SymbioteEntity dropped = SymbioteEntity.spawn(level,
				mob.getX(), mob.getY() + mob.getBbHeight() * 0.5 + 0.2, mob.getZ());
		level.sendParticles(ParticleTypes.SQUID_INK, mob.getX(), mob.getY() + 0.6, mob.getZ(),
				50, 0.5, 0.5, 0.5, 0.15);
		if (dropped != null) {
			// v0.13.19: it is a real creature now and will go looking for a new host on its own -- give
			// whoever killed the old one five seconds to reach it first.
			dropped.setHuntDelay(100);
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
