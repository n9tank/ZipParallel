package org.libDeflate;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import me.steinborn.libdeflate.LibdeflateCRC32;
import me.steinborn.libdeflate.LibdeflateCompressor;
import java.io.File;
import java.nio.channels.ReadableByteChannel;


public class ZipEntryOutput extends ByteBufIo {
 public class DeflaterIo implements BufIo {
  public LibdeflateCompressor def;
  public int lvl;
  public BufIo io;
  public BufOutput copy=new BufOutput(0);
  public ByteBuffer old;
  public boolean flush;
  public boolean isOpen() {
   return true;
  }
  public ByteBuffer getBuf() {
   return io.getBuf();
  }
  public void Crc() {
   if (!RC.zip_crc)return;
   ZipEntryOutput zip=ZipEntryOutput.this;
   ZipEntryM en=zip.entry;
   if (en.mode <= 0 && !en.notFix) {
    ByteBuffer buf=zip.buf;
    int zpos=zip.pos;
    en.crc(buf, zpos, buf.position() - zpos);
   }
  }
  public void end() {
   Crc();
   io.end();
  }
  public ByteBuffer getBufFlush() throws IOException {
   Crc();
   return io.getBufFlush();
  }
  public void putEntry(ZipEntryM zip) throws IOException {
   ZipEntryOutput.this.putEntry(zip);
   setEntry(zip);
  }
  public void setEntry(ZipEntryM zip) {
   int l=zip.mode;
   if (l > 0 && l != lvl) {
    LibdeflateCompressor def = this.def;
    if (def != null)def.close();
    this.def = new LibdeflateCompressor(l, 0);
    BufOutput copy=this.copy;
    ByteBuffer buf=copy.buf;
    int size=zip.size;
    if (buf == null || size > buf.capacity())
     copy.buf = RC.newbuf(copy.tableSizeFor(size));
    lvl = l;
   }
   io = l > 0 ?ZipEntryOutput.this: copy;
   flush = true;
  }
  public int write(ByteBuffer src) throws IOException {
   if (RC.zip_crc) {
    ZipEntryOutput zip=ZipEntryOutput.this;
    ZipEntryM en=zip.entry;
    if (en.mode <= 0 && !en.notFix)
     en.crc(src, src.position(), src.remaining());
   }
   return io.write(src);
  }
  public void free() {
   io = null;
   copy.buf = null;
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
    ByteBuffer src=this.copy.buf;
    src.flip();
    this.old = ParallelDeflate.deflate(def, zip, src, this.old , true, false, en);
    src.clear();
   }
  } 
 }
 public static final int AsInput=1;
 public static final int onlyInput=2;
 public static final int enmode=4;
 public static final int openJdk8opt=8;
 public static final int igonUtf8=16;
 public static final int zip64enmode=32;
 public long last;
 public DeflaterIo outDef=new DeflaterIo();
 public ArrayList<ZipEntryM> list=new ArrayList();
 public long off;
 public long headOff;
 public Path outFile;
 public ZipEntryM entry;
 public int flag=1;
 public CharsetEncoder charsetEncoder;
 public CharsetEncoder utf8=StandardCharsets.UTF_8.newEncoder();
 public ZipEntryOutput(File out) throws IOException {
  this(out.toPath());
 }
 //强烈推荐至少64K缓存，此缓存不会尝试动态扩容，因此需要尽可能的大
 public ZipEntryOutput(Path out) throws IOException {
  //文件模式需要支持并发写出，鸽了。
  this(out, RC.NSIZE, null);
 }
 public ZipEntryOutput(Path fs, int size, CharsetEncoder utf) throws IOException {
  this(FileChannel.open(fs, StandardOpenOption.CREATE, StandardOpenOption.WRITE), size, utf);
  outFile = fs;
 }
 public ZipEntryOutput(WritableByteChannel wt) {
  this(wt, RC.NSIZE, null);
 }
 public ZipEntryOutput(WritableByteChannel wt, int size, CharsetEncoder utf) {
  super(wt, size);
  charsetEncoder = utf;
 }
 public int pos;
 public ByteBuffer getBuf() {
  ByteBuffer buf= this.buf;
  pos = buf.position();
  return buf;
 }
 public void end() {
  upLength(buf.position() - pos);
 }
 public ByteBuffer getBufFlush() throws IOException {
  ByteBuffer buf=this.buf;
  upLength(buf.position() - pos);
  flush();
  pos = 0;
  return buf;
 }
 public void cancel() {
  list = null;
  buf = null;
  try {
   close();
  } catch (Exception e) {
  }
  Path out=outFile;
  if (out != null) {
   try {
    Files.delete(out);
   } catch (IOException e) {}
   out = null;
  } 
 }
 public boolean isOpen() {
  return true;
 }
 public void copyFromAndCrc32(ReadableByteChannel ch) throws IOException {
  ByteBuffer buf=this.buf;
  boolean usecrc=RC.zip_crc && !entry.notFix;
  int crc=0;
  while (true) {
   int pos=buf.position();
   int len = ch.read(buf);
   if (len == 0)flush();
   else if (len > 0) {
    if (usecrc)crc = LibdeflateCRC32.crc32Direct(crc, buf, pos, len);
    upLength(len);
   } else break;
  }
  if (usecrc)entry.crc = crc;
 }
 public void upLength(long i) {
  if (list.size() == 0)
   headOff += i;
  off += i;
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
    if (ze.mode <= 0)ParallelDeflate.fixEntry(this, ze);
    if ((flag & AsInput) > 0) {
     writeEntryFix(ze);
    }
   }
   entry = null;
  }
 }
 public void putEntryOnlyIn(ZipEntryM zip) throws IOException {
  closeEntry();
  entry = zip;  
  zip.start = off;
  writeEntry(zip);
  last = off;
 }
 public void putEntry(ZipEntryM zip) throws IOException {
  putEntryOnlyIn(zip);
  list.add(zip);
 }
 public long size() {
  return off - buf.position();
 }
 public void writeEntry(ZipEntryM zip) throws IOException {
  boolean utf8;
  boolean skip;
  CharsetEncoder charsetEncoder=this.charsetEncoder;
  int size=30;
  if ((flag & AsInput) > 0) {
   if (utf8 = zip.utf(charsetEncoder))
    charsetEncoder = this.utf8;
   size += charsetEncoder.maxBytesPerChar() * zip.name.length();
   skip = false;
  } else {
   utf8 = false;
   skip = true;
  }
  ByteBuffer buff=getBuf(size);
  int pos=buff.position();
  buff.putInt(0x04034b50);
  putBits(buff, utf8, false, zip);
  fill(buff, pos + 30);
  int len;
  if (!skip) {
   len = zip.encode(charsetEncoder, buff);
   buff.putShort(pos + 26, (short)len);
  } else len = 0;
  releaseBuf(buff, 30 + len);
 }
 public short globalBit(boolean data, boolean utf8) {
  short bit=0;
  if (data)bit |= 8;
  if ((flag & igonUtf8) == 0 && utf8)bit |= 2048;
  return bit;
 }
 public void writeEntryFix(ZipEntryM zip) throws IOException {
  int size=zip.size;
  if (zip.notFix || size <= 0)return;
  ByteBuffer buf=this.buf;
  if (buf.remaining() < 16) {
   buf = ByteBuffer.allocate(16);
   buf.order(ByteOrder.LITTLE_ENDIAN);
  }
  buf.putInt(0x08074b50);
  buf.putInt(zip.crc);
  buf.putInt(zip.csize);
  buf.putInt(size);
  releaseBuf(buf, 16);
 }
 public void finish() throws IOException {
  closeEntry();
  outDef.free();
  int flag=this.flag;
  if ((flag & onlyInput) == 0) {
   long size=off;
   for (ZipEntryM ze:list) {
    writeEntryEnd(ze);
   }
   long len=off - size;
   if ((flag & zip64enmode) > 0)
    writrEnd64(len);
   else writeEnd(len);
  }
  list = null;
 }
 public void close() throws IOException {
  try {
   if (list != null) finish();
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
  buff.putShort(ensize ?0: globalBit((!zip.notFix && input && !(wt instanceof FileChannel)), utf8));
  buff.putShort((short)(ensize ?0: !enmode && mode <= 0 ?0: 8));
  buff.putInt(ensize || enmode ?0: zip.xdostime);
  buff.putInt(ensize ?0: enmode ?0xff: zip.crc);
  buff.putInt(ensize ?0: (enmode && mode <= 0 ?0 : zip.csize));
  int size=zip.size;
  buff.putInt(ensize ?0: Math.max(0, mode > 0 && (openJdk8opt & flag) > 0 && size > 65534 ?65534: size));
 }
 public void writeEntryEnd(ZipEntryM zip) throws IOException {
  CharsetEncoder charsetEncoder=this.charsetEncoder;
  boolean utf8=zip.utf(charsetEncoder);
  if (utf8)charsetEncoder = this.utf8;
  ByteBuffer buff=getBuf(46 + (int)(charsetEncoder.maxBytesPerChar() * zip.name.length()));
  int pos=buff.position();
  buff.putInt(0x02014b50);
  buff.putShort((short)0);
  putBits(buff, utf8, true, zip);
  fill(buff, pos + 28);
  buff.position(pos + 30);
  fill(buff, pos + 42);
  buff.putInt((int)(zip.start - headOff));
  int len=zip.encode(charsetEncoder, buff);
  buff.putShort(pos + 28, (short)len);
  releaseBuf(buff, 46 + len);
 }
 public void fill(ByteBuffer buf, int pos) {
  if (buf == this.buf) {
   int i=buf.position();
   byte b=0;
   for (;i <= pos;++i)buf.put(i, b);
  }
  buf.position(pos);
 }
 public void releaseBuf(ByteBuffer buf, int len) throws IOException {
  if (buf != this.buf) {
   buf.flip();
   super.write(buf);
  }
  upLength(len);
 }
 public void writrEnd64(long size) throws IOException {
  ByteBuffer buff= getBuf(98);
  int pos=buff.position();
  buff.putInt(0x06064b50);
  fill(buff, pos + 32);
  buff.putLong(list.size());
  buff.putLong(size);
  buff.putLong(off - size - headOff);
  buff.putInt(0x07064b50);
  fill(buff, pos + 64);
  buff.putLong(off);
  fill(buff, pos + 76);
  buff.putInt(0X06054B50);
  fill(buff, pos + 86);
  buff.putShort((short)0xffff);
  fill(buff, pos + 98);
  releaseBuf(buff, 98); 
 }
 public void writeEnd(long size)throws IOException {
  ByteBuffer buff= getBuf(22);
  int pos=buff.position();
  buff.putInt(0X06054B50);
  fill(buff, pos + 10);
  buff.putShort((short)list.size());
  buff.putInt((int)size);
  buff.putInt((int)(off - size - headOff));
  fill(buff, pos + 22);
  releaseBuf(buff, 22); 
 }
}
