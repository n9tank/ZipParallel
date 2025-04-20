package org.libDeflate;
import java.nio.ByteBuffer;

public class RC {
 public final static ByteBuffer newDbuf(int size) {
  return size < SMSIZE ?
   ByteBuffer.allocate(size):
   ByteBuffer.allocateDirect(size);
 }
 public final static ByteBuffer newbuf(int size) {
  return size < DSIZE ?
   ByteBuffer.allocate(size):
   ByteBuffer.allocateDirect(size);
 }
 public static final int IOSIZE=8192;
 public static final boolean zip_time=false;
 public static final boolean zip_asInput=false;
 public static final boolean zip_close_async=false;
 public static final boolean zip_deflate_io=false;
 public static final boolean zip_read_mmap=true;
 public static final int MMAPSIZE=1024 * 64;
 public static final int DSIZE=1024 * 64;
 public static final int NSIZE=1024 * 64;
 public static final int SMSIZE=8192;
 public static final boolean zip_zlib=false;
 public static final boolean zip_addFile=false;
 public static final boolean zip_crc=false;
}
