package org.libDeflate;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.io.BufferedReader;
import android.util.SizeF;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public class ZipInputGet extends IoWriter {
 public void flush() throws Exception {
  ReadableByteChannel reader=io();
  try {
   BufIo out=this.out;
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
 public ReadableByteChannel io() throws IOException {
  zipEntry en=this.en;
  if (bufSize < 0)return zip.openChannel(en);
  return zip.open(zip.getBuf(en));
 }
 public static BufferedReader reader(zipFile zip, zipEntry en, Charset set) throws IOException  {
  int size=(int) Math.min(en.size, 8192);
  NioReader read;
  //已修复NioReader没有检查导致的性能问题
  if (en.mode > 0) 
   read = new NioReader(zip.open(zip.getBuf(en)), ByteBuffer.allocate(size), set);
  else read = new NioReader(zip.openChannel(en), (int)Math.min(en.size, 65536l), set);
  //这是为了解约内存
  return new BufferedReader(read, size);
 }
 public zipFile zip;
 public zipEntry en;
 public ZipInputGet(zipFile zip, zipEntry en) {
  this.zip = zip;
  this.en = en;
 }
}
