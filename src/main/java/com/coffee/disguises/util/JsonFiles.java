package com.coffee.disguises.util;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Crash-safe JSON writes.
 *
 * {@link Files#newBufferedWriter} truncates the target the moment it opens it, so a
 * crash, power loss, or full disk partway through serialisation leaves a truncated
 * file behind — and for {@code disguises-saved.json} that means every player's
 * presets are gone.  The window is not hypothetical: {@code persistAll} runs during
 * SERVER_STOPPING, which is exactly when a hard kill is most likely.
 *
 * Writing to a sibling temp file and moving it into place means the destination is
 * only ever replaced by a complete document.  This guarantees the file is never
 * torn; it does not fsync, so an OS-level crash can still lose the most recent
 * write — the previous complete version survives, which is the property that
 * matters here.
 */
public final class JsonFiles {

    private JsonFiles() {}

    /** Serialises {@code value} to {@code path}, replacing it only once fully written. */
    public static void write(Path path, Gson gson, Object value) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);

        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp)) {
            gson.toJson(value, w);
        }

        try {
            Files.move(tmp, path,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Some filesystems (a few network mounts) cannot do this atomically.
            // A plain replace is still better than truncating the original in place.
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
