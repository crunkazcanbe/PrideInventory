package com.dogpound.prideinventory;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** server -> client: rows unlocked, page, progress; client -> server: flip a page, buy a row */
public final class Net {
    public static final SimpleNetworkWrapper CH = NetworkRegistry.INSTANCE.newSimpleChannel("prideinventory");

    static void init() {
        CH.registerMessage(Sync.Handler.class, Sync.class, 0, Side.CLIENT);
        CH.registerMessage(Flip.Handler.class, Flip.class, 1, Side.SERVER);
        CH.registerMessage(Buy.Handler.class, Buy.class, 2, Side.SERVER);
        CH.registerMessage(GoTo.Handler.class, GoTo.class, 3, Side.SERVER);
    }

    static void sync(EntityPlayerMP p) {
        ExtraInv inv = ExtraInv.of(p);
        if (inv != null) CH.sendTo(new Sync(inv, Unlocks.nextPrice(inv), Unlocks.everything(p)), p);
    }

    public static class Sync implements IMessage {
        int unlocked, page, advancements, hours, blocksMined, mobKills, bought, granted;
        long nextPrice;
        boolean everything;

        public Sync() {}

        Sync(ExtraInv i, long nextPrice, boolean everything) {
            unlocked = i.unlocked; page = i.page; advancements = i.advancements; hours = i.hours;
            blocksMined = i.blocksMined; mobKills = i.mobKills; bought = i.bought; granted = i.granted;
            this.nextPrice = nextPrice; this.everything = everything;
        }

        @Override
        public void toBytes(ByteBuf b) {
            b.writeInt(unlocked); b.writeInt(page); b.writeInt(advancements); b.writeInt(hours); b.writeInt(blocksMined);
            b.writeInt(mobKills); b.writeInt(bought); b.writeInt(granted); b.writeLong(nextPrice); b.writeBoolean(everything);
        }

        @Override
        public void fromBytes(ByteBuf b) {
            unlocked = b.readInt(); page = b.readInt(); advancements = b.readInt(); hours = b.readInt(); blocksMined = b.readInt();
            mobKills = b.readInt(); bought = b.readInt(); granted = b.readInt(); nextPrice = b.readLong(); everything = b.readBoolean();
        }

        public static class Handler implements IMessageHandler<Sync, IMessage> {
            @Override
            public IMessage onMessage(Sync m, MessageContext ctx) {
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    ExtraInv i = ExtraInv.of(Minecraft.getMinecraft().player);
                    if (i == null) return;
                    i.unlocked = m.unlocked; i.page = m.page; i.advancements = m.advancements; i.hours = m.hours;
                    i.blocksMined = m.blocksMined; i.mobKills = m.mobKills; i.bought = m.bought; i.granted = m.granted;
                    ClientHooks.nextPrice = m.nextPrice;
                    ClientHooks.everything = m.everything;
                });
                return null;
            }
        }
    }

    public static class Flip implements IMessage {
        int dir;
        public Flip() {}
        public Flip(int dir) { this.dir = dir; }
        @Override public void toBytes(ByteBuf b) { b.writeByte(dir); }
        @Override public void fromBytes(ByteBuf b) { dir = b.readByte(); }

        public static class Handler implements IMessageHandler<Flip, IMessage> {
            @Override
            public IMessage onMessage(Flip m, MessageContext ctx) {
                EntityPlayerMP p = ctx.getServerHandler().player;
                p.getServerWorld().addScheduledTask(() -> Pages.flip(p, m.dir));
                return null;
            }
        }
    }

    public static class Buy implements IMessage {
        public Buy() {}
        @Override public void toBytes(ByteBuf b) { }
        @Override public void fromBytes(ByteBuf b) { }

        public static class Handler implements IMessageHandler<Buy, IMessage> {
            @Override
            public IMessage onMessage(Buy m, MessageContext ctx) {
                EntityPlayerMP p = ctx.getServerHandler().player;
                p.getServerWorld().addScheduledTask(() -> PrideInventory.buyRow(p));
                return null;
            }
        }
    }

    /** wide hotbar: bring hotbar page N into the real hotbar */
    public static class GoTo implements IMessage {
        int page;
        public GoTo() {}
        public GoTo(int page) { this.page = page; }
        @Override public void toBytes(ByteBuf b) { b.writeByte(page); }
        @Override public void fromBytes(ByteBuf b) { page = b.readByte(); }

        public static class Handler implements IMessageHandler<GoTo, IMessage> {
            @Override
            public IMessage onMessage(GoTo m, MessageContext ctx) {
                EntityPlayerMP p = ctx.getServerHandler().player;
                p.getServerWorld().addScheduledTask(() -> Pages.goTo(p, m.page));
                return null;
            }
        }
    }
}
