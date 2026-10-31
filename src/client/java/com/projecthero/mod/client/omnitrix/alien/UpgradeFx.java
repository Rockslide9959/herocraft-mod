package com.projecthero.mod.client.omnitrix.alien;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.2: Upgrade's world effects, drawn for every Upgrade in view.
 *
 * <p>User: "when upgrade takes over a block put the upgrade techno texture on it and make it look cool, also make an animation
 * for him taking over the block so it looks cool". While he is merged ({@code UpgradeKit} sends the target out in the synced
 * kit timers), the furnace / dispenser / dropper / piston is wrapped in his techno coat -- a black liquid-metal skin
 * ({@code upgrade_techno.png}, lit by the world, a glossy sheen sliding over it) with his green circuit lines glowing through
 * it ({@code upgrade_techno_glow.png}, full-bright, a pulse running along them). A golem, minecart or boat wears the coat over
 * its own model (the entity re-rendered through {@link CoatSource} with every texture swapped for his). The takeover (about a
 * second): a strand of liquid metal pours from where he stood onto the block, the coat flows over it from the side he touched
 * -- running down faster than across, its leading edge lit green, wobbling as it goes -- with sparks and drips, then the
 * circuits light up in a sweep from where he went in. Leaving, the coat peels back off the same way.
 *
 * <p>User: "please upgrade upgrades vfx aswell please". The Optic Beam and the Charged Beam are green energy tubes with a
 * white-hot core (the Charged Beam fat, with circuit rings racing down it and a double helix round it), Overload and the
 * Circuit Jolt a crackling arc, Tech Grab / Circuit Lash / Stretch Grapple real liquid-metal tendrils (a black strand with the
 * circuit lines lit) -- the grab's coils binding the target for the stun, the lash a whip that cracks out and snaps back, the
 * grapple anchored where it struck -- EMP Burst a shockwave wall racing out to its 8-block edge with circuit lines cracking
 * across the ground, the Charged Beam's charge a swelling orb at the eye, and the Liquid Form a puddle of liquid metal with
 * green circuit glints, his eye and drips.
 */
public final class UpgradeFx {
	public static final ResourceLocation TECHNO = ProjectHeroMod.id("textures/entity/omnitrix/upgrade_techno.png");
	public static final ResourceLocation TECHNO_GLOW = ProjectHeroMod.id("textures/entity/omnitrix/upgrade_techno_glow.png");
	/** Mechamorph green, its pale core, white-green. */
	public static final int GREEN = 0xFF39FF6A, PALE = 0xFFB8FFB0, CORE = 0xFFF0FFF0;
	static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.224f, 1.0f, 0.416f), 0.7f);
	static final DustParticleOptions METAL = new DustParticleOptions(new Vector3f(0.03f, 0.045f, 0.04f), 0.9f);

	/** The takeover: the strand pours for {@link #POUR} ticks, the coat covers the target by {@link #COVER}, the lines light by {@link #LIGHT}. */
	public static final float POUR = 8f, COVER = 20f, LIGHT = 32f, PEEL = 14f;

	private UpgradeFx() {
	}

	// ---------------------------------------------------------------- state

	/** A merge as the client sees it. */
	record MergeView(int kind, @Nullable BlockPos pos, int entityId, long start, Vec3 from) {
		boolean entity() {
			return pos == null;
		}

		boolean same(MergeView o) {
			return o != null && o.kind == kind && o.entityId == entityId && o.start == start && java.util.Objects.equals(o.pos, pos);
		}
	}

	/** One player's last seen pose / merge. */
	private static final class Track {
		int animId;
		long animStart;
		MergeView merge;
		Vec3 lastChest;
		boolean seen;
	}

	/** A move's effect in flight. */
	private static final class Fx {
		final int kind;
		final int owner;
		final long start;
		final int life;
		Vec3 end;
		int target;
		final Vec3 origin;
		final long seed;

		Fx(int kind, int owner, long start, int life, Vec3 end, int target, Vec3 origin, long seed) {
			this.kind = kind;
			this.owner = owner;
			this.start = start;
			this.life = life;
			this.end = end;
			this.target = target;
			this.origin = origin;
			this.seed = seed;
		}
	}

	private record Peel(MergeView merge, long start) {
	}

	private static final Map<Integer, Track> TRACKS = new HashMap<>();
	private static final List<Fx> FX = new ArrayList<>();
	private static final List<Peel> PEELS = new ArrayList<>();
	private static Level lastLevel;

	/** From {@code UpgradeClient}'s registration. */
	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(UpgradeFx::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(UpgradeFx::render);
		ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> clear());
	}

	private static void clear() {
		TRACKS.clear();
		FX.clear();
		PEELS.clear();
	}

	private static long timer(OmnitrixState st, String name) {
		return st.readyAt(UpgradeKit.ID + "." + name);
	}

	@Nullable
	private static MergeView mergeOf(Player pl, OmnitrixState st, @Nullable Track tr) {
		long kind = timer(st, UpgradeKit.T_MERGE_KIND);
		if (kind <= 0) {
			return null;
		}
		long ent = timer(st, UpgradeKit.T_MERGE_ENT);
		BlockPos pos = ent > 0 ? null
				: BlockPos.containing(UpgradeKit.decode(timer(st, UpgradeKit.T_MERGE_X)), UpgradeKit.decode(timer(st, UpgradeKit.T_MERGE_Y)),
						UpgradeKit.decode(timer(st, UpgradeKit.T_MERGE_Z)));
		Vec3 from = tr != null && tr.lastChest != null ? tr.lastChest : pl.position().add(0, pl.getBbHeight() * 0.55, 0);
		return new MergeView((int) kind - 1, pos, (int) ent - 1, timer(st, UpgradeKit.T_MERGE_AT), from);
	}

	/** The last move's end point from the synced timers, or null if none was sent. */
	@Nullable
	private static Vec3 fxEnd(OmnitrixState st) {
		long x = timer(st, UpgradeKit.T_FX_X);
		if (x <= 0) {
			return null;
		}
		return new Vec3(UpgradeKit.decode(x), UpgradeKit.decode(timer(st, UpgradeKit.T_FX_Y)), UpgradeKit.decode(timer(st, UpgradeKit.T_FX_Z)));
	}

	// ---------------------------------------------------------------- tick: spot new moves and merges, spray particles

	private static void tick(Minecraft mc) {
		Level level = mc.level;
		if (level != lastLevel) {
			clear();
			lastLevel = level;
		}
		if (level == null || mc.isPaused()) {
			return;
		}
		long now = level.getGameTime();
		RandomSource r = level.random;
		for (Track t : TRACKS.values()) {
			t.seen = false;
		}
		for (Player pl : level.players()) {
			OmnitrixState st = Omnitrix.state(pl);
			boolean fresh = !TRACKS.containsKey(pl.getId());
			Track tr = TRACKS.computeIfAbsent(pl.getId(), k -> new Track());
			tr.seen = true;
			boolean up = Omnitrix.hasPower(pl) && UpgradeKit.ID.equals(st.active);
			if (up && (st.animId != tr.animId || st.animStart != tr.animStart)) {
				tr.animId = st.animId;
				tr.animStart = st.animStart;
				if (!fresh && now - st.animStart <= 3) {
					spawn(pl, st, now);
				}
			}
			// the end point is saved just after the pose: catch it for a few ticks
			Vec3 end = fxEnd(st);
			if (end != null) {
				int target = (int) timer(st, UpgradeKit.T_FX_ENT) - 1;
				for (Fx f : FX) {
					if (f.owner == pl.getId() && now - f.start <= 3 && f.kind != UpgradeKit.ANIM_EMP) {
						f.end = end;
						f.target = target;
					}
				}
			}
			MergeView mv = up ? mergeOf(pl, st, tr) : null;
			if (tr.merge != null && (mv == null || !mv.same(tr.merge))) {
				PEELS.add(new Peel(tr.merge, now));
				tr.merge = null;
			}
			if (mv != null && tr.merge == null) {
				tr.merge = mv;
			}
			if (tr.merge != null) {
				mergeParticles(level, tr.merge, now, r);
			}
			if (up && UpgradeKit.sliding(pl)) {
				puddleParticles(pl, r);
			}
			if (!pl.isInvisible()) {
				tr.lastChest = pl.position().add(0, pl.getBbHeight() * 0.55, 0);
			}
		}
		for (Iterator<Map.Entry<Integer, Track>> it = TRACKS.entrySet().iterator(); it.hasNext();) {
			Track t = it.next().getValue();
			if (!t.seen) {
				if (t.merge != null) {
					PEELS.add(new Peel(t.merge, now));
				}
				it.remove();
			}
		}
		FX.removeIf(f -> now - f.start > f.life + 1);
		PEELS.removeIf(p -> now - p.start > PEEL + 1);
		for (Peel p : PEELS) {
			if (r.nextInt(2) == 0) {
				AABB box = box(level, p.merge, 1f);
				if (box != null) {
					level.addParticle(METAL, Mth.lerp(r.nextDouble(), box.minX, box.maxX), box.minY + r.nextDouble() * box.getYsize(), Mth.lerp(r.nextDouble(), box.minZ, box.maxZ),
							0, -0.05, 0);
				}
			}
		}
		for (Fx f : FX) {
			fxParticles(level, f, now, r);
		}
	}

	private static void spawn(Player pl, OmnitrixState st, long now) {
		int anim = st.animId;
		int life = switch (anim) {
			case UpgradeKit.ANIM_BEAM -> 9;
			case UpgradeKit.ANIM_CHARGED -> 16;
			case UpgradeKit.ANIM_GRAB -> UpgradeKit.STUN_TICKS;
			case UpgradeKit.ANIM_LASH -> 12;
			case UpgradeKit.ANIM_GRAPPLE -> 14;
			case UpgradeKit.ANIM_OVERLOAD -> 12;
			case UpgradeKit.ANIM_EMP -> 22;
			case UpgradeKit.ANIM_JOLT -> 6;
			case UpgradeKit.ANIM_REPAIR -> 18;
			case UpgradeKit.ANIM_SLIDE -> 8;
			default -> 0;
		};
		if (life <= 0) {
			return;
		}
		Vec3 end = fxEnd(st);
		if (end == null) {
			HitResult hit = pl.pick(24, 1f, false);
			end = hit.getLocation();
		}
		int target = (int) timer(st, UpgradeKit.T_FX_ENT) - 1;
		FX.add(new Fx(anim, pl.getId(), st.animStart, life, end, target, pl.position(), pl.getRandom().nextLong()));
		Level level = pl.level();
		RandomSource r = level.random;
		if (anim == UpgradeKit.ANIM_SLIDE) {
			// melting down: a splash of metal and green
			for (int k = 0; k < 16; k++) {
				double a = r.nextDouble() * Math.PI * 2;
				level.addParticle(k % 3 == 0 ? SPARK : METAL, pl.getX() + Math.cos(a) * 0.3, pl.getY() + 0.1 + r.nextDouble() * 0.6, pl.getZ() + Math.sin(a) * 0.3,
						Math.cos(a) * 0.08, 0.02, Math.sin(a) * 0.08);
			}
		} else if (anim == UpgradeKit.ANIM_EMP) {
			for (int k = 0; k < 40; k++) {
				double a = r.nextDouble() * Math.PI * 2;
				double s = 0.5 + r.nextDouble() * 0.6;
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, pl.getX(), pl.getY() + 0.2 + r.nextDouble(), pl.getZ(), Math.cos(a) * s, 0.02, Math.sin(a) * s);
			}
		}
	}

	private static void fxParticles(Level level, Fx f, long now, RandomSource r) {
		Entity owner = level.getEntity(f.owner);
		if (owner == null) {
			return;
		}
		long t = now - f.start;
		switch (f.kind) {
			case UpgradeKit.ANIM_BEAM, UpgradeKit.ANIM_CHARGED -> {
				if (t <= 3) {
					Vec3 a = eye(owner, 1f);
					for (int k = 0; k < (f.kind == UpgradeKit.ANIM_CHARGED ? 6 : 2); k++) {
						Vec3 p = a.lerp(f.end, r.nextDouble());
						level.addParticle(SPARK, p.x, p.y, p.z, (r.nextDouble() - 0.5) * 0.05, (r.nextDouble() - 0.5) * 0.05, (r.nextDouble() - 0.5) * 0.05);
					}
					level.addParticle(ParticleTypes.ELECTRIC_SPARK, f.end.x, f.end.y, f.end.z, (r.nextDouble() - 0.5) * 0.3, r.nextDouble() * 0.2,
							(r.nextDouble() - 0.5) * 0.3);
				}
			}
			case UpgradeKit.ANIM_GRAB -> {
				Entity target = level.getEntity(f.target);
				if (target != null && t > 3 && r.nextInt(3) == 0) {
					level.addParticle(r.nextBoolean() ? METAL : SPARK, target.getX() + (r.nextDouble() - 0.5) * target.getBbWidth(),
							target.getY() + r.nextDouble() * target.getBbHeight(), target.getZ() + (r.nextDouble() - 0.5) * target.getBbWidth(), 0, -0.03, 0);
				}
			}
			case UpgradeKit.ANIM_OVERLOAD -> {
				if (r.nextInt(2) == 0) {
					level.addParticle(ParticleTypes.ELECTRIC_SPARK, f.end.x + (r.nextDouble() - 0.5) * 0.8, f.end.y + (r.nextDouble() - 0.5) * 0.8,
							f.end.z + (r.nextDouble() - 0.5) * 0.8, 0, 0, 0);
				}
			}
			default -> {
			}
		}
	}

	private static void mergeParticles(Level level, MergeView m, long now, RandomSource r) {
		AABB box = box(level, m, 1f);
		if (box == null) {
			return;
		}
		float t = now - m.start;
		if (t < COVER + 2) {
			// along the advancing edge: sparks, and drips off the bottom
			Vec3 flow = flow(m, box);
			float front = front(t);
			for (int k = 0; k < 14; k++) {
				Vec3 p = surfacePoint(box, r);
				float d = coverD(p, box, flow, now);
				if (Math.abs(front - d) < 0.12) {
					level.addParticle(r.nextInt(3) == 0 ? ParticleTypes.ELECTRIC_SPARK : SPARK, p.x, p.y, p.z, (r.nextDouble() - 0.5) * 0.06, 0.02,
							(r.nextDouble() - 0.5) * 0.06);
					break;
				}
			}
			if (r.nextInt(2) == 0) {
				level.addParticle(METAL, Mth.lerp(r.nextDouble(), box.minX, box.maxX), box.minY + 0.05, Mth.lerp(r.nextDouble(), box.minZ, box.maxZ), 0, -0.06, 0);
			}
			if ((int) t == (int) COVER) {
				for (int k = 0; k < 18; k++) {
					Vec3 sp = surfacePoint(box, r);
					level.addParticle(ParticleTypes.ELECTRIC_SPARK, sp.x, sp.y, sp.z, (r.nextDouble() - 0.5) * 0.15, r.nextDouble() * 0.12, (r.nextDouble() - 0.5) * 0.15);
				}
			}
		} else if (r.nextInt(5) == 0) {
			Vec3 p = surfacePoint(box, r);
			level.addParticle(r.nextInt(4) == 0 ? ParticleTypes.ELECTRIC_SPARK : SPARK, p.x, p.y, p.z, 0, 0.01, 0);
		}
	}

	private static void puddleParticles(Player pl, RandomSource r) {
		Level level = pl.level();
		if (r.nextInt(3) == 0) {
			double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * 0.5;
			level.addParticle(SPARK, pl.getX() + Math.cos(a) * d, pl.getY() + 0.12, pl.getZ() + Math.sin(a) * d, 0, 0.01, 0);
		}
		if (pl.getDeltaMovement().horizontalDistanceSqr() > 0.003 && r.nextInt(2) == 0) {
			level.addParticle(METAL, pl.getX() + (r.nextDouble() - 0.5) * 0.7, pl.getY() + 0.06, pl.getZ() + (r.nextDouble() - 0.5) * 0.7, 0, 0.0, 0);
		}
	}

	// ---------------------------------------------------------------- shared geometry

	/** The merged block's (or entity's) box, inflated by {@code grow}; null if it is gone. */
	@Nullable
	private static AABB box(Level level, MergeView m, float partial) {
		if (m.entity()) {
			Entity e = level.getEntity(m.entityId);
			if (e == null) {
				return null;
			}
			Vec3 at = e.getPosition(partial);
			return e.getBoundingBox().move(at.subtract(e.position()));
		}
		VoxelShape s = level.getBlockState(m.pos).getShape(level, m.pos);
		AABB b = s.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : s.bounds();
		return b.move(m.pos);
	}

	/** The way the coat flows across the target: away from where he stood (entities: straight up). */
	private static Vec3 flow(MergeView m, AABB box) {
		if (m.entity()) {
			return new Vec3(0, 1, 0);
		}
		Vec3 d = box.getCenter().subtract(m.from);
		return d.lengthSqr() < 1.0e-4 ? new Vec3(0, 1, 0) : d.normalize();
	}

	/** The coat's leading edge at {@code t} ticks into the takeover (it starts behind the target, ends past it). */
	private static float front(float t) {
		float k = Mth.clamp((t - POUR * 0.4f) / (COVER - POUR * 0.4f), 0f, 1f);
		k = 1f - (1f - k) * (1f - k) * (1f - k); // out-cubic
		return -0.35f + 1.75f * k;
	}

	/** How far along the flow a point on the target is (0 where he went in, 1 the far side), bent so it runs down faster, wobbling. */
	private static float coverD(Vec3 p, AABB box, Vec3 flow, float time) {
		double d0 = Double.MAX_VALUE, d1 = -Double.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			double x = (i & 1) == 0 ? box.minX : box.maxX, y = (i & 2) == 0 ? box.minY : box.maxY, z = (i & 4) == 0 ? box.minZ : box.maxZ;
			double d = x * flow.x + y * flow.y + z * flow.z;
			d0 = Math.min(d0, d);
			d1 = Math.max(d1, d);
		}
		double d = (p.dot(flow) - d0) / Math.max(1.0e-3, d1 - d0);
		double yf = (p.y - box.minY) / Math.max(1.0e-3, box.getYsize());
		double sag = Math.abs(flow.y) > 0.9 ? 0 : 0.22 * (1 - yf) - 0.08; // liquid runs down the sides ahead of the front
		double wob = 0.06 * Math.sin(p.x * 9.1 + p.z * 7.3 + p.y * 5.7 + time * 0.25) + 0.04 * Math.sin(p.x * 17.0 - p.z * 13.0 + p.y * 3.0 - time * 0.4);
		return (float) (d - sag + wob);
	}

	private static Vec3 surfacePoint(AABB b, RandomSource r) {
		double x = Mth.lerp(r.nextDouble(), b.minX, b.maxX), y = Mth.lerp(r.nextDouble(), b.minY, b.maxY), z = Mth.lerp(r.nextDouble(), b.minZ, b.maxZ);
		switch (r.nextInt(6)) {
			case 0 -> x = b.minX;
			case 1 -> x = b.maxX;
			case 2 -> y = b.minY;
			case 3 -> y = b.maxY;
			case 4 -> z = b.minZ;
			default -> z = b.maxZ;
		}
		return new Vec3(x, y, z);
	}

	private static Vec3 eye(Entity owner, float partial) {
		Vec3 look = owner.getViewVector(partial);
		return owner.getEyePosition(partial).add(look.scale(0.4)).add(0, -0.06, 0);
	}

	/** His right hand (world): the right shoulder plus an arm's length down the look -- the tendril poses aim the arm along it. */
	private static Vec3 hand(Entity owner, float partial) {
		Vec3 look = owner.getViewVector(partial);
		float yaw = owner instanceof LivingEntity le ? Mth.lerp(partial, le.yBodyRotO, le.yBodyRot) : owner.getViewYRot(partial);
		float rad = yaw * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(rad), 0, -Mth.sin(rad));
		float s = owner.getBbHeight() / 1.8f;
		return owner.getPosition(partial).add(0, 1.375 * s, 0).add(right.scale(0.33 * s)).add(look.scale(0.68 * s));
	}

	private static void vert(VertexConsumer vc, PoseStack.Pose p, double x, double y, double z, int argb, float u, float v, int light, float nx, float ny, float nz) {
		vc.addVertex(p, (float) x, (float) y, (float) z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}

	private static int grey(float k, float alpha) {
		int g = (int) (Mth.clamp(k, 0f, 1f) * 255f);
		int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
		return a << 24 | g << 16 | g << 8 | g;
	}

	private static int add(int a, int b) {
		int r = Math.min(255, ((a >> 16) & 255) + ((b >> 16) & 255));
		int g = Math.min(255, ((a >> 8) & 255) + ((b >> 8) & 255));
		int bl = Math.min(255, (a & 255) + (b & 255));
		return 0xFF000000 | r << 16 | g << 8 | bl;
	}

	/** A consumer that fetches its buffer per vertex, so several render types can be drawn interleaved (see PyroDraw). */
	private static VertexConsumer lazy(MultiBufferSource buffers, RenderType type) {
		return new VertexConsumer() {
			private VertexConsumer cur;

			@Override
			public VertexConsumer addVertex(float x, float y, float z) {
				cur = buffers.getBuffer(type);
				cur.addVertex(x, y, z);
				return this;
			}

			@Override
			public VertexConsumer setColor(int r, int g, int b, int a) {
				cur.setColor(r, g, b, a);
				return this;
			}

			@Override
			public VertexConsumer setUv(float u, float v) {
				cur.setUv(u, v);
				return this;
			}

			@Override
			public VertexConsumer setUv1(int u, int v) {
				cur.setUv1(u, v);
				return this;
			}

			@Override
			public VertexConsumer setUv2(int u, int v) {
				cur.setUv2(u, v);
				return this;
			}

			@Override
			public VertexConsumer setNormal(float x, float y, float z) {
				cur.setNormal(x, y, z);
				return this;
			}
		};
	}

	// ---------------------------------------------------------------- render

	private static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		Level level = mc.level;
		if (level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		if (TRACKS.isEmpty() && PEELS.isEmpty() && FX.isEmpty()) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		float time = level.getGameTime() + partial;
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		MultiBufferSource buffers = context.consumers();
		Matrix4f inv = new Matrix4f(pose.last().pose()).invert();
		// the coats
		for (Track tr : TRACKS.values()) {
			if (tr.merge != null) {
				float t = time - tr.merge.start;
				coat(level, tr.merge, pose, buffers, inv, cam, partial, time, front(t), Mth.clamp((t - COVER + 2f) / (LIGHT - COVER), 0f, 1f), 1f);
				if (!tr.merge.entity() && t < POUR + 4f) {
					pour(level, tr.merge, pose, buffers, cam, time, t);
				}
			}
		}
		for (Peel p : PEELS) {
			float t = time - p.start;
			float k = Mth.clamp(t / PEEL, 0f, 1f);
			k = k * k * (3f - 2f * k);
			coat(level, p.merge, pose, buffers, inv, cam, partial, time, 1.4f - 1.75f * k, 1f - Mth.clamp(t / 5f, 0f, 1f), 1f - k * 0.3f);
		}
		// the moves
		for (Fx f : FX) {
			Entity owner = level.getEntity(f.owner);
			if (owner == null) {
				continue;
			}
			float t = time - f.start;
			if (t < 0 || t > f.life + partial) {
				continue;
			}
			switch (f.kind) {
				case UpgradeKit.ANIM_BEAM -> beam(pose, buffers, cam, eye(owner, partial), f.end, t, f.life, 0.075f, false, time);
				case UpgradeKit.ANIM_CHARGED -> beam(pose, buffers, cam, eye(owner, partial), f.end, t, f.life, 0.17f, true, time);
				case UpgradeKit.ANIM_GRAB -> grab(level, pose, buffers, cam, owner, f, t, partial, time);
				case UpgradeKit.ANIM_LASH -> lash(level, pose, buffers, cam, owner, f, t, partial, time);
				case UpgradeKit.ANIM_GRAPPLE -> grapple(level, pose, buffers, cam, owner, f, t, partial, time);
				case UpgradeKit.ANIM_OVERLOAD -> zap(level, pose, buffers, cam, hand(owner, partial), f, t, time, true);
				case UpgradeKit.ANIM_JOLT -> zap(level, pose, buffers, cam, hand(owner, partial), f, t, time, false);
				case UpgradeKit.ANIM_EMP -> emp(pose, buffers, cam, f, t, time);
				default -> {
				}
			}
		}
		// the Charged Beam's charge, the Liquid Form's puddle
		for (Player pl : level.players()) {
			OmnitrixState st = Omnitrix.state(pl);
			if (!Omnitrix.hasPower(pl) || !UpgradeKit.ID.equals(st.active)) {
				continue;
			}
			if ((st.kitFlags & UpgradeKit.F_CHARGING) != 0) {
				charging(pose, buffers, cam, pl, partial, time, time - st.animStart);
			}
			if ((st.kitFlags & UpgradeKit.F_SLIDE) != 0) {
				puddle(level, pose, buffers, cam, pl, partial, time);
			}
		}
	}

	// ---------------------------------------------------------------- the coat

	/**
	 * The techno coat over a merged target. {@code front}: how far the coat has flowed (past 1.2 = all of it); {@code lines}:
	 * how far the circuit lines have lit up (0..1, the sweep); {@code alpha}: the whole coat's opacity.
	 */
	private static void coat(Level level, MergeView m, PoseStack pose, MultiBufferSource buffers, Matrix4f inv, Vec3 cam, float partial, float time, float front,
			float lines, float alpha) {
		AABB box = box(level, m, partial);
		if (box == null || box.getCenter().distanceToSqr(cam) > 96 * 96) {
			return;
		}
		Vec3 flow = flow(m, box);
		if (m.entity()) {
			entityCoat(level, m, pose, buffers, inv, cam, partial, time, front, lines, alpha, box);
		} else {
			blockCoat(level, m.pos, box, flow, pose, buffers, cam, time, front, lines, alpha);
		}
	}

	private static final int G = 8;

	private static void blockCoat(Level level, BlockPos pos, AABB box, Vec3 flow, PoseStack pose, MultiBufferSource buffers, Vec3 cam, float time, float front,
			float lines, float alpha) {
		VertexConsumer base = lazy(buffers, RenderType.entityTranslucent(TECHNO));
		VertexConsumer glow = lazy(buffers, RenderType.eyes(TECHNO_GLOW));
		VertexConsumer addv = PyroDraw.additive(buffers);
		PoseStack.Pose last = pose.last();
		float scroll = time * 0.0025f;
		for (Direction dir : Direction.values()) {
			int light = LevelRenderer.getLightColor(level, pos.relative(dir));
			for (int pass = 0; pass < 3; pass++) {
				double grow = 0.022 + pass * 0.004;
				AABB b = box.inflate(grow);
				Vec3 n = Vec3.atLowerCornerOf(dir.getNormal());
				// the face's corner and its two edge vectors
				Vec3 o, ua, va;
				switch (dir) {
					case DOWN -> {
						o = new Vec3(b.minX, b.minY, b.minZ);
						ua = new Vec3(b.getXsize(), 0, 0);
						va = new Vec3(0, 0, b.getZsize());
					}
					case UP -> {
						o = new Vec3(b.minX, b.maxY, b.maxZ);
						ua = new Vec3(b.getXsize(), 0, 0);
						va = new Vec3(0, 0, -b.getZsize());
					}
					case NORTH -> {
						o = new Vec3(b.maxX, b.maxY, b.minZ);
						ua = new Vec3(-b.getXsize(), 0, 0);
						va = new Vec3(0, -b.getYsize(), 0);
					}
					case SOUTH -> {
						o = new Vec3(b.minX, b.maxY, b.maxZ);
						ua = new Vec3(b.getXsize(), 0, 0);
						va = new Vec3(0, -b.getYsize(), 0);
					}
					case WEST -> {
						o = new Vec3(b.minX, b.maxY, b.minZ);
						ua = new Vec3(0, 0, b.getZsize());
						va = new Vec3(0, -b.getYsize(), 0);
					}
					default -> {
						o = new Vec3(b.maxX, b.maxY, b.maxZ);
						ua = new Vec3(0, 0, -b.getZsize());
						va = new Vec3(0, -b.getYsize(), 0);
					}
				}
				for (int i = 0; i < G; i++) {
					for (int j = 0; j < G; j++) {
						Vec3[] c = { o.add(ua.scale(i / (double) G)).add(va.scale(j / (double) G)), o.add(ua.scale((i + 1) / (double) G)).add(va.scale(j / (double) G)),
								o.add(ua.scale((i + 1) / (double) G)).add(va.scale((j + 1) / (double) G)), o.add(ua.scale(i / (double) G)).add(va.scale((j + 1) / (double) G)) };
						int[] col = new int[4];
						float[] us = new float[4], vs = new float[4];
						boolean any = false;
						for (int k = 0; k < 4; k++) {
							Vec3 p = c[k];
							float d = coverD(p, box, flow, time);
							float a = Mth.clamp((front - d) / 0.1f, 0f, 1f);
							// texture coords from the world (32 texels a block, the tile spans two), the same on every pass
							double lx = p.x - pos.getX() + (pos.getX() & 1), ly = p.y - pos.getY() + (pos.getY() & 1), lz = p.z - pos.getZ() + (pos.getZ() & 1);
							float u, v;
							if (dir.getAxis() == Direction.Axis.X) {
								u = (float) (lz * 0.5);
								v = (float) (-ly * 0.5);
							} else if (dir.getAxis() == Direction.Axis.Y) {
								u = (float) (lx * 0.5);
								v = (float) (lz * 0.5);
							} else {
								u = (float) (lx * 0.5);
								v = (float) (-ly * 0.5);
							}
							us[k] = u;
							vs[k] = v + scroll;
							if (pass == 0) {
								col[k] = grey(1f, a * 0.97f * alpha);
								any |= a > 0.01f;
							} else if (pass == 1) {
								// the circuits: lit in a sweep from where he went in, then a pulse running along them
								float dd = coverD(p, box, flow, 0f);
								float sweep = Mth.clamp((lines * 1.5f - dd) / 0.18f, 0f, 1f);
								float flash = Mth.clamp(1f - Math.abs(lines * 1.5f - dd - 0.08f) / 0.12f, 0f, 1f) * (lines < 1f ? 1f : 0f);
								float pulse = 0.62f + 0.38f * Mth.sin(time * 0.22f - dd * 7f);
								float k2 = a * alpha * (sweep * pulse + flash * 0.9f + 0.12f * (1f - sweep));
								col[k] = grey(k2, 1f);
								any |= k2 > 0.01f;
							} else {
								// the wet leading edge, lit green, and a glossy sheen sliding over the metal
								float edge = (float) Math.exp(-Math.pow((front - d - 0.05f) / 0.07f, 2)) * (front < 1.3f ? 1f : 0f);
								float sheen = (float) Math.pow(Math.max(0, Math.sin((p.x * 0.6 + p.y * 0.9 + p.z * 0.35) * 2.6 - time * 0.07)), 18) * 0.16f * a;
								col[k] = add(PyroDraw.dim(GREEN, edge * 0.85f * alpha), PyroDraw.dim(0xFFD8FFE0, sheen * alpha));
								any |= edge > 0.02f || sheen > 0.01f;
							}
						}
						if (!any) {
							continue;
						}
						VertexConsumer vc = pass == 0 ? base : pass == 1 ? glow : addv;
						for (int k = 0; k < 4; k++) {
							vert(vc, last, c[k].x - cam.x, c[k].y - cam.y, c[k].z - cam.z, col[k], us[k], vs[k], light, (float) n.x, (float) n.y, (float) n.z);
						}
						if (pass > 0) {
							// the glow types cull: the back face too
							for (int k = 3; k >= 0; k--) {
								vert(vc, last, c[k].x - cam.x, c[k].y - cam.y, c[k].z - cam.z, col[k], us[k], vs[k], light, (float) -n.x, (float) -n.y, (float) -n.z);
							}
						}
					}
				}
			}
		}
	}

	/** The strand of liquid metal he pours himself into the block along, thinning as the last of him goes in. */
	private static void pour(Level level, MergeView m, PoseStack pose, MultiBufferSource buffers, Vec3 cam, float time, float t) {
		AABB box = box(level, m, 1f);
		if (box == null) {
			return;
		}
		Vec3 to = new Vec3(Mth.clamp(m.from.x, box.minX, box.maxX), Mth.clamp(m.from.y, box.minY, box.maxY), Mth.clamp(m.from.z, box.minZ, box.maxZ));
		float k = t < 2f ? t / 2f : 1f - Mth.clamp((t - 2f) / (POUR + 2f), 0f, 1f);
		if (k <= 0.02f) {
			return;
		}
		// the tail end follows him in
		float tail = Mth.clamp((t - 2f) / (POUR + 2f), 0f, 1f);
		Vec3 from = m.from.lerp(to, tail * tail);
		tendril(level, pose, buffers, cam, from, to, 0.42f * k + 0.04f, 0.18f * k + 0.04f, -0.25 * (1 - tail), time, 1f, 1.2f);
	}

	/** A golem, minecart or boat: the entity drawn again just over itself, every texture swapped for his techno coat. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static void entityCoat(Level level, MergeView m, PoseStack pose, MultiBufferSource buffers, Matrix4f inv, Vec3 cam, float partial, float time, float front,
			float lines, float alpha, AABB box) {
		Entity e = level.getEntity(m.entityId);
		if (e == null || (e instanceof Mob mob && mob.isLeashed())) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(e);
		float yaw = Mth.lerp(partial, e.yRotO, e.getYRot());
		int light = mc.getEntityRenderDispatcher().getPackedLightCoords(e, partial);
		Vec3 at = e.getPosition(partial).subtract(cam);
		float h = e.getBbHeight();
		float uvScale = e instanceof net.minecraft.world.entity.animal.IronGolem ? 4f : e instanceof net.minecraft.world.entity.vehicle.Boat ? 4f : 2f;
		for (int pass = 0; pass < 2; pass++) {
			pose.pushPose();
			pose.translate(at.x, at.y + h * 0.5, at.z);
			float s = pass == 0 ? 1.045f : 1.05f;
			pose.scale(s, s * 0.985f + 0.015f, s);
			pose.translate(0, -h * 0.5, 0);
			CoatSource src = new CoatSource(buffers, pass == 0 ? RenderType.entityTranslucent(TECHNO) : RenderType.eyes(TECHNO_GLOW), pass == 1, inv, cam, box, front,
					lines, alpha, time, uvScale);
			try {
				renderer.render(e, yaw, partial, pose, src, light);
			} catch (RuntimeException ex) {
				// a renderer that draws something the coat can't take: skip it this frame
			}
			src.flush();
			pose.popPose();
		}
	}

	/** Every buffer the entity asks for is his coat: per vertex the coat's cover (bottom up) and the circuits' sweep. */
	private static final class CoatSource implements MultiBufferSource {
		private final CoatConsumer consumer;

		CoatSource(MultiBufferSource buffers, RenderType type, boolean glow, Matrix4f inv, Vec3 cam, AABB box, float front, float lines, float alpha, float time,
				float uvScale) {
			consumer = new CoatConsumer(buffers, type, glow, inv, cam, box, front, lines, alpha, time, uvScale);
		}

		@Override
		public VertexConsumer getBuffer(RenderType type) {
			return consumer;
		}

		void flush() {
			consumer.flush();
		}
	}

	/** Collects each vertex whole (whatever format the caller writes), then emits it as a coat vertex. */
	private static final class CoatConsumer implements VertexConsumer {
		private final MultiBufferSource buffers;
		private final RenderType type;
		private final boolean glow;
		private final Matrix4f inv;
		private final Vec3 cam;
		private final AABB box;
		private final float front, lines, alpha, time, uvScale;
		private boolean has;
		private float x, y, z, u, v, nx, ny, nz;
		private int overlay, light;

		CoatConsumer(MultiBufferSource buffers, RenderType type, boolean glow, Matrix4f inv, Vec3 cam, AABB box, float front, float lines, float alpha, float time,
				float uvScale) {
			this.buffers = buffers;
			this.type = type;
			this.glow = glow;
			this.inv = inv;
			this.cam = cam;
			this.box = box;
			this.front = front;
			this.lines = lines;
			this.alpha = alpha;
			this.time = time;
			this.uvScale = uvScale;
		}

		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			flush();
			this.x = x;
			this.y = y;
			this.z = z;
			u = 0f;
			v = 0f;
			overlay = OverlayTexture.NO_OVERLAY;
			light = 0xF000F0;
			nx = 0f;
			ny = 1f;
			nz = 0f;
			has = true;
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			this.u = u;
			this.v = v;
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			overlay = (u & 0xFFFF) | v << 16;
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			light = (u & 0xFFFF) | v << 16;
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			nx = x;
			ny = y;
			nz = z;
			return this;
		}

		void flush() {
			if (!has) {
				return;
			}
			has = false;
			Vector4f w = inv.transform(new Vector4f(x, y, z, 1f));
			Vec3 p = new Vec3(w.x + cam.x, w.y + cam.y, w.z + cam.z);
			float yf = (float) ((p.y - box.minY) / Math.max(1.0e-3, box.getYsize()));
			float wob = 0.05f * Mth.sin((float) (p.x * 7.0 + p.z * 5.0) + time * 0.3f);
			float d = yf + wob;
			float a = Mth.clamp((front - d) / 0.12f, 0f, 1f) * alpha;
			int argb;
			if (glow) {
				float sweep = Mth.clamp((lines * 1.4f - yf) / 0.2f, 0f, 1f);
				float pulse = 0.62f + 0.38f * Mth.sin(time * 0.22f - yf * 6f);
				float edge = (float) Math.exp(-Math.pow((front - d - 0.05f) / 0.08f, 2)) * (front < 1.3f ? 1f : 0f);
				argb = grey(a * (sweep * pulse + 0.1f) + edge * 0.8f * alpha, 1f);
			} else {
				argb = grey(1f, a * 0.96f);
			}
			int ov = overlay;
			VertexConsumer out = buffers.getBuffer(type);
			out.addVertex(x, y, z).setColor(argb).setUv(u * uvScale, v * uvScale + time * 0.0025f).setUv1(ov & 0xFFFF, ov >>> 16).setUv2(light & 0xFFFF, light >>> 16)
					.setNormal(nx, ny, nz);
		}
	}

	// ---------------------------------------------------------------- tendrils

	/**
	 * A strand of his liquid metal from {@code a} to {@code b} (world): black metal with his circuit lines lit, {@code w0} thick at
	 * {@code a} tapering to {@code w1}, sagging by {@code sag} and rippling. {@code glow} scales the lines.
	 */
	private static void tendril(Level level, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 a, Vec3 b, float w0, float w1, double sag, float time,
			float glow, float ripple) {
		tendril(level, pose, buffers, cam, a, b, w0, w1, s -> new Vec3(0, -sag * 4 * s * (1 - s), 0), time, glow, ripple);
	}

	private static void tendril(Level level, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 a, Vec3 b, float w0, float w1,
			java.util.function.DoubleFunction<Vec3> bend, float time, float glow, float ripple) {
		int n = 18;
		List<Vec3> pts = new ArrayList<>(n + 1);
		Vec3 d = b.subtract(a);
		double len = d.length();
		if (len < 1.0e-3) {
			return;
		}
		Vec3 side = d.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		for (int i = 0; i <= n; i++) {
			double s = i / (double) n;
			double wave = Math.sin(s * 9.0 - time * 0.9) * 0.05 * ripple * Math.sin(s * Math.PI);
			pts.add(a.add(d.scale(s)).add(bend.apply(s)).add(side.scale(wave)).subtract(cam));
		}
		int light = LevelRenderer.getLightColor(level, BlockPos.containing(a.lerp(b, 0.5)));
		strip(pose, lazy(buffers, RenderType.entityTranslucent(TECHNO)), pts, w0, w1, light, 0xF5FFFFFF, (float) len, time, false);
		strip(pose, lazy(buffers, RenderType.eyes(TECHNO_GLOW)), pts, w0 * 0.92f, w1 * 0.92f, light, grey(0.75f * glow + 0.25f * glow * Mth.sin(time * 0.5f), 1f),
				(float) len, time, true);
		// a thin green energy thread down the middle
		PyroDraw.ribbon(pose, PyroDraw.additive(buffers), pts, s -> 0.035, s -> PyroDraw.dim(GREEN, 0.45f * glow));
	}

	/** A camera-facing strip along {@code pts} (camera-relative), textured along its length. */
	private static void strip(PoseStack pose, VertexConsumer vc, List<Vec3> pts, float w0, float w1, int light, int argb, float len, float time, boolean both) {
		PoseStack.Pose last = pose.last();
		Vec3 pl = null, pr = null;
		float pv = 0f;
		for (int i = 0; i < pts.size(); i++) {
			float s = i / (float) (pts.size() - 1);
			Vec3 p = pts.get(i);
			Vec3 tan = (i < pts.size() - 1 ? pts.get(i + 1) : p).subtract(i > 0 ? pts.get(i - 1) : p);
			Vec3 sd = tan.cross(p.scale(-1));
			sd = sd.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : sd.normalize();
			double w = Mth.lerp(s, w0, w1) * 0.5;
			Vec3 l = p.add(sd.scale(w)), r = p.subtract(sd.scale(w));
			float v = s * len * 0.5f + time * 0.01f;
			if (pl != null) {
				vert(vc, last, pl.x, pl.y, pl.z, argb, 0f, pv, light, 0, 1, 0);
				vert(vc, last, pr.x, pr.y, pr.z, argb, 0.25f, pv, light, 0, 1, 0);
				vert(vc, last, r.x, r.y, r.z, argb, 0.25f, v, light, 0, 1, 0);
				vert(vc, last, l.x, l.y, l.z, argb, 0f, v, light, 0, 1, 0);
				if (both) {
					vert(vc, last, l.x, l.y, l.z, argb, 0f, v, light, 0, -1, 0);
					vert(vc, last, r.x, r.y, r.z, argb, 0.25f, v, light, 0, -1, 0);
					vert(vc, last, pr.x, pr.y, pr.z, argb, 0.25f, pv, light, 0, -1, 0);
					vert(vc, last, pl.x, pl.y, pl.z, argb, 0f, pv, light, 0, -1, 0);
				}
			}
			pl = l;
			pr = r;
			pv = v;
		}
	}

	/** A band of liquid metal round a vertical axis at {@code at} (camera-relative): radius {@code r}, {@code half} tall. */
	private static void coil(PoseStack pose, MultiBufferSource buffers, Vec3 at, float r, float half, int light, float glow, float time) {
		PoseStack.Pose last = pose.last();
		int segs = 20;
		VertexConsumer base = lazy(buffers, RenderType.entityTranslucent(TECHNO));
		VertexConsumer lit = lazy(buffers, RenderType.eyes(TECHNO_GLOW));
		for (int pass = 0; pass < 2; pass++) {
			float rr = r + pass * 0.004f;
			VertexConsumer vc = pass == 0 ? base : lit;
			int c = pass == 0 ? 0xF5FFFFFF : grey(glow, 1f);
			for (int j = 0; j < segs; j++) {
				float a0 = Mth.TWO_PI * j / segs + time * 0.05f, a1 = Mth.TWO_PI * (j + 1) / segs + time * 0.05f;
				float x0 = Mth.cos(a0) * rr, z0 = Mth.sin(a0) * rr, x1 = Mth.cos(a1) * rr, z1 = Mth.sin(a1) * rr;
				float u0 = j / (float) segs, u1 = (j + 1) / (float) segs;
				vert(vc, last, at.x + x0, at.y - half, at.z + z0, c, u0, 0.25f, light, x0, 0, z0);
				vert(vc, last, at.x + x0, at.y + half, at.z + z0, c, u0, 0f, light, x0, 0, z0);
				vert(vc, last, at.x + x1, at.y + half, at.z + z1, c, u1, 0f, light, x1, 0, z1);
				vert(vc, last, at.x + x1, at.y - half, at.z + z1, c, u1, 0.25f, light, x1, 0, z1);
				vert(vc, last, at.x + x1, at.y - half, at.z + z1, c, u1, 0.25f, light, -x1, 0, -z1);
				vert(vc, last, at.x + x1, at.y + half, at.z + z1, c, u1, 0f, light, -x1, 0, -z1);
				vert(vc, last, at.x + x0, at.y + half, at.z + z0, c, u0, 0f, light, -x0, 0, -z0);
				vert(vc, last, at.x + x0, at.y - half, at.z + z0, c, u0, 0.25f, light, -x0, 0, -z0);
			}
		}
	}

	@Nullable
	private static Vec3 targetPoint(Level level, Fx f, float partial) {
		Entity e = f.target >= 0 ? level.getEntity(f.target) : null;
		return e == null ? null : e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0);
	}

	/** Tech Grab: the arm shoots out, wraps the target in coils that tighten and hold for the stun, then the arm draws back. */
	private static void grab(Level level, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Entity owner, Fx f, float t, float partial, float time) {
		Vec3 a = hand(owner, partial);
		Vec3 tp = targetPoint(level, f, partial);
		Vec3 b = tp != null ? tp : f.end;
		if (t < 14f) {
			float reach = t < 3f ? t / 3f : t < 10f ? 1f : 1f - (t - 10f) / 4f;
			tendril(level, pose, buffers, cam, a, a.lerp(b, reach), 0.2f, 0.14f, 0.15, time, 1f, 1f);
		}
		Entity e = f.target >= 0 ? level.getEntity(f.target) : null;
		if (e == null || !e.isAlive() || t < 2.5f) {
			return;
		}
		float hold = Mth.clamp((f.life - t) / 10f, 0f, 1f);
		float tighten = Mth.clamp((t - 2.5f) / 5f, 0f, 1f);
		float r = (e.getBbWidth() * 0.5f + 0.08f) * (1f + 0.7f * (1f - tighten));
		Vec3 base = e.getPosition(partial).subtract(cam);
		int light = LevelRenderer.getLightColor(level, e.blockPosition());
		for (int k = 0; k < 3; k++) {
			float y = e.getBbHeight() * (0.25f + 0.25f * k) + Mth.sin(time * 0.3f + k) * 0.03f;
			coil(pose, buffers, base.add(0, y, 0), r * (k == 1 ? 1.06f : 1f), 0.12f * hold, light, 1f, time * (k % 2 == 0 ? 1 : -1));
			// a lit circuit line round each coil's rim
			pose.pushPose();
			pose.translate(base.x, base.y + y, base.z);
			PyroDraw.band(pose, PyroDraw.additive(buffers), r * (k == 1 ? 1.06f : 1f) + 0.01f, 0.018f * hold, PyroDraw.dim(GREEN, 0.9f * hold), 0xFF000000);
			pose.popPose();
		}
	}

	/** Circuit Lash: a whip that cracks out in an S, snaps straight on the hit, and recoils. */
	private static void lash(Level level, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Entity owner, Fx f, float t, float partial, float time) {
		Vec3 a = hand(owner, partial);
		Vec3 tp = targetPoint(level, f, partial);
		Vec3 b = tp != null && t < 6f ? tp : f.end;
		float reach = t < 3f ? t / 3f : t < 6f ? 1f : 1f - (t - 6f) / 6f;
		if (reach <= 0.02f) {
			return;
		}
		Vec3 d = b.subtract(a);
		Vec3 side = d.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
		final Vec3 sd = side;
		float amp = (float) Math.min(1.2, d.length() * 0.12) * (t < 3f ? 1f - t / 3f : t < 6f ? 0.1f : (t - 6f) / 6f * 0.8f);
		Vec3 end = a.lerp(b, reach);
		tendril(level, pose, buffers, cam, a, end, 0.16f, 0.07f,
				s -> sd.scale(Math.sin(s * Math.PI * 2.0 + time * 0.6) * amp * Math.sin(s * Math.PI)).add(0, Math.sin(s * Math.PI) * amp * 0.4, 0), time, 1.2f, 0.6f);
		if (t >= 2.5f && t < 5f) {
			// the crack where it lands
			PyroDraw.glow(pose, PyroDraw.additive(buffers), end.subtract(cam), 0.35f * (1f - (t - 2.5f) / 2.5f), PyroDraw.dim(GREEN, 0.9f));
		}
	}

	/** Stretch Grapple: his arm stretched out to where it struck, anchored there in a splash of metal, shortening as he flies in. */
	private static void grapple(Level level, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Entity owner, Fx f, float t, float partial, float time) {
		Vec3 a = hand(owner, partial);
		float reach = t < 2.5f ? t / 2.5f : 1f;
		float fade = t > f.life - 4f ? (f.life - t) / 4f : 1f;
		if (fade <= 0.02f) {
			return;
		}
		Vec3 end = a.lerp(f.end, reach);
		tendril(level, pose, buffers, cam, a, end, 0.16f * fade + 0.02f, 0.12f * fade + 0.02f, 0.05 * fade, time, 1f, 0.4f);
		if (reach >= 1f) {
			PyroDraw.glow(pose, PyroDraw.additive(buffers), f.end.subtract(cam), 0.25f * fade, PyroDraw.dim(GREEN, 0.6f));
		}
	}

	// ---------------------------------------------------------------- beams, arcs, the EMP

	/**
	 * A beam from {@code a} to {@code b} (world): a soft green outer tube, a bright green tube and a white-hot core, a flare at
	 * each end; {@code big} (the Charged Beam) adds circuit rings racing down it and a double helix wound round it.
	 */
	private static void beam(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 a, Vec3 b, float t, int life, float r, boolean big, float time) {
		float k = t < 1.5f ? 0.6f + 0.4f * t / 1.5f : 1f - Mth.clamp((t - 1.5f) / (life - 1.5f), 0f, 1f);
		if (k <= 0.01f) {
			return;
		}
		Vec3 d = b.subtract(a);
		float len = (float) d.length();
		if (len < 0.05f) {
			return;
		}
		VertexConsumer add = PyroDraw.additive(buffers);
		float flick = 0.9f + 0.1f * Mth.sin(time * 3.1f);
		float w = r * (0.55f + 0.45f * k) * flick;
		pose.pushPose();
		Vec3 ra = a.subtract(cam);
		pose.translate(ra.x, ra.y, ra.z);
		PyroDraw.alignY(pose, d);
		tube(pose, add, w * 2.6f, len, PyroDraw.dim(GREEN, (big ? 0.14f : 0.22f) * k));
		tube(pose, add, w * 1.3f, len, PyroDraw.dim(GREEN, (big ? 0.5f : 0.7f) * k));
		tube(pose, add, w * 0.42f, len, PyroDraw.dim(CORE, (big ? 0.8f : 0.95f) * k));
		if (big) {
			// circuit rings racing out along it
			for (float y = (time * 0.9f) % 1.4f; y < len; y += 1.4f) {
				pose.pushPose();
				pose.translate(0, y, 0);
				PyroDraw.band(pose, add, w * 1.6f, 0.04f, PyroDraw.dim(PALE, 0.7f * k), 0xFF000000);
				pose.popPose();
			}
		}
		pose.popPose();
		if (big) {
			Vec3 side = d.cross(new Vec3(0, 1, 0));
			side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
			Vec3 up = side.cross(d).normalize();
			for (int h = 0; h < 2; h++) {
				List<Vec3> pts = new ArrayList<>();
				int n = Math.max(8, (int) (len * 4));
				for (int i = 0; i <= n; i++) {
					double s = i / (double) n;
					double ang = s * len * 3.2 - time * 0.8 + h * Math.PI;
					double rad = w * 1.7;
					pts.add(ra.add(d.scale(s)).add(side.scale(Math.cos(ang) * rad)).add(up.scale(Math.sin(ang) * rad)));
				}
				PyroDraw.ribbon(pose, add, pts, s -> 0.05, s -> PyroDraw.dim(GREEN, 0.75f * k));
			}
		}
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), ra, w * 4.5f, PyroDraw.dim(GREEN, 0.8f * k));
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), b.subtract(cam), w * (big ? 9f : 6f), PyroDraw.dim(GREEN, 0.9f * k));
		PyroDraw.glow(pose, add, b.subtract(cam), w * (big ? 3.5f : 2.5f), PyroDraw.dim(CORE, 0.8f * k));
	}

	/** An open cylinder up the pose's y axis, radius {@code r}, {@code len} long; both faces. Additive. */
	private static void tube(PoseStack pose, VertexConsumer vc, float r, float len, int argb) {
		PoseStack.Pose last = pose.last();
		int segs = 12;
		for (int j = 0; j < segs; j++) {
			float a0 = Mth.TWO_PI * j / segs, a1 = Mth.TWO_PI * (j + 1) / segs;
			float x0 = Mth.cos(a0) * r, z0 = Mth.sin(a0) * r, x1 = Mth.cos(a1) * r, z1 = Mth.sin(a1) * r;
			vert(vc, last, x0, 0, z0, argb, 0, 0, PyroDraw.FULL, x0, 0, z0);
			vert(vc, last, x0, len, z0, argb, 0, 1, PyroDraw.FULL, x0, 0, z0);
			vert(vc, last, x1, len, z1, argb, 1, 1, PyroDraw.FULL, x1, 0, z1);
			vert(vc, last, x1, 0, z1, argb, 1, 0, PyroDraw.FULL, x1, 0, z1);
			vert(vc, last, x1, 0, z1, argb, 1, 0, PyroDraw.FULL, -x1, 0, -z1);
			vert(vc, last, x1, len, z1, argb, 1, 1, PyroDraw.FULL, -x1, 0, -z1);
			vert(vc, last, x0, len, z0, argb, 0, 1, PyroDraw.FULL, -x0, 0, -z0);
			vert(vc, last, x0, 0, z0, argb, 0, 0, PyroDraw.FULL, -x0, 0, -z0);
		}
	}

	/** A jagged electric arc from {@code a} to {@code b} (camera-relative), re-jittered every couple of ticks. */
	private static List<Vec3> arc(Vec3 a, Vec3 b, long seed, double amp) {
		RandomSource r = RandomSource.create(seed);
		Vec3 d = b.subtract(a);
		Vec3 s1 = d.cross(new Vec3(0, 1, 0));
		s1 = s1.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : s1.normalize();
		Vec3 s2 = s1.cross(d).normalize();
		int n = Math.max(6, (int) (d.length() * 3));
		List<Vec3> pts = new ArrayList<>(n + 1);
		for (int i = 0; i <= n; i++) {
			double s = i / (double) n;
			double m = Math.sin(s * Math.PI) * amp;
			pts.add(a.add(d.scale(s)).add(s1.scale((r.nextDouble() - 0.5) * 2 * m)).add(s2.scale((r.nextDouble() - 0.5) * 2 * m)));
		}
		return pts;
	}

	/** Overload / the Circuit Jolt: a crackling arc into the target ({@code cage}: and a short-circuit cage of arcs round it). */
	private static void zap(Level level, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Vec3 from, Fx f, float t, float time, boolean cage) {
		float k = 1f - Mth.clamp(t / f.life, 0f, 1f);
		if (k <= 0.02f) {
			return;
		}
		Vec3 tp = targetPoint(level, f, 1f);
		Vec3 b = (tp != null ? tp : f.end).subtract(cam);
		Vec3 a = from.subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		long frame = (long) (time / 1.5f);
		if (t < (cage ? 6f : 4f)) {
			List<Vec3> pts = arc(a, b, f.seed + frame, Math.min(0.5, a.distanceTo(b) * 0.08));
			PyroDraw.ribbon(pose, add, pts, s -> 0.22 * k, s -> PyroDraw.dim(GREEN, 0.5f * k));
			PyroDraw.ribbon(pose, add, pts, s -> 0.06 * k, s -> PyroDraw.dim(CORE, k));
			// a fork off the main bolt
			List<Vec3> fork = arc(pts.get(pts.size() / 2), b.add(new Vec3(Math.sin(frame) * 0.6, 0.4, Math.cos(frame) * 0.6)), f.seed * 31 + frame, 0.15);
			PyroDraw.ribbon(pose, add, fork, s -> 0.035 * k, s -> PyroDraw.dim(PALE, 0.8f * k));
		}
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), b, (cage ? 0.9f : 0.45f) * k, PyroDraw.dim(GREEN, 0.9f * k));
		if (cage) {
			Entity e = f.target >= 0 ? level.getEntity(f.target) : null;
			double rad = e != null ? e.getBbWidth() * 0.6 + 0.2 : 0.6;
			double hh = e != null ? e.getBbHeight() * 0.55 : 0.6;
			RandomSource r = RandomSource.create(f.seed ^ frame);
			for (int i = 0; i < 4; i++) {
				double a0 = r.nextDouble() * Math.PI * 2, a1 = a0 + 1.0 + r.nextDouble() * 1.5;
				Vec3 p0 = b.add(Math.cos(a0) * rad, (r.nextDouble() - 0.5) * 2 * hh, Math.sin(a0) * rad);
				Vec3 p1 = b.add(Math.cos(a1) * rad, (r.nextDouble() - 0.5) * 2 * hh, Math.sin(a1) * rad);
				List<Vec3> pts = arc(p0, p1, f.seed + i * 977 + frame, 0.18);
				int col = i % 2 == 0 ? GREEN : CORE;
				PyroDraw.ribbon(pose, add, pts, s -> 0.05 * k, s -> PyroDraw.dim(col, 0.9f * k));
			}
		}
	}

	/** EMP Burst: a flash, a wall of green racing out to the 8-block edge, a glowing ground ring and circuit lines cracking outward. */
	private static void emp(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Fx f, float t, float time) {
		float grow = Mth.clamp(t / 12f, 0f, 1f);
		grow = 1f - (1f - grow) * (1f - grow);
		float rad = (float) UpgradeKit.EMP_RADIUS * grow + 0.3f;
		float fade = 1f - Mth.clamp((t - 8f) / (f.life - 8f), 0f, 1f);
		if (fade <= 0.01f) {
			return;
		}
		Vec3 c = f.origin.subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		if (t < 6f) {
			PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), c.add(0, 1, 0), 2.8f * (1f - t / 6f) + 0.5f, PyroDraw.dim(GREEN, 1f - t / 6f));
			PyroDraw.glow(pose, add, c.add(0, 1, 0), 1.1f * (1f - t / 6f), PyroDraw.dim(CORE, 1f - t / 6f));
		}
		pose.pushPose();
		pose.translate(c.x, c.y + 0.05, c.z);
		float wall = 1.5f * (1f - grow * 0.6f) * fade;
		pose.pushPose();
		pose.translate(0, wall, 0);
		PyroDraw.band(pose, add, rad, wall, PyroDraw.dim(GREEN, 0.75f * fade), 0xFF000000);
		PyroDraw.band(pose, add, rad * 0.985f, wall * 0.35f, PyroDraw.dim(CORE, 0.6f * fade), 0xFF000000);
		pose.popPose();
		PyroDraw.annulus(pose, add, rad * 0.8f, rad, 0xFF000000, PyroDraw.dim(GREEN, 0.7f * fade), 0.02f);
		PyroDraw.annulus(pose, add, rad, rad * 1.04f, PyroDraw.dim(PALE, 0.8f * fade), 0xFF000000, 0.02f);
		pose.popPose();
		// circuit lines cracking out across the ground behind the wave
		RandomSource r = RandomSource.create(f.seed);
		for (int i = 0; i < 14; i++) {
			double ang = i * Math.PI * 2 / 14 + r.nextDouble() * 0.3;
			List<Vec3> pts = new ArrayList<>();
			double x = 0, z = 0, a = ang;
			pts.add(c.add(0, 0.06, 0));
			double reach = rad * (0.7 + r.nextDouble() * 0.3);
			for (double s = 0.6; s < reach; s += 0.6) {
				if (r.nextInt(3) == 0) {
					a += (r.nextBoolean() ? 1 : -1) * Math.PI / 4;
				} else {
					a = ang + (a - ang) * 0.3;
				}
				x += Math.cos(a) * 0.6;
				z += Math.sin(a) * 0.6;
				pts.add(c.add(x, 0.06, z));
			}
			if (pts.size() > 1) {
				PyroDraw.ribbon(pose, add, pts, s -> 0.07, s -> PyroDraw.dim(GREEN, (float) (0.85 * fade * (0.4 + 0.6 * s))));
			}
		}
	}

	/** The Charged Beam's charge: an orb of green swelling at his eye, rings of circuitry closing in on it. */
	private static void charging(PoseStack pose, MultiBufferSource buffers, Vec3 cam, Player pl, float partial, float time, float t) {
		float k = Mth.clamp(t / UpgradeKit.CHARGE_TICKS, 0f, 1f);
		Vec3 at = eye(pl, partial).add(pl.getViewVector(partial).scale(0.15)).subtract(cam);
		VertexConsumer add = PyroDraw.additive(buffers);
		float pulse = 0.9f + 0.1f * Mth.sin(time * 2.2f);
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), at, (0.2f + 0.45f * k) * pulse, PyroDraw.dim(GREEN, 0.5f + 0.5f * k));
		PyroDraw.glow(pose, add, at, (0.06f + 0.16f * k) * pulse, PyroDraw.dim(CORE, 0.9f));
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		PyroDraw.alignY(pose, pl.getViewVector(partial));
		for (int i = 0; i < 2; i++) {
			pose.pushPose();
			pose.mulPose(com.mojang.math.Axis.YP.rotation(time * (i == 0 ? 0.3f : -0.4f)));
			pose.mulPose(com.mojang.math.Axis.XP.rotation(i == 0 ? 0.6f : -0.6f));
			PyroDraw.band(pose, add, 0.15f + 0.55f * (1f - k), 0.03f, PyroDraw.dim(PALE, 0.4f + 0.5f * k), 0xFF000000);
			pose.popPose();
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- the Liquid Form

	/** A puddle of liquid metal: a low wobbling dome of his techno skin, circuit glints running through it and his eye glowing at the front. */
	private static void puddle(Level level, PoseStack pose, MultiBufferSource buffers, Vec3 cam, Player pl, float partial, float time) {
		Vec3 at = pl.getPosition(partial);
		Vec3 c = at.subtract(cam);
		Vec3 vel = pl.getDeltaMovement();
		Vec3 flatV = new Vec3(vel.x, 0, vel.z);
		double speed = Math.min(1.0, flatV.length() * 4);
		Vec3 look = pl.getViewVector(partial);
		Vec3 fwd = new Vec3(look.x, 0, look.z);
		fwd = fwd.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : fwd.normalize();
		Vec3 stretchDir = speed > 0.05 ? flatV.normalize() : fwd;
		int light = LevelRenderer.getLightColor(level, BlockPos.containing(at.add(0, 0.2, 0)));
		int segs = 24, rings = 5;
		float R = 0.74f;
		float H = 0.17f;
		VertexConsumer base = lazy(buffers, RenderType.entityTranslucent(TECHNO));
		VertexConsumer glow = lazy(buffers, RenderType.eyes(TECHNO_GLOW));
		PoseStack.Pose last = pose.last();
		for (int pass = 0; pass < 2; pass++) {
			VertexConsumer vc = pass == 0 ? base : glow;
			float lift = pass * 0.004f;
			for (int ri = 0; ri < rings; ri++) {
				for (int j = 0; j < segs; j++) {
					double[][] q = new double[4][];
					int[] idx = { ri, j, ri + 1, j, ri + 1, j + 1, ri, j + 1 };
					for (int k = 0; k < 4; k++) {
						int ring = idx[k * 2], seg = idx[k * 2 + 1];
						double rr = ring / (double) rings;
						double ang = Mth.TWO_PI * seg / segs;
						double edge = R * (1 + 0.07 * Math.sin(3 * ang + time * 0.21) + 0.05 * Math.sin(5 * ang - time * 0.33) + 0.03 * Math.sin(9 * ang + time * 0.5));
						double ox = Math.cos(ang) * edge * rr, oz = Math.sin(ang) * edge * rr;
						// stretched along the way he flows
						double along = ox * stretchDir.x + oz * stretchDir.z;
						ox += stretchDir.x * along * 0.5 * speed;
						oz += stretchDir.z * along * 0.5 * speed;
						double h = H * Math.pow(Math.max(0, 1 - rr * rr), 0.7) * (1 + 0.08 * Math.sin(time * 0.3 + ang * 2)) + 0.012 + lift;
						q[k] = new double[] { ox, h, oz };
					}
					int col;
					if (pass == 0) {
						col = 0xF8FFFFFF;
					} else {
						float pulse = 0.55f + 0.45f * Mth.sin(time * 0.25f - ri * 0.9f);
						col = grey(pulse * 0.85f, 1f);
					}
					for (int k = 0; k < 4; k++) {
						double[] p = q[k];
						float u = (float) (p[0] * 0.9 + 0.5), v = (float) (p[2] * 0.9 + 0.5 + time * 0.004);
						vert(vc, last, c.x + p[0], c.y + p[1], c.z + p[2], col, u, v, light, 0, 1, 0);
					}
					if (pass == 1) {
						for (int k = 3; k >= 0; k--) {
							double[] p = q[k];
							float u = (float) (p[0] * 0.9 + 0.5), v = (float) (p[2] * 0.9 + 0.5 + time * 0.004);
							vert(vc, last, c.x + p[0], c.y + p[1], c.z + p[2], col, u, v, light, 0, -1, 0);
						}
					}
				}
			}
		}
		// a glossy highlight riding on top, and his eye at the front
		VertexConsumer add = PyroDraw.additive(buffers);
		PyroDraw.glow(pose, add, c.add(fwd.scale(-0.12)).add(0.05, H + 0.03, 0.05), 0.16f, PyroDraw.dim(0xFFD0E8E0, 0.25f));
		Vec3 eye = c.add(fwd.scale(R * 0.62)).add(0, H * 0.55 + 0.02, 0);
		float blink = 0.85f + 0.15f * Mth.sin(time * 0.4f);
		PyroDraw.glow(pose, PyroDraw.glowBuffer(buffers), eye, 0.28f * blink, PyroDraw.dim(GREEN, 0.85f));
		PyroDraw.glow(pose, add, eye, 0.085f, PyroDraw.dim(CORE, 0.95f));
		PyroDraw.glow(pose, add, eye, 0.14f, PyroDraw.dim(GREEN, 0.9f));
	}
}
