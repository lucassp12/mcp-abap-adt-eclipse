# MCP ABAP ADT Launcher — plugin Eclipse

View no Eclipse para cadastrar ambientes SAP, **on-premise e cloud**, e subir com um clique o
servidor [`mcp-abap-adt` (fr0ster)](https://github.com/fr0ster/mcp-abap-adt) de cada um, com
**todas as ferramentas** liberadas e já registrado no Claude Code.

## O que mudou na 1.3.1

- O botão **>_ Abrir Claude Code** encontra o `claude` mesmo quando o Eclipse foi aberto antes da
  instalação do Claude Code. O plugin procura no PATH do Eclipse, no PATH atual do Windows (lido
  do registro) e nas pastas padrão (`%USERPROFILE%\.local\bin`, `%APPDATA%\npm`). O terminal
  aberto também recebe o PATH atualizado.
- Novo campo opcional **Executável do claude** em Configurações, para os casos em que o Claude
  Code está numa pasta fora do padrão.

## O que mudou na 1.3

- **Botão ">_ Abrir Claude Code"** na view: com um ambiente selecionado, inicia o servidor dele
  (se ainda não estiver rodando), espera o servidor responder e abre o Claude Code já com o
  `/mcp` conectado. Sem ambiente selecionado, abre na pasta do usuário.
- **Onde abrir** (Configurações): janela do **Windows Terminal** (padrão; sem ele, usa o console
  do Windows) ou a **view Terminal do Eclipse**. Se o Terminal do Eclipse não estiver disponível
  na instalação, o plugin usa a janela externa automaticamente.
- **Pasta de trabalho por ambiente** (aba **Claude Code** do cadastro): por exemplo, a pasta do
  projeto do cliente, para o Claude ler as especificações e o `CLAUDE.md` de lá.
- **Argumentos do claude** (Configurações), por exemplo `--continue` para retomar a última
  conversa.
- Requer o Claude Code instalado (`claude --version` no prompt). Ele não é embutido no plugin:
  a licença não permite redistribuir.

## O que mudou na 1.2

- **Servidor embutido:** no Windows x64, o plugin já traz o Node.js e o `mcp-abap-adt`. Não é
  preciso instalar Node, npm nem o servidor. As versões ficam em `embedded-runtime.properties`
  (`server.version=auto` usa a mais recente publicada há pelo menos 14 dias) e aparecem em
  **Configurações** e no Console ao iniciar.
- A opção "Executável instalado na máquina" continua disponível em **Configurações**, para
  usar outra versão do servidor ou outra plataforma.

### Licenças do servidor embutido

O `@mcp-abap-adt/core` é distribuído sob **AGPL-3.0**, e várias dependências sob LGPL-3.0.
O plugin roda o servidor como processo separado, sem modificá-lo, e inclui em
`runtime/THIRD-PARTY.txt` a lista de licenças e o endereço do código-fonte correspondente.
Antes de distribuir o update site fora da Brain, valide com o jurídico. Para uso interno,
mantenha o site em local de acesso restrito (VPS com acesso controlado ou rede interna).

## O que mudou na 1.1

- **Grupos de ferramentas por ambiente:** `readonly` + `high` (padrão) ou `readonly` + `low`.
  O servidor recusa `high` e `low` juntos.
- **Todas as formas de conexão do servidor:** usuário/senha HTTP, RFC, certificado X.509 (PEM ou
  PFX), Kerberos/SPNEGO, service key do BTP (login no navegador) e `.env` próprio.
- **Tipo de sistema** (`SAP_SYSTEM_TYPE` = onprem/cloud), que decide quais ferramentas o servidor
  oferece.
- **Teste de conexão específico** para cada forma de conexão.
- **Padrões corrigidos para o fr0ster:** endpoint `/mcp/stream/http` e `--port=N`. Configurações
  salvas pela 1.0 são migradas sozinhas.

## Formas de conexão

| No cadastro | Para quê | O que o plugin passa ao servidor |
|---|---|---|
| On-premise – usuário e senha (HTTP) | ECC / S/4HANA on-premise | `.env` temporário com `SAP_AUTH_TYPE=basic` |
| On-premise – RFC | Sistemas em que o ADT via HTTP não atende; requer SAP NW RFC SDK | idem + `SAP_CONNECTION_TYPE=rfc` e `--connection-type=rfc` |
| On-premise – certificado X.509 | SSO por certificado de cliente (mTLS), PEM ou PFX | `SAP_AUTH_TYPE=certificate` + caminhos do certificado |
| On-premise – Kerberos/SPNEGO | SSO Windows (requer `npm i -g kerberos` e ticket válido) | `SAP_AUTH_TYPE=kerberos` (+ SPN opcional) |
| BTP / S/4HANA Cloud – service key | ABAP Environment (Steampunk) e similares | `--mcp=<destino>`; o login acontece no navegador |
| Arquivo .env próprio | JWT gerado pelo `mcp-auth`, SNC ou qualquer coisa nova do servidor | `--env-path=<arquivo>` |

Para os métodos por URL (usuário/senha, RFC, certificado, Kerberos), o plugin gera um `.env` em
`~/.brain-mcp-abap/run/`, que é a forma documentada pelo servidor. Esse arquivo tem permissão
só para o seu usuário, e é apagado quando o servidor para e também na próxima abertura do
Eclipse, se ele tiver sido fechado de forma abrupta. As mesmas variáveis também vão no ambiente
do processo.

**Service key (BTP):** no cadastro, use **Importar service key...**. O JSON é copiado para a pasta
de destinos do servidor (Windows: `%USERPROFILE%\Documents\mcp-abap-adt\service-keys\<DESTINO>.json`).
Na primeira subida, o servidor abre o navegador para o login no BTP. Para não precisar logar de
novo a cada reinício, marque **Lembrar o login**, que liga a opção `--unsafe` do servidor e grava
os tokens em disco. Cada ambiente BTP usa a sua própria porta de retorno do login; o padrão
começa em 61001.

**Legacy (BASIS < 7.50):** o próprio projeto fr0ster marca esse suporte como suspenso no momento.
Para ECC antigo, cadastre como on-premise. As ferramentas que dependem de serviços ADT
inexistentes no sistema vão falhar com erro do SAP.

## Ferramentas

Na aba **Ferramentas** do cadastro:

- **Leitura (readonly):** fonte, where-used, metadados, estrutura, pacotes, transportes, conteúdo
  de tabelas, SQL, dumps, traces, AST e análise semântica.
- **Alto nível (high):** criar, alterar, ativar e excluir classes, programas, DDIC, CDS, RAP,
  transportes, ABAP Unit e ATC.
- **Baixo nível (low):** operações ADT passo a passo (lock/unlock, check, validate, updates
  granulares).

`high` e `low` são mutuamente exclusivos no mcp-abap-adt: o servidor não sobe com os dois
(`'high' and 'low' are mutually exclusive`). O padrão é `--exposition=readonly,high`, que cobre
leitura e todo o ciclo de criar, alterar e ativar objetos. Busca e informações de sistema vêm
sempre. O **Tipo de sistema** filtra o que não existe no
ambiente (por exemplo, programas não aparecem em ABAP Cloud).

A fachada "compact" (22 ferramentas genéricas) é outro comando, `mcp-abap-adt-compact`. Para
usá-la, aponte o executável para ele em **Configurações**.

## Requisitos

- Eclipse 2024-03 ou mais novo, com Java 17+.
- Windows x64: nada além do plugin (Node.js e servidor embutidos).
- Outras plataformas, ou para usar outra versão do servidor: `npm install -g @mcp-abap-adt/core`.
- Claude Code instalado, para o botão **>_ Abrir Claude Code** (`claude --version`).

## Instalar

O plugin é instalado pelo update site publicado no GitHub Pages:

```
https://lucassp12.github.io/mcp-abap-adt-eclipse/
```

1. No Eclipse, vá em **Help → Install New Software… → Add…**.
2. Em **Name**, digite `MCP ABAP ADT Launcher`. Em **Location**, cole o endereço acima e clique em **Add**.
3. Marque **Brain Consulting → MCP ABAP ADT Launcher**, clique em **Next** e depois em **Finish**.
4. Aceite o aviso de conteúdo não assinado e reinicie o Eclipse.
5. Abra a view em **Window → Show View → Other… → Brain Consulting → MCP ABAP ADT**.

## Atualizar

Com o site cadastrado, use **Help → Check for Updates**. Os ambientes e as senhas são mantidos.

Se o Eclipse disser que não há atualizações logo depois de uma publicação, vá em
**Window → Preferences → Install/Update → Available Software Sites**, selecione o site e clique
em **Reload**.

## Publicar uma versão nova (mantenedores)

O build e a publicação são automáticos: cada push na branch `main` dispara o GitHub Actions,
que compila o plugin e atualiza o update site. Para publicar:

1. Suba a versão em `pom.xml` (raiz e módulos), nos dois `MANIFEST.MF` e no `feature.xml`.
2. Faça commit, crie a tag `vX.Y.Z` e faça push.
3. Acompanhe a aba **Actions** até o build ficar verde.

## Usar

1. **Configurações…** (botão direito na tabela, ou menu ⋮ da view): confira se **Servidor MCP**
   está como **Embutido no plugin**.
2. **+ Adicionar**: escolha a forma de conexão e preencha os campos que aparecem.
3. **Testar conexão**: o diagnóstico depende do método (login, certificado, Negotiate, service
   key, `.env`).
4. **▶ Iniciar**: o Console mostra conexão, tipo de sistema, ferramentas e o log do servidor.
5. Abra uma **nova sessão do Claude Code** (ou use `/mcp`).

## Onde ficam os dados

| O quê | Onde |
|---|---|
| Ambientes e configurações (sem senhas) | `~/.brain-mcp-abap/environments.json` |
| Senhas e senha do .pfx | Secure Storage do Eclipse (criptografado) |
| `.env` temporário dos servidores em execução | `~/.brain-mcp-abap/run/` (só o dono; apagado ao parar) |
| Service keys | pasta de destinos do mcp-abap-adt (configurável) |
| Registro no Claude Code | `~/.claude.json` → `mcpServers` (backup `.brain-mcp.bak`) |

## Avançado

Na aba **Avançado** de cada ambiente:

- **Argumentos extras**, por exemplo `--auth-broker-path=...` ou `--browser=none`.
- **Variáveis extras** no formato `CHAVE=valor`, por exemplo `AUTH_LOG_LEVEL=debug` ou
  `DEBUG_CONNECTORS=true`. Elas prevalecem sobre as geradas pelo plugin.

Assim você usa opções novas do servidor (que evolui rápido) sem esperar uma versão do plugin.

## Solução de problemas

| Sintoma | Causa provável |
|---|---|
| Teste: "Tempo esgotado ao conectar" | VPN, porta ou firewall. Confira a porta na SMICM. |
| Teste: "Falha TLS/SSL" | `https` numa porta HTTP (ou o contrário), ou certificado interno. |
| Kerberos: "oferece NTLM" | O ICM não oferece SPNEGO para o usuário; peça ao Basis. |
| Certificado: HTTP 401/403 | Certificado não mapeado (CERTRULE) ou o ICM não pede certificado de cliente. |
| Service key: login não abre | Porta de retorno ocupada, ou `--browser=none` nos extras. |
| Processo "terminou sozinho" | Leia o Console: argumento não aceito pela versão instalada, credencial etc. |
| Claude não vê o servidor | Nova sessão do Claude Code ou `/mcp`; confira o endpoint em Configurações. |

---

Brain Consulting — *Por pessoas, para pessoas, gerando inovação* — brainconsulting.info
