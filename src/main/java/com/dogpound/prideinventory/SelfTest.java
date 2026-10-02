package com.dogpound.prideinventory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * Dev check (runs only when a file named "prideinventory-selftest" is in the game folder): on the first player in a
 * world, checks the extra slots exist, fills hotbar + page rows with numbered items, flips pages forward and back,
 * and counts every item after every flip. Puts the player's inventory back exactly, then logs PRIDEINV PASS/FAIL.
 */
final class SelfTest {
    private static boolean ran;

    static void run(EntityPlayerMP p) {
        if (ran || !new File("prideinventory-selftest").isFile()) return;
        ran = true;
        List<String> fails = new ArrayList<>();
        ExtraInv inv = ExtraInv.of(p);
        int slots = p.inventoryContainer.inventorySlots.size();
        if (slots != 46 + Config.MAX_SLOTS) fails.add("container has " + slots + " slots, expected " + (46 + Config.MAX_SLOTS));
        // save everything we touch
        ItemStack[] hot = new ItemStack[9];
        for (int i = 0; i < 9; i++) hot[i] = p.inventory.mainInventory.get(i).copy();
        ItemStack[] rows = new ItemStack[inv.items.getSlots()];
        for (int i = 0; i < rows.length; i++) rows[i] = inv.items.getStackInSlot(i).copy();
        int unlocked = inv.unlocked, page = inv.page;
        try {
            inv.unlocked = Math.min(4, Config.maxRows);
            inv.page = 0;
            int k = Pages.pageRows(inv.unlocked);
            if (k == 0) fails.add("no page rows (maxRows/hotbarPages is 0)");
            // numbered items: stack size = which slot it started in (1..45)
            for (int i = 0; i < 9; i++) p.inventory.mainInventory.set(i, new ItemStack(Items.PAPER, i + 1));
            for (int r = 0; r < k; r++) for (int c = 0; c < 9; c++) inv.items.setStackInSlot(r * 9 + c, new ItemStack(Items.PAPER, 10 + r * 9 + c));
            int total = count(p, inv, k);
            for (int step = 0; step < 2 * (k + 1); step++) {
                Pages.flip(p, step < k + 1 ? 1 : -1);
                if (count(p, inv, k) != total) { fails.add("items changed after flip " + (step + 1)); break; }
            }
            if (inv.page != 0) fails.add("didn't come back to page 1 (page " + (inv.page + 1) + ")");
            for (int i = 0; i < 9; i++) if (p.inventory.mainInventory.get(i).getCount() != i + 1) { fails.add("hotbar slot " + i + " not home"); break; }
            if (k > 0) {
                Pages.flip(p, 1);
                if (p.inventory.mainInventory.get(0).getCount() != 10) fails.add("page 2 should put row 1 in the hotbar");
                Pages.flip(p, -1);
            }
        } catch (Throwable t) {
            fails.add("exception " + t);
        } finally {
            for (int i = 0; i < 9; i++) p.inventory.mainInventory.set(i, hot[i]);
            for (int i = 0; i < rows.length; i++) inv.items.setStackInSlot(i, rows[i]);
            inv.unlocked = unlocked; inv.page = page;
            p.inventoryContainer.detectAndSendChanges();
        }
        unlockTest(p, fails);
        System.out.println(fails.isEmpty() ? "[PrideInventory] PRIDEINV PASS: slots, page flips, no loss/dup" : "[PrideInventory] PRIDEINV FAIL: " + fails);
    }

    /** the rules: numbers alone, then for real — give the (throwaway test world) player every advancement */
    private static void unlockTest(EntityPlayerMP p, List<String> fails) {
        try {
            if (Config.advancementsPerRow > 0 && Unlocks.earned(0, 0, Config.advancementsPerRow * 2, 0, 0, 0) != Math.min(Config.maxRows, Config.startRows + 2))
                fails.add("rule: " + (Config.advancementsPerRow * 2) + " advancements should be 2 rows");
            if (Unlocks.earned(0, 0, 0, 0, 0, 0) != Math.min(Config.maxRows, Config.startRows)) fails.add("rule: a new player should have startRows");
            if (Unlocks.earned(999, 0, 0, 0, 0, 0) != Config.maxRows) fails.add("rule: rows must stop at maxRows");
            if (Unlocks.everything(p)) { System.out.println("[PrideInventory] PRIDEINV unlock test: player has everything (creative/op), live part skipped"); return; }
            ExtraInv inv = ExtraInv.of(p);
            int before = inv.unlocked;
            p.getServer().getCommandManager().executeCommand(p.getServer(), "advancement grant " + p.getName() + " everything");
            // the AdvancementEvent handler must already have recounted — no manual update here
            int want = Unlocks.earned(inv.bought, inv.granted, inv.advancements, inv.hours, inv.blocksMined, inv.mobKills);
            System.out.println("[PrideInventory] PRIDEINV unlock test: " + inv.advancements + " advancements -> rows " + before + " -> " + inv.unlocked + " (rules say " + want + ")");
            if (inv.advancements < Config.advancementsPerRow) fails.add("only " + inv.advancements + " advancements counted after granting all");
            else if (inv.unlocked != want || inv.unlocked <= before) fails.add("advancements didn't unlock rows right away (" + before + " -> " + inv.unlocked + ", want " + want + ")");
        } catch (Throwable t) {
            fails.add("unlock test exception " + t);
        }
    }

    /** sum of stack sizes x (1 + slot-number weight) so a swap between slots still counts, but a loss/dup never matches */
    private static int count(EntityPlayerMP p, ExtraInv inv, int k) {
        int total = 0;
        for (int i = 0; i < 9; i++) total += p.inventory.mainInventory.get(i).getCount();
        for (int r = 0; r < k; r++) for (int c = 0; c < 9; c++) total += inv.items.getStackInSlot(r * 9 + c).getCount();
        return total;
    }
}
