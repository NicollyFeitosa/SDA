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
