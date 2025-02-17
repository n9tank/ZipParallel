package org.libDeflate;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import me.steinborn.libdeflate.*;

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
  public DeflateWriter(ByteBuffer buf, ZipEntryM ze) {
   outbuf = buf;
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
    if (RC.sawp_ram)out.swap(outf);
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
    else {
     ByteBuffer outbuf=this.outbuf;
     ZipEntryM zip=this.zip;
     tag:
     if (outbuf == null && zip.mode > 0) {
      IoWriter wt=io;
      if (!RC.zip_addFile || wt != null) {
       if (wt.out != null)
        break tag;
       outbuf = write(wt, false, zip);
      } else
       outbuf = addFile(fc, false, zip);
      this.outbuf = outbuf;
     }
     if (wroking = !wrok.getAndSet(true))
      join();
     else {
	  list.offer(this);
	  wroking = !wrok.getAndSet(true);
	 }
    }
   } catch (Throwable e) {
    on.onError(e);
   }
   if (wroking)
    clearList();
   on.pop();
   return null;
  }
 }
 public void clearList() {
  ConcurrentLinkedQueue<DeflateWriter> list=this.list;
  AtomicBoolean wrok=this.wrok;
  do{
   DeflateWriter def;
   while ((def = list.poll()) != null) {
	try {
	 def.join();
	} catch (Throwable e) {
	 on.onError(e);
	}
   }
   wrok.set(false);
  }while(!list.isEmpty() && !wrok.getAndSet(true));
 }
 public void end() {
  try {
   zipout.close();
  } catch (Exception e) {
   on.onError(e);
  }
  ObjectPool.deflateGc();
 }
 public void close() {
  if (!RC.zip_close_async) {
   on.pop();
   return;
  }
  LongAdder io=on.io;
  io.decrement();
  if (io.sum() < 0) {
   pool.execute(new Runnable(){
     public void run() {
      if (!on.iscancel())
       end();
      on.onClose();
     }
    });
  }
 }
 public void cancel() {
  if (!on.cancel())return;
  ObjectPool.deflateGc();
  zipout.cancel();
  return;
 }
 public ByteBuffer deflate(ByteBuffer src, boolean wrok, ZipEntryM ze) throws IOException {
  LibdeflateCompressor def =ObjectPool.allocDeflate(ze.mode, 0);
  ByteBuffer buf;
  ZipEntryOutput zipout=this.zipout;
  int size=LibdeflateJavaUtils.getBufSize(src.remaining(), 0);
  ByteBuffer drc=zipout.buf;
  try {
   if (!wrok || drc.remaining() < size)
    drc = RC.newDbuf(size);
   buf = zipout.deflate(def, src, drc, wrok, ze);
   if (RC.sawp_ram && wrok && drc != zipout.buf)
    zipout.swap(drc);
  } finally {
   ObjectPool.free(def);
  }
  return buf;
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
  ObjectPool.deflateNum.increment();
 }
 public ByteBuffer write(IoWriter io, boolean iswrok, ZipEntryM zip) throws Exception {
  BufOutput buf;
  BufIo out=io.out;
  if (out == null)
   io.out = buf = new BufOutput(io.bufSize);
  else {
   buf = null;
   if (RC.zip_crc && RC.zip_deflate_io && out instanceof ZipEntryOutput.DeflaterIo)
    zipout.outDef.io = zipout;
  }
  if (iswrok)
   zipout.putEntry(zip);
  io.flush();
  if (buf != null) {
   ByteBuffer bytebuf=buf.buf;
   bytebuf.flip();
   if (!zipout.asInput() && bytebuf.limit() <= 4) {
    zip.mode = 0;
    if (iswrok)
     zipout.write(bytebuf);
    return bytebuf;
   }
   return deflate(bytebuf, iswrok, zip);
  }
  return null;
 }
 public void with(IoWriter io, ZipEntryM zip, boolean raw) throws Exception {
  if (zip.mode <= 0 || raw) {
   ZipEntryOutput zipout=this.zipout;
   io.out = !RC.zip_crc || !RC.zip_deflate_io || raw ?zipout: zipout.outDef;
  }
  on.add(new DeflateWriter(io, zip));
 }
 public void writeToZip(Path file, ZipEntryM zip) throws IOException {
  on.add(new DeflateWriter(file, zip));
 }
 public ByteBuffer addFile(Path file, boolean working, ZipEntryM zip) throws IOException {
  FileChannel nio=FileChannel.open(file, StandardOpenOption.READ);
  long size=nio.size();
  //经常mmap还是有比较高的代价的
  ZipEntryOutput data=zipout;
  if (working)data.putEntry(zip);
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
  zipEntry en=input.en;
  if (en.csize >= en.size || en.size <= 4)
   zip.mode = 0;
  raw = (raw && en.mode > 0 && zip.mode > 0) || (en.mode == 0 && zip.mode == 0);
  if (raw && RC.zip_read_mmap)
   on.add(new DeflateWriter(input.zip.getBuf(en), zip));
  else {
   if (RC.zip_read_mmap || !raw)
    input.bufSize = (int)en.size;
   with(input, zip, raw);
  }
 }
}
