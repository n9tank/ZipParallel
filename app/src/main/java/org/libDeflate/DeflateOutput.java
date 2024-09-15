package org.libDeflate;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import me.steinborn.libdeflate.LibdeflateCRC32;
import me.steinborn.libdeflate.LibdeflateJavaUtils;

public class DeflateOutput extends OutputStream implements WritableByteChannel {
 public ParallelDeflate lock;
 public WritableByteChannel data;
 public WritableByteChannel oldout;
 public libDeflate def;
 public LibdeflateCRC32 crc;
 public ZipEntryM ze;
 public ByteBuffer buf;
 public DeflateOutput(ParallelDeflate para, ZipEntryM en, WritableByteChannel out, int size) {
  ZipEntryOutput zipout=para.zipout;
  lock = para;
  ByteBuffer buf;
  this.def = new libDeflate(en.mode);
  if (out != zipout) {
   oldout = out;
   buf = ByteBuffer.allocateDirect(LibdeflateJavaUtils.getBufSize(size, false));
  } else {
   ZipEntryOutput.DeflaterIo defo=zipout.outDef;
   defo.putEntry(en);
   buf = defo.buf;
  }
  data = out;
  this.buf = buf;
  if (!en.notFix)crc = new LibdeflateCRC32();
  ze = en;
 }
 public void write(int b) {
  throw new RuntimeException();
 }
 public boolean isOpen() {
  return true;
 }
 public int write(ByteBuffer src) throws IOException {
  ParallelDeflate para=lock;
  if ((ze.onlyInput || para.hlist == null) && para.toZip(data, ze)) {
   data = para.zipout;
  }
  return ParallelDeflate.deflate(src, buf, def, crc, data);
 }
 public void write(byte[] b, int off, int len) throws IOException {
  write(ByteBuffer.wrap(b, off, len));
 }
 public boolean iswrok() {
  return data instanceof ZipEntryOutput;
 }
 public void close() throws IOException {
  ParallelDeflate.fixEntry(def, crc, ze);
  def.close();
  if (iswrok()) {
   WritableByteChannel out=oldout;
   if (out != null) {
    out.close();
   }
  }
 }
}
