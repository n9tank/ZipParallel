package org.libDeflate;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.FileChannel;

public class ByteBufIo extends OutputStream implements BufIo {
 public void write(int b) {
  throw new RuntimeException();
 }
 public boolean isOpen() {
  return true;
 }
 public ByteBuffer buf;
 public WritableByteChannel wt;
 public ByteBufIo(WritableByteChannel out, int size) {
  wt = out;
  ByteBuffer buf = ByteBuffer.allocateDirect(size);
  buf.order(ByteOrder.LITTLE_ENDIAN);
  this.buf = buf;
 }
 public void close() throws IOException {
  try {
   flush();
   if (wt instanceof FileChannel) {
    FileChannel rnio=(FileChannel)wt;
    rnio.truncate(rnio.position());
   }
  } finally {
   wt.close();
  }
 }
 public ByteBuffer getBuf() {
  return buf;
 }
 public ByteBuffer getBufFlush() throws IOException {
  ByteBuffer buf=this.buf;
  flush();
  return buf;
 }
 public void end() {
 }
 public void flush() throws IOException {
  ByteBuffer buf=this.buf;
  if (buf == null)return;
  buf.flip();
  WritableByteChannel wt=this.wt;
  while (buf.hasRemaining())
   wt.write(buf);
  buf.clear();
 }
 public ByteBuffer getBuf(int page) {
  ByteBuffer buf=this.buf;
  if (buf.remaining() < page) {
   buf = page < buf.capacity() ?ByteBuffer.allocate(page): ByteBuffer.allocateDirect(page);
   buf.order(ByteOrder.LITTLE_ENDIAN);
  }
  return buf;
 }
 public void write(byte brr[], int off, int len) throws IOException {
  write(ByteBuffer.wrap(brr, off, len));
 }
 public int write(ByteBuffer put) throws IOException {
  int len=put.remaining();
  ByteBuffer buf=this.buf;
  int cy=buf.capacity();
  int limt=buf.remaining();
  int wlen=len - limt;
  int rlen=put.limit();
  if (wlen > 0)put.limit(put.position() + limt);
  if (limt < cy || wlen < 0)buf.put(put);
  limt = put.position();
  wlen = len - limt;
  if (wlen > 0) {
   flush();
   if (wlen >= cy) {
    WritableByteChannel wt=this.wt;
    if (wt != null) {
     put.limit(limt + (wlen & -4096));
     while (put.hasRemaining())
      wt.write(put);
    }
   }
   put.limit(rlen);
   buf.put(put);
  }
  return len;
 }
}
