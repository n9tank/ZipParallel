package org.libDeflate;
import java.io.IOException;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;

public abstract class ErrorHandler {
 public AtomicReferenceArray<Future> cache=new AtomicReferenceArray(Runtime.getRuntime().availableProcessors() << 1);
 public volatile Vector<Future> flist=new Vector();
 public ExecutorService pool;
 public LongAdder io=new LongAdder();
 public boolean igron;
 public Vector<Throwable> err=new Vector();
 public Canceler can;
 public ErrorHandler(ExecutorService pool, Canceler can) {
  this.pool = pool;
  this.can = can;
 }
 public void add(Callable call) throws IOException {
  if (iscancel())throw new IOException();
  io.increment();
  fadd(call, false);
 }
 public void fadd(Callable call, boolean isCache) {
  Future fu = pool.submit(call);
  //尝试取活跃的任务避免数组扩张
  if (isCache) {
   AtomicReferenceArray<Future> cache=this.cache;
   for (int i=0,len=cache.length();i < len;++i) {
    Future check=cache.get(i);
    if (check == null || check.isDone()) {
     if (!cache.compareAndSet(i, check, fu))
      continue;
     return;
    }
   }
  }
  flist.add(fu);
 }
 public void addN(Callable call) {
  if (iscancel())pop();
  else fadd(call, true);
 }
 public final boolean iscancel() {
  return flist == null;
 }
 public void pop() {
  LongAdder io=this.io;
  io.decrement();
  if (io.sum() < 0) {
   if (flist != null)can.end();
   onClose();
  }
 }
 public void onError(Throwable e) {
  if (e instanceof InterruptedException)return;
  err.add(e);
  if (!igron)can.cancel();
 }
 public boolean cancel() {
  Vector<Future> list=flist;
  if (list == null)return false;
  synchronized (this) {
   list = flist;
   if (list != null)this.flist = null;
   else return false;
  }
  AtomicReferenceArray<Future> cache=this.cache;
  for (int i=0,len=cache.length();i < len;++i) {
   Future fu= cache.get(i);
   if (fu == null)break;
   boolean isrun=fu.cancel(true);
   if (!isrun && fu.isCancelled())
    pop();
  }
  for (Future fu:list) {
   boolean isrun=fu.cancel(true);
   if (!isrun && fu.isCancelled())
    pop();
  }
  return true;
 }
 public abstract void onClose();
}
