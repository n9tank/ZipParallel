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
  if (len <= 1) {
   ByteBuffer buf=out.buf;
   buf.putInt(0x504b0304);
   out.upLength(4);
  } else {
   Random ran=new Random();
   int j=0;
   int all=0;
   int i=0;
   do {
    int n= ran.nextInt(rnd[i++]) + rnd[i++];
    all += n;
    rnd[j++] = n;
   }while (i < len);
   byte[] buf=new byte[all];
   //由于输入页不能太多所以直接分配就好
   ran.nextBytes(buf);
   len = buf.length;
   for (int k=0;k < len;++k)
    buf[k] &= 63;
   //让压缩工作
   int c=0;
   ZipEntryOutput.DeflaterIo def=out.outDef;
   i = 0;
   do{
    int wlen=rnd[i];
    def.src = ByteBuffer.wrap(buf, c, wlen);
    out.putEntry(newEntry(null, 1), false, true);
    c += wlen;
   }while(++i < j);
   out.closeEntry();
  }
 }
}
