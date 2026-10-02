package com.dogpound.prideinventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/** One extra-row slot. Locked rows take nothing in but always let you take things out (nothing gets trapped). */
public class ExtraSlot extends SlotItemHandler {
    public static final int HIDDEN = -9999;
    final ExtraInv inv;
    public final int index;

    public ExtraSlot(ExtraInv inv, int index) {
        super(inv.items, index, HIDDEN, HIDDEN);   // the screen moves visible rows into place; others stay off-screen
        this.inv = inv;
        this.index = index;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return inv.usable(index) && super.isItemValid(stack);
    }

    @Override
    public boolean canTakeStack(EntityPlayer p) {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return xPos != HIDDEN;                     // hidden rows don't draw or take clicks
    }
}
