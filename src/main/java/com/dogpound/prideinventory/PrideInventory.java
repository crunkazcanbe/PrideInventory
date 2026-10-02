package com.dogpound.prideinventory;

import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** PrideInventory — extra rows you unlock by playing, hotbar pages, and a hook quests can call. */
@Mod(modid = "prideinventory", name = "PrideInventory", version = "0.1.0")
public class PrideInventory {

    @Mod.EventHandler
    public void pre(FMLPreInitializationEvent e) {
        Config.load(e.getSuggestedConfigurationFile());
        ExtraInv.register();
        Net.init();
        MinecraftForge.EVENT_BUS.register(this);
        if (FMLCommonHandler.instance().getSide().isClient()) ClientHooks.register();
    }

    @Mod.EventHandler
    public void serverStart(FMLServerStartingEvent e) {
        e.registerServerCommand(new Command());
    }

    // ---------------- the API other mods (the quest mod) call ----------------
    /** give a player extra rows for good (quest rewards, events...). Returns their new total. */
    public static int grantRows(EntityPlayer player, int rows, String why) {
        ExtraInv inv = ExtraInv.of(player);
        if (inv == null || !(player instanceof EntityPlayerMP)) return 0;
        inv.granted = Math.max(0, inv.granted + rows);
        if (why != null && rows > 0) player.sendMessage(new TextComponentString("§d✦ " + why + ": +" + rows + " inventory row" + (rows == 1 ? "" : "s")));
        Unlocks.update((EntityPlayerMP) player);
        return inv.unlocked;
    }

    // ---------------- player lifecycle ----------------
    @SubscribeEvent
    public void attach(AttachCapabilitiesEvent<Entity> e) {
        if (e.getObject() instanceof EntityPlayer) e.addCapability(ExtraInv.KEY, new ExtraInv.Provider());
    }

    @SubscribeEvent
    public void login(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP) { Unlocks.update((EntityPlayerMP) e.player); SelfTest.run((EntityPlayerMP) e.player); }
    }

    @SubscribeEvent
    public void respawn(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent e) {
        if (e.player instanceof EntityPlayerMP) Unlocks.update((EntityPlayerMP) e.player);
    }

    @SubscribeEvent
    public void changedDim(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.player instanceof EntityPlayerMP) Net.sync((EntityPlayerMP) e.player);
    }

    /** the new player object after death / leaving the End gets the old rows (items only if they were kept) */
    @SubscribeEvent
    public void clone(net.minecraftforge.event.entity.player.PlayerEvent.Clone e) {
        ExtraInv from = ExtraInv.of(e.getOriginal()), to = ExtraInv.of(e.getEntityPlayer());
        if (from == null || to == null) return;
        to.bought = from.bought; to.granted = from.granted; to.unlocked = from.unlocked;
        boolean keep = !e.isWasDeath() || Config.keepOnDeath || e.getEntityPlayer().world.getGameRules().getBoolean("keepInventory");
        if (keep) {
            for (int i = 0; i < from.items.getSlots(); i++) to.items.setStackInSlot(i, from.items.getStackInSlot(i).copy());
            to.page = from.page;
        }
    }

    /** dying drops the extra rows like the rest of the inventory (unless kept by config / keepInventory) */
    @SubscribeEvent
    public void drops(PlayerDropsEvent e) {
        EntityPlayer p = e.getEntityPlayer();
        ExtraInv inv = ExtraInv.of(p);
        if (inv == null || Config.keepOnDeath || p.world.getGameRules().getBoolean("keepInventory")) return;
        for (int i = 0; i < inv.items.getSlots(); i++) {
            ItemStack s = inv.items.getStackInSlot(i);
            if (s.isEmpty()) continue;
            EntityItem drop = new EntityItem(p.world, p.posX, p.posY + p.getEyeHeight() - 0.3, p.posZ, s.copy());
            drop.setPickupDelay(40);
            e.getDrops().add(drop);
            inv.items.setStackInSlot(i, ItemStack.EMPTY);
        }
        inv.page = 0; // the page rotation is gone with the items
    }

    /** a new advancement can be the one that earns a row: count right away, not at the next 30 s recount */
    @SubscribeEvent
    public void advancement(net.minecraftforge.event.entity.player.AdvancementEvent e) {
        if (e.getEntityPlayer() instanceof EntityPlayerMP && e.getAdvancement().getDisplay() != null) Unlocks.update((EntityPlayerMP) e.getEntityPlayer());
    }

    private int tick;

    /** recount unlocks every 30 s, and right away when someone changes game mode */
    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer s = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (s == null) return;
        boolean full = ++tick % 600 == 0;
        for (EntityPlayerMP p : s.getPlayerList().getPlayers()) {
            ExtraInv inv = ExtraInv.of(p);
            if (inv == null) continue;
            if (full || (tick % 20 == 0 && Unlocks.everything(p) != inv.wasEverything)) Unlocks.update(p); // game mode changed either way
        }
    }

    // ---------------- buying a row with RealmCoin ----------------
    static void buyRow(EntityPlayerMP p) {
        ExtraInv inv = ExtraInv.of(p);
        if (inv == null) return;
        if (!Config.buyWithCoins || !Loader.isModLoaded("realmcoin")) { p.sendMessage(new TextComponentString("§cRows can't be bought on this server.")); return; }
        if (inv.unlocked >= Config.maxRows) { p.sendMessage(new TextComponentString("§eYou already have every row.")); return; }
        long price = Unlocks.nextPrice(inv);
        String err = RealmCoinPay.charge(p, price * 100, "PrideInventory row #" + (inv.bought + 1));
        if (err != null) { p.sendMessage(new TextComponentString("§cCouldn't buy a row: " + err + " (costs " + price + " coins)")); return; }
        inv.bought++;
        p.sendMessage(new TextComponentString("§d✦ Bought an inventory row for " + price + " coins!"));
        Unlocks.update(p);
    }

    // ---------------- /prideinv ----------------
    static final class Command extends CommandBase {
        @Override public String getName() { return "prideinv"; }
        @Override public String getUsage(ICommandSender s) { return "/prideinv info | buy | page <next|back> | grant <player> <rows> | recount [player]"; }
        @Override public int getRequiredPermissionLevel() { return 0; }
        @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }
        @Override public List<String> getAliases() { return Collections.singletonList("pinv"); }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] a) throws CommandException {
            String sub = a.length == 0 ? "info" : a[0];
            boolean admin = sender.canUseCommand(2, "prideinv");
            switch (sub) {
                case "info": {
                    EntityPlayerMP p = getCommandSenderAsPlayer(sender);
                    ExtraInv inv = ExtraInv.of(p);
                    Unlocks.update(p);
                    p.sendMessage(new TextComponentString("§d✦ PrideInventory: §f" + inv.unlocked + "/" + Config.maxRows + " extra rows"
                        + (Unlocks.everything(p) ? " §a(all unlocked)" : "") + "§7 | advancements " + inv.advancements + ", " + inv.hours + " h played, "
                        + inv.blocksMined + " mined, " + inv.mobKills + " kills, bought " + inv.bought + ", rewards " + inv.granted));
                    return;
                }
                case "buy": buyRow(getCommandSenderAsPlayer(sender)); return;
                case "page": Pages.flip(getCommandSenderAsPlayer(sender), a.length > 1 && a[1].startsWith("b") ? -1 : 1); return;
                case "grant": {
                    if (!admin || a.length < 3) throw new CommandException(admin ? getUsage(sender) : "Only admins can grant rows.");
                    EntityPlayerMP p = getPlayer(server, sender, a[1]);
                    int total = grantRows(p, parseInt(a[2], -Config.MAX_ROWS_EVER, Config.MAX_ROWS_EVER), "Given by an admin");
                    sender.sendMessage(new TextComponentString(p.getName() + " now has " + total + " extra rows."));
                    return;
                }
                case "recount": {
                    EntityPlayerMP p = a.length > 1 && admin ? getPlayer(server, sender, a[1]) : getCommandSenderAsPlayer(sender);
                    Unlocks.update(p);
                    sender.sendMessage(new TextComponentString(p.getName() + ": " + ExtraInv.of(p).unlocked + " extra rows."));
                    return;
                }
                default: throw new CommandException(getUsage(sender));
            }
        }
    }
}
