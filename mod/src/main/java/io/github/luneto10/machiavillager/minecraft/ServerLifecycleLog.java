package io.github.luneto10.machiavillager.minecraft;

import io.github.luneto10.machiavillager.Machiavillager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class ServerLifecycleLog {
    public ServerLifecycleLog() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(sv ->
                Machiavillager.LOGGER.info("Server is UP!!! WAITING FOR YOU SOLDIER"));

        ServerLifecycleEvents.SERVER_STOPPED.register(sv ->
                Machiavillager.LOGGER.info("Server is down!!! go sleep."));
    }
}
