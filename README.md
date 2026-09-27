# TP01 - AEDS III

Trabalho Prático 1 da disciplina de Algoritmos e Estruturas de Dados III, implementado em Java. O sistema gerencia um arquivo de dados binário de **jogadores de futebol** com acesso direto (arquivo indexado sequencial com registros de tamanho variável), oferecendo operações de **CRUD**, carga a partir de um CSV e **ordenação externa por intercalação balanceada (merge sort externo)**.

## Funcionalidades

O programa é executado via terminal e apresenta um menu interativo com as seguintes opções:

| Opção | Ação |
|---|---|
| 1 | Carregar a base de dados a partir do CSV (recria o arquivo binário do zero) |
| 2 | Ler um registro pelo ID |
| 3 | Criar um novo registro |
| 4 | Atualizar um registro existente |
| 5 | Deletar um registro (exclusão lógica) |
| 6 | Ordenar o arquivo (ordenação externa por intercalação) |
| 7 | Gerar lista invertida (por nome, e já deixa fazer buscas na hora) |
| 8 | Gerar CRUD Indexado (create/read/update/delete usando a Árvore B) |
| 0 | Sair |

## Estrutura do repositório

```
.
├── src/
│   ├── Main.java             # Menu interativo e ponto de entrada da aplicação
│   ├── Jogador.java           # Entidade Jogador + serialização/desserialização em bytes
│   ├── ArquivoJogador.java    # Acesso ao arquivo binário (RandomAccessFile) e operações de CRUD
│   └── Ordenacao.java         # Ordenação externa por intercalação balanceada
│   ├── ArvoreB.java           # Índice primário (Árvore B) sobre o campo id
│   ├── CrudIndexado.java      # CRUD que localiza registros via ArvoreB, em vez de busca sequencial
│   └── ListaInvertida.java    # Índice invertido (campo Nome, com suporte também a Posição)
├── jogadores.db                          # Arquivo de dados binário (gerado pela carga do CSV)
├── players-selected-columns 2.csv        # Base de dados de origem (CSV)
└── TP01.iml                              # Arquivo de módulo do IntelliJ IDEA
```

## Como o registro é armazenado

Cada jogador (`Jogador`) é serializado manualmente em um vetor de bytes com os seguintes campos:

- **ID** do atleta (`int`)
- **Nome** (`String` de tamanho variável, via `writeUTF`)
- **Slug** — nome completo no formato `nome-sobrenome` (`String` de tamanho variável)
- **Posição** — sigla de 2 caracteres, ex.: `G`, `D`, `M`, `F`
- **Data** — armazenada como quantidade de dias desde a época (epoch day)

No arquivo binário, cada registro é gravado no formato:

```
[lápide (1 byte)] [tamanho do registro (4 bytes)] [dados serializados (N bytes)]
```

- A **lápide** indica se o registro está ativo (`' '`) ou excluído logicamente (`'*'`).
- Os 4 primeiros bytes do arquivo funcionam como **cabeçalho**, guardando o maior ID já utilizado (usado para gerar novos IDs sequenciais).
- Registros atualizados que não cabem mais no espaço original são marcados como excluídos e reinseridos no final do arquivo.

## Ordenação externa

A opção 6 do menu implementa uma ordenação externa por **intercalação balanceada**:

1. O CSV de origem é lido em blocos (o usuário define a quantidade de arquivos auxiliares e o tamanho de cada bloco).
2. Cada bloco é ordenado em memória pelo `athleteId` e distribuído ciclicamente entre os arquivos temporários (`arquivo_aux_N.csv`).
3. Os arquivos temporários são intercalados (merge), comparando o menor ID disponível entre eles a cada passo, gerando o arquivo final ordenado (`arquivo_destino.csv`).
4. O tempo total de execução da ordenação é exibido ao final.

## Árvore B (índice primário)

Optamos pela Árvore B clássica (não B+ nem B\*) porque o uso do índice é sempre de busca pontual por ID — não há necessidade de varrer um intervalo de chaves em sequência, cenário em que a B+ se destacaria pelas folhas encadeadas.

- A ordem da árvore é parametrizada (atualmente definida como `64` em `CrudIndexado`), seguindo a convenção `ordem = 2t`, onde `t` é o grau mínimo, por isso a ordem deve ser um número par.
- O arquivo de índice (`indice_id.idx`) guarda pares `(id, posição no jogadores.db)`.
- `inserir`, `buscar` e `remover` estão implementados, incluindo divisão preventiva de nós cheios na inserção e rebalanceamento (empréstimo entre irmãos e fusão de nós) na remoção.
- Toda operação de CRUD feita pela opção 8 do menu atualiza a árvore no mesmo momento em que atualiza o `jogadores.db`, mantendo os dois arquivos coerentes.
## CRUD Indexado

A classe `CrudIndexado` reimplementa `create`, `read`, `update` e `delete` localizando o registro através da Árvore B em vez de percorrer o arquivo sequencialmente — o que passa a busca de O(n) para O(log n). Ao abrir a opção 8 do menu, o índice é reconstruído a partir do `jogadores.db` existente (`construirIndice()`), e a partir daí toda alteração passa também pela árvore.

## Lista Invertida

A opção 7 do menu gera uma lista invertida sobre o campo Nome: para cada nome distinto, é guardada uma lista encadeada de posições no `jogadores.db` onde aquele nome aparece. A classe `ListaInvertida` também dá suporte ao campo Posição, embora o menu hoje só exponha a geração pelo campo Nome.

A geração (`gerar()`) é feita em lote, varrendo o arquivo de dados inteiro. Ainda não há atualização incremental da lista quando um registro é criado, atualizado ou deletado pelo CRUD comum (itens 3, 4 e 5 do menu) — isso está listado em **Observações** como pendência.
## Linguagem Utilzada

- JDK 21

## Como executar

Compile e execute a partir da pasta `src`:

```bash
cd src
javac *.java
java Main
```

> O arquivo `players-selected-columns 2.csv` deve estar disponível no diretório de execução para a carga inicial da base (opção 1) e para a ordenação (opção 6).

## Observações

- A leitura de registros (`read`) é feita por **busca sequencial** no arquivo.
- A exclusão (`delete`) é **lógica**, apenas marcando a lápide do registro como excluído, sem removê-lo fisicamente do arquivo.
- Ao recarregar a base via CSV (opção 1), os IDs originais do CSV são ignorados e novos IDs sequenciais são gerados.
