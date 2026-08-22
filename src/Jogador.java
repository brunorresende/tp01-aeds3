import java.io.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Jogador implements Comparable<Jogador> {
    private int athleteId; // ID
    private String firstName; // Primeiro Nome
    private List<String> slug; // Nome e Sobrenome
    private String positionAbbreviation; // Posição
    private LocalDate timestamp; // Data


    //Construtor

    public Jogador(){}

    public Jogador(int athleteId, String firstName, List<String> slug, String positionAbbreviation, LocalDate timestamp){
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

    public List<String> getSlug() {
        return slug;
    }

    public void setSlug(List<String> slug) {
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

        //ID
        dos.writeInt(athleteId);

        //Nome
        dos.writeUTF(firstName);

        //Slug (nome completo)
        if (slug != null) {
            dos.writeInt(slug.size()); // salva a quantidade de itens na lista
            for (String item : slug) {
                dos.writeUTF(item);
            }
        } else {
            dos.writeInt(0);
        }

        //Posicao
        String pos = (positionAbbreviation != null) ? positionAbbreviation : "";
        while (pos.length() < 2) {
            pos += " "; // metodo para garantir que strings vazias ou de 1 caractere fiquem com 2 caracteres
        }
        if (pos.length() > 2) {
            pos = pos.substring(0, 2);
        }
        dos.writeChars(pos);

        //Data
        long dias = (timestamp != null) ? timestamp.toEpochDay() : 0;
        dos.writeLong(dias);

        return baos.toByteArray();
    }


    //  Preenche os atributos da instancia a partir de um array de bytes
    public void fromByteArray(byte[] ba) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(ba);
        DataInputStream dis = new DataInputStream(bais);

        //le int
        this.athleteId = dis.readInt();

        //le string
        this.firstName = dis.readUTF();

        //le slug
        int qtdSlug = dis.readInt();
        this.slug = new ArrayList<>(qtdSlug);
        for (int i = 0; i < qtdSlug; i++) {
            this.slug.add(dis.readUTF());
        }

        //le posicao
        char c1 = dis.readChar();
        char c2 = dis.readChar();
        this.positionAbbreviation = ("" + c1 + c2).trim();

        //le data
        long dias = dis.readLong();
        this.timestamp = (dias != 0) ? LocalDate.ofEpochDay(dias) : null;
    }


    @Override
    public String toString() {
        return String.format("ID=%d | Nome=%-15s | Posicao=%s | Data=%s | Slug=%s",
                athleteId, firstName, positionAbbreviation, timestamp, slug);
    }
    @Override
    public int compareTo(Jogador outro) { // tornando o jogador comparavel
        return Integer.compare(this.athleteId, outro.athleteId);
    }
}
