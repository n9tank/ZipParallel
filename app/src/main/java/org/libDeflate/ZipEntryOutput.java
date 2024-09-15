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
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import me.steinborn.libdeflate.LibdeflateCRC32;
import me.steinborn.libdeflate.LibdeflateJavaUtils;


public class ZipEntryOutput extends OutputStream implements WritableByteChannel {
 public class DeflaterIo extends OutputStream implements WritableByteChannel {
  public libDeflate def;
  public int lvl;
  public ByteBuffer buf;
  public LibdeflateCRC32 crc;
  public void write(int b) {
   throw new RuntimeException();
  }
  public boolean isOpen() {
   return true;
  }
  public void setBufSize(int size) {
   buf = ByteBuffer.allocateDirect(LibdeflateJavaUtils.getBufSize(size, false));
  }
  public void putEntry(ZipEntryM zip) {
   int mode=zip.mode;
   if (zip.notFix)crc = null;
   else crc = new LibdeflateCRC32();
   if (mode > 0) {
    if (buf == null)buf = ByteBuffer.allocateDirect(8205);
    if (mode != lvl) {
     lvl = mode;
     this.def = def = new libDeflate(mode);
    }
   }
  }
  public int write(ByteBuffer src) throws IOException {
   ZipEntryOutput out=ZipEntryOutput.this;
   return ParallelDeflate.deflate(src, buf, def, crc, out);
  }
  public void write(byte[] b, int off, int len) throws IOException {
   write(ByteBuffer.wrap(b, off, len));
  }
  public void close() {
   libDeflate def=this.def;
   if (def != null) {
    this.def = null;
    def.close();
   }
  }
 }
 public boolean pk78;
 public byte[] buf=new byte[8192];
 public long last;
 public DeflaterIo outDef=new DeflaterIo();
 public List<ZipEntryM> list;
 public long off;
 public long headOff;
 public ByteBufIo outBuf;
 public File outFile;
 public FileChannel rnio;
 public ZipEntryM entry;
 public boolean AsInput=true;
 public boolean onlyInput;
 public Charset charset;
 public CharsetEncoder charsetEncoder;
 public ZipEntryOutput(OutputStream output) {
  outBuf = new ByteBufIo(output, 8192);
  list = new LinkedList();
  setCharset(StandardCharsets.UTF_8);
 }
 public ZipEntryOutput(File file) throws FileNotFoundException {
  FileChannel wt = new FileOutputStream(outFile = file).getChannel();
  rnio = wt;
  outBuf = new ByteBufIo(wt, 8192);
  list = new LinkedList();
  setCharset(StandardCharsets.UTF_8);
 }
 public void cancel() {
  File out=outFile;
  if (out != null) {
   out.delete();
   out = null;
  } 
  list = null;
  outBuf.buf = null;
  try {
   close();
  } catch (Exception e) {
  }
 }
 public boolean isOpen() {
  return true;
 }
 public WritableByteChannel getNio() throws IOException {
  ByteBufIo buf=outBuf;
  WritableByteChannel wt = buf.wt;
  if (wt != null)buf.flush();
  else wt = buf;
  return wt;
 }
 public void setCharset(Charset set) {
  charset = set;
  charsetEncoder = set.newEncoder();
 }
 public void upLength(long i) {
  if (list.size() == 0) {
   headOff += i;
  }
  off += i;
 }
 public void write(int b) throws IOException {
  throw new RuntimeException();
 }
 public void write(byte[] b, int off, int len) throws IOException {
  outBuf.write(b, off, len);
  upLength(len);
 }
 public int write(ByteBuffer src) throws IOException {
  int i=outBuf.write(src);
  upLength(i);
  return i;
 }
 public void closeEntry() throws IOException {
  ZipEntryM ze=entry;
  if (ze != null) {
   ZipEntryOutput.DeflaterIo defo=outDef;
   if (ze.size < 0) {
    LibdeflateCRC32 crc=defo.crc;
    if (ze.mode > 0) {
     libDeflate def=defo.def;
     if (def != null)ParallelDeflate.fixEntry(def, crc, ze);
    } else ParallelDeflate.fixEntry(this, crc, ze);
    if (AsInput && rnio == null) {
     writeEntryFix(ze);
    }
   }
   defo.close();
   entry = null;
  }
 }
 public void putEntry(ZipEntryM zip) throws IOException {
  putEntry(zip, false);
 }
 public void putEntry(ZipEntryM zip, boolean raw) throws IOException {
  closeEntry();
  if (!raw)outDef.putEntry(zip);
  entry = zip; 
  zip.start = off;
  if (!zip.onlyInput)list.add(zip);
  writeEntry(zip);
  last = off;
 }
 public void writeEntryModify(ZipEntryM zip) throws IOException {
  int size=zip.size;
  if (zip.notFix || size <= 0)return;
  FileChannel nio=rnio;
  nio.position(zip.start + 14);
  ByteBuffer buff=ByteBuffer.allocateDirect(12);
  buff.order(ByteOrder.LITTLE_ENDIAN);
  buff.putInt(zip.crc);
  buff.putInt(zip.csize);
  buff.putInt(size);
  buff.flip();
  nio.write(buff);
 }
 public void writeEntry(ZipEntryM zip) throws IOException {
  boolean utf8;
  ByteBuffer nameByte;
  if (AsInput) {
   String str=zip.name;
   utf8 = !charsetEncoder.canEncode(str);
   nameByte = (utf8 ?StandardCharsets.UTF_8: charset).encode(str);
  } else {
   utf8 = false;
   nameByte = ByteBuffer.allocate(0);
  }
  ByteBuffer buff=ByteBuffer.allocateDirect(30);
  buff.order(ByteOrder.LITTLE_ENDIAN);
  buff.putInt(0x04034b50);
  putBits(buff, utf8, AsInput, zip);
  int size=nameByte.limit();
  buff.putShort((short)size);
  buff.position(30);
  buff.flip();
  write(buff);
  write(nameByte);
 }
 public short globalBit(boolean data, boolean utf8) {
  short bit=0;
  if (data)bit |= 8;
  if (utf8)bit |= 2048;
  return bit;
 }
 public void writeEntryFix(ZipEntryM zip) throws IOException {
  int size=zip.size;
  if (zip.notFix || size <= 0)return;
  ByteBuffer buff=ByteBuffer.allocateDirect(16);
  buff.order(ByteOrder.LITTLE_ENDIAN);
  buff.putInt(0x08074b50);
  buff.putInt(zip.crc);
  buff.putInt(zip.csize);
  buff.putInt(size);
  buff.flip();
  write(buff);
 }
 public void flush() throws IOException {
  outBuf.flush();
 }
 public void finish(ZipEntryM[] badlist) throws IOException {
  closeEntry();
  FileChannel nio=rnio;
  if (AsInput && nio != null) {
   flush();
   for (ZipEntryM ze:list) {
    writeEntryModify(ze);
   }
   nio.position(nio.size());
  }
  if (!onlyInput) {
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
    out.close();
    outDef = null;
    outBuf.close();
   }
  }
 }
 public void putBits(ByteBuffer buff, boolean utf8, boolean need, ZipEntryM zip) {
  short mode=zip.mode;
  buff.putShort((short)0);//ver 该值没有任何作用
  boolean data;
  buff.putShort(globalBit(data = (!zip.notFix && AsInput && rnio == null), utf8));
  pk78 |= data;
  buff.putShort((short)(need && mode <= 0 ?0: 8));
  buff.putInt(zip.xdostime);
  buff.putInt(zip.crc);
  buff.putInt(zip.csize);
  buff.putInt(zip.size);
 }
 public void writeEntryEnd(ZipEntryM zip) throws IOException {
  String str=zip.name;
  boolean utf8=!charsetEncoder.canEncode(str);
  ByteBuffer nameByte = (utf8 ?StandardCharsets.UTF_8: charset).encode(str);
  ByteBuffer buff=ByteBuffer.allocateDirect(46);
  buff.order(ByteOrder.LITTLE_ENDIAN);
  buff.putInt(0x02014b50);
  buff.putShort((short)0);
  putBits(buff, utf8, true, zip);
  int size=nameByte.limit();
  buff.putShort(28, (short)size);
  buff.position(42);
  buff.putInt((int)(zip.start - headOff));
  buff.flip();
  write(buff);
  write(nameByte);
 }
 public void writeEnd(int size)throws IOException {
  ByteBuffer buff= ByteBuffer.allocateDirect(pk78 ?24: 22);
  buff.order(ByteOrder.LITTLE_ENDIAN);
  buff.putInt(0X06054B50);
  buff.position(8);
  short num=(short)list.size();
  buff.putShort(num);
  buff.putShort(num);
  buff.putInt(size);
  buff.putInt((int)(off - size - headOff));
  buff.position(buff.limit());
  buff.flip();
  write(buff);
 }
}
