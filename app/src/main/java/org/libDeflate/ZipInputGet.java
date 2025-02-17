package org.libDeflate;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;

public class ZipInputGet extends IoWriter {
 public void flush() throws Exception {
  BufIo out=this.out;
  ByteBuffer buf=out.getBuf();
  if (RC.zip_read_mmap || (en.mode > 0 && bufSize != 0)) {
   if (buf.remaining() < en.size)
    out.write(zip.unBuf(en));
   else {
	int pos=buf.position();
    zip.unBuf(zip.getBuf(en), buf);
    out.end();
   }
   return;
  }
  if (!RC.zip_read_mmap) {
   ReadableByteChannel reader=zip.openBlock(en);
   try {
    while (true) {
     int i = reader.read(buf);
     if (i < 0)break;
     if (i == 0)
      buf = out.getBufFlush();
    }
    out.end();
   } finally {
    reader.close();
   }
  }
 }
 public static BufferedReader reader(zipFile zip, zipEntry en, Charset set) throws IOException  {
  NioReader read;
  int size=(int)Math.min(en.size, 8192);
  if (!RC.zip_read_mmap) {
   if (en.mode > 0)
    read = new NioReader(null, zip.unBuf(en), set);
   else read = new NioReader(zip.openBlock(en), (int)Math.min(en.size, 65536l), set);
  } else 
   read = new NioReader(null, zip.unBuf(en), set);
  return new BufferedReader(read, size);
 }
 public zipFile zip;
 public zipEntry en;
 public ZipInputGet(zipFile zip, zipEntry en) {
  this.zip = zip;
  this.en = en;
 }
}
