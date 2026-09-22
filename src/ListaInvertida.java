import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

public class ListaInvertida {
    public enum Campo { NOME, POSICAO } // define em quais campos podem ser indexados

    private String arquivoDados;
    private String arquivoChaves;
    private String arquivoListas;
    private Campo campo;
    private static final int tamanhoCabecalho = 4; // 4 bytes do id no topo do arquivo principal
    private static final byte lapideAtiva = (byte) ' ';
    private static final int tamanhoChave = 40; // tamanho fixo da string normalizada em bytes
    private static final int tamanhoEntradaChave = tamanhoChave + 8; // tamanho da string mais o ponteiro
    private static final int tamanhoEntradaLista = 16; // endereço e ponteiro para o proximo

    public ListaInvertida(String arquivoDados, Campo campo) {
        this.arquivoDados = arquivoDados;
        this.campo = campo;
        String base = "lista_" + campo.name().toLowerCase();
        this.arquivoChaves = base + "_chaves.idx"; // termos de busca ordenados com tam fixo
        this.arquivoListas = base + "_listas.idx";
    }

    public void gerar() throws Exception {
        TreeMap<String, List<Long>> dicionario = new TreeMap<>();
        File file = new File(arquivoDados);
        if (!file.exists()) { // 2 verificações para ver se a base de dados foi carregada corretamente
            throw new FileNotFoundException("O arquivo de dados '" + arquivoDados + "' não foi encontrado. Execute a opção 1 no menu primeiro!");
        }
        if (file.length() <= tamanhoCabecalho) {
            throw new IllegalStateException("O arquivo '" + arquivoDados + "' está vazio ou contém apenas o cabeçalho.");
        }

        try (RandomAccessFile arq = new RandomAccessFile(arquivoDados, "r")) {
            arq.seek(tamanhoCabecalho); // não pega o id máximo do cabeçalho

            while (arq.getFilePointer() < arq.length()) { // percorre o 'jogadores.db' por completo, le a lapide e o tamanho do registro, se o a lapide for marcada como 'viva' puxa os dados, caso nao, apenas pula o registro
                long enderecoAtual = arq.getFilePointer();
                byte lapide = arq.readByte();
                int tamanho = arq.readInt();

                if (lapide == lapideAtiva) {
                    byte[] bytesRegistro = new byte[tamanho];
                    arq.readFully(bytesRegistro);

                    Jogador jogador = new Jogador();
                    jogador.fromByteArray(bytesRegistro); // converte os bytes no objeto Jogador

                    String chave = normalizarString(extrairChave(jogador));
                    if (!chave.isEmpty()) {
                        dicionario.computeIfAbsent(chave, k -> new ArrayList<>()).add(enderecoAtual);
                    }
                } else {
                    arq.skipBytes(tamanho); // pula registros excluídos
                }
            }
        }

        gravarIndicesNoDisco(dicionario);
    }

    private void gravarIndicesNoDisco(TreeMap<String, List<Long>> dicionario) throws IOException {
        try (RandomAccessFile arqChaves = new RandomAccessFile(arquivoChaves, "rw"); RandomAccessFile arqListas = new RandomAccessFile(arquivoListas, "rw")) {

            arqChaves.setLength(0);// limpa caso o programa rode mais de uma vez
            arqListas.setLength(0);

            long posicaoLista = 0;

            for (Map.Entry<String, List<Long>> entrada : dicionario.entrySet()) {
                arqChaves.write(formatarStringFixa(entrada.getKey()));
                arqChaves.writeLong(posicaoLista); // grava no arquivo, 40 bytes fixos, e a posição em que a primeira entrada da lista dessa chave vai ficar no arquivo de lista

                List<Long> enderecos = entrada.getValue();
                for (int i = 0; i < enderecos.size(); i++) { // percorre os endereços daquela chave e grava, um a um
                    arqListas.writeLong(enderecos.get(i)); // caso seja o ultimo elemnento aponta para -1, senao, aponta para o proximo
                    long proximo = (i == enderecos.size() - 1) ? -1 : posicaoLista + 1;arqListas.writeLong(proximo);
                    posicaoLista++;
                }
            }
        }
    }

    public List<Jogador> buscar(String termoBusca) throws Exception {
        List<Jogador> resultados = new ArrayList<>();
        String chaveBuscada = normalizarString(termoBusca);

        try (RandomAccessFile arqChaves = new RandomAccessFile(arquivoChaves, "r"); RandomAccessFile arqListas = new RandomAccessFile(arquivoListas, "r"); RandomAccessFile arqDados = new RandomAccessFile(arquivoDados, "r")) {
            long ponteiroLista = buscaBinariaChave(arqChaves, chaveBuscada); // devolve a posicao da primeira entrada da lista dessa chave
            if (ponteiroLista != -1) {
                while (ponteiroLista != -1) { // o while percorre a lista, calcula o offset em bytes, le o endereço e o ponteiro para o proximo 'nó' e avança ate achar o -1
                    arqListas.seek(ponteiroLista * tamanhoEntradaLista);
                    long enderecoDB = arqListas.readLong();
                    ponteiroLista = arqListas.readLong();

                    arqDados.seek(enderecoDB);
                    byte lapide = arqDados.readByte();
                    int tamanho = arqDados.readInt();

                    if (lapide == lapideAtiva) {
                        byte[] bytesRegistro = new byte[tamanho];
                        arqDados.readFully(bytesRegistro);

                        Jogador j = new Jogador();
                        j.fromByteArray(bytesRegistro);
                        resultados.add(j);
                    }
                }
            }
        }
        return resultados;
    }

    private long buscaBinariaChave(RandomAccessFile arqChaves, String chave) throws IOException {
        long inicio = 0;
        long fim = (arqChaves.length() / tamanhoEntradaChave) - 1; // busca binaria realizada em arquivo

        while (inicio <= fim) {
            long meio = (inicio + fim) / 2;
            arqChaves.seek(meio * tamanhoEntradaChave); // a cada interação pula para o registro do meio, le os 40 bytes da chave e o 'long' do ponteiro em seguida

            byte[] bytesLidos = new byte[tamanhoChave];
            arqChaves.readFully(bytesLidos);
            String chaveLida = new String(bytesLidos, StandardCharsets.UTF_8).trim();
            long ponteiroLista = arqChaves.readLong();

            int comparacao = chave.compareTo(chaveLida); // se for 0, achou, se for negativo a chave vem antes, descartando mediante a cada resultado
            if (comparacao == 0)
                return ponteiroLista;
            else if (comparacao < 0)
                fim = meio - 1;
            else
                inicio = meio + 1;
        }
        return -1;
    }

    private String extrairChave(Jogador j) {
        return (campo == Campo.POSICAO) ? j.getPositionAbbreviation() : j.getFirstName(); // adaptar os getters para o nome na classe jogador
    }

    private String normalizarString(String s) { // faciliatr o uso da string para comparação
        if (s == null) return "";
        String semAcento = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String n = semAcento.toLowerCase();
        return n.length() > tamanhoChave ? n.substring(0, tamanhoChave) : n;
    }

    private byte[] formatarStringFixa(String s) { // funçao que 'garante' que as variações em que um nome é escrito, no final resulte todos no mesmo
        byte[] buffer = new byte[tamanhoChave];
        byte[] bytesString = s.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytesString, 0, buffer, 0, Math.min(bytesString.length, tamanhoChave));
        return buffer;
    }
}