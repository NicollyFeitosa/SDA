package fs.disk;

import java.io.IOException;

public interface BlockDevice {
    byte[] readBlock(int n) throws IOException;

    void writeBlock(int n, byte[] data) throws IOException;
}
