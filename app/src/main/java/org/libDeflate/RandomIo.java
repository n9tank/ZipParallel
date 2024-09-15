package org.libDeflate;
import java.util.concurrent.ThreadLocalRandom;
import java.io.OutputStream;

public class RandomIo extends IoWriter {
 int size;
 int off;
 public RandomIo(int size, int off) {
  this.size = size;
  this.off = off;
  bufSize = size + off;
 }
 public void flush() throws Exception {
  ThreadLocalRandom ran=ThreadLocalRandom.current();
  OutputStream out=this.out;
  int len=size + ran.nextInt(off);
  byte[] buf=new byte[Math.min(1024, len)];
  int c=0;
  int blen=buf.length;
  try {
   while (len > 0) {
    if (c >= blen) {
     ran.nextBytes(buf);
     c = 0;
    }
    int wlen=Math.min(blen, len);
    out.write(buf, c, wlen);
    c += wlen;
    len -= wlen;
   }
  } finally {
   out.close();
  }
 }
}
