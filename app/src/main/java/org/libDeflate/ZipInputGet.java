package org.libDeflate;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;

public class ZipInputGet extends IoWriter {
 public void flush() throws Exception {
  BufIo out=this.out;
  if (RC.zip_read_mmap) {
   if (bufSize < 0) {
    ByteBuffer buf=zip.getBuf(en);
    if (out instanceof BufOutput) {
     ((BufOutput)out).buf = buf;
     buf.position(buf.limit());
    } else out.write(buf);
    return;
   }
  }
  ReadableByteChannel reader=io();
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
 public ReadableByteChannel io() throws IOException {
  zipEntry en=this.en;
  if (bufSize < 0)return zip.openChannel(en);
  return zip.open(zip.getBuf(en));
 }
 public static BufferedReader reader(zipFile zip, zipEntry en, Charset set) throws IOException  {
  int size=(int) Math.min(en.size, 8192);
  NioReader read;
  if (!RC.zip_read_mmap) {
   if (en.mode > 0) 
    read = new NioReader(zip.open(zip.getBuf(en)), ByteBuffer.allocate(size), set);
   else read = new NioReader(zip.openChannel(en), (int)Math.min(en.size, 65536l), set);
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
