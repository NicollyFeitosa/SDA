package fs.journal;

import fs.disk.BlockDevice;
import fs.disk.Layout;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class Transaction implements BlockDevice {
    private final long id;
    private final String descricao;
    private final BlockDevice base;
    private final Map<Integer, byte[]> alterados = new LinkedHashMap<>();

    Transaction(long id, String descricao, BlockDevice base) {
        this.id = id;
        this.descricao = descricao;
        this.base = base;
    }

    public long id() {
        return id;
    }

    public String descricao() {
        return descricao;
    }

    Map<Integer, byte[]> alterados() {
        return alterados;
    }

    @Override
    public byte[] readBlock(int n) throws IOException {
        byte[] dados = alterados.get(n);
        return dados != null ? dados.clone() : base.readBlock(n);
    }

    @Override
    public void writeBlock(int n, byte[] data) throws IOException {
        if (n < 0 || n >= Layout.TOTAL_BLOCKS || data.length != Layout.BLOCK_SIZE) {
            throw new IllegalArgumentException("escrita invalida no bloco " + n);
        }
        alterados.put(n, data.clone());
    }
}
