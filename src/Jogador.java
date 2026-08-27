import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


public class Jogador implements Comparable<Jogador> {
    private int athleteId; // ID
    private String firstName; // Primeiro Nome
    private String slug; // Nome e Sobrenome
    private String positionAbbreviation; // Posição
    private LocalDate timestamp; // Data


    //Construtor

    public Jogador(){}

    public Jogador(int athleteId, String firstName, String slug, String positionAbbreviation, LocalDate timestamp){
        this.athleteId = athleteId;
        this.firstName = firstName;
        this.slug = slug;
        this.positionAbbreviation = positionAbbreviation;
        this.timestamp = timestamp;
    }


    // Getters e Setters

    public int getAthleteId() {
        return athleteId;
    }

    public void setAthleteId(int athleteId) {
        this.athleteId = athleteId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getPositionAbbreviation() {
        return positionAbbreviation;
    }

    public void setPositionAbbreviation(String positionAbbreviation) {
        this.positionAbbreviation = positionAbbreviation;
    }

    public LocalDate getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDate timestamp) {
        this.timestamp = timestamp;
    }


    //Metodos de Serializacao

    //     Converte os atributos do objeto para um array de bytes

    public byte[] toByteArray() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        //  ID (campo int)
        dos.writeInt(athleteId);

        //  Nome (String de tamanho variavel)
        dos.writeUTF(firstName);

        //Slug (nome completo) - (Lista de valores com separador)
        dos.writeUTF(slug != null ? slug : "");

        //  Posicao (String de tamanho fixo)
        String pos = (positionAbbreviation != null) ? positionAbbreviation : "";
        while (pos.length() < 2) {
            pos += " "; // metodo para garantir que strings vazias ou de 1 caractere fiquem com 2 caracteres
        }
        if (pos.length() > 2) {
            pos = pos.substring(0, 2);
        }
        dos.writeChars(pos);

        //  Data
        long dias = (timestamp != null) ? timestamp.toEpochDay() : 0;
        dos.writeLong(dias);

        return baos.toByteArray();
    }


    //  Preenche os atributos da instancia a partir de um array de bytes
    public void fromByteArray(byte[] ba) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(ba);
        DataInputStream dis = new DataInputStream(bais);

        //lê o ID
        this.athleteId = dis.readInt();

        //lê o nome
        this.firstName = dis.readUTF();

        //lê o slug (nome-sobrenome)
        this.slug = dis.readUTF();

        //lê a posição
        char c1 = dis.readChar();
        char c2 = dis.readChar();
        this.positionAbbreviation = ("" + c1 + c2).trim();

        //lê a data
        long dias = dis.readLong();
        this.timestamp = (dias != 0) ? LocalDate.ofEpochDay(dias) : null;
    }
    //formatação da data para exibir
    private static final DateTimeFormatter formatoExibicao = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    @Override
    public String toString() {
        //adiciona a data formatada (DD/MM/AAAA)
        String dataFormatada = (timestamp != null) ? timestamp.format(formatoExibicao) : "N/A";
        return String.format("ID=%d | Nome=%-15s | Posicao=%s | Data=%s | Slug=%s",
                athleteId, firstName, positionAbbreviation, dataFormatada, slug);
    }
    @Override
    public int compareTo(Jogador outro) { // tornando o jogador comparavel
        return Integer.compare(this.athleteId, outro.athleteId);
    }
}
