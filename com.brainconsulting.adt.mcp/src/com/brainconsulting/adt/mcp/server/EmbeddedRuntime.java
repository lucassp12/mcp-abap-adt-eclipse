package com.brainconsulting.adt.mcp.server;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

import com.brainconsulting.adt.mcp.Activator;

/** Localiza o runtime embutido no fragmento instalado para esta plataforma (hoje: Windows x64). */
public final class EmbeddedRuntime {

    public static final String FRAGMENT_ID = "com.brainconsulting.adt.mcp.runtime.win32.x86_64";

    private EmbeddedRuntime() {
    }

    /** @return o runtime embutido, ou null se esta instalação não tiver um (outra plataforma, build sem runtime). */
    public static RuntimeInfo find() {
        Bundle fragment = Platform.getBundle(FRAGMENT_ID);
        if (fragment == null) {
            return null;
        }
        try {
            File root = FileLocator.getBundleFile(fragment);
            if (root == null || !root.isDirectory()) {
                return null; // o fragmento precisa estar descompactado (Eclipse-BundleShape: dir)
            }
            return RuntimeInfo.load(root.toPath().resolve("runtime"));
        } catch (IOException e) {
            Activator.log("Não foi possível localizar o servidor embutido", e);
            return null;
        }
    }
}
