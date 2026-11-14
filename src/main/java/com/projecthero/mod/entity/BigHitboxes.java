package com.projecthero.mod.entity;

import java.util.List;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.4: area searches for entities whose hit-box is far bigger than vanilla expects (Galactus's arm, hand and head
 * boxes are 12-45 blocks wide).
 *
 * <p>The bug this fixes: {@code Level.getEntities(..., AABB, ...)} only looks in the 16-block entity sections within 2
 * blocks of the search box, keyed by each entity's <b>position</b> (the bottom centre of its box). A 23-block fist box
 * has its position 11 blocks below its middle and 11 blocks in from its edge, so a player swinging at the edge of the fist
 * searched sections that never held it: the client's crosshair pick, arrows and every area power missed it. Entities
 * marked {@link Big} are kept in a per-level list (no static state; the list lives on the {@code Level}) and every area
 * search adds the ones whose box really overlaps ({@code LevelBigHitboxMixin}).
 */
public final class BigHitboxes {
	private BigHitboxes() {
	}

	/** An entity whose hit-box can be much bigger than an entity section. */
	public interface Big {
	}

	/** Implemented on {@code Level} by {@code LevelBigHitboxMixin}. */
	public interface Holder {
		List<Entity> projecthero$bigHitboxes();
	}

	public static void initialize() {
		ServerEntityEvents.ENTITY_LOAD.register(BigHitboxes::onLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(BigHitboxes::onUnload);
	}

	public static void onLoad(Entity e, Level level) {
		if (e instanceof Big && level instanceof Holder h) {
			List<Entity> list = h.projecthero$bigHitboxes();
			if (!list.contains(e)) {
				list.add(e);
			}
		}
	}

	public static void onUnload(Entity e, Level level) {
		if (e instanceof Big && level instanceof Holder h) {
			h.projecthero$bigHitboxes().remove(e);
		}
	}

	/** Adds the big entities overlapping {@code box} that the section search missed. */
	public static void addMissed(Level level, Entity except, AABB box, Predicate<? super Entity> predicate, List<Entity> out) {
		if (!(level instanceof Holder h)) {
			return;
		}
		List<Entity> big = h.projecthero$bigHitboxes();
		if (big.isEmpty()) {
			return;
		}
		for (int i = big.size() - 1; i >= 0; i--) {
			Entity e = big.get(i);
			if (e.isRemoved()) {
				big.remove(i);
				continue;
			}
			if (e != except && e.getBoundingBox().intersects(box) && !out.contains(e) && predicate.test(e)) {
				out.add(e);
			}
		}
	}

	/** The typed form ({@code getEntitiesOfClass} and friends). */
	public static <T extends Entity> void addMissed(Level level, EntityTypeTest<Entity, T> test, AABB box, Predicate<? super T> predicate,
			List<? super T> out, int max) {
		if (!(level instanceof Holder h)) {
			return;
		}
		List<Entity> big = h.projecthero$bigHitboxes();
		if (big.isEmpty()) {
			return;
		}
		for (int i = big.size() - 1; i >= 0 && out.size() < max; i--) {
			Entity e = big.get(i);
			if (e.isRemoved()) {
				big.remove(i);
				continue;
			}
			if (!e.getBoundingBox().intersects(box)) {
				continue;
			}
			T t = test.tryCast(e);
			if (t != null && !out.contains(t) && predicate.test(t)) {
				out.add(t);
			}
		}
	}
}
