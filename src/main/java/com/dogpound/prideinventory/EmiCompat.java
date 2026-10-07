package com.dogpound.prideinventory;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;

/**
 * EMI's item list moves out of the extra-rows panel's way (requested feature).
 * Only EMI loads this class (it finds it by the annotation), so the mod still works without EMI.
 */
@EmiEntrypoint
public class EmiCompat implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addGenericExclusionArea((screen, out) -> {
            int[] b = ClientHooks.panelBounds(screen);
            if (b != null) out.accept(new Bounds(b[0], b[1], b[2], b[3]));
        });
    }
}
