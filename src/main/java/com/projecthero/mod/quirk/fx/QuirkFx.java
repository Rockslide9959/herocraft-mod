package com.projecthero.mod.quirk.fx;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Quirk framework: the VFX toolkit's server API. Every effect is one {@link QuirkFxEntity} drawn from code geometry
 * by {@code client.quirk.fx.QuirkFxRenderer} (additive glow for fire / light, translucent lit quads for smoke, decals and
 * stone). Spawn on the server; the effect animates itself on every client from the one spawn packet.
 *
 * <pre>
 *   QuirkFx.blast(level, pos).colours(0xFFFFFF, 0xFF7A1A).size(3.5f).life(16).spawn();
 *   QuirkFx.ring(level, feet).colours(0xFFE0B0, 0xFF5010).size(6f).thickness(0.6f).ground().spawn();
 *   QuirkFx.ribbon(level, hand, target).colours(0xFFFFFF, 0x7A3CFF).size(0.35f).life(8).spawn();       // A to B
 *   QuirkFx.trail(level, player).colours(0x9FD8FF, 0x3A7BFF).size(0.25f).life(30).spawn();              // follows, records its path
 *   QuirkFx.cone(level, palm, look).colours(0xFFF4C0, 0xFF4A10).size(2.5f).length(8f).style(QuirkFx.CONE_FLAME).spawn();
 *   QuirkFx.spike(level, ground, Vec3.UP).size(2.4f).block(stone).spawn();
 *   QuirkFx.shards(level, at).block(dirt).size(2.0f).count(24).reassemble().spawn();
 *   QuirkFx.decal(level, ground).style(QuirkFx.DECAL_SCORCH).size(3f).life(300).spawn();
 *   QuirkFx.flipbook(level, at).style(QuirkFx.BOOK_FIREBALL).size(1.6f).life(16).spawn();
 * </pre>
 * Colours are RGB ramps (A at the start / the core, B at the end / the edge). {@link Ease} shapes the main growth curve.
 */
public final class QuirkFx {
	/** The effect kinds. Ids are synced: append only. */
	public enum Kind {
		NONE(0),
		/** A white flash sphere, then an expanding noise-wobbled fire shell, a ground shock ring and a smoke crown. */
		BLAST(1),
		/** A flat expanding ring (shock, gravity, steam); {@link #F_VERTICAL} stands it up facing {@code vec}. */
		RING(2),
		/** A tapered ribbon: from the spawn point to {@code vec} (an end point), or a trail of a followed entity's path. */
		RIBBON(3),
		/** A cone sheet along {@code vec} (a direction): flame, debris, or {@link #CONE_BEAM} a straight beam. */
		CONE(4),
		/** A jagged faceted spike rising out of the ground along {@code vec}, textured from {@code block}, crack decal at its base. */
		SPIKE(5),
		/** Block-textured fragments that burst out, hang, and fall -- or fly back and reassemble ({@link #F_REASSEMBLE}). */
		SHARDS(6),
		/** A flat ground mark fading over its life: scorch, crack web, shadow pool, fingertip prints. */
		DECAL(7),
		/** An animated 8-frame billboard: fireball, smoke puff, ink splash. */
		FLIPBOOK(8);

		public final int id;

		Kind(int id) {
			this.id = id;
		}

		public static Kind byId(int id) {
			for (Kind k : values()) {
				if (k.id == id) {
					return k;
				}
			}
			return NONE;
		}
	}

	/** Easing of an effect's main growth (radius / length over its life). Ids are synced: append only. */
	public enum Ease {
		LINEAR(0), OUT_QUAD(1), OUT_CUBIC(2), OUT_EXPO(3), IN_OUT(4), OUT_BACK(5);

		public final int id;

		Ease(int id) {
			this.id = id;
		}

		public static Ease byId(int id) {
			for (Ease e : values()) {
				if (e.id == id) {
					return e;
				}
			}
			return OUT_CUBIC;
		}

		/** Client and server: eased 0..1. */
		public float apply(float t) {
			t = Math.max(0f, Math.min(1f, t));
			return switch (this) {
				case LINEAR -> t;
				case OUT_QUAD -> 1f - (1f - t) * (1f - t);
				case OUT_CUBIC -> 1f - (1f - t) * (1f - t) * (1f - t);
				case OUT_EXPO -> t >= 1f ? 1f : 1f - (float) Math.pow(2, -10 * t);
				case IN_OUT -> t * t * (3f - 2f * t);
				case OUT_BACK -> {
					float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
					yield 1f + c3 * u * u * u + c1 * u * u;
				}
			};
		}
	}

	// ---- styles (4 bits, per kind)
	public static final int CONE_FLAME = 0;
	public static final int CONE_DEBRIS = 1;
	public static final int CONE_BEAM = 2;
	public static final int RING_SHOCK = 0;
	public static final int RING_STEAM = 1;
	public static final int RING_SOFT = 2;
	public static final int DECAL_SCORCH = 0;
	public static final int DECAL_CRACK = 1;
	public static final int DECAL_POOL = 2;
	public static final int DECAL_PRINT = 3;
	/** v0.17.1 (Hero Landing): a crack web with the Green Lantern emblem glowing in it (colour A = the glow). */
	public static final int DECAL_EMBLEM = 4;
	public static final int BOOK_FIREBALL = 0;
	public static final int BOOK_SMOKE = 1;
	public static final int BOOK_INK = 2;
	public static final int RIBBON_FLAME = 0;
	public static final int RIBBON_CLOTH = 1;
	public static final int RIBBON_SHADOW = 2;
	public static final int RIBBON_LIGHT = 3;

	// ---- flags (bit 16 up)
	/** Ribbon: follow {@code follow()} and draw the path it leaves (exhaust, a dash streak). */
	public static final int F_TRAIL = 1 << 16;
	/** Ring / decal / spike: snapped down onto the ground under the spawn point (client side). */
	public static final int F_GROUND = 1 << 17;
	/** Shards: after hanging they fly back to the centre and reassemble. */
	public static final int F_REASSEMBLE = 1 << 18;
	/** Blast: no smoke crown (a clean energy blast). */
	public static final int F_NO_SMOKE = 1 << 19;
	/** Ring: stood upright, facing {@code vec} (a sonic boom, a muzzle ring). */
	public static final int F_VERTICAL = 1 << 20;
	/** Blast: no ground shock ring (mid-air). */
	public static final int F_NO_RING = 1 << 21;

	public static final EntityType<QuirkFxEntity> TYPE = register("quirk_fx",
			EntityType.Builder.<QuirkFxEntity>of(QuirkFxEntity::new, MobCategory.MISC)
					.sized(0.3f, 0.3f)
					.clientTrackingRange(10)
					.updateInterval(40)
					.noSave()
					.noSummon()
					.fireImmune()
					.build("quirk_fx"));

	private QuirkFx() {
	}

	/** Class-load hook (from {@code QuirkFramework.initialize}). */
	public static void initialize() {
	}

	private static <T extends Entity> EntityType<T> register(String path, EntityType<T> type) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type);
	}

	// ---------------------------------------------------------------- builders

	public static Builder blast(ServerLevel level, Vec3 at) {
		return new Builder(level, Kind.BLAST, at).colours(0xFFFFFF, 0xFF7A1A).size(3f).life(16).ease(Ease.OUT_EXPO);
	}

	public static Builder ring(ServerLevel level, Vec3 at) {
		return new Builder(level, Kind.RING, at).colours(0xFFFFFF, 0xFFB060).size(5f).size2(0.5f).life(12).ease(Ease.OUT_CUBIC);
	}

	/** A ribbon from {@code from} to {@code to}. */
	public static Builder ribbon(ServerLevel level, Vec3 from, Vec3 to) {
		return new Builder(level, Kind.RIBBON, from).vec(to).colours(0xFFFFFF, 0xFF8030).size(0.3f).life(8).ease(Ease.OUT_QUAD);
	}

	/** A trail ribbon that follows {@code who} and draws the path it leaves for {@code life} ticks. */
	public static Builder trail(ServerLevel level, Entity who) {
		return new Builder(level, Kind.RIBBON, who.position()).follow(who).flags(F_TRAIL).colours(0xFFFFFF, 0x5AA9FF).size(0.3f).life(20)
				.size2(10f);
	}

	/** A cone (or beam) from {@code at} along {@code dir}. */
	public static Builder cone(ServerLevel level, Vec3 at, Vec3 dir) {
		return new Builder(level, Kind.CONE, at).vec(dir.normalize()).colours(0xFFF4C0, 0xFF4A10).size(2f).size2(6f).life(10)
				.ease(Ease.OUT_CUBIC);
	}

	public static Builder spike(ServerLevel level, Vec3 base, Vec3 dir) {
		return new Builder(level, Kind.SPIKE, base).vec(dir.normalize()).colours(0x9A9A9A, 0x5A5A5A).size(2.4f).size2(0.45f).life(40)
				.ease(Ease.OUT_BACK).flags(F_GROUND);
	}

	public static Builder shards(ServerLevel level, Vec3 at) {
		return new Builder(level, Kind.SHARDS, at).colours(0xB0B0B0, 0x707070).size(2f).count(20).life(30).ease(Ease.OUT_CUBIC);
	}

	public static Builder decal(ServerLevel level, Vec3 at) {
		return new Builder(level, Kind.DECAL, at).colours(0x201410, 0x000000).size(2.5f).life(200).flags(F_GROUND);
	}

	public static Builder flipbook(ServerLevel level, Vec3 at) {
		return new Builder(level, Kind.FLIPBOOK, at).colours(0xFFFFFF, 0xFF8030).size(1.2f).life(16);
	}

	/** Collects one effect's parameters; {@link #spawn} adds it to the level. */
	public static final class Builder {
		private final ServerLevel level;
		private final Kind kind;
		private final Vec3 at;
		private int life = 20;
		private int colourA = 0xFFFFFF;
		private int colourB = 0xFF8030;
		private float size = 1f;
		private float size2 = 1f;
		private Ease ease = Ease.OUT_CUBIC;
		private int style;
		private int count;
		private int flags;
		private Vec3 vec = new Vec3(0, 1, 0);
		private int follow;
		private int block;
		private float yaw;

		private Builder(ServerLevel level, Kind kind, Vec3 at) {
			this.level = level;
			this.kind = kind;
			this.at = at;
		}

		public Builder life(int ticks) {
			this.life = ticks;
			return this;
		}

		/** The colour ramp: {@code a} at the core / start, {@code b} at the edge / end (RGB). */
		public Builder colours(int a, int b) {
			this.colourA = a;
			this.colourB = b;
			return this;
		}

		public Builder colour(int rgb) {
			return colours(rgb, rgb);
		}

		public Builder size(float s) {
			this.size = s;
			return this;
		}

		public Builder size2(float s) {
			this.size2 = s;
			return this;
		}

		/** Cone / beam length (= size2). */
		public Builder length(float l) {
			return size2(l);
		}

		/** Ring thickness (= size2). */
		public Builder thickness(float t) {
			return size2(t);
		}

		public Builder ease(Ease e) {
			this.ease = e;
			return this;
		}

		public Builder style(int s) {
			this.style = s & 0xF;
			return this;
		}

		public Builder count(int n) {
			this.count = Math.max(0, Math.min(255, n));
			return this;
		}

		public Builder flags(int f) {
			this.flags |= f;
			return this;
		}

		public Builder ground() {
			return flags(F_GROUND);
		}

		public Builder reassemble() {
			return flags(F_REASSEMBLE);
		}

		public Builder vec(Vec3 v) {
			this.vec = v;
			return this;
		}

		public Builder follow(@Nullable Entity e) {
			this.follow = e == null ? 0 : e.getId();
			return this;
		}

		public Builder block(@Nullable BlockState state) {
			this.block = state == null ? 0 : Block.getId(state);
			return this;
		}

		/** The effect's yaw (degrees), for decals / prints that have a facing. */
		public Builder yaw(float degrees) {
			this.yaw = degrees;
			return this;
		}

		public QuirkFxEntity spawn() {
			QuirkFxEntity e = new QuirkFxEntity(TYPE, level);
			e.moveTo(at.x, at.y, at.z, yaw, 0f);
			int packed = (ease.id & 0xF) | ((style & 0xF) << 4) | ((count & 0xFF) << 8) | flags;
			e.setup(kind.id, life, colourA, colourB, size, size2, packed, vec, follow, block);
			level.addFreshEntity(e);
			return e;
		}
	}
}
