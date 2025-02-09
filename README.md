# ZipParallel
这是一个实验性的压缩库，用于构造特殊的Zip文件，使用libDeflate作为解压与压缩的实现并支持多线程压缩。
多线程压缩完全在内存上工作，只要牺牲内存就可以获得较高的性能，不过还是受限于java了。
尽管架构是异步的，但是目前不支持异步写出。

# release
- [arm64-v8a](https://gitee.com/n9tank/ZipParallel/releases)