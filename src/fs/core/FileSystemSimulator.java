package fs.core;

import fs.disk.BlockDevice;
import fs.disk.Layout;
import fs.disk.VirtualDisk;
import fs.meta.Bitmap;
import fs.meta.Inode;
import fs.meta.InodeTable;
import fs.meta.Superblock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FileSystemSimulator implements AutoCloseable {
    private final VirtualDisk disco;

    private FileSystemSimulator(VirtualDisk disco) {
        this.disco = disco;
    }

    public static void formatar(String caminhoImg) throws IOException {
        VirtualDisk.create(caminhoImg);
        try (VirtualDisk disco = new VirtualDisk(caminhoImg)) {
            disco.writeBlock(Layout.SUPERBLOCK, Superblock.novo().toBytes());
            Bitmap blocos = new Bitmap(disco, Layout.BLOCK_BITMAP, Layout.TOTAL_BLOCKS);
            for (int i = 0; i < Layout.DATA_START; i++) {
                blocos.marcar(i);
            }
            Directory.criar(disco, new InodeTable(disco), blocos, Layout.ROOT_INODE);
            disco.sync();
        }
    }

    public static FileSystemSimulator montar(String caminhoImg) throws IOException {
        if (!VirtualDisk.exists(caminhoImg)) {
            throw new FsException("disco nao encontrado: " + caminhoImg);
        }
        VirtualDisk disco = new VirtualDisk(caminhoImg);
        if (disco.tamanho() != VirtualDisk.TAMANHO
                || !Superblock.fromBytes(disco.readBlock(Layout.SUPERBLOCK)).isValid()) {
            disco.close();
            throw new FsException("disco invalido");
        }
        return new FileSystemSimulator(disco);
    }

    public void criarDiretorio(String caminho) throws IOException {
        executar("mkdir " + caminho, fs -> {
            String nome = fs.nomeNovo(caminho);
            Directory pai = fs.pai(caminho);
            if (pai.buscar(nome) != null) {
                throw new FsException("ja existe");
            }
            int novo = Directory.criar(fs.dev(), fs.inodes(), fs.blocos(), pai.numero());
            pai.adicionar(nome, novo);
        });
    }

    public void apagarDiretorio(String caminho) throws IOException {
        executar("rmdir " + caminho, fs -> {
            int n = fs.caminhos().resolver(caminho);
            if (n == Layout.ROOT_INODE) {
                throw new FsException("nao e possivel remover a raiz");
            }
            String nome = fs.nomeNovo(caminho);
            Directory alvo = fs.diretorio(caminho);
            if (!alvo.vazio()) {
                throw new FsException("diretorio nao esta vazio");
            }
            fs.pai(caminho).remover(nome);
            for (int bloco : fs.inodes().ler(n).blocos) {
                if (bloco >= 0) {
                    fs.blocos().liberar(bloco);
                }
            }
            fs.inodes().liberar(n);
        });
    }

    public void renomear(String caminho, String novoNome) throws IOException {
        executar("rename " + caminho + " " + novoNome, fs -> {
            validarNome(novoNome);
            String nome = fs.nomeNovo(caminho);
            Directory pai = fs.pai(caminho);
            if (pai.buscar(nome) == null) {
                throw new FsException("caminho nao encontrado");
            }
            if (pai.buscar(novoNome) != null) {
                throw new FsException("ja existe");
            }
            pai.renomear(nome, novoNome);
        });
    }

    public List<String> listar(String caminho) throws IOException {
        Fs fs = Fs.sobre(disco);
        SimpleDateFormat data = new SimpleDateFormat("dd/MM HH:mm");
        List<String> linhas = new ArrayList<>();
        for (DirEntry entrada : fs.diretorio(caminho).listar()) {
            Inode inode = fs.inodes().ler(entrada.inode());
            linhas.add(String.format("%s %7d  %s  %s",
                    inode.isDiretorio() ? "d" : "-",
                    inode.tamanho,
                    data.format(new Date(inode.modificado)),
                    entrada.nome()));
        }
        return linhas;
    }

    public void criarArquivo(String caminho) throws IOException {
        executar("touch " + caminho, fs -> {
            String nome = fs.nomeNovo(caminho);
            Directory pai = fs.pai(caminho);
            if (pai.buscar(nome) != null) {
                throw new FsException("ja existe");
            }
            pai.adicionar(nome, File.criar(fs.inodes()));
        });
    }

    public void escreverArquivo(String caminho, String texto) throws IOException {
        byte[] conteudo = texto.getBytes(StandardCharsets.UTF_8);
        if (conteudo.length > File.TAMANHO_MAXIMO) {
            throw new FsException("arquivo excede 5 KB");
        }
        executar("write " + caminho, fs -> {
            String nome = fs.nomeNovo(caminho);
            Directory pai = fs.pai(caminho);
            DirEntry entrada = pai.buscar(nome);
            int n;
            if (entrada == null) {
                n = File.criar(fs.inodes());
                pai.adicionar(nome, n);
            } else if (!fs.inodes().ler(entrada.inode()).isArquivo()) {
                throw new FsException("nao e um arquivo");
            } else {
                n = entrada.inode();
            }
            fs.arquivo(n).escrever(conteudo);
        });
    }

    public String lerArquivo(String caminho) throws IOException {
        return new String(Fs.sobre(disco).arquivo(caminho).ler(), StandardCharsets.UTF_8);
    }

    public void copiarArquivo(String origem, String destino) throws IOException {
        executar("cp " + origem + " " + destino, fs -> {
            byte[] conteudo = fs.arquivo(origem).ler();
            String nome = fs.nomeNovo(destino);
            Directory pai = fs.pai(destino);
            if (pai.buscar(nome) != null) {
                throw new FsException("ja existe");
            }
            int novo = File.criar(fs.inodes());
            fs.arquivo(novo).escrever(conteudo);
            pai.adicionar(nome, novo);
        });
    }

    public void apagarArquivo(String caminho) throws IOException {
        executar("rm " + caminho, fs -> {
            File arquivo = fs.arquivo(caminho);
            fs.pai(caminho).remover(fs.nome(caminho));
            arquivo.liberarBlocos();
            fs.inodes().liberar(arquivo.numero());
        });
    }

    public int blocosLivres() throws IOException {
        return Fs.sobre(disco).blocos().livres();
    }

    public int inodesLivres() throws IOException {
        return Fs.sobre(disco).inodes().livres();
    }

    public void fechar() throws IOException {
        disco.close();
    }

    @Override
    public void close() throws IOException {
        fechar();
    }

    private void executar(String descricao, Operacao operacao) throws IOException {
        operacao.executar(Fs.sobre(disco));
    }

    @FunctionalInterface
    private interface Operacao {
        void executar(Fs fs) throws IOException;
    }

    private record Fs(BlockDevice dev, Bitmap blocos, InodeTable inodes, PathResolver caminhos) {
        static Fs sobre(BlockDevice dev) {
            Bitmap blocos = new Bitmap(dev, Layout.BLOCK_BITMAP, Layout.TOTAL_BLOCKS);
            InodeTable inodes = new InodeTable(dev);
            return new Fs(dev, blocos, inodes, new PathResolver(dev, inodes, blocos));
        }

        Directory diretorio(String caminho) throws IOException {
            int n = caminhos.resolver(caminho);
            if (!inodes.ler(n).isDiretorio()) {
                throw new FsException("nao e um diretorio");
            }
            return new Directory(dev, inodes, blocos, n);
        }

        File arquivo(String caminho) throws IOException {
            int n = caminhos.resolver(caminho);
            if (!inodes.ler(n).isArquivo()) {
                throw new FsException("nao e um arquivo");
            }
            return arquivo(n);
        }

        File arquivo(int n) {
            return new File(dev, inodes, blocos, n);
        }

        Directory pai(String caminho) throws IOException {
            return diretorio(caminhos.separarPaiENome(caminho)[0]);
        }

        String nome(String caminho) {
            return caminhos.separarPaiENome(caminho)[1];
        }

        String nomeNovo(String caminho) {
            String nome = nome(caminho);
            validarNome(nome);
            return nome;
        }
    }

    private static void validarNome(String nome) {
        if (nome.isEmpty() || nome.equals(".") || nome.equals("..") || nome.contains("/")) {
            throw new FsException("nome invalido");
        }
        if (nome.getBytes(StandardCharsets.UTF_8).length > Directory.NAME_SIZE) {
            throw new FsException("nome maior que 28 bytes");
        }
    }
}
