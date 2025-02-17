package org.libDeflate;
import java.io.IOException;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;
import java.util.Queue;
import java.util.Iterator;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.ArrayDeque;

public class ErrorHandler {
 public volatile ConcurrentLinkedQueue<Future> flist;
 public ExecutorService pool;
 public LongAdder io=new LongAdder();
 public Vector<Throwable> err;
 public UIPost ui;
 public Canceler can;
 public ErrorHandler(ExecutorService pool, Canceler can, Vector list) {
  this.pool = pool;
  this.can = can;
  this.flist = new ConcurrentLinkedQueue();
  this.err = list;
 }
 public void add(Callable call) throws IOException {
  if (iscancel())throw new IOException();
  io.increment();
  fadd(call);
 }
 public void fadd(Callable call) {
  Future fu = pool.submit(call);
  Queue<Future> flist=this.flist;
  Iterator<Future> ite=flist.iterator();
  int size=ParallelDeflate.CPU;
  //仅对于有序的FrokJoin效果不理想
  while (ite.hasNext() && --size >= 0) {
   Future item=ite.next();
   if (item.isDone())
    ite.remove();
  }
  flist.offer(fu);
 }
 public void addN(Callable call) {
  if (iscancel())pop();
  else fadd(call);
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
  can.cancel();
 }
 public boolean cancel() {
  Queue<Future> list=flist;
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
 public void onClose() {
  UIPost ui=this.ui;
  if (ui != null)
   ui.accept(err);
 }
}
