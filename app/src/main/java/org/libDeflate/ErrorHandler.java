package org.libDeflate;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;
import java.io.IOException;

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
  if (!igron && err.size() > 0)throw new IOException();
  io.increment();
  addN(call);
 }
 public void addN(Callable call) {
  flist.add(pool.submit(call));
 }
 public boolean iscancel() {
  return flist == null;
 }
 public void pop() {
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
  this.flist = null;
  if (list == null)return false;
  for (Future fu:list) {
   fu.cancel(true);
  }
  return true;
 }
 public abstract void onClose();
}
