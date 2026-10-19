package com.brainconsulting.adt.mcp.ui;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.brainconsulting.adt.mcp.model.Settings;
import com.brainconsulting.adt.mcp.server.EmbeddedRuntime;
import com.brainconsulting.adt.mcp.server.RuntimeInfo;

/** Configurações gerais: servidor MCP, argumentos e integração com o Claude Code. */
public class SettingsDialog extends TitleAreaDialog {

    private final Settings settings;

    private Button embeddedRadio;
    private Button externalRadio;
    private Button commandBrowse;
    private Text commandText;
    private Text argsText;
    private Text endpointText;
    private Text prefixText;
    private Text claudeText;
    private Text serviceKeysText;
    private Button registerButton;
    private Button removeOnStopButton;

    public SettingsDialog(Shell parent, Settings settings) {
        super(parent);
        this.settings = settings;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    public void create() {
        super.create();
        getShell().setText("MCP ABAP ADT");
        setTitle("Configurações");
        setMessage("Valem para todos os ambientes. Ficam em ~/.brain-mcp-abap/environments.json.");
        validate();
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite c = new Composite(area, SWT.NONE);
        c.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 10;
        layout.marginHeight = 10;
        c.setLayout(layout);

        RuntimeInfo runtime = EmbeddedRuntime.find();
        new Label(c, SWT.NONE).setText("Servidor MCP:");
        embeddedRadio = new Button(c, SWT.RADIO);
        embeddedRadio.setText("Embutido no plugin (recomendado, não precisa de Node.js nem npm)");
        embeddedRadio.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        new Label(c, SWT.NONE);
        Label runtimeLabel = new Label(c, SWT.WRAP);
        runtimeLabel.setText(runtime != null
                ? "Incluído nesta instalação: " + runtime.describe()
                : "Não disponível nesta instalação (o servidor embutido é distribuído só para Windows x64).");
        GridData rl = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        rl.widthHint = 360;
        runtimeLabel.setLayoutData(rl);
        new Label(c, SWT.NONE);
        externalRadio = new Button(c, SWT.RADIO);
        externalRadio.setText("Executável instalado na máquina (npm install -g @mcp-abap-adt/core)");
        externalRadio.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        boolean embedded = settings.isEmbeddedServer() && runtime != null;
        embeddedRadio.setSelection(embedded);
        embeddedRadio.setEnabled(runtime != null);
        externalRadio.setSelection(!embedded);

        commandText = field(c, "Executável:", settings.getCommandPath());
        commandBrowse = browse(c, commandText, new String[] { "*.cmd;*.bat;*.exe", "*.*" });

        argsText = field(c, "Argumentos base:", settings.getArgsTemplate());
        filler(c);
        note(c, "Use ${port} para a porta local e ${name} para o nome do ambiente. Os argumentos de conexão e de "
                + "ferramentas (--env-path, --mcp, --exposition...) são acrescentados automaticamente.");

        endpointText = field(c, "Caminho do endpoint HTTP:", settings.getEndpointPath());
        filler(c);
        note(c, "Padrão do mcp-abap-adt (fr0ster): /mcp/stream/http. Confira no Console ao subir o servidor.");

        serviceKeysText = field(c, "Pasta das service keys:", settings.getServiceKeysDir());
        browseDir(c, serviceKeysText);

        prefixText = field(c, "Prefixo do nome no Claude:", settings.getServerPrefix());
        filler(c);

        registerButton = new Button(c, SWT.CHECK);
        registerButton.setText("Registrar automaticamente no Claude Code ao iniciar");
        registerButton.setSelection(settings.isRegisterInClaude());
        registerButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));

        removeOnStopButton = new Button(c, SWT.CHECK);
        removeOnStopButton.setText("Remover do Claude Code ao parar o servidor");
        removeOnStopButton.setSelection(settings.isRemoveOnStop());
        removeOnStopButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));

        claudeText = field(c, "Arquivo do Claude Code:", settings.getClaudeConfigPath());
        browse(c, claudeText, new String[] { "*.json", "*.*" });

        registerButton.addListener(SWT.Selection, e -> updateEnablement());
        embeddedRadio.addListener(SWT.Selection, e -> updateEnablement());
        externalRadio.addListener(SWT.Selection, e -> updateEnablement());
        for (Text t : new Text[] { commandText, argsText, endpointText, prefixText, claudeText, serviceKeysText }) {
            t.addModifyListener(e -> validate());
        }
        updateEnablement();
        return area;
    }

    private Text field(Composite c, String label, String value) {
        new Label(c, SWT.NONE).setText(label);
        Text t = new Text(c, SWT.BORDER | SWT.SINGLE);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.widthHint = 360;
        t.setLayoutData(gd);
        t.setText(value == null ? "" : value);
        return t;
    }

    private Button browse(Composite c, Text target, String[] filters) {
        Button b = new Button(c, SWT.PUSH);
        b.setText("Procurar...");
        b.addListener(SWT.Selection, e -> {
            FileDialog fd = new FileDialog(getShell(), SWT.OPEN);
            fd.setFilterExtensions(filters);
            fd.setFileName(target.getText());
            String path = fd.open();
            if (path != null) {
                target.setText(path);
            }
        });
        return b;
    }

    private void browseDir(Composite c, Text target) {
        Button b = new Button(c, SWT.PUSH);
        b.setText("Procurar...");
        b.addListener(SWT.Selection, e -> {
            DirectoryDialog dd = new DirectoryDialog(getShell());
            dd.setFilterPath(target.getText());
            String path = dd.open();
            if (path != null) {
                target.setText(path);
            }
        });
    }

    private void filler(Composite c) {
        new Label(c, SWT.NONE);
    }

    private void note(Composite c, String text) {
        new Label(c, SWT.NONE);
        Label l = new Label(c, SWT.WRAP);
        l.setText(text);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        gd.widthHint = 360;
        l.setLayoutData(gd);
    }

    private void updateEnablement() {
        boolean external = externalRadio.getSelection();
        commandText.setEnabled(external);
        commandBrowse.setEnabled(external);
        boolean reg = registerButton.getSelection();
        removeOnStopButton.setEnabled(reg);
        claudeText.setEnabled(reg);
        prefixText.setEnabled(reg);
        validate();
    }

    private void validate() {
        if (claudeText == null) {
            return;
        }
        String err = null;
        if (externalRadio.getSelection() && commandText.getText().trim().isEmpty()) {
            err = "Informe o executável do servidor MCP.";
        } else if (!argsText.getText().contains("${port}")) {
            err = "Os argumentos precisam conter ${port}, senão todos os ambientes usariam a mesma porta.";
        } else if (serviceKeysText.getText().trim().isEmpty()) {
            err = "Informe a pasta das service keys.";
        } else if (registerButton.getSelection() && claudeText.getText().trim().isEmpty()) {
            err = "Informe o arquivo de configuração do Claude Code.";
        }
        setErrorMessage(err);
        Button ok = getButton(IDialogConstants.OK_ID);
        if (ok != null) {
            ok.setEnabled(err == null);
        }
    }

    @Override
    protected void okPressed() {
        settings.setEmbeddedServer(embeddedRadio.getSelection());
        settings.setCommandPath(commandText.getText().trim());
        settings.setArgsTemplate(argsText.getText().trim());
        settings.setEndpointPath(endpointText.getText().trim());
        settings.setServerPrefix(prefixText.getText().trim());
        settings.setRegisterInClaude(registerButton.getSelection());
        settings.setRemoveOnStop(removeOnStopButton.getSelection());
        settings.setClaudeConfigPath(claudeText.getText().trim());
        settings.setServiceKeysDir(serviceKeysText.getText().trim());
        super.okPressed();
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    public Settings getSettings() {
        return settings;
    }
}
