package org.libDeflate;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.zip.ZipEntry;

public class ZipEntryInput extends InputStream {
 public static MethodHandle unwarp;
 static{
  try {
   Class fid= FilterInputStream.class;
   MethodHandles.Lookup lookup=MethodHandles.lookup();
   Field fin=fid.getDeclaredField("in");
   fin.setAccessible(true);
   unwarp = lookup.unreflectGetter(fin);
  } catch (Throwable e) {
  }  
 }
 InputStream io;
 InputStream src;
 public ZipEntryInput(InputStream io, InputStream src) {
  this.io = io;
  this.src = src;
 }
 public static InputStream getRaw(InputStream io, ZipEntry en) {  
  if (en.getMethod() == 0)return io;
  else {
   try {
    return new ZipEntryInput(io, (InputStream)unwarp.invokeExact((FilterInputStream)io));
   } catch (Throwable e) {
   }
  }
  return null;
 }
 public int read() {
  throw new RuntimeException();
 }
 public int read(byte[] b, int off, int len) throws IOException {
  return src.read(b, off, len);
 }
 public void close() throws IOException {
  io.close();
 }
}
