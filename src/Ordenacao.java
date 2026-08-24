import java.io.*;
import java.util.*;


public class Ordenacao {
    private ArquivoJogador arquivo;

    public Ordenacao(ArquivoJogador arquivo){
        this.arquivo = arquivo;
    }

    public void Ordenar(int quantArq, int quantBlocos){
        List<File> runs = gerarRuns(quantBlocos); // aqui esta sendo criado apenas a quantidade de blocos a ser usada
    }

    private List<File> gerarRuns(int quantBlocos) throws IOException {
        List<File> runs = new ArrayList<>();
        arquivo.resetarLeituraOrdenacao();

        List<Jogador> bloco = new ArrayList<>();
        Jogador j = arquivo.proximoAtivo();

        while (j != null) {
            bloco.add(j);

            if (bloco.size() == quantBlocos) {
                runs.add(gravarRun(bloco));
                bloco = new ArrayList<>();
            }

            j = arquivo.proximoAtivo();
        }

        // sobrou gente no bloco (não fechou um bloco cheio)
        if (!bloco.isEmpty()) {
            runs.add(gravarRun(bloco));
        }

        return runs;
    }

    private File gravarRun(List<Jogador> bloco) throws IOException {
        Collections.sort(bloco);

        File runFile = File.createTempFile("run", ".dat");

        try (RandomAccessFile rafRun = new RandomAccessFile(runFile, "rw")) {
            for (Jogador jog : bloco) {
                byte[] bytes = jog.toByteArray();
                rafRun.writeByte(' ');       // lápide ativo
                rafRun.writeInt(bytes.length);
                rafRun.write(bytes);
            }
        }

        return runFile;
    }

    private List<List<File>> distribuirRuns(List<File> runs, int quantArq) { // Divide a lista de runs em "quantArq" grupos
        List<List<File>> grupos = new ArrayList<>();
        for (int i = 0; i < quantArq; i++) {
            grupos.add(new ArrayList<>());
        }

        for (int i = 0; i < runs.size(); i++) {
            grupos.get(i % quantArq).add(runs.get(i));
        }
        return grupos;
    }

}