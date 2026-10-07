package com.dogpound.prideinventory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

/**
 * Another player's inventory (armour, offhand, main, hotbar, their unlocked extra rows) above your own.
 * The server is the only authority: every click path (pickup, shift-click, number keys, drag, double-click, throw)
 * ends in {@link Share#mayTake} / {@link Share#mayGive} checks on the owner's live lock state. Only real slot
 * moves happen — nothing is ever copied — so there is nothing to dupe. Slot order:
 * 0-3 armour (head..feet), 4 offhand, 5-31 main, 32-40 hotbar, 41.. extra rows, then the viewer's 27 + 9.
 */
public class ShareContainer extends Container {
    static final int OFFHAND = 4, MAIN = 5, HOTBAR = 32, EXTRA = 41;
    private static final EntityEquipmentSlot[] ARMOR = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET};

    final EntityPlayer viewer;
    final EntityPlayerMP owner;   // server only (null on the client)
    final int rows, ownerSlots;
    int flags;                    // client copy of the owner's state: 1 = unlocked, 2 = gifts allowed while locked
    private int sentFlags = -1;
    private boolean wasUnlocked;  // opened (or became) unlocked: locking again closes the view
    // what the viewer took (-) / gave (+), told to the owner after 2 quiet seconds or on close
    private final List<ItemStack> moved = new ArrayList<>();
    private final List<Integer> counts = new ArrayList<>();
    private int quiet;

    /** server */
    ShareContainer(EntityPlayer viewer, EntityPlayerMP owner, int rows) {
        this(viewer, owner, new InvWrapper(owner.inventory), ExtraInv.of(owner).items, rows);
    }

    /** client: empty stand-ins, the server fills every slot */
    ShareContainer(EntityPlayer viewer, int rows) {
        this(viewer, null, new ItemStackHandler(41), new ItemStackHandler(Config.MAX_SLOTS), rows);
    }

    private ShareContainer(EntityPlayer viewer, EntityPlayerMP owner, IItemHandlerModifiable vanilla, IItemHandlerModifiable extra, int rows) {
        this.viewer = viewer;
        this.owner = owner;
        this.rows = Math.max(0, Math.min(rows, Config.MAX_ROWS_EVER));
        this.ownerSlots = EXTRA + this.rows * 9;
        for (int k = 0; k < 4; k++) {
            ShareSlot s = new ShareSlot(this, vanilla, 36 + ARMOR[k].getIndex(), 8 + k * 18, 27, ARMOR[k], false);
            s.setBackgroundName(ItemArmor.EMPTY_SLOT_NAMES[ARMOR[k].getIndex()]);
            addSlotToContainer(s);
        }
        addSlotToContainer(new ShareSlot(this, vanilla, 40, 98, 27, null, false)).setBackgroundName("minecraft:items/empty_armor_slot_shield");
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlotToContainer(new ShareSlot(this, vanilla, 9 + r * 9 + c, 8 + c * 18, 49 + r * 18, null, false));
        for (int c = 0; c < 9; c++) addSlotToContainer(new ShareSlot(this, vanilla, c, 8 + c * 18, 107, null, false));
        for (int i = 0; i < this.rows * 9; i++) addSlotToContainer(new ShareSlot(this, extra, i, ExtraSlot.HIDDEN, ExtraSlot.HIDDEN, null, true));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(viewer.inventory, 9 + r * 9 + c, 8 + c * 18, 140 + r * 18));
        for (int c = 0; c < 9; c++) addSlotToContainer(new Slot(viewer.inventory, c, 8 + c * 18, 198));
        if (owner != null) { flags = Share.flags(owner); wasUnlocked = (flags & 1) != 0; }
    }

    // ---------------- who may do what (server reads the owner live; client uses the synced copy) ----------------
    int flags() { return owner != null ? Share.flags(owner) : flags; }
    boolean mayTake() { return valid() && (flags() & 1) != 0; }
    boolean mayGive() { return valid() && (flags() & 3) != 0; }

    /** still in range, both alive and online, same world — and not locked again after being unlocked */
    boolean valid() {
        if (owner == null) return true;
        if (wasUnlocked && (Share.flags(owner) & 1) == 0) return false;
        return Share.canView(viewer, owner);
    }

    @Override
    public boolean canInteractWith(EntityPlayer p) {
        return valid();   // false = the server closes the screen on both sides next tick
    }

    @Override
    public void addListener(IContainerListener l) {
        super.addListener(l);
        l.sendWindowProperty(this, 0, flags());
        sentFlags = flags();
    }

    @Override
    public void detectAndSendChanges() {
        if (owner != null) {
            int f = Share.flags(owner);
            if ((f & 1) != 0) wasUnlocked = true;
            if (f != sentFlags) { for (IContainerListener l : listeners) l.sendWindowProperty(this, 0, f); sentFlags = f; }
            if (!moved.isEmpty() && ++quiet >= 40) tell();
        }
        super.detectAndSendChanges();
    }

    @Override
    public void updateProgressBar(int id, int data) {
        if (id == 0) flags = data;
    }

    // ---------------- every click goes through here ----------------
    @Override
    public ItemStack slotClick(int id, int drag, ClickType type, EntityPlayer p) {
        if (!valid()) return ItemStack.EMPTY;
        boolean onOwner = id >= 0 && id < ownerSlots;
        if (onOwner && !mayTake() && !mayGive()) return ItemStack.EMPTY;   // view only (creative middle-click copy too)
        if (owner == null) return super.slotClick(id, drag, type, p);
        ItemStack[] before = new ItemStack[ownerSlots];
        for (int i = 0; i < ownerSlots; i++) before[i] = inventorySlots.get(i).getStack().copy();
        ItemStack r = super.slotClick(id, drag, type, p);
        for (int i = 0; i < ownerSlots; i++) {
            ItemStack a = before[i], b = inventorySlots.get(i).getStack();
            if (same(a, b)) count(a, b.getCount() - a.getCount());
            else { count(a, -a.getCount()); count(b, b.getCount()); }
        }
        return r;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int index) {
        Slot slot = inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack(), copy = stack.copy();
        if (index < ownerSlots) {                       // theirs -> yours
            if (!mayTake() || !mergeItemStack(stack, ownerSlots, ownerSlots + 36, true)) return ItemStack.EMPTY;
        } else {                                        // yours -> theirs (main + hotbar, then extra rows; never armour)
            if (!mayGive()) return ItemStack.EMPTY;
            boolean any = mergeItemStack(stack, MAIN, EXTRA, false);
            if (!stack.isEmpty() && rows > 0) any |= mergeItemStack(stack, EXTRA, ownerSlots, false);
            if (!any) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY); else slot.onSlotChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(p, stack);
        return copy;
    }

    @Override
    public void onContainerClosed(EntityPlayer p) {
        super.onContainerClosed(p);
        if (owner != null) tell();
    }

    // ---------------- "Alex took 16 × Iron Ingot" ----------------
    static boolean same(ItemStack a, ItemStack b) {
        return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private void count(ItemStack s, int n) {
        if (s.isEmpty() || n == 0) return;
        quiet = 0;
        for (int k = 0; k < moved.size(); k++) if (same(moved.get(k), s)) { counts.set(k, counts.get(k) + n); return; }
        ItemStack key = s.copy();
        key.setCount(1);
        moved.add(key);
        counts.add(n);
    }

    /** net change per item, so moving things around inside their inventory says nothing */
    private void tell() {
        for (int k = 0; k < moved.size(); k++) {
            int n = counts.get(k);
            if (n != 0) Share.notice(owner, viewer.getName() + (n < 0 ? " took " : " gave you ") + Math.abs(n) + " × " + moved.get(k).getDisplayName());
        }
        moved.clear();
        counts.clear();
    }

    /** one of the owner's slots; refuses everything the owner's lock state doesn't allow */
    static final class ShareSlot extends SlotItemHandler {
        final ShareContainer c;
        final EntityEquipmentSlot armor;
        final boolean extra;

        ShareSlot(ShareContainer c, IItemHandlerModifiable h, int index, int x, int y, EntityEquipmentSlot armor, boolean extra) {
            super(h, index, x, y);
            this.c = c;
            this.armor = armor;
            this.extra = extra;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            if (!c.mayGive()) return false;
            if (armor != null && !stack.getItem().isValidArmor(stack, armor, c.owner != null ? c.owner : c.viewer)) return false;
            if (extra && c.owner != null && !ExtraInv.of(c.owner).usable(getSlotIndex())) return false;
            return super.isItemValid(stack);
        }

        @Override
        public boolean canTakeStack(EntityPlayer p) {
            if (!c.mayTake()) return false;
            if (armor != null && EnchantmentHelper.hasBindingCurse(getStack())) return false;   // same rule as their own armour
            return super.canTakeStack(p);
        }

        @Override
        public int getSlotStackLimit() {
            return armor != null ? 1 : super.getSlotStackLimit();
        }

        @Override
        public int getItemStackLimit(ItemStack stack) {
            return armor != null ? 1 : super.getItemStackLimit(stack);
        }

        @Override
        public boolean isEnabled() {
            return xPos != ExtraSlot.HIDDEN;   // extra rows scrolled out of view
        }
    }
}
