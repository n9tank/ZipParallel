package org.libDeflate;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.io.IOException;

public class ZipEntryM {
 public byte mode;
 public String name;
 public int csize;
 public int size=-1;
 public int crc;
 public int xdostime;
 public long start;
 public boolean notFix;
 public ZipEntryM(String str, int lvl) {
  name = str;
  mode = (byte)lvl;
 }
 public static long unInt(int i) {
  return (long)i & 0xffffffffl;
 }
 public void setTime(long time) {
  xdostime = dosTime(time);
 }
 public static int dosTime(long time) {
  Date ldt=new Date(time);
  int year = ldt.getYear() - 80;
  return ((year << 25) |
   ((ldt.getMonth() + 1) << 21) |
   (ldt.getDate() << 16) |
   (ldt.getHours() << 11) |
   (ldt.getMinutes() << 5) |
   (ldt.getSeconds() >> 1)
   );
 }
 public boolean utf(CharsetEncoder en) {
  return en == null || !en.canEncode(name);
 }
 public int encode(CharsetEncoder en, ZipEntryOutput zip, boolean utf) throws IOException {
  String str=name;
  int len=0;
  ByteBufIo io=zip.rnio == null ?null: zip;
  ByteBuffer out=io.buf;
  int pos=out.position();
  int cy=out.capacity() & -4096;
  CharBuffer buf=CharBuffer.wrap(str);
  CharsetEncoder cr=(utf ?zip.utf8: en);
  while (cr.encode(buf, out, true).isOverflow()) {
   len += out.position() - pos;
   io.flush();
   pos = out.position();
   out.limit(cy);
  }
  while (cr.flush(out).isOverflow()) {
   len += out.position() - pos;
   io.flush();
   pos = out.position();
   out.limit(cy);
  }
  if (len > 0)len += out.position() - pos;
  out.limit(out.capacity());
  cr.reset();
  return len;
 }
}
