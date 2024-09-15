package org.libDeflate;
import java.nio.ByteBuffer;
import me.steinborn.libdeflate.LibdeflateCompressor;

public class libDeflate extends LibdeflateCompressor {
 public int mode;
 public int rby;
 public int wby;
 public libDeflate(int lvl) {
  super(lvl);
 }
 public int compress(ByteBuffer src, ByteBuffer drc) {
  rby += src.limit();
  int i=compress(src, drc, mode);
  wby += i;
  src.clear();
  drc.flip();
  return i;
 }
}
