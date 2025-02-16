package org.libDeflate;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.Random;
import me.steinborn.libdeflate.LibdeflateCompressor;
import me.steinborn.libdeflate.LibdeflateJavaUtils;

public class ZipUtil {
 //这个不属于标准库，所以性能啥的咱不管
 public static final ZipEntryM newEntry(String name, int lvl) {
  ZipEntryM zip= new ZipEntryM(name, lvl);
  if (RC.zip_crc)zip.notFix = true;
  return zip;
 }
 public static CharsetEncoder encode(Charset set) {
  CharsetEncoder encode= set.newEncoder();
  CodingErrorAction rp=CodingErrorAction.REPLACE;
  encode.onMalformedInput(rp);
  encode.onUnmappableCharacter(rp);
  return encode;
 }
 public static CharsetDecoder decode(Charset set) {
  CharsetDecoder dec = set.newDecoder();
  CodingErrorAction rp=CodingErrorAction.REPLACE;
  dec.onMalformedInput(rp);
  dec.onUnmappableCharacter(rp);
  return dec;    
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
       old = RC.newDbuf(BufOutput.tableSizeFor(bufsize));
      drc = old;
     }
     out.deflate(def, src, drc , true, ze);
     if (old != null)old.clear();
    }while (i < len);
   } finally {
    def.close();
   }
  }
 }
}
