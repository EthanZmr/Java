package com.ethan.c10k;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

/**
 * @author ethan
 * @since 2023/11/15
 */
public class C10KClient {
    public static void main(String[] args) {
        LinkedList<SocketChannel> clients = new LinkedList<>();
        InetSocketAddress serverAddr = new InetSocketAddress("192.168.1.109", 8080);

        for (int i = 10000; i < 65000; i++) {
            try {
                SocketChannel channel = SocketChannel.open();
                channel.bind(new InetSocketAddress("192.168.1.9", i));
                channel.connect(serverAddr);
                boolean c = channel.isOpen();
                clients.add(channel);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        System.out.println("clients " + clients.size());
        try {
            System.in.read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
