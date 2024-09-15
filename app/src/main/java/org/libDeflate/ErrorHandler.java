package org.libDeflate;
import java.util.concurrent.atomic.LongAdder;

public class ErrorHandler {
 public ParallelDeflate para;
 public boolean lock;
 public boolean ignore;
 public volatile Exception ex;
 public ErrorHandler(ParallelDeflate para) {
  this.para = para;
 }
 public boolean onError(Exception err) {
  if (para.flist != null && !(err instanceof InterruptedException)) {
   Exception e=ex;
   if (e == null) {
    synchronized (this) {
     if ((e = ex) == null)ex = err;
    }
   }
   if (e != null)e.addSuppressed(err);
   if (!ignore)return para.cancel();
  }
  return false;
 }
 public void setlock() {
  if (para.async)lock = true;
 }
 public void lock() throws Exception {
  if (lock) {
   LongAdder io=para.io;
   if (io.sum() > 0) {
    synchronized (io) {
     io.wait();
    }
   }
   Exception e=ex;
   if (e != null)throw e;
  }
 }
 public void unlock() {
  if (lock) {
   LongAdder io=para.io;
   synchronized (io) {
    io.notifyAll();
   }
  }
 }
 public void onClose() {
  unlock();
 }
}
