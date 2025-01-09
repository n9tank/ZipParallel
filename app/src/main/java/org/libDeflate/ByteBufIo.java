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
   if (buf != null) {
    flush();
    if (wt instanceof FileChannel) {
     FileChannel rnio=(FileChannel)wt;
     rnio.truncate(rnio.position());
    }
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
  int limt=put.limit();
  ByteBuffer buf=this.buf;
  if (len >= buf.capacity()) {
   int pos=buf.position();
   if (pos > 0) {
    pos &= 4095;
    if (pos > 0) {
     int rem=4096 - pos;
     len -= rem;
     put.limit(put.position() + rem);
     buf.put(put);
    }
    flush();
   }
   put.limit(put.position() + (len & -4096));
   WritableByteChannel wt=this.wt;
   while (put.hasRemaining())
    wt.write(put);
  } else {
   int rem=buf.remaining();
   put.limit(put.position() + Math.min(len, rem));
   buf.put(put);
   if (len >= rem)
    flush();
  }
  put.limit(limt);
  buf.put(put);
  return len;
 }
}
