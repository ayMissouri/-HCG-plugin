package dev.amissouri.hcg;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** /hat, moves the held item into the helmet slot. */
public final class HatCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            Messages.send(sender, "general.only-players");
            return true;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack held = inventory.getItemInMainHand();
        if (held.getType() == Material.AIR) {
            Messages.send(sender, "commands.hat.hold-item");
            return true;
        }
        ItemStack oldHelmet = inventory.getHelmet();
        inventory.setHelmet(held);
        inventory.setItemInMainHand(oldHelmet);
        return true;
    }
}
