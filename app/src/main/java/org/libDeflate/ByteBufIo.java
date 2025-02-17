package org.libDeflate;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.MappedByteBuffer;

public class ByteBufIo implements BufIo {
 public boolean isOpen() {
  return true;
 }
 public ByteBuffer buf;
 public WritableByteChannel wt;
 public ByteBufIo(WritableByteChannel out, int size) {
  wt = out;
  ByteBuffer buf = ByteBuffer.allocateDirect(size);
  buf.order(ByteOrder.LITTLE_ENDIAN);
  this.buf = buf;
 }
 public void close() throws IOException {
  WritableByteChannel wt=this.wt;
  try {
   if (buf != null) {
    flush();
    if (wt instanceof FileChannel) {
     FileChannel rnio=(FileChannel)wt;
     rnio.truncate(rnio.position());
    }
   }
  } finally {
   wt.close();
  }
 }
 public ByteBuffer getBuf() {
  return buf;
 }
 public ByteBuffer getBufFlush() throws IOException {
  ByteBuffer buf=this.buf;
  if (RC.getflush_pagesize) {
   int pos=buf.position();
   buf.rewind();
   buf.limit(pos & RC.PAGESIZE_N4096);
   WritableByteChannel wt=this.wt;
   while (buf.hasRemaining())
    wt.write(buf);
   buf.limit(pos);
   buf.compact();
  } else flush();
  return buf;
 }
 public void end() {}
 public void flush() throws IOException {
  ByteBuffer buf=this.buf;
  buf.flip();
  WritableByteChannel wt=this.wt;
  while (buf.hasRemaining())
   wt.write(buf);
  buf.clear();
 }
 public ByteBuffer getBuf(int page) throws IOException {
  ByteBuffer buf=this.buf;
  int len=buf.remaining();
  if (len <= 0) {
   flush();
   len = buf.capacity();
  }
  if (len < page) {
   buf = RC.newbuf(page);
   buf.order(ByteOrder.LITTLE_ENDIAN);
  }
  return buf;
 }
 public void swap(ByteBuffer put) {
  if (!put.isDirect() || (put instanceof MappedByteBuffer))
   return ;
  int drclen= put.capacity() & RC.PAGESIZE_N4096;
  if (drclen > buf.capacity()) {
   put.rewind();
   put.limit(drclen);
   put = put.slice();
   this.buf = put;
  }
 }
 
 public int write(ByteBuffer put) throws IOException {
  int len=put.remaining();
  ByteBuffer buf=this.buf;
  int rem=buf.position() & RC.PAGESIZE_4095;
  if (rem > 0)rem = RC.PAGESIZE - rem;
  int wlen=(len - rem) & RC.PAGESIZE_N4096;
  if (wlen >= buf.capacity()) {
   int limt=put.limit();
   put.limit(put.position() + rem);
   buf.put(put);
   flush();
   put.limit(put.position() + wlen);
   WritableByteChannel wt=this.wt;
   while (put.hasRemaining())
    wt.write(put);
   put.limit(limt);
   buf.put(put);
  } else {
   wlen = len;
   do{
	rem = Math.min(wlen, buf.remaining());
	wlen -= rem;
	put.limit(put.position() + rem);
	buf.put(put);
	if (!buf.hasRemaining())
	 flush();
   }while(wlen > 0);
  }
  return len;
 }
}
