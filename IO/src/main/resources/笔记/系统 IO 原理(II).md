# 系统 IO 原理(II)

## PageChache

推荐书籍：《深入理解 Linux 内核》《深入理解计算机系统》

> epoll 是怎么知道数据到达的？
>
> 通过中断机制(int 0x80 软中断)

物理内存：线性地址，里面有内核、程序。同一个程序的内存可能是碎片化的,物理内存的基本单位是 4k 称为 page。

虚拟内存：每一个程序看自己就认为自己直接占用了全部屋里内存空间，程序中的内存分配可能是连续的，但是在映射到屋里内存中可能是不连续的。映射的过程需要 cpu 中的 mmu 来完成。

不会全量分配一个程序的所有内存空间, 会预分配，随用随分配。

如果需要访问某一个地址的时候没有会产生缺页异常, 这时候会触发中断切换到内核

内核先分配一个 page，将这个 page映射到相应的虚拟线性地址位置，再回到应用程序继续访问

进程内部分布：代码段、数据段、堆、栈

两个进程访问磁盘中的同一个文件时，访问该文件在内核中的 pagecache 即可，这个 pagecache 在内核中只会有一个

pagecache是内核维护的,相当于是磁盘上的文件和进程之间的一个抽象层

命令`sysctl -a ｜ grep dirty` 显示系统中的脏页配置项

```
vm.dirty_background_ratio=90  // 内核中IO使用了90%的可用内存时进行刷盘
vm.dirty_ratio=90	// 程序往内核写数据时分配page达到了可用内存的90%时会阻塞进程
vm.dirty_writeback_centisecs = 5000 // 后台写脏页多长时间内核自动刷盘(百分之一秒)
vm.dirty_expire_centisecs = 30000	// 脏页过期时间,过期后内核自动刷盘
```

> 思考一下 redis 的持久化三种策略

刚创建的和被修改的 page 都是脏页，刷盘后这个 page 就不是脏页了，不是dirty 的 page 会在 LRU 或 LFU 时被淘汰掉。如果要淘汰的page是脏页，会在淘汰之前先进行刷盘。

当前活跃进程在写 page 时，可用内存已使用 90%(根据上面的配置)时会开始将脏页进行刷盘, 刷盘后这些 page 就不再是脏页, 可用内存不足时就会淘汰其他不活跃的 pagecache。其他不活跃的 cached 百分比就会降低，当前活跃进程就会有可用空间



* FileOutputStream 的 write 方法进行写操作
  * 速度慢
  * 每一次 write 都对应一次系统调用syscall，syscall次数太多, 会产生较大的开销(保存现场, 上下文切换)
  * 只要还有可用内存，pagecache 就是 100%。可用内存不足时就会淘汰之前缓存的 page
  * 只要没达到上面配置的90%可用内存的阈值, 没产生刷盘操作, 电源突然断了就会丢失所有当前写入的数据。
* BufferedOutputStream的write方法进行写操作
  * 相对 FileOutputStream 读写速度快
  * 一次读写8kb, 减少了 syscall 次数，减少了上下文切换，所以相应开销也变小

**pagecache 的本质是优化 IO 性能，但是对数据一致性无法保证，异常断电可能会丢失数据。**

ByteBuffer:

​	操作：put(往缓冲区放)、flip(读写反转)、get(从缓冲区获取)、compact(pos 指向第一个为空的位置, limit 指到 capacity 位置)、clear(清空缓冲区)

​	属性：position(默认 0)、limit(临界值)、capacity(总容量)

buffer的 JVM堆内分配：ByteBuffer.allocate(1024);

buffer的 JVM堆外分配：ByteBuffer.allocateDirect(1024);



RandomAccessFile：

​	操作：write(普通写) 、 seek(修改指针偏移量, 可以修改到任意位置开始写)、getChannel()拿到 FileChannel 对象、

只有文件系统有 map, FileChannel 的对象可以调用 map(MapMode, position, size)获得MappedByteBuffer对象，

通过系统调用mmap得到一个Java 进程堆外的和文件映射的 bytebuffer。

mappedByteBuffer.put()写入 pagecache不会产生系统调用, 无需进行上下文切换。直接映射到内核的 pagecache, 之前是需要write系统调用才能让数据进入到 pagechache 中的。但是 mmap 的内存映射依然是内核的 pagecache 体系所约束的, 意思就是可能会丢数据。

map.force()相当于 flush 操作

Java 是一个 C 的进程，Java 的堆和 JVM堆空间不一样，Java 堆空间是操作系统给Java 这个C 的程序分配的堆内存。

1. 相当于 JVM 的堆在 Java 这个 C 进程的堆里
2. 堆内：JVM 的堆里的字节数组
3. 堆外：JVM 的堆外，Java 的堆内
4. mapped 映射：是 mmap 调用的一个进程和内核共享的内存区域, 这个内存区域是 pagecache 到文件的映射

性能：堆内<堆外<mapped(仅限 file)

**操作系统 OS 没有绝对的可靠性, 为什么设计 pagecache, 减少硬件 IO 调用、 提速、 优先使用内存。即便想要绝对一致性，单点性能会让性能损耗，一毛钱收益都没有。 所以现在有主从赋值, 主备高可用**





