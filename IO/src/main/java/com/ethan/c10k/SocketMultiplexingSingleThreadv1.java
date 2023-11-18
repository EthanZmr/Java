package com.ethan.c10k;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

/**
 * @author ethan
 * @since 2023/11/17
 */
public class SocketMultiplexingSingleThreadv1 {


    private ServerSocketChannel server = null;
    // linux多路复用器(select、poll、epoll)
    private Selector selector = null;
    int port = 9090;

    public void initServer() {
        try {
            server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.bind(new InetSocketAddress(port));

            // 如果在epoll模型下, 这一步相当于epoll_create,
            // 会返回一个开辟红黑树空间的 fd3
            // select poll *epoll 优先选择: epoll 但是可以使用-D 启动参数修正
            selector = Selector.open();

            // server 相当于 listen 状态的 fd4
            // register:
            /**
             * register:
             * 如果是 select 模型：在 jvm 中存储 fd4
             * 如果是 epoll 模型：调用epoll_ctl(fd3, ADD, fd3, EPOLLIN);
             */
            server.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void start() {
        initServer();
        System.out.println("服务器启动了...");
        try {
            while (true) {
                Set<SelectionKey> keys = selector.keys();
                System.out.println(keys.size() + " size");
                /**
                 * 1. 调用多路复用器(select, poll or epoll(epoll_wait))
                 * 这里的 select、poll是系统调用 select(fd4)、poll(fd4)
                 * epoll调用的是内核的epoll_wait
                 * 参数可以带时间, 不传或传 0 会阻塞, 否则就是超时时间
                 */
                while (selector.select(500) > 0) {
                    // 返回有状态的 fd 集合
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    // socket分为 listen 和连接
                    while (iterator.hasNext()) {
                        SelectionKey selectionKey = iterator.next();
                        // 不移除会重复循环处理
                        iterator.remove();
                        // 如果要接受一个新的连接,
                        // accept 接受连接并返回 fd 之后
                        // 如果是 select 模型, 会将 fd 放入 jvm 中
                        // 如果是 epoll 就会通过 epoll_ctl把新的 fd 注册到内核空间中
                        if (selectionKey.isAcceptable()) {
                            acceptHandler(selectionKey);
                        }
                        // 处理读写操作
                        if (selectionKey.isReadable()) {
                            readHandler(selectionKey);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void acceptHandler(SelectionKey key) {
        try {
            ServerSocketChannel channel = (ServerSocketChannel) key.channel();
            SocketChannel client = channel.accept();
            client.configureBlocking(false);
            ByteBuffer byteBuffer = ByteBuffer.allocate(8192);
            // 把新的 fd 注册到内核空间
            client.register(selector, SelectionKey.OP_READ, byteBuffer);
            System.out.println("新客户端:" + client.getRemoteAddress());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void readHandler(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ByteBuffer byteBuffer = (ByteBuffer) key.attachment();
        byteBuffer.clear();
        int read = 0;
        try {
            while (true) {
                read = channel.read(byteBuffer);
                if (read > 0) {
                    byteBuffer.flip();
                    while (byteBuffer.hasRemaining()) {
                        channel.write(byteBuffer);
                    }
                    byteBuffer.clear();
                } else if (read == 0) {
                    break;
                } else {
                    channel.close();
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {

    }
}
