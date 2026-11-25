package fs.meta;

import fs.disk.Layout;

import java.nio.ByteBuffer;

public class Superblock {
    public int magic;
    public int blockSize;
    public int totalBlocks;
    public int totalInodes;
    public int dataStart;
    public int rootInode;
    public long lastTxId;

    public static Superblock novo() {
        Superblock sb = new Superblock();
        sb.magic = Layout.MAGIC;
        sb.blockSize = Layout.BLOCK_SIZE;
        sb.totalBlocks = Layout.TOTAL_BLOCKS;
        sb.totalInodes = Layout.TOTAL_INODES;
        sb.dataStart = Layout.DATA_START;
        sb.rootInode = Layout.ROOT_INODE;
        sb.lastTxId = 0;
        return sb;
    }

    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(Layout.BLOCK_SIZE);
        buf.putInt(magic)
                .putInt(blockSize)
                .putInt(totalBlocks)
                .putInt(totalInodes)
                .putInt(dataStart)
                .putInt(rootInode)
                .putLong(lastTxId);
        return buf.array();
    }

    public static Superblock fromBytes(byte[] dados) {
        ByteBuffer buf = ByteBuffer.wrap(dados);
        Superblock sb = new Superblock();
        sb.magic = buf.getInt();
        sb.blockSize = buf.getInt();
        sb.totalBlocks = buf.getInt();
        sb.totalInodes = buf.getInt();
        sb.dataStart = buf.getInt();
        sb.rootInode = buf.getInt();
        sb.lastTxId = buf.getLong();
        return sb;
    }

    public boolean isValid() {
        return magic == Layout.MAGIC
                && blockSize == Layout.BLOCK_SIZE
                && totalBlocks == Layout.TOTAL_BLOCKS
                && totalInodes == Layout.TOTAL_INODES;
    }
}
