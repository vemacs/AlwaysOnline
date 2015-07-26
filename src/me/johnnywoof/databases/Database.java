package me.johnnywoof.databases;

public interface Database {

    PlayerData getData(String username);

    void updatePlayer(String username, PlayerData data);

    void shutdown();
}
