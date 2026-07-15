#🔎 Sistema Distribuído de Indexação e Pesquisa Web
### Java RMI • UDP Multicast • Spring Boot • WebSockets • OpenAI API

Este projeto consiste num **motor de pesquisa distribuído** (Googol) desenvolvido para a unidade curricular de **Sistemas Distribuídos** da **Universidade de Coimbra**.

O sistema replica vários conceitos utilizados em motores de pesquisa modernos, incluindo:

-  Web Crawling distribuído
-  Índice invertido replicado
-  Comunicação distribuída através de **Java RMI**
-  Replicação síncrona utilizando **Reliable Multicast (UDP)**
-  Balanceamento de carga e tolerância a falhas
-  Interface Web em **Spring Boot**
-  Atualizações em tempo real com **WebSockets**
-  Integração com APIs externas (**Hacker News** e **OpenAI**)

---

# Índice

- [Arquitetura](#-arquitetura)
- [Tecnologias](#-tecnologias-utilizadas)
- [Compilação](#-compilação)
- [Execução](#-execução)
- [Interface Web](#-interface-web)
- [Funcionalidades](#-funcionalidades)
- [Teste Rápido](#-teste-rápido)
- [Failover e Tolerância a Falhas](#-failover-e-tolerância-a-falhas)
- [Autores](#-autores)

---

# Arquitetura

O sistema encontra-se dividido em vários componentes independentes.

```text
                   +---------------------+
                   |      Cliente CLI    |
                   +----------+----------+
                              |
                              |
                   +----------v----------+
                   |    Spring Boot Web  |
                   +----------+----------+
                              |
                        Java RMI
                              |
                   +----------v----------+
                   |       Gateway       |
                   | Balanceamento RMI   |
                   +----------+----------+
                              |
          ---------------------------------------------
          |                                           |
+---------v---------+                     +-----------v----------+
|   Storage Barrel  | <---- Multicast --->|   Storage Barrel    |
|     (Replica)     |        UDP          |      (Replica)      |
+---------+---------+                     +-----------+----------+
          ^                                           ^
          |                                           |
          +----------------- Crawlers ----------------+
```

---

## Downloaders (Web Crawlers)

Os Crawlers executam em paralelo para aumentar o desempenho do sistema.

Responsabilidades:

- descarregar páginas HTML;
- extrair texto utilizando **Jsoup**;
- identificar títulos;
- extrair citações;
- recolher hiperligações;
- adicionar novos URLs à fila de indexação.

---

## Storage Barrels

São os servidores responsáveis pelo armazenamento do índice invertido.

Características:

- índice invertido (`HashMap<String, HashSet<String>>`);
- replicação síncrona;
- comunicação via **Reliable Multicast UDP**;
- tolerância a falhas;
- consistência entre réplicas.

---

## Gateway

A Gateway constitui o ponto de entrada do sistema.

Responsabilidades:

- receber pedidos RMI;
- distribuir pesquisas pelos Barrels;
- balanceamento de carga;
- monitorização do cluster;
- failover automático.

---

## Web Server (Spring Boot)

A interface Web funciona como cliente RMI do sistema distribuído.

Disponibiliza:

- interface gráfica;
- páginas Thymeleaf;
- WebSockets;
- integração com APIs REST.

---

# Tecnologias Utilizadas

- Java
- Java RMI
- UDP Multicast
- Spring Boot
- Spring MVC
- Thymeleaf
- WebSockets
- Jsoup
- OpenAI API
- Hacker News API

---

# Compilação

O projeto inclui um script que compila automaticamente todas as classes e gera a documentação Javadoc.

```bash
.\Compilar.bat
```

---

# ▶ Execução

A ordem de execução é obrigatória.

## 1. Gateway

```bash
java -cp "target/classes;.;lib/jsoup-1.21.2.jar" search.GatewayImp
```

---

## 2. Storage Barrels

Inicie pelo menos **dois** Barrels em terminais diferentes para validar a replicação.

```bash
java -cp "target/classes;.;lib/jsoup-1.21.2.jar" search.MainStorageBarrel
```

---

## 3. Crawlers

```bash
java -cp "target/classes;.;lib/jsoup-1.21.2.jar" search.Crawler https://example.com Crawler1
```

### Argumentos

| Argumento | Descrição |
|-----------|-----------|
| `arg[0]` | URL inicial |
| `arg[1]` | Nome do crawler |

---

## 4. Cliente CLI

```bash
java -cp "target/classes;.;lib/jsoup-1.21.2.jar" search.ClientImp
```

---

# Interface Web

Antes de iniciar a aplicação Web, todos os serviços da Meta 1 devem estar ativos.

Execute:

```bash
java -cp "target/classes;..." search.ServingWebContentApplication
```

A aplicação ficará disponível em:

```
http://localhost:9090
```

---

## Exposição Pública

O projeto utiliza **Cloudflare Tunnels (cloudflared)** para disponibilizar o servidor Web através da Internet sem necessidade de abrir portas no router.

---

# Funcionalidades

## Pesquisa

- pesquisa por palavras-chave;
- índice invertido;
- paginação;
- resultados ordenados por relevância.

---

## Indexação

Permite adicionar novos URLs manualmente para indexação imediata.

---

## Backlinks

Consulta todas as páginas que apontam para um determinado URL.

---

## Estatísticas

Atualização em tempo real através de:

- Callbacks RMI
- WebSockets

Inclui:

- Top 10 pesquisas;
- Barrels ativos;
- tamanho do índice;
- tempo médio de resposta.

---

## APIs Externas

### Hacker News API

Permite indexar automaticamente URLs encontrados nas Top Stories.

### OpenAI API

Gera resumos e análises contextuais sobre os resultados devolvidos pelo motor de pesquisa.

---

# Teste Rápido

1. Iniciar Gateway
2. Iniciar dois Storage Barrels
3. Iniciar um Crawler
4. Iniciar o Cliente ou a Interface Web

Testar:

- Indexar URL
- Pesquisar palavra
- Consultar backlinks
- Visualizar estatísticas

Exemplo de saída:

```text
=== Resultados ===

Título: Example Domain

URL:
https://example.com

Citação:
This domain is for use in illustrative examples...

[OK] Callback recebido

[OK] Barrel replicado via Multicast
```

---

# Failover e Tolerância a Falhas

## RMI

Caso ocorra:

```text
java.rmi.ConnectException
```

Execute a aplicação indicando o endereço da máquina:

```bash
-Djava.rmi.server.hostname="IP_DA_MÁQUINA"
```

---

## Multicast

É necessário que a rede suporte **UDP Multicast**.

TTL utilizado:

```
2
```

---

## Recuperação Automática

Caso um Storage Barrel fique indisponível durante uma pesquisa:

- a Gateway deteta a falha;
- ocorre uma `RemoteException`;
- o pedido é automaticamente reenviado para outro Barrel ativo;
- o utilizador não necessita repetir a pesquisa.

---

# Autores

| Nome | Nº | Email |
|------|------|------|
| Pedro Ferreira | 2023112367 | uc2023112367@student.uc.pt |
| Lorando Ca | 2022195277 | uc2022195277@student.uc.pt |

---

# Contexto Académico

Projeto desenvolvido para a unidade curricular de **Sistemas Distribuídos** da **Universidade de Coimbra**.
