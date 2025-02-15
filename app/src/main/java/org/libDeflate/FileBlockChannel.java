package org.libDeflate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

public class FileBlockChannel implements ReadableByteChannel {
 public FileChannel fc;
 public long pos;
 public long rem;
 public FileBlockChannel(FileChannel ch, long off, long size) throws IOException {
  fc = ch;
  pos = off;
  rem = size;
 }
 public void close() {}
 public boolean isOpen() {
  return true;
 }
 public int read(ByteBuffer buf) throws IOException {
  long rem=this.rem;
  if (rem <= 0)
   return -1;
  long pos=this.pos;
  int size=(int)Math.min(rem, buf.remaining());
  buf.limit(buf.position() + size);
  this.pos = pos + size;
  this.rem = rem - size;
  int len=fc.read(buf, pos);
  buf.limit(buf.capacity());
  return len;
 };
}
