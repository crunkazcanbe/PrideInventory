package com.dogpound.prideinventory;

import com.dogpound.realmcoin.bank.Account;
import com.dogpound.realmcoin.bank.Ledger;
import net.minecraft.entity.player.EntityPlayerMP;

/** Only loaded when RealmCoin is installed. Pays the server treasury-side "PrideInventory" business, by RealmCoin's rules. */
final class RealmCoinPay {
    private static final String SHOP = "b:prideinventory";

    private RealmCoinPay() {}

    /** null = paid, else why not */
    static String charge(EntityPlayerMP p, long cents, String what) {
        Ledger l = Ledger.get(p.getServer().getWorld(0));
        l.personal(p.getUniqueID(), p.getName(), 0);
        if (l.get(SHOP) == null) l.create(SHOP, Account.Kind.BUSINESS, "Inventory Rows", null);
        Ledger.Result r = l.transfer("p:" + p.getUniqueID(), SHOP, cents, "inventory-row", what);
        switch (r) {
            case OK: return null;
            case INSUFFICIENT: return "not enough coins";
            case FROZEN: return "your account is frozen";
            default: return String.valueOf(r);
        }
    }
}
