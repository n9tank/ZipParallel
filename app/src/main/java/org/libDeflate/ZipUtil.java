package org.libDeflate;
import java.nio.ByteBuffer;
import java.util.Random;

public class ZipUtil {
 //这个不属于标准库，所以性能啥的咱不管
 public static ZipEntryM newEntry(String name, int lvl) {
  ZipEntryM zip= new ZipEntryM(name, lvl);
  zip.notFix = true;
  return zip;
 }
 public static void addRandomHead(ZipEntryOutput out, int rnd[]) throws Exception {
  int len=rnd.length;
  if (len == 1) {
   ByteBuffer buf=ByteBuffer.allocateDirect(4);
   buf.putInt(0x504b0304);
   buf.flip();
   out.write(buf);
  } else {
   Random ran=new Random();
   int j=0;
   int all=0;
   for (int i=len;--i > 0;) {
    int n= ran.nextInt(rnd[i]) + rnd[--i];
    all += n;
    rnd[j++] = n;
   }
   byte[] buf=new byte[all];
   //由于输入页不能太多所以直接分配就好
   ran.nextBytes(buf);
   for (int k=buf.length;--k >= 0;)
    buf[k] &= 63;
   //让压缩工作
   int c=0;
   ZipEntryOutput.DeflaterIo def=out.outDef;
   while (--j >= 0) {
    int wlen=rnd[j];
    out.putEntry(newEntry(null, 1), false, true);
    def.write(buf, c, wlen);
    c += wlen;
   }
   out.closeEntry();
  }
 }
}
