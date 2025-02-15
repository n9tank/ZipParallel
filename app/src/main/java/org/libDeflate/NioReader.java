package org.libDeflate;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

public class NioReader extends Reader {
 public CharsetDecoder decode;
 //这个解码器没有处理无法衍射的字符（应该不需要处理）
 public ByteBuffer buf;
 public ReadableByteChannel io;
 public static ReadableByteChannel warp(final InputStream input) {
  return new ReadableByteChannel(){
   public void close() throws IOException {
    input.close();
   }
   public boolean isOpen() {
    return true;
   }
   public int read(ByteBuffer dst) throws IOException {
    byte[] b=dst.array();
    int pos=dst.position();
    int limit=dst.limit() - pos;
    int len=input.read(b, dst.arrayOffset() + pos, limit);
    if (len > 0)
     dst.position(pos + len);
    return len;
   }
  };
 }
 public NioReader(ReadableByteChannel read, int size, Charset set) {
  this(read, RC.newDbuf(size), set);
 }
 public NioReader(ReadableByteChannel read, ByteBuffer bytebuf, Charset set) {
  decode = ZipUtil.decode(set);
  io = read;
  buf = bytebuf;
  if (read != null)buf.limit(0);
 }
 public void close() throws IOException {
  ReadableByteChannel io=this.io;
  if (io != null)
   io.close();
  decode.reset();
 }
 public final int read(CharBuffer str) throws IOException {
  CharsetDecoder en=this.decode;
  int pos=str.position();
  ReadableByteChannel io=this.io;
  boolean eof=io == null;
  ByteBuffer buf=this.buf;
  tag: {
   boolean has;
   while ((has = buf.hasRemaining()) || !eof) {
    if (!str.hasRemaining())break tag;
    if (has || eof) {
     CoderResult code=en.decode(buf, str, eof);
     if ((has = !code.isUnderflow()) && code.isOverflow())
      break tag;
    }
    if (!has) {
     if (eof)
      break;
     buf.compact();
     int size;
     do {
      size = io.read(buf);
      if (size < 0) {
       io.close();
       this.io = null;
       eof = true;
      }
     }while (size > 0);
     buf.flip();
    }
   }
   en.flush(str);
  }
  pos = str.position() - pos;
  return !eof || pos > 0 || buf.hasRemaining() ?pos: -1;
 }
 public final int read(char[] cbuf, int off, int len) throws IOException {
  return read(CharBuffer.wrap(cbuf, off, len));
 }
}
