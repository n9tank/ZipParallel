package org.libDeflate;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.Inflater;
import me.steinborn.libdeflate.LibdeflateDecompressor;
import me.steinborn.libdeflate.ObjectPool;
import java.util.concurrent.atomic.*;

public class InflatePool {
 public static final ConcurrentLinkedQueue<Inflater> inflatePool=new ConcurrentLinkedQueue();
 public static final LongAdder inflaterNum=new LongAdder();
 public static void addCount(){
  inflaterNum.increment();
 }
 public static Inflater allocInfalte() {
  Inflater obj= inflatePool.poll();
  if (obj == null)
   obj = new Inflater(true);
  return obj;
 }
 public static void free(Inflater ctx) {
  inflatePool.add(ctx);
 }
 public static void inflateGc() {
  inflaterNum.decrement();
  if (inflaterNum.sum() <= 0){
   ConcurrentLinkedQueue<Inflater> list=inflatePool;
   Inflater obj;
   while ((obj = (list.poll())) != null)
	obj.end();
  }
 }
}
