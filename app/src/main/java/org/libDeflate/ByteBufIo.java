package org.libDeflate;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.WritableByteChannel;

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
  } finally {
   wt.close();
  }
 }
 public ByteBuffer getBuf() {
  return buf;
 }
 public ByteBuffer getBufFlush() throws IOException {
  flush();
  return buf;
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
 public ByteBuffer getBuf(int page) throws IOException {
  ByteBuffer buf=this.buf;
  int pos=buf.position();
  int cy=buf.capacity() - page;
  if (pos > cy) {
   int len = pos & -4096;
   WritableByteChannel wt=this.wt;
   buf.rewind();
   buf.limit(len);
   while (buf.hasRemaining())
    wt.write(buf);
   buf.limit(pos);
   buf.position(len); 
   buf.compact();
  }
  return buf;
 }
 public void put(byte brr[], int off, int len) throws IOException {
  WritableByteChannel wt=this.wt;
  if (wt != null) {
   ByteBuffer buf=ByteBuffer.wrap(brr, off, len);
   while (buf.hasRemaining())
    wt.write(buf);
  }
 }
 public void write(byte brr[], int off, int len) throws IOException {
  ByteBuffer buf=this.buf;
  int cy=buf.capacity();
  int limt=buf.remaining();
  int wlen=len - limt;
  if (limt < cy || wlen < 0 || buf.isDirect())
   buf.put(brr, off, Math.min(len, limt));
  else wlen = len;
  if (wlen > 0) {
   flush();
   off += limt;
   if (wlen >= cy) {
    int rlen=wlen & 4095;
    put(brr, off , wlen & -4096);
    off += wlen;
    wlen = rlen;
   }
   buf.put(brr, off, wlen);
  }
 }
 public int write(ByteBuffer put) throws IOException {
  int len=put.remaining();
  int rsize=len;
  if (!put.isDirect()) {
   write(put.array(), put.position(), len);
  } else {
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
  }
  return rsize;
 }
}
