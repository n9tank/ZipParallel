package org.libDeflate;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;

public class ZipInputGet extends IoWriter {
 public void flush() throws Exception {
  BufIo out=this.out;
  ReadableByteChannel reader=zip.open(en);
  try {
   ByteBuffer buf=out.getBuf();
   int i;
   while (true) {
    do {
     i = reader.read(buf);
    }while(i > 0);
    if (i < 0)break;
    out.getBufFlush();
   }
   out.end();
  } finally {
   reader.close();
  }
 }
 public static BufferedReader reader(zipFile zip, zipEntry en, Charset set) throws IOException  {
  int size=(int) Math.min(en.size, 8192);
  NioReader read;
  if (!RC.zip_read_mmap && !RC.zip_read_all) {
   ReadableByteChannel io=zip.open(en);
   if (en.mode > 0)
    read = new NioReader(io, ByteBuffer.allocate(size), set);
   else read = new NioReader(io, (int)Math.min(en.size, 65536l), set);
  } else {
   ByteBuffer buf=zip.getBuf(en);
   if (en.mode > 0) 
    read = new NioReader(zip.open(buf), ByteBuffer.allocate(size), set);
   else read = new NioReader(null, buf, set);
  }
  return new BufferedReader(read, size);
 }
 public zipFile zip;
 public zipEntry en;
 public ZipInputGet(zipFile zip, zipEntry en) {
  this.zip = zip;
  this.en = en;
 }
}
