package com.dogpound.prideinventory;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/**
 * Hotbar pages. The hotbar stays the normal 9 slots (every other mod keeps working); the first K unlocked rows are
 * pages that rotate through it: forward = row 1 comes into the hotbar, the hotbar goes to the back of the line.
 * Only swaps, never copies — no item can be duplicated or lost.
 */
public final class Pages {
    private Pages() {}

    /** how many rows act as pages */
    static int pageRows(int unlocked) {
        return Math.max(0, Math.min(Config.hotbarPages, unlocked));
    }

    /** turn one page forward (+1) or back (-1) */
    public static void flip(EntityPlayerMP p, int dir) {
        ExtraInv inv = ExtraInv.of(p);
        if (inv == null) return;
        int k = pageRows(inv.unlocked);
        if (k == 0) return;
        rotate(p, inv, k, dir > 0);
        inv.page = Math.floorMod(inv.page + (dir > 0 ? 1 : -1), k + 1);
        p.inventoryContainer.detectAndSendChanges();
        Net.sync(p);
    }

    /** turn pages forward until page {@code target} is in the hotbar (the wide hotbar jumps straight to a row) */
    public static void goTo(EntityPlayerMP p, int target) {
        ExtraInv inv = ExtraInv.of(p);
        if (inv == null) return;
        int k = pageRows(inv.unlocked);
        if (k == 0 || target < 0 || target > k || target == inv.page) return;
        while (inv.page != target) {
            rotate(p, inv, k, true);
            inv.page = Math.floorMod(inv.page + 1, k + 1);
        }
        p.inventoryContainer.detectAndSendChanges();
        Net.sync(p);
    }

    /** back to the original hotbar using the page count that was in effect */
    static void home(EntityPlayerMP p, ExtraInv inv, int k) {
        if (k <= 0) { inv.page = 0; return; }
        while (inv.page != 0) {
            rotate(p, inv, k, false);
            inv.page = Math.floorMod(inv.page - 1, k + 1);
        }
    }

    /** cycle: hotbar <- row0 <- row1 <- ... <- row(k-1) <- hotbar (forward), or the reverse */
    private static void rotate(EntityPlayerMP p, ExtraInv inv, int k, boolean forward) {
        for (int col = 0; col < 9; col++) {
            ItemStack[] ring = new ItemStack[k + 1];
            ring[0] = p.inventory.mainInventory.get(col);
            for (int r = 0; r < k; r++) ring[r + 1] = inv.items.getStackInSlot(r * 9 + col);
            ItemStack[] out = new ItemStack[k + 1];
            for (int i = 0; i <= k; i++) out[i] = forward ? ring[(i + 1) % (k + 1)] : ring[Math.floorMod(i - 1, k + 1)];
            p.inventory.mainInventory.set(col, out[0]);
            for (int r = 0; r < k; r++) inv.items.setStackInSlot(r * 9 + col, out[r + 1]);
        }
    }
}
