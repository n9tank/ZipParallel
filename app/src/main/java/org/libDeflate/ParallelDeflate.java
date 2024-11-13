package org.libDeflate;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.InflaterInputStream;
import me.steinborn.libdeflate.LibdeflateCRC32;
import me.steinborn.libdeflate.LibdeflateCompressor;
import me.steinborn.libdeflate.LibdeflateDecompressor;
import me.steinborn.libdeflate.LibdeflateJavaUtils;

public class ParallelDeflate implements AutoCloseable,Canceler {
 public class DeflateWriter implements Callable {
  public InputGet in;
  public boolean raw;
  public IoWriter io;
  public ByteBuffer outbuf;
  public ZipEntryM zip;
  public File fc;
  public DeflateWriter(InputGet input, ZipEntryM ze, boolean raw) {
   in = input;
   zip = ze;
   this.raw = raw;
  }
  public DeflateWriter(IoWriter out, ZipEntryM ze) {
   io = out;
   zip = ze;
  }
  public DeflateWriter(File out, ZipEntryM ze) {
   fc = out;
   zip = ze;
  }
  public void join() throws Exception {
   ZipEntryOutput out=zipout;
   ByteBuffer outf=outbuf;
   if (outf != null) {
    out.putEntry(zip, true);
    out.write(outf);
    return;
   }
   IoWriter wt=io;
   if (wt != null) {
    write(wt, true, zip);
    return;
   }
   File fc=this.fc;
   if (fc != null)addFile(fc, true, zip);
   InputStream input=in.io();
   if (raw)copyToZip(input, zip);
   else deflate(input, true, zip);
  }
  public Object call() throws Exception {
   boolean wroking=false;
   try {
    if (wroking = !wrok.getAndSet(true))
     join();
    else if (outbuf == null) {
     ZipEntryM zip=this.zip;
     int mode=zip.mode;
     if (!raw && mode > 0) {
      IoWriter wt=io;
      if (wt != null)outbuf = write(wt, false, zip);
      else {
       InputGet ing=this.in;
       if (ing != null)
        outbuf = deflate(ing.io(), false, zip);
       else outbuf = addFile(fc, false, zip);
      }
      wroking = outbuf == null;
     }
     if (!wroking)list.offer(this);
    }
   } catch (Exception e) {
    on.onError(e);
   }
   if (wroking)clearList();
   on.pop();
   return null;
  }
 }
 public void clearList() {
  ConcurrentLinkedQueue<DeflateWriter> list=this.list;
  if (list != null) {
   DeflateWriter def;
   while ((def = list.poll()) != null) {
    try {
     def.join();
    } catch (Exception e) {
     on.onError(e);
    }
   }
  }
  wrok.set(false);
 }
 public void end() {
  clearList();
  try {
   zipout.close();
  } catch (Exception e) {
   on.onError(e);
  }
 }
 public void close() throws IOException {
  if (async)on.pop();
  else zipout.close();
 }
 public void cancel() {
  if (!on.cancel())return;
  zipout.cancel();
  return;
 }
 public ByteBuffer tryWrite(ByteBuffer src, ZipEntryM ze) throws IOException {
  if (src == null || wrok.getAndSet(true))return src;
  ZipEntryOutput zipout=this.zipout;
  zipout.putEntry(ze, true);
  zipout.write(src);
  return null;
 }
 public ByteBuffer deflate(ByteBuffer src, boolean wrok, ZipEntryM ze) throws IOException {
  src.flip();
  LibdeflateCompressor def = new LibdeflateCompressor(ze.mode, 0);
  ByteBuffer buf;
  LibdeflateCRC32 crc;
  if (!ze.notFix)crc = new LibdeflateCRC32();
  else crc = null;
  try {
   buf = deflate(def, crc, zipout, src, null, wrok, true, ze);
  } finally {
   def.close();
  }
  return buf;
 }
 public static ByteBuffer deflate(LibdeflateCompressor def, LibdeflateCRC32 crc, ZipEntryOutput out, ByteBuffer src, ByteBuffer old, boolean wrok, boolean is, ZipEntryM ze) throws IOException {
  int readlen=src.remaining();
  ze.size = readlen;
  int pos=src.position();
  if (crc != null) {
   crc.update(src);
   src.position(pos);
   ze.crc = (int)crc.getValue();
  }
  if (wrok && is)out.putEntry(ze, true);
  boolean has;
  ByteBuffer buf=out.buf;
  int size=LibdeflateJavaUtils.getBufSize(readlen, 0);
  if (has = (!wrok || buf.remaining() < size)) {
   if (!wrok)ze.notFix = true;
   buf = old == null || old.capacity() < size ?old = ByteBuffer.allocateDirect(is ?size: BufOutput.tableSizeFor(size)): old;
  }
  int outlen=def.compress(src, buf);
  ze.csize = outlen;
  src.clear();
  if (has)buf.flip();
  if (wrok) {
   if (has) {
    out.writeEntryModify(ze);
    out.write(buf);
    buf.clear();
   } else {
    out.upLength(outlen);
    out.writeEntryModify(ze);
   }
  }
  return is ? wrok ?null: buf: old;
 }
 public final static ExecutorService pool=new ForkJoinPool();
 public ConcurrentLinkedQueue list;
 public AtomicBoolean wrok;
 public ZipEntryOutput zipout;
 public boolean async;
 public ErrorHandler on;
 public ParallelDeflate(ZipEntryOutput out, boolean async) {
  zipout = out;
  if (async) {
   list = new ConcurrentLinkedQueue();
   wrok = new AtomicBoolean();
   this.async = async;
  }
 }
 public ByteBuffer write(IoWriter io, boolean iswrok, ZipEntryM zip) throws Exception {
  BufOutput buf;
  if (io.out == null)
   io.out = buf = new BufOutput(io.bufSize);
  else {
   buf = null;
   zipout.putEntry(zip, true);
  }
  io.flush();
  if (buf != null) {
   ByteBuffer src=buf.buf;
   src = deflate(src, iswrok, zip);
   return tryWrite(src, zip);
  }
  return null;
 }
 public void with(IoWriter io, ZipEntryM zip) throws Exception {
  ZipEntryOutput zipout=this.zipout;
  if (zip.mode <= 0 || !async)
   io.out = zipout.outDef;
  if (!async) {
   zipout.putEntry(zip);
   io.flush();
  } else on.add(new DeflateWriter(io, zip));
 }
 public void writeToZip(File file, ZipEntryM zip) throws IOException {
  if (!async)
   addFile(file, true, zip);
  else
   on.add(new DeflateWriter(file, zip));
 }
 public ByteBuffer addFile(File file, boolean working, ZipEntryM zip) throws IOException {
  FileChannel nio=new FileInputStream(file).getChannel();
  try {
   long size=nio.size();
   if (zip.mode > 0) {
    ByteBuffer src = deflate(nio.map(FileChannel.MapMode.READ_ONLY, 0, size), working, zip);
    nio.close();
    return tryWrite(src, zip);
   } else {
    ZipEntryOutput data=zipout;
    WritableByteChannel wt;
    if (zip.notFix)wt = data.getNio();
    else wt = data.outDef;
    nio.transferTo(0, size, wt);
    if (!(wt instanceof ZipEntryOutput.DeflaterIo))
     data.upLength(size);
   }
  } finally {
   nio.close();
  }
  return null;
 }
 public static int readLoop(InputStream in, byte arr[]) throws IOException {
  int i=0;
  int len=arr.length;
  while (i < len) {
   int n=in.read(arr, i, len - i);
   if (n < 0)return i;
   i += n;
  }
  return i;
 }
 public static ByteBuffer inflate(InputStream src, int unsize) throws Exception {
  InputStream in=ZipEntryInput.getRaw(src);
  ByteBuffer buf;
  try {
   buf = ByteBuffer.allocate(in.available());
   in.read(buf.array());
  } finally {
   in.close();
  }
  ByteBuffer drc=ByteBuffer.allocateDirect(unsize);
  LibdeflateDecompressor def=new LibdeflateDecompressor(0);
  try {
   while (buf.hasRemaining())
    def.decompress(buf, drc);
  } finally {
   def.close();
  }
  return drc;
 }
 //如果要使用自定义的InflaterInputStream，需要available返回解压大小只支持Deflate格式
 public ByteBuffer deflate(InputStream in, boolean wroking, ZipEntryM zip) throws Exception {
  try {
   int len=in.available();
   ByteBuffer outbuf;
   if (in instanceof InflaterInputStream)
    outbuf = inflate(in, len);
   else {
    outbuf = ByteBuffer.allocate(len);
    outbuf.position(readLoop(in, outbuf.array()));
   }
   in.close();
   ByteBuffer src=deflate(outbuf, wroking, zip);
   return tryWrite(src, zip);
  } finally {
   in.close();
  }
 }
 public byte[] copybuf;
 public byte[] getBuf() {
  byte buf[]=copybuf;
  int cy=zipout.buf.capacity();
  if (buf == null || buf.length < cy)
   copybuf = buf = new byte[cy];
  return buf;
 }
 public void copyIo(InputStream in, ZipEntryM zip) throws IOException {
  ZipEntryOutput out=zipout;
  byte buf[]=getBuf();
  int i;
  LibdeflateCRC32 crc=!zip.notFix ?new LibdeflateCRC32(): null;
  try {
   while ((i = readLoop(in, buf)) > 0) {
    if (crc != null)crc.update(buf, 0, i);
    out.write(buf, 0, i);
   }
  } finally {
   in.close();
  }
  fixEntry(out, crc, zip);
 }
 public static void fixEntry(ZipEntryOutput out, LibdeflateCRC32 crc, ZipEntryM zip) {
  int size = (int)(out.off - out.last);
  if (zip.mode <= 0) {
   if (crc != null)zip.crc = (int)crc.getValue();
   zip.size = size;
  }
  zip.csize = size;
 }
 public void copyToZip(InputStream in, ZipEntryM zip) throws IOException {
  zipout.putEntry(zip, true);
  copyIo(in, zip);
 }
 public void copyToZip(InputGet ing, ZipEntryM zip) throws IOException {
  on.add(new DeflateWriter(ing, zip, true));
 }
 public void writeToZip(InputStream in, ZipEntryM zip) throws Exception {
  ZipEntryOutput out=zipout;
  boolean def=zip.mode <= 0;
  out.putEntry(zip, def);
  if (def)copyIo(in, zip);
  else deflate(in, true, zip);
 }
 public void writeToZip(InputGet ing, ZipEntryM zip) throws IOException {
  on.add(new DeflateWriter(ing, zip, zip.mode <= 0));
 }
}
