package org.libDeflate;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.util.Date;
import me.steinborn.libdeflate.LibdeflateCRC32;

public class ZipEntryM {
 public byte mode;
 public CharSequence name;
 public int csize;
 public int size;
 public int crc;
 public int xdostime;
 public long start;
 public boolean notFix;
 public void crc(ByteBuffer buf, int off, int len) {
  int crc=this.crc;
  if (buf.isDirect())
   this.crc = LibdeflateCRC32.crc32Direct(crc, buf, off, len);
  this.crc = LibdeflateCRC32.crc32Heap(crc, buf.array(), buf.arrayOffset() + off, len);
 }
 public ZipEntryM(CharSequence str, int lvl) {
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
 public int encode(CharsetEncoder en, ByteBuffer out) {
  int pos=out.position();
  CharBuffer buf=CharBuffer.wrap(name);
  en.encode(buf, out, true);
  en.flush(out);
  int len = out.position() - pos;
  out.limit(out.capacity());
  en.reset();
  return len;
 }
}
