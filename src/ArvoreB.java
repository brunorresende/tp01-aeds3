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
}


