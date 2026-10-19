package com.brainconsulting.adt.mcp.ui;

import java.util.Map;
import java.util.function.Function;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IElementComparer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.part.ViewPart;

import com.brainconsulting.adt.mcp.Activator;
import com.brainconsulting.adt.mcp.model.Environment;
import com.brainconsulting.adt.mcp.model.EnvironmentStore;
import com.brainconsulting.adt.mcp.server.ClaudeLauncher;
import com.brainconsulting.adt.mcp.server.ConnectionTester;
import com.brainconsulting.adt.mcp.server.McpException;
import com.brainconsulting.adt.mcp.server.ServerManager;

/** View "MCP ABAP ADT": lista os ambientes SAP e controla o servidor MCP de cada um. */
public class McpServersView extends ViewPart implements ServerManager.Listener {

    public static final String ID = "com.brainconsulting.adt.mcp.view";

    /** Roxo Brain (#552579), usado no status "Rodando". */
    private static final Color BRAIN_PURPLE = new Color(85, 37, 121);

    private TableViewer viewer;

    private Action addAction;
    private Action editAction;
    private Action removeAction;
    private Action startAction;
    private Action stopAction;
    private Action testAction;
    private Action settingsAction;
    private Action claudeAction;

    private EnvironmentStore store() {
        return Activator.getDefault().getStore();
    }

    private ServerManager servers() {
        return Activator.getDefault().getServerManager();
    }

    @Override
    public void createPartControl(Composite parent) {
        viewer = new TableViewer(parent, SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        Table table = viewer.getTable();
        table.setHeaderVisible(true);
        table.setLinesVisible(true);

        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.setComparer(new IElementComparer() {
            @Override
            public boolean equals(Object a, Object b) {
                if (a instanceof Environment && b instanceof Environment) {
                    return ((Environment) a).getId().equals(((Environment) b).getId());
                }
                return a == null ? b == null : a.equals(b);
            }

            @Override
            public int hashCode(Object e) {
                return e instanceof Environment ? ((Environment) e).getId().hashCode() : e.hashCode();
            }
        });

        column("Status", 100, env -> servers().isRunning(env.getId()) ? "● Rodando" : "○ Parado", true);
        column("Ambiente", 160, Environment::getName, false);
        column("Sistema", 90, env -> env.getSystemType().shortLabel, false);
        column("Conexão", 110, env -> env.getAuthMethod().shortLabel, false);
        column("Alvo", 280, Environment::targetLabel, false);
        column("Ferramentas", 130, env -> env.exposition(), false);
        column("Porta local", 80, env -> String.valueOf(env.getPort()), false);
        column("Servidor no Claude Code", 180, env -> servers().serverName(env), false);

        makeActions();
        contributeToActionBars();
        hookContextMenu();

        viewer.addSelectionChangedListener(e -> updateActions());
        viewer.addDoubleClickListener(e -> {
            if (editAction.isEnabled()) {
                editAction.run();
            }
        });

        servers().addListener(this);
        refresh();
    }

    private void column(String title, int width, Function<Environment, String> text, boolean isStatus) {
        TableViewerColumn col = new TableViewerColumn(viewer, SWT.NONE);
        col.getColumn().setText(title);
        col.getColumn().setWidth(width);
        col.getColumn().setResizable(true);
        col.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return text.apply((Environment) element);
            }

            @Override
            public Color getForeground(Object element) {
                return isStatus && running(element) ? BRAIN_PURPLE : null;
            }

            @Override
            public Font getFont(Object element) {
                return isStatus && running(element)
                        ? JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT)
                        : null;
            }
        });
    }

    private boolean running(Object element) {
        return element instanceof Environment && servers().isRunning(((Environment) element).getId());
    }

    // ---------------------------------------------------------------- actions

    private void makeActions() {
        addAction = new Action("+ Adicionar") {
            @Override
            public void run() {
                addEnvironment();
            }
        };
        addAction.setToolTipText("Cadastrar um novo ambiente SAP");

        editAction = new Action("Editar") {
            @Override
            public void run() {
                editEnvironment();
            }
        };
        editAction.setToolTipText("Editar o ambiente selecionado (pare o servidor antes)");

        removeAction = new Action("Remover") {
            @Override
            public void run() {
                removeEnvironment();
            }
        };
        removeAction.setToolTipText("Remover o ambiente e a senha guardada");

        startAction = new Action("▶ Iniciar") {
            @Override
            public void run() {
                startSelected();
            }
        };
        startAction.setToolTipText("Iniciar o servidor MCP deste ambiente e registrá-lo no Claude Code");

        stopAction = new Action("■ Parar") {
            @Override
            public void run() {
                stopSelected();
            }
        };
        stopAction.setToolTipText("Parar o servidor MCP deste ambiente");

        testAction = new Action("Testar conexão") {
            @Override
            public void run() {
                testSelected();
            }
        };
        testAction.setToolTipText("Testa rede, porta, protocolo e credenciais do ambiente e explica o resultado");

        claudeAction = new Action(">_ Abrir Claude Code") {
            @Override
            public void run() {
                openClaudeCode();
            }
        };
        claudeAction.setToolTipText("Inicia o servidor do ambiente (se preciso) e abre o Claude Code na pasta de trabalho dele");

        settingsAction = new Action("Configurações...") {
            @Override
            public void run() {
                openSettings();
            }
        };
        settingsAction.setToolTipText("Caminho do mcp-abap-adt, argumentos e integração com o Claude Code");
    }

    private void contributeToActionBars() {
        IToolBarManager tb = getViewSite().getActionBars().getToolBarManager();
        tb.add(claudeAction);
        tb.add(new Separator());
        tb.add(startAction);
        tb.add(stopAction);
        tb.add(new Separator());
        tb.add(testAction);
        tb.add(new Separator());
        tb.add(addAction);
        tb.add(editAction);
        tb.add(removeAction);

        IMenuManager menu = getViewSite().getActionBars().getMenuManager();
        menu.add(settingsAction);
    }

    private void hookContextMenu() {
        MenuManager mm = new MenuManager();
        mm.add(claudeAction);
        mm.add(new Separator());
        mm.add(startAction);
        mm.add(stopAction);
        mm.add(new Separator());
        mm.add(testAction);
        mm.add(new Separator());
        mm.add(editAction);
        mm.add(removeAction);
        mm.add(new Separator());
        mm.add(addAction);
        mm.add(settingsAction);
        Menu menu = mm.createContextMenu(viewer.getControl());
        viewer.getControl().setMenu(menu);
    }

    private void updateActions() {
        Environment e = selected();
        boolean sel = e != null;
        boolean run = sel && servers().isRunning(e.getId());
        editAction.setEnabled(sel && !run);
        removeAction.setEnabled(sel && !run);
        startAction.setEnabled(sel && !run);
        stopAction.setEnabled(run);
        testAction.setEnabled(sel);
        claudeAction.setEnabled(true);
    }

    private Environment selected() {
        if (viewer == null) {
            return null;
        }
        IStructuredSelection sel = viewer.getStructuredSelection();
        return sel.isEmpty() ? null : (Environment) sel.getFirstElement();
    }

    private Shell shell() {
        return getSite().getShell();
    }

    // ---------------------------------------------------------------- handlers

    private void addEnvironment() {
        EnvironmentDialog d = new EnvironmentDialog(shell(), null, store());
        if (d.open() != Window.OK) {
            return;
        }
        saveFromDialog(d);
    }

    private void editEnvironment() {
        Environment e = selected();
        if (e == null) {
            return;
        }
        EnvironmentDialog d = new EnvironmentDialog(shell(), e, store());
        if (d.open() != Window.OK) {
            return;
        }
        saveFromDialog(d);
    }

    private void saveFromDialog(EnvironmentDialog d) {
        Environment env = d.getEnvironment();
        try {
            store().addOrUpdate(env);
            for (Map.Entry<String, String> secret : d.getSecrets().entrySet()) {
                store().setSecret(env.getId(), secret.getKey(), secret.getValue());
            }
            store().save();
        } catch (Exception ex) {
            MessageDialog.openError(shell(), "MCP ABAP ADT", "Não foi possível salvar o ambiente:\n" + ex.getMessage());
        }
        refresh();
        viewer.setSelection(new StructuredSelection(env), true);
    }

    private void removeEnvironment() {
        Environment e = selected();
        if (e == null) {
            return;
        }
        if (!MessageDialog.openConfirm(shell(), "Remover ambiente",
                "Remover \"" + e.getName() + "\"?\nA senha guardada no Secure Storage também será apagada.")) {
            return;
        }
        try {
            store().remove(e.getId());
            store().save();
        } catch (Exception ex) {
            MessageDialog.openError(shell(), "MCP ABAP ADT", "Não foi possível remover:\n" + ex.getMessage());
        }
        refresh();
    }

    private void startSelected() {
        Environment e = selected();
        if (e == null) {
            return;
        }
        try {
            servers().start(e);
        } catch (McpException ex) {
            MessageDialog.openError(shell(), "Não foi possível iniciar \"" + e.getName() + "\"", ex.getMessage());
        }
        refresh();
    }

    private void stopSelected() {
        Environment e = selected();
        if (e != null) {
            servers().stop(e.getId());
        }
        refresh();
    }

    private void testSelected() {
        Environment e = selected();
        if (e == null) {
            return;
        }
        Shell shell = shell();
        Environment env = e.copy();
        Job job = new Job("Testando conexão com " + env.getName()) {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                ConnectionTester.Result r = ConnectionTester.test(env, store().getSecrets(env.getId()),
                        store().getSettings().getServiceKeysDir());
                Display display = shell.getDisplay();
                if (!display.isDisposed()) {
                    display.asyncExec(() -> {
                        if (shell.isDisposed()) {
                            return;
                        }
                        String title = "Teste de conexão – " + env.getName();
                        if (r.ok) {
                            MessageDialog.openInformation(shell, title, r.message);
                        } else {
                            MessageDialog.openWarning(shell, title, r.message);
                        }
                    });
                }
                return Status.OK_STATUS;
            }
        };
        job.setUser(true);
        job.schedule();
    }

    /**
     * Garante o servidor do ambiente selecionado (inicia se preciso, espera a porta abrir) e então
     * abre o Claude Code. Sem ambiente selecionado, abre o Claude Code na pasta do usuário.
     */
    private void openClaudeCode() {
        Environment e = selected();
        Shell shell = shell();
        if (e != null && !servers().isRunning(e.getId())) {
            try {
                servers().start(e);
            } catch (McpException ex) {
                MessageDialog.openError(shell, "Não foi possível iniciar \"" + e.getName() + "\"", ex.getMessage());
                return;
            }
            refresh();
        }
        Environment env = e == null ? null : e.copy();
        Job job = new Job("Abrindo o Claude Code") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                java.nio.file.Path claude = ClaudeLauncher.resolveClaude(store().getSettings().getClaudePath());
                if (claude == null) {
                    asyncError(shell, "Claude Code não encontrado",
                            "O plugin não encontrou o executável do Claude Code (claude.exe ou claude.cmd).\n\n"
                            + "Se ele está instalado, descubra o caminho rodando no PowerShell:\n"
                            + "    where.exe claude\n"
                            + "e informe-o em Configurações → Executável do claude.\n\n"
                            + "Se não está instalado: " + ClaudeLauncher.INSTALL_URL);
                    return Status.OK_STATUS;
                }
                boolean ready = env == null || ClaudeLauncher.waitForPort(env.getPort(), 20_000);
                Display display = shell.getDisplay();
                if (display.isDisposed()) {
                    return Status.OK_STATUS;
                }
                display.asyncExec(() -> launchClaude(shell, env, claude, ready));
                return Status.OK_STATUS;
            }
        };
        job.setUser(true);
        job.schedule();
    }

    private void launchClaude(Shell shell, Environment env, java.nio.file.Path claude, boolean serverReady) {
        java.nio.file.Path dir = ClaudeLauncher.workDir(env == null ? null : env.getWorkDir());
        String title = env == null ? "Claude Code" : "Claude Code – " + env.getName();
        String args = store().getSettings().getClaudeArgs();
        boolean opened = false;
        if (store().getSettings().isClaudeInternalTerminal()) {
            String[] proc = ClaudeLauncher.internalProcess(claude, args);
            opened = EclipseTerminal.open(title, dir, proc[0], proc[1]);
            if (!opened) {
                MessageDialog.openInformation(shell, "Terminal do Eclipse indisponível",
                        "A view Terminal não está disponível nesta instalação do Eclipse.\n"
                        + "O Claude Code será aberto numa janela externa.");
            }
        }
        if (!opened) {
            try {
                ClaudeLauncher.launchExternal(dir, title, claude, args);
            } catch (Exception ex) {
                MessageDialog.openError(shell, "Não foi possível abrir o terminal", ex.getMessage());
                return;
            }
        }
        if (!serverReady) {
            MessageDialog.openWarning(shell, "Servidor ainda não respondeu",
                    "O servidor MCP de \"" + env.getName() + "\" ainda não respondia na porta " + env.getPort()
                    + " quando o Claude Code abriu.\nSe o /mcp mostrar o servidor com falha, confira o Console "
                    + "e use /mcp para reconectar.");
        }
    }

    private static void asyncError(Shell shell, String title, String message) {
        Display display = shell.getDisplay();
        if (!display.isDisposed()) {
            display.asyncExec(() -> {
                if (!shell.isDisposed()) {
                    MessageDialog.openError(shell, title, message);
                }
            });
        }
    }

    private void openSettings() {
        SettingsDialog d = new SettingsDialog(shell(), store().getSettings().copy());
        if (d.open() != Window.OK) {
            return;
        }
        store().setSettings(d.getSettings());
        try {
            store().save();
        } catch (Exception ex) {
            MessageDialog.openError(shell(), "MCP ABAP ADT", "Não foi possível salvar as configurações:\n" + ex.getMessage());
        }
        refresh();
    }

    // ------------------------------------------------------------------ state

    private void refresh() {
        if (viewer == null || viewer.getControl().isDisposed()) {
            return;
        }
        ISelection sel = viewer.getSelection();
        viewer.setInput(store().getEnvironments());
        viewer.setSelection(sel);
        viewer.refresh();
        updateActions();
    }

    @Override
    public void stateChanged() {
        Display display = Display.getDefault();
        if (display != null && !display.isDisposed()) {
            display.asyncExec(this::refresh);
        }
    }

    @Override
    public void setFocus() {
        if (viewer != null) {
            viewer.getControl().setFocus();
        }
    }

    @Override
    public void dispose() {
        Activator a = Activator.getDefault();
        if (a != null && a.getServerManager() != null) {
            a.getServerManager().removeListener(this);
        }
        super.dispose();
    }
}
