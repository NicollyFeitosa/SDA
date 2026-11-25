# Simulador de Sistema de Arquivos com Journaling

**Aluna:** Nicolly Feitosa  ·  **Disciplina:** Sistemas Operacionais  ·  **Professor:** &lt;nome do professor&gt;

**Repositório:** https://github.com/NicollyFeitosa/SDA

## Resumo

Este trabalho apresenta um simulador de sistema de arquivos escrito em Java, com journaling. Todo o sistema de arquivos vive dentro de um único arquivo, `disco.img`, que faz o papel de um disco físico de 512 KB dividido em blocos de 512 bytes. Sobre esse disco foram implementados superbloco, bitmaps de espaço livre, tabela de inodes e diretórios, além de um journal do tipo *write-ahead logging* físico. Um Shell permite copiar, apagar e renomear arquivos, criar, apagar e renomear diretórios e listar o conteúdo de diretórios. O comando `crash` derruba o programa no meio de uma operação e, ao reiniciar, o journal conclui a operação interrompida, demonstrando na prática como o journaling mantém o sistema de arquivos consistente.

## Introdução

O sistema de arquivos é a parte do sistema operacional que transforma um disco, que para o hardware é apenas uma sequência de blocos numerados, em algo que usuários e programas conseguem usar: arquivos com nome, organizados em diretórios. É ele que decide onde cada dado fica gravado, controla o espaço livre e garante que as informações continuem corretas mesmo quando o computador desliga de repente. Por isso o gerenciamento de arquivos é um dos serviços centrais de qualquer sistema operacional.

## Objetivo

Desenvolver em Java um simulador de sistema de arquivos com journaling, que crie um arquivo que simula o sistema de arquivos e permita:

1. copiar arquivos;
2. apagar arquivos;
3. renomear arquivos;
4. criar diretórios;
5. apagar diretórios;
6. renomear diretórios;
7. listar o conteúdo de um diretório.

## Metodologia

- Java 17 ou superior, sem bibliotecas externas. Compila com `javac`.
- O simulador roda sobre o arquivo `disco.img` (disco virtual). A única API de disco usada é `RandomAccessFile`, lendo e gravando blocos de 512 bytes.
- Cada comando do Shell chama um método público da classe `FileSystemSimulator`.
- Cada método que altera o disco abre uma transação, faz o trabalho em memória e só então grava tudo no disco pelo journal (commit).

---

## Parte 1: Introdução ao Sistema de Arquivos com Journaling

### 1.1 O que é um sistema de arquivos e por que é importante

Um sistema de arquivos é a forma de organizar dados em um dispositivo de armazenamento. O disco é dividido em **blocos** de tamanho fixo, e o sistema de arquivos guarda dois tipos de informação:

- **Dados:** o conteúdo dos arquivos.
- **Metadados:** informações sobre os dados (tamanho, datas, onde estão os blocos, nomes).

As principais estruturas de metadados são:

| Estrutura | Função |
| --- | --- |
| Superbloco | "Ficha técnica" do disco: tamanho do bloco, quantidade de blocos e inodes, onde começa cada região. |
| Inode | Descreve um arquivo ou diretório: tipo, tamanho, datas e a lista de blocos onde estão os dados. Não guarda o nome. |
| Diretório | Arquivo especial que contém uma tabela de pares (nome, número do inode). É aqui que o nome mora. |
| Bitmap | Um bit por bloco (ou por inode): 0 = livre, 1 = ocupado. Usado para achar espaço livre rapidamente. |

Sem um sistema de arquivos, cada programa teria que saber em qual setor do disco gravou cada informação, não haveria nomes, nem proteção contra um programa sobrescrever os dados de outro.

### 1.2 Métodos de alocação

| Método | Como funciona | Vantagens | Desvantagens |
| --- | --- | --- | --- |
| Contígua | O arquivo ocupa blocos seguidos. | Leitura sequencial muito rápida; só precisa guardar início e tamanho. | Fragmentação externa; difícil aumentar o arquivo. |
| Encadeada | Cada bloco aponta para o próximo (ex.: FAT). | Sem fragmentação externa; arquivo cresce fácil. | Acesso aleatório lento (precisa percorrer a lista); um ponteiro corrompido perde o resto do arquivo. |
| Indexada | Um bloco de índice (o inode) lista todos os blocos do arquivo. | Acesso aleatório direto; sem fragmentação externa. | Gasta espaço com o índice; tamanho máximo limitado pelo número de ponteiros (resolvido com blocos indiretos). |

**Escolha do simulador: alocação indexada.** Cada inode tem 10 ponteiros diretos para blocos. É o modelo do ext2/ext3/ext4 (sem os blocos indiretos), permite acessar qualquer bloco diretamente e combina bem com o bitmap, que encontra blocos livres em qualquer posição do disco.

### 1.3 Journaling: propósito e funcionamento

Uma operação simples como criar um diretório altera vários blocos: o bitmap de blocos, o bitmap de inodes, a tabela de inodes, o bloco do diretório novo e o bloco do diretório pai. Se a energia cair depois de gravar só parte deles, o disco fica **inconsistente**: por exemplo, um bloco marcado como ocupado que ninguém usa, ou uma entrada de diretório apontando para um inode livre.

O journaling resolve isso gravando as alterações primeiro em uma área separada (o **journal**) e só depois nos lugares definitivos:

1. **Begin:** inicia uma transação.
2. **Log:** grava no journal todas as alterações da transação.
3. **Commit:** grava um registro dizendo "esta transação está completa". A partir daqui a operação é considerada feita.
4. **Checkpoint:** copia as alterações do journal para os blocos definitivos.
5. **Limpeza:** marca o journal como vazio.

Na **recuperação** (ao montar o disco), se existe uma transação com commit no journal, ela é refeita (*replay*). Se não houve commit, a transação é ignorada e o disco fica como estava antes. Refazer é seguro porque a operação é **idempotente**: gravar o mesmo conteúdo no mesmo bloco duas vezes dá o mesmo resultado.

### 1.4 Tipos de journaling

| Tipo | Ideia | Exemplos |
| --- | --- | --- |
| Write-ahead logging físico | O journal guarda cópias inteiras dos blocos alterados. Simples e robusto, mas grava mais dados. | ext3/ext4 (JBD2), **este simulador** |
| Write-ahead logging lógico | O journal guarda a operação ("criar diretório X") ou só a parte alterada. Menor, porém a recuperação precisa reinterpretar a operação. | NTFS, XFS |
| Log-structured | O disco inteiro é um log: tudo é sempre gravado no fim, nada é sobrescrito. | LFS, F2FS |
| Copy-on-write | Nunca sobrescreve um bloco; grava a versão nova em outro lugar e troca o ponteiro raiz de forma atômica. | ZFS, Btrfs, APFS |

O ext4 oferece três modos de journaling:

| Modo | O que vai para o journal | Segurança | Desempenho |
| --- | --- | --- | --- |
| `journal` | Metadados e dados | Maior | Menor (dados gravados duas vezes) |
| `ordered` (padrão) | Só metadados, mas os dados são gravados antes do commit | Boa | Bom |
| `writeback` | Só metadados, sem ordem para os dados | Menor (arquivo pode ter lixo após queda) | Maior |

O simulador funciona como o modo `journal`: todos os blocos alterados, de dados ou metadados, passam pelo journal.

---

## Parte 2: Arquitetura do Simulador

### 2.1 Visão geral e camadas

Cada camada só chama a camada de baixo.

```
┌──────────────────────────────────────────────┐
│ Shell                                        │  lê comandos, imprime resultados
├──────────────────────────────────────────────┤
│ FileSystemSimulator + PathResolver           │  uma operação = um método público
├──────────────────────────────────────────────┤
│ File  /  Directory                           │  conteúdo de arquivo / entradas de diretório
├──────────────────────────────────────────────┤
│ Superblock, Bitmap, InodeTable               │  metadados: o que está livre, onde estão os blocos
├──────────────────────────────────────────────┤
│ Journal + Transaction                        │  junta alterações, commit, checkpoint, recover
├──────────────────────────────────────────────┤
│ VirtualDisk (BlockDevice)                    │  readBlock / writeBlock / sync sobre disco.img
└──────────────────────────────────────────────┘
                     │
                  disco.img   (único arquivo real no computador)
```

Durante uma operação, nenhuma classe acima do journal grava direto no `VirtualDisk`. Todas as leituras e gravações passam por uma `Transaction`, e só o `Journal.commit()` grava no disco de verdade.

### 2.2 Disco virtual e isolamento

Nada do que é criado no simulador aparece nas pastas reais do computador. Existe um único arquivo real, `disco.img`, que faz o papel do disco físico. Todos os arquivos e diretórios do simulador existem apenas como bytes dentro dele. O código nunca usa `java.io.File.mkdir()`, `createNewFile()` ou `Files.write()` para representar arquivos do simulador.

Motivos:

- **Senão, quem faria o trabalho seria o SO real.** Criar pastas de verdade faria o ext4 ou o NTFS decidir blocos, inodes e espaço livre; nada estaria sendo simulado.
- **É assim que um disco funciona.** Para o SO, um HD ou pendrive é só uma sequência de blocos numerados; o `disco.img` é exatamente isso. Máquinas virtuais fazem o mesmo com arquivos `.vdi`, `.vmdk` e `.img`.
- **Isolamento.** Um erro no simulador só estraga o `disco.img`, nunca os arquivos do usuário.
- **Controle das gravações.** Para demonstrar o journaling é preciso decidir a ordem exata em que cada bloco vai para o disco e simular uma queda no meio.
- **Portabilidade.** O sistema de arquivos inteiro é um arquivo só, que pode ser copiado e montado em outro computador.

### 2.3 Layout do disco

Bloco de 512 bytes, 1024 blocos (512 KB).

| Blocos | Região | Conteúdo |
| --- | --- | --- |
| 0 | Superbloco | magic, tamanho do bloco, total de blocos e inodes, início dos dados, inode raiz, último txId |
| 1 | Bitmap de blocos | 1024 bits |
| 2 | Bitmap de inodes | 64 bits |
| 3 a 10 | Tabela de inodes | 8 blocos × 8 inodes de 64 bytes = 64 inodes |
| 11 | Cabeçalho do journal | estado, txId, descrição, destinos |
| 12 a 74 | Área do journal | até 63 cópias de blocos |
| 75 a 1023 | Dados | 949 blocos; o bloco 75 é o diretório raiz |

```
 0   1   2   3 ........ 10  11  12 ............ 74  75 ................ 1023
┌───┬───┬───┬─────────────┬───┬─────────────────┬──────────────────────┐
│SB │BMb│BMi│   inodes    │JH │  área do journal │        dados         │
└───┴───┴───┴─────────────┴───┴─────────────────┴──────────────────────┘
```

### 2.4 Estruturas de dados

**Superbloco (bloco 0)**

| Campo | Tipo | Valor |
| --- | --- | --- |
| magic | int | `0x53494D46` ("SIMF") |
| blockSize | int | 512 |
| totalBlocks | int | 1024 |
| totalInodes | int | 64 |
| dataStart | int | 75 |
| rootInode | int | 0 |
| lastTxId | long | último id de transação |

**Inode (64 bytes)**

| Offset | Tamanho | Campo |
| --- | --- | --- |
| 0 | 1 | tipo (0 = livre, 1 = arquivo, 2 = diretório) |
| 1 | 4 | tamanho em bytes |
| 5 | 8 | data de criação |
| 13 | 8 | data de modificação |
| 21 | 40 | 10 ponteiros diretos para blocos (-1 = vazio) |
| 61 | 3 | reservado |

Tamanho máximo de arquivo: 10 × 512 = 5120 bytes.

**Entrada de diretório (32 bytes, 16 por bloco)**

| Offset | Tamanho | Campo |
| --- | --- | --- |
| 0 | 4 | número do inode (-1 = entrada vazia) |
| 4 | 28 | nome em UTF-8, completado com zeros |

Todo diretório tem `.` (ele mesmo) e `..` (o pai). Na raiz, `..` aponta para o próprio inode 0.

**Bitmap:** bit `i` do bloco indica se o bloco (ou inode) `i` está ocupado. Na formatação, os blocos 0 a 74 (metadados e journal) já são marcados como ocupados.

### 2.5 Journaling no simulador

**Cabeçalho do journal (bloco 11)**

| Campo | Tamanho | Descrição |
| --- | --- | --- |
| magic | 4 bytes | "JRNL" |
| estado | int | 0 = VAZIO, 1 = COMMITTED |
| txId | long | id da transação |
| quantidade | int | número de blocos no log (≤ 63) |
| descricao | 64 bytes | ex.: `mkdir /docs` |
| destinos | 63 × int | `destinos[i]` = bloco onde a cópia `i` deve ser gravada |

A cópia `i` fica no bloco `12 + i`.

**Operações registradas:** `mkdir`, `rmdir`, `rename`, `touch`, `write`, `cp` e `rm`. As operações de leitura (`ls`, `cat`, `df`) não abrem transação.

**Passos do commit:**

```
1. a transação tem em memória os blocos alterados (nada no disco ainda)
2. grava cada bloco em 12, 13, 14...  ......................... sync
3. grava cabeçalho: estado=COMMITTED, txId, descricao, destinos . sync   ← ponto de commit
4. checkpoint: grava cada cópia no bloco de destino ........... sync
5. grava cabeçalho: estado=VAZIO ............................... sync
```

O superbloco (com o novo `lastTxId`) entra na própria transação, então também é protegido pelo journal.

**Recuperação (ao montar o disco):** lê o cabeçalho; se o estado for COMMITTED, copia cada bloco `12 + i` para `destinos[i]`, faz `sync` e marca o journal como VAZIO.

**O que acontece se cair em cada passo:**

| Queda durante | Estado do journal ao reiniciar | Resultado |
| --- | --- | --- |
| Operação em memória (passo 1) | VAZIO | Nada foi gravado; a operação não aconteceu. |
| Passo 2 (gravando o log) | VAZIO | Cópias incompletas são ignoradas; a operação não aconteceu. |
| Passo 3 antes de terminar | VAZIO | A operação não aconteceu. |
| Depois do passo 3 (checkpoint ou limpeza) | COMMITTED | A recuperação refaz o checkpoint; a operação acontece por inteiro. |

Em nenhum caso o disco fica pela metade.

---

## Parte 3: Implementação em Java

### 3.1 Estrutura de pacotes

```
src/
└── fs/
    ├── Main.java
    ├── shell/
    │   └── Shell.java
    ├── core/
    │   ├── FileSystemSimulator.java
    │   ├── PathResolver.java
    │   ├── Directory.java
    │   ├── File.java
    │   ├── DirEntry.java
    │   └── FsException.java
    ├── meta/
    │   ├── Superblock.java
    │   ├── Inode.java
    │   ├── InodeTable.java
    │   └── Bitmap.java
    ├── journal/
    │   ├── Journal.java
    │   ├── JournalHeader.java
    │   └── Transaction.java
    └── disk/
        ├── BlockDevice.java
        ├── VirtualDisk.java
        └── Layout.java
```

A classe `File` pedida no enunciado fica em `fs.core` e não é a `java.io.File`.

### 3.2 FileSystemSimulator

| Método | Comando | Operação |
| --- | --- | --- |
| `formatar(caminhoImg)` | `format` | cria o disco vazio |
| `montar(caminhoImg)` | (início) | abre o disco e executa a recuperação do journal |
| `criarDiretorio(caminho)` | `mkdir` | criar diretório |
| `apagarDiretorio(caminho)` | `rmdir` | apagar diretório (vazio) |
| `renomear(caminho, novoNome)` | `rename` | renomear arquivo ou diretório |
| `listar(caminho)` | `ls` | listar diretório |
| `criarArquivo(caminho)` | `touch` | criar arquivo vazio |
| `escreverArquivo(caminho, texto)` | `write` | gravar conteúdo |
| `lerArquivo(caminho)` | `cat` | ler conteúdo |
| `copiarArquivo(origem, destino)` | `cp` | copiar arquivo |
| `apagarArquivo(caminho)` | `rm` | apagar arquivo |
| `historicoJournal()` | `journal` | transações da sessão |
| `armarCrash()` | `crash` | derruba a próxima operação |

Todo método que altera o disco segue o mesmo padrão: abre a transação, executa a operação usando a transação como disco e faz o commit. Se ocorrer um erro antes do commit, a transação é descartada e o disco não muda.

```java
private void executar(String descricao, Operacao operacao) throws IOException {
    Transaction tx = journal.begin(descricao);
    operacao.executar(Fs.sobre(tx));
    journal.commit(tx);
}

public void criarDiretorio(String caminho) throws IOException {
    executar("mkdir " + caminho, fs -> {
        String nome = fs.nomeNovo(caminho);
        Directory pai = fs.pai(caminho);
        if (pai.buscar(nome) != null) {
            throw new FsException("ja existe");
        }
        int novo = Directory.criar(fs.dev(), fs.inodes(), fs.blocos(), pai.numero());
        pai.adicionar(nome, novo);
    });
}
```

### 3.3 Classes File e Directory

**`Directory`** representa um diretório pelo número do seu inode. Lê e grava as entradas de 32 bytes nos blocos do diretório: `listar()`, `buscar(nome)`, `adicionar(nome, inode)` (usa uma entrada vazia ou aloca um bloco novo), `remover(nome)`, `renomear(antigo, novo)` e `vazio()`. O método estático `criar` aloca o inode e o primeiro bloco e grava `.` e `..`.

Renomear só troca o nome dentro da entrada; o inode e os dados não mudam:

```java
public void renomear(String antigo, String novo) throws IOException {
    alterarEntrada(antigo, novo);
}
```

**`File`** representa um arquivo pelo número do seu inode: `ler()` junta os blocos até o tamanho do arquivo; `escrever(conteudo)` libera os blocos antigos, aloca `ceil(tamanho / 512)` blocos novos e atualiza o inode; `liberarBlocos()` devolve os blocos ao bitmap.

```java
public void escrever(byte[] conteudo) throws IOException {
    if (conteudo.length > TAMANHO_MAXIMO) {
        throw new FsException("arquivo excede 5 KB");
    }
    liberarBlocos();
    Inode inode = inodes.ler(numero);
    for (int i = 0, pos = 0; pos < conteudo.length; i++, pos += Layout.BLOCK_SIZE) {
        int bloco = blocos.alocar();
        int fim = Math.min(pos + Layout.BLOCK_SIZE, conteudo.length);
        dev.writeBlock(bloco, Arrays.copyOf(Arrays.copyOfRange(conteudo, pos, fim), Layout.BLOCK_SIZE));
        inode.blocos[i] = bloco;
    }
    inode.tamanho = conteudo.length;
    inode.tocar();
    inodes.gravar(numero, inode);
}
```

### 3.4 Classe Journal (e Transaction)

**`Transaction`** implementa a mesma interface `BlockDevice` do disco. Ela guarda em um `LinkedHashMap` os blocos alterados: `writeBlock` só guarda uma cópia em memória, e `readBlock` devolve a versão alterada, se existir, ou lê do disco. Assim as classes de cima trabalham sobre a transação sem saber que não é o disco real.

```java
public byte[] readBlock(int n) throws IOException {
    byte[] dados = alterados.get(n);
    return dados != null ? dados.clone() : base.readBlock(n);
}

public void writeBlock(int n, byte[] data) throws IOException {
    alterados.put(n, data.clone());
}
```

**`Journal`** cria transações (`begin`), grava no disco (`commit`) e recupera (`recover`):

```java
for (Map.Entry<Integer, byte[]> bloco : alterados.entrySet()) {
    disco.writeBlock(Layout.JOURNAL_DATA_START + i, bloco.getValue());
    header.destinos[i] = bloco.getKey();
    i++;
}
disco.sync();

disco.writeBlock(Layout.JOURNAL_HEADER, header.toBytes());
disco.sync();

if (crashArmado) {
    Runtime.getRuntime().halt(1);
}

for (Map.Entry<Integer, byte[]> bloco : alterados.entrySet()) {
    disco.writeBlock(bloco.getKey(), bloco.getValue());
}
disco.sync();

limpar();
```

```java
public void recover() throws IOException {
    JournalHeader header = JournalHeader.fromBytes(disco.readBlock(Layout.JOURNAL_HEADER));
    if (header.estado != JournalHeader.COMMITTED) {
        return;
    }
    for (int i = 0; i < header.quantidade; i++) {
        disco.writeBlock(header.destinos[i], disco.readBlock(Layout.JOURNAL_DATA_START + i));
    }
    disco.sync();
    limpar();
}
```

### 3.5 Demais classes

| Classe | Responsabilidade |
| --- | --- |
| `BlockDevice` | Interface com `readBlock(n)` e `writeBlock(n, dados)`. |
| `VirtualDisk` | Única classe que conhece o `disco.img`. Usa `RandomAccessFile`; posição do bloco = `n × 512`; `sync()` força a gravação física. |
| `Layout` | Constantes do layout do disco. |
| `Superblock` | Converte o superbloco de/para bytes e valida o magic. |
| `Bitmap` | `alocar()` (primeiro bit livre), `liberar(i)`, `ocupado(i)`, `livres()`. |
| `Inode` / `InodeTable` | Inode de 64 bytes; a tabela calcula bloco `3 + n/8` e deslocamento `(n % 8) × 64`, aloca e libera inodes. |
| `PathResolver` | `resolver("/docs/a.txt")` percorre os diretórios a partir da raiz e devolve o inode; `separarPaiENome` devolve `{"/docs", "a.txt"}`. |
| `FsException` | Erros do usuário, com a mensagem pronta para o Shell. |
| `Shell` | Lê comandos, chama o `FileSystemSimulator` e imprime `erro: <mensagem>` em caso de falha. |
| `Main` | Formata o disco se ele não existe, monta e abre o Shell. |

### 3.6 Fluxo de uma operação: `mkdir /docs`

```
Shell ── criarDiretorio("/docs") ──► FileSystemSimulator
  1. journal.begin("mkdir /docs")                    → Transaction tx
  2. PathResolver.separarPaiENome → pai "/", nome "docs"; valida o nome
  3. PathResolver.resolver("/") → inode 0; confere que é diretório e que "docs" não existe
  4. Directory.criar:
       InodeTable.alocar  → bitmap de inodes (bloco 2) e inode novo (bloco 3)
       Bitmap.alocar      → bitmap de blocos (bloco 1), bloco 76
       grava "." e ".." no bloco 76
  5. Directory(raiz).adicionar("docs") → bloco 75 e data do pai (bloco 3)
  6. journal.commit(tx)  → superbloco (bloco 0) com o novo lastTxId
       blocos alterados: 2, 3, 1, 76, 75, 0
       log em 12..17 → sync → cabeçalho COMMITTED → sync → checkpoint → sync → VAZIO → sync
```

---

## Parte 4: Instalação e funcionamento

### 4.1 Requisitos

- JDK 17 ou superior (`java -version`).
- Git.

### 4.2 Como baixar, compilar e executar

```
git clone https://github.com/NicollyFeitosa/SDA.git
cd SDA
javac -d out $(find src -name "*.java")
java -cp out fs.Main disco.img
```

No Windows (PowerShell):

```
javac -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
java -cp out fs.Main disco.img
```

Se o `disco.img` não existir, ele é criado e formatado na primeira execução. O projeto também pode ser aberto no IntelliJ IDEA, executando a classe `fs.Main`.

### 4.3 Comandos disponíveis

| Comando | Operação do enunciado | Exemplo de saída |
| --- | --- | --- |
| `help` | (apoio) | lista os comandos |
| `mkdir <caminho>` | Criar diretório | `diretorio criado: /docs` |
| `rmdir <caminho>` | Apagar diretório | `diretorio removido: /docs` |
| `rename <caminho> <novoNome>` | Renomear arquivo e diretório | `renomeado: /docs/a.txt -> b.txt` |
| `ls [caminho]` | Listar diretório | `d     512  25/11 16:52  docs` |
| `touch <caminho>` | (apoio) | `arquivo criado: /a.txt` |
| `write <caminho> <texto...>` | (apoio) | `12 bytes gravados em /a.txt` |
| `cat <caminho>` | (apoio) | conteúdo do arquivo |
| `cp <origem> <destino>` | Copiar arquivo | `copiado: /a.txt -> /docs/b.txt` |
| `rm <caminho>` | Apagar arquivo | `arquivo removido: /a.txt` |
| `journal` | (demonstração) | `#3 mkdir /docs OK` |
| `df` | (apoio) | blocos e inodes livres |
| `crash` | (demonstração) | `crash armado: a proxima operacao sera interrompida` |
| `format` | (apoio) | apaga tudo e recria o disco |
| `exit` | (apoio) | fecha o disco e sai |

Mensagens de erro: `caminho nao encontrado`, `ja existe`, `nao e um diretorio`, `nao e um arquivo`, `diretorio nao esta vazio`, `nao e possivel remover a raiz`, `nome invalido`, `nome maior que 28 bytes`, `disco cheio`, `sem inodes livres`, `arquivo excede 5 KB`.

### 4.4 Exemplo de execução

Saída real do terminal, começando sem `disco.img`:

```
$ java -cp out fs.Main disco.img
disco formatado: disco.img
simulador de sistema de arquivos (digite help para ajuda)
> mkdir /docs
diretorio criado: /docs
> touch /docs/notas.txt
arquivo criado: /docs/notas.txt
> write /docs/notas.txt Ola, sistema de arquivos!
25 bytes gravados em /docs/notas.txt
> cat /docs/notas.txt
Ola, sistema de arquivos!
> cp /docs/notas.txt /copia.txt
copiado: /docs/notas.txt -> /copia.txt
> rename /copia.txt backup.txt
renomeado: /copia.txt -> backup.txt
> ls /
d     512  25/11 17:19  .
d     512  25/11 17:19  ..
d     512  25/11 17:19  docs
-      25  25/11 17:19  backup.txt
> rm /backup.txt
arquivo removido: /backup.txt
> mkdir /temp
diretorio criado: /temp
> rename /temp lixo
renomeado: /temp -> lixo
> rmdir /lixo
diretorio removido: /lixo
> ls /docs
d     512  25/11 17:19  .
d     512  25/11 17:19  ..
-      25  25/11 17:19  notas.txt
> df
blocos livres: 946
inodes livres: 61
> journal
#1 mkdir /docs OK
#2 touch /docs/notas.txt OK
#3 write /docs/notas.txt OK
#4 cp /docs/notas.txt /copia.txt OK
#5 rename /copia.txt backup.txt OK
#6 rm /backup.txt OK
#7 mkdir /temp OK
#8 rename /temp lixo OK
#9 rmdir /lixo OK
> rmdir /docs
erro: diretorio nao esta vazio
> rmdir /
erro: nao e possivel remover a raiz
> cat /nada
erro: caminho nao encontrado
```

Depois da execução, a pasta do projeto contém apenas o `disco.img` (524288 bytes); nenhuma pasta `docs` ou arquivo `notas.txt` foi criado no computador.

### 4.5 Teste de recuperação do journal

O comando `crash` faz a próxima operação parar logo depois do ponto de commit, antes do checkpoint, como se a energia tivesse caído:

```
> crash
crash armado: a proxima operacao sera interrompida
> mkdir /depois-do-crash
crash: programa interrompido apos o commit da transacao #10
```

Ao executar novamente, a montagem encontra a transação com commit no journal e a conclui:

```
$ java -cp out fs.Main disco.img
journal: transacao #10 (mkdir /depois-do-crash) recuperada
simulador de sistema de arquivos (digite help para ajuda)
> ls /
d     512  25/11 17:19  .
d     512  25/11 17:19  ..
d     512  25/11 17:19  docs
d     512  25/11 17:19  depois-do-crash
> journal
#10 mkdir /depois-do-crash RECUPERADA
> exit
```

---

## Resultados e conclusão

O simulador mostrou, na prática, como um sistema de arquivos transforma um conjunto de blocos numerados em arquivos e diretórios: o superbloco descreve o disco, os bitmaps controlam o espaço livre, os inodes guardam onde estão os dados e os diretórios associam nomes a inodes. Também ficou claro por que operações como renomear são baratas (só mudam uma entrada de diretório) enquanto copiar exige alocar e gravar blocos novos.

O teste com `crash` demonstrou o papel do journaling: mesmo com o programa interrompido no meio de uma operação que altera seis blocos, o disco voltou a um estado consistente na montagem seguinte, sem precisar verificar o disco inteiro como faria um `fsck`.

**Limitações:**

- Arquivos de no máximo 5 KB (10 ponteiros diretos, sem bloco indireto).
- 64 inodes e 949 blocos de dados.
- Uma transação por vez, com no máximo 63 blocos.
- Histórico do journal mantido apenas em memória, durante a sessão.
- Sem permissões, links ou caminhos relativos.

**Possíveis melhorias:** blocos indiretos para arquivos maiores; comandos `import`/`export` entre o computador e o disco virtual; `mv` entre diretórios; `rmdir -r`; checksum CRC32 nas transações do journal; testes automatizados com JUnit.

## Referências

- TANENBAUM, A. S. *Sistemas Operacionais Modernos*. Cap. 4: Sistemas de arquivos.
- SILBERSCHATZ, A.; GALVIN, P. B.; GAGNE, G. *Fundamentos de Sistemas Operacionais*. Capítulos de sistemas de arquivos.
- ARPACI-DUSSEAU, R. H.; ARPACI-DUSSEAU, A. C. *Operating Systems: Three Easy Pieces*. Capítulos "File System Implementation" e "Crash Consistency: FSCK and Journaling". Disponível em: https://ostep.org.
