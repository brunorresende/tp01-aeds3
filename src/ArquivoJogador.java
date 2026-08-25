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


            BufferedReader br = new BufferedReader(new FileReader(caminhoCSV));
            String linha = br.readLine(); // pula o cabeçalho do CSV



            // salva os 4 primeiros bytes para armazenar o último ID no arquivo
            raf.seek(0);
            raf.writeInt(0);

            int ultimoID = 0;

            while ((linha = br.readLine()) != null) {
                // o parâmetro -1 garante a leitura de colunas vazias no final da linha
                String[] dados = linha.split(",", -1);

                //  ID
                int id = Integer.parseInt(dados[0]);

                //  Nome
                String nome = dados[1]; //Salva o nome

                //  Slug
                List<String> listaSlug = new ArrayList<>(); //utilizando lista
                if (!dados[2].isEmpty()) {
                    listaSlug = Arrays.asList(dados[2].split("-")); //strings separado por hífen "-"
                }

                //  Posição
                String posicao = dados[3];

                //  Data (formato ISO (AAAA-MM-DD))
                LocalDate data = null;
                if (!dados[4].isEmpty()) {
                    String apenasData = dados[4].split(" ")[0]; // descartando componente de hora
                    data = LocalDate.parse(apenasData);
                }

                // serializa o objeto em array de bytes
                Jogador jogador = new Jogador(id, nome, listaSlug, posicao, data);
                byte[] vetorBytes = jogador.toByteArray();


                // gravação do registro
                raf.writeByte(LAPIDE_ATIVO);    // lápide (1B)
                raf.writeInt(vetorBytes.length);    // indicador de tamanho em bytes (4B)
                raf.write(vetorBytes);  // vetor de bytes do objeto (NB)

                if (id > ultimoID) {
                    ultimoID = id;
                }
            }
            br.close();

            // atualiza o cabeçalho no byte zero com o maior ID encontrado
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
            // leitura do (byte 0) para verificar o maior ID existente
            raf.seek(0);
            int ultimoId = raf.readInt();
            int novoId = ultimoId + 1; //gera o novo ID que sera usado

            novoJogador.setAthleteId(novoId); // sobrescreve qualquer ID que tenha vindo de outra funcao

            // atualiza o cabeçalho com o novo último ID
            raf.seek(0);
            raf.writeInt(novoId);

            raf.seek(raf.length()); // posiciona o ponteiro no fim do arquivo para escrita
            byte[] bytes = novoJogador.toByteArray(); // serializa o objeto em um array de bytes

            //escreve o padrão: lapide + tamanho + dados
            raf.writeByte(LAPIDE_ATIVO);
            raf.writeInt(bytes.length);
            raf.write(bytes);

            return true;

        } catch (Exception e) {
            System.out.println("Erro ao criar registro: " + e.getMessage());
        }
        return false;
    }


    //  READ (busca sequencial)
    public Jogador read(int idProcurado) {
        try {
            raf.seek(4); // Posiciona após os 4 bytes do cabeçalho com o maior ID

            //Percorre o arquivo registro por registro até o fim do arquivo
            while (raf.getFilePointer() < raf.length()) {
                byte lapide = raf.readByte();
                int tamanhoRegistro = raf.readInt();

                if (lapide == LAPIDE_ATIVO) {
                    //aloca o buffer e le todos os bytes do registro
                    byte[] buffer = new byte[tamanhoRegistro];
                    raf.readFully(buffer);

                    // desserializa para reconstruir o objeto
                    Jogador jogador = new Jogador();
                    jogador.fromByteArray(buffer);

                    //se for o registro procurado, interrompe a busca e retorna o objeto
                    if (jogador.getAthleteId() == idProcurado) {
                        return jogador;
                    }
                    // Se o ID não for o procurado, o ponteiro já está pronto no próximo registro

                } else {
                    // registro marcado como excluído, avança o ponteiro sem carregar os dados em RAM
                    raf.skipBytes(tamanhoRegistro);
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao ler registro: " + e.getMessage());
        }
        return null; // retorna null se alcançar o final do arquivo sem localizar o ID
    }


    //  DELETE
    public boolean delete(int idProcurado) {
        try {
            raf.seek(4); // Posiciona após os 4 bytes do cabeçalho com o maior ID

            //Percorre o arquivo registro por registro até o fim do arquivo
            while (raf.getFilePointer() < raf.length()) {
                //salva a posicao atual para que o sistema volte a esse ponto depois
                long posRegistro = raf.getFilePointer();
                byte lapide = raf.readByte();
                int tamanhoRegistro = raf.readInt();

                if (lapide == LAPIDE_ATIVO) {
                    //lê o payload do registro para identificar o ID
                    byte[] buffer = new byte[tamanhoRegistro];
                    raf.readFully(buffer);

                    Jogador j = new Jogador();
                    j.fromByteArray(buffer);

                    // se for o ID procurado, volta até a lápide e aplica o marcador de exclusão
                    if (j.getAthleteId() == idProcurado) {
                        raf.seek(posRegistro); // reposiciona o ponteiro no byte da lápide
                        raf.writeByte(LAPIDE_EXCLUIDO); // marca com '*'
                        return true;
                    }
                } else {
                    // salta os bytes de registros já excluídos sem alocar RAM
                    raf.skipBytes(tamanhoRegistro);
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao deletar: " + e.getMessage());
        }
        return false; // retorna false se o ID não for encontrado
    }

    //UPDATE
    public boolean update(Jogador novoJogador){
        try {
            raf.seek(4); //Posiciona após os 4 bytes do cabeçalho com o maior ID

            while (raf.getFilePointer() < raf.length()) {
                long posRegistro = raf.getFilePointer(); // Guarda início do registro
                byte lapide = raf.readByte();
                int tamanhoRegistro = raf.readInt();

                if (lapide == LAPIDE_ATIVO) {
                    byte[] buffer = new byte[tamanhoRegistro];
                    raf.readFully(buffer);

                    Jogador j = new Jogador();
                    j.fromByteArray(buffer);

                    if (j.getAthleteId() == novoJogador.getAthleteId()) {
                        byte[] novosBytes = novoJogador.toByteArray();

                        //se coube no espaço original: sobrescreve dados mantendo o indicador de tamanho original
                        if (novosBytes.length <= tamanhoRegistro) {
                            raf.seek(posRegistro + 5); // pula lapide e tamanho
                            raf.write(novosBytes);

                        } else {
                            // novo registro é maior: marca o atual como deletado e insere no fim do arquivo
                            raf.seek(posRegistro);
                            raf.writeByte(LAPIDE_EXCLUIDO); //"deleta" o registro atual

                            raf.seek(raf.length()); // move para o final do arquivo
                            raf.writeByte(LAPIDE_ATIVO);
                            raf.writeInt(novosBytes.length);
                            raf.write(novosBytes);
                        }
                        return true;
                    }
                } else {
                    raf.skipBytes(tamanhoRegistro); // pula registro excluído
                }
            }
        } catch (Exception e) {
        System.out.println("Erro ao atualizar: " + e.getMessage());
        }
        return false;
    }

    // reseta o ponteiro para o inicio dos dados
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