import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orquestra a compressão/descompressão do arquivo de dados com Huffman e LZW.
 *
 * Nome dos arquivos gerados: "nomeArquivo" + "NomeAlgoritmo" + "Compressao" + X + extensão
 * Exemplo (versão 1): jogadoresHuffmanCompressao1.db e jogadoresLZWCompressao1.db
 */
public class Compressao {

    private static final String[] ALGORITMOS = {"Huffman", "LZW"};

    /** Resultado de uma execução (compressão ou descompressão) de um algoritmo. */
    private static class Execucao {
        String algoritmo;
        long tamanhoEntrada;
        long tamanhoSaida;
        double tempoMs;
    }

    //  compressão

    /**
     * Comprime o arquivo de dados com os DOIS algoritmos, grava os novos arquivos (próxima versão livre),
     * mostra ganho/perda e tempo de cada um e indica qual foi melhor.
     * O arquivo de dados original não é alterado.
     */
    public static void comprimir(String arquivoDados) throws IOException {
        Path dados = new File(arquivoDados).toPath();
        if (!Files.exists(dados)) {
            System.out.println("Arquivo de dados não encontrado: " + arquivoDados);
            return;
        }
        byte[] original = Files.readAllBytes(dados); // o arquivo INTEIRO (cabeçalho, lápides, tamanhos, dados) é comprimido
        int versao = proximaVersao(arquivoDados);

        Execucao[] exec = new Execucao[2];
        for (int i = 0; i < ALGORITMOS.length; i++) {
            long ini = System.nanoTime();
            byte[] comp = (i == 0) ? Huffman.comprimir(original) : LZW.comprimir(original);
            long fim = System.nanoTime();

            Files.write(new File(nomeArquivoComprimido(arquivoDados, ALGORITMOS[i], versao)).toPath(), comp);

            exec[i] = new Execucao();
            exec[i].algoritmo = ALGORITMOS[i];
            exec[i].tamanhoEntrada = original.length;
            exec[i].tamanhoSaida = comp.length;
            exec[i].tempoMs = (fim - ini) / 1_000_000.0;
        }

        System.out.println("\n=== COMPRESSÃO (versão " + versao + ") ===");
        System.out.println("Tamanho original: " + original.length + " bytes");
        for (Execucao e : exec) {
            double ganho = (1.0 - (double) e.tamanhoSaida / e.tamanhoEntrada) * 100.0;
            if (e.tamanhoEntrada == 0) ganho = 0;
            System.out.printf("%-8s -> %s | %d bytes | %s %.2f%% | tempo %.2f ms%n",
                    e.algoritmo, nomeArquivoComprimido(arquivoDados, e.algoritmo, versao), e.tamanhoSaida,
                    ganho >= 0 ? "ganho de" : "perda de", Math.abs(ganho), e.tempoMs);
        }

        // comparação: menor arquivo = melhor taxa de compressão; menor tempo = mais rápido
        if (exec[0].tamanhoSaida == exec[1].tamanhoSaida) {
            System.out.println("Taxa de compressão: empate.");
        } else {
            Execucao melhor = exec[0].tamanhoSaida < exec[1].tamanhoSaida ? exec[0] : exec[1];
            Execucao pior = melhor == exec[0] ? exec[1] : exec[0];
            System.out.printf("Melhor taxa de compressão: %s (%d bytes a menos que %s)%n",
                    melhor.algoritmo, pior.tamanhoSaida - melhor.tamanhoSaida, pior.algoritmo);
        }
        Execucao rapido = exec[0].tempoMs <= exec[1].tempoMs ? exec[0] : exec[1];
        System.out.println("Mais rápido: " + rapido.algoritmo);
    }

    //  descompressão

    /**
     * Descomprime a versão informada com os dois algoritmos (quando os dois arquivos existem),
     * compara tempos e SUBSTITUI o arquivo de dados pelo resultado.
     * Retorna true se o arquivo de dados foi substituído.
     * O chamador deve fechar o arquivo de dados antes e reabri-lo depois.
     */
    public static boolean descomprimir(String arquivoDados, int versao) throws IOException {
        Execucao[] exec = new Execucao[2];
        byte[][] resultado = new byte[2][];

        for (int i = 0; i < ALGORITMOS.length; i++) {
            File f = new File(nomeArquivoComprimido(arquivoDados, ALGORITMOS[i], versao));
            if (!f.exists()) {
                System.out.println("Arquivo não encontrado: " + f.getName());
                continue;
            }
            byte[] comp = Files.readAllBytes(f.toPath());
            long ini = System.nanoTime();
            try {
                resultado[i] = (i == 0) ? Huffman.descomprimir(comp) : LZW.descomprimir(comp);
            } catch (RuntimeException e) {
                System.out.println("Falha ao descomprimir " + f.getName() + ": " + e.getMessage());
                continue;
            }
            long fim = System.nanoTime();

            exec[i] = new Execucao();
            exec[i].algoritmo = ALGORITMOS[i];
            exec[i].tamanhoEntrada = comp.length;
            exec[i].tamanhoSaida = resultado[i].length;
            exec[i].tempoMs = (fim - ini) / 1_000_000.0;
        }

        if (exec[0] == null && exec[1] == null) {
            System.out.println("Nenhuma versão " + versao + " pôde ser descomprimida. O arquivo de dados não foi alterado.");
            return false;
        }

        // os dois algoritmos precisam reconstruir exatamente os mesmos bytes; senão não substitui nada
        if (exec[0] != null && exec[1] != null && !Arrays.equals(resultado[0], resultado[1])) {
            System.out.println("Os resultados dos dois algoritmos são diferentes! O arquivo de dados não foi alterado.");
            return false;
        }

        System.out.println("\n=== DESCOMPRESSÃO (versão " + versao + ") ===");
        for (Execucao e : exec) {
            if (e == null) continue;
            System.out.printf("%-8s -> %d bytes comprimidos => %d bytes | tempo %.2f ms%n",
                    e.algoritmo, e.tamanhoEntrada, e.tamanhoSaida, e.tempoMs);
        }
        if (exec[0] != null && exec[1] != null) {
            System.out.println("Mais rápido na descompressão: " + (exec[0].tempoMs <= exec[1].tempoMs ? exec[0] : exec[1]).algoritmo);
        } else {
            System.out.println("Comparação indisponível: só um dos arquivos da versão " + versao + " foi encontrado.");
        }

        // substitui o arquivo de dados pelo conteúdo descomprimido
        byte[] bytes = resultado[0] != null ? resultado[0] : resultado[1];
        Files.write(new File(arquivoDados).toPath(), bytes);
        System.out.println("Arquivo de dados substituído pela versão " + versao + " descomprimida.");
        return true;
    }

    //  nomes e versões

    /** "dir/jogadores.db", "Huffman", 2  ->  "dir/jogadoresHuffmanCompressao2.db" */
    private static String nomeArquivoComprimido(String arquivoDados, String algoritmo, int versao) {
        File f = new File(arquivoDados);
        String nome = f.getName();
        int ponto = nome.lastIndexOf('.');
        String base = ponto >= 0 ? nome.substring(0, ponto) : nome;
        String ext = ponto >= 0 ? nome.substring(ponto) : "";
        String novo = base + algoritmo + "Compressao" + versao + ext;
        return f.getParent() == null ? novo : new File(f.getParent(), novo).getPath();
    }

    /** Próxima versão livre = maior X já existente (de qualquer algoritmo) + 1. */
    private static int proximaVersao(String arquivoDados) {
        File f = new File(arquivoDados).getAbsoluteFile();
        String nome = f.getName();
        int ponto = nome.lastIndexOf('.');
        String base = ponto >= 0 ? nome.substring(0, ponto) : nome;
        String ext = ponto >= 0 ? nome.substring(ponto) : "";
        Pattern p = Pattern.compile(Pattern.quote(base) + "(Huffman|LZW)Compressao(\\d+)" + Pattern.quote(ext));

        int maior = 0;
        File[] arquivos = f.getParentFile().listFiles();
        if (arquivos != null) {
            for (File a : arquivos) {
                Matcher m = p.matcher(a.getName());
                if (m.matches()) maior = Math.max(maior, Integer.parseInt(m.group(2)));
            }
        }
        return maior + 1;
    }
}
