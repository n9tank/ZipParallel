package org.libDeflate;
import java.io.OutputStream;
import java.nio.channels.WritableByteChannel;
import java.io.BufferedWriter;
import java.nio.charset.Charset;

public abstract class IoWriter {
 public int bufSize;
 public WritableByteChannel out;
 public BufferedWriter getWriter(Charset set) {
  return getWriter(set, Math.min(bufSize, 8192));
 }
 public BufferedWriter getWriter(Charset set, int size) {
  return new BufferedWriter(new BufWriter((BufIo)out, set), size);
 }
 public abstract void flush()throws Exception;
}
