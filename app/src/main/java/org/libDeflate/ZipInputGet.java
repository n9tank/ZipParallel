package org.libDeflate;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.io.InputStream;
import java.io.IOException;
import java.io.BufferedInputStream;

public class ZipInputGet implements InputGet {
 public ZipFile zip;
 public ZipEntry en;
 public boolean raw;
 public boolean buf;
 public InputStream io() throws IOException {
  InputStream in=zip.getInputStream(en);
  if (raw)in = ZipEntryInput.getRaw(in, en);
  else if (en.getMethod() > 0 && buf)
   in = new BufferedInputStream(in, Math.min(8192, in.available()));
  return in;
 }
 public ZipInputGet(ZipFile zip, ZipEntry en, boolean raw) {
  this.zip = zip;
  this.en = en;
  this.raw = raw;
 }
}
