package ro.ainpc.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import ro.ainpc.AINPCPlugin;
import ro.ainpc.managers.ConversationSessionManager;
import ro.ainpc.npc.AINPC;
import ro.ainpc.utils.MessageUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public abstract class AbstractPluginListener implements Listener {

    protected final AINPCPlugin plugin;

    protected AbstractPluginListener(AINPCPlugin plugin) {
        this.plugin = plugin;
    }

    protected MessageUtils messages() {
        return plugin.getMessageUtils();
    }

    protected ConversationSessionManager conversations() {
        return plugin.getConversationSessionManager();
    }

    protected boolean beginConversationSession(Player player, AINPC npc) {
        boolean firstMeeting = !plugin.getMemoryManager().hasMemoriesOf(npc, player);
        conversations().startConversation(player, npc);
        if (firstMeeting) {
            plugin.getMemoryManager().createFirstMeetingMemory(npc, player);
        }
        return firstMeeting;
    }

    protected void refreshConversationSession(Player player) {
        conversations().touchConversation(player);
    }

    protected void runSync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    protected void runLater(Runnable task, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    protected <T> T callSync(Supplier<T> supplier) {
        if (Bukkit.isPrimaryThread()) {
            return supplier.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        runSync(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });

        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Nu s-a putut executa operatia pe main thread.", e);
        }
    }
}
