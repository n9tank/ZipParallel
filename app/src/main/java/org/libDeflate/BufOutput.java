package org.libDeflate;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;


public class BufOutput implements BufIo {
 public boolean isOpen() {
  return true;
 }
 ByteBuffer buf;
 public BufOutput(int size) {
  size = size & 0x7fffffff;
  if (size > 0) {
   buf = RC.newDbuf(size);
  }
 }
 public static final int tableSizeFor(int cap) {  
  int n = cap - 1;  
  n |= n >>> 1;  
  n |= n >>> 2;  
  n |= n >>> 4;  
  n |= n >>> 8;  
  n |= n >>> 16;  
  return n + 1;
 }
 public ByteBuffer getBuf() {
  return buf;
 }
 public static ByteBuffer copy(ByteBuffer old, int size) {
  ByteBuffer buf=RC.newDbuf(size);
  old.flip();
  buf.put(old);
  return buf;
 }
 public ByteBuffer getBufFlush() {
  ByteBuffer old=this.buf;
  this.buf = old = copy(old, old.capacity() << 1);
  return old;
 }
 public void end() {}
 public int write(ByteBuffer src) {
  int len=src.remaining();
  ByteBuffer buf=this.buf;
  int limt=buf.remaining() - len;
  if (len > limt)
   this.buf = buf = copy(buf, tableSizeFor(buf.position() + len));
  buf.put(src);
  return len;
 }
 public void close() {}
}
