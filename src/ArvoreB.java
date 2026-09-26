import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class ArvoreB {

    private final RandomAccessFile raf;
    private final int ordem;
    private long offsetRaiz;

    private static final long OFFSET_CABECALHO = 0;
    private static final int TAMANHO_CABECALHO = 8; // um long guardando o offset da raiz

    public ArvoreB(String arquivoIndice, int ordem) throws IOException {
        if (ordem < 3) {
            throw new IllegalArgumentException("A ordem da arvore B deve ser >= 3.");
        }
        this.ordem = ordem;
        this.raf = new RandomAccessFile(arquivoIndice, "rw");

        if (raf.length() == 0) {
            // arquivo novo: grava cabecalho indicando arvore vazia
            offsetRaiz = -1;
            raf.seek(OFFSET_CABECALHO);
            raf.writeLong(offsetRaiz);
        } else {
            raf.seek(OFFSET_CABECALHO);
            offsetRaiz = raf.readLong();
        }
    }

    // Representacao de um no EM MEMORIA (usada durante as operacoes);
    // e serializada/deserializada do disco pelos metodos lerNo/escreverNo)
    private class No {
        boolean folha;
        int numChaves;
        int[] chaves;
        long[] posicoes; // posicao no arquivo de DADOS (jogadores.db) para cada chave
        long[] filhos;   // offset, no arquivo de INDICE, de cada filho
        long offsetNoArquivo = -1; // onde este no esta gravado;    -1 == ainda nao gravado

        No(boolean folha) {
            this.folha = folha;
            this.numChaves = 0;
            this.chaves = new int[ordem - 1];
            this.posicoes = new long[ordem - 1];
            this.filhos = new long[ordem];
            Arrays.fill(filhos, -1);
        }
    }

    // tamanho fixo, em bytes, de qualquer no desta arvore
    private int tamanhoNoEmBytes() {
        return 1 + 4 + (ordem - 1) * (4 + 8) + ordem * 8;
    }



    //  Leitura / escrita de nós no arquivo de indice

    //Carrega um nó da árvore que está salvo no arquivo para a memória RAM.
    private No lerNo(long offset) throws IOException {
        raf.seek(offset);   //move o ponteiro do arquivo para a posição do nó
        No no = new No(raf.readByte() == 1);    //lê o tipo do nó (1 para folha ou 0 para interno)
        no.offsetNoArquivo = offset;    //armazena o offset do próprio nó na memória
        no.numChaves = raf.readInt();   //lê quantas chaves estão ativas no nó
        //lê os pares (chave, posicao no arquivo de dados)
        for (int i = 0; i < ordem - 1; i++) {
            no.chaves[i] = raf.readInt();
            no.posicoes[i] = raf.readLong();
        }
        //lê os ponteiros (offsets) para os nós filhos
        for (int i = 0; i < ordem; i++) {
            no.filhos[i] = raf.readLong();
        }
        return no;
    }

    private void escreverNo(No no) throws IOException {
        if (no.offsetNoArquivo == -1) {
            // no novo: aloca espaco sempre no final do arquivo
            no.offsetNoArquivo = raf.length();
        }
        raf.seek(no.offsetNoArquivo);   //Move para a posicao onde o nó deve ser escrito
        raf.writeByte(no.folha ? 1 : 0);    //1 se for folha, 0 se nao
        raf.writeInt(no.numChaves);     //salva a quantidade atual de chaves

        //grava todas as chaves e suas posições associadas
        for (int i = 0; i < ordem - 1; i++) {
            raf.writeInt(no.chaves[i]);
            raf.writeLong(no.posicoes[i]);
        }
        //grava os offsets para os nós filhos
        for (int i = 0; i < ordem; i++) {
            raf.writeLong(no.filhos[i]);
        }
    }

    private void atualizarRaiz(long novoOffset) throws IOException {
        offsetRaiz = novoOffset;    // atualiza a variável na memória
        raf.seek(OFFSET_CABECALHO); //Vai para o iníciodo arquivo
        raf.writeLong(offsetRaiz); // escreve o offset da nova raiz
    }


    //  BUSCA

    // Retorna a posicao no arquivo de dados para o id informado, ou -1 se nao existir.
    //metodo public
    public long buscar(int chave) throws IOException {
        if (offsetRaiz == -1) return -1;
        return buscarNo(lerNo(offsetRaiz), chave);
    }

    //metodo private
    private long buscarNo(No no, int chave) throws IOException {
        int i = 0;
        //procura a posição adequada da chave dentro do nó atual
        while (i < no.numChaves && chave > no.chaves[i]) i++;

        //se encontrou a chave no nó atual, retorna a posição do registro no arquivo de dados
        if (i < no.numChaves && chave == no.chaves[i]) {
            return no.posicoes[i];
        }
        //chegou no limite da árvore e não achou: chave nao existe
        if (no.folha) {
            return -1;
        }
        //não é folha, desce para o filho correspondente
        return buscarNo(lerNo(no.filhos[i]), chave);
    }


    // INSERCAO

    /*
     * Insere (chave, posicao). Se a chave ja existir, apenas ATUALIZA a posicao
     * associada (upsert) - util para quando um UPDATE realoca um registro para
     * o fim do arquivo de dados, mudando sua posicao mas nao seu id.
     */

    public void inserir(int chave, long posicao) throws IOException {
        //Tenta atualizar se a chave já existir no arquivo (upsert)
        if (atualizarSeExistir(chave, posicao)) {
            return;
        }

        //arvore vazia
        if (offsetRaiz == -1) {
            No raiz = new No(true);
            raiz.chaves[0] = chave;
            raiz.posicoes[0] = posicao;
            raiz.numChaves = 1;
            escreverNo(raiz);   //grava a nova raiz no final do arquivo em disco
            atualizarRaiz(raiz.offsetNoArquivo);    //grava o offset no cabeçalho
            return;
        }

        //carrega a raiz atual do disco para a memória RAM
        No raiz = lerNo(offsetRaiz);

        //caso para a raiz cheia
        if (raiz.numChaves == ordem - 1) {
            /* raiz cheia: a arvore cresce em altura.
             * cria uma nova raiz (interna), com a raiz antiga como unico filho,
             * divide a raiz antiga imediatamente.*/
            No novaRaiz = new No(false);
            novaRaiz.filhos[0] = raiz.offsetNoArquivo;
            dividirFilho(novaRaiz, 0, raiz); //split da raiz antiga em dois nós
            inserirNaoCheio(novaRaiz, chave, posicao); //insere o novo elemento a partir da nova raiz
            atualizarRaiz(novaRaiz.offsetNoArquivo); //atualiza o cabeçalho
        } else {
            //A Raiz não está cheia
            inserirNaoCheio(raiz, chave, posicao);
        }
    }


    // metodo buscarNo com uma diferença: quando encontra a chave, ele a substitui e salva no disco.
    private boolean atualizarSeExistir(int chave, long novaPosicao) throws IOException {
        if (offsetRaiz == -1) return false;
        return atualizarNo(lerNo(offsetRaiz), chave, novaPosicao);
    }

    private boolean atualizarNo(No no, int chave, long novaPosicao) throws IOException {
        int i = 0;
        while (i < no.numChaves && chave > no.chaves[i]) i++;

        if (i < no.numChaves && chave == no.chaves[i]) {
            no.posicoes[i] = novaPosicao;   //Atualiza o offset em memória
            escreverNo(no); //salva a alteração no arquivo de disco
            return true;
        }
        if (no.folha) return false; //se não encontrou e chegou ao fim
        return atualizarNo(lerNo(no.filhos[i]), chave, novaPosicao); //continua buscando no filho correto
    }


    // metodo para fazer split, subindo o filho do meio
    private void dividirFilho(No pai, int indiceFilho, No filhoCheio) throws IOException {
        int totalChaves = filhoCheio.numChaves;   // = ordem - 1 (esta cheio)
        int meio = (ordem - 1) / 2;               // indice da chave que sobe

        No novoIrmao = new No(filhoCheio.folha);
        int qtdDireita = totalChaves - meio - 1;

        // copia a metade direita das chaves para o novo irmao
        for (int j = 0; j < qtdDireita; j++) {
            novoIrmao.chaves[j] = filhoCheio.chaves[meio + 1 + j];
            novoIrmao.posicoes[j] = filhoCheio.posicoes[meio + 1 + j];
        }
        novoIrmao.numChaves = qtdDireita;

        // se nao for folha, copia tambem os ponteiros de filhos correspondentes
        if (!filhoCheio.folha) {
            for (int j = 0; j <= qtdDireita; j++) {
                novoIrmao.filhos[j] = filhoCheio.filhos[meio + 1 + j];
            }
        }

        int chaveMediana = filhoCheio.chaves[meio];
        long posicaoMediana = filhoCheio.posicoes[meio];

        filhoCheio.numChaves = meio; // o filho original fica so com a metade esquerda

        // abre espaco no PAI para a chave mediana e o novo ponteiro de filho
        for (int j = pai.numChaves; j > indiceFilho; j--) {
            pai.chaves[j] = pai.chaves[j - 1];
            pai.posicoes[j] = pai.posicoes[j - 1];
        }
        for (int j = pai.numChaves + 1; j > indiceFilho + 1; j--) {
            pai.filhos[j] = pai.filhos[j - 1];
        }

        pai.chaves[indiceFilho] = chaveMediana;
        pai.posicoes[indiceFilho] = posicaoMediana;
        pai.numChaves++;

        // grava o novo irmao primeiro para obter seu offset definitivo em disco
        escreverNo(novoIrmao);
        pai.filhos[indiceFilho + 1] = novoIrmao.offsetNoArquivo;

        escreverNo(filhoCheio);
        escreverNo(pai);
    }


    // insere (chave, posicao) em um no que se sabe NAO estar cheio.
    private void inserirNaoCheio(No no, int chave, long posicao) throws IOException {
        int i = no.numChaves - 1;

        if (no.folha) {
            // desloca chaves maiores para a direita, abrindo espaco
            while (i >= 0 && chave < no.chaves[i]) {
                no.chaves[i + 1] = no.chaves[i];
                no.posicoes[i + 1] = no.posicoes[i];
                i--;
            }
            no.chaves[i + 1] = chave;
            no.posicoes[i + 1] = posicao;
            no.numChaves++;
            escreverNo(no);
        } else {
            // decide em qual filho descer
            while (i >= 0 && chave < no.chaves[i]) i--;
            i++;

            No filho = lerNo(no.filhos[i]);
            if (filho.numChaves == ordem - 1) {
                dividirFilho(no, i, filho);
                // apos a divisao, uma chave subiu para no; decide de novo o lado
                if (chave > no.chaves[i]) {
                    i++;
                }
                filho = lerNo(no.filhos[i]);
            }
            inserirNaoCheio(filho, chave, posicao);
        }
    }


    //  REMOÇÃO

    /* grau minimo (t): cada no (exceto a raiz) deve ter pelo menos t-1 chaves.
        ordem = 2t (convencao adotada), entao t = ceil(ordem/2).*/
    private int grauMinimo() {
        return ordem / 2;
    }

    // Remove a chave do indice, se ela existir. Se nao existir, nao faz nada.
    public void remover(int chave) throws IOException {
        if (offsetRaiz == -1) return; // arvore vazia

        removerDoNo(lerNo(offsetRaiz), chave);

        // a raiz pode ter ficado sem chaves depois da remocao (arvore diminui de altura)
        No raizAtual = lerNo(offsetRaiz);
        if (raizAtual.numChaves == 0) {
            if (raizAtual.folha) {
                atualizarRaiz(-1); // arvore ficou completamente vazia
            } else {
                atualizarRaiz(raizAtual.filhos[0]); // o unico filho vira a nova raiz
            }
        }
    }

    private void removerDoNo(No no, int chave) throws IOException {
        int i = 0;
        while (i < no.numChaves && chave > no.chaves[i]) i++;

        if (i < no.numChaves && no.chaves[i] == chave) {
            if (no.folha) {
                removerDeFolha(no, i); //Remove direto (caso simples)
            } else {
                removerDeNoInterno(no, i);
            }
            return;
        }
        //chegou numa folha e NÃO encontrou a chave
        if (no.folha) {
            return;
        }

        /* garante que o filho pelo qual vamos descer tenha pelo menos "grauMinimo" chaves
        para que remover uma chave dele (no pior caso) nao viole a ocupacao minima
         */
        No filho = lerNo(no.filhos[i]);
        // Se o filho tiver MENOS que o grau mínimo de chaves, ele precisa ser "preenchido"
        if (filho.numChaves < grauMinimo()) {
            preencher(no, i);
            // "no" pode ter mudado (chave/filho deslocados por emprestimo ou fusao);
            // relemos do disco para recalcular corretamente por onde descer
            no = lerNo(no.offsetNoArquivo);
            i = 0;
            while (i < no.numChaves && chave > no.chaves[i]) i++;
        }
        filho = lerNo(no.filhos[i]);
        removerDoNo(filho, chave); // Continua descendo na árvore
    }

    // Remove a chave de indice "i" de um no folha (caso simples: so desloca e grava).

    //Desloca todas as chaves e posições à direita para a esquerda
    private void removerDeFolha(No no, int i) throws IOException {
        for (int j = i; j < no.numChaves - 1; j++) {
            no.chaves[j] = no.chaves[j + 1];
            no.posicoes[j] = no.posicoes[j + 1];
        }
        no.numChaves--;
        escreverNo(no);
    }

    // Remove a chave de indice "i" de um no INTERNO (precisa de predecessor/sucessor ou fusao).


    private void removerDeNoInterno(No no, int i) throws IOException {
        int chave = no.chaves[i];
        No filhoEsquerdo = lerNo(no.filhos[i]); //sub arvore de valores menores
        No filhoDireito = lerNo(no.filhos[i + 1]); //sub arvore de valores maiores

        if (filhoEsquerdo.numChaves >= grauMinimo()) {
            // caso 2a: substitui pela chave predecessora (maior chave da subarvore da esquerda)

            //encontra a maior chave da subárvore esquerda
            long[] predecessor = obterExtremo(filhoEsquerdo, true);
            //substitui a chave do nó interno pelo predecessor
            no.chaves[i] = (int) predecessor[0];
            no.posicoes[i] = predecessor[1];
            escreverNo(no);
            //remove a chave predecessora da subárvore de onde ela foi retirada
            removerDoNo(lerNo(no.filhos[i]), (int) predecessor[0]);

        } else if (filhoDireito.numChaves >= grauMinimo()) {
            // caso 2b: substitui pela chave sucessora (menor chave da subarvore direita)

            //encontra a menor chave da subárvore direita
            long[] sucessor = obterExtremo(filhoDireito, false);
            //substitui a chave do nó interno pelo sucessor
            no.chaves[i] = (int) sucessor[0];
            no.posicoes[i] = sucessor[1];
            escreverNo(no);
            //remove a chave sucessora da subárvore de onde ela foi retirada
            removerDoNo(lerNo(no.filhos[i + 1]), (int) sucessor[0]);

        } else {
            // junta filhoEsquerdo + chave "i" do pai + filhoDireito em um único nó
            mesclarFilhos(no, i);
            //recarrega o pai atualizado do disco
            no = lerNo(no.offsetNoArquivo);
            int indiceFilhoMesclado = i; // apos a fusao, o no resultante ocupa a posicao "i"
            //remove a chave de dentro do novo nó mesclado resultante
            removerDoNo(lerNo(no.filhos[indiceFilhoMesclado]), chave);
        }
    }

    // Desce ate a folha mais a direita (predecessor) ou mais a esquerda (sucessor).
    private long[] obterExtremo(No no, boolean maiorChave) throws IOException {
        while (!no.folha) {
            no = maiorChave ? lerNo(no.filhos[no.numChaves]) : lerNo(no.filhos[0]);
        }
        int indice = maiorChave ? no.numChaves - 1 : 0;
        return new long[]{no.chaves[indice], no.posicoes[indice]};
    }

    /*
     * Garante que "pai.filhos[i]" fique com pelo menos "grauMinimo" chaves antes de
     * descermos nele: pega emprestado de um irmao com excedente, ou funde com um deles.
     */
    private void preencher(No pai, int i) throws IOException {
        //TENTATIVA 1: Pegar emprestado do irmão da ESQUERDA
        if (i > 0) {
            No irmaoEsquerdo = lerNo(pai.filhos[i - 1]);
            if (irmaoEsquerdo.numChaves >= grauMinimo()) {
                pegarEmprestadoDaEsquerda(pai, i);
                return;
            }
        }
        // TENTATIVA 2: Pegar emprestado do irmão da DIREITA
        if (i < pai.numChaves) {
            No irmaoDireito = lerNo(pai.filhos[i + 1]);
            if (irmaoDireito.numChaves >= grauMinimo()) {
                pegarEmprestadoDaDireita(pai, i);
                return;
            }
        }
        // TENTATIVA 3: Se nenhum irmão puder ceder, faz a FUSÃO (Merge)
        if (i < pai.numChaves) {
            mesclarFilhos(pai, i); // funde filhos[i] com filhos[i+1]
        } else {
            mesclarFilhos(pai, i - 1); // filhos[i] era o ultimo; funde com o irmao a esquerda
        }
    }

    //"pai.filhos[i]" recebe uma chave do pai, e o irmao esquerdo cede sua maior chave ao pai.
    private void pegarEmprestadoDaEsquerda(No pai, int i) throws IOException {
        No filho = lerNo(pai.filhos[i]);
        No irmaoEsquerdo = lerNo(pai.filhos[i - 1]);

        // abre espaco na primeira posicao do filho (desloca tudo uma casa para a direita)
        for (int j = filho.numChaves - 1; j >= 0; j--) {
            filho.chaves[j + 1] = filho.chaves[j];
            filho.posicoes[j + 1] = filho.posicoes[j];
        }
        if (!filho.folha) {
            for (int j = filho.numChaves; j >= 0; j--) {
                filho.filhos[j + 1] = filho.filhos[j];
            }
        }

        // a chave do pai "desce" para o inicio do filho
        filho.chaves[0] = pai.chaves[i - 1];
        filho.posicoes[0] = pai.posicoes[i - 1];
        if (!filho.folha) {
            filho.filhos[0] = irmaoEsquerdo.filhos[irmaoEsquerdo.numChaves];
        }

        // a maior chave do irmao esquerdo "sobe" para o pai
        pai.chaves[i - 1] = irmaoEsquerdo.chaves[irmaoEsquerdo.numChaves - 1];
        pai.posicoes[i - 1] = irmaoEsquerdo.posicoes[irmaoEsquerdo.numChaves - 1];

        filho.numChaves++;
        irmaoEsquerdo.numChaves--;

        escreverNo(filho);
        escreverNo(irmaoEsquerdo);
        escreverNo(pai);
    }

    /** Espelho do metodo anterior: empresta do irmao a DIREITA. */
    private void pegarEmprestadoDaDireita(No pai, int i) throws IOException {
        No filho = lerNo(pai.filhos[i]);
        No irmaoDireito = lerNo(pai.filhos[i + 1]);

        // a chave do pai "desce" para o final do filho
        filho.chaves[filho.numChaves] = pai.chaves[i];
        filho.posicoes[filho.numChaves] = pai.posicoes[i];
        if (!filho.folha) {
            filho.filhos[filho.numChaves + 1] = irmaoDireito.filhos[0];
        }

        // a menor chave do irmao direito "sobe" para o pai
        pai.chaves[i] = irmaoDireito.chaves[0];
        pai.posicoes[i] = irmaoDireito.posicoes[0];

        // desloca o irmao direito uma casa para a esquerda (removeu o primeiro elemento dele)
        for (int j = 1; j < irmaoDireito.numChaves; j++) {
            irmaoDireito.chaves[j - 1] = irmaoDireito.chaves[j];
            irmaoDireito.posicoes[j - 1] = irmaoDireito.posicoes[j];
        }
        if (!irmaoDireito.folha) {
            for (int j = 1; j <= irmaoDireito.numChaves; j++) {
                irmaoDireito.filhos[j - 1] = irmaoDireito.filhos[j];
            }
        }

        filho.numChaves++;
        irmaoDireito.numChaves--;

        escreverNo(filho);
        escreverNo(irmaoDireito);
        escreverNo(pai);
    }

    /*
     * Funde "pai.filhos[i]" e "pai.filhos[i+1]" (ambos com o minimo de chaves) num so no,
     * descendo a chave separadora "pai.chaves[i]" para dentro dele.
     * O no da direita fica orfao no arquivo (espaco desperdicado, mesma logica das
     * lapides do TP1: nao reaproveitamos o espaco, so paramos de referencia-lo).
     */

    private void mesclarFilhos(No pai, int i) throws IOException {
        No filhoEsquerdo = lerNo(pai.filhos[i]);
        No filhoDireito = lerNo(pai.filhos[i + 1]);

        // a chave separadora do pai desce para o final do filho esquerdo
        filhoEsquerdo.chaves[filhoEsquerdo.numChaves] = pai.chaves[i];
        filhoEsquerdo.posicoes[filhoEsquerdo.numChaves] = pai.posicoes[i];

        // copia todas as chaves (e filhos, se houver) do filho direito para o esquerdo
        for (int j = 0; j < filhoDireito.numChaves; j++) {
            filhoEsquerdo.chaves[filhoEsquerdo.numChaves + 1 + j] = filhoDireito.chaves[j];
            filhoEsquerdo.posicoes[filhoEsquerdo.numChaves + 1 + j] = filhoDireito.posicoes[j];
        }
        if (!filhoEsquerdo.folha) {
            for (int j = 0; j <= filhoDireito.numChaves; j++) {
                filhoEsquerdo.filhos[filhoEsquerdo.numChaves + 1 + j] = filhoDireito.filhos[j];
            }
        }
        filhoEsquerdo.numChaves = filhoEsquerdo.numChaves + 1 + filhoDireito.numChaves;

        // remove, do pai, a chave separadora e o ponteiro para o filho direito (agora orfao)
        for (int j = i; j < pai.numChaves - 1; j++) {
            pai.chaves[j] = pai.chaves[j + 1];
            pai.posicoes[j] = pai.posicoes[j + 1];
        }
        for (int j = i + 1; j < pai.numChaves; j++) {
            pai.filhos[j] = pai.filhos[j + 1];
        }
        pai.numChaves--;

        escreverNo(filhoEsquerdo);
        escreverNo(pai);
    }

    public void fechar() throws IOException {
        raf.close(); // Fecha o arquivo RAF
    }

    public void limpar() throws IOException {
        raf.setLength(0); // descarta todos os nos gravados
        offsetRaiz = -1;
        raf.seek(OFFSET_CABECALHO);
        raf.writeLong(offsetRaiz); // grava cabecalho de arvore vazia
    }

}



