package org.libDeflate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;
import me.steinborn.libdeflate.LibdeflateDecompressor;
import me.steinborn.libdeflate.ObjectPool;
import java.nio.MappedByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

public class zipFile implements AutoCloseable {
 public FileChannel rnio;
 public ByteBuffer mmap;
 public Charset encode;
 public zipFile(File f) throws IOException {
  this(f.toPath(), StandardCharsets.UTF_8);
 }
 public zipFile(Path path, Charset set) throws IOException {
  encode = set;
  rnio = FileChannel.open(path, new StandardOpenOption[]{StandardOpenOption.READ});
  if (RC.zip_read_mmap) {
   MappedByteBuffer buf= rnio.map(FileChannel.MapMode.READ_ONLY, 0, rnio.size());
   buf.order(ByteOrder.LITTLE_ENDIAN);
   mmap = buf;
  }
  //多次分配有坏处
  setEntiys();
 }
 public static void readFullyAt(FileChannel fc, ByteBuffer buf, int off, int end, long pos) throws IOException {
  buf.position(off);
  buf.limit(end);
  fc.read(buf, pos);
  buf.clear();
 }
 public HashMap<String,zipEntry> ens;
 public long getPos(zipEntry ze) throws IOException {
  long g=ze.start;
  if (g < 0) {
   g = g & 0x7fffffffffffffffl;
   ByteBuffer buf= readBlock(g, 4);
   g += buf.getShort(0) & 0xffff;
   g += buf.getShort(2) & 0xffff;
   ze.start = g += 4;
  }
  return g;
 }
 public ByteBuffer readBlock(long pos, int len) throws IOException {
  if (RC.zip_read_mmap) {
   int bufpos=(int)pos;
   ByteBuffer nbuf;
   ByteBuffer mmap=this.mmap;
   synchronized (this) {
    mmap.limit(bufpos + len);
    mmap.position(bufpos);
    nbuf = mmap.slice();
   }
   //切片不传递大小端
   nbuf.order(ByteOrder.LITTLE_ENDIAN);
   return nbuf;
  }
  ByteBuffer buf=RC.newDbuf(len);
  buf.order(ByteOrder.LITTLE_ENDIAN);
  rnio.read(buf, pos);
  buf.flip();
  return buf;
 }
 public ByteBuffer getBuf(zipEntry ze) throws IOException {
  int size=(int)ze.csize;
  long pos=getPos(ze);
  return readBlock(pos, size);
 }
 public ReadableByteChannel open(final zipEntry en) throws IOException {
  if (en.mode <= 0)return openBlock(en);
  return open(getBuf(en));
 }
 public void close() throws IOException {
  ObjectPool.inflateGc();
  if (RC.zip_zlib)InflatePool.inflateGc();
  rnio.close();
 }
 public static ReadableByteChannel open(final ByteBuffer buf) {
  final LibdeflateDecompressor inflate=ObjectPool.allocInfalte(0);
  return new ReadableByteChannel(){
   public void close() {
    ObjectPool.free(inflate);
   }
   public boolean isOpen() {
    return true;
   }
   public int read(ByteBuffer dst) throws IOException {
    if (!buf.hasRemaining())return -1;
    if (!dst.hasRemaining())return 0;
    return inflate.decompress(buf, dst);
   }
  };
 }
 public ReadableByteChannel openBlock(zipEntry ze) throws IOException {
  return new FileBlockChannel(rnio, getPos(ze), ze.csize);
 }
 public static InputStream warp(final ReadableByteChannel ch) {
  return new InputStream(){
   public int read() {
    //读取一个b违背性能要求请不要这么做
    return 0;
   }
   public int read(byte b[], int off, int len) throws IOException {
    return ch.read(ByteBuffer.wrap(b, off, len));
   }
   public void close() throws IOException {
    ch.close();
   }
  };
 }
 public InputStream openStream(zipEntry ze) throws IOException {
  return warp(openBlock(ze));
 }
 public InputStream openEntry(final zipEntry ze) throws IOException {
  final InputStream input= openStream(ze);
  if (ze.mode <= 0)return input;
  final Inflater Inflater= InflatePool.allocInfalte();
  return new InflaterInputStream(input, Inflater, (int)Math.min(ze.size, 65536l)){
   public void close() {
    InflatePool.free(Inflater);
   }
  };
 }
 public void setEntiys() throws IOException {
  int centot=0;
  long cenlen=0;
  long cenpos=0;
  long headoff=0;
  long ziplen=rnio.size();
  long minHDR = Math.max(ziplen - 65557, 0);
  long minPos = minHDR - 106;
  ByteBuffer buf;
  tag: {
   if (!RC.zip_read_mmap) {
    buf = ByteBuffer.allocate(132);
    buf.order(ByteOrder.LITTLE_ENDIAN);
    for (long pos = ziplen - 128; pos >= minPos; pos -= 106) {
     int off = pos < 0 ?(int)-pos: 0;
     buf.position(off);
     readFullyAt(rnio, buf, off, 128, pos + off);
     for (int i = 106; i >= off; i--) {
      if (buf.getInt(i) == 0x06054b50) {
       centot = buf.getShort(i + 10) & 0xffff;
       cenlen = buf.getInt(i + 12) & 0xffffffffL;
       long cenoff = buf.getInt(i + 16) & 0xffffffffL;
       long nowpos=pos + i;
       cenpos = nowpos - cenlen;
       headoff = cenpos - cenoff;
       int comlen = buf.getShort(i + 20) & 0xffff;
       if (nowpos + 22 + comlen != ziplen) {
        if (cenpos < 0 || headoff < 0)continue;
        readFullyAt(rnio, buf, 128, 132, cenpos);
        if (buf.getInt(128) != 0x02014b50)continue;
        readFullyAt(rnio, buf, 128, 132, headoff);
        if (buf.getInt(128) != 0x04034b50)continue;
       }
       if (cenlen == 0xffffffffL || cenoff == 0xffffffffL || centot == 0xffff) {
        readFullyAt(rnio, buf, 0, 16, nowpos - 20);
        if (buf.getInt(0) == 0x07064b50) {
         int nextpos = (int) buf.getLong(8);
         readFullyAt(rnio, buf, 0, 56, nextpos);
         if (buf.getInt(0) == 0x06064b50) {
          centot = (int)buf.getLong(32);
          cenlen = buf.getLong(40);
          cenoff = buf.getLong(48);
          cenpos = nextpos - cenlen;
          headoff = cenpos - cenoff;
         }
        }
       }
       break tag;
      }
     }
    }
   } else {
    buf = mmap;
    for (int pos =(int)ziplen - 22; pos >= minPos; --pos) {
     //readFullyAt(rnio, buf, off, 128, pos + off);
     if (buf.getInt(pos) == 0x06054b50) {
      centot = buf.getShort(pos + 10) & 0xffff;
      cenlen = buf.getInt(pos + 12) & 0xffffffffL;
      long cenoff = buf.getInt(pos + 16) & 0xffffffffL;
      cenpos = pos - cenlen;
      headoff = cenpos - cenoff;
      int comlen = buf.getShort(pos + 20) & 0xffff;
      if (pos + 22 + comlen != ziplen) {
       if (cenpos < 0 || headoff < 0)continue;
       //readFullyAt(rnio, buf, 128, 132, cenpos);
       if (buf.getInt((int)cenpos) != 0x02014b50)continue;
       //readFullyAt(rnio, buf, 128, 132, headoff);
       if (buf.getInt((int)headoff) != 0x04034b50)continue;
      }
      if (cenlen == 0xffffffffL || cenoff == 0xffffffffL || centot == 0xffff) {
       //readFullyAt(rnio, buf, 0, 16, nowpos - 20);
       if (buf.getInt(pos - 20) == 0x07064b50) {
        int nextpos = (int) buf.getLong(pos - 12);
        //readFullyAt(rnio, buf, 0, 56, nextpos);
        if (buf.getInt(nextpos) == 0x06064b50) {
         centot = (int)buf.getLong(nextpos + 32);
         cenlen = buf.getLong(nextpos + 40);
         cenoff = buf.getLong(nextpos + 48);
         cenpos = nextpos - cenlen;
         headoff = cenpos - cenoff;
        }
       }
      }
      break tag;
     }
    }
   }
   throw new ZipException();
  }
  int off;
  if (!RC.zip_read_mmap) {
   buf = readBlock(cenpos, (int)cenlen);
   off = 0;
  } else {
   off = (int)cenpos;
   cenlen += cenpos;
  }
  HashMap map=new HashMap(centot << 2 / 3);
  this.ens = map;
  CharsetDecoder utf8=ZipUtil.decode(StandardCharsets.UTF_8);
  CharsetDecoder encode=ZipUtil.decode(this.encode);
  cenlen -= 46;
  while (off <= cenlen) {
   off += 8;
   boolean isutf8=(buf.getShort(off) & 2048) > 0;
   off += 2;
   byte modtype=buf.get(off);
   off += 6;
   long csize=buf.getInt(off += 4) & 0xffffffffL;
   long ucsize=buf.getInt(off += 4) & 0xffffffffL; 
   if (csize == 0 || modtype == 0) {
    modtype = 0;
    csize = ucsize;
   }
   off += 4;
   int namelen=buf.getShort(off) & 0xffff;
   int exlen=buf.getShort(off += 2) & 0xffff;
   int cmlen=buf.getShort(off += 2) & 0xffff;
   off += 2;
   long zpos=buf.getInt(off += 8) & 0xffffffffL;
   off += 4;
   buf.position(off);
   buf.limit(off + namelen);
   String name=(isutf8 ?utf8: encode).decode(buf).toString();
   buf.clear();
   off += namelen;
   int zip64=off;
   int exoff=exlen + off;
   while (zip64 + 4 < exoff) {
    short ztag=buf.getShort(zip64);
    int sz=buf.getShort(zip64 + 2) & 0xffff;
    zip64 += 4;
    if (zip64 + sz > exoff)break;
    if (ztag == 0x0001) {
     if (sz < 8)break;
     if (ucsize == 0xffffffff)
      ucsize = buf.getLong(zip64);
     zip64 += 8;
     sz -= 8;
     if (sz < 8)break;
     if (csize == 0xffffffff)
      csize = buf.getLong(zip64);
     zip64 += 8;
     sz -= 8;
     if (sz < 8)break;
     if (zpos == 0xffffffffL)
      zpos = buf.getLong(zip64);
    }
   }
   off += exlen;
   off += cmlen;
   zipEntry ze=new zipEntry();
   ze.size = ucsize;
   ze.csize = csize;
   ze.mode = modtype;
   ze.name = name;
   ze.start = (zpos + headoff + 26l) | 0x8000000000000000l;
   map.put(name, ze);
  }
 }
}
