package com.projecthero.mod.client.ironman;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ironman.IronManFaceplate;
import com.projecthero.mod.ironman.IronManFaceplateLook;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManAssemblyPlan;
import com.projecthero.mod.ironman.suit.IronManSuitFx;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.cache.object.GeoBone;

/**
 * v0.14.21 self-assembly: the per-bone motion of an Iron Man piece going on or coming off, and the H faceplate lift.
 * Called from {@code SuperheroArmorRenderer#renderRecursively} for every bone of an Iron Man piece, inside a pose push
 * of its own, <i>before</i> GeckoLib applies the bone -- so the offset (outward along the bone's own direction, a 10-35
 * degree tilt, 0.6-0.8 scale; or, for the Mark V case, from the right hand) is applied in the parent's space about the
 * bone's own pivot, and children (thigh -> thigh plate) ride along. Everything comes from the synced
 * {@link IronManSuitFx} clock and {@link IronManAssemblyPlan}, so every viewer sees the same frame.
 *
 * <p>Also here: the snap FX (a few sparks at the bone's world position, a quiet high {@code ironman_clamp} click, rate-
 * limited), the arc-reactor / eye flash intensities the glow layer reads, and the client-side eased faceplate lift
 * (stepped per tick from {@code IRON_MAN_FACEPLATE_OPEN}, like the Mark V blades).
 */
public final class IronManAssemblyClient {
	/** Set by {@link IronManSuitGlowLayer} around its re-render: bones that have not snapped home stay dark. */
	public static boolean glowPass;

	private static final Map<Integer, float[]> LIFT = new HashMap<>();
	private static final Map<Integer, Long> EYE_FLASH_AT = new HashMap<>();
	private static final Map<Integer, Long> LAST_CLICK = new HashMap<>();
	private static final Set<String> SNAPPED = new HashSet<>();
	private static final int FLASH_TICKS = 5;

	private IronManAssemblyClient() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(IronManAssemblyClient::tick);
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null) {
			LIFT.clear();
			EYE_FLASH_AT.clear();
			LAST_CLICK.clear();
			SNAPPED.clear();
			IronManSuitReveal.clear();
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		IronManSuitReveal.clientTicks++;
		Set<Integer> seen = new HashSet<>();
		for (Player p : mc.level.players()) {
			IronManSuitReveal.observe(p);
			boolean open = p.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof IronManArmorItem && IronManFaceplate.isOpen(p);
			float[] s = LIFT.get(p.getId());
			if (s == null) {
				if (!open) {
					continue;
				}
				s = new float[2];
				LIFT.put(p.getId(), s);
			}
			s[0] = s[1];
			s[1] = IronManFaceplateLook.step(s[1], open);
			if (s[0] > 0f && s[1] == 0f) {
				// sealed: white eye flash + a little sparkle at the eyes
				EYE_FLASH_AT.put(p.getId(), mc.level.getGameTime());
				Vec3 eye = p.getEyePosition().add(p.getViewVector(1f).scale(0.33));
				for (int i = 0; i < 3; i++) {
					mc.level.addParticle(ParticleTypes.END_ROD, eye.x + (i - 1) * 0.08, eye.y + 0.02, eye.z, 0, 0.01, 0);
				}
			}
			seen.add(p.getId());
		}
		LIFT.keySet().removeIf(id -> !seen.contains(id) || LIFT.get(id)[0] == 0f && LIFT.get(id)[1] == 0f);
		if (SNAPPED.size() > 512) {
			SNAPPED.clear();
		}
	}

	/** The eased faceplate lift for {@code player}: 0 shut .. 1 raised (linear, shape it with {@link IronManFaceplateLook#angle}). */
	public static float lift(Player player, float partialTick) {
		float[] s = LIFT.get(player.getId());
		if (s == null) {
			return 0f;
		}
		return s[0] + (s[1] - s[0]) * partialTick;
	}

	/** True while the H faceplate is up far enough that the face should show through the helmet. */
	public static boolean faceShowing(Player player, float partialTick) {
		return IronManFaceplateLook.faceOpen(lift(player, partialTick));
	}

	/**
	 * Should the {@code helmet} bone's front (north) quads be skipped this pass? While the H faceplate is up, and -- in
	 * the glow pass -- while the faceplate has not snapped on during a lock-on / release: the shell's front shares the
	 * faceplate's UV (eye slits included), so it would otherwise glow eyes over the bare face.
	 */
	public static boolean helmetFrontHidden(Player player, float partialTick) {
		if (faceShowing(player, partialTick)) {
			return true;
		}
		if (!glowPass || player.level() == null) {
			return false;
		}
		float p = IronManSuitReveal.progress(player, EquipmentSlot.HEAD, partialTick);
		return p < 1f && !IronManAssemblyPlan.snapped(0, "faceplate", IronManSuitReveal.fromCase(player), p);
	}

	/**
	 * Applies this frame's assembly / faceplate offset for {@code bone} onto {@code pose} (the caller has pushed it).
	 * Returns false if the bone must not be drawn at all this pass (not yet flown in, already flown off, or -- in the
	 * glow pass -- not yet snapped home).
	 */
	public static boolean apply(PoseStack pose, GeoBone bone, Player player, EquipmentSlot slot, float partialTick) {
		int bit = IronManSuitFx.bit(slot);
		if (bit < 0 || player.level() == null) {
			return true;
		}
		String name = bone.getName();
		if (bit == 1 && (name.equals("right_blade") || name.equals("left_blade"))
				&& IronManSuitReveal.progress(player, slot, partialTick) < 1f) {
			return false; // Mark V blades never show mid-assembly
		}
		if (bit == 0 && name.equals("faceplate")) {
			float x = lift(player, partialTick);
			if (x > 0f) {
				if (glowPass && x > 0.02f) {
					return false; // eyes off while the faceplate is up
				}
				float deg = IronManFaceplateLook.angle(x);
				// a pure rotation about the plate's own top-front edge: that edge never moves, so the plate stays
				// attached to the helmet (and, as a sibling under armorHead, follows the head) at every angle
				float hx = -IronManFaceplateLook.HINGE[0] / 16f;
				float hy = IronManFaceplateLook.HINGE[1] / 16f;
				float hz = IronManFaceplateLook.HINGE[2] / 16f;
				pose.translate(hx, hy, hz);
				pose.mulPose(Axis.XP.rotationDegrees(deg));
				pose.translate(-hx, -hy, -hz);
			}
		}
		IronManSuitFx fx = IronManSuitFx.of(player);
		long now = player.level().getGameTime();
		float p = IronManSuitReveal.progress(player, slot, partialTick); // smooth + monotonic
		if (p >= 1f) {
			return true;
		}
		boolean fromCase = fx.style() == IronManSuitFx.STYLE_CASE;
		float start = IronManAssemblyPlan.start(bit, name, fromCase);
		if (start < 0f) {
			return true;
		}
		boolean assembling = IronManSuitReveal.assembling(player, slot);
		float t = IronManAssemblyPlan.local(bit, name, fromCase, p);
		if (t <= 0f) {
			return false;
		}
		if (glowPass && t < IronManAssemblyPlan.SNAP) {
			return false;
		}
		float d = IronManAssemblyPlan.displacement(t, assembling);
		float gx = bone.getPivotX() / 16f;
		float gy = bone.getPivotY() / 16f;
		float gz = bone.getPivotZ() / 16f;
		if (d != 0f) {
			// pivot in bedrock coordinates (GeckoLib stores x negated)
			float bx = -bone.getPivotX();
			float by = bone.getPivotY();
			float bz = bone.getPivotZ();
			float[] dv;
			if (fromCase) {
				float[] hand = IronManAssemblyPlan.CASE_HAND;
				dv = new float[] { hand[0] - bx, hand[1] - by, hand[2] - bz };
			} else {
				float[] o = IronManAssemblyPlan.outward(name);
				float dist = IronManAssemblyPlan.distance(name);
				dv = new float[] { o[0] * dist, o[1] * dist, o[2] * dist };
			}
			pose.translate(-dv[0] * d / 16f, dv[1] * d / 16f, dv[2] * d / 16f);
			float s0 = fromCase ? 0.35f : IronManAssemblyPlan.startScale(name) * (assembling ? 1f : 0.75f);
			float sc = IronManAssemblyPlan.scale(d, s0);
			float tilt = IronManAssemblyPlan.tilt(name) * d;
			pose.translate(gx, gy, gz);
			pose.mulPose(switch (IronManAssemblyPlan.tiltAxis(name)) {
				case 0 -> Axis.XP.rotationDegrees(tilt);
				case 1 -> Axis.YP.rotationDegrees(tilt);
				default -> Axis.ZP.rotationDegrees(tilt);
			});
			pose.scale(sc, sc, sc);
			pose.translate(-gx, -gy, -gz);
		}
		if (assembling && !glowPass) {
			snapFx(pose, player, fx, bit, name, fromCase, now, partialTick, gx, gy, gz);
		}
		return true;
	}

	/** Sparks + a quiet click the first frame a bone is home (only within 3 ticks of its snap, so late joiners stay quiet). */
	private static void snapFx(PoseStack pose, Player player, IronManSuitFx fx, int bit, String name, boolean fromCase,
			long now, float partialTick, float gx, float gy, float gz) {
		float snapTick = IronManAssemblyPlan.snapAt(bit, name, fromCase) * IronManSuitFx.LOCK_TICKS;
		float age = now - fx.start(bit) + partialTick;
		if (age < snapTick || age > snapTick + 3f) {
			return;
		}
		String key = player.getId() + ":" + bit + ":" + name + ":" + fx.start(bit);
		if (!SNAPPED.add(key)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		Vector3f v = pose.last().pose().transformPosition(new Vector3f(gx, gy, gz));
		Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
		Vec3 at = new Vec3(cam.x + v.x, cam.y + v.y, cam.z + v.z);
		if (at.distanceToSqr(player.position().add(0, 1, 0)) > 9.0) {
			at = player.position().add(0, 1.2, 0); // not a world-space render (GUI preview): fall back to the body
		}
		var rnd = player.getRandom();
		for (int i = 0; i < 3; i++) {
			mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, (rnd.nextFloat() - 0.5f) * 0.12f,
					rnd.nextFloat() * 0.08f, (rnd.nextFloat() - 0.5f) * 0.12f);
		}
		if (name.equals("faceplate")) {
			for (int i = 0; i < 4; i++) {
				mc.level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, (rnd.nextFloat() - 0.5f) * 0.05f, 0.02f,
						(rnd.nextFloat() - 0.5f) * 0.05f);
			}
		}
		if (bit == 1 && name.equals("arc_reactor")) {
			REACTOR_AT.put(player.getId(), at);
		}
		if (bit == 1 && name.equals(lastOf(1, fromCase))) {
			// the chest is complete: the arc reactor flares
			Vec3 r = REACTOR_AT.getOrDefault(player.getId(), player.position().add(0, 1.15, 0));
			DustParticleOptions cyan = new DustParticleOptions(new Vector3f(0.35f, 0.95f, 1f), 1.3f);
			for (int i = 0; i < 8; i++) {
				mc.level.addParticle(cyan, r.x, r.y, r.z, (rnd.nextFloat() - 0.5f) * 0.25f, (rnd.nextFloat() - 0.5f) * 0.25f,
						(rnd.nextFloat() - 0.5f) * 0.25f);
			}
		}
		Long last = LAST_CLICK.get(player.getId());
		if (last == null || now - last >= 2) {
			LAST_CLICK.put(player.getId(), now);
			mc.level.playLocalSound(at.x, at.y, at.z, IronManSounds.CLAMP, SoundSource.PLAYERS, 0.22f,
					1.7f + rnd.nextFloat() * 0.25f, false);
		}
	}

	private static final Map<Integer, Vec3> REACTOR_AT = new HashMap<>();

	/**
	 * How brightly the piece in {@code slot} flashes right now (0..1): the chest's arc reactor once the last chest bone
	 * is home, the eyes once the faceplate snaps on (suit-up) or seals (H). The glow layer draws extra passes with it.
	 */
	public static float flash(Player player, EquipmentSlot slot, float partialTick) {
		int bit = IronManSuitFx.bit(slot);
		if ((bit != 0 && bit != 1) || player.level() == null) {
			return 0f;
		}
		IronManSuitFx fx = IronManSuitFx.of(player);
		long now = player.level().getGameTime();
		float best = 0f;
		if (fx.assembling(bit) && fx.start(bit) > 0L) {
			boolean fromCase = fx.style() == IronManSuitFx.STYLE_CASE;
			String last = bit == 1 ? lastOf(1, fromCase) : "faceplate";
			float snapTick = IronManAssemblyPlan.snapAt(bit, last, fromCase) * IronManSuitFx.LOCK_TICKS;
			float since = now - fx.start(bit) + partialTick - snapTick;
			if (since >= 0f && since < FLASH_TICKS) {
				best = 1f - since / FLASH_TICKS;
			}
		}
		if (bit == 0) {
			Long at = EYE_FLASH_AT.get(player.getId());
			if (at != null) {
				float since = now - at + partialTick;
				if (since >= 0f && since < FLASH_TICKS) {
					best = Math.max(best, 1f - since / FLASH_TICKS);
				}
			}
		}
		return best;
	}

	private static String lastOf(int bit, boolean fromCase) {
		var g = IronManAssemblyPlan.groups(bit, fromCase);
		return g.get(g.size() - 1).get(0);
	}

	/**
	 * First person: the chestplate's gauntlet flies in to the hand with the same timetable as the third-person
	 * {@code right_gauntlet} / {@code left_gauntlet} bone. Applied in the vanilla arm's space (y down). Returns false
	 * while the gauntlet has not started (or has already gone).
	 */
	public static boolean applyFirstPerson(PoseStack pose, Player player, boolean right, float partialTick) {
		float p = IronManSuitReveal.progress(player, EquipmentSlot.CHEST, partialTick);
		if (p >= 1f) {
			return true;
		}
		boolean fromCase = IronManSuitReveal.fromCase(player);
		String name = right ? "right_gauntlet" : "left_gauntlet";
		float t = IronManAssemblyPlan.local(1, name, fromCase, p);
		if (t <= 0f) {
			return false;
		}
		float d = IronManAssemblyPlan.displacement(t, IronManSuitReveal.assembling(player, EquipmentSlot.CHEST));
		if (d == 0f) {
			return true;
		}
		float[] o = IronManAssemblyPlan.outward(name);
		float dist = IronManAssemblyPlan.distance(name);
		// bedrock (y up) -> vanilla arm space (y down)
		pose.translate(o[0] * dist * d / 16f, -o[1] * dist * d / 16f, o[2] * dist * d / 16f);
		float sc = IronManAssemblyPlan.scale(d, IronManAssemblyPlan.startScale(name));
		pose.translate(0f, 7f / 16f, 0f);
		pose.mulPose(Axis.ZP.rotationDegrees(IronManAssemblyPlan.tilt(name) * d));
		pose.scale(sc, sc, sc);
		pose.translate(0f, -7f / 16f, 0f);
		return true;
	}
}
