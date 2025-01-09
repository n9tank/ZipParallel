package org.libDeflate;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import me.steinborn.libdeflate.LibdeflateCRC32;
import me.steinborn.libdeflate.LibdeflateCompressor;


public class ZipEntryOutput extends ByteBufIo {
 public class DeflaterIo extends OutputStream implements BufIo {
  public LibdeflateCRC32 crc=new LibdeflateCRC32();
  public LibdeflateCompressor def;
  public int lvl;
  public BufOutput copy;
  public ByteBuffer old;
  public ByteBuffer src;
  public boolean flush;
  public void write(int b) {
   throw new RuntimeException();
  }
  public boolean isOpen() {
   return true;
  }
  public ByteBuffer getBuf() {
   ZipEntryOutput zip=ZipEntryOutput.this;
   return (zip.entry.mode > 0 ?copy: zip).getBuf();
  }
  public void end() {
   ZipEntryOutput zip=ZipEntryOutput.this;
   if (zip.entry.mode <= 0)
    zip.end();
  }
  public ByteBuffer getBufFlush() throws IOException {
   ZipEntryOutput zip=ZipEntryOutput.this;
   boolean raw;
   BufIo bufio = ((raw = zip.entry.mode <= 0) ?zip: copy);
   if (raw && !zip.entry.notFix) {
    ByteBuffer buf=zip.buf;
    int pos=buf.position();
    buf.flip();
    buf.position(zip.pos);
    crc.update(buf);
    buf.clear();
    buf.position(pos);
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
    if (src == null && copy == null)
     copy = new BufOutput(1024);
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
   } else copy.write(src);
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
    ByteBuffer src=this.src;
    if (src == null) {
     src = copy.buf;
     src.flip();
    } else this.src = null;
    this.old = ParallelDeflate.deflate(def, crc, zip, src, this.old , true, false, en);
   } else writeEntryModify(en);
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
 public File outFile;
 public ZipEntryM entry;
 public int flag=1;
 public CharsetEncoder charsetEncoder;
 public ByteBuffer tbuf;
 public CharsetEncoder utf8=StandardCharsets.UTF_8.newEncoder();
 public ZipEntryOutput(File out) throws FileNotFoundException {
  //文件模式需要支持并发写出，鸽了。
  this(out, 16384, null);
 }
 public ZipEntryOutput(File file, int size, CharsetEncoder utf) throws FileNotFoundException {
  this(new RandomAccessFile(file, "rw").getChannel(), size, utf);
  outFile = file;
 }
 public ZipEntryOutput(WritableByteChannel wt) {
  this(wt, 16384, null);
 }
 public ZipEntryOutput(WritableByteChannel wt, int size, CharsetEncoder utf) {
  super(wt, size);
  charsetEncoder = utf;
  ByteBuffer buf=ByteBuffer.allocateDirect(16);
  buf.order(ByteOrder.LITTLE_ENDIAN);
  tbuf = buf;
 }
 public int pos;
 public ByteBuffer getBuf() {
  ByteBuffer buf= this.buf;
  pos = buf.position();
  return buf;
 }
 public void end() {
  int set=buf.position();
  upLength(set - pos);
  pos = set;
 }
 public ByteBuffer getBufFlush() throws IOException {
  ByteBuffer buf=this.buf;
  upLength(buf.position() - pos);
  super.getBufFlush();
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
  File out=outFile;
  if (out != null) {
   out.delete();
   out = null;
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
  if (list.size() == 0)
   headOff += i;
  off += i;
 }
 public void write(int b) {
  throw new RuntimeException();
 }
 public void write(byte[] b, int off, int len) throws IOException {
  super.write(ByteBuffer.wrap(b, off, len));
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
    if ((flag & AsInput) > 0 && outFile == null) {
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
 public void writeEntryModify(ZipEntryM ze) {
  if ((flag & AsInput) == 0 || ze.notFix || ze.size <= 0)return;
  writeEntryModify(ze, null, size(), buf);
 }
 public void writeEntryModify(ZipEntryM zip, ByteBuffer mmap, long pos, ByteBuffer buf) {
  long start=zip.start + 14;
  ByteBuffer buff;
  int cpos=buf.position();
  int off=(int)(pos - start);
  if (start >= pos) {
   buff = buf;
   buff.position(off);
  } else {
   if (mmap == null)return;
   if (start + 12 >= pos) {
    buff = tbuf;
   } else buff = mmap;
   mmap.position((int)start);
  }
  buff.putInt(zip.size);
  buff.putInt(zip.csize);
  buff.putInt(zip.crc);
  if (mmap != null && buff != buf) {
   buff.rewind();
   buff.limit(off);
   mmap.put(buf);
   buff.limit(12);
   buf.rewind();
   buf.put(buff);
   buff = buf; 
  } else zip.notFix = true;
  buff.clear();
  buf.position(cpos);
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
  ByteBuffer tbuf=this.tbuf;
  ByteBuffer buff=buf.remaining() < 16 ?tbuf: buf;
  buff.putInt(0x08074b50);
  buff.putInt(zip.crc);
  buff.putInt(zip.csize);
  buff.putInt(size);
  releaseBuf(buff, 16);
  tbuf.clear();
 }
 public void finish() throws IOException {
  closeEntry();
  outDef.free();
  int flag=this.flag;
  if ((flag & AsInput) > 0 && outFile != null) {
   FileChannel nio=(FileChannel)wt;
   long off=0;
   long fileSize=size();
   ByteBuffer next=null;
   ByteBuffer map=null;
   int PAGESIZE=1024 * 1024 * 512;
   for (ZipEntryM ze:list) {
    if (ze.notFix || ze.size <= 0)continue;
    long start=ze.start + 14;
    if (start > off) {
     if (next instanceof MappedByteBuffer)map = next;
     else map =  nio.map(FileChannel.MapMode.READ_WRITE, off, Math.min(PAGESIZE, fileSize - off));
     off += PAGESIZE;
     map.order(ByteOrder.LITTLE_ENDIAN);
     next =  off > fileSize ?buf: nio.map(FileChannel.MapMode.READ_WRITE, off, Math.min(PAGESIZE, fileSize - off));
    }
    writeEntryModify(ze, map, Math.min(fileSize, off), next);
   }
  }
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
