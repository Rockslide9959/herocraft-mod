const fs=require('fs');
function edit(f,a,b){let s=fs.readFileSync(f,'utf8').replace(/\r\n/g,'\n');const n=s.split(a).length-1;if(n!==1)throw f+': '+n+' matches for '+a.slice(0,40);s=s.replace(a,b);fs.writeFileSync(f,s.replace(/\n/g,'\r\n'));}
const F='src/main/java/com/projecthero/mod/ironman/item/IronManArmorItem.java';
edit(F,`	@Override
	public void appendHoverText(`,`	/**
	 * v0.14.29: the Mark 5 only ever deploys from its suitcase, so right-clicking any loose Mark 5 piece while all four
	 * are in your inventory packs them into a fresh Mark 5 Suitcase (hand, hotbar, inventory, else at your feet)
	 * instead of strapping on one piece. With fewer than four you equip the piece the normal way.
	 */
	@Override
	public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
		if ("mark_v".equals(suitId) && player instanceof ServerPlayer sp && packLooseMarkV(sp)) {
			return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), false);
		}
		if ("mark_v".equals(suitId) && level.isClientSide() && hasAllLooseMarkV(player)) {
			return net.minecraft.world.InteractionResultHolder.success(player.getItemInHand(hand));
		}
		return super.use(level, player, hand);
	}

	private static boolean hasAllLooseMarkV(Player player) {
		boolean[] have = new boolean[4];
		for (ItemStack s : player.getInventory().items) {
			if (s.getItem() instanceof IronManArmorItem a && "mark_v".equals(a.suitId)) {
				have[SuitcaseContents.slotOf(a.getType())] = true;
			}
		}
		return have[0] && have[1] && have[2] && have[3];
	}

	/** Moves one of each loose Mark 5 piece out of the inventory into a new case. Returns false (no change) unless all four are there. */
	public static boolean packLooseMarkV(ServerPlayer player) {
		if (!hasAllLooseMarkV(player)) {
			return false;
		}
		var inv = player.getInventory();
		net.minecraft.core.NonNullList<ItemStack> slots = net.minecraft.core.NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY);
		for (int i = 0; i < inv.items.size(); i++) {
			ItemStack s = inv.items.get(i);
			if (s.getItem() instanceof IronManArmorItem a && "mark_v".equals(a.suitId)) {
				int idx = SuitcaseContents.slotOf(a.getType());
				if (slots.get(idx).isEmpty()) {
					slots.set(idx, s.split(1));
				}
			}
		}
		ItemStack caseStack = new ItemStack(IronManItems.MARK_V_SUITCASE);
		SuitcaseContents.write(caseStack, slots);
		com.projecthero.mod.ironman.suit.IronManSuitUpManager.placeSuitcase(player, caseStack);
		inv.setChanged();
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.mk5_packed").withStyle(ChatFormatting.AQUA), true);
		return true;
	}

	@Override
	public void appendHoverText(`);
const L='src/main/resources/assets/projecthero/lang/en_us.json';
const v='The Mark 5 pieces fold into a suitcase. Right-click the case to suit up.';
let t=fs.readFileSync(L,'utf8');
const end=t.lastIndexOf('}');
let body=t.slice(0,end).replace(/\s+$/,'');
t=body+',\r\n  "message.projecthero.ironman.mk5_packed": "'+v+'"\r\n}\r\n';
JSON.parse(t);fs.writeFileSync(L,t);
fs.writeFileSync('scratchpad/lang_v01429_coord.json',JSON.stringify({'message.projecthero.ironman.mk5_packed':v},null,2)+'\n');
