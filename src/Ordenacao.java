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
        Path arquivoOrigem = Paths.get("../players-selected-columns 2.csv");

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
                    indiceArquivoAtual = (indiceArquivoAtual + 1) % quantArq;
                }
            }
            // grava o ultimo bloco, que pode ter sobrado incompleto (menor que quantReg)
            if (!blocoAtual.isEmpty()) {
                List<String> blocoTemp = ordenarRegistros(blocoAtual);
                escreverArquivo(arquivoTemp[indiceArquivoAtual], blocoTemp);
            }

            Path arquivoDestino = Paths.get("arquivo_destino.csv");
            intercalarArquivo(arquivoTemp, arquivoDestino);


        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private List<String> ordenarRegistros(List<String> blocoAtual){
        blocoAtual.removeIf(linha -> linha == null || linha.trim().isEmpty() || !linha.contains(",")); // linha para previnir quanto as linhas em branco do csv
        Collections.sort(blocoAtual, Comparator.comparing(linha -> Integer.parseInt(linha.split(",")[0].trim())));
        /* nessa linha de código tem uma função lambda (função anônima) que utiliza o collection sort para ordenar os elementos do bloco de registros atuais,
        e dentro dele há a função 'Comparator.comparing' que é uma função que serve para comparar dois atributos, que nesse caso são os ids dos atletas, e
        dentro dessa função tem uma função lambda que funciona da seguinte forma, eu pego o dado atual e nele eu vou para o id do atleta, como no nosso csv os
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

    private void intercalarArquivo(Path[] arquivoTemp, Path arquivoDestino ) throws IOException{ // método para intercalar os arquivos criados
        try {
            int totalArq = arquivoTemp.length;
            BufferedReader[] readers = new BufferedReader[totalArq]; // crio um vetor de reader pra justamente conseguir ler a quantidade de arquivos ao mesmo tempo
            String[] proximaLinha = new String[totalArq]; // serve pra guardar a posicao em cada arquivo
            String cabecalho = "";

            for (int i = 0; i < totalArq; i++) {
                if (Files.exists(arquivoTemp[i]) && Files.size(arquivoTemp[i]) > 0) {
                    readers[i] = Files.newBufferedReader(arquivoTemp[i], StandardCharsets.UTF_8);

                    String linhaLida = readers[i].readLine();
                    if (cabecalho.isEmpty() && linhaLida != null) {
                        cabecalho = linhaLida; // guarda o cabeçalho do arquivo original
                    }

                    proximaLinha[i] = readers[i].readLine(); // le os arquivos
                }
            }
            try (BufferedWriter writer = Files.newBufferedWriter(arquivoDestino, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                if (!cabecalho.isEmpty()) { // abre o arquivo destino para escrever o cabeçalho
                    writer.write(cabecalho);
                    writer.newLine();
                }

                while (true) { // loop para encontrar o menor id entre o s registros abertos
                    int indiceMenor = -1;
                    String menorValor = null;

                    for (int i = 0; i < totalArq; i++) {
                        if (proximaLinha[i] != null) { // o if aqui vai procurando uma linha q nao esteja vazia
                            if (menorValor == null) {
                                menorValor = proximaLinha[i];
                                indiceMenor = i;
                            } else { // aqui ocorrre a comparação para caso menorvalor ja tenha outro valor sem ser null
                                int idAtual = Integer.parseInt(proximaLinha[i].split(",")[0].trim());
                                int idMenorAteAgora = Integer.parseInt(menorValor.split(",")[0].trim());

                                if (idAtual < idMenorAteAgora) { // se menor valor nao for mais null ele compara, e troca os valores do indice e a string
                                    menorValor = proximaLinha[i];
                                    indiceMenor = i;
                                }
                            }
                        }
                    }
                    if (indiceMenor == -1) { // caso tenha acabado os dados
                        break;
                    }
                    writer.write(menorValor); // escreve o menor no destino
                    writer.newLine();
                    proximaLinha[indiceMenor] = readers[indiceMenor].readLine();// pula o registro de onde o arquivo foi feito
                }
            }
            for (BufferedReader reader : readers) { // fecho os readers abertos
                if (reader != null) {
                    reader.close();
                }
            }
            System.out.println("Intercalação foi feita em " + arquivoDestino.getFileName());
        } catch (IOException e) {
            System.out.println("Não deu para intercalar");
            e.printStackTrace();
        }
    }
}
