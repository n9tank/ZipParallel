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
  buf = ByteBuffer.allocateDirect(size);
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
 public ByteBuffer getBuf(){
  return buf;
 }
 public ByteBuffer getBufFlush() {
  ByteBuffer old=this.buf;
  ByteBuffer buf=ByteBuffer.allocateDirect(old.capacity() << 1);
  this.buf = buf;
  old.flip();
  buf.put(old);
  return buf;
 }
 public int write(ByteBuffer src) {
  int len=src.remaining();
  ByteBuffer buf=this.buf;
  int limt=buf.remaining() - len;
  if (len > limt) {
   ByteBuffer old=buf;
   this.buf = buf = ByteBuffer.allocateDirect(tableSizeFor(buf.position() + len));
   old.flip();
   buf.put(old);
  }
  buf.put(src);
  return len;
 }
 public void close() {
 }
}
