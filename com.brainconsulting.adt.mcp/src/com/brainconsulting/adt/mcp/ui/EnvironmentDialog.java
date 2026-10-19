package com.brainconsulting.adt.mcp.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;

import com.brainconsulting.adt.mcp.model.AuthMethod;
import com.brainconsulting.adt.mcp.model.Environment;
import com.brainconsulting.adt.mcp.model.EnvironmentStore;
import com.brainconsulting.adt.mcp.model.Names;
import com.brainconsulting.adt.mcp.model.ServiceKeys;
import com.brainconsulting.adt.mcp.model.SystemType;
import com.brainconsulting.adt.mcp.server.CommandLine;

/** Cadastro/edição de um ambiente SAP, com todas as formas de conexão do mcp-abap-adt. */
public class EnvironmentDialog extends TitleAreaDialog {

    private final Environment env;
    private final boolean isNew;
    private final String serviceKeysDir;
    private final boolean hasPassword;
    private final boolean hasPassphrase;
    private final Map<Integer, String> usedPorts = new HashMap<>();
    private final Set<String> usedSlugs = new HashSet<>();
    private final Map<String, String> secrets = new LinkedHashMap<>();

    // geral
    private Text nameText;
    private Combo methodCombo;
    private Combo systemCombo;
    private Text portText;
    private Composite connectionPage;

    // grupos que aparecem/somem conforme o método
    private Group sapGroup;
    private Group userGroup;
    private Group certGroup;
    private Group kerberosGroup;
    private Group serviceKeyGroup;
    private Group envFileGroup;

    // SAP (URL)
    private Text urlText;
    private Text clientText;
    private Text languageText;
    private Button ignoreTlsButton;
    // usuário/senha
    private Text userText;
    private Text passwordText;
    // certificado
    private Button pemRadio;
    private Button pfxRadio;
    private Composite pemFields;
    private Composite pfxFields;
    private Text certPathText;
    private Text certKeyText;
    private Text pfxPathText;
    private Text passphraseText;
    // kerberos
    private Text spnText;
    // service key
    private Text destinationText;
    private Label keyStatusLabel;
    private Text browserPortText;
    private Button persistButton;
    private Path pendingServiceKey;
    private boolean destinationTouched;
    private boolean settingDestination;
    // .env próprio
    private Text envFileText;
    // ferramentas
    private Button readonlyButton;
    private Button highButton;
    private Button lowButton;
    // Claude Code
    private Text workDirText;
    // avançado
    private Text extraArgsText;
    private Text extraEnvText;

    public EnvironmentDialog(Shell parent, Environment existing, EnvironmentStore store) {
        super(parent);
        this.isNew = existing == null;
        this.env = isNew ? new Environment() : existing.copy();
        this.serviceKeysDir = store.getSettings().getServiceKeysDir();
        this.hasPassword = !isNew && store.hasSecret(env.getId(), CommandLine.SECRET_PASSWORD);
        this.hasPassphrase = !isNew && store.hasSecret(env.getId(), CommandLine.SECRET_CERT_PASSPHRASE);
        for (Environment other : store.getEnvironments()) {
            if (other.getId().equals(env.getId())) {
                continue;
            }
            usedPorts.put(other.getPort(), other.getName());
            if (other.getAuthMethod() == AuthMethod.SERVICE_KEY) {
                usedPorts.put(other.getBrowserAuthPort(), other.getName() + " (retorno do login)");
            }
            usedSlugs.add(Names.slug(other.getName()));
        }
        if (isNew) {
            int p = 3001;
            while (usedPorts.containsKey(p)) {
                p++;
            }
            env.setPort(p);
            int b = 61001;
            while (usedPorts.containsKey(b)) {
                b++;
            }
            env.setBrowserAuthPort(b);
        }
        destinationTouched = !isNew && !env.getDestination().isBlank();
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }

    @Override
    public void create() {
        super.create();
        getShell().setText("MCP ABAP ADT");
        setTitle(isNew ? "Novo ambiente SAP" : "Editar ambiente SAP");
        updateVisibility();
        validate();
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);
        TabFolder tabs = new TabFolder(area, SWT.TOP);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        TabItem t1 = new TabItem(tabs, SWT.NONE);
        t1.setText("Conexão");
        t1.setControl(createConnectionPage(tabs));

        TabItem t2 = new TabItem(tabs, SWT.NONE);
        t2.setText("Ferramentas");
        t2.setControl(createToolsPage(tabs));

        TabItem tc = new TabItem(tabs, SWT.NONE);
        tc.setText("Claude Code");
        tc.setControl(createClaudePage(tabs));

        TabItem t3 = new TabItem(tabs, SWT.NONE);
        t3.setText("Avançado");
        t3.setControl(createAdvancedPage(tabs));

        hookValidation();
        return area;
    }

    // ------------------------------------------------------------- páginas

    private Composite createConnectionPage(Composite parent) {
        connectionPage = page(parent, 2);

        nameText = field(connectionPage, "Nome do ambiente:", env.getName(), "ex.: Arezzo DEV");

        new Label(connectionPage, SWT.NONE).setText("Forma de conexão:");
        methodCombo = new Combo(connectionPage, SWT.READ_ONLY | SWT.DROP_DOWN);
        for (AuthMethod m : AuthMethod.values()) {
            methodCombo.add(m.label);
        }
        methodCombo.select(env.getAuthMethod().ordinal());
        methodCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(connectionPage, SWT.NONE).setText("Tipo de sistema:");
        systemCombo = new Combo(connectionPage, SWT.READ_ONLY | SWT.DROP_DOWN);
        for (SystemType t : SystemType.values()) {
            systemCombo.add(t.label);
        }
        systemCombo.select(env.getSystemType().ordinal());
        systemCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        portText = field(connectionPage, "Porta local do MCP:", String.valueOf(env.getPort()), "ex.: 3001");

        // --- sistema SAP por URL ---
        sapGroup = group(connectionPage, "Sistema SAP", 2);
        urlText = field(sapGroup, "URL HTTP(S):", env.getUrl(), "ex.: http://servidor.cliente.local:8000");
        clientText = field(sapGroup, "Mandante:", env.getClient(), "ex.: 100");
        languageText = field(sapGroup, "Idioma:", env.getLanguage(), "ex.: PT");
        ignoreTlsButton = check(sapGroup, "Ignorar validação de certificado TLS (somente DEV/QAS com certificado interno)",
                env.isIgnoreTls());
        note(sapGroup, "Use a URL do ICM (SMICM → Goto → Services), não a entrada do SAP Logon.");

        // --- usuário e senha ---
        userGroup = group(connectionPage, "Usuário e senha", 2);
        userText = field(userGroup, "Usuário:", env.getUser(), "");
        new Label(userGroup, SWT.NONE).setText("Senha:");
        passwordText = new Text(userGroup, SWT.BORDER | SWT.SINGLE | SWT.PASSWORD);
        passwordText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        if (hasPassword) {
            passwordText.setMessage("deixe em branco para manter a senha atual");
        }

        // --- certificado ---
        certGroup = group(connectionPage, "Certificado de cliente (mTLS)", 2);
        new Label(certGroup, SWT.NONE).setText("Formato:");
        Composite radios = new Composite(certGroup, SWT.NONE);
        radios.setLayout(noMargins(2));
        pemRadio = new Button(radios, SWT.RADIO);
        pemRadio.setText("PEM (.crt + .key)");
        pfxRadio = new Button(radios, SWT.RADIO);
        pfxRadio.setText("PKCS#12 (.pfx/.p12)");
        boolean pfx = Environment.CERT_PFX.equals(env.getCertFormat());
        pfxRadio.setSelection(pfx);
        pemRadio.setSelection(!pfx);

        pemFields = new Composite(certGroup, SWT.NONE);
        pemFields.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        pemFields.setLayout(noMargins(3));
        certPathText = fileField(pemFields, "Certificado:", env.getCertPath(), new String[] { "*.crt;*.pem;*.cer", "*.*" });
        certKeyText = fileField(pemFields, "Chave privada:", env.getCertKeyPath(), new String[] { "*.key;*.pem", "*.*" });

        pfxFields = new Composite(certGroup, SWT.NONE);
        pfxFields.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        pfxFields.setLayout(noMargins(3));
        pfxPathText = fileField(pfxFields, "Arquivo .pfx:", env.getPfxPath(), new String[] { "*.pfx;*.p12", "*.*" });
        new Label(pfxFields, SWT.NONE).setText("Senha do .pfx:");
        passphraseText = new Text(pfxFields, SWT.BORDER | SWT.SINGLE | SWT.PASSWORD);
        passphraseText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        if (hasPassphrase) {
            passphraseText.setMessage("deixe em branco para manter a atual");
        }
        note(certGroup, "Somente on-premise via HTTPS. O certificado precisa estar mapeado a um usuário (CERTRULE).");

        // --- kerberos ---
        kerberosGroup = group(connectionPage, "Kerberos / SPNEGO", 2);
        spnText = field(kerberosGroup, "SPN (opcional):", env.getKerberosSpn(), "padrão: HTTP@<host>");
        note(kerberosGroup, "Requer ticket Kerberos válido no Windows (klist) e o pacote npm \"kerberos\" "
                + "(npm i -g kerberos). NTLM não é aceito pelo servidor.");

        // --- service key ---
        serviceKeyGroup = group(connectionPage, "Service key (SAP BTP / S/4HANA Cloud)", 3);
        new Label(serviceKeyGroup, SWT.NONE).setText("Nome do destino:");
        destinationText = new Text(serviceKeyGroup, SWT.BORDER | SWT.SINGLE);
        destinationText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        destinationText.setText(env.getDestination().isBlank()
                ? ServiceKeys.suggestDestination(env.getName()) : env.getDestination());
        Button importButton = new Button(serviceKeyGroup, SWT.PUSH);
        importButton.setText("Importar service key...");
        importButton.addListener(SWT.Selection, e -> chooseServiceKey());
        new Label(serviceKeyGroup, SWT.NONE);
        keyStatusLabel = new Label(serviceKeyGroup, SWT.WRAP);
        GridData sd = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        sd.widthHint = 420;
        keyStatusLabel.setLayoutData(sd);
        new Label(serviceKeyGroup, SWT.NONE).setText("Porta de retorno do login:");
        browserPortText = new Text(serviceKeyGroup, SWT.BORDER | SWT.SINGLE);
        browserPortText.setText(String.valueOf(env.getBrowserAuthPort()));
        browserPortText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        persistButton = check(serviceKeyGroup, "Lembrar o login entre reinícios (grava os tokens em disco: --unsafe)",
                env.isPersistSession());
        note(serviceKeyGroup, "No BTP Cockpit: instância do ABAP environment → Service Keys → copie o JSON para um "
                + "arquivo. Ao importar, ele é copiado para a pasta de destinos do mcp-abap-adt.");

        // --- .env próprio ---
        envFileGroup = group(connectionPage, "Arquivo .env", 3);
        envFileText = fileField(envFileGroup, "Arquivo:", env.getEnvFilePath(), new String[] { "*.env", "*.*" });
        note(envFileGroup, "Para JWT gerado pelo mcp-auth (pacote @mcp-abap-adt/auth-broker-cli), SNC ou qualquer "
                + "configuração que a sua versão do servidor aceite. O arquivo é passado com --env-path.");

        // listeners que mudam a tela
        methodCombo.addListener(SWT.Selection, e -> {
            systemCombo.select(selectedMethod().defaultSystemType.ordinal());
            updateVisibility();
            validate();
        });
        pemRadio.addListener(SWT.Selection, e -> {
            updateVisibility();
            validate();
        });
        pfxRadio.addListener(SWT.Selection, e -> {
            updateVisibility();
            validate();
        });
        nameText.addModifyListener(e -> {
            if (!destinationTouched) {
                settingDestination = true;
                destinationText.setText(ServiceKeys.suggestDestination(nameText.getText()));
                settingDestination = false;
            }
        });
        destinationText.addModifyListener(e -> {
            if (!settingDestination) {
                destinationTouched = true;
            }
            updateKeyStatus();
        });
        updateKeyStatus();
        return connectionPage;
    }

    private Composite createToolsPage(Composite parent) {
        Composite c = page(parent, 1);
        note(c, "Grupos de ferramentas do mcp-abap-adt que o Claude vai enxergar neste ambiente (--exposition). "
                + "Alto nível e baixo nível não podem ser usados juntos: o servidor recusa essa combinação. "
                + "Recomendado: Leitura + Alto nível.");
        readonlyButton = check(c, "Leitura (readonly): código-fonte, where-used, metadados, estrutura de objetos, "
                + "pacotes, transportes, conteúdo de tabelas, SQL, dumps, traces, AST e análise semântica",
                env.isExposeReadonly());
        highButton = check(c, "Alto nível (high): criar, alterar, ativar e excluir classes, programas, DDIC, "
                + "CDS, RAP (BDEF, service definition/binding), transportes, ABAP Unit, ATC",
                env.isExposeHigh());
        lowButton = check(c, "Baixo nível (low): operações ADT passo a passo (lock/unlock, check, validate, "
                + "update granular). Alternativa ao alto nível, para fluxos avançados; use com cuidado.",
                env.isExposeLow());
        note(c, "Busca e informações de sistema estão sempre incluídas. Programas e outros objetos clássicos só "
                + "aparecem com \"Tipo de sistema\" = On-premise; no ABAP Cloud o servidor oculta o que não existe lá.");
        return c;
    }

    private Composite createClaudePage(Composite parent) {
        Composite c = page(parent, 3);
        new Label(c, SWT.NONE).setText("Pasta de trabalho:");
        workDirText = new Text(c, SWT.BORDER | SWT.SINGLE);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.widthHint = 300;
        workDirText.setLayoutData(gd);
        workDirText.setText(env.getWorkDir() == null ? "" : env.getWorkDir());
        workDirText.setMessage("vazio = pasta do usuário");
        Button b = new Button(c, SWT.PUSH);
        b.setText("Procurar...");
        b.addListener(SWT.Selection, e -> {
            org.eclipse.swt.widgets.DirectoryDialog dd = new org.eclipse.swt.widgets.DirectoryDialog(getShell());
            dd.setFilterPath(workDirText.getText());
            String path = dd.open();
            if (path != null) {
                workDirText.setText(path);
            }
        });
        note(c, "Pasta onde o Claude Code abre quando você usa \">_ Abrir Claude Code\" com este ambiente "
                + "selecionado. Use, por exemplo, a pasta do projeto do cliente com as especificações: o Claude "
                + "lê os arquivos e o CLAUDE.md de lá.");
        return c;
    }

    private Composite createAdvancedPage(Composite parent) {
        Composite c = page(parent, 2);
        extraArgsText = field(c, "Argumentos extras:", env.getExtraArgs(), "ex.: --auth-broker-path=C:\\chaves");
        Label l = new Label(c, SWT.NONE);
        l.setText("Variáveis extras:");
        l.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
        extraEnvText = new Text(c, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.WRAP);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        gd.heightHint = 90;
        gd.widthHint = 360;
        extraEnvText.setLayoutData(gd);
        extraEnvText.setText(env.getExtraEnv() == null ? "" : env.getExtraEnv());
        note(c, "Uma variável por linha, no formato CHAVE=valor (ex.: AUTH_LOG_LEVEL=debug). Prevalecem sobre as "
                + "geradas pelo plugin. Servem para usar recursos novos do servidor sem esperar uma versão nova "
                + "do plugin.");
        return c;
    }

    // ------------------------------------------------------------ comportamento

    private AuthMethod selectedMethod() {
        int i = methodCombo.getSelectionIndex();
        return i < 0 ? AuthMethod.BASIC : AuthMethod.values()[i];
    }

    private SystemType selectedSystemType() {
        int i = systemCombo.getSelectionIndex();
        return i < 0 ? SystemType.ONPREM : SystemType.values()[i];
    }

    private void updateVisibility() {
        AuthMethod m = selectedMethod();
        show(sapGroup, m.usesSapUrl());
        show(userGroup, m.usesUserPassword());
        show(certGroup, m == AuthMethod.CERTIFICATE);
        show(kerberosGroup, m == AuthMethod.KERBEROS);
        show(serviceKeyGroup, m == AuthMethod.SERVICE_KEY);
        show(envFileGroup, m == AuthMethod.ENV_FILE);
        boolean pfx = pfxRadio.getSelection();
        show(pemFields, !pfx);
        show(pfxFields, pfx);
        connectionPage.layout(true, true);
        Shell shell = getShell();
        if (shell != null) {
            Point want = shell.computeSize(SWT.DEFAULT, SWT.DEFAULT);
            Point cur = shell.getSize();
            shell.setSize(Math.max(cur.x, want.x), want.y);
        }
    }

    private void chooseServiceKey() {
        FileDialog fd = new FileDialog(getShell(), SWT.OPEN);
        fd.setFilterExtensions(new String[] { "*.json", "*.*" });
        String path = fd.open();
        if (path == null) {
            return;
        }
        String err = ServiceKeys.validate(Paths.get(path));
        if (err != null) {
            pendingServiceKey = null;
            updateKeyStatus();
            setErrorMessage(err);
            return;
        }
        pendingServiceKey = Paths.get(path);
        updateKeyStatus();
        validate();
    }

    private void updateKeyStatus() {
        if (keyStatusLabel == null) {
            return;
        }
        String dest = destinationText.getText().trim();
        if (pendingServiceKey != null) {
            keyStatusLabel.setText("Selecionada: " + pendingServiceKey.getFileName() + " (será copiada ao salvar).");
        } else if (ServiceKeys.isValidDestination(dest)
                && Files.isRegularFile(ServiceKeys.file(serviceKeysDir, dest))) {
            keyStatusLabel.setText("Service key encontrada: " + ServiceKeys.file(serviceKeysDir, dest));
        } else {
            keyStatusLabel.setText("Nenhuma service key importada para este destino.");
        }
        keyStatusLabel.getParent().layout(true, true);
    }

    private void hookValidation() {
        for (Text t : new Text[] { nameText, portText, urlText, clientText, languageText, userText, passwordText,
                certPathText, certKeyText, pfxPathText, passphraseText, spnText, destinationText, browserPortText,
                envFileText, workDirText, extraArgsText, extraEnvText }) {
            t.addModifyListener(e -> validate());
        }
        for (Button b : new Button[] { readonlyButton, highButton, lowButton }) {
            b.addListener(SWT.Selection, e -> validate());
        }
        systemCombo.addListener(SWT.Selection, e -> validate());
    }

    private void validate() {
        if (extraEnvText == null) {
            return;
        }
        String err = firstError();
        setErrorMessage(err);
        if (err == null) {
            setMessage("Senhas ficam criptografadas no Secure Storage do Eclipse, nunca em arquivo texto.");
        }
        Button ok = getButton(IDialogConstants.OK_ID);
        if (ok != null) {
            ok.setEnabled(err == null);
        }
    }

    private String firstError() {
        String name = nameText.getText().trim();
        if (name.isEmpty()) {
            return "Informe o nome do ambiente.";
        }
        if (Names.slug(name).isEmpty()) {
            return "O nome precisa ter letras ou números.";
        }
        if (usedSlugs.contains(Names.slug(name))) {
            return "Já existe um ambiente com nome equivalente.";
        }
        String portErr = portError(portText.getText(), "porta local");
        if (portErr != null) {
            return portErr;
        }
        AuthMethod m = selectedMethod();
        if (m.usesSapUrl()) {
            String url = urlText.getText().trim().toLowerCase(Locale.ROOT);
            if (!(url.startsWith("http://") || url.startsWith("https://")) || url.length() < 10) {
                return "A URL deve começar com http:// ou https://.";
            }
            if (m == AuthMethod.CERTIFICATE && !url.startsWith("https://")) {
                return "Autenticação por certificado exige URL https://.";
            }
            if (!clientText.getText().trim().matches("\\d{3}")) {
                return "O mandante deve ter 3 dígitos.";
            }
        }
        switch (m) {
            case BASIC:
            case RFC:
                if (userText.getText().trim().isEmpty()) {
                    return "Informe o usuário.";
                }
                if (!hasPassword && passwordText.getText().isEmpty()) {
                    return "Informe a senha.";
                }
                break;
            case CERTIFICATE:
                if (pfxRadio.getSelection()) {
                    if (!fileExists(pfxPathText)) {
                        return "Selecione um arquivo .pfx/.p12 existente.";
                    }
                } else if (!fileExists(certPathText) || !fileExists(certKeyText)) {
                    return "Selecione o certificado e a chave privada (arquivos existentes).";
                }
                break;
            case SERVICE_KEY: {
                String dest = destinationText.getText().trim();
                if (!ServiceKeys.isValidDestination(dest)) {
                    return "Nome do destino: use só letras, números, _ . ou -";
                }
                if (pendingServiceKey == null && !Files.isRegularFile(ServiceKeys.file(serviceKeysDir, dest))) {
                    return "Importe a service key deste destino.";
                }
                String bErr = portError(browserPortText.getText(), "porta de retorno do login");
                if (bErr != null) {
                    return bErr;
                }
                if (browserPortText.getText().trim().equals(portText.getText().trim())) {
                    return "A porta de retorno do login deve ser diferente da porta local do MCP.";
                }
                break;
            }
            case ENV_FILE:
                if (!fileExists(envFileText)) {
                    return "Selecione um arquivo .env existente.";
                }
                break;
            default:
                break;
        }
        if (!readonlyButton.getSelection() && !highButton.getSelection() && !lowButton.getSelection()) {
            return "Marque ao menos um grupo de ferramentas (aba Ferramentas).";
        }
        if (highButton.getSelection() && lowButton.getSelection()) {
            return "Alto nível e baixo nível são exclusivos no mcp-abap-adt: escolha um deles (aba Ferramentas).";
        }
        String wd = workDirText.getText().trim();
        if (!wd.isEmpty()) {
            try {
                if (!Files.isDirectory(Paths.get(wd))) {
                    return "A pasta de trabalho do Claude Code não existe (aba Claude Code).";
                }
            } catch (RuntimeException e) {
                return "Pasta de trabalho inválida (aba Claude Code).";
            }
        }
        String bad = CommandLine.invalidEnvLine(extraEnvText.getText());
        if (bad != null) {
            return "Variável extra inválida (aba Avançado): \"" + bad.trim() + "\". Use CHAVE=valor.";
        }
        return null;
    }

    private String portError(String text, String what) {
        int port;
        try {
            port = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return "A " + what + " deve ser um número.";
        }
        if (port < 1024 || port > 65535) {
            return "A " + what + " deve estar entre 1024 e 65535.";
        }
        if (usedPorts.containsKey(port)) {
            return "A porta " + port + " já é usada por \"" + usedPorts.get(port) + "\".";
        }
        return null;
    }

    private static boolean fileExists(Text t) {
        String p = t.getText().trim();
        try {
            return !p.isEmpty() && Files.isRegularFile(Paths.get(p));
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    protected void okPressed() {
        AuthMethod m = selectedMethod();
        String dest = destinationText.getText().trim();
        if (m == AuthMethod.SERVICE_KEY && pendingServiceKey != null) {
            try {
                ServiceKeys.importKey(pendingServiceKey, serviceKeysDir, dest);
            } catch (IOException e) {
                setErrorMessage("Não foi possível importar a service key: " + e.getMessage());
                return;
            }
        }
        env.setName(nameText.getText().trim());
        env.setAuthMethod(m);
        env.setSystemType(selectedSystemType());
        env.setPort(Integer.parseInt(portText.getText().trim()));
        env.setUrl(urlText.getText().trim().replaceAll("/+$", ""));
        env.setClient(clientText.getText().trim());
        String lang = languageText.getText().trim().toUpperCase(Locale.ROOT);
        env.setLanguage(lang.isEmpty() ? "PT" : lang);
        env.setIgnoreTls(ignoreTlsButton.getSelection());
        env.setUser(userText.getText().trim().toUpperCase(Locale.ROOT));
        env.setCertFormat(pfxRadio.getSelection() ? Environment.CERT_PFX : Environment.CERT_PEM);
        env.setCertPath(certPathText.getText().trim());
        env.setCertKeyPath(certKeyText.getText().trim());
        env.setPfxPath(pfxPathText.getText().trim());
        env.setKerberosSpn(spnText.getText().trim());
        env.setDestination(dest);
        try {
            env.setBrowserAuthPort(Integer.parseInt(browserPortText.getText().trim()));
        } catch (NumberFormatException e) {
            // só é validado quando o método é service key
        }
        env.setPersistSession(persistButton.getSelection());
        env.setEnvFilePath(envFileText.getText().trim());
        env.setExposeReadonly(readonlyButton.getSelection());
        env.setExposeHigh(highButton.getSelection());
        env.setExposeLow(lowButton.getSelection());
        env.setWorkDir(workDirText.getText().trim());
        env.setExtraArgs(extraArgsText.getText().trim());
        env.setExtraEnv(extraEnvText.getText());

        if (!passwordText.getText().isEmpty()) {
            secrets.put(CommandLine.SECRET_PASSWORD, passwordText.getText());
        }
        if (!passphraseText.getText().isEmpty()) {
            secrets.put(CommandLine.SECRET_CERT_PASSPHRASE, passphraseText.getText());
        }
        super.okPressed();
    }

    @Override
    protected boolean isResizable() {
        return true;
    }

    public Environment getEnvironment() {
        return env;
    }

    /** Segredos digitados nesta edição (campos vazios mantêm o valor atual). */
    public Map<String, String> getSecrets() {
        return secrets;
    }

    // --------------------------------------------------------------- helpers

    private static Composite page(Composite parent, int cols) {
        Composite c = new Composite(parent, SWT.NONE);
        GridLayout l = new GridLayout(cols, false);
        l.marginWidth = 10;
        l.marginHeight = 10;
        c.setLayout(l);
        return c;
    }

    private static GridLayout noMargins(int cols) {
        GridLayout l = new GridLayout(cols, false);
        l.marginWidth = 0;
        l.marginHeight = 0;
        return l;
    }

    private static Group group(Composite parent, String title, int cols) {
        Group g = new Group(parent, SWT.NONE);
        g.setText(title);
        g.setLayout(new GridLayout(cols, false));
        g.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
        return g;
    }

    private static Text field(Composite c, String label, String value, String hint) {
        new Label(c, SWT.NONE).setText(label);
        Text t = new Text(c, SWT.BORDER | SWT.SINGLE);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.widthHint = 340;
        t.setLayoutData(gd);
        t.setText(value == null ? "" : value);
        if (hint != null && !hint.isEmpty()) {
            t.setMessage(hint);
        }
        return t;
    }

    /** Campo de arquivo em 3 colunas: rótulo, texto, botão "Procurar...". */
    private Text fileField(Composite c, String label, String value, String[] filters) {
        new Label(c, SWT.NONE).setText(label);
        Text t = new Text(c, SWT.BORDER | SWT.SINGLE);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false);
        gd.widthHint = 280;
        t.setLayoutData(gd);
        t.setText(value == null ? "" : value);
        Button b = new Button(c, SWT.PUSH);
        b.setText("Procurar...");
        b.addListener(SWT.Selection, e -> {
            FileDialog fd = new FileDialog(getShell(), SWT.OPEN);
            fd.setFilterExtensions(filters);
            String path = fd.open();
            if (path != null) {
                t.setText(path);
            }
        });
        return t;
    }

    private static Button check(Composite c, String text, boolean selected) {
        Button b = new Button(c, SWT.CHECK | SWT.WRAP);
        b.setText(text);
        b.setSelection(selected);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false, ((GridLayout) c.getLayout()).numColumns, 1);
        gd.widthHint = 460;
        b.setLayoutData(gd);
        return b;
    }

    private static void note(Composite c, String text) {
        Label l = new Label(c, SWT.WRAP);
        l.setText(text);
        GridData gd = new GridData(SWT.FILL, SWT.CENTER, true, false, ((GridLayout) c.getLayout()).numColumns, 1);
        gd.widthHint = 460;
        l.setLayoutData(gd);
    }

    private static void show(Control c, boolean visible) {
        c.setVisible(visible);
        Object ld = c.getLayoutData();
        if (ld instanceof GridData) {
            ((GridData) ld).exclude = !visible;
        }
    }
}
