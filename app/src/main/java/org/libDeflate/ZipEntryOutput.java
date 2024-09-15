package org.libDeflate;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import me.steinborn.libdeflate.LibdeflateCRC32;
import me.steinborn.libdeflate.LibdeflateCompressor;
import me.steinborn.libdeflate.LibdeflateJavaUtils;
import java.io.BufferedWriter;


public class ZipEntryOutput extends ByteBufIo {
 public class DeflaterIo extends OutputStream implements BufIo {
  public LibdeflateCRC32 crc=new LibdeflateCRC32();
  public LibdeflateCompressor def;
  public int lvl;
  public BufOutput copy;
  public ByteBuffer old;
  public boolean flush;
  public void write(int b) {
   throw new RuntimeException();
  }
  public boolean isOpen() {
   return true;
  }
  public BufIo getBufIo() {
   ZipEntryOutput zip=ZipEntryOutput.this;
   BufIo buf=((BufIo)(zip.entry.mode > 0 ?copy: zip));
   return buf;
  }
  public ByteBuffer getBuf() {
   return getBufIo().getBuf();
  }
  public ByteBuffer getBufFlush() throws IOException {
   BufIo bufio = getBufIo();
   if (bufio instanceof ZipEntryOutput) {
    ZipEntryOutput zip=ZipEntryOutput.this;
    if (!zip.entry.notFix) {
     ByteBuffer buf=zip.buf;
     int pos=buf.position();
     buf.flip();
     buf.position(zip.pos);
     crc.update(buf);
     buf.clear();
     buf.position(pos);
    }
   }
   return bufio.getBufFlush();
  }
  public void putEntry(ZipEntryM zip) {
   crc.reset();
   int l=zip.mode;
   if (l > 0 && l != lvl) {
    LibdeflateCompressor def = this.def;
    if (def != null)def.close();
    this.def = new LibdeflateCompressor(l, 0);
    if (copy == null)copy = new BufOutput(1024);
    lvl = l;
   }
   flush = true;
  }
  public int write(ByteBuffer src) throws IOException {
   ZipEntryOutput zip=ZipEntryOutput.this;
   ZipEntryM en=zip.entry;
   int len=src.remaining();
   if (en.mode <= 0) {
    if (!en.notFix) {
     crc.update(src);
     src.rewind();
    }
    return zip.write(src);
   } else {
    copy.write(src);
    src.clear();
   }
   return len;
  }
  public void write(byte[] b, int off, int len) throws IOException {
   write(ByteBuffer.wrap(b, off, len));
  }
  public void free() {
   copy = null;
   old = null;
   LibdeflateCompressor def=this.def;
   if (def != null) {
    this.def = null;
    def.close();
   }
  }
  public void close() throws IOException {
   if (!flush)return;
   flush = false;
   ZipEntryOutput zip=ZipEntryOutput.this;
   ZipEntryM en=zip.entry;
   if (en.mode > 0) {
    ByteBuffer src=copy.buf;
    int size;
    if (ParallelDeflate.unIo(zip.outPage, src.position(), true) > 0)
     size = LibdeflateJavaUtils.getBufSize(src.capacity(), 0);
    else size = 0;
    ByteBuffer old = ParallelDeflate.deflate(def, crc, zip, src, this.old, size , false, en);
    if (old != null) {
     zip.write(old);
     old.clear();
     this.old = old;
    }
   } else writeEntryModify(en);
  }
 }
 public static final int AsInput=1;
 public static final int onlyInput=2;
 public static final int rcise=4;
 public static final int enmode=8;
 public static final int openJdk8opt=16;
 public static final int igonUtf8=32;
 public boolean pk78;
 public long last;
 public DeflaterIo outDef=new DeflaterIo();
 public ArrayList<ZipEntryM> list=new ArrayList();
 public long off;
 public long headOff;
 public File outFile;
 public FileChannel rnio;
 public ZipEntryM entry;
 public byte flag=1;
 public CharsetEncoder charsetEncoder;
 public int outPage;
 public ByteBuffer tbuf;
 public CharsetEncoder utf8=StandardCharsets.UTF_8.newEncoder();
 public void page(int size) {
  outPage = size >> 1;
  ByteBuffer buf=ByteBuffer.allocateDirect(16);
  buf.order(ByteOrder.LITTLE_ENDIAN);
  tbuf = buf;
 }
 public ZipEntryOutput(File out) throws FileNotFoundException {
  //文件模式需要支持并发写出，鸽了。
  this(out, 16384, null);
 }
 public ZipEntryOutput(File file, int size, CharsetEncoder utf) throws FileNotFoundException {
  this(new FileOutputStream(file).getChannel(), size, utf);
  outFile = file;
  rnio = (FileChannel)wt;
 }
 public ZipEntryOutput(WritableByteChannel wt) {
  this(wt, 16384, null);
 }
 public ZipEntryOutput(WritableByteChannel wt, int size, CharsetEncoder utf) {
  super(wt, size);
  charsetEncoder = utf;
  page(size);
 }
 public int pos;
 public ByteBuffer getBuf() {
  ByteBuffer buf= this.buf;
  pos = buf.position();
  return buf;
 }
 public ByteBuffer getBufFlush() throws IOException {
  ByteBuffer buf=this.buf;
  upLength(buf.position() - pos);
  flush();
  return buf;
 }  
 public void cancel() {
  File out=outFile;
  if (out != null) {
   out.delete();
   out = null;
  } 
  list = null;
  buf = null;
  try {
   close();
  } catch (Exception e) {
  }
 }
 public boolean isOpen() {
  return true;
 }
 public WritableByteChannel getNio() throws IOException {
  flush();
  return wt;
 }
 public void upLength(long i) {
  if (list.size() == 0) {
   headOff += i;
  }
  off += i;
 }
 public void write(int b) {
  throw new RuntimeException();
 }
 public void write(byte[] b, int off, int len) throws IOException {
  super.write(b, off, len);
  upLength(len);
 }
 public int write(ByteBuffer src) throws IOException {
  int i=super.write(src);
  upLength(i);
  return i;
 }
 public void closeEntry() throws IOException {
  ZipEntryM ze=entry;
  if (ze != null) {
   outDef.close();
   if (ze.size < 0) {
    if (ze.mode <= 0)ParallelDeflate.fixEntry(this, outDef.crc, ze);
    if ((flag & AsInput) > 0 && rnio == null) {
     writeEntryFix(ze);
    }
   }
   entry = null;
  }
 }
 public void putEntry(ZipEntryM zip) throws IOException {
  putEntry(zip, false, false);
 }
 public void putEntry(ZipEntryM zip, boolean raw) throws IOException {
  putEntry(zip, raw, false);
 }
 public void putEntry(ZipEntryM zip, boolean raw, boolean onlyIn) throws IOException {
  closeEntry();
  if (!raw)outDef.putEntry(zip);
  entry = zip;  
  zip.start = off;
  if (!onlyIn)list.add(zip);
  writeEntry(zip);
  last = off;
 }
 public void writeEntryModify(ZipEntryM ze) throws IOException {
  if ((flag & AsInput) == 0 || ze.notFix || ze.size <= 0)return;
  writeEntryModify(ze, size(), false);
 }
 public void writeEntryModify(ZipEntryM zip, long pos, boolean fix) throws IOException {
  int size=zip.size;
  long start=zip.start + 14;
  int wlen;
  if (zip.mode <= 0)wlen = 12;
  else {
   start += 4;
   wlen = 4;
  }
  ByteBuffer buff;
  int cpos;
  if (start >= pos) {
   buff = buf;
   cpos = buff.position();
   buff.position((int)(start - pos));
  } else {
   //必须进行一次写出，这里不处理细节控制
   if (!fix)return;
   cpos = 0;
   buff = tbuf;
  }
  boolean all=wlen > 4;
  //如果你想要细节的控制，无疑是过于困难的，建议让使用者输入简单。
  if (all)buff.putInt(size);
  buff.putInt(zip.csize);
  if (all)buff.putInt(zip.crc);
  if (start < pos) {
   if (start + wlen >= pos) {
    ByteBuffer buf = this.buf;
    int off=(int)(pos - start);
    int len=wlen - off;
    buf.rewind();
    buf.limit(len);
    buff.position(off);
    buf.put(buff);
    buff.rewind();
    buff.limit(off);
    buf.clear();
   } else buff.flip();
   if (buff.hasRemaining()) {
    FileChannel nio=rnio;
    nio.position(start);
    nio.write(buff);
   }
  }
  buff.clear();
  buff.position(cpos);
  if (!fix)zip.notFix = true;
 }
 public long size() {
  return off - buf.position();
 }
 public void writeEntry(ZipEntryM zip) throws IOException {
  boolean utf8;
  boolean skip;
  CharsetEncoder charsetEncoder=this.charsetEncoder;
  if ((flag & AsInput) > 0) {
   utf8 = zip.utf(charsetEncoder);
   skip = false;
  } else {
   utf8 = false;
   skip = true;
  }
  ByteBuffer buff=getBuf(1024);
  int pos=buff.position();
  buff.putInt(0x04034b50);
  putBits(buff, utf8, false, zip);
  fill(buff, pos + 26);
  buff.position(pos + 28);
  fill(buff, pos + 30);
  int len;
  if (!skip)len = zip.encode(charsetEncoder, this, utf8);
  else len = 0;
  if (len > 0) fixNameSize(off + 26, len);
  else buff.putShort(pos + 26, (short)(len = buff.position() - 30 - pos));
  upLength(len + 30);
 }
 public short globalBit(boolean data, boolean utf8) {
  short bit=0;
  if (data)bit |= 8;
  if ((flag & igonUtf8) == 0 && utf8)bit |= 2048;
  return bit;
 }
 public void fixNameSize(long g, int size) throws IOException {
  FileChannel rnio=this.rnio;
  rnio.position(g);
  ByteBuffer tbuf=this.tbuf;
  tbuf.putShort((short)size);
  tbuf.flip();
  rnio.write(tbuf);
  rnio.position(size());
  //否则需要一个队列
  tbuf.clear();
 }
 public void writeEntryFix(ZipEntryM zip) throws IOException {
  int size=zip.size;
  if (zip.notFix || size <= 0)return;
  ByteBuffer buff=getBuf(16);
  buff.putInt(0x08074b50);
  buff.putInt(zip.crc);
  buff.putInt(zip.csize);
  buff.putInt(size);
  upLength(16);
 }
 public void finish(ZipEntryM[] badlist) throws IOException {
  closeEntry();
  outDef.free();
  FileChannel nio=rnio;
  int flag=this.flag;
  if ((flag & AsInput) > 0 && rnio != null) {
   long pos=size();
   for (ZipEntryM ze:list) {
    if (ze.notFix || ze.size <= 0)continue;
    writeEntryModify(ze, pos, true);
   }
   nio.position(pos);
  }
  if ((flag & onlyInput) == 0) {
   long size=off;
   if (badlist != null) {
    Collections.addAll(list, badlist);
    Collections.shuffle(list);
   }
   for (ZipEntryM ze:list) {
    writeEntryEnd(ze);
   }
   writeEnd((int)(off - size));
  }
  list = null;
 }
 public void close() throws IOException {
  try {
   if (list != null) finish(null);
  } finally {
   ZipEntryOutput.DeflaterIo out=outDef;
   if (out != null) {
    outDef = null;
    out.free();
    super.close();
   }
  }
 }
 public void putBits(ByteBuffer buff, boolean utf8, boolean need, ZipEntryM zip) {
  short mode=zip.mode;
  int flag=this.flag;
  buff.putShort((short)0);//ver 该值没有任何作用
  boolean enmode=(flag & this.enmode) > 0;
  boolean input=(flag & AsInput) > 0;
  boolean ensize=!need && !input;
  boolean data=false;
  buff.putShort(ensize ?0: globalBit(data = (!zip.notFix && input && rnio == null), utf8));
  pk78 |= data;
  buff.putShort((short)(ensize ?0: !enmode && mode <= 0 ?0: 8));
  buff.putInt(ensize || enmode ?0: zip.xdostime);
  buff.putInt(ensize ?0: enmode ?0xff: zip.crc);
  buff.putInt(ensize ?0: (enmode && mode <= 0 ?0 : (flag & rcise) > 0 ?-1: zip.csize));
  int size=zip.size;
  buff.putInt(ensize ?0: Math.max(0, mode > 0 && (openJdk8opt & flag) > 0 && size > 65534 ?65534: size));
 }
 public void writeEntryEnd(ZipEntryM zip) throws IOException {
  CharsetEncoder charsetEncoder=this.charsetEncoder;
  boolean utf8=zip.utf(charsetEncoder);
  ByteBuffer buff=getBuf(1024);
  int pos=buff.position();
  buff.putInt(0x02014b50);
  buff.putShort((short)0);
  putBits(buff, utf8, true, zip);
  fill(buff, pos + 28);
  buff.position(pos + 30);
  fill(buff, pos + 42);
  buff.putInt((int)(zip.start - headOff));
  int len=zip.encode(charsetEncoder, this, utf8);
  if (len > 0)fixNameSize(off + 28, len);
  else buff.putShort(pos + 28, (short)(len = buff.position() - 46 - pos));
  upLength(len + 46);
 }
 public static void fill(ByteBuffer buf, int pos) {
  int i=buf.position();
  byte b=0;
  for (;i <= pos;++i)buf.put(i, b);
  buf.position(pos);
 }
 public void writeEnd(int size)throws IOException {
  ByteBuffer buff= getBuf(24);
  int pos=buff.position();
  int len= pos + (pk78 ?24: 22);
  buff.putInt(0X06054B50);
  fill(buff, pos + 8);
  short num=(short)list.size();
  buff.putShort(num);
  buff.putShort(num);
  buff.putInt(size);
  buff.putInt((int)(off - size - headOff));
  fill(buff, len);
  upLength(len);
 }
}
