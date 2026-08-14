package com.johncorby.gravityGuild2;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.battleplugins.arena.competition.map.LiveCompetitionMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

// https://github.com/BattlePlugins/BattleArena/blob/master/module/arena-restoration/src/main/java/org/battleplugins/arena/module/restoration/ArenaRestorationUtil.java
class ArenaRestorationUtil {

    public static void restoreArena(LiveCompetitionMap map) {
        var arena = map.getArena();
        var bounds = map.getBounds();

        Path path = getSchematicPath(map);
        if (Files.notExists(path)) {
            // No schematic found
            map.getArena().getPlugin().warn("Could not restore map {} for arena {} as no schematic was found!", map.getName(), map.getArena().getName());
            return;
        }

        // Restore the arena
        Clipboard clipboard;
        ClipboardFormat format = ClipboardFormats.findByFile(path.toFile());
        if (format == null) {
            // Invalid format
            arena.getPlugin().warn("Could not restore map {} for arena {} as the schematic format is invalid!", map.getName(), arena.getName());
            return;
        }

        try (ClipboardReader reader = format.getReader(Files.newInputStream(path))) {
            clipboard = reader.read();
        } catch (IOException e) {
            // Error reading schematic
            arena.getPlugin().error("Failed to restore map {} for arena {} due to an error reading the schematic!", map.getName(), arena.getName(), e);
            return;
        }

        try (EditSession session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(map.getWorld()))) {
            Operation operation = new ClipboardHolder(clipboard).createPaste(session)
                    .to(BlockVector3.at(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ()))
                    .build();

            Operations.complete(operation);
        } catch (WorldEditException e) {
            // Error restoring schematic
            arena.getPlugin().error("Failed to restore map {} for arena {} due to an error restoring the schematic!", map.getName(), arena.getName(), e);
        }
    }


    public static Path getSchematicPath(LiveCompetitionMap map) {
        var arena = map.getArena();

        return arena.getPlugin().getDataFolder().toPath()
                .resolve("schematics")
                .resolve(arena.getName().toLowerCase(Locale.ROOT))
                .resolve(map.getName().toLowerCase(Locale.ROOT) + "." +
                        BuiltInClipboardFormat.SPONGE_SCHEMATIC.getPrimaryFileExtension()
                );
    }

}