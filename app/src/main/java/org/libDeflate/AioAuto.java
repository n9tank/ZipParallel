package org.libDeflate;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.concurrent.atomic.LongAdder;
import java.util.Vector;

public class AioAuto implements BufIo,CompletionHandler {
 public void completed(Object result, Object attachment) {
  Object lock=attachment;
  if (lock instanceof longkv) {
   synchronized (blockBuf) {
	blockBuf.remove(lock);
   }
  } else {
   int i= (Integer)attachment;
   ByteBuffer buf=buff[i];
   lock = buf;
   buf.clear();
   block[i] = 0;
  }
  cou.decrement();
  long sum=cou.sum();
  if (sum == 0) {
   synchronized (this) {
	this.notifyAll();
   }
  }
  synchronized (lock) {
   lock.notifyAll();
  }
 }
 public Vector<Throwable> err=new Vector();
 public void failed(Throwable exc, Object attachment) {
  err.add(exc);
  if (buff != null) {
   try {
	fio.close();
   } catch (IOException e) {}
   buff = null;
  }
 }
 public ByteBuffer getBuf() {
  int i=indexOf(0);
  while (i < 0)i = indexOfSync();
  return buff[mark = i];
 }
 public int indexOfSync() {
  synchronized (block) {
   try {
	block.wait();
   } catch (InterruptedException e) {}
   return indexOfAll(0);
  }
 }
 public int mark;
 public int lastmark;
 public ByteBuffer getBuf(int size) throws IOException {
  if (size > buff[0].capacity())return null;
  lastmark = -1;
  int i = indexOf(0);
  int real=0;
  ByteBuffer buff[]=this.buff;
  while (true) {
   ByteBuffer buf;
   if (lastmark < 0) {
	while (i < 0)i = indexOfSync();
	buf = buff[i];
	if (buf.remaining() < size) {
	 lastmark = i;
	 continue;
	} else {
	 mark = i;
	 return buf;
	}
   } else {
	real = indexOfAll(real);
	while (real < 0)real = indexOfSync();
	buf = buff[real];
	if (buf.remaining() < size)continue;
	mark = real;
	lastIn = real;
	return buf;
   }
  }
 }
 public void restMark() {
  int last=lastmark;
  int mark=this.mark;
  ByteBuffer buff[]=this.buff;
  ByteBuffer src=buff[mark];
  if (last >= 0) {
   ByteBuffer drc=buff[last];
   int pos=src.position();
   src.flip();
   int len=drc.remaining();
   src.limit(len);
   drc.put(src);
   src.limit(pos);
   src.position(len);
   src.compact();
   release(last, true);
  }
  release(mark, false);
 }
 public ByteBuffer getBufFlush() {
  release(mark, false);
  return getBuf();
 }
 public void waitBlock(long pos) throws InterruptedException {
  ByteBuffer buff[]=this.buff;
  int cy=buff[0].capacity();
  for (int i=0,len=block.length;i < len;++i) {
   long j=block[i];
   if (j > 0 && j > pos && j - cy <= pos) {
	ByteBuffer buf=buff[i];
	synchronized (buf) {
	 buf.wait();
	}
	return;
   }
  }
  synchronized (blockBuf) {
   for (int i=0,len=blockBuf.size();i < len;++i) {
	longkv kv=blockBuf.get(i);
	long start=kv.start;
	if (start <= pos && start + kv.len > pos) {
	 synchronized (kv) {
	  kv.wait();
	 }
	 return;
	}
   }
  }
 }
 public void flush() {
  for (int i=0,len=buff.length;i < len;++i) {
   if (block[i] == 0 && buff[i].position() > 0)
	release(i, true);
  }
  if (cou.sum() > 0) {
   synchronized (this) {
	try {
	 this.wait();
	} catch (InterruptedException e) {}
   }
  }
 }
 public void close() throws IOException {
  flush();
  fio.close();
 }
 public boolean isOpen() {
  return true;
 }
 public void writeFast(ByteBuffer src) {
  int len=src.remaining();
  while (len > 0) {
   int i= indexOf(0);
   if (i < 0) {
	src.limit(src.position() + len);
	insertBuffer(src);
	return;
   } else {
	ByteBuffer buf=buff[i];
	if (buf.position() == 0 && len >= buf.capacity()) {
	 src.limit(src.position() + len);
	 insertBuffer(src);
	 return;
	}
	int size;
	src.limit(src.position() + (size = Math.min(len, buf.remaining())));
	len -= size;
	buf.put(src);
	release(i, false);
   }
  }
 }
 public int write(ByteBuffer src) {
  ByteBuffer buf=getBuf();
  int len=src.remaining();
  while (len > 0) {
   while (buf.hasRemaining()) {
	int size;
	src.limit(src.position() + (size = Math.min(len, buf.remaining())));
	len -= size;
	buf.put(src);
   }
   buf = getBufFlush();
  }
  return len;
 }
 public AsynchronousByteChannel fio;
 public volatile ByteBuffer buff[];
 public static class longkv {
  public long start;
  public int len;
  public longkv(long p, int l) {
   start = p;
   len = l;
  }
 }
 public ArrayList<longkv> blockBuf;
 public void insertBuffer(ByteBuffer buf) {
  int cy=buf.remaining();
  int block0=cy & -4096;
  if (block0 > 0) {
   int i=indexOf(0);
   if (i >= 0) {
	ByteBuffer abuf=buff[i];
	int pos=buf.position();
	if (pos == 0) {
	 buf.position(cy -= block0);
	 abuf.put(buf);
	 buf.rewind();
	 buf.limit(cy);
	}
   }
  }
  buf.flip();
  longkv lock;
  synchronized (blockBuf) {
   blockBuf.add(lock = new longkv(pos, cy));
  }
  cou.increment();
  fio.write(buf, lock, this);
  pos += cy;
 }
 public LongAdder cou;
 public long pos;
 public volatile long[] block;
 public AioAuto(AsynchronousByteChannel f, int size, int fame) {
  fio = f;
  blockBuf = new ArrayList();
  ByteBuffer[] buf=new ByteBuffer[fame];
  block = new long[fame];
  cou = new LongAdder();
  buff = buf;
  for (int i=0;i < fame;++i) {
   buf[i] = ByteBuffer.allocateDirect(size);
  }
 }
 public void release(int i, boolean or) {
  ByteBuffer buf=buff[i];
  int cy=buf.remaining();
  if (or || buf.position() == buf.capacity()) {
   block[i] = pos += cy;
   buf.flip();
   cou.increment();
   fio.write(buf, i, this);
  }
 }
 public volatile int lastIn;
 public int indexOf(int i) {
  int j=lastIn;
  if (block[j] == 0 && j >= i)return j;
  return lastIn = indexOfAll(i);
 }
 public int indexOfAll(int i) {
  ByteBuffer buff[]=this.buff;
  for (int len=buff.length;i < len;++i) {
   if (block[i] <= 0)return i;
  }
  return -1;
 }
}
