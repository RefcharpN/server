package org.example;

import org.json.JSONObject;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedList;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


class ServerSomthing extends Thread {

    private Socket socket; // сокет, через который сервер общается с клиентом,
    // кроме него - клиент и сервер никак не связаны
    private BufferedReader in; // поток чтения из сокета
    private BufferedWriter out; // поток завписи в сокет
    private Operations_List oper;
    PublicKey publicKey = null;
    PublicKey publicKey_client = null;
    PrivateKey privateKey = null;
    Logger logger;


    public ServerSomthing(Socket socket) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        this.socket = socket;

        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

        System.out.println("установлено новое соединение");

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        KeyPair pair = generator.generateKeyPair();

        this.privateKey = pair.getPrivate();
        this.publicKey = pair.getPublic();
        this.read_key();
        this.send_key();

        logger = Logger.getLogger("MyLog");
        FileHandler fh;
        try {
            fh = new FileHandler("./log/server_log.log", true);
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        start(); // вызываем run()
    }


    @Override
    public void run() {
        String input_string;

        try {
            while (true) {
                input_string = in.readLine();

                Cipher decryptCipher = Cipher.getInstance("RSA");
                decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);

                byte[] decryptedMessageBytes = decryptCipher.doFinal(Base64.getDecoder().decode(input_string));
                String decryptedMessage = new String(decryptedMessageBytes, StandardCharsets.UTF_8);

                JSONObject json = new JSONObject(decryptedMessage);

                if(json.getString("OPERATION").equals("-1")) {
                    this.downService(); // харакири
                    break; // если пришла пустая строка - выходим из цикла прослушки
                }


                System.out.println(decryptedMessage);

                this.oper = new Operations_List(Server.pg_adr,Server.pg_password, json);
                var out_result = this.oper.processing(json.getString("OPERATION"));

                System.out.println(out_result);
                logger.log(Level.INFO, String.format("расшифрованная строка - %s\nрезультат работы - %s\n", decryptedMessage, out_result));
                this.send(out_result);

            }
        } catch (NullPointerException ignored) {} catch (Exception e) {

            throw new RuntimeException(e);
        }
    }


    private void read_key() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String input_string = in.readLine();

        byte[] data = Base64.getDecoder().decode(input_string.getBytes());
        X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
        KeyFactory fact = KeyFactory.getInstance("RSA");
        this.publicKey_client = fact.generatePublic(spec);
    }

    private void send_key() {
        try {
            out.write(Base64.getEncoder().encodeToString(publicKey.getEncoded()) + "\n");
            out.flush();
        } catch (IOException ignored) {}

    }

    private void send(String msg) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");//строгое соблюдение
            cipher.init(Cipher.ENCRYPT_MODE, publicKey_client);
            out.write(Base64.getEncoder().encodeToString(cipher.doFinal(msg.getBytes())) + "\n");
            out.flush();
        } catch (IOException ignored) {} catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        } catch (BadPaddingException e) {
            throw new RuntimeException(e);
        }

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
    public static String pg_adr;
    public static String pg_password;
    public static LinkedList<ServerSomthing> serverList = new LinkedList<>(); // список всех нитей - экземпляров
    // сервера, слушающих каждый своего клиента

    public static void main(String[] args) throws IOException {

//        BufferedReader cons = new BufferedReader(new InputStreamReader(System.in));
//        System.out.println("введите адрес БД");
//        pg_adr = cons.readLine();
//        System.out.println("введите пароль БД");
//        pg_password = cons.readLine();

        Properties prop = new Properties();
        String fileName = "./server.config";
        try (FileInputStream fis = new FileInputStream(fileName)) {
            prop.load(fis);
        } catch (FileNotFoundException ex) {} catch (IOException ex) {}


        System.out.println(prop.getProperty("server.address"));
        pg_adr = prop.getProperty("server.address");
        System.out.println(prop.getProperty("server.password"));
        pg_password = prop.getProperty("server.password");


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
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                } catch (InvalidKeySpecException e) {
                    throw new RuntimeException(e);
                }
            }
        } finally {
            server.close();
        }
    }
}