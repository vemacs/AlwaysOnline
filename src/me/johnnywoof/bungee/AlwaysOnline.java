package me.johnnywoof.bungee;

import com.google.common.io.ByteStreams;
import me.johnnywoof.databases.Database;
import me.johnnywoof.databases.MySQLDatabase;
import me.johnnywoof.utils.Utils;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.*;
import java.sql.SQLException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class AlwaysOnline extends Plugin {
    public boolean disabled = false;
    public Database db = null;

    private boolean prevOnline = true;
    private boolean currOnline = true;
    public static boolean mojangOnline = true;

    @Override
    public void onEnable() {
        getProxy().getPluginManager().registerCommand(this, new AOCommand(this));
        reload();
    }

    public void reload() {
        if (db != null) {//Close existing open database connections on reload
            db.shutdown();
        }

        try {
            Configuration yml = ConfigurationProvider.getProvider(YamlConfiguration.class).
                    load(loadResource(this, "config.yml"));
            if (yml.getInt("config_version", 0) < 4) {

                this.getLogger().warning("*-*-*-*-*-*-*-*-*-*-*-*-*-*");
                this.getLogger().warning("Your configuration file is out of date!");
                this.getLogger().warning("Please consider deleting it for a fresh new generated copy!");
                this.getLogger().warning("Once done, do /alwaysonline reload");
                this.getLogger().warning("*-*-*-*-*-*-*-*-*-*-*-*-*-*");
                return;

            }
            int ct = yml.getInt("check-interval", 20);

            try {

                this.db = new MySQLDatabase(yml.getString("host"),
                        yml.getInt("port"),
                        yml.getString("database-name"),
                        yml.getString("database-username"),
                        yml.getString("database-password"));
            } catch (SQLException e) {
                this.getLogger().info("DB load fail");
                return;
            }

            this.getLogger().info("Database is ready to go!");

            if (ct == -1) {
                this.getLogger().severe("Negative number!");
                return;
            }

            //Kill existing runnables and listeners (in case of reload)
            this.getProxy().getScheduler().cancel(this);
            this.getProxy().getPluginManager().unregisterListeners(this);

            //Read the state.txt file and assign variables
            File stateFile = new File(this.getDataFolder(), "state.txt");
            if (stateFile.exists()) {
                Scanner scan = new Scanner(stateFile);
                String data = scan.nextLine();
                scan.close();
                if (data != null && data.contains(":")) {
                    String[] d = data.split(Pattern.quote(":"));
                    disabled = Boolean.parseBoolean(d[0]);
                    AlwaysOnline.mojangOnline = Boolean.parseBoolean(d[1]);
                    this.getLogger().info("Successfully loaded previous state variables!");
                }
            }

            //Register our new listener and runnable
            this.getProxy().getPluginManager().registerListener(this, new AOListener(this,
                    yml.getString("message-kick-invalid"),
                    yml.getString("message-kick-ip"),
                    yml.getString("message-kick-new"),
                    yml.getString("message-motd-offline", null)));

            this.getProxy().getScheduler().schedule(this, new Runnable() {
                @SuppressWarnings("deprecation")
                @Override
                public void run() {
                    if (!disabled) {
                        prevOnline = currOnline;
                        currOnline = Utils.isSessionServerOnline();
                        if (prevOnline && currOnline) {
                            if (!mojangOnline) {
                                mojangOnline = true;
                                getLogger().info("Mojang session servers are online (2x consecutive)!");
                            }
                        } else {
                            if (mojangOnline) {
                                mojangOnline = false;
                                getLogger().info("Mojang session servers are offline!");
                            }
                        }
                    }
                }
            }, 0, ct, TimeUnit.SECONDS);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {
        if (this.db != null) {
            db.shutdown();
        }
    }

    public static File loadResource(Plugin plugin, String resource) {
        File folder = plugin.getDataFolder();
        if (!folder.exists())
            folder.mkdir();
        File resourceFile = new File(folder, resource);
        try {
            if (!resourceFile.exists()) {
                resourceFile.createNewFile();
                try (InputStream in = plugin.getResourceAsStream(resource);
                     OutputStream out = new FileOutputStream(resourceFile)) {
                    ByteStreams.copy(in, out);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resourceFile;
    }
}
