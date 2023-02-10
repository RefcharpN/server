package org.example;

import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;


class ServerSomthing extends Thread {

    private Socket socket; // сокет, через который сервер общается с клиентом,
    // кроме него - клиент и сервер никак не связаны
    private BufferedReader in; // поток чтения из сокета
    private BufferedWriter out; // поток завписи в сокет

    private Operations_List oper;


    public ServerSomthing(Socket socket) throws IOException {
        this.socket = socket;

        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

        System.out.println("установлено новое соединение");

        start(); // вызываем run()
    }


    @Override
    public void run() {
        String input_string;
        try {
            while (true) {
                input_string = in.readLine();

                JSONObject json = new JSONObject(input_string);

                if(json.getString("OPERATION").equals("-1")) {
                    this.downService(); // харакири
                    break; // если пришла пустая строка - выходим из цикла прослушки
                }

                this.oper = new Operations_List(json);
                var result = this.oper.processing(json.getString("OPERATION"));
                System.out.println(result);
                this.send(result);

            }
        } catch (NullPointerException ignored) {} catch (Exception e) {
            throw new RuntimeException(e);
        }


    }

    private void send(String msg) {
        try {
            out.write(msg + "\n");
            out.flush();
        } catch (IOException ignored) {}

    }

    /**
     * закрытие сервера
     * прерывание себя как нити и удаление из списка нитей
     */
    private void downService() {
        try {
            if(!socket.isClosed()) {
                socket.close();
                in.close();
                out.close();
                for (ServerSomthing vr : Server.serverList) {
                    if(vr.equals(this)) vr.interrupt();
                    Server.serverList.remove(this);
                }
                System.out.println("соединение разорвано \n");
            }
        } catch (IOException ignored) {}
    }
}



public class Server {

    public static final int PORT = 8080;
    public static LinkedList<ServerSomthing> serverList = new LinkedList<>(); // список всех нитей - экземпляров
    // сервера, слушающих каждый своего клиента

    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(PORT);
        System.out.println("Server Started");
        try {
            while (true) {
                // Блокируется до возникновения нового соединения:
                Socket socket = server.accept();
                try {
                    serverList.add(new ServerSomthing(socket)); // добавить новое соединенние в список
                } catch (IOException e) {
                    // Если завершится неудачей, закрывается сокет,
                    // в противном случае, нить закроет его:
                    socket.close();
                }
            }
        } finally {
            server.close();
        }
    }
}