package com.betterpets.manager;

import com.betterpets.BetterPetsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Gestiona mensajes del plugin usando messages.yml.
 * Soporta múltiples placeholders por sustitución simple.
 */
public class MessageManager {

    private final BetterPetsPlugin plugin;
    private FileConfiguration messages;

    public MessageManager(BetterPetsPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessagesConfig();
    }

    public void reload() {
        this.messages = plugin.getMessagesConfig();
    }

    /** Obtiene el texto crudo del messages.yml con color codes como &a, &b, etc. */
    public String getRaw(String key) {
        String val = messages.getString(key);
        if (val == null) return "§c[msg:" + key + "]";
        return val;
    }

    /**
     * Formatea un mensaje: aplica colores y sustituye pares de placeholders.
     * @param key   clave en messages.yml
     * @param pairs pares "placeholder", "valor" (ej: "{0}", nombre, "{1}", nivel)
     */
    public String formatRaw(String key, String... pairs) {
        String msg = getRaw(key);
        if (pairs.length % 2 == 0) {
            for (int i = 0; i < pairs.length; i += 2) {
                msg = msg.replace(pairs[i], pairs[i + 1]);
            }
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    /**
     * Envía un mensaje al jugador con el prefijo del plugin.
     */
    public void send(Player player, String key, String... pairs) {
        String prefix = ChatColor.translateAlternateColorCodes('&', getRaw("prefijo"));
        player.sendMessage(prefix + formatRaw(key, pairs));
    }

    /**
     * Envía un mensaje sin prefijo.
     */
    public void sendRaw(Player player, String key, String... pairs) {
        player.sendMessage(formatRaw(key, pairs));
    }
}
