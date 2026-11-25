package fs.shell;

import fs.core.FileSystemSimulator;
import fs.core.FsException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Shell {
    private static final String AJUDA = """
            comandos:
              mkdir <caminho>              cria um diretorio
              rmdir <caminho>              apaga um diretorio vazio
              rename <caminho> <novoNome>  renomeia arquivo ou diretorio
              ls [caminho]                 lista o conteudo de um diretorio
              touch <caminho>              cria um arquivo vazio
              write <caminho> <texto...>   grava texto em um arquivo
              cat <caminho>                mostra o conteudo de um arquivo
              cp <origem> <destino>        copia um arquivo
              rm <caminho>                 apaga um arquivo
              journal                      mostra as transacoes da sessao
              df                           mostra blocos e inodes livres
              crash                        interrompe a proxima operacao apos o commit
              format                       apaga tudo e recria o disco
              exit                         fecha o disco e sai""";

    private final String caminhoDisco;
    private FileSystemSimulator fs;

    public Shell(String caminhoDisco, FileSystemSimulator fs) {
        this.caminhoDisco = caminhoDisco;
        this.fs = fs;
    }

    public void executar() throws IOException {
        System.out.println("simulador de sistema de arquivos (digite help para ajuda)");
        Scanner entrada = new Scanner(System.in, StandardCharsets.UTF_8);
        try {
            while (true) {
                System.out.print("> ");
                System.out.flush();
                if (!entrada.hasNextLine()) {
                    System.out.println();
                    break;
                }
                String linha = entrada.nextLine().trim();
                if (linha.isEmpty()) {
                    continue;
                }
                try {
                    if (!processar(linha)) {
                        break;
                    }
                } catch (FsException e) {
                    System.out.println("erro: " + e.getMessage());
                } catch (IOException e) {
                    System.out.println("erro de E/S: " + e.getMessage());
                }
            }
        } finally {
            fs.fechar();
        }
    }

    private boolean processar(String linha) throws IOException {
        String[] args = linha.split("\\s+");
        String comando = args[0];
        switch (comando) {
            case "help" -> System.out.println(AJUDA);
            case "mkdir" -> {
                exigir(args, 2, "mkdir <caminho>");
                fs.criarDiretorio(args[1]);
                System.out.println("diretorio criado: " + args[1]);
            }
            case "rmdir" -> {
                exigir(args, 2, "rmdir <caminho>");
                fs.apagarDiretorio(args[1]);
                System.out.println("diretorio removido: " + args[1]);
            }
            case "rename" -> {
                exigir(args, 3, "rename <caminho> <novoNome>");
                fs.renomear(args[1], args[2]);
                System.out.println("renomeado: " + args[1] + " -> " + args[2]);
            }
            case "ls" -> {
                if (args.length > 2) {
                    throw new FsException("uso: ls [caminho]");
                }
                fs.listar(args.length == 2 ? args[1] : "/").forEach(System.out::println);
            }
            case "touch" -> {
                exigir(args, 2, "touch <caminho>");
                fs.criarArquivo(args[1]);
                System.out.println("arquivo criado: " + args[1]);
            }
            case "write" -> {
                String[] partes = linha.split("\\s+", 3);
                if (partes.length < 3) {
                    throw new FsException("uso: write <caminho> <texto...>");
                }
                fs.escreverArquivo(partes[1], partes[2]);
                int bytes = partes[2].getBytes(StandardCharsets.UTF_8).length;
                System.out.println(bytes + " bytes gravados em " + partes[1]);
            }
            case "cat" -> {
                exigir(args, 2, "cat <caminho>");
                System.out.println(fs.lerArquivo(args[1]));
            }
            case "cp" -> {
                exigir(args, 3, "cp <origem> <destino>");
                fs.copiarArquivo(args[1], args[2]);
                System.out.println("copiado: " + args[1] + " -> " + args[2]);
            }
            case "rm" -> {
                exigir(args, 2, "rm <caminho>");
                fs.apagarArquivo(args[1]);
                System.out.println("arquivo removido: " + args[1]);
            }
            case "journal" -> {
                if (fs.historicoJournal().isEmpty()) {
                    System.out.println("nenhuma transacao nesta sessao");
                }
                fs.historicoJournal().forEach(System.out::println);
            }
            case "df" -> {
                System.out.println("blocos livres: " + fs.blocosLivres());
                System.out.println("inodes livres: " + fs.inodesLivres());
            }
            case "crash" -> {
                fs.armarCrash();
                System.out.println("crash armado: a proxima operacao sera interrompida");
            }
            case "format" -> {
                fs.fechar();
                FileSystemSimulator.formatar(caminhoDisco);
                fs = FileSystemSimulator.montar(caminhoDisco);
                System.out.println("disco formatado: " + caminhoDisco);
            }
            case "exit" -> {
                return false;
            }
            default -> throw new FsException("comando desconhecido: " + comando + " (digite help)");
        }
        return true;
    }

    private static void exigir(String[] args, int quantidade, String uso) {
        if (args.length != quantidade) {
            throw new FsException("uso: " + uso);
        }
    }
}
