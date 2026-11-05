import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Ordenação externa por intercalação balanceada do arquivo de dados (jogadores.db).
 *
 * 1) Distribuição: lê os registros ATIVOS do .db em blocos de quantReg, ordena cada bloco
 *    em memória por id e distribui os blocos ciclicamente em quantArq arquivos.
 * 2) Intercalação: em várias passadas entre dois grupos de quantArq arquivos, junta um bloco de
 *    cada arquivo por vez (o tamanho do bloco multiplica por quantArq a cada passada)
 *    até restar uma única sequência ordenada.
 * 3) Escreve arquivo_destino.csv com o cabeçalho fixo do CSV e a sequência final.
 */
public class Ordenacao {
    private static final String CABECALHO = "athleteId,firstName,slug,positionAbbreviation,timestamp";

    private static final String ARQUIVO_DESTINO = "arquivo_destino.csv";

    private static String arquivoAux(int n) { return "arquivo_aux_" + n + ".csv"; }

    private final ArquivoJogador arquivo;

    public Ordenacao(ArquivoJogador arquivo) {
        if (arquivo == null) throw new IllegalArgumentException("ArquivoJogador é obrigatório.");
        this.arquivo = arquivo;
    }

    public void gerarArq(int quantArq, int quantReg) throws IOException {
        if (quantArq < 2) {
            System.out.println("A quantidade de arquivos deve ser pelo menos 2.");
            return;
        }
        if (quantReg < 1) {
            System.out.println("A quantidade de registros por bloco deve ser pelo menos 1.");
            return;
        }

        int k = quantArq;
        Path[] ent = new Path[k]; // grupo de entrada da passada
        Path[] sai = new Path[k]; // grupo de saída da passada
        for (int i = 0; i < k; i++) {
            ent[i] = Paths.get(arquivoAux(i + 1));
            sai[i] = Paths.get(arquivoAux(k + i + 1));
        }

        try {
            int seqs = distribuir(ent, quantReg);

            long tam = quantReg; // tamanho (em registros) de cada sequência da passada atual
            while (seqs > 1) {
                seqs = passada(ent, sai, tam);
                tam *= k;
                Path[] t = ent; ent = sai; sai = t;
            }

            // a sequência final está sempre no primeiro arquivo do grupo de entrada
            gravarDestino(ent[0], Paths.get(ARQUIVO_DESTINO));
            System.out.println("Intercalação foi feita em " + Paths.get(ARQUIVO_DESTINO).getFileName());
        } finally {
            for (int i = 0; i < k; i++) { // apaga os temporários
                Files.deleteIfExists(Paths.get(arquivoAux(i + 1)));
                Files.deleteIfExists(Paths.get(arquivoAux(k + i + 1)));
            }
        }
    }

    // lê o .db, gera blocos ordenados e os distribui ciclicamente; devolve quantos blocos foram gerados
    private int distribuir(Path[] ent, int quantReg) throws IOException {
        int k = ent.length;
        BufferedWriter[] w = new BufferedWriter[k];
        int blocos = 0;
        try {
            for (int i = 0; i < k; i++) w[i] = Files.newBufferedWriter(ent[i], StandardCharsets.UTF_8);

            arquivo.resetarLeituraOrdenacao();
            List<Jogador> bloco = new ArrayList<>(quantReg);
            Jogador j;
            while ((j = arquivo.proximoAtivo()) != null) {
                bloco.add(j);
                if (bloco.size() == quantReg) {
                    gravarBloco(w[blocos % k], bloco);
                    blocos++;
                }
            }
            if (!bloco.isEmpty()) { // último bloco, possivelmente incompleto
                gravarBloco(w[blocos % k], bloco);
                blocos++;
            }
        } finally {
            for (BufferedWriter b : w) if (b != null) b.close();
        }
        return blocos;
    }

    private void gravarBloco(BufferedWriter w, List<Jogador> bloco) throws IOException {
        Collections.sort(bloco); // Jogador.compareTo compara por athleteId
        for (Jogador j : bloco) {
            w.write(paraLinha(j));
            w.newLine();
        }
        bloco.clear();
    }

    // uma passada: junta um bloco de cada arquivo de ent por vez, distribuindo o resultado em sai
    private int passada(Path[] ent, Path[] sai, long tam) throws IOException {
        int k = ent.length;
        BufferedReader[] r = new BufferedReader[k];
        BufferedWriter[] w = new BufferedWriter[k];
        String[] atual = new String[k];
        long[] resta = new long[k];
        int novas = 0;
        int destino = 0;
        try {
            for (int i = 0; i < k; i++) {
                r[i] = Files.newBufferedReader(ent[i], StandardCharsets.UTF_8);
                w[i] = Files.newBufferedWriter(sai[i], StandardCharsets.UTF_8);
            }
            while (true) {
                boolean algum = false;
                for (int i = 0; i < k; i++) {
                    atual[i] = r[i].readLine();
                    resta[i] = atual[i] == null ? 0 : tam;
                    algum |= atual[i] != null;
                }
                if (!algum) break;

                while (true) {
                    int m = -1;
                    for (int i = 0; i < k; i++) {
                        if (resta[i] > 0 && (m == -1 || id(atual[i]) < id(atual[m]))) m = i;
                    }
                    if (m == -1) break;
                    w[destino].write(atual[m]);
                    w[destino].newLine();
                    if (--resta[m] > 0 && (atual[m] = r[m].readLine()) == null) resta[m] = 0;
                }
                novas++;
                destino = (destino + 1) % k;
            }
        } finally {
            for (int i = 0; i < k; i++) {
                if (r[i] != null) r[i].close();
                if (w[i] != null) w[i].close();
            }
        }
        return novas;
    }

    private void gravarDestino(Path sequencia, Path destino) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(sequencia, StandardCharsets.UTF_8);
             BufferedWriter w = Files.newBufferedWriter(destino, StandardCharsets.UTF_8)) {
            w.write(CABECALHO);
            w.newLine();
            String linha;
            while ((linha = r.readLine()) != null) {
                w.write(linha);
                w.newLine();
            }
        }
    }

    private static int id(String linha) {
        return Integer.parseInt(linha.substring(0, linha.indexOf(',')).trim());
    }

    private static String paraLinha(Jogador j) {
        String data = j.getTimestamp() != null ? j.getTimestamp().toString() : "";
        return j.getAthleteId() + "," + j.getFirstName() + "," + j.getSlug() + ","
                + j.getPositionAbbreviation() + "," + data;
    }
}
