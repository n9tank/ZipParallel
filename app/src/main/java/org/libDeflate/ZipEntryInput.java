package org.libDeflate;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.zip.ZipEntry;
import java.util.zip.InflaterInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.security.PublicKey;

public class ZipEntryInput {
 public static MethodHandle unwarp;
 public static MethodHandle unbuf;
 public static MethodHandle seter;
 public static final InputStream nullInputPtr=new ByteArrayInputStream(new byte[0]);
 static{
  try {
   Class fid= FilterInputStream.class;
   MethodHandles.Lookup lookup=MethodHandles.lookup();
   Field fin=fid.getDeclaredField("in");
   fin.setAccessible(true);
   unwarp = lookup.unreflectGetter(fin);
   seter = lookup.unreflectSetter(fin);
   fin = InflaterInputStream.class.getDeclaredField("buf");
   fin.setAccessible(true);
   unbuf = lookup.unreflectGetter(fin);
  } catch (Throwable e) {
  }  
 }
 public static byte[] unbuf(InflaterInputStream io) {
  InflaterInputStream in=io;
  try {
   return (byte[])unbuf.invokeExact(in);
  } catch (Throwable e) {}
  return null;
 }
 public static InputStream getRaw(InputStream io) {
  if (!(io instanceof FilterInputStream))return io;
  try {
   FilterInputStream in=(FilterInputStream)io;
   InputStream nio=(InputStream)unwarp.invokeExact(in);
   seter.invokeExact(in, nullInputPtr);
   io.close();
   return nio;
  } catch (Throwable e) {
  }
  return null;
 }
}
