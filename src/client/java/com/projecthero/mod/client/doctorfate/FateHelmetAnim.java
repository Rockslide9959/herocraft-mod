package com.projecthero.mod.client.doctorfate;

import com.projecthero.mod.doctorfate.DoctorFate;
import com.projecthero.mod.doctorfate.DoctorFateConfig;
import com.projecthero.mod.doctorfate.data.FateState;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.4 the Helmet of Fate's put-on and lift-off sequences, frame by frame, for every viewer (driven by the synced
 * {@link FateState#helmetChangeAt}, so everyone sees the same thing). Read by the suit layer (the helmet in the hands, the suit
 * fading), {@link FatePose} (the arms) and the first-person overlay ({@link FateHelmetClient}).
 *
 * <pre>
 *  put on (30 ticks)                                   lift off (24 ticks)
 *  -20..0   summon flying in: one hand reaches up       0..6    hands up to the helmet on the head
 *  0..6     formed in both hands at the chest           6..14   lifted off, overhead
 *  6..14    raised overhead                             14..20  brought down to the chest
 *  14..20   held high, the ankh flares behind it        20..24  it turns to gold motes (the item is given)
 *  20..27   lowered onto the head (the suit fades in)
 *  27..30   settled, hands drop
 * </pre>
 *
 * @param held   the helmet is in the hands (drawn on its own at {@code hy} / {@code hz}), not on the head
 * @param onHead the helmet is drawn on the head
 * @param hy     the held helmet's offset from the head pivot, model pixels (negative = up)
 * @param hz     the same, forward (negative = in front)
 * @param alpha  the held helmet's opacity (it forms / dissolves)
 * @param arms   0..1 how far the arms are taken over to hold it
 * @param reach  0..1 the summon: the main hand raised toward the helmet flying in
 * @param glow   0..1 the gold sheen over the held helmet (forming, held high, dissolving)
 */
public record FateHelmetAnim(boolean held, boolean onHead, float hy, float hz, float alpha, float arms, float reach, float glow) {
	public static final FateHelmetAnim NONE = new FateHelmetAnim(false, false, 0f, 0f, 0f, 0f, 0f, 0f);
	public static final FateHelmetAnim WORN = new FateHelmetAnim(false, true, 0f, 0f, 1f, 0f, 0f, 0f);

	static final float CHEST_Y = 9f;
	static final float CHEST_Z = -7.5f;
	static final float OVER_Y = -11f;
	static final float OVER_Z = -2f;

	public static FateHelmetAnim of(Player player, float partial) {
		FateState s = DoctorFate.peek(player);
		if (s == null || !s.hasPower) {
			return NONE;
		}
		if (DoctorFate.possessed(player)) {
			return WORN;
		}
		if (s.helmetChangeAt <= 0L) {
			return s.helmetOn ? WORN : NONE;
		}
		float t = DoctorFate.helmetSequenceAge(player, partial);
		return s.helmetGoingOn ? putOn(t) : liftOff(t);
	}

	static FateHelmetAnim putOn(float t) {
		if (t < 0f) {
			float reach = smooth((t + 14f) / 10f);
			return new FateHelmetAnim(false, false, 0f, 0f, 0f, 0f, reach, 0f);
		}
		if (t < 6f) {
			float k = t / 6f;
			return new FateHelmetAnim(true, false, CHEST_Y - k, CHEST_Z, Math.min(1f, 0.25f + t / 3f), smooth(t / 3f), 1f - smooth(t / 4f), 1f - k);
		}
		if (t < 14f) {
			float k = smooth((t - 6f) / 8f);
			return new FateHelmetAnim(true, false, Mth.lerp(k, CHEST_Y - 1f, OVER_Y), Mth.lerp(k, CHEST_Z, OVER_Z), 1f, 1f, 0f, 0f);
		}
		if (t < 20f) {
			float bob = Mth.sin((t - 14f) / 6f * Mth.PI) * 0.8f;
			float g = Mth.sin((t - 14f) / 6f * Mth.PI);
			return new FateHelmetAnim(true, false, OVER_Y - bob, OVER_Z, 1f, 1f, 0f, g);
		}
		if (t < 27f) {
			float k = easeOut((t - 20f) / 7f);
			return new FateHelmetAnim(true, false, Mth.lerp(k, OVER_Y, 0f), Mth.lerp(k, OVER_Z, 0f), 1f, 1f, 0f, 0f);
		}
		float k = Mth.clamp((t - 27f) / (DoctorFateConfig.HELMET_EQUIP_TICKS - 27f), 0f, 1f);
		return new FateHelmetAnim(false, true, 0f, 0f, 1f, 1f - smooth(k), 0f, 1f - k);
	}

	static FateHelmetAnim liftOff(float t) {
		if (t < 0f) {
			t = 0f;
		}
		if (t < 6f) {
			return new FateHelmetAnim(false, true, 0f, 0f, 1f, smooth(t / 6f), 0f, 0f);
		}
		if (t < 14f) {
			float k = smooth((t - 6f) / 8f);
			return new FateHelmetAnim(true, false, Mth.lerp(k, 0f, OVER_Y), Mth.lerp(k, 0f, OVER_Z), 1f, 1f, 0f, 0f);
		}
		if (t < 20f) {
			float k = smooth((t - 14f) / 6f);
			return new FateHelmetAnim(true, false, Mth.lerp(k, OVER_Y, CHEST_Y), Mth.lerp(k, OVER_Z, CHEST_Z), 1f, 1f, 0f, 0f);
		}
		float k = Mth.clamp((t - 20f) / (DoctorFateConfig.HELMET_REMOVE_TICKS - 20f), 0f, 1f);
		return new FateHelmetAnim(true, false, CHEST_Y + k, CHEST_Z, 1f - k, 1f - smooth(k), 0f, k);
	}

	static float smooth(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	static float easeOut(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}
}
