package org.libDeflate;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.ThreadLocalRandom;


public class FileOrBufOutput implements WritableByteChannel {
 public boolean isOpen() {
  return true;
 }
 static{
  FileOrBufOutput.setCachePath(System.getProperty("java.io.tmpdir").concat("/tmp"));
 }
 public void write(int b) {
  throw new RuntimeException();
 }
 public static File Path;
 public static void setCachePath(String str) {
  File file = new File(str);
  Path = file;
  file.mkdirs();
  for (File toDel:file.listFiles()) {
   toDel.delete();
  }
 }
 ByteBuffer buf;
 FileChannel io;
 File cache;
 long last;
 public FileOrBufOutput(int size) {
  buf = ByteBuffer.allocateDirect(size);
 }
 public FileChannel openFile() throws FileNotFoundException {
  FileChannel fio=io;
  if (fio != null)return fio;
  ThreadLocalRandom ran=ThreadLocalRandom.current();
  for (;;) {
   File file=new File(Path, Integer.toHexString(ran.nextInt() & 0xffff));
   if (!file.exists()) {
    cache = file;
    return io = new RandomAccessFile(file, "rw").getChannel();
   }
  }
 }
 public int write(ByteBuffer src) throws IOException {
  int len=src.limit();
  ByteBuffer buf=this.buf;
  int capacity=buf.capacity();
  int limit=buf.remaining();
  if (limit < capacity || len < limit) {
   src.limit(Math.min(len, limit));
   buf.put(src);
  }
  limit = src.position();
  int wlen=len - limit;
  //与缓存相等大概率后面还有数据，不复到内存
  if (wlen >= 0) {
   FileChannel fio=openFile();
   //对齐内存
   buf.flip();
   if (buf.hasRemaining()) {
    last += capacity;
    fio.write(buf);
   }
   buf.clear();
   src.limit(len);
   if (wlen >= capacity) {
    last += src.remaining();
    fio.write(src);
   } else buf.put(src);
  }
  return len;
 }
 public void writeTo(ZipEntryOutput zip) throws IOException {
  long len=last + buf.position();
  WritableByteChannel zbuf= zip.outBuf;
  writeTo(zip.getNio(), zbuf);
  zip.upLength(len);
 }
 public void writeTo(WritableByteChannel out, WritableByteChannel zbuf) throws IOException {
  FileChannel fio=io;
  long len=last;
  if (fio != null) {
   fio.position(0);
   fio.transferTo(0, len, out);
  }
  ByteBuffer buf=this.buf;
  buf.flip();
  while (buf.hasRemaining())
   zbuf.write(buf);
 }
 public void close() throws IOException {
  FileChannel fio=io;
  if (fio != null) {
   try {
    fio.close();
   } finally {
    cache.delete();
   }
  }
 }
}
