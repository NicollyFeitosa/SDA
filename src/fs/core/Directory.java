package fs.core;

import fs.disk.BlockDevice;
import fs.disk.Layout;
import fs.meta.Bitmap;
import fs.meta.Inode;
import fs.meta.InodeTable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Directory {
    public static final int ENTRY_SIZE = 32;
    public static final int NAME_SIZE = 28;
    public static final int ENTRIES_PER_BLOCK = Layout.BLOCK_SIZE / ENTRY_SIZE;

    private final BlockDevice dev;
    private final InodeTable inodes;
    private final Bitmap blocos;
    private final int numero;

    public Directory(BlockDevice dev, InodeTable inodes, Bitmap blocos, int inodeNumero) {
        this.dev = dev;
        this.inodes = inodes;
        this.blocos = blocos;
        this.numero = inodeNumero;
    }

    public static int criar(BlockDevice dev, InodeTable inodes, Bitmap blocos, int inodePai) throws IOException {
        int novo = inodes.alocar(Inode.DIRETORIO);
        int bloco = blocos.alocar();
        byte[] dados = blocoVazio();
        escreverEntrada(dados, 0, ".", novo);
        escreverEntrada(dados, 1, "..", inodePai);
        dev.writeBlock(bloco, dados);

        Inode inode = inodes.ler(novo);
        inode.blocos[0] = bloco;
        inode.tamanho = Layout.BLOCK_SIZE;
        inodes.gravar(novo, inode);
        return novo;
    }

    public int numero() {
        return numero;
    }

    public List<DirEntry> listar() throws IOException {
        List<DirEntry> entradas = new ArrayList<>();
        for (int bloco : inodes.ler(numero).blocos) {
            if (bloco < 0) {
                continue;
            }
            byte[] dados = dev.readBlock(bloco);
            for (int i = 0; i < ENTRIES_PER_BLOCK; i++) {
                DirEntry entrada = lerEntrada(dados, i);
                if (entrada != null) {
                    entradas.add(entrada);
                }
            }
        }
        return entradas;
    }

    public DirEntry buscar(String nome) throws IOException {
        for (DirEntry entrada : listar()) {
            if (entrada.nome().equals(nome)) {
                return entrada;
            }
        }
        return null;
    }

    public void adicionar(String nome, int inodeNovo) throws IOException {
        Inode inode = inodes.ler(numero);
        for (int bloco : inode.blocos) {
            if (bloco < 0) {
                continue;
            }
            byte[] dados = dev.readBlock(bloco);
            for (int i = 0; i < ENTRIES_PER_BLOCK; i++) {
                if (lerEntrada(dados, i) == null) {
                    escreverEntrada(dados, i, nome, inodeNovo);
                    dev.writeBlock(bloco, dados);
                    tocar();
                    return;
                }
            }
        }

        int livre = 0;
        while (livre < Inode.MAX_BLOCOS && inode.blocos[livre] >= 0) {
            livre++;
        }
        if (livre == Inode.MAX_BLOCOS) {
            throw new FsException("diretorio cheio");
        }
        int bloco = blocos.alocar();
        byte[] dados = blocoVazio();
        escreverEntrada(dados, 0, nome, inodeNovo);
        dev.writeBlock(bloco, dados);

        inode.blocos[livre] = bloco;
        inode.tamanho += Layout.BLOCK_SIZE;
        inode.tocar();
        inodes.gravar(numero, inode);
    }

    public void remover(String nome) throws IOException {
        alterarEntrada(nome, null);
    }

    public void renomear(String antigo, String novo) throws IOException {
        alterarEntrada(antigo, novo);
    }

    public boolean vazio() throws IOException {
        for (DirEntry entrada : listar()) {
            if (!entrada.nome().equals(".") && !entrada.nome().equals("..")) {
                return false;
            }
        }
        return true;
    }

    private void alterarEntrada(String nome, String novoNome) throws IOException {
        for (int bloco : inodes.ler(numero).blocos) {
            if (bloco < 0) {
                continue;
            }
            byte[] dados = dev.readBlock(bloco);
            for (int i = 0; i < ENTRIES_PER_BLOCK; i++) {
                DirEntry entrada = lerEntrada(dados, i);
                if (entrada != null && entrada.nome().equals(nome)) {
                    if (novoNome == null) {
                        escreverEntrada(dados, i, "", -1);
                    } else {
                        escreverEntrada(dados, i, novoNome, entrada.inode());
                    }
                    dev.writeBlock(bloco, dados);
                    tocar();
                    return;
                }
            }
        }
        throw new FsException("caminho nao encontrado");
    }

    private void tocar() throws IOException {
        Inode inode = inodes.ler(numero);
        inode.tocar();
        inodes.gravar(numero, inode);
    }

    private static byte[] blocoVazio() {
        byte[] dados = new byte[Layout.BLOCK_SIZE];
        for (int i = 0; i < ENTRIES_PER_BLOCK; i++) {
            escreverEntrada(dados, i, "", -1);
        }
        return dados;
    }

    private static DirEntry lerEntrada(byte[] dados, int indice) {
        ByteBuffer buf = ByteBuffer.wrap(dados, indice * ENTRY_SIZE, ENTRY_SIZE);
        int inode = buf.getInt();
        if (inode < 0) {
            return null;
        }
        byte[] nome = new byte[NAME_SIZE];
        buf.get(nome);
        int fim = 0;
        while (fim < NAME_SIZE && nome[fim] != 0) {
            fim++;
        }
        return new DirEntry(new String(nome, 0, fim, StandardCharsets.UTF_8), inode);
    }

    private static void escreverEntrada(byte[] dados, int indice, String nome, int inode) {
        byte[] bytesNome = nome.getBytes(StandardCharsets.UTF_8);
        if (bytesNome.length > NAME_SIZE) {
            throw new FsException("nome maior que 28 bytes");
        }
        ByteBuffer buf = ByteBuffer.wrap(dados, indice * ENTRY_SIZE, ENTRY_SIZE);
        buf.putInt(inode);
        buf.put(Arrays.copyOf(bytesNome, NAME_SIZE));
    }
}
