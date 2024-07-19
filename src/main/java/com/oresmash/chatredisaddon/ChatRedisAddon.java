package com.oresmash.chatredisaddon;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public final class ChatRedisAddon extends JavaPlugin implements Listener {
    private Jedis jedis;
    private Jedis subscriberJedis;
    private String redisChannel;
    private String serverId;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String redisHost = getConfig().getString("redis.host");
        int redisPort = getConfig().getInt("redis.port");
        redisChannel = getConfig().getString("redis.channel");
        serverId = getConfig().getString("server.id");  // Get the unique server identifier

        // Initialize Redis
        jedis = new Jedis(redisHost, redisPort);
        subscriberJedis = new Jedis(redisHost, redisPort);

        // Register chat listener
        getServer().getPluginManager().registerEvents(this, this);

        // Subscribe to Redis channel
        new Thread(() -> {
            subscriberJedis.subscribe(new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    if (channel.equals(redisChannel)) {
                        // Split the message to get the server ID and the actual message
                        int separatorIndex = message.indexOf(':');
                        if (separatorIndex == -1) return;  // Invalid message format

                        String receivedServerId = message.substring(0, separatorIndex);
                        String serializedComponent = message.substring(separatorIndex + 1);

                        // Skip the message if it was sent by this server
                        if (!receivedServerId.equals(serverId)) {
                            Component component = miniMessage.deserialize(serializedComponent);
                            getServer().getScheduler().runTask(ChatRedisAddon.this, () -> {
                                getServer().broadcast(component);
                            });
                        }
                    }
                }
            }, redisChannel);
        }).start();
    }

    @Override
    public void onDisable() {
        if (jedis != null) {
            jedis.close();
        }
        if (subscriberJedis != null) {
            subscriberJedis.close();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncChatEvent event) {
        // Render the chat message
        ChatRenderer renderer = event.renderer();
        Component renderedMessage = renderer.render(event.getPlayer(), event.getPlayer().displayName(), event.message(), getServer().getConsoleSender());

        // Serialize the chat message
        String serializedMessage = miniMessage.serialize(renderedMessage);

        // Append the server ID to the message
        String messageWithId = serverId + ":" + serializedMessage;

        // Publish the chat message to Redis
        jedis.publish(redisChannel, messageWithId);
    }
}
