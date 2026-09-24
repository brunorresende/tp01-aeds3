import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class ArvoreB {

    private final RandomAccessFile raf;
    private final int ordem;
    private long offsetRaiz;

    private static final long OFFSET_CABECALHO = 0;
    private static final int TAMANHO_CABECALHO = 8; // um long guardando o offset da raiz

    public ArvoreB(String arquivoIndice, int ordem) throws IOException {
        if (ordem < 3) {
            throw new IllegalArgumentException("A ordem da arvore B deve ser >= 3.");
        }
        this.ordem = ordem;
        this.raf = new RandomAccessFile(arquivoIndice, "rw");

        if (raf.length() == 0) {
            // arquivo novo: grava cabecalho indicando arvore vazia
            offsetRaiz = -1;
            raf.seek(OFFSET_CABECALHO);
            raf.writeLong(offsetRaiz);
        } else {
            raf.seek(OFFSET_CABECALHO);
            offsetRaiz = raf.readLong();
        }
    }

    // Representacao de um no EM MEMORIA (usada durante as operacoes);
    // e serializada/deserializada do disco pelos metodos lerNo/escreverNo)
    private class No {
        boolean folha;
        int numChaves;
        int[] chaves;
        long[] posicoes; // posicao no arquivo de DADOS (jogadores.db) para cada chave
        long[] filhos;   // offset, no arquivo de INDICE, de cada filho
        long offsetNoArquivo = -1; // onde este no esta gravado;    -1 == ainda nao gravado

        No(boolean folha) {
            this.folha = folha;
            this.numChaves = 0;
            this.chaves = new int[ordem - 1];
            this.posicoes = new long[ordem - 1];
            this.filhos = new long[ordem];
            Arrays.fill(filhos, -1);
        }
    }

    // tamanho fixo, em bytes, de qualquer no desta arvore
    private int tamanhoNoEmBytes() {
        return 1 + 4 + (ordem - 1) * (4 + 8) + ordem * 8;
    }



    //  Leitura / escrita de nós no arquivo de indice

    //Carrega um nó da árvore que está salvo no arquivo para a memória RAM.
    private No lerNo(long offset) throws IOException {
        raf.seek(offset);   //move o ponteiro do arquivo para a posição do nó
        No no = new No(raf.readByte() == 1);    //lê o tipo do nó (1 para folha ou 0 para interno)
        no.offsetNoArquivo = offset;    //armazena o offset do próprio nó na memória
        no.numChaves = raf.readInt();   //lê quantas chaves estão ativas no nó
        //lê os pares (chave, posicao no arquivo de dados)
        for (int i = 0; i < ordem - 1; i++) {
            no.chaves[i] = raf.readInt();
            no.posicoes[i] = raf.readLong();
        }
        //lê os ponteiros (offsets) para os nós filhos
        for (int i = 0; i < ordem; i++) {
            no.filhos[i] = raf.readLong();
        }
        return no;
    }

    private void escreverNo(No no) throws IOException {
        if (no.offsetNoArquivo == -1) {
            // no novo: aloca espaco sempre no final do arquivo
            no.offsetNoArquivo = raf.length();
        }
        raf.seek(no.offsetNoArquivo);   //Move para a posicao onde o nó deve ser escrito
        raf.writeByte(no.folha ? 1 : 0);    //1 se for folha, 0 se nao
        raf.writeInt(no.numChaves);     //salva a quantidade atual de chaves

        //grava todas as chaves e suas posições associadas
        for (int i = 0; i < ordem - 1; i++) {
            raf.writeInt(no.chaves[i]);
            raf.writeLong(no.posicoes[i]);
        }
        //grava os offsets para os nós filhos
        for (int i = 0; i < ordem; i++) {
            raf.writeLong(no.filhos[i]);
        }
    }

    private void atualizarRaiz(long novoOffset) throws IOException {
        offsetRaiz = novoOffset;    // atualiza a variável na memória
        raf.seek(OFFSET_CABECALHO); //Vai para o iníciodo arquivo
        raf.writeLong(offsetRaiz); // escreve o offset da nova raiz
    }


    //  BUSCA

    // Retorna a posicao no arquivo de dados para o id informado, ou -1 se nao existir.
    //metodo public
    public long buscar(int chave) throws IOException {
        if (offsetRaiz == -1) return -1;
        return buscarNo(lerNo(offsetRaiz), chave);
    }

    //metodo private
    private long buscarNo(No no, int chave) throws IOException {
        int i = 0;
        //procura a posição adequada da chave dentro do nó atual
        while (i < no.numChaves && chave > no.chaves[i]) i++;

        //se encontrou a chave no nó atual, retorna a posição do registro no arquivo de dados
        if (i < no.numChaves && chave == no.chaves[i]) {
            return no.posicoes[i];
        }
        //chegou no limite da árvore e não achou: chave nao existe
        if (no.folha) {
            return -1;
        }
        //não é folha, desce para o filho correspondente
        return buscarNo(lerNo(no.filhos[i]), chave);
    }


    // INSERCAO

    /*
     * Insere (chave, posicao). Se a chave ja existir, apenas ATUALIZA a posicao
     * associada (upsert) - util para quando um UPDATE realoca um registro para
     * o fim do arquivo de dados, mudando sua posicao mas nao seu id.
     */

    public void inserir(int chave, long posicao) throws IOException {
        //Tenta atualizar se a chave já existir no arquivo (upsert)
        if (atualizarSeExistir(chave, posicao)) {
            return;
        }

        //arvore vazia
        if (offsetRaiz == -1) {
            No raiz = new No(true);
            raiz.chaves[0] = chave;
            raiz.posicoes[0] = posicao;
            raiz.numChaves = 1;
            escreverNo(raiz);   //grava a nova raiz no final do arquivo em disco
            atualizarRaiz(raiz.offsetNoArquivo);    //grava o offset no cabeçalho
            return;
        }

        //carrega a raiz atual do disco para a memória RAM
        No raiz = lerNo(offsetRaiz);

        //caso para a raiz cheia
        if (raiz.numChaves == ordem - 1) {
            /* raiz cheia: a arvore cresce em altura.
             * cria uma nova raiz (interna), com a raiz antiga como unico filho,
             * divide a raiz antiga imediatamente.*/
            No novaRaiz = new No(false);
            novaRaiz.filhos[0] = raiz.offsetNoArquivo;
            dividirFilho(novaRaiz, 0, raiz); //split da raiz antiga em dois nós
            inserirNaoCheio(novaRaiz, chave, posicao); //insere o novo elemento a partir da nova raiz
            atualizarRaiz(novaRaiz.offsetNoArquivo); //atualiza o cabeçalho
        } else {
            //A Raiz não está cheia
            inserirNaoCheio(raiz, chave, posicao);
        }
    }


    // metodo buscarNo com uma diferença: quando encontra a chave, ele a substitui e salva no disco.
    private boolean atualizarSeExistir(int chave, long novaPosicao) throws IOException {
        if (offsetRaiz == -1) return false;
        return atualizarNo(lerNo(offsetRaiz), chave, novaPosicao);
    }

    private boolean atualizarNo(No no, int chave, long novaPosicao) throws IOException {
        int i = 0;
        while (i < no.numChaves && chave > no.chaves[i]) i++;

        if (i < no.numChaves && chave == no.chaves[i]) {
            no.posicoes[i] = novaPosicao;   //Atualiza o offset em memória
            escreverNo(no); //salva a alteração no arquivo de disco
            return true;
        }
        if (no.folha) return false; //se não encontrou e chegou ao fim
        return atualizarNo(lerNo(no.filhos[i]), chave, novaPosicao); //continua buscando no filho correto
    }


    // metodo para fazer split, subindo o filho do meio
    private void dividirFilho(No pai, int indiceFilho, No filhoCheio) throws IOException {
        int totalChaves = filhoCheio.numChaves;   // = ordem - 1 (esta cheio)
        int meio = (ordem - 1) / 2;               // indice da chave que sobe

        No novoIrmao = new No(filhoCheio.folha);
        int qtdDireita = totalChaves - meio - 1;

        // copia a metade direita das chaves para o novo irmao
        for (int j = 0; j < qtdDireita; j++) {
            novoIrmao.chaves[j] = filhoCheio.chaves[meio + 1 + j];
            novoIrmao.posicoes[j] = filhoCheio.posicoes[meio + 1 + j];
        }
        novoIrmao.numChaves = qtdDireita;

        // se nao for folha, copia tambem os ponteiros de filhos correspondentes
        if (!filhoCheio.folha) {
            for (int j = 0; j <= qtdDireita; j++) {
                novoIrmao.filhos[j] = filhoCheio.filhos[meio + 1 + j];
            }
        }

        int chaveMediana = filhoCheio.chaves[meio];
        long posicaoMediana = filhoCheio.posicoes[meio];

        filhoCheio.numChaves = meio; // o filho original fica so com a metade esquerda

        // abre espaco no PAI para a chave mediana e o novo ponteiro de filho
        for (int j = pai.numChaves; j > indiceFilho; j--) {
            pai.chaves[j] = pai.chaves[j - 1];
            pai.posicoes[j] = pai.posicoes[j - 1];
        }
        for (int j = pai.numChaves + 1; j > indiceFilho + 1; j--) {
            pai.filhos[j] = pai.filhos[j - 1];
        }

        pai.chaves[indiceFilho] = chaveMediana;
        pai.posicoes[indiceFilho] = posicaoMediana;
        pai.numChaves++;

        // grava o novo irmao primeiro para obter seu offset definitivo em disco
        escreverNo(novoIrmao);
        pai.filhos[indiceFilho + 1] = novoIrmao.offsetNoArquivo;

        escreverNo(filhoCheio);
        escreverNo(pai);
    }


    // insere (chave, posicao) em um no que se sabe NAO estar cheio.
    private void inserirNaoCheio(No no, int chave, long posicao) throws IOException {
        int i = no.numChaves - 1;

        if (no.folha) {
            // desloca chaves maiores para a direita, abrindo espaco
            while (i >= 0 && chave < no.chaves[i]) {
                no.chaves[i + 1] = no.chaves[i];
                no.posicoes[i + 1] = no.posicoes[i];
                i--;
            }
            no.chaves[i + 1] = chave;
            no.posicoes[i + 1] = posicao;
            no.numChaves++;
            escreverNo(no);
        } else {
            // decide em qual filho descer
            while (i >= 0 && chave < no.chaves[i]) i--;
            i++;

            No filho = lerNo(no.filhos[i]);
            if (filho.numChaves == ordem - 1) {
                dividirFilho(no, i, filho);
                // apos a divisao, uma chave subiu para no; decide de novo o lado
                if (chave > no.chaves[i]) {
                    i++;
                }
                filho = lerNo(no.filhos[i]);
            }
            inserirNaoCheio(filho, chave, posicao);
        }
    }

    public void fechar() throws IOException {
        raf.close(); // Fecha o arquivo RAF
    }
}



