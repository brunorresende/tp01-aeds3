# TP01 / TP02 / TP03 - AEDS III

Trabalho Prático da disciplina de Algoritmos e Estruturas de Dados III, implementado em Java. O sistema gerencia um arquivo de dados binário de **jogadores de futebol** (registros de tamanho variável com acesso direto) e foi evoluído em três etapas:

- **TP01:** CRUD sobre arquivo sequencial, carga a partir de CSV e **ordenação externa** por intercalação balanceada.
- **TP02:** **Árvore B** (índice primário por ID), **CRUD indexado** e **lista invertida** por nome.
- **TP03:** **compressão de dados** (Huffman e LZW) e **casamento de padrões** (KMP e Boyer-Moore).
## Funcionalidades

O programa é executado via terminal e apresenta um menu interativo:

| Opção | Ação |
|---|---|
| 1 | Carregar a base de dados a partir do CSV (recria o arquivo binário do zero) |
| 2 | Ler um registro pelo ID (busca sequencial) |
| 3 | Criar um novo registro |
| 4 | Atualizar um registro existente |
| 5 | Deletar um registro (exclusão lógica) |
| 6 | Ordenar o arquivo de dados (ordenação externa por intercalação) |
| 7 | Gerar lista invertida (por nome) e fazer buscas |
| 8 | CRUD indexado (create/read/update/delete usando a Árvore B) |
| 9 | Compactar a base de dados (Huffman e LZW) |
| 10 | Descompactar a base de dados (substitui o arquivo de dados) |
| 11 | Buscar padrão no arquivo de dados (KMP e Boyer-Moore) |
| 0 | Sair |

Entradas não numéricas no menu não encerram o programa (a pergunta é repetida) e um erro em uma opção não derruba as demais.

## Estrutura do repositório

```
.
├── src/
│   ├── Main.java              # Menu interativo e ponto de entrada da aplicação
│   ├── Jogador.java           # Entidade Jogador + serialização/desserialização em bytes
│   ├── ArquivoJogador.java    # Acesso ao arquivo binário (RandomAccessFile), CRUD sequencial e carga do CSV
│   ├── Ordenacao.java         # Ordenação externa por intercalação balanceada (sobre o jogadores.db)
│   ├── ArvoreB.java           # Índice primário (Árvore B) sobre o campo id
│   ├── CrudIndexado.java      # CRUD que localiza registros via ArvoreB, em vez de busca sequencial
│   ├── ListaInvertida.java    # Índice invertido (campo Nome, com suporte também a Posição)
│   ├── Huffman.java           # Compressão/descompressão de Huffman
│   ├── LZW.java               # Compressão/descompressão LZW
│   ├── Compressao.java        # Nomes/versões dos arquivos, tempos, ganho e comparação Huffman x LZW
│   └── CasamentoPadrao.java   # KMP e Boyer-Moore sobre o arquivo de dados
├── players-selected-columns 2.csv        # Base de dados de origem (CSV)
├── video/                                # Vídeos de explicação (links em video/README.md)
└── TP01.iml                              # Arquivo de módulo do IntelliJ IDEA
```

Arquivos gerados durante a execução, na pasta de execução (`src/`): `jogadores.db` (arquivo de dados), `indice_id.idx` e `indice_id.ok` (Árvore B), `lista_nome_chaves.idx`, `lista_nome_listas.idx` e `lista_nome.ok` (lista invertida), `arquivo_destino.csv` (ordenação) e `jogadores<Algoritmo>Compressao<X>.db` (compressão).

## Como o registro é armazenado

Cada jogador (`Jogador`) é serializado manualmente em um vetor de bytes com os campos:

- **ID** do atleta (`int`)
- **Nome** (`String` de tamanho variável, via `writeUTF`)
- **Slug** — nome completo no formato `nome-sobrenome` (`String` de tamanho variável)
- **Posição** — sigla, ex.: `G`, `D`, `M`, `F`
- **Data** — quantidade de dias desde a época (epoch day). A data ausente é gravada com o sentinela `Long.MIN_VALUE`, de modo que `01/01/1970` é uma data válida.
  No arquivo binário, cada registro é gravado como:

```
[lápide (1 byte)] [tamanho do registro (4 bytes)] [dados serializados (N bytes)]
```

- A **lápide** indica se o registro está ativo (`' '`) ou excluído logicamente (`'*'`).
- Os 4 primeiros bytes do arquivo são o **cabeçalho**, com o último ID utilizado (usado para gerar novos IDs sequenciais). Se o arquivo é criado vazio, o construtor de `ArquivoJogador` já grava o cabeçalho (ID = 0).
- Registros atualizados que não cabem no espaço original são marcados como excluídos e reinseridos no final do arquivo; se cabem, são reescritos no lugar.
### Carga do CSV (opção 1)

- Os IDs originais do CSV são ignorados: novos IDs sequenciais (1..n) são gerados.
- O CSV é aberto **antes** de apagar a base. Se a carga falhar (ex.: CSV não encontrado), a base existente é mantida e o menu informa o erro.
- O cabeçalho com o último ID é gravado mesmo que uma linha cause exceção no meio da carga, evitando IDs repetidos depois.
- Após a carga, os índices (Árvore B e lista invertida) são marcados como desatualizados.
## Ordenação externa (opção 6)

Implementa a **intercalação balanceada** sobre o próprio `jogadores.db` (somente registros ativos, com os IDs do arquivo de dados):

1. O usuário informa a quantidade de arquivos `k` (>= 2) e o tamanho `b` do bloco (>= 1).
2. Os registros ativos são lidos em blocos de `b`, cada bloco é ordenado em memória pelo `athleteId` e distribuído ciclicamente entre `k` arquivos temporários (`arquivo_aux_N.csv`).
3. Os temporários são intercalados em **várias passadas** entre dois grupos de `k` arquivos: a cada passada junta-se um bloco de cada arquivo por vez e o tamanho do bloco é multiplicado por `k`, até restar uma única sequência ordenada.
4. O resultado é gravado em `arquivo_destino.csv`, com o cabeçalho do CSV, e os temporários são apagados.
5. O tempo total da ordenação é exibido ao final.
## Árvore B (índice primário)

Optamos pela Árvore B clássica (não B+ nem B\*) porque o uso do índice é sempre de busca pontual por ID — não há varredura de intervalos, cenário em que a B+ se destacaria.

- A ordem é parametrizada (definida como `64` em `CrudIndexado`) e deve ser **par e >= 4** (com ordem 3 o grau mínimo seria 1 e a remoção não funciona). Segue a convenção `ordem = 2t`, onde `t` é o grau mínimo.
- O arquivo de índice (`indice_id.idx`) guarda pares `(id, posição no jogadores.db)`. Cada nó tem tamanho fixo e é lido e gravado com **uma única chamada de E/S**.
- `inserir`, `buscar` e `remover` estão implementados, incluindo divisão preventiva de nós cheios na inserção e rebalanceamento (empréstimo entre irmãos e fusão) na remoção. Chave repetida apenas atualiza a posição (usado quando o update move o registro para o fim do arquivo).
- Nós liberados em fusões ficam órfãos no arquivo de índice (comportamento documentado, não é erro).
## CRUD Indexado (opção 8)

A classe `CrudIndexado` reimplementa `create`, `read`, `update` e `delete` localizando o registro pela Árvore B (O(log n)) em vez de percorrer o arquivo.

- **Índice reaproveitado entre execuções:** o marcador `indice_id.ok` guarda o tamanho do `jogadores.db` no último fechamento correto do CRUD indexado. Se o marcador não existir ou não bater (carga do CSV, create/update/delete pelas opções 3 a 5, descompressão, fechamento incorreto), o índice é reconstruído (`construirIndice()`) na próxima abertura da opção 8. Se estiver em dia, a abertura é praticamente instantânea.
- Dentro da opção 8, toda alteração atualiza a árvore no mesmo momento em que atualiza o `jogadores.db`.
- As opções 2 a 5 usam busca sequencial no `ArquivoJogador` e não mexem na árvore; elas apenas invalidam o índice para que seja refeito quando necessário.
## Lista Invertida (opção 7)

A lista invertida é construída sobre o **primeiro nome completo** (normalizado: sem acento, minúsculo, até 40 caracteres). Para cada nome distinto guarda uma lista encadeada de posições no `jogadores.db`:

- `lista_nome_chaves.idx`: termos de tamanho fixo, em ordem, com ponteiro para a lista (busca binária).
- `lista_nome_listas.idx`: nós `(posição no arquivo de dados, próximo)`.
- A geração (`gerar()`) varre o arquivo de dados em lote. A busca descarta registros com lápide.
- **Atualização:** qualquer create/update/delete (CRUD comum ou indexado) marca a lista como desatualizada (`lista_nome.ok` é apagado) e a próxima busca a regera automaticamente, então os resultados sempre refletem o arquivo de dados.
- A busca usa a **linha inteira** digitada, pois a chave é o nome completo (ex.: `José Ignacio`).
- A classe também suporta o campo Posição, embora o menu só exponha o campo Nome.
## Compressão de dados (opções 9 e 10)

A compressão é feita **byte a byte sobre o arquivo inteiro** (cabeçalho, lápides, indicadores de tamanho e dados dos registros).

- **Opção 9 – Compactar:** a base passa pelos dois algoritmos e gera `jogadoresHuffmanCompressaoX.db` e `jogadoresLZWCompressaoX.db`, onde `X` é a próxima versão livre (maior versão existente + 1). Para cada algoritmo o programa mostra o tamanho, o ganho ou perda em % e o tempo, e indica qual teve melhor taxa de compressão e qual foi mais rápido. O `jogadores.db` não é alterado.
- **Opção 10 – Descompactar:** o usuário informa a versão `X`. As duas versões são descomprimidas (com tempo de cada uma e indicação do mais rápido), os resultados são conferidos entre si e o `jogadores.db` é **substituído** pelo conteúdo descomprimido. O arquivo é fechado antes e reaberto depois, e os índices são invalidados. Se só um dos arquivos da versão existir, usa esse e avisa que não há comparação; se a versão não existir, a base não é alterada.
### Huffman (`Huffman.java`)
Conta a frequência dos 256 bytes e monta a árvore de Huffman com fila de prioridade. Arquivo comprimido: tamanho original (8 B), quantidade de símbolos (2 B), tabela `símbolo + frequência` (5 B por símbolo) e os bits dos códigos. A árvore **não é gravada**: é reconstruída na descompressão com o mesmo critério de desempate (frequência, depois chave única), então os códigos são idênticos. Trata arquivo vazio e arquivo com um único símbolo.

### LZW (`LZW.java`)
- **Dicionário inicial (definido pelo grupo):** códigos 0–255 = os 256 bytes; 256 = CLEAR; 257 = EOF; novas sequências a partir de 258.
- **Códigos de largura variável:** de 9 a 16 bits, crescendo conforme o dicionário enche. O descompressor calcula a mesma largura, pois sempre tem uma entrada a menos que o compressor.
- **Dicionário cheio (65536 entradas):** o compressor emite CLEAR e recomeça com o dicionário inicial, continuando a se adaptar em arquivos grandes.
- Arquivo comprimido: tamanho original (8 B) + códigos empacotados em bits.
### Resultado com a base entregue (69.024 registros, 3.103.697 bytes)

| Algoritmo | Tamanho comprimido | Ganho | Tempo para comprimir |
|---|---:|---:|---:|
| Huffman | 1.890.545 B | 39,09% | ~0,15 s |
| LZW | 1.393.931 B | 55,09% | ~0,6–0,7 s |

O LZW comprime mais, pois aproveita as repetições de nomes, slugs e posições; o Huffman é mais rápido para comprimir, pois só considera a frequência de cada byte. Os tempos variam conforme a máquina.

## Casamento de padrões (opção 11)

A busca é feita sobre os **bytes do arquivo de dados inteiro** (texto em UTF-8). O usuário digita o padrão (ex.: parte de um nome), e o programa executa os dois algoritmos, mostra ocorrências, comparações e tempo de cada um, e confirma se as posições encontradas são iguais. Cada ocorrência (até 20 são exibidas) é associada ao registro que a contém, mostrando ID e nome do jogador (e `[EXCLUÍDO]` se estiver com lápide).

- **KMP (Knuth-Morris-Pratt):** pré-processa o padrão em uma tabela de falhas (maior prefixo que também é sufixo) e nunca volta no texto, garantindo O(n + m) no pior caso. Também encontra ocorrências sobrepostas.
- **Boyer-Moore (heurística do caractere ruim):** compara o padrão da direita para a esquerda e, ao divergir, desloca o padrão para alinhar a última ocorrência do byte do texto no padrão (ou salta o padrão inteiro se o byte não existe nele). Na prática costuma pular grande parte do texto.
  **Justificativa da escolha:** o arquivo é grande e a busca é sempre exata por uma cadeia. O KMP oferece a garantia de tempo linear no pior caso, independentemente do padrão. O Boyer-Moore é o algoritmo de melhor desempenho médio para padrões em texto comum, e a comparação entre os dois (garantia de pior caso x desempenho médio) é visível pelo número de comparações exibido. Exemplo na base entregue, padrão `Miguel` (207 ocorrências): KMP fez 3.141.849 comparações e Boyer-Moore, 580.167.

## Linguagem utilizada

- JDK 21
## Como executar

Compile e execute a partir da pasta `src`:

```bash
cd src
javac *.java
java Main
```

> Todas as classes usam os mesmos caminhos, definidos em `ArquivoJogador` (`ARQUIVO_DADOS` = `jogadores.db` e `CSV` = `../players-selected-columns 2.csv`). O `jogadores.db`, os índices e os arquivos de saída ficam na pasta de execução (`src/`) e o CSV é lido de `../` (raiz do projeto), por isso o programa deve ser executado a partir de `src/`. No IntelliJ, configure o *working directory* como `src`.
>
> Na primeira execução (sem `src/jogadores.db`), use a **opção 1** para carregar o CSV. Bases geradas antes da mudança do sentinela de data devem ser recarregadas pela opção 1.

## Observações

- A leitura pelas opções 2 a 5 é feita por **busca sequencial**; a opção 8 usa a Árvore B.
- A exclusão (`delete`) é **lógica**: apenas marca a lápide, sem remover fisicamente o registro nem reaproveitar o espaço.
- O hash extensível (opcional do TP02) não foi implementado.
- O `split(",")` da carga do CSV não trata campos entre aspas (o dataset entregue não tem aspas).