package com.dogpound.prideinventory;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.items.ItemStackHandler;

/**
 * A player's extra rows (9 wide), saved with the player. `unlocked` rows are usable; the rest show locked.
 * `bought` / `granted` are rows paid for with RealmCoin or handed out by quests/admins; the rest come from play.
 */
public final class ExtraInv {
    @CapabilityInject(ExtraInv.class)
    public static Capability<ExtraInv> CAP;
    public static final ResourceLocation KEY = new ResourceLocation("prideinventory", "extra_rows");

    public final ItemStackHandler items = new ItemStackHandler(Config.MAX_ROWS_EVER * 9);
    public int unlocked;          // worked out by Unlocks (server), synced to the client
    public int bought, granted;   // never lost
    public int page;              // hotbar page shown right now (0 = the original hotbar)
    // progress numbers the tooltip shows (client copy)
    public int advancements, hours, blocksMined, mobKills;
    // inventory sharing: locked (false) = others only look; giveWhenLocked = they may still give you things
    public boolean shareOpen, giveWhenLocked;
    public final List<String> shareLog = new ArrayList<>();
    static final int LOG_SIZE = 30;
    boolean wasEverything;        // creative/op/list state at the last count (not saved): a flip either way recounts at once

    public static ExtraInv of(EntityPlayer p) {
        return p == null || CAP == null ? null : p.getCapability(CAP, null);
    }

    public boolean usable(int slot) {
        return slot / 9 < unlocked;
    }

    NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setTag("items", items.serializeNBT());
        t.setInteger("bought", bought);
        t.setInteger("granted", granted);
        t.setInteger("page", page);
        t.setInteger("unlocked", unlocked);
        t.setBoolean("shareOpen", shareOpen);
        t.setBoolean("giveWhenLocked", giveWhenLocked);
        NBTTagList log = new NBTTagList();
        for (String line : shareLog) log.appendTag(new NBTTagString(line));
        t.setTag("shareLog", log);
        return t;
    }

    void read(NBTTagCompound t) {
        if (t.hasKey("items")) {
            ItemStackHandler saved = new ItemStackHandler();
            saved.deserializeNBT(t.getCompoundTag("items"));
            for (int i = 0; i < Math.min(saved.getSlots(), items.getSlots()); i++) items.setStackInSlot(i, saved.getStackInSlot(i));
        }
        bought = t.getInteger("bought");
        granted = t.getInteger("granted");
        page = t.getInteger("page");
        unlocked = t.getInteger("unlocked");
        shareOpen = t.getBoolean("shareOpen");
        giveWhenLocked = t.getBoolean("giveWhenLocked");
        shareLog.clear();
        NBTTagList log = t.getTagList("shareLog", 8);
        for (int i = 0; i < log.tagCount(); i++) shareLog.add(log.getStringTagAt(i));
    }

    static void register() {
        CapabilityManager.INSTANCE.register(ExtraInv.class, new Capability.IStorage<ExtraInv>() {
            @Override public NBTBase writeNBT(Capability<ExtraInv> c, ExtraInv i, EnumFacing s) { return i.write(); }
            @Override public void readNBT(Capability<ExtraInv> c, ExtraInv i, EnumFacing s, NBTBase n) { i.read((NBTTagCompound) n); }
        }, ExtraInv::new);
    }

    /** attached to every player */
    public static final class Provider implements ICapabilitySerializable<NBTTagCompound> {
        final ExtraInv inv = new ExtraInv();
        @Override public boolean hasCapability(Capability<?> c, @Nullable EnumFacing f) { return c == CAP; }
        @Override public <T> T getCapability(Capability<T> c, @Nullable EnumFacing f) { return c == CAP ? CAP.cast(inv) : null; }
        @Override public NBTTagCompound serializeNBT() { return inv.write(); }
        @Override public void deserializeNBT(NBTTagCompound n) { inv.read(n); }
    }
}
