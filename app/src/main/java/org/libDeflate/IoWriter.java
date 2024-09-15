package org.libDeflate;
import java.io.OutputStream;
import java.nio.channels.WritableByteChannel;

public abstract class IoWriter {
 public int bufSize;
 public OutputStream out;
 public abstract void flush()throws Exception;
}
