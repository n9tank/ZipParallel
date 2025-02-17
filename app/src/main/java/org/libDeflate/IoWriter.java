package org.libDeflate;
import java.io.*;
import java.nio.charset.*;

public abstract class IoWriter {
 public int bufSize;
 public BufIo out;
 public BufferedWriter getWriter(Charset set) {
  return getWriter(set, Math.min(bufSize, 8192));
 }
 public BufferedWriter getWriter(Charset set, int size) {
  return new BufferedWriter(new BufWriter(out, set), size);
 }
 public abstract void flush()throws Exception;
}
