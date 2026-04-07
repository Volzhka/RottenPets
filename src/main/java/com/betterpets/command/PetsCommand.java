package com.betterpets.command;

import com.betterpets.BetterPetsPlugin;
import com.betterpets.data.PetData;
import com.betterpets.data.PetType;
import com.betterpets.data.PlayerData;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manejador del comando /mascotas (y alias /pets, /mascota).
 *
 * Subcomandos:
 *   /mascotas                   → Abre el menú
 *   /mascotas lista             → Lista las mascotas del jugador
 *   /mascotas info <tipo>       → Muestra info de un tipo de mascota
 *   /mascotas dar <jugador> <tipo> → (Admin) Da una mascota
 *   /mascotas recargar          → (Admin) Recarga la configuración
 */
public class PetsCommand implements CommandExecutor, TabCompleter {

    private final BetterPetsPlugin plugin;

    public PetsCommand(BetterPetsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            // Abrir menú
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cEste comando solo puede usarlo un jugador.");
                return true;
            }
            if (!player.hasPermission("betterpets.menu")) {
                plugin.getMsgManager().send(player, "general.sin-permiso");
                return true;
            }
            plugin.openPetMenu(player, 0);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "lista", "list" -> cmdLista(sender);
            case "info"          -> cmdInfo(sender, args);
            case "dar", "give"   -> cmdDar(sender, args);
            case "recargar", "reload" -> cmdRecargar(sender);
            case "ayuda", "help" -> cmdAyuda(sender);
            default -> cmdAyuda(sender);
        }

        return true;
    }

    // --- /mascotas lista ---
    private void cmdLista(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores pueden usar este comando.");
            return;
        }
        PlayerData data = plugin.getPetManager().getPlayerData(player.getUniqueId());
        player.sendMessage(plugin.getMsgManager().formatRaw("comando.lista-titulo",
            "{0}", String.valueOf(data.getPetCount())));

        if (data.getPetCount() == 0) {
            player.sendMessage(plugin.getMsgManager().formatRaw("comando.lista-vacia"));
            return;
        }

        long base = plugin.getConfig().getLong("exp.base", 50);
        long mult = plugin.getConfig().getLong("exp.multiplicador", 50);

        for (int i = 0; i < data.getPetCount(); i++) {
            PetData pet = data.getPet(i);
            String nombre = plugin.getPetManager().getPetDisplayName(pet.getType());
            player.sendMessage(plugin.getMsgManager().formatRaw("comando.lista-entrada",
                "{0}", nombre,
                "{1}", String.valueOf(pet.getLevel()),
                "{2}", String.valueOf(pet.getExp()),
                "{3}", String.valueOf(pet.getExpRequired(base, mult))));
        }
    }

    // --- /mascotas info <tipo> ---
    private void cmdInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUso: /mascotas info <tipo>");
            return;
        }
        PetType type = PetType.fromKey(args[1].toLowerCase());
        if (type == null) {
            if (sender instanceof Player p)
                plugin.getMsgManager().send(p, "mascota.tipo-invalido", "{0}", args[1]);
            else sender.sendMessage("§cTipo de mascota inválido: " + args[1]);
            return;
        }

        String nombre = plugin.getPetManager().getPetDisplayName(type);
        sender.sendMessage(plugin.getMsgManager().formatRaw("comando.info-titulo", "{0}", nombre));

        String habilidad = plugin.getPetsConfig().getString(
            "mascotas." + type.getKey() + ".habilidad", "");
        sender.sendMessage(plugin.getMsgManager().formatRaw("comando.info-habilidad", "{0}", habilidad));

        List<String> desc = plugin.getPetsConfig().getStringList(
            "mascotas." + type.getKey() + ".descripcion");
        for (String line : desc) {
            sender.sendMessage(plugin.getMsgManager().formatRaw("comando.info-descripcion", "{0}", line));
        }

        List<String> hDesc = plugin.getPetsConfig().getStringList(
            "mascotas." + type.getKey() + ".descripcion-habilidad");
        for (String line : hDesc) {
            sender.sendMessage("§7  " + line);
        }
    }

    // --- /mascotas dar <jugador> <tipo> ---
    private void cmdDar(CommandSender sender, String[] args) {
        if (!sender.hasPermission("betterpets.give")) {
            if (sender instanceof Player p)
                plugin.getMsgManager().send(p, "general.sin-permiso");
            else sender.sendMessage("§cSin permiso.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUso: /mascotas dar <jugador> <tipo>");
            return;
        }

        org.bukkit.entity.Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            if (sender instanceof Player p)
                plugin.getMsgManager().send(p, "general.jugador-no-encontrado", "{0}", args[1]);
            else sender.sendMessage("§cJugador no encontrado: " + args[1]);
            return;
        }

        PetType type = PetType.fromKey(args[2].toLowerCase());
        if (type == null) {
            if (sender instanceof Player p)
                plugin.getMsgManager().send(p, "mascota.tipo-invalido", "{0}", args[2]);
            else sender.sendMessage("§cTipo inválido: " + args[2]);
            return;
        }

        boolean success = plugin.getPetManager().givePet(target, type);
        if (success) {
            String nombre = plugin.getPetManager().getPetDisplayName(type);
            if (sender instanceof Player p) {
                plugin.getMsgManager().send(p, "comando.dar-exito", "{0}", nombre, "{1}", target.getName());
            } else {
                sender.sendMessage("§aMascota " + nombre + " dada a " + target.getName() + ".");
            }
            plugin.getMsgManager().send(target, "comando.dar-recibido",
                "{0}", sender.getName(), "{1}", nombre);
        }
    }

    // --- /mascotas recargar ---
    private void cmdRecargar(CommandSender sender) {
        if (!sender.hasPermission("betterpets.reload")) {
            if (sender instanceof Player p)
                plugin.getMsgManager().send(p, "general.sin-permiso");
            else sender.sendMessage("§cSin permiso.");
            return;
        }
        if (sender instanceof Player p)
            plugin.getMsgManager().send(p, "general.recargando");
        else sender.sendMessage("§eRecargando...");

        plugin.reloadPluginConfig();

        if (sender instanceof Player p)
            plugin.getMsgManager().send(p, "general.recargado");
        else sender.sendMessage("§aConfiguración recargada.");
    }

    // --- /mascotas ayuda ---
    private void cmdAyuda(CommandSender sender) {
        String ayuda = plugin.getMsgManager().getRaw("comando.ayuda");
        for (String line : ayuda.split("\n")) {
            sender.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("lista", "info", "dar", "recargar", "ayuda")
                .stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            return Arrays.stream(PetType.values())
                .map(PetType::getKey)
                .filter(k -> k.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("dar") || args[0].equalsIgnoreCase("give"))) {
            return plugin.getServer().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("dar") || args[0].equalsIgnoreCase("give"))) {
            return Arrays.stream(PetType.values())
                .map(PetType::getKey)
                .filter(k -> k.startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
