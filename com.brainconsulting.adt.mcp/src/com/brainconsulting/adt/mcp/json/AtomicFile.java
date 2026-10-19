package com.brainconsulting.adt.mcp.json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Grava um arquivo de forma atômica: escreve num temporário na mesma pasta e depois
 * substitui o original. Assim, uma falha no meio da gravação nunca deixa o arquivo
 * (por exemplo, o ~/.claude.json) pela metade.
 */
public final class AtomicFile {

    private AtomicFile() {
    }

    public static void write(Path file, String content) throws IOException {
        Path target = file.toAbsolutePath();
        Path dir = target.getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        Path tmp = Files.createTempFile(dir, target.getFileName().toString() + ".", ".tmp");
        try {
            Files.write(tmp, content.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
