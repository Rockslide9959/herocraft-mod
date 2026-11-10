// v0.17.3: Andy's Suit follows the same part rules as worn armour (AndyArmorPartsMixin): no piece on a missing / regrowing part,
// and a chestplate sleeve torn off with its shoulder pad (AndyArmour) is not drawn. A cut hand / foot keeps the sleeve / leggings.
const fs = require('fs');
const f = 'src/client/java/com/projecthero/mod/client/quirk/QuirkCostume.java';
let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
function rep(a, b) { if (!t.includes(a)) throw new Error('missing: ' + a.slice(0, 80)); t = t.replace(a, b); }
rep(`		head &= parent.head.visible;
		body &= parent.body.visible;
		legs &= parent.rightLeg.visible || parent.leftLeg.visible;
		m.head.visible = baseParts && head;
		m.body.visible = baseParts && body;
		m.rightArm.visible = baseParts && body && parent.rightArm.visible;
		m.leftArm.visible = baseParts && body && parent.leftArm.visible;
		m.rightLeg.visible = baseParts && legs && parent.rightLeg.visible;
		m.leftLeg.visible = baseParts && legs && parent.leftLeg.visible;
		m.hat.visible = !baseParts && head;
		m.jacket.visible = !baseParts && body;
		m.rightSleeve.visible = !baseParts && body && parent.rightArm.visible;
		m.leftSleeve.visible = !baseParts && body && parent.leftArm.visible;
		m.rightPants.visible = !baseParts && legs && parent.rightLeg.visible;
		m.leftPants.visible = !baseParts && legs && parent.leftLeg.visible;
		return m;`,
`		boolean vHead = vis(parent.head, com.projecthero.mod.andy.data.AndyState.HEAD);
		boolean vBody = vis(parent.body, com.projecthero.mod.andy.data.AndyState.BODY);
		boolean vRA = vis(parent.rightArm, com.projecthero.mod.andy.data.AndyState.RIGHT_ARM);
		boolean vLA = vis(parent.leftArm, com.projecthero.mod.andy.data.AndyState.LEFT_ARM);
		boolean vRL = vis(parent.rightLeg, com.projecthero.mod.andy.data.AndyState.RIGHT_LEG);
		boolean vLL = vis(parent.leftLeg, com.projecthero.mod.andy.data.AndyState.LEFT_LEG);
		// v0.17.3: a chestplate sleeve torn off with its shoulder pad (AndyArmour) is gone until the piece is repaired
		if (body) {
			vRA &= (andyLostPads & com.projecthero.mod.andy.AndyArmour.PAD_RIGHT) == 0;
			vLA &= (andyLostPads & com.projecthero.mod.andy.AndyArmour.PAD_LEFT) == 0;
		}
		head &= vHead;
		body &= vBody;
		legs &= vRL || vLL;
		m.head.visible = baseParts && head;
		m.body.visible = baseParts && body;
		m.rightArm.visible = baseParts && body && vRA;
		m.leftArm.visible = baseParts && body && vLA;
		m.rightLeg.visible = baseParts && legs && vRL;
		m.leftLeg.visible = baseParts && legs && vLL;
		m.hat.visible = !baseParts && head;
		m.jacket.visible = !baseParts && body;
		m.rightSleeve.visible = !baseParts && body && vRA;
		m.leftSleeve.visible = !baseParts && body && vLA;
		m.rightPants.visible = !baseParts && legs && vRL;
		m.leftPants.visible = !baseParts && legs && vLL;
		return m;`);
rep(`	private static void draw(PoseStack pose, MultiBufferSource buffers, int light, int overlay, HumanoidModel<?> parent, boolean slim,`,
`	/**
	 * v0.17.3: for an Andy, which of his six parts may wear a suit piece (bit per AndyState part; -1 = not an Andy, follow the
	 * parent model) and which shoulder pads his chestplate has lost. The same rule as worn armour (AndyArmorPartsMixin): nothing on a
	 * part that is gone or regrowing, while a part with only its hand or foot cut keeps its piece (the parent model hides those for
	 * Andy's own cut drawing, so his own state is read instead).
	 */
	private static int andyParts = -1, andyLostPads = 0;

	private static boolean vis(ModelPart parentPart, int andyPart) {
		return andyParts < 0 ? parentPart.visible : (andyParts & (1 << andyPart)) != 0;
	}

	/** Sets {@link #andyParts} / {@link #andyLostPads} for this wearer (cleared with {@link #endAndy}). */
	private static void beginAndy(LivingEntity entity) {
		andyParts = -1;
		andyLostPads = 0;
		if (entity instanceof Player p) {
			com.projecthero.mod.andy.data.AndyState s = com.projecthero.mod.andy.Andy.peek(p);
			if (s != null && s.hasPower) {
				int mask = 0;
				for (int i = 0; i < com.projecthero.mod.andy.data.AndyState.PARTS; i++) {
					if (!s.broken(i)) {
						mask |= 1 << i;
					}
				}
				andyParts = mask;
			}
		}
		andyLostPads = com.projecthero.mod.andy.AndyArmour.wornLostPads(entity);
	}

	private static void endAndy() {
		andyParts = -1;
		andyLostPads = 0;
	}

	private static void draw(PoseStack pose, MultiBufferSource buffers, int light, int overlay, HumanoidModel<?> parent, boolean slim,`);
// wrap render / renderLegs bodies
rep(`		boolean slim = slim(entity);
		int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f);`,
`		beginAndy(entity);
		try {
			renderPieces(pose, buffers, light, entity, parent, bird);
		} finally {
			endAndy();
		}
	}

	private static void renderPieces(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, HumanoidModel<?> parent, boolean bird) {
		boolean slim = slim(entity);
		int overlay = LivingEntityRenderer.getOverlayCoords(entity, 0f);`);
rep(`	public static void renderLegs(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, HumanoidModel<?> parent) {
		boolean slim = slim(entity);`,
`	public static void renderLegs(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, HumanoidModel<?> parent) {
		beginAndy(entity);
		try {
			renderLegPieces(pose, buffers, light, entity, parent);
		} finally {
			endAndy();
		}
	}

	private static void renderLegPieces(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, HumanoidModel<?> parent) {
		boolean slim = slim(entity);`);
rep(`		Tex chest = worn(player, EquipmentSlot.CHEST);
		if (chest == null) {
			return;
		}
		boolean slim = slim(player);
		ModelPart a = right`,
`		Tex chest = worn(player, EquipmentSlot.CHEST);
		if (chest == null) {
			return;
		}
		// v0.17.3: not on a torn-off shoulder pad's arm, nor on an arm Andy hasn't got
		int lost = com.projecthero.mod.andy.AndyArmour.wornLostPads(player);
		if ((lost & (right ? com.projecthero.mod.andy.AndyArmour.PAD_RIGHT : com.projecthero.mod.andy.AndyArmour.PAD_LEFT)) != 0) {
			return;
		}
		com.projecthero.mod.andy.data.AndyState s = com.projecthero.mod.andy.Andy.peek(player);
		if (s != null && s.hasPower && s.broken(right ? com.projecthero.mod.andy.data.AndyState.RIGHT_ARM : com.projecthero.mod.andy.data.AndyState.LEFT_ARM)) {
			return;
		}
		boolean slim = slim(player);
		ModelPart a = right`);
fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');
