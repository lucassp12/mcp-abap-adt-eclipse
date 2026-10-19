package com.brainconsulting.adt.mcp.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.brainconsulting.adt.mcp.model.Environment;
import com.brainconsulting.adt.mcp.model.EnvironmentStore;
import com.brainconsulting.adt.mcp.model.Names;

/** Cadastro/edição de um ambiente SAP. */
public class EnvironmentDialog extends TitleAreaDialog {

    private final Environment env;
    private final boolean isNew;
    private final boolean hasPassword;
    private final Map<Integer, String> usedPorts = new HashMap<>();
    private final Set<String> usedSlugs = new HashSet<>();

    private Text nameText;
    private Text urlText;
    private Text clientText;
    private Text userText;
    private Text passwordText;
    private Text languageText;
    private Text portText;
    private Button ignoreTlsButton;

    private String password;

    public EnvironmentDialog(Shell parent, Environment existing, EnvironmentStore store, boolean hasPassword) {
        super(parent);
        this.isNew = existing == null;
        this.env = isNew ? new Environment() : existing.copy();
        this.hasPassword = hasPassword;
        for (Environment other : store.getEnvironments()) {
            if (other.getId().equals(env.getId())) {
                continue;
            }
            usedPorts.put(other.getPort(), other.getName());
            usedSlugs.add(Names.slug(other.getName()));
        }
        if (isNew) {
            int p = 3001;
            while (usedPorts.containsKey(p)) {
                p++;
            }
            env.setPort(p);
        }
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    public void create() {
        super.create();
        getShell().setText("MCP ABAP ADT");
        setTitle(isNew ? "Novo ambiente SAP" : "Editar ambiente SAP");
        validate();
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        Composite c = new Composite(area, SWT.NONE);
        c.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = 10;
        layout.marginHeight = 10;
        c.setLayout(layout);

        nameText = field(c, "Nome do ambiente:", env.getName(), "ex.: Arezzo DEV", SWT.NONE);
        urlText = field(c, "URL HTTP do SAP:", env.getUrl(), "ex.: http://servidor.cliente.local:8000", SWT.NONE);
        clientText = field(c, "Mandante:", env.getClient(), "ex.: 100", SWT.NONE);
        userText = field(c, "Usuário:", env.getUser(), "", SWT.NONE);
        passwordText = field(c, "Senha:", "",
                hasPassword ? "deixe em branco para manter a senha atual" : "", SWT.PASSWORD);
        languageText = field(c, "Idioma:", env.getLanguage(), "ex.: PT", SWT.NONE);
        portText = field(c, "Porta local do MCP:", String.valueOf(env.getPort()), "ex.: 3001", SWT.NONE);

        ignoreTlsButton = new Button(c, SWT.CHECK);
        ignoreTlsButton.setText("Ignorar validação de certificado TLS (somente DEV/QAS com certificado interno)");
        ignoreTlsButton.setSelection(env.isIgnoreTls());
        ignoreTlsButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        Label hint = new Label(c, SWT.WRAP);
        hint.setText("Dica: as portas HTTP/HTTPS do sistema aparecem na transação SMICM → Goto → Services. "
                + "A porta local é onde o servidor MCP vai escutar no seu computador; cada ambiente usa uma.");
        GridData hd = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        hd.widthHint = 460;
        hint.setLayoutData(hd);

        for (Text t : new Text[] { nameText, urlText, clientText, userText, passwordText, languageText, portText }) {
            t.addModifyListener(e -> validate());
        }
        return area;
    }

    private Text field(Composite c, String label, String value, String hint, int style) {
        new Label(c, SWT.NONE).setText(label);
        Text t = new Text(c, SWT.BORDER | SWT.SINGLE | style);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.widthHint = 320;
        t.setLayoutData(gd);
        if (value != null) {
            t.setText(value);
        }
        if (hint != null && !hint.isEmpty()) {
            t.setMessage(hint);
        }
        return t;
    }

    private void validate() {
        if (portText == null) {
            return;
        }
        String err = null;
        String name = nameText.getText().trim();
        String url = urlText.getText().trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            err = "Informe o nome do ambiente.";
        } else if (Names.slug(name).isEmpty()) {
            err = "O nome precisa ter letras ou números.";
        } else if (usedSlugs.contains(Names.slug(name))) {
            err = "Já existe um ambiente com nome equivalente.";
        } else if (!(url.startsWith("http://") || url.startsWith("https://")) || url.length() < 10) {
            err = "A URL deve começar com http:// ou https://.";
        } else if (!clientText.getText().trim().matches("\\d{3}")) {
            err = "O mandante deve ter 3 dígitos.";
        } else if (userText.getText().trim().isEmpty()) {
            err = "Informe o usuário.";
        } else if (!hasPassword && passwordText.getText().isEmpty()) {
            err = "Informe a senha.";
        } else {
            int port = -1;
            try {
                port = Integer.parseInt(portText.getText().trim());
            } catch (NumberFormatException e) {
                // tratado abaixo
            }
            if (port < 1024 || port > 65535) {
                err = "A porta local deve ser um número entre 1024 e 65535.";
            } else if (usedPorts.containsKey(port)) {
                err = "A porta " + port + " já é usada pelo ambiente \"" + usedPorts.get(port) + "\".";
            }
        }
        setErrorMessage(err);
        if (err == null) {
            setMessage("A senha fica criptografada no Secure Storage do Eclipse, nunca em arquivo texto.");
        }
        Button ok = getButton(IDialogConstants.OK_ID);
        if (ok != null) {
            ok.setEnabled(err == null);
        }
    }

    @Override
    protected void okPressed() {
        env.setName(nameText.getText().trim());
        env.setUrl(urlText.getText().trim().replaceAll("/+$", ""));
        env.setClient(clientText.getText().trim());
        env.setUser(userText.getText().trim().toUpperCase(Locale.ROOT));
        String lang = languageText.getText().trim().toUpperCase(Locale.ROOT);
        env.setLanguage(lang.isEmpty() ? "PT" : lang);
        env.setPort(Integer.parseInt(portText.getText().trim()));
        env.setIgnoreTls(ignoreTlsButton.getSelection());
        String pw = passwordText.getText();
        password = pw.isEmpty() ? null : pw;
        super.okPressed();
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    public Environment getEnvironment() {
        return env;
    }

    /** Nova senha digitada, ou null para manter a atual. */
    public String getPassword() {
        return password;
    }
}
