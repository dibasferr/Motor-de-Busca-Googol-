# Sistema Distribuído de Indexação e Pesquisa Web (RMI + Multicast)

##  Compilação

### Compilar usando o ficheiro Compilar.bat
```bash
.\Compilar.bat
```

---

## Execução dos Componentes

A execução deve seguir a **ordem obrigatória**:

### 1️ Iniciar o Gateway (servidor RMI principal)
```bash
java -cp "target/classes;.;target/lib/jsoup-1.21.2.jar" search.GatewayImp
```

### 2️ Iniciar um ou mais Storage Barrels
```bash
java -cp "target/classes;.;target/lib/jsoup-1.21.2.jar" search.MainStorageBarrel
```

### 3️ Iniciar Crawler(s)
```bash
java -cp "target/classes;.;target/lib/jsoup-1.21.2.jar" search.Crawler https://example.com Crawler1
```
> Argumentos:  
> `arg[0]` = URL inicial  
> `arg[1]` = Nome do Crawler  

### 4️ Iniciar Cliente(s)
```bash
java -cp "target/classes;.;target/lib/jsoup-1.21.2.jar" search.ClientImp
```

---

## Gerar Relatório Javadoc

Já está implementado o código da criação do Javadoc no ficheiro de compilação "Compilar.bat".

---

## Como validar o sistema (teste rápido)

1. Iniciar Gateway  
2. Iniciar **2 Barrels** em janelas separadas  
3. Iniciar um `Crawler` com uma URL conhecida  
4. Iniciar um `Client`  
5. No menu do cliente, testar:

```
1) Indexar URL
2) Pesquisar palavra
3) Consultar páginas que apontam para uma URL
4) Estatísticas distribuídas (top10, barrels ativos, tempo médio)
```

Se tudo estiver correto, deverá aparecer algo como:

```
=== Resultados da pesquisa ===
Título: Example Domain
URL: https://example.com
Citação: This domain is for use in illustrative examples...

[OK] Callback recebido com estatísticas globais
[OK] Barrel replicado via multicast
```

---

## Créditos / Autores
```
Pedro Ferreira - 2023112367 - uc2023112367@student.uc.pt  
Lorando Ca - 2022195277 - uc2022195277@student.uc.pt
```

---

## Notas importantes

- O sistema usa **RMI**, logo todos os processos devem estar na **mesma rede** ou ter IP público configurado.
- O Barrel usa **Multicast UDP**, que deve estar ativado na rede (TTL configurado para 2).
- Caso o erro `java.rmi.ConnectException` apareça, atualizar o IP no código-fonte ou com `-Djava.rmi.server.hostname`.