package me.johnnywoof.bungee;

import me.johnnywoof.databases.PlayerData;
import net.md_5.bungee.UserConnection;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.connection.InitialHandler;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class AOListener implements Listener {

    private final Pattern pat = Pattern.compile("^[a-zA-Z0-9_-]{2,16}$");//The regex to verify usernames;

    private final String kick_invalid_name;
    private final String kick_not_same_ip;
    private final String kick_new_player;
    private final String motdOffline;

    private final AlwaysOnline ao;
    
    private final Map<String, PlayerData> dataCache = new ConcurrentHashMap<>();

    public AOListener(AlwaysOnline ao, String invalid, String kick_ip, String kick_new, String motdOffline) {
        this.ao = ao;
        if ("null".equals(motdOffline) || motdOffline == null) {
            this.motdOffline = null;
        } else {
            this.motdOffline = ChatColor.translateAlternateColorCodes('&', motdOffline);
        }

        kick_invalid_name = ChatColor.translateAlternateColorCodes('&', invalid);
        kick_not_same_ip = ChatColor.translateAlternateColorCodes('&', kick_ip);
        kick_new_player = ChatColor.translateAlternateColorCodes('&', kick_new);
    }

    //A high priority to allow other plugins to go first
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(PreLoginEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (!AlwaysOnline.mojangOnline) {
            if (event.getConnection().getName().length() > 16) {
                event.setCancelReason(kick_invalid_name);
                event.setCancelled(true);
                return;
            } else if (!validate(event.getConnection().getName())) {
                event.setCancelReason(kick_invalid_name);
                event.setCancelled(true);
                return;
            }

            InitialHandler handler = (InitialHandler) event.getConnection();
            final String ip = handler.getAddress().getAddress().getHostAddress();
            String username = event.getConnection().getName();
            PlayerData data = ao.db.getData(event.getConnection().getName());
            String lastIp = null;
            if (data != null) lastIp = data.ipAddress;

            if (lastIp == null) {
                event.setCancelReason(kick_new_player);
                event.setCancelled(true);
                ao.getLogger().info("Denied " + event.getConnection().getName() + " from logging in because their ip [" + ip + "] has never connected to this server before!");
            } else {
                if (ip.equals(lastIp)) {
                    ao.getLogger().info("Skipping session login for player " + event.getConnection().getName() + " [Connected ip: " + ip + ", Last ip: " + lastIp + "]!");
                    handler.setOnlineMode(false);
                    dataCache.put(username, data);
                } else {
                    ao.getLogger().info("Denied " + event.getConnection().getName() + " from logging in because their ip [" + ip + "] does not match their last ip!");
                    handler.setOnlineMode(true);
                    event.setCancelReason(kick_not_same_ip);
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPing(ProxyPingEvent event) {
        if (!AlwaysOnline.mojangOnline && motdOffline != null) {
            ServerPing sp = event.getResponse();
            String s = motdOffline;
            s = s.replaceAll(".newline.", "\n");
            sp.setDescription(s);
            event.setResponse(sp);
        }
    }

    private Field uniqueId;
    private Field offlineId;

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPost(final PostLoginEvent event) {
        if (!AlwaysOnline.mojangOnline) {
            InitialHandler handler = (InitialHandler) event.getPlayer().getPendingConnection();
            try {
                String username = event.getPlayer().getName();
                UUID uuid;
                if (dataCache.containsKey(username)) {
                    uuid = dataCache.get(username).uuid;
                    dataCache.remove(username);
                } else {
                    return;
                }
                //Reflection
                if (uniqueId == null) {
                    uniqueId = handler.getClass().getDeclaredField("uniqueId");
                    uniqueId.setAccessible(true);
                }
                uniqueId.set(handler, uuid);

                if (offlineId == null) {
                    offlineId = handler.getClass().getDeclaredField("offlineId");
                    offlineId.setAccessible(true);
                }
                offlineId.set(handler, uuid);

                Collection<String> g = ao.getProxy().getConfigurationAdapter().getGroups(event.getPlayer().getName());
                g.addAll(ao.getProxy().getConfigurationAdapter().getGroups(event.getPlayer().getUniqueId().toString()));

                UserConnection userConnection = (UserConnection) event.getPlayer();
                for (String s : g) {
                    userConnection.addGroups(s);
                }

                ao.getLogger().info(event.getPlayer().getName() + " successfully logged in while Mojang servers were offline!");

            } catch (Exception e) {//Play it safe, if an error deny the player
                event.getPlayer().disconnect(kick_new_player);

                ao.getLogger().warning("Internal error for " + event.getPlayer().getName() + ", preventing login.");

                e.printStackTrace();
            }
        } else {
            //If we are not in mojang offline mode, update the player data
            final String username = event.getPlayer().getName();
            final PlayerData data = new PlayerData(event.getPlayer().getAddress().getAddress().getHostAddress(), event.getPlayer().getUniqueId());
            ProxyServer.getInstance().getScheduler().runAsync(ao, new Runnable() {
                @Override
                public void run() {
                    ao.db.updatePlayer(username, data);
                }
            });
        }
    }

    /**
     * Validate username with regular expression
     *
     * @param username username for validation
     * @return true valid username, false invalid username
     */
    public boolean validate(String username) {
        return username != null && pat.matcher(username).matches();
    }
}
