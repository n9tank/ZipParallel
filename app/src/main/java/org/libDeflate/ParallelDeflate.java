package org.libDeflate;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import me.steinborn.libdeflate.LibdeflateCompressor;
import me.steinborn.libdeflate.LibdeflateJavaUtils;
import me.steinborn.libdeflate.ObjectPool;

public class ParallelDeflate implements AutoCloseable,Canceler {
 public class DeflateWriter implements Callable {
  public IoWriter io;
  public Path fc;
  public ByteBuffer outbuf;
  public ZipEntryM zip;
  public DeflateWriter(IoWriter out, ZipEntryM ze) {
   io = out;
   zip = ze;
  }
  public DeflateWriter(Path out, ZipEntryM ze) {
   fc = out;
   zip = ze;
  }
  public void join() throws Exception {
   ZipEntryOutput out=zipout;
   ByteBuffer outf=outbuf;
   if (outf != null) {
    out.putEntry(zip);
    out.write(outf);
    return;
   }
   IoWriter wt=io;
   if (!RC.zip_addFile || wt != null) {
    write(wt, true, zip);
    return;
   }
   addFile(fc, true, zip);
  }
  public Object call() throws Exception {
   boolean wroking=false;
   try {
    if (wroking = !wrok.getAndSet(true))
     join();
    else if (outbuf == null) {
     ZipEntryM zip=this.zip;
     int mode=zip.mode;
     tag:
     if (mode > 0) {
      IoWriter wt=io;
      if (!RC.zip_addFile || wt != null) {
       if (wt.out != null)
        break tag;
       outbuf = write(wt, false, zip);
      } else
       outbuf = addFile(fc, false, zip);
      wroking = outbuf == null;
     }
     if (!wroking)list.offer(this);
    }
   } catch (Throwable e) {
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
    } catch (Throwable e) {
     on.onError(e);
    }
   }
  }
  wrok.set(false);
 }
 public void end() {
  clearList();
  ObjectPool.inflateGc();
  ObjectPool.deflateGc();
  try {
   zipout.close();
  } catch (Exception e) {
   on.onError(e);
  }
 }
 public void close() {
  on.pop();
 }
 public void cancel() {
  if (!on.cancel())return;
  ObjectPool.inflateGc();
  ObjectPool.deflateGc();
  zipout.cancel();
  return;
 }
 public ByteBuffer tryWrite(ByteBuffer src, ZipEntryM ze) throws IOException {
  if (src == null || wrok.getAndSet(true))return src;
  ZipEntryOutput zipout=this.zipout;
  zipout.putEntry(ze);
  zipout.write(src);
  return null;
 }
 public ByteBuffer deflate(ByteBuffer src, boolean wrok, ZipEntryM ze) throws IOException {
  LibdeflateCompressor def =ObjectPool.allocDeflate(ze.mode, 0);
  ByteBuffer buf;
  try {
   buf = deflate(def, zipout, src, null, wrok, true, ze);
  } finally {
   ObjectPool.free(def);
  }
  buf = tryWrite(buf, ze);
  return buf;
 }
 public static ByteBuffer deflate(LibdeflateCompressor def, ZipEntryOutput out, ByteBuffer src, ByteBuffer old, boolean wrok, boolean is, ZipEntryM ze) throws IOException {
  int readlen=src.remaining();
  ze.size = readlen;
  int pos=src.position();
  if (RC.zip_crc && !ze.notFix)
   ze.crc(src, pos, readlen);
  if (wrok)out.putEntry(ze);
  ByteBuffer buf=out.buf;
  int size=LibdeflateJavaUtils.getBufSize(readlen, 0);
  if (!wrok || buf.remaining() < size) {
   if (!wrok)ze.notFix = true;
   buf = old == null || old.capacity() < size ?old = RC.newbuf(is ?size: BufOutput.tableSizeFor(size)): old;
  }
  int zpos=buf.position();
  int outlen=def.compress(src, buf);
  if ((out.flag & out.AsInput) == 0 && outlen >= readlen) {
   ze.mode = 0;
   src.position(pos);
   buf.position(zpos);
   buf = src;
   outlen = readlen;
  }
  ze.csize = outlen;
  if (wrok)out.releaseBuf(buf, outlen);
  //如果输入的buf允许偏移，这没有必要
  /*else{
   buf.limit(buf.position());
   buf.position(zpos);
   }*/
  else buf.flip();
  return is ? (wrok ?null: buf): old;
 }
 public final static int CPU=Runtime.getRuntime().availableProcessors();
 public final static ExecutorService pool=new ForkJoinPool(CPU + 1);
 public ConcurrentLinkedQueue list;
 public AtomicBoolean wrok;
 public ZipEntryOutput zipout;
 public ErrorHandler on;
 public ParallelDeflate(ZipEntryOutput out) {
  zipout = out;
  list = new ConcurrentLinkedQueue();
  wrok = new AtomicBoolean();
 }
 public ByteBuffer write(IoWriter io, boolean iswrok, ZipEntryM zip) throws Exception {
  BufOutput buf;
  BufIo out=io.out;
  if (out == null)
   io.out = buf = new BufOutput(io.bufSize);
  else {
   buf = null;
   zipout.putEntry(zip);
   if (RC.zip_crc && out instanceof ZipEntryOutput.DeflaterIo)
    zipout.outDef.io = zipout;
  }
  io.flush();
  if (buf != null) {
   ByteBuffer bytebuf=buf.buf;
   bytebuf.flip();
   return deflate(bytebuf, iswrok, zip);
  }
  return null;
 }
 public void with(IoWriter io, ZipEntryM zip, boolean raw) throws Exception {
  ZipEntryOutput zipout=this.zipout;
  if (zip.mode <= 0 || raw)
   io.out = !RC.zip_crc || raw ?zipout: zipout.outDef;
  on.add(new DeflateWriter(io, zip));
 }
 public void writeToZip(Path file, ZipEntryM zip) throws IOException {
  on.add(new DeflateWriter(file, zip));
 }
 public ByteBuffer addFile(Path file, boolean working, ZipEntryM zip) throws IOException {
  FileChannel nio=FileChannel.open(file, StandardOpenOption.READ);
  long size=nio.size();
  //经常mmap还是有比较高的代价的
  ByteBuffer mmap=RC.zip_read_mmap && (RC.MMAPSIZE <= 0 || size >= RC.MMAPSIZE) ?nio.map(FileChannel.MapMode.READ_ONLY, 0, size): null;
  try {
   if (zip.mode > 0) {
    if (mmap == null) {
     mmap = RC.newDbuf((int)size);
     nio.read(mmap);
     mmap.flip();
    }
    return deflate(mmap, working, zip);
   } else {
    ZipEntryOutput data=zipout;
    data.putEntry(zip);
    if (mmap != null) {
     ZipEntryM en= data.entry;
     if (RC.zip_crc && !en.notFix)
      en.crc(mmap, mmap.position(), mmap.limit());
     //不推荐在mmap模型下启用crc，无法保证操作系统何时进行换页
     data.write(mmap);
    } else data.copyFromAndCrc32(nio);
   }
  } finally {
   nio.close();
  }
  return null;
 }
 public void writeToZip(ZipInputGet input, ZipEntryM zip, boolean raw) throws Exception {
  with(input, zip, raw = (raw || (zip.mode <= 0 && (!RC.zip_crc || zip.notFix))));
  int size=(int)input.en.size;
  if (raw) {
   if (!RC.zip_read_mmap)size |= 0x80000000;
   else size = 0x80000000;
  }
  input.bufSize = size;
 }
 public static void fixEntry(ZipEntryOutput out, ZipEntryM zip) {
  int size = (int)(out.off - out.last);
  if (zip.mode <= 0)
   zip.size = size;
  zip.csize = size;
 }
}
