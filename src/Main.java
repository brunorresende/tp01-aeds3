import java.sql.SQLOutput;
import java.time.LocalDate;
import java.util.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        try {
            ArquivoJogador arquivo = new ArquivoJogador();


            boolean rodando = true;

            while (rodando) {
                System.out.println("    MENU DE OPÇÕES  ");
                System.out.println("1 - Carregar base de dados");
                System.out.println("2 - Ler registro por ID");
                System.out.println("3 - Criar novo registro");
                System.out.println("4 - Atualizar registro");
                System.out.println("5 - Deletar registro");
                System.out.println("6 - Ordenar arquivo");
                System.out.println("0 - Sair");
                System.out.print("Escolha uma opção: ");

                int opcao = sc.nextInt();
                sc.nextLine(); // limpa o buffer

                switch (opcao) {
                    case 1: { // Carregar a base de dados
                        System.out.print("\nIsso vai apagar todos os dados atuais do arquivo e recarregar do CSV. Confirma? (s/n): ");
                        String confirma = sc.nextLine().trim();

                        if (confirma.equalsIgnoreCase("s")) {
                            arquivo.realizarCargaCSV("players-selected-columns 2.csv"); //Carga da base de dados
                            System.out.println("Base de dados carregada com sucesso!");
                        } else {
                            System.out.println("Carga cancelada.");
                        }
                        break;
                    }
                    case 2: { // Read
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
                    case 3: { // Create
                        System.out.println("\nNOVO CADASTRO");

                        Jogador novo = lerDadosDoTeclado(sc); //chama funcao para preencher os dados manualmente

                        boolean sucesso = arquivo.create(novo);

                        if (sucesso) {
                            System.out.println("Novo jogador com ID " + novo.getAthleteId() + "cadastrado com sucesso!");
                        } else {
                            System.out.println("Erro ao cadastrar jogador.");
                        }
                        break;
                    }
                    case 4: { // Update
                        System.out.println("\nATUALIZAR REGISTRO");
                        System.out.print("Digite o ID do jogador a ser atualizado: ");
                        int id = sc.nextInt();
                        sc.nextLine();

                        Jogador existente = arquivo.read(id); //busca o jogador primeiro
                        if (existente == null) {
                            System.out.println("Jogador com ID " + id + " não foi encontrado.");
                        } else {
                            System.out.println("Dados atuais: " + existente);
                            System.out.println("\nInforme os novos dados:");
                            Jogador atualizado = lerDadosDoTeclado(sc); //chama funcao para preencher os dados manualmente

                            //reatribui manualmente o id correto para sobrescrever o que veio da funcao
                            atualizado.setAthleteId(id);
                            boolean sucesso = arquivo.update(atualizado);

                            if (sucesso) {
                                System.out.println("Registro atualizado com sucesso!");
                            } else {
                                System.out.println("Erro ao atualizar registro.");
                            }
                        }
                        break;
                    }
                    case 5: { // Delete
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

                    case 6: { // Ordenação
                        System.out.println("Digite a quantidade de arquivos que voce quer utilizar entre 3-5");
                        int quantArq = sc.nextInt();
                        System.out.println("Agora digite a quantidade de registros por blocos");
                        int quantBlocos = sc.nextInt();
                        double inicio = new Date().getTime();
                        Ordenacao ordem = new Ordenacao(arquivo); // passando a quantidade de arquivo que o usario quer usar para intercalar
                        ordem.Ordenar(quantArq, quantBlocos);
                        double fim = new Date().getTime();
                        double tempoExecucao = (fim - inicio) / 1000.0;

                        System.out.println("A ordenação com " + quantArq + " arquivos durou " + tempoExecucao + "s");

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
    private static Jogador lerDadosDoTeclado(Scanner sc) {
        //leitura do jogador
        System.out.print("Nome: ");
        String nome = sc.nextLine().trim();

        //leitura da posição
        System.out.print("Posição (ex: G, D, M, F): ");
        String posicao = sc.nextLine().trim();

        //leitura do slug
        System.out.print("Slug (separado por hífen, ex: nome-sobrenome): ");
        String slugInput = sc.nextLine().trim();
        List<String> listaSlug = new ArrayList<>();
        if (!slugInput.isEmpty()) {
            listaSlug = Arrays.asList(slugInput.split("-"));
        }

        //leitura da data
        System.out.print("Data (AAAA-MM-DD ou ENTER para data de hoje): ");
        String dataInput = sc.nextLine().trim();
        LocalDate data;

        //adiciona a data do momento de execução se o usuário não digitar
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

        return new Jogador(0, nome, listaSlug, posicao, data); //id = 0, muda dentro da funcao create ou no case do update
    }
}