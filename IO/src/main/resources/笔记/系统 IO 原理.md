# 系统 IO 原理



## Linux

kernel(内核)、

`mount /path` 挂载文件或硬盘

`amount /path` 取消挂载

`/boot`是从sda1 分区挂载覆盖了 sda3 分区中的`/boot` 目录的

`ln /root/xxoo.txt /root/ooxx.txt` 建立硬链接

`ln -s /root/test.txt /root/ethan.txt` 建立软连接 test.txt -> ethan.txt

`dd if=/dev/zero of=mydisk.img bs=1048576 count=100`	zero:无限大的空, if:输入文件 of:输出文件  

* 如果 if 是分区，of 是文件 相当于把分区做了一个压缩备份，

* 如果if 是一个虚拟的例如zero, 相当于填充了一个都是空的文件-->of
* 如果 if 和 of 都是分区，则是两块硬盘对拷
* 如果 if 是文件，of 是分区，则是恢复

`losetup /dev/loop0 mydisk.img` 回环挂载镜像文件

`mount -t ext /dev/loop0 /mnt/ooxx`将loop0挂载到/mnt/ooxx下

之后/mnt/ooxx 下的所有文件都属于 mydisk.img文件，如果将 mydisk 卸载

/mnt/ooxx下的文件会消失，重新挂载之后文件恢复。

`cp /lib64/{文件,文件,文件} /ethan` 批量拷贝文件到 ethan 目录下

`echo $$ 、echo $BASHPID` 当前进城 ID, `$$`优先级高于管道`|`



`lsof -op $$` 当前 bash进程打开了哪些文件,o:偏移量, p:进程号

任何程序都有 0，1，2 三个文件描述符：

0：标准输入

1：标准输出

2：报错输出

`exec 8< ooxx.txt` 使用文件描述符 8 读取 ooxx .txt文件

`<` 读取 

`>`写入

read a 0<& 8 ：通过文件描述符 8 读取文件的回车符之前的内容赋值给变量 a，read 一次只能读取一行,遇到换行符就结束

echo $a：输出变量 a 的值

`<`或`>`的左边一般是一个文件描述符，右边是文件，如果右边也是文件描述符, 需要在`<`或`>`紧后面跟一个`&`

`cd /proc/$$/fd` 当前进程文件描述符目录

`proc`内核映射目录,映射了内核中变量属性等

### 重定向：机制,不是命令

可以让 IO 重定向到其他地方去，

通过`>`和`<`实现

`ls ./ 1> ~/ls.out` 标准输出重定向到ls.out文件中

`cat 0< ooxx.txt 1> cat.out` 标准输入输出重定向

### 管道

`head xxoo.txt` 默认显示指定文件前 10 行

`tail xxoo.txt`默认限制指定文件尾 10 行

`head/tail -n xxoo.txt` 显示前/后 n 行

显示第八行：

`head -8 xxoo.txt | tail -1` 

前面的输出作为后面的输入

进程之间是严格隔离的，如果在父进程中给一个变量赋值, 如果不用export定义变量x那么在其他进程中这个变量不就可见

指令块：`{ echo $$; echo "123"; } | cat` 指令块中的指令一起执行，管道左右两边是不同的进程, 两个进程的输入输出通过管道对接,管道右边的输入是左边管道的输出

 $$优先级高于管道`|`, bash解释执行时先遇到`$$`, 所以先输出`echo $$`时输出的父进程的 id，如果要打印子进程 id 使用`$BASHPID`.



## VFS、FD(file descriptor)、pagecache

* 虚拟文件系统

在内存中构建的目录树、是内存和硬件之间的抽象层、 inode id(可以理解成文件 ID)、内核将文件读取到内存中的 开辟一个 pagecache 、默认 4k 大小、如果两个程序想打开同一个文件，两个程序共享VFS 中的pagecache 的、dirty(pagecache 被修改后会被标记为 dirty, 就会有一个 flush 过程将脏页写到磁盘中，如果脏页还没来得及写到磁盘中突然断电了就会有数据丢失)、可以调用系统函数立即写入也可以由系统自己决定什么时候写入、 

文件描述符fd、有一个指针 seek，指向已经读取的一个偏移量，程序在读取同一个文件的时候有各自的 seek 表示读取到什么位置。两边读取互不影响。

Linux中一切皆文件 、

文件类型(开头)：

* -：普通文件（可执行,图片,文本...） （REG）
* d：目录
* b：块设备
* c：字符设备   CHR
* s：socket
* l：链接
* p：管道(pipeline)
* 【eventpoll】...

硬连接：被两个链接(类似变量)指向同一个内存区域的文件，各自拥有自己的no'deID，删除其中一个没有任何影响，另一个连接既然能读写文件

软连接：被引用数量不会增加，修改任意文件内容对另一个连接课件，如果删除了被连接的文件，则软连接报错

### pagecache

应用程序和内核通信时, 需要一个system call(系统调用)，系统调用是通过int 0x80这个 CPU 中断指令实现的。

Int 0x80是十进制的 128，这个值是存储在 CPU 的寄存器中的, 是和内核中的中断描述符表进行匹配的。

128 在CPU指令中表示call back

写文件的时候如果突然电源断了在没有写完pagecache,如果不手动刷盘, 则不会持久化到磁盘中, 如果断电之前写满了 pagecache 就能成功刷盘。

