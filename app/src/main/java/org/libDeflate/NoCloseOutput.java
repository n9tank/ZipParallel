package org.libDeflate;
import java.io.IOException;
import java.io.OutputStream;

public class NoCloseOutput extends OutputStream {
 public OutputStream out;
 public NoCloseOutput(OutputStream put) {
  this.out = put;
 }
 public void write(int b) {
  throw new RuntimeException();
 }
 public void write(byte[] b, int off, int len) throws IOException {
  out.write(b, off, len);
 }
}
