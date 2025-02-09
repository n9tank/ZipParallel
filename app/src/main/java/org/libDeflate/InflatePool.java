package org.libDeflate;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.Inflater;
import me.steinborn.libdeflate.LibdeflateDecompressor;
import me.steinborn.libdeflate.ObjectPool;

public class InflatePool {
 public static final ConcurrentLinkedQueue<Inflater> inflatePool=new ConcurrentLinkedQueue();
 public static Inflater allocInfalte() {
  Inflater obj= inflatePool.poll();
  if (obj != null)
   return obj;
  else return new Inflater(true);
 }
 public static void free(Inflater ctx) {
  inflatePool.add(ctx);
 }
 public static void inflateGc() {
  ConcurrentLinkedQueue<Inflater> list=inflatePool;
  Inflater obj;
  while ((obj = (list.poll())) != null)
   obj.end();
 }
}
