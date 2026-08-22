import java.io.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArquivoJogador {
    private String NOME_ARQUIVO = "jogadores.db";
    private long ponteiroLeituraOrdenacao = 4; // começa depois do cabeçalho
    private RandomAccessFile raf;

    // marcadores de lápide
    private final byte LAPIDE_ATIVO = (byte) ' ';
    private final byte LAPIDE_EXCLUIDO = (byte) '*';

    public ArquivoJogador() throws FileNotFoundException {
        //abrir arquivo no modo de leitura e escrita
        this.raf = new RandomAccessFile(NOME_ARQUIVO, "rw");
    }

    //  CARREGA A BASE DE DADOS

    public void realizarCargaCSV(String caminhoCSV){
        try {

            raf.setLength(0);

            BufferedReader br = new BufferedReader(new FileReader(caminhoCSV));
            String linha = br.readLine(); // pula o cabeçalho do CSV



            // 4 primeiros bytes para o último ID
            raf.seek(0);
            raf.writeInt(0);

            int ultimoID = 0;

            while ((linha = br.readLine()) != null) {
                // preserva colunas vazias
                String[] dados = linha.split(",", -1);

                int id = Integer.parseInt(dados[0]);
                String nome = dados[1];

                // slug separado por hífen "-"
                List<String> listaSlug = new ArrayList<>();
                if (!dados[2].isEmpty()) {
                    listaSlug = Arrays.asList(dados[2].split("-"));
                }

                String posicao = dados[3];

                // salva a data descartando o horário
                LocalDate data = null;
                if (!dados[4].isEmpty()) {
                    String apenasData = dados[4].split(" ")[0];
                    data = LocalDate.parse(apenasData);
                }

                // instancia e serializa o objeto
                Jogador jogador = new Jogador(id, nome, listaSlug, posicao, data);
                byte[] vetorBytes = jogador.toByteArray();


                // ESCRITA DO REGISTRO
                raf.writeByte(LAPIDE_ATIVO);    // lápide
                raf.writeInt(vetorBytes.length);    // indicador de tamanho em bytes
                raf.write(vetorBytes);  // vetor de bytes do objeto

                if (id > ultimoID) {
                    ultimoID = id;
                }
            }
            br.close();

            // volta ao início do arquivo para gravar o último ID no cabeçalho
            raf.seek(0);
            raf.writeInt(ultimoID);

        } catch (Exception e) {
            System.out.println("Erro ao carregar CSV: " + e.getMessage());
            e.printStackTrace();
        }
    }


    //  CRUD

    // CREATE
    public boolean create(Jogador novoJogador) {
        try {
            // Atualiza o cabeçalho se o novo ID for o maior
            raf.seek(0);
            int ultimoId = raf.readInt();

            if (novoJogador.getAthleteId() > ultimoId) {
                raf.seek(0);
                raf.writeInt(novoJogador.getAthleteId());
            }

            // escreve o registro no final do arquivo
            raf.seek(raf.length());
            byte[] bytes = novoJogador.toByteArray();

            raf.writeByte(LAPIDE_ATIVO);
            raf.writeInt(bytes.length);
            raf.write(bytes);
            return true;
        } catch (Exception e) {
            System.out.println("Erro ao criar registro: " + e.getMessage());
        }
        return false;
    }


    //  READ
    public Jogador read(int idProcurado) {
        try {
            raf.seek(4); // Posiciona após os 4 bytes do cabeçalho

            while (raf.getFilePointer() < raf.length()) {
                byte lapide = raf.readByte();
                int tamanhoRegistro = raf.readInt();

                if (lapide == LAPIDE_ATIVO) {
                    byte[] buffer = new byte[tamanhoRegistro];
                    raf.readFully(buffer); // Lê os bytes e avança o ponteiro automaticamente

                    Jogador jogador = new Jogador();
                    jogador.fromByteArray(buffer);

                    if (jogador.getAthleteId() == idProcurado) {
                        return jogador;
                    }
                    // Se o ID não for o procurado, não fazemos nada aqui.
                    // O readFully já avançou o ponteiro até o início do próximo registro.

                } else {
                    // Este 'else' pertence ao 'if (lapide == LAPIDE_ATIVO)'.
                    // Se o registro foi excluído ('*'), aí sim usamos skipBytes:
                    raf.skipBytes(tamanhoRegistro);
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao ler registro: " + e.getMessage());
        }
        return null;
    }


    //  DELETE
    public boolean delete(int idProcurado) {
        try {
            raf.seek(4); // Pula os 4 bytes do cabeçalho

            while (raf.getFilePointer() < raf.length()) {
                long posRegistro = raf.getFilePointer();
                byte lapide = raf.readByte();
                int tamanhoRegistro = raf.readInt();

                if (lapide == LAPIDE_ATIVO) {
                    byte[] buffer = new byte[tamanhoRegistro];
                    raf.readFully(buffer);

                    Jogador j = new Jogador();
                    j.fromByteArray(buffer);

                    if (j.getAthleteId() == idProcurado) {
                        raf.seek(posRegistro); // volta para a posição da lapide
                        raf.writeByte(LAPIDE_EXCLUIDO); // marca com '*'
                        return true;
                    }
                } else {
                    raf.skipBytes(tamanhoRegistro);
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao deletar: " + e.getMessage());
        }
        return false;
    }

    //UPDATE
    public boolean update(Jogador novoJogador){
        try {
            raf.seek(4);

            while (raf.getFilePointer() < raf.length()) {
                long posRegistro = raf.getFilePointer();
                byte lapide = raf.readByte();
                int tamanhoRegistro = raf.readInt();

                if (lapide == LAPIDE_ATIVO) {
                    byte[] buffer = new byte[tamanhoRegistro];
                    raf.readFully(buffer);

                    Jogador j = new Jogador();
                    j.fromByteArray(buffer);

                    if (j.getAthleteId() == novoJogador.getAthleteId()) {
                        byte[] novosBytes = novoJogador.toByteArray();

                        if (novosBytes.length <= tamanhoRegistro) {
                            // coube no espaço original: sobrescreve dados mantendo o indicador de tamanho original
                            raf.seek(posRegistro + 5); // pula lapide e tamanho
                            raf.write(novosBytes);

                        } else {
                            // ficou maior: marca o atual como deletado e insere no fim do arquivo
                            raf.seek(posRegistro);
                            raf.writeByte(LAPIDE_EXCLUIDO);

                            raf.seek(raf.length());
                            raf.writeByte(LAPIDE_ATIVO);
                            raf.writeInt(novosBytes.length);
                            raf.write(novosBytes);
                        }
                        return true;
                    }
                } else {
                    raf.skipBytes(tamanhoRegistro);
                }
            }
        } catch (Exception e) {
        System.out.println("Erro ao atualizar: " + e.getMessage());
    }
    return false;
    }
    public void resetarLeituraOrdenacao() {
        ponteiroLeituraOrdenacao = 4;
    }

    public Jogador proximoAtivo() throws IOException {
        raf.seek(ponteiroLeituraOrdenacao); // vai pra onde parou da ultima vez

        while (raf.getFilePointer() < raf.length()) {
            byte lapide = raf.readByte();
            int tamanhoRegistro = raf.readInt();

            byte[] buffer = new byte[tamanhoRegistro];
            raf.readFully(buffer);

            ponteiroLeituraOrdenacao = raf.getFilePointer(); // guarda onde ficou

            if (lapide == LAPIDE_ATIVO) {
                Jogador j = new Jogador();
                j.fromByteArray(buffer);
                return j; // achou um ativo, devolve
            }
            // se foi excluído, o while continua e tenta o próximo
        }

        return null; // chegou ao fim do arquivo
    }

    public void fechar() throws IOException {
        if (raf != null) raf.close();
    }
}