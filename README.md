# MCP ABAP ADT Launcher — plugin Eclipse

View no Eclipse para cadastrar ambientes SAP e, com um clique, subir o servidor
`mcp-abap-adt` (instalado via npm) de cada ambiente, já registrado no Claude Code.

A view mostra uma tabela com Status, Ambiente, URL SAP, Mandante, Usuário, Porta local e
o nome do servidor no Claude Code, e uma barra com **▶ Iniciar**, **■ Parar**,
**Testar conexão**, **+ Adicionar**, **Editar** e **Remover**.

## O que ele faz

- Cadastra ambientes (URL HTTP, mandante, usuário, idioma, porta local). A senha fica
  criptografada no **Secure Storage do Eclipse**, nunca em arquivo texto.
- **▶ Iniciar** sobe um processo `mcp-abap-adt --transport=http --port <porta>` com as
  credenciais em variáveis de ambiente (`SAP_URL`, `SAP_CLIENT`, `SAP_USERNAME`,
  `SAP_PASSWORD`, `SAP_AUTH_TYPE=basic`, `SAP_LANGUAGE`). O log aparece no Console do
  Eclipse, com a senha mascarada.
- Registra o servidor em `~/.claude.json` → `mcpServers` como
  `{"type":"http","url":"http://localhost:<porta>/mcp"}`, e remove ao parar.
- **■ Parar** encerra a árvore inteira de processos (`cmd.exe` + `node.exe`), para não
  deixar porta presa. Fechar o Eclipse para todos os servidores.
- **Testar conexão** chama `/sap/bc/adt/discovery` e explica o resultado: timeout (VPN,
  porta, firewall), DNS, porta fechada, http × https, 401, 403, 404 (SICF).
- Vários ambientes podem rodar ao mesmo tempo, cada um na sua porta.

## Requisitos

- Eclipse 2024-03 ou mais novo (testado como alvo de compilação: 2024-03), Java 17+.
- `mcp-abap-adt` instalado globalmente via npm
  (padrão: `%APPDATA%\npm\mcp-abap-adt.cmd`).
- Para compilar: JDK 17+ e Maven 3.9+.

## Compilar

Na pasta raiz do projeto:

```cmd
mvn clean verify
```

O primeiro build baixa as dependências do Eclipse (alguns minutos). O update site sai em:

- `com.brainconsulting.adt.mcp.site\target\repository\` (pasta)
- `com.brainconsulting.adt.mcp.site\target\com.brainconsulting.adt.mcp.site-1.0.0-SNAPSHOT.zip` (zip)

## Instalar no Eclipse

1. **Help → Install New Software… → Add… → Archive…** e escolha o `.zip` acima
   (ou **Local…** e escolha a pasta `repository`).
2. Marque **Brain Consulting → MCP ABAP ADT Launcher**, Next, Finish.
3. Aceite o aviso de conteúdo não assinado e reinicie o Eclipse.
4. **Window → Show View → Other… → Brain Consulting → MCP ABAP ADT**.

> Dica, depois do susto com o ASM: instale este plugin numa etapa separada, depois do
> ADT já estar funcionando. Ele não traz nenhuma biblioteca de terceiros (o JSON é
> tratado por código próprio), então não disputa versão com outros plugins.

## Usar

1. Na view, abra **Configurações…** (menu da view, o triângulo ▾) e confira o caminho do
   `mcp-abap-adt.cmd`. Para descobrir: `where mcp-abap-adt` no prompt.
2. **+ Adicionar** um ambiente. Use a URL **HTTP do ICM** (SMICM → Goto → Services),
   não a do SAP Logon: o ADT clássico conecta via RFC, mas o MCP usa HTTP.
3. **Testar conexão**. Só siga quando der "Conexão OK".
4. **▶ Iniciar**. O Console mostra o log do servidor e o nome registrado no Claude Code.
5. Abra uma **nova sessão do Claude Code** (ou rode `/mcp`) para ele carregar o servidor.

## Onde ficam os dados

| O quê | Onde | Observação |
|---|---|---|
| Ambientes e configurações | `~/.brain-mcp-abap/environments.json` | Sem senhas. Sobrevive a reinstalação do Eclipse; pode ser compartilhado com o time. |
| Senhas | Secure Storage do Eclipse (`~/.eclipse/org.eclipse.equinox.security`) | Criptografadas, por usuário do Windows. |
| Registro no Claude Code | `~/.claude.json` → `mcpServers` | Cópia de segurança na primeira alteração: `~/.claude.json.brain-mcp.bak`. |

## Ajustes para a sua versão do mcp-abap-adt

Existem forks diferentes do `mcp-abap-adt` (fr0ster / `@mcp-abap-adt/core`,
mario-andreschak...). Em **Configurações…** dá para adaptar sem recompilar:

- **Argumentos**: padrão `--transport=http --port ${port}`. Confira com
  `mcp-abap-adt --help`. Placeholders: `${port}` e `${name}`.
- **Caminho do endpoint HTTP**: padrão `/mcp`. Na primeira subida, veja no Console a URL
  que o servidor anuncia e ajuste se for diferente.
- Se a sua versão só funciona em `stdio`, ela não serve para este modelo (um processo
  rodando no Eclipse e atendendo o Claude Code por HTTP). Nesse caso, configure-a direto
  no `~/.claude.json` com `"command"`, sem o plugin.

## Distribuir para o time

1. Publique a pasta `target/repository` num local acessível (pasta de rede, GitHub Pages,
   servidor interno) e passe a URL para o time usar em **Install New Software**.
2. Opcional: compartilhe um `environments.json` modelo (sem senhas) com os ambientes dos
   clientes; cada consultor coloca o arquivo em `~/.brain-mcp-abap/` e só digita a senha.

## Solução de problemas

| Sintoma | Causa provável |
|---|---|
| "Tempo esgotado ao conectar" | VPN desligada, porta errada, ou firewall. Confira a porta na SMICM. |
| "Falha na negociação TLS/SSL" | `https://` numa porta HTTP (ou o contrário); ou certificado interno (marque "ignorar validação", só DEV/QAS). |
| "A porta ... já está em uso" | Outro ambiente ou processo usando a porta local; mude a porta do ambiente. |
| Processo "terminou sozinho" | Leia o Console: argumento não aceito pela sua versão, credencial inválida etc. |
| Claude Code não vê o servidor | Abra uma nova sessão ou use `/mcp`; confira a URL do endpoint. |

## Estrutura

```
pom.xml                                  build Tycho (Eclipse 2024-03 como alvo)
com.brainconsulting.adt.mcp/             o plugin
  src/.../Activator.java                 ciclo de vida; para tudo ao fechar o Eclipse
  src/.../model/                         Environment, Settings, EnvironmentStore (JSON + Secure Storage)
  src/.../server/ServerManager.java      sobe/derruba processos, Console, árvore de processos
  src/.../server/ConnectionTester.java   teste e diagnóstico de conexão ADT
  src/.../claude/ClaudeConfig.java       edita ~/.claude.json de forma atômica, com backup
  src/.../json/                          parser JSON próprio (sem dependências)
  src/.../ui/                            view e diálogos
com.brainconsulting.adt.mcp.feature/     feature para instalação via p2
com.brainconsulting.adt.mcp.site/        update site
```

---

Brain Consulting — *Por pessoas, para pessoas, gerando inovação* — brainconsulting.info
