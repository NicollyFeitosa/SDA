package fs.disk;

public final class Layout {
    public static final int BLOCK_SIZE = 512;
    public static final int TOTAL_BLOCKS = 1024;
    public static final int SUPERBLOCK = 0;
    public static final int BLOCK_BITMAP = 1;
    public static final int INODE_BITMAP = 2;
    public static final int INODE_TABLE_START = 3;
    public static final int INODE_TABLE_BLOCKS = 8;
    public static final int INODE_SIZE = 64;
    public static final int INODES_PER_BLOCK = BLOCK_SIZE / INODE_SIZE;
    public static final int TOTAL_INODES = INODE_TABLE_BLOCKS * INODES_PER_BLOCK;
    public static final int JOURNAL_HEADER = 11;
    public static final int JOURNAL_DATA_START = 12;
    public static final int JOURNAL_CAPACITY = 63;
    public static final int DATA_START = 75;
    public static final int ROOT_INODE = 0;
    public static final int MAGIC = 0x53494D46;

    private Layout() {}
}
