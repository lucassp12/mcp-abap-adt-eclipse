# MCP ABAP ADT Launcher: instalação e requisitos

Guia para instalar e começar a usar o plugin Eclipse que sobe o servidor
[`mcp-abap-adt`](https://github.com/fr0ster/mcp-abap-adt) por ambiente SAP e o registra no
Claude Code.

---

## 1. Requisitos

### 1.1 Na sua máquina

| Item | Versão / observação | Como conferir |
|---|---|---|
| Windows 10/11 (também roda em Linux/macOS) | — | — |
| Eclipse IDE | 2024-03 ou mais novo | Help → About Eclipse IDE |
| ABAP Development Tools (ADT) | Instalado e **funcionando** antes do plugin | Abrir um projeto ABAP |
| Java do Eclipse | 17 ou superior (Eclipse 2025+ já usa 21) | `java -version` |
| Node.js e mcp-abap-adt | **Não precisa no Windows x64**: vêm embutidos no plugin (1.2+). Em outras plataformas: Node.js LTS + `npm install -g @mcp-abap-adt/core` | Configurações da view |
| Claude Code | Instalado e logado (necessário também para o botão ">_ Abrir Claude Code") | `claude --version` |

### 1.2 Só para compilar o plugin

Dispensável se você recebeu o `.zip` do update site pronto.

| Item | Versão |
|---|---|
| JDK | 17 ou superior (ex.: Eclipse Temurin 21) |
| Apache Maven | 3.9 ou superior, com o `bin` no PATH |
| Internet | Acesso a `download.eclipse.org` e ao Maven Central no primeiro build |

### 1.3 No sistema SAP

| Requisito | Onde verificar |
|---|---|
| Acesso de rede ao servidor (VPN do cliente, quando houver) | `Test-NetConnection <host> -Port <porta>` |
| Porta HTTP/HTTPS do ICM ativa (não é a entrada do SAP Logon) | SMICM → Goto → Services |
| Serviço `/sap/bc/adt` ativo | SICF |
| Usuário com autorização de desenvolvimento ADT | Objetos S_ADT_RES, S_DEVELOP (e S_TRANSPRT para transportes) |

### 1.4 Requisitos extras por forma de conexão

| Forma de conexão | O que precisa a mais |
|---|---|
| On-premise – usuário e senha (HTTP) | Nada além do item 1.3 |
| On-premise – RFC | SAP NW RFC SDK instalado + pré-requisitos do guia `RFC_SETUP.md` do projeto fr0ster |
| On-premise – certificado X.509 | Certificado de cliente (PEM ou PFX) mapeado a um usuário (CERTRULE); ICM aceitando certificado de cliente; URL **https** |
| On-premise – Kerberos/SPNEGO | Ticket Kerberos válido (`klist`), `npm i -g kerberos` e SPNEGO habilitado no SAP (NTLM não é aceito) |
| BTP / S/4HANA Cloud – service key | Arquivo JSON da service key (BTP Cockpit → instância ABAP → Service Keys) e navegador para o login |
| Arquivo .env próprio | O `.env` pronto, por exemplo gerado pelo `mcp-auth` (pacote `@mcp-abap-adt/auth-broker-cli`) |

---

## 2. Instalação

### Passo 1: servidor MCP

No **Windows x64**, pule este passo: a partir da 1.2, o plugin já traz o Node.js e o
`mcp-abap-adt`.

Em outras plataformas, ou para usar uma versão diferente do servidor:

```cmd
npm install -g @mcp-abap-adt/core
where mcp-abap-adt
```

Depois, em **Configurações**, escolha "Executável instalado na máquina" e informe o caminho.

### Passo 2: obter o update site do plugin

**Opção A: recebeu o zip pronto.** Pule para o passo 3.

**Opção B: compilar.** Descompacte `mcp-abap-adt-eclipse.zip` e, na pasta raiz:

```cmd
mvn clean verify
```

O update site sai em:

```
com.brainconsulting.adt.mcp.site\target\com.brainconsulting.adt.mcp.site-1.3.0-SNAPSHOT.zip
```

O primeiro build demora alguns minutos, porque baixa as dependências do Eclipse.

### Passo 3: instalar no Eclipse

> Confirme antes que o Eclipse e o ADT abrem normalmente. Instalar o plugin numa etapa
> separada facilita identificar a causa se algo der errado.

1. **Help → Install New Software… → Add… → Archive…** e selecione o zip do passo 2.
2. Marque **Brain Consulting → MCP ABAP ADT Launcher** → **Next** → **Finish**.
3. Aceite o aviso de conteúdo não assinado.
4. Reinicie o Eclipse.

### Passo 4: abrir a view

**Window → Show View → Other… → Brain Consulting → MCP ABAP ADT**

### Passo 5: configurações iniciais

No menu da view (▾), abra **Configurações…** e confira:

| Campo | Valor esperado |
|---|---|
| Servidor MCP | **Embutido no plugin** (mostra a versão incluída) |
| Argumentos base | `--transport=http --port=${port}` |
| Caminho do endpoint HTTP | `/mcp/stream/http` |
| Pasta das service keys | `%USERPROFILE%\Documents\mcp-abap-adt\service-keys` |
| Registrar no Claude Code | Marcado |

### Passo 6: cadastrar o primeiro ambiente

1. Clique em **+ Adicionar**.
2. Na aba **Conexão**, preencha o nome e escolha a **forma de conexão** e o **tipo de sistema**
   (On-premise ou Cloud). Os campos mudam conforme a escolha.
3. Na aba **Ferramentas**, mantenha os três grupos marcados para ter todas as ferramentas.
4. Salve e clique em **Testar conexão**. Só siga quando o resultado for positivo.
5. Clique em **▶ Iniciar** e acompanhe o log no Console do Eclipse.

### Passo 7: usar no Claude Code

O jeito mais simples é selecionar o ambiente na view e clicar em **>_ Abrir Claude Code**: o
plugin inicia o servidor, espera ele responder e abre o Claude Code na pasta de trabalho do
ambiente (aba **Claude Code** do cadastro).

Para usar uma sessão do Claude Code que você abriu por conta própria:

1. Abra uma **nova sessão** do Claude Code, ou use `/mcp` numa sessão aberta.
2. Confirme que o servidor (ex.: `sap-arezzo-dev`) aparece conectado em `/mcp`.
3. Faça um teste simples, como pedir para ler uma classe Z.

---

## 3. Checklist rápido

- [ ] Servidor MCP: "Embutido no plugin" disponível em **Configurações** (Windows x64)
- [ ] Eclipse e ADT abrem sem erro antes da instalação
- [ ] Plugin instalado e view **MCP ABAP ADT** visível
- [ ] Executável e endpoint conferidos em **Configurações**
- [ ] Ambiente cadastrado e **Testar conexão** com resultado positivo
- [ ] Servidor iniciado (status "● Rodando")
- [ ] Servidor visível em `/mcp` no Claude Code

---

## 4. Atualizar e desinstalar

**Atualizar:** instale o novo zip pelo mesmo caminho do passo 3. Ambientes e senhas são mantidos.

**Desinstalar:**
1. Pare todos os servidores na view. Isso remove as entradas do `~/.claude.json`.
2. Vá em **Help → About Eclipse IDE → Installation Details**, selecione o plugin e clique em
   **Uninstall**.
3. Opcional: apague `%USERPROFILE%\.brain-mcp-abap` (lista de ambientes).

---

## 5. Onde ficam os dados

| O quê | Onde |
|---|---|
| Ambientes e configurações (sem senhas) | `%USERPROFILE%\.brain-mcp-abap\environments.json` |
| Senhas e senha do .pfx | Secure Storage do Eclipse (criptografado) |
| `.env` temporário dos servidores em execução | `%USERPROFILE%\.brain-mcp-abap\run\` (apagado ao parar) |
| Service keys | `%USERPROFILE%\Documents\mcp-abap-adt\service-keys\` |
| Registro no Claude Code | `%USERPROFILE%\.claude.json` → `mcpServers` |

O `environments.json` não tem senhas e pode ser compartilhado com o time. Cada consultor coloca
o arquivo na mesma pasta e só digita a própria senha.

---

## 6. Problemas comuns na instalação

| Sintoma | O que fazer |
|---|---|
| `mvn` não é reconhecido | Instale o Maven e adicione a pasta `bin` ao PATH |
| Build falha baixando dependências | Verifique o acesso a `download.eclipse.org` (proxy corporativo) |
| Eclipse não abre depois de instalar | Rode `eclipse.exe -clean -consoleLog` e verifique o log em `configuration\*.log` |
| View não aparece na lista | Confirme a instalação em **Installation Details** e reinicie com `-clean` |
| "Executável não encontrado" ao iniciar | Ajuste o caminho em **Configurações** com o resultado do `where` |
| "Tempo esgotado ao conectar" no teste | VPN, porta do ICM (SMICM) ou firewall |
| Servidor não aparece no Claude Code | Abra uma nova sessão e confira o endpoint `/mcp/stream/http` |

Para detalhes sobre cada forma de conexão e diagnóstico, veja o `README.md` do projeto.

---

Brain Consulting — *Por pessoas, para pessoas, gerando inovação* — brainconsulting.info
