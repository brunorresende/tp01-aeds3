import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        try {
            ArquivoJogador arquivo = new ArquivoJogador();



            arquivo.realizarCargaCSV("players-selected-columns 2.csv");

            boolean rodando = true;

            while (rodando) {
                System.out.println("    MENU DE OPÇÕES  ");
                System.out.println("1 - Ler registro por ID");
                System.out.println("2 - Criar novo registro");
                System.out.println("3 - Atualizar registro");
                System.out.println("4 - Deletar registro");
                System.out.println("0 - Sair");
                System.out.print("Escolha uma opção: ");

                int opcao = sc.nextInt();
                sc.nextLine(); // limpa o buffer

                switch (opcao) {
                    case 1: { // READ
                        System.out.print("\nDigite o ID do jogador: ");
                        int id = sc.nextInt();
                        sc.nextLine();

                        Jogador j = arquivo.read(id);
                        if (j != null) {
                            System.out.println(j);
                        } else {
                            System.out.println("Registro não encontrado.");
                        }
                        break;
                    }
                    case 2: { // CREATE
                        System.out.println("\nNOVO CADASTRO");
                        System.out.print("Digite o ID do novo jogador: ");
                        int id = sc.nextInt();
                        sc.nextLine();

                        Jogador novo = lerDadosDoTeclado(sc, id);
                        boolean sucesso = arquivo.create(novo);

                        if (sucesso) {
                            System.out.println("Jogador cadastrado com sucesso!");
                        } else {
                            System.out.println("Erro ao cadastrar jogador.");
                        }
                        break;
                    }
                    case 3: { // UPDATE
                        System.out.println("\nATUALIZAR REGISTRO");
                        System.out.print("Digite o ID do jogador a ser atualizado: ");
                        int id = sc.nextInt();
                        sc.nextLine();

                        Jogador existente = arquivo.read(id);
                        if (existente == null) {
                            System.out.println("Jogador com ID " + id + " não foi encontrado.");
                        } else {
                            System.out.println("Dados atuais: " + existente);
                            System.out.println("\nInforme os novos dados:");
                            Jogador atualizado = lerDadosDoTeclado(sc, id);

                            boolean sucesso = arquivo.update(atualizado);
                            if (sucesso) {
                                System.out.println("Registro atualizado com sucesso!");
                            } else {
                                System.out.println("Erro ao atualizar registro.");
                            }
                        }
                        break;
                    }
                    case 4: { // DELETE
                        System.out.println("\nEXCLUIR REGISTRO");
                        System.out.print("Digite o ID do jogador a ser deletado: ");
                        int id = sc.nextInt();
                        sc.nextLine();

                        boolean sucesso = arquivo.delete(id);
                        if (sucesso) {
                            System.out.println("Registro excluído com sucesso!");
                        } else {
                            System.out.println("Erro: ID não encontrado ou já excluído.");
                        }
                        break;
                    }
                    case 0: {
                        System.out.println("Encerrando aplicação...");
                        rodando = false;
                        break;
                    }
                    default:
                        System.out.println("Opção inválida! Tente novamente.");
                        break;
                }
            }

            arquivo.fechar();
            sc.close();

        } catch (Exception e) {
            System.out.println("Erro de execução: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // metodo auxiliar para a leitura no terminal
    private static Jogador lerDadosDoTeclado(Scanner sc, int id) {
        System.out.print("Nome: ");
        String nome = sc.nextLine().trim();

        System.out.print("Posição (ex: G, D, M, F): ");
        String posicao = sc.nextLine().trim();

        System.out.print("Slug (separado por hífen, ex: nome-sobrenome): ");
        String slugInput = sc.nextLine().trim();
        List<String> listaSlug = new ArrayList<>();
        if (!slugInput.isEmpty()) {
            listaSlug = Arrays.asList(slugInput.split("-"));
        }

        System.out.print("Data (AAAA-MM-DD ou ENTER para data de hoje): ");
        String dataInput = sc.nextLine().trim();
        LocalDate data;

        if (dataInput.isEmpty()) {
            data = LocalDate.now();
        } else {
            try {
                data = LocalDate.parse(dataInput);
            } catch (Exception e) {
                System.out.println("Data em formato inválido! Assumindo data atual.");
                data = LocalDate.now();
            }
        }

        return new Jogador(id, nome, listaSlug, posicao, data);
    }
}