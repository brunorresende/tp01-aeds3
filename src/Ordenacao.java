import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class Ordenacao {
    private ArquivoJogador arquivo;

    public Ordenacao() {
    }

    public Ordenacao(ArquivoJogador arquivo) {
        this.arquivo = arquivo;
    }

    public void gerarArq(int quantArq, int quantReg) throws IOException { // metodo para gerar os arquivos auxiliares
        Path arquivoOrigem = Paths.get("players-selected-columns 2.csv");

        if (!Files.exists(arquivoOrigem)) { // verificando se o usuario conseguiu realizar a carga do arquivo antes de ordenar
            System.out.println("O arquivo de origem 'players-selected-columns 2.csv' não foi encontrado!");
            return;
        }
        String cabecalho = "";
        Path[] arquivoTemp = new Path[quantArq];

        for (int i = 0; i < quantArq; i++) { // criando os arquivos
            arquivoTemp[i] = Paths.get("arquivo_aux_" + (i + 1) + ".csv");
            Files.writeString(arquivoTemp[i], "", StandardCharsets.UTF_8);
        }

        try (BufferedReader reader = Files.newBufferedReader(arquivoOrigem, StandardCharsets.UTF_8)) {
            cabecalho = reader.readLine(); // guarda o cabeçalho
            if (cabecalho == null) return;

            List<String> blocoAtual = new ArrayList<>(); // serve para criar uma lista de registros que vai guardando com base na quantidade que o usuario pediu
            String linha; // serve para ir "alimentando" o bloco atual
            int indiceArquivoAtual = 0; // manuseia onde o arquivo atual esta

            while ((linha = reader.readLine()) != null) {
                blocoAtual.add(linha); // "alimentando" a lista

                if (blocoAtual.size() == quantReg) {

                    List<String> blocoTemp = ordenarRegistros(blocoAtual);

                    escreverArquivo(arquivoTemp[indiceArquivoAtual], blocoTemp);

                    blocoAtual.clear();
                    blocoTemp.clear();
                    indiceArquivoAtual = (indiceArquivoAtual + 1) % quantArq;
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private List<String> ordenarRegistros(List<String> blocoAtual){
        Collections.sort(blocoAtual, Comparator.comparing(linha -> linha.split(",")[1]));
        /* nessa linha de código tem uma função lambda (função anônima) que utiliza o collection sort para ordenar os elementos do bloco de registros atuais,
        e dentro dele há a função 'Comparator.comparing' que é uma função que serve para comparar dois atributos, que nesse caso são os nomes dos atletas, e
        dentro dessa função tem uma função lambda que funciona da seguinte forma, eu pego o dado atual e nele eu vou para o nome do atleta, como no nosso csv os
        dados sao separados por virgula utilizamos o split e comparamos e por fim ordena o bloco de registro atual
        * */

        return blocoAtual;
    }

    private static void escreverArquivo(Path arquivoAtual, List<String> blocoTemp) throws IOException{ // metodo para escrever no arquivo
            try (BufferedWriter writer = Files.newBufferedWriter(arquivoAtual, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (String linha : blocoTemp) {  // agora que os registros ja estao ordenados, grava no arquivo
                    writer.write(linha);
                    writer.newLine();
                }
            }
            catch (IOException e) {
            System.out.println("Erro ao escrever no arquivo: " + arquivoAtual);
            e.printStackTrace();
        }
    }

}