import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Casamento de padrões no arquivo de dados: KMP e Boyer-Moore (heurística do caractere ruim).
 *
 * A busca é feita sobre os BYTES do arquivo inteiro (texto em UTF-8). Cada ocorrência é
 * depois associada ao registro que a contém, para mostrar o ID e o nome do jogador.
 */
public class CasamentoPadrao {

    /** Resultado de uma busca: onde achou, quantas comparações fez e quanto tempo levou. */
    public static class Resultado {
        public final List<Integer> posicoes = new ArrayList<>();
        public long comparacoes = 0;
        public double ms = 0;
    }

    // ------------------------------------------------------------------------- KMP

    /**
     * Knuth-Morris-Pratt: pré-processa o padrão numa tabela de "falhas" (maior prefixo que
     * também é sufixo). Quando há uma divergência, o padrão avança sem voltar o texto,
     * garantindo O(n + m) no pior caso.
     */
    public static Resultado kmp(byte[] texto, byte[] padrao) {
        Resultado r = new Resultado();
        long t0 = System.nanoTime();
        int n = texto.length, m = padrao.length;
        if (m > 0 && m <= n) {
            // tabela de falhas: falha[i] = tamanho do maior prefixo próprio de padrao[0..i]
            // que também é sufixo de padrao[0..i]
            int[] falha = new int[m];
            for (int i = 1, k = 0; i < m; i++) {
                while (k > 0 && padrao[i] != padrao[k]) k = falha[k - 1];
                if (padrao[i] == padrao[k]) k++;
                falha[i] = k;
            }
            // varredura do texto
            for (int i = 0, k = 0; i < n; i++) {
                while (k > 0) {
                    r.comparacoes++;
                    if (texto[i] == padrao[k]) break;
                    k = falha[k - 1];             // desliza o padrão usando a tabela
                }
                if (k == 0) {
                    r.comparacoes++;
                    if (texto[i] == padrao[0]) k = 1;
                } else {
                    k++;                          // a comparação do while acima foi bem-sucedida
                }
                if (k == m) {                     // padrão completo encontrado
                    r.posicoes.add(i - m + 1);
                    k = falha[k - 1];             // continua permitindo ocorrências sobrepostas
                }
            }
        }
        r.ms = (System.nanoTime() - t0) / 1e6;
        return r;
    }

    // ------------------------------------------------------------------ BOYER-MOORE

    /**
     * Boyer-Moore com a heurística do caractere ruim: compara o padrão da DIREITA para a
     * ESQUERDA; ao divergir, desloca o padrão para alinhar a última ocorrência do byte
     * do texto dentro do padrão (ou salta o padrão inteiro se o byte nem existe nele).
     * Na prática costuma ser muito rápido por pular trechos do texto.
     */
    public static Resultado boyerMoore(byte[] texto, byte[] padrao) {
        Resultado r = new Resultado();
        long t0 = System.nanoTime();
        int n = texto.length, m = padrao.length;
        if (m > 0 && m <= n) {
            // ultima[c] = última posição do byte c no padrão (-1 se não aparece)
            int[] ultima = new int[256];
            java.util.Arrays.fill(ultima, -1);
            for (int i = 0; i < m; i++) ultima[padrao[i] & 0xFF] = i;

            int i = 0;                            // posição do padrão sobre o texto
            while (i <= n - m) {
                int j = m - 1;
                while (j >= 0) {                  // compara da direita para a esquerda
                    r.comparacoes++;
                    if (padrao[j] != texto[i + j]) break;
                    j--;
                }
                if (j < 0) {                      // casou o padrão inteiro
                    r.posicoes.add(i);
                    i += 1;
                } else {
                    int deslocamento = j - ultima[texto[i + j] & 0xFF];
                    i += Math.max(1, deslocamento);
                }
            }
        }
        r.ms = (System.nanoTime() - t0) / 1e6;
        return r;
    }

    // ------------------------------------------------------------------ OPÇÃO DE MENU

    /**
     * Opção de menu: pergunta o padrão ao usuário e executa KMP e Boyer-Moore no arquivo de dados.
     * No Main basta chamar: CasamentoPadrao.opcaoMenu(scanner, "jogadores.db");
     */
    public static void opcaoMenu(Scanner sc, String arquivoDados) {
        if (!new java.io.File(arquivoDados).exists()) {
            System.out.println("Arquivo de dados não encontrado: " + arquivoDados);
            return;
        }
        System.out.print("Padrão a buscar (ex.: parte de um nome): ");
        String padrao = sc.nextLine();
        try {
            buscarNoArquivo(arquivoDados, padrao);
        } catch (IOException e) {
            System.out.println("Erro na busca: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ BUSCA NO ARQUIVO

    public static void buscarNoArquivo(String caminhoDados, String padraoTexto) throws IOException {
        byte[] texto = Files.readAllBytes(Paths.get(caminhoDados));
        byte[] padrao = padraoTexto.getBytes(StandardCharsets.UTF_8);
        if (padrao.length == 0) {
            System.out.println("Padrão vazio.");
            return;
        }

        Resultado rk = kmp(texto, padrao);
        Resultado rb = boyerMoore(texto, padrao);

        System.out.println("\n=== CASAMENTO DE PADRÕES: \"" + padraoTexto + "\" em " + caminhoDados + " ===");
        System.out.printf("KMP         : %d ocorrência(s) | %d comparações | %.3f ms%n",
                rk.posicoes.size(), rk.comparacoes, rk.ms);
        System.out.printf("Boyer-Moore : %d ocorrência(s) | %d comparações | %.3f ms%n",
                rb.posicoes.size(), rb.comparacoes, rb.ms);
        System.out.println("Mesmas posições nos dois algoritmos? " + rk.posicoes.equals(rb.posicoes));
        System.out.println("Menos comparações: " + (rk.comparacoes <= rb.comparacoes ? "KMP" : "Boyer-Moore")
                + " | Mais rápido: " + (rk.ms <= rb.ms ? "KMP" : "Boyer-Moore"));

        if (rk.posicoes.isEmpty()) return;

        // associa cada ocorrência ao registro que a contém (mostra no máximo 20)
        int[] inicios = localizarRegistros(texto);
        System.out.println("\nOcorrências (até 20):");
        int mostradas = 0;
        for (int pos : rk.posicoes) {
            if (mostradas++ == 20) {
                System.out.println("... e mais " + (rk.posicoes.size() - 20) + " ocorrência(s).");
                break;
            }
            System.out.println(descreverOcorrencia(texto, inicios, pos));
        }
    }

    /**
     * Percorre o arquivo registro a registro para saber onde cada um começa.
     * Formato (TP1): cabeçalho de 4 bytes, depois [lápide 1 byte][tamanho 4 bytes][dados].
     * Se o formato não bater, devolve o que conseguiu e a busca continua mostrando só o offset.
     */
    private static int[] localizarRegistros(byte[] arq) {
        List<Integer> lista = new ArrayList<>();
        int pos = 4;
        while (pos + 5 <= arq.length) {
            int tam = ByteBuffer.wrap(arq, pos + 1, 4).getInt();
            if (tam <= 0 || pos + 5 + (long) tam > arq.length) break;
            lista.add(pos);
            pos += 5 + tam;
        }
        int[] v = new int[lista.size()];
        for (int i = 0; i < v.length; i++) v[i] = lista.get(i);
        return v;
    }

    private static String descreverOcorrencia(byte[] arq, int[] inicios, int offset) {
        // busca binária: último registro que começa em ou antes do offset
        int lo = 0, hi = inicios.length - 1, achado = -1;
        while (lo <= hi) {
            int meio = (lo + hi) >>> 1;
            if (inicios[meio] <= offset) { achado = meio; lo = meio + 1; } else hi = meio - 1;
        }
        if (achado < 0) return "  offset " + offset + " (fora de registros: cabeçalho ou formato desconhecido)";
        int ini = inicios[achado];
        try {
            char lapide = (char) (arq[ini] & 0xFF);
            int tam = ByteBuffer.wrap(arq, ini + 1, 4).getInt();
            // dados começam em ini+5: [int id][String nome em UTF (writeUTF)]...
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(arq, ini + 5, tam));
            int id = in.readInt();
            String nome = in.readUTF();
            return "  offset " + offset + " -> registro ID " + id + " | " + nome
                    + (lapide == '*' ? " [EXCLUÍDO]" : "");
        } catch (IOException | RuntimeException e) {
            return "  offset " + offset + " -> registro iniciado em " + ini;
        }
    }
}