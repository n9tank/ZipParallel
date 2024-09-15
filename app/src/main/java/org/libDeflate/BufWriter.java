package org.libDeflate;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.io.IOException;

public class BufWriter extends Writer {
 public void write(char[] cbuf, int off, int len) throws IOException {
  write(CharBuffer.wrap(cbuf, off, len));
 }
 public void write(String str) throws IOException {
  write(CharBuffer.wrap(str));
 }
 public void write(CharBuffer str) throws IOException {
  BufIo put=this.buf;
  ByteBuffer buf=put.getBuf();
  CharsetEncoder en=this.en;
  while (str.hasRemaining()) {
   if (en.encode(str, buf, false).isOverflow())
    buf = put.getBufFlush();
  }
 }
 public void flush() throws IOException {
  BufIo put=this.buf;
  ByteBuffer buf=put.getBuf();
  CharBuffer str=CharBuffer.allocate(0);
  while (en.encode(str, buf, true).isOverflow())
   buf = put.getBufFlush();
  while (en.flush(buf).isOverflow())
   buf = put.getBufFlush();
  en.reset();
 }
 public void close() throws IOException {
  buf.close();
 }
 public boolean flush;
 public BufIo buf;
 public CharsetEncoder en;
 public BufWriter(BufIo out, Charset set) {
  buf = out;
  en = set.newEncoder();
 }
}
