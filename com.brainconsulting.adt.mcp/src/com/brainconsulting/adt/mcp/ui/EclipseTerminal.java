package com.brainconsulting.adt.mcp.ui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

import com.brainconsulting.adt.mcp.Activator;

/**
 * Abre um processo na view Terminal do Eclipse, sem dependência de compilação.
 * <p>
 * O Terminal do Eclipse mudou de nome: até 2025 era o TM Terminal ({@code org.eclipse.tm.terminal.*}),
 * depois passou para a plataforma como {@code org.eclipse.terminal.*}. Por isso tudo é feito por
 * reflexão: se o Terminal não estiver instalado (ou a API mudar), o método retorna false e a view
 * usa a janela externa.
 */
final class EclipseTerminal {

    /** Prefixos de pacote/bundle das duas gerações do Terminal, do mais novo para o mais antigo. */
    private static final String[] BASES = { "org.eclipse.terminal", "org.eclipse.tm.terminal" };

    private EclipseTerminal() {
    }

    /** @return true se o terminal foi aberto. */
    static boolean open(String title, Path workDir, String program, String args) {
        for (String base : BASES) {
            try {
                if (tryOpen(base, title, workDir, program, args)) {
                    return true;
                }
            } catch (Throwable t) {
                Activator.log("Terminal do Eclipse (" + base + ") indisponível; usando janela externa", t);
            }
        }
        return false;
    }

    private static boolean tryOpen(String base, String title, Path workDir, String program, String args)
            throws Exception {
        Bundle core = Platform.getBundle(base + ".view.core");
        if (core == null) {
            return false;
        }
        Class<?> factory = core.loadClass(base + ".view.core.TerminalServiceFactory");
        Object service = factory.getMethod("getService").invoke(null);
        if (service == null) {
            return false;
        }
        Class<?> constants = core.loadClass(base + ".view.core.interfaces.constants.ITerminalsConnectorConstants");

        Map<String, Object> props = new HashMap<>();
        props.put(constant(constants, "PROP_DELEGATE_ID", "delegateId"), localLauncherId(base));
        props.put(constant(constants, "PROP_TITLE", "title"), title);
        props.put(constant(constants, "PROP_PROCESS_PATH", "process.path"), program);
        props.put(constant(constants, "PROP_PROCESS_ARGS", "process.args"), args);
        props.put(constant(constants, "PROP_PROCESS_WORKING_DIR", "process.working_dir"), workDir.toString());
        props.put(constant(constants, "PROP_FORCE_NEW", "terminal.forceNew"), Boolean.TRUE);

        for (Method m : service.getClass().getMethods()) {
            if ("openConsole".equals(m.getName()) && m.getParameterCount() == 2) {
                m.invoke(service, props, null);
                return true;
            }
        }
        return false;
    }

    private static String constant(Class<?> constants, String field, String fallback) {
        try {
            Field f = constants.getField(field);
            Object v = f.get(null);
            return v instanceof String ? (String) v : fallback;
        } catch (ReflectiveOperationException e) {
            return fallback;
        }
    }

    /** Descobre o id do "launcher local" registrado (o que abre um processo da máquina). */
    private static String localLauncherId(String base) {
        String fallback = base + ".connector.local.launcher.local";
        try {
            Bundle ui = Platform.getBundle(base + ".view.ui");
            if (ui == null) {
                return fallback;
            }
            Class<?> mgr = ui.loadClass(base + ".view.ui.launcher.LauncherDelegateManager");
            Object instance = mgr.getMethod("getInstance").invoke(null);
            Object delegates = mgr.getMethod("getLauncherDelegates", boolean.class).invoke(instance, false);
            Iterable<?> list = delegates instanceof Object[] ? java.util.Arrays.asList((Object[]) delegates)
                    : delegates instanceof Iterable ? (Iterable<?>) delegates : java.util.List.of();
            for (Object d : list) {
                Object id = d.getClass().getMethod("getId").invoke(d);
                if (id instanceof String && ((String) id).endsWith(".local")) {
                    return (String) id;
                }
            }
        } catch (Throwable t) {
            // usa o id conhecido
        }
        return fallback;
    }
}
