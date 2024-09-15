package org.libDeflate;
import java.io.OutputStream;
import java.nio.channels.WritableByteChannel;
import java.io.BufferedWriter;
import java.nio.charset.Charset;

public abstract class IoWriter {
 public int bufSize;
 public WritableByteChannel out;
 public BufferedWriter getWriter(Charset set) {
  return new BufferedWriter(new BufWriter((BufIo)out, set));
 }
 public abstract void flush()throws Exception;
}
