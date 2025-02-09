package org.libDeflate;
import java.nio.ByteBuffer;
import java.util.Random;

public class ZipUtil {
 //这个不属于标准库，所以性能啥的咱不管
 public static final ZipEntryM newEntry(String name, int lvl) {
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
   ZipEntryOutput.DeflaterIo def=out.outDef;
   int i=0;
   do {
    int size= ran.nextInt(rnd[i++]) + rnd[i++];
    ByteBuffer warp=ByteBuffer.allocate(size);
    byte[] buf=warp.array();
    def.copy.buf = warp;
    ran.nextBytes(buf);
    for (int k=0;k < size;++k)
     buf[k] &= 63;
    warp.position(size);
    ZipEntryM ze= newEntry(null, 1);
    out.putEntryOnlyIn(ze);
    def.setEntry(ze);
   }while (i < len);
   out.closeEntry();
  }
 }
}
