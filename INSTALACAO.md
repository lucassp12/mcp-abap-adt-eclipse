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

### 1.2 No sistema SAP

| Requisito | Onde verificar |
|---|---|
| Acesso de rede ao servidor (VPN do cliente, quando houver) | `Test-NetConnection <host> -Port <porta>` |
| Porta HTTP/HTTPS do ICM ativa (não é a entrada do SAP Logon) | SMICM → Goto → Services |
| Serviço `/sap/bc/adt` ativo | SICF |
| Usuário com autorização de desenvolvimento ADT | Objetos S_ADT_RES, S_DEVELOP (e S_TRANSPRT para transportes) |

### 1.3 Requisitos extras por forma de conexão

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

### Passo 2: instalar no Eclipse

> Confirme antes que o Eclipse e o ADT abrem normalmente. Instalar o plugin numa etapa
> separada facilita identificar a causa se algo der errado.

O plugin é instalado pelo update site publicado no GitHub Pages:

```
https://lucassp12.github.io/mcp-abap-adt-eclipse/
```

1. No Eclipse, vá em **Help → Install New Software… → Add…**.
2. Em **Name**, digite `MCP ABAP ADT Launcher`. Em **Location**, cole o endereço acima e clique em **Add**.
3. Marque **Brain Consulting → MCP ABAP ADT Launcher**, clique em **Next** e depois em **Finish**.
4. Aceite o aviso de conteúdo não assinado.
5. Reinicie o Eclipse.

### Passo 3: abrir a view

**Window → Show View → Other… → Brain Consulting → MCP ABAP ADT**

### Passo 4: configurações iniciais

Clique com o botão direito na tabela da view (ou use o menu ⋮ da view), abra **Configurações…** e confira:

| Campo | Valor esperado |
|---|---|
| Servidor MCP | **Embutido no plugin** (mostra a versão incluída) |
| Argumentos base | `--transport=http --port=${port}` |
| Caminho do endpoint HTTP | `/mcp/stream/http` |
| Pasta das service keys | `%USERPROFILE%\Documents\mcp-abap-adt\service-keys` |
| Registrar no Claude Code | Marcado |

### Passo 5: cadastrar o primeiro ambiente

1. Clique em **+ Adicionar**.
2. Na aba **Conexão**, preencha o nome e escolha a **forma de conexão** e o **tipo de sistema**
   (On-premise ou Cloud). Os campos mudam conforme a escolha.
3. Na aba **Ferramentas**, mantenha o padrão **Leitura + Alto nível**. Alto e baixo nível não podem ser
   usados juntos: o servidor recusa essa combinação.
4. Salve e clique em **Testar conexão**. Só siga quando o resultado for positivo.
5. Clique em **▶ Iniciar** e acompanhe o log no Console do Eclipse.

### Passo 6: usar no Claude Code

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
- [ ] Endpoint `/mcp/stream/http` conferido em **Configurações**
- [ ] Ambiente cadastrado e **Testar conexão** com resultado positivo
- [ ] Servidor iniciado (status "● Rodando")
- [ ] Servidor visível em `/mcp` no Claude Code

---

## 4. Atualizar e desinstalar

**Atualizar:** use **Help → Check for Updates**. Ambientes e senhas são mantidos. Se o Eclipse
disser que não há atualizações logo depois de uma publicação, vá em **Window → Preferences →
Install/Update → Available Software Sites**, selecione o site e clique em **Reload**.

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
| "No repository found" ao adicionar o site | Confira o endereço (com `/` no final, sem `index.html`) e se ele abre no navegador; depois use **Reload** em **Available Software Sites** |
| Eclipse não encontra atualização | **Reload** do site em **Available Software Sites** e tente de novo |
| Eclipse não abre depois de instalar | Rode `eclipse.exe -clean -consoleLog` e verifique o log em `configuration\*.log` |
| View não aparece na lista | Confirme a instalação em **Installation Details** e reinicie com `-clean` |
| "Servidor embutido não disponível" | O servidor embutido é só para Windows x64; em outras plataformas, escolha "Executável instalado na máquina" em **Configurações** |
| "Claude Code não encontrado" | Rode `where.exe claude` no PowerShell e informe o caminho em **Configurações → Executável do claude** |
| "Tempo esgotado ao conectar" no teste | VPN, porta do ICM (SMICM) ou firewall |
| Servidor não aparece no Claude Code | Abra uma nova sessão e confira o endpoint `/mcp/stream/http` |

Para detalhes sobre cada forma de conexão e diagnóstico, veja o `README.md` do projeto.

---

Brain Consulting — *Por pessoas, para pessoas, gerando inovação* — brainconsulting.info
