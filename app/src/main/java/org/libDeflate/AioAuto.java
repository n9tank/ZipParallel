package org.libDeflate;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.Lock;

public class AioAuto implements BufIo,CompletionHandler {
 public void completed(Object result, Object attachment) {
  long block[]=this.block;
  if (attachment instanceof longkv) {
   synchronized (blockBuf) {
	blockBuf.remove(attachment);
   }
   synchronized (attachment) {
	attachment.notifyAll();
   }
  } else {
   ByteBuffer buf=(ByteBuffer)attachment;
   clearBlock(buf);
   int i=getOff(buf);
   int cy=buf.capacity() / buff[0].capacity();
   while (--cy >= 0) {
	block[i] = 0;
	buf = buff[i++];
	synchronized (buf) {
	 buf.notifyAll();
	}
   }
  }
  cou.decrement();
  long sum=cou.sum();
  if (sum == 0) {
   synchronized (this) {
	this.notifyAll();
   }
  }
  synchronized (block) {
   block.notify();
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
  int i;
  try {
   synchronized (block) {
	i = indexOf(0);
	while (i < 0) {
	 block.wait();
	 i = lastIn = indexOfAll(0);
	}
   }
   return mark = buff[i];
  } catch (InterruptedException e) {}
  return null;
 }
 public ReadWriteLock lock;
 public ByteBuffer mark;
 public ByteBuffer lastMark;
 public ByteBuffer getBuf(int size) {
  lastMark = null;
  int i=indexOf(0);
  ByteBuffer last=i < 0 ?null: buff[i];
  if (last != null && last.remaining() >= size)
   return mark = last;
  ByteBuffer buf=getBlockSync(last, size);
  if (buf.position() > 0 && buf.remaining() < size) {
   ByteBuffer next= getBlockSync(null, size);
   if (next.arrayOffset() != buf.arrayOffset()) {
	lastMark = buf;
	buf = next;
   }
  }
  return mark = buf;
 }
 public ByteBuffer getBlock(ByteBuffer last, int size) {
  ArrayList<ByteBuffer> list=getBlocks();
  if (last != null && last.position() > 0) {
   int c = findBlockHasByte(list, getOff(last));
   if (c >= 0)last = list.get(c);
  } else {
   int c=findBlockOrSmail(list, size);
   if (c >= 0)last = list.get(c);
  }
  return last;
 }
 public ByteBuffer getBlockSync(ByteBuffer last, int size) {
  try {
   synchronized (block) {
	while (true) {
	 last = getBlock(last, size);
	 if (last == null)
	  block.wait();
	 else break;
	}
   }
  } catch (InterruptedException e) {
   last = null;
  }
  return last;
 }
 public void restMark() {
  ByteBuffer drc=lastMark;
  ByteBuffer src=this.mark;
  if (drc != null) {
   int pos=src.position();
   src.flip();
   int len=drc.remaining();
   src.limit(len);
   drc.put(src);
   src.limit(pos);
   src.position(len);
   src.compact();
   release(drc, true);
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
   ByteBuffer buf=buff[i];
   synchronized (buf) {
	long j=block[i];
	if (j > 0 && j > pos && j - cy <= pos) {
	 buf.wait();
	 return;
	}
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
   ByteBuffer buf=buff[i];
   if (block[i] == 0 && buf.position() > 0)
	release(buf, true);
  }
 }
 public void waitIo() throws InterruptedException {
  if (cou.sum() > 0) {
   synchronized (this) {
	this.wait();
   }
  }
 }
 public void close() throws IOException {
  flush();
  try {
   waitIo();
  } catch (InterruptedException e) {}
  fio.close();
 }
 public boolean isOpen() {
  return true;
 }
 public void writeFast(ByteBuffer src) {
  int i= indexOf(0);
  if (i < 0) {
   insertBuffer(src);
   return;
  } else {
   ByteBuffer buf=buff[i];
   if (buf.position() > 0) {
	int lt=src.limit();
	src.limit(src.position() + Math.min(src.remaining(), buf.remaining()));
	buf.put(src);
	src.limit(lt);
	release(buf, false);
   }
   int len;
   if ((len = src.remaining()) < buf.capacity() && (i = (lastIn = indexOfAll(0))) >= 0) {
	buf = buff[i];
	int lt=src.limit();
	src.limit(src.position() + len);
	buf.put(src);
	src.limit(lt);
	release(buf, false);
   }
   if (src.remaining() > 0)
	insertBuffer(src);
  }
 }
 public int write(ByteBuffer src) {
  int len=src.remaining();
  int i=indexOf(0);
  ByteBuffer last=i <= 0 ?null: buff[i];
  while (len > 0) {
   if (last != null && last.remaining() < len)
	last = getBlockSync(last, len);
   int size;
   src.limit(src.position() + (size = Math.min(len, last.remaining())));
   len -= size;
   last.put(src);
   release(last, false);
  }
  return len;
 }
 public AsynchronousByteChannel fio;
 public volatile ByteBuffer buff[];
 public ByteBuffer src;
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
   while (block0 > 0) {
	int i=indexOf(0);
	if (i >= 0) {
	 ByteBuffer abuf = buff[i];
	 if (abuf.remaining() < block0)
	  abuf = getBlock(abuf, block0);
	 int pos=buf.position();
	 int lt=buf.limit();
	 int size=Math.min(abuf.remaining(), block0);
	 block0 -= size;
	 buf.limit(pos + size);
	 abuf.put(buf);
	 buf.limit(lt);
	 release(abuf, false);
	} else break;
   }
  }
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
  ByteBuffer all;
  lock = new ReentrantReadWriteLock();
  src = all = ByteBuffer.allocateDirect(size * fame);
  ByteBuffer[] buf=new ByteBuffer[fame];
  block = new long[fame];
  cou = new LongAdder();
  buff = buf;
  for (int i=0;i < fame;++i) {
   int k=size * fame;
   all.limit(k);
   all.position(k - size);
   buf[i] = all.slice();
  }
 }
 public void release(ByteBuffer buf, boolean or) {
  int len=buf.position();
  int cy=buff[0].capacity();
  int i=getOff(buf);
  if (or || len >= cy) {
   while (or ?len > 0: len >= cy) {
	block[i++] = pos += cy;
	len -= cy;
   }
   if (!or && len > 0) {
	lastIn = i;
	buff[i].position(len);
	int pos=buf.position() - len;
	buf.rewind();
	buf.limit(pos);
	buf = buf.slice();
   } else buf.flip();
   cou.increment();
   fio.write(buf, buf, this);
  } else lastIn = i;
 }
 public volatile int lastIn=-1;
 public ArrayList<ByteBuffer> getBlocks() {
  int pos=-1;
  int i=0;
  ArrayList<ByteBuffer> buf=new ArrayList();
  while (i >= 0) {
   int n= indexOfAll(i);
   if (i + 1 != n || i < 0) {
	if (pos >= 0 && (i - pos) > 1)
	 buf.add(block(pos, i));
	pos = -1;
   } else if (pos < 0 && buff[n].position() == 0) 
	pos = i;
   i = n;
  }
  return buf;
 }
 public int findBlockHasByte(ArrayList<ByteBuffer> arr, int in) {
  for (int i=0,len=arr.size();i < len;++i) {
   ByteBuffer buf=arr.get(i);
   if (getOff(buf) != in)continue;
   return i;
  }
  return -1;
 }
 public int findBlockOrSmail(ArrayList<ByteBuffer> arr, int size) {
  int cy=buff[0].capacity();
  while (size >= cy) {
   int buf=findBlock(arr, size);
   if (buf >= 0)return buf;
   size -= cy;
  }
  int c=indexOfAll(0);
  return c < 0 ?-1: c;
 }
 public int findBlock(ArrayList<ByteBuffer> arr, int size) {
  for (int i=0,len=arr.size();i < len;++i) {
   ByteBuffer buf=arr.get(i);
   if (buf.remaining() < size)continue;
   return i;
  }
  return -1;
 }
 public int getOff(ByteBuffer buf) {
  return buf.arrayOffset() / buff[0].capacity();
 }
 public void clearBlock(ByteBuffer buf) {
  int off= getOff(buf);
  buff[off].clear();
  buf.clear();
 }
 public ByteBuffer block(int pos, int end) {
  ByteBuffer src=this.src;
  ByteBuffer buf=buff[pos];
  int cy=buf.capacity();
  src.position(pos * cy);
  src.limit(end * cy);
  ByteBuffer ret=src.slice();
  ret.position(buf.position());
  return ret;
 }
 public int indexOf(int i) {
  int j=lastIn;
  if (j >= 0 && block[j] == 0)return j;
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
