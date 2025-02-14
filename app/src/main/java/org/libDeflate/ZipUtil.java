package org.libDeflate;
import java.nio.ByteBuffer;
import java.util.Random;
import me.steinborn.libdeflate.LibdeflateJavaUtils;
import me.steinborn.libdeflate.LibdeflateCompressor;

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
   int i=0;
   if (RC.zip_deflate_io) {
    ZipEntryOutput.DeflaterIo def=out.outDef;
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
   } else {
    ByteBuffer old=null;
    LibdeflateCompressor def= new LibdeflateCompressor(1, 0);
    try {
     do {
      int size= ran.nextInt(rnd[i++]) + rnd[i++];
      ByteBuffer src=ByteBuffer.allocate(size);
      byte[] buf=src.array();
      ran.nextBytes(buf);
      for (int k=0;k < size;++k)
       buf[k] &= 63;
      ZipEntryM ze= newEntry(null, 1);
      out.putEntryOnlyIn(ze);
      ByteBuffer drc=out.buf;
      int bufsize=LibdeflateJavaUtils.getBufSize(src.remaining(), 0);
      if (drc.remaining() < bufsize) {
       if (old == null || old.remaining() < bufsize)
        old = RC.newbuf(BufOutput.tableSizeFor(size));
       drc = old;
      }
      out.deflate(def, src, drc , true, ze);
     }while (i < len);
    } finally {
     def.close();
    }
   }
  }
 }
}
