package org.libDeflate;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;

public interface BufIo extends WritableByteChannel {
 public ByteBuffer getBuf();
 public ByteBuffer getBufFlush() throws IOException;
}
