package fs.disk;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

public class VirtualDisk implements BlockDevice, AutoCloseable {
    public static final long TAMANHO = (long) Layout.TOTAL_BLOCKS * Layout.BLOCK_SIZE;

    private final RandomAccessFile raf;

    public VirtualDisk(String caminho) throws IOException {
        this.raf = new RandomAccessFile(caminho, "rw");
    }

    public static boolean exists(String caminho) {
        return Files.isRegularFile(Path.of(caminho));
    }

    public static void create(String caminho) throws IOException {
        try (RandomAccessFile novo = new RandomAccessFile(caminho, "rw")) {
            novo.setLength(0);
            novo.setLength(TAMANHO);
        }
    }

    public long tamanho() throws IOException {
        return raf.length();
    }

    @Override
    public byte[] readBlock(int n) throws IOException {
        verificar(n);
        byte[] dados = new byte[Layout.BLOCK_SIZE];
        raf.seek((long) n * Layout.BLOCK_SIZE);
        raf.readFully(dados);
        return dados;
    }

    @Override
    public void writeBlock(int n, byte[] data) throws IOException {
        verificar(n);
        if (data.length != Layout.BLOCK_SIZE) {
            throw new IllegalArgumentException("bloco deve ter " + Layout.BLOCK_SIZE + " bytes");
        }
        raf.seek((long) n * Layout.BLOCK_SIZE);
        raf.write(data);
    }

    public void sync() throws IOException {
        raf.getFD().sync();
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }

    private void verificar(int n) {
        if (n < 0 || n >= Layout.TOTAL_BLOCKS) {
            throw new IllegalArgumentException("bloco fora do disco: " + n);
        }
    }
}
