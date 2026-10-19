package com.brainconsulting.adt.mcp.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Node.js + servidor mcp-abap-adt embutidos no fragmento da plataforma (runtime/runtime.properties). */
public final class RuntimeInfo {

    public final Path node;
    public final Path entry;
    public final String serverPackage;
    public final String serverVersion;
    public final String nodeVersion;
    public final String serverLicense;

    private RuntimeInfo(Path node, Path entry, Properties p) {
        this.node = node;
        this.entry = entry;
        this.serverPackage = p.getProperty("server.package", "@mcp-abap-adt/core");
        this.serverVersion = p.getProperty("server.version", "?");
        this.nodeVersion = p.getProperty("node.version", "?");
        this.serverLicense = p.getProperty("server.license", "?");
    }

    /** @return o runtime descrito em {@code runtimeDir/runtime.properties}, ou null se estiver incompleto. */
    public static RuntimeInfo load(Path runtimeDir) {
        Path props = runtimeDir.resolve("runtime.properties");
        if (!Files.isRegularFile(props)) {
            return null;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(props)) {
            p.load(in);
        } catch (IOException e) {
            return null;
        }
        String nodeRel = p.getProperty("node");
        String entryRel = p.getProperty("server.entry");
        if (nodeRel == null || entryRel == null) {
            return null;
        }
        Path node = runtimeDir.resolve(nodeRel).normalize();
        Path entry = runtimeDir.resolve(entryRel).normalize();
        if (!Files.isRegularFile(node) || !Files.isRegularFile(entry)) {
            return null;
        }
        return new RuntimeInfo(node, entry, p);
    }

    public String describe() {
        return serverPackage + " " + serverVersion + " (Node.js " + nodeVersion + ", licença " + serverLicense + ")";
    }
}
