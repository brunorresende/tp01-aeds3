import java.io.ByteArrayOutputStream;
import java.util.PriorityQueue;

/**
 * Compressão de Huffman aplicada byte a byte sobre o arquivo INTEIRO
 * (cabeçalho, lápides, indicadores de tamanho e dados dos registros).
 *
 * Formato do arquivo comprimido:
 *   [8 bytes] tamanho original em bytes
 *   [2 bytes] quantidade de símbolos distintos (0 a 256)
 *   [5 bytes * quantidade] para cada símbolo: 1 byte do símbolo + 4 bytes com a frequência
 *   [restante] bits dos códigos de Huffman, na ordem dos bytes originais (primeiro bit = bit menos significativo)
 *
 * A árvore NÃO é gravada: ela é reconstruída na descompressão a partir das frequências,
 * usando exatamente o mesmo algoritmo determinístico (critério de desempate fixo).
 */
public class Huffman {

    /** Nó da árvore de Huffman.
     * Folha: simbolo >= 0.
     * Nó interno: simbolo = -1. */
    private static class No implements Comparable<No> {
        int simbolo;      // byte (0..255) se for folha, -1 se for nó interno
        long freq;        // soma das frequências da subárvore
        int desempate;    // chave única: garante a mesma ordem na compressão e na descompressão
        No esq, dir;

        No(int simbolo, long freq, int desempate, No esq, No dir) {
            this.simbolo = simbolo; this.freq = freq; this.desempate = desempate;
            this.esq = esq; this.dir = dir;
        }

        boolean folha() { return esq == null; }

        // menor frequência primeiro; em empate, menor chave de desempate
        public int compareTo(No o) {
            if (freq != o.freq) return Long.compare(freq, o.freq);
            return Integer.compare(desempate, o.desempate);
        }
    }

    //  compressão

    public static byte[] comprimir(byte[] dados) {
        // conta a frequência de cada byte
        long[] freq = new long[256];
        for (byte b : dados) freq[b & 0xFF]++;

        // monta a árvore de Huffman
        No raiz = construirArvore(freq);

        // percorre a árvore para obter o código de cada símbolo
        // codigo guarda os bits (primeiro bit da raiz = bit 0 e tamanho a quantidade de bits
        long[] codigo = new long[256];
        int[] tamanho = new int[256];
        if (raiz != null) {
            if (raiz.folha()) {
                // só existe um símbolo distinto: nenhum bit é necessário (a frequência já diz quantas vezes repetir)
                tamanho[raiz.simbolo] = 0;
            } else {
                gerarCodigos(raiz, 0L, 0, codigo, tamanho);
            }
        }

        // grava o cabeçalho (tamanho original + tabela de frequências)
        ByteArrayOutputStream saida = new ByteArrayOutputStream(dados.length / 2 + 1024);
        escreverLong(saida, dados.length);
        int distintos = 0;
        for (int s = 0; s < 256; s++) if (freq[s] > 0) distintos++;
        saida.write(distintos >>> 8);
        saida.write(distintos);
        for (int s = 0; s < 256; s++) {
            if (freq[s] > 0) {
                saida.write(s);
                escreverInt(saida, (int) freq[s]);
            }
        }

        // grava os bits dos códigos de cada byte do arquivo original
        long buffer = 0;     // acumulador de bits (menos significativo primeiro)
        int nBits = 0;       // quantos bits válidos há no acumulador
        for (byte b : dados) {
            int s = b & 0xFF;
            buffer |= codigo[s] << nBits;
            nBits += tamanho[s];
            while (nBits >= 8) {          // despeja bytes completos
                saida.write((int) (buffer & 0xFF));
                buffer >>>= 8;
                nBits -= 8;
            }
        }
        if (nBits > 0) saida.write((int) (buffer & 0xFF)); // último byte, completado com zeros

        return saida.toByteArray();
    }

    //  descompressão

    public static byte[] descomprimir(byte[] comp) {
        int pos = 0;

        // lê o cabeçalho
        long tamOriginal = 0;
        for (int i = 0; i < 8; i++) tamOriginal = (tamOriginal << 8) | (comp[pos++] & 0xFF);
        int distintos = ((comp[pos++] & 0xFF) << 8) | (comp[pos++] & 0xFF);
        long[] freq = new long[256];
        for (int i = 0; i < distintos; i++) {
            int s = comp[pos++] & 0xFF;
            int f = 0;
            for (int j = 0; j < 4; j++) f = (f << 8) | (comp[pos++] & 0xFF);
            freq[s] = f;
        }

        byte[] saida = new byte[(int) tamOriginal];
        if (tamOriginal == 0) return saida;

        // reconstrói exatamente a mesma árvore da compressão
        No raiz = construirArvore(freq);

        //  caso especial: um único símbolo, basta repeti-lo
        if (raiz.folha()) {
            java.util.Arrays.fill(saida, (byte) raiz.simbolo);
            return saida;
        }

        //  lê bit a bit e anda na árvore: bit 0 = esquerda, bit 1 = direita; ao chegar numa folha emite o byte
        int escritos = 0;
        No atual = raiz;
        while (escritos < saida.length) {
            int b = comp[pos++] & 0xFF;
            for (int i = 0; i < 8 && escritos < saida.length; i++) {
                atual = ((b >>> i) & 1) == 0 ? atual.esq : atual.dir;
                if (atual.folha()) {
                    saida[escritos++] = (byte) atual.simbolo;
                    atual = raiz;
                }
            }
        }
        return saida;
    }

    //  auxiliares

    // Constrói a árvore a partir das frequências. Retorna null se não há nenhum símbolo (arquivo vazio).
    private static No construirArvore(long[] freq) {
        PriorityQueue<No> fila = new PriorityQueue<>();
        for (int s = 0; s < 256; s++) {
            if (freq[s] > 0) fila.add(new No(s, freq[s], s, null, null)); // chave de desempate das folhas = símbolo
        }
        if (fila.isEmpty()) return null;

        int contador = 256; // chaves dos nós internos começam depois das folhas (todas únicas)
        while (fila.size() > 1) {
            No a = fila.poll(); // os dois nós de menor frequência
            No b = fila.poll();
            fila.add(new No(-1, a.freq + b.freq, contador++, a, b));
        }
        return fila.poll();
    }

    // Percorre a árvore acumulando o caminho (esquerda = bit 0, direita = bit 1).
    private static void gerarCodigos(No no, long bits, int profundidade, long[] codigo, int[] tamanho) {
        if (no.folha()) {
            codigo[no.simbolo] = bits;
            tamanho[no.simbolo] = profundidade;
            return;
        }
        gerarCodigos(no.esq, bits, profundidade + 1, codigo, tamanho);                          // bit 0
        gerarCodigos(no.dir, bits | (1L << profundidade), profundidade + 1, codigo, tamanho);   // bit 1
    }

    private static void escreverLong(ByteArrayOutputStream o, long v) {
        for (int i = 7; i >= 0; i--) o.write((int) (v >>> (8 * i)));
    }

    private static void escreverInt(ByteArrayOutputStream o, int v) {
        for (int i = 3; i >= 0; i--) o.write(v >>> (8 * i));
    }
}
