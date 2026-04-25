package ro.ainpc.addons;

import ro.ainpc.AINPCPlugin;

public interface AINPCAddon {

    AddonDescriptor getDescriptor();

    default void onLoad(AINPCPlugin plugin) {
    }

    default void onEnable(AINPCPlugin plugin) {
    }

    default void onDisable(AINPCPlugin plugin) {
    }
}
