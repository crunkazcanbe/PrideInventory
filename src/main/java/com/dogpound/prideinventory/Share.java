package com.dogpound.prideinventory;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.IGuiHandler;

/**
 * Inventory sharing (requested feature): right-click a player to see their inventory. Locked (the default) = look
 * only; unlocked (the padlock in E, a key, or /invlock off) = others can take and give. The owner hears about it.
 */
public final class Share implements IGuiHandler {
    static final int GUI = 1;

    /** LOW + not receiving cancelled events: any mod that uses right-click on players (and cancels) wins */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void interact(PlayerInteractEvent.EntityInteract e) {
        EntityPlayer p = e.getEntityPlayer();
        if (e.getHand() != EnumHand.MAIN_HAND || !(e.getTarget() instanceof EntityPlayer) || !triggered(p)) return;
        if (p instanceof FakePlayer || e.getTarget() instanceof FakePlayer) return;
        e.setCanceled(true);                                  // client too: no offhand use on the same click
        e.setCancellationResult(EnumActionResult.SUCCESS);
        if (!e.getWorld().isRemote && p instanceof EntityPlayerMP && e.getTarget() instanceof EntityPlayerMP)
            open((EntityPlayerMP) p, (EntityPlayerMP) e.getTarget());
    }

    static boolean triggered(EntityPlayer p) {
        if (!Config.sharing || !p.getHeldItemMainhand().isEmpty() || p.isSpectator()) return false;
        switch (Config.shareTrigger) {
            case "empty_hand": return !p.isSneaking();
            case "sneak_empty_hand": return p.isSneaking();
            case "any_empty_hand": return true;
            default: return false;
        }
    }

    /** both alive, online, same world, close enough */
    static boolean canView(EntityPlayer viewer, EntityPlayerMP owner) {
        if (!Config.sharing || viewer == owner || owner.isDead || viewer.isDead || owner.getHealth() <= 0 || viewer.getHealth() <= 0) return false;
        if (owner.world != viewer.world || owner.isSpectator() || viewer.isSpectator()) return false;
        if (owner.getServer() == null || owner.getServer().getPlayerList().getPlayerByUUID(owner.getUniqueID()) != owner) return false;
        return viewer.getDistanceSq(owner) <= Config.shareDistance * Config.shareDistance;
    }

    /** 1 = unlocked, 2 = gifts allowed while locked */
    static int flags(EntityPlayer owner) {
        ExtraInv o = ExtraInv.of(owner);
        return o == null ? 0 : (o.shareOpen ? 1 : 0) | (o.giveWhenLocked ? 2 : 0);
    }

    static void open(EntityPlayerMP viewer, EntityPlayerMP owner) {
        ExtraInv o = ExtraInv.of(owner);
        if (o == null || !canView(viewer, owner)) return;
        int rows = Math.max(0, Math.min(o.unlocked, Config.maxRows));
        viewer.openGui(PrideInventory.INSTANCE, GUI, viewer.world, owner.getEntityId(), rows, 0);
        if (viewer.openContainer instanceof ShareContainer)
            notice(owner, viewer.getName() + " is looking at your inventory " + (o.shareOpen ? "§a(unlocked: they can take and give)"
                : o.giveWhenLocked ? "§7(locked: they can only give)" : "§7(locked: view only)"));
    }

    /** chat line to the owner + their log */
    static void notice(EntityPlayerMP owner, String text) {
        ExtraInv o = ExtraInv.of(owner);
        if (Config.shareNotify) owner.sendMessage(new TextComponentString("§d✦ §f" + text));
        if (o == null) return;
        o.shareLog.add(new SimpleDateFormat("MM-dd HH:mm").format(new Date()) + "  " + text.replaceAll("§.", ""));
        while (o.shareLog.size() > ExtraInv.LOG_SIZE) o.shareLog.remove(0);
    }

    /** null = leave as is */
    static void set(EntityPlayerMP p, Boolean unlocked, Boolean gifts) {
        ExtraInv o = ExtraInv.of(p);
        if (o == null) return;
        if (unlocked != null) o.shareOpen = unlocked;
        if (gifts != null) o.giveWhenLocked = gifts;
        p.sendMessage(new TextComponentString(status(o)));
        Net.sync(p);   // the padlock; open views of this player pick it up on their next tick
    }

    static String status(ExtraInv o) {
        return o.shareOpen ? "§a✦ Inventory UNLOCKED §7— players who right-click you can take and give."
            : "§c✦ Inventory LOCKED §7— others can only look" + (o.giveWhenLocked ? " and give you things." : ".");
    }

    // ---------------- GUI ----------------
    @Override
    public Object getServerGuiElement(int id, EntityPlayer p, World w, int x, int y, int z) {
        Entity t = w.getEntityByID(x);
        return id == GUI && t instanceof EntityPlayerMP && canView(p, (EntityPlayerMP) t) ? new ShareContainer(p, (EntityPlayerMP) t, y) : null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer p, World w, int x, int y, int z) {
        return id == GUI ? GuiShare.create(p, w, x, y) : null;
    }

    // ---------------- /invlock ----------------
    static final class LockCommand extends CommandBase {
        @Override public String getName() { return "invlock"; }
        @Override public String getUsage(ICommandSender s) { return "/invlock [on|off|gifts on|off|log|status]  (on = locked, others only look)"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] a) throws CommandException {
            EntityPlayerMP p = getCommandSenderAsPlayer(sender);
            ExtraInv o = ExtraInv.of(p);
            if (o == null) return;
            String sub = a.length == 0 ? "toggle" : a[0].toLowerCase();
            switch (sub) {
                case "toggle": set(p, !o.shareOpen, null); return;
                case "on": case "lock": set(p, false, null); return;
                case "off": case "unlock": set(p, true, null); return;
                case "gifts": case "give":
                    set(p, null, a.length > 1 ? a[1].equalsIgnoreCase("on") || a[1].equalsIgnoreCase("true") : !o.giveWhenLocked);
                    return;
                case "status": p.sendMessage(new TextComponentString(status(o))); return;
                case "log":
                    if (o.shareLog.isEmpty()) { p.sendMessage(new TextComponentString("§7Nobody has looked in your inventory yet.")); return; }
                    p.sendMessage(new TextComponentString("§d✦ Inventory log (newest last):"));
                    for (String line : o.shareLog.subList(Math.max(0, o.shareLog.size() - 15), o.shareLog.size())) p.sendMessage(new TextComponentString("§7" + line));
                    return;
                default: throw new CommandException(getUsage(sender));
            }
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] a, BlockPos pos) {
            if (a.length == 1) return getListOfStringsMatchingLastWord(a, Arrays.asList("on", "off", "gifts", "log", "status"));
            if (a.length == 2 && a[0].startsWith("gift")) return getListOfStringsMatchingLastWord(a, Arrays.asList("on", "off"));
            return Collections.emptyList();
        }
    }
}
