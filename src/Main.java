import java.sql.SQLOutput;
import java.time.LocalDate;
import java.util.Date;
import java.util.Scanner;
import java.util.List;

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
                System.out.println("7 - Gerar Lista Invertida");
                System.out.println("8 - Gerar CRUD Indexado");
                System.out.println("9 - Compactar base de dados (Huffman e LZW)");
                System.out.println("10 - Descompactar base de dados");
                System.out.println("0 - Sair");
                System.out.print("Escolha uma opção: ");

                int opcao = lerInt(sc);

                try {
                    switch (opcao) {
                        case 1: { // Carregar a base de dados
                            System.out.print("\nIsso vai apagar todos os dados atuais do arquivo e recarregar do CSV. Confirma? (s/n): ");
                            String confirma = sc.nextLine().trim();

                            if (confirma.equalsIgnoreCase("s")) {
                                if (arquivo.realizarCargaCSV(ArquivoJogador.CSV)) { //Carga da base de dados
                                    System.out.println("Base de dados carregada com sucesso!");
                                } else {
                                    System.out.println("A carga falhou. A base de dados anterior foi mantida.");
                                }
                            } else {
                                System.out.println("Carga cancelada.");
                            }
                            break;
                        }
                        case 2: { // Read
                            System.out.print("\nDigite o ID do jogador: ");
                            int id = lerInt(sc);

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
                                System.out.println("Novo jogador com ID " + novo.getAthleteId() + " cadastrado com sucesso!");
                            } else {
                                System.out.println("Erro ao cadastrar jogador.");
                            }
                            break;
                        }
                        case 4: { // Update
                            System.out.println("\nATUALIZAR REGISTRO");
                            System.out.print("Digite o ID do jogador a ser atualizado: ");
                            int id = lerInt(sc);

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
                            int id = lerInt(sc);

                            boolean sucesso = arquivo.delete(id);
                            if (sucesso) {
                                System.out.println("Registro excluído com sucesso!");
                            } else {
                                System.out.println("Erro: ID não encontrado ou já excluído.");
                            }
                            break;
                        }

                        case 6: { // Ordenação
                            System.out.print("Digite a quantidade de arquivos que voce quer utilizar (>= 2): ");
                            int quantArq = lerInt(sc);
                            System.out.print("Agora digite a quantidade de registros por blocos (>= 1): ");
                            int quantReg = lerInt(sc);
                            if (quantArq < 2 || quantReg < 1) {
                                System.out.println("Valores inválidos: use pelo menos 2 arquivos e 1 registro por bloco.");
                                break;
                            }
                            double inicio = new Date().getTime();
                            Ordenacao ordem = new Ordenacao(arquivo);
                            ordem.gerarArq(quantArq, quantReg); // passando a quantidade de arquivo que o usario quer usar para intercalar
                            double fim = new Date().getTime();
                            double tempoExecucao = (fim - inicio) / 1000.0;

                            System.out.println("A ordenação com " + quantArq + " arquivos durou " + tempoExecucao + "s");

                            break;
                        }

                        case 7: {
                            System.out.println("Comando entendido! Vamos gerar a lista invertida!");
                            ListaInvertida lista = new ListaInvertida(ArquivoJogador.ARQUIVO_DADOS, ListaInvertida.Campo.NOME);
                            double inicio = new Date().getTime();
                            lista.gerar();
                            double fim = new Date().getTime();
                            double tempoGasto = (fim - inicio) / 1000.0;

                            System.out.println("Lista Gerada! O tempo levado pelo código foi " + tempoGasto + "s! Escolha a quantidade de buscas que quer realizar");
                            int quantBusca = lerInt(sc);
                            for (int i = 0; i < quantBusca; i++) {
                                double inicioBusca = new Date().getTime();
                                System.out.println("Digite a  palavra que deseja ser buscada");
                                String palavraBusca = sc.nextLine().trim(); // linha inteira: o termo é o nome completo, pode ter espaços
                                List<Jogador> regBusca = lista.buscar(palavraBusca);
                                double fimBusca = new Date().getTime();
                                double tempoBusca = (fimBusca - inicioBusca) / 1000.0;
                                System.out.println("O tempo de busca foi  " + tempoBusca + "s e os resultados obtidos foram os seguintes: ");

                                if (regBusca.isEmpty()) {
                                    System.out.println("Nenhum jogador encontrado com o termo informado.");
                                } else {
                                    for (Jogador jogadorEncontrado : regBusca) {
                                        System.out.println(jogadorEncontrado.toString());
                                    }
                                }
                            }
                            break;
                        }
                        case 8:{
                            System.out.println("Perfeito vamos criar o CRUD Indexado");
                            double inicio = new Date().getTime();
                            CrudIndexado novoCrud = new CrudIndexado();
                            double fim = new Date().getTime();
                            double tempoExecucao = (fim - inicio) / 1000;
                            System.out.println("O CRUD Indexado foi gerado com sucesso!Em " + tempoExecucao + "s");
                            boolean rodandoCrudIndexado = true;

                            while (rodandoCrudIndexado) {
                                System.out.println("\n    CRUD INDEXADO (Árvore B)  ");
                                System.out.println("1 - Ler registro por ID");
                                System.out.println("2 - Criar novo registro");
                                System.out.println("3 - Atualizar registro");
                                System.out.println("4 - Deletar registro");
                                System.out.println("0 - Voltar ao menu principal");
                                System.out.print("Escolha uma opção: ");
                                int opcaoCrud = lerInt(sc);
                                switch (opcaoCrud) {
                                    case 1: { // Read
                                        System.out.print("\nDigite o ID do jogador: ");
                                        int id = lerInt(sc);
                                        Jogador j = novoCrud.read(id);
                                        if (j != null) {
                                            System.out.println(j);
                                        } else {
                                            System.out.println("Registro não encontrado.");
                                        }
                                        break;
                                    }
                                    case 2: { // Create
                                        System.out.println("\nNOVO CADASTRO");
                                        Jogador novo = lerDadosDoTeclado(sc); //chama funcao para preencher os dados manualmente
                                        boolean sucesso = novoCrud.create(novo);

                                        if (sucesso) {
                                            System.out.println("Novo jogador com ID " + novo.getAthleteId() + " cadastrado com sucesso!");
                                        } else {
                                            System.out.println("Erro ao cadastrar jogador.");
                                        }
                                        break;
                                    }
                                    case 3: { // Update
                                        System.out.println("\nATUALIZAR REGISTRO");
                                        System.out.print("Digite o ID do jogador a ser atualizado: ");
                                        int id = lerInt(sc);
                                        Jogador existente = novoCrud.read(id); //busca o jogador primeiro

                                        if (existente == null) {
                                            System.out.println("Jogador com ID " + id + " não foi encontrado.");
                                        } else {
                                            System.out.println("Dados atuais: " + existente);
                                            System.out.println("\nInforme os novos dados:");
                                            Jogador atualizado = lerDadosDoTeclado(sc); //chama funcao para preencher os dados manualmente
                                            atualizado.setAthleteId(id);//reatribui manualmente o id correto para sobrescrever o que veio da funcao
                                            boolean sucesso = novoCrud.update(atualizado);

                                            if (sucesso) {
                                                System.out.println("Registro atualizado com sucesso!");
                                            } else {
                                                System.out.println("Erro ao atualizar registro.");
                                            }
                                        }
                                        break;
                                    }
                                    case 4: { // Delete
                                        System.out.println("\nEXCLUIR REGISTRO");
                                        System.out.print("Digite o ID do jogador a ser deletado: ");
                                        int id = lerInt(sc);
                                        boolean sucesso = novoCrud.delete(id);
                                        if (sucesso) {
                                            System.out.println("Registro excluído com sucesso!");
                                        } else {
                                            System.out.println("Erro: ID não encontrado ou já excluído.");
                                        }
                                        break;
                                    }
                                    case 0: {
                                        rodandoCrudIndexado = false;
                                        break;
                                    }
                                    default:
                                        System.out.println("Opção inválida! Tente novamente.");
                                        break;
                                }
                            }
                            novoCrud.fechar();
                            System.out.println("Saindo do CRUD indexado...");
                            break;
                        }
                        case 9: { // Compressão com Huffman e LZW
                            Compressao.comprimir(ArquivoJogador.ARQUIVO_DADOS);
                            break;
                        }
                        case 10: { // Descompressão: substitui o arquivo de dados pela versão escolhida
                            System.out.print("Digite a versão (X) da compressão que deseja descompactar: ");
                            int versao = lerInt(sc);
                            arquivo.fechar(); // o arquivo de dados vai ser reescrito: fecha antes...
                            boolean substituiu;
                            try {
                                substituiu = Compressao.descomprimir(ArquivoJogador.ARQUIVO_DADOS, versao);
                            } finally {
                                arquivo = new ArquivoJogador(); // ...e reabre depois (mesmo se der erro)
                            }
                            if (substituiu) { // índices guardam posições do arquivo antigo: precisam ser refeitos
                                CrudIndexado.invalidarIndice();
                                ListaInvertida.invalidarLista();
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
                } catch (Exception e) { // erro numa opção não encerra o programa
                    System.out.println("Erro: " + e.getMessage());
                }
            }

            arquivo.fechar();
            sc.close();

        } catch (Exception e) {
            System.out.println("Erro de execução: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // le um inteiro do teclado; repete a pergunta enquanto a entrada nao for numerica
    private static int lerInt(Scanner sc) {
        while (true) {
            String linha = sc.nextLine().trim();
            try {
                return Integer.parseInt(linha);
            } catch (NumberFormatException e) {
                System.out.print("Entrada inválida. Digite um número inteiro: ");
            }
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
        String slug = sc.nextLine().trim();


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

        return new Jogador(0, nome, slug, posicao, data); //id = 0, muda dentro da funcao create ou no case do update
    }
}