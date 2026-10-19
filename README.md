# MCP ABAP ADT Launcher — plugin Eclipse

View no Eclipse para cadastrar ambientes SAP, **on-premise e cloud**, e subir com um clique o
servidor [`mcp-abap-adt` (fr0ster)](https://github.com/fr0ster/mcp-abap-adt) de cada um, com
**todas as ferramentas** liberadas e já registrado no Claude Code.

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

- Eclipse 2024-03 ou mais novo, Java 17+.
- `npm install -g @mcp-abap-adt/core` (padrão: `%APPDATA%\npm\mcp-abap-adt.cmd`).
- Para compilar o plugin: JDK 17+ e Maven 3.9+.

## Compilar e instalar

```cmd
mvn clean verify
```

1. Vá em **Help → Install New Software… → Add… → Archive…** e escolha
   `com.brainconsulting.adt.mcp.site\target\com.brainconsulting.adt.mcp.site-1.1.0-SNAPSHOT.zip`.
2. Marque **Brain Consulting → MCP ABAP ADT Launcher**, avance e reinicie o Eclipse.
3. Abra **Window → Show View → Other… → Brain Consulting → MCP ABAP ADT**.

Para atualizar a partir da 1.0, instale da mesma forma. Os ambientes e senhas são mantidos.

## Usar

1. **Configurações…** (menu ▾ da view): confira o executável (`where mcp-abap-adt`).
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
