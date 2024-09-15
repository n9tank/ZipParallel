package org.libDeflate;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class ByteBufIo extends OutputStream implements WritableByteChannel {
 public void write(int b) {
  throw new RuntimeException();
 }
 public boolean isOpen() {
  return true;
 }
 public ByteBuffer buf;
 public OutputStream out;
 public WritableByteChannel wt;
 public ByteBufIo(OutputStream out, int size) {
  buf = ByteBuffer.allocate(size);
  this.out = out;
 }
 public ByteBufIo(WritableByteChannel out, int size) {
  wt = out;
  buf = ByteBuffer.allocateDirect(size);
 }
 public void close() throws IOException {
  try {
   flush();
  } finally {
   if (out != null)
    out.close();
   else wt.close();
  }
 }
 public void flush() throws IOException {
  ByteBuffer buf=this.buf;
  if (buf == null)return;
  OutputStream outs=out;
  if (outs == null) {
   buf.flip();
   while (buf.hasRemaining())
    wt.write(buf);
  } else out.write(buf.array(), 0, buf.position());
  buf.clear();
 }
 public void put(byte brr[], int off, int len) throws IOException {
  OutputStream outs=out;
  if (outs == null) {
   ByteBuffer buf=ByteBuffer.wrap(brr, off, len);
   while (buf.hasRemaining())
    wt.write(buf);
  } else outs.write(brr, off, len);
 }
 public void write(byte brr[], int off, int len) throws IOException {
  ByteBuffer buf=this.buf;
  if (buf == null)throw new IOException();
  int cy=buf.capacity();
  int limt=buf.remaining();
  int wlen=len - limt;
  if (limt < cy || wlen < 0 || buf.isDirect())
   buf.put(brr, off, Math.min(len, limt));
  if (wlen >= 0) {
   flush();
   off += limt;
   if (wlen >= cy)
    put(brr, off , wlen);
   else buf.put(brr, off, wlen);
  }
 }
 public int write(ByteBuffer put) throws IOException {
  int len=put.limit();
  int rsize=len;
  if (!put.isDirect()) {
   write(put.array(), 0, len);
  } else {
   ByteBuffer buf=this.buf;
   if (buf == null)throw new IOException();
   int cy=buf.capacity();
   int limt=buf.remaining();
   int wlen=len - limt;
   if (wlen > 0) {
    if (!buf.isDirect()) {
     if (len > cy) {
      ByteBuffer old=buf;
      this.buf = buf = buf.allocate(cy = len);
      old.flip();
      buf.put(old);
     }
     limt = buf.limit();
    }
    put.limit(limt);
   }
   if (limt < cy || wlen < 0 || !buf.isDirect())buf.put(put);
   limt = put.position();
   wlen = len - limt;
   if (wlen >= 0) {
    flush();
    put.limit(len);
    if (wlen >= cy) {
     WritableByteChannel wt=this.wt;
     if (wt != null) {
      while (put.hasRemaining())
       wt.write(put);
     }
    }
    buf.put(put);
   }
  }
  return rsize;
 }
}
