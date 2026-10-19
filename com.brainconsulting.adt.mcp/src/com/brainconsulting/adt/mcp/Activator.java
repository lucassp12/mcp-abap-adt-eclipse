package com.brainconsulting.adt.mcp;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.brainconsulting.adt.mcp.model.EnvironmentStore;
import com.brainconsulting.adt.mcp.server.ServerManager;

public class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "com.brainconsulting.adt.mcp";

    private static Activator plugin;

    private EnvironmentStore store;
    private ServerManager serverManager;
    private Thread shutdownHook;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        store = new EnvironmentStore();
        store.load();
        serverManager = new ServerManager(store);
        ServerManager.cleanRunDir();
        // Garantia extra: se a JVM do Eclipse terminar de forma abrupta, não deixa node.exe órfão.
        shutdownHook = new Thread(serverManager::stopAllQuietly, "mcp-abap-adt-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        try {
            if (serverManager != null) {
                serverManager.stopAllQuietly();
            }
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException e) {
                    // JVM já está encerrando
                }
            }
        } finally {
            plugin = null;
            super.stop(context);
        }
    }

    public static Activator getDefault() {
        return plugin;
    }

    public EnvironmentStore getStore() {
        return store;
    }

    public ServerManager getServerManager() {
        return serverManager;
    }

    public static void log(String message, Throwable t) {
        Activator p = plugin;
        if (p != null) {
            p.getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, message, t));
        }
    }
}
