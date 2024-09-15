package org.libDeflate;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import me.steinborn.libdeflate.LibdeflateCRC32;

public class ParallelDeflate implements AutoCloseable {
 public class DeflateWriter implements Runnable,AutoCloseable {
  public InputStream in;
  public boolean raw;
  public IoWriter io;
  public FileOrBufOutput outflush;
  public ZipEntryM zip;
  public File fc;
  public DeflateWriter(InputStream input, ZipEntryM ze, boolean raw) {
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
   out.putEntry(zip, !(fc != null && zip.mode <= 0 && !zip.notFix));
   IoWriter wt=io;
   if (wt != null) {
    write(wt, true, zip);
    return;
   }
   InputStream input=in;
   if (raw)copyIo(input, zip);
   else {
    FileOrBufOutput outf=outflush;
    try {
     if (outf == null) {
      if (input != null)deflate(input, out, zip);
      else addFile(fc, out, zip);
     } else outf.writeTo(out);
    } finally {
     if (outf != null)outf.close();
    }
   }
  }
  public void run() {
   boolean wroking=false;
   boolean isheadWork=zip.onlyInput;
   try {
    if ((isheadWork || hlist == null) && (wroking = !wrok.getAndSet(true)))
     join();
    else if (outflush == null) {
     ZipEntryM zip=this.zip;
     int mode=zip.mode;
     IoWriter wt=io;
     if (wt == null) {
      if (!raw && mode > 0) {
       boolean is=in != null;
       int bufsize=(int)Math.min(8192, is ?(long)in.available(): fc.length());
       FileOrBufOutput cio=new FileOrBufOutput(bufsize);
       outflush = cio;
       if (is)wroking = deflate(in, cio, zip);
       else wroking = addFile(fc, cio, zip);
      }
     } else if (mode > 0) {
      io = null;
      wroking = (outflush = write(wt, false, zip)) == null;
     }
     boolean upwrok=wroking || ((isheadWork || hlist == null) && !wrok.getAndSet(true));
     if (upwrok) {
      if (!wroking)join();
      wroking = upwrok;
     } else (isheadWork ?hlist: list).push(this);
    }
   } catch (Exception e) {
    on.onError(e);
   }
   if (wroking)clearList(false);
   check(isheadWork);
  }
  public void close() throws Exception {
   AutoCloseable cs=in;
   if (cs != null)cs.close();
   cs = outflush;
   if (cs != null)cs.close();
  }
 }
 public static int deflate(ByteBuffer src, ByteBuffer buf, libDeflate def, LibdeflateCRC32 crc, WritableByteChannel out) throws IOException {
  int len=src.limit();
  if (crc != null) {
   crc.update(src);
   src.rewind();
  }
  if (def != null)def.compress(src, buf);
  else buf = src;
  out.write(buf);
  buf.clear();
  return len;
 }
 public void clearList(boolean close) {
  ConcurrentLinkedDeque obj=hlist;
  if (obj != null)clearList(obj, close);
  else clearList(list, close);
  wrok.set(false);
 }
 public void clearList(ConcurrentLinkedDeque<DeflateWriter> obj, boolean close) {
  if (obj == null)return;
  while (!obj.isEmpty()) {
   DeflateWriter def=obj.pop();
   try {
    if (close)def.close();
    else def.join();
   } catch (Exception e) {
    on.onError(e);
   }
  }
 }
 public void check(boolean headwrok) {
  boolean async=this.async;
  if (async) {
   LongAdder la=(headwrok ?hio: io);
   la.decrement();
   if (la.sum() >= 0)return;
   if (flist == null) {
    on.onClose();
    return;
   }
   clearList(hlist, false);
   hlist = null;
   clearList(list, false);
  }
  if (!async || (hlist == null && (!headwrok || io.sum() < 0))) {
   try {
    zipout.close();
   } catch (Exception e) {
    on.onError(e);
   }
   on.onClose();
  }
 }
 public void close() throws Exception {
  if (!async || flist != null) {
   check(false);
   on.lock();
  }
 }
 public boolean cancel() {
  List<Future> flist=this.flist;
  if (flist == null)return false;
  this.flist = null;
  for (Future fu:flist)
   fu.cancel(true);
  zipout.cancel();
  clearList(true);
  on.unlock();
  return true;
 }
 public void addTask(DeflateWriter run) throws IOException {
  if (flist == null)throw new IOException();
  (run.zip.onlyInput ?hio: io).increment();
  flist.add(pool.submit(run));
 }
 public static ExecutorService pool=Executors.newWorkStealingPool();
 public volatile Vector<Future> flist;
 public volatile ConcurrentLinkedDeque hlist;
 public ConcurrentLinkedDeque list;
 public AtomicBoolean wrok;
 public LongAdder io;
 public LongAdder hio;
 public ZipEntryOutput zipout;
 public boolean async;
 public ErrorHandler on;
 public void setHeadOffMode() {
  if (async) {
   hlist = new ConcurrentLinkedDeque();
   hio = new LongAdder();
  }
 }
 public ParallelDeflate(ZipEntryOutput out, boolean async) {
  zipout = out;
  if (async) {
   list = new ConcurrentLinkedDeque();
   flist = new Vector();
   wrok = new AtomicBoolean();
   this.async = async;
   io = new LongAdder();
  }
 }
 public FileOrBufOutput write(IoWriter io, boolean iswrok, ZipEntryM zip) throws Exception {
  FileOrBufOutput is=null;
  try {
   if (io.out == null) {
    int size=io.bufSize;
    WritableByteChannel wt;
    if (!iswrok && wrok.getAndSet(true))wt = is = new FileOrBufOutput(size);
    else wt = zipout;
    io.out = new DeflateOutput(this, zip, wt, size);
   }
   io.flush();
  } finally {
   OutputStream out= io.out;
   if (out instanceof DeflateOutput) {
    DeflateOutput def=(DeflateOutput)out;
    if (def.iswrok()) is = null;
   }
  }
  return is;
 }
 public void with(IoWriter io, ZipEntryM zip) throws Exception {
  ZipEntryOutput zipout=this.zipout;
  if (zip.mode <= 0 || !async)
   io.out = new NoCloseOutput(zip.mode <= 0 && zip.notFix ?zipout: zipout.outDef);
  if (!async) {
   zipout.putEntry(zip);
   io.flush();
  } else addTask(new DeflateWriter(io, zip));
 }
 public boolean toZip(WritableByteChannel out, ZipEntryM zip) throws IOException {
  ZipEntryOutput zipput=zipout;
  boolean iswrok = false;
  if (zipput != out && (iswrok = !wrok.getAndSet(true))) {
   zipput.putEntry(zip, true);
   FileOrBufOutput data=(FileOrBufOutput)out;
   data.writeTo(zipput);
  }
  return iswrok;
 }
 public void writeToZip(File file, ZipEntryM zip) throws IOException {
  if (!async) {
   ZipEntryOutput data=zipout;
   data.putEntry(zip);
   addFile(file, data, zip);
  } else {
   addTask(new DeflateWriter(file, zip));
  }
 }
 public boolean addFile(File file, WritableByteChannel out, ZipEntryM zip) throws IOException {
  FileChannel nio=new FileInputStream(file).getChannel();
  long size=nio.size();
  if (zip.mode > 0) {
   DeflateOutput dio=new DeflateOutput(this, zip, out, (int)Math.min(size, 8192l));
   try {
    nio.transferTo(0, size, dio);
   } finally {
    dio.close();
    nio.close();
   }
   return dio.iswrok();
  } else {
   ZipEntryOutput data= zipout;
   try {
    WritableByteChannel wt;
    boolean fixSize;
    if (zip.notFix) {
     wt = data.getNio();
     fixSize = true;
    } else {
     wt = data.outDef;
     fixSize = false;
    }
    nio.transferTo(0, size, wt);
    if (fixSize)data.upLength(size);
   } finally {
    nio.close();
   }
   return true;
  }
 }
 public boolean deflate(InputStream in, WritableByteChannel out, ZipEntryM zip) throws IOException {
  int len=Math.min(in.available(), 8192);
  byte[] buf = new byte[len];
  DeflateOutput def=new DeflateOutput(this, zip, out, len);
  int i;
  try {
   while ((i = in.read(buf)) > 0)
    def.write(buf, 0, i);
  } finally {
   in.close();
   def.close();
  }
  return def.iswrok();
 }
 public static void fixEntry(libDeflate def, LibdeflateCRC32 crc, ZipEntryM zip) {
  if (!zip.notFix)zip.crc = (int)crc.getValue();
  zip.size = def.rby;
  zip.csize = def.wby;
 }
 public void copyIo(InputStream in, ZipEntryM zip) throws IOException {
  ZipEntryOutput out=zipout;
  byte buf[]=out.buf;
  int i;
  LibdeflateCRC32 crc=!zip.notFix ?new LibdeflateCRC32(): null;
  try {
   while ((i = in.read(buf)) > 0) {
    if (crc != null)crc.update(buf, 0, i);
    out.write(buf, 0, i);
   }
  } finally {
   in.close();
  }
  fixEntry(out, crc, zip);
 }
 public static void fixEntry(ZipEntryOutput out, LibdeflateCRC32 crc, ZipEntryM zip) {
  int size=(int)(out.off - out.last);
  if (!zip.notFix)zip.crc = (int)crc.getValue();
  zip.size = size;
  zip.csize = size;
 }
 public void copyToZip(InputStream in, ZipEntryM zip) throws IOException, InterruptedException {
  if (!async) {
   zipout.putEntry(zip);
   copyIo(in, zip);
  } else {
   addTask(new DeflateWriter(in, zip, true));
  }
 }
 public void writeToZip(InputStream in, ZipEntryM zip) throws IOException, InterruptedException {
  int mode=zip.mode;
  if (!async) {
   ZipEntryOutput out=zipout;
   out.putEntry(zip);
   if (mode > 0) {
    deflate(in, out, zip);
   } else  {
    copyIo(in, zip);
   }
  } else {
   addTask(new DeflateWriter(in, zip, mode <= 0));
  }
 }
}
