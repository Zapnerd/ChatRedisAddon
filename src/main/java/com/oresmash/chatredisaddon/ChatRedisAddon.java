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
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String redisHost = getConfig().getString("redis.host");
        int redisPort = getConfig().getInt("redis.port");
        redisChannel = getConfig().getString("redis.channel");

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
                        Component component = miniMessage.deserialize(message);
                        getServer().getScheduler().runTask(ChatRedisAddon.this, () -> {
                            getServer().broadcast(component);
                        });
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

        // Publish the chat message to Redis
        jedis.publish(redisChannel, serializedMessage);

        // Cancel the original chat event to prevent local broadcast
        event.setCancelled(true);
    }
}
