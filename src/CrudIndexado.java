import java.io.*;

public class CrudIndexado {

    private final String nomeArquivo = "../jogadores.db";
    private final String nomeIndice = "indice_id.idx"; // indice (arvore B): athleteId -> posicao no jogadores.db
    private static final int ordemArvore = 64; // ordem da arvore B usada como indice primario
    private RandomAccessFile raf;
    private ArvoreB indice;
    private final byte lapideAtivo = (byte) ' '; // marcadores de lápide (mesmo padrão usado pelo ArquivoJogador)
    private final byte lapideExcluido = (byte) '*';

    public CrudIndexado() throws IOException {
        this.raf = new RandomAccessFile(nomeArquivo, "rw");
        if (raf.length() == 0) {// garante que o cabeçalho de 4 bytes exista mesmo num arquivo novo
            raf.seek(0);
            raf.writeInt(0);
        }
        this.indice = new ArvoreB(nomeIndice, ordemArvore);
        construirIndice();
    }
    public void construirIndice() throws IOException { // varre o jogadores.db e cria as entradas no indice
        indice.limpar();
        raf.seek(4); // pula o cabeçalho

        while (raf.getFilePointer() < raf.length()) {
            long posRegistro = raf.getFilePointer();
            byte lapide = raf.readByte();
            int tamanhoRegistro = raf.readInt();

            if (lapide == lapideAtivo) {
                byte[] buffer = new byte[tamanhoRegistro];
                raf.readFully(buffer);

                Jogador j = new Jogador();
                j.fromByteArray(buffer);
                indice.inserir(j.getAthleteId(), posRegistro);
            } else {
                raf.skipBytes(tamanhoRegistro); // registros excluídos nao entram no indice
            }
        }
    }
    public boolean create(Jogador novoJogador) {
        try {
            raf.seek(0); // leitura do 'byte 0' para verificar o maior id existente
            int ultimoId = raf.readInt();
            int novoId = ultimoId + 1; // gera o novo id que sera usado
            novoJogador.setAthleteId(novoId);
            raf.seek(0); // atualiza o cabeçalho com o novo último id
            raf.writeInt(novoId);
            long posRegistro = raf.length(); // fim do arquivo, onde o registro sera gravado
            raf.seek(posRegistro);
            byte[] bytes = novoJogador.toByteArray();
            raf.writeByte(lapideAtivo);
            raf.writeInt(bytes.length);
            raf.write(bytes);
            indice.inserir(novoId, posRegistro); // indexa o novo registro pelo athleteId

            return true;

        } catch (Exception e) {
            System.out.println("Erro ao criar registro: " + e.getMessage());
        }
        return false;
    }

    public Jogador read(int idProcurado) {
        try {
            long posRegistro = indice.buscar(idProcurado); // consulta o indice em vez de varrer o arquivo
            if (posRegistro == -1) {
                return null; // id nao existe no indice
            }

            raf.seek(posRegistro);
            byte lapide = raf.readByte();
            int tamanhoRegistro = raf.readInt();

            if (lapide != lapideAtivo) {
                return null; // registro ja foi excluido logicamente
            }

            byte[] buffer = new byte[tamanhoRegistro];
            raf.readFully(buffer);

            Jogador jogador = new Jogador();
            jogador.fromByteArray(buffer);
            return jogador;

        } catch (Exception e) {
            System.out.println("Erro ao ler registro: " + e.getMessage());
        }
        return null;
    }

    public boolean update(Jogador novoJogador) {
        try {
            long posRegistro = indice.buscar(novoJogador.getAthleteId());
            if (posRegistro == -1) {
                return false; // id nao existe no indice
            }

            raf.seek(posRegistro);
            byte lapide = raf.readByte();
            int tamanhoRegistro = raf.readInt();

            if (lapide != lapideAtivo) {
                return false; // registro já foi excluído
            }

            byte[] novosBytes = novoJogador.toByteArray();

            if (novosBytes.length <= tamanhoRegistro) { // reescreve dados mantendo o indicador de tamanho original
                raf.seek(posRegistro + 5); // pula lapide e tamanho
                raf.write(novosBytes);   // a posição do registro nao mudou, entao o indice não precisa ser atualizado


            } else {
                raf.seek(posRegistro); // novo registro é maior: marca o atual como deletado e insere no fim do arquivo
                raf.writeByte(lapideExcluido); // 'deleta' o registro atual
                long novaPosicao = raf.length();
                raf.seek(novaPosicao);
                raf.writeByte(lapideAtivo);
                raf.writeInt(novosBytes.length);
                raf.write(novosBytes);

                indice.inserir(novoJogador.getAthleteId(), novaPosicao); // atualiza (upsert) a posição no indice
            }
            return true;

        } catch (Exception e) {
            System.out.println("Erro ao atualizar: " + e.getMessage());
        }
        return false;
    }

    public boolean delete(int idProcurado) {
        try {
            long posRegistro = indice.buscar(idProcurado);
            if (posRegistro == -1) {
                return false; // id nao existe no indice
            }

            raf.seek(posRegistro);
            byte lapide = raf.readByte();

            if (lapide != lapideAtivo) {
                return false; // registro já estava excluído
            }

            raf.seek(posRegistro); // reposiciona o ponteiro no byte da lápide
            raf.writeByte(lapideExcluido); // marca com '*'
            return true;

        } catch (Exception e) {
            System.out.println("Erro ao deletar: " + e.getMessage());
        }
        return false;
    }

    public void fechar() throws IOException {
        if (raf != null) raf.close();
        if (indice != null) indice.fechar();
    }
}