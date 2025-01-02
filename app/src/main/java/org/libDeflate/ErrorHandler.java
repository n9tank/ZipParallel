package org.libDeflate;
import java.io.IOException;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;

public abstract class ErrorHandler {
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
  Vector<Future> flist=this.flist;
  if (flist == null)throw new IOException();
  io.increment();
  flist.add(pool.submit(call));
 }
 public void addN(Callable call) {
  Vector<Future> flist=this.flist;
  if (flist == null)pop();
  else flist.add(pool.submit(call));
 }
 public boolean iscancel() {
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
  for (Future fu:list) {
   boolean isrun=fu.cancel(true);
   if (!isrun && fu.isCancelled())
    pop();
  }
  return true;
 }
 public abstract void onClose();
}
