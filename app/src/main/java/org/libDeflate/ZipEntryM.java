package org.libDeflate;
import java.util.Date;

public class ZipEntryM {
 public byte mode;
 public String name;
 public int csize;
 public int size=-1;
 public int crc;
 public int xdostime;
 public long start;
 public boolean onlyInput;
 //用于伪装文件头
 public boolean notFix;
 public ZipEntryM(String str, int lvl) {
  name = str;
  mode = (byte)lvl;
 }
 public static long unInt(int i) {
  return (long)i & 0xffffffffl;
 }
 public void setTime(long time) {
  Date ldt=new Date(time);
  int year = ldt.getYear() - 80;
  xdostime = (int)(((year << 25 |
   (ldt.getMonth() + 1) << 21 |
   (ldt.getDate()) << 16 |
   ldt.getHours() << 11 |
   ldt.getMinutes() << 5 |
   ldt.getSeconds() >> 1) & 0xffffffffL) + ((time % 2000) << 32));
 }
}
