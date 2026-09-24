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
}

