import java.io.ByteArrayOutputStream;
import java.util.HashMap;

/**
 * Compressão LZW aplicada byte a byte sobre o arquivo INTEIRO (cabeçalho, lápides,
 * indicadores de tamanho e dados dos registros).
 *
 * Dicionário inicial (definido pelo grupo):
 *   códigos 0..255 = os 256 possíveis bytes, cada um como uma sequência de tamanho 1
 *   código 256     = CLEAR (esvazia o dicionário e volta ao estado inicial)
 *   código 257     = EOF   (fim dos dados)
 *   códigos 258..  = novas sequências aprendidas durante a compressão
 *
 * Os códigos têm largura variável: começam com 9 bits e crescem até 16 bits conforme o
 * dicionário aumenta. Quando o dicionário chega a 65536 entradas, o compressor emite CLEAR
 * e recomeça com o dicionário inicial (assim o algoritmo continua se adaptando em arquivos grandes).
 *
 * Formato do arquivo comprimido:
 *   [8 bytes] tamanho original em bytes
 *   [restante] códigos LZW empacotados em bits (primeiro bit = bit menos significativo)
 */
public class LZW {
    private static final int CLEAR = 256;
    private static final int EOF = 257;
    private static final int PRIMEIRO_LIVRE = 258;
    private static final int LARGURA_INICIAL = 9;
    private static final int LARGURA_MAX = 16;
    private static final int MAX_CODIGOS = 1 << LARGURA_MAX; // 65536

    //  compressão

    public static byte[] comprimir(byte[] dados) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream(dados.length / 2 + 1024);
        for (int i = 7; i >= 0; i--) saida.write((int) (((long) dados.length) >>> (8 * i)));

        // dicionário: chave = (código do prefixo << 8) | próximo byte  ->  código da sequência
        HashMap<Integer, Integer> dicionario = new HashMap<>();
        int proximo = PRIMEIRO_LIVRE;  // próximo código livre
        int largura = LARGURA_INICIAL;
        EscritorBits w = new EscritorBits(saida);

        int atual = -1; // código da maior sequência já reconhecida
        for (byte bb : dados) {
            int b = bb & 0xFF;
            if (atual == -1) { atual = b; continue; }

            Integer achado = dicionario.get((atual << 8) | b);
            if (achado != null) {
                atual = achado;           // sequência + b já existe: continua estendendo
                continue;
            }

            // sequência + b é nova: emite o código da sequência conhecida
            largura = ajustarLargura(largura, proximo);
            w.escrever(atual, largura);

            if (proximo < MAX_CODIGOS) {
                dicionario.put((atual << 8) | b, proximo++); // aprende a nova sequência
            } else {
                // dicionário cheio: manda o decodificador recomeçar e recomeça também
                w.escrever(CLEAR, LARGURA_MAX);
                dicionario.clear();
                proximo = PRIMEIRO_LIVRE;
                largura = LARGURA_INICIAL;
            }
            atual = b;
        }

        if (atual != -1) {
            largura = ajustarLargura(largura, proximo);
            w.escrever(atual, largura);
            // o descompressor ainda vai aprender uma entrada ao ler este último código;
            // simulamos isso para os dois lados calcularem a mesma largura para o EOF
            if (proximo < MAX_CODIGOS) proximo++;
        }
        largura = ajustarLargura(largura, proximo);
        w.escrever(EOF, largura);
        w.finalizar();
        return saida.toByteArray();
    }

    /**
     * Largura necessária para o próximo código a ser emitido.
     * O maior código possível nesse momento é (proximo - 1), pois o descompressor tem sempre uma entrada a menos que o compressor.
     */
    private static int ajustarLargura(int largura, int proximo) {
        while (largura < LARGURA_MAX && proximo > (1 << largura)) largura++;
        return largura;
    }


    //  descompressão

    public static byte[] descomprimir(byte[] comp) {
        int pos = 0;
        long tamOriginal = 0;
        for (int i = 0; i < 8; i++) tamOriginal = (tamOriginal << 8) | (comp[pos++] & 0xFF);
        byte[] saida = new byte[(int) tamOriginal];

        // dicionário em forma de árvore de prefixos: cada código >= 258 = (código do prefixo, último byte)
        int[] prefixo = new int[MAX_CODIGOS];
        byte[] sufixo = new byte[MAX_CODIGOS];
        byte[] pilha = new byte[MAX_CODIGOS + 1]; // usada para montar uma sequência de trás para frente

        int proximo = PRIMEIRO_LIVRE;
        int largura = LARGURA_INICIAL;
        int anterior = -1;     // código lido na rodada anterior (-1 = nenhum, logo após o início ou um CLEAR)
        int escritos = 0;

        // leitor de bits (primeiro bit = menos significativo), inline para ser rápido
        long buffer = 0;
        int nBits = 0;

        while (true) {
            largura = ajustarLarguraDecod(largura, proximo);

            // garante bits suficientes no acumulador
            while (nBits < largura) {
                if (pos >= comp.length) throw new IllegalStateException("Arquivo LZW truncado ou corrompido.");
                buffer |= ((long) (comp[pos++] & 0xFF)) << nBits;
                nBits += 8;
            }
            int codigo = (int) (buffer & ((1L << largura) - 1));
            buffer >>>= largura;
            nBits -= largura;

            if (codigo == EOF) break;
            if (codigo == CLEAR) {              // volta ao dicionário inicial
                proximo = PRIMEIRO_LIVRE;
                largura = LARGURA_INICIAL;
                anterior = -1;
                continue;
            }

            int primeiroByte;
            if (anterior == -1) {
                // primeiro código depois do início/CLEAR: obrigatoriamente um byte simples
                if (codigo > 255) throw new IllegalStateException("Arquivo LZW corrompido.");
                saida[escritos++] = (byte) codigo;
                primeiroByte = codigo;
            } else {
                int topo = 0;
                int c;
                if (codigo < proximo) {
                    c = codigo;                   // código já conhecido
                } else if (codigo == proximo) {
                    c = anterior;                 // caso especial (sequência = anterior + 1º byte dela mesma)
                } else {
                    throw new IllegalStateException("Arquivo LZW corrompido.");
                }
                // monta a sequência de c de trás para frente na pilha
                while (c >= PRIMEIRO_LIVRE) { pilha[topo++] = sufixo[c]; c = prefixo[c]; }
                pilha[topo++] = (byte) c;     // c < 256: byte inicial da sequência
                primeiroByte = pilha[topo - 1] & 0xFF;

                // despeja a sequência na ordem correta
                while (topo > 0) saida[escritos++] = pilha[--topo];
                if (codigo == proximo) saida[escritos++] = (byte) primeiroByte; // caso especial: acrescenta o 1º byte

                // aprende a nova entrada: sequência anterior + primeiro byte da atual
                if (proximo < MAX_CODIGOS) {
                    prefixo[proximo] = anterior;
                    sufixo[proximo] = (byte) primeiroByte;
                    proximo++;
                }
            }
            anterior = codigo;
        }

        if (escritos != saida.length) throw new IllegalStateException("Tamanho descomprimido diferente do original.");
        return saida;
    }

    // Mesma regra do compressor: aqui o descompressor tem 'proximo' = (proximo do compressor) - 1.
    private static int ajustarLarguraDecod(int largura, int proximo) {
        while (largura < LARGURA_MAX && proximo + 1 > (1 << largura)) largura++;
        return largura;
    }

    //  escrita de bits

    // Empacota códigos de largura variável em bytes (primeiro bit = menos significativo).
    private static class EscritorBits {
        private final ByteArrayOutputStream saida;
        private long buffer = 0;
        private int nBits = 0;

        EscritorBits(ByteArrayOutputStream saida) { this.saida = saida; }

        void escrever(int valor, int largura) {
            buffer |= ((long) valor) << nBits;
            nBits += largura;
            while (nBits >= 8) {
                saida.write((int) (buffer & 0xFF));
                buffer >>>= 8;
                nBits -= 8;
            }
        }

        void finalizar() {
            if (nBits > 0) saida.write((int) (buffer & 0xFF)); // completa o último byte com zeros
            buffer = 0; nBits = 0;
        }
    }
}
