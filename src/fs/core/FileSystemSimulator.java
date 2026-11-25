package fs.core;

import fs.disk.Layout;
import fs.disk.VirtualDisk;
import fs.meta.Superblock;

import java.io.IOException;

public class FileSystemSimulator implements AutoCloseable {
    private final VirtualDisk disco;

    private FileSystemSimulator(VirtualDisk disco) {
        this.disco = disco;
    }

    public static void formatar(String caminhoImg) throws IOException {
        VirtualDisk.create(caminhoImg);
        try (VirtualDisk disco = new VirtualDisk(caminhoImg)) {
            disco.writeBlock(Layout.SUPERBLOCK, Superblock.novo().toBytes());
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

    public void fechar() throws IOException {
        disco.close();
    }

    @Override
    public void close() throws IOException {
        fechar();
    }
}
